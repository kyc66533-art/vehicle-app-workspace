// Local-only compilation; no Wrangler login, deployment or network calls.
import {build} from 'esbuild';
import {readFile} from 'node:fs/promises';
const notice=await readFile(new URL('../THIRD_PARTY_NOTICES.md',import.meta.url),'utf8');
await build({entryPoints:[new URL('../src/portable-worker.ts',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1')],bundle:true,platform:'browser',format:'esm',target:'es2022',outfile:new URL('../.bundle/portable-worker.mjs',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1'),legalComments:'eof',banner:{js:'// @ts-nocheck\n// GENERATED JavaScript including jose. Type-check src/*.ts, not erased dependency types.\n/*\n'+notice+'\n*/'}});
console.log('PORTABLE_BUNDLE_PASS (local only, not deployed)');
