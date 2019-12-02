package no.nav.helse.sykdomstidslinje

import no.nav.helse.sykdomstidslinje.dag.*
import java.time.DayOfWeek
import java.time.LocalDate

internal class Utbetalingsberegner(private val dagsats: Int, private val alder: Alder) : SykdomstidslinjeVisitor {

    private var state: UtbetalingState = Initiell
    private val utbetalingslinjer = mutableListOf<InternUtbetalingslinje>()

    private var sykedager = 0
    private var ikkeSykedager = 0
    private var fridager = 0

    private var betalteSykedager = 0
    private var betalteSykepengerEtter67 = 0
    private var maksdato: LocalDate? = null

    fun results(): Utbetalingsberegning {
        require(state != Ugyldig)
        return Utbetalingsberegning(
            maksdato,
            utbetalingslinjer.map { Utbetalingslinje(it.startdato, it.tom, it.dagsats) }
        )
    }

    private data class InternUtbetalingslinje(val startdato: LocalDate, val dagsats: Int) {
        var tom = startdato
    }

    private fun state(state: UtbetalingState) {
        this.state.leaving(this)
        this.state = state
        this.state.entering(this)
    }

    override fun visitArbeidsdag(arbeidsdag: Arbeidsdag) = arbeidsdag(arbeidsdag.dagen)
    override fun visitImplisittDag(implisittDag: ImplisittDag) = if (implisittDag.erHelg()) fridag(implisittDag.dagen) else arbeidsdag(implisittDag.dagen)
    override fun visitFeriedag(feriedag: Feriedag) = fridag(feriedag.dagen)
    override fun visitSykedag(sykedag: Sykedag) = sykedag(sykedag.dagen)
    override fun visitEgenmeldingsdag(egenmeldingsdag: Egenmeldingsdag) = if(egenmeldingsdag.erHelg()) fridag(egenmeldingsdag.dagen) else sykedag(egenmeldingsdag.dagen)
    override fun visitSykHelgedag(sykHelgedag: SykHelgedag) = fridag(sykHelgedag.dagen)
    override fun postVisitComposite(compositeSykdomstidslinje: CompositeSykdomstidslinje) {
        if(utbetalingslinjer.isNotEmpty()) {
            maksdato = alder.maksdato(betalteSykedager, betalteSykepengerEtter67, utbetalingslinjer.last().tom)
        }
    }

    private fun arbeidsdag(dagen: LocalDate) {
        //Siden telleren alltid er en dag bak dagen vi ser på, sjekker vi for < 16 i stedet for <= 16
        if (ikkeSykedager < 16) state.færreEllerLik16arbeidsdager(this, dagen) else state.merEnn16arbeidsdager(this, dagen)
    }

    private fun sykedag(dagen: LocalDate) {
        //Siden telleren alltid er en dag bak dagen vi ser på, sjekker vi for < 16 i stedet for <= 16
        if (sykedager < 16) state.færreEllerLik16Sykedager(this, dagen) else state.merEnn16Sykedager(this, dagen)
    }

    private fun fridag(dagen: LocalDate){
        state.fridag(this, dagen)
    }

    private fun opprettBetalingslinje(dagen: LocalDate) {
        if (burdeUtbetales(dagen)) {
            utbetalingslinjer.add(InternUtbetalingslinje(dagen, dagsats))
            betalteSykedager += 1
            if (alder.harFylt67(dagen)) {
                betalteSykepengerEtter67 +=1
            }
        }
    }

    private fun burdeUtbetales(dagen: LocalDate) = alder.navBurdeBetale(betalteSykedager, betalteSykepengerEtter67, dagen)

    override fun visitUtenlandsdag(utenlandsdag: Utenlandsdag) = state(Ugyldig)
    override fun visitUbestemt(ubestemtdag: Ubestemtdag) = state(Ugyldig)
    override fun visitStudiedag(studiedag: Studiedag) = state(Ugyldig)
    override fun visitPermisjonsdag(permisjonsdag: Permisjonsdag) = state(Ugyldig)

    abstract class UtbetalingState {
        open fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {}
        open fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {}
        open fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {}
        open fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {}
        open fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {}

        open fun entering(splitter: Utbetalingsberegner) {}
        open fun leaving(splitter: Utbetalingsberegner) {}
    }

    private object Initiell : UtbetalingState() {
        override fun entering(splitter: Utbetalingsberegner) {
            splitter.sykedager = 0
            splitter.ikkeSykedager = 0
            splitter.fridager = 0
        }

        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.sykedager = 1
            splitter.state(ArbeidsgiverperiodeSykedager)
        }

        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }
    }

    private object ArbeidsgiverperiodeSykedager : UtbetalingState() {
        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.sykedager += 1
        }

        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(UtbetalingSykedager).also { splitter.opprettBetalingslinje(dagen) }
        }

        override fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.fridager = 1
            splitter.state(Fri)
        }

        override fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager = 1
            splitter.state(ArbeidsgiverperiodeOpphold)
        }

        override fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }
    }

    private object ArbeidsgiverperiodeOpphold : UtbetalingState() {
        override fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager += 1
            if (splitter.ikkeSykedager == 16) splitter.state(Initiell)
        }

        override fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager += 1
        }

        override fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Initiell)
        }

        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.sykedager += 1
            splitter.state(ArbeidsgiverperiodeSykedager)
        }

        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.opprettBetalingslinje(dagen)
            splitter.state(UtbetalingSykedager)
        }
    }

    private object Fri : UtbetalingState() {
        override fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.fridager += 1
        }

        override fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager = (if (splitter.sykedager >= 16) 0 else splitter.fridager) + 1
            if (splitter.ikkeSykedager >= 16) splitter.state(Initiell) else splitter.state =
                ArbeidsgiverperiodeOpphold
        }

        override fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.sykedager += splitter.fridager + 1
            if (splitter.sykedager >= 16) splitter.state =
                UtbetalingSykedager.also { splitter.opprettBetalingslinje(dagen) }
            else splitter.state(ArbeidsgiverperiodeSykedager)
        }

        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(UtbetalingSykedager).also { splitter.opprettBetalingslinje(dagen) }
        }
    }

    private object UtbetalingSykedager : UtbetalingState() {
        override fun entering(splitter: Utbetalingsberegner) {
            splitter.ikkeSykedager = 0
        }

        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            if (splitter.burdeUtbetales(dagen)) {
                splitter.utbetalingslinjer.last().tom = dagen
                splitter.betalteSykedager += 1
                if (splitter.alder.harFylt67(dagen)) {
                    splitter.betalteSykepengerEtter67 +=1
                }
            }
        }

        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager = 1
            splitter.state(UtbetalingOpphold)
        }

        override fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(UtbetalingFri)
        }
    }

    private object UtbetalingFri: UtbetalingState() {
        override fun entering(splitter: Utbetalingsberegner) {
            splitter.fridager = 1
        }

        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.opprettBetalingslinje(dagen)
            splitter.state(UtbetalingSykedager)
        }

        override fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.fridager += 1
        }

        override fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager = splitter.fridager + 1
            splitter.state(if (splitter.ikkeSykedager >= 16) Initiell else UtbetalingOpphold)
        }

        override fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager = splitter.fridager + 1
            splitter.state(if (splitter.ikkeSykedager >= 16) Ugyldig else UtbetalingOpphold)
        }
    }

    private object UtbetalingOpphold: UtbetalingState() {
        override fun merEnn16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.opprettBetalingslinje(dagen)
            splitter.state(UtbetalingSykedager)
        }

        override fun færreEllerLik16Sykedager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Ugyldig)
        }

        override fun færreEllerLik16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager += 1
            if (splitter.ikkeSykedager == 16) splitter.state(Initiell)
        }

        override fun merEnn16arbeidsdager(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.state(Initiell)
        }

        override fun fridag(splitter: Utbetalingsberegner, dagen: LocalDate) {
            splitter.ikkeSykedager += 1
            if (splitter.ikkeSykedager == 16) splitter.state(Initiell)
        }
    }

    private object Ugyldig : UtbetalingState()
}