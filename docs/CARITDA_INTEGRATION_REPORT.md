# CARITDA 통합 구현·실행 검증

## DriveCheck 보조·위치·SOC 안정화 — 2026-11-11 19:10 +09:00

CARITDA의 기존 홈/기록/차량/AI 구조를 유지하며 위치 동의→실제 last-known 조회→명시적 append 연결/이력과 DriveCheck 안내→편집 초안→명시적 저장→기존 AI/인계를 연결했다. SDK 버전·기존 STT/Gemini/TTS·DB 스키마·Gleo·운영 정책은 바꾸지 않았다. 센서값은 Gemini body에 자동 추가하지 않는다.

실제 Navi에서 목적지 일치·탐색 시작·ROUTE_DRIVE/ROUTE_GUIDING을 받았지만 onRoutingCompleted는30초 안에 오지 않았다. 수신 이력을 표시하고 전체 완료는 보류한다. 기존 경로와 약관·좌표·권한·중복·생명주기를 검사하며 사용자 경로를 교체하지 않는다. 이번에 발행한 일치 fixture 경로만 정리하고 SAFE_GUIDING/inactive를 재확인했다. 공식 이벤트 의미에 따라 onRouteStarted를 안내 시작으로 잘못 표시하던 부분을 수정했다.

실제 validation Main에서 Console100→SDK18 callback→반복18 GET→45 callback, 동일 사건 진입1/해제1, A 명시 저장·B 읽음·cold restore를 검증했다. 종료 뒤 SDK 수신 중단·공백 및 Console100/P/0와 SDK100/실제 ALLOWED 복원 확인. 차량20항목12수신/8미지원과 실제 Fused 연결·TTS 완료를 재검증했다. CAR_SPEED 권한은 거부, 연속 속도/가속도 미확보·기기 가속도 센서 없음이므로 급가감속 점수/진단은 없다. 마이크는 validation 수동 최대5초 RMS/peak/직접 중단만 보완했고 이번 자동 녹음0이다.

Debug/Validation/AndroidTest/Lint 성공,0오류/56경고·Unit NO-SOURCE. **신규102+관련 회귀331=433 assertion**, 새로운 유료 Gemini/Worker 배포0회. 모델ON·동의·인증·캐시·한도 보존. 일반 앱 서명 일치 교체 및 COLD/resumed 실행 성공, 일반 UI 자동화0이다. 새 위치/DriveCheck의 일반 사람 실조작 E와 실차 F는 미검증이다.

[이번 파일21개·A~F·433개 검사·실제 SDK/합성 구분·데이터 보존·남은 기술적 제한](DRIVE_CHECK_ACCEPTANCE.md)이 최신 근거다. 아래 과거 실모델1회/298/281개 결과는 각 작업 당시 기록으로 보존한다.

---

## 작성·AI 설명·TTS·인계 연결 — 2026-11-11 17:31 +09:00

일반 작성 화면에 **저장하고 AI 설명**을 추가했습니다. STT final+ended→직접 편집→명시적 SQLite 저장/읽기 확인→그 issue ID의 기존 AI 화면으로 이어집니다. 전송 요약을 확인하고 새 설명을 명시적으로 요청해야 모델을 호출합니다. 현재 `USER_SELECTED_RECORD`/issue별 revision 캐시 계약을 유지하기 위해 미저장 분석은 추가하지 않았습니다. 가짜 문제·ID 재사용·AI 완료 후 자동 저장은 없습니다.

기존 Firebase/PortableAiClient/암호화 store를 재사용하는 `IssueAnalysisService`를 일반 UI와 Gleo에서 공유합니다. 새 결과/기존 캐시/진행 중 요청 회수를 구분하고 동시 진입·UNKNOWN/pending을 지킵니다. 별도 AI 전송 사본을 암호화 보존하며 원문을 바꾸지 않습니다. TTS는 실제 표시 결과 전체를 기존 기기 내 음성으로 나누어 읽고 새 Gemini 호출을 하지 않습니다. 연결된 실제 차량 snapshot과 최근 참고 snapshot은 로컬 표시만 하며 AI body에 추가하지 않습니다.

Debug/validation/AndroidTest/Lint 빌드 성공, Lint0오류/56경고·Unit NO-SOURCE. **신규45+관련 회귀253=298 assertion** 통과. 실제 MainActivity→AI View→fixture 모델→TTS 전달→B 읽음·점검 메모/재시작을 검사했고 실제 Pleos 한국어 TTS 완료, 실제 SDK SOC·거리와 fixture AI의 같은 화면 표시/차량값 미전송을 별도 검증했습니다. 합성 STT/모델과 실제 SDK를 구분합니다. 이번 실모델·배포0회이며 아래 기존 실모델 성공을 새 UI의 실모델 성공으로 재사용하지 않습니다.

서명 일치 확인 후 일반 APK 교체·설치 산출물 해시 대조, MainActivity COLD/resumed 성공. 일반 앱 UI 자동화0이며 새 흐름 실조작은 validation입니다. 기존226DB의 전체 원본1206+9380행/스키마·무결성을 보존했습니다. 일반24문제/96이벤트/10운전자/1초안 불변, 정상 SDK 진입 조회/파생23행만 추가. Firebase·동의·AI 캐시/사용량 등15파일 동일, 설치 실행 표시파일1개만 갱신됐습니다. 기존 모델ON/정책·인증·Worker/원장을 수정하지 않았습니다.

[채택 이유·9개 Kotlin 파일·298개 검사·실행·보존·사용자 최종 확인](STT_AI_HANDOVER_ACCEPTANCE.md)이 이번 최신 기준입니다. 아래281개/신규 실모델1회는 각각 직전 작업의 근거로 보존합니다.

---

## Gemini 독립 신규 분석 성공 — 2026-11-11 16:45 +09:00

Gleo와 독립적으로 **일반 CARITDA 선택 증상 → 기존 Worker/Gemini 신규 설명 → 화면 표시 → 앱 한국어 TTS**를 사용자가 실제 확인했습니다. 승인된 실제 시험은1회입니다. 활성 Worker `200edeff-41b1-4454-9d40-b0d2809014e8`, 모델ON·무인증401·기존 BUDGET namespace/정책 보존을 실행기가 검증했고 COMPLETE로 종료했습니다.

사용량은 TEST1 유지, 정식 시도5→6, 잔여5→4입니다. 기존 잔여0은 최초 이월일 제한이었으며 오늘 일일 갱신 후 해소됐습니다. 한도 증액 없이 모델OFF→ON만 적용했습니다. 일5/월100·서비스 월300·월 USD3 비용 예약을 유지합니다. 기존 동의대로 종류/증상 문장만 전송했으며 Vehicle/위치/다른 기록을 전송하는 계약을 추가하지 않았습니다.

Android·SDK·서명·Worker 공급자 코드/번들은 그대로 사용했습니다. 운영 실행기에는 선택적인 TTS 확인과 엄격한1회 사용량 판정을 보완했고 로컬72개 검사를 통과했습니다. Android 재빌드/이번 계측 재실행은 없으며 아래281개는 직전 동일 APK의 결과입니다.

기존226DB의 전체 원본 행/스키마/무결성 검사를 통과했고 일반24문제/96이벤트/10운전자/1초안은 동일합니다. AI 암호화 결과/사용량과 Firebase SDK 로컬 상태만 정상 갱신됐고 기존 설명 복원도 확인했습니다. 앱 TTS는 이번 사용자 직접 청취로 확인했으며, 원본 운영 기록에 없는 TTS 전용 필드를 만들어 넣지 않았습니다. 신규 Gemini1회와 캐시 재생/Mock는 구분합니다.

[실행·보안·사용량·비용·보존 상세](GEMINI_INDEPENDENT_ACCEPTANCE.md)를 기준으로 합니다. 아래의 모델OFF/신규0회는 앞선 통합 단계 당시 기록입니다. **Gleo 외부 왕복/7초 응답/Vehicle 값의 AI 전송은 계속 미검증·별도 범위**이며, 이번 성공으로 확대하지 않습니다.

---

## 최종 통합 갱신 — 2026-11-11 15:46 +09:00

기준 main `d946720bb5598982ded7d56027b4eb788188740e`와 로컬 tracked 파일이 일치한 상태에서 시작했습니다. 새로운 사용자 지시에 따라 일반 앱의 음성 초안 팝업 제거를 먼저 완료했습니다. 아래 과거 통합 결과는 이력이며, **이번 버전의 검사는 총 281 assertion**입니다. 과거 313개 전체를 재실행한 것으로 쓰지 않습니다.

| 기능 | 이번 판정 | 실제 근거와 한계 |
| --- | --- | --- |
| 일반 앱 STT → 입력란 | 확인된 런타임 성공 | 사용자가 교체된 일반 앱에서 “팝업 없이 입력란에 반영됨” 확인. 실제 PLEOS_ON_DEVICE ENDED→FINAL→COMPLETE 순서 수신. 음성/문장 로그 저장 없음 |
| 초안·명시적 저장·다음 운전자 | 확인된 런타임 성공 | validation 실제 MainActivity/SQLite: 합성 callback→초안, 중복/취소, Activity 재생성, 명시적 저장, B 재진입·인계 확인. 자동 issue 저장/해결 없음 |
| Vehicle 2.3.2 | 확인된 런타임 성공 | 기존 20개 공개 조회 중 12개 RECEIVED/8개 UNSUPPORTED. SOC100%, 거리0.0km, 문/등화/방향지시/페달/출력저하. 실차 고장 판단 아님 |
| 기존 Gemini 설명·암호화 캐시 | 확인된 런타임 성공(기존 사용자 사실), 이번은 로컬 회귀 | 이전 실모델 성공 사실 유지. 이번 issue/cache/cold/인증 계약은 격리 Mock 검사이며 신규 Gemini0회 |
| Gleo Schema·Intent·Callback 구성(A~C) | 확인된 런타임 성공(내부 계약/validation) | 일반/TEST별 Schema3개, 실제 ActionActivity 실행, payload 검사37개. 자연어 호출 성공과 다름 |
| 신뢰 대상 전송·Wake word·Gleo 음성(D~F) | 외부 Gleo 승인·환경 대기 | 관련 시스템 앱은 존재하지만 공식 응답 receiver/호출 신원·Schema 승인을 확인하지 못함. 개인정보 Broadcast는 차단 |
| Gleo 신규 Gemini 실시간 분기 | 코드·빌드만 성공 / 기존 Gemini 모델 OFF·한도 차단 유지 | 기존 클라이언트/동의/한도 재사용. 신규 호출0, Gleo 7초 내 신규 분석·음성 왕복 미검증. 현재 서버 정책을 변경하거나 추가 조회하지 않음 |
| FusedLocation last-known·연결 | 확인된 런타임 성공(validation) | 실제 유효 좌표·시각 수신, 선택 fixture issue에 sidecar 저장·재개방 일치4개. 일반 앱 사용자 위치 연결 UI 실조작은 미검증, 정확도(m)·증상 당시 위치는 확정 불가 |
| 앱 Pleos TTS | 확인된 런타임 성공(callback) | ON_DEVICE 완료 callback. 이번 물리 스피커 청취는 별도 미검증 |
| Navi 경로 상태 | 확인된 런타임 성공 | routing=false, terms=true 실제 callback. request/검색완료/시작 분리·listener 해제 보완은 빌드 확인; 실제 사용자 목적지 전달/시작은 미검증 |
| 실제 TPMS 압력·DTC·등화 고장 | 공개 API 미지원/현재 접근 불가 | 저압 기준·등화 ON을 실압·고장으로 쓰지 않음 |
| Release APK | 코드·빌드만 성공 | unsigned 산출물만 생성. 서명·설치·공개 제출용 검증 미완료 |

### 변경 파일

- 일반 소스: `MainActivity.kt`, `CaritdaActionActivity.kt`, `integration/CaritdaActions.kt`, 신규 `SpeechDraftDelivery.kt`, `GleoReplyChannel.kt`, `GleoIssueAnalysis.kt`, `InspectionNavigation.kt`.
- Manifest, 일반/validation `assets/schema.json`, 신규 `res/values/gleo.xml`. 추가 SDK/계정/Secret/권한을 만들지 않았습니다.
- 계측: `MvpScenarioInstrumentation.kt`의 준비 전 조기 분기, 신규 `GleoIntegrationChecks.kt`, `SpeechDraftUiChecks.kt`, `LocationLinkChecks.kt`.
- 문서: 본 보고서, [Gleo 검증·공식 근거](GLEO_INTEGRATION_ACCEPTANCE.md), [STT 기록](STT_STABILITY.md), [실행법](STT_RUN_GUIDE.md), README, SDK_CAPABILITY_MATRIX, CODEX_PROGRESS.

STT SDK/생명주기 코드를 다시 쓰지 않았습니다. `SpeechDraftDelivery`는 원문/작성자/초안/화면이 같은 경우에만 성공 문장을 한 번 덧붙입니다. 일반 앱의 확인 팝업을 제거하고 수동 완료 버튼도 복원하지 않았습니다. `validation_fixture`는 validation 패키지와 제한된 고유 이름에서만 별도 DB를 선택하며 일반 앱은 이 extra를 무시합니다. 합성 callback 주입은 androidTest의 reflection에만 있습니다.

### 실제 빌드·검사

Windows11, JBR21, Gradle8.11.1, AGP8.10.1, Kotlin2.2.21, API34, 기존 Pleos_Connect_v2.1.0/user10, 2560×1440/density160. 호스트 오디오 시작 설정과 AVD는 그대로입니다.

```text
:app:assembleDebug :app:assembleValidation :app:assembleValidationAndroidTest
:app:lintDebug :app:testDebugUnitTest :app:assembleRelease
BUILD SUCCESSFUL (3m20s, exit 0)
```

Lint 오류0/경고56. `testDebugUnitTest`는 NO-SOURCE로, Unit Test 성공 개수를 만들어내지 않습니다. 이후 새 검사의 fixture 운전자 등록/화면 재생성·위치 저장 검사를 보완한 테스트 APK만 재빌드했고 exit0입니다. 초기 PowerShell -P 인자 분리 오류와 신규 fixture의 미등록 운전자 오류를 고친 뒤 재실행했습니다. 기존 DB를 지우거나 운전자 정책을 바꾸어 통과시키지 않았습니다.

위 명령은 기존 `-PcaritdaDebugKeystore`로 이미 설치된 Debug 서명을 재사용했습니다. 로컬 Gradle 네트워크 제한 때문에 기존 읽기 캐시/오프라인 설정을 사용했으며 새 Sync·설치를 요구하지 않았습니다.

| 실제 validation 분기 | assertion |
| --- | ---: |
| gleoOnly — 새 Schema/입력/Callback/신뢰 차단/분석 정책 | 37 |
| speechDraftUiOnly — 새 MainActivity 초안·저장·재생성·B 확인 | 21 |
| locationLinkOnly — 실제 위치 값의 격리 연결·복원 | 4 |
| coreDatabaseOnly / integrationOnly | 19 / 33 |
| sttSessionOnly / sttAutomaticUiOnly | 40 / 6 |
| issueAiOnly / issueAiColdOnly | 56 / 4 |
| portableAiOnly / portableAiUiOnly | 26 / 4 |
| analyticsContractOnly / integrationLiveOnly | 24 / 7 |
| 합계 | **281** |

각 분기를 `am instrument -w -r --user 10 -e <분기> true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation`로 실행했고 PASS marker·실제 계측 종료를 확인했습니다. 일반 앱 UI 자동화와 기존 destructive prepare는 실행하지 않았습니다. 합성 SDK callback/Mock 모델, 실제 에뮬레이터 Vehicle·Fused·TTS, 사용자 실제 한국어 발화를 분리합니다.

### 데이터·일반 앱 설치

변경 전 및 교체 직후/사용자 시험 후 각각 일반·TEST의 databases/shared_prefs/files를 로컬 비공개 백업했습니다. 기존 일반 41DB + validation163DB = **204DB**의 스키마/user_version/전체 원본 행/무결성/외래키 검사를 통과했습니다. 이후 추가 fixture DB는 기존 DB를 초기화하지 않았습니다.

| 일반 앱 | 변경 전 | 최종 사용자 음성 시험 후 |
| --- | ---: | ---: |
| 문제 / 이벤트 / 운전자 | 24 / 96 / 10 | 24 / 96 / 10 |
| 초안 | 0 | 1 — 사용자가 실제 음성으로 작성한 미저장 초안 |
| 원관측 | 220 | 240 — 실제 진입 조회20개 추가 |
| 스냅샷 | 73 | 74 |
| 분석 인사이트 | 144 | 146 |

일반 원본1112행·validation 원본9264행은 모두 동일하게 남았고, 일반 앱은 새 관측/스냅샷/인사이트/사용자 초안24행이 추가됐습니다. 일반 앱 인증·암호화 캐시·개인 파일15개는 변경 전후 byte-identical입니다. 기록 수를 과거23건으로 고정하지 않았습니다. Worker·Firebase·BUDGET·Secret·모델/정책 설정·연구 파일은 수정하지 않았고 외부 모델/배포0회입니다.

설치된 일반 APK와 새 Debug의 인증서 SHA256 일치를 확인한 뒤 `adb install -r --user 10 .../app-debug.apk` 성공, 일반 MainActivity COLD 실행 Status:ok, 실제 foreground를 확인했습니다. 사용자가 새 일반 앱의 음성 자동 반영을 직접 확인했습니다. 시험 직후 백업을 위해 정상 앱을 종료했지만 초안과 인증 자료는 유지했습니다.

### APK와 제출 제한

- 일반: `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk` — 기존 Debug 서명, 교체 설치·사용자 실행 확인.
- TEST: `NextDrive-Pleos/app/build/outputs/apk/validation/app-validation.apk` — 별도 applicationId·데이터.
- 계측: `NextDrive-Pleos/app/build/outputs/apk/androidTest/validation/app-validation-androidTest.apk`.
- Release: `NextDrive-Pleos/app/build/outputs/apk/release/app-release-unsigned.apk` — 빌드 성공, apksigner verify exit1(서명 없음). 실행 가능한 제출 APK라고 표시하지 않습니다. 새 키 생성·내보내기·APK 게시 없음.

공모전 실제 열람·제출 준비는 README의 최신 절을 따릅니다. 저장소는 이번 연결 도구 조회에서 Private입니다. 공개 전 이력 전체 시크릿 검사, 기존 소유 서명키로 Release 서명·설치 검증, 최종 APK/영상·기획서 준비와 사용자 최종 공개/제출 승인이 남았습니다.

### 남은 확인

실제 Gleo A~G 판정과 승인/호출자 인증 조건은 [Gleo 보고서](GLEO_INTEGRATION_ACCEPTANCE.md)에 기록했습니다. 외부 D~G, 실제 새 Gemini 7초 왕복, 사용자 정비소 목적지/경로 시작, 물리 TTS 청취, PC B·실차·고령 사용자·TalkBack/rotary는 이번 성공으로 주장하지 않습니다. 안전 제한을 우회하지 않았으며 주행 중 복잡한 UI/백그라운드 음성 감시는 제공하지 않습니다.

UI 팀은 MainActivity의 음성 최종 결과 적용, validation DB 분리, CarSafetyGate, 기록 원문/초안·작성자, Gleo 신뢰 확인과 기존 AI 최소 전송 계약을 유지해야 합니다. 홈 구조와 디자인 토큰은 다시 설계하지 않았습니다.

---

아래는 이전 통합 작업 당시의 기록입니다.

기준: GitHub main `e0517f3972b8043c6f13b448c412d23f2217682e`. 2026-11-10 Asia/Seoul. 기존 Android View·홈·3개 핵심 흐름을 유지하며 보조 기능을 연결했습니다. 아래 결과는 Pleos Connect v2.1.0 에뮬레이터이며 실차 검증이 아닙니다.

## 실제 구현

- Vehicle 2.3.2의 기존 SOC·거리·연료 단발 조회에 공개 읽기 17항목을 추가했습니다. 권한 → READ Capability → 영역 → 값 유효성 → 기존 ObservationStore 원본 저장 순서입니다. 추가 polling·제어·백그라운드 수집은 없습니다.
- 차량 정보의 접힌 보조 영역에서 문 위치·등화·방향지시·브레이크 페달·공개 차량 안내를 확인합니다. 홈은 재설계하지 않았습니다. 값/미지원/미수신/오류/이전값/오래됨을 구분합니다.
- 문제 작성에 명시적 음성 입력을 연결했습니다. 최종 인식 문장을 편집·확인하고 초안에 반영한 뒤 기존 저장 버튼을 눌러야 합니다. 자동 확정 저장은 없습니다.
- 문제 상세·정비 상담·이미 검증된 AI 설명에 사용자가 누르는 기기 내 TTS를 연결했습니다. AI 설명 듣기는 캐시/현재 표시문만 읽으며 새로운 Gemini 요청을 하지 않습니다.
- 문제 상세에서 사용자가 동의한 위치를 1회 가져와 좌표·측정 시각을 확인한 뒤 로컬 sidecar에 연결할 수 있습니다. 마지막 위치는 증상 발생 당시 위치로 확정하지 않습니다. 기존 문제 DB와 별도이며 AI 전송 payload에 포함하지 않습니다.
- 정비 상담의 길찾기는 사용자가 직접 확인한 장소·좌표를 입력하고 요청합니다. 기존 경로·약관 상태를 먼저 확인하고 기존 경로를 취소/덮어쓰지 않습니다. 연결 성공·요청 전송·경로 완료 callback은 구분합니다. 예약 기능은 없습니다.
- Gleo용 두 Action/Schema, 로컬 읽기 처리, 확인 화면, 음성 명령 문장의 선택적 초안 반영을 구현했습니다. 외부 Intent가 자동 읽음·기록 저장·Gemini 호출·상태 변경을 실행하지 않습니다. 신뢰할 Callback 수신 앱이 확인되지 않아 개인 기록을 implicit broadcast하지 않습니다.
- 속도 변화 계산과 센서 가용성 실험은 validation AndroidTest에만 있습니다. 일반 앱에 점수/위험도/고장 예측 메뉴를 넣지 않았습니다.

## 공식 SDK·API 및 의존성

| 대상 | 적용 | 확인한 근거와 경계 |
| --- | --- | --- |
| Vehicle | 2.3.2 유지 | 공개 AAR sources의 Door/Light/TurnSignal/Safety/Brake/Tire. CAR_DOORS/CAR_LIGHTS/CAR_SAFETY/CAR_DRIVE 읽기만 사용 |
| STT / TTS | 각 3.3.1 | 공개 AAR의 typed initialize/listener/request/speak. ON_DEVICE 사용. 서버 Client Secret·새 계정 불필요 |
| FusedLocation | 1.1.3 | 공개 sources의 isSupported/getLastKnownLocation/release. FUSED_LOCATION은 실제 기기에서도 dangerous. 사용자 허용 필요 |
| NaviHelper | 3.0.5 | 공개 sources의 initialize/getRouteStateInfo/requestRoute/listener. NAVI_ROUTE/NAVI_ROUTE_SEARCH는 기기에서도 normal |
| Gleo Interface | Intent·assets/schema.json | 별도 LLM SDK로 대체하지 않음. Schema 승인과 실제 Gleo Action/Callback 런타임 검증은 별도 |
| AAOS 속도·기어 | validation 전용 | 공개 CarPropertyManager/PERF_VEHICLE_SPEED/GEAR_SELECTION. SDK의 hidden Gear API는 호출하지 않음 |

공식 [Playground](https://developers.pleos.ai/playground), [SDK 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note), [Vehicle 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app), [Navi 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app), [Fused 샘플](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app)을 확인했습니다. 한국어 최신 릴리스와 실제 AAR 기준으로 버전을 정했습니다. 일부 영어 문서는 이전 버전을 안내합니다. 샘플의 mock location provider는 도입하지 않았습니다.

공식 [Gleo Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability), [승인·전달](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema), [Callback](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback), [AAOS 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds)을 기준으로 구현했습니다. `developers.hyundai.com` 본문은 도구 시간초과로 확인하지 못했습니다. 별도 Vehicle Data API의 지역/OAuth 고장 데이터를 내부 SDK 권한으로 대신 호출하지 않습니다.

### Maven 장애 수정

`ai.pleos.playground.security:universal-access:0.6.0`은 STT/TTS 3.3.1의 전이 의존성입니다. 기존 Maven content 필터가 `ai.pleos.playground`만 허용해 하위 그룹을 차단했습니다. 공식 저장소에 정확한 `.security` 그룹만 추가했습니다. SDK 버전/저장소 인증/Firebase/Gradle 전체를 변경하지 않았습니다. 사용자가 수정 후 Sync 성공을 확인했고 Codex에서도 일반·validation APK 빌드에 성공했습니다.

테스트 APK의 오래된 AndroidX compile 해석은 앱에 이미 선택된 annotation-experimental 1.3.1/lifecycle-common 2.6.1로 테스트 의존성을 맞췄습니다. 사용자 Android Studio 테스트 APK 빌드 성공과 이후 Codex 로컬 빌드를 구분해 기록합니다.

## 실제 SDK 실험

Android user 10, emulator-5554. 합성 앱 fixture와 별도의 고유 관측 DB를 사용했습니다. 실제 SDK 출처는 PLEOS_EMULATOR_SDK입니다.

| 신호 | 실제 결과 | 의미 |
| --- | --- | --- |
| SOC | 100 % | 조회 당시 SDK값, 고정 예시 아님 |
| 누적거리 | 0.0 km | 유효한 0, 미수신 아님 |
| 문 4개 위치 | 각 0 position | 차량 기준 위치값. 전 차종 공통 고장/닫힘 판정으로 일반화하지 않음 |
| 하향등 / 상향등 / 미등 | OFF / ON / ON | 논리 상태. 실제 전구·LED 정상 여부 아님 |
| 방향지시 | OFF | 실제 SDK 상태 |
| 브레이크 페달 | false | 페달 상태. 브레이크등 정상 여부 아님 |
| 출력 저하 안내 | false | 현재 해당 차량 안내가 없다는 수신 결과 |
| 연료, 엔진오일, 브레이크액, 워셔액 안내, 저압 기준 4개 | UNSUPPORTED | 8항목 모두 raw/unit/receivedAt를 만들지 않음 |

20개 요청 각각 종결 상태를 저장했고 12개 실제 값을 수신했습니다. 타이어 `getLowTirePressure`는 **임계 저압 기준 kPa**이며 실압/고장 경고가 아닙니다. 실압/Telltale 전체는 PRIVATE_CAR_SAFETY 경로이므로 선언·호출하지 않았습니다. 자동 고장 기록이나 경고를 합성하지 않았습니다.

NaviHelper는 실제 `onRouteStateInfo`에서 routing=false, terms=true를 받았습니다. 목적지 전달과 경로 완료는 이 읽기 시험으로 통과 처리하지 않습니다. Pleos ON_DEVICE TTS는 실제 onDone을 받았습니다. 물리 스피커 청취와 마이크 인식은 별도 사용자 시험입니다. FusedLocation은 최초 검사 시 사용자 권한이 없어 요청하지 않았습니다.

검증 전용 AAOS 실험에서 GEAR_SELECTION status=0/value=4를 읽었습니다. 기어값은 이동 증거가 아닙니다. 현재 속도 property는 가용 목록에서 확인되지 않았습니다. 원시 가속도 센서는 없었습니다. 원시 마이크를 자동 녹음하지 않았고 독립 고장 라벨이 없어 소음·진동 진단 성능은 산출하지 않습니다.

## 검증·데이터 보존

최신 검사와 제한은 아래 판정표와 STT 안정화 보고서를 기준으로 합니다. 과거 297개를 새 코드 전체 통과로 재사용하지 않습니다. 위험한 기존 prepare/default DB 초기화 경로는 실행하지 않습니다.

- 시작 전 일반·validation의 databases/shared_prefs/files 전체 로컬 tar 백업 완료. 일반 41개 DB, validation 147개 DB. 비공개 `.tools/issue-ai-backup/` 보관.
- 시작 시 일반 실제 DB는 문제 **24건**, 이벤트 **96건**, 운전자 **10명**, 초안 **0건**입니다. 과거 23건으로 고정하지 않았습니다.
- Gemini Secret·Worker·Firebase 프로젝트·모델 OFF·사용량/UNKNOWN 원장을 변경하지 않았습니다. 새 Gemini/유료 API/서버 배포 0회.
- 최초 신규 fixture 검사는 빈 DB에 legacy A/B가 있다고 가정해 실패했습니다. 등록한 영구 ID를 사용하도록 수정했습니다. 다음 실패는 시험 이름의 12자 제한이었으며 짧은 fixture 이름으로 수정했습니다.
- 기존 관측 검사의 ‘최근 3행이 SOC/거리/연료’ 가정은 추가 공개 신호로 깨졌습니다. 기본 3종을 명시적으로 조회하도록 수정했고, 새 coreDatabaseOnly는 SQLite fixture 부분만 실행하도록 분리했습니다. 최초 실행은 validation 선택 이름 변경과 실제 관측 추가까지 진행했으며 일반 앱에는 영향을 주지 않았습니다. 기존 validation 선택값도 최종 보존 검사에서 확인합니다.

## 사용 방법·검증 경계

1. 차량 정보 → 문·등화·차량 안내 확인: 실제 값과 미지원 구분.
2. 증상 남기기 → 음성으로 증상 입력: 마이크 허용 → 인식문 확인/수정 → 입력에 반영 → 기존 저장.
3. 문제 상세 → 이 기록 음성으로 듣기. AI 화면의 기존 설명 보기 후 음성 듣기는 모델 호출 없음.
4. 본인 기록 상세 → 현재 위치 함께 저장: 동의·권한 허용 → 측정 시각/좌표 확인 → 명시적 저장. 실패해도 원문 유지.
5. 정비 상담 → 정비 장소 길찾기: 확인한 장소·좌표를 입력. 이미 경로가 있으면 변경하지 않음.
6. Gleo 실제 발화는 Schema 심사·호환 런타임이 필요합니다. ADB Intent 실행은 Gleo 음성 인식 성공을 뜻하지 않습니다.

Release 서명 APK·PC B·실차·고령 사용자·TalkBack/Rotary·주행 중 복잡한 조작 검증은 로컬 Mock/콜백으로 대체하지 않습니다. APK/키/DB/개인 캡처/로그는 게시하지 않습니다. UI 팀은 MainActivity의 보조 액션 연결과 독립 integration 패키지, 기존 canEdit/canWrite·초안/이력·AI 전송 계약을 유지해야 합니다.

## 최종 상태 — 2026-11-11 02:26:21 +09:00 (Asia/Seoul)

| 기능 | 판정 | 실제 근거·제한 |
| --- | --- | --- |
| Vehicle20종 종결/12종 실수신 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 실제 SDK·고유 관측 DB, 미지원8개 분리 |
| 기존 기록·인계/AI 캐시 계약 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 안전한 기존 회귀부분219개, 신규 모델 호출0 |
| 한국어 자동 STT·연속 입력 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 공식 request/ON_DEVICE, 사용자 실제3회와 SDK final/ended 확인 |
| 자동/취소 최종 UI | IMPLEMENTED_AND_RUNTIME_VERIFIED | 합성40개·실제 SDK 준비/취소/재초기화 View6개; 최종 레이아웃 후 발화는 별도 미수행 |
| 직접 PCM | validation 전용 | 정상 공개 영어3/3, 실제 사용자 마이크와 별도 |
| TTS | IMPLEMENTED_RUNTIME_UNVERIFIED | 실제 onDone 수신, 물리 청취 별도 |
| 위치1회 | IMPLEMENTED_RUNTIME_UNVERIFIED | SDK valid metadata, 실제 사용자 문제 연결은 미수행 |
| Navi 길찾기 | IMPLEMENTED_RUNTIME_UNVERIFIED | 경로 상태 callback만 확인, 목적지 전달/경로 완료 미검증 |
| 외부 Gleo | BLOCKED_EXTERNAL_DEPENDENCY | 내부 Action/Schema만 구현, 승인·실제 호출 없음 |
| 속도·소음/진동 진단 | UNSUPPORTED_BY_AVAILABLE_API | 현재 실속도 property/가속도·고장 정답 없음, 독립 실험만 유지 |

기존219 + 통합33/실제SDK7/독립실험5/최종 합성STT40/실제 캡처경계3/실제 SDK·취소UI6 =313개 assertion 통과. 이전35개는 최종40개로 대체해 중복 집계하지 않았습니다. 실제 발화3회·공개 PCM3회·독립 request/종료순서 진단은 별도입니다. 과거297개 전체를 기본 DB 초기화 경로로 재실행하지 않았습니다.

최종 Debug/validation/AndroidTest/Lint 성공, unit NO-SOURCE. Debug APK는 NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk, TEST는 outputs/apk/validation/app-validation.apk, 계측은 outputs/apk/androidTest/validation/app-validation-androidTest.apk입니다. Release/PC B/실차는 미검증입니다.

최종 일반41DB/validation163DB 백업에서 시작188개 기존DB schema/version/원본행/integrity/FK를 확인했습니다. 일반24/96/10/0·원본1106행 불변, validation 원본8938행 보존. 추가119행은 앞선 실제 관측과 고유 fixture이며 일반 시험 기록은0개입니다. 일반 files/shared_prefs15개·인증·AI 암호화 결과 바이트 동일. 기존 validation 선택A도 보존했습니다. 일반 앱 기존 설치 유지, TEST만 교체했습니다. Gemini/Worker/Secret/예산/모델OFF·Firebase 설정은 변경하지 않았습니다.

호스트 오디오 전달 문제를 -allow-host-audio 명시 재실행으로 복구했습니다. 수동 stop의 SDK 콜백 누락은 성공으로 바꾸지 않았으며 사용자 최종 지시에 따라 수동 완료 UI를 제거하고 자동 종료/명시적 취소만 제공합니다. 요청 경로의 수동 EOF 실험도 사용자 코드에서 제거했습니다. [실제 STT 결과](STT_STABILITY.md)·[PC별 실행 안내](STT_RUN_GUIDE.md)를 따릅니다.

다음 단계는 음성 확인→문제 초안→저장→운전자 인계의 실제 통합 검증과 승인된 Gleo 외부 Action/Callback입니다. 새 유료 모델/서버 배포 없이 진행하며 인식문을 임의 저장·자동 해결하지 않습니다. UI 팀은 integration의 독립 컴포넌트·canEdit/canWrite·초안/이력/AI 최소 전송 계약을 유지해야 합니다. 검증된 파일만 GitHub 연결 도구로 게시하며 DB/키/음성/개인 캡처/로그/Aside는 제외합니다. 최종 커밋은 완료 응답에서 보고합니다.
