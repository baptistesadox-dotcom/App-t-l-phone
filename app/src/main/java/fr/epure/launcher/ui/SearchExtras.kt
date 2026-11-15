package fr.epure.launcher.ui

import fr.epure.launcher.data.config.SearchConfig
import fr.epure.launcher.data.contacts.ContactResult
import fr.epure.launcher.domain.Calculator
import fr.epure.launcher.domain.SystemShortcut
import fr.epure.launcher.domain.SystemShortcuts

/** Résultat d'un calcul tapé dans la recherche. */
data class Calculation(val result: String, val plain: String)

/** Résultats de la recherche en plus des apps : calcul, contacts, réglages d'Android. */
data class SearchExtras(
    val calculation: Calculation? = null,
    val contacts: List<ContactResult> = emptyList(),
    val shortcuts: List<SystemShortcut> = emptyList(),
    /** Recherche dans les contacts activée, mais l'accès aux contacts n'est pas autorisé. */
    val askContactsPermission: Boolean = false,
) {
    val isEmpty: Boolean get() = calculation == null && contacts.isEmpty() && shortcuts.isEmpty()

    companion object {
        /**
         * Partie instantanée (calcul et réglages). Un calcul occupe toute la place : on ne
         * cherche alors ni contacts ni réglages.
         */
        fun instant(query: String, settings: SearchConfig): SearchExtras {
            val q = query.trim()
            if (q.isEmpty()) return SearchExtras()
            val calculation = if (settings.calculator) {
                Calculator.evaluate(q)?.let { Calculation(Calculator.format(it), Calculator.format(it, grouping = false)) }
            } else {
                null
            }
            if (calculation != null) return SearchExtras(calculation = calculation)
            return SearchExtras(shortcuts = if (settings.systemSettings) SystemShortcuts.search(q) else emptyList())
        }

        /** Faut-il interroger les contacts pour cette recherche ? */
        fun wantsContacts(query: String, settings: SearchConfig, instant: SearchExtras): Boolean =
            settings.contacts && instant.calculation == null && query.trim().length >= 2
    }
}
