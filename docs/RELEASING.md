# 发布说明

当前状态：主 App 仅配置 Android Debug CI；下面的签名、版本标签和 Release 上传流程是发布前必须实现的要求，不代表现有工作流已经具备。独立教务开发包另由 `jw-import-kit.yml` 验证和 `jw-import-kit-v*` 预览 Release 分发，不替代主 App 发布流程。

## 版本

- 开发验证版从 `0.1.0` 开始。
- 完成 JLJU 取证、端到端导入、三人真机测试和安全复核后才可发布 `1.0.0`。

## 签名

签名文件及密码不得提交 Git。未来签名 CI 应使用以下加密 Secrets：

- `WEDO_KEYSTORE_BASE64`
- `WEDO_STORE_PASSWORD`
- `WEDO_KEY_ALIAS`
- `WEDO_KEY_PASSWORD`

签名流水线实现后应只在 `v*` 标签上生成 Release；签名文件写入 Runner 临时目录，任务结束后由 Runner 销毁。每次 Release 同时附 APK、SHA-256、源代码标签、变更说明、隐私摘要和已知问题。

## 发布门

发布前必须通过 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、敏感信息扫描、脱敏夹具检查、三名测试者的本地端到端验证及许可证复核。刷新失败不得删除最后一次成功课表。
