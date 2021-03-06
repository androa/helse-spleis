package no.nav.helse.person

import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal fun tilUtbetaltEvent(
    aktørId: String,
    fødselsnummer: String,
    førsteFraværsdag: LocalDate,
    vedtaksperiodeId: UUID,
    utbetaling: Utbetaling,
    forbrukteSykedager: Int
) = OppdragMapper(
    aktørId = aktørId,
    fødselsnummer = fødselsnummer,
    førsteFraværsdag = førsteFraværsdag,
    vedtaksperiodeId = vedtaksperiodeId,
    utbetaling = utbetaling,
    forbrukteSykedager = forbrukteSykedager
).tilEvent()

private class OppdragMapper(
    private val aktørId: String,
    private val fødselsnummer: String,
    private val førsteFraværsdag: LocalDate,
    private val vedtaksperiodeId: UUID,
    private val utbetaling: Utbetaling,
    private val forbrukteSykedager: Int
) : UtbetalingVisitor {
    private lateinit var opprettet: LocalDateTime
    private val utbetalingslinjer = mutableListOf<PersonObserver.Utbetalingslinje>()

    internal fun tilEvent(): PersonObserver.UtbetaltEvent {
        utbetaling.accept(this)

        return PersonObserver.UtbetaltEvent(
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            førsteFraværsdag = førsteFraværsdag,
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingslinjer = utbetalingslinjer.toList(),
            forbrukteSykedager = forbrukteSykedager,
            opprettet = opprettet
        )
    }

    override fun preVisitUtbetaling(utbetaling: Utbetaling, tidsstempel: LocalDateTime) {
        opprettet = tidsstempel
    }

    override fun visitUtbetalingslinje(
        linje: Utbetalingslinje,
        fom: LocalDate,
        tom: LocalDate,
        dagsats: Int,
        grad: Double,
        delytelseId: Int,
        refDelytelseId: Int?,
        refFagsystemId: String?,
        endringskode: Endringskode
    ) {
        utbetalingslinjer.add(
            PersonObserver.Utbetalingslinje(
                fom = fom,
                tom = tom,
                dagsats = dagsats,
                grad = grad
            )
        )
    }
}
