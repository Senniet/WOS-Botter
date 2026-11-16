"use strict";
/* All remote data rendered as plain text; authenticated images are whitelisted. */
(()=>{
 const el=id=>document.getElementById(id);
 const make=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=String(text);return n;};
 const url=(p,x)=>p+'?'+new URLSearchParams(x);
 let choices={},activeTask='',loaded=false;
 async function get(path){const r=await fetch(path,{credentials:'same-origin',cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);return r.json();}
 async function post(path,body){const r=await fetch(path,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  const result=await r.json();if(!r.ok)throw Error(result.error||'HTTP '+r.status);return result;}
 function labelChoice(name){return choices[name]==='hidden'?'Verborgen in lijst':choices[name]==='removal_review'?'Verwijderkandidaat':'Niet gemarkeerd';}
 function decorateTemplate(parent,t){
   const img=make('img','inspect-template-img');img.src=url('/api/inspector/template-image',{name:t.name});
   img.alt='Werkelijke templateafbeelding '+t.name;img.loading='lazy';parent.append(img);
   const bar=make('div','inspect-actions'),state=make('span','',labelChoice(t.name));bar.append(state);
   const actions=[['hidden','Verberg in mijn lijst'],['removal_review','Markeer voor opschonen'],['restore','Herstel']];
   for(const [action,title] of actions){
     const b=make('button','outline',title);b.type='button';
     b.disabled=action==='removal_review'&&t.runtime_role==='protected_guard';
     b.addEventListener('click',async()=>{
       try{const result=await post('/api/inspector/review',{name:t.name,action});
         if(action==='restore')delete choices[t.name];else choices[t.name]=action;
         state.textContent=labelChoice(t.name)+' · productie ongewijzigd';
         window.dispatchEvent(new Event('template-review-changed'));
       }catch(e){state.textContent='Niet opgeslagen: '+e.message;}
     });bar.append(b);
   }
   parent.append(bar,nodeHint());
 }
 function nodeHint(){return make('p','muted','Verbergen en opschoningsverzoeken beïnvloeden alleen het QA-overzicht. Geen bestanden of runtime-herkenningen worden verwijderd.');}
 async function refreshChoices(){try{choices=(await get('/api/inspector/review')).choices||{};}catch{choices={};}}
 async function ocr(){const host=el('inspector-ocr');host.textContent='OCR-gegevens laden…';
   try{const d=await get('/api/inspector/ocr');host.replaceChildren();
     const c=d.counts||{};const title=make('p','',
       'Lege OCR-tekst: '+(c.empty_text||0)+' · afgewezen tekst: '+(c.text_rejected||0)+
       ' · vastloop/fouten bij schermopname: '+(c.capture_or_transport_error||0)+
       ' · overige OCR-enginefouten: '+(c.engine_exception||0)+
       ' · afzonderlijke mislukte pogingen: '+(c.trial_exceptions||0));host.append(title);
     host.append(make('p','muted','Gedetecteerde mislukte ADB-screenshots: '+(c.adb_screencap_failures||0)+
       ' (ook deze regels kunnen in meerdere logbestanden voorkomen).'));
     host.append(make('p','muted','Bron: '+d.log_scope+' Taalbestanden: '+d.bundled_languages.join(', ')+
       '. Een lege of verkeerde uitsnede is geen bewezen Tesseract-crash. OCR op jouw Windows-pc is hiermee niet rechtstreeks getest.'));
     for(const ex of (d.samples||[]).slice(0,6))host.append(make('div','inspect-small',ex.source_day+' · '+ex.category+' · '+ex.failure));
   }catch(e){host.textContent='OCR-rapport niet bereikbaar: '+e.message;}}
 async function extras(){const host=el('inspector-extras');host.replaceChildren();
   try{const data=await get('/api/inspector/extras');
     for(const file of data.files){const figure=make('figure','');const img=make('img','');
       img.loading='lazy';img.src=url('/api/inspector/extra-image',{path:file});img.alt=file;
       figure.append(img,make('figcaption','',file));host.append(figure);
     }
     if(!data.files.length)host.textContent='Geen extra niet-geregistreerde PNGs.';
   }catch(e){host.textContent='Extra afbeeldingen niet beschikbaar: '+e.message;}}
 async function sessions(){
   const task=el('inspector-task').value,select=el('inspector-run');select.replaceChildren();
   try{const data=await get(url('/api/inspector/sessions',{task}));
     for(const session of data.sessions){const o=make('option','',session.id+' ('+session.capture_count+' beelden)');o.value=session.id;select.append(o);}
     if(!data.sessions.length)select.append(make('option','','Geen runs gevonden'));
   }catch(e){select.append(make('option','',e.message));}}
 async function flow(){const task=el('inspector-task').value,host=el('inspector-flow');host.textContent='Broncodeflow lezen…';
   try{const d=await get(url('/api/inspector/flow',{task}));host.replaceChildren();
     host.append(make('h4','',d.label),make('div','inspect-small','Bron: '+(d.source||'niet gevonden')));
     host.append(make('p','muted',d.description||'Broncodeflow niet beschikbaar.'));
     if(d.annotated_stages?.length){
       host.append(make('h4','', 'Training: volledige logische volgorde'));
       const annotated=make('ol','inspect-steps');
       for(const stage of d.annotated_stages){
         const li=make('li','');li.append(make('strong','',stage.label),
           make('div','inspect-small',stage.method+'() · '+stage.condition));annotated.append(li);
       }host.append(annotated);
     }
     host.append(make('h4','','Rechtstreekse methode-aanroepen uit execute()'));
     const ol=make('ol','inspect-steps');for(const step of d.steps||[]){
       const li=make('li','',step.method+'()'+(step.conditional?' — afhankelijk van de spelstatus':''));
       li.title='Java-bronregel '+step.line;ol.append(li);
     }host.append(ol);
     host.append(make('p','inspect-small','Templates rechtstreeks in deze bron: '+(d.templates?.length||0)));
     const set=make('div','inspect-template-tags');for(const name of (d.templates||[]).slice(0,70)){
       const b=make('button','outline',name);b.type='button';
       b.addEventListener('click',()=>{const input=el('template-query');input.value=name;input.dispatchEvent(new Event('input'));input.scrollIntoView({behavior:'smooth',block:'center'});});set.append(b);
     }host.append(set);
   }catch(e){host.textContent=e.message;}}
 async function run(){const id=el('inspector-run').value,host=el('inspector-run-result');if(!id)return;
   host.textContent='Run laden…';
   try{const d=await get(url('/api/inspector/run',{id}));host.replaceChildren();
     host.append(make('h4','',d.task+' · '+d.id),make('p','muted',
       'Scheduler: '+d.scheduler_outcome+' · dit is geen bewijs van voltooiing in de game.'));
     const summary=make('details','inspect-sub');summary.open=true;summary.append(make('summary','',
       'Alle gebruikte afbeeldingen ('+d.templates.length+') en herkenningsmethoden'));
     for(const t of d.templates){
       const row=make('div','inspect-template-row');
       const b=make('button','outline',t.name);b.type='button';
       b.addEventListener('click',()=>{const input=el('template-query');input.value=t.name;input.dispatchEvent(new Event('input'));});
       row.append(b,make('span','',t.hits+'/'+t.checks+' match · '+t.max_score+'% maximaal · '+
          Object.entries(t.methods).map(([k,v])=>k+': '+v).join(' / ')));summary.append(row);
     }host.append(summary);
     const captures=make('details','inspect-sub');captures.open=true;captures.append(make('summary','',
       'Screenshots uit deze run ('+d.captures.length+')'));
     const gallery=make('div','inspect-gallery');
     for(const file of d.captures){
       const figure=make('figure','');const image=make('img','');
       image.src=url('/api/inspector/run-image',{id,file});image.alt=file;image.loading='lazy';
       figure.append(image,make('figcaption','',file));gallery.append(figure);
     }captures.append(gallery);host.append(captures);
     const detections=make('details','inspect-sub');detections.append(make('summary','',
       'Chronologische controlepunten ('+d.records.length+')'));
     detections.append(make('p','inspect-small',
       'Klik op een regel om het bijbehorende vastgelegde scherm te openen. Groene stip = gevonden klikpunt, als die screenshot bestaat. Een miss is niet automatisch een defect.'));
     const sample=make('div','inspect-stage-view');const shot=make('img','');
     const dot=make('span','inspect-stage-dot');dot.hidden=true;
     const description=make('div','inspect-small','Klik op een herkenningsregel.');
     sample.append(shot,dot);detections.append(description,sample);
     const list=make('div','inspect-records');
     for(const item of d.records){
       const line=make('button','inspect-record '+(item.hit?'inspect-hit':'inspect-miss'),
         item.time.slice(11,19)+' · '+item.template+' · '+item.method+
         ' · '+item.score+'% '+(item.hit?'HERKEND':'geen match')+
         (item.hit?' · ('+item.x+','+item.y+')':''));line.type='button';
       line.addEventListener('click',()=>{
         const fragment=item.template.toLowerCase().replaceAll('_','-');
         const simplified=item.template.toLowerCase().replaceAll('_','');
         const file=d.captures.find(f=>f.includes(fragment)||f.replaceAll('-','').includes(simplified));
         if(!file){description.textContent='Geen opname van precies deze controle opgeslagen; er wordt geen bijpassend scherm verzonnen.';
           sample.hidden=true;return;}
         sample.hidden=false;shot.src=url('/api/inspector/run-image',{id,file});
         shot.alt='Bewaarde screenshot '+file;description.textContent=file+' · '+item.template+' · '+item.score+'%';
         const x=Number(item.x),y=Number(item.y);
         dot.hidden=!item.hit||!Number.isFinite(x)||!Number.isFinite(y)||x<0||y<0;
         if(!dot.hidden){dot.style.left=(x/720*100)+'%';dot.style.top=(y/1280*100)+'%';}
       });list.append(line);
     }detections.append(list);host.append(detections);
   }catch(e){host.textContent='Kon run niet laden: '+e.message;}}
 async function open(){await refreshChoices();if(!loaded){
     const target=el('inspector-task');
     for(const t of [...tasks].sort((a,b)=>a.label.localeCompare(b.label,'nl'))){const o=make('option','',t.label);o.value=t.key;target.append(o);}
     target.value='TRAINING_TROOPS';loaded=true;
   }
   await Promise.all([ocr(),sessions(),extras()]);await flow();}
 el('inspector-task').addEventListener('change',()=>{sessions();flow();});
 el('inspector-flow-btn').addEventListener('click',flow);
 el('inspector-run-btn').addEventListener('click',run);
 el('inspector-ocr-refresh').addEventListener('click',ocr);
 window.VisualInspector={open,decorateTemplate,isHidden:name=>choices[name]==='hidden'};
})();
