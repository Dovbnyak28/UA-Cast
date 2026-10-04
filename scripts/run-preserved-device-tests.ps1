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
    if ($privateStateDirectories.Count -eq 0) { return }
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $AdbPath
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in (@('-s', $Serial, 'exec-out', 'run-as', $packageName, 'tar', '-cf', '-') + $privateStateDirectories)) {
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

function Assert-PrivateStateRestored {
    if ($privateStateDirectories.Count -eq 0) {
        Write-Host 'Original private file hashes verified: 0 (no original private directories)'
        return
    }
    # Compare file bytes, not just directory existence. Do not print private contents or hashes.
    Add-Type -AssemblyName System.Formats.Tar
    $inputArchive = [System.IO.File]::OpenRead($backupPath)
    $reader = [System.Formats.Tar.TarReader]::new($inputArchive)
    $verified = 0
    try {
        while ($null -ne ($entry = $reader.GetNextEntry())) {
            if ($null -eq $entry.DataStream) { continue }
            if ($entry.Name -notmatch '^(files|shared_prefs)/[a-zA-Z0-9_./-]+$' -or
                @($entry.Name.Split('/')).Contains('..')) {
                throw 'Unexpected private archive path; refuse a shell argument'
            }
            $expected = [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($entry.DataStream))
            $actual = (Invoke-AdbChecked @('shell', 'run-as', $packageName, '/system/bin/toybox',
                'sha256sum', $entry.Name) | Out-String).Trim().Split(' ')[0]
            if ($expected -ne $actual) { throw 'Restored private file differs from recovery archive' }
            $verified++
        }
    } finally { $reader.Dispose(); $inputArchive.Dispose() }
    Write-Host "Original private file hashes verified: $verified"
}

$actualDirectory = (Invoke-AdbChecked @('shell', 'run-as', $packageName, 'pwd') | Out-String).Trim()
if ($actualDirectory -notin @('/data/user/0/com.uacastplayer.debug', '/data/data/com.uacastplayer.debug')) {
    throw "Unexpected run-as directory: $actualDirectory"
}
# All subsequent move targets are literal children of this verified debug-app sandbox.
Invoke-AdbChecked @('shell', 'am', 'force-stop', $packageName)
$privateStateDirectories = @(foreach ($name in @('files', 'shared_prefs')) {
    $probe = & $AdbPath -s $Serial shell run-as $packageName ls -d $name 2>&1
    if ($LASTEXITCODE -eq 0) { $name }
    elseif (($probe | Out-String) -notmatch 'No such file or directory') {
        throw 'Unable to inspect original private directories; refuse to move any data'
    }
})
Save-PrivateStateArchive
if ($privateStateDirectories.Count -gt 0) {
    Write-Host "Private recovery archive (do not publish): $backupPath"
}
Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mkdir', $preservedDirectory)
$filesPreserved = $false
$preferencesPreserved = $false
try {
    if ($privateStateDirectories -contains 'files') {
        Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', 'files', "$preservedDirectory/files")
        $filesPreserved = $true
    }
    if ($privateStateDirectories -contains 'shared_prefs') {
        Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', 'shared_prefs', "$preservedDirectory/shared_prefs")
        $preferencesPreserved = $true
    }
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
        $name = [string]$entry[0]
        # An originally absent directory must stay absent after the test too. A failed move of an
        # existing original is different: leave that untouched original in place, never relocate it.
        if ($entry[1] -or $privateStateDirectories -notcontains $name) {
            # `test` is a shell builtin, not a runnable binary on some API-28 TV firmware.
            # A failed probe must never make mv nest the original folder inside fixture data.
            $probe = & $AdbPath -s $Serial shell run-as $packageName ls -d $name 2>&1
            $probeExit = $LASTEXITCODE
            if ($probeExit -eq 0) {
                Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', $name, "$preservedDirectory/test-$name")
            } elseif (($probe | Out-String) -notmatch 'No such file or directory') {
                throw 'Unable to inspect fixture directory; refusing to nest original data'
            }
            if ($entry[1]) {
                Invoke-AdbChecked @('shell', 'run-as', $packageName, 'mv', "$preservedDirectory/$name", $name)
            }
        }
    }
    Assert-PrivateStateRestored
    Write-Host "Original debug app data restored. Fixture data retained at $actualDirectory/$preservedDirectory"
}
Write-Host "Device regression passed: $logPath"
