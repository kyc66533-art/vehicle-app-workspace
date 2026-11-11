package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import com.nextdrive.pleos.integration.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import java.util.UUID

/** Real MainActivity/SQLite, synthetic SDK callbacks explicitly injected only by instrumentation.
 * The ordinary app cannot select this fixture database. No actual microphone or Gemini call. */
class SpeechDraftUiChecks(private val inst:Instrumentation){
    private val c=inst.targetContext;private val lines=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};lines.add("PASS $label")}
    private fun <T> main(block:()->T):T{var v:T?=null;var e:Throwable?=null;inst.runOnMainSync{try{v=block()}catch(t:Throwable){e=t}};e?.let{throw it};@Suppress("UNCHECKED_CAST")return v as T}
    private fun await(block:()->Boolean){val end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){if(main(block))return;Thread.sleep(100)};error("DRAFT_UI_TIMEOUT")}
    private class Fake:SpeechBackend{
        override val source="VALIDATION_CALLBACK_FIXTURE";lateinit var events:SpeechBackend.Events
        var requests=0;var releases=0
        override fun initialize(ready:()->Unit,failed:(String)->Unit)=ready()
        override fun listen(events:SpeechBackend.Events){this.events=events;requests++}
        override fun finish(){}
        override fun detach(){}
        override fun release(){releases++}
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c)
        fun delivery()=SpeechDraftDelivery("draft","A","원문")
        verify(delivery().accept("새 문장","draft","A","원문",true)=="원문\n새 문장","SD01 original content appended not replaced")
        verify(delivery().accept("새 문장","draft","B","원문",true)==null,"SD02 changed author rejected")
        verify(delivery().accept("새 문장","other","A","원문",true)==null,"SD03 changed draft rejected")
        verify(delivery().accept("새 문장","draft","A","직접 바꿈",true)==null,"SD04 concurrent edit retained")
        verify(delivery().accept("새 문장","draft","A","원문",false)==null,"SD05 hidden/unsafe form rejected")
        verify(delivery().accept(" ","draft","A","원문",true)==null,"SD06 empty final not applied")
        val once=delivery();once.accept("새 문장","draft","A","원문",true)
        verify(once.accept("새 문장","draft","A","원문",true)==null,"SD07 duplicate delivery ignored")
        verify(SpeechDraftDelivery("d","A","x".repeat(999)).accept("xx","d","A","x".repeat(999),true)==null,"SD08 overflow retains full original")
        val prefix="gleo-ui-"+UUID.randomUUID();val name=prefix+".db"
        val (author,reader)=SqliteIssueRepository(c,name).use{val s=CaritdaService(it,{true});val a=s.registerDriver("초안작성 fixture");val b=s.registerDriver("초안인계 fixture");s.selectDriver(a);a to b}
        fun launch()=inst.startActivitySync(Intent(c,MainActivity::class.java).putExtra("validation_fixture",prefix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as MainActivity
        var activity=launch()
        fun button(tag:String)=activity.window.decorView.findViewWithTag<Button>(tag)
        fun click(tag:String){if(BLightTestNavigation.prepare(inst,activity,tag))return;await{button(tag)?.isEnabled==true};main{check(button(tag).performClick())}}
        try{
            click("driver_${author.id}");click("start_driving")
            val engine=Fake()
            val speech=main{SpeechInput(activity,factory={engine},allowed={true})}
            // Test-only reflection swaps the dependency, not production code or SDK settings.
            main{val f=MainActivity::class.java.getDeclaredField("speechInput\$delegate");f.isAccessible=true;(f.get(activity) as Lazy<*>).let{if(it.isInitialized())(it.value as SpeechInput).stop()};f.set(activity,lazy{speech})}
            click("new_issue")
            val field=main{activity.window.decorView.findViewWithTag<EditText>("situation")}
            main{field.setText("기존 초안 fixture")}
            click("voice_symptom");await{engine.requests==1}
            main{engine.events.started();engine.events.updated("추가 음성 fixture",false)};inst.waitForIdleSync()
            verify(main{field.text.toString()=="기존 초안 fixture"},"SD09 partial never updates editing field")
            main{engine.events.updated("추가 음성 fixture",true)};inst.waitForIdleSync()
            verify(main{field.text.toString()=="기존 초안 fixture"&&!button("save_issue").isEnabled},"SD10 final waits for ended and prevents premature save")
            main{engine.events.ended()};await{field.text.toString()=="기존 초안 fixture\n추가 음성 fixture"}
            verify(main{button("save_issue").isEnabled&&!speech.busy},"SD11 final and ended append directly to editable form without confirmation modal")
            SqliteIssueRepository(c,name).use{verify(it.issues().isEmpty()&&it.draft()?.body==field.text.toString(),"SD12 automatic speech completion persists draft but creates no issue")}
            main{engine.events.ended();engine.events.updated("중복 fixture",true)};inst.waitForIdleSync()
            verify(main{field.text.toString()=="기존 초안 fixture\n추가 음성 fixture"},"SD13 duplicate SDK final does not append twice")
            click("voice_symptom");await{engine.requests==2};main{engine.events.updated("취소 fixture",false)};inst.waitForIdleSync();click("voice_cancel")
            main{engine.events.updated("늦은 fixture",true);engine.events.ended()};inst.waitForIdleSync()
            verify(main{field.text.toString()=="기존 초안 fixture\n추가 음성 fixture"&&speech.state.phase==SpeechPhase.CANCELLED},"SD14 cancelled late result retains original form")
            val monitor=inst.addMonitor(MainActivity::class.java.name,null,false)
            main{activity.recreate()}
            activity=(inst.waitForMonitorWithTimeout(monitor,12000) as? MainActivity)?:error("RECREATION_NOT_OBSERVED")
            inst.removeMonitor(monitor)
            await{activity.window.decorView.findViewWithTag<EditText>("situation")?.text?.toString()=="기존 초안 fixture\n추가 음성 fixture"&&button("save_issue")?.isEnabled==true}
            verify(true,"SD19 Activity recreation restores editable speech draft without auto save")
            click("save_issue")
            val issue=SqliteIssueRepository(c,name).use{it.issues().single()}
            verify(issue.reporter.id==author.id&&issue.situation=="기존 초안 fixture\n추가 음성 fixture","SD15 only explicit Save creates issue with original author")
            main{activity.finish()};inst.waitForIdleSync();activity=launch();click("driver_${reader.id}");click("start_driving")
            SqliteIssueRepository(c,name).use{repo->val s=CaritdaService(repo,{true});s.restoreConfiguration(reader.id)
                verify(s.needsAcknowledgement(s.issue(issue.id)),"SD16 next driver sees saved issue after Activity restart")
                verify(s.issue(issue.id).situation==issue.situation&&s.events(issue.id).single().kind==EventKind.CREATED,"SD17 restart and driver selection preserve original issue/history")
            }
            verify(main{activity.window.decorView.findViewWithTag<Button>("new_issue")!=null},"SD18 general-source home remains reachable after handover")
            click("all_issues");click("issue_${issue.id}")
            verify(main{button("acknowledge")?.isEnabled==true},"SD20 B issue detail offers explicit acknowledgement")
            click("acknowledge")
            SqliteIssueRepository(c,name).use{repo->verify(repo.events(issue.id).count{it.kind==EventKind.ACKNOWLEDGED&&it.actor.id==reader.id}==1&&repo.issue(issue.id)?.status==issue.status,"SD21 explicit B acknowledgement persists without resolving the issue")}
        }finally{main{activity.finish()}}
        return lines
    }
}
