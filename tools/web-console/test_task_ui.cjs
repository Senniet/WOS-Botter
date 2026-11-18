/* Verify the redesigned task workspace against the actual running Java configuration API.
 * Read-only assertions: never starts or toggles a game task. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
(async()=>{
 const [tasks,settings]=await Promise.all([
   fetch('http://127.0.0.1:18765/api/tasks?profile=1').then(r=>r.json()),
   fetch('http://127.0.0.1:18765/api/settings?profile=1').then(r=>r.json())
 ]);
 const idList=['task-categories','task-summary','task-list-title','task-visible-count','task-list','task-sort',
   'task-detail-content','tasks-search','settings-search','settings-type','settings-advanced','settings-list',
   'metric-tasks','metric-tasks-sub'];
 const ui=Object.fromEntries(idList.map(id=>[id,{innerHTML:'',value:id==='task-sort'?'logical':'',checked:false,textContent:''}]));
 const buttons=[];
 const root={classList:{add(){},remove(){}}};
 const document={getElementById:id=>ui[id],querySelectorAll:()=>buttons,querySelector:()=>root,activeElement:null};
 const localData=new Map();
 const localStorage={getItem:id=>localData.get(id)||null,setItem:(id,v)=>localData.set(id,v)};
 const ctx=vm.createContext({document,localStorage,console,setTimeout,clearTimeout,testData:{tasks,settings}});
 const app=fs.readFileSync('tools/web-console/static/app.js','utf8');
 vm.runInContext(app.split("document.addEventListener('click',")[0],ctx);
 const run=cmd=>vm.runInContext(cmd,ctx);
 run('state.tasks=testData.tasks;state.taskSettings=testData.settings;state.profileId="1";loadTaskStars()');
 const taskIds=run('workGroups.flatMap(g=>g.ids)');
 assert.equal(taskIds.length,new Set(taskIds).size,'Each task should appear in one logical section');
 assert.equal(taskIds.filter(id=>!tasks.some(t=>t.id===id)).length,0,'No invalid task IDs in the workspace');
 assert.equal(tasks.map(t=>t.id).filter(id=>!taskIds.includes(id)).length,0,'All original Java tasks must be included');
 function optionIds(id){return run(`taskBehavior(state.tasks.find(t=>t.id===${JSON.stringify(id)})).map(x=>x.key)`);}
 function mustHave(task,key){assert.ok(optionIds(task).includes(key),task+' must expose '+key);}
 function mustNotHave(task,key){assert.ok(!optionIds(task).includes(key),task+' must not expose '+key);}
 mustHave('HERO_RECRUITMENT','HERO_RECRUIT_POINTS_CHEST_BOOL');
 mustHave('HERO_RECRUITMENT','HERO_RECRUIT_EPIC_FREE_BOOL');
 mustHave('HEAL_TROOPS','HEAL_REQUEST_ALLIANCE_HELP_BOOL');
 mustHave('ONLINE_REWARDS','ONLINE_REWARDS_CHECK_INTERVAL_INT');
 mustHave('FREE_DAILY_CHESTS','FREE_DAILY_CHESTS_INTERVAL_INT');
 mustHave('SMART_DAILY_OVERVIEW','SMART_DAILY_OVERVIEW_INTERVAL_INT');
 mustHave('SMART_DAILY_OVERVIEW','SMART_DAILY_OVERVIEW_AUTO_DISPATCH_BOOL');
 assert.ok(!tasks.some(t=>t.id==='DAILY_OVERVIEW_CHECK'),'Legacy Daily Overview Check must not be a duplicate routine');
 assert.ok(!settings.some(s=>s.key==='DAILY_OVERVIEW_CHECK_OFFSET_INT'),'Retired interval setting must not appear in the task editor');
 mustHave('TRAINING_TROOPS','TRAIN_INFANTRY_BOOL');
 mustHave('ALLIANCE_AUTOJOIN','ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL');
 mustHave('ALLIANCE_AUTOJOIN','ALLIANCE_AUTOJOIN_QUEUES_INT');
 mustNotHave('ALLIANCE_AUTOJOIN','ALLIANCE_AUTOJOIN_USE_PREDEFINED_FORMATION_BOOL');
 mustHave('EXPLORATION_CHEST','INT_EXPLORATION_CHEST_OFFSET');
 mustHave('GATHER_RESOURCES','GATHER_REMOVE_HEROS_BOOL');
 mustNotHave('HERO_RECRUITMENT','BOOL_HERO_RECRUITMENT');
 mustNotHave('TRAINING_TROOPS','TRAIN_BOOL');
 mustNotHave('TRAINING_TROOPS','HEAL_TROOPS_BOOL');
 mustNotHave('GATHER_RESOURCES','GATHER_SPEED_BOOL');
 mustNotHave('GATHER_RESOURCES','GATHER_SPEED_BOOST_TYPE_STRING');
 mustNotHave('LIFE_ESSENCE','LIFE_ESSENCE_CONSECUTIVE_FAILURES_INT');
 const activation=new Set(tasks.map(t=>t.activationKey).filter(Boolean));
 for(const task of tasks){
   const options=optionIds(task.id);
   assert.ok(options.every(key=>!activation.has(key)),'Task cannot edit activation key of another task: '+task.id);
   assert.equal(new Set(options).size,options.length,'No duplicated keys per task');
 }
 run('renderTasks()');
 assert.match(ui['task-list'].innerHTML,/Daily Tasks/);
 assert.match(ui['task-list'].innerHTML,/Gather Resources/);
 assert.match(ui['task-list'].innerHTML,/data-task-toggle="HERO_RECRUITMENT"/);
 assert.match(ui['task-categories'].innerHTML,/All Areas/);
 assert.match(ui['task-summary'].innerHTML,/enabled/);
 assert.match(ui['task-visible-count'].textContent,/67 shown/);
 run('taskSetSelection("HERO_RECRUITMENT")');
 assert.match(ui['task-detail-content'].innerHTML,/Use free Epic Recruitment/);
 assert.match(ui['task-detail-content'].innerHTML,/Claim Recruitment Points Chest/);
 assert.match(ui['task-detail-content'].innerHTML,/data-task-option="HERO_RECRUIT_POINTS_CHEST_BOOL"/);
 assert.doesNotMatch(ui['task-list'].innerHTML,/data-task-option="HERO_RECRUIT_POINTS_CHEST_BOOL"/,'Behavior belongs only in inspector');
 // Exhaustively verify all task detail controls: raw Java/Boolean keys are
 // kept in data attributes for the API, never rendered as hover text.
 let controlCount=0;
 for(const task of tasks){
   run('state.selectedTaskId='+JSON.stringify(task.id)+';renderTaskDetail()');
   const html=ui['task-detail-content'].innerHTML;
   assert.doesNotMatch(html,/task-option-key|title=|Technical key/,task.id+' exposed internal hover metadata');
   assert.doesNotMatch(html,/\b(?:Boolean|Integer|Long|String)\b/,task.id+' exposed technical option types');
   controlCount+=optionIds(task.id).length;
 }
 assert.ok(controlCount>50,'Task Manager should include multiple behavior controls');
 run('state.selectedTaskId="ALLIANCE_AUTOJOIN";renderTaskDetail()');
 assert.match(ui['task-detail-content'].innerHTML,/Keep in-game Auto-Join enabled/);
 assert.match(ui['task-detail-content'].innerHTML,/does not activate it instantly/);
 assert.match(ui['task-detail-content'].innerHTML,/does not manually join individual rallies/);
 assert.match(ui['task-detail-content'].innerHTML,/Rally queues \(1–6\)/);
 assert.match(ui['task-detail-content'].innerHTML,/Use all available troops \(off: specific formation\)/);
 assert.doesNotMatch(ui['task-detail-content'].innerHTML,/ALLIANCE_AUTOJOIN_USE_PREDEFINED_FORMATION_BOOL/);
 run('state.selectedTaskId="HERO_RECRUITMENT";renderTaskDetail()');

 run('state.taskGroup="army";renderTasks()');
 assert.match(ui['task-list'].innerHTML,/Troop Training/);
 assert.match(ui['task-list'].innerHTML,/Heal Troops/);
 assert.doesNotMatch(ui['task-list'].innerHTML,/Gather Resources/);
 run('state.taskGroup="all";state.taskFilter="favorites";state.taskStars.add("HERO_RECRUITMENT");saveTaskStars();renderTasks()');
 assert.match(ui['task-list'].innerHTML,/Hero Recruitment/);
 assert.doesNotMatch(ui['task-list'].innerHTML,/Gather Resources/);
 assert.equal(JSON.parse(localData.get('survivalops.web.stars.1'))[0],'HERO_RECRUITMENT');
 run('clearTaskFilters()');
 assert.match(ui['task-visible-count'].textContent,/67 shown/);
 ui['tasks-search'].value='chests';run('renderTasks()');
 assert.ok(!ui['task-list'].innerHTML.includes('Heal Troops'));
 ui['tasks-search'].value='';
 run('state.settings=testData.settings;renderSettings()');
 assert.match(ui['settings-list'].innerHTML,/SERVE_EMU_ADB_ENDPOINTS_STRING/);
 assert.doesNotMatch(ui['settings-list'].innerHTML,/HERO_RECRUIT_POINTS_CHEST_BOOL/);
 ui['settings-advanced'].checked=true;run('renderSettings()');
 assert.match(ui['settings-list'].innerHTML,/HERO_RECRUIT_POINTS_CHEST_BOOL/);
 const exposed=tasks.filter(t=>optionIds(t.id).length>0);
 const page=fs.readFileSync('tools/web-console/index.html','utf8');
 const css=fs.readFileSync('tools/web-console/static/operations.css','utf8');
 assert.match(page,/static\/operations.css/);
 assert.match(page,/id="task-detail-content"/);
 assert.match(css,/work-layout/);
 console.log('OK: '+tasks.length+' routines uniquely grouped, '+exposed.length+' with behavior controls, '+controlCount+' task options free of technical hover text, '+settings.length+' original configuration keys');
})().catch(e=>{console.error(e);process.exitCode=1});
