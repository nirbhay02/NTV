import gzip
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    text = f.read()

for m in re.finditer(r'<channel id="([^"]+)"><display-name>([^<]+)</display-name>', text):
    cid, name = m.groups()
    if any(k in name.lower() for k in ["dd", "rajasthan", "india"]):
        print(f"ID: {cid} -> {name}")
