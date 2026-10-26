import gzip
import xml.etree.ElementTree as ET
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

print("Reading XML from gzip...")
with gzip.open("scripts/epg.xml.gz", "rb") as gz:
    # Let's read first 10MB
    data = gz.read(15000000)

print(f"Read {len(data)} uncompressed bytes")
# Parse XML elements from this chunk or search with regex
import re
channels = re.findall(r'<channel id="([^"]+)"><display-name>([^<]+)</display-name>', data.decode('utf-8', errors='ignore'))
print(f"Found {len(channels)} channel definitions. Sample:")
for cid, name in channels[:20]:
    print(f"CID: {cid} -> {name}")

# Now look for programmes with channel="165" or channel="472" or channel="zee"
progs = re.findall(r'<programme start="([^"]+)" stop="([^"]+)" channel="([^"]+)"><title>([^<]+)</title>', data.decode('utf-8', errors='ignore'))
print(f"Found {len(progs)} programmes in chunk. Sample:")
for start, stop, cid, title in progs[:15]:
    print(f"Prog: cid={cid}, start={start}, stop={stop}, title={title}")

# Check specifically for cid="165"
p165 = [p for p in progs if p[2] == "165"]
print(f"Total programmes for 165: {len(p165)}")
for start, stop, cid, title in p165[:5]:
    print(f"165: {start} -> {stop} : {title}")

# Check programmes for Zee Cinema or cinema
print("\nCheck if any programmes have 'Cinema' or 'Zee Cinema':")
for start, stop, cid, title in progs:
    if "cinema" in title.lower():
        print(f"CID {cid}: {title} ({start})")
        break
