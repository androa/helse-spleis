package no.nav.helse.utbetalingstidslinje

import no.nav.helse.Grunnbeløp.Companion.`6G`
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.UtbetalingsdagVisitor
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import kotlin.math.roundToInt

internal class MaksimumUtbetaling(
    private val sykdomsgrader: Sykdomsgrader, // Skal brukes når vi støtter flere arbeidsgivere
    private val tidslinjer: List<Utbetalingstidslinje>,
    private val periode: Periode,
    private val aktivitetslogg: Aktivitetslogg
): UtbetalingsdagVisitor {

    init {
        require(tidslinjer.size == 1) {"Flere arbeidsgivere er ikke støttet enda"}
    }

    private var harRedusertUtbetaling = false

    internal fun beregn() {
        tidslinjer.forEach { it.accept(this) }
        if (harRedusertUtbetaling)
            aktivitetslogg.info("Redusert utbetaling minst én dag på grunn av inntekt over 6G")
        else
            aktivitetslogg.info("Utbetaling har ikke blitt redusert på grunn av 6G")
    }

    override fun visitNavDag(dag: NavDag) {
        require(!dag.inntekt.isNaN()) { "Inntekt for ${dag.dato} er NaN" }
        require(!dag.grad.isNaN()) { "Grad for ${dag.dato} er NaN" }
        if (dag.dato in periode && dag.inntekt > `6G`.dagsats(dag.dato)) harRedusertUtbetaling = true
        dag.utbetaling = (minOf(dag.inntekt, `6G`.dagsats(dag.dato)).roundToInt() * (dag.grad / 100)).roundToInt() // TODO: Foreløpig ikke avklart om dette er riktig avrundingsstrategi
    }
}
