package com.nextdrive.pleos

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nextdrive.pleos.integration.CaritdaActions
import com.nextdrive.pleos.safety.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.vehicle.ObservationStore
import com.nextdrive.pleos.ui.*
import org.json.JSONObject

/** External action opens an explicit local preview, not a data-export or mutation endpoint. */
class CaritdaActionActivity:Activity(){
    private lateinit var safety:CarSafetyGate
    override fun onCreate(saved:Bundle?){super.onCreate(saved);safety=CarSafetyGate(this){draw()};draw()}
    override fun onStart(){super.onStart();safety.start()}
    override fun onStop(){safety.stop();super.onStop()}
    private fun draw(){
        if(!::safety.isInitialized)return
        val dark=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val ui=CaritdaComponents(this,CaritdaPalette.of(dark));val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
        fun state(title:String,text:String){body.addView(ui.state(title,text))}
        state(if(packageName.endsWith(".validation"))"검증 전용 · TEST"else"CARITDA 음성 요청","기록 조회나 작성 준비를 도와드려요. AI 요청·읽음·저장·상태 변경을 자동으로 실행하지 않아요.")
        if(safety.state!=EditingState.ALLOWED){state("안전하게 정차한 뒤 확인해 주세요.","안전 상태를 확인할 때까지 기록을 표시하거나 변경하지 않아요.");setContentView(ScrollView(this).apply{addView(body)});return}
        try{
            val fn=intent.action;val raw=intent.getStringExtra("ACTION_PARAM");require(raw==null||raw.length<=1200)
            val args=raw?.let{JSONObject(it)}
            when(fn){
                CaritdaActions.QUERY->{val topic=CaritdaActions.topic(intent.getStringExtra("topic")?:args?.optString("topic"));body.addView(ui.action("요청한 내용 확인","action_confirm",ActionStyle.PRIMARY,true){
                    if(safety.state==EditingState.ALLOWED)try{SqliteIssueRepository(this).use{repo->ObservationStore(this).use{obs->state("확인한 기록",CaritdaActions.query(topic,CaritdaService(repo,{safety.state==EditingState.ALLOWED}),obs))}}}catch(_:Exception){state("기록을 확인하지 못했어요.","기존 기록은 그대로 유지돼요.")}
                })}
                CaritdaActions.DRAFT->{val text=CaritdaActions.draft(intent.getStringExtra("situation")?:args?.optString("situation"));state("작성할 내용 미리보기",text.ifBlank{"앱에서 증상을 직접 입력하거나 음성으로 남겨 주세요."});body.addView(ui.action("기록 작성으로 이동","action_draft",ActionStyle.PRIMARY,true){if(safety.state==EditingState.ALLOWED)startActivity(Intent(this,MainActivity::class.java).putExtra("voice_draft",text))})}
                else->state("지원하지 않는 요청이에요.","앱에서 차량 정보와 저장한 기록을 확인해 주세요.")
            }
            state("Gleo 연결 범위","공식 Schema 승인과 신뢰할 수 있는 Callback 수신 환경은 별도 확인이 필요해요. 이 화면의 확인 내용을 외부 앱에 자동 전송하지 않아요.")
        }catch(_:Exception){state("요청 내용을 확인해 주세요.","지원하지 않는 항목이거나 내용이 너무 길어요. 기록은 변경하지 않았어요.")}
        body.addView(ui.action("CARITDA 열기","action_open",ActionStyle.SECONDARY,true){startActivity(Intent(this,MainActivity::class.java));finish()})
        setContentView(ScrollView(this).apply{addView(body)})
    }
}
