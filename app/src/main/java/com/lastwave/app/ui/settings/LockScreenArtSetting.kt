package com.lastwave.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.LockScreenArtPreferences
import com.lastwave.app.playback.lockscreen.LockScreenArtPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockScreenArtSettingsViewModel @Inject constructor(
    private val preferences: LockScreenArtPreferences,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = preferences.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setEnabled(enabled) }
    }
}

/**
 * Appearance → Lock screen row. The state lives in its own ViewModel and
 * preference store so the existing SettingsViewModel / MiscSettings mapping
 * is untouched.
 */
@Composable
internal fun LockScreenArtSettingCard(
    position: GroupPosition,
    isHighlighted: Boolean,
    viewModel: LockScreenArtSettingsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var gate by remember { mutableStateOf(LockScreenArtPermissions.gate(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        // The grant lives in system Settings, so re-read it when the user
        // comes back instead of showing a stale "permission required" note.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                gate = LockScreenArtPermissions.gate(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsToggleCard(
        icon = Icons.Filled.StayCurrentPortrait,
        iconContainer = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        title = "Full-screen lock screen cover",
        subtitle = when {
            !enabled -> "Off • Show the album cover across the lock screen while playing"
            gate == LockScreenArtPermissions.Gate.FullScreenDenied ->
                "On • Allow full-screen notifications in system settings"
            gate == LockScreenArtPermissions.Gate.MiuiLockScreenDenied ->
                "On • Allow \"Show on lock screen\" in Other permissions"
            else -> "On • Album cover fills the lock screen while playing"
        },
        checked = enabled,
        onCheckedChange = { value ->
            viewModel.setEnabled(value)
            if (value) {
                when (LockScreenArtPermissions.gate(context)) {
                    LockScreenArtPermissions.Gate.FullScreenDenied ->
                        LockScreenArtPermissions.requestFullScreenIntents(context)
                    LockScreenArtPermissions.Gate.MiuiLockScreenDenied ->
                        LockScreenArtPermissions.requestMiuiLockScreen(context)
                    LockScreenArtPermissions.Gate.Ready -> Unit
                }
            }
        },
        position = position,
        isHighlighted = isHighlighted,
    )
}
