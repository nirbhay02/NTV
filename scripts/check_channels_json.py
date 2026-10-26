import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

url = "http://192.168.0.249:5001/channels"
with urllib.request.urlopen(url, timeout=5) as resp:
    data = json.loads(resp.read().decode('utf-8'))
    print(f"Channels endpoint returned type {type(data)}")
    if isinstance(data, dict):
        print(f"Keys: {list(data.keys())}")
        result = data.get("result", data)
    else:
        result = data
    if isinstance(result, list) and len(result) > 0:
        print(f"Total channels in server: {len(result)}")
        print(f"First item: {result[0]}")
