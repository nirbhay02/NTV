import subprocess, time

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

apk_path = r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\build\outputs\apk\debug\app-debug.apk'
ret = run_adb(['-s', device, 'install', '-r', apk_path])
print("Install returncode:", ret)

run_adb(['-s', device, 'shell', 'am', 'start', '-n', 'com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity'])
