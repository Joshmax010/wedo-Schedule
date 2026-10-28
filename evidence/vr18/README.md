# VR-18 · `@RequiresApi` 隔离验证

日期：2026-10-28
工程：`feature/v3-redesign` worktree，minSdk 26 / compileSdk 37

> 后续决策：2026-10-28 用户将 `minSdk` 提升到 33（Android 13+），API 26 复核取消。
> 本报告保留为“隔离写法曾经验证”的历史证据，不代表当前仍支持 pre-33。
临时 demo（验证后已删除）：

- `app/src/main/java/com/wedo/schedule/vr18/GlassApiFacade.kt`
- `app/src/main/java/com/wedo/schedule/vr18/GlassApi33.kt`
- `app/src/androidTest/java/com/wedo/schedule/vr18/GlassApiIsolationTest.kt`

## 验证结果

### 1. 编译（minSdk 26）

`compileDebugKotlin` 成功。`@RequiresApi(33)` 文件 + minSdk 26 调用方没有编译错误。

### 2. facade 字节码签名

`javap -p -s com.wedo.schedule.vr18.GlassApiFacade`：

```text
public final java.lang.Object createShader();
  descriptor: ()Ljava/lang/Object;
```

- facade 返回 `java.lang.Object`
- facade 字节码中 `RuntimeShader` 引用数：**0**
- facade 的调用只在 `SDK_INT >= 33` 分支内（`bipush 33; if_icmplt`）

### 3. API 33 类型隔离

`javap -p -s com.wedo.schedule.vr18.GlassApi33`：

```text
public final java.lang.Object createShader();
  descriptor: ()Ljava/lang/Object;
```

`RuntimeShader` 只出现在 `GlassApi33` 的常量池/字节码中；它没有进入 facade 的签名或字段。

### 4. 运行时代理验证

设备：MuMu `MuMu安卓设备`，Android 12（API 32，x86_64）
说明：API 32 同样没有 `android.graphics.RuntimeShader`，可验证 pre-33 类加载路径。

手动 instrumentation 结果：

```text
com.wedo.schedule.vr18.GlassApiIsolationTest:
OK (1 test)
```

测试内容：加载 facade，确认 pre-33 返回 `null`，并断言 `createShader()` 的反射返回类型是 `Object`。

## 结论与边界

**结论：A-17 的「API 33 侧返回 `Any?`、26 侧签名不出现 RuntimeShader」写法成立。** 编译和字节码签名均通过，API 32 运行时加载 facade 不崩。

**边界：没有真实 API 26 实例。** 本次用 API 32 作为 pre-33 代理；API 26 的类校验行为仍需一台 API 26 设备/模拟器做最终复核。不能把本报告写成“API 26 已真机通过”。

## 复现命令（临时 demo 已删除，需重新落文件后执行）

```bash
# 编译
bash scripts/build.sh compileDebugKotlin

# 查看 facade 签名与 RuntimeShader 引用
javap -classpath app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes \
  -p -s com.wedo.schedule.vr18.GlassApiFacade

# 手动 instrumentation（先安装 debug/test APK）
adb shell am instrument -w -r \
  -e class com.wedo.schedule.vr18.GlassApiIsolationTest \
  com.wedo.schedule.debug.test/androidx.test.runner.AndroidJUnitRunner
```
