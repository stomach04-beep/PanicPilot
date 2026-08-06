package com.example.panicpilot.data

// ─── 米国市場のスナップショット（Yahoo Finance ^GSPC / ^VIX から計算した結果） ───
// 基準指数はS&P500。日本版と違い騰落レシオの無料日次ソースが米国には無いため、
// 点灯条件は「52週DD」「5日急落」の2本（検証45: この2本で12M超過+24.8pt・勝率88% SPXL）。
// VIXは点灯条件に入れない（検証45: VIXをORで足すと超過が悪化）。確信度＝金額の厚みだけに使う
data class UsMarketStatus(
    val dataDate: String,        // データの最終日 yyyy-MM-dd（米国東部時間の日付）
    val fetchedAt: String,       // 取得時刻 yyyy-MM-dd HH:mm（JST）
    val indexLast: Double,       // S&P500 終値
    val vix: Double,             // VIX（恐怖指数）。欠損時はNaN
    val high52w: Double,         // S&P500 52週高値（直近252営業日の最大）
    val dd52w: Double,           // 52週高値からの下落率（例 -0.18 = -18%）
    val ret5d: Double,           // 5営業日リターン
    val spxl: Double?,           // SPXL（S&P500ブル3倍）の終値USD（取得失敗時 null）
    val usdJpy: Double?,         // ドル円（円建て口数計算の目安用。取得失敗時 null）
    // スパークライン・推移表示用に直近60営業日のS&P500を保持
    val indexRecent: List<Double> = emptyList(),
    // 推移タブ用に直近約1年（252営業日）の指標の日次系列を保持（v2.1）
    val history: List<UsHistoryPoint> = emptyList()
) {
    // ─── 点灯判定（検証45: 日本版と同じしきい値が米国でも機能） ───
    val sigDd: Boolean get() = dd52w <= TH_DD          // 52週高値-15%
    val sigFast: Boolean get() = ret5d <= TH_FAST      // 5日で-8%急落
    val deep: Boolean get() = sigDd || sigFast          // 米国版に「浅い点灯」は無い

    // ─── 撤退判定（検証32: 指数-35%撤退線は日米で独立に最良） ───
    val sigRetreat: Boolean get() = dd52w <= TH_RETREAT
    val retreatLine: Double get() = high52w * (1 + TH_RETREAT)
    val exitLine: Double get() = high52w * (1 + TH_EXIT)
    val recovered: Boolean get() = dd52w >= TH_EXIT

    // ─── 確信度（検証46 Part2） ───
    // 点灯日VIX≥30は12M平均+34.9%勝率85%、VIX<30は-17.8%勝率33%。
    // 2008年の2大災害（VIX27/23）は両方VIX<30＝日本版と同じ「半分」設計が米国では防御としても効く
    val vixHigh: Boolean get() = !vix.isNaN() && vix >= TH_VIX
    /** 出動予算に対する推奨投入率。VIXが跳ねた局面だけ満額 */
    val confidenceRatio: Double get() = if (vixHigh) 1.0 else 0.5
    val confidenceLabel: String get() = when {
        vix.isNaN() -> "確信度 不明（VIX未取得）"
        vixHigh -> "確信度 高（VIX ${"%.1f".format(vix)} ≥ ${TH_VIX.toInt()}）"
        else -> "確信度 標準（VIX ${"%.1f".format(vix)} < ${TH_VIX.toInt()}）"
    }

    // ─── 総合の点灯レベル（消灯判定の単一の真実の源。SHALLOWは使わない） ───
    val level: LitLevel get() = if (deep) LitLevel.DEEP else LitLevel.CALM

    /** いま点灯している条件のキー一覧（消灯通知の「何が消えたか」用） */
    val litSignalKeys: Set<String>
        get() = buildSet {
            if (sigDd) add(SIG_DD)
            if (sigFast) add(SIG_FAST)
        }

    // ─── しきい値（単一の真実の源。日本版 MarketStatus と同じ値だが独立に定義） ───
    companion object {
        const val TH_DD = -0.15          // 52週高値からの下落率 点灯ライン
        const val TH_FAST = -0.08        // 5営業日リターン 点灯ライン
        const val TH_EXIT = -0.03        // 52週高値-3%まで回復＝利確／撤退ロック解除
        const val TH_RETREAT = -0.35     // 52週高値-35%割れ＝撤退（検証46 Part3:
                                         //   合成3倍の最悪-84.6%→-63.4%、平均+38.8%→+48.4%）
        const val TH_VIX = 30.0          // VIX 確信度ライン（検証46 Part2）

        const val SIG_DD = "dd"
        const val SIG_FAST = "fast"

        /** 条件キー → 日本語の呼び名（通知文用） */
        fun signalLabel(key: String): String = when (key) {
            SIG_DD -> "52週高値からの下落"
            SIG_FAST -> "5日間の急落"
            else -> key
        }
    }
}

// ─── 米国の過去推移の1日分（日本版 HistoryPoint の米国版。点灯判定は同じ定数を参照） ───
data class UsHistoryPoint(
    val date: String,        // yyyy-MM-dd（米国東部）
    val dd52w: Double,       // その日の52週高値からの下落率
    val ret5d: Double,       // その日の5営業日リターン（NaN=算出不可）
    val vix: Double          // その日のVIX終値（NaN=欠損）
) {
    val litDd: Boolean get() = dd52w <= UsMarketStatus.TH_DD
    val litFast: Boolean get() = !ret5d.isNaN() && ret5d <= UsMarketStatus.TH_FAST
}
