package no.nav.helse.person

import no.nav.helse.behov.BehovType
import no.nav.helse.behov.partisjoner
import no.nav.helse.hendelser.*
import no.nav.helse.sykdomstidslinje.CompositeSykdomstidslinje
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.*

internal class VilkårsgrunnlagHendelseTest {
    companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private const val ORGNR = "12345"
    }

    private lateinit var person: Person
    private val inspektør get() = TestPersonInspektør(person)
    private lateinit var personObserver: TestPersonObserver
    private lateinit var aktivitetslogger: Aktivitetslogger
    private lateinit var aktivitetslogg: Aktivitetslogg

    @BeforeEach
    internal fun opprettPerson() {
        personObserver = TestPersonObserver()
        person = Person("12345", UNG_PERSON_FNR_2018)
        person.addObserver(personObserver)
        aktivitetslogger = Aktivitetslogger()
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `egen ansatt`() {
        håndterVilkårsgrunnlag(egenAnsatt = true, inntekter = emptyList(), arbeidsforhold = ansattSidenStart2017())

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(1, inspektør.vedtaksperiodeIder.size)
        assertTilstand(TilstandType.TIL_INFOTRYGD)
    }

    @Test
    fun `avvik i inntekt`() {
        håndterVilkårsgrunnlag(egenAnsatt = false, inntekter = emptyList(), arbeidsforhold = ansattSidenStart2017())

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(1, inspektør.vedtaksperiodeIder.size)
        assertTilstand(TilstandType.TIL_INFOTRYGD)
    }

    @Test
    fun `ikke egen ansatt og ingen avvik i inntekt`() {
        val månedslønn = 1000.0
        håndterVilkårsgrunnlag(
            egenAnsatt = false,
            beregnetInntekt = månedslønn,
            inntekter = tolvMånederMedInntekt(månedslønn),
            arbeidsforhold = ansattSidenStart2017()
        )

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(1, inspektør.vedtaksperiodeIder.size)
        assertTilstand(TilstandType.AVVENTER_HISTORIKK)
        val utgangspunktForBeregningAvYtelse = inspektør.sykdomstidslinje(0).førsteDag()
        val vedtaksperiodeId = inspektør.vedtaksperiodeId(0)
        assertEquals(
            utgangspunktForBeregningAvYtelse.minusDays(1),
            personObserver.etterspurtBehov(
                vedtaksperiodeId,
                "Sykepengehistorikk",
                "utgangspunktForBeregningAvYtelse"
            )
        )
    }

    @Test
    fun `ikke egen ansatt og mer enn 25 % avvik i inntekt`() {
        val månedslønn = 1000.0
        val `25 % mer` = månedslønn * 1.25 + 1
        håndterVilkårsgrunnlag(
            egenAnsatt = false,
            beregnetInntekt = `25 % mer`,
            inntekter = tolvMånederMedInntekt(månedslønn),
            arbeidsforhold = ansattSidenStart2017()
        )

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(1, inspektør.vedtaksperiodeIder.size)
        assertTilstand(TilstandType.TIL_INFOTRYGD)
    }

    @Test
    fun `ikke egen ansatt og mindre enn 25 % avvik i inntekt`() {
        val månedslønn = 1000.0
        val `25 % mindre` = månedslønn * 0.75 - 1
        håndterVilkårsgrunnlag(
            egenAnsatt = false,
            beregnetInntekt = `25 % mindre`,
            inntekter = tolvMånederMedInntekt(månedslønn),
            arbeidsforhold = ansattSidenStart2017()
        )

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(1, inspektør.vedtaksperiodeIder.size)
        assertTilstand(TilstandType.TIL_INFOTRYGD)
    }

    private fun ansattSidenStart2017() =
        listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNR, 1.januar(2017)))


    private fun tolvMånederMedInntekt(beregnetInntekt: Double): List<Vilkårsgrunnlag.Måned> {
        return (1..12).map {
            Vilkårsgrunnlag.Måned(
                YearMonth.of(2018, it), listOf(beregnetInntekt)
            )
        }
    }

    private fun assertTilstand(expectedTilstand: TilstandType) {
        assertEquals(
            expectedTilstand,
            inspektør.tilstand(0)
        ) { "Forventet tilstand $expectedTilstand: $aktivitetslogger" }
    }

    private fun håndterVilkårsgrunnlag(
        egenAnsatt: Boolean,
        beregnetInntekt: Double = 1000.0,
        inntekter: List<Vilkårsgrunnlag.Måned>,
        arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>
    ) {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding(beregnetInntekt = beregnetInntekt))
        person.håndter(vilkårsgrunnlag(egenAnsatt = egenAnsatt, inntekter = inntekter, arbeidsforhold = arbeidsforhold))
    }

    private fun sykmelding() =
        Sykmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            orgnummer = ORGNR,
            sykeperioder = listOf(Triple(1.januar, 31.januar, 100)),
            aktivitetslogg = Aktivitetslogg()
        ).apply {
            addObserver(personObserver)
        }

    private fun søknad() =
        Søknad(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            orgnummer = ORGNR,
            perioder = listOf(Søknad.Periode.Sykdom(1.januar, 31.januar, 100)),
            aktivitetslogg = Aktivitetslogg(),
            harAndreInntektskilder = false
        ).apply {
            addObserver(personObserver)
        }

    private fun inntektsmelding(beregnetInntekt: Double) =
        Inntektsmelding(
            meldingsreferanseId = UUID.randomUUID(),
            refusjon = Inntektsmelding.Refusjon(null, beregnetInntekt, emptyList()),
            orgnummer = ORGNR,
            fødselsnummer = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            førsteFraværsdag = 1.januar,
            beregnetInntekt = beregnetInntekt,
            arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)),
            ferieperioder = emptyList(),
            aktivitetslogg = Aktivitetslogg()
        ).apply {
            addObserver(personObserver)
        }

    private fun vilkårsgrunnlag(
        egenAnsatt: Boolean,
        inntekter: List<Vilkårsgrunnlag.Måned>,
        arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>
    ) =
        Vilkårsgrunnlag(
            vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = ORGNR,
            inntektsmåneder = inntekter,
            erEgenAnsatt = egenAnsatt,
            aktivitetslogg = Aktivitetslogg(),
            arbeidsforhold = Vilkårsgrunnlag.MangeArbeidsforhold(arbeidsforhold)
        ).apply {
            addObserver(personObserver)
        }

    private inner class TestPersonInspektør(person: Person) : PersonVisitor {

        private var vedtaksperiodeindeks: Int = -1
        private val tilstander = mutableMapOf<Int, TilstandType>()
        internal val vedtaksperiodeIder = mutableSetOf<UUID>()
        private val sykdomstidslinjer = mutableMapOf<Int, CompositeSykdomstidslinje>()

        init {
            person.accept(this)
        }

        override fun preVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode, id: UUID) {
            vedtaksperiodeindeks += 1
            tilstander[vedtaksperiodeindeks] = TilstandType.START
            vedtaksperiodeIder.add(id)
        }

        override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) {
            tilstander[vedtaksperiodeindeks] = tilstand.type
        }

        override fun preVisitComposite(compositeSykdomstidslinje: CompositeSykdomstidslinje) {
            sykdomstidslinjer[vedtaksperiodeindeks] = compositeSykdomstidslinje
        }

        internal val vedtaksperiodeTeller get() = tilstander.size

        internal fun vedtaksperiodeId(vedtaksperiodeindeks: Int) = vedtaksperiodeIder.elementAt(vedtaksperiodeindeks)

        internal fun tilstand(indeks: Int) = tilstander[indeks]

        internal fun sykdomstidslinje(indeks: Int) = sykdomstidslinjer[indeks] ?: throw IllegalAccessException()

    }

    private inner class TestPersonObserver : PersonObserver, HendelseObserver {
        private val etterspurteBehov = mutableListOf<BehovType>()

        inline fun <reified T> etterspurtBehov(id: UUID, behov: String, felt: String): T? {
            return etterspurteBehov.partisjoner().let {
                it.filter { it["vedtaksperiodeId"] == id }
                    .filter { behov in (it["@behov"] as List<*>) }
                    .first()[felt] as T?
            }
        }

        override fun onBehov(behov: BehovType) {
            etterspurteBehov.add(behov)
        }
    }
}
