package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.domain.CaritdaService
import com.nextdrive.pleos.model.*
import java.util.UUID

/** Same real Activity and SQLite, confined to the separately installed validation application. */
class NavigationChecks(private val instrumentation:Instrumentation) {
    private val context=instrumentation.targetContext
    private val results=mutableListOf<String>()
    private fun verify(ok:Boolean,message:String){check(ok){message};results.add("PASS $message")}
    private fun <T> main(action:()->T):T {var result:T?=null;instrumentation.runOnMainSync{result=action()};@Suppress("UNCHECKED_CAST") return result as T}
    private fun await(message:String,condition:()->Boolean){val end=System.currentTimeMillis()+20_000;while(System.currentTimeMillis()<end){if(condition())return;Thread.sleep(80)};error(message)}
    private fun launch()=instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN).apply{setClassName(context,MainActivity::class.java.name);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}) as MainActivity
    private fun finish(a:MainActivity){main{a.finish()};instrumentation.waitForIdleSync()}
    private fun tag(a:MainActivity,key:String):View?=a.window.decorView.findViewWithTag(key)
    private fun click(a:MainActivity,key:String){ValidationUiActions.reveal(instrumentation,a,key);main{val view=tag(a,key)?:error("Missing $key");check(view.isEnabled){"Disabled $key"};check(view.performClick())};instrumentation.waitForIdleSync()}
    private fun select(a:MainActivity,id:String){await("driver $id input allowed"){main{tag(a,"driver_$id")?.isEnabled==true}};click(a,"driver_$id");click(a,"start_driving")}
    private fun text(a:MainActivity)=main{
        fun collect(v:View):String=if(v is ViewGroup)(0 until v.childCount).joinToString("\n"){collect(v.getChildAt(it))} else if(v is TextView && v.visibility==View.VISIBLE)v.text.toString() else ""
        collect(a.window.decorView)
    }
    private fun set(a:MainActivity,key:String,value:String){ValidationUiActions.reveal(instrumentation,a,key);main{(tag(a,key) as EditText).setText(value)};instrumentation.waitForIdleSync()}
    private fun search(a:MainActivity,value:String){set(a,"record_search",value);click(a,"apply_search")}
    private fun dialog(label:String){
        await("dialog $label"){instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.any{it.isClickable&&it.text?.toString()==label}==true}
        val node=instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).first{it.isClickable&&it.text?.toString()==label}
        check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync()
    }
    private fun snapshot(repo:SqliteIssueRepository)=listOf("drivers","issues","events","settings","drafts").associateWith{table->repo.readableDatabase.rawQuery("SELECT * FROM $table ORDER BY 1",null).use{c->buildList{while(c.moveToNext())add((0 until c.columnCount).map{if(c.isNull(it))null else c.getString(it)})}}}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(context)
        val repo=SqliteIssueRepository(context);val service=CaritdaService(repo,{true});service.selectDriver(Driver.A)
        repeat(24){service.record(SymptomType.NOISE,"격리 목록 기록 $it",System.currentTimeMillis(),"")}
        val target=service.record(SymptomType.VIBRATION,"긴 상황 "+"앞부분 ".repeat(70)+"꼬리검색",System.currentTimeMillis(),"특별메모")
        service.addNote(target.id,"기존 조치 보존");service.recur(target.id,"기존 재발 보존")
        val deleted=service.record(SymptomType.OTHER,"검색해도 안 보일 삭제 기록",System.currentTimeMillis(),"");service.deleteOwned(deleted.id)
        service.selectDriver(Driver.B)
        val other=service.record(SymptomType.OTHER,"다른 운전자의 완료 기록",System.currentTimeMillis(),"");service.changeStatus(other.id,IssueStatus.RESOLVED);service.selectDriver(Driver.A)
        var a=launch();select(a,"A")
        val before=snapshot(repo)
        click(a,"nav_HOME");click(a,"nav_HOME")
        verify(snapshot(repo)==before,"N01 repeated app home changes no records drivers acknowledgements or confirmed data")
        click(a,"my_issues")
        verify(main{tag(a,"issue_${target.id}")!=null && tag(a,"issue_${other.id}")==null},"N02 home opens mine by permanent driver ID")
        click(a,"filter_ALL")
        verify(main{tag(a,"issue_${target.id}")!=null && tag(a,"issue_${other.id}")!=null && tag(a,"issue_${deleted.id}")==null},"N03 all includes managed completed and excludes logically deleted")
        click(a,"filter_COMPLETED");verify(main{tag(a,"issue_${other.id}")!=null&&tag(a,"issue_${target.id}")==null},"N04 completed filter remains explicit")
        click(a,"filter_MANAGED");verify(main{tag(a,"issue_${other.id}")==null&&tag(a,"issue_${target.id}")!=null},"N05 managed includes unresolved inspection stages")
        click(a,"filter_ALL")
        search(a,"진동");verify(main{tag(a,"issue_${target.id}")!=null&&tag(a,"issue_${other.id}")==null},"N06 category search matches human label")
        search(a,"특별메모");verify(main{tag(a,"issue_${target.id}")!=null},"N07 search includes additional memo")
        search(a,"꼬리검색");verify(main{tag(a,"issue_${target.id}")!=null},"N08 search reads full body beyond card preview")
        val scroll=main{tag(a,"screen_scroll") as ScrollView}
        main{scroll.fullScroll(View.FOCUS_DOWN)};instrumentation.waitForIdleSync()
        val oldY=main{scroll.scrollY};val homeY=main{tag(a,"nav_HOME")!!.getLocationOnScreen(IntArray(2)).let{tag(a,"nav_HOME")!!.top}}
        verify(main{var parent=tag(a,"navigation_bar")!!.parent;var nested=false;while(parent is View){if(parent is ScrollView)nested=true;parent=parent.parent};!nested&&tag(a,"nav_HOME")!!.getGlobalVisibleRect(Rect())},"N09 home navigation remains visible outside scrolling body")
        click(a,"issue_${target.id}");click(a,"back")
        await("previous list scroll restored"){main{(tag(a,"screen_scroll") as ScrollView).scrollY==oldY}}
        verify(main{(tag(a,"record_search") as EditText).text.toString()=="꼬리검색" && (tag(a,"filter_ALL") as Button).text.startsWith("✓") && tag(a,"nav_HOME")!!.top==homeY},"N10 detail previous restores search filter and scroll without moving fixed home")
        search(a,"절대없는검색어")
        verify(text(a).contains("결과 0건")&&text(a).contains("조건에 맞는 기록이 없어요")&&!text(a).contains("아직 남긴 기록이 없어요"),"N11 zero query results are distinct from an empty database")
        click(a,"reset_conditions")
        verify(main{(tag(a,"record_search") as EditText).text.isEmpty()&&tag(a,"issue_${other.id}")!=null},"N12 reset clears query and status scope together")
        click(a,"issue_${target.id}");click(a,"help");click(a,"nav_HOME")
        verify(main{tag(a,"all_issues")!=null},"N13 help home directly reaches app home")
        main{a.onBackPressed()};instrumentation.waitForIdleSync()
        // Android 12+ may background a root launcher task instead of finishing it.
        // The invariant is that Back cannot resurrect a nested detail screen.
        verify(main{a.isFinishing||(tag(a,"all_issues")!=null&&tag(a,"issue_summary")==null)},"N14 home clears nested history rather than returning to old detail")
        finish(a);a=launch();select(a,"A")
        click(a,"new_issue");set(a,"situation","홈으로 가져갈 새 기록 초안");set(a,"memo","원 작성자 보존")
        val draft=repo.draft()!!;click(a,"nav_HOME")
        verify(repo.issue(draft.id)==null&&repo.draft()?.body==draft.body&&repo.draft()?.ownerId=="A","N15 new record home preserves only draft and original author")
        click(a,"resume_draft");click(a,"back")
        verify(repo.draft()?.id==draft.id,"N16 previous navigation also retains authored draft")
        click(a,"resume_draft");click(a,"discard_draft");dialog("확인")
        click(a,"all_issues");search(a,"꼬리검색");click(a,"issue_${target.id}");click(a,"edit_issue")
        set(a,"situation","수정 후 조건이 달라진 기록");set(a,"memo","수정 초안의 메모")
        click(a,"nav_HOME")
        verify(repo.issue(target.id)==target.copy(status=IssueStatus.UNRESOLVED,revision=2,updatedAt=repo.issue(target.id)!!.updatedAt)&&repo.draft()?.kind=="EDIT","N17 edit home keeps committed original and separate edit draft")
        context.getFileStreamPath("navigation-target.txt").writeText(target.id)
        finish(a);repo.close();return results
    }
    fun coldRun():List<String>{
        ValidationEnvironment.requireIsolated(context)
        val repo=SqliteIssueRepository(context);val id=context.getFileStreamPath("navigation-target.txt").readText();val original=repo.issue(id)!!
        val priorEvents=repo.events(id)
        val a=launch();select(a,"A");click(a,"resume_draft")
        verify(main{(tag(a,"situation") as EditText).text.toString()=="수정 후 조건이 달라진 기록"}&&repo.issue(id)==original,"N18 process restart restores edit draft with original unchanged")
        click(a,"discard_draft");dialog("확인")
        verify(repo.issue(id)==original&&repo.events(id)==priorEvents&&main{tag(a,"issue_summary")!=null},"N19 edit cancellation returns to original detail without audit mutation")
        click(a,"edit_issue");click(a,"save_issue")
        verify(repo.issue(id)==original&&repo.events(id)==priorEvents&&text(a).contains("변경된 내용이 없어"),"N20 unchanged edit consumes draft without fake content revision")
        click(a,"nav_HOME");click(a,"all_issues");search(a,"꼬리검색");click(a,"issue_$id");click(a,"edit_issue")
        set(a,"situation","수정 후 조건이 달라진 기록");set(a,"memo","수정한 메모");click(a,"save_issue")
        verify(repo.issue(id)!!.situation=="수정 후 조건이 달라진 기록"&&text(a).contains("현재 검색·필터 조건과 달라"),"N21 edit same ID reports exclusion by previous query")
        click(a,"back")
        verify(main{(tag(a,"record_search") as EditText).text.toString()=="꼬리검색"}&&text(a).contains("결과 0건"),"N22 edited detail returns to original search instead of silently resetting it")
        click(a,"reset_conditions");click(a,"issue_$id")
        val revised=repo.issue(id)!!
        var previous=revised.status
        for(next in listOf(IssueStatus.PLANNED,IssueStatus.INSPECTED,IssueStatus.RESOLVED,IssueStatus.UNRESOLVED)){
            click(a,"change_status");dialog(next.label);if(next==IssueStatus.RESOLVED)dialog("확인")
            val event=repo.events(id).last()
            check(event.kind==EventKind.STATUS&&event.actor.id=="A"&&event.status==next&&event.text=="${previous.label} → ${next.label}"&&event.at>0);previous=next
        }
        verify(repo.issue(id)==revised.copy(status=IssueStatus.UNRESOLVED,updatedAt=repo.issue(id)!!.updatedAt)&&repo.events(id).containsAll(priorEvents),"N23 four manual states retain before after actor time and all original content history")
        click(a,"edit_issue")
        repo.writableDatabase.execSQL("CREATE TRIGGER nav_fail_draft BEFORE INSERT ON drafts BEGIN SELECT RAISE(ABORT,'isolated draft write failure'); END")
        set(a,"situation","보존 실패 후 화면에 유지할 수정")
        click(a,"nav_HOME")
        verify(main{tag(a,"save_issue")!=null}&&text(a).contains("초안을 기기에 보존하지 못했어요")&&repo.issue(id)!!.situation==revised.situation,"N24 failed draft persistence blocks home and never claims success")
        repo.writableDatabase.execSQL("DROP TRIGGER nav_fail_draft");click(a,"nav_HOME");click(a,"resume_draft")
        verify(main{(tag(a,"situation") as EditText).text.toString()=="보존 실패 후 화면에 유지할 수정"},"N25 retry persists held input and allows explicit resume")
        click(a,"discard_draft");dialog("확인")
        click(a,"edit_issue")
        val staleDraft=repo.draft()!!
        val concurrent=CaritdaService(repo,{true});concurrent.selectDriver(Driver.A);concurrent.recur(id,"격리된 동시 변경 검증")
        val changed=repo.issue(id)!!;val changedEvents=repo.events(id)
        click(a,"save_issue")
        verify(repo.issue(id)==changed&&repo.events(id)==changedEvents&&repo.draft()?.id==staleDraft.id&&text(a).contains("기록이 변경되었어요"),"N29 stale unchanged edit cannot bypass revision guard or silently discard its draft")
        click(a,"discard_draft");dialog("확인")
        val before=snapshot(repo);val home=main{tag(a,"nav_HOME")!!}
        main{instrumentation.callActivityOnStop(a);home.performClick()}
        verify(snapshot(repo)==before,"N26 UNKNOWN home does not bypass gate or write user decisions")
        main{instrumentation.callActivityOnStart(a)};await("gate reconnected"){main{tag(a,"nav_HOME")?.isEnabled==true}}
        layoutChecks(a)
        verify(repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()},"N27 navigation keeps all relational references valid")
        finish(a);repo.close();return results
    }
    private fun layoutChecks(a:MainActivity){
        val configuration=Configuration(a.resources.configuration)
        val root=main{(a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)}
        val oldWidth=root.layoutParams.width;val oldHeight=root.layoutParams.height
        try{
            for(width in listOf(320,600,1024))for(scale in listOf(1f,2f)){
                main{
                    @Suppress("DEPRECATION")
                    a.resources.updateConfiguration(Configuration(configuration).apply{fontScale=scale},a.resources.displayMetrics)
                    root.layoutParams=root.layoutParams.apply{this.width=(width*a.resources.displayMetrics.density).toInt();height=(320*a.resources.displayMetrics.density).toInt()};root.requestLayout()
                };instrumentation.waitForIdleSync()
                click(a,"help");click(a,"nav_HOME");instrumentation.waitForIdleSync()
                main{check(tag(a,"nav_HOME")!!.getGlobalVisibleRect(Rect()));check((tag(a,"screen_scroll") as ScrollView).height>0)}
            }
            verify(true,"N28 fixed home and usable scroll remain in six low-height width font combinations")
        }finally{
            main{
                @Suppress("DEPRECATION")
                a.resources.updateConfiguration(configuration,a.resources.displayMetrics)
                root.layoutParams=root.layoutParams.apply{width=oldWidth;height=oldHeight};root.requestLayout()
            }
        }
    }
}
