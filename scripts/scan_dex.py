import zipfile
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

apk_path = "releases/v1.0/JioTV_AndroidTV_v1.0.apk"
with zipfile.ZipFile(apk_path, 'r') as z:
    for name in z.namelist():
        if name.endswith(".dex"):
            print(f"Scanning {name}...")
            data = z.read(name)
            # Find URLs or epg strings
            urls = re.findall(rb'https?://[a-zA-Z0-9_\-\./:%?=&#+]+', data)
            for u in set(urls):
                try:
                    us = u.decode('utf-8')
                    if any(k in us.lower() for k in ["epg", "xml", "fenyx", "jiotv", "short", "github"]):
                        print(f"URL: {us}")
                except:
                    pass
            # Also search for "epg" in strings
            matches = re.findall(rb'([a-zA-Z0-9_\-\./:]*epg[a-zA-Z0-9_\-\./:]*)', data, re.IGNORECASE)
            for m in set(matches):
                try:
                    ms = m.decode('utf-8')
                    if len(ms) > 3 and not ms.startswith("Lcom/"):
                        print(f"EPG String: {ms}")
                except:
                    pass
