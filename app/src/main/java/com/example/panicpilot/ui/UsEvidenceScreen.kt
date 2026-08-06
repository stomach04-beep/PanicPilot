package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.panicpilot.data.UsCrashHistoryData

/**
 * 米国・根拠タブ: 検証45・46（S&P500 1990-2026・実在ETF実勢価格）の実績とルール。
 * 実績の数値は UsCrashHistoryData.kt（スクリプト自動生成）から導出し、ここには直書きしない
 */
@Composable
fun UsEvidenceScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("米国版ルールの根拠", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(
            "S&P500（1990〜2026年）とVIX、実在ETF（SPY/SSO/SPXL）の実勢価格で検証した" +
            "ルールだけを実装しています（検証45・46）。日本版と同じしきい値が米国でも機能します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 実績サマリー（数値は自動生成データから。過去局面タブが本体）
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val eps = UsCrashHistoryData.episodes
                val spxl12 = eps.mapNotNull { it.spxl12m }
                val spxlWins = spxl12.count { it > 0 }
                Text("点灯でSPXL（3倍）を買った実績（12ヶ月保有）", fontWeight = FontWeight.SemiBold)
                UsSummaryLine("点灯した局面", "${eps.size}回（1990年以降36年）")
                UsSummaryLine("うちSPXLで計測可能", "${spxl12.size}回（2008年11月上場以降）")
                UsSummaryLine("12ヶ月後の成績", "${spxlWins}勝${spxl12.size - spxlWins}敗")
                UsSummaryLine("12ヶ月後の中央値",
                    String.format("%+.1f%%", UsCrashHistoryData.SPXL_12M_MED))
                UsSummaryLine("参考: 1倍SPYの中央値",
                    String.format("%+.1f%%（n=%d）", UsCrashHistoryData.SPY_12M_MED,
                        UsCrashHistoryData.SPY_12M_N))
                Text(
                    "局面ごとの成績は「過去局面」タブで見られます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("検証済みルール", fontWeight = FontWeight.SemiBold)
                UsRule("点灯は「52週高値-15%」か「5日-8%」の2本だけ",
                    "日本版の騰落レシオに相当する無料の日次データは米国に無い。代わりにVIXを" +
                    "OR条件で足すと点灯が薄まり超過リターンが悪化（SPY 12M -7.3pt）するため、" +
                    "2条件のままが最良（検証45）")
                UsRule("買う対象はSPXL（3倍）。1倍では出動する意味がない",
                    "SPY（1倍）は12ヶ月の超過エッジがゼロ（-0.6pt）＝ただ持っているのと同じ。" +
                    "エッジはレバ型に集中し、SPXLは12M平均+62.7%・超過+24.8pt・勝率88%（検証45）。" +
                    "楽天証券で現物購入可")
                UsRule("VIX≥30なら満額、30未満なら予算の半分（点灯条件ではない）",
                    "点灯日VIX≥30の局面は12M平均+34.9%・勝率85%。VIX<30は-17.8%・勝率33%で、" +
                    "2008年の2大災害（VIX27/23）は両方VIX<30だった＝金額を絞る判断が防御を兼ねる（検証46）")
                UsRule("3分割の価格分割エントリー（基準-5%/-10%で買い増し）",
                    "日本版と同じ設計。②③が60営業日以内に来なければ残りを投入")
                UsRule("出口はS&P500が52週高値-3%回復",
                    "日本版と同じ。売り時を当てにいかず機械的ルールで（検証17・40）")
                UsRule("撤退はS&P500が52週高値-35%割れ（指数ベース）＋回復まで再出動禁止",
                    "検証32で日米それぞれ独立に最良となった撤退線。3倍レバに付けると" +
                    "最悪-84.6%→-63.4%、平均+38.8%→+48.4%。2008年6月型の-83%はロックで丸ごと回避（検証46）")
                UsRule("為替ヘッジはしない",
                    "ヘッジなしはヘッジありに平均-2.4pt劣るが、ヘッジコスト（約4〜5%/年）を引くと" +
                    "ほぼ相殺。ヘッジ付き投信は約定が翌営業日になる欠点もある（検証46）")
                UsRule("買い付けは点灯通知の当日夜の寄り",
                    "通知は夕方に届き、米国市場は日本時間22:30/23:30に開く＝その日の夜に注文できる")
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚠ 限界と注意", fontWeight = FontWeight.SemiBold)
                Text(
                    "・SPXLの好成績（勝率88%・最悪-8.5%）は2008年11月上場＝リーマンショックを" +
                    "経験していない標本。合成3倍で2008年を再現すると-84.6%（撤退線ありで-63.4%）。" +
                    "破滅級のリスクは残るので、金額は日本版より控えめに\n" +
                    "・3倍レバは-80%級の下落で償還・消滅する可能性がある\n" +
                    "・米国の点灯は約2〜3年に1回と寡黙。待つのが仕事\n" +
                    "・円建てでは為替も動く（パニックは円高と重なりやすく、買値は円換算でさらに安くなる）\n" +
                    "・投資判断は自己責任で。本アプリは記録と参考情報の提供のみ",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun UsSummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UsRule(title: String, detail: String) {
    Column {
        Text("• $title", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp))
    }
}
