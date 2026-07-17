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

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE: 既存スケジュールを新しい内容で更新する
                // （KEEPだとアプリ更新後も旧スケジュールが残り、実行時刻の変更が反映されない）
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
