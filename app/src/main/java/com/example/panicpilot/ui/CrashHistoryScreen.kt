package com.example.panicpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicpilot.data.CRASH_HISTORY
import com.example.panicpilot.data.CrashEpisode
import com.example.panicpilot.data.LitSegment
import com.example.panicpilot.data.MarketStatus

// 画面共通の色（点灯＝赤、消灯＝グレー、底＝緑）
private val COL_LIT = Color(0xFFEF5350)
private val COL_DIM = Color(0xFFBDBDBD)
private val COL_BOTTOM = Color(0xFF2E7D32)
private val COL_PLUS = Color(0xFF2E7D32)
private val COL_MINUS = Color(0xFFD32F2F)

private fun pct(v: Double?, digits: Int = 1): String =
    if (v == null) "—" else String.format("%+.${digits}f%%", v)

private fun plainPct(v: Double?, digits: Int = 1): String =
    if (v == null) "—" else String.format("%.${digits}f%%", v)

private fun colorOf(v: Double?): Color = when {
    v == null -> Color.Gray
    v >= 0 -> COL_PLUS
    else -> COL_MINUS
}

private fun median(xs: List<Double>): Double? {
    if (xs.isEmpty()) return null
    val s = xs.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
}

/**
 * 過去局面タブ: アプリと同じ3条件で10年分を再現し、「点灯したときに1570を買っていたら
 * どうなったか」を局面ごとに見る画面。
 *
 * 数値は data/CrashHistoryData.kt（スクリプト自動生成）を唯一の出どころにする。
 * ここで再計算・直書きすると本体とズレるため、集計もすべてそのリストから導出する。
 */
@Composable
fun CrashHistoryScreen(status: MarketStatus?) {
    // 新しい局面ほど関心が高いので降順。先頭に「まとめ」タブを置く
    val episodes = remember { CRASH_HISTORY.sortedByDescending { it.litDate } }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            Tab(selected = tab == 0, onClick = { tab = 0 },
                text = { Text("まとめ", fontSize = 13.sp, maxLines = 1) })
            episodes.forEachIndexed { i, e ->
                Tab(selected = tab == i + 1, onClick = { tab = i + 1 },
                    text = {
                        Text(
                            e.nickname.ifBlank { e.litDate.substring(0, 7) },
                            fontSize = 13.sp, maxLines = 1
                        )
                    })
            }
        }
        if (tab == 0) SummaryPage(episodes) else EpisodePage(episodes[tab - 1], status)
    }
}

/** まとめ: 全局面から集計した「いつ売ればよかったか」 */
@Composable
private fun SummaryPage(episodes: List<CrashEpisode>) {
    val r12 = episodes.mapNotNull { it.r12m }
    val r6 = episodes.mapNotNull { it.r6m }
    val r3 = episodes.mapNotNull { it.r3m }
    val r1 = episodes.mapNotNull { it.r1m }
    val r5 = episodes.mapNotNull { it.r5 }
    val exits = episodes.mapNotNull { it.exitRet }
    val exitDays = episodes.mapNotNull { it.exitDays }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("過去の点灯局面 ${episodes.size} 回のまとめ",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "アプリと同じ3条件（52週高値-15% / 5日-8% / 騰落レシオ<70）を日経平均で10年分" +
            "再現し、点灯の翌営業日に1570を買った場合の実績です。1570の実際の値段で計算して" +
            "いるので、レバETFの減価と信託報酬は織り込み済みです。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("何日後に売ったか別の成績（一括買い）", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1.1f), fontSize = 11.sp)
                    Text("勝率", Modifier.weight(0.9f), fontSize = 11.sp,
                        color = Color.Gray, textAlign = TextAlign.End)
                    Text("中央値", Modifier.weight(1f), fontSize = 11.sp,
                        color = Color.Gray, textAlign = TextAlign.End)
                    Text("最悪", Modifier.weight(1f), fontSize = 11.sp,
                        color = Color.Gray, textAlign = TextAlign.End)
                }
                HorizonRow("5営業日", r5)
                HorizonRow("1ヶ月", r1)
                HorizonRow("3ヶ月", r3)
                HorizonRow("6ヶ月", r6)
                HorizonRow("12ヶ月", r12)
                Text(
                    "早く売るほど負けます。数日で利確すると勝率は5割を切り、" +
                    "3ヶ月まで持てばほぼ負けません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("このアプリの出口ルールで売った場合", fontWeight = FontWeight.SemiBold)
                Text("（日経が52週高値-3%まで回復したら売却）",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(2.dp))
                StatRow("成績", "${exits.count { it > 0 }}勝${exits.count { it <= 0 }}敗",
                    if (exits.all { it > 0 }) COL_PLUS else COL_MINUS)
                StatRow("損益の中央値", pct(median(exits)), colorOf(median(exits)))
                StatRow("保有期間の中央値",
                    median(exitDays.map { it.toDouble() })?.let { "${it.toInt()}営業日" } ?: "—",
                    MaterialTheme.colorScheme.onSurface)
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("買い方の違い（12ヶ月保有）", fontWeight = FontWeight.SemiBold)
                val g12 = episodes.mapNotNull { it.g12m }
                val ddA = episodes.map { it.maxDdA }
                val ddG = episodes.map { it.maxDdG }
                StatRow("一括の中央値", pct(median(r12)), colorOf(median(r12)))
                StatRow("3分割の中央値", pct(median(g12)), colorOf(median(g12)))
                StatRow("一括の含み損（最悪）", plainPct(ddA.minOrNull()), COL_MINUS)
                StatRow("3分割の含み損（最悪）", plainPct(ddG.minOrNull()), COL_MINUS)
                Text(
                    "3分割（出動タブの既定プラン）は、深く下げた局面で買い増しが入るぶん" +
                    "含み損が浅くなります。浅い押し目で終わると1/3しか買えないので" +
                    "中央値は一括に劣ります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚠ この表の限界", fontWeight = FontWeight.SemiBold)
                Text(
                    "・局面は${episodes.size}回しかなく、大きく下げたのは実質コロナ1回だけです\n" +
                    "・過去10年は「結局回復した」時代の実績で、回復しない暴落では最悪化します\n" +
                    "・手数料・税金は含みません",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HorizonRow(label: String, xs: List<Double>) {
    val win = if (xs.isEmpty()) null else xs.count { it > 0 } * 100.0 / xs.size
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.1f), fontSize = 13.sp)
        // toInt()は切り捨て。81.8%を81%と出すと検証レポート側（82%）とズレるので四捨五入する
        Text(win?.let { "${Math.round(it)}%" } ?: "—", Modifier.weight(0.9f), fontSize = 13.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
            color = if ((win ?: 0.0) >= 80) COL_PLUS else if ((win ?: 0.0) < 50) COL_MINUS else Color.Gray)
        Text(pct(median(xs)), Modifier.weight(1f), fontSize = 13.sp,
            textAlign = TextAlign.End, color = colorOf(median(xs)))
        Text(pct(xs.minOrNull()), Modifier.weight(1f), fontSize = 13.sp,
            textAlign = TextAlign.End, color = colorOf(xs.minOrNull()))
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 局面1つぶんの詳細 */
@Composable
private fun EpisodePage(e: CrashEpisode, status: MarketStatus?) {
    // 局面を切り替えたら先頭から表示する（rememberScrollStateのままだと前の局面の
    // スクロール位置が残り、別の暴落を開いたのに途中から表示されてしまう）
    val scroll = remember(e.litDate) { ScrollState(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ヘッダー: 局面名 + 点灯した条件
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.title, Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(color = COL_LIT, shape = RoundedCornerShape(4.dp)) {
                Text(" ${e.kind} ", color = Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }

        // サマリー
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Chip("点灯日", e.litDate, MaterialTheme.colorScheme.onSurface)
            Chip("日経の下落", plainPct(e.dd52w), COL_MINUS)
            Chip("点灯回数", "${e.litCount}回", MaterialTheme.colorScheme.onSurface)
            Chip("底まで", "${e.bottomOffset}日", COL_MINUS)
            Chip("12ヶ月後", pct(e.r12m), colorOf(e.r12m))
        }

        // ── 点灯⇔消灯の推移（チャタリングが見えるバー） ──
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("点灯と消灯の推移", fontWeight = FontWeight.SemiBold)
                LitTimelineBar(e)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(COL_LIT, "点灯中")
                    LegendDot(COL_DIM, "消灯")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(10.dp).background(COL_BOTTOM))
                        Spacer(Modifier.width(3.dp))
                        Text("1570の底", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                val dim = if (e.dimDate.isBlank()) "まだ消えていません" else "${e.dimDate}に消灯"
                Text(
                    "初回は${e.litRunDays}営業日つづけて点灯し、$dim。" +
                    if (e.litCount > 1) "その後この局面で計${e.litCount}回点きました（点いたり消えたりは普通に起きます）。"
                    else "この局面ではこの1回だけでした。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 買っていたらどうなったか ──
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("点灯の翌営業日に1570を買っていたら", fontWeight = FontWeight.SemiBold)
                RetRow("5営業日後に売る", e.r5)
                RetRow("1ヶ月後に売る", e.r1m)
                RetRow("3ヶ月後に売る", e.r3m)
                RetRow("6ヶ月後に売る", e.r6m)
                RetRow("12ヶ月後に売る", e.r12m)
                Spacer(Modifier.height(2.dp))
                RetRow("いちばん深い含み損", e.maxDdA)
                Text(
                    "底は${e.bottomDate}（買ってから${e.bottomOffset}営業日後）で、" +
                    "そこでの含み損は${plainPct(e.bottomPct)}でした。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 出動タブの3分割プランと出口ルール ──
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("3分割で買った場合（出動タブの既定）", fontWeight = FontWeight.SemiBold)
                RetRow("12ヶ月後", e.g12m)
                RetRow("いちばん深い含み損", e.maxDdG)
                Spacer(Modifier.height(4.dp))
                Text("出口ルールで売った場合", fontWeight = FontWeight.SemiBold)
                if (e.exitRet == null) {
                    Text("まだ52週高値-3%まで回復していません（保有中）",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    RetRow("${e.exitDate}に売却（${e.exitDays}営業日）", e.exitRet)
                }
            }
        }

        // ── 点灯時の指標 vs 現在（しきい値は MarketStatus の定義を参照＝二重定義しない） ──
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("点灯したときの3指標", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1.3f), fontSize = 10.sp)
                    Text("当時", Modifier.weight(0.8f), fontSize = 10.sp,
                        color = Color.Gray, textAlign = TextAlign.End)
                    Text("いま", Modifier.weight(0.8f), fontSize = 10.sp,
                        color = Color.Gray, textAlign = TextAlign.End)
                    Text("点灯ライン", Modifier.weight(0.9f), fontSize = 10.sp,
                        color = Color.Gray, textAlign = TextAlign.End)
                }
                CompareRow("52週高値からの下落", e.dd52w, status?.let { it.dd52w * 100 },
                    plainPct(MarketStatus.TH_DD * 100))
                CompareRow("5営業日リターン", e.ret5d, status?.let { it.ret5d * 100 },
                    plainPct(MarketStatus.TH_FAST * 100))
                CompareRow("25日騰落レシオ", e.adr25, status?.adr25,
                    "< ${MarketStatus.TH_ADR_DEEP.toInt()}")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 点灯（赤）と消灯（グレー）の帯。1570の底に緑の縦線を立てる */
@Composable
private fun LitTimelineBar(e: CrashEpisode) {
    val total = e.timeline.sumOf { it.days }.toFloat().coerceAtLeast(1f)
    // タイムラインは点灯日から始まる。買いは翌営業日なので、底の位置は +1 日ずらす
    val bottomPos = (e.bottomOffset + 1).toFloat()
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp))
        ) {
            val fullWidth = maxWidth
            Row(Modifier.fillMaxSize()) {
                e.timeline.forEach { seg ->
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(seg.days / total)
                            .background(if (seg.lit) COL_LIT else COL_DIM)
                    )
                }
            }
            if (bottomPos <= total) {
                Box(
                    Modifier
                        .offset(x = fullWidth * (bottomPos / total) - 1.5.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(COL_BOTTOM)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(e.litDate, fontSize = 10.sp, color = Color.Gray)
            Text(
                if (bottomPos <= total) "底: ${e.bottomDate}"
                else "底はこの先（${e.bottomDate}）",
                fontSize = 10.sp, color = COL_BOTTOM, fontWeight = FontWeight.Bold
            )
            Text("${e.timelineDays}営業日", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun Chip(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun RetRow(label: String, v: Double?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Text(pct(v), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorOf(v))
    }
}

@Composable
private fun CompareRow(label: String, past: Double?, now: Double?, threshold: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.3f), fontSize = 12.sp, color = Color.Gray)
        Text(past?.let { String.format("%.1f", it) } ?: "—", Modifier.weight(0.8f),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        Text(now?.let { String.format("%.1f", it) } ?: "—", Modifier.weight(0.8f),
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
        Text(threshold, Modifier.weight(0.9f), fontSize = 10.sp,
            color = Color.Gray, textAlign = TextAlign.End)
    }
}
