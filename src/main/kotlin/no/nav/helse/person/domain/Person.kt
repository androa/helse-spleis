package no.nav.helse.person.domain

import no.nav.helse.inntektsmelding.domain.Inntektsmelding
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.KildeHendelse
import no.nav.helse.søknad.domain.Sykepengesøknad
import java.util.*

class Person: SakskompleksObserver {
    private val arbeidsgivere = mutableMapOf<String, Arbeidsgiver>()

    private val personObservers = mutableListOf<PersonObserver>()
    fun håndterNySøknad(søknad: Sykepengesøknad) {
        require(søknad.erNy()) { "søknad må være ny" }

        findOrCreateArbeidsgiver(søknad).håndterNySøknad(søknad)
    }

    fun håndterSendtSøknad(søknad: Sykepengesøknad) {
        require(søknad.erSendt()) { "søknad må være sendt" }

        findOrCreateArbeidsgiver(søknad).håndterSendtSøknad(søknad)
    }

    fun håndterInntektsmelding(inntektsmelding: Inntektsmelding) {

    }

    override fun sakskompleksChanged(event: SakskompleksObserver.StateChangeEvent) {
        personObservers.forEach {
            it.personEndret(this)
        }
    }

    fun addObserver(observer: PersonObserver) {
        personObservers.add(observer)
        arbeidsgivere.values.forEach { it.addObserver(observer) }
    }

    private fun findOrCreateArbeidsgiver(hendelse: Sykdomshendelse) =
            arbeidsgivere.getOrPut(hendelse.organisasjonsnummer()) {
                Arbeidsgiver(hendelse).also {
                    it.addObserver(this)
                    personObservers.forEach { personObserver ->
                        it.addObserver(personObserver)
                    }
                }
            }

    internal inner class Arbeidsgiver(hendelse: Sykdomshendelse) {
        private val saker = mutableListOf<Sakskompleks>()
        private val sakskompleksObservers = mutableListOf<SakskompleksObserver>()

        fun håndterNySøknad(søknad: Sykepengesøknad) {
            findOrCreateSakskompleks(søknad).leggTil(søknad)
        }

        fun håndterSendtSøknad(søknad: Sykepengesøknad) {
            findOrCreateSakskompleks(søknad).leggTil(søknad)
        }

        fun addObserver(observer: SakskompleksObserver) {
            sakskompleksObservers.add(observer)
            saker.forEach { it.addObserver(observer) }
        }

        private fun findOrCreateSakskompleks(hendelse: Sykdomshendelse) : Sakskompleks {
            return Sakskompleks(UUID.randomUUID(), hendelse.aktørId()).also {
                sakskompleksObservers.forEach(it::addObserver)
                saker.add(it)
            }
        }

        private val organisasjonsnummer = hendelse.organisasjonsnummer()
    }
}

interface PersonObserver : SakskompleksObserver {

    fun personEndret(person: Person)
}

interface Sykdomshendelse : KildeHendelse {
    fun aktørId(): String
    fun organisasjonsnummer(): String

    fun sykdomstidslinje(): Sykdomstidslinje
}
