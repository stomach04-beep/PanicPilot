package com.example.panicpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicpilot.data.MarketStatus
import com.example.panicpilot.data.TopixLadderStatus

// 点灯色（日本の相場慣習: 点灯=注意すべき状態なので赤系、待機=グレー）
private val FireRed = Color(0xFFE05B4C)
private val WarnAmber = Color(0xFFDBA13A)
private val CalmGray = Color(0xFF8A8A8A)
private val GoGreen = Color(0xFF3D9C5A)

/** メイン: 出動シグナルの状態表示 */
@Composable
fun SignalScreen(
    status: MarketStatus?,
    lastError: String?,
    tpx: TopixLadderStatus? = null   // 1306はしご（v2.2。日経レバ用シグナルとは別枠）
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (status == null) {
            Text(
                if (lastError != null) "データ取得に失敗しました:\n$lastError"
                else "データ取得中…",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        // ─── 総合判定バナー ───
        val (label, color, advice) = when {
            status.deep -> Triple(
                "🚨 出動", FireRed,
                "深い点灯。翌々日に予算の1/3で1回目。出動ナビタブで計画を作成"
            )
            status.sigShallow -> Triple(
                "⏳ 二番底待ち", WarnAmber,
                "浅い点灯（騰落<80）。急がず30〜40営業日待って二番底を確認（検証9）"
            )
            else -> Triple(
                "😴 待機", CalmGray,
                "平時。個別株は小型×低PBR×配当≥3%の土俵で。タイミング売買はしない"
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.height(6.dp))
                Text(advice, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // v2.3.5: 点灯の位置づけ（検証69・167）。点灯待ちで資金を寝かせる設計は棄却済み
        Text(
            "点灯は資金を寝かせて待つ理由ではなく、上乗せで買う合図です（検証69）。" +
                "過去10年の高い勝率は点灯条件ではなく上昇相場の手柄でした（検証167）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ─── シグナル3枚 ───
        SignalCard(
            title = "日経平均 52週高値からの下落",
            value = "%+.1f%%".format(status.dd52w * 100),
            threshold = "点灯: -15%以下",
            lit = status.sigDd,
            // v2.3.5: 「1M+4.5%勝率89%」は10年・比較相手なしの数字だった（検証167で再監査）
            note = "10年の「1M+4.5%・勝率89%」は比較相手なしの数字。2013年以降は何もない日に" +
                "買っても12M中央値+13.3%で、点灯（-15%か5日-8%）の超過は-2.7pt（検証167）"
        )
        SignalCard(
            title = "日経平均 5日間リターン（急落検知）",
            value = "%+.1f%%".format(status.ret5d * 100),
            threshold = "点灯: -8%以下",
            lit = status.sigFast,
            // v2.3.5: 「全勝・最も強い」は検証72で覆った（10年上昇相場の産物）
            note = "10年では3M・12Mとも全勝に見えたが、61年では12M超過-7.73pt・" +
                "プラセボ4.0%タイル＝10年上昇相場の産物だった（検証72）"
        )
        SignalCard(
            title = "25日騰落レシオ",
            value = if (status.adr25.isNaN()) "取得不可" else "%.1f".format(status.adr25),
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
            value = if (status.nikkeiVi.isNaN()) "取得不可" else "%.1f".format(status.nikkeiVi),
            threshold = "確信度 高: ${MarketStatus.TH_VI.toInt()}以上",
            lit = false,
            warn = status.viHigh,
            note = "30以上が重なった点灯は買値が底に近く12M+70%（検証33）。" +
                "出動の可否は変えず、出す金額を満額にするかの判断に使う"
        )

        // ─── 1306はしご（TOPIX・検証48 E60）。日経レバ用の点灯とは別枠のシグナル ───
        TopixLadderCard(tpx)

        // ─── 参考情報 ───
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("日経平均", "%,.0f円".format(status.indexLast))
                InfoRow("52週高値", "%,.0f円".format(status.high52w))
                // しきい値は MarketStatus 側の計算を使う（0.97 を直書きしない）
                // v2.3.5: -3%回復は売却の合図ではなく撤退ロックの解除条件（検証50・57）
                InfoRow("回復ライン（高値-3%・ロック解除）", "%,.0f円".format(status.exitLine))
                InfoRow("撤退ライン（高値-35%）", "%,.0f円".format(status.retreatLine))
                status.lev1458?.let { InfoRow("楽天日経レバ1458", "%,.0f円".format(it)) }
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
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 状態に応じた見出し
            val (head, headColor) = when {
                tpx == null -> "🪜 1306はしご（TOPIX）" to CalmGray
                tpx.timedOut -> "🪜 1306はしご：⏰タイムアウト（残り全額投入）" to FireRed
                tpx.clockRunning -> "🪜 1306はしご：時計進行中" to WarnAmber
                else -> "🪜 1306はしご：平常（-10%待ち）" to CalmGray
            }
            Text(head, fontWeight = FontWeight.SemiBold, color = headColor)
            if (tpx == null) {
                Text("1306のデータ未取得（↻で再取得）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            InfoRow("1306 現在値", "%,.1f円（52週高値から%+.1f%%）"
                .format(tpx.close, tpx.dd52w * 100))
            if (tpx.clockRunning) {
                InfoRow("時計", "${tpx.clockStartDate}開始 / ${tpx.elapsedBiz}営業日経過" +
                    (if (tpx.timedOut) "（60日超過）" else "（60日で残投入）"))
            } else {
                InfoRow("時計スタート（-10%）", "%,.1f円（まだ買わない）".format(tpx.lineClock))
            }
            // 3段のライン。時計進行中は到達済みかどうかを✅で示す
            fun rungLabel(hit: Boolean) = if (tpx.clockRunning && hit) "✅到達（1/3投入）" else ""
            InfoRow("1段目 -15%", "%,.1f円 %s".format(tpx.lineRung1, rungLabel(tpx.rung1Hit)))
            InfoRow("2段目 -20%", "%,.1f円 %s".format(tpx.lineRung2, rungLabel(tpx.rung2Hit)))
            InfoRow("3段目 -25%", "%,.1f円 %s".format(tpx.lineRung3, rungLabel(tpx.rung3Hit)))
            Text(
                "検証48 E60: 各段で予算の1/3、60営業日で未投入分を全額投入。買った玉は売らずに恒久保有" +
                    "（検証50: 12Mで売って回すと年率6% vs 恒久+撤退線9.4%。出口は日経-35%撤退線のみ）。" +
                    "日経レバの点灯とは別枠（検証49: 両者は補完）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "データ日付: ${tpx.dataDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SignalCard(
    title: String, value: String, threshold: String,
    lit: Boolean, warn: Boolean = false, note: String
) {
    val color = when { lit -> FireRed; warn -> WarnAmber; else -> CalmGray }
    Card(shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 点灯インジケータ（丸）
            Column(
                Modifier
                    .width(14.dp)
                    .height(14.dp)
                    .background(color, RoundedCornerShape(7.dp))
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(threshold, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = if (lit) FireRed else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
