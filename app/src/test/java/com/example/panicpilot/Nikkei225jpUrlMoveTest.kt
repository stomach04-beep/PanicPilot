package com.example.panicpilot

import com.example.panicpilot.data.MarketFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * v2.3.8（2026-10-05）: nikkei225jp.com のデータファイルの置き場所変更に追従する処理のテスト。
 * 2026-10-04 にサイト側が …/_nfsDATA/DAY/ を …/_nfsDATA/data_DAY/ へ移し、
 * 決め打ちURLが404になって取得が止まった。script タグの形は 2026-10-05 に実ページで確認したもの。
 */
class Nikkei225jpUrlMoveTest {

    private val oldDaily = "https://nikkei225jp.com/_data/_nfsDATA/DAY/daily2year.json"
    private val newDaily = "https://nikkei225jp.com/_data/_nfsDATA/data_DAY/daily2year.json"
    private val page = MarketFetcher.REFERER

    // 実ページ（karauri.php）の該当行と同じ形。前後に紛らわしいタグを置く
    private val realHtml = """
        <html><head>
        <script src="/js/jquery.min.js"></script>
        <a href="/old/daily2year.json">リンクは対象外</a>
        <script>var memo = "/inline/daily2year.json";</script>
        <script src="/_data/_nfsDATA/data_DAY/daily2year.json?497541">
        </script>
        </head></html>
    """.trimIndent()

    private fun notFound(url: String): Nothing = throw MarketFetcher.HttpStatusException(404, url)

    // ─── 置き場所を探す ───

    @Test
    fun 控えのURLは新しい置き場所を指している() {
        assertEquals(newDaily, MarketFetcher.DAILY_URL)
    }

    @Test
    fun scriptのsrcからファイル名で新しい置き場所を見つける() {
        assertEquals(newDaily, MarketFetcher.discoverDataUrl(realHtml, oldDaily, page))
    }

    @Test
    fun 単一引用符と番号なしと絶対URLでも見つかる() {
        val html = "<SCRIPT type='text/javascript' src = 'https://nikkei225jp.com/x/y/daily2year.json'></SCRIPT>"
        assertEquals(
            "https://nikkei225jp.com/x/y/daily2year.json",
            MarketFetcher.discoverDataUrl(html, oldDaily, page)
        )
    }

    @Test
    fun scriptのsrc以外に出てくるファイル名は拾わない() {
        val html = """
            <a href="/a/daily2year.json">x</a>
            <script>var u = "/b/daily2year.json";</script>
            <script data-src="/c/daily2year.json"></script>
            <script src="/d/xdaily2year.json"></script>
            <script src="/e/daily2year.json.bak"></script>
        """.trimIndent()
        assertNull(MarketFetcher.discoverDataUrl(html, oldDaily, page))
    }

    @Test
    fun 別のホストを指すsrcは採らない() {
        val html = """<script src="https://example.com/_data/daily2year.json?1"></script>"""
        assertNull(MarketFetcher.discoverDataUrl(html, oldDaily, page))
    }

    // ─── 404 のときだけ探し直す ───

    @Test
    fun 控えURLが200なら参照元ページは取りに行かない() {
        val calls = mutableListOf<String>()
        val body = MarketFetcher.fetchDailyBody(
            url = newDaily,
            getter = { url, _ -> calls.add(url); "var DAILY = [" },
            onMoved = { fail("置き場所の変更は起きていない") }
        )
        assertEquals("var DAILY = [", body)
        assertEquals(listOf(newDaily), calls)
    }

    @Test
    fun 控えURLが404ならページから探して取り直す() {
        val calls = mutableListOf<Pair<String, String?>>()
        var moved: String? = null
        val body = MarketFetcher.fetchDailyBody(
            url = oldDaily,
            getter = { url, referer ->
                calls.add(url to referer)
                when (url) {
                    oldDaily -> notFound(url)
                    page -> realHtml
                    newDaily -> "var DAILY = ["
                    else -> throw MarketFetcher.HttpStatusException(500, url)
                }
            },
            onMoved = { moved = it }
        )
        assertEquals("var DAILY = [", body)
        assertEquals(newDaily, moved)
        // データファイルは Referer 付き、参照元ページは Referer なしで取る
        assertEquals(listOf(oldDaily to page, page to null, newDaily to page), calls)
    }

    @Test
    fun 見つからない以外の失敗では探し直さない() {
        val calls = mutableListOf<String>()
        try {
            MarketFetcher.fetchDailyBody(
                url = oldDaily,
                getter = { url, _ -> calls.add(url); throw MarketFetcher.HttpStatusException(403, url) },
                onMoved = {}
            )
            fail("403 はそのまま投げる")
        } catch (e: MarketFetcher.HttpStatusException) {
            assertEquals(403, e.code)
        }
        assertEquals(listOf(oldDaily), calls)
    }

    @Test
    fun ページから見つからなければ最初の404を投げる() {
        try {
            MarketFetcher.fetchDailyBody(
                url = oldDaily,
                getter = { url, _ -> if (url == page) "<html></html>" else notFound(url) },
                onMoved = {}
            )
            fail("見つからなければ 404 を投げる")
        } catch (e: MarketFetcher.HttpStatusException) {
            assertEquals(404, e.code)
            // 画面に出る文言は従来と同じ形（HTTP 404: URL）
            assertEquals("HTTP 404: $oldDaily", e.message)
        }
    }
}
