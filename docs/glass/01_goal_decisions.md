# 01 · 改造目标与设计决策

> 本卷定义**做什么、不做什么、什么算成功**。它不写参数值（归 02/03/04），不写组件方案（归 05），不写阶段（归 07）。
> 上游：`00_MASTER` §0.3 事实基线、§0.4 决策表、§0.8 取代矩阵
> 下游：02/03/04 的所有参数选择，都必须能追溯回本卷的 G-/B-/SC-ID

---

## 1.1 为什么「全面玻璃化」四个字必须先被拆解

「全面玻璃化」如果按字面执行，会得到一个必然失败的结果。原因不在审美，在物理：

**原因一：API 26–30 没有模糊。** `BlurEffect` 需要 API 31+（`AndroidRenderEffect.android.kt` 内 `isSupported = SDK_INT >= S`），`Modifier.blur` 在低于 31 时被静默忽略。项目 `minSdk = 26`（`app/build.gradle.kts:14`）。如果「全面」= 所有表面都上真模糊，那么 26–30 的设备上，整个界面会变成**满屏半透明色块 + 零模糊**——比现在的纯色实底更糊、更难读、更像半成品。这不是「降级」，这是「劣化」。

**原因二：内容层没有东西可采。** 当前全 app 的背景是两块纯色：`#F2F2F7`（浅）/ `#000000`（深）（`WedoShell.kt:56-59`）。玻璃的价值来自「采到有信息量的背景」。给一块纯色背景加玻璃，光学上等价于「给纯色加一点噪声再调亮度」——成本全付，收益为零。

**原因三：面积即成本。** 课表网格一屏可能有 60+ 课程块（`CourseTableView.kt:143` 注释原文：「一屏可能有 60+ 块，保持绘制廉价」）。若每块都做背景捕获，合成成本与面积成正比，结果是滚动直接掉帧。

**结论**：所谓「全面玻璃化」，可执行的含义只能是——**所有表面都纳入统一的材质体系并分级定额，而不是所有表面都使用同一种采样玻璃**。这个定义是本卷最核心的产出，见 §1.3。

---

## 1.2 现状基线（改造前到底是什么样）

写任何目标之前必须先说清起点。起点不是「一个朴素的实色界面」，而是「一个**已经删过一次玻璃**的界面」。

### 1.2.1 渲染能力基线

| 项 | 值 | 影响 |
|---|---|---|
| Compose | UI 1.7.4 / Material3 1.3.0（BOM 2024.10.00） | 具备 `rememberGraphicsLayer()`、`GraphicsLayer.record/drawLayer/toImageBitmap()`、`Modifier.graphicsLayer{renderEffect}`、`ShaderBrush` |
| `RuntimeShader` | 平台类，**API 33+**；Compose 无包装类 | L2 必须直接用 `android.graphics.RuntimeShader` + `BitmapShader` |
| `RenderEffect` / `BlurEffect` | **API 31+**，低于 31 静默 no-op | L1 的硬门槛 |
| Compose backdrop | **不存在** | 必须自己搭建「父容器录制 → 重绘」链路 |
| 现有渲染类玻璃代码 | **0 行** | `RuntimeShader`/`ShaderBrush`/`RenderEffect`/`BackdropFilter`/`Modifier.blur`/`drawWithCache` 全部零命中 |
| 现存玻璃资产 | `ui/theme/WedoGlassTokens.kt`（202 行），**无生产消费方** | 令牌活、渲染死。`WedoGlassTokensTest.kt` 已锁住 Smooth/Balanced/Fine 参数矩阵 |
| shader 资源 | `assets/`、`res/raw/` **均不存在** | AGSL 需从零建目录，仓库无任何既有约定 |

**能力三段（由 minSdk 26 强制产生）**：

| 区间 | 真模糊 | AGSL | 能做到的玻璃 |
|---|---|---|---|
| **26–30** | ✘ | ✘ | 半透明填充 + 渐变 + 描边 + 内高光 + 投影（即 G0） |
| **31–32** | ✔ | ✘ | G0 + 真实背景 blur sigma（即 L1） |
| **33+** | ✔ | ✔ | L1 + 折射位移与色散（即 L2） |

### 1.2.2 视觉体系基线

| 项 | 现状 | 证据 |
|---|---|---|
| 配色 | `WakeUpColorScheme` + `appleScheme()`。`LightScheme/DarkScheme` 运行时不参与 | `Theme.kt:39`、`AppleScheme.kt:86-149` |
| 背景 | 纯色 `#F2F2F7` / `#000000`，无渐变、无光斑、无图 | `WedoShell.kt:56-59` |
| 阴影 | **全库仅 1 处** `.shadow(` | `SettingsCards.kt:93`（开关滑块） |
| 透明度 | 仅 4 档：`WedoTheme.Alpha` = highContent .8 / hairline .3 / tinted .12 / inactive .6，65 处引用 | `Theme.kt:325-337` |
| 圆角 | `WedoShapes` 4/8/12/16/28 + `WedoAppleShapes.continuous()` 的 **×1.2 squircle 近似** | `Theme.kt:280`、`:644` |
| 强调色 | 三角色分离：`accent`（背景/选中底）、`accentIcon`（图标，门槛 3.0）、`accentText`（文字，门槛 4.5） | `WedoAppleTokens.kt:301/304/750/762/777` |
| 课程块 | `courseName` 哈希取 11 色之一 → 55% 淡底 + `solveForContrast` 反解深字（门槛 6.5） | `WedoCourseCard.kt:41`、`WedoAppleTokens.kt:171/204-237` |
| 层级结构 | `WedoBackground { Box{ MainTabs } + WedoTabBar }`，底栏与内容**同级叠加** | `MainActivity.kt:451-476` |
| 二级页底色 | 12 个页面各自 `Scaffold(containerColor = colors.background)`，把不透明底色**又涂一遍** | 各 `*Screen.kt` |

**这一条是关键的结构性事实**：全 app 唯一「内容真的在栏下滚动」的位置，只有底部 `WedoTabBar`。其余所有二级页都用 `Scaffold` 把背景重涂了一層，导致任何落在那里的玻璃都只能采到纯色。

### 1.2.3 交互反馈基线

| 项 | 现状 | 证据 |
|---|---|---|
| 动画总量 | 3 处 `spring()` + 1 处 `tween()` + 5 个 `animate*AsState` + 1 个 `Animatable` + 2 个 `AnimatedVisibility` + 1 个 `AnimatedContent` | 全仓统计 |
| 弹簧封装 | **不存在**。`WedoMotion.kt:11-14` 只有一个常量，且是 `DampingRatioNoBouncy`（ζ=1.0，零回弹） | `WedoMotion.kt` |
| 零使用 API | `updateTransition` / `keyframes` / `animateContentSize` / `Crossfade` / `rememberInfiniteTransition` | 全仓零命中 |
| 唯一「手搓物理」范式 | `SegmentedSwitcher`：`Animatable` + `graphicsLayer` 补亚像素 + 自定义 Layout 在 measure 阶段读值重排 + 字符矩形∩滑块矩形覆盖率 lerp 上色 | `SegmentedSwitcher.kt:77-123, 166-239` |
| 动效令牌 | `WedoAppleMotion`：`enter=350/exit=250/quick=150`、`springEasing=cubic(.34,1.4,.64,1)` | `WedoAppleTokens.kt:660-675`，**生产零引用，只被单测断言** |
| 触觉 | 仅 2 处：换周落定、长按课程块 | `ScheduleScreen.kt:147-151`、`:184` |
| 减动效 | `WedoDisplay.motion` 存在但**恒为 true**——`WedoPreferences.write()` 生产零调用 | `WedoDesign.kt:13`、`WedoDesign.kt:31` |
| 页面转场 | `OverlayScreen` 早退 if 链，覆盖页与被覆盖页不同时存在，**零转场** | `MainActivity.kt:376-444` |
| 主题切换 | 同帧硬切 | `Theme.kt:396-480` |

**这一条是改造的最大落差**：规范 `DESIGN_SPEC_V2.md:196` 承诺「全部受减弱动效总开关约束」，`strings.xml` 里连一个相关 key 都没有。所以本计划把「三个死开关」列为 P0 前置（D6），不是顺手加功能，是因为**没有减动效开关，动效类验收无法成立**。

### 1.2.4 既有债务（会绊住改造的四项）

| # | 债务 | 证据 | 处置 |
|---|---|---|---|
| 1 | `FullWeekView` 家族零调用死代码，约 450 行 | `CourseTableView.kt:617-1068` | P0 删除 |
| 2 | 课程色种子不一致（`courseName` vs `groupId`） | `WedoCourseCard.kt:41` vs `CourseColorUtil.kt:169` | U-05 |
| 3 | `local.properties` 指向不存在的 SDK | `local.properties:8` | P0 修 |
| 4 | proguard keep 规则仍是旧包名 `com.lingion.sleepy.**` | `proguard-rules.pro:13-25` | P0 修 |

---

## 1.3 「全面玻璃化」的可执行定义

### 1.3.1 三轴模型

三个轴**必须分开**，因为它们回答三个不同问题。合并成一个枚举会产生 `ContentGlass_L1` 这类无意义组合，也无法表达「内容层永不上采样」这条硬规则（ADR-15）。

```
轴一  SurfaceClass —— 语义：「这块面是什么角色？」→ 决定要不要采样
        BASE      环境层：不渲染玻璃，只提供可被采样的明度场
        CONTENT   内容层：页面容器 / 卡片 / 课程块 / 列表行 / 表单组 → 恒 G0
        FLOAT     浮层：底栏 / 顶栏 / Sheet / Dialog / Picker / 浮动按钮 → 可 G1/G2

轴二  QuotaGrade —— 手段：「用什么方式做材质？」→ 决定成本
        G0   材质化实色：不采样。tint + rim + 内高光 + hairline + 轻阴影
        G1   静态采样：Bake，面板打开时录一次，移动期 Hold 不重拍
        G2   动态采样：LiveStrip，每帧重录，仅限包围盒 ≤ 屏面积 15%

轴三  RenderTier —— 能力：「这台设备能做到什么？」→ 决定视觉上限
        L0 静态（26+）  L1 真模糊（31+）  L2 AGSL 折射（33+）
```

三轴正交：`SurfaceClass` 由**组件职责**决定（写死在组件里），`QuotaGrade` 由**配额与阶段**决定（可运行时调整），`RenderTier` 由**设备与用户偏好**决定（只读）。

### 1.3.2 唯一允许的分配表

**这张表是硬约束。任何组件不得偏离。** 卷 04 定义配方，卷 05 只引用。

| SurfaceClass | 默认档 | L0（26–30） | L1（31–32） | L2（33+） |
|---|---|---|---|---|
| BASE | 不适用 | 纯色 + 极低幅中性纹理 | 同 L0 | 同 L0（环境层不进 shader） |
| CONTENT | **G0 恒定** | G0 全配方 | G0（**不升档**） | G0（**不升档**） |
| FLOAT·大面（Sheet / Picker / Dialog） | G1 | 退化为 G0 + 加强 rim | Bake + blur sigma | Bake + blur sigma + 可选轻折射 |
| FLOAT·条带（底栏 / 顶栏 / 分段 thumb） | G2 | 退化为 G0 + 加强 rim | LiveStrip + blur sigma | LiveStrip + blur sigma + 折射 + 色散 |

**为什么 CONTENT 恒 G0**（ADR-18）：
1. 面积最大、重绘最频繁，升档即性能自杀。
2. 内容层的底色是不透明的（D7 保住 6.5:1 推导前提），采样它本身无信息量。
3. 「内容层也用玻璃」这个诉求的真实意图是「内容层不要显得扁平」——G0 的 rim + 内高光 + 轻阴影恰好解决，不需要采样。

### 1.3.3 配额硬约束

| 约束 | 值 | 违反后果 |
|---|---|---|
| 同屏 G2 数量 | ≤ 3 | 每增 1 个即多一份全宽录制 |
| 同屏 G1 数量 | ≤ 2 | 多份 Bake 图集占离屏内存 |
| 单个 G2 的 record 包围盒 | ≤ 屏面积 15% | 超出即退 L1 |
| 嵌套 Host 深度 | ≤ 2（页面级 / 浮层级） | 超出即玻璃采样玻璃，双倍发灰 |
| CONTENT 被升档 | **禁止** | 架构缺陷，不是性能问题 |
| 全局总闸 | `GLASS_ENABLED: Boolean` | false 时全走今天实色路径，零分支残留 |

滚动中 G2 自动折叠为 L1（禁折射），落定 120ms 后恢复——这条不是优化，是必需项：滚动 + 折射 = 每帧抓位图 + suspend 分配，必崩。

### 1.3.4 什么算「违反本定义」

给实施 Agent 的反例清单。**出现以下任一现象，实现就是错的**，不需要争论审美：

| 反例 | 为什么错 |
|---|---|
| 课程卡底色变成半透明 | 破坏 `solveForContrast` 的恒定背景前提，6.5:1 失效（Q-31 会红） |
| 列表行加 `blur` | CONTENT 层被升档，违反 ADR-18 |
| 底栏每帧 `toImageBitmap()` | suspend + 位图分配进交互路径，帧率必崩（Q-52 会红） |
| 玻璃面板里再放一块玻璃 | 嵌套深度 3，双倍采样导致灰化（Q-24 会红） |
| API 30 上尝试 `RenderEffect` | 类不存在，运行时不崩但静默无效果 → 必须走 `GlassCapability` 判定，不能 try/catch |
| 用 `Surface(color = ...)` 包住内容来实现玻璃 | 改的是布局与绘制路径，会撞 `ScheduleHeaderLayoutContractTest` 的 padding 断言（ADR-21） |
| 新组件直接读 `WedoGlassTokens` 常量 | 绕过了 `buildGlassSpec` 的配额与降级仲裁，是「第二套实现」 |
| 把 accent 用作材质着色 | 违反 V2-09 / V4 保留条款；强调色只属于「可交互且选中」 |

---

## 1.4 设计语言定位：Apple 影响、但不能是任何人的复刻

本项目有两条**必须同时满足**的约束，它们的交集很窄：

1. **GPL-3.0 合规 + 视觉去关联**：wedo 是上游 sleepy 的 GPL 复刻/重写版本。`docs/LICENSE_REVIEW.md` 与 `docs/PROJECT_HANDOFF.md` 都要求完全自主的 UI/页面设计，避免被识别为 sleepy 衍生。
2. **不许成为 FlClash 的复刻**：`Research/FlClash 液态玻璃研究（本地参考材料，不入库）/` 是参考资料，其参数（`refraction -4.3`、`dispersion 6.5`、`bevel 5.5`）是 Flutter/Impeller 环境下的拟合值，直接搬到 Compose 不仅技术不可行，视觉上会得到「另一个 FlClash」。

### 1.4.1 继承什么

| 继承项 | 来源 | 理由 |
|---|---|---|
| 「材质只属于浮层」的层级直觉 | Apple HIG / V2 §2.1 | 这是玻璃不干扰阅读的根本原因，与 G0/G1/G2 分级同构 |
| rim + hairline 构成的「厚度感」 | FlClash 研究 §B.2/B.7 | 玻璃的「实感」来自边缘 1dp 内的加光与最外圈 0.67dp 的压暗，不是来自模糊 |
| 弹簧分三根管三件事 | FlClash 研究 §4 第一层 | 位置/幅度/形变共用一个参数是最常见错误 |
| 「先捕获架构、后 shader 本体」的顺序 | FlClash 研究 §8 | 顺序反了性能先崩 |
| 降级路径必须常驻 | `PROJECT_HANDOFF.md:259` | 原文要求「保留平衡/流畅的可读降级路径」 |

### 1.4.2 必须区别什么

| 不能像 | 具体做法 |
|---|---|
| sleepy（上游） | 不使用其任何图标、配色、页面结构；玻璃的**参数来源自建**，不引用上游价值 |
| FlClash | 不复刻其 Dock 透镜（本项目无 Dock，`WedoDock` 已删）、不复刻图集架构、不复刻 goo 流体融合（本项目无相邻胶囊群） |
| 「通用 iOS 主题包」 | 品牌锚点必须来自 wedo 自己的业务：课程色、6.5:1 对比度纪律、强调色三角色分离 |

### 1.4.3 品牌辨识度锚点（三条，任何改造不得削弱）

| 锚点 | 说明 | 为什么是锚点 |
|---|---|---|
| **A1 课程色即身份** | 11 色系统，55% 淡底 + 反解深字，`TARGET_TEXT_CONTRAST = 6.5` | 这是 wedo 与「通用日程 App」唯一的语义区分：颜色编码的是「哪门课」，不是装饰 |
| **A2 强调色三角色分离** | `accent` / `accentIcon` / `accentText`，门槛 3.0 / 4.5 各自独立 | 大多数 App 只有一个 accent，导致图标看不清或文字发灰。三角色分离是本项目少见的工程优点 |
| **A3 中性骨架 + 高对比文字** | 背景 `#F2F2F7`/`#000`，界面 95% 面积是中性灰阶 | 课程表是信息密集型工具，任何彩色背景都会与课程色打架 |

**玻璃改造对三条锚点的影响判定**：

- A1：**不得影响**。CONTENT 恒 G0，课程底色不透明度不变（D7）。
- A2：**可能被削弱**。玻璃面上的图标若使用 accentIcon，会与 rim 高光叠加导致对比度下降。→ 强制规则：**玻璃面上的图标不得用 accentIcon**，改用 `onSurface`，由 `accent` 只标记选中态。
- A3：**被增强**。BASE 层加极低幅中性纹理后，玻璃才有可采内容，中性骨架反而更成立。

---

## 1.5 目标（G-01 … G-08，逐条可判定）

| ID | 目标 | 判定方式 |
|---|---|---|
| **G-01** | 全 app 表面纳入统一材质体系，无「裸 Surface」 | 扫描 `Surface(` / `.background(` 调用，每一处都能对应到 `SurfaceClass` 与 `QuotaGrade` |
| **G-02** | 三个渲染档在对应 API 区间均可正常显示，无白屏、无静默无效果 | 26/31/33 三段各出一份真机或模拟器截图（VR-01） |
| **G-03** | 浮层的「厚度感」在 L0 档即可辨识 | 盲比测试：L0 截图与改造前截图，5 人中有 4 人能指出「新的更有厚度」（VR-02） |
| **G-04** | 减弱动效开关真实生效，覆盖全部 16 处动画入口 | 开关关闭后逐处核对，无一处仍有弹动（Q-41） |
| **G-05** | 课程块文字对比度在全部 11 色 × 明暗两主题下 ≥ 6.5:1 | `solveForContrast` 全量重算，输出 22 组数值表（Q-31） |
| **G-06** | 滚动场景帧率不劣化 | `dumpsys gfxinfo framestats` 对比改造前后 P90，劣化 ≤ 10%（P-01，VR-05） |
| **G-07** | 831 个既有单测零回归（允许 1 条窄化） | `testDebugUnitTest` 全绿，测试数 ≥ 830（Q-01） |
| **G-08** | 无新增「死开关」 | 每个新增设置项都有生产消费方，且被单测断言（Q-02） |

---

## 1.6 非目标（NG-01 … NG-10）

非目标的作用是防 scope creep。**每一条都是「看起来该做、本计划明确不做」**。

| ID | 非目标 | 为什么不做 |
|---|---|---|
| **NG-01** | 不改造桌面小组件（`widget/`） | RemoteViews 无法承载 Compose 材质；`RemoteViewsWidgetHelper.kt:38/45` 的 `TIRAMISU`/`S` 分支只能做简单模糊。L-24 作为反例登记 |
| **NG-02** | 不改造启动画面 | `core-splashscreen` 的生命周期极短，材质的感知收益接近零 |
| **NG-03** | 不引入 Navigation-Compose | ADR-24。现有 `OverlayScreen` push/pop 已满足「返回键逐层只退一级」 |
| **NG-04** | 不升级 Compose / AGP / Kotlin | ADR-25。1.7.4 已够；升版会把 Material3 1.3.0 拖进未知版本 |
| **NG-05** | 不做 goo 流体融合（相邻胶囊 smin 合并） | 本项目无「相邻胶囊群」这一场景（唯一相近的是 `SegmentedSwitcher`，但它的段是刚性等分的）。做它需要 CPU 轮廓射线步进，成本不抵收益 |
| **NG-06** | 不做课程块拖拽换课 | 需从零建手势 + 冲突检测回写，属交互改造不属材质改造 |
| **NG-07** | 不做真环境反射（天空盒 / 环境图） | 无视向量、无姿态输入；FlClash 研究 §P3-1 已把它列为「过门槛才做」的探索项 |
| **NG-08** | 不做动态壁纸 / 沉浸式全屏 | 与「信息密集型工具」定位冲突 |
| **NG-09** | 不接 `density` 死键 | 全仓零消费点。接了也是假开关（U-11） |
| **NG-10** | 不改教务导入链路的任何行为 | 白名单、SSL `handler.cancel()`、不回显响应片段三条安全红线，与本改造无交集，一律不碰 |

---

## 1.7 设计边界（B-01 … B-12，硬约束）

**边界与目标的区别**：目标是「要做到什么」（可权衡优先级），边界是「不许越过什么」（零容忍）。违反任一条即为缺陷，不是取舍。

| ID | 边界 | 检测方式 |
|---|---|---|
| **B-01** | CONTENT 层的底色不透明度不得降低 | 扫 `WedoCourseCard` / 列表行的 `color.copy(alpha=)` |
| **B-02** | 玻璃面板内的文字与图标，对比度按**最差背景**（纯白与纯黑两极）验算 | Q-32：把玻璃面板盖在纯白与纯黑上各截一次，取样文字区域 |
| **B-03** | `onReceivedSslError` 必须 `handler.cancel()`；域名白名单不得扩为通配；解析失败只回显错误码 | `WedoWebViewSecurityContractTest` 与 `JwWhitelistContractTest` 保持全绿 |
| **B-04** | Room 不得使用 `fallbackToDestructiveMigration` | 契约测试保持全绿 |
| **B-05** | 不提交真实学号 / Cookie / HAR / 课表 / 密钥 | `scripts/scan-secrets.ps1` 通过 |
| **B-06** | 材质一律画在 `drawBehind` / `drawWithContent`，不改布局 padding | `ScheduleHeaderLayoutContractTest:126-135` 的 `start = 16.dp` / `end = 16.dp` 断言保持全绿 |
| **B-07** | `MainActivity.kt` 不得出现 `AnimatedVisibility` / `.nestedScroll(` / `NestedScrollConnection` / `scrollConnection`（唯一例外见 ADR-22） | `NavigationSkeletonContractTest:84-102` |
| **B-08** | `TabBarVisibilityState.onPreScroll` 必须 `return Offset.Zero` | `TabBarHideContractTest:64-70` |
| **B-09** | 底栏隐藏必须用 `graphicsLayer{ translationY = size.height * hideFraction }` | `TabBarHideContractTest:110-116` |
| **B-10** | `@RequiresApi(31)` / `@RequiresApi(33)` 代码必须在独立文件，不得被 minSdk 26 的类静态引用 | 类校验阶段就会失败，故为编译期边界 |
| **B-11** | `toImageBitmap()` 只允许出现在 Bake 失败兜底、golden 抓帧、分享截图三处 | 文本契约测试（Q-03） |
| **B-12** | 强调色不得用于任何材质着色（tint / rim / 环境纹理） | 扫材质实现中的 `accent` 引用 |

---

## 1.8 成功标准（SC-01 … SC-08，可判定）

目标说方向，成功标准说**判定**。每条都必须能回答「怎么知道做到了」。

| ID | 成功标准 | 判定方法 | 证据形式 |
|---|---|---|---|
| **SC-01** | 三层结构在代码里可被机械识别 | 全仓扫描：每个视觉表面要么是 `WedoGlassSurface(SurfaceClass.X)`，要么在显式白名单内 | 扫描脚本输出 + 白名单文件 |
| **SC-02** | 三段 API 各有可辨认的视觉差异，且低档不劣于改造前 | 26/31/33 三份同场景截图并排 | 三张截图（VR-01） |
| **SC-03** | G0 单独交付即有净收益 | 只上 G0、不上 L1/L2 的构建与改造前截图盲比 | 5 人盲比记录（VR-02） |
| **SC-04** | 减动效开关关闭后，全 app 无弹动 | 逐个交互点录制 3 秒并人工核对 | 交互点清单 + 勾选表（Q-41） |
| **SC-05** | 22 组课程色对比度全部 ≥ 6.5:1 | 单测输出数值表 | `CourseContrastMatrixTest` 报告 |
| **SC-06** | 滚动帧率劣化 ≤ 10% | `dumpsys gfxinfo framestats` 的 P90 对比 | 两份 framestats 摘要（VR-05） |
| **SC-07** | 831 个单测零回归 | `testDebugUnitTest` 输出 | 测试报告 |
| **SC-08** | 无双实现：不存在「第二套材质参数」 | 扫 `rim` / `bevel` / `blurSigma` 的字面量出现位置，只允许在 `WedoGlassTokens.kt` 与 `GlassSpec.kt` | 扫描结果 |

---

## 1.9 优先级与「必修 / 可选 / 不值得」

### 1.9.1 三级分类

**必修**（不做则整个改造不成立）：

| 项 | 归属阶段 | 理由 |
|---|---|---|
| 修 SDK 路径、proguard 包名 | P0 | 不修则任何构建与验证都是假绿 |
| 三个死开关接线 + 6 locale 文案 | P0 | 没有开关，动效验收无从谈起 |
| P0 前置（死代码、失效测试、契约窄化） | P0 | 保证回归网可信 |
| `GlassCapability` + `QuotaGrade` 判定（纯函数） | P1 | 所有渲染分支的唯一入口 |
| G0 全配方 + CONTENT 层铺开 | P1 | ADR-19：唯一可独立交付的净收益 |
| BASE 层极低幅中性纹理 | P1 | 没有它，L1/L2 全无收益（D8） |
| 12 个 `Scaffold(containerColor)` 去重 | P1 | 不去重则浮层玻璃只能采到纯色 |
| `WedoGlassHost` + Stage record 管线 | P2 | L1/L2 的地基 |
| 底栏 G2 | P2 | 唯一「内容真在栏下滚动」的位置，收益最直观 |
| 弹簧族 + `WedoMotionValue` + 减动效门控 | P3 | 「Q 弹」的唯一正解路径 |

**可选**（有价值但不阻塞整体）：

| 项 | 归属阶段 | 取舍条件 |
|---|---|---|
| WeekPicker 全屏 Dialog 玻璃化 | P4 | 需先去掉 `Surface(background)`，否则采到纯色 |
| Tab 切换转场 | P3 | 依赖契约窄化（ADR-22）；不做也可交付 |
| 主题切换过渡 | P5 | 需改 `WedoBackground` + 全量 colors 读取路径，收益中等 |
| 顶栏玻璃化 | P2 | 顶栏当前零材质（`WedoWeekHeader.kt` 117 行），改造成本低于底栏，但滚离变色需滚动量（U-07） |

**不值得**（明确不做，理由必须留档）：

| 项 | 理由 |
|---|---|
| 给课程卡上采样玻璃 | 违反 ADR-18；破坏 6.5:1 前提；60+ 块同屏成本爆 |
| 给列表行上 `blur` | 同上 |
| 桌面小组件玻璃化 | RemoteViews 不承载 Compose 材质（NG-01） |
| goo 流体融合 | 无相邻胶囊群场景（NG-05） |
| 真环境反射 | 无视向量、无姿态输入（NG-07） |
| 动态壁纸 | 与工具定位冲突（NG-08） |
| 把 accent 用于材质着色 | 违反 V2-09 / B-12 |
| 升级 Compose 换新 API | 1.7.4 已够（ADR-25） |

---

## 1.10 与 2026-09-27 否决记录的关系（RR-01 展开）

这一段必须单独成节，因为它决定「这次凭什么不一样」。

### 1.10.1 发生过什么

`\.workbuddy/memory/2026-09-27-origination.md:27,57` 记录：

> 用户先后否定了 Liquid Glass 方案（原话：「全面取消玻璃质感，你做的效果一点都不好看」）
> 玻璃方案当时也是 27 个测试全绿

`app/src/test/java/com/wedo/schedule/AppleSystemDiagnostic.kt:10` 把这条写成了常驻教训：测试全绿不等于观感成立。

### 1.10.2 当时的方案错在哪（技术推断，标注为推断）

当时的实现是 `Modifier.wedoGlass(...)`，它**没有采样能力**——`WedoGlassTokens.kt:14-15` 头注释自承：「Compose 的 `Modifier.blur` 只能模糊自身子树，不能模糊其背后的兄弟节点，且 `RenderEffect` 需 API 31+。因此玻璃质感的重心落在顶部内高光与边缘光圈」。

也就是说：**当时做的不是玻璃，是一个半透明渐变卡片 + 1dp 描边**。把它叫「液态玻璃」，用户自然会拿真正的玻璃来比，然后说「一点都不好看」。这不是审美分歧，是**交付物与名称不符**。

### 1.10.3 这次凭什么不一样（四条，逐条可验）

| 差异 | 上次 | 这次 | 怎么验 |
|---|---|---|---|
| **D-1 有真采样路径** | 无。`Modifier.blur` 不能采背后 | `GraphicsLayer.record` + 父容器录制 + `drawLayer`，1.7.4 已具备 | P2 的 Bake 首个截图（VR-03） |
| **D-2 有可采的内容** | BASE 是纯色，采样无信息量 | BASE 加极低幅中性纹理（D8），ΔL ≤ 2% | P1 截图对比（VR-04） |
| **D-3 有独立可交付的地基** | 全有或全无 | ADR-19：G0 独立可交付，光学失败不拖累基础质感 | SC-03 盲比 |
| **D-4 有前置的观感门禁** | 「27 个测试全绿」即交付 | 每阶段真机盲比（新旧随机顺序，5 人制），宁可停在 G0 | VR-02/VR-03 记录 |

### 1.10.4 必须承认的不确定性

这次也不能保证观感一定成立。**可以保证的是**：即使 L1/L2 全部失败，退回到纯 G0 的版本，也比 2026-09-27 那一版强——因为它至少有 rim + 内高光 + hairline + 轻阴影构成的**厚度**，而不是「一块更浅的渐变」。

如果 G0 也被否，那说明「半透明材质」这条路在本产品的信息密度下不成立，应当**整体回退到实色**，并把这次的经验写回 `AppleSystemDiagnostic`。这条退路在 P1 结束时就有一个明确的判定点（VR-02），不需要等到最后。

---

## 1.11 本卷验收

| 检查项 | 判定 |
|---|---|
| 「全面玻璃化」有可执行定义，且给出反例 | §1.3 三轴模型 + 分配表 + 8 条反例 ✅ |
| 目标 8 条全部可判定 | 每条附判定方式 ✅ |
| 非目标 10 条全部有理由 | ✅ |
| 边界 12 条全部有检测方式 | 每条附契约测试或扫描方式 ✅ |
| 成功标准 8 条全部有证据形式 | ✅ |
| 必修/可选/不值得三档齐备 | ✅ |
| 与历史否决的关系有正面回答 | §1.10 四条差异 + 承认不确定性 ✅ |
| 品牌锚点 3 条，含对改造的影响判定 | §1.4.3 ✅ |

**遗留问题**：
1. §1.4.2 的「不能像 FlClash」只能靠参数自建来保证，缺少可执行的检测手段——目前只能靠人工评审。列为 VR-06。
2. §1.9.1 的「底栏 G2」依赖 `WedoGlassHost` 的正确性，而 Host 的正确性本身无法在无真机环境下验证（VR-03）。这是 P2 的真实风险，见 R-02。
