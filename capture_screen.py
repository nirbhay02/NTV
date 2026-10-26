import subprocess, time

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

subprocess.run([adb, 'connect', device])
time.sleep(1)
subprocess.run([adb, '-s', device, 'shell', 'screencap', '-p', '/sdcard/screen_zee.png'])
subprocess.run([adb, '-s', device, 'pull', '/sdcard/screen_zee.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_zee.png'])
print("Screenshot captured")
