# PowerShell script to prepare an OTA update release
param(
    [string]$VersionName = "1.3.0",
    [int]$VersionCode = 4,
    [string]$Changelog = "Bug fixes and performance improvements."
)

$ErrorActionPreference = "Stop"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   NTV Android TV - Prepare OTA Update       " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$releaseDir = "releases\v$VersionName"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
}

$universalApk = Get-ChildItem "app\build\outputs\apk\release\*universal*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
$arm64Apk = Get-ChildItem "app\build\outputs\apk\release\*arm64*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
$armv7Apk = Get-ChildItem "app\build\outputs\apk\release\*armeabi-v7a*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

$universalSize = if ($universalApk) { $universalApk.Length } else { 0 }

$jsonContent = [PSCustomObject]@{
    versionCode = $VersionCode
    versionName = $VersionName
    title       = "NTV Android TV v$VersionName"
    changelog   = $Changelog
    size        = $universalSize
    assets      = [PSCustomObject]@{
        universal = "https://github.com/nirbhay02/NTV/releases/download/v$VersionName/NTV_v$VersionName.apk"
        arm64     = "https://github.com/nirbhay02/NTV/releases/download/v$VersionName/NTV_v${VersionName}_arm64.apk"
        firetv    = "https://github.com/nirbhay02/NTV/releases/download/v$VersionName/NTV_v${VersionName}_firetv_armv7.apk"
    }
}

$jsonPath = "releases\update.json"
$jsonContent | ConvertTo-Json -Depth 5 | Set-Content $jsonPath -Encoding utf8

Write-Host "[OK] Created update manifest at $jsonPath" -ForegroundColor Green
Write-Host ""
Write-Host "Next Steps for Auto-Updating users:" -ForegroundColor Yellow
Write-Host "1. Create a Release with tag 'v$VersionName' on GitHub (or your server)."
Write-Host "2. Upload the APKs to the GitHub Release:"
Write-Host "   - NTV_v$VersionName.apk (Universal)"
Write-Host "   - NTV_v${VersionName}_firetv_armv7.apk (Fire TV)"
Write-Host "   - NTV_v${VersionName}_arm64.apk (Modern TVs/Tablets)"
Write-Host "3. Connected Android TVs will automatically detect the new version and prompt users to update!" -ForegroundColor Green
