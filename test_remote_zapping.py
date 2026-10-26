import subprocess, time

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

def run(cmd):
    res = subprocess.run([adb, '-s', device] + cmd, capture_output=True, text=True)
    print("RUN:", ' '.join(cmd), "->", res.stdout.strip())
    return res

subprocess.run([adb, 'connect', device])
time.sleep(1)

print("--- Launching app ---")
run(['shell', 'am', 'start', '-n', 'com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity'])
time.sleep(4)

print("--- Selecting channel card Zee TV HD ---")
run(['shell', 'input', 'tap', '1000', '680'])
time.sleep(2)

print("--- Starting playback (DPAD_CENTER or Tap Watch Live) ---")
run(['shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER'])
time.sleep(1)
run(['shell', 'input', 'tap', '2000', '1560'])
time.sleep(5)

# Screenshot channel 1
run(['shell', 'screencap', '-p', '/sdcard/screen_zap_initial.png'])
run(['pull', '/sdcard/screen_zap_initial.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_zap_initial.png'])

print("--- Pressing DPAD_DOWN to switch to NEXT channel ---")
run(['shell', 'input', 'keyevent', 'KEYCODE_DPAD_DOWN'])
time.sleep(4)

# Screenshot after DOWN
run(['shell', 'screencap', '-p', '/sdcard/screen_zap_down.png'])
run(['pull', '/sdcard/screen_zap_down.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_zap_down.png'])

print("--- Pressing DPAD_UP to switch back to PREVIOUS channel ---")
run(['shell', 'input', 'keyevent', 'KEYCODE_DPAD_UP'])
time.sleep(4)

# Screenshot after UP
run(['shell', 'screencap', '-p', '/sdcard/screen_zap_up.png'])
run(['pull', '/sdcard/screen_zap_up.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_zap_up.png'])

print("--- Testing Complete ---")
