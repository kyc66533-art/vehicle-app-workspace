package com.nextdrive.pleos

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nextdrive.pleos.data.SqliteIssueRepository
import com.nextdrive.pleos.model.*

class ReliabilityChecks(private val instrumentation:Instrumentation) {
    private val context=instrumentation.targetContext
    private val results=mutableListOf<String>()
    private fun verify(ok:Boolean,label:String){check(ok){label};results.add("PASS $label")}
    private fun <T> main(action:()->T):T {var value:T?=null;instrumentation.runOnMainSync{value=action()};@Suppress("UNCHECKED_CAST") return value as T}
    private fun await(label:String,test:()->Boolean){val end=System.currentTimeMillis()+20_000;while(System.currentTimeMillis()<end){if(test())return;Thread.sleep(80)};error(label)}
    private fun launch()=instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN).apply{setClassName(context,MainActivity::class.java.name);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}) as MainActivity
    private fun tag(a:MainActivity,key:String):View?=a.window.decorView.findViewWithTag(key)
    private fun click(a:MainActivity,key:String){ValidationUiActions.reveal(instrumentation,a,key);main{val v=tag(a,key)?:error("Missing $key");check(v.isEnabled){"Disabled $key"};check(v.performClick())};instrumentation.waitForIdleSync()}
    private fun navigate(a:MainActivity,key:String){if(main{tag(a,"nav_$key")==null})click(a,"menu");click(a,"nav_$key")}
    private fun select(a:MainActivity,id:String){await("driver ready"){main{tag(a,"driver_$id")?.isEnabled==true}};click(a,"driver_$id");click(a,"start_driving")}
    private fun text(a:MainActivity)=main{
        fun collect(v:View):String=if(v is ViewGroup)(0 until v.childCount).joinToString("\n"){collect(v.getChildAt(it))} else if(v is TextView && v.visibility==View.VISIBLE)v.text.toString() else ""
        collect(a.window.decorView)
    }
    private fun fill(a:MainActivity,body:String,memo:String="",type:SymptomType=SymptomType.NOISE){click(a,"type_${type.name}");ValidationUiActions.reveal(instrumentation,a,"memo");main{(tag(a,"situation") as EditText).setText(body);(tag(a,"memo") as EditText).setText(memo)};instrumentation.waitForIdleSync()}
    private fun finish(a:MainActivity){main{a.finish()};instrumentation.waitForIdleSync()}
    private fun accessible(label:String){
        await("dialog $label"){instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.any{it.isClickable && it.text?.toString()==label}==true}
        val node=instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).first{it.isClickable && it.text?.toString()==label}
        check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync()
    }
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(context)
        val repo=SqliteIssueRepository(context)
        var allowed=true
        val service=com.nextdrive.pleos.domain.CaritdaService(repo,{allowed})
        service.selectDriver(Driver.A)
        val original=service.record(SymptomType.OTHER,"보존할 기존 기록",System.currentTimeMillis(),"기존 메모")
        service.addNote(original.id,"보존할 점검 이력");service.selectDriver(Driver.B);service.acknowledge(original.id)
        service.selectDriver(Driver.A);service.recur(original.id,"보존할 재발 이력")
        service.selectDriver(Driver.B);service.acknowledge(original.id)
        val complete=service.record(SymptomType.OTHER,"다른 운전자의 완료 기록",System.currentTimeMillis(),"")
        service.changeStatus(complete.id,IssueStatus.RESOLVED);service.selectDriver(Driver.A)
        val originalEvents=repo.events(original.id);val completeEvents=repo.events(complete.id)
        var a=launch();select(a,"A")
        click(a,"new_issue");fill(a,"   ","실패해도 유지할 메모")
        val before=repo.issues().size
        click(a,"save_issue")
        verify(repo.issues().size==before && repo.draft()?.memo=="실패해도 유지할 메모" && text(a).contains("저장하지 못했어요"),"RR01 blank input preserves draft and shows persistent failure")
        verify(main{val v=tag(a,"feedback")!!;v.getGlobalVisibleRect(android.graphics.Rect()) && (tag(a,"situation") as EditText).error!=null},"RR02 failed save feedback stays visible beside field guidance")
        fill(a,"새로 작성한 사용자 증상","처음 메모",SymptomType.NOISE)
        val save=main{tag(a,"save_issue")!!}
        main{save.performClick();save.performClick()};instrumentation.waitForIdleSync()
        val created=repo.issues().single{it.situation=="새로 작성한 사용자 증상"}
        verify(repo.events(created.id).size==1 && main{tag(a,"issue_summary")!=null} && text(a).contains("저장되었습니다"),"RR03 double save commits once and remains on saved detail with success")
        main{a.onBackPressed()};instrumentation.waitForIdleSync()
        verify(main{tag(a,"issue_${created.id}")!=null} && text(a).contains("전체 기록"),"RR04 back from save opens full list containing authored content")
        finish(a);a=launch();select(a,"A");click(a,"all_issues");click(a,"issue_${created.id}")
        verify(repo.issue(created.id)==created,"RR05 saved record survives app activity restart")
        click(a,"edit_issue");fill(a,"가속할 때 핸들이 진동합니다","점검할 내용을 추가했어요",SymptomType.VIBRATION)
        val editDraft=repo.draft()!!
        click(a,"save_issue")
        val revised=repo.issue(created.id)!!
        verify(revised.type==SymptomType.VIBRATION && revised.situation=="가속할 때 핸들이 진동합니다" && revised.memo=="점검할 내용을 추가했어요" && revised.discoveredAt==created.discoveredAt,"RR06 category description memo edit keeps original discovery time")
        verify(repo.events(created.id).any{it.kind==EventKind.EDITED && it.text.contains(created.situation) && it.text.contains(revised.situation)} && revised.revision==2,"RR07 edit audit retains old and new text with timestamp and revision")
        service.selectDriver(Driver.A)
        verify(service.revise(created.id,revised.type,revised.situation,revised.memo,editDraft.id,1).revision==2 && repo.events(created.id).count{it.kind==EventKind.EDITED}==1,"RR08 repeated edit draft ID never duplicates an edit")
        click(a,"issue_summary")
        verify(text(a).contains(revised.memo) && text(a).contains("진동") && text(a).contains("작성 내용 수정 이력"),"RR09 consultation shows current content and edit history")
        finish(a);a=launch();select(a,"A");click(a,"all_issues");click(a,"issue_${created.id}")
        verify(repo.issue(created.id)==revised && text(a).contains(revised.situation),"RR10 edited content survives restart")
        click(a,"change_driver");select(a,"B");click(a,"all_issues");click(a,"issue_${created.id}")
        service.selectDriver(Driver.B)
        verify(main{tag(a,"edit_issue")==null && tag(a,"delete_issue")==null} && runCatching{service.revise(created.id,SymptomType.NOISE,"forbidden","","other",2)}.isFailure && runCatching{service.deleteOwned(created.id)}.isFailure,"RR11 another permanent driver ID cannot edit or delete in UI or service")
        service.renameDriver("A","이름을 바꾼 작성자")
        click(a,"change_driver");select(a,"A");click(a,"all_issues");click(a,"issue_${created.id}")
        verify(main{tag(a,"edit_issue")!=null && tag(a,"delete_issue")!=null} && repo.issue(created.id)!!.reporter==Driver.A,"RR12 original ID regains edit delete after name change without rewriting author snapshot")
        click(a,"delete_issue");accessible("취소")
        verify(repo.issue(created.id)!=null && repo.events(created.id).none{it.kind==EventKind.DELETED},"RR13 delete cancellation preserves original and all events")
        click(a,"delete_issue");accessible("삭제")
        verify(repo.issue(created.id)==null && repo.issue(created.id,true)?.deletedAt!=null && repo.issue(created.id,true)?.deletedById=="A" && repo.events(created.id).count{it.kind==EventKind.DELETED}==1,"RR14 author-approved logical delete keeps ID original and linked audit")
        RecordFilter.entries.forEach{filter->click(a,"filter_${filter.name}");check(main{tag(a,"issue_${created.id}")==null})}
        verify(true,"RR15 deleted record is excluded from all four normal filters")
        click(a,"all_summary")
        verify(!text(a).contains(revised.situation) && text(a).contains(original.situation),"RR16 deleted record excluded from consultation without removing others")
        service.selectDriver(Driver.A)
        verify(runCatching{service.record(SymptomType.OTHER,"resurrect",System.currentTimeMillis(),"",created.id)}.isFailure,"RR17 deleted IDs cannot be reused by an old save request")
        navigate(a,"ISSUES");click(a,"filter_COMPLETED")
        verify(main{tag(a,"issue_${complete.id}")!=null && tag(a,"issue_${original.id}")==null},"RR18 completed filter is explicit and shows only completed records")
        click(a,"filter_MANAGED")
        verify(main{tag(a,"issue_${original.id}")!=null && tag(a,"issue_${complete.id}")==null},"RR19 managed filter shows current records")
        click(a,"filter_MINE")
        verify(main{tag(a,"issue_${original.id}")!=null && tag(a,"issue_${complete.id}")==null},"RR20 mine filter uses permanent author ID rather than mutable name")
        navigate(a,"HOME");click(a,"new_issue");fill(a,"실패 후 다시 저장할 격리 기록","그대로 보존할 메모")
        val failedId=repo.draft()!!.id
        repo.writableDatabase.execSQL("CREATE TRIGGER rr_fail_create BEFORE INSERT ON events WHEN NEW.kind='CREATED' BEGIN SELECT RAISE(ABORT,'isolated create fault'); END")
        click(a,"save_issue")
        verify(repo.issue(failedId)==null && repo.draft()?.id==failedId && main{(tag(a,"situation") as EditText).text.toString()}=="실패 후 다시 저장할 격리 기록" && text(a).contains("저장하지 못했어요"),"RR21 actual SQLite event failure rolls back issue insertion and retains input draft")
        repo.writableDatabase.execSQL("DROP TRIGGER rr_fail_create");click(a,"save_issue")
        verify(repo.issue(failedId)!=null && repo.events(failedId).size==1 && repo.draft()==null,"RR22 retry after SQLite rollback commits the same ID once")
        click(a,"edit_issue");fill(a,"저장 실패 중 보존할 수정 내용","다시 저장할 메모",SymptomType.OTHER)
        val beforeEdit=repo.issue(failedId)
        repo.writableDatabase.execSQL("CREATE TRIGGER rr_fail_edit BEFORE INSERT ON events WHEN NEW.kind='EDITED' BEGIN SELECT RAISE(ABORT,'isolated edit fault'); END")
        click(a,"save_issue")
        verify(repo.issue(failedId)==beforeEdit && repo.draft()?.kind=="EDIT" && main{(tag(a,"situation") as EditText).text.toString()}=="저장 실패 중 보존할 수정 내용","RR23 edit transaction fault rolls back content and retains edit draft")
        repo.writableDatabase.execSQL("DROP TRIGGER rr_fail_edit");click(a,"save_issue")
        verify(repo.issue(failedId)!!.type==SymptomType.OTHER && repo.events(failedId).count{it.kind==EventKind.EDITED}==1,"RR24 edit retry commits exactly once")
        repo.writableDatabase.execSQL("CREATE TRIGGER rr_fail_delete BEFORE INSERT ON events WHEN NEW.kind='DELETED' BEGIN SELECT RAISE(ABORT,'isolated delete fault'); END")
        click(a,"delete_issue");accessible("삭제")
        verify(repo.issue(failedId)!=null && repo.events(failedId).none{it.kind==EventKind.DELETED},"RR25 delete event fault rolls back tombstone and preserves references")
        accessible("취소");repo.writableDatabase.execSQL("DROP TRIGGER rr_fail_delete")
        navigate(a,"HOME");click(a,"new_issue");fill(a,"뒤로 가기 경로 검증")
        navigate(a,"HOME");click(a,"resume_draft");click(a,"save_issue")
        main{a.onBackPressed();a.onBackPressed()};instrumentation.waitForIdleSync()
        verify(main{tag(a,"save_issue")==null},"RR26 saved form cannot return as an inert no-draft save screen")
        navigate(a,"HOME");click(a,"new_issue");fill(a,"입력 제한 중 보존할 초안")
        val draftBefore=repo.draft()!!;val eventsBefore=repo.issues().associate{it.id to repo.events(it.id)}
        val stoppedSave=main{tag(a,"save_issue")!!}
        main{instrumentation.callActivityOnStop(a);stoppedSave.performClick()}
        verify(repo.issue(draftBefore.id)==null && repo.draft()?.body==draftBefore.body && eventsBefore.all{repo.events(it.key)==it.value},"RR27 actual lifecycle UNKNOWN blocks save and retains draft without bypass")
        main{instrumentation.callActivityOnStart(a)}
        await("parked after stop"){main{tag(a,"save_issue")?.isEnabled==true}}
        click(a,"discard_draft");accessible("확인")
        service.selectDriver(Driver.A);allowed=false
        verify(runCatching{service.deleteOwned(original.id)}.isFailure && runCatching{service.revise(original.id,original.type,"blocked","","blocked",2)}.isFailure,"RR28 restricted domain gate blocks new edit and delete mutations")
        allowed=true
        verify(repo.events(original.id)==originalEvents && repo.events(complete.id)==completeEvents && repo.issue(original.id)==original.copy(status=IssueStatus.UNRESOLVED,revision=2,updatedAt=repo.issue(original.id)!!.updatedAt),"RR29 unrelated old handover note recurrence and identities remain intact")
        verify(repo.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use{!it.moveToFirst()},"RR30 all issue event driver references remain valid")
        context.getFileStreamPath("cold-record-id.txt").writeText(failedId)
        finish(a);repo.close()
        return results
    }
    fun coldCheck():List<String>{
        ValidationEnvironment.requireIsolated(context)
        val repo=SqliteIssueRepository(context)
        val id=context.getFileStreamPath("cold-record-id.txt").readText()
        val a=launch();select(a,"A");click(a,"all_issues");click(a,"issue_$id")
        val item=repo.issue(id)!!
        verify(item.type==SymptomType.OTHER && item.situation=="저장 실패 중 보존할 수정 내용" && item.memo=="다시 저장할 메모","RR31 edited original persists across a full target process stop")
        verify(text(a).contains(item.situation) && text(a).contains(item.memo) && main{tag(a,"edit_issue")!=null},"RR32 cold-start actual detail restores authored fields and owner actions")
        finish(a);repo.close();return results
    }
}
