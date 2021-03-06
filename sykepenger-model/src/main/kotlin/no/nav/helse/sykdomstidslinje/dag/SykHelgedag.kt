package no.nav.helse.sykdomstidslinje.dag
import no.nav.helse.person.SykdomstidslinjeVisitor
import java.time.LocalDate

internal sealed class SykHelgedag(gjelder: LocalDate, grad: Double) : GradertDag(gjelder, grad) {

    override fun toString() = formatter.format(dagen) + "\tSykedag helg ($grad %)"

    internal class Søknad(gjelder: LocalDate, grad: Double) : SykHelgedag(gjelder, grad) {
        override fun accept(visitor: SykdomstidslinjeVisitor) {
            visitor.visitSykHelgedag(this)
        }
    }

    internal class Sykmelding(gjelder: LocalDate, grad: Double) : SykHelgedag(gjelder, grad) {
        override fun accept(visitor: SykdomstidslinjeVisitor) {
            visitor.visitSykHelgedag(this)
        }
    }
}
