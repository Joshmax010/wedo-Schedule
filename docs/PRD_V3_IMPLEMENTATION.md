# wedo 课表 · 重设计实现 PRD（V3 Implementation）

> 需求编号：PRD-V3 · 状态：**待实施** · 撰写：产品经理 许清楚
> 输入依据（本 PRD 不含已定事项的重新论证）：
> 1. `docs/DESIGN_V3_INTERACTION.md` —— 交互/功能层（**主要依据**：信息架构、功能取舍、导入方案）
> 2. `docs/DESIGN_SPEC_V2.md` —— 视觉/令牌层（色彩、字阶、间距、圆角、组件、动效、无障碍）
> 3. `docs/PROJECT_HANDOFF.md` —— 项目交接（安全不变量、许可证、已知风险）
>
> 用户要求（原文）：把 `DESIGN_V3_INTERACTION.md` 规划的重设计**全部实现**。
> 用户明确：**不用担心真机验证，全部盲做完，之后由用户做真机验证。**

---

## 0. 项目信息

| 项 | 值 |
|---|---|
| Language | 中文 |
| 工程性质 | **既有 Android 工程增量改造**（非新建项目） |
| 工程根目录 | `D:/AAAAA/ai/wedo` |
| 技术栈 | Android + Kotlin + Jetpack Compose（`app/src/main/java/com/wedo/schedule/`） |
| 包名 | `com.wedo.schedule`（Debug：`com.wedo.schedule.debug`） |
| 服务对象 | 单一学校：吉林建筑大学（JLJU，新版正方 `jwglxt`） |
| 最低版本 | `minSdk = 26`；`compileSdk = targetSdk = 37` |
| 交付形态 | 源码 + 可构建 Debug APK；**本轮不做签名发布** |
| 原始需求复述 | 按 `DESIGN_V3_INTERACTION.md` §7 落地路线，实现 P0–P5 六个阶段的重设计；用户后续自行真机验证 |

---

## 1. 产品目标

### 1.1 要解决的问题

现状（源码实测，见 `DESIGN_V3_INTERACTION.md` §1）：

- **导航语义混乱**：3 个 Tab（课表/管理/我的）中有两个是「管理」，导入入口被拆散；浮动 Dock 把**动作**（＋）塞进**导航**，违反 HIG。
- **最高频需求未直达**：用户 80% 的打开意图是「我下一节在哪」，却必须先看整周网格。
- **特色功能可用性有缺口**：教务导入失败时只给纯文字建议，无降级出口；域名白名单硬编码在 UI 层，加学校易漏改。
- **能力闲置**：`TodayScreen`（381 行）是死代码；5 种小组件 + 4 个通知 receiver 全部 `enabled=false` 且无动态启用，产品层面完全不可用。
- **结构冗余**：8 个覆盖页 enum + 覆盖页栈；死资源 `import_qr` / `schedule_share_table` 零引用（`tab_today` 经 V3 重设计后由「今天」页复用而复活，不再属死资源）。

### 1.2 成功标准（可度量）

| # | 目标 | 成功标准 |
|---|---|---|
| G1 | 导航对齐时间尺度 | 底部 tab bar 常驻、只做导航；3 个 Tab = 今天/课表/设置；全 App 无浮动 Dock、无下滑收起；返回键逐层只退一级 |
| G2 | 高频需求零点击直达 | 打开 App 首屏即「今天」页并显示下一节课倒计时；今日无课/无课表有明确空态与引导 |
| G3 | 特色功能可用性不回退 | 教务导入链路每个失败分支都有**可点击的下一步出口**；白名单下沉数据层且由契约测试锁定；导入成功/失败路径均有单测覆盖 |
| G4 | 能力复活与结构清理 | 小组件 2 种可添加（receiver 启用）；上课前提醒可开启；死代码/死资源清零 |

> 说明：G4 的「小组件可添加」需真机确认，本轮以「receiver 启用 + 配置链路可达 + 单测通过」为交付口径（见 §6 风险）。

---

## 2. 用户故事（角色：学生）

| # | 用户故事 | 验收标准（可测试） |
|---|---|---|
| US-1 | 作为学生，我**打开 App 就想知道下一节课在哪**，这样我不用翻整周网格。 | 打开 App 默认落在「今天」Tab；存在下一节课时首屏可见「下一节课倒计时卡」，含课程名/时间/教室/倒计时；倒计时按分钟刷新。 |
| US-2 | 作为学生，我**想一眼看清今天一天的安排**，包括正在上的课。 | 「今天」页纵向时间轴按开始节次排序；当前进行中的课程有高亮标识；无冲突课整宽，冲突课并排分栏（复用 `ConflictLayoutEngine.weekLaneRows`）。 |
| US-3 | 作为学生，我**第一次装 App 时不知道从哪导入**，希望有个明确入口。 | 无任何课表时，进入「今天」Tab 显示全屏引导，主 CTA 为「从教务系统导入」；点击进入教务导入向导，而非平铺列表。 |
| US-4 | 作为学生，我**从教务导入失败时想知道还能怎么办**，而不是只看到一段文字。 | 教务导入失败页出现两个可点击按钮：**改用文件导入**、**手动添加课程**；点击分别进入对应导入路径。 |
| US-5 | 作为学生，我**想快速找到某门课什么时候上**，翻周很痛苦。 | 「课表」页切到「课程」视图后可输入关键字；支持中文名包含匹配与拼音首字母匹配（接入 `PinyinMatcher.match`）；结果按课程名聚合。 |
| US-6 | 作为学生，我**想看清整个学期的分布**（考试周、起止）。 | 「课表」页切到「学期」视图，纵向列出全部周次概览；当前周有高亮。 |
| US-7 | 作为学生，我**想快速跳到某一周并知道学期进度**，不迷失在翻周里。 | 点击周标题打开**全屏周次选择器**，含周次网格、学期进度条、「回到本周」按钮；选择后课表定位到该周。 |
| US-8 | 作为学生，我**把导入/导出/外观都放在设置里就够**，不想在两个 Tab 里找。 | 「设置」Tab 含四组：课表 / 显示 / 通知 / 关于；原「管理」「我的」入口全部可在设置内到达；全 App 无重复入口。 |
| US-9 | 作为学生，我**想在桌面看到今天的课**。 | 系统小组件列表可见 2 种 wedo 小组件（今日、周网格）；添加后显示对应内容。 |
| US-10 | 作为学生，我**想在上课前收到提醒**。 | 「设置 → 通知」可开启「上课前提醒」；开启后有通知权限申请；到点触发通知（真机验证）。 |

---

## 3. 需求池（P0 / P1 / P2 优先级）

> 优先级口径：**P0 = Must have**（本轮必交付，核心骨架/红线）、**P1 = Should have**（高价值，同等交付）、**P2 = Nice to have**（有则更好，可延后）。
> 依赖列：标 ✅ 表示该需求**阻塞下游**，未完成则下游无法验收。

### 3.1 P0 · 导航重构

| 编号 | 名称 | 描述 | 涉及代码位置 | 验收标准 | 依赖 |
|---|---|---|---|---|---|
| REQ-P0-01 | Tab 枚举重构 | `Tab` 由 `Schedule/Manage/Mine` 改为 `Today/Schedule/Settings`；标签用「今天/课表/设置」；图标：今天=`Today`、课表=`CalendarMonth`、设置=`Settings`（填充态图标）。 | `MainActivity.kt`（`Tab` enum 172–176、`MainTabs` 378–404） | 编译通过；3 个 Tab 顺序为 今天→课表→设置；无遗留 `tab_manage`/`tab_mine` 引用（可保留字符串资源但代码不再引用）。 | ✅ 阻塞 P1/P4 |
| REQ-P0-02 | 移除浮动 Dock | 删除 `WedoDock` 的挂载与其 `AnimatedVisibility` 容器；「＋」不再出现在底部（改为课表页顶部导航栏右侧动作，见 REQ-P3-06）。 | `MainActivity.kt`（344–360 `WedoDock` 调用）；`ui/component/`（`WedoDock`） | 运行期底部无凸起胶囊/＋；无 `WedoDock` 调用点；`onAdd` 仅由顶部动作触发。 | 依赖 REQ-P0-01 |
| REQ-P0-03 | 移除「下滑收起」行为 | 删除联动 `collapsed` 状态的 `nestedScroll` 连接与 Dock/顶栏高度联动；tab bar 与顶栏常驻不隐藏。 | `MainActivity.kt`（307–322 `scrollConnection`、`LocalWedoCollapsed` 提供点）；`WedoWeekHeader.kt`（收起逻辑） | 上下滑动时 tab bar 恒定可见；无 `collapsed` 驱动的隐藏动画。 | 依赖 REQ-P0-02 |
| REQ-P0-04 | 底部 tab bar 常驻（Liquid Glass） | 新增底部 tab bar（3 项），材质为 Liquid Glass；内容区底部留白适配；点击切换 Tab。 | `MainActivity.kt`；`ui/component/`（新建 tab bar 组件）；`docs/DESIGN_SPEC_V2.md §5.1` | tab bar 常驻、3 项、填充态图标；不遮挡内容末行（`navigationBarsPadding` + 底部留白）；选中态用 `accent`。 | 依赖 REQ-P0-01 |
| REQ-P0-05 | 返回键分层 | 保留并适配：非「课表」Tab 时返回键回「课表」Tab；「课表」Tab 且无覆盖页时双击退出；有覆盖页/编辑会话时逐层退。 | `MainActivity.kt`（`BackHandler` 233–261） | 单测/契约测试：任意 Tab 按返回 → 落在课表 Tab；课表 Tab 连按两次返回 → finish。 | 依赖 REQ-P0-01 |

### 3.2 P1 · 今天页重写

| 编号 | 名称 | 描述 | 涉及代码位置 | 验收标准 | 依赖 |
|---|---|---|---|---|---|
| REQ-P1-01 | 复活 `TodayScreen` | 将 `TodayScreen` 接入 `Tab.Today`（当前只 import 未使用）；删除其「死代码」身份。 | `ui/screen/today/TodayScreen.kt`；`MainActivity.kt`（import 58、`MainTabs`） | 进入「今天」Tab 渲染 `TodayScreen`；无「未使用」告警；`TodayScreen` 有测试覆盖。 | 依赖 REQ-P0-01 |
| REQ-P1-02 | 下一节课倒计时卡 | 新增卡片：课程名 / 时间（起止）/ 教室 / 剩余倒计时；无下一节课时切换文案。 | `TodayScreen.kt`（新增 Composable）；数据来自 `ScheduleViewModel.state` | 有下一节课时显示倒计时且每 ≤1 分钟刷新；当天已无课/学期外显示对应空文案；数值可由纯函数单测验证（给定 now + 课程 → 倒计时）。 | ✅ 阻塞 US-1 |
| REQ-P1-03 | 今日时间轴 | 纵向列表按 `startNode` 排序；当前进行中课程高亮；冲突课程并排分栏。 | `TodayScreen.kt`（既有 `TodayCourseCard` + `weekLaneRows` 复用） | 排序正确；进行中课程有区别于普通项的高亮；冲突行 `laneCount>1` 时并排分栏且栏间细分隔线。 | 依赖 REQ-P1-01 |
| REQ-P1-04 | 今日空态（无课/学期外/无课表分流） | 区分三态：无课表 / 本日无课 / 学期外（BEFORE_START / AFTER_END）；「本日无课」附上下文提示（如「本周还有 N 天有课」）。 | `TodayScreen.kt`（`EmptyToday`）；`util/DateUtils`（`semesterStatus`） | 三态文案互斥且正确；「本日无课」统计本周有课天数（无课表/学期外不显示该统计）。 | 依赖 REQ-P1-01 |
| REQ-P1-05 | 首次无课表全屏引导 | 无任何课表时，「今天」页全屏引导，主 CTA「从教务系统导入」（触发导入向导）。 | `TodayScreen.kt`；`ui/screen/imports/`（向导入口） | 无课表时显示引导与主 CTA；点击进入教务导入（向导 Step2 教务流程）；有课表时不显示。 | 依赖 REQ-P2-01 |

### 3.3 P2 · 导入向导 + 教务导入可用性补强

| 编号 | 名称 | 描述 | 涉及代码位置 | 验收标准 | 依赖 |
|---|---|---|---|---|---|
| REQ-P2-01 | 导入向导（全屏分步） | 导入从底部 sheet 平铺改为全屏分步：Step1 选择来源 → Step2 来源专属流程 → Step3 预览+冲突处理 → Step4 完成跳课表。步骤间用**水平滑动转场**。 | `ui/screen/imports/ImportSheet.kt`（重构）；`MainActivity.kt`（`showAddSheet` 362–375） | 入口触发全屏向导；4 步可达；转场为水平滑动（非淡入淡出）；Step3 复用 `ImportPreviewDialog` 能力。 | ✅ 阻塞 REQ-P1-05/P2-03 |
| REQ-P2-02 | Step1 来源列表（教务首选） | Step1 首项为「从教务系统导入 ★特色 / 吉林建筑大学 · 需登录」带说明；其后为 从文件导入 / 粘贴文本导入 / 手动添加课程。 | 向导 Step1 Composable | 首项为教务且带「特色」标注与副说明；点击各来源进入对应 Step2。 | 依赖 REQ-P2-01 |
| REQ-P2-03 | 教务失败页可操作化 | 教务导入失败时，错误页除文案外给**两个可点击按钮**：「改用文件导入」「手动添加课程」；文案改为「原因 + 下一步动作」结构。 | `ui/screen/imports/JwImportActivity.kt`（`errorMsg` 渲染 300–321、`DiagMapper` 350–403）；`JwParseDiagnostics.kt` | 失败分支渲染 2 个按钮；点击分别跳文件导入/手动添加；文案含「下一步」动作句；**不改变**「只回显 error code + 诊断特征、不回显响应内容」约束。 | 依赖 REQ-P2-01 |
| REQ-P2-04 | 域名白名单下沉数据层 | 将 `JwWebViewLoginScreen` 中硬编码的 `VERIFIED_AUTH_HOSTS` / `VERIFIED_JS_BRIDGE_HOSTS` 下沉为 `JwSchoolInfo` 字段（如 `authHosts` / `jsBridgeHosts`），UI 层从数据读取。 | `ui/screen/imports/JwWebViewLoginScreen.kt`（62–70 常量）；`data/jw/JwSchoolInfo.kt`；`data/jw/JwImportViewModel.defaultWedoSchools()` | 运行期白名单来自 `JwSchoolInfo`；JLJU 三项域名（`lxr.jlju.edu.cn`、`cas.jlju.edu.cn`、`jwxt.jlju.edu.cn`）语义不变；**严禁**通配；新增学校仅改数据一处。 | ✅ 阻塞 REQ-P2-05 |
| REQ-P2-05 | 白名单契约测试 | 契约测试锁定「每所学校必须声明 authHosts 与 jsBridgeHosts 且非空、非通配」。 | `app/src/test/`（新增契约测试） | 存在测试：遍历 `defaultWedoSchools()` 断言每校 host 集合非空且不含 `*`/`*.`；新增无白名单学校时测试失败。 | 依赖 REQ-P2-04 |
| REQ-P2-06 | 会话上下文保留 | 失败重试时 `Stage` 回退到**上一有效 Stage**（如登录页失败 → 停在 `WebViewLogin` 而非回 `SelectSchool`），不从头走学校选择。 | `ui/screen/imports/JwImportActivity.kt`（`Stage` sealed 338–342、状态流转 103–297） | 重试路径不回到「选择学校」；`Stage` 状态机有单测覆盖关键转移。 | 依赖 REQ-P2-01 |
| REQ-P2-07 | 重试抓取 + 提示 | 抓取失败（`NO_TABLE_CONTAINER` 等）时提供「重试抓取」按钮，并提示「请停在个人课表页」。 | `JwImportActivity.kt`、`JwWebViewLoginScreen.kt`（`CaptureBar`） | 失败页含「重试抓取」；提示文案含停留课表页指引。 | 依赖 REQ-P2-03 |
| REQ-P2-08 | 学期选择引导 | `EMPTY_SEMESTER` 时提示用户确认教务侧学期是否已切换到正确学期。 | `JwParseDiagnostics.kt`（`EMPTY_SEMESTER`）、`DiagMapper` | 该分类文案含「确认学期」动作指引。 | 依赖 REQ-P2-03 |

### 3.4 P3 · 课表页三视图 + 全屏周次选择器 + 课程搜索

| 编号 | 名称 | 描述 | 涉及代码位置 | 验收标准 | 依赖 |
|---|---|---|---|---|---|
| REQ-P3-01 | 三视图分段控件 | 课表页新增分段控件：`周` / `学期` / `课程`；默认「周」。 | `ui/screen/schedule/ScheduleScreen.kt` | 三选项可切换且状态持久于会话；默认周视图；切换不丢失当前周。 | ✅ 阻塞 P3-02/03/04 |
| REQ-P3-02 | 学期视图 | 「学期」视图纵向列出全部周次概览，当前周高亮，用于看整学期分布。 | `ScheduleScreen.kt`（新增视图） | 列出 1..maxWeek；当前周有高亮；无课表时显示空态。 | 依赖 REQ-P3-01 |
| REQ-P3-03 | 课程视图（列表） | 「课程」视图按课程名聚合展示列表。 | `ScheduleScreen.kt`、`data/repository`（聚合数据） | 同名课程聚合为一项；含教师/教室等摘要；点击可进入相关课程详情。 | 依赖 REQ-P3-01 |
| REQ-P3-04 | 课程搜索（PinyinMatcher） | 课程视图顶部搜索框，接入 `PinyinMatcher.match(name, sortKey, query)`；支持中文包含 + 拼音首字母。 | `ScheduleScreen.kt`、`util/PinyinMatcher.kt` | 输入中文子串命中；输入拼音首字母（如 `gdsx`）命中「高等数学」；空查询返回全部；有纯函数单测。 | 依赖 REQ-P3-03 |
| REQ-P3-05 | 全屏周次选择器 | 点击周标题打开**全屏**选择器（替换现 `DropdownMenu`）：周次网格 + 学期进度条 + 「回到本周」；选中后定位。 | `ui/screen/schedule/`（周顶栏/新组件）、`WedoWeekHeader.kt` | 全屏呈现；含进度条与「回到本周」；选择周后课表定位且关闭选择器；替换掉旧 `DropdownMenu`。 | 依赖 REQ-P3-01 |
| REQ-P3-06 | 课表页顶部动作 | 课表页顶部导航栏右侧放 `＋`（添加课程）与 `⋯`（导入/导出/周次范围）；大标题 `第 N 周` + 副标题日期，滚动收起为 inline。 | `ScheduleScreen.kt`、`WedoWeekHeader.kt` | 顶部有 ＋ 与 ⋯；＋ 触发添加课程；大标题滚动收起为 inline。 | 依赖 REQ-P0-02 |
| REQ-P3-07 | 冲突课程非颜色标识 | 冲突课程除描边外增加 `!` 符号（WCAG：颜色不得为唯一信息载体）。 | `ScheduleScreen.kt`、`ui/component/WedoCourseCard.kt` | 冲突课程块可见 `!`；具备无障碍语义标签。 | 依赖 REQ-P3-01 |

### 3.5 P4 · 设置页合并重排 + 死代码清理

| 编号 | 名称 | 描述 | 涉及代码位置 | 验收标准 | 依赖 |
|---|---|---|---|---|---|
| REQ-P4-01 | 合并「管理」+「我的」为「设置」 | 设置页四组：**课表**（当前课表/全部课表与学期/导入课表/导出课表/手动添加课程）、**显示**（主题模式与强调色/显示设置/节假日/冲突样式）、**通知**（上课前提醒）、**关于**（检查更新/隐私/开源许可/反馈）。 | `ui/screen/mine/MineScreen.kt`、`WedoSettingsScreen.kt`；`ui/screen/manage/ManagementPage.kt`；`MainActivity.kt`（`MainTabs` 390–403） | 设置页含四组、条目齐全；原管理/我的入口全部可在设置内到达；全 App 无重复入口（同一能力仅一处）。 | 依赖 REQ-P0-01 |
| REQ-P4-02 | 覆盖页栈改标准导航 | 8 个 `OverlayScreen` enum 的覆盖页栈改为标准 push/pop 导航；返回键逐层退一级、语义可预期。 | `MainActivity.kt`（`OverlayScreen` 178–180、栈 200–306） | 进出各页面返回行为一致（每层只退一级）；无覆盖页栈遗留在主骨架。 | 依赖 REQ-P0-05 |
| REQ-P4-03 | 删除死资源 | 删除字符串 `import_qr` / `schedule_share_table`（零引用）。**注**：`tab_today` 原列入死资源，但 V3 重设计后「今天」页复活，该键由死资源转为**存活资源**（新 `Tab.Today` 标签），**不再删除**。 | `res/values*/strings.xml`（多语言：values、zh-rCN、zh-rTW、es、ja、en） | 全语言删除 `import_qr` / `schedule_share_table` 两项；`tab_today` 保留；构建通过；grep 零引用确认。 | — |
| REQ-P4-04 | 删除死代码（服务） | 删除 `FluidCloudService` / `ScrollStripService`（随小组件裁减）及其 Manifest 声明。 | `widget/notification/FluidCloudService.kt`、`widget/ScrollStripService.kt`、`AndroidManifest.xml`（296–307） | 类文件删除；Manifest 无残留 service 声明；构建通过。 | 依赖 REQ-P5-01 |
| REQ-P4-05 | 处理不可达 Widget 屏幕 | `WidgetManagementScreen` / `WidgetEditScreen` 不可达，随小组件裁到 2 种后重写或删除配置流程。 | `ui/screen/widget/WidgetManagementScreen.kt`、`WidgetEditScreen.kt`；`widget/WidgetConfigureActivity.kt` | 无不可达屏幕；小组件配置流程指向存活的 2 种；构建通过。 | 依赖 REQ-P5-01 |

### 3.6 P5 · 小组件 5→2 复活 + 上课前提醒复活

| 编号 | 名称 | 描述 | 涉及代码位置 | 验收标准 | 依赖 |
|---|---|---|---|---|---|
| REQ-P5-01 | 小组件裁到 2 种 | 保留「今日」「周网格」，删除 `WeekList` / `WeekView` / `TwoDay` 三种（含大小变体，共 3 种 × 2 尺寸 + 相应资源/配置）。 | `widget/WeekListWidget.kt`、`WeekViewWidget.kt`、`TwoDayWidget.kt`、`WeekList*WidgetReceiver.kt`、`WeekView*WidgetReceiver.kt`、`TwoDay*WidgetReceiver.kt`；`AndroidManifest.xml`（119–221）；`res/xml/*_widget_info.xml` | 存活 2 种：`TodayWidgetReceiver`、`WeekGridWidgetProvider`（含小尺寸变体按现保留策略）；三类删除后无残留引用；构建通过。 | ✅ 阻塞 P4-04/05 |
| REQ-P5-02 | 启用 2 种小组件 receiver | 将存活小组件 receiver 的 `android:enabled` 置为 `true`（当前全部 `false`，且全仓无 `setComponentEnabledSetting`）。 | `AndroidManifest.xml`（93–117、171–195） | 存活 receiver `enabled="true"`；系统小组件列表可见 2 种（真机验证）；静态契约测试断言存活 receiver 已启用。 | 依赖 REQ-P5-01 |
| REQ-P5-03 | 启用「上课前提醒」 | 启用 `BeforeClassScheduleReceiver` / `BeforeClassNotifyReceiver`（含 `BootReceiver` 开机重排）；「设置 → 通知」提供开关与通知权限申请。 | `AndroidManifest.xml`（224–250）；`widget/notification/*`；`ui/screen/mine/ReminderScreen.kt` | 相关 receiver `enabled="true"`；设置项可开启；开启触发权限申请；到点触发通知（真机验证）。 | 依赖 REQ-P4-01 |
| REQ-P5-04 | 删除「每日提醒」 | 删除 `DailyNotifyReceiver`（与「上课前提醒」重叠且价值低）。 | `widget/notification/DailyNotifyReceiver.kt`、`AndroidManifest.xml`（234–238）、相关调度代码 | 类与 Manifest 声明删除；无残留引用；构建通过。 | 依赖 REQ-P5-01 |

---

## 4. 不做的事（Out of Scope）

明确列出本次**不做**，避免范围蔓延：

| # | 不做项 | 原因 |
|---|---|---|
| OOS-1 | **不新增第二所学校 / 不改教务协议** | 仅维护 JLJU 新版正方；白名单只做「下沉」，不扩域、不加学校。 |
| OOS-2 | **不实现云同步 / 账号中心 / 埋点 / 广告 / 后端** | 与产品定位（本地优先、无后端）冲突。 |
| OOS-3 | **不做签名 Release / 发布工作流 / GitHub Release** | 发布门（`docs/RELEASING.md`）未闭环，非本轮范围。 |
| OOS-4 | **不做拍板项之外的交互再设计** | 浮动 Dock 移除、Tab 语义变更、小组件 2 种已拍板；不再扩大删减/改版范围。 |
| OOS-5 | **不做「冲突一键处理」批量操作** | `DESIGN_V3_INTERACTION.md §4.3` 列为新增项，但不在 §7 落地路线的 P0–P5，本轮不做（记为待确认，见 §8）。 |
| OOS-6 | **不改 Room 数据库 schema / 不新增迁移** | 本轮无实体改动需求；现有 Room v5 明确迁移保持不动。 |
| OOS-7 | **不做真机/模拟器验收** | 用户明确「全部盲做完，之后由用户真机验证」；本轮以单测 + 源码契约测试为口径。 |
| OOS-8 | **不删除保留能力**（`DESIGN_V3_INTERACTION §4.1` 的 40 项） | 均为红线/核心能力，任何阶段不得使其不可用。 |
| OOS-9 | **不复活 5 种小组件中被砍的 3 种** | 已拍板删除。 |
| OOS-10 | **不做每门课独立字段勾选 / 用户照片背景** | 与现行产品契约冲突（字段为全局模板）；非本轮需求。 |

---

## 5. UI 设计要点（引用 `DESIGN_SPEC_V2.md` 令牌）

> 以下仅描述**新增/重写页面**的结构与状态；色彩/字阶/间距/圆角一律引用令牌，不得写死。

### 5.1 全局（tab bar）

- **结构**：底部 tab bar，3 项（今天 / 课表 / 设置），**常驻不隐藏**（HIG：*Don't disable or hide tab bar buttons*）。
- **材质**：Liquid Glass，**仅控件层**（`DESIGN_SPEC_V2 §2.1/§5.1`），内容卡片保持实色。
- **图标**：填充态（`§5.1`）；选中态用 `accent`（`§2.3 #007AFF`）。
- **触控目标** ≥ 44×44dp（`§7`）。

### 5.2 今天页（`TodayScreen` 重写）

| 区块 | 结构 | 令牌 |
|---|---|---|
| 下一节课卡 | 课程名（Headline）· 时间/教室（Caption1）· 倒计时（Title2，强调色） | Title2 = 22/28；Headline = 17/22 Semibold；Caption1 = 12/16 |
| 今日时间轴 | `LazyColumn`，每项 = 时间槽（宽 76dp） + 课程名 + 元信息；左侧 4px 实色条 | 卡片圆角 16dp；课程块 8dp；卡内 padding 16dp；项间距 8dp |
| 进行中高亮 | 当前节次项加区分度（如左侧强调色条加粗/底色微调） | `accent`；**不得**仅靠颜色区分（`§7`） |
| 冲突分栏 | `laneCount>1` 并排分栏，栏间 0.5pt `separator` 竖线 | `separator` `#3C3C43@29%`；圆角沿用 |

**状态**：

| 状态 | 呈现 |
|---|---|
| 加载中 | 骨架/占位（浅灰块），不闪烁跳变 |
| 正常有课 | 倒计时卡 + 时间轴 |
| 本日无课 | 空态卡：图标（中性灰，`tertiaryLabel`）+ 标题 + 「本周还有 N 天有课」上下文 |
| 学期外 | 空态卡：`BEFORE_START`→「学期未开始」/ `AFTER_END`→「学期已结束」 |
| 首次无课表 | 全屏引导 + 主 CTA「从教务系统导入」（Filled 按钮，`§5.4`） |
| 错误 | 顶部错误条/卡片（`errorContainer`），提供重试 |

### 5.3 导入向导（全屏分步）

| 步骤 | 结构 | 令牌/交互 |
|---|---|---|
| Step1 选择来源 | 列表，首项教务（★特色 + 副说明），其后文件/文本/手动 | 卡片圆角 16dp；行间距 8dp；教务项用 Tinted 强调 |
| Step2 来源专属 | 教务：学校 → 登录 → 抓取；文件：选择器；文本：粘贴框 | 复用现有流程 |
| Step3 预览 + 冲突 | 复用 `ImportPreviewDialog` 能力 | 冲突项加 `!`（非仅颜色） |
| Step4 完成 | 成功反馈 → 跳课表页并定位新学期 | 300ms 滑动转场 |

**转场**：步骤间**水平滑动**（前进/后退空间隐喻），不用淡入淡出（`DESIGN_V3 §5.4`、`DESIGN_SPEC_V2 §6` 页面转场 300ms）。

**教务失败页状态**（REQ-P2-03）：

```
┌───────────────────────────────┐
│  ⚠ 导入未完成                  │
│  原因：<分类文案>              │
│  下一步：请停在「个人课表」页   │
│  [ 重试抓取 ]                  │   ← Tinted
│  [ 改用文件导入 ]  [ 手动添加 ] │   ← Plain/Text 按钮
└───────────────────────────────┘
```

### 5.4 全屏周次选择器

| 元素 | 结构 | 令牌 |
|---|---|---|
| 周次网格 | N 列（按 maxWeek 自适应），当前周高亮 | 单元格圆角 14dp；间距 8dp |
| 学期进度条 | 水平条，进度 = 当前周 / maxWeek | `accent` 前景 + `surfaceContainer` 底 |
| 回到本周 | 底部按钮（Filled 或 Tinted） | `§5.4` |

### 5.5 课表页大标题与顶部动作

- 大标题 `第 N 周`（LargeTitle 34/41）+ 副标题日期（Subheadline 15/20）；滚动收起为 inline（Title3）。
- 顶部右：`＋`（添加课程，内容层动作，HIG 允许）、`⋯`（导入/导出/周次范围）。
- 页面左右外边距 16dp；区块间距 24dp。

### 5.6 无障碍（贯穿，`DESIGN_SPEC_V2 §7`）

- 文字对比度 ≥ 4.5:1，**课程块内文字 ≥ 6.5:1**；图标 ≥ 3:1。
- 触控目标 ≥ 44×44dp；语义化标签（TalkBack）；Dynamic Type 放大不截断（横向改纵向堆叠）。
- 颜色非唯一信息载体：冲突课程除描边外必须有 `!`（REQ-P3-07）。
- 所有动效受「设置 → 减弱动效」总开关约束。

---

## 6. 风险与缓解

### 6.1 核心风险：无真机 / 无模拟器

| 风险 | 影响 | 缓解 |
|---|---|---|
| P0 改的是**全 App 骨架**，只能靠单测/审查验证，无法目视 | 布局/返回栈/留白缺陷可能在用户真机才暴露 | 以**源码契约测试**锁定结构（Tab 集合、返回栈转移、receiver 启用、白名单非通配、无死资源）；交付时明确标注「未真机验证」 |
| P2 教务导入兼容性改进**无法真机验证**，而它是「必须可用」的特色功能 | 若真机发现解析/登录异常，可能影响红线 | 严格不改安全与解析核心，只做**可用性补强**（降级出口/白名单下沉/文案）；全部走纯离线单测（`JwParserRegistry`/`JwNewZfParser`/`JwParseDiagnostics`） |
| P5 小组件能否添加**必须真机确认** | 无法在本轮证明「桌面可见」 | 本轮交付口径降级为「receiver 启用 + 配置链路可达 + 契约测试断言 enabled」；真机添加列入用户后续验证清单 |
| 通知到点触发依赖系统调度 | 无法在本轮证明 | 同上：交付「receiver 启用 + 开关可开启 + 权限申请」，真机验证列入清单 |

### 6.2 功能/结构风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 移除浮动 Dock / 改 Tab 语义破坏老用户肌肉记忆 | 用户找不到导入入口 | 已拍板；用**空态引导**补偿（REQ-P1-05、US-8），设置页导入入口醒目 |
| 白名单下沉引入回归（误放行/误拦截） | 登不进教务 / 安全漏洞 | 契约测试锁「非空、非通配」；JLJU 三域名语义逐字保持一致；**严禁**通配 |
| 覆盖页栈改标准导航引入返回键回归 | 返回栈错乱 | 保留并测试逐层退一级；关键状态（editTableId 等）仍需可恢复 |
| 删除死代码误伤存活引用 | 构建失败 / 功能丢失 | 删除前逐条追调用链（`PROJECT_HANDOFF §10.6`）；删除后构建 + 单测把关 |
| `JwNewZfParser.parseWeekStr` 回落到 `1–16` 可能误展 | 单次课显示为整学期 | 本轮**不改**解析行为，仅记录为已知风险（`PROJECT_HANDOFF §10.4`） |

### 6.3 交付纪律

- 每阶段独立可验收（`DESIGN_V3 §7`），先后端不阻塞：P0 → P1 → P2 → P3 → P4 → P5。
- 每个需求完成须有：相关单测 + `lintDebug` + `assembleDebug` + 敏感信息扫描。
- **诚实标注**：任何未经真机验证的结论不得表述为「已验收」。

---

## 7. 约束（不可违反，写死为红线）

以下约束来自 `DESIGN_V3_INTERACTION §8.3` 与 `PROJECT_HANDOFF §6`，任何阶段不得违反：

| # | 约束 |
|---|---|
| C1 | **教务导入是特色功能，任何阶段不得使其不可用**（链路：`JwImportActivity` → `SchoolSelectScreen` → `JwWebViewLoginScreen` → `JwImportViewModel.parseHtml` → `JwParserRegistry.FACTORIES` → `JwNewZfParser` → `importAsNewTable` 单事务） |
| C2 | WebView 域名白名单**严禁**扩为通配（不得 `*.jlju.edu.cn`，不得对所有学校通配放行）；`onReceivedSslError` 必须 `handler.cancel()` |
| C3 | 解析失败**不得回显响应内容**，只回显错误 code（如 `E_PARSE_FORMAT`）与诊断特征（`matchedFeatures`，不含学号/姓名/Cookie/token/HTML） |
| C4 | Room 数据库**严禁** `fallbackToDestructiveMigration`；沿用明确迁移 |
| C5 | 许可证：GPL-3.0；第三方来源如实标注（保留 `LICENSE`/`NOTICE`/上游归属与第三方许可） |
| C6 | 无真机/无模拟器环境，所有验证靠**单元测试 + 源码契约测试** |
| C7 | JS Bridge 只在可信教务顶层页面短暂安装，离开即移除；禁用混合内容/文件访问/内容访问 |
| C8 | `allowBackup=false`、`usesCleartextTraffic=false`；备份/迁移排除课表与设置；不放宽 TLS |

---

## 8. 待确认问题

> 仅列**真正需要用户决策**的项；用户已拍板项（移除 Dock、Tab 语义、「管理」并入「设置」、小组件复活 2 种）不再列入。

| # | 问题 | 背景 | 默认处理（若用户不回复） |
|---|---|---|---|
| Q1 | **「冲突一键处理」是否纳入本轮？** | `DESIGN_V3_INTERACTION §4.3` 列为新增项，但不在 §7 落地路线的 P0–P5 内 | 默认**不纳入**本轮（见 OOS-5），记为后续迭代 |
| Q2 | **小组件 2 种是否都保留大小两种尺寸？** | 现 5 种各有大小两个 receiver，共 10 配置；裁到 2 种后是否保留"小尺寸"变体未明确 | 默认**每种保留大 + 小两种尺寸**（信息密度差异有价值），即 4 个 receiver 存活 |
| Q3 | **「今天」页是否设为首屏默认 Tab？** | `PROJECT_HANDOFF §1` 曾述"首页打开即见课表"；本次重设计将「今天」列为首 Tab | 默认**首屏默认「今天」**（对齐最高频需求），原「课表即首页」描述作废 |
| Q4 | **考试周 / 教学周区分是否要在学期视图体现？** | 学期视图价值之一是"看考试周"，但当前数据模型无考试周字段 | 默认**不做**（无数据支撑），学期视图仅展示周次与课程分布 |

---

## 附录 A · 阶段交付对照（P0–P5 ↔ 需求编号）

| 阶段 | 需求编号 | 交付标志 |
|---|---|---|
| P0 | REQ-P0-01 ~ 05 | 底部无 ＋；tab bar 常驻；返回键可预期 |
| P1 | REQ-P1-01 ~ 05 | 打开即见下一节课 |
| P2 | REQ-P2-01 ~ 08 | 失败时能一键转文件导入；白名单下沉 + 契约测试 |
| P3 | REQ-P3-01 ~ 07 | 能搜到课、能看学期分布、全屏选周 |
| P4 | REQ-P4-01 ~ 05 | 设置四组齐全；死代码/死资源清零 |
| P5 | REQ-P5-01 ~ 04 | 小组件 receiver 启用；上课前提醒可开启 |

## 附录 B · 关键代码位置索引

| 主题 | 文件 |
|---|---|
| 根 Tab / Dock / 覆盖页栈 / 返回键 | `app/src/main/java/com/wedo/schedule/MainActivity.kt` |
| 今天页（死代码，待复活） | `app/src/main/java/com/wedo/schedule/ui/screen/today/TodayScreen.kt` |
| 导入面板 | `app/src/main/java/com/wedo/schedule/ui/screen/imports/ImportSheet.kt` |
| 教务导入流程 | `.../ui/screen/imports/JwImportActivity.kt` |
| 教务 WebView + 白名单硬编码 | `.../ui/screen/imports/JwWebViewLoginScreen.kt` |
| 学校模型 | `.../data/jw/JwSchoolInfo.kt` |
| 诊断分类（7 类） | `.../data/jw/JwParseDiagnostics.kt` |
| 解析注册表（扩展点） | `.../data/jw/JwParserRegistry.kt`（`FACTORIES`） |
| 新版正方解析器 | `.../data/jw/JwNewZfParser.kt` |
| 拼音匹配器 | `.../util/PinyinMatcher.kt` |
| 设置页（待合并） | `.../ui/screen/mine/MineScreen.kt`、`WedoSettingsScreen.kt` |
| 管理页（待并入设置） | `.../ui/screen/manage/ManagementPage.kt` |
| 小组件 receiver 声明 | `app/src/main/AndroidManifest.xml`（92–250） |
| 死资源 | `app/src/main/res/values*/strings.xml`（`import_qr` / `schedule_share_table`；`tab_today` 已复活为「今天」页标签，不删） |
