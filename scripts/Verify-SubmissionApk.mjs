// Verify an existing Android Studio APK. Never builds, signs, installs, or reads a keystore.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {inflateRawSync} from 'node:zlib';
import {execFileSync} from 'node:child_process';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const args=process.argv.slice(2);
const option=name=>{const i=args.indexOf(name);return i<0?undefined:args[i+1]};
const resolve=p=>path.resolve(root,p);
const candidate=resolve(option('--apk')??'NextDrive-Pleos/app/release/app-release.apk');
const sdk=option('--sdk')??process.env.ANDROID_HOME??process.env.ANDROID_SDK_ROOT??path.join(os.homedir(),'AppData','Local','Android','Sdk');
const java=path.join(process.env.JAVA_HOME??'C:/Program Files/Android/Android Studio/jbr','bin','java.exe');
const aapt=path.join(sdk,'build-tools','35.0.0','aapt2.exe');
const signer=path.join(sdk,'build-tools','35.0.0','lib','apksigner.jar');
const hash=b=>createHash('sha256').update(b).digest('hex');

/** Digest uncompressed entries: zip alignment, compression and APK signing blocks may differ. */
function inventory(file){
    const bytes=fs.readFileSync(file);
    let end=bytes.length-22;
    const earliest=Math.max(0,bytes.length-65557);
    while(end>=earliest&&bytes.readUInt32LE(end)!==0x06054b50)end--;
    if(end<earliest)throw Error('ZIP end record missing');
    let at=bytes.readUInt32LE(end+16);
    const result=[],names=new Set();
    for(let i=0;i<bytes.readUInt16LE(end+10);i++){
        if(bytes.readUInt32LE(at)!==0x02014b50)throw Error('Invalid ZIP directory');
        const method=bytes.readUInt16LE(at+10),packed=bytes.readUInt32LE(at+20),size=bytes.readUInt32LE(at+24);
        const nl=bytes.readUInt16LE(at+28),el=bytes.readUInt16LE(at+30),cl=bytes.readUInt16LE(at+32),local=bytes.readUInt32LE(at+42);
        const name=bytes.toString('utf8',at+46,at+46+nl);
        at+=46+nl+el+cl;
        if(names.has(name))throw Error('Duplicate ZIP entry');
        names.add(name);
        if(/^META-INF\/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$/i.test(name))continue;
        if(bytes.readUInt32LE(local)!==0x04034b50)throw Error('Invalid ZIP local header');
        const start=local+30+bytes.readUInt16LE(local+26)+bytes.readUInt16LE(local+28);
        const packedBytes=bytes.subarray(start,start+packed);
        const value=method===0?packedBytes:method===8?inflateRawSync(packedBytes):null;
        if(!value||value.length!==size)throw Error('Unsupported ZIP method or size');
        result.push({name,size,sha256:hash(value)});
    }
    return result.sort((a,b)=>a.name.localeCompare(b.name));
}

for(const file of [candidate,java,aapt,signer])if(!fs.existsSync(file))throw Error('Missing existing APK/tool: '+file);
const cert=execFileSync(java,['-jar',signer,'verify','--verbose','--print-certs',candidate],{encoding:'utf8'});
const certificateSha256=cert.match(/Signer #1 certificate SHA-256 digest: (\w+)/)?.[1];
if(!certificateSha256||/certificate DN:.*CN=Android Debug/.test(cert))throw Error('Not a verified Release signing certificate');
const xml=execFileSync(aapt,['dump','xmltree','--file','AndroidManifest.xml',candidate],{encoding:'utf8',maxBuffer:3e6});
const badging=execFileSync(aapt,['dump','badging',candidate],{encoding:'utf8',maxBuffer:3e6});
const pkg=badging.match(/^package: name='([^']+)'/m)?.[1];
if(pkg!=='com.nextdrive.pleos'||/debuggable[^\n]*(0xffffffff|true)/.test(xml))throw Error('Wrong package or debuggable APK');
const entries=inventory(candidate);
let sourceInputCount=0,sourceFingerprint=null,referenceContentIdentical=null;
const sourceFile=option('--source-manifest');
if(sourceFile){
    const prepared=JSON.parse(fs.readFileSync(resolve(sourceFile)));
    for(const e of prepared.sourceInputs)if(!fs.existsSync(resolve(e.path))||hash(fs.readFileSync(resolve(e.path)))!==e.sha256)throw Error('Release source changed: '+e.path);
    sourceInputCount=prepared.sourceInputs.length;
    sourceFingerprint=hash(JSON.stringify(prepared.sourceInputs));
}
const reference=option('--reference');
if(reference){
    referenceContentIdentical=JSON.stringify(entries)===JSON.stringify(inventory(resolve(reference)));
    if(!referenceContentIdentical)throw Error('APK payload differs from the frozen unsigned reference');
}
const bytes=fs.readFileSync(candidate);
const result={package:pkg,versionCode:badging.match(/versionCode='([^']+)'/)?.[1],versionName:badging.match(/versionName='([^']+)'/)?.[1],
    size:bytes.length,sha256:hash(bytes),gitBlobSha:createHash('sha1').update('blob '+bytes.length+'\0').update(bytes).digest('hex'),
    certificateSha256,debuggable:false,archiveEntries:entries.length,archiveFingerprint:hash(JSON.stringify(entries)),
    sourceInputCount,sourceFingerprint,referenceContentIdentical,rebuilt:false,resigned:false};
if(args.includes('--stage')){
    if(!sourceFile||!reference||!referenceContentIdentical)throw Error('Staging requires verified source manifest and unsigned reference');
    const out=resolve('apk/주시조_CARITDA_release.apk');
    fs.mkdirSync(path.dirname(out),{recursive:true});
    if(fs.existsSync(out)){if(hash(fs.readFileSync(out))!==result.sha256)throw Error('Refusing to overwrite another APK')}
    else fs.copyFileSync(candidate,out,fs.constants.COPYFILE_EXCL);
    fs.writeFileSync(resolve('apk/ARTIFACT.json'),JSON.stringify({path:'apk/주시조_CARITDA_release.apk',baseCommit:'7f147d9032bb00ffdc76e2e7fc9b6ce752e1c7a1',...result},null,2));
}
console.log(JSON.stringify(result,null,2));
