package no.nav.helse.sykdomstidslinje.dag
import no.nav.helse.person.SykdomstidslinjeVisitor
import java.time.LocalDate

internal sealed class Sykedag(gjelder: LocalDate, grad: Double) : GradertDag(gjelder, grad) {

    override fun toString() = formatter.format(dagen) + "\tSykedag ($grad %)"

    class Sykmelding(gjelder: LocalDate, grad: Double) : Sykedag(gjelder, grad) {
        override fun accept(visitor: SykdomstidslinjeVisitor) {
            visitor.visitSykedag(this)
        }
    }

    class Søknad(gjelder: LocalDate, grad: Double) : Sykedag(gjelder, grad) {
        override fun accept(visitor: SykdomstidslinjeVisitor) {
            visitor.visitSykedag(this)
        }
    }
}
