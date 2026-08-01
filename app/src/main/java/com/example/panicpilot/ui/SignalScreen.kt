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

// 点灯色（日本の相場慣習: 点灯=注意すべき状態なので赤系、待機=グレー）
private val FireRed = Color(0xFFE05B4C)
private val WarnAmber = Color(0xFFDBA13A)
private val CalmGray = Color(0xFF8A8A8A)
private val GoGreen = Color(0xFF3D9C5A)

/** メイン: 出動シグナルの状態表示 */
@Composable
fun SignalScreen(status: MarketStatus?, lastError: String?) {
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

        // ─── シグナル3枚 ───
        SignalCard(
            title = "日経平均 52週高値からの下落",
            value = "%+.1f%%".format(status.dd52w * 100),
            threshold = "点灯: -15%以下",
            lit = status.sigDd,
            note = "点灯後1M+4.5%勝率89%。初週が勝負、12M保有なら60日遅れも可"
        )
        SignalCard(
            title = "日経平均 5日間リターン（急落検知）",
            value = "%+.1f%%".format(status.ret5d * 100),
            threshold = "点灯: -8%以下",
            lit = status.sigFast,
            note = "5日-8%急落は3M・12Mとも全勝（検証18）。最も簡単で強いシグナル"
        )
        SignalCard(
            title = "25日騰落レシオ",
            value = if (status.adr25.isNaN()) "取得不可" else "%.1f".format(status.adr25),
            threshold = "点灯: 70未満（80未満=注意）",
            lit = status.sigAdr,
            warn = status.sigShallow && !status.sigAdr,
            note = "70割れは10年全勝。80割れは急がず二番底待ちが正解"
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

        // ─── 参考情報 ───
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("日経平均", "%,.0f円".format(status.indexLast))
                InfoRow("52週高値", "%,.0f円".format(status.high52w))
                // しきい値は MarketStatus 側の計算を使う（0.97 を直書きしない）
                InfoRow("出口ライン（高値-3%）", "%,.0f円".format(status.exitLine))
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
