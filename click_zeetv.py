import subprocess, time

adb = r'C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe'
device = '192.168.0.199:37849'

def run(cmd):
    return subprocess.run([adb, '-s', device] + cmd, capture_output=True, text=True)

subprocess.run([adb, 'connect', device])
time.sleep(1)

# In the screenshot, the center list item for Zee TV HD is at x=1000, y=680
print("Tapping Zee TV HD at (1000, 680)")
run(['shell', 'input', 'tap', '1000', '680'])

time.sleep(2)
# Tap Watch Live at bottom right (x=2000, y=1560) or Enter key
print("Tapping Watch Live or pressing Enter")
run(['shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER'])
time.sleep(1)
run(['shell', 'input', 'tap', '2000', '1560'])

time.sleep(5)
run(['shell', 'screencap', '-p', '/sdcard/screen_zeetv_play.png'])
run(['pull', '/sdcard/screen_zeetv_play.png', r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\screen_zeetv_play.png'])
print("Captured screen_zeetv_play.png")
