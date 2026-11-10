import {reject} from './portable-common.ts';
/** Bound our fixed text-only provider envelope. No extra API call or token-count endpoint. */
export const MAX_PROVIDER_BYTES=8192,MAX_OUTPUT_TOKENS=1024;
export function boundedModelBody(value:any):string {
 reject(!value.tools&&Number.isInteger(value.generationConfig?.maxOutputTokens)&&value.generationConfig.maxOutputTokens>0&&value.generationConfig.maxOutputTokens<=MAX_OUTPUT_TOKENS,'MODEL_REQUEST_LIMIT',400);
 const body=JSON.stringify(value);reject(new TextEncoder().encode(body).length<=MAX_PROVIDER_BYTES,'MODEL_REQUEST_LIMIT',400);return body;
}
