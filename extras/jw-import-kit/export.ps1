param([switch]$AllowDirty)
$ErrorActionPreference = 'Stop'
$kitRoot = $PSScriptRoot
$repoRoot = (git -C $kitRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Run from the wedo Git source checkout.' }
$relativeKit = [IO.Path]::GetRelativePath($repoRoot, $kitRoot).Replace('\', '/')
if (-not $AllowDirty -and @(git -C $repoRoot status --porcelain -- $relativeKit).Count) {
    throw 'Commit kit changes before an official export, or use -AllowDirty only for preview.'
}
$commit = (git -C $repoRoot rev-parse HEAD).Trim()
$version = '0.1.0'
$name = "wedo-jw-import-kit-$version"
$outputDir = Join-Path $repoRoot 'releases'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$zipPath = Join-Path $outputDir "$name.zip"
if (Test-Path -LiteralPath $zipPath) { throw "Output already exists: $zipPath. Preserve or remove the old generated export explicitly." }
$relativeFiles = [Collections.Generic.List[string]]::new()
foreach ($file in @('.gitignore','.gitattributes','README.md','NOTICE','LICENSE','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','export.ps1','library/build.gradle.kts','library/consumer-rules.pro','demo/build.gradle.kts')) {
    $relativeFiles.Add($file)
}
foreach ($folder in @('gradle/wrapper','LICENSES','library/src','demo/src')) {
    $base = Join-Path $kitRoot $folder
    foreach ($file in Get-ChildItem -LiteralPath $base -File -Recurse -Force) {
        $relativeFiles.Add([IO.Path]::GetRelativePath($kitRoot, $file.FullName).Replace('\', '/'))
    }
}
foreach ($relative in $relativeFiles) {
    $item = Get-Item -LiteralPath (Join-Path $kitRoot $relative)
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refuse a linked file: $relative" }
    if ($relative -match '(?i)\.(har|apk|aar|jks|keystore|key|pem)$|local\.properties|(^|/)(build|private|\.git|\.gradle)/') {
        throw "Unexpected private/generated file: $relative"
    }
}
Add-Type -AssemblyName System.IO.Compression
$stream = [IO.File]::Open($zipPath, [IO.FileMode]::CreateNew)
$archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($relative in @($relativeFiles | Sort-Object -Unique)) {
        $entry = $archive.CreateEntry("$name/$relative", [IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
        $entry.ExternalAttributes = if ($relative -eq 'gradlew') { [int]0x81ED -shl 16 } else { [int]0x81A4 -shl 16 }
        $input = [IO.File]::OpenRead((Join-Path $kitRoot $relative))
        $target = $entry.Open()
        try { $input.CopyTo($target) } finally { $input.Dispose(); $target.Dispose() }
    }
    $entry = $archive.CreateEntry("$name/SOURCE_COMMIT.txt")
    $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
    try { $writer.Write("Repository: https://github.com/Joshmax010/wedo-Schedule`nCommit: $commit`nKit version: $version`nPreview dirty export: $([bool]$AllowDirty)`n") } finally { $writer.Dispose() }
} finally { $archive.Dispose(); $stream.Dispose() }
$hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$zipPath.sha256", "$hash  $name.zip`n", [Text.UTF8Encoding]::new($false))
Write-Output "ZIP: $zipPath"
Write-Output "SHA-256: $hash"
Write-Output "Source files: $($relativeFiles.Count)"
