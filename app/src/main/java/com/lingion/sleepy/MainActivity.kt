package com.lingion.sleepy

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
import com.lingion.sleepy.ui.component.NavDockSpec
import com.lingion.sleepy.ui.component.PillNavigationBar
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.screen.manage.ManagementPage
import com.lingion.sleepy.ui.screen.mine.AllTablesScreen
import com.lingion.sleepy.ui.screen.mine.AppearanceScreen
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.GeneralSettingsScreen
import com.lingion.sleepy.ui.screen.mine.ExportScreen
import com.lingion.sleepy.ui.screen.mine.WedoAboutScreen
import com.lingion.sleepy.ui.screen.mine.LicenseScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleScreen
import com.lingion.sleepy.ui.screen.today.TodayScreen
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.lingion.sleepy.util.LocaleHelper.wrapDefault(newBase))
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
        fun intentForCourse(context: Context, courseId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_COURSE_ID, courseId)
            }
        }
        val pendingImportTextState: androidx.compose.runtime.MutableState<String?> =
            androidx.compose.runtime.mutableStateOf(null)
        @Volatile var incomingImportText: String? = null
        var pendingImportText: String?
            get() = pendingImportTextState.value
            set(v) { pendingImportTextState.value = v }
    }

    private val editingCourseFromIntent = MutableStateFlow<CourseEntity?>(null)
    val editingCourseFlow: StateFlow<CourseEntity?> = editingCourseFromIntent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 高刷新率(流畅优先): 按开关把窗口钉到屏幕最高刷率, 不表态会被省电逻辑限 60Hz
        com.lingion.sleepy.util.HighRefreshRate.apply(this, com.lingion.sleepy.util.AppPrefs.isHighRefresh(this))
        handleDeepLinkIntent(intent)
        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var themeMode by remember { mutableStateOf(AppPrefs.getThemeMode(this@MainActivity)) }
            var dark by remember { mutableStateOf(AppPrefs.isDarkMode(this@MainActivity, systemDark)) }
            val privacyPreferences = remember {
                getSharedPreferences("wedo_privacy", Context.MODE_PRIVATE)
            }
            var privacyAccepted by remember {
                mutableStateOf(privacyPreferences.getBoolean("accepted_v1", false))
            }
            androidx.compose.runtime.LaunchedEffect(systemDark) { dark = AppPrefs.isDarkMode(this@MainActivity, systemDark) }
            fun applyTheme() { dark = AppPrefs.isDarkMode(this@MainActivity, systemDark) }
            val deepLinkCourse by editingCourseFlow.collectAsState()
            val themeKey by AppPrefs.themeKeyFlow(this@MainActivity).collectAsState(initial = AppPrefs.getThemeKey(this@MainActivity))
            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey) {
                com.lingion.sleepy.ui.theme.WedoDisplayProvider {
                if (privacyAccepted) {
                    AppRoot(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            AppPrefs.setThemeMode(this@MainActivity, mode)
                            themeMode = mode
                            applyTheme()
                        },
                        deepLinkCourse = deepLinkCourse,
                        onDeepLinkConsumed = { editingCourseFromIntent.value = null },
                        pendingImportText = pendingImportText,
                        consumePendingImportText = { MainActivity.pendingImportText = null }
                    )
                } else {
                    WedoPrivacyConsent(
                        onAccept = {
                            privacyPreferences.edit().putBoolean("accepted_v1", true).apply()
                            privacyAccepted = true
                        },
                        onReject = { finish() },
                    )
                }
            }
        }
    }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val importText = intent?.getStringExtra(
            com.lingion.sleepy.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT
        ) ?: com.lingion.sleepy.MainActivity.incomingImportText
        if (!importText.isNullOrBlank()) {
            com.lingion.sleepy.MainActivity.pendingImportText = importText
            com.lingion.sleepy.MainActivity.incomingImportText = null
            intent?.removeExtra(com.lingion.sleepy.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT)
        }
        val courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1L) ?: -1L
        if (courseId <= 0) return
        if (editingCourseFromIntent.value?.id == courseId) return
        lifecycleScope.launch {
            try {
                val course = (application as SleepyApp).repository.getCourse(courseId)
                editingCourseFromIntent.value = course
            } catch (e: Throwable) {
                android.util.Log.e("Sleepy", "deep link course lookup failed", e)
            }
        }
    }
}

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Schedule(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    Manage(R.string.tab_manage, Icons.Outlined.Settings),
    Mine(R.string.tab_mine, Icons.Outlined.Person)
}

private enum class OverlayScreen {
    AddCourse, AllTables, EditTable, Theme, General, Export, About, License
}

@Composable
private fun AppRoot(
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    deepLinkCourse: CourseEntity? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingImportText: String? = null,
    consumePendingImportText: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(Tab.Schedule) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    // v7.10.8 返回键分层修复: overlayScreen 从单变量改成导航栈 —
    // 旧实现一个 BackHandler 把整摞 overlay 一次清空(通用设置→假期设置 按一次返回
    // 直接退两级); 栈化后每层只弹自己(通用→假期 返回 只回通用)。
    // 栈顶 = 当前显示页。pushOverlay 进页, popOverlay 退页。
    // 语言切换触发 Activity.recreate() 后仍需保留栈(旧注释决策 D2 同理),
    // editingCourse(CourseEntity)无法 Bundle 化: 编辑课程会话中不保存栈,
    //   旋转/进程恢复后退回主 Tab(丢弃编辑但安全), 避免恢复成"新增课程"空表单造成重复加课。
    val overlayScreenState = rememberSaveable(
        stateSaver = Saver<List<OverlayScreen>, List<OverlayScreen>>(
            save = { stack -> if (editingCourse == null) stack else emptyList() },
            restore = { it }
        )
    ) { mutableStateOf<List<OverlayScreen>>(emptyList()) }
    var overlayStack by overlayScreenState
    fun topOverlay(): OverlayScreen? = overlayStack.lastOrNull()
    fun pushOverlay(s: OverlayScreen) { overlayStack = overlayStack + s }
    fun popOverlay() { overlayStack = overlayStack.dropLast(1) }
    fun popToRoot() { overlayStack = emptyList() }
    fun hasOverlay(): Boolean = overlayStack.isNotEmpty()
    // overlayScreen 的伴生导航参数必须同步持久化, 否则旋转恢复后 overlay 存活但参数归 null:
    //   EditTable 的 tableId=null 语义为"编辑当前课表", 会静默改错表; pendingNewTableId 丢失
    //   会让新建空表遗留在 DB 且误显示删除按钮。三者均可 Bundle 化(Long?), 一并 rememberSaveable。
    var editTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingNewTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previousDefaultTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var autoImportTriggered by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    // 底栏形态(贴底/悬浮 Dock): AppRoot 持真值 — 设置页改, 底栏即时切
    val context = LocalContext.current
    var navDock by remember { mutableStateOf(AppPrefs.isNavDock(context)) }
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()

    androidx.compose.runtime.LaunchedEffect(deepLinkCourse?.id) {
        if (deepLinkCourse != null) { editingCourse = deepLinkCourse; onDeepLinkConsumed() }
    }
    androidx.compose.runtime.LaunchedEffect(pendingImportText) {
        if (!autoImportTriggered && pendingImportText != null) { autoImportTriggered = true; showAddSheet = true }
    }

    // 返回键: 只处理"有 overlay 在栈上"或"编辑课程"两种拦截; 主页面留给双击退出
    // (下方 exitBackHandler — enabled 互斥, 栈空时才接管)。
    BackHandler(enabled = hasOverlay() || editingCourse != null) {
        if (pendingNewTableId != null) {
            val discardId = pendingNewTableId!!; val fallback = previousDefaultTableId
            pendingNewTableId = null; previousDefaultTableId = null
            mainVm.discardNewTable(discardId, fallback)
            popToRoot(); editTableId = null
        } else { editingCourse = null; editTableId = null; popOverlay() }
    }

    // v7.10.8 主页面双击返回退出 — 第一次按 Toast 提示, 2 秒内再按才真退。
    // enabled 条件与上面互斥: 栈空且无编辑会话时才接管。
    // v7.10.9: 课表页 = 首页 — 其他 Tab(今日/管理/我的)按返回先回课表页,
    // 只有课表页本身才触发双击退出(用户 2026-09-02)。
    val ctxForExit = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = !hasOverlay() && editingCourse == null && currentTab != Tab.Schedule) {
        currentTab = Tab.Schedule
    }
    BackHandler(enabled = !hasOverlay() && editingCourse == null && currentTab == Tab.Schedule) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000L) {
            (ctxForExit as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(
                ctxForExit, R.string.exit_press_back_again, Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (topOverlay() == OverlayScreen.AddCourse || editingCourse != null) {
        AddCourseScreen(onBack = { popOverlay(); editingCourse = null }, onSaved = { popOverlay(); editingCourse = null; currentTab = Tab.Schedule }, editingCourse = editingCourse)
        return
    }
    if (topOverlay() == OverlayScreen.AllTables) {
        AllTablesScreen(onBack = { popOverlay() }, onCreateNewTable = {
            mainScope.launch {
                val previousId = mainVm.state.value.currentTable?.id
                val newId = mainVm.createEmptyTable(commitSelection = false)
                previousDefaultTableId = previousId; pendingNewTableId = newId; editTableId = newId; pushOverlay(OverlayScreen.EditTable)
            }
        }, onOpenEditTable = { tableId -> editTableId = tableId; pendingNewTableId = null; pushOverlay(OverlayScreen.EditTable) })
        return
    }
    if (topOverlay() == OverlayScreen.EditTable) {
        EditTableScreen(tableId = editTableId, pendingNewTableId = pendingNewTableId, onBack = { popOverlay(); editTableId = null; pendingNewTableId = null; previousDefaultTableId = null }, onDiscardPending = {
            val discardId = pendingNewTableId; val fallback = previousDefaultTableId; pendingNewTableId = null; previousDefaultTableId = null
            if (discardId != null) mainVm.discardNewTable(discardId, fallback)
            popOverlay(); editTableId = null
        }, onSaved = { popOverlay(); editTableId = null; pendingNewTableId = null; previousDefaultTableId = null }, onDeleted = { popOverlay(); editTableId = null; currentTab = Tab.Schedule })
        return
    }
    if (topOverlay() == OverlayScreen.Theme) {
        AppearanceScreen(onBack = { popOverlay() }, themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        return
    }
    if (topOverlay() == OverlayScreen.General) {
        GeneralSettingsScreen(
            onBack = { popOverlay() },
            navDock = navDock,
            onNavDockChange = { navDock = it }
        )
        return
    }
    if (topOverlay() == OverlayScreen.Export) {
        ExportScreen(onBack = { popOverlay() })
        return
    }
    if (topOverlay() == OverlayScreen.About) {
        WedoAboutScreen(onBack = { popOverlay() }, onOpenLicense = { pushOverlay(OverlayScreen.License) })
        return
    }
    if (topOverlay() == OverlayScreen.License) {
        LicenseScreen(onBack = { popOverlay() })
        return
    }
    var collapsed by remember { mutableStateOf(false) }
    val display = com.lingion.sleepy.ui.theme.LocalWedoDisplay.current
    androidx.compose.runtime.LaunchedEffect(currentTab) { collapsed = false }
    val scrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (consumed.y < -2f) collapsed = true
                if (consumed.y > 2f || available.y > 2f) collapsed = false
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    com.lingion.sleepy.ui.component.WedoBackground(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding provides 84.dp,
            com.lingion.sleepy.ui.theme.LocalWedoCollapsed provides collapsed
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).nestedScroll(scrollConnection)) {
                MainTabs(
                    currentTab = currentTab, setCurrentTab = { currentTab = it },
                    pushOverlay = ::pushOverlay, editingCourse = { editingCourse = it },
                    onAdd = { showAddSheet = true },
                    onCreateNewTable = {
                        mainScope.launch {
                            val previousId = mainVm.state.value.currentTable?.id
                            val newId = mainVm.createEmptyTable(commitSelection = false)
                            previousDefaultTableId = previousId; pendingNewTableId = newId
                            editTableId = newId; pushOverlay(OverlayScreen.EditTable)
                        }
                    }
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !collapsed || !navDock,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(
                animationSpec = if (display.motion) androidx.compose.animation.core.spring(dampingRatio = .76f)
                    else androidx.compose.animation.core.snap(), initialOffsetY = { it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(
                animationSpec = if (display.motion) androidx.compose.animation.core.spring(dampingRatio = .9f)
                    else androidx.compose.animation.core.snap(), targetOffsetY = { it })
        ) {
            com.lingion.sleepy.ui.component.WedoDock(
                settings = currentTab != Tab.Schedule,
                onSchedule = { currentTab = Tab.Schedule },
                onAdd = { showAddSheet = true },
                onSettings = { currentTab = Tab.Mine }
            )
        }
    }
    if (showAddSheet) {
        com.lingion.sleepy.ui.screen.imports.ImportSheet(
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showAddSheet = false },
            onJwImportRequested = {
                showAddSheet = false
                currentTab = Tab.Schedule
                context.startActivity(Intent(context, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java))
            },
            onImported = { showAddSheet = false; currentTab = Tab.Schedule },
            onManualAdd = { showAddSheet = false; pushOverlay(OverlayScreen.AddCourse) },
            viewModel = mainVm
        )
    }
}

@Composable
private fun MainTabs(
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    pushOverlay: (OverlayScreen) -> Unit,
    editingCourse: (CourseEntity?) -> Unit,
    onCreateNewTable: () -> Unit,
    onAdd: () -> Unit
) {
    when (currentTab) {
        Tab.Schedule -> ScheduleScreen(onGoImport = onAdd,
            onManualAdd = { pushOverlay(OverlayScreen.AddCourse) }, onEditCourse = { editingCourse(it) })
        Tab.Manage -> {
            val ctx = LocalContext.current
            ManagementPage(onJwImportRequested = { ctx.startActivity(Intent(ctx, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java)) },
                onCreateNewTableRequested = onCreateNewTable, onManualAdd = { pushOverlay(OverlayScreen.AddCourse) },
                onEditCurrentTable = { pushOverlay(OverlayScreen.EditTable) }, onImported = { setCurrentTab(Tab.Schedule) })
        }
        Tab.Mine -> com.lingion.sleepy.ui.screen.mine.WedoSettingsScreen(
            onManage = { setCurrentTab(Tab.Manage) },
            onOpenAllTables = { pushOverlay(OverlayScreen.AllTables) },
            onOpenAppearance = { pushOverlay(OverlayScreen.Theme) },
            onOpenGeneral = { pushOverlay(OverlayScreen.General) },
            onOpenExport = { pushOverlay(OverlayScreen.Export) },
            onOpenAbout = { pushOverlay(OverlayScreen.About) })
    }
}
