package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.panicpilot.data.GoldPlan

/**
 * 金・配分タブ: 目標比率とバンド±25%の判定。
 * リバランスは「バンドを外れたときだけ・新規資金で・売らずに」が検証の結論
 * （26年で取引13〜23回と最少でCAGR最良／売却益税20.315%を出さないノーセル方式）。
 */
@Composable
fun GoldAllocScreen(
    plan: GoldPlan?,
    onUpdateAmounts: (riskAssetYen: Long, holdingYen: Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("配分とリバランス", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(
            "金は「増やす資産」ではなく「株が落ちる月に落ちない資産」です。" +
            "株との相関は0.16、株が最も悪かった月の平均は株-9.4%に対し金-0.6%でした。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 金額の入力（MoneyForwardの数字を手で入れる想定）
        var risk by remember { mutableStateOf(plan?.riskAssetYen?.takeIf { it > 0 }?.toString() ?: "") }
        var hold by remember { mutableStateOf(plan?.holdingYen?.takeIf { it > 0 }?.toString() ?: "") }
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("いまの金額を入れる", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = risk, onValueChange = { risk = it.filter { c -> c.isDigit() } },
                    label = { Text("リスク資産の合計（円）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hold, onValueChange = { hold = it.filter { c -> c.isDigit() } },
                    label = { Text("いまの金の保有額（円）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onUpdateAmounts(risk.toLongOrNull() ?: 0L, hold.toLongOrNull() ?: 0L)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("更新する") }
            }
        }

        if (plan != null && plan.riskAssetYen > 0) {
            val ratio = plan.currentRatio()
            val state = plan.bandState()
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state == 0) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("いまの判定", fontWeight = FontWeight.Bold)
                    AllocLine("いまの金比率", String.format("%.2f%%", ratio * 100), strong = true)
                    AllocLine("目標比率",
                        String.format("%.1f%%", GoldPlan.TARGET_RATIO * 100))
                    AllocLine("バンド（この中なら何もしない）",
                        String.format("%.2f%% 〜 %.2f%%",
                            GoldPlan.bandLower * 100, GoldPlan.bandUpper * 100))
                    val gap = plan.gapToTargetYen()
                    Text(
                        when (state) {
                            -1 -> "▼ 下限割れ。目標に戻すには ${String.format("%,d", gap)}円 の買い増しです。" +
                                  "既存の株は売らず、新しい資金で埋めてください（売却益税を出さないため）。"
                            1 -> "▲ 上限超え。金が増えすぎています。" +
                                 "積立期は売らずに、株側へ新規資金を厚く入れて比率を戻してください。"
                            else -> "● バンドの中です。何もしなくて構いません。" +
                                    "頻繁に触るほど税と手数料で損をします。"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("なぜこのやり方か（検証の結論）", fontWeight = FontWeight.SemiBold)
                AllocLine("配分", "リスク資産の5〜10%")
                AllocLine("10%にした場合", "最大DD -63.3%→-59.0%")
                AllocLine("そのときのCAGR", "-0.30pt")
                AllocLine("リバランス", "バンド±25%（26年で13〜23回）")
                AllocLine("年1回なら", "26回・四半期なら104回の取引")
                AllocLine("積立期の戻し方", "売らずに新規資金で")
                Text(
                    "毎月10万円・金10%を26年続けた検証では、ノーセル方式9.29倍に対し" +
                    "売買リバランスは9.33倍。差はごくわずかで、税を払う価値はありません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * ラベルと値の1行。値が長いと SpaceBetween だけでは折り返しが重なるので、
 * 両方に weight を持たせて各列の中で折り返させる（実機で崩れを確認して修正）
 */
@Composable
private fun AllocLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f))
    }
}
