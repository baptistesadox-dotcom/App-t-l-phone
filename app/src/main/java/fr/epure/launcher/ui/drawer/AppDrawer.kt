package fr.epure.launcher.ui.drawer

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import fr.epure.launcher.data.config.ScrollerSide
import fr.epure.launcher.ui.AppMenuOrigin
import fr.epure.launcher.ui.AppMenuRequest
import fr.epure.launcher.ui.DrawerList
import fr.epure.launcher.ui.DrawerRow
import fr.epure.launcher.ui.FolderEditorRequest
import fr.epure.launcher.ui.FolderEntry
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.LauncherUiController
import fr.epure.launcher.ui.common.AppRow
import fr.epure.launcher.ui.common.LauncherRow
import fr.epure.launcher.ui.theme.LocalLauncherLook

/**
 * Liste complète des apps : recherche, onglets de dossiers et apps classées par lettre.
 * L'alphabet latéral est dessiné par l'écran parent, au-dessus de cette liste.
 */
@Composable
fun AppDrawer(
    drawer: DrawerList,
    tabs: List<FolderEntry>,
    selectedTab: String?,
    query: String,
    listState: LazyListState,
    actions: LauncherActions,
    ui: LauncherUiController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLauncherLook.current
    val colors = MaterialTheme.colorScheme
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
    val scrollerPadding = if (drawer.isSearch) 0.dp else 40.dp
    val listPadding = PaddingValues(
        start = if (look.scrollerSide == ScrollerSide.START) scrollerPadding else 0.dp,
        end = if (look.scrollerSide == ScrollerSide.END) scrollerPadding else 0.dp,
        bottom = navBottom + 24.dp,
    )

    // Surface : couleur de texte cohérente et aucun appui ne traverse vers l'accueil caché dessous.
    Surface(
        color = colors.surface.copy(alpha = 0.9f),
        contentColor = colors.onSurface,
        modifier = modifier.fillMaxSize(),
    ) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        SearchBar(
            query = query,
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
                onSelect = actions::selectTab,
                onCreateFolder = { ui.folderEditor = FolderEditorRequest(folderId = null) },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(closeOnPull),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = listPadding,
                modifier = Modifier.fillMaxSize(),
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
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp * look.textScale,
                                color = colors.onSurface,
                            ),
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
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (drawer.isSearch) {
                    if (drawer.resultCount == 0) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.drawer_no_result),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                    item(key = "web") {
                        LauncherRow(
                            onClick = { actions.webSearch(query) },
                            onLongClick = {},
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.drawer_web_search, query),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primary,
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

@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawer_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryTabs(
    tabs: List<FolderEntry>,
    selectedTab: String?,
    onSelect: (String?) -> Unit,
    onCreateFolder: () -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedTab == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.drawer_tab_all)) },
                colors = chipColors,
            )
        }
        items(tabs, key = { it.id }) { tab ->
            FilterChip(
                selected = selectedTab == tab.id,
                onClick = { onSelect(if (selectedTab == tab.id) null else tab.id) },
                label = { Text("${tab.name}  ${tab.apps.size}") },
                colors = chipColors,
            )
        }
        item(key = "new") {
            FilterChip(
                selected = false,
                onClick = onCreateFolder,
                label = { Text(stringResource(R.string.drawer_tab_new)) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = chipColors,
            )
        }
    }
}
