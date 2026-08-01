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
    retreatedAt: String?,          // 撤退ライン割れの日。null でなければ出動ロック中
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
        if (retreatedAt != null) RetreatLockBanner(status, retreatedAt)
        if (position == null) {
            PlanBuilder(status, onStart)
        } else {
            PositionTracker(status, position, onFill2, onFill3, onClose)
        }
    }
}

/** 撤退ライン割れ後のロック表示（検証32・36: 弱気相場で買い増すと負けを重ねる） */
@Composable
private fun RetreatLockBanner(status: MarketStatus?, retreatedAt: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("⛔ 撤退ロック中（新規出動しません）", fontWeight = FontWeight.Bold)
            Text(
                "${retreatedAt}に日経平均が撤退ライン（52週高値-35%）を割りました。" +
                (status?.let { "52週高値-3%＝${"%,.0f".format(it.exitLine)}円まで回復すれば解除されます。" } ?: "") +
                "点灯しても出動しないのがルールです（検証36: 弱気相場での追加出動は" +
                "1991年-29.9%・2007年-61.2%と、いずれも負けを重ねただけ）",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** 出動前: 予算入力 → 計画表示 */
@Composable
private fun PlanBuilder(status: MarketStatus?, onStart: (Long) -> Unit) {
    var budgetMan by rememberSaveable { mutableStateOf("100") }   // 万円
    val budgetYen = (budgetMan.toLongOrNull() ?: 0L) * 10000L
    // 確信度（日経VI）で実際に出す金額を調整する。出動の可否は変えない（検証33・標本が小さいため）
    val ratio = status?.confidenceRatio ?: 1.0
    val deployYen = (budgetYen * ratio).toLong()
    val tranche = deployYen / 3

    Text("出動計画をつくる", style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)
    Text(
        "対象は楽天日経レバ1458（1570と同指数で信託報酬が半分以下・検証39補足）。" +
        "新規資金・待機現金で行い、" +
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

    // ── 確信度カード（日経VIで出す金額を厚くするか決める材料） ──
    status?.let { s ->
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.confidenceLabel, fontWeight = FontWeight.SemiBold)
                Text(
                    if (s.viHigh)
                        "恐怖が極まっている局面。過去はこの条件が重なると買値が底に近く" +
                        "（底より+9.9%）、12ヶ月成績も+44%→+70%だった。予算の満額を出す"
                    else
                        "まだ恐怖が浅い局面。予算の半分に抑える。" +
                        "日経VIが${MarketStatus.TH_VI.toInt()}を超えたら満額に切り替わる",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "※出動するかどうかは変えません（VIの検証は2016年以降のn=8と標本が小さいため、" +
                    "金額の厚みだけに使う・検証33）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3分割エントリー計画（検証19: 価格分割）", fontWeight = FontWeight.SemiBold)
            if (ratio < 1.0) {
                Text(
                    "確信度が標準のため、予算${yen(budgetYen)}のうち${yen(deployYen)}を出します",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val t = status?.indexLast
            StepRow("① いま（点灯の翌々日）", yen(tranche),
                t?.let { "日経 %,.0f円".format(it) } ?: "")
            StepRow("② 日経が基準-5%まで下落", yen(tranche),
                t?.let { "日経 %,.0f円 以下".format(it * 0.95) } ?: "")
            StepRow("③ 日経が基準-10%まで下落", yen(tranche),
                t?.let { "日経 %,.0f円 以下".format(it * 0.90) } ?: "")
            Text(
                "※②③が60営業日以内に来なければ、その時点で残りを投入\n" +
                "※出口: 日経平均が52週高値-3%以内に回復したら全売却（検証17: 8回全勝）\n" +
                // 撤退ラインの基準は「現在値」ではなく「52週高値」。MarketStatus 側の計算を使う
                "※撤退: 日経平均が52週高値-35%" +
                (status?.let { "（%,.0f円）".format(it.retreatLine) } ?: "") +
                "を割ったら損切りし、-3%回復まで新規出動しない（検証32・36）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status?.lev1458?.let { p ->
                if (tranche > 0) {
                    Text(
                        "1458換算: 1回あたり約${floor(tranche / p).toInt()}口（@%,.0f円）".format(p),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    Button(
        // 記録するのは「実際に出す額」（確信度で絞った後の額）。予算そのものではない
        onClick = { if (deployYen > 0) onStart(deployYen) },
        enabled = deployYen > 0 && status != null,
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
                // しきい値は MarketStatus 側の定義を参照する（0.97 等を直書きしない）
                InfoLine("出口ライン（利確）", "%,.0f円（52週高値%.0f%%）"
                    .format(it.exitLine, MarketStatus.TH_EXIT * 100))
                InfoLine("撤退ライン（損切り）", "%,.0f円（52週高値%.0f%%）"
                    .format(it.retreatLine, MarketStatus.TH_RETREAT * 100))
                if (it.recovered) {
                    Text("🏁 出口シグナル点灯中！ 全売却のタイミングです",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold)
                }
                if (it.sigRetreat) {
                    Text("🛑 撤退シグナル点灯中！ 全売却して次の回復まで待機",
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
