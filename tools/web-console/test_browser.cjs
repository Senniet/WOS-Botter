/* Browser smoke: real authenticated UI, read-only navigation. Requires playwright-core.
 * Login password is read from the local private file, never printed or stored in Git. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_HEADLESS_SHELL||undefined,args:['--no-sandbox']});
 const errs=[];
 try{
  const ctx=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width:1460,height:940}});
  const page=await ctx.newPage();
  page.on('pageerror',e=>errs.push(String(e)));
  page.on('console',msg=>{if(msg.type()==='error')errs.push('console: '+msg.text())});
  await page.goto(process.env.SURVIVALOPS_WEB_BASE||'https://10.0.0.100:9098/',{waitUntil:'domcontentloaded'});
  assert.equal(await page.locator('html').getAttribute('lang'),'en','Login page must use English');
  if(await page.locator('#login-form').count())assert.equal((await page.locator('#login-form label').innerText()).trim(),'Password');
  const password=fs.readFileSync(path.join(os.homedir(),'.config/survivalops-web/initial-password.txt'),'utf8').trim();
  if(await page.locator('#login-form').count()){
    await page.locator('#password').fill(password);
    await page.locator('#login-form button').click();
  }
  await page.locator('.nav-btn[data-page="tasks"]').waitFor({timeout:15000});
  // The post-login navigation can settle after the sidebar is rendered.
  // Wait until the deferred app script and task API have initialized.
  await page.waitForFunction(()=>typeof state!=='undefined' && state.tasks.length>0,{timeout:15000});
  assert.match(await page.locator('.nav-btn[data-page="tasks"]').innerText(),/Task Manager/);
  await page.locator('.nav-btn[data-page="tasks"]').click();
  await page.locator('.work-task').first().waitFor({timeout:12000});
  const count=await page.locator('.work-task').count();
  assert.equal(count,69,'All routines visible');
  // Regression: the long All Tasks list must not enlarge the *document*
  // past the actual page content. It is scrollable inside its own panel.
  // Without paint containment Chromium reports ~4,428px document height
  // even though the real content is only ~1,175px, creating blank space.
  const allScroll=await page.evaluate(()=>({
    documentHeight:document.documentElement.scrollHeight,
    bodyHeight:document.body.scrollHeight,
    listHeight:document.querySelector('#task-list').scrollHeight,
    listViewport:document.querySelector('#task-list').clientHeight
  }));
  assert.ok(allScroll.documentHeight<=allScroll.bodyHeight+4,
    'All Tasks must not add phantom document scroll: '+JSON.stringify(allScroll));
  assert.ok(allScroll.listHeight>allScroll.listViewport+600,
    'All Tasks must remain independently scrollable');
  await page.locator('#task-list').evaluate(element=>{element.scrollTop=element.scrollHeight;});
  assert.ok(await page.locator('#task-list').evaluate(element=>element.scrollTop)>300,
    'The 69 tasks must still be reachable with the internal scrollbar');
  await page.locator('#task-list').evaluate(element=>{element.scrollTop=0;});
  assert.equal(await page.locator('.work-group').count(),10,'All areas + all menu');
  assert.equal((await page.locator('.work-heading h2').innerText()).trim(),'My Routines');
  assert.match(await page.locator('#task-categories').innerText(),/Daily Tasks/);
  assert.doesNotMatch(await page.locator('.operations-tasks').innerText(),/\b(?:ingeschakeld|uitgeschakeld|instellingen|bekijk|zoeken|overzicht|verversen|profielen|wachtrij|beloningen|troepen|geclaimd)\b/i);
  await page.locator('[data-open-task="HERO_RECRUITMENT"]').first().click();
  await page.locator('#task-detail-content').getByText('Claim Recruitment Points Chest').waitFor();
  assert.ok((await page.locator('#task-detail-content [data-task-option]').count())>=5);
  // Intercept writes: prove that switches use the original API keys, without touching the live bot.
  const writes=[];
  await page.route('**/api/setting',route=>{writes.push(route.request().postDataJSON());return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({ok:true})});});
  const heroPoints=page.locator('#task-detail-content [data-task-option="HERO_RECRUIT_POINTS_CHEST_BOOL"]');
  assert.equal(await heroPoints.isChecked(),true);
  await page.locator("#task-detail-content label.toggle-option:has(input[data-task-option=\"HERO_RECRUIT_POINTS_CHEST_BOOL\"])").click();
  await page.waitForTimeout(180);
  assert.equal(await heroPoints.isChecked(),false);
  assert.equal(writes.length,1);
  assert.equal(writes[0].key,'HERO_RECRUIT_POINTS_CHEST_BOOL');
  assert.equal(writes[0].value,'false');
  assert.equal(String(writes[0].profileId),'1');
  await page.unroute('**/api/setting');
  const toggles=[];
  await page.route('**/api/task',route=>{toggles.push(route.request().postDataJSON());return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({ok:true})});});
  const heroEnable=page.locator('#task-detail-content [data-task-toggle="HERO_RECRUITMENT"]');
  const oppositeState=!(await heroEnable.isChecked());
  if(oppositeState) await heroEnable.check(); else await heroEnable.uncheck();
  await page.waitForTimeout(300);
  assert.ok(toggles.some(w=>w.action==='toggle'&&w.task==='HERO_RECRUITMENT'&&w.enabled===oppositeState));
  await page.unroute('**/api/task');
  // Refresh from real API to restore untouched production data in the browser.
  await page.evaluate(()=>loadTasks());

  const widths=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,client:document.documentElement.clientWidth,layout:getComputedStyle(document.querySelector('.work-layout')).gridTemplateColumns}));
  assert.ok(widths.scroll<=widths.client+4,'No horizontal overflow desktop '+JSON.stringify(widths));
  await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-redesign-desktop.png'),fullPage:false});
  await page.locator('[data-task-group="army"]').click();
  assert.equal(await page.locator('.work-task').count(),2,'Troops group only contains training and healing');
  await page.locator('[data-task-group="all"]').click();
  await page.locator('#tasks-search').fill('recruitment');
  assert.ok(await page.locator('.work-task').count()>=1,'Search includes recruitment');
  await page.locator('#tasks-search').fill('');
  await page.locator('[data-star-task="HERO_RECRUITMENT"]').click();
  await page.locator('[data-task-filter="favorites"]').click();
  assert.equal(await page.locator('.work-task').count(),1,'Favorites filter');
  await page.locator('[data-star-task="HERO_RECRUITMENT"]').click();
  await page.locator('[data-task-filter="all"]').click();
  const pageLabels={
    dashboard:'Operations Overview',custom:'Saved Custom Tasks',emulator:'Live emulator',
    statistics:'Execution Statistics',profiles:'Existing Profiles',settings:'Account Security',
    logs:'Live Engine Logs',qa:'Task Coach & QA Review'
  };
  for(const [route,label] of Object.entries(pageLabels)){
    await page.locator('.nav-btn[data-page="'+route+'"]').click();
    await page.locator('.page.active').getByText(label,{exact:false}).first().waitFor();
    if(route==='emulator'){
      const view=await page.locator('.emu-phone').boundingBox();
      assert.ok(view && view.y<190, 'Live emulator view should start high on the desktop, actual y='+view?.y);
      assert.ok(await page.evaluate(()=>document.body.classList.contains('is-emulator-page')));
      const iframe=page.locator('#emu-live');
      assert.match(await iframe.getAttribute('src'),/\/embed\.html$/,'Must use the native compact interactive player');
      if(await page.locator('#emu-input-shield').isVisible()) assert.notEqual(await iframe.getAttribute('inert'),null,'The active bot must shield native emulator input');
      else assert.equal(await iframe.getAttribute('inert'),null,'Manual input is enabled when bot is idle');
      assert.equal(await page.locator('.emu-secondary').count(),0,'No duplicate screenshot or settings area');
      assert.equal(await page.locator('.emu-external-link').count(),1,'Full Serve-Emu link in the toolbar');
      await page.frameLocator('#emu-live').locator('.device canvas').waitFor({timeout:14000});
      const nativeSidebar=await page.frameLocator('#emu-live').locator('.device-sidebar').evaluate(el=>getComputedStyle(el).display);
      assert.equal(nativeSidebar,'none','Native device sidebar must be hidden in compact player');
      const player=page.frameLocator('#emu-live').locator('.device canvas');
      await page.waitForTimeout(1800); // Allow the native OffscreenCanvas first frames to arrive.
      const canvasBox=await player.boundingBox();
      assert.ok(canvasBox && canvasBox.height>400 && canvasBox.width>200,
        'The native interactive canvas must fill the portrait player, not be a tiny preview');
      const nativePanels=await page.frameLocator('#emu-live').locator('.side-panel').evaluate(el=>getComputedStyle(el).display);
      assert.equal(nativePanels,'none','Native settings/tools panel must be hidden');
      // Simulate the web scheduler going active without starting any actual bot tasks.
      await page.evaluate(()=>{state.status={...state.status,running:true};updateEmulatorInputSafety()});
      assert.ok(await page.locator('#emu-input-shield').isVisible(),'Controls are shielded while bot runs');
      assert.equal(await iframe.getAttribute('tabindex'),'-1');
      await page.evaluate(()=>{state.status={...state.status,running:false,executing:false};updateEmulatorInputSafety()});
      assert.equal(await page.locator('#emu-input-shield').isVisible(),false,'Controls restore on stop');
      assert.equal(await iframe.getAttribute('tabindex'),'0');
      await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-emulator-compact-desktop.png'),fullPage:false});
    }
  }
  await page.locator('.nav-btn[data-page="tasks"]').click();
  await page.evaluate(()=>showScheduleDialog('HERO_RECRUITMENT'));
  assert.equal(await page.locator('#schedule-dialog h3').innerText(),'Schedule Routine');
  await page.locator('#schedule-cancel').click();
  await page.locator('.nav-btn[data-page="dashboard"]').click();
  await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-redesign-dashboard.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.locator('#mobile-menu').click();
  await page.locator('.nav-btn[data-page="tasks"]').click();
  await page.locator('.work-task').first().waitFor();
  const mobile=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,client:document.documentElement.clientWidth}));
  assert.ok(mobile.scroll<=mobile.client+4,'No horizontal overflow mobile '+JSON.stringify(mobile));
  const mobileScroll=await page.evaluate(()=>({
    documentHeight:document.documentElement.scrollHeight,
    bodyHeight:document.body.scrollHeight,
    rows:document.querySelectorAll('.work-task').length
  }));
  assert.ok(mobileScroll.documentHeight<=mobileScroll.bodyHeight+4,
    'Mobile must keep all tasks in normal page flow: '+JSON.stringify(mobileScroll));
  assert.equal(mobileScroll.rows,69,'Mobile All Tasks still shows the full routine list');
  await page.locator('[data-open-task="HERO_RECRUITMENT"]').first().click();
  assert.equal(await page.locator('.operations-tasks.has-task-detail').count(),1);
  const bounds=await page.locator('#task-detail').boundingBox();
  assert.ok(bounds&&bounds.width>300&&bounds.width<=390,'Mobile inspector covers screen');
  await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-redesign-mobile.png'),fullPage:false});
  await page.locator('[data-close-task]').click();
  assert.equal(await page.locator('.operations-tasks.has-task-detail').count(),0);
  await page.locator('#mobile-menu').click();
  await page.locator('.nav-btn[data-page="emulator"]').click();
  const mobileView=await page.locator('.emu-phone').boundingBox();
  assert.ok(mobileView && mobileView.y<160,'Live emulator view should start high on mobile, actual y='+mobileView?.y);
  assert.equal(await page.locator('.emu-secondary').count(),0);
  await page.frameLocator('#emu-live').locator('.device canvas').waitFor({timeout:14000});
  await page.waitForTimeout(500); // allow the collapsing mobile navigation transition to finish
  await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-emulator-compact-mobile.png'),fullPage:false});
  const distinct=errs.filter(e=>!e.includes('ERR_CERT_AUTHORITY_INVALID')&&!e.includes('Failed to load resource: the server responded with a status of 401'));
  assert.equal(distinct.length,0,'No JavaScript or console errors: '+distinct.join('; '));
  console.log('Browser PASS: 69 tasks, categories, detail panel, settings, search, favorites, desktop/mobile overflow, close panel.');
  console.log('Screenshots saved locally in ~/.cache/survivalops-redesign-*.png');
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});
