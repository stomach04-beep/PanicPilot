package com.example.panicpilot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 通知まわり。チャンネルIDはバージョン付き
 * （設定を変えたいときは _v2 に上げて作り直す既知の教訓）
 */
object NotificationHelper {

    private const val CHANNEL_ID = "panic_signal_v1"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(
            CHANNEL_ID,
            "出動シグナル通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "パニック買いの点灯・買い増し・出口の通知"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(ch)
    }

    /** 通知を出す（POST_NOTIFICATIONS 未許可なら静かにスキップ） */
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, n)
    }
}
