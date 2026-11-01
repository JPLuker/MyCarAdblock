#!/usr/bin/env python3
"""
A music service's sign-in page on the phone (WebMusicSignInActivity), looked at through the debug
build's devtools socket (forwarded by adb): is its first field laid out, painted and the thing a
finger at its centre would touch? Spotify's came out black once - the WebView, in an AndroidView,
laid the page out 0 px tall - and this says so again if it ever does. Numbers and the page's public
markup only. Usage: signin-probe.py http://127.0.0.1:<port> <out prefix> [part of the page's address]
"""
import base64
import json
import sys
import urllib.request

import websocket  # websocket-client

LOOK = r"""(() => {
  const field = document.querySelector('input');
  const name = el => el ? (el.tagName + (el.id ? '#' + el.id : '')) : null;
  const tall = el => el ? Math.round(el.getBoundingClientRect().height) : null;
  const main = document.querySelector('main');
  const out = {
    at: location.origin + location.pathname,
    window: [innerWidth, innerHeight],
    heights: { html: tall(document.documentElement), body: tall(document.body), main: tall(main) },
  };
  if (!field) return JSON.stringify({ ...out, field: null });
  const r = field.getBoundingClientRect();
  const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
  return JSON.stringify({ ...out, field: [r.left, r.top, r.width, r.height].map(Math.round),
    onTop: name(top), reachable: !!top && (top === field || field.contains(top)) });
})()"""


class Page:
    def __init__(self, url):
        self.ws = websocket.create_connection(url, timeout=40, suppress_origin=True)
        self.n = 0

    def call(self, method, params=None):
        self.n += 1
        self.ws.send(json.dumps({"id": self.n, "method": method, "params": params or {}}))
        while True:
            m = json.loads(self.ws.recv())
            if m.get("id") == self.n:
                return m

    def value(self, expression):
        r = self.call("Runtime.evaluate", {"expression": expression, "returnByValue": True})
        return r.get("result", {}).get("result", {}).get("value")

    def shot(self, path):
        data = self.call("Page.captureScreenshot", {"format": "png"}).get("result", {}).get("data")
        if data:
            with open(path, "wb") as f:
                f.write(base64.b64decode(data))


def main(base, out, site):
    pages = [p for p in json.load(urllib.request.urlopen(base + "/json", timeout=20)) if p.get("type") == "page"]
    target = next((p for p in pages if site in p.get("url", "")), None)
    if target is None:
        print("no", site, "page among", [p.get("url", "")[:60] for p in pages])
        return
    page = Page(target["webSocketDebuggerUrl"])
    page.call("Page.enable")
    print("web engine:", json.load(urllib.request.urlopen(base + "/json/version", timeout=20)).get("Browser"))
    look = page.value(LOOK)
    print("sign-in page:", look)
    seen = json.loads(look) if look else {}
    print("sign-in field:", "reachable" if seen.get("reachable") else "NOT reachable")
    page.shot(out + ".png")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "spotify")
