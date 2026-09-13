# wedo / Sleepy 架构与 JLJU 接入点

## 现有数据流

```text
ManagementPage
  -> JwImportActivity
  -> SchoolSelectScreen
  -> JwWebViewLoginScreen
  -> 抓取当前页面 HTML 或已验证协议的 JSON
  -> JwImportViewModel.parseHtml
  -> JwParserRegistry / 具体 JwParser
  -> List<JwCourse>
  -> JwImportViewModel.toCourseEntities
  -> 导入预览
  -> ScheduleRepository / Room
  -> ScheduleViewModel
  -> ScheduleScreen / CourseTableView / CourseDetailSheet
```

## 课程模型

教务解析中间模型 `JwCourse` 包含课程名、教师、教室、星期、起止节次、起止周和单双周类型。持久化模型 `CourseEntity` 额外包含课表 ID、课程组 ID、备注、颜色、自定义时间、学分与层级。

关键语义：

- `day`：1–7，周一为 1。
- `startNode`：从 1 开始。
- `step`：持续节数，等于 `endNode - startNode + 1`。
- `type`：0 每周、1 单周、2 双周、3 显式/拆分周段。
- 同一课程的不同时间记录通过 `groupId` 归组，Room 行负责具体星期、节次和周段。
- 学期开始日期、最大周数和节次时间表位于 `TimeTableEntity`，无需为 JLJU 新建第二套课程实体。

## 解析器扩展方式

`JwParser` 是无 Android Context 的离线解析抽象。`JwParserRegistry` 将协议常量映射到解析器工厂，并在协议未知时依据结构置信度与课程数选择候选。新增 JLJU 时按真实响应选择以下最小路径：

1. 若 JLJU 是已支持协议，只新增一条受验证的学校元数据并复用现有 parser/fetch。
2. 若协议相同但 HTML 有小差异，在对应 parser 中增加窄范围结构分支和夹具测试。
3. 只有响应结构独立时才新增 `JlJuParser` 并注册协议；不得把学校域名判断散落到通用解析器。
4. JSON 接口优先解析 JSON；只有实际返回 HTML 时才使用 Jsoup。

## 已跟踪适配调用链：新正方

- 学校目录声明 `type=zf_new` 和入口 URL。
- 用户在 `SchoolSelectScreen` 选择学校后进入 `JwImportActivity`。
- `JwWebViewLoginScreen` 加载官方页面，用户自行完成 CAS/验证码登录。
- 用户触发导入时，经过验证的正方抓取脚本请求课表数据或抓取页面框架。
- `JwParserRegistry.parserFor(TYPE_ZF_NEW, html)` 构造 `JwNewZfParser`。
- Parser 将列表/网格内容转换为 `JwCourse`；混合周次会展开为多个周段。
- `JwImportViewModel` 转换为 `CourseEntity` 并进入预览；用户确认后由现有仓库写入 Room。
- `ScheduleViewModel` 观察数据库，周视图和详情组件自动更新。

## JLJU 预定接入点

- 学校元数据：`JwSchoolInfo` 的内置学校集合或其数据源。
- 认证与抓取：`JwWebViewLoginScreen` 中仅增加 JLJU 所需且有证据的域名策略/抓取策略。
- 解析：优先复用 `JwParserRegistry` 现有协议；确有必要才加入 `JlJuParser`。
- 保存和 UI：完全复用 `JwImportViewModel`、`ScheduleRepository`、Room 和课表组件。

在获得脱敏响应前，不确定 JLJU 使用哪种协议，也不指定 API、Token、URL 或字段名。

