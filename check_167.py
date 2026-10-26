import urllib.request
url = 'http://192.168.0.199:5001/playlist.m3u'
try:
    content = urllib.request.urlopen(url, timeout=5).read().decode('utf-8', errors='ignore')
    blocks = content.split('#EXTINF:')
    for b in blocks:
        if 'tvg-id="167"' in b or 'tvg-id="165"' in b:
            print("Found block:", b.splitlines()[0])
except Exception as e:
    print("Error:", e)
