# CARITDA B안 라이트 · 실제 Android UI 개편

## 최종 구현·검증 기록 — 2026-11-11 22:54 +09:00

**Android UI 구현/빌드/validation 자동검증은 완료했고 일반 앱에 교체 설치·실행했다. 일반 앱9화면의 사람 실조작·고령자/초보자 이해도·실차는 미검증이다. 따라서 최종 사용자 경험 수용까지 ‘전체 완료’로 처리하지 않는다.** 기존22개 화면+AI2모드와 신규 인계25개 모두 실제 Kotlin View에 대응했다. [화면/버튼 전수 비교](B_LIGHT_UI_SCREEN_MATRIX.md), [실제 native 캡처14장](B_LIGHT_UI_GALLERY.md).

| 단계 | 결과 | 실제 근거/한계 |
| --- | --- | --- |
| 1 최신 main/원본·설계 대조 | 완료 | 시작 c424ec25, 로컬221 일치/원격 연구26 보존·새 전체 백업 |
| 2 홈·상단·확인 필요 상태 | 완료 B/C/D 일부 | 전역4탭 제거·미확인 큐·현재 조건, 실제 SOC18→홈/45→제거 |
| 3 전체 본문 간소화 | 완료 B/C | 22+2+신규 인계, 위치/수정/보관·계정/동의/수동 상태의 새 경로 |
| 4 배치/회귀/시안 대조·설치 | 자동검증 완료, 사람 UX 부분 확인 | 1600조합/446 assertion/9페이지 시각 대조/일반 실행·원본 비교. 일반9화면 실조작/이해도 미확인 |

### 1. 없앤 중복 메뉴와 버튼

홈의 홈 버튼/전역 기록·차량·메뉴4탭, 메뉴의 업무 목적지 복제/내 작성, 목록의 빈 결과 전체보기·상시 초기화, 도움/메뉴/안내 다시보기의 중복 닫기, 대기 중 비활성 음성 취소·빈 인식 결과를 정리했다. 하위 이전/홈, 홈의 단일 기록/차량 진입과 목록의 MINE 필터·조건부 초기화로 연결한다. 같은 목적의 숨은 호환 View는 없다. 전체 상담/원문 듣기·네 수동 상태/재발/수정/삭제·계정 기능은 접힌 실제 경로로 남겼다. 차량 안내를 누르면 안내가 있는 첫 위치를 열고, 일반 차량 진입의 기존 스크롤은 유지한다.

### 2. 기본 화면에 남긴 정보

홈은 현재 기록 운전자/빠른 전환→미확인 인계→현재 유효한 확인 항목→작성/보존 초안→차량·기록이다. 인계/확인 항목0이면 빈 대형 카드를 만들지 않는다. 정상 수치나 지난 SOC 사건을 홈에 나열하지 않는다. 차량은 확인 항목·주요 수치/수신 시각/출처·짧은 한계·자동 안내/그래프의 별도 입구를 남겼다. 작성은 종류4개/상황/음성·선택 정보 접기와 명시 저장 영역이다. HTML의 select 대신76dp 네 선택을 유지해 기존 종류 계약과 차량 조작을 보존했다. 상세는 원문→상태/작성자→읽음 또는 메모→AI→위치/차량→접힌 관리/이력이다.

### 3. 상세로 이동한 기술 정보

문·등화·차량 안내/페달·타이어 기준4그룹 뒤에 신호별 raw/단위/지원/수신 상태/시각/출처, ID/세션/원관측 참조를 둔다. 미지원 연료도20종 기술 상세에서 확인한다. 문 정수는 열림/닫힘으로, 타이어 기준은 실압으로 해석하지 않는다. 저장 위치는 “위치가 기록에 저장됐어요.”와 저장 시각/기기 마지막 확인 위치 주의만 먼저 표시한다. 좌표·측정/수신 시각·SDK 출처·이력은 위치 자세히 보기에 있다. 로컬 JSON 전송 요약 도구는 기술 정보로 옮겼으며 실제 AI 요청처럼 표시하지 않는다.

### 4. 홈 확인 필요 항목의 조건

기존 DriveCheck.assess의 latest attempt/last valid, supported/valid/source/unit/신선도를 그대로 사용한다. 현재 유효한 POWER_DOWN=true, BRAKE_FLUID/ENGINE_OIL 안내 enum, WASHER_LOW=true, 그리고 **사용자가 켠 SOC 기준 미만**만 VehicleAttention에서 같은 kind1개로 만든다. SDK 안내를 먼저/사용자 기준을 다음에 보여주는 표시 순서이며 위험도 순위가 아니다. source는 실제 SDK 계열이어야 하고10분 미만/미래 아님·정확한 단위 조건을 유지한다. 최신 실패/권한/미지원/오래됨은 차량 이상 카드로 승격하지 않는다. 도어 정수·등화ON/OFF·페달·저압 기준을 고장으로 쓰지 않는다. 실제 일반 에뮬레이터에서는 차량 안내 일부가 미지원이며 그 신호의 실제 이상 발생을 확인했다고 주장하지 않는다.

### 5. 기존 기능·데이터 보존

STT의 ON_DEVICE/request/Locale.KOREA·final+ended·취소/generation은 불변이다. SpeechControls는 표시 순서와 빈/비활성 영역만 정리했다. 저장/저장AI/전송 사본·최초 동의·계정/한도·pending/UNKNOWN/중복 방지·캐시 출처·전체 TTS·Gleo3 Action/Callback은 유지했다. 화면 진입만으로 모델을 요청하지 않으며 새 센서/위치/이름/메모를 증상 AI에 넣지 않는다. 미저장 분석/가짜 문제는 추가하지 않았다. 위치는 append, 네 문제 상태/수정 버전/읽음/재발은 원래 서비스 의미 그대로다.

TTS 회귀에서 시작 전 STOPPED가 재현돼, 완료된 구절 정리에도 stop을 보내던 경로를 제거했다. 같은 EventListener를 제거한 뒤 release하고 **활성 재생의 사용자 취소/안전 제한에서만 stop**한다. generation·실제 onDone 완료 조건과20초 실패 watchdog을 유지하며 이벤트 무시나 타이머 성공을 만들지 않았다. isFinal=true는 그대로다. [공식 재생/중단](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop), [동일 리스너 해제 계약](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)을 실제3.3.1 AAR와 대조했다. 수정 후 같은 player에서 전체 준비 문장을 연속2번 실제 Pleos 완료, 합성 전달/취소/실패/안전 gate 회귀도 통과했다. 준비된 문구와 실제 모델 응답·사람 청취는 구분한다.

기존330DB(일반42/validation288)의 전체 원본 **11,264행(1345+9919)**, 테이블 schema/user_version/integrity/FK를 전후 비교해 보존했다. 일반 문제24/이벤트97/운전자10/초안0 불변, 기존 DB에 정상 진입 SDK/파생 **23행만 추가**됐다. 설치 직후 기존16 private파일 모두 동일, 실행 후15개 동일/files/profileInstalled 설치 표시1개만 갱신됐다. Firebase 인증·동의·암호화 AI 캐시/사용량 파일은 동일하다. Keystore를 추출·재생성하지 않았고 앱 UID/서명을 유지했다. validation 기존9919행 불변/고유 fixture251DB 추가·원래 default0/0/2/0 유지, 포인터·SDK 메타7개 갱신. 기존 validation도 삭제하지 않았다.

이번 유료 Gemini 신규 요청/Cloudflare 배포/운영 할당량 변경0이다. 모델ON·일5/월100/서비스300·비용 예약 정책을 변경하지 않았으며 서버 잔여 횟수를 새로 조회했다고 주장하지 않는다. 기존 Gemini 성공1회를 이번 신규 통합 성공으로 재사용하지 않는다.

### 6. B안 라이트 적용

공통 라이트 F4F7F9/FFFFFF/142D3D/12596A, 다크101D27/192C39/F1F6F9/9BE4D5, 실제 white header/구분선·인계 강조선·카드18dp/패딩24dp·32/24/20/40sp·최소76dp를 적용했다. 미선택 기본은 라이트이며 기존 명시 테마 선택은 보존한다. 넓은 창은1.4:1, 큰 글씨/작은 창은 한 열이다. 최대1160dp 읽기 폭을 OS 앱 창 안에 두며 immersive/시스템바/공조/Driving View 제어는 추가하지 않았다. 짧은 AI 창/큰 글씨는 상단과 본문을 함께 스크롤한다. 결과가 준비되면 전체 듣기를 설명 위에서 찾을 수 있다.

### 7. 실제 화면과 시안 대조

해시527ad37bd9b9482a183277b2f64ff7c48e90b52c의 원본 HTML B 라이트9페이지를 네트워크/파일/콘텐츠 접근이 차단된 Android WebView로 렌더해 native 홈/차량/작성/목록/상세/AI/인계/설정/도움과 직접 시각 대조했다. 이 이미지의 Chromium DPR1.375를 측정해 CSS viewport1024를 맞췄다. 로컬 Chrome/Edge 실패0x80000003을 우회한 성공으로 숨기지 않는다. WebView는 **기준 시안용 시험 Dialog**일 뿐 실제 앱 화면이 아니다.

실제 Kotlin 캡처30장 중14장을 검토/게시한다. 합성 fixture UI, 실제 SDK SOC18, 다크/320dp 큰 글씨를 표시로 구분한다. 일반 앱은 실제 초기 운전자 선택 화면의 B 라이트/OS 경계와 resumed를 확인했고 원본 캡처는 개인 이름 보호를 위해 로컬 비공개로 보관했다. 일반 앱의9화면을 자동 조작하지 않았다.

### 8. 빌드·회귀·배치 결과

Debug/Validation/ValidationAndroidTest/Lint exit0. 최종 공통 소스 빌드2m23s/107tasks, 계측 추가 빌드1m11s/44tasks. Unit task는 NO-SOURCE이며 단위 테스트 성공으로 집계하지 않는다. Lint **오류0/경고63**다. SDK/Gradle/16KB·미사용 리소스/프로그램 전용 ViewConstructor/DrawAllocation·UseKtx 등이며 경고 전체 정리는 미완료다.

고유 validation 조기분기의 **446 assertion PASS**, 별도로25scene×4폭(320/600/840/1024dp)×2창 방향×4font(1/1.3/1.5/2)×2테마=**1600 실제 View 조합 PASS**다. 실제 요청/측정 폭, 텍스트 ink/ellipsis/높이, 가로 넘침,76dp, 스크롤로 버튼 접근을 검사했다. 창 크기와 앱 process의 Configuration만 바꾸고 OS wm/폰트/AVD 설정은 바꾸지 않았다. 구성 재생성/초안·편집·검색·필터·복귀·SQLite commit/실패 보존·위치/스냅샷/cold·운전자 추가/수정/보관·인계·AI 한도/중복/캐시·STT/TTS/Gleo 회귀를 포함한다.

| 격리 분기 | assertion |
| --- | --- |
| bLightStageOneOnly | 29 |
| bLightStageTwoOnly | 21 |
| bLightColdOnly | 3 |
| bLightLayoutOnly | 3 |
| bLightReferenceOnly | 2 |
| driveCheckNavigationUiOnly | 7 |
| driveCheckLocationUiOnly | 6 |
| driveCheckLocationColdOnly | 2 |
| driveCheckP1Only | 24 |
| driveCheckSocUiOnly | 7 |
| driveCheckSocColdOnly | 2 |
| driveCheckSocLiveOnly | 12 |
| driveCheckSocLiveColdOnly | 3 |
| driveCheckRestoredOnly | 2 |
| integratedFlowOnly | 35 |
| integratedFlowColdOnly | 4 |
| integratedVehicleOnly | 4 |
| integratedTtsOnly | 3 |
| speechDraftUiOnly | 21 |
| sttAutomaticUiOnly | 6 |
| sttSessionOnly | 40 |
| issueAiOnly | 56 |
| issueAiColdOnly | 4 |
| portableAiOnly | 26 |
| portableAiUiOnly | 4 |
| coreDatabaseOnly | 19 |
| integrationOnly | 33 |
| integrationLiveOnly | 7 |
| gleoOnly | 37 |
| analyticsContractOnly | 24 |

실제 SDK 검사는20종 중12수신/8미지원, SOC100/거리0.0을 확인했다. Console100→실제 SDK18 callback→새 홈 사용자 기준 안내/반복18→45 callback/현재 안내 제거, A 명시 저장/B 읽음/cold복원, 종료 구독 공백을 검증했다. 마지막 Console100/P/0와 SDK100·CarSafetyGate ALLOWED를 복원하고 이번에 만든8088 forward만 해제했다. 실제 Fused valid와 Navi routing=false/terms=true 상태도 읽었고 목적지를 새로 요청/기존 경로 취소하지 않았다.

초기 실패도 구분한다: fixture batch의 연료 미지원 누락, 숫자0.0/표시0 기대, trailing whitespace를 글자 ink로 계산한 검사, 캡처 파일명의 underscore, Dialog inset/DPR는 각각 수정해 재검사했다. 첫 단발 SDK의 SOC 미수신은 이후 실제 Console/SDK·동일 단발 검사에서 수신을 확인했으며 원인을 임의 단정하지 않는다. 간헐 TTS 미완료는 안전한 상태 코드로 STOPPED-before-start를 확인하고 위 정리 경로를 수정한 뒤 연속 전체 재생을 통과했다. 과거 성공으로 실패를 덮거나 신규 Gemini/실차 성공으로 바꾸지 않았다.

### 9. 미완료·검증 한계

일반 앱9화면의 사람 실조작/실발화·실제 기존 AI 캐시 청취, 고령자/초보자의 이해도/만족도, 물리 rotary/TalkBack·실제 OS 분할/물리 회전, 실차는 미검증이다. 인앱 창 가로/세로·구성 재생성·font fixture와 구분한다. Lint 경고63개, 기존 Navi 검색완료 이벤트 미수신, 공개 연속 속도/가속도·소음/진동의 고장 판정 근거 부족, Gleo 외부 승인은 이번 UI로 완료되지 않는다. UI 코드/핵심 기능을 일부만 바꾼 상태로 남기지는 않았지만 최종 사용자 경험 수용은 사람 확인이 필요하다.

### 10. 일반 앱·GitHub

기존 Debug 서명 일치·install-r/user10·설치 APK 재추출 SHA 대조 성공. Debug APK SHA256 **3b5d1aa1d397fc37e61c35a1b7645300c500003b92c4ef3c06890dbadfe30f9b**. MainActivity COLD Status:ok/resumed 성공, UI 자동 입력0/일반 시험 문제0. 마지막 전체 보존 검사를 위해 앱을 종료했다. APK/키/DB/백업/일반 개인 캡처는 게시하지 않는다.

단계1 cc825855f90f9d4071620f657e11267369e57426, 단계2 ee39d6f131f388f4cf46372395e14265362d164b에 이어 이번 검증된 명시 파일만 **GitHub 연결 도구를 통한 게시**한다. 최신 main의 tree/parent·기대HEAD/force=false, 대상 blob과 나머지/Aside26 SHA를 확인한다. 최종 SHA는 완료 응답/원격 main에서 확인하며 자기 SHA를 문서에 넣기 위한 반복 커밋은 만들지 않는다.

### 11. 사용자가 직접 확인할 최소 조작

1. 일반 CARITDA를 열어 본인 이름→‘이 이름으로 기록 시작’을 누르고 홈의 가독성을 확인한다. 어두운 화면을 명시 선택해 둔 경우 설정·도움말→밝게로 B 라이트를 볼 수 있다.
2. 홈→차량 상태 보기에서 주요 수치/안내를 보고 ‘기준 설정·안내 기록’과 ‘그래프·분석 보기’가 별도인지 확인한다. 설정→운전자 등록·이름 관리에서 추가/수정/보관 경로를 볼 수 있다.
3. 홈→문제 남기기의 음성/직접 편집 화면, 또는 기록 전체 보기→기존 기록→AI 설명 보기→기존 설명의 음성 듣기를 확인한다. 실제 문제를 남길 때만 내용을 확인해 명시 저장한다. 이 확인에 신규 유료 분석은 필요하지 않다.

### 변경 파일

일반 Kotlin9개: MainActivity, PortableAiActivity, ui/CaritdaDesign, CaritdaPage, CaritdaTheme, VehicleAttention, VehicleHealthPresentation, integration/SpeechControls, SpeechOutput. 실제 Manifest/themes도 적용했다. 저장소/DB migration/AI client/Worker/Firebase 설정/SDK 버전/Release 키는 불변이다. validation Kotlin12개는 BLightStageOne/StageTwo/Layout/Reference/Harness/TestNavigation, Mvp runner, DriveCheckP0/P1/SocLive, IntegratedFlow, SpeechDraftUi다. 문서·안전한 native PNG만 추가 게시하며 연구는 수정하지 않는다.

---

아래는 각 단계 당시의 기록이다. 최종 상태는 위 기록을 우선한다.

## 단계 1 — 기준·홈·탐색 (2026-11-11 Asia/Seoul)

기준 main `c424ec25cfe5599faf0958c96b9814d7fe3bd9d1`, tree `94e2e389fb44495f4d8e3acfd14a529377dd1ad1`. 로컬221개 파일 일치, 원격 전용 Aside26개 보존. 승인된 REDESIGN_20261111 AUDIT/DESIGN/CODEX/MOBILE/PROTOTYPE와 보조 README/SOURCES/VALIDATION8개를 blob SHA 대조 후 읽었다. 연구 파일은 수정하지 않았다. 연구의 옛 P0/P1 한정 제안보다 이번 사용자의 전체 화면 구현 지시를 우선한다.

| 사용자 단계 | 현재 상태 | 근거 |
| --- | --- | --- |
| 1 최신 소스·안전·설계 대조 | 완료 | 원격/로컬 비교·새 독립 백업·연구 대조 |
| 2 상단·홈·확인 필요 연결 | 완료(코드/validation) | B 공통 토큰, 새 헤더, 인계 큐, 조건별 홈·검색·초안·이전29개 |
| 3 차량·기록·인계·AI 전체 간소화 | 완료(코드/기능 계측), 시각 대조는 다음 단계 | 그룹·접힌 상세·실제 저장/AI/인계 경로 |
| 4 전체 화면/폭/흐름/캡처·일반 앱 | 미완료·다음 검증 | 단계1 검사만으로 전체 UX 완료를 주장하지 않음 |

### 실제 변경

B 라이트는 #F4F7F9/#FFFFFF/#142D3D/#12596A, B 다크는 #101D27/#192C39/#F1F6F9/#9BE4D5. 원래 명시한 테마 선택은 보존하며 미선택 기본은 라이트다. 제목32/본문24/보조20/값40sp·카드18dp/패딩24dp·버튼76dp 하한을 사용한다. 차트·행동 서비스·DB·인증·SDK 버전을 바꾸지 않는다. Material light 시작 테마를 실제 Manifest에 적용했고 Compose를 도입하지 않았다.

홈 순서는 기록 운전자/빠른 전환→미확인 인계→유효한 차량 안내→작성/기존 초안→차량·기록 허브다. 정상 수치·지난 LOW/EXITED 사건 카드를 홈에 반복하지 않는다. 빈 인계/차량 안내는 큰 빈 카드로 표시하지 않는다. 넓은 창은 B의 1.4:1 두 영역, 작은 창/큰 글자는 한 열이다.

홈과 차량의 확인 필요 목록은 읽기 전용 `VehicleAttention`이 기존 `DriveCheck.assess` 결과를 사용한다. SDK 안내를 먼저, 사용자 SOC 기준을 다음에 배치하는 **표현 순서**이며 위험도 순위가 아니다. 현재 유효한 `POWER_DOWN=true`, 비정상 수위 enum `BRAKE_FLUID/ENGINE_OIL`, `WASHER_LOW=true`, 사용자가 켠 기준 미만 SOC만 대상이다. source·supported·valid·단위·10분 미만/미래 아님을 검증한 기존 assess 조건을 유지한다. 같은 kind는 한 번만 표시한다. 최근 실패/미지원/권한/오래됨은 현재 안내로 올리지 않는다. 도어 정숫값·등화 ON·저압 기준·페달을 이상으로 해석하지 않는다. 홈 한 카드에서 상세의 이유와 작성 행동으로 이어지며 문제/초안/해결을 자동 생성하지 않는다.

새 인계 큐는 현재 운전자에 대한 기존 `needsAcknowledgement`를 그대로 적용한 목록이다. 열기만으로 읽지 않으며 상세의 명시적 읽음만 기록한다. 상태·작성자·수정 버전 의미를 합치지 않았다.

| 없앤 요소 | 실제 새 경로 |
| --- | --- |
| 홈 nav_HOME·전역 nav_ISSUES/nav_VEHICLE·menu | 홈 비대화형 CARITDA+settings_help, 하위 back/nav_HOME 최대2개 |
| 메뉴의 업무 목적지 복제 | 기록은 홈 all_issues, 차량은 홈 vehicle_health |
| 내 작성 메뉴 | 기록 filter_MINE |
| 홈의 지난 SOC 사건/정상 metric | 유효한 현재 home_vehicle_attention 또는 차량 건강 상세 |
| 조건 없는 테마 순환 | 설정의 theme_light/theme_dark/theme_system 명시 선택 |
| 미확인 인계→ALL | 홈 handover→전용 큐→상세→acknowledge→이전 큐 |

삭제된 탐색 태그의 숨은 더미 View는 만들지 않았다. 기존 action tag와 저장/AI/SDK 서비스 의미를 보존한다. 설정에서 하위 화면을 열고 이전을 눌렀을 때 홈으로 건너뛰던 옛 MENU overlay 분기를 새 실제 history에 맞췄다. 홈·이전은 초안 보관 실패 시 이동하지 않는다.

### 단계1 검증

Debug/Validation/ValidationAndroidTest/Lint/Unit task exit0. 최종2m47s, Lint0오류/56경고, Unit NO-SOURCE. 초기 새 drawable의 colors 이름 충돌은 수정 후 빌드했다. 새29+관련 회귀226=255 assertion. 회귀는 STT40, issue AI56+cold4, portable26+UI4, DB19, 통합33+실제 SDK7, Gleo37이다. 단발 실제 Vehicle20종12수신/8미지원·Navi 경로 상태·Fused valid·Pleos TTS 완료는 신규 모델 성공과 구분한다. Gemini 신규/Cloudflare 배포0이다.

전후 기존330DB(일반42/validation288)의 원본11,264행(1345+9919)·스키마·user_version·무결성/FK를 비교해 보존했다. 일반 문제24/이벤트97/운전자10/초안0 및 files/prefs16개 전부 동일, 일반 APK는 아직 교체하지 않았다. validation 기존9919행 불변, 고유 fixture21DB 추가·기존 SDK heartbeat/시험 포인터3개만 정상 갱신. UI 시험의 테마·차량 표시·도움 상태도 고유 fixture prefs를 사용한다. 일반 UI 자동 조작·기본 prepare·AVD/DB reset은0이다.

### 다음 단계와 실제 제한

다음에는 기존22개 화면+AI2모드를 전수 표로 대조하고 차량 그룹/점진 상세·기록/AI·위치·설정/도움의 본문을 정리한다. 이어320/600/840/1024dp·가로/세로·font1/1.3/1.5/2·두 테마의 native layout, 실제 SDK SOC 홈 연결, 화면 캡처, 일반 APK 교체와 전체 데이터 재비교를 수행한다. 전체 작업 완료가 아니다.

승인 HTML의 CSS/레이아웃 소스를 실제로 읽었다. 로컬 Chrome/Edge의 headless 기준 이미지 생성은 모두0x80000003으로 종료돼 아직 기준 PNG가 없다. 보안 해제·sandbox 우회는 하지 않았다. 이 제한을 Android 캡처나 시안 대조 완료로 바꾸지 않는다. Android 화면 캡처는 다음 검증 단계다. 고령자/초보자의 실제 이해도·만족도·실차/물리 접근성은 자동 검사와 별도다.

게시: 검증된 명시 파일만 최신 main을 부모로 **GitHub 연결 도구를 통한 게시**. 기대 HEAD/force=false·전체 다른 blob/Aside SHA를 대조한다. 자기 SHA를 넣기 위한 반복 커밋은 하지 않는다.

## 단계 2 — 차량·기록·AI·설정 본문 (2026-11-11 21:35 +09:00)

차량 기본 화면을 확인 항목→주요 수치→요약→충전량 자동 안내/데이터 분석→기타 그룹 순서로 구성했다. 문·등화·차량 안내·타이어 기준의 4그룹으로 묶고 원시값/지원/시각/출처는 펼친 상세에 둔다. 문 정수를 열림으로 해석하거나 저압 기준을 실압으로 쓰지 않는다. 정상 수치는 홈에 올리지 않는다. 실제 수신·오래됨·미지원·권한·실패는 차량 정보 품질이며 고장 여부와 별개다.

작성은 음성/편집 영역과 명시적 저장 영역, 상세는 원문→상태/작성자→읽음 또는 메모→AI→위치/스냅샷→수동 상태/관리/이력 순서다. 위치 기본 문구는 “위치가 기록에 저장됐어요.”이며 저장 시각/마지막 확인 위치 주의만 남긴다. 좌표/출처/측정·수신 시각과 연결 이력은 접힌 상세에서 확인한다. 운전자 빠른 전환과 설정의 추가/이름 변경/보관을 분리했다. 도움말은 세 역할과 6개 접힌 주제다. 목록의 검색/네 필터·초안/스크롤과 원본 이벤트를 유지했다.

AI는 B 요청/결과 영역과 이전/홈을 사용하며 실제 이용 동의/전송 항목은 그대로 표시한다. 차량 참고는 로컬 상세로 접고 category/situation 외 전송하지 않는다. 새 결과/기존 캐시의 출처와 전체 설명 TTS를 유지한다. 화면 진입/데이터 분석 이동만으로 모델을 실행하던 자동 분기를 제거하고 명시적 버튼만 사용한다. 계정/사용량/철회/로그아웃 기능은 계정·이용 정보에 남겼다. 로그아웃의 기존 AI 캐시 삭제 의미를 정확히 설명한다.

최대 읽기 폭1160dp의 CaritdaPage를 실제 OS 앱 창 안에 적용했다. 입력 중 IME 높이 변화로 편집 필드를 재생성하지 않는다. ACTION 초안 보관 실패 시 옛 초안으로 메모를 저장하던 경로를 차단하고 현재 필드/화면을 유지했다. 실패 주입 후 재시도로 한 번만 commit되는 것을 검증했다. DB/인증/암호화/Worker/정책/SDK 계약은 바꾸지 않았다.

### 단계2 실제 검사

Debug/Validation/AndroidTest/Lint exit0(최종2m33s), Unit NO-SOURCE, Lint0오류/57경고(추가1개는 XML에서 사용하지 않는 CaritdaPage의 ViewConstructor 안내). AndroidTest 추가 빌드1m10s exit0. 신규 BT20+cold3와 관련 경로120=143 assertion, 단계1 홈29 재검사도 통과했다. 관련120은 위치6+cold2/내비UI7/통합35+cold4/실제 SDK 문맥4/DriveCheck24/SOC7+cold2/STT초안21/실제 STT 준비·취소6/실제 TTS2이다. 실제 SDK 값은 숫자 표시 형식까지 대조했다. 초기 스냅샷 fixture의 FUEL 미지원 누락과 숫자0.0/화면0 비교 기대를 수정 후 재검사했으며 실패 결과를 숨기지 않는다. Mock AI를 신규 Gemini 성공이라고 부르지 않는다.

전후330개 기존 DB의11,264 원본 행/스키마/버전/무결성/FK 보존. 일반24문제/97이벤트/10운전자/초안0과16 private파일 모두 동일하며 아직 일반 APK 교체 전이다. validation 기존9919행 불변, 고유 fixture87DB 추가, 포인터/SDK 메타6개 갱신. 기존 validation DB도 초기화하지 않았다. 다음 단계는22+2 전체 매핑,1600개 조합의 실제 View 배치,시안과 캡처 대조,실제 SDK SOC 홈,일반 APK 교체/보존이다. 전체 UX 완료 판정은 다음 검증 뒤에 한다.
