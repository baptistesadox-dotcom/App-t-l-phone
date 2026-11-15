package fr.epure.notifications

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Verrouillage de l'écran pour le double appui sur l'accueil Épure.
 *
 * C'est un service d'accessibilité car Android ne permet de verrouiller l'écran (comme le
 * bouton marche/arrêt, empreinte et visage restant utilisables) que par ce biais. Il ne lit
 * rien à l'écran et ne reçoit aucun événement : il attend seulement la demande d'Épure.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun lock(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    companion object {
        @Volatile
        var instance: LockService? = null
    }
}
