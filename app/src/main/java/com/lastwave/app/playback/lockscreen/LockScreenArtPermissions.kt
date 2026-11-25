package com.lastwave.app.playback.lockscreen

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android 14 stopped granting `USE_FULL_SCREEN_INTENT` by default to apps
 * that are not alarms or calling apps; the user grants it from Special app
 * access. Without that grant a full-screen intent is downgraded to a
 * heads-up notification, so the cover would never appear over the keyguard
 * and the feature has to say so instead of silently doing nothing.
 */
internal object LockScreenArtPermissions {

    fun canUseFullScreenIntents(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

    /** Settings → Special app access → Full-screen notifications. */
    fun requestFullScreenIntents(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
