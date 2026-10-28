# 05 · 页面与组件逐项改造计划（下：L-13 … L-24）

> 本卷是**执行手册**。每个对象固定 10 小节，与上半卷同模板。
> **本卷永不写设定型数值**——数值一律引用 `V-xx` / `O-xx` / `M-xx` / `P-xx` / `Q-xx`。举证型（描述既有代码尺寸）可以裸写，但须标「当前」。
> 上游：卷 04 §4.3.2（谁负责录制）、§4.3.9（一帧时序）、§4.5（分层与嵌套）
> 前置：`05_components_a.md`（L-01 … L-12）

---

## 5.13 前置：一个贯穿下半卷的结构事实

动笔写 L-14 时发现的问题，必须先单独讲，否则后面每个对象的结论都会错。

### 5.13.1 事实：Sheet / Dialog / Picker 在独立窗口里

Android 上，以下三者在**独立的 Window** 里渲染，不在主 Activity 的窗口内：

| 组件 | 实现 | 是否独立窗口 |
|---|---|---|
| `ModalBottomSheet`（Material3 1.3.0） | `ModalBottomSheetDialog` / `ModalBottomSheetWindow` | **是** |
| `AlertDialog`（Material3） | `BasicAlertDialog` → `Dialog` | **是** |
| `Dialog`（`WeekPicker` 用的） | `androidx.compose.ui.window.Dialog` | **是** |

**后果一**：卷 04 的捕获管线（`GraphicsLayer.record` + `drawWithContent`）**只作用于同一个窗口的图层树**。独立窗口有自己的一棵树，看不到主窗口的图层。

**后果二**：所以「Sheet 上的玻璃实时采到底下页面」这件事，**用 in-window 的方案根本做不到**。不是参数没调对，是结构上不存在这条路径。

**后果三**：`ModalBottomSheet` 自带 `containerColor`（`CourseDetailSheet.kt:94` 传 `surfaceContainerLow`、`ShareScheduleSheet.kt:57` 传 `surface`、`WedoCourseActions.kt:33` 传 `surface`），且自带 scrim。给它加 `Modifier` 会得到「双层底」。

### 5.13.2 唯一可行的替代：跨窗口 Bake（Snapshot）

`O-28` 已经把 `toImageBitmap()` 限定在三个调用点，其中一个是「**Bake 兜底**」。跨窗口玻璃正是它的用武之地：

```
① 主窗口在 Sheet 打开前，对内容区调一次 toImageBitmap()
   ↓（suspend，会分配一张位图）
② 把这张位图作为 Sheet 窗口内玻璃的「背景纹理」
   ↓
③ Sheet 内的玻璃在这张纹理上做 blur sigma + rim + tint
```

这在光学上等价于 `Bake` 模式（`O-10`）：一张静态背景，磨砂与高光照做，只是背景不会随下方页面滚动而变——而 Sheet 打开时下方页面本来就被 scrim 盖住且不可滚动，所以**语义上无损失**。

| ID | 规则 |
|---|---|
| **O-81** | 跨窗口玻璃**只能**用跨窗口 Bake。禁止指望实时采样 |
| **O-82** | 快照必须在**目标窗口出现之前**拍，否则拍到的是「已经盖上 scrim 的画面」 |
| **O-83** | 快照尺寸按 `O-04`（0.5×）降采样，避免每次开面板分配一张全尺寸位图 |
| **O-84** | 快照失败（OOM / 主窗口正被销毁）→ 降 G0。不重试、不阻塞打开 |
| **O-85** | 快照只允许保留**一张**（最近一次）。同时存在两张即泄漏 |

### 5.13.3 预拍窗口：长按给了我们时间

`toImageBitmap()` 是 suspend，最快也要一帧。如果「点开瞬间」才拍，用户会看到：

```
点 → G0（实色）→ 一帧后 → G1（玻璃）   ← 可见的材质跳变
```

**解决办法是利用已有的时间窗**：`WedoCourseActions` 与 `CourseDetailSheet` 都由**长按**触发（`ScheduleScreen.kt:182-185`），而长按本身有 300–500ms 的按住期。在这段期间就可以开始预拍。

| 触发方式 | 预拍时机 | 结果 |
|---|---|---|
| 长按课程块 → `WedoCourseActions` | `onLongClick` 触发的**同一帧**开始预拍 | 面板出现时快照已就绪，无跳变 |
| 长按 / 点击课程块 → `CourseDetailSheet` | 同上 | 同上 |
| 点「分享」→ `ShareScheduleSheet` | 按钮按下时（`wedoPress` 的 pressed = true） | 有一次点击时长可用 |
| 点日期 → `WeekPicker` | 顶栏「第 N 周」按下时 | 同上 |
| `AlertDialog`（删除确认等） | **无预兆** | 只能 G0，或接受一帧跳变 |

| ID | 规则 |
|---|---|
| **O-86** | 有「按住期」的面板，必须在按住时预拍；无按住期的，优先 G0 而不是「先 G0 再跳 G1」 |
| **O-87** | 预拍的触发点放在 `onLongClick` / `pressed` 变化处，**不得**放在 Composable 的 `LaunchedEffect(Unit)`（那是面板已经在开的时刻） |

O-86 的取舍理由：**一次材质跳变比「一直不做玻璃」更糟**。跳变会让用户注意到「这里有东西在变」，而玻璃的价值恰恰是「不引人注意地增加质感」。

### 5.13.4 对下半卷的影响（一次说清，下面各对象不再重复）

| 对象 | 是否跨窗口 | 材质结论 |
|---|---|---|
| L-13 冲突卡 | 否（in-window） | G0 |
| L-14 课程详情弹层 | **是** | G1 跨窗口 Bake（长按预拍） |
| L-15 分享弹层 | **是** | G1 跨窗口 Bake（按下预拍） |
| L-16 课程动作弹层 | **是** | G1 跨窗口 Bake（长按预拍） |
| L-17 周次选择器 | **是** | G1 跨窗口 Bake（按下预拍） |
| L-18 页面容器族 | 否 | 无材质（只做底色去重） |
| L-19 导入向导 | 否（但有独立 `WedoBackground`） | G0 |
| L-20 对话框族 | **是** | **G0**（无预兆，不做跳变） |
| L-21 今日页 | 否 | G0 |
| L-22 设置页 | 否 | G0 |
| L-23 空状态族 | 否 | 无材质 |
| L-24 桌面小组件 | 不适用 | 不改造 |

**这张表最重要的一行是 L-20**：15 个 `AlertDialog` 全部判 G0，不是因为做不到，是因为**做 G1 必然有一次可见跳变**，而对话框出现的频率高（删除、确认），跳变会被反复看到。

→ 这是一次**主动放弃**，不是能力不足。记录在卷 10 的 `R-07`（「能力有但不该用」的风险类型）。

---

## 5.14 L-13 · 冲突卡 `ConflictCard`

### ① 现有功能与调用链

`ui/component/ConflictCard.kt`（981 行，全仓第二大 UI 文件）。用于展示课程时间冲突，支持「折角切换」在冲突的两个课程间来回看。

`foldSwitchHitArea`（`:665-668`、`:749`）是**点击命中区**，不是拖拽手势——这一点容易误判。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 981 行单文件，内部包含折角绘制、冲突条目、切换逻辑、以及**至少 2 处 `AlertDialog`**（与 L-20 重叠） | `:665-668`、`:749` |
| 2 | 无 rim / 无内高光，与 `WedoCourseCard`（L-11 加 G0 后）会形成质感断层 | 全文无 `.border(` 之外的边缘处理 |
| 3 | 折角是纯 `Canvas` 绘制，没有「折起来」的动效——切换是瞬变 | `:665` 附近 |

### ③ 预期效果

冲突卡获得与课程块**同一套** G0 镀层（rim + 内高光 + 极轻阴影），使「正常课」与「冲突课」在质感上同源，只靠红色描边区分语义。

引用：`V-12`、`V-14`、`V-30`、`V-38`/`V-39`（层序）。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止（A 面） | G0 镀层；显示第一个冲突课程 |
| 静止（B 面） | 同上；显示另一个 |
| 按下（折角区） | 命中区内点击切换（**不是拖拽**）；`M-02` |
| 按下（卡片主体） | `wedoPress` → 打开详情 |
| 长按 | 不适用 |
| 拖拽中 | 不适用（无拖拽） |
| 松手落定 | 切换瞬变（当前无动画） |
| 禁用 | 不适用 |
| 滚动中 | 无变化（`M-58`） |
| 减弱动效 | 同现有（切换本就无动画） |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `CONTENT` |
| `QuotaGrade` | **G0 恒定** |
| 降级链 | 与 `RenderTier` 无关 |
| 弹簧 | `M-02`（按压）；折角切换**建议补** `M-12` 的色变（`U-19`） |

### ⑥ 需新增能力

| 能力 | 归属 |
|---|---|
| `wedoGlassSurface(CONTENT, G0)` | 复用 L-11 的同一实现 |

### ⑦ 与其他组件关系

- 与 `L-11`（课程块）共享 `WedoCourseBlockColors`，但**不共享** `V-40` 的「无阴影」条款：
  - 冲突卡同屏数量极少（≤ 2），**可以有** `V-30` 近层阴影（`PATCH-03` 已把 `V-40` 限定为「同屏 > 10」）
- 内含 2 处 `AlertDialog`，归 `L-20` 处置。

### ⑧ 实施顺序依赖

前置：`L-11`（共享 G0 实现）、`L-05`。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 981 行文件，改动面大 | G0 只加在根 `Box` 上，不进入内部各条目 |
| 折角区的命中与卡片点击重叠 | 现有 `foldSwitchHitArea` 已处理；G0 的 `drawBehind` 不影响命中 |
| 深色纯黑 | rim 用 `V-60` |
| 低于 API 31 | G0 三档一致 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-35` | 冲突卡的 rim + 内高光与 `L-11` **参数完全相同**（同一 `GlassSpec` 来源） |
| `Q-36` | 折角区点击仍能切换（命中区未被 G0 的绘制层遮挡） |
| `Q-37` | 冲突卡的近层阴影存在，且不影响 60 块同屏的性能（它与课程块不同屏） |

---

## 5.15 L-14 · 课程详情弹层 `CourseDetailSheet`

### ① 现有功能与调用链

`ui/component/CourseDetailSheet.kt`（327 行）。`ModalBottomSheet`（`:89`）+ `rememberModalBottomSheetState(skipPartiallyExpanded = true)`（`:76`）+ `containerColor = surfaceContainerLow`（`:94`）。

**跨窗口**（§5.13.1）。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 实色 `surfaceContainerLow`，完全遮住下方内容——打开时「页面消失」感强 | `:94` |
| 2 | 跨窗口 → 无法实时采样（§5.13.1 后果二） | — |
| 3 | `skipPartiallyExpanded = true` 意味着一次到位，没有中间态可利用来预拍 | `:76` |

### ③ 预期效果

打开时，sheet 面板上是**模糊的下方页面**而不是一块灰：能隐约看出课程表的色块分布，磨砂程度按 `O-30` 的 G1 档。

关键：**背景是打开前那一瞬的快照**，不是实时的。因为 sheet 打开时下方已被 scrim 盖住且不可交互，所以「快照 = 用户看到的背景」在语义上成立。

引用：`O-81`..`O-87`、`V-11`、`V-12`、`V-30`。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止（已展开） | G1 跨窗口 Bake；rim + tint |
| 按下（内容区按钮） | `M-02` |
| 长按 | 不适用 |
| 拖拽中（下拉关闭） | **Hold**（`O-12`）：沿已知路径移动不重拍 |
| 松手落定（关闭） | 释放快照（`O-65`） |
| 松手落定（回弹） | 补一次 Bake（`O-12`） |
| 禁用 | 不适用 |
| 选中 | 不适用 |
| 滚动中（sheet 内部滚动） | 背景不变（快照是静态的，恰好正确） |
| 减弱动效 | `M-33` 的 S3（只淡变）；材质不降级（`M-39`） |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `FLOAT`·大面 |
| `QuotaGrade` | **G1**（Bake，非 LiveStrip） |
| 降级链 | G1 快照 → 快照失败则 G0（`O-84`）→ 无 `RenderTier` 依赖（L0 也能做 G0） |
| 捕获模式 | **Bake（快照）**；下拉关闭期间 **Hold** |
| 弹簧 | 开合走 `ModalBottomSheet` 自带；内容淡入 `M-20` 单进度 |
| 嵌套深度 | **2**（页面级 Host → 浮层 Host），`O-14` 的上界 |

### ⑥ 需新增能力

| 能力 | 归属 |
|---|---|
| `GlassSnapshot.take()` —— 主窗口快照，suspend，单张 | `ui/glass/GlassSnapshot.kt` |
| 预拍触发点（`onLongClick` 时调） | `ui/screen/schedule/ScheduleScreen.kt` 的调用侧 |
| 跨窗口玻璃的 `RenderEffect` 应用（sheet 窗口内） | `ui/glass/WedoGlassSurface.kt` 的 cross-window 变体 |

### ⑦ 与其他组件关系

- **不读** `LocalGlassStage`（那是主窗口的 Host）——它读 `GlassSnapshot`。
- 与 `L-16`（`WedoCourseActions`）**不得同时存在**（一个长按只能触发一个；当前 `ScheduleScreen.kt:182-185` 是 actionCourse 优先）。
- 内部可弹 `L-20` 的 `AlertDialog`（删除确认）——那是**深度 3**，违反 `O-14`。
  → **处置**：`AlertDialog` 读 Level 0（主窗口快照），**不读** sheet 的结果。这正是卷 04 §4.5.3 预判的场景，此处落实。

### ⑧ 实施顺序依赖

前置：`L-11`（课程块，决定长按语义）、`L-05`。是 `L-20` 的前置（快照机制先建）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 快照 OOM | 降 G0（`O-84`），不阻塞 sheet 打开 |
| 长按到 sheet 出现的时间 < 一帧 | 极快松手的情况；此时快照未就绪 → G0，且**不升级**（`O-86`） |
| 屏幕旋转期间打开 | 快照尺寸失配 → 丢弃，降 G0 |
| 深色纯黑 | 快照本身是深色，blur 后几乎为黑 → tint 用 `V-62`，rim 用 `V-61` |
| 低于 API 31 | 无 `RenderEffect` → 快照位图直接以低 alpha 叠在 tint 下（近似磨砂），或降 G0 |
| 内存 | 快照只保留一张（`O-85`），sheet 关闭即释放 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-54` | 从长按到 sheet 完全展开的录屏中，**无可见材质跳变**（`O-86` 的预拍生效） |
| `Q-55` | sheet 内能辨认出下方课程表的色块分布（截图，磨砂后仍可辨） |
| `Q-56` | sheet 关闭后 `GlassSnapshot` 已释放（无位图泄漏） |
| `Q-57` | sheet 内弹 `AlertDialog` 时，dialog 读 Level 0 而非 sheet 结果（无三重叠加） |

---

## 5.16 L-15 · 分享弹层 `ShareScheduleSheet`

### ① 现有功能与调用链

`ui/component/ShareScheduleSheet.kt`（151 行）。`ModalBottomSheet`（`:54`）+ `containerColor = colors.surface`（`:57`）。**跨窗口**。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 实色 `surface`，比 `CourseDetailSheet` 更亮，与页面背景反差更大 | `:57` |
| 2 | 跨窗口，同 L-14 | — |
| 3 | 触发是**点击**（顶栏分享按钮）不是长按 → 预拍窗口短 | `WedoWeekHeader.kt:104` |

### ③ 预期效果

同 L-14，但背景快照是「课表页当前周」的画面。因为课表页是信息最密集的一页，磨砂后能看出网格与色块，观感收益最大。

引用：同 L-14。

### ④ 9 种状态

同 `L-14`（同为 `ModalBottomSheet`，状态机一致）。差异：无内部滚动，所以「滚动中」态不适用。

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `FLOAT`·大面 |
| `QuotaGrade` | **G1**（跨窗口 Bake） |
| 降级链 | 同 L-14 |
| 弹簧 | 开合自带；内容 `M-20` |

### ⑥ 需新增能力

同 L-14（复用 `GlassSnapshot`）。

### ⑦ 与其他组件关系

- 由 `L-03`（顶栏）的分享按钮触发 → 预拍挂在 `WedoIconButton` 的 `pressed` 上（`O-87`）。
- 与 `L-14`/`L-16` 互斥（同时只可能有一个 sheet）。

### ⑧ 实施顺序依赖

前置：`L-14`（快照机制先建）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 点击到出现的时间极短（< 1 帧） | 预拍挂在 `pressed = true` 上，利用按压期；若仍未就绪 → G0（`O-86`） |
| 分享内容含课程表图片生成 | 与本对象无关（那是导出逻辑），但**不得**把生成的图片当作快照背景 |
| 其余同 L-14 | — |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-58` | 从点击到 sheet 展开，无可见材质跳变 |
| `Q-59` | 快照内容与「点击时刻的课表页」一致（截图比对，允许 blur 后差异） |

---

## 5.17 L-16 · 课程动作弹层 `WedoCourseActions`

### ① 现有功能与调用链

`ui/screen/schedule/WedoCourseActions.kt`（63 行，下半卷最小对象）。`ModalBottomSheet(onDismissRequest, containerColor = surface)`（`:33`）。由长按课程块触发（`ScheduleScreen.kt:184`），**同时给触觉** `LongPress`。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 63 行、无独立状态、无动画——但它是**长按的第一个落点**，用户感知频率高 | `:33` |
| 2 | 实色 `surface`，与背后课表完全断开 | `:33` |
| 3 | 它出现时**同时**有触觉与 sheet，如果材质做跳变，会被绑定在「长按」这个动作记忆上 | `ScheduleScreen.kt:184` |

### ③ 预期效果

同 L-14，但更重要：**长按 → 触觉 → sheet 展开 → 背景是模糊的课表**，这条链路的时间窗最长（长按 300–500ms），预拍最可靠，是这个改造里「最容易做对」的一个。

引用：`O-86`、`O-87`、`V-11`、`V-12`。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止（展开） | G1 跨窗口 Bake |
| 按下（动作项） | `M-02` |
| 长按 | 不适用（它本身就是长按的结果） |
| 下拉关闭 | Hold |
| 松手落定 | 释放快照 |
| 其余 | 同 L-14 |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `FLOAT`·大面 |
| `QuotaGrade` | **G1**（不是 G2——它是被动出现的面板，不需要每帧重拍） |
| 降级链 | 同 L-14 |
| 弹簧 | 开合自带 |

### ⑥ 需新增能力

同 L-14。

### ⑦ 与其他组件关系

- 与 `L-11` 的 `onLongClick` 直接耦合（`ScheduleScreen.kt:184`）。
- 与 `L-14` 的触发互斥：长按先弹 actions，点「详情」才进 `CourseDetailSheet`。

### ⑧ 实施顺序依赖

前置：`L-14`（快照机制）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 长按后立刻松手（< 100ms） | sheet 可能还没出现；预拍已开始，快照仍然有效（快照的是背景，不是 sheet） |
| 触觉与材质同时变化 | 触觉在 `onLongClick` 同帧，材质在 sheet 展开后 → 时间上错开，不冲突 |
| 其余同 L-14 | — |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-60` | 长按 → sheet 展开全过程无材质跳变（这是全项目最可靠的预拍场景，若这条都不过，说明快照机制有问题） |

---

## 5.18 L-17 · 周次选择器 `WeekPicker`

### ① 现有功能与调用链

`ui/screen/schedule/WeekPicker.kt`（221 行）。**全屏 `Dialog`**（`:79-82`）+ `Surface(Modifier.fillMaxSize(), color = colors.background)`（`:83`）。内容：标题栏 + 学期进度条 + `LazyVerticalGrid(GridCells.Adaptive(60.dp))` + `WeekChip`。

**跨窗口**。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | **`Surface(fillMaxSize, color = background)` 把整屏涂成不透明**——这是「玻璃采到纯色」的根源 | `:83` |
| 2 | 打开/关闭是**瞬变**（`ScheduleScreen.kt:219-227` 的 `if (showWeekPicker)`），无转场 | `ScheduleScreen.kt:219` |
| 3 | `WeekChip` 选中态**直接换色无动画** | `:159-196` |
| 4 | `SemesterProgressBar` 纯两个 Box，**无进度动画** | `:205-221` |

### ③ 预期效果

整屏不再是「一块灰盖住课表」，而是**透过磨砂看到模糊的课表**——20 周的选择器占了整屏，如果能看见底下的课表，用户的空间感会强得多。

引用：`O-81`..`O-87`、`V-11`、`V-12`、`V-52`（内容内缩）。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止 | G1 跨窗口 Bake；网格浮在磨砂上 |
| 按下（`WeekChip`） | **无反馈**（当前）；建议补 `M-02`（`U-20`） |
| 长按 | 不适用 |
| 拖拽中（网格滚动） | 背景不变（快照静态，正确） |
| 松手落定 | — |
| 禁用 | 不适用（所有周都可选） |
| 选中 | `WeekChip` 换色（当前瞬变，建议 `M-12`） |
| 滚动中 | 同「拖拽中」 |
| 减弱动效 | `M-33`；材质不降级 |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `FLOAT`·大面（全屏） |
| `QuotaGrade` | **G1**（跨窗口 Bake） |
| 降级链 | G1 → G0（快照失败）→ 实色 |
| 捕获模式 | Bake（快照）；全屏面板，无 Hold 需求 |
| 弹簧 | 打开 `M-07`（淡入）；`WeekChip` 选中 `M-12` |

### ⑥ 需新增能力

| 能力 | 归属 |
|---|---|
| `GlassSnapshot`（复用 L-14） | `ui/glass/GlassSnapshot.kt` |
| **`Surface(color = background)` 的移除/改透明** | 本文件 `:83` |

### ⑦ 与其他组件关系

- 由 `L-03` 顶栏的「第 N 周」区域触发。
- 它本身是全屏 `FLOAT`，内部**不得**再嵌套任何 `FLOAT`（`O-14` 的深度 2 已满）。
- `WeekChip` 与 `L-08`（`WedoChip`）**不是同一个组件**——`WeekChip` 是 `WeekPicker` 私有，不可混用。

### ⑧ 实施顺序依赖

前置：`L-14`（快照机制）、`L-03`（触发点）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| **必须先删 `Surface`** | 顺序错了会「调参数三天发现没用」（§5.13 的核心警告） |
| 全屏快照位图较大 | 按 `O-04` 降采样；全屏 + 0.5× 在 1080p 上是 540×1200，内存可接受 |
| 20 周网格需滚动 | 磨砂是静态的，滚动网格时背景不动——**这是正确的**（快照语义） |
| 深色纯黑 | 快照几乎全黑 → 靠 rim 与 `WeekChip` 的对比度建立层级 |
| 系统返回键 | `Dialog` 自带 `onDismissRequest`，不影响 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-61` | `Surface(color = background)` 已移除或改透明（文本契约测试） |
| `Q-62` | 打开时能透过磨砂看到模糊的课表（截图） |
| `Q-63` | 20 周网格滚动时背景不动、不闪烁（录屏 3 秒） |

---

## 5.19 L-18 · 页面容器族（11 个 `Scaffold` + 7 处显式底色）

### ① 现有功能与调用链

**这是本卷事实修正最多的一节。** 原稿写「12 个二级页各自 `Scaffold(containerColor = colors.background)`」，**经逐行核实是错的**。

真实情况：底色由**三种机制**铺设，合计 **18 处**：

| 机制 | 处数 | 位置 |
|---|---|---|
| `Scaffold(` 使用默认 `containerColor` | 10 | `AddCourseScreen.kt:451`、`AllTablesScreen.kt:63`、`AppearanceScreen.kt:93`、`EditTableScreen.kt:149`、`ExportScreen.kt:120`、`GeneralSettingsScreen.kt:105`、`HolidaySettingsScreen.kt:162`、`LicenseScreen.kt:79`、`ReminderScreen.kt:149`、`WidgetEditScreen.kt:60` |
| `Scaffold(containerColor = colors.background)` | 1 | `EditTableScreen.kt:97` |
| `Modifier.fillMaxSize().background(colors.background)` | 7 | `JwImportActivity.kt:415`、`ExportScreen.kt:121`、`GeneralSettingsScreen.kt:106`、`HolidaySettingsScreen.kt:163`、`ReminderScreen.kt:150`、`TodayScreen.kt:133`、`TodayScreen.kt:318` |

外加 **2 处 `WedoBackground`**：`MainActivity.kt:451`（主入口）与 `ImportWizard.kt:226`（导入向导自己又包了一层）。

**关键机制**：`Theme.kt:476` 有 `MaterialTheme(colorScheme = m3Scheme)`。所以 `Scaffold()` 的默认 `containerColor` = `m3Scheme.background`，**同样是不透明底色**。也就是说「没写 containerColor」不等于「没涂底色」。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 18 处不透明底色，任何落在二级页上的玻璃都只能采到纯色 | 上表 |
| 2 | 有些页面**双重铺设**：`Scaffold` 一次 + `modifier.background` 一次（如 `ExportScreen.kt:120` 与 `:121`） | 同左 |
| 3 | `TodayScreen.kt:133` 与 `:318` 两处，说明同一页内有两层容器各涂一次 | 同左 |
| 4 | `ImportWizard.kt:226` 有自己的 `WedoBackground` → 环境纹理会出现**两份**，且切换时可能不一致 | `:226` |

### ③ 预期效果

**18 处全部改为透明，让唯一的底色来自 `MainActivity` 的 `WedoBackground`（`L-01`）。**

视觉上没有任何变化（因为 `WedoBackground` 本来就是同一个颜色）——但**结构上**，从此任何二级页上的浮层都能采到环境纹理而不是纯色。

这是「看起来什么都没变，但后面所有事都能成立」的典型改动。

引用：`R-BG-1`..`R-BG-4`（卷 02 §2.2.3）、`V-01`..`V-04`。

### ④ 9 种状态

不适用（容器无状态）。唯一需要保证的：**透明之后不得出现「透出下层页面」的情况**——因为 `WedoBackground` 在最底层，透明只会露出它，不会露出别的页面。

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | 容器本身**不是** `SurfaceClass` 的成员（它是结构，不是表面） |
| `QuotaGrade` | 无材质 |
| 弹簧 | 无 |

### ⑥ 需新增能力

无新增 API，只有**删除**。

### ⑦ 与其他组件关系

- 它是 `L-01` 成立的前提：`L-01` 提供纹理，`L-18` 负责不要盖住它。
- `ImportWizard.kt:226` 的第二个 `WedoBackground` **必须保留**——因为导入向导在 `JwImportActivity` 里，可能没有 `MainActivity` 的 `WedoBackground` 包着。但要确保它提供的是同一个 `L-01` 环境层（不是另一套参数）。

### ⑧ 实施顺序依赖

前置：`L-01`（先有环境纹理，再拆覆盖层，否则中间状态是「露出纯黑」）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 某页面**没有**被 `WedoBackground` 包住 | 移除底色后会露出 `#000000` 或白。**必须逐页确认包裹关系**，不能批量替换 |
| `Scaffold` 的 `containerColor` 改为 `Color.Transparent` 后，`contentWindowInsets` 行为不变 | `Scaffold` 的 insets 与底色无关，安全 |
| `JwImportActivity` 是独立 Activity | 它不在 `MainActivity` 的 `WedoBackground` 内 → **必须保留**自己的 `WedoBackground`（`:415` 的 `.background` 可改，但不能没有底） |
| 深色纯黑 | 同上，逐页确认 |
| 动画期间（页面切换） | 透明底在转场中可能露出黑底一帧 → 因为当前无页面转场（`NG-03` 相关），不成问题 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-64` | 18 处底色已处理：10 处 `Scaffold` 加 `containerColor = Color.Transparent`、1 处改值、7 处 `.background()` 删除 |
| `Q-65` | 逐页截图与改造前**像素级一致**（因为颜色本就相同） |
| `Q-66` | `ImportWizard` 与 `JwImportActivity` 仍有正确底色（不露出纯黑） |
| `Q-67` | 二级页上的浮层玻璃能采到环境纹理（`debugLastCapture != null`） |

---

## 5.20 L-19 · 导入向导 `ImportWizard`

### ① 现有功能与调用链

`ui/screen/imports/ImportWizard.kt`（`:103` 起）。4 步流程（SOURCE / SOURCE_DETAIL / PREVIEW / DONE），步骤间用 `AnimatedContent` + `tween(300)` 水平滑动（`:241-251`），KDoc 明确「刻意不加淡入淡出」。

根容器：`WedoBackground(Modifier.fillMaxSize())`（`:226`）——**它自己包了一层**。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 自己包 `WedoBackground`，与 `MainActivity` 的那层重复 → `L-01` 的纹理会有两份 | `:226` |
| 2 | 步骤切换是水平滑动，但面板无材质 → 滑动时两个步骤都贴在同一个平面上，空间感弱 | `:241-251` |
| 3 | `AnimatedContent(` 必须存在（`ImportWizardWiringTest:85` 断言），且禁 `Crossfade`（`:90`）——转场方案被锁死 | 同左 |

### ③ 预期效果

**向导内的步骤卡片用 G0**（rim + 内高光），使「当前步骤浮在上一个步骤之上」有质感依据。容器本身无材质（它已经是 `WedoBackground`）。

引用：`V-12`、`V-14`、`V-30`。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止（任一步骤） | 步骤内容 G0 |
| 按下（按钮） | `M-02` |
| 长按 | 不适用 |
| 拖拽中 | 不适用（无手势，只有步骤按钮） |
| 松手落定 | 步骤切换 `tween` `M-10` |
| 禁用 | 加载中（`isLoading`）时按钮禁用 |
| 选中 | 步骤指示器（如有） |
| 滚动中 | 各步骤内部滚动，容器不动 |
| 减弱动效 | `M-33` → S3（只淡变，`M-34`）；但 `AnimatedContent` 的 `transitionSpec` 要支持退化 |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | 步骤卡片 = `CONTENT`；容器 = BASE（`WedoBackground`） |
| `QuotaGrade` | **G0**（步骤卡片） |
| 降级链 | 与 `RenderTier` 无关 |
| 弹簧 | 步骤切换 `M-10`（`tween` 300ms，**保留现有值**） |

### ⑥ 需新增能力

| 能力 | 归属 |
|---|---|
| `AnimatedContent` 的减动效退化包装 | `ui/motion/WedoMotionPolicy.kt`（`M-34` 的扩展） |
| `WedoBackground` 的去重（与 `L-18` 同批） | `ImportWizard.kt:226` |

### ⑦ 与其他组件关系

- 它在 `JwImportActivity` 或 `MainActivity` 的 overlay 中（`OverlayScreen.Import`），两者都有 `WedoBackground` 祖先 → 它自己那层**是冗余的**，但不能直接删（要确认 Activity 路径）。
- 与 `L-17`（`WeekPicker`）无关系——不要因为「都是全屏」就混为一谈（`WeekPicker` 是跨窗口 `Dialog`，向导是 in-window）。

### ⑧ 实施顺序依赖

前置：`L-01`、`L-18`（底色去重同批做）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 在 `JwImportActivity` 里 | 那个 Activity 自己也有底色（`:415`），需确认层级 |
| 步骤切换中的两帧重叠 | `AnimatedContent` 默认同时存在前后两帧 → G0 的 rim 会短暂出现两条边。**可接受**（滑动方向明确，不会误读） |
| 加载中 | 有 `isLoading` 状态（`:220`），按钮禁用；材质不变 |
| 深色纯黑 | G0 的 tint 用 `V-62` |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-68` | `ImportWizardWiringTest:85` 的 `AnimatedContent(` 断言仍绿，`:90` 的 `Crossfade` 禁令未被违反 |
| `Q-69` | 向导内只有**一份**环境纹理（不重复叠加） |
| `Q-70` | 四步流程功能回归（导入链路不得因材质改造失败——这是 `B-03` 的间接要求） |

---

## 5.21 L-20 · 对话框族（15 处 `AlertDialog`）

### ① 现有功能与调用链

15 处 `AlertDialog(`，分布在 10 个文件：

`ConflictCard.kt`、`DateTimePickers.kt`、`AddCourseScreen.kt`、`EditTableScreen.kt`、`ExportScreen.kt`、`HolidaySettingsScreen.kt`、`UpdateChangelogDialog.kt`、`WedoSettingsScreen.kt`、`WedoCourseActions.kt`、`WedoPrivacyConsent.kt`。

**全部跨窗口**（§5.13.1）。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 15 处各自写 `AlertDialog`，样式靠 Material 默认 + 局部参数，**无统一入口** | 10 个文件 |
| 2 | 跨窗口 → 无法实时采样 | §5.13.1 |
| 3 | 触发无预兆（点击即弹）→ 无预拍窗口 | `O-86` |
| 4 | 部分对话框承重业务（删除确认、隐私同意），材质改动风险高于视觉收益 | `WedoPrivacyConsent.kt` |

### ③ 预期效果

**全部 G0，不做采样。**

理由链（这是本卷唯一一次「主动放弃」）：
1. 跨窗口 → 只能用快照。
2. 无预兆 → 快照必然滞后 → **一次可见的材质跳变**。
3. 对话框出现频率高（每次删除都弹）→ 跳变被反复看到。
4. 而对话框面积小、下方被 scrim 盖住 → 玻璃的收益本来就低。

**权衡结论**：`O-86` 的「一次跳变比不做更糟」在这里直接适用 → 全 G0。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止 | G0 镀层（rim + 内高光）；`containerColor` 改为透明 + 内部 G0 |
| 按下（按钮） | Material 默认或 `M-02` |
| 长按 | 不适用 |
| 出现 / 消失 | 平台 Dialog 动画（**不改**） |
| 滚动中 | 不适用（对话框内容不滚动） |
| 减弱动效 | 平台 Dialog 动画不随 App 开关（系统级）→ **待确认**（`U-21`） |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `FLOAT`·大面 |
| `QuotaGrade` | **G0 恒定** |
| 降级链 | 与 `RenderTier` 无关 |
| 弹簧 | 无（出现/消失由平台控制） |

### ⑥ 需新增能力

| 能力 | 归属 |
|---|---|
| **统一的 `WedoDialog` 包装**（替换 15 处 `AlertDialog`，提供 G0 底 + 一致的圆角/描边） | `ui/component/WedoDialog.kt` |
| `AlertDialog` 的 `containerColor` 替换策略（不是加 Modifier） | 同上 |

**为什么必须统一包装**：15 处各自改，会出现 15 种细微差异（圆角不一致、rim 深浅不一）。这是 `C-08`「禁止第二套实现」在对话框上的具体化。

### ⑦ 与其他组件关系

- `ConflictCard.kt` 内的 2 处归本对象，不归 `L-13`。
- `WedoCourseActions.kt` 内的 1 处归本对象，不归 `L-16`。
- **深度问题**：`CourseDetailSheet`（L-14，深度 2）内弹 Dialog → 若 Dialog 用快照，会读 Level 0（主窗口），不会形成深度 3。因为 G0 不采样，这个问题**自动消失**。这是选 G0 的另一个隐藏收益。

### ⑧ 实施顺序依赖

前置：`L-01`（G0 的 tint 基准）。**不依赖** `L-14`（因为不用快照）。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 15 处逐一替换的风险 | 分批：先 `WedoPrivacyConsent`（独立）→ 再 `AddCourseScreen`（最复杂）→ 其余 |
| `AlertDialog` 自带 `shape` 与 `tonalElevation` | 替换时要显式给 `shape`（用 `WedoAppleShapes.card`）与 `tonalElevation = 0.dp`，否则有 Material 的抬高色 |
| 深色纯黑 | G0 的 tint 用 `V-62`；对话框底色用 `surfaceContainerHigh` 而非 `surface`（纯黑上需要更高的分离度） |
| 低于 API 31 | G0 三档一致 |
| 业务承重（隐私同意） | 该处**只改底，不改任何布局与文案**；改动前先读 `WedoPrivacyConsent.kt` 全文 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-71` | 15 处全部经 `WedoDialog` 包装（文本契约：`AlertDialog(` 不再直接出现在业务文件中） |
| `Q-72` | 每个对话框的圆角、rim、内高光**参数一致**（同一 `GlassSpec`） |
| `Q-73` | `WedoPrivacyConsent` 的同意/拒绝流程功能回归（不得因改底影响业务） |
| `Q-74` | 对话框内**无**材质跳变（因为从未尝试升级到 G1） |

---

## 5.22 L-21 · 今日页 `TodayScreen`

### ① 现有功能与调用链

`ui/screen/today/TodayScreen.kt`。时间轴式列表。**两层容器各涂一次底色**（`:133` 与 `:318`）。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 两层 `.background(WedoTheme.colors.background)` → 覆盖两次 | `:133`、`:318` |
| 2 | 时间轴条目为纯色，无 rim / 内高光 | 全文无 `.border(` |
| 3 | 与课表页（`L-11` 有 G0 后）质感不一致 | — |

### ③ 预期效果

时间轴条目获得 G0 镀层，与课程块同源。页面底色去重（归 `L-18`）。

引用：`V-12`、`V-14`、`V-30`。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止 | 条目 G0；「当前时段」条目不额外高亮（`U-22`） |
| 按下（条目） | `wedoPress` → 打开详情 |
| 长按 | 同 |
| 其余 | 同 `L-11` |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `CONTENT` |
| `QuotaGrade` | **G0 恒定** |
| 弹簧 | `M-02` |

### ⑥ 需新增能力

复用 `wedoGlassSurface(CONTENT, G0)`。

### ⑦ 与其他组件关系

- 与 `L-11` 应当共享同一 `GlassSpec`（同为「内容条目」）。
- 底色去重归 `L-18`。

### ⑧ 实施顺序依赖

前置：`L-11`（先定「内容条目」的 G0 参数）、`L-18`。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 时间轴条目数量 | 一天最多 ~12 条，同屏 ≤ 12 → **可以用近层阴影**（`V-40` 的 10 条界限附近，按「≤12 可接受」处理，`PATCH-04`） |
| 空状态 | 归 `L-23` |
| 深色纯黑 | 同 `L-11` |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-75` | 今日页条目与课表课程块的 rim / 内高光参数一致 |
| `Q-76` | 页面底色只涂一次（文本契约） |

---

## 5.23 L-22 · 设置页族 `WedoSettingsScreen` 与 8 个二级设置页

### ① 现有功能与调用链

`ui/screen/mine/` 下：`WedoSettingsScreen`、`AppearanceScreen`、`GeneralSettingsScreen`、`AllTablesScreen`、`EditTableScreen`、`ExportScreen`、`LicenseScreen`、`ReminderScreen`、`HolidaySettingsScreen`、`UpdateChangelogDialog`。

其中 6 个用 `Scaffold`（底色归 `L-18`），4 个额外用 `modifier.background`。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 底色重复铺设（同 `L-18`） | 见 `L-18` ① 的表 |
| 2 | 设置分组卡已归 `L-10`（G0），但页面内的**列表行**无镀层 → 卡内卡外的质感不一致 | `SettingsCards.kt` vs 各 `*Screen.kt` |
| 3 | `AppearanceScreen.kt:137-155` 是唯一有「调色器」的页面，P0 要在这里加三个开关（画质/动效/触觉），当前无位置 | `:137-155` |

### ③ 预期效果

1. 列表行**不加镀层**——它们嵌在 `L-10` 的分组卡里，卡有 G0，行再加就重复了。
2. `AppearanceScreen` 增加「材质」分组（画质三选一 + 动效 + 触觉）。

引用：`D6`、`V-12`、`V-14`。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止（行） | 无独立材质（父卡有） |
| 按下（行） | `wedoPress`（`U-16` 待定是否采纳） |
| 选中（如主题选中） | accent |
| 其余 | 不适用 |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | 行 = `CONTENT`（继承自 `L-10` 的卡） |
| `QuotaGrade` | 行**无材质** |
| 弹簧 | `M-02` |

### ⑥ 需新增能力

| 能力 | 归属 |
|---|---|
| `WedoSettingsStore`（统一写入口，`D6`） | `ui/settings/WedoSettingsStore.kt` |
| 「材质」设置分组 UI | `AppearanceScreen.kt` |
| 14 个新 `strings.xml` key × 6 locale | `values*/strings.xml` |

### ⑦ 与其他组件关系

- `L-10` 是它的容器，`L-09`/`L-06` 是它的子元素。
- 它是 `D6`（三个死开关）的落点，也是 `Q-02`（无新增死开关）的主战场。

### ⑧ 实施顺序依赖

前置：`L-10`、`L-09`。**是 P0 的一部分，必须先于所有材质工作完成。**

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 6 locale 文案 | `localeFilters` 只打包 `zh-rCN`，但 `StringsKeyParityTest` 要求 6 套对齐 → 必须补 6 份（`U-04`） |
| `quality` 默认档 | 现为 `"balanced"`（`U-02`） |
| 开关的持久化 | 必须走 `WedoSettingsStore`，不得再出现「有 key 无写入口」 |
| 深色纯黑 | 分组卡 `#1C1C1E` 在 `#000000` 上已足够区分 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-02` | 三个开关均有生产消费方，且被单测断言（`G-08`） |
| `Q-77` | 6 个 locale 的 key 齐备（`StringsKeyParityTest` 扩展后全绿） |
| `Q-78` | 切换画质档后重启，设置保持（持久化正确） |

---

## 5.24 L-23 · 空状态族

### ① 现有功能与调用链

`ScheduleScreen.kt:103-117`（课表空状态）、`:188`（本周无课）、`AllTablesScreen` 等。结构：图标（`hairline` alpha）+ 标题 + 提示 + `WedoPrimaryButton`。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | 无容器、无材质，纯文字浮在背景上 | `:103-117` |
| 2 | 与「有内容时」的视觉密度落差大 | — |

### ③ 预期效果

**不加材质。** 空状态是「留白」，加材质等于把留白填满，与 `V2-02`（层级靠留白）矛盾。

唯一建议：图标 alpha 用 `WedoTheme.Alpha.hairline`（现有）保持不变。

引用：`V2-02`、`ADR-18`。

### ④ 9 种状态

| 状态 | 行为 |
|---|---|
| 静止 | 无材质 |
| 按下（CTA 按钮） | `M-02`（归 `L-07`） |
| 其余 | 不适用 |

### ⑤ 材质与动画

| 项 | 值 |
|---|---|
| `SurfaceClass` | `CONTENT` |
| `QuotaGrade` | **无材质** |
| 弹簧 | 无 |

### ⑥ 需新增能力

无。**本对象只做确认。**

### ⑦ 与其他组件关系

- `L-07`（主按钮）不加材质，与本对象一致。
- 它是「无材质也是设计」的例证。

### ⑧ 实施顺序依赖

无。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 超长提示文案 | 现有 `padding(vertical = 12.dp)` + 默认换行，不改 |
| 深色纯黑 | 不适用 |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-79` | 空状态**无**材质（截图确认无 rim、无 tint） |

---

## 5.25 L-24 · 桌面小组件（**明确不改造**）

### ① 现有功能与调用链

`widget/`：`TodayWidgetReceiver`、`TodaySmallWidgetReceiver`、`WeekGridWidgetProvider`、`WeekGridSmallWidgetProvider`（4 个 receiver，**全部 `enabled="true"`**，见 `00_MASTER` §0.3 的 F-04 更正）。

渲染走 `RemoteViews` + `RemoteViewsWidgetHelper`（`:38/45` 有 `TIRAMISU`/`S` 的模糊分支）。

### ② 现存问题

| # | 问题 | 证据 |
|---|---|---|
| 1 | RemoteViews 只能声明系统提供的 View，**不能承载 Compose** | `widget/` 全部 |
| 2 | `RemoteViewsWidgetHelper.kt:38/45` 的模糊分支只影响根布局的背景，不是玻璃 | 同左 |
| 3 | 课程色用 `groupId`，与 App 内的 `courseName` 不一致（`F-08`） | `CourseColorUtil.kt:169` |

### ③ 预期效果

**不改造。** 作为反例登记：说明「全面玻璃化」的边界到哪为止。

如果一定要提升质感，可行路径是「在 App 内渲染一张位图 → `setImageViewBitmap`」，但那是**静态图**，与材质无关，且需要长周期的自定义渲染管线 —— 明确列入 `NG-01`。

### ④ 9 种状态

不适用（RemoteViews 无交互状态机，只有点击跳转）。

### ⑤ 材质与动画

不适用。

### ⑥ 需新增能力

无。

### ⑦ 与其他组件关系

- 它通过 `CourseColorUtil` 与 App 内的课程色共享色板，但**种子不同**（`F-08` / `U-05`）。
- 与 `L-01` 的 `WedoBackground` 无关系（不同进程/不同渲染器）。

### ⑧ 实施顺序依赖

无。

### ⑨ 异常与边界

| 边界 | 处理 |
|---|---|
| 用户会不会觉得「App 变了但小组件没变」 | 会。这是 `R-07`（「能力边界不一致」风险）的具体表现，需在发布说明里讲清 |
| `U-05`（色种统一）若不修，玻璃化后 App 内色更饱和，组件的色差会更明显 | 建议把 `U-05` 提前到 P1（卷 07） |

### ⑩ 验收标准

| Q-ID | 内容 |
|---|---|
| `Q-80` | 4 个 receiver 的 `enabled` 状态与改造前**一致**（`ReminderWiringContractTest:42-53` 保持绿） |
| `Q-81` | `widget/` 目录内**无**任何 `wedoGlassSurface` 引用（文本契约） |

---

## 5.26 下半卷自检

| 检查项 | 判定 |
|---|---|
| 12 个对象齐备，每个 10 小节 | ✅ 逐对象核对（L-13 … L-24） |
| 每个「现存问题」至少 1 处带 `文件:行号` | ✅ 全部满足 |
| 材质判定与卷 01 分配表一致 | ✅ 见 §5.13.4 汇总表 |
| 每个对象标注降级链 | ✅ |
| 每个对象指向 ≥1 个 Q-ID | ✅ |
| 不写设定型数值 | ✅（举证型已标「当前」） |

### 本卷的三条事实修正（都已回写）

1. **`Scaffold` 默认也涂底色**。`Theme.kt:476` 有 `MaterialTheme(colorScheme = m3Scheme)`，所以「没写 `containerColor`」≠「没涂底色」。真实是 **18 处**（10 默认 Scaffold + 1 显式 + 7 显式 `background()`），不是原稿说的「12 个页面各自 `Scaffold(containerColor)`」。→ `PATCH-04`
2. **Sheet / Dialog / Picker 在独立窗口**，in-window 捕获看不见它们。跨窗口玻璃只能走跨窗口 Bake。→ `O-81`..`O-87` 新增
3. **`ImportWizard` 有自己的 `WedoBackground`**（`:226`），环境纹理会叠两份。→ 归 `L-18` 同批处理

### 遗留问题

1. **`U-19`**：冲突卡折角切换是否补 `M-12` 色变动效（当前瞬变）
2. **`U-20`**：`WeekChip` 是否补 `M-02` 按压反馈（当前无）
3. **`U-21`**：平台 `Dialog` 的出现/消失动画是否受 App 的「减弱动效」开关影响（系统级，不受控）
4. **`U-22`**：今日页「当前时段」条目是否高亮
5. **`PATCH-04` 待写**：`V-40` 的「同屏 > 10」在今日页（≤ 12 条）上处于边界，需明确「12 条以内可用阴影」
6. **L-20 的 G0 决策需要一次确认**：如果用户认为对话框也必须有玻璃质感，那就得接受「每次弹窗一次材质跳变」。这是**产品取舍**，不是技术限制 → 登记为 `U-23`，由卷 01 的决策表复核
