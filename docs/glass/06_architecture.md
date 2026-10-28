# 06 · 技术架构与模块设计

> 本卷定义**代码怎么摆**：模块边界、公开 API 面、依赖方向、生命周期、缓存、API 隔离、接入成本。
> **本卷不写**：参数值（归 02/03/04）、逐组件方案（归 05）、阶段（归 07）、验收方法（归 09）。
> 上游：卷 05 全部 24 个对象的 ⑥ 小节（需求来源）、卷 04 §4.12.1（文件布局）、`ADR-15`..`ADR-29`
> 下游：卷 07 的阶段任务按本卷的模块划分；卷 11 的交付清单按本卷的文件清单

---

## 6.1 本卷的三条硬约束

架构设计可以有很多种做法，但本项目只有三条不可违反的约束。它们不是偏好，是**改了就得出问题**。

| # | 约束 | 违反后果 | 强制手段 |
|---|---|---|---|
| **A-1** | `@RequiresApi(31/33)` 代码必须在**独立文件**，不得被 minSdk 26 的类静态引用 | API 26–32 设备在**类校验阶段**抛 `NoClassDefFoundError`（不是运行时分支能救） | `GlassApiIsolationContractTest`（读源码断言 import 关系） |
| **A-2** | `ui/glass` **不得** import `ui/screen` / `ui/component` | 玻璃层被业务耦合，无法独立测试；改一个页面可能连带编译整个模块 | `GlassDependencyContractTest`（扫 import 行） |
| **A-3** | `MainActivity.kt` 的 `pushOverlay(OverlayScreen.X)` lambda **必须留在原文件** | `NavigationSkeletonContractTest:84-102` 立刻红 | 该测试本身（`B-07`） |

A-1 与 A-2 是**本卷新增**的强制测试，A-3 是既有测试。三者共同构成「架构不是靠嘴说」的底线。

---

## 6.2 现状盘点（真实规模）

| 模块 | 目录 | 文件数 | 行数 | 说明 |
|---|---|---|---|---|
| 组件层 | `ui/component/` | 14 | 4,629 | `WedoShell`、`WedoTabBar`、`CourseTableView`(1224)、`ConflictCard`(981) 等 |
| 主题层 | `ui/theme/` | 7 | 2,137 | `Theme`、`AppleScheme`、`WedoAppleTokens`、`WedoGlassTokens` 等 |
| 编辑页 | `ui/screen/edit/` | 1 | 1,845 | `AddCourseScreen` 独占 |
| 导入 | `ui/screen/imports/` | 8 | 2,579 | `ImportWizard`、`ImportFlow`、`JwWebViewLoginScreen` 等 |
| 设置 | `ui/screen/mine/` | 10 | 3,631 | 9 个页面 + `UpdateChangelogDialog` |
| 课表 | `ui/screen/schedule/` | 5 | 959 | `ScheduleScreen`、`ScheduleViewModel`、`WeekPicker`、`WedoWeekHeader` 等 |
| 今日 | `ui/screen/today/` | 1 | 551 | `TodayScreen` |
| 小组件 UI | `ui/screen/widget/` | 3 | 265 | `WidgetEditScreen` 等 |
| **空目录** | `ui/screen/manage/` | 0 | 0 | **死目录**，P0 删除 |
| 根 | `MainActivity.kt` | 1 | 509 | 含 `AppRoot`、`MainTabs`、`OverlayScreen` |

**合计**：`ui/` 下 49 个 Kotlin 文件，约 16,600 行。

**新增的两个模块**（本卷的产出）：

```
ui/glass/     ~1,100 行（7 个文件）
ui/motion/    ~500 行（5 个文件）
```

占比约 **10%**。这个比例是有意的：`ADR-19` 要求 G0 独立可交付，所以渲染部分必须小到可以一次审完。

---

## 6.3 模块划分与依赖方向

### 6.3.1 目标结构

```
ui/theme/      令牌（V-ID 的存储载体）
    ↑
ui/glass/      材质系统（O-ID）+ 能力判定
    ↑
ui/motion/     运动系统（M-ID）
    ↑
ui/component/  可复用组件
    ↑
ui/screen/     页面
    ↑
MainActivity.kt
```

### 6.3.2 依赖规则（可机械检查）

| 模块 | 允许 import | 禁止 import |
|---|---|---|
| `ui/theme` | `androidx.*`、`util.CourseColorUtil` | 其他所有 `ui.*` |
| `ui/glass` | `ui/theme`、`androidx.*`、`android.graphics.*` | `ui/motion`、`ui/component`、`ui/screen` |
| `ui/motion` | `ui/theme`、`androidx.*` | `ui/glass`、`ui/component`、`ui/screen` |
| `ui/component` | `ui/theme`、`ui/glass`、`ui/motion`、`androidx.*` | `ui/screen` |
| `ui/screen` | 全部 | — |

**三条非直观的规则，理由必读**：

| 规则 | 理由 |
|---|---|
| `ui/glass` **不得** import `ui/motion` | 玻璃不驱动动画。若玻璃能调弹簧，就会出现「材质变化有自己的时间曲线」，与 `M-20` 单进度冲突 |
| `ui/motion` **不得** import `ui/glass` | 运动系统不感知材质。若运动能读 `GlassSpec`，就会写出「玻璃档位低时动画也变慢」这种耦合 |
| `ui/component` **不得** import `ui/screen` | 组件不知道自己在哪个页面。`LocalSurfaceClass` 就是为了替代「组件问页面我是谁」 |

### 6.3.3 强制测试

`app/src/test/java/com/wedo/schedule/GlassDependencyContractTest.kt`：

```
① 遍历 ui/glass 下所有 .kt，剥注释后断言：
   assertFalse(src.contains("import com.wedo.schedule.ui.screen"))
   assertFalse(src.contains("import com.wedo.schedule.ui.component"))
   assertFalse(src.contains("import com.wedo.schedule.ui.motion"))
② 遍历 ui/motion 下所有 .kt，断言不含 ui.glass / ui.screen
③ 遍历 ui/component 下所有 .kt（排除已白名单的过渡期文件），断言不含 ui.screen
```

**为什么必须用测试而不是约定**：项目已有一个反例——`WedoCourseCard.kt:41` 用 `courseName` 取色，而 `CourseColorUtil` 用 `groupId`，两处「约定」不一致且**没有任何测试发现**（`F-08`）。约定会漂移，测试不会。

### 6.3.4 过渡期白名单

`ui/component` 里有若干文件会被 `ui/screen` 反向引用（历史遗留）。规则 A-2 落地时不可能一次全绿。

| 阶段 | 白名单内容 | 说明 |
|---|---|---|
| P1 | `ui/component/` 全部文件 | 规则先只对 `ui/glass` 生效 |
| P3 | 只保留 `CourseTableView.kt` | 它被 `ScheduleScreen` 直接使用 |
| P6 | **清空** | 全部迁移完成 |

白名单文件放在 `app/src/test/java/com/wedo/schedule/GlassDependencyWhitelist.kt`，**测试断言「白名单只减不增」**——否则它会变成第二个「永远不做的 TODO」。

---

## 6.4 公开 API 面（恰好 12 项，多一个就是抽象过度）

### 6.4.1 全部公开 API

| # | 名称 | 类型 | 来源需求 | 说明 |
|---|---|---|---|---|
| 1 | `WedoEnvironmentLayer(modifier, content)` | Composable | L-01 | 环境层容器，内含 `L-01` 的纹理 |
| 2 | `WedoGlassHost(depth, content)` | Composable | L-02 / L-18 | 捕获宿主，提供 `LocalGlassStage` |
| 3 | `WedoDialog(...)` | Composable | L-20 | 统一 15 处 `AlertDialog` |
| 4 | `WedoAnimatedContent(...)` | Composable | L-19 | 带减动效退化的 `AnimatedContent` |
| 5 | `WedoAnimatedVisibility(...)` | Composable | L-10 | 带减动效退化的 `AnimatedVisibility` |
| 6 | `Modifier.wedoGlassSurface(surface, quota)` | Modifier | L-02..L-17 | 材质入口 |
| 7 | `Modifier.wedoGlassInset()` | Modifier | 全部 FLOAT | `V-52` 内容内缩 |
| 8 | `Modifier.wedoPress(onLongClick, onClick)` | Modifier | L-05 | 既有，从 `WedoShell` 迁入 |
| 9 | `GlassSpec` | data class | — | 解析后的材质参数（`V-`/`O-` 的运行时载体） |
| 10 | `GlassSnapshot` | class | L-14..L-17 | 跨窗口快照句柄（`O-81`..`O-85`） |
| 11 | `GlassCapability` | object | L-01 / L-02 | `resolveRenderTier(...)` 纯函数 |
| 12 | `WedoSprings` + `WedoMotionPolicy` | object | L-04 / L-05 | 弹簧族表 + 门控 |

**统计**：5 个 Composable + 3 个 Modifier 扩展 + 2 个数据类型 + 2 个 object = 12。符合「≤ 8 composable + 3 Modifier + 2 data class」的上限。

### 6.4.2 其余全部 `internal`

| 类/函数 | 可见性 | 理由 |
|---|---|---|
| `SurfaceClass` / `QuotaGrade` / `RenderTier` | `internal enum` | 只有 `ui/glass` 内部需要判断；外部只传 `SurfaceClass` 与 `QuotaGrade` 的值 |
| `LocalGlassStage` / `LocalGlassDepth` / `LocalSurfaceClass` | `internal` | `L-06` 需要读 `LocalSurfaceClass`，但它在 `ui/glass` 内部泄漏为 `internal` 后，`ui/component` 同模块可见 —— Kotlin 的 `internal` 是模块级，够用 |
| `GlassStage` / `GlassMode` / `GlassPanelTable` | `internal` | 管线内部 |
| `GlassUniforms` | `internal` | uniform 顺序的唯一源 |
| `WedoPressState` | `internal` | 最小驻留的状态机 |
| `WedoGlassDebug` | `internal` | 降级观测，只在 `debug` 变体下写入日志 |

**为什么 `GlassCapability` 是公开而 `RenderTier` 是 internal**：外部（卷 08 的诊断 UI）需要问「这台设备是什么档」，但不应该能构造 `RenderTier`。所以公开一个只读的 `object`，枚举本身藏起来。

### 6.4.3 为什么不公开更多

三个被明确否决的「看起来该公开」的 API：

| 候选 | 否决理由 |
|---|---|
| `WedoGlassScope.of(context)` 之类的全局单例 | 项目无 DI，`CompositionLocal` 已经够；再加单例会与 `WedoApp.get()` 争地盘 |
| `buildGlassSpec(quality, dark, level, surface, tier)` | 它是**纯函数**，应该留在 `internal` 供测试用。外部只通过 `WedoGlass` 解析 |
| `GlassPanelTable` 的构造器 | L2 的 shader 输入，属实现细节。若公开，会有人手工拼面板表绕过配额（`GlassQuota`） |

---

## 6.5 公开 API 的精确签名

### 6.5.1 材质入口

```kotlin
// ui/glass/WedoGlassSurface.kt
@Composable
fun Modifier.wedoGlassSurface(
    surface: SurfaceClass,
    quota: QuotaGrade = QuotaGrade.Auto,
): Modifier
```

| 参数 | 语义 |
|---|---|
| `surface` | 唯一必传。**必须由调用方显式声明**，不提供默认值——默认值会让「忘了声明」静默通过 |
| `quota` | 默认 `Auto`，由 `GlassQuota` 根据 `surface` + 配额 + 当前阶段解析 |

**为什么 `surface` 没有默认值**：卷 02 §2.2.5 的 R-CLS-1 说「按最近祖先判定」，但那是**子元素**的规则。`wedoGlassSurface` 是**分类根**的声明点，必须显式。两者不矛盾：分类根声明，子元素继承。

**为什么 `quota` 有默认值**：绝大多数调用方不需要关心定额，`Auto` 就是「由系统决定」。只有 `L-11`（课程块要「无阴影」变体）需要显式传。

### 6.5.2 环境层

```kotlin
// ui/glass/GlassEnvironment.kt
@Composable
fun WedoEnvironmentLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)
```

内部 `drawBehind { 明度场; 噪点 tile }`，**不改布局**（`ADR-21`）。

`WedoShell.kt:56` 的 `WedoBackground` 改为调用它，保留原签名：

```kotlin
@Composable
fun WedoBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    WedoEnvironmentLayer(modifier, content)   // 签名不变，内部换实现
}
```

这样做的好处：`MainActivity.kt:451` 与 `ImportWizard.kt:226` 两处调用**一行都不用改**（`R-BG-5` 要求保留第二处）。

### 6.5.3 捕获宿主

```kotlin
// ui/glass/GlassCapture.kt
@Composable
fun WedoGlassHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)
```

内部：
- 读 `LocalGlassDepth.current`，若 `>= 2` 则**不建 Host、直接 content**（`O-14` 的硬限，而非抛异常）
- 建 `GlassStage`，`LocalGlassDepth provides (current + 1)`
- `LocalGlassStage provides stage`
- `Modifier.drawWithContent { if (stage.hasLiveConsumer) { record; drawLayer } else drawContent() }`

**关键设计：深度超限时静默降级，不抛异常。** 理由：`O-14` 是架构约束，但抛异常会让「某个页面多包了一层 Host」直接崩。静默降级后画面仍是 G0，只是没有采样——**能看，只是不玻璃**。

### 6.5.4 材质解析

```kotlin
// ui/glass/GlassSpec.kt
@Immutable
data class GlassSpec(
    // 视觉（V-ID）
    val tint: Color, val tintAlpha: Float,
    val rimAlpha: Float, val rimWidth: Dp,
    val hairlineAlpha: Float, val hairlineWidth: Dp,
    val innerHighlightAlpha: Float, val innerHighlightExtent: Float,
    val shadowNear: ShadowSpec?, val shadowFar: ShadowSpec?,
    // 光学（O-ID）
    val blurSigma: Float, val refraction: Float,
    val bevel: Float, val dispersion: Float, val rimBlur: Float,
    // 元信息
    val surface: SurfaceClass, val quota: QuotaGrade, val tier: RenderTier,
    val shadowAllowed: Boolean,   // V-40 的高密度单元
)
```

**字段命名直接对齐 V-ID 与 O-ID 的语义**，不另起简称。这是为了让「文档里的参数」与「代码里的字段」一一对应——否则一年后没人知道 `a1` 是什么。

### 6.5.5 跨窗口快照

```kotlin
// ui/glass/GlassSnapshot.kt
class GlassSnapshot internal constructor(
    internal val bitmap: ImageBitmap,
    internal val capturedAtNanos: Long,
) {
    companion object {
        /** 预拍：在有「按住期」的交互里立刻调用 */
        suspend fun captureFrom(window: ComposeWindow): GlassSnapshot?
        /** 取最近一次，取完即置空（O-85：只保留一张） */
        fun takeLatest(): GlassSnapshot?
        fun release()
    }
}
```

**`takeLatest()` 取完即置空**——这是 `O-85`「只保留一张」的强制实现，用 API 形态防止误用，而不是靠注释提醒。

---

## 6.6 三层令牌与参数流

### 6.6.1 流经路径

```
① 原始数值      WedoGlassTokens（常量）+ 卷 02/03/04 的 ID 表
                        ↓
② 解析          buildGlassSpec(quality, dark, surface, tier)
                纯函数，JVM 可测，不依赖 Compose
                        ↓
③ 绘制          GlassSpec 被 drawBehind 消费
                只做「读字段 → 画」，不含任何 if (dark) 之类的分支
```

| 规则 | 内容 |
|---|---|
| **A-4** | 第 ① 层的数值只允许出现在 `WedoGlassTokens.kt`。其他文件出现字面量即缺陷（`V-70`） |
| **A-5** | 第 ② 层必须是**纯函数**，可脱离 Compose 单测（`V-71`） |
| **A-6** | 第 ③ 层**禁止分支判断**，所有 `if` 在第 ② 层完成（`V-72`） |

### 6.6.2 解析的调用时机

**必须 `remember`，不能每帧调。**

```kotlin
@Composable
fun Modifier.wedoGlassSurface(surface: SurfaceClass, quota: QuotaGrade): Modifier {
    val dark = LocalWedoDark.current
    val tier = GlassCapability.renderTier()          // remember 内部
    val spec = remember(surface, quota, dark, tier) {
        buildGlassSpec(...)                          // 一次
    }
    return this.drawBehind { drawGlass(spec) }        // 每帧只读 spec
}
```

| 规则 | 内容 |
|---|---|
| **A-7** | `buildGlassSpec` 的调用必须在 `remember` 的 key 明确列出依赖（`surface`/`quota`/`dark`/`tier`），不得用 `remember { }` 空 key |
| **A-8** | `isLowRamDevice` 的查询必须在 `remember` 里做一次（`O-69`），不得进入绘制路径 |

**A-7 的反例**：`remember { buildGlassSpec(...) }` 空 key 会在主题切换时**不重新解析**，导致深色模式下仍用浅色的 tint。这类 bug 不会崩，只会「颜色不对」。

---

## 6.7 生命周期与 CompositionLocal 提供点

### 6.7.1 提供点

| Local | 提供者 | 默认值 | 消费方 |
|---|---|---|---|
| `LocalGlassStage` | `WedoGlassHost` | `null` | `wedoGlassSurface`（null → G0，`O-74`） |
| `LocalGlassDepth` | `WedoGlassHost` | `0` | `WedoGlassHost` 自身（判断是否超 2） |
| `LocalSurfaceClass` | `WedoGlassHost` 与各分类根 | `CONTENT` | 子组件（R-CLS-3） |

**默认值的选择是有意的**：

- `LocalGlassStage = null` → 组件在 Host 外时自动 G0，不崩（`O-74`）。这让单测与 Preview 都能正常工作。
- `LocalSurfaceClass = CONTENT` → 最保守的默认（不采样），漏声明时「少一点质感」而不是「多一份开销」。

### 6.7.2 引用计数与录制开关

`GlassStage` 需要知道「有没有 G1/G2 消费者」，用**引用计数**实现：

```
面板进入组合（L1/L2 档）→ stage.acquireLive()
面板离开组合          → stage.releaseLive()
stage.hasLiveConsumer = (liveCount > 0)
```

| 规则 | 内容 |
|---|---|
| **A-9** | 计数必须在 `DisposableEffect` 的 `onDispose` 里减，不得依赖 `LaunchedEffect` 的取消 |
| **A-10** | 计数为 0 时立即停止录制并释放离屏（`O-03` / `O-65`） |

**A-9 的理由**：`LaunchedEffect` 的取消时机不保证在离开组合的同一帧，可能出现「面板已消失但计数未减」→ 永远在录。`DisposableEffect` 的 `onDispose` 是确定性的。

### 6.7.3 与 `OverlayScreen` 的关系

卷 04 §4.13.4 已指出：`OverlayScreen` 的早退链（`MainActivity.kt:376-444`）让覆盖页与被覆盖页**不同时在组合中**，所以 Host 会自然 dispose。

| 规则 | 内容 |
|---|---|
| **A-11** | 不得为了「保留捕获状态」而把覆盖页改成常驻组合 | 会破坏 `NG-03` 与内存模型 |
| **A-12** | 若未来迁移到 `NavHost`，必须补「路由遮盖时停止录制」，否则离屏内存翻倍 | 记入 `R-08` |

---

## 6.8 复用与失效

### 6.8.1 三种可复用资产

| 缓存 | 键 | 失效条件 | 生命周期 |
|---|---|---|---|
| **Bake 离屏** | `GlassContentVersion` | 版本号变化 | 面板打开期间 |
| **环境噪点位图** | 无（全局单例） | 不失效（确定性，`V-02`） | 进程内 |
| **跨窗口 Snapshot** | 无（只保留一张，`O-85`） | `takeLatest()` 取走即失效 | 一次面板打开 |

### 6.8.2 `GlassContentVersion` 的组成

```
version = hash32(
    scrollValue,        // Int，滚动位置
    dataVersion,        // Int，课程数据变更计数
    darkTheme,          // Boolean
    width shl 16 or height  // Int，尺寸
)
```

| 规则 | 内容 |
|---|---|
| **A-13** | 版本号用**单一 32 位整数**，不用 data class | `remember` 的比较成本更低；哈希碰撞的后果只是「该重录没重录」，下次还会触发 |
| **A-14** | 不得把 `Float` 进度值放进版本号 | 浮点抖动会让每次微移都重录 |
| **A-15** | `scrollValue` 必须是**离屏像素**级（除过 `O-04` 的 0.5×），不是逻辑 dp | 否则 120Hz 下每帧都变（`O-07`） |

A-15 是 `O-07` 的落地：`scrollValue.toFloat() / density / 2` 取整后再进哈希。

---

## 6.9 API 隔离与文件布局

### 6.9.1 七个文件（与卷 04 §4.12.1 一致，不重复发明）

| 文件 | 最低 API | 行数估算 | 职责 |
|---|---|---|---|
| `ui/glass/GlassCapability.kt` | 26 | ~80 | 纯函数，`resolveRenderTier` |
| `ui/glass/GlassSpec.kt` | 26 | ~180 | data class + `buildGlassSpec` |
| `ui/glass/GlassEnvironment.kt` | 26 | ~120 | 环境层 + 噪点位图 |
| `ui/glass/WedoGlassSurface.kt` | 26 | ~200 | 公开 Modifier，按 tier 分发 |
| `ui/glass/GlassCapture.kt` | 26 | ~320 | Host + Stage + 三模 |
| `ui/glass/GlassBlur.android.kt` | **31** | ~60 | `BlurEffect` 包装 |
| `ui/glass/GlassShader.android.kt` | **33** | ~140 | `RuntimeShader` + `BitmapShader` |
| `ui/glass/GlassSnapshot.kt` | 26 | ~90 | 跨窗口快照 |

合计约 1,190 行。

### 6.9.2 `internal` 文件

| 文件 | 职责 |
|---|---|
| `ui/glass/GlassQuota.kt` | 配额解析（`G2 ≤ 3` 等） |
| `ui/glass/GlassUniforms.kt` | uniform 顺序的唯一源（`O-44`） |
| `ui/glass/GlassDebug.kt` | 降级观测（`O-68`） |
| `ui/motion/WedoSprings.kt` | 弹簧族表（`M-01`..`M-08`） |
| `ui/motion/WedoMotionPolicy.kt` | 门控（`M-32`..`M-39`） |
| `ui/motion/WedoPress.kt` | 最小驻留（`M-43`） |
| `ui/motion/WedoProgress.kt` | 单进度（`M-20`..`M-23`） |
| `ui/motion/WedoMotion.kt` | 迁入 `WedoThumbSpring`，保留常量名 |

### 6.9.3 API 隔离的实现方式

`GlassBlur.android.kt` 与 `GlassShader.android.kt` 的**签名必须只含 26 可用的类型**：

```kotlin
// GlassBlur.android.kt —— 文件头 @RequiresApi(31)
internal object GlassBlurEffect {
    /** 唯一对外入口：接收 GraphicsLayer，返回修饰后的 Modifier（26 可用类型） */
    fun apply(radiusPx: Float): (GraphicsLayerScope.() -> Unit)?
}
```

26 侧的调用：

```kotlin
// GlassCapture.kt
val effectApplier = if (Build.VERSION.SDK_INT >= 31) GlassBlurEffect.apply(radius) else null
```

| 规则 | 内容 |
|---|---|
| **A-16** | 31/33 文件里的函数**只能出现在 `SDK_INT` 分支的第一层调用**，不得被 26 文件的顶层属性/常量引用 | 顶层属性会在类初始化时触发加载 |
| **A-17** | 33 文件的返回类型必须是 `Any?` 或 26 可用的接口类型 | 否则 26 侧的方法签名会触发类校验 |

**A-17 的具体做法**：`GlassShader` 返回 `Any?`（实为 `RuntimeShader`），26 侧只把它透传给 `GraphicsLayer`，**不调用其任何方法**。这样 26 的字节码里没有对 `RuntimeShader` 的符号引用。

这是整个架构里最反直觉的一处：**为了兼容，33 的代码必须对 26 完全「类型不可见」**。

---

## 6.10 新组件接入的三行代码

设计目标：**新增一个玻璃面板，不超过三行**。超过就说明 API 设计失败。

### 6.10.1 三种接入方式

**方式 A：在已有 Host 内加一个浮层**

```kotlin
Box(
    Modifier
        .wedoGlassSurface(SurfaceClass.FLOAT)   // ← 一行
        .wedoGlassInset()                        // ← 可选，V-52 的内容内缩
) { content() }
```

**方式 B：新建一个 Host（页面级）**

```kotlin
WedoGlassHost {                                  // ← 一行
    content()
}
```

**方式 C：跨窗口面板**

```kotlin
val snapshot = remember { GlassSnapshot.takeLatest() }
ModalBottomSheet(
    containerColor = Color.Transparent,           // ← 必须
    modifier = Modifier.wedoGlassSurface(SurfaceClass.FLOAT, QuotaGrade.G1),
) { content() }
```

### 6.10.2 接入检查清单

新增组件时必须逐条确认：

| # | 检查 | 不做的后果 |
|---|---|---|
| 1 | `SurfaceClass` 显式声明了吗 | 默认 `CONTENT`，会拿到 G0 而不是想要的档 |
| 2 | 高度 ≥ `V-28` 的门槛吗 | 低于门槛应**不接材质**，而不是硬加 |
| 3 | 在 Host 内吗（G1/G2） | 不在 Host 内自动降 G0（`O-74`），不崩但没采样 |
| 4 | 是跨窗口吗 | 是则必须走 `GlassSnapshot`，不能指望 `LocalGlassStage` |
| 5 | 有「按住期」可预拍吗 | 没有则优先 G0（`O-86`） |
| 6 | 有减动效门控吗 | 走 `WedoMotionPolicy`，不要直接写 `spring()` |
| 7 | 动了 padding 吗 | **不许**（`B-06`），材质走 `drawBehind` |

---

## 6.11 状态管理：三套状态，三个归属

项目无 DI（无 Hilt/Koin），`WedoApp.get()` 是手写单例。这决定了玻璃的状态只能有三个归属，不能有第四种。

### 6.11.1 三套状态与归属

| 状态 | 归属 | 生命周期 | 载体 |
|---|---|---|---|
| **材质解析结果**（`GlassSpec`） | Composable 局部 | 随组合 | `remember(surface, quota, dark, tier)` |
| **渲染运行时**（`GlassStage` 及其离屏、引用计数） | Host 局部 | 随 Host 组合 | `remember { GlassStage() }` + `DisposableEffect` |
| **用户偏好**（质量档、动效、触觉） | 全局单例 | 进程 + 持久化 | `WedoSettingsStore` → `SharedPreferences` |

### 6.11.2 为什么不放进 ViewModel

项目只有 3 个 ViewModel（`ScheduleViewModel` / `JwImportViewModel` / `WidgetEditViewModel`），全部是 `StateFlow` 驱动的业务状态。

玻璃状态**不能进 ViewModel**，两个理由：

| 理由 | 说明 |
|---|---|
| 它是**渲染细节**，不是业务状态 | ViewModel 里出现 `GlassSpec` 会让「换渲染方案」变成「改业务层」 |
| 它是**每帧相关**的 | ViewModel 的 `StateFlow` 发射会触发重组；材质参数重组一次就够，不该走流 |

### 6.11.3 为什么 `WedoSettingsStore` 必须是单例

现有三个存储键分散在三处：`WedoDesign.kt:18-36`（`wedo_display`）、`AppPrefs`（`wedo_prefs`）、`MainActivity.kt:153`（`wedo_privacy`）。

`WedoDisplay.motion` / `quality` 已经存在但**写入口在生产代码零调用**（`00_MASTER` `F-06` 的相关条目）。

| 规则 | 内容 |
|---|---|
| **A-21** | 三个开关的读写**只能**通过 `WedoSettingsStore`，不得再直连 `WedoDesign` 的 `SharedPreferences` |
| **A-22** | `WedoSettingsStore` 必须以 `StateFlow<WedoSettingsState>` 暴露，而不是裸 `Boolean` | 便于 `AppearanceScreen` 订阅，且避免「改了值但界面不刷新」 |
| **A-23** | 写入必须 `apply()`（异步）而非 `commit()` | `commit()` 在主线程做磁盘 IO，`WedoApp.kt:34` 的 StrictMode 会抓 |

A-23 的具体风险：`WedoApp.kt:34` 已启用 StrictMode 的 diskRead 检测。若 `commit()` 在主线程调用，debug 变体会直接抛。

### 6.11.4 状态的分层图

```
SharedPreferences（3 个文件）
        ↓ 读一次 + 监听
WedoSettingsStore（单例，StateFlow）
        ↓ collectAsState
LocalWedoDisplay（CompositionLocal）
        ↓ 读
GlassCapability.resolveRenderTier()  ← 纯函数，不读 Local
        ↓
GlassSpec（remember 缓存）
        ↓
drawBehind（每帧读字段）
```

**关键**：`GlassCapability` 是**纯函数**，输入是 `sdkInt` / `isLowRam` / `quality`，不读 `CompositionLocal`。这样它能被 JVM 单测直接覆盖（`Q-85`）。

---

## 6.12 组件级 vs 公共模块：什么进 `ui/glass`，什么留在组件里

这是最容易抽象过度的决策点。判据只有一条：**「换个组件还能不能原样用」**。

### 6.12.1 进公共模块（3 类）

| 类别 | 内容 | 判据 |
|---|---|---|
| **材质解析** | `buildGlassSpec` / `GlassQuota` | 所有面板共用同一套参数逻辑 |
| **捕获管线** | `GlassStage` / 三模 / `GlassSnapshot` | 与「这块面板是什么」无关 |
| **运动族表** | `WedoSprings` / `WedoMotionPolicy` | 弹簧参数与业务无关 |

### 6.12.2 留在组件级（5 类）

| 内容 | 留在哪 | 为什么不抽 |
|---|---|---|
| **`SegmentedSwitcher` 的字符覆盖率上色**（`:209-239`） | `ui/component/SegmentedSwitcher.kt` | 只有「色块扫过文字」这一种场景需要。抽成公共会变成「一个参数 20 个、只有一处调用」的典型过度设计 |
| **`ConflictCard` 的折角绘制** | `ui/component/ConflictCard.kt` | 冲突卡专属图形，无第二处使用 |
| **`CourseTableView` 的网格布局** | `ui/component/CourseTableView.kt` | 只有课表需要「节次 × 星期」的网格 |
| **`WeekPicker` 的学期进度条**（`:205-221`） | `ui/screen/schedule/WeekPicker.kt` | 两个 Box 自绘，抽出去反而增加一层间接 |
| **各页面的空状态布局** | 各页面 | `L-23` 明确判「不加材质」，本来就没有可抽的东西 |

### 6.12.3 一条反直觉的规则

| 规则 | 内容 |
|---|---|
| **A-24** | **只有出现第二个使用方时，才允许抽公共模块**。第一个使用方必须先在原地写 |

理由来自本项目的一个真实教训：`WedoGlassTokens.kt`（202 行）在 `Modifier.wedoGlass` 删除后仍存活，**唯一消费方是单测**。它当初就是为了「以后可能复用」而抽出来的，结果复用没发生，维护成本留下了。

→ 这条规则直接约束本次改造：**`ui/glass` 里的每个文件都必须有 ≥1 个生产调用方**。P6 时逐一核对，无用即删。

### 6.12.4 三个「看起来该抽」但不抽的候选

| 候选 | 否决理由 |
|---|---|
| 通用的「卡片容器」 | `SettingsCards` / `WedoCourseCard` / `ConflictCard` 的层序与内容内缩都不同（`V-52` 对 FLOAT、`V-38`/`V-39` 对 CONTENT），抽成通用卡会让三处各自加 if |
| 通用的「弹出面板」 | `ModalBottomSheet` / `Dialog` / `WeekPicker` 三者的窗口语义、scrim、拖拽关闭都不同（卷 05 §5.13） |
| 通用的「弹簧动画属性」 | `Animatable` 已经够；再包一层会丢掉 `block{}` 的逐帧回写（`SegmentedSwitcher:80-86` 依赖它） |

---

## 6.13 配置规范：参数只能从这三个地方来

### 6.13.1 三个来源，无第四个

| 来源 | 存什么 | 谁能改 | 变更成本 |
|---|---|---|---|
| **编译期常量** | `WedoGlassTokens` 的数值、`P-01` 的预算 | 改代码 + 重新构建 | 高 |
| **构建期开关** | `GLASS_ENABLED`、`GlassQuota.maxG2`、`BuildConfig.DEBUG` | 改 `build.gradle.kts` + 重新构建 | 中 |
| **用户偏好** | `QualityTier`、动效、触觉 | 用户在设置页 | 低（立即生效） |

| 规则 | 内容 |
|---|---|
| **A-25** | **不得出现运行时远程配置**（无后端、无 feature flag 服务） | 项目无后端；引入远程配置等于引入一个永不使用的依赖 |
| **A-26** | 不得把「应该编译期定」的参数做成用户可调 | 例：`refraction` 不能给用户调，因为不同值的视觉正确性未经验证 |
| **A-27** | 任何新增的用户偏好，必须同时提供：① 设置页入口 ② `WedoSettingsStore` 字段 ③ 至少一处生产消费方 ④ 单测断言 ①–③ 齐备 | 否则就是第二个「死开关」（`Q-02`） |

### 6.13.2 用户偏好的三个合法项

只有三个，且都已有既定键名：

| 项 | 键 | 默认 | 合法值 |
|---|---|---|---|
| 质量档 | `"quality"`（`wedo_display`） | `"balanced"` | `smooth` / `balanced` / `fine` |
| 动效 | `"motion"`（`wedo_display`） | `true` | `true` / `false` |
| 触觉 | `"haptics"`（`wedo_display`） | `true` | `true` / `false` |

**`density` 是第四个已存在的键，但本次明确不接**（`U-11`）——全仓零消费点，接了也是假开关。登记为技术债。

### 6.13.3 参数变更的留痕规范

| 规则 | 内容 |
|---|---|
| **A-28** | 任何 `WedoGlassTokens` 的数值变更，必须在 KDoc 里写「值 + 原因 + 日期」 | `M-66` 的推广 |
| **A-29** | 变更必须附对应的 `V-ID` / `O-ID` / `M-ID`，便于回溯到本套文档 | 否则文档与代码脱钩 |
| **A-30** | 不得「为了让某一帧好看」而改参数 | `M-67` 的推广；以完整交互的观感为准 |

A-28 的现状反差：`WedoMotion.kt:11-14` 只有一行「用户实测定参（高硬度+无回弹，MediumLow 拖沓不跟手）」，既没说试了几版，也没说为什么是 `StiffnessHigh` 而不是别的。**这是最典型的「参数有值但没理由」**，本次改造必须避免重演。

---

## 6.14 扩展机制：三种「以后要加东西」的预案

架构的价值不在「现在够用」，在「以后加东西不用推倒」。三种最可能的扩展：

### 6.14.1 加一个新组件

见 §6.10 的三行接入。**不需要动 `ui/glass`** —— 这是 API 面设计的验收标准：如果加组件要改 `ui/glass`，说明抽象漏了。

### 6.14.2 加一个新渲染档

假设未来要加 L3（真环境反射，`NG-07` 目前禁止）。需要的改动：

| 步骤 | 改动点 |
|---|---|
| ① 加枚举值 | `RenderTier.L3` |
| ② 加能力判定 | `GlassCapability.resolveRenderTier` 加一个分支 |
| ③ 加配方 | `buildGlassSpec` 加一个 `when` 分支 |
| ④ 加实现文件 | `GlassReflection.android.kt`，`@RequiresApi(34)` |
| ⑤ 加 uniform | `GlassUniforms` + AGSL 注释块（两边同时改，`O-46` 会抓漏） |

**不需要改的**：`GlassStage`、三模、配额、所有组件的 `wedoGlassSurface` 调用。

→ 这就是 `ADR-15` 三轴分离的收益：加档只动「能力轴」，不动「语义轴」。

### 6.14.3 加第二个学校（教务导入）

**与本改造无关，但架构上必须不冲突**：`JwSchoolInfo.authHosts` / `jsBridgeHosts` 是唯一扩展点（`00_MASTER` 未覆盖，属既有架构）。

玻璃改造对它的唯一影响：`JwWebViewLoginScreen` 所在的 `JwImportActivity` 是独立 Activity（`R-BG-6`），**不能**跟着 `L-18` 一起把底色删掉。

### 6.14.4 不允许的扩展

| 扩展 | 为什么禁止 |
|---|---|
| 引入 `Navigation-Compose` | `NG-03` / `ADR-24`；且会让 `OverlayScreen` 的早退链消失，Host 不会 dispose（`A-11`/`A-12`） |
| 引入 DI 框架 | 项目无 DI 是既定事实；引入会让 `WedoApp.get()` 与容器并存，出现两套取实例的方式 |
| 引入远程配置 | `A-25` |
| 引入 benchmark 模块 | `U-08` 未定；且 `AGP 9.1.1` 的兼容性未验证 |

---

## 6.15 维护策略：三类改动的正确做法

### 6.15.1 改参数

```
① 找到对应的 V-/O-/M-ID
② 改 WedoGlassTokens 的常量
③ 在 KDoc 写「值 + 原因 + 日期 + ID」（A-28/A-29）
④ 跑 WedoGlassTokensTest —— 它会红（因为断言锁的是旧值）
⑤ 更新断言并说明「为什么这个变化是预期的」
```

**第 ④ 步必须红一次。** 如果改完参数测试还是绿的，说明那个参数根本没有被测试覆盖——这比测试红更糟。

### 6.15.2 加材质到一个新组件

```
① 确认高度 ≥ V-28 门槛
② 确认在 Host 内（否则自动降 G0）
③ 确认是跨窗口还是同窗口（决定 Bake 还是 Snapshot）
④ 加 wedoGlassSurface(SurfaceClass.X)
⑤ 加 wedoGlassInset()（若内容贴边）
⑥ 在卷 05 里追加一条 L-ID，并给 10 小节
```

**第 ⑥ 步不能省。** 不加 L-ID 的组件会变成「文档里不存在但有材质」的黑户，下次审计时会被当成缺陷。

### 6.15.3 删功能

```
① 先把 wedoGlassSurface 的调用移除（回 G0 或实色）
② 跑 Q-06 确认像素级回退
③ 再删文件
```

**顺序为什么是「先调用后文件」**：如果先删文件，编译直接失败，无法验证「删掉后界面是否正常」。先移除调用可以保持可运行状态，验证回退无误后再删实现。

### 6.15.4 三人（或三个 Agent）并行时的文件归属

若未来多 Agent 并行实施，按目录切分：

| 归属 | 目录 | 冲突风险 |
|---|---|---|
| A | `ui/glass/` | 低（只被引用，不引用外界） |
| B | `ui/motion/` | 低（同上） |
| C | `ui/component/` + `ui/screen/` | 中（18 处底色 + 24 个对象） |
| D | `docs/` + 测试 | 高（测试是全项目共享的） |

| 规则 | 内容 |
|---|---|
| **A-31** | A/B/C 三方**不得同时**改 `WedoGlassTokens.kt` | 它是唯一共享的可写文件，必然冲突 |
| **A-32** | D 方（测试）必须在 A/B/C 每次合并后跑全量 | 测试是唯一的跨方一致性保障 |

---

## 6.16 接口约束矩阵（12 个公开 API 的前置/保证/违规后果）

签名只是「能调」，约束才是「能调对」。下表每个 API 三列：调用前必须满足什么、调用后能保证什么、违约会怎样。

### 6.16.1 材质相关

| API | 调用前置条件 | 调用后保证 | 违规后果 |
|---|---|---|---|
| `wedoGlassSurface(surface, quota)` | ① 面板高度 ≥ `V-28` 门槛<br>② 若 `surface == FLOAT`，应在 Host 内 | 返回的 Modifier 会把材质画在 `drawBehind`，**不改布局**（`ADR-21`） | 高度不足 → 边缘退化为描边框；不在 Host 内 → 自动 G0（不崩，但没采样） |
| `wedoGlassInset()` | 必须与 `wedoGlassSurface` 同一节点 | 内容内缩到 `V-52` 的距离 | 单独使用而无材质 → 内容无故缩进 |
| `WedoEnvironmentLayer(modifier, content)` | 应是页面最外层 | 提供 `V-01`..`V-04` 的纹理；`GLASS_ENABLED=false` 时退化为纯色 | 嵌套两层 → 纹理叠两份（`R-BG-5` 的唯一例外是 `ImportWizard`） |

### 6.16.2 管线相关

| API | 调用前置条件 | 调用后保证 | 违规后果 |
|---|---|---|---|
| `WedoGlassHost(modifier, content)` | 深度 ≤ 2（由 `LocalGlassDepth` 自动判断） | 提供 `LocalGlassStage`；有 G1/G2 消费者时才录制 | 深度超 2 → 内层静默降 G0（不抛异常，见 §6.5.3） |
| `GlassSnapshot.captureFrom(window)` | 必须在目标窗口出现**之前**调用（`O-82`） | 返回一张按 `O-04` 降采样的位图；失败返回 `null` | 在窗口出现后调用 → 拍到已盖 scrim 的画面，玻璃里是灰 |
| `GlassSnapshot.takeLatest()` | 无 | 返回唯一一张并**置空**（`O-85`） | 连调两次 → 第二次返回 `null`，面板降 G0 |
| `GlassCapability.renderTier()` | 无（纯函数） | 返回当前 `RenderTier`，同一次组合内稳定 | 在绘制路径调用 → 每帧算 `isLowRamDevice`（`A-8`） |

### 6.16.3 运动相关

| API | 调用前置条件 | 调用后保证 | 违规后果 |
|---|---|---|---|
| `WedoSprings.xxx` | 必须确认「这根弹簧驱动什么」（`M-R1`） | 返回 `AnimationSpec`，可直接用于 `Animatable` / `animate*AsState` | 一根弹簧驱动两件事 → 跟手发飘或回弹发硬 |
| `WedoMotionPolicy.resolve(spec)` | 无 | 动效关闭时返回 `snap()`，否则原样返回 | 不用它直接写 `spring()` → 关动效后仍弹 |
| `Modifier.wedoPress(...)` | 元素需可点击 | `scale 0.97` + 最小驻留 `M-43`；终值恒 1.0 | 在已有 `clickable` 的节点上重复挂 → 双重点击响应 |

### 6.16.4 其他

| API | 调用前置条件 | 调用后保证 | 违规后果 |
|---|---|---|---|
| `WedoDialog(...)` | 必传 `shape` 与 `tonalElevation = 0.dp` | 一致圆角 + G0 底；无 Material 抬高色 | 漏传 → 出现 Material 默认的紫灰底（深色模式下明显） |
| `WedoAnimatedContent(...)` | `transitionSpec` 必传 | 动效关闭时退化为淡变（`M-34`） | 不传 → 编译不过（签名要求），不会静默 |
| `WedoAnimatedVisibility(...)` | 同上 | 同上 | 同上 |

### 6.16.5 三条跨 API 的通用约束

| 规则 | 内容 |
|---|---|
| **A-35** | 任何 API 的**违规后果必须是「外观降级」或「编译失败」，不得是「运行时崩溃」 | 玻璃是锦上添花，任何失败都不该让 App 不可用（`O-67`） |
| **A-36** | 任何 API 不得要求调用方**按顺序**调用两个方法才有正确结果 | 顺序依赖是 bug 的温床；宁可多一个包装函数 |
| **A-37** | 任何 API 的默认参数，必须是「最保守」的那个值 | `surface` 无默认（强制声明）、`quota = Auto`（由系统裁决）、`LocalSurfaceClass = CONTENT`（不采样） |

A-37 的例子：`LocalSurfaceClass` 默认 `CONTENT` 而不是 `FLOAT`。因为默认成 FLOAT 会让「漏声明」变成「多一份采样开销」，而默认成 CONTENT 只是「少一点质感」。

---

## 6.17 反例清单

| # | 反例 | 违反 | 为什么错 |
|---|---|---|---|
| 1 | `RuntimeShader` 写在 `WedoGlassSurface.kt` 里 | A-1 / `O-48` | API 26 类校验阶段崩 |
| 2 | `ui/glass` import `ui/screen` | A-2 | 玻璃层被业务耦合 |
| 3 | 把 `pushOverlay` lambda 迁出 `MainActivity` | A-3 / `B-07` | `NavigationSkeletonContractTest` 红 |
| 4 | `remember { buildGlassSpec() }` 空 key | A-7 | 主题切换后 tint 不更新，颜色错但不崩 |
| 5 | 每帧调 `buildGlassSpec` | A-6 / `O-69` | 每帧算一次 `isLowRamDevice` |
| 6 | `drawBehind` 里写 `if (dark)` | A-6 / `V-72` | 每帧走分支 |
| 7 | 用 `LaunchedEffect` 减引用计数 | A-9 | 面板消失后计数不减，永远在录 |
| 8 | Host 嵌套超过 2 层还抛异常 | `O-14` | 多包一层就崩，应静默降 G0 |
| 9 | `GlassSnapshot` 允许同时存两张 | `O-85` | 位图泄漏 |
| 10 | 把 `scrollValue`（dp）直接进版本哈希 | A-15 | 120Hz 下每帧重录 |
| 11 | 公开 `GlassPanelTable` 构造器 | §6.4.3 | 有人手工拼面板绕过配额 |
| 12 | `SurfaceClass` 给默认值 | §6.5.1 | 「忘了声明」静默通过 |
| 13 | 把 `WedoAnimatedContent` 写成 `AnimatedContent` 的 `@Composable` 直通 | `M-34` | 减动效失效，包装等于没包 |

---

## 6.18 本卷验收

| 检查项 | 判定 |
|---|---|
| 三条硬约束齐备，各有强制手段 | §6.1 ✅ |
| 现状盘点有真实文件数/行数 | §6.2 ✅（49 文件 / 16,600 行；含 `ui/screen/manage` 空目录） |
| 依赖方向可机械检查，含三条非直观规则的理由 | §6.3.2 / §6.3.3 ✅ |
| 过渡期白名单「只减不增」有断言 | §6.3.4 ✅ |
| 公开 API 恰好 12 项，符合上限 | §6.4.1 ✅（5 + 3 + 2 + 2） |
| 每个 API 有精确签名与参数语义 | §6.5 ✅ |
| 三层令牌的流向与三条规则 | §6.6 ✅ |
| 生命周期用引用计数而非 LaunchedEffect | §6.7.2 / A-9 ✅ |
| 三种缓存的键与失效条件 | §6.8 ✅ |
| API 隔离的「33 对 26 类型不可见」写法 | §6.9.3 / A-17 ✅ |
| 接入成本 ≤ 3 行，有 7 条检查清单 | §6.10 ✅ |
| 状态管理三套归属，含「为什么不进 ViewModel」 | §6.11 ✅ |
| 组件级 vs 公共模块的判据与 5 类留在组件级 | §6.12 ✅ |
| 「第二个使用方才抽公共」的规则与反例 | §6.12.3 / A-24 ✅ |
| 配置三来源，无第四个；三个合法用户偏好 | §6.13 ✅ |
| 加组件/加档/加学校三种扩展预案 | §6.14 ✅ |
| 改参数/加组件/删功能三类维护做法 | §6.15 ✅ |
| 12 个 API 的前置/保证/违规后果矩阵 | §6.16 ✅ |
| 三条跨 API 通用约束（不崩、不依赖顺序、保守默认） | §6.16.5 ✅ |
| 反例 13 条 | §6.17 ✅ |

**遗留问题**：

1. **A-17 的「类型不可见」写法需要编译验证**。理论上把 33 侧返回 `Any?` 可以避免符号引用，但 Kotlin 的 `@RequiresApi` 检查与 R8 的行为需实测（`VR-18`）。
2. **`GlassDependencyContractTest` 的白名单机制**依赖「只减不增」的断言，而该断言本身需要读写一个文件——如果白名单被改成动态生成，断言即失效。→ 白名单必须是**硬编码的 `val` 列表**。
3. **`ui/screen/manage` 空目录**已登记删除（P0），但删空目录不影响编译，容易被忘。→ 卷 07 的 P0 任务里单列一条。
4. 本卷未定义 `GlassQuota` 的**配额仲裁顺序**（设备能力 > 用户偏好 > 运行时配额，`ADR-27`）——它属卷 08 的降级策略，此处只确认接口位置。

---

## 6.19 与既有契约测试的兼容（逐条）

改造会碰到 4 个既有契约测试。下表是「谁会碰、怎么不碰坏」：

| 测试 | 会碰到的改动 | 兼容策略 |
|---|---|---|
| `NavigationSkeletonContractTest:84-102` | 抽 `GlassTabStage` 时可能引入 `AnimatedVisibility` | 转场用 `WedoAnimatedContent`（内部实现**不得**含 `AnimatedVisibility` 字面量，否则 `MainActivity.kt` 里的调用虽合规、但若实现被内联会有风险）→ 该包装类放 `ui/motion`，`MainActivity` 只调用它 |
| `TabBarHideContractTest:64-70,110-116` | 底栏加材质 | 材质走 `drawBehind`，**不动** `graphicsLayer { translationY = size.height * hideFraction }` |
| `ScheduleHeaderLayoutContractTest:126-135,149-156` | 顶栏加 G0 | 只 `drawBehind`，`padding(start = 16.dp, end = 16.dp)` 与 `WedoChip` 高度不变 |
| `ImportWizardWiringTest:85,90` | 向导步骤加 G0 | `AnimatedContent(` 字面量保留；不引入 `Crossfade` |

**一条额外的自保措施**：新增的 `GlassDependencyContractTest` 与 `WedoGlassTokensTest` 属于**新增**，不改既有断言。CI 的测试数只应增加，不应减少（`R-03`）。

---

## 6.20 附录：完整符号表（16 个新文件的类与函数）

§6.9.1 给的是「文件 + 行数」。实施时还需要「文件里有什么」。本节是**唯一的符号清单**，新增符号必须回到这里登记。

### 6.20.1 `ui/glass`（8 文件）

| 文件 | 符号 | 可见性 | 备注 |
|---|---|---|---|
| `GlassCapability.kt` | `GlassCapability` (object) | public | `renderTier(): RenderTier` 单方法 |
| | `RenderTier` (enum) | internal | `L0` / `L1` / `L2` |
| `GlassSpec.kt` | `GlassSpec` (data class) | public | 15 字段，见 §6.5.4 |
| | `SurfaceClass` (enum) | public | `BASE` / `CONTENT` / `FLOAT` |
| | `QuotaGrade` (enum) | public | `Auto` / `G0` / `G1` / `G2` |
| | `buildGlassSpec(...)` | internal | 5 参纯函数 |
| `GlassEnvironment.kt` | `WedoEnvironmentLayer(...)` | public | 唯一公开 composable |
| | `noiseTile()` | private | 64×64 位图生成 |
| `WedoGlassSurface.kt` | `Modifier.wedoGlassSurface(...)` | public | |
| | `Modifier.wedoGlassInset()` | public | |
| | `drawGlass(spec)` | internal | `DrawScope` 扩展 |
| `GlassCapture.kt` | `WedoGlassHost(...)` | public | |
| | `GlassStage` (class) | internal | `acquireLive` / `releaseLive` / `record` |
| | `GlassMode` (enum) | internal | `Bake` / `LiveStrip` / `Hold` |
| | `LocalGlassStage` / `LocalGlassDepth` / `LocalSurfaceClass` | internal | |
| `GlassBlur.android.kt` | `GlassBlurEffect` (object) | internal | **`@RequiresApi(31)`** |
| `GlassShader.android.kt` | `GlassShaderRenderer` (object) | internal | **`@RequiresApi(33)`**，返回 `Any?`（A-17） |
| `GlassSnapshot.kt` | `GlassSnapshot` (class) | public | `captureFrom` / `takeLatest` / `release` |
| `GlassQuota.kt` | `GlassQuota` (object) | internal | `resolve` / `maxG2` |
| `GlassUniforms.kt` | `GlassUniforms` (object) | internal | 顺序数组（`O-44`） |
| `GlassDebug.kt` | `GlassDebug` (object) | internal | 6 个观测字段，**只在 DEBUG 写**（A-33） |

**注意**：`SurfaceClass` 与 `QuotaGrade` 是 public，而 `RenderTier` 是 internal。理由见 §6.4.2——外部要传前两个的值，但不该构造第三个。

### 6.20.2 `ui/motion`（6 文件）

| 文件 | 符号 | 可见性 |
|---|---|---|
| `WedoSprings.kt` | `WedoSprings` (object) | public |
| | 8 个 `val`（`Track` / `Press` / `Settle` / `Jelly` / `Lift` / `Travel` / `Fade` / `FlingSettle`） | public |
| `WedoMotionPolicy.kt` | `WedoMotionPolicy` (object) | public |
| | `resolve(spec)` / `isEnabled` | public |
| `WedoPress.kt` | `Modifier.wedoPress(...)` | public |
| | `WedoPressState` (class) | internal |
| `WedoProgress.kt` | `WedoProgress` (class) | internal |
| `WedoMotion.kt` | `WedoThumbSpring` | public（兼容既有引用） |
| `WedoAnimated.kt` | `WedoAnimatedContent(...)` / `WedoAnimatedVisibility(...)` | public |

### 6.20.3 其他（2 文件）

| 文件 | 符号 | 可见性 |
|---|---|---|
| `ui/settings/WedoSettingsStore.kt` | `WedoSettingsStore` (object) | public |
| | `WedoSettingsState` (data class) | public |
| `ui/component/WedoDialog.kt` | `WedoDialog(...)` | public |

### 6.20.4 符号总数

| 类别 | 数量 |
|---|---|
| public | 22 |
| internal | 17 |
| private | 3（`noiseTile`、`drawGlass` 的内部辅助等） |
| **合计** | **42** |

**对照检查**：§6.4.1 说「公开 API 恰好 12 项」。上表的 public 有 22 个，差异来自——12 项是**入口**（composable / Modifier / 主类型），另外 10 个是它们的**配套**（枚举值、状态类、Spring 常量）。

这不是自相矛盾，但说明「12 项」这个指标**口径不清**。→ 精确定义为：**12 个「调用入口」+ 10 个「配套类型」**。`C-06` 的核对以本表为准。

