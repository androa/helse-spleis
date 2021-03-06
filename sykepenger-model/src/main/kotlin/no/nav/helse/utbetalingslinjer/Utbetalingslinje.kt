package no.nav.helse.utbetalingslinjer

import no.nav.helse.person.OppdragVisitor
import no.nav.helse.sykdomstidslinje.dag.erHelg
import no.nav.helse.utbetalingslinjer.Endringskode.*
import no.nav.helse.utbetalingslinjer.Klassekode.RefusjonIkkeOpplysningspliktig
import java.time.LocalDate
import kotlin.streams.toList

internal class Utbetalingslinje internal constructor(
    internal var fom: LocalDate,
    internal var tom: LocalDate,
    internal var dagsats: Int,
    internal val grad: Double,
    internal var refFagsystemId: String? = null,
    private var delytelseId: Int = 1,
    private var refDelytelseId: Int? = null,
    private var endringskode: Endringskode = NY,
    private var klassekode: Klassekode = RefusjonIkkeOpplysningspliktig
) {

    internal fun accept(visitor: OppdragVisitor) {
        visitor.visitUtbetalingslinje(
            this,
            fom,
            tom,
            dagsats,
            grad,
            delytelseId,
            refDelytelseId,
            refFagsystemId,
            endringskode
        )
    }

    internal fun linkTo(other: Utbetalingslinje) {
        this.delytelseId = other.delytelseId + 1
        this.refDelytelseId = other.delytelseId
    }

    internal fun totalbeløp() = dagsats * antallDager()

    internal fun dager() = fom
        .datesUntil(tom.plusDays(1))
        .filter { !it.erHelg() }
        .toList()

    private fun antallDager() = dager().size

    override fun equals(other: Any?) = other is Utbetalingslinje && this.equals(other)

    private fun equals(other: Utbetalingslinje) =
        this.fom == other.fom &&
            this.tom == other.tom &&
            this.dagsats == other.dagsats &&
            this.grad == other.grad

    internal fun kunTomForskjelligFra(other: Utbetalingslinje) =
        this.fom == other.fom &&
            this.dagsats == other.dagsats &&
            this.grad == other.grad

    override fun hashCode(): Int {
        return fom.hashCode() * 37 +
            tom.hashCode() * 17 +
            dagsats.hashCode() * 41 +
            grad.hashCode()
    }

    internal fun ghostFrom(tidligere: Utbetalingslinje) = copyWith(UEND, tidligere)

    internal fun utvidTom(tidligere: Utbetalingslinje) = copyWith(ENDR, tidligere)

    private fun copyWith(linjetype: Endringskode, tidligere: Utbetalingslinje) {
        this.refFagsystemId = tidligere.refFagsystemId
        this.delytelseId = tidligere.delytelseId
        this.refDelytelseId = tidligere.refDelytelseId
        this.klassekode = tidligere.klassekode
        this.endringskode = linjetype
    }

    internal fun erForskjell() = endringskode != UEND

    internal fun deletion(fagsystemId: String, fom: LocalDate, tom: LocalDate) =
        Utbetalingslinje(fom, tom, 0, 0.0, fagsystemId, delytelseId + 1, delytelseId, OPPH)
}

internal enum class Endringskode {
    NY, UEND, ENDR, OPPH
}

internal enum class Klassekode(internal val verdi: String) {
    RefusjonIkkeOpplysningspliktig(verdi = "SPREFAG-IOP");

    companion object {
        private val map = values().associateBy(Klassekode::verdi)
        fun from(verdi: String) = map[verdi] ?: throw IllegalArgumentException("Støtter ikke klassekode: $verdi")
    }
}

internal enum class Fagområde(private val linjerStrategy: (Utbetaling) -> Oppdrag) {
    SPREF(Utbetaling::arbeidsgiverOppdrag),
    SP(Utbetaling::personOppdrag);

    internal fun utbetalingslinjer(utbetaling: Utbetaling): Oppdrag =
        linjerStrategy(utbetaling)
}
