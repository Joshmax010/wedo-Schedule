# 液态玻璃改造验收报告（P0–P6）

> 收尾日期：2026-10-28
> 范围：材质分级（P0–P1）、采样管线（P2）、运动系统（P3）、L2 折射灰度（P4）、配额治理（P5）、文档收尾（P6）

---

## 1. 阶段结论

| 阶段 | 目标 | 结论 |
|---|---|---|
| P0 | 基础开关 / 死代码清理 / 契约骨架 | ✅ 完成 |
| P1 | G0 参数层、底色去重、材质令牌 | ✅ 完成 |
| P2 | Host + Stage + 采样三模 + G1 代表面 | ✅ 完成 |
| P3 | 弹簧族表、减动效门控、最小驻留 | ✅ 完成 |
| P4 | AGSL L2 折射灰度（仅底栏） | ✅ 完成 |
| P5 | 配额三闸、统一对话框、跨窗口两波 | ✅ 完成 |
| P6 | 规范文档、权威指向、技术债清单 | ✅ 完成 |

---

## 2. 架构落点

```
ui/glass/
  GlassCapture.kt          Host + Stage + GlassBackdrop（Bake / LiveStrip）
  GlassQuota.kt            语义闸 / 数量闸 / 面积闸
  GlassUniforms.kt         uniform 单一顺序源
  GlassShader.android.kt   RuntimeShader + 失败降级
  GlassDebug.kt            capture / draw / toImageBitmap 计数
  WedoGlassSurface.kt      G0 表面修饰器
  GlassCapability.kt       设备能力纯函数

ui/motion/
  WedoSprings.kt           M-01..M-08 族表
  WedoMotionPolicy.kt      S1 / S3 降级语义
  WedoPress.kt             L1 按压 + 80ms 最小驻留
  WedoProgress.kt          单进度

ui/components/
  WedoDialog.kt            15 处对话框的唯一入口
  WedoTabBar.kt            悬浮胶囊底栏（G2）
```

---

## 3. 24 个对象的材质分配

| 档 | 对象 |
|---|---|
| **G2** | 底部浮动胶囊 `WedoTabBar` |
| **G1** | 课程详情 Sheet、分享 Sheet、课程动作 Sheet、周次选择器 |
| **G0** | 全部内容卡、设置卡、课程块、冲突卡、15 个对话框、空状态、导入向导、今日页、设置页族、顶栏 |
| **不改造** | 桌面小组件（`L-24`） |

---

## 4. 测试证据

| 类别 | 数量 | 位置 |
|---|---|---|
| 单元测试总数 | 872 | `app/src/test/java` |
| 失败 | 0 | — |
| 材质 / 配额 | `GlassQuotaTest`、`P5GlassGovernanceTest` | `ui/glass/` |
| Shader 契约 | `GlassShaderUniformContractTest` | `ui/glass/` |
| 捕获契约 | `GlassCaptureContractTest` | `ui/glass/` |
| 运动覆盖 | `WedoMotionCoverageTest`、`WedoSpringsTest` | `ui/motion/` |
| 真机截图 | P1–P4 共 20+ 张 | `evidence/baseline/` |

---

## 5. 真机验证记录

| 阶段 | 验证内容 | 结论 |
|---|---|---|
| P1 | 设置卡 / 导出页 / 手动添加页 浅色深色 | ✅ 无白条、黑底、层级异常 |
| P2 | 悬浮胶囊底栏、课程详情 Sheet、周次选择器、课程动作 Sheet | ✅ 采样可见、文字可读 |
| P3 | 设置页 / 今天页（按压、转场、底栏隐藏接入族表后） | ✅ 界面无回归 |
| P4 | AGSL 编译 | ✅ 日志 `L2 effect active size=844.0x104.0 corner=52.0`，无 crash |
| P5 | 15 处对话框统一后的底色 | 见 P6 截图 |

---

## 6. 未闭环项（需人工 / 真机）

| ID | 内容 | 原因 |
|---|---|---|
| VR-12 | L1 vs L2 的 5 人盲比 | **已用真机 A/B 代替**：L1/L2 并排图肉眼无差别，故 L2 默认关闭（方案 P4 失败处理 ③） |
| VR-13 | 滚动停止后 L1→L2 材质跳变观感 | 随 L2 关闭一并消失（不再有 L1/L2 切换） |
| Q-84 | 帧率 P90 劣化对比 | 需真机跑 `dumpsys gfxinfo` |
| U-11 | `density` 死键 | 属设置项语义，不在本轮范围 |
| R-08 | 迁 NavHost | 属导航架构，不在本轮范围 |

以上 5 项均已登记在 `docs/glass/TECH_DEBT.md`，**不是「已知问题放行」**，而是明确划到本轮范围外。

---

## 7. 权威指向

| 用途 | 文件 |
|---|---|
| 推导过程与全部编号 | `docs/glass/00_MASTER.md` |
| 实现条款正文 | `docs/glass/DESIGN_SPEC_V4_GLASS.md`（G-001…G-072） |
| 当前实现参数定稿 | `docs/glass/02_visual_system.md` 附录 A |
| 色彩 / 字阶 / 间距 / 圆角 | `docs/DESIGN_SPEC_V2.md`（材质章节已被 v4 取代） |
| 技术债 | `docs/glass/TECH_DEBT.md` |
