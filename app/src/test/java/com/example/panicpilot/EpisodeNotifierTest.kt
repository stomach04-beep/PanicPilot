package com.example.panicpilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「状態が続いている間は1回だけ」通知の判定を、日々の流れで確かめる（v2.3.7） */
class EpisodeNotifierTest {

    /** 1日分の判定を回し、通知が出たら true を返す */
    private fun run(set: MutableSet<String>, key: String, active: Boolean, day: String): Boolean {
        var fired = false
        EpisodeNotifier.episode(set, key, active, day) { fired = true }
        return fired
    }

    @Test
    fun `点灯が続く間は初日の1回だけ鳴る`() {
        val set = mutableSetOf<String>()
        assertTrue(run(set, "deep", true, "2026-10-01"))
        assertFalse(run(set, "deep", true, "2026-10-02"))
        assertFalse(run(set, "deep", true, "2026-10-05"))
        assertTrue("ep:deep" in set)
    }

    @Test
    fun `消灯してから再点灯したらもう1回鳴る`() {
        val set = mutableSetOf<String>()
        assertTrue(run(set, "deep", true, "2026-10-01"))
        assertFalse(run(set, "deep", false, "2026-10-02"))   // 消灯＝エピソード終了
        assertFalse("ep:deep" in set)
        assertTrue(run(set, "deep", true, "2026-10-03"))     // 再点灯
    }

    @Test
    fun `種類ごとに別々に数える`() {
        val set = mutableSetOf<String>()
        assertTrue(run(set, "fill2", true, "2026-10-01"))
        assertTrue(run(set, "fill3", true, "2026-10-01"))
        assertFalse(run(set, "fill2", true, "2026-10-02"))
        // "deep" と "deep_locked"、"deep" と "us_deep" は前方一致で混ざらない
        assertTrue(run(set, "deep", true, "2026-10-02"))
        assertTrue(run(set, "deep_locked", true, "2026-10-02"))
        assertTrue(run(set, "us_deep", true, "2026-10-02"))
    }

    @Test
    fun `更新直後は旧方式の直近の印を通知済みとみなし二重に鳴らさない`() {
        val set = mutableSetOf("deep:2026-09-24")
        assertFalse(run(set, "deep", true, "2026-09-25"))
        assertTrue("ep:deep" in set)
        assertFalse("旧方式の印は片づく", set.any { it.startsWith("deep:") })
        assertFalse(run(set, "deep", true, "2026-09-26"))
    }

    @Test
    fun `旧方式の古い印は新しい点灯を止めない`() {
        // 実機に残っていた 2026-07-29 の印。2か月後の新しい点灯は鳴らす
        val set = mutableSetOf("deep:2026-07-29")
        assertTrue(run(set, "deep", true, "2026-09-26"))
    }

    @Test
    fun `消灯中は旧方式の印も片づける`() {
        val set = mutableSetOf("deep:2026-07-29", "off_deep:2026-07-30")
        assertFalse(run(set, "deep", false, "2026-09-26"))
        assertEquals(setOf("off_deep:2026-07-30"), set)   // 別の種類の印には触らない
    }
}
