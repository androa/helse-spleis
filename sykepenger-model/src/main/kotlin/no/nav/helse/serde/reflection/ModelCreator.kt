package no.nav.helse.serde.reflection

import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.person.Inntekthistorikk
import no.nav.helse.person.Person
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VedtaksperiodeObserver
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import java.util.UUID
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

internal fun createPerson(
    aktørId: String,
    fødselsnummer: String,
    arbeidsgivere: MutableList<Arbeidsgiver>,
    hendelser: MutableList<ArbeidstakerHendelse>,
    aktivitetslogger: Aktivitetslogger
) = Person::class.primaryConstructor!!
    .apply { isAccessible = true }
    .call(aktørId, fødselsnummer, arbeidsgivere, hendelser, aktivitetslogger)

internal fun createArbeidsgiver(
    organisasjonsnummer: String,
    id: UUID,
    inntekthistorikk: Inntekthistorikk,
    tidslinjer: MutableList<Utbetalingstidslinje>,
    perioder: MutableList<Vedtaksperiode>,
    vedtaksperiodeObservers: MutableList<VedtaksperiodeObserver>,
    aktivitetslogger: Aktivitetslogger
) = Arbeidsgiver::class.primaryConstructor!!
    .apply { isAccessible = true }
    .call(organisasjonsnummer, id, inntekthistorikk, tidslinjer, perioder, vedtaksperiodeObservers, aktivitetslogger)