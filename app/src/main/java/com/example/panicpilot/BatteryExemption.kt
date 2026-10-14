package com.example.panicpilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 電池最適化の除外（v2.3.9・2026-10-15）
 *
 * なぜ要るか:
 *   除外していないアプリは、開く頻度が低いと Android に「制限（RESTRICTED）」へ分類され、
 *   裏での通信を止められる。Be Greedy は 2026-10-15 時点で待機バケット 45＝RESTRICTED だった。
 *   この状態だと日次チェックは1日1回に絞られて遅れ（16:30 予定が 18:01 に実行）、
 *   通信を遮断された回は名前解決から失敗する（10-06 の米国取得失敗 UnknownHostException）。
 *   兄弟アプリ PnlSentiment は同じ理由で定期チェックが13日間1回も起動せず黙っていた（LESSON-262）。
 *
 * 判定・依頼・通知文の一文をここ1か所に置き、画面と Worker の両方から呼ぶ。
 */
object BatteryExemption {

    /** このアプリが電池最適化の対象外か（取れなければ除外済み扱い＝余計な案内を出さない） */
    fun isExempted(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
    } catch (e: Exception) {
        true
    }

    /**
     * 「制限しない」を選ぶシステムのダイアログを直接出す（EtfBuyAlert v1.21 と同じ作り）。
     * 端末が直接のダイアログを拒否したら、電池最適化の一覧画面へ誘導する
     */
    fun request(ctx: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        )
        try {
            ctx.startActivity(direct)
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) { }
        }
    }

    /**
     * 取得失敗の通知に添える一文。未除外なら、一番ありそうな原因として除外を促す。
     * 除外済みなら空文字（従来どおりの文面）。Context を取らない純粋な関数（単体テスト用）
     */
    fun failureHint(exempted: Boolean): String =
        if (exempted) "" else
            "\n【まず確認】このアプリは電池最適化の対象のままです。端末が裏での通信を止めている可能性が高いので、" +
                "「履歴」タブの「電池最適化の除外」から除外してください。"
}
