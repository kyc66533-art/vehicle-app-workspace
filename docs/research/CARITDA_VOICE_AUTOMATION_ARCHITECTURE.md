# CARITDA 자동화 구현 아키텍처

2026-11-12 KST · 기준 main c2757596397f96c3fe2fe45927dd025895ec13b3. 이 문서의 신규 API/테이블은 **설계 제안**이며 이번에 앱에 구현한 것이 아니다.

함께 읽기: [가능성/공식 근거](CARITDA_VOICE_AUTOMATION_FEASIBILITY.md), [13단계 개발·시험](CARITDA_AUTOMATION_CODEX_PLAN.md).

## 1. 무엇을 재사용하고 무엇이 빠져 있는가

K = NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/. 모든 행 번호는 기준커밋에만 적용.

| 현재 파일/위치 | 재사용할 실제 계약 | 빠진 연결 |
|---|---|---|
| K/MainActivity.kt:102-123 voiceDraft | permission→현재초안·작성자·원문 일치→STT결과만반영 | 자연어명령/자동commit/연속대화 없음 |
| K/integration/SpeechBackend.kt:15-47 | ON_DEVICE, ResultListener, Locale.KOREA, request/stop/detach/release | 세션간공유ownership·focus조정 |
| K/integration/SpeechInput.kt:74-81,100-113 | final+ended이후완료, generation/취소 | 800자 silent take 제거, typed 원문/길이오류 |
| K/integration/SpeechDraftDelivery.kt:5-14 | 같은초안/owner/base본문에한번적용 | 자동기록은별도 frozencommand·commitreceipt |
| K/integration/SpeechOutput.kt:27,44,54,79-84 | speak(text,completed:Boolean,changed), onDone만true | stop은현재completion통지없이해제. typed terminal cancellation 필요 |
| K/integration/ExplanationPlayback.kt:9-27 | 전체문장을serial chunk로재생, generation | aggregate DONE/CANCELLED/FAILED/REJECTED callback 없음 |
| K/domain/CaritdaService.kt:23-27,41-67,108-115 | 운전자확정/초안소유권/record/revise/requireEditing | atomic commitDraft+내용동일성+명시자동화permit |
| K/data/SqliteIssueRepository.kt:128-177 | issue+event+draft소비 transaction, 수정CAS | 서비스사전check까지원자적이지않음, 완료receipt추가 |
| K/ai/IssueAnalysisService.kt:29-48 | explain(api,selected,excerpt,fresh,current), 단일lease·현재버전·출처 | dialoguecontroller가호출, 실제multi-turn은별도계약 |
| K/ai/IssueExplanation.kt:7-21 | contract/source/category/situation,3~800자 | 후속질문/메시지·차량context 없음 |
| K/ai/PortableAiClient.kt:39-81 | frozen intent/pending·신규/회수/캐시·usage/UNKNOWN | turn별원장연결·별도dialogue scope |
| K/ai/PortableAiStore.kt | account별AES-GCM/Keystore·pending/result | 대화이력과동일하지않음. 원문공유DB로우회복제금지 |
| K/ai/AiExplanation.kt:55-81 | 고정HTTPS/endpoint allowlist,4KiB request,16KiB response | 새dialogue endpoint는명시추가필요 |
| K/vehicle/PleosSocObserver.kt + SocBackend.kt:22-31 | 실제SOC callback/GET/timeout/generation | 다른Safety4종observer·durable음성전달 |
| K/vehicle/DriveCheck.kt + ui/VehicleAttention.kt | accepted/quality/attention, 네SDK안내+SOC사용자기준 | pure표현을event저장/오디오실행기로바꾸지않음 |
| K/MainActivity.kt:251-254,390,568-598 | 원관측→재정리/분석→홈갱신, 가시/ALLOWED구독 | 자동TTS·알림후세션진입 |
| K/integration/CaritdaActions.kt:21-45 | QUERY/DRAFT/ANALYZE dispatcher | 로컬query를재사용하되자기앱호출에외부Intent를돌리지않음 |
| K/integration/GleoReplyChannel.kt:24-48 | peer/서명/receiver/읽기동의·1회전송·6초예산 | 빈pin을유지. 내부음성의성공조건과분리 |

Fused/Navi는현재명시적요청과기존경로보호를유지한다. 위치·경로·마이크원본수집을이번자동화에서은근히추가하지않는다.

## 2. 최소 신규 구성요소와 실제 수정 파일

아래는미구현 API다. 책임별새클래스를무한증식시키지않고controller/router/store/domain/observer/선택적cloud계약으로제한한다.

| 파일 | 책임·이유 | 입력→출력 | 상태/오류·연결 | DB/권한/검증 |
|---|---|---|---|---|
| 새 K/voice/VoiceConversationController.kt | FSM·단일actor·mic/TTS/focus·맥락·알림queue. 기존서비스는전체흐름을소유하지않음 | SessionPermit, ASR/TTS/AI/alert event→UIstate/효과 | generation/session/turn/driver/issueRevision/operation 검증, cancel(reason), offerAttention(event) | 이력은store위임. mic+기존safety. fake clock/adapter로race검사 |
| 새 K/voice/VoiceCommandRouter.kt | 순수로컬명령해석/모호성질문, generic외부LLM으로쓰기결정안함 | finalTranscript+PinnedContext→sealed VoiceCommand | QuerySoc/QueryAttention/QueryHandover/QueryRecent/CreateIssue/Confirm/Explain/Repeat/SwitchIssue/End/Unknown | DB없음·모델0, 한국어표현/부정/인용/오인식fixture |
| 새 K/data/ConversationStore.kt | 세션/메시지/작업/재생/링크의영속화·암호화. 원문긴문자열append대체 | appendOnce(sessionId,turn), stageOperation, receipt→typed rows | UNIQUE turn/seq/token, 범위별읽기·cleanup·암호실패시차단. Cipherhelper는같은파일내/기존primitive 재사용 | 신규sidecar v1, 기존journal재작성없음. crash/계정분리검사 |
| 새 K/domain/DraftCommit.kt + 기존 Service/Repository/Sqlite수정 | 자동쓰기명령/permit/receipt; 동일token다른본문충돌방지 | frozen DraftCommit+LocalSavePermit→CommitReceipt | commitDraft를동일SQLite transaction으로검증/쓰기/소비/receipt. 같은digest만동일결과 | journal v3→v4 비파괴receipt표추가(선택안아래), 이전/롤백검사 |
| 새 K/vehicle/PleosAttentionObserver.kt | Safety4종의실제공개subscription adapter. SOC는기존observer를중복생성안함 | allowed/권한/capability→원관측commit참조+품질event | 신호별listener동일인스턴스해제,timeout/generation·null/ERROR거부 | 기존CAR_SAFETY, 신규특권권한0. 실제지원별검사 |
| 새 K/vehicle/AttentionEventStore.kt | durable episode/delivery/ack/snooze/link. VehicleAttention은순수표현으로유지 | commit된observation+rule→episode/update/exit/gap | 원관측storeUUID+row+kind+source 기준dedupe. SOC기존사건재사용 | 별도sidecar v1. raw/journal원자성주장안함 |
| 기존 K/integration/ExplanationPlayback.kt, SpeechOutput.kt, SpeechInput.kt | typed 완료/취소·전체원문·focus hook | PlaybackRequest→Outcome, FinalTranscript→router | 기존API의default overload로현재UI회귀보호. stop은1회terminal | 새권한0, 기존STT/TTS회귀 |
| 기존 K/MainActivity.kt, PortableAiActivity.kt | 소형대화시작/종료진입/상태표시,기존UI경로와같은분석/재생arbiter | 사용자start/stop/lifecycle/driver변경→controller | UI그리기에서네트워크/구독시작금지, onStop즉시중단 | B안UI유지. navigation/초안회귀 |
| 선택 신규 K/ai/IssueDialogueContract.kt + IssueDialogueService.kt | 진짜후속질문cloud projector/응답검증. 기존단일issue계약을위장확장안함 | 허용한issue·bounded turns/facts→frozen request/result | 기존operation엔진재사용,새scope/동의/현재버전/seq검증 | Worker별도코드/테스트필요. 배포·실호출별도 |
| 기존 Gleo3파일 | 같은로컬query/domain/analysis포트로위임하는adapter만 | 검증된Action→기존callback | 외부auth/6초/3action은불변. 로컬세션이Gleo송신권한얻지않음 | fixture/차단회귀,외부E2E별도 |

새 별도 Android Service/ForegroundService는이최소구조에필요없다. controller의Service라는용어는도메인서비스이지OS백그라운드권한이아니다.

### 제안 시그니처

~~~kotlin
// 모두 proposed API. 현재 SDK 메서드로 오인하지 말 것.
fun start(permit: SessionPermit, anchor: ContextAnchor?): SessionId
fun acceptFinal(token: RecognitionToken, transcript: FinalTranscript)
fun offerAttention(event: AttentionEpisode)
fun cancel(reason: EndReason)

fun route(text: String, context: PinnedContext): VoiceCommand
fun commitDraft(command: DraftCommit, permit: LocalSavePermit): CommitReceipt
fun appendOnce(sessionId: String, turn: ConversationTurn): AppendResult
fun reconcileLinks(): ReconcileResult

fun play(text: String, completed: (PlaybackOutcome) -> Unit,
         changed: (String) -> Unit)
// PlaybackOutcome = DONE / CANCELLED / FAILED / REJECTED, reason 별도
~~~

## 3. 세션·동의·발화 해석의 정확한 규칙

### 3.1 진입과 '음성 호출 한 번'의 의미

1차는기존앱안작은'음성대화시작'동작한번으로세션을연다. 이후마이크를매턴누르지않는다. 이미활성화된세션에서 '카잇다, 차앞쪽에서소리가나'는접두사와증상을해석한다. 앱이닫힌상태의동일발화로앱을깨운다는약속은하지않는다. 시스템전체호출은공식OEM/Gleo경로가실제허용될때만adapter로추가한다.

### 3.2 권한을 하나의 boolean으로 합치지 않음

- DevicePermit: visible, CarSafetyGate ALLOWED, mic권한, focus/입력가용성.
- ActorContext: 확정localDriverId/당시이름, cloudAccountHash(있을때), driverEpoch, localStoreScope. FirebaseUID와driverID는다름.
- AutomationPermit: QUERY_ONLY / RECORD_ONLY / RECORD_EXPLAIN_SPEAK, 후속자동청취 여부, 유효세션/만료/최대cloud시도/내용범위. opt-in기본꺼짐.
- LocalSavePermit: 명시'기록해줘'또는사전허용증상자동화모드+명확한새증상. ASRfinal자체가동의가아님.
- CloudPermit: 현재계정/기기등록/동의contract·허용fields·자동요청범위+frozen payload digest, 예산/UNKNOWN. runtime마이크허용/로컬저장동의는cloud동의가아님.
- AnnouncementPermit: 차량알림자동발화와알림뒤자동마이크를따로허용. 음성알림허용만으로몰래청취하지않음.
- HistoryPermit: STT 텍스트/질문·답변/재생 상태의 암호화 보관·30일 제안기간·공유 범위. 원본 오디오는 포함하지 않는다. RECORD_EXPLAIN_SPEAK라는 이름만으로 이력 보관/후속 마이크까지 묵시 허용하지 않는다.

완전 자동화 성공 경로의 전제는 LocalSavePermit + 해당 턴의 CloudPermit + 자동 TTS 허용 + HistoryPermit + 후속 청취 허용이다. 하나라도 없으면 허용된 하위 기능만 실행하고 전체 자동화 완료로 표시하지 않는다.

현재payload미리보기/동의의의미를단순hasConsent플래그로우회하지않는다. 자동모드용동의문구/범위·세션permit을별도정의하고기존server정책과맞는지검사한다. 개인정보가능문장은cloud사본에서확인/수정하도록멈출수있어야한다. 로컬PII검출은완전하지않다고명시하고이름/위치/메모를자동붙이지않는다.

### 3.3 실행 직전 재검사

자동 journal commit, cloud dispatch(RESERVED READ에 의한 최초 dispatch 포함), TTS 재생, 후속 STT, queued alert 전달 직전에 현재 visible/ALLOWED/driverEpoch/계정/permit·generation을 다시 검사한다. 과거 start 시의 allowed snapshot만 쓰지 않는다. controller actor와 도메인 transaction 직전 guard를 공유하고 안전 epoch가 바뀌면 다음 효과를 차단한다. 이미 진행된 provider 작업의 결과/오류 메타 보관·receipt 조회·기존 link 복구 같은 비내용 기술 bookkeeping은 허용할 수 있으나, 그것이 새 issue 생성/paid dispatch/음성·마이크 실행을 일으키면 안 된다. 원래 작업을 실제로 취소하지 못한 경우도 중단 성공으로 가장하지 않는다.

### 3.4 로컬 명령 예시

| 발화 | 처리 | 쓰기/모델 |
|---|---|---|
| 배터리얼마나남았어 | EV_SOC 최신시도·유효값/시각조회. 12V와모호하면확인질문 | 0/0 |
| 차에확인할문제있어 | 현재SDK안내/사용자SOC기준/수동문제별로출처구분 | 0/0 |
| 다른운전자가남긴내용알려줘 | 현재운전자기준미확인조회. 읽음자동생성안함 | 0/0 |
| 최근문제뭐야 | 정렬된실제기록을명시선택, 하나로고정 | 0/0 |
| 기록해줘,앞에서소리나 | freeze/초안/현재운전자검사→자동commit | 로컬1/모드에따라AI |
| 카잇다,앞에서소리나 (자동모드) | 새증상패턴·부정/인용아님·기존draft없음→허용범위자동화 | 로컬1/허용시최초AI1 |
| 소리가난다던데/기록하지마/예를들어… | 인용/부정/대상불명확→쓰기안함 | 0/0 |
| 왜그럴까/그럼어떻게해 | pinned issue/alert없으면명확화. 있으면로컬기존설명또는별도dialogue계약 | 신규모델이필요한경우명확구분 |
| 정비소가야할까 | 생성형점검참고. 안전보증/자동목적지설정안함 | 새계약또는기존inspection재생 |
| 이내용기록해줘 (alert대화) | alert anchor+로컬입력내용미리freeze→명시commit, AI문장을원증상으로위장안함 | 로컬1 |
| 해결됐어/삭제해 | 파괴/상태수정은첫릴리스자동실행제외,대상·내용되읽기+명시확인후기존도메인흐름 | 확인전0 |
| 그만/나중에다시볼게 | 세션종료·mic/TTS중단·작업상태보존 | 새요청0 |

분류는기존NOISE/VIBRATION/WARNING/OTHER를유지한다. ASR의confidence값이없으면임의신뢰점수를만들지않고명령패턴/현재맥락/모호성으로안전하게거부한다. 같은텍스트가다른turn에서반복된것을전역중복으로버리면합법적재발기록을잃을수있다.

### 3.5 원문 길이와 개인정보

SpeechInput의take(800)를그대로쓰면'전체원문보관'은거짓이다. adapter는rawTranscript와normalizedCommand를분리하고silenttruncate금지. 기존상황1000자/AI3~800자를각각검사한다. 초과시짧게다시말하기/전송사본편집을요청하고기존원문을몰래덮어쓰지않는다. 초안검증실패면자동issue/AI0.

원본음성PCM/파일은기본저장하지않는다. ON_DEVICE를유지하고HYBRID/SERVER자동전환금지. STT텍스트는민감할수있어별도암호화/보존선택. 향후오디오보관은별도명시동의·목적·암호화·기간·삭제/공유제한이필요하고이번범위제외다. 로그에는원문/좌표/토큰/음성/AI결과를남기지않는다.

## 4. 음성 대화 상태 머신과 반이중 실행

상태:
IDLE → ARMING → LISTENING → INTERPRETING → (CONFIRMING 또는 SAVING 또는 LOCAL_ANSWER 또는 AI_REQUESTING → AI_WAITING) → SPEAKING → FOLLOW_UP_WAIT → ARMING … → ENDED.
실패는ERROR_PAUSED/RECOVERING, 안전·이탈은STOPPING→ENDED. CONFIRMING은확인질문을TTS한뒤새recognition turn에서확인응답을받는상위업무상태다.

| 상태 | 진입 동작 | 정상 다음 단계 | 실패/취소 |
|---|---|---|---|
| IDLE | 녹음/출력0 | 사용자start→ARMING | 자동부팅진입없음 |
| ARMING | permit/safety/owner확인, TTS완료/해제확인, mic준비 | SDKready/실제started→LISTENING | 권한/엔진실패→ERROR_PAUSED |
| LISTENING | 한turn유한request. partial은표시만 | final+ended둘다→INTERPRETING | final누락/ended누락/timeout은실패,성공추정금지 |
| INTERPRETING | 한actor에서frozen command·문맥확정 | Query/Save/AI/확인/End | 모호하면확인,쓰기0 |
| SAVING | pending command기록→atomiccommit→receipt조회 | 성공후만AI_REQUESTING | 실패면원문/초안유지,AI0 |
| AI_REQUESTING | permit/계정/현재revision/usage/pending검사, frozenintent영속 | AI_WAITING | 한도/동의/UNKNOWN이면로컬fallback |
| AI_WAITING | STT꺼짐,기존operation결과처리 | 검증성공→SPEAKING | 실패기록→로컬안내,다른issue로결과전달0 |
| SPEAKING | STT종료·listener해제, focusgrant, bounded전체재생 | 최종pieceDONE→FOLLOW_UP_WAIT | CANCELLED/FAILED/REJECTED에서는자동청취금지 |
| FOLLOW_UP_WAIT | 현재session/permit/expiry/generation재검사 | 허용시다음유한STT,버튼불필요 | 한차례무응답/시간만료→ENDED |
| ERROR_PAUSED | 기능별실패·현재op보존,유료루프0 | 명시resume/로컬query | 반복자동mic/network재시작0 |
| STOPPING/ENDED | generation증가,stop/detach/release/focus반납,terminal이력 | 사용자다시start만 | 늦은callback무시,재시작0 |

### 불변식

- 한시점SDK STT/TTS owner는하나. PortableAI의수동재생/기존voiceDraft와controller가동시실행되지않도록단일audio arbiter를controller/기존wrapper에주입한다.
- 원래SDKcallback에requestID가없으므로 RecognitionToken(sessionId,generation,turnId,listenerIdentity)를adapter closure에고정. 이전listener결과를새turn의전역변수로귀속시키지않음.
- final+ended순서는보장되지않아둘다기다림. 완료/취소/오류는각turn1회terminal. silenttimeout은오류이지음성완료가아님.
- TTS는실제onDone만완료. stop은CANCELLED, watchdog은FAILED, focus거부는REJECTED. 현재SpeechOutput.stop이completion을억제하므로awaitstop에매달리지않도록terminal callback보강.
- ExplanationPlayback의마지막piece완료를typedDONE으로상향전파. '음성안내를마쳤어요'라는표시문구로성공판단하지않음.
- STT가정말종료되고adapter정리된뒤TTS. TTS전체DONE·리소스/포커스정리뒤짧은잔향guard를둘수있으나타이머만으로종료성공을만들지않음. full-duplex/AEC/바지인보장없음.
- 반이중이므로TTS중음성'그만'을항상들을수없다. 항상화면중단/OSfocusloss/안전전환으로중단가능해야하며음성종료는다음청취구간에서처리한다.
- 세션기본제안: 최대3분/5user턴, 1회무응답후종료, 새유료재시도0. 자체초기정책이며SDK제한수치아님. 과제시험후조정.
- focusloss/통화/긴급음/다른비서등에서중단후자동발화·자동micresume금지. 현재전달되지않은차량안내는화면에서볼수있다.

## 5. 기록·대화·재생 데이터 모델

### 5.1 기존 journal 비파괴 보강

기존caritda_v01.db v3의문제/이벤트/운전자/초안열과의미는보존한다. 자동저장을엄밀하게멱등처리하려면v4에 **draft_commit_receipts** 테이블만추가하는안을권고한다. source코드/DB변경은이번실행하지않았다.

~~~sql
CREATE TABLE draft_commit_receipts (
  token TEXT PRIMARY KEY,
  command_digest TEXT NOT NULL, -- canonical frozen command digest
  owner_id TEXT NOT NULL,
  issue_id TEXT NOT NULL,
  issue_revision INTEGER NOT NULL,
  kind TEXT NOT NULL,
  committed_at INTEGER NOT NULL
);
~~~

여기서 원자적으로 commit되는 receipt는 **journal DB의 draft_commit_receipts 행만**이다. conversation sidecar의 operation/link는 같은 transaction이 아니며 후속 복구 대상이다.

한transaction안에서receipt동일성→draft존재/owner/kind/target/expectedRevision/현재DB운전자검사→issue/event쓰기→그draft만소비→receipt저장. digest는kind/target/owner/type/body/memo/foundAt/expectedRevision. updatedAt/재시도시각은제외. token은sessionId+recognitionTurn의안정UUID이며내용만으로중복판정하지않음.

동일token+같은digest는같은receipt, 다른digest는CONFLICT. 완료receipt도draft도없을때현재driverfallback으로기록하지않음. create/update transaction과service바깥사전검사가이미전부원자적이라고주장하지않는다. v3독립백업사본의비파괴이전/반복이전/롤백/원본행대조를먼저수행. 첫단계에서migration검증을못하면자동savefeature를켜지않는다.

대안으로기존PK를멱등토큰으로쓰는최소patch도가능하지만payload/owner/CREATED내용원자비교가필요하다. 단순record(draftId)재호출만으로완료선언하지않는다. 구현계획은상기receipt안을기본선택한다.

### 5.2 새 caritda-conversations.db v1 (sidecar)

기존journal/AI계정cache와별도다. Room/서버동기화를새로도입할필요없고기존SQLiteOpenHelper패턴을재사용한다.

| 테이블 | 핵심 필드·제약 |
|---|---|
| sessions | session_id PK, owner_driver_id, owner_name_snapshot, account_scope_hash nullable, anchor_type ISSUE/ALERT/NONE, issue_id/revision nullable, alert_episode_id nullable, generation, state, started_at/ended_at, permit_version, retention_until |
| turns | turn_id PK, session_id, seq UNIQUE(session_id,seq), role USER/ASSISTANT, origin ASR/LOCAL_RULE/GEMINI_NEW/GEMINI_SAVED, body_ciphertext, body_digest, issue_revision, parent_turn_id, created_at, outcome |
| operations | local_op_key PK, turn_id UNIQUE, frozen_payload_ciphertext/digest, contract, intent_id, server_operation_id nullable, state PREPARED/RESERVED/DISPATCHING/SUCCEEDED/FAILED/UNKNOWN/CANCELLED, error_code, provider_model_metadata nullable, requested_at/completed_at |
| playbacks | delivery_key PK, session_id, assistant_turn_id, state QUEUED/STARTED/DONE/CANCELLED/FAILED/UNCERTAIN/SKIPPED, started_at/ended_at, error_code |
| links | session_id, issue_id, issue_revision, alert_episode_id nullable, link_state PENDING/COMMITTED/ORPHANED, link_key PK (session/issue/revision/alertIdOrNone을 canonical 처리한 비-null 키); SQLite UNIQUE의 NULL 중복 허용에 의존하지 않음 |

긴대화한문자열append가아니라메시지·operation·재생을분리한다. DB간FK/원자transaction을가정하지않고linkrepair를한다. ConversationStore PREPARED→journalcommit→sidecarreceipt/link반영. 중간종료시deterministic token으로receipt조회·복구하며새issue를만들지않는다. FK는각sidecar안에서만가능한관계를설정한다.

- 질문과답변은seq로정렬하고벽시계역행을표시. 실제SDK사실은observation store UUID/row/출처/수신시각을참조하며AI추론문장을센서사실로승격하지않음.
- issue본문수정/revision변경은기존대화를삭제/덮어쓰지않고과거revision으로묶는다. 현재설명으로자동읽지않으며새분석은별도turn/op.
- 녹음원본은없고STT최종텍스트만선택보관. body/payload는Keystore AES-GCM 암호화, 메타에는원문없음. 기존PortableAiStore코드를공유문서DB로통째복사하지않고암호primitive/검증패턴만재사용.
- 기본권한은현재확정운전자+계정scope일치의private대화. 다른운전자는기존저장문제/공유허용요약만확인. 전체대화자동공유금지. 가족profile은로그인인증과동일한신원보증이아님.
- 계정logout/동의철회시기존cache정책을우회하는사본이남지않게관련cloud본문/키scope정리. journal원문은별도기존정책으로보존. 철회가과거모델요청취소/공급자데이터삭제완료라는뜻은아님.
- 초기보존정책제안은private대화30일·원문음성0일. 설정/동의화면에명시하고기존문제/이벤트를같이자동파기하지않음. 삭제/보관/개인정보영구파기는별도기능·서버보존범위까지정의해야함.

### 5.3 계정·운전자 전환과 보관의 구체 정책

모든 세션은 owner_driver_id가 필수다. account_scope_hash가 NULL인 것은 순수 로컬 세션만 뜻한다. ASR 사용자 본문은 local-driver 하위 키, cloud 응답/전송 사본은 account+driver 하위 키로 암호화하고 origin에 맞는 소유 범위를 강제한다.

- 운전자 변경: 즉시 세션 종료·메모리 문맥/마이크/출력 제거. 기존 암호문은 원 소유 범위로 보관, 새 운전자에게 자동 표시/전송하지 않음.
- 계정 logout/교체: 세션 종료. 기존 Portable cache 정책과 맞춰 해당 account의 cloud 응답/전송 사본 암호문·키를 제거하고 작업 상태의 비내용 메타만 유지. driver-local 텍스트는 승인된 기간까지만 남지만 새 계정의 cloud context로 자동 전송하지 않음.
- cloud 동의 철회: 신규 요청 중단·cloud body/key 정리. 이미 공급자에 간 요청은 실제 취소·삭제됐다고 단정하지 않음.
- HistoryPermit 철회: sidecar 대화 본문/관련 키를 제거하고 최소 operation/commit 비내용 메타만 필요한 정책기간 보존. 기존 journal 문제/상태 이력 삭제와는 별도.
- 현재 로컬 프로필 선택은 강한 사용자 인증이 아니다. 위 private는 앱 화면·repository 범위의 차단이며 driver ID/암호화만으로 같은 앱을 쓰는 가족 사이의 강한 신원 격리를 보장하지 않는다. 민감 대화의 강한 보호가 필요하면 OS 사용자/앱 잠금의 별도 검증을 추가하고, 이 조건이 없으면 보관 opt-in에서 한계를 알린다.

### 5.4 공유 복구 키와 결과

ConversationStore의 PREPARED 행에 journal_commit_token, command_digest, owner_driver_id, target/revision을 저장하고 journal receipt와 같은 token/digest를 사용한다.

- PREPARED + 동일 receipt: receipt의 issue/revision/owner를 대조해 LINK_COMMITTED, 새 issue/분석/음성 없음.
- PREPARED + receipt 없음 + 원래 draft 있음: WAITING_USER_RESUME. 현재 운전자/안전/동의를 재검증한 명시 재개 전 commit하지 않음.
- receipt도 draft도 없음: ORPHANED_NEEDS_REVIEW, 다른 draft/현재 driver로 대체하지 않음.
- receipt는 있으나 sidecar 행 유실/복호화 실패: token 기반 최소 ORPHANED_HISTORY 메타만 복원하고 issue를 보존. 사라진 질문·답변을 재생성하거나 모델로 채우지 않음. 대화 이력 복원이 불가능한 사실을 표시.
- owner/digest/issue 불일치: LINK_CONFLICT로 차단, 원본과 오류 메타 보존, 자동 덮어쓰기/삭제 금지.

### 5.5 지속 저장 실패의 실행 경계

PREPARED/권한·동의 snapshot/frozen payload/assistant turn/재생 STARTED 같은 다음 효과의 선행 이력 쓰기가 실패하면 해당 자동 효과를 시작하지 않고 ERROR_PAUSED로 둔다. 이미 journal commit된 문제는 되돌리지 않으며 링크/메타 저장 실패를 별도 미완료로 표시한다. 복원시 이력을 기록하려다 새 cloud 작업이나 TTS를 재실행하지 않는다. 같은 DB의 이력 기록 실패에 대한 오류는 민감 본문 없는 비내용 상태로만 표시하고 사용자가 재개하도록 한다.

### 5.6 음성 출력의 exactly-once 한계

playbacks에STARTED를먼저durable기록하고재생,실제onDone뒤DONE. 하드웨어가말한직후프로세스가죽으면'들었는지'를원자확정할수없다. STARTED복원은UNCERTAIN으로두고자동재생하지않는다. **자동재생at-most-once와명시수동재생**을선택하며정확히1회청취보장을거짓으로약속하지않는다.

## 6. 차량 경고 이벤트·전달·대화 연결

### 6.1 입력과 episode

관측이원본DB에commit/readback된뒤만평가. key는로컬차량scope/storeUUID+source+kind+rule. 실차VIN이없으면차량전역ID라고주장하지않음.

- 정상/주의의업무episode와데이터품질은별개. ACTIVE→fresh해제값일때만RECOVERED. 실패/미지원/오래됨/권한거부/연결해제는GAP/UNKNOWN이며복구/수리완료아님.
- 현재DriveCheck의10분freshness와source/valid/supported/unit검사를그대로시작점으로사용. 온도/오일/기타신호고장규격을새로창작하지않음. 실차정책확인뒤신호별freshness를설정형으로확장.
- SOC는LowSocStore의기존stable사건/2%p해제여유/원관측참조를재사용. 새AttentionEventStore에다시SOCepisode를중복생성하지않고existing noticeID를adapter로참조.
- 다른4종Safety는실제registerCallback추가, 각capability/nullable/ERROR처리. 첫수신의현재주의상태는새'변화'라고허위표시하지않고현재주의발견으로표시.
- 같은값반복GET/callback/화면재그리기/운전자변경/날짜/재시작만으로새episode나새음성안내를만들지않음. 전체위험등급점수없음.

### 6.2 알림 원장

AttentionEventStore의episodes에는episodeId/rule/source/first/lastObserved/activeStatus/observationRef를저장하고, deliveries에는episodeId+channel+eventVersion의unique key와QUEUED/STARTED/DONE/SKIPPED/UNCERTAIN을둔다. acknowledgements는episodeId+driverId의읽음시각, snooze는재알림제안시각이다.

- 확인/무시는사용자행동이며센서해제아님. 읽음확인은수리/문제RESOLVED가아님.
- 기본은새episode당자동음성1회. 지속ACTIVE반복발화없음. 수동'다시알려줘'는별도명시delivery, 자동재알림은처음범위에서꺼짐.
- 정책변경/앱재실행/driver변경은무조건재발화안함. 새유효변화/새episode일때만기본재안내. driver별미확인표시는별도로가능.
- 포커스거부/다른세션바쁨이면queue1개+화면유지,현재성이사라진뒤지연발화하지않음. 여러알림은FIFO+짧은합산/한건선택이며임의의의학적위험순위/긴급사이렌없음.
- Android notification을별도로추가한다면채널/권한/내부화면PendingIntent는명시적/immutable, source·episode범위만. 잠금화면PII/다른운전자문장노출금지. 일반차량warning의HUN/소리보장은하지않음.

### 6.3 알림→대화

1. episode가ACTIVE/fresh이며사용자가음성안내opt-in,현재visible+ALLOWED/focusgrant면짧은로컬TTS. origin=LOCAL_RULE,모델0.
2. 자동후속청취까지별도허용됐을때만동일episode anchor로유한세션을연다. 그렇지않으면마이크는꺼진채로대화시작동작을제공.
3. '얼마나남았어'는해당episode의숫자와현재시점최신실제시도를구분해로컬응답. 세션사이사라진맥락을최근문제로몰래대체하지않음.
4. '어떻게해'는SOC면일반충전안내등로컬규칙가능. 복잡한원인은별도contract가허용한facts/질문만cloud. currentissue계약으로차량값을보내지않음.
5. '이내용기록해줘'는alert와발화에기초한사용자승인일반문장으로issue를commit. 원관측snapshot/alert/session을로컬link로연결,AI추정을사용자직접관측으로표기하지않음. 다음운전자는기존인계로확인.
6. vehicleepisode해제는문제수동상태를자동해결하지않음. 문제가완료돼도실제activeepisode를숨겨정상으로만들지않음.

## 7. Gemini 연결과 진짜 후속 대화

### 7.1 첫 문제 분석은 기존 경로

저장receipt후현재issue를읽고IssueAnalysisService.explain에위임한다. command의3~800자검증된전송사본·현재계정·동의·jobKey/revision을고정. 이미동일성공이있으면origin=SAVED로재사용할수있지만새분석이라고말하지않음. new/recovered/saved를turn metadata에분리. 결과검증실패시말하지않고기존성공을유지.

현재jobKey/계정/operation의보호를재개발하지않는다. 모든화면/자동대화/Gleo가동일single-flight/lease를공유. UI자동draw→요청금지,중복ASRcallback→새intent금지.

### 7.2 '그럼어떻게해?'의 세 가지 응답

A. 기존검증결과의checks/inspection을정확히다시읽는로컬경로: 새로운추론아님,새모델0.
B. 저장된같은issue결과의사실을명시적으로조회/요약하는규칙경로: 원문에없는답을추가하지않음.
C. 이전질문/답변을참고해새로운답을생성하는진짜multi-turn: **신규IssueDialogueContract/서버지원필요**. A/B만만들고C완성이라고보고하지않음.

### 7.3 신규 cloud 계약안

별도 contract=caritda-issue-dialogue-v1, source=USER_AUTHORIZED_ISSUE_DIALOGUE. fields는서버allowlist로엄격검증:
- selected_record: category, 승인된situation excerpt (ID/이름/위치/메모자동전송없음)
- question: 현재사용자질문
- context_turns: 선택한최근Q/A의role/origin/text (최대직전2쌍제안)
- facts: 기본빈배열. 센서facts는별도동의/typed validator로허용된kind/value/unit/품질/조회상대성만, 위치/차량ID/운전자제외
- reply 목적: 점검참고,output schema grounding_refs/answer/checks/inspection/uncertainty. 모든수치/fact참조는허용facts와검증하고고장/안전보증을거부.

rawJSON string내assistant지시/사용자문장도데이터로취급,시스템지시로실행하지않음. 과거assistant답변은증상사실이아니라ASSISTANT_INFERENCE로마킹. 질문전환은명시명령/확인으로새anchor와session segment생성. 숨은다른기록검색/원문통째전송금지.

현재Android4KiB본문·Worker8KiB/출력16KiB·provider출력token제한을먼저충족한다. envelope포함UTF-8 byte count가중요하며한글글자수만세지않음. 최대4KiB유지라면fields/최근턴을작게project하고초과시명시선택/최근성분리;조용한원문절삭금지. 한도확장은별도보안테스트를통과할때만.

필수후속수정:
- K/ai/PortableAiClient.kt의scope/endpoint/result검증 또는공통operationport추출(기존single-issue계약불변).
- **K/ai/AiExplanation.kt:55-60 endpoint allowlist**.
- infra/gemini-relay/src/portable-worker.ts routing, portable-gate.ts contract/hash/consent/provider dispatch, 신규portable-dialogue.ts validator/prompt/output.
- 기존provider-budget.ts/recurring-quota.ts/인증/서명/nonce/UNKNOWN예산원장재사용. 새로운context scope의동의·버전·사용량회귀.
- 이번조사에서Worker/운영모델/Secret/배포변경은0. 구현시서버코드와fixture테스트를작성해도운영배포는별도명시권한필요.

## 8. 503와 취소·복구 처리

| 상태/오류 | 보존할 것 | 다음 행동 | 금지 |
|---|---|---|---|
| 현재 구현의 provider MODEL_HTTP_503→ApiError(502)→FAILED | journal/이력/기존last, FAILED op/code | 로컬안내·같은issue유지,명시새시도또는세션scope의구체적허용안에서만 | 자동무한retry·임의환급·새분석위장 |
| 앱네트워크/Relay503 | frozenpending/intent/opid | 서버상태확인. RESERVED READ는최초dispatch가능함을인지 | '조회니까과금0'단정/새intent발급 |
| provider TIMEOUT/NETWORK/UNKNOWN | 예약·불확실상태 | 같은op복구/안내,새provider실행차단 | fallback모델로즉시재요청 |
| quota/modelOFF/동의없음 | issue/로컬이력 | 설정/동의안내또는로컬질문 | 관리자정책임의변경 |
| 응답검증불일치 | 이전성공/오류원인 | 검증실패로종료,AI음성0 | 모델문장을숫자사실로표시 |
| issue삭제/revision변경/driver전환 | 원래session소유자의작업metadata | 결과는원래anchor에만보존,현재세션표시/재생중단 | 새issue로응답귀속 |
| 사용자취소/백그라운드 | 이미commit된issue와이미발송가능한pending | localcancel기록/오디오중단,늦은결과는무발화로정리 | 서버요청이취소됐다고확정/원장삭제 |

이 표의 503 분류는 기준 커밋의 현재 매핑이다. 동시에 진행되는 Codex 수정에서 오류 상태가 바뀌면 최신 코드와 회귀 결과로 재판정하며 영구 불변 규칙으로 하드코딩하지 않는다. RESERVED는 기존 예약의 최초 dispatch만 허용할 수 있고, DISPATCHING/UNKNOWN은 재dispatch·대체 operation 생성 금지다.

기존성공설명은origin=SAVED/생성시각/revision과함께사용. 로컬fallback은origin=LOCAL_RULE. '이전설명을다시안내'와'새AI응답'을이력/음성에서혼동하지않는다. 대체모델은가격/allowlist/contract/output/consent가준비된후에만별도operation으로검토하며기본계획은자동fallback0이다.

## 9. 전체 세 흐름의 성공·독립 실패 경계

### A 음성 문제 자동화

start/permit → finite STT final+ended → interpret → freeze transcript/command → append PREPARED → atomicjournalcommit/readback → linkreceipt → approvedsingle-issueAI → validatedassistantturn → focusgrantedTTS → playbackterminal → followup.

저장실패는AI를막지만로컬대화/초안은유지. AI실패는이미저장된문제를취소하지않고로컬fallback. TTS실패는AI결과를버리지않고자동청취를멈춤. 최종후속대화가실패해도앞의issue/op/turns는보존한다.

### B 연속 대화

pinnedissue/revision+precedingturn → nextSTT → resolvepronoun/command → localretrieval OR approvedcloudprojector → operationperturn → validatecurrentanchor → appendanswer → TTS → followup. 새질문은새turn,같은callback재전달은같은turn. 다른문제전환은확인후새segment,driver변경은종료.

### C 차량 능동 알림

SDKcapability/subscription → realcallback → rawcommit → quality/freshness → episode/reconcile → VehicleAttention홈자동갱신 → permitted/focus TTS → optionalboundedlistening(anchoralert) → localnumericquery OR newcontract → explicitrecordcommand → journalreceipt+alert/sessionlink → existinghandover.

홈표시는음성/focus/cloud실패와독립. 상태감지와문제최종생성도독립이며자동고장진단/자동해결은하지않음.

### 공통 예외 매트릭스

| 사건 | A 자동기록 | B 후속대화 | C 능동안내 |
|---|---|---|---|
| 사용자취소 | commit전쓰기0,후면보존 | 마이크/출력중단,op보존 | 전달취소,episode유지 |
| mic권한거부/오류 | 원문없음/초안유지,AI0 | 기존이력/typed입력경로유지 | 홈유지,강제청취0 |
| SDK실패 | ASR실패를성공추정안함 | 로컬기록조회가능 | GAP,기존active를clear하지않음 |
| Gemini/네트워크오류 | 저장보존·op분류 | 같은anchor로로컬fallback | 실제값/홈유지,추론0 |
| 앱종료/프로세스종료 | receipt/linkrepair,자동재청취0 | frozenop/turn복원,명시resume | observer해제/gap,과거값재생0 |
| 중복callback | recognitionturn/token로1commit | turn/op1개 | observation/episode/delivery unique |
| 오래된data | 저장시각과증상발견시각구분 | 오래된맥락고지,새측정위장0 | 현재경고판정/발화차단,기록보존 |
| 운전자변경 | 원작성자frozen,변경시중단 | privatehistory/계정scope확인 | episode동일·driverack별도,새발화없음 |
| UXR제한전환 | 다음쓰기/AI/음성중단 | 재청취금지 | 구독해제·앱제한,OS정책존중 |

## 10. 성공 조건

제안구조가완료라고말하려면 단일기능pass외에 A/B/C의sameID연결·크래시복구·503/UNKNOWN·중복callback·focus/주행전환·개인정보분리를통합검증해야한다. 실제Gemini가없으면providerfixture로명시하고진짜modelend-to-end는미검증으로남긴다. Gleo외부실행여부가이로컬완료조건을대신하지않는다.
