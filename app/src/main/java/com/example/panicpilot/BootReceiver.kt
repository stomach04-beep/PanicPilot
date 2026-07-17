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
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 日次の市場チェックWorkを再登録（UPDATEポリシーなので重複登録の心配なし）
                PanicPilotApp.scheduleDailyCheck(context)
            }
        }
    }
}
