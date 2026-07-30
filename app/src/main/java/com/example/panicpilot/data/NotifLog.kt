package com.example.panicpilot.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通知履歴の保存（JSONファイル1本、最大100件）。
 * NotificationHelper.notify() を通るすべての通知をここに記録するので、
 * 点灯・買い増し・出口・取得失敗警告・テスト通知の全部が履歴に残る。
 * シグナルデータ本体(panicpilot_data.json)とは別ファイルにして、
 * Worker側のデータ保存と競合しないようにしている。
 */
object NotifLog {

    private const val FILE_NAME = "panicpilot_notif_log.json"
    private const val MAX_ENTRIES = 100   // これを超えた古い分は捨てる

    /** 履歴1件分 */
    data class Entry(
        val at: String,          // 通知時刻 yyyy-MM-dd HH:mm（日本時間）
        val title: String,       // 通知タイトル
        val text: String,        // 通知本文
        val delivered: Boolean   // false = 通知権限が無くて出せなかった
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 履歴を読み込む（新しい順に並んでいる） */
    fun load(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    at = o.getString("at"),
                    title = o.getString("title"),
                    text = o.getString("text"),
                    delivered = o.optBoolean("delivered", true)
                )
            }
        } catch (e: Exception) {
            emptyList()   // 壊れていたら空扱い（履歴なので失っても本体機能に影響なし）
        }
    }

    /**
     * 先頭（最新）に1件追記する。
     * WorkerスレッドとUIスレッドの両方から呼ばれ得るので @Synchronized で直列化。
     */
    @Synchronized
    fun append(context: Context, entry: Entry) {
        val list = (listOf(entry) + load(context)).take(MAX_ENTRIES)
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("at", e.at)
                put("title", e.title)
                put("text", e.text)
                put("delivered", e.delivered)
            })
        }
        // 原子的保存（一時ファイルに書いてからリネーム。書き込み中クラッシュ対策の既知の教訓）
        val f = file(context)
        val tmp = File(f.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) {          // renameTo失敗時のフォールバック
            f.writeText(arr.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }
}
