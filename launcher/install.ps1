param([string]$TvHost)

$ErrorActionPreference = "Stop"
if (-not $env:JAVA_HOME) { Write-Error "Set JAVA_HOME to a JDK 17 install." }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { Write-Error "Set ANDROID_HOME to the Android SDK." }
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Set-Content -Encoding ascii -Path (Join-Path $PSScriptRoot "local.properties") -Value "sdk.dir=$($sdk -replace '\\','/')"

$gradle = Get-Command gradle -ErrorAction SilentlyContinue
if (-not $gradle) { Write-Error "Put Gradle 8.9 on PATH." }
Push-Location $PSScriptRoot
& gradle test assembleDebug
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { exit $code }

if ($TvHost) {
    Write-Host "adb connect $TvHost"
    Write-Host "adb -s ${TvHost}:5555 install -r app\build\outputs\apk\debug\app-debug.apk"
}
