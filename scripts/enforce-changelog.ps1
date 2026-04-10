$ErrorActionPreference = "Stop"

try {
    $stdinText = [Console]::In.ReadToEnd()
} catch {
    $stdinText = ""
}

# Only enforce when the agent is attempting to complete a task.
$isTaskCompleteCall = $false
if ($stdinText) {
    if ($stdinText -match '"task_complete"' -or $stdinText -match 'task_complete') {
        $isTaskCompleteCall = $true
    }
}

if (-not $isTaskCompleteCall) {
    Write-Output '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"Not a task completion tool call."}}'
    exit 0
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$statusLines = git -C $repoRoot --no-optional-locks status --porcelain

if (-not $statusLines) {
    Write-Output '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"No repository changes detected."}}'
    exit 0
}

$changedFiles = @()
foreach ($line in $statusLines) {
    if ($line.Length -lt 4) { continue }
    $path = $line.Substring(3).Trim()
    if ($path -match ' -> ') {
        $path = ($path -split ' -> ')[-1]
    }
    if ($path) {
        $changedFiles += ($path -replace '\\', '/')
    }
}

$changelogPath = 'app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt'

$requiresChangelog = $false
foreach ($file in $changedFiles) {
    if ($file -ne $changelogPath) {
        $requiresChangelog = $true
        break
    }
}

$hasChangelogChange = $changedFiles -contains $changelogPath

if ($requiresChangelog -and -not $hasChangelogChange) {
    $reason = "Blocked: repository changes detected without updating ChangelogData.kt. Add changelog entry before completing."
    $safeReason = $reason.Replace('"', '\"')
    Write-Output "{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\",\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"$safeReason\"}}"
    exit 2
}

Write-Output '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"Changelog policy satisfied."}}'
exit 0