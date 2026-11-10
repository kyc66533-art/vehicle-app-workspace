// Provider status fixtures only: no external network, secrets, deployment or paid requests.
import assert from 'node:assert/strict';
import {explainIssue} from '../src/portable-issue.ts';
import {explain} from '../src/portable-analysis.ts';
const issue=JSON.stringify({contract:'caritda-issue-v1',source:'USER_SELECTED_RECORD',category:'NOISE',situation:'차량 앞쪽에서 소음이 들려요.'});
const soc=JSON.stringify({contract:'caritda-analysis-v2',signal:'EV_SOC',source:'PLEOS_EMULATOR_SDK',synthetic:false,unit:'%',window:{duration_ms:0,latest_age_ms:0,sample_count:1,truncated:false},facts:{latest:80,minimum:80,maximum:80,mean_of_receipts:80,delta_last_comparable:null},quality:{flag_count:0,segments:1,latest_valid:true},rule:'receipt-analysis-v1',limitations:'receipts_not_ecu;not_trip;not_time_average;no_diagnosis;no_safety_decision'});
let checks=0;
for(const [fn,payload] of [[explainIssue,issue],[explain,soc]]){
 for(const status of [404,500,502,503]){
  await assert.rejects(()=>fn(payload,'local-fixture','NOT_A_SECRET',async()=>Response.json({error:{message:'PRIVATE_PROVIDER_FIXTURE_BODY'}},{status})),e=>e.code==='MODEL_HTTP_'+status&&!String(e).includes('PRIVATE_PROVIDER_FIXTURE_BODY'));
  checks++;
 }
 await assert.rejects(()=>fn(payload,'local-fixture','NOT_A_SECRET',async()=>{throw new TypeError('PRIVATE_NETWORK_FIXTURE');}),e=>e.code==='MODEL_NETWORK'&&!String(e).includes('PRIVATE_NETWORK_FIXTURE'));
 checks++;
}
console.log('STABILIZATION_ERRORS_PASS checks='+checks+' real_model_calls=0');
