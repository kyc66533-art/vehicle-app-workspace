# CARITDA 주변 정비소 자동 검색 — 구현 기록 (2026-11-13)

## 2026-11-13 22:59 +09:00 — PR #3 로컬 통합·검증 결과

PR #3은 음성 수정 PR #2(a4e0cf5)를 대상으로 하는 Draft이며 시작 head는 a71bdc1d3a5de395b419f597542aedd0f46f0ea4다. 로컬302파일이 PR #2와 일치했고 원격 전용 Aside 연구29개는 보존했다. 원본 소스와 일반/validation 데이터를 별도 사본으로 보존한 뒤 PR #3의7파일을 반영했다. main 직접 변경·PR 병합·서버 배포·신규 Gemini 유료 호출은 하지 않는다.

### 실제 반영과 최소 수정

- 정비 상담에 위치 동의→기존 OneShotLocation/FusedLocation→Firebase 인증 검색→최대3곳 카드→사용자 선택/확인→기존 InspectionNavigation/NaviHelper.requestRoute 경로를 연결했다. 수동 좌표 입력과 기존 경로/약관/권한/CarSafetyGate/Listener 해제를 유지했다. 새 지도 SDK나 다른 내비 앱은 추가하지 않았다.
- NearbyRepairClient는 응답 출처·직선거리·후보 구분·ID/중복/범위/크기를 확인한다. 목록의100자 업체명은 보존하면서 기존 내비 목적지 이름80자 제한에 맞춰 전달한다. 잘못된 JSON과 네트워크/시간 초과/미설정을 구분한다.
- Main의 새 검색에 운전자·Firebase 계정·선택 기록 범위를 고정하고 화면 이탈/전환/안전 제한/계정 변경 후 늦은 결과를 버린다. 기존 FirebasePortableSession을 재사용하며 Gemini client를 만들거나 AI 동의를 우회하지 않는다.
- 새 Worker 경로가 반환한 비동기 ApiError를 기존 오류 처리기가 받도록 `return await nearbyRepair(...)` 한 곳을 보완했다. 이 전에는 미설정503이 처리기 밖으로 reject됐다. 기존 Gemini 분석/대화·기기 서명·BUDGET 원장 코드와 운영 설정은 바꾸지 않았다.
- 검색 핸들러는 두 키워드의 공급자 요청을 합계6초 범위로 제한하고, 기존 bodyText를 재사용해 응답을 스트림 단계에서64KiB로 제한한다. 값이 없는 거리를0m로 만들지 않는다. 결과/위치는 문제 SQLite에 자동 저장하지 않는다.
- 새 NearbyRepairChecks/runner의 prepare 전 분기와 nearby-repair-boundaries 오프라인 검사를 추가했다. 입력/위치/계정/검색/내비 fixture와 실제 SDK 결과를 구분한다.

### 빌드·검사

| 검사 | 결과 | 실제 범위 |
| --- | --- | --- |
| 원본 PR Kotlin 컴파일 | PASS | Debug/Validation AndroidTest compile,2분7초. 앱 컴파일 오류는 없었음 |
| 최종 Debug/Validation/AndroidTest | PASS | 기존 Gradle8.11.1/JBR·SDK 사용, 전체3분35초 |
| Lint/Unit | PASS/NO-SOURCE | Lint0오류68경고. Unit NO-SOURCE는 성공 건수에서 제외 |
| 새 서버 검색 | PASS | 기존11+경계29=40 assertion, 실제 Kakao/Gemini0 |
| 기존 Worker 회귀 | PASS | 인증/예산64·단일 문제42/반복4/overlay5·대화54=169, 모두 오프라인 |
| TypeScript | PASS | npm run check / tsc --noEmit |
| 새 검색 Android UI/계약 | PASS | 34 assertion. 실제 View/SQLite, 위치·인증·업체·내비 fixture |
| 영향 회귀 | PASS | 407 assertion: 위치/내비/DriveCheck·SOC·음성/호출어/STT/TTS·DB·Gleo 내부·AI 캐시·A/B 인계 |
| 운영 endpoint 확인 | PASS(진단2) | 에뮬레이터 health200/익명 빈 본문 repair404, 좌표·계정 전송0 |
| 실제 Navi SDK | 부분 확인(진단6) | 아래 실제 이벤트/화면과 미수신 완료 콜백을 구분 |

Android 합계449 assertion/22개 선택 실행, Node 합계209 assertion이다. 기존477/198 전체를 무조건 반복한 결과가 아니다. 일반 앱 UI 자동화·일반 APK 교체·SDK 버전/서명 변경은 하지 않았다. validation APK/AndroidTest만 기존 인증서 일치 확인 후 install-r로 교체했다.

프로젝트에 gradlew.bat가 없어 기존 사용자 Gradle8.11.1 배포본을 사용했다. 처음 프로젝트 전용 캐시에 AGP가 없어 컴파일 전 실패했으며, 기존 캐시를 프로젝트 내부 전용 캐시로 복사한 후 --offline으로 해결했다. 전역 설치·시스템 설정·기존 전역 캐시·키 변경은 없다. Kotlin daemon 접근 오류는 기존 in-process 컴파일 옵션으로 처리했다. 새 소프트웨어나 SDK를 내려받지 않았다.

### 실제 Pleos 내비게이션

NaviHelper3.0.5와 설치된 ai.pleos.playground.service.navi(version3.0.4), ai.umos.maps.android.navigation.app을 사용했다. 공식 문서의 좌표/‘공식 문서 목적지 시험’ 이름으로 단1회 요청한 별도 SDK 시험이며, 실제 Kakao 검색 결과가 아니다.

| 단계 | 실제 관찰 |
| --- | --- |
| 기존 경로·약관 | NAVI_PREFLIGHT_READY: 진행 경로 없음/동의 확인 후 요청 |
| 목적지 전달 | NAVI_REQUEST_SENT |
| 경로 탐색 시작 | NAVI_ROUTE_SEARCH_STARTED |
| 목적지 일치 | NAVI_DESTINATION_CONFIRMED |
| 안내 상태 | NAVI_STATUS_ROUTE_DRIVE / NAVI_MODE_ROUTE_GUIDING |
| 내비 화면 | 시험 목적지·경로·예상시간/거리·안내 종료 버튼 표시 확인 |
| 경로 탐색 완료 콜백 | **미수신**: NAVI_ROUTING_COMPLETED 없음,30초 후 NAVI_TIMEOUT |
| 목적지 도착/안내 종료 | 미시험. 정차/0km/h에서 도착 성공으로 해석하지 않음 |
| 정리 | 일치하는 새 시험 경로만 공식 SDK로 취소. routing=false/SAFE_GUIDING과 ‘어디로 갈까요?’ 화면 확인 |

Listener는 시간 초과/정리 시 해제했다. 실제 안내 화면 진입은 확인했지만 누락된 onRoutingCompleted를 임의 생성하거나 기존 네 조건의 guidanceConfirmed 기준을 낮추지 않았다. SDK/service 버전 차이는 관찰 사항이며 콜백 누락의 원인으로 확정하지 않는다. 일반 CARITDA가 내비 화면으로 전환돼 보이지 않으면 기존 정책대로 모니터링을 멈춘다.

공식 [목적지 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)는 requestRoute 호출 성공과 탐색 완료를 구분한다. API-reference destinations 페이지는 이번 조회에서 읽을 본문이 없어 별도 확인으로 취급하지 않았다.

### 현재 실제 검색 가능 여부

**BLOCKED: 운영 /v2/repair/nearby는 실제 에뮬레이터에서 HTTP404다.** 로컬 프로세스의 KAKAO_LOCAL_REST_KEY/PLACES_ENABLED와 .dev.vars도 미설정이다. 운영 Secret 값은 조회하지 않았으므로 서버 키 유무까지 확정하지 않는다. 신규 endpoint 배포·키/호출 제한 설정 전에는 실제 주변 업체 조회 성공이라고 보고할 수 없다. 서버 배포와 키 등록은 이번에 수행하지 않았다.

합성 목록은 TEST 배너가 있는 격리 UI에서만 표시했다. 블루핸즈는 검색 후보이며 공식 서비스망 인증·영업·예약·견인 성공을 의미하지 않는다. 키워드/좌표는 [카카오 Local 공식 문서](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)와 대조했으며 별도 지도·카카오내비 SDK는 사용하지 않았다. 실제 활성화 전에는 원래 계획의 별도 검색 호출 제한·비용 통제도 검토해야 한다.

### 데이터 보존과 엄격 비교 예외

시작 일반44DB/4016행+validation1031DB/15103행=1075DB/19119원본행을 새로 보존했다. 원본 행 키 누락0, 스키마·버전·integrity/FK 정상이다. 일반 문제31/이벤트113/운전자10/초안0, validation 루트1/1/2/0을 유지했다. 시험은 새 고유 fixture DB74개만 추가했다. 일반 관측342·스냅샷15·인사이트28·SOC평가27/공백13, 총425행은 기존 관측/파생 DB에 추가됐다. 일반 새 문제나 자동 해결은0이다.

**엄격한 원본행 완전 동일 검사는 FAIL(1열 차이)**다. 기존 일반 음성 세션1개는 ENDED 상태와 본문/연결 정보를 유지한 채 ended만21:30:13.559→22:45:25.918 KST로 바뀌었다. 기존 ConversationStore.state/Controller.end는 ENDED를 재기록할 때 종료 시각을 갱신하며, 내비 화면 전환 시점과 일치한다. 일반 APK/음성 코드는 교체하지 않았다. 기존 수명주기의 종료 메타데이터 갱신으로 판단하며 그 시각을 임의로 되돌리거나 이번 범위에서 음성 코드를 바꾸지 않았다. 전후 백업과 정확한 행/열 감사 근거를 보존했다.

나머지19118원본행은 동일하다. 일반 인증/AI 캐시 등 files/prefs17개는 바이트 동일/삭제0이다. validation435개 동일/진단·fixture 포인터·heartbeat·fixture AI 캐시·설치 표시7개 변경/삭제0을 별도로 기록했다. 따라서 전체 DB/파일 완전 불변으로 확대하지 않는다. 개인 DB·음성/SDK 로그·백업·실제 내비 캡처는 게시하지 않는다.

### 에뮬레이터에서 직접 확인할 절차

1. Android Studio에서 기존 프로젝트의 validation 변형을 선택해 [TEST] 앱을 실행한다. Debug/Validation/ValidationAndroidTest Gradle 작업 이름은 위 표와 같다. 일반 앱 삭제/데이터 초기화/무인 prepare는 하지 않는다.
2. 정차/CarSafetyGate 허용 상태에서 TEST 운전자를 확정하고 기존 TEST 기록의 상세→점검·상태·상담→정비 상담 준비로 들어간다.
3. ‘현재 위치로 주변 정비소 찾기’를 누르고 위치/외부 전송 범위를 확인한다. 현재 배포 상태에서는 미연결 안내가 예상 결과다. 실제 업체 목록을 만들어 표시하지 않는다.
4. 승인된 향후 서버 설정/배포 뒤 동일 흐름에서 최근 마지막 위치·검색 결과·직선거리/후보 표시를 확인한다. 이 조회는 Gemini를 요청하지 않는다.
5. 업체를 선택하고 목적지를 확인하면 기존 NaviHelper로 전달한다. 기존 경로가 있으면 요청을 중단한다. 고급 좌표 입력도 그대로 사용할 수 있다.
6. ‘길찾기 수신 이력’과 내비 화면을 함께 확인한다. 목적지 전달/탐색 시작/탐색 완료/안내 상태/도착은 별개이며 현재 완료 콜백 누락을 성공으로 바꾸지 않는다. ‘요청 결과 확인 멈추기’는 내비 경로를 취소하지 않는다.

자체 검사 실행은 validation runner의 nearbyRepairOnly / driveCheckNavigationUiOnly / driveCheckNavigationLiveOnly 같은 명시적 prepare 전 분기만 사용한다. live 검사는 기존 경로를 보존하고 새로 만든 일치 경로만 정리하는 별도 시험이다. 향후 실제 검색/일반 앱 사용자 흐름/실차/콜백 누락의 플랫폼 원인은 아직 미완료다.

## 목표
정비 상담 화면의 기본 경로를 '현재 위치로 주변 정비소 찾기'로 바꾼다. 운전자는 좌표를 입력하지 않는다. Pleos FusedLocation 마지막 위치 → 명시적 동의·신선도 확인 → Firebase 인증 Cloudflare Worker /v2/repair/nearby → 카카오 Local 키워드 검색 → 최대 3곳의 주소·전화·직선거리 → 사용자가 고른 목적지에 기존 NaviHelper를 전달한다.

최초 초안 판정: 소스 구현·별도 브랜치 게시만 수행했다. 당시 미실행인 빌드/검사와 현재 상태의 차이는 상단 통합 결과를 따른다. 실제 정비소 검색이 성공했다고 보고하면 안 된다.

## 기존 기능 재사용
- MainActivity.kt: 정비 상담 화면의 새 검색 버튼, 상태, 결과 카드, 목적지 선택.
- integration/NearbyRepairClient.kt: Firebase ID 토큰을 사용하는 HTTPS 요청, 응답 검증, 오류 처리. 로컬 데이터베이스를 변경하지 않는다.
- infra/gemini-relay/src/nearby-repair.ts: 서버에만 보관할 카카오 REST 키, 반경 20km 거리순 검색, 블루핸즈 후보 우선 표시 및 중복 제거.
- infra/gemini-relay/src/portable-worker.ts: 기존 Firebase 인증 뒤 추가 라우트 처리. Gemini 호출이나 AI 사용량 장부는 건드리지 않는다.
- infra/gemini-relay/test/nearby-repair.test.mjs: 공급자 모의 응답만 쓰는 오프라인 테스트. 최초 게시 때 미실행이었으며 이번 실행/추가 경계 검사 결과는 상단에 구분했다.

## 프라이버시와 안전
- 검색할 때마다 사용자가 위치·외부 서비스 전송에 동의한다. 정확한 위치는 CARITDA Worker로 보내고, 카카오 전송 전에 좌표를 소수점 3자리로 줄인다. 증상, 운전자 이름, 기존 DB는 보내지 않는다.
- 마지막 위치가 10분 이상 오래되거나 시각 이상이면 검색을 중지한다. 마지막 조회값은 현재 실제 위치를 보장하지 않는다.
- 화면 거리는 직선거리다. 실제 이동거리·영업시간·예약 가능 여부·공식 블루핸즈 인증 여부는 확인하지 않는다.
- 경로 요청은 사용자가 업체를 선택하고 확인한 뒤 실행한다. 기존 경로 보호, CarSafetyGate, SQLite·AI·음성 설정은 유지한다. 자동 예약·견인 호출 없음.
- 종전의 수동 좌표 입력은 고급 대체 경로와 기존 회귀 테스트를 위해 남겨 둔다.

## 활성화 전 필수 조건
1. 카카오 디벨로퍼스의 Local API 사용 가능 여부, 쿼터, 비용을 확인하고 사용자 계정에서 키를 발급한다. 새 서비스 가입이나 결제는 임의 진행하지 않는다.
2. Cloudflare Worker secret KAKAO_LOCAL_REST_KEY를 서버에만 등록한다. Android APK, GitHub, 문서에 키를 올리지 않는다.
3. 별도 호출 제한·비용 통제를 확인한 뒤 PLACES_ENABLED=true 설정 및 Worker 배포를 사용자 승인 후 진행한다.
4. 기존 Gemini Durable Object BUDGET, Firebase 인증, 암호화 캐시, AI 과금 원장은 절대 초기화·삭제·재생성하지 않는다.
5. Node 22 이상 환경에서 아래 오프라인 검사, npm run check, Worker dry-run을 수행한다. Android Studio에서 기존 빌드와 driveCheckNavigationUiOnly/인계/음성 회귀 검사를 실행한다.
6. Pleos 실제 위치→카카오 검색 결과→NaviHelper 경로 상태를 개별 검증한다. 기존 일반 APK를 임의 교체하지 않는다.

오프라인 검사 명령:
    cd infra/gemini-relay
    node --experimental-strip-types test/nearby-repair.test.mjs
    npm run check

## 사용자 경로
기록 → 기록 상세 → 점검·상태·상담 → 정비 상담 준비 → 현재 위치로 주변 정비소 찾기 → 동의 → 결과 카드 → 이곳으로 길찾기.

## GitHub 협업
기준은 음성 수정 PR #2의 커밋 a4e0cf5이다. 이 작업은 별도 feature 브랜치에만 게시하고 기존 main과 PR #2를 변경·병합하지 않는다.

공식 근거:
https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword
https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations
https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
