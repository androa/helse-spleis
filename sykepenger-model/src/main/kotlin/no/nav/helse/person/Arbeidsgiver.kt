package no.nav.helse.person

import no.nav.helse.hendelser.*
import no.nav.helse.person.Aktivitetslogg.Aktivitet
import no.nav.helse.utbetalingslinjer.Utbetaling
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

internal class Arbeidsgiver private constructor(
    private val person: Person,
    private val organisasjonsnummer: String,
    private val id: UUID,
    private val inntekthistorikk: Inntekthistorikk,
    private val perioder: MutableList<Vedtaksperiode>,
    private val utbetalinger: MutableList<Utbetaling>
) : Aktivitetskontekst {

    internal fun inntektshistorikk() = inntekthistorikk.clone()

    internal constructor(person: Person, organisasjonsnummer: String) : this(
        person = person,
        organisasjonsnummer = organisasjonsnummer,
        id = UUID.randomUUID(),
        inntekthistorikk = Inntekthistorikk(),
        perioder = mutableListOf(),
        utbetalinger = mutableListOf()
    )

    internal fun accept(visitor: ArbeidsgiverVisitor) {
        visitor.preVisitArbeidsgiver(this, id, organisasjonsnummer)
        inntekthistorikk.accept(visitor)
        visitor.preVisitUtbetalinger(utbetalinger)
        utbetalinger.forEach { it.accept(visitor) }
        visitor.postVisitUtbetalinger(utbetalinger)
        visitor.preVisitPerioder()
        perioder.forEach { it.accept(visitor) }
        visitor.postVisitPerioder()
        visitor.postVisitArbeidsgiver(this, id, organisasjonsnummer)
    }

    internal fun organisasjonsnummer() = organisasjonsnummer

    internal fun utbetaling() = utbetalinger.lastOrNull()

    internal fun nåværendeTidslinje() =
        utbetaling()?.utbetalingstidslinje() ?: throw IllegalStateException("mangler utbetalinger")

    internal fun push(utbetaling: Utbetaling) = utbetalinger.add(utbetaling)

    internal fun håndter(sykmelding: Sykmelding) {
        sykmelding.kontekst(this)
        if(perioder.map { it.håndter(sykmelding)}.none { it } ) {
            sykmelding.info("Lager ny vedtaksperiode")
            nyVedtaksperiode(sykmelding).håndter(sykmelding)
            Vedtaksperiode.sorter(perioder)
        }
    }

    internal fun håndter(søknad: Søknad) {
        søknad.kontekst(this)
        if(perioder.map { it.håndter(søknad)}.none { it } ) {
            søknad.error("Forventet ikke søknad. Har nok ikke mottatt sykmelding")
        }
    }

    internal fun håndter(søknad: SøknadArbeidsgiver) {
        søknad.kontekst(this)
        if(perioder.map { it.håndter(søknad)}.none { it } ) {
            søknad.error("Forventet ikke søknad til arbeidsgiver. Har nok ikke mottatt sykmelding")
        }
    }

    internal fun håndter(inntektsmelding: Inntektsmelding) {
        inntektsmelding.kontekst(this)
        if(perioder.map { it.håndter(inntektsmelding)}.none { it } ) {
            inntektsmelding.error("Forventet ikke inntektsmelding. Har nok ikke mottatt sykmelding")
        }
    }

    internal fun håndter(ytelser: Ytelser) {
        ytelser.kontekst(this)
        perioder.forEach { it.håndter(ytelser) }
    }

    internal fun håndter(manuellSaksbehandling: ManuellSaksbehandling) {
        manuellSaksbehandling.kontekst(this)
        perioder.forEach { it.håndter(manuellSaksbehandling) }
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        vilkårsgrunnlag.kontekst(this)
        perioder.forEach { it.håndter(vilkårsgrunnlag) }
    }

    internal fun håndter(simulering: Simulering) {
        simulering.kontekst(this)
        perioder.forEach { it.håndter(simulering) }
    }

    internal fun håndter(utbetaling: UtbetalingOverført) {
        utbetaling.kontekst(this)
        perioder.forEach { it.håndter(utbetaling) }
    }

    internal fun håndter(utbetaling: UtbetalingHendelse) {
        utbetaling.kontekst(this)
        perioder.forEach { it.håndter(utbetaling) }
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        påminnelse.kontekst(this)
        return perioder.any { it.håndter(påminnelse) }
    }

    internal fun håndter(hendelse: KansellerUtbetaling) {
        hendelse.kontekst(this)
        utbetalinger.reversed().firstOrNull {
            it.arbeidsgiverOppdrag().fagsystemId() == hendelse.fagsystemId
        }
            ?.kansellerUtbetaling()
            ?.also {
                utbetalinger.add(it)
                Aktivitet.Behov.utbetaling(
                    hendelse.aktivitetslogg,
                    it.arbeidsgiverOppdrag(),
                    saksbehandler = hendelse.saksbehandler
                )
            }
            ?: hendelse.error("Avvis hvis vi ikke finner utbetalingsreferanse %s", hendelse.fagsystemId)
    }

    internal fun sykdomstidslinje() = Vedtaksperiode.sykdomstidslinje(perioder)

    internal fun inntekt(dato: LocalDate): BigDecimal? =
        inntekthistorikk.inntekt(dato)

    internal fun invaliderPerioder(hendelse: ArbeidstakerHendelse) {
        perioder.forEach { it.invaliderPeriode(hendelse) }
    }

    internal fun addInntekt(inntektsmelding: Inntektsmelding) {
        inntektsmelding.addInntekt(inntekthistorikk)
    }

    private fun nyVedtaksperiode(sykmelding: Sykmelding): Vedtaksperiode {
        return Vedtaksperiode(
            person = person,
            arbeidsgiver = this,
            id = UUID.randomUUID(),
            aktørId = sykmelding.aktørId(),
            fødselsnummer = sykmelding.fødselsnummer(),
            organisasjonsnummer = sykmelding.organisasjonsnummer()
        ).also {
            perioder.add(it)
        }
    }

    internal fun tilstøtende(vedtaksperiode: Vedtaksperiode) =
        Vedtaksperiode.tilstøtendePeriode(vedtaksperiode, perioder)

    internal fun tidligerePerioderFerdigBehandlet(vedtaksperiode: Vedtaksperiode) =
        Vedtaksperiode.tidligerePerioderFerdigBehandlet(perioder, vedtaksperiode)

    internal fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: ArbeidstakerHendelse) {
        perioder.forEach { it.håndter(vedtaksperiode, GjenopptaBehandling(hendelse)) }
    }

    internal fun avsluttBehandling(vedtaksperiode: Vedtaksperiode, hendelse: ArbeidstakerHendelse) {
        perioder.forEach { it.håndter(vedtaksperiode, AvsluttBehandling(hendelse)) }
    }

    internal class GjenopptaBehandling(internal val hendelse: ArbeidstakerHendelse)
    internal class AvsluttBehandling(internal val hendelse: ArbeidstakerHendelse)

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Arbeidsgiver", mapOf("organisasjonsnummer" to organisasjonsnummer))
    }
}
