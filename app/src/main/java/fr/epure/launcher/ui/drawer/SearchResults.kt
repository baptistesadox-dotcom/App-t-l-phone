package fr.epure.launcher.ui.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.R
import fr.epure.launcher.data.contacts.ContactResult
import fr.epure.launcher.domain.SystemShortcut
import fr.epure.launcher.ui.Calculation
import fr.epure.launcher.ui.LauncherActions
import fr.epure.launcher.ui.SearchExtras
import fr.epure.launcher.ui.common.LauncherRow
import fr.epure.launcher.ui.home.HomeMargin
import fr.epure.launcher.ui.home.appIconGap
import fr.epure.launcher.ui.home.appLabelStyle
import fr.epure.launcher.ui.home.appSecondaryStyle
import fr.epure.launcher.ui.icons.LineIcons
import fr.epure.launcher.ui.theme.LocalLauncherLook

/** Résultat d'un calcul, en tête de la recherche : toucher copie le résultat. */
fun LazyListScope.calculationResult(extras: SearchExtras, rowPadding: PaddingValues, actions: LauncherActions) {
    val calculation = extras.calculation ?: return
    item(key = "calcul") { CalculationRow(calculation, rowPadding) { actions.copyResult(calculation) } }
}

/** Contacts et réglages d'Android trouvés, sous les apps. */
fun LazyListScope.otherResults(extras: SearchExtras, rowPadding: PaddingValues, actions: LauncherActions) {
    if (extras.contacts.isNotEmpty() || extras.askContactsPermission) {
        item(key = "titre-contacts") { ResultsHeader(stringResource(R.string.search_contacts)) }
    }
    items(extras.contacts, key = { it.key }) { contact -> ContactRow(contact, rowPadding, actions) }
    if (extras.askContactsPermission) {
        item(key = "autoriser-contacts") {
            ResultRow(
                icon = "address-book",
                title = stringResource(R.string.search_contacts_permission),
                subtitle = stringResource(R.string.search_contacts_permission_sub),
                rowPadding = rowPadding,
                muted = true,
                onClick = actions::requestContactsPermission,
            )
        }
    }
    if (extras.shortcuts.isNotEmpty()) {
        item(key = "titre-reglages") { ResultsHeader(stringResource(R.string.search_settings)) }
    }
    items(extras.shortcuts, key = { "reglage:${it.id}" }) { shortcut ->
        ResultRow(icon = shortcut.icon, title = shortcut.title, subtitle = null, rowPadding = rowPadding) {
            actions.openShortcut(shortcut)
        }
    }
}

@Composable
private fun ResultsHeader(text: String) {
    val look = LocalLauncherLook.current
    Text(
        text = text.uppercase(),
        style = with(look) {
            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                .onWallpaper(muted = true)
        },
        modifier = Modifier.padding(start = HomeMargin + 2.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** Icône au trait d'un résultat, à la taille des icônes d'apps. */
@Composable
private fun ResultIcon(name: String, muted: Boolean = false) {
    val look = LocalLauncherLook.current
    if (!look.showIcons) return
    Box(Modifier.size(look.iconSize), contentAlignment = Alignment.Center) {
        LineIcons.vector(name, look.iconSize * 0.82f)?.let { vector ->
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = if (muted) look.onWallpaperMuted else look.onWallpaper,
                modifier = Modifier.size(look.iconSize * 0.82f),
            )
        }
    }
    Spacer(Modifier.width(appIconGap(look)))
}

@Composable
private fun CalculationRow(calculation: Calculation, rowPadding: PaddingValues, onCopy: () -> Unit) {
    val look = LocalLauncherLook.current
    LauncherRow(onClick = { onCopy() }, onLongClick = onCopy, contentPadding = rowPadding) {
        ResultIcon("calculator")
        Column(Modifier.weight(1f)) {
            Text(
                text = "= ${calculation.result}",
                style = with(look) {
                    MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp * textScale, fontWeight = FontWeight.Medium).onWallpaper()
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.search_calc_copy), style = appSecondaryStyle(look), maxLines = 1)
        }
    }
}

@Composable
private fun ContactRow(contact: ContactResult, rowPadding: PaddingValues, actions: LauncherActions) {
    val look = LocalLauncherLook.current
    LauncherRow(onClick = { actions.openContact(contact) }, onLongClick = { actions.openContact(contact) }, contentPadding = rowPadding) {
        ResultIcon("user")
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = appLabelStyle(look), maxLines = 1, overflow = TextOverflow.Ellipsis)
            contact.phone?.let { Text(it, style = appSecondaryStyle(look), maxLines = 1) }
        }
        if (contact.phone != null) {
            ActionIcon("phone", stringResource(R.string.search_call)) { actions.callContact(contact) }
            ActionIcon("message-circle", stringResource(R.string.search_message)) { actions.messageContact(contact) }
        }
    }
}

@Composable
private fun ActionIcon(name: String, description: String, onClick: () -> Unit) {
    val look = LocalLauncherLook.current
    IconButton(onClick = onClick) {
        LineIcons.vector(name, 22.dp)?.let { Icon(it, contentDescription = description, tint = look.onWallpaper, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun ResultRow(
    icon: String,
    title: String,
    subtitle: String?,
    rowPadding: PaddingValues,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val look = LocalLauncherLook.current
    LauncherRow(onClick = { onClick() }, onLongClick = onClick, contentPadding = rowPadding) {
        ResultIcon(icon, muted)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = with(look) { appLabelStyle(look).let { if (muted) it.copy(color = onWallpaperMuted) else it } },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, style = appSecondaryStyle(look), maxLines = 2) }
        }
    }
}
