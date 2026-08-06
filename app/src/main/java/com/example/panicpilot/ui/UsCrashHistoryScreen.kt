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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicpilot.data.UsCrashEpisode
import com.example.panicpilot.data.UsCrashHistoryData
import com.example.panicpilot.data.UsMarketStatus

private val UpGreen = Color(0xFF3D9C5A)
private val DownRed = Color(0xFFE05B4C)

/**
 * 米国・過去局面タブ: 1990年以降の点灯16局面と、実在ETF（SPY/SSO/SPXL）の実勢成績。
 * データは jquants-bulk/gen_panicpilot_us_history.py が自動生成した UsCrashHistoryData.kt
 * （手編集禁止）を参照する
 */
@Composable
fun UsCrashHistoryScreen(status: UsMarketStatus?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("過去の点灯局面（米国・1990年以降）",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ─── まとめカード ───
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("まとめ（点灯翌営業日買い→12ヶ月保有）", fontWeight = FontWeight.SemiBold)
                UsHistLine("点灯した局面",
                    "${UsCrashHistoryData.episodes.size}回 ≒ 2〜3年に1回")
                // ラベルが長いと折り返しで崩れるので短く（3倍=買い対象・1倍=参考）
                UsHistLine("SPXL(3倍)",
                    "中央値%+.1f%% 勝率%d%%（n=%d）".format(
                        UsCrashHistoryData.SPXL_12M_MED,
                        UsCrashHistoryData.SPXL_12M_WIN, UsCrashHistoryData.SPXL_12M_N))
                UsHistLine("SPY(1倍)参考",
                    "中央値%+.1f%% 勝率%d%%（n=%d）".format(
                        UsCrashHistoryData.SPY_12M_MED,
                        UsCrashHistoryData.SPY_12M_WIN, UsCrashHistoryData.SPY_12M_N))
                Text(
                    "⚠ SPXLは2008年11月上場のため、リーマンショック（-84%級）を経験していない" +
                    "標本での数字。2008年型が来れば撤退線（-35%）ありでも-63%級は覚悟する（検証46）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // ─── 現在との比較 ───
        status?.let { s ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("いまの3指標（過去の点灯日と見比べる用）", fontWeight = FontWeight.SemiBold)
                    UsHistLine("52週高値からの下落", "%+.1f%%（点灯 -15%%）".format(s.dd52w * 100))
                    UsHistLine("5日間リターン", "%+.1f%%（点灯 -8%%）".format(s.ret5d * 100))
                    UsHistLine("VIX", if (s.vix.isNaN()) "取得不可"
                                      else "%.1f（確信度 30）".format(s.vix))
                }
            }
        }

        Text(
            "各局面: 点灯日の指標と、実在ETFの実勢リターン（3ヶ月/12ヶ月/12ヶ月内の最大含み損）。" +
            "「—」はそのETFが当時まだ上場していなかった期間。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ─── 局面カード（新しい順） ───
        UsCrashHistoryData.episodes.reversed().forEach { ep -> EpisodeCard(ep) }
    }
}

@Composable
private fun EpisodeCard(ep: UsCrashEpisode) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(ep.date.substring(0, 7).replace("-", "年") + "月",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (ep.name.isNotEmpty()) {
                    Text(ep.name, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 点灯条件と当日の指標
            val condText = ep.conds.joinToString("・") { UsMarketStatus.signalLabel(it) }
            Text(
                "点灯: $condText ／ 52週%+.1f%% ／ 5日%+.1f%%".format(ep.ddAt, ep.ret5At) +
                    (ep.vixAt?.let { " ／ VIX %.0f".format(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // VIX確信度の目印（30以上=満額の局面だった）
            ep.vixAt?.let {
                if (it >= UsMarketStatus.TH_VIX) {
                    Text("VIX≥30 ＝ 確信度高（満額）の局面",
                        style = MaterialTheme.typography.bodySmall, color = UpGreen)
                } else {
                    Text("VIX<30 ＝ 半分に抑える局面（2008年型の警戒）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(2.dp))
            // 成績表（ヘッダ＋3行）
            ReturnHeader()
            ReturnRow("SPXL(3倍)", ep.spxl3m, ep.spxl12m, ep.spxlDd, bold = true)
            ReturnRow("SSO(2倍)", ep.sso3m, ep.sso12m, ep.ssoDd)
            ReturnRow("SPY(1倍)", ep.spy3m, ep.spy12m, ep.spyDd)
        }
    }
}

@Composable
private fun ReturnHeader() {
    Row(Modifier.fillMaxWidth()) {
        Text("", Modifier.weight(1.2f))
        listOf("3ヶ月", "12ヶ月", "最大含み損").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReturnRow(label: String, r3m: Double?, r12m: Double?, dd: Double?,
                      bold: Boolean = false) {
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall,
            fontWeight = w)
        Pct(r3m, Modifier.weight(1f), w)
        Pct(r12m, Modifier.weight(1f), w)
        // 最大含み損は常にマイナス側なので色分けせずグレー
        Text(dd?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = w)
    }
}

@Composable
private fun Pct(v: Double?, modifier: Modifier, weight: FontWeight) {
    Text(
        v?.let { "%+.0f%%".format(it) } ?: "—",
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            v == null -> MaterialTheme.colorScheme.onSurfaceVariant
            v >= 0 -> UpGreen
            else -> DownRed
        },
        fontWeight = weight
    )
}

@Composable
private fun UsHistLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
