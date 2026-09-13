# JLJU fixtures

本目录只接受完全脱敏、无法还原个人身份且不含 Cookie、Token、Authorization、密码、真实姓名或学号的响应样本。

已取得并提交的完全脱敏夹具：

- `request_structure.json`：只包含请求方法、主机、路径、参数名称和响应类型。
- `terms_response.html`：从真实课表首页提取并重建的学年、学期选择器。
- `schedule_response.json`：从真实 `kbList` 响应生成；课程、教师和教室使用稳定测试别名。
- `empty_schedule_response.json`：从真实 HTTP 200 空课表响应生成，保留 `kbList: []` 判定结构。
- `sso_request_structure.json`：门户服务目录、无 Cookie 重定向链和认证域名的脱敏结构，不含票据值。
- `calendar_response.json`：课表日期分段结构，只保留日期、时段和校区代码。
- `sections_response.json`：真实 12 节作息时间，只保留节次和起止时间。

尚未取得：

- 真实会话过期响应。
- 交互式登录完成后的回跳链及验证码触发条件。

`private/` 只用于测试者本机保存原始 HAR，整个目录由 Git 忽略。公开夹具由
`scripts/sanitize-jlju-har.ps1` 生成；不得手工复制原始响应到公开目录。
