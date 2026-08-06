package com.example.panicpilot.data

// ============================================================
// ★このファイルは自動生成。手で編集しない★
// 生成元: jquants-bulk/gen_panicpilot_us_history.py
// 生成日: 2026-08-07／データ: Yahoo Finance 実勢価格
// 定義は検証45（bt_panic_us.py）と同一: 点灯=52週DD≤-15% or 5日≤-8%、
// 局面=40営業日ルール、買いは点灯翌営業日の終値
// ============================================================

/** 米国の過去点灯局面1件分。リターンは実在ETFの実勢価格（null=当時未上場/データ不足） */
data class UsCrashEpisode(
    val date: String,          // 点灯日
    val name: String,          // 出来事の呼び名（不明は空欄）
    val conds: List<String>,   // 点灯した条件キー（dd/fast）
    val ddAt: Double,          // 点灯日の52週DD（%）
    val ret5At: Double,        // 点灯日の5日リターン（%）
    val vixAt: Double?,        // 点灯日のVIX
    val spy3m: Double?, val spy12m: Double?, val spyDd: Double?,
    val sso3m: Double?, val sso12m: Double?, val ssoDd: Double?,
    val spxl3m: Double?, val spxl12m: Double?, val spxlDd: Double?
)

object UsCrashHistoryData {
    val episodes = listOf(
        UsCrashEpisode("1990-08-23", "湾岸危機", listOf("dd"), -16.8, -7.6, 36.5,
            null, null, null,
            null, null, null,
            null, null, null),
        UsCrashEpisode("1991-01-09", "湾岸戦争", listOf("dd"), -15.6, -4.6, 33.3,
            null, null, null,
            null, null, null,
            null, null, null),
        UsCrashEpisode("1997-10-27", "アジア通貨危機", listOf("fast"), -10.8, -8.2, 31.1,
            6.5, 15.8, -2.5,
            null, null, null,
            null, null, null),
        UsCrashEpisode("1998-08-31", "ロシア危機・LTCM", listOf("dd", "fast"), -19.3, -12.0, 44.3,
            17.6, 33.6, -3.5,
            null, null, null,
            null, null, null),
        UsCrashEpisode("2000-04-14", "ITバブル崩壊", listOf("fast"), -11.2, -10.5, 33.5,
            6.4, -11.9, -21.6,
            null, null, null,
            null, null, null),
        UsCrashEpisode("2000-12-20", "ITバブル崩壊2", listOf("dd"), -17.2, -7.0, 31.7,
            -8.8, -8.8, -23.5,
            null, null, null,
            null, null, null),
        UsCrashEpisode("2008-01-18", "リーマン危機前夜", listOf("dd"), -15.3, -5.4, 27.2,
            5.5, -35.7, -42.3,
            9.6, -65.4, -71.0,
            null, null, null),
        UsCrashEpisode("2008-06-20", "リーマン危機", listOf("dd"), -15.8, -3.1, 22.9,
            -7.7, -32.0, -48.2,
            -16.4, -62.0, -77.7,
            null, null, null),
        UsCrashEpisode("2010-06-30", "欧州債務危機", listOf("dd"), -15.3, -5.6, 34.5,
            11.1, 28.4, -0.5,
            22.8, 64.9, -1.3,
            36.1, 112.9, -1.6),
        UsCrashEpisode("2011-08-08", "米国債格下げ", listOf("dd", "fast"), -17.9, -13.0, 48.0,
            7.5, 19.6, -6.4,
            13.1, 39.9, -13.4,
            15.2, 51.7, -22.9),
        UsCrashEpisode("2015-08-24", "チャイナショック", listOf("fast"), -11.2, -10.0, 40.7,
            11.6, 16.3, -2.4,
            24.6, 35.3, -4.9,
            36.8, 52.2, -9.4),
        UsCrashEpisode("2018-02-08", "VIXショック", listOf("fast"), -10.2, -8.5, 33.5,
            4.3, 4.8, -10.4,
            7.6, 6.3, -21.5,
            10.1, 4.4, -32.5),
        UsCrashEpisode("2018-12-20", "年末急落", listOf("dd"), -15.8, -6.9, 28.4,
            16.8, 33.5, -2.6,
            34.6, 73.9, -5.5,
            53.9, 119.9, -7.9),
        UsCrashEpisode("2020-02-27", "コロナショック", listOf("fast"), -12.0, -11.7, 39.2,
            2.7, 31.5, -24.7,
            -4.3, 55.2, -46.9,
            -19.4, 60.0, -65.1),
        UsCrashEpisode("2022-05-09", "利上げショック", listOf("dd"), -16.8, -4.0, 34.8,
            5.2, 3.3, -10.7,
            9.1, -0.4, -22.2,
            11.1, -8.5, -34.2),
        UsCrashEpisode("2025-04-04", "関税ショック", listOf("dd", "fast"), -17.4, -9.1, 45.3,
            23.7, 34.8, -1.6,
            48.7, 69.2, -3.1,
            76.8, 108.9, -4.6),
    )

    // SPY 12M実績: n=14 中央値+16.1% 最悪-35.7% 勝率71%
    // SSO 12M実績: n=10 中央値+37.6% 最悪-65.4% 勝率70%
    // SPXL 12M実績: n=8 中央値+56.1% 最悪-8.5% 勝率88%
    /** まとめ表示用（画面はこの定数を参照。数値の再計算はしない） */
    const val SPY_12M_N = 14; const val SPY_12M_MED = 16.1
    const val SPY_12M_WORST = -35.7; const val SPY_12M_WIN = 71
    const val SPXL_12M_N = 8; const val SPXL_12M_MED = 56.1
    const val SPXL_12M_WORST = -8.5; const val SPXL_12M_WIN = 88
}
