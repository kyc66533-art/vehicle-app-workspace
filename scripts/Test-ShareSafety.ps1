[CmdletBinding()]
param([Parameter(Mandatory = $true)][string[]]$Paths)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$rootPrefix = $root.TrimEnd('\') + '\'
$blockedPath = '(?i)(^|/)(\.git|\.codex|\.agents|\.aws|\.ssh|\.idea|\.gradle|\.gradle-user-home|\.kotlin|\.tools|\.sync|build)(/|$)|(^|/)(\.env[^/]*|local\.properties|credentials(?:\.[^/]*)?|secrets\.[^/]*|tokens?\.json|google-services\.json|service-account[^/]*\.json|client_secret[^/]*\.json|keystore\.properties|\.git-credentials|\.netrc)$|\.(jks|keystore|p12|pfx|pem|key|apk|aab|ap_|class|log)$'
$secretPattern = '(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|https?://[^\s/@]+:[^\s/@]+@'
$allowedExtensions = @('.md', '.kt', '.kts', '.xml', '.properties', '.ps1', '.json', '.txt')

foreach ($relativePath in $Paths) {
    $normalized = $relativePath.Replace('\', '/')
    $fullPath = [IO.Path]::GetFullPath((Join-Path $root $relativePath))
    if (-not $fullPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside workspace: $relativePath"
    }
    if ($normalized -match $blockedPath) { throw "Blocked file path: $relativePath" }
    $item = Get-Item -LiteralPath $fullPath -Force
    $cursor = $item
    while ($cursor.FullName -ne $root) {
        if ($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Links are not shared automatically: $relativePath"
        }
        $cursor = if ($cursor.PSIsContainer) { $cursor.Parent } else { $cursor.Directory }
        if ($null -eq $cursor) { throw "Invalid file ancestry: $relativePath" }
    }
    $extension = [IO.Path]::GetExtension($fullPath)
    if ($item.PSIsContainer -or ($extension -notin $allowedExtensions -and $item.Name -ne '.gitignore')) {
        throw "File type requires explicit review: $relativePath"
    }
    if ($item.Length -gt 1MB) { throw "Large file requires explicit review: $relativePath" }
    $content = [IO.File]::ReadAllText($fullPath)
    if ($content.IndexOf([char]0) -ge 0) { throw "Binary content is not allowed: $relativePath" }
    if ($content -match $secretPattern) { throw "Potential credential detected in: $relativePath (value hidden)" }
}
Write-Output "PASS: $($Paths.Count) explicitly selected text files passed path and credential-pattern checks."
