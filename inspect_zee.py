import re

# Let's inspect embedded channels in ZeeRepository.kt
with open(r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\src\main\java\com\jiotv\androidtv\data\repository\ZeeRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# find all #EXTINF in EMBEDDED_M3U and EMBEDDED_SPORTS_M3U and EMBEDDED_SONY_M3U
extinfs = re.findall(r'#EXTINF:[^\n]+', content)
print(f"Total EXTINFs in ZeeRepository: {len(extinfs)}")
for e in extinfs:
    if "Zee" in e or "Pictures" in e or "&" in e:
        print("  ", e)
