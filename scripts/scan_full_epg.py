import gzip
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

p165 = []
p472 = []
p484 = []
all_found = {}

print("Scanning full gzip stream for programmes...")
with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    for line in f:
        # Check if line contains programme tags
        matches = re.finditer(r'<programme start="([^"]+)" stop="([^"]+)" channel="([^"]+)"><title[^>]*>([^<]+)</title>', line)
        for m in matches:
            start, stop, cid, title = m.groups()
            all_found[cid] = all_found.get(cid, 0) + 1
            if cid == "165":
                p165.append((start, stop, title))
            elif cid == "472":
                p472.append((start, stop, title))
            elif cid == "484":
                p484.append((start, stop, title))

print(f"Total distinct programme channel IDs: {len(all_found)}")
print(f"CID 165 count: {len(p165)}")
for s, e, t in p165[:5]:
    print(f"  165: {s} -> {e} : {t}")

print(f"CID 472 count: {len(p472)}")
for s, e, t in p472[:5]:
    print(f"  472: {s} -> {e} : {t}")

print(f"CID 484 count: {len(p484)}")
for s, e, t in p484[:5]:
    print(f"  484: {s} -> {e} : {t}")
