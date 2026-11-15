package fr.epure.launcher.screenshots

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.epure.launcher.R
import fr.epure.launcher.data.config.Appearance
import fr.epure.launcher.data.config.FontChoice
import fr.epure.launcher.data.config.HomeStyle
import fr.epure.launcher.data.config.HomeTextColor
import fr.epure.launcher.data.config.LauncherConfig
import fr.epure.launcher.data.config.ThemeMode
import fr.epure.launcher.data.config.SearchConfig
import fr.epure.launcher.data.config.updateWidgets
import fr.epure.launcher.data.contacts.ContactResult
import fr.epure.launcher.data.media.NowPlaying
import fr.epure.launcher.domain.SystemShortcuts
import fr.epure.launcher.ui.SearchExtras
import fr.epure.launcher.ui.home.NowPlayingCard
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
import fr.epure.launcher.ui.wallpaper.ParallaxWallpaper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    /** Apparence par défaut (Épure, icônes au trait), en sombre : pas de Material You sans téléphone. */
    private val epure = Appearance().copy(themeMode = ThemeMode.DARK, dynamicColor = false)

    /** Option « accueil typographique » avec les réglages qui vont avec (police, blanc, texte clair). */
    private val typographic = epure.copy(
        homeStyle = HomeStyle.TYPOGRAPHIC,
        font = FontChoice.MONTSERRAT,
        accentColor = 0xFFFFFFFF,
        homeTextColor = HomeTextColor.LIGHT,
        iconSizeDp = 28,
        wallpaperDim = 0.2f,
        showScrollerOnHome = false,
    )

    /** Fond d'écran : le fond « Rivage » fourni avec l'app, ou un faux fond sombre abstrait. */
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
        appearance: Appearance = epure,
        config: LauncherConfig = SampleData.config.updateWidgets { it.copy(showTodo = false, showAgenda = false) },
        shore: Boolean = false,
        query: String = "",
        tab: String? = null,
        scrollerItem: String? = null,
        withNotifications: Boolean = false,
        searchExtras: SearchExtras = SearchExtras(),
        homeExtras: @Composable (Modifier) -> Unit = {},
    ) {
        val state = state(config.copy(appearance = appearance))
        val source = tab?.let { id -> state.folderById(id)?.apps } ?: state.apps
        compose.setContent {
            LauncherTheme(appearance = appearance) {
                Wallpaper(shore = shore) {
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
                        searchExtras = searchExtras,
                        homeExtras = homeExtras,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    // ----- Apparence par défaut : Épure avec les icônes au trait -----

    @Test
    fun accueil() {
        render(withNotifications = true)
        compose.onRoot().captureRoboImage("../docs/screenshots/1-accueil.png")
    }

    @Test
    fun notificationsDepliees() {
        val ui = LauncherUiController().apply { expandedNotifications = SampleData.key("WhatsApp") }
        render(ui = ui, withNotifications = true)
        compose.onRoot().captureRoboImage("../docs/screenshots/2-notifications.png")
    }

    @Test
    fun widgetsEtDossier() {
        render(
            ui = LauncherUiController().apply { expandedFolderId = "social" },
            config = SampleData.config,
            withNotifications = true,
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/3-widgets-dossier.png")
    }

    @Test
    fun listeEtAlphabet() {
        val ui = LauncherUiController(drawerInitiallyOpen = true).apply {
            scrollerLetter = "M"
            scrollerY = 1000f
        }
        render(ui = ui, scrollerItem = "M")
        compose.onRoot().captureRoboImage("../docs/screenshots/4-liste-alphabet.png")
    }

    @Test
    fun ongletDeDossier() {
        render(ui = LauncherUiController(drawerInitiallyOpen = true), tab = "travail")
        compose.onRoot().captureRoboImage("../docs/screenshots/5-onglet-dossier.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun menuDUneApp() {
        val ui = LauncherUiController()
        val instagram = state(SampleData.config).apps.first { it.label == "Instagram" }
        ui.appMenu = AppMenuRequest(instagram, AppMenuOrigin.Home)
        render(ui = ui)
        captureScreenRoboImage("../docs/screenshots/6-menu-app.png")
    }

    // ----- Options, une par une -----

    /** Accueil typographique, police Montserrat, accent blanc, fond d'écran Rivage. */
    @Test
    fun optionTypographique() {
        render(appearance = typographic, config = SampleData.rivageConfig, shore = true)
        compose.onRoot().captureRoboImage("../docs/screenshots/7-option-typographique.png")
    }

    /**
     * Effet parallaxe : l'image de l'accueil (fond Rivage) décalée comme quand on incline le
     * téléphone, puis une série d'images pour l'animation (app/build/parallaxe/, assemblée
     * par tools/make_parallax_gif.py). Avec -Pepure.demo.image=… et -Pepure.demo.out=…,
     * l'aperçu se fait avec n'importe quelle image.
     */
    @Test
    fun parallaxe() {
        val demoImage = System.getProperty("epure.demo.image")
        val outDir = System.getProperty("epure.demo.out") ?: "build/parallaxe"
        val shift = mutableStateOf(Offset(-1f, -0.6f))
        val appearance = epure.copy(homeTextColor = HomeTextColor.LIGHT)
        val config = SampleData.config.updateWidgets { it.copy(showTodo = false, showAgenda = false) }
        val state = state(config.copy(appearance = appearance))
        compose.setContent {
            val resource = ImageBitmap.imageResource(R.drawable.rivage_wallpaper)
            val image = remember(demoImage) { demoImage?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } ?: resource }
            LauncherTheme(appearance = appearance) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    LauncherScreen(
                        state = state,
                        drawer = buildDrawerList(state.apps, ""),
                        query = "",
                        selectedTab = null,
                        weather = SampleData.weather,
                        agenda = SampleData.agenda,
                        notifications = SampleData.notifications,
                        now = SampleData.now,
                        use24Hour = true,
                        actions = NoOpActions(),
                        ui = LauncherUiController(),
                        hostedWidget = {},
                        onConfigureWeather = {},
                        wallpaper = {
                            ParallaxWallpaper(image = image, strength = appearance.parallaxStrength, previewShift = shift.value)
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        if (demoImage == null) compose.onRoot().captureRoboImage("../docs/screenshots/9-parallaxe.png")
        // Le téléphone décrit une ellipse : un tour en 24 images.
        repeat(24) { i ->
            val angle = 2 * PI * i / 24
            shift.value = Offset(cos(angle).toFloat(), 0.6f * sin(angle).toFloat())
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("$outDir/frame-${i.toString().padStart(2, '0')}.png")
        }
    }

    // ----- Recherche universelle et lecteur de musique -----

    @Test
    fun rechercheUniverselle() {
        val extras = SearchExtras(
            contacts = listOf(ContactResult(1, "", "Bastien Morel", "06 12 34 56 78")),
            shortcuts = SystemShortcuts.search("ba"),
        )
        render(ui = LauncherUiController(drawerInitiallyOpen = true), query = "ba", searchExtras = extras)
        compose.onRoot().captureRoboImage("../docs/screenshots/10-recherche.png")
    }

    @Test
    fun calcul() {
        render(
            ui = LauncherUiController(drawerInitiallyOpen = true),
            query = "12×7",
            searchExtras = SearchExtras.instant("12×7", SearchConfig()),
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/11-calcul.png")
    }

    @Test
    fun lecteurDeMusique() {
        val playing = NowPlaying("com.spotify.music", "Clair de lune", "Claude Debussy", isPlaying = true, art = null, canSkip = true)
        render(
            withNotifications = true,
            homeExtras = { padding -> NowPlayingCard(playing, {}, {}, {}, {}, padding.padding(bottom = 14.dp)) },
        )
        compose.onRoot().captureRoboImage("../docs/screenshots/12-musique.png")
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
