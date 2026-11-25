package com.lastwave.app.playback.lockscreen

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the lock-screen cover shows, handed from the playback service to
 * [LockScreenArtworkActivity]. Both live in the same process, so a tiny
 * state holder is enough — no binder, no disk, nothing that survives the
 * service (a cleared snapshot is what dismisses the activity).
 */
internal object LockScreenArtworkHub {
    @Immutable
    data class Snapshot(
        val title: String,
        val artist: String,
        val album: String,
        val artworkUrl: String?,
        val isPlaying: Boolean,
    )

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    fun publish(
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        isPlaying: Boolean,
    ) {
        val next = Snapshot(title, artist, album, artworkUrl, isPlaying)
        if (_snapshot.value != next) _snapshot.value = next
    }

    fun clear() {
        if (_snapshot.value != null) _snapshot.value = null
    }
}
