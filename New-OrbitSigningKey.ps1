# Creates Orbit's permanent release signing key. Run it yourself: YOU choose and type the password.
# The password is never written to disk or shown; it only lives in this window while keytool runs.
# The key is saved OUTSIDE the source tree, and an existing key is never overwritten.
param(
    [string]$KeyStore = (Join-Path $env:USERPROFILE 'OrbitSigning\orbit-release.p12'),
    [string]$Alias = 'orbit'
)
$ErrorActionPreference = 'Stop'

if (Test-Path -LiteralPath $KeyStore) {
    Write-Host "A signing key already exists at $KeyStore. Nothing was changed." -ForegroundColor Yellow
    Write-Host 'Never replace it: phones only accept updates signed with that same key.'
    return
}

$homes = @()
if ($env:JAVA_HOME) { $homes += $env:JAVA_HOME }
$jdks = Join-Path $env:USERPROFILE '.jdks'
if (Test-Path $jdks) { $homes += @(Get-ChildItem $jdks -Directory | Sort-Object Name -Descending | ForEach-Object FullName) }
$keytool = $homes | ForEach-Object { Join-Path $_ 'bin\keytool.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $keytool) { throw 'Could not find keytool.exe (part of a JDK). Install JDK 21 or set JAVA_HOME.' }

Write-Host ''
Write-Host 'ORBIT RELEASE SIGNING KEY' -ForegroundColor Cyan
Write-Host 'This key proves future Orbit updates come from you. Choose a strong password (12+ characters)'
Write-Host 'and store it in a password manager. If you lose the key or its password, you can never update'
Write-Host 'the app again under the same identity. Never share either with anyone (including AI assistants).'
Write-Host ''

function Read-Plain([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

while ($true) {
    $first = Read-Plain 'Choose a password'
    if ($first.Length -lt 12) { Write-Host 'Please use at least 12 characters.' -ForegroundColor Yellow; continue }
    $second = Read-Plain 'Type it again'
    if ($first -cne $second) { Write-Host 'The two passwords did not match. Try again.' -ForegroundColor Yellow; continue }
    break
}

New-Item -ItemType Directory -Force -Path (Split-Path $KeyStore) | Out-Null
$env:ORBIT_SIGNING_PASS = $first
try {
    & $keytool -genkeypair -keystore $KeyStore -storetype PKCS12 -storepass:env ORBIT_SIGNING_PASS `
        -alias $Alias -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Orbit, O=Orbit'
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the key.' }
    $listing = & $keytool -list -v -keystore $KeyStore -storetype PKCS12 -storepass:env ORBIT_SIGNING_PASS -alias $Alias
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not read the new key back.' }
} finally {
    Remove-Item Env:ORBIT_SIGNING_PASS -ErrorAction SilentlyContinue
    $first = $null; $second = $null
}

$match = $listing | Select-String '^\s*SHA256:\s*([0-9A-F:]{95})\s*$' | Select-Object -First 1
$sha = if ($match) { $match.Matches[0].Groups[1].Value } else { '(not found - the key was still created; run keytool -list -v to see it)' }
$fingerprintFile = Join-Path (Split-Path $KeyStore) 'orbit-release-fingerprint.txt'
@(
    'Orbit release signing certificate (public information, safe to share)'
    "Keystore: $KeyStore"
    "Alias: $Alias"
    "SHA-256: $sha"
    "Created: $((Get-Date).ToString('yyyy-MM-dd HH:mm'))"
) | Set-Content -Encoding utf8 -LiteralPath $fingerprintFile

Write-Host ''
Write-Host 'Done. Your signing key was created.' -ForegroundColor Green
Write-Host "  Key file:    $KeyStore"
Write-Host "  Fingerprint: $sha  (public, also saved to $fingerprintFile)"
Write-Host ''
Write-Host 'NOW BACK IT UP (do this today):' -ForegroundColor Cyan
Write-Host '  1. Copy the key file to a USB stick or other drive you keep safe.'
Write-Host '  2. Save the password (and ideally the key file too) in your password manager.'
Write-Host 'You can close this window.'
