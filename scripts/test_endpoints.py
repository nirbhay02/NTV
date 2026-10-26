import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

endpoints = [
    "/epg",
    "/epg/143",
    "/epg/165",
    "/api/epg",
    "/api/epg/143",
    "/playlist.m3u",
    "/playlist.m3u8",
    "/play",
    "/swagger",
    "/docs",
    "/epg.xml",
    "/epg.xml.gz"
]

for ep in endpoints:
    url = f"http://192.168.0.249:5001{ep}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"{ep} -> {resp.status} ({resp.headers.get('Content-Type')}) length={resp.headers.get('Content-Length')}")
    except urllib.error.HTTPError as e:
        print(f"{ep} -> HTTP {e.code}")
    except Exception as e:
        print(f"{ep} -> {e}")
