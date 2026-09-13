# 吉林建筑大学教务系统取证记录

状态：2026-09-12 已完成官方教务入口、无 Cookie SSO 前半段、课表页面、学期选择器、正常课表和真实空课表响应取证；交互式登录回跳、验证码条件与会话过期响应仍待补充。

## 已确认入口与系统类型

- 融合服务门户：`https://portal.jlju.edu.cn/main.html#/Index`。
- 教学综合信息服务平台：`https://jwxt.jlju.edu.cn`。
- 个人课表页面：`GET /jwglxt/kbcx/xskbcx_cxXskbcxIndex.html`。
- 页面功能码：`gnmkdm=N2151`；布局参数为 `layout=default`。
- 教务系统类型：新版正方 `jwglxt`。页面加载了 `zftal-ui-v5-1.0.2` 资源，响应结构与基座 `zf_new` 协议一致。
- 门户服务目录 `/portal-api/v2/service/showAll` 中“教务管理系统”的官方入口为 `https://jwxt.jlju.edu.cn/sso/hnyyxyiotlogin`。
- 不带 Cookie 访问该入口实证得到：`jwxt.jlju.edu.cn/sso/hnyyxyiotlogin`（302）→ `lxr.jlju.edu.cn/controller/v1/public/verify`（302）→ `lxr.jlju.edu.cn/portal/shortcut.html`（200，JavaScript Loading 页）。所有票据值均被丢弃，只保留参数名称 `t`、`dest`、`appUrl`。
- 已登录门户同时观察到 `cas.jlju.edu.cn/cas/login/detect`。因此登录导航白名单暂限定为 `lxr.jlju.edu.cn` 和 `cas.jlju.edu.cn`，教务数据与 JS Bridge 仅允许 `jwxt.jlju.edu.cn`。

## 已确认请求

### 学期列表

学期数据不需要独立 JSON 接口，直接包含在个人课表首页的两个选择器中：

- 学年选择器：`select#xnm`，值为学年起始年份，例如 `2026`，显示名为 `2026-2027`。
- 学期选择器：`select#xqm`，观察到代码 `3`、`12`、`16`，显示名分别为 `1`、`2`、`3`。

### 个人课表

- Method：`POST`。
- Path：`/jwglxt/kbcx/xskbcx_cxXsgrkb.html`。
- Query 参数名称：`gnmkdm`、`sf_request_type`。
- Content-Type：`application/x-www-form-urlencoded;charset=UTF-8`。
- Form 参数名称：`xnm`、`xqm`、`kzlx`、`xsdm`、`kclbdm`、`kclxdm`。
- 正常响应：HTTP 200，`application/json`。
- 课表数组：顶层 `kbList`。

正常样本确认的课程字段包括：

- `kcmc`：课程名称。
- `xm`：教师。
- `cdmc`：教室。
- `xqj` / `xqjmc`：星期数字与显示名。
- `jc` / `jcs`：带“节”的显示值与可解析的节次范围。
- `zcd`：周次表达式；实证包含 `5-7周(单),8-13周` 和 `5-7周(单),8-9周`。
- `xnm` / `xqm`：学年与学期代码。
- `xkbz`：选课备注，可为空。

### 空课表判定

- 使用与正常课表完全相同的 POST 接口和参数结构。
- 实证响应为 HTTP 200、`application/json`，并保留正常响应的顶层结构。
- “本学期无课”的确定判据是存在 `kbList` 字段且其值为空数组。
- 缺少 `kbList`、响应正文为空、非 JSON 或非成功 HTTP 状态均不得判为无课，应进入接口或解析错误流程。
- 已由真实空课表 HAR 生成 `empty_schedule_response.json`，原始 HAR 继续只保存在 Git 忽略目录。

### 日期与节次配置

- 日期接口：`POST /jwglxt/kbcx/xskbcx_cxRsd.html`，Form 参数为 `xnm`、`xqm`、`xqh_id`。
- 节次接口：`POST /jwglxt/kbcx/xskbcx_cxRjc.html`，Form 参数为 `xnm`、`xqm`、`xqh_id`。
- 两者均观察到 HTTP 200 JSON 响应。节次响应字段含 `jcmc`、`qssj`、`jssj`；本次样本完整返回 12 节作息，已生成脱敏夹具供后续自动填充作息时间。

## 数据流结论

JLJU 可以沿用基座现有新版正方链路：

`官方 WebView 会话 → xskbcx_cxXsgrkb POST → kbList JSON → JlJuParser/JwNewZfParser → JwCourse → CourseEntity → Room`

`JlJuParser` 是学校专用的纯函数入口，内部复用基座 `JwNewZfParser`。无需创建第二套 Course Model。现有 `ZF_NEW_FETCH_JS` 的请求路径、Form 参数与此次真实请求一致。

## WebView 安全边界

- 初始 URL 固定为门户服务目录确认的 `https://jwxt.jlju.edu.cn/sso/hnyyxyiotlogin`，应用不创建账号密码表单。
- 仅允许 HTTPS 的教务主机及已确认认证主机在 WebView 内导航；其他 HTTP(S) 链接交给系统浏览器。
- 原生 JavaScript Bridge 不在初始页注册。只有顶层页面最终落在 `jwxt.jlju.edu.cn` 后才以随机接口名安装并重载一次，再仅在可信顶层创建稳定别名。
- 一旦导航离开教务主机，立即移除 Bridge；`lxr`、`cas` 登录页面永远不主动注入采集脚本。
- 不忽略证书错误，不允许混合内容、文件访问或内容访问。

## 脱敏与证据保存

- 原始 HAR 仅保存在 `test/fixtures/jlju/private/`，该目录和所有 `.har` 文件均被 Git 忽略。
- `scripts/sanitize-jlju-har.ps1` 只输出方法、路径、参数名称、学期选择器和结构化课程字段。
- 真实课程、教师和教室分别替换为稳定的 `测试课程NN`、`测试教师NN`、`测试教室NN`。
- 星期、节次、周次、学期代码和重复关系原样保留，供离线解析回归测试。
- 公开夹具不包含请求 Header 值、Cookie、Token、Authorization、姓名或学号。

## 仍待确认

- `lxr` JavaScript 中转页之后的交互式登录页面、成功回跳链和验证码触发条件。
- Cookie 名称与会话有效期；本次 HAR 未包含可用的 Cookie 元数据。
- 主动退出路径以及退出后课表接口的实际响应。
- 系统维护及其他异常响应。
- 学期开始日期和总教学周数是否可由其他接口直接获得。

JLJU 入口现已按最小白名单开放用于端到端测试；在上述项目确认并通过真机验收前，仍不满足 M3/M5 发布门。
