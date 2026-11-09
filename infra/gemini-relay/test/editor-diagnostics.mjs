// Diagnostic audit only: generated JS is not the source TypeScript type-check target.
import fs from 'node:fs';import path from 'node:path';import ts from 'typescript';
const file=path.resolve(process.argv[2]||new URL('../.bundle/portable-worker.mjs',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1'));
const source=fs.readFileSync(file,'utf8');
function run(text,target){
 const options={target,module:ts.ModuleKind.ESNext,moduleResolution:ts.ModuleResolutionKind.Bundler,lib:['lib.es2022.d.ts','lib.webworker.d.ts'],allowJs:true,checkJs:true,noEmit:true,strict:false,skipLibCheck:true};
 const host=ts.createCompilerHost(options),read=host.readFile.bind(host);host.readFile=f=>path.resolve(f)===file?text:read(f);
 return ts.getPreEmitDiagnostics(ts.createProgram([file],options,host)).filter(d=>d.file&&path.resolve(d.file.fileName)===file).map(d=>({code:d.code,line:d.file.getLineAndCharacterOfPosition(d.start||0).line+1,message:ts.flattenDiagnosticMessageText(d.messageText,' ')}));
}
const unmarked=source.replace(/^\/\/ @ts-nocheck[^\r\n]*[\r\n]+/,'');
const actual=run(source,ts.ScriptTarget.ES2022),modern=run(unmarked,ts.ScriptTarget.ES2022),downlevel=run(unmarked,ts.ScriptTarget.ES2021);
console.log(JSON.stringify({file:path.basename(file),typescript:ts.version,generatedDirective:source.startsWith('// @ts-nocheck'),editorProfile:'standard WebWorker lib; NOT the exact Cloudflare editor configuration',actualCount:actual.length,unsuppressedES2022:modern,unsuppressedES2021:downlevel},null,2));
if(actual.length&&source.startsWith('// @ts-nocheck'))process.exitCode=1;
