[CmdletBinding()]
param()

# Pushes an already committed, verified HEAD. Never stages, commits, merges or resets.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$statePath = Join-Path $root '.sync'
$recordPath = Join-Path $statePath 'validated-commit.json'
$expectedRemote = 'https://github.com/kyc66533-art/vehicle-app-workspace.git'
$previousPrompt = $env:GIT_TERMINAL_PROMPT
$previousGcmInteractive = $env:GCM_INTERACTIVE
$originalLocation = Get-Location

function Invoke-Git {
    param([string[]]$GitArgs)
    $result = @(& git @GitArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        # Avoid copying authentication/remote output into persistent logs.
        throw "Git step failed: $($GitArgs[0]). Check network, authentication, permissions and repository state."
    }
    return $result
}

try {
    Set-Location -LiteralPath $root
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git is not installed or not on PATH.' }
    $env:GIT_TERMINAL_PROMPT = '0'
    $env:GCM_INTERACTIVE = 'Never'
    $repoRoot = (Invoke-Git -GitArgs @('rev-parse', '--show-toplevel')) -join ''
    if ([IO.Path]::GetFullPath($repoRoot) -ne [IO.Path]::GetFullPath($root)) { throw 'Unexpected repository root.' }
    if (((Invoke-Git -GitArgs @('branch', '--show-current')) -join '') -ne 'main') { throw 'Only main is configured for publishing.' }
    $fetchUrls = @(Invoke-Git -GitArgs @('remote', 'get-url', '--all', 'origin'))
    $pushUrls = @(Invoke-Git -GitArgs @('remote', 'get-url', '--push', '--all', 'origin'))
    if ($fetchUrls.Count -ne 1 -or $pushUrls.Count -ne 1 -or $fetchUrls[0] -cne $expectedRemote -or $pushUrls[0] -cne $expectedRemote) {
        throw 'Origin fetch/push URL must exactly match the configured HTTPS repository.'
    }
    if (@(Invoke-Git -GitArgs @('status', '--porcelain', '--untracked-files=all')).Count -gt 0) {
        throw 'Working tree is not clean. Existing changes are preserved; nothing was uploaded.'
    }
    if (-not (Test-Path -LiteralPath $recordPath)) { throw 'No validated commit record. Nothing was uploaded.' }
    $record = Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $headSha = (Invoke-Git -GitArgs @('rev-parse', 'HEAD')) -join ''
    if ($record.commit -cne $headSha -or $headSha -notmatch '^[0-9a-f]{40}$') { throw 'Validation record does not match HEAD.' }
    if ($record.status -cne 'passed' -or @($record.checks).Count -eq 0) { throw 'Validation has not passed.' }
    foreach ($check in $record.checks) {
        if ([string]::IsNullOrWhiteSpace($check.name) -or $check.exitCode -ne 0) { throw 'A required check did not pass.' }
    }
    $reportPath = Join-Path $root 'docs\CODEX_PROGRESS.md'
    if ((Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash -cne $record.reportSha256) {
        throw 'Progress report hash does not match validation record.'
    }
    $tracked = @(Invoke-Git -GitArgs @('-c', 'core.quotepath=false', 'ls-files'))
    & (Join-Path $PSScriptRoot 'Test-ShareSafety.ps1') -Paths $tracked
    Invoke-Git -GitArgs @('fetch', '--no-tags', 'origin', 'main') | Out-Null
    & git merge-base --is-ancestor origin/main HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Remote is ahead or histories diverged. No automatic merge or overwrite is allowed.' }
    $pending = @(Invoke-Git -GitArgs @('rev-list', 'origin/main..HEAD'))
    if ($pending.Count -eq 0) { Write-Output 'Already synchronized. No changes uploaded.'; exit 0 }
    $changed = @(Invoke-Git -GitArgs @('-c', 'core.quotepath=false', 'diff', '--name-only', 'origin/main..HEAD'))
    if ('docs/CODEX_PROGRESS.md' -notin $changed) { throw 'Pending work must include an updated progress report.' }
    if (@($changed | Where-Object { $_ -match '\.(kt|kts|xml|properties)$|(^|/)gradlew(\.bat)?$' }).Count -gt 0) {
        foreach ($required in @('assembleDebug', 'lintDebug', 'testDebugUnitTest')) {
            if (@($record.checks | Where-Object { $_.name -ceq $required -and $_.exitCode -eq 0 }).Count -ne 1) {
                throw "App changes require a successful $required check for this HEAD."
            }
        }
    }
    # Recheck after validation/fetch so work begun in another terminal stops publication.
    if (((Invoke-Git -GitArgs @('rev-parse', 'HEAD')) -join '') -cne $headSha -or @(Invoke-Git -GitArgs @('status', '--porcelain', '--untracked-files=all')).Count -gt 0) {
        throw 'Repository changed during preflight. Nothing was uploaded.'
    }
    Invoke-Git -GitArgs @('push', 'origin', "${headSha}:refs/heads/main") | Out-Null
    $remoteHead = ((Invoke-Git -GitArgs @('ls-remote', '--heads', 'origin', 'refs/heads/main')) -join '') -split '\s+'
    if ($remoteHead[0] -cne $headSha) { throw 'Push returned, but remote HEAD verification did not match.' }
    Write-Output "Uploaded and verified commit: $headSha"
} catch {
    New-Item -ItemType Directory -Path $statePath -Force | Out-Null
    $message = "$(Get-Date -Format o) $($_.Exception.Message)"
    Add-Content -LiteralPath (Join-Path $statePath 'publish-errors.log') -Value $message -Encoding UTF8
    Write-Error $message
    exit 1
} finally {
    $env:GIT_TERMINAL_PROMPT = $previousPrompt
    $env:GCM_INTERACTIVE = $previousGcmInteractive
    Set-Location -LiteralPath $originalLocation
}
