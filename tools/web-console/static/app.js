const state = { page: 'dashboard', profiles: [], tasks: [], categories: [], profileId: '', settings: [], taskSettings: [], expandedTasks: new Set(), selectedTaskId: '', taskGroup: 'all', taskFilter: 'all', taskSort: 'logical', taskStars: new Set(), taskBusy: new Set(), status: null, emulator: null, frameBusy: false, lastFrame: '', lastError: '' };
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
  if(page!=='tasks' && state.page==='tasks'){state.selectedTaskId='';document.querySelector('.operations-tasks')?.classList.remove('has-task-detail');}
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
// Curated working areas; the engine's 63 original task IDs remain authoritative.
const workGroups=[
 {id:'daily',name:'Dagelijkse taken',icon:'◷',description:'Beloningen en dagelijkse controles',ids:['DAILY_MISSIONS','MAIL_REWARDS','STOREHOUSE_CHEST','INTEL','CITY_SURVIVORS']},
 {id:'city',name:'Stad & ontwikkeling',icon:'⌂',description:'Bouwen, onderzoek en Chief Orders',ids:['CITY_UPGRADE_FURNACE','CITY_UPGRADE_PRIORITISE_FURNACE','RESEARCH','CHIEF_ORDER_RUSH_JOB','CHIEF_ORDER_URGENT_MOBILIZATION','CHIEF_ORDER_PRODUCTIVITY_DAY']},
 {id:'army',name:'Troepen',icon:'♜',description:'Trainen en genezen',ids:['TRAINING_TROOPS','HEAL_TROOPS']},
 {id:'world',name:'Wereldkaart',icon:'◇',description:'Verzamelen, jagen en marsen',ids:['GATHER_RESOURCES','GATHER_BOOST','BEAST_HUNTING','EVENT_POLAR_TERROR','EVENT_BERSERK_CRYPTID']},
 {id:'heroes',name:'Helden & verkenning',icon:'✧',description:'Rekrutering, arena, exploratie',ids:['HERO_RECRUITMENT','ARENA','DO_EXPLORATION','EXPLORATION_CHEST','LABYRINTH','EXPERT_AGNES_INTEL','EXPERT_ROMULUS_TAG','EXPERT_ROMULUS_TROOPS','EXPERT_SKILL_TRAINING']},
 {id:'alliance',name:'Alliantie',icon:'⚑',description:'Hulp, bijdragen, rally en winkels',ids:['ALLIANCE_AUTOJOIN','ALLIANCE_HELP_REQUEST','ALLIANCE_TECH','ALLIANCE_CHESTS','ALLIANCE_TRIUMPH','ALLIANCE_CHAMPIONSHIP','ALLIANCE_MOBILIZATION','ALLIANCE_PET_TREASURE','ALLIANCE_SHOP','LIFE_ESSENCE_CARING']},
 {id:'rewards',name:'Winkel & extra\'s',icon:'▤',description:'Winkels, VIP en huisdieren',ids:['SHOP_MYSTERY','NOMADIC_MERCHANT','VIP_POINTS','BANK','CRYSTAL_LABORATORY','WAR_ACADEMY','LIFE_ESSENCE','PET_SKILLS','PET_ADVENTURE']},
 {id:'events',name:'Evenementen',icon:'✳',description:'Tijdelijke en speciale evenementen',ids:['BEAR_TRAP','EVENT_TUNDRA_TRUCK','FISHING_MINIGAME','EVENT_HERO_MISSION','EVENT_JOURNEY_OF_LIGHT','EVENT_MYRIAD_BAZAAR','MERCENARY_EVENT','TREK_AUTOMATION','TREK_SUPPLIES']},
 {id:'system',name:'Systeem & tests',icon:'⌘',description:'Technische en ondersteunende routines',ids:['INITIALIZE','CREATE_CHARACTER','SKIP_TUTORIAL','DUMMY_TASK','TEST_HOOK_LOOP','CUSTOM_TASK','GAME_ANALYTICS_LABYRINTH','GAME_ANALYTICS_POWER']}
];
const workLabels={
 DAILY_MISSIONS:['Dagelijkse missies','Ontvang beloningen uit voltooide dagelijkse missies.'],
 MAIL_REWARDS:['Post ophalen','Verzamel beschikbare beloningen uit de mailbox.'],
 STOREHOUSE_CHEST:['Voorraadkist','Controleer en claim beschikbare voorraadkisten.'],
 INTEL:['Intel-missies','Voer Intel-opdrachten uit via de vuurtoren.'],
 CITY_SURVIVORS:['Overlevenden ontvangen','Laat nieuwe overlevenden binnen en wijs ze toe.'],
 CITY_UPGRADE_FURNACE:['Furnace verbeteren','Verwerk upgrades van de furnace en eventuele vereisten.'],
 CITY_UPGRADE_PRIORITISE_FURNACE:['Furnace voorrang geven','Geef de furnace prioriteit bij het bouwen.'],
 RESEARCH:['Onderzoek','Start beschikbare upgrades in het onderzoeksgebouw.'],
 CHIEF_ORDER_RUSH_JOB:['Chief Order · Rush Job','Activeer de bouwtijdverkorting wanneer deze beschikbaar is.'],
 CHIEF_ORDER_URGENT_MOBILIZATION:['Chief Order · Urgent Mobilization','Gebruik de mobilisatieopdracht wanneer deze beschikbaar is.'],
 CHIEF_ORDER_PRODUCTIVITY_DAY:['Chief Order · Productivity Day','Activeer de productieopdracht wanneer deze beschikbaar is.'],
 TRAINING_TROOPS:['Troepen trainen','Voltooi lopende training en train nieuwe eenheden.'],
 HEAL_TROOPS:['Troepen genezen','Genees gewonde troepen in de ziekenboeg.'],
 GATHER_RESOURCES:['Grondstoffen verzamelen','Stuur marsen naar grondstofvelden.'],
 GATHER_BOOST:['Verzamelsnelheid verhogen','Gebruik ingestelde verzamelbonussen.'],
 BEAST_HUNTING:['Beesten jagen','Jaag op beesten op het wereldscherm.'],
 EVENT_POLAR_TERROR:['Polar Terror','Val Polar Terrors aan volgens je instellingen.'],
 EVENT_BERSERK_CRYPTID:['Rally\'s aansluiten','Neem deel aan beschikbare rally\'s.'],
 HERO_RECRUITMENT:['Helden rekruteren','Gebruik gratis pogingen en optioneel sleutels.'],
 ARENA:['Arena','Voer arena-uitdagingen uit en haal beloningen op.'],
 DO_EXPLORATION:['Exploratie','Ga verder in de exploratie en claim beloningen.'],
 EXPLORATION_CHEST:['Exploratiekist','Open beschikbare beloningskisten.'],
 LABYRINTH:['Labyrint','Voer de dagelijkse labyrinttaak uit.'],
 EXPERT_AGNES_INTEL:['Expert Agnes · Intel','Gebruik de ingestelde Expert Agnes-opties.'],
 EXPERT_ROMULUS_TAG:['Expert Romulus · Tag','Gebruik de ingestelde Expert Romulus-opties.'],
 EXPERT_ROMULUS_TROOPS:['Expert Romulus · Troepen','Gebruik de troepenbonus van Romulus.'],
 EXPERT_SKILL_TRAINING:['Expertvaardigheden','Train ingestelde expertvaardigheden.'],
 ALLIANCE_AUTOJOIN:['Autojoin-rally\'s','Sluit automatisch aan bij alliantieactiviteiten.'],
 ALLIANCE_HELP_REQUEST:['Alliantiehulp','Help alliantieleden bij hun verzoeken.'],
 ALLIANCE_TECH:['Alliantietechnologie','Lever bijdragen aan alliantietechnologie.'],
 ALLIANCE_CHESTS:['Alliantiekisten','Haal beschikbare alliantiekisten op.'],
 ALLIANCE_TRIUMPH:['Alliance Triumph','Verwerk beschikbare Triumph-beloningen.'],
 ALLIANCE_CHAMPIONSHIP:['Alliance Championship','Beheer deelname aan het kampioenschap.'],
 ALLIANCE_MOBILIZATION:['Alliance Mobilization','Voer de geselecteerde mobilisatieopdrachten uit.'],
 ALLIANCE_PET_TREASURE:['Alliantie · Dierenschat','Haal de beschikbare dierenbeloningen op.'],
 ALLIANCE_SHOP:['Alliantiewinkel','Koop items volgens de ingestelde voorkeuren.'],
 LIFE_ESSENCE_CARING:['Life Essence · Alliantie','Beheer de alliantiegebonden verzorging.'],
 SHOP_MYSTERY:['Mystery Shop','Controleer aanbiedingen in de Mystery Shop.'],
 NOMADIC_MERCHANT:['Nomadische handelaar','Bezoek de handelaar en kies ingestelde aankopen.'],
 VIP_POINTS:['VIP-punten','Verzamel beschikbare VIP-punten.'],
 BANK:['Bank','Verwerk de ingestelde bankactiviteit.'],
 CRYSTAL_LABORATORY:['Kristallaboratorium','Verwerk beschikbare laboratoriumbeloningen.'],
 WAR_ACADEMY:['War Academy','Verwerk War Academy-beloningen.'],
 LIFE_ESSENCE:['Life Essence','Verzorg dieren en gebruik optionele rollen.'],
 PET_SKILLS:['Dierenvaardigheden','Activeer de geselecteerde dierenvaardigheden.'],
 PET_ADVENTURE:['Dierenavontuur','Voer de ingestelde dierenavonturen uit.'],
 BEAR_TRAP:['Bear Trap','Beheer deelname rondom het Bear Trap-evenement.'],
 EVENT_TUNDRA_TRUCK:['Tundra Truck','Voer de ingestelde truckactiviteiten uit.'],
 FISHING_MINIGAME:['Vistoernooi','Voer de ingestelde vispogingen uit.'],
 EVENT_HERO_MISSION:['Hero Mission','Voer tijdelijke heldenmissies uit.'],
 EVENT_JOURNEY_OF_LIGHT:['Journey of Light','Beheer het Journey of Light-evenement.'],
 EVENT_MYRIAD_BAZAAR:['Myriad Bazaar','Voer de ingestelde Bazaar-activiteit uit.'],
 MERCENARY_EVENT:['Mercenary Event','Voer het huurlingenevenement uit.'],
 TREK_AUTOMATION:['Tundra Trek','Automatiseer de ingestelde trekactiviteiten.'],
 TREK_SUPPLIES:['Trek Supplies','Haal beschikbare trekbenodigdheden op.'],
 INITIALIZE:['Initialisatie','Interne voorbereiding van de bot.'],
 CREATE_CHARACTER:['Personage maken','Start een nieuw spelpersonage.'],
 SKIP_TUTORIAL:['Tutorial overslaan','Verwerk het introductiescherm van een nieuw account.'],
 DUMMY_TASK:['Testtaak','Technische routine voor testdoeleinden.'],
 TEST_HOOK_LOOP:['Testlus','Technische testfunctie, niet voor normaal gebruik.'],
 CUSTOM_TASK:['Eigen taken','Verwerkt zelfgemaakte Java-routines.'],
 GAME_ANALYTICS_LABYRINTH:['Labyrintstatistiek','Verzamel statistische alliantiegegevens.'],
 GAME_ANALYTICS_POWER:['Machtsstatistiek','Verzamel statistische alliantiegegevens.']
};
const workAliases={
 HERO_RECRUITMENT:'rekrutering recruit sleutels keys heldenwerving',
 DAILY_MISSIONS:'daily missie dagtaken',MAIL_REWARDS:'mail post beloningen',
 STOREHOUSE_CHEST:'kisten voorraadkisten storehouse',ALLIANCE_CHESTS:'kisten alliantie chest',
 GATHER_RESOURCES:'farm farms verzamelen hout vlees ijzer kolen wood meat iron coal',
 GATHER_BOOST:'snelheid verzamelbonus',HEAL_TROOPS:'healing ziekenboeg genezen helen',
 TRAINING_TROOPS:'train training opleiden infanterie schutters lanciers',
 CITY_UPGRADE_FURNACE:'oven furnace bouwen upgrade construction',
 CITY_SURVIVORS:'survivors welcome in allocate bewoners',
 ALLIANCE_AUTOJOIN:'rally rallys joins aansluiten alliance autojoin',
 ALLIANCE_TECH:'contribution bijdragen contributie tech',
 DO_EXPLORATION:'exploration verkennen claim exploratie',
 EXPLORATION_CHEST:'exploration kisten kist',
 EVENT_TUNDRA_TRUCK:'truck vrachtwagen tundra',
 ARENA:'arena challenges uitdagingen chest kist',
 INTEL:'lighthouse vuurtoren hero journey beast',
 LIFE_ESSENCE:'essence dieren verzorging',
 FISHING_MINIGAME:'viswedstrijd vissen tournament',
 BEAR_TRAP:'bear beer jacht rally'
};
const workIds=new Map(workGroups.flatMap(group=>group.ids.map(id=>[id,group.id])));
const workSortOrder=new Map(workGroups.flatMap(group=>group.ids.map((id,index)=>[id,index])));
function workGroup(t){return workIds.get(t.id)||'system';}
function workName(t){return workLabels[t.id]?.[0]||t.name;}
function workHelp(t){return workLabels[t.id]?.[1]||'Instellingen en uitvoering van deze routine.';}
function taskStarsKey(){return 'survivalops.web.stars.'+state.profileId;}
function loadTaskStars(){try{const ids=JSON.parse(localStorage.getItem(taskStarsKey())||'[]');state.taskStars=new Set(Array.isArray(ids)?ids:[]);}catch(_){state.taskStars=new Set();}}
function saveTaskStars(){try{localStorage.setItem(taskStarsKey(),JSON.stringify([...state.taskStars]));}catch(_){/* Restricted/private browser mode */}}
function workStatus(t){
 if(t.executing)return {label:'Bezig',kind:'running',priority:0};
 if(t.outcome==='FAILED')return {label:'Mislukt',kind:'failed',priority:1};
 if(t.outcome==='SUCCEEDED')return {label:'Geslaagd',kind:'success',priority:2};
 if(t.queued)return {label:'In wachtrij',kind:'queued',priority:3};
 if(t.outcome==='RUNNING')return {label:'Recent gestart',kind:'running',priority:0};
 return {label:'Nog geen resultaat',kind:'idle',priority:4};
}
function workMatched(t){
 const text=$('tasks-search').value.trim().toLocaleLowerCase('nl-NL');
 if(text && ![workName(t),workHelp(t),workAliases[t.id],t.name,t.id,workGroups.find(g=>g.id===workGroup(t))?.name]
      .some(s=>String(s||'').toLocaleLowerCase('nl-NL').includes(text)))return false;
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
 $('task-summary').innerHTML='<span><b>'+state.tasks.length+'</b> routines</span><span><i class="work-dot is-on"></i><b>'+state.tasks.filter(t=>t.enabled).length+'</b> ingeschakeld</span><span><i class="work-dot is-error"></i><b>'+state.tasks.filter(t=>t.outcome==='FAILED').length+'</b> mislukt</span><span class="work-summary-note">'+counters+' handmatig schakelbaar · '+allEnabled+' actief in selectie'+(allErrors?' · '+allErrors+' fout in selectie':'')+'</span>';
 $('task-categories').innerHTML=[{id:'all',name:'Alle gebieden',icon:'▦',description:''},...workGroups].map(group=>{
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
 if(!profileId){$('task-list').textContent='Maak eerst een profiel aan.';return;}
 try{
  const [tasks,settings]=await Promise.all([api('/api/tasks?profile='+encodeURIComponent(profileId)),api('/api/settings?profile='+encodeURIComponent(profileId))]);
  if(profileId!==state.profileId)return;
  state.tasks=tasks;state.taskSettings=settings;
  $('metric-tasks').textContent=tasks.filter(t=>t.enabled).length+' / '+tasks.length;
  $('metric-tasks-sub').textContent='Taken ingeschakeld';
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
 $('task-list-title').textContent=state.taskGroup==='all'?'Alle routines':(workGroups.find(g=>g.id===state.taskGroup)?.name||'Routines');
 $('task-visible-count').textContent=listing.length+' zichtbaar';
 let html='';
 for(const group of groups){
  let items=relevant.filter(t=>workGroup(t)===group.id);
  if(!items.length)continue;
  if($('task-sort').value==='name')items=items.sort((a,b)=>workName(a).localeCompare(workName(b),'nl-NL'));
  else if($('task-sort').value==='state')items=items.sort((a,b)=>workStatus(a).priority-workStatus(b).priority||workName(a).localeCompare(workName(b),'nl-NL'));
  else items=items.sort((a,b)=>(workSortOrder.get(a.id)??999)-(workSortOrder.get(b.id)??999));
  html+='<div class="work-section"><div class="work-section-header"><div><span class="work-section-icon">'+esc(group.icon)+'</span><strong>'+esc(group.name)+'</strong><small>'+esc(group.description)+'</small></div><span>'+items.filter(t=>t.enabled).length+' / '+items.length+' aan</span></div>';
  for(const t of items){
   const status=workStatus(t),selected=state.selectedTaskId===t.id;
   const note=t.executing?'Wordt momenteel uitgevoerd':t.lastAttempt?'Laatste '+datetime(t.lastAttempt):'Nog niet uitgevoerd';
   const next=t.enabled?(t.next?'Volgende '+datetime(t.next):'Geen planning'):'Uitgeschakeld';
   const opts=taskBehavior(t).length;
   html+='<div class="work-task'+(selected?' is-selected':'')+(t.enabled?' is-enabled':'')+'" data-task-row="'+esc(t.id)+'">'
     +'<button class="work-star'+(state.taskStars.has(t.id)?' is-starred':'')+'" type="button" data-star-task="'+esc(t.id)+'" title="'+(state.taskStars.has(t.id)?'Losmaken van favorieten':'Vastzetten als favoriet')+'" aria-label="Favoriet: '+esc(workName(t))+'" aria-pressed="'+state.taskStars.has(t.id)+'">'+(state.taskStars.has(t.id)?'★':'☆')+'</button>'
     +'<button class="work-task-open" type="button" data-open-task="'+esc(t.id)+'" aria-label="Instellingen voor '+esc(workName(t))+' openen"><strong>'+esc(workName(t))+'</strong><small>'+esc(workHelp(t))+'</small></button>'
     +'<span class="work-state is-'+status.kind+'"><i></i>'+status.label+'</span>'
     +'<span class="work-task-timing" title="'+esc(note)+'">'+esc(next)+'</span>'
     +'<label class="work-main-toggle" title="'+(t.toggleable?'Taak in- of uitschakelen':'Interne taak, niet handmatig schakelbaar')+'"><input type="checkbox" data-task-toggle="'+esc(t.id)+'" aria-label="'+esc(workName(t))+' in- of uitschakelen" '+(t.enabled?'checked ':'')+(t.toggleable?'':'disabled ')+(state.taskBusy.has(t.id)?'disabled ':'')+'><span></span></label>'
     +'<button type="button" class="work-row-arrow" data-open-task="'+esc(t.id)+'" aria-label="Details '+esc(workName(t))+'">'+(opts?'⚙':'›')+'</button></div>';
  }
  html+='</div>';
 }
 $('task-list').innerHTML=html||'<div class="work-no-results"><strong>Geen routines gevonden</strong><p>Wijzig je zoekwoord, filter of werkgebied.</p><button type="button" data-clear-task-filters>Filters wissen</button></div>';
}
function renderTaskDetail(){
 const t=state.tasks.find(t=>t.id===state.selectedTaskId);
 if(!t){$('task-detail-content').innerHTML='<div class="work-empty-detail"><span>↗</span><strong>Selecteer een routine</strong><p>Open een taak om instellingen, planning en het laatste resultaat te bekijken.</p></div>';return;}
 const status=workStatus(t),group=workGroups.find(g=>g.id===workGroup(t));
 const settings=taskBehavior(t);
 const booleans=settings.filter(s=>s.type==='Boolean'), values=settings.filter(s=>s.type!=='Boolean');
 const optionSection=(title,items)=>items.length?'<div class="work-option-section"><h4>'+esc(title)+'</h4><div class="task-option-grid">'+items.map(s=>optionControl(t,s)).join('')+'</div></div>':'';
 const last=t.lastAttempt?'Laatste poging: '+datetime(t.lastAttempt):'Nog geen uitvoering geregistreerd';
 const successful=t.lastSuccess?'Laatste succes: '+datetime(t.lastSuccess):'Nog geen succes geregistreerd';
 const options=booleans.length+values.length;
 $('task-detail-content').innerHTML='<header class="work-detail-head"><span>'+esc(group?.name||'Taak')+'</span><button type="button" data-close-task aria-label="Details sluiten">×</button></header>'
   +'<div class="work-detail-title"><h3>'+esc(workName(t))+'</h3><p>'+esc(workHelp(t))+'</p></div>'
   +'<div class="work-detail-status"><span class="work-state is-'+status.kind+'"><i></i>'+esc(status.label)+'</span><span>'+(t.enabled?'Ingeschakeld':'Uitgeschakeld')+'</span></div>'
   +'<div class="work-detail-activation"><div><strong>Automatisch uitvoeren</strong><small>Wordt uitgevoerd als de bot draait en deze taak aan de beurt is.</small></div><label class="work-main-toggle"><input type="checkbox" data-task-toggle="'+esc(t.id)+'" aria-label="Taak automatisch uitvoeren" '+(t.enabled?'checked ':'')+(t.toggleable?'':'disabled ')+(state.taskBusy.has(t.id)?'disabled ':'')+'><span></span></label></div>'
   +'<div class="work-detail-actions"><button type="button" class="primary" data-run-task="'+esc(t.id)+'" '+(!t.enabled?'disabled ':'')+'>▶ Nu uitvoeren</button><button type="button" class="secondary" data-schedule-task="'+esc(t.id)+'" '+(!t.enabled?'disabled ':'')+'>◷ Plannen</button></div>'
   +'<div class="work-detail-block"><h4>UITVOERING</h4><dl><div><dt>Laatste poging</dt><dd>'+esc(t.lastAttempt?datetime(t.lastAttempt):'Nog niet')+'</dd></div><div><dt>Laatste succes</dt><dd>'+esc(t.lastSuccess?datetime(t.lastSuccess):'Nog niet')+'</dd></div><div><dt>Volgende planning</dt><dd>'+esc(t.next?datetime(t.next):'Niet gepland')+'</dd></div></dl>'+(t.outcome==='FAILED'&&t.detail?'<p class="work-error-detail">'+esc(t.detail.slice(0,500))+'</p>':'')+'</div>'
   +'<div class="work-detail-options" data-task-options="'+esc(t.id)+'"><div class="work-detail-section-head"><h4>GEDRAG & OPTIES</h4><span>'+options+' opties</span></div>'
   +(settings.length?optionSection('Schakelaars',booleans)+optionSection('Waarden en grenzen',values):'<p class="work-no-options">Deze routine heeft geen extra opties. In- en uitschakelen doe je hierboven.</p>')
   +'</div><p class="work-detail-help">Instellingen worden direct in het gekozen profiel opgeslagen. Aanpassingen starten geen taak.</p>';
}
function clearTaskFilters(){
 state.taskGroup='all';state.taskFilter='all';$('tasks-search').value='';$('task-sort').value='logical';renderTasks();
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
 const run=e.target.closest('[data-run-task]');if(run)action(async()=>{await api('/api/task','POST',{action:'run',profileId:state.profileId,task:run.dataset.runTask});await loadTasks();},'Taak toegevoegd aan de wachtrij');
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
   },'Routine '+(enabled?'ingeschakeld':'uitgeschakeld'));
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
(async()=>{await loadStatus();await loadProfiles();loadTaskStars();if(state.profileId)await loadTasks();loadDevice();})().catch(e=>alertUser(e.message,true));
