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


LOOK = r"""(() => {
  const field = document.querySelector('input');
  if (!field) return 'no field';
  const r = field.getBoundingClientRect();
  const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
  const name = el => el ? (el.tagName + (el.id ? '#' + el.id : '') + (el.className && el.className.baseVal === undefined ? '.' + String(el.className).split(' ').slice(0, 2).join('.') : '')) : null;
  const chain = [];
  for (let el = field; el; el = el.parentElement) {
    const s = getComputedStyle(el);
    if (s.opacity !== '1' || s.visibility !== 'visible' || s.filter !== 'none' || s.display === 'none' || (s.transform !== 'none' && s.transform.indexOf('matrix(1, 0, 0, 1') !== 0) || s.clipPath !== 'none' || s.contentVisibility === 'hidden')
      chain.push(name(el) + ' opacity ' + s.opacity + ' vis ' + s.visibility + ' filter ' + s.filter + ' transform ' + s.transform + ' clip ' + s.clipPath + ' cv ' + s.contentVisibility);
  }
  const h = document.querySelector('h1');
  const hs = h ? getComputedStyle(h) : null;
  const covers = [...document.querySelectorAll('body *')].filter(el => {
    const s = getComputedStyle(el); if (s.position !== 'fixed' && s.position !== 'absolute') return false;
    const b = el.getBoundingClientRect(); return b.width >= innerWidth * 0.9 && b.height >= innerHeight * 0.9;
  }).map(el => name(el) + ' z ' + getComputedStyle(el).zIndex + ' bg ' + getComputedStyle(el).backgroundColor + ' op ' + getComputedStyle(el).opacity).slice(0, 6);
  return JSON.stringify({
    field: [r.left, r.top, r.width, r.height].map(Math.round), onTop: name(top), fieldIsOnTop: top === field || field.contains(top),
    hidden: chain, h1: h ? [h.innerText.length, hs.color, hs.opacity, hs.visibility] : null,
    fieldColors: [getComputedStyle(field).color, getComputedStyle(field).backgroundColor],
    covers, dark: matchMedia('(prefers-color-scheme: dark)').matches, colorScheme: getComputedStyle(document.documentElement).colorScheme,
    fonts: document.fonts ? document.fonts.status : null, htmlBg: getComputedStyle(document.documentElement).backgroundColor,
  });
})()"""


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
    print("why it doesn't show:", page.value(LOOK))
    page.shot(out + "-1-as-opened.png")
    print("ua:", page.value("navigator.userAgent"))
    # 1. Without the web engine's automatic dark mode.
    print("auto dark off:", page.call("Emulation.setAutoDarkModeOverride", {"enabled": False}).get("error"))
    time.sleep(3)
    print("why, auto dark off:", page.value(LOOK))
    page.shot(out + "-2-auto-dark-off.png")
    page.call("Emulation.setAutoDarkModeOverride", {})
    # 2. A light colour scheme asked of the page.
    page.call("Emulation.setEmulatedMedia", {"features": [{"name": "prefers-color-scheme", "value": "light"}]})
    time.sleep(3)
    print("why, light scheme:", page.value(LOOK))
    page.shot(out + "-3-light.png")
    page.call("Emulation.setEmulatedMedia", {"features": []})
    # 3. Laid out as wide as the car's page (a computer's window), then loaded again.
    page.call("Emulation.setDeviceMetricsOverride", {"width": 1024, "height": 768, "deviceScaleFactor": 1, "mobile": False})
    page.call("Page.reload", {"ignoreCache": False})
    time.sleep(15)
    print("wide:", page.value(PROBE))
    print("why, wide:", page.value(LOOK))
    page.shot(out + "-4-wide.png")
    page.call("Emulation.clearDeviceMetricsOverride")
    # 4. Loaded again as it is, after all that: the same?
    page.call("Page.reload", {"ignoreCache": False})
    time.sleep(15)
    print("again as opened:", page.value(LOOK))
    page.shot(out + "-5-again.png")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
