# Gemini 응답 오류·TEST 인증 개선

2026-11-09, Asia/Seoul. 기준 GitHub main: `99547db7a2e2c73c0146685134849c430b82859a`. 원격128개 blob 중 로컬 공유111개가 작업 시작 시 일치했습니다. 로컬에 없는 연구17개(HINT D6 자료 포함)를 그대로 보존합니다. 최종 게시 결과는 완료 응답/진행 기록에 남깁니다.

## 확인된 원인과 아직 확정할 수 없는 원인

- 기존 `RelayFailure`는 `MODEL_*` 오류를 모두 같은 문구로 표시했습니다. 사용자가 본 문구는 이 서버 오류 분기를 가리키며, 그 자체로 앱 facts 검사 실패를 증명하지 않습니다. 공급자400/JSON 오류/MAX_TOKENS/안전 필터/보수적인 문구 거부 등을 구별할 수 없었습니다.
- 과거 Worker는 비정상 HTTP 본문과 finishReason을 보존·전달하지 않았습니다. 따라서 현재 자료만으로 당시 공급자 오류를 확정하지 않습니다. 과거 원문을 복구하려고 키·토큰·응답을 조회하지 않습니다.
- 요청의 `temperature:0`은 [Gemini 3.x 공식 기본 샘플링 권장](https://ai.google.dev/gemini-api/docs/whats-new-gemini-3.5?hl=en)에 맞춰 제거했습니다. Enterprise의 동일 모델 문서에는 샘플링 사용자 값 미지원이 명시되어 있지만, 이를 Developer API에서 실제400이 발생했다는 증거로 사용하지 않습니다.
- 출력256토큰은 짧은 한국어 JSON에도 종료 제한 후보입니다. 512로 제한된 여유를 추가하되 실제 잘림 여부는 미확정입니다. [모델](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)과 [GenerateContent API](https://ai.google.dev/api/generate-content?hl=en)의 JSON/종료/안전 응답을 기준으로 검사합니다. 지원 근거가 있는 기존 JSON schema를 임의의 새 API로 교체하지 않습니다.
- 토큰 반복 입력은 `request()`에서 매번 입력을 지우고 `onStop()`에서도 지우던 명시적인 구현 때문입니다. 43자리 형식·배포 승인·합성 전송 동의·ALLOWED·요청 중 여부가 버튼 활성 조건이며 화면에 부족한 조건을 표시합니다.

## 실제 수정

Worker release는 `gemini-response-fix-v2`입니다. 원문/finishMessage/안전 평가 원문/비밀값을 반환하지 않습니다. 오류에는 고정 code, stage, 허용된 provider_status·finish_reason·request_field, HTTP상태·처리ms·차단 여부만 포함합니다. 인증되지 않은 입력을 메시지로 되돌려주지 않습니다. 앱도 다시 허용 목록으로 걸러 표시합니다.

| 코드 | 의미와 다음 행동 |
|---|---|
| MODEL_REQUEST_REJECTED | 공급자400/422; 안전한 요청 필드 힌트·배포 버전 확인 |
| MODEL_NOT_FOUND / MODEL_HTTP_ERROR | 모델404 / 다른 공급자HTTP 오류 |
| MODEL_QUOTA / PROVIDER_AUTH | 공급자429 / 공급자401·403; 자동 재시도 없음 |
| MODEL_TIMEOUT / MODEL_NETWORK | Worker→공급자 시간초과 / 연결 실패 |
| MODEL_OUTPUT_LIMIT | MAX_TOKENS; 안전 차단과 구분 |
| MODEL_SAFETY_BLOCKED | prompt 또는 candidate의 실제 차단 표시 |
| MODEL_NO_CANDIDATE / MODEL_FINISH_REJECTED / MODEL_EMPTY_TEXT | 후보 없음 / STOP 이외 / 답변 없음 |
| MODEL_BODY_INVALID / MODEL_JSON_INVALID / MODEL_SCHEMA_INVALID | 응답 전체 / 답변 JSON / 필드 구조 오류 |
| MODEL_TEXT_INVALID / MODEL_NUMERIC_PROSE / MODEL_UNSAFE_PROSE / MODEL_ACTION_INVALID | 문구·숫자·진단/안전 표현·action 검사 거부 |
| APP_* | 앱 측 연결·응답 포장·동의·origin·hash·facts·문구 검사; 공급자 오류와 분리 |

thought 표시가 있는 parts는 답변 JSON에서 제외합니다. STOP·형식·허용 action·문구를 통과한 공급자 응답만 MODEL로 반환하고 앱이 다시 계약/요청ID/모델ID/facts/hash/동의를 확인합니다. Mock는 native 실제 모델 표시 경로에 주입하지 않습니다. 앱은 preflight release가 v2가 아니면 APP_RELAY_VERSION으로 실제 요청 전에 차단하여 승인된1회를 구형 Worker에 소모하지 않습니다. 필터는 여전히 보수적이므로 ‘고장이 아니에요’ 같은 부정문도 거부하며, 이를 안전성 보증이나 실제 실패 원인으로 과장하지 않습니다.

TEST 개발자 진단 화면에만 선택형10분 메모리 인증을 추가했습니다. 저장/자동 갱신/자동 요청은 없고 프로세스 종료·만료·인증 거부·명시 취소·안전 제한 때 지웁니다. 선택한 경우 화면 이탈/재생성에도 인증만 잠시 유지하며 전송 동의는 취소합니다. 단말을 공유할 때는 시험 인증 지우기를 눌러야 합니다. 서버 만료를 연장하는 기능이 아니며 Java 문자열·네트워크 버퍼의 완전한 메모리 소거를 보장하지 않습니다. FLAG_SECURE·password·no autofill·no saved state를 유지합니다.

## 실제 서비스 인증 방향 — 아직 미구현

일반 앱에 공통 TEST 토큰을 내장하지 않습니다. 인증된 사용자 세션 또는 기기 페어링 뒤 서버가 특정 사용자/기기/audience/권한 범위에 묶인 단기 토큰을 발급하고 서버에서 검증·폐기·사용자별/전역 예산을 적용하는 방향입니다. 갱신 인증은 Android Keystore를 이용한 보호와 별도 계정·동의 설계가 필요합니다. 데이터 미리보기/전송 동의는 인증과 별개이며 키는 중계 env Secret에만 둡니다. 이번에는 계정 서버·키 발급·외부 배포를 구현하거나 승인받은 것으로 처리하지 않습니다.

## 검증과 보호

Web API provider/DO Mock 검사, native 계약/수명주기 검사와 실제 Gemini 응답은 서로 다른 검증입니다. 실제 호출 없이 과거 실패가 해결됐다고 확정하지 않습니다. 일반/TEST 전체 tar와 일반5DB를 `.tools/gemini-fix-backup/20261109-195913/`에 보존했습니다. 일반 앱은 교체·UI 자동화하지 않으며 TEST 기본 DB를 초기화하지 않는 계측 분기로 검사합니다. 기존 DO 클래스 본문/식별자/namespace/migration/예산을 유지하고 실패한 요청도 누적5회에 포함합니다.

- Debug/validation/validationAndroidTest의 offline Gradle 빌드 성공. 최종 앱 소스 빌드 `gemini-fix-build-6.txt`: 1m36s,93작업 중13실행/80up-to-date. 앞선 단계별 빌드도 성공했습니다. 기존 Kotlin deprecation/type-inference 경고는 남아 있습니다.
- Pleos emulator-5554/Android user10에 validation/test만 install-r. **Gemini 로컬86개 = native/계약/수명주기27 + Worker Web API59**. 이전46개를 포함하며 신규40개를 추가했습니다. Worker는 실제 소스의 export 선언만 테스트 포장용으로 제거하여 WebView에서 실행했고 provider/저장소만 Mock입니다. 공급자400/404/5xx·MAX_TOKENS·안전·빈 후보·thought·JSON·스키마·숫자·허위 수리·정책·예산을 구분합니다.
- 기존 분석 계약24개를 재실행하여 통과했습니다. 근거 불일치·동의 취소·Mock 거부·숫자 창작·수리/안전 확정 거부와 시계열 분석/두 테마 대비 회귀를 포함합니다. 새 계측 모드는 default DB 초기화 전에 실행하며 독립 fixture DB만 추가합니다. **총110개**, 기존369개 전체 회귀를 재실행한 것은 아닙니다.
- 일반/TEST 각5개 기존 DB의 전후 SHA256이 모두 일치했습니다. 문제·운전자·이벤트·초안·관측·스냅샷·SOC 사건·분석 이력을 포함한 원본 전체가 바이트 동일합니다. 기존 기록 수를 고정하거나 테스트 데이터로 바꾸지 않았습니다. 일반 APK에는 GeminiPocActivity가 없고 validation에만 exported=false로 존재합니다.
- 추가로 준비된 Node26.13.0/npm11.20.0/로컬Wrangler4.158.0을 확인했습니다. **tsc --noEmit 통과**, Node에서도 실제 Worker 소스/provider Mock59개 통과(앞의59개와 같은 검사이므로 중복 합산하지 않음), 기존 배포 설정의 Worker/BUDGET/migration 동일·vars 없음·keep_vars 검사를 통과했습니다. 도구를 설치하거나 전역 설정을 변경하지 않았습니다.
- Wrangler bundle dry-run은 **`Cannot read directory "../../..": Access is denied.` / `Could not resolve ...src/index.ts`**로 실패했습니다. 인증 없는 health 조회도 EACCES였고 CLI 인증 조회도 `Not logged in`이며 정상 인증을 확인하지 못했습니다. 제한 우회·자동 로그인·실제 배포는 시도하지 않습니다. 사용자 일반PowerShell에서 실행할 최소 명령은 [README](../infra/gemini-relay/README.md)에 있습니다.
- 실제 원격401/preflight/초기 배포 성공은 사용자의 확인입니다. 이번 실행에서는 재배포/실제 모델 호출0회이며 Cloudflare 실제 DO/실제 Gemini 응답/최종 TEST 실제 응답 표시는 미검증입니다. 새 TEST 화면의 모든 화면 크기·실제 주행 전환·고령자 사용성은 별도 미검증입니다.

변경 파일15개: `AGENTS.md`, `docs/CODEX_PROGRESS.md`, 이 보고서, `infra/gemini-relay/{README.md,src/index.ts,test/security.js,wrangler.existing.jsonc}`, main의 `ai/{AiExplanation.kt,AnalysisExplanation.kt,RelayDiagnostics.kt}`, validation의 `GeminiPocActivity.kt`·`PocTokenSession.kt`, androidTest의 `GeminiPocChecks.kt`·`AnalyticsChecks.kt`·`MvpScenarioInstrumentation.kt`. 일반 MainActivity/저장소/분석 엔진/DB 스키마/SDK/권한/AVD/연구 파일은 변경하지 않습니다.

## 승인 이후의 최소 절차

1. Cloudflare → Workers & Pages → caritda-ai-relay-dev → Settings → Builds에서 main 자동 배포 사용 여부부터 확인합니다. 자동 배포 미확인 상태로 GitHub main을 변경하지 않습니다. 연결되어 있다면 이번 코드 교체를 승인하거나 자동 배포를 먼저 중지해야 합니다.
2. 승인 후 기존 Worker만 새 코드로 교체합니다. Deployments에서 기존 버전 확인 후 기존 BUDGET namespace와 Secret을 유지하고 migration을 새로 추가하지 않습니다. wrangler의 기본 꺼짐/만료 vars가 실제 값에 미치는 영향을 확인합니다. 새 키를 채팅/코드/APK에 넣지 않습니다.
3. TEST → 차량 정보 → Gemini 연결 시험에서 임시 앱 인증을 입력하고 원하면10분 기억을 선택합니다. 배포/시험 승인과 합성 전송 동의를 확인하고 **보안 연결 확인 · 모델 호출 없음**을 누릅니다. release v2와 남은 횟수를 확인합니다. preflight는 예산을 예약하지 않습니다.
4. 남은 횟수가0이면 중단합니다. DO 초기화나 namespace 변경으로 우회하지 않습니다. 실제 모델 시험의 별도 승인 후 **최대1회**만 합성 SOC로 요청합니다. 실패해도 다시 누르지 않습니다. 비용 발생 가능성이 있고 출력은 최대512토큰/12초/16KiB, 입력은 고정 fixture뿐입니다.
5. 성공 시 검증된 Gemini 실제 모델 응답 표시를 확인합니다. 실패 시 화면의 안전한 code/stage/HTTP/finish_reason만 공유합니다. 원문·토큰·키를 공유하지 않습니다. 시험 뒤 인증 지우기와 서버 시험 비활성화/인증 폐기를 수행합니다.

사용자가 **GitHub main 자동 배포 연결 없음**과 **Worker 교체·실제 최대1회 승인**을 확인했습니다. GitHub 연결 도구를 통한 소스 게시는 진행하며, 실제 배포는 위 CLI 권한/인증 문제 때문에 사용자 작업이 필요합니다. 승인을 다시 요구하지 않습니다. 사용자 배포 후 TEST에서 임시 토큰 입력→선택적10분 기억→전송 동의→모델 없는 preflight→남은 횟수 확인→승인된 실제 모델1회 순서로 시험합니다. 원격 코드/실응답 성공을 확인하기 전에는 해결 완료로 보고하지 않습니다.
