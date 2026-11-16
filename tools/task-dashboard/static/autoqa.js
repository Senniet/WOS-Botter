"use strict";
(()=>{
 const $=id=>document.getElementById(id);
 let visible=false,selected=null,loading=false;
 const node=(tag,cls,text)=>{const x=document.createElement(tag);if(cls)x.className=cls;if(text!==undefined)x.textContent=String(text);return x;};
 const labels={queued:"In wachtrij",running:"Wordt uitgevoerd",observed:"Screenshots verzameld",navigation_verified:"Exploration-navigatie bevestigd",blocked:"Geblokkeerd",interrupted:"Onderbroken"};
 async function api(path,options){
   const r=await fetch(path,{credentials:"same-origin",cache:"no-store",...options});
   if(!r.ok){let message="HTTP "+r.status;try{message=(await r.json()).error||message;}catch{}throw Error(message);}
   return r.json();
 }
 function format(iso){try{return new Date(iso).toLocaleString("nl-NL");}catch{return iso||"";}}
 function options(){
   const target=$("autoqa-task");if(target.options.length>0)return;
   for(const t of [...tasks].sort((a,b)=>a.label.localeCompare(b.label,"nl"))){
     const opt=node("option","",t.label);opt.value=t.key;target.append(opt);
   }
   $("autoqa-navigate").hidden=target.value!=="DO_EXPLORATION";
 }
 async function open(){
   if(typeof hasUnsavedEdits==="function" && hasUnsavedEdits() && !confirm("Niet-opgeslagen testnotities verlaten?"))return;
   window.LiveDiagnostics?.close();window.TemplateAudit?.close();
   $("workspace").hidden=true;$("stats").hidden=true;
   $("autoqa-section").hidden=false;$("autoqa-nav").textContent="▦ Takenoverzicht";
   visible=true;options();await refresh();
 }
 function close(){
   if(!visible)return;
   visible=false;$("autoqa-section").hidden=true;
   $("workspace").hidden=false;
   $("stats").hidden=false;
   $("autoqa-nav").textContent="▣ Autotest";
 }
 async function refresh(){
   if(!visible||loading)return;loading=true;
   try{
     const data=await api('/api/autoqa');
     const d=data.device;
     $("autoqa-device").textContent=d?`Testemulator: ${d.serial} · Android ${d.api} · ${d.size} · ${d.density} · spel actief · 62 observatieworkflows, Exploration-navigatie beperkt beschikbaar`:'Nog geen testemulator vastgezet';
     $("autoqa-enqueue").disabled=!d;
     const host=$("autoqa-runs");host.replaceChildren();
     for(const run of data.runs){
       const button=node("button","autoqa-run"+(selected===run.id?" active":""));
       button.type="button";
       button.append(node("strong","",run.task),node("span","",labels[run.state]||run.state),
        node("small","",format(run.created_at)+" · "+run.checks+" checks · "+run.actions+" acties"));
       button.addEventListener("click",()=>showDetail(run.id));host.append(button);
     }
     if(!data.runs.length)host.append(node("p","muted","Nog geen tests uitgevoerd."));
     if(selected && data.runs.some(r=>r.id===selected))await showDetail(selected);
   }catch(e){$("autoqa-feedback").textContent="Testcontroller niet bereikbaar: "+e.message;}
   finally{loading=false;}
 }
 async function showDetail(id){
   selected=id;const box=$("autoqa-detail");
   try{
     const d=await api('/api/autoqa/detail?id='+encodeURIComponent(id));
     box.replaceChildren();box.append(node("h4","",d.run.task),
       node("p","",(labels[d.run.state]||d.run.state)+" · "+(d.run.reason||"")));
     for(const image of d.result?.screens||[]){
       if(!['before','after'].includes(image.name))continue;
       const figure=node("figure","autoqa-proof"),pic=node("img","");
       pic.src='/api/autoqa/image?id='+encodeURIComponent(id)+'&name='+image.name;
       pic.alt='Automatisch vastgelegde '+image.name+'-screenshot';pic.loading='lazy';
       const match=image.recognition;
       figure.append(node("figcaption","",image.name+" · "+(match?.state||"onbekend")+
           " · score "+(match?.score??"—")+" · "+(image.capture_ms??"—")+
           "ms screenshot · "+(image.recognition_ms??"—")+"ms herkennen"),pic);
       box.append(figure);
     }
     for(const candidate of d.result?.proposals||[]){
       const wrap=node("figure","autoqa-proof"),img=node("img","");
       img.src='/api/autoqa/image?id='+encodeURIComponent(id)+'&name=candidate';
       img.alt='Concept-template, wacht op positieve en negatieve test';
       wrap.append(node("figcaption","","Voorstel voor onbekend scherm · stabiliteit "+candidate.stability_score+
            " · NIET ACTIEF, beoordeling vereist"),img);box.append(wrap);
     }
     if(d.result?.events){
       const list=node("ul","");for(const event of d.result.events)list.append(node("li","",event.type+": "+event.detail));box.append(list);
     }
   }catch(e){box.textContent=e.message;}
 }
 async function enqueue(mode="observe"){
   const b=mode==="navigate"?$("autoqa-navigate"):$("autoqa-enqueue");b.disabled=true;
   try{
     const data=await api('/api/autoqa/enqueue',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({task:$("autoqa-task").value,mode})});
     $("autoqa-feedback").textContent=(mode==="observe"?'Observatie':'Beperkte navigatietest')+' in wachtrij: '+data.id;
     selected=data.id;await refresh();
   }catch(e){$("autoqa-feedback").textContent='Starten mislukt: '+e.message;}
   finally{b.disabled=false;}
 }
 $("autoqa-nav").addEventListener("click",()=>visible?close():open());
 $("autoqa-back").addEventListener("click",close);
 $("autoqa-refresh").addEventListener("click",refresh);
 $("autoqa-enqueue").addEventListener("click",()=>enqueue("observe"));
 $("autoqa-navigate").addEventListener("click",()=>enqueue("navigate"));
 $("autoqa-task").addEventListener("change",()=>{
   $("autoqa-navigate").hidden=$("autoqa-task").value!=="DO_EXPLORATION";
 });
 setInterval(()=>{if(visible&&!$("app").hidden&&!document.hidden)refresh();},12000);
 window.AutoQA={open,close,refresh};
})();
