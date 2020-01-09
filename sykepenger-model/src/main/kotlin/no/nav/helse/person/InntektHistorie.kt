package no.nav.helse.person

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.util.RawValue
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelser.Inntektsmelding
import java.math.BigDecimal
import java.time.LocalDate

internal class InntektHistorie {
    private class Inntekt(val fom: LocalDate, val hendelse: ArbeidstakerHendelse, val beløp: BigDecimal)

    private val inntekter = mutableListOf<Inntekt>()

    fun add(dagen: LocalDate, hendelse: ArbeidstakerHendelse, beløp: BigDecimal) {
        inntekter.add(Inntekt(dagen, hendelse, beløp))
    }

    fun add(inntektsmelding: Inntektsmelding) {
        inntektsmelding.beregnetInntekt ?.run {
            inntekter.add(
                Inntekt(
                    inntektsmelding.førsteFraværsdag,
                    inntektsmelding,
                    this
                )
            )
        }
    }

    internal class Memento internal constructor(
        internal val inntekter: List<Inntekt> = emptyList()
    ) {

        fun state(): JsonNode {
            return objectMapper.createObjectNode().also {
                it.set<ArrayNode>("inntekter", inntekter.fold(objectMapper.createArrayNode()) { result, current ->
                    result.addRawValue(RawValue(current.state()))
                })
            }
        }

        internal class Inntekt(val fom: LocalDate, val hendelse: ArbeidstakerHendelse, val beløp: BigDecimal) {
            internal companion object {
                fun fromJsonNode(json: JsonNode) =
                    Inntekt(
                        fom = LocalDate.parse(json["fom"].textValue()),
                        hendelse = ArbeidstakerHendelse.fromJson(json["hendelse"].asText()),
                        beløp = json["beløp"].decimalValue()
                    )
            }

            fun state(): JsonNode = objectMapper.convertValue<ObjectNode>(
                mapOf("fom" to this.fom, "hendelse" to this.hendelse.toJson(), "beløp" to this.beløp)
            )
        }

        internal companion object {
            val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)


            fun fromJsonNode(json: JsonNode): Memento {
                return Memento(
                    inntekter = json["inntekter"].map {
                        Inntekt.fromJsonNode(it)
                    }
                )
            }
        }
    }

    internal companion object {
        fun restore(memento: Memento) = InntektHistorie().apply {
            memento.inntekter.forEach { this.add(it.fom, it.hendelse, it.beløp) }
        }
    }

    internal fun memento() = Memento(inntekter.map {
        Memento.Inntekt(
            it.fom,
            it.hendelse,
            it.beløp
        )
    })

}