package com.nextdrive.pleos.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.NaviMode

/** Explicit destination request. Never cancels an existing route or claims a service reservation. */
class InspectionNavigation(private val context:Context,private val factory:()->NavigationBackend,
    private val timeoutMs:Long=30000,private val allowed:()->Boolean){
    constructor(context:Context,allowed:()->Boolean):this(context,{PleosNavigationBackend(context)},30000,allowed)
    data class Event(val code:String,val at:Long)
    private val main=Handler(Looper.getMainLooper());private var sdk:NavigationBackend?=null;private var generation=0
    private var report:((String)->Unit)?=null
    private val trace=mutableListOf<Event>()
    val events get()=trace.toList()
    var busy=false;private set
    var destinationVerified=false;private set
    var guidanceConfirmed=false;private set
    fun request(latitude:Double,longitude:Double,name:String,report:(String)->Unit){
        val target=NavigationTarget(latitude,longitude,name);require(target.valid())
        if(busy||!allowed())return
        stop();trace.clear();destinationVerified=false;guidanceConfirmed=false;this.report=report
        fun emit(code:String){if(trace.none{it.code==code}){trace.add(Event(code,System.currentTimeMillis()));report(code)}}
        if(listOf("pleos.car.permission.NAVI_ROUTE","pleos.car.permission.NAVI_ROUTE_SEARCH").any{context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}){emit("NAVI_PERMISSION_DENIED");return}
        busy=true;val run=generation;var submitted=false;var checkingDestination=false;var routeDrive=false
        var searchComplete=false;var routeMode:NaviMode?=null;var checkingState=false;var stateQueries=0
        fun post(action:()->Unit){main.post{if(run==generation&&busy&&allowed())action()}}
        fun finish(code:String){stop();emit(code)}
        fun safely(action:()->Unit){try{action()}catch(_:SecurityException){finish("NAVI_PERMISSION_DENIED")}catch(_:Exception){finish("NAVI_REQUEST_FAILED")}}
        fun verifyDestination(){if(submitted&&!checkingDestination&&!destinationVerified){checkingDestination=true;safely{sdk?.checkDestination()}}}
        fun verifyState(){if(submitted&&!checkingState&&stateQueries<2){checkingState=true;stateQueries++;safely{sdk?.checkState()}}}
        fun complete(){if(destinationVerified&&routeDrive&&searchComplete&&routeMode==NaviMode.ROUTE_GUIDING){guidanceConfirmed=true;finish("NAVI_GUIDANCE_ACTIVE")}}
        emit("NAVI_CONNECTING")
        try{
            val backend=factory();sdk=backend
            backend.initialize(object:NavigationBackend.Events{
                override fun connected()=post{emit("NAVI_CONNECTED");safely{backend.checkState()}}
                override fun state(routing:Boolean,terms:Boolean,mode:NaviMode)=post{
                    if(!submitted){
                        if(routing||mode in setOf(NaviMode.ROUTE_GUIDING,NaviMode.SIMULATION_GUIDING)){finish("NAVI_EXISTING_ROUTE");return@post}
                        if(!terms){finish("NAVI_TERMS_REQUIRED");return@post}
                        if(mode==NaviMode.NOT_WORKING){finish("NAVI_APP_NOT_READY");return@post}
                        emit("NAVI_PREFLIGHT_READY")
                        safely{submitted=true;backend.request(target);emit("NAVI_REQUEST_SENT")}
                    }else{checkingState=false;routeMode=mode;emit("NAVI_MODE_"+mode.name);complete()}
                }
                override fun destinationChanged()=post{if(submitted){emit("NAVI_DESTINATION_CHANGED");verifyDestination()}}
                override fun destination(latitude:Double,longitude:Double)=post{
                    if(submitted&&checkingDestination){checkingDestination=false
                        if(!target.matches(latitude,longitude)){finish("NAVI_DESTINATION_MISMATCH");return@post}
                        destinationVerified=true;emit("NAVI_DESTINATION_CONFIRMED");complete()
                    }
                }
                override fun searchStarted()=post{if(submitted){emit("NAVI_ROUTE_SEARCH_STARTED");verifyDestination()}}
                override fun searchCompleted()=post{if(submitted){searchComplete=true;emit("NAVI_ROUTING_COMPLETED");verifyDestination();verifyState();complete()}}
                override fun navigation(status:NavigationStatus)=post{
                    if(!submitted&&status==NavigationStatus.ROUTE_DRIVE){finish("NAVI_EXISTING_ROUTE");return@post}
                    if(submitted){emit("NAVI_STATUS_"+status.name);routeDrive=status==NavigationStatus.ROUTE_DRIVE;if(routeDrive){verifyDestination();verifyState();complete()}}
                }
                override fun cancelled()=post{if(submitted)finish("NAVI_ROUTE_CANCELLED")}
                override fun failed(code:String)=post{finish(code.takeIf{Regex("[A-Z_0-9]{1,80}").matches(it)}?:"NAVI_SERVICE_ERROR")}
            })
        }catch(_:SecurityException){finish("NAVI_PERMISSION_DENIED")}catch(_:Exception){finish("NAVI_SERVICE_ERROR")}catch(_:LinkageError){finish("NAVI_SDK_UNAVAILABLE")}
        main.postDelayed({if(run==generation&&busy){finish("NAVI_TIMEOUT")}},timeoutMs)
    }
    fun cancel(){val callback=report;stop();trace.add(Event("NAVI_MONITOR_CANCELLED",System.currentTimeMillis()));if(allowed())callback?.invoke("NAVI_MONITOR_CANCELLED")}
    fun stop(){generation++;busy=false;report=null;main.removeCallbacksAndMessages(null);runCatching{sdk?.release()};sdk=null}
    companion object {
        fun message(code:String)=when(code){
            "NAVI_CONNECTING"->"내비게이션 연결을 준비하고 있어요."
            "NAVI_CONNECTED"->"내비게이션에 연결됐어요. 기존 경로와 약관 상태를 확인해요."
            "NAVI_PREFLIGHT_READY"->"진행 중인 경로가 없고 약관 동의가 확인됐어요."
            "NAVI_REQUEST_SENT"->"목적지를 전달했어요. 수신 결과를 기다려요."
            "NAVI_DESTINATION_CHANGED"->"내비게이션에서 목적지 변경 이벤트를 받았어요. 좌표를 확인해요."
            "NAVI_DESTINATION_CONFIRMED"->"내비게이션이 요청한 목적지 좌표를 돌려줬어요."
            "NAVI_ROUTE_SEARCH_STARTED"->"경로 탐색이 시작됐어요. 안내 시작 여부는 아직 확인 중이에요."
            "NAVI_ROUTING_COMPLETED"->"경로 탐색이 끝났어요. 안내 시작 여부는 별도로 확인해요."
            "NAVI_GUIDANCE_ACTIVE"->"목적지 일치와 실제 경로 안내 상태를 확인했어요. 내비게이션에서 경로를 확인해 주세요."
            "NAVI_STATUS_ROUTE_DRIVE"->"경로 안내 상태 이벤트를 받았어요. 요청한 목적지와 일치하는지 확인해요."
            "NAVI_STATUS_FREE_DRIVE"->"지도 표시 상태예요. 경로 안내 시작은 확인되지 않았어요."
            "NAVI_STATUS_INITIALIZING"->"내비게이션이 안내를 준비하고 있어요."
            "NAVI_STATUS_INACTIVE"->"내비게이션 안내가 활성화되지 않았어요."
            "NAVI_EXISTING_ROUTE"->"진행 중인 경로가 있어 새 목적지를 보내지 않았어요."
            "NAVI_TERMS_REQUIRED"->"내비게이션에서 이용 약관을 먼저 확인해 주세요."
            "NAVI_APP_NOT_READY"->"내비게이션 앱이 아직 준비되지 않았어요. 내비게이션에서 초기 설정을 확인해 주세요."
            "NAVI_MODE_ROUTE_GUIDING"->"조회한 내비게이션 모드가 경로 안내예요. 다른 완료 이벤트와 함께 확인해요."
            "NAVI_MODE_SAFE_GUIDING"->"조회한 내비게이션 모드는 경로 안내 전 상태예요."
            "NAVI_MODE_SIMULATION_GUIDING"->"모의 안내 모드예요. 실제 경로 안내 성공으로 표시하지 않아요."
            "NAVI_MODE_NOT_WORKING"->"조회한 내비게이션 모드가 준비되지 않은 상태예요."
            "NAVI_DESTINATION_MISMATCH"->"돌려받은 목적지가 요청과 달라 안내 시작으로 확인하지 않았어요. 내비게이션을 확인해 주세요."
            "NAVI_ROUTE_CANCELLED"->"내비게이션에서 경로 취소 이벤트를 받았어요."
            "NAVI_MONITOR_CANCELLED"->"이번 요청 결과 확인을 멈췄어요. 내비게이션의 경로는 그대로예요."
            "NAVI_MONITOR_STOPPED"->"앱이 보이지 않거나 안전 상태가 바뀌어 결과 확인을 멈췄어요. 내비게이션에서 현재 경로를 확인해 주세요."
            "NAVI_TIMEOUT"->"내비게이션 결과 확인 시간이 지났어요. 아래 수신 이력과 내비게이션을 확인해 주세요."
            "NAVI_PERMISSION_DENIED"->"내비게이션 권한이 허용되지 않았어요."
            "NAVI_CONNECTION_FAILED","NAVI_SDK_UNAVAILABLE"->"내비게이션 서비스에 연결하지 못했어요."
            else->"길찾기를 완료하지 못했어요. 확인 코드: $code"
        }
    }
}
