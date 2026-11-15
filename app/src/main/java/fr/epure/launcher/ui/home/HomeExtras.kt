package fr.epure.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.epure.launcher.R
import fr.epure.launcher.data.media.NowPlaying
import fr.epure.launcher.ui.icons.LineIcons
import fr.epure.launcher.ui.theme.LocalLauncherLook

/** Petite pastille « Nouvelle version d'Épure disponible » au-dessus des favoris. */
@Composable
fun UpdateNotice(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLauncherLook.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(look.onWallpaper.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(Icons.Outlined.ArrowCircleUp, contentDescription = null, tint = look.onWallpaper, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = with(look) {
                MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp * textScale).onWallpaper()
            },
        )
    }
}

/**
 * Lecteur de l'accueil : pochette (ou note de musique), titre et artiste, puis
 * lecture-pause et morceau suivant (et précédent s'il y a la place).
 */
@Composable
fun NowPlayingCard(
    nowPlaying: NowPlaying,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLauncherLook.current
    val type = MaterialTheme.typography
    val artSize = 44.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Le bouton « précédent » seulement si le titre garde assez de place.
        val showPrevious = nowPlaying.canSkip && maxWidth >= 340.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(look.onWallpaper.copy(alpha = 0.10f))
                .padding(start = 8.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpen),
            ) {
                val art = nowPlaying.art
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(look.onWallpaper.copy(alpha = 0.08f)),
                ) {
                    if (art != null) {
                        val image = remember(art) { art.asImageBitmap() }
                        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    } else {
                        LineIcons.vector("music", 24.dp)?.let {
                            Icon(it, contentDescription = null, tint = look.onWallpaper, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.title,
                        style = with(look) { type.titleMedium.copy(fontSize = 15.sp * textScale, fontWeight = FontWeight.Medium).onWallpaper() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    nowPlaying.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                        Text(
                            text = artist,
                            style = with(look) { type.bodyMedium.copy(fontSize = 13.sp * textScale).onWallpaper(muted = true) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showPrevious) MediaButton("player-skip-back", stringResource(R.string.media_previous), onPrevious)
            MediaButton(
                if (nowPlaying.isPlaying) "player-pause" else "player-play",
                stringResource(if (nowPlaying.isPlaying) R.string.media_pause else R.string.media_play),
                onPlayPause,
                size = 26.dp,
            )
            if (nowPlaying.canSkip) MediaButton("player-skip-forward", stringResource(R.string.media_next), onNext)
        }
    }
}

@Composable
private fun MediaButton(icon: String, description: String, onClick: () -> Unit, size: Dp = 22.dp) {
    val look = LocalLauncherLook.current
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        LineIcons.vector(icon, size)?.let {
            Icon(it, contentDescription = description, tint = look.onWallpaper, modifier = Modifier.size(size))
        }
    }
}
