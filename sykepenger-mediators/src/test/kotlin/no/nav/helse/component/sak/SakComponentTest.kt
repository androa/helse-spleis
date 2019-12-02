package no.nav.helse.component.sak

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import no.nav.common.KafkaEnvironment
import no.nav.helse.*
import no.nav.helse.TestConstants.inntektsmeldingDTO
import no.nav.helse.TestConstants.søknadDTO
import no.nav.helse.Topics.behovTopic
import no.nav.helse.Topics.inntektsmeldingTopic
import no.nav.helse.Topics.opprettGosysOppgaveTopic
import no.nav.helse.Topics.søknadTopic
import no.nav.helse.Topics.vedtaksperiodeEventTopic
import no.nav.helse.behov.Behov
import no.nav.helse.behov.BehovsTyper
import no.nav.helse.behov.BehovsTyper.*
import no.nav.helse.component.JwtStub
import no.nav.helse.sak.Sak
import no.nav.helse.sak.TilstandType
import no.nav.helse.spleis.oppgave.GosysOppgaveProducer.OpprettGosysOppgaveDto
import no.nav.helse.spleis.path
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.syfo.kafka.sykepengesoknad.dto.ArbeidsgiverDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.SoknadsstatusDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.SykepengesoknadDTO
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Duration.ofMillis
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@KtorExperimentalAPI
internal class SakComponentTest {

    private companion object {

        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"
        private const val kafkaApplicationId = "spleis-v1"

        private val topics = listOf(søknadTopic, inntektsmeldingTopic, behovTopic, opprettGosysOppgaveTopic, vedtaksperiodeEventTopic)
        // Use one partition per topic to make message sending more predictable
        private val topicInfos = topics.map { KafkaEnvironment.TopicInfo(it, partitions = 1) }

        private val embeddedKafkaEnvironment = KafkaEnvironment(
            autoStart = false,
            noOfBrokers = 1,
            topicInfos = topicInfos,
            withSchemaRegistry = false,
            withSecurity = false,
            topicNames = topics
        )

        private lateinit var adminClient: AdminClient
        private lateinit var kafkaProducer: KafkaProducer<String, String>

        private lateinit var embeddedPostgres: EmbeddedPostgres
        private lateinit var postgresConnection: Connection

        private lateinit var hikariConfig: HikariConfig

        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        private lateinit var jwtStub: JwtStub

        private lateinit var embeddedServer: ApplicationEngine

        private fun applicationConfig(wiremockBaseUrl: String): Map<String, String> {
            return mapOf(
                "KAFKA_APP_ID" to kafkaApplicationId,
                "KAFKA_BOOTSTRAP_SERVERS" to embeddedKafkaEnvironment.brokersURL,
                "KAFKA_USERNAME" to username,
                "KAFKA_PASSWORD" to password,
                "KAFKA_COMMIT_INTERVAL_MS_CONFIG" to "100", // Consumer commit interval must be low because we want quick feedback in the [assertMessageIsConsumed] method
                "DATABASE_JDBC_URL" to embeddedPostgres.getJdbcUrl("postgres", "postgres"),
                "AZURE_CONFIG_URL" to "$wiremockBaseUrl/config",
                "AZURE_CLIENT_ID" to "spleis_azure_ad_app_id",
                "AZURE_CLIENT_SECRET" to "el_secreto",
                "AZURE_REQUIRED_GROUP" to "sykepenger-saksbehandler-gruppe"
            )
        }

        private fun producerProperties() =
            Properties().apply {
                put(BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
                put(SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                // Make sure our producer waits until the message is received by Kafka before returning. This is to make sure the tests can send messages in a specific order
                put(ACKS_CONFIG, "all")
                put(MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
                put(LINGER_MS_CONFIG, "0")
                put(RETRIES_CONFIG, "0")
                put(SASL_MECHANISM, "PLAIN")
            }

        private fun consumerProperties(): MutableMap<String, Any>? {
            return HashMap<String, Any>().apply {
                put(BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
                put(SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                put(SASL_MECHANISM, "PLAIN")
                put(GROUP_ID_CONFIG, "sakComponentTest")
                put(AUTO_OFFSET_RESET_CONFIG, "earliest")
            }
        }

        @BeforeAll
        @JvmStatic
        internal fun `start embedded environment`() {
            embeddedPostgres = EmbeddedPostgres.builder().start()
            postgresConnection = embeddedPostgres.postgresDatabase.connection
            hikariConfig = createHikariConfig(embeddedPostgres.getJdbcUrl("postgres", "postgres"))
            runMigration(HikariDataSource(hikariConfig))

            embeddedKafkaEnvironment.start()
            adminClient = embeddedKafkaEnvironment.adminClient ?: fail("Klarte ikke få tak i adminclient")
            kafkaProducer = KafkaProducer(producerProperties(), StringSerializer(), StringSerializer())

            //Stub ID provider (for authentication of REST endpoints)
            wireMockServer.start()
            jwtStub = JwtStub("Microsoft Azure AD", wireMockServer)
            stubFor(jwtStub.stubbedJwkProvider())
            stubFor(jwtStub.stubbedConfigProvider())

            embeddedServer = embeddedServer(Netty, createTestApplicationConfig(applicationConfig(wireMockServer.baseUrl())))
                .start(wait = false)
        }

        @AfterAll
        @JvmStatic
        internal fun `stop embedded environment`() {
            embeddedServer.stop(1, 1, SECONDS)
            wireMockServer.stop()
            TestConsumer.close()
            adminClient.close()
            embeddedKafkaEnvironment.tearDown()

            postgresConnection.close()
            embeddedPostgres.close()
        }

    }

    @BeforeEach
    fun `create test consumer`() {
        TestConsumer.reset()
    }

    @Test
    fun `gitt en komplett tidslinje, når vi mottar sykepengehistorikk mer enn 6 måneder tilbake i tid, så skal saken til Speil for godkjenning`() {
        val aktørID = "87654321962"
        val virksomhetsnummer = "123456789"

        val søknad = sendNySøknad(aktørID, virksomhetsnummer)
        assertVedtaksperiodeEndretEvent(aktørId = aktørID, virksomhetsnummer = virksomhetsnummer, previousState = TilstandType.START, currentState = TilstandType.NY_SØKNAD_MOTTATT)
        sendSøknad(aktørID, virksomhetsnummer)
        assertVedtaksperiodeEndretEvent(aktørId = aktørID, virksomhetsnummer = virksomhetsnummer, previousState = TilstandType.NY_SØKNAD_MOTTATT, currentState = TilstandType.SENDT_SØKNAD_MOTTATT)
        sendInnteksmelding(aktørID, virksomhetsnummer)
        assertVedtaksperiodeEndretEvent(aktørId = aktørID, virksomhetsnummer = virksomhetsnummer, previousState = TilstandType.SENDT_SØKNAD_MOTTATT, currentState = TilstandType.KOMPLETT_SYKDOMSTIDSLINJE)

        val sykehistorikk = listOf(SpolePeriode(
            fom = søknad.fom!!.minusMonths(8),
            tom = søknad.fom!!.minusMonths(7),
            grad = "100"
        ))
        sendSykepengehistorikkløsning(aktørID, sykehistorikk)

        assertVedtaksperiodeEndretEvent(aktørId = aktørID, virksomhetsnummer = virksomhetsnummer, previousState = TilstandType.KOMPLETT_SYKDOMSTIDSLINJE, currentState = TilstandType.TIL_GODKJENNING)
        assertBehov(aktørId = aktørID, virksomhetsnummer = virksomhetsnummer, typer = listOf(GodkjenningFraSaksbehandler.name))

        aktørID.hentSak {
            assertTrue(this.contains("maksdato"))
            assertTrue(this.contains("utbetalingslinjer"))
            assertTrue(this.contains("dagsats"))
        }
    }

    @Test
    fun `gitt en sak for utbetaling, skal vi kunne hente opp saken via utbetalingsreferanse`() {
        val aktørID = "87659123421962"
        val virksomhetsnummer = "123456789"

        val søknad = sendNySøknad(aktørID, virksomhetsnummer)
        sendSøknad(aktørID, virksomhetsnummer)
        sendInnteksmelding(aktørID, virksomhetsnummer)

        val sykehistorikk = listOf(SpolePeriode(
                fom = søknad.fom!!.minusMonths(8),
                tom = søknad.fom!!.minusMonths(7),
                grad = "100"
        ))
        sendSykepengehistorikkløsning(aktørID, sykehistorikk)
        sendGodkjenningFraSaksbehandlerløsning(aktørID, true, "en_saksbehandler_ident")

        assertVedtaksperiodeEndretEvent(aktørId = aktørID, virksomhetsnummer = virksomhetsnummer, previousState = TilstandType.TIL_GODKJENNING, currentState = TilstandType.TIL_UTBETALING)

        val utbetalingsbehov = ventPåBehov(aktørId = aktørID, behovType = Utbetaling)
        val utbetalingsreferanse: String = utbetalingsbehov["utbetalingsreferanse"]!!

        utbetalingsreferanse.hentUtbetaling {
            val sak = Sak.fromJson(this)
            assertEquals(aktørID, sak.aktørId)
        }
    }

    @Test
    fun `gitt en ny sak, så skal den kunne hentes ut på saken`() {
        val enAktørId = "1211109876543"
        val virksomhetsnummer = "123456789"

        val nySøknad = sendNySøknad(enAktørId, virksomhetsnummer)

        enAktørId.hentSak {
            val lagretNySøknad = objectMapper.readTree(this).findValue("søknad")
            assertEquals(nySøknad.toJsonNode(), lagretNySøknad)
        }
    }

    private fun String.httpGet(testBlock: String.() -> Unit) {
        val token = jwtStub.createTokenFor(
                subject = "en_saksbehandler_ident",
                groups = listOf("sykepenger-saksbehandler-gruppe"),
                audience = "spleis_azure_ad_app_id"
        )

        val connection = embeddedServer.handleRequest(HttpMethod.Get, this,
                builder = {
                    setRequestProperty(Authorization, "Bearer $token")
                })

        assertEquals(HttpStatusCode.OK.value, connection.responseCode)

        connection.responseBody.testBlock()
    }

    private fun String.hentSak(testBlock: String.() -> Unit) {
        (path + this).httpGet(testBlock)
    }

    private fun String.hentUtbetaling(testBlock: String.() -> Unit) {
        ("/api/utbetaling/" + this).httpGet(testBlock)
    }

    private fun sendSykepengehistorikkløsning(aktørId: String, perioder: List<SpolePeriode>) {
        val behov = ventPåBehov(aktørId, Sykepengehistorikk)

        assertNotNull(behov["tom"])

        behov.løsBehov(TestConstants.responsFraSpole(
            perioder = perioder
        ))
        sendBehov(behov)
    }

    private fun sendGodkjenningFraSaksbehandlerløsning(aktørId: String, utbetalingGodkjent: Boolean, saksbehandler: String) {
        val behov = ventPåBehov(aktørId, GodkjenningFraSaksbehandler)
        behov.løsBehov(mapOf(
            "godkjent" to utbetalingGodkjent
        ))
        behov["saksbehandlerIdent"] = saksbehandler
        sendBehov(behov)
    }

    private fun sendInnteksmelding(aktorID: String, virksomhetsnummer: String, inntektsMelding: Inntektsmelding = inntektsmeldingDTO(aktørId = aktorID, virksomhetsnummer = virksomhetsnummer)) {
        synchronousSendKafkaMessage(inntektsmeldingTopic, inntektsMelding.inntektsmeldingId, inntektsMelding.toJsonNode().toString())
    }

    private fun sendSøknad(aktorID: String, virksomhetsnummer: String, sendtSøknad: SykepengesoknadDTO = søknadDTO(aktørId = aktorID, arbeidsgiver = ArbeidsgiverDTO(orgnummer = virksomhetsnummer), status = SoknadsstatusDTO.SENDT)) {
        synchronousSendKafkaMessage(søknadTopic, sendtSøknad.id!!, sendtSøknad.toJsonNode().toString())
    }

    private fun sendNySøknad(aktorID: String, virksomhetsnummer: String, nySøknad: SykepengesoknadDTO = søknadDTO(aktørId = aktorID, arbeidsgiver = ArbeidsgiverDTO(orgnummer = virksomhetsnummer), status = SoknadsstatusDTO.NY)): SykepengesoknadDTO {
        synchronousSendKafkaMessage(søknadTopic, nySøknad.id!!, nySøknad.toJsonNode().toString())
        return nySøknad
    }

    private fun sendBehov(behov: Behov) {
        sendKafkaMessage(behovTopic, behov.id().toString(), behov.toJson())
    }

    private fun ventPåBehov(aktørId: String, behovType: BehovsTyper): Behov {
        var behov: Behov? = null

        await()
            .atMost(5, SECONDS)
            .until {
                behov = TestConsumer.records(behovTopic)
                    .map { Behov.fromJson(it.value()) }
                    .filter { it.behovType() == behovType.name }
                    .firstOrNull { aktørId == it["aktørId"] }

                behov != null
            }

        return behov!!
    }

    private fun assertVedtaksperiodeEndretEvent(virksomhetsnummer: String, aktørId: String, previousState: TilstandType, currentState: TilstandType) {
        await()
                .atMost(5, SECONDS)
                .untilAsserted {
                    val meldingerPåTopic = TestConsumer.records(vedtaksperiodeEventTopic)
                    val vedtaksperiodeEndretHendelser = meldingerPåTopic
                            .map { objectMapper.readTree(it.value()) }
                            .filter { aktørId == it["aktørId"].textValue() }
                            .filter { virksomhetsnummer == it["organisasjonsnummer"].textValue() }
                            .filter { previousState == TilstandType.valueOf(it["previousState"].textValue())
                                    && currentState == TilstandType.valueOf(it["currentState"].textValue()) }

                    assertEquals(1, vedtaksperiodeEndretHendelser.size)
                }
    }

    private fun assertBehov(virksomhetsnummer: String, aktørId: String, typer: List<String>) {
        await()
            .atMost(5, SECONDS)
            .untilAsserted {
                val meldingerPåTopic = TestConsumer.records(behovTopic)
                val behov = meldingerPåTopic
                    .map { Behov.fromJson(it.value()) }
                    .filter { aktørId == it["aktørId"] }
                    .filter { virksomhetsnummer == it["organisasjonsnummer"] }
                    .filter { it.behovType() in typer }
                    .map(Behov::behovType)
                    .distinct()

                assertEquals(typer, behov)
            }
    }

    private fun assertOpprettGosysOppgave(aktørId: String) {
        await()
            .atMost(5, SECONDS)
            .untilAsserted {
                val opprettGosysOppgaveList = TestConsumer.records(opprettGosysOppgaveTopic)
                    .map { objectMapper.readValue<OpprettGosysOppgaveDto>(it.value()) }
                assertTrue(opprettGosysOppgaveList.any { aktørId == it.aktorId })
            }
    }

    private fun sendKafkaMessage(topic: String, key: String, message: String) =
        kafkaProducer.send(ProducerRecord(topic, key, message))

    /**
     * Trick Kafka into behaving synchronously by sending the message, and then confirming that it is read by the consumer group
     */
    private fun synchronousSendKafkaMessage(topic: String, key: String, message: String) {
        val metadata = sendKafkaMessage(topic, key, message)
        kafkaProducer.flush()
        metadata.get().assertMessageIsConsumed()
    }

    /**
     * Check that the consumers has received this message, by comparing the position of the message with the reported last read message of the consumer group
     */
    private fun RecordMetadata.assertMessageIsConsumed(recordMetadata: RecordMetadata = this) {
        await()
            .atMost(5, SECONDS)
            .untilAsserted {
                val offsetAndMetadataMap = adminClient.listConsumerGroupOffsets(kafkaApplicationId).partitionsToOffsetAndMetadata().get()
                val topicPartition = TopicPartition(recordMetadata.topic(), recordMetadata.partition())
                val currentPositionOfSentMessage = recordMetadata.offset()
                val currentConsumerGroupPosition = offsetAndMetadataMap[topicPartition]?.offset()?.minus(1)
                    ?: fail() // This offset represents next position to read from, so we subtract 1 to get the last read offset
                assertEquals(currentConsumerGroupPosition, currentPositionOfSentMessage)
            }
    }

    private object TestConsumer {
        private val records = mutableListOf<ConsumerRecord<String, String>>()

        private val kafkaConsumer = KafkaConsumer(consumerProperties(), StringDeserializer(), StringDeserializer()).also {
            it.subscribe(topics)
        }

        fun reset() {
            records.clear()
        }

        fun records(topic: String) = records().filter { it.topic() == topic }

        fun records() =
            records.also { it.addAll(kafkaConsumer.poll(ofMillis(0))) }

        fun close() {
            kafkaConsumer.unsubscribe()
            kafkaConsumer.close()
        }
    }
}