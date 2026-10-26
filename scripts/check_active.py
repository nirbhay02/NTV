import gzip
import re
import datetime
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

now_utc = "2026102605" # Oct 26 05:45 UTC = 11:15 IST
print(f"Checking programs active around {now_utc} UTC...")

active_channels = set()
channels_with_programmes = set()

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    text = f.read()

prog_pattern = re.compile(r'<programme start="([^"]+)" stop="([^"]+)" channel="([^"]+)"[^>]*><title[^>]*>([^<]+)</title>')
for m in prog_pattern.finditer(text):
    start, stop, cid, title = m.groups()
    channels_with_programmes.add(cid)
    # Check if current time falls between start and stop
    # e.g. start <= '20261026054500' <= stop
    if start <= "20261026054500 +0000" <= stop:
        active_channels.add((cid, title, start, stop))

print(f"Total channels in EPG with ANY programs: {len(channels_with_programmes)}")
print(f"Total channels with CURRENTLY ACTIVE program (Oct 26 11:15 IST): {len(active_channels)}")
for cid, title, start, stop in list(active_channels)[:20]:
    print(f"Active [{cid}]: {title} ({start} - {stop})")
