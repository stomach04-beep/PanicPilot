package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.panicpilot.data.GoldPlan
import com.example.panicpilot.data.GoldStatus

/** 金タブ共通の1行表示 */
@Composable
private fun GoldLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun yen(v: Long): String = String.format("%,d円", v)

/**
 * 金・計画タブ: 目標額を6〜12か月に分けて淡々と実行するナビ。
 * 「いつ買うか」を当てるルールは検証で棄却されたので、ここに点灯シグナルは無い。
 */
@Composable
fun GoldPlanScreen(
    status: GoldStatus?,
    plan: GoldPlan?,
    onStart: (targetYen: Long, months: Int) -> Unit,
    onBuyDone: (amountYen: Long) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("金スリーブの積み上げ", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)

        // 現在値カード（判断材料。売買シグナルではないと明記する）
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${GoldStatus.TICKER} ${GoldStatus.TICKER_NAME}",
                    fontWeight = FontWeight.SemiBold)
                if (status == null) {
                    Text("取得中またはデータなし",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    GoldLine("終値（${status.dataDate}）",
                        String.format("%,.1f円", status.price), strong = true)
                    GoldLine("52週高値", String.format("%,.1f円", status.high52w))
                    GoldLine("高値からの下落率", String.format("%+.1f%%", status.dd52w * 100))
                    GoldLine("日足RSI", String.format("%.1f", status.rsi14))
                    Text(
                        "※ 下落率もRSIも参考表示です。金は下落率でもRSIでも" +
                        "「買い時」を当てられないことを検証済みなので、これらで待たないでください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (plan == null) {
            GoldPlanSetup(onStart)
        } else {
            GoldPlanProgress(status, plan, onBuyDone, onReset)
        }
    }
}

/** 計画がまだ無いときの入力フォーム */
@Composable
private fun GoldPlanSetup(onStart: (Long, Int) -> Unit) {
    var target by remember { mutableStateOf("5500000") }
    var months by remember { mutableStateOf("12") }

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("分割購入をはじめる", fontWeight = FontWeight.SemiBold)
            Text(
                "一括で入れるより6〜12か月に分けたほうが、5年後に元本割れする確率が" +
                "2.8%→0.4%に下がります（中央値はほぼ同じ）。24か月まで延ばすと逆に悪化します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = target, onValueChange = { target = it.filter { c -> c.isDigit() } },
                label = { Text("目標額（円）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = months, onValueChange = { months = it.filter { c -> c.isDigit() } },
                label = { Text("分割回数（${GoldPlan.MONTHS_MIN}〜${GoldPlan.MONTHS_MAX}）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            val t = target.toLongOrNull() ?: 0L
            val m = (months.toIntOrNull() ?: 12)
                .coerceIn(GoldPlan.MONTHS_MIN, GoldPlan.MONTHS_MAX)
            if (t > 0) {
                Text("→ 毎月 ${yen(t / m)} を $m 回",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = { if (t > 0) onStart(t, m) }, modifier = Modifier.fillMaxWidth()) {
                Text("この計画ではじめる")
            }
        }
    }
}

/** 計画の進捗と「次にやること」 */
@Composable
private fun GoldPlanProgress(
    status: GoldStatus?,
    plan: GoldPlan,
    onBuyDone: (Long) -> Unit,
    onReset: () -> Unit
) {
    val today = GoldPlan.todayJst()
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (plan.isDue(today) && !plan.isComplete)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan.isComplete) {
                Text("分割購入は完了しました", fontWeight = FontWeight.Bold)
                Text("これ以降は「配分」タブのバンド判定に従って、" +
                     "比率が外れたときだけ新規資金で調整します。",
                    style = MaterialTheme.typography.bodySmall)
            } else if (plan.isDue(today)) {
                Text("今日やること", fontWeight = FontWeight.Bold)
                Text("${yen(plan.perBuyYen)} ぶんの ${GoldStatus.TICKER} を買う",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (status != null && status.price > 0) {
                    Text("→ 約 ${(plan.perBuyYen / status.price).toInt()} 口" +
                         "（終値 ${String.format("%,.1f", status.price)}円）",
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("次の購入予定", fontWeight = FontWeight.Bold)
                Text("${plan.nextBuyDate()} に ${yen(plan.perBuyYen)}",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("進捗", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { plan.progress },
                modifier = Modifier.fillMaxWidth()
            )
            GoldLine("実行済み", "${plan.doneCount} / ${plan.months} 回")
            GoldLine("投じた額", yen(plan.investedYen))
            GoldLine("目標額", yen(plan.targetYen))
            GoldLine("残り", "${plan.remaining}回・${yen(plan.targetYen - plan.investedYen)}")
            GoldLine("開始日", plan.startDate)
        }
    }

    if (!plan.isComplete) {
        Button(onClick = { onBuyDone(plan.perBuyYen) }, modifier = Modifier.fillMaxWidth()) {
            Text("${plan.doneCount + 1}回目を買った（記録する）")
        }
    }
    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
        Text("計画をやめる・作り直す")
    }
}
