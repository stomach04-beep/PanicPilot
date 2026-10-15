package com.example.panicpilot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 日次チェックのアラーム時刻（v2.3.10）。
 * 「前回いつ走ったか」ではなく「いまから見て次の 16:30（日本時間）」を毎回選ぶこと＝遅れが持ち越されないこと
 */
class DailyAlarmTest {

    private val jst = ZoneId.of("Asia/Tokyo")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, jst).toInstant().toEpochMilli()

    @Test
    fun 朝なら当日の16時30分() {
        assertEquals(ms(2026, 10, 16, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 16, 9, 0)))
    }

    @Test
    fun 遅れて19時30分に走っても次は翌日の16時30分() {
        // 10-15 は 19:30 に実行された。24時間周期だと次も 19:30 だったが、アラームは 16:30 に戻る
        assertEquals(ms(2026, 10, 16, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 15, 19, 30)))
    }

    @Test
    fun アラームが鳴った直後は当日を選ばず翌日() {
        // 16:30:00 ちょうど・数秒過ぎに掛け直しても、同じ日の 16:30 をもう一度選ばない
        assertEquals(ms(2026, 10, 16, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 15, 16, 30, 0)))
        assertEquals(ms(2026, 10, 16, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 15, 16, 30, 5)))
        // 1分より前なら当日のまま
        assertEquals(ms(2026, 10, 15, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 15, 16, 28)))
    }

    @Test
    fun 月末と年末をまたぐ() {
        assertEquals(ms(2026, 11, 1, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 31, 20, 0)))
        assertEquals(ms(2027, 1, 1, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 12, 31, 23, 59)))
    }

    @Test
    fun 端末の時間帯が日本でなくても日本時間の16時30分() {
        // 日本時間の 2026-10-16 01:00（UTC では 10-15 16:00）→ 日本時間の 10-16 16:30
        assertEquals(ms(2026, 10, 16, 16, 30), DailyAlarm.nextTriggerMillis(ms(2026, 10, 16, 1, 0)))
    }
}
