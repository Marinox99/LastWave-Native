package com.lastwave.app.playback.lockscreen

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lastwave.app.MainShellActivity
import com.lastwave.app.R
import com.lastwave.app.data.artwork.ArtworkNormalizer
import com.lastwave.app.data.local.LockScreenArtPreferences
import com.lastwave.app.playback.MusicPlayerState
import com.lastwave.app.playback.PlayableTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Shows the current cover over the lock screen while music plays, when the
 * user turned the option on.
 *
 * The lock screen itself belongs to SystemUI and cannot be replaced, and a
 * background service may not start an activity on its own since Android 10.
 * The one supported way over the keyguard is a full-screen intent, so the
 * service posts a silent, high-importance notification carrying that intent
 * at screen-off; the system then launches [LockScreenArtworkActivity], which
 * declares `showWhenLocked`. Android 14+ additionally requires the user to
 * grant full-screen notification access, checked before anything is posted.
 */
internal class LockScreenArtworkController(
    context: Context,
    private val preferences: LockScreenArtPreferences,
    private val scope: CoroutineScope,
    private val playerState: StateFlow<MusicPlayerState>,
    private val resolveArtworkUrl: () -> String?,
) {
    private val appContext = context.applicationContext
    private val notifications = appContext.getSystemService(NotificationManager::class.java)
    private val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    @Volatile private var enabled = false
    private var revealJob: Job? = null
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> scheduleReveal()
                Intent.ACTION_USER_PRESENT -> hide()
                // The panel came back on while still locked: leave the cover
                // alone. It only has to disappear once the device is unlocked.
                Intent.ACTION_SCREEN_ON -> if (!isLocked()) hide()
            }
        }
    }

    fun start() {
        scope.launch {
            preferences.enabled.collect { value ->
                enabled = value
                if (value) sync(playerState.value) else hide()
            }
        }
        scope.launch {
            playerState
                .map { PublishKey(it.current, it.isPlaying, resolveArtworkUrl()) }
                .distinctUntilChanged()
                .collect {
                    if (enabled) sync(playerState.value) else LockScreenArtworkHub.clear()
                }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        receiverRegistered = runCatching {
            // These are protected system broadcasts; the project registers
            // its other system receivers as exported so delivery is never
            // filtered by the Android 13 receiver flags.
            ContextCompat.registerReceiver(
                appContext,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            true
        }.getOrDefault(false)
    }

    /** Called when artwork resolves: the URL the cover should use has changed. */
    fun refresh(state: MusicPlayerState) {
        if (enabled) sync(state)
    }

    fun stop() {
        hide()
        if (receiverRegistered) runCatching { appContext.unregisterReceiver(screenReceiver) }
        receiverRegistered = false
    }

    private fun sync(state: MusicPlayerState) {
        val track = state.current
        if (track == null) {
            hide()
            return
        }
        // A pause keeps the cover on screen: its play button is the way back
        // into playback, and closing it would leave the user with only the
        // system media card underneath. Only an empty queue (stop/end) hides
        // the cover on its own; unlocking and disabling the option call
        // hide() directly.
        LockScreenArtworkHub.publish(
            title = track.title,
            artist = track.artist,
            album = track.album.orEmpty(),
            artworkUrl = coverUrl(track),
            isPlaying = state.isPlaying,
        )
    }

    private fun coverUrl(track: PlayableTrack): String? {
        val primary = resolveArtworkUrl()?.takeIf { it.isNotBlank() }
        // The cover is full-screen, so ask for the high-resolution master
        // even when the resolved URL is a compressed YouTube/Google variant;
        // other providers already hand back their largest image and pass
        // through unchanged.
        return ArtworkNormalizer.upscaleYoutubeArtwork(primary)
            ?: primary
            ?: ArtworkNormalizer.upscaleYoutubeArtwork(track.artworkUrl)
            ?: track.artworkUrl
    }

    private fun scheduleReveal() {
        if (!enabled || !LockScreenArtPermissions.canUseFullScreenIntents(appContext)) return
        revealJob?.cancel()
        revealJob = scope.launch {
            // The keyguard finishes locking a beat after the screen goes
            // dark. A full-screen intent posted during that transition counts
            // as "device in use" on Android 13+ and is shown as a heads-up
            // instead of covering the lock screen.
            delay(KEYGUARD_SETTLE_MS)
            if (!enabled || !isLocked()) return@launch
            if (!LockScreenArtPermissions.canUseFullScreenIntents(appContext)) return@launch
            val state = playerState.value
            if (state.current == null || !state.isPlaying) return@launch
            sync(state)
            postCoverNotification(notifications ?: return@launch)
        }
    }

    private fun postCoverNotification(manager: NotificationManager) {
        val track = playerState.value.current ?: return
        ensureChannel(manager)
        val openApp = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            Intent(appContext, MainShellActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showCover = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            Intent(appContext, LockScreenArtworkActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_logo)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setFullScreenIntent(showCover, true)
            .setSilent(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { error ->
                android.util.Log.w(TAG, "Lock-screen cover notification rejected", error)
            }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Lock screen artwork",
                    // Full-screen intents are only honoured from a
                    // high-importance channel; the sound and vibration are
                    // off so posting it never makes a noise.
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Shows the album cover over the lock screen while music plays"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }

    private fun hide() {
        revealJob?.cancel()
        revealJob = null
        LockScreenArtworkHub.clear()
        runCatching { notifications?.cancel(NOTIFICATION_ID) }
    }

    private fun isLocked(): Boolean =
        runCatching { keyguard?.isKeyguardLocked == true }.getOrDefault(false)

    private data class PublishKey(
        val track: PlayableTrack?,
        val isPlaying: Boolean,
        val artworkUrl: String?,
    )

    private companion object {
        const val TAG = "LockScreenArtwork"
        const val CHANNEL_ID = "lastwave_lock_screen_art_v1"
        const val NOTIFICATION_ID = 4103
        const val KEYGUARD_SETTLE_MS = 700L
    }
}
