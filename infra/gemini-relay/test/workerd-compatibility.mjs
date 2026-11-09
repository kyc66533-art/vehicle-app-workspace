// No Cloudflare account, secret or model call. Uses a fresh LOCAL SQLite DO namespace.
import fs from 'node:fs/promises';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
import {generateKeyPair,SignJWT,exportJWK} from 'jose';
import {b64,digest,proofBytes,publicKey,ORIGIN} from '../src/portable-common.ts';
const script=await fs.readFile(process.argv[2]||new URL('../.bundle/portable-worker.mjs',import.meta.url),'utf8');
let count=0;const check=(ok,label)=>{if(!ok)throw Error(label);count++;};
let externalAttempts=0;
const outboundService=()=>{externalAttempts++;throw Error('External network forbidden in this compatibility test');};
const uid='workerd-compat-fixture',now=Math.floor(Date.now()/1000);
const harness=`import app,{handlePortable,BudgetGate,verifyFirebase,bodyText} from './worker.mjs';
export {BudgetGate};
export default {async fetch(request,env){
 const path=new URL(request.url).pathname;
 if(path==='/__encoding'){
  let invalid=false;try{await bodyText(new Response(Uint8Array.of(255)));}catch(e){invalid=e.code==='INVALID_UTF8';}
  let limited=false;try{await bodyText(new Response('abcdef'),3);}catch(e){limited=e.code==='BODY_LIMIT';}
  return Response.json({text:await bodyText(new Response('카잇다')),invalid,limited,bom:new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.of(239,187,191,65)),ignoreBOM:typeof new TextDecoder().ignoreBOM,fromBase64:typeof Uint8Array.fromBase64});
 }
 if(path==='/__jwt'){
  const b=await request.json();const base=Object.getOwnPropertyDescriptor(Uint8Array,'fromBase64'),capture=Object.getOwnPropertyDescriptor(Error,'captureStackTrace');
  try{
   if(b.fallback)Object.defineProperty(Uint8Array,'fromBase64',{value:undefined,writable:true,configurable:true});
   if(b.noCapture)Object.defineProperty(Error,'captureStackTrace',{value:undefined,writable:true,configurable:true});
   const key=await crypto.subtle.importKey('jwk',b.jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
   const result=await verifyFirebase(b.token,'caritda',Date.now(),async()=>key);
   return Response.json({valid:result.uid==='${uid}',issued:typeof result.issuedAt==='number'});
  }catch(e){return Response.json({code:e.code||'RUNTIME_FAILURE'}, {status:e.status||500});}
  finally {if(b.fallback){if(base)Object.defineProperty(Uint8Array,'fromBase64',base);else delete Uint8Array.fromBase64;}if(b.noCapture){if(capture)Object.defineProperty(Error,'captureStackTrace',capture);else delete Error.captureStackTrace;}}
 }
 if(path.startsWith('/v2/'))return handlePortable(request,env,async()=>({uid:'${uid}',authTime:Math.floor(Date.now()/1000),issuedAt:Math.floor(Date.now()/1000)}));
 return app.fetch(request,env);
}};`;
const mf=new Miniflare(convertV4MiniflareOptions({workers:[
 {name:'caritda-local',modules:true,script,compatibilityDate:'2026-11-09',durableObjects:{BUDGET:{className:'BudgetGate',useSQLite:true}},bindings:{PORTABLE_ENABLED:'false',MODEL_ENABLED:'false',FIREBASE_PROJECT_ID:'caritda'},outboundService},
 {name:'caritda-harness',modules:[{type:'ESModule',path:'harness.mjs',contents:harness},{type:'ESModule',path:'worker.mjs',contents:script+'\nexport {verifyFirebase,bodyText};'}],compatibilityDate:'2026-11-09',durableObjects:{BUDGET:{className:'BudgetGate',useSQLite:true}},bindings:{PORTABLE_ENABLED:'true',MODEL_ENABLED:'false',FIREBASE_PROJECT_ID:'caritda',PORTABLE_POLICY:JSON.stringify({global_total:0,per_minute:0,expires_at:now+3600,users:{[uid]:{enabled:true,after:0,total:0,daily:0,devices:1,device_total:0}}})},outboundService}
]}));
try{
 const health=await mf.dispatchFetch('https://caritda-ai-relay-dev.kyc66533.workers.dev/health');check(health.status===200,'module evaluation/health');
 const h=await health.json();check(h.release==='caritda-portable-ai-v2'&&!h.model_enabled,'release and model off');
 const off=await mf.dispatchFetch('https://caritda-ai-relay-dev.kyc66533.workers.dev/v2/explanations',{method:'POST'});check(off.status===503,'model disabled without upstream');
 const ns=await mf.getDurableObjectNamespace('BUDGET','caritda-local');const stub=ns.get(ns.idFromName('local-fixture-not-production'));
 const status=await stub.fetch('https://budget.internal/status');check(status.status===200&&(await status.json()).remaining===5,'actual workerd DO constructor/storage baseline');
 const local=await mf.getWorker('caritda-harness');const encoding=await(await local.fetch('https://fixture.invalid/__encoding')).json();
 check(encoding.text==='카잇다','direct Response bounded UTF8 decoding');check(encoding.invalid,'fatal UTF8 rejection');check(encoding.limited,'stream size bound');check(encoding.bom==='A'&&encoding.ignoreBOM==='boolean','runtime decoder BOM/default option support');
 const rsa=await generateKeyPair('RS256');const jwk=await exportJWK(rsa.publicKey);
 const make=async(aud='caritda',exp=now+3600)=>new SignJWT({auth_time:now,firebase:{sign_in_provider:'password'}}).setProtectedHeader({alg:'RS256',kid:'local-fixture'}).setIssuer('https://securetoken.google.com/caritda').setAudience(aud).setSubject(uid).setIssuedAt(now).setExpirationTime(exp).sign(rsa.privateKey);
 async function jwt(token,extra={}){return local.fetch('https://fixture.invalid/__jwt',{method:'POST',body:JSON.stringify({token,jwk,...extra})});}
 const good=await make(),verified=await jwt(good);check(verified.ok&&(await verified.json()).issued,'actual bundled jose RSA result has payload/header object');
 const fallback=await jwt(good,{fallback:true});check(fallback.ok&&(await fallback.json()).valid,'actual bundled jose without Uint8Array.fromBase64');
 const expired=await jwt(await make('caritda',now-120),{noCapture:true});check(expired.status===401&&(await expired.json()).code==='AUTH_INVALID_TOKEN','actual JWTExpired super and optional captureStackTrace fallback');
 const wrong=await jwt(await make('wrong-project'));check(wrong.status===401,'actual JWTClaimValidationFailed super');
 const bad=await jwt(good.slice(0,-4)+'AAAA');check(bad.status===401,'actual RSA invalid signature');
 const pair=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);const exported=await crypto.subtle.exportKey('jwk',pair.publicKey);const deviceJwk={kty:exported.kty,crv:exported.crv,x:exported.x,y:exported.y},key=await publicKey(deviceJwk);
 const headers={'authorization':'Bearer local.fixture.token','content-type':'application/json'};
 const challenge=await local.fetch(ORIGIN+'/v2/devices/challenge',{method:'POST',headers,body:JSON.stringify({purpose:'REGISTER',device_id:'',client_intent_id:'',jwk:deviceJwk})});check(challenge.ok,'actual workerd SQL transaction/challenge/alarm');const c=await challenge.json();
 const payload=JSON.stringify({jwk:deviceJwk,consent_version:'soc-explanation-v1'}),fields=['CARITDA-KEY-PROOF-V1','REGISTER',uid,c.binding_id,key.thumb,'POST',ORIGIN,'/v2/devices/register','-',await digest(payload),c.nonce,String(c.expires_at)];
 const signature=b64(new Uint8Array(await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},pair.privateKey,proofBytes(fields))));
 const registered=await local.fetch(ORIGIN+'/v2/devices/register',{method:'POST',headers,body:JSON.stringify({challenge_id:c.challenge_id,registration_payload:payload,signature})});check(registered.ok&&(await registered.json()).registered,'actual workerd verifies P1363 device proof');
 check(externalAttempts===0,'all outbound services rejected; no Google/Cloudflare account/model traffic');
 console.log('PORTABLE_WORKERD_COMPATIBILITY_PASS checks='+count+' (LOCAL runtime/SQLite only; no deployment/model)');
}finally{await mf.dispose();}
