package fr.epure.launcher.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Un événement d'agenda à afficher sur l'accueil. */
data class AgendaEvent(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    /** Couleur ARGB du calendrier ou de l'événement, si connue. */
    val color: Int?,
)

/** Lit les prochains événements des agendas du téléphone (permission « Agenda » requise). */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Événements qui ne sont pas encore terminés, sur [days] jours à partir d'aujourd'hui,
     * triés par heure de début. Les événements refusés sont ignorés.
     */
    suspend fun upcoming(days: Int, max: Int = 4, nowMillis: Long = System.currentTimeMillis()): List<AgendaEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val start = nowMillis - DAY_MILLIS // les événements « journée entière » commencent à minuit UTC
            val end = nowMillis + days.coerceIn(1, 14) * DAY_MILLIS
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, start)
                ContentUris.appendId(it, end)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            )
            val selection = "${CalendarContract.Instances.VISIBLE} = 1"
            val events = mutableListOf<AgendaEvent>()
            try {
                context.contentResolver.query(
                    uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val status = c.getInt(6)
                        if (status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        val event = AgendaEvent(
                            eventId = c.getLong(0),
                            title = c.getString(1)?.trim().orEmpty(),
                            beginMillis = c.getLong(2),
                            endMillis = c.getLong(3),
                            allDay = c.getInt(4) != 0,
                            color = if (c.isNull(5)) null else c.getInt(5),
                        )
                        if (AgendaFormat.isStillRelevant(event, nowMillis, end)) events += event
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Accès à l'agenda refusé", e)
            } catch (e: Exception) {
                Log.w(TAG, "Lecture de l'agenda impossible", e)
            }
            AgendaFormat.sorted(events).take(max)
        }

    /** Émet un signal à chaque modification des agendas (ajout, suppression d'événement…). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    private companion object {
        const val TAG = "Calendar"
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
