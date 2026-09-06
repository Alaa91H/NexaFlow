# NexaFlow — One-shot ROM integration installer for Evolution X (Windows)
# Builds a ROM-compatible APK and optionally pushes it / installs as Magisk module via ADB.
# Usage:
#   .\rom\install-rom-integration.ps1                    # build only
#   .\rom\install-rom-integration.ps1 -PushAdb           # build + adb install
#   .\rom\install-rom-integration.ps1 -Magisk            # build + Magisk module via adb (needs root)
param(
    [switch]$PushAdb,
    [switch]$Magisk
)
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$Jdk = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk\bin;$env:Path"

Write-Host "== NexaFlow Evolution X (cnb / API 37) — ROM build ==" -ForegroundColor Cyan

# 1) Build
Write-Host "`n[1/3] Building release APK..." -ForegroundColor Yellow
Push-Location $RepoRoot
& .\gradlew.bat assembleRelease --warning-mode all
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$apk = "app\build\outputs\apk\release\app-release.apk"
if (!(Test-Path $apk)) { throw "APK not found: $apk" }
Write-Host "APK: $apk ($( [math]::Round((Get-Item $apk).Length/1MB,2) ) MB)" -ForegroundColor Green

# 2) Copy to rom/prebuilt
Write-Host "`n[2/3] Copying to rom/prebuilt/NexaFlow.apk..." -ForegroundColor Yellow
Copy-Item $apk "rom\prebuilt\NexaFlow.apk" -Force
Write-Host "Done: rom\prebuilt\NexaFlow.apk" -ForegroundColor Green

# 16KB check
Write-Host "`n[Check] 16 KB alignment..." -ForegroundColor Yellow
python scripts/check_16kb.py $apk
if ($LASTEXITCODE -ne 0) { Write-Warning "16KB check FAILED — see output" } else { Write-Host "16KB OK" -ForegroundColor Green }

if ($PushAdb -or $Magisk) {
    Write-Host "`n[3/3] ADB..." -ForegroundColor Yellow
    $dev = (adb devices | Select-String "device$" | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1)
    if (-not $dev) { throw "No adb device. Connect via adb connect <ip>:<port> first." }
    Write-Host "Device: $dev" -ForegroundColor Green
    if ($Magisk) {
        Write-Host "Magisk install path: build zip via in-app SystemAppInstaller or push prebuilt module" -ForegroundColor Yellow
        # Push APK for manual Magisk install (the in-app builder creates the zip with whitelist)
        adb -s $dev push rom/prebuilt/NexaFlow.apk /sdcard/NexaFlow-rom.apk
        Write-Host "Pushed to /sdcard/NexaFlow-rom.apk — open NexaFlow > ROM Integration > Install as System App, or run: magisk --install-module <zip>" -ForegroundColor Cyan
    }
    if ($PushAdb) {
        adb -s $dev install -r $apk
        Write-Host "Installed via adb install -r" -ForegroundColor Green
    }
}

Pop-Location
Write-Host "`n== Done. To bake into Evolution X tree, copy rom/ to vendor/nexaflow/prebuilt/ and inherit NexaFlow.mk ==" -ForegroundColor Cyan
