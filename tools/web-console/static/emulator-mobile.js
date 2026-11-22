/*
 * One same-origin continuous Serve-Emu video player on desktop and mobile.
 * The native cross-origin iframe may fail behind another self-signed TLS
 * certificate (especially on mobile and Edge). Use the working 18fps H.264
 * -> MJPEG streaming gateway for both without ADB polling or page refresh.
 * Keep existing iOS tap/swipe and connection cleanup behavior intact.
 */
(() => {
  'use strict';
  const phone = document.getElementById('emu-phone');
  const native = document.getElementById('emu-live');
  const view = document.getElementById('emu-mobile-view');
  const frame = document.getElementById('emu-mobile-frame');
  const message = document.getElementById('emu-mobile-message');
  if (!phone || !native || !view || !frame || !message) return;

  // Never load the cross-origin iframe. Reuse the same trusted HTTPS origin
  // for desktop and mobile so a second browser certificate is not needed.
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
    return emulatorManualState().blocked;
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
