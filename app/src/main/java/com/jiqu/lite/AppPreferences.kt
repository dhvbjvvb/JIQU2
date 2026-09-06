package com.jiqu.lite

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/** User settings persisted independently from the Compose UI. */
internal object AppPreferences {
    const val FILE = "app_settings"
    const val DARK_THEME = "dark_theme"
    const val ACCENT_HUE = "accent_hue"
    const val APPLE_FLOATING_NAV = "apple_floating_nav"
    const val DOWNLOAD_COMPLETION_NOTIFICATIONS = "download_completion_notifications"
    const val AUTO_PASTE_PARSE = "auto_paste_parse"
    const val AUTOMATIC_UPDATE_CHECK_IGNORED = "automatic_update_check_ignored"
    const val DOWNLOAD_CHANNEL_MIGRATED = "download_channel_migrated_v8"
}

/** Migrates the former download-notification switch to completion notifications once. */
internal fun Context.isDownloadCompletionNotificationsEnabled(): Boolean {
    val preferences = getSharedPreferences(AppPreferences.FILE, Context.MODE_PRIVATE)
    if (preferences.contains(AppPreferences.DOWNLOAD_COMPLETION_NOTIFICATIONS)) {
        return preferences.getBoolean(AppPreferences.DOWNLOAD_COMPLETION_NOTIFICATIONS, true)
    }
    val enabled = preferences.getBoolean("download_notifications", true)
    preferences.edit()
        .putBoolean(AppPreferences.DOWNLOAD_COMPLETION_NOTIFICATIONS, enabled)
        .remove("download_notifications")
        .apply()
    return enabled
}

/** Dedicated channel for the notification emitted after a download completes. */
internal object DownloadCompletionNotification {
    const val CHANNEL_ID = "download_complete_v8"

    /** Remove old channels once and create the single completion channel with system defaults. */
    fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // On Android 13+, creating the channel before notification permission is granted can
        // make some ROMs initialize the category with their own disabled defaults.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val preferences = context.getSharedPreferences(AppPreferences.FILE, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(AppPreferences.DOWNLOAD_CHANNEL_MIGRATED, false)) {
            listOf(
                "download_completion",
                "download_completion_v2",
                "download_complete",
                "download_complete_v2",
                "download_status",
                "download_progress",
                "download_complete_v3",
                "download_complete_v4",
                "download_complete_v5",
                "download_complete_v6",
                "download_complete_v7",
                CHANNEL_ID
            )
                .forEach(manager::deleteNotificationChannel)
            preferences.edit().putBoolean(AppPreferences.DOWNLOAD_CHANNEL_MIGRATED, true).apply()
        }
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "下载完成",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "媒体文件下载完成提醒"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 250L)
                    setSound(
                        // Keep the channel tied to the system notification sound instead of
                        // copying the current URI, so future system sound changes are respected.
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setAllowBubbles(true)
                }
            )
        }
    }
}

/** Public MediaStore locations used by every download entry point. */
internal object DownloadPaths {
    const val ROOT = "JIQU"
    const val VIDEO = "video"
    const val PICTURES = "pictures"
    const val DISPLAY_ROOT = "Download / JIQU"
}
