package com.example.panicpilot.data

// ─────────────────────────────────────────────────────────────
// 【自動生成ファイル・手で編集しない】
// 生成元: jquants-bulk/gen_panicpilot_crash_history.py
// 元データ: J-Quants 10年（生存バイアス無し）+ yfinance。検証25〜29の実測値。
//   ・点灯判定は MarketStatus と同じ3条件（52週DD≤-15% / 5日≤-8% / 騰落<70）を
//     日経平均に対して10年再現したもの
//   ・成績はすべて 1570（日経レバ）の実際の市場価格。レバETFの日次リバランス減価と
//     信託報酬は価格に織り込み済み（理論2倍換算はしていない）
//   ・「一括」＝点灯翌営業日に全額、「3分割」＝点灯翌日1/3＋日経-5%/-10%で1/3ずつ
//     （＝出動タブの既定プラン）。未投入分は現金として計算
// 数値を直したくなったら、このファイルではなく上記スクリプトを直して再生成する
// （同じ数字を2か所に持つとズレるため）
// ─────────────────────────────────────────────────────────────

/** 点灯/消灯の1区間（lit=点灯中か, days=営業日数） */
data class LitSegment(val lit: Boolean, val days: Int)

/** 過去の点灯局面1件ぶんの実績 */
data class CrashEpisode(
    val litDate: String,        // 初回点灯日
    val nickname: String,       // 通称（特定できないものは空）
    val kind: String,           // 点灯した条件
    val dd52w: Double,          // 点灯日の52週高値からの下落率(%)
    val ret5d: Double,          // 点灯日の5営業日リターン(%)
    val adr25: Double?,         // 点灯日の25日騰落レシオ
    val dimDate: String,        // 初回消灯日
    val litRunDays: Int,        // 最初の連続点灯日数
    val litCount: Int,          // 局面内で点灯した回数（チャタリング回数）
    val bottomDate: String,     // 1570が最も安かった日
    val bottomOffset: Int,      // 買い日から底までの営業日数
    val bottomPct: Double,      // 買値から底までの下落率(%)
    val r5: Double?,            // 5営業日後に売った場合(%)
    val r1m: Double?,           // 1ヶ月後(%)
    val r3m: Double?,           // 3ヶ月後(%)
    val r6m: Double?,           // 6ヶ月後(%)
    val r12m: Double?,          // 12ヶ月後(%)
    val maxDdA: Double,         // 一括買いの最大含み損(%)
    val g12m: Double?,          // 3分割の12ヶ月後(%)
    val maxDdG: Double,         // 3分割の最大含み損(%)
    val exitDate: String,       // 出口ルール（52週高値-3%回復）での売却日
    val exitDays: Int?,         // 買いから売却までの営業日数
    val exitRet: Double?,       // 出口ルールで売った場合の損益(%)
    val timeline: List<LitSegment>,  // 点灯⇔消灯の推移
    val timelineDays: Int,
) {
    /** 表示用の見出し（通称があれば添える） */
    val title: String get() = if (nickname.isBlank()) litDate else "$litDate $nickname"
}

/** 過去の点灯局面（新しい順に表示するのは画面側の役目） */
val CRASH_HISTORY: List<CrashEpisode> = listOf(

    CrashEpisode(
        litDate = "2016-08-18", nickname = "", kind = "52週-15%",
        dd52w = -20.8, ret5d = -1.5, adr25 = 90.4,
        dimDate = "2016-09-05", litRunDays = 12, litCount = 5,
        bottomDate = "2016-11-09", bottomOffset = 54, bottomPct = -2.4,
        r5 = -2.0, r1m = -0.9, r3m = 20.8,
        r6m = 35.7, r12m = 37.5,
        maxDdA = -2.4, g12m = 12.5, maxDdG = -0.8,
        exitDate = "2016-12-15", exitDays = 79,
        exitRet = 36.0,
        timeline = listOf(LitSegment(true, 12), LitSegment(false, 3), LitSegment(true, 20), LitSegment(false, 1), LitSegment(true, 6), LitSegment(false, 10), LitSegment(true, 1), LitSegment(false, 2), LitSegment(true, 1), LitSegment(false, 10)), timelineDays = 66,
    ),
    CrashEpisode(
        litDate = "2017-04-14", nickname = "", kind = "騰落<70",
        dd52w = -6.6, ret5d = -1.8, adr25 = 69.3,
        dimDate = "2017-04-18", litRunDays = 2, litCount = 1,
        bottomDate = "2017-04-17", bottomOffset = 0, bottomPct = 0.0,
        r5 = 5.7, r1m = 13.8, r3m = 18.6,
        r6m = 37.8, r12m = 47.1,
        maxDdA = 0.0, g12m = 15.7, maxDdG = 0.0,
        exitDate = "2017-04-26", exitDays = 7,
        exitRet = 10.3,
        timeline = listOf(LitSegment(true, 2), LitSegment(false, 10)), timelineDays = 12,
    ),
    CrashEpisode(
        litDate = "2018-02-09", nickname = "VIXショック", kind = "5日-8%",
        dd52w = -11.4, ret5d = -8.1, adr25 = 88.1,
        dimDate = "2018-02-13", litRunDays = 1, litCount = 1,
        bottomDate = "2018-12-25", bottomOffset = 215, bottomPct = -19.2,
        r5 = 6.4, r1m = 4.7, r3m = 16.5,
        r6m = 9.6, r12m = 0.8,
        maxDdA = -19.2, g12m = 16.0, maxDdG = -6.4,
        exitDate = "2018-09-19", exitDays = 150,
        exitRet = 24.7,
        timeline = listOf(LitSegment(true, 1), LitSegment(false, 214), LitSegment(true, 13)), timelineDays = 228,
    ),
    CrashEpisode(
        litDate = "2018-12-20", nickname = "年末急落", kind = "52週-15%",
        dd52w = -16.0, ret5d = -6.5, adr25 = 73.4,
        dimDate = "2019-01-18", litRunDays = 15, litCount = 4,
        bottomDate = "2018-12-25", bottomOffset = 1, bottomPct = -10.2,
        r5 = -6.6, r1m = 4.5, r3m = 14.4,
        r6m = 16.2, r12m = 41.5,
        maxDdA = -10.2, g12m = 31.9, maxDdG = -3.4,
        exitDate = "2019-10-29", exitDays = 202,
        exitRet = 31.1,
        timeline = listOf(LitSegment(true, 15), LitSegment(false, 2), LitSegment(true, 3), LitSegment(false, 3), LitSegment(true, 1), LitSegment(false, 6), LitSegment(true, 1), LitSegment(false, 10)), timelineDays = 41,
    ),
    CrashEpisode(
        litDate = "2019-05-31", nickname = "米中摩擦の再燃", kind = "52週-15%",
        dd52w = -15.1, ret5d = -2.4, adr25 = 79.5,
        dimDate = "2019-06-05", litRunDays = 3, litCount = 1,
        bottomDate = "2020-03-19", bottomOffset = 194, bottomPct = -35.8,
        r5 = 7.3, r1m = 13.7, r3m = 1.9,
        r6m = 31.5, r12m = 17.8,
        maxDdA = -35.8, g12m = 46.1, maxDdG = -20.3,
        exitDate = "2019-10-29", exitDays = 100,
        exitRet = 28.0,
        timeline = listOf(LitSegment(true, 3), LitSegment(false, 43), LitSegment(true, 3), LitSegment(false, 1), LitSegment(true, 1), LitSegment(false, 1), LitSegment(true, 3), LitSegment(false, 1), LitSegment(true, 2), LitSegment(false, 1), LitSegment(true, 4), LitSegment(false, 1), LitSegment(true, 2), LitSegment(false, 113), LitSegment(true, 27)), timelineDays = 206,
    ),
    CrashEpisode(
        litDate = "2019-08-06", nickname = "米中摩擦の激化", kind = "52週-15%",
        dd52w = -15.2, ret5d = -5.2, adr25 = 86.5,
        dimDate = "2019-08-09", litRunDays = 3, litCount = 6,
        bottomDate = "2020-03-19", bottomOffset = 148, bottomPct = -36.3,
        r5 = -1.1, r1m = 6.6, r3m = 33.1,
        r6m = 32.4, r12m = 20.7,
        maxDdA = -36.3, g12m = 50.6, maxDdG = -20.5,
        exitDate = "2019-10-29", exitDays = 54,
        exitRet = 26.9,
        timeline = listOf(LitSegment(true, 3), LitSegment(false, 1), LitSegment(true, 1), LitSegment(false, 1), LitSegment(true, 3), LitSegment(false, 1), LitSegment(true, 2), LitSegment(false, 1), LitSegment(true, 4), LitSegment(false, 1), LitSegment(true, 2), LitSegment(false, 113), LitSegment(true, 27)), timelineDays = 160,
    ),
    CrashEpisode(
        litDate = "2020-02-26", nickname = "コロナショック", kind = "騰落<70",
        dd52w = -6.9, ret5d = -3.3, adr25 = 67.2,
        dimDate = "2020-05-20", litRunDays = 55, litCount = 2,
        bottomDate = "2020-03-19", bottomOffset = 15, bottomPct = -44.7,
        r5 = -5.8, r1m = -26.4, r3m = 0.0,
        r6m = 7.3, r12m = 69.3,
        maxDdA = -44.7, g12m = 86.0, maxDdG = -39.2,
        exitDate = "2020-09-04", exitDays = 128,
        exitRet = 6.8,
        timeline = listOf(LitSegment(true, 55), LitSegment(false, 2), LitSegment(true, 1), LitSegment(false, 10)), timelineDays = 68,
    ),
    CrashEpisode(
        litDate = "2022-02-24", nickname = "ウクライナ侵攻", kind = "52週-15%",
        dd52w = -15.3, ret5d = -5.4, adr25 = 89.1,
        dimDate = "2022-02-25", litRunDays = 1, litCount = 5,
        bottomDate = "2022-03-09", bottomOffset = 8, bottomPct = -13.2,
        r5 = -3.7, r1m = 13.0, r3m = 8.0,
        r6m = 12.2, r12m = 15.2,
        maxDdA = -13.2, g12m = 5.1, maxDdG = -4.4,
        exitDate = "2023-03-09", exitDays = 253,
        exitRet = 16.7,
        timeline = listOf(LitSegment(true, 1), LitSegment(false, 5), LitSegment(true, 9), LitSegment(false, 35), LitSegment(true, 1), LitSegment(false, 25), LitSegment(true, 2), LitSegment(false, 8), LitSegment(true, 1), LitSegment(false, 10)), timelineDays = 97,
    ),
    CrashEpisode(
        litDate = "2024-08-05", nickname = "円高ショック", kind = "52週-15%+5日-8%",
        dd52w = -25.5, ret5d = -18.2, adr25 = 75.0,
        dimDate = "2024-08-13", litRunDays = 5, litCount = 2,
        bottomDate = "2025-04-07", bottomOffset = 161, bottomPct = -18.0,
        r5 = 12.8, r1m = 13.6, r3m = 31.7,
        r6m = 30.7, r12m = 54.8,
        maxDdA = -18.0, g12m = 18.3, maxDdG = -6.0,
        exitDate = "2025-07-24", exitDays = 235,
        exitRet = 43.4,
        timeline = listOf(LitSegment(true, 5), LitSegment(false, 21), LitSegment(true, 1), LitSegment(false, 130), LitSegment(true, 16)), timelineDays = 173,
    ),
    CrashEpisode(
        litDate = "2025-03-31", nickname = "関税ショック", kind = "52週-15%",
        dd52w = -15.6, ret5d = -5.3, adr25 = 101.6,
        dimDate = "2025-04-30", litRunDays = 21, litCount = 1,
        bottomDate = "2025-04-07", bottomOffset = 4, bottomPct = -23.7,
        r5 = -15.3, r1m = 2.1, r3m = 20.9,
        r6m = 61.0, r12m = 135.7,
        maxDdA = -23.7, g12m = 174.3, maxDdG = -8.9,
        exitDate = "2025-07-24", exitDays = 78,
        exitRet = 33.6,
        timeline = listOf(LitSegment(true, 21), LitSegment(false, 10)), timelineDays = 31,
    ),
    CrashEpisode(
        litDate = "2026-03-09", nickname = "", kind = "5日-8%",
        dd52w = -10.4, ret5d = -9.2, adr25 = 108.8,
        dimDate = "2026-03-10", litRunDays = 1, litCount = 1,
        bottomDate = "2026-03-31", bottomOffset = 14, bottomPct = -10.4,
        r5 = -2.0, r1m = 6.4, r3m = 45.1,
        r6m = null, r12m = null,
        maxDdA = -10.4, g12m = null, maxDdG = -3.5,
        exitDate = "2026-04-15", exitDays = 25,
        exitRet = 14.6,
        timeline = listOf(LitSegment(true, 1), LitSegment(false, 25)), timelineDays = 26,
    ),
    CrashEpisode(
        litDate = "2026-07-29", nickname = "", kind = "52週-15%",
        dd52w = -15.1, ret5d = -7.1, adr25 = 109.9,
        dimDate = "2026-07-30", litRunDays = 1, litCount = 1,
        bottomDate = "2026-07-30", bottomOffset = 0, bottomPct = 0.0,
        r5 = null, r1m = null, r3m = null,
        r6m = null, r12m = null,
        maxDdA = 0.0, g12m = null, maxDdG = 0.0,
        exitDate = "", exitDays = null,
        exitRet = null,
        timeline = listOf(LitSegment(true, 1), LitSegment(false, 2)), timelineDays = 3,
    ),
)
