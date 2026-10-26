import urllib.request
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

url = "http://192.168.0.249:5001/playlist.m3u"
req = urllib.request.urlopen(url)
text = req.read().decode('utf-8', errors='ignore')

for m in re.finditer(r'#EXTINF:[^\n]*tvg-id="([^"]+)"[^\n]*,([^\n]+)', text):
    cid, name = m.groups()
    if any(k in name.lower() for k in ["rajasthan", "dd india", "dd madhya"]):
        print(f"M3U Channel: id='{cid}', name='{name.strip()}'")
