package com.example.panicpilot

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.panicpilot.data.MarketFetcher
import com.example.panicpilot.data.MarketStatus
import com.example.panicpilot.data.Position
import com.example.panicpilot.data.Storage
import com.example.panicpilot.ui.EvidenceScreen
import com.example.panicpilot.ui.HistoryScreen
import com.example.panicpilot.ui.PlanScreen
import com.example.panicpilot.ui.SignalScreen
import com.example.panicpilot.ui.TrendScreen
import com.example.panicpilot.ui.theme.PanicPilotTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // 通知権限のリクエスト（Android 13+）
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PanicPilotTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── 状態: 保存値をまず表示し、裏で最新を取得（未接続でも最終値が見える教訓） ───
    var status by remember { mutableStateOf<MarketStatus?>(null) }
    var position by remember { mutableStateOf<Position?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    fun persist() {
        val notified = Storage.load(context).notifiedKeys
        Storage.save(context, Storage.Saved(status, position, notified))
    }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MarketFetcher.fetch() }
            }
            result.onSuccess {
                status = it
                lastError = null
                persist()
            }.onFailure { lastError = it.message ?: "不明なエラー" }
            loading = false
        }
    }

    // 起動時: 保存値を読み込み→最新取得
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { Storage.load(context) }
        status = saved.status
        position = saved.position
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (loading) "暴落出動ナビ（更新中…）" else "暴落出動ナビ") },
                actions = {
                    IconButton(onClick = { refresh() }) { Text("↻") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                // 5タブ化に伴い「出動ナビ」→「出動」に短縮（幅の都合）
                listOf("シグナル", "推移", "出動", "根拠", "履歴").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(label) })
                }
            }
            when (tab) {
                0 -> SignalScreen(status, lastError)
                1 -> TrendScreen(status)
                2 -> PlanScreen(
                    status = status,
                    position = position,
                    onStart = { budget ->
                        val s = status ?: return@PlanScreen
                        val jst = TimeZone.getTimeZone("Asia/Tokyo")
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
                            .apply { timeZone = jst }.format(Date())
                        position = Position(
                            entryDate = today,
                            baseIndex = s.indexLast,
                            budgetYen = budget
                        )
                        persist()
                    },
                    onFill2 = { position = position?.copy(fill2Done = true); persist() },
                    onFill3 = { position = position?.copy(fill3Done = true); persist() },
                    onClose = { position = null; persist() }
                )
                3 -> EvidenceScreen()
                4 -> HistoryScreen()
            }
        }
    }
}
