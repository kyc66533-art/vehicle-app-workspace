// Only pure local checks. Never invokes Wrangler, external fetch, or the deployment main function.
import fs from'node:fs';import assert from'node:assert/strict';
import{validateConfigs,invariant,publicInvariant,confirmation,resumeAllowed,quotaResumeAllowed,numeric,verifyWaitingCode,verifyTrialUsage}from'./apply-operations.mjs';
import{INITIAL_RECURRING}from'./prepare-recurring-policy.mjs';
const off=JSON.parse(fs.readFileSync(new URL('../../../.tools/worker-operations/model-off.json',import.meta.url),'utf8')),on=JSON.parse(fs.readFileSync(new URL('../../../.tools/worker-operations/model-on.json',import.meta.url),'utf8'));
let n=0;function good(fn){assert.doesNotThrow(fn);n++}function bad(fn){assert.throws(fn);n++}
const hash='8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123';
good(()=>validateConfigs(off,on,hash));bad(()=>validateConfigs(off,on,'unapproved'));
for(const mutate of [c=>c.name='wrong-worker',c=>c.keep_vars=false,c=>c.vars.MODEL_ENABLED='true',c=>c.vars.PORTABLE_POLICY='forbidden',c=>c.routes=['unapproved'],c=>c.durable_objects.bindings[0].class_name='AnotherGate',c=>c.vars.RECURRING_POLICY=JSON.stringify({...INITIAL_RECURRING,user_daily:6})]){const c=structuredClone(off);mutate(c);bad(()=>validateConfigs(c,on,hash));}
const before={resources:{bindings:[{name:'BUDGET',type:'durable_object_namespace',class_name:'BudgetGate',namespace_id:'LOCAL_FIXTURE_NAMESPACE'},{name:'GEMINI_API_KEY',type:'secret_text'},{name:'PORTABLE_POLICY',type:'secret_text'},{name:'FIREBASE_PROJECT_ID',type:'plain_text',text:'caritda'}]}};
const after=structuredClone(before);after.resources.bindings.push(...Object.entries(off.vars).map(([name,text])=>({name,type:'plain_text',text})));
good(()=>invariant(before,after,false));
for(const mutate of [v=>v.resources.bindings[0].namespace_id='different',v=>v.resources.bindings=v.resources.bindings.filter(x=>x.name!=='GEMINI_API_KEY'),v=>v.resources.bindings.find(x=>x.name==='FIREBASE_PROJECT_ID').text='different',v=>v.resources.bindings.find(x=>x.name==='MODEL_ENABLED').text='true',v=>v.resources.bindings.find(x=>x.name==='RECURRING_POLICY').text='{}']){const v=structuredClone(after);mutate(v);bad(()=>invariant(before,v,false));}
const health={status:200,body:{release:'caritda-portable-ai-v2',features:['selected-issue-v1','firebase-keys-manual-v1'],auth_required:true,portable_enabled:true,model_enabled:false}},unauth={status:401,body:{error:{code:'AUTH_REQUIRED'}}};
good(()=>publicInvariant(health,unauth,false));bad(()=>publicInvariant(health,{status:200,body:{}},false));bad(()=>publicInvariant(health,unauth,true));bad(()=>publicInvariant({...health,body:{...health.body,auth_required:false}},unauth,false));
good(()=>assert.deepEqual(confirmation('OK 1 2 4',3),[1,2,4]));bad(()=>confirmation('FAIL MODEL_HTTP_ERROR',3));bad(()=>confirmation('OK -1 2',2));bad(()=>confirmation('OK 1 2 private-token',2));
good(()=>assert.equal(resumeAllowed({stage:'STOPPED',code:'APP_CHECK_FAILED',trial_instruction_issued:false}),true));
good(()=>assert.equal(resumeAllowed({stage:'STOPPED',code:'APP_CHECK_FAILED',trial_instruction_issued:true}),false));
good(()=>assert.equal(resumeAllowed({stage:'STOPPED',code:'APP_CHECK_FAILED',before_usage:{legacy:1,portable:1}}),false));
good(()=>assert.equal(numeric(' 3 '),3));good(()=>assert.equal(numeric('OK 3'),null));good(()=>assert.equal(numeric(''),null));
const waiting={stage:'WAITING_QUOTA',quota_wait_confirmed:true,trial_instruction_issued:false,off_version:{},before_usage:{legacy:1,portable:5},off_usage:{legacy:1,portable:5,remaining:0}};
good(()=>assert.equal(quotaResumeAllowed(waiting),true));good(()=>assert.equal(quotaResumeAllowed({...waiting,trial_instruction_issued:true}),false));good(()=>assert.equal(quotaResumeAllowed({...waiting,off_usage:{legacy:1,portable:0,remaining:0}}),false));
good(()=>verifyWaitingCode({off_version:{script:{etag:'fixture-code'}}},{script:{etag:'fixture-code'}}));bad(()=>verifyWaitingCode({off_version:{script:{etag:'fixture-code'}}},{script:{etag:'changed-code'}}));bad(()=>verifyWaitingCode({},{script:{etag:'fixture-code'}}));
good(()=>verifyTrialUsage([1,5],[1,6,4],5,true));
bad(()=>verifyTrialUsage([1,5],[1,5,5],5,true)); // cached result, no new request
bad(()=>verifyTrialUsage([1,5],[1,7,3],5,true)); // extra request
bad(()=>verifyTrialUsage([1,5],[0,6,4],5,true)); // legacy ledger changed
bad(()=>verifyTrialUsage([1,5],[1,6,3],5,true)); // review consumed quota
bad(()=>verifyTrialUsage([1,5],[1,6,-1],5,false));
good(()=>verifyTrialUsage([1,5],[1,6,5],5,false)); // KST day changed, monthly usage remains
console.log('APPROVED_OPERATIONS_ORCHESTRATION_PASS checks='+n+' deployments=0 model_calls=0');
