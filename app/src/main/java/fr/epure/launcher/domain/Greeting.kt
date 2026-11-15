package fr.epure.launcher.domain

import java.time.DayOfWeek

/** Salutation de l'accueil typographique : « PASSE / UN [BON] / DIMANCHE ». */
object Greeting {

    /** [highlight] est affiché en négatif (texte sombre dans un cadre clair). */
    data class Lines(val first: String, val second: String, val highlight: String, val last: String)

    private val days = mapOf(
        DayOfWeek.MONDAY to "LUNDI",
        DayOfWeek.TUESDAY to "MARDI",
        DayOfWeek.WEDNESDAY to "MERCREDI",
        DayOfWeek.THURSDAY to "JEUDI",
        DayOfWeek.FRIDAY to "VENDREDI",
        DayOfWeek.SATURDAY to "SAMEDI",
        DayOfWeek.SUNDAY to "DIMANCHE",
    )

    fun forDay(day: DayOfWeek): Lines = Lines(first = "PASSE", second = "UN", highlight = "BON", last = days.getValue(day))
}
