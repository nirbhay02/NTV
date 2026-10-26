import gzip
import re

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    text = f.read()

m = re.findall(r'<programme start="([^"]+)" stop="([^"]+)" channel="538"[^>]*><title[^>]*>([^<]+)</title>', text)
if m:
    print("First:", m[0])
    print("Last:", m[-1])
