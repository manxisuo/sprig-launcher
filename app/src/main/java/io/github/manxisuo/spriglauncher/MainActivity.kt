package io.github.manxisuo.spriglauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.manxisuo.spriglauncher.data.AppPreferenceEntity
import io.github.manxisuo.spriglauncher.data.ThemeMode
import io.github.manxisuo.spriglauncher.domain.LaunchableEntry
import io.github.manxisuo.spriglauncher.domain.UsageSource
import io.github.manxisuo.spriglauncher.ui.AppStats
import io.github.manxisuo.spriglauncher.ui.LauncherUiState
import io.github.manxisuo.spriglauncher.ui.MainViewModel
import io.github.manxisuo.spriglauncher.ui.Screen
import io.github.manxisuo.spriglauncher.ui.theme.SprigLauncherTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(application as SprigApplication) }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { viewModel.back() }
        })
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> null
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SprigLauncherTheme(darkThemeOverride = dark) {
                LauncherApp(state, viewModel, onRequestHome = {
                    viewModel.homeRoleRequest()?.let(roleLauncher::launch)
                        ?: startSafely(viewModel.homeSettingsIntent())
                })
            }
        }
    }

    override fun onResume() { super.onResume(); viewModel.onResume() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); viewModel.navigate(Screen.HOME) }

    private fun startSafely(intent: Intent) {
        runCatching { startActivity(intent) }
            .getOrElse { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

@Composable
private fun LauncherApp(state: LauncherUiState, vm: MainViewModel, onRequestHome: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.status) {
        state.status?.let { snackbar.showSnackbar(it); vm.consumeStatus() }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.screen) {
                Screen.HOME -> HomeScreen(state, vm)
                Screen.ALL_APPS -> AllAppsScreen(state, vm)
                Screen.SETTINGS -> SettingsScreen(state, vm, onRequestHome)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: LauncherUiState, vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        ClockHeader()
        Spacer(Modifier.height(20.dp))
        if (state.pinned.isNotEmpty()) {
            SectionTitle("固定")
            AppGrid(state.pinned, state, vm, userScroll = false)
            Spacer(Modifier.height(14.dp))
        }
        SectionTitle(if (state.learning) "常用 · 正在学习使用习惯" else "常用")
        if (state.frequent.isEmpty()) {
            Text("从这里打开应用后，常用入口会逐渐稳定。", style = MaterialTheme.typography.bodyMedium)
        } else AppGrid(state.frequent, state, vm, userScroll = false)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.navigate(Screen.ALL_APPS) }, modifier = Modifier.weight(1f)) { Text("搜索 / 全部应用") }
            OutlinedButton(onClick = { vm.navigate(Screen.SETTINGS) }) { Text("设置") }
        }
    }
}

@Composable
private fun ClockHeader() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Date() } }
    Column {
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
        Text(DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(now), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllAppsScreen(state: LauncherUiState, vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val filtered = remember(state.entries, query) {
        if (query.isBlank()) state.entries else state.entries.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("全部应用") }, navigationIcon = { TextButton(onClick = { focus.clearFocus(); vm.navigate(Screen.HOME) }) { Text("返回") } })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("按名称或包名搜索") },
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有匹配的可启动应用") }
        else AppGrid(filtered, state, vm, userScroll = true, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: LauncherUiState, vm: MainViewModel, onRequestHome: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") }, navigationIcon = { TextButton(onClick = { vm.navigate(Screen.HOME) }) { Text("返回") } })
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingCard("默认桌面", if (state.homeRoleHeld) "已设为默认桌面" else "尚未设为默认桌面") {
                Button(onClick = onRequestHome) { Text(if (state.homeRoleHeld) "打开系统桌面设置" else "设为默认桌面") }
            }
            SettingCard("使用情况访问", if (state.usageAccess) "使用系统事件估算使用次数" else "仅统计从本桌面发起的打开请求") {
                Button(onClick = {
                    runCatching { context.startActivity(vm.usageSettingsIntent()) }
                        .getOrElse { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }) { Text("打开授权设置") }
            }
            SettingCard("主题", "跟随系统、浅色或深色") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(selected = state.themeMode == mode, onClick = { vm.setTheme(mode) }, label = {
                            Text(when (mode) { ThemeMode.SYSTEM -> "系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::manualResort) { Text("立即重新排序") }
                OutlinedButton(onClick = { confirmClear = true }) { Text("清空学习数据") }
            }
            Text("当前版本仅处理个人用户；工作资料、私密空间和多用户暂不支持。系统使用次数为近似值。", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空学习数据？") },
        text = { Text("会删除使用统计和排序快照，但保留固定、排除和主题设置。旧系统历史不会重新导入。") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearLearning() }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
}

@Composable
private fun SettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun AppGrid(entries: List<LaunchableEntry>, state: LauncherUiState, vm: MainViewModel, userScroll: Boolean, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxWidth().then(if (userScroll) Modifier else Modifier.height((((entries.size + 3) / 4) * 100).dp)),
        userScrollEnabled = userScroll,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry -> AppTile(entry, state, vm) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(entry: LaunchableEntry, state: LauncherUiState, vm: MainViewModel) {
    var menu by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<AppStats?>(null) }
    Column(
        modifier = Modifier.height(92.dp).combinedClickable(onClick = { vm.launch(entry) }, onLongClick = { menu = true }).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = state.icons[entry.id]
        if (icon != null) Image(icon.asImageBitmap(), entry.label, Modifier.size(52.dp))
        else Card(Modifier.size(52.dp), shape = RoundedCornerShape(14.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(entry.label.take(1)) } }
        Text(entry.label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
    }
    val pref = state.preferences.firstOrNull { it.entryId == entry.id }
    val excluded = state.preferences.any { it.packageName == entry.packageName && it.excluded }
    if (menu) AppMenu(entry, pref, excluded, onDismiss = { menu = false }, vm = vm, onStats = { stats = vm.statsFor(entry); menu = false })
    stats?.let { AppStatsDialog(entry, it, onDismiss = { stats = null }) }
}

@Composable
private fun AppMenu(entry: LaunchableEntry, pref: AppPreferenceEntity?, excluded: Boolean, onDismiss: () -> Unit, vm: MainViewModel, onStats: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.label) },
        text = {
            Column {
                TextButton(onClick = { vm.togglePinned(entry); onDismiss() }) { Text(if (pref?.pinnedOrder != null) "取消固定" else "固定") }
                if (pref?.pinnedOrder != null) Row {
                    TextButton(onClick = { vm.movePinned(entry, -1); onDismiss() }) { Text("上移") }
                    TextButton(onClick = { vm.movePinned(entry, 1); onDismiss() }) { Text("下移") }
                }
                TextButton(onClick = { vm.toggleExcluded(entry); onDismiss() }) { Text(if (excluded) "恢复常用推荐" else "排除常用推荐") }
                TextButton(onClick = onStats) { Text("统计详情") }
                TextButton(onClick = {
                    runCatching { context.startActivity(vm.appDetailsIntent(entry.packageName)) }
                        .getOrElse { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    onDismiss()
                }) { Text("系统应用详情") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun AppStatsDialog(entry: LaunchableEntry, stats: AppStats, onDismiss: () -> Unit) {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.label} · 统计") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (stats.source == UsageSource.SYSTEM) "来源：系统事件（估算使用次数）" else "来源：仅统计从本桌面发起的打开请求")
            Text("过去 7 天：${stats.sevenDayCount} 次")
            Text("最后使用：${stats.lastUsedAt?.let { formatter.format(Date(it)) } ?: "暂无记录"}")
            Text("当前评分：%.3f".format(stats.score))
            Text("覆盖起点：${stats.coverageStartAt?.let { formatter.format(Date(it)) } ?: "尚未建立"}")
            Text("上次排序：${stats.rankingUpdatedAt?.let { formatter.format(Date(it)) } ?: "尚未排序"}")
            Text("计分按 UTC 日桶中点近似，7 天半衰期。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
