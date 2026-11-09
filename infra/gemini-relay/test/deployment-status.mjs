// Read-only diagnosis: no deploy/upload/settings writes, credentials copied, or model requests.
import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath,pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';import {spawn} from 'node:child_process';import ts from 'typescript';
const root=fileURLToPath(new URL('../',import.meta.url)),origin='https://caritda-ai-relay-dev.kyc66533.workers.dev',name='caritda-ai-relay-dev';
const args=process.argv.slice(2),cloudflare=args.includes('--cloudflare'),network=cloudflare||args.includes('--public');
const config=path.join(root,'wrangler.existing.jsonc'),reported='3265582e-15e3-4327-be46-7d651f536a8a';
export function summary(j){return {contract:j?.contract??null,release:j?.release??null,model_enabled:j?.model_enabled??null,enabled:j?.enabled??null,code:j?.error?.code??null,error_release:j?.error?.release??null};}
export function deploymentView(value){const d=value?.result??value;const versions=Array.isArray(d?.versions)?d.versions:[];return {deployment_id:d?.id??null,created_on:d?.created_on??null,versions:versions.map(v=>({version_id:v.version_id??v.id??null,percentage:v.percentage??null}))};}
export function cleanVersion(value){const v=value?.result??value,s=v?.resources?.script;return {version_id:v?.id??null,number:v?.number??null,created_on:v?.metadata?.created_on??null,source:v?.metadata?.source??null,script:{etag:s?.etag??null,handlers:s?.handlers??null,entry_point:s?.entry_point??s?.main_module??null},runtime:{compatibility_date:v?.resources?.script_runtime?.compatibility_date??null},bindings:(v?.resources?.bindings??[]).map(b=>({name:b.name,type:b.type,class_name:b.class_name??null,namespace_sha256:b.namespace_id?createHash('sha256').update(b.namespace_id).digest('hex'):null}))};}
export async function localFile(relative){const file=path.join(root,relative);try{
 const bytes=await fs.readFile(file),module=await import(pathToFileURL(file).href);if(typeof module.default?.fetch!=='function')throw Error('NO_DEFAULT_FETCH');
 const env={PORTABLE_ENABLED:'false',MODEL_ENABLED:'false',POC_ENABLED:'false'};
 const health=await module.default.fetch(new Request(origin+'/health'),env,{});
 const off=await module.default.fetch(new Request(origin+'/v2/account/status',{method:'POST',headers:{'content-type':'application/json'},body:'{}'}),env,{});
 const active=await module.default.fetch(new Request(origin+'/v2/account/status',{method:'POST',headers:{'content-type':'application/json'},body:'{}'}),{...env,PORTABLE_ENABLED:'true',BUDGET:{get(){throw Error('Unauthorized request must never access DO');}}},{});
 return {file:relative,sha256:createHash('sha256').update(bytes).digest('hex'),health:{status:health.status,...summary(await health.json())},v2_model_off:{status:off.status,...summary(await off.json())},v2_enabled_unauthenticated:{status:active.status,...summary(await active.json())}};
 }catch(e){return {file:relative,read_or_runtime_error:e?.code==='ENOENT'?'MISSING':'LOCAL_INSPECTION_FAILED'};}}
async function probe(url,method='GET',headers={}){try{
 const r=await fetch(url,{method,redirect:'manual',signal:AbortSignal.timeout(8000),headers:{'cache-control':'no-cache, no-store','pragma':'no-cache',...(method==='POST'?{'content-type':'application/json'}:{}),...headers},...(method==='POST'?{body:'{}'}:{})});
 const reader=r.body?.getReader();let text='';if(reader){const decoder=new TextDecoder();let bytes=0;try{for(;;){const part=await reader.read();if(part.done)break;bytes+=part.value.length;if(bytes>8192){await reader.cancel();throw Error('BODY_LIMIT');}text+=decoder.decode(part.value,{stream:true});}}finally{reader.releaseLock();}}
 let body;try{body=JSON.parse(text);}catch{body={};}
 return {url,method,status:r.status,...summary(body),headers:Object.fromEntries(['cache-control','cf-cache-status','cf-ray','date','age'].map(k=>[k,r.headers.get(k)]))};
 }catch(e){return {url,method,network_error:e?.cause?.code??(e?.name==='TimeoutError'?'TIMEOUT':'REQUEST_FAILED')};}}
async function wrangler(command){return new Promise(resolve=>{
 const child=spawn(process.execPath,[path.join(root,'node_modules/wrangler/bin/wrangler.js'),...command,'--name',name,'--config',config,'--json'],{cwd:root,windowsHide:true,env:{...process.env,WRANGLER_SEND_METRICS:'false'},stdio:['ignore','pipe','pipe']});let stdout='',stderrSize=0,ended=false;
 const finish=value=>{if(!ended){ended=true;clearTimeout(timer);resolve(value);}};const timer=setTimeout(()=>{child.kill();finish({error:'CLI_TIMEOUT'});},45000);
 child.stdout.setEncoding('utf8');child.stdout.on('data',b=>{stdout+=b;if(stdout.length>1048576){child.kill();finish({error:'CLI_RESPONSE_LIMIT'});}});child.stderr.on('data',b=>{stderrSize+=b.length;});
 child.on('error',()=>finish({error:'CLI_UNAVAILABLE'}));child.on('close',code=>{if(code!==0)return finish({error:'CLI_READ_FAILED',exit_code:code,stderr_present:stderrSize>0});try{finish({value:JSON.parse(stdout)});}catch{finish({error:'CLI_JSON_INVALID'});}});
 });}
export async function diagnose(){
 const raw=await fs.readFile(config,'utf8'),parsed=ts.parseConfigFileTextToJson(config,raw);if(parsed.error)throw Error('CONFIG_INVALID');const c=parsed.config;
 const report={observed_at:new Date().toISOString(),worker:name,origin,reported_id:reported,read_only:true,gemini_calls:0,config:{path:'wrangler.existing.jsonc',name:c.name,main:c.main,keep_vars:c.keep_vars,workers_dev:c.workers_dev,durable_objects:c.durable_objects?.bindings?.map(b=>({name:b.name,class_name:b.class_name})),migrations:c.migrations?.map(m=>m.tag)},local:[]};
 for(const file of ['src/portable-worker.ts','.bundle/index.js','.bundle/portable-worker.mjs','.bundle/wrangler-diagnostic/portable-worker.js'])report.local.push(await localFile(file));
 if(cloudflare){const current=await wrangler(['deployments','status']);if(current.value){report.active=deploymentView(current.value);report.reported_id_kind=report.active.deployment_id===reported?'DEPLOYMENT_ID':report.active.versions.some(v=>v.version_id===reported)?'VERSION_ID':'NOT_IN_CURRENT_DEPLOYMENT';
  const single=report.active.versions.filter(v=>v.percentage===100&&/^[a-f0-9-]{36}$/i.test(v.version_id));if(single.length===1){const version=await wrangler(['versions','view',single[0].version_id]);report.active_version=version.value?cleanVersion(version.value):version;}
 }else report.control_plane=current;}
 if(network){report.public_health=await probe(origin+'/health');report.public_v2=await probe(origin+'/v2/account/status','POST');const id=report.active_version?.version_id;if(id&&/^[a-f0-9-]{36}$/i.test(id))report.pinned_health=await probe(origin+'/health','GET',{'Cloudflare-Workers-Version-Overrides':name+'="'+id+'"'});}
 report.acceptance=report.public_health?.status===200&&report.public_health?.release==='caritda-portable-ai-v2'&&report.public_health?.model_enabled===false&&report.public_v2?.error_release==='caritda-portable-ai-v2'&&((report.public_v2?.status===401&&report.public_v2?.code==='AUTH_REQUIRED')||(report.public_v2?.status===503&&report.public_v2?.code==='PORTABLE_DISABLED'))?'PUBLIC_V2_MODEL_OFF_PASS':'NOT_VERIFIED_OR_MISMATCH';
 const target=path.resolve(root,'../../.tools/deployment-mismatch');await fs.mkdir(target,{recursive:true});await fs.writeFile(path.join(target,'report.json'),JSON.stringify(report,null,2));return report;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){console.log(JSON.stringify(await diagnose(),null,2));}
