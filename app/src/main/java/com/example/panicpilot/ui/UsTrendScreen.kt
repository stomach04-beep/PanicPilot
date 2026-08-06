package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.panicpilot.data.UsMarketStatus

/**
 * 米国・推移タブ: 3指標（52週DD・5日率・VIX）の過去約1年の折れ線グラフ。
 * チャート部品は日本版 TrendScreen と共用（IndicatorChart / ThLine）
 */
@Composable
fun UsTrendScreen(status: UsMarketStatus?) {
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

        // ① 52週高値からの下落率（点灯=-15%以下）
        IndicatorChart(
            title = "S&P500 52週高値からの下落率",
            hist = hist,
            dateOf = { it.date },
            valueOf = { it.dd52w * 100.0 },
            litOf = { it.litDd },
            thresholds = listOf(ThLine(UsMarketStatus.TH_DD * 100.0, TrendFireRed, "点灯 -15%")),
            valueLabel = { "%+.0f%%".format(it) },
            currentLabel = { "%+.1f%%".format(it) }
        )

        // ② 5営業日リターン（点灯=-8%以下）
        IndicatorChart(
            title = "S&P500 5日間リターン（急落検知）",
            hist = hist,
            dateOf = { it.date },
            valueOf = { it.ret5d * 100.0 },
            litOf = { it.litFast },
            thresholds = listOf(ThLine(UsMarketStatus.TH_FAST * 100.0, TrendFireRed, "点灯 -8%")),
            valueLabel = { "%+.0f%%".format(it) },
            currentLabel = { "%+.1f%%".format(it) }
        )

        // ③ VIX（点灯条件ではなく確信度ライン30。litは常にfalse＝赤丸を打たない）
        IndicatorChart(
            title = "VIX（恐怖指数）※点灯条件ではありません",
            hist = hist,
            dateOf = { it.date },
            valueOf = { it.vix },
            litOf = { false },
            thresholds = listOf(ThLine(UsMarketStatus.TH_VIX, TrendWarnAmber, "確信度 30")),
            valueLabel = { "%.0f".format(it) },
            currentLabel = { "%.1f".format(it) }
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "期間: ${hist.first().date} 〜 ${hist.last().date}（米国東部の日付）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
