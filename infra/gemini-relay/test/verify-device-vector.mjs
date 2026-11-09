// Offline interoperability check only: fixture Keystore public key/proof, no Worker/model call.
import fs from 'node:fs/promises';
import { webcrypto } from 'node:crypto';
const v=JSON.parse(await fs.readFile(process.argv[2],'utf8'));
if(v.fixture!==true||!v.alias.startsWith('caritda.validation.fixture.'))throw Error('Only validation fixture vectors are accepted');
let count=0;
function verify(ok,label){if(!ok)throw Error(label);count++;}
const decode=s=>Buffer.from(s,'base64url');
const canonical=JSON.stringify({crv:'P-256',kty:'EC',x:v.jwk.x,y:v.jwk.y});
const hash=await webcrypto.subtle.digest('SHA-256',Buffer.from(canonical));
verify(Buffer.from(hash).toString('base64url')===v.thumbprint,'RFC7638 thumbprint mismatch');
const key=await webcrypto.subtle.importKey('jwk',v.jwk,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
const proof=decode(v.proof),signature=decode(v.signature);
verify(signature.length===64&&await webcrypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,signature,proof),'Actual Keystore signature fails WebCrypto');
const altered=Buffer.from(proof);altered[altered.length-1]^=1;
verify(!await webcrypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,signature,altered),'Changed proof unexpectedly accepted');
const expected=Buffer.concat(v.fields.map(s=>{const b=Buffer.from(s,'utf8'),n=Buffer.alloc(4);n.writeUInt32BE(b.length);return Buffer.concat([n,b]);}));
verify(expected.equals(proof),'Multibyte field encoding differs across platforms');
console.log('PORTABLE_KEY_WEBCRYPTO_PASS checks='+count+' (offline fixture; actual Worker unverified)');
