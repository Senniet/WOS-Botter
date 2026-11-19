/*
 * iPhone/iPad Serve-Emu player. The native scrcpy player requires worker
 * OffscreenCanvas + WebCodecs; iOS Safari does not reliably render that
 * third-party, separately certified iframe. Use only the authenticated,
 * same-origin screenshot + guarded input endpoints already present in the
 * web console. Other browsers retain the native low-latency WebSocket player.
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
  let scheduled = null;
  let inflight = false;
  let firstFrame = false;
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
  function plan(delay) {
    clearTimeout(scheduled);
    scheduled = null;
    if (active()) scheduled = setTimeout(refresh, delay);
  }
  function refresh() {
    if (!active() || inflight) return;
    inflight = true;
    // Every successful load schedules the next image, so slow screenshots
    // cannot pile up concurrent ADB processes or stall the web UI.
    frame.onload = () => {
      inflight = false;
      firstFrame = true;
      setMessage('');
      plan(1300);
    };
    frame.onerror = () => {
      inflight = false;
      setMessage(firstFrame ? 'Connection interrupted · retrying…'
        : 'Connecting to emulator · retrying…');
      plan(2500);
    };
    frame.src = '/api/emulator/frame?mobile=' + Date.now();
  }
  function sync() {
    if (!active()) {
      clearTimeout(scheduled);
      scheduled = null;
      return;
    }
    if (!inflight && !scheduled) refresh();
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
    if (!active() || !firstFrame || busy() || !event.isPrimary) return;
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
        // Show the result promptly after the single confirmed input.
        if (!inflight) plan(250);
      }
    } catch (_) {setMessage('Input unavailable · check connection');}
  });
  sync();
})();
