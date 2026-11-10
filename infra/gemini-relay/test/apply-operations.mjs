// Approved deployment orchestration only. No provider requests, credentials, app DB reads or builds.
import fs from'node:fs';import path from'node:path';import{fileURLToPath}from'node:url';import{createHash}from'node:crypto';
import{execFileSync}from'node:child_process';import{createInterface}from'node:readline/promises';
import{INITIAL_RECURRING}from'./prepare-recurring-policy.mjs';import{deploymentView,cleanVersion}from'./deployment-status.mjs';
const relay=fileURLToPath(new URL('../',import.meta.url)),workspace=path.resolve(relay,'../..'),folder=path.join(workspace,'.tools/worker-operations');
const bundle=path.join(relay,'.bundle/portable-worker.mjs'),approved='8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123';
const worker='caritda-ai-relay-dev',origin='https://caritda-ai-relay-dev.kyc66533.workers.dev',record=path.join(folder,'activation-session.json');
const sha=b=>createHash('sha256').update(b).digest('hex'),canonical=x=>x&&typeof x==='object'?(Array.isArray(x)?x.map(canonical):Object.fromEntries(Object.keys(x).sort().map(k=>[k,canonical(x[k])]))):x,same=(a,b)=>JSON.stringify(canonical(a))===JSON.stringify(canonical(b)),ensure=(ok,code)=>{if(!ok)throw Error(code)};
export function validateConfigs(off,on,hash){
 ensure(hash===approved,'APPROVED_BUNDLE_MISMATCH');
 for(const[c,enabled]of[[off,'false'],[on,'true']]){
  ensure(Object.keys(c).sort().join(',')===['name','main','compatibility_date','workers_dev','keep_vars','send_metrics','durable_objects','migrations','observability','vars'].sort().join(',')&&c.compatibility_date==='2026-11-09'&&c.send_metrics===false&&same(c.observability,{enabled:false}),'UNEXPECTED_DEPLOY_CONFIG');
  ensure(c.name===worker&&c.keep_vars===true&&c.workers_dev===true&&path.resolve(c.main)===bundle,'DEPLOY_CONFIG_MISMATCH');
  ensure(same(c.durable_objects,{bindings:[{name:'BUDGET',class_name:'BudgetGate'}]})&&same(c.migrations,[{tag:'caritda-budget-v1',new_sqlite_classes:['BudgetGate']}]),'BUDGET_CONFIG_MISMATCH');
  ensure(Object.keys(c.vars||{}).sort().join(',')==='MODEL_ENABLED,POC_ENABLED,PORTABLE_ENABLED,RECURRING_POLICY','UNEXPECTED_VAR_OVERRIDE');
  ensure(c.vars.MODEL_ENABLED===enabled&&c.vars.PORTABLE_ENABLED==='true'&&c.vars.POC_ENABLED==='false'&&same(JSON.parse(c.vars.RECURRING_POLICY),INITIAL_RECURRING),'APPROVED_POLICY_MISMATCH');
 }
}
function bindings(v){return(v?.result??v)?.resources?.bindings||[]}
function signature(b){return{type:b.type,class_name:b.class_name??null,namespace:b.namespace_id?sha(b.namespace_id):null,...(b.type==='plain_text'?{text_sha:sha(String(b.text??b.value??''))}:{})}}
export function invariant(before,after,expectedModel){
 const a=bindings(before),b=bindings(after),budget=a.find(x=>x.name==='BUDGET'),next=b.find(x=>x.name==='BUDGET');
 ensure(budget?.namespace_id&&budget.class_name==='BudgetGate'&&same(signature(budget),signature(next||{})),'BUDGET_BINDING_CHANGED');
 for(const key of['GEMINI_API_KEY','PORTABLE_POLICY'])ensure(a.some(x=>x.name===key&&x.type==='secret_text')&&b.some(x=>x.name===key&&x.type==='secret_text'),'SECRET_BINDING_MISSING');
 const changes=new Set(['RECURRING_POLICY','MODEL_ENABLED','PORTABLE_ENABLED','POC_ENABLED']);
 for(const binding of a.filter(x=>!changes.has(x.name)))ensure(same(signature(binding),signature(b.find(x=>x.name===binding.name)||{})),'EXISTING_BINDING_CHANGED');
 const text=name=>{const x=b.find(x=>x.name===name);return x?.text??x?.value};
 ensure(text('MODEL_ENABLED')===String(expectedModel)&&text('PORTABLE_ENABLED')==='true'&&text('POC_ENABLED')==='false','ACTIVE_MODE_MISMATCH');
 ensure(same(JSON.parse(text('RECURRING_POLICY')||'null'),INITIAL_RECURRING),'ACTIVE_POLICY_MISMATCH');
}
export function publicInvariant(health,unauth,model){
 ensure(unauth.status===401&&unauth.body.error?.code==='AUTH_REQUIRED','UNAUTHENTICATED_ACCESS_NOT_REJECTED');
 ensure(health.status===200&&health.body.release==='caritda-portable-ai-v2'&&health.body.features?.includes('selected-issue-v1')&&health.body.features?.includes('firebase-keys-manual-v1')&&health.body.auth_required===true&&health.body.portable_enabled===true,'PUBLIC_HEALTH_MISMATCH');
 if(model!==null)ensure(health.body.model_enabled===model,'PUBLIC_MODEL_MODE_MISMATCH');
}
export function confirmation(line,fields){const parts=line.trim().split(/\s+/);ensure(parts[0]==='OK'&&parts.length===fields+1&&parts.slice(1).every(x=>/^\d{1,7}$/.test(x)),'APP_CHECK_FAILED');return parts.slice(1).map(Number);}
export function resumeAllowed(s){return s.stage==='STOPPED'&&s.code==='APP_CHECK_FAILED'&&!s.trial_instruction_issued&&!s.before_usage&&!s.off_version&&!s.on_version}
export function quotaResumeAllowed(s){const q=s.off_usage||s.user_confirmed_off_usage;return !s.trial_instruction_issued&&!s.on_version&&!!s.off_version&&q?.remaining===0&&q.legacy===s.before_usage?.legacy&&q.portable===s.before_usage?.portable&&((s.stage==='WAITING_QUOTA'&&s.quota_wait_confirmed===true)||(s.stage==='STOPPED'&&s.code==='AUTHENTICATED_LEDGER_OR_QUOTA_MISMATCH'&&s.recovery==='MODEL_OFF_VERIFIED'&&s.user_confirmed_off_usage?.uncertain===false))}
export function numeric(line){return /^\d{1,7}$/.test(line.trim())?Number(line.trim()):null}
export function verifyWaitingCode(prior,current){ensure(prior.off_version?.script?.etag&&current.script?.etag===prior.off_version.script.etag,'ACTIVE_CODE_CHANGED_DURING_QUOTA_WAIT')}
async function usage(ask,count){const values=[];for(const label of ['기존 TEST 사용 횟수','정식 요청 시도 횟수','현재 잔여 횟수'].slice(0,count)){for(;;){const line=await ask(label+' (숫자만, 오류면 FAIL) > ');if(/^FAIL\b/i.test(line.trim()))throw Error('APP_CHECK_FAILED');const n=numeric(line);if(n!==null){values.push(n);break}console.log('형식만 다시 입력하세요. 예: 1 (OK나 회라는 글자는 넣지 않아요.)')}}return values}
async function affirm(ask,prompt){for(;;){const answer=(await ask(prompt+' (정상이면 Y, 오류면 FAIL) > ')).trim().toUpperCase();if(['Y','YES','OK','네','예'].includes(answer))return;if(['FAIL','N','NO','아니요'].includes(answer))throw Error('APP_CHECK_FAILED');console.log('화면 확인 결과만 Y 또는 FAIL로 입력하세요. 형식 오류는 서버 요청 없이 다시 입력할 수 있어요.')}}
function cf(args,json=false){try{const out=execFileSync(process.execPath,[path.join(relay,'node_modules/wrangler/bin/wrangler.js'),...args,...(json?['--json']:[])],{cwd:relay,windowsHide:true,encoding:'utf8',timeout:120000,maxBuffer:4*1024*1024,env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'},stdio:['ignore','pipe','pipe']});return json?JSON.parse(out):null;}catch{throw Error('CLOUDFLARE_'+args[0].toUpperCase()+'_FAILED')}}
function version(){const d=deploymentView(cf(['deployments','status','--name',worker,'--config','wrangler.existing.jsonc'],true));ensure(d.versions.length===1&&d.versions[0].percentage===100&&/^[a-f0-9-]{36}$/i.test(d.versions[0].version_id),'ACTIVE_VERSION_NOT_SINGLE_100');const v=cf(['versions','view',d.versions[0].version_id,'--name',worker,'--config','wrangler.existing.jsonc'],true);return{deployment:d,raw:v}}
async function probe(route,post=false){try{const r=await fetch(origin+route,{method:post?'POST':'GET',headers:{'cache-control':'no-cache, no-store',...(post?{'content-type':'application/json'}:{})},...(post?{body:'{}'}:{}),redirect:'manual',signal:AbortSignal.timeout(12000)});ensure(r.status<300||r.status>=400,'PUBLIC_REDIRECT');const reader=r.body?.getReader();let text='',size=0;if(reader){try{for(;;){const p=await reader.read();if(p.done)break;size+=p.value.length;ensure(size<=8192,'PUBLIC_BODY_LIMIT');text+=Buffer.from(p.value).toString('utf8')}}finally{await reader.cancel().catch(()=>{})}}return{status:r.status,body:JSON.parse(text),date:r.headers.get('date')};}catch(e){throw Error(['PUBLIC_REDIRECT','PUBLIC_BODY_LIMIT'].includes(e.message)?e.message:'PUBLIC_REQUEST_FAILED')}}
async function publicCheck(model){const h=await probe('/health'),u=await probe('/v2/issues/explanations',true);publicInvariant(h,u,model);return{at:new Date().toISOString(),server_date:h.date,release:h.body.release,model_enabled:h.body.model_enabled,unauthenticated_status:u.status,unauthenticated_code:u.body.error.code}}
function local(){const off=JSON.parse(fs.readFileSync(path.join(folder,'model-off.json'),'utf8')),on=JSON.parse(fs.readFileSync(path.join(folder,'model-on.json'),'utf8'));validateConfigs(off,on,sha(fs.readFileSync(bundle)).toUpperCase());return{off,on}}
function deploy(enabled){local();cf(['deploy',bundle,'--no-bundle','--config',path.join(folder,enabled?'model-on.json':'model-off.json'),'--keep-vars'])}
function save(s){fs.mkdirSync(folder,{recursive:true});fs.writeFileSync(record,JSON.stringify(s,null,2))}
function kst(date){return new Date(Date.parse(date)+9*3600000).toISOString().slice(0,10)}
async function main(){
 ensure(process.argv.includes('--approved'),'USE_APPROVED_FLAG_NO_ACTION_TAKEN');local();const resumeQuota=process.argv.includes('--resume-quota');if(process.argv.includes('--resume-input')||resumeQuota)ensure(fs.existsSync(record),'RESUME_RECORD_MISSING');
 let prior=null;if(fs.existsSync(record)){prior=JSON.parse(fs.readFileSync(record,'utf8'));if(process.argv.includes('--resume-input')||resumeQuota){ensure(resumeQuota?quotaResumeAllowed(prior):resumeAllowed(prior),'RESUME_NOT_SAFE');fs.writeFileSync(path.join(folder,'activation-stopped-'+Date.now()+'.json'),JSON.stringify(prior,null,2),{flag:'wx'});console.log(resumeQuota?'이전 OFF 배포·앱 확인은 유지하고 인증된 잔여 확인부터 재개합니다. 원장이나 사용량은 초기화하지 않습니다.':'배포 전 형식 오류 기록을 보존하고 입력 단계만 재개합니다. 기존 앱 복원 검사는 반복하지 않습니다.');}else{console.log('기존 실행 기록이 있습니다. 배포·실제 호출을 반복하지 않습니다.');console.log(JSON.stringify(prior,null,2));return;}}
 const state={at:new Date().toISOString(),approved_bundle:approved,stage:'PRECHECK',gemini_calls_by_script:0,trial_instruction_issued:false,complete:false};
 const rl=createInterface({input:process.stdin,output:process.stdout}),cancel=new AbortController();const interrupt=()=>cancel.abort();process.once('SIGINT',interrupt);rl.on('SIGINT',interrupt);
 const ask=prompt=>rl.question(prompt,{signal:cancel.signal});let mutation=false;
 try{
  console.log('1/5 공개 인증 차단·활성 배포·로컬 승인 파일을 확인합니다. 재빌드·Secret 입력은 없습니다.');
  const before=version(),priorBindings=bindings(before.raw);if(prior&&!resumeQuota)ensure(cleanVersion(before.raw).version_id===prior.before_version?.version_id,'ACTIVE_VERSION_CHANGED_BEFORE_RESUME');if(resumeQuota){verifyWaitingCode(prior,cleanVersion(before.raw));const old=prior.before_version.bindings.find(x=>x.name==='BUDGET'),current=cleanVersion(before.raw).bindings.find(x=>x.name==='BUDGET');ensure(old?.namespace_sha256&&old.namespace_sha256===current?.namespace_sha256,'BUDGET_BINDING_CHANGED');invariant(before.raw,before.raw,false)}ensure(priorBindings.some(x=>x.name==='BUDGET'&&x.class_name==='BudgetGate'&&x.namespace_id)&&['GEMINI_API_KEY','PORTABLE_POLICY'].every(name=>priorBindings.some(x=>x.name===name&&x.type==='secret_text')),'BASELINE_BINDINGS_NOT_VERIFIED');state.before_version=cleanVersion(before.raw);state.before_public=await publicCheck(resumeQuota?false:null);save(state);
  if(!prior){console.log('\n일반 CARITDA에서 성공했던 기존 AI 설명을 보고 앱 종료·재실행 후 다시 확인하세요. 새 생성은 누르지 마세요.');await affirm(ask,'기존 설명 복원·로그인 정상·불확실없음 확인')}else console.log('\n기존 설명 복원·로그인 정상은 사용자 확인을 유지합니다.');
  console.log('횟수 확인에서 현재 TEST/정식 시도와 불확실 요청 없음이 확인되면 입력하세요. 오류나 불확실 상태면 FAIL을 입력하세요.');
  const start=resumeQuota?[prior.before_usage.legacy,prior.before_usage.portable]:await usage(ask,2);state.before_usage={legacy:start[0],portable:start[1]};state.existing_cache_confirmed=true;save(state);
  let off;if(resumeQuota){console.log('2/5 기존 모델 OFF 배포를 재사용합니다. 반복 배포하지 않습니다.');off=before}else{console.log('2/5 같은 Worker를 모델 OFF로 배포하고 바인딩·정책·공개401을 확인합니다.');mutation=true;state.stage='OFF_DEPLOYING';save(state);deploy(false);off=version();ensure(off.deployment.versions[0].version_id!==before.deployment.versions[0].version_id,'NEW_VERSION_NOT_ACTIVE')}invariant(before.raw,off.raw,false);state.off_version=cleanVersion(off.raw);state.off_public=await publicCheck(false);state.stage='OFF_VERIFIED';save(state);
  console.log('\n앱의 횟수 확인을 누르세요. 일일·월간 정책/모델 꺼짐/불확실없음이어야 하며 TEST·정식 시도는 직전과 같아야 합니다.');
  await affirm(ask,'반복 정책·모델 꺼짐·불확실없음 확인');const offUsage=await usage(ask,3);state.off_usage={legacy:offUsage[0],portable:offUsage[1],remaining:offUsage[2]};save(state);ensure(offUsage[0]===start[0]&&offUsage[1]===start[1]&&offUsage[2]>=0&&offUsage[2]<=5,'AUTHENTICATED_LEDGER_OR_QUOTA_MISMATCH');if(offUsage[2]===0){state.stage='WAITING_QUOTA';state.quota_wait_confirmed=true;save(state);console.log('QUOTA_WAIT: 정상적인 잔여0입니다. 모델 OFF를 유지하고 새 요청을 하지 않습니다. 한국 시간 갱신 후 --approved --resume-quota로 이어가세요.');return;}
  console.log('3/5 승인한 같은 정책을 모델 ON으로 전환하고 공개401·기존 바인딩을 확인합니다.');mutation=true;state.stage='ON_DEPLOYING';save(state);deploy(true);const on=version();invariant(before.raw,on.raw,true);state.on_version=cleanVersion(on.raw);state.on_public=await publicCheck(true);state.stage='TRIAL_ISSUED';state.trial_instruction_issued=true;save(state);
  console.log('\n4/5 앱에서 횟수를 확인하세요. 모델 켜짐·불확실없음·직전 사용량 동일이 아니면 새 생성을 누르지 말고 FAIL을 입력하세요.');
  console.log('조건이 맞으면 개인정보 없는 선택 기록 한 건에 새로 설명 생성 버튼을 딱 한 번 누르세요. 실패하면 재시도하지 마세요.');
  console.log('새 요청 뒤 "AI 설명을 받았어요" 성공 안내와 새 본문을 먼저 확인하세요. 예전 설명이 남아 있는 것만으로 성공 처리하지 마세요.');
  console.log('실제 새 Gemini 설명 성공 후 기존 설명 보기→앱 종료·재실행→기존 설명 보기→횟수 확인을 진행하세요.');
  await affirm(ask,'신규 실제 Gemini 성공·저장 설명 복원·불확실없음 확인');
  const finish=await usage(ask,3);ensure(finish[0]===start[0]&&finish[1]===start[1]+1&&finish[2]>=0&&finish[2]<=5,'TRIAL_COUNT_MISMATCH');
  const final=await publicCheck(true);if(kst(final.server_date)===kst(state.off_public.server_date))ensure(finish[2]===offUsage[2]-1,'REVIEW_CONSUMED_EXTRA_QUOTA');
  const active=version();ensure(active.deployment.versions[0].version_id===on.deployment.versions[0].version_id,'REMOTE_DEPLOYMENT_CHANGED');invariant(before.raw,active.raw,true);
  state.after_usage={legacy:finish[0],portable:finish[1],remaining:finish[2]};state.final_public=final;state.actual_model_success_user_verified=true;state.policy_verified=INITIAL_RECURRING;state.stage='COMPLETE';state.complete=true;state.finished_at=new Date().toISOString();save(state);
  console.log('5/5 OPERATIONAL_ACCEPTANCE_PASS: 실제 배포·반복 정책·기존 원장/인증·저장 설명·실모델1회·잔여 확인 완료. 모델 원문/인증값은 저장하지 않았습니다.');
  console.log('검증 보고서: '+record);
 }catch(e){
  state.stage='STOPPED';state.complete=false;state.code=/^[A-Z_]{1,80}$/.test(e.message)?e.message:'OPERATION_INTERRUPTED';
  if(mutation){console.log('검증 실패: 다음 단계와 모델 시험을 중단하고 모델 OFF 복구를 한 번 시도합니다.');try{deploy(false);state.recovery_public=await publicCheck(false);state.recovery='MODEL_OFF_VERIFIED'}catch{state.recovery='MODEL_OFF_NOT_VERIFIED';console.log('모델 OFF 자동 복구를 확인하지 못했습니다. Cloudflare에서 MODEL_ENABLED=false를 확인해야 합니다.')}}
  save(state);console.log('OPERATION_STOPPED code='+state.code+' trial_instruction_issued='+state.trial_instruction_issued+'; 같은 실행/새 생성은 반복하지 마세요.');process.exitCode=1;
 }finally{rl.close();process.removeListener('SIGINT',interrupt)}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(()=>{console.log('PREFLIGHT_OR_JOURNAL_FAILED: 실행 파일/설정/비공개 상태 기록을 확인하고 다음 단계로 진행하지 마세요. 실제 배포 여부를 확인하기 전 반복 실행하지 마세요.');process.exitCode=1});
