/* Dependency-free regression tests for the first-party Cinema fullscreen fix.
 * node tools/test_cinema_fullscreen_alignment.cjs
 */
"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const script = fs.readFileSync(
  path.join(__dirname, "../cinema_gecko/src/main/assets/cinema_fullscreen/alignment.js"),
  "utf8"
);

function style() {
  const props = new Map();
  return {
    getPropertyValue: (name) => props.get(name)?.value || "",
    getPropertyPriority: (name) => props.get(name)?.priority || "",
    setProperty: (name, value, priority = "") => props.set(name, {value, priority}),
    removeProperty: (name) => props.delete(name)
  };
}

function runFullscreenCase({width, height, top, videoHeight, fullscreen = true}) {
  const videoStyle = style();
  const captionStyle = style();
  const video = {
    videoWidth: 1920, videoHeight: 1080,
    style: videoStyle,
    getBoundingClientRect() {
      const translated = Number.parseFloat(videoStyle.getPropertyValue("translate").split(" ")[1]) || 0;
      return {top: top + translated, height: videoHeight, width};
    },
    matches() { return false; }
  };
  const caption = {
    style: captionStyle,
    matches(selector) { return selector === ".ytp-caption-window-container"; }
  };
  const stage = {
    getBoundingClientRect() { return {top: 0, width, height}; },
    querySelector(selector) {
      if (selector === "video.html5-main-video, video") return video;
      if (selector === ".ytp-caption-window-container") return caption;
      return null;
    }
  };
  const doc = {
    fullscreenElement: fullscreen ? stage : null,
    querySelector(selector) {
      if (selector === ".ytp-caption-window-container") return caption;
      return null;
    },
    addEventListener() {}
  };
  let tick = null;
  const fakeWindow = {innerWidth: width, innerHeight: height, addEventListener() {}};
  vm.runInNewContext(script, {
    document: doc,
    window: fakeWindow,
    requestAnimationFrame(callback) { callback(); },
    setTimeout() {},
    setInterval(callback) { tick = callback; },
    Number, Math
  }, {filename: "alignment.js", timeout: 1000});
  assert.equal(typeof tick, "function");
  return {videoStyle, captionStyle, exitFullscreen() { doc.fullscreenElement = null; tick(); }};
}

// Narrow 56% Cinema pane: video pinned to the top gets +104px to center it,
// while captions anchored at the bottom move up by the same amount.
{
  const frame = runFullscreenCase({width: 313, height: 384, top: 0, videoHeight: 176});
  assert.equal(frame.videoStyle.getPropertyValue("translate"), "0px 104px");
  assert.equal(frame.captionStyle.getPropertyValue("translate"), "0px -104px");
  frame.exitFullscreen();
  assert.equal(frame.videoStyle.getPropertyValue("translate"), "");
  assert.equal(frame.captionStyle.getPropertyValue("translate"), "");
}

// Full-width Gecko fullscreen is already correct: do not touch its DOM.
{
  const frame = runFullscreenCase({width: 560, height: 384, top: 34, videoHeight: 315});
  assert.equal(frame.videoStyle.getPropertyValue("translate"), "");
  assert.equal(frame.captionStyle.getPropertyValue("translate"), "");
}

// If Gecko centered the split video without help, don't shift it further.
{
  const frame = runFullscreenCase({width: 313, height: 384, top: 104, videoHeight: 176});
  assert.equal(frame.videoStyle.getPropertyValue("translate"), "0px 0px");
  assert.equal(frame.captionStyle.getPropertyValue("translate"), "0px 0px");
}

console.log("Cinema fullscreen centering: narrow, full-width, and restore checks passed");
