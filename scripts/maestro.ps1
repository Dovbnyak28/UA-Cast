param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MaestroArguments)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$launcher = Join-Path $repoRoot '.tools/maestro-2.11.0/maestro/bin/maestro.bat'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw 'Run scripts/setup-maestro.ps1 first.'
}
if (-not $env:JAVA_HOME) {
    $java = (Get-Command java -ErrorAction Stop).Source
    $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
}
$env:Path = "$(Join-Path $env:ANDROID_HOME 'platform-tools');$env:Path"
$env:MAESTRO_CLI_NO_ANALYTICS = 'true'
$env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = 'true'
$env:MAESTRO_DISABLE_UPDATE_CHECK = 'true'
& $launcher @MaestroArguments
exit $LASTEXITCODE
