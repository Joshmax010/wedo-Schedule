$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$patterns = [ordered]@{
    'private key' = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    'authorization bearer' = '(?i)Authorization\s*[:=]\s*Bearer\s+[A-Za-z0-9._~+/=-]{16,}'
    'cookie header' = '(?im)^\s*Cookie\s*[:=]\s*(?!<|REDACTED|TEST)[^\r\n]{16,}'
    'hard-coded password' = '(?i)(?:password|passwd|pwd)\s*[:=]\s*["''][^"''\s]{8,}["'']'
}
$binaryExtensions = @('.png', '.jpg', '.jpeg', '.webp', '.gif', '.jar', '.zip', '.apk')
$findings = [System.Collections.Generic.List[string]]::new()

Push-Location $projectRoot
try {
    # Include untracked, non-ignored files locally; in CI every repository file is tracked.
    $candidateFiles = git ls-files --cached --others --exclude-standard
    foreach ($relativePath in $candidateFiles) {
        $fullPath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
        if ($binaryExtensions -contains [IO.Path]::GetExtension($fullPath).ToLowerInvariant()) { continue }

        try {
            $content = Get-Content -Raw -LiteralPath $fullPath -ErrorAction Stop
        } catch {
            continue
        }

        foreach ($entry in $patterns.GetEnumerator()) {
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
    Write-Error ("Potential credentials found in tracked files:`n" + ($findings -join "`n"))
    exit 1
}

Write-Output 'No high-confidence credential patterns found in repository candidate files.'
