Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRootForWsl = ($repoRoot -replace '\\', '/')
$wslRepoRoot = (& wsl.exe wslpath -au "$repoRootForWsl").Trim()

if (-not $wslRepoRoot) {
    throw "Failed to resolve the repository path inside WSL."
}

& wsl.exe bash -lc "cd '$wslRepoRoot' && bash scripts/build_linux_package.sh"
