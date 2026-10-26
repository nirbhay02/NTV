import urllib.request
import gzip
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

url = "https://avkb.short.gy/jioepg.xml.gz"
print(f"Checking URL {url}...")
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        print(f"Final URL: {resp.geturl()}")
        data = resp.read()
        print(f"Downloaded {len(data)} bytes")
        with open("scripts/jioepg_avkb.xml.gz", "wb") as f:
            f.write(data)
except Exception as e:
    print(f"Error: {e}")
