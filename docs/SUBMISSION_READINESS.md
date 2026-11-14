# CARITDA 공모전 제출 후보 — 2026-11-14

PR #7 `7f147d9032bb00ffdc76e2e7fc9b6ce752e1c7a1`을 기준으로 준비한다. 기존 UI 통합과 내비게이션 100회 시험은 반복하지 않았다. main과 PR #2/#3/#6/#7은 병합하거나 갱신하지 않는다. 제출용 후속 브랜치는 `release/submission-readiness-20261114`이며 실제 최종 SHA는 해당 PR의 HEAD를 사용한다.

## 제출물과 실행

- 팀명: CARITDA. 앱: CARITDA(카잇다, 상표 검증 전 잠정 명칭).
- 제출 APK 목표 경로: `apk/CARITDA_CARITDA_release.apk`, package `com.nextdrive.pleos`.
- 기존 일반 앱은 Debug 인증서로 설치돼 있다. 새 Release 인증서로 바로 업데이트할 수 없으며 기존 앱을 삭제해 해결하지 않는다.
- 초기 준비의 `submissionCheck` 빌드 유형은 기존 앱을 보호하는 대체 경로다. 사용자는 Android Studio의 정식 `release`만 서명했으며, 확인용 APK를 추가 서명/생성하지 않는다. 실제 제출 APK 그대로를 별도 프로젝트 전용 Pleos AVD에 설치한다.
- 환경: Windows 11, Android Studio, Pleos Connect v2.1.0/API34, 2560×1440/density160. Vehicle2.3.2/NaviHelper3.0.5/Fused1.1.3/STT·TTS3.3.1. 실제 주행·실차 검증은 없다.
- 운전자 선택 → 내 차 상태 → 문제 남기기(수동 STT 또는 편집) → 저장 → 기존 AI 설명 확인/선택적 새 분석 → TTS → 다음 운전자 인계 → 정비 상담 준비 → 내비에서 정비소 찾기. 새 AI 요청은 별도 승인/동의/잔여량이 필요하며 이번 검증에서는 보내지 않는다.

## 서명 절차와 보호

사용자가 Android Studio의 Generate Signed App Bundle / APK → APK → Create new에서 별도 Release 키를 만들었다. 키/비밀번호는 PC에서만 보관하고 GitHub에 올리지 않는다. Debug 키·Keystore·Firebase 인증은 바꾸지 않는다.

정식 Gradle Debug/Validation/ValidationAndroidTest/Release/submissionCheck 및 lintDebug/lintRelease가 성공했다. CMD apksigner는 비밀번호 입력 문제로 완료하지 못했으나 사용자가 Android Studio에서 기존 키/alias로 `release` 생성에 성공했다. 이후 Release 재빌드·재서명·키 생성은0회다. 실제 원본은 `NextDrive-Pleos/app/release/app-release.apk`다.

```cmd
node scripts\Verify-SubmissionApk.mjs --apk NextDrive-Pleos/app/release/app-release.apk
```

`JAVA_HOME`과 `ANDROID_HOME`은 설치된 도구 경로를 사용한다. 필요하면 `--sdk <기존 SDK 경로>`를 지정한다. 검증기는 키/비밀번호를 읽거나 서명·빌드·설치를 하지 않는다. `--source-manifest`와 `--reference`로 준비 당시 입력과 서명 전 APK를 대조했고 `--stage`는 검증한 같은 바이트만 제출 파일명으로 복사했다. 기존 다른 APK는 덮어쓰지 않는다.

- 서명 검증 PASS, package `com.nextdrive.pleos`, versionCode1/versionName0.1.0, debuggable=false.
- SHA-256 `72a001e9d2f4b0d42f7aa68155f976fc7fc4846b975a2f33168555a9717539db`, 31,739,265 bytes.
- 공개 인증서 SHA-256 `389f24bac0558701ccf7d99573bd855743d9f5b8987916618317c4108dca49f1`. 개인키·비밀번호는 포함하지 않는다.
- 서명 메타데이터를 제외한 ZIP992개 파일의 압축 해제 SHA-256이 고정 Release와 모두 동일하다. 준비 당시 Release 입력109개도 변경0. 앱 Kotlin/리소스는 PR7 그대로이고 새 코드 차이는 검증·제출 준비용이다.
- 자세한 공개 산출물 메타데이터는 [apk/ARTIFACT.json](../apk/ARTIFACT.json)에 있다.

## 팀원 시험 해석과 지역 불일치

[Issue #5 최종 내비게이션 댓글](https://github.com/kyc66533-art/vehicle-app-workspace/issues/5#issuecomment-6870745566)은 수정된 판정 도구로 안내 화면 진입 100/100을 보고했다. 최초 55회와 재실행 45회가 유효 집계이며 중간에 실패한 도구 실행을 삭제된 앱 오류처럼 취급하지 않는다. 지역은 세종·창원의 서울 결과 10건과 광주광역시 대신 경기도 광주시 결과 5건을 제외하면 85/100이다. 도착·경로 정확도·실차·모든 SDK 이벤트 성공을 뜻하지 않는다.

CARITDA의 `PleosNavigationBackend.showRepairSearch()`는 `requestNavigationScreen(SearchPlace("자동차 정비소"))`만 보낸다. 도시 선택·결과 순위·첫 결과 자동 선택 코드는 없다. 팀원은 내비 앱 안에 `<도시>블루핸즈`를 입력하고 첫 항목을 골랐다. 따라서 현재 증거는 내비의 검색/모호한 지명/첫 결과 선택 문제를 가리키며 CARITDA가 목적지 좌표를 변조했다는 근거는 없다. 검색 공급자의 순위 원인까지 확정하지 않는다.

시연에서는 결과의 시·구·주소를 확인한 다음 목적지를 선택한다. `세종특별자치시`, `경상남도 창원시`, `광주광역시`처럼 구체적인 지역명 사용을 권한다. 이 검색어가 문제를 해결했다는 재시험 주장은 하지 않는다. 새 지도/API·임의 지역 필터·카카오·서버를 추가하지 않았다. `onRoutingCompleted` 미수신은 과거 기록대로 남기고 안내 화면 진입과 구분한다.

## 검사 오류와 최소 변경

- CP 기기 시간: 기존 검사는 화면에서 `ZoneId.systemDefault().id`를 찾았지만 서울은 의도적으로 `KST`로 표시된다. 기기 시간 선택과 정확한 epoch 포맷을 검사하도록 고쳤다. 앱의 시간·OS 시간대·원본 epoch는 바꾸지 않았다.
- PX03: SOC/거리/연료 3개와 스냅샷이 생겨도 다른 공개 신호의 첫 응답은 진행 중일 수 있다. 별도 Safety 구독도 자체 세션이다. 일괄 조회 및 Safety의 최초 결과가 끝난 후 기준을 잡고 기존 관측 불변/일괄 조회 세션 불변을 확인한다. 서로 다른 정상 구독을 중복 조회로 단정하지 않는다.
- `submissionReadinessOnly`는 고유 UUID 저장소를 사용하는 prepare 이전 분기다. 기존 `compatOnly`/`designOnly` 전체를 실행하지 않으며, 인자 없는 runner는 금지한다.
- 사람 Console 조작을 위한 `submissionSocLiveOnly`는 기존 SOC 검사와 같은 실제 SDK/TTS를 사용하되 대기만 4분으로 늘렸다. 합성 후속 ASR과 실제 SDK/TTS를 구분한다.
- 사람 발화 검사기는 READY와 실제 음성 시작 STARTED를 구분했다. 새 질문 전의 SDK READY를 기다리고, 호출 확인의 ‘네’를 새 후속 질문으로 잘못 세지 않도록 기준 이후의 추가 COMPLETE를 요구한다. 앱의 SpeechInput/마이크 표시/공식 final+ended 계약은 바꾸지 않았다.

## 이번 실측과 한계

검사 시작 당시 일반44DB/4,549행, validation1,408DB/17,378행을 새 비공개 사본으로 보존했다. 일반 핵심은 문제31/이벤트113/운전자10/초안0이다. 이전 PR7의 531 assertion·1,600배치는 당시177입력과 일치했고 이번에 전부 재실행하지 않았다. 일반 Debug APK SHA-256도 새 Debug 빌드와 동일하다.

사람이 P·0km/h·SOC100을 준비한 후 SDK100을 확인하고 18 변경을 안내했다. 실제 수신은 100→14→15→17→18→19였고, 이후 재진입에서 18 CALLBACK/GET이 확인됐다. 자동 음성은 같은 episode에서 실제 DONE 1건이며 사용자가 청취를 확인했다. 전체 자동 검사는 합성 후속 질문 시점의 최신값19와 고정 기대값18이 달라 FAIL했다. 앱 오류를 수정했다고 보고하지 않는다. 실제18 수신/홈 카드/음성은 개별 확인 결과다.

사용자가45로 변경한 뒤 실제45 CALLBACK과 홈 카드 해제를 확인했다. 이때 최초 복귀 근거31%는 사건 이력에 그대로 남고 최신 관측45%와 구분된다. 하나의 episode, 하나의 DONE 전달 이력을 유지했다. 문제가 자동 생성되거나 해결 처리되지 않았다.

100% 복원을 실제 SDK GET과 CarSafetyGate ALLOWED로 확인했다. 이후 사용자의 Android Studio/에뮬레이터 재접속으로 준비했던 첫 사람 발화 계측이 끊겼다. 재접속 후 사용자 직접 TEST 조작의 문제1건/생성1건/영수증1건/TTS DONE3건은 보존하되, 그 이력에는 저장 후 새 질문이 없어 전체 후속 검증으로 확대하지 않았다. 재접속이 마이크 회복과 연관됐다는 사용자 관찰은 기록하며 Android Studio 충돌을 직접 원인으로 확정하지 않는다.

검사 대기 조건만 보완한 뒤 새 UUID fixture에서 실제 사람 발화로 `CARITDA_HOME_MICROPHONE_PASS checks=6`을 받았다. 홈 READY→실제 호출→실제 증상1회 저장→실제 전체 TTS DONE→별도 마이크 버튼 없이 추가 발화 완료→같은 issue/revision/session→ASR 해제를 확인했다. 입력 fixture/가짜 FINAL/가짜 TTS는0이다. 출처는 **PLEOS_BOUNDED_STT**, 모델은OFF·새 답변 출처는LOCAL_RULE다. 독립 호출어 엔진·앱 종료 상태 시스템 호출어·새 Gemini 대화 성공을 뜻하지 않는다. 이 최종 발화 후 SOC100·정차 허용을 SDK로 다시 확인했다.

집중 회귀 `submissionReadinessOnly`12개 PASS: 실제 SDK 조회, 일괄 조회와 Safety 세션 구분, PX03 홈3회 재그리기 관측 불변, 서울/LA/UTC 기기 시간 UI·원본 불변. 시간대 전환은 validation 프로세스에 한정하며 OS 시간대를 바꾸지 않았다. 기존 compatOnly/designOnly 전체 PASS라고 보고하지 않는다.

Debug·Validation·AndroidTest·Release 빌드 성공. Lint Debug0오류/70경고, Release0오류/69경고. AndroidTest 수정은 관련 계측 APK만 다시 컴파일했다. 531개·1600배치·내비100회는 재실행하지 않았다. Unit NO-SOURCE를 성공 건수로 포함하지 않는다.

## Release 실행과 최종 보존 결과

같은 APK를 새 `CARITDA_Release_Check_20261114` AVD/emulator-5556/user10에 **설치·실행 PASS**했다. 기존5554의 패키지는 교체하지 않았다. 이미 설치된 공식 API34 Pleos v2.1.0 이미지만 사용해 프로젝트 내부에 별도 AVD를 만들었고 기존 userdata/SQLite/인증을 복사하지 않았다. 새 소프트웨어/드라이버 설치0·기존 AVD Wipe0이다. 새 AVD는 공식 Pleos 프로필 기본2GB RAM/2560×1440/density160을 사용했다.

Codex 계정 실행은 AEHD `ERROR_ACCESS_DENIED`로 막혔고, 사용자의 정상 CMD 계정에서 준비한 AVD를 실행했다. 권한 확대·드라이버 변경은 하지 않았다. 사용자 직접 첫 안내→새 운전자 ‘운전자 A’ 등록→확정→기록0건 홈을 확인했다. 첫 설치에는 등록 운전자가 없는 것이 실제 동작이며 기존 A/B 데이터가 복사된 것처럼 안내하지 않는다.

새 Release에서 수동 ‘차 상태 불러오기’ 후 **실제 SDK SOC100%·누적거리0km·KST 수신 시각**을 화면/XML로 확인했다. 차 정보/자동 안내/그래프의 별도 진입도 보존했다. 첫 안내 뒤 홈·차량 화면의 **첫 자동 조회는 미동작**했다. `maybeAutomaticRead()`가 안전 상태 callback에서만 실행되고 첫 안내 중에는 보류되며 `finishGuide()`와 운전자 확정은 재호출하지 않는 코드 경로와 일치한다. 수동 조회 경로는 PASS다. 현재 서명 APK를 유지하라는 지시에 따라 이 P1 앱 코드를 바꾸거나 다시 서명하지 않았다. 시연자는 첫 설치에서 ‘차 상태 불러오기’를 한 번 누른다. 자동 조회 전체가 정상이라고 보고하지 않는다.

새 AVD의 `ai.umos.inputmethod` fatal 로그2회를 확인했다. CARITDA fatal은 이번 관측에서 없고 등록·홈·수동 SDK 조회가 이어졌다. OEM 키보드 재시작 원인을 앱 문제로 확정하거나 해당 앱을 수정하지 않았다. 일반 앱 화면/원래31기록은 앞선 사용자 직접 확인과 동일 Debug APK 근거를 보존했다. Release의 전체 운전자 인계·STT/TTS·새 AI 인증·새 Gemini·새 경로 안내까지 모두 재시험했다고 주장하지 않는다.

최종 원본 비교: **기존1,452DB/21,927행, 변경0·삭제0·스키마/버전 동일·무결성/FK 정상**. 일반44DB/4,549행 및 문제31/이벤트113/운전자10/초안0, 기존 files/preferences17개가 모두 그대로다. validation의 기존1,408DB/17,378행도 보존했다. 사용자 재접속 TEST 조작으로 root 문제1건이 추가됐고(2→3), 기존 DB에는 SDK 관측·대화 등47행이 추가됐다. 고유fixture/복원조회용 새 DB37개를 더한 최종 validation은1,445DB/17,712행이다. ‘변경0’은 기존 행 비교이며 시험 데이터 증가까지 없었다는 뜻이 아니다.

validation 기존 비공개 파일557개 중 변경5개는 진단 캡처·마이크 제어/상태·profileInstalled·Firebase heartbeat이며 삭제0이다. 일반 인증·AI 캐시·동의 설정 불변, validation 인증·AI 캐시·음성 동의 원본도 변경 목록에 없다. 신규 Gemini operation0·Worker 배포0·한도/예산 정책 수정0이다. 실모델 잔여 승인0을 유지했다.

최종 사람 fixture는 문제1/CREATED1/receipt1, NOISE·UNRESOLVED·revision1, 같은 issue의 session ENDED, 암호화 turns8/실제 TTS DONE4/AI operation0을 재조회했다. 6개 계측 PASS와 실제 사람 입력을 뒷받침하며 단순 fixture 콜백 성공으로 대신하지 않았다.

### 제출 전 남은 항목

- 첫 설치의 안내 후 자동 조회 재시도는 P1. 현재 후보는 수동 조회를 안내한다. 새 수정본을 택하면 별도 소스 검증·APK 재생성/서명·해시 확정이 필요하다.
- 영상·기획서/서명·최종 GitHub SHA/브랜치 표기·저장소 공개 또는 ZIP 대안은 제출자가 마감 전 확정한다. 공개 범위 변경·메일/외부 제출은 이번 작업에서 하지 않는다.
- 이 APK의 새로운 AI 로그인/기기 등록·실모델 왕복, 외부 Gleo·실차·타 PC 재현은 NOT_TESTED다. 기존 분석/대화 코드를 보존했고 실제 신규 호출은 금지 범위라0회다.
- 세종/창원/광주 결과 지역 일치 문제와 onRoutingCompleted 미수신은 해결했다고 주장하지 않는다. 결과 주소 확인이 필요하다.
- 기록 시각의 기기 시간대와 차량 수신 시각의 기본 KST가 다를 수 있다. 원본 epoch 오류가 아니며 시연에서 표시 기준을 설명한다.

## 공식 제출 조건

[공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)와 [FAQ](https://appdevchallenge.auto-hmg.io/faq)를 2026-11-14 열람했다. 접수양식(기획서)·참가자 서명이 필수이며 영상/GitHub/APK는 선택 가산점 산출물이다. 마감은11-15 23:59이며 APK는 실행 가능한 Release, `apk/팀명_앱이름_release.apk` 또는 용량 초과 시 GitHub Releases로 제공한다. 저장소 URL·최종 commit hash·실행 방법·설정·실제 에뮬레이터 버전/화면 규격/APK 위치를 명시한다.

Private 제출은 가능하나11-16까지 Public 전환 후11-23까지 유지해야 개발 산출물 가산점이 반영된다. 공개가 어려우면 마감 전 소스/APK ZIP 다운로드 주소를 보내는 대안이 있다. 이번 작업은 공개 범위 변경·계정 초대·외부 제출을 수행하지 않는다. 키·인증·개인 기록·백업·로그·일반 앱 개인 캡처는 제출하지 않는다.

공식 기술 근거: [NaviHelper3.0.5](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [SearchPlace](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/entry), [Android 앱 서명](https://developer.android.com/studio/publish/app-signing). SearchPlace 본문이 이번 텍스트 열람에는 제공되지 않아 새 API 계약을 추측하지 않았다. 기존 PR6의 공식 sources.jar 대조와 실제 컴파일된 호출을 보존했다.
