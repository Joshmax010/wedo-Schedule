# 教务直连导入开发包（Android，0.1.0）

这是从 [wedo课程表](https://github.com/Joshmax010/wedo-Schedule) 提取并解耦的开发者包：官方 WebView 认证、学期读取、新版正方课表请求、离线解析、结果预览、用户确认和本地保存示例。下载一个 ZIP 即可获得独立工程，无需下载整个 wedo，也不依赖 wedo 的玻璃 UI、Room、Compose、账号系统或后端。

维护/提取贡献者：[Joshmax010](https://github.com/Joshmax010)。基于 Sleepy/wedo 的现有字段映射和 JLJU 脱敏证据，采用 **GPL-3.0**；不能把它当成 Apache/MIT 的闭源 SDK 使用，分发衍生 App 必须遵守 GPL，保留原作者、源码及构建材料。

> 当前是开发者预览版。已知、具备取证的学校配置仅吉林建筑大学；新版正方协议相似不代表其他学校已经支持。代码编译和离线测试不替代真实 SSO/验证码/会话过期/API 26 验收。原 wedo App 的导入实现未被这个包替换。

## 1. 包里有什么

```text
library/          可复用 Android Library（登录、请求、解析、匿名错误）
demo/             独立、可安装的 Android 原生 Views 示例
gradle/           Gradle 9.3.1 Wrapper
export.ps1        只打包白名单源码、教程和许可证的导出脚本
README.md         本教程
NOTICE            来源、修改记录、第三方归属
LICENSE           GPL-3.0 完整文本
LICENSES/         Apache-2.0 许可文本及 Gradle 组件声明
```

源码来源路径与修改差异在 `NOTICE`，不把整套旧学校目录、历史截图或私有 HAR 塞进开发包。公开样本来自 `wedo/test/fixtures/jlju`，课程/教师/教室使用测试别名，星期/节次/周次关系保留。

## 2. 下载、打开与首次运行

1. 从 [GitHub Releases](https://github.com/Joshmax010/wedo-Schedule/releases/tag/jw-import-kit-v0.1.0) 下载 `wedo-jw-import-kit-0.1.0.zip` 和 `.sha256` 校验文件。
2. 对 ZIP 计算 SHA-256，和校验文件一致后解压；解压目录就是独立 Android 工程。
3. 用 Android Studio 打开解压目录（不是只打开 `library` 子目录）。配置 JDK；CI 使用 JDK 21，Java/Kotlin 字节码目标 17。
4. 安装 Android SDK Platform 37、Build Tools 37；在 SDK Manager 中自行接受许可。运行设备最低 Android 8/API 26。
5. Android Studio 会配置 SDK 路径；命令行可设置 `ANDROID_HOME`，或在工程根 `local.properties` 写自己的 `sdk.dir`。本机绝对路径不在 ZIP 中。
6. 运行 `demo` 的 Debug 版本，包名为 `com.wedo.jwimport.demo.debug`，不覆盖 wedo 或其他课表 App。

Windows PowerShell：

```powershell
Get-FileHash ./wedo-jw-import-kit-0.1.0.zip -Algorithm SHA256
# 下面命令在 ZIP 解压出的工程根目录执行
./gradlew.bat :library:testDebugUnitTest :library:lintDebug :demo:lintDebug
./gradlew.bat :demo:assembleDebug :library:assembleRelease
```

macOS/Linux：

```sh
chmod +x gradlew
./gradlew :library:testDebugUnitTest :library:lintDebug :demo:lintDebug
./gradlew :demo:assembleDebug :library:assembleRelease
```

输出：`demo/build/outputs/apk/debug/demo-debug.apk`、`library/build/outputs/aar/library-release.aar`。Release AAR 是代码库，不是可安装 APK，也不包含 Jsoup 二进制；优先采用下面的源码模块方式，让 Gradle 正确解析依赖。依赖/Gradle 下载需联网，国内镜像仅改变依赖下载顺序，不参与学校认证。

构建组合：AGP 9.1.1（内置 Kotlin）、Gradle 9.3.1、compile SDK 37、min SDK 26、Jsoup 1.18.1；库不要求 Compose、Room、Google Play Services 或协程。已有项目如果使用其他 AGP/Kotlin 组合，应先验证源码兼容，不能盲目升级宿主项目。

## 3. 示例 App 怎么使用

### 无账号也能演示

首次启动同意隐私说明，点击“脱敏样例”。示例读取**明确标注为离线测试数据**的夹具，经真实解析器产生课程预览，并不假装学校登录成功。点“确认保存”后课程预览保存在本机 SQLite；重启或断网后点“离线缓存”仍可查看。

### 真实教务导入

1. 点击“官方登录”，在学校网页自行输入账号密码/验证码。应用不读取密码，也不提供原生密码表单。
2. 按校方页面正常完成认证并进入教务系统。顶部的可信页面状态仅表示域名正确，**不是登录成功判据**。
3. 点“读取学期”，选择校方返回的学年和学期。JLJU 学期代码 `3/12/16` 不等于 UI 的第 `1/2/3` 学期，不能自行写死转换。
4. 点“读取课表”，检查课程名称、教师、教室、星期、节次、明确上课周集合，以及匿名跳过记录。
5. 点“确认保存”。有坏记录时要先核对；明确空学期也需二次确认。失败/取消不会删除上一份确认缓存。
6. “退出登录”会清除**此 App 全部 WebView Cookie**，不会删除已保存课程。CookieManager 是应用共享的，不是每个 WebView 独立；宿主有其他 WebView 时必须评估影响后才能使用该操作。

示例只保存标准化课程的文字预览，不保存原始响应或账号，也不单独序列化/导出 Cookie、Token。登录会话由 Android WebView 管理，可能被系统 WebView 持久化；需要清会话时使用明确的退出操作。真实产品应将结构化 `ImportedCourse` 转入自己的既有数据库，不能把示例预览缓存当成完整课程数据库。

如果 SSO 流程不能在 WebView 运行、外链转到系统浏览器后会话不能共享、校方接口改变或当前白名单不涵盖新认证域名，停止导入并收集脱敏结构证据；不要采集用户密码、放行证书错误或直接扩成域名通配符。

## 4. 接到你自己的 Android 项目

### 4.1 推荐：源码模块

将包里的 `library/` 复制为宿主的 `jw-import/`。将 `LICENSE`、`NOTICE`、`LICENSES` 一起保留，并在宿主开源声明中标注来源。在宿主 `settings.gradle.kts` 增加：

```kotlin
include(":jw-import")
```

宿主应用依赖：

```kotlin
dependencies { implementation(project(":jw-import")) }
```

库内 `implementation(org.jsoup:jsoup:1.18.1)` 已声明。宿主统一配置 Google/Maven Central 仓库并保证兼容 AGP/Kotlin。不要复制 `demo` 的 applicationId 到自己的 App。

### 4.2 建立控制器

在你自己的 Activity/Fragment 的主线程创建一个**专用于教务登录**的 WebView：

```kotlin
val controller = JwImportController(
    webView = officialWebView,
    school = SchoolDefinition.JLJU,
    listener = object : ImportListener {
        override fun onTerms(options: TermOptions) {
            // 用 options.years / options.semesters 展示选择器。
            // 选择完成后创建 TermSelection(year.value, semester.value)。
        }
        override fun onSchedule(result: ScheduleResult) {
            // 展示预览和 result.skipped；等待用户确认后才写数据库。
            // result.emptySemester 为 true 才表示明确无课。
        }
        override fun onError(error: ImportError) {
            // 根据枚举显示简短提示，不写入 URL 参数/响应/认证值。
        }
    },
)
controller.openLogin() // 用户点击登录时调用
// 登录到可信教务页面后，用户点击读取学期：
controller.loadTerms()
// 用户从校方返回选项中选择后：
controller.loadSchedule(TermSelection(selectedYear.value, selectedSemester.value))
```

这是接入片段，`officialWebView`、`selectedYear`、`selectedSemester` 是你的 UI 持有对象，**不是库中隐藏的全局变量**。能直接构建运行的完整代码在 `demo/.../MainActivity.kt`。所有控制器方法与回调在主线程；耗时解析在库的后台线程执行。独立静态解析器可被宿主放到后台任务，不能在 UI 线程解析大响应。

生命周期销毁时按顺序调用 `controller.close()`，再销毁这个专用 WebView；`close()` 不销毁宿主所有的 WebView。取消只结束请求，不删除课程。Compose 可用 `AndroidView` 持有 WebView，在释放/离开页面时完成同样的关闭；不要在每次重组时新建控制器。

### 4.3 映射课程并可靠保存

`ImportedCourse` 是临时传输对象，含 `name/teacher/room/day/startNode/endNode/weeks/note`；`weeks` 是明确整数集合。`ScheduleResult` 含所选学期、源记录数、去重后的课程时段、跳过原因和获取时间。一次源记录包含多段节次时会产生多个时段；重复记录会去重，因而源记录数不一定等于结果数。

宿主数据库支持周集合时直接映射。若像 wedo 的 `CourseEntity` 只支持起止周/单双周，可先用每周一条的安全展开再优化压缩：

```kotlin
// 示意：仅用于已有 wedo CourseEntity 的宿主；库本身不依赖 Room。
val rows = result.courses.flatMap { course ->
    course.weeks.sorted().map { week ->
        CourseEntity(
            groupId = yourStableGroupId(course), tableId = confirmedTableId,
            courseName = course.name, teacher = course.teacher, room = course.room,
            note = course.note, day = course.day, startNode = course.startNode,
            step = course.endNode - course.startNode + 1,
            startWeek = week, endWeek = week, type = 0, color = "#FF1764D9",
        )
    }
}
// 用户确认后，在宿主 Room withTransaction 中新建/覆盖；失败回滚。
```

`yourStableGroupId` 和 `confirmedTableId` 必须由宿主实现；不要随机重算业务键后宣称重复导入幂等。分组与覆盖应限定在同一学校/学期/课表，跨学期不能互相覆盖。若把周集合压缩成连续/单双周范围，须测试展开后的集合与原集合完全一致。开学周一、总周数、节次作息目前不由开发包自动保存，需从可靠来源获取或由用户确认，再交给宿主课表配置。示例没有伪造开学日期。

## 5. 接口与错误含义

- `SchoolDefinition`：官方登录 URL、精确认证域名、教务域名、已确认路径前缀及功能码；严格 HTTPS、无通配符、默认端口。
- `ImportListener.onPageChanged`：当前顶层页是否在教务白名单，仅页面状态。
- `onTerms`：校方学期选择器解析成功；`onSchedule`：结构可识别、课程解析完成；这些返回结果才是数据面的成功证据。
- `onBusyChanged`：禁用重复操作/展示进度；`onError`：稳定匿名枚举，绝不返回原始响应或认证值。
- `NOT_ON_EDUCATION_PAGE`：尚未在允许的数据域；`NETWORK`：断网、fetch 失败或禁止的重定向；`SERVER`：非成功 HTTP 状态；`TLS`：证书错误拒绝。
- `SESSION_EXPIRED`：401/403 或已识别登录表单，是保守提示，不是对所有 SSO 失效形态的完整认证分类；`INVALID_RESPONSE`：未知结构/缺 `kbList`/非法 JSON；`TIMEOUT`：20 秒未完成；`CANCELLED`：主动取消；`BLOCKED_NAVIGATION`：外链交给浏览器或非 HTTPS/自定义协议被阻止。

`kbList: []`、`kbList` 缺失、所有记录损坏必须区分。单条坏数据返回索引和 `RecordError`；全部损坏时 `emptySemester=false`，宿主应禁止空结果覆盖旧数据。未知周次严格拒绝，不能回退成整学期上课。

当前限制：只处理顶层 `kbList` JSON；不暴露原项目的 HTML/其他学校协议兜底。节次范围 1..32，周次 1..100，正文最多 1,000,000 字符、最多 5000 源记录；超界失败，不能悄悄截断课程。老师/教室/备注字段的文本长度有防御上限。需要更大规模时先评估、改上限并补测试。

## 6. 工作原理与安全要求

```text
用户操作官方 WebView 登录
  → 精确 HTTPS 域名检查
  → 可信教务顶层执行同源 fetch（Cookie 留在 WebView）
  → 随机请求槽位 + evaluateJavascript 轮询
  → 校验导航代次与当前域名，页面跳转即废弃旧结果
  → 后台纯解析器 → 标准化课程与匿名错误
  → 宿主预览/用户确认 → 宿主本地事务保存
```

开发包不安装 `addJavascriptInterface`，不读取密码输入框，不把 Cookie 传给原生 HTTP 客户端，不为所有导航页注入采集脚本。请求使用同源凭据，拒绝自动 HTTP 重定向，不能把未知登录 HTML 当成无课。请求槽位仅短期存在于已信任网页内，完成/取消会删除；不把页面原文长期保存在库中。

宿主必须配置 INTERNET、禁止明文网络/云备份/设备迁移、信任系统证书且拒绝 TLS 错误；参照 `demo/AndroidManifest.xml` 和 `demo/res/xml/`。库不通过 Manifest 强行覆盖宿主整个应用的安全配置，**这不意味着宿主可以不配置**。共享 Cookie 退出的影响已明确，不假装支持按域精确删除。

不得关闭证书校验、绕验证码、修改账号访问范围、扫描学校接口或收集他人信息。不要把原始 HAR、账号、Cookie、Token、真实学号和课表放进 GitHub Issue/PR/教程截图。调试 WebView 只能由宿主在受控 Debug 场景开启，Release 必须关闭。库不包含日志上传、统计或广告。

## 7. 增加其他学校（不能只换名字）

1. 确认你有权访问本人课表，通过校方正常网页登录。
2. 本地采集官方入口、认证链、学期与课表请求结构；原始 HAR 只放仓库外。
3. 脱敏后确认系统是否仍是同一 `kbList` 协议，真实字段/周次是否匹配，是否允许 WebView。
4. 新建经过证据确认的 `SchoolDefinition`；仅添加精确主机、正确前缀/功能码，不猜 URL。
5. 若协议不同，新增独立适配器和脱敏测试，不能硬套这个新版正方请求。
6. 至少验证正常课表、空课表、SSO 回跳、验证码、取消、过期、维护、异地登录与退出，再向用户声明支持。

JLJU 配置来源详见主项目的 [教务取证记录](https://github.com/Joshmax010/wedo-Schedule/blob/main/docs/JLJU_EDUCATION_SYSTEM.md)，该文件仍明确列出未完成证据。其他学校的原生/外部登录限制应优先保持安全，不退回收集密码的方式。

## 8. 测试、打包和贡献

`ParserTest` 覆盖真实脱敏正常/空/学期样本、单双/离散/混合/位图周次、补零/多段节次、重复、部分损坏、未知字段结构和精确域名策略。该测试集没有真实账号，也不直接登录学校。端到端 WebView、API 26、证书失败和真实会话仍需各开发者在自己的设备上验证。

在主仓库的干净提交上使用 PowerShell 7 执行：

```powershell
# 在主仓库根目录
./extras/jw-import-kit/export.ps1
```

脚本输出到主仓库 `releases/`（已忽略）：一个源代码 ZIP 和 SHA-256。按白名单打包，不含 `.git`、`local.properties`、缓存、APK/AAR、原始 HAR、密钥或真实数据；ZIP 内 `SOURCE_COMMIT.txt` 记录来源提交。预检可加 `-AllowDirty`，但不可把它当作正式源代码标签一致性的证明。最终发布使用干净提交和匹配标签。

若从 ZIP 脱离 Git 使用，源码文件本身仍可构建；`export.ps1` 面向主仓库维护者，不要求接入者重新发布。代码、教程、许可有改动时都应一起更新；问题反馈只提供匿名错误码和完全脱敏结构。

## 9. 与原 wedo 的差异与验收状态

这是**可运行的解耦提取版，不是原文件逐字复制**。原版包含多教务协议、Compose 页面、Room 写入和学校选择 UI；开发包只保留 JLJU 已取证的新版正方数据面，提供通用宿主接口和轻量 Native Views 示例。新包的单条课程持有明确 `weeks` 集合；同一正常样本原版拆为 12 个周范围记录，新包保留 6 条课程时段并保留全部周集合，二者不能仅按条数判断丢课。

编译/解析测试结果与现场验证范围见 GitHub Release 说明。不要因为 ZIP 里存在 Demo APK 的构建入口，就认为真实学校认证或商店发布已验收；Release Demo 未配置正式签名，不承诺任意学校 SSO 兼容。
