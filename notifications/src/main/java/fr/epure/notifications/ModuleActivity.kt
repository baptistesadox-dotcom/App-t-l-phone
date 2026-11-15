package fr.epure.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import fr.epure.shared.NotificationsContract

/** Écran d'aide du module : état de l'accès aux notifications et marche à suivre. */
class ModuleActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var lockStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        fun text(resId: Int, sizeSp: Float, bold: Boolean = false) = TextView(this).apply {
            setText(resId)
            textSize = sizeSp
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, pad / 2)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(text(R.string.module_title, 26f, bold = true))
            addView(text(R.string.module_intro, 16f))
            status = TextView(this@ModuleActivity).apply {
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, pad / 2, 0, pad / 2)
            }
            addView(status)
            addView(Button(this@ModuleActivity).apply {
                setText(R.string.button_grant)
                setOnClickListener { open(ModuleBridge.accessSettingsIntent(), Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            })
            addView(text(R.string.restricted_help, 14f).apply { setPadding(0, pad, 0, pad / 2) })
            addView(Button(this@ModuleActivity).apply {
                setText(R.string.button_app_info)
                setOnClickListener {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
            })
            addView(text(R.string.lock_title, 20f, bold = true).apply { setPadding(0, pad * 2, 0, pad / 2) })
            lockStatus = TextView(this@ModuleActivity).apply {
                textSize = 15f
                setPadding(0, 0, 0, pad / 2)
            }
            addView(lockStatus)
            addView(Button(this@ModuleActivity).apply {
                setText(R.string.button_lock)
                setOnClickListener { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
        }
        val root = ScrollView(this).apply { addView(content) }
        // Le contenu ne passe pas sous les barres système (affichage bord à bord d'Android 15+).
        ViewCompat.setOnApplyWindowInsetsListener(root) { view: View, insets: WindowInsetsCompat ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val on = ModuleBridge.hasAccess(this)
        status.setText(if (on) R.string.status_on else R.string.status_off)
        if (on) ModuleListenerService.instance?.publish()
        val lockOn = NotificationsContract.isLockServiceEnabled(
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        )
        lockStatus.setText(if (lockOn) R.string.lock_status_on else R.string.lock_status_off)
    }

    private fun open(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            fallback?.let { runCatching { startActivity(it) } }
        }
    }
}
