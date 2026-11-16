"use strict";
/* Isolated live diagnostic viewer: metadata polling every eight seconds,
   full debug/screenshot retrieved only after a deliberate click. */
(()=>{
  const el=id=>document.getElementById(id);
  const make=(tag,cls,text)=>{
    const x=document.createElement(tag);
    if(cls)x.className=cls;
    if(text!==undefined)x.textContent=String(text);
    return x;
  };
  const statusNames={error:"Fout",warning:"Waarschuwing",notice:"Observatie"};
  const state={
    visible:false,paused:false,task:"all",severity:"all",query:"",hours:24,
    entries:[],selected:null,detail:null,total:0,
    latestAt:null,age:null,globalTotal:0,
    loading:false,detailLoading:false,requestToken:0,detailToken:0
  };
  const fmt=s=>dateText(s);
  const elapsed=n=>{
    if(n===null||n===undefined)return "Nog geen uploads";
    if(n<60)return n+" seconden";
    if(n<3600)return Math.floor(n/60)+" minuten";
    if(n<86400)return Math.floor(n/3600)+" uur";
    return Math.floor(n/86400)+" dagen";
  };
  const formatLines=lines=>lines.map(x=>x.text).join("\n");

  function updateTaskOptions(){
    const select=el("live-task"),old=select.value;
    while(select.options.length>2)select.remove(2);
    for(const t of [...tasks].sort((a,b)=>a.label.localeCompare(b.label,"nl"))){
      const o=make("option","",t.label);
      o.value=t.key;
      select.append(o);
    }
    select.value=[...select.options].some(o=>o.value===old)?old:"all";
  }
  function updateOverview(){
    const list=el("live-overview");
    list.replaceChildren();
    const stats=[
      ["In archief",state.globalTotal.toLocaleString("nl-NL")],
      ["In filter",state.total.toLocaleString("nl-NL")],
      ["Meest recent",state.latestAt?fmt(state.latestAt):"—"]
    ];
    for(const [name,val] of stats){
      const card=make("div","live-metric");
      card.append(make("span","",name),make("strong","",val));
      list.append(card);
    }
    const connected=state.age!==null&&state.age<120;
    const badge=el("live-indicator");
    badge.className="live-indicator "+(connected?"online":"stale");
    badge.textContent=connected?"● Nieuwe uploads aanwezig":"◷ Geen recente uploads";
    const message=el("live-connection");
    if(state.age===null){
      message.className="live-connection stale";
      message.textContent="Er zijn nog geen diagnostische gebeurtenissen ontvangen.";
    }else if(connected){
      message.className="live-connection online";
      message.textContent="Automatische update actief · elke 8 seconden controleren · laatste upload "+elapsed(state.age)+" geleden.";
    }else{
      message.className="live-connection stale";
      message.textContent="Geen nieuwe diagnostiek sinds "+fmt(state.latestAt)+" ("+elapsed(state.age)+" geleden). Historische meldingen blijven zichtbaar. Controleer of SurvivalOps nog loggegevens naar de webserver verstuurt.";
    }
  }

  function renderFeed(){
    const host=el("live-events");
    host.replaceChildren();
    const fragment=document.createDocumentFragment();
    for(const d of state.entries){
      const row=make("button","live-item"+(state.selected===d.path?" active":""));
      row.type="button";
      const badge=make("span","live-severity "+d.severity,statusNames[d.severity]||"Melding");
      const meta=make("div","live-item-meta");
      meta.append(badge,make("span","live-time",fmt(d.timestamp)));
      const title=make("div","live-item-title",d.kind.replaceAll("-"," "));
      const sub=make("div","live-item-sub",(d.task_label||"Algemeen · "+d.activity)+(d.has_image?" · screenshot":""));
      row.append(meta,title,sub);
      row.addEventListener("click",()=>selectDetail(d.path));
      fragment.append(row);
    }
    host.append(fragment);
    el("live-empty").hidden=state.entries.length!==0;
    el("live-more").hidden=state.entries.length>=state.total||state.entries.length===0;
    el("live-results").textContent=state.entries.length+" van "+state.total;
  }

  async function fetchPage({more=false,manual=false}={}){
    if(!state.visible||el("app").hidden||state.loading)return;
    state.loading=true;
    const requestId=++state.requestToken;
    if(manual)el("live-refresh").disabled=true;
    try{
      const q=new URLSearchParams({
        task:state.task,severity:state.severity,q:state.query,hours:String(state.hours),
        offset:more?String(state.entries.length):"0",limit:"40"
      });
      const data=await request("/api/diagnostics?"+q.toString());
      if(requestId!==state.requestToken)return;
      state.total=data.total;
      state.globalTotal=data.global_total;
      state.age=data.seconds_since_latest;
      state.latestAt=data.latest_at;
      if(more){
        const known=new Set(state.entries.map(x=>x.path));
        for(const item of data.items){
          if(!known.has(item.path)){state.entries.push(item);known.add(item.path);}
        }
      }else{
        // Keep older paginated entries when the live feed adds new events.
        const older=state.entries;
        const known=new Set(data.items.map(x=>x.path));
        state.entries=[...data.items,...older.filter(x=>!known.has(x.path))].slice(0,180);
      }
      updateOverview();renderFeed();
      if(!state.selected&&state.entries.length){
        await selectDetail(state.entries[0].path);
      }
    }catch(err){
      const box=el("live-connection");box.className="live-connection stale";
      box.textContent="Verbinding met de diagnostiek mislukt: "+err.message;
      if(manual)showToast(err.message,true);
    }finally{
      if(requestId===state.requestToken){
        state.loading=false;el("live-refresh").disabled=false;
      }
    }
  }

  async function selectDetail(path){
    state.selected=path;
    renderFeed();
    const box=el("live-detail");
    box.replaceChildren(make("div","detail-placeholder","Debuggegevens ophalen…"));
    const stamp=++state.detailToken;
    try{
      const d=await request("/api/diagnostics/detail?path="+encodeURIComponent(path));
      if(!state.visible||stamp!==state.detailToken||state.selected!==path)return;
      state.detail=d;
      renderDetail(d);
    }catch(e){
      if(stamp===state.detailToken)box.replaceChildren(make("div","detail-placeholder","Debuggegevens niet beschikbaar."));
    }
  }

  function renderDetail(d){
    const host=el("live-detail");
    host.replaceChildren();
    const heading=make("div","live-detail-heading");
    heading.append(make("span","live-severity "+d.severity,statusNames[d.severity]||d.severity),
      make("span","muted",fmt(d.timestamp)));
    host.append(heading,make("h3","",d.kind.replaceAll("-"," ")));
    const refs=make("div","live-detail-ref",d.activity+" · "+(d.platform||"Onbekend platform"));
    host.append(refs);
    if(d.task_key){
      const button=make("button","outline","Open taak: "+(d.task_label||d.task_key));
      button.type="button";
      button.addEventListener("click",()=>{
        close();
        selectTask(d.task_key);
      });
      host.append(button);
    }else{
      host.append(make("p","detail-help","Algemene herkennings- of systeemmelding; niet automatisch aan een taak gekoppeld."));
    }

    const imageHeading=make("div","section-title","Screenshot");
    host.append(imageHeading);
    if(d.has_image){
      const pic=make("img","live-screenshot");
      pic.alt="Screenshot op het moment van deze diagnose";
      pic.loading="lazy";
      pic.src="/api/diagnostic/image?path="+encodeURIComponent(d.path);
      const holder=make("div","live-screenshot-container");
      holder.append(pic);
      const expand=make("button","outline","Vergroot screenshot");
      expand.type="button";
      expand.addEventListener("click",()=>lightbox(pic.src));
      host.append(holder,expand);
    }else{
      host.append(make("div","muted","Bij deze melding is geen screenshot opgeslagen."));
    }
    const lineHeader=make("div","live-debug-heading");
    lineHeader.append(make("strong","", "Debug- en actiegeschiedenis"));
    const copy=make("button","ghost","Kopieer logregels");
    copy.type="button";
    copy.addEventListener("click",async()=>{
      try{await navigator.clipboard.writeText(formatLines(d.log_lines));showToast("Debugregels gekopieerd.");}
      catch{showToast("Kopiëren niet toegestaan door browser.",true);}
    });
    lineHeader.append(copy);host.append(lineHeader);
    if(!d.log_lines.length){
      host.append(make("div","muted","Geen extra logregels beschikbaar bij deze diagnose."));
    }else{
      host.append(make("div","live-log-note",
        d.log_lines.length+" meegezonden logregels. Dit fragment kan ook voorgaande taken bevatten; toegangscodes worden afgeschermd."));
      const lines=make("div","live-log-lines");
      for(const x of d.log_lines){
        const line=make("div","live-log-line");
        const level=make("span","live-log-level "+(x.level||"LOG").toLowerCase(),x.level);
        const content=make("span","live-log-text",x.text);
        line.append(level,content);
        lines.append(line);
      }
      host.append(lines);
    }
    const foot=make("div","live-debug-foot","Bron: diagnostische upload · "+d.path);
    host.append(foot);
  }

  function lightbox(src){
    const overlay=make("div","live-lightbox");
    overlay.setAttribute("role","dialog");
    overlay.setAttribute("aria-modal","true");
    overlay.setAttribute("aria-label","Screenshot vergroten");
    const img=make("img");
    img.alt="Uitvergrote diagnostische screenshot";
    img.src=src;
    const btn=make("button","live-lightbox-close","✕ Sluiten");
    btn.type="button";
    const closeWindow=()=>{overlay.remove();document.removeEventListener("keydown",esc);};
    const esc=e=>{if(e.key==="Escape")closeWindow();};
    btn.addEventListener("click",closeWindow);
    overlay.addEventListener("click",e=>{if(e.target===overlay)closeWindow();});
    document.addEventListener("keydown",esc);
    overlay.append(btn,img);
    document.body.append(overlay);
    btn.focus();
  }

  function initFilters(){
    updateTaskOptions();
    el("live-task").value=state.task;
    el("live-severity").value=state.severity;
    el("live-period").value=String(state.hours);
    el("live-search").value=state.query;
  }
  async function open(task="all"){
    if(notesDirty&&!confirm("Je niet-opgeslagen notities verlaten?"))return;
    window.TemplateAudit?.close();
    window.AutoQA?.close();
    notesDirty=false;
    state.visible=true;
    state.selected=null;state.detail=null;
    state.task=task;
    state.entries=[];state.total=0;
    el("workspace").hidden=true;
    el("stats").hidden=true;
    el("live-section").hidden=false;
    el("live-nav").textContent="▦ Takenoverzicht";
    el("live-nav").classList.add("live-active");
    el("live-detail").replaceChildren(make("div","detail-placeholder","Selecteer een gebeurtenis om debug en screenshots te bekijken."));
    initFilters();
    await fetchPage({manual:true});
    if(window.matchMedia("(max-width:900px)").matches)
      el("live-section").scrollIntoView({behavior:"smooth",block:"start"});
  }
  function close(){
    state.visible=false;
    state.requestToken++;
    state.detailToken++;
    state.loading=false;
    el("live-refresh").disabled=false;
    el("live-section").hidden=true;
    el("workspace").hidden=false;
    el("stats").hidden=false;
    el("live-nav").textContent="◉ Live diagnostiek";
    el("live-nav").classList.remove("live-active");
  }
  function applyFilters(){
    state.task=el("live-task").value;
    state.severity=el("live-severity").value;
    state.hours=Number(el("live-period").value);
    state.query=el("live-search").value.trim().slice(0,100);
    state.requestToken++;
    state.loading=false;
    state.detailToken++;
    state.entries=[];state.selected=null;state.detail=null;
    el("live-detail").replaceChildren(make("div","detail-placeholder","Selecteer een melding…"));
    fetchPage({manual:true});
  }

  el("live-nav").addEventListener("click",()=>state.visible?close():open());
  el("live-back").addEventListener("click",close);
  el("live-refresh").addEventListener("click",()=>fetchPage({manual:true}));
  el("live-more").addEventListener("click",()=>fetchPage({more:true,manual:true}));
  el("live-task").addEventListener("change",applyFilters);
  el("live-severity").addEventListener("change",applyFilters);
  el("live-period").addEventListener("change",applyFilters);
  let searchTimer;
  el("live-search").addEventListener("input",()=>{
    clearTimeout(searchTimer);searchTimer=setTimeout(applyFilters,300);
  });
  el("live-pause").addEventListener("click",()=>{
    state.paused=!state.paused;
    el("live-pause").textContent=state.paused?"▶ Hervatten":"⏸ Pauzeren";
    el("live-pause").setAttribute("aria-pressed",String(state.paused));
    if(!state.paused)fetchPage();
  });
  setInterval(()=>{
    if(!state.visible||el("app").hidden||document.hidden||state.paused)return;
    // As uploads age, update the freshness indicator between real refreshes.
    fetchPage();
  },8000);
  window.LiveDiagnostics={open,close};
})();
