# CARITDA Codex 자동화 구현 지시서

2026-11-12 KST · 기준 main c2757596397f96c3fe2fe45927dd025895ec13b3.

**이 파일은 구현을 위한 설계다. 이번 Aside 연구에서는 앱/Worker/DB/운영 설정을 수정하지 않았다.** 최신 Codex의 Gemini 오류 수정과 충돌하지 않도록 시작/게시 직전에 원격 main과 대상파일을 다시 비교한다. 사용자가 기능개발을 지시한 Codex 세션에서 실행한다. 실제모델 유료호출·운영배포·모델/예산변경까지 이 문서가 자동허용하는 것은 아니다.

필수근거: [FEASIBILITY](CARITDA_VOICE_AUTOMATION_FEASIBILITY.md), [ARCHITECTURE](CARITDA_VOICE_AUTOMATION_ARCHITECTURE.md).

## 0. 첫 작업·보존 게이트

1. 최신main HEAD/tree, AGENTS.md, docs/CODEX_PROGRESS.md, B_LIGHT_UI_ACCEPTANCE.md, DRIVE_CHECK_ACCEPTANCE.md, STT_AI_HANDOVER_ACCEPTANCE.md, GEMINI_RESPONSE_FIX.md, AI_STABILIZATION.md와아래실제소스를읽는다. c275759 이후변경은진행중503수정/음성변경/DB변경별로분리하고옛행번호를무조건적용하지않는다.
2. 기존사용자DB/files/prefs/APK와validationDB도비공개백업·현재수/전체원본·schema/version/integrity/FK비교. 과거24건/446검사숫자를기댓값으로쓰지않는다. 서버인증/키/개인텍스트를로그/연구/원격에게시하지않는다.
3. SDK Vehicle2.3.2/STT·TTS3.3.1/Fused1.1.3/Navi3.0.5, appId/서명/B안UI·기존태그·76dp·기존DB의미/CarSafetyGate를유지. Compose전환·SDK업그레이드·UI전면개편을섞지않는다.
4. 운영UI자동화/인자없는instrumentation runner/default prepare 금지. runner의정확한validation패키지검사와prepare전고유fixture조기분기를추가한뒤만시험. designOnly도안전한조기분기라고가정하지않음.
5. 이작업의첫MVP는visible+ALLOWED 사용자시작세션. FGS/BOOT/DO/특권/시스템hotword/새cloudsensortelemetry는추가하지않는다.

이력은 7단계에서 메시지·UI·보존 정책까지 완성하지만, **1단계부터 ConversationStore의 최소 세션/작업/재생 write-ahead 골격을 준비**한다. 3~5단계 자동 효과는 선행 영속화 없이 출시하거나 실행하지 않는다. 기록 실패는 ERROR_PAUSED이며 이미 commit된 journal은 보존한다.

## 1. 13단계 실행 순서

각단계작은diff/독립시험/실제결과보고. 새API이름은제안이며컴파일되는실제시그니처로구현해야한다. K=NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/.

| 순서 | 실제 작업·기존 재사용 | 파일·API/메서드 | 검증·완료 조건 |
|---|---|---|---|
| 1 자체대화진입 | 기존voiceDraft를삭제하지않고smallstart/stop세션추가. 권한/운전자/자동화scope확정 | MainActivity.kt:voiceDraft/onStop/driver변경; 새voice/VoiceConversationController.kt start/cancel | mic권한거부/UNKNOWN/RESTRICTED/foreground아님→시작0. 원문초안불변. 새대형Gleo메뉴0 |
| 2 STT명령해석 | 기존ON_DEVICE/Locale.KOREA/request·final+ended그대로. typed원문/길이초과추가 | integration/SpeechInput.kt / SpeechBackend.kt / SpeechDraftDelivery.kt, 새voice/VoiceCommandRouter.kt route | take800silenttruncate제거, partial/timeout성공0, 부정/인용/모호명령쓰기0, localquery모델0 |
| 3 음성자동저장 | 명시'기록해줘'또는opt-in증상모드. 초안저장→atomiccommit/readback | 새domain/DraftCommit.kt; CaritdaService.commitDraft, IssueRepository/SqliteIssueRepository.commitDraft; receipt표v4 | 같은token/digest1issue+1CREATED+1receipt, 다른digest충돌, driver/revision/draft오류rollback. 백업사본migration통과전기본OFF |
| 4 저장직후AI | 기존단일문제분석을controller에서호출. UIActivity의click을자동누르지않음 | IssueAnalysisService.explain/PortableAiClient/PortableSession, controller.startIssueAnalysis | 저장성공이선행,허용된3~800자category/situation만,고정intent/lease,모델fixture1회. quota/동의/UNKNOWN차단 |
| 5 결과자동TTS | 기존SpeechOutput/per-pieceplayback재사용,aggregate typedterminal+focus추가 | ExplanationPlayback.play(completed), SpeechOutput.speak/stop, controller.audioarbiter | DONE=마지막onDone만, cancel/fail/reject구분1회, mic/TTS겹침0,기존전체결과재생회귀 |
| 6 후속질문 | pinnedissue/revision/alert/parentturn로재청취. 우선local기존설명조회, 진짜생성은새계약 | controller.acceptFinal, router, 선택ai/IssueDialogueContract.kt/IssueDialogueService.kt | 같은issue귀속/대상없는'그럼'명확화. 로컬재생과신규생성구분. 새계약없이전체multi-turn완성주장금지 |
| 7 전체이력 | 사건/질문/AI결과/재생을메시지단위암호화sidecar에보관,repair | 새data/ConversationStore.kt appendOnce/stageOperation/reconcileLinks, controller | issue/turn/op/playbackID연결,crash후중복0·재청취0·account/driverprivate,새로고침긴문자열append0 |
| 8 차량변화감지 | SOC기존observer그대로, 다른Safety4종의publicsubscription추가 | PleosSocObserver/SocBackend/ObservationStore재사용, 새vehicle/PleosAttentionObserver.kt, DriveCheck.accepted/assess | 권한/capability/실callback분리,rawcommit후평가,null/ERROR/실패→해제0. 모든신호성공이라고보고금지 |
| 9 홈경고·알림 | 기존VehicleAttention/Main홈경로에episode연결,읽음/센서해제분리 | 새vehicle/AttentionEventStore.kt episode/delivery/ack/snooze, MainActivity재그림 | 반복GET/callback/driver변경/재실행으로새episode/음성0. Androidnotification은별도옵션/권한검사 |
| 10 차량경고→대화 | permittedlocalTTS후별도허용후속mic,anchor=episodeId,명시기록연결 | controller.offerAttention/start(anchor), storelinks, 기존snapshot/CaritdaService | SOC얼마남음은실제숫자로컬,동의없이cloud차량값0,읽음=해결아님,focus거부뒤오래된발화0 |
| 11 Gemini장애 | 최신503수정과대조한state/회귀,localfallback | PortableAiClient/Store/AiExplanation+controller, Worker변경은승인된개별phase | provider503 FAILED vs transport/Relay503 pending vs UNKNOWN. 기존last/issue/turn유지,자동fresh retry0 |
| 12 Gleo유지 | 기존3Action/Schema/trust/6초budget를공유domainadapter로유지 | CaritdaActions/GleoIssueAnalysis/GleoReplyChannel/ActionActivity/schema.json | peerpin추측0·implicitPIIbroadcast0,fixture통과를실Gleo로보고0. 동시UI/Gleo/controllerlease검사 |
| 13 실제통합검증 | 아래A~E,실제STT/TTS/SDK와fixtureprovider구분 | androidTest새VoiceAutomationChecks/VoiceAutomationLiveChecks + runner조기분기 | 데이터원본보존·build/lint·통합ID/503/수명주기통과,실차/유료E2E는미검증명시 |

단계1~2를통과한뒤세션사용성/음성중단부터확인. 단계3~5의모델은fixture로시험하고단계8~10은실제SOC부터검증한다. 진짜생성형후속계약은별도커밋/배포승인과함께개발한다. 기능플래그기본OFF로기존사용흐름을보존한다.

## 2. 자동화에서 반드시 구현할 불변식

- 단일controller actor/mainexecutor와worker결과marshalling으로상태전환직렬화. SDKlisteneridentity+session/generation/turn/owner/anchor검증. UI재그리기/회전은effect를재실행하지않음.
- STTfinal+ended둘다확인후만한번명령. 원문을silenttruncate하지않고기록1000/AI800한도를구분.
- 사전허용자동모드내명확한증상또는명시저장명령만commit. 일반대화/인용/부정/의문만으로기록0. 선택후운전시작확정은유지.
- token·canonicaldigest·owner·draftkind·target·expectedRevision검사를동일SQLitetransaction에서실행. v4receipt표만추가,기존열/행/이력이동·삭제금지. 기존주요UI저장도동일도메인API로점진전환하되별도회귀.
- journalcommit/readback전AI0. cloud허용은마이크/로컬저장동의와별개. 기존문제source/revision/nonce/기기/동의/budget/UNKNOWN유지.
- STT완료/해제→TTS focusgrant→마지막onDone→현재세션재검사→후속STT. 어떤실패/cancel/focusloss라도자동재청취0.
- TTS중음성중단을항상들을수없으므로화면/OS중단을보장. full-duplex/바지인/AEC완료주장금지.
- 대화sidecar는암호화·private scope. 기존계정cache를공용대화텍스트로복제해logout/철회를우회하지않음. 공유는승인된문제/요약만.
- 센서사실/사용자발화/AI추론을origin으로분리. priorassistant문장을센서사실로다시넣지않음.
- listener등록성공/GET수신/변화callback/자동홈/자동TTS/사용자청취는별도성공단계.
- 재생STARTED뒤crash는UNCERTAIN,자동재생금지. 하드웨어발화exactly-once보장대신at-most-once자동전달.
- onStop/제한/driverchange/계정change/revoke/종료/focusloss는generation무효화+mic/output/observer종료. cloud호출이미진행된원장은삭제/환급하지않음.

모든 자동 commit/cloud dispatch(RESERVED READ 포함)/TTS/후속STT/queued alert는 실행 직전 현재 visible+ALLOWED와 owner/account/permit/generation을 재검사한다. 비내용 상태·receipt/link 복구는 새 issue/유료호출/출력/청취를 절대 발생시키지 않는 bookkeeping으로 분리한다.

## 3. 진짜 Gemini 후속 대화 단계의 필수 변경

단일IssueAnalysisService의기존checks/inspection을로컬로다시읽는것과새답변생성은다르다. 새cloud계약을만들기전에는'연속음성조작/기존설명조회'라고보고한다.

### 필수 코드

- K/ai/IssueDialogueContract.kt: 새contract/scope,허용recordexcerpt/question/최근최대2쌍Q&A/facts검증,UTF8 byte budget,origin/grounding refs.
- K/ai/IssueDialogueService.kt: pinnedrevision/current계정/turnseq,동일turnoperation재개,결과검증·출처.
- K/ai/PortableAiClient.kt: 기존operation엔진공유/새scope만추가. single-issue/soc계약회귀. pending은계정+contract+turn범위와전체UNKNOWN제약을모두반영.
- K/ai/AiExplanation.kt의고정pathregex: 새endpoint를최소범위로허용. arbitraryURL/HTTP/리디렉션허용금지.
- infra/gemini-relay/src/portable-worker.ts,portable-gate.ts와새portable-dialogue.ts: route/계약/동의/hash/safeproviderprompt/strictoutput. 기존provider-budget/recurringquota/인증서명재사용.
- infra/gemini-relay/test/: 기존issue/soc/nonce/replay/receipt/UNKNOWN/usage회귀+dialogue injection/byte/grounding/turntests. 모든모델호출은fixture또는offline stub.

### 모델 장애/재시도 기본 정책

provider MODEL_HTTP_503는현재FAILED/시도소모다. Relay503·앱network는pending유지일수있다. READ RESERVED는기존예약의최초dispatch를실행할수있으므로'resume=모델0'을전제하지않는다. DISPATCHING/UNKNOWN은재dispatch하지않고전체UNKNOWN신규요청차단을보존.

첫릴리스자동fresh재시도0. '다시분석해줘'등명시음성지시도새요청비용/잔여/현재동의를확인하고실행. 향후자동재시도는사용자/서버정책이구체적으로허용한최대시도/비용·backoff+jitter범위로만추가하고숨은PythonSDK/HTTPclient자동retry가새dispatch를늘리지않게검사. 대체모델/예산증액/모델ON변경/원장초기화/실배포금지.

## 4. 상태 영속화와 크래시 시험 지점

P0 before ASR final, P1 final후해석전, P2 commandPREPARED후, P3 journaltransaction중, P4 journalcommit후sidecarlink전, P5 AIintent저장후reserve전, P6 reserve후dispatch전, P7 dispatch도중, P8 response검증후turn저장전, P9 playbackSTARTED후, P10 onDone후DONE쓰기전, P11 driver/issue변경중.

각지점에서고유fixture프로세스를종료/재시작. 기존DB는초기화하지않고유일token/receipt·원문·event·pending·operation·cache·재생상태를비교한다. 기본복원은화면/이력과상태만,자동mic/TTS/newpaidrequest없음. RESERVED회수도사용자재개/허용조건없이는실행하지않음.

## 5. A~E 실제 검증 시나리오

### TEST A 음성 기록 자동화

전제: [TEST] validation 고유DB, 운전자명시확정, 정차ALLOWED, ON_DEVICE mic권한, RECORD_EXPLAIN_SPEAK의 로컬 저장·해당 payload cloud·자동 TTS 범위와 별도 HistoryPermit(텍스트/응답/재생 이력 보관 기간), 후속 자동 청취 허용을 각각 확인, provider fixture. 전체 자동화 성공 판정은 이 허용들이 모두 존재할 때만 한다.

1. 사용자가대화시작1회. '카잇다, 차앞쪽에서소리가나.' 실제발화. 시스템hotword가아니라활성세션입력임을표시.
2. 실제SDK partial/final/ended trace를메타만기록. original text는비공개fixture에암호저장하고로그출력금지.
3. 유형+원문+확정driver+발견시각+tokenfreeze. receipt전AI요청0, afterreadback같은 issueID로1번fixture분석.
4. 준비된전체결과를실제PleosTTS로재생,실제onDone/중단/실패별도로검사. 사람청취확인은별도.
5. 같은final/ended중복·역순·늦은callback주입→issue/CREATED/receipt/AIop/autoPlayback각1.
6. 인용/부정/일반발화/driver미확정/기존dirtydraft/초과길이→자동쓰기0. 새command본문으로같은token재사용→CONFLICT.

성공: 수동저장/AI/듣기버튼을추가누르지않고sameIDchain이연결됨. fixtureAI를실Gemini라고표기하지않음. 실제모델E2E는별도최대호출/비용승인후에만.

### TEST B 후속 음성 대화

1. A설명의최종onDone후자동후속청취허용일때만다음STT. '그럼어떻게해야해?' 실제발화.
2. 동일session/issueRevision/parentTurn;기존checks를로컬로읽는경로는NEWAI0·originLOCAL_RULE/SAVED. 신규dialogue계약경로는별도fixture응답1/operation1.
3. '정비소가야할까?'→같은anchor,안전확정표현거부. '다른문제말할게'→확인후새segment. 대상없는대명사→명확화,최근문제임의선택0.
4. Aissue응답대기중Bissue전환/본문수정/삭제/계정전환→A응답을B로표시/읽기0.
5. '나중에다시볼게'→sessionEND·mic0·TTS0. 재시작이력seq/operation/source복원,자동발화0.
6. TTS음성과동일한문장녹음/재입력fixture를주입해SPEAKING중저장/AI0;실제잔향/마이크종료도별도확인.

### TEST C 실제 차량 상태 자동 알림

1. 기존validation/공식Console연결을확인하고사용자SOC기준예20%를명시설정. 과거낮은값재생없이새SDK18callback수신.
2. GET결과/구독등록/실변화callback을각각기록. 원관측commit후기존SOCnotice1/홈card1.
3. 18반복GET/동일callback/재그림/회전/재실행→새episode/음성0. 최초ACTIVE음성허용+focusgrant일때만localTTS1.
4. 자동후속청취별도허용후 '얼마나남았어?'→실제18%와수신시각로컬안내,AI0. alertID와turn연결.
5. '이내용기록해줘'→기존안내문장/발화확인범위의issue1+snapshot/alert/sessionlinks,다른운전자의명시읽음·점검상태별개.
6. 45callback→센서조건해제,문제RESOLVED자동변경0. 단절/오래됨/null/권한거부는clear0.
7. 네Safety신호는먼저합성 fixture로pipeline검사,지원되는실제신호만callback/변화별실행. 미지원엔진오일/브레이크액/워셔를실제감지했다고쓰지않음.
8. 시험후Console100/P/0와실제SDK100/safetyALLOWED 복원을직접확인. 기존사용자위치/경로/운영DB손대지않음.

TEST A의 실제 STT/실제 TTS callback, provider fixture, 사람의 발화/청취 확인을 각각 별도 PASS 열로 기록한다. TEST C의 실제 SDK callback은 실제 Pleos backend에서 생산되어 원관측의 source=PLEOS_EMULATOR_SDK/등록 listener 경로가 확인된 것만 인정한다. Console 표시값 변경이나 합성 callback 주입만으로 실제 수신 PASS를 만들지 않는다. 실차는 별도 미검증 열이다.

### TEST D Gemini 오류와 비용 안전

fixture마다원본문제/기존성공/cache/usage/pending를시작전후비교한다.

| 주입 | 기대 |
|---|---|
| providerHTTP503 | MODEL_HTTP_503/FAILED,issue/last유지,해당실패메타저장,자동fresh0 |
| RelayHTTP503/앱network | frozenpending유지,새intent0,동일op복구가능 |
| provider timeout | UNKNOWN·예약유지·새요청차단·무환급 |
| RESERVED READ | 기존예약의최초dispatch가능,추가reserve/dispatch중복0 |
| DISPATCHING/UNKNOWN READ | 새providerdispatch0 |
| quota0/modelOFF/consentrevoked | provider0,로컬저장·조회·허용TTS는정상 |
| malformed/grounding실패 | 새성공저장/발화0,이전성공유지 |
| 늦은성공/사용자취소 | 원래operation에만정리,현재session음성0 |
| user새retry명령 | 별도검증된허용범위/한도안새op1,기존op를성공으로위장0 |

로컬fallback문장에는'새AI설명을받지못했어요/기존설명을다시안내'를출처에맞게써야한다. 대체모델시험은배포없이fixtureallowlist/가격/응답contract검사만한다.

### TEST E 주행·앱·오디오 상태 전환

- LISTENING/INTERPRETING/SAVING/AI_WAITING/SPEAKING/FOLLOWUP각상태에서UNKNOWN/RESTRICTED/onStop/permissionrevoke/driverchange 주입.
- 늦은callback무시,다음쓰기/paidrequest/자동mic0,이미committedissue/기존이력보존. transaction중종료도원자성검사.
- 실제마이크거부·오디오입력무음/다른앱경합,media/nav/통화/Gleo focusloss/거부를별도검사. 시험앱이긴급/통화를사칭하지않고플랫폼지원된시험경로만사용.
- TTS중STOP은CANCELLED,watchdogFAILED,focusdeniedREJECTED. 소리끝난것처럼시계로DONE만들지않음.
- background/재시작후사용자resume전mic/TTS0. 일반APK의DO/FGS/boot권한을늘려통과시키지않음.

## 6. 빌드·시험 결과 보고 형식

최신프로젝트task를확인해 assembleDebug/assembleValidation/assembleValidationAndroidTest/lintDebug/testDebugUnitTest를현재허용환경에서실행. 기존SDKcache/서명/appId를유지한다. 설치/권한확대/새SDK설치는기존허용범위를확인하고없으면막힌부분을명시. NO-SOURCE는실제테스트통과건수아님.

고유fixture조기분기예: voiceAutomationOnly, voiceAutomationColdOnly, voiceAutomationLiveOnly. **신규제안명**이며현재runner에이미있다고가정하지말것. 정확한validation패키지guard뒤·기본prepare전분기. 각분기새assertions/fixtureSDK/실제SDK/사람발화·청취/실모델을다른열로기록한다.

완료보고필수:
- 시작/끝main SHA,수정파일/커밋·다른작업보존.
- 구현완료/fixture통과/실제에뮬레이터/일반사용자/외부Gleo/실차별판정.
- A/B/C same issue/session/turn/operation/episode linkage,중복수/오류복구/원본보존.
- 실제유료호출/배포/운영설정변경횟수(이번기본0).
- 남은제약: 시스템wakeword/주행전체사용/미지원신호/전체multi-turn/실차를솔직히명시.

## 7. 복사 가능한 Codex 최종 프롬프트

~~~text
CARITDA의 Gleo 독립 음성·차량 자동화 기능을 구현하라. 먼저 최신 원격 main, AGENTS.md, CODEX_PROGRESS와 docs/research/CARITDA_VOICE_AUTOMATION_FEASIBILITY.md, CARITDA_VOICE_AUTOMATION_ARCHITECTURE.md, CARITDA_AUTOMATION_CODEX_PLAN.md를 전부 읽고 c275759 이후의 Gemini 오류 수정·현재 작업과 비교하라. 기존 연구/다른 작업을 덮어쓰지 마라.

목표는 사용자 시작 또는 이미 활성화된 앱 내 음성 세션에서 STT→명확한 명령/허용 증상→현재 운전자에 대한 atomic 자동 저장→기존 단일 기록 Gemini 분석→전체 결과 자동 TTS→후속 질문→메시지/작업/재생 이력 보관이다. 별도 경로는 실제 Vehicle callback→유효·현재성/원관측 commit→중복 없는 episode/홈 안내→허용된 local TTS→같은 alert 문맥의 질문→명시 기록·기존 인계다. 시스템 어디서나 '카잇다'로 앱을 깨운다고 가정하지 마라.

PLAN의13단계를 작은 커밋으로 순서대로 수행하되, 첫배치는 단계1~2의로컬유한세션과안전·취소·명령fixture부터다. 이어3~5를providerfixture로연결하고SOC부터검증하라. 개별STT/TTS/Gemini성공만으로통합자동화완료를선언하지마라. 실제multi-turn은기존situation에이력을끼워넣지말고명시적인새dialogue계약/서버검증/동의·예산을개발해야한다. 아직그계약이없으면로컬기존설명재생과진짜신규추론을구분하라.

기존SpeechInput/SpeechBackend의ON_DEVICE·Locale.KOREA·final+actualended·cancelgeneration을유지하고take800 silenttruncation을명시길이오류/원문보존으로바꿔라. 기존SpeechOutput/ExplanationPlayback에typed DONE/CANCELLED/FAILED/REJECTED 및한번의terminalcallback을보강하라. 단일audioowner/focusgrant를확인하고STT와TTS를동시에실행하지마라. TTS최종onDone뒤현재session/generation/owner/anchor/permit이유효할때만다음유한STT를시작하라. TTS중음성중단을항상들을수있다고약속하지말고화면/OS중단을유지하라.

자동저장은명시저장지시또는사전허용한증상자동화모드에서만한다. 부정/인용/일반대화/모호한대상/미확정운전자/dirtydraft는쓰기0이다. 안정turntoken과canonicaldigest·owner·revision·draft검사를동일SQLitetransaction에서수행하고issue+event+draft소비+journal DB의 receipt만원자화하라. 기존행/열/작성당시이름/발견시각/4수동상태/인계/논리삭제는보존하라. 새대화는암호화sidecar로메시지/operation/playback/links를분리하고crossDB원자성을가정하지마라.

저장receipt/readback전AI는0이다. 기존IssueAnalysisService/PortableAiClient/Firebase/Keystore/기기등록/동의/nonce/할당량·비용예약/pendingUNKNOWN/singleflight를재사용하라. 마이크/저장opt-in을cloud동의로간주하지마라. 차량값·위치·이름·메모·다른기록/전체대화를기존category/situation에자동전송하지마라. 진짜dialogueendpoint추가시AiExplanation.kt의pathallowlist와4KiB UTF8 request·Worker/결과한도를함께검증하라.

MODEL_HTTP_503는현재provider확정FAILED이고Relay503/앱network/UNKNOWN과다르다. 실패시원본문제/기존last/이력을보존하고localfallback으로세션을유지하라. operationREAD는RESERVED일때기존예약의최초dispatch가가능하므로모델0이라고단정하지마라. UNKNOWN/DISPATCHING을새op로재실행하지말고자동freshretry/모델fallback/예산변경/원장초기화는하지마라. 실제유료호출·Worker배포·Secret/모델정책변경은별도명시허용전0이다.

SOC는기존PleosSocObserver/SocBackend/LowSocStore를재사용하고새중복observer를만들지마라. Safety4종은공식2.3.2 public capability/get/register/unregister와동일listener해제를검증해개별추가하라. 워셔API의Waring철자는실제정의다. 미지원/null/ERROR/실패/오래됨은정상·조건해제가아니다. 타이어저압기준은실압이아니며PRIVATE_CAR_SAFETY/Telltales우회는금지다. sensor해제/사용자읽음/문제해결을분리하라. episode/delivery원장으로중복음성과재시작재생을억제하라.

기존B안UI/76dp/SDK버전/서명/appId/기존Gleo3Schema·Action/ReplyChannel신뢰gate를유지하라. 빈Gleopeer/pin을추정하지말고implicit민감정보broadcast를금지하라. 현재visible+CarSafetyGateALLOWED를유지하고onStop/제한/권한회수/운전자·계정변경/종료/focusloss에서마이크·TTS·observer를중단하라. DO/시스템권한/FGS/BOOT/호출어우회로통과시키지마라.

운영앱UI자동화·인자없는계측defaultprepare·기존validationDB초기화금지다. 백업/현재전체원본비교후정확한validationpackageguard+prepare전고유fixture조기분기에서만PLAN의A~E/crash/중복/503시험을수행하라. 실제SDK·합성callback·providerfixture·사람발화/청취·실모델·외부Gleo·실차를분리해보고하라. 미검증소스를완료로게시하지말고최신main을다시확인해이번검증된명시파일만추가하라.
~~~
