// Existing Worker code update only. No Secret/policy/model writes, DO reset or model request.
import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';import {execFileSync} from 'node:child_process';import ts from 'typescript';
const root=fileURLToPath(new URL('../',import.meta.url)),workspace=path.resolve(root,'../..'),folder=path.join(workspace,'.tools/voice-automation');
const config='wrangler.existing.jsonc',name='caritda-ai-relay-dev',origin='https://caritda-ai-relay-dev.kyc66533.workers.dev';
export const APPROVED_DIALOGUE_BUNDLE='327b6f8b8715e71aa9c809a8ea0c85b451c183931a267e83e12c952f7f1fb833';
// Last audited operational deployment. A parallel unpublished Worker fix must be reviewed first.
export const APPROVED_BEFORE_VERSION='200edeff-41b1-4454-9d40-b0d2809014e8';
export const APPROVED_ISSUED_READ_BUNDLE='ecde6a83bf161040834af3094c3821cb04c77696228bd28df3f6632c76c5a883';
export const APPROVED_ISSUED_READ_BEFORE_VERSION='fb39412c-11f2-4080-b30f-b4751d71731f';
export const APPROVED_CONTEXT_BUNDLE='4643c387ccce7a9056f12a3874a23abf3a75eea79e68779994c94b600498ca19';
export const APPROVED_CONTEXT_BEFORE_VERSION='b0f65833-546d-4e5c-9a0c-4a5071f45ce7';
export function deploymentPlan(issuedReadFix=false){
 if(issuedReadFix==='context')return {bundle:APPROVED_CONTEXT_BUNDLE,before:APPROVED_CONTEXT_BEFORE_VERSION,journal:'dialogue-context-deployment.json'};
 return issuedReadFix
  ?{bundle:APPROVED_ISSUED_READ_BUNDLE,before:APPROVED_ISSUED_READ_BEFORE_VERSION,journal:'dialogue-issued-read-deployment.json'}
  :{bundle:APPROVED_DIALOGUE_BUNDLE,before:APPROVED_BEFORE_VERSION,journal:'dialogue-deployment.json'};
}
const ensure=(ok,code)=>{if(!ok)throw Error(code)},sha=value=>createHash('sha256').update(value).digest('hex');
const resources=value=>(value?.result??value)?.resources;
export function bindingFingerprint(version){return(resources(version)?.bindings??[]).map(b=>({name:b.name,type:b.type,class_name:b.class_name??null,namespace:b.namespace_id?sha(b.namespace_id):null,text:b.type==='plain_text'?sha(String(b.text??b.value??'')):null})).sort((a,b)=>a.name.localeCompare(b.name));}
export function validatePreserved(before,after){
 const a=bindingFingerprint(before),b=bindingFingerprint(after);ensure(JSON.stringify(a)===JSON.stringify(b),'BINDING_OR_POLICY_CHANGED');
 const budget=a.find(x=>x.name==='BUDGET');ensure(budget?.class_name==='BudgetGate'&&budget.namespace,'ORIGINAL_BUDGET_REQUIRED');
 for(const name of ['GEMINI_API_KEY','PORTABLE_POLICY'])ensure(a.some(x=>x.name===name&&x.type==='secret_text'),'SECRET_BINDING_REQUIRED');
 const values=resources(before).bindings;const text=n=>{const b=values.find(b=>b.name===n);return b?.text??b?.value};
 ensure(text('MODEL_ENABLED')==='true'&&text('PORTABLE_ENABLED')==='true','EXISTING_MODEL_ON_REQUIRED');
 const recurring=JSON.parse(text('RECURRING_POLICY')||'{}');ensure(recurring.user_daily===5&&recurring.user_monthly===100&&recurring.service_monthly===300&&recurring.reserve_micros>0&&recurring.monthly_micros>0,'EXISTING_QUOTA_REQUIRED');
}
function cli(args,json=false){
 try{const out=execFileSync(process.execPath,[path.join(root,'node_modules/wrangler/bin/wrangler.js'),...args,...(json?['--json']:[])],{cwd:root,encoding:'utf8',timeout:120000,windowsHide:true,maxBuffer:1048576,env:{...process.env,WRANGLER_SEND_METRICS:'false',WRANGLER_LOG_PATH:path.join(folder,'wrangler-dialogue.log')},stdio:['ignore','pipe','pipe']});return json?JSON.parse(out):out}
 catch{throw Error('CLOUDFLARE_CLI_AUTH_NETWORK_OR_ACCESS_REQUIRED')}
}
function current(){const d=cli(['deployments','status','--name',name,'--config',config],true),v=(d.result??d).versions;ensure(v?.length===1&&v[0].percentage===100&&/^[a-f0-9-]{36}$/i.test(v[0].version_id),'SINGLE_ACTIVE_VERSION_REQUIRED');return {id:v[0].version_id,raw:cli(['versions','view',v[0].version_id,'--name',name,'--config',config],true)}}
async function publicCheck(dialogue){
 const h=await fetch(origin+'/health',{redirect:'error',signal:AbortSignal.timeout(12000)}),value=await h.json();ensure(h.status===200&&value.model_enabled===true&&value.portable_enabled===true&&value.features?.includes('selected-issue-v1'),'PUBLIC_MODEL_ON_REQUIRED');
 if(dialogue)ensure(value.features.includes('issue-dialogue-v1'),'DIALOGUE_FEATURE_MISSING');
 for(const endpoint of ['/v2/issues/explanations',...(dialogue?['/v2/issues/dialogue']:[])]){const r=await fetch(origin+endpoint,{method:'POST',headers:{'content-type':'application/json'},body:'{}',redirect:'error',signal:AbortSignal.timeout(12000)}),v=await r.json();ensure(r.status===401&&v.error?.code==='AUTH_REQUIRED','PUBLIC_AUTH_GUARD_FAILED')}
 return {health:200,model_enabled:true,authentication_guard:401,dialogue};
}
async function main(){
 ensure(process.argv.includes('--execute'),'USE_EXECUTE_FOR_APPROVED_CODE_DEPLOY');fs.mkdirSync(folder,{recursive:true});
 const contextFix=process.argv.includes('--context-schema-fix'),issuedFix=process.argv.includes('--issued-read-fix');
 ensure(!(contextFix&&issuedFix),'ONE_APPROVED_PATCH_MODE_REQUIRED');
 const fix=contextFix?'context':issuedFix,plan=deploymentPlan(fix);
 const original=fix?JSON.parse(fs.readFileSync(path.join(folder,contextFix?'dialogue-issued-read-deployment.json':'dialogue-deployment.json'),'utf8')):null;
 if(fix)ensure(original.stage==='COMPLETE'&&original.after?.id===plan.before&&original.bundle_sha256===(contextFix?APPROVED_ISSUED_READ_BUNDLE:APPROVED_DIALOGUE_BUNDLE),'VERIFIED_PRIOR_DEPLOYMENT_REQUIRED');
 const journal=path.join(folder,plan.journal);if(fs.existsSync(journal)){const previous=JSON.parse(fs.readFileSync(journal,'utf8'));ensure(!['DEPLOYING','COMPLETE','POSTCHECK_FAILED'].includes(previous.stage),'EXISTING_DEPLOYMENT_DO_NOT_REPEAT')}
 const parsed=ts.parseConfigFileTextToJson(config,fs.readFileSync(path.join(root,config),'utf8'));const c=parsed.config;
 ensure(!parsed.error&&c.name===name&&c.keep_vars===true&&!c.vars&&c.durable_objects?.bindings?.length===1&&c.durable_objects.bindings[0].name==='BUDGET'&&c.migrations?.length===1&&c.migrations[0].tag==='caritda-budget-v1','PRESERVING_CONFIG_REQUIRED');
 const state={at:new Date().toISOString(),stage:'PRECHECK',issued_read_fix:issuedFix,context_schema_fix:contextFix,gemini_requests:0};const save=()=>fs.writeFileSync(journal,JSON.stringify(state,null,2));
 let before,uploaded;
 try{
  for(const args of [['node_modules/typescript/bin/tsc','--noEmit'],['test/bundle-dialogue.mjs'],['--experimental-strip-types','test/portable-security.mjs','.bundle/voice-dialogue-worker.mjs'],['--experimental-strip-types','test/issue-security.mjs','.bundle/voice-dialogue-worker.mjs'],['--experimental-strip-types','test/dialogue-security.mjs','.bundle/voice-dialogue-worker.mjs'],['--experimental-strip-types','test/workerd-compatibility.mjs','.bundle/voice-dialogue-worker.mjs']])execFileSync(process.execPath,args,{cwd:root,stdio:['ignore','pipe','pipe'],timeout:120000});
  const bundle=path.join(root,'.bundle/voice-dialogue-worker.mjs');ensure(sha(fs.readFileSync(bundle))===plan.bundle,'REVIEWED_BUNDLE_HASH_REQUIRED');state.bundle_sha256=plan.bundle;
  before=current();ensure(before.id===plan.before,'ACTIVE_WORKER_CODE_NOT_AUDITED_DO_NOT_OVERWRITE');validatePreserved(before.raw,before.raw);
  if(fix)ensure(JSON.stringify(bindingFingerprint(before.raw))===JSON.stringify(original.after.bindings),'PRIOR_BINDINGS_OR_POLICY_CHANGED');
  state.before={id:before.id,bindings:bindingFingerprint(before.raw)};state.before_public=await publicCheck(fix);save();
  ensure(current().id===before.id,'WORKER_CHANGED_BEFORE_DEPLOY');state.stage='DEPLOYING';save();
  const output=cli(['deploy',bundle,'--no-bundle','--config',config,'--keep-vars']);uploaded=output.match(/Current Version ID:\s*([a-f0-9-]{36})/i)?.[1];
  ensure(uploaded,'UPLOAD_VERSION_UNCONFIRMED');const after=current();ensure(after.id===uploaded,'WORKER_CHANGED_AFTER_DEPLOY');validatePreserved(before.raw,after.raw);
  state.after={id:after.id,bindings:bindingFingerprint(after.raw)};state.after_public=await publicCheck(true);state.stage='COMPLETE';state.complete=true;save();console.log('DIALOGUE_DEPLOYMENT_PASS code_only=true model_ON_preserved=true budget_binding_preserved=true gemini_requests=0 version='+after.id);
 }catch(e){state.code=/^[A-Z_]{1,90}$/.test(e.message)?e.message:'DEPLOYMENT_PRECHECK_OR_NETWORK_FAILED';
  if(state.stage==='DEPLOYING'){state.stage='POSTCHECK_FAILED';if(uploaded&&before){try{if(current().id===uploaded){cli(['rollback',before.id,'--config',config,'--message','Restore prior CARITDA code after failed dialogue checks']);const restored=current();validatePreserved(before.raw,restored.raw);state.recovery='PRIOR_CODE_AND_BINDINGS_VERIFIED'}}catch{state.recovery='NOT_VERIFIED'}}}
  else state.stage='PRECHECK_FAILED';save();console.log('DIALOGUE_DEPLOYMENT_STOPPED code='+state.code+' gemini_requests=0');process.exitCode=1;
 }
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(()=>{console.log('DIALOGUE_DEPLOYMENT_PREFLIGHT_FAILED');process.exitCode=1});
