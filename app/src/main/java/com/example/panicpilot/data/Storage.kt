package com.example.panicpilot.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * ローカル保存（JSONファイル1本）。
 * 保存は「一時ファイルに書いてからリネーム」の原子的保存
 * （書き込み中のクラッシュでファイルが壊れるのを防ぐ既知の教訓）
 */
object Storage {

    private const val FILE_NAME = "panicpilot_data.json"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    // ─── 読み込み ───
    fun load(context: Context): Saved {
        val f = file(context)
        if (!f.exists()) return Saved(null, null, emptySet(), null, emptySet())
        return try {
            val root = JSONObject(f.readText(Charsets.UTF_8))
            val status = root.optJSONObject("status")?.let { s ->
                MarketStatus(
                    dataDate = s.getString("dataDate"),
                    fetchedAt = s.getString("fetchedAt"),
                    indexLast = s.getDouble("indexLast"),
                    adr25 = s.optDouble("adr25", Double.NaN),
                    nikkeiVi = s.optDouble("nikkeiVi", Double.NaN),  // 旧保存ファイルには無い
                    high52w = s.getDouble("high52w"),
                    dd52w = s.getDouble("dd52w"),
                    ret5d = s.getDouble("ret5d"),
                    // 旧キー"lev1570"は読まない（v1.8で1458へ変更。古い値は次回取得で埋まる）
                    lev1458 = s.optDouble("lev1458").takeIf { !it.isNaN() },
                    indexRecent = s.optJSONArray("indexRecent")?.let { a ->
                        (0 until a.length()).map { a.getDouble(it) }
                    } ?: emptyList(),
                    // 推移データ（無い旧保存ファイルなら空リスト）
                    history = s.optJSONArray("history")?.let { a ->
                        (0 until a.length()).map { i ->
                            val h = a.getJSONObject(i)
                            HistoryPoint(
                                date = h.getString("date"),
                                dd52w = h.getDouble("dd52w"),
                                ret5d = h.optDouble("ret5d", Double.NaN),
                                adr25 = h.optDouble("adr25", Double.NaN)
                            )
                        }
                    } ?: emptyList()
                )
            }
            val pos = root.optJSONObject("position")?.let { p ->
                Position(
                    entryDate = p.getString("entryDate"),
                    baseIndex = p.getDouble("baseIndex"),
                    budgetYen = p.getLong("budgetYen"),
                    fill2Done = p.optBoolean("fill2Done"),
                    fill3Done = p.optBoolean("fill3Done")
                )
            }
            val notified = root.optJSONArray("notified")?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } ?: emptySet()
            // 前回の点灯レベル（消灯判定用）。旧保存ファイルには無いので null 可
            val lastLevel = root.optString("lastLevel").takeIf { it.isNotEmpty() }
            val lastSignals = root.optJSONArray("lastLitSignals")?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } ?: emptySet()
            // 撤退後ロック（旧保存ファイルには無いので null 可）
            val retreatedAt = root.optString("retreatedAt").takeIf { it.isNotEmpty() }

            // ─── 米国市場（v1.9追加。旧保存ファイルには無いので全て null/空 可） ───
            val usStatus = root.optJSONObject("usStatus")?.let { s ->
                UsMarketStatus(
                    dataDate = s.getString("dataDate"),
                    fetchedAt = s.getString("fetchedAt"),
                    indexLast = s.getDouble("indexLast"),
                    vix = s.optDouble("vix", Double.NaN),
                    high52w = s.getDouble("high52w"),
                    dd52w = s.getDouble("dd52w"),
                    ret5d = s.getDouble("ret5d"),
                    spxl = s.optDouble("spxl").takeIf { !it.isNaN() },
                    usdJpy = s.optDouble("usdJpy").takeIf { !it.isNaN() },
                    indexRecent = s.optJSONArray("indexRecent")?.let { a ->
                        (0 until a.length()).map { a.getDouble(it) }
                    } ?: emptyList(),
                    // 推移データ（v2.1追加。無い旧保存ファイルなら空リスト）
                    history = s.optJSONArray("history")?.let { a ->
                        (0 until a.length()).map { i ->
                            val h = a.getJSONObject(i)
                            UsHistoryPoint(
                                date = h.getString("date"),
                                dd52w = h.getDouble("dd52w"),
                                ret5d = h.optDouble("ret5d", Double.NaN),
                                vix = h.optDouble("vix", Double.NaN)
                            )
                        }
                    } ?: emptyList()
                )
            }
            val usPos = root.optJSONObject("usPosition")?.let { p ->
                Position(
                    entryDate = p.getString("entryDate"),
                    baseIndex = p.getDouble("baseIndex"),
                    budgetYen = p.getLong("budgetYen"),
                    fill2Done = p.optBoolean("fill2Done"),
                    fill3Done = p.optBoolean("fill3Done")
                )
            }
            val usLastLevel = root.optString("usLastLevel").takeIf { it.isNotEmpty() }
            val usLastSignals = root.optJSONArray("usLastLitSignals")?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } ?: emptySet()
            val usRetreatedAt = root.optString("usRetreatedAt").takeIf { it.isNotEmpty() }

            Saved(status, pos, notified, lastLevel, lastSignals, retreatedAt,
                  usStatus, usPos, usLastLevel, usLastSignals, usRetreatedAt)
        } catch (e: Exception) {
            Saved(null, null, emptySet(), null, emptySet())   // 壊れていたら初期状態から
        }
    }

    // ─── 保存（原子的） ───
    fun save(context: Context, saved: Saved) {
        val root = JSONObject()
        saved.status?.let { s ->
            root.put("status", JSONObject().apply {
                put("dataDate", s.dataDate); put("fetchedAt", s.fetchedAt)
                put("indexLast", s.indexLast)
                put("adr25", s.adr25); put("high52w", s.high52w)
                if (!s.nikkeiVi.isNaN()) put("nikkeiVi", s.nikkeiVi)
                put("dd52w", s.dd52w); put("ret5d", s.ret5d)
                s.lev1458?.let { put("lev1458", it) }
                put("indexRecent", JSONArray(s.indexRecent))
                // 推移データ（3指標×直近252営業日）
                put("history", JSONArray().apply {
                    s.history.forEach { h ->
                        put(JSONObject().apply {
                            put("date", h.date)
                            put("dd52w", h.dd52w)
                            if (!h.ret5d.isNaN()) put("ret5d", h.ret5d)
                            if (!h.adr25.isNaN()) put("adr25", h.adr25)
                        })
                    }
                })
            })
        }
        saved.position?.let { p ->
            root.put("position", JSONObject().apply {
                put("entryDate", p.entryDate); put("baseIndex", p.baseIndex)
                put("budgetYen", p.budgetYen)
                put("fill2Done", p.fill2Done); put("fill3Done", p.fill3Done)
            })
        }
        root.put("notified", JSONArray(saved.notifiedKeys.toList()))
        // 前回の点灯レベル（消灯通知の判定に使う。DailyCheckWorker だけが更新する）
        saved.lastLevel?.let { root.put("lastLevel", it) }
        root.put("lastLitSignals", JSONArray(saved.lastLitSignals.toList()))
        saved.retreatedAt?.let { root.put("retreatedAt", it) }

        // ─── 米国市場（v1.9追加） ───
        saved.usStatus?.let { s ->
            root.put("usStatus", JSONObject().apply {
                put("dataDate", s.dataDate); put("fetchedAt", s.fetchedAt)
                put("indexLast", s.indexLast)
                if (!s.vix.isNaN()) put("vix", s.vix)
                put("high52w", s.high52w)
                put("dd52w", s.dd52w); put("ret5d", s.ret5d)
                s.spxl?.let { put("spxl", it) }
                s.usdJpy?.let { put("usdJpy", it) }
                put("indexRecent", JSONArray(s.indexRecent))
                // 推移データ（v2.1）
                put("history", JSONArray().apply {
                    s.history.forEach { h ->
                        put(JSONObject().apply {
                            put("date", h.date)
                            put("dd52w", h.dd52w)
                            if (!h.ret5d.isNaN()) put("ret5d", h.ret5d)
                            if (!h.vix.isNaN()) put("vix", h.vix)
                        })
                    }
                })
            })
        }
        saved.usPosition?.let { p ->
            root.put("usPosition", JSONObject().apply {
                put("entryDate", p.entryDate); put("baseIndex", p.baseIndex)
                put("budgetYen", p.budgetYen)
                put("fill2Done", p.fill2Done); put("fill3Done", p.fill3Done)
            })
        }
        saved.usLastLevel?.let { root.put("usLastLevel", it) }
        root.put("usLastLitSignals", JSONArray(saved.usLastLitSignals.toList()))
        saved.usRetreatedAt?.let { root.put("usRetreatedAt", it) }

        val f = file(context)
        val tmp = File(f.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) {          // renameTo失敗時のフォールバック
            f.writeText(root.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    /** 保存内容ひとまとめ */
    data class Saved(
        val status: MarketStatus?,
        val position: Position?,
        val notifiedKeys: Set<String>,   // 通知済みキー（"deep:2026-07-14" 等）重複通知防止
        // ↓ 消灯通知のための「前回の点灯レベル」。画面の更新ボタンでは書き換えず、
        //   DailyCheckWorker（通知を出す側）だけが更新する。
        //   status を前回値として使うと、アプリを開いた瞬間の再取得で
        //   点灯→消灯の変化が上書きされて通知が出なくなるため、別に持つ
        val lastLevel: String? = null,          // LitLevel.name（DEEP/SHALLOW/CALM）
        val lastLitSignals: Set<String> = emptySet(),  // そのとき点灯していた条件キー
        // ↓ 撤退ライン（52週高値-35%）を割った日。null でなければ「撤退後ロック中」＝
        //   52週高値-3%まで回復するまで新規出動しない（検証32・36）。
        //   DailyCheckWorker が設定/解除する
        val retreatedAt: String? = null,

        // ─── 米国市場（v1.9追加。日本側と同じ設計を並行で持つ） ───
        val usStatus: UsMarketStatus? = null,
        val usPosition: Position? = null,      // Position は共用（基準値がS&P500になるだけ）
        val usLastLevel: String? = null,       // LitLevel.name（DEEP/CALM。SHALLOWは使わない）
        val usLastLitSignals: Set<String> = emptySet(),
        val usRetreatedAt: String? = null
    )
}
