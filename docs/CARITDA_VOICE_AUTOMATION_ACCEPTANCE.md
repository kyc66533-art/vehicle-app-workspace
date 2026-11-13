# CARITDA 음성 자동화 인수 결과

## 2026-11-13 — 게시 재개 범위

이번 작업은 어제 수정본을 별도 브랜치·PR로 게시하는 작업이다. Kotlin5파일의 어제 검증 해시 일치·이전 빌드/검사 로그·현재 main 차이와 게시 안전성을 확인한다. 아래332 assertion/실제 A→B→A/TTS/원본 보존은 11월12일 결과로 유지하며 오늘 다시 실행했다고 표시하지 않는다. 일반 앱/SQLite/인증/원장·유료 모델·Worker는 조작하지 않고 main도 직접 변경하지 않는다. 어제의 GitHub BLOCKED는 당시 도구 취소 결과다.

## 최신 음성 복구 판정 — 2026-11-12 23:38 +09:00

아래 16:30의 사람 발화 미실시 판정은 이번 실제 시험 범위에서 갱신한다. [환경·원인·공식 자료·보존 상세](STT_STABILITY.md), [현재 실행 안내](STT_RUN_GUIDE.md).

| 항목 | 결과 | 실제 검증 범위 |
| --- | --- | --- |
| Windows→Android PCM | PASS | 정상 사용자 캡처 peak7682, 같은 AVD 명시 host audio 재실행 후 Android peak2→14958 |
| 수동 한국어·자동 종료·초안 | PASS | 실제 사용자 진단 연속2회 + 같은 Main 입력란, 마지막 A→B→A 재실행까지 확인 |
| 기존 홈의 정확 호출 | FAIL | 사용자가 ‘카잇다’를 말했으나 실제 최종 ‘가 있다’, 기존 패턴 거부 |
| 홈 음성 확인 대체 경로 | PASS | 가 있다→확인 질문→네→말씀하세요→실제 증상→1건/receipt1→실제 전체 TTS·다음 STT READY. 모호한 응답은 저장0 |
| 실제 음성 OFF 명령 | NOT_TESTED | 종료했다고 사용자 응답했으나 로그상 시간 초과·해제, 해당 final 없음. 설정 OFF는 별도 실제 확인 |
| 독립 호출 엔진·외부 Gleo·실차 | NOT_TESTED | 위 결과로 확대하지 않음 |
| 신규 Gemini/Worker | 미실행 | 요청/배포0회, 기존 한도·계정·원장 유지 |
| 회귀·빌드 | PASS | 새14+관련318=332 assertion, Debug/Validation/AndroidTest/Lint 성공. Unit NO-SOURCE |
| 원본 보존 | PASS | 1025DB/17023원본행·schema/integrity/FK, 일반 문제/이벤트/운전자/초안25/100/10/0 유지 |
| 일반 앱 반영 | PASS | 같은 서명 install-r/빌드·설치SHA 대조/Main resumed. 일반 UI 자동 조작0, 사람 발화는 별도 미검증 |
| GitHub 게시 | BLOCKED | 연결 도구가 user cancelled MCP tool call 반환. 재시도 없이 중단/main7f913a4 불변. 검증된 로컬 소스/설치 APK 유지 |

사람 시험은 validation 고유 UUID/동일 Main 소스이며 일반 앱의 실제 사람 발화 성공으로 바꾸지 않는다. 일반/validation UI 자동화 분리와 기존 암호화 대화·수동 기능을 유지했다. 추가 실제 모델이나 기존 서버 수정은 이번 장애 복구에 필요하지 않았다. 설치 직후 실행 중 복사 사본의 인덱스 검사 실패와, 원본을 변경하지 않고 쓰기 중단 후 재복사해 전체 통과한 결과를 STT 보고서에 함께 기록했다. 최종 일반 기존159행 추가/원본행 변경0, 인증·캐시 파일 동일/heartbeat·설치 표시 변경이다.

## 최신 판정 — 2026-11-12 16:30 +09:00

시작 main `a9c2b29c66ccff3b5a0bd2381113834e731eb5a2`. 기존 구현/성공 근거를 재사용하고 홈 대기·운영 배포·실모델·실패 복구의 잔여 부분을 수정했다. [이번 파일·계약·배포 상세](CARITDA_AUTOMATION_INTEGRATION.md). 아래 접힌 과거 기록의477/198·배포0·모델0을 이번 성공으로 재사용하지 않는다.

**운영 대화 API와 실제 Gemini 분석/새 후속 답변·전체 TTS는 검증했다. 실제 사람의 홈 호출→연속 STT까지 포함한 전체 흐름은 미완료다.** 독립 호출어 실험은 FAIL, 기본 Pleos 제한 대기의 사람 발화는 사용자 보류 NOT_TESTED다.

검증 수준은 ①소스 ②컴파일 ③합성 ④실제SDK ⑤validation 에뮬레이터 연결 ⑥일반 앱 실행 ⑦신규Gemini ⑧외부Gleo ⑨실차다. 각각을 대신 성공으로 표시하지 않는다.

### 최종 A–E 시나리오

| 시나리오 | 판정 | 실제 확인 범위 |
|---|---|---|
| A 홈에서 버튼 없이 호출 | **NOT_TESTED**(기본 Pleos 사람 발화) / **FAIL**(독립 Vosk 실제 발화) | 홈 자동대기·started표시·wake final+ended→prompt→증상·OFF/수명주기29개는 실제 Activity/SQLite와 callback fixture PASS. 별도 음성 Activity/시작 버튼은 필요하지 않음. 실제 PCM/SDK 권한은 확인했으나 말소리 인식 성공이 아님 |
| B 저장→실제 신규 Gemini→전체 TTS | **PASS**(명시 validation 입력) | ASR만 fixture. 홈의 기존 제어기가 현재 운전자 문제1건을 자동commit→새 단일분석→실제 전체TTS DONE. 최초 전체 계측은 이후 후속 오류로 실패했으며 이 부분 성공은 저장 메타데이터/실제callback/새 프로세스 재조회로 확인 |
| C 같은 문제의 새 Gemini 후속 답변 | **PASS**(복구 진입의 실모델/TTS) / **NOT_TESTED**(사람 연속STT) | 앞선 증상·검증된 실제AI를 사용한 ‘그럼 어떻게 해야 해?’의 새 응답·같은issue/revision/session·전체TTS DONE·암호화보관. 초기 후속은 MODEL_CONTEXT_INVALID로 실패. 수정 후 별도 추가 승인1회로 성공. 세 번째 질문은 계약만 검사/모델0 |
| D SDK 변화→홈→음성→후속 | **NOT_TESTED**(이번 신규 실제SOC/사람발화) | 기존 실제18/45·자동TTS 성공은 과거 기준으로 보존. 이번에는 같은 사건→홈inline제어기/중복없음/이력연결 fixture PASS. SDK Console 값 변경·추가 Safety 이상값 수신을 새로 성공 처리하지 않음 |
| E 장애·수명주기·보존 | **PASS**(선택 회귀) / **NOT_TESTED**(물리전화/PTT/일반캐시내용) | OFF·포커스거부/상실·안전제한·백그라운드·운전자/계정범위·늦은callback·중복·UNKNOWN/공유lock·영속조회. 실제503은 이번 모델에서 재발하지 않음. 원본 엄격 행동일성은 아래 읽음1건 차이로 FAIL이며 내용보존을 별도 감사 |

### 실제 배포와 사용량

- 기존 Worker에 사용자 정상 CMD 인증으로 비파괴 코드 배포3회. 최종 활성 **`37c6cab1-deb8-42ab-a0db-baa3614e44ae`**, 번들 SHA256 `4643c387ccce7a9056f12a3874a23abf3a75eea79e68779994c94b600498ca19`/99,641byte.
- health200/modelON·기존단일분석401·신규dialogue401을 실제 에뮬레이터 HTTPS로 확인했다. `/v2/issues/dialogue`404 해결. 기존 Secret/binding/DO/Firebase/모델/할당량/비용 지문은 배포 전후 동일하다. Codex 호스트의 Cloudflare 제어 접근은 여전히 EACCES이며 사용자 OAuth를 복사하지 않았다.
- 신규 공급자 시도 **총3회**: 최초분석 성공1, 첫후속 검증실패1, 사용자 ‘추가1회 승인’ 후 새후속 성공1. RATE_LIMIT/ISSUED READ만 있었던 구간을 별도 유료 호출로 중복 계산하지 않는다. 기존2회 hard guard를 늘리지 않고 추가1회 전용 guard를 사용했다.
- 실제 계정 원장 portable attempts8→11, 잔여3→0, uncertain=false. 일5/월100/서비스300/기존 비용예약 유지, 원장초기화0/정책변경0. 추가 유료 호출0. 실제 청구금액은 조회하지 않았다.
- 미해결 문제1건/revision1·실제AI2개·서버성공operation2개·prompt+두설명의 전체TTS DONE을 프로세스 재시작 뒤 복호화 재조회했다. 실패operation도 보존했다. 이력의 실제 응답과 local/fixture 응답을 구분한다.

### 변경 영향 검사

최종 Debug·Validation·ValidationAndroidTest·Lint·testDebugUnitTest 작업은 **BUILD SUCCESSFUL(2분20초)**이다. Lint **오류0/경고67**. Unit은 **NO-SOURCE**로 검사 수에 포함하지 않는다. 일반빌드는 선택 Vosk 엔진을 포함하지 않는다. 선택 native 실험의 추가16KiB정렬 경고2개/실차미검증은 일반APK 완료 결과와 분리한다.

| 이번 선택 검사 | assertion | 경계 |
|---|---:|---|
| 홈 자동대기·같은제어기·inline경고 | 29 | 실제Main/SQLite/정차gate, ASR/AI/TTS callback fixture. 로컬SOC Q/A의 외부문맥 제외 포함 |
| 기존 음성 핵심 / 수동 음성 Activity | 72 / 17 | 동의/receipt/암호화/중복/오류/latecallback/구성변경·두새fixture응답 |
| STT 종료계약 / 전체TTS | 40 / 3 | final+실제ended·취소/generation, TTS는 실제SDK 전체재생 |
| B 홈 / DB / Gleo 내부 | 29 / 19 / 37 | 기존 운전자·기록·홈·Action/Callback 회귀 |
| 단일AI / 저장·AI·A/B인계 / 재실행 | 56 / 35 / 4 | fixture provider·기존cache/pending·명시저장·인계/읽음/점검영속 |
| 실제 마이크/플랫폼 | 7 | AudioRecord16k/16,000sample수신, 음성원본저장0. role제공true/보유false, 온디바이스Recognizer false |
| Relay 읽기 / 실모델 이력 재조회 / 사용량 읽기 | 4 / 5 / 1 | 실제HTTPS·Keystore·원래운전자/계정·남은횟수0, 모델0 |
| 추가1회 실모델·실제TTS | 8 | 사용자 추가승인, ASR는 앞선fixture, 새Gemini후속/실제전체DONE |
| 합계 | **366** | 최종 성공 묶음별 중복실행 제외. 최초477개를 더하지 않음 |

별도 ‘기존2회 승인으로 추가1회 gate 실행’ 거부 검사1개도 통과했고 기존 원장 해시는 동일했다. 최초 실패한 호출어·후속·보존 검사는 성공 합계에 넣지 않았다. AndroidTest prepare/일반앱계측/무조건적인 전체477개 재실행은 하지 않았다.

Worker 관련 검사는 TypeScript/결정적 번들과 portable64+issue42+recurring4+overlay5+dialogue54+quota12+stabilization10+workerd16+배포guard15 = **222개**다. 기존198개 전체를 무조건 반복하지 않았다. 실패 응답을 허용하도록 validator를 완화하지 않았다.

### 일반 앱 적용과 최종 데이터 비교

기존 일반APK를 별도 백업하고 서명 동일성을 확인해 `install -r`했다. 다시 읽은 설치 APK SHA256은 **`a3ebd79f1857769aaab46dbe0de2b0bc24dccadb540655ac234775d0ee598cee`**로 빌드 파일과 일치했다. 기본 APK에서 Vosk 모델/libvosk 미포함을 확인했다. MainActivity COLD 실행 Status:ok/resumed, 일반UI 버튼자동조작0. 일반 앱에서 실제 음성대화를 완료한 것은 아니다.

최신 작업전 백업은 일반44DB/1,874행과 validation797DB/12,941행, 총 **841DB/14,815원본행**이다. 최종 새 백업은 일반44/validation981DB다. 양쪽 앱을 종료하고 독립 사본에서 전체schema/version/index/trigger/view·integrity/FK 및 원본행을 비교했다.

| 대상 | 최종 결과 |
|---|---|
| 일반 문제·운전자·초안 | 문제25/운전자10/초안0 유지. 시험문제 추가0. 원문/작성자/발견시각/수동상태/revision/삭제표식 모두 유지 |
| 엄격 행 동일성 | **FAIL: 기존 문제1행의 updated_at만 변경**. 16:04:40.012 KST에 같은문제/revision2의 ACKNOWLEDGED 이벤트1건 추가, 이벤트99→100. 일반APK 업데이트16:23:54 이전 발생. 생성주체 미확인이므로 사용자행동/자동화로 단정하거나 되돌리지 않음 |
| 원본 내용·기존 이력 | 위1개 시각 차이를 정확한문제hash·시각·새읽음event로만 별도 감사. 나머지14,814원본행 완전동일, 해당문제의 다른모든필드·기존99이력 동일. 전체841DB schema/integrity/FK통과. 원래 strict실패와 전후백업을 보존 |
| 일반 추가행 | 기존DB에366행 추가/새DB0. 문제추가0이며 읽음1건 포함. 전체불변이나 증가0으로 표현하지 않음 |
| validation | 기존12,941행 내용 유지/기존DB391행추가/고유시험DB184개 추가. 루트0문제/0이벤트/2운전자/0초안 유지. 기존validation삭제0 |
| 일반 private | 16개 중12개 byte동일. 변경4개는 암호화인증·heartbeat·암호화issue캐시·설치표시. 인증state/캐시파일은 존재하며 전후백업 유지. 캐시 ciphertext7,178→7,512byte. 일반UI자동화금지에 따라 내용을 복호화 비교하지 않아 **계정/캐시내용 완전동일은 미검증** |
| validation private | 354개 중348개 동일/변경6개(같은계정재확인·heartbeat·시험pointer2·fixture캡처·설치표시). 기존 portable캐시86개는 byte동일. 신규실모델결과는 별도sidecar에서 복호화 재조회 성공 |
| 서명·서버 | 같은키 update/uninstall0·Keystore교체0. Firebase로그아웃0·서버원장/Secret/DO초기화0·정책변경0. 사용량은 승인된 실제시도3회를 반영 |

### 남은 최소 확인

1. 사용자가 원할 때 일반 CARITDA에서 **운전자 확정 → 홈 → 음성 대화·설정·이력 → 음성 설정**의 필요한 옵션과 마이크 권한을 한 번 선택한다. AI 새 요청 없이 음성만 확인하려면 Gemini 전송은 끈다. 홈 복귀 후 실제 ‘음성 대기 중’이 표시되는30초 안에 ‘카잇다’, ‘말씀하세요’ 뒤 증상을 말한다. 이번에는 사용자가 발화 시험을 보류했으므로 다시 요청하지 않았다.
2. 기존 AI 설명은 해당 문제의 AI 화면/음성 대화 이력에서 재생한다. 새 음성 입력·후속 질문의 실마이크 검증은 남아 있다. 이번 유료승인3회는 소진했으므로 에이전트가 네 번째 요청을 하지 않는다.
3. 일반 앱의 기존 계정/저장 설명은 사람이 해당 화면에서 확인해야 한다. 위읽음 이벤트 생성주체와 암호화파일의 의미변경 여부는 로그만으로 확정할 수 없다. 시스템 비서 선택/PTT/외부Gleo·실차는 별도이며 홈 기능의 필수설정이 아니다.

검증된 명시파일만 GitHub 연결 도구를 통한 게시로 main 이력을 잇는다. 원격 최신HEAD/대상SHA/부모/나머지파일·Aside29개를 재확인하고 최종SHA는 응답에 보고한다. 원본DB/개인캡처/인증·캐시/모델바이너리/로그는 게시하지 않는다.

<details>
<summary>이전 a9c2b29c 작업 인수 기록 — 이번366개/실모델/배포와 별도</summary>

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

</details>
