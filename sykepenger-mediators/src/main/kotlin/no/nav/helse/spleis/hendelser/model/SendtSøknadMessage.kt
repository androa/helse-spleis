package no.nav.helse.spleis.hendelser.model

import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Periode
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.spleis.hendelser.MessageFactory
import no.nav.helse.spleis.hendelser.MessageProcessor
import no.nav.helse.spleis.rest.HendelseDTO

// Understands a JSON message representing a Søknad
internal class SendtSøknadMessage(originalMessage: String, private val problems: MessageProblems) :
    SøknadMessage(originalMessage, problems) {
    init {
        requireValue("@event_name", "sendt_søknad")
        requireValue("status", "SENDT")
        requireKey("id", "sendtNav", "fom", "tom", "egenmeldinger", "fravar")
        interestedIn("arbeidGjenopptatt")
        interestedIn("andreInntektskilder")
    }

    private val søknadFom get() = this["fom"].asLocalDate()
    private val søknadTom get() = this["tom"].asLocalDate()
    private val aktørId get() = this["aktorId"].asText()
    private val orgnummer get() = this["arbeidsgiver.orgnummer"].asText()
    private val sendtNav get() = this["sendtNav"].asLocalDateTime()
    private val perioder get() = this["soknadsperioder"].map {
        Periode.Sykdom(
            fom = it.path("fom").asLocalDate(),
            tom = it.path("tom").asLocalDate(),
            grad = it.path("sykmeldingsgrad").asInt(),
            faktiskGrad = it.path("faktiskGrad").asDouble(it.path("sykmeldingsgrad").asDouble())
        )
    } + this["egenmeldinger"].map {
        Periode.Egenmelding(
            fom = it.path("fom").asLocalDate(),
            tom = it.path("tom").asLocalDate()
        )
    } + this["fravar"].map {
        val fraværstype = it["type"].asText()
        val fom = it.path("fom").asLocalDate()
        when (fraværstype) {
            in listOf("UTDANNING_FULLTID", "UTDANNING_DELTID") -> Periode.Utdanning(fom, søknadTom)
            "PERMISJON" -> Periode.Permisjon(fom, it.path("tom").asLocalDate())
            "FERIE" -> Periode.Ferie(fom, it.path("tom").asLocalDate())
            "UTLANDSOPPHOLD" -> Periode.Utlandsopphold(fom, it.path("tom").asLocalDate())
            else -> problems.severe("Ukjent fraværstype $fraværstype")
        }
    } + (this["arbeidGjenopptatt"].asOptionalLocalDate()?.let { listOf(Periode.Arbeid(it, søknadTom)) }
    ?: emptyList())

    override fun accept(processor: MessageProcessor) {
        processor.process(this)
    }

    internal fun asSøknad(): Søknad {
        return Søknad(
            meldingsreferanseId = this.id,
            fnr = fødselsnummer,
            aktørId = aktørId,
            orgnummer = orgnummer,
            perioder = perioder,
            harAndreInntektskilder = harAndreInntektskilder(),
            sendtTilNAV = sendtNav
        )
    }

    private fun harAndreInntektskilder() = this["andreInntektskilder"].isArray && !this["andreInntektskilder"].isEmpty

    fun asSpeilDTO(): HendelseDTO = HendelseDTO.SendtSøknadDTO(
        rapportertdato = sendtNav,
        sendtNav = sendtNav,
        fom = søknadFom,
        tom = søknadTom
    )

    object Factory : MessageFactory<SendtSøknadMessage> {
        override fun createMessage(message: String, problems: MessageProblems) = SendtSøknadMessage(message, problems)
    }
}
