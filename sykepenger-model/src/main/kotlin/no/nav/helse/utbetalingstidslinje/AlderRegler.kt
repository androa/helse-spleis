package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek
import java.time.LocalDate

internal class AlderRegler(fødselsnummer: String,
                           private val startDato: LocalDate,
                           private val sluttDato: LocalDate,
                           private val arbeidsgiverRegler: ArbeidsgiverRegler) {

    private val maksSykepengedager = arbeidsgiverRegler.maksSykepengedager()
    private val maksSykepengedagerEtter67 = 60
    private val individnummer = fødselsnummer.substring(6, 9).toInt()
    private val fødselsdag = LocalDate.of(
        fødselsnummer.substring(4, 6).toInt().toYear(individnummer),
        fødselsnummer.substring(2, 4).toInt(),
        fødselsnummer.substring(0, 2).toInt().toDay()
    )
    private val øvreAldersgrense = fødselsdag.plusYears(70)
    private val redusertYtelseAlder = fødselsdag.plusYears(67)

    internal val navBurdeBetale
        get(): BurdeBetale {
            return when {
                redusertYtelseAlder.isAfter(sluttDato) ->
                    { antallDager: Int, _: Int, _: LocalDate -> antallDager < maksSykepengedager }
                øvreAldersgrense.isBefore(startDato) ->
                    { _: Int, _: Int, _: LocalDate -> false }
                redusertYtelseAlder.isAfter(startDato) ->
                    { antallDager: Int, antallDagerEtter60: Int, dagen: LocalDate ->
                        fyller67IPerioden(
                            antallDager,
                            antallDagerEtter60,
                            dagen
                        )
                    }
                else -> { antallDager: Int, _: Int, dagen: LocalDate ->
                    antallDager < maksSykepengedagerEtter67 && dagen.isBefore(
                        øvreAldersgrense
                    )
                }
            }
        }

    private fun Int.toDay() = if (this > 40) this - 40 else this
    private fun Int.toYear(individnummer: Int): Int {
        return this + when {
            this in (54..99) && individnummer in (500..749) -> 1800
            this in (0..99) && individnummer in (0..499) -> 1900
            this in (40..99) && individnummer in (900..999) -> 1900
            else -> 2000
        }
    }

    private fun fyller67IPerioden(antallDager: Int, antallDagerEtter67: Int, dagen: LocalDate): Boolean {
        if (dagen.isBefore(redusertYtelseAlder) && antallDager < maksSykepengedager) return true
        return antallDager < maksSykepengedager && antallDagerEtter67 < maksSykepengedagerEtter67
    }

    internal fun harFylt67(dagen: LocalDate) = dagen.isAfter(redusertYtelseAlder)

    internal fun maksdato(antallDager: Int, gammelpersonDager: Int, sisteUtbetalingsdag: LocalDate?): LocalDate {
        val aldersgrense = if (harFylt67(sisteDag(sisteUtbetalingsdag))) øvreAldersgrense.minusDays(1)
            else redusertYtelseAlder.addWeekdays(maksSykepengedagerEtter67)

        return listOf(
            sisteDag(sisteUtbetalingsdag).addWeekdays(gjenståendeDager(antallDager, gammelpersonDager, sisteDag(sisteUtbetalingsdag))),
            aldersgrense
        ).min()!!
    }

    private fun sisteDag(sisteUtbetalingsdag: LocalDate?) = (sisteUtbetalingsdag ?: sluttDato)

    private fun LocalDate.addWeekdays(gjenståendeAntallUkedager: Int): LocalDate {
        val virkedagerIEnUke = 5
        val dagerIEnUke = 7

        val heleUkerIgjen = gjenståendeAntallUkedager / virkedagerIEnUke
        val heleUkerIDager = heleUkerIgjen * dagerIEnUke

        val gjenståendeDagerISisteUke = gjenståendeAntallUkedager % virkedagerIEnUke

        return this
            .trimHelg()
            .plusDays((heleUkerIDager).toLong())
            .leggTilGjenståendeDager(gjenståendeDagerISisteUke)
    }

    internal fun gjenståendeDager(antallDager: Int, antallDagerEtter67: Int, sisteUtbetalingsdag: LocalDate?): Int {
        return if (harFylt67(sisteDag(sisteUtbetalingsdag))) {
            maksSykepengedagerEtter67 - antallDagerEtter67
        } else maksSykepengedager - antallDager
    }

    private fun LocalDate.leggTilGjenståendeDager(gjenståendeDagerISisteUke: Int) =
        (0..gjenståendeDagerISisteUke + 2)
            .map { plusDays(it.toLong()) }
            .filterNot { it.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
            .get(gjenståendeDagerISisteUke)

    private fun LocalDate.trimHelg() = when (dayOfWeek) {
        DayOfWeek.SATURDAY -> minusDays(1)
        DayOfWeek.SUNDAY -> minusDays(2)
        else -> this
    }

}

internal typealias BurdeBetale = (Int, Int, LocalDate) -> Boolean
