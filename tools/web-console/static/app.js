const state = { page: 'dashboard', profiles: [], tasks: [], categories: [], profileId: '', settings: [], taskSettings: [], expandedTasks: new Set(), selectedTaskId: '', taskGroup: 'all', taskFilter: 'all', taskSort: 'logical', taskStars: new Set(), taskBusy: new Set(), status: null, emulator: null, lastError: '' };
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const datetime = value => { if (!value) return '—'; const d = new Date(value); return Number.isNaN(+d) ? esc(value) : d.toLocaleString('en-GB', {day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}); };
const plain = v => v == null || v === '' ? '—' : String(v);
const api = async (path, method='GET', data) => {
  const response = await fetch(path, {method, credentials:'same-origin', cache:'no-store', headers:{...(data ? {'Content-Type':'application/json'} : {}), ...(method === 'POST' ? {'X-SurvivalOps-Request':'web'} : {})}, body: data ? JSON.stringify(data) : undefined});
  if (response.status === 401) { location.reload(); throw new Error('Session expired'); }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.error || 'Request failed');
  return result;
};
let noticeTimer;
function alertUser(message, isError=false) { const el=$('notice'); el.hidden=false; el.textContent=message; el.classList.toggle('error',isError); clearTimeout(noticeTimer); noticeTimer=setTimeout(()=>el.hidden=true,5500); }
async function action(fn, success) { try { await fn(); if (success) alertUser(success); } catch(e) { alertUser(e.message,true); } }
function setPage(page) {
  if(page!=='tasks' && state.page==='tasks'){state.selectedTaskId='';document.querySelector('.operations-tasks')?.classList.remove('has-task-detail');}
  state.page = page;
  document.body.classList.toggle('is-emulator-page', page === 'emulator');
  document.querySelectorAll('.nav-btn').forEach(btn=>btn.classList.toggle('active',btn.dataset.page===page));
  document.querySelectorAll('.page').forEach(el=>el.classList.toggle('active',el.dataset.view===page));
  const label = {dashboard:'Overview',tasks:'Task Manager',custom:'Custom Tasks',statistics:'Statistics',emulator:'Emulator',profiles:'Profiles',settings:'Settings',logs:'Logs',qa:'Diagnostics'}[page] || page;
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
    el.innerHTML=(name==='settings-profile'?'<option value="">Global (Serve-Emu)</option>':'')+options;
    if([...el.options].some(o=>o.value===before)) el.value=before;
    else if(name==='tasks-profile'||name==='statistics-profile') el.value=state.profileId || (state.profiles[0]?.id ?? '');
  }
}
async function loadProfiles(){
  state.profiles=await api('/api/profiles');
  if(!state.profiles.some(p=>String(p.id)===String(state.profileId))) state.profileId=String(state.profiles[0]?.id ?? '');
  updateSelects(); renderProfiles();
  $('metric-profiles').textContent=state.profiles.length;
  $('metric-profiles-sub').textContent=state.profiles.filter(p=>p.enabled).length+' active';
}
function renderProfiles(){
  const root=$('profiles-list');
  if(!state.profiles.length){root.innerHTML='<p class="text-muted">No profiles yet. Create your first profile.</p>';return;}
  root.innerHTML=state.profiles.map(p=>'<div class="profile-row"><div><strong>'+esc(p.name)+'</strong><small>Slot '+esc(p.slot)+' · '+(p.enabled?'Active':'Disabled')+(p.queueActive?' · Scheduler connected':'')+'</small></div><div class="actions">'+(p.queueActive?'<button class="secondary small" data-profile-queue="'+esc(p.id)+'" data-queue-action="'+(p.paused?'resume':'pause')+'">'+(p.paused?'Resume':'Pause')+'</button>':'')+'<button class="secondary small" data-edit-profile="'+esc(p.id)+'">Edit</button></div></div>').join('');
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
 $('metric-engine').textContent=busy?'Running':running?'Active':'Stopped';
 $('metric-engine-sub').textContent=busy?'A routine is running':running?'Scheduler active':'Start manually';
 $('queue-count').textContent=s.queues.length+' queues';
 $('queues').innerHTML=s.queues.length?s.queues.map(q=>'<div class="queue-item"><div><strong>'+esc(q.name)+'</strong><small>'+esc(q.next?'Next '+datetime(q.next):'Not scheduled')+'</small></div><span class="pill '+(q.executing?'good':'')+'">'+(q.executing?'Running':q.paused?'Paused':'Waiting')+'</span></div>').join(''):'<span class="text-muted">The scheduler is not running. Start the bot when you have an active profile.</span>';
}
async function loadStatus(){
  try{
    state.status=await api('/api/status');
    engineControls(state.status);
    updateEmulatorInputSafety();
    $('connection').textContent='Connected'; $('connection').className='pill good';
    $('side-status').textContent='Engine connected';document.querySelector('.status-led').classList.add('on');
    $('last-sync').textContent='Updated '+new Date().toLocaleTimeString('en-GB');
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
 DAILY_OVERVIEW_CHECK:['DAILY_OVERVIEW_CHECK_'],ENDLESS_TRIAL:['ENDLESS_TRIAL_'],
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
 ENDLESS_TRIAL_TARGET_ATTACKS_INT:'Daily Windhowler attacks (max 3)',
 DAILY_OVERVIEW_CHECK_OFFSET_INT:'Check interval (minutes)',
 "HERO_RECRUIT_ADVANCED_FREE_BOOL": "Use free Advanced Recruitment",
 "HERO_RECRUIT_EPIC_FREE_BOOL": "Use free Epic Recruitment",
 "HERO_RECRUIT_ADVANCED_KEYS_BOOL": "Use Advanced Recruitment Keys",
 "HERO_RECRUIT_EPIC_KEYS_BOOL": "Use Epic Recruitment Keys",
 "HERO_RECRUIT_POINTS_CHEST_BOOL": "Claim Recruitment Points Chest",
 "GATHER_REMOVE_HEROS_BOOL": "Remove heroes from gathering formation",
 "GATHER_ONLY_FULL_RESOURCES_BOOL": "Gather only from full resource nodes",
 "GATHER_DOWNGRADE_LEVEL_BOOL": "Try lower-level nodes if necessary",
 "GATHER_NO_HERO_FALLBACK_BOOL": "Allow gathering without heroes",
 "CITY_UPGRADE_DOUBLE_TIME_BOOL": "Use double construction speed bonus",
 "CITY_UPGRADE_RESERVE_PRODUCTION_BOOL": "Preserve production bonus",
 "ARENA_TASK_ATTACK_QUICK_DEPLOY_BOOL": "Quick deploy for Arena attacks",
 "ARENA_TASK_REFRESH_WITH_GEMS_BOOL": "Refresh Arena opponents with Gems",
 "ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL": "Use all available troops",
 "ALLIANCE_AUTOJOIN_USE_PREDEFINED_FORMATION_BOOL": "Use preset troop formation",
 "INTEL_RECALL_GATHER_TROOPS_BOOL": "Recall gatherers for Intel",
 "POLAR_TERROR_USE_STAMINA_ITEMS_BOOL": "Use Stamina items",
 "TUNDRA_TRUCK_USE_GEMS_BOOL": "Spend Gems on Tundra Truck",
 "BOOL_DEBUG": "Enable extra debug logging"
};
const taskWords = {
 "CONSTRUCTION": "construction",
 "LOCK": "lock",
 "SPEED": "speed",
 "BOOST": "boost",
 "TO": "to",
 "FOR": "for",
 "WITH": "with",
 "WITHOUT": "without",
 "DURATION": "duration",
 "POINT": "point",
 "GATHERING": "gathering",
 "UPGRADE": "upgrade",
 "MOBILIZATION": "mobilization",
 "BOOL": "",
 "INT": "",
 "LONG": "",
 "STRING": "",
 "ENABLED": "",
 "TASK": "",
 "AUTO": "automatically",
 "USE": "use",
 "ALL": "all",
 "TRAIN": "train",
 "TRAINING": "training",
 "TROOPS": "troops",
 "HEAL": "heal",
 "HEALING": "healing",
 "HERO": "hero",
 "HEROS": "heroes",
 "HEROES": "heroes",
 "RECRUIT": "recruitment",
 "REWARDS": "rewards",
 "CHEST": "chest",
 "CHESTS": "chests",
 "CLAIM": "claim",
 "FREE": "free",
 "ADVANCED": "advanced",
 "EPIC": "epic",
 "KEYS": "keys",
 "POINTS": "points",
 "PERCENTAGE": "percentage",
 "INFANTRY": "infantry",
 "LANCERS": "lancers",
 "MARKSMANS": "marksmen",
 "LIMIT": "limit",
 "SPEEDUPS": "speedups",
 "GEMS": "Gems",
 "STAMINA": "Stamina",
 "RESERVE": "reserve",
 "LEVEL": "level",
 "MARCHES": "marches",
 "QUEUE": "queue",
 "QUEUES": "queues",
 "FLAG": "flag",
 "TARGET": "target",
 "TIME": "time",
 "MINUTES": "minutes",
 "HOURS": "hours",
 "OFFSET": "interval",
 "MAX": "maximum",
 "MIN": "minimum",
 "RECALL": "recall",
 "GATHER": "gather",
 "RESOURCE": "resource",
 "RESOURCES": "resources",
 "AUTOJOIN": "auto-join",
 "DEFEAT": "defeat",
 "BEASTS": "beasts",
 "BUY": "purchase",
 "SKILL": "skill",
 "SKILLS": "skills",
 "ACTIVE": "active",
 "ONLY": "only",
 "RESTARTS": "restarts",
 "CASTS": "casts",
 "CATCHES": "catches",
 "FISHING": "fishing",
 "PRIORITIES": "priorities",
 "PRIORITY": "priority",
 "ACTIVATION": "activation",
 "SCHEDULE": "schedule",
 "DATETIME": "date/time",
 "PREDEFINED": "preset",
 "WEEKLY": "weekly",
 "DAILY": "daily",
 "MONTHLY": "monthly",
 "AVAILABLE": "available",
 "REQUIRE": "require",
 "SPECIAL": "special",
 "HIGHEST": "highest",
 "ITEMS": "items",
 "SCROLL": "scroll",
 "SWIPE": "swipe",
 "ATTEMPTS": "attempts"
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
 return label?label.charAt(0).toUpperCase()+label.slice(1):'Option';
}
function optionControl(task,s){
 const label=optionLabel(task,s.key);
 const id='option-'+task.id+'-'+s.key;
 const help='<small class="task-option-key" title="Technical key">'+esc(s.key)+'</small>';
 if(s.type==='Boolean'){
  return '<label class="task-option toggle-option" for="'+esc(id)+'"><span class="option-name">'+esc(label)+help+'</span><input type="checkbox" id="'+esc(id)+'" data-task-option="'+esc(s.key)+'" data-task-owner="'+esc(task.id)+'" '+(s.value==='true'?'checked':'')+'><span class="switch-track" aria-hidden="true"></span></label>';
 }
 const inputType=['Integer','Long'].includes(s.type)?'number':'text';
 return '<label class="task-option value-option" for="'+esc(id)+'"><span class="option-name">'+esc(label)+help+'</span><span class="value-controls"><input id="'+esc(id)+'" type="'+inputType+'" data-task-value="'+esc(s.key)+'" data-task-owner="'+esc(task.id)+'" value="'+esc(s.value)+'" aria-label="'+esc(label)+'"><button class="secondary small" data-save-task-value="'+esc(s.key)+'" data-task-owner="'+esc(task.id)+'">Save</button></span></label>';
}
// Curated work areas; the original Java task IDs remain authoritative.
const workGroups=[
 {id:'daily',name:'Daily Tasks',icon:'◷',description:'Daily rewards and routine checks',ids:['DAILY_MISSIONS','MAIL_REWARDS','STOREHOUSE_CHEST','INTEL','CITY_SURVIVORS','DAILY_OVERVIEW_CHECK']},
 {id:'city',name:'City & Development',icon:'⌂',description:'Construction, research and Chief Orders',ids:['CITY_UPGRADE_FURNACE','CITY_UPGRADE_PRIORITISE_FURNACE','RESEARCH','CHIEF_ORDER_RUSH_JOB','CHIEF_ORDER_URGENT_MOBILIZATION','CHIEF_ORDER_PRODUCTIVITY_DAY']},
 {id:'army',name:'Troops',icon:'♜',description:'Training and healing',ids:['TRAINING_TROOPS','HEAL_TROOPS']},
 {id:'world',name:'World Map',icon:'◇',description:'Gathering, hunting and marches',ids:['GATHER_RESOURCES','GATHER_BOOST','BEAST_HUNTING','EVENT_POLAR_TERROR','EVENT_BERSERK_CRYPTID']},
 {id:'heroes',name:'Heroes & Exploration',icon:'✧',description:'Recruitment, Arena and exploration',ids:['HERO_RECRUITMENT','ARENA','DO_EXPLORATION','EXPLORATION_CHEST','LABYRINTH','EXPERT_AGNES_INTEL','EXPERT_ROMULUS_TAG','EXPERT_ROMULUS_TROOPS','EXPERT_SKILL_TRAINING']},
 {id:'alliance',name:'Alliance',icon:'⚑',description:'Helps, contributions, rallies and shops',ids:['ALLIANCE_AUTOJOIN','ALLIANCE_HELP_REQUEST','ALLIANCE_TECH','ALLIANCE_CHESTS','ALLIANCE_TRIUMPH','ALLIANCE_CHAMPIONSHIP','ALLIANCE_MOBILIZATION','ALLIANCE_PET_TREASURE','ALLIANCE_SHOP','LIFE_ESSENCE_CARING']},
 {id:'rewards',name:'Shops & Extras',icon:'▤',description:'Merchants, VIP and pets',ids:['SHOP_MYSTERY','NOMADIC_MERCHANT','VIP_POINTS','BANK','CRYSTAL_LABORATORY','WAR_ACADEMY','LIFE_ESSENCE','PET_SKILLS','PET_ADVENTURE']},
 {id:'events',name:'Events',icon:'✳',description:'Limited-time and special events',ids:['BEAR_TRAP','EVENT_TUNDRA_TRUCK','FISHING_MINIGAME','EVENT_HERO_MISSION','EVENT_JOURNEY_OF_LIGHT','EVENT_MYRIAD_BAZAAR','ENDLESS_TRIAL','MERCENARY_EVENT','TREK_AUTOMATION','TREK_SUPPLIES']},
 {id:'system',name:'System & Tests',icon:'⌘',description:'Technical and support routines',ids:['INITIALIZE','CREATE_CHARACTER','SKIP_TUTORIAL','DUMMY_TASK','TEST_HOOK_LOOP','CUSTOM_TASK','GAME_ANALYTICS_LABYRINTH','GAME_ANALYTICS_POWER']}
];
const workLabels = {
 "DAILY_OVERVIEW_CHECK": [
  "Daily Overview Check",
  "Inspect the Daily tab for available tasks without spending resources."
 ],
 "ENDLESS_TRIAL": [
  "Endless Trial",
  "Attack Windhowler up to three times, then claim all daily challenges with one Claim."
 ],
 "DAILY_MISSIONS": [
  "Daily Missions",
  "Claim rewards from completed Daily Missions."
 ],
 "MAIL_REWARDS": [
  "Mail Rewards",
  "Collect available rewards from your mailbox."
 ],
 "STOREHOUSE_CHEST": [
  "Storehouse Chest",
  "Check and claim available Storehouse Chests."
 ],
 "INTEL": [
  "Intel Missions",
  "Complete Intel Missions at the Lighthouse."
 ],
 "CITY_SURVIVORS": [
  "Welcome Survivors",
  "Welcome new Survivors and assign them to buildings."
 ],
 "CITY_UPGRADE_FURNACE": [
  "Furnace Upgrade",
  "Upgrade the Furnace and complete its prerequisites."
 ],
 "CITY_UPGRADE_PRIORITISE_FURNACE": [
  "Prioritize Furnace",
  "Give Furnace construction the highest priority."
 ],
 "RESEARCH": [
  "Research",
  "Start available Academy research upgrades."
 ],
 "CHIEF_ORDER_RUSH_JOB": [
  "Chief Order · Rush Job",
  "Activate Rush Job when available."
 ],
 "CHIEF_ORDER_URGENT_MOBILIZATION": [
  "Chief Order · Urgent Mobilization",
  "Activate Urgent Mobilization when available."
 ],
 "CHIEF_ORDER_PRODUCTIVITY_DAY": [
  "Chief Order · Productivity Day",
  "Activate Productivity Day when available."
 ],
 "TRAINING_TROOPS": [
  "Troop Training",
  "Collect finished training and train new troops."
 ],
 "HEAL_TROOPS": [
  "Heal Troops",
  "Heal wounded troops in the Infirmary."
 ],
 "GATHER_RESOURCES": [
  "Gather Resources",
  "Dispatch marches to resource nodes on the World Map."
 ],
 "GATHER_BOOST": [
  "Gathering Speed Boost",
  "Apply configured gathering speed bonuses."
 ],
 "BEAST_HUNTING": [
  "Beast Hunting",
  "Hunt Beasts on the World Map."
 ],
 "EVENT_POLAR_TERROR": [
  "Polar Terror",
  "Attack Polar Terrors using your configured strategy."
 ],
 "EVENT_BERSERK_CRYPTID": [
  "Join Rallies",
  "Join available Alliance Rallies."
 ],
 "HERO_RECRUITMENT": [
  "Hero Recruitment",
  "Use free recruitment attempts and optional keys."
 ],
 "ARENA": [
  "Arena",
  "Complete Arena challenges and claim rewards."
 ],
 "DO_EXPLORATION": [
  "Exploration",
  "Progress through Exploration and claim rewards."
 ],
 "EXPLORATION_CHEST": [
  "Exploration Chest",
  "Open available Exploration reward chests."
 ],
 "LABYRINTH": [
  "Labyrinth",
  "Complete the daily Labyrinth routine."
 ],
 "EXPERT_AGNES_INTEL": [
  "Expert Agnes · Intel",
  "Use the configured Expert Agnes Intel options."
 ],
 "EXPERT_ROMULUS_TAG": [
  "Expert Romulus · Tag",
  "Use the configured Expert Romulus options."
 ],
 "EXPERT_ROMULUS_TROOPS": [
  "Expert Romulus · Troops",
  "Activate Romulus troop bonuses."
 ],
 "EXPERT_SKILL_TRAINING": [
  "Expert Skill Training",
  "Train the selected Expert Skills."
 ],
 "ALLIANCE_AUTOJOIN": [
  "Alliance Auto-Join",
  "Automatically join eligible Alliance Rallies."
 ],
 "ALLIANCE_HELP_REQUEST": [
  "Alliance Help",
  "Help Alliance members with their requests."
 ],
 "ALLIANCE_TECH": [
  "Alliance Tech",
  "Make contributions to Alliance Technology."
 ],
 "ALLIANCE_CHESTS": [
  "Alliance Chests",
  "Claim available Alliance Chests."
 ],
 "ALLIANCE_TRIUMPH": [
  "Alliance Triumph",
  "Claim available Alliance Triumph rewards."
 ],
 "ALLIANCE_CHAMPIONSHIP": [
  "Alliance Championship",
  "Manage Alliance Championship participation."
 ],
 "ALLIANCE_MOBILIZATION": [
  "Alliance Mobilization",
  "Complete your selected Mobilization missions."
 ],
 "ALLIANCE_PET_TREASURE": [
  "Alliance Pet Treasure",
  "Claim available Alliance pet rewards."
 ],
 "ALLIANCE_SHOP": [
  "Alliance Shop",
  "Purchase items using your configured preferences."
 ],
 "LIFE_ESSENCE_CARING": [
  "Life Essence · Alliance",
  "Manage Alliance-related Life Essence care."
 ],
 "SHOP_MYSTERY": [
  "Mystery Shop",
  "Check offers in the Mystery Shop."
 ],
 "NOMADIC_MERCHANT": [
  "Nomadic Merchant",
  "Visit the Nomadic Merchant and select purchases."
 ],
 "VIP_POINTS": [
  "VIP Points",
  "Collect available VIP Points."
 ],
 "BANK": [
  "Bank",
  "Run your configured Bank routine."
 ],
 "CRYSTAL_LABORATORY": [
  "Crystal Laboratory",
  "Collect available Crystal Laboratory rewards."
 ],
 "WAR_ACADEMY": [
  "War Academy",
  "Process War Academy rewards."
 ],
 "LIFE_ESSENCE": [
  "Life Essence",
  "Care for pets and optionally use Pet Skill Scrolls."
 ],
 "PET_SKILLS": [
  "Pet Skills",
  "Activate your selected Pet Skills."
 ],
 "PET_ADVENTURE": [
  "Pet Adventure",
  "Run your configured Pet Adventures."
 ],
 "BEAR_TRAP": [
  "Bear Trap",
  "Manage participation in the Bear Trap event."
 ],
 "EVENT_TUNDRA_TRUCK": [
  "Tundra Truck",
  "Complete your configured Tundra Truck activities."
 ],
 "FISHING_MINIGAME": [
  "Fishing Tournament",
  "Use the configured Fishing Tournament casts."
 ],
 "EVENT_HERO_MISSION": [
  "Hero Mission",
  "Complete limited-time Hero Missions."
 ],
 "EVENT_JOURNEY_OF_LIGHT": [
  "Journey of Light",
  "Manage the Journey of Light event."
 ],
 "EVENT_MYRIAD_BAZAAR": [
  "Myriad Bazaar",
  "Complete your configured Myriad Bazaar activities."
 ],
 "MERCENARY_EVENT": [
  "Mercenary Event",
  "Complete the Mercenary event."
 ],
 "TREK_AUTOMATION": [
  "Tundra Trek",
  "Automate the selected Tundra Trek activities."
 ],
 "TREK_SUPPLIES": [
  "Trek Supplies",
  "Claim available Trek Supplies."
 ],
 "INITIALIZE": [
  "Initialize",
  "Internal bot initialization routine."
 ],
 "CREATE_CHARACTER": [
  "Create Character",
  "Create a new game character."
 ],
 "SKIP_TUTORIAL": [
  "Skip Tutorial",
  "Skip the introductory tutorial for a new account."
 ],
 "DUMMY_TASK": [
  "Dummy Task",
  "Technical test routine."
 ],
 "TEST_HOOK_LOOP": [
  "Test Loop",
  "Technical routine, not intended for normal use."
 ],
 "CUSTOM_TASK": [
  "Custom Tasks",
  "Run user-created Java routines."
 ],
 "GAME_ANALYTICS_LABYRINTH": [
  "Labyrinth Analytics",
  "Collect Alliance Labyrinth ranking statistics."
 ],
 "GAME_ANALYTICS_POWER": [
  "Power Analytics",
  "Collect Alliance Power ranking statistics."
 ]
};
const workAliases = {
 "HERO_RECRUITMENT": "recruit recruit keys heroes epic advanced",
 "DAILY_OVERVIEW_CHECK": "daily overview sidebar availability missions status",
 "ENDLESS_TRIAL": "windhowler endless trial daily challenges three attacks claim rewards",
 "DAILY_MISSIONS": "daily missions quests tasks",
 "MAIL_REWARDS": "mail post rewards",
 "STOREHOUSE_CHEST": "chest chests storehouse rewards",
 "ALLIANCE_CHESTS": "alliance chests rewards",
 "GATHER_RESOURCES": "farm farms gather wood meat iron coal",
 "GATHER_BOOST": "gather speed buff boost",
 "HEAL_TROOPS": "heal healing infirmary wounded",
 "TRAINING_TROOPS": "train training infantry marksmen lancers",
 "CITY_UPGRADE_FURNACE": "furnace build upgrade construction",
 "CITY_SURVIVORS": "survivors welcome allocate assign workers",
 "ALLIANCE_AUTOJOIN": "rally rallies join alliance autojoin",
 "ALLIANCE_TECH": "contribution technology alliance tech",
 "DO_EXPLORATION": "exploration stages rewards",
 "EXPLORATION_CHEST": "exploration chests rewards",
 "EVENT_TUNDRA_TRUCK": "tundra truck event",
 "ARENA": "arena challenges chest rewards",
 "INTEL": "lighthouse intel hero journey beast",
 "LIFE_ESSENCE": "life essence pet care",
 "FISHING_MINIGAME": "fishing tournament casts",
 "BEAR_TRAP": "bear trap rally event"
};
const workIds=new Map(workGroups.flatMap(group=>group.ids.map(id=>[id,group.id])));
const workSortOrder=new Map(workGroups.flatMap(group=>group.ids.map((id,index)=>[id,index])));
function workGroup(t){return workIds.get(t.id)||'system';}
function workName(t){return workLabels[t.id]?.[0]||t.name;}
function workHelp(t){return workLabels[t.id]?.[1]||'Settings and execution for this routine.';}
function taskStarsKey(){return 'survivalops.web.stars.'+state.profileId;}
function loadTaskStars(){try{const ids=JSON.parse(localStorage.getItem(taskStarsKey())||'[]');state.taskStars=new Set(Array.isArray(ids)?ids:[]);}catch(_){state.taskStars=new Set();}}
function saveTaskStars(){try{localStorage.setItem(taskStarsKey(),JSON.stringify([...state.taskStars]));}catch(_){/* Restricted/private browser mode */}}
function workStatus(t){
 if(t.executing)return {label:'Running',kind:'running',priority:0};
 if(t.outcome==='FAILED')return {label:'Failed',kind:'failed',priority:1};
 if(t.outcome==='SUCCEEDED')return {label:'Succeeded',kind:'success',priority:2};
 if(t.queued)return {label:'Queued',kind:'queued',priority:3};
 if(t.outcome==='RUNNING')return {label:'Recently started',kind:'running',priority:0};
 return {label:'No result yet',kind:'idle',priority:4};
}
function workMatched(t){
 const text=$('tasks-search').value.trim().toLocaleLowerCase('en-GB');
 if(text && ![workName(t),workHelp(t),workAliases[t.id],t.name,t.id,workGroups.find(g=>g.id===workGroup(t))?.name]
      .some(s=>String(s||'').toLocaleLowerCase('en-GB').includes(text)))return false;
 if(state.taskFilter==='enabled'&&!t.enabled)return false;
 if(state.taskFilter==='issues'&&t.outcome!=='FAILED')return false;
 if(state.taskFilter==='favorites'&&!state.taskStars.has(t.id))return false;
 return true;
}
function taskSetSelection(id){
 state.selectedTaskId=id;
 document.querySelector('.operations-tasks')?.classList.add('has-task-detail');
 renderTasks();
 renderTaskDetail();
}
function closeTaskDetail(){
 state.selectedTaskId='';
 document.querySelector('.operations-tasks')?.classList.remove('has-task-detail');
 renderTasks();renderTaskDetail();
}
function renderTaskNavigation(){
 const relevant=state.tasks.filter(workMatched);
 const allEnabled=relevant.filter(t=>t.enabled).length;
 const allErrors=relevant.filter(t=>t.outcome==='FAILED').length;
 const counters=state.tasks.filter(t=>t.toggleable).length;
 $('task-summary').innerHTML='<span><b>'+state.tasks.length+'</b> routines</span><span><i class="work-dot is-on"></i><b>'+state.tasks.filter(t=>t.enabled).length+'</b> enabled</span><span><i class="work-dot is-error"></i><b>'+state.tasks.filter(t=>t.outcome==='FAILED').length+'</b> failed</span><span class="work-summary-note">'+counters+' manually configurable · '+allEnabled+' enabled in selection'+(allErrors?' · '+allErrors+' failed in selection':'')+'</span>';
 $('task-categories').innerHTML=[{id:'all',name:'All Areas',icon:'▦',description:''},...workGroups].map(group=>{
  const total=relevant.filter(t=>group.id==='all'||workGroup(t)===group.id).length;
  const enabled=relevant.filter(t=>(group.id==='all'||workGroup(t)===group.id)&&t.enabled).length;
  return '<button type="button" class="work-group'+(state.taskGroup===group.id?' is-current':'')+'" data-task-group="'+esc(group.id)+'" aria-current="'+(state.taskGroup===group.id?'true':'false')+'"><span class="work-group-symbol">'+esc(group.icon)+'</span><span>'+esc(group.name)+'</span><small>'+enabled+'/'+total+'</small></button>';
 }).join('');
 document.querySelectorAll('[data-task-filter]').forEach(btn=>{
  const selected=btn.dataset.taskFilter===state.taskFilter;
  btn.classList.toggle('is-active',selected);btn.setAttribute('aria-pressed',String(selected));
 });
}
async function loadTasks(){
 const profileId=state.profileId;
 if(!profileId){$('task-list').textContent='Create a profile first.';return;}
 try{
  const [tasks,settings]=await Promise.all([api('/api/tasks?profile='+encodeURIComponent(profileId)),api('/api/settings?profile='+encodeURIComponent(profileId))]);
  if(profileId!==state.profileId)return;
  state.tasks=tasks;state.taskSettings=settings;
  $('metric-tasks').textContent=tasks.filter(t=>t.enabled).length+' / '+tasks.length;
  $('metric-tasks-sub').textContent='Enabled routines';
  renderTasks();
  const editing=document.activeElement?.closest('.work-inspector [data-task-options]');
  if(!editing)renderTaskDetail();
 }catch(e){$('task-list').textContent=e.message;}
}
function renderTasks(){
 renderTaskNavigation();
 const groups=state.taskGroup==='all'?workGroups:workGroups.filter(g=>g.id===state.taskGroup);
 const relevant=state.tasks.filter(workMatched);
 const listing=groups.flatMap(g=>relevant.filter(t=>workGroup(t)===g.id));
 $('task-list-title').textContent=state.taskGroup==='all'?'All Routines':(workGroups.find(g=>g.id===state.taskGroup)?.name||'Routines');
 $('task-visible-count').textContent=listing.length+' shown';
 let html='';
 for(const group of groups){
  let items=relevant.filter(t=>workGroup(t)===group.id);
  if(!items.length)continue;
  if($('task-sort').value==='name')items=items.sort((a,b)=>workName(a).localeCompare(workName(b),'en-GB'));
  else if($('task-sort').value==='state')items=items.sort((a,b)=>workStatus(a).priority-workStatus(b).priority||workName(a).localeCompare(workName(b),'en-GB'));
  else items=items.sort((a,b)=>(workSortOrder.get(a.id)??999)-(workSortOrder.get(b.id)??999));
  html+='<div class="work-section"><div class="work-section-header"><div><span class="work-section-icon">'+esc(group.icon)+'</span><strong>'+esc(group.name)+'</strong><small>'+esc(group.description)+'</small></div><span>'+items.filter(t=>t.enabled).length+' / '+items.length+' enabled</span></div>';
  for(const t of items){
   const status=workStatus(t),selected=state.selectedTaskId===t.id;
   const note=t.executing?'Currently running':t.lastAttempt?'Last '+datetime(t.lastAttempt):'Not run yet';
   const next=t.enabled?(t.next?'Next '+datetime(t.next):'Not scheduled'):'Disabled';
   const opts=taskBehavior(t).length;
   html+='<div class="work-task'+(selected?' is-selected':'')+(t.enabled?' is-enabled':'')+'" data-task-row="'+esc(t.id)+'">'
     +'<button class="work-star'+(state.taskStars.has(t.id)?' is-starred':'')+'" type="button" data-star-task="'+esc(t.id)+'" title="'+(state.taskStars.has(t.id)?'Remove from favorites':'Add to favorites')+'" aria-label="Favorite: '+esc(workName(t))+'" aria-pressed="'+state.taskStars.has(t.id)+'">'+(state.taskStars.has(t.id)?'★':'☆')+'</button>'
     +'<button class="work-task-open" type="button" data-open-task="'+esc(t.id)+'" aria-label="Open settings for '+esc(workName(t))+'"><strong>'+esc(workName(t))+'</strong><small>'+esc(workHelp(t))+'</small></button>'
     +'<span class="work-state is-'+status.kind+'"><i></i>'+status.label+'</span>'
     +'<span class="work-task-timing" title="'+esc(note)+'">'+esc(next)+'</span>'
     +'<label class="work-main-toggle" title="'+(t.toggleable?'Enable or disable routine':'Internal routine; cannot be enabled manually')+'"><input type="checkbox" data-task-toggle="'+esc(t.id)+'" aria-label="'+esc(workName(t))+' enable or disable" '+(t.enabled?'checked ':'')+(t.toggleable?'':'disabled ')+(state.taskBusy.has(t.id)?'disabled ':'')+'><span></span></label>'
     +'<button type="button" class="work-row-arrow" data-open-task="'+esc(t.id)+'" aria-label="Details for '+esc(workName(t))+'">'+(opts?'⚙':'›')+'</button></div>';
  }
  html+='</div>';
 }
 $('task-list').innerHTML=html||'<div class="work-no-results"><strong>No routines found</strong><p>Try another search term, filter or work area.</p><button type="button" data-clear-task-filters>Clear filters</button></div>';
}
function renderTaskDetail(){
 const t=state.tasks.find(t=>t.id===state.selectedTaskId);
 if(!t){$('task-detail-content').innerHTML='<div class="work-empty-detail"><span>↗</span><strong>Select a routine</strong><p>Open a routine to view its settings, schedule and latest result.</p></div>';return;}
 const status=workStatus(t),group=workGroups.find(g=>g.id===workGroup(t));
 const settings=taskBehavior(t);
 const booleans=settings.filter(s=>s.type==='Boolean'), values=settings.filter(s=>s.type!=='Boolean');
 const optionSection=(title,items)=>items.length?'<div class="work-option-section"><h4>'+esc(title)+'</h4><div class="task-option-grid">'+items.map(s=>optionControl(t,s)).join('')+'</div></div>':'';
 const last=t.lastAttempt?'Last attempt: '+datetime(t.lastAttempt):'No execution recorded';
 const successful=t.lastSuccess?'Last success: '+datetime(t.lastSuccess):'No success recorded';
 const options=booleans.length+values.length;
 $('task-detail-content').innerHTML='<header class="work-detail-head"><span>'+esc(group?.name||'Routine')+'</span><button type="button" data-close-task aria-label="Close details">×</button></header>'
   +'<div class="work-detail-title"><h3>'+esc(workName(t))+'</h3><p>'+esc(workHelp(t))+'</p></div>'
   +'<div class="work-detail-status"><span class="work-state is-'+status.kind+'"><i></i>'+esc(status.label)+'</span><span>'+(t.enabled?'Enabled':'Disabled')+'</span></div>'
   +'<div class="work-detail-activation"><div><strong>Run automatically</strong><small>Runs when the bot is active and this routine is due.</small></div><label class="work-main-toggle"><input type="checkbox" data-task-toggle="'+esc(t.id)+'" aria-label="Run routine automatically" '+(t.enabled?'checked ':'')+(t.toggleable?'':'disabled ')+(state.taskBusy.has(t.id)?'disabled ':'')+'><span></span></label></div>'
   +'<div class="work-detail-actions"><button type="button" class="primary" data-run-task="'+esc(t.id)+'" '+(!t.enabled?'disabled ':'')+'>▶ Run Now</button><button type="button" class="secondary" data-schedule-task="'+esc(t.id)+'" '+(!t.enabled?'disabled ':'')+'>◷ Schedule</button></div>'
   +'<div class="work-detail-block"><h4>EXECUTION</h4><dl><div><dt>Last attempt</dt><dd>'+esc(t.lastAttempt?datetime(t.lastAttempt):'Never')+'</dd></div><div><dt>Last success</dt><dd>'+esc(t.lastSuccess?datetime(t.lastSuccess):'Never')+'</dd></div><div><dt>Next run</dt><dd>'+esc(t.next?datetime(t.next):'Not scheduled')+'</dd></div></dl>'+(t.outcome==='FAILED'&&t.detail?'<p class="work-error-detail">'+esc(t.detail.slice(0,500))+'</p>':'')+'</div>'
   +'<div class="work-detail-options" data-task-options="'+esc(t.id)+'"><div class="work-detail-section-head"><h4>BEHAVIOR & OPTIONS</h4><span>'+options+' options</span></div>'
   +(settings.length?optionSection('Switches',booleans)+optionSection('Values and Limits',values):'<p class="work-no-options">This routine has no additional options. Use the switch above to enable or disable it.</p>')
   +'</div><p class="work-detail-help">Changes are saved to the selected profile immediately. Updating settings will not start a routine.</p>';
}
function clearTaskFilters(){
 state.taskGroup='all';state.taskFilter='all';$('tasks-search').value='';$('task-sort').value='logical';renderTasks();
}

async function saveTaskOption(key,value,profileId){
 await api('/api/setting','POST',{profileId,key,value});
 if(profileId===state.profileId){
  const s=state.taskSettings.find(s=>s.key===key);if(s)s.value=value;
 }
 alertUser('Routine option saved');
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
   const field=s.type==='Boolean'?'<select data-setting-value="'+esc(s.key)+'"><option value="true" '+(s.value==='true'?'selected':'')+'>On</option><option value="false" '+(s.value==='false'?'selected':'')+'>Off</option></select>':
     '<input data-setting-value="'+esc(s.key)+'" '+(s.secret?'type="password" autocomplete="new-password" placeholder="New value (current value hidden)"':'type="'+(s.type==='Integer'?'number':'text')+'" value="'+esc(s.value)+'"')+' >';
   return '<div class="setting-row"><label for="'+id+'">'+esc(s.key)+'<small>'+esc(s.type)+(s.secret?' · protected':'')+'</small></label>'+field+'<button class="secondary small" data-save-setting="'+esc(s.key)+'">Save</button></div>';
 }).join('')||'<div class="panel-content text-muted">No settings found.</div>';
}
async function saveSetting(key){
 const input=[...document.querySelectorAll('[data-setting-value]')].find(i=>i.dataset.settingValue===key);
 if(!input)return;
 await api('/api/setting','POST',{profileId:$('settings-profile').value,key,value:input.value});
 if(input.type==='password')input.value='';
 alertUser('Setting saved: '+key);
}
function updateEmulatorInputSafety(){
 const shield=$('emu-input-shield'),frame=$('emu-live');
 if(!shield||!frame)return;
 const busy=Boolean(state.status?.running||state.status?.executing);
 const external=Boolean(state.emulator?.externalControllerLikelyActive);
 const blocked=busy||external;
 shield.hidden=!blocked;
 frame.inert=blocked;
 frame.style.pointerEvents=blocked?'none':'auto';
 frame.tabIndex=blocked?-1:0;
 if(blocked)frame.blur();
 if(blocked){
  shield.querySelector('strong').textContent=busy?'Bot is controlling the emulator':'Another controller may be active';
  shield.querySelector('span:last-child').textContent=busy?'Stop automation to use the screen here.':'Manual control is temporarily paused while another session uses Serve-Emu.';
 }
}
async function loadDevice(){
 try {
  state.emulator=await api('/api/emulator/state');
  $('emu-connection').textContent=state.emulator.connected?'ADB connected':'ADB offline';
  $('emu-connection').className='pill '+(state.emulator.connected?'good':'bad');
  $('metric-device').textContent=state.emulator.connected?'Online':'Offline';
  $('metric-device-sub').textContent=state.emulator.endpoint || 'Serve-Emu';
  updateEmulatorInputSafety();
 } catch(e){
  $('emu-connection').textContent='Disconnected';$('emu-connection').className='pill bad';
  $('metric-device').textContent='Offline';$('metric-device-sub').textContent=e.message;
  updateEmulatorInputSafety();
 }
}
async function loadLogs(){
 try{const result=await api('/api/logs');$('logs-body').textContent=(result.lines||[]).join('\n')||result.note||'No logs available';}catch(e){$('logs-body').textContent=e.message;}
}
async function loadStatistics(){
 const id=$('statistics-profile').value||state.profileId;
 if(!id)return;
 try{
  const report=await api('/api/statistics?profile='+encodeURIComponent(id));
  const all=report.routines||[],total=report.totalRuns||0;
  const ocr=all.reduce((a,t)=>a+t.ocrErrors,0),matches=all.reduce((a,t)=>a+t.matchErrors,0);
  $('statistics-stats').innerHTML=[['Runs',total],['Distinct routines',all.length],['OCR errors',ocr],['Recognition errors',matches]].map(([k,v])=>'<article class="metric"><span>'+esc(k)+'</span><strong>'+esc(v)+'</strong></article>').join('');
  $('statistics-routines').innerHTML=all.map(t=>'<div class="stats-row"><div><strong>'+esc(t.name||t.key)+'</strong><small>Latest: '+esc(t.last||'—')+'</small></div><div><strong>'+esc(t.runs)+'×</strong><small>Avg. '+(t.runs?Math.round(t.totalMs/t.runs/1000):0)+'s</small></div><div><span class="outcome '+(t.ocrErrors+t.matchErrors?'bad':'good')+'">'+esc(t.ocrErrors+t.matchErrors)+' errors</span></div></div>').join('')||'<p class="text-muted">No runs recorded for this profile.</p>';
  $('statistics-counters').innerHTML=Object.entries(report.counters||{}).map(([k,v])=>'<div class="detail-row"><span>'+esc(k)+'</span><strong>'+esc(v)+'</strong></div>').join('')||'<p class="text-muted">No additional counters.</p>';
 }catch(e){$('statistics-routines').textContent=e.message;}
}
let builderSteps = [];
const builderFields = {
 TAP_POINT: [['x','X',360,0,719],['y','Y',640,0,1279]],
 WAIT: [['durationMs','Wait (ms)',750,100,30000]],
 SWIPE: [['startX','Start X',360,0,719],['startY','Start Y',900,0,1279],
         ['endX','End X',360,0,719],['endY','End Y',300,0,1279]],
 BACK_BUTTON: []
};
const builderLabels={TAP_POINT:'Tap Point',WAIT:'Wait',SWIPE:'Swipe',BACK_BUTTON:'Back Button'};
function renderBuilder(){
 $('builder-steps').innerHTML=builderSteps.map((step,index)=>{
  const fields=builderFields[step.type]||[];
  return '<div class="builder-step"><strong>'+(index+1)+'. '+esc(builderLabels[step.type])+'</strong><div class="inputs">'+fields.map(([key,label,value,min,max])=>'<label>'+esc(label)+'<input type="number" min="'+min+'" max="'+max+'" required data-step-field="'+key+'" data-step-index="'+index+'" value="'+esc(step[key]??value)+'"></label>').join('')+'</div><button type="button" class="danger small" data-remove-step="'+index+'" aria-label="Remove step">×</button></div>';
 }).join('')||'<p class="text-muted">Add at least one step to the workflow.</p>';
}
async function loadCustom(){
 try{
  const all=await api('/api/custom-tasks');
  $('custom-task-list').innerHTML=all.map(t=>{
   const id=esc(t.className);
   return '<form class="custom-row" data-custom-form="'+id+'"><div><strong>'+id+'</strong><small>'+(t.loaded?'Available in Java engine':'Not loaded')+'</small></div><label>Minutes between runs<input type="number" name="offsetMinutes" min="0" max="10080" value="'+esc(t.offsetMinutes)+'"></label><label>Priority<input type="number" name="priority" min="-1000" max="1000" value="'+esc(t.priority)+'"></label><label>Follow-up after hours<input type="number" name="followUpDelayHours" min="1" max="720" value="'+esc(t.followUpDelayHours??8)+'"></label><div class="custom-actions"><span class="pill '+(t.enabled?'good':'')+'">'+(t.enabled?'Enabled':'Disabled')+'</span><button class="secondary small" type="submit" data-custom-action="save" '+(!t.loaded?'disabled':'')+'>Save</button><button class="'+(t.enabled?'danger':'primary')+' small" type="submit" data-custom-action="'+(t.enabled?'disable':'enable')+'" '+(!t.loaded?'disabled':'')+'>'+(t.enabled?'Disable':'Enable')+'</button></div></form>';
  }).join('')||'<p class="text-muted">No custom tasks imported into this web workspace yet. The 63 built-in routines are in Task Manager.</p>';
 }catch(e){$('custom-task-list').textContent=e.message;}
}
async function loadQa(){
 try{
  const data=await api('/api/qa/summary');
  const counter=data.status_counts||{};
  const stats=[['Tracked routines',data.total_tasks||0],['Verified',counter.verified||0],['Not completed',(counter.testing||0)+(counter.working||0)+(counter.broken||0)+(counter.blocked||0)],['Diagnostic uploads',data.diagnostic_total||0]];
  $('qa-stats').innerHTML=stats.map(([k,v])=>'<div class="metric"><span>'+esc(k)+'</span><strong>'+esc(v)+'</strong></div>').join('');
  $('qa-issues').innerHTML=(data.open_issues||[]).map(i=>'<div class="qa-item"><span class="pill '+(i.severity==='high'?'bad':'')+'">'+esc(i.severity)+'</span><div><strong>'+esc(i.title)+'</strong><small>'+esc(i.label||i.task_key||'System')+' · '+datetime(i.created_at)+'</small></div></div>').join('')||'<p class="text-muted">No open QA issues.</p>';
  $('qa-diagnostics').innerHTML=(data.diagnostics||[]).map(d=>'<button class="qa-item qa-diagnostic" data-diagnostic="'+esc(d.path)+'"><span class="pill '+(d.severity==='error'?'bad':'')+'">'+esc(d.severity)+'</span><div><strong>'+esc(d.task_label||d.task_key||d.activity||'System')+'</strong><small>'+esc(d.kind)+' · '+datetime(d.timestamp)+(d.has_image?' · Screenshot':'')+'</small></div><span class="muted">›</span></button>').join('')||'<p class="text-muted">No diagnostic uploads yet.</p>';
 }catch(e){$('qa-stats').textContent=e.message;$('qa-issues').textContent='Archive unavailable';$('qa-diagnostics').textContent='Archive unavailable';}
}
async function showQa(path){
 try{
  const d=await api('/api/qa/detail?path='+encodeURIComponent(path));
  $('qa-dialog-title').textContent=d.activity||d.kind||'Diagnostics';
  $('qa-dialog-info').textContent=[d.task_key,d.kind,d.severity,d.timestamp].filter(Boolean).join(' · ');
  const img=$('qa-dialog-image');img.hidden=!d.has_image;
  if(d.has_image)img.src='/api/qa/image?path='+encodeURIComponent(path);
  else img.removeAttribute('src');
  $('qa-dialog-logs').textContent=(d.log_lines||[]).join('\n')||'No log excerpt available';
  $('qa-dialog').showModal();
 }catch(e){alertUser(e.message,true);}
}
document.addEventListener('click',e=>{
 const schedule=e.target.closest('[data-schedule-task]');if(schedule)showScheduleDialog(schedule.dataset.scheduleTask);
 const q=e.target.closest('[data-profile-queue]');if(q)action(async()=>{await api('/api/profile-queue','POST',{profileId:q.dataset.profileQueue,action:q.dataset.queueAction});await loadProfiles();},'Queue updated');
 const diag=e.target.closest('[data-diagnostic]');if(diag)showQa(diag.dataset.diagnostic);
 const edit=e.target.closest('[data-edit-profile]');if(edit)editProfile(edit.dataset.editProfile);
 const go=e.target.closest('[data-go]');if(go)setPage(go.dataset.go);
 const run=e.target.closest('[data-run-task]');if(run)action(async()=>{await api('/api/task','POST',{action:'run',profileId:state.profileId,task:run.dataset.runTask});await loadTasks();},'Routine added to the queue');
 const setting=e.target.closest('[data-save-setting]');if(setting)action(()=>saveSetting(setting.dataset.saveSetting));
 const close=e.target.closest('[data-close-task]');if(close)closeTaskDetail();
 const open=e.target.closest('[data-open-task]');if(open)taskSetSelection(open.dataset.openTask);
 const group=e.target.closest('[data-task-group]');if(group){state.taskGroup=group.dataset.taskGroup;renderTasks();}
 const filter=e.target.closest('[data-task-filter]');if(filter){state.taskFilter=filter.dataset.taskFilter;renderTasks();}
 const star=e.target.closest('[data-star-task]');if(star){const id=star.dataset.starTask;if(state.taskStars.has(id))state.taskStars.delete(id);else state.taskStars.add(id);saveTaskStars();renderTasks();}
 const clear=e.target.closest('[data-clear-task-filters]');if(clear)clearTaskFilters();
 const saveTask=e.target.closest('[data-save-task-value]');if(saveTask){
   const input=saveTask.parentElement.querySelector('input[data-task-value]');
   if(input && input.checkValidity()){
     const profileId=state.profileId,key=input.dataset.taskValue;
     saveTask.disabled=true;
     action(async()=>{try{await saveTaskOption(key,input.value,profileId);}finally{saveTask.disabled=false;}});
   }else if(input)input.reportValidity();
 }
});
document.addEventListener('change',e=>{
 const task=e.target.closest('[data-task-toggle]');
 if(task){
   const profileId=state.profileId,enabled=task.checked,id=task.dataset.taskToggle;
   if(state.taskBusy.has(id))return;
   state.taskBusy.add(id);task.disabled=true;
   action(async()=>{
     try{
       await api('/api/task','POST',{action:'toggle',profileId,task:id,enabled});
       if(profileId===state.profileId){const model=state.tasks.find(t=>t.id===id);if(model)model.enabled=enabled;}
       await loadTasks();
     }catch(error){task.checked=!enabled;throw error;}
     finally{state.taskBusy.delete(id);if(profileId===state.profileId){renderTasks();renderTaskDetail();}}
   },'Routine '+(enabled?'enabled':'disabled'));
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
 if(!confirm('Reset all recorded statistics for this profile?'))return;
 action(async()=>{
  await api('/api/statistics','POST',{action:'reset',profileId:$('statistics-profile').value});
  await loadStatistics();
 },'Profile statistics reset');
});
document.querySelectorAll('[data-add-step]').forEach(button=>button.addEventListener('click',()=>{
 const type=button.dataset.addStep;
 if(!builderFields[type])return;
 if(builderSteps.length>=48){alertUser('Maximum of 48 steps',true);return;}
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
 if(!builderSteps.length){alertUser('Add at least one step',true);return;}
 action(async()=>{
  const data=await api('/api/builder','POST',{
   name:$('builder-name').value.trim(),startLocation:$('builder-location').value,
   steps:builderSteps
  });
  $('builder-form').reset();builderSteps=[];renderBuilder();await loadCustom();
  alertUser('Java-workflow '+data.className+' compiled. Enable it explicitly before starting the bot.');
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
 if(command==='delete'&&!confirm('Delete this custom task and its associated files?'))return;
 action(async()=>{
  await api('/api/custom-task','POST',{action:command,className:form.dataset.customForm,
   offsetMinutes:Number(data.offsetMinutes),priority:Number(data.priority),
   followUpDelayHours:Number(data.followUpDelayHours)});
  await loadCustom();
 },'Custom task saved');
});
$('mobile-menu').addEventListener('click',()=>document.querySelector('.sidebar').classList.toggle('open'));
$('logout').addEventListener('click',async()=>{try{await api('/api/logout','POST',{});}finally{location.replace('/');}});
for(const [name, actionName] of [['start','start'],['pause','pause'],['resume','resume'],['stop','stop']]) {
 $(name).addEventListener('click',()=>action(async()=>{await api('/api/engine','POST',{action:actionName});await loadStatus();await loadProfiles();},'Bot: '+actionName));
}
$('tasks-profile').addEventListener('change',e=>{state.profileId=e.target.value;state.selectedTaskId='';document.querySelector('.operations-tasks')?.classList.remove('has-task-detail');loadTaskStars();loadTasks();});
$('tasks-search').addEventListener('input',renderTasks);
$('task-sort').addEventListener('change',renderTasks);
$('task-detail-scrim').addEventListener('click',closeTaskDetail);
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&state.page==='tasks'&&state.selectedTaskId)closeTaskDetail();});
$('settings-profile').addEventListener('change',loadSettings);
for(const id of ['settings-search','settings-type','settings-advanced']) $(id).addEventListener(id==='settings-search'?'input':'change',renderSettings);
$('profile-new').addEventListener('click',()=>{$('profile-form').reset();$('profile-id').value='';});
$('password-form').addEventListener('submit',e=>{
 e.preventDefault();
 const current=$('password-current').value,newPassword=$('password-new').value;
 if(newPassword!==$('password-confirm').value){alertUser('New passwords do not match',true);return;}
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
},'Profile saved');});
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
 action(async()=>{await api('/api/task','POST',{action:'schedule',profileId:state.profileId,task,when});$('schedule-dialog').close();await loadTasks();},'Schedule saved');
});

setInterval(()=>{$('clock').textContent=new Date().toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});},1000);
setInterval(loadStatus,4500);
// The embedded native Serve-Emu browser provides its own continuous WebSocket stream.
// ADB screenshot capture is now manual fallback only, never an automatic 1.8s loop.
setInterval(()=>{if(state.page==='tasks')loadTasks();if(state.page==='logs')loadLogs();if(state.page==='qa')loadQa();if(state.page==='statistics')loadStatistics();if(state.page==='emulator')loadDevice();},20000);
(async()=>{await loadStatus();await loadProfiles();loadTaskStars();if(state.profileId)await loadTasks();loadDevice();})().catch(e=>alertUser(e.message,true));
