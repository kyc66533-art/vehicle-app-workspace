// Fails closed before an operator uploads an old or wrong local entry. Never deploys itself.
import {localFile} from './deployment-status.mjs';
const file=process.argv[2]||'.bundle/portable-worker.mjs';
if(!['src/portable-worker.ts','.bundle/portable-worker.mjs','.bundle/wrangler-diagnostic/portable-worker.js','.bundle/index.js'].includes(file))throw Error('Use an audited project entry, not arbitrary downloaded code');
const r=await localFile(file);
const valid=r.health?.status===200&&r.health?.release==='caritda-portable-ai-v2'&&r.health?.model_enabled===false&&r.v2_model_off?.status===503&&r.v2_model_off?.code==='PORTABLE_DISABLED'&&r.v2_model_off?.error_release==='caritda-portable-ai-v2'&&r.v2_enabled_unauthenticated?.status===401&&r.v2_enabled_unauthenticated?.code==='AUTH_REQUIRED'&&r.v2_enabled_unauthenticated?.error_release==='caritda-portable-ai-v2';
console.log(JSON.stringify({file,sha256:r.sha256??null,default_export_v2:valid,diagnostic:valid?'LOCAL_V2_ENTRY_VERIFIED':'WRONG_OR_UNREADABLE_ENTRY',deployment_performed:false}));
if(!valid)process.exitCode=1;
