// JavaScript-compatible TypeScript: the exact handler can also run in a Web API test engine.
// No dependencies, logs, client-provided prompts, URLs, model names or provider credentials.
export const CONTRACT = "caritda-gemini-synthetic-v1";
export const MODEL = "gemini-3.5-flash-lite";
const RELEASE = "gemini-response-fix-v2";
const MAX_BYTES = 4096;
const MAX_RESPONSE_BYTES = 16384;
const TOTAL_ATTEMPTS = 5;
const PER_MINUTE = 2;

class Rejected extends Error {
  status; code;
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
function json(value, status = 200) {
  return new Response(JSON.stringify(value), {status, headers: {
    "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff", "Referrer-Policy": "no-referrer"
  }});
}
async function limitedBody(message, limit) {
  const length = message.headers.get("content-length");
  if (length && (!/^\d+$/.test(length) || Number(length) > limit)) throw new Rejected(413, "BODY_LIMIT");
  if (!message.body) throw new Rejected(400, "BODY_REQUIRED");
  const reader = message.body.getReader(); const parts = []; let size = 0;
  try {
    while (true) { const {done, value} = await reader.read(); if (done) break;
      size += value.byteLength; if (size > limit) { await reader.cancel(); throw new Rejected(413, "BODY_LIMIT"); } parts.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
  return new TextDecoder("utf-8", {fatal: true}).decode(bytes);
}
async function digest(text) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
}
async function sameSecret(a, b) {
  const x = await digest(a), y = await digest(b); let different = 0;
  for (let i = 0; i < x.length; i++) different |= x[i] ^ y[i];
  return different === 0;
}
function keys(value, expected) {
  if (!value || typeof value !== "object" || Array.isArray(value) ||
      Object.keys(value).sort().join(",") !== [...expected].sort().join(",")) throw new Rejected(400, "INVALID_INPUT");
}
function validate(payload, now) {
  keys(payload, ["signal","source","unit","first_received_at","last_received_at","sample_count","latest","delta","minimum","maximum","mean_of_receipts","rule","quality","limitations","fixture","synthetic","criterion"]);
  if (payload.signal !== "EV_SOC" || payload.source !== "TEST_ONLY" || payload.unit !== "%" ||
      payload.fixture !== "SOC_80_60_18_V1" || payload.synthetic !== true || payload.sample_count !== 3 ||
      payload.latest !== 18 || payload.delta !== -42 || payload.minimum !== 18 || payload.maximum !== 80 ||
      Math.abs(payload.mean_of_receipts - 158 / 3) > 1e-9 || typeof payload.mean_of_receipts !== "number" ||
      payload.rule !== "receipt-analysis-v1" ||
      payload.limitations !== "app_receipts_not_ecu_time;session_not_trip;emulator_is_simulated;no_diagnosis_no_safety_decision;observation_mean_not_time_average") throw new Rejected(400, "INVALID_INPUT");
  keys(payload.quality, ["synthetic_fixture"]); keys(payload.criterion, ["operator","threshold"]);
  if (payload.quality.synthetic_fixture !== 3 || payload.criterion.operator !== "LT" || payload.criterion.threshold !== 20 ||
      !Number.isSafeInteger(payload.first_received_at) || !Number.isSafeInteger(payload.last_received_at) ||
      payload.last_received_at - payload.first_received_at !== 2000 || Math.abs(now - payload.last_received_at) > 300000) throw new Rejected(400, "INVALID_INPUT");
}
function budgetState(value) {
  const state=value || {total:0,minute:0,count:0,ids:[]};
  if (!Number.isInteger(state.total)||state.total<0||state.total>TOTAL_ATTEMPTS||!Number.isInteger(state.minute)||!Number.isInteger(state.count)||state.count<0||state.count>PER_MINUTE||!Array.isArray(state.ids)||state.ids.length!==state.total||new Set(state.ids).size!==state.total) throw new Error("Invalid budget storage");
  return state;
}

/** Fixed global ID plus durable transaction. An isolate-local Map/KV is not a billing cap. */
export class BudgetGate {
  ctx;
  constructor(ctx) { this.ctx = ctx; }
  async fetch(request) {
    // Portable cutover marker prevents the preserved v1 path from bypassing the shared ledger.
    if(request.method === "POST" && new URL(request.url).pathname === "/reserve" && await this.ctx.storage.get("portable:v2")) return json({code:"LEGACY_MODEL_DISABLED"},403);
    // This endpoint is available only through the internal DO binding, never a public route.
    if (request.method === "GET" && new URL(request.url).pathname === "/status") {
      const state = budgetState(await this.ctx.storage.get("budget"));
      return json({remaining:Math.max(0,TOTAL_ATTEMPTS-state.total)});
    }
    if (request.method !== "POST") return json({code:"METHOD"},405);
    let input;
    try { input = JSON.parse(await limitedBody(request,256)); } catch (_) { return json({code:"INVALID_INPUT"},400); }
    if (!/^[0-9a-f-]{36}$/i.test(input.request_id) || !Number.isSafeInteger(input.now)) return json({code:"INVALID_INPUT"},400);
    const result = await this.ctx.storage.transaction(async tx => {
      const state = budgetState(await tx.get("budget"));
      if (state.ids.includes(input.request_id.toLowerCase())) return {code:"DUPLICATE_REQUEST",status:409};
      if (state.total >= TOTAL_ATTEMPTS) return {code:"TOTAL_LIMIT",status:429};
      const minute = Math.floor(input.now / 60000); const count = state.minute === minute ? state.count : 0;
      if (count >= PER_MINUTE) return {code:"RATE_LIMIT",status:429};
      await tx.put("budget", {total:state.total+1, minute, count:count+1, ids:[...state.ids,input.request_id.toLowerCase()]});
      return {code:"RESERVED",status:200};
    });
    return json({code:result.code},result.status);
  }
}

export async function handle(request, env, providerFetch = fetch, now = Date.now()) {
  // Only locally chosen enums/status/counts. Never attach provider messages or output text.
  const diagnostic = Object.assign(Object.create(null),{release:RELEASE,stage:"REQUEST"});
  try {
    const url = new URL(request.url);
    if (url.search || url.hash) throw new Rejected(400,"INVALID_PATH");
    if (request.method === "GET" && url.pathname === "/health") return json({contract:CONTRACT,release:RELEASE, auth_required:true, synthetic_only:true, total_attempt_limit:TOTAL_ATTEMPTS, per_minute_limit:PER_MINUTE, enabled:env.POC_ENABLED === "true"});
    if (request.method !== "POST") throw new Rejected(405,"METHOD");
    if (!["/v1/synthetic-soc","/v1/preflight"].includes(url.pathname)) throw new Rejected(404,"NOT_FOUND");
    if (request.headers.has("origin")) throw new Rejected(403,"BROWSER_ORIGIN_REJECTED");
    const auth = request.headers.get("authorization") || "";
    if (!/^Bearer [A-Za-z0-9_-]{43}$/.test(auth)) throw new Rejected(401,"UNAUTHORIZED");
    if (!/^[A-Za-z0-9_-]{43}$/.test(env.TEST_APP_TOKEN || "") || !env.BUDGET) throw new Rejected(503,"SECURITY_NOT_CONFIGURED");
    if (!await sameSecret(auth.substring(7),env.TEST_APP_TOKEN)) throw new Rejected(401,"UNAUTHORIZED");
    const expiry = Date.parse(env.TEST_TOKEN_EXPIRES_AT || "");
    if (!Number.isFinite(expiry) || expiry <= now || expiry > now + 86400000) throw new Rejected(403,"TOKEN_EXPIRED");
    if (url.pathname !== "/v1/preflight" && (env.POC_ENABLED !== "true" || !env.GEMINI_API_KEY)) throw new Rejected(503,"POC_DISABLED");
    if (!/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(request.headers.get("content-type") || "")) throw new Rejected(415,"CONTENT_TYPE");
    if (request.headers.has("content-encoding")) throw new Rejected(415,"CONTENT_ENCODING");
    let envelope, facts;
    try { envelope = JSON.parse(await limitedBody(request,MAX_BYTES)); keys(envelope,["request_id","analysis_payload"]);
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(envelope.request_id) || typeof envelope.analysis_payload !== "string") throw new Rejected(400,"INVALID_INPUT");
      facts = JSON.parse(envelope.analysis_payload); validate(facts,now);
    } catch (e) { if(e instanceof Rejected) throw e; throw new Rejected(400,"INVALID_INPUT"); }
    const gate = env.BUDGET.get(env.BUDGET.idFromName("caritda-gemini-poc-global-v1"));
    diagnostic.stage="BUDGET";
    if (url.pathname === "/v1/preflight") {
      const status = await gate.fetch(new Request("https://budget.internal/status"));
      if (status.status !== 200) throw new Rejected(503,"BUDGET_UNAVAILABLE");
      const value = await status.json();
      return json({contract:CONTRACT,release:RELEASE,authenticated:true,synthetic_only:true,provider_calls:false,binding_ok:true,remaining_attempts:value.remaining,enabled:env.POC_ENABLED === "true" && !!env.GEMINI_API_KEY});
    }
    const reservation = await gate.fetch(new Request("https://budget.internal/reserve", {method:"POST",body:JSON.stringify({request_id:envelope.request_id,now})}));
    if (reservation.status !== 200) { const result = await reservation.json(); throw new Rejected(reservation.status,result.code); }
    const controller = new AbortController(); const timer = setTimeout(()=>controller.abort(),12000);
    let upstream, result;
    const started=Date.now();
    try {
      diagnostic.stage="PROVIDER_HTTP";
      upstream = await providerFetch("https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent", {
        method:"POST",redirect:"error",signal:controller.signal,
        headers:{"Content-Type":"application/json","x-goog-api-key":env.GEMINI_API_KEY},
        body:JSON.stringify({
          systemInstruction:{parts:[{text:"You explain a SYNTHETIC SOC test, not a real vehicle. Return short polite Korean, at most two sentences, without any digits, diagnoses, named components, repair claims, or driving-safety judgments. Do not use the words 고장, 수리, 안전, 브레이크, 타이어, 교체. Explain only that a user-configured charging threshold was crossed; suggest checking a charging plan. Action must be CHECK_CHARGING_PLAN. Never invent facts. This is not a diagnosis."}]},
          contents:[{role:"user",parts:[{text:JSON.stringify({synthetic:true,soc_sequence:[80,60,18],threshold_operator:"LT",threshold:20})}]}],
          // Gemini 3.x recommends default sampling; do not send temperature/topP/topK.
          // Bounded headroom for Korean JSON; no retries or fallback model calls.
          generationConfig:{maxOutputTokens:512,responseMimeType:"application/json",responseJsonSchema:{type:"object",properties:{explanation:{type:"string"},action:{type:"string",enum:["CHECK_CHARGING_PLAN"]}},required:["explanation","action"],additionalProperties:false}}
        })
      });
      diagnostic.upstream_status=upstream.status;
      if (upstream.status !== 200) {
        // Canonical Google status and a known request field only, never error.message/details.
        try {
          const error=JSON.parse(await limitedBody(upstream,MAX_RESPONSE_BYTES)).error;
          const allowed=["INVALID_ARGUMENT","UNAUTHENTICATED","PERMISSION_DENIED","NOT_FOUND","RESOURCE_EXHAUSTED","UNAVAILABLE","INTERNAL","DEADLINE_EXCEEDED","FAILED_PRECONDITION"];
          diagnostic.provider_status=allowed.includes(error?.status)?error.status:"UNKNOWN";
          const message=typeof error?.message==="string"?error.message:"";
          if (/temperature/i.test(message)) diagnostic.request_field="TEMPERATURE";
          else if (/response_?json_?schema/i.test(message)) diagnostic.request_field="RESPONSE_JSON_SCHEMA";
          else if (/max_?output_?tokens/i.test(message)) diagnostic.request_field="MAX_OUTPUT_TOKENS";
        } catch (_) { diagnostic.provider_status="UNAVAILABLE"; }
        if (upstream.status === 429) throw new Rejected(429,"MODEL_QUOTA");
        if (upstream.status === 401 || upstream.status === 403) throw new Rejected(502,"PROVIDER_AUTH");
        if (upstream.status === 400 || upstream.status === 422) throw new Rejected(502,"MODEL_REQUEST_REJECTED");
        if (upstream.status === 404) throw new Rejected(502,"MODEL_NOT_FOUND");
        throw new Rejected(502,"MODEL_HTTP_ERROR");
      }
      diagnostic.stage="PROVIDER_BODY";
      try { result = JSON.parse(await limitedBody(upstream,MAX_RESPONSE_BYTES)); } catch (_) { throw new Rejected(502,"MODEL_BODY_INVALID"); }
    } catch (e) { if(e instanceof Rejected) throw e; throw new Rejected(controller.signal.aborted?504:502,controller.signal.aborted?"MODEL_TIMEOUT":"MODEL_NETWORK"); }
    finally { clearTimeout(timer); diagnostic.provider_ms=Math.min(60000,Math.max(0,Date.now()-started)); }
    diagnostic.stage="PROVIDER_FINISH";
    const candidate = result?.candidates?.[0];
    const reasons=["STOP","MAX_TOKENS","SAFETY","RECITATION","LANGUAGE","OTHER","BLOCKLIST","PROHIBITED_CONTENT","SPII","MALFORMED_FUNCTION_CALL","IMAGE_SAFETY","FINISH_REASON_UNSPECIFIED"];
    diagnostic.finish_reason=reasons.includes(candidate?.finishReason)?candidate.finishReason:"UNKNOWN";
    const blocked=result?.promptFeedback?.blockReason;
    diagnostic.prompt_blocked=!!blocked&&blocked!=="BLOCK_REASON_UNSPECIFIED";
    if (diagnostic.prompt_blocked || candidate?.safetyRatings?.some(r=>r.blocked===true) || ["SAFETY","BLOCKLIST","PROHIBITED_CONTENT","SPII","IMAGE_SAFETY"].includes(candidate?.finishReason)) throw new Rejected(502,"MODEL_SAFETY_BLOCKED");
    if (!candidate) throw new Rejected(502,"MODEL_NO_CANDIDATE");
    if (candidate.finishReason === "MAX_TOKENS") throw new Rejected(502,"MODEL_OUTPUT_LIMIT");
    if (candidate.finishReason !== "STOP") throw new Rejected(502,"MODEL_FINISH_REJECTED");
    diagnostic.stage="PROVIDER_JSON";
    const parts=candidate.content?.parts;
    // Thought parts/signatures are not the answer. Never send them to the application.
    const answerText=Array.isArray(parts)?parts.filter(p=>p.thought!==true&&typeof p.text==="string").map(p=>p.text).join(""):"";
    if (!answerText.trim()) throw new Rejected(502,"MODEL_EMPTY_TEXT");
    let answer;
    try { answer = JSON.parse(answerText); } catch (_) { throw new Rejected(502,"MODEL_JSON_INVALID"); }
    try { keys(answer,["explanation","action"]); } catch (_) { throw new Rejected(502,"MODEL_SCHEMA_INVALID"); }
    diagnostic.stage="RELAY_GUARD";
    const text = answer.explanation;
    if (typeof text !== "string" || text.length<1 || text.length>260 || !/[가-힣]/.test(text)) throw new Rejected(502,"MODEL_TEXT_INVALID");
    if (/\d/.test(text)) throw new Rejected(502,"MODEL_NUMERIC_PROSE");
    if (/고장|수리|안전|운행 가능|브레이크|타이어|교체|정비 완료/.test(text)) throw new Rejected(502,"MODEL_UNSAFE_PROSE");
    if (answer.action !== "CHECK_CHARGING_PLAN") throw new Rejected(502,"MODEL_ACTION_INVALID");
    const hash = [...await digest(envelope.analysis_payload)].map(x=>x.toString(16).padStart(2,"0")).join("");
    diagnostic.stage="COMPLETE";
    return json({contract:CONTRACT,request_id:envelope.request_id,origin:"MODEL",model:MODEL,informational_only:true,evidence_digest:hash,facts,explanation:text,text,action:answer.action,diagnostic});
  } catch (e) {
    if (e instanceof Rejected) return json({error:{code:e.code,diagnostic}},e.status);
    return json({error:{code:"RELAY_UNAVAILABLE",diagnostic}},503);
  }
}

export default {fetch(request,env) { return handle(request,env); }};
