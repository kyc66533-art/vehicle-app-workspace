package com.nextdrive.pleos.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Visible, input-allowed sessions only. Registration is not evidence of a received callback. */
class PleosSocObserver(private val context:Context,private val raw:ObservationStore,
    private val allowed:()->Boolean,private val received:(ObservationRef)->Unit,private val changed:()->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var vehicle:Vehicle?=null
    private var listener:EvBatteryStateOfChargeListener?=null
    private var generation=0
    private var session=""
    var running=false;private set
    var registered=false;private set
    var callbacks=0;private set
    var message="자동 확인이 꺼져 있어요.";private set
    private val source=if(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"))ObservationSource.PLEOS_EMULATOR_SDK else ObservationSource.PLEOS_VEHICLE_SDK
    fun start(){
        check(Looper.myLooper()==Looper.getMainLooper())
        if(running||!allowed())return
        stop("연결을 준비하고 있어요.")
        val run=generation;session=ObservationStore.sessionId();running=true;callbacks=0
        fun dispatch(f:()->Unit){handler.post{if(run==generation&&running&&allowed())f()}}
        fun outcome(value:Int?,path:String){
            val valid=value!=null&&value in 0..100
            val at=System.currentTimeMillis()
            val result=VehicleObservation("EV_SOC",if(valid)value.toString()else null,if(valid)"%"else null,if(valid)at else null,source,true,valid,session,
                if(valid)ObservationStatus.RECEIVED else ObservationStatus.INVALID,"Vehicle2.3.2 · SOC $path · ${if(valid)"공식 응답"else"범위 밖 응답: $value"}")
            try{val ref=raw.appendRef(result);received(ref);message=if(path=="CALLBACK")"SOC 변화 응답을 받았어요."else"최초 값을 받았어요. 변화 응답을 기다려요."}
            catch(e:Exception){message="받은 값을 기록하는 중 오류가 났어요. 저장 기록에서 다시 확인해 주세요."}
            changed()
        }
        fun fail(e:Exception){
            val status=when{e is SecurityException||e.message=="ERROR_SECURITY_PERMISSION"->ObservationStatus.PERMISSION_DENIED;e.message?.contains("NOT_SUPPORTED")==true->ObservationStatus.UNSUPPORTED;else->ObservationStatus.CONNECTION_FAILED}
            runCatching{raw.appendRef(VehicleObservation("EV_SOC",null,null,null,source,if(status==ObservationStatus.UNSUPPORTED)false else null,false,session,status,"SOC 구독 · ${e.javaClass.simpleName} · ${e.message.orEmpty().take(150)}")).also(received)}
            stop("자동 확인에 연결하지 못했어요. ${status.name}")
        }
        if(context.checkSelfPermission("pleos.car.permission.CAR_ENERGY")!=PackageManager.PERMISSION_GRANTED){fail(SecurityException("CAR_ENERGY"));return}
        message="자동 확인 연결을 준비하고 있어요.";changed()
        try{
            val sdk=Vehicle(context.applicationContext).also{vehicle=it};sdk.initialize()
            sdk.getEvBattery().checkEvBatteryStateOfChargeCapabilities({cap->dispatch{
                if(cap.accessType !in setOf(AccessType.READ,AccessType.READ_WRITE)){fail(IllegalStateException("ERROR_NOT_SUPPORTED_PROPERTY"));return@dispatch}
                try{
                    val callback=object:EvBatteryStateOfChargeListener {
                        override fun onEvBatteryStateOfChargeUpdated(value:Int?){dispatch{callbacks++;outcome(value,"CALLBACK")}}
                        override fun onFailed(e:Exception){dispatch{fail(e)}}
                    }
                    listener=callback;sdk.getEvBattery().registerEvBatteryStateOfCharge(callback);registered=true
                    message="등록됐어요. 실제 변화 응답을 기다려요.";changed()
                    sdk.getEvBattery().getEvBatteryStateOfCharge({v->dispatch{outcome(v,"GET")}}, {e->dispatch{fail(e)}})
                }catch(e:Exception){fail(e)}
            }},{e->dispatch{fail(e)}})
            handler.postDelayed({if(run==generation&&running&&!registered)fail(IllegalStateException("SOC Capability timeout"))},15000)
        }catch(e:Exception){fail(e)}
    }
    fun stop(reason:String="자동 확인을 멈췄어요. 이 시간 동안에는 관측 공백이 있어요."){
        generation++;handler.removeCallbacksAndMessages(null)
        val sdk=vehicle;val callback=listener
        if(callback!=null)runCatching{sdk?.getEvBattery()?.unregisterEvBatteryStateOfCharge(callback)}
        runCatching{sdk?.release()};vehicle=null;listener=null;registered=false;running=false;message=reason;changed()
    }
}
