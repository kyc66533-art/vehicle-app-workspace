export const ORIGIN="https://caritda-ai-relay-dev.kyc66533.workers.dev";
export const PORTABLE_CONTRACT="caritda-analysis-v2";
export const PORTABLE_RELEASE="caritda-portable-ai-v2";
export class ApiError extends Error {status:number;code:string;constructor(status:number,code:string){super(code);this.status=status;this.code=code;}}
export function reject(ok:unknown,code="INVALID_INPUT",status=400):asserts ok {if(!ok)throw new ApiError(status,code);}
export function keys(value:unknown,names:string[]):asserts value is Record<string,any> {
 reject(value&&typeof value==="object"&&!Array.isArray(value)&&Object.keys(value).sort().join(",")===[...names].sort().join(","));
}
export function response(value:unknown,status=200){return new Response(JSON.stringify(value),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store","x-content-type-options":"nosniff","referrer-policy":"no-referrer"}});}
// Both Request and Response expose these fields. Do not construct a Node-style duplex Request.
export async function bodyText(request:Pick<Request,"headers"|"body">,limit=4096):Promise<string>{
 const length=request.headers.get("content-length");reject(!length||/^\d+$/.test(length)&&Number(length)<=limit,"BODY_LIMIT",413);
 reject(request.body,"BODY_REQUIRED");const reader=request.body.getReader();const chunks:Uint8Array[]=[];let size=0;
 try{for(;;){const item=await reader.read();if(item.done)break;size+=item.value.byteLength;if(size>limit){await reader.cancel();throw new ApiError(413,"BODY_LIMIT");}chunks.push(item.value);}}
 finally{reader.releaseLock();}
 const bytes=new Uint8Array(size);let at=0;for(const part of chunks){bytes.set(part,at);at+=part.length;}
 try{return new TextDecoder("utf-8",{fatal:true}).decode(bytes);}catch{throw new ApiError(400,"INVALID_UTF8");}
}
export async function bodyJson(request:Request,limit=4096):Promise<any>{try{return JSON.parse(await bodyText(request,limit));}catch(e){if(e instanceof ApiError)throw e;throw new ApiError(400,"INVALID_JSON");}}
export const b64=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replace(/=/g,"").replace(/\+/g,"-").replace(/\//g,"_");
export function unb64(text:string,length:number):Uint8Array<ArrayBuffer>{
 reject(typeof text==="string"&&/^[A-Za-z0-9_-]+$/.test(text)&&text.length<=2048);
 try{const raw=atob(text.replace(/-/g,"+").replace(/_/g,"/"));const bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));reject(bytes.length===length&&b64(bytes)===text);return bytes;}catch(e){if(e instanceof ApiError)throw e;throw new ApiError(400,"INVALID_ENCODING");}
}
export async function digest(text:string){return [...new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(text)))].map(x=>x.toString(16).padStart(2,"0")).join("");}
export function proofBytes(fields:string[]):Uint8Array<ArrayBuffer>{
 reject(fields.length===12);const parts=fields.map(s=>new TextEncoder().encode(s));reject(parts.every(p=>p.length<=1024));
 const bytes=new Uint8Array(parts.reduce((n,p)=>n+4+p.length,0));reject(bytes.length<=8192);const view=new DataView(bytes.buffer);let at=0;
 for(const p of parts){view.setUint32(at,p.length,false);at+=4;bytes.set(p,at);at+=p.length;}return bytes;
}
export async function publicKey(jwk:any):Promise<{jwk:JsonWebKey,thumb:string,key:CryptoKey}>{
 keys(jwk,["kty","crv","x","y"]);reject(jwk.kty==="EC"&&jwk.crv==="P-256");unb64(jwk.x,32);unb64(jwk.y,32);
 const canonical=JSON.stringify({crv:"P-256",kty:"EC",x:jwk.x,y:jwk.y});let key:CryptoKey;
 try{key=await crypto.subtle.importKey("jwk",jwk,{name:"ECDSA",namedCurve:"P-256"},false,["verify"]);}catch{throw new ApiError(400,"DEVICE_KEY_INVALID");}
 const thumb=b64(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(canonical))));return {jwk,thumb,key};
}
export async function verifyProof(jwk:JsonWebKey,fields:string[],signature:string){
 try{const decoded=await publicKey(jwk);reject(await crypto.subtle.verify({name:"ECDSA",hash:"SHA-256"},decoded.key,unb64(signature,64),proofBytes(fields)),"DEVICE_PROOF_INVALID",403);}
 catch(e){if(e instanceof ApiError&&e.code==="DEVICE_PROOF_INVALID")throw e;throw new ApiError(403,"DEVICE_PROOF_INVALID");}
}
export function uuid(value:unknown):value is string {return typeof value==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);}
