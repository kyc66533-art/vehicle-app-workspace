# Codex 구현 지시서: CARITDA 재설계

기준: 2026-11-11 main 80f76b1bdac9044f649eba01e727ef4cee084b48. **사용자가 A/B 방향을 확인하기 전에는 실행하지 않는다.** 이번 Aside 작업은 앱을 수정하지 않았다. 권고는 B의 시각 시스템+최소 탐색이다.

## 우선순위와 변경 범위

| 단계 | 목적 | 수정 후보 | 완료 조건 |
|---|---|---|---|
| P0 읽기/기준고정 | 최신 main·Codex작업 비교, 데이터/fixture안전/화면태그 매핑 | 새 구현계획/시험표, 기존코드 읽기 | 실제 HEAD/target SHA, 현재 기능·원본수·백업·미검증 구분. 일반 UI자동조작0 |
| P1 탐색/홈 | 4전역탭→홈상단1/하위2, 우선행동홈 | MainActivity navigationBar/pageTitle/moreMenu/home/goHome/openScreen/onBackPressed, 필요한 ui header | 초안/필터/스크롤/운전자 확정/안전 회귀, 이동중쓰기0. SDK·DB·AI계약 수정없음 |
| P2 기록/인계 위계 | 목록·작성·상세·운전자·도움 | MainActivity issues/recordForm/detail/driver*/help*, CaritdaDesign | 기존버튼의미/ID·작성자·수동상태/저장신뢰성 유지. 76dp/큰글자/두테마 |
| P3 건강/AI 표현 | 건강정보 점진공개/정확한카피·전송위계 | vehicleScreen/metricGroup/socNotices/analyticsScreen, PortableAiActivity.render, VehicleUi | 원본·최근시도·이전값·출처 구분. 모델0회로 UI검사, 원문/사본/결과구분 |
| 별도결함패치 | captureDraft실패/기간전달/SOC자동요청 등 | 해당 함수만 + 고유fixture | 재현/실패주입→최소수정→동일검사. 시각PR과 분리 |
| 후속 모바일 | 독립설치와 기기간공유 | 별도 :mobile 또는 승인된platformflavor | 자동차 APK/DB/인증 정상동작 유지, 실폰근거 전 완성주장금지 |

각 단계는 작은 diff/커밋으로 분리하고 앞 단계 검증 실패시 다음 단계로 진행하지 않는다. Compose 전환·SDK 업그레이드·기능추가·DB이전·서버동기화를 시각개편과 함께 하지 않는다.

## 파일 매핑

- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
  - M369-427 상단/메뉴/제목, M518-676 홈·목록·운전자·초안/ACTION.
  - M689-895 건강·SOC·분석·관측/기술상세, M896-991 안내/도움/폼.
  - M992-1174 저장후AI·상세·상담·상태·이동·현재안전검사.
- 동일 경로 PortableAiActivity.kt: render136-219 표현. requestExplanation/sessionTask/lifecycle은 계약보존. 명시요청 변경은 별도패치.
- ui/CaritdaDesign.kt / ui/VehicleUi.kt: 시각토큰/컴포넌트·읽기전용표현. 실제 source/미지원/이전값/시각의 의미 불변.
- integration/SpeechControls.kt: 상태·입력·취소 배치만. SpeechInput/Backend/Session/ExplanationPlayback 프로토콜 변경금지.
- 기존 androidTest DesignChecks/Nav/Issue/IntegratedFlow 등: 최신 runner를 먼저 읽고 새 안전fixture와 태그매핑 계획을 세운 뒤 수정. 일반앱 대상 계측 금지.
- docs/CODEX_PROGRESS.md 및 단계별 acceptance는 **후속 구현자가** 그때 실제 결과로 갱신. 이번 Aside 게시에는 넣지 않음.

## 보존·변경 태그 규칙

새 문구로 바꿔도 new_issue/save_issue/save_issue_ai/record_search/filter_*/issue_{id}/resume_draft/discard_draft/driver_*/start_driving/acknowledge/edit_issue/delete_issue/add_note/change_status/recurrence/issue_ai_explain/portable_* 등 행동 태그와 domain contract는 유지한다. 상세는 AUDIT의 전수목록 참고.

반대로 제거할 nav_ISSUES/nav_VEHICLE/현행menu/my_issues는 기존 테스트를 그대로 만족시키기 위한 숨은 더미 View를 만들지 않는다. 다음 매핑을 acceptance에 명시한다.
- nav_HOME: 홈에서는 없음, 하위에서 앱홈복귀에만 존재.
- nav_ISSUES: 홈 전체기록 행동(all_issues)로 대체. 헤더전역탭 없음 검사.
- nav_VEHICLE: 홈 차량상태 행동의 명확한 새태그/기존안정태그로 대체.
- menu: 홈 settings_help(새역할)로 대체. 기존5항목 메뉴 기대검사를 새 IA 기준으로 수정.
- my_issues: filter_MINE로 대체. 원작성자/운전자미확정 검사 보존.
- 기존 ALL/MANAGED/COMPLETED/MINE 및 status enum 4개 의미를 변경하지 않음.

## 회귀 검사와 성공 조건

1. **보존**: 변경전 원본 DB/files/prefs/APK를 승인된 비공개위치에 백업. 현재수/스키마/전체원본행/관계/무결성을 비교. 과거24문제/23건/298assertion을 고정기댓값으로 쓰지 않음. 운영/기존validation DB초기화0. 신규시험은 고유DB fixture만.
2. **탐색**: 홈상단1, 하위최대2; 목록검색/필터/위치→상세/AI/도움→원위치. 홈복귀는 앱HOME intent아님. 빈DB/조건0건구분. 초안보존실패면 이동0.
3. **기록**: 저장commit/readback 전 성공표시0, 빠른중복탭 중복기록0, 실패시 입력/작성자유지. 수정없음/취소/충돌/논리삭제/발견시각/이력/재발/인계/본인ID검사.
4. **운전자**: 후보선택과명시운전시작분리, 콜드스타트확인, dirty초안타인재귀속0. 변경/보관으로과거작성자불변.
5. **안전**: UNKNOWN/RESTRICTED/ALLOWED와화면전환중제한. 현재gate를모든행동에서재검사. 제한/onStop시 STT/TTS/Navi/위치/observer중단·generation차단. OS차단가능성도실기확인.
6. **차량**: 최신성/실패/미지원/이전유효값·단위·시간대·출처, 차종수동배치. SDK진입당단발/수동재시도·명시켠SOC구독만, redraw/회전으로중복요청0. 타이어기준=실압아님, 등화ON=고장아님.
7. **AI**: 저장ID/revision에연결, situation3~800 전송사본검증(원문1000 무단절삭0), category/situation만. 로그인/동의/등록/할당량/취소/UNKNOWN/pending회수/계정분리/기존결과·신규결과출처. 단순열람·듣기는새모델0. SOC별도계약과증상계약혼합0.
8. **음성**: final+ended 이후자동반영, 취소·늦은결과·재입력·recreate회귀. AI TTS전체분할/중단, 원문600자 제한은 정확히표기. Gleo세Action/trust/callback유지, wakeword실승인성공주장금지.
9. **레이아웃**: 실제앱창320/600/840/1024dp, portrait/landscape, height축소/IME, fontScale1/1.3/1.5/2, 두테마. 가로넘침/핵심버튼소실/덮침/ellipsis0. 76dp하한·포커스순서·실제사용색대비4.5:1. 차량거리물리가독성은 별도실차검사.
10. **빌드**: 현재 Gradle task와 cached환경을확인해 assembleDebug/Validation/ValidationAndroidTest, lintDebug/testDebugUnitTest, 필요Release를 실행. NO-SOURCE를검사통과건수로세지않음. 실패/환경제한은그대로보고, 미검증앱소스게시0.
11. **시험안전**: MvpScenarioInstrumentation 인자없는prepare 금지, designOnly가안전한earlybranch인지새로확인. 승인된고유fixture조기분기만. 운영UI자동화/키·DB·개인캡처공개/실모델호출/서버배포 금지.
12. **사용자평가**: 초보·고령자형성과제는 별도로 수행. 자동렌더/계측으로 만족도향상·실차인증을 주장하지않음.

## 복사 가능한 최종 개발 프롬프트

아래 프롬프트는 사용자가 **B안(또는 선택안)을 승인한 뒤** Codex CLI에 전달한다.

~~~text
너는 CARITDA의 기존 기능·데이터를 보존하며 UI를 개선하는 Android Automotive/Kotlin 엔지니어다.

먼저 원격 main의 실제 최신 HEAD와 AGENTS.md, CODEX_PROGRESS.md, STT_AI_HANDOVER_ACCEPTANCE.md를 읽고 아래 설계의 기준 80f76b1 이후 차이를 확인하라. 다른 작업자가 변경한 파일을 덮어쓰지 마라. 최신 사용자 승인은 기존 '홈 유지/전역탭 유지' 설계 가정보다 우선하지만 안전·기록·인증 계약은 우선 보존한다.

필수 설계자료:
docs/research/CARITDA_REDESIGN_20261111_AUDIT.md
docs/research/CARITDA_REDESIGN_20261111_DESIGN.md
docs/research/CARITDA_REDESIGN_20261111_MOBILE.md
docs/research/CARITDA_REDESIGN_20261111_SOURCES.md
docs/research/CARITDA_REDESIGN_20261111_CODEX.md
docs/research/CARITDA_REDESIGN_20261111_PROTOTYPE.html
HTML은 가상 시안이며 앱 기능/수치/안전검증 근거가 아니다.

승인된 방향은 B의 절제된 브랜드 시스템과 최소 탐색이다. 이번 첫 단계는 P0+P1만 시행하라. 광범위한 기능수정·Compose전환·SDK업그레이드·DB/Manifest/Gradle/인증/서버/할당량 변경은 하지 마라. 모바일실행·원격공유·새Gleo메뉴도 추가하지 마라.

MainActivity의 navigationBar/home/moreMenu와 관련 헤더표현만 작은 diff로 바꿔라. 홈상단은 비대화형브랜드+설정·도움말1개, 하위상단은 이전/제목/홈 최대2개다. 전역기록/차량탭은 없앤다. 홈은 미확인인계→점검할문제→실제근거변화→새기록 순의 우선카드, 문제남기기, 일부차량상태, 전체기록 경로다. 같은행동을 우선카드와 별도CTA에 이중노출하지 마라. AI는 저장된문제에 붙이고 전체건강정상점수/자동진단을 만들지 마라.

인계큐는 기존 needsAcknowledgement 의미를 그대로 쓰는 읽기전용화면/필터로 설계하고, 열기만으로읽음처리하지마라. 큐추가가첫단계범위를넘으면 미확인기록으로정확히이동하는최소경로를먼저구현하고나머지는미완료로보고하라.

goHome/openScreen/onBackPressed의 초안/원작성자/목록필터·검색·위치 복원계약을 보존하라. captureDraft 실패면 이동을막고입력을유지하라. CarSafetyGate UNKNOWN/RESTRICTED를허용으로바꾸지말고모든행동에서현재상태를확인하라. 실제운전시작확정, 4수동상태, 수정/논리삭제/재발/점검/읽음을합치지마라.

CaritdaDesign/VehicleUi를재사용하되76dp터치하한, 충분한대비, 줄바꿈, 큰글자한열을유지하라. 가용창폭/높이/fontScale/IME/insets로배치하고OS상태표시줄·DrivingView·공조·GNB를침범하지마라. 기존태그는행동의미별로보존하되제거된전역탭에숨은더미버튼을만들지말고테스트매핑을명시하라.

실제일반/기존validation데이터를초기화하지마라. 백업·현재전체원본비교·정확한validation패키지guard를선행하라. 운영UI자동화와인자없는instrumentation기본prepare는금지한다. 최신runner를읽고고유fixture를쓰는조기분기만사용하라. designOnly도prepare뒤라면안전하지않다.

SDK2.3.2/STT·TTS3.3.1/Navi3.0.5/Fused1.1.3와Maven.security필터, 진입당조회/가시SOC, 원관측·snapshot연결, Firebase/Keystore/동의/BUDGET/쿼터/pending UNKNOWN/계정별캐시와Gleo신뢰검사를보존하라. 실모델호출·운영설정변경·서버배포는하지마라.

기준커밋/변경파일·태그매핑/실제시험/미검증/원본보존을보고하라. 작은단계의빌드·레이아웃·안전·탐색회귀가통과한뒤에만 다음P2진행여부를판단하라. ACTION초안실패반환·SOC진입자동요청·기간범위불일치는별도재현/최소패치로분리하라.

게시전main을다시확인하고현재트리에서이번검증된변경만더하라. 강제덮어쓰기/포괄스테이징/키·개인DB게시금지. 기존연구를수정하지말고후속구현보고서를추가하라. 완료시원격커밋·부모·변경파일과실제검증근거를제시하라.
~~~
