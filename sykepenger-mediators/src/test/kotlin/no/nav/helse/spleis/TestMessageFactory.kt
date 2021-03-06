package no.nav.helse.spleis

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.TilstandType
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.inntektsmeldingkontrakt.*
import no.nav.syfo.kafka.felles.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

internal class TestMessageFactory(
    private val fødselsnummer: String,
    private val aktørId: String,
    private val organisasjonsnummer: String,
    private val inntekt: Double
) {

    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private fun SykepengesoknadDTO.toMap(): Map<String, Any> = objectMapper.convertValue(this)
        private fun Inntektsmelding.toMap(): Map<String, Any> = objectMapper.convertValue(this)
    }

    fun lagNySøknad(vararg perioder: SoknadsperiodeDTO): String {
        val nySøknad = SykepengesoknadDTO(
            status = SoknadsstatusDTO.NY,
            id = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            aktorId = aktørId,
            fodselsnummer = SkjultVerdi(fødselsnummer),
            arbeidsgiver = ArbeidsgiverDTO(orgnummer = organisasjonsnummer),
            fom = perioder.minBy { it.fom!! }?.fom,
            tom = perioder.maxBy { it.tom!! }?.tom,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            startSyketilfelle = LocalDate.now(),
            sendtNav = null,
            egenmeldinger = emptyList(),
            fravar = emptyList(),
            soknadsperioder = perioder.toList(),
            opprettet = LocalDateTime.now()
        )
        return nyHendelse("ny_søknad", nySøknad.toMap())
    }

    fun lagSøknadArbeidsgiver(
        perioder: List<SoknadsperiodeDTO>,
        egenmeldinger: List<PeriodeDTO> = emptyList()
    ): String {
        val sendtSøknad = SykepengesoknadDTO(
            status = SoknadsstatusDTO.SENDT,
            id = UUID.randomUUID().toString(),
            aktorId = aktørId,
            fodselsnummer = SkjultVerdi(fødselsnummer),
            arbeidsgiver = ArbeidsgiverDTO(orgnummer = organisasjonsnummer),
            fom = perioder.minBy { it.fom!! }?.fom,
            tom = perioder.maxBy { it.tom!! }?.tom,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            startSyketilfelle = LocalDate.now(),
            sendtArbeidsgiver = perioder.maxBy { it.tom!! }?.tom?.atStartOfDay(),
            egenmeldinger = egenmeldinger,
            fravar = emptyList(),
            soknadsperioder = perioder.toList(),
            opprettet = LocalDateTime.now()
        )
        return nyHendelse("sendt_søknad_arbeidsgiver", sendtSøknad.toMap())
    }

    fun lagSøknadNav(
        perioder: List<SoknadsperiodeDTO>,
        egenmeldinger: List<PeriodeDTO> = emptyList()
    ): String {
        val sendtSøknad = SykepengesoknadDTO(
            status = SoknadsstatusDTO.SENDT,
            id = UUID.randomUUID().toString(),
            aktorId = aktørId,
            fodselsnummer = SkjultVerdi(fødselsnummer),
            arbeidsgiver = ArbeidsgiverDTO(orgnummer = organisasjonsnummer),
            fom = perioder.minBy { it.fom!! }?.fom,
            tom = perioder.maxBy { it.tom!! }?.tom,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            startSyketilfelle = LocalDate.now(),
            sendtNav = perioder.maxBy { it.tom!! }?.tom?.atStartOfDay(),
            papirsykmeldinger = emptyList(),
            egenmeldinger = egenmeldinger,
            fravar = emptyList(),
            soknadsperioder = perioder.toList(),
            opprettet = LocalDateTime.now()
        )
        return nyHendelse("sendt_søknad_nav", sendtSøknad.toMap())
    }

    fun lagInnteksmelding(
        arbeidsgiverperiode: List<Periode>,
        førsteFraværsdag: LocalDate
    ): String {
        val inntektsmelding = Inntektsmelding(
            inntektsmeldingId = UUID.randomUUID().toString(),
            arbeidstakerFnr = fødselsnummer,
            arbeidstakerAktorId = aktørId,
            virksomhetsnummer = organisasjonsnummer,
            arbeidsgiverFnr = null,
            arbeidsgiverAktorId = null,
            arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
            arbeidsforholdId = null,
            beregnetInntekt = inntekt.toBigDecimal(),
            refusjon = Refusjon(inntekt.toBigDecimal(), null),
            endringIRefusjoner = emptyList(),
            opphoerAvNaturalytelser = emptyList(),
            gjenopptakelseNaturalytelser = emptyList(),
            arbeidsgiverperioder = arbeidsgiverperiode,
            status = Status.GYLDIG,
            arkivreferanse = "",
            ferieperioder = emptyList(),
            foersteFravaersdag = førsteFraværsdag,
            mottattDato = LocalDateTime.now()
        )
        return nyHendelse("inntektsmelding", inntektsmelding.toMap())
    }

    fun lagYtelser(vedtaksperiodeId: UUID, tilstand: TilstandType): String {
        return lagBehovMedLøsning(
            vedtaksperiodeId = vedtaksperiodeId,
            tilstand = tilstand,
            behov = listOf("Sykepengehistorikk", "Foreldrepenger"),
            løsninger = mapOf(
                "Sykepengehistorikk" to emptyList<Any>(),
                "Foreldrepenger" to emptyMap<String, String>()
            )
        )
    }

    fun lagVilkårsgrunnlag(
        vedtaksperiodeId: UUID,
        tilstand: TilstandType,
        egenAnsatt: Boolean = false,
        inntekter: List<Pair<YearMonth, Double>>,
        opptjening: List<Triple<String, LocalDate, LocalDate?>>
    ): String {
        return lagBehovMedLøsning(
            behov = listOf("Inntektsberegning", "EgenAnsatt", "Opptjening", "Dagpenger", "Arbeidsavklaringspenger"),
            vedtaksperiodeId = vedtaksperiodeId,
            tilstand = tilstand,
            løsninger = mapOf(
                "EgenAnsatt" to egenAnsatt,
                "Inntektsberegning" to inntekter
                    .groupBy { it.first }
                    .map {
                        mapOf(
                            "årMåned" to it.key,
                            "inntektsliste" to it.value.map { mapOf(
                                "beløp" to it.second,
                                "inntektstype" to "LOENNSINNTEKT",
                                "orgnummer" to organisasjonsnummer
                            ) }
                        )
                    },
                "Opptjening" to opptjening.map {
                    mapOf(
                        "orgnummer" to it.first,
                        "ansattSiden" to it.second,
                        "ansattTil" to it.third
                    )
                },
                "Dagpenger" to mapOf(
                    "meldekortperioder" to emptyList<Any>()
                ),
                "Arbeidsavklaringspenger" to mapOf(
                    "meldekortperioder" to emptyList<Any>()
                )
            )
        )
    }

    fun lagSimulering(vedtaksperiodeId: UUID, tilstand: TilstandType, simuleringOK: Boolean): String {
        return lagBehovMedLøsning(
            behov = listOf("Simulering"),
            vedtaksperiodeId = vedtaksperiodeId,
            tilstand = tilstand,
            løsninger = mapOf(
                "Simulering" to mapOf(
                    "status" to if (simuleringOK) "OK" else "FEIL",
                    "feilmelding" to if (simuleringOK) "" else "FEIL I SIMULERING",
                    "simulering" to if (!simuleringOK) null else mapOf(
                        "gjelderId" to fødselsnummer,
                        "gjelderNavn" to "Korona",
                        "datoBeregnet" to "2020-01-01",
                        "totalBelop" to 9999,
                        "periodeList" to listOf(
                            mapOf(
                                "fom" to "2020-01-01",
                                "tom" to "2020-01-02",
                                "utbetaling" to listOf(
                                    mapOf(
                                        "fagSystemId" to "1231203123123",
                                        "utbetalesTilId" to organisasjonsnummer,
                                        "utbetalesTilNavn" to "Koronavirus",
                                        "forfall" to "2020-01-03",
                                        "feilkonto" to true,
                                        "detaljer" to listOf(
                                            mapOf(
                                                "faktiskFom" to "2020-01-01",
                                                "faktiskTom" to "2020-01-02",
                                                "konto" to "12345678910og1112",
                                                "belop" to 9999,
                                                "tilbakeforing" to false,
                                                "sats" to 1111,
                                                "typeSats" to "DAG",
                                                "antallSats" to 9,
                                                "uforegrad" to 100,
                                                "klassekode" to "SPREFAG-IOP",
                                                "klassekodeBeskrivelse" to "Sykepenger, Refusjon arbeidsgiver",
                                                "utbetalingsType" to "YTEL",
                                                "refunderesOrgNr" to organisasjonsnummer
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    fun lagManuellSaksbehandling(
        vedtaksperiodeId: UUID,
        tilstand: TilstandType,
        utbetalingGodkjent: Boolean
    ): String {
        return lagBehovMedLøsning(
            behov = listOf("Godkjenning"),
            tilstand = tilstand,
            vedtaksperiodeId = vedtaksperiodeId,
            ekstraFelter = mapOf(
                "saksbehandlerIdent" to "en_saksbehandler",
                "godkjenttidspunkt" to LocalDateTime.now()
            ),
            løsninger = mapOf(
                "Godkjenning" to mapOf(
                    "godkjent" to utbetalingGodkjent
                )
            )
        )
    }

    fun lagPåminnelse(vedtaksperiodeId: UUID, tilstand: TilstandType): String {
        return nyHendelse("påminnelse", mapOf(
            "aktørId" to aktørId,
            "fødselsnummer" to fødselsnummer,
            "organisasjonsnummer" to organisasjonsnummer,
            "vedtaksperiodeId" to vedtaksperiodeId,
            "tilstand" to tilstand.name,
            "antallGangerPåminnet" to 0,
            "tilstandsendringstidspunkt" to LocalDateTime.now(),
            "påminnelsestidspunkt" to LocalDateTime.now(),
            "nestePåminnelsestidspunkt" to LocalDateTime.now()
        ))
    }

    fun lagUtbetaling(vedtaksperiodeId: UUID, tilstand: TilstandType, utbetalingOK: Boolean = true): String {
        return lagBehovMedLøsning(
            behov = listOf("Utbetaling"),
            tilstand = tilstand,
            vedtaksperiodeId = vedtaksperiodeId,
            løsninger = mapOf(
                "Utbetaling" to mapOf(
                    "status" to if (utbetalingOK) UtbetalingHendelse.Oppdragstatus.AKSEPTERT.name else UtbetalingHendelse.Oppdragstatus.AVVIST.name,
                    "beskrivelse" to if (!utbetalingOK) "FEIL fra Spenn" else ""
                )
            ),
            ekstraFelter = mapOf(
                "fagsystemId" to "123456789"
            )
        )
    }

    fun lagKansellerUtbetaling(fagsystemId: String): String {
        return nyHendelse("kanseller_utbetaling", mapOf(
            "aktørId" to aktørId,
            "fødselsnummer" to fødselsnummer,
            "organisasjonsnummer" to organisasjonsnummer,
            "fagsystemId" to fagsystemId,
            "saksbehandler" to "Ola Nordmann"
        ))
    }

    private fun nyHendelse(navn: String, hendelse: Map<String, Any>) =
        JsonMessage.newMessage(nyHendelse(navn) + hendelse).toJson()

    private fun nyHendelse(navn: String) = mutableMapOf<String, Any>(
        "@id" to UUID.randomUUID(),
        "@event_name" to navn,
        "@opprettet" to LocalDateTime.now()
    )

    private fun lagBehovMedLøsning(
        behov: List<String> = listOf(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        tilstand: TilstandType,
        løsninger: Map<String, Any> = emptyMap(),
        ekstraFelter: Map<String, Any> = emptyMap()
    ) = nyHendelse("behov", ekstraFelter + mapOf(
        "@behov" to behov,
        "tilstand" to tilstand.name,
        "aktørId" to aktørId,
        "fødselsnummer" to fødselsnummer,
        "organisasjonsnummer" to organisasjonsnummer,
        "vedtaksperiodeId" to vedtaksperiodeId.toString(),
        "@løsning" to løsninger,
        "@final" to true,
        "@besvart" to LocalDateTime.now()
    ))
}
