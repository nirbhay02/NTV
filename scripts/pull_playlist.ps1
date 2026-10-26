$adb = "C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$target = "192.168.0.199:37849"

& $adb connect $target
Start-Sleep -Milliseconds 500

& $adb -s $target shell "curl -s http://127.0.0.1:5001/playlist.m3u > /sdcard/jiotv_playlist.m3u"
& $adb -s $target pull /sdcard/jiotv_playlist.m3u scripts/jiotv_playlist.m3u

Write-Host "Pulled playlist. Checking size..."
Get-Item scripts/jiotv_playlist.m3u
