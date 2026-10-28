# wedo课程表

新接手项目请先阅读 [项目交接手册](docs/PROJECT_HANDOFF.md)：它按当前源码说明技术栈、UI/美学、功能交互、数据流、安全边界、构建验证与未完成事项。

以吉林建筑大学为首个教务适配的 Android 原生课程表应用，后续可扩展到其他学校。项目采用本地优先设计：用户通过学校官方网页自行登录，课表在设备上解析和保存，不建设账号系统、课表服务器、广告或商业服务。

> 当前状态：V3 交互重设计已全量落地（今日 / 课表 / 设置 三页 + 全屏导入向导），并完成一轮「液态玻璃」材质系统改造（P0–P6 全部收口，872 个单元测试全绿）。基座与安全基线已经建立，JLJU 正常及空课表接口、官方 SSO 入口和脱敏夹具已取得。应用内 JLJU 入口已按最小域名白名单开放测试；交互式登录回跳和会话过期仍需真机验证，尚不满足发布条件。

## 功能目标

- Android 13（API 33）及以上。
- 吉林建筑大学官方网页登录与课表导入。
- 周视图、课程详情和周切换。
- 本地 Room 缓存，离线可查看最后一次成功导入的课表。
- 浅色/深色主题，可选跟随系统。
- 不依赖 Google Play Services。

首版不包含云同步、用户中心、自建后端或课程提醒。

## 界面与材质系统

界面按「苹果 HIG 对齐 + 液态玻璃」重做，权威文档：

- [液态玻璃方案主索引](docs/glass/00_MASTER.md)：全量设计推导与编号（M-/V-/O-/Q-）。
- [液态玻璃实现规范](docs/DESIGN_SPEC_V4_GLASS.md)：条款正文。
- [界面设计规范 v2](docs/DESIGN_SPEC_V2.md)：色彩 / 字阶 / 间距 / 圆角 / 无障碍（材质章节已由 v4 取代）。
- [验收报告](docs/glass/ACCEPTANCE_REPORT.md) 与 [技术债清单](docs/glass/TECH_DEBT.md)。

材质分三级，**只有浮层允许采样**：

| 档 | 含义 | 用在哪 |
|---|---|---|
| G0 | 镀层，不采样 | 全部内容卡、设置卡、课程块、15 个对话框 |
| G1 | 静态采样 | 课程详情 / 分享 / 课程动作 Sheet、周次选择器 |
| G2 | 实时采样 | 底部浮动胶囊底栏 |

两个关键工程决策：

- **采样用 `GraphicsLayer` 录制，不做 `toImageBitmap()`**，因此交互路径不会因为位图分配而卡顿。
- **vibrancy**：半透明面板身后的内容自动降饱和（iOS 行为），用 `ColorFilter.colorMatrix` 实现。少了这一步，浅色下底栏压住彩色图标会出现刺眼色斑。

材质配额有闸：同屏 G2 ≤ 3、G1 ≤ 2，单个面板包围盒 ≤ 屏面积 15%，超出按面积倒序静默降级。

运动系统同样收敛：弹簧族表 8 根（`ui/motion/WedoSprings.kt`），生产代码禁止裸 `spring()`；减动效取「用户开关 **且** 系统动画倍率 > 0」双源与门，位移类跳终态、出现类只淡变。

## 隐私与安全

- wedo 不提供账号密码输入框，也不读取或保存用户在学校网页中输入的密码。
- 不上传个人课表，不保存教务原始响应。
- 不绕过验证码、证书错误、登录限制或其他安全机制。
- 认证页面只允许经过真实取证确认的 HTTPS 学校/SSO 域名。
- 课程数据库和应用设置默认禁止进入 Android 云备份。
- 测试夹具必须完全脱敏，Cookie、Token、Authorization、真实姓名和学号禁止进入 Git。

详见 [PRIVACY.md](PRIVACY.md) 和 [吉林建筑大学取证说明](docs/JLJU_EDUCATION_SYSTEM.md)。

## 技术栈

- Kotlin、Jetpack Compose、Material 3。
- Room、DataStore、Coroutines。
- Jsoup 与按教务协议划分的离线 Parser。
- Gradle 9.3.1、AGP 9.1.1、JDK 17+，compile/target SDK 37.0，minSdk 33。
- Application ID：`com.wedo.schedule`。

## 本地构建

准备 JDK 17 或 Android Studio 内置 JBR，并通过 Android SDK Manager 安装 Android SDK Platform 37。

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
```

日常出包推荐用仓库脚本（默认只构建 x86_64 调试包，供模拟器验收）：

```bash
bash scripts/build.sh assembleDebug      # 产物：app/build/outputs/apk/debug/app-x86_64-debug.apk
WEDO_ALL_ABIS=1 bash scripts/build.sh assembleDebug   # 需要全部 ABI 时
```

Debug APK 输出在 `app/build/outputs/apk/`。正式签名密钥不得提交仓库；Release 所需环境变量见 [发布说明](docs/RELEASING.md)。

## 精简目录

- `app/`：应用源码、资源、单元测试与 UI 测试；请勿按类名或学校名盲删继承模块。
- `app/src/main/java/com/wedo/schedule/ui/glass/`：材质分级、采样管线（Host / Stage / Backdrop）、配额与 vibrancy。
- `app/src/main/java/com/wedo/schedule/ui/motion/`：弹簧族表、减动效门控、按压与单进度。
- `app/src/main/java/com/wedo/schedule/ui/components/`：通用组件（`WedoTabBar` / `WedoDialog` / `WedoShell` 等）。
- `gradle/`、Gradle 脚本：唯一构建入口。
- `.github/workflows/android.yml`：编译、测试、Lint 和敏感信息扫描。
- `scripts/`：构建脚本、敏感信息扫描及 JLJU 取证脱敏。
- `test/fixtures/jlju/`：公开脱敏夹具。
- `docs/`：交接手册、设计规范、JLJU 取证、发布要求、许可审查和液态玻璃方案。

2026-09-27 已移除上游历史发布材料、调研副本、旧采集工具、重复 Wrapper 和非 wedo 的 Gitee 工作流。已跟踪的旧材料可通过 Git 历史追溯；本地删除项另可从系统回收站恢复。构建会重新生成 `.gradle/`、`.kotlin/` 和 `build/`，这些生成目录不属于需要交接的源码。

## 项目来源

wedo 基于 [Sleepy · 轻课表](https://github.com/lingion/sleepy) 二次开发，基准提交为 `08a1f26a5d7b1a117e2216d92804b202ddde228d`。我们保留原项目及其引用项目的版权和许可证说明，主要修改包括：

- 将产品收敛为吉林建筑大学单校版本。
- 使用 `wedo课程表` 品牌和 `com.wedo.schedule` Application ID。
- 收紧 WebView 域名、证书、混合内容和 JavaScript 桥策略。
- 禁止课程与会话数据进入系统云备份。
- 新增吉林建筑大学新版正方解析入口、脱敏夹具和回归测试。
- 移除上游与当前 MVP 无关的采集工具、旧发布材料、截图及 OPPO 流体云示例；如需追溯，可查看保留的上游 Git 历史。
- 删除 30 个上游教务协议解析器、182 校学校目录与相关测试夹具，教务导入链路收敛为单一新版正方实现；原生导入格式标识由 `#sleepy-v1` 改为 `#wedo-v1`（导入端兼容旧标识）。
- 网格视图与列表视图解析（`parseKbGridCells` / `parseKbListRows`）按本仓脱敏夹具独立实现，不再是 `zhengfang_01.js` 的移植件。仍在使用的第三方成果继续在应用内「开源许可」页逐条标注，审查结论见 `docs/LICENSE_REVIEW.md`。
- 本地存储键名统一为 `wedo` 前缀：数据库 `sleepy.db` → `wedo.db`，偏好文件 `sleepy_prefs` → `wedo_prefs`。老安装首次启动会自动搬迁既有数据，旧文件保留不删；这是文件级改名，数据库 schema 与版本号未动。
- 界面按 V3 方案全量重做：Tab 收敛为「今天 / 课表 / 设置」，课表改为周视图 + 横向换周，导入向导改为全屏流程，设置页改为 iOS 分组风格；配套建立液态玻璃材质系统（`ui/glass`）与统一运动系统（`ui/motion`）。

## 许可证与声明

本项目依照 [GNU GPL v3](LICENSE) 发布。维护团队不收费、不植入广告、不经营用户数据；这是运营承诺，不构成对 GPL 权利的额外限制。

本项目不是吉林建筑大学官方应用，与吉林建筑大学不存在隶属、商业合作或官方授权关系。用户应只访问本人有权访问的数据，并遵守学校规定。第三方代码和资源分别遵守其原始许可证。

## 贡献

wedo 项目维护与贡献者：[Joshmax010](https://github.com/Joshmax010)。Sleepy 原作者及上游贡献者的归属保持不变，详见上面的项目来源与 `NOTICE`。

提交代码前请运行构建、测试、Lint 和敏感信息扫描。教务适配问题只能提供完全脱敏的响应结构；请勿在 Issue、PR、截图或日志中发布账号、密码、Cookie、Token、真实姓名、学号或未脱敏课表。
