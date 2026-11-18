const state = { page: 'dashboard', profiles: [], tasks: [], categories: [], profileId: '', settings: [], taskSettings: [], expandedTasks: new Set(), status: null, emulator: null, frameBusy: false, lastFrame: '', lastError: '' };
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const datetime = value => { if (!value) return '—'; const d = new Date(value); return Number.isNaN(+d) ? esc(value) : d.toLocaleString('nl-NL', {day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}); };
const plain = v => v == null || v === '' ? '—' : String(v);
const api = async (path, method='GET', data) => {
  const response = await fetch(path, {method, credentials:'same-origin', cache:'no-store', headers:{...(data ? {'Content-Type':'application/json'} : {}), ...(method === 'POST' ? {'X-SurvivalOps-Request':'web'} : {})}, body: data ? JSON.stringify(data) : undefined});
  if (response.status === 401) { location.reload(); throw new Error('Sessie verlopen'); }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.error || 'Verzoek mislukt');
  return result;
};
let noticeTimer;
function alertUser(message, isError=false) { const el=$('notice'); el.hidden=false; el.textContent=message; el.classList.toggle('error',isError); clearTimeout(noticeTimer); noticeTimer=setTimeout(()=>el.hidden=true,5500); }
async function action(fn, success) { try { await fn(); if (success) alertUser(success); } catch(e) { alertUser(e.message,true); } }
function setPage(page) {
  state.page = page;
  document.querySelectorAll('.nav-btn').forEach(btn=>btn.classList.toggle('active',btn.dataset.page===page));
  document.querySelectorAll('.page').forEach(el=>el.classList.toggle('active',el.dataset.view===page));
  const label = {dashboard:'Overzicht',tasks:'Takenbeheer',custom:'Eigen taken',statistics:'Statistieken',emulator:'Serve-Emu',profiles:'Profielen',settings:'Instellingen',logs:'Logboek',qa:'Diagnostiek'}[page] || page;
  $('page-title').textContent = label; $('breadcrumb').textContent = label.toUpperCase();
  document.querySelector('.sidebar').classList.remove('open');
  if(page==='tasks') loadTasks();
  if(page==='settings') loadSettings();
  if(page==='emulator') loadDevice();
  if(page==='logs') loadLogs();
  if(page==='profiles') renderProfiles();
  if(page==='qa') loadQa();
  if(page==='custom') loadCustom();
  if(page==='statistics') loadStatistics();
}
function updateSelects(){
  for(const name of ['tasks-profile','settings-profile','statistics-profile']){
    const el=$(name), before=el.value;
    const options = state.profiles.map(p=>'<option value="'+esc(p.id)+'">'+esc(p.name)+'</option>').join('');
    el.innerHTML=(name==='settings-profile'?'<option value="">Globaal (Serve-Emu)</option>':'')+options;
    if([...el.options].some(o=>o.value===before)) el.value=before;
    else if(name==='tasks-profile'||name==='statistics-profile') el.value=state.profileId || (state.profiles[0]?.id ?? '');
  }
}
async function loadProfiles(){
  state.profiles=await api('/api/profiles');
  if(!state.profiles.some(p=>String(p.id)===String(state.profileId))) state.profileId=String(state.profiles[0]?.id ?? '');
  updateSelects(); renderProfiles();
  $('metric-profiles').textContent=state.profiles.length;
  $('metric-profiles-sub').textContent=state.profiles.filter(p=>p.enabled).length+' actief';
}
function renderProfiles(){
  const root=$('profiles-list');
  if(!state.profiles.length){root.innerHTML='<p class="text-muted">Nog geen profielen. Maak je eerste profiel aan.</p>';return;}
  root.innerHTML=state.profiles.map(p=>'<div class="profile-row"><div><strong>'+esc(p.name)+'</strong><small>Slot '+esc(p.slot)+' · '+(p.enabled?'Actief':'Uitgeschakeld')+(p.queueActive?' · Scheduler verbonden':'')+'</small></div><div class="actions">'+(p.queueActive?'<button class="secondary small" data-profile-queue="'+esc(p.id)+'" data-queue-action="'+(p.paused?'resume':'pause')+'">'+(p.paused?'Hervatten':'Pauzeren')+'</button>':'')+'<button class="secondary small" data-edit-profile="'+esc(p.id)+'">Bewerken</button></div></div>').join('');
}
function editProfile(id){
 const p=state.profiles.find(p=>String(p.id)===String(id)); if(!p)return;
 $('profile-id').value=p.id; $('profile-name').value=p.name; $('profile-enabled').checked=p.enabled; setPage('profiles');
}
function engineControls(s){
 const running=!!s.running, busy=!!s.executing;
 $('start').disabled=running;
 $('pause').disabled=!running;
 $('resume').disabled=!running;
 $('stop').disabled=!running;
 $('metric-engine').textContent=busy?'Bezig':running?'Actief':'Gestopt';
 $('metric-engine-sub').textContent=busy?'Er draait een taak':running?'Scheduler ingeschakeld':'Handmatig starten';
 $('queue-count').textContent=s.queues.length+' wachtrijen';
 $('queues').innerHTML=s.queues.length?s.queues.map(q=>'<div class="queue-item"><div><strong>'+esc(q.name)+'</strong><small>'+esc(q.next?'Volgende '+datetime(q.next):'Nog geen planning')+'</small></div><span class="pill '+(q.executing?'good':'')+'">'+(q.executing?'Bezig':q.paused?'Gepauzeerd':'Wacht')+'</span></div>').join(''):'<span class="text-muted">De scheduler draait nog niet. Start de bot als je een actief profiel hebt.</span>';
}
async function loadStatus(){
  try{
    state.status=await api('/api/status');
    engineControls(state.status);
    $('connection').textContent='Verbonden'; $('connection').className='pill good';
    $('side-status').textContent='Engine verbonden';document.querySelector('.status-led').classList.add('on');
    $('last-sync').textContent='Bijgewerkt '+new Date().toLocaleTimeString('nl-NL');
  }catch(e){
    $('connection').textContent='Offline';$('connection').className='pill bad';
    $('side-status').textContent='Engine offline';document.querySelector('.status-led').classList.remove('on');
    $('metric-engine').textContent='Offline';
  }
}
// Task-specific controls reuse the real MSI ConfigurationKeyEnum keys and profile database.
// The task activation flag itself remains in the task's main checkbox.
const taskOptionPrefixes = {
 ALLIANCE_AUTOJOIN:['ALLIANCE_AUTOJOIN_'], ALLIANCE_CHAMPIONSHIP:['ALLIANCE_CHAMPIONSHIP_'],
 ALLIANCE_CHESTS:['ALLIANCE_CHESTS_'],ALLIANCE_HELP_REQUEST:['ALLIANCE_HELP_'],
 ALLIANCE_MOBILIZATION:['ALLIANCE_MOBILIZATION_'],ALLIANCE_PET_TREASURE:['ALLIANCE_PET_TREASURE_'],
 ALLIANCE_SHOP:['ALLIANCE_SHOP_'],ALLIANCE_TECH:['ALLIANCE_TECH_'],ALLIANCE_TRIUMPH:['ALLIANCE_TRIUMPH_'],
 BEAR_TRAP:['BEAR_TRAP_'],CITY_SURVIVORS:['CITY_ACCEPT_NEW_SURVIVORS_'],
 CITY_UPGRADE_FURNACE:['CITY_UPGRADE_'],RESEARCH:['RESEARCH_'],
 DAILY_MISSIONS:['DAILY_MISSION_'],EXPERT_AGNES_INTEL:['EXPERT_AGNES_'],
 EXPERT_ROMULUS_TAG:['EXPERT_ROMULUS_TAG_'],EXPERT_ROMULUS_TROOPS:['EXPERT_ROMULUS_TROOPS_'],
 EXPERT_SKILL_TRAINING:['EXPERT_SKILL_TRAINING_'],INTEL:['INTEL_'],MAIL_REWARDS:['MAIL_'],
 STOREHOUSE_CHEST:['STOREHOUSE_'],EVENT_BERSERK_CRYPTID:['RALLY_'],
 EVENT_HERO_MISSION:['HERO_MISSION_'],EVENT_JOURNEY_OF_LIGHT:['JOURNEY_OF_LIGHT_'],
 EVENT_MYRIAD_BAZAAR:['MYRIAD_BAZAAR_'],EVENT_POLAR_TERROR:['POLAR_TERROR_'],
 EVENT_TUNDRA_TRUCK:['TUNDRA_TRUCK_'],FISHING_MINIGAME:['FISHING_'],
 MERCENARY_EVENT:['MERCENARY_'],TEST_HOOK_LOOP:['TEST_HOOK_'],
 CREATE_CHARACTER:['CREATE_CHARACTER_'],DUMMY_TASK:['DUMMY_TASK_'],
 GATHER_BOOST:['GATHER_SPEED_'],GATHER_RESOURCES:['GATHER_'],
 HEAL_TROOPS:['HEAL_'],TRAINING_TROOPS:['TRAIN_'],PET_SKILLS:['PET_SKILL_'],
 ARENA:['ARENA_'],BANK:['INT_BANK_DELAY','BOOL_BANK'],BEAST_HUNTING:['BEAST_HUNTING_'],
 CRYSTAL_LABORATORY:['CRYSTAL_LAB_','BOOL_CRYSTAL_LAB_'],
 DO_EXPLORATION:['DO_EXPLORATION_'], EXPLORATION_CHEST:['EXPLORATION_CHEST_','INT_EXPLORATION_CHEST_'],
 HERO_RECRUITMENT:['HERO_RECRUIT_'],LABYRINTH:['DAILY_LABYRINTH_'],
 LIFE_ESSENCE:['LIFE_ESSENCE_'],LIFE_ESSENCE_CARING:['ALLIANCE_LIFE_ESSENCE_'],
 NOMADIC_MERCHANT:['NOMADIC_MERCHANT_'],PET_ADVENTURE:['PET_ADVENTURE_'],
 TREK_AUTOMATION:['TUNDRA_TREK_AUTOMATION_'],TREK_SUPPLIES:['TUNDRA_TREK_SUPPLIES_'],
 VIP_POINTS:['VIP_','BOOL_VIP_POINTS'],WAR_ACADEMY:['WAR_ACADEMY_'],
 SHOP_MYSTERY:['MYSTERY_SHOP_']
};
const taskOptionLabels = {
 HERO_RECRUIT_ADVANCED_FREE_BOOL:'Gratis geavanceerde rekrutering',
 HERO_RECRUIT_EPIC_FREE_BOOL:'Gratis epische rekrutering',
 HERO_RECRUIT_ADVANCED_KEYS_BOOL:'Geavanceerde sleutels gebruiken',
 HERO_RECRUIT_EPIC_KEYS_BOOL:'Epische sleutels gebruiken',
 HERO_RECRUIT_POINTS_CHEST_BOOL:'Puntenkist claimen',
 GATHER_REMOVE_HEROS_BOOL:'Helden uit de formatie verwijderen',
 GATHER_ONLY_FULL_RESOURCES_BOOL:'Alleen volle grondstofvelden',
 GATHER_DOWNGRADE_LEVEL_BOOL:'Lager niveau proberen als nodig',
 GATHER_NO_HERO_FALLBACK_BOOL:'Zonder helden proberen',
 CITY_UPGRADE_DOUBLE_TIME_BOOL:'Dubbele bouwversnelling',
 CITY_UPGRADE_RESERVE_PRODUCTION_BOOL:'Productiebonus reserveren',
 ARENA_TASK_ATTACK_QUICK_DEPLOY_BOOL:'Snel inzetten bij aanval',
 ARENA_TASK_REFRESH_WITH_GEMS_BOOL:'Verversen met edelstenen',
 ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL:'Alle beschikbare troepen inzetten',
 ALLIANCE_AUTOJOIN_USE_PREDEFINED_FORMATION_BOOL:'Vaste formatie gebruiken',
 INTEL_RECALL_GATHER_TROOPS_BOOL:'Verzameltroepen terugroepen voor Intel',
 POLAR_TERROR_USE_STAMINA_ITEMS_BOOL:'Uithoudingsvermogen-items gebruiken',
 TUNDRA_TRUCK_USE_GEMS_BOOL:'Edelstenen gebruiken voor de truck',
 BOOL_DEBUG:'Extra debugregistratie',
};
const taskWords={CONSTRUCTION:'bouw',LOCK:'vergrendeling',
 SPEED:'snelheid',BOOST:'versnelling',TO:'naar',FOR:'voor',
 WITH:'met',WITHOUT:'zonder',DURATION:'duur',POINT:'punt',
 GATHERING:'verzamelen',UPGRADE:'verbeteren',MOBILIZATION:'mobilisatie',
 BOOL:'',INT:'',STRING:'',ENABLED:'',TASK:'',AUTO:'automatisch',USE:'gebruik',ALL:'alle',
 TRAIN:'trainen',TRAINING:'training',TROOPS:'troepen',HEAL:'genezen',HEALING:'genezing',
 HERO:'held',HEROS:'helden',HEROES:'helden',RECRUIT:'rekrutering',REWARDS:'beloningen',
 CHEST:'kist',CHESTS:'kisten',CLAIM:'claimen',FREE:'gratis',ADVANCED:'geavanceerd',EPIC:'episch',
 KEYS:'sleutels',POINTS:'punten',PERCENTAGE:'percentage',INFANTRY:'infanterie',
 LANCERS:'lanciers',MARKSMANS:'schutters',LIMIT:'limiet',SPEEDUPS:'versnellingen',
 GEMS:'edelstenen',STAMINA:'uithoudingsvermogen',RESERVE:'reserve',LEVEL:'niveau',
 MARCHES:'marsen',QUEUE:'wachtrij',QUEUES:'wachtrijen',FLAG:'vlag',TARGET:'doelwit',
 TIME:'tijd',MINUTES:'minuten',HOURS:'uren',OFFSET:'interval',MAX:'maximum',MIN:'minimum',
 RECALL:'terugroepen',GATHER:'verzamelen',RESOURCE:'grondstof',RESOURCES:'grondstoffen',
 AUTOJOIN:'automatisch deelnemen',DEFEAT:'verslaan',BEASTS:'beesten',BUY:'kopen',
 SKILL:'vaardigheid',SKILLS:'vaardigheden',ACTIVE:'actief',ONLY:'alleen',
 RESTARTS:'herstarts',CASTS:'worpen',CATCHES:'vangsten',FISHING:'vissen',
 PRIORITIES:'prioriteiten',PRIORITY:'prioriteit',ACTIVATION:'activering',
 SCHEDULE:'planning',DATETIME:'datum en tijd',USE:'gebruiken',
};
// Prefix overlap must never cause one task to edit another task's activation switch.
function taskBehavior(task){
 const prefixes=taskOptionPrefixes[task.id]||[];
 const activations=new Set(state.tasks.map(t=>t.activationKey).filter(Boolean));
 return state.taskSettings.filter(s=>!s.secret && !/(?:_LAST_|_NEXT_|_CONSECUTIVE_FAILURES_|_STATE_|_TIME_LONG$)/.test(s.key) && s.key!==task.activationKey &&
     !activations.has(s.key) && prefixes.some(prefix=>s.key.startsWith(prefix)) &&
     !(task.id==='GATHER_RESOURCES'&&s.key.startsWith('GATHER_SPEED_')) &&
     !(task.id==='CITY_UPGRADE_FURNACE'&&s.key==='CITY_UPGRADE_PRIORITISE_FURNACE_BOOL'));
}
function optionLabel(task,key){
 if(taskOptionLabels[key])return taskOptionLabels[key];
 const prefixes=taskOptionPrefixes[task.id]||[];
 const prefix=prefixes.filter(p=>key.startsWith(p)).sort((a,b)=>b.length-a.length)[0]||'';
 const remaining=key.slice(prefix.length).replace(/_?(BOOL|INT|STRING|JSON)$/,'');
 const words=remaining.split('_').map(w=>taskWords[w]===undefined?w.toLowerCase():taskWords[w]).filter(Boolean);
 const label=words.join(' ').trim();
 return label?label.charAt(0).toUpperCase()+label.slice(1):'Optie';
}
function optionControl(task,s){
 const label=optionLabel(task,s.key);
 const id='option-'+task.id+'-'+s.key;
 const help='<small class="task-option-key" title="Technische sleutel">'+esc(s.key)+'</small>';
 if(s.type==='Boolean'){
  return '<label class="task-option toggle-option" for="'+esc(id)+'"><span class="option-name">'+esc(label)+help+'</span><input type="checkbox" id="'+esc(id)+'" data-task-option="'+esc(s.key)+'" data-task-owner="'+esc(task.id)+'" '+(s.value==='true'?'checked':'')+'><span class="switch-track" aria-hidden="true"></span></label>';
 }
 const inputType=['Integer','Long'].includes(s.type)?'number':'text';
 return '<label class="task-option value-option" for="'+esc(id)+'"><span class="option-name">'+esc(label)+help+'</span><span class="value-controls"><input id="'+esc(id)+'" type="'+inputType+'" data-task-value="'+esc(s.key)+'" data-task-owner="'+esc(task.id)+'" value="'+esc(s.value)+'" aria-label="'+esc(label)+'"><button class="secondary small" data-save-task-value="'+esc(s.key)+'" data-task-owner="'+esc(task.id)+'">Opslaan</button></span></label>';
}
async function loadTasks(){
 const profileId=state.profileId;
 if(!profileId){$('task-list').innerHTML='<div class="panel-content text-muted">Maak eerst een profiel aan.</div>';return;}
 try{
   const [tasks,settings]=await Promise.all([
     api('/api/tasks?profile='+encodeURIComponent(profileId)),
     api('/api/settings?profile='+encodeURIComponent(profileId))]);
   if(state.profileId!==profileId)return;
   state.tasks=tasks; state.taskSettings=settings;
   const categories=[...new Set(tasks.map(t=>t.category))].sort();
   const selected=$('tasks-category').value;
   $('tasks-category').innerHTML='<option value="">Alle categorieën</option>'+categories.map(c=>'<option>'+esc(c)+'</option>').join('');
   $('tasks-category').value=categories.includes(selected)?selected:'';
   $('metric-tasks').textContent=tasks.filter(t=>t.enabled).length+' / '+tasks.length;
   $('metric-tasks-sub').textContent='Taken ingeschakeld';
   // Do not replace a form while the user is typing a task option.
   if(!document.activeElement?.closest('[data-task-options]'))renderTasks();
 }catch(e){$('task-list').innerHTML='<div class="panel-content">'+esc(e.message)+'</div>';}
}
function renderTasks(){
 const search=$('tasks-search').value.trim().toLowerCase(),cat=$('tasks-category').value,enabled=$('tasks-enabled').checked;
 const items=state.tasks.filter(t=>(!search||(t.name+' '+t.id+' '+t.category).toLowerCase().includes(search))&&(!cat||t.category===cat)&&(!enabled||t.enabled));
 $('task-list').innerHTML=items.map(t=>{
   const label=t.outcome==='SUCCEEDED'?'Geslaagd':t.outcome==='FAILED'?'Mislukt':t.outcome==='RUNNING'?'Uitvoering':t.outcome==='NOT_RUN'?'Nog niet':t.outcome;
   const cls=t.outcome==='SUCCEEDED'?'good':t.outcome==='FAILED'?'bad':'';
   const behavior=taskBehavior(t),isOpen=state.expandedTasks.has(t.id);
   const actionButton=behavior.length?'<button class="secondary" data-expand-task="'+esc(t.id)+'" aria-expanded="'+isOpen+'">⚙ Opties ('+behavior.length+') '+(isOpen?'▴':'▾')+'</button>':'';
   const top='<div class="task-row"><div class="task-name"><label><input type="checkbox" data-task-toggle="'+esc(t.id)+'" '+(t.enabled?'checked ':'')+(t.toggleable?'':'disabled ')+'> <span><strong>'+esc(t.name)+'</strong><small>'+esc(t.category.replace(/_/g,' '))+'</small></span></label></div><div class="outcome '+cls+'" title="'+esc(t.detail)+'">'+esc(t.executing?'Bezig':label)+'<small class="text-muted">'+(t.lastAttempt?' · '+datetime(t.lastAttempt):'')+'</small></div><div class="text-muted">'+datetime(t.next)+'</div><div class="task-actions">'+actionButton+'<button class="secondary" data-schedule-task="'+esc(t.id)+'" '+(!t.enabled?'disabled':'')+'>Plannen</button><button class="secondary" data-run-task="'+esc(t.id)+'" '+(!t.enabled?'disabled':'')+'>Nu uitvoeren</button></div></div>';
   const opts=isOpen&&behavior.length?'<div class="task-option-panel" data-task-options="'+esc(t.id)+'"><div class="task-option-heading"><strong>Gedrag van deze taak</strong><span>Deze keuzes worden direct per profiel opgeslagen.</span></div><div class="task-option-grid">'+behavior.map(s=>optionControl(t,s)).join('')+'</div></div>':'';
   return '<div class="task-card">'+top+opts+'</div>';
 }).join('')||'<div class="panel-content text-muted">Geen taken gevonden.</div>';
}
async function saveTaskOption(key,value,profileId){
 await api('/api/setting','POST',{profileId,key,value});
 if(profileId===state.profileId){
  const s=state.taskSettings.find(s=>s.key===key);if(s)s.value=value;
 }
 alertUser('Taakoptie opgeslagen');
}
async function loadSettings(){
 try {state.settings=await api('/api/settings'+($('settings-profile').value?'?profile='+encodeURIComponent($('settings-profile').value):''));renderSettings(); }
 catch(e){$('settings-list').textContent=e.message;}
}
function renderSettings(){
 const search=$('settings-search').value.trim().toLowerCase(),type=$('settings-type').value;
 const advanced=$('settings-advanced').checked;
 const essentials=new Set(['CURRENT_EMULATOR_STRING','SERVE_EMU_ADB_ENDPOINTS_STRING',
  'AUTO_START_ENABLED_BOOL','AUTO_START_DELAY_MINUTES_INT','IDLE_BEHAVIOR_STRING',
  'STOP_BEHAVIOR_STRING','MAX_IDLE_TIME_INT','BOOL_DEBUG',
  'LAN_DIAGNOSTICS_ENABLED_BOOL','LAN_DIAGNOSTICS_SCREENSHOTS_BOOL']);
 const arr=state.settings.filter(s=>(advanced||search||essentials.has(s.key))&&
    (!search||s.key.toLowerCase().includes(search))&&(!type||s.type===type));
 $('settings-list').innerHTML=arr.map(s=>{
   const id='setting-'+s.key;
   const field=s.type==='Boolean'?'<select data-setting-value="'+esc(s.key)+'"><option value="true" '+(s.value==='true'?'selected':'')+'>Aan</option><option value="false" '+(s.value==='false'?'selected':'')+'>Uit</option></select>':
     '<input data-setting-value="'+esc(s.key)+'" '+(s.secret?'type="password" autocomplete="new-password" placeholder="Nieuwe waarde (bestaande blijft verborgen)"':'type="'+(s.type==='Integer'?'number':'text')+'" value="'+esc(s.value)+'"')+' >';
   return '<div class="setting-row"><label for="'+id+'">'+esc(s.key)+'<small>'+esc(s.type)+(s.secret?' · beveiligd':'')+'</small></label>'+field+'<button class="secondary small" data-save-setting="'+esc(s.key)+'">Opslaan</button></div>';
 }).join('')||'<div class="panel-content text-muted">Geen instellingen gevonden.</div>';
}
async function saveSetting(key){
 const input=[...document.querySelectorAll('[data-setting-value]')].find(i=>i.dataset.settingValue===key);
 if(!input)return;
 await api('/api/setting','POST',{profileId:$('settings-profile').value,key,value:input.value});
 if(input.type==='password')input.value='';
 alertUser('Instelling opgeslagen: '+key);
}
async function loadDevice(){
 try {
  state.emulator=await api('/api/emulator/state');
  $('emu-connection').textContent=state.emulator.connected?'ADB verbonden':'ADB offline';
  $('emu-connection').className='pill '+(state.emulator.connected?'good':'bad');
  $('metric-device').textContent=state.emulator.connected?'Online':'Offline';
  $('metric-device-sub').textContent=state.emulator.endpoint || 'Serve-Emu';
  $('emu-endpoint').textContent=state.emulator.endpoint || '—';
  if(state.emulator.externalControllerLikelyActive){
   $('emu-mode').textContent='MSI/Task Coach mogelijk actief; bediening geblokkeerd';
  }
 } catch(e){
  $('emu-connection').textContent='Niet verbonden';$('emu-connection').className='pill bad';
  $('metric-device').textContent='Offline';$('metric-device-sub').textContent=e.message;
 }
}
async function refreshFrame(){
 if(state.frameBusy || state.page!=='emulator')return;
 state.frameBusy=true;
 try{
   const response=await fetch('/api/emulator/frame',{cache:'no-store'});
   if(!response.ok){const data=await response.json();throw new Error(data.error||'Geen afbeelding');}
   const blob=await response.blob();
   const url=URL.createObjectURL(blob), previous=state.lastFrame;
   $('emu-image').src=url;state.lastFrame=url;
   if(previous) $('emu-image').onload=()=>URL.revokeObjectURL(previous);
   $('emu-snapshot-status').textContent='Screenshot '+new Date().toLocaleTimeString('nl-NL');
 }catch(e){$('emu-snapshot-status').textContent=e.message;}
 finally{state.frameBusy=false;}
}
async function loadLogs(){
 try{const result=await api('/api/logs');$('logs-body').textContent=(result.lines||[]).join('\n')||result.note||'Geen logs beschikbaar';}catch(e){$('logs-body').textContent=e.message;}
}
async function loadStatistics(){
 const id=$('statistics-profile').value||state.profileId;
 if(!id)return;
 try{
  const report=await api('/api/statistics?profile='+encodeURIComponent(id));
  const all=report.routines||[],total=report.totalRuns||0;
  const ocr=all.reduce((a,t)=>a+t.ocrErrors,0),matches=all.reduce((a,t)=>a+t.matchErrors,0);
  $('statistics-stats').innerHTML=[['Uitvoeringen',total],['Verschillende taken',all.length],['OCR-fouten',ocr],['Herkenningsfouten',matches]].map(([k,v])=>'<article class="metric"><span>'+esc(k)+'</span><strong>'+esc(v)+'</strong></article>').join('');
  $('statistics-routines').innerHTML=all.map(t=>'<div class="stats-row"><div><strong>'+esc(t.name||t.key)+'</strong><small>Laatste: '+esc(t.last||'—')+'</small></div><div><strong>'+esc(t.runs)+'×</strong><small>Gem. '+(t.runs?Math.round(t.totalMs/t.runs/1000):0)+'s</small></div><div><span class="outcome '+(t.ocrErrors+t.matchErrors?'bad':'good')+'">'+esc(t.ocrErrors+t.matchErrors)+' fouten</span></div></div>').join('')||'<p class="text-muted">Nog geen uitvoering geregistreerd voor dit profiel.</p>';
  $('statistics-counters').innerHTML=Object.entries(report.counters||{}).map(([k,v])=>'<div class="detail-row"><span>'+esc(k)+'</span><strong>'+esc(v)+'</strong></div>').join('')||'<p class="text-muted">Geen aanvullende tellers.</p>';
 }catch(e){$('statistics-routines').textContent=e.message;}
}
let builderSteps = [];
const builderFields = {
 TAP_POINT: [['x','X',360,0,719],['y','Y',640,0,1279]],
 WAIT: [['durationMs','Wacht (ms)',750,100,30000]],
 SWIPE: [['startX','Start X',360,0,719],['startY','Start Y',900,0,1279],
         ['endX','Eind X',360,0,719],['endY','Eind Y',300,0,1279]],
 BACK_BUTTON: []
};
const builderLabels={TAP_POINT:'Tik op punt',WAIT:'Wachten',SWIPE:'Vegen',BACK_BUTTON:'Terugknop'};
function renderBuilder(){
 $('builder-steps').innerHTML=builderSteps.map((step,index)=>{
  const fields=builderFields[step.type]||[];
  return '<div class="builder-step"><strong>'+(index+1)+'. '+esc(builderLabels[step.type])+'</strong><div class="inputs">'+fields.map(([key,label,value,min,max])=>'<label>'+esc(label)+'<input type="number" min="'+min+'" max="'+max+'" required data-step-field="'+key+'" data-step-index="'+index+'" value="'+esc(step[key]??value)+'"></label>').join('')+'</div><button type="button" class="danger small" data-remove-step="'+index+'" aria-label="Stap verwijderen">×</button></div>';
 }).join('')||'<p class="text-muted">Voeg minimaal één stap toe aan de workflow.</p>';
}
async function loadCustom(){
 try{
  const all=await api('/api/custom-tasks');
  $('custom-task-list').innerHTML=all.map(t=>{
   const id=esc(t.className);
   return '<form class="custom-row" data-custom-form="'+id+'"><div><strong>'+id+'</strong><small>'+(t.loaded?'Beschikbaar in Java-engine':'Niet geladen')+'</small></div><label>Minuten tussen uitvoeringen<input type="number" name="offsetMinutes" min="0" max="10080" value="'+esc(t.offsetMinutes)+'"></label><label>Prioriteit<input type="number" name="priority" min="-1000" max="1000" value="'+esc(t.priority)+'"></label><label>Vervolg na uren<input type="number" name="followUpDelayHours" min="1" max="720" value="'+esc(t.followUpDelayHours??8)+'"></label><div class="custom-actions"><span class="pill '+(t.enabled?'good':'')+'">'+(t.enabled?'Ingeschakeld':'Uitgeschakeld')+'</span><button class="secondary small" type="submit" data-custom-action="save" '+(!t.loaded?'disabled':'')+'>Opslaan</button><button class="'+(t.enabled?'danger':'primary')+' small" type="submit" data-custom-action="'+(t.enabled?'disable':'enable')+'" '+(!t.loaded?'disabled':'')+'>'+(t.enabled?'Uitschakelen':'Inschakelen')+'</button></div></form>';
  }).join('')||'<p class="text-muted">Nog geen eigen taken geïmporteerd in deze aparte webworkspace. De 63 ingebouwde routines staan in Takenbeheer.</p>';
 }catch(e){$('custom-task-list').textContent=e.message;}
}
async function loadQa(){
 try{
  const data=await api('/api/qa/summary');
  const counter=data.status_counts||{};
  const stats=[['Geregistreerde taken',data.total_tasks||0],['Geverifieerd',counter.verified||0],['Nog niet afgerond',(counter.testing||0)+(counter.working||0)+(counter.broken||0)+(counter.blocked||0)],['Diagnostiek-uploads',data.diagnostic_total||0]];
  $('qa-stats').innerHTML=stats.map(([k,v])=>'<div class="metric"><span>'+esc(k)+'</span><strong>'+esc(v)+'</strong></div>').join('');
  $('qa-issues').innerHTML=(data.open_issues||[]).map(i=>'<div class="qa-item"><span class="pill '+(i.severity==='high'?'bad':'')+'">'+esc(i.severity)+'</span><div><strong>'+esc(i.title)+'</strong><small>'+esc(i.label||i.task_key||'Systeem')+' · '+datetime(i.created_at)+'</small></div></div>').join('')||'<p class="text-muted">Geen openstaande QA-issues.</p>';
  $('qa-diagnostics').innerHTML=(data.diagnostics||[]).map(d=>'<button class="qa-item qa-diagnostic" data-diagnostic="'+esc(d.path)+'"><span class="pill '+(d.severity==='error'?'bad':'')+'">'+esc(d.severity)+'</span><div><strong>'+esc(d.task_label||d.task_key||d.activity||'Systeem')+'</strong><small>'+esc(d.kind)+' · '+datetime(d.timestamp)+(d.has_image?' · Screenshot':'')+'</small></div><span class="muted">›</span></button>').join('')||'<p class="text-muted">Nog geen diagnostische uploads.</p>';
 }catch(e){$('qa-stats').textContent=e.message;$('qa-issues').textContent='Archief niet bereikbaar';$('qa-diagnostics').textContent='Archief niet bereikbaar';}
}
async function showQa(path){
 try{
  const d=await api('/api/qa/detail?path='+encodeURIComponent(path));
  $('qa-dialog-title').textContent=d.activity||d.kind||'Diagnostiek';
  $('qa-dialog-info').textContent=[d.task_key,d.kind,d.severity,d.timestamp].filter(Boolean).join(' · ');
  const img=$('qa-dialog-image');img.hidden=!d.has_image;
  if(d.has_image)img.src='/api/qa/image?path='+encodeURIComponent(path);
  else img.removeAttribute('src');
  $('qa-dialog-logs').textContent=(d.log_lines||[]).join('\n')||'Geen logfragment opgeslagen';
  $('qa-dialog').showModal();
 }catch(e){alertUser(e.message,true);}
}
document.addEventListener('click',e=>{
 const schedule=e.target.closest('[data-schedule-task]');if(schedule)showScheduleDialog(schedule.dataset.scheduleTask);
 const q=e.target.closest('[data-profile-queue]');if(q)action(async()=>{await api('/api/profile-queue','POST',{profileId:q.dataset.profileQueue,action:q.dataset.queueAction});await loadProfiles();},'Wachtrij bijgewerkt');
 const diag=e.target.closest('[data-diagnostic]');if(diag)showQa(diag.dataset.diagnostic);
 const edit=e.target.closest('[data-edit-profile]');if(edit)editProfile(edit.dataset.editProfile);
 const go=e.target.closest('[data-go]');if(go)setPage(go.dataset.go);
 const run=e.target.closest('[data-run-task]');if(run) action(async()=>{await api('/api/task','POST',{action:'run',profileId:state.profileId,task:run.dataset.runTask}); await loadTasks();},'Taak toegevoegd aan de wachtrij');
 const setting=e.target.closest('[data-save-setting]');if(setting)action(()=>saveSetting(setting.dataset.saveSetting));
 const expand=e.target.closest('[data-expand-task]');if(expand){
   const id=expand.dataset.expandTask;
   if(state.expandedTasks.has(id))state.expandedTasks.delete(id);else state.expandedTasks.add(id);
   renderTasks();
 }
 const saveTask=e.target.closest('[data-save-task-value]');if(saveTask){
   const input=saveTask.parentElement.querySelector('input[data-task-value]');
   if(input && input.checkValidity()){
     const profileId=state.profileId,key=input.dataset.taskValue;
     action(()=>saveTaskOption(key,input.value,profileId));
   } else if(input)input.reportValidity();
 }
});
document.addEventListener('change',e=>{
 const task=e.target.closest('[data-task-toggle]');
 if(task){
   const profileId=state.profileId,enabled=task.checked;
   task.disabled=true;
   action(async()=>{
     await api('/api/task','POST',{action:'toggle',profileId,task:task.dataset.taskToggle,enabled});
     await loadTasks();
   },'Taakinstelling opgeslagen');
 }
 const taskOption=e.target.closest('[data-task-option]');
 if(taskOption){
   const profileId=state.profileId,key=taskOption.dataset.taskOption;
   const wasChecked=!taskOption.checked,value=String(taskOption.checked);
   taskOption.disabled=true;
   action(async()=>{
     try{await saveTaskOption(key,value,profileId);}
     catch(e){taskOption.checked=wasChecked;throw e;}
     finally{taskOption.disabled=false;}
   });
 }
});
document.querySelectorAll('.nav-btn').forEach(btn=>btn.addEventListener('click',()=>setPage(btn.dataset.page)));
$('custom-refresh').addEventListener('click',loadCustom);
$('statistics-profile').addEventListener('change',loadStatistics);
$('statistics-refresh').addEventListener('click',loadStatistics);
$('statistics-reset').addEventListener('click',()=>{
 if(!confirm('Alle opgebouwde statistieken van dit profiel wissen?'))return;
 action(async()=>{
  await api('/api/statistics','POST',{action:'reset',profileId:$('statistics-profile').value});
  await loadStatistics();
 },'Profielstatistieken gewist');
});
document.querySelectorAll('[data-add-step]').forEach(button=>button.addEventListener('click',()=>{
 const type=button.dataset.addStep;
 if(!builderFields[type])return;
 if(builderSteps.length>=48){alertUser('Maximaal 48 stappen',true);return;}
 const newStep={type};
 for(const [key,label,value] of builderFields[type])newStep[key]=value;
 builderSteps.push(newStep);renderBuilder();
}));
$('builder-steps').addEventListener('click',e=>{
 const button=e.target.closest('[data-remove-step]');
 if(button){builderSteps.splice(Number(button.dataset.removeStep),1);renderBuilder();}
});
$('builder-steps').addEventListener('input',e=>{
 const input=e.target.closest('[data-step-field]');
 if(input){const step=builderSteps[Number(input.dataset.stepIndex)];if(step)step[input.dataset.stepField]=Number(input.value);}
});
$('builder-form').addEventListener('submit',e=>{
 e.preventDefault();
 if(!builderSteps.length){alertUser('Voeg minstens één stap toe',true);return;}
 action(async()=>{
  const data=await api('/api/builder','POST',{
   name:$('builder-name').value.trim(),startLocation:$('builder-location').value,
   steps:builderSteps
  });
  $('builder-form').reset();builderSteps=[];renderBuilder();await loadCustom();
  alertUser('Java-workflow '+data.className+' gecompileerd. Schakel hem expliciet in voordat je de bot start.');
 });
});
renderBuilder();

$('custom-task-list').addEventListener('click',e=>{
 const action=e.target.closest('[data-custom-action]');
 if(action)action.closest('form').dataset.submission=action.dataset.customAction;
});
$('custom-task-list').addEventListener('submit',e=>{
 e.preventDefault();const form=e.target;
 const data=Object.fromEntries(new FormData(form));
 const command=form.dataset.submission||'save';
 if(command==='delete'&&!confirm('Deze eigen taak en de bijbehorende bestanden verwijderen?'))return;
 action(async()=>{
  await api('/api/custom-task','POST',{action:command,className:form.dataset.customForm,
   offsetMinutes:Number(data.offsetMinutes),priority:Number(data.priority),
   followUpDelayHours:Number(data.followUpDelayHours)});
  await loadCustom();
 },'Eigen taak opgeslagen');
});
$('mobile-menu').addEventListener('click',()=>document.querySelector('.sidebar').classList.toggle('open'));
$('logout').addEventListener('click',async()=>{try{await api('/api/logout','POST',{});}finally{location.replace('/');}});
for(const [name, actionName] of [['start','start'],['pause','pause'],['resume','resume'],['stop','stop']]) {
 $(name).addEventListener('click',()=>action(async()=>{await api('/api/engine','POST',{action:actionName});await loadStatus();await loadProfiles();},'Bot: '+actionName));
}
$('tasks-profile').addEventListener('change',e=>{state.profileId=e.target.value;state.expandedTasks.clear();loadTasks();});
for(const id of ['tasks-search','tasks-category','tasks-enabled']) $(id).addEventListener(id==='tasks-search'?'input':'change',renderTasks);
$('settings-profile').addEventListener('change',loadSettings);
for(const id of ['settings-search','settings-type','settings-advanced']) $(id).addEventListener(id==='settings-search'?'input':'change',renderSettings);
$('profile-new').addEventListener('click',()=>{$('profile-form').reset();$('profile-id').value='';});
$('password-form').addEventListener('submit',e=>{
 e.preventDefault();
 const current=$('password-current').value,newPassword=$('password-new').value;
 if(newPassword!==$('password-confirm').value){alertUser('Nieuwe wachtwoorden komen niet overeen',true);return;}
 action(async()=>{
  await api('/api/change-password','POST',{current,new:newPassword});
  location.replace('/');
 });
});
$('profile-form').addEventListener('submit',e=>{e.preventDefault();action(async()=>{
 const id=$('profile-id').value;
 const data={name:$('profile-name').value,slot:'0',enabled:$('profile-enabled').checked};
 if(id)data.id=Number(id);
 await api('/api/profile','POST',data);await loadProfiles();await loadStatus();$('profile-form').reset();$('profile-id').value='';
},'Profiel opgeslagen');});
$('logs-refresh').addEventListener('click',loadLogs);
$('qa-refresh').addEventListener('click',loadQa);
$('qa-dialog-close').addEventListener('click',()=>$('qa-dialog').close());
$('schedule-dialog-close').addEventListener('click',()=>$('schedule-dialog').close());
$('schedule-cancel').addEventListener('click',()=>$('schedule-dialog').close());
function showScheduleDialog(taskId){
 const task=state.tasks.find(t=>t.id===taskId);
 if(!task)return;
 $('schedule-task').value=taskId;
 $('schedule-description').textContent=task.name+' · '+(state.profiles.find(p=>String(p.id)===state.profileId)?.name||'');
 const now=new Date(Date.now()+60000);now.setSeconds(0,0);
 const localDate=new Date(now.getTime()-now.getTimezoneOffset()*60000).toISOString().slice(0,16);
 $('schedule-when').value=localDate;
 $('schedule-dialog').showModal();
}
$('schedule-form').addEventListener('submit',e=>{
 e.preventDefault();
 const task=$('schedule-task').value,when=$('schedule-when').value;
 action(async()=>{await api('/api/task','POST',{action:'schedule',profileId:state.profileId,task,when});$('schedule-dialog').close();await loadTasks();},'Planning opgeslagen');
});

$('screen-refresh').addEventListener('click',refreshFrame);
$('emu-back').addEventListener('click',()=>action(async()=>{await api('/api/emulator/input','POST',{action:'back'});await refreshFrame();},'Terug uitgevoerd'));
let emuPointer = null;
function emulatorPoint(e, rect){
 return {x:Math.max(0,Math.min(719,Math.floor((e.clientX-rect.left)/rect.width*720))),
         y:Math.max(0,Math.min(1279,Math.floor((e.clientY-rect.top)/rect.height*1280)))};
}
$('emu-image').addEventListener('pointerdown', e=>{
 e.preventDefault();
 const img=e.currentTarget;
 if(img.setPointerCapture)img.setPointerCapture(e.pointerId);
 emuPointer={...emulatorPoint(e,img.getBoundingClientRect()),at:performance.now()};
});
$('emu-image').addEventListener('pointerup',e=>{
 e.preventDefault();
 if(!emuPointer)return;
 const from=emuPointer,to=emulatorPoint(e,e.currentTarget.getBoundingClientRect());
 emuPointer=null;
 if(state.status?.running){alertUser('Stop eerst de bot om het scherm te bedienen',true);return;}
 const distance=Math.hypot(to.x-from.x,to.y-from.y);
 const request=distance>=18
   ?{action:'swipe',x:from.x,y:from.y,toX:to.x,toY:to.y,duration:Math.max(150,Math.min(1200,Math.floor(performance.now()-from.at)))}
   :{action:'tap',x:to.x,y:to.y};
 action(async()=>{await api('/api/emulator/input','POST',request);await refreshFrame();});
});
$('emu-image').addEventListener('pointercancel',()=>{emuPointer=null;});
setInterval(()=>{$('clock').textContent=new Date().toLocaleTimeString('nl-NL',{hour:'2-digit',minute:'2-digit'});},1000);
setInterval(loadStatus,4500);
// The embedded native Serve-Emu browser provides its own continuous WebSocket stream.
// ADB screenshot capture is now manual fallback only, never an automatic 1.8s loop.
setInterval(()=>{if(state.page==='tasks')loadTasks();if(state.page==='logs')loadLogs();if(state.page==='qa')loadQa();if(state.page==='statistics')loadStatistics();},20000);
(async()=>{await loadStatus();await loadProfiles();if(state.profileId)await loadTasks();loadDevice();})().catch(e=>alertUser(e.message,true));
