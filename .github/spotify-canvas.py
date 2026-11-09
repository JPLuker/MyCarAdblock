#!/usr/bin/env python3
# Prints what Spotify's web player scripts say about a song's Canvas: the code around "canvaz" (its CDN)
# and "canvas" in the page's scripts and in the chunks they name that look like the Now playing view.
import re, sys, urllib.request
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req, timeout=40).read().decode("utf-8", "replace")
html = get("https://open.spotify.com/")
scripts = sorted(set(re.findall(r'<script[^>]+src="([^"]+\.js)"', html)))
print("page scripts:", scripts)
texts = {}
for s in scripts:
    try:
        texts[s] = get(s)
    except Exception as e:
        print("not fetched", s, e)
# Webpack chunk names and hashes: {id:"name"} and {id:"hash"} maps in the runtime.
chunks = {}
for url, js in list(texts.items()):
    base = url.rsplit("/", 1)[0] + "/"
    names = dict(re.findall(r'(\d+):"([a-zA-Z][\w-]*)"', js))
    hashes = dict(re.findall(r'(\d+):"([0-9a-f]{8,32})"', js))
    for cid, name in names.items():
        if cid in hashes and re.search(r'npv|now-?playing|canvas|nowplaying', name, re.I):
            chunks[base + name + "." + hashes[cid] + ".js"] = name
print("chunks that look like the Now playing view:", sorted(chunks.values()))
for url in sorted(chunks):
    try:
        texts[url] = get(url)
    except Exception as e:
        print("not fetched", url, e)
for url, js in texts.items():
    found = re.findall(r'.{0,160}(?:canvaz|[Cc]anvas[A-Z_]|canvas_|"canvas|\.canvas\b|canvas-).{0,160}', js)
    found = [f for f in found if 'getContext' not in f and 'HTMLCanvasElement' not in f]
    if not found:
        continue
    print("\n==", url, len(js), "bytes,", len(found), "places")
    for f in found[:40]:
        print("  ..." + f.replace("\n", " ") + "...")
