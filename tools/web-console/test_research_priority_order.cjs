/* Live-browser UI verification with mocked writes; does not touch real config. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_HEADLESS_SHELL,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({ignoreHTTPSErrors:true,viewport:{width:1440,height:900}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(process.env.SURVIVALOPS_WEB_BASE||'https://192.168.1.20:9098/',{waitUntil:'domcontentloaded'});
  if(await page.locator('#login-form').count()){
   await page.locator('#password').fill(fs.readFileSync(path.join(os.homedir(),'.config/survivalops-web/initial-password.txt'),'utf8').trim());
   await page.locator('#login-form button').click();
  }
  await page.locator('.nav-btn[data-page="tasks"]').waitFor({timeout:18000});
  await page.waitForFunction(()=>typeof state!=='undefined'&&state.tasks.length===69);
  await page.locator('.nav-btn[data-page="tasks"]').click();
  await page.locator('[data-open-task="RESEARCH"]').first().click();
  // Finish the post-login Task Manager GET before injecting the mock order.
  // Otherwise a pending real settings response can replace the synthetic
  // baseline and make the first click a no-op on a heavily loaded server.
  await page.waitForLoadState('networkidle');
  const settings=await page.evaluate(()=>state.taskSettings.map(x=>({...x})));
  const target=settings.find(x=>x.key==='RESEARCH_PRIORITIES_STRING');
  // Use a synthetic initial order; the user's LIVE order may already have
  // been customized. This test intercepts writes and never changes the API.
  const baseline='growth:Growth:1:true|economy:Economy:2:true|battle:Battle:3:true';
  target.value=baseline;
  await page.evaluate(value=>{
   state.taskSettings.find(s=>s.key==='RESEARCH_PRIORITIES_STRING').value=value;
   renderTaskDetail();
  },baseline);
  const writes=[];
  await page.route('**/api/setting',async route=>{
   const body=route.request().postDataJSON();
   if(body.key==='RESEARCH_PRIORITIES_STRING'){target.value=body.value;writes.push(body.value);}
   await route.fulfill({status:200,contentType:'application/json',body:'{"ok":true}'});
  });
  await page.route('**/api/settings?**',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(settings)}));
  const tags=async()=>page.locator('[data-research-row]').evaluateAll(els=>els.map(e=>e.dataset.researchRow));
  const visible=()=>page.locator('[data-research-priorities="RESEARCH"]');
  assert.deepEqual(await tags(),['growth','economy','battle']);
  assert.equal(await visible().locator('[data-task-value="RESEARCH_PRIORITIES_STRING"]').count(),0,'raw rank string must be hidden');
  await page.getByRole('button',{name:'Move Economy higher'}).click();
  await page.waitForFunction(()=>!researchPrioritySaving && state.taskSettings.find(s=>s.key==='RESEARCH_PRIORITIES_STRING')?.value.startsWith('economy:Economy:1:true'));
  assert.deepEqual(await tags(),['economy','growth','battle']);
  assert.equal(writes.at(-1),'economy:Economy:1:true|growth:Growth:2:true|battle:Battle:3:true');
  await page.getByRole('button',{name:'Move Battle higher'}).click();
  await page.waitForFunction(()=>!researchPrioritySaving);
  assert.deepEqual(await tags(),['economy','battle','growth']);
  await page.locator('[data-research-row="growth"]').dragTo(page.locator('[data-research-row="economy"]'));
  await page.waitForFunction(()=>!researchPrioritySaving);
  assert.deepEqual(await tags(),['growth','economy','battle'],'Drag must reorder the same saved Java priority string');
  await page.locator('[data-research-enabled="battle"]').uncheck();
  await page.waitForFunction(()=>!researchPrioritySaving);
  assert.match(writes.at(-1),/battle:Battle:3:false$/);
  await page.evaluate(()=>loadTasks());
  await page.waitForTimeout(200);
  assert.deepEqual(await tags(),['growth','economy','battle']);
  assert.equal(await page.locator('[data-research-enabled="battle"]').isChecked(),false);
  assert.equal(errors.length,0,errors.join('\n'));
  console.log('RESEARCH ORDER PASS: arrows, drag/drop, category checkbox, persisted serialization, refresh');
  console.log('WRITES',writes.join(' | '));
 }finally{await browser.close();}
})().catch(error=>{console.error(error.stack||error);process.exitCode=1});
