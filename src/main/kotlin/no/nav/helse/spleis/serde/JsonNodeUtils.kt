package no.nav.helse.spleis.serde

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

fun JsonNode?.safelyUnwrapDate(): LocalDate? {
    return if (this?.isNull != false) {
        null
    } else {
        LocalDate.parse(textValue())
    }
}