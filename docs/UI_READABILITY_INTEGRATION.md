# CARITDA UI·가독성 통합 검증

검증일: 2026-11-14 KST. 기반은 Draft PR [#6](https://github.com/kyc66533-art/vehicle-app-workspace/pull/6)의 `6248c31512130dc32f6fce8fb134262c01fe4f50`이다. main/PR #2/#3/#6/팀원 브랜치에는 변경을 게시하거나 병합하지 않는다. 작업 브랜치는 `feature/ui-readability-native-nav-20261114`, PR의 비교 기준도 PR #6 브랜치다.

## 기준과 원본 보존

- 시작 시 연결 도구로 확인한 main은 `7f913a466490664171706029a3235885558a7584`. 로컬 소스·설정·문서 265개를 PR #6의 blob SHA와 비교했으며 미게시 충돌은 없었다.
- 기존 소스, 일반/validation/test APK, 설정, 두 앱의 databases/shared_prefs/files를 `.tools/ui-readability-20261114/`에 보관했다. 이 사적 백업·인증·실사용 기록·APK는 게시하지 않는다. Git reset/clean/stash/checkout, 앱 삭제/데이터 초기화는 수행하지 않았다.
- 팀원 [Issue #5](https://github.com/kyc66533-art/vehicle-app-workspace/issues/5), 패치 16개 파일, 문구표, README, PR #3 ZIP을 읽고 함수 단위로 이식했다. ZIP을 소스 위에 덮어쓰지 않았다.
- 최초 자료 `11f00d05429b94e7de860663df7c03dff6fa8e89`의 PNG 18장을 다운로드해 원본 blob SHA/크기를 검사하고 **모두 실제로 열어** 비교했다. 작업 중 팀원 브랜치가 `f4568d29baca7045089bda460a3dad006891d00e`로 전진했다. 차이는 README와 PR #6용 ZIP뿐이었다. 새 ZIP 16개도 별도 참고 폴더에 풀어 대조했다. 기존 패치·18개 이미지는 변경되지 않았다.
- 팀원 예시 APK는 설치하지 않는다. 이 PC의 기존 디버그 서명을 사용한 APK만 검증한다. Release 키·Firebase·Keystore·Worker 설정은 바꾸지 않는다.

## 16개 패치 파일의 채택·수정·보류

경로의 공통 접두사는 `NextDrive-Pleos/app/src/`다. 테스트 기대값을 없애거나 실제 실패를 성공으로 바꾸지 않았다.

| 파일 | 결정 | 실제 반영 및 보완 |
|---|---|---|
| main/.../MainActivity.kt | 수정 채택 | 설정 7행, 화면 색 아이콘, 유형 타일, 접힌 기술·인계 설명, 쉬운 문구. `moreMenu`와 `summary` 충돌은 함수 단위로 병합. PR #6 검색어·버튼·상태·안전 동작 유지. ‘기록 모두 보기’와 이전 검색 복원 구현 |
| main/.../PortableAiActivity.kt | 수정 채택 | 입력 크기·간략 설명·전체 설명 접힘. 두 모드의 기존 동의 원문은 그대로 보존. Cloudflare→Google Gemini/Firebase, 보관·철회·인증 의미 유지. ‘이 기기’ 표현 사용 |
| main/.../VoiceConversationActivity.kt | 채택 | 설정 체크박스 글자만 확대. 세션/마이크/호출어/동의 저장 방식은 그대로 |
| main/.../analytics/VehicleAnalytics.kt | 수정 채택 | 분석 설명을 쉽게 표현. ‘받은 값 평균’, 20%p 변화, 10분 **이상** 공백의 정확한 의미 유지. 계산·외부 전송 로직 변경 없음 |
| main/.../ui/CaritdaDesign.kt | 수정 채택 | 본문 기본 +6sp·대비·아이콘·목록 행·타일·접힘. 76dp 유지. 실제 눌림 색 대비 실패를 수정하고 버튼 높이/줄바꿈 확보 |
| main/.../ui/CaritdaPage.kt | 수정 채택 | 실제 남는 여백에만 CARITDA 표시. 글씨 배율·창 폭을 반영하고 라이트/다크 대비 확보 |
| main/.../ui/VehicleHealthPresentation.kt | 채택 | 도어·등화 등 한 번에 한 그룹 펼치기. 원시값 의미·출처·오래됨·실패 안내 유지 |
| androidTest/.../AnalyticsChecks.kt | 채택 | 새 화면 진입 경로와 실제 접힘을 사용. 레거시 실행기는 이번에 실행하지 않고 컴파일 확인 |
| androidTest/.../AutomaticChecks.kt | 채택 | 현재 차량 화면의 실제 진입 경로로 조정. 레거시 실행기는 컴파일만 확인 |
| androidTest/.../BLightStageTwoChecks.kt | 채택 | 실제 도어 그룹을 펼친 다음 원시값 표현 검사. 실제 validation 실행 |
| androidTest/.../CompatibilityChecks.kt | 채택 | 바뀐 설정/차량 접근 경로 반영. 레거시 실행기는 컴파일만 확인 |
| androidTest/.../DesignChecks.kt | 채택 | 목록형 설정·현재 탐색 구조로 접근. 레거시 실행기는 컴파일만 확인 |
| androidTest/.../IntegratedFlowChecks.kt | 수정 채택 | AAOS ‘기기에서만’ 기대값, 기존 실제 차량 연결/AI/TTS 검사 유지. 실제 validation 실행 |
| androidTest/.../NavigationChecks.kt | 수정 채택 | 실제 홈/이전 경로, 헤더 가시성 검사 뒤 원래 목록 스크롤 복원. 레거시 실행기는 컴파일만 확인 |
| androidTest/.../OnboardingChecks.kt | 수정 채택 | ‘모두 보기’는 ALL·빈 검색이고 이전 검색 복원 제공. 미지원 연료 검사에서 항상 참이 되는 OR 제거. 레거시 실행기는 컴파일만 확인 |
| androidTest/.../ValidationUiActions.kt | 채택 | 현재 B안 버튼/설정 경로로 실제 이동. 사라진 전역 탭을 클릭한 척하지 않음 |

추가 검증 파일: `ReadabilityUiChecks.kt`와 runner의 `readabilityUiOnly` 조기 분기. `BLightLayoutChecks.kt`는 보안 AI 화면의 캡처를 건너뛰도록 했다. `FLAG_SECURE`를 해제하거나 우회하지 않으며 두 AI 모드의 실제 View/동의 원문/배치는 그대로 검사한다.

채택하지 않은 내용: PR #3의 `repairNearbyPanel`/`NearbyRepairClient`/`findNearbyRepair`, 카카오 위치 전송 확인창, ‘이 휴대폰’ 일괄 치환, SOC의 %와 %p 혼용, 동의 전체 설명을 약화하는 축약. 신규 지도·검색 서비스·서버 기능은 추가하지 않았다.

## 화면·기능 비교

| 화면 | 이전/팀원 제안 | 통합 결과와 연결된 기능 |
|---|---|---|
| 홈 | 옅은 보조 글씨, ‘모두 보기’가 이전 필터를 유지 | 대비·크기 개선. 운전자/미확인 인계/실제 확인 필요 상태/작성/차량·기록 순서 유지. 자동 문제 생성 없음 |
| 기록 목록 | 검색/필터가 남아 ‘모두’와 불일치 | 홈 ‘기록 모두 보기’는 ALL·빈 검색으로 연다. 이전 검색어·입력문·필터·스크롤을 별도 보관하고 ‘이전 검색으로 돌아가기’ 제공. 상세→이전은 현재 조건 보존 |
| 설정·도움말 | 여러 카드와 작은 글씨 | 화면 색 3타일(좁은 창·큰 글씨는 1열), 운전자/화면/음성/사용법/처음 안내/AI/앱 정보의 7행. 하단 개인정보 설명 접힘 |
| 문제 작성 | 텍스트 유형 버튼 | 아이콘+글자 2×2 타일, 선택 표시·원래 유형·STT·초안·명시적 저장 유지 |
| 차량 | 반복된 세부 수신 설명 | 주요 수치/배터리 기준 안내/별도 그래프 경로 유지. 같은 유형의 상세를 그룹으로 접음. 미수신을 0·정상으로 해석하지 않음 |
| 기록 상세·위치 | 긴 기술 정보 | 본문·상태·주요 행동 우선. ‘위치가 기록에 저장됐어요.’와 마지막 확인 위치 안내. 좌표·출처·이력은 상세에 유지 |
| 인계 | 반복된 사용 설명 | 미확인 개수와 짧은 설명, 자세한 방법은 접힘. 읽음/점검/해결은 별개로 유지 |
| AI 두 모드 | 긴 설명을 기본 표시 | 간략 안내+전체 동의 설명 접힘. 실제 전송 범위와 원문 유지. 신규 호출/계정 등록/동의 변경은 시험하지 않음 |
| 정비 상담 | 팀원 추가 캡처는 카카오 흐름 | **PR #6의 네이티브** ‘자동차 정비소’ 검색으로 연결. 목록·선택·길 안내는 설치된 내비게이션에서 진행. 증상 전송 없음 |
| 앱 정보 | 개발자용 정보가 바로 노출 | 바깥 ‘개발자용 정보’ 접힘 안에 실제 SDK 원문·로컬 JSON 도구 유지 |
| 사용법·처음 안내 | 긴 설명 | 기록 확인/증상 작성/다음 운전자 전달을 쉬운 존댓말로 안내. AI는 선택 기능으로 유지 |
| 확인창 | 시스템 기본 크기의 작은 글씨 | 제목 30sp·본문 26sp·버튼 30sp, 다중 줄·76dp, 취소 시 초안 보존 |

## 팀원 이미지 18장 실제 대조

[원본 자료(고정 SHA)](https://github.com/kyc66533-art/vehicle-app-workspace/tree/f4568d29baca7045089bda460a3dad006891d00e/docs/ui-readability-20261113). 원본 자료는 수정·재게시하지 않는다.

| 원본 이미지 | 관찰/채택 또는 조정 |
|---|---|
| 전후비교/01_홈 | 글씨 대비·우선순위 채택. 확대 후 어색한 줄바꿈은 짧은 홈 제목 두 곳으로 수정 |
| 전후비교/02_내_차_상태 | 큰 주요 수치, 안내/그래프 분리, 상세 접힘 채택 |
| 전후비교/03_기록___다음_운전자에게_넘기기 | 목록·검색 가독성 채택. ‘모두 보기’ 필터 혼동은 별도 수정 |
| 전후비교/04_다음_운전자에게_넘기기_인계_ | 짧은 미확인 요약·접힌 설명 채택 |
| 전후비교/05_설정___도움말 | 화면 색 아이콘과 7행 목록 채택, 좁은 창은 1열 |
| 전후비교/06_사용법 | 큰 설명 채택, 최신 M3 문구 사용 |
| 전후비교/07_화면_보기_차량_표시_시각_ | 대비·글자 확대 채택, 시간대·원본 시각 의미 유지 |
| 전후비교/08_운전자 | 이름·관리 버튼 가독성 채택, 영구 ID/보관/과거 작성자 유지 |
| 전후비교/09_앱_정보_자세한_수신_기록_ | 원문 접근 유지, 최신 M4 바깥 접힘 사용 |
| 전후비교/10_처음_안내 | 세 단계 설명 채택, 기존 사용자 작업을 초기화하지 않음 |
| 전후비교/11_문제_남기기 | 큰 입력·2열/좁은 창 1열, 최신 M1 아이콘 사용 |
| 추가_캡처/H1_분석_아래 | 통계·로컬 설명·상세 접힘. %p·평균 모집단·공백 기준은 정확하게 보완 |
| 추가_캡처/J1_로고_홈 | 여백 로고 채택. 좁은 창에서는 숨기고 다크 대비 보완 |
| 추가_캡처/L1_정비소찾기_화면 | 카카오 기능/UI 보류. PR #6 Pleos 검색으로 대체 |
| 추가_캡처/L2_정비소찾기_확인창 | 확인창 크기만 채택. 카카오 위치 전송 동의 재도입 없음 |
| 추가_캡처/M1_문제남기기_타일 | 아이콘·텍스트·선택 채움·테두리 채택 |
| 추가_캡처/M3_사용법_카드 | 세 가지 실제 작업 경로 채택 |
| 추가_캡처/M4_앱정보_접힘 | 바깥 개발자용 접힘 채택, 안쪽 도구 보존 |

## 실제 검사 결과

검증 완료 시각: 2026-11-14 01:23:13 +09:00. **531개 assertion / 26개 안전한 조기 분기 검사 PASS**, 별도로 **1,600개 실제 Android View 배치 조합 PASS**다. 중간 실패/재검사, NO-SOURCE, 팀원 PC의 검사 수는 성공 수에 중복 합산하지 않았다.

| 대상 | 실제 결과 | 검증 수준 |
|---|---|---|
| Debug / Validation / Validation AndroidTest / Lint | PASS; 최종 V6 107작업, 단위 테스트 NO-SOURCE 제외 | 컴파일·정적 검사 |
| Lint | 오류 0 / 경고 70; 경고를 0개로 표현하지 않음 | 정적 검사 |
| 새 가독성 검사 | 38 PASS: 모두 보기/검색 복원·재생성, 7행 설정·실제 접힘·유형 타일·취소/초안·대비·두 AI 동의 | 에뮬레이터의 실제 View/SQLite, 외부 SDK 입력은 fixture |
| B안 실제 흐름/냉간 재진입 | 29 + 21 + 3 PASS | 에뮬레이터 View/격리 SQLite |
| 레이아웃 | 3 assertion / 1,600조합: 320·600·840·1024dp × 가로/세로 × 1/1.3/1.5/2배 × 라이트/다크 × Main23+AI2 | 에뮬레이터 실제 창/자원/텍스트/스크롤 측정; 데이터는 합성 |
| 확인창/버튼 | 320·600dp, 1·2배, 양 테마 확인창 8조합 및 눌림 4종×양 테마 대비 ≥4.5 | 네이티브 대화상자·실제 drawable 합성 색상 |
| 관련 기존 회귀 | 407 PASS / 19분기: 위치·경로·SOC·홈 호출어·음성 자동화·수동 STT·AI·Gleo 내부·저장/인계 | SDK seam은 합성; 실제 SDK 사용 항목은 아래 별도 |
| Pleos TTS | 준비한 전체 한국어 문장 두 번 실제 재생 완료; 3 assertion PASS | 실제 SDK PLEOS_ON_DEVICE; 사람 증상 발화 아님 |
| 네이티브 정비소 검색 | 26계약/UI + 실제 SDK4 PASS. 무경로/약관 확인→검색 화면 전달; 실제 업체 목록과 검색어를 XML/캡처로 확인 | 실제 NaviHelper/설치 내비 응답. 신규 목적지 선택·안내/도착·onRoutingCompleted는 이번 미검사 |
| 실제 사용자 STT/호출어·Gemini 신규 분석 | 미실시. 음성 controller/콜백/AI 캐시·UNKNOWN/중복/한도/인계는 위 fixture 회귀 통과 | 실제 발화/새 모델 성공으로 확대하지 않음 |

검사는 모두 `ValidationEnvironment.prepare` **전**의 명시 분기와 고유 UUID 저장소를 사용했다. 일반 앱 UI 자동화, 기존 validation DB 초기화, OS 글꼴·화면 크기 변경은 0회다. V4에서 407개 회귀와 실제 내비 연결을 완료한 뒤 최종 V6까지는 Main의 문구 세 곳만 변경했음을 바이트 비교했다. V6에서 새 38개 UI 검사와 전체 1,600배치를 다시 통과했다. 테스트 APK 자체의 SHA는 V4/V5/V6가 동일하다.

발견·수정한 실패도 보존했다. 첫 대비 검사에서 라이트 DANGER 눌림 비율 4.481이 실패해 덮개 투명도를 줄였고 실제 5.505를 확인했다. 첫 AI 동의 fixture가 로그아웃 상태여서 로그인 화면만 열렸던 오류는 전송을 금지한 로그인 fixture로 교정했다. 정비소 UI 검사가 검색어 누락을 검출해 ‘자동차 정비소’를 복원했다. 마지막 캡처에서 홈 제목의 한 글자만 내려가는 줄바꿈은 짧은 제목 두 곳으로 조정했다.

재실행은 validation/test APK로만 `adb -s emulator-5554 shell am instrument -w -r --user 10 -e readabilityUiOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation`처럼 **분기 인자를 명시**한다. 무인자 prepare나 debug 대상 runner를 실행하지 않는다. [분기별 결과](ui-readability-integration-20261114/checks.json)에 실제 assertion을 남겼다.

### 원본 데이터와 설정

원본 **1244DB / 20,766행**을 전후 독립 사본에서 비교했다. schema/version/integrity/FK는 일치·정상, 기존 행 누락은 0개다. strict 전체 행 동일: **PASS**.

| 앱 | 기존 DB | 기존 행 | 변경/누락 | 원본 DB 내 추가 행 | 현재 루트 문제/이벤트/운전자/초안 | 기존 private 파일 동일/전체·삭제 |
|---|---:|---:|---|---:|---|---|
| com.nextdrive.pleos | 44 | 4514 | 0 / 0 | 0 | 31/113/10/0 | 17/17 · 0 |
| com.nextdrive.pleos.validation | 1200 | 16252 | 0 / 0 | 0 | 2/2/2/0 | 449/487 · 0 |

validation의 기존 private 파일 38개는 바뀌었다. 시험 포인터·Firebase heartbeat·캡처/배치 결과·설치 프로파일 등이 포함되며, 기존 portable-issue 암호화 설정 파일 1개도 바이트가 달라졌다. 이 암호화 파일의 평문 전체 동일 여부는 이번에 복호화 비교하지 않았다. 따라서 validation 캐시까지 전부 바이트 동일하다고 주장하지 않는다. 변경 전 원본은 보존했고 삭제된 파일은 없다. 일반 앱의 기존 private 17개는 모두 바이트 동일하다.

기존 hot journal 1개는 이미 존재했다. **독립 분석 사본**에서만 SQLite의 정상 복구 후 비교했고 DB 해시는 복구 전후 동일했다. 앱 원본·tar·백업을 수리/삭제/초기화하지 않았다. validation 새 fixture DB/암호화 캐시/캡처는 추가되며 과거 저장소를 덮어쓰지 않는다.

모든 검사 후 일반 APK만 기존 동일 서명으로 install-r 업데이트했다. 일반 앱 실행·UI 조작·자동 문제 저장은 하지 않았다. APK SHA-256: `28761ecaccf187e07f6c4cdf81e32372fbded26f7f6a8cb469fc2bad60369528`. 일반 앱 설정/인증/기존 캐시 파일과 Worker·NaviHelper·음성·DB 구현은 원본 SHA 검사로 보존했다. 신규 Gemini 유료 호출·Cloudflare 배포·새 외부 서비스·서명 변경 0회다.

### 새 Android 캡처와 시각 비교

21개 선택 캡처는 TEST fixture의 비개인정보 화면이다. 기존/새 홈 비교는 서로 다른 UUID의 같은 빈 인계 조건이므로 데이터 값 비교가 아닌 글자·대비·구조 비교다. HTML을 다시 출력한 이미지가 아니다.

| 실제 캡처 | 확인 내용 |
|---|---|
| [PR #6 이전 홈](ui-readability-integration-20261114/before-home.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [새 홈: 큰 글씨·대비·짧은 표제](ui-readability-integration-20261114/home.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [새 홈 다크](ui-readability-integration-20261114/home-dark.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [7행 설정·아이콘 테마](ui-readability-integration-20261114/settings.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [문제 종류 타일](ui-readability-integration-20261114/record.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [320dp·2배 글꼴: 줄바꿈·스크롤](ui-readability-integration-20261114/record-320-font2.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [차량 주요 수치·안내/그래프 분리](ui-readability-integration-20261114/vehicle.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [차량 다크](ui-readability-integration-20261114/vehicle-dark.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [검색/필터 목록](ui-readability-integration-20261114/records.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [내용·상태·AI·위치](ui-readability-integration-20261114/detail.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [인계 미확인 목록·접힌 설명](ui-readability-integration-20261114/handover.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [세 가지 작업 사용법](ui-readability-integration-20261114/help.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [차량 표시·시각 설정](ui-readability-integration-20261114/display.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [등록·이름 변경·보관 경로](ui-readability-integration-20261114/drivers.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [개발자용 접힘](ui-readability-integration-20261114/developer.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [처음 안내](ui-readability-integration-20261114/onboarding.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [별도 시계열 분석](ui-readability-integration-20261114/analytics.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [PR #6 정비소 검색 진입](ui-readability-integration-20261114/native-repair-entry.png) | 에뮬레이터에서 실행한 실제 Android View를 PNG로 저장. 데이터는 TEST 합성 예시 |
| [실제 emulator screencap: 홈](ui-readability-integration-20261114/emulator-home.png) | ADB 화면 캡처에서 validation 앱 창만 잘라냄. OS/다른 앱 영역 제외 |
| [실제 emulator screencap: 설정](ui-readability-integration-20261114/emulator-settings.png) | ADB 화면 캡처에서 validation 앱 창만 잘라냄. OS/다른 앱 영역 제외 |
| [실제 emulator screencap: 작성](ui-readability-integration-20261114/emulator-record.png) | ADB 화면 캡처에서 validation 앱 창만 잘라냄. OS/다른 앱 영역 제외 |

새 캡처를 열어 팀원 18장과 대조했다. 큰 글씨에서 내용이 여러 줄/아래 스크롤로 이어지는 것은 유지하되 고정 높이 잘림·버튼 겹침·읽을 수 없는 눌림 대비를 허용하지 않았다. 2배·320dp는 한 화면에 담기는 양이 적어 실제 고령 사용자 체험은 추가 필요하다. 보안 AI 이미지는 내보내지 않았으며 실제 View의 두 모드 배치와 전체 동의 원문을 별도로 확인했다.


## 한계와 직접 확인

- 자동 UI/DB·합성 SDK 콜백 성공은 사용자 실제 STT·호출어·Gemini 모델·실차 성공이 아니다. 이번에는 신규 유료 Gemini 호출 0회, Cloudflare 배포 0회다.
- 보안 AI 화면은 이미지로 내보내지 않는다. 실제 사람의 문구 이해도, 물리 rotary/TalkBack, 실차 및 OEM 분할 화면은 별도 확인 대상이다.
- 자동 검사는 OS 글꼴/해상도를 바꾸지 않고 검증 Activity의 창·리소스만 조정한다. OS 시스템바·Driving View·공조와 주행 제한을 우회하지 않는다.
- 레거시 `analyticsOnly`/`compatOnly`/`designOnly`/`onboardingOnly`/`navigationOnly` 등은 기존 runner의 `prepare` 뒤에 있어 실행하지 않았다. 기존 validation DB 보존을 위해 컴파일만 확인하며, 이번 핵심 흐름은 UUID 조기 분기 검사로 확인한다. 팀원 PC의 검사 수를 이번 성공 수에 합산하지 않는다.
- 사용자가 직접 볼 경로: 홈 → 설정·도움말(화면 색/운전자/사용법), 홈 → 문제 남기기(유형 타일·음성 입력), 홈 → 기록 모두 보기(검색 복원), 기록 상세 → 정비소에 말할 내용 정리 → 내비에서 정비소 찾기, 내 차 상태 → 배터리 안내 및 별도 그래프.

