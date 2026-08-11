package com.example.panicpilot.data

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * TOPIXはしご用のデータ取得。
 * daily2year.json にはTOPIX列が無い（実データで確定済み・MarketFetcher参照）ので、
 * Yahoo Finance chart API で 1306.T（NEXT FUNDS TOPIX連動型上場投信）自身の
 * 2年日足を取る。判定もラインも1306の実勢価格ベース＝ユーザーが発注する値段と同じ土俵。
 *
 * 日付は東京市場なのでJSTで日付化する（米国版のような時差の罠は無い）
 */
object TopixFetcher {

    // 52週高値の計算に直近252営業日＋エピソード判定の遡り分が要るので2年取る
    private const val URL_1306 =
        "https://query1.finance.yahoo.com/v8/finance/chart/1306.T?range=2y&interval=1d"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
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
     * chart APIから (タイムスタンプ秒, 終値) を取り出す。
     * 【重要】1306は毎年7月に分配金落ち（約2%）があり、生の終値だと落ち幅が
     * 「下落」として52週高値との差に混入する（実測: 生値高値435.4円 vs 調整後427.6円、
     * -15%ラインが370円になり検証48のTOPIX基準363円より約2%早く発火してしまう）。
     * そこで分配金調整済みの adjclose を優先して使う＝TOPIX指数基準のバックテスト
     * （検証48のライン 385/363/342/321円）と一致することを実データで確認済み（2026-08-11）。
     * adjclose が無い場合だけ生の close にフォールバックする
     */
    private fun parseCloses(body: String): List<Pair<Long, Double>> {
        val result = JSONObject(body).getJSONObject("chart")
            .getJSONArray("result").getJSONObject(0)
        val ts = result.optJSONArray("timestamp") ?: return emptyList()
        val indicators = result.getJSONObject("indicators")
        val closes = indicators.optJSONArray("adjclose")?.optJSONObject(0)
            ?.optJSONArray("adjclose")
            ?: indicators.getJSONArray("quote").getJSONObject(0)
                .optJSONArray("close")
            ?: return emptyList()
        val out = ArrayList<Pair<Long, Double>>(ts.length())
        for (i in 0 until ts.length()) {
            val c = closes.optDouble(i)          // nullは NaN で返る
            if (!c.isNaN() && c > 0) out.add(ts.getLong(i) to c)
        }
        return out
    }

    /** 1306の2年日足からはしご状態のスナップショットを作る */
    fun fetch(): TopixLadderStatus {
        val rows = parseCloses(httpGet(URL_1306)).sortedBy { it.first }
        require(rows.size >= 260) { "1306の行数不足: ${rows.size}行（ソース障害の可能性）" }

        val closes = rows.map { it.second }
        val n = closes.size
        val jst = TimeZone.getTimeZone("Asia/Tokyo")
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).apply { timeZone = jst }
        val minFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN).apply { timeZone = jst }

        // 各日のDD（その日から見た直近252営業日の最大値比。MarketFetcherのhistoryと同じ定義）
        val days = ArrayList<Pair<String, Double>>(n)
        for (i in 0 until n) {
            val winStart = maxOf(0, i - 251)
            val hiI = closes.subList(winStart, i + 1).max()
            days.add(dayFmt.format(Date(rows[i].first * 1000)) to (closes[i] / hiI - 1.0))
        }

        val high52w = closes.subList(maxOf(0, n - 252), n).max()
        val (clockStart, elapsed, minDd) = TopixLadderStatus.deriveEpisode(days)

        return TopixLadderStatus(
            dataDate = days.last().first,
            fetchedAt = minFmt.format(Date()),
            close = closes.last(),
            high52w = high52w,
            dd52w = closes.last() / high52w - 1.0,
            clockStartDate = clockStart,
            elapsedBiz = elapsed,
            minDdSinceClock = minDd
        )
    }
}
