package com.example.panicpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.panicpilot.NotificationHelper
import com.example.panicpilot.data.CRASH_HISTORY

/**
 * 根拠タブ: J-Quants 10年データ（生存バイアス無し）バックテストの実績。
 * 実績の数値は CrashHistoryData.kt（スクリプト自動生成）から導出し、ここには直書きしない
 */
@Composable
fun EvidenceScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("このアプリのルールの根拠", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(
            "J-Quants 10年データ（2016-07〜2026-07、廃止銘柄込み・リークなし）の" +
            "バックテスト16本で検証したルールだけを実装しています。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 実績の一覧は「過去局面」タブが本体。ここでは同じデータから要約だけ出す
        // （旧版は8行を直書きしていて、条件を日経基準に直した後の実データとズレていた）
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val r12 = CRASH_HISTORY.mapNotNull { it.r12m }
                val wins = r12.count { it > 0 }
                Text("点灯で日経レバ1570を買った実績（12ヶ月保有）",
                    fontWeight = FontWeight.SemiBold)
                SummaryLine("点灯した局面", "${CRASH_HISTORY.size}回（10年）")
                SummaryLine("12ヶ月後の成績", "${wins}勝${r12.size - wins}敗")
                SummaryLine("12ヶ月後の平均",
                    String.format("%+.1f%%", r12.average()))
                SummaryLine("いちばん悪かった局面",
                    String.format("%+.1f%%", r12.minOrNull() ?: 0.0))
                Text(
                    "局面ごとの値動き・点灯と消灯の推移・いつ売ればよかったかは" +
                    "「過去局面」タブで見られます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("検証済みルール", fontWeight = FontWeight.SemiBold)
                Rule("買う対象は高ベータ（1570等）",
                    "パニック12回で高β銘柄は12M対TOPIX+10.7%勝率75%。低β株は-8.4%勝率8%（検証8・12）")
                Rule("3分割の価格分割エントリー",
                    "一括+23.4% vs 分割+22.0%で期待値同等。深掘れ時（コロナ）は分割が大幅に有利（検証19）")
                Rule("出口は52週高値-3%回復",
                    "8回全勝・平均保有8ヶ月。トレーリングストップは勝率38%で厳禁（検証17）")
                Rule("撤退は日経が52週高値-35%割れ（指数ベース）",
                    "出口だけだと弱気相場で3年握らされ-70%になる（検証30）。指数-35%で切ると" +
                    "1990年-70.8%→-46.0%、2000年-76.5%→-46.8%、2007年-67.3%→-43.0%。" +
                    "直近10年では一度も発動せず勝ち局面を1つも切っていない（検証32・34・36）")
                Rule("撤退後は-3%回復まで新規出動しない",
                    "弱気相場の途中での追加出動は1991年-29.9%・2007年-61.2%と負けを重ねただけ。" +
                    "資金を使い回す前提だと1990年以降の最終資産が15万円→113万円に変わる（検証35・36）")
                Rule("含み損ではなく指数で切る",
                    "レバの含み損で切ると通常の暴落で狩られる。恐怖指数(VIX/日経VI)での損切りも" +
                    "「底で跳ねる」性質のため底値売りになり2020年が+37%→-23%に悪化（検証33）")
                Rule("浅い点灯（騰落<80）は二番底待ち",
                    "即買いよりも30〜40営業日後エントリーの方が高成績（検証9）")
                Rule("新規資金・待機現金だけで行う",
                    "タイミング運用単体は10年+149%でTOPIX持ちっぱなし+210%に負ける。コアは売らない（検証21）")
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚠ 限界と注意", fontWeight = FontWeight.SemiBold)
                Text(
                    "・過去10年は「結局回復した」時代の実績。回復しないバブル崩壊型の暴落では" +
                    "高ベータ戦略は最悪化する\n" +
                    "・短期は荒い（2020-02点灯は1ヶ月後-21%）。12ヶ月保有前提で、" +
                    "続落に耐えられる金額だけ出動する\n" +
                    "・投資判断は自己責任で。本アプリは記録と参考情報の提供のみ",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ── 通知の動作確認 ──
        // 点灯していない平常時は本物の通知が出ないため、「通知が届く状態か」を
        // 確かめる手段が無かった（作った日から確認できないまま残っていた項目）。
        // 本番と同じ NotificationHelper・同じチャンネルを通すので、
        // ここで音が鳴れば本番の点灯通知も同じ経路で届く。
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("通知が届くか確かめる", fontWeight = FontWeight.SemiBold)
                Text(
                    "点灯していない平常時は通知が出ないので、届く状態かどうかを" +
                    "自分で確かめられません。下のボタンは本番と同じ通知経路でテスト通知を1件出します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        NotificationHelper.notify(
                            context,
                            TEST_NOTIFICATION_ID,
                            "通知テスト（これは本物の点灯ではありません）",
                            "この通知が見えていれば、実際に出動条件が点灯したときも同じように届きます。"
                        )
                        Toast.makeText(context, "テスト通知を出しました", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("テスト通知を出す")
                }
            }
        }
    }
}

// 本番の通知IDとぶつからないよう、テスト専用の大きな番号を使う
private const val TEST_NOTIFICATION_ID = 90001

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Rule(title: String, detail: String) {
    Column {
        Text("• $title", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp))
    }
}
