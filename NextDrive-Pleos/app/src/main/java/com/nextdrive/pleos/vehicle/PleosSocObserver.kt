package com.nextdrive.pleos.vehicle

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Visible, input-allowed sessions only. Registration is not evidence of a received callback. */
class PleosSocObserver(private val context:Context,private val raw:ObservationStore,
    private val allowed:()->Boolean,private val received:(ObservationRef)->Unit,private val changed:()->Unit,
    private val factory:()->SocBackend={PleosSocBackend(context)}) {
    private val handler=Handler(Looper.getMainLooper())
    private var backend:SocBackend?=null
    private var generation=0
    private var session=""
    var running=false;private set
    var registered=false;private set
    var callbacks=0;private set
    var receivedCount=0;private set
    var lastReceivedAt:Long?=null;private set
    var message="자동 확인이 꺼져 있어요.";private set
    private val source=if(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"))ObservationSource.PLEOS_EMULATOR_SDK else ObservationSource.PLEOS_VEHICLE_SDK
    fun start(){
        check(Looper.myLooper()==Looper.getMainLooper())
        if(running||!allowed())return
        stop("연결을 준비하고 있어요.")
        val run=generation;session=ObservationStore.sessionId();running=true;callbacks=0;receivedCount=0;lastReceivedAt=null
        fun dispatch(f:()->Unit){handler.post{if(run==generation&&running&&allowed())f()}}
        fun outcome(value:Int?,path:String){
            val valid=value!=null&&value in 0..100
            val at=System.currentTimeMillis()
            val result=VehicleObservation("EV_SOC",if(valid)value.toString()else null,if(valid)"%"else null,if(valid)at else null,source,true,valid,session,
                if(valid)ObservationStatus.RECEIVED else ObservationStatus.INVALID,"${backend?.provenance?:"SOC_BACKEND_UNKNOWN"} · SOC $path · ${if(valid)"수신"else"범위 밖 응답: $value"}")
            try{
                val ref=raw.appendRef(result)
                if(valid){receivedCount++;lastReceivedAt=at}
                try{received(ref);message=if(!valid)"유효한 SOC 값을 받지 못했어요. 이전 안내는 그대로예요."else if(path=="CALLBACK")"SOC 변화 응답을 받았어요."else"최초 값을 받았어요. 변화 응답을 기다려요."}
                catch(_:Exception){message="원관측은 저장됐지만 안내 정리를 다시 시도해야 해요."}
            }catch(_:Exception){message="받은 값을 기록하는 중 오류가 났어요. 저장 기록에서 다시 확인해 주세요."}
            changed()
        }
        fun fail(e:Exception){
            val status=when{e is SecurityException||e.message=="ERROR_SECURITY_PERMISSION"->ObservationStatus.PERMISSION_DENIED;e.message?.contains("NOT_SUPPORTED")==true->ObservationStatus.UNSUPPORTED;e.message=="SOC_INITIAL_TIMEOUT"->ObservationStatus.TIMEOUT;e.message=="SOC_SDK_UNAVAILABLE"->ObservationStatus.SDK_UNAVAILABLE;else->ObservationStatus.CONNECTION_FAILED}
            runCatching{raw.appendRef(VehicleObservation("EV_SOC",null,null,null,source,if(status==ObservationStatus.UNSUPPORTED)false else null,false,session,status,"SOC 구독 · "+status.name)).also(received)}
            stop("자동 확인에 연결하지 못했어요. ${status.name}")
        }
        if(context.checkSelfPermission("pleos.car.permission.CAR_ENERGY")!=PackageManager.PERMISSION_GRANTED){fail(SecurityException("CAR_ENERGY"));return}
        message="자동 확인 연결을 준비하고 있어요.";changed()
        try{
            val sdk=factory().also{backend=it};sdk.initialize()
            sdk.capability({supported->dispatch{
                if(!supported){fail(IllegalStateException("ERROR_NOT_SUPPORTED_PROPERTY"));return@dispatch}
                try{
                    sdk.subscribe({value->dispatch{callbacks++;outcome(value,"CALLBACK")}},{e->dispatch{fail(e)}});registered=true
                    message="등록됐어요. 실제 변화 응답을 기다려요.";changed()
                    sdk.initial({v->dispatch{outcome(v,"GET")}},{e->dispatch{fail(e)}})
                }catch(e:Exception){fail(e)}
            }},{e->dispatch{fail(e)}})
            handler.postDelayed({if(run==generation&&running&&receivedCount==0)fail(IllegalStateException("SOC_INITIAL_TIMEOUT"))},15000)
        }catch(e:Exception){fail(e)}catch(_:LinkageError){fail(IllegalStateException("SOC_SDK_UNAVAILABLE"))}
    }
    fun stop(reason:String="자동 확인을 멈췄어요. 이 시간 동안에는 관측 공백이 있어요."){
        generation++;handler.removeCallbacksAndMessages(null)
        runCatching{backend?.release()};backend=null;registered=false;running=false;message=reason;changed()
    }
}
