package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.panicpilot.data.MarketStatus
import com.example.panicpilot.data.Position
import kotlin.math.floor

/**
 * 出動ナビ: 予算から3分割エントリー計画を作り、ポジションを記録・追跡する。
 * ルールは全てJ-Quants 10年バックテストで検証済みのもの（検証15/17/19）
 */
@Composable
fun PlanScreen(
    status: MarketStatus?,
    position: Position?,
    onStart: (budgetYen: Long) -> Unit,
    onFill2: () -> Unit,
    onFill3: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (position == null) {
            PlanBuilder(status, onStart)
        } else {
            PositionTracker(status, position, onFill2, onFill3, onClose)
        }
    }
}

/** 出動前: 予算入力 → 計画表示 */
@Composable
private fun PlanBuilder(status: MarketStatus?, onStart: (Long) -> Unit) {
    var budgetMan by rememberSaveable { mutableStateOf("100") }   // 万円
    val budgetYen = (budgetMan.toLongOrNull() ?: 0L) * 10000L
    val tranche = budgetYen / 3

    Text("出動計画をつくる", style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)
    Text(
        "対象は日経レバ1570（または高ベータ大型株）。新規資金・待機現金で行い、" +
        "保有中のコア資産は売らないこと（検証21: タイミング運用単体はB&Hに負ける）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = budgetMan,
        onValueChange = { budgetMan = it.filter { c -> c.isDigit() }.take(6) },
        label = { Text("出動予算（万円）") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3分割エントリー計画（検証19: 価格分割）", fontWeight = FontWeight.SemiBold)
            val t = status?.indexLast
            StepRow("① いま（点灯の翌々日）", yen(tranche),
                t?.let { "日経 %,.0f円".format(it) } ?: "")
            StepRow("② 日経が基準-5%まで下落", yen(tranche),
                t?.let { "日経 %,.0f円 以下".format(it * 0.95) } ?: "")
            StepRow("③ 日経が基準-10%まで下落", yen(tranche),
                t?.let { "日経 %,.0f円 以下".format(it * 0.90) } ?: "")
            Text(
                "※②③が60営業日以内に来なければ、その時点で残りを投入\n" +
                "※出口: 日経平均が52週高値-3%以内に回復したら全売却（検証17: 8回全勝）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status?.lev1570?.let { p ->
                if (tranche > 0) {
                    Text(
                        "1570換算: 1回あたり約${floor(tranche / p).toInt()}口（@%,.0f円）".format(p),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    Button(
        onClick = { if (budgetYen > 0) onStart(budgetYen) },
        enabled = budgetYen > 0 && status != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("① を実行した — 出動を記録する") }

    if (status != null && !status.deep) {
        Text(
            "⚠ いまは深い点灯なし。記録は点灯時に行うのが原則です",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** 出動後: ポジション追跡 */
@Composable
private fun PositionTracker(
    status: MarketStatus?, pos: Position,
    onFill2: () -> Unit, onFill3: () -> Unit, onClose: () -> Unit
) {
    Text("出動中のポジション", style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoLine("出動日", pos.entryDate)
            InfoLine("基準 日経平均", "%,.0f円".format(pos.baseIndex))
            InfoLine("予算", yen(pos.budgetYen))
            status?.let {
                InfoLine("現在 日経平均", "%,.0f円（基準比 %+.1f%%）"
                    .format(it.indexLast, (it.indexLast / pos.baseIndex - 1) * 100))
                InfoLine("出口ライン", "%,.0f円（52週高値-3%%）".format(it.high52w * 0.97))
                if (it.dd52w >= -0.03) {
                    Text("🏁 出口シグナル点灯中！ 全売却のタイミングです",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("買い増しトリガー", fontWeight = FontWeight.SemiBold)
            TriggerRow("② -5%（日経 %,.0f円）".format(pos.trigger2),
                pos.fill2Done, status?.indexLast?.let { it <= pos.trigger2 } == true, onFill2)
            TriggerRow("③ -10%（日経 %,.0f円）".format(pos.trigger3),
                pos.fill3Done, status?.indexLast?.let { it <= pos.trigger3 } == true, onFill3)
            Text("60営業日以内に来なければ残りを投入して完了扱いに",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text("手仕舞いした（記録をクリア）")
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun StepRow(label: String, amount: String, trigger: String) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (trigger.isNotEmpty()) Text(trigger, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(amount, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TriggerRow(label: String, done: Boolean, reached: Boolean, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                when { done -> "✅ 投入済み"; reached -> "🔔 水準到達！投入のタイミング"; else -> "未到達" },
                style = MaterialTheme.typography.bodySmall,
                color = if (reached && !done) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!done) OutlinedButton(onClick = onDone) { Text("投入した") }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun yen(v: Long): String = when {
    v >= 10000 * 10000L -> "%,.1f億円".format(v / (10000.0 * 10000))
    v >= 10000 -> "%,d万円".format(v / 10000)
    else -> "%,d円".format(v)
}
