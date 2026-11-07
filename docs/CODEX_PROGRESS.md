# Codex 개발 진행 기록

마지막 업데이트: 2026-11-08 00:17:00 +09:00 (Asia/Seoul).

## 최신 작업 — Simple UI·한 화면 첫 안내·사용법 실제 구현

### 구현한 화면과 데이터 보존

- Aside 온보딩 보고서 e5473532c639cd69d8e0a8017697ad6cbe66783d와 기존 UI 보고서/AC/최신 소스·모델을 읽고 실제 Kotlin 화면에 반영했습니다. 연구 파일은 수정하지 않았습니다.
- 홈을 기록 이름/변경·확인 가능한 정보 범위·읽지 않은 전달 기록/관리 중/완료 건수·기록 보기·문제 남기기·차량 정보로 단순화했습니다. 긴 문제 목록·상담·점검·SDK 정보는 각각의 화면에 유지했습니다. 홈에서 기록 보기는 이전 필터를 초기화해 관리 중 기록을 우선, 모두 완료이면 완료 목록을 엽니다.
- 차량 기본 화면은 충전량·주행거리·단위·실제 수신 시각·오래됨·에뮬레이터 출처·차량값 불러오기에 집중했습니다. 값32sp/항목24sp/시간22sp/최소76dp 버튼, 자릿수 구분을 적용했습니다. 원본 값은 그대로 저장합니다. 연료 미지원은 제공되지 않는 항목에서 설명하며 0%로 바꾸지 않습니다.
- SDK/Capability/원본 상태/세션/오류/내부 출처/밀리초 시각은 불러오기 상세로 이동했습니다. 마지막 성공값과 최신 실패를 분리하고, 100회 이상 실패 뒤에도 이전 성공값을 찾는 읽기 전용 조회를 추가했습니다. 미수신·유효0·미지원·권한 없음·실패를 구분합니다.
- 신규 안내는 사용자가 지정한 한 화면과 운전자 선택/안내 건너뛰기입니다. 기존 설치/기록이 있으면 자동 제안을 생략합니다. 안내 완료/skip은 preference만 변경하고 운전자 확정/SDK 조회/읽음/안전 변경을 하지 않습니다. 사용법의 여섯 과제와 처음 안내 재보기·원래 화면/초안 복귀를 구현했습니다. 테마 선택은 사용법에 옮겼습니다.
- DB v2/운전자 영구 ID/A·B/작성 당시 이름/인계/조치/재발/초안을 유지했습니다. 관측 DB 버전과 원본 저장 구조, PleosReadClient·SDK2.3.2·CarSafetyGate는 변경하지 않았습니다. 자동 감지/상시 수집/AI/외부 연동은 추가하지 않았습니다.
- 화면이 보이는 동안 오래됨을 재평가하지만 SDK를 반복 호출하지 않습니다. 10분은 표시용 기준이며 고장 판정이 아닙니다. UNKNOWN/RESTRICTED 입력 차단과 안내 중단 상태를 유지하며, 실패한 초안 저장을 성공했다고 안내하지 않습니다.

### 최종 빌드와 실제 검사

1. 기존 앱을 force-stop하고 문제/관측 DB를 .tools/simple-ui-backup 및 앱 전용 비교 사본에 보존했습니다. 앱/DB/AVD 초기화나 삭제는 하지 않았습니다. 개인 백업은 게시하지 않습니다.
2. 기존 Windows/JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0 캐시의 offline assembleDebug+assembleDebugAndroidTest 성공: BUILD SUCCESSFUL in 1m 1s, exit0. 앱/계측 APK 모두 기존 Pleos Connect v2.1.0 emulator-5554에 install -r Success.
3. 최종 CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6, CARITDA_UI_PASS checks=27. PRODUCTION_MIGRATION_PASS로 원래 문제5건/이벤트16건과 원래 관측 보존도 확인했습니다. 회귀가 추가한 검증용 프로필/기록은 삭제하지 않았습니다.
4. 신규 CARITDA_ON_PASS checks=32: 안내 정책/UNKNOWN 차단/skip 무부작용/재실행/재보기/초안 복귀/홈 필터/기술 정보 분리/실제 SDK/기존 전체 행과 원래 관측 보존. 별도 fixture에서 유효0/미수신/성공 뒤105회 실패/15% 무진단/10분 경계/미래 시각/TEST_ONLY 배제를 검사했습니다. 가상값을 운영 관측 DB에 넣지 않았습니다.
5. 다섯 주요 화면 × 다섯 콘텐츠 폭320~1024dp × 글꼴100/130/150/200% × 두 테마 = 200개 렌더링 조합의 넘침/말줄임/최소76dp 버튼 검사 통과. 앱 리소스와 테마는 원복했고 OS 전역 설정은 바꾸지 않았습니다. 실제 Pleos의 홈/차량/사용법/재보기 화면 캡처도 검토했습니다.
6. 현재 실제 에뮬레이터 SDK 응답: SOC15%, 주행거리 원본1000000.0km(화면1,000,000km), 각 READ·RECEIVED. 연료 ERROR_NOT_SUPPORTED_PROPERTY·UNSUPPORTED·숫자 없음. PLEOS_EMULATOR_SDK 출처이며 실차/앱내 가상값과 구분합니다. 이전100%/0km 기록은 과거 수신 사실로 보존합니다. 기기 수신 시각과 PC의 보고 시각은 구분합니다.
7. 최종 앱 APK SHA256: 9cafeb74e304a06067c0830792c0943fa48b2196c93ba34926902adf949efef3. 로그/화면은 app/build/validation에 보존하며 APK/DB/키/캐시와 함께 게시하지 않습니다.

### 실패·부분·미검증

- 초기에 로컬 소스 구문 오류로 컴파일이 한 번 실패했고 수정 후 최종 빌드/전체 검사가 통과했습니다. 깨진 중간 코드는 게시하지 않습니다.
- ON01~ON24: 통과13/부분8/미검증3. 상세 범위는 docs/ONBOARDING_ACCEPTANCE.md에 별도 기록했습니다. 32개 assertion 통과가 24개 기준 전체 통과라는 뜻은 아닙니다. 기존 AC 판정도 새 ON 결과로 덮어쓰지 않았습니다.
- 신규 설치 UI는 데이터 삭제 없이 pending preference를 임시 설정하고 원복해 검사했습니다. 실제 빈 신규 설치, 실제 주행 RESTRICTED/OS 차단 전환, 저장소 장애 주입, 전체 안내/오류/상세의 모든 창 조합, 물리 키보드/TalkBack/rotary·초보/고령 사용자 독립 과제·실차·Release는 미검증입니다.
- 이전 offline Lint는 lint-gradle31.10.1 캐시 부재로 실패했습니다. 이번에는 같은 다운로드를 반복하지 않았고 Lint 통과로 보고하지 않습니다. deprecated API 경고가 남아 있습니다.

### 변경 파일·공유·다음 작업

사용자 PC에 실제 반영한 12개 파일:

- AGENTS.md, README.md, NextDrive-Pleos/README.md
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/ConsultationSummary.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt
- 신규 NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ui/GuideState.kt
- 신규 NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ui/VehiclePresentation.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt
- 신규 NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/OnboardingChecks.kt
- 신규 docs/ONBOARDING_ACCEPTANCE.md, docs/CODEX_PROGRESS.md

기준 main e5473532c639cd69d8e0a8017697ad6cbe66783d. GitHub 연결 도구를 통한 게시를 사용합니다. 게시 직전 HEAD/대상 SHA를 재확인하고 최신 트리/부모와 기대 HEAD를 사용해 명시적 검증 파일만 반영합니다. Aside 및 나머지 파일 SHA와 게시 커밋 부모를 대조하며 최종 SHA는 응답으로 보고합니다. 로컬 Git push가 아닙니다.

이미 교체 설치한 앱에서 이름 선택→운전 시작→차량 정보→차량값 불러오기→사용법/처음 안내 재보기를 확인할 수 있습니다. Android Studio 파일 수동 편집·재설치·데이터 삭제는 필요 없습니다. 다음은 ON 부분/미검증의 실제 창·접근성·사용자 이해 과제를 우선 검증하고 캐시 확보 가능 시 Lint를 완료하는 단계입니다. 자동 감지·AI·외부 연동은 별도 지시 후 진행합니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 22:15:17 +09:00 (Asia/Seoul).

## 최신 작업 — Aside UI·운전자 M0 구현·운영 DB 이전·실제 실행 성공

### 구현 결과

- 이름/별명 등록, 영구 ID, 후보 카드 선택→명시적 운전 시작, 선택 취소, 이름 수정·보관. 최근 운전자와 이번 이용의 확정을 구분하며 콜드 스타트는 재확인합니다. 차량 시동/OS 계정과 무관합니다.
- 홈/문제/차량 분리. 홈은 현재 기록 운전자·문제 기록 CTA·미해결/미확인 건수·최근 차량 상태 중심입니다. 긴 목록·조치·재발·상담은 별도 화면입니다. 운전자 수정·보관은 관리 화면에 분리했고 운전 시작은 고정 하단에 둡니다.
- 사용자 직접 기록 출처, 명시적 읽음 확인, 원 작성자 snapshot, 상담 범위/시간대/외부 미전송 표시를 유지했습니다. 자동 감지·Gleo·모바일/정비소 API는 구현하지 않았습니다.
- SQLite v1→v2 additive migration: 기존 열/행/ID/시각/상태/revision/순서를 그대로 유지하고 drivers/작성자 ID·당시 이름/drafts/idempotency 인덱스를 추가했습니다. 기존 A/B ID는 유지하며 신규 설치에는 가짜 A/B를 만들지 않습니다. 관측 DB/SDK 구현은 변경하지 않았습니다.
- 새 문제·조치·재발 초안의 원 작성자/대상/revision/내용을 SQLite에 보존합니다. 작성 중 다른 운전자 전환을 막고 저장·명시적 취소·계속 작성으로 구분합니다. 저장과 해당 초안 제거는 트랜잭션 처리하며 draft ID로 반복 저장을 막습니다. 제한 중에는 비확정 초안 보존만 허용합니다.
- 시스템/밝은/어두운 테마, 주요 버튼 76dp 이상·24sp·내용에 따라 높이 확장, 선택 체크·focus 테두리·지속 오류 문구, 좁은 창/큰 글꼴의 세로 메뉴, 목록 위치 보존과 IME adjustResize를 적용했습니다. 실제값 freshness는 표시용 10분 기준이며 고장 기준이 아닙니다.

### 단계별 검증과 데이터 보존

1. 기존 앱 force-stop 후 문제/관측 DB+journal와 이전 APK를 .tools/ui-migration-backup에 바이너리 보관하고 앱 전용 비교 사본을 만들었습니다. 개인 데이터이므로 게시하지 않습니다. 복구 절차는 docs/UI_MIGRATION_PLAN.md에 먼저 작성했습니다.
2. 모델/마이그레이션 단계 빌드 성공 후 운영 Activity를 열기 전에 독립 v1 fixture 18개 검사를 통과했습니다. 중간 예외로 스키마·행·user_version rollback, 모든 원래 필드/관계 비교, 반복 실행, 재연결, snapshot, 보관, 초안 중복 확정 방지를 검사했습니다.
3. 실제 운영 DB 이전 후 기존 문제 5건·이벤트 16건의 모든 원본 필드와 관계 보존, 원래 관측 행 보존을 비교 사본으로 확인했습니다. 이후 테스트가 추가한 행은 별도이며 기존 행을 지우지 않았습니다.
4. 최종 앱/계측 APK assembleDebug+assembleDebugAndroidTest: BUILD SUCCESSFUL in 55s, exit 0. 기존 Windows/JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0, 기존 캐시 offline 빌드. 앱·테스트 APK install -r 둘 다 Success. 실제 Pleos Connect v2.1.0/API34 emulator-5554에서 실행했습니다.
5. 최종 MIGRATION_PASS checks=18, CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6, CARITDA_UI_PASS checks=27. 합계 75개 검사이며 Aside AC28 전부 통과라는 뜻은 아닙니다.
6. 새 UI 검사는 이름 오류/등록/후보 취소/시작, dirty 전환 차단, Activity 재생성, 원 작성자 초안 복원, 저장 중복 방지, 조치·재발·명시적 취소·상태, 이름 변경과 보관 후 모든 기존 기록 보존을 포함합니다.
7. SDK 2.3.2 실제 응답 유지: EV SOC=100%, 누적 주행거리=0.0km, 각 Capability READ. 연료는 ERROR_NOT_SUPPORTED_PROPERTY로 값 없이 UNSUPPORTED 저장. PLEOS_EMULATOR_SDK이며 앱내 가상값/실차와 구분합니다. 최종 통합 세션 48861d71-6288-4b38-afc3-9dd1c0411794.
8. 320/480/600/840/1024dp 콘텐츠 폭에서 홈 CTA 폭·76dp 터치 높이, 테스트 프로세스 fontScale 100/130/150/200%에서 CTA 확장/말줄임 없음 검사 통과. 앱 리소스와 테마 설정은 테스트 후 원복했으며 OS 전역 글꼴/보안 정책은 변경하지 않았습니다. 두 테마 CTA 실제 색상의 대비 4.5:1 이상 확인. 실제 화면 캡처를 검토했습니다.
9. 추가 실제 키보드로 CARITDA_draft_restart_test 임시 초안 입력→force-stop/cold start→원 작성자 B로 운전 시작→계속 작성에서 내용 복원 확인. 사용자 확인 동작으로 해당 임시 초안만 취소했고 확정 문제/이력은 유지했습니다.

### 실패·부분 검증·남은 제한

- 첫 확장 검사는 별도 상태 dialog를 Activity decor에서 찾는 테스트 오류로 중단됐습니다. 접근성 노드로 선택하도록 수정 후 전체 통과했습니다. 그 과정의 검증용 프로필/카드도 삭제하지 않았습니다. 정상 완료한 검증 프로필은 보관 처리했습니다.
- offline lintDebug는 exit 1: com.android.tools.lint:lint-gradle:31.10.1 캐시 부재로 실행되지 못했습니다. 동일 네트워크 다운로드를 반복하지 않았으며 Lint 성공으로 기록하지 않습니다.
- 실차/Release/실제 주행 UXR 전환/서비스 단절·저장소 고갈 주입, TalkBack·물리 키보드·rotary 종합 검사, 모든 화면의 전체 글꼴·창 조합, 고령/신규 사용자 과제는 미검증입니다. deprecated onBackPressed 경고가 남습니다.
- AC01~AC28별 통과/부분/미검증과 범위를 docs/UI_ACCEPTANCE.md에 명시했습니다. UI 폭/폰트 검사는 콘텐츠를 제한한 계측이며 모든 실차 패널 검사와 다릅니다. 선택/확정·cache/비파괴 이전·native View 설계 조정도 기록했습니다.

### 변경 파일 / 공유

- AGENTS.md: 최신 UI M0 범위와 데이터 보존 지침.
- app/src/main/java/com/nextdrive/pleos/model/IssueModels.kt: 영구 운전자와 snapshot/초안 모델.
- data/IssueRepository.kt, data/SqliteIssueRepository.kt: 비파괴 v2·운전자/초안 저장.
- domain/CaritdaService.kt: 명시적 확정, ID 기반 인계, 원 작성자 draft 규칙·중복 방지.
- MainActivity.kt, app/src/main/AndroidManifest.xml: 실제 UI/탐색/테마/초안/IME.
- app/src/androidTest/.../MvpScenarioInstrumentation.kt, 신규 MigrationChecks.kt: 기존 회귀+UI/SDK/이전 검사.
- 신규 docs/UI_MIGRATION_PLAN.md, docs/UI_ACCEPTANCE.md, docs/CODEX_PROGRESS.md, README.md, NextDrive-Pleos/README.md.
총 14개 소스·설정·문서를 사용자 PC에 반영하고 GitHub 연결 도구로 게시합니다. 기존 SDK/관측 코드와 Aside 연구는 변경하지 않았습니다. APK SHA256 03529f7cabfb4e2db6a50d708fc345f0aeef999d5636f42c64acb82bbd6db475. 앱/테스트 APK·로그·화면·개인 DB·백업·키·캐시는 게시하지 않습니다.

작업 기준 main 6f6c281ed40c702378643ee2ba93356ad4f79413. 게시 직전 최신 HEAD/대상 SHA를 재확인하고 최신 트리/부모를 잇는 커밋과 기대 HEAD 갱신을 사용합니다. 원격 파일 SHA/부모 및 Aside 포함 나머지 파일 보존을 확인하며 최종 커밋은 응답으로 보고합니다. 로컬 Git push가 아닙니다.

사용자는 설치된 앱에서 이름 등록→카드 선택→운전 시작을 사용할 수 있습니다. 파일 편집/재설치/DB 초기화는 필요 없습니다. 다음 우선 작업은 부분 검증된 실제 창·접근성·입력 제한 전환과 Lint 완료입니다. 사용자 기능 확장은 별도 지시 후 진행합니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 21:18:19 +09:00 (Asia/Seoul).

## 최신 작업 — 실제 Pleos SDK 수신·저장·회귀 검증 성공

### 직접 수행한 결과

- 사용자가 부팅한 기존 Pleos Connect v2.1.0에 ADB emulator-5554 연결, boot_completed=1 확인.
- 이전 최종 빌드 앱/계측 APK를 install -r로 교체 설치: 둘 다 Success. 기존 앱/SQLite/AVD를 삭제하거나 초기화하지 않았습니다. 동일 APK SHA256 7a6412cc0cfc4b5aec24aa903540b485b345c2f90d8e1282dfdbaccf55056dd1이며 이전 빌드 성공(exit0,47초) 후 소스를 바꾸지 않아 재빌드는 생략했습니다.
- SDK initialize 후 실제 Capability/get 콜백으로 서비스 연결 응답을 확인했습니다. CAR_ENERGY/CAR_DRIVE/VEHICLE_BINDING granted=true.
- EV_SOC: Capability READ, 원본 100 %, receivedAt 1794053740683. ODOMETER: READ, 원본 0.0 km, receivedAt 1794053740751. 둘 다 supported=true, valid=true, RECEIVED로 저장했습니다.
- FUEL_PERCENT: Capability 콜백 ERROR_NOT_SUPPORTED_PROPERTY. 읽기를 호출하지 않고 UNSUPPORTED/supported=false/valid=false, 원본·단위·수신시각 null로 저장했습니다. 권한 거부나 연료0%로 해석하지 않습니다.
- 세 항목 모두 source=PLEOS_EMULATOR_SDK, 세션 06b5899d-baad-473e-9ed0-ce71ed35093f. 에뮬레이터 Vehicle Service 시뮬레이션 응답이지 실차/앱내 가상값이 아닙니다.
- CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6 전체 통과. 실제 UI 흐름과 SQLite, 기존 문제 ID 보존, SDK 응답 저장/Activity 재시작 복원 검사입니다. 시험용 문제 한 개가 추가됐고 기존 문제는 유지했습니다.
- 추가 force-stop → cold start Status ok. 새 SDK 읽기 없이 기존 세션·SOC·주행거리·연료 미지원이 실제 화면에서 복원됨을 UI XML/스크린샷으로 확인했습니다. 처음 XML에는 스크롤 밖 연료 카드가 없어 검사 실패했으나 스크롤 후 같은 세션/연료 상태를 확인했습니다. 앱/저장 오류가 아니었습니다.

### 수정·게시 파일

앞서 로컬에만 있던 실행 검증 성공 소스 4개: MainActivity.kt, 신규 vehicle/PleosReadClient.kt, AndroidManifest.xml, MvpScenarioInstrumentation.kt. 이번에 소스를 다시 변경한 것은 아니며 이전 구현을 실제로 검증한 뒤 게시합니다.
문서 갱신: docs/VEHICLE_SDK_232_RUNTIME.md, SDK_CAPABILITY_MATRIX.md, docs/CODEX_PROGRESS.md, README.md, NextDrive-Pleos/README.md. 총 게시 대상 9개. 기존 ObservationStore와 수동 DB 스키마는 변경하지 않았습니다.


추가 검수에서 입력 폼의 처음 초안에 취소 버튼이 없던 점과 취소 후 빈 폼이 남던 점을 수정했습니다. 새 문제·조치 폼에 항상 취소를 제공하고 확인 후 홈으로 돌아갑니다. 이 경로를 추가 회귀로 검증했습니다. 중단된 검사의 초안만 정차 UI의 명시적 취소로 정리했으며 기존 확정 기록은 유지했습니다.
### 한계·다음 작업

실제 권한 거부/타임아웃/서비스 끊김 실패 조건은 이번에 강제로 만들지 않았습니다. 해당 방어 로직의 모든 런타임 경로를 검증했다고 주장하지 않습니다. 연료는 현재 이미지에서 미지원입니다. 실차·Release·Lint·실제 주행 전환은 이번 미검증. 자동 감지/반복 수집/운전자 관리 개편/UI 전면 개편은 구현하지 않았습니다.

사용자 추가 파일 편집/재설치/SDK 설치는 필요 없습니다. 현 앱에서 차량 데이터 연결 확인 → 차량 데이터 읽고 저장하기로 다시 확인할 수 있습니다. 다음 기능 후보는 수동 문제와 최근 관측의 ID/시각/출처 연결입니다.

### 공유와 보존

작업 시작 main 50d27034cba44f0dc8a7027d4c38d00605458d68을 읽고 검증했습니다. 최신 HEAD/대상 SHA를 다시 확인한 뒤 최신 트리를 부모로 GitHub 연결 도구를 통해 검증된 파일만 게시합니다. 커밋 부모/파일 SHA/나머지 파일과 Aside 연구 보존을 원격에서 확인합니다. 최종 커밋은 응답으로 보고하며 자기 SHA를 넣기 위한 반복 커밋을 하지 않습니다. 로컬 Git push가 아닙니다.

로컬 증거 app/build/validation/sdk232-runtime.txt, sdk232-home/restored/fuel.xml, sdk232-restored.png와 앱/계측 APK는 게시하지 않습니다. 사용자 DB·키·SDK 바이너리·캐시는 공유하지 않습니다. 이전 실패 기록을 삭제하지 않았습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 21:05:11 +09:00 (Asia/Seoul).

## 최신 작업 — Vehicle 2.3.2 확보 확인, 실제 읽기 구현과 빌드 성공 / 런타임 대기

### 완료한 작업

- 사용자 보고(2.3.1 HTTP404, 2.3.2 Sync/다운로드/Build APK 성공)를 현재 상태로 반영했습니다. 기존 실패 기록은 보존합니다. 실제 로컬 Gradle이 2.3.2이고 Vehicle-2.3.2.aar, vehicle-aidl-1.1.1.aar, vehicle-data-1.1.11.aar와 공식 sources JAR이 존재함을 확인했습니다.
- 공식 공개 SDK 소스/배포 AAR에서 SOC/주행거리/연료 Capability·읽기 함수, nullable 타입, %/km 단위와 RequiresPermission을 확인했습니다. 제어 API를 호출하거나 특권 권한을 요청하지 않았습니다.
- 로컬 PleosReadClient에 Vehicle 초기화/해제, 세 Capability 확인, READ/READ_WRITE만 조회, 수치 검증, 출처/원본/단위/시각/세션 저장, 실패·타임아웃 분리, 생명주기 정리·늦은 콜백 무시를 구현했습니다. initialize 반환만으로 연결 성공을 표시하지 않습니다.
- MainActivity의 기존 최소 차량 화면에 읽기 버튼과 상태를 연결하고 Manifest에는 CAR_ENERGY/CAR_DRIVE만 추가했습니다. SDK가 VEHICLE_BINDING을 병합합니다. 기존 수동 문제 DB와 ObservationStore 스키마는 그대로 유지했습니다.
- 기존 24개 계측 시나리오를 유지하고, SDK 실제 결과/최소 한 개 값/재시작 저장 복원을 확인하는 계측으로 갱신했습니다. 이는 준비한 테스트이며 이번에 실행하지 못했습니다.

### 실제 빌드 결과와 해결한 오류

- 첫 컴파일은 배포 AAR의 VehicleException 난독화와 runtime-only ErrorCode 의존성으로 실패했습니다. 예외 타입을 반사 호출하지 않고 Exception/공개 ErrorCode 이름으로 처리했습니다. vehicle-data를 implementation으로 추가한 시도는 테스트 전이 의존성의 offline 해석이 실패했습니다. 최종 설정은 runtime 그래프를 유지하며 compileOnly, transitive=false로 공개 ErrorCode만 노출합니다.
- 최종 :app:assembleDebug :app:assembleDebugAndroidTest → BUILD SUCCESSFUL in 47s, exit 0. JBR21.0.8/Gradle8.11.1/Platform34/Build-Tools35.0.0, 기존 캐시 offline 빌드입니다. 새 APK와 계측 APK가 실제 생성됐습니다.
- aapt permissions: CAR_ENERGY, CAR_DRIVE, SDK의 VEHICLE_BINDING. 제어 권한 없음.
- 앱 APK SHA256: 7a6412cc0cfc4b5aec24aa903540b485b345c2f90d8e1282dfdbaccf55056dd1. 로그 app/build/validation/sdk232-build.txt. APK/로그/SDK sources/키/DB는 게시하지 않습니다.

### 런타임 실패·미검증

- 시작 시 ADB 연결 기기가 없었습니다. 기존 Pleos_Connect_v2.1.0을 초기화 없이 시작했으나 .android/emu-last-feature-flags.protobuf.lock 쓰기 error 5가 반복되어 이번에 시작한 emulator/qemu 프로세스만 중단했습니다. 보안 제한 우회나 SDK/AVD 재설치를 하지 않았습니다. AVD 시작 로그는 .tools에 보존합니다.
- 일반 Android Studio의 Device Manager에서 기존 AVD를 부팅해 달라는 사용자 조작 요청을 전달했습니다. 실제 앱 설치/실행, SDK initialize/서비스 응답, Capability, 실제값 수신/저장, 런타임 오류 분류와 이번 회귀 검사는 미수행입니다. 실제 수신값은 아직 0개입니다.
- 과거 24+5 검사 성공은 과거 기록이며 이번 수정본의 검사 성공으로 표현하지 않습니다. Lint/Release/실차/실제 주행 전환도 이번 미검증입니다.

### 변경 파일 및 게시 범위

사용자 PC에 직접 반영: AGENTS.md, app/build.gradle.kts(사용자가 적용한 2.3.2 유지 + ErrorCode 컴파일 노출), app/src/main/AndroidManifest.xml, MainActivity.kt, 신규 vehicle/PleosReadClient.kt, app/src/androidTest/.../MvpScenarioInstrumentation.kt, SDK_CAPABILITY_MATRIX.md, docs/VEHICLE_SDK_232_RUNTIME.md, docs/CODEX_PROGRESS.md.

빌드 검증한 app/build.gradle.kts와 AGENTS.md/결과 문서 3개만 우선 GitHub 연결 도구를 통해 게시합니다(총 5개). 런타임 미검증 Manifest/MainActivity/PleosReadClient/계측은 로컬에 보존하고 실제 AVD 검사 전 게시에서 제외합니다. 원격 앱에는 아직 새 읽기 UI가 반영되지 않습니다. 사용자 PC에서는 새 SDK 포함 APK를 실행할 준비가 되었습니다.

작업 시작 main c9d5469cef2913ca1ae497d0da8efa69b651f380과 Aside 조사 파일을 확인했습니다. 최신 HEAD/대상 SHA를 다시 대조하고 기존 main 트리/부모를 잇는 커밋, 기대 HEAD 갱신, 원격 해시·부모·Aside 포함 나머지 파일 보존을 확인합니다. 게시 커밋은 최종 보고하며 로컬 Git push 성공으로 표현하지 않습니다.

다음 최우선은 기존 AVD 부팅 → ADB 교체 설치 → 기존 24개 회귀와 실제 SDK 계측 실행 → 결과에 따른 연결/Capability 문제 해결입니다. 사용자가 편집할 파일은 없습니다. UI 개편·운전자 관리 확장·자동 이상 감지는 진행하지 않았습니다. 자세한 실행 순서는 docs/VEHICLE_SDK_232_RUNTIME.md에 있습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 20:31:59 +09:00 (Asia/Seoul).

## 최신 작업 — 실제 앱 Vehicle 의존성 추가 후 다운로드 실패 재확인

- 공식 시작 가이드·샘플 Gradle의 Vehicle 2.3.1과 settings.gradle.kts의 공식 Maven URL을 확인했습니다. settings 파일은 변경하지 않았습니다.
- 사용자 PC 로컬 `NextDrive-Pleos/app/build.gradle.kts`에 `implementation("ai.pleos.playground:Vehicle:2.3.1")`을 추가했습니다. 이전과 달리 init probe가 아니라 실제 앱 의존성으로 다운로드/assembleDebug를 실행했습니다.
- 결과: Gradle exit 1, BUILD FAILED in 41s. :app:checkDebugAarMetadata → debugRuntimeClasspath → Vehicle 2.3.1 POM의 공식 Nexus GET이 `Permission denied: getsockopt`로 실패. Google/Maven Central도 동일 오류였습니다. DNS는 정상 해석되었으며 HTTP 401/403/404 응답은 받지 못했습니다. 인증·버전 문제로 확정할 근거가 없습니다.
- 사용자/프로젝트 내부 Gradle 캐시에서 SDK AAR 없음. SDK 포함 Sync 동등 의존성 해결·빌드 실패. SDK 초기화/서비스 연결/Capability/실수신/수신값 저장은 미수행입니다. 기존 APK와 앱/SQLite 데이터는 보존했습니다. 이번 회귀 검사는 빌드 선행 실패로 실행하지 않았습니다. 과거 24+5 성공은 과거 기록으로 유지합니다.
- 현재 실행 정책은 network restricted 및 sandbox_approval 요청 불가입니다. 범위를 좁힌 네트워크 승인 창도 현재 도구로 요청할 수 없습니다. 사용자 승인을 받은 뒤 권한 확대 실행했다고 주장하지 않습니다. 보안 설정 변경·Sandbox 해제·우회·동일 명령 반복은 하지 않았습니다.
- 로컬 파일 편집은 완료했으므로 사용자는 일반 Android Studio에서 File > Sync Project with Gradle Files만 실행하면 다운로드를 확인할 수 있습니다. 성공 시 External Libraries/Build APK(s)를 확인합니다. 기존 앱 삭제·SDK/AVD 재설치는 불필요합니다.
- 현재 로컬 빌드는 미다운로드 의존성으로 막혀 있습니다. 미검증 app/build.gradle.kts는 로컬에 보존하고 GitHub 게시 대상에서는 제외합니다. 원격 앱 소스/빌드 설정은 기존 검증본을 유지합니다. 이 차이를 명확히 보고합니다.

### 변경 파일 및 다음 단계

로컬만: `NextDrive-Pleos/app/build.gradle.kts`.
문서 게시 대상: 신규 `docs/SDK_DOWNLOAD_RETRY.md`, 갱신 `SDK_CAPABILITY_MATRIX.md`, `docs/CODEX_PROGRESS.md`.
임시 로그: `NextDrive-Pleos/app/build/validation/sdk-retry-build.txt` (게시 제외). 기존 앱 소스/UI, 연구 파일, 사용자 DB/키, 전역 설정은 수정하지 않았습니다.

작업 시작 최신 main은 Aside의 `a5c738aed91b43d98d4234a3d9718c0affe9c7d8`이며 부모가 기존 Codex `1b55aeb685fa252f197d4760553813bf5457389c`임을 확인했습니다. CARITDA_UI_UX_RESEARCH.md를 읽고 이름/별명 등록, 후보 선택→운전 시작 확정, stable ID와 당시 이름·초안·이력 보존, 홈/문제 분리, UXR 우선 원칙을 향후 별도 UI 단계의 기준으로 확인했습니다. 이번에는 구현하지 않았습니다.

다음 최우선 작업은 네트워크가 허용되는 일반 Android Studio에서 아티팩트 확보/빌드 확인 → 실제 AAR API/권한 확인 → typed read adapter → Capability/실수신/저장/회귀입니다. 사용자 조작·오류 분류는 docs/SDK_DOWNLOAD_RETRY.md에 정리했습니다.

GitHub 연결 도구를 통한 문서 게시만 수행합니다. 최신 main 재확인, 대상 SHA 대조, 최신 트리 기반 커밋/기대 HEAD 갱신, 나머지 SHA와 Aside 연구 파일 보존을 확인합니다. 커밋 ID는 최종 응답으로 보고하며 로컬 Git push 성공으로 표현하지 않습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 17:13:57 +09:00 (Asia/Seoul).

## 최신 작업 — CARITDA v0.2 SDK 수신 시도 및 기반 구현 (핵심 목표 미완료)

### 완료 작업 / 변경 파일

- 로컬 사용자 PC NextDrive-Pleos에 구현: MainActivity의 차량 연결 확인 화면, vehicle/ObservationStore.kt의 별도 append-only SQLite, MvpScenarioInstrumentation.kt의 추가 5개 관측 검사. 기존 수동 문제 DB·테이블·데이터를 변경/삭제하지 않았습니다.
- AGENTS.md에 현재 v0.2 범위와 Aside 병렬 커밋 보존 규칙을 추가했습니다.
- SDK_CAPABILITY_MATRIX.md, docs/CARITDA_V02_VALIDATION.md, docs/CODEX_PROGRESS.md에 현재 사실과 다운로드 절차를 기록했습니다. 총 게시 대상 7파일. Aside 연구·Google AI Studio 앱·기존 기록은 수정하지 않았습니다.
- 관측 필드는 종류/원본/단위/수신시각/기록시각/출처/지원/유효성/세션/nullable 미래 운전자 ID입니다. 수신 실패 기록에는 값과 수신 시각이 null입니다. 운전자 A/B 재설계·자동 이상 감지 엔진은 구현하지 않았습니다.

### 실제 검사 결과

- 최종 assembleDebug + assembleDebugAndroidTest: BUILD SUCCESSFUL, 45초, exit 0. Windows JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0, 기존 캐시 offline 사용. SDK 없이 동작하는 현재 기반 앱 빌드 성공입니다.
- Pleos Connect v2.1.0 emulator-5554에 앱·테스트 APK 교체 설치 성공. 실제 UI 실행.
- CARITDA_MVP_PASS checks=24: 기존 저장·인계·조치·재발·상담·입력 제한·화면 폭 검사 모두 재통과했습니다.
- CARITDA_OBSERVATION_PASS checks=5: 실패 상태에 가상값 없음, 별도 TEST_ONLY DB의 필드 저장/재연결 복원, 잘못된 수신값 거부, 실제 화면에서 SDK_UNAVAILABLE 진단 저장, Activity 재시작 후 복원 통과. 테스트 숫자 42%는 독립 DB 저장 검사이며 실제 센서/SDK 응답이 아닙니다.
- 초기 확장 계측 실행은 종료 응답 없이 대기하여 force-stop으로 중단했습니다(중단 결과 Process crashed). 기존 시나리오 끝에서 Activity 종료를 명시하도록 테스트를 수정하고 재빌드·재설치한 최종 실행은 24+5 모두 통과했습니다. 기존 데이터를 삭제하지 않았습니다.
- 최종 앱 APK SHA256: 29812d449d92fb827c81b3ba157c102b31880d141f15be510fca81eb30618b46. 앱/테스트 APK와 v02-build.txt, v02-instrumentation.txt, v02-sdk-download.txt는 로컬 build 아래에만 있습니다.
- 현재 AVD의 ai.pleos.playground.service.vehicle versionName 2.3.22와 공개 권한 CAR_ENERGY/CAR_DRIVE/CAR_DOORS의 prot=normal 정의를 dumpsys로 확인했습니다. 설치된 서비스 존재는 앱 연결/권한 허용/Capability 성공과 다릅니다.

### 실패 / 미구현

- 공식 Maven 두 버전 POM 요청 연결 실패. Gradle 2.3.1 실제 resolve도 Nexus/Google/Maven Central에서 Permission denied: getsockopt로 실패(exit 1). 초기 probe 경로와 PowerShell 인자 문제는 수정했으며 최종 실패 원인은 네트워크입니다. 제한을 우회하거나 같은 다운로드를 반복하지 않았습니다.
- 샘플 README는 2.3.2, 실제 Gradle 버전 파일은 2.3.1입니다. 시작 가이드와 Gradle 기준 2.3.1을 다운로드 후보로 택했으나 바이너리 확보·이미지 호환성 검증은 실패/보류입니다.
- SDK 설치·SDK 의존성 포함 Sync/컴파일·초기화·서비스 연결·Capability·실제 읽기 모두 미완료. SOC/주행거리/연료/도어 원본 수신 0개. 실제 센서값 저장·복원도 미수행입니다. SDK 없이 현재 화면은 미설치/미확인을 안내합니다.
- Lint는 기존 미캐시 의존성/네트워크 제한 상태로 이번에 재실행하지 않았습니다. Release/실차/실제 주행 전환 미검증. AI·Gleo·자동 감지·운전자 관리 개편·외부 공유는 미구현입니다.

### 다음 작업 / 게시

일반 Android Studio에서 v0.2 검증 문서의 임시 Vehicle 2.3.1 dependency를 추가하고 Sync하여 아티팩트 확보 여부를 먼저 확인해야 합니다. 현재 기본 빌드에는 미확보 SDK를 영구 추가하지 않았습니다. 다운로드만으로 현재 앱이 센서를 읽지는 않습니다. 확보 후 AAR 기준 typed 읽기 어댑터를 구현하고 Capability·권한·실수신·저장 순으로 검증하는 것이 다음 최우선 작업입니다.

GitHub 연결 도구를 통한 게시만 사용합니다. 작업 시작 main a07d3234cbf6d4f2e7c4e93553e391ff13c652e7. 게시 직전 최신 HEAD와 대상 SHA를 재확인하고 Aside의 다른 파일 변경은 최신 트리에 보존합니다. 게시 커밋은 최종 응답으로 보고하며 자기 SHA 기록을 위한 반복 커밋은 하지 않습니다. APK/DB/인증/디버그 키/캐시/init probe/로그는 게시하지 않습니다. 로컬 Git push를 수행하지 않았습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 16:14:00 +09:00 (Asia/Seoul).

## 최신 작업 — CARITDA v0.1 구현 및 Pleos 실제 실행

사용자의 신규 MVP 지시로 서비스 개발 보류를 해제했습니다. CARITDA는 상표 검증 전 가칭입니다. 폴더와 패키지를 유지하고 기존 NextDrive-Pleos에 실제 서비스 화면을 구현했습니다. 변경은 사용자 PC의 프로젝트 폴더에 직접 반영되었습니다. 기존 Google AI Studio 앱, Aside 연구 파일, 과거 개발 기록과 기존 앱 데이터를 삭제하거나 초기화하지 않았습니다.

### 완료한 기능

- 운전자 A/B와 선택 상태 저장, 미해결 문제 목록 및 해결 기록 표시.
- 증상 네 종류, 발생 상황, 발견 시각, 등록자, 추가 메모 수기 기록.
- SQLite 영구 저장 및 트랜잭션, 재실행 복원, 다음 운전자 확인과 중복 방지.
- 미해결/점검 예정/점검 완료/해결 확인, 점검·조치 메모와 변경 이력.
- 같은 카드의 재발 차수와 재확인, 과거 확인 이력 보존.
- 증상·시각·설명·확인자·조치·해결 상태를 포함하는 정비 상담 요약.
- 공개 AAOS CarUxRestrictions 기반 상세 입력 제한. 플랫폼 응답 미확인/연결 끊김/제한 시 입력을 차단합니다. 실제 차량 속도·기어를 추정하거나 Vehicle SDK 값을 꾸미지 않습니다.

### 생성·수정한 파일

- `AGENTS.md`: 신규 MVP 범위와 현재 표시명을 우선 지침으로 추가.
- `NextDrive-Pleos/app/build.gradle.kts`: 공개 android.car 컴파일 라이브러리와 프레임워크 계측 runner.
- `NextDrive-Pleos/app/src/main/AndroidManifest.xml`: 플랫폼 car 라이브러리 사용 선언. 차량 제어나 인터넷 권한을 추가하지 않음.
- `NextDrive-Pleos/app/src/main/res/values/strings.xml`: 표시명 CARITDA(가칭).
- `NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt`: 실제 서비스 UI.
- 같은 경로의 `model/IssueModels.kt`, `data/IssueRepository.kt`, `data/SqliteIssueRepository.kt`, `domain/CaritdaService.kt`, `domain/ConsultationSummary.kt`, `safety/CarSafetyGate.kt`: 모델·저장·업무 규칙·요약·입력 제한 신규 구현.
- `NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt`: 실제 DB/Activity/UI 계측 시나리오.
- `README.md`, `NextDrive-Pleos/README.md`: 현재 실행 상태와 안내. 프로젝트 README의 이전 환경 구축 기록은 보존.
- `docs/CARITDA_V01_MVP.md`: 사용자 실행 순서, 9단계 인수 시나리오, 실제 검증과 한계.
- `docs/CODEX_PROGRESS.md`: 이 기록. 이전 실패·사용자 확인 기록은 아래에 보존.

### 실제 테스트 결과

검증 환경: Windows, JBR 21.0.8, Gradle 8.11.1, AGP 8.10.1/Kotlin 2.2.21, Platform 34/Build-Tools 35.0.0, 기존 Pleos Connect v2.1.0/API 34 AVD emulator-5554.

1. 최종 소스로 `:app:assembleDebug :app:assembleDebugAndroidTest` BUILD SUCCESSFUL(56초). 기존 Gradle/의존성 캐시와 프로젝트 내부 작업 캐시를 사용한 offline 빌드입니다. 기존 디버그 인증서로 서명하여 앱 교체 설치 시 데이터를 보존했습니다. 키·캐시·로컬 설정은 공유하지 않습니다.
2. ADB `install -r`로 앱/테스트 APK 설치 성공, 실제 Activity 실행 성공. 실제 플랫폼 Car UX 서비스에서 입력 허용 응답 확인.
3. `CARITDA_MVP_PASS checks=24`: 요청한 9단계 전체를 실제 UI에서 수행. SQLite 재연결과 Activity 재시작 복원, A→B 확인, 중복 확인 방지, 점검 메모, 네 상태, 재발/재확인/기존 이력 보존, 잘못된 입력 거부, 제한 시 여섯 변경 동작 차단, 기존 기록 보존, 600dp 콘텐츠 폭 홈/입력 화면 검사 통과.
4. 최종 앱 프로세스 force-stop 후 실행하여 B와 기존 진동 기록 복원 확인. 실제 터치로 점검 예정 변경, 상담 요약에 진동 설명·B·점검 메모 표시 확인. 실제 요약 화면 스크린샷을 로컬 build/validation에 보관했습니다.
5. 앱 APK: `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk`. SHA-256 `9e0c7197f9d76d1b1abe665c2e9cd804537d20b28560a5514f7ea5a68705f043`.

### 실패·미검증·주의할 한계

- Lint 미완료: offline 캐시에 lint-gradle 31.10.1이 없고 일반 온라인 재시도는 Google Maven/Maven Central의 `Permission denied: getsockopt`로 실패했습니다. 네트워크 제한을 우회하지 않았습니다. 컴파일·APK·실제 계측 성공과 구분합니다.
- 초기 프로젝트 내부 빌드에서 캐시 경로·debug.keystore 잠금 파일·Kotlin daemon 쓰기 문제가 발생했습니다. 기존 읽기 캐시 경로를 바로잡고 프로젝트 내부 디버그 키 경로와 in-process 컴파일로 해결했습니다. 시스템 정책·전역 설정 변경이나 새 소프트웨어 설치는 하지 않았습니다.
- deprecated onBackPressed 컴파일 경고가 남아 있습니다. Release 서명/배포 빌드·모든 화면 크기·실제 주행 제한 전환·실차는 미검증입니다.
- Vehicle SDK 차량 데이터 수신, AI 진단/Gleo, 휴대폰 알림, 외부 정비 접수, 차량 제어는 미구현이며 완료로 표시하지 않습니다. 공개 AAOS 화면 제한 응답은 Pleos Vehicle SDK 센서 수신과 별개입니다.
- 검증용 수기 카드는 예시임을 메모에 표시하고 보존했습니다. 에뮬레이터 시간대 America/Los_Angeles 기준 시각을 화면에 사용하며 PC 시간대와 다릅니다.

### 게시 경로·후속 작업

GitHub 연결 도구를 통한 게시 대상은 위의 명시적 소스·문서 16개입니다. APK·DB·로그·스크린샷·개인 키·.tools·로컬 설정은 포함하지 않습니다. 작업 기준 main `ab7a05645fe44aa6e7d6d31471809a1a28b15ba1`과 대상 SHA를 대조하고, 게시 직전 다시 확인합니다. 같은 main을 부모로 한 커밋과 기대 HEAD 갱신을 사용하며 나머지 파일과 Aside SHA 보존을 검증합니다. 본 문서의 자기 커밋 ID를 넣기 위한 재커밋은 하지 않으며 최종 보고에 실제 게시 커밋을 제공합니다. 로컬 Git push/인증 작업은 수행하지 않았습니다.

다음: 사용자가 Android Studio에서 기존 app을 Run 하여 인수 시나리오를 재확인합니다. 네트워크가 허용된 일반 환경에서 Lint를 완료하고 주행 제한 전환을 별도 검증합니다. 다음 기능 후보는 사용자 확인을 거치는 상담 요약 파일 내보내기입니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 14:24:17 +09:00 (Asia/Seoul).

## 최신 검증 기록 — 사용자 환경에서 기본 Debug APK 실행 성공

### 완료 확인과 검증 주체

사용자가 일반 Windows Android Studio 및 ADB에서 아래 8개 항목을 직접 완료·확인했다고 전달했습니다. 이번 기록은 사용자 확인 결과이며 Codex가 재설치·재빌드·재실행한 결과로 표현하지 않습니다. 실제 실행 시각은 별도 미제공이며 위 시각은 문서 갱신 시각입니다.

1. Android SDK Platform 34 설치 완료.
2. Android SDK Build-Tools 35.0.0 설치 완료.
3. Android SDK Command-line Tools 및 Platform-Tools 설치 상태 확인 완료.
4. ADB로 Pleos Connect v2.1.0 에뮬레이터 연결 성공.
5. NextDrive-Pleos Gradle Sync 성공.
6. Gradle 8.11.1 및 기본 앱 관련 의존성 다운로드 성공.
7. Debug APK 빌드 성공.
8. Pleos Connect 에뮬레이터에 APK 설치 및 NextDrive 기본 테스트 화면 실행 성공.

**현재 확인된 상태:** 일반 사용자 환경에서 기본 앱 빌드·설치·실행 성공. **아직 미구현/미검증:** 공식 Vehicle SDK 실제 데이터 수신, 일반 앱의 차량 권한 허용, 항목별 Capability와 조회 응답. 이번 사용자 보고를 Release/실차/Lint/단위 테스트 성공으로 확대하지 않습니다.

### 수정 파일과 이번 작업의 검사 범위

- `PLATFORM_VALIDATION.md`: 사용자 확인 결과를 최신 검증 기록으로 추가하고 과거 Codex 실패 기록을 보존했습니다.
- `docs/CODEX_PROGRESS.md`: 사용자 확인 성공 8개와 검증 주체, 미구현 범위, 다음 작업을 기록했습니다.
- `docs/VEHICLE_SDK_READ_PLAN.md`: 다음 작업의 읽기 전용 차량 정보 1개 기술 검증 계획과 Android Studio 단계별 조작을 작성했습니다.
- 앱 소스·매니페스트·Gradle 설정은 읽기만 했고 변경하지 않았습니다. 현재 공식 Maven 설정은 있으나 Vehicle 의존성과 도어 권한은 없음을 확인했습니다. 서비스 기능 구현·SDK 설치·SDK 호출·실제 데이터 수신 시험은 이번 작업에서 수행하지 않았습니다.
- 선택한 Markdown 문서 3개의 용량·링크·바이너리·자격증명 패턴 검사와 기존 SDK/ADB 실패 기록 보존 확인을 통과했습니다. GitHub 연결 도구로 게시하고 원격 커밋 부모와 파일 해시/나머지 파일 보존을 확인합니다. 로컬 Git 연결·push·443 접근은 시도하지 않습니다.

### 과거 실패 기록과 다음 작업

아래의 SDK 다운로드·Gradle 준비·ADB 시작 실패 내역을 삭제하지 않았습니다. 당시 Codex 실행 환경의 관측 결과와 현재 사용자 환경의 성공을 각각 보존합니다. 해결된 사용자 환경을 다시 초기화하거나 SDK/AVD를 재설치하도록 요구하지 않습니다.

다음에는 공식 Vehicle SDK의 **앞 왼쪽 도어 위치**를 1차 후보로, 실제 AAR·함수별 권한 확인 → 읽기 Capability/영역 확인 → 유효한 콜백 값·시각 수신만 기술 검증할 계획입니다. 일반 앱 접근 가능성은 아직 확정하지 않습니다. 설치된 SDK의 실제 시그니처를 먼저 확인하며, 추측 API나 가상 값으로 성공을 만들지 않습니다. 사용자의 다음 조작은 필요한 의존성 변경이 준비된 후 Sync → External Libraries 확인 → 기존 Pleos AVD 선택 → Run → 테스트 화면/Logcat 확인 순서입니다. 자세한 내용은 `docs/VEHICLE_SDK_READ_PLAN.md`를 참조합니다.

현재 아이디어 재검토 상태를 유지하며 가족 인계·진단·정비사 연동 등 서비스 기능은 구현하지 않습니다. Aside 연구 파일과 기존 커밋도 보존합니다.

## 이전 작업 — Pleos 정식 빌드·Vehicle SDK 수신 준비 시도

### 성공한 작업

- 설치 상태와 공식 문서를 대조했습니다. 현재 Android Studio/JBR은 `C:\Program Files\Android\Android Studio` 아래에 있고 포함 Java 21.0.8 실행에 성공했습니다. 이전 Desktop1 경로가 없음을 확인해 활성 README의 경로를 바로잡았습니다.
- 기존 Pleos API 34 시스템 이미지/AVD(RAM 4 GB, Cold Boot) 설정을 확인했습니다. 사용자 세션의 기존 부팅 성공 사실은 유지하고 이번 Codex 세션의 앱 실행 성공과 구분했습니다.
- 시스템 SDK를 변경하지 않는 프로젝트 내부 `.tools/android-sdk` 설치 대상, Java/Android 사용자/임시 경로를 준비했습니다. 기존 수락 라이선스만 사용했고 새 라이선스를 자동 수락하지 않았습니다. 전역 환경 변수·PowerShell 정책·드라이버는 바꾸지 않았습니다.
- 공식 시작 가이드의 Vehicle 2.3.1 Maven 좌표, 도어 조회 권한, Capability 및 앞 왼쪽 도어 조회 흐름을 재확인했습니다. 확인한 문서와 실제 아티팩트/권한/수신을 구분했습니다.

### 실패한 작업과 미수행 단계

| 요청 단계 | 실제 결과 |
| --- | --- |
| SDK Platform 34 / Build Tools 35.0.0 | 첫 시도는 과거 JAVA_HOME 경로로 실패. 현재 JBR로 수정한 설치 요청도 원격 목록/manifest 다운로드 실패 후 종료 코드 1. 프로젝트 내부 SDK의 두 패키지 파일 없음 |
| Gradle 8.11.1 / 정식 Wrapper | 공식 CDN SHA-256 URL 연결 실패. 배포본/Wrapper 미확보. 전체 Gradle 빌드·Lint·Gradle 테스트 미수행 |
| 정식 APK | SDK/Wrapper 선행 조건이 없어 생성하지 못함. 기존 오프라인 리소스·Kotlin 검사 산출물을 APK라고 표현하지 않음 |
| Pleos 실제 앱 실행 | ADB 서버 시작 실패, 종료 코드 1. 로그는 `Cannot mkdir '\.android': Permission denied`. 프로젝트 내 Android 사용자 경로·호환 경로 지정 후에도 같은 결과. APK 설치·시작 미수행 |
| Vehicle SDK 실수신 | 공식 Nexus의 Vehicle 2.3.1 POM 연결 실패. AAR/함수별 권한/Capability/일반 앱 접근/값 수신 모두 미검증. 가상 값으로 대체하지 않음 |

이 세션에서 일반 앱이 접근 가능한 차량 정보 1개를 실제 수신했다는 결과는 없습니다. 문서상 최소 후보는 앞 왼쪽 도어 위치이며, 실제 SDK 확보 후 읽기 권한·영역·Capability·조회 응답을 차례로 검증해야 합니다. 상세 Reference 일부는 본문 시그니처가 노출되지 않아 추측으로 코드를 만들지 않았습니다.

### 변경 파일 및 검사 범위

- 수정: `NextDrive-Pleos/README.md`, `PLATFORM_VALIDATION.md`, `SDK_CAPABILITY_MATRIX.md`, `docs/CODEX_PROGRESS.md`.
- 생성: `docs/PLEOS_ENVIRONMENT_RECOVERY.md` — 일반 Windows 환경에서 SDK·정식 Wrapper·APK·ADB·최소 SDK 조회를 이어갈 절차. 실행하지 않은 명령은 안내로 표시했습니다.
- 이번 게시 대상은 문서 5개입니다. 앱 소스·매니페스트·Gradle 설정은 바꾸지 않았고 미검증 소스도 게시하지 않습니다. 문서 내용·명시적 경로·용량·링크·바이너리·민감정보 패턴 검사를 통과했습니다. 안내의 PowerShell 예제 2개는 구문 검사만 통과했으며 실행 성공으로 표현하지 않습니다.
- 도구용 파일·로그·라이선스는 Git 제외 경로 `.tools` 아래에만 남겼습니다. 기존 프로젝트·Google AI Studio 앱·Aside 연구 파일을 삭제하거나 초기화하지 않았습니다.

### 다음 작업 및 공유

현재 접근 제한으로 공통 환경 완성을 달성하지 못했습니다. 동일한 실패를 반복하거나 권한/네트워크 제한을 우회하지 않습니다. 일반 Windows 사용자 환경에서 문서의 SDK/Gradle/ADB 준비를 완료한 뒤 실제 기본 APK 실행, 공식 SDK 읽기 전용 어댑터 컴파일과 최소 1개 신호 수신을 검증해야 합니다. 가족 인계·진단·정비사 연동 등 서비스 기능은 계속 보류합니다.

원격 작업 기준 main은 `8081743d7322c251e2fdacaf7d17ffed43b1cbfb`입니다. 검증된 문서만 기존 Codex/Aside 이력을 잇는 커밋으로 **GitHub 연결 도구를 통해 게시**하고 파일 해시·나머지 파일 보존을 확인합니다. 커밋 ID는 최종 응답에 보고하며 로컬 Git 연결/443 접근을 다시 시도하지 않습니다.

## 이전 작업 — GitHub 연결 도구로 게시 경로 확정

### 완료한 작업 및 변경 파일

- 로컬 Git 연결 작업을 중단하고, 사용자 지시에 따라 `codex_apps.github`를 완료 후 자동 게시의 기본 경로로 확정했습니다. 이번 작업에서는 Git/GCM/clone/push·443 포트·루트 `.git` 접근을 재시도하지 않았습니다.
- `AGENTS.md`: 기존 로컬 Git 우선 규칙을 연결 도구 우선 규칙으로 교체했습니다. 변경 파일 검토·가능한 검사·보고서 갱신·검증된 파일 커밋·원격 확인·커밋 ID 보고를 상시 절차로 저장했습니다.
- `docs/GITHUB_SYNC.md`: 현재 활성 연결 도구 절차와 보류된 로컬 연결 안내를 구분했습니다.
- `docs/AUTO_PUBLISH_TEST.md`: `connector-completion-20261107` 테스트용 문서 변경을 추가했습니다.
- `docs/CODEX_PROGRESS.md`: 이번 작업과 검사 범위·오류·다음 작업을 기록했습니다. Android 소스/설정 및 Aside 연구 파일은 변경하지 않았습니다.

### 검사 결과 및 게시 검증 범위

- 중단 지점에서 재개하면서 현재 파일과 GitHub 기록을 다시 확인했습니다. 원격 main은 여전히 `bc5e60805decad1eb33c3c2a89534f54d8cb9b3e`이며, 이미 작성된 4개 문서의 게시 단계만 미완료였습니다. 문서 변경을 다시 만들거나 기존 커밋을 재업로드하지 않고 남은 검사·게시·원격 대조를 수행합니다.
- 연결 도구로 작업 시작 main `bc5e60805decad1eb33c3c2a89534f54d8cb9b3e`와 트리를 조회했습니다. 변경 대상 4개 문서의 원격 blob SHA와 로컬 기준 SHA가 모두 일치하여 시작 시 충돌이 없음을 확인했습니다.
- 이번 변경은 Markdown 문서 4개만 포함합니다. 내용·공유 안전 검사 후 기존 main 트리를 기반으로 해당 파일만 커밋합니다. 원격 main과 커밋 부모, 해당 4개 파일의 blob SHA 및 나머지 파일 보존 여부를 게시 후 확인합니다.
- 재개 후 문서 4개의 명시적 경로·파일 크기·링크 여부·바이너리/NUL·자격증명 패턴 검사를 통과했습니다. `AGENTS.md`의 기본 크기 제한도 확인했습니다. 앱/스크립트 실행 테스트를 수행한 것으로 표현하지 않습니다.
- Aside의 `docs/research/ASIDE_RESEARCH.md`, `docs/research/ASIDE_SYNC_TEST.md`는 기존 내용과 이력을 그대로 보존합니다. 기준 HEAD나 대상 파일 SHA가 바뀌거나 충돌하면 덮어쓰지 않고 중단합니다.
- 게시 경로는 **GitHub 연결 도구**입니다. 로컬 Git push 성공으로 표현하지 않습니다. 검증한 커밋 ID와 게시 후 대조 결과는 최종 응답에 제공합니다.

### 현재 오류·미구현·다음 작업

- 공통 환경을 읽기 전용으로 재확인했습니다. 설치된 컴파일 플랫폼은 API 36, Build Tools는 36.1.0이며 API 34 컴파일 플랫폼/Build Tools 35.0.0과 정식 `gradlew.bat`/`gradle-wrapper.jar`는 아직 없습니다. Pleos API 34 시스템 이미지와 컴파일용 SDK를 구분합니다. 이미 사용자 확인된 에뮬레이터 부팅과 기존 오프라인 소스 검사는 반복하지 않았습니다.
- 로컬 Git 연결은 실패 재시도 대상이 아니라 현재 보류된 작업입니다. 사용자가 일반 Windows PowerShell에서 인증·원격 접근을 확인했다고 전달한 사실과 Codex 세션의 제한을 구분합니다. 로그인 승인을 다시 요청하지 않습니다.
- 전체 Gradle 빌드·APK 실행·Vehicle SDK 실제 수신은 미검증 상태이며 이번 문서 게시 테스트에 포함하지 않았습니다. 기존 서비스 기능은 계속 개발 보류입니다.
- 다음 개발 작업부터 동일한 연결 도구 게시 절차를 적용합니다. 앱 개발을 재개할 경우 해당 변경에 맞는 검증을 수행한 후 검증된 파일만 게시합니다. 스케줄러나 로컬 Git 환경 변경은 진행하지 않습니다.

## 이전 작업 — 개발 완료 직후 자동 게시 규칙

### 완료한 작업

- Git for Windows 2.56.0.windows.4를 `C:\Program Files\Git\cmd\git.exe`에서 확인. 재설치하지 않음. GCM 2.9.1과 기본 credential helper `manager` 확인.
- 별도 Git 제외 폴더 `.sync/git-smoke-20261107-001502`에서 init/add/commit/status/log 실행 성공. 테스트 커밋 `fb82ef98b560f4f023436dac20297a7314356362`, 작업 트리 변경 없음 확인. 로컬 전용 가상 테스트 작성자이며 GitHub에 올리지 않음.
- `AGENTS.md`의 무조건적인 자동 커밋 금지를 '작업 완료·검증 성공 후 이번 작업의 명시적인 변경 파일만 자동 커밋·게시'로 교체. 이후 정상 게시에는 반복 확인을 요청하지 않는 상시 승인을 기록.
- 문서와 앱 변경의 검증 게이트, 기존 사용자 변경/Aside 연구 파일 보호, 원격 충돌 중단, API 게시와 로컬 push의 구분, 직접 Git 명령을 사용하는 정책 미변경 경로를 문서화.
- 최초 로컬 연결 절차와 테스트 문서를 준비. 이번 작업은 서비스 기능이나 앱 소스/설정을 바꾸지 않음.

### 수정 파일

- 수정: `AGENTS.md`, `docs/GITHUB_SYNC.md`, `docs/CODEX_PROGRESS.md`.
- 생성: `docs/LOCAL_GIT_SETUP.md`, `docs/AUTO_PUBLISH_TEST.md`.
- `docs/research/**`와 기존 앱 파일은 수정/삭제/스테이징하지 않음. 원격에만 있는 Aside 파일도 보존 대상으로 확인.

### 실제 검사 결과와 오류

- Git 버전 및 격리된 로컬 커밋 검사 성공. 실제 프로젝트 루트의 `.git`은 아직 없음.
- GCM `github list`: `Unable to persist credentials with the 'wincredman' credential store` 오류. 이 세션에서 저장소 접근이 안 되어 로컬 인증 유무를 확정하지 못함.
- 비대화형 Git `ls-remote`: github.com:443 연결 실패, 종료 코드 128. 로컬 인증·clone·push 미검증.
- PowerShell 유효 정책은 `Restricted`, 각 scope는 Undefined. 시스템 정책 변경, Bypass 옵션, 보안 해제, 자격증명 추출은 하지 않음. 직접 Git 실행과 문서 작업은 가능함을 확인.
- 프로젝트 루트 `.git` 경로가 현재 Codex 파일 권한에서 읽기로 제한됨. 이 제한을 우회하여 초기화하거나 외부 프로세스로 대신 실행하지 않음.
- 원격 main의 작업 시작 기준은 `6f1467e1828cec601dbd6c6a5abf92ff6f5263a5`. Codex/Aside 파일 목록을 읽기 전용으로 확인. 새 문서만 이 원격 이력을 잇는 커밋으로 게시하고 해시를 대조하는 범위임.
- `AGENTS.md`의 현재 내용은 직접 읽고 이번 작업에 적용. 새 세션을 실행하는 재시작 검증은 `codex` 재실행 금지 조건 때문에 하지 않음.
- 공유할 Markdown 파일 5개의 독립 경로/자격증명 패턴 검사 통과. `AGENTS.md`는 11,164 bytes로 기본 32 KiB 제한 이내이며 무조건적인 자동 커밋 금지와의 충돌을 수정했음을 확인.
- 전체 Gradle 빌드·APK 실행·SDK 수신은 이번 문서 변경의 테스트 대상이 아니며 여전히 미검증. 기존 앱이 검증 완료됐다고 주장하지 않음.

### 게시 경로 및 남은 작업

이번 문서의 게시 경로는 이미 인증된 GitHub 연결 도구입니다. 로컬 Git commit/push 성공으로 기록하지 않습니다. 보고서는 검증 문서·지침과 같은 커밋에 포함하고 게시 후 원격 내용을 확인하며, 자기 자신의 SHA를 넣기 위한 반복 커밋 대신 최종 작업 응답에 검증 SHA를 제공합니다.

최초 GCM 로그인 승인은 사용자에게 별도로 요청한 상태입니다. 승인 전 브라우저 로그인/인증 명령을 시작하지 않았습니다. 이 승인은 현재 세션의 네트워크·자격 증명·루트 `.git` 권한 제한을 자동 해제하지 않습니다. 필요 시 일반 사용자 PowerShell에서 승인된 최초 연결을 수행하고 정상 CLI push를 별도 검증해야 합니다.

**완료:** 영구 지침·작업 절차·로컬 Git 실행 검사·문서 게시 테스트 준비. **미완료:** 프로젝트 루트 Git 연결, GCM 실제 인증, 원격 clone/비교 및 CLI push 검증. 스케줄러는 등록하지 않았습니다.

## 이전 초기 공유 작업 기록 (아래는 2026-11-06 기준)

## 현재 범위

공모전 아이디어는 재검토 중입니다. 기존 NextDrive/카잇다 서비스 기능은 보류하며 이번 작업은 GitHub 공유·개발 기록·환경 확인으로 제한합니다. 기존 Google AI Studio 앱은 수정하지 않았고 현재 프로젝트 파일을 삭제하거나 초기화하지 않았습니다.

## 완료한 개발 작업

- GitHub 저장소 `kyc66533-art/vehicle-app-workspace`의 존재·비공개 상태와 연결 계정의 쓰기 권한 확인. 최초 조회에서 브랜치 없음/빈 저장소 확인.
- 로컬 루트·상위 경로·하위 앱 폴더에 Git 초기화 흔적 없음 확인. Git/gh는 PATH·일반 설치 경로·확인한 Codex 배포 폴더에서 미발견. 설치나 로컬 인증은 임의로 수행하지 않음.
- 사용자 확인 환경을 반영: Android Studio 정상 실행, Pleos Connect v2.1.0/API 34 이미지 설치·AVD 부팅. AVD 설정 파일에서 RAM 4096 MB / Cold Boot / Automotive 태그 재확인.
- 루트 `.gitignore`, 공유 파일 검사 스크립트, 검증된 기존 커밋만 게시하는 스크립트와 운영 절차 작성.
- 작업 완료마다 이 보고서를 갱신하도록 `AGENTS.md`에 지침 추가. 30분 스케줄러는 설계만 하고 등록하지 않음.

## 수정하거나 생성한 파일

- 생성: `.gitignore`, `README.md`, `docs/CODEX_PROGRESS.md`, `docs/GITHUB_SYNC.md`, `docs/UPLOADED_FILES.md`, `scripts/Test-ShareSafety.ps1`, `scripts/Publish-ValidatedCommit.ps1`.
- 수정: `AGENTS.md`, `PLATFORM_VALIDATION.md`.
- 기존 Android 소스·리소스·Gradle 설정 및 다른 명세서는 이번 작업에서 변경하지 않음. 공유 대상에는 기존 소스가 포함됨.

## 테스트 결과

- GitHub 인증·저장소 조회 성공. 연결 계정의 push 권한 확인. 로컬 Git 인증 성공으로 해석하지 않음.
- 초기 소스 커밋 `9d01e7739d5f1faceaaeab43c826747783706bbf`의 `main` 반영 확인. 재귀 트리 조회 결과 파일 26개, 추가 파일 0개이며 모든 Git blob SHA가 로컬 공유본과 일치함. 이 검증 후 현재 보고서에 결과를 반영하는 별도 문서 커밋을 추가함.
- Pleos AVD 설정 파일과 설치 이미지의 API 34/Automotive 태그 확인. 부팅 성공은 사용자 직접 확인 결과이며 이번 Codex 세션에서 다시 실행하지 않음.
- 새 PowerShell 스크립트 2개의 Parser 구문 검사 통과.
- 명시적으로 선정한 텍스트 파일 26개의 독립 인라인 경로·크기·바이너리·자격증명 패턴 검사 통과. 실제 키/토큰은 발견되지 않았으며 패턴 검사의 한계가 있어 파일도 검토함.
- `.ps1` 안전 검사 스크립트 실행 시 PowerShell 정책에 의해 차단됨. 정책을 변경/우회하지 않았으며 게시 스크립트의 실제 Git 동작은 테스트하지 못함. 위 인라인 파일 검사를 스크립트 런타임 검증과 구분함.
- 이전 작업: 설치된 AAPT2/SDK 36·Kotlin 2.1.21을 사용한 리소스 및 Kotlin 소스 오프라인 검사 통과. 정식 Gradle 툴체인 빌드가 아님.

## 현재 오류 및 제약

- 로컬 Git/gh 실행 파일 미발견, 로컬 `.git` 미생성. 따라서 로컬 origin 설정·Git 자격증명·CLI push는 미완료.
- 현재 세션의 PowerShell 스크립트 실행 정책으로 `.ps1` 직접 실행이 차단됨. 자동 게시 운영 전 사용자 환경에서 실행 정책과 조직 정책을 검토해야 함. 설정을 임의 변경하지 않음.
- 이전 Codex 프로세스의 IDE 시작/AEHD 접근 거부·Gradle/Pleos Maven 연결 실패는 과거 환경의 관측값. 사용자 세션에서 현재 IDE·Pleos 부팅은 정상 확인됨.
- 컴파일용 Platform 34와 Build Tools 35.0.0, 정식 Wrapper/Gradle, Vehicle SDK 설치·해석은 아직 미확인/미완료. 시스템 이미지 설치와 구분.

## 아직 구현·검증하지 않은 항목

- 전체 Gradle 빌드, Lint, Gradle 단위 테스트, APK 설치·실행, Vehicle SDK 실제 데이터·권한·Capability 수신.
- 로컬 Git 연결/인증, 게시 스크립트의 실제 Git 통합 실행, Windows 예약 작업 등록 및 실행.
- 기존 서비스 기능 전체는 개발 보류 상태이며 이번 공유를 통해 추가 구현하지 않음.

## 다음 개발 작업

1. Git 설치 위치 확인 또는 사용자 승인 후 설치, 사용자 승인에 따른 로컬 로그인과 원격 이력을 보존하는 로컬 연결.
2. 컴파일 SDK/빌드 도구·Wrapper·Gradle 준비 후 정식 빌드·테스트와 Pleos APK 실행 확인.
3. 공식 Vehicle SDK 확보 후 최소 읽기 전용 신호의 권한·Capability·수신을 한 화면에서 검증.
4. 실제 검증을 통과한 커밋/보고서를 공유. 게시 스크립트의 정상·dirty tree·인증 실패·원격 선행·분기 이력 사례를 테스트.
5. 위 조건 충족 후 필요하면 30분 예약 동기화를 사용자 별도 승인으로 등록.

## GitHub 업로드 상태

- **GitHub 연결 도구를 통한 초기 업로드 및 원격 검증 성공.** 저장소: [vehicle-app-workspace](https://github.com/kyc66533-art/vehicle-app-workspace), 브랜치 `main`, 비공개 유지.
- 첫 커밋: `9b381f49b3e1b6a1393c8b817e5eaf9fd342578b` — 저장소 README 초기화.
- 검증한 소스·문서 커밋: [9d01e773](https://github.com/kyc66533-art/vehicle-app-workspace/commit/9d01e7739d5f1faceaaeab43c826747783706bbf). 첫 커밋을 부모로 보존하는 일반 이력으로 반영했으며 강제 갱신하지 않음.
- 업로드 파일 26개는 `docs/UPLOADED_FILES.md`에 열거됨. 현재 보고서 갱신 커밋은 위 검증 결과를 추가하는 문서 변경이며 소스 변경이 아님.
- 최종 점검 중 다른 작업의 커밋 `2280ea3c6f764b7851b7d32852c77e541cc901aa`(`docs: add Aside GitHub connection test`)이 보고서 업로드 이후 추가된 것을 확인함. 추가 파일 `docs/research/ASIDE_SYNC_TEST.md`를 보존했으며 강제로 덮어쓰거나 로컬 파일로 가장하지 않음. 초기 공유 목록 26개와 별도로 원격에 추가된 문서임. 로컬 Git 연결 시 이 원격 이력도 반드시 보존해야 함.
- 로컬 Git 초기화·origin 연결·로컬 인증은 **미완료**. 연결된 GitHub 계정 인증만 사용했으며 인증 파일/토큰은 업로드하지 않음.
- **자동 동기화는 비활성/미등록.** 완료된 기존 커밋만 전송하는 스크립트와 절차만 준비했으며 구문 검사 외 실제 Git 통합 실행은 미검증. `.sync/validated-commit.json`도 생성하지 않음.
- 초기 공유는 사용자가 명시적으로 요청한 기존 소스 보존용이며 정식 빌드 미검증 상태를 포함함. 향후 자동 게시에는 동일 예외를 적용하지 않음.
