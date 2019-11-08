package no.nav.helse.spleis

import no.nav.helse.behov.Behov
import no.nav.helse.behov.BehovProducer
import no.nav.helse.person.*
import no.nav.helse.person.Sakskompleks.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.hendelser.inntektsmelding.InntektsmeldingHendelse
import no.nav.helse.person.hendelser.saksbehandling.ManuellSaksbehandlingHendelse
import no.nav.helse.person.hendelser.sykepengehistorikk.SykepengehistorikkHendelse
import no.nav.helse.person.hendelser.søknad.NySøknadHendelse
import no.nav.helse.person.hendelser.søknad.SendtSøknadHendelse
import no.nav.helse.spleis.oppgave.GosysOppgaveProducer

internal class PersonMediator(private val personRepository: PersonRepository,
                              private val lagrePersonDao: PersonObserver,
                              private val sakskompleksProbe: SakskompleksProbe = SakskompleksProbe,
                              private val behovProducer: BehovProducer,
                              private val gosysOppgaveProducer: GosysOppgaveProducer) : PersonObserver {

    override fun personEndret(personEndretEvent: PersonObserver.PersonEndretEvent) {}

    override fun sakskompleksTrengerLøsning(event: Behov) {
        behovProducer.sendNyttBehov(event)
    }

    fun håndterNySøknad(nySøknadHendelse: NySøknadHendelse) =
            try {
                finnPerson(nySøknadHendelse)
                        .also { person ->
                            person.håndterNySøknad(nySøknadHendelse)
                        }
            } catch (err: UtenforOmfangException) {
                sakskompleksProbe.utenforOmfang(err, nySøknadHendelse)
            } catch (err: PersonskjemaForGammelt) {
                sakskompleksProbe.forGammelSkjemaversjon(err)
            }

    fun håndterSendtSøknad(sendtSøknadHendelse: SendtSøknadHendelse) =
            try {
                finnPerson(sendtSøknadHendelse)
                        .also { person ->
                            person.håndterSendtSøknad(sendtSøknadHendelse)
                        }
            } catch (err: UtenforOmfangException) {
                sakskompleksProbe.utenforOmfang(err, sendtSøknadHendelse)
            } catch (err: PersonskjemaForGammelt) {
                sakskompleksProbe.forGammelSkjemaversjon(err)
            }

    fun håndterInntektsmelding(inntektsmeldingHendelse: InntektsmeldingHendelse) =
            try {
                finnPerson(inntektsmeldingHendelse).also { person ->
                    person.håndterInntektsmelding(inntektsmeldingHendelse)
                }
            } catch (err: UtenforOmfangException) {
                sakskompleksProbe.utenforOmfang(err, inntektsmeldingHendelse)
            } catch (err: PersonskjemaForGammelt) {
                sakskompleksProbe.forGammelSkjemaversjon(err)
            }

    fun håndterSykepengehistorikk(sykepengehistorikkHendelse: SykepengehistorikkHendelse) {
        try {
            finnPerson(sykepengehistorikkHendelse).also { person ->
                person.håndterSykepengehistorikk(sykepengehistorikkHendelse)
            }
        } catch (err: PersonskjemaForGammelt) {
            sakskompleksProbe.forGammelSkjemaversjon(err)
        }
    }

    fun håndterManuellSaksbehandling(manuellSaksbehandlingHendelse: ManuellSaksbehandlingHendelse) {
        try {
            finnPerson(manuellSaksbehandlingHendelse).also { person ->
                person.håndterManuellSaksbehandling(manuellSaksbehandlingHendelse)
            }
        } catch (err: PersonskjemaForGammelt) {
            sakskompleksProbe.forGammelSkjemaversjon(err)
        }
    }

    fun hentPersonJson(aktørId: String): String? = personRepository.hentPersonJson(aktørId)

    override fun sakskompleksEndret(event: SakskompleksObserver.StateChangeEvent) {
        if (event.currentState == TIL_INFOTRYGD) {
            gosysOppgaveProducer.opprettOppgave(event.aktørId)
        }
    }

    private fun finnPerson(arbeidstakerHendelse: ArbeidstakerHendelse) =
            (personRepository.hentPerson(arbeidstakerHendelse.aktørId()) ?: Person(aktørId = arbeidstakerHendelse.aktørId())).also {
                it.addObserver(this)
                it.addObserver(lagrePersonDao)
                it.addObserver(sakskompleksProbe)
            }

}