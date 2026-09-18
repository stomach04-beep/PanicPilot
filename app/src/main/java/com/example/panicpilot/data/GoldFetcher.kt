package com.example.panicpilot.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    /** 分配金調整済みの adjclose を優先（1306で踏んだ「分配落ちが下落に化ける」罠と同じ対策） */
    private fun parseCloses(body: String): List<Pair<Long, Double>> =
        YahooChart.parseCloses(body, preferAdj = true)

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
        val rows = dropBadTicks(parseCloses(YahooChart.httpGet(URL_GOLD)).sortedBy { it.first })
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
