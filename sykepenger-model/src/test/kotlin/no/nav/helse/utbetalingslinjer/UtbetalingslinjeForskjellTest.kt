package no.nav.helse.utbetalingslinjer

import no.nav.helse.serde.reflection.ReflectInstance.Companion.get
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingslinjer.Fagområde.SPREF
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UtbetalingslinjeForskjellTest {

    protected companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private const val ORGNUMMER = "987654321"
        private const val FAGSYSTEMID = "FAGSYSTEMID"


    }

    @Test
    internal fun `helt separate utbetalingslinjer`() {
        val original = linjer(1.januar to 5.januar)
        val recalculated = linjer(5.februar to 9.februar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(5.februar to 9.februar), actual)
        assertNotEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.NY, actual.endringskode)
    }

    @Test
    internal fun `tomme utbetalingslinjer fungerer som Null Object Utbetalingslinjer`() {
        val original = tomOppdrag()
        val recalculated = linjer(5.februar to 9.februar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(5.februar to 9.februar), actual)
        assertNotEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.NY, actual.endringskode)
    }

    @Test
    internal fun `fullstendig overskriv`() {
        val original = linjer(8.januar to 13.januar)
        val recalculated = linjer(1.januar to 9.februar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 9.februar), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(original.fagsystemId, actual[0].refFagsystemId)
    }

    @Test
    internal fun `ny tom`() {
        val original = linjer(1.januar to 5.januar)
        val recalculated = linjer(1.januar to 13.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 13.januar), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.ENDR, actual[0].endringskode)
    }

    @Test
    internal fun `bare flere utbetalingslinjer`() {
        val original = linjer(1.januar to 5.januar)
        val recalculated = linjer(1.januar to 5.januar, 15.januar to 19.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(recalculated, actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.UEND, actual[0].endringskode)
        assertEquals(Endringskode.NY, actual[1].endringskode)
    }

    @Test
    internal fun `grad endres`() {
        val original = linjer(1.januar to 5.januar)
        val recalculated = linjer(1.januar to 5.januar grad 80, 15.januar to 19.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(recalculated, actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.NY, actual[0].endringskode)
        assertEquals(Endringskode.NY, actual[1].endringskode)
        assertEquals(original[0].id + 1, actual[0].id)  // chained off of last of original
        assertEquals(actual[0].id + 1, actual[1].id)
    }

    @Test
    internal fun `dagsats endres`() {
        val original = linjer(1.januar to 5.januar)
        val recalculated = linjer(1.januar to 5.januar dagsats 1000, 15.januar to 19.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(recalculated, actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.NY, actual[0].endringskode)
        assertEquals(Endringskode.NY, actual[1].endringskode)
        assertEquals(original[0].id + 1, actual[0].id)  // chained off of last of original
        assertEquals(actual[0].id + 1, actual[1].id)
    }

    @Test
    internal fun `Tre perioder hvor grad endres i siste periode`() {
        val original = linjer(17.juni(2020) to 30.juni(2020))
        val new = linjer(17.juni(2020) to 31.juli(2020))
        val intermediate = new forskjell original
        assertEquals(original.fagsystemId, intermediate.fagsystemId)

        val new2 = linjer(
            17.juni(2020) to 31.juli(2020),
            1.august(2020) to 31.august(2020) grad 50
        )

        val actual = new2 forskjell intermediate

        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(intermediate.fagsystemId, actual.fagsystemId)

        assertEquals(original[0].id, actual[0].id)
        assertEquals(Endringskode.NY, original[0].endringskode)
        assertEquals(Endringskode.ENDR, intermediate[0].endringskode)

        assertEquals(original[0].id + 1, actual[1].id)
        assertEquals(Endringskode.UEND, actual[0].endringskode)
        assertEquals(Endringskode.NY, actual[1].endringskode)
    }

    @Test
    internal fun `potpourri`() {
        val original = linjer(
            1.januar to 5.januar,
            6.januar to 12.januar grad 50,
            13.januar to 19.januar grad 80
        )
        val recalculated = linjer(
            1.januar to 5.januar,
            6.januar to 17.januar grad 50,  // extended tom
            18.januar to 19.januar grad 80,
            1.februar to 9.februar
        )
        val actual = recalculated forskjell original
        assertUtbetalinger(recalculated, actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.UEND, actual[0].endringskode)
        assertEquals(Endringskode.ENDR, actual[1].endringskode)
        assertEquals(Endringskode.NY, actual[2].endringskode)
        assertEquals(Endringskode.NY, actual[3].endringskode)
        assertEquals(original[1].id, actual[1].id)      // picks up id from original
        assertEquals(original[2].id + 1, actual[2].id)  // chained off of last of original
        assertEquals(actual[2].id + 1, actual[3].id)
    }

    @Test
    internal fun `potpourri 2`() {
        val original = linjer(
            1.januar to 5.januar,
            6.januar to 12.januar grad 50,
            13.januar to 19.januar grad 80,
            1.februar to 3.februar,
            4.februar to 6.februar,
            7.februar to 8.februar
        )
        val recalculated = linjer(
            1.januar to 5.januar,
            6.januar to 17.januar grad 50,  // extended tom
            18.januar to 19.januar grad 80,
            1.februar to 9.februar
        )
        val actual = recalculated forskjell original
        assertUtbetalinger(recalculated, actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.UEND, actual[0].endringskode)
        assertEquals(Endringskode.ENDR, actual[1].endringskode)
        assertEquals(Endringskode.NY, actual[2].endringskode)
        assertEquals(Endringskode.NY, actual[3].endringskode)
        assertEquals(original[1].id, actual[1].id)      // picks up id from original
        assertEquals(original[5].id + 1, actual[2].id)  // chained off of last of original
        assertEquals(actual[2].id + 1, actual[3].id)
    }

    @Test
    internal fun `fom endres`() {
        val original = linjer(5.januar to 10.januar)
        val recalculated = linjer(1.januar to 10.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 10.januar), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.NY, actual[0].endringskode)
        assertEquals(original[0].id + 1, actual[0].id)
        assertEquals(original[0].id, actual[0].refId)
    }

    @Test
    internal fun `potpourri 3`() {
        val original = linjer(
            1.januar to 5.januar,
            6.januar to 12.januar grad 50,
            13.januar to 19.januar
        )
        val new = linjer(
            1.januar to 5.januar,
            6.januar to 19.januar grad 50,
            20.januar to 26.januar
        )
        val actual = new forskjell original
        assertEquals(original.fagsystemId, actual.fagsystemId)

        assertEquals(original[0].id, actual[0].id)
        assertEquals(original[1].id, actual[1].id)
        assertEquals(original[2].id + 1, actual[2].id)

        assertEquals(Endringskode.NY, original[0].endringskode)
        assertEquals(Endringskode.NY, original[1].endringskode)
        assertEquals(Endringskode.NY, original[2].endringskode)

        assertEquals(Endringskode.UEND, actual[0].endringskode)
        assertEquals(Endringskode.ENDR, actual[1].endringskode)
        assertEquals(Endringskode.NY, actual[2].endringskode)
    }

    @Test internal fun `slett nøyaktig en periode`() {
        val original = linjer(1.januar to 5.januar, 6.januar to 12.januar grad 50)
        val recalculated = linjer(6.januar to 12.januar grad 50)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 5.januar grad 0 dagsats 0, 6.januar to 12.januar grad 50), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[1].id, actual[0].refId)
        assertEquals(Endringskode.UEND, actual[1].endringskode)
        assertEquals(original[1].id, actual[1].id)
        assertEquals(original[1].refId, actual[1].refId)
    }

    @Test internal fun `original andre periode samsvarer delvis med ny`() {
        val original = linjer(1.januar to 5.januar, 6.januar to 12.januar grad 50)
        val recalculated = linjer(6.januar to 19.januar grad 50)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 5.januar grad 0 dagsats 0, 6.januar to 19.januar grad 50), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[1].id, actual[0].refId)
        assertEquals(Endringskode.ENDR, actual[1].endringskode)
        assertEquals(original[1].id, actual[1].id)
        assertEquals(original[1].refId, actual[1].refId)
    }

    @Test internal fun `perioden min starter midt i en original periode`() {
        val original = linjer(1.januar to 3.januar, 4.januar to 12.januar grad 50)
        val recalculated = linjer(6.januar to 19.januar grad 50)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 5.januar grad 0 dagsats 0, 6.januar to 19.januar grad 50), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[1].id, actual[0].refId)
        assertEquals(Endringskode.NY, actual[1].endringskode)
        assertEquals(actual[0].id, actual[1].refId)
        assertEquals(actual[0].id + 1, actual[1].id)
    }

    @Test internal fun `ny er tom`() {
        val original = linjer(1.januar to 3.januar, 4.januar to 12.januar grad 50)
        val recalculated = tomOppdrag()
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 12.januar grad 0 dagsats 0), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[1].id, actual[0].refId)
        assertEquals(original[1].id + 1, actual[0].id)
        assertEquals(original.fagsystemId, actual[0].refFagsystemId)
    }

    @Test internal fun `ny er tom uten sisteArbeidsgiverdag`() {
        val original = linjer(1.januar to 3.januar, 4.januar to 12.januar grad 50)
        val recalculated = tomOppdrag(null)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(1.januar to 12.januar grad 0 dagsats 0), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[1].id, actual[0].refId)
        assertEquals(original[1].id + 1, actual[0].id)
        assertEquals(original.fagsystemId, actual[0].refFagsystemId)
    }

    @Test internal fun `ny er tom og sisteArbeidsgiverdag er etter tidligere`() {
        val original = linjer(1.januar to 3.januar, 4.januar to 12.januar grad 50)
        val recalculated = tomOppdrag(1.februar)
        val actual = recalculated forskjell original
        assertUtbetalinger(recalculated, actual)
        assertNotEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.NY, actual.endringskode)
    }

    @Test internal fun `originalen har hale å slette`() {
        val original = linjer(1.januar to 12.januar)
        val recalculated = linjer(1.januar to 5.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(
            1.januar to 5.januar,
            6.januar to 12.januar grad 0 dagsats 0
        ), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)
        assertEquals(Endringskode.ENDR, actual[0].endringskode)
        assertEquals(original[0].id, actual[0].id)
        assertNull(actual[0].refId)
        assertEquals(Endringskode.OPPH, actual[1].endringskode)
        assertEquals(actual[0].id, actual[1].refId)
        assertEquals(actual[0].id + 1, actual[1].id)
    }

    @Test internal fun `slette fra hode og hale av originalen`() {
        val original = linjer(1.januar to 12.januar)
        val recalculated = linjer(3.januar to 9.januar)
        val actual = recalculated forskjell original
        assertUtbetalinger(linjer(
            1.januar to 2.januar grad 0 dagsats 0,
            3.januar to 9.januar,
            10.januar to 12.januar grad 0 dagsats 0
        ), actual)
        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)

        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[0].id + 1, actual[0].id)
        assertEquals(original[0].id, actual[0].refId)

        assertEquals(Endringskode.NY, actual[1].endringskode)
        assertEquals(actual[0].id + 1, actual[1].id)
        assertEquals(actual[0].id, actual[1].refId)

        assertEquals(Endringskode.OPPH, actual[2].endringskode)
        assertEquals(actual[1].id + 1, actual[2].id)
        assertEquals(actual[1].id, actual[2].refId)
    }

    @Test
    internal fun `deletion potpourri`() {
        val original = linjer(
            1.januar to 5.januar,
            6.januar to 12.januar grad 50,
            13.januar to 19.januar,
            20.januar to 31.januar
        )
        val new = linjer(
            6.januar to 19.januar grad 50,
            20.januar to 26.januar
        )
        val actual = new forskjell original
        assertUtbetalinger(linjer(
            1.januar to 5.januar grad 0 dagsats 0,
            6.januar to 19.januar grad 50,
            20.januar to 26.januar,
            27.januar to 31.januar grad 0 dagsats 0
        ), actual)

        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)

        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[3].id + 1, actual[0].id)
        assertEquals(original[3].id, actual[0].refId)

        assertEquals(Endringskode.ENDR, actual[1].endringskode)
        assertEquals(original[1].id, actual[1].id)
        assertEquals(original[1].refId, actual[1].refId)

        assertEquals(Endringskode.NY, actual[2].endringskode)
        assertEquals(actual[0].id + 1, actual[2].id)
        assertEquals(actual[0].id, actual[2].refId)

        assertEquals(Endringskode.OPPH, actual[3].endringskode)
        assertEquals(actual[2].id + 1, actual[3].id)
        assertEquals(actual[2].id, actual[3].refId)
    }

    @Test
    internal fun `deletion all`() {
        val original = linjer(
            1.januar to 5.januar,
            6.januar to 12.januar grad 50,
            13.januar to 19.januar,
            20.januar to 31.januar
        )
        val new = tomOppdrag()
        val actual = new forskjell original
        assertUtbetalinger(linjer(1.januar to 31.januar grad 0 dagsats 0), actual)

        assertEquals(original.fagsystemId, actual.fagsystemId)
        assertEquals(Endringskode.UEND, actual.endringskode)

        assertEquals(Endringskode.OPPH, actual[0].endringskode)
        assertEquals(original[3].id + 1, actual[0].id)
        assertEquals(original[3].id, actual[0].refId)
    }

    private fun tomOppdrag(sisteArbeidsgiverdag: LocalDate? = 31.desember(2017)) =
        Oppdrag(ORGNUMMER, SPREF, sisteArbeidsgiverdag = sisteArbeidsgiverdag)

    private val Oppdrag.endringskode get() = this.get<Endringskode>("endringskode")

    private val Utbetalingslinje.endringskode get() = this.get<Endringskode>("endringskode")

    private val Utbetalingslinje.id get() = this.get<Int>("delytelseId")

    private val Utbetalingslinje.refId get() = this.get<Int?>("refDelytelseId")

    private val Oppdrag.fagsystemId get() = this.get<String>("fagsystemId")

    private fun assertUtbetalinger(expected: Oppdrag, actual: Oppdrag) {
        assertEquals(expected.size, actual.size, "Utbetalingslinjer er i forskjellige størrelser")
        (expected zip actual).forEach { (a, b) ->
            assertEquals(a.fom, b.fom, "fom stemmer ikke overens")
            assertEquals(a.tom, b.tom, "tom stemmer ikke overens")
            assertEquals(a.dagsats, b.dagsats, "dagsats stemmer ikke overens")
            assertEquals(a.grad, b.grad, "grad stemmer ikke overens")
        }
    }

    private fun linjer(vararg linjer: TestUtbetalingslinje) =
        Oppdrag(ORGNUMMER, SPREF, linjer.map { it.asUtbetalingslinje() }, sisteArbeidsgiverdag = 31.desember(2017)).also {
            it.zipWithNext { a, b -> b.linkTo(a) }
        }

    private fun linjer(vararg linjer: Utbetalingslinje) =
        Oppdrag(ORGNUMMER, SPREF, linjer.toList(), sisteArbeidsgiverdag = 31.desember(2017)).also {
            it.zipWithNext { a, b -> b.linkTo(a) }
        }

    private inner class TestUtbetalingslinje(
        private val fom: LocalDate,
        private val tom: LocalDate
    ) {
        private var grad: Double = 100.0
        private var dagsats = 1200

        internal infix fun grad(percentage: Number): TestUtbetalingslinje {
            grad = percentage.toDouble()
            return this
        }

        internal infix fun dagsats(amount: Int): TestUtbetalingslinje {
            dagsats = amount
            return this
        }

        internal infix fun forskjell(other: TestUtbetalingslinje) = this forskjell other.asUtbetalingslinjer()

        internal infix fun forskjell(other: Oppdrag) = this.asUtbetalingslinjer() forskjell other

        internal fun asUtbetalingslinje() = Utbetalingslinje(fom, tom, dagsats, grad, FAGSYSTEMID)

        private fun asUtbetalingslinjer() = linjer(asUtbetalingslinje())
    }

    private infix fun LocalDate.to(other: LocalDate) = TestUtbetalingslinje(this, other)
}

