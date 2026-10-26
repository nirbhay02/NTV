import gzip
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    chunk = ""
    while True:
        c = f.read(500000)
        if not c:
            break
        idx = c.find("<programme")
        if idx != -1:
            print("Found <programme! Exact snippet:")
            print(c[idx:idx+500])
            break
