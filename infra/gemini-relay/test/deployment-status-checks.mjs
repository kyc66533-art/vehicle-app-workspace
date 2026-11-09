import assert from 'node:assert/strict';import {cleanVersion,deploymentView,summary} from './deployment-status.mjs';
let n=0;const check=(ok,label)=>{assert.ok(ok,label);n++;};
const sanitized=cleanVersion({id:'fixture-version',metadata:{author_email:'PRIVATE_EMAIL'},resources:{script:{etag:'opaque',source:'PRIVATE_CODE'},bindings:[{name:'GEMINI_API_KEY',type:'secret_text',text:'PRIVATE_SECRET'},{name:'PORTABLE_POLICY',type:'plain_text',text:'PRIVATE_UID_POLICY'},{name:'BUDGET',type:'durable_object_namespace',class_name:'BudgetGate',namespace_id:'PRIVATE_NAMESPACE'}]}});
check(!JSON.stringify(sanitized).includes('PRIVATE'),'binding/author/source values are not reported');
check(sanitized.bindings[2].namespace_sha256.length===64,'namespace equality can be compared without raw ID');
const view=deploymentView({id:'deployment-A',versions:[{version_id:'version-B',percentage:100}]});check(view.deployment_id==='deployment-A'&&view.versions[0].version_id==='version-B','deployment ID and version ID remain distinct');
check(summary({release:'caritda-portable-ai-v2',secret:'PRIVATE'}).release==='caritda-portable-ai-v2'&&!JSON.stringify(summary({secret:'PRIVATE'})).includes('PRIVATE'),'public report only allowlisted fields');
console.log('DEPLOYMENT_DIAGNOSTIC_CHECKS_PASS checks='+n+' (no network/deployment/model)');
