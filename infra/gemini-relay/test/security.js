// Local Web API harness. Every provider response below is MOCKED; no Gemini request is made.
async function runWorkerChecks(handle, BudgetGate) {
  const lines = []; const now = Date.now(); const token = "d".repeat(43); let calls = 0;
  function ok(value, name) { if(!value) throw new Error(name); lines.push("PASS "+name); globalThis.__workerProgress=lines.length; }
  function storage() {
    const data = new Map(); let serial = Promise.resolve();
    return {get:async k=>structuredClone(data.get(k)),transaction:f=>{
      const run=serial.then(async()=>{const pending=new Map(data);const tx={get:async k=>structuredClone(pending.get(k)),put:async(k,v)=>pending.set(k,structuredClone(v))};const r=await f(tx);data.clear();for(const [k,v] of pending)data.set(k,v);return r;});serial=run.catch(()=>{});return run;
    }};
  }
  function env(){const state=storage();const gate=new BudgetGate({storage:state});return {TEST_APP_TOKEN:token,TEST_TOKEN_EXPIRES_AT:new Date(now+3600000).toISOString(),POC_ENABLED:"true",GEMINI_API_KEY:"MOCK_PROVIDER_SECRET_NOT_A_KEY",BUDGET:{idFromName:n=>{if(n!=="caritda-gemini-poc-global-v1")throw Error("unbounded scope");return n;},get:()=>gate}};}
  function facts(at=now){return {signal:"EV_SOC",source:"TEST_ONLY",unit:"%",first_received_at:at-2000,last_received_at:at,sample_count:3,latest:18,delta:-42,minimum:18,maximum:80,mean_of_receipts:158/3,rule:"receipt-analysis-v1",quality:{synthetic_fixture:3},limitations:"app_receipts_not_ecu_time;session_not_trip;emulator_is_simulated;no_diagnosis_no_safety_decision;observation_mean_not_time_average",fixture:"SOC_80_60_18_V1",synthetic:true,criterion:{operator:"LT",threshold:20}};}
  function request(f=facts(),id=crypto.randomUUID(),headers={},path="/v1/synthetic-soc"){
    const h={authorization:"Bearer "+token,"content-type":"application/json",...headers};
    const r=new Request("https://relay.test"+path,{method:"POST",body:JSON.stringify({request_id:id,analysis_payload:JSON.stringify(f)})});
    // Browser Request filters forbidden outbound Origin; Workers receive that inbound header.
    return {url:r.url,method:r.method,body:r.body,headers:new Headers(h)};
  }
  const answer={explanation:"설정한 충전량 기준보다 낮아졌어요. 다음 운행 전에 충전 계획을 확인해 주세요.",action:"CHECK_CHARGING_PLAN"};
  const mock=async()=>{calls++;return new Response(JSON.stringify({candidates:[{finishReason:"STOP",content:{parts:[{text:JSON.stringify(answer)}]}}]}));};
  async function status(req,e=env(),f=mock,at=now){return (await handle(req,e,f,at)).status;}
  ok(await status(new Request("https://relay.test/v1/synthetic-soc",{method:"POST"}))===401&&calls===0,"unauthenticated rejected before provider");
  ok(await status(request(facts(),crypto.randomUUID(),{authorization:"Bearer "+"e".repeat(43)}))===401&&calls===0,"wrong token rejected without provider");
  ok(await status(request(),{...env(),TEST_TOKEN_EXPIRES_AT:new Date(now-1).toISOString()})===403,"expired token rejected");
  ok(await status(request(),{...env(),TEST_TOKEN_EXPIRES_AT:new Date(now+172800000).toISOString()})===403,"overlong token lifetime rejected");
  ok(await status(request(),{...env(),BUDGET:undefined})===503,"missing durable budget fails closed");
  ok(await status(request(),{...env(),POC_ENABLED:"false"})===503,"default disabled prevents model request");
  ok(await status(request(),{...env(),GEMINI_API_KEY:undefined})===503,"missing provider secret fails closed");
  const large=new Request("https://relay.test/v1/synthetic-soc",{method:"POST",headers:{authorization:"Bearer "+token,"content-type":"application/json","content-length":"1"},body:"x".repeat(5000)});
  ok(await status(large)===413,"stream byte limit survives false content length");
  ok(await status(request(facts(),crypto.randomUUID(),{"content-type":"text/plain"}))===415,"wrong content type rejected");
  ok(await status(request(facts(),crypto.randomUUID(),{"content-encoding":"gzip"}))===415,"compressed input rejected");
  ok(await status(request({...facts(),prompt:"arbitrary prompt"}))===400,"unknown fields and arbitrary prompt rejected");
  ok(await status(request({...facts(),source:"PLEOS_EMULATOR_SDK"}))===400,"real SDK source rejected in synthetic-only endpoint");
  ok(await status(request({...facts(),latest:19}))===400,"altered sample scalar rejected");
  ok(await status(request(facts(now+600000)))===400,"future synthetic time rejected");
  ok(await status(request(facts(),crypto.randomUUID(),{origin:"https://evil.test"}))===403,"browser origin rejected");
  ok(await status(request(facts(),crypto.randomUUID(),{},"/v1/synthetic-soc?key=x"))===400,"query and key URL rejected");
  ok(await status(new Request("https://relay.test/v1/synthetic-soc"))===405,"model route rejects GET");
  const health=await (await handle(new Request("https://relay.test/health"),{POC_ENABLED:"false"},mock,now)).json();
  ok(health.auth_required&&health.total_attempt_limit===5&&!health.enabled&&calls===0,"health never invokes provider or exposes secrets");
  const e=env();const pre=await (await handle(request(facts(),crypto.randomUUID(),{},"/v1/preflight"),{...e,POC_ENABLED:"false"},mock,now)).json();
  ok(pre.authenticated&&pre.binding_ok&&!pre.provider_calls&&pre.remaining_attempts===5&&!pre.enabled&&calls===0,"authenticated preflight verifies binding without spend");
  const id=crypto.randomUUID();const successful=await handle(request(facts(),id),e,mock,now);const good=await successful.json();
  ok(successful.status===200&&calls===1&&good.origin==="MODEL"&&good.model==="gemini-3.5-flash-lite"&&good.facts.source==="TEST_ONLY"&&good.request_id===id,"mocked provider result preserves model/facts/request contract; not real AI");
  ok(await status(request(facts(),id),e)===409&&calls===1,"replay UUID never calls provider twice");
  await status(request(),e);ok(await status(request(),e)===429&&calls===2,"global per-minute limit enforced");
  const total=env();const begin=calls;
  for(let i=0;i<5;i++)ok(await status(request(facts(now+Math.floor(i/2)*60000)),total,mock,now+Math.floor(i/2)*60000)===200,"mock total reservation "+(i+1));
  ok(await status(request(facts(now+180000)),total,mock,now+180000)===429&&calls-begin===5,"durable total limit does not reset each minute");
  const concurrent=env();const before=calls;const statuses=await Promise.all(Array.from({length:8},()=>status(request(),concurrent)));
  ok(statuses.filter(x=>x===200).length===2&&calls-before===2,"serialized transaction mock rejects concurrent overspend");
  const quota=await handle(request(),env(),async()=>{calls++;return new Response("private provider error",{status:429});},now);
  ok(quota.status===429&&(await quota.text()).includes("MODEL_QUOTA"),"provider quota handled without retry or raw error echo");
  const auth=await handle(request(),env(),async()=>new Response("MOCK_PROVIDER_SECRET_NOT_A_KEY",{status:403}),now);const authText=await auth.text();
  ok(auth.status===502&&authText.includes("PROVIDER_AUTH")&&!authText.includes("MOCK_PROVIDER_SECRET"),"provider auth failure redacts secret and error body");
  ok(await status(request(),env(),async()=>new Response("not JSON"))===502,"malformed model JSON rejected");
  const unsafe=async()=>new Response(JSON.stringify({candidates:[{finishReason:"STOP",content:{parts:[{text:JSON.stringify({explanation:"수리가 완료되어 안전합니다.",action:"CHECK_CHARGING_PLAN"})}]}}]}));
  ok(await status(request(),env(),unsafe)===502,"unsupported repair/safety claims rejected");
  const number=async()=>new Response(JSON.stringify({candidates:[{finishReason:"STOP",content:{parts:[{text:JSON.stringify({...answer,explanation:"충전량은 99%예요."})}]}}]}));
  ok(await status(request(),env(),number)===502,"invented model numeric prose rejected");
  ok(await status(request(),env(),async()=>{throw Error("network failure");})===502,"model network failure handled without retry");
  const broken={...env(),BUDGET:{idFromName:x=>x,get:()=>({fetch:async()=>{throw Error("storage unavailable");}})}};
  const old=calls;ok(await status(request(),broken)===503&&calls===old,"budget failure never falls back to an open proxy");
  const timeout=await handle(request(),env(),async(_url,options)=>new Promise((_,reject)=>options.signal.addEventListener("abort",()=>reject(Error("deadline")))),now);
  ok(timeout.status===504&&(await timeout.text()).includes("MODEL_TIMEOUT"),"real local deadline aborts mocked stalled provider");
  ok(!JSON.stringify(good).includes(token)&&!JSON.stringify(good).includes("MOCK_PROVIDER_SECRET"),"response never contains app/provider credentials");
  let config;
  const captured=await handle(request(),env(),async(url,options)=>{config=JSON.parse(options.body).generationConfig;return mock();},now);
  ok(captured.status===200&&!Object.hasOwn(config,"temperature")&&!Object.hasOwn(config,"topP")&&!Object.hasOwn(config,"topK")&&config.maxOutputTokens===512&&config.responseJsonSchema.required.length===2,"Gemini default sampling and bounded structured JSON request");
  async function errorOf(body,statusCode=200){const response=await handle(request(),env(),async()=>new Response(JSON.stringify(body),{status:statusCode}),now);return (await response.json()).error;}
  const badRequest=await errorOf({error:{status:"INVALID_ARGUMENT",message:"temperature not supported; PRIVATE_TOKEN",details:[{secret:"PRIVATE_DETAIL"}]}},400);
  ok(badRequest.code==="MODEL_REQUEST_REJECTED"&&badRequest.diagnostic.upstream_status===400&&badRequest.diagnostic.provider_status==="INVALID_ARGUMENT"&&badRequest.diagnostic.request_field==="TEMPERATURE"&&!JSON.stringify(badRequest).includes("PRIVATE"),"provider 400 safely identifies request field without error message");
  ok((await errorOf({error:{status:"NOT_FOUND",message:"private"}},404)).code==="MODEL_NOT_FOUND","missing model is distinct from request and safety errors");
  ok((await errorOf({error:{status:"INTERNAL"}},500)).code==="MODEL_HTTP_ERROR","provider 5xx is distinct from model output validation");
  const candidate=(finishReason,parts=[{text:JSON.stringify(answer)}],extra={})=>({candidates:[{finishReason,content:{parts},...extra}]});
  const truncated=await errorOf(candidate("MAX_TOKENS"));
  ok(truncated.code==="MODEL_OUTPUT_LIMIT"&&truncated.diagnostic.finish_reason==="MAX_TOKENS","MAX_TOKENS is not mislabeled safety filtering");
  ok((await errorOf(candidate("SAFETY"))).code==="MODEL_SAFETY_BLOCKED","candidate safety block is explicit");
  ok((await errorOf({promptFeedback:{blockReason:"SAFETY"}})).code==="MODEL_SAFETY_BLOCKED","prompt safety block is distinct from empty candidates");
  ok((await errorOf({candidates:[]})).code==="MODEL_NO_CANDIDATE","missing candidate is explicit");
  ok((await errorOf(candidate("RECITATION"))).code==="MODEL_FINISH_REJECTED","non-STOP termination never becomes MODEL");
  ok((await errorOf(candidate("STOP",[{text:"SECRET_THOUGHT",thought:true}]))).code==="MODEL_EMPTY_TEXT","thought-only output is not user answer");
  const withThought=await handle(request(),env(),async()=>new Response(JSON.stringify(candidate("STOP",[{text:"SECRET_THOUGHT",thought:true},{text:JSON.stringify(answer)}]))),now);
  const withThoughtText=await withThought.text();
  ok(withThought.status===200&&!withThoughtText.includes("SECRET_THOUGHT"),"thoughts excluded from JSON parsing and response");
  ok((await errorOf(candidate("STOP",[{text:"PRIVATE_MODEL_TEXT"}]))).code==="MODEL_JSON_INVALID","invalid answer JSON gets safe code only");
  ok((await errorOf(candidate("STOP",[{text:JSON.stringify({...answer,unknown:true})}]))).code==="MODEL_SCHEMA_INVALID","answer shape mismatch separated from JSON syntax");
  ok((await errorOf(candidate("STOP",[{text:JSON.stringify({...answer,explanation:"최근 값은 18%예요."})}]))).code==="MODEL_NUMERIC_PROSE","numeric prose remains blocked rather than weakening grounding");
  ok((await errorOf(candidate("STOP",[{text:JSON.stringify({...answer,explanation:"고장이 아니에요."})}]))).code==="MODEL_UNSAFE_PROSE","conservative negative diagnosis also blocked and identified honestly");
  ok((await errorOf(candidate("STOP",[{text:JSON.stringify({...answer,action:"REPAIR_NOW"})}]))).code==="MODEL_ACTION_INVALID","unsupported model action never reaches app");
  ok((await errorOf(candidate("STOP",[{text:JSON.stringify({...answer,explanation:"English only"})}]))).code==="MODEL_TEXT_INVALID","non-Korean answer fails closed");
  const unknown=await errorOf({error:{status:"PRIVATE_SECRET",message:"PRIVATE_TOKEN"}},400);
  ok(unknown.diagnostic.provider_status==="UNKNOWN"&&!JSON.stringify(unknown).includes("PRIVATE"),"unknown provider enums cannot leak secret strings");
  const noSafety=await handle(request(),env(),async()=>new Response(JSON.stringify({...candidate("STOP"),promptFeedback:{blockReason:"BLOCK_REASON_UNSPECIFIED"}})),now);
  ok(noSafety.status===200,"unspecified prompt block is not a real safety block");
  const afterFailure=env();
  await handle(request(),afterFailure,async()=>new Response("private",{status:400}),now);
  const afterPre=await(await handle(request(facts(),crypto.randomUUID(),{},"/v1/preflight"),afterFailure,mock,now)).json();
  ok(afterPre.remaining_attempts===4,"failed provider request still consumes existing lifetime budget");
  ok(good.diagnostic.release==="gemini-response-fix-v2"&&good.diagnostic.stage==="COMPLETE","release and phase distinguish newly deployed handler");
  return lines;
}
