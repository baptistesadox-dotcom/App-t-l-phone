package fr.epure.launcher.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import fr.epure.launcher.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Problème rencontré lors d'une vérification ou d'une installation. */
enum class UpdateError {
    /** Pas de connexion, ou GitHub injoignable. */
    NETWORK,

    /** Fichier de versions illisible. */
    INVALID,

    /** APK téléchargé différent de celui publié (empreinte SHA-256). */
    CORRUPTED,

    /** Android a refusé l'installation (voir le message). */
    INSTALL_FAILED,
}

/** Téléchargement ou installation en cours. */
data class InstallProgress(
    val target: UpdateTarget,
    /** Avancement du téléchargement (0 à 1), ou null pendant l'installation. */
    val fraction: Float?,
)

data class UpdateState(
    val checking: Boolean = false,
    val lastCheckMillis: Long = 0L,
    val manifest: UpdateManifest? = null,
    /** Mises à jour des apps installées. */
    val updates: List<AvailableUpdate> = emptyList(),
    /** Module de notifications publié mais pas installé : on peut l'installer d'ici. */
    val moduleOffer: UpdateEntry? = null,
    val progress: InstallProgress? = null,
    val error: UpdateError? = null,
    /** Message d'Android quand l'installation échoue. */
    val errorMessage: String? = null,
    /** Écran de confirmation d'Android à afficher (lancé par l'activité visible). */
    val confirmation: Intent? = null,
) {
    val launcherUpdate: AvailableUpdate? get() = updates.firstOrNull { it.target == UpdateTarget.LAUNCHER }
}

/**
 * Mises à jour d'Épure et du module, publiées dans le dépôt GitHub (dossier `updates/`).
 * Vérification automatique au plus deux fois par jour, téléchargement vérifié par
 * empreinte, puis installation par Android (qui exige la même clé de signature).
 */
class UpdateRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("mises_a_jour", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UpdateState(lastCheckMillis = prefs.getLong(KEY_LAST_CHECK, 0L)))
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var installJob: Job? = null

    fun installedVersion(target: UpdateTarget): Long? = runCatching {
        PackageInfoCompat.getLongVersionCode(appContext.packageManager.getPackageInfo(target.packageName, 0))
    }.getOrNull()

    /** Vérifie s'il y a du nouveau, si la dernière vérification date de plus de 12 heures. */
    fun checkIfDue(now: Long = System.currentTimeMillis()) {
        if (now - _state.value.lastCheckMillis >= CHECK_INTERVAL_MS) check()
    }

    /** Vérifie tout de suite (bouton « Rechercher une mise à jour »). */
    fun check() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _state.update { it.copy(checking = true, error = null, errorMessage = null) }
            val manifest = withContext(Dispatchers.IO) { runCatching { fetchManifest() } }
            val now = System.currentTimeMillis()
            manifest.onSuccess { result ->
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                if (result == null) {
                    _state.update { it.copy(checking = false, lastCheckMillis = now, error = UpdateError.INVALID) }
                } else {
                    _state.update { it.copy(checking = false, lastCheckMillis = now).withManifest(result) }
                }
            }.onFailure { e ->
                Log.i(TAG, "Vérification impossible", e)
                _state.update { it.copy(checking = false, error = UpdateError.NETWORK) }
            }
        }
    }

    /** Relit les versions installées (après une installation, au retour dans les réglages…). */
    fun refreshInstalled() {
        _state.update { current -> current.manifest?.let { current.withManifest(it) } ?: current }
    }

    private fun UpdateState.withManifest(manifest: UpdateManifest): UpdateState {
        val launcherVersion = installedVersion(UpdateTarget.LAUNCHER) ?: 0L
        val moduleVersion = installedVersion(UpdateTarget.MODULE)
        return copy(
            manifest = manifest,
            updates = UpdateManifests.available(manifest, launcherVersion, moduleVersion),
            moduleOffer = manifest.module?.takeIf { moduleVersion == null },
        )
    }

    /** Android autorise-t-il Épure à installer des apps ? (réglage « Sources inconnues ») */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    /** Réglage Android où l'on autorise Épure à installer des apps. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    /** Télécharge puis installe la version publiée de [target]. */
    fun install(target: UpdateTarget) {
        if (installJob?.isActive == true) return
        val manifest = _state.value.manifest ?: return
        val entry = when (target) {
            UpdateTarget.LAUNCHER -> manifest.launcher
            UpdateTarget.MODULE -> manifest.module
        } ?: return
        installJob = scope.launch {
            _state.update { it.copy(progress = InstallProgress(target, 0f), error = null, errorMessage = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    download(entry, target) { fraction ->
                        _state.update { it.copy(progress = InstallProgress(target, fraction)) }
                    }
                }
                _state.update { it.copy(progress = InstallProgress(target, null)) }
                withContext(Dispatchers.IO) { commit(file, target) }
                // La suite arrive dans InstallStatusReceiver.
            } catch (e: UpdateException) {
                _state.update { it.copy(progress = null, error = e.error) }
            } catch (e: IOException) {
                Log.i(TAG, "Téléchargement interrompu", e)
                _state.update { it.copy(progress = null, error = UpdateError.NETWORK) }
            } catch (e: Exception) {
                Log.w(TAG, "Installation impossible", e)
                _state.update { it.copy(progress = null, error = UpdateError.INSTALL_FAILED, errorMessage = e.message) }
            }
        }
    }

    /** L'activité visible a affiché l'écran de confirmation d'Android. */
    fun confirmationShown() {
        _state.update { it.copy(confirmation = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, errorMessage = null) }
    }

    internal fun onInstallStatus(status: Int, message: String?, confirmation: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                _state.update { it.copy(confirmation = confirmation) }
            PackageInstaller.STATUS_SUCCESS -> {
                _state.update { it.copy(progress = null, confirmation = null) }
                refreshInstalled()
                cleanDownloads()
            }
            else -> {
                // Annulé par l'utilisateur, bloqué par Play Protect, clé différente…
                val aborted = status == PackageInstaller.STATUS_FAILURE_ABORTED
                _state.update {
                    it.copy(
                        progress = null,
                        confirmation = null,
                        error = if (aborted) null else UpdateError.INSTALL_FAILED,
                        errorMessage = message,
                    )
                }
            }
        }
    }

    private fun fetchManifest(): UpdateManifest? {
        // Paramètre unique : évite une copie trop ancienne gardée en cache en chemin.
        val text = get("$MANIFEST_URL?t=${System.currentTimeMillis() / 60_000}") { it.readBytes().decodeToString() }
        return UpdateManifests.parse(text)
    }

    private fun <T> get(url: String, read: (java.io.InputStream) -> T): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Epure")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.use(read)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(entry: UpdateEntry, target: UpdateTarget, onProgress: (Float) -> Unit): File {
        val dir = downloadsDir().apply { listFiles()?.forEach { it.delete() } }
        val file = File(dir, "${target.fileName}-${entry.versionCode}.apk")
        val connection = (URL(entry.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Epure")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: entry.size
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!UpdateManifests.sha256(file).equals(entry.sha256, ignoreCase = true)) {
            file.delete()
            throw UpdateException(UpdateError.CORRUPTED)
        }
        return file
    }

    private fun commit(file: File, target: UpdateTarget) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(target.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Sans confirmation quand Android le permet (Épure a déjà installé cette app).
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(appContext, InstallStatusReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun downloadsDir(): File = File(appContext.cacheDir, "mises-a-jour").apply { mkdirs() }

    private fun cleanDownloads() {
        downloadsDir().listFiles()?.forEach { it.delete() }
    }

    private class UpdateException(val error: UpdateError) : Exception(error.name)

    companion object {
        private const val TAG = "Updates"
        private const val KEY_LAST_CHECK = "derniere_verification"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

        /** Fichier des dernières versions, publié dans le dépôt GitHub d'Épure. */
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/baptistesadox-dotcom/App-t-l-phone/ccr-9ef49c26-q77is6/updates/latest.json"
    }
}

/** Résultat d'une installation, envoyé par Android. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        context.appContainer.updates.onInstallStatus(
            status = status,
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirmation = confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
