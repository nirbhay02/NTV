import subprocess
import time
import sys

adb = r"C:\Users\Nirbhay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
device = "192.168.0.199:37849"

def run(args, timeout=30):
    cmd = [adb, "-s", device] + args
    print(f">> Running: {' '.join(cmd)}")
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    print(f"Stdout:\n{p.stdout.strip()}")
    if p.stderr.strip():
        print(f"Stderr:\n{p.stderr.strip()}")
    return p.stdout

# 1. Connect
subprocess.run([adb, "connect", device], capture_output=True, text=True)

# 2. Check device info
print("=== Device Info ===")
run(["shell", "getprop", "ro.product.model"])
run(["shell", "getprop", "ro.build.version.release"])
run(["shell", "getprop", "ro.product.cpu.abi"])

# 3. Clear logcat
run(["logcat", "-c"])

# 4. Install updated APK
apk_path = r"c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\build\outputs\apk\debug\app-debug.apk"
print(f"=== Installing APK ({apk_path}) ===")
run(["install", "-r", apk_path], timeout=120)

# 5. Launch app
print("=== Launching App ===")
run(["shell", "am", "start", "-n", "com.jiotv.androidtv.debug/com.jiotv.androidtv.MainActivity"])

# 6. Wait 3 seconds and check logcat for crash or server
time.sleep(3)
print("=== Checking Logcat ===")
log = run(["logcat", "-d", "-t", "100"])

# Check for exceptions
lines = log.splitlines()
crashes = [l for l in lines if "FATAL" in l or "AndroidRuntime" in l or "Exception" in l or "EmbeddedServer" in l or "jiotv" in l.lower()]
print("=== Relevant Logs ===")
print("\n".join(crashes))
