package com.example.panicpilot.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.panicpilot.TestNotifyWorker
import com.example.panicpilot.data.NotifLog
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 履歴タブ: これまでに出した通知の一覧（最新100件）＋
 * 「アプリを閉じても通知が届くか」を自分で確かめるテストボタン。
 */
@Composable
fun HistoryScreen() {
    val context = LocalContext.current

    // 履歴の読み込み（reloadKeyを増やすと再読み込みされる）
    var entries by remember { mutableStateOf<List<NotifLog.Entry>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        entries = withContext(Dispatchers.IO) { NotifLog.load(context) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── 停止状態テストの案内カード ───
        item {
            Spacer(Modifier.height(6.dp))
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("アプリを閉じても届くかテスト", fontWeight = FontWeight.SemiBold)
                    Text(
                        "ボタンを押すと約15秒後にテスト通知を予約します。" +
                        "押したらすぐアプリを閉じて（タスク一覧からスワイプで消してOK）、" +
                        "通知が届けば、アプリが動いていなくても点灯サインの通知が届く証明になります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            // 本番の日次チェックと同じWorkManager経路で15秒後に通知を出す
                            val req = OneTimeWorkRequestBuilder<TestNotifyWorker>()
                                .setInitialDelay(15, TimeUnit.SECONDS)
                                .build()
                            WorkManager.getInstance(context).enqueue(req)
                            Toast.makeText(
                                context,
                                "約15秒後に通知が届きます。アプリを閉じてお待ちください",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("15秒後にテスト通知を予約")
                    }
                }
            }
        }

        // ─── 履歴一覧の見出し ───
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "通知履歴（最新100件）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                // タブを開き直さなくても最新化できる再読込
                Text(
                    "↻ 更新",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { reloadKey++ }
                        .padding(4.dp)
                )
            }
        }

        // ─── 履歴本体 ───
        val list = entries
        when {
            list == null -> item {
                Text("読み込み中…", style = MaterialTheme.typography.bodySmall)
            }
            list.isEmpty() -> item {
                Text(
                    "まだ通知はありません。\n点灯・買い増し・出口・テスト通知が出るとここに残ります。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> items(list) { e ->
                HistoryCard(e)
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** 履歴1件分のカード */
@Composable
private fun HistoryCard(e: NotifLog.Entry) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.at,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 権限が無くて出せなかった通知は目立たせる
                if (!e.delivered) {
                    Text(
                        "⚠ 未達（通知権限オフ）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(e.title, fontWeight = FontWeight.SemiBold,
                 style = MaterialTheme.typography.bodyMedium)
            Text(e.text, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
