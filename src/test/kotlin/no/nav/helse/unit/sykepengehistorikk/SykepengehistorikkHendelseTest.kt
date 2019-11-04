package no.nav.helse.unit.sykepengehistorikk

import no.nav.helse.SpolePeriode
import no.nav.helse.TestConstants.sykepengehistorikk
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.sykepengehistorikk.SykepengehistorikkHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.*

class SykepengehistorikkHendelseTest {

    @Test
    fun `tidslinje fra tom sykepengehistorikk`() {
        val sykepengehistorikkHendelse = SykepengehistorikkHendelse(sykepengehistorikk(
                        perioder = emptyList(),
                        organisasjonsnummer = organisasjonsnummer,
                        aktørId = aktørId,
                        sakskompleksId = sakskompleksId
                ))

        assertNull(sykepengehistorikkHendelse.sykdomstidslinje())
    }

    @Test
    fun `tidslinje fra sykepengehistorikk med én periode`() {
        val sykepengehistorikkHendelse = SykepengehistorikkHendelse(sykepengehistorikk(
                perioder = listOf(
                        SpolePeriode(1.juni, 2.juni, "100")
                ),
                organisasjonsnummer = organisasjonsnummer,
                aktørId = aktørId,
                sakskompleksId = sakskompleksId
        ))

        assertEquals(2.juni, sykepengehistorikkHendelse.sykdomstidslinje()?.startdato())
        assertEquals(2.juni, sykepengehistorikkHendelse.sykdomstidslinje()?.sluttdato())
    }

    @Test
    fun `tidslinje fra sykepengehistorikk med flere periode`() {
        val sykepengehistorikkHendelse = SykepengehistorikkHendelse(sykepengehistorikk(
                perioder = listOf(
                        SpolePeriode(1.juni, 2.juni, "100"),
                        SpolePeriode(1.juli, 2.juli, "100")
                ),
                organisasjonsnummer = organisasjonsnummer,
                aktørId = aktørId,
                sakskompleksId = sakskompleksId
        ))

        assertEquals(2.juli, sykepengehistorikkHendelse.sykdomstidslinje()?.startdato())
        assertEquals(2.juli, sykepengehistorikkHendelse.sykdomstidslinje()?.sluttdato())
    }

    @Test
    fun `tidslinje fra sykepengehistorikk med overlappende perioder`() {
        val sykepengehistorikkHendelse = SykepengehistorikkHendelse(sykepengehistorikk(
                perioder = listOf(
                        SpolePeriode(1.juni, 2.juni, "100"),
                        SpolePeriode(1.juni, 3.juni, "100")
                ),
                organisasjonsnummer = organisasjonsnummer,
                aktørId = aktørId,
                sakskompleksId = sakskompleksId
        ))

        assertEquals(3.juni, sykepengehistorikkHendelse.sykdomstidslinje()?.startdato())
        assertEquals(3.juni, sykepengehistorikkHendelse.sykdomstidslinje()?.sluttdato())
    }

    private companion object {
        private val organisasjonsnummer = "123456789"
        private val aktørId = "987654321"
        private val sakskompleksId = UUID.randomUUID()
    }
}
