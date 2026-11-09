#!/usr/bin/env python3
# Prints what Spotify's web player scripts say about a song's Canvas: the code around "canvas" in the
# page's scripts, and wider around the Canvas player itself (where its <video> gets its source).
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
for url, js in texts.items():
    found = re.findall(r'.{0,160}(?:canvaz|[Cc]anvas[A-Z_]|canvas_|"canvas|\.canvas\b|canvas-).{0,160}', js)
    found = [f for f in found if 'getContext' not in f and 'HTMLCanvasElement' not in f]
    if not found:
        continue
    print("\n==", url, len(js), "bytes,", len(found), "places")
    for f in found[:40]:
        print("  ..." + f.replace("\n", " ") + "...")
# Wider, around the Canvas player: how its video plays (a <video> with the canvas's address, or a player
# fed by its file id).
for marker in ["canvasThumbnailUrl:", "overridePlatformId", "\"canvas.url\"", "cnvs", "canvaz", "LoopOnPlayback"]:
    for url, js in texts.items():
        at = [m.start() for m in re.finditer(re.escape(marker), js)][:2]
        for i in at:
            print("\n==== %s in %s at %d" % (marker, url.rsplit("/", 1)[1], i))
            print(js[max(0, i - 1500):i + 4500].replace("\n", " "))
