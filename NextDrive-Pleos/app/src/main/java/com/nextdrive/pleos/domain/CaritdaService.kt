package com.nextdrive.pleos.domain

import com.nextdrive.pleos.data.IssueRepository
import com.nextdrive.pleos.model.*
import java.util.UUID

class EditingBlockedException : IllegalStateException("정차 상태와 화면 제한이 확인된 뒤 이용해 주세요.")

class CaritdaService(
    private val repository: IssueRepository,
    private val editingAllowed: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var confirmedId: String? = null
    fun driver() = repository.currentDriver() ?: throw IllegalArgumentException("먼저 운전자를 등록하고 운전 시작을 눌러 주세요.")
    fun confirmed() = confirmedId != null && confirmedId == repository.currentDriver()?.id
    fun restoreConfiguration(id: String?) { if(repository.currentDriver()?.id==id) confirmedId=id }
    fun drivers() = repository.drivers()
    fun registerDriver(name:String):Driver { requireEditing();return repository.registerDriver(name) }
    fun renameDriver(id:String,name:String) { requireEditing();repository.renameDriver(id,name) }
    fun archiveDriver(id:String) { requireEditing();repository.archiveDriver(id) }
    fun draft() = repository.draft()
    fun saveDraft(draft:JournalDraft) {
        val old=repository.draft()
        require(old?.let{it.id==draft.id && it.ownerId==draft.ownerId && it.ownerName==draft.ownerName && it.kind==draft.kind && it.target==draft.target && it.revision==draft.revision} ?: (confirmed() && driver().id==draft.ownerId)) { "초안 작성자를 확인해 주세요." }
        repository.saveDraft(draft.copy(updatedAt=clock()))
    }
    fun discardDraft(id:String) { requireEditing();repository.discardDraft(id) }
    fun issues() = repository.issues()
    fun issues(filter:RecordFilter):List<VehicleIssue> {
        val all=issues()
        return when(filter){
            RecordFilter.ALL->all
            RecordFilter.MANAGED->all.filter{it.status!=IssueStatus.RESOLVED}
            RecordFilter.COMPLETED->all.filter{it.status==IssueStatus.RESOLVED}
            RecordFilter.MINE->{require(confirmed()) { "기록할 이름을 먼저 선택해 주세요." };all.filter{it.reporter.id==driver().id}}
        }
    }
    fun issue(id: String) = repository.issue(id) ?: throw IllegalArgumentException("문제를 찾을 수 없습니다.")
    fun events(id: String) = repository.events(id)
    fun selectDriver(driver: Driver) {
        requireEditing()
        require(repository.draft()?.let{it.ownerId==driver.id} != false) { "작성 중인 초안을 먼저 저장하거나 취소해 주세요." }
        repository.selectDriver(driver);confirmedId=driver.id
    }
    fun record(type: SymptomType, situation: String, discoveredAt: Long, memo: String, draftId:String?=null): VehicleIssue {
        requireDriver()
        if(draftId!=null) repository.issue(draftId,true)?.let { require(it.reporter.id==driver().id && it.deletedAt==null) { "삭제된 기록의 ID는 다시 사용할 수 없어요." };return it }
        val description = text(situation, 1000, "발생 상황을 입력해 주세요.")
        require(memo.trim().length <= 2000) { "추가 메모는 2,000자 이내로 입력해 주세요." }
        val now = clock()
        require(discoveredAt in 0..(now + 60_000)) { "발견 시각을 현재 시각 이전으로 확인해 주세요." }
        val issue = VehicleIssue(draftId?:UUID.randomUUID().toString(), type, description, discoveredAt, now, draftActor(draftId,"RECORD",null), memo.trim())
        repository.create(issue, IssueEvent(EventKind.CREATED, issue.reporter, now, "운전자가 직접 기록했습니다.", 1))
        return repository.issue(issue.id)?.also { check(it==issue && repository.events(it.id).any{e->e.kind==EventKind.CREATED}) { "저장 결과를 다시 확인해 주세요." } }
            ?:error("저장 결과를 확인하지 못했어요. 같은 화면에서 다시 저장해 주세요.")
    }
    fun canManage(issue:VehicleIssue)=confirmed() && driver().id==issue.reporter.id && issue.deletedAt==null
    fun revise(id:String,type:SymptomType,situation:String,memo:String,draftId:String,expectedRevision:Int):VehicleIssue {
        requireDriver();val current=issue(id)
        require(canManage(current)) { "원래 작성자만 수정할 수 있어요." }
        val actor=draftActor(draftId,"EDIT",id,checkRevision=false)
        val body=text(situation,1000,"발생 상황을 입력해 주세요.")
        require(memo.trim().length<=2000) { "추가 메모는 2,000자 이내로 입력해 주세요." }
        val history="카테고리: ${current.type.label} → ${type.label}\n수정 전 발생 상황: ${current.situation}\n수정 후 발생 상황: $body\n수정 전 추가 메모: ${current.memo}\n수정 후 추가 메모: ${memo.trim()}"
        repository.revise(id,type,body,memo.trim(),expectedRevision,IssueEvent(EventKind.EDITED,actor,clock(),history,expectedRevision+1,draftId=draftId))
        return issue(id)
    }
    fun deleteOwned(id:String) {
        requireDriver();val current=repository.issue(id,true)?:throw IllegalArgumentException("기록을 찾을 수 없어요.")
        require(driver().id==current.reporter.id) { "원래 작성자만 삭제할 수 있어요." }
        if(current.deletedAt!=null)return
        require(repository.draft()?.target!=id) { "이 기록의 작성 중인 초안을 먼저 저장하거나 취소해 주세요." }
        repository.markDeleted(id,IssueEvent(EventKind.DELETED,driver(),clock(),"작성자가 삭제했습니다. 원본과 이력은 논리 삭제 상태로 보존합니다.",current.revision))
        check(repository.issue(id)==null) { "삭제 결과를 다시 확인해 주세요." }
    }
    fun needsAcknowledgement(issue: VehicleIssue, driver: Driver = driver()) =
        driver.id != issue.reporter.id && events(issue.id).none {
            it.kind == EventKind.ACKNOWLEDGED && it.actor.id == driver.id && it.revision == issue.revision
        }
    fun acknowledge(id: String): Boolean {
        requireDriver()
        val issue = issue(id)
        require(driver().id != issue.reporter.id) { "다른 운전자가 인계 내용을 확인해 주세요." }
        return repository.append(id, IssueEvent(EventKind.ACKNOWLEDGED, driver(), clock(), "이 차량 앱에서 인계 내용을 확인했습니다.", issue.revision))
    }
    fun addNote(id: String, note: String, draftId:String?=null) {
        requireDriver()
        val issue = issue(id)
        repository.append(id, IssueEvent(EventKind.NOTE, draftActor(draftId,"NOTE",id), clock(), text(note, 2000, "점검·조치 내용을 입력해 주세요."), issue.revision,draftId=draftId))
    }
    fun changeStatus(id: String, status: IssueStatus): Boolean {
        requireDriver()
        val issue = issue(id)
        if (issue.status == status) return false
        return repository.append(id, IssueEvent(EventKind.STATUS, driver(), clock(),
            "${issue.status.label} → ${status.label}", issue.revision, status), status)
    }
    fun recur(id: String, description: String, draftId:String?=null) {
        requireDriver()
        val issue = issue(id)
        repository.append(id, IssueEvent(EventKind.RECURRENCE, draftActor(draftId,"RECURRENCE",id), clock(),
            text(description, 2000, "다시 발생한 상황을 입력해 주세요."), issue.revision + 1,draftId=draftId),
            IssueStatus.UNRESOLVED, recurrence = true)
    }
    private fun requireEditing() { if (!editingAllowed()) throw EditingBlockedException() }
    private fun draftActor(id:String?,kind:String,target:String?,checkRevision:Boolean=true):Driver {
        val saved=repository.draft()?.takeIf{it.id==id} ?: return driver()
        require(saved.ownerId==driver().id && saved.kind==kind && saved.target==target) { "원래 작성자와 초안을 확인해 주세요." }
        if(target!=null && checkRevision)require(saved.revision==issue(target).revision) { "문제가 변경되었습니다. 초안과 현재 기록을 다시 확인해 주세요." }
        return Driver(saved.ownerId,saved.ownerName)
    }
    private fun requireDriver() { requireEditing();require(confirmed()) { "운전자를 선택하고 운전 시작을 눌러 주세요." } }
    private fun text(value: String, max: Int, message: String): String {
        val result = value.trim()
        require(result.isNotEmpty()) { message }
        require(result.length <= max) { "${max}자 이내로 입력해 주세요." }
        return result
    }
}
