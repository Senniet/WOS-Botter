"use strict";
const $=id=>document.getElementById(id);
const labels={not_tested:"Nog niet getest",testing:"In onderzoek",working:"Lijkt te werken",broken:"Werkt niet",blocked:"Geblokkeerd",verified:"Werkt — afgevinkt"};
let tasks=[], selected=null, details=null, onlyIssues=false, notesDirty=false, busy=false, toastTimer=null;
// Drafts remain local until explicitly saved; a 60s read refresh must NEVER
// replace partially entered QA evidence. A per-round key avoids mixing runs.
const qaDrafts=new Map();
function qaDraftId(task,round){return task+":"+(round?.id??"new");}
function qaValue(task,round,field,fallback){
 const draft=qaDrafts.get(qaDraftId(task,round));
 return draft?.has(field)?draft.get(field):fallback;
}
function setQaDraft(task,round,field,value,original){
 const id=qaDraftId(task,round),draft=qaDrafts.get(id)||new Map();
 if(value===original)draft.delete(field);else draft.set(field,value);
 if(draft.size)qaDrafts.set(id,draft);else qaDrafts.delete(id);
}
function clearQaDraft(task,round,fields){
 const id=qaDraftId(task,round),draft=qaDrafts.get(id);
 if(!draft)return;
 for(const f of fields)draft.delete(f);
 if(!draft.size)qaDrafts.delete(id);
}
function hasUnsavedEdits(){return notesDirty||qaDrafts.size>0;}
function editingDetailField(){
 const active=document.activeElement;
 return !!(active&&$("detail")?.contains(active)&&["INPUT","TEXTAREA","SELECT"].includes(active.tagName));
}
function confirmLeavingEdits(){
 return !hasUnsavedEdits()||confirm("Je hebt testvelden of notities die nog niet zijn opgeslagen. Deze wijzigingen weggooien?");
}
const qaErrors={
 needs_evidence_note:"De opgeslagen teststappen bevatten nog geen concrete toelichting. Vul per stap je waarneming in en sla de stappen op.",
 needs_note:"Schrijf bij deze stap minimaal 8 tekens over wat je hebt gezien.",
 incomplete_checks:"Niet alle 7 controlepunten zijn opgeslagen als ‘Werkt’. Sla de stappen eerst apart op.",
 missing_coach_evidence:"Er ontbreekt een nieuwe, bij deze taak behorende Task Coach-run. Koppel die eerst.",
 invalid_evidence:"Deze Task Coach-run is niet gevonden, hoort bij een andere taak of is ouder dan de testronde.",
 open_issues:"Er zijn nog openstaande fouten. Los die eerst op en test opnieuw voordat je bevestigt.",
 no_round:"Start eerst een gezamenlijke testronde.",
 finalized:"Deze testronde is al afgerond. Start een nieuwe voor een hertest.",
 invalid:"Deze invoer is ongeldig. Controleer de versie en de ingevulde velden."
};
const priority={broken:0,testing:1,blocked:2,not_tested:3,working:4,verified:5};
function node(tag,cls,txt){
 const e=document.createElement(tag);if(cls)e.className=cls;if(txt!==undefined)e.textContent=String(txt);return e;
}
function statusBadge(status){return node("span","pill "+status,labels[status]||status);}
function dateText(s){
 if(!s)return "Nog niet";
 try{return new Date(s).toLocaleString("nl-NL",{day:"2-digit",month:"short",hour:"2-digit",minute:"2-digit"});}
 catch{return s.slice(0,16);}
}
function showToast(message,error=false){
 const t=$("toast");t.textContent=message;t.className="toast"+(error?" error":"");t.hidden=false;
 clearTimeout(toastTimer);toastTimer=setTimeout(()=>{t.hidden=true;},4700);
}
async function request(path,options={}){
 const config={credentials:"same-origin",...options,headers:{"Accept":"application/json",...(options.body?{"Content-Type":"application/json"}:{}),...(options.headers||{})}};
 let response;
 try{response=await fetch(path,config);}
 catch{throw new Error("Dashboard niet bereikbaar. Controleer de verbinding.");}
 const contentType=response.headers.get("Content-Type")||"";
 const data=contentType.includes("application/json")?await response.json():null;
 if(response.status===401&&path!=="/api/login"){showLogin();throw new Error("Log opnieuw in.");}
 if(response.status===409)throw new Error("Deze taak is inmiddels elders aangepast. Ververs de taak.");
 if(!response.ok)throw new Error(data?.error||"Verzoek mislukt ("+response.status+").");
 return data;
}
function showLogin(){
 $("app").hidden=true;$("login").hidden=false;$("password").focus();
}
function showApp(){$("app").hidden=false;$("login").hidden=true;}
function createStat(text,value,style,marker){
 const box=node("div","stat"+(style?" "+style:""));
 box.append(node("div","marker",marker),node("div","number",value),node("div","label",text));
 return box;
}
function updateStats(){
 const counts={};for(const t of tasks)counts[t.status]=(counts[t.status]||0)+1;
 const totalIssues=tasks.reduce((n,t)=>n+t.open_issues,0);
 $("stats").replaceChildren(createStat("Geregistreerde taken",tasks.length,"","▦"),
  createStat("Afgevinkt als werkend",counts.verified||0,"success","✓"),
  createStat("Werkt niet",counts.broken||0,"alert","⚠"),
  createStat("Openstaande fouten",totalIssues,totalIssues?"alert":"","◉"));
 $("updated").textContent="Laatste controle "+new Date().toLocaleTimeString("nl-NL",{hour:"2-digit",minute:"2-digit"});
}
function renderCategories(){
 const select=$("category"),old=select.value;
 while(select.options.length>1)select.remove(1);
 const cats=[...new Set(tasks.map(t=>t.category))].sort();
 for(const c of cats){const opt=node("option","",c.replaceAll("_"," "));opt.value=c;select.append(opt);}
 select.value=old;
}
function filteredTasks(){
 const text=$("search").value.trim().toLowerCase(),cat=$("category").value,status=$("status").value;
 return tasks.filter(t=>(!cat||t.category===cat)&&(!status||t.status===status)&&
  (!text||[t.label,t.key,t.category,t.notes].some(x=>String(x||"").toLowerCase().includes(text)))&&
  (!onlyIssues||t.open_issues>0||["broken","blocked"].includes(t.status)))
  .sort((a,b)=>priority[a.status]-priority[b.status]||a.category.localeCompare(b.category)||a.label.localeCompare(b.label));
}
function renderList(){
 const matches=filteredTasks(),box=$("task-list");
 box.replaceChildren();$("show-count").textContent="("+matches.length+"/"+tasks.length+")";
 $("empty").hidden=matches.length!==0;
 const fragment=document.createDocumentFragment();
 for(const t of matches){
  const row=node("button","task-row"+(selected===t.key?" active":""));
  row.type="button";row.setAttribute("aria-label",t.label+", "+labels[t.status]);
  const name=node("div","task-main"),check=node("span","task-check"+(t.status==="verified"?" on":""),
   t.status==="verified"?"✓":"");
  check.title=t.status==="verified"?"Vinkje verwijderen":"Afvinken als volledig werkend";
  const names=node("div");
  names.append(node("div","task-name",t.label),node("div","task-sub",t.category.replaceAll("_"," ")));
  name.append(check,names);
  const signals=node("span","signals"+(t.open_issues?" has":""),
   (t.open_issues?"⚠ "+t.open_issues:"")+(t.open_issues&&t.diagnostics?" · ":"")+
   (t.diagnostics?"◷ "+t.diagnostics:(!t.open_issues?"—":"")));
  row.append(name,statusBadge(t.status),signals);
  row.addEventListener("click",e=>{
    if(e.target===check)verify(t);
    else selectTask(t.key);
  });
  fragment.append(row);
 }
 box.append(fragment);
}
async function reload({preserveSelection=true,quiet=false,forceDetail=false,refreshDetail=true}={}){
 const data=await request("/api/tasks");
 tasks=data.tasks;
 renderCategories();updateStats();renderList();
 $("diagnostics-count").textContent=data.unmapped_diagnostics+
  " algemene diagnostiekmeldingen (nog niet gekoppeld)";
 if(!preserveSelection||!selected||!tasks.some(t=>t.key===selected)){
  const first=[...tasks].sort((a,b)=>priority[a.status]-priority[b.status])[0];
  selected=first?.key||null;
 }
 if(selected&&refreshDetail&&(forceDetail||(!hasUnsavedEdits()&&!editingDetailField())))
  await loadDetail(selected,quiet,forceDetail);
 if(!quiet)showToast("Takenlijst en diagnostiek bijgewerkt.");
}
async function selectTask(key){
 if(selected!==key&&!confirmLeavingEdits())return;
 if(selected!==key){qaDrafts.clear();notesDirty=false;}
 selected=key;renderList();
 await loadDetail(key);
 if(window.matchMedia("(max-width:900px)").matches){
  $("detail-panel").scrollIntoView({behavior:"smooth",block:"start"});
 }
}
async function loadDetail(key,quiet=false,force=false){
 const data=await request("/api/tasks/"+encodeURIComponent(key)+"/detail");
 if(selected!==key||(!force&&(hasUnsavedEdits()||editingDetailField())))return;
 details=data;notesDirty=false;renderDetail();
}
function section(title){
 const box=node("section"),hd=node("div","section-title",title);box.append(hd);
 return box;
}
function divider(){return node("div","detail-divider");}
function formInput(tag,placeholder,cls=""){
 const f=node(tag,cls||"field-input");f.placeholder=placeholder;return f;
}
async function patchTask(key,changes){
 if(busy)return;
 const row=details?.task;
 if(!row||row.key!==key)return;
 busy=true;
 try{
  const result=await request("/api/tasks/"+encodeURIComponent(key),{
   method:"PATCH",body:JSON.stringify({...changes,version:row.version})
  });
  details.task=result.task;notesDirty=false;
  await reload({preserveSelection:true,quiet:true,forceDetail:true});
  showToast("Taak opgeslagen. Wijziging is zichtbaar voor iedereen.");
 }catch(e){showToast(e.message,true);if(String(e.message).includes("elders"))await reload({quiet:true});}
 finally{busy=false;}
}
async function verify(task){
 if(selected!==task.key){
  if(!confirmLeavingEdits())return;
  qaDrafts.clear();notesDirty=false;
  selected=task.key;await loadDetail(selected,true);
 }
 const panel=$('qa-panel');
 if(panel){panel.scrollIntoView({behavior:'smooth',block:'start'});}
 showToast('Afvinken kan pas na de gezamenlijke testronde en twee bevestigingen.');
}
async function refreshPreservingUnsavedMain(){
 const oldMainDraft=notesDirty?document.querySelector('#detail .note-area')?.value:null;
 await reload({quiet:true,forceDetail:true});
 if(oldMainDraft!==null){
  const textarea=document.querySelector('#detail .note-area');
  if(textarea){textarea.value=oldMainDraft;notesDirty=true;}
 }
}
async function updateQa(key, action, payload){
 const round=details?.qa?.round;
 try{
  await request('/api/tasks/'+encodeURIComponent(key)+'/qa/'+action,
   {method:'POST',body:JSON.stringify(payload)});
  // One Save button saves ONE step; drafts for the other six steps survive
  // the resulting refresh, including unsaved selection and textarea content.
  if(action==='check')clearQaDraft(key,round,['result:'+payload.step,'note:'+payload.step]);
  if(action==='attach')clearQaDraft(key,round,['coach_run']);
  if(action==='operator-sign')clearQaDraft(key,round,['operator_note']);
  if(action==='start')qaDrafts.delete(qaDraftId(key,round));
  await refreshPreservingUnsavedMain();
  showToast(action==='check'?'Controlepunt opgeslagen. Andere ingevulde velden blijven staan.':'Testronde bijgewerkt.');
 }catch(e){
  showToast(qaErrors[e.message]||e.message,true);
 }
}
async function savePendingQaSteps(t,qa){
 // Save all currently filled QA controls before the operator attempts to
 // confirm. This removes the confusing requirement to click seven Save buttons.
 const draft=qaDrafts.get(qaDraftId(t.key,qa.round));
 if(!draft)return;
 for(const spec of qa.steps){
  const statusField='result:'+spec.id,noteField='note:'+spec.id;
  if(!draft.has(statusField)&&!draft.has(noteField))continue;
  const original=qa.checks.find(c=>c.step===spec.id)||{result:'pending',note:''};
  const result=qaValue(t.key,qa.round,statusField,original.result);
  const note=qaValue(t.key,qa.round,noteField,original.note);
  if(result!=='pending'&&note.trim().length<8)throw new Error('needs_note');
  await request('/api/tasks/'+encodeURIComponent(t.key)+'/qa/check',
   {method:'POST',body:JSON.stringify({step:spec.id,result,note})});
  clearQaDraft(t.key,qa.round,[statusField,noteField]);
 }
 if(draft.has('coach_run')){
  await request('/api/tasks/'+encodeURIComponent(t.key)+'/qa/attach',
   {method:'POST',body:JSON.stringify({coach_run:draft.get('coach_run').trim()})});
  clearQaDraft(t.key,qa.round,['coach_run']);
 }
}
function renderQa(t,qa){
 const sectionBox=section('Gezamenlijke testronde');
 sectionBox.id='qa-panel';
 sectionBox.append(node('p','detail-help',
  'Eén testversie en één nieuwe Task Coach-run. Per stap vastleggen wat het spel werkelijk deed. Alleen jij bevestigt de gamekant; ChatGPT controleert de logs, screenshotregressie en code. Beide goedkeuringen zijn verplicht.'));
 if(!qa){
  sectionBox.append(node('p','detail-help','Nog geen testronde voor deze taak geregistreerd. De oude vinkjes zijn historische vermeldingen, geen gezamenlijke verificatie.'));
 }else{
  const r=qa.round;
  sectionBox.append(node('div','qa-run-head','Ronde '+r.id+' · versie '+r.build+' · '+dateText(r.started_at)));
  sectionBox.append(node('p','detail-help','Task Coach-bewijs: '+(r.coach_run||'nog niet gekoppeld')));
  if(r.completed_at)sectionBox.append(node('div','verification','✓ Door jou én ChatGPT geverifieerd op '+dateText(r.completed_at)));
  if(r.superseded_at)sectionBox.append(node('div','detail-help','Deze ronde is vervangen door een nieuwe.'));
  const checks=new Map(qa.checks.map(c=>[c.step,c]));
  for(const spec of qa.steps){
   const c=checks.get(spec.id)||{result:'pending',note:'',actor:''};
   const wrap=node('div','qa-step');
   wrap.append(node('div','qa-step-title',spec.label));
   const saveStatus=node('div','qa-save-status',c.updated_at?'Opgeslagen op '+dateText(c.updated_at):'Nog niet opgeslagen');
   const markUnsaved=()=>{saveStatus.textContent='Niet opgeslagen — bevestigen bewaart dit automatisch';saveStatus.classList.add('dirty');};
   if(qaDrafts.get(qaDraftId(t.key,r))?.has('result:'+spec.id)||qaDrafts.get(qaDraftId(t.key,r))?.has('note:'+spec.id))markUnsaved();
   const sel=node('select','field-input');
   for(const [value,label] of [['pending','Nog niet gecontroleerd'],['pass','Werkt'],['fail','Werkt niet'],['blocked','Nu niet mogelijk']]){
    const opt=node('option','',label);opt.value=value;sel.append(opt);
   }
   sel.value=qaValue(t.key,r,'result:'+spec.id,c.result);
   sel.addEventListener('change',()=>{setQaDraft(t.key,r,'result:'+spec.id,sel.value,c.result);markUnsaved();});
   const note=formInput('textarea','Wat zag je? Welke stap/screenshot bewijst dit?','qa-step-note');
   note.value=qaValue(t.key,r,'note:'+spec.id,c.note);note.maxLength=1200;
   note.addEventListener('input',()=>{setQaDraft(t.key,r,'note:'+spec.id,note.value,c.note);markUnsaved();});
   const save=node('button','outline','Stap vastleggen');save.type='button';
   save.disabled=!!r.completed_at;
   save.addEventListener('click',()=>updateQa(t.key,'check',{step:spec.id,result:sel.value,note:note.value}));
   wrap.append(sel,note,save,saveStatus);
   if(c.actor)wrap.append(node('div','muted',c.actor+' · '+dateText(c.updated_at)));
   sectionBox.append(wrap);
  }
  if(!r.completed_at){
   const evidence=formInput('input','Bijv. 20261115-194511-wi5khp');
   evidence.value=qaValue(t.key,r,'coach_run',r.coach_run||'');evidence.maxLength=64;
   evidence.addEventListener('input',()=>setQaDraft(t.key,r,'coach_run',evidence.value,r.coach_run||''));
   const attach=node('button','outline','Task Coach-run koppelen');attach.type='button';
   attach.addEventListener('click',()=>updateQa(t.key,'attach',{coach_run:evidence.value.trim()}));
   sectionBox.append(node('div','field-label','Nieuwe Task Coach-run (na start van deze testronde)'),evidence,attach);
   const opNote=formInput('textarea','Welke acties en resultaten heb je zelf in de game gecontroleerd?','qa-step-note');
   opNote.value=qaValue(t.key,r,'operator_note',r.operator_note||'');opNote.maxLength=1200;
   opNote.addEventListener('input',()=>setQaDraft(t.key,r,'operator_note',opNote.value,r.operator_note||''));
   const sign=node('button','primary',r.operator_signed_at?'✓ Jij hebt bevestigd':'Teststappen opslaan en bevestigen');
   sign.type='button';sign.disabled=!!r.operator_signed_at;
   sign.addEventListener('click',async()=>{
    if(!confirm('Bevestig je dat je de volledige actie en het resultaat in de game zelf hebt gecontroleerd?'))return;
    sign.disabled=true;
    try{
     await savePendingQaSteps(t,qa);
     await updateQa(t.key,'operator-sign',{note:opNote.value});
    }catch(error){
     showToast(qaErrors[error.message]||error.message,true);
     // Successful partial saves must not erase other in-progress fields.
     await refreshPreservingUnsavedMain();
    }finally{sign.disabled=false;}
   });
   sectionBox.append(node('div','field-label','Jouw bevestiging (de opgeslagen stapnotities zijn voldoende; extra tekst is optioneel)'),opNote,sign);
   sectionBox.append(node('p','detail-help','Bevestiging door ChatGPT: '+(r.reviewer_signed_at?dateText(r.reviewer_signed_at):'nog niet gegeven')));
  }
 }
 const form=node('div','qa-start');
 const build=formInput('input','Appversie, bijvoorbeeld 1.1.66');build.maxLength=40;
 build.value=qaValue(t.key,qa?.round,'build','');
 build.addEventListener('input',()=>setQaDraft(t.key,qa?.round,'build',build.value,''));
 const start=node('button','outline','Nieuwe testronde starten');start.type='button';
 start.addEventListener('click',()=>{
  if(qa&&!confirm('Dit vervangt de openstaande testronde. Alle controlepunten moeten opnieuw worden getest. Doorgaan?'))return;
  updateQa(t.key,'start',{build:build.value.trim()});
 });
 form.append(node('div','field-label','Versioneer iedere hercontrole'),build,start);
 sectionBox.append(form);
 return sectionBox;
}
function renderDetail(){
 const wrap=$("detail"),data=details;if(!data)return;
 const t=data.task;wrap.replaceChildren();
 const top=node("div","detail-category",t.category.replaceAll("_"," "));
 top.append(statusBadge(t.status));
 wrap.append(top,node("h2","",t.label),node("div","detail-key",t.key+" · "+t.factory));
 wrap.append(divider());
 const edit=section("Teststatus");
 const label=node("label","field-label","Status voor deze taak");
 const select=node("select","field-input");
 for(const [k,v] of Object.entries(labels)){const option=node("option","",v);option.value=k;select.append(option);}
 select.value=t.status;
 select.addEventListener("change",async ()=>{
  if(select.value==="verified"&&t.status!=="verified"){
    select.value=t.status;
    verify(t);
    return;
  }
  await patchTask(t.key,{status:select.value,notes:textarea.value});
 });
 const noteLabel=node("label","field-label","Notities / resultaat van de test");
 const textarea=formInput("textarea","Wat is getest? Wat werkt wel of nog niet?","note-area");
 textarea.value=t.notes||"";
 textarea.addEventListener("input",()=>{notesDirty=true;});
 const save=node("button","primary","Notities opslaan");save.type="button";
 save.addEventListener("click",()=>patchTask(t.key,{notes:textarea.value,status:select.value}));
 const verifyBtn=node("button","outline verify-btn",t.status==="verified"?"✓ Eerder afgevinkt":"Samen testen en afvinken");
 verifyBtn.type="button";verifyBtn.setAttribute("aria-pressed",String(t.status==="verified"));
 verifyBtn.addEventListener("click",()=>verify(t));
 edit.append(label,select,noteLabel,textarea,save,verifyBtn);
 if(t.verified_at){
  const verification=node("div","verification","✓ Handmatig bevestigd door "+t.verified_by+" · "+dateText(t.verified_at));
  edit.append(verification);
 }else edit.append(node("p","detail-help","Vink pas af na een echte test. Code- of screenshottests alleen zijn geen bewijs van volledige werking."));
 edit.append(node("div","detail-help","Laatst aangepast: "+dateText(t.updated_at)+" · versie "+t.version));
 wrap.append(edit,divider());
 wrap.append(renderQa(t,data.qa),divider());
 const issueSection=section("Openstaande fouten ("+data.issues.filter(i=>i.state==="open").length+")");
 const issues=data.issues.sort((a,b)=>(a.state==="open"?-1:1)-(b.state==="open"?-1:1)||b.id-a.id);
 if(!issues.length)issueSection.append(node("div","muted","Nog geen handmatige foutmeldingen."));
 for(const i of issues){
  const el=node("div","issue"+(i.state==="closed"?" closed":""));
  const head=node("div","issue-head"),title=node("span","issue-title",i.title);
  const sev=node("span","severity",i.severity.toUpperCase());
  head.append(title,sev);el.append(head);
  if(i.detail)el.append(node("p","",i.detail));
  const foot=node("div","action-row");
  foot.append(node("span","muted",dateText(i.created_at)+" · "+i.created_by));
  const change=node("button","",i.state==="open"?"✓ Opgelost":"Heropenen");
  change.type="button";change.addEventListener("click",()=>setIssueState(i.id,i.state==="open"?"closed":"open"));
  foot.append(change);el.append(foot);issueSection.append(el);
 }
 const newForm=node("form","issue-new");
 const newTitle=formInput("input","Wat gaat er mis?");
 newTitle.required=true;newTitle.maxLength=160;newTitle.minLength=3;
 const newDetails=formInput("textarea","Scherm, knop, verwachte actie en huidige resultaat…");
 newDetails.maxLength=3000;
 const level=node("select");for(const [code,name] of [["medium","Normaal"],["high","Hoog"],["critical","Kritiek"],["low","Laag"]]){
  const opt=node("option","",name);opt.value=code;level.append(opt);
 }
 const add=node("button","primary","+ Fout toevoegen");add.type="submit";
 newForm.append(node("div","section-title","Nieuwe fout registreren"),newTitle,newDetails,level,add);
 newForm.addEventListener("submit",async e=>{
  e.preventDefault();
  try{await request("/api/tasks/"+encodeURIComponent(t.key)+"/issues",{method:"POST",body:JSON.stringify({title:newTitle.value,detail:newDetails.value,severity:level.value})});
   showToast("Fout geregistreerd.");await refreshPreservingUnsavedMain();}
  catch(error){showToast(error.message,true);}
 });
 issueSection.append(newForm);wrap.append(issueSection,divider());
 const diag=section("Ontvangen diagnostiek ("+data.diagnostics.length+" recent)");
 diag.append(node("p","detail-help","Automatisch uit SurvivalOps. Dit zijn observaties; niet elke herkenningsmelding is een bewezen defect."));
 const allDiag=node("button","outline","Alle debug en screenshots bekijken");
 allDiag.type="button";allDiag.addEventListener("click",()=>window.LiveDiagnostics?.open(t.key));diag.append(allDiag);
 if(!data.diagnostics.length)diag.append(node("div","muted","Nog geen diagnostiek aan deze taak gekoppeld."));
 for(const d of data.diagnostics.slice(0,12)){
   const item=node("div","diag "+d.severity);
   item.append(node("div","diag-title",d.kind.replaceAll("-"," ")),
              node("div","diag-meta",dateText(d.timestamp)+" · "+d.activity));
   if(d.has_image){
    const btn=node("a","","Screenshot bekijken");
    btn.href="#";btn.addEventListener("click",e=>{
     e.preventDefault();
     const current=item.querySelector("img");
     if(current){current.remove();btn.textContent="Screenshot bekijken";return;}
     const img=node("img","diag-img");img.alt="Diagnostische screenshot";img.loading="lazy";
     img.src="/api/diagnostic/image?path="+encodeURIComponent(d.path);
     item.append(img);btn.textContent="Screenshot verbergen";
    });item.append(btn);
   }diag.append(item);
 }
 wrap.append(diag,divider());
 const history=section("Wijzigingsgeschiedenis");
 if(!data.events.length)history.append(node("div","muted","Nog geen wijzigingen."));
 for(const e of data.events.slice(0,12)){
  const entry=node("div","event");
  entry.append(node("strong","",e.actor+" · "+dateText(e.occurred_at)),node("div","",e.kind.replaceAll("_"," ")+" · "+e.detail));
  history.append(entry);
 }
 wrap.append(history);
}
async function setIssueState(id,status){
 try{await request("/api/issues/"+id,{method:"PATCH",body:JSON.stringify({state:status})});
   showToast(status==="closed"?"Fout gemarkeerd als opgelost.":"Fout heropend.");await refreshPreservingUnsavedMain();
 }catch(e){showToast(e.message,true);}
}
$("login-form").addEventListener("submit",async e=>{
 e.preventDefault();$("login-error").textContent="";
 try{
  await request("/api/login",{method:"POST",body:JSON.stringify({password:$("password").value})});
  $("password").value="";showApp();await reload({preserveSelection:false,quiet:true});
 }catch(err){$("login-error").textContent=err.message;}
});

$("logout").addEventListener("click",async()=>{
 if(!confirmLeavingEdits())return;
 qaDrafts.clear();notesDirty=false;
 try{await request("/api/logout",{method:"POST",body:"{}"});}catch{}
 showLogin();selected=null;tasks=[];details=null;
});
$("refresh").addEventListener("click",async()=>{
 if(!confirmLeavingEdits())return;
 qaDrafts.clear();notesDirty=false;
 try{await request("/api/sync",{method:"POST",body:"{}"});await reload({quiet:false});}
 catch(e){showToast(e.message,true);}
});
$("search").addEventListener("input",renderList);
$("category").addEventListener("change",renderList);
$("status").addEventListener("change",renderList);
$("only-issues").addEventListener("click",()=>{
 onlyIssues=!onlyIssues;$("only-issues").classList.toggle("toggled",onlyIssues);
 $("only-issues").textContent=onlyIssues?"Toon alle taken":"Alleen aandachtspunten";renderList();
});
window.addEventListener('beforeunload',event=>{
 if(!hasUnsavedEdits())return;
 event.preventDefault();
 event.returnValue=''; // Browser-standard leave-page confirmation.
});
(async()=>{
 try{await request("/api/session");showApp();await reload({preserveSelection:false,quiet:true});}
 catch{showLogin();}
 setInterval(async()=>{
  if($("app").hidden||busy||hasUnsavedEdits()||editingDetailField())return;
  try{await reload({quiet:true,refreshDetail:false});}catch{}
 },60000);
})();