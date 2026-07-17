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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 根拠タブ: J-Quants 10年データ（生存バイアス無し）バックテストの実績。
 * 数値は 2026-07-13 実施の検証15（bt_lev_etf.py）等の実測値
 */
@Composable
fun EvidenceScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("このアプリのルールの根拠", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(
            "J-Quants 10年データ（2016-07〜2026-07、廃止銘柄込み・リークなし）の" +
            "バックテスト16本で検証したルールだけを実装しています。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("過去8回の点灯で日経レバ1570を買った実績（12ヶ月保有）",
                    fontWeight = FontWeight.SemiBold)
                EvidenceRow("2018-10 DD型", "+21.6%", "+6.7%")
                EvidenceRow("2018-12 騰落型", "+56.1%", "+21.2%")
                EvidenceRow("2019-05 DD型", "-1.0%", "+0.9%")
                EvidenceRow("2020-02 騰落型", "+84.1%", "+27.4%")
                EvidenceRow("2020-03 DD型", "+108.3%", "+41.5%")
                EvidenceRow("2022-03 DD型", "+18.9%", "+9.7%")
                EvidenceRow("2024-08 DD型", "+42.8%", "+24.5%")
                EvidenceRow("2025-04 DD型", "+200.7%", "+55.3%")
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("平均（8回中7勝）", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("+66.4%", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("検証済みルール", fontWeight = FontWeight.SemiBold)
                Rule("買う対象は高ベータ（1570等）",
                    "パニック12回で高β銘柄は12M対TOPIX+10.7%勝率75%。低β株は-8.4%勝率8%（検証8・12）")
                Rule("3分割の価格分割エントリー",
                    "一括+23.4% vs 分割+22.0%で期待値同等。深掘れ時（コロナ）は分割が大幅に有利（検証19）")
                Rule("出口は52週高値-3%回復",
                    "8回全勝・平均保有8ヶ月。トレーリングストップは勝率38%で厳禁（検証17）")
                Rule("浅い点灯（騰落<80）は二番底待ち",
                    "即買いよりも30〜40営業日後エントリーの方が高成績（検証9）")
                Rule("新規資金・待機現金だけで行う",
                    "タイミング運用単体は10年+149%でTOPIX持ちっぱなし+210%に負ける。コアは売らない（検証21）")
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚠ 限界と注意", fontWeight = FontWeight.SemiBold)
                Text(
                    "・過去10年は「結局回復した」時代の実績。回復しないバブル崩壊型の暴落では" +
                    "高ベータ戦略は最悪化する\n" +
                    "・短期は荒い（2020-02点灯は1ヶ月後-21%）。12ヶ月保有前提で、" +
                    "続落に耐えられる金額だけ出動する\n" +
                    "・投資判断は自己責任で。本アプリは記録と参考情報の提供のみ",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun EvidenceRow(label: String, lev: String, topix: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
        Text("1570: $lev", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium)
        Text("TOPIX: $topix", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Rule(title: String, detail: String) {
    Column {
        Text("• $title", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp))
    }
}
