# Build and test the Android app with a complete JDK, avoiding JBR runtimes missing jlink.
# Usage (PowerShell): .\Build-Android.ps1 [-Offline] [-Clean] [-Fresh] [-ReleaseCandidate]
param(
    [switch]$Offline,
    [switch]$Clean,
    [switch]$Fresh,
    [switch]$ReleaseCandidate,
    [string]$SigningKeyStore,
    [string]$SigningKeyAlias,
    [string]$SigningCertificateSha256,
    [string]$SignedApkPath
)

$ErrorActionPreference = 'Stop'

function Get-ApkSha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

$signingRequested = $SigningKeyStore -or $SigningKeyAlias -or $SigningCertificateSha256 -or $SignedApkPath
if ($signingRequested) {
    if (-not ($ReleaseCandidate -and $SigningKeyStore -and $SigningKeyAlias -and $SigningCertificateSha256 -and $SignedApkPath)) {
        throw 'Signing requires -ReleaseCandidate, -SigningKeyStore, -SigningKeyAlias, -SigningCertificateSha256 and -SignedApkPath.'
    }
    $keyStore = Get-Item -LiteralPath $SigningKeyStore
    if ($keyStore.PSIsContainer) { throw 'The signing keystore must be a file.' }
    $expectedSigner = ($SigningCertificateSha256 -replace ':', '').ToLowerInvariant()
    if ($expectedSigner -notmatch '^[0-9a-f]{64}$') { throw 'Use a SHA-256 fingerprint for the release signing certificate.' }
    $outputApk = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($SignedApkPath)
    if ([IO.Path]::GetExtension($outputApk) -ine '.apk' -or
        -not (Test-Path -LiteralPath (Split-Path -Parent $outputApk) -PathType Container)) {
        throw 'Choose an .apk output in an existing directory.'
    }
    if (Test-Path -LiteralPath $outputApk) { throw "Signing output already exists: $outputApk" }

    $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
    foreach ($sdkRoot in $sdkRoots) {
        if (-not $sdkRoot) { continue }
        $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'lib\apksigner.jar')) -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'zipalign.exe')) } |
            Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
        if ($buildTools) { break }
    }
    if (-not $buildTools) { throw 'Android SDK Build Tools are required to sign the release candidate.' }
}

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$homes = @()
if (Test-Path (Join-Path $env:USERPROFILE '.jdks')) {
    $homes += @(Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory |
        Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
}
foreach ($root in @('C:\Program Files\Eclipse Adoptium','C:\Program Files\Java','C:\Program Files\Microsoft')) {
    if (Test-Path $root) {
        $homes += @(Get-ChildItem $root -Directory | Select-Object -ExpandProperty FullName)
    }
}
if ($env:JAVA_HOME) { $homes += $env:JAVA_HOME }
$jdk = $homes | Where-Object {
    (Test-Path (Join-Path $_ 'bin\java.exe')) -and
    (Test-Path (Join-Path $_ 'bin\javac.exe')) -and
    (Test-Path (Join-Path $_ 'bin\jlink.exe'))
} | Select-Object -First 1
if (-not $jdk) {
    throw 'No complete JDK with java.exe, javac.exe and jlink.exe found. Install JDK 21, not a JRE/JBR-only runtime.'
}
$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
Write-Output "Using complete JDK: $jdk"
$arguments = @(':app:assembleDebug', ':app:testDebugUnitTest', '--no-configuration-cache', '--console=plain')
if ($ReleaseCandidate) { $arguments += ':app:assembleRelease' }
if ($Clean) { $arguments = @('clean') + $arguments }
if ($Offline) { $arguments += '--offline' }
if ($Fresh) { $arguments += @('--no-build-cache', '--rerun-tasks') }
Push-Location $project
try {
    # Gradle and the SDK print warnings on stderr (for example "SDK XML version 4"). Under 'Stop',
    # Windows PowerShell turns the first such line into a terminating error when output is
    # redirected, which kills the build before it compiles. The exit code is the real verdict.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & (Join-Path $project 'gradlew.bat') @arguments
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle build or unit tests failed (exit $LASTEXITCODE)." }
    $apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw "Gradle returned success but no debug APK exists at $apk" }
    $tests = Get-ChildItem (Join-Path $project 'app\build\test-results\testDebugUnitTest') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue
    if (@($tests).Count -eq 0) { throw 'Unit test task returned success but zero test reports were produced.' }
    Write-Output ("APK verified: " + $apk + " (" + (Get-Item $apk).Length + " bytes)")
    Write-Output ("JUnit XML reports: " + @($tests).Count)
    if ($ReleaseCandidate) {
        $candidate = Join-Path $project 'app\build\outputs\apk\release\app-release-unsigned.apk'
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            throw "Gradle returned success but no unsigned release APK exists at $candidate"
        }
        $hash = Get-ApkSha256 $candidate
        Write-Output "Unsigned release candidate: $candidate"
        Write-Output "Unsigned release candidate SHA-256: $hash"
        if ($signingRequested) {
            $apksigner = Join-Path $buildTools.FullName 'lib\apksigner.jar'
            $java = Join-Path $jdk 'bin\java.exe'
            $zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
            $temporaryApk = Join-Path (Split-Path -Parent $outputApk) ('.orbit-signing-' + [guid]::NewGuid().ToString('N') + '.apk')
            try {
                & $zipalign -c -P 16 4 $candidate
                if ($LASTEXITCODE -ne 0) { throw 'Unsigned APK alignment check failed.' }
                & $java -jar $apksigner sign --ks $keyStore.FullName --ks-key-alias $SigningKeyAlias `
                    --v4-signing-enabled false --in $candidate --out $temporaryApk
                if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }

                $verification = & $java -jar $apksigner verify --print-certs $temporaryApk
                if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
                $digests = @($verification | ForEach-Object {
                    if ($_ -match '^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$') {
                        $Matches[1].ToLowerInvariant()
                    }
                })
                if ($digests.Count -ne 1 -or $digests[0] -ne $expectedSigner) {
                    throw 'The APK signing certificate does not match the expected release certificate.'
                }
                $signedHash = Get-ApkSha256 $temporaryApk
                [IO.File]::Move($temporaryApk, $outputApk)
                Write-Output "Signed release APK: $outputApk"
                Write-Output "Signed release APK SHA-256: $signedHash"
                Write-Output "Release signing certificate SHA-256: $expectedSigner"
            } finally {
                [IO.File]::Delete($temporaryApk)
            }
        }
    }
} finally {
    Pop-Location
}
