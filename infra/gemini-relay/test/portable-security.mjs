// Offline WebCrypto + serialized transactional storage fixture. NO real Google/Worker calls.
import assert from 'node:assert/strict';
import {generateKeyPair,SignJWT} from 'jose';
import {verifyFirebase} from '../src/firebase-token.ts';
import {pathToFileURL} from 'node:url';
const {default:deployedEntry,handlePortable,BudgetGate}=await import(process.argv[2]?pathToFileURL(process.argv[2]).href:new URL('../src/portable-worker.ts',import.meta.url).href);
import {ORIGIN,b64,digest,proofBytes,publicKey} from '../src/portable-common.ts';
import {validateAnalysis,explain} from '../src/portable-analysis.ts';
let checks=0;const check=(ok,label)=>{assert.ok(ok,label);checks++;};
const now=Date.now(),sec=Math.floor(now/1000),uid='fixture-user-not-real';
const rsa=await generateKeyPair('RS256');
const token=async(over={},header={})=>new SignJWT({auth_time:sec,firebase:{sign_in_provider:'password'},...over}).setProtectedHeader({alg:'RS256',kid:'fixture',...header}).setIssuer('https://securetoken.google.com/caritda').setAudience('caritda').setSubject(over.sub??uid).setIssuedAt(sec).setExpirationTime(over.exp??sec+3600).sign(rsa.privateKey);
const jwt=await token();check((await verifyFirebase(jwt,'caritda',now,async()=>rsa.publicKey)).uid===uid,'signed RS256 Firebase claims');
for(const [payload,header] of [[{exp:sec-60},{}],[{auth_time:sec+120},{}],[{sub:''},{}],[{firebase:{sign_in_provider:'anonymous'}},{}],[{},{jku:'https://attacker.invalid'}]]){
 await assert.rejects(()=>token(payload,header).then(t=>verifyFirebase(t,'caritda',now,async()=>rsa.publicKey)));checks++;
}
await assert.rejects(()=>verifyFirebase(jwt,'wrong-project',now,async()=>rsa.publicKey));checks++;
await assert.rejects(()=>verifyFirebase(jwt.slice(0,-5)+'AAAAA','caritda',now,async()=>rsa.publicKey));checks++;
const legacy={total:3,minute:0,count:0,ids:[crypto.randomUUID(),crypto.randomUUID(),crypto.randomUUID()]};
class Storage {
 data=new Map([['budget',structuredClone(legacy)]]);chain=Promise.resolve();alarm=null;
 async get(k){return structuredClone(this.data.get(k));}async put(k,v){this.data.set(k,structuredClone(v));}async setAlarm(v){this.alarm=v;}
 transaction(fn){const run=this.chain.then(async()=>{const copy=new Map([...this.data].map(([k,v])=>[k,structuredClone(v)]));const tx={get:async k=>structuredClone(copy.get(k)),put:async(k,v)=>copy.set(k,structuredClone(v)),setAlarm:async v=>{this.alarm=v;}};const r=await fn(tx);this.data=copy;return r;});this.chain=run.catch(()=>{});return run;}
}
const storage=new Storage();const policy={global_total:5,per_minute:2,expires_at:sec+3600,users:{[uid]:{enabled:true,after:0,total:2,daily:2,devices:2,device_total:2}}};
const env={PORTABLE_ENABLED:'true',MODEL_ENABLED:'true',GEMINI_API_KEY:'fixture-not-real',FIREBASE_PROJECT_ID:'caritda',PORTABLE_POLICY:JSON.stringify(policy)};
let gate=new BudgetGate({storage},env),calls=0;
const provider=async()=>{calls++;return Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify({explanation:'최근 충전량 기록을 확인했어요. 차량 표시와 함께 확인해 주세요.',action:'CHECK_VEHICLE_DISPLAY'})}]}}]});};
gate.providerFetch=provider;env.BUDGET={idFromName:name=>{assert.equal(name,'caritda-gemini-poc-global-v1');return name;},get:()=>gate};
const auth=async()=>({uid,authTime:sec,issuedAt:sec});
async function req(path,body,device='',version=1,extra={}){const r=await handlePortable(new Request(ORIGIN+path,{method:'POST',headers:{authorization:'Bearer fixture.jwt.sig','content-type':'application/json','x-caritda-device-id':device,'x-caritda-key-version':String(version),...extra},body:JSON.stringify(body)}),env,auth);return {status:r.status,body:await r.json()};}
const pair=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);const exported=await crypto.subtle.exportKey('jwk',pair.publicKey);const jwk={kty:exported.kty,crv:exported.crv,x:exported.x,y:exported.y};const key=await publicKey(jwk);
async function challenge(purpose,device='',intent=''){const r=await req('/v2/devices/challenge',{purpose,device_id:device,client_intent_id:intent,jwk:purpose==='REGISTER'?jwk:null},device);assert.equal(r.status,200,JSON.stringify(r.body));return r.body;}
async function signed(c,purpose,path,payload='',device='',operation){const proof=proofBytes(['CARITDA-KEY-PROOF-V1',purpose,uid,c.binding_id,key.thumb,'POST',ORIGIN,path,c.operation_id,await digest(payload),c.nonce,String(c.expires_at)]);const signature=b64(new Uint8Array(await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},pair.privateKey,proof)));return {challenge_id:c.challenge_id,...(purpose==='REGISTER'?{registration_payload:payload}:purpose==='EXPLAIN'?{analysis_payload:payload}:{operation_id:operation??c.operation_id}),signature};}
const noauth=await handlePortable(new Request(ORIGIN+'/v2/devices/challenge',{method:'POST'}),env);check(noauth.status===401,'unauthenticated rejected before provider');check(calls===0,'no auth no bill');
const runtime=await deployedEntry.fetch(new Request(ORIGIN+'/v2/devices/challenge',{method:'POST',headers:{authorization:'Bearer invalid.jwt.token','content-type':'application/json'},body:'{}'}),env,{waitUntil(){}});check(runtime.status===401,'real Worker entry ignores Cloudflare ExecutionContext as auth callback');
const ch=await challenge('REGISTER');const registration=JSON.stringify({jwk,consent_version:'soc-explanation-v1'});const rb=await signed(ch,'REGISTER','/v2/devices/register',registration);const registered=await req('/v2/devices/register',rb);check(registered.status===200,'valid registration');const device=registered.body.device_id;
check((await req('/v2/devices/register',rb)).status===403,'register nonce replay');
check(JSON.stringify(await storage.get('budget'))===JSON.stringify(legacy),'legacy ledger byte-equivalent object');
const sc=await challenge('READ',device),sb=await signed(sc,'READ','/v2/account/status','',device);const status=await req('/v2/account/status',sb,device);
check(status.status===200&&status.body.legacy_total===3&&status.body.remaining===2,'authenticated status exposes actual legacy checkpoint and approved remaining');
check(calls===0,'account status never calls model');
const old=await gate.fetch(new Request('https://budget.internal/reserve',{method:'POST',body:JSON.stringify({request_id:crypto.randomUUID(),now})}));check(old.status===403,'old reservation cannot bypass v2');
const facts={contract:'caritda-analysis-v2',signal:'EV_SOC',source:'PLEOS_EMULATOR_SDK',synthetic:false,unit:'%',window:{duration_ms:2000,latest_age_ms:1000,sample_count:3,truncated:false},facts:{latest:18,minimum:18,maximum:80,mean_of_receipts:158/3,delta_last_comparable:-42},quality:{flag_count:0,segments:1,latest_valid:true},rule:'receipt-analysis-v1',limitations:'receipts_not_ecu;not_trip;not_time_average;no_diagnosis;no_safety_decision'};
check(validateAnalysis(facts)===facts,'bounded actual-source schema (offline fixture, not SDK evidence)');
for(const bad of [{...facts,synthetic:true},{...facts,vin:'secret'},{...facts,source:'TEST_ONLY'},{...facts,facts:{...facts.facts,latest:-1}},{...facts,window:{...facts.window,latest_age_ms:600000}},{...facts,window:{...facts.window,sample_count:0}},{...facts,quality:{...facts.quality,latest_valid:false}}]){assert.throws(()=>validateAnalysis(bad));checks++;}
const payload=JSON.stringify(facts),intent=crypto.randomUUID(),c=await challenge('EXPLAIN',device,intent);const b=await signed(c,'EXPLAIN','/v2/explanations',payload,device);
const badProof={...b,signature:b.signature.slice(0,-2)+'AA'};check((await req('/v2/explanations',badProof,device)).status===403,'signature rejected');check(calls===0,'bad signature no dispatch');
const responses=await Promise.all([req('/v2/explanations',b,device),req('/v2/explanations',b,device)]);check(responses.every(r=>r.status===200),'concurrent same operation accepted idempotently');check(calls===1,'one dispatch under concurrent duplicate');
const rec=await challenge('EXPLAIN',device,intent);check(rec.operation_id===c.operation_id,'lost response intent recovers same operation');
const changed=await signed(rec,'EXPLAIN','/v2/explanations',JSON.stringify({...facts,facts:{...facts.facts,latest:19}}),device);check((await req('/v2/explanations',changed,device)).status===409,'same operation different payload conflict');
const read=await challenge('READ',device,c.operation_id),readbody=await signed(read,'READ','/v2/operations/'+c.operation_id,'',device);const output=await req('/v2/operations/'+c.operation_id,readbody,device);check(output.body.result.origin==='MODEL','only provider completion MODEL');check(output.body.result.evidence_digest===await digest(payload),'exact UTF8 payload digest');check(JSON.stringify(output.body.result.facts)===payload,'typed full facts remain exact');check((await req('/v2/operations/'+c.operation_id,readbody,device)).status===403,'fresh read proof mandatory');
// Restart retains usage, keys and result; storage is not a process-local quota map.
gate=new BudgetGate({storage},env);gate.providerFetch=provider;check((await storage.get('portable:v2')).used===1,'restart retains usage');
const browser=await req('/v2/devices/challenge',{},device,1,{origin:'https://attacker.invalid'});check(browser.status===403,'browser origin reject');
check((await req('/v2/explanations',b,device,2)).status===403,'key version reject');
const denied=await handlePortable(new Request(ORIGIN+'/v2/devices/challenge',{method:'POST',headers:{authorization:'Bearer fixture.jwt.sig','content-type':'application/json'},body:'{}'}),env,async()=>({uid:'not-allowed',authTime:sec,issuedAt:sec}));check(denied.status===403,'UID allowlist');
// Release uncertainty: provider exception consumes the remaining permit and blocks new work.
const c2=await challenge('EXPLAIN',device,crypto.randomUUID()),b2=await signed(c2,'EXPLAIN','/v2/explanations',payload,device);gate.providerFetch=async()=>{calls++;throw Error('fixture transport unknown');};const unknown=await req('/v2/explanations',b2,device);check(unknown.body.state==='UNKNOWN','transport uncertainty retained');check((await storage.get('portable:v2')).used===2,'failed dispatch still consumes budget');
const s=await storage.get('portable:v2');check(s.legacyCheckpoint+s.used===5,'aggregate old+new total limit');check(s.operations.find(o=>o.id===c2.operation_id).attempts===1,'at most one attempt persisted');
check((await req('/v2/explanations',b2,device)).body.state==='UNKNOWN'&&calls===2,'unknown is never retried');
check(JSON.stringify(await storage.get('budget'))===JSON.stringify(legacy),'old total+ids unchanged after all calls');
// Revoke blocks old signed proof immediately while preserving old results/usage.
const rev=await challenge('REVOKE',device),revbody=await signed(rev,'REVOKE','/v2/devices/revoke','',device);check((await req('/v2/devices/revoke',revbody,device)).status===200,'device revocation');check((await req('/v2/explanations',b,device)).status===403,'revoked device cannot replay');
check(!JSON.stringify(output.body).includes('fixture-user-not-real'),'response excludes UID');
// Provider failures are controlled fixtures; no HTTP request leaves this test process.
for(const [label,mock] of [
 ['quota',async()=>new Response('{}',{status:429})],
 ['auth',async()=>new Response('{}',{status:401})],
 ['format rejection',async()=>new Response('{}',{status:400})],
 ['truncated JSON',async()=>Response.json({candidates:[{finishReason:'MAX_TOKENS'}]})],
 ['safety block',async()=>Response.json({promptFeedback:{blockReason:'SAFETY'}})],
 ['missing candidate',async()=>Response.json({})],
 ['numeric invention',async()=>Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify({explanation:'충전량은 999%예요.',action:'CHECK_VEHICLE_DISPLAY'})}]}}]})],
 ['repair claim',async()=>Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify({explanation:'수리가 완료되어 안전해요.',action:'CHECK_VEHICLE_DISPLAY'})}]}}]})],
 ['wrong action',async()=>Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify({explanation:'최근 충전량을 확인해 주세요.',action:'CHECK_CHARGING_PLAN'})}]}}]})],
 ['network loss',async()=>{throw Error('fixture');}],
 ['oversized body',async()=>new Response(' '.repeat(16385))]
]){await assert.rejects(()=>explain(payload,c.operation_id,'fixture-never-real',mock),undefined,label);checks++;}
const blocked=await handlePortable(new Request(ORIGIN+'/v2/devices/challenge',{method:'POST',headers:{authorization:'Bearer fixture.jwt.sig','content-type':'application/json'},body:JSON.stringify({padding:'x'.repeat(8193)})}),env,auth);check(blocked.status===413,'stream/body limit');
const disabled=await handlePortable(new Request(ORIGIN+'/v2/explanations',{method:'POST'}),{...env,PORTABLE_ENABLED:'false'});check(disabled.status===503,'off by default');
const legacyRoute=await handlePortable(new Request(ORIGIN+'/v1/synthetic-soc',{method:'POST',headers:{authorization:'Bearer '+'a'.repeat(43),'content-type':'application/json'},body:'{}'}),{...env,POC_ENABLED:'true',TEST_APP_TOKEN:'a'.repeat(43),TEST_TOKEN_EXPIRES_AT:new Date(now+3600000).toISOString()});check(legacyRoute.status===503,'v1 cost route cannot be enabled by old POC flag');
// Crash after dispatch CAS cannot recover by re-fetching the model.
const crashed=await storage.get('portable:v2');crashed.operations.push({id:'o2.'+now+'.'+crypto.randomUUID(),uid,device,intent:crypto.randomUUID(),state:'DISPATCHING',created:now,lease:now-1,attempts:1});await storage.put('portable:v2',crashed);await gate.alarm();check((await storage.get('portable:v2')).operations.at(-1).state==='UNKNOWN','persistent alarm preserves crash uncertainty');
check(calls===2,'all failure/schema/JWT probes did not invoke real provider');
console.log('PORTABLE_WORKER_SECURITY_PASS checks='+checks+' (offline RSA/ECDSA/DO/provider fixture, NO real model call)');
