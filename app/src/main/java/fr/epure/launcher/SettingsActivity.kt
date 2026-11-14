package fr.epure.launcher

import android.Manifest
import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.ui.common.LocalIconProvider
import fr.epure.launcher.ui.common.LoaderIconProvider
import fr.epure.launcher.ui.settings.SettingsApp
import fr.epure.launcher.ui.settings.SettingsCallbacks
import fr.epure.launcher.ui.settings.SettingsPage
import fr.epure.launcher.ui.settings.SettingsViewModel
import fr.epure.launcher.ui.theme.LauncherTheme
import fr.epure.launcher.ui.theme.LocalLauncherLook
import fr.epure.launcher.widgets.LauncherWidgetHost
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Réglages d'Épure. C'est aussi l'icône affichée dans la liste des apps. */
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private val isDefaultLauncher = mutableStateOf(false)
    private val pendingImport = mutableStateOf<LauncherConfig?>(null)

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, R.string.agenda_permission_denied, Toast.LENGTH_LONG).show()
    }

    private val defaultLauncherRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultLauncher.value = SystemActions.isDefaultLauncher(this)
        if (isDefaultLauncher.value) {
            // Ouvre directement le nouvel écran d'accueil.
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = viewModel.importFont(uri)
            Toast.makeText(this@SettingsActivity, if (ok) R.string.font_import_ok else R.string.font_import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = viewModel.exportTo(uri)
            Toast.makeText(this@SettingsActivity, if (ok) R.string.backup_export_ok else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val config = viewModel.readBackup(uri)
            if (config == null) Toast.makeText(this@SettingsActivity, R.string.backup_invalid, Toast.LENGTH_LONG).show()
            else pendingImport.value = config
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val startPage = intent.getStringExtra(EXTRA_PAGE)
            ?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
            ?: SettingsPage.MAIN
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

        setContent {
            val config by viewModel.config.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val iconGeneration by appContainer.iconLoader.generation.collectAsStateWithLifecycle()
            val current = config ?: return@setContent
            val iconPack = current.appearance.iconPack
            val iconProvider = remember(iconPack, iconGeneration) { LoaderIconProvider(appContainer.iconLoader, iconPack) }

            LifecycleResumeEffect(Unit) {
                isDefaultLauncher.value = SystemActions.isDefaultLauncher(this@SettingsActivity)
                onPauseOrDispose { }
            }

            LauncherTheme(appearance = current.appearance) {
                val dark = LocalLauncherLook.current.isDark
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                CompositionLocalProvider(LocalIconProvider provides iconProvider) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SettingsApp(
                            vm = viewModel,
                            config = current,
                            state = state,
                            startPage = startPage,
                            isDefaultLauncher = isDefaultLauncher.value,
                            versionName = versionName,
                            callbacks = callbacks,
                            onFinish = ::finish,
                        )
                    }
                    pendingImport.value?.let { imported ->
                        AlertDialog(
                            onDismissRequest = { pendingImport.value = null },
                            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
                            text = { Text(stringResource(R.string.backup_import_confirm_text)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.applyBackup(imported)
                                    pendingImport.value = null
                                    Toast.makeText(this@SettingsActivity, R.string.backup_import_ok, Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.backup_import_apply)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingImport.value = null }) { Text(stringResource(R.string.action_cancel)) }
                            },
                        )
                    }
                }
            }
        }
    }

    private val callbacks = object : SettingsCallbacks {
        override fun requestDefaultLauncher() {
            runCatching { defaultLauncherRequest.launch(SystemActions.defaultLauncherIntent(this@SettingsActivity)) }
                .onFailure { SystemActions.start(this@SettingsActivity, Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }

        override fun requestCalendarPermission() = calendarPermission.launch(Manifest.permission.READ_CALENDAR)

        override fun hasCalendarPermission(): Boolean =
            ContextCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

        override fun pickFont() {
            runCatching {
                pickFont.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream"))
            }.onFailure { showMessage(getString(R.string.error_no_app)) }
        }

        override fun exportBackup() {
            runCatching { exportBackup.launch("epure-sauvegarde-${LocalDate.now()}.json") }
                .onFailure { showMessage(getString(R.string.error_no_app)) }
        }

        override fun importBackup() {
            runCatching { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                .onFailure { showMessage(getString(R.string.error_no_app)) }
        }

        override fun addAndroidWidget() {
            startActivity(Intent(this@SettingsActivity, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_WIDGET))
            finish()
        }

        override fun removeAndroidWidget(appWidgetId: Int) {
            runCatching { (LauncherWidgetHost(this@SettingsActivity) as AppWidgetHost).deleteAppWidgetId(appWidgetId) }
        }

        override fun openWallpaperPicker() = SystemActions.openWallpaperPicker(this@SettingsActivity)

        override fun openUrl(url: String) {
            SystemActions.start(this@SettingsActivity, Intent(Intent.ACTION_VIEW, Uri.parse(url)), getString(R.string.error_no_app))
        }

        override fun showMessage(message: String) {
            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
        }

        override fun hasNotificationAccess(): Boolean = appContainer.notificationHub.hasAccess()

        override fun openNotificationAccess() {
            val hub = appContainer.notificationHub
            if (!SystemActions.start(this@SettingsActivity, hub.accessSettingsIntent())) {
                SystemActions.start(
                    this@SettingsActivity,
                    Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    getString(R.string.error_no_app),
                )
            }
        }

        override fun openAppDetails() = SystemActions.openAppSettings(this@SettingsActivity)
    }

    companion object {
        const val EXTRA_PAGE = "page"
        val PAGE_WIDGETS = SettingsPage.WIDGETS.name
    }
}
