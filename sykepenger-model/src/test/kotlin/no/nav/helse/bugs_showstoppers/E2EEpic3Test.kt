package no.nav.helse.bugs_showstoppers

import no.nav.helse.e2e.AbstractEndToEndTest
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Søknad.Periode.Egenmelding
import no.nav.helse.hendelser.Søknad.Periode.Sykdom
import no.nav.helse.hendelser.SøknadArbeidsgiver
import no.nav.helse.hendelser.Utbetaling
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.TilstandType.*
import no.nav.helse.sykdomstidslinje.dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.dag.Egenmeldingsdag
import no.nav.helse.sykdomstidslinje.dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.dag.Sykedag
import no.nav.helse.testhelpers.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class E2EEpic3Test : AbstractEndToEndTest() {

    @Test
    internal fun `forlenger ikke vedtaksperiode som har gått til infotrygd`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterPåminnelse(0, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar,  23.februar, 100))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertTilstander(1, START, TIL_INFOTRYGD)
    }

    @Test
    internal fun `gradert sykmelding først`() {
        // ugyldig sykmelding lager en tom vedtaksperiode uten tidslinje, som overlapper med alt
        håndterSykmelding(Triple(3.januar(2020), 3.januar(2020), 50))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterSykmelding(Triple(13.januar(2020), 17.januar(2020), 100))
        håndterSøknad(Sykdom(13.januar(2020),  17.januar(2020), 100))
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `Søknad treffer flere perioder`() {
        håndterSykmelding(Triple(1.januar(2020), 5.januar(2020), 100))
        håndterSykmelding(Triple(6.januar(2020), 10.januar(2020), 100))
        håndterSykmelding(Triple(13.januar(2020), 17.januar(2020), 100))
        håndterSøknad(
            Sykdom(13.januar(2020),  17.januar(2020), 100),
            Egenmelding(30.desember(2019), 31.desember(2019))
        )
        håndterSykmelding(Triple(18.januar(2020), 26.januar(2020), 100))
        håndterSøknad(Sykdom(18.januar(2020),  26.januar(2020), 100))
        håndterSykmelding(Triple(27.januar(2020), 30.januar(2020), 100))
        håndterSøknad(Sykdom(27.januar(2020),  30.januar(2020), 100))
        håndterSykmelding(Triple(30.januar(2020), 14.februar(2020), 100))
        håndterSykmelding(Triple(30.januar(2020), 14.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(30.desember(2019), 31.desember(2019)),
                Periode(1.januar(2020), 5.januar(2020)),
                Periode(6.januar(2020), 10.januar(2020)),
                Periode(13.januar(2020), 16.januar(2020))
            ), førsteFraværsdag = 13.januar(2020)
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(30.desember(2019), 31.desember(2019)),
                Periode(1.januar(2020), 5.januar(2020)),
                Periode(6.januar(2020), 10.januar(2020)),
                Periode(13.januar(2020), 16.januar(2020))
            ), førsteFraværsdag = 13.januar(2020)
        )

        assertTilstander(0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE
        )
        assertTilstander(3,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE
        )
        assertTilstander(4,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE
        )
        Assertions.assertEquals(5, observatør.tilstander.size)
    }

    @Test
    internal fun `Ingen sykedager i tidslinjen - første fraværsdag bug`() {
        håndterSykmelding(Triple(6.januar(2020), 7.januar(2020), 100))
        håndterSykmelding(Triple(8.januar(2020), 10.januar(2020), 100))
        håndterSykmelding(Triple(27.januar(2020), 28.januar(2020), 100))

        håndterInntektsmelding(
            listOf(
                Periode(18.november(2019), 23.november(2019)),
                Periode(14.oktober(2019), 18.oktober(2019)),
                Periode(1.november(2019), 5.november(2019))
            ), 18.november(2019), listOf(
                Periode(5.desember(2019), 6.desember(2019)),
                Periode(30.desember(2019), 30.desember(2019)),
                Periode(2.januar(2020), 3.januar(2020)),
                Periode(22.januar(2020), 22.januar(2020))
            )
        )

        assertTilstander(0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE
        )
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `inntektsmelding starter etter sykmeldingsperioden`() {
        håndterSykmelding(Triple(15.januar(2020), 12.februar(2020), 100))
        håndterSøknad(Sykdom(15.januar(2020),  12.februar(2020), 100))
        håndterInntektsmelding(listOf(Periode(16.januar(2020), 31.januar(2020))), 16.januar(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(
            0,
            Triple(3.april(2019), 30.april(2019), 100),
            Triple(18.mars(2018), 2.april(2018), 100),
            Triple(29.november(2017), 3.desember(2017), 100),
            Triple(13.november(2017), 28.november(2017), 100)
        )
        håndterSimulering(0)
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    internal fun `periode uten sykedager`() {
        håndterSykmelding(Triple(3.januar, 4.januar, 100))
        håndterSykmelding(Triple(8.januar, 9.januar, 100))
        håndterSykmelding(Triple(15.januar, 16.januar, 100))
        håndterInntektsmeldingMedValidering(
            0,
            listOf(
                Periode(3.januar, 4.januar),
                Periode(15.januar, 16.januar)
            ),
            3.januar
        )

        håndterSøknadMedValidering(0, Sykdom(3.januar,  4.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVSLUTTET
        )

        håndterSøknadMedValidering(1, Sykdom(8.januar,  9.januar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVSLUTTET
        )

        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP
        )
    }

    @Test
    internal fun `enkeltstående sykedag i arbeidsgiverperiode-gap`() {
        håndterSykmelding(Triple(10.februar(2020), 12.februar(2020), 100))
        håndterSykmelding(Triple(14.februar(2020), 14.februar(2020), 100))
        håndterSykmelding(Triple(27.februar(2020), 28.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(10.februar(2020), 12.februar(2020)),
                Periode(27.februar(2020), 28.februar(2020))
            ),
            førsteFraværsdag = 27.februar(2020)
        )
        assertTilstander(0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP
        )
        assertTilstander(2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP
        )
    }

    @Test
    internal fun `Inntektsmelding med ferie etter arbeidsgiverperioden`() {
        håndterSykmelding(Triple(10.januar(2020), 21.januar(2020), 100))
        håndterSykmelding(Triple(23.januar(2020), 24.januar(2020), 100))
        håndterSøknad(Sykdom(23.januar(2020),  24.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(6.januar(2020), 21.januar(2020))),
            førsteFraværsdag = 23.januar(2020),
            ferieperioder = listOf(Periode(4.februar(2020), 5.februar(2020)))
        )

        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_UFERDIG_GAP
        )
    }

    @Test
    internal fun `ignorerer egenmeldingsdag i søknaden langt tilbake i tid`() {
        håndterSykmelding(Triple(6.januar(2020), 23.januar(2020), 100))
        håndterSøknad(
            Egenmelding(24.september(2019), 24.september(2019)), // ignored because it's too long ago relative to 6.januar
            Sykdom(6.januar(2020),  23.januar(2020), 100)
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(24.september(2019), 24.september(2019)),
                Periode(27.september(2019), 6.oktober(2019)),
                Periode(14.oktober(2019), 18.oktober(2019))
            ),
            førsteFraværsdag = 24.september(2019),
            ferieperioder = listOf(Periode(7.oktober(2019), 11.oktober(2019)))
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
    }

    @Test
    internal fun `person med gammel sykmelding`() {
        håndterSykmelding(Triple(13.januar(2020), 31.januar(2020), 100))
        håndterSykmelding(Triple(9.februar(2017), 15.februar(2017), 100))
        håndterSøknad(Sykdom(13.januar(2020),  31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(13.januar(2020), 21.januar(2020))
            ),
            førsteFraværsdag = 13.januar(2020)
        )
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)

        assertNotNull(inspektør.maksdato(1))

        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING)
    }

    @Test
    internal fun `periode som begynner på siste dag i arbeidsgiverperioden`() {
        håndterSykmelding(Triple(3.februar(2020), 17.februar(2020), 100))
        håndterSykmelding(Triple(18.februar(2020), 1.mars(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(3.februar(2020), 18.februar(2020))
            ),
            førsteFraværsdag = 3.januar(2020)
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_SØKNAD_UFERDIG_FORLENGELSE)
    }

    @Test
    internal fun `sykmeldinger som overlapper med gradering`() {
        håndterSykmelding(Triple(13.januar(2020), 28.januar(2020), 100)) // sykmelding A
        håndterSykmelding(Triple(13.januar(2020), 19.januar(2020), 80),
            Triple(20.januar(2020), 26.januar(2020), 100)) // sykmelding B (ignored)
        håndterSykmelding(Triple(27.januar(2020), 11.februar(2020), 100)) // sykmelding C (ignored)
        håndterSykmelding(Triple(10.februar(2020), 29.februar(2020), 100)) // sykmelding D
        håndterSøknad(Sykdom(27.januar(2020),  11.februar(2020), 100)) // søknad for sykemelding C (covers A & D actually)
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(13.januar(2020), 28.januar(2020))
            ),
            førsteFraværsdag = 13.januar(2020)
        ) // <-- error here
        håndterSøknad(Sykdom(10.februar(2020),  29.februar(2020), 100)) // søknad for sykmelding D (ignored)
        håndterSykmelding(Triple(1.mars(2020), 15.mars(2020), 100)) // sykmelding E
        assertEquals(3, inspektør.vedtaksperiodeTeller)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `sykmeldinger som overlapper`() {
        håndterSykmelding(Triple(15.januar(2020), 30.januar(2020), 100)) // sykmelding A, part 1
        håndterSykmelding(Triple(31.januar(2020), 15.februar(2020), 100)) // sykmelding A, part 2
        håndterSykmelding(Triple(16.januar(2020), 31.januar(2020), 100)) // sykmelding B
        håndterSykmelding(Triple(1.februar(2020), 16.februar(2020), 100)) // sykmelding C
        håndterSøknad(Sykdom(16.januar(2020),  31.januar(2020), 100)) // -> sykmelding B
        håndterSøknad(Sykdom(1.februar(2020),  16.februar(2020), 100)) // sykmelding C
        håndterSøknad(Sykdom(31.januar(2020),  15.februar(2020), 100)) // sykmelding A, part 2
        håndterSykmelding(Triple(18.februar(2020), 8.mars(2020), 100)) // sykmelding D
        assertEquals(3, inspektør.vedtaksperiodeTeller)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(15.januar(2020), 30.januar(2020))
            ),
            førsteFraværsdag = 15.januar(2020)
        ) // <-- error here
        håndterYtelser(0) // No history
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `overlapp i arbeidsgivertidslinjer`() {
        håndterSykmelding(Triple(7.januar(2020), 13.januar(2020), 100))
        håndterSykmelding(Triple(14.januar(2020), 24.januar(2020), 100))
        håndterSøknad(
            Egenmelding(6.januar(2020), 6.januar(2020)),
            Sykdom(14.januar(2020),  24.januar(2020), 100)
        )
        håndterSykmelding(Triple(25.januar(2020), 7.februar(2020), 80))
        håndterSykmelding(Triple(8.februar(2020), 28.februar(2020), 80))
        håndterSøknad(Sykdom(25.januar(2020),  7.februar(2020), 80))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(6.januar(2020), 21.januar(2020))
            ),
            førsteFraværsdag = 6.januar(2020)
        )
        håndterSykmelding(Triple(29.februar(2020), 11.mars(2020), 80))

        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0) // No history

        assertEquals(5, inspektør.vedtaksperiodeTeller)
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVSLUTTET)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, AVVENTER_UFERDIG_FORLENGELSE, AVVENTER_HISTORIKK)
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(3, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(4, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
    }

    @Test
    internal fun `ferie inni arbeidsgiverperioden`() {
        håndterSykmelding(Triple(21.desember(2019), 5.januar(2020), 80))
        håndterSøknad(
            Egenmelding(18.september(2019), 20.september(2019)),
            Sykdom(21.desember(2019),  5.januar(2020), 80)
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(18.september(2019), 20.september(2019)),
                Periode(21.september(2019), 22.september(2019)),
                Periode(23.september(2019), 30.september(2019)),
                Periode(1.oktober(2019), 2.oktober(2019)),
                Periode(8.oktober(2019), 8.oktober(2019)) // grad for 8. oktober is NaN
            ),
            ferieperioder = listOf(
                Periode(3.oktober(2019), 7.oktober(2019)),
                Periode(9.desember(2019), 23.desember(2019)),
                Periode(27.desember(2019), 27.desember(2019)),
                Periode(30.desember(2019), 30.desember(2019))
            ),
            førsteFraværsdag = 24.desember(2019)
        )
        // Sykedag beats IM Feriedag; 21 Desember to 5 Januar is another employer period!
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0) // No history

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertNotNull(inspektør.maksdato(0))
        assertTrue(inspektør.utbetalingslinjer(0).isEmpty())
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING)
    }

    @Test
    internal fun `Inntektsmelding, etter søknad, overskriver sykedager før arbeidsgiverperiode med arbeidsdager`() {
        håndterSykmelding(Triple(7.januar, 28.januar, 100))
        håndterSøknad(Sykdom(7.januar,  28.januar, 100))
        // Need to extend Arbeidsdag from first Arbeidsgiverperiode to beginning of Vedtaksperiode, considering weekends
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(9.januar, 24.januar)),
            ferieperioder = emptyList(),
            førsteFraværsdag = 9.januar
        )
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(3, inspektør.sykdomshistorikk.size)
        assertEquals(22, inspektør.sykdomshistorikk.sykdomstidslinje().length())
        assertEquals(7.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteDag())
        assertEquals(SykHelgedag.Søknad::class, inspektør.sykdomshistorikk.sykdomstidslinje()[7.januar]!!::class)
        assertEquals(Arbeidsdag.Inntektsmelding::class, inspektør.sykdomshistorikk.sykdomstidslinje()[8.januar]!!::class)
        assertEquals(9.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteFraværsdag())
        assertEquals(28.januar, inspektør.sykdomshistorikk.sykdomstidslinje().sisteDag())
    }

    @Test
    internal fun `Inntektsmelding, før søknad, overskriver sykedager før arbeidsgiverperiode med arbeidsdager`() {
        håndterSykmelding(Triple(7.januar, 28.januar, 100))
        // Need to extend Arbeidsdag from first Arbeidsgiverperiode to beginning of Vedtaksperiode, considering weekends
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(9.januar, 24.januar)),
            ferieperioder = emptyList(),
            førsteFraværsdag = 9.januar
        )
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(2, inspektør.sykdomshistorikk.size)
        assertEquals(22, inspektør.sykdomshistorikk.sykdomstidslinje().length())
        assertEquals(7.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteDag())
        assertEquals(SykHelgedag.Sykmelding::class, inspektør.sykdomshistorikk.sykdomstidslinje()[7.januar]!!::class)
        assertEquals(Arbeidsdag.Inntektsmelding::class, inspektør.sykdomshistorikk.sykdomstidslinje()[8.januar]!!::class)
        assertEquals(9.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteFraværsdag())
        assertEquals(28.januar, inspektør.sykdomshistorikk.sykdomstidslinje().sisteDag())
    }

    @Test
    internal fun `andre vedtaksperiode utbetalingslinjer dekker to perioder`() {
        håndterSykmelding(Triple(1.januar(2020), 31.januar(2020), 100))
        håndterSøknad(Sykdom(1.januar(2020),  31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar(2020), 16.januar(2020))),
            førsteFraværsdag = 1.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, Utbetaling.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Triple(1.februar(2020), 28.februar(2020), 100))
        håndterSøknad(Sykdom(1.februar(2020),  28.februar(2020), 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, Utbetaling.Oppdragstatus.AKSEPTERT)

        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))

        assertTilstander(0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(1,
            START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET)

        inspektør.also {
            assertEquals(1, it.utbetalingslinjer[0]?.size)
            assertEquals(17.januar(2020), it.utbetalingslinjer[0]?.first()?.fom)
            assertEquals(31.januar(2020), it.utbetalingslinjer[0]?.first()?.tom)
            assertEquals(1, it.utbetalingslinjer[1]?.size)
            assertEquals(3.februar(2020), it.utbetalingslinjer[1]?.first()?.fom) // starts mandag
            assertEquals(28.februar(2020), it.utbetalingslinjer[1]?.first()?.tom)
        }
    }

    @Test
    fun `simulering av periode der tilstøtende ikke ble utbetalt`() {
        håndterSykmelding(Triple(28.januar(2020), 10.februar(2020), 100))
        håndterSykmelding(Triple(11.februar(2020), 21.februar(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(28.januar(2020), 10.februar(2020), 100))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(28.januar(2020), 12.februar(2020))),
            førsteFraværsdag = 28.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(1)   // No history

        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVSLUTTET_UTEN_UTBETALING, AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD, AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, AVVENTER_UFERDIG_FORLENGELSE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        assertNull(inspektør.utbetalingsreferanse(0))
        val utbetalingsreferanse = inspektør.utbetalingsreferanse(1) ?: fail { "utbetalingsreferanse er forventet å være satt" }
        assertEquals(utbetalingsreferanse, inspektør.etterspurteBehov(1, Behovtype.Simulering, "utbetalingsreferanse"))
        assertEquals(false, inspektør.etterspurteBehov(1, Behovtype.Simulering, "forlengelse"))
    }

    @Test
    fun `simulering av periode der tilstøtende ble utbetalt`() {
        håndterSykmelding(Triple(17.januar(2020), 10.februar(2020), 100))
        håndterSykmelding(Triple(11.februar(2020), 21.februar(2020), 100))
        håndterSøknad(Sykdom(17.januar(2020), 10.februar(2020), 100))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(17.januar(2020), 2.februar(2020))),
            førsteFraværsdag = 18.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, Utbetaling.Oppdragstatus.AKSEPTERT)
        håndterYtelser(1)   // No history

        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        val utbetalingsreferanse = inspektør.utbetalingsreferanse(0) ?: fail { "utbetalingsreferanse er forventet å være satt" }
        assertEquals(utbetalingsreferanse, inspektør.utbetalingsreferanse(1))
        assertEquals(utbetalingsreferanse, inspektør.etterspurteBehov(0, Behovtype.Simulering, "utbetalingsreferanse"))
        assertEquals(utbetalingsreferanse, inspektør.etterspurteBehov(1, Behovtype.Simulering, "utbetalingsreferanse"))
        assertEquals(false, inspektør.etterspurteBehov(0, Behovtype.Simulering, "forlengelse"))
        assertEquals(true, inspektør.etterspurteBehov(1, Behovtype.Simulering, "forlengelse"))
    }

    @Test
    internal fun `dobbeltbehandling av første periode aborterer behandling av andre periode`() {
        håndterSykmelding(Triple(1.januar(2020), 31.januar(2020), 100))
        håndterSøknad(Sykdom(1.januar(2020),  31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar(2020), 16.januar(2020))),
            førsteFraværsdag = 1.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, Utbetaling.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Triple(1.februar(2020), 28.februar(2020), 100))
        håndterSøknad(Sykdom(1.februar(2020),  28.februar(2020), 100))
        håndterYtelser(1, Triple(17.januar(2020), 31.januar(2020), 1400))   // Duplicate processing

        assertTilstander(0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(1,
            START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, TIL_INFOTRYGD)
    }

    @Test
    internal fun `helg i gap i arbeidsgiverperioden`() {
        håndterSykmelding(Triple(3.januar, 10.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 4.januar), Periode(9.januar, 10.januar)), 3.januar)
        håndterSøknad(Sykdom(3.januar, 10.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        inspektør.also {
            assertEquals(4, it.dagtelling[Sykedag::class])
            assertEquals(2, it.dagtelling[SykHelgedag::class])
            assertEquals(2, it.dagtelling[Arbeidsdag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `grad er NaN`() {
        håndterSykmelding(Triple(10.februar(2020), 16.februar(2020), 100))
        // egenmelding: 28. januar og 6.-7. februar
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(10.februar(2020), 16.februar(2020), 100))
        håndterSykmelding(Triple(24.februar(2020), 29.februar(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(24.februar(2020), 29.februar(2020), 100))
        håndterSykmelding(
            Triple(1.mars(2020), 10.mars(2020), 100),
            Triple(11.mars(2020), 21.mars(2020), 50)
        )
        håndterSykmelding(Triple(13.mars(2020), 31.mars(2020), 100))
        val arbeidsgiverperioder = listOf(
            Periode(28.januar(2020), 28.januar(2020)),
            Periode(6.februar(2020), 16.februar(2020)),
            Periode(24.februar(2020), 27.februar(2020))
        )
        val ferieperioder = listOf(Periode(17.februar(2020), 21.februar(2020)))
        håndterInntektsmelding(arbeidsgiverperioder, 28.januar(2020), ferieperioder)
        håndterInntektsmelding(arbeidsgiverperioder, 6.februar(2020), ferieperioder);
        håndterInntektsmelding(arbeidsgiverperioder, 24.februar(2020), ferieperioder)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterSøknad(Sykdom(13.mars(2020), 31.mars(2020), 100))
        håndterYtelser(2)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVSLUTTET_UTEN_UTBETALING, AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD, AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVSLUTTET_UTEN_UTBETALING, AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING)
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    internal fun `Egenmelding i søknad overstyres av inntektsmelding når IM mottas først`() {
        håndterSykmelding(Triple(20.februar(2020), 8.mars(2020), 100))
        håndterInntektsmelding(listOf(Periode(20.februar(2020), 5.mars(2020))), 20.februar(2020))
        håndterSøknad(Egenmelding(17.februar(2020), 19.februar(2020)), Sykdom(20.februar(2020), 5.mars(2020), 100))

        inspektør.also {
            assertNull(it.dagtelling[Egenmeldingsdag::class])
        }
    }

    @Test
    internal fun `Egenmelding i søknad overstyres av inntektsmelding når IM mottas sist`() {
        håndterSykmelding(Triple(20.februar(2020), 8.mars(2020), 100))
        håndterSøknad(Egenmelding(17.februar(2020), 19.februar(2020)), Sykdom(20.februar(2020), 5.mars(2020), 100))
        håndterInntektsmelding(listOf(Periode(20.februar(2020), 5.mars(2020))), 20.februar(2020))

        inspektør.also {
            assertNull(it.dagtelling[Egenmeldingsdag::class])
        }
    }

}
