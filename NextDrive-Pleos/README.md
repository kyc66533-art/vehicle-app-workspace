# CARITDA(가칭) — 실제 SOC 자동 안내 PoC

2026-11-08: Pleos Virtual Console80→18→45%를 실제 SDK GET/변화 callback으로 수신해 원본·사용자 낮은 충전량 사건·중복 방지·조건 해제·재실행 복원을 검증했습니다. 가시/입력 허용 상태에서 명시적으로 켠 경우만 구독하며 종료 중 상시 감시·실차 고장/수리 판정은 아닙니다.

**부분 성공:** 실제 인증된 AI 모델은 아직 호출하지 않았습니다. HTTPS 연결과 validation JSON Mock만 검사했습니다. [실험·320개 검사·데이터 보존·AI 승인/설정·시연 순서](../docs/CARITDA_VIRTUAL_DRIVING_AI_POC.md)를 확인하세요. 일반 앱은 데이터 유지 교체 설치됐고 자동 안내 기본 꺼짐/AI미연결입니다. 현재 사용자23건과 원본을 보존했으며 시험 사건은 validation에만 있습니다.

앱의 차량 정보→충전량 자동 안내·설정과 기록에서 사용자 기준을 설정합니다. 시험은 [TEST] 앱과 기존 AVD/공식 Console을 사용하세요. 실제 모델 시연에는 별도 승인된 HTTPS 중계·인증·비용 설정이 필요합니다. 로컬 Debug APK는 app/build/outputs/apk/debug/app-debug.apk이며 Release가 아닙니다. 아래 이전 기록을 보존합니다.

---

# CARITDA(가칭) — 공통 메뉴·차종별 정보 표시 안정화

2026-11-08: 만족한 홈 구성을 유지하면서 독립된 동일5항목 메뉴, 명시적 수동 동력 방식 배치, 기본 KST/선택 기기 시간과 읽기 쉬운 조회 시각을 적용했습니다. 내부 내용 버전만 숨겼고 실제 원문·수정·점검·인계/재발·SDK 단발 조회는 유지합니다. SDK가 차종을 자동 판별하거나 고장을 진단하는 기능이 아닙니다.

최종 Pleos v2.1.0에서 기존246+신규51=297 assertion 통과, 일반 APK 데이터 유지 교체 설치/화면 확인, 이번 현재 사용자 기록23건과3DB 원본 보존을 확인했습니다. [API·권한 근거, 검사 범위와 직접 확인 순서](../docs/VEHICLE_DISPLAY_COMPATIBILITY.md)를 확인하세요. 실제 ICE/HEV/PHEV 신호·CarInfo 수신·OS 분할/IME/물리 접근성·실차·Release는 미검증/미완료입니다.

일반 차량 아이콘→메뉴→차량 동력 방식·시각 설정에서 배치를 고릅니다. 설정은 사용자 선택이며 미지원 연료가0%로 바뀌지 않습니다. 사용자 PC의 소스와 일반 앱에 이미 반영됐으므로 별도 파일 수정은 필요하지 않습니다. Android Studio에서는 이 프로젝트/기존 AVD를 사용하세요. 로컬 Debug APK는 app/build/outputs/apk/debug/app-debug.apk입니다. 아래 이전 기록을 보존합니다.

---

# CARITDA(가칭) — Premium Automotive UI

2026-11-08: 실제 차량 정보 두 카드·확인할 기록·기록 확인/증상 남기기 중심 홈, 고정 적응형 탐색, 간편 작성과 선택 정보, 짧은 조회 이력/기술 상세를 구현했습니다. Kotlin Android View와 SDK2.3.2·기존 데이터/권한/단발 자동 조회를 유지합니다. 현대차 공식 자산을 복제하지 않은 CARITDA 자체 디자인입니다.

Pleos v2.1.0에서 기존215+신규31=246 assertion 통과, 일반 APK 데이터 유지 교체 설치/화면 확인, 기존 문제22건과3DB 원본 보존을 확인했습니다. 실제 고령 사용자 시험·OS 분할/IME·TalkBack/rotary·실차·Release는 미검증/미완료입니다. [디자인 시스템](../docs/CARITDA_DESIGN_SYSTEM.md)과 [검사 범위·전후 비교·직접 확인 순서](../docs/DESIGN_ACCEPTANCE.md)를 확인하세요.

이 프로젝트를 Android Studio로 열어 기존 AVD에서 app Run을 사용합니다. 일반 앱은 차량 아이콘, 검증 앱은 [TEST] 플라스크입니다. Debug APK는 로컬 app/build/outputs/apk/debug/app-debug.apk이며 앱 데이터/키/백업/로그/개인 캡처/APK는 GitHub 게시 대상이 아닙니다. 아래 이전 기록을 보존합니다.

---

# CARITDA(가칭) — 실제 SDK 자동 스냅샷 P0

2026-11-08 검증: 앱 가시·입력 허용 진입당 한 번 실제 SOC/누적거리 조회 → 원본 저장 → 차량 상태 카드 한 개 갱신 → 사용자가 선택한 증상에 불변 스냅샷 연결. 고장 진단/자동 상태 변경/상시 감시가 아닙니다. 일반 차량 아이콘과 [TEST] 플라스크 검증 앱을 구분합니다.

기존174 + 신규 자동 기록41 = 215 assertion 통과. 일반 문제22건/원문·이력·초안 보존, 관측 원본 전체 보존/추가 메타데이터 이전을 확인했습니다. [구현·AM36 판정·실제 값/제약·실행 순서](../docs/AUTOMATIC_RECORDING_ACCEPTANCE.md)를 확인하세요. 전체 AM36/사용성/실차가 통과했다는 뜻은 아닙니다.

기존 Android Studio에서 NextDrive-Pleos를 열고 기존 Pleos v2.1.0 AVD를 선택해 app Run을 사용합니다. 일반 APK는 현재 PC에 데이터 보존 방식으로 이미 설치됐습니다. 로컬 Debug APK는 app/build/outputs/apk/debug/app-debug.apk(프로젝트 내부 경로)이며 저장소에 APK를 게시하지 않았습니다. Release 서명/실행 검증·제출 APK 위치와 Private 저장소 심사 계정 접근은 아직 준비해야 합니다. 키/개인 DB/백업/로그는 공유하지 않습니다.

아래는 이전 버전의 개발 기록과 실행 안내입니다. 현재 기능/검증 상태는 위 링크가 우선합니다.

---

# CARITDA — 고정 홈·기록 검색 실행 검증 완료

2026-11-08: 스크롤과 분리한 앱 내부 홈, 전체/내 작성 바로가기, 네 필터와 카테고리·전체 본문·메모 검색/조건 초기화를 구현했습니다. 상세 이전의 검색·필터·목록 위치, 수정 취소/변경 없음/버전 충돌·초안 보존 실패 시 이동 차단을 확인했습니다. 네 수동 상태 변경과 기존 수정·논리 삭제는 유지합니다.

Pleos Connect v2.1.0 validation 앱에서 신규NAV29개+기존145개=174개 자동 검사를 통과했습니다. 일반 APK도 install-r로 교체하고 홈·스크롤·차량 정보·사용법을 확인했으며 운영 문제/관측 DB는 백업과 바이트 단위로 같습니다. 실제 SDK100%/0km 수신은 validation의 별도 관측이며 일반 앱의 과거 값을 회복 근거로 합치지 않습니다.

[검증 결과·NAV 기준별 부분/미검증과 사용 순서](../docs/NAV_ACCEPTANCE.md), [자동화 후속 설계](../docs/AUTOMATION_FOUNDATION.md), [진행 기록](../docs/CODEX_PROGRESS.md)을 참고하세요. 자동 회복·Gleo·휴지통 복원은 미구현입니다. 실차/실제 주행 전환/종합 접근성·일반 사용자 시험·Lint는 남아 있습니다. 아래는 과거 기록입니다.

# CARITDA — 기록 저장·수정·삭제 신뢰성 개선

2026-11-08: 전체/관리 중/완료/내 작성 필터, 지속 저장 성공·실패 안내, 저장 후 상세 이동과 뒤로 가기 복구, 작성자 ID 기반 수정·논리 삭제를 구현했습니다. 발견 시각과 수정 전후 이력은 보존합니다. 기존 운전자·문제22건·이력·초안은 v3 이전 후에도 유지됐고 관측 DB 해시도 같습니다.

자동화는 별도 CARITDA 검증 전용 앱에서만 실행합니다. 기존 테스트를 포함한145개 검사와 운영 DB 보존·잘못된 운영 대상 테스트 거부를 확인했습니다. 과거 테스트 입증5건과 출처 미확정17건은 삭제하지 않았습니다. Virtual Console은 현재HTTP200입니다. 사람/실차/종합 접근성·Lint는 남아 있습니다.

[사용 순서·원인·새 인수기준·삭제 보존 정책](../docs/RECORD_RELIABILITY.md), [진행 기록](../docs/CODEX_PROGRESS.md)을 참고하세요. 이미 일반 앱을 데이터 보존 방식으로 교체 설치했습니다. 아래는 과거 기록이며 최신 결과를 우선합니다.

# CARITDA — Simple UI·첫 안내·사용법

2026-11-08: 홈의 다음 행동을 단순화하고 차량 정보의 충전량·주행거리·실제 수신 시각을 크게 표시했습니다. SDK/Capability/오류·세션은 불러오기 상세에 유지합니다. 신규 한 화면 안내와 여섯 항목 사용법/재보기, 초안 복귀를 구현했습니다. 기존 DB·운전자·수동 기록·SDK2.3.2·안전 제한은 보존했습니다.

앱/테스트 APK 빌드와 기존57개+신규32개 검사, 다섯 주요 화면200개 조합을 Pleos 에뮬레이터에서 확인했습니다. 현재 수신은 SOC15%/주행거리 원본1000000.0km, 연료 미지원이며 실차값/자동 진단이 아닙니다. ON기준은 통과13/부분8/미검증3이며 실제 사용자 이해도·실차·종합 접근성·Lint는 남아 있습니다.

[ON별 결과와 사용 순서](../docs/ONBOARDING_ACCEPTANCE.md), [진행 기록](../docs/CODEX_PROGRESS.md)을 참고하세요. 아래는 과거 버전의 기록이며 현재 상태는 이 절과 최신 진행 기록이 우선합니다.

# CARITDA — 운전자·UI M0 업데이트

2026-11-07: 이름 등록·카드 선택·운전 시작·이름 수정/보관, 홈/문제/차량 분리와 초안 복원을 구현했습니다. 기존 A/B DB는 영구 ID와 당시 이름을 추가하는 비파괴 v2로 이전했고 원래 문제 5건·이벤트 16건과 관측을 보존했습니다. 실제 Pleos 에뮬레이터에서 이전 18개 + 기존 24개 + 관측 6개 + 새 UI 27개 검사를 통과했습니다. SOC 100%/주행거리 0.0km 수신과 연료 미지원 처리를 유지합니다.

[사용 방법과 AC별 검증 범위](../docs/UI_ACCEPTANCE.md), [이전·복구 계획](../docs/UI_MIGRATION_PLAN.md), [최신 진행 기록](../docs/CODEX_PROGRESS.md)을 참고하세요. Lint/실차/종합 접근성 검증은 미완료입니다. 아래는 이전 버전의 기록이며 현재 상태는 이 절과 최신 기록이 우선합니다.

## 최신 상태 — Pleos Vehicle SDK 실제 수신 확인

2026-11-07: 기존 v0.1을 보존한 채 SDK 2.3.2로 Pleos 에뮬레이터의 SOC 100%, 누적 주행거리 0.0km를 실제 수신·SQLite 저장했습니다. 연료 잔량은 미지원 응답으로 기록합니다. 실차값과 구분하며 앱 프로세스 재시작 후 복원, 기존 24개 회귀와 관측 6개 검사를 통과했습니다. [상세 결과](../docs/VEHICLE_SDK_232_RUNTIME.md).

# CARITDA v0.1 — 현재 실행 가능한 MVP

2026-11-07 사용자의 신규 MVP 명세가 아래의 서비스 보류 기록보다 우선합니다. 앱 표시명은 CARITDA(가칭), 프로젝트 폴더와 패키지는 기존 NextDrive-Pleos / com.nextdrive.pleos를 유지합니다.

운전자 A/B 선택 → 수기 증상 저장 → 앱 재시작 후 복원 → 다음 운전자 확인 → 점검 메모·상태 변경 → 정비 상담용 요약을 구현했습니다. SQLite에 로컬로 보관하며 재발 시 기존 카드에 이력을 추가합니다. 실제 차량 센서값이나 외부 전송을 구현한 것으로 표시하지 않습니다.

기존 Android Studio에서 이 폴더를 열고, 기존 JDK/SDK/Gradle 설정을 유지한 채 Pleos Connect v2.1.0 AVD를 선택하고 app을 Run 하세요. 정차 중 플랫폼 화면 제한이 해제된 경우에만 입력할 수 있습니다. 앱 삭제·데이터 초기화는 하지 마세요.

상세 사용 순서·시험 결과·파일 구조는 [MVP 검증 안내](../docs/CARITDA_V01_MVP.md)를 참고하세요. Debug 앱과 계측 APK 빌드 및 에뮬레이터 24개 검사는 통과했습니다. Lint 의존성 다운로드는 제한으로 실패했습니다. 실차와 Release 빌드는 미검증입니다.

## 아래 내용은 기본 환경 구축 당시의 과거 기록입니다

다음의 미설치·미빌드·서비스 보류 설명은 당시 상태이며 현재 상태는 위 기록과 최신 진행 보고서를 따릅니다. 과거 기록과 기존 차량 데이터 모델·레이아웃은 보존합니다.

# NextDrive-Pleos

## 현재 범위

아이디어 재검토 중이므로 기존 서비스 기능은 개발 보류입니다. 이 프로젝트는 Pleos/AAOS 공통 실행 환경과 차량 상태 한 화면 테스트만 준비합니다. 실행 검증 현황은 상위 `PLATFORM_VALIDATION.md`를 참조하세요.

테스트 화면은 실제 기기의 Automotive 특성, 차량 데이터 연결 상태, 앞 왼쪽 도어·연료·주행거리의 지원 확인 상태/값/최근 수신 시각을 표시합니다. 현재 `UnavailableVehicleDataSource`만 연결되어 있어 차량 수치는 표시하지 않습니다. Vehicle SDK 미설치 상태이며 실제 데이터 수신·Capability 확인은 아직 이루어지지 않았습니다. 모의 값이나 서비스 기능은 넣지 않았습니다.

폴더·패키지·앱 표시명은 기존 식별자를 유지합니다. 아래는 초기 골격 준비 기록이며 새로운 서비스 기능 구현 지시가 아닙니다.

Pleos Connect 차량 내부 AAOS용 신규 Kotlin 프로젝트입니다. 상위 `AGENTS.md`, `NEXTDRIVE_SPEC.md`, `DEVELOPMENT_PLAN.md`를 개발 기준으로 사용합니다. 기존 Google AI Studio 프로토타입의 코드·리소스는 사용하지 않았습니다.

## 현재 상태

- AAOS 필수 하드웨어 선언, 크기 변경 가능한 Activity, 한국어 시작 화면, Kotlin DSL 빌드 설정을 작성했습니다.
- 시작 화면은 데이터 미연결 상태를 안내합니다. 차량 수치, 운행 관리, 모의 데이터, 실제 SDK 연결은 아직 구현하지 않았습니다.
- 운전 중 사용을 허용하는 `distractionOptimized` 선언은 없습니다. 주행 규제 연동·카테고리 검증 전에는 정차 상태에서만 검증합니다.
- API 34와 Build Tools 35.0.0, Gradle 8.11.1이 준비되지 않아 **정식 빌드·APK 생성·앱 실행은 미검증**입니다. Pleos AVD 자체의 부팅은 사용자 세션에서 이미 확인했습니다.
- `gradle-wrapper.properties`는 사용할 버전 설정만 담고 있습니다. **`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`는 아직 없습니다.** 공통 환경 준비 요청에 따라 프로젝트 내부 SDK 설치를 시도했으나 패키지 목록 다운로드 실패로 종료됐습니다. Gradle/Pleos Maven 접근도 실패했습니다. 자세한 결과는 상위 `PLATFORM_VALIDATION.md`와 `docs/PLEOS_ENVIRONMENT_RECOVERY.md`를 확인하세요.

## 일반 Windows 환경에서 이어갈 실행 준비 순서

아래는 이후 수행할 절차이며 이번 작업에서 실행하지 않았습니다.

1. 현재 Android Studio 실행 파일: `C:\Program Files\Android\Android Studio\bin\studio64.exe`. 이전 `Desktop1` 경로는 현재 존재하지 않습니다.
2. SDK Manager에서 Android SDK Platform 34와 Build Tools 35.0.0을 설치합니다. 기존 API 36 SDK/휴대전화 AVD는 보존합니다.
3. 승인된 프로젝트 전용 Gradle 8.11.1 배포본을 공식 배포처에서 받고 SHA-256을 확인한 뒤, 이 프로젝트에서 `gradle wrapper --gradle-version 8.11.1 --distribution-type bin`으로 정식 Wrapper를 생성합니다. 생성된 Wrapper와 배포본의 체크섬도 검증·기록합니다.
4. Android Studio에서 이 폴더를 엽니다. SDK 위치를 `C:\Users\kyc66\AppData\Local\Android\Sdk`, Gradle JDK를 현재 포함 JBR(`C:\Program Files\Android\Android Studio\jbr`)로 확인합니다. 이번 점검에서 Java 21.0.8 실행을 확인했습니다. 시스템 전역 설정은 임의로 바꾸지 않습니다.
5. 이미 설치·부팅 확인된 Pleos Connect v2.1.0/API 34 AVD(RAM 4 GB, Cold boot)를 사용합니다. 재설치하거나 기존 AVD를 삭제하지 않습니다.
6. Gradle Sync 후 `gradlew.bat :app:assembleDebug :app:lintDebug`를 실행합니다. APK 예상 경로는 `app/build/outputs/apk/debug/app-debug.apk`이며 아직 파일은 없습니다.
7. Pleos AVD를 선택해 Run으로 실행합니다. 런처 표시, 시작 화면, 크기 변경, 백그라운드 복귀, 재실행, 플랫폼의 주행 제한을 확인합니다.

설치·환경 변경 승인 항목과 공식 링크는 상위 `DEVELOPMENT_PLAN.md`를 참조하세요. 패키지 ID `com.nextdrive.pleos`는 개발용 제안이며, 공식 프로젝트 등록·권한 신청 전 확정해야 합니다.
