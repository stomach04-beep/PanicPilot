package com.example.panicpilot.data

// ─── 市場データのスナップショット（daily2year.json から計算した結果） ───
// 基準指数は日経平均（daily2year.json にTOPIX列は無い・1458も日経連動なので一貫）
data class MarketStatus(
    val dataDate: String,        // データの最終日 yyyy-MM-dd
    val fetchedAt: String,       // 取得時刻 yyyy-MM-dd HH:mm
    val indexLast: Double,       // 日経平均 終値
    val adr25: Double,           // 25日騰落レシオ
    val nikkeiVi: Double,        // 日経VI（恐怖指数）。欠損時はNaN
    val high52w: Double,         // 日経平均 52週高値（直近252営業日の最大）
    val dd52w: Double,           // 52週高値からの下落率（例 -0.18 = -18%）
    val ret5d: Double,           // 5営業日リターン（例 -0.09 = -9%）
    // v1.8で1570→1458へ変更（同じ日経レバレッジ指数連動で信託報酬0.385% vs 0.88%。
    // 検証39補足: 全10局面で1458が勝ち・10年CAGR+0.79pt/年）
    val lev1458: Double?,        // 楽天日経レバ1458の終値（取得失敗時 null）
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

    // ─── 撤退判定（検証32〜36で追加。損切りが無いのが唯一の致命的な弱点だった） ───
    // 指数ベースで見るのがポイント。レバの含み損で切ると通常の暴落で狩られる（検証33）
    val sigRetreat: Boolean get() = dd52w <= TH_RETREAT   // 52週高値-35%割れ＝構造的弱気相場
    val retreatLine: Double get() = high52w * (1 + TH_RETREAT)  // 撤退する日経平均の水準
    val exitLine: Double get() = high52w * (1 + TH_EXIT)        // 利確する日経平均の水準
    val recovered: Boolean get() = dd52w >= TH_EXIT       // 52週高値-3%まで回復＝出口/ロック解除

    // ─── 確信度（検証33）───
    // 日経VI≥30 が重なった点灯は買値が底に近く（底より+18.6%高い→+9.9%）12M成績も
    // +44.0%→+69.6%と良い。ただし2016-2026のn=8（12M計測はn=6）と標本が少ないので、
    // 「出動するかどうか」は変えず、金額の厚みだけを変える材料に使う
    val viHigh: Boolean get() = !nikkeiVi.isNaN() && nikkeiVi >= TH_VI
    /** 出動予算に対する推奨投入率。確信度が高い局面だけ満額 */
    val confidenceRatio: Double get() = if (viHigh) 1.0 else 0.5
    val confidenceLabel: String get() = when {
        nikkeiVi.isNaN() -> "確信度 不明（日経VI未取得）"
        viHigh -> "確信度 高（日経VI ${"%.1f".format(nikkeiVi)} ≥ ${TH_VI.toInt()}）"
        else -> "確信度 標準（日経VI ${"%.1f".format(nikkeiVi)} < ${TH_VI.toInt()}）"
    }

    // ─── 総合の点灯レベル（消灯判定の単一の真実の源） ───
    // 通知（点灯・消灯）はこのレベルの変化だけを見る。ここ以外で強弱を再定義しない
    val level: LitLevel
        get() = when {
            deep -> LitLevel.DEEP
            sigShallow -> LitLevel.SHALLOW
            else -> LitLevel.CALM
        }

    /** いま点灯している条件のキー一覧（消灯時に「何が消えたか」を書くために使う） */
    val litSignalKeys: Set<String>
        get() = buildSet {
            if (sigDd) add(SIG_DD)
            if (sigFast) add(SIG_FAST)
            if (sigAdr) add(SIG_ADR)
        }

    // ─── 点灯しきい値（単一の真実の源。シグナル判定・推移グラフの両方がここを見る） ───
    companion object {
        const val TH_DD = -0.15          // 52週高値からの下落率 点灯ライン
        const val TH_FAST = -0.08        // 5営業日リターン 点灯ライン
        const val TH_ADR_DEEP = 70.0     // 25日騰落レシオ 深い点灯ライン
        const val TH_ADR_SHALLOW = 80.0  // 25日騰落レシオ 注意ライン

        // 出口と撤退のライン（従来ここが直書きだったのを一元化）
        const val TH_EXIT = -0.03        // 52週高値-3%まで回復＝利確／撤退ロック解除
        const val TH_RETREAT = -0.35     // 52週高値-35%割れ＝撤退（損切り）
        const val TH_VI = 30.0           // 日経VI 確信度ライン（検証23・33）

        // 点灯条件のキー（保存ファイル・通知文の両方でこの文字列を使う）
        const val SIG_DD = "dd"       // 52週高値からの下落率
        const val SIG_FAST = "fast"   // 5営業日の急落
        const val SIG_ADR = "adr"     // 25日騰落レシオ

        /** 条件キー → 日本語の呼び名（通知文用） */
        fun signalLabel(key: String): String = when (key) {
            SIG_DD -> "52週高値からの下落"
            SIG_FAST -> "5日間の急落"
            SIG_ADR -> "騰落レシオ<70"
            else -> key
        }
    }
}

/** 総合の点灯レベル。DEEP（出動）> SHALLOW（浅い点灯）> CALM（平常） */
enum class LitLevel(val rank: Int) {
    CALM(0), SHALLOW(1), DEEP(2);

    companion object {
        /** 保存文字列から復元（未知の値・null は平常扱い） */
        fun fromName(name: String?): LitLevel =
            entries.firstOrNull { it.name == name } ?: CALM
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
