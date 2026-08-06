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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicpilot.data.Position
import com.example.panicpilot.data.UsMarketStatus
import kotlin.math.floor

// 点灯色（SignalScreenと同じ配色。private のためファイルごとに定義）
private val FireRed = Color(0xFFE05B4C)
private val WarnAmber = Color(0xFFDBA13A)
private val CalmGray = Color(0xFF8A8A8A)

/**
 * 米国・シグナルタブ: S&P500の出動シグナル表示（v2.0でタブを日本/米国の2階層に分け、
 * 出動計画は UsPlanScreen へ分離）。
 * ルールは検証45・46で確認したもの（点灯2条件・VIX確信度・撤退線-35%）
 */
@Composable
fun UsSignalScreen(
    status: UsMarketStatus?,
    retreatedAt: String?,          // 撤退ライン割れの日。null でなければ出動ロック中
    lastError: String?
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
                if (lastError != null) "米国データ取得に失敗しました:\n$lastError"
                else "米国データ取得中…",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        // ─── 総合判定バナー（米国はDEEP/CALMの2値） ───
        val (label, color, advice) = when {
            retreatedAt != null && status.deep -> Triple(
                "⛔ 点灯中だが出動禁止", FireRed,
                "撤退ロック中。52週高値-3%まで回復するまで新規出動しない（検証46）"
            )
            status.deep -> Triple(
                "🚨 出動（米国）", FireRed,
                "点灯中。VIXで金額を決めて、SPXLを3分割でエントリー（出動タブで計画を作成）"
            )
            else -> Triple(
                "😴 待機（米国）", CalmGray,
                "平時。米国の点灯は日本より頻度が低い（1990年以降16回）。待つのが仕事"
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

        // ─── シグナル2枚＋VIX ───
        UsSignalCard(
            title = "S&P500 52週高値からの下落",
            value = "%+.1f%%".format(status.dd52w * 100),
            threshold = "点灯: -15%以下",
            lit = status.sigDd,
            note = "検証45: 点灯→SPXL 12M+62.7%・勝率88%（超過+24.8pt）"
        )
        UsSignalCard(
            title = "S&P500 5日間リターン（急落検知）",
            value = "%+.1f%%".format(status.ret5d * 100),
            threshold = "点灯: -8%以下",
            lit = status.sigFast,
            note = "検証45: 3M超過は全対象プラス。米国も「速く落ちた」が本質"
        )
        UsSignalCard(
            title = "VIX（恐怖指数）※点灯条件ではありません",
            value = if (status.vix.isNaN()) "取得不可" else "%.1f".format(status.vix),
            threshold = "確信度 高: ${UsMarketStatus.TH_VIX.toInt()}以上",
            lit = false,
            warn = status.vixHigh,
            note = "検証46: VIX≥30の点灯は12M+35%勝率85%、VIX<30は-18%勝率33%。" +
                "30未満なら予算を半分に（2008年の2大災害は両方VIX<30だった）"
        )

        // ─── 参考情報 ───
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UsInfoRow("S&P500", "%,.0f".format(status.indexLast))
                UsInfoRow("52週高値", "%,.0f".format(status.high52w))
                UsInfoRow("出口ライン（高値-3%）", "%,.0f".format(status.exitLine))
                UsInfoRow("撤退ライン（高値-35%）", "%,.0f".format(status.retreatLine))
                status.spxl?.let { UsInfoRow("SPXL（S&P500ブル3倍）", "$%,.2f".format(it)) }
                status.usdJpy?.let { UsInfoRow("ドル円", "%,.2f円".format(it)) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "データ日付: ${status.dataDate}（米国東部） ／ 取得: ${status.fetchedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ─── 注意書き（日本版との違い） ───
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("米国版の注意（日本版との違い）", fontWeight = FontWeight.SemiBold)
                Text(
                    "・SPXLは3倍レバ。撤退線があっても最悪-63%級は残る（検証46）。" +
                        "レバなし（VOO等）は12Mの超過エッジがゼロ（検証45）\n" +
                        "・1倍でエッジが出ないので出動は必ずレバ型で。その分、金額は日本版より控えめに\n" +
                        "・為替はヘッジ不要（ヘッジコスト4〜5%/年でほぼ相殺・検証46）。円のまま考えてよい\n" +
                        "・買い付けは点灯通知の当日夜（日本時間22:30/23:30の寄り）が実行しやすい",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 米国・出動タブ: SPXLの出動計画とポジション追跡（日本版 PlanScreen と同じ構成）。
 * v2.0でシグナルタブから分離した
 */
@Composable
fun UsPlanScreen(
    status: UsMarketStatus?,
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
        if (retreatedAt != null) UsRetreatLockBanner(status, retreatedAt)
        if (position == null) {
            UsPlanBuilder(status, onStart)
        } else {
            UsPositionTracker(status, position, onFill2, onFill3, onClose)
        }
    }
}

/** 撤退ライン割れ後のロック表示 */
@Composable
private fun UsRetreatLockBanner(status: UsMarketStatus?, retreatedAt: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("⛔ 撤退ロック中（新規出動しません）", fontWeight = FontWeight.Bold)
            Text(
                "${retreatedAt}にS&P500が撤退ライン（52週高値-35%）を割りました。" +
                (status?.let { "52週高値-3%＝${"%,.0f".format(it.exitLine)}まで回復すれば解除されます。" } ?: "") +
                "3倍レバは指数-35%時点で約-70%。ここで粘ると2008年型の-84%コースです（検証46）",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** 出動前: 予算入力 → SPXL 3分割計画 */
@Composable
private fun UsPlanBuilder(status: UsMarketStatus?, onStart: (Long) -> Unit) {
    var budgetMan by rememberSaveable { mutableStateOf("50") }   // 万円（3倍レバなので既定は控えめ）
    val budgetYen = (budgetMan.toLongOrNull() ?: 0L) * 10000L
    // 確信度（VIX）で実際に出す金額を調整（検証46: VIX<30は2008年型の危険信号でもある）
    val ratio = status?.confidenceRatio ?: 1.0
    val deployYen = (budgetYen * ratio).toLong()
    val tranche = deployYen / 3

    Text("出動計画をつくる（SPXL）", style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)
    Text(
        "対象はSPXL（S&P500ブル3倍・楽天証券で購入可）。" +
        "1倍のVOO/SPYでは12Mの超過エッジがゼロのため、出動は必ずレバ型で（検証45）。" +
        "新規資金・待機現金で行い、保有中のコア資産は売らないこと",
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

    // ── 確信度カード（VIXで出す金額を決める） ──
    status?.let { s ->
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.confidenceLabel, fontWeight = FontWeight.SemiBold)
                Text(
                    if (s.vixHigh)
                        "恐怖が極まっている局面。VIX≥30の点灯は12M平均+35%・勝率85%（検証46）。" +
                        "予算の満額を出す"
                    else
                        "VIXが跳ねていない点灯は危険信号でもある（VIX<30は12M平均-18%・勝率33%。" +
                        "2008年の2大災害は両方VIX<30だった）。予算の半分に抑える",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3分割エントリー計画（日本版と同じ価格分割）", fontWeight = FontWeight.SemiBold)
            if (ratio < 1.0) {
                Text(
                    "確信度が標準のため、予算${usYen(budgetYen)}のうち${usYen(deployYen)}を出します",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val t = status?.indexLast
            UsStepRow("① いま（点灯通知の当日夜の寄り）", usYen(tranche),
                t?.let { "S&P500 %,.0f".format(it) } ?: "")
            UsStepRow("② S&P500が基準-5%まで下落", usYen(tranche),
                t?.let { "S&P500 %,.0f 以下".format(it * 0.95) } ?: "")
            UsStepRow("③ S&P500が基準-10%まで下落", usYen(tranche),
                t?.let { "S&P500 %,.0f 以下".format(it * 0.90) } ?: "")
            Text(
                "※②③が60営業日以内に来なければ、その時点で残りを投入\n" +
                "※出口: S&P500が52週高値-3%以内に回復したら全売却\n" +
                "※撤退: S&P500が52週高値-35%" +
                (status?.let { "（%,.0f）".format(it.retreatLine) } ?: "") +
                "を割ったら損切りし、-3%回復まで新規出動しない（検証46）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // SPXL株数の目安（ドル建て価格×ドル円で円換算）
            val spxl = status?.spxl
            val fx = status?.usdJpy
            if (spxl != null && fx != null && tranche > 0) {
                val perUnitYen = spxl * fx
                Text(
                    ("SPXL換算: 1回あたり約${floor(tranche / perUnitYen).toInt()}株" +
                        "（@$%,.2f ≒ %,.0f円）").format(spxl, perUnitYen),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    Button(
        // 記録するのは「実際に出す額」（確信度で絞った後の額）
        onClick = { if (deployYen > 0) onStart(deployYen) },
        enabled = deployYen > 0 && status != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("① を実行した — 出動を記録する") }

    if (status != null && !status.deep) {
        Text(
            "⚠ いまは点灯なし。記録は点灯時に行うのが原則です",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** 出動後: ポジション追跡 */
@Composable
private fun UsPositionTracker(
    status: UsMarketStatus?, pos: Position,
    onFill2: () -> Unit, onFill3: () -> Unit, onClose: () -> Unit
) {
    Text("出動中のポジション（米国）", style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UsInfoRow("出動日", pos.entryDate)
            UsInfoRow("基準 S&P500", "%,.0f".format(pos.baseIndex))
            UsInfoRow("予算", usYen(pos.budgetYen))
            status?.let {
                UsInfoRow("現在 S&P500", "%,.0f（基準比 %+.1f%%）"
                    .format(it.indexLast, (it.indexLast / pos.baseIndex - 1) * 100))
                // しきい値は UsMarketStatus 側の定義を参照する（数値を直書きしない）
                UsInfoRow("出口ライン（利確）", "%,.0f（52週高値%.0f%%）"
                    .format(it.exitLine, UsMarketStatus.TH_EXIT * 100))
                UsInfoRow("撤退ライン（損切り）", "%,.0f（52週高値%.0f%%）"
                    .format(it.retreatLine, UsMarketStatus.TH_RETREAT * 100))
                if (it.recovered) {
                    Text("🏁 出口シグナル点灯中！ SPXL全売却のタイミングです",
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
            UsTriggerRow("② -5%（S&P500 %,.0f）".format(pos.trigger2),
                pos.fill2Done, status?.indexLast?.let { it <= pos.trigger2 } == true, onFill2)
            UsTriggerRow("③ -10%（S&P500 %,.0f）".format(pos.trigger3),
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

// ─── 部品（SignalScreen/PlanScreen の private 部品と同型。ファイル間で共有できないため再定義） ───

@Composable
private fun UsSignalCard(
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
private fun UsStepRow(label: String, amount: String, trigger: String) {
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
private fun UsTriggerRow(label: String, done: Boolean, reached: Boolean, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
private fun UsInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun usYen(v: Long): String = when {
    v >= 10000 * 10000L -> "%,.1f億円".format(v / (10000.0 * 10000))
    v >= 10000 -> "%,d万円".format(v / 10000)
    else -> "%,d円".format(v)
}
