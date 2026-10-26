$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.0.199:37849"
& $adb connect $device
Start-Sleep -Seconds 1

Write-Host "Launching NTV..."
& $adb -s $device shell am start -n com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity
Start-Sleep -Seconds 4

Write-Host "Tapping Kids Category (150, 505)..."
& $adb -s $device shell input tap 150 505
Start-Sleep -Seconds 2

& $adb -s $device shell screencap -p /sdcard/v1_2_kids_section.png
& $adb -s $device pull /sdcard/v1_2_kids_section.png releases\v1.2\kids_section.png
Write-Host "Kids section captured"

Write-Host "Playing first channel in Kids (tapping Watch Live 1460, 875)..."
& $adb -s $device shell input tap 1460 875
Start-Sleep -Seconds 5

& $adb -s $device shell screencap -p /sdcard/v1_2_player.png
& $adb -s $device pull /sdcard/v1_2_player.png releases\v1.2\player_screen.png
Write-Host "Player captured"

Write-Host "Pressing Back to return to Dashboard..."
& $adb -s $device shell input keyevent 4
Start-Sleep -Seconds 2

& $adb -s $device shell screencap -p /sdcard/v1_2_back_result.png
& $adb -s $device pull /sdcard/v1_2_back_result.png releases\v1.2\back_to_kids_result.png
Write-Host "Back result captured"
