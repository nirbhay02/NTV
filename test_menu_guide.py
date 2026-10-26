import subprocess, time

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

def run(cmd):
    res = subprocess.run([adb, '-s', device] + cmd, capture_output=True, text=True)
    print("RUN:", ' '.join(cmd), "->", res.stdout.strip())
    return res

print("--- Pressing DPAD_LEFT or KEYCODE_MENU to open Quick Guide ---")
run(['shell', 'input', 'keyevent', 'KEYCODE_MENU'])
time.sleep(2)
run(['shell', 'screencap', '-p', '/sdcard/screen_quick_guide.png'])
run(['pull', '/sdcard/screen_quick_guide.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_quick_guide.png'])
print("Captured screen_quick_guide.png")
