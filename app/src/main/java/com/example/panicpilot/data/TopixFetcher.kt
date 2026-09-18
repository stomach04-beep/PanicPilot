package com.example.panicpilot.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    /**
     * 取得（再試行3回）と末尾null行の補完は YahooChart に集約（v2.3.4）。
     * chart APIから (タイムスタンプ秒, 終値) を取り出す。
     * 【重要】1306は毎年7月に分配金落ち（約2%）があり、生の終値だと落ち幅が
     * 「下落」として52週高値との差に混入する（実測: 生値高値435.4円 vs 調整後427.6円、
     * -15%ラインが370円になり検証48のTOPIX基準363円より約2%早く発火してしまう）。
     * そこで分配金調整済みの adjclose を優先して使う＝TOPIX指数基準のバックテスト
     * （検証48のライン 385/363/342/321円）と一致することを実データで確認済み（2026-08-11）。
     * adjclose が無い場合だけ生の close にフォールバックする
     */
    private fun parseCloses(body: String): List<Pair<Long, Double>> =
        YahooChart.parseCloses(body, preferAdj = true)

    /**
     * バッドティック（偽の値飛び）を除去する。
     * Yahooの1306データには日付を誤った分割レコード由来の異常値が実在する
     * （2026-03-30/31だけ価格が1/10スケール＝偽の-90%と+948%。2026-08-11実測）。
     * TOPIX ETFの実際の日次変動は最大でも±12%程度なので、直前の正常値から
     * ±25%を超えて飛んだ日は捨てる。放置すると偽の暴落ではしごが誤発火する
     */
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

    /** 1306の2年日足からはしご状態のスナップショットを作る */
    fun fetch(): TopixLadderStatus {
        val rows = dropBadTicks(parseCloses(YahooChart.httpGet(URL_1306)).sortedBy { it.first })
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
