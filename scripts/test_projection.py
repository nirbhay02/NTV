import gzip
import re
import datetime

# Let's simulate now = 2026-10-26 11:20:00 IST
# In UTC: 2026-10-26 05:50:00 UTC
now_dt = datetime.datetime(2026, 10, 26, 5, 50, 0, tzinfo=datetime.timezone.utc)
now_epoch = int(now_dt.timestamp() * 1000)

with gzip.open("scripts/epg.xml.gz", "rt", encoding="utf-8", errors="ignore") as f:
    text = f.read()

def parse_xmltv(s):
    # 20261025173900 +0000
    y = int(s[0:4])
    m = int(s[4:6])
    d = int(s[6:8])
    h = int(s[8:10])
    mi = int(s[10:12])
    sec = int(s[12:14])
    tz = s[15:20]
    dt = datetime.datetime(y, m, d, h, mi, sec, tzinfo=datetime.timezone.utc)
    return int(dt.timestamp() * 1000)

for test_cid in ["165", "167", "185", "472", "484", "538", "528"]:
    progs = []
    for m in re.finditer(rf'<programme start="([^"]+)" stop="([^"]+)" channel="{test_cid}"[^>]*><title[^>]*>([^<]+)</title>', text):
        st, sp, t = m.groups()
        progs.append((parse_xmltv(st), parse_xmltv(sp), t))
    
    # 1. Direct hit
    direct = next((p for p in progs if p[0] <= now_epoch < p[1]), None)
    if direct:
        print(f"[{test_cid}] DIRECT HIT: {direct[2]} ({datetime.datetime.fromtimestamp(direct[0]/1000)} to {datetime.datetime.fromtimestamp(direct[1]/1000)})")
        continue
    
    # 2. 24h shift
    one_day = 24 * 3600 * 1000
    shift = next((p for p in progs if (p[0] + one_day) <= now_epoch < (p[1] + one_day)), None)
    if shift:
        s = datetime.datetime.fromtimestamp((shift[0] + one_day)/1000)
        e = datetime.datetime.fromtimestamp((shift[1] + one_day)/1000)
        print(f"[{test_cid}] 24H SCHEDULE PROJECTION: {shift[2]} ({s} to {e})")
        continue

    print(f"[{test_cid}] No hit, total progs={len(progs)}")
