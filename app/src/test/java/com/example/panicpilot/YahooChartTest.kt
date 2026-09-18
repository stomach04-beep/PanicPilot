package com.example.panicpilot

import com.example.panicpilot.data.YahooChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * YahooChart（再試行＋末尾null行の補完）の単体テスト。
 * 時刻は 2026-09-19 に実測した 1306.T の値を使う:
 *   09-17 の行 = 1789603200（09:00 JST）… 終値 427.4
 *   09-18 の行 = 1789689600（09:00 JST）… close=null で返ってきた
 *   meta.regularMarketTime = 1789713000（09-18 15:30 JST）/ regularMarketPrice = 426.3
 */
class YahooChartTest {

    private val d0917 = 1789603200L
    private val d0918 = 1789689600L
    private val meta0918 = 1789713000L
    private val tokyo = "Asia/Tokyo"

    // ─── 末尾null行の補完 ───

    @Test
    fun 末尾がnullならmetaの終値で埋まる() {
        val rows = listOf(d0917 to 427.4)   // 09-18 は null で捨てられた状態
        val out = YahooChart.fillLastFromMeta(rows, d0918, meta0918, 426.3, tokyo)
        assertEquals(2, out.size)
        assertEquals(d0918, out.last().first)
        assertEquals(426.3, out.last().second, 1e-9)
    }

    @Test
    fun 末尾が入っていれば何もしない() {
        val rows = listOf(d0917 to 427.4, d0918 to 426.3)
        val out = YahooChart.fillLastFromMeta(rows, d0918, meta0918, 999.0, tokyo)
        assertEquals(rows, out)
    }

    @Test
    fun metaの日付が違えば埋めない() {
        // meta が前日（09-17 15:30）のままなら、09-18 の値段は持っていない
        val rows = listOf(d0917 to 427.4)
        val out = YahooChart.fillLastFromMeta(rows, d0918, d0917 + 6 * 3600 + 1800, 427.4, tokyo)
        assertEquals(rows, out)
    }

    @Test
    fun metaが欠けていれば埋めない() {
        val rows = listOf(d0917 to 427.4)
        assertEquals(rows, YahooChart.fillLastFromMeta(rows, d0918, meta0918, null, tokyo))
        assertEquals(rows, YahooChart.fillLastFromMeta(rows, d0918, 0L, 426.3, tokyo))
        assertEquals(rows, YahooChart.fillLastFromMeta(rows, d0918, meta0918, 426.3, ""))
    }

    @Test
    fun 米国は東部時間の日付で突き合わせる() {
        // 2026-09-18 09:30 ET の行（=13:30 UTC）と、同日 16:00 ET の meta（=20:00 UTC）。
        // JSTで日付化すると meta 側だけ 09-19 に化けて不一致になる＝取引所の時間帯で比べる必要がある
        val bar = 1789738200L      // 2026-09-18 13:30 UTC
        val metaT = 1789761600L    // 2026-09-18 20:00 UTC
        val rows = listOf(bar - 86400 to 6500.0)
        val out = YahooChart.fillLastFromMeta(rows, bar, metaT, 6510.0, "America/New_York")
        assertEquals(2, out.size)
        assertEquals(6510.0, out.last().second, 1e-9)
    }

    // ─── 再試行 ───

    private val url = "https://query1.finance.yahoo.com/v8/finance/chart/1306.T?range=2y&interval=1d"

    @Test
    fun 二回目は予備サーバへ切り替える() {
        assertTrue(YahooChart.urlForAttempt(url, 0).contains("query1."))
        assertTrue(YahooChart.urlForAttempt(url, 1).contains("query2."))
        assertTrue(YahooChart.urlForAttempt(url, 2).contains("query1."))
    }

    @Test
    fun 一回失敗しても二回目で成功すれば値が返る() {
        val called = ArrayList<String>()
        val waits = ArrayList<Long>()
        val body = YahooChart.httpGet(
            url,
            getter = { u -> called.add(u); if (called.size == 1) throw IllegalStateException("HTTP 429") else "OK" },
            sleeper = { waits.add(it) }
        )
        assertEquals("OK", body)
        assertEquals(2, called.size)
        assertTrue(called[1].contains("query2."))
        assertEquals(listOf(1500L), waits)
    }

    @Test
    fun 三回とも失敗なら各回の理由を並べて投げる() {
        var n = 0
        try {
            YahooChart.httpGet(url, getter = { n++; throw IllegalStateException("HTTP 429") }, sleeper = {})
            fail("例外が出るはず")
        } catch (e: IllegalStateException) {
            assertEquals(3, n)
            assertTrue(e.message!!.contains("3回とも失敗"))
            assertEquals(3, Regex("HTTP 429").findAll(e.message!!).count())
        }
    }
}
