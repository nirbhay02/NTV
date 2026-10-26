$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.0.199:37849"
& $adb connect $device
Start-Sleep -Seconds 1

# Click Settings button (at top right: ~780, 40 on tablet 1080p, let's use d-pad or keycode or tap)
# Let's tap the Settings button
& $adb -s $device shell input tap 770 40
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_settings.png
& $adb -s $device pull /sdcard/v1_2_settings.png releases\v1.2\settings.png
Write-Host "Settings captured"

# Tap Search button (to the left of Settings: ~620, 40)
& $adb -s $device shell input tap 620 40
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_search.png
& $adb -s $device pull /sdcard/v1_2_search.png releases\v1.2\search.png
Write-Host "Search captured"
