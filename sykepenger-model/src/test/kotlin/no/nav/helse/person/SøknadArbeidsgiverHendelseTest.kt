package no.nav.helse.person

import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.SøknadArbeidsgiver
import no.nav.helse.hendelser.SøknadArbeidsgiver.Periode.Sykdom
import no.nav.helse.person.TilstandType.*
import no.nav.helse.spleis.e2e.TestPersonInspektør
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class SøknadArbeidsgiverHendelseTest {

    companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
    }

    private lateinit var person: Person
    private val inspektør get() = TestPersonInspektør(person)

    @BeforeEach
    internal fun opprettPerson() {
        person = Person("12345", UNG_PERSON_FNR_2018)
    }

    @Test
    internal fun `søknad matcher sykmelding`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
        assertEquals(5, inspektør.sykdomstidslinje(0).length())
    }

    @Test
    internal fun `sykdomsgrad ikke 100`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 50)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
    }

    @Test
    internal fun `mangler Sykmelding`() {
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertTrue(inspektør.personLogg.hasErrors())
        assertEquals(0, inspektør.vedtaksperiodeTeller)
    }

    @Test
    internal fun `andre søknad ugyldig`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertTrue(inspektør.personLogg.hasWarnings())
        assertFalse(inspektør.personLogg.hasErrors(), inspektør.personLogg.toString())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
    }

    @Test
    internal fun `ignorer andre sendt søknad`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        person.håndter(søknad(Søknad.Periode.Sykdom(1.januar, 5.januar, 100, 100)))
        assertTrue(inspektør.personLogg.hasWarnings())
        assertFalse(inspektør.personLogg.hasErrors(), inspektør.personLogg.toString())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
    }


    @Test
    internal fun `ignorer andre søknad til arbeidsgiver`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknad(Søknad.Periode.Sykdom(1.januar, 5.januar, 100, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertTrue(inspektør.personLogg.hasWarnings())
        assertFalse(inspektør.personLogg.hasErrors(), inspektør.personLogg.toString())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(AVVENTER_GAP, inspektør.sisteTilstand(0))
    }

    @Test
    internal fun `To søknader uten overlapp`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(sykmelding(Triple(6.januar, 10.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(6.januar, 10.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
        assertEquals(5, inspektør.sykdomstidslinje(0).length())
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(1))
        assertEquals(5, inspektør.sykdomstidslinje(1).length())
    }
    @Test
    internal fun `To søknader med opphold`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(sykmelding(Triple(15.januar, 19.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(15.januar, 19.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
        assertEquals(5, inspektør.sykdomstidslinje(0).length())
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(1))
        assertEquals(5, inspektør.sykdomstidslinje(1).length())
    }

    @Test
    internal fun `forlengelse etter avsluttet periode`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        person.håndter(sykmelding(Triple(6.januar, 10.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(6.januar, 10.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
        assertEquals(5, inspektør.sykdomstidslinje(0).length())
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(1))
        assertEquals(5, inspektør.sykdomstidslinje(1).length())
    }

    @Test
    internal fun `gjenopptar første periode etter avslutting av avsluttet periode`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(sykmelding(Triple(6.januar, 10.januar, 100)))
        assertEquals(MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, inspektør.sisteTilstand(1))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
        assertEquals(5, inspektør.sykdomstidslinje(0).length())
        assertEquals(MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, inspektør.sisteTilstand(1))
        assertEquals(5, inspektør.sykdomstidslinje(1).length())
    }

    @Test
    internal fun `avslutter andre periode før første periode behandles`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(sykmelding(Triple(6.januar, 10.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(6.januar, 10.januar, 100)))
        person.håndter(søknad(Søknad.Periode.Sykdom(1.januar, 5.januar, 100, 100)))
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertEquals(AVVENTER_GAP, inspektør.sisteTilstand(0))
        assertEquals(5, inspektør.sykdomstidslinje(0).length())
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(1))
        assertEquals(5, inspektør.sykdomstidslinje(1).length())
    }

    @Test
    internal fun `Sykmelding med overlapp på en periode`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100)))
        person.håndter(søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100)))
        person.håndter(sykmelding(Triple(4.januar, 10.januar, 100)))
        assertTrue(inspektør.personLogg.hasWarnings())
        assertFalse(inspektør.personLogg.hasErrors())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(AVSLUTTET_UTEN_UTBETALING, inspektør.sisteTilstand(0))
    }

    @Test
    internal fun `to forskjellige arbeidsgivere er ikke støttet`() {
        person.håndter(sykmelding(Triple(1.januar, 5.januar, 100), orgnummer = "orgnummer1"))
        person.håndter(
            søknadArbeidsgiver(Sykdom(1.januar, 5.januar, 100), orgnummer = "orgnummer2")
        )
        assertTrue(inspektør.personLogg.hasErrors())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(TIL_INFOTRYGD, inspektør.sisteTilstand(0))
    }

    private fun søknad(vararg perioder: Søknad.Periode, orgnummer: String = "987654321") =
        Søknad(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "12345",
            orgnummer = orgnummer,
            perioder = listOf(*perioder),
            harAndreInntektskilder = false,
            sendtTilNAV = Søknad.Periode.søknadsperiode(perioder.toList())!!.endInclusive.atStartOfDay(),
            permittert = false
        )

    private fun søknadArbeidsgiver(vararg perioder: SøknadArbeidsgiver.Periode, orgnummer: String = "987654321") =
        SøknadArbeidsgiver(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "12345",
            orgnummer = orgnummer,
            perioder = listOf(*perioder)
        )

    private fun sykmelding(vararg sykeperioder: Triple<LocalDate, LocalDate, Int>, orgnummer: String = "987654321") =
        Sykmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "12345",
            orgnummer = orgnummer,
            sykeperioder = listOf(*sykeperioder)
        )
}
