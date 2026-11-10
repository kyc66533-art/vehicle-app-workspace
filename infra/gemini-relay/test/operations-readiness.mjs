import assert from'node:assert/strict';
import{boundedModelBody}from'../src/provider-budget.ts';
import{prepare,INITIAL_RECURRING}from'./prepare-recurring-policy.mjs';
import{runtimePolicy}from'../src/portable-gate.ts';
import{recurringRemaining as left,reserveRecurring as reserve,validateRecurring}from'../src/recurring-quota.ts';
let checks=0;const ck=v=>{assert.ok(v);checks++;};
const old={global_total:2,per_minute:1,expires_at:1,users:{'PRIVATE_FIXTURE_UID':{enabled:true,after:10,total:1,daily:1,devices:2,device_total:1}}},copy=JSON.stringify(old),prepared=runtimePolicy(copy,JSON.stringify(INITIAL_RECURRING),Date.now());
ck(JSON.stringify(old)===copy);ck(JSON.stringify(prepared.users)===JSON.stringify(old.users));ck(prepared.global_total===2&&prepared.expires_at===0&&prepared.recurring.user_daily===5);
assert.throws(()=>runtimePolicy(copy,undefined,Date.now()));checks++;
assert.throws(()=>runtimePolicy(copy,'{}',Date.now()));checks++;
const config={name:'caritda-ai-relay-dev',keep_vars:true,migrations:[{tag:'caritda-budget-v1',new_sqlite_classes:['BudgetGate']}],durable_objects:{bindings:[{name:'BUDGET',class_name:'BudgetGate'}]}},off=prepare(config,'LOCAL_BUNDLE');
ck(off.vars.MODEL_ENABLED==='false'&&!('PORTABLE_POLICY' in off.vars));ck(JSON.stringify(off.migrations)===JSON.stringify(config.migrations)&&JSON.stringify(off.durable_objects)===JSON.stringify(config.durable_objects));
ck(boundedModelBody({generationConfig:{maxOutputTokens:1024},contents:[{text:'fixture'}]}).includes('1024'));
for(const value of [{generationConfig:{maxOutputTokens:1025}},{generationConfig:{maxOutputTokens:1024},tools:[{}]},{generationConfig:{maxOutputTokens:1024},text:'가'.repeat(3000)}]){assert.throws(()=>boundedModelBody(value),e=>e.code==='MODEL_REQUEST_LIMIT');checks++;}
const r=validateRecurring({...INITIAL_RECURRING,device_daily:1,device_monthly:1}),now=Date.parse('2026-11-10T12:00:00Z'),s={legacyCheckpoint:0,used:0,counts:{}};
reserve(s,r,'u','d',now);ck(left(s,r,'u','d',now+86400000)===0);ck(left(s,r,'u','d',Date.parse('2026-12-01T00:00:00Z'))===1);
const prior={legacyCheckpoint:1,used:1,counts:{'uid:u':1,'device:d':1},recurring:{checkpoint:{at:now,legacy:1,portable:1},months:{'2026-11':{service:2,micros:20000,users:{u:1}}}}};
ck(left(prior,r,'u','d',now)===0&&prior.recurring.months['2026-11'].devices.d===1);ck(prior.used===1&&prior.legacyCheckpoint===1);
// Reserve ten times the former lifetime cap without resetting the original counters.
const life={legacyCheckpoint:1,used:1,counts:{}};for(let i=0;i<10;i++)reserve(life,INITIAL_RECURRING,'u','d',now+i*86400000);ck(life.used===1&&life.legacyCheckpoint===1&&life.recurring.months['2026-11'].service===12);
// Cost planning doubles the 8192-byte envelope allowance; this is not a tokenizer/billing receipt.
const worst=16384*0.30/1e6+1024*2.50/1e6;ck(worst<INITIAL_RECURRING.reserve_micros/1e6);ck(INITIAL_RECURRING.service_monthly*INITIAL_RECURRING.reserve_micros===INITIAL_RECURRING.monthly_micros);
console.log('OPERATIONS_READINESS_PASS checks='+checks+' network_calls=0 model_calls=0');
