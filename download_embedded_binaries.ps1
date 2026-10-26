# Downloads and bundles jiotv_go native Android binaries directly into the app assets
param (
    [string]$Arch = "all"
)

$baseDir = "$PSScriptRoot\app\src\main\assets\bin"

$targets = @{
    "arm64" = "https://github.com/jiotv-go/jiotv_go/releases/latest/download/jiotv_go-android-arm64"
    "arm"   = "https://github.com/jiotv-go/jiotv_go/releases/latest/download/jiotv_go-android-arm"
    "amd64" = "https://github.com/jiotv-go/jiotv_go/releases/latest/download/jiotv_go-android-amd64"
    "386"   = "https://github.com/jiotv-go/jiotv_go/releases/latest/download/jiotv_go-android-386"
}

foreach ($archKey in $targets.Keys) {
    if ($Arch -ne "all" -and $Arch -ne $archKey) {
        continue
    }

    $outDir = "$baseDir\$archKey"
    if (!(Test-Path $outDir)) {
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    }

    $outFile = "$outDir\jiotv_go"
    $url = $targets[$archKey]

    Write-Host "Downloading $archKey from $url..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $outFile -TimeoutSec 60 -UseBasicParsing
        Write-Host "Successfully bundled $archKey to $outFile"
    } catch {
        Write-Warning "Could not download ${archKey}: $($_.Exception.Message)"
    }
}
