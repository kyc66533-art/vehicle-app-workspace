package com.nextdrive.pleos.vehicle

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.listener.*

/** Public Safety 2.3.2 only; the existing SOC observer remains the single SOC subscriber. */
class PleosAttentionObserver(private val context:Context,private val raw:ObservationStore,private val allowed:()->Boolean,private val received:(ObservationRef)->Unit,private val changed:()->Unit={}){
    private val main=Handler(Looper.getMainLooper())
    private var generation=0
    private var sdk:Vehicle?=null
    private val cleanup=linkedMapOf<String,()->Unit>()
    var message="차량 안내 자동 확인을 준비하고 있어요.";private set
    var running=false;private set
    val registered=mutableSetOf<String>()
    val callbackCounts=mutableMapOf<String,Int>()
    private val source=if(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"))ObservationSource.PLEOS_EMULATOR_SDK else ObservationSource.PLEOS_VEHICLE_SDK
    fun start(){
        check(Looper.myLooper()==Looper.getMainLooper());if(running||!allowed())return
        generation++;val run=generation;running=true;val session=ObservationStore.sessionId();val answered=mutableSetOf<String>();val failed=mutableSetOf<String>()
        fun post(f:()->Unit){main.post{if(run==generation&&running&&allowed())runCatching{f()}.onFailure{stop();message="받은 차량 정보를 저장하지 못해 자동 확인을 멈췄어요.";changed()}}}
        fun fail(kind:String,e:Exception){post{
            if(!failed.add(kind))return@post
            cleanup.remove(kind)?.let{runCatching{it()}};registered.remove(kind)
            val status=when{e is SecurityException||e.message?.contains("SECURITY")==true->ObservationStatus.PERMISSION_DENIED;e.message?.contains("NOT_SUPPORTED")==true->ObservationStatus.UNSUPPORTED;e.message=="ATTENTION_TIMEOUT"->ObservationStatus.TIMEOUT;else->ObservationStatus.CONNECTION_FAILED}
            answered.add(kind)
            received(raw.appendRef(VehicleObservation(kind,null,null,null,source,if(status==ObservationStatus.UNSUPPORTED)false else null,false,session,status,"PLEOS_PUBLIC_SAFETY_2_3_2 · "+status.name)))
            if(failed.size==4)stop()
        }}
        fun value(kind:String,text:String?,callback:Boolean){post{
            if(kind in failed)return@post
            answered.add(kind);if(callback)callbackCounts[kind]=(callbackCounts[kind]?:0)+1
            val at=System.currentTimeMillis();val probe=VehicleObservation(kind,text,"state",at,source,true,true,session,ObservationStatus.RECEIVED,"PLEOS_PUBLIC_SAFETY_2_3_2 · "+if(callback)"CALLBACK"else"GET")
            val observation=if(DriveCheck.accepted(probe))probe else probe.copy(raw=null,unit=null,receivedAt=null,valid=false,status=ObservationStatus.INVALID)
            received(raw.appendRef(observation))
        }}
        val kinds=listOf("WASHER_LOW","POWER_DOWN","BRAKE_FLUID","ENGINE_OIL")
        if(context.checkSelfPermission("pleos.car.permission.CAR_SAFETY")!=PackageManager.PERMISSION_GRANTED){kinds.forEach{fail(it,SecurityException())};return}
        try{
            val vehicle=Vehicle(context.applicationContext);sdk=vehicle;vehicle.initialize();val safety=vehicle.getSafety()
            fun subscribe(kind:String,access:AccessType,work:()->Unit){post{if(access !in setOf(AccessType.READ,AccessType.READ_WRITE)){fail(kind,IllegalStateException("NOT_SUPPORTED"));return@post};try{work();registered.add(kind)}catch(e:Exception){fail(kind,e)}}}
            safety.checkLowWasherFluidWarningStateCapabilities({config->subscribe("WASHER_LOW",config.accessType){
                val listener=object:LowWasherFluidWaringListener{override fun onLowWasherFluidWaringUpdated(v:Boolean?)=value("WASHER_LOW",v?.toString(),true);override fun onFailed(e:Exception)=fail("WASHER_LOW",e)}
                cleanup["WASHER_LOW"]={ safety.unregisterLowWasherFluidWaringState(listener) };safety.registerLowWasherFluidWaringState(listener);safety.hasLowWasherFluidWarning({value("WASHER_LOW",it?.toString(),false)},{fail("WASHER_LOW",it)})
            }},{fail("WASHER_LOW",it)})
            safety.checkPowerDownWarningIndicatorCapabilities({config->subscribe("POWER_DOWN",config.accessType){
                val listener=object:PowerDownWarningIndicatorListener{override fun onPowerDownWarningIndicatorUpdated(v:Boolean?)=value("POWER_DOWN",v?.toString(),true);override fun onFailed(e:Exception)=fail("POWER_DOWN",e)}
                cleanup["POWER_DOWN"]={safety.unregisterPowerDownWarningIndicator(listener)};safety.registerPowerDownWarningIndicator(listener);safety.hasPowerDownWarningIndicator({value("POWER_DOWN",it?.toString(),false)},{fail("POWER_DOWN",it)})
            }},{fail("POWER_DOWN",it)})
            fun oil(kind:String,access:AccessType){subscribe(kind,access){
                val listener=object:OilLevelStateListener{override fun onOilLevelUpdated(v:OilLevelState?)=value(kind,v?.name,true);override fun onFailed(e:Exception)=fail(kind,e)}
                if(kind=="ENGINE_OIL"){cleanup[kind]={safety.unregisterEngineOilLevelState(listener)};safety.registerEngineOilLevelState(listener);safety.getEngineOilLevelState({value(kind,it?.name,false)},{fail(kind,it)})}
                else{cleanup[kind]={safety.unregisterBrakeFluidLevelState(listener)};safety.registerBrakeFluidLevelState(listener);safety.getBrakeFluidLevelState({value(kind,it?.name,false)},{fail(kind,it)})}
            }}
            safety.checkEngineOilLevelStateCapabilities({config,_->oil("ENGINE_OIL",config.accessType)},{fail("ENGINE_OIL",it)})
            safety.checkBrakeFluidLevelStateCapabilities({config,_->oil("BRAKE_FLUID",config.accessType)},{fail("BRAKE_FLUID",it)})
            main.postDelayed({if(run==generation&&running)kinds.filter{it !in answered}.forEach{fail(it,IllegalStateException("ATTENTION_TIMEOUT"))}},15000)
        }catch(e:Exception){kinds.forEach{fail(it,e)}}catch(_:LinkageError){kinds.forEach{fail(it,IllegalStateException("SDK_UNAVAILABLE"))}}
    }
    fun stop(){generation++;main.removeCallbacksAndMessages(null);running=false;cleanup.values.toList().forEach{runCatching{it()}};cleanup.clear();registered.clear();runCatching{sdk?.release()};sdk=null}
}
