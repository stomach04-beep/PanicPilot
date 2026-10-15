package com.example.panicpilot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 日次チェックを毎日 16:30（日本時間）に鳴らすアラーム（v2.3.10・2026-10-15）
 *
 * なぜ要るか:
 *   WorkManager の24時間周期は「前回の実行時刻から24時間」で数える。端末の省電力で1回遅れると
 *   その遅れが翌日へ持ち越され、16:30 の目標から毎日後ろへずれていく
 *   （実機: 10-14 は 18:01、10-15 は 19:30 に実行、次回予定も 10-16 19:30 ごろだった）。
 *   点灯・買い増しの判定は引け後すぐに走ることに意味があるので、時刻を毎回「次の 16:30」から
 *   計算し直すアラームの自己連鎖にする（EtfBuyAlert v1.21 で遅延を解消した方式と同じ）。
 *
 * setExactAndAllowWhileIdle は省電力（Doze）中でも鳴り、鳴った直後は一時的に通信できる。
 * 24時間周期の定期ジョブは保険として残す（アラームが端末に消された日だけ拾う）。
 * 1日に2回走ることがあるが、通知は「状態が続く間は1回だけ」（EpisodeNotifier）なので重複しない。
 */
object DailyAlarm {

    private const val TAG = "DailyAlarm"
    private const val REQUEST_CODE = 1630
    const val ACTION = "com.example.panicpilot.DAILY_CHECK_ALARM"

    /** 判定を走らせる時刻（日本時間）。引け 15:30 のあと、データが出そろう頃 */
    val CHECK_TIME: LocalTime = LocalTime.of(16, 30)
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    /** アラームが鳴った直後に「今日の 16:30」をもう一度選ばないための余裕（ミリ秒） */
    private const val MIN_LEAD_MS = 60_000L

    /**
     * nowMillis より後の、いちばん近い 16:30（日本時間）をミリ秒で返す。
     * 「今日の 16:30」が1分以内または過去なら翌日の 16:30。
     * 前回いつ走ったかに関係なく毎回ここから計算するので、遅れが翌日へ持ち越されない。
     * Context を取らない純粋な関数（単体テスト用）
     */
    fun nextTriggerMillis(nowMillis: Long): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(JST)
        var target = ZonedDateTime.of(now.toLocalDate(), CHECK_TIME, JST)
        if (target.toInstant().toEpochMilli() - nowMillis < MIN_LEAD_MS) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    /** 次の 16:30 にアラームを掛ける（同じ要求コードなので何度呼んでも1本だけ） */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE,
            Intent(ctx, DailyAlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = nextTriggerMillis(System.currentTimeMillis())
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // 正確なアラームの権限が無い端末では、不正確でも省電力中に鳴る方へ落とす
                Log.w(TAG, "正確なアラームの権限なし→不正確なアラームで代替")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            Log.i(TAG, "次の日次チェックのアラーム: ${Instant.ofEpochMilli(at).atZone(JST)}")
        } catch (e: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e2: Exception) {
                Log.e(TAG, "アラームの代替も失敗: ${e2.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "アラームの設置に失敗: ${e.message}")
        }
    }
}

/**
 * 16:30 のアラームで呼ばれる受け口。日次チェックを1回積み、翌日の 16:30 を掛け直す（自己連鎖）。
 * 通信の条件は付けない（v2.3.9 と同じ理由。通信できない回は失敗として数えて知らせる）
 */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyAlarm.ACTION) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily_market_check_alarm",       // 同名で積む＝連打されても1本だけ
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DailyCheckWorker>().build()
        )
        // 次の 16:30 を掛け直す（これを忘れると1回で止まる）
        DailyAlarm.schedule(context)
    }
}
