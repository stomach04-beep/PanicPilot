package com.example.panicpilot

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.panicpilot.data.LitLevel
import com.example.panicpilot.data.MarketFetcher
import com.example.panicpilot.data.MarketFetcherUs
import com.example.panicpilot.data.MarketStatus
import com.example.panicpilot.data.Storage
import com.example.panicpilot.data.UsMarketStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * 1日1回（引け後）に市場データを取得して判定するWorker。
 * 通知は「同じデータ日付×同じ種類」で1回だけ（notifiedKeysで重複防止。
 * 初回取得で既に点灯している場合も通知する＝出動アプリなので望ましい動作）
 *
 * 点灯だけでなく「消灯」（前回より点灯レベルが下がった日）も通知する。
 * 判定は保存した lastLevel（前回この Worker が見たレベル）との比較で行う。
 * 保存済みの status を前回値に使うと、アプリを開いた時の再取得で上書きされ、
 * 点灯→消灯の変化が消えて通知が出なくなるため。
 *
 * 取得失敗の無言死対策:
 * 「シグナル無し（平穏）」と「取得失敗」を区別できるように、
 * 連続失敗日数をSharedPreferencesでカウントし、2日連続で失敗したら警告通知を出す。
 * 暴落時こそデータ源が高負荷になるアプリなので、この可視化が生命線。
 */
class DailyCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // ─── 米国市場のチェック（v1.9） ───
        // 日本側より先に、独立に 取得→通知→保存 まで完結させる。
        // 米国が失敗しても日本側は続行し、日本が失敗（下のretry/failure）しても
        // 米国の通知と保存は済んでいる＝どちらかのデータ源障害がもう片方を殺さない
        checkUsMarket(ctx)

        val status = try {
            MarketFetcher.fetch()
        } catch (e: Exception) {
            // 通信失敗はリトライ（WorkManagerのバックオフに任せる。最大3回）
            if (runAttemptCount < 3) return Result.retry()
            // リトライを使い切った＝この日の取得は失敗確定。連続失敗を記録する
            val (streak, counted) = recordFetchFailure(ctx)
            // 2日連続で失敗したら警告通知（同じ日に二重通知しないよう counted でガード）
            if (counted && streak >= FAIL_NOTIFY_THRESHOLD) {
                NotificationHelper.notify(
                    ctx, NOTIF_ID_FETCH_FAIL,
                    "⚠️ 市場データの取得に失敗しています",
                    "市場データの取得に失敗しています（${streak}日連続）。" +
                        "データ源の障害か形式変更の可能性"
                )
            }
            return Result.failure()
        }

        // 取得成功 → 連続失敗カウンタをリセット
        resetFetchFailure(ctx)

        val saved = Storage.load(ctx)
        val notified = saved.notifiedKeys.toMutableSet()
        val pos = saved.position

        // ─── 通知判定 ───
        fun fireOnce(key: String, id: Int, title: String, text: String) {
            val fullKey = "$key:${status.dataDate}"
            if (fullKey !in notified) {
                NotificationHelper.notify(ctx, id, title, text)
                notified.add(fullKey)
            }
        }

        // ─── 消灯の判定（点灯していたものが消えた瞬間を知らせる） ───
        // 比較するのは「前回この Worker が通知判定に使ったレベル」であって、
        // 保存済みの status ではない（画面の更新ボタンで status は書き換わるため）
        val prevLevel = LitLevel.fromName(saved.lastLevel)
        val nowLevel = status.level
        if (nowLevel.rank < prevLevel.rank) {
            // 消えた条件（前回点灯していて今回は点いていないもの）
            val turnedOff = saved.lastLitSignals
                .filter { it !in status.litSignalKeys }
                .joinToString("・") { MarketStatus.signalLabel(it) }
            // いまの3指標（消灯後の水準を1通知で把握できるように併記）
            val nowValues = "52週${fmtPct(status.dd52w)} / 5日${fmtPct(status.ret5d)} / " +
                "騰落レシオ${"%.1f".format(status.adr25)}"
            // 保有中なら「消灯＝売り」ではないことを明記する（出口は52週高値-3%回復のまま）
            val holdNote = if (pos != null) {
                "保有中の分は消灯では売らない。出口は52週高値-3%まで回復したときのまま（検証17）"
            } else {
                "次の点灯まで待機。追いかけて買わない"
            }
            when {
                // 深い点灯 → 浅い点灯（騰落レシオ70〜80）。まだ注意レベルは続いている
                prevLevel == LitLevel.DEEP && nowLevel == LitLevel.SHALLOW -> fireOnce(
                    "off_deep", NOTIF_ID_LIGHTS_OFF, "🔵 出動シグナル消灯（浅い点灯は継続）",
                    "消えた条件: ${turnedOff.ifEmpty { "出動条件" }}。現在 $nowValues。$holdNote"
                )
                // 深い点灯 → 平常
                prevLevel == LitLevel.DEEP -> fireOnce(
                    "off_deep", NOTIF_ID_LIGHTS_OFF, "🔵 出動シグナル消灯",
                    "消えた条件: ${turnedOff.ifEmpty { "出動条件" }}。現在 $nowValues。$holdNote"
                )
                // 浅い点灯 → 平常
                else -> fireOnce(
                    "off_shallow", NOTIF_ID_LIGHTS_OFF, "✅ 浅い点灯も消灯（平常に戻りました）",
                    "騰落レシオが80以上に回復。現在 $nowValues。$holdNote"
                )
            }
        }

        // ─── 撤退ライン（52週高値-35%割れ）とロックの管理 ───
        // 検証32〜36: 出口ルールだけだと弱気相場で3年握らされて-70%になる。
        // 指数が-35%を割ったら全売却し、-3%まで回復するまで新規出動しない。
        // 過去10年では一度も発動せず、発動したのは1990/2000/2007の3回だけ
        var retreatedAt = saved.retreatedAt
        if (status.sigRetreat) {
            if (retreatedAt == null) {
                retreatedAt = status.dataDate
                fireOnce(
                    "retreat", NOTIF_ID_RETREAT, "🛑 撤退シグナル（52週高値-35%割れ）",
                    "日経平均 ${fmt(status.indexLast)}円が撤退ライン" +
                        "${fmt(status.retreatLine)}円を割りました。" +
                        (if (pos != null) "保有分は全売却。" else "") +
                        "52週高値-3%（${fmt(status.exitLine)}円）まで回復するまで新規出動しません"
                )
            }
        } else if (retreatedAt != null && status.recovered) {
            retreatedAt = null       // 52週高値-3%まで回復＝ロック解除
            fireOnce(
                "unlock", NOTIF_ID_RETREAT, "🔓 撤退ロック解除",
                "日経平均が52週高値-3%まで回復しました。次の点灯から通常どおり出動できます"
            )
        }
        val lockedOut = retreatedAt != null

        if (status.deep) {
            val reasons = buildList {
                if (status.sigDd) add("52週高値から${fmtPct(status.dd52w)}")
                if (status.sigFast) add("5日で${fmtPct(status.ret5d)}の急落")
                if (status.sigAdr) add("騰落レシオ${"%.1f".format(status.adr25)}")
            }.joinToString(" / ")
            if (lockedOut) {
                // 点灯しても出動しない。弱気相場の途中で買い増すと負けを重ねる（検証36）
                fireOnce(
                    "deep_locked", 1, "⛔ 点灯したが出動しません（撤退ロック中）",
                    "$reasons。${retreatedAt}に撤退ライン割れ。" +
                        "52週高値-3%（${fmt(status.exitLine)}円）まで回復するまで待機"
                )
            } else {
                // 確信度（日経VI）で「満額か半分か」まで通知本文に入れる（検証33）
                val conf = if (status.viHigh) {
                    "日経VI${"%.1f".format(status.nikkeiVi)}＝確信度高。予算の満額で"
                } else {
                    "確信度は標準。予算の半分に抑えて"
                }
                fireOnce(
                    "deep", 1, "🚨 出動シグナル点灯",
                    "$reasons。$conf、翌々日に1/3を1回目のエントリー（詳細はアプリで）"
                )
            }
        } else if (status.sigShallow) {
            fireOnce(
                "shallow", 2, "⚠ 浅い点灯（騰落レシオ<80）",
                "急がない。30〜40営業日待って二番底を確認してから（検証9）"
            )
        }

        // ─── ポジション保有中: 買い増し・出口の通知 ───
        if (pos != null) {
            if (!pos.fill2Done && status.indexLast <= pos.trigger2) {
                fireOnce(
                    "fill2", 3, "📉 2回目の買い増し水準に到達",
                    "日経平均 ${fmt(status.indexLast)}円 ≤ 基準-5%（${fmt(pos.trigger2)}円）。予算の1/3を追加投入"
                )
            }
            if (!pos.fill3Done && status.indexLast <= pos.trigger3) {
                fireOnce(
                    "fill3", 4, "📉 3回目の買い増し水準に到達",
                    "日経平均 ${fmt(status.indexLast)}円 ≤ 基準-10%（${fmt(pos.trigger3)}円）。残りの予算を投入"
                )
            }
            // 出口: 52週高値-3%以内まで回復（検証17: 8回全勝の出口ルール）
            if (status.recovered) {
                fireOnce(
                    "exit", 5, "🏁 出口シグナル（高値圏まで回復）",
                    "日経平均が52週高値-3%以内に回復。全売却のタイミング（検証17: 8回全勝）"
                )
            }
        }

        // 通知キーの肥大防止: 30件を超えたら当日データ分だけ残す
        // （米国キーは日付が違うので、米国側の当日データ分も一緒に残す）
        val usDate = saved.usStatus?.dataDate
        val keep = if (notified.size <= 30) notified
                   else notified.filter {
                       it.endsWith(status.dataDate) || (usDate != null && it.endsWith(usDate))
                   }.toMutableSet()

        // 今回のレベルを「前回値」として記録（次回の消灯判定の材料）＋撤退ロックの状態。
        // 米国側のフィールドは checkUsMarket が保存済みの値（saved に読み込み済み）を
        // copy でそのまま持ち越す＝ここで消さない
        Storage.save(
            ctx,
            saved.copy(
                status = status, position = pos, notifiedKeys = keep,
                lastLevel = nowLevel.name, lastLitSignals = status.litSignalKeys,
                retreatedAt = retreatedAt
            )
        )
        return Result.success()
    }

    /**
     * 米国市場のチェック（取得→通知→保存まで独立に完結）。
     * 点灯条件は52週DD-15% / 5日-8%の2本（検証45）。VIXは確信度＝金額の厚みだけ（検証46）。
     * 撤退線-35%と出口-3%は日本側と同じ設計（検証32・46）
     */
    private fun checkUsMarket(ctx: Context) {
        val us: UsMarketStatus = try {
            MarketFetcherUs.fetch()
        } catch (e: Exception) {
            // 失敗の可視化（日本側と同じ2日連続ルール。カウンタは別キーで独立）
            val (streak, counted) = recordFetchFailure(ctx, US_FAIL_SUFFIX)
            if (counted && streak >= FAIL_NOTIFY_THRESHOLD) {
                NotificationHelper.notify(
                    ctx, NOTIF_ID_US_FETCH_FAIL,
                    "⚠️ 米国市場データの取得に失敗しています",
                    "米国市場データの取得に失敗しています（${streak}日連続）。" +
                        "Yahoo Financeの障害か形式変更の可能性"
                )
            }
            return   // 米国失敗でも日本側の処理は続行する
        }
        resetFetchFailure(ctx, US_FAIL_SUFFIX)

        val saved = Storage.load(ctx)
        val notified = saved.notifiedKeys.toMutableSet()
        val pos = saved.usPosition

        fun fireOnce(key: String, id: Int, title: String, text: String) {
            val fullKey = "$key:${us.dataDate}"
            if (fullKey !in notified) {
                NotificationHelper.notify(ctx, id, title, text)
                notified.add(fullKey)
            }
        }

        // ─── 消灯（米国はDEEP/CALMの2値。浅い点灯は無い） ───
        val prevLevel = LitLevel.fromName(saved.usLastLevel)
        if (us.level.rank < prevLevel.rank) {
            val turnedOff = saved.usLastLitSignals
                .filter { it !in us.litSignalKeys }
                .joinToString("・") { UsMarketStatus.signalLabel(it) }
            val nowValues = "52週${fmtPct(us.dd52w)} / 5日${fmtPct(us.ret5d)} / " +
                "VIX${"%.1f".format(us.vix)}"
            val holdNote = if (pos != null) {
                "保有中の分は消灯では売らない。出口は52週高値-3%回復のまま"
            } else {
                "次の点灯まで待機。追いかけて買わない"
            }
            fireOnce(
                "us_off_deep", NOTIF_ID_US_LIGHTS_OFF, "🔵 米国：出動シグナル消灯",
                "消えた条件: ${turnedOff.ifEmpty { "出動条件" }}。現在 $nowValues。$holdNote"
            )
        }

        // ─── 撤退ライン（S&P500が52週高値-35%割れ）とロックの管理 ───
        // 検証46 Part3: 合成3倍の最悪-84.6%→-63.4%、2008-06の-83%はロックで丸ごと回避
        var usRetreatedAt = saved.usRetreatedAt
        if (us.sigRetreat) {
            if (usRetreatedAt == null) {
                usRetreatedAt = us.dataDate
                fireOnce(
                    "us_retreat", NOTIF_ID_US_RETREAT, "🛑 米国：撤退シグナル（52週高値-35%割れ）",
                    "S&P500 ${fmt(us.indexLast)}が撤退ライン${fmt(us.retreatLine)}を割りました。" +
                        (if (pos != null) "SPXLは全売却。" else "") +
                        "52週高値-3%（${fmt(us.exitLine)}）まで回復するまで新規出動しません"
                )
            }
        } else if (usRetreatedAt != null && us.recovered) {
            usRetreatedAt = null
            fireOnce(
                "us_unlock", NOTIF_ID_US_RETREAT, "🔓 米国：撤退ロック解除",
                "S&P500が52週高値-3%まで回復しました。次の点灯から通常どおり出動できます"
            )
        }

        if (us.deep) {
            val reasons = buildList {
                if (us.sigDd) add("52週高値から${fmtPct(us.dd52w)}")
                if (us.sigFast) add("5日で${fmtPct(us.ret5d)}の急落")
            }.joinToString(" / ")
            if (usRetreatedAt != null) {
                fireOnce(
                    "us_deep_locked", NOTIF_ID_US_LIT, "⛔ 米国：点灯したが出動しません（撤退ロック中）",
                    "$reasons。${usRetreatedAt}に撤退ライン割れ。" +
                        "52週高値-3%（${fmt(us.exitLine)}）まで回復するまで待機"
                )
            } else {
                // 確信度（VIX）。検証46: VIX<30の点灯は2008年型の危険信号でもある＝半分に抑える
                val conf = if (us.vixHigh) {
                    "VIX${"%.1f".format(us.vix)}＝確信度高。予算の満額で"
                } else {
                    "VIXが30未満＝確信度は標準。予算の半分に抑えて"
                }
                fireOnce(
                    "us_deep", NOTIF_ID_US_LIT, "🚨 米国：出動シグナル点灯（S&P500）",
                    "$reasons。$conf、SPXLを3分割で（詳細はアプリの米国タブで）"
                )
            }
        }

        // ─── ポジション保有中: 買い増し・出口 ───
        if (pos != null) {
            if (!pos.fill2Done && us.indexLast <= pos.trigger2) {
                fireOnce(
                    "us_fill2", NOTIF_ID_US_FILL2, "📉 米国：2回目の買い増し水準に到達",
                    "S&P500 ${fmt(us.indexLast)} ≤ 基準-5%（${fmt(pos.trigger2)}）。予算の1/3を追加投入"
                )
            }
            if (!pos.fill3Done && us.indexLast <= pos.trigger3) {
                fireOnce(
                    "us_fill3", NOTIF_ID_US_FILL3, "📉 米国：3回目の買い増し水準に到達",
                    "S&P500 ${fmt(us.indexLast)} ≤ 基準-10%（${fmt(pos.trigger3)}）。残りの予算を投入"
                )
            }
            if (us.recovered) {
                fireOnce(
                    "us_exit", NOTIF_ID_US_EXIT, "🏁 米国：出口シグナル（高値圏まで回復）",
                    "S&P500が52週高値-3%以内に回復。SPXL全売却のタイミング"
                )
            }
        }

        // 米国分だけを保存（日本側フィールドは読み込んだ値をそのまま持ち越す）
        Storage.save(
            ctx,
            saved.copy(
                usStatus = us, notifiedKeys = notified,
                usLastLevel = us.level.name, usLastLitSignals = us.litSignalKeys,
                usRetreatedAt = usRetreatedAt
            )
        )
    }

    // ─── 連続失敗カウンタ（SharedPreferences） ───

    /**
     * 取得失敗を記録し、(連続失敗日数, 今回カウントを進めたか) を返す。
     * 同じ日に複数回失敗しても1日分としか数えない（日付でガード）。
     * suffix="" が日本市場、US_FAIL_SUFFIX が米国市場（カウンタは別持ち）
     */
    private fun recordFetchFailure(ctx: Context, suffix: String = ""): Pair<Int, Boolean> {
        val prefs = ctx.getSharedPreferences(PREFS_HEALTH, Context.MODE_PRIVATE)
        val today = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString()
        val lastFailDate = prefs.getString(KEY_LAST_FAIL_DATE + suffix, null)
        var streak = prefs.getInt(KEY_FAIL_STREAK + suffix, 0)
        if (lastFailDate == today) {
            return streak to false   // 今日はカウント済み
        }
        streak += 1
        prefs.edit()
            .putString(KEY_LAST_FAIL_DATE + suffix, today)
            .putInt(KEY_FAIL_STREAK + suffix, streak)
            .apply()
        return streak to true
    }

    /** 取得成功時に連続失敗カウンタをリセットする */
    private fun resetFetchFailure(ctx: Context, suffix: String = "") {
        val prefs = ctx.getSharedPreferences(PREFS_HEALTH, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_FAIL_STREAK + suffix, 0) != 0 ||
            prefs.getString(KEY_LAST_FAIL_DATE + suffix, null) != null
        ) {
            prefs.edit()
                .putInt(KEY_FAIL_STREAK + suffix, 0)
                .remove(KEY_LAST_FAIL_DATE + suffix)
                .apply()
        }
    }

    companion object {
        // 取得失敗の可視化まわり
        private const val PREFS_HEALTH = "panicpilot_health"       // 健全性チェック用Prefs
        private const val KEY_FAIL_STREAK = "fetch_fail_streak"    // 連続失敗日数
        private const val KEY_LAST_FAIL_DATE = "fetch_last_fail_date"  // 最後に失敗を数えた日
        private const val FAIL_NOTIFY_THRESHOLD = 2                // この日数連続で失敗したら通知
        private const val NOTIF_ID_FETCH_FAIL = 6                  // 通知ID（1〜5はシグナル系で使用済み）
        private const val NOTIF_ID_LIGHTS_OFF = 7                  // 消灯通知の通知ID
        private const val NOTIF_ID_RETREAT = 8                     // 撤退シグナル／ロック解除

        // ─── 米国市場（v1.9。日本側の1〜8と衝突しないよう100番台） ───
        private const val US_FAIL_SUFFIX = "_us"                   // 失敗カウンタのキー接尾辞
        private const val NOTIF_ID_US_LIT = 101                    // 点灯／ロック中の点灯
        private const val NOTIF_ID_US_FILL2 = 103                  // 2回目の買い増し
        private const val NOTIF_ID_US_FILL3 = 104                  // 3回目の買い増し
        private const val NOTIF_ID_US_EXIT = 105                   // 出口
        private const val NOTIF_ID_US_FETCH_FAIL = 106             // 取得失敗
        private const val NOTIF_ID_US_LIGHTS_OFF = 107             // 消灯
        private const val NOTIF_ID_US_RETREAT = 108                // 撤退／ロック解除

        fun fmt(v: Double) = "%,.1f".format(v)
        fun fmtPct(v: Double) = "%+.1f%%".format(v * 100)
    }
}
