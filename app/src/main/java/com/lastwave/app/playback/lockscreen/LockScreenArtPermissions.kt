package com.lastwave.app.playback.lockscreen

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * The two locks between a full-screen intent and the keyguard: Android 14's
 * `USE_FULL_SCREEN_INTENT` grant, and MIUI's own "Show on lock screen" app-op
 * (10020). MIUI ignores `showWhenLocked` without the second one and its
 * settings UI reports neither state truthfully, so the feature reads the real
 * grants instead of trusting a toggle.
 */
internal object LockScreenArtPermissions {

    enum class Gate { Ready, FullScreenDenied, MiuiLockScreenDenied }

    /** MIUI app-op behind App info → Other permissions → Show on lock screen. */
    private const val MIUI_OP_SHOW_WHEN_LOCKED = 10020

    fun gate(context: Context): Gate = when {
        !canUseFullScreenIntents(context) -> Gate.FullScreenDenied
        miuiLockScreenAllowed(context) == false -> Gate.MiuiLockScreenDenied
        else -> Gate.Ready
    }

    fun canUseFullScreenIntents(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

    /**
     * `true`/`false` when MIUI answers, `null` on every other ROM: op 10020
     * does not exist in AOSP, so the reflective lookup throws and the feature
     * must not claim a grant it cannot see. Xiaomi denies the op by default
     * for apps it has not whitelisted, so anything but an explicit allow is a denial.
     */
    fun miuiLockScreenAllowed(context: Context): Boolean? {
        if (!isMiui()) return null
        return runCatching {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val check = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java,
            )
            val mode = check.invoke(ops, MIUI_OP_SHOW_WHEN_LOCKED, Process.myUid(), context.packageName) as Int
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    fun isMiui(): Boolean = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
        Build.MANUFACTURER.equals("redmi", ignoreCase = true) ||
        Build.MANUFACTURER.equals("poco", ignoreCase = true)

    /** Settings → Special app access → Full-screen notifications. */
    fun requestFullScreenIntents(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** App info → Other permissions → Show on lock screen. */
    fun requestMiuiLockScreen(context: Context): Boolean {
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(editor) }.isSuccess) return true
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(details) }.isSuccess
    }
}
