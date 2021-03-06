package no.nav.helse.sykdomstidslinje.dag
import no.nav.helse.person.SykdomstidslinjeVisitor
import java.time.LocalDate

internal class Studiedag internal constructor(gjelder: LocalDate) : Dag(gjelder) {
    override fun accept(visitor: SykdomstidslinjeVisitor) {
        visitor.visitStudiedag(this)
    }

    override fun toString() = formatter.format(dagen) + "\tStudiedag"
}
