import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

for endpoint in ["/epg.xml.gz", "/epg.xml", "/channels", "/status"]:
    url = f"http://192.168.0.249:5001{endpoint}"
    try:
        req = urllib.request.Request(url, method='HEAD')
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"HEAD {url} -> {resp.status} {resp.headers.get('Content-Length')} bytes, type={resp.headers.get('Content-Type')}")
    except Exception as e:
        print(f"HEAD {url} -> {e}")
