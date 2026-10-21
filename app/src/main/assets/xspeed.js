/**
 * X倍速 — long-press on playing video → 2x; release → 1x.
 * Uses a hold timer (~200ms) so scroll / tap / double-tap stay intact.
 */
(function () {
  if (window.__xspeedInjected) return;
  window.__xspeedInjected = true;

  var HOLD_MS = 200;
  var RATE_FAST = 2;
  var RATE_NORMAL = 1;

  var overlay = null;
  var holdTimer = null;
  var activeVideo = null;
  var startX = 0;
  var startY = 0;
  var moved = false;
  var MOVE_TOLERANCE = 12;

  function ensureOverlay() {
    if (overlay && document.body.contains(overlay)) return overlay;
    overlay = document.createElement('div');
    overlay.id = 'xspeed-overlay';
    overlay.textContent = '2x';
    overlay.setAttribute('aria-hidden', 'true');
    Object.assign(overlay.style, {
      position: 'fixed',
      top: '50%',
      left: '50%',
      transform: 'translate(-50%, -50%)',
      zIndex: '2147483647',
      background: 'rgba(0,0,0,0.72)',
      color: '#fff',
      fontSize: '28px',
      fontWeight: '700',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
      padding: '10px 18px',
      borderRadius: '10px',
      pointerEvents: 'none',
      letterSpacing: '1px',
      display: 'none',
      boxShadow: '0 2px 12px rgba(0,0,0,0.45)'
    });
    (document.body || document.documentElement).appendChild(overlay);
    return overlay;
  }

  function showOverlay() {
    var el = ensureOverlay();
    el.style.display = 'block';
  }

  function hideOverlay() {
    if (overlay) overlay.style.display = 'none';
  }

  function findVideoFromTarget(target) {
    if (!target || !target.closest) return null;
    // Direct video
    if (target.tagName === 'VIDEO') return target;
    // Ancestor video
    var v = target.closest('video');
    if (v) return v;
    // Container that wraps a video (X players often use nested divs)
    var container = target.closest('[data-testid="videoPlayer"], [data-testid="videoComponent"], [aria-label*="视频"], [aria-label*="Video"], article');
    if (container) {
      var inner = container.querySelector('video');
      if (inner) return inner;
    }
    // Walk up a few levels looking for a sibling/descendant video
    var node = target;
    for (var i = 0; i < 8 && node; i++) {
      if (node.querySelector) {
        var found = node.querySelector('video');
        if (found) return found;
      }
      node = node.parentElement;
    }
    return null;
  }

  function collectVideos(root) {
    var list = [];
    try {
      var vids = (root || document).querySelectorAll('video');
      for (var i = 0; i < vids.length; i++) list.push(vids[i]);
    } catch (e) {}
    // Shadow DOM walk (limited depth)
    try {
      var all = (root || document).querySelectorAll('*');
      for (var j = 0; j < all.length; j++) {
        if (all[j].shadowRoot) {
          var nested = all[j].shadowRoot.querySelectorAll('video');
          for (var k = 0; k < nested.length; k++) list.push(nested[k]);
        }
      }
    } catch (e2) {}
    return list;
  }

  function setRate(video, rate) {
    if (!video) return;
    try {
      video.playbackRate = rate;
    } catch (e) {}
  }

  function clearHold() {
    if (holdTimer) {
      clearTimeout(holdTimer);
      holdTimer = null;
    }
  }

  function endSpeed() {
    clearHold();
    if (activeVideo) {
      setRate(activeVideo, RATE_NORMAL);
      activeVideo = null;
    }
    hideOverlay();
  }

  function onTouchStart(e) {
    if (!e.touches || e.touches.length !== 1) return;
    var t = e.touches[0];
    startX = t.clientX;
    startY = t.clientY;
    moved = false;
    clearHold();

    var video = findVideoFromTarget(e.target);
    if (!video) return;
    // Prefer currently playing / recently played
    if (video.paused && video.readyState < 2) {
      // still allow — user may hold while buffering
    }

    holdTimer = setTimeout(function () {
      holdTimer = null;
      if (moved) return;
      activeVideo = video;
      setRate(video, RATE_FAST);
      showOverlay();
    }, HOLD_MS);
  }

  function onTouchMove(e) {
    if (!e.touches || e.touches.length !== 1) return;
    var t = e.touches[0];
    var dx = Math.abs(t.clientX - startX);
    var dy = Math.abs(t.clientY - startY);
    if (dx > MOVE_TOLERANCE || dy > MOVE_TOLERANCE) {
      moved = true;
      // Cancel pending hold (user is scrolling)
      if (holdTimer) {
        clearHold();
      }
      // If already in 2x and user scrolls away, restore
      if (activeVideo) {
        endSpeed();
      }
    }
  }

  function onTouchEnd() {
    endSpeed();
  }

  function onTouchCancel() {
    endSpeed();
  }

  // Capture phase so we see events before X's handlers; we do NOT call preventDefault
  // so scroll / tap / double-tap keep working.
  document.addEventListener('touchstart', onTouchStart, { capture: true, passive: true });
  document.addEventListener('touchmove', onTouchMove, { capture: true, passive: true });
  document.addEventListener('touchend', onTouchEnd, { capture: true, passive: true });
  document.addEventListener('touchcancel', onTouchCancel, { capture: true, passive: true });

  // Also bind directly on newly appearing videos (extra safety)
  function bindVideo(video) {
    if (!video || video.__xspeedBound) return;
    video.__xspeedBound = true;
    video.addEventListener('touchstart', onTouchStart, { passive: true });
    video.addEventListener('touchend', onTouchEnd, { passive: true });
    video.addEventListener('touchcancel', onTouchCancel, { passive: true });
  }

  function scan() {
    collectVideos(document).forEach(bindVideo);
  }

  scan();

  var mo = new MutationObserver(function () {
    scan();
    // Re-ensure overlay if body was replaced by SPA nav
    if (overlay && !document.body.contains(overlay)) {
      overlay = null;
    }
  });
  mo.observe(document.documentElement || document.body, {
    childList: true,
    subtree: true
  });

  // SPA pushState / popstate — re-scan shortly after navigation
  var _push = history.pushState;
  history.pushState = function () {
    var r = _push.apply(this, arguments);
    setTimeout(scan, 300);
    return r;
  };
  window.addEventListener('popstate', function () {
    setTimeout(scan, 300);
  });

  console.log('[XSpeed] long-press 2x injected');
})();
