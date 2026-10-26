import urllib.request
import gzip
import os
import re

gz_path = "scripts/epg.xml.gz"
if not os.path.exists(gz_path):
    print("Downloading EPG...")
    urllib.request.urlretrieve("https://mitthu786.github.io/tvepg/jiotv/epg.xml.gz", gz_path)
    print("Downloaded EPG gz")

print(f"File size: {os.path.getsize(gz_path)} bytes")

with gzip.open(gz_path, "rt", encoding="utf-8", errors="ignore") as f:
    channels = {}
    sample_programmes = []
    
    for i, line in enumerate(f):
        if "<channel id=" in line:
            cid = re.search(r'id="([^"]+)"', line)
            if cid:
                c_id = cid.group(1)
                # Next few lines might have display name
                for _ in range(5):
                    nline = next(f, "")
                    dname = re.search(r'<display-name[^>]*>([^<]+)</display-name>', nline)
                    if dname:
                        channels[c_id] = dname.group(1)
                        break
        elif "<programme" in line:
            cid = re.search(r'channel="([^"]+)"', line)
            start = re.search(r'start="([^"]+)"', line)
            stop = re.search(r'stop="([^"]+)"', line)
            for _ in range(5):
                nline = next(f, "")
                tname = re.search(r'<title[^>]*>([^<]+)</title>', nline)
                if tname and cid and start:
                    sample_programmes.append((cid.group(1), start.group(1), stop.group(1) if stop else "", tname.group(1)))
                    break
        if len(sample_programmes) > 50 and len(channels) > 200:
            break

print(f"Total channels found in sample: {len(channels)}")
for cid, name in list(channels.items())[:30]:
    if any(k in name.lower() for k in ["zee", "cinema", "star", "sony", "sab"]):
        print(f"Channel: ID='{cid}' -> Name='{name}'")

print("\nSample programmes:")
for cid, start, stop, title in sample_programmes[:10]:
    cname = channels.get(cid, "Unknown")
    print(f"Prog: Channel [{cid}] ({cname}) {start} -> {stop}: {title}")
