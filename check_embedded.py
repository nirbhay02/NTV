import re

with open(r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\src\main\java\com\jiotv\androidtv\data\repository\ZeeRepository.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Extract EMBEDDED_M3U
m = re.search(r'private const val EMBEDDED_M3U = """(.*?)"""', text, re.DOTALL)
embedded_m3u = m.group(1) if m else ""

# Extract EMBEDDED_SPORTS_M3U
m_sports = re.search(r'private const val EMBEDDED_SPORTS_M3U = """(.*?)"""', text, re.DOTALL)
sports_m3u = m_sports.group(1) if m_sports else ""

# Extract EMBEDDED_SONY_M3U
m_sony = re.search(r'private const val EMBEDDED_SONY_M3U = """(.*?)"""', text, re.DOTALL)
sony_m3u = m_sony.group(1) if m_sony else ""

print("EMBEDDED_M3U lines:", len(embedded_m3u.splitlines()))
print("SPORTS_M3U lines:", len(sports_m3u.splitlines()))
print("SONY_M3U lines:", len(sony_m3u.splitlines()))
