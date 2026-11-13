package com.nextdrive.pleos.integration

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus

/** Opens the installed navigation's public SearchPlace screen. No POI data, location,
 * Firebase request, destination selection or route replacement is performed by CARITDA.
 * A dispatched screen request is never treated as search results or active guidance. */
class InspectionSearch(private val context:Context,private val factory:()->NavigationBackend,
    private val timeoutMs:Long=10000,private val allowed:()->Boolean){
    constructor(context:Context,allowed:()->Boolean):this(context,{PleosNavigationBackend(context)},10000,allowed)
    private val main=Handler(Looper.getMainLooper())
    private var backend:NavigationBackend?=null
    private var generation=0
    private var report:((String)->Unit)?=null
    private val trace=mutableListOf<String>()
    val events get()=trace.toList()
    var busy=false;private set
    var dispatched=false;private set

    fun request(report:(String)->Unit){
        if(busy||!allowed())return
        stop();trace.clear();dispatched=false;this.report=report
        fun emit(code:String){if(code !in trace){trace.add(code);this.report?.invoke(code)}}
        if(listOf("pleos.car.permission.NAVI_ROUTE","pleos.car.permission.NAVI_CUSTOM_MAP").any{
            context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED
        }){emit("NAVI_SEARCH_PERMISSION_DENIED");this.report=null;return}
        busy=true;val run=generation;var stateRequested=false;var stateHandled=false
        fun finish(code:String){val callback=this.report;stop();if(code !in trace){trace.add(code);callback?.invoke(code)}}
        fun post(action:()->Unit){main.post{
            if(run!=generation||!busy)return@post
            if(!allowed()){finish("NAVI_SEARCH_STOPPED");return@post}
            action()
        }}
        fun safely(action:()->Unit){try{action()}
            catch(_:SecurityException){finish("NAVI_SEARCH_PERMISSION_DENIED")}
            catch(_:UnsupportedOperationException){finish("NAVI_SEARCH_UNSUPPORTED")}
            catch(_:Exception){finish("NAVI_SEARCH_REQUEST_FAILED")}
        }
        emit("NAVI_SEARCH_CONNECTING")
        if(run!=generation||!busy)return
        try{
            val sdk=factory();backend=sdk
            sdk.initialize(object:NavigationBackend.Events{
                override fun connected()=post{
                    if(!stateRequested){stateRequested=true;emit("NAVI_SEARCH_CONNECTED");safely{sdk.checkState()}}
                }
                override fun state(routing:Boolean,terms:Boolean,mode:NaviMode)=post{
                    if(!stateRequested||stateHandled)return@post
                    stateHandled=true
                    if(routing||mode in setOf(NaviMode.ROUTE_GUIDING,NaviMode.SIMULATION_GUIDING)){
                        finish("NAVI_SEARCH_EXISTING_ROUTE");return@post
                    }
                    if(!terms){finish("NAVI_SEARCH_TERMS_REQUIRED");return@post}
                    if(mode==NaviMode.NOT_WORKING){finish("NAVI_SEARCH_APP_NOT_READY");return@post}
                    emit("NAVI_SEARCH_PREFLIGHT_READY")
                    if(run!=generation||!busy||!allowed()){if(run==generation&&busy)finish("NAVI_SEARCH_STOPPED");return@post}
                    safely{sdk.showRepairSearch();dispatched=true;emit("NAVI_SEARCH_REQUEST_SENT")}
                }
                // SearchPlace does not return a POI result set. Selection and route guidance
                // remain in the navigation UI; unrelated route events are not our success.
                override fun destinationChanged()=Unit
                override fun destination(latitude:Double,longitude:Double)=Unit
                override fun searchStarted()=Unit
                override fun searchCompleted()=Unit
                override fun navigation(status:NavigationStatus)=post{
                    if(!dispatched&&status==NavigationStatus.ROUTE_DRIVE)finish("NAVI_SEARCH_EXISTING_ROUTE")
                }
                override fun cancelled()=post{finish("NAVI_SEARCH_CANCELLED")}
                override fun failed(code:String)=post{finish(if(Regex("NAVI_[A-Z_0-9]{1,75}").matches(code))code else "NAVI_SEARCH_SERVICE_ERROR")}
            })
        }catch(_:SecurityException){finish("NAVI_SEARCH_PERMISSION_DENIED")}
        catch(_:Exception){finish("NAVI_SEARCH_SERVICE_ERROR")}
        catch(_:LinkageError){finish("NAVI_SEARCH_UNSUPPORTED")}
        if(busy)main.postDelayed({if(run==generation&&busy){
            // This only ends bounded error monitoring. It is not a screen/result acknowledgement.
            finish(if(dispatched)"NAVI_SEARCH_MONITOR_ENDED" else "NAVI_SEARCH_TIMEOUT")
        }},timeoutMs)
    }
    fun stop(){generation++;busy=false;report=null;main.removeCallbacksAndMessages(null);runCatching{backend?.release()};backend=null}
    companion object {
        fun message(code:String)=when(code){
            "NAVI_SEARCH_CONNECTING"->"내비게이션을 연결하고 있어요."
            "NAVI_SEARCH_CONNECTED","NAVI_SEARCH_PREFLIGHT_READY"->"현재 안내와 내비게이션 설정을 확인하고 있어요."
            "NAVI_SEARCH_REQUEST_SENT","NAVI_SEARCH_MONITOR_ENDED"->"내비게이션에 정비소 검색 화면을 요청했어요. 내비에서 장소를 선택해 주세요."
            "NAVI_SEARCH_EXISTING_ROUTE"->"진행 중인 경로가 있어 검색을 열지 않았어요. 내비게이션에서 현재 안내를 먼저 확인해 주세요."
            "NAVI_SEARCH_TERMS_REQUIRED"->"내비게이션에서 이용 약관을 먼저 확인해 주세요."
            "NAVI_SEARCH_APP_NOT_READY"->"내비게이션에서 초기 설정을 먼저 확인해 주세요."
            "NAVI_SEARCH_PERMISSION_DENIED"->"내비게이션 검색 화면을 여는 권한이 없어요."
            "NAVI_SEARCH_UNSUPPORTED"->"이 내비게이션은 정비소 검색 화면 연결을 지원하지 않아요."
            "NAVI_SEARCH_TIMEOUT"->"내비게이션 연결 확인 시간이 지났어요. 잠시 뒤 다시 시도해 주세요."
            "NAVI_SEARCH_STOPPED"->"화면이나 안전 상태가 바뀌어 검색 연결을 멈췄어요."
            "NAVI_SEARCH_CANCELLED"->"내비게이션에서 취소 안내를 받았어요."
            else->"내비게이션 검색 화면을 열지 못했어요. 내비게이션 상태를 확인해 주세요."
        }
    }
}
