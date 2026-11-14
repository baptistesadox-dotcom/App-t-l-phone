package fr.epure.launcher.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.epure.launcher.data.config.Appearance
import fr.epure.launcher.data.config.FontChoice
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.ThemeMode
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

    private val darkAppearance = Appearance(themeMode = ThemeMode.DARK, dynamicColor = false)

    /** Faux fond d'écran sombre et abstrait (formes grises douces sur noir). */
    @Composable
    private fun Wallpaper(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D)),
        ) {
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
            content()
        }
    }

    private fun state(config: LauncherConfig): LauncherState = buildLauncherState(config, SampleData.apps, Locale.FRANCE)

    private fun render(
        ui: LauncherUiController = LauncherUiController(),
        appearance: Appearance = darkAppearance,
        config: LauncherConfig = SampleData.config.updateWidgets { it.copy(showTodo = false, showAgenda = false) },
        query: String = "",
        tab: String? = null,
        scrollerItem: String? = null,
    ) {
        val state = state(config.copy(appearance = appearance))
        val source = tab?.let { id -> state.folderById(id)?.apps } ?: state.apps
        compose.setContent {
            LauncherTheme(appearance = appearance) {
                Wallpaper {
                    LauncherScreen(
                        state = state,
                        drawer = buildDrawerList(source, query),
                        query = query,
                        selectedTab = tab,
                        weather = SampleData.weather,
                        agenda = SampleData.agenda,
                        notifications = SampleData.notifications,
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

    @Test
    fun accueil() {
        render()
        compose.onRoot().captureRoboImage("../docs/screenshots/1-accueil.png")
    }

    @Test
    fun notificationsDepliees() {
        val ui = LauncherUiController().apply { expandedNotifications = SampleData.key("WhatsApp") }
        render(ui = ui)
        compose.onRoot().captureRoboImage("../docs/screenshots/2-notifications.png")
    }

    @Test
    fun accueilAvecWidgetsEtDossier() {
        render(
            ui = LauncherUiController().apply { expandedFolderId = "social" },
            config = SampleData.config,
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/3-widgets-dossier.png")
    }

    @Test
    fun listeDesAppsAvecAlphabet() {
        // Doigt posé sur « M » aux deux tiers de l'écran : les apps en M viennent à sa hauteur.
        val ui = LauncherUiController(drawerInitiallyOpen = true).apply {
            scrollerLetter = "M"
            scrollerY = 1060f
        }
        render(ui = ui, scrollerItem = "M")
        compose.onRoot().captureRoboImage("../docs/screenshots/4-liste-alphabet.png")
    }

    @Test
    fun ongletDossier() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), tab = "travail")
        compose.onRoot().captureRoboImage("../docs/screenshots/5-onglet-dossier.png")
    }

    @Test
    fun recherche() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), query = "me")
        compose.onRoot().captureRoboImage("../docs/screenshots/6-recherche.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun menuDUneApp() {
        val ui = LauncherUiController()
        val discord = state(SampleData.config).apps.first { it.label == "Discord" }
        ui.appMenu = AppMenuRequest(discord, AppMenuOrigin.Home)
        render(ui = ui)
        captureScreenRoboImage("../docs/screenshots/7-menu-app.png")
    }

    @Test
    fun personnalisation() {
        render(
            appearance = Appearance(
                themeMode = ThemeMode.LIGHT,
                dynamicColor = false,
                accentColor = 0xFFF2C14E,
                font = FontChoice.PLAYFAIR,
                showIcons = false,
                textScale = 1.15f,
                wallpaperDim = 0.25f,
            ),
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/8-personnalisation.png")
    }
}
