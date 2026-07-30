package com.example.panicpilot

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 「アプリを閉じた状態でも通知が届くか」を確かめるためのテストWorker。
 * 履歴タブのボタンから15秒遅延で予約される。
 * 実行時点でアプリのプロセスが死んでいても、WorkManagerがプロセスを
 * 起こしてこのWorkerを走らせ通知を出す＝本番の日次チェックと同じ経路なので、
 * これが届けば点灯サインの通知も届くことの証明になる。
 */
class TestNotifyWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        NotificationHelper.notify(
            applicationContext,
            TEST_DELAYED_NOTIFICATION_ID,
            "🔔 停止状態テスト通知",
            "アプリを閉じていてもこの通知が見えていれば、実際に点灯サインがついたときも同じ経路で届きます。"
        )
        return Result.success()
    }

    companion object {
        // 本番の通知ID(1〜6)や即時テスト(90001)とぶつからない番号
        const val TEST_DELAYED_NOTIFICATION_ID = 90002
    }
}
