package com.example.panicpilot.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 金（ゴールド）スリーブの状態と実行計画。
 *
 * 【重要な設計判断】このタブには「点灯シグナル」を置かない。
 * 2026-08-12 の検証（gold-etf-backtest/bt_spot_rules_broad.py ほか）で、
 * 金のスポット買いタイミングを計るルールを24本総当たりしたが、採用に値するものが無かった:
 *   - 52週高値-20%以上の下落は独立エピソードが1〜2回しかなく判断材料にならない
 *   - 52週高値-10%/-15%割れは「上乗せ」がマイナス（むしろ買わないほうがよい）
 *   - 最有力だった日足RSI<30（13エピソード全勝・5年上乗せ+19.4pt）も
 *     ①ドル建てでは-14.2ptと符号が逆転 ②期間後半で+4.9ptに減衰
 *     ③隣接しきい値(RSI<32)で符号が変わる ④プラセボ検定でランダムの上位24%
 *     ＝偶然の範囲として棄却
 * よってこのタブは「いつ買うかを当てる道具」ではなく、
 * 決めた配分どおりに淡々と実行するための【執行ナビ】として作る。
 */

/** 金ETFの現在値スナップショット（判断材料であって売買シグナルではない） */
data class GoldStatus(
    val dataDate: String,
    val fetchedAt: String,
    val price: Double,          // 314A の終値（円）
    val high52w: Double,
    val dd52w: Double,          // 52週高値からの下落率（参考表示のみ）
    val rsi14: Double           // 日足RSI（参考表示のみ。買いシグナルには使わない＝検証で棄却）
) {
    companion object {
        /** 買い付け対象。2026-08-12 の実測で選定（GoldEvidenceScreen に根拠） */
        const val TICKER = "314A"
        const val TICKER_NAME = "iシェアーズ ゴールドETF"
    }
}

/**
 * 金スリーブの実行計画。
 * 検証（gold-etf-backtest）の結論をそのまま定数化している:
 *   - 目標はリスク資産の5〜10%（10%で最大DD -63.3%→-59.0%、CAGRは-0.30pt）
 *   - 建てるときは6〜12か月の分割（一括は元本割れ率2.8%、12か月分割は0.4%、24か月は逆効果）
 *   - 完成後はバンド±25%を外れたら戻す（26年で取引13〜23回と最少でCAGR最良）
 *   - 積立期は売らずに新規資金だけで戻す（売却益税20.315%を出さない）
 */
data class GoldPlan(
    val startDate: String,       // 分割開始日 yyyy-MM-dd
    val targetYen: Long,         // 金スリーブの目標額
    val months: Int,             // 分割回数（6〜12）
    val doneCount: Int = 0,      // 実行済み回数
    val investedYen: Long = 0,   // 実際に投じた累計額
    val riskAssetYen: Long = 0,  // リスク資産の総額（配分判定用・手入力）
    val holdingYen: Long = 0     // いまの金保有額（手入力）
) {
    /** 1回あたりの購入額 */
    val perBuyYen: Long get() = if (months > 0) targetYen / months else 0L

    /** 残り回数 */
    val remaining: Int get() = (months - doneCount).coerceAtLeast(0)

    /** 分割は完了したか */
    val isComplete: Boolean get() = doneCount >= months

    /** 進捗（0.0〜1.0） */
    val progress: Float get() = if (months > 0) (doneCount.toFloat() / months).coerceIn(0f, 1f) else 0f

    /** 次回の購入予定日（開始日から doneCount か月後）。完了後は null */
    fun nextBuyDate(): String? {
        if (isComplete) return null
        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"))
            cal.time = DATE_FMT.parse(startDate) ?: return null
            cal.add(Calendar.MONTH, doneCount)
            DATE_FMT.format(cal.time)
        } catch (e: Exception) {
            null
        }
    }

    /** 次回の購入が予定日を過ぎているか（過ぎていれば「今日やること」として出す） */
    fun isDue(today: String): Boolean {
        val d = nextBuyDate() ?: return false
        return today >= d
    }

    /** いまの金比率（リスク資産に対する割合）。分母が0なら NaN */
    fun currentRatio(): Double =
        if (riskAssetYen > 0) holdingYen.toDouble() / riskAssetYen else Double.NaN

    /** バンド判定。-1=下限割れ（買い増し） 0=バンド内 +1=上限超え（増やさない） */
    fun bandState(): Int {
        val r = currentRatio()
        if (r.isNaN()) return 0
        return when {
            r < TARGET_RATIO * (1 - BAND) -> -1
            r > TARGET_RATIO * (1 + BAND) -> 1
            else -> 0
        }
    }

    /** 目標比率に戻すために必要な追加額（プラスなら買い増し・マイナスなら超過） */
    fun gapToTargetYen(): Long =
        if (riskAssetYen > 0) (riskAssetYen * TARGET_RATIO).toLong() - holdingYen else 0L

    companion object {
        /** 目標比率＝リスク資産の10%（5〜10%が推奨レンジの上限側） */
        const val TARGET_RATIO = 0.10

        /** リバランスのバンド幅。目標の±25%（10%なら7.5%〜12.5%）を外れたら戻す */
        const val BAND = 0.25

        /** 分割の推奨回数レンジ（24か月まで延ばすと中央値も元本割れ率も悪化する） */
        const val MONTHS_MIN = 6
        const val MONTHS_MAX = 12

        val DATE_FMT: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            }

        fun todayJst(): String = DATE_FMT.format(Date())

        /** バンドの下限・上限（表示用） */
        val bandLower: Double get() = TARGET_RATIO * (1 - BAND)
        val bandUpper: Double get() = TARGET_RATIO * (1 + BAND)
    }
}
