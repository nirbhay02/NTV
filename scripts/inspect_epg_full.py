import gzip
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

channels = {}
programmes_by_cid = {}

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    text = f.read()

# Channels
for m in re.finditer(r'<channel id="([^"]+)"><display-name>([^<]+)</display-name>', text):
    cid, name = m.groups()
    channels[cid] = name

# Programmes
prog_pattern = re.compile(r'<programme start="([^"]+)" stop="([^"]+)" channel="([^"]+)"[^>]*>(.*?)</programme>')
for m in prog_pattern.finditer(text):
    start, stop, cid, body = m.groups()
    tm = re.search(r'<title[^>]*>([^<]+)</title>', body)
    title = tm.group(1) if tm else ""
    if cid not in programmes_by_cid:
        programmes_by_cid[cid] = []
    programmes_by_cid[cid].append((start, stop, title))

print(f"Total channels in EPG: {len(channels)}")
print(f"Total channels with programmes: {len(programmes_by_cid)}")

# Check Zee channels
print("\n--- Zee Channels in EPG ---")
for cid, name in channels.items():
    if "zee" in name.lower():
        prog_count = len(programmes_by_cid.get(cid, []))
        print(f"CID: {cid} | Name: {name} | Programs: {prog_count}")

# Check IDs 165, 167, 185, 472, 484, 487, 488, 504
test_ids = ["165", "167", "185", "472", "484", "487", "488", "504"]
print("\n--- Test IDs in EPG ---")
for tid in test_ids:
    name = channels.get(tid, "UNKNOWN")
    progs = programmes_by_cid.get(tid, [])
    print(f"ID: {tid} -> {name} ({len(progs)} progs)")
    if progs:
        print(f"   First: {progs[0]}")
        print(f"   Last: {progs[-1]}")
