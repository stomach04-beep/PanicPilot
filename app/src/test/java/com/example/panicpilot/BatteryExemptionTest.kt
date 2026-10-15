package com.example.panicpilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取得失敗の通知に添える「電池最適化の除外」の案内（v2.3.9）。
 * 未除外のときだけ、一番ありそうな原因として除外を促す
 */
class BatteryExemptionTest {

    @Test
    fun 未除外なら除外を促す一文を返す() {
        val hint = BatteryExemption.failureHint(exempted = false)
        assertTrue(hint.contains("【まず確認】"))
        assertTrue(hint.contains("電池最適化の除外"))
        assertTrue(hint.contains("履歴"))   // どこから除外できるかまで書く
    }

    @Test
    fun 除外済みなら何も足さない() {
        assertEquals("", BatteryExemption.failureHint(exempted = true))
    }
}
