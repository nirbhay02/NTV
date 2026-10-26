import test_full_channels, test_merge, re

server_channels = test_merge.server_channels
third_party = list(test_full_channels.distinct_by_name.values())

def clean(name):
    return re.sub(r'[^a-z0-9]', '', name.lower())

result = []
added_names = set()
added_ids = set()

for tp in third_party:
    c = clean(tp['name'])
    if c and c in added_names:
        continue
    result.append(tp)
    added_ids.add(tp['id'])
    if c: added_names.add(c)

for sc in server_channels:
    c = clean(sc['name'])
    if 'zee' in sc['name'].lower() or 'pictures' in sc['name'].lower():
        if c in added_names or sc['id'] in added_ids:
            continue
    if sc['id'] in added_ids or (c and c in added_names):
        continue
    result.append(sc)
    added_ids.add(sc['id'])
    if c: added_names.add(c)

print(f"Total merged channels: {len(result)}")
zee_final = [c for c in result if 'zee' in c['name'].lower() or 'pictures' in c['name'].lower()]
print(f"Total Zee channels in merged: {len(zee_final)}")
for z in sorted(zee_final, key=lambda x: x['number']):
    print(f"  {z['number']:4d}: {z['name']}")
