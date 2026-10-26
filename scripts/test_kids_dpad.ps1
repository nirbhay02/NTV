$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.0.199:37849"
& $adb connect $device
Start-Sleep -Seconds 1

Write-Host "Ensuring NTV is in foreground..."
& $adb -s $device shell am start -n com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity
Start-Sleep -Seconds 3

Write-Host "Tapping directly on Kids Category (x=225, y=758)..."
& $adb -s $device shell input tap 225 758
Start-Sleep -Seconds 2

& $adb -s $device shell screencap -p /sdcard/v1_2_kids_view.png
& $adb -s $device pull /sdcard/v1_2_kids_view.png releases\v1.2\kids_view.png
Write-Host "Kids view captured"

Write-Host "Clicking first channel in Kids list (x=675, y=240)..."
& $adb -s $device shell input tap 675 240
Start-Sleep -Seconds 6

& $adb -s $device shell screencap -p /sdcard/v1_2_playing_kids.png
& $adb -s $device pull /sdcard/v1_2_playing_kids.png releases\v1.2\playing_kids.png
Write-Host "Playing Kids channel captured"

Write-Host "Pressing DPAD_DOWN (keycode 20) to switch to next channel..."
& $adb -s $device shell input keyevent 20
Start-Sleep -Seconds 4

& $adb -s $device shell screencap -p /sdcard/v1_2_zapped_kids.png
& $adb -s $device pull /sdcard/v1_2_zapped_kids.png releases\v1.2\zapped_kids.png
Write-Host "Zapped channel captured"

Write-Host "Pressing Back (keycode 4) to return to Dashboard..."
& $adb -s $device shell input keyevent 4
Start-Sleep -Seconds 2

& $adb -s $device shell screencap -p /sdcard/v1_2_back_to_kids_verified.png
& $adb -s $device pull /sdcard/v1_2_back_to_kids_verified.png releases\v1.2\back_to_kids_verified.png
Write-Host "Dashboard after back captured"
