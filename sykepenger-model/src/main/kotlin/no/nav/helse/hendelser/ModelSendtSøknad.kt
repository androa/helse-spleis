package no.nav.helse.hendelser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.person.IAktivitetslogger
import no.nav.helse.sykdomstidslinje.ConcreteSykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.dag.Dag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class ModelSendtSøknad(
    hendelseId: UUID,
    private val fnr: String,
    private val aktørId: String,
    private val orgnummer: String,
    private val rapportertdato: LocalDateTime,
    private val perioder: List<Periode>,
    private val aktivitetslogger: Aktivitetslogger,
    private val originalJson: String
) : SykdomstidslinjeHendelse(hendelseId, Hendelsestype.SendtSøknad), IAktivitetslogger by aktivitetslogger {

    private val fom: LocalDate
    private val tom: LocalDate

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        fun fromJson(json: String): ModelSendtSøknad {
            return objectMapper.readTree(json).let {
                ModelSendtSøknad(
                    hendelseId = UUID.fromString(it["hendelseId"].textValue()),
                    fnr = it.path("søknad").path("fnr").asText(),
                    aktørId = it.path("søknad").path("aktorId").asText(),
                    orgnummer = it.path("søknad").path("arbeidsgiver").path("orgnummer").asText(),
                    rapportertdato = it.path("søknad").path("opprettet").asText().let { LocalDateTime.parse(it) },
                    perioder = it.path("søknad").path("soknadsperioder").map { periode: JsonNode ->
                        Periode.Sykdom(
                            fom = periode["fom"].asLocalDate(),
                            tom = periode["tom"].asLocalDate(),
                            grad = periode["sykmeldingsgrad"].asInt(),
                            faktiskGrad = periode["faktiskGrad"]?.asDouble() ?: periode["sykmeldingsgrad"].asDouble()
                        )
                    },
                    aktivitetslogger = Aktivitetslogger(),
                    originalJson = objectMapper.writeValueAsString(it.path("søknad"))
                )
            }
        }

        private fun JsonNode.asLocalDate() =
            asText().let { LocalDate.parse(it) }

    }

    init {
        if (perioder.isEmpty()) aktivitetslogger.severe("Søknad må inneholde perioder")
        perioder.filterIsInstance<Periode.Sykdom>()
            .also { fom = it.minBy { it.fom }?.fom ?: aktivitetslogger.severe("Søknad mangler fradato") }
            .also { tom = it.maxBy { it.tom }?.tom ?: aktivitetslogger.severe("Søknad mangler tildato") }
    }

    override fun sykdomstidslinje() = perioder
        .map { it.sykdomstidslinje(this) }
        .reduce(ConcreteSykdomstidslinje::plus)
        .also { if(aktivitetslogger.hasErrors()) throw aktivitetslogger }

    override fun nøkkelHendelseType() = Dag.NøkkelHendelseType.Søknad

    override fun fødselsnummer() = fnr

    override fun organisasjonsnummer() = orgnummer

    override fun rapportertdato() = rapportertdato

    override fun aktørId() = aktørId

    // TODO: Should not be part of Model events
    override fun toJson(): String = objectMapper.writeValueAsString(
        mapOf(
            "hendelseId" to hendelseId(),
            "type" to hendelsetype(),
            "søknad" to objectMapper.readTree(originalJson)
        )
    )

    override fun kanBehandles() = !valider().hasErrors()

    internal fun valider(): Aktivitetslogger {
        perioder.forEach { it.valider(this, aktivitetslogger) }
        return aktivitetslogger
    }

    sealed class Periode(internal val fom: LocalDate, internal val tom: LocalDate) {

        internal abstract fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad): ConcreteSykdomstidslinje

        internal open fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger) {}

        internal fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger, beskjed: String){
            if(fom < sendtSøknad.fom || tom > sendtSøknad.tom) aktivitetslogger.error(beskjed)
        }

        class Ferie(fom: LocalDate, tom: LocalDate): Periode(fom, tom) {
            override fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad) =
                ConcreteSykdomstidslinje.ferie(fom, tom, sendtSøknad)

            override fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger) =
                valider(sendtSøknad, aktivitetslogger, "Ferie ligger utenfor sykdomsvindu")
        }
        class Sykdom(fom: LocalDate, tom: LocalDate, private val grad: Int, private val faktiskGrad: Double = grad.toDouble()): Periode(fom, tom) {
            override fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad) =
                ConcreteSykdomstidslinje.sykedager(fom, tom, sendtSøknad)

            override fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger){
                if(grad != 100) aktivitetslogger.error("grad i søknaden er ikke 100%%")
                if(faktiskGrad != 100.0) aktivitetslogger.error("faktisk grad i søknaden er ikke 100%%")
            }
        }

        class Utdanning(fom: LocalDate, private val _tom: LocalDate? = null): Periode(fom, LocalDate.MAX) {
            override fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad) =
                ConcreteSykdomstidslinje.utenlandsdager(fom, _tom ?: sendtSøknad.tom, sendtSøknad)

            override fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger){
                if(fom < sendtSøknad.fom || (_tom ?: sendtSøknad.tom) > sendtSøknad.tom)
                    aktivitetslogger.error("Utdanning ligger utenfor sykdomsvindu")
            }
        }
        class Permisjon(fom: LocalDate, tom: LocalDate): Periode(fom, tom) {
            override fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad) =
                ConcreteSykdomstidslinje.permisjonsdager(fom, tom, sendtSøknad)

            override fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger) =
                aktivitetslogger.error("Permisjon foreløpig ikke understøttet")
        }
        class Egenmelding(fom: LocalDate, tom: LocalDate): Periode(fom, tom) {
            override fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad) =
                ConcreteSykdomstidslinje.egenmeldingsdager(fom, tom, sendtSøknad)

            override fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger) {}
        }

        class Arbeid(fom: LocalDate, tom: LocalDate): Periode(fom, tom) {
            override fun sykdomstidslinje(sendtSøknad: ModelSendtSøknad) =
                ConcreteSykdomstidslinje.ikkeSykedager(fom, tom, sendtSøknad)

            override fun valider(sendtSøknad: ModelSendtSøknad, aktivitetslogger: Aktivitetslogger) =
                valider(sendtSøknad, aktivitetslogger, "Arbeidsdag ligger utenfor sykdomsvindu")
        }
    }
}
