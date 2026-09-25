package com.example.panicpilot

import java.time.LocalDate

/**
 * 「状態が続いている間は1回だけ」通知する判定（エピソード単位。v2.3.7〜・LESSON-211）。
 *
 * 旧方式は「種類＋データ日付」で重複を防いでいたため、点灯が続く間は毎営業日同じ通知が届いた。
 * 1306はしごの "clock:開始日" と同じ考え方で、状態に入ったときに1回だけ鳴らし、
 * 状態から外れたら印を消す（次に同じ状態に入ったらまた1回鳴る）。
 * 通知処理（Android）を持たない純粋な関数にしてあるので、単体テストで確かめられる。
 */
internal object EpisodeNotifier {

    /**
     * @param notified 通知済みの印（保存データの notifiedKeys。この関数が書き換える）
     * @param key      通知の種類（"deep" "fill2" "us_exit" など）
     * @param active   いまその状態にあるか
     * @param today    データ日付 yyyy-MM-dd（旧方式の印の移行判定にだけ使う）
     * @param fire     通知を出す処理（鳴らすべきときだけ呼ばれる）
     */
    fun episode(
        notified: MutableSet<String>, key: String, active: Boolean, today: String,
        fire: () -> Unit
    ) {
        val epKey = "ep:$key"
        if (!active) {
            notified.remove(epKey)   // 状態から外れた＝エピソード終了
            notified.removeAll { it.startsWith("$key:") }   // 旧方式の印も片づける
            return
        }
        if (epKey !in notified) {
            // 移行措置: 旧方式の印（"種類:日付"）が直近7日以内にあれば、今のエピソードは通知済みとみなす。
            // これが無いと、更新した直後に点灯中の通知がもう一度鳴る
            val weekAgo = runCatching { LocalDate.parse(today).minusDays(7).toString() }.getOrNull()
            val legacyRecent = weekAgo != null && notified.any {
                it.startsWith("$key:") && it.substringAfter("$key:") >= weekAgo
            }
            if (!legacyRecent) fire()
            notified.add(epKey)
        }
        // 旧方式の印はもう書かないので片づける（残すと移行判定が古い印を拾い続ける）
        notified.removeAll { it.startsWith("$key:") }
    }
}
