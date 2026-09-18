package com.example.panicpilot.data

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * Yahoo Finance chart API の共通部品（v2.3.4・2026-09-19）。
 *
 * それまで 1306／金(314A)／米国(S&P500・VIX) の各Fetcherが同じ httpGet と parseCloses を
 * 別々に持っていたのを1か所へ集約した。理由は次の2つの穴を全部まとめて塞ぐため:
 *
 *  1) 再試行が無かった。1306は「1日1回・1発勝負」で、Yahooの一時的な失敗
 *     （HTTP 429/5xx・タイムアウト）が2日続いただけで「取得に失敗しています」通知が出た
 *     （2026-09-18/19 に実発生。1時間後の取得は普通に成功＝恒久障害ではなかった）
 *  2) 末尾の日足が close=null で返ることがある。空行を捨てるだけだと最新日が1営業日
 *     古いままになる（2026-09-19 実測: 1306の 09-18 行が null、meta の終値だけ正しい）。
 *     SectorHeatmap / SectorLagSignal / MacroAlert で修正済みの穴がここに残っていた
 */
object YahooChart {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_ATTEMPTS = 3                       // 合計の試行回数
    private val BACKOFF_MS = longArrayOf(1_500L, 4_000L)     // 1回目→2回目、2回目→3回目の待ち時間
    private const val HOST_MAIN = "query1.finance.yahoo.com"
    private const val HOST_SUB = "query2.finance.yahoo.com"  // 予備サーバ（中身は同じ）

    /** 1回だけ取りに行く（失敗は例外で返す） */
    private fun httpGetOnce(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    /** 何回目の試行でどのURLを叩くか。2回目以降は予備サーバと交互にする（テスト用に internal） */
    internal fun urlForAttempt(url: String, attempt: Int): String =
        if (attempt % 2 == 1) url.replace(HOST_MAIN, HOST_SUB) else url

    /**
     * 再試行つきの取得。最大3回、間隔を空けて試す（1.5秒→4秒）。
     * 全部だめなら「各回の失敗理由」を並べた例外を投げる＝呼び出し側が理由を記録できる。
     * sleeper は単体テストで待ち時間を飛ばすための差し替え口
     */
    fun httpGet(
        url: String,
        getter: (String) -> String = ::httpGetOnce,
        sleeper: (Long) -> Unit = { Thread.sleep(it) }
    ): String {
        val errors = ArrayList<String>(MAX_ATTEMPTS)
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                return getter(urlForAttempt(url, attempt))
            } catch (e: Exception) {
                // 例外の種類も残す（タイムアウトはメッセージが空のことがあるため）
                errors.add("${e.javaClass.simpleName}: ${e.message ?: "-"}")
                if (attempt < MAX_ATTEMPTS - 1) sleeper(BACKOFF_MS[attempt])
            }
        }
        throw IllegalStateException("${MAX_ATTEMPTS}回とも失敗 [" + errors.joinToString(" / ") + "]")
    }

    /**
     * chart APIの本文から (タイムスタンプ秒, 終値) の列を取り出す。null・0以下の行は捨てる。
     * @param preferAdj true=分配金調整済みの adjclose を優先（ETF用。1306の分配落ちが
     *                  「下落」に化ける罠の対策）。false=生の close（指数用）
     * 末尾の行が null だったときは meta の終値で補完する（fillLastFromMeta）
     */
    fun parseCloses(body: String, preferAdj: Boolean): List<Pair<Long, Double>> {
        val result = JSONObject(body).getJSONObject("chart")
            .getJSONArray("result").getJSONObject(0)
        val ts = result.optJSONArray("timestamp") ?: return emptyList()
        val indicators = result.getJSONObject("indicators")
        val adj = if (preferAdj) indicators.optJSONArray("adjclose")?.optJSONObject(0)
            ?.optJSONArray("adjclose") else null
        val closes = adj
            ?: indicators.getJSONArray("quote").getJSONObject(0).optJSONArray("close")
            ?: return emptyList()
        val out = ArrayList<Pair<Long, Double>>(ts.length())
        for (i in 0 until ts.length()) {
            val c = closes.optDouble(i)          // nullは NaN で返る
            if (!c.isNaN() && c > 0) out.add(ts.getLong(i) to c)
        }
        if (ts.length() == 0) return out

        val meta = result.optJSONObject("meta") ?: return out
        val metaPrice = meta.optDouble("regularMarketPrice")
        return fillLastFromMeta(
            rows = out,
            lastTs = ts.getLong(ts.length() - 1),
            metaTime = meta.optLong("regularMarketTime", 0L),
            metaPrice = if (metaPrice.isNaN()) null else metaPrice,
            // 取引所の時間帯が分からないときは日付の突き合わせができないので補完しない
            tzId = meta.optString("exchangeTimezoneName", "")
        )
    }

    /**
     * 末尾の日足が空（null）だったとき、meta の終値（regularMarketPrice）で埋める。
     * 埋めるのは「配列末尾のタイムスタンプの日付」と「regularMarketTime の日付」が
     * 取引所の現地日付で同じときだけ（＝その日の値段を meta が持っている）。
     * 最新日は調整済み終値と生の終値が一致するので、adjclose の列に足しても問題ない。
     * JSONに触らない純粋な関数にしてある（単体テスト用）
     */
    internal fun fillLastFromMeta(
        rows: List<Pair<Long, Double>>,
        lastTs: Long,
        metaTime: Long,
        metaPrice: Double?,
        tzId: String
    ): List<Pair<Long, Double>> {
        if (metaPrice == null || metaPrice <= 0.0 || metaTime <= 0L || tzId.isBlank()) return rows
        // 末尾の行がちゃんと入っている（か、それより新しい行がある）なら何もしない
        val lastValidTs = rows.lastOrNull()?.first
        if (lastValidTs != null && lastValidTs >= lastTs) return rows
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(tzId) }
        if (fmt.format(Date(lastTs * 1000L)) != fmt.format(Date(metaTime * 1000L))) return rows
        return rows + (lastTs to metaPrice)
    }
}
