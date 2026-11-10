import {ApiError,keys,reject,digest,bodyText,PORTABLE_CONTRACT} from './portable-common.ts';
import {MODEL} from './index.ts';
export function validateAnalysis(value:any){
 keys(value,['contract','signal','unit','source','synthetic','window','facts','quality','rule','limitations']);
 reject(value.contract===PORTABLE_CONTRACT&&value.signal==='EV_SOC'&&value.unit==='%'&&value.synthetic===false);
 reject(['PLEOS_EMULATOR_SDK','PLEOS_VEHICLE_SDK'].includes(value.source));
 keys(value.window,['duration_ms','latest_age_ms','sample_count','truncated']);
 const w=value.window;reject(Number.isSafeInteger(w.duration_ms)&&w.duration_ms>=0&&w.duration_ms<=366*86400000&&Number.isSafeInteger(w.latest_age_ms)&&w.latest_age_ms>=0&&w.latest_age_ms<600000&&Number.isInteger(w.sample_count)&&w.sample_count>=1&&w.sample_count<=2000&&typeof w.truncated==='boolean');
 keys(value.facts,['latest','minimum','maximum','mean_of_receipts','delta_last_comparable']);
 const f=value.facts;for(const k of ['latest','minimum','maximum','mean_of_receipts'])reject(typeof f[k]==='number'&&Number.isFinite(f[k])&&f[k]>=0&&f[k]<=100);
 reject(f.minimum<=f.latest&&f.latest<=f.maximum&&f.minimum<=f.mean_of_receipts&&f.mean_of_receipts<=f.maximum);
 reject(f.delta_last_comparable===null||typeof f.delta_last_comparable==='number'&&Number.isFinite(f.delta_last_comparable)&&Math.abs(f.delta_last_comparable)<=100&&w.sample_count>=2);
 if(w.sample_count===1)reject(f.minimum===f.latest&&f.maximum===f.latest&&f.mean_of_receipts===f.latest&&f.delta_last_comparable===null);
 keys(value.quality,['flag_count','segments','latest_valid']);reject(Number.isInteger(value.quality.flag_count)&&value.quality.flag_count>=0&&value.quality.flag_count<=100000&&Number.isInteger(value.quality.segments)&&value.quality.segments>=1&&value.quality.segments<=w.sample_count&&value.quality.latest_valid===true);
 reject(value.rule==='receipt-analysis-v1'&&value.limitations==='receipts_not_ecu;not_trip;not_time_average;no_diagnosis;no_safety_decision');return value;
}
export function actionFor(f:any){return f.window.sample_count<2?'INSUFFICIENT_DATA':'CHECK_VEHICLE_DISPLAY';}
/** Production only: one authenticated provider request. No retries, tools or client prompts. */
export async function explain(payload:string,operationId:string,secret:string,providerFetch=fetch){
 const facts=validateAnalysis(JSON.parse(payload)),action=actionFor(facts);
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);
 try{
  const upstream=await providerFetch('https://generativelanguage.googleapis.com/v1beta/models/'+MODEL+':generateContent',{
   method:'POST',redirect:"manual",signal:controller.signal,headers:{'content-type':'application/json','x-goog-api-key':secret},
   body:JSON.stringify({systemInstruction:{parts:[{text:'Explain only the supplied SOC receipt summary in one or two short polite Korean sentences. Emulator values are simulated. Do not include digits, inferred causes, diagnoses, repair claims or driving safety judgments. Do not use 고장, 수리, 안전, 운행 가능, 브레이크, 타이어, 교체, 정비 완료. Do not invent vehicle conditions. Suggest checking the actual vehicle display; insufficient samples mean a comparison cannot be made. Return the exact supplied action. No tools.'}]},contents:[{role:'user',parts:[{text:JSON.stringify({analysis:facts,required_action:action})}]}],generationConfig:{maxOutputTokens:512,responseMimeType:'application/json',responseJsonSchema:{type:'object',properties:{explanation:{type:'string'},action:{type:'string',enum:[action]}},required:['explanation','action'],additionalProperties:false}}})});
  reject(upstream.status<300||upstream.status>=400,'MODEL_REDIRECT_BLOCKED',502);
  if(!upstream.ok)throw new ApiError(upstream.status===429?429:502,upstream.status===429?'MODEL_QUOTA':upstream.status===401||upstream.status===403?'PROVIDER_AUTH':upstream.status===400?'MODEL_REQUEST_REJECTED':'MODEL_HTTP_'+upstream.status);
  let result:any;try{result=JSON.parse(await bodyText(upstream,16384));}catch{throw new ApiError(502,'MODEL_BODY_INVALID');}
  const c=result?.candidates?.[0];reject((!result?.promptFeedback?.blockReason||result.promptFeedback.blockReason==='BLOCK_REASON_UNSPECIFIED')&&c&&!c.safetyRatings?.some((x:any)=>x.blocked)&&c.finishReason==='STOP','MODEL_FINISH_REJECTED',502);
  const text=Array.isArray(c.content?.parts)?c.content.parts.filter((x:any)=>x.thought!==true&&typeof x.text==='string').map((x:any)=>x.text).join(''):'';
  let answer:any;try{answer=JSON.parse(text);keys(answer,['explanation','action']);}catch{throw new ApiError(502,'MODEL_JSON_INVALID');}
  reject(typeof answer.explanation==='string'&&answer.explanation.length>=1&&answer.explanation.length<=260&&/[가-힣]/.test(answer.explanation),'MODEL_TEXT_INVALID',502);
  reject(!/\d|고장|수리|안전|운행 가능|브레이크|타이어|교체|정비 완료/.test(answer.explanation),'MODEL_UNSAFE_PROSE',502);
  reject(answer.action===action,'MODEL_ACTION_INVALID',502);
  return {contract:PORTABLE_CONTRACT,operation_id:operationId,origin:'MODEL',model:MODEL,evidence_digest:await digest(payload),facts,explanation:answer.explanation,action};
 }catch(e){if(e instanceof ApiError)throw e;throw new ApiError(controller.signal.aborted?504:502,controller.signal.aborted?'MODEL_TIMEOUT':'MODEL_NETWORK');}
 finally{clearTimeout(timer);}
}
