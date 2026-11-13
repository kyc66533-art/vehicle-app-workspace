import assert from 'node:assert/strict';
import {nearbyRepair} from '../src/nearby-repair.ts';
import {ApiError} from '../src/portable-common.ts';

const env={PLACES_ENABLED:'true',KAKAO_LOCAL_REST_KEY:'test_not_real_secret_123456'};
let calls=0;
const backend=async (url,options)=>{
  calls++;
  const target=new URL(url);
  assert.equal(target.origin,'https://dapi.kakao.com');
  assert.equal(target.searchParams.get('x'),'129.061');
  assert.equal(target.searchParams.get('y'),'35.159');
  assert.equal(target.searchParams.get('sort'),'distance');
  assert.equal(options.headers.Authorization,'KakaoAK '+env.KAKAO_LOCAL_REST_KEY);
  return new Response(JSON.stringify({documents:[
    {id:'101',place_name:'현대자동차 블루핸즈 A',y:'35.160',x:'129.060',distance:'210',road_address_name:'부산광역시 시험구 A로',phone:'051-100-0000'},
    {id:'102',place_name:'현대자동차 블루핸즈 B',y:'35.165',x:'129.055',distance:'600',road_address_name:'부산광역시 시험구 B로',phone:''},
    {id:'103',place_name:'현대자동차 블루핸즈 C',y:'35.167',x:'129.051',distance:'1050',road_address_name:'부산광역시 시험구 C로',phone:''}
  ]}),{status:200,headers:{'content-type':'application/json'}});
};
const result=await nearbyRepair({latitude:35.159,longitude:129.061},env,backend);
assert.equal(result.status,200);
const json=await result.json();
assert.equal(calls,1);
assert.equal(json.places.length,3);
assert.equal(json.places[0].distance_m,210);
assert.equal(json.verified_service_network,false);
assert.equal(json.places[0].category,'BLUEHANDS_CANDIDATE');
assert.deepEqual(json.places.map(p=>p.id),['101','102','103']);

await assert.rejects(nearbyRepair({latitude:35.159,longitude:129.061,issue:'private'},env,backend),e=>e instanceof ApiError&&e.status===400);
await assert.rejects(nearbyRepair({latitude:0,longitude:0},env,backend),e=>e instanceof ApiError&&e.code==='LOCATION_OUT_OF_RANGE');
await assert.rejects(nearbyRepair({latitude:35.159,longitude:129.061},{PLACES_ENABLED:'false'},backend),e=>e instanceof ApiError&&e.status===503);
assert.equal(calls,1,'rejected inputs must never reach provider');
console.log('CARITDA_NEARBY_REPAIR_OFFLINE_PASS checks=11 provider_real_calls=0 gemini_calls=0');
