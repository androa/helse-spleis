package no.nav.helse.unit

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.createConfigFromEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
class AppTest {

    @Test
    fun `skal sette sammen jdbc-url fra host, port og databasenavn`() {
        val appConfig = createConfigFromEnvironment(mapOf(
                "DATABASE_HOST" to "localhost",
                "DATABASE_PORT" to "1234",
                "DATABASE_NAME" to "foo",
                "AZURE_CLIENT_ID" to "foo",
                "AZURE_CLIENT_SECRET" to "bar"
        ))

        assertEquals("jdbc:postgresql://localhost:1234/foo", appConfig.property("database.jdbc-url").getString())
    }

    @Test
    fun `skal sette sammen jdbc-url fra host, port, databasenavn og brukernavn dersom satt`() {
        val appConfig = createConfigFromEnvironment(mapOf(
                "DATABASE_HOST" to "localhost",
                "DATABASE_PORT" to "1234",
                "DATABASE_NAME" to "foo",
                "DATABASE_USERNAME" to "bar",
                "AZURE_CLIENT_ID" to "foo",
                "AZURE_CLIENT_SECRET" to "bar"
        ))

        assertEquals("jdbc:postgresql://localhost:1234/foo?user=bar", appConfig.property("database.jdbc-url").getString())
    }

    @Test
    fun `skal ikke sette sammen jdbc-url når url er gitt som miljøvariabel`() {
        val jdbcUrl = "jdbc:postgresql://db.example.org:5132/mydb"

        val appConfig = createConfigFromEnvironment(mapOf(
                "DATABASE_JDBC_URL" to jdbcUrl,
                "DATABASE_HOST" to "localhost",
                "DATABASE_PORT" to "1234",
                "DATABASE_NAME" to "foo",
                "AZURE_CLIENT_ID" to "foo",
                "AZURE_CLIENT_SECRET" to "bar"
        ))

        assertEquals(jdbcUrl, appConfig.property("database.jdbc-url").getString())
    }
}