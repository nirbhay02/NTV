import urllib.request, re

with open(r'c:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv\app\src\main\java\com\jiotv\androidtv\data\repository\ZeeRepository.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Extract metadata map
meta_matches = re.findall(r'"([a-z0-9]+)"\s*to\s*ZeeChannelMeta\((\d+),\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"', text)
channel_metadata = {}
for k, cid, cname, grp, lang in meta_matches:
    channel_metadata[k] = {'id': int(cid), 'name': cname, 'group': grp, 'lang': lang}

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
    index = 1
    for line in lines:
        l = line.strip()
        if l.startswith("#EXTINF:"):
            cur_id = parse_tag(l, "tvg-id")
            cur_name = parse_tag(l, "tvg-name")
            if not cur_name:
                cur_name = l.split(",")[-1].strip()
        elif l.startswith("http://") or l.startswith("https://"):
            key_by_name = re.sub(r'[^a-z0-9]', '', cur_name.lower())
            key_by_id = re.sub(r'[^a-z0-9]', '', cur_id.lower())
            meta = channel_metadata.get(key_by_name) or channel_metadata.get(key_by_id)
            cid_num = meta['id'] if meta else (int(cur_id) if cur_id.isdigit() else 9000 + index)
            final_name = meta['name'] if meta else cur_name
            channels.append({
                'id': str(cid_num),
                'name': final_name,
                'number': cid_num
            })
            cur_id = cur_name = ""
            index += 1
    return channels

# Simulate ZeeRepository.loadChannels
zee_remote = urllib.request.urlopen("https://raw.githubusercontent.com/rathee-ashish/zee-playlist/main/zee.m3u", timeout=5).read().decode('utf-8')

m_sony = re.search(r'private const val EMBEDDED_SONY_M3U = """(.*?)"""', text, re.DOTALL)
sony_content = m_sony.group(1) if m_sony else ""

combined = []
combined.extend(parse_m3u(zee_remote))
combined.extend(parse_m3u(sony_content))

m_sports = re.search(r'private const val EMBEDDED_SPORTS_M3U = """(.*?)"""', text, re.DOTALL)
if m_sports:
    combined.extend(parse_m3u(m_sports.group(1)))

print(f"Total raw channels in combined: {len(combined)}")

# Distinct by clean name
distinct_by_name = {}
for c in combined:
    name_clean = re.sub(r'[^a-z0-9]', '', c['name'].lower())
    if name_clean not in distinct_by_name:
        distinct_by_name[name_clean] = c

print(f"Distinct channels by name: {len(distinct_by_name)}")
zee_network = [c for c in distinct_by_name.values() if 'zee' in c['name'].lower() or 'pictures' in c['name'].lower()]
print(f"Zee Network channels count: {len(zee_network)}")
for z in sorted(zee_network, key=lambda x: x['number']):
    print(f"  {z['number']:4d}: {z['name']}")
