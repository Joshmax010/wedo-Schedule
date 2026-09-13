param(
    [Parameter(Mandatory = $true)]
    [string]$HarPath,
    [string]$OutputDirectory = "test/fixtures/jlju",
    [ValidateSet("normal", "empty")]
    [string]$CaptureKind = "normal"
)

$ErrorActionPreference = "Stop"

function Get-HarResponseText {
    param([Parameter(Mandatory = $true)]$Entry)

    $text = [string]$Entry.response.content.text
    if ($Entry.response.content.encoding -eq "base64") {
        return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($text))
    }
    return $text
}

function Find-EntryByPath {
    param(
        [Parameter(Mandatory = $true)]$Entries,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Method
    )

    return $Entries | Where-Object {
        $uri = [Uri]$_.request.url
        $uri.AbsolutePath -eq $Path -and (!$Method -or $_.request.method -eq $Method)
    } | Select-Object -First 1
}

function Add-StableAlias {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Aliases,
        [AllowEmptyString()][string]$Original,
        [Parameter(Mandatory = $true)][string]$Prefix
    )

    if ([string]::IsNullOrWhiteSpace($Original)) { return "" }
    if (!$Aliases.ContainsKey($Original)) {
        $Aliases[$Original] = "{0}{1:D2}" -f $Prefix, ($Aliases.Count + 1)
    }
    return $Aliases[$Original]
}

$resolvedHar = (Resolve-Path -LiteralPath $HarPath).Path
$resolvedOutput = [IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
[IO.Directory]::CreateDirectory($resolvedOutput) | Out-Null

$har = Get-Content -LiteralPath $resolvedHar -Raw | ConvertFrom-Json
$entries = @($har.log.entries)

$indexPath = "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
$schedulePath = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html"
$calendarPath = "/jwglxt/kbcx/xskbcx_cxRsd.html"
$sectionPath = "/jwglxt/kbcx/xskbcx_cxRjc.html"

$indexEntry = Find-EntryByPath -Entries $entries -Path $indexPath -Method "GET"
$scheduleEntry = Find-EntryByPath -Entries $entries -Path $schedulePath -Method "POST"
$calendarEntry = Find-EntryByPath -Entries $entries -Path $calendarPath -Method "POST"
$sectionEntry = Find-EntryByPath -Entries $entries -Path $sectionPath -Method "POST"

if ($null -eq $scheduleEntry) { throw "Missing JLJU schedule request" }
if ($CaptureKind -eq "normal" -and $null -eq $indexEntry) {
    throw "Missing JLJU timetable index request"
}

$scheduleJson = (Get-HarResponseText -Entry $scheduleEntry) | ConvertFrom-Json
if ($null -eq $scheduleJson.PSObject.Properties["kbList"]) {
    throw "Captured schedule does not contain a kbList field"
}
$rawCourses = @($scheduleJson.kbList)
if ($CaptureKind -eq "normal" -and $rawCourses.Count -eq 0) {
    throw "Normal capture contains no kbList records"
}
if ($CaptureKind -eq "empty" -and $rawCourses.Count -ne 0) {
    throw "Empty capture unexpectedly contains kbList records"
}

if ($CaptureKind -eq "empty") {
    $safeEmptySchedule = [ordered]@{
        _fixture = [ordered]@{
            school = "吉林建筑大学"
            source = "verified-local-har"
            captureKind = "empty-schedule"
            sanitized = $true
            recordCount = 0
        }
        kbList = @()
    }

    $safeEmptySchedule |
        ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $resolvedOutput "empty_schedule_response.json") -Encoding utf8

    Write-Output "Created sanitized JLJU empty-schedule fixture from local HAR."
    Write-Output "Courses: 0."
    exit 0
}

$courseAliases = @{}
$teacherAliases = @{}
$roomAliases = @{}

$safeCourses = foreach ($course in $rawCourses) {
    [ordered]@{
        kcmc = Add-StableAlias -Aliases $courseAliases -Original ([string]$course.kcmc) -Prefix "测试课程"
        xm = Add-StableAlias -Aliases $teacherAliases -Original ([string]$course.xm) -Prefix "测试教师"
        cdmc = Add-StableAlias -Aliases $roomAliases -Original ([string]$course.cdmc) -Prefix "测试教室"
        xqj = [string]$course.xqj
        xqjmc = [string]$course.xqjmc
        jc = [string]$course.jc
        jcs = [string]$course.jcs
        zcd = [string]$course.zcd
        xnm = [string]$course.xnm
        xqm = [string]$course.xqm
        xkbz = if ([string]::IsNullOrWhiteSpace([string]$course.xkbz)) { "" } else { "测试备注" }
    }
}

$safeSchedule = [ordered]@{
    _fixture = [ordered]@{
        school = "吉林建筑大学"
        source = "verified-local-har"
        captureKind = "normal-schedule"
        sanitized = $true
        recordCount = $safeCourses.Count
    }
    kbList = @($safeCourses)
}

$safeSchedule | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $resolvedOutput "schedule_response.json") -Encoding utf8

if ($null -ne $calendarEntry) {
    $calendarJson = (Get-HarResponseText -Entry $calendarEntry) | ConvertFrom-Json
    $safeCalendar = @($calendarJson) | ForEach-Object {
        [ordered]@{
            date = [string]$_.date
            day = [string]$_.day
            rsdm = [string]$_.rsdm
            rsdmc = [string]$_.rsdmc
            xqh_id = [string]$_.xqh_id
        }
    }
    ConvertTo-Json -InputObject @($safeCalendar) -Depth 5 |
        Set-Content -LiteralPath (Join-Path $resolvedOutput "calendar_response.json") -Encoding utf8
}

if ($null -ne $sectionEntry) {
    $sectionJson = (Get-HarResponseText -Entry $sectionEntry) | ConvertFrom-Json
    $safeSections = @($sectionJson) | ForEach-Object {
        [ordered]@{
            jcmc = [string]$_.jcmc
            qssj = [string]$_.qssj
            jssj = [string]$_.jssj
            rsdm = [string]$_.rsdm
            rsdmc = [string]$_.rsdmc
        }
    }
    ConvertTo-Json -InputObject @($safeSections) -Depth 5 |
        Set-Content -LiteralPath (Join-Path $resolvedOutput "sections_response.json") -Encoding utf8
}

$indexHtml = Get-HarResponseText -Entry $indexEntry
$termSelects = foreach ($id in @("xnm", "xqm")) {
    $selectMatch = [regex]::Match(
        $indexHtml,
        "<select[^>]*id=[`"']$id[`"'][^>]*>([\s\S]*?)</select>",
        [Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (!$selectMatch.Success) { throw "Missing term selector: $id" }

    $options = [regex]::Matches(
        $selectMatch.Groups[1].Value,
        "<option[^>]*value=[`"']([^`"']*)[`"']([^>]*)>([^<]*)</option>",
        [Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if ($options.Count -eq 0) { throw "Term selector has no options: $id" }

    $lines = foreach ($option in $options) {
        $selected = if ($option.Groups[2].Value -match "selected") { " selected" } else { "" }
        "    <option value=`"$($option.Groups[1].Value)`"$selected>$($option.Groups[3].Value.Trim())</option>"
    }
    "  <select id=`"$id`">`n$($lines -join "`n")`n  </select>"
}

@"
<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8"><title>JLJU 学期选择脱敏夹具</title></head>
<body>
$($termSelects -join "`n")
</body>
</html>
"@ | Set-Content -LiteralPath (Join-Path $resolvedOutput "terms_response.html") -Encoding utf8

function Get-RequestDescription {
    param([AllowNull()]$Entry)
    if ($null -eq $Entry) { return $null }
    $uri = [Uri]$Entry.request.url
    return [ordered]@{
        method = [string]$Entry.request.method
        host = $uri.Host
        path = $uri.AbsolutePath
        queryParameterNames = @($Entry.request.queryString | ForEach-Object { $_.name })
        formParameterNames = @(
            $Entry.request.postData.params |
                Where-Object { $null -ne $_.name } |
                ForEach-Object { $_.name }
        )
        contentType = [string]$Entry.request.postData.mimeType
        responseMimeType = [string]$Entry.response.content.mimeType
        observedStatus = [int]$Entry.response.status
    }
}

$requestStructure = [ordered]@{
    schoolId = "jlju"
    capturedAt = $har.log.pages[0].startedDateTime.ToUniversalTime().ToString("o")
    timetableIndex = Get-RequestDescription -Entry $indexEntry
    schedule = Get-RequestDescription -Entry $scheduleEntry
    calendarDays = Get-RequestDescription -Entry $calendarEntry
    sections = Get-RequestDescription -Entry $sectionEntry
    sensitiveHeaderValuesIncluded = $false
}

$requestStructure | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $resolvedOutput "request_structure.json") -Encoding utf8

Write-Output "Created sanitized JLJU fixtures from local HAR."
Write-Output "Courses: $($safeCourses.Count); aliases: $($courseAliases.Count) courses, $($teacherAliases.Count) teachers, $($roomAliases.Count) rooms."
