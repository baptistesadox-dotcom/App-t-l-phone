package fr.epure.launcher.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.epure.launcher.R
import fr.epure.launcher.data.config.Appearance
import fr.epure.launcher.data.config.FontChoice
import fr.epure.launcher.data.config.IconStyle
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.ThemeMode
import fr.epure.launcher.data.config.ThemePresets
import fr.epure.launcher.data.config.updateWidgets
import fr.epure.launcher.ui.AppMenuOrigin
import fr.epure.launcher.ui.AppMenuRequest
import fr.epure.launcher.ui.LauncherScreen
import fr.epure.launcher.ui.LauncherState
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.NoOpActions
import fr.epure.launcher.ui.SampleData
import fr.epure.launcher.ui.buildDrawerList
import fr.epure.launcher.ui.buildLauncherState
import fr.epure.launcher.ui.theme.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Captures d'écran du launcher, générées sans téléphone (Robolectric).
 * Pour les régénérer : ./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
 * Elles sont enregistrées dans docs/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val rivage = ThemePresets.RIVAGE.appearance
    private val epure = ThemePresets.EPURE.appearance.copy(themeMode = ThemeMode.DARK, dynamicColor = false)

    /** Fond d'écran : celui du thème Rivage, ou un faux fond sombre abstrait. */
    @Composable
    private fun Wallpaper(shore: Boolean, content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D)),
        ) {
            if (shore) {
                Image(
                    painter = painterResource(R.drawable.rivage_wallpaper),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF5E5C61), Color(0xFF2A292D), Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.12f),
                            radius = size.width * 0.75f,
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width * 0.95f, size.height * 0.12f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF4A494E), Color(0xFF1C1B1F), Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.55f),
                            radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f,
                        center = Offset(size.width * 0.1f, size.height * 0.55f),
                    )
                }
            }
            content()
        }
    }

    private fun state(config: LauncherConfig): LauncherState = buildLauncherState(config, SampleData.apps, Locale.FRANCE)

    private fun render(
        ui: LauncherUiController = LauncherUiController(),
        appearance: Appearance = rivage,
        config: LauncherConfig = SampleData.rivageConfig,
        query: String = "",
        tab: String? = null,
        scrollerItem: String? = null,
        withNotifications: Boolean = false,
    ) {
        val state = state(config.copy(appearance = appearance))
        val source = tab?.let { id -> state.folderById(id)?.apps } ?: state.apps
        compose.setContent {
            LauncherTheme(appearance = appearance) {
                Wallpaper(shore = appearance.iconStyle == IconStyle.LINE) {
                    LauncherScreen(
                        state = state,
                        drawer = buildDrawerList(source, query),
                        query = query,
                        selectedTab = tab,
                        weather = SampleData.weather,
                        agenda = SampleData.agenda,
                        notifications = if (withNotifications) SampleData.notifications else emptyMap(),
                        now = SampleData.now,
                        use24Hour = true,
                        actions = NoOpActions(),
                        ui = ui,
                        hostedWidget = {},
                        onConfigureWeather = {},
                        previewScrollerItem = scrollerItem,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    // ----- Thème Rivage (par défaut) -----

    @Test
    fun rivageAccueil() {
        render()
        compose.onRoot().captureRoboImage("../docs/screenshots/1-rivage-accueil.png")
    }

    @Test
    fun rivageListe() {
        val ui = LauncherUiController(drawerInitiallyOpen = true).apply {
            scrollerLetter = "M"
            scrollerY = 1000f
        }
        render(ui = ui, scrollerItem = "M")
        compose.onRoot().captureRoboImage("../docs/screenshots/2-rivage-liste.png")
    }

    @Test
    fun rivageOnglet() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), tab = "travail")
        compose.onRoot().captureRoboImage("../docs/screenshots/3-rivage-onglet.png")
    }

    // ----- Thème Épure -----

    @Test
    fun epureAccueil() {
        render(
            appearance = epure,
            config = SampleData.config.updateWidgets { it.copy(showTodo = false, showAgenda = false) },
            withNotifications = true,
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/4-epure-accueil.png")
    }

    @Test
    fun epureNotificationsDepliees() {
        val ui = LauncherUiController().apply { expandedNotifications = SampleData.key("WhatsApp") }
        render(
            ui = ui,
            appearance = epure,
            config = SampleData.config.updateWidgets { it.copy(showTodo = false, showAgenda = false) },
            withNotifications = true,
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/5-epure-notifications.png")
    }

    @Test
    fun epureWidgetsEtDossier() {
        render(
            ui = LauncherUiController().apply { expandedFolderId = "social" },
            appearance = epure,
            config = SampleData.config,
            withNotifications = true,
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/6-epure-widgets-dossier.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun menuDUneApp() {
        val ui = LauncherUiController()
        val instagram = state(SampleData.rivageConfig).apps.first { it.label == "Instagram" }
        ui.appMenu = AppMenuRequest(instagram, AppMenuOrigin.Home)
        render(ui = ui)
        captureScreenRoboImage("../docs/screenshots/7-menu-app.png")
    }

    @Test
    fun personnalisation() {
        render(
            appearance = epure.copy(
                themeMode = ThemeMode.LIGHT,
                accentColor = 0xFFF2C14E,
                font = FontChoice.PLAYFAIR,
                showIcons = false,
                textScale = 1.15f,
                wallpaperDim = 0.25f,
            ),
            config = SampleData.config.updateWidgets { it.copy(showTodo = false, showAgenda = false) },
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/8-personnalisation.png")
    }
}
