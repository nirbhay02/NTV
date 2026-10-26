$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$target = "192.168.0.199:37849"

& $adb connect $target
Start-Sleep -Milliseconds 500

# 1. Bring app to foreground
Write-Host "1. Launching NTV app..."
& $adb -s $target shell am start -n com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity
Start-Sleep -Seconds 3

# 2. Select Kids Category at (200, 950)
Write-Host "2. Tapping Kids Category at (200, 950)..."
& $adb -s $target shell input tap 200 950
Start-Sleep -Seconds 1

& $adb -s $target shell screencap -p /sdcard/final_kids_category.png
& $adb -s $target pull /sdcard/final_kids_category.png releases/v1.2/final_kids_category.png

# 3. Tap 'Watch Live' button at (2000, 1570) to play the Kids channel
Write-Host "3. Tapping 'Watch Live' button at (2000, 1570)..."
& $adb -s $target shell input tap 2000 1570
Start-Sleep -Seconds 4

# 4. Capture screenshot of Kids channel playing
Write-Host "4. Capturing Kids channel playing..."
& $adb -s $target shell screencap -p /sdcard/final_kids_playing.png
& $adb -s $target pull /sdcard/final_kids_playing.png releases/v1.2/final_kids_playing.png

# 5. Zap channel within Kids: send DPAD_DOWN
Write-Host "5. Zapping channel with DPAD_DOWN..."
& $adb -s $target shell input keyevent 20
Start-Sleep -Seconds 3

# 6. Capture zapped channel
Write-Host "6. Capturing zapped Kids channel..."
& $adb -s $target shell screencap -p /sdcard/final_kids_zapped.png
& $adb -s $target pull /sdcard/final_kids_zapped.png releases/v1.2/final_kids_zapped.png

# 7. Press Back to exit Player and return to Dashboard
Write-Host "7. Pressing Back to return to Dashboard..."
& $adb -s $target shell input keyevent 4
Start-Sleep -Milliseconds 600
# If overlay was visible, one more back brings us to dashboard:
& $adb -s $target shell input keyevent 4
Start-Sleep -Seconds 2

# 8. Capture Dashboard verifying Kids category remains selected!
Write-Host "8. Capturing Dashboard after Back to verify Kids category retention..."
& $adb -s $target shell screencap -p /sdcard/final_kids_back_verified.png
& $adb -s $target pull /sdcard/final_kids_back_verified.png releases/v1.2/final_kids_back_verified.png

Write-Host "All done! Verifications saved."
