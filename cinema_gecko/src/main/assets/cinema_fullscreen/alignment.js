/* myCar Cinema -- correct Mozilla GeckoView's narrow-pane fullscreen geometry.
 *
 * Bug 1750416: with a ContentDelegate and a GeckoView smaller than the Android
 * window, YouTube's fullscreen video can be pinned to the pane top while its
 * captions remain anchored near the pane bottom.
 *
 * The Android GeckoView itself MUST NOT resize on split fullscreen entry: that
 * caused Firefox to exit document fullscreen immediately. Instead, change ONLY
 * the translate CSS property of the page's video and caption overlay. This is
 * scoped to YouTube in a narrow fullscreen viewport. No network, storage,
 * monitoring, user actions, or audio/video API manipulation.
 */
(() => {
  "use strict";

  // Translate is independent of YouTube's own transform/top/width/height styles.
  // Capture original author styles and restore them when leaving fullscreen.
  const altered = new Map();
  let pending = false;

  function rememberAndTranslate(element, dy) {
    if (!element) return;
    if (!altered.has(element)) {
      altered.set(element, {
        value: element.style.getPropertyValue("translate"),
        priority: element.style.getPropertyPriority("translate")
      });
    }
    element.style.setProperty("translate", "0px " + Math.round(dy) + "px", "important");
  }

  function restore(element) {
    const old = altered.get(element);
    if (!old) return;
    if (old.value) element.style.setProperty("translate", old.value, old.priority);
    else element.style.removeProperty("translate");
    altered.delete(element);
  }

  function restoreAll() {
    for (const element of altered.keys()) restore(element);
  }

  function baseRect(element) {
    const original = altered.get(element);
    if (!original) return element.getBoundingClientRect();
    // Read the unmodified rectangle to avoid accumulating a new offset on
    // every resize/caption update. These synchronous changes do not paint.
    if (original.value) {
      element.style.setProperty("translate", original.value, original.priority);
    } else {
      element.style.removeProperty("translate");
    }
    const rect = element.getBoundingClientRect();
    element.style.setProperty("translate", "0px 0px", "important");
    return rect;
  }

  function align() {
    const stage = document.fullscreenElement;
    if (!stage) {
      restoreAll();
      return;
    }

    const frame = stage.getBoundingClientRect();
    const vw = frame.width || window.innerWidth;
    const vh = frame.height || window.innerHeight;

    // Full-width Firefox fullscreen already works correctly. Correct ONLY
    // the Map + Cinema narrow pane. Don't change normal YouTube playback,
    // tall/portrait videos, or fullscreen on other websites.
    if (vw < 120 || vh < 120 || vw / vh >= 1.15) {
      restoreAll();
      return;
    }

    const video = stage.querySelector("video.html5-main-video, video");
    const aspect = video && video.videoWidth > 0 && video.videoHeight > 0
      ? video.videoWidth / video.videoHeight
      : 16 / 9;
    if (!video || !Number.isFinite(aspect) || aspect < 1.2 || aspect > 4) {
      restoreAll();
      return;
    }

    // A fullscreen video is expected to occupy a centered 16:9-like region
    // inside a narrow, taller pane. Read Gecko's existing video dimensions
    // instead of altering any native Android view bounds.
    const rect = baseRect(video);
    if (rect.width <= 0 || rect.height <= 0) return;
    const visibleHeight = Math.min(vh, vw / aspect);
    const frameCenter = frame.top + vh / 2;
    const videoCenter = rect.top + rect.height / 2;
    let dy = frameCenter - videoCenter;

    // If Gecko already centered its video, do not move it. In particular,
    // some players use a video element sized to the full pane with internal
    // object-fit letterboxing; shifting that entire element would be wrong.
    if (rect.height > visibleHeight * 1.35) {
      restoreAll();
      return;
    }
    // Avoid moving off-pane overlays when the site is in an unusual state.
    if (Math.abs(dy) > vh * 0.45) {
      restoreAll();
      return;
    }
    if (Math.abs(dy) < 2) dy = 0;

    for (const node of altered.keys()) {
      if (node !== video && !node.matches?.(".ytp-caption-window-container")) restore(node);
    }
    rememberAndTranslate(video, dy);

    // YouTube's subtitles are outside the <video> element and are normally
    // aligned to the BOTTOM of the (too-tall) fullscreen viewport. Move the
    // entire caption overlay in the opposite direction so captions stay at
    // the bottom of the newly centered picture instead of far below it.
    const captions = stage.querySelector(".ytp-caption-window-container") ||
      document.querySelector(".ytp-caption-window-container");
    if (captions) {
      rememberAndTranslate(captions, -dy);
    }
  }

  function scheduleAlign() {
    if (pending) return;
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      align();
    });
  }

  document.addEventListener("fullscreenchange", () => {
    scheduleAlign();
    // Gecko/YouTube sometimes settles the fullscreen video dimensions over
    // several frames. Recheck without changing document fullscreen itself.
    setTimeout(scheduleAlign, 150);
    setTimeout(scheduleAlign, 500);
  }, true);
  window.addEventListener("resize", scheduleAlign);

  // YouTube updates captions and video layout as playback proceeds.
  // This low-frequency check also restores the original styles on exit.
  setInterval(scheduleAlign, 700);
  scheduleAlign();
})();
