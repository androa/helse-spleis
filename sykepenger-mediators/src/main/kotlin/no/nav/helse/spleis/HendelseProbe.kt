package no.nav.helse.spleis

import io.prometheus.client.Counter
import no.nav.helse.hendelser.*
import no.nav.helse.sak.ArbeidstakerHendelse
import org.slf4j.LoggerFactory

class HendelseProbe: HendelseListener {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

        private val hendelseCounter = Counter.build("hendelser_totals", "Antall hendelser mottatt")
            .labelNames("type")
            .register()

        private val påminnetCounter =
            Counter.build("paminnet_totals", "Antall ganger vi har mottatt en påminnelse")
                .labelNames("tilstand")
                .register()
    }

    override fun onPåminnelse(påminnelse: Påminnelse) {
        påminnetCounter
            .labels(påminnelse.tilstand.toString())
            .inc()
        påminnelse.tell()
    }

    override fun onYtelser(ytelser: Ytelser) {
        ytelser.tell()
    }

    override fun onManuellSaksbehandling(manuellSaksbehandling: ManuellSaksbehandling) {
        manuellSaksbehandling.tell()
    }

    override fun onInntektsmelding(inntektsmelding: Inntektsmelding) {
        inntektsmelding.tell()
    }

    override fun onNySøknad(søknad: NySøknad) {
        søknad.tell()
    }

    override fun onSendtSøknad(søknad: SendtSøknad) {
        søknad.tell()
    }

    private fun ArbeidstakerHendelse.tell() {
        sikkerLogg.info(this.toJson())
        hendelseCounter.labels(this.hendelsetype().name).inc()
    }
}