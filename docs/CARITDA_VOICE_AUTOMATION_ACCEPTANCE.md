# CARITDA 음성 자동화 인수 결과

기준일: 2026-11-12 KST. 시작 main `d27cc79dc3ea70687065107ca5e408dbcb085421`. [구현/파일/계약 상세](CARITDA_AUTOMATION_INTEGRATION.md).

**판정: 앱 코드 구현·격리 통합 검증 완료, 실제 음성+실모델 전체 자동화는 미완료.** 사용자는 실제 STT 발화를 나중에 직접 확인하고 Wrangler 로그인은 직접 복구하기로 했다. 새 배포 0회·새 유료 모델 0회다. 과거 Gemini 성공 1회를 이번 성공으로 계산하지 않는다.

검증 수준: ①소스 ②컴파일 ③합성 ④실제 SDK ⑤validation 에뮬레이터 연결 ⑥일반 앱 실행 ⑦신규 Gemini 모델 ⑧외부 Gleo ⑨실차. 아래에서 확인하지 않은 수준은 통과가 아니다.

## 기능별 판정

| 기능 | 판정/검증 수준 | 실제 근거와 남은 경계 |
|---|---|---|
| 내부 음성 세션 | 구현/검증 ①②③⑤ | 시작/종료, final+ended, 전체 재생 후 후속 입력, 5분/5회 제한; 새 실제 발화는 사용자 보류 |
| ‘카잇다’ | 부분 ①②③ | 이미 듣는 세션 내 명령 인식; 마이크 OFF의 독립 호출어/앱 밖 실행 미구현 |
| 자동 문제 저장 | 구현/검증 ①②③⑤ | 실제 SQLite commit/운전자/CREATED/영수증/중복 방지; 일반 DB에 시험 기록 없음 |
| 저장 후 기존 AI 분석 | 구현/검증 ①②③⑤ | 기존 인증/client/service 자동 연결, provider fixture; 이번 신규 모델 ⑦ 미검증 |
| 자동 전체 TTS | 구현/검증 ①②③④⑤ | 실제 Pleos on-device DONE·중단 CANCELLED; 모델 설명 통합은 callback fixture |
| 새 후속 AI 대화 | 코드/격리 검증 ①②③⑤ | 서로 다른 새 응답 2개와 이전 문맥 payload 검증; 운영 endpoint/실모델 ⑦ 미완료 |
| 대화/AI/재생 보관 | 구현/검증 ①②③⑤ | AES-GCM sidecar, 순서/문제버전/재생/receipt·재시작·계정/운전자 범위 |
| SOC 자동 안내 | 실제 연결 ①②③④⑤ | 실제 SDK 100→18→반복18→45, 홈/TTS/이력/회복, SDK100/P/0 복원 |
| 추가 Safety 신호 | 부분 ①②④ | POWER_DOWN false GET+실제 callback; 나머지 3개 capability 미지원, 이상 발생 실측 아님 |
| 차량 안내 후 질문 | 구현/검증 ①②③④⑤ | 실제18% 조회 답변+TTS, 후속 ASR만 fixture, ‘기록해 줘’ 후 실제 SQLite 연결 |
| 시스템 비서/Gleo | 부분 ①②③ | role 제공 true/선택 false; Gleo 내부37회귀; 외부 실행⑧ 미검증 |
| 일반 CARITDA | 설치/시작 확인 ⑥ | 동일 서명 업데이트·cold launch만; 일반 앱 UI 자동화 금지에 따라 실사용 대화 확인은 사용자 몫 |
| 실차 | 미검증 ⑨ | 에뮬레이터 SDK를 실차 성능으로 주장하지 않음 |

## TEST A–E

| 검사 | 결과 | 정확한 경계 |
|---|---|---|
| A 음성→기록→분석→전체 안내 | 실제 Activity/SQLite/Keystore 통합 통과 | 인식 final/ended·분석 응답·모델 설명 재생 callback은 fixture, 별도 저장/생성/듣기 버튼 없음. 실제 STT/신규 모델 통합 미검증 |
| B 연속 새 질문 | 같은 issue/revision/session, 새 요청 2개·이전 Q/A·새 응답·이력 통과 | fixture 응답을 기존 설명 재생과 구분; 새 Gemini 모델 답변 미검증 |
| C SOC 변화→홈→자동 음성 | 실제 SDK/TTS 연결 통과 | Console HTTP200·SDK callback·반복GET을 별도 확인. 이어지는 ASR은 fixture. 회복으로 사용자 문제를 해결하지 않음 |
| D 실패/복구 | 공급자503·Relay503·네트워크/timeout·UNKNOWN·한도·인증·검증 실패·캐시 유/무 합성 회귀 | 실제 공급자 장애를 유료로 유발하지 않음; 예약/UNKNOWN 재dispatch 방지, 원본/캐시 보존 |
| E 수명주기 | generation·취소·안전·백그라운드·재생중단·구성변경·운전자/계정 범위·동시 요청 검사 | 실제 전화/내비 동시 통화·주행 중 플랫폼 음성·물리 PTT·실차 미검증 |

## 빌드와 계측

최종 Debug·Validation·Validation AndroidTest·Lint 빌드 `BUILD SUCCESSFUL`(2분32초), 시험 연결 수정 후 AndroidTest 재빌드 성공(1분6초). Lint **오류0/경고67**. `testDebugUnitTest`는 **NO-SOURCE**이며 성공 검사 수에 포함하지 않는다. 기본 prepare/일반 앱 계측/connected 전체 실행은 하지 않았다.

| 검사 묶음 | 중복 제외 assertion | 범위 |
|---|---:|---|
| 새 음성 핵심 | 72 | router·원자 commit/중복·암호화/재시작·동의범위·state/late callback·읽기 OFF 후속입력·공유 캐시 lock·503/UNKNOWN 보호 |
| 새 실제 Activity 통합 | 17 | SQLite·Keystore·운전자 연결·새 dialogue 두 turn·보관·Activity 구성변경·설정에서 홈 복귀; ASR/provider/playback callback fixture |
| 새 실제 SDK/TTS | 7 | 정차 safety·Safety4 capability/get·POWER_DOWN 실제 callback·전체 TTS DONE/중단 CANCELLED·Listener 해제 |
| 새 실제 SOC 음성 연결 | 13 | 실제 SDK18/45·홈·자동 전체 TTS·중복/이력·후속 ASR fixture |
| 새 실제 Relay 읽기 | 4 | 실제 HTTPS health/모델ON/인증401/대화미배포404, 모델 호출0 |
| 기존 회귀 | 364 | STT40+자동6, Gleo37, DB19, integration33, flow35+cold4, 실제TTS3, issueAI56+cold4, portable26, B29+21+cold3, 위치6+cold2, Navi7, DriveCheck24+SOC7+cold2 |
| 합계 | **477** | 반복 실행은 더하지 않음. 별도 실제 SDK100/P/0 복원2개 통과 |

Worker는 TypeScript 검사와 번들 기반 보안64+기존 issue42+recurring gate4+overlay5+대화33+quota12+errors10+provider5+배포guard7+로컬 workerd16 = **198개 오프라인 검사**를 통과했다. 합성 provider dispatch를 유료 호출로 세지 않는다. 번들 SHA256 `327b6f8b8715e71aa9c809a8ea0c85b451c183931a267e83e12c952f7f1fb833`, 98,691byte. 이것은 운영 배포 검증과 구분한다.

일반 APK는 기존 서명과 일치하며 `install -r` 후 설치된 APK를 다시 읽어 SHA256 `eaba72678134888a7c10b51bcb6a119922439c4904703c9474ebee90ac5b012e`와 대조했다. 일반 앱 실행 결과와 최종 데이터 보존은 아래에 별도 기록한다.

## 일반 앱과 원본 보존

일반 MainActivity를 COLD로 실행해 `Status: ok`/resumed와 실제 운전자 선택 화면·B 라이트·OS 경계를 확인했다. 일반 앱의 버튼 자동 조작은 0회이며 새 음성 대화 자체의 일반 앱 실사용은 아직 확인하지 않았다. 개인 운전자 자료가 보이는 전체 화면 캡처는 비공개 백업에만 보관했다. 검사 후 두 앱을 종료해 저장소의 일관된 사본을 비교했다.

| 대상 | 새 기준 백업 → 최종 비교 |
|---|---|
| 일반 원본 DB | 42개/1,675원본행 전부 유지, 원래 table schema와 integrity/FK 통과, journal v3→v4만 허용 |
| 일반 사용자 기록 | 문제25·이벤트99·운전자10·초안0 불변, 시험 문제/운전자 추가0 |
| 일반 신규 데이터 | 정상 실행의 SDK/파생70행, 새 attention sidecar1개; 기존 행 덮어쓰기0 |
| validation 기존 DB | 539개/11,164원본행 전부 유지, 루트 문제0·이벤트0·운전자2·초안0 불변 |
| validation 신규 시험 | 고유 fixture258DB 추가. 기존 validation도 삭제하지 않음 |
| 전체 원본 | **581DB / 12,839행**의 행 multiset·schema·integrity/FK 대조 통과 |
| 일반 private 파일 | 16개 중14개 byte 동일. 변경2개는 Firebase heartbeat와 설치 프로필이며 인증/동의/기존 AI 캐시 원문은 동일 |
| validation private 파일 | 269개 중261개 동일. 변경8개는 시험 포인터5·시험 캡처1·heartbeat·설치 프로필. 인증/기존 캐시 초기화 없음 |
| Keystore/서명/서버 | 같은 서명의 update 설치, uninstall/키교체0. 유료 요청0/배포0/Secret·정책·DO 원장 초기화0. 운영 잔여 횟수의 새 인증 조회는 하지 않음 |

운영 적용 전 최신 백업의 독립 SQLite 사본에서 v3→v4 이전, 원본136행(일반)/4행(validation), rollback·반복 적용도 검증했다. 새 영수증 표 이외 기존 사용자 표는 변환하지 않았다. 백업/DB/토큰/인증 파일/개인 로그는 게시하지 않는다.

## 실제 Android 화면

모두 validation 앱에서 실행한 Kotlin View 캡처다. 사용자 원본 이름·기록·인증 화면은 게시하지 않았다. 배경으로 이동한 홈 View 캡처는 빈 화면이어서 게시하지 않았으며 홈 조건은 실제 SDK/붙어 있는 View 검사로 검증했다.

| 화면 | 캡처 | 경계 |
|---|---|---|
| 자동 저장·새 후속 설명 | [음성 대화](assets/voice-automation/voice-dialogue-fixture.png) | 실제 Activity/SQLite, ASR·provider·재생 callback 모두 fixture |
| 새 SOC 안내 자동 읽기 후 대기 | [차량 안내](assets/voice-automation/voice-sdk-attention.png) | 실제 SDK18·실제 on-device TTS DONE, 뒤에 받는 ASR은 fixture |
| ‘이 내용 기록해 줘’ 후 저장 | [안내와 기록 연결](assets/voice-automation/voice-sdk-saved.png) | 실제 SDK/TTS/SQLite, 해당 명령 ASR은 fixture, AI 전송 OFF |

## 문제·권한·복구 기록

1. 초기 native 새 대화 검증에서 JSON facts 배열을 문자열/객체 동일성으로 비교해 APP_FACTS가 발생했다. 별도 dialogue 구조 비교로 수정하고 원본 계약 회귀를 유지했다.
2. 자동 차량 TTS 중 Firebase 복원에 의해 빈 계정→계정 변화가 감지돼 안전 종료됐다. Main에서 인증을 미리 준비하고 자동 시작은 복원 완료 뒤로 미뤘다. 실제 SDK/TTS 연결을 재검사했다.
3. 기존 STT 종료 시 stop 생략 시도가 AS14 회귀를 일으켜 되돌렸다. 원래 final+ended/stop/release와 40개 회귀를 유지한다.
4. 긴 계측 묶음 중 Android `System has crashed`로 중단됐다. AVD 초기화·재부팅 명령 없이 기존 에뮬레이터 정상 복귀 후 중단된 187개 검사를 재실행해 통과했다. 최초 실패를 삭제하지 않았다.
5. Console100 복원 직후 SDK 조회가 이전45를 반환한 시도가 있었다. 실제 SDK100을 별도 제한된 복원 검사로 확인했다. Console200만으로 SDK복원 성공으로 판정하지 않았다.
6. Cloudflare 제어 API는 CLI 인증/호스트 접근·네트워크 문제로 미검증이다. 실제 에뮬레이터 HTTPS는 기존 모델 ON·단일 분석 인증401·신규 대화404를 확인했다. 배포 전 코드/198개 오프라인 검사를 완료해도 운영 배포 성공을 뜻하지 않는다.
7. 마지막 SOC 회복 검사에서 별도 일회 조회용 SDK 연결을 해제한 뒤 45 Callback을 받지 못했다. 시험을 기존 구독 연결의 공개 GET 재사용으로 바꾼 후 같은 13개와 실제100 복원을 통과했다. 생산 코드에 가짜 Callback/회복값/재구독 polling을 추가하지 않았다.

## 사용자가 직접 확인할 최소 경로

1. 일반 CARITDA에서 기록 운전자를 확인하고 **홈 → 음성 대화 → 음성 설정**으로 이동한다. 필요한 자동화/전송 범위를 직접 선택한다. 기존 AI 계정이 필요하면 같은 화면의 **AI 계정·기기·전송 동의**로 이동한다.
2. **음성 대화 시작**을 한 번 누른 뒤 ‘카잇다, 차 앞쪽에서 이상한 소리가 나. 기록해 줘’라고 말한다. 대화 보관/자동 기록만으로 로컬 저장을 확인할 수 있다. 현재 신규 대화 서버가 배포되기 전에는 연속 Gemini 성공을 기대하지 않는다.
3. 배포/상태 확인 후 승인 잔여 내에서 초기 분석과 후속 질문 하나를 검증한다. 같은 작업의 실모델 합계 최대2를 지킨다. ‘정비소에 가야 할까’까지 추가 유료 검증이 필요하면 기존2회 결과/잔여부터 확인한다.
4. **음성 대화 → 대화 이력 보기**, **기록 → 해당 문제**, 운전자 빠른 전환 후 **미확인 인계**에서 내용을 확인한다. 재생 취소·화면 이탈 후 자동 마이크 재시작이 없는지도 확인한다.

Wrangler 로그인 복구는 사용자가 진행 중이다. 프로젝트에 준비한 `infra/gemini-relay/test/deploy-dialogue.mjs --execute`는 기존 Worker 코드만 갱신하며 모델을 호출하지 않는다. 완료 여부는 실제 배포 버전/feature와 인증·binding 사후 검사를 통해 판단한다.
