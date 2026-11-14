# CARITDA(가칭) — 차량 시계열·자동 인사이트

## 공모전 제출 후보 — 2026-11-14

**한 줄 소개:** 차가 알려 준 정보와 운전자가 남긴 문제를 함께 확인하고, 점검 참고 설명과 다음 운전자 인계로 이어 주는 Pleos Connect 차량용 앱입니다. 고장이나 수리 완료를 자동 판정하지 않습니다.

- 기준: [PR #7](https://github.com/kyc66533-art/vehicle-app-workspace/pull/7), `7f147d9032bb00ffdc76e2e7fc9b6ce752e1c7a1`. 제출 준비 후속 브랜치: `release/submission-readiness-20261114`. main과 기존 PR은 자동 병합하지 않습니다. 제출 시 후속 PR의 최종 HEAD SHA를 명시합니다.
- 저장소는 이번 작업 시작 전 API 응답부터 Public이었습니다. 공개 범위는 변경하지 않았습니다. 이전 개발 기록의 Private 표기는 당시의 안내이며 현재 상태로 사용하지 마세요.
- 팀명 CARITDA의 제출 APK 경로는 `apk/CARITDA_CARITDA_release.apk`입니다. 현재 서명·실행 검증 및 게시 상태는 [제출 준비 결과](docs/SUBMISSION_READINESS.md)를 확인하세요. 서명 전 파일이나 Debug APK를 제출 APK로 대신하지 않습니다.
- 실행: 정차 상태에서 운전자 선택 → **내 차 상태 보기** / **문제 남기기** → 음성 또는 글 입력 → 확인·저장 → 선택적 AI 설명/TTS → 기록·인계. 정비 상담 준비의 **내비에서 정비소 찾기**는 설치된 Pleos 내비를 엽니다. 업체의 지역·주소를 확인하고 선택하세요.
- 환경: Windows11/Android Studio/Pleos Connect v2.1.0(API34), 전체2560×1440·density160. 앱은 OS Driving View·시스템바·공조를 제외한 앱 창에서 표시됩니다. Vehicle2.3.2/NaviHelper3.0.5/Fused1.1.3/STT·TTS3.3.1. 기존 AVD·앱 데이터·서명키를 지우지 마세요.
- 설정: 마이크는 사용자가 허용해야 하며 홈 자동 대기는 운전자/계정별 기본 OFF입니다. 전면·정차·안전 허용 상태에서만 동작합니다. Gemini는 기존 Firebase 로그인·기기 등록·전송 범위 동의와 일5/월100/서비스 월300·비용 한도가 필요합니다. 이번 제출 준비에서는 유료 요청·Worker 배포를 하지 않습니다.
- 처음 설치하면 ‘시작하기’ → 운전자 등록 → 이름 선택·확정으로 홈을 엽니다. 첫 안내 이후 차량값이 없으면 ‘내 차 상태 보기’ → ‘차 상태 불러오기’를 한 번 눌러 주세요. 새 Release의 이 수동 경로에서 실제 SOC100%·거리0km를 확인했으며 첫 자동 조회 미동작은 남아 있습니다.
- 기존 일반 앱은 Debug 서명이므로 새 Release 서명 APK로 덮어쓸 수 없습니다. 별도 Pleos AVD에서 같은 제출 APK를 시험하며, 원래 앱을 삭제해 해결하지 않습니다. 정확한 서명·설치 상태와 실제/합성/미검증 구분은 위 보고서를 따릅니다.

아래는 이전 단계의 개발·검증 이력입니다. 수치와 APK 상태는 각 기록의 시점 기준이며 최신 제출 준비 결과를 우선합니다.

## B안 라이트 Android UI — 2026-11-11 22:54 +09:00

일반 CARITDA에 B안 라이트 기본/선택 다크를 적용했습니다. 홈은 운전자·미확인 인계·현재 유효한 차량 안내·문제 작성·차량/기록 순서이며 전역4탭을 정리했습니다. 위치/차량 기술·수정/상태/이력은 상세에 남겼고 SOC 자동 안내와 그래프는 별도입니다.

[실제 Android 캡처14장](docs/B_LIGHT_UI_GALLERY.md) · [22+2 화면/버튼 비교](docs/B_LIGHT_UI_SCREEN_MATRIX.md) · [446개·1600배치·일반 설치/원본 보존](docs/B_LIGHT_UI_ACCEPTANCE.md). 기존 기록24/이력97/운전자10·인증/AI 캐시/사용량을 보존했고 이번 유료 Gemini/Worker 배포는0회입니다. 일반 앱은 같은 서명으로 교체·실행했습니다. 일반9화면 사람 조작/고령자 이해도/실차는 미검증이며 자동 검사만으로 최종 UX 수용을 완료 처리하지 않습니다.


**차량 건강 보조 통합(2026-11-11):** 기존 화면에서 선택적 위치 연결 이력과 DriveCheck 안내→편집 초안→명시 저장→선택 AI·인계를 지원합니다. 실제 SDK SOC18/45 변화·중복 방지·재시작과433개 검사를 확인했습니다. 내비는 목적지·안내 상태를 실제 수신했지만 탐색 완료 이벤트가 빠져 전체 완료로 표시하지 않습니다. 급가감속 점수·소음/진동 고장 판정은 제공하지 않습니다. [이번 구현·실제/합성·미완료·사용자 확인](docs/DRIVE_CHECK_ACCEPTANCE.md).

**최신 실제 검증(2026-11-11):** 일반 CARITDA에서 선택한 증상 한 건의 **Gemini 신규 실시간 설명 → 화면 표시 → 한국어 앱 TTS**를 확인했습니다. 기존 Firebase·기기 등록·동의·암호화 캐시를 사용하며 Gleo 승인이 필요하지 않습니다. 모델ON·일5/월100/서비스 월300 정책 유지, 시험 후 TEST1/정식6/잔여4입니다. 기존 설명 보기는 Gemini를 호출하지 않습니다. [실행 결과·전송 범위·보존·사용법](docs/GEMINI_INDEPENDENT_ACCEPTANCE.md). 아래 단계별 모델OFF/신규0회는 당시 이력입니다.

## 공모전 최종 통합 기준 — 2026-11-11

CARITDA는 지원되는 차량 정보 확인 → 운전자 증상 초안 → 명시적 저장 → 선택적 Gemini 설명 → 다음 운전자 인계·점검 확인 흐름을 제공합니다. Android View 홈/기록/차량 정보 구조를 유지합니다. 자동 고장 진단·수리 완료 판정 서비스로 소개하지 않습니다.

**일반 앱에서 실제 한국어 음성이 확인 팝업 없이 입력란에 반영되는 것을 사용자가 확인했습니다.** STT는 공식 ON_DEVICE/request 자동 종료이며, 직접 확인한 뒤 저장해야 문제 기록이 됩니다. 이번281개 계측 검사와 일반 앱 데이터 보존·교체 설치를 완료했습니다. [전체 결과/제한](docs/CARITDA_INTEGRATION_REPORT.md), [STT 실행법](docs/STT_RUN_GUIDE.md)을 확인하세요.

Gleo Schema3개/Intent/Callback 구성과 보안 차단은 구현했습니다. **실제 Gleo wake word→새 Gemini→7초 내 Gleo 음성 답변은 미검증**입니다. 승인된 Schema와 검증 가능한 호출자·수신자 계약이 필요합니다. 캐시 재생을 새 분석으로 표시하지 않습니다. 기존 Firebase·Keystore·Worker·사용량 정책은 유지했고 이번 신규 모델/서버 배포는0회입니다. [Gleo 공식 근거·A~G 검증](docs/GLEO_INTEGRATION_ACCEPTANCE.md).

### 실행 환경과 APK

Android Studio에서 `NextDrive-Pleos`를 엽니다. 기존 Pleos_Connect_v2.1.0/API34, 앱 창2560×1440/density160에서 검증했습니다. Gradle8.11.1/AGP8.10.1/Kotlin2.2.21, Vehicle2.3.2, Navi3.0.5, Fused1.1.3, STT/TTS3.3.1입니다. 다른 PC는 공식 SDK Maven과 자기 환경의 Android SDK 경로가 필요하며 PC B 실행은 아직 미검증입니다. 기존 AVD/앱 데이터를 초기화하지 마세요.

일반 `com.nextdrive.pleos`와 검증 전용 `com.nextdrive.pleos.validation`은 별도 UID/DB입니다. 일반 앱은 기존 설치 서명과 같은 APK로만 `adb install -r` 교체합니다. API 키나 공통 인증 토큰을 APK에 넣지 않습니다. AI 최초 로그인·동의·기기 등록 이후 세션/암호화 캐시를 재사용합니다. 관리자 사용량/모델 설정을 앱이 임의로 바꾸지 않습니다.

| 산출물 | 프로젝트 루트 기준 경로 / 상태 |
| --- | --- |
| 일반 Debug | `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk` — 기존 Debug 서명, 일반 앱 교체·실행 검증 |
| TEST | `NextDrive-Pleos/app/build/outputs/apk/validation/app-validation.apk` |
| 계측 | `NextDrive-Pleos/app/build/outputs/apk/androidTest/validation/app-validation-androidTest.apk` |
| 서명 전 Release | `NextDrive-Pleos/app/build/outputs/apk/release/app-release-unsigned.apk` — 빌드만 성공, 설치 가능한 제출 APK 아님 |

기존 서명을 사용한 Debug/validation/AndroidTest, Lint, Release 빌드를 실행했습니다. AndroidTest는 격리 분기만 사용하며 인자 없는 기존 runner는 DB 준비 경로가 있어 실행하지 마세요. 정확한 분기·결과는 통합 보고서에 있습니다. Firebase 공개 Android 설정 파일은 로컬 구성으로 유지하며 인증 파일/DB/백업/음성/개인 캡처/APK는 이번 소스 커밋에 포함하지 않았습니다.

### 제출 준비 상태

이번에 [공식 공모전](https://appdevchallenge.auto-hmg.io/), [FAQ](https://appdevchallenge.auto-hmg.io/faq), [개발 가이드](https://appdevchallenge.auto-hmg.io/guide/development), [제출 가이드](https://appdevchallenge.auto-hmg.io/guide/submission)를 직접 확인했습니다. 접수양식(기획서)이 필수이고 영상·소스/APK GitHub는 선택 가산점 산출물입니다. 공식1차 마감은2026-11-15 23:59 KST, 2차 PT는12-03~04입니다.

- 공식 안내의 제출 APK 위치는 같은 저장소의 `apk/팀명_앱이름_release.apk`입니다. **현재 그 파일은 게시하지 않았습니다.** 기존 소유 Release 서명키 사용·서명/설치 검증과 사용자 게시 승인이 필요합니다. Debug를 Release로 이름만 바꾸지 않습니다.
- 시연 영상과 기획서 파일은 아직 이번 작업에서 생성·제출하지 않았습니다. 실제 일반 앱 STT 초안·저장/인계, SDK 차량 정보·그래프, 저장된 AI 설명을 구분해 촬영하세요. 신규 Gemini와 Gleo 왕복을 시연하려면 해당 실제 검증이 먼저 필요합니다.
- 과거 Private 준비 안내와 달리 2026-11-14 작업 시작 시점 API의 저장소 상태는 Public입니다. 이번 공개 범위 변경은0회입니다. 공식 FAQ의 심사 기간 공개 유지 또는 ZIP 대안은 최신 제출 안내를 따르며, 외부 제출은 별도 사용자 결정으로 남깁니다.
- 심사 기준 SHA는 최종 GitHub 연결 도구 게시 커밋을 사용합니다. 본 보고서에 자기 자신의 SHA를 넣기 위한 반복 커밋은 만들지 않습니다.

아래는 이전 단계의 실행 기록이며 현재 기능·검증 범위는 위 링크를 우선합니다.

2026-11-08: 실제 Pleos SDK SOC/누적거리 원본으로 그래프·통계·데이터 품질·자동 인사이트와 다음 확인 행동을 구현했습니다. Console80→60→18→45%의 실제 callback/차트/사건해제·cold복원, 기존320+신규49=369 assertion을 확인했습니다. SOC 현재값과 이전 사건을 분리하고 보조 버튼 경계·아이콘을 개선했습니다. 기존 홈·사용자 기록·원관측은 유지합니다.

**실제 AI는 아직 미연결입니다.** 사용자가 중계/인증/모델·비용 설정 없이 로컬 분석부터 진행하도록 선택했습니다. 모델 호출·외부 전송·서버 배포는 하지 않았습니다. 분석 설명은 로컬 규칙이며 JSON/동의 검사를 실제 AI 성공으로 보고하지 않습니다. [실행 방법·실제/합성 구분·검사·중단 로그·데이터 보존·남은 작업](docs/CARITDA_ANALYTICS_AI_INTEGRATED_REPORT.md)을 확인하세요.

차량 정보→차량 데이터 분석에서 충전량/누적거리와 기간을 선택합니다. 시험 사건을 일반 앱에 남기지 않으려면 `[TEST] CARITDA 검증 전용`에서 Console을 조작하세요. 관측은 허용된 가시 수명주기뿐이며 상시 감시·실차 고장/수리 판정이 아닙니다. 최종 APK는 Debug입니다. Release/심사자 접근·영상·기획서는 별도 준비가 필요합니다. 아래 과거 기록은 보존합니다.

---

# CARITDA(가칭) — 실제 SOC 자동 안내 PoC

2026-11-08: Pleos Virtual Console80→18→45%를 실제 SDK GET/변화 callback으로 수신해 원본·사용자 낮은 충전량 사건·중복 방지·조건 해제·재실행 복원을 검증했습니다. 가시/입력 허용 상태에서 명시적으로 켠 경우만 구독하며 종료 중 상시 감시·실차 고장/수리 판정은 아닙니다.

**부분 성공:** 실제 인증된 AI 모델은 아직 호출하지 않았습니다. HTTPS 연결과 validation JSON Mock만 검사했습니다. [실험·320개 검사·데이터 보존·AI 승인/설정·시연 순서](docs/CARITDA_VIRTUAL_DRIVING_AI_POC.md)를 확인하세요. 일반 앱은 데이터 유지 교체 설치됐고 자동 안내 기본 꺼짐/AI미연결입니다. 현재 사용자23건과 원본을 보존했으며 시험 사건은 validation에만 있습니다.

앱의 차량 정보→충전량 자동 안내·설정과 기록에서 사용자 기준을 설정합니다. 시험은 [TEST] 앱과 기존 AVD/공식 Console을 사용하세요. 실제 모델 시연에는 별도 승인된 HTTPS 중계·인증·비용 설정이 필요합니다. 키/DB/백업/로그/개인캡처/APK는 저장소에 게시하지 않습니다. 아래 이전 기록을 보존합니다.

---

# CARITDA(가칭) — 공통 메뉴·차종별 정보 표시 안정화

2026-11-08: 만족한 홈 구성을 유지하면서 독립된 동일5항목 메뉴, 명시적 수동 동력 방식 배치, 기본 KST/선택 기기 시간과 읽기 쉬운 조회 시각을 적용했습니다. 내부 내용 버전만 숨겼고 실제 원문·수정·점검·인계/재발·SDK 단발 조회는 유지합니다. SDK가 차종을 자동 판별하거나 고장을 진단하는 기능이 아닙니다.

최종 Pleos v2.1.0에서 기존246+신규51=297 assertion 통과, 일반 APK 데이터 유지 교체 설치/화면 확인, 이번 현재 사용자 기록23건과3DB 원본 보존을 확인했습니다. [API·권한 근거, 검사 범위와 직접 확인 순서](docs/VEHICLE_DISPLAY_COMPATIBILITY.md)를 확인하세요. 실제 ICE/HEV/PHEV 신호·CarInfo 수신·OS 분할/IME/물리 접근성·실차·Release는 미검증/미완료입니다.

일반 차량 아이콘→메뉴→차량 동력 방식·시각 설정에서 배치를 고릅니다. 설정은 사용자 선택이며 미지원 연료가0%로 바뀌지 않습니다. 사용자 PC의 소스와 일반 앱에 이미 반영됐으므로 별도 파일 수정은 필요하지 않습니다. Android Studio에서는 기존 NextDrive-Pleos/AVD를 사용하세요. 아래 이전 기록을 보존합니다.

---

# CARITDA(가칭) — Premium Automotive UI

2026-11-08: 실제 차량 정보 두 카드·확인할 기록·기록 확인/증상 남기기 중심 홈, 고정 적응형 탐색, 간편 작성과 선택 정보, 짧은 조회 이력/기술 상세를 구현했습니다. Kotlin Android View와 SDK2.3.2·기존 데이터/권한/단발 자동 조회를 유지합니다. 현대차 공식 자산을 복제하지 않은 CARITDA 자체 디자인입니다.

Pleos v2.1.0에서 기존215+신규31=246 assertion 통과, 일반 APK 데이터 유지 교체 설치/화면 확인, 기존 문제22건과3DB 원본 보존을 확인했습니다. 실제 고령 사용자 시험·OS 분할/IME·TalkBack/rotary·실차·Release는 미검증/미완료입니다. [디자인 시스템](docs/CARITDA_DESIGN_SYSTEM.md)과 [검사 범위·전후 비교·직접 확인 순서](docs/DESIGN_ACCEPTANCE.md)를 확인하세요.

로컬 NextDrive-Pleos를 Android Studio로 열어 기존 AVD에서 app Run을 사용합니다. 일반 앱은 차량 아이콘, 검증 앱은 [TEST] 플라스크입니다. Debug APK는 로컬 NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk이며 앱 데이터/키/백업/로그/개인 캡처/APK는 GitHub 게시 대상이 아닙니다. 아래 이전 기록을 보존합니다.

---

# CARITDA(가칭) — 실제 SDK 자동 스냅샷 P0

2026-11-08 검증: 앱 가시·입력 허용 진입당 한 번 실제 SOC/누적거리 조회 → 원본 저장 → 차량 상태 카드 한 개 갱신 → 사용자가 선택한 증상에 불변 스냅샷 연결. 고장 진단/자동 상태 변경/상시 감시가 아닙니다. 일반 차량 아이콘과 [TEST] 플라스크 검증 앱을 구분합니다.

기존174 + 신규 자동 기록41 = 215 assertion 통과. 일반 문제22건/원문·이력·초안 보존, 관측 원본 전체 보존/추가 메타데이터 이전을 확인했습니다. [구현·AM36 판정·실제 값/제약·실행 순서](docs/AUTOMATIC_RECORDING_ACCEPTANCE.md)를 확인하세요. 전체 AM36/사용성/실차가 통과했다는 뜻은 아닙니다.

기존 Android Studio에서 NextDrive-Pleos를 열고 기존 Pleos v2.1.0 AVD를 선택해 app Run을 사용합니다. 일반 APK는 현재 PC에 데이터 보존 방식으로 이미 설치됐습니다. 로컬 Debug APK는 app/build/outputs/apk/debug/app-debug.apk(프로젝트 내부 경로)이며 저장소에 APK를 게시하지 않았습니다. Release 서명/실행 검증·제출 APK 위치와 Private 저장소 심사 계정 접근은 아직 준비해야 합니다. 키/개인 DB/백업/로그는 공유하지 않습니다.

아래는 이전 버전의 개발 기록과 실행 안내입니다. 현재 기능/검증 상태는 위 링크가 우선합니다.

---

# CARITDA — 고정 홈·기록 검색 실행 검증 완료

2026-11-08: 스크롤과 분리한 앱 내부 홈, 전체/내 작성 바로가기, 네 필터와 카테고리·전체 본문·메모 검색/조건 초기화를 구현했습니다. 상세 이전의 검색·필터·목록 위치, 수정 취소/변경 없음/버전 충돌·초안 보존 실패 시 이동 차단을 확인했습니다. 네 수동 상태 변경과 기존 수정·논리 삭제는 유지합니다.

Pleos Connect v2.1.0 validation 앱에서 신규NAV29개+기존145개=174개 자동 검사를 통과했습니다. 일반 APK도 install-r로 교체하고 홈·스크롤·차량 정보·사용법을 확인했으며 운영 문제/관측 DB는 백업과 바이트 단위로 같습니다. 실제 SDK100%/0km 수신은 validation의 별도 관측이며 일반 앱의 과거 값을 회복 근거로 합치지 않습니다.

[검증 결과·NAV 기준별 부분/미검증과 사용 순서](docs/NAV_ACCEPTANCE.md), [자동화 후속 설계](docs/AUTOMATION_FOUNDATION.md), [진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. 자동 회복·Gleo·휴지통 복원은 미구현입니다. 실차/실제 주행 전환/종합 접근성·일반 사용자 시험·Lint는 남아 있습니다. 아래는 과거 기록입니다.

# CARITDA — 기록 저장·수정·삭제 신뢰성 개선

2026-11-08: 전체/관리 중/완료/내 작성 필터, 지속 저장 성공·실패 안내, 저장 후 상세 이동과 뒤로 가기 복구, 작성자 ID 기반 수정·논리 삭제를 구현했습니다. 발견 시각과 수정 전후 이력은 보존합니다. 기존 운전자·문제22건·이력·초안은 v3 이전 후에도 유지됐고 관측 DB 해시도 같습니다.

자동화는 별도 CARITDA 검증 전용 앱에서만 실행합니다. 기존 테스트를 포함한145개 검사와 운영 DB 보존·잘못된 운영 대상 테스트 거부를 확인했습니다. 과거 테스트 입증5건과 출처 미확정17건은 삭제하지 않았습니다. Virtual Console은 현재HTTP200입니다. 사람/실차/종합 접근성·Lint는 남아 있습니다.

[사용 순서·원인·새 인수기준·삭제 보존 정책](docs/RECORD_RELIABILITY.md), [진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. 이미 일반 앱을 데이터 보존 방식으로 교체 설치했습니다. 아래는 과거 기록이며 최신 결과를 우선합니다.

# CARITDA — Simple UI·첫 안내·사용법

2026-11-08: 홈의 다음 행동을 단순화하고 차량 정보의 충전량·주행거리·실제 수신 시각을 크게 표시했습니다. SDK/Capability/오류·세션은 불러오기 상세에 유지합니다. 신규 한 화면 안내와 여섯 항목 사용법/재보기, 초안 복귀를 구현했습니다. 기존 DB·운전자·수동 기록·SDK2.3.2·안전 제한은 보존했습니다.

앱/테스트 APK 빌드와 기존57개+신규32개 검사, 다섯 주요 화면200개 조합을 Pleos 에뮬레이터에서 확인했습니다. 현재 수신은 SOC15%/주행거리 원본1000000.0km, 연료 미지원이며 실차값/자동 진단이 아닙니다. ON기준은 통과13/부분8/미검증3이며 실제 사용자 이해도·실차·종합 접근성·Lint는 남아 있습니다.

[ON별 결과와 사용 순서](docs/ONBOARDING_ACCEPTANCE.md), [진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. 아래는 과거 버전의 기록이며 현재 상태는 이 절과 최신 진행 기록이 우선합니다.

# CARITDA — 운전자·UI M0 업데이트

2026-11-07: 이름 등록·카드 선택·운전 시작·이름 수정/보관, 홈/문제/차량 분리와 초안 복원을 구현했습니다. 기존 A/B DB는 영구 ID와 당시 이름을 추가하는 비파괴 v2로 이전했고 원래 문제 5건·이벤트 16건과 관측을 보존했습니다. 실제 Pleos 에뮬레이터에서 이전 18개 + 기존 24개 + 관측 6개 + 새 UI 27개 검사를 통과했습니다. SOC 100%/주행거리 0.0km 수신과 연료 미지원 처리를 유지합니다.

[사용 방법과 AC별 검증 범위](docs/UI_ACCEPTANCE.md), [이전·복구 계획](docs/UI_MIGRATION_PLAN.md), [최신 진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. Lint/실차/종합 접근성 검증은 미완료입니다. 아래는 이전 버전의 기록이며 현재 상태는 이 절과 최신 기록이 우선합니다.

## 최신 상태 — Pleos Vehicle SDK 실제 수신 확인

2026-11-07: 기존 v0.1을 보존한 채 SDK 2.3.2로 Pleos 에뮬레이터의 SOC 100%, 누적 주행거리 0.0km를 실제 수신·SQLite 저장했습니다. 연료 잔량은 미지원 응답으로 기록합니다. 실차값과 구분하며 앱 프로세스 재시작 후 복원, 기존 24개 회귀와 관측 6개 검사를 통과했습니다. [상세 결과](docs/VEHICLE_SDK_232_RUNTIME.md).

# CARITDA v0.1 — Vehicle App Workspace

Pleos Connect / Android Automotive OS에서 사용하는 차량 문제 기록·인계 MVP입니다. CARITDA(카잇다)는 상표 검증 전의 잠정 명칭이며 폴더·패키지 식별자는 NextDrive로 유지합니다.

현재 기능은 운전자 A/B 선택, 수기 증상 기록, SQLite 영구 저장, 다음 운전자 확인, 점검·상태·재발 이력, 정비 상담용 요약입니다. 모든 기록은 동일 차량 앱 내부에 저장하며 실제 차량 센서값·AI 진단·휴대폰 전송·정비소 접수는 제공하지 않습니다.

- Android 프로젝트와 실행: [NextDrive-Pleos](NextDrive-Pleos/README.md)
- MVP 사용·검증 안내: [docs/CARITDA_V01_MVP.md](docs/CARITDA_V01_MVP.md)
- 개발 기록: [docs/CODEX_PROGRESS.md](docs/CODEX_PROGRESS.md)
- 과거 환경 검증: [PLATFORM_VALIDATION.md](PLATFORM_VALIDATION.md)
- Vehicle SDK 미검증 항목: [SDK_CAPABILITY_MATRIX.md](SDK_CAPABILITY_MATRIX.md), [읽기 검증 계획](docs/VEHICLE_SDK_READ_PLAN.md)
- 게시 절차: [docs/GITHUB_SYNC.md](docs/GITHUB_SYNC.md)

2026-11-07: Debug 앱·테스트 APK 빌드 및 Pleos Connect v2.1.0/API 34 에뮬레이터 실행, 24개 계측 검사를 통과했습니다. Lint는 의존성 다운로드 제한으로 미완료입니다. 실차·배포용 Release APK·Vehicle SDK 데이터 수신은 미검증입니다. 기존 Google AI Studio 앱과 Aside 연구 파일은 수정하지 않았습니다.
