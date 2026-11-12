// Local-only build using the installed esbuild API; reads modules only within this relay folder.
import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';import {build} from 'esbuild';
const root=fileURLToPath(new URL('../',import.meta.url));
const result=await build({stdin:{contents:'export * from "./src/portable-worker.ts";export {default} from "./src/portable-worker.ts";',loader:'ts',sourcefile:'entry.ts'},bundle:true,write:false,platform:'browser',format:'esm',target:'es2022',legalComments:'eof',plugins:[{name:'relay-modules',setup(api){
 api.onResolve({filter:/.*/},args=>{
  let file;if(args.path==='jose')file='node_modules/jose/dist/webapi/index.js';
  else if(args.path.startsWith('.'))file=path.posix.normalize(path.posix.join(args.importer&&args.importer!=='entry.ts'?path.posix.dirname(args.importer):'.',args.path));
  else throw Error('UNAPPROVED_BARE_MODULE');
  if(file.startsWith('../')||path.isAbsolute(file)||file.includes('\\'))throw Error('OUTSIDE_RELAY_MODULE');
  return {path:file,namespace:'relay'};
 });
 api.onLoad({filter:/.*/,namespace:'relay'},args=>({contents:fs.readFileSync(path.join(root,args.path),'utf8'),loader:args.path.endsWith('.ts')?'ts':'js'}));
}}]});
const notice=fs.readFileSync(path.join(root,'THIRD_PARTY_NOTICES.md'),'utf8');const bytes=Buffer.from('// @ts-nocheck\n/*\n'+notice+'\n*/\n'+result.outputFiles[0].text);
const folder=path.join(root,'.bundle');fs.mkdirSync(folder,{recursive:true});fs.writeFileSync(path.join(folder,'voice-dialogue-worker.mjs'),bytes);
const manifest={sha256:createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length,networkRequests:0};fs.writeFileSync(path.join(folder,'voice-dialogue-manifest.json'),JSON.stringify(manifest,null,2));console.log(JSON.stringify(manifest));
