<#
.SYNOPSIS
    仓库密钥 / 隐私泄漏门禁扫描。

.DESCRIPTION
    wedo 的不变量（见 docs/PROJECT_HANDOFF.md）：不得提交真实学号、Cookie、HAR、
    课表、签名密钥。本脚本扫描「下次提交可能带上的文件」——已跟踪 + 未跟踪但未被
    .gitignore 排除的文件（CI 上是干净检出，等价于全部仓库文件），命中即以退出码 1 失败。

    两类规则：

      1. 文件名规则 —— 这些后缀本身就是凭据载体（.jks / .keystore / .har …）。
         它们虽已被 .gitignore 覆盖，但 `git add -f` 仍能强行入库，
         且从那一刻起 .gitignore 就再也拦不住了，故单独再拦一道。

      2. 内容规则 —— 高置信度凭据形态：私钥块、Bearer、Cookie 头、会话号、
         CSRF token、学员登录号、硬编码口令。

    设计取向：**只收高置信度形态，宁可漏报不做宽泛匹配。**
    门禁一旦开始误报，人就会习惯性绕过它，比没有门禁更糟。
    例：`csrftoken` 与 `yhm` 都要求值长度 / 纯数字达标，才不会打中
    `app/src/test/resources/zf-new/*.html` 里那种 12 位的合成占位值。

    调用方式：
      本地    ./gradlew scanSecrets          （Windows 上自动找 powershell.exe / pwsh）
      CI      ./scripts/scan-secrets.ps1     （.github/workflows/android.yml）

    输出约定：首行带 ASCII 状态词 `scan-secrets: PASS` / `scan-secrets: FAIL`。
    原因是 Windows 上 Gradle 控制台按 CP936 解码子进程的 UTF-8 输出，中文会变乱码，
    只有 ASCII 部分保证可读；判定通过与否一律看**退出码**，不看文字。
#>

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot

# ── 1. 文件名规则 ────────────────────────────────────────────────────────────
$forbiddenNamePatterns = [ordered]@{
    'keystore file'     = '\.(?:jks|keystore|p12|pfx|pepk)$'
    'private key file'  = '\.(?:pem|key)$'
    'har capture'       = '\.har$'
    'session dump'      = '(?i)^(?:cookies|session|credentials)\.json$'
    'keystore props'    = '(?i)^keystore\.properties$'
}

# ── 2. 内容规则 ──────────────────────────────────────────────────────────────
$contentPatterns = [ordered]@{
    'private key block'     = '-----BEGIN (?:RSA |EC |OPENSSH |PGP )?PRIVATE KEY-----'
    'authorization bearer'  = '(?i)Authorization\s*[:=]\s*Bearer\s+[A-Za-z0-9._~+/=-]{16,}'
    'cookie header'         = '(?im)^\s*Cookie\s*[:=]\s*(?!<|REDACTED|TEST)[^\r\n]{16,}'
    'hard-coded password'   = '(?i)(?:password|passwd|pwd)\s*[:=]\s*["''][^"''\s]{8,}["'']'
    'session id value'      = '(?i)\bJSESSIONID\s*[=:]\s*["'']?[A-Za-z0-9._-]{16,}'
    'csrf token value'      = '(?i)\bcsrftoken\s*[=:]\s*["'']?[A-Za-z0-9._-]{16,}'
    'student login id'      = '(?i)\b(?:yhm|学号)\s*[=:：]\s*["'']?[0-9]{8,}'
}

# 二进制不按文本读 —— 除了省时间，也避免把 keystore 内容当乱码文本误报
$binaryExtensions = @(
    '.png', '.jpg', '.jpeg', '.webp', '.gif', '.ico', '.ttf', '.otf', '.woff', '.woff2',
    '.jar', '.zip', '.gz', '.apk', '.aab', '.jks', '.keystore', '.p12', '.pfx', '.pem', '.key'
)

$findings = [System.Collections.Generic.List[string]]::new()
$scannedContentCount = 0

Push-Location $projectRoot
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw 'git 不在 PATH 上，无法枚举待提交文件。'
    }

    $candidateFiles = @(git ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'git ls-files --cached --others --exclude-standard 执行失败。' }

    $trackedFiles = @(git ls-files --cached)
    if ($LASTEXITCODE -ne 0) { throw 'git ls-files --cached 执行失败。' }

    # ── 文件名规则：只看已跟踪文件 ──
    # 未跟踪的凭据文件被 .gitignore 挡住是正常状态；真正危险的是「已经被跟踪」，
    # 那意味着它进过仓库，或者有人用了 git add -f。
    foreach ($relativePath in $trackedFiles) {
        $fileName = Split-Path -Leaf $relativePath
        foreach ($entry in $forbiddenNamePatterns.GetEnumerator()) {
            if ([regex]::IsMatch($fileName, $entry.Value)) {
                $findings.Add("${relativePath}: tracked $($entry.Key) — 凭据载体不得入库（gitignore 挡不住 git add -f）")
            }
        }
    }

    # ── 内容规则：扫全部候选文件 ──
    foreach ($relativePath in $candidateFiles) {
        $fullPath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
        if ($binaryExtensions -contains [IO.Path]::GetExtension($fullPath).ToLowerInvariant()) { continue }

        try {
            $content = Get-Content -Raw -LiteralPath $fullPath -ErrorAction Stop
        } catch {
            continue
        }
        if ([string]::IsNullOrEmpty($content)) { continue }
        $scannedContentCount++

        foreach ($entry in $contentPatterns.GetEnumerator()) {
            foreach ($match in [regex]::Matches($content, $entry.Value)) {
                $line = 1 + ($content.Substring(0, $match.Index).Split("`n").Count - 1)
                $findings.Add("${relativePath}:${line}: $($entry.Key)")
            }
        }
    }
} finally {
    Pop-Location
}

if ($findings.Count -gt 0) {
    Write-Error ("scan-secrets: FAIL —— 发现疑似凭据 / 隐私泄漏，共 $($findings.Count) 处：`n" + ($findings -join "`n") +
        "`n`n确认是合成测试值的话，改短 / 改字母占位即可绕过误报；确认是真实凭据的话，清掉并轮换该凭据。")
    exit 1
}

Write-Output "scan-secrets: PASS —— 文件名规则 $($forbiddenNamePatterns.Count) 条 / 内容规则 $($contentPatterns.Count) 条，已扫 $scannedContentCount 个文本文件。"
