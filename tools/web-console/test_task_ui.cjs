/* No browser dependency: test the task -> original MSI configuration mapping against the live Java API. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
(async()=>{
  const [tasks,settings]=await Promise.all([
    fetch('http://127.0.0.1:18765/api/tasks?profile=1').then(r=>r.json()),
    fetch('http://127.0.0.1:18765/api/settings?profile=1').then(r=>r.json())
  ]);
  const ui={
    'tasks-search':{value:''},'tasks-category':{value:''},'tasks-enabled':{checked:false},
    'task-list':{innerHTML:''},'settings-search':{value:''},'settings-type':{value:''},
    'settings-advanced':{checked:false},'settings-list':{innerHTML:''}
  };
  const doc={getElementById:id=>ui[id]};
  const ctx=vm.createContext({document:doc,console,setTimeout,clearTimeout});
  const app=fs.readFileSync('tools/web-console/static/app.js','utf8');
  vm.runInContext(app.split("document.addEventListener('click',")[0],ctx);
  ctx.testData={tasks,settings};
  vm.runInContext('state.tasks=testData.tasks;state.taskSettings=testData.settings;',ctx);
  function optionIds(id){return vm.runInContext(`taskBehavior(state.tasks.find(t=>t.id===${JSON.stringify(id)})).map(x=>x.key)`,ctx);}
  function mustHave(task,key){assert.ok(optionIds(task).includes(key),task+' must expose '+key);}
  function mustNotHave(task,key){assert.ok(!optionIds(task).includes(key),task+' must not expose '+key);}
  mustHave('HERO_RECRUITMENT','HERO_RECRUIT_POINTS_CHEST_BOOL');
  mustHave('HERO_RECRUITMENT','HERO_RECRUIT_EPIC_FREE_BOOL');
  mustHave('HEAL_TROOPS','HEAL_REQUEST_ALLIANCE_HELP_BOOL');
  mustHave('TRAINING_TROOPS','TRAIN_INFANTRY_BOOL');
  mustHave('ALLIANCE_AUTOJOIN','ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL');
  mustHave('EXPLORATION_CHEST','INT_EXPLORATION_CHEST_OFFSET');
  mustHave('GATHER_RESOURCES','GATHER_REMOVE_HEROS_BOOL');
  mustNotHave('HERO_RECRUITMENT','BOOL_HERO_RECRUITMENT');
  mustNotHave('TRAINING_TROOPS','TRAIN_BOOL');
  mustNotHave('TRAINING_TROOPS','HEAL_TROOPS_BOOL');
  mustNotHave('GATHER_RESOURCES','GATHER_SPEED_BOOL');
  mustNotHave('GATHER_RESOURCES','GATHER_SPEED_BOOST_TYPE_STRING');
  mustNotHave('LIFE_ESSENCE','LIFE_ESSENCE_CONSECUTIVE_FAILURES_INT');
  const taskActivation=new Set(tasks.map(t=>t.activationKey).filter(Boolean));
  for(const task of tasks){
    const optionKeys=optionIds(task.id);
    assert.ok(optionKeys.every(key=>!taskActivation.has(key)), 'No task may edit another task enable flag: '+task.id);
    assert.equal(new Set(optionKeys).size,optionKeys.length,'No duplicate option keys');
  }
  vm.runInContext('state.expandedTasks.add("HERO_RECRUITMENT");renderTasks();',ctx);
  assert.match(ui['task-list'].innerHTML,/Gratis epische rekrutering/);
  assert.match(ui['task-list'].innerHTML,/Puntenkist claimen/);
  assert.match(ui['task-list'].innerHTML,/data-task-option="HERO_RECRUIT_POINTS_CHEST_BOOL"/);
  vm.runInContext('state.settings=testData.settings;renderSettings();',ctx);
  assert.match(ui['settings-list'].innerHTML,/SERVE_EMU_ADB_ENDPOINTS_STRING/);
  assert.doesNotMatch(ui['settings-list'].innerHTML,/HERO_RECRUIT_POINTS_CHEST_BOOL/);
  ui['settings-advanced'].checked=true;
  vm.runInContext('renderSettings();',ctx);
  assert.match(ui['settings-list'].innerHTML,/HERO_RECRUIT_POINTS_CHEST_BOOL/);
  const exposed=tasks.filter(t=>optionIds(t.id).length>0);
  console.log('OK: '+tasks.length+' tasks, '+settings.length+' config keys, '+exposed.length+' tasks have per-task behavior controls');
  for(const id of ['HERO_RECRUITMENT','ALLIANCE_AUTOJOIN','HEAL_TROOPS','TRAINING_TROOPS','GATHER_RESOURCES','BEAR_TRAP','FISHING_MINIGAME']) console.log(id+': '+optionIds(id).length+' settings');
})().catch(e=>{console.error(e);process.exitCode=1});
