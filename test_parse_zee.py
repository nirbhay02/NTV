import re, urllib.request

with open(r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\src\main\java\com\jiotv\androidtv\data\repository\ZeeRepository.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Extract metadata map
meta_matches = re.findall(r'"([a-z0-9]+)"\s*to\s*ZeeChannelMeta\((\d+),\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"', text)
channel_metadata = {}
for k, cid, cname, grp, lang in meta_matches:
    channel_metadata[k] = {'id': int(cid), 'name': cname, 'group': grp, 'lang': lang}

print(f"Loaded {len(channel_metadata)} channel metadata entries")

def parse_tag(line, tag):
    p = f'{tag}="'
    idx = line.find(p)
    if idx == -1: return ""
    start = idx + len(p)
    end = line.find('"', start)
    return line[start:end] if end != -1 else ""

def parse_m3u(content):
    channels = []
    lines = content.splitlines()
    cur_id = ""
    cur_name = ""
    cur_logo = ""
    cur_group = ""
    cur_lang = ""
    cur_key = ""
    cur_mpd = False
    index = 1
    for line in lines:
        l = line.strip()
        if l.startswith("#EXTINF:"):
            cur_id = parse_tag(l, "tvg-id")
            cur_name = parse_tag(l, "tvg-name")
            if not cur_name:
                cur_name = l.split(",")[-1].strip()
            cur_logo = parse_tag(l, "tvg-logo")
            cur_group = parse_tag(l, "group-title")
            cur_lang = parse_tag(l, "tvg-language")
        elif l.startswith("#KODIPROP:inputstream.adaptive.license_key="):
            cur_key = l.split("=", 1)[1]
        elif l.startswith("http://") or l.startswith("https://"):
            key_by_name = re.sub(r'[^a-z0-9]', '', cur_name.lower())
            key_by_id = re.sub(r'[^a-z0-9]', '', cur_id.lower())
            meta = channel_metadata.get(key_by_name) or channel_metadata.get(key_by_id)
            cid_num = meta['id'] if meta else (int(cur_id) if cur_id.isdigit() else 9000 + index)
            final_name = meta['name'] if meta else cur_name
            channels.append({
                'id': str(cid_num),
                'name': final_name,
                'number': cid_num,
                'key': cur_key
            })
            cur_id = cur_name = cur_logo = cur_group = cur_lang = cur_key = ""
            index += 1
    return channels

# Test parsing rathee-ashish zee.m3u
zee_remote = urllib.request.urlopen("https://raw.githubusercontent.com/rathee-ashish/zee-playlist/main/zee.m3u", timeout=5).read().decode('utf-8')
parsed_zee = parse_m3u(zee_remote)
print(f"Parsed remote Zee channels: {len(parsed_zee)}")
for c in parsed_zee:
    if "Zee TV" in c['name'] or "Cinema" in c['name']:
        print("  ", c)
