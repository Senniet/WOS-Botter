/* The Heal Troops batch mode must be editable using the original MSI choices.
 * API writes are intercepted so the live profile and game are never modified. */
const {chromium}=require('playwright-core');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_HEADLESS_SHELL,args:['--no-sandbox']});
 try{
  for(const width of [1460,390]){
   const page=await browser.newPage({viewport:{width,height:width===390?844:940},ignoreHTTPSErrors:true});
   const errors=[];page.on('pageerror',e=>errors.push(String(e)));
   await page.goto(process.env.SURVIVALOPS_WEB_BASE||'https://192.168.1.20:9098/',{waitUntil:'domcontentloaded'});
   if(await page.locator('#login-form').count()){
    const password=fs.readFileSync(path.join(os.homedir(),'.config/survivalops-web/initial-password.txt'),'utf8').trim();
    await page.locator('#password').fill(password);
    await page.locator('#login-form button').click();
   }
   await page.locator('[data-page="tasks"]').waitFor();
   if(width===390)await page.locator('#mobile-menu').click();
   await page.locator('[data-page="tasks"]').click();
   await page.locator('[data-open-task="HEAL_TROOPS"]').first().click();
   const select=page.locator('#task-detail-content select[data-task-value="HEAL_BATCH_MODE_STRING"]');
   await select.waitFor();
   assert.deepEqual(await select.locator('option').allTextContents(),['Full - Quick Select','Small - Current selection']);
   assert.equal(await select.inputValue(),'Full - Quick Select');
   assert.equal(await select.getAttribute('disabled'),null);
   const wrapper=select.locator('xpath=..');
   assert.equal(await wrapper.locator('[data-save-task-value="HEAL_BATCH_MODE_STRING"]').count(),1);
   const posts=[];
   await page.route('**/api/setting',route=>{posts.push(route.request().postDataJSON());return route.fulfill({status:200,contentType:'application/json',body:'{"ok":true}'})});
   await select.selectOption({label:'Small - Current selection'});
   assert.equal(await select.inputValue(),'Small - Current selection');
   await wrapper.locator('[data-save-task-value="HEAL_BATCH_MODE_STRING"]').click();
   await page.waitForTimeout(200);
   assert.equal(posts.length,1,'Save one click -> one API write');
   assert.equal(posts[0].key,'HEAL_BATCH_MODE_STRING');
   assert.equal(posts[0].value,'Small - Current selection');
   assert.equal(String(posts[0].profileId),'1');
   assert.equal(await page.locator('#task-detail-content .task-option-key').count(),0);
   // Now choose the default back and ensure it is also a valid choice.
   await select.selectOption({label:'Full - Quick Select'});
   await wrapper.locator('[data-save-task-value="HEAL_BATCH_MODE_STRING"]').click();
   await page.waitForTimeout(180);
   assert.equal(posts.length,2);
   assert.equal(posts[1].value,'Full - Quick Select');
   await page.unroute('**/api/setting');
   assert.deepEqual(errors,[]);
   console.log('HEAL BATCH SELECT PASS',width,'px: both choices, two single writes, no live profile changes');
   await page.close();
  }
 }finally{await browser.close()}
})().catch(err=>{console.error(err.stack||err);process.exitCode=1});
