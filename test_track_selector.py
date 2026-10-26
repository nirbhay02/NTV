import subprocess, time

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

def run(cmd):
    res = subprocess.run([adb, '-s', device] + cmd, capture_output=True, text=True)
    print("RUN:", ' '.join(cmd), "->", res.stdout.strip())
    return res

subprocess.run([adb, 'connect', device])
time.sleep(1)

print("--- Selecting channel Zee TV HD ---")
run(['shell', 'input', 'tap', '1000', '680'])
time.sleep(2)

print("--- Starting playback ---")
run(['shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER'])
time.sleep(1)
run(['shell', 'input', 'tap', '2000', '1560'])
time.sleep(5)

print("--- Pressing DPAD_RIGHT to open Stream Settings (Quality & Audio) ---")
run(['shell', 'input', 'keyevent', 'KEYCODE_DPAD_RIGHT'])
time.sleep(2)

# Screenshot of Stream Settings Drawer
run(['shell', 'screencap', '-p', '/sdcard/screen_track_selector.png'])
run(['pull', '/sdcard/screen_track_selector.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_track_selector.png'])
print("Captured screen_track_selector.png")
