import urllib.request, re

# Fetch server channels
server_url = 'http://192.168.0.199:5001/playlist.m3u'
server_data = urllib.request.urlopen(server_url, timeout=5).read().decode('utf-8', errors='ignore')

def parse_server_channels(content):
    channels = []
    lines = content.splitlines()
    cur_id = ""
    cur_name = ""
    idx = 1
    for l in lines:
        l = l.strip()
        if l.startswith("#EXTINF:"):
            m_id = re.search(r'tvg-id="([^"]*)"', l)
            cur_id = m_id.group(1) if m_id else str(idx)
            comma = l.rfind(',')
            cur_name = l[comma+1:].strip() if comma != -1 else f"Channel {idx}"
        elif l.startswith("http://") or l.startswith("https://"):
            channels.append({'id': cur_id, 'name': cur_name, 'number': idx, 'url': l})
            idx += 1
            cur_id = ""
            cur_name = ""
    return channels

server_channels = parse_server_channels(server_data)
print(f"Server channels parsed: {len(server_channels)}")

# Check how many have 'Zee' in server channels
server_zee = [c for c in server_channels if 'zee' in c['name'].lower()]
print(f"Zee channels in serverChannels: {len(server_zee)}")
for z in server_zee:
    print("  ", z['id'], z['name'])
