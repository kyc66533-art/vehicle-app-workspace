import {BudgetGate as LegacyGate} from './index.ts';
import {ApiError,reject,keys,response,bodyJson,publicKey,verifyProof,ORIGIN,b64,digest,uuid} from './portable-common.ts';
import {validateAnalysis,explain} from './portable-analysis.ts';
import {validateIssue,explainIssue,ISSUE_CONSENT} from './portable-issue.ts';
const SOC_CONSENT='soc-explanation-v1';
const granted=(d:Device,scope:string)=>d.consent===scope||!!d.consents?.includes(scope);
import {validateRecurring,recurringRemaining,reserveRecurring,type Recurring} from './recurring-quota.ts';
import type {Principal} from './firebase-token.ts';
export interface Policy {recurring?:Recurring;global_total:number;per_minute:number;expires_at:number;users:Record<string,{enabled:boolean;after:number;total:number;daily:number;devices:number;device_total:number}>}
export function policyOf(raw:string,now:number):Policy {
 let p:any;try{p=JSON.parse(raw);}catch{throw new ApiError(503,'POLICY_REQUIRED');}
 keys(p,p.recurring?['global_total','per_minute','expires_at','users','recurring']:['global_total','per_minute','expires_at','users']);if(p.recurring)validateRecurring(p.recurring);reject(Number.isInteger(p.global_total)&&p.global_total>=0&&p.global_total<=100&&Number.isInteger(p.per_minute)&&p.per_minute>=0&&p.per_minute<=2&&Number.isSafeInteger(p.expires_at)&&(p.recurring?p.expires_at===0:p.expires_at>now/1000&&p.expires_at<=now/1000+31*86400),'POLICY_INVALID',503);
 reject(p.users&&typeof p.users==='object'&&!Array.isArray(p.users)&&Object.keys(p.users).length<=20,'POLICY_INVALID',503);
 for(const [uid,u]of Object.entries(p.users) as [string,any][]){reject(uid.length>=1&&uid.length<=128,'POLICY_INVALID',503);keys(u,['enabled','after','total','daily','devices','device_total']);reject(typeof u.enabled==='boolean'&&Number.isSafeInteger(u.after)&&u.after>=0&&['total','daily','device_total'].every(k=>Number.isInteger(u[k])&&u[k]>=0&&u[k]<=100)&&Number.isInteger(u.devices)&&u.devices>=0&&u.devices<=5,'POLICY_INVALID',503);}
 return p as unknown as Policy;
}
/** Non-secret opt-in overlay: never reads/replaces the existing PORTABLE_POLICY Secret or UID roster. */
export function runtimePolicy(raw:string,recurringRaw:string|undefined,now:number):Policy{
 if(!recurringRaw)return policyOf(raw,now);
 let p:any,r:any;try{p=JSON.parse(raw);r=JSON.parse(recurringRaw);}catch{throw new ApiError(503,'POLICY_INVALID');}
 validateRecurring(r);p.recurring=r;p.expires_at=0;p.per_minute=1;
 return policyOf(JSON.stringify(p),now);
}
interface Device {id:string;uid:string;thumb:string;jwk:JsonWebKey;active:boolean;version:number;consent:string;consents?:string[]}
interface Challenge {id:string;uid:string;device:string;thumb:string;purpose:string;operation:string;nonce:string;expires:number;used:boolean;jwk?:JsonWebKey;intent?:string}
interface Operation {id:string;uid:string;device:string;intent:string;hash?:string;state:string;created:number;owner?:string;lease?:number;payload?:string;result?:any;code?:string;attempts:number;day?:string;consent?:string}
interface State {version:2;legacyCheckpoint:number;used:number;minute:number;minuteCount:number;devices:Device[];challenges:Challenge[];operations:Operation[];counts:Record<string,number>;rate:Record<string,{minute:number,count:number}>;recurring?:any}
const STATE='portable:v2',GLOBAL='caritda-gemini-poc-global-v1';
export {GLOBAL};
const counter=(s:State,k:string)=>s.counts[k]||0;
function prune(s:State,now:number){
 s.operations.forEach(o=>{if(o.state==='DISPATCHING'&&o.lease!<=now){o.state='UNKNOWN';o.code='MODEL_OUTCOME_UNKNOWN';delete o.payload;}});
 s.challenges=s.challenges.filter(c=>c.expires*1000+60000>now);
 s.operations=s.operations.filter(o=>o.created+30*86400000>now||['DISPATCHING','UNKNOWN'].includes(o.state));
 s.operations.forEach(o=>{if(o.created+86400000<=now)delete o.result;});
 for(const k of Object.keys(s.counts))if((k.startsWith('day:')||k.startsWith('device-day:'))&&k.slice(-10)<new Date(now-30*86400000).toISOString().slice(0,10))delete s.counts[k];
}
async function schedule(tx:any,s:State,now:number){
 const times=[...s.challenges.map(c=>c.expires*1000+60000),...s.operations.flatMap(o=>[...(o.state==='DISPATCHING'?[o.lease!]:[]),...(o.result?[o.created+86400000]:[]),...(!['DISPATCHING','UNKNOWN'].includes(o.state)?[o.created+30*86400000]:[])])].filter(t=>t>now);
 if(times.length)await tx.setAlarm(Math.min(...times));
}
/** Same class/binding/DO name as v1. Legacy budget and ids are never rewritten. */
export class BudgetGate extends LegacyGate {
 env:any;providerFetch:typeof fetch=fetch;
 constructor(ctx:any,env:any){super(ctx);this.env=env;}
 async fetch(request:Request):Promise<Response>{
  const path=new URL(request.url).pathname;
  if(!path.startsWith('/portable/')){
   if(path==='/reserve'&&(this.env.PORTABLE_ENABLED==='true'||await this.ctx.storage.get(STATE)))return response({code:'LEGACY_MODEL_DISABLED'},403);
   return super.fetch(request);
  }
  try{
   reject(this.env.PORTABLE_ENABLED==='true','PORTABLE_DISABLED',503);
   const input=await bodyJson(request,8192);const now=Date.now();const policy=runtimePolicy(this.env.PORTABLE_POLICY||'',this.env.RECURRING_POLICY,now);
   const principal=input.principal as Principal;const u=policy.users[principal?.uid];reject(u?.enabled&&principal.authTime>=u.after,'UID_NOT_APPROVED',403);
   const result=await this.ctx.storage.transaction(async(tx:any)=>{
    let s:State=await tx.get(STATE);
    if(!s){const old=await tx.get('budget')||{total:0,ids:[],minute:0,count:0};reject(Number.isInteger(old.total)&&old.total>=0&&old.total<=5&&Array.isArray(old.ids)&&old.ids.length===old.total,'LEGACY_BUDGET_INVALID',503);s={version:2,legacyCheckpoint:old.total,used:0,minute:old.minute,minuteCount:old.count,devices:[],challenges:[],operations:[],counts:{},rate:{}};}
    reject(s.version===2,'LEDGER_VERSION',503);
    // Expired DISPATCHING keeps its reservation and concurrency slot. Never redispatch.
    prune(s,now);
    const minute=Math.floor(now/60000),r=s.rate[principal.uid];reject(!r||r.minute!==minute||r.count<20,'CONTROL_RATE_LIMIT',429);
    s.rate[principal.uid]={minute,count:r?.minute===minute?r.count+1:1};
    const answer=await this.route(path,input,s,policy,now);
    await tx.put(STATE,s);await schedule(tx,s,now);return answer;
   });
   if(result.dispatch){const dispatched=await this.dispatch(result.operation_id,policy);if(path==='/portable/read'&&dispatched.ok){const s:State=await this.ctx.storage.get(STATE);const o=s.operations.find(o=>o.id===result.operation_id);return response({operation_id:o!.id,state:o!.state,result:o!.result||null,code:o!.code||null});}return dispatched;}
   return response(result);
  }catch(e){return response({error:{code:e instanceof ApiError?e.code:'PORTABLE_UNAVAILABLE'}},e instanceof ApiError?e.status:503);}
 }
 private async route(path:string,i:any,s:State,p:Policy,now:number):Promise<any>{
  const uid=i.principal.uid,u=p.users[uid],b=i.body;
  const device=()=>{const d=s.devices.find(d=>d.id===i.device&&d.uid===uid);reject(d?.active&&d.version===i.version&&(granted(d,SOC_CONSENT)||granted(d,ISSUE_CONSENT)),'DEVICE_NOT_REGISTERED',403);return d;};
  if(path==='/portable/challenge'){
   keys(b,['purpose','device_id','client_intent_id','jwk']);reject(['REGISTER','EXPLAIN','READ','REVOKE'].includes(b.purpose));
   let d:Device|undefined,k:any,op='-';
   if(b.purpose==='REGISTER'){
    reject(i.principal.authTime>=now/1000-300,'RECENT_LOGIN_REQUIRED',403);k=await publicKey(b.jwk);reject(b.device_id===''&&b.client_intent_id==='');
    d=s.devices.find(d=>d.uid===uid&&d.thumb===k.thumb);
    reject(!d||d.active,'DEVICE_REVOKED',403);reject(!!d||s.devices.filter(d=>d.uid===uid&&d.active).length<u.devices,'DEVICE_LIMIT',403);
   }else{
    i.device=b.device_id;d=device();k={thumb:d.thumb,jwk:d.jwk};reject(b.jwk===null);
    if(b.purpose==='EXPLAIN'){
     reject(uuid(b.client_intent_id));let o=s.operations.find(o=>o.uid===uid&&o.device===d!.id&&o.intent===b.client_intent_id);
     if(o){reject(o.created>=now-86400000,'OPERATION_EXPIRED',410);op=o.id;}
     else{reject(s.operations.length<1000,'LEDGER_CAPACITY',503);op='o2.'+now+'.'+crypto.randomUUID();s.operations.push({id:op,uid,device:d.id,intent:b.client_intent_id,state:'ISSUED',created:now,attempts:0});}
    }else {reject(b.client_intent_id===''||/^o2\.\d{13}\.[0-9a-f-]{36}$/.test(b.client_intent_id));op=b.client_intent_id||'-';}
   }
   reject(s.challenges.length<200,'CHALLENGE_LIMIT',429);
   const c:Challenge={id:crypto.randomUUID(),uid,device:d?.id||'',thumb:k.thumb,purpose:b.purpose,operation:op,nonce:b64(crypto.getRandomValues(new Uint8Array(32))),expires:Math.floor(now/1000)+60,used:false,jwk:k.jwk};s.challenges.push(c);
   return {challenge_id:c.id,nonce:c.nonce,expires_at:c.expires,binding_id:b.purpose==='REGISTER'?c.id:d!.id,operation_id:op,key_version:d?.version||1};
  }
  const issueRequest=path==='/portable/issue-explain',issueRevoke=path==='/portable/issue-revoke';
  const isExplain=path==='/portable/explain'||issueRequest;
  const scope=issueRequest?ISSUE_CONSENT:SOC_CONSENT;
  const d=path==='/portable/register'?undefined:device();
  if(isExplain)reject(granted(d!,scope),'CONSENT_REQUIRED',403);
  keys(b,path==='/portable/register'?['challenge_id','registration_payload','signature']:isExplain?['challenge_id','analysis_payload','signature']:['challenge_id','operation_id','signature']);
  const c=s.challenges.find(c=>c.id===b.challenge_id&&c.uid===uid);reject(c,'CHALLENGE_INVALID',403);
  const purpose=path==='/portable/register'?'REGISTER':isExplain?'EXPLAIN':path==='/portable/read'||path==='/portable/status'?'READ':'REVOKE';
  const payload=purpose==='REGISTER'?b.registration_payload:purpose==='EXPLAIN'?b.analysis_payload:'';
  reject(typeof payload==='string'&&payload.length<=4096&&c.purpose===purpose&&(!d||c.device===d.id),'CHALLENGE_INVALID',403);
  const proofPath=purpose==='REGISTER'?'/v2/devices/register':purpose==='EXPLAIN'?(issueRequest?'/v2/issues/explanations':'/v2/explanations'):path==='/portable/status'?'/v2/account/status':purpose==='READ'?'/v2/operations/'+b.operation_id:issueRevoke?'/v2/issues/consent/revoke':'/v2/devices/revoke';
  reject(!['READ','REVOKE'].includes(purpose)||b.operation_id===c.operation,'OPERATION_MISMATCH',403);
  await verifyProof(c.jwk!,['CARITDA-KEY-PROOF-V1',purpose,uid,purpose==='REGISTER'?c.id:d!.id,c.thumb,'POST',ORIGIN,proofPath,c.operation,await digest(payload),c.nonce,String(c.expires)],b.signature);
  const o=s.operations.find(o=>o.id===c.operation&&o.uid===uid&&o.device===d?.id);
  const hash=await digest(payload);
  if(purpose==='EXPLAIN'&&o&&o.state!=='ISSUED'){
   reject(o.hash===hash&&(o.consent||SOC_CONSENT)===scope,'PAYLOAD_CONFLICT',409);return {operation_id:o.id,state:o.state,code:o.code||null};
  }
  reject(!c.used&&c.expires>=now/1000,'CHALLENGE_EXPIRED',403);
  if(purpose==='REGISTER'){
   let r:any;try{r=JSON.parse(payload);}catch{throw new ApiError(400,'REGISTRATION_INVALID');}
   keys(r,['jwk','consent_version']);reject([SOC_CONSENT,ISSUE_CONSENT].includes(r.consent_version));const k=await publicKey(r.jwk);reject(k.thumb===c.thumb,'KEY_MISMATCH',403);
   reject(i.principal.authTime>=now/1000-300,'RECENT_LOGIN_REQUIRED',403);
   let registered=s.devices.find(d=>d.uid===uid&&d.thumb===c.thumb);reject(!registered||registered.active,'DEVICE_REVOKED',403);
   if(!registered){reject(s.devices.filter(d=>d.uid===uid&&d.active).length<u.devices,'DEVICE_LIMIT',403);registered={id:crypto.randomUUID(),uid,thumb:c.thumb,jwk:k.jwk,active:true,version:1,consent:r.consent_version};s.devices.push(registered);}registered.consents=[...new Set([registered.consent,...(registered.consents||[]),r.consent_version])].filter(Boolean);c.used=true;return {device_id:registered.id,key_version:registered.version,registered:true};
  }
  if(purpose==='REVOKE'){const removed=issueRevoke?ISSUE_CONSENT:SOC_CONSENT;d!.consents=[...new Set([d!.consent,...(d!.consents||[])])].filter(x=>x&&x!==removed);if(d!.consent===removed)d!.consent='';c.used=true;return {revoked:true};}
  if(path==='/portable/status'){
   reject(b.operation_id==='-'&&c.operation==='-','OPERATION_MISMATCH');c.used=true;
   return {legacy_total:s.legacyCheckpoint,portable_attempts:s.used,combined_total:s.legacyCheckpoint+s.used,quota_mode:p.recurring?'recurring':'legacy',global_total:p.recurring?.service_monthly??p.global_total,remaining:p.recurring?recurringRemaining(s,p.recurring,uid,d!.id,now):Math.max(0,Math.min(p.global_total-s.legacyCheckpoint-s.used,u.total-counter(s,'uid:'+uid),u.daily-counter(s,'day:'+uid+':'+new Date(now+9*3600000).toISOString().slice(0,10)),u.device_total-counter(s,'device:'+d!.id))),model_enabled:this.env.MODEL_ENABLED==='true',uncertain_block:s.operations.some(o=>o.state==='UNKNOWN')};
  }
  reject(o&&o.created>=now-86400000,'OPERATION_EXPIRED',410);
  if(purpose==='READ'){reject(granted(d!,o.consent||SOC_CONSENT),'CONSENT_REQUIRED',403);c.used=true;if(o.state==='RESERVED')return {dispatch:true,operation_id:o.id};return {operation_id:o.id,state:o.state,result:o.result||null,code:o.code||null};}
  let facts:any;try{facts=JSON.parse(payload);}catch{throw new ApiError(400,'INVALID_JSON');}if(issueRequest)validateIssue(facts);else{validateAnalysis(facts);reject(facts.source==='PLEOS_EMULATOR_SDK','REAL_VEHICLE_NOT_APPROVED',403);}reject(this.env.MODEL_ENABLED==='true'&&typeof this.env.GEMINI_API_KEY==='string'&&this.env.GEMINI_API_KEY.length>0,'MODEL_DISABLED',503);
  reject(!s.operations.some(o=>['DISPATCHING','UNKNOWN','RESERVED'].includes(o.state)),'CONCURRENCY_LIMIT',429);
  const day=new Date(now+9*3600000).toISOString().slice(0,10),global=s.legacyCheckpoint+s.used;
  if(p.recurring)reserveRecurring(s,p.recurring,uid,d!.id,now);else reject(global<p.global_total&&counter(s,'uid:'+uid)<u.total&&counter(s,'day:'+uid+':'+day)<u.daily&&counter(s,'device:'+d!.id)<u.device_total,'TOTAL_LIMIT',429);
  const minute=Math.floor(now/60000);reject(s.minute!==minute||s.minuteCount<p.per_minute,'RATE_LIMIT',429);reject(p.per_minute>0,'RATE_LIMIT',429);
  c.used=true;s.used++;s.minuteCount=s.minute===minute?s.minuteCount+1:1;s.minute=minute;
  for(const k of ['uid:'+uid,'day:'+uid+':'+day,'device:'+d!.id])s.counts[k]=counter(s,k)+1;
  Object.assign(o,{hash,state:'RESERVED',payload,day,consent:scope});return {dispatch:true,operation_id:o!.id};
 }
 private async dispatch(id:string,p:Policy):Promise<Response>{
  reject(this.env.MODEL_ENABLED==='true'&&!!this.env.GEMINI_API_KEY&&(p.recurring||p.expires_at>Date.now()/1000),'MODEL_DISABLED',503);
  const owner=crypto.randomUUID();const selected=await this.ctx.storage.transaction(async(tx:any)=>{
   const s:State=await tx.get(STATE),o=s.operations.find(o=>o.id===id);if(o?.state!=='RESERVED')return null;
   const u=p.users[o.uid],d=s.devices.find(d=>d.id===o.device);reject(u?.enabled&&d?.active&&granted(d,o.consent||SOC_CONSENT),'DISPATCH_CANCELLED',403);
   o.state='DISPATCHING';o.owner=owner;o.attempts=1;o.lease=Date.now()+15000;await tx.put(STATE,s);await schedule(tx,s,Date.now());return {...o};
  });
  if(!selected)return response({operation_id:id,state:'PENDING'},202);
  let result:any,code:string|undefined;try{result=await (selected.consent===ISSUE_CONSENT?explainIssue:explain)(selected.payload!,id,this.env.GEMINI_API_KEY,this.providerFetch);}catch(e){code=e instanceof ApiError?e.code:'MODEL_OUTCOME_UNKNOWN';}
  const answer=await this.ctx.storage.transaction(async(tx:any)=>{
   const s:State=await tx.get(STATE),o=s.operations.find(o=>o.id===id);if(!o||o.owner!==owner||o.state!=='DISPATCHING')return {operation_id:id,state:'UNKNOWN',code:'MODEL_OUTCOME_UNKNOWN'};
   o.state=result?'SUCCEEDED':code==='MODEL_TIMEOUT'||code==='MODEL_NETWORK'||code==='MODEL_OUTCOME_UNKNOWN'?'UNKNOWN':'FAILED';o.code=code;delete o.payload;if(result)o.result=result;await tx.put(STATE,s);await schedule(tx,s,Date.now());return {operation_id:id,state:o.state,code:code||null};
  });return response(answer);
 }
 async alarm(){await this.ctx.storage.transaction(async(tx:any)=>{const s:State=await tx.get(STATE);if(!s)return;prune(s,Date.now());await tx.put(STATE,s);await schedule(tx,s,Date.now());});}
}
