/* Read-only mobile emulator regression. Tap/swipe requests are mocked in the
 * browser, so this test NEVER issues input to the real Serve-Emu device. */
const {chromium} = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
(async () => {
  const browser = await chromium.launch({headless:true,
    executablePath:process.env.CHROME_HEADLESS_SHELL||undefined,
    args:['--no-sandbox']});
  try {
    const context = await browser.newContext({ignoreHTTPSErrors:true,
      viewport:{width:390,height:844},deviceScaleFactor:3,isMobile:true,
      hasTouch:true,userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1'});
    const page = await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(String(e)));
    const origin=process.env.SURVIVALOPS_WEB_BASE||'https://10.0.0.100:9098/';
    await page.goto(origin,{waitUntil:'domcontentloaded'});
    if(await page.locator('#login-form').count()){
      const secret=fs.readFileSync(path.join(os.homedir(),'.config/survivalops-web/initial-password.txt'),'utf8').trim();
      await page.locator('#password').fill(secret);
      await page.locator('#login-form button').click();
    }
    await page.locator('.nav-btn[data-page="emulator"]').waitFor({timeout:16000});
    await page.locator('#mobile-menu').click();
    await page.locator('.nav-btn[data-page="emulator"]').click();
    await page.locator('#emu-mobile-view').waitFor({state:'visible',timeout:10000});
    assert.ok(await page.locator('#emu-phone').evaluate(el=>el.classList.contains('emu-mobile-fallback')));
    assert.equal(await page.locator('#emu-live').getAttribute('src'),null,
      'iPhone does not load unsupported third-party certificate iframe');
    await page.waitForFunction(() => {
      const f=document.getElementById('emu-mobile-frame');
      return f.complete && f.naturalWidth === 720 && f.naturalHeight === 1280;
    },{timeout:20000});
    const img=page.locator('#emu-mobile-frame');
    const box=await img.boundingBox();
    assert.ok(box && box.width>170 && box.height>350,
      'portrait game screen fills useful phone viewport: '+JSON.stringify(box));
    assert.equal(await page.locator('#emu-mobile-message').isVisible(),false,
      'successful screenshot hides connecting/error message');
    await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-iphone-emulator-fix.png')});

    // The actual scheduler keeps running during this browser-only test; use
    // an intercepted stopped-state response only in the test page.
    await page.route('**/api/status',route=>route.fulfill({status:200,
      contentType:'application/json',body:JSON.stringify({running:false,executing:false,online:true,queues:[]})}));
    await page.route('**/api/emulator/state',route=>route.fulfill({status:200,
      contentType:'application/json',body:JSON.stringify({connected:true,externalControllerLikelyActive:false})}));
    const requests=[];
    await page.route('**/api/emulator/input',route=>{
      requests.push(route.request().postDataJSON());
      return route.fulfill({status:200,contentType:'application/json',body:'{"ok":true}'});
    });
    // Safety: the bot is normally active, so the overlay MUST block touches.
    await page.evaluate(()=>{state.status={...state.status,running:true,executing:false};
      state.emulator={...state.emulator,externalControllerLikelyActive:false};
      updateEmulatorInputSafety()});
    assert.equal(await page.locator('#emu-input-shield').isVisible(),true);
    const center={x:box.x+box.width/2,y:box.y+box.height/2};
    await page.mouse.click(center.x,center.y);
    assert.equal(requests.length,0,'Touches are blocked while scheduler is active');

    // Simulated paused state: intercept POSTs before they can touch the game.
    await page.evaluate(()=>{state.status={...state.status,running:false,executing:false};
      updateEmulatorInputSafety()});
    assert.equal(await page.locator('#emu-input-shield').isVisible(),false);
    const liveBox=await img.boundingBox();
    const liveCenter={x:liveBox.x+liveBox.width/2,y:liveBox.y+liveBox.height/2};
    await page.mouse.click(liveCenter.x,liveCenter.y);
    await page.waitForTimeout(150);
    assert.equal(requests.length,1);
    assert.equal(requests[0].action,'tap');
    assert.ok(Math.abs(requests[0].x-360)<=2 && Math.abs(requests[0].y-640)<=2,
      'tap maps full 720×1280 device, got '+JSON.stringify(requests[0]));
    await page.evaluate(()=>{state.status={...state.status,running:false,executing:false};
      state.emulator={...state.emulator,externalControllerLikelyActive:false};
      updateEmulatorInputSafety()});
    assert.equal(await page.locator('#emu-input-shield').isVisible(),false);
    await page.mouse.move(liveCenter.x,liveCenter.y-90);
    await page.mouse.down();
    await page.mouse.move(liveCenter.x+45,liveCenter.y+115,{steps:8});
    await page.mouse.up();
    await page.waitForTimeout(200); // Allow async mocked POST to reach the test route.
    assert.equal(requests.length,2);
    assert.equal(requests[1].action,'swipe');
    assert.ok(requests[1].toX>requests[1].x&&requests[1].toY>requests[1].y);
    await page.unroute('**/api/emulator/input');
    assert.deepEqual(errors,[], 'Mobile browser script errors');
    console.log('MOBILE EMULATOR PASS: 720x1280 actual image, no third-party iframe, bot shield, touch tap/swipe coordinate mapping; native game input untouched.');
    console.log('Screenshot ~/.cache/survivalops-iphone-emulator-fix.png');
  } finally {await browser.close();}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});
