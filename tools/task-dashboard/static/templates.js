"use strict";
/* Read-only template inventory; no screenshot, emulator or repository actions. */
(()=>{
 const el=id=>document.getElementById(id);
 const state={visible:false,data:null,shown:100,selected:"",loading:false};
 const statuses={missing_asset:"Bestand ontbreekt",invalid_png:"Ongeldig PNG",
   observed_hit:"Herkenning gezien",review_observed_misses:"Alleen missers",not_observed:"Geen runbewijs"};
 const node=(tag,cls,txt)=>{
   const n=document.createElement(tag);
   if(cls)n.className=cls;
   if(txt!==undefined)n.textContent=String(txt);
   return n;
 };
 const summary=(name,value)=>{
   const n=node("div","audit-stat");n.append(node("span","",name),node("strong","",value));return n;
 };
 const setView=(visible)=>{
   state.visible=visible;
   el("template-section").hidden=!visible;
   el("workspace").hidden=visible;
   el("stats").hidden=visible;
   el("template-nav").textContent=visible?"▦ Takenoverzicht":"▧ Templates";
 };
 async function refresh(){
   if(state.loading)return;
   state.loading=true;
   try{
     const response=await fetch("/api/template-audit",{credentials:"same-origin",cache:"no-store"});
     if(!response.ok)throw new Error("Rapport nog niet beschikbaar (HTTP "+response.status+")");
     state.data=await response.json();
     if(!state.data||!Array.isArray(state.data.templates))throw new Error("Ongeldig templateoverzicht");
     const areas=[...new Set(state.data.templates.map(x=>x.area))].sort();
     const select=el("template-area"),old=select.value;
     select.replaceChildren(node("option","","Alle categorieën"));
     for(const a of areas){const option=node("option","",a);option.value=a;select.append(option);}
     select.value=areas.includes(old)?old:"";
     render();
   }catch(error){
     el("template-summary").replaceChildren(node("div","audit-error",error.message));
     el("template-list").replaceChildren();
     el("template-count").textContent="Geen bijgewerkt rapport beschikbaar.";
   }finally{state.loading=false;}
 }
 function details(t){
   const info=node("div","audit-detail");
   const obs=t.observations||{};
   info.append(node("div","","Pad: "+t.resource),
     node("div","","Codegebruik: "+((t.source_uses||[]).join(", ")||"geen directe enum-verwijzing")),
     node("div","","Gedeelde afbeelding: "+((t.shared_with||[]).join(", ")||"geen andere template-ID")),
     node("div","","Herkomst: "+({original_unchanged:"origineel, ongewijzigd",modified:"aangepast",new:"nieuwe afbeelding"}[t.upstream]||t.upstream)),
     node("div","","Afmetingen: "+(t.dimensions?t.dimensions.join(" × "):"niet beschikbaar")),
     node("div","","Task Coach: "+(obs.hits||0)+" herkenningen / "+(obs.checks||0)+" methodemetingen in "+(obs.sessions||0)+" sessies"),
     node("div","","Taken met waarnemingen: "+((obs.tasks||[]).join(", ")||"nog geen")));
   return info;
 }
 function render(){
   const data=state.data;if(!data)return;
   const s=data.summary;
   el("template-summary").replaceChildren(
     summary("Geregistreerd",s.registered),summary("Ontbrekende afbeeldingen",s.missing),
     summary("Origineel, ongewijzigd",s.registered_unchanged_original),
     summary("Met herkenning",s.templates_with_positive_hits),
     summary("Alleen missers in sample",s.templates_only_observed_misses),
     summary("Coach-sessies",s.coach_sessions));
   const query=el("template-query").value.toLowerCase().trim();
   const area=el("template-area").value,status=el("template-status").value;
   const priority={missing_asset:0,invalid_png:0,review_observed_misses:1,not_observed:2,observed_hit:3};
   const filtered=data.templates.filter(t=>
     (!area||t.area===area)&&(!status||t.status===status)&&
     (!query||(t.name+" "+t.resource+" "+(t.source_uses||[]).join(" ")).toLowerCase().includes(query)));
   filtered.sort((a,b)=>(priority[a.status]??4)-(priority[b.status]??4)||a.name.localeCompare(b.name));
   el("template-count").textContent=filtered.length+" templates gevonden · overzicht " +
      new Date(data.generated_utc).toLocaleString("nl-NL");
   const list=el("template-list"),frag=document.createDocumentFragment();
   for(const t of filtered.slice(0,state.shown)){
     const wrap=node("div","audit-entry");
     const button=node("button","audit-row");button.type="button";
     button.append(node("span","audit-name",t.name),
                   node("span","audit-area",t.area),
                   node("span","audit-status "+t.status,statuses[t.status]||"Onbekend"),
                   node("span","audit-hits",(t.observations?.hits||0)+" / "+(t.observations?.checks||0)));
     const expanded=state.selected===t.name;
     button.setAttribute("aria-expanded",String(expanded));
     wrap.append(button);
     if(expanded)wrap.append(details(t));
     button.addEventListener("click",()=>{state.selected=expanded?"":t.name;render();});
     frag.append(wrap);
   }
   list.replaceChildren(frag);
   el("template-more").hidden=filtered.length<=state.shown;
 }
 async function open(){
   if(notesDirty&&!confirm("Je niet-opgeslagen notities verlaten?"))return;
   notesDirty=false;
   window.LiveDiagnostics?.close();
   setView(true);
   if(!state.data)await refresh();else render();
 }
 function close(){if(!state.visible)return;setView(false);}
 el("template-nav").addEventListener("click",()=>state.visible?close():open());
 el("template-back").addEventListener("click",close);
 el("template-refresh").addEventListener("click",refresh);
 for(const id of ["template-query","template-area","template-status"]){
   el(id).addEventListener(id==="template-query"?"input":"change",()=>{state.shown=100;state.selected="";render();});
 }
 el("template-more").addEventListener("click",()=>{state.shown+=100;render();});
 window.TemplateAudit={open,close};
})();
