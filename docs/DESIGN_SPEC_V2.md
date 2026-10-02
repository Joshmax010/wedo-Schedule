# wedo 界面设计规范 v2 —— 苹果 HIG 对齐版

> 状态：**待评审**。本文件是本轮「完全原创 UI」的设计依据与落地契约。
> 所有色值、字号、间距、圆角均来自本文件；代码偏离本文件视为缺陷。
>
> 依据来源：Apple Human Interface Guidelines 一手资料
> （Layout / Materials / Typography / Tab bars / Color，2025-07 与 2026-06 版本）。
> **不依据记忆，不依据第三方复述。**

---

## 0. 为什么要有这一版

现状诊断（这是"看得出是 sleepy 改版"的**根因**，不是审美问题）：

| 症状 | 技术根因 |
|---|---|
| 观感像上游改版 | `ThemePresets` 五套预设（`default`/`spring`/`ocean`/`peach`/`slate`）是上游的品牌资产，改强调色不改变骨架 |
| Material / iOS 混搭 | `WedoAppleTokens`（HIG 令牌）与 Material3 组件（`Scaffold`/`DropdownMenu`/`Icon`）两套体系并存，各自带自己的默认动效、阴影、水波纹 |
| 不像一个产品 | 缺自有的组件规范层：现在每个页面各自决定卡片怎么做、标题怎么排 |
| 玻璃质感混乱 | `WedoGlassTokens` 的材质**曾用于内容卡片**，违反 HIG「Don't use Liquid Glass in the content layer」 |

本轮目标不是"换一套配色"，而是**建立一套自洽的设计语言 + 把 Material3 组件层替换为自有组件层**。

---

## 1. 设计原则（五条，按优先级）

1. **内容优先于容器。** 课程是主角，界面是配角。任何装饰如果不能帮助识别课程/星期/周次，就该删掉。
2. **层级靠留白与字重，不靠边框与阴影。** 这是 Apple 的核心手法：用 spacing 和 weight 建立层级，而非堆叠分割线与描边。
3. **玻璃只属于控件层。** HIG 原文：*"Don't use Liquid Glass in the content layer."* 材质用于 tab bar / toolbar / 导航控件；内容卡片用**实色**。
4. **颜色承载语义，不承载装饰。** 强调色是"可交互"的信号，课程色是"这是哪门课"的识别码。两者不得混用。
5. **动效克制。** 只动 `transform` 与 `opacity`，200–300ms。动效服务于因果，不做入场秀。

---

## 2. 色彩系统

### 2.1 三层结构（关键！这是去掉混搭感的核心）

```
┌─ 控件层 (Control Layer) ── 玻璃材质 / 强调色 / 导航
├─ 内容层 (Content Layer) ── 实色 surface / 课程色块
└─ 基底 (Base) ─────────── 页面背景
```

**硬规则**：Liquid Glass 只出现在最上层。内容卡片**禁止**使用材质、模糊、半透明。

### 2.2 中性灰阶（界面骨架，占 95% 面积）

| 令牌 | Light | Dark | 用途 |
|---|---|---|---|
| `background` | `#F2F2F7` | `#000000` | 页面底色 |
| `surface` | `#FFFFFF` | `#1C1C1E` | 内容卡片 |
| `surfaceContainer` | `#FFFFFF` | `#2C2C2E` | 卡内次级块 |
| `separator` | `#3C3C43` @29% | `#545458` @65% | 0.5pt 分割线 |
| `label` | `#000000` | `#FFFFFF` | 主文字 |
| `secondaryLabel` | `#3C3C43` @60% | `#EBEBF5` @60% | 次文字 |
| `tertiaryLabel` | `#3C3C43` @30% | `#EBEBF5` @30% | 三级文字/图标 |

Dark 用**纯黑** `#000000`（OLED 省电 + Apple 标准），不是深灰。

### 2.3 强调色（11 个系统色）

`#007AFF` 蓝（默认）/ 绿 / 靛 / 橙 / 粉 / 紫 / 红 / 青 / 黄（已移除）/ 棕 / 灰。

**黄色已移除**：浅色 `#FFCC00` 对比度仅 1.512:1，无法达到图标 3:1 门槛，无解。
历史值经 `legacyAliases` 迁移到橙色。

### 2.4 对比度门槛（分角色，别混）

| 角色 | 门槛 | 依据 |
|---|---|---|
| 图标 | 3:1 | WCAG 1.4.11 Non-text Contrast |
| 文字 | 4.5:1 | WCAG 1.4.3 |
| **课程块内文字** | **6.5:1** | 本产品自定：课程名在彩色底上，需要更高余量 |

判断口诀：**当背景 → `accent`；当图标 → `accentIcon`；当文字 → `accentText`。**

### 2.5 课程色

- 同名课程跨重启保持稳定色相（`WedoCourseCard` 的哈希取色），不随机。
- 用户手动指定色 > 自动色 > 无色模式（全局开关）。
- 底色亮 → 深字；底色暗 → 白字。按实际亮度取对比更高者。

---

## 3. 排版系统

### 3.1 字体

- **拉丁 + 数字**：SF Pro（系统）
- **中文**：苹方 PingFang SC（系统内置）
- 字体种类**上限 2**。层级靠 `weight` + `size` + `color` 建立，不靠加字体。

### 3.2 字阶（HIG 标准，pt → sp 1:1）

| 样式 | Weight | Size | Line | 用途 |
|---|---|---|---|---|
| LargeTitle | Regular | 34 | 41 | 页面主标题 |
| Title1 | Regular | 28 | 34 | 大区块标题 |
| Title2 | Regular | 22 | 28 | 卡片组标题 |
| Title3 | Regular | 20 | 25 | 卡片标题 |
| Headline | Semibold | 17 | 22 | 强调行 |
| Body | Regular | 17 | 22 | 正文（默认 17pt） |
| Callout | Regular | 16 | 21 | 次级正文 |
| Subheadline | Regular | 15 | 20 | 辅助信息 |
| Footnote | Regular | 13 | 18 | 脚注 |
| Caption1 | Regular | 12 | 16 | 最小说明 |
| Caption2 | Regular | 11 | 13 | 极限（**最小 11pt**） |

**最小字号 11pt**，低于此不可读。字重避免 Ultralight/Thin/Light。

### 3.3 Dynamic Type

必须响应系统字号放大。布局策略：横向排列在大字号下**改为纵向堆叠**，容器增高而非截断文字。

---

## 4. 间距与圆角

### 4.1 间距（8px 基线网格）

`4 / 8 / 12 / 16 / 20 / 24 / 32 / 40`。

- 页面左右外边距：**16dp**（手机）
- 区块之间：**24dp**
- 卡内 padding：**16dp**
- 紧密元素之间：**8dp**

### 4.2 圆角（squircle 连续曲率）

| 元素 | 圆角 |
|---|---|
| 内容卡片 | 16dp |
| 内嵌单元格 | 14dp |
| 课程块 | 8dp |
| 按钮/胶囊 | full |
| 小组件容器 | 20dp |

---

## 5. 组件规范

### 5.1 导航（推倒重来）

**HIG 原文**：*"Use a tab bar to support navigation, not to provide actions."*

**当前设计违反此条**：底部 Dock 是"左课表 / 中＋ / 右设置"——把**动作**（＋）塞进了**导航**栏。

**新方案**：
- 底部 tab bar 只放**导航**，3 项上限：`课表` / `今日` / `我的`
- **"＋ 添加课程"移出 tab bar** → 放到课表页顶部导航栏右侧（iOS 惯例）
- tab bar 用 Liquid Glass 材质，图标用**填充态**（HIG: *"Prefer filled symbols"*）
- **不隐藏 tab bar**（HIG: *"Don't disable or hide tab bar buttons"*）—— 当前"下滑收起 Dock"违反此条

### 5.2 顶部导航栏

- 大标题 → 滚动时收起为 inline 标题（iOS 标准行为）
- 右侧放页面级动作（添加 / 更多）
- 材质：滚动到顶部时透明，滚离后变 `regular` 玻璃

### 5.3 课程卡片（内容层，实色）

```kotlin
// ✅ 正确：内容层用实色
Surface(color = courseColor, shape = RoundedCornerShape(8.dp)) { ... }

// ❌ 错误：内容层用玻璃
Modifier.wedoGlass(...)  // 已删除，勿复活
```

状态：默认 / 按下（scale 0.96）/ 长按（触觉反馈 + 菜单）/ 淡化（非本周）。

### 5.4 按钮

| 类型 | 样式 | 用途 |
|---|---|---|
| Filled | 强调色实底 + 白字 | 唯一主 CTA |
| Tinted | 强调色 15% 底 + 强调色字 | 次级动作 |
| Plain | 无底 + 强调色字 | 行内动作 |

按下反馈：scale 0.96，spring 回弹，**无水波纹**（Material 特征，逐页清除）。

---

## 6. 动效

| 场景 | 参数 |
|---|---|
| 按钮按下 | scale 0.96，spring(damping 0.86, stiffness 380) |
| 切周 | 阻尼弹簧位移 380 |
| 顶栏折叠 | 高度动画 240ms |
| 页面转场 | 滑动 + 淡入 300ms |

仅动 `transform` / `opacity`。全部受「设置 → 减弱动效」总开关约束。

---

## 7. 无障碍检查清单

- [ ] 文字对比度 ≥ 4.5:1（课程块内 ≥ 6.5:1）
- [ ] 图标对比度 ≥ 3:1
- [ ] 触控目标 ≥ 44×44dp
- [ ] 键盘/遥控器可聚焦，焦点可见
- [ ] 语义化标签（TalkBack 可读）
- [ ] 颜色非唯一信息载体（冲突课程除描边外加 `!` 符号）
- [ ] Dynamic Type 放大不截断
- [ ] 支持减弱动效 / 增大对比度系统设置

---

## 8. 落地顺序（下一轮执行）

按**风险从低到高**推进，每步独立可验收：

1. **导航重构**：拆掉底部 Dock 的动作项，tab bar 归位为纯导航 ← 影响面最大，收益最直接
2. **Material3 清除**：逐页替换 `Scaffold`/`DropdownMenu`/水波纹为自有组件
3. **ThemePresets 重做**：五套上游预设 → wedo 自有命名与色阶
4. **页面级重排**：课表页 / 设置页 / 我的页按本规范重排间距与字阶

---

## 9. 已知偏离（待修）

| 位置 | 偏离 | 状态 |
|---|---|---|
| `WedoShell.kt` | 「下滑收起 Dock」违反 HIG 不隐藏 tab bar | 待改 |
| `ScheduleScreen.kt` | 「＋」在导航栏中承担动作 | 待改 |
| 全局 | Material3 水波纹仍存在 | 待清 |
| `docs/PROJECT_HANDOFF.md` §4.1 | 仍在描述已删除的 `Modifier.wedoGlass` | **已过期，需同步** |
