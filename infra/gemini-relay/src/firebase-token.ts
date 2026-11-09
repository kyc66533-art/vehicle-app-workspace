import { importX509,jwtVerify, type JWTVerifyGetKey } from "jose";
import { ApiError,reject,bodyText } from "./portable-common.ts";
const CERT_URL="https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
type Cached={until:number,certs:Record<string,string>};
let cache:Cached|undefined,pending:Promise<Cached>|undefined,cooldown=0;
async function certificates(now:number,fetcher=fetch,force=false):Promise<Cached>{
 if(!force&&cache&&cache.until>now)return cache;
 if(pending)return pending;
 reject(now>=cooldown,"AUTH_KEYS_COOLDOWN",503);cooldown=now+10000;
 pending=(async()=>{try{
  const r=await fetcher(CERT_URL,{redirect:"manual",signal:AbortSignal.timeout(5000)});reject(r.ok,"AUTH_KEYS_HTTP_"+r.status,503);
  let text:string;try{text=await bodyText(r,65536);}catch{throw new ApiError(503,"AUTH_KEYS_BODY");}
  let certs:any;try{certs=JSON.parse(text);}catch{throw new ApiError(503,"AUTH_KEYS_JSON");}
  reject(certs&&typeof certs==="object"&&!Array.isArray(certs)&&Object.keys(certs).length>=1&&Object.keys(certs).length<=8,"AUTH_KEYS_SCHEMA",503);
  for(const [kid,pem] of Object.entries(certs))reject(/^[A-Za-z0-9_-]{1,128}$/.test(kid)&&typeof pem==="string"&&pem.length<=8192&&pem.startsWith("-----BEGIN CERTIFICATE-----"),"AUTH_KEYS_SCHEMA",503);
  const age=Number(/(?:^|,)\s*max-age=(\d+)/i.exec(r.headers.get("cache-control")||"")?.[1]||"60");
  cache={certs,until:now+Math.min(3600,Math.max(0,age))*1000};return cache;
 }catch(e){if(e instanceof ApiError)throw e;const name=(e as {name?:string})?.name;throw new ApiError(503,name==="TimeoutError"||name==="AbortError"?"AUTH_KEYS_TIMEOUT":"AUTH_KEYS_NETWORK");}})();
 try{return await pending;}finally{pending=undefined;}
}
export interface Principal {uid:string,authTime:number,issuedAt:number}
export async function verifyFirebase(token:string,project:string,nowMs=Date.now(),keyOverride?:JWTVerifyGetKey):Promise<Principal>{
 reject(/^[a-z][a-z0-9-]{4,62}$/.test(project),"AUTH_NOT_CONFIGURED",503);
 reject(typeof token==="string"&&token.length<=8192&&token.split(".").length===3,"AUTH_REQUIRED",401);
 try {
  const resolve:JWTVerifyGetKey=keyOverride??(async header=>{
   reject(header.alg==="RS256"&&typeof header.kid==="string"&&/^[A-Za-z0-9_-]{1,128}$/.test(header.kid),"AUTH_INVALID_TOKEN",401);
   let set=await certificates(nowMs);if(!Object.hasOwn(set.certs,header.kid))set=await certificates(nowMs,fetch,true);reject(Object.hasOwn(set.certs,header.kid),"AUTH_UNKNOWN_KEY",401);
   return importX509(set.certs[header.kid],"RS256");
  });
  const verified=await jwtVerify(token,resolve,{algorithms:["RS256"],issuer:"https://securetoken.google.com/"+project,audience:project,requiredClaims:["exp","iat","auth_time","sub"],clockTolerance:30,currentDate:new Date(nowMs)});
  const p=verified.payload,h=verified.protectedHeader,now=nowMs/1000;
  reject(h.alg==="RS256"&&!h.jku&&!h.x5u&&typeof h.kid==="string"&&p.aud===project&&p.iss==="https://securetoken.google.com/"+project,"AUTH_INVALID_TOKEN",401);
  reject(typeof p.sub==="string"&&p.sub.length>0&&p.sub.length<=128&&[p.iat,p.exp,p.auth_time].every(x=>typeof x==="number"&&Number.isSafeInteger(x)),"AUTH_INVALID_TOKEN",401);
  const authTime=p.auth_time as number;
  reject(p.iat!<=now+30&&authTime<=now+30&&authTime<=p.iat!+30&&p.exp!>now-30&&p.exp!>p.iat!,"AUTH_INVALID_TOKEN",401);
  const firebase=p.firebase as Record<string,unknown>|undefined;reject(firebase?.sign_in_provider==="password","AUTH_PROVIDER_REJECTED",403);
  return {uid:p.sub,authTime,issuedAt:p.iat!};
 }catch(e){if(e instanceof ApiError)throw e;throw new ApiError(401,"AUTH_INVALID_TOKEN");}
}
