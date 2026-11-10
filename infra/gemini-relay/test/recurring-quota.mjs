import assert from'node:assert/strict';import{recurringRemaining as left,reserveRecurring as reserve,validateRecurring}from'../src/recurring-quota.ts';
let checks=0;const ck=(v)=>{assert.ok(v);checks++;};const r=validateRecurring({user_daily:5,user_monthly:10,device_daily:5,service_monthly:20,monthly_micros:20000,reserve_micros:1000});
const now=Date.parse('2026-11-10T14:59:00Z'),s={legacyCheckpoint:1,used:2,counts:{'uid:u':2,'device:d':2}},original=JSON.stringify(s);
ck(left(s,r,'u','d',now)===3);const checkpoint=JSON.stringify(s.recurring.checkpoint);
for(let i=0;i<3;i++){reserve(s,r,'u','d',now);s.counts['day:u:2026-11-10']=(s.counts['day:u:2026-11-10']||0)+1;}
ck(left(s,r,'u','d',now)===0);assert.throws(()=>reserve(s,r,'u','d',now));checks++;
ck(left(s,r,'u','d',now+120000)===5);ck(JSON.stringify(s.recurring.checkpoint)===checkpoint);
ck(left(s,r,'u','d',Date.parse('2026-12-01T00:00:00Z'))===5);ck(s.used===2&&s.legacyCheckpoint===1&&s.counts['uid:u']===2);
const cap={...r,monthly_micros:1000},empty={legacyCheckpoint:0,used:0,counts:{}};reserve(empty,cap,'u','d',now);ck(left(empty,cap,'u','d',now+86400000)===0);
ck(left(JSON.parse(JSON.stringify(empty)),cap,'u','d',now+86400000)===0);ck(left(empty,cap,'u','d',Date.parse('2026-12-01T00:00:00Z'))===1);
assert.throws(()=>validateRecurring({...r,user_daily:6}));checks++;ck(original.includes('"used":2'));
console.log('RECURRING_QUOTA_PASS checks='+checks+' real_model_calls=0');
