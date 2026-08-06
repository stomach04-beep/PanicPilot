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
import com.example.panicpilot.data.NotifLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    /**
     * 通知を出す（POST_NOTIFICATIONS 未許可なら通知自体はスキップ）。
     * どちらの場合も通知履歴（NotifLog）には必ず記録する。
     * 権限が無くて出せなかったことも履歴に残るので、後から気づける。
     */
    fun notify(context: Context, id: Int, title: String, text: String) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        // ─── 通知履歴に記録（日本時間） ───
        val jstNow = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }
            .format(Date())
        NotifLog.append(context, NotifLog.Entry(jstNow, title, text, granted))

        if (!granted) return
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dip_buy)
            .setColor(android.graphics.Color.parseColor("#FFAB00"))
            .setLargeIcon(notifBadge(context, R.drawable.ic_badge_dip_buy))
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


// 通知の大アイコン用: カラーの丸バッジ(ベクター)を Bitmap に焼いて返す。
// 上端バーの小アイコンと違い、大アイコンはOSに白く塗り直されないので色が出る。
private fun notifBadge(ctx: android.content.Context, resId: Int): android.graphics.Bitmap {
    val size = ((ctx.resources.displayMetrics.density * 48).toInt()).coerceAtLeast(96)
    val d = ctx.getDrawable(resId)!!
    val bmp = android.graphics.Bitmap.createBitmap(
        size, size, android.graphics.Bitmap.Config.ARGB_8888
    )
    d.setBounds(0, 0, size, size)
    d.draw(android.graphics.Canvas(bmp))
    return bmp
}
