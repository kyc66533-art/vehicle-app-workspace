# CARITDA 현재 UI·UX 전수 감사

2026-11-11 · main [80f76b1bdac9044f649eba01e727ef4cee084b48](https://github.com/kyc66533-art/vehicle-app-workspace/commit/80f76b1bdac9044f649eba01e727ef4cee084b48).

코드·검증 보고서 정적 감사이며 실제 앱 UI를 조작하지 않았다. MainActivity의 Screen enum **22개**와 PortableAiActivity의 **증상/SOC 두 모드**, 공용 다이얼로그와 컴포넌트를 조사했다. '현재'는 이 커밋의 코드다. 과거 문서의 시험 결과는 당시 보고로만 인용한다.

경로 약어: M = NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt, P = 같은 경로의 PortableAiActivity.kt, U = ui/CaritdaDesign.kt. 아래 줄 번호는 기준 커밋에 고정되며 최신 main에서 이동할 수 있다.

## 1. 핵심 진단

1. **탐색 목적지가 중복된다.** 넓은 화면은 홈/기록/차량/메뉴, 좁은 화면은 홈/메뉴+메뉴 내부 기록/차량. 홈 '기록 확인'과 상단 기록은 같은 전체 목록으로 간다. 메뉴의 내 작성/차량 조회까지 분산돼 메뉴를 알아야 기록을 찾는 구조다.
2. **우선순위가 운전자 행동보다 차량 수치에 있다.** 홈은 최근 차량 정보부터 렌더하고 미확인/점검 예정은 아래에 나온다. 미확인 카드 자체의 전용 행동 없이 '기록 확인'은 ALL을 연다.
3. **장문 면책·정책·기술 정보가 반복되며 일부는 오래됐다.** 범위가 다른 'AI 미연결' 문구, 자동 조회를 부정하는 도움말, 구형 버튼 이름이 남아 있다. 반복 안내를 무조건 삭제하지 말고 출처·신선도·동의·안전은 유지해야 한다.
4. **AI 모드가 하나의 서비스처럼 이해되지 않는다.** 실제 증상 AI와 실제 SOC AI, 전송 없는 JSON 동의 미리보기가 병존한다. 사용자에게 '동의했는데 실행 안 됨'으로 보일 가능성이 있다.
5. **기본 디자인 기반은 이미 있다.** 라이트/다크·76dp 타깃·아이콘·줄바꿈·카드가 구현돼 있어 '아무 디자인도 없다'는 진단은 부정확하다. 문제는 동등한 테두리 버튼 다수·위계/상태별 행동·브랜드의 일관된 정보 흐름이다.
6. **작성 신뢰성·안전 기반은 유지 대상이다.** 새 저장→AI 연결, 고정 홈, 초안/검색/필터/스크롤, 사용자 ID와 수동 상태를 디자인 때문에 되돌리면 안 된다.

## 2. 전수 감사표

| 화면/코드 | 현재 주요 조작 → 대상 | 중복 평가 | 설명·고령자 문제 | 권고 | 개선 후 흐름 |
|---|---|---|---|---|---|
| 공통 탐색 M369-388,427-491 | 홈→HOME, 기록→ISSUES, 차량→VEHICLE, 메뉴→MENU; 별도 이전/메뉴닫기/도움닫기 | 현재 화면·본문 진입과 반복. 넓이마다 노출도 달라짐 | 버튼이 같은 강도로 보이며 홈/이전/닫기 의미 학습 필요 | 상단 홈에서는 설정1, 하위 이전/홈2. 기능 탭 제거, 현재 맥락은 제목 | 홈에서 핵심 영역, 하위는 원위치/홈만 |
| HOME M518-539 | 기록 확인→ALL, 증상 남기기→RECORD/DRIVERS, 초안 계속/취소, 안내 이어보기 | 기록 확인=상단 기록; 초안·안내는 맥락상 필요 | 수치 먼저, 미확인 행동이 전체목록으로 희석. 자동진단 아님 안내 반복 | 미확인/다음 행동을 최상단. 전체기록 단일 경로. 초안 보존 | 인계→상세→명시확인, 또는 문제작성 |
| ISSUES M540-576 | new_issue, 검색, ALL/MANAGED/COMPLETED/MINE, 초기화, 빈결과 전체보기, issue 카드→DETAIL, 전체상담→SUMMARY | 홈의 작성은 다른 맥락이라 유지. 초기화와 빈결과 전체보기는 상당부분 중복 | 검색범위/검색어없음/전체수/결과수/정렬 안내 동시노출. 좁은창 필터가 긴 세로버튼 | 검색1+필터wrap+결과수. 조건 있을 때만 초기화. 전체상담 보조영역 | 찾기→상세→원래 조건/위치 |
| DRIVERS M577-599 | 후보 카드, 추가→DRIVER_EDIT, 관리→DRIVER_MANAGE, 운전시작 확정→HOME/대기목록, 취소→HOME | 후보와 확정은 중복 아님 | '운전 시작'이 차 시동으로 오인될 수 있음. 낮은창 제어 위치 변경 | 기록 운전자 설명 한줄, 선택/확정 유지. 주요 확정1 | 이름 후보→이 이름으로 기록 시작 |
| DRIVER_MANAGE M600-612 | 이름 수정→DRIVER_EDIT, 보관 확인→선택 제외 | 설정과 빠른운전자변경은 목적 다름 | 수정/보관이 각 사람마다 동급. 보관=삭제 오인 | 이름관리 하위에 유지, 보관은 보조/확인 | 설정→이름관리→수정/보관 |
| DRIVER_EDIT M613-621 | 이름입력, 이름저장→DRIVERS | 추가/수정 동일 폼 재사용 적절 | 1~12자 안내와 EditText48자 허용의 UI/도메인 차이 | 표시·실제 허용 길이 일치, 과거 작성자 보존 안내1회 | 저장 후 별도 기록운전자 확정 |
| RECORD M936-991,999-1044 | 4종류, 상황, 음성/취소, 저장/저장AI, 선택정보(차량연결·발견시각·메모), 외부음성후보 채택, 초안취소 | 저장과 저장AI는 결과가 다르므로 유지. 음성후보와 음성입력은 출처 구분 필요 | 여러 설명·긴 폼. 저장AI가 즉시 외부전송으로 오인될 위험 | 필수2+선택접기, 저장시점 명시. 원문/전송사본 구분 | commit/readback→상세 또는 전송확인 |
| ACTION M653-676 | 점검메모/재발 입력, save_action→DETAIL, 초안취소 | 기록폼과 목적 다름, 합쳐 의미 삭제 금지 | '이 차량 앱에 저장' 장문. captureDraft 반환 무시 가능 | 문구는 저장. 실패처리는 별도 신뢰성 패치로 검증 | 점검/재발→저장→같은상세 |
| DETAIL M1045-1098 | AI, 원문듣기, 위치첨부, snapshot연결/상세, 본인수정/삭제, 타인읽음, 점검메모, 상태, 재발, 상담, 조건밖 전체보기 | 원문듣기와 AI듣기는 내용 다름. 목록/상담 중복 진입은 맥락 필요 | 장문 이력+다수 동급 행동. 읽음/점검/해결 혼동 | 미확인/점검 주행동, AI블록, 기록관리에 수정/삭제, 이력접기 | 읽음·점검·AI 중 해당 과제 선택 |
| SUMMARY M1099-1117 | 전체/선택 요약, 점검목적지 좌표입력→Navi, 각요약듣기, 질문복사, linked차량상세 | 기록상세 AI/듣기와 구분 필요 | 좌표입력은 초보자 부담. 복사=전송 아님 | 상담용 하위화면 유지. 길안내 기존기능은 보조. 새 장소검색 기능은 별도개발 | 기록→상담준비→질문복사/명시 길안내 |
| VEHICLE M689-739 | 수동조회, 건강정보펼침, 조회기록, SOC안내, 분석, 미지원펼침, 수신상세; validation 전용 PoC/Auth | 메뉴 차량조회기록과 반복. 수동조회와 진입자동조회는 기능 다름 | 여러 수치·출처·시각 설명. 건강17신호가 한번에 늘어남 | 일부건강정보+근거변화, 상세별 점진공개. 기존 수동조회는 명시 행동 유지 | 상태→필요한 안내/분석/원본 |
| SOC_NOTICES M741-789 | 기준1~95, 켜기/끄기, 원관측재정리, 근거/이전사건펼침, 더보기 | 조회/조건해제/문제상태는 같은 기능 아님 | 여러 연속 면책·watch상태·AI미연결. 이전사건과 현재값 혼동 위험 | 현재값/사용자기준/안내 하나, 이전은 접기. 재정리는 기술상세. AI문구는 이 모드만 한정 | 사용자 기준 설정→현재안내→과거근거 |
| ANALYTICS M790-824 | SOC/거리, 하루/7일/전체2000, 차트/통계/인사이트, 실제AI→P, JSON미리보기→AI_PREVIEW, 상세/페이지 | 실제AI와 전송없는 동의가 병존 | 두 AI 경로, 기간 넘김 불일치 가능. 숫자를 진단처럼 해석할 위험 | 실제 AI는 맥락행동 하나. 구형 미리보기는 일반흐름 제외/검증도구로 분리. 기간 계약을 먼저 확인 | 기간→규칙근거→명시 AI 요청 |
| AI_PREVIEW M825-832 | JSON ai_payload_preview, ai_consent(전송없음), ai_cancel | 실제 AI와 의도 중복이나 결과 다름 | '전송 동의' 눌러도 모델 실행 아님 | 사용자 경로에서 제거하되 기존 계약·연구·검사 삭제 안 함 | 실제전송 사본/동의는 P에서 일관되게 |
| SNAPSHOTS M845-860 | 숨긴카드 보기, 열기→SNAPSHOT_DETAIL, 숨김/다시표시, 최근이력 | 메뉴/차량 여러 입구는 필요성 낮음 | 숨김=문제삭제/해결 오인, 이력 반복 | 건강 하위관측 단일입구. 숨김 의미 유지 | 차량→저장된차량정보→조회시점 |
| SNAPSHOT_DETAIL M861-870 | 해당 snapshot값, 기술정보펼침, 기기시간대 | 문제상세의 linked값과 같은근거지만 상세진입 정당 | raw/시간대 설명이 많고 증상시점으로 오인 | 값·조회시각·출처 우선, 원본은 펼침 | 문제연결근거→불변조회정보 |
| HISTORY M833-844,871-879 | 최근3/전체, 신규/과거20개, snapshot 행→상세 | 최근미리보기/전체는 깊이차이 정당 | 같은값반복 수신을 변화로 오해 | 최초저장/변경/동일재확인 라벨 유지, 페이지 위치복귀 | 관측카드→전체이력→당시값 |
| LOAD_DETAIL M888-895 | 최근100 원관측 raw/permission/capability/session등, 공통이전 | 개발정보 반복은 일반화면에서 제거 | SDK 원문이 초보자에게 무행동 정보 | 도움/오류상세에서만, 오류요약에 다음행동 | 확인실패→짧은복구안내→선택원문 |
| VEHICLE_SETTINGS M499-514 | UNKNOWN/EV/ICE/HEV/PHEV/OTHER 배치, 기기/KST시각 | 메뉴→설정 위치는 적절 | 차종자동인증/OS시간변경 오인 | 설정으로 이동, 배치선택/표시시각 명시 | 설정→표시만 변경, 원본보존 |
| WELCOME M896-909 | 시작/나중에 모두DRIVERS, 재보기닫기 | 시작/건너뛰기 의미는 다름 | '건너뛰기' 후에도 운전자단계 필요. 정보밀도 | 한화면 핵심3역할, 안전/계정동의와 분리 | 안내→기존운전자확인, 재보기는원화면 |
| HELP M918-922,492-498 | 안내재보기, 6주제, 테마순환 | 설정이 도움말 안에 숨어있음 | 테마가 누를때마다순환, 현재/다음값 예측 어려움 | 설정·도움말 묶음, 테마 명시선택. 주제유지 | 설정→도움말/화면선택 |
| HELP_TOPIC M923-935 | 6주제 설명, 목차/닫기 | 비슷한 안내 반복 | 현 버튼명/자동수집 범위와 불일치 | 최신 실제흐름에 맞춰 전면문구감사 | 현재 작업의 짧은 설명→원위치 |
| MENU M409-426 | 좁은창기록/차량, 운전자변경, 내작성, 조회기록, 도움·테마, 차량표시·시각 | 첫3기록경로/차량경로 분산 | 기능 이름이 혼합, 메뉴가 또다른허브 | 설정·도움말로 재정의. 내작성은 필터/조회는건강 하위 | 설정은 선호/도움, 업무는홈영역 |
| P 증상모드 P136-219,220-237 | 뒤로, 전송사본, 차량참고상세, 로그인, 전송요약, 동의등록/취소, 생성, 결과듣기/중단, usage, 저장결과, 철회, 로그아웃 | 전송범위/차량면책 반복. 보기/새생성/듣기는 유지 | 계정·동의·정책·결과가 긴 단일열. 캐시/새결과 구분 어려움 | 원문/사본/결과 출처 분리, 최초필수동의 유지, 기술정보 접기 | 저장된문제→사본확인→명시생성→선택듣기 |
| P SOC모드 P186,216-218,254-258 | 최근하루SOC, 등록/동의, 분석생성, 결과/usage/철회/로그아웃 | 앞화면기간과고정하루가 다르고 자동요청 경로 | 에뮬레이터 출처 상수, 분석범위 오인, 진입=요청 가능 | scope/출처 사실대로, 신규 외부요청 별도 버튼. pending회수·saved열람 구분 보존 | 근거범위확인→동의→명시생성 |

## 3. 삭제·통합·이동할 조작의 정확한 판단

| 현재 요소 | 결정 | 이유/대체 |
|---|---|---|
| HOME의 nav_HOME | 삭제(현재화면중복) | 워드마크는 비대화형 |
| 전역 nav_ISSUES/nav_VEHICLE | 상단에서 제거 | 홈의 전체기록/건강 1탭 경로로 대체. 삭제된 태그를 숨은 더미버튼으로 남기지 않음 |
| menu | 설정·도움말로 의미변경 | 업무목적지를 다시 나열하는 허브가 아니라 선호/계정/도움 |
| all_issues | 전체기록 경로로 유지/이름수정 | 상단 기록과 같은화면내 중복 제거 |
| 홈 미확인 카드→ALL | 미확인큐 경로로 수정 | 전체목록과 다른 목적. 인계 전용 필터/큐는 읽기전용 presentation부터 |
| 내작성 메뉴 my_issues | 메뉴에서 이동 | 기록 MINE 필터로 통합. 운전자 미확정 규칙 유지 |
| 메뉴 auto_snapshots | 차량건강 하위로 이동 | 기록/차량데이터 구분 |
| 목록 reset_conditions + show_all_records | 한 초기화 행동으로 통합 | ALL 필터선택은 같은화면 맥락으로 유지, 검색/필터 모두 초기화 명시 |
| 목록 new_issue와 홈 new_issue | 유지 | 목록에서 이어서 쓰기는 정당한 빠른접근 |
| 상세 AI와 저장AI | 유지 | 기존문제 분석과 작성직후 연결이라는 다른맥락 |
| 저장/저장AI | 유지 | 둘다저장하되 목적지 다름. AI외부요청은 다음화면에서 명시 |
| 읽음/점검메모/상태/재발 | 유지·위계정리 | 업무의미가 다르므로 하나의 '완료'로 합치지 않음 |
| 구형 analysis_ai→AI_PREVIEW | 일반경로 제외 제안 | 동의만하고 전송안하는 실험UI는 실제AI와 혼동. 테스트/근거까지 삭제하지 않음 |
| 수동 read_vehicle | 유지 | 단발재시도는 사용자 가치 있음. 새 자동polling 금지 |
| Gleo 독립 대형메뉴 | 신설하지 않음 | 미래 버튼없는호출, 승인/자연어왕복 미검증 |

## 4. 문구 감사와 대체안

| 위치/현재 문제 | 기본 화면 문구 제안 | 상세에 유지할 것 |
|---|---|---|
| M532 직접남긴/자동진단아님 반복 | 운전자가 남긴 기록 | 자동진단 아님 의미는 첫안내·건강·AI에 맥락적으로 유지 |
| M547,561,563 검색범위/검색어없음/정렬 안내 | 검색결과 N건, 조건있을때 초기화 | 검색범위는 도움/필드설명 한줄 |
| M763-765 SOC 면책 연속 | 사용자 기준 미만이에요. 충전량을 확인해 주세요. | 규격 아님/관측공백/해제는수리완료아님, 근거·시각 |
| M774 AI 아직연결안됨 | 이 충전량 안내의 AI 설명은 제공하지 않아요. (실제모드별) | 증상AI와실제SOC분석 지원을 부정하지않음 |
| M925-929 도움말 버튼명·자동수집 설명 | 문제 남기기→저장→선택AI. 앱을 열 때 지원값을 확인해요. 켠 안내만 허용중 확인해요. | 가시수명주기/중단/미지원 범위 |
| P148,160-164,187-190 전송범위 반복 | 이번에 보낼 내용 / 문제 종류·상황만 보냅니다. | 최초동의의 수집항목·목적·보존·철회, SOC모드별별도범위 |
| P118-127,149-155 차량면책 반복 | 기기에서만 보는 차량 참고 | 조회시점·출처·최신실패/오래됨, 전송제외 |
| P186 에뮬레이터 출처 고정 | 실제 observation provenance로 결정 | 실차라고도임의변경하지않음 |
| 저장 실패 | 저장하지 못했어요. 입력은 유지됩니다. 다시 시도해 주세요. | 확인된 실제원인·복구행동, 원문오류는상세 |
| AI 실패 | 기록은 저장되어 있어요. 연결 후 다시 시도해 주세요. | 보안/인증/할당량별복구. 무조건네트워크탓으로하지않음 |
| 전송값 길이 3~800 vs 기록1000 | AI에 보낼 상황을 3~800자로 확인해 주세요. | IssueExplanation.kt:13-21 계약. 원문을 몰래 자르거나변경하지않음 |

## 5. 공통 컴포넌트·보존 태그 목록

U: column/shape/text/card/appHeader/pageHeading/attention/issueRecord/loading/action/status/state/metric/history/expandable/grid (75-159), light/dark palette·76dp/본문22/보조20(12-40). 기존 토큰은 시작점이며 새 시각위계로 조정한다. 자체도형/토큰을 Pleos공식컴포넌트 인증으로 소개하지 않는다.

보존할 행동 태그: new_issue, all_issues, record_search, apply_search, filter_ALL/MANAGED/COMPLETED/MINE, record_filter_status, reset_conditions, issue_{id}, all_summary, driver_{id}, add_driver, manage_drivers, start_driving, cancel_driver, rename_{id}, archive_{id}, driver_name, save_driver, resume_draft, discard_draft, symptom_type, type_NOISE/VIBRATION/WARNING/OTHER, situation, save_issue, save_issue_ai, record_extras, select_snapshot, discovered_at, memo, adopt_voice_action, action_note, save_action, issue_ai_explain, speak_issue, attach_location, link_snapshot, edit_issue, delete_issue, acknowledge, acknowledged, add_note, change_status, recurrence, issue_summary, show_in_all, inspection_navigation, consultation_summary, consultation_question, copy_question_{id}, read_vehicle, auto_snapshots, soc_notices, vehicle_analytics, load_details, health_expand, metric_/value_/time_/attempt_, enable_soc, disable_soc, reconcile_soc, soc_threshold, soc_evidence, soc_history, soc_history_more, analysis_EV_SOC/ODOMETER, analysis_days_1/7/0, analysis_chart, analysis_statistics, analysis_delta, portable_ai, analysis_detail, analysis_more, analysis_reset_page, toggle_hidden, open_snapshot, hide_snapshot, snapshot_technical, snapshot_raw, history_newer/older, all_vehicle_history, linked_snapshot/linked_snapshot_detail, layout_*, time_device/seoul, replay_guide, help_topic_0..5, theme, help_contents, guide_choose/skip, close_guide.

P 태그: portable_back/status/saved_record, issue_ai_excerpt, portable_vehicle_context/details, portable_preview/payload_preview, portable_email/password/login, portable_register/consent_cancel, portable_explain, portable_model_result/result_input, portable_speak/speak_stop, portable_quota_notice, portable_withdraw, portable_status_check, portable_view_saved/cached_result, portable_logout.

공통: navigation_bar/page_title/attention_card/home_counts/issue_record_card/loading_state/status_label/empty_state/error_state, screen_scroll/feedback/safety_status/test_banner/retry. SpeechControls.kt:8-29의 speech_state/partial, voice_symptom/voice_cancel, validation만 speech_level. 수동완료 버튼 없음.

주의: **삭제하는 탐색 태그(nav_ISSUES/nav_VEHICLE/menu/my_issues 등)는 새 구조에 맞는 의미별 대체·테스트 매핑이 필요**하다. 유지목록은 모든 태그를 모든화면에 계속 노출하라는 뜻이 아니다. 숨은 클릭대상·거짓 태그 통과를 만들지 않는다. 구형 ai_payload_preview/ai_consent/ai_cancel은 일반흐름 제외 여부를 별도검증한다.

## 6. 다이얼로그와 예외 경로 전수

- M81-89 런타임 마이크/위치 권한: 거부 원인과 다음행동, 강제동의 없음.
- M115-132 위치취득확인→좌표/시각 저장확인 2단계: 사용자 동의와 실제 위치출처를 합치지 않음.
- M133-143 길안내: 장소명/위도/경도/취소/요청, 기존 경로/약관 검사. 새 장소검색/예약이 아님.
- M607-610 운전자보관: 과거기록 유지.
- M626-631,668-675 초안폐기: 기존기록과 분리, 수정취소는 원상세로.
- M1063-1068 snapshot 연결: 원문/연결 실패·pending복구 구분.
- M1073-1076 논리삭제: 작성자 확인·확인창·이력보존, 영구삭제 아님.
- M1119-1129 status_UNRESOLVED/PLANNED/INSPECTED/RESOLVED: 해결은 추가확인. 읽음과 다른 수동상태.
- M1137-1145 일반confirm/취소. M1145-1156 신규 발견시각 Date→Time picker.
- P196 기존결과 있을때 신규생성 확인: 새모델 요청/과금 가능성과 기존결과 열람 분리.

## 7. UI와 분리해 처리할 신뢰성·안전 이슈

정적 발견이며 재현완료가 아니다. 대규모 시각수정과 동시에 고치지 않는다.

1. M659-665 ACTION 저장은 captureDraft 반환을 검사하지 않음(증상저장은 M1007 검사). 안전전환/onStop M221-230,278도 반환무시. 실패주입으로 손실 가능성부터 검증하고 별도 패치.
2. M280-299 일부 펼침/숨김/페이지 상태(hiddenSnapshots/showHealth/showUnavailable/socTechnical/socHistoryLimit)와 AI_PREVIEW consent/payload는 모두 Bundle에 저장되는 것은 아님. 새 화면 복원 범위를 명시하고 테스트.
3. P216-218의 SOC request_from_analysis+기동의는 render 후 자동 requestExplanation 경로. 새 설계의 명시요청과 다름. 자동 요청 제거는 전송정책 변경을 포함하므로 독립 패치/모델없는 fixture로 검사. pending 회수까지 무조건 금지하지 않음.
4. M793 선택기간과 P254-258 고정최근하루는 같은범위라고 표시하면 안 됨. 전달계약확장 또는 실제하루범위명시 중 하나를 별도결정.
5. 원문/상담TTS는 M90 take600, AI는 ExplanationPlayback 전체분할. '전체듣기'로 통일표기하면 거짓이 될 수 있음.
6. P 로그아웃/철회가 계정별sidecar에 미치는 실제효과를 고지. 디자인단순화를 이유로 삭제/복구 의미를 재정의하지 않음.

## 8. 기존 연구를 어떻게 다르게 판단했는가

CARITDA_UI_UX_RESEARCH의 홈/문제분리, ONBOARDING의 짧은안내/데이터사실성은 유용하다. NAVIGATION_RECORD_GLEO_UX의 공간있으면 섹션탭추가, 디자인시스템/acceptance의 기존홈유지는 이번에는 채택하지 않는다. 새 IA는 기존문서를 수정·삭제하지 않고 별도제안으로 남긴다.

DESIGN_ACCEPTANCE의198렌더 및 STT_AI_HANDOVER_ACCEPTANCE의298assertion은 당시문서상결과다. 이번 조사에서 다시실행한것이 아니며 고령자과제/실차/새모바일/신규모델왕복 성공을 뜻하지 않는다.
