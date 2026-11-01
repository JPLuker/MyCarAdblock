#!/usr/bin/env python3
"""
What the pages in myCar's web engine hold, read through the debug build's devtools socket (forwarded
by adb): where each page is, whether it finished, how much text and how many fields it shows, where
the first field sits, and the window it's laid out in. Numbers only - never the page's words.
Usage: page-probe.py http://127.0.0.1:<port>
"""
import json
import sys
import urllib.request

import websocket  # websocket-client

PROBE = r"""(() => {
  const field = document.querySelector('input');
  const r = field ? field.getBoundingClientRect() : null;
  const root = document.querySelector('#root, #__next, #app, main') || document.body;
  const look = el => { const s = getComputedStyle(el); return s.display + '/' + s.visibility + '/' + s.opacity; };
  return JSON.stringify({
    at: location.origin + location.pathname, ready: document.readyState,
    textChars: (document.body && document.body.innerText || '').length,
    fields: document.querySelectorAll('input').length, buttons: document.querySelectorAll('button').length,
    firstField: r ? [r.left, r.top, r.width, r.height].map(Math.round) : null,
    root: root ? (root.id || root.tagName) + ' ' + look(root) + ' kids ' + root.children.length : null,
    htmlChars: document.documentElement.outerHTML.length, scripts: document.scripts.length,
    iframes: document.querySelectorAll('iframe').length,
    window: [innerWidth, innerHeight, devicePixelRatio, window.visualViewport ? visualViewport.scale : null],
    scroll: [document.documentElement.scrollWidth, document.documentElement.scrollHeight, scrollX, scrollY],
    background: document.body ? getComputedStyle(document.body).backgroundColor : null,
    uaData: navigator.userAgentData ? [navigator.userAgentData.mobile, navigator.userAgentData.platform] : null,
  });
})()"""


def main(base):
    pages = json.load(urllib.request.urlopen(base + "/json", timeout=20))
    for page in pages:
        if page.get("type") != "page":
            continue
        where = page.get("url", "").split("?")[0][:100]
        try:
            ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20)
            ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate", "params": {"expression": PROBE, "returnByValue": True}}))
            while True:
                reply = json.loads(ws.recv())
                if reply.get("id") == 1:
                    break
            ws.close()
            print(where, "->", reply.get("result", {}).get("result", {}).get("value") or reply)
        except Exception as e:  # a page that went away meanwhile
            print(where, "-> not read:", e)


if __name__ == "__main__":
    main(sys.argv[1])
