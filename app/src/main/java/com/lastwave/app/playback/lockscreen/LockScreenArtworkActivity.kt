package com.lastwave.app.playback.lockscreen

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.repository.ThemeRepository
import com.lastwave.app.playback.MusicPlaybackService
import com.lastwave.app.ui.theme.LastWaveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen cover shown on top of the keyguard. It is only ever started by
 * the full-screen intent [LockScreenArtworkController] posts while music is
 * playing, and it never turns the panel on by itself: the cover is waiting
 * when the user picks the phone up.
 *
 * The activity is Hilt-enabled only to read [ThemeRepository]: the cover has
 * to use the same app theme (Google Sans Flex typography, haptic setting) as
 * every player surface, not bare Material defaults.
 */
@AndroidEntryPoint
class LockScreenArtworkActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launch window would otherwise paint the theme's surface before
        // Compose draws the cover, a visible flash over the keyguard.
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        enableEdgeToEdge()
        showOverKeyguard()
        setContent {
            val theme by themeRepository.uiState.collectAsStateWithLifecycle()
            LastWaveTheme(theme) {
                val snapshot by LockScreenArtworkHub.snapshot.collectAsState()
                val cover = snapshot
                if (cover == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    LaunchedEffect(Unit) {
                        // The service publishes before posting the intent; an
                        // empty snapshot means playback already stopped, so do
                        // not leave a black page over the lock screen.
                        finish()
                    }
                } else {
                    LockScreenArtworkScreen(
                        snapshot = cover,
                        onPrevious = { sendPlaybackAction(MusicPlaybackService.ACTION_PREVIOUS) },
                        onTogglePlayPause = { sendPlaybackAction(MusicPlaybackService.ACTION_TOGGLE) },
                        onNext = { sendPlaybackAction(MusicPlaybackService.ACTION_NEXT) },
                    )
                }
            }
        }
    }

    private fun showOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Reuses the transport actions the media notification already handles. */
    private fun sendPlaybackAction(action: String) {
        runCatching {
            startService(Intent(this, MusicPlaybackService::class.java).setAction(action))
        }.onFailure { error ->
            android.util.Log.w(TAG, "Lock-screen transport action failed", error)
        }
    }

    private companion object {
        const val TAG = "LockScreenArtwork"
    }
}
