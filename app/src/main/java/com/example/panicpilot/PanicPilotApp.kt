package com.example.panicpilot

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Applicationクラス。通知チャンネル作成と日次Workerの登録。
 */
class PanicPilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        scheduleDailyCheck(this)
    }

    companion object {
        /** 日次チェックWorkの一意名 */
        private const val WORK_NAME = "daily_market_check"

        /** 「今どういう予定で登録済みか」を覚えておく場所（毎起動の再登録を避けるため） */
        private const val SCHEDULE_PREFS = "panicpilot_schedule"
        private const val KEY_SCHEDULED_SIGNATURE = "scheduled_signature"

        /**
         * 毎日1回、引け後（16:30 JST目標）に市場チェックを走らせる。
         * WorkManagerの周期実行は正確な時刻を保証しないが、
         * 初回遅延を「次の16:30まで」にして以後24時間周期で近づける。
         * （Dozeで数時間遅れることがある既知の挙動は許容＝日次判定なので実害小）
         *
         * Application.onCreate と BootReceiver（再起動・アプリ更新時）の両方から呼ばれる。
         */
        fun scheduleDailyCheck(context: Context) {
            val jst = TimeZone.getTimeZone("Asia/Tokyo")
            val now = Calendar.getInstance(jst)
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 16)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)   // 今日の16:30を過ぎていたら翌日
            }
            val initialDelayMin = (next.timeInMillis - now.timeInMillis) / 60000

            val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMin, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            // 【なぜ毎回UPDATEしないか】UPDATEは実行中のジョブを中断（onStopJob）して
            // 再スケジュールする。このWorkerが発火するとプロセスが起動し、
            // Application.onCreate がここを呼ぶため、「自分を起こしたジョブを中断して
            // 作り直す」形になる。中断されても中身は走り切るので二重実行になり、
            // 最悪 initialDelay が翌日の16:30に付け直されてその日のチェックが飛ぶ。
            // EtfBuyAlert v1.20 で実機ログ（generation 2818まで増加・同一通知3連発）
            // から特定した事故と同じ構造なので、同じ対策を入れる。
            // 予定時刻が変わったときだけUPDATE、同じならKEEP＝既存の登録に触らない。
            val prefs = context.applicationContext
                .getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)
            val signature = "16:30/24h"   // 予定の内容。変えたらここも変える
            val changed = prefs.getString(KEY_SCHEDULED_SIGNATURE, null) != signature

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (changed) ExistingPeriodicWorkPolicy.UPDATE
                else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            if (changed) prefs.edit().putString(KEY_SCHEDULED_SIGNATURE, signature).apply()
        }
    }
}
