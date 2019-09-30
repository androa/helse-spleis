package no.nav.helse.sykdomstidslinje

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

abstract class Sykdomstidslinje {
    abstract fun startdato(): LocalDate
    abstract fun sluttdato(): LocalDate
    abstract fun antallSykedagerUtenHelg(): Int
    abstract fun antallSykedagerMedHelg(): Int
    abstract fun flatten(): List<Dag>
    abstract fun length(): Int
    abstract fun accept(visitor: SykdomstidslinjeVisitor)
    internal abstract fun sisteHendelse(): Sykdomshendelse
    internal abstract fun dag(dato: LocalDate, hendelse: Sykdomshendelse): Dag

    operator fun plus(other: Sykdomstidslinje): Sykdomstidslinje {
        if (this.startdato().isAfter(other.startdato())) return other + this

        val datesUntil = this.førsteStartdato(other).datesUntil(this.sisteSluttdato(other).plusDays(1)).toList()
        val intervalEtterKonflikter =
            datesUntil.map { this.beste(other, it) }.toList()

        return CompositeSykdomstidslinje(intervalEtterKonflikter.map { it.tilDag() })
    }

    fun antallDagerMellom(other: Sykdomstidslinje) =
        when {
            inneholder(other) -> -min(this.length(), other.length())
            harOverlapp(other) -> max(this.avstandMedOverlapp(other), other.avstandMedOverlapp(this))
            else -> min(this.avstand(other), other.avstand(this))
        }

    private fun beste(other: Sykdomstidslinje, dato: LocalDate): Dag {
        val dag = this.dag(dato, this.sisteHendelse())
        val otherDag = other.dag(dato, other.sisteHendelse())

        val (best, loser) = if (dag > otherDag) dag to otherDag else otherDag to dag

        best.erstatter(loser.dagerErstattet() + loser)
        return best
    }

    private fun førsteStartdato(other: Sykdomstidslinje) =
        if (this.startdato().isBefore(other.startdato())) this.startdato() else other.startdato()

    private fun sisteSluttdato(other: Sykdomstidslinje) =
        if (this.sluttdato().isAfter(other.sluttdato())) this.sluttdato() else other.sluttdato()

    private fun avstand(other: Sykdomstidslinje) =
        this.sluttdato().until(other.startdato(), ChronoUnit.DAYS).absoluteValue.toInt() - 1

    private fun avstandMedOverlapp(other: Sykdomstidslinje) =
        -(this.sluttdato().until(other.startdato(), ChronoUnit.DAYS).absoluteValue.toInt() + 1)

    private fun erDelAv(other: Sykdomstidslinje) =
        this.harBeggeGrenseneInnenfor(other) || other.harBeggeGrenseneInnenfor(this)

    private fun inneholder(other: Sykdomstidslinje) =
        this.harBeggeGrenseneInnenfor(other) || other.harBeggeGrenseneInnenfor(this)

    private fun harBeggeGrenseneInnenfor(other: Sykdomstidslinje) =
        this.startdato() in other.startdato()..other.sluttdato() && this.sluttdato() in other.startdato()..other.sluttdato()

    private fun harOverlapp(other: Sykdomstidslinje) = this.harGrenseInnenfor(other) || other.harGrenseInnenfor(this)

    private fun harGrenseInnenfor(other: Sykdomstidslinje) =
        this.startdato() in (other.startdato()..other.sluttdato())

    fun syketilfeller(): List<Sykdomstidslinje> {
        val stateMachine = ArbeidsdagStatemaskin(flatten())

        return stateMachine.getSyketilfeller()
    }

    fun trim(): Sykdomstidslinje {
        val days = flatten()
            .dropWhile { it.antallSykedagerUtenHelg() < 1 }
            .dropLastWhile { it.antallSykedagerUtenHelg() < 1 }
        return CompositeSykdomstidslinje(days)
    }

    companion object {
        fun sykedager(gjelder: LocalDate, hendelse: Sykdomshendelse) =
            if (erArbeidsdag(gjelder)) Sykedag(
                gjelder,
                hendelse
            ) else SykHelgedag(
                gjelder,
                hendelse
            )

        fun ferie(gjelder: LocalDate, hendelse: Sykdomshendelse) =
            if (erArbeidsdag(gjelder)) Feriedag(
                gjelder,
                hendelse
            ) else Helgedag(
                gjelder,
                hendelse
            )

        fun ikkeSykedag(gjelder: LocalDate, hendelse: Sykdomshendelse) =
            if (erArbeidsdag(gjelder)) Arbeidsdag(
                gjelder,
                hendelse
            ) else Helgedag(
                gjelder,
                hendelse
            )

        fun sykedager(fra: LocalDate, til: LocalDate, hendelse: Sykdomshendelse): Sykdomstidslinje {
            require(!fra.isAfter(til)) { "fra må være før eller lik til" }
            return CompositeSykdomstidslinje(fra.datesUntil(til.plusDays(1)).map {
                sykedager(
                    it,
                    hendelse
                )
            }.toList())
        }

        fun ferie(fra: LocalDate, til: LocalDate, hendelse: Sykdomshendelse): Sykdomstidslinje {
            require(!fra.isAfter(til)) { "fra må være før eller lik til" }
            return CompositeSykdomstidslinje(fra.datesUntil(til.plusDays(1)).map {
                ferie(
                    it,
                    hendelse
                )
            }.toList())
        }

        fun ikkeSykedager(fra: LocalDate, til: LocalDate, hendelse: Sykdomshendelse): Sykdomstidslinje {
            require(!fra.isAfter(til)) { "fra må være før eller lik til" }
            return CompositeSykdomstidslinje(fra.datesUntil(til.plusDays(1)).map {
                ikkeSykedag(
                    it,
                    hendelse
                )
            }.toList())
        }

        private fun erArbeidsdag(dato: LocalDate) =
            dato.dayOfWeek != DayOfWeek.SATURDAY && dato.dayOfWeek != DayOfWeek.SUNDAY

    }

    internal interface SykedagerTellerTilstand {
        fun visitArbeidsdag(dag: Arbeidsdag) {}
        fun visitSykdag(dag: Sykedag) {}
        fun visitHelgdag(dag: Helgedag) {}
        fun visitSykHelgedag(dag: SykHelgedag) {}
        fun visitFerie(dag: Feriedag) {}
    }

    internal class ArbeidsdagStatemaskin(dager: List<Dag>) {
        var state: SykedagerTellerTilstand = Starttilstand()
        var ikkeSykedager = 0
        private var syketilfelle = mutableListOf<Dag>()
        private val syketilfeller = mutableListOf<Sykdomstidslinje>()

        init {
            for (dag in dager) {
                nesteDag(dag)
            }
            updateSyketilfeller()
        }

        private fun nesteDag(dag: Dag) {
            syketilfelle.add(dag)
            when (dag) {
                is Arbeidsdag -> state.visitArbeidsdag(dag)
                is Sykedag -> state.visitSykdag(dag)
                is Helgedag -> state.visitHelgdag(dag)
                is SykHelgedag -> state.visitSykHelgedag(dag)
                is Feriedag -> state.visitFerie(dag)
                else -> error("Uhåndtert dag: ${dag::class}")
            }
        }

        private fun updateSyketilfeller() {
            syketilfeller.add(CompositeSykdomstidslinje(syketilfelle).trim())
        }

        fun nullstill() {
            ikkeSykedager = 0
            syketilfelle = mutableListOf()
        }

        internal inner class Starttilstand : SykedagerTellerTilstand {
            override fun visitSykdag(dag: Sykedag) {
                state = TellerSykedager()
                state.visitSykdag(dag)
            }

            override fun visitSykHelgedag(dag: SykHelgedag) {
                state = TellerSykedager()
                state.visitSykHelgedag(dag)
            }
        }

        internal inner class TellerSykedager : SykedagerTellerTilstand {
            override fun visitSykdag(dag: Sykedag) {
                ikkeSykedager = 0
            }

            override fun visitSykHelgedag(dag: SykHelgedag) {
                ikkeSykedager = 0
            }

            override fun visitArbeidsdag(dag: Arbeidsdag) {
                state = TellerIkkeSykedager()
                state.visitArbeidsdag(dag)
            }

            override fun visitHelgdag(dag: Helgedag) {
                state = TellerHelg()
                state.visitHelgdag(dag)
            }

            override fun visitFerie(dag: Feriedag) {
                state = FeriePåfølgendeSykdom()
                state.visitFerie(dag)
            }
        }

        internal inner class TellerIkkeSykedager : SykedagerTellerTilstand {
            override fun visitArbeidsdag(dag: Arbeidsdag) {
                tellIkkeSykedager()
            }

            override fun visitSykdag(dag: Sykedag) {
                state = TellerSykedager()
                state.visitSykdag(dag)
            }

            override fun visitHelgdag(dag: Helgedag) {
                state = TellerHelg()
                state.visitHelgdag(dag)
            }

            override fun visitSykHelgedag(dag: SykHelgedag) {
                state = TellerSykedager()
                state.visitSykHelgedag(dag)
            }

            override fun visitFerie(dag: Feriedag) {
                tellIkkeSykedager()
            }

            private fun tellIkkeSykedager() {
                ikkeSykedager++
                if (ikkeSykedager >= 16) {
                    ikkeSykedager = 0
                    state = Starttilstand()
                    updateSyketilfeller()
                    nullstill()
                }
            }
        }

        internal inner class FeriePåfølgendeSykdom : SykedagerTellerTilstand {
            override fun visitArbeidsdag(dag: Arbeidsdag) {
                state = TellerIkkeSykedager()
                state.visitArbeidsdag(dag)
            }

            override fun visitSykdag(dag: Sykedag) {
                state = TellerSykedager()
                state.visitSykdag(dag)
            }

            override fun visitSykHelgedag(dag: SykHelgedag) {
                state = TellerHelg()
                state.visitSykHelgedag(dag)
            }

            override fun visitHelgdag(dag: Helgedag) {
                state = TellerIkkeSykedager()
                state.visitHelgdag(dag)
            }
        }

        internal inner class TellerHelg : SykedagerTellerTilstand {
            override fun visitArbeidsdag(dag: Arbeidsdag) {
                ikkeSykedager += 2
                state = TellerIkkeSykedager()
                state.visitArbeidsdag(dag)
            }

            override fun visitSykdag(dag: Sykedag) {
                state = TellerSykedager()
                state.visitSykdag(dag)
            }
        }

        fun getSyketilfeller() = syketilfeller
    }

}
