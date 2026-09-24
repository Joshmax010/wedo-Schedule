# wedo课程表

以吉林建筑大学为首个教务适配的 Android 原生课程表应用，后续可扩展到其他学校。项目采用本地优先设计：用户通过学校官方网页自行登录，课表在设备上解析和保存，不建设账号系统、课表服务器、广告或商业服务。

> 当前状态：基座与安全基线已经建立，JLJU 正常及空课表接口、官方 SSO 入口和脱敏夹具已取得。应用内 JLJU 入口已按最小域名白名单开放测试；交互式登录回跳和会话过期仍需真机验证，尚不满足发布条件。

## 功能目标

- Android 8.0（API 26）及以上。
- 吉林建筑大学官方网页登录与课表导入。
- 周视图、课程详情和周切换。
- 本地 Room 缓存，离线可查看最后一次成功导入的课表。
- 浅色/深色主题。
- 不依赖 Google Play Services。

首版不包含云同步、用户中心、自建后端、桌面组件或课程提醒。

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
- Gradle 9.3.1、AGP 9.1.1、JDK 17+，compile/target SDK 37.0。
- Application ID：`com.wedo.schedule`。

## 本地构建

准备 JDK 17 或 Android Studio 内置 JBR，并通过 Android SDK Manager 安装 Android SDK Platform 37。

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
```

Debug APK 输出在 `app/build/outputs/apk/`。正式签名密钥不得提交仓库；Release 所需环境变量见 [发布说明](docs/RELEASING.md)。

## 项目来源

wedo 基于 [Sleepy · 轻课表](https://github.com/lingion/sleepy) 二次开发，基准提交为 `08a1f26a5d7b1a117e2216d92804b202ddde228d`。我们保留原项目及其引用项目的版权和许可证说明，主要修改包括：

- 将产品收敛为吉林建筑大学单校版本。
- 使用 `wedo课程表` 品牌和 `com.wedo.schedule` Application ID。
- 收紧 WebView 域名、证书、混合内容和 JavaScript 桥策略。
- 禁止课程与会话数据进入系统云备份。
- 新增吉林建筑大学新版正方解析入口、脱敏夹具和回归测试。
- 移除上游与当前 MVP 无关的预编译采集器、旧版截图及 OPPO 流体云示例；如需追溯，可查看保留的上游 Git 历史。

## 许可证与声明

本项目依照 [GNU GPL v3](LICENSE) 发布。维护团队不收费、不植入广告、不经营用户数据；这是运营承诺，不构成对 GPL 权利的额外限制。

本项目不是吉林建筑大学官方应用，与吉林建筑大学不存在隶属、商业合作或官方授权关系。用户应只访问本人有权访问的数据，并遵守学校规定。第三方代码和资源分别遵守其原始许可证。

## 贡献

提交代码前请运行构建、测试、Lint 和敏感信息扫描。教务适配问题只能提供完全脱敏的响应结构；请勿在 Issue、PR、截图或日志中发布账号、密码、Cookie、Token、真实姓名、学号或未脱敏课表。
