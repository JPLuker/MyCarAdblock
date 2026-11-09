#!/usr/bin/env python3
# Checks, in Spotify's web player as it is today, what myCar's page script relies on for a song's Canvas
# (WebMusicScripts, videoCover): the Canvas player gets the video's address as the canvasUrl prop and
# plays it by file id through Spotify's own video player (so its <video> has no such address), and its
# previews of other songs play on hover (playbackType loopOnHover). Prints facts, not Spotify's code.
import re, urllib.request
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req, timeout=40).read().decode("utf-8", "replace")
html = get("https://open.spotify.com/")
scripts = sorted(set(re.findall(r'<script[^>]+src="([^"]+\.js)"', html)))
js = ""
for s in scripts:
    try:
        js += get(s)
    except Exception as e:
        print("not fetched:", s.rsplit("/", 1)[-1], e)
def has(pattern):
    return len(re.findall(pattern, js))
print("scripts:", len(scripts), "-", sum(1 for _ in js), "characters")
print("components given canvasUrl:", has(r'canvasUrl:'))
print("the Canvas player plays by file id (MANIFEST_ID) near canvasUrl:",
      bool(re.search(r'canvasUrl:[^;]{0,4000}format:"MANIFEST_ID"|format:"MANIFEST_ID"[^;]{0,4000}canvasUrl', js)))
print("its playback types: loopOnHover", has(r'"loopOnHover"'), "- loopOnPlayback", has(r'"loopOnPlayback"'))
print("canvas.url in the songs' metadata:", has(r'"canvas\.url"'), "- the canvas query:", has(r'"canvas","query"'))
print("canvaz.scdn.co in the scripts:", has(r'canvaz\.scdn\.co'))
print("Now playing view modes (artwork, canvas…):", bool(re.search(r'\["artwork","canvas","hero","video","lyrics"\]', js)))
