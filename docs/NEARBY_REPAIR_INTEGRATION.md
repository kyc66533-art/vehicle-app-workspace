# CARITDA 정비소 찾기 — 기존 Pleos 내비게이션 재사용

## 2026-11-13 23:55 +09:00 — 카카오 계획 중단·실제 검색 연결

최신 지시에 따라 카카오 Local API와 별도 지도/검색 서버 계획을 중단했다. PR #3 head `a0a421fadaaa6d9acb69a9686977e5deb7e38b98`에서 별도 `fix/pleos-native-repair-search-20261113` 브랜치로 작업한다. main/PR #2/원래 Draft PR #3을 직접 변경·병합하지 않는다. 이전 카카오 구현은 원래 PR #3과 비공개 로컬 사본에 보존하며 현재 후속 작업으로 키 등록·배포를 요구하지 않는다.

## 실제 구현과 사용자 경로

**운전자 선택 → 기록 상세 → 점검·상태·상담 → 정비 상담 준비 → 내비에서 정비소 찾기 → 내비 목록에서 장소 → 목적지로 → 경로 확인/안내**

기존 NaviHelper **3.0.5**/PleosNavigationBackend를 재사용해 공개 `requestNavigationScreen(NavigationScreenRequest.SearchPlace("자동차 정비소"))`를 호출한다. 이름·위도·경도·API 키 입력은 필요 없다. 목록과 장소 선택·경로 안내는 설치된 내비 앱이 담당한다. 새 지도 SDK·내비 앱·CARITDA 서버 검색은 없다.

새 InspectionSearch는 앱 가시·확정 운전자·CarSafetyGate ALLOWED·권한·약관을 확인한다. 기존 경로나 안내/모의 안내 모드가 있으면 새 검색을 보내지 않는다. 중복 연결/상태 callback을 차단하고 화면 이탈·운전자/안전 상태 변경·시간 초과 시 generation 및 removeListener/release를 적용한다. **화면 REQUEST_SENT와 제한된 오류 감시 종료는 검색 결과/안내 성공이 아니다.** 기존 InspectionNavigation.requestRoute와 수동 목적지 고급 경로·기존 엄격한 안내 완료 조건은 유지한다. 기본 흐름에는 수동 입력이 없다.

Manifest에 공개 Normal 권한 NAVI_CUSTOM_MAP만 추가했다. CARITDA가 증상·운전자·Firebase 계정·위치를 검색 서버로 전송하지 않는다. 설치된 내비의 위치/검색 데이터 처리는 해당 앱의 기존 기능이다. SearchPlace는 검색 화면 요청이며 CARITDA가 POI 목록/선택 좌표를 반환받는 기능으로 만들지 않았다. 최단거리 순서·실시간 영업·서비스점 인증·예약·견인을 보증하지 않는다.

## 공식 근거·실제 설치 상태

- [NaviHelper3.0.5 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [SearchPlace](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/entry), [requestNavigationScreen](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-navigation-screen): 웹 API 문서는 항목/탐색 구조만 읽혀 상세 signature는 공식 NaviHelper3.0.5 및 전이 navi-data3.0.4 sources.jar의 공개 클래스/KDoc으로 대조했다. InternalApi는 호출하지 않는다.
- [공식 권한 가이드](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions)의 NAVI_CUSTOM_MAP과 AAR 소스 Normal 권한을 확인했다.
- [공식 navi-app 예제](https://github.com/Pleos-Playground/pleos-sample-navi-app/tree/76ed592487a1eab7ba102b9d4b321705426886fe)의 NaviHelperViewModel.requestNavigationScreen와 NaviTestData.RouteOptionList를 확인했다. 예제 좌표를 실제 업체 결과로 사용하지 않았다.
- [공식 이벤트 가이드](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/event-listeners)처럼 탐색 시작/완료/안내 상태/도착을 구분한다.
- 실제 설치: `ai.umos.maps.android.navigation.app` **3.3.2-rc5-hotfix.3**, Navi 서비스 **3.0.4**. APK pull은 Permission denied여서 우회하지 않았다. dumpsys의 공개 Android ACTION_SEARCH는 resolve됐지만 표준 query만으로 검색 화면은 열리지 않아 채택하지 않았다. geo VIEW는 resolve되지 않았다. Gleo용 broadcast·임의 component·private API는 사용하지 않고 실제 작동한 SDK SearchPlace만 채택했다.

## 이번 실제 에뮬레이터 시연

동일 소스 **TEST CARITDA**의 고유 UUID 문제/운전자 fixture에서 실제 버튼을 누른 뒤 기존 내비 공개 UI만 선택했다. 일반 앱 UI 자동화는 하지 않았다.

| 단계 | 결과 | 실제 증거 |
| --- | --- | --- |
| CARITDA → SDK 검색 요청 | PASS | CONNECTED/PREFLIGHT_READY/REQUEST_SENT, 실제 권한/약관/무경로 확인 |
| 정비소 검색 결과 | PASS | 내비 검색란 ‘자동차 정비소’와 업체명·주소·거리 목록, 이름/좌표 입력 없음 |
| 장소 선택·경로 미리보기 | PASS | 실제 목록 업체 선택 → ‘목적지로’ → 추천 경로/시간/거리 화면 |
| 안내 화면 진입 | PASS | 내비 자체 미리보기 후 목적지·경로·안내 종료 표시. ‘안내 시작’ 수동 tap 전에 내비 자체 전환이 진행돼 그 tap은 실행되지 않음 |
| SDK 선택 목적지·안내 모드 | PASS | ROUTE_SEARCH_STARTED / 선택 업체명·좌표 일치 / STATUS_ROUTE_DRIVE / MODE_ROUTE_GUIDING |
| onRoutingCompleted | NOT_RECEIVED | 별도 실제 리스너 **45초** 동안 미수신. 화면 요청이나 다른 이벤트로 완료를 생성하지 않음 |
| 도착·실차 | NOT_TESTED | 정차 에뮬레이터 시험이며 도착 성공을 주장하지 않음 |
| 새 시험 경로 복원 | PASS | 최초 무경로+선택 이름/좌표를 재확인한 새 경로만 공식 cancelRoute. SDK 무경로 및 ‘어디로 갈까요?’ 화면 재확인 |

이전의 예제 좌표 시험과 달리 이번에는 실제 검색 결과를 선택한 흐름이다. 목록을 가까운 순으로 가정하지 않고 내비의 예상 SOC를 실제 현재 SOC로 해석하지 않는다. SDK 전체 완료 계약은 **부분 확인**이며 기존 InspectionNavigation.guidanceConfirmed의 네 조건을 낮추지 않았다. 실제 화면/로그는 개인정보 보호를 위해 비공개 로컬 `.tools/pleos-repair-20261113/private`, results에 보관한다.

## 코드·빌드·검사

앱 변경: MainActivity의 카카오 검색/위치/로그인/카드를 공식 화면 진입으로 교체, NavigationBackend 공개 화면 요청 추가, InspectionSearch 신규, Manifest Normal 권한 추가. 검사: InspectionSearchChecks/InspectionSearchLiveChecks 및 runner의 prepare 전 분기, 기존 내비 UI 검사 설명 수정.

NearbyRepairClient/기존 검사·nearby-repair.ts/두 Node 검사는 현재 브랜치에서 제외했다. portable-worker.ts는 카카오 import/라우트/분기를 제거해 **PR #2 원본 blob `e2f5ef235dd6bb046a5e0d426b1d8dfafd3c04c1`과 바이트 동일**하다. Gemini 분석/대화·Firebase·기기 서명·BUDGET 본체·운영 설정은 수정하지 않았다. 음성 호출·SQLite 스키마·DriveCheck·Gleo·위치·인계 핵심 코드는 보존했다.

| 검사 | 이번 최종 결과 |
| --- | --- |
| Debug/Validation/Validation AndroidTest | PASS, 기존 오프라인 Gradle/JBR/서명. 최초3분2초·최종1분9초 |
| Lint | 오류0 / 기존 경고68 |
| Unit | NO-SOURCE, 통과 건수 제외 |
| 신규 Android 상태/실제 View·SQLite | 26 assertion, SDK 부분은 fixture |
| 영향 회귀 | 407 assertion/19분기: 위치/내비·DriveCheck/SOC·수동 STT/홈 호출/TTS·DB·Gleo 내부·AI 캐시·A/B 인계/재시작 |
| 실제 SDK/내비 진단 | 화면 연결4 + 선택 경로4 assertion, 완료 callback 미수신 별도 표기 |
| 최종 Android 집계 | **441 assertion / 22개 선택 실행**. 중간 실행·기존477개는 중복 합산하지 않음 |
| Worker 오프라인 | 인증/예산64 + 문제42/반복4/overlay5 + 대화54 = **169**. 실제 모델0 |
| TypeScript | npm.cmd run check/tsc --noEmit PASS. wrapper CMD 호출 오류 뒤 직접 실행 성공, 소스 컴파일 오류 아님 |

validation과 일반 앱 모두 기존 인증서 일치 확인 후 install-r 업데이트했다. 일반 Debug SHA256는 `3cc21eed55c17fc644f04088a2987a7424726ded6d5717d0c272630932f29f4d`다. 일반 UI 자동 실행/조작·새 문제/운전자 추가·앱 삭제/clear·AVD/서명 변경은 없다. 일반 앱 사용자의 수동 조작은 이번 TEST 시연과 별개다. 실제 사람 STT·새 Gemini·외부 Gleo·실차 성공으로 확대하지 않는다. 유료 Gemini/서버 배포/새 서비스 신청은 **0**이다.

## 데이터 보존 — 엄격한 완전 동일성 FAIL, 기존 종료 시각 1열

새 기준 사본은 일반44DB/4,441행 + validation1,105DB/15,580행 = **1,149DB/20,021원본행**이다. 원본 키 누락0·schema/version/integrity/FK 정상, 일반31문제/113이벤트/10운전자/0초안과 기존 validation 루트1/1/2/0 보존이다.

이미 ENDED인 일반 음성 세션 1개의 `ended`만 **22:45:25.918 → 23:39:46.628 KST**로 재기록됐다. 나머지 열과20,020행은 동일하다. 변경 시각은 이번 일반 APK 업데이트 **23:49:07 KST보다 앞서**, 이전 일반 앱 실행 기간에 발생했다. 변경하지 않은 ConversationStore.state는 ENDED마다 현재 시각을 쓰고 Controller.end가 수명주기에서 다시 호출될 수 있다. 정확한 호출 스택은 미계측이며 기존 종료 처리로 판단한다. 이번 정비소 작업에서 음성 코드를 수정하거나 원본 값을 되돌리지 않았다. 전후 사본과 정확한 행 비교를 보존하며 완전 동일로 축약하지 않는다.

일반에는 관측56/스냅샷2/인사이트6/SOC평가7·공백2 = 파생·관측73행이 추가됐고 문제는 추가되지 않았다. validation 기존15,580행은 동일하고 고유 fixture DB95개가 추가됐다. 일반 인증/AI 캐시 등을 포함한 private17파일은 전부 동일/삭제0이다. validation 기존462파일 중455동일/fixture 포인터·Firebase heartbeat·로컬 검사 캐시·설치 표시7변경/삭제0이다. 개인 DB·백업·로그·APK·실제 지도 캡처는 GitHub에 게시하지 않는다.

## 남은 범위·게시

이제 외부 API 키·CARITDA 서버 배포 없이 내비 검색을 사용할 수 있다. 진행 중인 경로가 있으면 먼저 내비에서 현재 안내를 확인한다. 남은 사항은 onRoutingCompleted 미수신의 플랫폼 원인, 실차/도착, 일반 앱 사용자 직접 확인이다. 서비스/SDK 버전 차이는 관찰했지만 누락 원인으로 확정하지 않는다. 음성 종료 시각의 반복 기록은 별도 후속 점검 항목이다. 상시 내비 감시·예약·견인 기능은 추가하지 않았다.

검증된 명시 파일만 GitHub 연결 도구를 통해 새 수정 브랜치와 **PR #3 대상 별도 PR**에 게시한다. main/PR #2/원래 Draft PR #3/Aside와 다른 파일은 보존한다. 실제 커밋·PR 링크는 원격 확인 후 최종 응답으로 제공한다.
