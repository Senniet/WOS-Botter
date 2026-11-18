const state = { page: 'dashboard', profiles: [], tasks: [], categories: [], profileId: '', settings: [], status: null, emulator: null, frameBusy: false, lastFrame: '', lastError: '' };
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
  if(page==='emulator') {loadDevice(); refreshFrame();}
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
async function loadTasks(){
 if(!state.profileId){$('task-list').innerHTML='<div class="panel-content text-muted">Maak eerst een profiel aan.</div>';return;}
 try{
   state.tasks=await api('/api/tasks?profile='+encodeURIComponent(state.profileId));
   const categories=[...new Set(state.tasks.map(t=>t.category))].sort();
   const selected=$('tasks-category').value;
   $('tasks-category').innerHTML='<option value="">Alle categorieën</option>'+categories.map(c=>'<option>'+esc(c)+'</option>').join('');
   $('tasks-category').value=categories.includes(selected)?selected:'';
   $('metric-tasks').textContent=state.tasks.filter(t=>t.enabled).length+' / '+state.tasks.length;
   $('metric-tasks-sub').textContent='Taken ingeschakeld';
   renderTasks();
 }catch(e){$('task-list').innerHTML='<div class="panel-content">'+esc(e.message)+'</div>';}
}
function renderTasks(){
 const search=$('tasks-search').value.trim().toLowerCase(),cat=$('tasks-category').value,enabled=$('tasks-enabled').checked;
 const items=state.tasks.filter(t=>(!search||(t.name+' '+t.id+' '+t.category).toLowerCase().includes(search))&&(!cat||t.category===cat)&&(!enabled||t.enabled));
 $('task-list').innerHTML=items.map(t=>{
   const label=t.outcome==='SUCCEEDED'?'Geslaagd':t.outcome==='FAILED'?'Mislukt':t.outcome==='RUNNING'?'Uitvoering':t.outcome==='NOT_RUN'?'Nog niet':' '+t.outcome;
   const cls=t.outcome==='SUCCEEDED'?'good':t.outcome==='FAILED'?'bad':'';
   return '<div class="task-row"><div class="task-name"><label><input type="checkbox" data-task-toggle="'+esc(t.id)+'" '+(t.enabled?'checked ':'')+(t.toggleable?'':'disabled ')+'> <span><strong>'+esc(t.name)+'</strong><small>'+esc(t.category.replace(/_/g,' '))+' · '+esc(t.id)+'</small></span></label></div><div class="outcome '+cls+'" title="'+esc(t.detail)+'">'+esc(t.executing?'Bezig':label)+'<small class="text-muted">'+(t.lastAttempt?' · '+datetime(t.lastAttempt):'')+'</small></div><div class="text-muted">'+datetime(t.next)+'</div><div class="task-actions"><button class="secondary" data-schedule-task="'+esc(t.id)+'" '+(!t.enabled?'disabled':'')+'>Plannen</button><button class="secondary" data-run-task="'+esc(t.id)+'" '+(!t.enabled?'disabled':'')+'>Nu uitvoeren</button></div></div>';
 }).join('')||'<div class="panel-content text-muted">Geen taken gevonden.</div>';
}
async function loadSettings(){
 try {state.settings=await api('/api/settings'+($('settings-profile').value?'?profile='+encodeURIComponent($('settings-profile').value):''));renderSettings(); }
 catch(e){$('settings-list').textContent=e.message;}
}
function renderSettings(){
 const search=$('settings-search').value.trim().toLowerCase(),type=$('settings-type').value;
 const arr=state.settings.filter(s=>(!search||s.key.toLowerCase().includes(search))&&(!type||s.type===type));
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
   $('emu-mode').textContent=new Date().toLocaleTimeString('nl-NL');
 }catch(e){$('emu-mode').textContent=e.message;}
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
});
document.addEventListener('change',e=>{
 const task=e.target.closest('[data-task-toggle]');
 if(task)action(async()=>{await api('/api/task','POST',{action:'toggle',profileId:state.profileId,task:task.dataset.taskToggle,enabled:task.checked});await loadTasks();},'Taakinstelling opgeslagen');
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
$('tasks-profile').addEventListener('change',e=>{state.profileId=e.target.value;loadTasks();});
for(const id of ['tasks-search','tasks-category','tasks-enabled']) $(id).addEventListener(id==='tasks-search'?'input':'change',renderTasks);
$('settings-profile').addEventListener('change',loadSettings);
for(const id of ['settings-search','settings-type']) $(id).addEventListener(id==='settings-search'?'input':'change',renderSettings);
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
setInterval(()=>{if(state.page==='emulator'&&$('auto-frame').checked)refreshFrame();},1800);
setInterval(()=>{if(state.page==='tasks')loadTasks();if(state.page==='logs')loadLogs();if(state.page==='qa')loadQa();if(state.page==='statistics')loadStatistics();},20000);
(async()=>{await loadStatus();await loadProfiles();if(state.profileId)await loadTasks();loadDevice();})().catch(e=>alertUser(e.message,true));
