package com.example.panicpilot.data

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * 市場データの取得。
 * - メイン: nikkei225jp.com の daily2year.json（約508営業日 × 35カラム）
 *   1リクエストで 日経平均[1]・25日騰落レシオ[7] が取れる
 *   （BargainChecker で実績のあるソース。**Refererヘッダ必須**＝無いと404。
 *    実データ検証済み: TOPIX列は存在しないため基準指数は日経平均を使う）
 * - サブ: Yahoo Finance chart API で日経レバ1570の終値（失敗しても続行）
 */
object MarketFetcher {

    private const val DAILY_URL =
        "https://nikkei225jp.com/_data/_nfsDATA/DAY/daily2year.json"
    private const val REFERER = "https://nikkei225jp.com/data/karauri.php"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val YAHOO_1570_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/1570.T?range=5d&interval=1d"

    /** HTTP GET（タイムアウト・Referer付き） */
    private fun httpGet(url: String, referer: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        referer?.let { conn.setRequestProperty("Referer", it) }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode}: $url")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * daily2year.json をパースして MarketStatus を作る。
     * 形式: var DAILY = [\n[ミリ秒,日経平均,...,騰落レシオ,...],\n[...]...];
     * 改行・空白が混ざるので除去してから indexOf でパース（正規表現は使わない）
     */
    fun fetch(): MarketStatus {
        val body = httpGet(DAILY_URL, REFERER)
        // 改行・空白を除去して「[[行],[行]]」の形に正規化
        val compact = buildString(body.length) {
            for (c in body) if (c != '\n' && c != '\r' && c != ' ' && c != '\t') append(c)
        }
        val start = compact.indexOf("[[")
        val end = compact.lastIndexOf("]]")
        require(start >= 0 && end > start) { "DAILY配列が見つからない（形式変更の可能性）" }
        val inner = compact.substring(start + 2, end)

        data class Row(val timeMs: Long, val nikkei: Double, val adr: Double)
        val rows = ArrayList<Row>(520)
        var pos = 0
        while (pos < inner.length) {
            val next = inner.indexOf("],[", pos)
            val rowStr = if (next >= 0) inner.substring(pos, next) else inner.substring(pos)
            val cols = rowStr.split(",")
            // [0]時刻ms [1]日経平均 [7]25日騰落レシオ
            if (cols.size > 7) {
                val t = cols[0].trim().toLongOrNull()
                val nk = cols[1].trim().toDoubleOrNull()
                val ad = cols[7].trim().toDoubleOrNull()
                if (t != null && nk != null && nk > 0) {
                    rows.add(Row(t, nk, ad ?: Double.NaN))
                }
            }
            if (next < 0) break
            pos = next + 3
        }
        require(rows.size >= 260) { "行数不足: ${rows.size}行（ソース障害の可能性）" }
        rows.sortBy { it.timeMs }   // 念のため昇順を保証

        val idxAll = rows.map { it.nikkei }
        val last = rows.last()
        val n = idxAll.size
        val win252 = idxAll.subList(maxOf(0, n - 252), n)   // 直近252営業日
        val high52w = win252.max()
        val dd = last.nikkei / high52w - 1.0
        // 5営業日前比（pct_change(5) と同じ定義）
        val ret5d = if (n > 5) last.nikkei / idxAll[n - 6] - 1.0 else 0.0
        // 騰落レシオは最後の有効値（当日欄が未確定のことがある）
        val adrLast = rows.lastOrNull { !it.adr.isNaN() && it.adr > 0 }?.adr ?: Double.NaN

        val jst = TimeZone.getTimeZone("Asia/Tokyo")
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).apply { timeZone = jst }
        val minFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN).apply { timeZone = jst }

        // ─── 推移タブ用: 直近252営業日（約1年）分の3指標を日ごとに計算 ───
        // 各日 i について 52週DD・5日率・騰落レシオを最新日と同じ定義で再計算する
        val histFrom = maxOf(0, n - 252)
        val history = ArrayList<HistoryPoint>(n - histFrom)
        for (i in histFrom until n) {
            val winStart = maxOf(0, i - 251)                 // その日から見た直近252営業日
            val hiI = idxAll.subList(winStart, i + 1).max()  // その日時点の52週高値
            val ddI = idxAll[i] / hiI - 1.0
            val retI = if (i >= 5) idxAll[i] / idxAll[i - 5] - 1.0 else Double.NaN
            history.add(
                HistoryPoint(
                    date = dayFmt.format(Date(rows[i].timeMs)),
                    dd52w = ddI,
                    ret5d = retI,
                    adr25 = rows[i].adr   // 欠損日は NaN のまま（グラフ側でスキップ）
                )
            )
        }

        return MarketStatus(
            dataDate = dayFmt.format(Date(last.timeMs)),
            fetchedAt = minFmt.format(Date()),
            indexLast = last.nikkei,
            adr25 = adrLast,
            high52w = high52w,
            dd52w = dd,
            ret5d = ret5d,
            lev1570 = fetch1570(),
            indexRecent = idxAll.subList(maxOf(0, n - 60), n),
            history = history
        )
    }

    /** 日経レバ1570の直近終値（口数計算の目安用。失敗したら null で続行） */
    private fun fetch1570(): Double? = try {
        val json = JSONObject(httpGet(YAHOO_1570_URL))
        val meta = json.getJSONObject("chart")
            .getJSONArray("result").getJSONObject(0)
            .getJSONObject("meta")
        meta.optDouble("regularMarketPrice").takeIf { !it.isNaN() && it > 0 }
    } catch (e: Exception) {
        null
    }
}
