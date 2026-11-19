/*
 * Live iPhone/iPad Serve-Emu player. Native scrcpy requires worker
 * OffscreenCanvas + WebCodecs; iOS Safari cannot reliably render the
 * third-party separately certified iframe. Instead we play an uninterrupted
 * MJPEG stream repackaged from the SAME native scrcpy H.264 WebSocket source.
 * It is not an ADB screenshot loop: one image request continuously receives
 * new frames (18fps) without reloads. Input uses the guarded same-origin API.
 * Other browsers retain the native low-latency WebSocket player.
 */
(() => {
  'use strict';
  const agent = navigator.userAgent || '';
  const ios = /iPad|iPhone|iPod/i.test(agent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const nativeWorkerUnavailable = !window.Worker
    || !HTMLCanvasElement.prototype.transferControlToOffscreen;
  if (!ios && !nativeWorkerUnavailable) return;

  const phone = document.getElementById('emu-phone');
  const native = document.getElementById('emu-live');
  const view = document.getElementById('emu-mobile-view');
  const frame = document.getElementById('emu-mobile-frame');
  const message = document.getElementById('emu-mobile-message');
  if (!phone || !native || !view || !frame || !message) return;

  // Do not load a separate untrusted TLS certificate or an unsupported
  // OffscreenCanvas bundle in mobile Safari. All requests stay on :9098.
  native.removeAttribute('src');
  phone.classList.add('emu-mobile-fallback');
  view.hidden = false;
  let firstFrame = false;
  let streamActive = false;
  let reconnect = null;
  let pointer = null;

  function active() {
    return document.body.classList.contains('is-emulator-page')
      && !document.hidden;
  }
  function busy() {
    return !state.status || state.status.running || state.status.executing
      || state.emulator?.externalControllerLikelyActive;
  }
  function setMessage(text) {
    message.textContent = text;
    message.hidden = !text;
  }
  function openLiveStream() {
    if (!active() || streamActive) return;
    streamActive = true;
    clearTimeout(reconnect);
    reconnect = null;
    // This URL is requested ONCE per open viewing session. The HTTP
    // connection stays open, delivering 18 real frames/second using the same
    // scrcpy feed as the desktop. No timestamp query, interval or reload.
    frame.src = '/api/emulator/live';
  }
  frame.onload = () => {
    firstFrame = true;
    setMessage('');
  };
  frame.onerror = () => {
    streamActive = false;
    if (!active()) return;
    setMessage(firstFrame ? 'Live video disconnected · reconnecting…'
      : 'Connecting to live video · retrying…');
    clearTimeout(reconnect);
    reconnect = setTimeout(() => {reconnect=null;openLiveStream();}, 2500);
  };
  function sync() {
    if (!active()) {
      clearTimeout(reconnect);
      reconnect = null;
      if (streamActive) {
        streamActive = false;
        frame.removeAttribute('src'); // Disconnect video, free FFmpeg/WS.
      }
      return;
    }
    openLiveStream();
  }
  const observer = new MutationObserver(sync);
  observer.observe(document.body, {attributes:true,attributeFilter:['class']});
  document.addEventListener('visibilitychange', sync);

  function coordinates(event) {
    const box = frame.getBoundingClientRect();
    if (!box.width || !box.height) return null;
    const x = (event.clientX - box.left) / box.width;
    const y = (event.clientY - box.top) / box.height;
    if (x < 0 || x > 1 || y < 0 || y > 1) return null;
    return {x: Math.max(0, Math.min(719, Math.round(x * 719))),
      y: Math.max(0, Math.min(1279, Math.round(y * 1279)))};
  }
  frame.addEventListener('pointerdown', event => {
    if (!active() || !frame.naturalWidth || busy() || !event.isPrimary) return;
    const point = coordinates(event);
    if (!point) return;
    event.preventDefault();
    pointer = {id:event.pointerId,point,time:Date.now()};
    try {frame.setPointerCapture(event.pointerId);} catch (_) { /* iOS fallback */ }
  });
  frame.addEventListener('pointercancel', () => {pointer = null;});
  frame.addEventListener('pointerup', async event => {
    if (!pointer || pointer.id !== event.pointerId) return;
    event.preventDefault();
    const start = pointer;
    pointer = null;
    const end = coordinates(event);
    if (!end || busy() || !active()) return;
    const distance = Math.hypot(end.x - start.point.x, end.y - start.point.y);
    const swiping = distance > 20;
    const payload = swiping
      ? {action:'swipe', x:start.point.x, y:start.point.y,
          toX:end.x, toY:end.y,
          duration:Math.max(100,Math.min(2000,Date.now()-start.time))}
      : {action:'tap',x:start.point.x,y:start.point.y};
    try {
      const result = await fetch('/api/emulator/input',{
        method:'POST',credentials:'same-origin',cache:'no-store',
        headers:{'Content-Type':'application/json','X-SurvivalOps-Request':'web'},
        body:JSON.stringify(payload)
      });
      if (result.status === 401) {location.reload(); return;}
      if (!result.ok) {
        const problem = await result.json().catch(() => ({}));
        setMessage(problem.error || 'Manual control unavailable');
      } else {
        // No image refresh needed: the *continuous* video immediately shows
        // the game response to this single confirmed tap/swipe.
      }
    } catch (_) {setMessage('Input unavailable · check connection');}
  });
  sync();
})();
