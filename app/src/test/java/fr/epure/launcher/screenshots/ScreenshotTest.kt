package fr.epure.launcher.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.epure.launcher.data.config.Appearance
import fr.epure.launcher.data.config.FontChoice
import fr.epure.launcher.data.config.ThemeMode
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

    private val darkAppearance = Appearance(themeMode = ThemeMode.DARK, dynamicColor = false, wallpaperDim = 0.1f)

    /** Faux fond d'écran (dégradé façon coucher de soleil). */
    @Composable
    private fun Wallpaper(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2541), Color(0xFF3A506B), Color(0xFF8E6C88), Color(0xFFD99A6C)))),
        ) { content() }
    }

    private fun state(): LauncherState = buildLauncherState(SampleData.config, SampleData.apps, Locale.FRANCE)

    private fun render(
        ui: LauncherUiController = LauncherUiController(),
        appearance: Appearance = darkAppearance,
        query: String = "",
        tab: String? = null,
        scrollerLetter: String? = null,
    ) {
        val state = state().let { it.copy(config = it.config.copy(appearance = appearance)) }
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
                        now = SampleData.now,
                        use24Hour = true,
                        actions = NoOpActions(),
                        ui = ui,
                        hostedWidget = {},
                        onConfigureWeather = {},
                        previewScrollerLetter = scrollerLetter,
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
    fun accueilDossierOuvert() {
        render(ui = LauncherUiController().apply { expandedFolderId = "social" })
        compose.onRoot().captureRoboImage("../docs/screenshots/2-dossier.png")
    }

    @Test
    fun listeDesAppsAvecAlphabet() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), scrollerLetter = "M")
        compose.onRoot().captureRoboImage("../docs/screenshots/3-liste-alphabet.png")
    }

    @Test
    fun ongletDossier() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), tab = "travail")
        compose.onRoot().captureRoboImage("../docs/screenshots/4-onglet-dossier.png")
    }

    @Test
    fun recherche() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), query = "me")
        compose.onRoot().captureRoboImage("../docs/screenshots/5-recherche.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun menuDUneApp() {
        val ui = LauncherUiController()
        val spotify = state().apps.first { it.label == "Spotify" }
        ui.appMenu = AppMenuRequest(spotify, AppMenuOrigin.Home)
        render(ui = ui)
        captureScreenRoboImage("../docs/screenshots/6-menu-app.png")
    }

    @Test
    fun themeClairEtPolice() {
        render(
            appearance = Appearance(
                themeMode = ThemeMode.LIGHT,
                dynamicColor = false,
                accentColor = 0xFFF28BA8,
                font = FontChoice.SERIF,
                showIcons = false,
                textScale = 1.1f,
                wallpaperDim = 0.45f,
            ),
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/7-personnalisation.png")
    }
}
