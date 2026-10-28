# 07 · 分阶段工程实施计划

> 本卷定义**怎么分批做**：7 个阶段、每阶段 11 项、阶段之间的硬门禁与回退。
> **本卷不写**：参数值（02/03/04）、组件方案（05）、模块划分（06）、判定方法（09）。
> 上游：卷 06 的模块划分与依赖规则、卷 01 的 `ADR-19`（G0 独立可交付）
> 下游：卷 09 的 `Q-ID` 是本卷门禁的判定依据；卷 11 的交付清单按本卷的产出

---

## 7.1 本卷的三条排序原则

阶段划分不是「按工作量均分」，而是按**风险释放顺序**。三条原则：

| # | 原则 | 来源 |
|---|---|---|
| **S-1** | **先把「已实现的东西」做到一致与可回归，再扩光学维度** | FlClash 研究 §06 的实施顺序结论（本项目独立采纳） |
| **S-2** | **不要在没有视觉回归网的情况下先动 shader 光学核心** | 同上。L2 排在 P4，前面必须有 G0/L1 的观感基线 |
| **S-3** | **高风险技术不得一次性进主线**：L2 折射、跨窗口快照、全量背景捕获三者都要灰度 | `ADR-28`、卷 01 `R1` |

S-1 的实践含义：P1 只做 G0（零采样、零 shader、零新窗口），**它必须能独立交付**。如果 P1 之后项目停止，界面也应该比改造前好——这是 `ADR-19` 的验收条件，也是 `SC-03`。

---

## 7.2 阶段总表

| 阶段 | 名称 | 目标一句话 | 涉及对象 | 阶段门禁 | 可回退 |
|---|---|---|---|---|---|
| **P0** | 让改动可被验证 | 构建能跑、开关能改、测试可信 | — | `Q-01` / `Q-02` | 不需要（纯前置） |
| **P1** | 令牌 + G0 静态材质 | 全 app 有统一的「厚度感」，零采样 | L-01 / L-05 / L-06 / L-08 / L-09 / L-10 / L-11 / L-13 / L-18 / L-21 / L-22 / L-23 | `Q-05` / `Q-31` / `Q-64` | `GLASS_ENABLED=false` |
| **P2** | Host + L1 真模糊 | 底栏能看见下方内容流过 | L-02 / L-03 / L-17 / L-20 | `Q-07` / `Q-08` / `Q-50` | 配额强制 G0 |
| **P3** | 运动系统 | 按压、回弹、形变有统一族表 | L-04 / L-05 / L-19 | `Q-15` / `Q-12` / `Q-68` | 逐项改回原 `spring()` |
| **P4** | L2 折射灰度 | 底栏边缘能弯折，滚动时不折 | L-02 | `Q-52` / `VR-12` | 删 1 文件 + 改 1 枚举 |
| **P5** | 剩余对象 + 配额治理 | 24 个对象全覆盖，配额有闸 | L-07 / L-12 / L-14 / L-15 / L-16 / L-24 | `Q-83` / `Q-54` / `Q-61` | `maxG2 = 0` |
| **P6** | 全面验收与收尾 | 卷 09 全表通过，V4 规范落地 | 全部 | 卷 09 全部 `Q-ID` | — |

---

## 7.3 阶段依赖图

```
P0 ──▶ P1 ──▶ P2 ──┬──▶ P4 ──▶ P5 ──▶ P6
       │           │
       └──▶ P3 ────┘
```

| 边 | 含义 | 硬性？ |
|---|---|---|
| P0 → P1 | 没有可用的构建与开关，P1 的验收无法进行 | **硬** |
| P1 → P2 | 没有 BASE 环境纹理（L-01），P2 的底栏采到的是纯色 | **硬** |
| P1 → P3 | `wedoPress` 的参数归入族表（P3），但 P1 的按压仍用现有值 → **可并行** | 软 |
| P2 → P4 | L2 依赖 Host 管线（P2 的产出） | **硬** |
| P3 → P5 | L-14/L-15/L-16 的开合动画用 `M-20` 单进度 | 软（可在 P5 内补） |
| P4 → P5 | 底栏的 L2 决定 L-03 顶栏的档位选择（因 `V-19`） | 软 |

**P2 与 P3 可并行**：两者改的文件不重叠（P2 在 `ui/glass`，P3 在 `ui/motion`）。但若并行，`Q-07`（底栏契约）与 `Q-15`（按压）需分别验证，不得合并出一个「都过了」的结论。

---

## 7.4 P0 · 让改动可被验证

**目标**：把仓库从「构建设备都能跑但验证链是假的」变成「每一步都能证伪」。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | 构建可跑、开关可用、回归网可信 |
| **涉及面** | — | `local.properties`、`proguard-rules.pro`、`ui/settings/`、`ui/screen/mine/AppearanceScreen.kt`、`values*/strings.xml`、`ui/component/CourseTableView.kt`、`androidTest/WedoUiSmokeTest.kt`、`NavigationSkeletonContractTest.kt`、`ui/screen/manage/` |
| **前置** | — | 无 |
| **任务** | — | 见下表 T-0.1 … T-0.7 |
| **依赖** | — | 无 |
| **产出** | — | 可构建仓库 + 3 个可用开关 + 5 个新契约测试 |
| **测试** | — | `testDebugUnitTest` 全绿、`lintDebug` 无 error、`assembleDebug` 成功、**一次 minify 构建 + 真机安装** |
| **验收** | — | `Q-01`（831 测试零回归，允许 1 条窄化）、`Q-02`（无新增死开关） |
| **失败处理** | — | 任一项失败则**不进 P1**。P0 是纯前置，没有「部分完成」的说法 |
| **回退** | — | 不需要（P0 不改任何视觉行为） |
| **进入下一阶段条件** | — | `Q-01` + `Q-02` 双绿，且 release 变体在真机装得上、打得开 |

### 任务清单

| ID | 任务 | 落点 | 备注 |
|---|---|---|---|
| **T-0.1** | 修 SDK 路径 | `local.properties:8` | 指向 `D:\AAAAA\ai\codex\.android-sdk`（不存在），改为 `ANDROID_HOME` 或删该行 |
| **T-0.2** | 修 proguard keep 规则 | `proguard-rules.pro:13-25` | 仍写 `com.lingion.sleepy.**`，改为 `com.wedo.schedule.**` 精确子集 |
| **T-0.3** | 三个死开关接线 | 新建 `ui/settings/WedoSettingsStore.kt`；`AppearanceScreen.kt:137-155` 追加「材质」分组 | 画质三选一 + 动效 Toggle + 触觉 Toggle；走统一 `update{}` 写入口 |
| **T-0.4** | 系统无障碍并入 | 新建 `util/SystemAccessibility.kt` | 读 `ANIMATOR_DURATION_SCALE` + `HIGH_TEXT_CONTRAST_ENABLED`，`ContentObserver` 监听（`M-36`..`M-38`） |
| **T-0.5** | 删死代码 | `CourseTableView.kt:617-1068`（`FullWeekView` 家族）+ `:1200`（`SectionHead`）+ `ui/screen/manage/` 空目录 | 约 450 行 + 1 空目录 |
| **T-0.6** | 修/删失效测试 | `androidTest/WedoUiSmokeTest.kt:68/76/79` | 断言 `testTag("wedo-dock")` 与「下一周」箭头，两者都已删 → 开启必挂 |
| **T-0.7** | 契约窄化 1 条 | `NavigationSkeletonContractTest:88` | `assertFalse("AnimatedVisibility")` → `assertFalse("WedoDock")` + 两条旧 Dock 符号断言；写入契约修改登记表 |
| **T-0.8** | **保存改造前基线截图集** | 新建 `evidence/baseline/` | 见 §7.4.1。**这条是 P1–P5 所有「与改造前对比」类验收的前置**，漏了它，卷 09 的 20+ 条 B 类全部做不了 |

### T-0.6 的两种处置与选择

| 方案 | 代价 | 选择 |
|---|---|---|
| 改为断言存在的 `testTag`（`week-grid-scroll-*`） | 需要重写测试逻辑，且 `wedoUiQa` 参数机制要保留 | **采纳** |
| 直接删除 | 失去唯一的仪器测试 | 不采纳——它是唯一能跑真机的入口 |

### 7.4.1 T-0.8：基线截图集（为什么它在 P0 而不在 P6）

卷 09 的 B 类里有 20 多条以「与改造前对比」为判据（`Q-06` / `Q-20` / `Q-65` / `Q-89` 等）。**改造前的界面一旦被改掉，就再也拍不到了。**

| 规则 | 内容 |
|---|---|
| **S-4** | 基线截图必须在 **P0**（任何视觉改动之前）保存，不得拖到 P1 |
| **S-5** | 覆盖范围：3 个 Tab × 明暗两套 × 关键二级页（≥ 12 张）；另加 4 个 FLOAT 面板各 1 张 | 
| **S-6** | 命名与卷 09 §9.5.2 一致，但前缀用 `baseline_` 而非 `Q-xx_` |

**为什么这是本计划的一个真实漏洞**：卷 07 的 P0 原有 7 个任务，全部是「让验证链可用」，但**没有一条要求保存视觉基线**——因为写 P0 时（第 5 轮）还没写卷 09（第 6 轮），不知道有 20 多条对比类用例。这是典型的「后写的卷对先写的卷提出新前置」。

→ 记为 `PATCH-06`，原因分类：**顺序依赖遗漏**（不是因为写错，是因为写 P0 时依赖还没产生）。

### T-0.7 的唯一性

这是整个计划里**唯一允许修改的既有测试**（`D9` / `ADR-22`）。

改法：

```kotlin
// 原
assertFalse("旧 Dock 的 AnimatedVisibility 容器应一并删除", src.contains("AnimatedVisibility"))
// 新
assertFalse("MainActivity 不得再调用 WedoDock", src.contains("WedoDock"))
assertFalse("不得再声明 dockVisible", src.contains("dockVisible"))
assertFalse("不得再引用 PILL_NAV_HEIGHT", src.contains("PILL_NAV_HEIGHT"))
```

安全意图不降：原断言防的是「Dock 复活」，新断言直接防「Dock」三个字出现，比防 `AnimatedVisibility` 更直接。

---

## 7.5 P1 · 令牌 + G0 静态材质

**目标**：全 app 表面获得统一的「厚度感」，**零采样、零 shader、零新窗口**。

这是 `ADR-19` 的核心：**P1 必须能独立交付**。若项目在 P1 后停止，界面也应该比改造前好（`SC-03`）。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | 12 个对象拿到 G0，BASE 有可采纹理，18 处底色去重 |
| **涉及面** | — | `ui/glass/`（4 个新文件）、`ui/theme/WedoGlassTokens.kt`、`WedoShell.kt`、18 处底色、L-05/L-06/L-08/L-09/L-10/L-11/L-13/L-21/L-22/L-23 |
| **前置** | — | P0 全部完成 |
| **任务** | — | T-1.1 … T-1.7 |
| **依赖** | — | P0 |
| **产出** | — | `GlassSpec` + `buildGlassSpec`（5 参）、`WedoEnvironmentLayer`、`wedoGlassSurface` 的 G0 分支、18 处底色透明化 |
| **测试** | — | `WedoGlassTokensTest` **扩展**（不改既有断言）、`Q-05`/`Q-06`/`Q-31`/`Q-64`/`Q-65` |
| **验收** | — | `Q-31`（22 组对比度全 ≥ 6.5）、`Q-05`（纹理存在且 ΔL ≤ 2%）、`Q-64`/`Q-65`（18 处底色，像素级一致） |
| **失败处理** | — | 若 `Q-31` 不过 → **立即停**，不得调整 `TARGET_TEXT_CONTRAST` 迁就（那是把标准降下来） |
| **回退** | — | `GLASS_ENABLED = false` → 全 app 回到今天的实色路径，零分支残留（`Q-06`） |
| **进入下一阶段条件** | — | `Q-31` + `Q-64` + `VR-02`（G0 盲比，5 人中 4 人能指出「新的更有厚度」） |

### 任务清单

| ID | 任务 | 关键点 |
|---|---|---|
| **T-1.1** | 扩 `buildGlassSpec` 签名 | 3 参 → 5 参（加 `surface` / `tier`）；**同步扩 `WedoGlassTokensTest` 的断言，不得改既有断言**（`ADR-16`） |
| **T-1.2** | 实现 `WedoEnvironmentLayer` | 明度场 + 确定性噪点（`V-01`..`V-04`）；`WedoBackground` 内部改调它，**签名不变** |
| **T-1.3** | `wedoGlassSurface` 的 G0 分支 | 只实现 G0（rim + 内高光 + hairline + 可选阴影）；G1/G2 留空返回 G0 |
| **T-1.4** | 18 处底色去重 | 逐页确认包裹关系（`R-BG-1`..`R-BG-6`）；**不得批量替换** |
| **T-1.5** | CONTENT 层铺开 | L-10 / L-11 / L-13 / L-21 / L-22 用 G0；L-08 / L-09 / L-23 **明确不接** |
| **T-1.6** | FLOAT 层 G0 | L-03（顶栏，G0 恒定）、L-02 的 L0 退化路径 |
| **T-1.7** | `wenoPress` 迁入 `ui/motion` | 从 `WedoShell.kt:67-82` 迁出，保留 `Modifier` 名称与签名 |

### T-1.1 的最大陷阱

`WedoGlassTokensTest` 现有 12+ 条断言锁住 Smooth/Balanced/Fine 的参数矩阵。

| 做法 | 后果 |
|---|---|
| ❌ 改既有断言的值 | 护栏被拆，后续任何参数漂移都不会被发现 |
| ✅ 新增断言（加 `surface` / `tier` 维度） | 护栏变宽，覆盖新维度 |

`ADR-16` 的原文：「保留，并随签名扩展补断言。**不得删除既有断言，只允许新增**。」

### T-1.4 的逐页确认

18 处底色不能批量替换。每处必须确认：

```
① 该页面是否在 MainActivity 的 WedoBackground 内？
   否 → 保留自己的底色（如 JwImportActivity）
   是 → 可以改透明
② 改透明后，页面内是否有「假设底色不透明」的绘制？
   如 blend 模式、alpha 计算 → 需要复核
```

**为什么不能批量**：`JwImportActivity` 是独立 Activity，它不在 `MainActivity` 的 `WedoBackground` 内（`R-BG-6`）。批量改会露出纯黑。

---

## 7.6 P2 · Host + L1 真模糊

**目标**：底栏能看见下方内容流过。这是全项目**唯一**「内容真在栏下滚动」的位置（卷 01 §1.2.2）。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | 捕获管线可用；底栏 G2 走 Bake；Sheet/Picker 走 Snapshot；Dialog 走 G0 + Veil |
| **涉及面** | — | `ui/glass/GlassCapture.kt`、`GlassBlur.android.kt`、`GlassSnapshot.kt`、`WedoTabBar.kt`、`WeekPicker.kt`、`WedoGlassHost` 挂载点 |
| **前置** | — | P1 完成（BASE 有纹理） |
| **任务** | — | T-2.1 … T-2.6 |
| **依赖** | — | P1 |
| **产出** | — | Host + Stage + Bake/LiveStrip/Hold 三模；底栏玻璃；4 处跨窗口面板的快照玻璃；15 处 Dialog 的 G0 |
| **测试** | — | `Q-07`/`Q-08`/`Q-50`/`Q-51`/`Q-52`/`Q-54`/`Q-61` |
| **验收** | — | `Q-07`（底栏三条契约全绿）、`Q-51`（全 G0 页面无捕获）、`Q-52`（滚动路径零 `toImageBitmap`） |
| **失败处理** | — | 若底栏掉帧（`Q-34` 的 P90 劣化 > 10%）→ 降 L1 为 G0，P2 部分回退 |
| **回退** | — | `GlassQuota.maxG2 = 0` → 全 app 回到 G0 |
| **进入下一阶段条件** | — | `Q-07` + `Q-51` + `VR-03`（首个 Bake 截图）+ `VR-05`（帧率对比不劣化 > 10%） |

### 任务清单

| ID | 任务 | 关键点 |
|---|---|---|
| **T-2.1** | `GlassCapture.kt`：Host + Stage | `drawWithContent { record; drawLayer }`；只在 `hasLiveConsumer` 时录（`O-03`） |
| **T-2.2** | 三模切换 | 静止 Bake / 滚动 LiveStrip / 开合 Hold（`O-10`..`O-13`，`O-50`..`O-52`） |
| **T-2.3** | `GlassBlur.android.kt` | `@RequiresApi(31)` 独立文件；返回 `(GraphicsLayerScope.() -> Unit)?`（A-16/A-17） |
| **T-2.4** | 底栏 G2 + 滚动降 L1 | **先写契约兼容方案**（不动 `translationY`），再写材质 |
| **T-2.5** | `GlassSnapshot` + 4 处预拍 | 长按/按下时预拍（`O-86`/`O-87`）；`takeLatest()` 取完即空 |
| **T-2.6** | `WeekPicker` 去 `Surface` | `WeekPicker.kt:83` 的 `Surface(fillMaxSize, color = background)` 必须先处理 |

### T-2.4 的顺序不能反

`WedoTabBar` 是被三条契约测试锁住的组件（`B-08`/`B-09`）。正确顺序：

```
① 先确认材质加进去后，`graphicsLayer { translationY = size.height * hideFraction }` 一字不动
② 再确认 `onPreScroll` 仍 `return Offset.Zero`
③ 最后才加玻璃
```

反过来的后果：先加玻璃、跑了发现测试红、然后**去改测试** —— 那是拆护栏的典型路径。

### T-2.5 的灰度

跨窗口快照是本阶段风险最高的部分（`S-3`）。灰度方式：

| 波次 | 范围 | 通过标准 |
|---|---|---|
| 第 1 波 | 只做 `L-16`（`WedoCourseActions`，63 行，最小） | `Q-60` 无跳变 |
| 第 2 波 | `L-14` + `L-15`（两个 Sheet） | `Q-54`/`Q-55` |
| 第 3 波 | `L-17`（全屏 Picker，面积最大） | `Q-61`/`Q-62` |

每波不过则停在该波，不进下一波。

---

## 7.7 P3 · 运动系统

**目标**：把散落的 5 处 `spring()` 收敛成族表；补减动效门控；`wedoPress` 加最小驻留。

可与 P2 并行（改的文件不重叠）。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | 弹簧族表落地；16 处动画入口全部门控；快速连点不抖 |
| **涉及面** | — | `ui/motion/`（5 个新文件）、`WedoShell.kt`、`WedoTabBar.kt`、`ScheduleScreen.kt`、`SegmentedSwitcher.kt`、`SettingsCards.kt`、`EditTableScreen.kt`、`ImportWizard.kt` |
| **前置** | — | P0（三个开关可用） |
| **任务** | — | T-3.1 … T-3.5 |
| **依赖** | — | P0（软：可与 P2 并行） |
| **产出** | — | `WedoSprings`（8 根）、`WedoMotionPolicy`、`WedoPress`（最小驻留）、`WedoProgress` |
| **测试** | — | `Q-12`/`Q-15`/`Q-16`/`Q-17`/`Q-41`/`Q-42`/`Q-44` |
| **验收** | — | `Q-41`（减动效逐处核对 16 处全过）、`Q-42`（三条弹簧硬规则）、`Q-17`（6 个消费方零回归） |
| **失败处理** | — | 若 `Q-17` 红（改 `wedoPress` 连带别处坏）→ 回退 `WedoPress` 到原实现，其余保留 |
| **回退** | — | 逐项改回原 `spring(...)`；族表文件保留（不删，供后续） |
| **进入下一阶段条件** | — | `Q-41` + `Q-42` + `Q-44` 三绿 |

### 任务清单

| ID | 任务 | 关键点 |
|---|---|---|
| **T-3.1** | `WedoSprings.kt` 族表 | 8 根（`M-01`..`M-08`）；**KDoc 必须写值与理由**（`M-66`） |
| **T-3.2** | `WedoMotionPolicy.kt` | 双源门控（`M-36`..`M-38`）；`resolve(spec)` 返回 `snap()` 或原 spec |
| **T-3.3** | `WedoPress.kt` 最小驻留 | `M-43`（80ms）+ `M-44`（终值恒 1.0） |
| **T-3.4** | 16 处入口逐一接门控 | 按卷 03 §3.7.4 的表，优先补 8 处「当前未门控」 |
| **T-3.5** | `WedoProgress.kt` 单进度 | `M-20`..`M-23`；先只用于 `L-10` 的折叠卡（最小验证） |

### T-3.4 的清单必须逐条勾

卷 03 §3.7.4 列了 16 处，其中 8 处当前无门控：

`SegmentedSwitcher:80`、`SettingsCards:75`、`:113`、`:134`、`EditTableScreen:246`、`ImportWizard:247`，加上 P2/P5 新增的 3 处。

| 规则 | 内容 |
|---|---|
| **A-18** | 每补一处，在 `WedoMotionCoverageTest` 的清单里勾掉一条；清单未清空则 P3 不通过 |

**为什么要有这个测试**：减动效漏一处，用户在系统里关了动画后仍会看到那一处弹——而漏的往往是「最不常用」的那一处，靠人工回归永远发现不了。

---

## 7.8 P4 · L2 折射灰度

**目标**：底栏边缘能弯折。**只在一个组件上灰度**。

这是全计划风险最高的阶段（`R-06` + `VR-17`）。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | L2 在一个组件上跑通；滚动中自动折叠为 L1 |
| **涉及面** | — | `ui/glass/GlassShader.android.kt`、`res/raw/liquid_glass.agsl`、`GlassUniforms.kt`、`WedoTabBar` |
| **前置** | — | P2 完成（Host 管线）+ **`VR-17`（AGSL 能否编译）必须先过** |
| **任务** | — | T-4.1 … T-4.4 |
| **依赖** | — | P2 |
| **产出** | — | AGSL shader + uniform 契约测试 + 底栏 L2 |
| **测试** | — | `Q-03`（`toImageBitmap` 文本契约）、`Q-04`（uniform 顺序）、`Q-52`、`Q-53` |
| **验收** | — | `Q-04` 绿 + `VR-12`（L1 vs L2 的差异**可见**） |
| **失败处理** | — | 三种失败：① AGSL 编译不过 → 删 shader 文件，L2 永久关闭；② 帧率崩 → 底栏回 L1；③ 看不出差别 → **L2 默认永久关闭，只留用户手动选项** |
| **回退** | — | 删 1 个文件（`GlassShader.android.kt`）+ 改 1 个枚举分支 |
| **进入下一阶段条件** | — | `Q-04` + `VR-12` 双过；否则 P5 直接跳过 L2 相关项 |

### 三种失败处理的具体判据

| 失败 | 判据 | 处置 |
|---|---|---|
| AGSL 编译不过 | 加载时抛异常，`GlassDebug` 记 `shaderLoadFailed` | 删文件，`O-58` 的永久降级生效 |
| 帧率崩 | `Q-84` 的 P90 劣化 > 15% | 底栏 `O-52` 折叠下不来 → 强制 L1 |
| 看不出差别 | `VR-12` 的盲比：5 人中 ≤ 2 人能分辨 | **L2 默认永久关闭**，保留手动开关（`U-12`） |

第三条是最可能的结果。FlClash 的研究实测（§E0.6）显示 L1/L2 的差异**只集中在边缘带**，静止时几乎不可辨。本项目的底栏比 FlClash 的 Dock 更薄，差异可能更小。

**这不是失败**：如果 `VR-12` 判「看不出差别」，那说明 L2 的成本（一个 shader 文件 + 一个 API 33 分支 + 每次改动的双端对齐）不值得——及时止损比硬撑正确。

---

## 7.9 P5 · 剩余对象 + 配额治理

**目标**：24 个对象全覆盖；配额有闸。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | 全部 L-ID 落地；`GlassQuota` 有运行时裁决 |
| **涉及面** | — | `ui/glass/GlassQuota.kt`、L-07 / L-12 / L-14 / L-15 / L-16 / L-24 |
| **前置** | — | P2（快照）+ P3（运动） |
| **任务** | — | T-5.1 … T-5.3 |
| **依赖** | — | P2、P3 |
| **产出** | — | 24 个对象全覆盖；配额闸门；`Q-83`/`Q-33`（死代码删除） |
| **测试** | — | `Q-24`（嵌套深度）、`Q-83`、`Q-33`、`Q-60`、`Q-71`/`Q-72` |
| **验收** | — | `Q-24`（深度 ≤ 2）+ `Q-72`（15 个对话框参数一致） |
| **失败处理** | — | 配额被触发过于频繁（日志可见）→ 复核分配表，而非放宽配额 |
| **回退** | — | `maxG2 = 0` |
| **进入下一阶段条件** | — | `Q-24` + `Q-72` + 24 个对象的 ⑩ 列全部有对应 Q-ID |

### 任务清单

| ID | 任务 | 关键点 |
|---|---|---|
| **T-5.1** | 配额闸门 | `G2 ≤ 3` / `G1 ≤ 2` / 包围盒 ≤ 15% 屏面积；超出按面积倒序降档（`O-15b`） |
| **T-5.2** | `WedoDialog` 统一 15 处 | 分批：先 `WedoPrivacyConsent` → 再 `AddCourseScreen` → 其余 |
| **T-5.3** | 跨窗口剩余两波 | `L-14`/`L-15`（第 2 波）、`L-17`（第 3 波） |

### T-5.2 的分批理由

`WedoPrivacyConsent` 是**独立文件 + 承重业务**，适合当第 1 个试验品：改坏了立刻知道；且它不在任何复杂布局里。

`AddCourseScreen` 是 1,845 行的巨型文件，放最后。

---

## 7.10 P6 · 全面验收与收尾

**目标**：卷 09 全表通过；`DESIGN_SPEC_V4_GLASS.md` 落地；`PROJECT_HANDOFF` 的权威指向改掉。

### 11 项

| # | 项 | 内容 |
|---|---|---|
| **目标** | — | 全部 Q-ID 通过；规范文档取代完成 |
| **涉及面** | — | `docs/`、`PROJECT_HANDOFF.md:154`、`DESIGN_SPEC_V2.md` 文件头 |
| **前置** | — | P1–P5 完成 |
| **任务** | — | T-6.1 … T-6.4 |
| **依赖** | — | 全部 |
| **产出** | — | 验收报告、V4 规范正文、权威指向更新、技术债清单 |
| **测试** | — | 卷 09 全部 `Q-ID` + `VR-01`..`VR-18` |
| **验收** | — | 卷 09 全绿；`VR-01`（三档 API 截图）齐备 |
| **失败处理** | — | 任一 Q 不过则不进发布；不得「记为已知问题」放行（除已在卷 10 登记的风险） |
| **回退** | — | 无（这是收尾阶段，回退等于不发） |
| **进入下一阶段条件** | — | 不存在（终点） |

### 任务清单

| ID | 任务 | 关键点 |
|---|---|---|
| **T-6.1** | 跑完卷 09 的 82 个 Q-ID | 每个都要有证据（截图/日志/测试输出） |
| **T-6.2** | `DESIGN_SPEC_V4_GLASS.md` 定稿 | 由 01–04 的结论提炼为条款正文（`G-001`..`G-0NN`） |
| **T-6.3** | 权威指向更新 | `PROJECT_HANDOFF.md:154` 的「唯一权威」改指 `docs/glass/00_MASTER.md`；`DESIGN_SPEC_V2.md` 文件头加一行取代声明 |
| **T-6.4** | 技术债清单 | `U-11`（`density` 死键）、`U-18`（`bar` 字段）、`R-08`（若迁 NavHost）等 |

### T-6.3 的必要性

当前 `PROJECT_HANDOFF.md:154` 声明 `DESIGN_SPEC_V2.md` 是「唯一权威」。本套文档要取代它的材质章节。

如果不同时改建指向，会出现**两个「唯一权威」并存**——这不是文档洁癖，是：未来有人按 V2 的「内容卡片禁止材质」来 review，会否定本计划的全部工作。

---

## 7.11 灰度与开关矩阵

四个开关，覆盖所有灰度需求：

| 开关 | 作用域 | 默认 | 谁能改 |
|---|---|---|---|
| `GLASS_ENABLED` | 全局总闸 | `true` | 构建期常量（`BuildConfig`） |
| `QualityTier` | 渲染档（Smooth/Balanced/Fine） | `Balanced` | 用户（`L-22` 的设置项） |
| `GlassQuota.maxG2` | 同屏动态采样数 | `3` | 构建期常量 |
| `WedoMotionPolicy.userEnabled` | 动效总闸 | `true` | 用户 |

| 规则 | 内容 |
|---|---|
| **A-19** | `GLASS_ENABLED = false` 时，全 app 走今天的实色路径，**零分支残留**（`Q-06` 像素级验证） |
| **A-20** | 任何阶段出问题，优先用 `GlassQuota.maxG2 = 0` 而不是关总闸 | 关总闸会连 G0 一起没了，那是「回到改造前」而不是「降级」 |

A-20 是一条实践教训的固化：**降级应该是「少一点质感」，不是「什么都没有」**。

---

## 7.12 反例清单

| # | 反例 | 违反 | 后果 |
|---|---|---|---|
| 1 | P0 没做完就进 P1 | 阶段硬门禁 | 验收无从判定，等于凭空宣称「过了」 |
| 2 | P1 时顺手把 L2 的 shader 也写了 | `S-3` | 高风险技术与低风险技术同批进主线 |
| 3 | P2 加玻璃导致底栏契约红，然后去改契约测试 | `D9` / `ADR-22` | 拆护栏 |
| 4 | P3 一次改完 16 处动画入口 | 无法定位回归来源 | 逐处改、逐处勾 |
| 5 | P4 直接全 app 上 L2 | `S-3` | 帧率崩且无法定位 |
| 6 | P4 判「看不出差别」但仍硬上 | 成本纪律 | 为一个看不见的差异维护两份 shader |
| 7 | 配额超限就放宽配额 | §7.9 失败处理 | 配额失去意义 |
| 8 | 用 `GLASS_ENABLED=false` 做「降级」 | `A-20` | 连 G0 一起没了 |
| 9 | P6 把不过的 Q 记为「已知问题」放行 | §7.10 失败处理 | 卷 09 变成装饰 |
| 10 | 阶段内改参数却不确定「这是设计起点还是实测值」 | `M-66` | 三个月后没人知道 0.55 是哪来的 |
| 11 | 先改代码再补文档 | 本套文档的定位 | 文档变成事后追认，不再是蓝图 |

---

## 7.13 五个工程动作与七阶段的映射

用户要求的顺序是「基础设施 → 代表性组件验证 → 核心组件推广 → 全局一致性优化 → 最终质量验收」。本计划的七阶段是对它的具体化，映射如下。

| 工程动作 | 落在哪些阶段 | 具体内容 |
|---|---|---|
| **基础设施** | P0 + P1（前半） | 构建可用、开关可用、`GlassSpec` 与 `WedoEnvironmentLayer` 落地 |
| **代表性组件验证** | P1（后半）+ P2（第 1 波） | L-10（设置卡，最小 CONTENT）、L-16（`WedoCourseActions`，63 行，最小 FLOAT） |
| **核心组件推广** | P2（第 2/3 波）+ P3 + P4 | L-02（底栏，唯一真滚动位置）、L-03（顶栏）、`wedoPress`、L2 |
| **全局一致性优化** | P5 | 24 个对象全覆盖、`WedoDialog` 统一 15 处、配额闸门 |
| **最终质量验收** | P6 | 卷 09 全表 + V4 规范 + 权威指向 |

### 7.13.1 为什么「代表性组件」选 L-10 与 L-16

代表性组件的作用是**用最小成本暴露最多问题**。选择标准三条：面积小、依赖少、失败可容忍。

| 候选 | 判定 | 理由 |
|---|---|---|
| **L-10 设置分组卡** | ✅ 选为 CONTENT 代表 | 14 行核心逻辑、同屏 ≤ 6、无子面板、改坏只影响设置页 |
| **L-16 `WedoCourseActions`** | ✅ 选为 FLOAT 代表 | 63 行（全项目最小）、单一职责、跨窗口（能验证 Snapshot 全链路） |
| L-11 课程块 | ❌ 不用 | 60+ 同屏，一旦性能问题会与「网格本身慢」混淆 |
| L-02 底栏 | ❌ 不用 | 被 3 条契约测试锁死，改坏代价最高 |
| L-17 `WeekPicker` | ❌ 不用 | 全屏，快照位图最大，先做会掩盖小面板的问题 |

**这一选择直接决定 P1 的成功判据**：如果 L-10 与 L-16 都做不对，后面 22 个对象全是重复的错误。

### 7.13.2 「全局一致性优化」为什么要单独一个阶段

P5 之前，24 个对象是分批做的，必然出现「同一档参数在不同对象上不一致」。P5 的职责不是「再做几个」，而是**把已有的对齐**。

具体要做的三件事：

| # | 内容 | 判据 |
|---|---|---|
| 1 | 所有 CONTENT 对象的 G0 参数来自同一个 `GlassSpec` | `Q-35`（冲突卡与课程块参数完全相同） |
| 2 | 15 处对话框的参数一致 | `Q-72` |
| 3 | 所有 FLOAT 的 rim / hairline 分档一致 | 人工复核（按 `V-20`/`V-29` 的尺寸分档） |

**反例**：把 P5 当成「剩下的组件补完」，会导致 24 个对象各有一套参数，然后「全局一致性」变成「24 个不一致」。

---

## 7.14 每阶段的可观测指标

阶段门禁靠 `Q-ID` 判定，但 `Q-ID` 是**结果**。执行过程中需要**过程指标**，否则要等到阶段末才知道偏了。

| 阶段 | 过程指标 | 观察方式 | 预警阈值 |
|---|---|---|---|
| **P0** | `testDebugUnitTest` 的测试数 | CI 输出 | **低于 830 即预警**（`R-03`：有人删了断言） |
| **P1** | `GlassSpec` 的字段数 vs `V-`/`O-` ID 数 | 人工对账 | 字段数 < 文档 ID 数的一半 → 说明参数没落地完 |
| **P1** | `wedoGlassSurface` 的调用点数 | `grep -c` | 与卷 05 的 24 个对象对账，差 > 3 需说明 |
| **P2** | `stage.hasLiveConsumer` 为真的帧占比 | `GlassDebug` 日志 | 静止页面也 100% → 录制没收住（`O-03` 失效） |
| **P2** | 单帧 `record` 次数 | `GlassDebug` 计数器 | 静止时 > 0 → Bake 失效 |
| **P3** | `Spring` 未走 `WedoMotionPolicy` 的调用点数 | `grep -c "spring("` | > 0 即未完成 |
| **P4** | `shaderLoadFailed` 是否出现 | `GlassDebug` | 出现一次即降级永久生效（`O-58`） |
| **P4** | 滚动中 `RenderTier` 是否为 L1 | `GlassDebug` | 滚动中仍报 L2 → `O-52` 未生效 |
| **P5** | 同屏 `G2` 实际数量 | `GlassDebug` | > 3 → 配额未生效 |
| **P5** | 嵌套深度最大值 | `GlassDebug` | > 2 → `O-14` 失效 |
| **P6** | 未通过的 `Q-ID` 数 | 卷 09 表格 | > 0 则不发 |

### 7.14.1 为什么需要 `GlassDebug`

上表 11 项里 6 项依赖运行时观测。`GlassDebug`（`O-68`）就是为此存在的最小设施：

```kotlin
internal object GlassDebug {
    var lastTier: RenderTier
    var recordCount: Int
    var hasLiveConsumer: Boolean
    var maxNestingDepth: Int
    var shaderLoadFailed: Boolean
    var quotaDowngrades: Int
}
```

| 规则 | 内容 |
|---|---|
| **A-33** | `GlassDebug` 只在 `BuildConfig.DEBUG` 下写入，release 下是空实现 | 避免长期运行的计数器带来开销 |
| **A-34** | 它的字段**不得**被业务代码读取 | 它是观测，不是状态源 |

**A-34 的意义**：一旦有业务代码依赖 `GlassDebug.lastTier`，它就从「观测」变成「逻辑」，然后 release 下它是空的 → 行为不一致。

### 7.14.2 阶段内的检查节奏

| 频率 | 检查 |
|---|---|
| 每完成一个任务 | 对应 `Q-ID` 单跑 |
| 每完成一个阶段的一半任务 | 跑全量 `testDebugUnitTest` + 过程指标 |
| 阶段结束 | `Q-ID` 全跑 + 过程指标 + 出口评审 |
| 阶段出口后 | 更新 `00_MASTER` 的进度表与自检结果 |

**「一半任务」这个节点是有意的**：等到阶段末才发现偏了，返工量最大；太频繁又会打断节奏。半程是最划算的检查点。

---

## 7.15 逐阶段文件级清单（实施时按此开清单）

上文的「涉及面」是模块级。真正开工时需要文件级。下表是每个阶段的**新增 / 修改 / 删除**清单，可直接当作 PR 的 checklist。

### P0

| 动作 | 文件 |
|---|---|
| 改 | `local.properties`（`T-0.1`）<br>`app/proguard-rules.pro`（`T-0.2`）<br>`app/src/main/java/com/wedo/schedule/ui/screen/mine/AppearanceScreen.kt`（`T-0.3`）<br>`app/src/main/res/values*/strings.xml`（6 个，`T-0.3`）<br>`app/src/test/java/com/wedo/schedule/NavigationSkeletonContractTest.kt`（`T-0.7`）<br>`app/src/androidTest/java/com/wedo/schedule/WedoUiSmokeTest.kt`（`T-0.6`） |
| 新增 | `ui/settings/WedoSettingsStore.kt`（`T-0.3`）<br>`util/SystemAccessibility.kt`（`T-0.4`）<br>`app/src/test/.../WedoSettingsStoreTest.kt`（`Q-02`）<br>`app/src/test/.../SpringAccessibilityTest.kt`（`M-36`..`M-38`） |
| 删 | `ui/component/CourseTableView.kt:617-1068`（`FullWeekView` 家族）<br>同文件 `:1200`（`SectionHead`）<br>`ui/screen/manage/`（空目录） |

### P1

| 动作 | 文件 |
|---|---|
| 新增 | `ui/glass/GlassCapability.kt`<br>`ui/glass/GlassSpec.kt`<br>`ui/glass/GlassEnvironment.kt`<br>`ui/glass/WedoGlassSurface.kt`（仅 G0 分支）<br>`ui/glass/GlassQuota.kt`<br>`app/src/test/.../GlassCapabilityTest.kt`<br>`app/src/test/.../GlassDependencyContractTest.kt`<br>`app/src/test/.../CourseTintOpacityContractTest.kt`（`Q-19b`）<br>`app/src/test/.../CourseContrastMatrixTest.kt`（`Q-31`，22 组） |
| 改 | `ui/theme/WedoGlassTokens.kt`（签名 3→5 参）<br>`ui/component/WedoShell.kt`（`WedoBackground` 内部换实现、`wedoPress` 迁出）<br>**18 处底色**（见卷 05 `L-18` ①，逐页确认）<br>`ui/component/SettingsCards.kt`（L-10）<br>`ui/component/WedoCourseCard.kt`（L-11）<br>`ui/component/ConflictCard.kt`（L-13）<br>`ui/screen/today/TodayScreen.kt`（L-21）<br>`ui/screen/schedule/WedoWeekHeader.kt`（L-03） |
| 删 | 无 |

### P2

| 动作 | 文件 |
|---|---|
| 新增 | `ui/glass/GlassCapture.kt`<br>`ui/glass/GlassBlur.android.kt`（`@RequiresApi(31)`）<br>`ui/glass/GlassSnapshot.kt`<br>`ui/glass/GlassDebug.kt`<br>`app/src/test/.../GlassSnapshotContractTest.kt`（`Q-56`）<br>`app/src/test/.../ToImageBitmapContractTest.kt`（`Q-03`/`Q-52`，文本契约） |
| 改 | `ui/component/WedoTabBar.kt`（L-02，**先写契约兼容**）<br>`ui/screen/schedule/WeekPicker.kt:83`（去 `Surface`）<br>`ui/screen/schedule/ScheduleScreen.kt`（Host 挂载点 + 长按预拍）<br>`MainActivity.kt`（Host 挂载，**不动 pushOverlay lambda**）<br>`ui/component/WedoShell.kt`（`WedoBackground` 加 Host？——**不加**，Host 在页面层） |
| 删 | 无 |

### P3

| 动作 | 文件 |
|---|---|
| 新增 | `ui/motion/WedoSprings.kt`<br>`ui/motion/WedoMotionPolicy.kt`<br>`ui/motion/WedoPress.kt`<br>`ui/motion/WedoProgress.kt`<br>`ui/motion/WedoMotion.kt`（迁入 `WedoThumbSpring`）<br>`ui/motion/WedoAnimated.kt`（`WedoAnimatedContent` / `WedoAnimatedVisibility`）<br>`app/src/test/.../WedoSpringsTest.kt`（`Q-42`）<br>`app/src/test/.../WedoMotionCoverageTest.kt`（`Q-41`） |
| 改 | `ui/component/SegmentedSwitcher.kt:80`<br>`ui/component/SettingsCards.kt:75/113/134`<br>`ui/screen/mine/EditTableScreen.kt:246`<br>`ui/screen/imports/ImportWizard.kt:241-251`<br>`ui/component/WedoTabBar.kt:137`（换 `M-06`）<br>`ui/screen/schedule/ScheduleScreen.kt:136`（换 `M-06`）<br>`ui/component/WedoShell.kt`（引用 `WedoPress` 而非本地实现） |
| 删 | 无（`WedoMotion.kt` 的内容迁走后保留为空壳或删除，二者皆可，但**必须留 KDoc 说明迁到哪**) |

### P4

| 动作 | 文件 |
|---|---|
| 新增 | `ui/glass/GlassShader.android.kt`（`@RequiresApi(33)`）<br>`app/src/main/res/raw/liquid_glass.agsl`<br>`ui/glass/GlassUniforms.kt`<br>`app/src/test/.../GlassUniformContractTest.kt`（`Q-04`） |
| 改 | `ui/glass/WedoGlassSurface.kt`（L2 分支）<br>`ui/glass/GlassCapture.kt`（面板表上传）<br>`app/proguard-rules.pro`（`tools:keep` for `res/raw`，`O-49`）<br>`ui/component/WedoTabBar.kt`（L2 开启） |
| 删 | 若 `VR-17` 失败：删 `GlassShader.android.kt` + `liquid_glass.agsl` + `GlassUniforms.kt` |

### P5

| 动作 | 文件 |
|---|---|
| 新增 | `ui/component/WedoDialog.kt`（`A-…` 统一 15 处）<br>`app/src/test/.../DialogUniformityTest.kt`（`Q-72`） |
| 改 | `ui/component/WedoDialog.kt` 的 15 个消费方：`ConflictCard.kt`、`DateTimePickers.kt`、`AddCourseScreen.kt`、`EditTableScreen.kt`、`ExportScreen.kt`、`HolidaySettingsScreen.kt`、`UpdateChangelogDialog.kt`、`WedoSettingsScreen.kt`、`WedoCourseActions.kt`、`WedoPrivacyConsent.kt`<br>`ui/component/CourseDetailSheet.kt`（L-14）<br>`ui/component/ShareScheduleSheet.kt`（L-15）<br>`ui/screen/schedule/WedoCourseActions.kt`（L-16）<br>`ui/screen/schedule/WeekPicker.kt`（L-17）<br>`ui/glass/GlassQuota.kt`（闸门） |
| 删 | 15 处里若出现自定义的「仿 AlertDialog」实现，一并删 |

### P6

| 动作 | 文件 |
|---|---|
| 新增 | `docs/glass/DESIGN_SPEC_V4_GLASS.md`<br>`docs/glass/09_qa_report.md`（验收报告，可选） |
| 改 | `docs/PROJECT_HANDOFF.md:154`（权威指向）<br>`docs/DESIGN_SPEC_V2.md` 文件头（一行取代声明）<br>`docs/glass/00_MASTER.md`（全部卷冻结）<br>`README.md`（若需提及材质） |
| 删 | 无 |

### 7.15.1 文件清单的用途

| 用途 | 说明 |
|---|---|
| PR checklist | 每个阶段的 PR 描述直接贴对应表 |
| 冲突预判 | 两个 Agent 并行时，看清单是否重叠 |
| 回退边界 | 回退某阶段时，按表逆序还原 |
| 交付审计 | P6 时对照本表确认「说要做的是否都做了」 |

**一个统计**：全部 7 个阶段合计 **新增 32 个文件、修改约 45 个、删除 3 处**。

其中「新增 32」包含主代码 19 个（`ui/glass` 11 + `ui/motion` 5 + 其他 3）+ 测试 12 个 + 文档 1 个（V4 规范）。与卷 06 §6.2 的「新增约 10% 代码量」估算一致。

---

## 7.16 本卷验收

| 检查项 | 判定 |
|---|---|
| 7 个阶段，每阶段 11 项齐备 | ✅ §7.4–§7.10 |
| 每阶段门禁指向 ≥1 个 `Q-ID` | ✅（`C-05`） |
| 每阶段有失败处理与回退 | ✅ |
| 高风险技术分散在三个阶段（P2 快照 / P4 折射 / P5 配额） | ✅ `S-3` |
| P1 可独立交付 | ✅ `ADR-19` / `SC-03` |
| 灰度矩阵 4 个开关覆盖全部降级需求 | ✅ §7.11 |
| 反例 11 条 | ✅ |
| 阶段依赖图含「硬/软」区分 | ✅ §7.3 |

**遗留问题**：

1. **P4 的「三种失败处理」中，第三种（看不出差别）最可能发生**，而它意味着一个 shader 文件的成本白付。→ 建议把 `VR-12` 的判据前移到 P4 的**第二天**（先只做一个静态测试图，不做产品集成），避免整阶段白做。
2. **P2 与 P3 并行**需要两个独立的验证通道，否则会出现「都过了」的合并结论掩盖单方失败。→ 并行时 `Q-07` 与 `Q-15` 必须分别出证据。
3. **本卷未给时间估算**。原因是：本项目无真机，很多步骤的耗时取决于外部（用户何时回传截图）。给一个假的时间表比不给更糟。
4. **P0 的 `T-0.3`（三个开关）依赖 6 个 locale 的文案**（`U-04`）。若 locale 补齐被推迟，P0 会卡住——建议先只补 `zh-rCN` + `values`（默认），其余 4 个按 `U-04` 另开任务，并让 `StringsKeyParityTest` 的断言在本阶段暂时只查 2 套。
