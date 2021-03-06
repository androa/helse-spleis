package no.nav.helse.sykdomstidslinje.dag
import no.nav.helse.person.SykdomstidslinjeVisitor
import java.time.LocalDate

internal class ImplisittDag internal constructor(gjelder: LocalDate) : Dag(gjelder) {
    override fun accept(visitor: SykdomstidslinjeVisitor) {
        visitor.visitImplisittDag(this)
    }

    override fun toString() = formatter.format(dagen) + "\tImplisitt dag"
}
