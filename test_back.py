import subprocess, time

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

def run(cmd):
    return subprocess.run([adb, '-s', device] + cmd, capture_output=True, text=True)

subprocess.run([adb, 'connect', device])
time.sleep(1)

# Press Back key to return to dashboard
run(['shell', 'input', 'keyevent', 'KEYCODE_BACK'])
time.sleep(2)

run(['shell', 'screencap', '-p', '/sdcard/screen_back_dash.png'])
run(['pull', '/sdcard/screen_back_dash.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_back_dash.png'])
print("Captured dashboard after back")
