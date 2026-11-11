package com.nextdrive.pleos

import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.*
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.ui.*

/** Explicit permission/voice trial only. No default DB writes or automatic model requests. */
class IntegrationDiagnosticActivity:Activity(){
    private var visible=false;private lateinit var gate:CarSafetyGate;private lateinit var body:LinearLayout;private lateinit var status:TextView
    private var appMicrophone=false
    private val speech by lazy{SpeechInput(this,factory={if(appMicrophone)PleosPcmSpeechBackend(this)else PleosSpeechBackend(this)},allowed={allowed()})}
    private val spoken by lazy{SpeechOutput(this){allowed()}}
    private val location by lazy{OneShotLocation(this){allowed()}}
    private val micProbe by lazy{MicrophoneProbe(this){allowed()}}
    private var continuation:(()->Unit)?=null
    private fun allowed()=visible&&::gate.isInitialized&&gate.state==EditingState.ALLOWED
    override fun onCreate(saved:Bundle?){super.onCreate(saved)
        appMicrophone=saved?.getBoolean("app_microphone")?:intent.getBooleanExtra("use_app_mic",false)
        val dark=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val ui=CaritdaComponents(this,CaritdaPalette.of(dark));body=ui.column().apply{setPadding(24,24,24,24);setBackgroundColor(ui.colors.background)};status=ui.text("정차 상태를 확인하고 있어요.",24)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ui.colors.background))
        body.addView(ui.text("검증 전용 · TEST · 보조 SDK 실험",28,true));body.addView(status)
        body.addView(ui.text("각 버튼을 직접 눌렀을 때만 실행해요. 음성 결과·위치는 이 화면에만 표시하고 사용자 DB·Gemini에는 보내지 않아요.",22))
        val route=ui.action(if(appMicrophone)"입력 경로: 기기 마이크 → Pleos PCM"else"입력 경로: Pleos 차량 마이크","integration_stt_route",ActionStyle.QUIET,true){}
        route.setOnClickListener{if(allowed()&&!speech.busy&&!micProbe.busy){speech.stop();appMicrophone=!appMicrophone;route.text=if(appMicrophone)"입력 경로: 기기 마이크 → Pleos PCM"else"입력 경로: Pleos 차량 마이크";speech.observer(speech.state)}};body.addView(route)
        body.addView(SpeechControls(this,ui,speech,{allowed()&&!micProbe.busy},{
            if(allowed())permission(android.Manifest.permission.RECORD_AUDIO){spoken.stop();speech.listen({status.text=it},{text->
                status.text="인식 결과 (미저장): "+text+"\n이번 실행에서 입력 완료 "+speech.completions+"회"
                getSharedPreferences("integration_diagnostics",MODE_PRIVATE).edit().putBoolean("stt_final_received",true).putString("stt_source",speech.source).putInt("stt_completed_sessions",speech.completions).putInt("stt_requests",speech.requests).apply()
            })}
        }))
        body.addView(ui.text("DriveCheck 보조 실험 · 마이크 크기는 보정되지 않은 디지털 값이에요. 차량 소음·진동이나 고장 원인을 판정하지 않아요.",22))
        body.addView(ui.action("동의하고 마이크 크기 측정 · 최대 5초","integration_mic_probe",ActionStyle.QUIET,true){
            if(allowed()&&!speech.busy&&!micProbe.busy)permission(android.Manifest.permission.RECORD_AUDIO){
                speech.stop();spoken.stop();status.text="최대 5초 동안 마이크 입력 크기를 측정해요. 직접 중단할 수 있으며 녹음 파일은 남기지 않아요."
                micProbe.start{code->
                    status.text=when(code){"MIC_INPUT_PRESENT"->"PCM 데이터를 받았어요. 음성 인식 성공을 뜻하지는 않아요.";"MIC_INPUT_ZERO"->"PCM 샘플값이 모두 0이에요.";else->"마이크 입력을 확인하지 못했어요."}+"\n확인 코드: "+code+"\n수신 "+(micProbe.lastSamples*2)+"바이트 · read "+micProbe.lastMinRead+"~"+micProbe.lastMaxRead+"샘플\n입력 크기 "+micProbe.lastRms?.let{String.format(java.util.Locale.ROOT,"%.6f",it)}+" · 최댓값 "+micProbe.lastPeak+"/32768\nAndroid 무음 처리: "+when(micProbe.lastSilenced){true->"예";false->"아니요";null->"확인 불가"}
                    getSharedPreferences("integration_diagnostics",MODE_PRIVATE).edit().putString("mic_probe_code",code).apply();speech.observer(speech.state)
                };speech.observer(speech.state)
            }
        })
        body.addView(ui.action("마이크 측정 중단","integration_mic_stop",ActionStyle.QUIET,true){if(micProbe.busy){micProbe.stop();status.text="마이크 측정을 중단했어요. 이번 측정 결과를 사용하지 않아요.";speech.observer(speech.state)}})
        body.addView(ui.action("한국어 음성 출력 시험","integration_tts",ActionStyle.SECONDARY,true){if(allowed()&&!speech.busy&&!micProbe.busy)spoken.speak("카잇다 음성 안내 시험입니다. 실제로 소리가 들리는지 확인해 주세요."){status.text=it}})
        body.addView(ui.action("기기 위치 1회 확인 · 위치 허용 필요","integration_location",ActionStyle.SECONDARY,true){if(allowed())permission("pleos.car.permission.FUSED_LOCATION"){location.read{value,code->status.text=if(value==null)code+" · 위치를 받지 못했어요."else"위도 "+value.latitude+" · 경도 "+value.longitude+"\n확인 시각 "+VehicleUi.shortTime(value.measuredAt)+" · 마지막 위치이며 증상 당시 위치가 아니에요.";getSharedPreferences("integration_diagnostics",MODE_PRIVATE).edit().putString("location_code",code).putBoolean("location_valid",value!=null).apply()}}})
        body.addView(ui.action("시험 종료","integration_close",ActionStyle.QUIET,true){finish()})
        setContentView(ScrollView(this).apply{setBackgroundColor(ui.colors.background);addView(body)})
        gate=CarSafetyGate(this){if(it!=EditingState.ALLOWED){speech.stop();spoken.stop();location.stop();micProbe.stop();status.text="안전하게 정차한 뒤 시험해 주세요."}else{speech.observer(speech.state);status.text="정차 상태예요. 필요한 시험만 직접 선택해 주세요."}}
    }
    private fun permission(name:String,action:()->Unit){if(checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED)action()else{continuation=action;requestPermissions(arrayOf(name),702)}}
    override fun onRequestPermissionsResult(code:Int,names:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,names,results);if(code==702){val action=continuation;continuation=null;if(results.isNotEmpty()&&results.all{it==PackageManager.PERMISSION_GRANTED}&&allowed())action?.invoke()else status.text="권한을 허용하지 않았어요. 수집하지 않아요."}}
    override fun onStart(){super.onStart();visible=true;gate.start()}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("app_microphone",appMicrophone);super.onSaveInstanceState(out)}
    override fun onStop(){visible=false;continuation=null;speech.stop();spoken.stop();location.stop();micProbe.stop();gate.stop();super.onStop()}
}
