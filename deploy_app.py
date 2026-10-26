import subprocess, time, os

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

def run_adb(args):
    cmd = [adb] + args
    res = subprocess.run(cmd, capture_output=True, text=True)
    print("CMD:", ' '.join(cmd))
    print("STDOUT:", res.stdout.strip())
    if res.stderr:
        print("STDERR:", res.stderr.strip())
    return res.returncode

run_adb(['devices'])
run_adb(['connect', device])
time.sleep(1)
run_adb(['devices'])

apk_path = r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\build\outputs\apk\debug\app-universal-debug.apk'
if not os.path.exists(apk_path):
    apk_path = r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk'

print(f"Installing APK: {apk_path} (exists={os.path.exists(apk_path)})")
ret = run_adb(['-s', device, 'install', '-r', '-d', apk_path])
print("Install returncode:", ret)

run_adb(['-s', device, 'shell', 'am', 'start', '-n', 'com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity'])
time.sleep(3)
run_adb(['-s', device, 'shell', 'screencap', '-p', '/sdcard/screen_tab.png'])
run_adb(['-s', device, 'pull', '/sdcard/screen_tab.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_tab.png'])
print("Deployment completed!")
