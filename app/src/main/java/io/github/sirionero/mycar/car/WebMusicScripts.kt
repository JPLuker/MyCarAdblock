package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.webmusic.WebMusicService
import org.json.JSONObject

/** The scripts myCar adds to a music service's web player (see [WebMusicPage]), on its own addresses only. */
internal object WebMusicScripts {

    /**
     * From the start of each of its pages, before the service's own scripts:
     * - its CSS of its own, if any;
     * - the page laid out at the width myCar gives it (`__mycarMusicWidth`, `__mycarMusicViewport`),
     *   as in a computer's window that wide, then fitted to the car's screen;
     * - what plays, for myCar's player, dock and the car's buttons: the page's own Media Session
     *   (what the service tells a computer's media keys) is kept as it's set - its song, its state, its
     *   position and its handlers - and said to myCar (`MyCarMusic`) when it changes. Android's web
     *   engine has none (seen in the car preview): then a stand-in, there before the page's scripts,
     *   takes what they say. Where the page says nothing there, its media elements and its player
     *   bar ([WebMusicService.bar]) stand in;
     * - `__mycarMusicAct(action, seconds)`: a button from the car, through the page's own handler as
     *   a computer's media key would, or else the service's own button on the page.
     */
    fun setup(service: WebMusicService): String {
        val bar = service.bar?.let {
            JSONObject()
                .put("playPause", it.playPause).put("next", it.next).put("previous", it.previous)
                .put("position", it.position).put("duration", it.duration)
                .put("widget", it.widget).put("title", it.title).put("artist", it.artist)
                .toString()
        } ?: "null"
        return SETUP.replace("__MYCAR_BAR__", bar).replace("__MYCAR_CSS__", JSONObject.quote(service.css))
    }

    private const val SETUP = """
(function () {
  if (window.__mycarMusic || window.top !== window || !window.MyCarMusic) return;
  window.__mycarMusic = true;
  var car = window.MyCarMusic;
  // The service's own player bar, by data-testid (or null), and its own CSS.
  var BAR = __MYCAR_BAR__;
  var CSS = __MYCAR_CSS__;

  var style = document.createElement('style');
  style.textContent = CSS;
  function addStyle() {
    var parent = document.head || document.documentElement;
    if (CSS && parent && !style.isConnected) parent.appendChild(style);
  }

  // The layout width myCar gives the page (__mycarMusicWidth, from myCar): the page is laid out
  // that wide, as on a computer's window, then fitted to the car's screen.
  function fit() {
    var width = window.__mycarMusicWidth;
    if (!width || !document.head) return;
    var content = 'width=' + Math.round(width) + ', user-scalable=no';
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'viewport';
      document.head.appendChild(meta);
    }
    if (meta.content !== content) meta.content = content;
  }
  window.__mycarMusicViewport = function (width) {
    window.__mycarMusicWidth = width;
    fit();
  };
  addStyle();
  var watch = new MutationObserver(function () {
    addStyle();
    if (document.querySelector('meta[name="viewport"]')) fit();
  });
  watch.observe(document, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', function () {
    addStyle();
    fit();
    watch.disconnect();
  });

  // --- What plays, for myCar's player, dock and the car's buttons ---
  // The page's own Media Session (what it tells a computer's media keys): its handlers are kept,
  // and its song, state and position read as it sets them.
  // Android's web engine has no Media Session (what a computer's media keys and its system player
  // use), and these players tell it what plays: a stand-in, there before the page's own scripts,
  // takes what they say - the song, the state, the position, the buttons - for myCar.
  if (!navigator.mediaSession) {
    if (!window.MediaMetadata) {
      window.MediaMetadata = function MediaMetadata(init) {
        init = init || {};
        this.title = String(init.title || '');
        this.artist = String(init.artist || '');
        this.album = String(init.album || '');
        this.artwork = Array.prototype.map.call(init.artwork || [], function (a) {
          return { src: String(a.src || ''), sizes: String(a.sizes || ''), type: String(a.type || '') };
        });
      };
    }
    var said = { metadata: null, playbackState: 'none' };
    var standIn = {
      setActionHandler: function () {},
      setPositionState: function () {},
      setCameraActive: function () {},
      setMicrophoneActive: function () {}
    };
    ['metadata', 'playbackState'].forEach(function (name) {
      Object.defineProperty(standIn, name, {
        configurable: true,
        enumerable: true,
        get: function () { return said[name]; },
        set: function (value) { said[name] = value; schedule(); }
      });
    });
    Object.defineProperty(standIn, '__mycar', { value: true });
    try {
      Object.defineProperty(Navigator.prototype, 'mediaSession', { configurable: true, enumerable: true, get: function () { return standIn; } });
    } catch (e) {
      try { Object.defineProperty(navigator, 'mediaSession', { configurable: true, value: standIn }); } catch (e2) {}
    }
  }
  var session = navigator.mediaSession || null;
  var handlers = {};
  var position = null;
  if (session) {
    var setAction = session.setActionHandler;
    if (setAction) session.setActionHandler = function (action, handler) {
      handlers[action] = handler || null;
      schedule();
      return setAction.apply(session, arguments);
    };
    var setPosition = session.setPositionState;
    if (setPosition) session.setPositionState = function (state) {
      position = state && isFinite(state.duration) && state.duration > 0
        ? { duration: state.duration, at: state.position || 0, rate: state.playbackRate || 1, when: Date.now() }
        : null;
      schedule();
      return setPosition.apply(session, arguments);
    };
    var proto = Object.getPrototypeOf(session);
    if (!session.__mycar) ['metadata', 'playbackState'].forEach(function (name) {
      var d = proto && Object.getOwnPropertyDescriptor(proto, name);
      if (!d || !d.get || !d.set) return;
      Object.defineProperty(session, name, {
        configurable: true,
        enumerable: true,
        get: function () { return d.get.call(session); },
        set: function (value) { d.set.call(session, value); schedule(); }
      });
    });
  }

  // The media elements it plays, in the page or not (Spotify's never is).
  var elements = [];
  function watchElement(el) {
    if (elements.indexOf(el) >= 0) return;
    elements.push(el);
    if (elements.length > 8) elements.shift();
    ['play', 'playing', 'pause', 'ended', 'durationchange', 'seeked', 'emptied'].forEach(function (type) {
      el.addEventListener(type, schedule);
    });
  }
  if (window.HTMLMediaElement) {
    var play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      watchElement(this);
      return play.apply(this, arguments);
    };
  }
  document.addEventListener('play', function (e) {
    if (window.HTMLMediaElement && e.target instanceof HTMLMediaElement) watchElement(e.target);
  }, true);
  // The music, not a muted loop behind a song (Spotify's Canvas).
  function music() {
    var paused = null;
    for (var i = elements.length - 1; i >= 0; i--) {
      var el = elements[i];
      if (el.muted || el.loop) continue;
      if (!el.paused && !el.ended) return el;
      if (!paused) paused = el;
    }
    return paused;
  }

  function byTestId(id, root) { return (root || document).querySelector('[data-testid="' + id + '"]'); }
  function text(el) { return el ? String(el.textContent || '').trim() : ''; }
  function clip(s, n) { return String(s || '').replace(/\s+/g, ' ').trim().slice(0, n); }
  // "3:45", "1:02:03"; "-2:30" is the time left.
  function clock(s) {
    var m = /^(-?)(\d+):(\d{2})(?::(\d{2}))?$/.exec(String(s || '').trim());
    if (!m) return null;
    var seconds = m[4] !== undefined ? +m[2] * 3600 + +m[3] * 60 + +m[4] : +m[2] * 60 + +m[3];
    return { ms: seconds * 1000, left: m[1] === '-' };
  }
  function absolute(src) {
    try { return src ? new URL(src, location.href).href : ''; } catch (e) { return ''; }
  }
  function biggestArt(meta) {
    var list = meta && meta.artwork;
    if (!list || !list.length) return '';
    var best = '', size = -1;
    for (var i = 0; i < list.length; i++) {
      var side = 0;
      String(list[i].sizes || '').split(/\s+/).forEach(function (s) {
        var n = parseInt(s, 10);
        if (n > side) side = n;
      });
      if (side > size) { size = side; best = list[i].src; }
    }
    return absolute(best);
  }

  function snapshot() {
    var meta = session && session.metadata;
    var widget = BAR && byTestId(BAR.widget);
    var title = (meta && meta.title) || text(widget && byTestId(BAR.title, widget));
    var artist = (meta && meta.artist) || text(widget && byTestId(BAR.artist, widget));
    var cover = widget && widget.querySelector('img');
    var el = music();
    var said = session && session.playbackState;
    var playing = said === 'playing' ? true : said === 'paused' ? false : !!(el && !el.paused && !el.ended);
    var at = 0, duration = 0;
    if (position) {
      duration = position.duration * 1000;
      at = (position.at + (playing ? (Date.now() - position.when) / 1000 * position.rate : 0)) * 1000;
    } else {
      var now = BAR && clock(text(byTestId(BAR.position))), end = BAR && clock(text(byTestId(BAR.duration)));
      if (now && end) {
        at = now.ms;
        duration = end.left ? now.ms + end.ms : end.ms;
      } else if (el && isFinite(el.duration) && el.duration > 0 && el.duration < 6 * 3600) {
        at = el.currentTime * 1000;
        duration = el.duration * 1000;
      }
    }
    if (duration > 0) at = Math.max(0, Math.min(at, duration));
    return {
      type: 'state',
      title: clip(title, 300),
      artist: clip(artist, 300),
      album: clip(meta && meta.album, 300),
      art: biggestArt(meta) || absolute(cover && cover.src),
      playing: playing,
      position: Math.round(at),
      duration: Math.round(duration),
      seek: typeof handlers.seekto === 'function'
    };
  }

  // Said when something changes, and when the position jumps (a seek): not every second.
  var timer = 0, last = '', lastAt = 0, lastPosition = 0, lastPlaying = false;
  function schedule() {
    if (!timer) timer = setTimeout(function () { timer = 0; send(); }, 150);
  }
  function send() {
    var s;
    try { s = snapshot(); } catch (e) { return; }
    var now = Date.now();
    var key = [s.title, s.artist, s.album, s.art, s.playing, s.duration, s.seek].join('\n');
    var expected = lastPosition + (lastPlaying ? now - lastAt : 0);
    if (key === last && Math.abs(s.position - expected) < 2500) return;
    last = key;
    lastAt = now;
    lastPosition = s.position;
    lastPlaying = s.playing;
    car.postMessage(JSON.stringify(s));
  }
  setInterval(function () { fit(); send(); }, 1000);

  // A button from myCar's player, the dock or the car's wheel: the page's own handler, as a
  // computer's media keys would call it; without one, the service's own button on the page.
  var BUTTONS = BAR ? { play: BAR.playPause, pause: BAR.playPause, nexttrack: BAR.next, previoustrack: BAR.previous } : {};
  window.__mycarMusicAct = function (action, seconds) {
    var h = handlers[action];
    if (typeof h === 'function') {
      try {
        h({ action: action, seekTime: seconds, fastSeek: false });
        schedule();
        return 'page';
      } catch (e) {}
    }
    var playing = snapshot().playing;
    if ((action === 'play' && playing) || (action === 'pause' && !playing)) return 'already';
    var button = BUTTONS[action] && byTestId(BUTTONS[action]);
    if (button && !button.disabled) {
      button.click();
      schedule();
      return 'button';
    }
    var el = music();
    if (el && action === 'play') { el.play(); return 'element'; }
    if (el && action === 'pause') { el.pause(); return 'element'; }
    return 'none';
  };
})();
"""

    /**
     * On a search's page, the Play of what it found first, pressed: the service's own buttons
     * ([WebMusicService.firstPlay]) as they are, then the usual marks of a Play button
     * ([WebMusicService.ANY_PLAY]) only where they show. Says which, or "nothing" yet.
     */
    fun firstPlay(service: WebMusicService): String {
        val own = org.json.JSONArray(service.firstPlay).toString()
        val any = org.json.JSONArray(WebMusicService.ANY_PLAY).toString()
        return """
(function (own, any) {
  function find(selector, shown) {
    var all;
    try { all = document.querySelectorAll(selector); } catch (e) { return null; }
    for (var i = 0; i < all.length; i++) {
      var r = all[i].getBoundingClientRect();
      if (!shown || (r.width > 0 && r.height > 0)) return all[i];
    }
    return null;
  }
  var lists = [[own, false], [any, true]];
  for (var l = 0; l < lists.length; l++) {
    for (var i = 0; i < lists[l][0].length; i++) {
      var el = find(lists[l][0][i], lists[l][1]);
      if (el) {
        var inner = el.querySelector('button, [role="button"]');
        (el.matches('button, [role="button"], a') || !inner ? el : inner).click();
        return 'pressed ' + lists[l][0][i];
      }
    }
  }
  return 'nothing';
})($own, $any)
"""
    }

    /** Debug builds, after a search: the page's buttons that look like a Play, to fit [WebMusicService.firstPlay]. */
    const val PLAY_BUTTONS = """
(function () {
  var out = [];
  document.querySelectorAll('[data-testid*="play" i], [data-test*="play" i], [class*="play-button" i], [class*="playButton" i], [class*="sc-button-play"], ytmusic-play-button-renderer, [aria-label^="Play" i]').forEach(function (el) {
    if (out.length >= 15) return;
    var r = el.getBoundingClientRect();
    out.push(el.tagName.toLowerCase() + (el.getAttribute('data-testid') ? '[data-testid=' + el.getAttribute('data-testid') + ']' : '') +
      (el.getAttribute('data-test') ? '[data-test=' + el.getAttribute('data-test') + ']' : '') +
      (el.getAttribute('aria-label') ? '[aria-label=' + el.getAttribute('aria-label').slice(0, 30) + ']' : '') +
      (typeof el.className === 'string' && el.className ? '.' + el.className.split(' ').slice(0, 2).join('.') : '') +
      ' ' + Math.round(r.width) + 'x' + Math.round(r.height));
  });
  return location.href + ' | ' + out.join(' | ');
})()
"""

    /**
     * Once, at its first page: what the car's web engine is to the service - its address, the user
     * agent, whether it takes protected audio (Widevine, which most of these players need), whether
     * the page has said what plays - into myCar's log. With [layout] (debug builds), a few seconds
     * later also the page's parts, to fit it better.
     */
    fun probe(layout: Boolean) = """
(function () {
  var car = window.MyCarMusic;
  if (!car) return;
  var hints = navigator.userAgentData;
  var session = navigator.mediaSession;
  var out = { type: 'probe', url: location.href, title: document.title, ua: navigator.userAgent,
    mobile: hints ? hints.mobile : null, platform: hints ? hints.platform : null,
    mediaSession: !session ? 'none' : session.__mycar ? 'myCar stand-in' : 'native', metadata: !!(session && session.metadata),
    width: innerWidth, height: innerHeight, ratio: devicePixelRatio };
  function send(widevine) { out.widevine = widevine; car.postMessage(JSON.stringify(out)); }
  if (!navigator.requestMediaKeySystemAccess) send('no EME');
  else navigator.requestMediaKeySystemAccess('com.widevine.alpha', [{ audioCapabilities: [{ contentType: 'audio/mp4; codecs="mp4a.40.2"' }] }])
    .then(function (access) { send('yes, ' + access.keySystem); }, function (e) { send('no: ' + e.name + ' ' + e.message); });
  if (!$layout) return;
  setTimeout(function () {
    var seen = {}, ids = [], links = [], labels = [];
    document.querySelectorAll('[data-testid]').forEach(function (el) {
      var id = el.getAttribute('data-testid');
      if (!seen[id]) { seen[id] = true; ids.push(id); }
    });
    document.querySelectorAll('a[href*="download"], a[href*="premium"], a[href*="signup"], a[href*="login"]').forEach(function (a) {
      var holder = a.parentElement && a.parentElement.closest('[data-testid]');
      links.push(a.getAttribute('href') + ' [' + String(a.getAttribute('aria-label') || a.textContent || '').trim().slice(0, 40) + '] in ' + (holder ? holder.getAttribute('data-testid') : '-'));
    });
    document.querySelectorAll('a[aria-label], button[aria-label]').forEach(function (el) {
      if (labels.length < 60) labels.push(el.tagName + ':' + el.getAttribute('aria-label'));
    });
    // What myCar's Back sits over, in the top corner: from the element there up to the page.
    var under = [];
    for (var el = document.elementFromPoint(24, 24); el && under.length < 6; el = el.parentElement) {
      under.push(el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.getAttribute('data-testid') ? '[data-testid=' + el.getAttribute('data-testid') + ']' : '') +
        (el.getAttribute('aria-label') ? '[aria-label=' + el.getAttribute('aria-label') + ']' : '') + (el.className && el.className.baseVal === undefined ? '.' + String(el.className).split(' ').slice(0, 2).join('.') : ''));
    }
    car.postMessage(JSON.stringify({ type: 'layout', url: location.href, metadata: !!(session && session.metadata),
      under: under, ids: ids.slice(0, 120), links: links.slice(0, 8), labels: labels.slice(0, 30) }));
  }, 12000);
})();
"""
}
