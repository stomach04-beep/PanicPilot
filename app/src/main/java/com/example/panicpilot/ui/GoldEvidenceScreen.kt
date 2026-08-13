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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.panicpilot.data.GoldStatus

/**
 * 金・根拠タブ: なぜ314Aなのか、そしてなぜ「買い時シグナル」を置かないのか。
 * 検証は 2026-08-12 実施（Claud Code / gold-etf-backtest）。
 */
@Composable
fun GoldEvidenceScreen() {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("金スリーブの根拠", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)

        // ─── なぜ314Aか ───
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("なぜ ${GoldStatus.TICKER}（${GoldStatus.TICKER_NAME}）か",
                    fontWeight = FontWeight.SemiBold)
                EvLine("信託報酬", "0.220%（1540は0.44%＝約2倍）")
                EvLine("為替ヘッジ", "なし（LBMA金価格の円換算に連動）")
                EvLine("1口の値段", "約333円（1540は約21,130円）")
                EvLine("月4万円で買える口数", "120口（1540は1口）")
                EvLine("1日の売買代金", "約9.0億円")
                EvLine("550万円を買うと", "1日の売買代金の0.61%＝板を壊さない")
                Text(
                    "1口が安いので積立で細かく刻めます。1540だと月4万円では1口しか買えず、" +
                    "端数がずっと現金のまま残ります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("他の候補を外した理由", fontWeight = FontWeight.SemiBold)
                EvLine("425A グローバルX", "信託報酬は最安0.1775%だが売買代金が約1.0億円と薄い")
                EvLine("", "550万円が1日の5.26%＝価格を動かしかねない。上場も2025-09で実績が短い")
                EvLine("1540 純金上場信託", "16.1年の実測で金価格に年-0.61pt負け（公表0.44%と整合）")
                EvLine("1328 NEXT FUNDS", "17.6年の実測で年-1.84pt負け＝実質コストが重い")
                EvLine("1326 SPDR", "1口約64,420円で刻めない")
                EvLine("2036", "そもそも金ではない（日経・TOCOMレバレッジ商品）")
                Text(
                    "※ 314A/425Aは上場が2025年で、実測でコストを検算するには期間が足りません。" +
                    "そこは運用会社の公表値に頼っています。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ─── なぜシグナルが無いか（このタブの肝） ───
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("なぜ「買い時シグナル」が無いのか", fontWeight = FontWeight.Bold)
                Text(
                    "金のスポット買いルールを24本総当たりで検証しましたが、" +
                    "採用に値するものは1本もありませんでした。",
                    style = MaterialTheme.typography.bodyMedium
                )
                EvLine("52週高値-10%/-15%割れ", "上乗せがマイナス（買わないほうがマシ）")
                EvLine("52週高値-20%以上の下落", "独立エピソードが1〜2回しかなく判断不能")
                EvLine("VIX>35（株のパニック）", "3年では+14.1ptだが5年では-0.3pt＝一貫しない")
                EvLine("日足RSI<30（最有力だった）", "13エピソード全勝・5年で+19.4ptに見えたが…")
                Text(
                    "そのRSIルールは次の4つで落ちました:\n" +
                    "① ドル建てでは-14.2ptと符号が逆転（円建てだけの見かけ）\n" +
                    "② 期間の後半では+4.9ptまで減衰\n" +
                    "③ RSI<32にすると符号が変わる（ピンポイントすぎ＝ノイズ）\n" +
                    "④ 同条件のランダムなルール2000本と比べて上位24%止まり",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "24本×2期間＝48通りを試せば、偶然どれかが「勝率100%」に見える確率は91.5%です。" +
                    "見かけの好成績に飛びつかないために、あえてシグナルを置いていません。",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("代わりに守ること", fontWeight = FontWeight.SemiBold)
                EvLine("1. 目標", "リスク資産の5〜10%まで")
                EvLine("2. 建て方", "6〜12か月の分割（一括は元本割れ率2.8%→分割0.4%）")
                EvLine("3. 維持", "バンド±25%を外れたときだけ調整")
                EvLine("4. 戻し方", "売らずに新規資金で（税ゼロ）")
                EvLine("5. 期待値", "金は増やす資産ではない。30年窓で株に勝った割合は0%")
                Text(
                    "金の役目は下落を浅くすることです。株100%と株90%+金10%を比べると、" +
                    "最大下落は-63.3%→-59.0%に改善し、年リターンは0.30pt落ちます。" +
                    "この交換が納得できる範囲で持つのが正解です。",
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
private fun EvLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
        }
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(if (label.isEmpty()) 2.3f else 1.3f))
    }
}
