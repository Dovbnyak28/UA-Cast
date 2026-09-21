param(
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$TestClasses = 'com.uacastplayer.player.PlayerLifecycleInstrumentedTest,com.uacastplayer.player.PlayerVideoFitInstrumentedTest'
)

# Instrumented fixtures replace playlists. Preserve the actual debug app's files/preferences,
# including a host-side emergency archive, instead of treating a developer phone as disposable.
$ErrorActionPreference = 'Stop'
$packageName = 'com.uacastplayer.debug'
$repoDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Resolve-UniversalApk([string]$RelativeDirectory, [string]$ExpectedApplicationId) {
    $apkDirectory = (Resolve-Path (Join-Path $repoDirectory $RelativeDirectory)).Path
    $metadata = Get-Content -LiteralPath (Join-Path $apkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $ExpectedApplicationId) {
        throw "Unexpected APK application ID in $RelativeDirectory"
    }
    $candidates = @($metadata.elements | Where-Object { @($_.filters).Count -eq 0 })
    if ($candidates.Count -ne 1) {
        throw "Expected one unfiltered APK in $RelativeDirectory; build a universal APK before running device tests"
    }
    $apkPath = (Resolve-Path (Join-Path $apkDirectory $candidates[0].outputFile)).Path
    $directoryPrefix = $apkDirectory.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $apkPath.StartsWith($directoryPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
        [System.IO.Path]::GetExtension($apkPath) -ne '.apk' -or
        (Get-Item -LiteralPath $apkPath).Length -eq 0) {
        throw "Invalid APK output in $RelativeDirectory"
    }
    return $apkPath
}

# Validate both artifacts before stopping the app or relocating any private data. AGP can
# produce app-debug.apk or app-universal-debug.apk depending on the requested variants.
$appApk = Resolve-UniversalApk 'app/build/outputs/apk/debug' $packageName
$testApk = Resolve-UniversalApk 'app/build/outputs/apk/androidTest/debug' "$packageName.test"
$artifactDirectory = Join-Path $repoDirectory 'app/build/device-audit'
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
$runId = [Guid]::NewGuid().ToString('N')
$preservedDirectory = "audit-preserved-$runId"
$backupPath = Join-Path $artifactDirectory "state-$runId.tar"
$logPath = Join-Path $artifactDirectory "instrumented-$runId.txt"

function Invoke-AdbChecked([string[]]$Arguments) {
    & $AdbPath -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments[0])" }
}

function Save-PrivateStateArchive {
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $AdbPath
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in @('-s', $Serial, 'exec-out', 'run-as', $packageName, 'tar', '-cf', '-', 'files', 'shared_prefs')) {
        $start.ArgumentList.Add($argument)
    }
    $process = [System.Diagnostics.Process]::Start($start)
    $errorRead = $process.StandardError.ReadToEndAsync()
    $destination = [System.IO.File]::Create($backupPath)
    try { $process.StandardOutput.BaseStream.CopyTo($destination) } finally { $destination.Dispose() }
    $process.WaitForExit()
    $errorText = $errorRead.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw "Private-state backup failed: $errorText" }
    $process.Dispose()
    if ((Get-Item -LiteralPath $backupPath).Length -lt 1024) { throw 'Private-state backup is unexpectedly empty' }
}

$actualDirectory = (Invoke-AdbChecked @('shell', 'run-as', $packageName, 'pwd') | Out-String).Trim()
if ($actualDirectory -ne '/data/user/0/com.uacastplayer.debug') {
    throw "Unexpected run-as directory: $actualDirectory"
}
# All subsequent move targets are literal children of this verified debug-app sandbox.
Invoke-AdbChecked @('shell', 'am', 'force-stop', $packageName)
Save-PrivateStateArchive
Write-Host "Private recovery archive (do not publish): $backupPath"
Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mkdir', $preservedDirectory)
$filesPreserved = $false
$preferencesPreserved = $false
try {
    Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', 'files', "$preservedDirectory/files")
    $filesPreserved = $true
    Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', 'shared_prefs', "$preservedDirectory/shared_prefs")
    $preferencesPreserved = $true
    Invoke-AdbChecked @('install', '-r', $appApk)
    Invoke-AdbChecked @('install', '-r', $testApk)
    Invoke-AdbChecked @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-AdbChecked @('shell', 'wm', 'dismiss-keyguard')
    $runner = "$packageName.test/androidx.test.runner.AndroidJUnitRunner"
    $output = & $AdbPath -s $Serial shell am instrument -w -e class $TestClasses $runner 2>&1
    $exitCode = $LASTEXITCODE
    $output | Tee-Object -FilePath $logPath
    if ($exitCode -ne 0 -or ($output -match 'FAILURES!!!') -or -not ($output -match 'OK \([0-9]+ tests?\)')) {
        throw "Instrumented run did not pass; see $logPath"
    }
} finally {
    Invoke-AdbChecked @('shell', 'am', 'force-stop', $packageName)
    # Retain fixture data in the preserved directory for diagnostics; never delete user data.
    foreach ($entry in @(@('files', $filesPreserved), @('shared_prefs', $preferencesPreserved))) {
        if ($entry[1]) {
            $name = [string]$entry[0]
            & $AdbPath -s $Serial shell run-as $packageName test -d $name
            if ($LASTEXITCODE -eq 0) {
                Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', $name, "$preservedDirectory/test-$name")
            }
            Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', "$preservedDirectory/$name", $name)
        }
    }
    Write-Host "Original debug app data restored. Fixture data retained at $actualDirectory/$preservedDirectory"
}
Write-Host "Device regression passed: $logPath"
