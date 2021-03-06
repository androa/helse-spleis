package no.nav.helse.sykdomstidslinje.dag
import no.nav.helse.person.SykdomstidslinjeVisitor
import java.time.LocalDate

internal sealed class Feriedag(gjelder: LocalDate) : Dag(gjelder) {

    override fun toString() = formatter.format(dagen) + "\tFerie"

    class Søknad(gjelder: LocalDate) : Feriedag(gjelder) {
        override fun accept(visitor: SykdomstidslinjeVisitor) {
            visitor.visitFeriedag(this)
        }
    }

    class Inntektsmelding(gjelder: LocalDate) : Feriedag(gjelder) {
        override fun accept(visitor: SykdomstidslinjeVisitor) {
            visitor.visitFeriedag(this)
        }
    }
}
