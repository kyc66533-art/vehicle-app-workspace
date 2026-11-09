# CARITDA Portable AI — 구현·인수 기록

2026-11-10 00:03 +09:00, Asia/Seoul. 기준 main `b156648ab31fa615b2a26c8cd819db42c1bd0154`. Aside의 [통합 조사](research/ASIDE_CARITDA_PORTABLE_AI.md), [이전 Gemini 오류 수정](GEMINI_RESPONSE_FIX.md)을 읽고 구현했습니다. 소스 게시와 서버 배포·실모델·PC B 검증은 각각 별도입니다. 최종 게시 SHA는 완료 응답에 기록합니다.

## 현재 결론

**Firebase는 실제 Pleos TEST 환경에서 동작합니다. 정식 인증 Worker와 일반 앱 흐름을 구현했지만, 새 Worker 배포·기기 등록·Gemini 왕복·PC B는 아직 미검증입니다. 실제 Gemini 요청은 이번 작업에서0회입니다.** 과거 Google 직접 API 성공이나 로컬 Mock를 새 일반 앱의 AI 성공으로 바꾸어 보고하지 않습니다.

## 실제 구현

- Auth24.2.0 직접 의존성, 공개 JSON을 package별 `FirebaseOptions` 리소스로 주입합니다. JSON 원본은 Git 제외입니다. 프로젝트/패키지가 없거나 다르면 차단하고 설정값을 만들어 넣지 않습니다. 일반/TEST는 서로 다른 Firebase Android App ID·SDK 세션입니다.
- `FirebaseSessionProvider`: 실제 SDK 로그인, 초기 세션 복원 대기, ID 토큰 자동 캐시/만료 갱신, 명시적 갱신 시험, 로그아웃. UI에는 비밀번호·JWT·UID 원문을 표시하지 않습니다. 별도의 공통 비밀 토큰/refresh-token 복사 저장을 하지 않습니다.
- `DeviceKeyStore`: Android Keystore P-256, 공개 JWK/RFC7638, 엄격 DER↔P1363 변환, 12필드 길이 접두 증명입니다. 기존 키를 임의로 재생성하거나 PC B로 복사하지 않습니다. 하드웨어 attestation은 미구현입니다.
- Worker의 JWT 검증·UID 허용/재인증 기준·기기 등록·nonce/서명·현재 동의·중복 작업·UID/기기/일/전역/동시성 원자 예산을 구현했습니다. 동일 기존 DO/namespace/객체를 사용하고 `budget`을 그대로 둡니다. 이전 사용량 체크포인트+v2 시도가 합계 상한에 포함됩니다. 새 허용량 기본0, 모델 OFF입니다.
- 공급자 전송 전 `RESERVED → DISPATCHING` 소유권을 영속화합니다. 모델 전송은 transaction 밖에서1회만 수행합니다. 전송 결과가 불명확하면 UNKNOWN·비용 예약·동시성 슬롯을 유지합니다. 완료 본문은 fresh READ 서명이 있어야 재조회합니다. 원장 초기화/환불/자동 모델 재시도가 없습니다.
- 일반 앱의 기존 분석 화면에 **AI로 쉽게 설명**을 추가했습니다. 최초 로그인→전송 안내/동의→기기 등록 후 요청합니다. 이후 같은 목적/모델/항목 범위에는 체크박스를 반복하지 않습니다. 기존 로컬 전송 미리보기와 TEST 합성 진단은 별도로 보존했습니다. 홈/차트/SDK 수집 디자인을 재작성하지 않았습니다.
- `PortableAnalysis`는 기존 `VehicleAnalytics`와 `AnalysisExplanation`을 사용하고, `PortableAiClient`는 기존 `AiRelayClient`의 크기 제한 HTTPS 전송을 재사용합니다. endpoint/model/필드를 고정하고 숫자·facts/hash/action을 재검사합니다. 기존 nested JSON 문자열 동등 비교는 재귀 typed 비교로 개선했습니다.
- 현재 허용 입력은 최근 유효한 **Pleos 에뮬레이터 SOC 요약**입니다. 최신/최소/최대/관측 평균/비교 가능한 변화·표본·상대시간·품질 표시 수/구간 수만 보냅니다. 품질 표시 수는 중복 가능한 검사 표시 합계이지 제외된 원본 행 수가 아닙니다. 실차·증상 원문·이름/VIN/위치/원행ID/절대시각 전송은 막았습니다. SDK 원본은 바꾸지 않습니다.
- 결과/동의/등록/미완료 intent는 별도 AES-GCM/Android Keystore sidecar에 저장합니다. Firebase 자격증명은 SDK만 관리합니다. 다른 계정에는 이전 AI 결과를 표시하지 않고 비가시/안전 제한/취소 시 연결과 늦은 표시를 막습니다. 동의 철회는 로컬과 서버 기기 동의를 구분하고 이미 전송된 자료의 취소라고 표현하지 않습니다.
- `AuthCompatibilityActivity`는 TEST 전용입니다. 일반 AI 화면에는43자리 토큰·provider key·Cloudflare 설정란이 없습니다. provider HTTP/형식/STOP/안전/숫자/행동 오류는 실패로 처리하고 실제 provider 응답 검증 성공만 MODEL입니다. 자연어 전체의 사실성을 완벽히 보증하는 필터라고 주장하지 않습니다.

## 실제 환경과 문제 해결

- Windows11, Pleos Connect2.1.0/API34, emulator-5554, 실제 Android user10. Kotlin2.2.21/AGP8.10.1/Gradle8.11.1/Build-Tools35.0.0/Vehicle2.3.2를 유지했습니다. 다른 PC의 user/serial을10/5554로 고정하지 않습니다.
- Firebase25.0.0의 Kotlin metadata2.4.0을 현재 컴파일러가 거부했습니다. SDK/Kotlin 전체 업그레이드 대신 공식24.2.0을 선택했고 실제 AAR 다운로드와 APK 포함을 확인했습니다.
- Codex Maven/npm 네트워크 제한 때문에 사용자가 일반 Android Studio/PowerShell에서 의존성을 내려받았습니다. 초기 getsockopt·테스트 AndroidX 미캐시·Lint artifact 미캐시는 과거 실패로 보존합니다. `.ps1` 정책이나 sandbox를 해제하지 않았습니다.
- 첫 로그인 시험은 SDK 초기화/완료를 충분히 기다리지 않아 실패했습니다. `AuthStateListener`, SDK idle 대기·재실행 검사를 수정했습니다. 당시 토큰 오류 원문을 수집하지 않아 모든 실패를 특정 공급자 원인으로 단정하지 않습니다. 사용자가 다시 로그인한 후 실제 성공을 확인했습니다.
- Lint의 기존 화면 설정 버튼 들여쓰기 오류1개를 수정했습니다. 규칙을 끄거나 baseline으로 숨기지 않았습니다. 최종 빌드/Lint 상태는 아래의 검증 결과를 따릅니다.
- 사용자가 생성한 Worker 번들에서 Cloudflare의 세 번째 `ExecutionContext`를 시험용 인증 함수로 오인할 진입점을 발견했습니다. 소스와 생성 번들의 진입점 한 줄을 함께 수정하고 **동일64개 검사를 소스와 최종 번들에 각각 실행**했습니다. 번들에는 MIT 고지도 포함했습니다. esbuild의 sandbox 상위 폴더 Access denied를 우회하지 않았습니다.

## 검증 결과

| 구분 | 실제 실행 결과 | 한계 |
|---|---|---|
| Firebase 실제 로그인 | 사용자 TEST 입력 뒤2 assertion 통과 | 일반 앱의 별도 최초 로그인은 아직 필요 |
| TEST 세션 복원/ID 토큰·명시 갱신 |4 assertion 통과, 교체 설치 후에도 재확인 | Worker의 JWT Google 인증서 네트워크 왕복과 별개 |
| Auth/Keystore 로컬 |20 native + 재실행2 + Node WebCrypto4 통과 | 서명 fixture 공개키만 확인, 서버 등록 아님 |
| Worker v2 |64 offline RSA/ECDSA/transaction/provider fixture 통과 | Google 실제 key 회전/Cloudflare DO/실모델 아님 |
| 배포할 번들 |동일64 검사 통과, 소스 검사와 중복 집계하지 않음 | 외부 배포 아님 |
| 앱 요약/응답/암호화/재시도 |26 native assertion 통과 | fixture 분석과 provider 없는 transport Mock |
| Pleos 실제 새 View 화면 |4 assertion 통과 | 정상 모델 결과·실제 일반 사용자 이해도 시험 아님 |
| 기존 Gemini 계약/UI |86 assertion 통과 | 전체 기존 회귀 수를 대신하지 않음 |
| 기존 분석 계약 |24 assertion 통과 | 원본·기본 DB 초기화 없는 계약 검사 |
| Gradle/최종 APK/Lint |Debug/validation/androidTest/Release 빌드·Lint 통과,146 tasks |Lint0오류/50경고. testDebugUnitTest는 NO-SOURCE이며 단위검사 통과 건수로 세지 않음 |
| 일반 앱 교체·실행 |같은 인증서 확인 후 `install -r --user 10` 성공·화면 확인 |일반 앱 UI 자동검사 없이 기존 운전자 선택 화면을 확인 |
| 업데이트 후 실제 SDK |SOC100%·거리0.0km 수신·저장, 연료 UNSUPPORTED |Pleos 에뮬레이터이며 실차·전체 신호 성공 아님 |
| 실제 Gemini/Worker v2/PC B |미검증·0회 호출 | 관리자 배포/인가/정식 예산·해당 모델 요청 승인 필요 |

기존369 assertion 전체를 새 코드의 통과로 재사용하지 않습니다. 기존 suite의 `ValidationEnvironment.prepare`는 기본 validation 관측 DB를 초기화하므로 이번에는 실행하지 않았고, 기존 SDK 관측을 보존하는 fixture/계약/별도 신규 검사를 수행했습니다. 검사 수는 PA/PB 인수기준 수와 다릅니다.

## 데이터 보존

- `.tools/portable-ai-backup/20261109-220450/`에 일반/TEST의 DB·prefs·files를 비공개 백업했습니다. **Firebase SDK 세션이 포함될 수 있으므로 백업을 GitHub/PC B에 보내지 않습니다.** 처음 tar는 없는 `no_backup` 디렉터리 오류가 섞여 전체 아카이브 검증에 실패했습니다. 복구에 사용하지 않았으며, 추출된10DB의 원본 행·schema/무결성을 확인하고 실제 존재하는 디렉터리만 담은 새 `*-verified.tar`의 목록 검증에 성공했습니다.
- 작업 중 확인한 일반 앱은 문제23/이벤트92/운전자10/초안0입니다. 이 숫자는 현재 실측값이지 정책상 고정값이 아닙니다. 원본10DB의 모든 기존 행 보존·integrity/FK 검사를 통과했습니다. 설치 전 비교에서 일반5DB는 추가 행0이었으며, 최종 실행 후 정상 SDK 추가분은 아래에 따로 기록합니다. validation에는 기존 실제 SDK 생명주기 조회 등 새 행이 있었으며, 이를 일반 사용자 DB에 복사하지 않았습니다.
- 신규 자동화 분기는 fixture 초기화 전에 validation 패키지를 확인합니다. 인증/암호화 시험은 고유 이름 sidecar와 키만 추가하며 기본 사용자 DB를 삭제하지 않습니다. 일반 앱 대상 계측·AVD 초기화·앱 삭제·키 삭제·원본 관측 교체는 하지 않았습니다.
- 최종 일반 앱의 기존/신규 APK 인증서가 같음을 확인한 후 교체 설치했습니다. 설치 직전 새 전체 tar 검증 성공, 설치 직후 DB5개가 바이트 동일했습니다. 앱 재실행 후 기존 운전자 선택 화면을 실제 확인했고, SOC100%/거리0.0km/연료 미지원의 새 SDK 조회3행과 정상 파생 스냅샷1행·분석2행이 추가됐습니다. 기존 문제23/이벤트92/운전자10/초안0 및10DB 전체 기존 행·스키마·user_version·integrity/FK가 최종 백업에서 보존됐습니다. 신규 실제 SDK 조회를 합성 검사 기록으로 혼동하지 않습니다. 일반 앱의 실제 로그인/AI 결과 화면은 아직 사용자 조작·서버 설정이 필요합니다.

## PA01–PA30 실제 판정

‘로컬 통과’는 원격/실모델 합격이 아닙니다. 부분 항목의 남은 조건을 생략하지 않습니다.

| ID | 판정 | 증거/남은 작업 |
|---|---|---|
| PA01 | 통과·현재 데이터 |10DB 원본·schema/버전/무결성, 같은 signer 교체 직후5DB 바이트 동일 |
| PA02 | 미검증 |로컬 release/contract 준비, 원격 배포 버전 확인 필요 |
| PA03 | 통과·실제 Pleos TEST |Auth24.2.0 로그인·복원·ID token 갱신 |
| PA04 | 부분 |일반 앱 UX 구현, 실제 등록/버튼→모델 왕복 필요 |
| PA05 | 부분·로컬 통과 |RS256/위조/만료/claim fixture, Google 실제 키 왕복 미검증 |
| PA06 | 부분 |cache/max-age/unknown kid cooldown 구현, 실제 회전/망 장애 미검증 |
| PA07 | 부분·로컬 통과 |UID/기기/본문/proof 거부, 배포 후 실제 JWT 필요 |
| PA08 | 부분·로컬 통과 |실제 Keystore↔WebCrypto 증명, Worker 실제 등록 미검증 |
| PA09 | 부분 |매 요청 UID enable/after·기기/동의 검사, 관리자 기기 폐기 UI 미구현 |
| PA10 | 부분 |SDK 실제 갱신 통과, Worker401 후 별도 강제 갱신 재시도 미구현·재로그인 안내 |
| PA11 | 부분 |지속 동의·payload별 고정 hash·철회/late-write 차단, 원격 철회 미검증 |
| PA12 | 부분·로컬 통과 |Keystore/AES/백업 제외·비밀번호/JWT 비표시, 외부 운영 보안 검수 별도 |
| PA13 | 부분 |최소화/실차 전송 차단, 실제 시험 전 데이터 범위 동의 필요 |
| PA14 | 부분 |실SDK 기존 분석 읽기 경로 구현, 아직 서버 전송하지 않음 |
| PA15 | 부분·로컬 통과 |타입/범위/미지원/size/고정 model·origin 검사 |
| PA16 | 부분·로컬 통과 |Mock origin/숫자/hash/이력/행동/finish 검사, 실모델 응답 없음 |
| PA17 | 통과·로컬 |재귀 typed 동등성/정확 UTF8 hash, 키 순서·18/18.0 구분 처리 |
| PA18 | 부분·로컬 통과 |기존3회 fixture+v2합계5, 실제 원격 잔액은 미조회 |
| PA19 | 부분·로컬 통과 |단일 DO transaction 동시 예약, 실제 Cloudflare 경합 미검증 |
| PA20 | 부분·로컬 통과 |동일 intent/operation 재송·다른 본문409, 실제 회전 중 망 유실 미검증 |
| PA21 | 부분·로컬 통과 |CAS/UNKNOWN/중복 모델0·ISSUED 복구, 실제 crash/DO alarm 미검증 |
| PA22 | 부분 |입력/출력·고정 모델·시도 상한, 금액 hard cap·실제 비용 승인은 없음 |
| PA23 | 부분 |형식/인증/한도/망/timeout fixture, 실제 각 서비스 오류 미검증 |
| PA24 | 부분 |비가시·UXR 차단 구현/늦은 저장 방지, 전체 회전·분할·주행 시험 미완료 |
| PA25 | 부분 |기존 디자인/76dp/TEST View 확인, 두 테마·200%·rotary·고령 사용자 미검증 |
| PA26 | 미검증 |실제 Gemini 호출0회 |
| PA27 | 미검증 |실제 다른 PC·정식 서명 APK E2E 필요 |
| PA28 | 부분 |기존 signer로 실제 일반 앱 교체·기록 유지 성공, release signer 전환은 별도 |
| PA29 | 통과·로컬 |jose6.2.16 MIT 고지·lock, 연구/OEM 소스 무단 복사 없음 |
| PA30 | 부분 |실제/Mock/미검증 문서화, 서명/서버/PC B 완료 전 제출 가능이라고 하지 않음 |

## PC B 설치·시험과 PB01–PB18

현재 **실제 PC B에서 실행한 항목은 없습니다.** 아래는 준비 상태이며 통과가 아닙니다.

- PB01/02: 소스 빌드와 APK 설치 절차 분리, 고정 Kotlin/Gradle/SDK/Auth/jose·package-lock 준비. 소스 빌드 시 Firebase 프로젝트의 공개 Android 설정은 개발자가 별도 제공해야 합니다.
- PB03/05/17: 정식 release signer 관리·SHA256/cert/versionCode/debuggable 검증 필요. 기존 PC A가 debug signer이면 새 release signer로 교체하지 않습니다. 설치 실패 때 uninstall/clear-data/강제 downgrade를 하지 않습니다.
- PB04/06/07: PC B 지원 Pleos AVD·Vehicle Service·인터넷/TLS, 같은 HTTPS Worker release/인가·예산 확인 필요. PC B에 Worker나 Gemini 키를 설치하지 않습니다.
- PB08/09/10: 승인된 계정 최초 로그인→B 자체 Keystore 기기 등록→동의. A의 SDK 세션/DB/키를 복사하지 않습니다. 같은 계정이라도 일반/TEST/각 PC의 등록 기기는 별개이며 관리자 device 한도에 포함됩니다.
- PB11/12: B에서 SOC Capability/유효값·시각·출처를 실제 수신한 뒤 B 요약으로 분석합니다. A의 값이나 TEST fixture를 fallback으로 넣지 않습니다.
- PB13/14: 첫 설정 후 분석의 AI 버튼1회→실제 MODEL/hash/facts 표시를 확인해야 합니다. 현재 이 E2E는 미검증입니다.
- PB15/16/18: 계정/기기 제한·오프라인·취소·한도·UXR·확대글꼴·심사자 계정 준비를 별도 검수합니다. Release 서명·승인 예산·실모델 없이 완료로 표시하지 않습니다.

설치 담당자는 ADB로 serial/current user와 기존 signer를 확인한 뒤 `adb -s <serial> install --user <current-user> -r <approved-signed.apk>`를 사용합니다. 이는 사용자 매 AI 요청 절차가 아닙니다. 일반 이용자는 **차량 정보 → 차량 데이터 분석 → AI로 쉽게 설명**만 이용합니다.

## 다음 승인·사용자 설정

[관리자 단계별 설정](../infra/gemini-relay/PORTABLE_SETUP.md)을 따릅니다. (1) 최종 번들·모델 OFF Worker 교체 승인, (2) Firebase Users UID를 Worker 허용 정책에만 등록, (3) 기존 BUDGET 유지·모델 OFF로 기기 서명/잔액 조회, (4) 실제 잔액 확인 후 요청 횟수·해당 에뮬레이터 요약/비용 승인, (5) 모델 ON·명시적 요청·실제 응답 검수 순서입니다. 비밀번호·provider key·ID token을 채팅이나 GitHub에 보내지 않습니다.

사용자는 **모델 OFF Worker 교체·Firebase UID 인가/기기 등록·사용량 조회만 승인**했습니다. 대시보드 수동 실행을 안내하고 완료를 기다립니다. Gemini 활성화/실제 호출/추가 모델 예산은 승인받지 않았습니다. 현재 실제 원격 잔액을 모르므로 추가 허용량을 임의로 넣지 않았습니다. 서명된 제출용 APK, 실제 PC B, 영상/기획서·심사자 접근 준비는 별도 남은 작업입니다. 저장소 공개·심사자 초대·외부 제출은 실행하지 않았습니다.

## 현재 로컬 산출물

| 파일 | SHA256 | 상태 |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `9DC392ACC8984397D9B89A5791E3B11FDBDDD01AAABBA3EB15519F7C2B012AC7` |기존 debug signer, 일반 앱 교체 성공 |
| `app/build/outputs/apk/validation/app-validation.apk` | `D918DAD057B2F37E4A8C9F3E353BCD59BC7E293FC6EF015451021C91F0FF2CEC` |TEST 교체·새 검사 실행 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `0E15934C0ED89A15DC82F372BAFC144E8E28FA1D8E65B1B167DAE2FCCA646563` |Release 빌드 성공·미서명·배포용 아님 |
| `infra/gemini-relay/.bundle/portable-worker.mjs` | `6BBF958083E40C8A12727F35752CB1454A66F2765A9BD8CD23FD32182FE9765D` |최종 진입점/MIT 포함·64 offline 검사 통과·배포 대기 |

APK 세 경로는 `NextDrive-Pleos` 기준입니다. APK/개인 설정/백업/검사 로그는 이번 GitHub 게시 대상이 아닙니다. 새 release 개인키를 임의로 생성하지 않았습니다. 현재 설치된 debug와 서명이 달라지는 정식 release로 업데이트할 때 기존 앱 삭제를 요구하지 않습니다.

## 생성·수정 파일 묶음

- 앱: `app/build.gradle.kts`, 일반/TEST Manifest, 백업 제외 XML2개, `MainActivity.kt`, `PortableAiActivity.kt`, `ai/AiExplanation.kt`, `ai/AnalysisExplanation.kt`, `ai/PortableAnalysis.kt`, `ai/PortableAiClient.kt`, `ai/PortableAiStore.kt`, `ai/auth/FirebaseSessionProvider.kt`, `ai/auth/DeviceKeyStore.kt`, TEST `AuthCompatibilityActivity.kt`.
- 검사: `PortableAuthChecks.kt`, `PortableAiChecks.kt`, `MvpScenarioInstrumentation.kt`, Worker `test/portable-security.mjs`, `test/verify-device-vector.mjs`, `test/bundle-portable.mjs`.
- 서버: `src/index.ts`의 cutover 보호, 신규 `portable-common.ts`, `firebase-token.ts`, `portable-analysis.ts`, `portable-gate.ts`, `portable-worker.ts`; 의존성/lock/TS 설정, 두 Wrangler 진입점, `THIRD_PARTY_NOTICES.md`, `PORTABLE_SETUP.md`, 기존 README 우선 안내.
- 지침/기록: 루트 `AGENTS.md`, `docs/CODEX_PROGRESS.md`, 이 인수 보고서. 기존 연구 문서는 수정하지 않았습니다. 공개 Firebase 설정 JSON2개는 로컬에만 준비했고 Git 제외 상태입니다.

## 공식 근거

- [Firebase Android Play services 요구](https://firebase.google.com/docs/android/android-play-services), [이메일 로그인](https://firebase.google.com/docs/auth/android/password-auth), [세션 초기화와 사용자 상태](https://firebase.google.com/docs/auth/android/manage-users)
- [Firebase 릴리스](https://firebase.google.com/support/release-notes/android), [ID 토큰 검증 조건](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [세션 폐기 지연](https://firebase.google.com/docs/auth/admin/manage-sessions)
- [Cloudflare storage transaction](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/), [alarm](https://developers.cloudflare.com/durable-objects/api/alarms/), [jose 라이선스](../infra/gemini-relay/THIRD_PARTY_NOTICES.md)
