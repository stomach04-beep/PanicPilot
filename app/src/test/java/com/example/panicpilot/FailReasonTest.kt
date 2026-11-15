package com.example.panicpilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 取得失敗の通知に入れる理由を日本語にする（v2.3.20）。
 * 通知に英単語が出ないこと（HTTP と数字だけは可）を確かめる
 */
class FailReasonTest {

    /** HTTP を除いて英字が1文字も無いか */
    private fun assertNoEnglish(s: String) {
        assertFalse("英字が残っている: $s", s.replace("HTTP", "").any { it in 'A'..'Z' || it in 'a'..'z' })
        assertTrue("100字を超えた: $s", s.length <= 100)
    }

    @Test
    fun 時間切れは日本語になる() {
        val r = DailyCheckWorker.failReasonJa(SocketTimeoutException("timeout"))
        assertEquals("時間切れ（相手の応答が遅い）", r)
        assertNoEnglish(r)
    }

    @Test
    fun 住所を引けないのは日本語になる() {
        val r = DailyCheckWorker.failReasonJa(UnknownHostException("query1.finance.yahoo.com"))
        assertEquals("接続できない（ネットが切れているか、住所を引けない）", r)
        assertNoEnglish(r)
    }

    @Test
    fun その他の通信エラーは日本語になる() {
        val r = DailyCheckWorker.failReasonJa(IOException("Connection reset"))
        assertEquals("接続できない（ネットか相手のサーバーの不調）", r)
        assertNoEnglish(r)
    }

    @Test
    fun 再試行の束は先頭の日本語を残して中身から分類する() {
        // YahooChart.httpGet が投げる実際の形
        val e = IllegalStateException("3回とも失敗 [SocketTimeoutException: timeout / SocketTimeoutException: timeout / HTTP 429]")
        val r = DailyCheckWorker.failReasonJa(e)
        assertEquals("3回とも失敗：時間切れ（相手の応答が遅い）", r)
        assertNoEnglish(r)
    }

    @Test
    fun HTTPの応答コードは番号を残す() {
        val r = DailyCheckWorker.failReasonJa(IllegalStateException("HTTP 404: https://example.com/data.js"))
        assertEquals("相手のサーバーの応答が異常（HTTP 404）", r)
        assertNoEnglish(r)
    }

    @Test
    fun causeの連鎖もたどる() {
        val r = DailyCheckWorker.failReasonJa(RuntimeException("wrap", SocketTimeoutException("Read timed out")))
        assertEquals("時間切れ（相手の応答が遅い）", r)
    }

    @Test
    fun 英字を含む先頭は付けずに形の異常と分類する() {
        val r = DailyCheckWorker.failReasonJa(IllegalArgumentException("S&P500の行数不足: 120行（ソース障害の可能性）"))
        assertEquals("データの形が想定と違う（形式が変わった可能性）", r)
        assertNoEnglish(r)
    }

    @Test
    fun 分類できないものは想定外() {
        val r = DailyCheckWorker.failReasonJa(IllegalStateException("something odd"))
        assertEquals("想定外のエラー（詳しくはログ）", r)
        assertNoEnglish(r)
    }
}
