package no.nav.helse.sykdomstidslinje.dag

import java.time.LocalDate

// GoF Factory pattern
internal interface DagFactory {
    fun arbeidsdag(dato: LocalDate): Arbeidsdag { error("Arbeidsdag ikke støttet") }
    fun egenmeldingsdag(dato: LocalDate): Egenmeldingsdag { error("Egenmeldingsdag ikke støttet") }
    fun feriedag(dato: LocalDate): Feriedag { error("Feriedag ikke støttet") }
    fun implisittDag(dato: LocalDate): ImplisittDag =
        ImplisittDag(dato)
    fun permisjonsdag(dato: LocalDate): Permisjonsdag { error("Permisjonsdag ikke støttet") }
    fun studiedag(dato: LocalDate): Studiedag =
        Studiedag(dato)
    fun sykedag(dato: LocalDate): Sykedag { error("Sykedag ikke støttet") }
    fun sykHelgedag(dato: LocalDate): SykHelgedag =
        SykHelgedag(dato)
    fun ubestemtdag(dato: LocalDate): Ubestemtdag =
        Ubestemtdag(dato)
    fun utenlandsdag(dato: LocalDate): Utenlandsdag =
        Utenlandsdag(dato)
}