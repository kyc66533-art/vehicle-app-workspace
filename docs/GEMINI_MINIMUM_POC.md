# Gemini 최소 연결 검증 — 배포/실호출 대기

2026-11-09 Asia/Seoul. 기준 main `1ef6bbdd4fb07d8c2e2407ca79496cc54fc3d743`. 모델은 사용자가 지정한 `gemini-3.5-flash-lite`, 대상은 기존 `caritda-ai-relay-dev`입니다.

## 현재 판정

사용자는 준비된 소스/46개 검사 결과를 본 뒤 **‘보류 — 검증된 로컬 코드만 게시’**를 선택했습니다. 따라서 이번에는 기존 Worker를 교체하거나 DO/인증/계정 설정·실제 모델 호출을 진행하지 않습니다. 아래 설정 안내는 향후 별도 승인을 받은 뒤 사용할 절차입니다.

**부분 구현/로컬 검증 완료. 실제 Worker 배포·Gemini 응답·TEST 화면 실모델 표시는 아직 미검증입니다.** 사용자는 기존 Worker에 Gemini 키만 있고 앱 인증은 아직 없다고 확인했습니다. 기존 PowerShell의 모델 성공은 사용자 확인 결과이며 이번 Codex의 Worker 경유 성공으로 바꾸어 기록하지 않습니다.

최신 main과 로컬99개 공유 파일 SHA가 일치했습니다. `docs/research/**`와 다른 변경을 보존했습니다. Windows의 Worker HEAD는 ‘원격 서버에 연결할 수 없습니다’로 실패했습니다. 반복 권한 우회/보안 해제는 하지 않았습니다. Cloudflare 관리 도구·로컬 Node/npm/tsc/Wrangler는 없어서 새 프로그램을 설치하거나 서버를 배포하지 않았습니다.

## 실제 구현

- Cloudflare용 JS 문법 호환 TypeScript Worker와 wrangler 설정. provider key는 `env.GEMINI_API_KEY`에서만 읽고 요청·소스·APK·로그·응답에 넣지 않습니다. 현재 등록된 Secret을 조회하지 않았습니다.
- 고정 합성 SOC80→60→18/20%미만만 허용하는 폐쇄된 입력 계약.43자 임시 앱 토큰·24시간 이내 만료·POST/JSON·실제4KiB·Origin/쿼리/압축·추가 필드 검증. 누락 설정은 fail closed입니다.
- 전역 고정 ID의 SQLite-backed Durable Object transaction으로 분당2회/누적5회 상한·UUID 중복 거부. 실패도 횟수를 소비하고 자동 재시도하지 않습니다. 누적 상한은 분/날짜/토큰 변경으로 자동 초기화되지 않습니다. 메모리/KV만으로 전체 상한을 주장하지 않습니다.
- `/health`와 인증된 `/v1/preflight`는 모델을 호출하지 않습니다. 실제 DO binding과 남은 횟수를 확인한 뒤 명시적 모델 요청 경로를 사용합니다. 배포 기본값은 꺼짐/만료 미설정입니다.
- 기존 `AiRelayClient`, `AnalysisExplanation`, `VehicleAnalysis`/`VehicleAnalytics.id`를 재사용했습니다. 숫자/facts/hash/요청 ID/모델 ID/출처를 검증하며 응답 지연·한도·인증·형식/근거 오류를 처리합니다. 안전 필터를 전체 모델 의미 안전성 인증으로 표현하지 않습니다.
- `[TEST]` 차량 정보에 진입 버튼 하나와 별도 `GeminiPocActivity`를 추가했습니다. 일반 앱 manifest에는 이 Activity가 없습니다. 기존 홈/그래프/DB·자동 분석·SDK·권한을 변경하지 않았습니다.
- 합성 DTO는 메모리에만 있으며 `TEST_ONLY`/`synthetic=true`입니다. 원관측/스냅샷/사용자 DB에 넣지 않습니다. 실제 SDK 값이라고 표시하지 않습니다. Google에도 고정 합성값과 기준만 전달합니다.
- 앱 토큰은 Google 키와 다르며 입력 저장/자동완성/스크린샷을 막고 메모리에서만 사용합니다. 전송/화면 종료/취소 시 입력을 지우고 동의를 취소합니다. 중단 뒤 늦은 응답은 표시하지 않습니다. 이미 서버에 도달한 요청의 비용까지 취소됐다고 보장하지 않습니다.
- 실제 모델 경로가 HTTP/계약 검증을 통과해야만 ‘Gemini 실제 모델 응답’으로 표시합니다. preflight는 ‘모델 호출 없음’, 로컬 규칙은 별도 문구입니다. Mock 응답을 실제 화면에 주입하지 않습니다.

## 로컬 검증 결과

- `assembleDebug/assembleValidation/assembleValidationAndroidTest` 성공. 마지막 앱 변경 빌드 `gemini-build-4.txt`:1m39s/93작업10실행·83up-to-date. 테스트 도구 수정 뒤 `gemini-build-5.txt`:57s/93작업4실행·89up-to-date.
- validation APK SHA256 `E223734197C6CC360CB83DE3C397C2A397AB811BE8702FE7F3D696999A5D7389`. validation와 test APK를 데이터 유지 `install -r --user 10`로 교체했습니다. 일반 앱은 교체하지 않았습니다.
- `CARITDA_GEMINI_LOCAL_PASS checks=46`. native UI/합성/facts8개와 Worker Web API38개입니다. Worker의 실제 소스를 WebView131 Web API 엔진에서 실행했고 **provider fetch와 DO 저장소만 mock**입니다. 무인증/오인증/만료/미설정/크기·필드·출처/동시성·누적/중복/429/공급자 인증/잘못된 JSON/위험·숫자 문구/네트워크·실제 로컬12초 타이머/비밀정보 비반환을 검사했습니다.
- 이 결과는 Cloudflare의 실제 SQL namespace/분산 배포/실제 Gemini 호출을 대신하지 않습니다. Node/tsc/Wrangler/dry-run은 미실행입니다. 배포 전 Cloudflare Builds 또는 승인된 PC CLI에서 check/dry-run이 성공해야 합니다.
- 첫 빌드 검사에서 테스트 latch의 Long/Int 비교 오류를 수정했습니다. 로컬1은 브라우저 Request가 Origin을 제거하는 테스트 어댑터 차이, 로컬2는 비가시 WebView callback 대기 문제로 실패했습니다. 수신 헤더 어댑터와 활성1px 테스트 WebView를 수정해 로컬3을 통과했습니다. 서버 Origin 방어를 제거하지 않았습니다. 실패 로그도 보존합니다.
- 기존369개 전체 회귀는 이번 최소 PoC에서 재실행하지 않았습니다. 기존 분석/저장/주행 제한 구현을 재작성하지 않았고, 새 기능의 범위·관련 계약·APK 분리를 검사했습니다. 일반 사용자가 실제 모델 화면을 검수한 결과와 자동 검사를 혼동하지 않습니다.

## 원본 보존

`.tools/gemini-poc-backup/20261109-010917/`에 일반/validation 전체 databases/shared_prefs/files를 새로 백업했습니다. 일반5DB는 검사 전후 바이트 동일입니다. 문제 DB SHA256은 `A906AA7FDEFF00A9AC444A8DB306EF06791D0DD1A0B461073932277DBAC1C92C`입니다. Gemini 검사는 fixture 초기화보다 앞의 validation 전용 분기이며 기존 default DB를 삭제하거나 테스트 프로필을 생성하지 않습니다. AVD/백업/기존 SQLite·Driver/초안/관측·분석 이력을 초기화하지 않았습니다. 새 WebView 테스트 캐시와 시험 소스 파일만 validation 환경에 추가됐습니다.

## 사용자 승인/설정이 필요한 다음 단계

전체 클릭 경로·명령은 [중계 설정 안내](../infra/gemini-relay/README.md)에 있습니다. 기존 Worker 교체, SQLite DO 생성, 새 앱 시험 토큰/만료 설정, 최대5회 실제 모델 요청에 대한 승인이 필요합니다. 무료 사용 가능 여부/계정 결제 조건은 Codex가 확인하지 못했습니다. 키·Cloudflare 계정 토큰·임시 앱 토큰을 채팅에 보내지 마세요.

1. 승인 후 Cloudflare Builds(PC 설치 없음) 또는 승인돼 준비된 Node/Wrangler로 check/dry-run → 기본 꺼짐 상태로 배포합니다. 기존 코드/버전을 먼저 보존하고 `GEMINI_API_KEY` Secret은 유지합니다.
2. `TEST_APP_TOKEN` Secret·2시간 권장 만료·`POC_ENABLED=false`를 설정합니다. 무인증 preflight가401인지 확인합니다.
3. TEST 앱에 **앱 토큰**을 직접 입력하고 배포/합성 전송 동의 후 ‘보안 연결 확인 · 모델 호출 없음’을 누릅니다. 인증·실제 binding/남은5회·활성화false를 확인합니다.
4. 그 다음만 `POC_ENABLED=true`를 승인해 설정하고 토큰을 다시 붙여넣어 ‘실제 Gemini 설명 요청’을 한 번 누릅니다. 실제 응답/왕복시간·오류 여부를 확인합니다. 실패 시 원인부터 확인하고 자동 반복하지 않습니다.
5. 시험 후 비활성화/토큰 폐기합니다. API 제공자 키를 앱에 넣거나 Worker를 인증 없는 공개 프록시로 사용하지 않습니다.

현재 성공 기준 판정: 코드상 키 분리·로컬 무인증 거부·일반 DB 보존은 확인. **원격 Worker401/실제 Gemini 응답/TEST 실제 표시/원격DO 상태는 배포·인증 설정 전으로 미검증**입니다. Cloudflare 연결·계정 설정 없이 임의로 성공 처리하지 않습니다.

검증한 소스와 비밀정보 없는 문서만 최신 main을 잇는 **GitHub 연결 도구를 통한 게시**로 반영합니다. 최종 커밋은 완료 응답과 [문서 커밋 기록](https://github.com/kyc66533-art/vehicle-app-workspace/commits/main/docs/GEMINI_MINIMUM_POC.md)에서 확인합니다. DB/키/백업/로그/개인 화면/APK는 게시하지 않습니다.
