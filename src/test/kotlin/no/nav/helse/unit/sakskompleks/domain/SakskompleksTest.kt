package no.nav.helse.unit.sakskompleks.domain

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.TestConstants.nySøknad
import no.nav.helse.TestConstants.sendtSøknad
import no.nav.helse.hendelse.Inntektsmelding
import no.nav.helse.inntektsmelding.InntektsmeldingConsumer
import no.nav.helse.juli
import no.nav.helse.person.domain.Sakskompleks
import no.nav.helse.readResource
import no.nav.syfo.kafka.sykepengesoknad.dto.SoknadsperiodeDTO
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class SakskompleksTest {
    companion object {
        val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private val standardNySøknad = nySøknad()
        private val standardSendtSøknad = sendtSøknad()

        private val enInntektsmeldingSomJson = InntektsmeldingConsumer.inntektsmeldingObjectMapper.readTree("/inntektsmelding.json".readResource())
        private val enInntektsmelding = Inntektsmelding(enInntektsmeldingSomJson)
    }

    @Test
    internal fun `gyldig jsonrepresentasjon av tomt sakskompleks`() {
        val id = UUID.randomUUID()
        val aktørId = "1234"
        val organisasjonsnummer = "123456789"

        val sakskompleks = Sakskompleks(
                id = id,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer
        )

        val jsonRepresentation = sakskompleks.jsonRepresentation()

        assertEquals(id, jsonRepresentation.id)
        assertEquals(aktørId, jsonRepresentation.aktørId)
        assertEquals(organisasjonsnummer, jsonRepresentation.organisasjonsnummer)
        assertNull(jsonRepresentation.sykdomstidslinje)
    }

    @Test
    internal fun `gyldig sakskompleks fra jsonrepresentasjon av tomt sakskompleks`() {
        val id = UUID.randomUUID()
        val aktørId = "1234"
        val organisasjonsnummer = "123456789"

        val sakskompleks = Sakskompleks(
                id = id,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer
        )

        val jsonRepresentation = sakskompleks.jsonRepresentation()
        val gjenopprettetSakskompleks = Sakskompleks.fromJson(jsonRepresentation)

        assertEquals(jsonRepresentation, gjenopprettetSakskompleks.jsonRepresentation())
    }

    @Test
    fun `memento inneholder en tilstand`() {
        val id = UUID.randomUUID()
        val sakskompleks = Sakskompleks(
                id = id,
                aktørId = "aktørId",
                organisasjonsnummer = "orgnummer"
        )

        val memento = sakskompleks.memento()
        val node = objectMapper.readTree(memento.state)

        assertNotNull(node["tilstand"])
        assertFalse(node["tilstand"].isNull)
        assertTrue(node["tilstand"].textValue().isNotEmpty())
    }

    @Test
    fun `memento inneholder aktørId og sakskompleksId`() {
        val id = UUID.randomUUID()
        val sakskompleks = Sakskompleks(
                id = id,
                aktørId = "aktørId",
                organisasjonsnummer = "orgnummer"
        )

        val memento = sakskompleks.memento()
        val node = objectMapper.readTree(memento.state)

        assertEquals(id.toString(), node["id"].textValue())
        assertEquals("aktørId", node["aktørId"].textValue())
        assertEquals("orgnummer", node["organisasjonsnummer"].textValue())
    }

    @Test
    fun `restore bygger opp likt objekt fra lagret memento`() {
        val id = UUID.randomUUID()
        val sakskompleks = Sakskompleks(
                id = id,
                aktørId = "aktørId",
                organisasjonsnummer = "orgnummer"
        )
        sakskompleks.håndterNySøknad(standardNySøknad)
        sakskompleks.håndterSendtSøknad(standardSendtSøknad)
        sakskompleks.håndterInntektsmelding(enInntektsmelding)

        val inMemento = sakskompleks.memento()

        val nyttSakskompleks = Sakskompleks.restore(inMemento)
        val outMemento = nyttSakskompleks.memento()
        val inNode = objectMapper.readTree(inMemento.state)
        val outNode = objectMapper.readTree(outMemento.state)

        assertEquals(inNode, outNode)

    }

    @Test
    fun `nytt sakskompleks godtar ny søknad`() {
        val sakskompleks = Sakskompleks(
                id = UUID.randomUUID(),
                aktørId = "aktørId",
                organisasjonsnummer = ""
        )
        assertTrue(sakskompleks.håndterNySøknad(nySøknad()))
    }

    @Test
    fun `eksisterende sakskompleks godtar ikke søknader som ikke overlapper tidslinje i sendt søknad`() {
        val sakskompleks = Sakskompleks(
                id = UUID.randomUUID(),
                aktørId = "aktørId",
                organisasjonsnummer = ""
        )
        sakskompleks.håndterNySøknad(nySøknad(fom = 1.juli, tom = 20.juli, søknadsperioder = listOf(SoknadsperiodeDTO(fom = 1.juli, tom = 20.juli)), egenmeldinger = emptyList(), fravær = emptyList()))

        assertFalse(sakskompleks.håndterSendtSøknad(sendtSøknad(fom = 21.juli, tom = 25.juli, søknadsperioder = listOf(SoknadsperiodeDTO(fom = 21.juli, tom = 25.juli)), egenmeldinger = emptyList(), fravær = emptyList())))

    }
}