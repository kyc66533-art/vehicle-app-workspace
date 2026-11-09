// Synthetic RSA/X509 and fake Google certificate HTTP only. No tokens/keys leave this process.
import assert from'node:assert/strict';import{generateKeyPair,SignJWT}from'jose';
let count=0;const check=(v,label)=>{assert.ok(v,label);count++;};const realFetch=globalThis.fetch;
const rsa=await generateKeyPair('RS256',{extractable:true}),now=Date.now(),sec=Math.floor(now/1000);
const der=(tag,...parts)=>{const content=Buffer.concat(parts),n=content.length,bytes=[];let x=n;do{bytes.unshift(x&255);x>>>=8;}while(x);return Buffer.concat([Buffer.from([tag,...(n<128?[n]:[128+bytes.length,...bytes])]),content]);};
const seq=(...x)=>der(0x30,...x),oid=x=>der(6,Buffer.from(x)),integer=x=>der(2,Buffer.from([x]));
const alg=seq(oid([0x2a,0x86,0x48,0x86,0xf7,0x0d,1,1,0x0b]),der(5));
const name=seq(der(0x31,seq(oid([0x55,4,3]),der(0x0c,Buffer.from('LOCAL FIXTURE')))));
const validity=seq(der(0x17,Buffer.from('260101000000Z')),der(0x17,Buffer.from('300101000000Z')));
const spki=Buffer.from(await crypto.subtle.exportKey('spki',rsa.publicKey));const tbs=seq(der(0xa0,integer(2)),integer(1),alg,name,validity,name,spki);
const sig=Buffer.from(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',rsa.privateKey,tbs)),cert=seq(tbs,alg,der(3,Buffer.from([0]),sig));
const pem='-----BEGIN CERTIFICATE-----\n'+cert.toString('base64').match(/.{1,64}/g).join('\n')+'\n-----END CERTIFICATE-----\n';
const token=await new SignJWT({auth_time:sec,firebase:{sign_in_provider:'password'}}).setProtectedHeader({alg:'RS256',kid:'fixture'}).setIssuer('https://securetoken.google.com/caritda').setAudience('caritda').setSubject('local-fixture-not-real').setIssuedAt(sec).setExpirationTime(sec+3600).sign(rsa.privateKey);
const fresh=()=>import('../src/firebase-token.ts?keys-fixture='+crypto.randomUUID());
try{
 let calls=0;globalThis.fetch=async(url,init)=>{calls++;assert.equal(url,'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');assert.equal(init.redirect,'manual');return Response.json({fixture:pem},{headers:{'cache-control':'public, max-age=3600'}});};
 let m=await fresh();check((await m.verifyFirebase(token,'caritda',now)).uid==='local-fixture-not-real','real X509 import path, not key override');check((await m.verifyFirebase(token,'caritda',now+1000)).authTime===sec&&calls===1,'valid key cache reuses verified Google response fixture');
 for(const[code,fetcher]of[
  ...[302,403,404,429,500].map(status=>['AUTH_KEYS_HTTP_'+status,async()=>new Response('{}',{status})]),
  ['AUTH_KEYS_NETWORK',async()=>{throw new TypeError('PRIVATE NETWORK DETAIL');}],
  ['AUTH_KEYS_TIMEOUT',async()=>{throw new DOMException('PRIVATE DETAIL','TimeoutError');}],
  ['AUTH_KEYS_JSON',async()=>new Response('<html>not certificates</html>')],
  ['AUTH_KEYS_BODY',async()=>new Response(Uint8Array.of(255))],
  ['AUTH_KEYS_BODY',async()=>new Response('{}',{headers:{'content-length':'65537'}})],
  ['AUTH_KEYS_SCHEMA',async()=>Response.json([])],
  ['AUTH_KEYS_SCHEMA',async()=>Response.json({fixture:'not a certificate'})],
  ['AUTH_KEYS_SCHEMA',async()=>Response.json(Object.fromEntries(Array.from({length:9},(_,i)=>['fixture'+i,pem])))],
 ]){globalThis.fetch=fetcher;m=await fresh();await assert.rejects(()=>m.verifyFirebase(token,'caritda',now),e=>e.code===code&&e.status===503&&!e.message.includes('PRIVATE'));count++;}
 globalThis.fetch=async()=>{throw Error('NO DETAILS');};m=await fresh();await assert.rejects(()=>m.verifyFirebase(token,'caritda',now),e=>e.code==='AUTH_KEYS_NETWORK');await assert.rejects(()=>m.verifyFirebase(token,'caritda',now+1000),e=>e.code==='AUTH_KEYS_COOLDOWN');count++;
 console.log('FIREBASE_PUBLIC_KEYS_PASS checks='+count+' real_network_calls=0 real_model_calls=0');
}finally{globalThis.fetch=realFetch;}
if(process.argv[2]){
 const{readFile}=await import('node:fs/promises');const{Miniflare,convertV4MiniflareOptions}=await import('miniflare');const script=await readFile(process.argv[2],'utf8');let simulatedFetches=0;
 const harness=`import {verifyFirebase} from './worker.mjs';export default {async fetch(r){try{const b=await r.json();await verifyFirebase(b.token,'caritda');return Response.json({verified:true});}catch(e){return Response.json({code:e.code||'RUNTIME_ERROR'},{status:e.status||500});}}};`;
 const mf=new Miniflare(convertV4MiniflareOptions({workers:['good','http','json','network'].map(mode=>({name:'keys-'+mode,modules:[{type:'ESModule',path:'harness.mjs',contents:harness},{type:'ESModule',path:'worker.mjs',contents:script+'\nexport {verifyFirebase};'}],compatibilityDate:'2026-11-09',outboundService:async request=>{simulatedFetches++;assert.equal(request.url,'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');if(mode==='network')throw Error('LOCAL NETWORK FIXTURE');return mode==='http'?new Response('{}',{status:403}):mode==='json'?new Response('<html>fixture</html>'):Response.json({fixture:pem},{headers:{'cache-control':'max-age=3600'}});}}))}));
 // Miniflare converts an exception in the HOST service binding to HTTP500; genuine fetch rejection is covered above.
 try{let n=0;for(const[mode,code]of[['good',null],['http','AUTH_KEYS_HTTP_403'],['json','AUTH_KEYS_JSON'],['network','AUTH_KEYS_HTTP_500']]){const worker=await mf.getWorker('keys-'+mode);const r=await worker.fetch('https://fixture.invalid/verify',{method:'POST',body:JSON.stringify({token})}),body=await r.json();assert.ok(code?r.status===503&&body.code===code:r.ok&&body.verified,JSON.stringify({mode,status:r.status,body}));n++;}assert.equal(simulatedFetches,4);n++;console.log('FIREBASE_KEYS_WORKERD_PASS checks='+n+' real_network_calls=0 real_model_calls=0');}finally{await mf.dispose();}
}
