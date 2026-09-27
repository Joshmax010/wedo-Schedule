# wedo课程表项目交接手册

> 核对基准：2026-09-25，本地 `main` 的 `4334826c3b4c06559da82a8a76eccc5759af475a`。本文依据该提交的源码、构建配置、工作流和公开文档编写；后续代码变更应同步更新本文。项目根目录为 `wedo/`。本文不是上线验收报告，**“代码已有入口”不等于“真实场景已验收”**。

> 目录维护：2026-09-27 已执行精简；保留核心源码、全部现有应用测试、许可证、Android CI、两项安全脚本、JLJU 公开夹具和五份核心文档。旧规划、上游发布记录、调研截图、采集器、重复 Wrapper、原始私有取证和 Gitee 工作流已从工作目录移除。源码业务行为未因此改写；被 Git 跟踪的旧文件可查历史，本地删除项可从回收站恢复。

> 精简验证：本机 `lintDebug`、`assembleDebug` 成功；强制重新执行 `testDebugUnitTest`，1304 项测试、0 失败、0 错误；敏感信息扫描与核心文档本地链接检查通过。验证后生成的 APK、报告和项目缓存已清理，接手者可按本文构建命令重新生成。

## 0. 接手时先读的结论

wedo 是从 [Sleepy · 轻课表](https://github.com/lingion/sleepy) 派生的 Android 原生、本地优先课程表。当前默认使用蓝色、明亮通透的周视图和 Android 原生 Compose 交互；已开放吉林建筑大学（JLJU）官方网页登录导入，同时提供日历文件、通用文件、文本和手动添加入口。无自建账号和后端，不依赖 Google Play Services。代码仍保留大量 Sleepy 的类名、包名、页面和解析器，不能因为名称带 `Sleepy`/`WakeUp` 就视为无用代码。

接手者最需要记住的五件事：

1. **应用 ID 与源码包名不同**：安装包 `com.wedo.schedule`，Debug 为 `com.wedo.schedule.debug`；Kotlin `namespace`/主要源码包仍为 `com.lingion.sleepy`。不要为“统一命名”贸然全仓换包。
2. **首页是已实现的 MVP，不是设计稿**：七日周网格、紧凑周切换、彩色课程卡和悬浮 Dock 在代码中；已移除的历史 `UI_DESIGN.md` 曾有过时措辞，以本手册和当前源码为准。
3. **WakeUp 迁移的产品入口是其“导出为日历文件”产生的 ICS**；并不存在经用户证实的“WakeUp JSON 导出文件”入口。通用 JSON 解析仍保留，但不能营销为 WakeUp JSON 兼容。
4. **教务直连目前只公开 JLJU**。`assets/schools.json` 和多所学校解析器是继承的能力/研究材料，不代表它们在 wedo 已验证或已对用户开放。
5. **尚未达到正式发布门**：JLJU 会话过期、异常响应、完整交互式 SSO 链、更多设备/三人实测及签名 Release 工作流等仍需核验。不能把当前 `0.1.0` Debug 可构建等同于 `1.0.0` 可发布。

### 信息源优先级

当文档与行为冲突，按“当前源码与测试 → 当前构建/CI 配置 → 最近的实证记录 → 旧计划/状态文档”的顺序判断。尤其注意：

- 历史 `IMPLEMENTATION_STATUS.md`、`UI_DESIGN.md`、`UI_MVP_IMPLEMENTATION_PLAN.md` 等已移出工作目录，不再作为接手入口；追溯时查 Git 历史。现在 `origin` 是 `https://github.com/Joshmax010/wedo-Schedule.git`。
- `docs/RELEASING.md` 描述的是目标发布流程；当前 `.github/workflows/` 只保留 `android.yml`，**没有** `v*` 标签自动签名并发布 APK 的工作流。移除的 Gitee 脚本使用上游作者的目标仓库，不是 wedo 必需流程。
- `docs/JLJU_EDUCATION_SYSTEM.md` 是已脱敏的取证记录，也明确列出尚未确认的认证及异常场景。

## 1. 产品定位、用户承诺与范围

目标用户是希望快速看清每周课程、拥有可离线课表、愿意通过学校官方页面自行登录的大学生。首页打开即见课表，不先进入信息流或“今日课程”页。产品追求清楚、轻快、年轻，但视觉装饰不能盖住课程名称、教室和节次。

设计和业务约束如下：

- Android 8.0/API 26 起；学校账号仅由官方网页承接，应用不创建密码表单、不采集明文密码，不绕验证码和 TLS。
- 教务数据默认在本机处理和保存；无云同步、用户中心、广告、埋点服务或自建课表后端。
- 先使 JLJU 适配可靠，再按真实证据逐校扩展。UI 用“教务直连/选择学校”，不要在通用入口写死 JLJU。
- 课程显示字段是一套**所有课程共用**的模板，不做每门课独立字段勾选；但允许对课程组手动换色。
- 课程卡采用稳定而非每次随机的底色，以提高辨识度；冲突使用红色提醒且不能只靠颜色传达。
- 用户照片作背景、提醒/组件/云同步等不属于当前 MVP；源码里有继承的相关类，并不意味着当前产品启用。
- 非官方应用，维护团队不收费、不投广告、不经营用户数据；许可仍是标准 GPL-3.0，不附加“禁止商业使用”。

## 2. 仓库、技术栈与构建身份

### 2.1 仓库及版本

- 上游仓库：`https://github.com/lingion/sleepy.git`；基准提交 `08a1f26a5d7b1a117e2216d92804b202ddde228d`；本仓仍保留上游历史，远端名 `upstream`。
- 项目远端：`https://github.com/Joshmax010/wedo-Schedule.git`，远端名 `origin`；默认分支 `main`。编辑代码时先核对 `git status` 和本地/远端差异，不覆盖他人未提交改动。
- `app/build.gradle.kts`：`applicationId = com.wedo.schedule`、`minSdk = 26`、`compileSdk = targetSdk = 37`、`versionName = 0.1.0`、`versionCode = 1`。Debug 加 `.debug` 后缀；Release 当前配置为**未签名**。
- 打包只生成 `arm64-v8a`、`armeabi-v7a`、`x86_64` ABI 包，无 universal APK；只打包简体中文资源，上游其他语言资源仍在源码中。

### 2.2 主要技术选择

| 层 | 当前实现 | 修改入口/注意点 |
| --- | --- | --- |
| 语言/构建 | Kotlin，Java/Kotlin JVM 17，Gradle 9.3.1，AGP 9.1.1；CI 使用 Temurin 21 | `build.gradle.kts`、`app/build.gradle.kts`、`gradle/wrapper/gradle-wrapper.properties` |
| UI | Jetpack Compose + Material 3，部分继承的旧页面/组件并存；Compose BOM 2024.10.00 | `MainActivity.kt`、`ui/screen/`、`ui/component/`、`ui/theme/` |
| 数据与状态 | Room 2.7.0 + KSP、Flow/Coroutines 1.8.1、ViewModel；DataStore 依赖与 SharedPreferences 并存 | `data/AppDatabase.kt`、`data/repository/ScheduleRepository.kt`、`util/AppPrefs.kt` |
| 解析 | JLJU/教务协议解析器、通用 `ScheduleParser`、Jsoup 1.18.1、Kotlinx Serialization 1.6.3 | `data/jw/`、`data/parser/`；新增格式先写纯离线测试 |
| 登录/网络 | 官方页 WebView、按学校主机白名单导航、限定 JS Bridge | `ui/screen/imports/JwWebViewLoginScreen.kt`；不能弱化证书或域名校验 |
| 图片及旧功能依赖 | Coil Compose、WorkManager 等仍在构建中 | “有依赖/代码”不表示 MVP 已在运行图片背景或通知；先查调用链 |
| 测试/CI | JUnit 本地测试、Android/Compose 测试依赖、Lint、PowerShell 敏感信息扫描 | `app/src/test/`、`app/src/androidTest/`、`.github/workflows/android.yml` |

`settings.gradle.kts` 在 GitHub Actions 优先官方 Gradle/Maven 仓库，在本地环境优先国内镜像。更新构建版本时既要检查 CI，也要检查国内网络下的可复现性。不要把 JDK 17 的字节码目标误认为 CI 只能使用 JDK 17。

### 2.3 推荐首次接手构建

在仓库根目录准备 Android SDK Platform 37 和 Build Tools 37（用户已同意其许可证和安装），配置有效的 Android SDK 路径及 JDK。PowerShell 下运行：

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
./scripts/scan-secrets.ps1
```

Debug APK 位于 `app/build/outputs/apk/debug/` 下的 ABI 分包；安装前按模拟器/手机 ABI 选包。若用 MuMu，先确认模拟器 API 与 ABI，不要假定它代表最低 API 26。调试安装包与正式包 ID 不同，二者数据不会自动互通。涉及 UI、安全或数据库的变更，单元测试之外还需在设备/模拟器上做流程验证。不要把真实账号、HAR、Cookie、课表或签名材料放进源码目录。

## 3. 代码地图与架构数据流

以下路径均相对于仓库根目录：

```text
app/src/main/java/com/lingion/sleepy/
├─ SleepyApp.kt                 应用级数据库/仓库初始化
├─ MainActivity.kt              首页路由、导入面板、Dock、返回栈与外部 Intent
├─ WedoPrivacyConsent.kt       首次启动隐私确认
├─ data/
│  ├─ AppDatabase.kt            Room 数据库版本 5、DAO、迁移
│  ├─ entity/                   CourseEntity、TimeTableEntity
│  ├─ repository/               ScheduleRepository 数据写入/观察边界
│  ├─ jw/                       教务学校模型、协议注册表、JLJU/新版正方解析
│  └─ parser/ScheduleParser.kt  ICS、JSON、CSV、HTML、纯文本通用导入
├─ ui/
│  ├─ screen/schedule/          周视图、ViewModel、周顶栏、课程长按动作
│  ├─ screen/imports/           添加面板、教务导入 Activity、WebView
│  ├─ screen/mine/              wedo 设置及继承的管理/外观页面
│  ├─ component/                七日网格、课程卡、详情弹层、玻璃 Dock
│  └─ theme/                    主题预设、wedo 色彩与显示偏好
└─ util/                        AppPrefs、日期/作息、冲突布局、课程配色
app/src/main/res/xml/             网络安全、备份/迁移排除规则
app/src/test/                    本地单元测试
test/fixtures/jlju/             只含脱敏结构证据
.github/workflows/               唯一 Android Debug CI 工作流
```

正常数据流：

```text
学校官方 WebView 页面
  → 只在可信教务域读取授权后的页面/课表响应
  → JwParserRegistry / JwNewZfParser（JLJU 专项有 JlJuParser 包装与测试）
  → JwCourse 临时中间模型
  → 用户确认学期起始日、课表名、节次时间
  → JwImportViewModel 导入事务
  → TimeTableEntity + CourseEntity / Room
  → ScheduleRepository 的 Flow
  → ScheduleViewModel
  → ScheduleScreen / CardsGridView / WedoCourseCard
```

本地文件/粘贴走另一入口：`ImportSheet → ScheduleParser.parse → 导入预览 → 用户选应用方式 → Repository/Room → 同一个 ScheduleViewModel/周视图`。不要另建长期存在的第二套课程表实体，也不要让 UI 直接依赖教务原始响应。

### 3.1 启动与导航

`MainActivity` 是启动页。首次启动由 `WedoPrivacyConsent` 要求明确接受；拒绝则退出。接受状态在 `SharedPreferences("wedo_privacy")` 中。主界面由 `SleepyThemeProvider`、`WedoDisplayProvider` 和 `WedoBackground` 包裹，`MainActivity.AppRoot` 自己管理根 Tab、覆盖页面栈和导入弹层：

- 可见 Dock 三个入口：左“课表”、中“＋”、右“设置”。“创建、导入与管理”是从设置继续进入的内部页面，并非第三个常驻 Dock 项。
- 设置页/管理页返回课表；覆盖页按栈逐层返回；课表根页面双击返回键退出。
- 向下纵向滚动隐藏 Dock，向上或回到顶部时恢复；同一 `collapsed` 状态也把周顶栏高度从 64dp 缩到 48dp。
- `ImportReceiverActivity` 接外部 `ACTION_VIEW` 文本类文件，向 `MainActivity` 传导入内容，自动打开导入预览。`OpenJsonAlias` 是历史命名，实际覆盖 JSON/ICS/CSV/HTML/纯文本等文本 MIME。不要无界接受二进制文件或在 Intent 中长期保存原始敏感内容。

### 3.2 本地实体和日期契约

- `TimeTableEntity`：一张课表/学期，含 `startDate`（`yyyy-MM-dd`，保存时按周一规范化）、`maxWeek`、`nodesPerDay`、`timeJson`、默认标记、智慧节次配置等。
- `CourseEntity`：一条上课记录，含 `tableId`、课程组 `groupId`、名称/教师/教室/备注、星期 `day=1..7`、起始节 `startNode`（1-based）、连续节数 `step`、起止周和单双周 `type`，以及颜色、非常规节次/时间等。**同一门课可以有多条记录**（不同周、地点、星期、时间）。删除“这次课程”只删除该记录；课程组操作依 `groupId`。
- Room 数据库名仍为 `sleepy.db`，版本 5；使用明确迁移，不使用破坏性回退。增字段/改表时必须补 Migration 和旧库升级测试，不能通过卸载 App 掩盖迁移问题。
- 当前周由 `ScheduleViewModel` 调用 `DateUtils.currentWeek(startDate)` 计算，周标题今日日期显式使用 `Asia/Shanghai`。`DateUtils` 默认日期参数则使用设备本地日期；若要全链路保证中国时区或学期外“假期/非教学周”，需单独核对并补测试，**不要把目标文档的描述当作现有完全实现**。
- `CourseEntity.inWeek` 依据起止周及单双周决定是否显示。`type=3` 代码层仍按记录范围显示，离散周依解析阶段拆分为多条记录；后续若改周次模型，须保留此兼容语义并测试混合周、单双周和重复导入。
- 设置持久化并非全在 DataStore：wedo 显示项放在 `SharedPreferences("wedo_display")`，主题、显示星期、冲突样式等放在继承的 `AppPrefs`，首次隐私同意又是单独的 `wedo_privacy`。修改设置前先查真理源，避免同名状态写两处。

## 4. 视觉美学与 UI 规格（当前代码）

### 4.1 设计原则

用户明确选择“苹果式明亮通透 + Android 原生可行性”的方向：优先清晰和响应，再用漂浮、透光、边缘高光、弹簧反馈营造灵动感。不是照搬 iOS 控件，也不是纯白毛玻璃卡片墙。默认基调蓝白，深色为深海军蓝；颜色层次服务于识别课程、星期和操作。WakeUp 截图仅作为周网格信息密度和课程色块的参考，界面实现为 wedo 自身的 Compose 组件。

`WedoDesign.kt` 的 `wedoColors` 对五套静态预设统一覆盖浅色/深色背景及 surface 层级，默认的**海蓝**强调色约为浅色 `#1764D9`、深色 `#80B5FF`（默认键由 `AppPrefs.getThemeKey` 指向 `ocean`）。`ThemePresets` 仍保留预设色和系统取色：`default`、`spring`、`ocean`、`peach`、`slate`、`system`；`AppearanceScreen` 可选主题色与浅/深/跟随系统。静态预设换强调色后仍以冷调蓝白/藏蓝为表面基底；Android 12/API 31+ 选择 `system` 时 `SleepyThemeProvider` 走 Material You 动态取色，不能把它当作完全相同的调色板。`WedoBackground` 的背景渐变另由组件固定绘制。别把 `Theme.kt` 中继承的紫色 `LightScheme` 常量误判为用户实际看到的 wedo 首页默认视觉；以 `SleepyThemeProvider` 的选择分支和 `WedoBackground` 的最终结果为准。

`WedoBackground` 负责纵向冷色渐变和两处淡蓝径向环境光。`Modifier.wedoGlass` 用低透明渐变、细边缘亮线和柔和阴影叠加模拟玻璃。它可在 API 26 工作，但**不是实时背景模糊、折射或 AGSL Shader**。`quality` 为流畅/平衡/精致三档，默认平衡；低内存设备自动减轻装饰。精致档刻意降低白色不透明度，避免“透明拉满后露出一张白卡”的旧问题。评审视觉时需要同时看浅/深色、低内存、不同壁纸/背景及弱性能设备，不能只看静态生图。

### 4.2 首页空间分配

- 顶部只有紧凑玻璃周导航：左上一周、中间“第 N 周”与**今日日期**、右下一周。点击中间可打开周次网格选择；没有常驻“回到当前周”大按钮，也没有独立大块今日课程卡。`WedoWeekHeader.kt` 控制高度和箭头可用性。
- 主体为可纵向查看节次的周网格；水平滑动整页换周，当前页范围 `1..maxWeek`。星期列、日期、左侧节次/作息刻度由 `CardsGridView` 提供。默认全周七列；设置可切换工作日五列或六列。纵向密度默认为平衡，另有紧凑/宽松。
- 课程卡按时间跨行、按星期定位；名字、教室、教师等在卡内按全局勾选模板显示，并按卡片可用高度/字体缩放限制行数与省略。浅色课程卡采用较明亮的底色，深色会重新计算明度；文字根据实际底色亮度在深蓝/白色之间取对比更高者。
- 底部 Dock 是居中的窄胶囊（最大 300dp，屏幕宽度约 74%，高 56dp），两侧入口靠中间的凸起蓝色圆形“＋”聚拢。它不应盖住课表最后几节；`navigationBarsPadding` 与页面底部留白须一起检查。下滑收起以释放空间，向上滚动重新出现。
- 空状态要区分“没有任何课表”和“这张课表/本周没有课程”。当前 `ScheduleScreen` 具备无课表和本周无课提示；更细的未登录/导入失败/会话过期状态仍需按真实行为核对。

### 4.3 彩色、冲突与自定义

`WedoCourseCard.kt` 对课程名称计算稳定色相，同名课程跨重启保持相近底色，不是每次渲染随机分配。已有自定义色优先；长按课程可为同 `groupId` 的课选预设颜色。若用户启用继承的无色课表设置，自动色会被抑制。自动底色算法和手动颜色都必须做明暗两套对比测试；“能看清”优先于花哨。

时间冲突以红色描边加 `!` 标识（并提供无障碍语义），布局可选错位堆叠、折角、侧边轨道。`ConflictLayoutEngine` 是冲突分簇/布局核心；课程详情里的“默认置顶课程”与它共享簇键。新增冲突布局样式时要同时检查网格、详情、设置持久化和屏幕阅读器语义。

`WedoDisplay` 默认 `density=balanced`、`quality=balanced`、字段 `{name, room, teacher}`、动效/轻触震动开启、淡化显示非本周课程关闭。设置中可勾选课程名、教室、教师、上课周次、节次、备注，但不能全部取消。字段的顺序目前由 `WedoCourseCard` 的固定顺序控制，**不是**用户可拖拽排版。个人照片背景是未来需求，目前没有该设置或替换流程。

### 4.4 动效与 Android 体验

点击反馈使用 Compose spring 将控件压到约 0.96 后回弹；切周使用带阻尼的弹簧位移；周顶栏折叠采用高度动画；可在设置中关动效和触觉反馈。保留系统状态栏/手势导航边距、原生返回手势/返回键、Bottom Sheet、文件选择器、系统浏览器外链等 Android 惯例。后续可增强“液态”连贯感，但需保证 API 26、低内存机、60Hz 设备、字体放大、TalkBack、手势导航下仍清楚可用；不要把高帧率 GPU 特效变成核心功能前提。

## 5. 功能与实际用户交互

### 5.1 用户主路径

1. 首次启动阅读隐私/非官方提示，接受后进入首页；拒绝则退出。
2. 无课表时点“添加课表”或 Dock 中间“＋”，打开 `ImportSheet`。入口次序：**教务直连 → 从 WakeUp 迁移（日历文件）→ 粘贴课表文本 → 从文件导入 → 手动添加课程**。
3. 教务直连进入选择学校；当前公开目录只有 JLJU。用户在官方网页自行完成登录/验证码。抓取或解析到课表后，先确认开学日期、课表名和节次时间，再创建本地课表。
4. 文件/文本导入先生成预览，再由用户决定新建、追加、覆盖等应用方式；不能选完文件就静默覆盖当前课表。预览、确认和对话框状态位于 `ImportSheet.kt`。
5. 导入后首页直接展示周视图。左右箭头/水平滑动切周；点周标题快速选周；点课程看详情，长按课程选颜色/编辑/复制/删除这次课程；设置里可进入所有课表、导出、管理和显示偏好。

### 5.2 教务直连与 JLJU 证据边界

`JwImportViewModel.defaultWedoSchools()` 暴露的学校是 JLJU，入口 `https://jwxt.jlju.edu.cn/sso/hnyyxyiotlogin`，协议 `zf_new`。真实取证确认新版正方 `jwglxt`，学期选择项在个人课表 HTML 中；个人课表为 `POST /jwglxt/kbcx/xskbcx_cxXsgrkb.html`，成功 JSON 顶层含 `kbList`。`kbList: []` 才是有证据支持的“本学期无课”；空正文、缺字段、非 JSON、失败状态都不能当无课。日期和节次也有独立接口证据；详见 `docs/JLJU_EDUCATION_SYSTEM.md` 与 `test/fixtures/jlju/README.md`。

`JwImportActivity` 的三阶段是学校选择、WebView 登录/采集、配置确认；`JwImportViewModel` 经 `JwParserRegistry` 选择解析器、暂存 `JwCourse`，确认后以数据库事务写新课表并设为默认。`JlJuParser` 是 JLJU 的纯离线包装/测试入口，内部复用 `JwNewZfParser`；当前运行时通用 `zf_new` 注册表路径也会直接选 `JwNewZfParser`。不应因为看见独立 `JlJuParser` 就再造平行认证/课程模型。

目前不应声称“自动处理所有会话过期/维护响应”或“重复教务导入会幂等更新原表”。当前主路径创建**新表**，会话及异常形态还有实证空缺；若新增刷新功能，必须定义覆盖范围、保留上次成功数据和重复记录策略，并加端到端测试。

### 5.3 WakeUp 及其他文件路径

WakeUp 目前用户可见的“导出为日历文件”是本项目迁移入口，选择 ICS 后由 `ScheduleParser.parseIcs` 解释。通用文件导入可按内容嗅探 JSON、ICS、CSV、HTML、纯文本等，另可用外部 App “用 wedo 打开”文本类附件。`ScheduleParser` 中确有名为 `parseWakeUpJson`、`parseWakeUpShareText` 的历史兼容解析分支，但这**不证明** WakeUp 提供可导出的 JSON 文件，也不证明其在线分享口令可直接粘贴导入。对外说明以用户实际可导出的 ICS 为准；真实 WakeUp ICS 样本的跨版本兼容还须以脱敏样本回归验证。

导入策略包括新建、追加非冲突、全部追加、覆盖当前等，具体启用选项和确认条件在 `ImportPreviewDialog`/`ImportApplyMode`；下游修改前要检查每条路径是否保留原课表并支持失败回滚。导出入口在设置页，具体格式以当前 `ExportScreen` 及其测试为准，不把旧计划里的“在线分享”当作已实现。

### 5.4 课程与设置操作

- 点课程：底部详情展示课程名、教师、地点、时间/节次、周次、备注；如果冲突簇存在，可选择默认置顶层；可进入编辑。
- 长按课程：整组换色、编辑、复制单次记录、删除单次记录；删除有二次确认。若调整 `groupId` 语义，必须同步检查这些操作。
- 设置页分四组：外观与手感、课表显示、课表与数据、隐私与关于；外观子页提供主题预设和系统/浅色/深色模式。
- 课表在本机 Room 中可离线查看；用户主动清课程和退出教务登录在产品语义上应分离，修改相关代码时先确认不会误删缓存。

## 6. 安全、隐私和许可证不变量

这是接手后任何改动都不能越过的边界：

1. WebView 只允许已确认的 HTTPS 学校/认证主机在内部导航；外部链接由系统浏览器接管。JLJU 教务主机是 `jwxt.jlju.edu.cn`，已取证认证主机包括 `lxr.jlju.edu.cn`、`cas.jlju.edu.cn`。**不要**把白名单扩大成 `*.jlju.edu.cn`，更不能对所有学校做通配符放行。
2. WebView 仅为学校网页启用必要 JavaScript；JS Bridge 只能在可信教务顶层页面短暂安装，离开就移除。证书错误 `cancel()`，混合内容、文件/内容访问均禁用；不读密码输入框、不注入破解登录逻辑。相关守护测试为 `WedoWebViewSecurityContractTest`。
3. `AndroidManifest.xml` 中 `allowBackup=false`、`usesCleartextTraffic=false`；`network_security_config.xml` 只信系统证书；`data_extraction_rules.xml` 与 `backup_rules.xml` 排除课表/设置备份。调整 Android 版本时重查这些策略，不把敏感会话迁移到云备份。
4. 原始 HAR、Cookie、Token、授权头、真实学号、姓名、课表、签名密钥绝不提交 Git、Issue、PR 或测试夹具。公开 `test/fixtures/jlju/` 只存脱敏结构；私有证据位于被忽略的 `test/fixtures/jlju/private/`，不得上传。`scripts/sanitize-jlju-har.ps1` 供本地脱敏，`scripts/scan-secrets.ps1` 供提交前扫描。
5. 对日志/诊断保持审慎：新增错误日志不得带 URL 参数、完整异常正文或课表原文。现有路径也应在发版审查中重新复核，不能仅凭“已有隐私文档”认定已完全合规。
6. GPL-3.0 约束源码/APK 分发：保留 `LICENSE`、上游版权/来源与第三方许可；发布 APK 时提供对应源码、构建脚本与同一标签。维护团队的非商业运营承诺**不是**禁止他人商业使用的附加许可条款。学校名称只表示适配对象，不代表官方合作或授权。

详见仓库根 `PRIVACY.md`、`LICENSE`、`docs/LICENSE_REVIEW.md`。发布前仍需完整依赖许可清单和人工复核。

## 7. 测试、CI、发布的真实状态

`android.yml` 在 PR 或 `main` 推送时执行：安装 SDK → 扫描已跟踪文件敏感信息 → `testDebugUnitTest` → `lintDebug` → `assembleDebug` → 上传 Debug APK。CI 用 JDK 21；这能证明自动化构建门，不代表登录真实学校、不同 Android 设备或正式签名 APK 已验收。旧 Gitee 同步/修复脚本已删除，本项目当前不配置该镜像发布流程。

本地已有 JLJU 解析、WebView 安全契约、课程颜色、ICS 解析、外部打开 MIME、导入错误提示等测试。新增功能至少遵循“纯解析单测 + 数据边界测试 + Compose/UI/设备冒烟”三层验证。课程周次必须覆盖普通周、单双周、离散周、跨节次、同名不同地点、重叠课程、空学期与部分损坏输入。用户真机账号只由本人输入，不要求提供认证材料。

发布仍未完成的门包括：完整 SSO 回跳、验证码条件、会话过期/维护响应、JLJU 重新导入且旧数据不丢、WakeUp 真实 ICS 脱敏样本、API 26 与字体放大/5-6 日布局、至少三名不同课程结构测试者、本地库升级和安全许可复核。`docs/RELEASING.md` 所说的 `v*` 标签签名发布目前只是计划；当前 `release` build type 未签名，仓库中没有自动签名/上传 Release 的工作流。不要声称已有 GitHub `1.0.0` Release。

## 8. 常见修改任务从哪里开始

| 需求 | 首先阅读/修改 | 必须补的验证 |
| --- | --- | --- |
| 调整首页布局/顶部/ Dock | `MainActivity.kt`、`ScheduleScreen.kt`、`WedoWeekHeader.kt`、`WedoGlass.kt`、`CourseTableView.kt` | 切周、纵向滚动收起/恢复、手势栏遮挡、浅/深色、API 26 |
| 调整课程卡信息或颜色 | `WedoCourseCard.kt`、`WedoDesign.kt`、`CourseColorUtil.kt`、`WedoSettingsScreen.kt` | 名称稳定色、手选色、对比度、字号放大、字段全局设置、冲突红色 |
| 调整主题/玻璃性能 | `Theme.kt`、`ThemePresets.kt`、`WedoDesign.kt`、`WedoGlass.kt`、`AppearanceScreen.kt` | 主题预设与系统取色、低内存回退、动效关闭、明暗背景 |
| 增加学校 | 先补真实脱敏证据和夹具，再改 `JwImportViewModel.defaultWedoSchools()`、必要时 `JwParserRegistry`/Parser、`JwWebViewLoginScreen` 白名单 | 官方入口与回跳、无课、单双周、会话失效、异常响应、域名安全测试 |
| 增加文件格式 | `ImportSheet.kt`、`ScheduleParser.kt`、必要时 `AndroidManifest.xml` 的外部打开 MIME | 内容嗅探、预览、覆盖/追加、错误与大文件输入、Round-trip |
| 改学期/课程模型 | `TimeTableEntity.kt`、`CourseEntity.kt`、`AppDatabase.kt`、`ScheduleRepository.kt`、`ScheduleViewModel.kt` | Migration、旧数据保留、当前周、不同周次、重复导入、Room 事务 |
| 增加诊断/网络功能 | `JwParseDiagnostics.kt`、WebView/导入 ViewModel、隐私配置 | 不记录原文/Token/学号，TLS 不放宽，异常仍保留旧课表 |
| 做正式发布 | `app/build.gradle.kts`、`.github/workflows/`、`docs/RELEASING.md`、`LICENSE` | 签名与 SHA-256、标签源码一致、依赖许可、安全扫描、三人真机 |

新增学校的顺序必须是：**证据与权限确认 → 脱敏夹具 → 离线解析器测试 → 域名/登录策略 → 产品目录开放 → 真机验收**。仅看到上游 `schools.json` 有配置，不允许直接给用户显示“支持”。若学校禁用 WebView 或依赖外部认证，应设计系统浏览器/Custom Tabs 回调，不退回原生密码输入。

## 9. 接手后的 30/60/90 分钟清单

**前 30 分钟**：确认 `git status`、当前提交与 `origin/upstream`；读本手册、README、PRIVACY、`JLJU_EDUCATION_SYSTEM.md`；检查 IDE SDK/JDK；运行 Debug 单测和构建。不要先改包名或导入协议。

**接着 30 分钟**：从 `MainActivity → ScheduleScreen → CardsGridView → WedoCourseCard` 走一遍首页，从 `ImportSheet → JwImportActivity → JwImportViewModel → Room` 走一遍导入；用公开脱敏夹具看测试，不打开或提交私有 HAR。安装对应 ABI 的 Debug 包，以假课程体验滚动、切周、课程详情、导入预览、深浅色与设置。

**最后 30 分钟**：选一个小改动，先找对应回归测试再改代码；跑相关单测、`lintDebug`、`assembleDebug`、敏感信息扫描；记录当前证据仍不足的内容。任何计划进入发布的变更都要额外检查真机、API 26、备份/TLS、许可和 `docs/RELEASING.md` 与真实 CI 的一致性。

## 10. 接手后需要优先偿还的风险

1. 认证链的真实边缘场景未闭环：补齐交互式 SSO、验证码、过期、退出、维护/超时的脱敏证据与测试；不能靠猜 URL 或抓用户密码解决。
2. Release 文档与工作流不一致：在发布前实现真正的签名、版本标签、APK 校验和与源码对应，并保护密钥。
3. 基础日期计算有设备本地时间与 `Asia/Shanghai` 混用风险；检查跨时区和学期范围外的显示，不要硬夹到首周/末周后掩盖问题。
4. `JwNewZfParser.parseWeekStr` 对空或无法识别的周次表达式可能回落到 `1–16`，这在未知校方响应变化时可能把单次课误展示为整学期课。需要用异常样本明确失败/跳过策略并补回归，不能把该回退误写为“严格解析”。
5. WakeUp ICS 入口需要真实、可公开的脱敏样本和多版本验证；若格式不兼容，明确提示限制，不能再新增虚构的 WakeUp JSON 能力。
6. 部分旧 UI/组件/WorkManager/小组件代码留在树中但产品已禁用；清理时逐条追调用链和许可证来源，避免破坏导入、数据库或上游历史兼容。
7. 玻璃视觉目前是模拟层，质量档和弹簧反馈仍需真机性能、字号、TalkBack 与对比度测试。即使以后实现更高级 GPU 效果，也要保留平衡/流畅的可读降级路径。
8. 旧状态/设计文档已精简，以本手册作为接手入口。每次重要变更应更新本手册，不要从 Git 历史复制过时结论进发行说明。

## 11. 完整交接的最低验收

下一位维护者应能独立回答并定位：安装包 ID 与源码包名为何不同；首页从哪里组合；玻璃效果真实实现为何；课程颜色和冲突如何算；哪些导入格式对用户真实可用；JLJU 登录页面与解析器的安全边界；数据保存在哪、修改实体如何迁移；如何本地构建/验证；哪些证据缺口禁止发布。若这些答案不能从当前源码或测试复现，请先修正文档或补测试，再扩大功能。
