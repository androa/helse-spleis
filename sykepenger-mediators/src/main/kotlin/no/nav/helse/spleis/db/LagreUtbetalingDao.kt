package no.nav.helse.spleis.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.VedtaksperiodeObserver
import no.nav.helse.spleis.PostgresProbe
import java.util.*
import javax.sql.DataSource

class LagreUtbetalingDao(private val dataSource: DataSource,
                         private val probe: PostgresProbe = PostgresProbe
): PersonObserver {

    override fun personEndret(personEndretEvent: PersonObserver.PersonEndretEvent) {
    }

    override fun vedtaksperiodeTilUtbetaling(event: VedtaksperiodeObserver.UtbetalingEvent) {
        lagreUtbetaling(event.utbetalingsreferanse, event.aktørId, event.organisasjonsnummer, event.vedtaksperiodeId)
    }

    private fun lagreUtbetaling(utbetalingsreferanse: String, aktørId: String, organisasjonsnummer: String, vedtaksperiodeId: UUID) {
        using(sessionOf(dataSource)) { session ->
            session.run(queryOf("INSERT INTO utbetalingsreferanse (id, aktor_id, orgnr, vedtaksperiode_id) VALUES (?, ?, ?, ?)",
                    utbetalingsreferanse, aktørId, organisasjonsnummer, vedtaksperiodeId.toString()).asExecute)
        }.also {
            PostgresProbe.utbetalingSkrevetTilDb()
        }
    }

}