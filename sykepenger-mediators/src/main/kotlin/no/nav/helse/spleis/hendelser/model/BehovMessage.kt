package no.nav.helse.spleis.hendelser.model

import no.nav.helse.behov.Behovstype
import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.spleis.hendelser.JsonMessage
import no.nav.helse.spleis.hendelser.MessageFactory
import no.nav.helse.spleis.hendelser.MessageProcessor

// Understands a JSON message representing a Behov
internal abstract class BehovMessage(originalMessage: String, private val problems: Aktivitetslogger) :
    JsonMessage(originalMessage, problems) {
    init {
        requiredKey(
            "@behov", "@id", "@opprettet",
            "@final", "@løsning", "@besvart",
            "hendelse", "aktørId", "fødselsnummer",
            "organisasjonsnummer", "vedtaksperiodeId"
        )
        requiredValue("@final", true)
    }
}

internal class YtelserMessage(originalMessage: String, private val problems: Aktivitetslogger) :
    BehovMessage(originalMessage, problems) {
    init {
        requiredValues("@behov", Behovstype.Sykepengehistorikk, Behovstype.Foreldrepenger)
    }

    override fun accept(processor: MessageProcessor) {
        processor.process(this, problems)
    }

    object Factory : MessageFactory<YtelserMessage> {

        override fun createMessage(message: String, problems: Aktivitetslogger): YtelserMessage {
            return YtelserMessage(message, problems)
        }
    }
}

internal class VilkårsgrunnlagMessage(originalMessage: String, private val problems: Aktivitetslogger) :
    BehovMessage(originalMessage, problems) {
    init {
        requiredValues("@behov", Behovstype.Inntektsberegning, Behovstype.EgenAnsatt)
    }

    override fun accept(processor: MessageProcessor) {
        processor.process(this, problems)
    }

    object Factory : MessageFactory<VilkårsgrunnlagMessage> {

        override fun createMessage(message: String, problems: Aktivitetslogger): VilkårsgrunnlagMessage {
            return VilkårsgrunnlagMessage(message, problems)
        }
    }
}

internal class ManuellSaksbehandlingMessage(originalMessage: String, private val problems: Aktivitetslogger) :
    BehovMessage(originalMessage, problems) {
    init {
        requiredValues("@behov", Behovstype.GodkjenningFraSaksbehandler)
    }

    override fun accept(processor: MessageProcessor) {
        processor.process(this, problems)
    }

    object Factory : MessageFactory<ManuellSaksbehandlingMessage> {

        override fun createMessage(message: String, problems: Aktivitetslogger): ManuellSaksbehandlingMessage {
            return ManuellSaksbehandlingMessage(message, problems)
        }
    }
}