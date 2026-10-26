import urllib.request

url = "http://192.168.0.249:5001/playlist.m3u"
with urllib.request.urlopen(url) as resp:
    lines = [resp.readline().decode("utf-8", errors="ignore").strip() for _ in range(25)]
    for line in lines:
        print(line)
