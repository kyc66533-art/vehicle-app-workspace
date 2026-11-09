import {handle as legacy} from './index.ts';
import {BudgetGate,GLOBAL} from './portable-gate.ts';
import {verifyFirebase} from './firebase-token.ts';
import {ApiError,reject,response,bodyJson,ORIGIN,PORTABLE_RELEASE} from './portable-common.ts';
export {BudgetGate};
export async function handlePortable(request:Request,env:any,authenticate=verifyFirebase){
 try{
  const url=new URL(request.url);reject(url.origin===ORIGIN&&!url.search&&!url.hash,'INVALID_PATH');
  if(url.pathname==='/health')return response({release:PORTABLE_RELEASE,features:['selected-issue-v1','firebase-keys-manual-v1'],portable_enabled:env.PORTABLE_ENABLED==='true',model_enabled:env.MODEL_ENABLED==='true',auth_required:true});
  if(url.pathname.startsWith('/v1/'))return legacy(request,{...env,POC_ENABLED:'false'});
  reject(request.method==='POST','METHOD',405);reject(!request.headers.has('origin'),'BROWSER_ORIGIN_REJECTED',403);
  reject(env.PORTABLE_ENABLED==='true'&&env.BUDGET,'PORTABLE_DISABLED',503);
  const route=url.pathname==='/v2/devices/challenge'?'challenge':url.pathname==='/v2/devices/register'?'register':url.pathname==='/v2/explanations'?'explain':url.pathname==='/v2/issues/explanations'?'issue-explain':url.pathname==='/v2/issues/consent/revoke'?'issue-revoke':url.pathname==='/v2/devices/revoke'?'revoke':url.pathname==='/v2/account/status'?'status':/^\/v2\/operations\/o2\.\d{13}\.[0-9a-f-]{36}$/.test(url.pathname)?'read':null;reject(route,'NOT_FOUND',404);
  const auth=request.headers.get('authorization')||'';reject(/^Bearer [A-Za-z0-9_.-]{1,8192}$/.test(auth),'AUTH_REQUIRED',401);
  reject(/^application\/json(?:;\s*charset=utf-8)?$/i.test(request.headers.get('content-type')||'')&&!request.headers.has('content-encoding'),'CONTENT_TYPE',415);
  const principal=await authenticate(auth.slice(7),env.FIREBASE_PROJECT_ID);
  const body=await bodyJson(request,8192),device=request.headers.get('x-caritda-device-id')||'',version=Number(request.headers.get('x-caritda-key-version')||'1');
  if(route==='read')reject(body.operation_id===url.pathname.slice('/v2/operations/'.length),'OPERATION_MISMATCH');
  const stub=env.BUDGET.get(env.BUDGET.idFromName(GLOBAL));
  return await stub.fetch(new Request('https://budget.internal/portable/'+route,{method:'POST',body:JSON.stringify({principal,body,device,version})}));
 }catch(e){return response({error:{code:e instanceof ApiError?e.code:'PORTABLE_UNAVAILABLE',release:PORTABLE_RELEASE}},e instanceof ApiError?e.status:503);}
}
// Cloudflare passes ExecutionContext as arg3; never treat it as the test authenticator.
export default {fetch(request:Request,env:any){return handlePortable(request,env);}};
