package com.lastwave.app.playback.lockscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastwave.app.ui.common.ExpressiveMotion

/**
 * The full-screen cover. It deliberately reuses the full player's visual
 * language instead of inventing one: the track-change crossfade from
 * [LockScreenArtworkCover], the same ExtraBold headline/artist typography,
 * the same Material transport icons, white-veil circles and play pill, the
 * same expressive press springs and haptics, and the same top/bottom
 * contrast scrim as the full-bleed player.
 */
@Composable
internal fun LockScreenArtworkScreen(
    snapshot: LockScreenArtworkHub.Snapshot,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = ExpressiveMotion.effectsDefault(),
        label = "lockCoverContentAlpha",
    )
    val contentOffset by animateDpAsState(
        targetValue = if (revealed) 0.dp else 22.dp,
        animationSpec = ExpressiveMotion.spatialDefault(),
        label = "lockCoverContentOffset",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LockScreenArtworkCover(
            artworkUrl = snapshot.artworkUrl,
            contentDescription = "${snapshot.title} — ${snapshot.artist}",
            modifier = Modifier.fillMaxSize(),
        )
        // Same scrim recipe as the player's full-bleed canvas: it dims the
        // edges just enough for the title and controls without washing out
        // the cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.34f),
                        0.16f to Color.Transparent,
                        0.54f to Color.Transparent,
                        0.78f to Color.Black.copy(alpha = 0.42f),
                        1.00f to Color.Black.copy(alpha = 0.84f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset.toPx()
                }
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = snapshot.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    letterSpacing = (-0.35).sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.artist,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (snapshot.album.isNotBlank()) {
                Text(
                    text = snapshot.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(26.dp))
            LockScreenTransport(
                isPlaying = snapshot.isPlaying,
                onPrevious = onPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun LockScreenTransport(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    // Identical geometry to the player's MainControls: fixed circles and an
    // adaptive play pill that never clips on narrow screens.
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val sideSize = 54.dp
        val gap = 18.dp
        val playWidth = (maxWidth - sideSize * 2 - gap * 2).coerceIn(112.dp, 180.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockScreenSideControl(
                icon = Icons.Filled.SkipPrevious,
                label = "Previous",
                size = sideSize,
                onClick = onPrevious,
            )
            LockScreenPlayPause(
                isPlaying = isPlaying,
                width = playWidth,
                onClick = onTogglePlayPause,
            )
            LockScreenSideControl(
                icon = Icons.Filled.SkipNext,
                label = "Next",
                size = sideSize,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun LockScreenSideControl(
    icon: ImageVector,
    label: String,
    size: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = ExpressiveMotion.spatialSpring(),
        label = "lockSideScale",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun LockScreenPlayPause(
    isPlaying: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        // Exactly the full player's play-button spring.
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "lockPlayScale",
    )
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(100))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = {
                    haptics.performHapticFeedback(
                        if (isPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    onClick()
                },
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LockScreenPlayPauseIcon(isPlaying, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isPlaying) "Pause" else "Play",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.94f),
        )
    }
}

/** Exactly the player's animated play/pause swap: fade + expressive scale. */
@Composable
private fun LockScreenPlayPauseIcon(isPlaying: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(tween(ExpressiveMotion.Quick)) +
                scaleIn(ExpressiveMotion.spatialSpring(), initialScale = 0.7f)) togetherWith
                (fadeOut(tween(ExpressiveMotion.Quick)) +
                    scaleOut(tween(ExpressiveMotion.Quick), targetScale = 0.7f))
        },
        label = "lockPlayPauseIcon",
        modifier = modifier,
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
