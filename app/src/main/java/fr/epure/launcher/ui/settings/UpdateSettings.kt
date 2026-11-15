package fr.epure.launcher.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.epure.launcher.R
import fr.epure.launcher.data.update.AvailableUpdate
import fr.epure.launcher.data.update.UpdateError
import fr.epure.launcher.data.update.UpdateRepository
import fr.epure.launcher.data.update.UpdateState
import fr.epure.launcher.data.update.UpdateTarget
import java.util.Date
import kotlin.math.roundToInt

/** Nom affiché de l'app concernée. */
@Composable
private fun UpdateTarget.label(): String = stringResource(
    when (this) {
        UpdateTarget.LAUNCHER -> R.string.app_name
        UpdateTarget.MODULE -> R.string.update_module_name
    },
)

/** Lance l'installation, ou d'abord le réglage Android qui l'autorise. */
private fun startInstall(context: Context, updates: UpdateRepository, target: UpdateTarget) {
    if (!updates.canInstall()) {
        try {
            context.startActivity(updates.installPermissionIntent())
        } catch (_: ActivityNotFoundException) {
            updates.install(target)
        }
        return
    }
    updates.install(target)
}

/** Section « Mises à jour » de la page À propos. */
@Composable
fun UpdatesSection(updates: UpdateRepository) {
    val state by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Au retour (réglage « Sources inconnues », installation terminée…), on relit les versions.
    LifecycleResumeEffect(Unit) {
        updates.refreshInstalled()
        onPauseOrDispose { }
    }

    SettingsSection(stringResource(R.string.section_updates))
    state.progress?.let { progress ->
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            val fraction = progress.fraction
            Text(
                text = if (fraction != null) {
                    stringResource(R.string.update_downloading, progress.target.label(), (fraction * 100).roundToInt())
                } else {
                    stringResource(R.string.update_installing, progress.target.label())
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
    state.updates.forEach { update ->
        UpdateCard(update, busy = state.progress != null) { startInstall(context, updates, update.target) }
    }
    if (state.updates.isEmpty() && state.manifest != null && state.progress == null) {
        SettingsNote(stringResource(R.string.update_up_to_date))
    }
    state.error?.let { error -> UpdateErrorNote(error, state.errorMessage) }
    if (state.updates.isNotEmpty() && !updates.canInstall()) {
        SettingsNote(stringResource(R.string.update_permission_note))
    }
    ButtonRow {
        OutlinedButton(onClick = updates::check, enabled = !state.checking && state.progress == null) {
            Text(stringResource(if (state.checking) R.string.update_checking else R.string.update_check))
        }
    }
    if (state.lastCheckMillis > 0) {
        val time = DateFormat.getTimeFormat(context).format(Date(state.lastCheckMillis))
        val day = DateFormat.getMediumDateFormat(context).format(Date(state.lastCheckMillis))
        SettingsNote(stringResource(R.string.update_last_check, day, time))
    }
    SettingsNote(stringResource(R.string.update_how))
}

@Composable
private fun UpdateCard(update: AvailableUpdate, busy: Boolean, onInstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.update_available, update.target.label(), update.entry.versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (update.entry.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    update.entry.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (update.target == UpdateTarget.MODULE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.update_module_play_protect),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onInstall, enabled = !busy) { Text(stringResource(R.string.update_install)) }
        }
    }
}

@Composable
private fun UpdateErrorNote(error: UpdateError, message: String?) {
    val text = when (error) {
        UpdateError.NETWORK -> stringResource(R.string.update_error_network)
        UpdateError.INVALID -> stringResource(R.string.update_error_invalid)
        UpdateError.CORRUPTED -> stringResource(R.string.update_error_corrupted)
        UpdateError.INSTALL_FAILED -> stringResource(R.string.update_error_install) +
            (message?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** Carte en haut des réglages quand une nouvelle version d'Épure est disponible. */
@Composable
fun UpdateBanner(state: UpdateState, onOpen: () -> Unit) {
    val update = state.launcherUpdate ?: state.updates.firstOrNull() ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.update_available, update.target.label(), update.entry.versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.update_banner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Bouton « Installer le module » (réglages des notifications), quand il n'est pas installé. */
@Composable
fun ModuleInstallButton(updates: UpdateRepository) {
    val state by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (state.manifest == null) updates.check() }
    if (state.moduleOffer == null) return
    val busy = state.progress != null
    ButtonRow {
        Button(onClick = { startInstall(context, updates, UpdateTarget.MODULE) }, enabled = !busy) {
            Text(stringResource(R.string.notif_module_install))
        }
    }
    state.progress?.takeIf { it.target == UpdateTarget.MODULE }?.let { progress ->
        val fraction = progress.fraction
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
    state.error?.let { UpdateErrorNote(it, state.errorMessage) }
}

/**
 * Affiche l'écran de confirmation d'Android dès qu'il est prêt (il doit partir d'une
 * activité visible).
 */
@Composable
fun UpdateConfirmationEffect(updates: UpdateRepository) {
    val state by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val confirmation = state.confirmation
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            runCatching { context.startActivity(confirmation) }
            updates.confirmationShown()
        }
    }
}
