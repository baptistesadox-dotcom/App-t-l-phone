package fr.epure.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Hôte des widgets Android (ceux des autres apps : météo, agenda, notes…).
 * L'identifiant d'hôte doit rester le même d'une version à l'autre.
 */
class LauncherWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherWidgetHostView(context)

    companion object {
        const val HOST_ID = 0x45505552 // « EPUR »
    }
}

/**
 * Vue d'un widget qui détecte l'appui long (pour ouvrir le menu du widget)
 * sans gêner les boutons du widget lui-même.
 */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressPerformed = false

    private val longPressRunnable = Runnable {
        if (parent != null && hasWindowFocus()) {
            // À partir d'ici, onInterceptTouchEvent renvoie vrai : les boutons du widget
            // reçoivent ACTION_CANCEL et ne déclenchent pas de clic.
            longPressPerformed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    private fun trackLongPress(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressPerformed = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) removeCallbacks(longPressRunnable)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN ->
                removeCallbacks(longPressRunnable)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackLongPress(ev)
        return longPressPerformed
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackLongPress(ev)
        // On garde la suite du geste pour détecter l'appui long sur les zones vides du widget.
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(longPressRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    /** Indique au widget la place dont il dispose, pour qu'il choisisse sa mise en page. */
    fun applySize(widthDp: Int, heightDp: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        }
    }
}

/** Widget proposé dans le sélecteur, regroupé par app. */
data class WidgetProviderEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    /** Taille minimale en dp. */
    val minWidthDp: Int,
    val minHeightDp: Int,
)

object WidgetProviders {

    fun list(context: Context): List<WidgetProviderEntry> {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        val profiles = context.getSystemService(android.os.UserManager::class.java).userProfiles
        return profiles.flatMap { user ->
            runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList())
        }.map { info ->
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            WidgetProviderEntry(
                info = info,
                label = runCatching { info.loadLabel(pm) }.getOrNull().orEmpty().ifEmpty { appLabel },
                appLabel = appLabel,
                minWidthDp = (info.minWidth / density).toInt(),
                minHeightDp = (info.minHeight / density).toInt(),
            )
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    /** Hauteur de départ d'un nouveau widget, d'après sa taille minimale. */
    fun defaultHeightDp(entry: WidgetProviderEntry): Int = when {
        entry.minHeightDp <= 0 -> 120
        else -> (entry.minHeightDp + 16).coerceIn(80, 320)
    }
}
