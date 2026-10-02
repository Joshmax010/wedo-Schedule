plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
    id("com.google.devtools.ksp") version "2.1.10-1.0.29" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10" apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// 密钥 / 隐私泄漏门禁
//
// 规则实现只有一份：scripts/scan-secrets.ps1。
// CI（.github/workflows/android.yml）直接以 `shell: pwsh` 调它，失败归属更清楚；
// 这里再把同一个脚本接进本地构建，让 `./gradlew check` 与 CI 走同一道门 ——
// 否则「CI 会拦，但我推送前不知道」等于把问题推给推送之后。
//
// 无 PowerShell 的环境（未装 pwsh 的 Linux）显式跳过并告警，不静默通过。
// ─────────────────────────────────────────────────────────────────────────────
val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

/** 可用的 PowerShell 调用前缀；找不到时为 null。 */
val powershellPrefix: List<String>? = if (isWindows) {
    // Windows 必带 powershell.exe（5.1）；装了 PowerShell 7 的机器也走同一条命令
    listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass")
} else {
    System.getenv("PATH")
        ?.split(java.io.File.pathSeparator)
        ?.map { java.io.File(it, "pwsh") }
        ?.firstOrNull { it.canExecute() }
        ?.let { listOf(it.absolutePath, "-NoProfile") }
}

val scanSecrets = if (powershellPrefix == null) {
    tasks.register("scanSecrets") {
        group = "verification"
        description = "扫描待提交文件中的凭据 / 隐私泄漏（本机无 PowerShell，跳过）"
        doLast {
            logger.warn("scanSecrets 已跳过：本机未找到 pwsh / powershell.exe。请在装有 PowerShell 的环境或 CI 上运行。")
        }
    }
} else {
    tasks.register<Exec>("scanSecrets") {
        group = "verification"
        description = "扫描待提交文件中的凭据 / 隐私泄漏（scripts/scan-secrets.ps1）"
        workingDir = rootDir
        commandLine(powershellPrefix + listOf("-File", "scripts/scan-secrets.ps1"))
    }
}

// 挂到各子项目的 check 上：`./gradlew check` / `build` 自动带上；
// 单跑 test / assemble 不受影响（本地跑测试不必每次扫全仓）。
subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(scanSecrets)
    }
}
