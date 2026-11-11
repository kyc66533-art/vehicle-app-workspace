# CARITDA B안 라이트 · 실제 Android UI 개편

## 단계 1 — 기준·홈·탐색 (2026-11-11 Asia/Seoul)

기준 main `c424ec25cfe5599faf0958c96b9814d7fe3bd9d1`, tree `94e2e389fb44495f4d8e3acfd14a529377dd1ad1`. 로컬221개 파일 일치, 원격 전용 Aside26개 보존. 승인된 REDESIGN_20261111 AUDIT/DESIGN/CODEX/MOBILE/PROTOTYPE와 보조 README/SOURCES/VALIDATION8개를 blob SHA 대조 후 읽었다. 연구 파일은 수정하지 않았다. 연구의 옛 P0/P1 한정 제안보다 이번 사용자의 전체 화면 구현 지시를 우선한다.

| 사용자 단계 | 현재 상태 | 근거 |
| --- | --- | --- |
| 1 최신 소스·안전·설계 대조 | 완료 | 원격/로컬 비교·새 독립 백업·연구 대조 |
| 2 상단·홈·확인 필요 연결 | 완료(코드/validation) | B 공통 토큰, 새 헤더, 인계 큐, 조건별 홈·검색·초안·이전29개 |
| 3 차량·기록·인계·AI 전체 간소화 | 미완료·다음 구현 | 이전 본문 일부가 남아 있음 |
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
