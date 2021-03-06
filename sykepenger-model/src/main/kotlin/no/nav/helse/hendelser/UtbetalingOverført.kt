package no.nav.helse.hendelser

import no.nav.helse.person.ArbeidstakerHendelse
import java.time.LocalDateTime

class UtbetalingOverført(
    internal val vedtaksperiodeId: String,
    private val aktørId: String,
    private val fødselsnummer: String,
    private val orgnummer: String,
    internal val avstemmingsnøkkel: Long,
    internal val overføringstidspunkt: LocalDateTime
) : ArbeidstakerHendelse() {
    override fun aktørId() = aktørId
    override fun fødselsnummer() = fødselsnummer
    override fun organisasjonsnummer() = orgnummer
}
