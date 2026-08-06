package com.example.panicpilot.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicpilot.data.HistoryPoint
import com.example.panicpilot.data.MarketStatus

// グラフ配色（シグナル画面と同じ相場慣習）
// v2.1: 米国推移タブ（UsTrendScreen）とチャート部品を共用するため internal に変更
internal val TrendFireRed = Color(0xFFE05B4C)
internal val TrendWarnAmber = Color(0xFFDBA13A)

/** 推移タブ: 3指標の過去約1年（252営業日）の折れ線グラフ */
@Composable
fun TrendScreen(status: MarketStatus?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val hist = status?.history ?: emptyList()
        if (hist.size < 2) {
            Text(
                "推移データがまだありません。トップの ↻ で更新してください。",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        Text(
            "各指標の過去約1年の推移です。左が縦軸（値）、下が横軸（日付）。" +
                "色付きの点線が点灯ライン、赤丸はその日に点灯していた日です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ① 52週高値からの下落率（%表示、点灯=-15%以下 → 下向きが危険）
        IndicatorChart(
            title = "日経平均 52週高値からの下落率",
            hist = hist,
            dateOf = { it.date },
            valueOf = { it.dd52w * 100.0 },
            litOf = { it.litDd },
            thresholds = listOf(ThLine(MarketStatus.TH_DD * 100.0, TrendFireRed, "点灯 -15%")),
            valueLabel = { "%+.0f%%".format(it) },
            currentLabel = { "%+.1f%%".format(it) }
        )

        // ② 5営業日リターン（%表示、点灯=-8%以下）
        IndicatorChart(
            title = "日経平均 5日間リターン（急落検知）",
            hist = hist,
            dateOf = { it.date },
            valueOf = { it.ret5d * 100.0 },
            litOf = { it.litFast },
            thresholds = listOf(ThLine(MarketStatus.TH_FAST * 100.0, TrendFireRed, "点灯 -8%")),
            valueLabel = { "%+.0f%%".format(it) },
            currentLabel = { "%+.1f%%".format(it) }
        )

        // ③ 25日騰落レシオ（点灯=70未満、80未満=注意）
        IndicatorChart(
            title = "25日騰落レシオ",
            hist = hist,
            dateOf = { it.date },
            valueOf = { it.adr25 },
            litOf = { it.litAdr },
            thresholds = listOf(
                ThLine(MarketStatus.TH_ADR_DEEP, TrendFireRed, "点灯 70"),
                ThLine(MarketStatus.TH_ADR_SHALLOW, TrendWarnAmber, "注意 80")
            ),
            valueLabel = { "%.0f".format(it) },
            currentLabel = { "%.1f".format(it) }
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "期間: ${hist.first().date} 〜 ${hist.last().date}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 水平しきい値ライン1本の定義（日米の推移タブで共用） */
internal data class ThLine(val value: Double, val color: Color, val label: String)

/** 指標1つ分のカード＋折れ線グラフ（日米の推移タブで共用のためジェネリック） */
@Composable
internal fun <T> IndicatorChart(
    title: String,
    hist: List<T>,
    dateOf: (T) -> String,
    valueOf: (T) -> Double,
    litOf: (T) -> Boolean,
    thresholds: List<ThLine>,
    valueLabel: (Double) -> String,      // 軸目盛り用（整数など短め）
    currentLabel: (Double) -> String     // 最新値用（小数1桁）
) {
    // NaN は「欠損」として保持（線は切るが x位置は保つ）
    val values = hist.map { valueOf(it) }
    val lits = hist.map { litOf(it) }
    val dates = hist.map { dateOf(it) }
    val valid = values.filter { !it.isNaN() }
    if (valid.isEmpty()) return

    // y軸の範囲は「値の最小最大」と「しきい値」を両方含める（点灯ラインが必ず見えるように）
    var minV = valid.min()
    var maxV = valid.max()
    thresholds.forEach { minV = minOf(minV, it.value); maxV = maxOf(maxV, it.value) }
    if (maxV - minV < 1e-6) { maxV += 1.0; minV -= 1.0 }
    val pad = (maxV - minV) * 0.08
    minV -= pad; maxV += pad

    val lastValid = values.lastOrNull { !it.isNaN() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                lastValid?.let {
                    Text(
                        "最新 " + currentLabel(it),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (lits.last()) TrendFireRed else onSurface
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                drawIndicator(
                    values, lits, dates, minV, maxV, thresholds,
                    lineColor = onSurface, axisColor = axisColor,
                    gridColor = gridColor, valueLabel = valueLabel
                )
            }
        }
    }
}

/** Canvas への実描画（縦軸目盛り・横軸日付・折れ線・しきい値点線・点灯日の赤丸） */
private fun DrawScope.drawIndicator(
    values: List<Double>,
    lits: List<Boolean>,
    dates: List<String>,
    minV: Double,
    maxV: Double,
    thresholds: List<ThLine>,
    lineColor: Color,
    axisColor: Color,
    gridColor: Color,
    valueLabel: (Double) -> String
) {
    val n = values.size
    if (n < 2) return

    // 目盛りラベルのための余白（px）: 左＝縦軸の数値、下＝横軸の日付
    val leftPad = 96f
    val bottomPad = 44f
    val topPad = 14f
    val rightPad = 20f
    val plotW = size.width - leftPad - rightPad
    val plotH = size.height - topPad - bottomPad

    fun xAt(i: Int) = leftPad + if (n == 1) 0f else plotW * i / (n - 1)
    fun yAt(v: Double) = topPad + (plotH - ((v - minV) / (maxV - minV) * plotH)).toFloat()

    // テキスト用 Paint（android.graphics）
    val txt = android.graphics.Paint().apply {
        color = axisColor.toArgb()
        textSize = 26f
        isAntiAlias = true
    }
    val nc = drawContext.canvas.nativeCanvas

    // ─── 縦軸: 上端(最大)・中央・下端(最小) の3本のグリッド線＋数値 ───
    val yTicks = listOf(maxV, (maxV + minV) / 2.0, minV)
    txt.textAlign = android.graphics.Paint.Align.RIGHT
    yTicks.forEachIndexed { idx, v ->
        val y = yAt(v)
        drawLine(
            color = gridColor,
            start = Offset(leftPad, y),
            end = Offset(size.width - rightPad, y),
            strokeWidth = 1.5f
        )
        // 数値は左余白の右端へ右揃え。最上段だけ線の「下」に描く（上に描くと画面上端で切れるため）
        val baseline = if (idx == 0) y + 24f else y - 6f
        nc.drawText(valueLabel(v), leftPad - 8f, baseline, txt)
    }
    // 0ライン（範囲内なら薄く強調＝プラスマイナスの境目が分かる）
    if (minV < 0.0 && maxV > 0.0) {
        val y0 = yAt(0.0)
        drawLine(
            color = gridColor.copy(alpha = 0.5f),
            start = Offset(leftPad, y0),
            end = Offset(size.width - rightPad, y0),
            strokeWidth = 1.5f
        )
    }

    // ─── しきい値の水平点線＋右端に数値 ───
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    txt.textAlign = android.graphics.Paint.Align.RIGHT
    thresholds.forEach { th ->
        val y = yAt(th.value)
        drawLine(
            color = th.color.copy(alpha = 0.8f),
            start = Offset(leftPad, y),
            end = Offset(size.width - rightPad, y),
            strokeWidth = 2f,
            pathEffect = dash
        )
        val save = txt.color
        txt.color = th.color.toArgb()
        nc.drawText(th.label, size.width - rightPad, y - 4f, txt)
        txt.color = save
    }

    // ─── 折れ線（欠損 NaN の前後は線を切る） ───
    for (i in 0 until n - 1) {
        val a = values[i]; val b = values[i + 1]
        if (a.isNaN() || b.isNaN()) continue
        drawLine(
            color = lineColor.copy(alpha = 0.9f),
            start = Offset(xAt(i), yAt(a)),
            end = Offset(xAt(i + 1), yAt(b)),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    // ─── 点灯日を赤丸で強調 ───
    for (i in 0 until n) {
        val v = values[i]
        if (v.isNaN() || !lits[i]) continue
        drawCircle(color = TrendFireRed, radius = 3.5f, center = Offset(xAt(i), yAt(v)))
    }

    // ─── 横軸: 開始・中央・終了の3つの日付（MM/dd） ───
    fun shortDate(d: String): String {
        // "2026-07-16" → "7/16"
        val p = d.split("-")
        return if (p.size == 3) "${p[1].trimStart('0')}/${p[2]}" else d
    }
    val baseY = topPad + plotH
    // 軸の下線
    drawLine(
        color = gridColor,
        start = Offset(leftPad, baseY),
        end = Offset(size.width - rightPad, baseY),
        strokeWidth = 1.5f
    )
    val yText = size.height - 12f
    txt.color = axisColor.toArgb()
    txt.textAlign = android.graphics.Paint.Align.LEFT
    nc.drawText(shortDate(dates.first()), leftPad, yText, txt)
    txt.textAlign = android.graphics.Paint.Align.CENTER
    nc.drawText(shortDate(dates[n / 2]), leftPad + plotW / 2f, yText, txt)
    txt.textAlign = android.graphics.Paint.Align.RIGHT
    nc.drawText(shortDate(dates.last()), size.width - rightPad, yText, txt)
}
