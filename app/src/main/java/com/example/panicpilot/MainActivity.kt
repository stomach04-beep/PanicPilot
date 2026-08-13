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
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.unit.dp
import com.example.panicpilot.data.GoldFetcher
import com.example.panicpilot.data.GoldPlan
import com.example.panicpilot.data.GoldStatus
import com.example.panicpilot.data.MarketFetcher
import com.example.panicpilot.data.MarketFetcherUs
import com.example.panicpilot.data.MarketStatus
import com.example.panicpilot.data.Position
import com.example.panicpilot.data.Storage
import com.example.panicpilot.data.TopixFetcher
import com.example.panicpilot.data.TopixLadderStatus
import com.example.panicpilot.data.UsMarketStatus
import com.example.panicpilot.ui.CrashHistoryScreen
import com.example.panicpilot.ui.EvidenceScreen
import com.example.panicpilot.ui.GoldAllocScreen
import com.example.panicpilot.ui.GoldEvidenceScreen
import com.example.panicpilot.ui.GoldPlanScreen
import com.example.panicpilot.ui.HistoryScreen
import com.example.panicpilot.ui.PlanScreen
import com.example.panicpilot.ui.SignalScreen
import com.example.panicpilot.ui.TrendScreen
import com.example.panicpilot.ui.UsCrashHistoryScreen
import com.example.panicpilot.ui.UsEvidenceScreen
import com.example.panicpilot.ui.UsPlanScreen
import com.example.panicpilot.ui.UsSignalScreen
import com.example.panicpilot.ui.UsTrendScreen
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
    // v2.0: タブを「市場（日本/米国）→ 機能」の2階層に。タブ位置は市場ごとに別々に覚える
    var market by remember { mutableIntStateOf(0) }   // 0=日本 1=米国 2=金
    var jpTab by remember { mutableIntStateOf(0) }
    var usTab by remember { mutableIntStateOf(0) }
    var goldTab by remember { mutableIntStateOf(0) }
    // 撤退ライン割れの日（null なら通常運用）。設定・解除するのは DailyCheckWorker だけ
    var retreatedAt by remember { mutableStateOf<String?>(null) }

    // ─── 米国市場（v1.9）。日本側と同じ設計を並行で持つ ───
    var usStatus by remember { mutableStateOf<UsMarketStatus?>(null) }
    var usPosition by remember { mutableStateOf<Position?>(null) }
    var usLastError by remember { mutableStateOf<String?>(null) }
    var usRetreatedAt by remember { mutableStateOf<String?>(null) }

    // ─── TOPIX（1306）はしご（v2.2・検証48）。日経レバ用シグナルとは別枠 ───
    var tpxStatus by remember { mutableStateOf<TopixLadderStatus?>(null) }

    // ─── 金スリーブ（v2.3）。点灯シグナルは持たない＝分割の執行と配分の維持だけ ───
    var goldStatus by remember { mutableStateOf<GoldStatus?>(null) }
    var goldPlan by remember { mutableStateOf<GoldPlan?>(null) }

    fun persist() {
        // 通知まわりの記録（通知済みキー・前回の点灯レベル）は画面側では触らず、
        // 読み込んだ値をそのまま書き戻す（消灯通知の判定材料を消さないため）
        val saved = Storage.load(context)
        Storage.save(
            context,
            saved.copy(status = status, position = position,
                       usStatus = usStatus, usPosition = usPosition,
                       tpxStatus = tpxStatus ?: saved.tpxStatus,
                       goldStatus = goldStatus ?: saved.goldStatus,
                       goldPlan = goldPlan)
        )
    }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            // 日本と米国は独立に取得（片方の失敗がもう片方を殺さない）
            val jpResult = withContext(Dispatchers.IO) {
                runCatching { MarketFetcher.fetch() }
            }
            val usResult = withContext(Dispatchers.IO) {
                runCatching { MarketFetcherUs.fetch() }
            }
            // 1306はしご（失敗しても他を巻き込まない fail-soft）
            val tpxResult = withContext(Dispatchers.IO) {
                runCatching { TopixFetcher.fetch() }
            }
            jpResult.onSuccess {
                status = it
                lastError = null
            }.onFailure { lastError = it.message ?: "不明なエラー" }
            usResult.onSuccess {
                usStatus = it
                usLastError = null
            }.onFailure { usLastError = it.message ?: "不明なエラー" }
            // 金ETF（314A）も独立取得。失敗しても他を巻き込まない fail-soft
            val goldResult = withContext(Dispatchers.IO) {
                runCatching { GoldFetcher.fetch() }
            }
            tpxResult.onSuccess { tpxStatus = it }
            goldResult.onSuccess { goldStatus = it }
            if (jpResult.isSuccess || usResult.isSuccess ||
                tpxResult.isSuccess || goldResult.isSuccess) persist()
            loading = false
        }
    }

    // 起動時: 保存値を読み込み→最新取得
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { Storage.load(context) }
        status = saved.status
        position = saved.position
        retreatedAt = saved.retreatedAt
        usStatus = saved.usStatus
        usPosition = saved.usPosition
        usRetreatedAt = saved.usRetreatedAt
        tpxStatus = saved.tpxStatus
        goldStatus = saved.goldStatus
        goldPlan = saved.goldPlan
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // アプリ名は v2.1 で「Be Greedy」に改名（バフェットの格言
                // "Be greedy when others are fearful" ＝他人が恐れているときこそ貪欲に）
                title = { Text(if (loading) "Be Greedy（更新中…）" else "Be Greedy") },
                actions = {
                    IconButton(onClick = { refresh() }) { Text("↻") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            // ─── 1段目: 市場切替（日本 / 米国） ───
            TabRow(selectedTabIndex = market) {
                listOf("🇯🇵 日本", "🇺🇸 米国", "🥇 金").forEachIndexed { i, label ->
                    Tab(selected = market == i, onClick = { market = i },
                        text = { Text(label) })
                }
            }
            // ─── 2段目: 市場ごとの機能タブ ───
            if (market == 0) {
                ScrollableTabRow(selectedTabIndex = jpTab, edgePadding = 8.dp) {
                    listOf("シグナル", "推移", "出動", "根拠", "過去局面", "履歴")
                        .forEachIndexed { i, label ->
                            Tab(selected = jpTab == i, onClick = { jpTab = i },
                                text = { Text(label) })
                        }
                }
                when (jpTab) {
                    0 -> SignalScreen(status, lastError, tpxStatus)
                    1 -> TrendScreen(status)
                    2 -> PlanScreen(
                        status = status,
                        position = position,
                        retreatedAt = retreatedAt,
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
                    4 -> CrashHistoryScreen(status)
                    5 -> HistoryScreen()
                }
            } else if (market == 1) {
                ScrollableTabRow(selectedTabIndex = usTab, edgePadding = 8.dp) {
                    listOf("シグナル", "推移", "出動", "根拠", "過去局面", "履歴")
                        .forEachIndexed { i, label ->
                            Tab(selected = usTab == i, onClick = { usTab = i },
                                text = { Text(label) })
                        }
                }
                when (usTab) {
                    0 -> UsSignalScreen(usStatus, usRetreatedAt, usLastError)
                    1 -> UsTrendScreen(usStatus)
                    2 -> UsPlanScreen(
                        status = usStatus,
                        position = usPosition,
                        retreatedAt = usRetreatedAt,
                        onStart = { budget ->
                            val s = usStatus ?: return@UsPlanScreen
                            val jst = TimeZone.getTimeZone("Asia/Tokyo")
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
                                .apply { timeZone = jst }.format(Date())
                            usPosition = Position(
                                entryDate = today,
                                baseIndex = s.indexLast,
                                budgetYen = budget
                            )
                            persist()
                        },
                        onFill2 = { usPosition = usPosition?.copy(fill2Done = true); persist() },
                        onFill3 = { usPosition = usPosition?.copy(fill3Done = true); persist() },
                        onClose = { usPosition = null; persist() }
                    )
                    3 -> UsEvidenceScreen()
                    4 -> UsCrashHistoryScreen(usStatus)
                    5 -> HistoryScreen()   // 履歴は日米共通（全通知を1つのログに記録している）
                }
            } else {
                // ─── 金スリーブ（v2.3）───
                // 日米と違い「シグナル」タブが無い。金のタイミングルールは24本総当たり＋
                // プラセボ検定で全部棄却されたので、当てにいく画面をあえて作っていない
                ScrollableTabRow(selectedTabIndex = goldTab, edgePadding = 8.dp) {
                    listOf("計画", "配分", "根拠").forEachIndexed { i, label ->
                        Tab(selected = goldTab == i, onClick = { goldTab = i },
                            text = { Text(label) })
                    }
                }
                when (goldTab) {
                    0 -> GoldPlanScreen(
                        status = goldStatus,
                        plan = goldPlan,
                        onStart = { target, months ->
                            goldPlan = GoldPlan(
                                startDate = GoldPlan.todayJst(),
                                targetYen = target,
                                months = months
                            )
                            persist()
                        },
                        onBuyDone = { amount ->
                            goldPlan = goldPlan?.let {
                                it.copy(
                                    doneCount = (it.doneCount + 1).coerceAtMost(it.months),
                                    investedYen = it.investedYen + amount,
                                    // 買った分は保有額にも足しておく（配分タブの手入力を減らす）
                                    holdingYen = it.holdingYen + amount
                                )
                            }
                            persist()
                        },
                        onReset = { goldPlan = null; persist() }
                    )
                    1 -> GoldAllocScreen(
                        plan = goldPlan,
                        onUpdateAmounts = { risk, hold ->
                            goldPlan = goldPlan?.copy(riskAssetYen = risk, holdingYen = hold)
                                ?: GoldPlan(
                                    startDate = GoldPlan.todayJst(),
                                    targetYen = 0L, months = GoldPlan.MONTHS_MAX,
                                    riskAssetYen = risk, holdingYen = hold
                                )
                            persist()
                        }
                    )
                    2 -> GoldEvidenceScreen()
                }
            }
        }
    }
}
