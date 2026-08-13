package com.example.panicpilot.data

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * 金ETF（314A: iシェアーズ ゴールドETF）の日足を Yahoo Finance chart API から取る。
 *
 * 314A は2025-01上場でまだ2年分に満たない。52週高値が計算できるだけの行数が
 * 揃っていない場合は「ある分だけで計算した高値」を使い、画面側に断りを出す
 * （行数不足で例外を投げると金タブごと落ちるので fail-soft にする）。
 *
 * 日付は東京市場なのでJSTで日付化する（米国版のような時差の罠は無い）。
 */
object GoldFetcher {

    private const val URL_GOLD =
        "https://query1.finance.yahoo.com/v8/finance/chart/314A.T?range=2y&interval=1d"
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

    /** 分配金調整済みの adjclose を優先（1306で踏んだ「分配落ちが下落に化ける」罠と同じ対策） */
    private fun parseCloses(body: String): List<Pair<Long, Double>> {
        val result = JSONObject(body).getJSONObject("chart")
            .getJSONArray("result").getJSONObject(0)
        val ts = result.optJSONArray("timestamp") ?: return emptyList()
        val indicators = result.getJSONObject("indicators")
        val closes = indicators.optJSONArray("adjclose")?.optJSONObject(0)
            ?.optJSONArray("adjclose")
            ?: indicators.getJSONArray("quote").getJSONObject(0).optJSONArray("close")
            ?: return emptyList()
        val out = ArrayList<Pair<Long, Double>>(ts.length())
        for (i in 0 until ts.length()) {
            val c = closes.optDouble(i)
            if (!c.isNaN() && c > 0) out.add(ts.getLong(i) to c)
        }
        return out
    }

    /** 偽の値飛びを除去（1306で実在した誤日付の分割レコード対策を横展開） */
    private fun dropBadTicks(rows: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val out = ArrayList<Pair<Long, Double>>(rows.size)
        var last = Double.NaN
        for (r in rows) {
            if (!last.isNaN() && kotlin.math.abs(r.second / last - 1.0) > 0.25) continue
            out.add(r)
            last = r.second
        }
        return out
    }

    /** Wilder方式のRSI（参考表示用。買いシグナルには使わない＝検証で棄却済み） */
    private fun rsi14(closes: List<Double>): Double {
        if (closes.size < 20) return Double.NaN
        val n = 14
        var avgUp = 0.0
        var avgDn = 0.0
        for (i in 1..n) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) avgUp += d else avgDn += -d
        }
        avgUp /= n
        avgDn /= n
        for (i in n + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            val up = if (d > 0) d else 0.0
            val dn = if (d < 0) -d else 0.0
            avgUp = (avgUp * (n - 1) + up) / n
            avgDn = (avgDn * (n - 1) + dn) / n
        }
        if (avgDn == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + avgUp / avgDn)
    }

    fun fetch(): GoldStatus {
        val rows = dropBadTicks(parseCloses(httpGet(URL_GOLD)).sortedBy { it.first })
        require(rows.size >= 30) { "314Aの行数不足: ${rows.size}行（ソース障害の可能性）" }

        val closes = rows.map { it.second }
        val n = closes.size
        val jst = TimeZone.getTimeZone("Asia/Tokyo")
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).apply { timeZone = jst }
        val minFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN).apply { timeZone = jst }

        // 252営業日ぶん無ければある分だけで高値を取る（314Aは2025-01上場のため）
        val high = closes.subList(maxOf(0, n - 252), n).max()

        return GoldStatus(
            dataDate = dayFmt.format(Date(rows.last().first * 1000)),
            fetchedAt = minFmt.format(Date()),
            price = closes.last(),
            high52w = high,
            dd52w = closes.last() / high - 1.0,
            rsi14 = rsi14(closes)
        )
    }
}
