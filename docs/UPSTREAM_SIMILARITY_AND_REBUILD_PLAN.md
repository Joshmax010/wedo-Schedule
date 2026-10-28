# 上游相似度报告与设置族全面重构计划（方案 B）

> 审计日期：2026-10-22
> 上游：`https://github.com/lingion/sleepy`（Sleepy · 轻课表，GPL-3.0）
> 上游当前 HEAD：`3dafa110f74ac4f32464ce1efddb24197a4091d9`（2026-10-22）
> wedo fork 基准提交：`08a1f26a5d7b1a117e2216d92804b202ddde228d`
> 只读副本：`D:/AAAAA/ai/upstream/sleepy`（工作区之外，不参与 wedo 构建）

## 0. TL;DR

- wedo 与上游有 **97 个同名 `.kt` 文件**，`SettingsCards.kt` 几乎逐行同构；设置族多数页面是 1:1 移植后改名。
- 相似度最高的不是"某个像素"，而是**布局骨架 + 控件组合**：折叠卡、裸图标行、`surfaceContainer` 灰卡、蓝色药丸分段、顶部 `TopAppBar + LazyColumn`。
- 已经差异化的页面：设置主页、所有课表、导出、外观、开关绿色、彩色图标块、品牌图标。**不需要推倒重来**。
- 仍明显带上游骨架的页面：**通用、节假日、提醒、编辑课表、开源许可、手动创建课程**（+ 可选的桌面小组件编辑）。
- 方案 B 采用"逐页重构布局层、不动业务逻辑"的方式，分 4 批、每批一次构建。预计 **6 个必做页面 + 2 个可选页面**。

## 1. 审计口径

| 项 | 说明 |
|---|---|
| 血缘 | 同名文件交集、共享组件名、相同 `CardSection`/`SettingsItem` 骨架 |
| 布局 | 读上游 `MineScreen` / `SettingsCards` / `AppearanceScreen` / `GeneralSettingsScreen` 等源码，记录布局模式 |
| 页面 | 只覆盖"设置入口可达"的页面；首页/课表/今天页不在本次范围 |
| 版本 | 血缘对比用 fork 基准 `08a1f26a`；布局对比用上游 HEAD `3dafa11`（上游自己也改过，部分差异不代表 wedo 的改动） |

## 2. 相似度结论

### 2.1 血缘层

- 同名 `.kt` 文件：**97 个**。
- 共享组件：`SettingsCards.kt`（`SectionHeader` / `SettingsCard` / `SettingsFlatCard` / `DisplayModeOption` / `SettingToggleRow`）、`SegmentedSwitcher.kt`、`ConflictCard.kt`、`CourseDetailSheet.kt`、`CourseTableView.kt`、`ShareScheduleSheet.kt`、`DateTimePickers.kt`、`SmartPeriodEditor.kt`、`TimeSlotEditor.kt`。
- wedo 的 `SettingsCards.kt` 与上游同构度最高：连注释里的"为什么用 TextMeasurer 算 tab 宽"都一致；目前只做了"去折叠 + 换底色"两处改动。

### 2.2 页面级相似度

| wedo 页面 | 上游对应 | 相似度 | 主要重合点 | 当前状态 |
|---|---|---|---|---|
| 设置主页 `WedoSettingsScreen` | `MineScreen` + `ManagementPage` | 中 | 分组 + 行式入口；wedo 已合并四组、换彩色图标块 | 已改造 |
| 所有课表 `AllTablesScreen` | `AllTablesScreen` | 中 | 表列表 + 复制/设置 IconButton + 当前表标记 | 已改为 profile + 列表 |
| 外观与主题 `AppearanceScreen` | `AppearanceScreen` | **低** | 上游有主题预设/自定义主题/色相滑杆；wedo 已删，仅深浅 + 预览 | 已改造 |
| 通用 `GeneralSettingsScreen` | `GeneralSettingsScreen` | **高** | 同一批设置项、折叠卡 + Slider + Toggle 组合、同分组顺序 | 仅组件层去折叠 |
| 导出课表 `ExportScreen` | `ExportScreen` | **高** | 顶部信息卡 + `ExportItem` 列表 + 四个导出项 | 已改 profile + 分色 |
| 节假日 `HolidaySettingsScreen` | `HolidaySettingsScreen` | **高** | 同一列表/表单骨架、同 `SettingsFlatCard` | 未重构 |
| 提醒 `ReminderScreen` | `ReminderScreen` | **高** | 主开关 + 提前分钟 + 时间选择，结构一致 | 未重构 |
| 编辑课表 `EditTableScreen` | `EditTableScreen` | **高** | 基本信息/学期/颜色表单，字段顺序一致 | 未重构 |
| 开源许可 `LicenseScreen` | `LicenseScreen` | **高** | 静态分组文本页 | 未重构 |
| 手动创建课程 `AddCourseScreen` | `AddCourseScreen` | **高** | `CardSection` + `MeetingBlockEditor` + 同字段顺序 + 同时段模式 | 仅卡片底色/按钮主次 |
| 导入向导 `ImportWizard` | `ImportSheet` / `ImportDraftSheet` | 中 | wedo 改为全屏分步，信息架构已不同 | 部分重构 |
| 桌面小组件编辑 `WidgetEditScreen` | `WidgetEditScreen` | 中高 | 分组 section + 预览 | 未重构（可选） |

### 2.3 设计语言重合点（"上游味"的真正来源）

1. `MaterialTheme.colorScheme.surfaceContainer` 灰色卡片 + 大圆角。
2. `SettingsCard` 折叠卡 + `ExpandMore` 旋转箭头 + `AnimatedVisibility`。
3. 裸图标行（`SettingsItem`：一个 icon + 文字 + chevron，不套色块）。
4. `SettingsFlatCard`：标题行右侧内嵌 `SegmentedSwitcher`。
5. `FilledTonalButton` / 实底 `Button` 作为页面内动作条。
6. `SegmentedSwitcher` 的**蓝色药丸**选中态。
7. `Scaffold + TopAppBar + LazyColumn` + 16dp contentPadding 的通用骨架（这条本身通用，不构成问题）。

### 2.4 已完成的差异化（不要重复劳动）

- 设置主页：A 方案（inset grouped + 彩色图标块 + profile 行 + 页脚）。
- 所有课表：profile 行 + 全部课表列表 + 新建行。
- 导出：profile 行 + 四格式分色 + inset 卡。
- 外观：迷你课表预览 + iOS 分段；强调色配置已移除。
- 共享组件：`SettingsCard` 不可折叠、`SettingsFlatCard` 换卡底、`WedoToggle` 绿色、彩色图标块。
- 品牌：原创矢量图标/启动图；proguard 去上游包名。

## 3. 方案 B：设置族全面重构计划

### 3.1 原则

1. **以 Apple HIG 为唯一参照**，不再对照上游逐页模仿或反着做。
2. **只改布局层**：state、handler、持久化、校验、导入/导出管线一律不动。
3. **每页至少改"信息架构"**，不是只换卡片颜色：
   - 页眉从卡内标题移到卡外；
   - 折叠改为平铺或子页面；
   - 动作条降级为行/次要按钮；
   - 加页脚说明。
4. **不复制上游代码**：只读副本仅用于确认差异，不把上游实现搬进 wedo。
5. **每批一次构建**（用户 2026-10-22 明确要求），不逐页构建。

### 3.2 目标页面模板

```
[返回 + 标题]
分组页眉（卡外，小字灰）
inset 卡片（10dp 圆角，白/黑卡）
  行：彩色图标块 26×26 / 标题 / 当前值 / chevron（44pt）
  行：开关（绿色）/ 滑杆 / 分段（iOS 灰滑块）
页脚说明（小字灰）
[仅在需要时：底部主 CTA]
```

### 3.3 页面清单、方向与工作量

| 优先级 | 页面 | 现状相似度 | 重构方向 | 工作量 | 风险 |
|---|---|---|---|---|---|
| P0 | **通用** | 高 | 拆成「主页显示 / 显示星期 / 冲突课程」三段；去折叠；分段改 iOS 灰滑块；`surfaceContainer` 行改 inset 卡；加页脚 | M | 中（滑杆/开关持久化多） |
| P0 | **节假日** | 高 | 列表 + 新增表单拆两段；`SettingsFlatCard` 改 inset 卡；日期行统一 44pt | M | 中（日期/补班逻辑） |
| P1 | **提醒** | 高 | 主开关 + 提前分钟 + 时间窗三段；inset 卡；开关绿色；页脚解释权限 | S | 低 |
| P1 | **编辑课表** | 高 | 基本信息 / 学期 / 颜色三卡；字段标签统一；保存 CTA 底部 | M | 中（表单校验） |
| P1 | **开源许可** | 高 | 静态分组卡 + 页脚；去掉长段裸文本；保留 GPL 归属 | S | 低 |
| P2 | **手动创建课程** | 高 | 课程 / 时间 / 颜色三卡；时段编辑卡统一；「新增时段」降为行；主 CTA 吸底 | L | 高（1800 行表单） |
| P2 | **导入向导** | 中 | 步骤卡 + 来源选择卡；统一 inset；页脚说明 | M | 中（导入状态机） |
| P3（可选） | **桌面小组件编辑** | 中高 | 预览 + 分组卡；同模板 | M | 中 |
| 收尾 | 所有课表 / 导出 / 外观 / 设置主页 | 已低 | 只做一致性检查（页眉/页脚/行高/图标色） | S | 低 |

### 3.4 分批与构建节奏

| 批次 | 页面 | 构建 |
|---|---|---|
| Batch 1 | 通用 + 节假日 | 改完 → 1 次 `assembleDebug` → 真机验收 |
| Batch 2 | 提醒 + 编辑课表 + 开源许可 | 改完 → 1 次构建 → 真机验收 |
| Batch 3 | 手动创建课程 + 导入向导 | 改完 → 1 次构建 → 真机验收 |
| Batch 4（可选） | 桌面小组件编辑 + 全族一致性收尾 | 改完 → 1 次构建 → 真机验收 |

### 3.5 每页 Definition of Done

- [ ] 无折叠卡；页眉在卡外；卡片 10dp inset。
- [ ] 列表行 ≥44pt；图标为彩色块；右侧值 + chevron；分隔线 inset。
- [ ] 开关为 iOS 绿色；分段为 iOS 灰滑块（如该页有）。
- [ ] 页脚有解释性文案（复用现有 string，不新增硬编码中文）。
- [ ] 浅色/深色均检查。
- [ ] 原有交互全部保留：开关、滑杆、选择、保存、校验、导入/导出。
- [ ] 六语种无新增未翻译硬编码。

### 3.6 风险与控制

| 风险 | 控制 |
|---|---|
| 表单/状态机被碰坏 | 只改布局层；改前记录关键 handler 清单；改后逐项手测 |
| 滑杆 `onValueChangeFinished` 持久化丢失 | 不重写 Slider，只换外层容器 |
| 六语种新增文案 | 复用已有 string；必须新增时一次性补 6 语言 |
| 构建节奏拖慢 | 每批一次构建；失败才修 |
| 与上游"越改越像" | 以 HIG 模板为准，不以"和上游不同"为目标 |

### 3.7 不建议做的事

- 不把上游代码复制进 wedo（只读参照）。
- 不为"看起来不同"牺牲可用性（例如把开关改成复选框、把滑杆改成输入框）。
- 不在本轮重写 `AddCourseScreen` 的底层逻辑，只重构布局与主次。

## 4. 下一步

建议从 **Batch 1（通用 + 节假日）** 开始：这两页的上游骨架最重、共享组件已就位，改动集中在 `GeneralSettingsScreen.kt` / `HolidaySettingsScreen.kt` + 少量共享组件；改完统一构建一次，真机验收后再推进 Batch 2。

## 附录：审计命令

```bash
# 同名文件交集
find upstream/app/src/main/java -name '*.kt' -exec basename {} \; | sort -u > /tmp/up.txt
find wedo/app/src/main/java  -name '*.kt' -exec basename {} \; | sort -u > /tmp/wd.txt
comm -12 /tmp/up.txt /tmp/wd.txt | wc -l   # 97

# 上游 HEAD
git -C upstream rev-parse HEAD             # 3dafa110f74ac4f32464ce1efddb24197a4091d9
```
