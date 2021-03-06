package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Periode.Sykdom
import no.nav.helse.hendelser.SøknadArbeidsgiver
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.TilstandType.*
import no.nav.helse.sykdomstidslinje.dag.KunArbeidsgiverSykedag
import no.nav.helse.sykdomstidslinje.dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.dag.Sykedag
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class KunEnArbeidsgiverTest : AbstractEndToEndTest() {

    @Test
    fun `ingen historie med inntektsmelding først`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `ingen historie med søknad til arbeidsgiver først`() {
        håndterSykmelding(Triple(3.januar, 8.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(3.januar,  8.januar, 100))
        assertNoWarnings(inspektør)
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[KunArbeidsgiverSykedag::class])
            assertEquals(2, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
    }

    @Test
    fun `ingen historie med to søknader til arbeidsgiver før inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 5.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(3.januar,  5.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 5.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)

        håndterSykmelding(Triple(8.januar, 10.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(8.januar,  10.januar, 100))

        håndterSykmelding(Triple(11.januar, 22.januar, 100))
        håndterSøknad(Sykdom(11.januar,  22.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(1, INNTEKT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(6, it.dagtelling[KunArbeidsgiverSykedag::class])
            assertEquals(4, it.dagtelling[SykHelgedag::class])
            assertEquals(8, it.dagtelling[Sykedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med to søknader til arbeidsgiver først`() {
        håndterSykmelding(Triple(3.januar, 5.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(3.januar,  5.januar, 100))

        håndterSykmelding(Triple(8.januar, 10.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(8.januar,  10.januar, 100))

        håndterSykmelding(Triple(11.januar, 22.januar, 100))
        håndterSøknad(Sykdom(11.januar,  22.januar, 100))

        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(6, it.dagtelling[KunArbeidsgiverSykedag::class])
            assertEquals(4, it.dagtelling[SykHelgedag::class])
            assertEquals(8, it.dagtelling[Sykedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVSLUTTET_UTEN_UTBETALING,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med to søknader (med gap mellom) til arbeidsgiver først`() {
        håndterSykmelding(Triple(3.januar, 4.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(3.januar,  4.januar, 100))

        håndterSykmelding(Triple(8.januar, 10.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(8.januar,  10.januar, 100))

        håndterSykmelding(Triple(11.januar, 22.januar, 100))
        håndterSøknad(Sykdom(11.januar,  22.januar, 100))

        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 4.januar), Periode(8.januar, 21.januar)), førsteFraværsdag = 8.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(5, it.dagtelling[KunArbeidsgiverSykedag::class])
            assertEquals(4, it.dagtelling[SykHelgedag::class])
            assertEquals(8, it.dagtelling[Sykedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med inntektsmelding, så søknad til arbeidsgiver`() {
        håndterSykmelding(Triple(3.januar, 8.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        assertNoWarnings(inspektør)
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(3.januar,  8.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[KunArbeidsgiverSykedag::class])
            assertEquals(2, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
    }

    @Test
    fun `ingen historie med Søknad først`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `søknad sendt etter 3 mnd`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknad(Sykdom(3.januar,  26.januar, 100), sendtTilNav = 1.mai)
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterManuellSaksbehandling(0, true)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertNull(it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
            assertDoesNotThrow { it.arbeidsgiver.nåværendeTidslinje() }
            assertTrue(it.utbetalingslinjer(0).isEmpty())
            TestTidslinjeInspektør(it.utbetalingstidslinjer(0)).also { tidslinjeInspektør ->
                assertEquals(7, tidslinjeInspektør.dagtelling[ForeldetDag::class])
                assertEquals(2, tidslinjeInspektør.dagtelling[NavHelgDag::class])
                assertEquals(16, tidslinjeInspektør.dagtelling[ArbeidsgiverperiodeDag::class])
                assertEquals(1, tidslinjeInspektør.dagtelling[Arbeidsdag::class])
            }
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
    }

    @Test
    fun `gap historie før inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterYtelser(0, Triple(1.desember(2017), 15.desember(2017), 15000))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `no-gap historie før inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterYtelser(0, Triple(1.desember(2017), 16.desember(2017), 15000))
        inspektør.also {
            assertTrue(it.personLogg.hasErrors())
            assertMessages(it)
            assertTrue(it.inntekter.isEmpty())
            assertNull(it.inntektshistorikk.inntekt(2.januar))
            assertEquals(2, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `ingen nav utbetaling kreves, blir automatisk behandlet og avsluttet`() {
        håndterSykmelding(Triple(3.januar, 5.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  5.januar, 100))
        håndterYtelser(0)   // No history
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertFalse(hendelselogg.hasErrors())

        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        assertTrue(person.aktivitetslogg.logg(inspektør.vedtaksperioder(0)).hasOnlyInfoAndNeeds())
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVSLUTTET
        )
    }

    @Test
    fun `ikke automatisk behandling hvis warnings`() {
        håndterSykmelding(Triple(3.januar, 5.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  5.januar, 100))
        håndterSøknad(Sykdom(3.januar,  5.januar, 100))
        håndterYtelser(0)   // No history
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertFalse(hendelselogg.hasErrors())

        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        assertFalse(person.aktivitetslogg.logg(inspektør.vedtaksperioder(0)).hasOnlyInfoAndNeeds())
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `To perioder med opphold`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Kiler tilstand i uferdig venter for inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterYtelser(1)   // No history
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Kilt etter søknad og inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_UFERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Kilt etter inntektsmelding og søknad`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_SØKNAD_UFERDIG_GAP, AVVENTER_UFERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `Kilt etter inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `beregning av utbetaling ignorerer tidligere ugyldige perioder`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterYtelser(0, Triple(1.januar, 2.januar, 15000)) // -> TIL_INFOTRYGD

        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)), 1.februar)
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)

        assertNotNull(inspektør.maksdato(1))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, TIL_INFOTRYGD)
        assertTilstander(
            1,
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
    fun `første fraværsdato fra inntektsmelding er ulik utregnet første fraværsdato`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 4.januar)
        inspektør.also { assertTrue(it.personLogg.hasWarnings()) }
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
    }

    @Test
    fun `første fraværsdato fra inntektsmelding er ulik utregnet første fraværsdato for påfølgende perioder`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(27.januar, 7.februar, 100))
        håndterInntektsmeldingMedValidering(
            0,
            listOf(Periode(3.januar, 18.januar)),
            3.januar,
            listOf(Periode(27.januar, 27.januar))
        )
        inspektør.also { assertFalse(it.personLogg.hasWarnings()) }
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_SØKNAD_UFERDIG_FORLENGELSE)
    }

    @Test
    fun `første fraværsdato i inntektsmelding er utenfor perioden`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 27.januar)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
    }

    @Test
    fun `første fraværsdato i inntektsmelding, før søknad, er utenfor perioden`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 27.januar)
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP
        )
    }

    @Test
    fun `Sammenblandede hendelser fra forskjellige perioder med søknad først`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        forventetEndringTeller++
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_UFERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder søknad først`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar,  23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)

        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }


    @Test
    fun `Venter å på bli kilt etter søknad og inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 7.januar, 100))
        håndterSykmelding(Triple(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  7.januar, 100))
        håndterSøknadMedValidering(1, Sykdom(8.januar,  23.februar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        håndterManuellSaksbehandling(0, true)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `søknad til arbeidsgiver etter inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 7.januar, 100))
        håndterSykmelding(Triple(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Periode.Sykdom(8.januar,  23.februar, 100))

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(35, it.dagtelling[KunArbeidsgiverSykedag::class])
            assertEquals(14, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD
        )
    }

    @Test
    fun `Venter på å bli kilt etter inntektsmelding`() {
        håndterSykmelding(Triple(3.januar, 7.januar, 100))
        håndterSykmelding(Triple(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  7.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterManuellSaksbehandling(0, true)

        håndterSøknadMedValidering(1, Sykdom(8.januar,  23.februar, 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Fortsetter før andre søknad`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(
            vedtaksperiodeIndex = 0,
            arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)),
            førsteFraværsdag = 3.januar
        )
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSøknadMedValidering(1, Sykdom(29.januar,  23.februar, 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(3.januar, it.førsteFraværsdag(0))
            assertEquals(3.januar, it.førsteFraværsdag(1))
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `Tilstøtende perioder blir gruppert sammen`() {
        håndterSykmelding(Triple(1.januar, 2.januar, 100))
        håndterSykmelding(Triple(3.januar, 4.januar, 100))

        assertEquals(inspektør.gruppeId(0), inspektør.gruppeId(1))
    }

    @Test
    fun `Perioder med gap blir ikke gruppert sammen`() {
        håndterSykmelding(Triple(1.januar, 2.januar, 100))
        håndterSykmelding(Triple(4.januar, 5.januar, 100))

        assertNotEquals(inspektør.gruppeId(0), inspektør.gruppeId(1))
    }

    @Test
    fun `To tilstøtende perioder der den første er utbetalt`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar,  23.februar, 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder inntektsmelding først`() {
        håndterSykmelding(Triple(3.januar, 7.januar, 100))
        håndterSykmelding(Triple(8.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(8.januar,  23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  7.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        håndterManuellSaksbehandling(0, true)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder der den første er i utbetaling feilet`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AVVIST)

        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar,  23.februar, 100))

        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, UTBETALING_FEILET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE
        )
    }

    @Test
    fun `når utbetaling er ikke godkjent skal påfølgende perioder også kastes ut`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterManuellSaksbehandling(0, false)

        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, TIL_INFOTRYGD)
    }

    @Test
    fun `kan ikke forlenge en periode som er gått TilInfotrygd`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, false) // går til TilInfotrygd
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))

        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, TIL_INFOTRYGD)
    }

    @Test
    fun `ignorer inntektsmeldinger på påfølgende perioder`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Triple(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar,  23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(3.januar, 18.januar)))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)


        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `kiler bare andre periode og ikke tredje periode i en rekke`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterSykmelding(Triple(1.mars, 28.mars, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar,  23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP, AVVENTER_GAP
        )

        assertTilstander(
            2,
            START, MOTTATT_SYKMELDING_UFERDIG_GAP
        )
    }

    @Test
    fun `Sykmelding med gradering`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 50))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 50, 50))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING
        )
    }

    @Test
    fun `dupliserte hendelser produserer bare advarsler`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar,  26.januar, 100))
        håndterSøknad(Sykdom(3.januar,  26.januar, 100))
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterSøknad(Sykdom(3.januar,  26.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterSøknad(Sykdom(3.januar,  26.januar, 100))
        inspektør.also {
            assertNoErrors(it)
            assertWarnings(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `Sykmelding i omvendt rekkefølge`() {
        håndterSykmelding(Triple(10.januar, 20.januar, 100))
        håndterSykmelding(Triple(3.januar, 5.januar, 100))
        håndterInntektsmelding(
            listOf(
                Periode(4.januar, 5.januar),
                Periode(9.januar, 23.januar)
            )
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
    }

    @Test
    fun `Inntektsmelding vil ikke utvide vedtaksperiode til tidligere vedtaksperiode`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 3.januar)
        inspektør.also {
            assertNoErrors(it)
            assertNoWarnings(it)
        }
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        forventetEndringTeller++
        håndterManuellSaksbehandling(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Triple(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(16.januar, 16.februar))) // Touches prior periode
        assertNoErrors(inspektør)

        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterManuellSaksbehandling(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertNoErrors(inspektør)

        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AVVENTER_INNTEKTSMELDING_FERDIG_GAP`() {
        håndterSykmelding(Triple(1.januar, 31.januar, 100))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100))
        håndterYtelser(0)

        assertNoErrors(inspektør)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AVVENTER_INNTEKTSMELDING_UFERDIG_GAP`() {
        håndterSykmelding(Triple(1.januar, 20.januar, 100))
        håndterSykmelding(Triple(1.februar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))

        assertNoErrors(inspektør)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE`() {
        håndterSykmelding(Triple(1.januar, 20.januar, 100))
        håndterSykmelding(Triple(21.januar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))

        assertNoErrors(inspektør)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `søknad med papirsykmelding`() {
        håndterSykmelding(Triple(21.januar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100), Søknad.Periode.Papirsykmelding(1.januar, 20.januar))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `vedtaksperiode med søknad som går til infotrygd ber om inntektsmelding`() {
        håndterSykmelding(Triple(21.januar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))
        håndterYtelser(0, Triple(18.januar, 20.januar, 15000)) // -> TIL_INFOTRYGD

        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `vedtaksperiode uten søknad som går til infotrygd ber ikke om inntektsmelding`() {
        håndterSykmelding(Triple(21.januar, 28.februar, 100))
        håndterPåminnelse(0, MOTTATT_SYKMELDING_FERDIG_GAP)

        assertEquals(0, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `refusjon opphører i perioden`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), refusjon = Triple(14.januar, INNTEKT, emptyList()))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `refusjon endres i perioden`() {
        håndterSykmelding(Triple(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), refusjon = Triple(null, INNTEKT, listOf(14.januar)))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }
}
