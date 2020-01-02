package no.nav.helse.utbetalingstidslinje

import no.nav.helse.fixtures.*
import no.nav.helse.fixtures.NAV
import no.nav.helse.fixtures.UtbetalingstidslinjeInspektør
import no.nav.helse.fixtures.tidslinjeOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class TidslinjeShortenTest {
    private lateinit var inspektør: UtbetalingstidslinjeInspektør

    @Test internal fun `forkorte enkel utbetalingstidslinje`() {
        undersøke(tidslinjeOf(10.NAV).gjøreKortere(3.januar))
        Assertions.assertEquals(8, inspektør.size)
        Assertions.assertEquals(8, inspektør.navDagTeller)
    }

    @Test internal fun `ignorere forkort dato før utbetalingstidslinje startdato`() {
        undersøke(tidslinjeOf(10.NAV).gjøreKortere(1.januar.minusDays(5)))
        Assertions.assertEquals(10, inspektør.size)
        Assertions.assertEquals(10, inspektør.navDagTeller)
    }

    @Test internal fun `forkort dato etter utbetalingstidslinje sluttdato`() {
        undersøke(tidslinjeOf(10.NAV).gjøreKortere(11.januar))
        Assertions.assertEquals(0, inspektør.size)
    }

    @Test internal fun `forkorte dato på sluttdato`() {
        undersøke(tidslinjeOf(10.NAV).gjøreKortere(10.januar))
        Assertions.assertEquals(1, inspektør.size)
        Assertions.assertEquals(1, inspektør.navDagTeller)
    }

    @Test internal fun `forkorte dato på startdato`() {
        undersøke(tidslinjeOf(10.NAV).gjøreKortere(1.januar))
        Assertions.assertEquals(10, inspektør.size)
        Assertions.assertEquals(10, inspektør.navDagTeller)
    }

    private fun undersøke(tidslinje: Utbetalingstidslinje) {
        inspektør = UtbetalingstidslinjeInspektør(tidslinje).result()
    }
}