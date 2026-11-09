# 선택한 사용자 문제 기록 Gemini 설명 — 검증 기록

업데이트: 2026-11-10 02:57 KST. 작업 기준 main `db39d36c0724c8688c48b12082d8e3e2427f9d1c`. 시작 시 원격 트리와 로컬 파일 SHA를 비교했고 미게시 변경은 없었습니다. 기존 Firebase 등록/Auth 24.2.0, Worker v2 배포 해결 결과, Gemini Secret, DO·사용량은 재설정하지 않았습니다.

## 실제 일반 앱 등록 성공·1회 비용 승인

+- 사용자 수정 배포 버전 d99f21c0-8384-454e-83b4-7083c755a494. 로컬 공개 보고서 2026-11-10 02:30:46 KST에서 feature2개/portable true/model false/401/pass true를 확인했습니다. 직전02:29의 feature누락/passfalse를 합격으로 세지 않았습니다. 이 확인은 v1/v2 재조사가 아니라 이번 인증서 수정본 활성 확인입니다.
+- 사용자가 RECENT_LOGIN_REQUIRED 뒤 동일 계정 재로그인→기기 등록 성공을 확인했고 로컬 issue 동의 파일1개가 저장됐습니다. 실제 Firebase 서명 검증/UID 인가/P-256 기기 증명/동의 저장이 일반 앱에서 성공했습니다. Firebase 프로젝트/기존 계정/키를 재생성하지 않았습니다.
+- 실제 앱 사용량 표시: **기존TEST1, 정식시도0, 승인잔여0, 모델OFF**. 불확실 상태는 최초 문구에서 명시되지 않아 추측으로 통과시키지 않았습니다. UI에 global_total과 불확실 요청 있음/없음을 명시하고 모델OFF/한도0에서 본문 전송 전 상태만 확인하는 preflight를 추가했습니다.
+- 최종 앱 빌드/Lint 및 **신규38+기존140=178 PASS**. 새2검사는 model OFF/잔여0에서 증상 body/새 intent를 보내지 않음을 검사합니다. 기존 pending 결과 조회는 모델OFF에서도 유지합니다. 최종 일반APK `DC8F7E5A667847BE2F666EDFFD564D676221F3E145DBC0F60E2A79111804B811`, TEST `C0A9991E5C290E8A56DFA86B55B6C02395BFBA3976BB0379EDC5607D0B20AAD8`. before-one-call 비공개 백업 후 일반 앱을 데이터 유지 교체했습니다.
+- 사용자가 **선택한 개인정보 없는 기록 한 건/실제 Gemini 최대1회/모델ON/가능한 비용**을 승인했고 정책이 UID1개/after0/devices2임을 확인했습니다. 기존1+신규최대1=global2, UID/일/기기1, 분1,24시간 만료만 준비했습니다. 기존 DO 사용량은 초기화하지 않습니다. [정확한 한 번 시험 절차](../infra/gemini-relay/ISSUE_ONE_CALL_TRIAL.md)를 안내했습니다. PowerShell 블록은 AST 문법 검사만 했고 Codex가 Secret을 설정하거나 모델을 호출하지 않았습니다.
+- 현재 실제 Gemini 결과는 **사용자 시험 대기/미검증**, 이번 단계 실모델0회입니다. 승인 그 자체를 실행 성공으로 기록하지 않습니다. 기존 사용자 원문·상태·관측 및 연구 파일은 유지합니다.

## 인증 실패 실제 원인 — workerd redirect 옵션

사용자가 고정 오류 안내에서 AUTH_KEYS_UNAVAILABLE를 확인했습니다. 이는 토큰 공개 서명 인증서를 가져오는 단계이며 UID·기기 서명 검증보다 앞섭니다. 기존 일반 Node fetch Mock 및 공개키 override 검사는 이 네이티브 요청 경로를 빠뜨렸습니다. 새 실제 workerd/X509 시험에서 같은 실패를 재현했고 로컬 합성 전용 probe가 다음 플랫폼 예외를 확인했습니다: Invalid redirect value, must be one of follow or manual. Worker는 redirect:error를 지원하지 않아 Google 요청 전에 TypeError가 발생했습니다. 실제 사용자 토큰·키·본문이나 모델 원문을 로깅한 결과가 아닙니다.

수정은 공개키/SOC/선택 기록/보존 v1의 fetch를 **manual**로 통일하고3xx를 거부하는 것입니다. 인증을 건너뛰거나 다른 호스트로 키를 보내는 fallback은 없습니다. Google cert HTTP 상태·body/JSON/schema·timeout/network/cooldown은 안전한 코드로 구분하고 앱도 AUTH_KEYS_HTTP_403 같은 제한된 숫자 코드를 표시합니다. TLS·alg/iss/aud/exp/UID/Keystore/nonce/quota는 유지합니다.

- 최종 전체 소스 번들 SHA256: CE59954A7122F44133C6D5E631B59B6B9821903ED73273E64E500E0D1C43DEE4. 이전 FB259…는 최초 문제API, 0AA6…는 원인 구분 진단본으로 이번 배포 대상이 아닙니다. 인메모리 변경 사본은 원인/수정 검증에만 썼고 배포하지 않습니다. 최종 전체 소스 MJS·Wrangler 산출물은 사용자가 일반 PowerShell에서 재생성·dry-run 성공했습니다.
- Node 공개 인증서16 + 실제 workerd 인증서/서명5 + 실제 workerd Gemini 전송/302차단5 + 기존 보안64 + 문제API42 + 기존 workerd16 = **148 PASS**. 실제 HTTP 전송을 로컬 서비스로 대체한 fixture이고 실제 Gemini0회입니다. 별도 기존 v1 격리 harness59 PASS도 완료했으며 이 원장을 운영 원장으로 복사하지 않았습니다. Node에서 security.js 파일을 실행만 한 것은 assertion 실행으로 세지 않고 runWorkerChecks가59를 반환한 실행만 셌습니다.
- 최종 앱 빌드/Lint 성공 및 Android 신규36+기존140=176 PASS 재확인. 최신 일반 APK `E105AFFEAC0588FAC4AD4D176B75B76A3169FA5FEABD43AB205D0AFF0B8CE614`, TEST `C83E8C082223F1821F8B90273150B4238A40E16F2164563143BEB72EB6857E4B`. Firebase 세션 포함 before-keys-fix 백업 뒤 install-r로 일반 앱을 교체했습니다.
- 사용자는 이 **실제 원인 수정본의 모델OFF1회 교체**를 별도로 승인했습니다. 현재 최신 ISSUE_AI_SETUP 1번 실행·공개 feature firebase-keys-manual-v1 확인 대기입니다. 실제 기기 등록/잔액/선택 기록의 실모델은 여전히 미검증이며 모델ON·비용은 승인받지 않았습니다.

[Cloudflare Request 공식 문서](https://developers.cloudflare.com/workers/runtime-apis/request/)는 error 모드를 지원하지 않고 요청 전 TypeError가 난다고 명시합니다. [Firebase 토큰 검증 공식 문서](https://firebase.google.com/docs/auth/admin/verify-id-tokens)는 사용 중인 Google X509 주소와 max-age 갱신을 안내합니다.

## 후속 사용자 검수 — 기기 등록 안내 미표시

사용자는 일반 앱 로그인·선택 기록/JSON 미리보기까지 확인했으나 기기 등록 결과/잔액이 보이지 않았다고 보고했습니다. 당시 로컬 issue 동의 파일이0개여서 앱의 등록 완료 저장은 확인되지 않았습니다. 이것으로 서버에 요청이 전혀 없었다고 단정하지 않습니다. 실제 실패 코드/서버 원인은 아직 확인 중입니다.

안내 TextView가 본문 ScrollView 안에 있어 등록 응답이 화면 위쪽으로 숨을 수 있고 JSON 미리보기가 같은 message를 덮는 구조를 확인했습니다. 진행/성공/실패를 스크롤 밖에 고정하고 미리보기를 별도 본문 패널로 분리했습니다. 허용된 대문자 오류 코드만 보여 주고 예외/토큰/본문은 표시하지 않습니다. Firebase ID 토큰 callback이 오지 않으면15초 후 취소 안내하며 늦은 callback은 차단합니다. 안전 제한·동의·계정·모델OFF는 유지합니다.

수정 후 **신규36 + 기존 관련140 = Android176 PASS**, 최종 빌드/Lint 재통과했습니다. 고정 피드백 위치와 원문 예외 비노출2검사를 추가했습니다. 일반/TEST APK를 데이터 유지 교체했고 ordinary Firebase 세션을 포함한 새 비공개 백업을 남겼습니다. Firebase 재설정/로그인 자동화/일반 앱 UI 계측은 하지 않았습니다. 사용자가 새 고정 안내로 등록을 한 번 확인하는 중이며 실제 Gemini0회입니다. 아래34검사는 최초 구현 당시 결과이고 최신 결과는36입니다.

- 최신 일반 Debug APK: `323ED7584F10FD3FAD1672839D539D0C18F29F6C7139C7A879AAC84EFAE2EA62`
- 최신 TEST APK: `9439AA87C37D5AC53084A5BC3C4FCB8A9DA7E10920D842C4DBF3921E8CE1ABBF`


## 실제 구현

- 일반 앱의 **기록 확인 → 한 건 상세 → AI로 쉽게 설명**을 추가했습니다. 기존 홈·SOC·분석 디자인은 유지합니다.
- 기존 PortableAiActivity/Firebase 세션/Keystore 서명/중계 transport를 재사용합니다. 최초 **문제 기록 범위** 동의만 추가하며 SOC 동의와 분리합니다. 등록한 기기를 재사용하고, 이후 로그인/43자리 토큰/체크박스를 매번 요구하지 않습니다.
- 전송 데이터는 `contract`, `source`, `category`, `situation` 네 필드뿐입니다. 원래 issue ID·작성자·발견 시각·메모·이벤트·차량값·다른 기록은 보내지 않습니다. 전송 본문 사본을 편집할 수 있고 원문은 보존합니다.
- `/v2/issues/explanations`는 기존 Firebase UID 인가·기기 서명·nonce·단일 dispatch·전역/UID/기기/일/분 요청 제한을 사용합니다. 같은 BUDGET/portable:v2에서 SOC와 합산하며 v1 budget 누적값을 보존합니다. 새 namespace나 budget reset은 없습니다.
- 기기 등록에 `issue-explanation-v1` 범위를 추가하고 기존 SOC 범위를 유지합니다. `/v2/issues/consent/revoke`는 문제 기록 동의만 해제합니다. 기존 저장 구조에 optional consents/operation consent를 추가하며 기존 행을 삭제하거나 버전을 초기화하지 않습니다.
- Gemini 응답은 **가능한 원인 / 추가로 확인할 사항 / 점검이 필요한 경우**로 구분합니다. 고정 시스템 지시·신뢰하지 않는 증상 JSON·tool 미사용·구조화 JSON 응답을 사용합니다. provider 성공/STOP/safety/형식·입력 hash/facts/본문 근거 인용·수치·조건부 표현을 검증한 응답만 MODEL로 표시합니다.
- 기록 ID+내용 버전의 로컬 hash로 요청/결과를 분리합니다(서버 전송 안 함). 앱 중단·망 오류 후 같은 intent/operation을 조회하며 자동으로 새 모델 요청을 만들지 않습니다. 결과는 암호화 sidecar에 보관하고 원 문제/상태는 변경하지 않습니다.
- 실제 API 키는 기존 env.GEMINI_API_KEY만 읽습니다. 앱에는 공통 장기 토큰/provider key가 없습니다. 예외 원문·사용자 본문·모델 원문·JWT를 로그에 출력하지 않습니다.

## 화면 로딩 문제와 수정

새 격리 화면 검사에서 IA17이 실패했습니다. 안전 상태 초기화가 AI 요청 generation을 바꾸는 동안 매우 빠르게 끝나는 읽기 전용 issue 로딩도 같은 generation으로 취소돼 본문이 표시되지 않았습니다. 로딩 세대를 별도로 분리한 뒤 같은 실제 Pleos View/회전 검사가 통과했습니다. 전송의 visible/ALLOWED/UID/generation 검사는 그대로 유지합니다.

최초 SQLite fixture 검사에는 신규 DB에 없는 legacy A 작성자를 사용해 FK 오류가 났습니다. 사용자 DB를 건드리지 않고 고유 fixture DB에 운전자를 등록하도록 테스트 준비만 수정했습니다. 초기 실패를 성공으로 집계하지 않습니다.

## 실행한 검증

| 구분 | 실제 결과 | 한계 |
|---|---|---|
| 일반/validation/test APK | 최종 오프라인 Gradle 빌드 성공, 103 tasks | Debug이며 제출용 signed Release 아님 |
| Lint | 오류0, 기존 경고50 | unitTest는 NO-SOURCE, 단위 검사 성공으로 집계하지 않음 |
| 신규 Android IssueAiChecks | **34 PASS**, 실제 Pleos emulator-5554/user10 | 고유 fixture DB/암호화 prefs/키·provider fixture, 실모델 아님 |
| 기존 Android 회귀 | PortableAi26 + View4 + Gemini86 + analytics contract24 = **140 PASS** | 기존 전체 기능 자동 검사 전부 재실행한 결과가 아님 |
| 기존 Worker 보안 | **64 PASS** | offline RSA/ECDSA/DO/provider fixture |
| 신규 Worker 문제 기록 보안 | **42 PASS** | offline, 실제 모델0회 |
| 실제 로컬 workerd | **16 PASS** | 로컬 SQLite/WebCrypto/jose, Cloudflare 운영 호출 아님 |
| 번들·dry-run | 사용자 일반 PowerShell 성공, 생성 MJS/Wrangler 산출물의 위 검사 재통과 | 실제 배포와 구분; 같은 검사 반복은 추가 assertion으로 합산하지 않음 |
| 일반 앱 교체 | `install -r --user 10` 성공 | 앱 삭제/clear-data/AVD 초기화 없음 |
| 사용자/TEST 보존 | 기존 **172개 DB**의 원본 행·schema·user_version·integrity/FK 확인 | 새 fixture DB 6개는 TEST에만 추가 |
| 일반 사용자 DB | issues23/events92/drivers10/drafts0, 원본905행 유지·추가0 | 다음 작업에서23건으로 고정하지 않음 |
| 기존 validation DB | 원본8831행 유지·기존 DB 추가0 | fixture는 독립 고유 DB에만 존재 |

자동 검사는 모두 `com.nextdrive.pleos.validation`에서 prepare/reset 전에 분기해 실행했습니다. 일반 앱 대상 UI 계측을 하지 않았습니다. 문제·관측·스냅샷·낮은SOC·분석 DB 스키마를 변경하지 않았습니다. 백업/DB/키/SDK 세션/캡처/생성 번들/APK는 GitHub 게시 대상이 아닙니다.

### 로컬 산출물 SHA256

- 일반 Debug APK: `B65DFB4F1F1C9C2876C1B204899818CA6929008637C6E5D02F904CCCDBA731A2`
- validation APK: `755E02D981996EBBBB3517BE9AA022BF73C0710B9D7091943E41F4A653747E02`
- Worker MJS: `FB25919962E7A5B8D913E820072094DF0ADAF0BDA53ABB68F87FC16B49D58A5C`

## 원격·실제 Gemini 상태

사용자는 **이번 문제 API의 모델 OFF 교체·PORTABLE_ENABLED=true 인증 확인**을 승인했습니다. [관리자 단일 배포 블록](../infra/gemini-relay/ISSUE_AI_SETUP.md) 1번을 사용자가 실행했고 2026-11-10 01:54:27 KST의 공개 report를 직접 확인했습니다. /health200/release v2/features selected-issue-v1/portable true/model false, 무인증 문제 API401 AUTH_REQUIRED, pass true입니다. 일반 앱 등록·현재 잔액 확인은 아직 대기합니다. 모델 활성화·추가 예산·사용자 실제 기록 전송·실모델 호출은 아직 승인/실행하지 않았습니다. 현재 실제 Gemini 호출 **0회**, 일반 앱에서 실제 설명 표시 **미검증**입니다. Mock/MODEL fixture를 이 성공 기준으로 대체하지 않습니다.

인증/등록·현재 잔액/UNKNOWN을 먼저 확인한 뒤 사용자가 선택한 개인정보 없는 한 건과 최대1회/비용을 승인받습니다. 기존 누적 TEST5회 제한을 초기화/우회하지 않습니다. 승인 전 MODEL_ENABLED=false/기존 정책을 유지합니다.

## 안전 한계

- 문자열 필터는 알려진 전화/메일/VIN/URL·명백한 탈취 지시를 거부하며 모든 이름·의미적 prompt injection·오답을 완전히 찾지는 못합니다. 전송 사본 확인, 고정 시스템 지시, 별도 데이터 영역, 도구 없음, 구조 검증을 함께 사용합니다.
- 근거 인용은 원문에 실제 존재하는지 검사합니다. 가능한 원인은 추정이며 모든 의미적 정비 오답을 자동 검증한 것이 아닙니다. 새 수치·확정 고장/수리 완료/운행 안전 보증 문구는 거부합니다.
- 서버 결과24h/작업 metadata30d/불확실 요청·누적 원장 예외 보관과 공급자 자체 데이터 보관은 구분합니다. 이미 전송된 정보를 로컬 철회만으로 회수하는 기능은 아닙니다.
- PC B 실기기·실차·실제 Gemini·실제 일반 사용자/고령자 사용성·제출용 Release 서명은 별도 검증입니다.

## 사용자가 직접 확인할 순서

1. 모델 OFF 배포/무인증401 확인 후 일반 CARITDA를 실행합니다.
2. **기록 확인 → 기존 한 건 선택 → AI로 쉽게 설명**에서 선택 본문만 보이는지 확인합니다.
3. 최초 Firebase 로그인과 문제 기록 전송 동의를 합니다. 개인정보가 본문에 있으면 전송 사본에서 뺍니다. 원문/추가 메모는 그대로인지 확인합니다.
4. **AI 이용 가능 횟수 확인**에서 기존/정식 사용·승인 잔여/불확실 상태를 확인합니다. 모델 OFF이므로 아직 AI 요청하지 않습니다.
5. 별도 승인된 예산과 모델 ON 설정 뒤 **AI로 쉽게 설명**을 한 번 누릅니다. 세 구획의 실제 Gemini 설명 표시, 기존 원문/점검 상태 유지 여부를 확인합니다.
6. 서버/앱 검증 실패 시 원 기록과 기존 SOC 분석이 유지되는지 확인합니다. 새 모델 요청을 반복하지 않고 기존 요청 결과 조회를 사용합니다.

## 변경 파일·GitHub

앱: MainActivity, PortableAiActivity, ai/AiExplanation, PortableAiClient, PortableAnalysis, 신규 IssueExplanation. 검사: IssueAiChecks, MvpScenarioInstrumentation, Worker issue-security/issue-public-status. 서버: portable-worker/gate, 신규 portable-issue. 안내: ISSUE_AI_SETUP, 이 문서, PORTABLE_AI_ACCEPTANCE/CODEX_PROGRESS/AGENTS.

게시 경로는 **GitHub 연결 도구를 통한 게시**입니다. 최신 main을 다시 비교하고 이번 검증 파일만 같은 이력을 이어 게시합니다. 자기 자신의 commit SHA를 넣기 위한 반복 커밋은 만들지 않으며 최종 응답에 ID를 보고합니다. 연구 파일은 수정하지 않습니다.

## 공식 API 근거

[Gemini 구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output)의 responseMimeType/responseJsonSchema를 사용하며, JSON 구조 보장이 내용의 정확성을 보장하지 않으므로 별도 검증합니다. [Gemini 안전 안내](https://ai.google.dev/gemini-api/docs/safety-guidance)의 위험 평가 원칙을 참고하되 CARITDA가 진단·안전 인증을 받은 것으로 표현하지 않습니다. 문서 직접 fetch가 text/markdown 처리 오류를 반환한 부분은 공식 검색 결과와 기존 검증 API 형식을 함께 확인했습니다.
