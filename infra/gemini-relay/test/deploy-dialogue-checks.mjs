import assert from 'node:assert/strict';import {validatePreserved,bindingFingerprint,deploymentPlan,APPROVED_DIALOGUE_BUNDLE,APPROVED_BEFORE_VERSION,APPROVED_ISSUED_READ_BUNDLE,APPROVED_ISSUED_READ_BEFORE_VERSION,APPROVED_CONTEXT_BUNDLE,APPROVED_CONTEXT_BEFORE_VERSION} from './deploy-dialogue.mjs';
const before={resources:{bindings:[{name:'BUDGET',type:'durable_object_namespace',namespace_id:'fixture-namespace',class_name:'BudgetGate'},{name:'GEMINI_API_KEY',type:'secret_text'},{name:'PORTABLE_POLICY',type:'secret_text'},{name:'MODEL_ENABLED',type:'plain_text',text:'true'},{name:'PORTABLE_ENABLED',type:'plain_text',text:'true'},{name:'RECURRING_POLICY',type:'plain_text',text:JSON.stringify({user_daily:5,user_monthly:100,service_monthly:300,reserve_micros:10000,monthly_micros:3000000})}]}};
let checks=0;validatePreserved(before,structuredClone(before));checks++;
for(const mutate of [b=>b.resources.bindings[0].namespace_id='different',b=>b.resources.bindings[3].text='false',b=>b.resources.bindings.pop(),b=>b.resources.bindings[1].type='plain_text',b=>b.resources.bindings.push({name:'EXTRA',type:'plain_text',text:'new'})]){const after=structuredClone(before);mutate(after);assert.throws(()=>validatePreserved(before,after));checks++}
assert.ok(!JSON.stringify(bindingFingerprint(before)).includes('fixture-namespace'));checks++;
assert.equal(deploymentPlan().bundle,APPROVED_DIALOGUE_BUNDLE);checks++;
assert.equal(deploymentPlan().before,APPROVED_BEFORE_VERSION);checks++;
assert.equal(deploymentPlan(true).bundle,APPROVED_ISSUED_READ_BUNDLE);checks++;
assert.equal(deploymentPlan(true).before,APPROVED_ISSUED_READ_BEFORE_VERSION);checks++;
assert.notEqual(deploymentPlan().journal,deploymentPlan(true).journal);checks++;
assert.equal(deploymentPlan('context').bundle,APPROVED_CONTEXT_BUNDLE);checks++;
assert.equal(deploymentPlan('context').before,APPROVED_CONTEXT_BEFORE_VERSION);checks++;
assert.notEqual(deploymentPlan(true).journal,deploymentPlan('context').journal);checks++;
console.log('DIALOGUE_DEPLOYMENT_GUARDS_PASS checks='+checks+' real_deployments=0 real_model_calls=0');
