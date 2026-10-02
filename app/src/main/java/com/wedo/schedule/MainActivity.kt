package com.wedo.schedule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wedo.schedule.data.entity.CourseEntity
import com.wedo.schedule.ui.component.LocalNavExtraBottomPadding
import com.wedo.schedule.ui.component.WedoBackground
import com.wedo.schedule.ui.component.WedoTabBar
import com.wedo.schedule.ui.component.WedoTabBarDefaults
import com.wedo.schedule.ui.screen.edit.AddCourseScreen
import com.wedo.schedule.ui.screen.imports.ImportWizard
import com.wedo.schedule.ui.screen.imports.JwImportActivity
import com.wedo.schedule.ui.screen.mine.AllTablesScreen
import com.wedo.schedule.ui.screen.mine.AppearanceScreen
import com.wedo.schedule.ui.screen.mine.EditTableScreen
import com.wedo.schedule.ui.screen.mine.ExportScreen
import com.wedo.schedule.ui.screen.mine.GeneralSettingsScreen
import com.wedo.schedule.ui.screen.mine.HolidaySettingsScreen
import com.wedo.schedule.ui.screen.mine.LicenseScreen
import com.wedo.schedule.ui.screen.mine.ReminderScreen
import com.wedo.schedule.ui.screen.mine.WedoSettingsScreen
import com.wedo.schedule.ui.screen.schedule.ScheduleScreen
import com.wedo.schedule.ui.screen.schedule.ScheduleViewModel
import com.wedo.schedule.ui.screen.today.TodayScreen
import com.wedo.schedule.ui.theme.LocalWedoCollapsed
import com.wedo.schedule.ui.theme.WedoThemeProvider
import com.wedo.schedule.util.AppPrefs
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.wedo.schedule.util.LocaleHelper.wrapDefault(newBase))
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"

        /**
         * REQ-P2-03：教务导入失败页 → 直接打开**全屏导入向导**（`ImportWizard`）。
         * 失败页与主界面不在同一 Activity，须经本 extra 通道跨 Activity 请求覆盖页。
         */
        const val EXTRA_OPEN_IMPORT = "extra_open_import"

        /** REQ-P2-03：教务导入失败页 → 直接打开**手动添加课程**（`AddCourse`）。 */
        const val EXTRA_OPEN_ADD_COURSE = "extra_open_add_course"

        /** [pendingOverlayRequest] 的取值：打开导入向导。 */
        const val PENDING_OVERLAY_IMPORT = "import"

        /** [pendingOverlayRequest] 的取值：打开发手动添加课程。 */
        const val PENDING_OVERLAY_ADD_COURSE = "add_course"

        fun intentForCourse(context: Context, courseId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_COURSE_ID, courseId)
            }
        }

        /**
         * REQ-P2-03：教务失败页「改用文件导入」→ 打开导入向导。
         *
         * MainActivity 为 `singleTask`：CLEAR_TOP + SINGLE_TOP 会把既有实例带到前台
         * 并回调 [MainActivity.onNewIntent]，不新建 Activity。
         */
        fun intentForImportWizard(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_IMPORT, true)
            }
        }

        /** REQ-P2-03：教务失败页「手动添加课程」→ 打开 AddCourse（同样复用既有实例）。 */
        fun intentForManualAdd(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_ADD_COURSE, true)
            }
        }

        val pendingImportTextState: androidx.compose.runtime.MutableState<String?> =
            androidx.compose.runtime.mutableStateOf(null)
        @Volatile var incomingImportText: String? = null
        var pendingImportText: String?
            get() = pendingImportTextState.value
            set(v) { pendingImportTextState.value = v }

        /**
         * REQ-P2-03：跨 Activity 的**覆盖页请求**（导入向导 / 手动添加）。
         *
         * 与 [pendingImportText] 同为 companion 快照状态：`onNewIntent` 写入、`AppRoot` 读取并消费。
         * 消费后置 null，避免重组重复压栈。
         */
        val pendingOverlayRequestState: androidx.compose.runtime.MutableState<String?> =
            androidx.compose.runtime.mutableStateOf(null)
        var pendingOverlayRequest: String?
            get() = pendingOverlayRequestState.value
            set(v) { pendingOverlayRequestState.value = v }
    }

    private val editingCourseFromIntent = MutableStateFlow<CourseEntity?>(null)
    val editingCourseFlow: StateFlow<CourseEntity?> = editingCourseFromIntent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 高刷新率(流畅优先): 按开关把窗口钉到屏幕最高刷率, 不表态会被省电逻辑限 60Hz
        com.wedo.schedule.util.HighRefreshRate.apply(this, com.wedo.schedule.util.AppPrefs.isHighRefresh(this))
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
            WedoThemeProvider(darkTheme = dark, themeKey = themeKey) {
                com.wedo.schedule.ui.theme.WedoDisplayProvider {
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
                        consumePendingImportText = { MainActivity.pendingImportText = null },
                        pendingOverlayRequest = pendingOverlayRequest,
                        consumePendingOverlayRequest = { MainActivity.pendingOverlayRequest = null }
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
        // REQ-P2-03：教务导入失败页经 extra 通道请求打开覆盖页（导入向导 / 手动添加）。
        if (intent?.getBooleanExtra(EXTRA_OPEN_IMPORT, false) == true) {
            pendingOverlayRequest = PENDING_OVERLAY_IMPORT
            intent.removeExtra(EXTRA_OPEN_IMPORT)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_COURSE, false) == true) {
            pendingOverlayRequest = PENDING_OVERLAY_ADD_COURSE
            intent.removeExtra(EXTRA_OPEN_ADD_COURSE)
        }
        val importText = intent?.getStringExtra(
            com.wedo.schedule.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT
        ) ?: com.wedo.schedule.MainActivity.incomingImportText
        if (!importText.isNullOrBlank()) {
            com.wedo.schedule.MainActivity.pendingImportText = importText
            com.wedo.schedule.MainActivity.incomingImportText = null
            intent?.removeExtra(com.wedo.schedule.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT)
        }
        val courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1L) ?: -1L
        if (courseId <= 0) return
        if (editingCourseFromIntent.value?.id == courseId) return
        lifecycleScope.launch {
            try {
                val course = (application as WedoApp).repository.getCourse(courseId)
                editingCourseFromIntent.value = course
            } catch (e: Throwable) {
                android.util.Log.e("Wedo", "deep link course lookup failed", e)
            }
        }
    }
}

/**
 * 底部导航三轴（V3 重设计 REQ-P0-01）。
 *
 * 由 `Schedule/Manage/Mine` 换轴为 `Today/Schedule/Settings`：
 *  - 首项 `Today` 为默认首屏（Q3 拍板：最高频需求「我下一节在哪」零点击直达）。
 *  - 标签键复用 `tab_today` / `tab_schedule`，并新增 `tab_settings`（6 语言）。
 *  - 图标统一**填充态**（HIG: *Prefer filled symbols*）。
 *
 * 枚举与原 `MainTabs` 同处根包；`WedoTabBar`（`ui.component`）读取本枚举的标签键与图标，
 * 故本枚举为 `public`（跨包可见）。
 */
enum class Tab(val labelRes: Int, val icon: ImageVector) {
    // 2026-10-01 真机反馈：课表是主角，「今天」是从课表衍生的待办视图。
    // 顺序与默认首屏统一为课表（原 V3「时间尺度」设计的第一原则被真机使用逻辑推翻）。
    Schedule(R.string.tab_schedule, Icons.Filled.CalendarMonth),
    Today(R.string.tab_today, Icons.Filled.Today),
    Settings(R.string.tab_settings, Icons.Filled.Settings)
}

/**
 * 覆盖页栈的页面标识（TASK-05 重置）。
 *
 * 旧 P0 的**过渡项 `Manage` 已移除** —— P4 已把「课表管理」的全部能力并入设置页
 * 四组（课表 / 显示 / 通知 / 关于），`ManagementPage` 与过渡入口一并删除。
 *
 * `Import` 是全屏导入向导（取代旧底部 `ImportSheet`，后者已删除）：今天页 CTA 与
 * 课表页 `＋` 都进此页。`Holiday` / `Reminder` 是 P4 设置页「显示 / 通知」两组进入的
 * 二级页（原为不可达页面，本批接入设置后复活）。
 */
private enum class OverlayScreen {
    // 2026-10-01 真机反馈：`About` 中间页已删——设置「关于」组四项各自直达
    // （更新原地弹结果 / 隐私应用内弹层 / 反馈开 GitHub Issues / 许可 License 页）。
    AddCourse, AllTables, EditTable, Theme, General, Export, License, Import, Holiday, Reminder
}

@Composable
private fun AppRoot(
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    deepLinkCourse: CourseEntity? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingImportText: String? = null,
    consumePendingImportText: () -> Unit = {},
    pendingOverlayRequest: String? = null,
    consumePendingOverlayRequest: () -> Unit = {}
) {
    // 默认首屏 = 课表（2026-10-01 真机反馈定调：打开就是为了看课表）。
    // 返回键「非课表 → 回课表」「课表 → 双击退出」的既有逻辑因此天然成立。
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
    val context = LocalContext.current
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()

    androidx.compose.runtime.LaunchedEffect(deepLinkCourse?.id) {
        if (deepLinkCourse != null) { editingCourse = deepLinkCourse; onDeepLinkConsumed() }
    }
    androidx.compose.runtime.LaunchedEffect(pendingImportText) {
        if (!autoImportTriggered && pendingImportText != null) { autoImportTriggered = true; pushOverlay(OverlayScreen.Import) }
    }
    // REQ-P2-03：教务失败页经 extra 通道请求打开覆盖页（导入向导 / 手动添加）。
    androidx.compose.runtime.LaunchedEffect(pendingOverlayRequest) {
        when (pendingOverlayRequest) {
            MainActivity.PENDING_OVERLAY_IMPORT -> {
                pushOverlay(OverlayScreen.Import)
                consumePendingOverlayRequest()
            }
            MainActivity.PENDING_OVERLAY_ADD_COURSE -> {
                editingCourse = null
                pushOverlay(OverlayScreen.AddCourse)
                consumePendingOverlayRequest()
            }
        }
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
    // v7.10.9: 课表页 = 首页 — 其他 Tab(今天/设置)按返回先回课表页,
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
            onBack = { popOverlay() }
        )
        return
    }
    if (topOverlay() == OverlayScreen.Export) {
        ExportScreen(onBack = { popOverlay() })
        return
    }
    if (topOverlay() == OverlayScreen.License) {
        LicenseScreen(onBack = { popOverlay() })
        return
    }
    // REQ-P4-01：设置页「显示」组 → 节假日与补班日二级页（原页面复活的接入点）。
    if (topOverlay() == OverlayScreen.Holiday) {
        HolidaySettingsScreen(onBack = { popOverlay() })
        return
    }
    // REQ-P4-01 / REQ-P5-03：设置页「通知」组 → 提醒二级页（课前提醒 + 通知权限）。
    if (topOverlay() == OverlayScreen.Reminder) {
        ReminderScreen(onBack = { popOverlay() })
        return
    }
    // REQ-P2-01: 全屏分步导入向导（取代旧底部 ImportSheet）。
    // 今天页 CTA「从教务系统导入」与课表页 ＋ 都经 pushOverlay(Import) 进此页。
    if (topOverlay() == OverlayScreen.Import) {
        ImportWizard(
            onDismiss = { popOverlay() },
            onFinish = { popOverlay(); currentTab = Tab.Schedule },
            // 手动添加: 先退掉向导再进 AddCourse（顺序不可换，否则 popOverlay 会把刚推入的 AddCourse 弹掉）。
            onManualAdd = { popOverlay(); pushOverlay(OverlayScreen.AddCourse) },
            // 教务直连: 关掉向导 + 落回课表页, 交给 JwImportActivity（导入成功自行入库后 finish 返回）。
            onJwImport = {
                popOverlay()
                currentTab = Tab.Schedule
                context.startActivity(Intent(context, JwImportActivity::class.java))
            },
            viewModel = mainVm
        )
        return
    }

    // 内容区底部留白 = tab bar 本体(52dp) + 系统导航栏安全区。
    // tab bar 以覆盖层形式常驻底部(见下), 内容滚动到屏幕边缘时最后一行会被其遮挡,
    // 故各页 contentPadding 需加上本值; 安全区高度与 tab bar 内部的 navigationBarsPadding
    // 同源, 保证「不遮挡内容末行」在带手势条/三键导航的设备上都成立。
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    WedoBackground(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalNavExtraBottomPadding provides (WedoTabBarDefaults.contentHeight + navBarBottom),
            // P0 决策(ADR-2): 移除「下滑收起」后 collapsed 恒为 false;
            // WedoWeekHeader 暂消费该值(零改动编译), P3 由顶栏大标题滚动收起接手。
            LocalWedoCollapsed provides false
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                MainTabs(
                    currentTab = currentTab,
                    pushOverlay = ::pushOverlay,
                    editingCourse = { editingCourse = it },
                    onAdd = { pushOverlay(OverlayScreen.Import) }
                )
            }
        }
        // 常驻底部 tab bar（不隐藏、不收起）—— 取代原浮动 Dock + 下滑收起联动。
        WedoTabBar(
            current = currentTab,
            onSelect = { currentTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MainTabs(
    currentTab: Tab,
    pushOverlay: (OverlayScreen) -> Unit,
    editingCourse: (CourseEntity?) -> Unit,
    onAdd: () -> Unit
) {
    when (currentTab) {
        Tab.Today -> TodayScreen(onOpenImport = onAdd, onEditCourse = { editingCourse(it) })
        Tab.Schedule -> ScheduleScreen(onGoImport = onAdd,
            onManualAdd = { pushOverlay(OverlayScreen.AddCourse) },
            onGoExport = { pushOverlay(OverlayScreen.Export) },
            onEditCourse = { editingCourse(it) })
        // REQ-P4-01：设置页合并为四组（课表 / 显示 / 通知 / 关于）；全部「进入下一页」
        // 动作经覆盖页栈（pushOverlay），返回键逐层只退一级。
        Tab.Settings -> WedoSettingsScreen(
            onOpenAllTables = { pushOverlay(OverlayScreen.AllTables) },
            onOpenAppearance = { pushOverlay(OverlayScreen.Theme) },
            onOpenGeneral = { pushOverlay(OverlayScreen.General) },
            onOpenExport = { pushOverlay(OverlayScreen.Export) },
            onOpenLicense = { pushOverlay(OverlayScreen.License) },
            onOpenImport = { pushOverlay(OverlayScreen.Import) },
            onManualAdd = { pushOverlay(OverlayScreen.AddCourse) },
            onEditCurrentTable = { pushOverlay(OverlayScreen.EditTable) },
            onOpenReminder = { pushOverlay(OverlayScreen.Reminder) },
            onOpenHoliday = { pushOverlay(OverlayScreen.Holiday) })
    }
}
