import {ApiError,bodyText,keys,reject,response} from './portable-common.ts';

/** Firebase authentication happens in portable-worker before entering this handler.
 * The Kakao credential stays server-side and is never returned to the Android app.
 * This lookup does not touch Gemini, the AI budget ledger, or the issue journal. */
export async function nearbyRepair(body:any,env:any,providerFetch:typeof fetch=fetch):Promise<Response> {
  keys(body,['latitude','longitude']);
  const lat=body.latitude,lon=body.longitude;
  reject(typeof lat==='number'&&typeof lon==='number'&&Number.isFinite(lat)&&Number.isFinite(lon)&&lat>=33&&lat<=39.5&&lon>=124&&lon<=132,'LOCATION_OUT_OF_RANGE');
  reject(env.PLACES_ENABLED==='true'&&typeof env.KAKAO_LOCAL_REST_KEY==='string'&&env.KAKAO_LOCAL_REST_KEY.length>=12,'PLACES_NOT_CONFIGURED',503);
  const found=new Map<string,any>();
  const deadline=Date.now()+6000;
  for(const [keyword,category] of [['현대 블루핸즈','BLUEHANDS_CANDIDATE'],['자동차 정비소','GENERAL']] as const){
    if(found.size>=3)break;
    const url=new URL('https://dapi.kakao.com/v2/local/search/keyword.json');
    url.searchParams.set('query',keyword);
    url.searchParams.set('x',lon.toFixed(3));url.searchParams.set('y',lat.toFixed(3));
    url.searchParams.set('radius','20000');url.searchParams.set('size','15');url.searchParams.set('sort','distance');
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),Math.max(1,deadline-Date.now()));
    try{
      const result=await providerFetch(url.toString(),{method:'GET',redirect:'manual',signal:controller.signal,
        headers:{Authorization:'KakaoAK '+env.KAKAO_LOCAL_REST_KEY,Accept:'application/json'}});
      if(result.status!==200){if(found.size)break;throw new ApiError(502,'PLACES_PROVIDER_UNAVAILABLE');}
      let raw:string;
      try{raw=await bodyText(result,65536);}catch(e){
        if(e instanceof ApiError)throw new ApiError(502,e.code==='BODY_LIMIT'?'PLACES_RESPONSE_LIMIT':'PLACES_RESPONSE_INVALID');
        throw e;
      }
      let parsed:any;try{parsed=JSON.parse(raw);}catch{throw new ApiError(502,'PLACES_RESPONSE_INVALID');}
      reject(Array.isArray(parsed?.documents),'PLACES_RESPONSE_INVALID',502);
      for(const p of parsed.documents.slice(0,15)){
        if(p?.distance===null||p?.distance===undefined||typeof p.distance==='string'&&!p.distance.trim())continue;
        const name=p?.place_name,latitude=Number(p?.y),longitude=Number(p?.x),distance=Number(p?.distance);
        if(typeof name!=='string'||name.trim().length<2||name.length>100||
            !Number.isFinite(latitude)||!Number.isFinite(longitude)||latitude<33||latitude>39.5||longitude<124||longitude>132||
            !Number.isFinite(distance)||distance<0||distance>20000)continue;
        if(category==='BLUEHANDS_CANDIDATE'&&!/블루핸즈/.test(name))continue;
        const id=String(p?.id||'');
        if(!/^\d{1,24}$/.test(id)||found.has(id))continue;
        const address=(typeof p?.road_address_name==='string'&&p.road_address_name.trim()||
          typeof p?.address_name==='string'&&p.address_name.trim()||'주소 정보 없음').slice(0,150);
        const phone=typeof p?.phone==='string'?p.phone.slice(0,35):'';
        found.set(id,{id,name:name.trim(),address,phone,latitude,longitude,distance_m:Math.round(distance),category});
      }
    }catch(e){if(found.size)break;if(e instanceof ApiError)throw e;throw new ApiError(e instanceof Error&&e.name==='AbortError'?504:502,e instanceof Error&&e.name==='AbortError'?'PLACES_TIMEOUT':'PLACES_NETWORK_UNAVAILABLE');}
    finally{clearTimeout(timer);}
  }
  const places=[...found.values()].sort((a,b)=>
    Number(b.category==='BLUEHANDS_CANDIDATE')-Number(a.category==='BLUEHANDS_CANDIDATE')||
    a.distance_m-b.distance_m).slice(0,3);
  return response({source:'KAKAO_LOCAL',distance:'straight_line_m',verified_service_network:false,
    location_precision:'approx_100m',places});
}
