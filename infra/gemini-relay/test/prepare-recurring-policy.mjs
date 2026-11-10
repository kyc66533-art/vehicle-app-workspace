// Local non-secret config preparation only. No account/UID/key/Secret input or deployment.
import fs from'node:fs';import path from'node:path';import{fileURLToPath}from'node:url';import{createHash}from'node:crypto';
export const INITIAL_RECURRING=Object.freeze({user_daily:5,user_monthly:100,device_daily:5,device_monthly:100,service_monthly:300,monthly_micros:3000000,reserve_micros:10000});
export function prepare(base,bundle,enabled=false){
 if(base.name!=='caritda-ai-relay-dev'||base.keep_vars!==true||base.durable_objects?.bindings?.length!==1||base.durable_objects.bindings[0].name!=='BUDGET'||base.durable_objects.bindings[0].class_name!=='BudgetGate')throw Error('Existing binding/config must be preserved');
 const next=structuredClone(base);next.main=bundle;next.vars={...(base.vars||{}),PORTABLE_ENABLED:'true',MODEL_ENABLED:String(enabled),POC_ENABLED:'false',RECURRING_POLICY:JSON.stringify(INITIAL_RECURRING)};return next;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const project=fileURLToPath(new URL('../../../',import.meta.url)),relay=fileURLToPath(new URL('../',import.meta.url));
 const folder=path.join(project,'.tools','worker-operations'),bundle=path.join(relay,'.bundle','portable-worker.mjs');
 const base=JSON.parse(fs.readFileSync(path.join(relay,'wrangler.existing.jsonc'),'utf8').replace(/^\s*\/\/.*$/gm,''));
 const sha=createHash('sha256').update(fs.readFileSync(bundle)).digest('hex').toUpperCase();fs.mkdirSync(folder,{recursive:true});
 for(const [name,enabled]of [['model-off',false],['model-on',true]]){const file=path.join(folder,name+'.json');if(fs.existsSync(file))throw Error('Prepared config exists; preserve it and review rather than overwrite');fs.writeFileSync(file,JSON.stringify(prepare(base,bundle,enabled),null,2),{flag:'wx'});}
 console.log(JSON.stringify({prepared:true,deployed:false,model_calls:0,secret_changes:0,bundle_sha256:sha,configs:folder,service_monthly:300,reserved_usd:3}));
}
