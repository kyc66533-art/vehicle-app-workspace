// Optional project-local offline engine assets. No account, credential, global install or API call.
import fs from'node:fs';import path from'node:path';import{fileURLToPath}from'node:url';import{createHash,randomUUID}from'node:crypto';
const workspace=fileURLToPath(new URL('../',import.meta.url)),folder=path.resolve(workspace,'.tools/home-wake-engine');
if(!folder.toLowerCase().startsWith(path.resolve(workspace,'.tools').toLowerCase()+path.sep))throw Error('WORKSPACE_ONLY');
const sources=[
 {name:'vosk-android-0.3.47.aar',url:'https://repo.maven.apache.org/maven2/com/alphacephei/vosk-android/0.3.47/vosk-android-0.3.47.aar',license:'Apache-2.0',checksum:true},
 {name:'jna-5.13.0.aar',url:'https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.aar',license:'Apache-2.0 OR LGPL-2.1-or-later',checksum:true},
 {name:'vosk-model-small-ko-0.22.zip',url:'https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip',license:'Apache-2.0',model:true}
];
const hash=(name,b)=>createHash(name).update(b).digest('hex');
if(process.argv[2]==='--check'){
 const file=path.join(folder,'manifest.json'),manifest=fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):null;
 const ready=manifest?.complete===true&&manifest.files?.length===sources.length&&sources.every(s=>{
  const item=manifest.files.find(f=>f.name===s.name),target=path.join(folder,s.model?'assets':'',s.name);
  return item&&fs.existsSync(target)&&fs.statSync(target).size===item.bytes&&hash('sha256',fs.readFileSync(target))===item.sha256;
 });
 console.log(JSON.stringify({projectLocal:true,ready,model:'Korean 82MB; Apache-2.0',accountsRequired:false,paidRequests:0}));
 process.exit(ready?0:2);
}
if(process.argv[2]!=='--download')throw Error('USE_DOWNLOAD_FOR_PUBLIC_PROJECT_ASSETS');
fs.mkdirSync(folder,{recursive:true});fs.mkdirSync(path.join(folder,'assets'),{recursive:true});const previous=fs.existsSync(path.join(folder,'manifest.json'))?JSON.parse(fs.readFileSync(path.join(folder,'manifest.json'),'utf8')):null;
async function get(url){const u=new URL(url);if(!['repo.maven.apache.org','alphacephei.com'].includes(u.hostname)||u.protocol!=='https:')throw Error('OFFICIAL_HTTPS_ONLY');const r=await fetch(u,{redirect:'error',signal:AbortSignal.timeout(240000)});if(!r.ok)throw Error('DOWNLOAD_HTTP_'+r.status);return r}
const files=[];
for(const s of sources){
 const target=path.join(folder,s.model?'assets':'',s.name);
 if(fs.existsSync(target)){const b=fs.readFileSync(target),known=previous?.files?.find(f=>f.name===s.name);if(!known||hash('sha256',b)!==known.sha256)throw Error('EXISTING_ARTIFACT_REQUIRES_REVIEW');files.push(known);console.log('PRESERVED '+s.name);continue}
 const expected=s.checksum?(await(await get(s.url+'.sha1')).text()).trim().split(/\s+/)[0]:null;
 if(expected&&!/^[a-f0-9]{40}$/i.test(expected))throw Error('OFFICIAL_CHECKSUM_INVALID');
 const response=await get(s.url),chunks=[];let size=0;
 for await(const chunk of response.body){size+=chunk.length;if(size>100*1024*1024)throw Error('ARTIFACT_SIZE_LIMIT');chunks.push(chunk)}
 const b=Buffer.concat(chunks);if(expected&&hash('sha1',b)!==expected.toLowerCase())throw Error('OFFICIAL_CHECKSUM_MISMATCH');
 if(b[0]!==80||b[1]!==75)throw Error('ZIP_FORMAT_REQUIRED');
 const pending=target+'.partial-'+randomUUID();fs.writeFileSync(pending,b,{flag:'wx'});if(fs.existsSync(target))throw Error('TARGET_APPEARED');fs.renameSync(pending,target);
 const item={...s,bytes:b.length,sha256:hash('sha256',b)};files.push(item);
 // Preserve a verified first download even if a later network request fails.
 fs.writeFileSync(path.join(folder,'manifest.json'),JSON.stringify({complete:false,files},null,2));console.log('VERIFIED '+s.name+' bytes='+size+' sha256='+item.sha256);
}
const model=files.find(f=>f.model);fs.writeFileSync(path.join(folder,'assets/home-wake-model.sha256'),model.sha256);
fs.writeFileSync(path.join(folder,'assets/HOME_WAKE_NOTICES.txt'),'Vosk API and vosk-model-small-ko-0.22: Apache License 2.0. https://alphacephei.com/vosk/ https://github.com/alphacep/vosk-api\nJNA 5.13.0: Apache License 2.0 or LGPL 2.1 or later. This app chooses Apache License 2.0. https://github.com/java-native-access/jna\nNo raw voice is saved. Recognition and runtime performance require emulator validation.\n');
fs.writeFileSync(path.join(folder,'manifest.json'),JSON.stringify({complete:true,files},null,2));console.log('HOME_WAKE_ASSETS_READY project_local=true accounts=0 paid_requests=0');
