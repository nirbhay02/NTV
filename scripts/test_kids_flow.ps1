$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$target = "192.168.0.199:37849"

Write-Host "Ensuring connected..."
& $adb connect $target

Write-Host "Force stopping app for a fresh start..."
& $adb -s $target shell am force-stop com.jiotv.androidtv.debug
Start-Sleep -Seconds 1

Write-Host "Launching NTV app..."
& $adb -s $target shell am start -n com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity
Start-Sleep -Seconds 4

Write-Host "Focusing category list..."
# Category list is on the left pane. Let's make sure we are focused on Categories:
# Send DPAD_LEFT twice
& $adb -s $target shell input keyevent 21
Start-Sleep -Milliseconds 300
& $adb -s $target shell input keyevent 21
Start-Sleep -Milliseconds 300

# Go all the way to top of categories
for ($i = 0; $i -lt 10; $i++) {
    & $adb -s $target shell input keyevent 19
    Start-Sleep -Milliseconds 150
}

# The category list order:
# [0] Favorites
# [1] Zee Network
# [2] Entertainment
# [3] Movies
# [4] Sports
# [5] Music
# [6] News
# [7] Kids
# Let's move down 7 steps to reach Kids:
Write-Host "Navigating down to Kids category..."
for ($i = 0; $i -lt 7; $i++) {
    & $adb -s $target shell input keyevent 20
    Start-Sleep -Milliseconds 300
}

# Select Kids category
& $adb -s $target shell input keyevent 23
Start-Sleep -Milliseconds 800

# Screenshot of Dashboard with Kids selected
Write-Host "Capturing Kids category selected on Dashboard..."
& $adb -s $target shell screencap -p /sdcard/kids_dashboard_selected.png
& $adb -s $target pull /sdcard/kids_dashboard_selected.png releases/v1.2/kids_dashboard_selected.png

# Move right into channels list
Write-Host "Navigating into Kids channels..."
& $adb -s $target shell input keyevent 22
Start-Sleep -Milliseconds 500

# Click to play the first Kids channel
Write-Host "Playing first Kids channel..."
& $adb -s $target shell input keyevent 23
Start-Sleep -Seconds 4

# Screenshot of Kids channel playing
Write-Host "Capturing Kids channel playing..."
& $adb -s $target shell screencap -p /sdcard/kids_channel_playing.png
& $adb -s $target pull /sdcard/kids_channel_playing.png releases/v1.2/kids_channel_playing.png

# Zap channel: DPAD_DOWN to switch to next Kids channel
Write-Host "Zapping to next channel in Kids..."
& $adb -s $target shell input keyevent 20
Start-Sleep -Seconds 3

# Capture zapped channel overlay
Write-Host "Capturing zapped Kids channel..."
& $adb -s $target shell screencap -p /sdcard/kids_channel_zapped.png
& $adb -s $target pull /sdcard/kids_channel_zapped.png releases/v1.2/kids_channel_zapped.png

# Press Back twice to return to Dashboard
Write-Host "Returning to Dashboard..."
& $adb -s $target shell input keyevent 4
Start-Sleep -Milliseconds 800
& $adb -s $target shell input keyevent 4
Start-Sleep -Seconds 2

# Capture Dashboard screenshot confirming Kids category remains selected!
Write-Host "Capturing Dashboard after returning (confirming Kids retention)..."
& $adb -s $target shell screencap -p /sdcard/kids_back_verified.png
& $adb -s $target pull /sdcard/kids_back_verified.png releases/v1.2/kids_back_verified.png

Write-Host "Done! Verification complete."
