# Android 课程表开源基座调研

调研日期：2026-09-09。候选均以实际仓库源码为依据；本机缺少 Android SDK，因此本轮完成了 Gradle Wrapper 启动验证，完整本地构建将在 M2 安装 SDK 后执行。

## 初筛候选

1. [Sleepy](https://github.com/lingion/sleepy)：Kotlin、Jetpack Compose、Room、WebView 教务导入，多种教务协议和大量解析测试。
2. [拾光课程表](https://github.com/XingHeYuZhuan/shiguangschedule)：Apache-2.0、Kotlin Multiplatform、Compose Multiplatform，适配脚本由独立仓库维护。
3. [Dawn Course](https://github.com/HF-CYGG/Dawn-Course)：GPL-3.0、Kotlin/Compose、多模块 Clean Architecture，具备脚本导入、云端解析和大量测试。
4. [WakeUp Kotlin](https://github.com/tKM9WsmQUaUgNttn3DGUsHkxG8/WakeupSchedule_Kotlin)：2018 年旧工程。
5. [Schedule](https://github.com/Yngu196/Schedule)：WakeUp 魔改版，Kotlin/XML，仍在维护。

## 淘汰结论

- WakeUp Kotlin：仓库根目录没有 LICENSE；使用 Android Support Library、Fabric、JCenter 和 AGP 3.2.1；构建文件中提交了签名口令和本机密钥路径。许可证与安全均不满足硬门槛。
- Schedule：虽然采用 Apache-2.0 且 API 26，但没有有效的解析器测试；学校列表含大量仅按名称拼接或手工填入的 URL；主要逻辑集中在大型 Activity，Room 依赖因编译问题被注释，不适合作为长期安全基座。

## 前三名评分

| 项目 | 许可 20 | 适配 20 | 构建 15 | 模型 15 | UI 10 | 安全/本地 10 | 活跃 5 | 测试 5 | 总分 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Sleepy | 20 | 20 | 12 | 14 | 10 | 6 | 5 | 5 | **92** |
| 拾光课程表 | 20 | 19 | 11 | 15 | 10 | 8 | 5 | 2 | **90** |
| Dawn Course | 20 | 15 | 12 | 15 | 9 | 7 | 5 | 5 | **88** |

“构建”分暂未给满：本机 JDK 21 可以启动各项目的现代 Gradle，但尚未安装 Android SDK 37。该限制属于本机环境，不视为仓库构建失败；最终 M2 必须以实际 `testDebugUnitTest` 和 `assembleDebug` 结果补证。

## 选择 Sleepy

固定基准提交：`08a1f26a5d7b1a117e2216d92804b202ddde228d`（2026-09-09）。

选择原因：

- 与目标完全一致的 Android 原生 Kotlin + Compose、API 26 和 Room 技术线。
- `JwParser`、`JwParserRegistry`、`JwSchoolInfo`、WebView 导入、预览和 Room 写入已经形成完整调用链。
- 已实现正方、强智、URP、金智等多种中国高校教务协议，新增学校通常只需学校元数据、必要的抓取逻辑、解析器复用或小型专用解析器。
- 调研提交包含 106 个 JVM/Android 测试源文件，其中大量直接覆盖教务解析器、学校目录、WebView 契约和课程模型。
- 周视图、课程详情、当前周、多课表、本地缓存和深色模式均已成熟。

已知缺点与处理：

- GPL-3.0 要求衍生分发继续提供对应源码和许可证；wedo 接受该要求。
- 上游 WebView 允许混合内容、部分证书错误例外和宽范围 JS 桥；wedo 必须在 M2 收紧。
- 上游课程数据库允许系统云备份；wedo 必须默认禁止。
- 上游包含提醒、组件和更新功能；wedo 首版禁用其导航、权限和后台入口，不要求立刻删除所有稳定代码。
- Kotlin 包 namespace 暂不整体迁移；只改变 Application ID 和用户可见品牌，降低无关回归。

## 其他候选说明

拾光课程表具有更宽松的 Apache-2.0 和成熟 UI，但当前工程已是 Kotlin Multiplatform，并将学校适配脚本作为独立仓库/运行时资源管理；这会扩大首版的脚本供应链与跨平台维护范围。

Dawn Course 的模块化与测试质量优秀，但工程同时包含云端解析、脚本同步、WebDAV、凭据仓库、SQLCipher 和多种同步状态机。为得到单校、本地优先的首版，需要删除或隔离的非目标能力明显多于 Sleepy。

