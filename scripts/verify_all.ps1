$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.0.199:37849"
& $adb connect $device
Start-Sleep -Seconds 1

Write-Host "1. Testing Settings Screen (tapping 1460, 50)..."
& $adb -s $device shell input tap 1460 50
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_settings.png
& $adb -s $device pull /sdcard/v1_2_settings.png releases\v1.2\settings_screen.png

Write-Host "Returning to Dashboard..."
& $adb -s $device shell input keyevent 4
Start-Sleep -Seconds 1

Write-Host "2. Testing Search Screen (tapping 1240, 50)..."
& $adb -s $device shell input tap 1240 50
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_search.png
& $adb -s $device pull /sdcard/v1_2_search.png releases\v1.2\search_screen.png

Write-Host "Typing 'pogo' into search bar..."
& $adb -s $device shell input tap 500 130
Start-Sleep -Seconds 1
& $adb -s $device shell input text "pogo"
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_search_pogo.png
& $adb -s $device pull /sdcard/v1_2_search_pogo.png releases\v1.2\search_pogo.png

Write-Host "Returning to Dashboard..."
& $adb -s $device shell input keyevent 4
Start-Sleep -Seconds 1
& $adb -s $device shell input keyevent 4
Start-Sleep -Seconds 1

Write-Host "3. Testing Kids Category (tapping 150, 505)..."
& $adb -s $device shell input tap 150 505
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_kids.png
& $adb -s $device pull /sdcard/v1_2_kids.png releases\v1.2\kids_selected.png

Write-Host "4. Playing first channel in Kids (tapping Watch Live 1460, 875)..."
& $adb -s $device shell input tap 1460 875
Start-Sleep -Seconds 5
& $adb -s $device shell screencap -p /sdcard/v1_2_kids_playing.png
& $adb -s $device pull /sdcard/v1_2_kids_playing.png releases\v1.2\kids_playing.png

Write-Host "5. Switching to next channel in Kids (DPAD_DOWN)..."
& $adb -s $device shell input keyevent 20
Start-Sleep -Seconds 4
& $adb -s $device shell screencap -p /sdcard/v1_2_kids_zapped.png
& $adb -s $device pull /sdcard/v1_2_kids_zapped.png releases\v1.2\kids_zapped.png

Write-Host "6. Pressing Back to verify it returns to Kids category..."
& $adb -s $device shell input keyevent 4
Start-Sleep -Seconds 2
& $adb -s $device shell screencap -p /sdcard/v1_2_back_to_kids.png
& $adb -s $device pull /sdcard/v1_2_back_to_kids.png releases\v1.2\after_back_to_kids.png

Write-Host "All verification tests completed!"
