$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.0.199:37849"
& $adb connect $device
Start-Sleep -Seconds 1
Write-Host "Installing NTV v1.2 APK..."
& $adb -s $device install -r releases\v1.2\NTV_v1.2.apk
Start-Sleep -Seconds 2
Write-Host "Starting MainActivity..."
& $adb -s $device shell am start -n com.jiotv.androidtv/.MainActivity
Start-Sleep -Seconds 6
& $adb -s $device shell screencap -p /sdcard/v1_2_dashboard.png
& $adb -s $device pull /sdcard/v1_2_dashboard.png releases\v1.2\dashboard.png
Write-Host "Dashboard captured successfully"
