#!/usr/bin/env python3
# Prints what Spotify's web player scripts say about a song's Canvas: the code around "canvas" in the
# page's scripts and in its chunks for the Now playing view, and wider around the Canvas player and its
# uses (canvasUrl: the address it gets; its <video> plays through Spotify's own video player).
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
# Webpack's runtime: how a chunk's address is made, and the chunks of the Now playing view.
main = next((u for u in texts if "/web-player." in u), None)
if main:
    js = texts[main]
    base = main.rsplit("/", 1)[0] + "/"
    for m in list(re.finditer(r'\.u=', js))[:3]:
        print("\n==== runtime at %d: %s" % (m.start(), js[m.start() - 300:m.start() + 1200].replace("\n", " ")[:1500]))
    names = re.findall(r'(\d+):"([a-z][\w-]*(?:now-playing|npv|nowplaying|canvas|cinema)[\w-]*)"', js)
    print("\nchunk names:", names[:40])
    for cid, name in names[:12]:
        for hm in re.finditer(r'%s:"([0-9a-f]{6,40})"' % cid, js):
            for url in (base + name + "." + hm.group(1) + ".js", base + hm.group(1) + ".js"):
                try:
                    texts[url] = get(url)
                    print("fetched", url, len(texts[url]))
                except Exception as e:
                    print("not fetched", url, e)
for url, js in texts.items():
    for m in list(re.finditer(r'canvasUrl:', js))[:6]:
        print("\n==== canvasUrl: in %s at %d" % (url.rsplit("/", 1)[1], m.start()))
        print(js[max(0, m.start() - 700):m.start() + 700].replace("\n", " "))
