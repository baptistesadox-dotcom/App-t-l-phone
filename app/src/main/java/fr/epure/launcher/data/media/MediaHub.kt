package fr.epure.launcher.data.media

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import fr.epure.shared.NotificationsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/** Ce qui est en cours de lecture (musique, podcast…), pour le lecteur de l'accueil. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val art: Bitmap?,
    val canSkip: Boolean,
)

/**
 * Lecteur de l'accueil. Le module « Épure Notifications » (qui a l'accès aux notifications)
 * envoie les sessions de lecture en cours ; avec elles, le launcher affiche le titre et
 * pilote la lecture directement, sans autre autorisation.
 */
class MediaHub(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = HashMap<MediaController, MediaController.Callback>()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NotificationsContract.ACTION_MEDIA) return
            val tokens = IntentCompat.getParcelableArrayListExtra(
                intent, NotificationsContract.EXTRA_MEDIA_TOKENS, MediaSession.Token::class.java,
            ).orEmpty()
            setSessions(tokens)
        }
    }

    init {
        // Seul le module (signé avec la même clé) peut envoyer ces sessions.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NotificationsContract.ACTION_MEDIA),
            NotificationsContract.PERMISSION,
            handler,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun setSessions(tokens: List<MediaSession.Token>) {
        callbacks.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        callbacks.clear()
        controllers = tokens.mapNotNull { token -> runCatching { MediaController(context, token) }.getOrNull() }
            .filter { it.packageName != context.packageName }
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onSessionDestroyed() = refresh()
            }
            runCatching { controller.registerCallback(callback, handler) }.onSuccess { callbacks[controller] = callback }
        }
        refresh()
    }

    /** La session à afficher : celle qui joue, sinon la plus récente qui a un titre. */
    private fun current(): MediaController? {
        val withTitle = controllers.filter { !it.metadata?.title().isNullOrBlank() }
        return withTitle.firstOrNull { it.playbackState?.isActive() == true } ?: withTitle.firstOrNull()
    }

    private fun refresh() {
        val controller = current()
        if (controller == null) {
            _nowPlaying.value = null
            return
        }
        val metadata = controller.metadata
        val state = controller.playbackState
        val previous = _nowPlaying.value
        val title = metadata?.title().orEmpty()
        val sameTrack = previous != null && previous.packageName == controller.packageName && previous.title == title
        _nowPlaying.value = NowPlaying(
            packageName = controller.packageName,
            title = title,
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            isPlaying = state?.isActive() == true,
            // La pochette ne change qu'avec le morceau : on évite de la réduire à chaque seconde.
            art = if (sameTrack && previous?.art != null) previous.art else metadata?.let(::smallArt),
            canSkip = state == null || (state.actions and (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)) != 0L,
        )
    }

    fun playPause() {
        val controller = current() ?: return
        runCatching {
            if (controller.playbackState?.isActive() == true) controller.transportControls.pause() else controller.transportControls.play()
        }
    }

    fun next() {
        current()?.let { runCatching { it.transportControls.skipToNext() } }
    }

    fun previous() {
        current()?.let { runCatching { it.transportControls.skipToPrevious() } }
    }

    /** Ouvre le lecteur de l'app (ou l'app elle-même). Renvoie false si rien ne s'est ouvert. */
    fun open(activity: Context): Boolean {
        val controller = current() ?: return false
        controller.sessionActivity?.let { pending ->
            val sent = runCatching {
                val options = ActivityOptions.makeBasic().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        @Suppress("DEPRECATION")
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                }.toBundle()
                pending.send(activity, 0, null, null, null, null, options)
            }
            if (sent.isSuccess) return true
            if (sent.exceptionOrNull() !is PendingIntent.CanceledException) Log.w(TAG, "Lecteur injoignable", sent.exceptionOrNull())
        }
        val launch = activity.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return false
        return runCatching { activity.startActivity(launch) }.isSuccess
    }

    private fun MediaMetadata.title(): String? =
        getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)

    private fun PlaybackState.isActive(): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING

    private fun smallArt(metadata: MediaMetadata): Bitmap? {
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        val size = max(art.width, art.height)
        if (size <= ART_SIZE) return art
        val scale = ART_SIZE.toFloat() / size
        return runCatching {
            Bitmap.createScaledBitmap(art, (art.width * scale).toInt().coerceAtLeast(1), (art.height * scale).toInt().coerceAtLeast(1), true)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "MediaHub"
        const val ART_SIZE = 160
    }
}
