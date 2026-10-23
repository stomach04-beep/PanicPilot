package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicpilot.data.MarketStatus
import com.example.panicpilot.data.TopixLadderStatus
import com.example.panicpilot.ui.kit.Fmt
import com.example.panicpilot.ui.kit.InfoToggle
import com.example.panicpilot.ui.kit.Panel
import com.example.panicpilot.ui.kit.PanelShape
import com.example.panicpilot.ui.kit.Sem
import com.example.panicpilot.ui.kit.Tone
import com.example.panicpilot.ui.kit.num

// 2026-10-23 表示の決まり UiKit: 点灯色（赤・橙・灰）の直書きをやめた。
// 点灯（買いの合図）は primary の濃淡、注意は Tone.CAUTION、赤（Tone.CRIT）は撤退ロック＝危険だけ

/** メイン: 出動シグナルの状態表示 */
@Composable
fun SignalScreen(
    status: MarketStatus?,
    lastError: String?,
    tpx: TopixLadderStatus? = null,  // 1306はしご（v2.2。日経レバ用シグナルとは別枠）
    retreatedAt: String? = null      // 撤退ライン割れの日。null でなければ出動ロック中（v2.3.11）
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (status == null) {
            NoDataPanel(lastError, "日経平均")
            return@Column
        }

        // ─── 総合判定バナー ───
        // 2026-10-23 表示の決まり UiKit: 色でなく「見た目の種類」で持ち、色は SignalBanner が決める
        val (label, look, advice) = when {
            // v2.3.11: ロック中は「出動」と出さない（通知は「⛔出動しません」なのに画面だけ出動と言っていた。米国画面と同じ並び）
            retreatedAt != null && status.deep -> Triple(
                "⛔ 点灯中だが出動禁止", BannerLook.DANGER,
                "撤退ロック中（${retreatedAt}に撤退ライン割れ）。52週高値-3%まで回復するまで新規出動しない"
            )
            status.deep -> Triple(
                "🚨 出動", BannerLook.STRONG,
                // v2.3.12: 「翌々日」は初期BT（v1.1）の名残。検証25以降と過去局面タブは「点灯の翌営業日に買い」
                "深い点灯。翌営業日に予算の1/3で1回目。出動ナビタブで計画を作成"
            )
            status.sigShallow -> Triple(
                "⏳ 二番底待ち", BannerLook.WEAK,
                "浅い点灯（騰落<80）。急がず30〜40営業日待って二番底を確認（検証9）"
            )
            else -> Triple(
                "😴 待機", BannerLook.CALM,
                "平時。個別株は小型×低PBR×配当≥3%の土俵で。タイミング売買はしない"
            )
        }
        SignalBanner(label, look, advice)

        // v2.3.5: 点灯の位置づけ（検証69・167）。点灯待ちで資金を寝かせる設計は棄却済み
        // 2026-10-23 表示の決まり UiKit: 常時2行出ていたのを「根拠を見る」に畳む（文言は不変）
        InfoToggle(title = "根拠を見る") {
            NoteText(
                "点灯は資金を寝かせて待つ理由ではなく、上乗せで買う合図です（検証69）。" +
                    "過去10年の高い勝率は点灯条件ではなく上昇相場の手柄でした（検証167）。"
            )
        }

        // ─── シグナル3枚 ───
        SignalCard(
            title = "日経平均 52週高値からの下落",
            value = Fmt.pct(status.dd52w * 100, 1),
            threshold = "点灯: -15%以下",
            lit = status.sigDd,
            // v2.3.5: 「1M+4.5%勝率89%」は10年・比較相手なしの数字だった（検証167で再監査）
            note = "10年の「1M+4.5%・勝率89%」は比較相手なしの数字。2013年以降は何もない日に" +
                "買っても12M中央値+13.3%で、点灯（-15%か5日-8%）の超過は-2.7pt（検証167）"
        )
        SignalCard(
            title = "日経平均 5日間リターン（急落検知）",
            value = Fmt.pct(status.ret5d * 100, 1),
            threshold = "点灯: -8%以下",
            lit = status.sigFast,
            // v2.3.5: 「全勝・最も強い」は検証72で覆った（10年上昇相場の産物）
            note = "10年では3M・12Mとも全勝に見えたが、61年では12M超過-7.73pt・" +
                "プラセボ4.0%タイル＝10年上昇相場の産物だった（検証72）"
        )
        SignalCard(
            title = "25日騰落レシオ",
            value = if (status.adr25.isNaN()) "取得不可" else Fmt.num(status.adr25, 1),
            threshold = "点灯: 70未満（80未満=注意）",
            lit = status.sigAdr,
            warn = status.sigShallow && !status.sigAdr,
            // v2.3.5: 「10年全勝」は比較相手なしの10年の数字（検証167）
            note = "「70割れは10年全勝」も比較相手なしの10年の数字（検証167）。" +
                "80割れは急がず二番底待ち（検証9）"
        )

        // 日経VIは点灯条件ではなく「確信度」の材料（検証33: 出動可否は変えず金額の厚みだけ）
        SignalCard(
            title = "日経VI（恐怖指数）※点灯条件ではありません",
            value = if (status.nikkeiVi.isNaN()) "取得不可" else Fmt.num(status.nikkeiVi, 1),
            threshold = "確信度 高: ${MarketStatus.TH_VI.toInt()}以上",
            lit = false,
            warn = status.viHigh,
            warnLabel = "確信度 高",
            note = "30以上が重なった点灯は買値が底に近く12M+70%（検証33）。" +
                "出動の可否は変えず、出す金額を満額にするかの判断に使う"
        )

        // ─── 1306はしご（TOPIX・検証48 E60）。日経レバ用の点灯とは別枠のシグナル ───
        TopixLadderCard(tpx)

        // ─── 参考情報 ───
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("日経平均", Fmt.yen(status.indexLast))
                InfoRow("52週高値", Fmt.yen(status.high52w))
                // しきい値は MarketStatus 側の計算を使う（0.97 を直書きしない）
                // v2.3.5: -3%回復は売却の合図ではなく撤退ロックの解除条件（検証50・57）
                InfoRow("回復ライン（高値-3%・ロック解除）", Fmt.yen(status.exitLine))
                InfoRow("撤退ライン（高値-35%）", Fmt.yen(status.retreatLine))
                status.lev1458?.let { InfoRow("楽天日経レバ1458", Fmt.yen(it)) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "データ日付: ${status.dataDate} ／ 取得: ${status.fetchedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 1306（TOPIX ETF）E60はしごカード（v2.2・検証48）。
 * 日経レバの点灯（PanicPilot 3条件）とは独立した「恒久はしご」のシグナル:
 *  -10%割れで60営業日の時計スタート → -15/-20/-25%で各1/3 → 60営業日で残投入
 */
@Composable
private fun TopixLadderCard(tpx: TopixLadderStatus?) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 状態に応じた見出し
            // 2026-10-23 表示の決まり UiKit: タイムアウト（残り全額投入）は買いの指示なので赤でなく primary、
            // 時計進行中は注意色、平常は補足色
            val (head, headColor) = when {
                tpx == null -> "🪜 1306はしご（TOPIX）" to MaterialTheme.colorScheme.onSurfaceVariant
                tpx.timedOut -> "🪜 1306はしご：⏰タイムアウト（残り全額投入）" to MaterialTheme.colorScheme.primary
                tpx.clockRunning -> "🪜 1306はしご：時計進行中" to Sem.fg(Tone.CAUTION)
                else -> "🪜 1306はしご：平常（-10%待ち）" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(head, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = headColor)
            if (tpx == null) {
                Text("1306のデータ未取得（右上の更新ボタンで再取得）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            InfoRow("1306 現在値", "${Fmt.withUnit(tpx.close, 1, "円")}（52週高値から${Fmt.pct(tpx.dd52w * 100, 1)}）")
            if (tpx.clockRunning) {
                InfoRow("時計", "${tpx.clockStartDate}開始 / ${tpx.elapsedBiz}営業日経過" +
                    (if (tpx.timedOut) "（60日超過）" else "（60日で残投入）"))
            } else {
                InfoRow("時計スタート（-10%）", "${Fmt.withUnit(tpx.lineClock, 1, "円")}（まだ買わない）")
            }
            // 3段のライン。時計進行中は到達済みかどうかを✅で示す
            fun rungLabel(hit: Boolean) = if (tpx.clockRunning && hit) " ✅到達（1/3投入）" else ""
            InfoRow("1段目 -15%", Fmt.withUnit(tpx.lineRung1, 1, "円") + rungLabel(tpx.rung1Hit))
            InfoRow("2段目 -20%", Fmt.withUnit(tpx.lineRung2, 1, "円") + rungLabel(tpx.rung2Hit))
            InfoRow("3段目 -25%", Fmt.withUnit(tpx.lineRung3, 1, "円") + rungLabel(tpx.rung3Hit))
            // 2026-10-23 表示の決まり UiKit: 検証の説明は「根拠を見る」に畳む（文言は不変）
            InfoToggle(title = "根拠を見る") {
                NoteText(
                    "検証48 E60: 各段で予算の1/3、60営業日で未投入分を全額投入。買った玉は売らずに恒久保有" +
                        "（検証50: 12Mで売って回すと年率6% vs 恒久+撤退線9.4%。出口は日経-35%撤退線のみ）。" +
                        "日経レバの点灯とは別枠（検証49: 両者は補完）"
                )
            }
            Text(
                "データ日付: ${tpx.dataDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── 日米のシグナル画面で共用する部品（2026-10-23 表示の決まり UiKit。以前は米国側に同型の複製があった） ───

/** 総合判定バナーの見た目の種類（色は SignalBanner だけが決める） */
internal enum class BannerLook {
    DANGER,  // 撤退ロック中＝危険（赤はここだけ）
    STRONG,  // 深い点灯＝買いの合図（primary で塗りつぶし）
    WEAK,    // 浅い点灯＝弱い合図（primaryContainer の薄い地）
    CALM     // 平時（主役の面の色のまま）
}

/** 総合判定バナー。1画面の主役なので面を目立たせ、状態の言葉を大きく出す */
@Composable
internal fun SignalBanner(label: String, look: BannerLook, advice: String) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (look) {
        BannerLook.DANGER -> Sem.bg(Tone.CRIT) to Sem.fg(Tone.CRIT)
        BannerLook.STRONG -> cs.primary to cs.onPrimary
        BannerLook.WEAK -> cs.primaryContainer to cs.onPrimaryContainer
        BannerLook.CALM -> cs.surfaceVariant to cs.onSurface
    }
    // 説明文は、塗りつぶし（STRONG）と薄い地（WEAK）では地に合わせた文字色、それ以外は本文の色
    val adviceColor = when (look) {
        BannerLook.STRONG -> cs.onPrimary
        BannerLook.WEAK -> cs.onPrimaryContainer
        else -> cs.onSurface
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = PanelShape, color = bg) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = fg)
            Spacer(Modifier.height(6.dp))
            Text(advice, style = MaterialTheme.typography.bodyMedium, color = adviceColor)
        }
    }
}

/**
 * データがまだ無いときの面。取得に失敗したときだけ危険色で理由を出し、
 * そうでなければ取得中であることと、自分で取り直す方法を書く
 */
@Composable
internal fun NoDataPanel(lastError: String?, what: String) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lastError != null) {
                Text("${what}のデータ取得に失敗しました",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Sem.fg(Tone.CRIT))
                Text(lastError, style = MaterialTheme.typography.bodyMedium)
                NoteText("通信状態を確かめて、右上の更新ボタンで取り直してください。")
            } else {
                Text("${what}のデータを取得しています…",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                NoteText("初回は数秒かかります。終わらないときは右上の更新ボタンで取り直してください。")
            }
        }
    }
}

/**
 * シグナル1枚。ふだんはタイトル／しきい値／値だけを出し、検証の注記は「根拠を見る」に畳む。
 *  ・lit  … 点灯（買いの合図）＝薄い primary の地＋値を primary で
 *  ・warn … 注意（騰落80未満・VI高）＝値と札を注意色で
 */
@Composable
internal fun SignalCard(
    title: String, value: String, threshold: String,
    lit: Boolean, warn: Boolean = false, note: String,
    warnLabel: String = "注意"
) {
    val cs = MaterialTheme.colorScheme
    val accent: Color = when { lit -> cs.primary; warn -> Sem.fg(Tone.CAUTION); else -> cs.onSurface }
    val stateLabel: String? = when { lit -> "点灯"; warn -> warnLabel; else -> null }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = if (lit) cs.primaryContainer else cs.surface
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左：タイトルとしきい値（長くても値を押しつぶさないよう weight を付ける）
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        if (stateLabel != null) "$stateLabel ・ $threshold" else threshold,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (stateLabel != null) FontWeight.Bold else null,
                        color = if (stateLabel != null) accent else cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                // 右：値（主役。等幅の数字・1行）
                Text(
                    value, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                    style = TextStyle.Default.num(), color = accent,
                    maxLines = 1, softWrap = false
                )
            }
            InfoToggle(title = "根拠を見る") {
                Column(Modifier.padding(bottom = 12.dp)) { NoteText(note) }
            }
        }
    }
}

/**
 * ラベルと値の1行。ラベルは固定幅（2行まで折り返し）、値は残りの幅で右寄せ。
 * 2026-10-23 表示の決まり UiKit: 以前はラベル側だけ weight(1f) で、値が長いとラベルが1文字ずつ縦に折れていた
 */
@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.width(136.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium.num(),
            fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

/** 補足の文（12sp・補足色）。畳んだ説明の中身に使う */
@Composable
internal fun NoteText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
