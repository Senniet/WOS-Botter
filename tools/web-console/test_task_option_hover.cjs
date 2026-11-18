/* Regression: technical Java config names must never leak into Task Manager hover UI.
 * Safe for a running bot: settings/toggle writes are mocked inside Chromium. */
const {chromium}=require('playwright-core');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const assert=require('node:assert/strict');

(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_HEADLESS_SHELL,args:['--no-sandbox']});
  try{
    const page=await browser.newPage({viewport:{width:1460,height:940},ignoreHTTPSErrors:true});
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(process.env.SURVIVALOPS_WEB_BASE||'https://192.168.1.20:9098/',{waitUntil:'domcontentloaded'});
    if(await page.locator('#login-form').count()){
      const password=fs.readFileSync(path.join(os.homedir(),'.config/survivalops-web/initial-password.txt'),'utf8').trim();
      await page.locator('#password').fill(password);
      await page.locator('#login-form button').click();
    }
    await page.waitForFunction(()=>typeof state!=='undefined' && state.tasks.length>0,{timeout:15000});
    await page.locator('[data-page="tasks"]').click();
    await page.locator('[data-task-row]').first().waitFor({timeout:15000});
    const audit=await page.evaluate(()=>{
      let checked=0, optionCount=0;
      const issues=[];
      const options=[];
      for(const t of state.tasks){
        state.selectedTaskId=t.id;
        renderTaskDetail();
        const root=document.querySelector('#task-detail-content');
        const keyNodes=root.querySelectorAll('.task-option-key,[title],[data-tooltip]');
        if(keyNodes.length)issues.push(t.id+': leaked hover attributes/keys');
        const text=root.innerText;
        if(/\b(Boolean|Integer|Long|String|Technical key)\b/.test(text))issues.push(t.id+': leaked technical types');
        const actual=taskBehavior(t);
        optionCount+=actual.length;
        const elements=root.querySelectorAll('button,input,select');
        for(const element of elements){
          if(element.title||element.getAttribute('data-tooltip'))issues.push(t.id+': control has a hover popup');
        }
        checked++;
        options.push(...actual.map(x=>({owner:t.id,key:x.key,type:x.type})));
      }
      state.selectedTaskId='HERO_RECRUITMENT';renderTaskDetail();renderTasks();
      return {checked,optionCount,issues,options};
    });
    assert.equal(audit.checked,67);
    assert.ok(audit.optionCount>150);
    assert.deepEqual(audit.issues,[]);
    assert.equal(await page.locator('.operations-tasks [title]').count(),0,'No native Task Manager tooltips on controls');
    assert.equal(await page.locator('.operations-tasks .task-option-key').count(),0);
    // The actual hover reproducer: it must NOT reveal a Boolean/config key underneath the label.
    const toggle=page.locator('.work-inspector .toggle-option').first();
    const before=(await toggle.innerText()).trim();
    await toggle.hover();
    await page.waitForTimeout(1350);
    const after=(await toggle.innerText()).trim();
    assert.equal(after,before,'Hover must not change option label');
    assert.doesNotMatch(after,/_BOOL\b|\bBoolean\b|Technical key/);
    // Numeric/text settings also remain clean and save using their real Java key.
    await page.evaluate(()=>{state.selectedTaskId='ENDLESS_TRIAL';renderTaskDetail()});
    const numeric=page.locator('.work-inspector .value-option').first();
    assert.ok(await numeric.count());
    await numeric.hover();
    await page.waitForTimeout(200);
    assert.equal(await numeric.locator('.task-option-key').count(),0);
    const originalValue=await numeric.locator('input').inputValue();
    const posts=[];
    await page.route('**/api/setting',route=>{posts.push(route.request().postDataJSON());return route.fulfill({status:200,contentType:'application/json',body:'{"ok":true}'})});
    await numeric.locator('input').fill(originalValue);
    await numeric.locator('[data-save-task-value]').click();
    await page.waitForTimeout(150);
    assert.equal(posts.length,1,'Save should fire once and only once');
    assert.equal(posts[0].key,'ENDLESS_TRIAL_TARGET_ATTACKS_INT');
    assert.equal(posts[0].value,originalValue);
    await page.unroute('**/api/setting');
    // Other task controls keep accessible names and their existing functions.
    await page.evaluate(()=>{state.selectedTaskId='HERO_RECRUITMENT';renderTaskDetail();renderTasks()});
    assert.equal(await page.locator('[data-open-task="HERO_RECRUITMENT"]').count(),2);
    assert.ok(await page.locator('[data-star-task="HERO_RECRUITMENT"]').getAttribute('aria-label'));
    assert.ok(await page.locator('[data-task-toggle="HERO_RECRUITMENT"]').first().getAttribute('aria-label'));
    assert.equal(await page.locator('.operations-tasks [title]').count(),0);
    assert.deepEqual(errors,[]);
    // Mobile check: technical labels must stay absent without hover.
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.locator('.operations-tasks .task-option-key').count(),0);
    assert.equal(await page.locator('.operations-tasks [title]').count(),0);
    console.log(`HOVER AUDIT PASSED: ${audit.checked} tasks, ${audit.optionCount} options, no key/type popup; Save fires once, desktop and mobile clean.`);
  }finally{await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});
