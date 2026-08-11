package com.example.panicpilot.data

/**
 * TOPIX（1306）E60はしごの状態（検証48: bt_1306_crash_buying.py）。
 * 日経レバ用の既存3条件（MarketStatus）とは完全に別枠のシグナル。
 *
 * ルール（E60・検証48で採用）:
 *  - 1306が52週高値-10%を割ったら「60営業日の時計」をスタート（この時点では買わない）
 *  - -15% / -20% / -25% で予算の各1/3を買う
 *  - 時計スタートから60営業日たったら未投入分を全額投入（タイムアウト）
 *  - 52週高値-3%以内へ回復したらエピソード終了（次の-10%割れで新しい時計が始まる）
 *
 * エピソード状態は保存値でなく「取得した日次系列から毎回導出」する。
 * 保存カウンタを日々進める方式だと取得漏れの日があるとズレるが、
 * 系列から数え直せば常に正しい営業日数になる（状態ドリフトしない設計）。
 */
data class TopixLadderStatus(
    val dataDate: String,          // 1306データの最終日 yyyy-MM-dd
    val fetchedAt: String,         // 取得時刻
    val close: Double,             // 1306 終値
    val high52w: Double,           // 1306 52週高値（直近252営業日の最大）
    val dd52w: Double,             // 52週高値からの下落率
    // ─── エピソード状態（日次系列から導出） ───
    val clockStartDate: String?,   // -10%を割った日（null=平常＝時計は動いていない）
    val elapsedBiz: Int,           // 割った日からの経過営業日数（割った日=0）
    val minDdSinceClock: Double    // 割った日から現在までの最深DD（段の到達判定に使う）
) {
    // ─── 4本のライン（1306の円建て。52週高値が動けば自動で追従する） ───
    val lineClock: Double get() = high52w * (1 + TH_CLOCK)   // 時計スタート
    val lineRung1: Double get() = high52w * (1 + TH_RUNG1)   // 1段目 1/3
    val lineRung2: Double get() = high52w * (1 + TH_RUNG2)   // 2段目 1/3
    val lineRung3: Double get() = high52w * (1 + TH_RUNG3)   // 3段目 1/3

    // ─── 段の到達判定（エピソード内の最深DDで見る＝一度到達したら回復しても到達済み） ───
    val clockRunning: Boolean get() = clockStartDate != null
    val rung1Hit: Boolean get() = clockRunning && minDdSinceClock <= TH_RUNG1
    val rung2Hit: Boolean get() = clockRunning && minDdSinceClock <= TH_RUNG2
    val rung3Hit: Boolean get() = clockRunning && minDdSinceClock <= TH_RUNG3
    /** 60営業日経過＝未投入分を全額投入するタイミング */
    val timedOut: Boolean get() = clockRunning && elapsedBiz >= TIMEOUT_BIZ

    companion object {
        // しきい値（単一の真実の源。画面・通知・判定すべてここを参照する）
        const val TH_CLOCK = -0.10   // 時計スタート（買わない）
        const val TH_RUNG1 = -0.15   // 1段目 1/3
        const val TH_RUNG2 = -0.20   // 2段目 1/3
        const val TH_RUNG3 = -0.25   // 3段目 1/3
        const val TH_RESET = -0.03   // 52週高値-3%以内へ回復＝エピソード終了
        const val TIMEOUT_BIZ = 60   // タイムアウト（営業日）

        /**
         * 日次系列（日付昇順の 日付＋その日のDD）からエピソード状態を導出する。
         * バックテスト（検証48 find_episodes）と同じ状態機械:
         *  -3%以内へ回復して「武装」→ -10%割れで時計スタート → 回復で解除
         * 戻り値: Triple(時計スタート日, 経過営業日数, 開始以降の最深DD)。平常なら (null, 0, NaN)
         */
        fun deriveEpisode(days: List<Pair<String, Double>>): Triple<String?, Int, Double> {
            var armed = false
            var startIdx = -1
            for (i in days.indices) {
                val dd = days[i].second
                if (startIdx < 0) {
                    // エピソード外: 回復水準で武装し、-10%割れで時計スタート
                    if (dd >= TH_RESET) armed = true
                    else if (armed && dd <= TH_CLOCK) startIdx = i
                } else {
                    // エピソード中: -3%以内へ回復したら終了して再武装
                    if (dd >= TH_RESET) {
                        startIdx = -1
                        armed = true
                    }
                }
            }
            if (startIdx < 0) return Triple(null, 0, Double.NaN)
            val minDd = days.subList(startIdx, days.size).minOf { it.second }
            return Triple(days[startIdx].first, days.size - 1 - startIdx, minDd)
        }
    }
}
