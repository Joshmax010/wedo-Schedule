# wedo 课程表 UI MVP 实施计划

更新日期：2026-09-12

## 1. 实施目标

在不改写 JLJU 登录、课程解析、本地数据库和现有导入器的前提下，将现有 Debug 界面改造成已经确认的 wedo 首页：完整周视图、蓝色液态玻璃主题、稳定彩色课程块、三段悬浮 Dock、通用添加课表入口、深浅主题和可降级的弹簧动效。

这次是 UI MVP，不包含照片背景、扫码导入、新学校适配、桌面组件和提醒功能。

## 2. 代码改造原则

- 保留 `ScheduleViewModel`、`ScheduleRepository`、数据库实体和现有 Parser；UI 只消费现有状态。
- 保留 `CourseTableView` 的节次定位、非常规时间课程和 `ConflictLayoutEngine`，只抽取样式与交互参数。
- 复用 `ImportSheet`、`ManagementPage`、`JwImportActivity` 和已有文件/文本解析器，新增统一入口编排，不复制导入逻辑。
- 复用现有课程详情、编辑、课表管理、导出和设置能力，通过新导航壳重新组织。
- 每一步保持可编译；不在同一提交中同时重写主题、导航、网格算法和数据层。
- 工作区当前已有未提交的 JLJU 与基线改动，实施时按文件核对差异，禁止覆盖或回滚这些改动。

## 3. 阶段与任务

### UI-0：冻结基线与建立回归保护（0.5 天）

任务：

- 记录当前 `git status`、Debug APK 构建结果和 JLJU 真机导入成功结论。
- 运行 UI 改造前的相关单元测试、Lint 和 Debug 构建，保存失败基线。
- 为主题模式、课程稳定配色、可见星期和课程显示模板补纯逻辑测试入口。
- 将浅色/深色概念图的颜色、层级和组件结构固化到 `docs/UI_DESIGN.md`。

验收：数据导入与现有课表能正常打开；基线失败项与本次改造新增失败可区分。

### UI-1：设计令牌与玻璃基础组件（1 天）

主要文件：

- `ui/theme/Theme.kt`
- `ui/theme/ThemePresets.kt`
- 新建 `ui/theme/WedoTokens.kt`
- 新建 `ui/component/WedoGlass.kt`
- `util/AppPrefs.kt`

任务：

- 定义浅色/深色背景渐变、Wedo Blue 色阶、文字色、玻璃边框、高光、阴影和冲突警示色。
- 建立 `GlassSurface`、`GlassIconButton`、`GlassPill` 和 `WedoBackground`，避免页面各自拼装不同玻璃效果。
- 添加主题模式、玻璃质量、动效开关和震动开关偏好；默认跟随系统、平衡质量、开启动效与震动。
- 先实现所有 API 26 可用的轻量材质；增强渲染通过独立能力判断启用，不能散落版本判断。

验收：浅色和深色预览中玻璃层级一致；API 26 不调用高版本 API；关闭增强效果后布局不变化。

### UI-2：主导航壳与三段 Dock（1 天）

主要文件：

- `MainActivity.kt`
- `ui/component/PillNavigationBar.kt`（重构或替换为 `WedoDock`）
- `ui/component/SleepyMotion.kt`

任务：

- 删除主导航中的独立 Today Tab；保留 Today 相关内部计算与非首版组件代码，不做破坏性删除。
- 主壳只保留课表和设置两个目的地，中间加号作为操作而不是 Tab。
- 中间加号打开 `AddScheduleSheet`；Android 返回手势先关闭 Sheet/设置，再回课表。
- 用嵌套滚动信号驱动 Dock：向下隐藏、向上立即出现、顶部固定出现、停止滚动不自动出现。
- Dock 动画使用可中断弹簧；为减少动态效果提供短淡入降级。
- 正确处理状态栏、手势导航和三键导航 Insets，保证课表最后一行可滚到 Dock 上方。

验收：三个区域职责明确；快速反复滚动和切页无动画排队；返回逻辑无回归。

### UI-3：首页顶部与周切换（1 天）

主要文件：

- `ui/screen/schedule/ScheduleScreen.kt`
- 新建 `ui/screen/schedule/WedoWeekHeader.kt`
- `ui/screen/schedule/ScheduleViewModel.kt`

任务：

- 顶部只保留第几周、今日日期、左右切周按钮；分享、撤销、课表切换等次要动作移动到详情菜单或设置。
- 保留现有 `HorizontalPager` 周分页和 ViewModel 周次状态，加入更明确的跟手位移与弹簧吸附。
- 点击周次打开周选择器；浏览其他周时不额外显示“回到本周”按钮。
- 建立可折叠头部状态：纵向下滑收起，星期日期条吸顶，向上恢复。
- 周次越界时不翻页；学期外状态沿用既有日期逻辑，不伪造课程。

验收：箭头、横滑和周选择器三条路径状态一致；旋转或重组后周次不跳变。

### UI-4：周网格与课程卡视觉（1–1.5 天）

主要文件：

- `ui/component/CourseTableView.kt`
- `ui/component/ConflictCard.kt`
- `util/CourseColorUtil.kt`
- `util/ConflictLayoutEngine.kt`（原则上只读，必要时仅补展示元数据）

任务：

- 继续使用现有时间栏、日期列宽、课程绝对定位和冲突聚簇算法。
- 将网格改为透明内容层：弱化容器大底色，使用低对比蓝色网格线和雾蓝背景。
- 课程卡改为稳定彩色轻玻璃；统一高光描边、圆角、按压反馈和自动前景色。
- 将课程副信息从单选扩展为全局字段集合，MVP 默认课程名、教室、教师；根据卡片高度和字体缩放安全裁剪。
- 可见星期提供 5/6/7 日三个快捷方案，并兼容原有自定义集合。
- 新增紧凑、平衡、宽松三档，映射到现有网格缩放能力；默认平衡。
- 冲突卡保留原样式选择，默认错位堆叠，并增加红色轮廓、徽标和无障碍说明。
- 单击沿用课程详情；长按打开操作 Sheet，不启用网格内拖拽。

验收：同一课程重启后颜色不变；浅深主题均可读；多教师、多教室、长课程名、12 节、冲突课程不越界崩溃。

### UI-5：通用添加课表 Sheet（0.5–1 天）

主要文件：

- 新建 `ui/screen/imports/AddScheduleSheet.kt`
- `ui/screen/imports/ImportSheet.kt`
- `ui/screen/manage/ManagementPage.kt`
- `MainActivity.kt`

任务：

- 建立五类入口：教务系统、WakeUp 导出的 ICS 日历文件、其他文件、粘贴文本、手动创建。
- 教务系统入口先进入学校选择器，JLJU 只是当前可用学校之一。
- WakeUp 导入排在第二位，并复用现有 JSON/分享文本识别与预览。
- 当前课表存在学校来源时显示通用“同步当前课表”。
- 导入完成回到周视图；失败保留旧数据并显示匿名错误分类。

验收：所有已有导入格式仍能到达原业务路径；中间加号不包含 JLJU 特有文案。

### UI-6：设置重组与自定义（1 天）

主要文件：

- `ui/screen/mine/MineScreen.kt`
- `ui/screen/mine/AppearanceScreen.kt`
- `ui/screen/mine/GeneralSettingsScreen.kt`
- `ui/component/SettingsCards.kt`
- `util/AppPrefs.kt`

任务：

- 按外观、课表显示、课表管理、隐私与关于分块呈现 Debug 版功能。
- 增加主题三选、玻璃质量三档、密度三档、字段复选框、显示星期快捷项、动效和震动开关。
- 保留冲突样式、课程配色、全部课表、编辑、导出、清除数据、退出登录和许可证入口。
- 不在 MVP 显示未实现的照片背景和扫码导入开关。

验收：设置修改能即时反映到首页；重启后保持；清除课程仍需二次确认且不与退出登录混淆。

### UI-7：状态、动效与无障碍收尾（0.5–1 天）

任务：

- 统一未登录、未导入、本学期无课、离线缓存、刷新失败和解析失败状态。
- 为加载、空状态和错误提示使用低干扰玻璃卡，不遮挡缓存课表。
- 加入切周吸附、长按和导入成功的轻触震动；遵守用户关闭设置。
- 检查 TalkBack 描述、48dp 触控目标、字体缩放、屏幕宽度和颜色对比度。
- 若实现 AGSL 增强，只允许作用于小范围顶栏/Dock，不对整张课表持续重绘。

验收：关闭动效/震动后功能完整；字体放大时核心操作可用；无网络仍能查看缓存。

### UI-8：测试、真机验收与交付（1 天）

自动验证：

- 主题与偏好纯逻辑单元测试。
- 稳定课程色、前景对比度和冲突警示测试。
- `testDebugUnitTest`、`lintDebug`、`assembleDebug`。
- 敏感信息扫描，确认 UI 诊断和预览数据不包含真实课表或认证信息。

真机验收矩阵：

- 浅色 / 深色 / 跟随系统。
- 5、6、7 日列；紧凑、平衡、宽松密度。
- 普通课程、长文本、单双周、课程冲突、空课表和 9–12 节晚课。
- 箭头切周、左右跟手切周、周选择器。
- Dock 向下隐藏、向上出现、顶部常驻、加号打开导入。
- JLJU 重新导入、WakeUp ICS 日历文件、手动课程和离线重启。
- Android 8 轻量玻璃、Android 12 分层玻璃、Android 13+ 增强效果。

交付：新的 arm64 Debug APK、SHA-256、构建结果、已知视觉差异和需要产品方确认的截图。

## 4. 实施顺序与提交边界

建议按以下边界提交，便于回归和撤销单个视觉阶段：

1. `ui: add wedo glass tokens and primitives`
2. `ui: replace main navigation with schedule dock shell`
3. `ui: redesign weekly schedule header and navigation`
4. `ui: restyle timetable cards and conflicts`
5. `ui: add unified schedule import sheet`
6. `ui: reorganize appearance and schedule settings`
7. `test: cover wedo theme preferences and ui contracts`

在当前工作区尚未整理提交前，不直接执行这些提交；先保证已有 JLJU 改动归属清晰。

## 5. 预计工期

单人集中开发预计 6–8 个工作日：

- 可安装的第一版首页与 Dock：约 2–3 天。
- 完成导入入口、设置重组和深色模式：再 2–3 天。
- 动效、兼容性、测试和真机收尾：再 2 天。

实际时间主要受现有大体量 `CourseTableView` 的拆分风险、低版本设备表现和真机动画调优影响。第一版应先实现可靠的分层玻璃，不以高成本实时折射阻塞可用 APK。

## 6. 产品方需要参与的节点

开发阶段不需要再次提供账号、密码、Cookie 或 HAR。仅在以下节点需要产品方操作：

1. 第一版 APK：安装后确认首页信息密度、颜色和 Dock 手感。
2. 第二版 APK：分别切换浅色和深色，验证 5/6/7 日显示和课程字段模板。
3. 候选版 APK：使用本人课表完成一次教务同步，并用 WakeUp 导出的 ICS 日历文件验证第二导入路径。
4. 发布前：在至少两种 Android 设备或系统版本上完成匿名验收记录。

反馈只需截图或录屏并说明“设备/系统版本、所在页面、预期和实际”；不得发送登录凭据或未脱敏网络数据。

## 7. UI MVP 发布门

以下条件全部满足才视为完成：

- 启动后直接进入周视图，不再存在独立 Today 主入口。
- 顶栏、星期栏、课程网格和 Dock 的层级符合设计规范。
- 中间加号进入通用添加课表，教务导入不写死 JLJU，WakeUp 导入排第二。
- 深浅模式、密度、星期范围和课程字段模板可配置并持久化。
- 课程颜色稳定、文字可读，冲突课程有明确红色警示。
- Dock 与切周动画可中断、可关闭，并在 API 26 降级可用。
- JLJU 登录导入、缓存、课程详情、编辑、管理和导出没有回归。
- 单元测试、Lint、Debug 构建与敏感信息扫描通过。
