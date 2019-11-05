package no.nav.helse.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.SykdomshendelseType
import no.nav.helse.person.domain.PersonHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje.Companion.egenmeldingsdag
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.dag.Dag
import java.util.*

class InntektsmeldingHendelse private constructor(hendelseId: String, private val inntektsmelding: Inntektsmelding) : PersonHendelse, SykdomstidslinjeHendelse(hendelseId) {
    constructor(inntektsmelding: Inntektsmelding) : this(UUID.randomUUID().toString(), inntektsmelding)

    companion object {

        fun fromJson(jsonNode: JsonNode): InntektsmeldingHendelse {
            return InntektsmeldingHendelse(jsonNode["hendelseId"].textValue(), Inntektsmelding(jsonNode["inntektsmelding"]))
        }
    }

    override fun nøkkelHendelseType() = Dag.NøkkelHendelseType.Inntektsmelding

    override fun aktørId() =
            inntektsmelding.arbeidstakerAktorId

    override fun rapportertdato() =
            inntektsmelding.mottattDato

    override fun organisasjonsnummer() =
            inntektsmelding.virksomhetsnummer

    override fun sykdomstidslinje(): Sykdomstidslinje {
        val arbeidsgiverperiode = if (inntektsmelding.arbeidsgiverperioder.isNotEmpty())
            inntektsmelding.arbeidsgiverperioder
                    .map { Sykdomstidslinje.egenmeldingsdager(it.fom, it.tom, this) }
                    .reduce { acc, sykdomstidslinje -> acc.plus(sykdomstidslinje, Sykdomstidslinje.Companion::ikkeSykedag) }
        else null

        val ferietidslinje = if (inntektsmelding.ferie.isNotEmpty()) inntektsmelding.ferie
                .map { Sykdomstidslinje.ferie(it.fom, it.tom, this) }
                .reduce { resultatTidslinje, delTidslinje -> resultatTidslinje + delTidslinje }
        else null

        return when {
            arbeidsgiverperiode != null && ferietidslinje != null -> (arbeidsgiverperiode + ferietidslinje)
            arbeidsgiverperiode == null && ferietidslinje != null -> ferietidslinje
            arbeidsgiverperiode != null && ferietidslinje == null -> arbeidsgiverperiode
            else  -> egenmeldingsdag(inntektsmelding.førsteFraværsdag, this)
        }

    }

    override fun toJson(): JsonNode {
        return (super.toJson() as ObjectNode).apply {
            put("type", SykdomshendelseType.InntektsmeldingMottatt.name)
            set("inntektsmelding", inntektsmelding.toJson())
        }
    }


}
