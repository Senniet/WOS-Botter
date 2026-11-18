/* Authenticated browser regression: mock only task writes, never mutate live settings. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_HEADLESS_SHELL||undefined,args:['--no-sandbox']});
 try {
  const page=await browser.newPage({ignoreHTTPSErrors:true,viewport:{width:1440,height:900}});
  const pageErrors=[];page.on('pageerror',e=>pageErrors.push(String(e)));
  await page.goto(process.env.SURVIVALOPS_WEB_BASE||'https://192.168.1.20:9098/',{waitUntil:'domcontentloaded'});
  if(await page.locator('#login-form').count()){
   const password=fs.readFileSync(path.join(os.homedir(),'.config/survivalops-web/initial-password.txt'),'utf8').trim();
   await page.locator('#password').fill(password);
   await page.locator('#login-form button').click();
  }
  await page.locator('.nav-btn[data-page="tasks"]').waitFor({timeout:16000});
  await page.waitForFunction(()=>typeof state!=='undefined'&&state.tasks.length>=60,{timeout:16000});
  await page.locator('.nav-btn[data-page="tasks"]').click();
  assert.equal(await page.locator('.work-task').count(),69);
  const target='EVENT_MILESTONE_REWARDS';
  const original=await page.locator('[data-task-row="'+target+'"] input[data-task-toggle]').isChecked();
  const writes=[];
  const virtualTasks=await page.evaluate(()=>state.tasks.map(task=>({...task})));
  let failNext=false;
  await page.route('**/api/task',async route=>{
   const data=route.request().postDataJSON();writes.push(data);
   if(failNext){failNext=false;await route.fulfill({status:503,contentType:'application/json',body:'{"error":"Test write refused"}'});return;}
   const model=virtualTasks.find(task=>task.id===data.task);
   if(model&&data.action==='toggle')model.enabled=data.enabled;
   await route.fulfill({status:200,contentType:'application/json',body:'{"ok":true}'});
  });
  let failTasksFetch=false;
  await page.route('**/api/tasks?**',route=>failTasksFetch
    ? route.fulfill({status:503,contentType:'application/json',body:'{"error":"Temporary test API failure"}'})
    : route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(virtualTasks)}));
  // The real GET may still return the untouched actual setting. A successful
  // switch must update the current UI without destroying the existing list.
  const before=await page.evaluate(target=>{
   const list=document.querySelector('#task-list');list.scrollTop=2800;
   const row=document.querySelector('[data-task-row="'+target+'"]');
   window.__toggleRegression={list,row};
   return {scroll:list.scrollTop,rowCount:document.querySelectorAll('.work-task').length};
  },target);
  await page.locator('[data-task-row="'+target+'"] label.work-main-toggle').click();
  await page.waitForFunction(()=>state.taskBusy.size===0,{timeout:10000});
  await page.waitForTimeout(150);
  const after=await page.evaluate(()=>({
   rowConnected:window.__toggleRegression.row.isConnected,
   rowIsSame:window.__toggleRegression.row===document.querySelector('[data-task-row="EVENT_MILESTONE_REWARDS"]'),
   scroll:document.querySelector('#task-list').scrollTop,
   rowCount:document.querySelectorAll('.work-task').length,
   page:state.page,visible:document.querySelector('.operations-tasks').classList.contains('active')
  }));
  console.log('DESKTOP TOGGLE STABILITY',JSON.stringify({before,after}));
  assert.equal(after.rowIsSame,true,'Single task switch must NOT replace the entire list');
  assert.equal(after.rowCount,69,'All Tasks view remains present');
  assert.equal(after.scroll,before.scroll,'Scrollbar does not jump on toggling');
  assert.ok(after.visible);
  assert.ok(writes.some(w=>w.task===target&&w.enabled===!original));
  // A normal background refresh should preserve the very same task row too.
  await page.evaluate(()=>loadTasks());
  await page.waitForTimeout(220);
  assert.ok(await page.evaluate(()=>window.__toggleRegression.row.isConnected),'Periodic refresh must not recreate task rows');
  assert.equal(await page.locator('[data-task-row="'+target+'"] input[data-task-toggle]').isChecked(),!original);
  // Failed server writes must roll back only their own switch, with no blank UI.
  failNext=true;
  await page.locator('[data-task-row="'+target+'"] label.work-main-toggle').click();
  await page.waitForFunction(()=>state.taskBusy.size===0,{timeout:10000});
  assert.equal(await page.locator('[data-task-row="'+target+'"] input[data-task-toggle]').isChecked(),!original);
  assert.ok(await page.evaluate(()=>window.__toggleRegression.row.isConnected));
  // Mobile inspector with many settings and its independent scroll region.
  await page.setViewportSize({width:390,height:844});
  const mobileTarget='HERO_RECRUITMENT';
  await page.locator('[data-open-task="'+mobileTarget+'"]').first().click();
  const inspector=page.locator('#task-detail-content');
  const detailBefore=await inspector.evaluate(el=>{
   el.scrollTop=230;window.__inspectorRef=el.firstElementChild;return {scroll:el.scrollTop,title:el.querySelector('h3')?.textContent};
  });
  await page.locator('#task-detail-content label.work-main-toggle').click();
  await page.waitForFunction(()=>state.taskBusy.size===0,{timeout:10000});
  const detailAfter=await inspector.evaluate(el=>({
   rowConnected:window.__inspectorRef.isConnected,scroll:el.scrollTop,
   title:el.querySelector('h3')?.textContent,
   overlay:document.querySelector('.operations-tasks').classList.contains('has-task-detail')
  }));
  console.log('MOBILE INSPECTOR STABILITY',JSON.stringify({detailBefore,detailAfter}));
  assert.ok(detailAfter.rowConnected,'Toggling in details must not reconstruct inspector');
  assert.ok(detailAfter.overlay,'Mobile settings overlay must stay open');
  assert.equal(detailAfter.scroll,detailBefore.scroll);
  // The periodic request must update metadata while preserving the open panel.
  await page.evaluate(()=>loadTasks());await page.waitForTimeout(250);
  assert.ok(await page.evaluate(()=>window.__inspectorRef.isConnected),'Refresh must not rebuild inspector');
  failTasksFetch=true;
  await page.evaluate(()=>loadTasks());
  assert.ok(await page.evaluate(()=>window.__inspectorRef.isConnected),'Transient API failure must not erase the open inspector');
  assert.equal(await page.locator('.work-task').count(),69,'Transient API failure must retain all routine rows');
  assert.deepEqual(pageErrors,[]);
  console.log('PASS: task toggles preserve list, selection, scroll and inspector on desktop/mobile');
 } finally {await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});
