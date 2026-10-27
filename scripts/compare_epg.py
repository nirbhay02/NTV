import urllib.request
import gzip
import io
import xml.etree.ElementTree as ET

def check_epg(url, name):
    print(f"Downloading {name} from {url}...")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:
        data = gzip.decompress(resp.read())
    context = ET.iterparse(io.BytesIO(data), events=("end",))
    channels = {}
    for event, elem in context:
        if elem.tag == "channel":
            cid = elem.get("id", "")
            dname = elem.find("display-name")
            channels[cid] = dname.text if dname is not None else ""
            elem.clear()
        elif elem.tag == "programme":
            elem.clear()
    print(f"=== {name} ===")
    print(f"Total channels: {len(channels)}")
    for test_id in ["144", "165", "167", "472", "185", "6", "8", "99", "107", "0-9-zeetv", "ts840"]:
        print(f"  ID {test_id}: {channels.get(test_id, 'NOT FOUND')}")

check_epg("https://mitthu786.github.io/tvepg/jiotv/epg.xml.gz", "JioTV Dedicated")
check_epg("https://mitthu786.github.io/tvepg/epg.xml.gz", "AIO OTT EPG")
