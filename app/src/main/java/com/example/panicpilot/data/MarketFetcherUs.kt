package com.example.panicpilot.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * 米国市場データの取得（Yahoo Finance chart API）。
 * - メイン: ^GSPC（S&P500）2年日足 → 52週DD・5日率を計算
 * - サブ: ^VIX（最後の有効終値）・SPXL・USDJPY（失敗しても続行 fail-soft）
 *
 * 日付の罠: タイムスタンプをJSTで日付にすると米国の金曜終値が土曜扱いになり1日ズレる。
 * 必ず米国東部時間（America/New_York）で日付化する（既知の教訓）
 */
object MarketFetcherUs {

    // ^ はURLでは %5E にエスケープする
    private const val GSPC_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%5EGSPC?range=2y&interval=1d"
    // VIXは推移タブ（約1年分の日次系列）にも使うので range=1y で取る
    private const val VIX_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%5EVIX?range=1y&interval=1d"
    private const val SPXL_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/SPXL?range=5d&interval=1d"
    private const val FX_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/JPY=X?range=5d&interval=1d"
    /**
     * chart APIレスポンスから (タイムスタンプ秒, 終値) の列を取り出す。
     * 指数なので生の close を使う。取得の再試行と末尾null行の補完は YahooChart に集約（v2.3.4）
     */
    private fun parseCloses(body: String): List<Pair<Long, Double>> =
        YahooChart.parseCloses(body, preferAdj = false)

    private fun httpGet(url: String): String = YahooChart.httpGet(url)

    /** 単一銘柄の直近終値（meta.regularMarketPrice。失敗したら null で続行） */
    private fun fetchLastPrice(url: String): Double? = try {
        val meta = JSONObject(httpGet(url)).getJSONObject("chart")
            .getJSONArray("result").getJSONObject(0)
            .getJSONObject("meta")
        meta.optDouble("regularMarketPrice").takeIf { !it.isNaN() && it > 0 }
    } catch (e: Exception) {
        null
    }

    /**
     * S&P500の2年日足からスナップショットを作る。
     * 計算定義は日本版 MarketFetcher.fetch() と同じ（52週=直近252営業日、5日率=pct_change(5)）
     */
    fun fetch(): UsMarketStatus {
        val rows = parseCloses(httpGet(GSPC_URL)).sortedBy { it.first }
        require(rows.size >= 260) { "S&P500の行数不足: ${rows.size}行（ソース障害の可能性）" }

        val idxAll = rows.map { it.second }
        val last = rows.last()
        val n = idxAll.size
        val win252 = idxAll.subList(maxOf(0, n - 252), n)
        val high52w = win252.max()
        val dd = last.second / high52w - 1.0
        val ret5d = if (n > 5) last.second / idxAll[n - 6] - 1.0 else 0.0

        // VIX（約1年分）。当日欄が未確定のことがあるので最新値は最後の有効値
        val vixRows = try {
            parseCloses(httpGet(VIX_URL)).sortedBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
        val vixLast = vixRows.lastOrNull()?.second ?: Double.NaN

        // 日付は米国東部時間で（JSTだと金曜終値が土曜に化ける既知の罠）
        val et = TimeZone.getTimeZone("America/New_York")
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = et }
        val jst = TimeZone.getTimeZone("Asia/Tokyo")
        val minFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN).apply { timeZone = jst }

        // ─── 推移タブ用: 直近252営業日の指標を日ごとに再計算（日本版と同じ方式） ───
        // VIXは日付文字列で突き合わせる（^GSPCと^VIXで営業日が微妙にズレることがあるため）
        val vixByDate = vixRows.associate { dayFmt.format(Date(it.first * 1000L)) to it.second }
        val histFrom = maxOf(0, n - 252)
        val history = ArrayList<UsHistoryPoint>(n - histFrom)
        for (i in histFrom until n) {
            val winStart = maxOf(0, i - 251)
            val hiI = idxAll.subList(winStart, i + 1).max()
            val dateI = dayFmt.format(Date(rows[i].first * 1000L))
            history.add(
                UsHistoryPoint(
                    date = dateI,
                    dd52w = idxAll[i] / hiI - 1.0,
                    ret5d = if (i >= 5) idxAll[i] / idxAll[i - 5] - 1.0 else Double.NaN,
                    vix = vixByDate[dateI] ?: Double.NaN
                )
            )
        }

        return UsMarketStatus(
            dataDate = dayFmt.format(Date(last.first * 1000L)),
            fetchedAt = minFmt.format(Date()),
            indexLast = last.second,
            vix = vixLast,
            high52w = high52w,
            dd52w = dd,
            ret5d = ret5d,
            spxl = fetchLastPrice(SPXL_URL),
            usdJpy = fetchLastPrice(FX_URL),
            indexRecent = idxAll.subList(maxOf(0, n - 60), n),
            history = history
        )
    }
}
