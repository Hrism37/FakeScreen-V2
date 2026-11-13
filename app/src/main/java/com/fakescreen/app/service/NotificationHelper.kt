package com.fakescreen.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fakescreen.app.R
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.ui.MainActivity

object NotificationHelper {

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val sessionChannel = NotificationChannel(
            AppConstants.SESSION_CHANNEL_ID,
            context.getString(R.string.channel_session),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_session_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val alertChannel = NotificationChannel(
            AppConstants.ALERT_CHANNEL_ID,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
        }
        manager.createNotificationChannel(sessionChannel)
        manager.createNotificationChannel(alertChannel)
    }

    fun buildSessionNotification(
        context: Context,
        mode: ScreenMode,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, AppConstants.SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在模拟息屏")
            .setContentText("${mode.title} · 输入 PIN 可退出")
            .setSubText("深色覆盖不会真正关闭屏幕")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyAlert(context: Context, title: String, text: String) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            val notification = NotificationCompat.Builder(context, AppConstants.ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context)
                .notify(AppConstants.ALERT_NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            AppLog.w("Unable to post alert notification", t)
        }
    }
}
