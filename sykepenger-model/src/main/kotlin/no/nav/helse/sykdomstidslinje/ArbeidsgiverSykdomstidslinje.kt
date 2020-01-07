package no.nav.helse.sykdomstidslinje

import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.person.InntektHistorie
import no.nav.helse.utbetalingstidslinje.Inntektsberegner
import java.time.LocalDate

internal class ArbeidsgiverSykdomstidslinje(
    private val sykdomstidslinjer: List<ConcreteSykdomstidslinje>,
    internal val arbeidsgiverRegler: ArbeidsgiverRegler,
    internal val inntektsberegner: Inntektsberegner,
    internal val arbeidsgiverperiodeSeed: Int = 0
) : SykdomstidslinjeElement {

    override fun accept(visitor: SykdomstidslinjeVisitor) {
        visitor.preVisitArbeidsgiver(this)
        sykdomstidslinjer.forEach { it.accept(visitor) }
        visitor.postVisitArbeidsgiver(this)
    }

    internal fun kutt(sisteDag: LocalDate): ArbeidsgiverSykdomstidslinje {

        return ArbeidsgiverSykdomstidslinje(
            sykdomstidslinjer.mapNotNull { it.kutt(sisteDag) },
            arbeidsgiverRegler,
            inntektsberegner,
            arbeidsgiverperiodeSeed
        )
    }
}
