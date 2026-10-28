# 技术债清单（液态玻璃改造 P0–P6 收尾）

> 记录于 2026-10-28，P6 收尾时点。
> 每一项都写明**现状证据**与**处置建议**，避免下一个人重新调研一遍。

## U-11 · `density` 是死键

| 项 | 内容 |
|---|---|
| 现状 | `WedoDisplay.density`（`WedoDesign.kt:12`）被读写，但**没有任何消费方**：网格密度实际由 `WedoGridDensity` 之类独立配置决定，与 `density` 键无关 |
| 证据 | `grep -rn "density" app/src/main/java` 只命中 `WedoDesign.kt` 的字段声明与 prefs 读写 |
| 风险 | 低。用户改了「密度」以为生效，实际没生效——这是**行为与 UI 不一致**，比死代码本身更值得修 |
| 建议 | 二选一：① 把设置页的密度项接到真实消费方；② 删字段与 prefs 键，并在设置页隐藏该开关 |
| 现状处置 | **未处理**。本轮不碰设置项语义，避免与 P0 已交付的三开关冲突 |

## U-18 · `bar` 字段语义窄

| 项 | 内容 |
|---|---|
| 现状 | `WedoCourseBlockColors.bar` 是课程块左侧 4px 实色条，**只表达实色**；灰化（冲突簇折叠态）靠 `copy(alpha)` 临时处理 |
| 证据 | `ConflictCard.kt:468-481`：`block.bar.copy(alpha = WedoTheme.Alpha.inactive)` |
| 风险 | 低。语义窄但自洽 |
| 建议 | 若未来要区分「灰化」与「低饱和」两种弱化，需把 `bar` 拆成两个字段 |

## R-08 · 尚未迁 NavHost

| 项 | 内容 |
|---|---|
| 现状 | 页面切换仍由 `MainActivity` 里的自建覆盖页栈承担（`pushOverlay` / `OverlayScreen`），**未使用 Navigation Compose 的 `NavHost`** |
| 证据 | `grep -rn "NavHost" app/src/main/java` 0 命中；`app/build.gradle.kts` 里 `navigation-compose` 依赖存在但未被使用 |
| 风险 | 中。返回键逐层只退一级目前靠自建栈保证，行为正确；但「深链」「跨页恢复」等能力缺失 |
| 建议 | 若要迁，必须先补齐返回栈语义测试（当前 `OverlayScreen` 栈已有契约测试），否则会引入返回键回归 |
| 现状处置 | **不迁**。当前实现行为正确，迁移属独立议题，不应混进材质改造 |

## 风险登记（来自 `docs/glass/10_risk.md` 的结论）

| ID | 风险 | 状态 |
|---|---|---|
| R-07 | 能力有但不该用：对话框族放弃 G1 采样 | ✅ 主动接受并落地（15 处统一 `WedoDialog`） |
| R-06 | L2 折射是高风险阶段 | ✅ 已灰度到底栏；真机 A/B 无可见差异 → **默认关闭**，保留手动开关 |
| VR-13 | 滚动停止后 L1→L2 会有一次可见材质跳变 | ✅ 随 L2 默认关闭一并消失 |
| VR-12 | L1 vs L2 的差异可能小于预期 | ✅ **已证实**：真机并排 A/B 肉眼无差别，方案失败处理 ③ 生效 |
| Q-84 | 帧率 P90 对比 | ⚠️ **未闭环**：需真机跑 `dumpsys gfxinfo` 对比 |

## 已闭环项

| ID | 内容 | 证据 |
|---|---|---|
| U-11 | `density` 死键 | 见上（未处理，但已记录） |
| — | 15 处 `AlertDialog` 无统一入口 | `P5GlassGovernanceTest` 文本契约 |
| — | 裸 `spring()` / `tween()` 散落 | `WedoMotionCoverageTest` |
| — | `toImageBitmap` 进入交互路径 | `GlassCaptureContractTest` |
| — | uniform 顺序漂移 | `GlassShaderUniformContractTest` |
