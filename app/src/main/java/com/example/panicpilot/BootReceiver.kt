package com.example.panicpilot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 端末再起動時・アプリ更新時に日次チェックのPeriodicWorkを再登録するレシーバー。
 * adb install -r 等の更新は内部でforce-stop相当が走りWorkが止まることがあるため、
 * BOOT_COMPLETED に加えて MY_PACKAGE_REPLACED も必ず受ける（既知の教訓）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // 正確なアラームの権限が変わったら 16:30 のアラームを掛け直す（v2.3.10）
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                // 日次の市場チェックWorkを再登録（変わったときだけUPDATE）＋16:30のアラームを掛け直す
                PanicPilotApp.scheduleDailyCheck(context)
            }
        }
    }
}
