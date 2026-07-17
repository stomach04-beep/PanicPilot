package com.example.panicpilot.data

// ─── 市場データのスナップショット（daily2year.json から計算した結果） ───
// 基準指数は日経平均（daily2year.json にTOPIX列は無い・1570も日経連動なので一貫）
data class MarketStatus(
    val dataDate: String,        // データの最終日 yyyy-MM-dd
    val fetchedAt: String,       // 取得時刻 yyyy-MM-dd HH:mm
    val indexLast: Double,       // 日経平均 終値
    val adr25: Double,           // 25日騰落レシオ
    val high52w: Double,         // 日経平均 52週高値（直近252営業日の最大）
    val dd52w: Double,           // 52週高値からの下落率（例 -0.18 = -18%）
    val ret5d: Double,           // 5営業日リターン（例 -0.09 = -9%）
    val lev1570: Double?,        // 日経レバ1570の終値（取得失敗時 null）
    // スパークライン用に直近60営業日の日経平均を保持
    val indexRecent: List<Double> = emptyList(),
    // 推移タブ用に直近約1年（252営業日）の3指標の日次系列を保持
    val history: List<HistoryPoint> = emptyList()
) {
    // ─── 点灯判定（J-Quants 10年バックテスト検証済みの3条件） ───
    // しきい値は下の companion に一元化し、ここも推移画面も同じ定数を参照する（DRY）
    val sigDd: Boolean get() = dd52w <= TH_DD          // 検証2: DD≤-15%（1M+4.5% 勝率89%）
    val sigFast: Boolean get() = ret5d <= TH_FAST      // 検証18: 5日で-8%急落（3M/12M全勝）
    val sigAdr: Boolean get() = adr25 < TH_ADR_DEEP    // 検証1: 騰落レシオ<70（全勝）
    val sigShallow: Boolean get() = adr25 < TH_ADR_SHALLOW  // 浅い点灯（30-40日待って二番底）
    val deep: Boolean get() = sigDd || sigFast || sigAdr

    // ─── 点灯しきい値（単一の真実の源。シグナル判定・推移グラフの両方がここを見る） ───
    companion object {
        const val TH_DD = -0.15          // 52週高値からの下落率 点灯ライン
        const val TH_FAST = -0.08        // 5営業日リターン 点灯ライン
        const val TH_ADR_DEEP = 70.0     // 25日騰落レシオ 深い点灯ライン
        const val TH_ADR_SHALLOW = 80.0  // 25日騰落レシオ 注意ライン
    }
}

// ─── 過去推移の1日分（3指標の値と、その日が点灯していたか） ───
// 値は daily2year.json から日ごとに再計算したもの。点灯判定は MarketStatus と同じ定数を参照
data class HistoryPoint(
    val date: String,        // yyyy-MM-dd
    val dd52w: Double,       // その日の52週高値からの下落率
    val ret5d: Double,       // その日の5営業日リターン（NaN=算出不可）
    val adr25: Double        // その日の25日騰落レシオ（NaN=欠損）
) {
    val litDd: Boolean get() = dd52w <= MarketStatus.TH_DD
    val litFast: Boolean get() = !ret5d.isNaN() && ret5d <= MarketStatus.TH_FAST
    val litAdr: Boolean get() = !adr25.isNaN() && adr25 < MarketStatus.TH_ADR_DEEP
}

// ─── 出動ポジションの記録 ───
data class Position(
    val entryDate: String,       // 1回目を入れた日
    val baseIndex: Double,       // 買い増しトリガーの基準となる日経平均（1回目時点）
    val budgetYen: Long,         // 出動予算（円）
    val fill2Done: Boolean = false,   // 2回目（-5%）投入済みか
    val fill3Done: Boolean = false    // 3回目（-10%）投入済みか
) {
    val trigger2: Double get() = baseIndex * 0.95   // 2回目の買い増し水準
    val trigger3: Double get() = baseIndex * 0.90   // 3回目の買い増し水準
    val trancheYen: Long get() = budgetYen / 3      // 1回あたりの投入額
}
