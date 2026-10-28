import urllib.request
import json
import subprocess
import os
import sys

VERSION = sys.argv[1] if len(sys.argv) > 1 else "v2.1"
TITLE = sys.argv[2] if len(sys.argv) > 2 else f"NTV Android TV {VERSION} — Native JioTV EPG & Action Button UI Fix"
NOTES_PATH = f"releases/{VERSION}/RELEASE_NOTES.md"

# 1. Get Token from Git Credential Helper
res = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n', text=True, capture_output=True)
token = [l.split('=', 1)[1] for l in res.stdout.splitlines() if l.startswith('password=')][0]
headers = {
    'Authorization': f'token {token}',
    'User-Agent': 'Mozilla/5.0',
    'Accept': 'application/vnd.github.v3+json'
}

# 2. Read Release Notes
with open(NOTES_PATH, 'r', encoding='utf-8') as f:
    body = f.read()

# 3. Create Release on nirbhay02/NTV
payload = {
    'tag_name': VERSION,
    'target_commitish': 'main',
    'name': TITLE,
    'body': body,
    'draft': False,
    'prerelease': False
}

req = urllib.request.Request(
    'https://api.github.com/repos/nirbhay02/NTV/releases',
    data=json.dumps(payload).encode('utf-8'),
    headers=headers,
    method='POST'
)

try:
    with urllib.request.urlopen(req) as resp:
        rel = json.loads(resp.read().decode())
except urllib.error.HTTPError as e:
    # If release for tag already exists, fetch it
    err_body = e.read().decode()
    print("HTTPError creating release:", err_body)
    req2 = urllib.request.Request(
        f'https://api.github.com/repos/nirbhay02/NTV/releases/tags/{VERSION}',
        headers=headers
    )
    with urllib.request.urlopen(req2) as resp2:
        rel = json.loads(resp2.read().decode())

release_id = rel['id']
upload_url = rel['upload_url'].split('{')[0]
print(f"Release #{release_id} at {rel.get('html_url')}")

# 4. Upload Assets
assets = [
    (f'releases/{VERSION}/NTV_{VERSION}.apk', f'NTV_{VERSION}.apk', 'application/vnd.android.package-archive'),
    (f'releases/{VERSION}/NTV_{VERSION}_arm64.apk', f'NTV_{VERSION}_arm64.apk', 'application/vnd.android.package-archive'),
    (f'releases/{VERSION}/NTV_{VERSION}_firetv_armv7.apk', f'NTV_{VERSION}_firetv_armv7.apk', 'application/vnd.android.package-archive'),
]

for file_path, name, mime in assets:
    if not os.path.exists(file_path):
        print(f"Warning: {file_path} not found, skipping...")
        continue
    print(f"Uploading {name} ({os.path.getsize(file_path)} bytes)...")
    with open(file_path, 'rb') as f:
        data = f.read()
    u_url = f"{upload_url}?name={name}"
    u_req = urllib.request.Request(
        u_url,
        data=data,
        headers={
            'Authorization': f'token {token}',
            'User-Agent': 'Mozilla/5.0',
            'Content-Type': mime,
            'Content-Length': str(len(data))
        },
        method='POST'
    )
    try:
        with urllib.request.urlopen(u_req) as u_resp:
            u_data = json.loads(u_resp.read().decode())
            print(f"Uploaded {name} -> {u_data.get('browser_download_url')}")
    except urllib.error.HTTPError as ue:
        print(f"Asset upload response error for {name}: {ue.read().decode()}")

print("\nRelease process completed successfully!")
