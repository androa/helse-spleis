package no.nav.helse.hendelser


import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.person.Inntekthistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import java.time.LocalDate
import java.util.*

@Deprecated("Sykepengehistorikk og foreldrepenger sendes som to parametre til modellen")
class Ytelser(
    private val meldingsreferanseId: UUID,
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    internal val vedtaksperiodeId: String,
    private val utbetalingshistorikk: Utbetalingshistorikk,
    private val foreldrepermisjon: Foreldrepermisjon,
    aktivitetslogg: Aktivitetslogg
) : ArbeidstakerHendelse(aktivitetslogg) {
    internal fun utbetalingshistorikk() = utbetalingshistorikk

    internal fun foreldrepenger() = foreldrepermisjon

    internal fun valider(arbeidsgivertidslinje: Sykdomstidslinje, førsteFraværsdag: LocalDate? = null) =
        utbetalingshistorikk.valider(arbeidsgivertidslinje, førsteFraværsdag)

    internal fun addInntekter(inntekthistorikk: Inntekthistorikk) {
        utbetalingshistorikk().addInntekter(this.meldingsreferanseId, inntekthistorikk)
    }

    override fun aktørId(): String {
        return aktørId
    }

    override fun fødselsnummer(): String {
        return fødselsnummer
    }

    override fun organisasjonsnummer(): String {
        return organisasjonsnummer
    }
}
