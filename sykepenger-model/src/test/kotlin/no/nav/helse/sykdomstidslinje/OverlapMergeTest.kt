package no.nav.helse.sykdomstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.sykdomstidslinje.NyDag.*
import no.nav.helse.testhelpers.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OverlapMergeTest {
    @Test
    internal fun `overlap av samme type`() {
        val actual = (1.januar jobbTil 12.januar).merge(7.januar jobbTil 19.januar)

        assertEquals(Periode(1.januar, 19.januar), actual.periode())
        assertEquals(15, actual.filterIsInstance<NyArbeidsdag>().size)
        assertEquals(4, actual.filterIsInstance<NyDag.NyFriskHelgedag>().size)
    }

    @Test
    internal fun `dagturnering`() {
        val actual =
            (1.januar ferieTil 12.januar).merge(7.januar betalingTil 19.januar).merge(17.januar ferieTil 26.januar)

        assertEquals(Periode(1.januar, 26.januar), actual.periode())
        assertEquals(6 + 7, actual.filterIsInstance<NyFeriedag>().size)
        assertEquals(2, actual.filterIsInstance<NyArbeidsgiverHelgedag>().size)
        assertEquals(2, actual.filterIsInstance<NyArbeidsgiverdag>().size)
        assertEquals(9, actual.filterIsInstance<ProblemDag>().size)
    }

    @Test
    internal fun `inntektsmelding uten ferie`() {
        val actual = listOf(
            1.januar jobbTil 17.januar,
            2.januar betalingTil 4.januar,
            7.januar betalingTil 11.januar
        ).merge(testBeste)

        assertEquals(Periode(1.januar, 17.januar), actual.periode())
        assertEquals(3 + 4, actual.filterIsInstance<NyArbeidsgiverdag>().size)
        assertEquals(1, actual.filterIsInstance<NyArbeidsgiverHelgedag>().size)
        assertEquals(1 + 1 + 1 + 3, actual.filterIsInstance<NyArbeidsdag>().size)
        assertEquals(3, actual.filterIsInstance<NyFriskHelgedag>().size)
    }

    @Test
    internal fun `inntektsmelding med ferie`() {
        val actual = listOf(
            1.januar jobbTil 17.januar,
            2.januar betalingTil 4.januar,
            7.januar betalingTil 11.januar,
            1.januar ferieTil 3.januar
        ).merge(testBeste)

        assertEquals(Periode(1.januar, 17.januar), actual.periode())
        assertEquals(3 + 4, actual.filterIsInstance<NyArbeidsgiverdag>().size)
        assertEquals(1, actual.filterIsInstance<NyArbeidsgiverHelgedag>().size)
        assertEquals(1 + 1 + 3, actual.filterIsInstance<NyArbeidsdag>().size)
        assertEquals(3, actual.filterIsInstance<NyFriskHelgedag>().size)
        assertEquals(1, actual.filterIsInstance<NyFeriedag>().size)
    }

    @Test
    internal fun `inntektsmelding med ferie i helg`() {
        val actual = listOf(
            1.januar jobbTil 17.januar,
            2.januar betalingTil 4.januar,
            7.januar betalingTil 11.januar,
            5.januar ferieTil 8.januar
        ).merge(testBeste)

        assertEquals(Periode(1.januar, 17.januar), actual.periode())
        assertEquals(3 + 4, actual.filterIsInstance<NyArbeidsgiverdag>().size)
        assertEquals(1, actual.filterIsInstance<NyArbeidsgiverHelgedag>().size)
        assertEquals(1 + 1 + 3, actual.filterIsInstance<NyArbeidsdag>().size)
        assertEquals(2, actual.filterIsInstance<NyFriskHelgedag>().size)
        assertEquals(2, actual.filterIsInstance<NyFeriedag>().size)
    }

    @Test
    internal fun `første fraværsdag`() {
        val actual = listOf(
            1.januar sykTil 1.januar,
            5.januar ferieTil 8.januar
        ).merge(testBeste)

        assertEquals(Periode(1.januar, 8.januar), actual.periode())
        assertEquals(4, actual.filterIsInstance<NyFeriedag>().size)
        assertEquals(1, actual.filterIsInstance<NySykedag>().size)
    }

    private val testBeste = { venstre: NyDag, høyre: NyDag ->
        when {
            venstre is NyUkjentDag -> høyre
            høyre is NyUkjentDag -> venstre
            venstre is NyArbeidsgiverdag || venstre is NyArbeidsgiverHelgedag -> venstre
            høyre is NyArbeidsgiverdag || høyre is NyArbeidsgiverHelgedag -> høyre
            venstre is NySykedag -> venstre
            høyre is NySykedag -> høyre
            venstre is NyFeriedag && høyre is NyArbeidsdag -> venstre
            høyre is NyFeriedag && venstre is NyArbeidsdag -> høyre
            venstre is NyFeriedag && høyre is NyFriskHelgedag -> venstre
            høyre is NyFeriedag && venstre is NyFriskHelgedag -> høyre
            else -> venstre.problem()
        }
    }
}
