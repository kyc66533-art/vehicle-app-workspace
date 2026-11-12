# CARITDA 음성 자동화 통합

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
