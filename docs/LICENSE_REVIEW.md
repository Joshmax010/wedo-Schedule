# 许可证审查

## 基座

- Base Project：Sleepy · 轻课表
- Original Author：lingion 及仓库贡献者
- Original Repository：https://github.com/lingion/sleepy
- Baseline Commit：`08a1f26a5d7b1a117e2216d92804b202ddde228d`
- Original License：GNU General Public License v3.0

## 结论

GPL-3.0 允许运行、研究、修改和再分发。分发 wedo 源码或 APK 时必须保留许可证与版权信息，并向 APK 接收者提供相应版本的完整对应源码及构建所需脚本。wedo 的修改继续采用 GPL-3.0，不附加“禁止商业使用”等额外限制。

项目声明“维护团队不收费、不投放广告、不经营数据”属于运营承诺，而不是对下游使用者增加的许可条件。

## 上游归属

Sleepy 的教务解析代码注明部分设计或实现来自 Apache-2.0 项目，例如 WakeUp 相关分叉。Apache-2.0 代码可以组合进 GPL-3.0 项目，但必须保留原有版权、许可证和修改声明。

**2026-09-27 内核重构后的状态**：30 个上游协议解析器已删除，原先挂在应用内「开源许可」页的 40+ 条教务适配致谢随之撤下——那些条目致谢的代码已不在仓库里，继续保留反而是失实声明。

仍在仓库中、且确实源自他方的成果只剩以下四项，已逐条在应用内「开源许可」页列出：

- `shiguang_warehouse`（MIT）`zhengfang_01.js` 的网格视图 / 列表视图解析 —— 代码移植，义务最重
- `FlowCourse`（GPL-3.0）与 `zfn_api`（MPL-2.0）—— kbList 字段形态交叉验证依据
- `WakeupSchedule_BUPT`（Apache-2.0）—— 解析器基类契约设计参考

`JwParser` 基类与 `JwNewZfParser` 仍是本仓代码，但 `JwNewZfParser.parseKbgridTable0` / `parseKblistTable` 两个函数属移植实现，其来源注释必须保留。若要彻底去除这项义务，需先完成这两个函数的独立重写，重写后方可从致谢中撤下 `shiguang_warehouse`。

## wedo 修改义务

- 保留根目录 GPL-3.0 `LICENSE`。
- README 明确列出 Sleepy 上游、基准提交和 wedo 的主要修改。
- 对源自 Apache-2.0 的文件保留原有来源和版权声明。
- Release 同时发布对应源码标签，不能只发布 APK。
- 不使用“吉林建筑大学官方”名义，不暗示学校授权。
- 正式发布前生成并人工复核第三方依赖许可证清单。

## 当前未决事项

依赖许可证的自动生成与完整复核属于 M7 发布门。JLJU 响应样本不构成可公开提交内容，只有完全脱敏且无认证信息的测试夹具可以进入仓库。

