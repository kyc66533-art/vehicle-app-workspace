# CARITDA 음성 자동화 통합

## 2026-11-12 16:30 +09:00 — 잔여 통합 결과

이번 기준은 main `a9c2b29c66ccff3b5a0bd2381113834e731eb5a2`이다. 로컬 공통285파일과 원격314 blob을 대조했으며 원격 전용 연구29개를 보존한다. 아래 과거 보고의 배포0/모델0은 이전 작업의 결과다.

**신규 대화 API 운영 배포, 실제 신규 Gemini 분석·새 후속 답변, 실제 전체 TTS와 영속 재조회는 완료했다. 홈 자동 대기 경로는 구현·격리 검증했지만 실제 호출어 인식까지 이어지는 사람 발화 시험은 완료하지 못했다.** 독립 Vosk 호출어 실험은 실제 발화에서 실패했으므로 일반 APK에서 제외했다. 전체 버튼 없는 음성 자동화 완료로 판정하지 않는다.

### 홈에서 시작하는 경로

운전자 확정 → 홈 → 사전에 허용한 음성 대기 → ‘카잇다’의 final+실제 ended → ‘말씀하세요’의 실제 TTS DONE → 증상 입력 → 기존 원자 commit/receipt → 기존 분석 서비스 → 전체 TTS → 후속 STT다. 음성 Activity를 열거나 시작 버튼을 누르는 과정은 홈 경로의 필수 단계가 아니다. 기존 화면은 **음성 대화·설정·이력**이라는 수동 대체 경로로 유지한다.

- 운전자·Firebase 계정별 VoiceSettings에 `autoWait`만 일곱 번째 bit로 추가했다. 기존 여섯 선택과 동의 범위를 바꾸지 않았으며 모두 기본 OFF다. 마이크 권한, 보관, 자동 기록, 전송, 읽기, 후속 질문, 차량 안내는 각각 선택한다.
- 일반 APK와 Android Studio 기본 빌드는 공개 Pleos STT의 **30초 대기창**이다. 침묵·오류·호출어 불일치 뒤 자동 재시작하지 않는다. 한 전면 진입에서 자동창 최대3개이며 정상 대화 종료 시 다음 창을 사용할 수 있다. 시간 종료 후에는 ‘다시 대기’가 필요하다. 이것은 상시 독립 호출어가 아니다.
- 대화의 기존5분/5발화 제한은 대기창과 별개다. 실제 SDK started/마이크 상태가 확인돼야 ‘음성 대기 중’으로 표시한다. ‘음성 대기 꺼 줘’는 설정 자체를 OFF로 저장한다.
- 화면 이탈·백그라운드·운전자/계정 변경·권한/안전 제한·오디오 포커스 상실은 종료한다. TTS와 입력은 오디오 소유권을 넘겨 사용하며 타이머로 DONE을 만들지 않는다. 포커스가 돌아와도 자동 청취를 재개하지 않는다.
- 공개 STT AAR의 AmbientAi 호출어 설정은 `@PrivilegedApi`다. 호출하지 않았다. 실제 Android on-device SpeechRecognizer 제공 여부는 false였다. 기본 비서 역할은 제공true/보유false, 현재 OS 비서는 `ai.umos.ambientai`였다. 시스템 전체 호출어·PTT·외부 Gleo는 미검증이다.

### 독립 호출어 실험의 실제 한계

사용자 승인으로 공식 Vosk0.3.47와 한국어 small0.22 모델 약82MB를 프로젝트에만 받았다. 공식 Apache2 라이선스/고지, APK 배포 가능 범위, checksum과 오프라인 AudioRecord16k mono를 확인했다. 원본 음성 파일은 저장하지 않는다. 다운로드 도구는 `scripts/Prepare-HomeWakeEngine.mjs`이며 빌드 중 다운로드하지 않는다.

실제 마이크·native 모델 초기화·PCM 수신·해제는 성공했다. 그러나 첫 발화에서 사용자가 ‘말했지만 안내가 없었음’이라고 확인했다. 모델 사전에 ‘카잇다’가 없어 지원 단어 ‘카/잇다/있다’ 조합을 제한적으로 시험했지만 두 번째180초 시험도 2,883,200sample/582,228nonzero/35segment/호출매치0이었다. 임의 발음 별칭으로 성공을 만들지 않았다. CPU/전력 효율·인식률·실차 성능은 미검증이다. 선택 빌드에는16KiB native 정렬 경고도 남는다.

따라서 `-PcaritdaHomeWakeEngine=true`는 검증용 실험이고 기본false다. 일반 설치 APK에는 모델·libvosk가 없음을 별도 확인했다. 이후 공개 Pleos 대체 경로의 실제 사람 발화는 사용자가 ‘이번 발화 시험은 나중에’를 선택해 마이크 시작 전에 중단했다. 독립 호출어 FAIL과 대체 경로 사람 발화 NOT_TESTED를 구분한다.

### 이번 Kotlin 변경

공통 경로는 `NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/`다. 기존 기능 전체를 재작성하지 않았다.

| 수정/추가 파일 | 이번 변경 |
|---|---|
| `MainActivity.kt` | 홈 inline 음성 상태/중지/재대기, 운전자·인증·안전·수명주기 연결, 차량 안내의 기존 사건을 같은 홈 제어기로 전달 |
| `voice/HomeVoiceRuntime.kt` | 기존 Controller/AIbridge/SpeechInput/Output를 홈에 연결, 대기 수명·포커스·generation·실제 마이크 상태 |
| `voice/VoiceIssueContext.kt` | 기존 음성 화면의 저장/분석/대화/로컬 조회 어댑터를 공유. 로컬 차량 답변을 Gemini 문맥에서 제외 |
| `voice/VoiceSettings.kt`, `VoiceConversationActivity.kt` | 기본OFF 자동 대기 옵션·마이크 권한·기존 수동 시작/이력/계정 경로 |
| `voice/VoiceConversationController.kt`, `VoiceCommandRouter.kt` | 완료된 호출을 기존 대화로 전달, prompt DONE 후 증상 입력, 명시 청취OFF·한국어 띄어쓰기 처리 |
| `voice/VoiceListeningFocus.kt`, `HomeWakeDetector.kt` | 공개 오디오 포커스·선택 detector 경계. 기본 factory는 detector를 만들지 않음 |
| `data/ConversationStore.kt` | 호출문 출처 ASR_WAKE 허용. 기존 DB v4/암호화 sidecar 스키마·보관 정책 유지 |
| `PortableAiActivity.kt`, `ai/auth/FirebaseSessionProvider.kt`, `ai/PortableAnalysis.kt` | 기존 계정의 최근 확인만 갱신. 로그아웃/키교체 없이 공식 Firebase reauthenticate, 비밀번호 저장/로그 없음 |
| `src/homeWake/...VoskHomeWakeDetector.kt`, `HomeWakeDetectorFactory.kt`, `src/noHomeWake/...HomeWakeDetectorFactory.kt` | 선택 실험/기본 빌드 분리, 무음/실패를 성공으로 만들지 않음 |

검증은 새 HomeVoiceChecks/Platform/Microphone/Recovery/ModelResult/VoiceReauth와 기존 runner/VoiceAutomationUiChecks/VoiceRealModelChecks/VoiceRelayChecks를 변경했다. build.gradle.kts의 선택 엔진 설정 이외 기존 SDK 버전·서명·Firebase 설정·Manifest/Gleo 코드는 유지했다. `SpeechInput`, `SpeechOutput`, `VoiceAiBridge`, `IssueDialogueContract/Service`는 이번에 재작성하지 않았다.

### 실제 Worker와 모델

Codex는 `CodexSandboxOffline` 계정이며 Cloudflare 제어 API는 EACCES였다. 사용자의 정상 CMD OAuth 인증을 복사하거나 재로그인을 반복 요구하지 않았다. 사용자가 같은 기존 인증으로 검증된 배포 도구를 실행했다. 세 번 모두 버전/번들 SHA/같은 binding·Secret·DO·모델ON·한도 지문을 검사한 기존 Worker의 코드 배포다.

| 배포 | 실제 활성 버전 | 목적 |
|---|---|---|
| 1 | `fb39412c-11f2-4080-b30f-b4751d71731f` | 기존 분석을 유지하며 `/v2/issues/dialogue` 노출 |
| 2 | `b0f65833-546d-4e5c-9a0c-4a5071f45ce7` | 예약 전 RATE_LIMIT의 unbound ISSUED 메타데이터 READ 복구 |
| 3·최종 | `37c6cab1-deb8-42ab-a0db-baa3614e44ae` | 모델 context_refs schema를 실제 이전 assistant 인덱스로 제한 |

최종 번들 SHA256 `4643c387ccce7a9056f12a3874a23abf3a75eea79e68779994c94b600498ca19`/99,641byte다. 실제 에뮬레이터 HTTPS health200·모델ON·기존 분석401·신규 대화401을 확인했다. 신규404는 해결됐다. 배포 도구 자체는 모델을 호출하지 않는다.

READ 수정은 인증·서명·기기·operation 소유권 확인 뒤 attempts0/hash없음/consent없음인 ISSUED에 빈 메타데이터만 돌려준다. 이미 묶인 결과의 동의 보호는 유지한다. 후속 답변 수정은 검증기를 완화하지 않고 공급자 schema의 context_refs를 실제 assistant 인덱스 enum으로 제한했다. [공식 structured output의 enum/minItems/maxItems](https://aistudio.google.com/docs/structured-output#json-schema-support)를 사용한다.

실모델은 최초 승인2회와 추가 명시 승인1회를 사용했다. 첫 신규 분석은 성공했고 자동 전체 TTS DONE이었다. 첫 후속은 분당 제한/READ 오류를 수정해 같은 operation으로 이어갔지만 공급자 응답이 MODEL_CONTEXT_INVALID여서 표시하지 않았다. 새 schema 배포 뒤 추가 승인1회로 **같은 문제/revision·질문·이전 AI를 사용한 새 Gemini 답변과 전체 실제 TTS DONE**을 확인했다. 첫 설명 재생으로 대체하지 않았다. 공급자 시도는 총3회이며 성공 설명2개다. ASR 입력은 명시된 fixture였다. 추가 승인1회는 별도 원장이고 기존2회 원장은 변경하지 않았다.

실제 계정 상태는 portable attempts8→11, 잔여3→0, uncertain=false다. 원장·예약·일5/월100/서비스300/비용 정책은 초기화하거나 늘리지 않았다. 추가1회 계획 비용은 약US$0.0075/예약US$0.01로 안내했으며 실제 청구서를 확인한 금액은 아니다. [공식 모델 단가](https://aistudio.google.com/docs/pricing#gemini-3.5-flash-lite)와 기존 비용 보호를 유지한다. 더 이상 유료 요청을 실행하지 않는다.

### 전송과 영속 보관

같은 문제의 증상 종류/본문·현재 질문·최대2쌍의 이전 **검증된 AI** Q/A만 전송한다. 이전 AI는 추론이고 facts는 빈 배열이다. 중간 SOC/차량 로컬 질문·답변은 문맥에서 제외하는 회귀를 추가했다. 차량·위치·이름·메모·다른 기록을 추가하지 않는다. 네 수동 상태·원문·receipt·기존 캐시/pending/UNKNOWN을 유지한다.

새 프로세스에서 같은 미해결 문제1건/revision1, 암호화된 실제 AI 답변2개, 성공 operation2개, prompt/분석/후속의 전체 TTS DONE을 다시 읽었다. ‘정비소에 가야 할까?’는 이 실제 저장 문맥으로 3500byte 계약만 검사했고 네 번째 모델 요청을 하지 않았다. 최초 실패와 새 성공 이력을 함께 남겼다.

이번 선택 Android 검사·일반 업데이트·841DB 보존과 예외·남은 실제 발화는 [최신 인수 결과](CARITDA_VOICE_AUTOMATION_ACCEPTANCE.md)를 따른다. SOC18/45 실제 callback·추가 Safety 지원 결과는 이전 작업의 근거를 유지하며 이번 fixture를 새 SDK 성공으로 세지 않는다.

<details>
<summary>이전 작업 기록 — a9c2b29c 게시 당시 결과(이번 검증과 별도)</summary>

작성 기준: 2026-11-12 KST. 개발 기준 main `d27cc79dc3ea70687065107ca5e408dbcb085421`. Aside 연구 3개를 전체 검토하고 실제 최신 소스에 맞춰 구현했다. 기존 B 라이트 화면·수동 입력·AI 설명·Gleo 경로를 유지한다.

**앱 코드와 격리 통합 시험은 구현했다. 신규 Worker 대화 API의 운영 배포 및 실제 모델 대화는 아직 완료하지 못했다. 실제 STT 발화는 사용자가 나중에 직접 확인하기로 했다.** 따라서 전체 실사용 자동화 완료로 판정하지 않는다. 상세 결과는 [인수 보고서](CARITDA_VOICE_AUTOMATION_ACCEPTANCE.md)를 따른다.

## 실제 변경된 사용 흐름

홈의 **음성 대화 → 음성 설정**에서 운전자·AI 계정별 허용 범위를 선택한다. 모든 자동화 선택은 기본 꺼짐이다. 증상 자동 기록, Gemini 전송, 자동 읽기, 후속 질문 듣기, 30일 대화 보관, 새 차량 안내 읽기를 각각 선택한다. 전송 동의 준비는 증상을 전송하거나 모델을 실행하지 않는다.

한 번 대화를 시작한 뒤, 명확한 증상 또는 ‘기록해 줘’ 명령을 인식하면 SQLite 저장 영수증을 확인하고 기존 분석 서비스로 연결한다. 준비된 전체 설명의 실제 TTS DONE 후 다음 STT를 시작한다. 매 질문마다 저장·AI 생성·듣기를 누르는 방식은 사용하지 않는다. 기존 수동 ‘증상 남기기 → 저장하고 AI 설명’도 유지한다.

- 음성 세션은 앱이 보이고 정차·안전 허용 상태일 때 최대 5분·사용자 발화 5회다. 끝내기 버튼은 본문 스크롤 밖에 있다.
- ‘카잇다’는 이미 시작한 STT 세션에서 인식하는 명령이다. 마이크가 꺼진 상태의 독립 호출어 감지나 앱 밖 실행은 구현 성공으로 주장하지 않는다.
- 대화가 끝나거나 화면을 벗어나면 자동 재시작하지 않는다. STT 오류도 무한 재실행하지 않는다.
- 반복 설명·SOC·차량 안내·최근 기록·인계 조회는 로컬 처리한다. 새 문제 분석과 같은 문제의 새 후속 조언만 Gemini 대상으로 보낸다.
- 증상·AI 답변·재생 결과는 같은 문제/대화/내용 버전에 연결한다. 다른 운전자에게는 기존 문제 인계만 제공하며 개인 AI 대화 전체를 자동 공유하지 않는다.

## 구성과 수정 Kotlin 파일

아래 경로의 공통 접두사는 `NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/`다.

| 경로 | 실제 변경 |
|---|---|
| `MainActivity.kt` | B 홈/설정의 음성 진입, 가시·안전 상태의 실제 신호 감시, 새 안내 자동 연결, 안내 확인과 상태 해소 분리 |
| `VoiceConversationActivity.kt` | 설정·시작/종료·인식/저장/AI/재생/이력 UI, 기존 인증·수동 AI 화면 연결 |
| `PortableAiActivity.kt` | 기존 AI 화면과 새 음성 경로 사이의 개인정보 정리·동시 요청 보호 |
| `voice/VoiceConversationController.kt` | 상태 전이, 저장→AI→전체 재생→후속 발화, 세션/문제/버전 고정 |
| `voice/VoiceCommandRouter.kt` | 한국어 명령 해석, 명시 저장/허용된 증상만 저장, 단순 조회 로컬 분기 |
| `voice/VoiceAiBridge.kt` | 기존 인증·단일 분석·신규 대화 서비스, 동의 준비, 원래 요청 복구 |
| `voice/VoiceSettings.kt`, `VoicePrivacy.kt` | 운전자+AI 계정별 기본 OFF 설정, 동의 철회·30일 보관 정리 |
| `voice/VoiceSessionOwner.kt`, `VoiceRateWindow.kt` | 한 자동화 세션, 예약 전 분당 제한에만 원래 요청으로 한 번 대기 |
| `voice/WholeExplanationPlayback.kt` | 기존 전체 설명 재생기의 typed 결과 어댑터 |
| `voice/CaritdaVoiceInteractionService.kt` | 선택 가능한 표준 비서 Service/Session 경로, 특권·백그라운드 호출어 없음 |
| `integration/SpeechInput.kt` | 기존 ON_DEVICE·한국어·final+실제 ended·취소/generation 보존, 앱 오디오 소유권 연결 |
| `integration/SpeechOutput.kt` | 실제 DONE/CANCELLED/FAILED/REJECTED, 오디오 포커스·동일 Listener 해제 |
| `integration/ExplanationPlayback.kt`, `VoiceAudioOwner.kt` | 기존 전체 문장 재생 재사용, STT/TTS 반이중 제어 |
| `domain/DraftCommit.kt`, `CaritdaService.kt` | 음성 명령별 불변 commit/receipt, 기존 안전·운전자·상태 의미 유지 |
| `data/IssueRepository.kt`, `SqliteIssueRepository.kt` | 문제 DB v4의 영수증 표만 추가, 문제+생성 이벤트+영수증 원자 저장 |
| `data/ConversationStore.kt` | 암호화 sidecar, 순서/중복 방지/문제 버전/AI 및 재생 상태/복구 |
| `ai/IssueDialogueContract.kt`, `IssueDialogueService.kt` | 별도 대화 계약, 같은 문제의 질문·답변 문맥, 구조화 응답 검증 |
| `ai/IssueAiLease.kt`, `IssueAnalysisService.kt` | 기존 수동/Gleo/음성 문제 분석의 공통 중복 요청 방지 |
| `ai/PortableOperationLock.kt`, `PortableAiClient.kt` | 공유 암호화 캐시 전체 작업 직렬화, 별도 대화 scope, 기존 pending/UNKNOWN 보존 |
| `ai/AiExplanation.kt`, `ai/auth/FirebaseSessionProvider.kt`, `ai/auth/PortableSession.kt` | 고정 endpoint 허용, 같은 인증/기기/키 재사용, 계정 변경 정리 |
| `vehicle/AttentionEventStore.kt`, `PleosAttentionObserver.kt` | 실제 관측별 사건·전달/확인/연결 이력, Safety capability/get/callback·해제 |

추가 Android 파일은 `AndroidManifest.xml`, `res/xml/voice_interaction.xml`이다. 검증 Kotlin은 runner, `RecordMigrationChecks`, `VoiceAutomationChecks`, `VoiceAutomationUiChecks`, `VoiceAutomationLiveChecks`, `VoiceAttentionSocChecks`, `VoiceRelayChecks`, `VoiceRealModelChecks`다. 마지막 실모델 검사는 컴파일됐으나 아직 실행하지 않았다.

## 저장과 재시작

문제 DB는 v3에서 v4로 `draft_commit_receipts`만 추가한다. 영구 운전자 ID, 원문, 작성 당시 이름, 수정·상태 이력과 네 수동 상태를 유지한다. 같은 명령 token/digest는 같은 영수증으로 끝나며 다른 내용 재사용은 거부한다. 분석 성공으로 점검 완료나 해결 상태를 만들지 않는다.

대화는 별도 `caritda-conversations.db`에 저장한다. 세션·순서 있는 메시지·AI 작업·재생 결과·문제 연결 영수증을 보관한다. 본문은 Android Keystore 기반 AES-GCM으로 운전자/AI 계정 범위에서 암호화한다. 원본 음성은 저장하지 않는다. 운전자 선택 자체는 암호 인증이 아니므로 민감한 대화의 완전한 개인 인증 수단으로 설명하지 않는다.

문제 commit 전에 sidecar에 token/draft/digest를 남기고 commit 이후 영수증과 대조한다. 두 DB가 하나의 트랜잭션이라고 가정하지 않는다. 재시작에서는 기존 영수증의 연결만 복구하고, 새 문제 저장·새 유료 요청·마이크·TTS를 자동 재실행하지 않는다. 재생 STARTED 잔여는 UNCERTAIN이다. 미확정 AI 작업은 원래 서버 operation/intent로만 확인한다.

동의 철회·계정 변경은 새 자동 전송을 차단한다. 대화 cloud 본문과 신규 대화 캐시의 보관 범위를 정리하되 원래 문제 기록·기존 수동 AI 캐시·서버 사용량 원장을 삭제하지 않는다. 실행 중 캐시 writer가 있으면 영속 pending 정리를 남기고 완료 후 정리한다. 운전자 한 명의 대화 지우기가 다른 운전자의 대화에 적용되지 않도록 분리한다.

30일은 대화 본문의 보관 기간이다. 만료 정리는 다음 음성 화면 진입에서 실행하며 앱 종료 중 백그라운드 삭제 서비스를 운영하지 않는다. 본문 삭제 후에도 세션·문제 연결·요청/재생 결과와 중복 요청 방지용 최소 메타데이터는 복구 이력으로 남긴다. 기존 SQLite 문제 원문과 수동 AI 설명은 해당 기능의 기존 보관 정책을 따른다.

## Gemini 계약과 보호 장치

기존 단일 분석 계약은 그대로 유지하고 신규 `caritda-issue-dialogue-v1`을 추가했다. endpoint는 `/v2/issues/dialogue`, 동의 scope는 `issue-dialogue-v1`이다.

전송 대상은 선택 문제의 증상 종류/본문, 현재 질문, 같은 문제의 이전 질문/AI 답변 최대 두 쌍이다. 이전 AI 답변은 `ASSISTANT_INFERENCE`로 표시한다. 답변 문맥은 첫 완결 문단으로 투영하고 크기를 넘으면 전송을 거부한다. 차량 facts는 빈 배열만 허용한다. 차량 수치·위치·운전자 이름·전화·메모·다른 문제·DB 원본은 자동 추가하지 않는다. 센서 facts 전송은 별도 계약/동의가 필요하다.

본문 800자·질문 240자·이전 turn 500자 및 총 3500 UTF-8 byte 제한을 검증한다. 기존 외부 wire 4KiB 제한은 유지한다. 응답은 답변·점검 사항·조건부 정비 설명·불확실성·증상 인용·문맥 참조·모델/시각/원문 hash와 facts를 검증한다. 객체 배열은 JSON 구조로 비교한다. 고장 확정 문구와 허용하지 않은 개인정보를 거부한다.

`portable-dialogue.ts`, `portable-worker.ts`, `portable-gate.ts`가 기존 Worker와 같은 Firebase/기기 서명·nonce·BUDGET Durable Object·일5/월100/서비스300·비용 예약·UNKNOWN 원장을 사용한다. Secret·정책·모델·DO namespace를 새로 만들거나 초기화하지 않는다. 기존 단일 분석과 대화는 같은 예산을 소비한다.

공급자 503, Relay 오류, 네트워크, 한도, 인증/동의, UNKNOWN, 검증 실패를 구분한다. 공급자 오류나 dispatch 불확실에는 새 유료 요청을 자동 재발급하지 않는다. RATE_LIMIT이며 아직 예약 전인 같은 intent/operation에만 61초 대기 후 한 번 이어갈 수 있다. 이전 설명은 ‘이전 Gemini 설명’으로 구분하며 재생은 유료 호출을 만들지 않는다.

## 차량 감시와 자동 안내

기존 SOC observer와 LowSocStore의 사용자 기준·해제 여유 2%p를 그대로 쓴다. 원본 관측 commit 후 사건을 평가하며 SOC 사건 ID도 기존 사건을 재사용한다. 추가 Safety 신호는 공개 capability/get/callback 결과를 기록한다. 워셔액/오일/브레이크액/출력 저하는 실제 유효값이 있을 때만 안내하며 미지원·권한 거부·오류·오래됨은 고장 또는 회복으로 판정하지 않는다.

홈 카드는 최신 유효 DriveCheck 결과만 표시한다. 전달 이력은 사건별 한 번이고 확인, 실제 회복, 사용자 문제 해결은 서로 다른 이력이다. 자동 음성은 **홈·현재 운전자 확정·초안 없음·가시·안전 허용·계정 준비·보관/음성/차량 안내 허용** 조건에서만 시작한다. 백그라운드 서비스나 무한 polling은 추가하지 않는다. 같은 사건 STARTED가 중단돼도 다음 실행에서 자동 재생하지 않는다.

차량 안내만으로 Gemini를 호출하지 않는다. SOC 질문은 실제 최근 SDK 값으로 로컬 답변하고 ‘이 내용 기록해 줘’가 있어야 문제에 연결한다. 차량 수치를 Gemini 본문에 자동 삽입하지 않는다. 제조사 경고를 대체하거나 위험도/고장을 추론하지 않는다.

## 외부 실행 경계

- Android [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)는 연속 인식용으로 설계되지 않았다고 안내한다. 이 앱은 해당 API로 무제한 호출어 polling을 만들지 않고 기존 Pleos STT의 제한된 대화 세션을 재사용한다. 이는 독립 호출어 엔진 검증과 다르다.
- [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)의 전역 비서는 사용자가 선택한 서비스이고, [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)는 제공 여부와 현재 보유 여부를 따로 조회한다. 역할 존재만으로 해당 앱의 전역 음성 실행을 입증하지 않는다. 세 문서는 2026-11-12 실제 열람했다.
- 이 환경의 RoleManager는 assistant role 제공 `true`, 이 앱 선택 `false`였다. 선택 화면/정상 Service 경로는 구현했지만 시스템 전체 호출어·차량 PTT·실제 비서 역할 실행은 미검증이다.
- Gleo schema/3 Actions/Callback 신뢰 검증은 보존했고 내부 계약 회귀를 실행했다. 외부 Gleo 실행은 확인하지 않았다.
- Cloudflare CLI는 처음 `Not logged in`, 현재 Codex 호스트에서는 인증 경로 접근/네트워크 `fetch failed`로 제어 API 확인을 완료하지 못했다. 사용자가 직접 로그인을 복구하기로 했다. 우회/키 추출은 하지 않았다.
- 배포 도구 `infra/gemini-relay/test/deploy-dialogue.mjs --execute`는 검토한 기존 Worker 버전·번들 SHA·같은 binding/정책·모델 ON을 배포 전후 확인한다. 다른 세션이 Worker를 바꾸면 중단한다. 새 모델 호출은 배포 도구에 포함하지 않는다.
- 승인된 실모델 요청은 이 작업 전체 최대 2회다. 현재 사용 0회. 초기 분석 1회+후속 답변 1회까지만 같은 승인으로 실제 검증할 수 있으며, 두 번째 후속 질문까지 총 3회를 자동 실행하지 않는다. hard guard/기존 한도와 UNKNOWN 보호를 해제하지 않는다.

후속 완료 조건은 배포/인증 확인 후 제한 내 실모델 분석·새 답변 검증, 사용자의 실제 발화 확인이다. 실차와 외부 Gleo는 별도 검증 단계다.

## 13단계 연결 결과

| 단계 | 결과 |
|---|---|
| 1 최신 통합 확인 | main/249개 공통 파일/연구3개/503·DB·SDK·Gleo·B안 대조 완료 |
| 2 자체 비서 시작 | 앱 내부 제한 세션 구현·실제 화면 검증, 새 발화는 사용자 보류; 독립 호출어 미완료 |
| 3 명령 해석 | 기록/조회/후속/반복/종료·모호함/거부/길이 범위 검사 완료 |
| 4 자동 기록 | 실제 SQLite atomic commit·receipt·중복방지·A/B 인계 검사 완료 |
| 5 저장 직후 분석 | 기존 IssueAnalysisService 자동 연결 구현/fixture 통과, 이번 실모델 미검증 |
| 6 자동 TTS | 실제 전체 on-device DONE·취소 검증; 신규 모델 결과의 실제 TTS는 모델 배포/요청 후 확인 필요 |
| 7 연속 대화 | 별도 client/server/context 계약·새 응답 두 turn fixture 통과; 운영 배포/실모델 답변 미완료 |
| 8 전체 이력 | 암호화/순서/AI원천·시각/재생·receipt·재시작/철회·운전자 분리 검증 완료 |
| 9 차량 변화 | 실제 SOC callback/POWER_DOWN false callback 확인, 나머지 Safety3 미지원 |
| 10 홈 안내 | 실제 유효 SOC18/45의 표시·회복·중복 방지 확인 |
| 11 능동 음성 | 홈의 허용된 새 SOC사건→실제 TTS→로컬 조회/기록 연결 통과; 이어지는 STT는 fixture |
| 12 오류/Gleo | 503/Relay/네트워크/UNKNOWN/캐시/공유lock 회귀, 기존 내부 Actions37 통과; 외부 실행 미검증 |
| 13 최종 통합 | 빌드/Lint/계측/일반 업데이트/원본보존·GitHub 게시 대상 준비. 전체 실모델/실제발화 완료로 판정하지 않음 |

</details>
