package fr.epure.launcher.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.R
import fr.epure.shared.AppUserKey
import fr.epure.shared.NotificationPreview
import fr.epure.launcher.ui.AppMenuOrigin
import fr.epure.launcher.ui.AppMenuRequest
import fr.epure.launcher.ui.DrawerList
import fr.epure.launcher.ui.DrawerRow
import fr.epure.launcher.ui.FolderEditorRequest
import fr.epure.launcher.ui.FolderEntry
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.common.AppRow
import fr.epure.launcher.ui.common.BoundsHolder
import fr.epure.launcher.ui.common.LauncherRow
import fr.epure.launcher.ui.home.HomeMargin
import fr.epure.launcher.ui.home.appLabelStyle
import fr.epure.launcher.ui.home.appRowPadding
import fr.epure.launcher.ui.home.forApp
import fr.epure.launcher.ui.theme.LocalLauncherLook

/**
 * Liste complète des apps, posée directement sur le fond d'écran assombri :
 * recherche, onglets de dossiers et apps classées par lettre.
 * L'alphabet latéral est dessiné par l'écran parent, au-dessus de cette liste.
 */
@Composable
fun AppDrawer(
    drawer: DrawerList,
    tabs: List<FolderEntry>,
    selectedTab: String?,
    query: String,
    listState: LazyListState,
    notifications: Map<AppUserKey, List<NotificationPreview>>,
    showDots: Boolean,
    actions: LauncherActions,
    ui: LauncherUiController,
    onClose: () -> Unit,
    listPosition: BoundsHolder,
    modifier: Modifier = Modifier,
) {
    val look = LocalLauncherLook.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val closeThresholdPx = with(density) { 96.dp.toPx() }

    // Glisser vers le bas quand la liste est déjà en haut referme le tiroir.
    val closeOnPull = remember(onClose) {
        object : NestedScrollConnection {
            private var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && consumed.y == 0f) {
                    pulled += available.y
                    if (pulled > closeThresholdPx) {
                        pulled = 0f
                        onClose()
                    }
                } else {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val rowPadding = appRowPadding(look, scrollerVisible = !drawer.isSearch)
    val labelStyle = appLabelStyle(look)
    val glass = look.onWallpaper.copy(alpha = 0.13f)

    // Surface transparente : texte de la bonne couleur et aucun appui ne traverse vers l'accueil.
    Surface(color = Color.Transparent, contentColor = look.onWallpaper, modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            SearchBar(
                query = query,
                glass = glass,
                onQueryChange = actions::setQuery,
                onSubmit = {
                    val first = drawer.rows.firstNotNullOfOrNull { (it as? DrawerRow.Item)?.entry }
                    if (first != null) {
                        keyboard?.hide()
                        actions.launch(first, null)
                    } else if (query.isNotBlank()) {
                        actions.webSearch(query)
                    }
                },
                onOpenSettings = actions::openSettings,
            )

            if (query.isEmpty() && (tabs.isNotEmpty() || selectedTab != null)) {
                CategoryTabs(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    glass = glass,
                    onSelect = actions::selectTab,
                    onCreateFolder = { ui.folderEditor = FolderEditorRequest(folderId = null) },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(closeOnPull)
                    .fadingTopEdge(with(density) { 28.dp.toPx() }),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = navBottom + 32.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .onPlaced { listPosition.coordinates = it },
                ) {
                    items(
                        items = drawer.rows,
                        key = { it.key },
                        contentType = { if (it is DrawerRow.Header) 0 else 1 },
                    ) { row ->
                        when (row) {
                            is DrawerRow.Header -> SectionHeader(row.letter)
                            is DrawerRow.Item -> AppRow(
                                entry = row.entry,
                                textStyle = labelStyle,
                                showIcon = look.showIcons,
                                iconSize = look.iconSize,
                                onClick = { bounds ->
                                    focusManager.clearFocus()
                                    actions.launch(row.entry, bounds)
                                },
                                onLongClick = {
                                    val origin = selectedTab?.let { AppMenuOrigin.Folder(it) } ?: AppMenuOrigin.Drawer
                                    ui.appMenu = AppMenuRequest(row.entry, origin)
                                },
                                contentPadding = rowPadding,
                                showDot = showDots && notifications.forApp(row.entry).isNotEmpty(),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (drawer.isSearch) {
                        if (drawer.resultCount == 0) {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.drawer_no_result),
                                    style = with(look) { MaterialTheme.typography.bodyLarge.onWallpaper(muted = true) },
                                    modifier = Modifier.padding(horizontal = HomeMargin, vertical = 16.dp),
                                )
                            }
                        }
                        item(key = "web") {
                            LauncherRow(
                                onClick = { actions.webSearch(query) },
                                onLongClick = {},
                                contentPadding = rowPadding,
                            ) {
                                Box(Modifier.size(look.iconSize), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        tint = look.onWallpaperMuted,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(Modifier.width(20.dp))
                                Text(
                                    text = stringResource(R.string.drawer_web_search, query),
                                    style = with(look) { MaterialTheme.typography.titleMedium.onWallpaper(muted = true) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Les lignes qui passent sous la barre de recherche s'estompent au lieu d'être coupées net. */
private fun Modifier.fadingTopEdge(heightPx: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, heightPx / size.height to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun SectionHeader(letter: String) {
    val look = LocalLauncherLook.current
    Text(
        text = letter,
        style = with(look) {
            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp).onWallpaper(muted = true)
        },
        modifier = Modifier.padding(start = HomeMargin + 2.dp, top = 18.dp, bottom = 2.dp),
    )
}

@Composable
private fun SearchBar(
    query: String,
    glass: Color,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val look = LocalLauncherLook.current
    val text = with(look) { MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp).onWallpaper() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HomeMargin - 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(glass)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = look.onWallpaperMuted)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.drawer_search_hint), style = text.copy(color = look.onWallpaperMuted))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = text,
                    cursorBrush = SolidColor(look.onWallpaper),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = look.onWallpaperMuted,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onQueryChange("") }
                        .padding(6.dp),
                )
            }
        }
        Icon(
            Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.action_settings),
            tint = look.onWallpaperMuted,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onOpenSettings)
                .padding(12.dp),
        )
    }
}

/** Onglet en forme de pilule translucide. */
@Composable
private fun Pill(label: String, selected: Boolean, glass: Color, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val look = LocalLauncherLook.current
    val background = if (selected) look.onWallpaper else glass
    val content = if (selected) {
        if (look.onWallpaper.red > 0.5f) Color(0xFF15161A) else Color.White
    } else {
        look.onWallpaper
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = content,
        )
    }
}

@Composable
private fun CategoryTabs(
    tabs: List<FolderEntry>,
    selectedTab: String?,
    glass: Color,
    onSelect: (String?) -> Unit,
    onCreateFolder: () -> Unit,
) {
    val look = LocalLauncherLook.current
    LazyRow(
        contentPadding = PaddingValues(start = HomeMargin - 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        item(key = "all") {
            Pill(stringResource(R.string.drawer_tab_all), selectedTab == null, glass, onClick = { onSelect(null) })
        }
        items(tabs, key = { it.id }) { tab ->
            Pill(
                label = "${tab.name}  ${tab.apps.size}",
                selected = selectedTab == tab.id,
                glass = glass,
                onClick = { onSelect(if (selectedTab == tab.id) null else tab.id) },
            )
        }
        item(key = "new") {
            Pill(
                label = stringResource(R.string.drawer_tab_new),
                selected = false,
                glass = glass,
                onClick = onCreateFolder,
                leading = { Icon(Icons.Outlined.Add, contentDescription = null, tint = look.onWallpaper, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}
