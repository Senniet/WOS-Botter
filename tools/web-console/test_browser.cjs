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
  assert.match(await page.locator('.nav-btn[data-page="tasks"]').innerText(),/Task Manager/);
  await page.locator('.nav-btn[data-page="tasks"]').click();
  await page.locator('.work-task').first().waitFor({timeout:12000});
  const count=await page.locator('.work-task').count();
  assert.equal(count,65,'All routines visible');
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
  await heroEnable.check();
  await page.waitForTimeout(300);
  assert.ok(toggles.some(w=>w.action==='toggle'&&w.task==='HERO_RECRUITMENT'&&w.enabled===true));
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
    dashboard:'Operations Overview',custom:'Saved Custom Tasks',emulator:'Live View',
    statistics:'Execution Statistics',profiles:'Existing Profiles',settings:'Account Security',
    logs:'Live Engine Logs',qa:'Task Coach & QA Review'
  };
  for(const [route,label] of Object.entries(pageLabels)){
    await page.locator('.nav-btn[data-page="'+route+'"]').click();
    await page.locator('.page.active').getByText(label,{exact:false}).first().waitFor();
    if(route==='emulator'){
      const view=await page.locator('.emu-live-container').boundingBox();
      assert.ok(view && view.y<175, 'Live emulator view should start high on the desktop, actual y='+view?.y);
      assert.ok(await page.evaluate(()=>document.body.classList.contains('is-emulator-page')));
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
  await page.locator('[data-open-task="HERO_RECRUITMENT"]').first().click();
  assert.equal(await page.locator('.operations-tasks.has-task-detail').count(),1);
  const bounds=await page.locator('#task-detail').boundingBox();
  assert.ok(bounds&&bounds.width>300&&bounds.width<=390,'Mobile inspector covers screen');
  await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-redesign-mobile.png'),fullPage:false});
  await page.locator('[data-close-task]').click();
  assert.equal(await page.locator('.operations-tasks.has-task-detail').count(),0);
  await page.locator('#mobile-menu').click();
  await page.locator('.nav-btn[data-page="emulator"]').click();
  const mobileView=await page.locator('.emu-live-container').boundingBox();
  assert.ok(mobileView && mobileView.y<150,'Live emulator view should start high on mobile, actual y='+mobileView?.y);
  await page.screenshot({path:path.join(os.homedir(),'.cache/survivalops-emulator-compact-mobile.png'),fullPage:false});
  const distinct=errs.filter(e=>!e.includes('ERR_CERT_AUTHORITY_INVALID')&&!e.includes('Failed to load resource: the server responded with a status of 401'));
  assert.equal(distinct.length,0,'No JavaScript or console errors: '+distinct.join('; '));
  console.log('Browser PASS: 65 tasks, categories, detail panel, settings, search, favorites, desktop/mobile overflow, close panel.');
  console.log('Screenshots saved locally in ~/.cache/survivalops-redesign-*.png');
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});
