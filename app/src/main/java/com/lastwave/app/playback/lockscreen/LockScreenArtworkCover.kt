package com.lastwave.app.playback.lockscreen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastwave.app.R

/**
 * Full-screen cover with the track-change transition.
 *
 * A new cover is pushed as a new layer above the current one, but stays fully
 * transparent until Coil has decoded it; meanwhile the previous cover remains
 * on screen, so a skip can never flash black or show an empty frame. Once the
 * new image is ready it fades in over [COVER_FADE_MS] while growing from 96%
 * with the player's 22 dp artwork corner radius to the full-bleed edge, then
 * the layers underneath are dropped. The link is the published track artwork
 * URL itself, i.e. exactly the track the service is playing.
 *
 * Layers are capped at [MAX_COVER_LAYERS]: skipping several times quickly
 * keeps the visible base cover (the oldest layer) and only the two newest
 * requests, so the transition stays gapless without holding a stack of
 * full-screen bitmaps. A failed or missing image falls back to the branded
 * cover instead of leaving the previous track's art on screen.
 */
@Composable
internal fun LockScreenArtworkCover(
    artworkUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // Same decode target as the previous single-image cover: the panel's long
    // edge, so a square cover fills a tall screen without upscaling twice.
    val decodeSizePx = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density,
    ) {
        with(density) {
            maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx()
        }.coerceAtLeast(1)
    }
    val layers = remember { mutableStateListOf(CoverLayer(0L, artworkUrl)) }

    LaunchedEffect(artworkUrl) {
        val current = layers.lastOrNull()?.artworkUrl
        if (current != artworkUrl) {
            layers += CoverLayer((layers.maxOfOrNull { it.id } ?: 0L) + 1L, artworkUrl)
            if (layers.size > MAX_COVER_LAYERS) {
                // Keep the oldest (the one actually visible) plus the newest
                // two; the layer between them is either covered or redundant.
                layers.removeAt(1)
            }
        }
    }

    Box(modifier) {
        layers.forEach { layer ->
            key(layer.id) {
                CoverLayerView(
                    layer = layer,
                    contentDescription = contentDescription,
                    decodeSizePx = decodeSizePx,
                    isLatest = layer.id == layers.last().id,
                    onSettled = {
                        val index = layers.indexOfFirst { it.id == layer.id }
                        repeat(index.coerceAtLeast(0)) { layers.removeAt(0) }
                    },
                )
            }
        }
    }
}

@Composable
private fun CoverLayerView(
    layer: CoverLayer,
    contentDescription: String,
    decodeSizePx: Int,
    isLatest: Boolean,
    onSettled: () -> Unit,
) {
    val context = LocalContext.current
    val reveal = remember { Animatable(0f) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(layer.artworkUrl.isNullOrBlank()) }

    val request = remember(layer.artworkUrl, decodeSizePx, context) {
        layer.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(decodeSizePx)
                .allowHardware(true)
                .build()
        }
    }

    // Independently of being the newest layer: an image that finishes loading
    // under a newer request still becomes a visible cover, never a hole.
    LaunchedEffect(loaded, failed) {
        if (!loaded && !failed) return@LaunchedEffect
        if (reveal.value < 1f) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(COVER_FADE_MS, easing = FastOutSlowInEasing),
            )
            onSettled()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = reveal.value
                val scale = COVER_START_SCALE + (1f - COVER_START_SCALE) * reveal.value
                scaleX = scale
                scaleY = scale
                clip = true
                shape = RoundedCornerShape(COVER_START_CORNER * (1f - reveal.value))
            },
    ) {
        if (request != null && !failed) {
            AsyncImage(
                model = request,
                contentDescription = if (isLatest) contentDescription else null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                filterQuality = FilterQuality.High,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LockScreenArtworkFallback()
        }
    }
}

@Composable
private fun LockScreenArtworkFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1C1B1F), Color.Black))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_logo),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(120.dp),
        )
    }
}

private data class CoverLayer(val id: Long, val artworkUrl: String?)

private const val COVER_FADE_MS = 420
private const val MAX_COVER_LAYERS = 3
private const val COVER_START_SCALE = 0.96f
private val COVER_START_CORNER = 22.dp
