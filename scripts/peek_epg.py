import gzip

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    for i in range(40):
        print(f.readline().strip())
