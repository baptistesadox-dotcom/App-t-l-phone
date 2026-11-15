package fr.epure.launcher

import android.app.Application
import android.content.Context
import fr.epure.launcher.data.apps.AppsRepository
import fr.epure.launcher.data.calendar.CalendarRepository
import fr.epure.launcher.data.config.ConfigRepository
import fr.epure.launcher.data.icons.IconLoader
import fr.epure.launcher.data.icons.IconPackManager
import fr.epure.launcher.data.notifications.NotificationHub
import fr.epure.launcher.data.wallpaper.HomeImageStore
import fr.epure.launcher.data.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Objets partagés par tous les écrans (une seule instance pour toute l'app). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Tâches de fond qui vivent aussi longtemps que l'app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val configRepository = ConfigRepository(appContext, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    val appsRepository = AppsRepository(appContext, appScope)
    val iconPacks = IconPackManager(appContext)
    val iconLoader = IconLoader(appContext, iconPacks, appsRepository)
    val weatherRepository = WeatherRepository(appContext)
    val calendarRepository = CalendarRepository(appContext)
    val notificationHub = NotificationHub(appContext)
    val homeImages = HomeImageStore(appContext)

    init {
        // Une app installée ou mise à jour peut changer d'icône : on vide le cache.
        appScope.launch { appsRepository.apps.filterNotNull().drop(1).collect { iconLoader.clear() } }
    }
}

class LauncherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LauncherApplication).container
