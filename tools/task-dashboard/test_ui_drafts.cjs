"use strict";
// Standalone, dependency-free browser-state regression checks. The runtime's
// actual draft logic and save-all behavior are evaluated, not reimplemented.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'static/app.js'),'utf8');
const head=source.slice(0,source.indexOf('const priority='));
const saving=source.slice(source.indexOf('async function savePendingQaSteps('),source.indexOf('function renderQa('));
const calls=[];
const context=vm.createContext({
 document:{getElementById:()=>null},
 request:async (url,opts)=>{calls.push({url,body:JSON.parse(opts.body)});return {ok:true};}
});
vm.runInContext(head+saving+`
globalThis.hooks={qaValue,setQaDraft,clearQaDraft,hasUnsavedEdits,savePendingQaSteps};`,context);
const {qaValue,setQaDraft,clearQaDraft,hasUnsavedEdits,savePendingQaSteps}=context.hooks;
const t={key:'TRAINING_TROOPS'},qa={round:{id:1},steps:[{id:'preflight'},{id:'navigation'},{id:'recognition'},
 {id:'interaction'},{id:'postcondition'},{id:'return_schedule'},{id:'regression'}],
 checks:['preflight','navigation','recognition','interaction','postcondition','return_schedule','regression'].map(
 step=>({step,result:'pending',note:''}))};
async function run(){
 for(const spec of qa.steps){
  setQaDraft(t.key,qa.round,'result:'+spec.id,'pass','pending');
  setQaDraft(t.key,qa.round,'note:'+spec.id,'Observed success on emulator: '+spec.id,'');
 }
 setQaDraft(t.key,qa.round,'coach_run','20261115-220000-qaabcdef','');
 setQaDraft(t.key,qa.round,'operator_note','My observations remain typed','');
 assert.equal(hasUnsavedEdits(),true);
 assert.equal(qaValue(t.key,qa.round,'note:postcondition',''),
              'Observed success on emulator: postcondition');
 // Simulate clicking Save for one item: the other six remain in the DOM draft.
 clearQaDraft(t.key,qa.round,['result:preflight','note:preflight']);
 assert.equal(qaValue(t.key,qa.round,'note:navigation',''),
              'Observed success on emulator: navigation');
 // Clicking the final confirmation stores every remaining step and the coach
 // run, but must not silently overwrite separately typed operator comments.
 await savePendingQaSteps(t,qa);
 assert.equal(calls.length,7);
 assert.equal(calls[6].url,'/api/tasks/TRAINING_TROOPS/qa/attach');
 assert.equal(calls[6].body.coach_run,'20261115-220000-qaabcdef');
 assert.equal(qaValue(t.key,qa.round,'operator_note',''), 'My observations remain typed');
 assert.equal(qaValue(t.key,qa.round,'note:postcondition',''), '');
 // A failed request may save earlier steps, but later unsaved edits stay put.
 calls.length=0;
 setQaDraft(t.key,qa.round,'note:interaction','New interaction note','');
 setQaDraft(t.key,qa.round,'note:regression','Still typing regression note','');
 context.request=async(url,opts)=>{
   calls.push({url,body:JSON.parse(opts.body)});
   if(url.includes('/regression'))throw Error('network down');
 };
 // Stub failure on the second check (any network/HTTP error).
 context.request=async(url,opts)=>{
   calls.push({url,body:JSON.parse(opts.body)});
   if(calls.length===2)throw Error('network down');
 };
 await assert.rejects(savePendingQaSteps(t,qa),/network down/);
 assert.equal(qaValue(t.key,qa.round,'note:interaction',''), '');
 assert.equal(qaValue(t.key,qa.round,'note:regression',''), 'Still typing regression note');
 assert.equal(hasUnsavedEdits(),true);
 // Periodic refresh explicitly never redraws task details.
 assert.match(source,/reload\(\{quiet:true,refreshDetail:false\}\)/);
 console.log('QA UI draft regression tests passed (full save, partial save, pending input, auto refresh).');
}
run().catch(error=>{console.error(error);process.exitCode=1;});
