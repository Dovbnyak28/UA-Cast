param([switch]$AddToUserPath)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$version = '2.11.0'
$expectedSha256 = '5384593cb4e7a106489e75a821d157dd43f4e438df6bc308b72e82c685e1283a'
$installDirectory = Join-Path $repoRoot ".tools/maestro-$version"
$launcher = Join-Path $installDirectory 'maestro/bin/maestro.bat'
$archiveDirectory = Join-Path $repoRoot 'build/tool-downloads'
$archive = Join-Path $archiveDirectory "maestro-$version.zip"

if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    if (Test-Path -LiteralPath $installDirectory) {
        throw "Incomplete installation at $installDirectory. Inspect it before replacing anything."
    }
    New-Item -ItemType Directory -Path $archiveDirectory -Force | Out-Null
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri "https://github.com/mobile-dev-inc/Maestro/releases/download/cli-$version/maestro.zip" -OutFile $archive
    }
    $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        throw 'Maestro archive checksum mismatch. Refusing to extract or execute it.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $installDirectory
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw 'The verified archive has an unexpected launcher layout.'
    }
    Write-Host "Verified and installed Maestro $version."
}

$env:MAESTRO_CLI_NO_ANALYTICS = 'true'
$env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = 'true'
$env:MAESTRO_DISABLE_UPDATE_CHECK = 'true'
& $launcher --version
if ($LASTEXITCODE -ne 0) { throw 'Maestro version check failed.' }

if ($AddToUserPath) {
    $binDirectory = Split-Path $launcher -Parent
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $entries = @($userPath -split ';' | Where-Object { $_ })
    if ($binDirectory.TrimEnd('\') -notin @($entries | ForEach-Object { $_.TrimEnd('\') })) {
        $updatedPath = (@($entries) + $binDirectory) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $updatedPath, 'User')
        Write-Host 'Added only the Maestro bin directory to user PATH; existing entries were preserved.'
    }
    $env:Path = "$binDirectory;$env:Path"
}
