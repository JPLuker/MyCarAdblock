#!/usr/bin/env python3
"""
Spotify's sign-in page on the phone, tried several ways in the same web engine, through the debug
build's devtools socket (forwarded by adb): as myCar opens it (a computer's user agent), with a
phone's user agent, the web player, and the sign-in again after the web player. Numbers and the
page's public markup only. Usage: signin-probe.py http://127.0.0.1:<port> <out prefix>
"""
import base64
import json
import re
import sys
import time
import urllib.request

import websocket  # websocket-client

PROBE = r"""(() => {
  const field = document.querySelector('input');
  const r = field ? field.getBoundingClientRect() : null;
  const failed = performance.getEntriesByType('resource')
    .filter(e => (e.responseStatus && e.responseStatus >= 400) || (e.transferSize === 0 && e.decodedBodySize === 0 && e.duration > 0))
    .map(e => (e.responseStatus || 0) + ' ' + e.name.split('?')[0].slice(-90)).slice(0, 15);
  return JSON.stringify({
    at: location.origin + location.pathname, ready: document.readyState,
    textChars: (document.body && document.body.innerText || '').length,
    fields: document.querySelectorAll('input').length, buttons: document.querySelectorAll('button').length,
    firstField: r ? [r.left, r.top, r.width, r.height].map(Math.round) : null,
    window: [innerWidth, innerHeight, devicePixelRatio],
    scroll: [document.documentElement.scrollWidth, document.documentElement.scrollHeight],
    scripts: [...document.scripts].map(s => (s.src || 'inline').split('?')[0].slice(-70)).slice(0, 12),
    resources: performance.getEntriesByType('resource').length, failed,
    bodyStart: (document.body ? document.body.innerHTML : '').replace(/\s+/g, ' ').slice(0, 900),
  });
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


def main(base, out):
    pages = [p for p in json.load(urllib.request.urlopen(base + "/json", timeout=20)) if p.get("type") == "page"]
    target = next((p for p in pages if "spotify" in p.get("url", "")), None)
    if target is None:
        print("no Spotify page among", [p.get("url", "")[:60] for p in pages])
        return
    page = Page(target["webSocketDebuggerUrl"])
    page.call("Page.enable")
    page.call("Network.enable")
    print("as myCar opens it:", page.value(PROBE))
    page.shot(out + "-1-as-opened.png")
    ua = page.value("navigator.userAgent") or ""
    print("user agent:", ua)
    phone = re.sub(r"\(X11; Linux x86_64\)", "(Linux; Android 14; K)", ua).replace("Safari/537.36", "Mobile Safari/537.36")
    page.call("Network.setUserAgentOverride", {"userAgent": phone})
    page.call("Page.reload", {"ignoreCache": True})
    time.sleep(15)
    print("with a phone's user agent:", page.value(PROBE))
    page.shot(out + "-2-phone-ua.png")
    page.call("Network.setUserAgentOverride", {"userAgent": ua})
    page.call("Page.navigate", {"url": "https://open.spotify.com/"})
    time.sleep(15)
    print("the web player:", page.value(PROBE))
    page.shot(out + "-3-player.png")
    page.call("Page.navigate", {"url": "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"})
    time.sleep(15)
    print("sign-in after the web player:", page.value(PROBE))
    page.shot(out + "-4-after-player.png")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
