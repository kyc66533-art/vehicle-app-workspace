// Read-only public checks. No bearer, user record, provider key or model request.
import fs from'node:fs/promises';
const origin='https://caritda-ai-relay-dev.kyc66533.workers.dev';
const h=await fetch(origin+'/health',{redirect:'error',signal:AbortSignal.timeout(10000)}),j=await h.json();
const r=await fetch(origin+'/v2/issues/explanations',{method:'POST',headers:{'content-type':'application/json'},body:'{}',redirect:'error',signal:AbortSignal.timeout(10000)}),v=await r.json();
const result={at:new Date().toISOString(),release:j.release,features:j.features,portable_enabled:j.portable_enabled,model_enabled:j.model_enabled,health_status:h.status,unauthenticated_status:r.status,unauthenticated_code:v.error?.code,gemini_calls:0};
result.pass=h.status===200&&j.release==='caritda-portable-ai-v2'&&j.features?.includes('selected-issue-v1')&&j.features?.includes('firebase-keys-manual-v1')&&j.portable_enabled===true&&j.model_enabled===false&&r.status===401&&v.error?.code==='AUTH_REQUIRED';
await fs.mkdir(new URL('../../../.tools/issue-ai-diagnostics/',import.meta.url),{recursive:true});await fs.writeFile(new URL('../../../.tools/issue-ai-diagnostics/public-status.json',import.meta.url),JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));if(!result.pass)process.exitCode=1;
