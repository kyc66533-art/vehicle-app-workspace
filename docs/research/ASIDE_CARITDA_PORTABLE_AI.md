# CARITDA: 차량용 Gemini AI 아키텍처·오픈소스·다중 PC 재현성·사용자 UX 통합 조사

확인일: **2026-11-09, Asia/Seoul**  
날짜 근거: **get_time 실제 반환 2026-11-09 20:59 및 21:33 KST, 세션 생성일11/6과 구분**  
조사 시작 main: **7ca8797f5e262e86a38507fdfbc7e9af0e25e48b**  
게시 전 보존 확인: **동일 main, 기존 기준38개 원문 일치, 보고서만 추가**  
산출물: **docs/research/ASIDE_CARITDA_PORTABLE_AI.md**

## 0. 최종 결론

**가장 현실적인 방법은 C안, Firebase Authentication(이메일/비밀번호 최초 로그인) + 기기 등록 + 기존 Cloudflare Worker + 기존 Gemini Secret을 유지하는 구조다.** 로그인 유지·ID token 자동 갱신은 표준 SDK에 맡기고, Worker는 사용자/등록 기기 검증·정식 예산 원장·실관측 요약 계약을 추가한다. Firebase AI Logic으로 모델 경로 전체를 교체하거나 자체 계정/refresh-token 서버부터 만들지 않는다.

질문에 대한 직접 답:

> **CARITDA를 다른 PC의 Pleos Connect에 설치했을 때, 최초 1회 로그인·기기 등록·전송 동의를 마친 사용자가 AI 설명 버튼 하나로 Firebase ID token을 자동 확보하고, 같은 원격 Cloudflare Worker가 보관 중인 Gemini 인증정보로 실제 모델을 호출하도록 구현하는 것이 가장 현실적이다. PC B에 Gemini 키나 Worker를 새로 만들 필요는 없다.**

이는 **설계 권고**다. 현재 일반 앱에서 그 흐름이 완성됐다는 뜻은 아니다. Firebase Auth의 GMS 비필수는 공식 확인됐지만 이 앱/대상 Pleos에서 실행 검증은 남아 있다. Day1 호환성 관문을 통과하지 못하면 성공으로 발표하지 않는다.

- **현재:** 실제 Pleos SDK/로컬 분석·인사이트, 합성 전용 relay, 안전한 응답 검사·진단, TEST 인증10분 기억은 코드/개발 기록에 있다.
- **미완성:** 일반 앱 실관측 AI, 정식 자동 인증/갱신·기기 등록, 동의 유지, 정식 사용자/기기별 quota, release/PC B 실증.
- **중요 현장 확인:** 공개 GET /health는 응답했지만 최신 소스의 release 항목이 없어 **수정 Worker v2 배포는 확인되지 않았다**. 실제 모델 응답을 이번 조사에서 호출/표시하지 않았다.
- **권한 경계:** 조사만 수행했다. Worker 재배포·모델 호출·키/토큰 조회·DO 초기화·앱/DB/AVD/백업 변경은 하지 않았다. 공개 health만 무인증 GET으로 읽었다. 보고서만 새로 추가한다.

확인 사실 / 설계 판단 / 미검증을 각 절에서 구분한다. 아래 신규 파일·API·예산 숫자·테스트 항목은 구현 요구사항이지 이미 구현된 기능이 아니다.

---

## 1. 기존 구현 현황과 실제 실패 범위

### 1.1 확인한 기준

고정 main의 Worker·Android AI·validation·분석·관측·공통 UI·빌드 설정과 최신 오류/PoC 보고서를 읽었다. 저장소 고정 근거 [R1]–[R30] 및 기존 연구 원문을 보존 기준으로 삼는다.

| 항목 | 확인 사실 | 판정 |
|---|---|---|
| 최신 코드 | main7ca8797, 최신 진행 기록20:30 KST | 코드 게시 확인, 원격 배포와 별개 [R1] |
| 일반 앱 | MainActivity 분석 화면은 ‘실제 AI 미연결’, payload/동의 미리보기만 | **일반 앱 모델 응답 미구현** [R7] |
| Worker | CONTRACT=caritda-gemini-synthetic-v1, MODEL=gemini-3.5-flash-lite, RELEASE=gemini-response-fix-v2 | 최신 소스 [R3] |
| 입력 | TEST_ONLY·synthetic=true·SOC80→60→18 고정 fixture만 검증 | 실제 SDK 요약은 현재 거부 [R3] [R18] |
| 인증 | 서버 TEST_APP_TOKEN과43자 비교, 만료·켜짐·binding 검증 | TEST 임시 인증, 사용자/기기 인증 아님 |
| 예산 | 고정 DO 이름/저장 key, transaction으로 분당2·누적5 예약 | **CARITDA 자체 제한**. Google 기본5회가 아님 |
| preflight | 인증·고정 입력·binding/남은 횟수 확인, 모델 호출/예약 없음 | 연결 확인≠모델 성공 |
| 응답 | origin·model·requestId·hash·facts·action·문구를 Worker/앱에서 검사 | 구조 안전장치, 의미 안전성 인증 아님 [R18] [R19] |
| 진단 개선 | HTTP400/404/429/5xx·MAX_TOKENS·안전·JSON/스키마·앱 검사 구별 | 과거 공급자 실패 원인까지 확정한 것은 아님 [R13] [R20] |
| TEST10분 기억 | PocTokenSession 프로세스 메모리, 고정 TTL, 종료/만료/거부 때 소거 | 영구 세션/자동 refresh 아님 [R21] [R22] |
| 로컬 검증 | 최신 기록은 native/계약27+Worker Mock59=86, 분석 회귀24로 총110 | 실제 모델 검사 아님, 이전369 전체 재실행 아님 [R13] |
| 운영 데이터 | 일반/TEST5개 DB 전후 동일을 최신 작업에서 보고 | 이번 조사에서 DB를 직접 열거나 변경하지 않음 |
| 배포 | 최신 기록은 Wrangler bundle 권한 오류·CLI 인증 미확인, 실제 배포0회 | GitHub 게시만으로 배포 성공 선언 불가 |

기존 직접 Gemini 호출·초기 배포·무인증401·인증 preflight 성공은 **사용자/개발 기록의 보고**다. TEST 실제 모델 설명 화면의 끝까지 성공은 별도 미검증으로 남긴다.

### 1.2 이번 공개 health 관측

2026-11-09 **21:01 KST**, 인증 헤더 없이 GET만 수행했다. /health 소스 분기는 DO 예약·모델을 호출하지 않는다.

~~~json
{"contract":"caritda-gemini-synthetic-v1","auth_required":true,
 "synthetic_only":true,"total_attempt_limit":5,
 "per_minute_limit":2,"enabled":true}
~~~

HTTP200이었지만 **release 필드가 없다**. 현재 source v2는 /health에 release를 항상 넣으므로 공개 응답과 최신 소스 계약이 다르다. 배포 이력/전단 캐시/실제 바인딩까지 계정에서 확인한 것은 아니며, 정확히 v2가 실행 중이라고 볼 수 없다. enabled=true도 키 유효성·남은 예산·모델 성공을 증명하지 않는다.

새 TEST 앱은 인증 preflight의 release=v2를 검사한다. 공개 health 관측만으로 그 preflight를 대신하거나 실모델 시험을 시작하지 않는다. 이번에는 preflight/모델 route·실제 DO 남은 횟수를 조회하지 않았다.

### 1.3 오류 수정의 의미

temperature0 제거·출력256→512·thought 부분 제외는 코드에 반영됐으나 당시 오류의 원인이 그 항목이었다고 입증되지 않았다. 실제 실패를 확정하려면 배포 버전→모델 없는 인증 검사→별도 승인된1회 실제 요청→안전한 code/stage/finishReason 확인 순서가 필요하다. 남은 TEST 예산0이면 중단한다. 이번 조사에는 그 실행 승인이 포함되지 않는다.

---

## 2. 필수10과제 결론과 근거 위치

| 과제 | 조사 결론 | 상세 |
|---|---|---|
| 1 Firebase AI Logic+App Check | 공식 Kotlin/샘플 있음. GMS 비필수 SDK와 Play Integrity GMS 필수를 구분 | §3·§9, [F1]–[F8] |
| 2 Worker+사용자 인증 | Firebase ID token을 서명/claim 검증하고 UID별 인가. Worker는 계속 모델 호출 | §5, [F9] [C1] |
| 3 최초1회 기기 등록 | 서버 registry+Keystore key·challenge, 재설치/새 PC는 새 등록 | §5.3 |
| 4 Keystore·갱신 | C는 Firebase SDK 세션, 기기 키 별도 보호. A의 자체 회전 refresh는 별도 난도 | §5.4, [F10] [P12] |
| 5 모델·키·무료 | 3.5 Flash-Lite stable/무료등급 확인, 개별 key 재사용/실제 quota는 미확인 | §6 |
| 6 정식 quota | 기존5회 보존, 한 권위 DO에서 전역/UID/기기/일별 원자 예약 | §7 |
| 7 Pleos 호환 | Auth/AI SDK의 일반 지원과 Pleos 실행은 별도. Play Integrity를 가정하지 않음 | §4 |
| 8 오류 UX | 사용자 문구와 운영 진단 분리, 무한 retry·수동 서버 설정 요구 제거 | §10 |
| 9 최소화 | 로컬 결정론적 집계만, 개인기록/이름/VIN/위치/원행 식별자는 제외 | §8 |
| 10 코드 재사용 | 기존 분석/DB/UI/relay guard 유지, 합성 제한을 풀기보다 v2 계약 추가 | §11 |

---

## 3. A/B/C 아키텍처 비교와 최종 선택

| 기준 | A: Worker+자체 정식 자동 인증 | B: Firebase AI Logic+App Check | C: Firebase Auth+Worker+Gemini |
|---|---|---|---|
| Pleos | HTTPS/Keystore 기반, 해당 기기 검증 필요 | AI SDK GMS 비필수이나 운영 App Check 경로가 난점 | Auth SDK GMS 비필수, 이메일 로그인/네트워크 실증 필요 |
| 최초/일상 UX | 기기 승인 후 자동 세션, 이후 버튼1회 | App Check 준비 후 버튼, 계정은 별도 | 최초 로그인·등록·동의 후 버튼1회 |
| Android 변경 | 자체 access/refresh·키 보호·회전/유실 복구 구현 | firebase-ai·App Check/provider와 UI 연결 | firebase-auth·등록키, 기존 AiRelayClient 확장 |
| 서버 변경 | 등록·토큰 발급·회전·폐기·인가·quota 모두 직접 | 원모델 relay 대체. 강한 UID/일별 제한에는 추가 서버 필요 | JWT검증·인가/기기 registry·quota. 토큰 발급/refresh 서버는 불필요 |
| 재사용 | 기존 relay/분석 높음, 인증은 신규 | 분석/UI 높음, 기존 Worker 모델 경로 재사용 낮음 | **분석/UI/Worker/Secret/guard 재사용 높음** |
| 무료 조건 | Gemini free/Cloudflare free 조건 각각, 보장 아님 | AI Logic 자체 무료, Developer API free 조건. 기업 backend billing 별도 | 기본 비전화 Auth+기존 Gemini/Cloudflare 조건. Identity Platform 업그레이드 별도 |
| 비용 통제 | 중앙 DO로 강제 가능 | 기본 per-user RPM≠UID별 일별 quota. 직접 경로가 서버 gate 우회 가능 | **모든 모델 호출을 Worker gate로 강제** |
| 안전성 | 자체 refresh 재사용·경합 구현 위험 큼 | App Check≠사용자 인증, debug provider 운영 사용 금지 | 검증된 ID token+등록키+인가·예산. Firebase 상태 폐기는 별도 |
| PC B | 새 키/서버 불필요, 새 기기 승인 필요 | Firebase 프로젝트 동일, App Check verdict/등록 필요 | 새 Gemini 키/서버 불필요, 최초 계정 로그인/기기 등록 |
| 차량 확장 | 소수 승인 기기엔 가능, 계정 운영 직접 확장 | attestation/모델 접근은 편하나 별도 사건/비용 정책 필요 | UID와 차량/기기 연결을 확장하기 쉬움 |
| 예상 집중 작업량 | 약3~5개발일+실증, 자체 보안 검토 추가 | 약3~5일 이상, custom attestation 필요하면 불확실 | 약2~4개발일+1~2일 통합/PC B 검증 |

작업량은 견적 제안이며 인력/네트워크/실패 상황에 따라 달라진다. 재사용 **라인 비율은 구현 diff가 없어 산정하지 않았다**. §11의 실제 파일 단위 보존/수정 계획이 기준이다. 위 숫자는 성공 보증이 아니다.

### C를 선택하는 이유

1. 현행 Worker·Secret·DO·Kotlin 분석과 응답 검증을 버리지 않는다.
2. 직접 구현하기 까다로운 로그인 유지·ID token 갱신을 표준 Firebase Auth SDK에 맡긴다.
3. GMS 없는 환경에서도 Auth 자체는 공식 비필수다. 단, Google Sign-In/전화인증까지 모두 된다고 확장하지 않고 **이메일/비밀번호** 한 방식만 사용한다.
4. B와 달리 비용 경로가 기존 Worker 한 곳으로 모여 UID/기기/일별·전역 제한을 강제할 수 있다.
5. A의 자체 refresh family/회전/응답 유실 복구 서버를 이번 공모전 필수로 만들지 않는다.

**선택은 C 하나다.** A는 관리 기기용 대안, B는 공식 대체 구조 비교다. C 호환성 관문 실패를 debug App Check·공통 토큰·무인증 proxy로 우회하지 않는다.

### B안의 공식 Kotlin 예시와 현행 변화

공식 문서 형태에 공식 지원 모델3.5 Flash-Lite를 적용한 설계 예다. 실행하지 않았다. 현재 문서의 기본 예시 모델은3.8 Flash이지만 CARITDA의 모델을 그 이유만으로 바꾸지 않는다. [F1] [M1] [F18]

~~~kotlin
implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
implementation("com.google.firebase:firebase-ai")
// 운영 provider 선택/검증은 별도. debug provider는 개발에만 사용.

val model = Firebase.ai(backend = GenerativeBackend.googleAI())
    .generativeModel("gemini-3.5-flash-lite")
// suspend 함수: coroutine 안에서 실행
val response = model.generateContent(prompt)
~~~

공식 Play Integrity 초기화 형태는 Firebase.initialize(context=this) 뒤 Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())다. provider 설치와 **enforcement**는 별도다. 최신 FAQ는2026-11-02부터 AI Logic 자동 enforcement 및 일부 기존 앱의2027-01-21까지 이행 경로를 설명한다. 실제 프로젝트 적용 상태는 조회하지 않았다. [F2] [F3] [F5] [F7]

- Debug provider는 개발/CI용 secret 신뢰 경로다. release에 넣어 무결성 인증을 대신하지 않는다.
- Custom provider는 자체 서버가 증거를 검증하고 App Check token을 발급해야 한다. 클라이언트가 임의 token을 만드는 기능이 아니다. [F4]
- AI Logic 기본 per-user/region RPM은100이며 사용자/그룹별 다른 제한 설정 기능은 문서에서 제공하지 않는다. 이를 Firebase UID별 하루 N회로 해석하지 않는다. [F8]
- 강한 일별 gate를 자체 서버에 두어도 앱에 AI Logic 직접 호출 경로가 남으면 모든 비용을 통제했다고 말할 수 없다.
- 현재 FAQ는 AI Logic의 Gemini 인증을 **Google-managed service account(P4SA)**로 설명한다. 기존 Gemini Secret을 AI Logic 앱에 넣는 구조가 아니다.
- Firebase 공개 구성 API key는 프로젝트 식별용으로 포함될 수 있지만 Gemini credential과 다르다. 공개 Firebase key에 Generative Language API 권한을 덧붙이지 않는다. [F7]

---

## 4. Pleos/AAOS 호환성 판정

| 기술 | 일반 공식 근거 | Pleos 판정·검증 |
|---|---|---|
| Firebase SDK 전체 | 제품별 GMS 의존성 다름 | ‘Firebase 전부 지원/전부 불가’ 단정 금지 |
| Google Play services | AAOS와 GAS는 별도, OEM 선택 | Pleos 공식 non-GAS 문맥 존재. 모든 이미지 GMS 부재로 일반화 금지 |
| Firebase Auth25.0.0 | 공식 표 GMS Not Required | **문서상 후보, 현행 CARITDA 실행 미검증**. 이메일 로그인/재실행/갱신 시험 |
| Firebase AI18.1.0 | GMS Not Required | SDK 자체와 App Check enforcement/모델 접근을 나눠 시험 |
| App Check custom/debug20.0.0 | GMS Not Required | custom의 신뢰 증거/서버 필요. debug는 운영 대안 아님 |
| App Check Play Integrity20.0.0 | GMS Required | non-GAS/sideload/AVD에서 성공 가정 금지, verdict·배포채널·서명 조건 확인 |
| Android Keystore | Android 공식 기능, Pleos 보안문서 권장 | alias 생성·서명·재실행 복원 시험. StrongBox/하드웨어 보장은 없음 |
| Firebase Authentication 개별 provider | SDK 비필수≠모든 provider 비필수 | Google Sign-In·전화/Play Integrity를 이번 최소 범위에 넣지 않음 |

[F6] [P1] [P2] [P3] [P12] [P14]

Pleos는 ‘non-GAS AAOS 환경에서는 Google 기반 앱 데이터 자동 백업을 현재 사용하지 않음’을 공식 설명한다. HTTPS/TLS1.2 이상·1.3 권장, Keystore, release 서명·debug 설정 제거도 권고한다. [P1]

Play 외 배포를 지원하는 Play Integrity 설정이 있다는 사실은 비-GMS 기기에서 작동한다는 뜻이 아니다. APK sideload·서명/인식/verdict 정책·GMS 가용성을 함께 봐야 한다. C에서는 이 불확실한 attestation을 필수로 삼지 않고 **인증된 계정+등록키+중앙 비용 gate**로 시작한다. 이를 기기 무결성 attestation이라고 부르지 않는다.

### 현행 프로젝트 버전이 우선

현재 고정 소스: minSdk28 / compile·target34 / BuildTools35.0.0 / AGP8.10.1 / Kotlin2.2.21 / Gradle8.11.1 / Java target17 / Vehicle2.3.2. 개발 기록은 JBR21 사용을 보고한다. [R8]–[R12]

공식 Sample의 JDK17/Gradle8.10.2/AGP8.8.2/Kotlin2.0/SDK35·Service2.3.22는 **샘플 기준**이다. 소개의 Service2.3.21과도 구별한다. 샘플 minSdk23은 Pleos 제품 배포 최소28을 대신하지 않는다. [P4] [P5]

Firebase BoM35.1.0/Auth25.0.0은 이번 공식 문서 기준 후보 버전이다. 기존 compile34에서 AAR metadata·의존성·Dex 확인이 먼저다. 필요 compileSdk 변경은 별도 회귀 후 결정하고 target/min/SDK/AVD를 한꺼번에 최신화하지 않는다. 미검증 구버전을 임의로 pin해 성공이라고 하지 않는다.

---

## 5. 권장 C안: 인증·등록·버튼1회 계약

### 5.1 관리자가 먼저 준비할 것과 사용자 작업

| 관리자1회 준비 | 최종 사용자 |
|---|---|
| Firebase 프로젝트/Android 앱 등록, 이메일 provider·허용 계정 구성 | 최초 로그인1회 |
| Worker의 신뢰 project ID·UID entitlement·기기 수 정책·예산 설정 | ‘이 기기 등록’ 최초 확인 |
| 기존 Gemini Secret 유지·제약/billing/모델 권한 확인 | 최초 AI 안내·데이터 동의 |
| 실제 배포·release·PC B 실증·운영 해제 경로 | 이후 **AI 설명** 버튼 |

사용자에게 Cloudflare 로그인·PowerShell·Gemini key·TEST token·서버 true/false 조작을 요구하지 않는다. Firebase 공개 app/project 구성은 APK에 포함할 수 있으나 서버 비밀이 아니다. 현재 이름 선택/운전 시작은 **Firebase 로그인이나 차주 인증이 아니다**.

### 5.2 계정·인가·서버 검증

공모전은 관리자가 사전 발급한 소수 계정+서버 UID 허용 목록으로 시작한다. 공개 회원가입 버튼을 숨기는 것만으로 보호하지 않는다. 다른 경로로 생성된 Firebase 계정도 **Worker entitlement가 없으면403**이어야 한다. anonymous UID 반복 생성으로 일별 한도를 초기화하는 구조는 제외한다.

공식 인증의 최소 Kotlin 흐름 [F11] [F12]:

~~~kotlin
implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
implementation("com.google.firebase:firebase-auth")

val auth = FirebaseAuth.getInstance()
// 최초 화면에서만: auth.signInWithEmailAndPassword(email, password)
// 일상 버튼: 실제 네트워크/수명주기 coordinator에 연결
val user = auth.currentUser ?: return showSignIn()
user.getIdToken(false).addOnSuccessListener { result ->
    val idToken = result.token ?: return@addOnSuccessListener
    // 로깅/화면 출력 금지. 등록 기기 서명과 함께 relay에 전달.
    enqueueAuthorizedExplanation(idToken, immutableRequest)
}
~~~

showSignIn/enqueueAuthorizedExplanation 등은 **CARITDA가 구현할 함수**다. Firebase가 제공하는 완성 코드라고 하지 않는다. 상시 forceRefresh=true를 사용하지 않는다. SDK 세션 유지/ID token 약1시간·자동 갱신을 활용하며, SDK refresh token 원문을 꺼내 별도 코드에 복제하지 않는다. [F10]

Worker는 Firebase ID token에 대해 RS256·kid/서명·exp/iat/auth_time·정확한 iss/aud·비어 있지 않은 sub를 검증한다. Google OAuth ID token/Firebase custom token/body.uid를 대체로 받지 않는다. project ID와 공개키 URL은 서버 고정 설정이다. [F9]

Workers에서 검증할 jose는 이번 검증 commit의 **6.2.16**을 고정 후보로 삼고 lockfile/Workers 실제 bundle 시험을 한다. Firebase Admin Node 전체가 nodejs_compat만으로 동작한다고 가정하지 않는다. [C1] [C2] [C3] [O4]

핵심 설계 예, 미실행:

~~~typescript
import { importX509, jwtVerify } from "jose";
const PROJECT = SERVER_CONFIGURED_FIREBASE_PROJECT_ID;
// certsFor(): 공식 x509 endpoint의 kid -> PEM map.
// Cache-Control max-age, timeout, single-flight/cooldown을 별도 구현.
const { payload } = await jwtVerify(token, async (header) => {
  if (header.alg !== "RS256" || typeof header.kid !== "string") throw Error("AUTH");
  const certs = await certsFor(header.kid);
  if (!Object.hasOwn(certs, header.kid)) throw Error("AUTH");
  return importX509(certs[header.kid], "RS256");
}, {
  algorithms: ["RS256"], issuer: "https://securetoken.google.com/" + PROJECT,
  audience: PROJECT, requiredClaims: ["exp", "iat", "auth_time", "sub"],
  clockTolerance: 30
});
// 후속 필수: aud 문자열 정확일치, sub형식/길이, iat/auth_time의 미래값,
// entitlement·기기 소유/폐기·작업별 nonce/signature·quota를 검사.
~~~

고정 공개키 endpoint는 Firebase 검증 문서의 **X.509 인증서 map**이다. 이를 JWKS라고 부르며 createRemoteJWKSet에 그대로 넣지 않는다. JWT의 jku/x5u를 따라 fetch하거나 unknown kid마다 무제한 외부 요청하지 않는다. signature 검증만 하고 claim 검사를 빠뜨리지 않는다.

**폐기 경계:** jose 서명 검증은 Firebase 계정 disabled/refresh 폐기를 자동 조회하지 않는다. 공식 verifyIdToken(token,true)는 추가 상태 확인이다. C MVP는 Worker registry의 user/device disable·auth_time cutoff를 매 요청 검사하고, 관리자 해제는 그 registry부터 적용한다. Firebase 콘솔에서만 폐기하고 동기화하지 않으면 이미 발급된 ID token의 잔여 수명(통상 약1시간) 동안 반영이 늦을 수 있음을 명시한다. Firebase 상태 동기화/온라인 revocation을 구현하지 않고 ‘즉시 모두 폐기’라고 하지 않는다.

### 5.3 최초 기기 등록과 복제 방어

1. 앱 설치별 Keystore P-256 키 생성. private key는 밖으로 내보내지 않는다.
2. 로그인한 UID의 entitlement와 허용 기기 수를 서버 확인. 신규 등록에는 최근 재로그인 auth_time 또는 관리자 승인이라는 별도 신뢰 관문을 둔다. 탈취한 오래된 refresh만으로 새 기기를 추가하지 않게 한다.
3. server nonce에 protocol/purpose/registrationId/key thumbprint/server origin/expiry를 결합해 서명한다. nonce는 서버에서 난수·1회·만료로 관리한다.
4. 서버가 공개키 사용 능력을 검증하고 UID에 묶인 deviceId/등록버전을 발급한다. deviceId 문자열·Android ID·serial·Linux UID·클라이언트 hardware=true는 증명이 아니다.
5. AI 작업에도 method/path/payloadDigest/operationId/nonce/purpose를 등록키로 서명해 Firebase bearer만 복사한 다른 기기 호출을 막는다. 이 설계가 실제 하드웨어 무결성 증명은 아니다.
6. 같은 UID라도 PC B의 새 AVD/재설치는 새 key/device다. 자동 복사/승인 승계하지 않는다. 사용자/전역 예산은 초기화하지 않는다.

최근 로그인 조건의 예시는5분, challenge60초, UID별 기기2대다. **예시 운영 정책**이며 구현 전 승인할 설정이다. 신규 사용자에게 임의5회 예산을 주는 조건이 아니다. 등록 대수 제한·교체/분실 해제 경로를 별도로 둔다.

서버 인증은 사용자의 실제 차량 소유권이나 SDK 관측의 OEM 서명을 입증하지 않는다. 서버가 받는 것은 **승인된 앱 기기가 보고한 관측 요약**이다. attestation이 없는 sideload APK의 역공학/감염 위험은 남는다.

### 5.3a 기기 서명 wire 규격: 구현 선택 확정

다음은 **신규 v2 프로토콜 결정**이며 미실행이다. Firebase JWT의 RS256 서명과 아래 기기 ECDSA 서명을 혼동하지 않는다.

- 공개키는 JWK {kty:EC, crv:P-256, x, y}로 전송한다. x/y는 unsigned big-endian32바이트, base64url 무패딩이다. Android ECPublicKey 좌표의 부호용00은 제거하고32바이트로 왼쪽0패딩한다. 다른 curve/추가 private d/잘못된 길이·점은 거부한다.
- thumbprint는 RFC7638의 정확한 순서 {crv,kty,x,y} JSON을 UTF-8 SHA256 후 base64url 무패딩한다. 서버가 계산하며 클라이언트 주장값만 믿지 않는다. [P17]
- Android는 SHA256withECDSA로 proofBytes에 서명한다. JCA의 ASN.1 DER(r,s) 결과를 strict decoder로 분리한 뒤 각32바이트 r||s, **P1363 총64바이트**로 변환해 base64url 무패딩한다. WebCrypto는 ECDSA/P-256/SHA-256과 이64바이트를 검증한다. DER 가변 길이를 고정64바이트라고 오인하지 않는다. [P13] [P16]
- DER 변환기는 최소 길이/양수/r,s 범위·여분 byte 거부를 검사한다. 직접 만든 암호 알고리즘은 쓰지 않고 표준 Signature/WebCrypto를 사용하며 형식 adapter만 시험한다.

proofBytes는 아래 **12개 필드 각각의 UTF-8 byte 길이를 unsigned32-bit big-endian으로 붙인 뒤 원 byte를 이어 붙인 값**이다. 단순 separator 결합/임의 JSON 재직렬화가 아니다.

~~~text
protocol = CARITDA-KEY-PROOF-V1
purpose = REGISTER | EXPLAIN | READ | REVOKE
uid / bindingId(registrationId 또는 deviceId) / keyThumbprint
HTTP method(대문자) / origin(고정 HTTPS, trailing slash 없음)
path(정확한 route, query/fragment 없음)
operationId(등록은 '-' 고정) / payloadSHA256(서명 대상 payload UTF-8 bytes의 소문자 hex)
nonce(base64url) / expiresAt(서버 Unix seconds의 정규 decimal 문자열)
~~~

위 나열은 protocol/purpose/uid/bindingId/thumbprint/method/origin/path/operationId/bodySHA256/nonce/expiresAt의12필드다. payload 숫자 표현을 다시 바꿔 hash하지 않는다. body/route/case/기기키·등록버전이 다르면 proof는 유효하지 않다.

전송 header는 Authorization: Bearer <Firebase ID token>, X-Caritda-Device-Id, X-Caritda-Key-Version, X-Caritda-Nonce, X-Caritda-Proof(base64url64바이트), Idempotency-Key(operationId)로 고정한다. proof/token은 envelope 밖 header에 둔다. EXPLAIN envelope는 operationId와 analysis_payload 문자열만 허용한다. **digest는 전체 envelope가 아니라 analysis_payload 문자열의 정확한 UTF-8 bytes**에서 계산하며 operationId는 proof의 별도 필드로 결합한다. 따라서 operationId를 받기 전에도 payloadDigest가 확정돼 challenge 발급과 순환하지 않는다. 서버는 JSON을 다른 순서로 재직렬화하지 않고 수신한 문자열을 hash한다. REGISTER는 공개 JWK를 담은 registration_payload 문자열, READ는 빈 byte 배열의 SHA256을 사용한다. 추가 envelope 필드는 거부한다.

앱은 먼저 불변 analysis_payload의 digest와 clientIntentId를 전달하고, 서버가 operationId/nonce를 발급해 그 digest와 결합한다. 같은 UID/device/clientIntentId의 발급 응답이 유실되면 같은 intent로 기존 operationId를 복구한다. 다른 digest면409이며 새 operation을 만들지 않는다. 아직 실행 전이고 nonce만 만료됐다면 같은 operation/digest에 새 challenge를 발급한다. 기기 signature에는 발급된 operationId와 같은 payloadDigest가 모두 포함된다. 서버 challenge는 목적·주체·기기키·operation/digest에 결합한다. v2의 새 operationId는 서버 시각과 UUID로 만든 **o2.<epochMillis>.<uuid>** 형식이며 challenge에 저장한다. 클라이언트는 버튼 intent의 UUID와 서버 operationId를 구별하고 서버 응답 뒤 고정해 쓴다. exact operation은24시간 후 새 dispatch가 불가하며410으로 끝난다. 새 버튼만 새 intent/operation을 만든다. v1의 UUID/gate 형식은 바꾸지 않는다.

필수 golden-vector 시험: 고정된 TEST 키/nonce/본문으로 Android JWK→Worker import/서명검증, 역방향 검증, DER의 r/s leading00·짧은 정수/음수/과잉 길이, multibyte uid의 byte 길이, 변조 method/path/digest, 다른 purpose/origin, 같은 키의 재서명. 실제 운영 private key/token으로 vector를 만들거나 게시하지 않는다. 적어도 한 실제 Pleos Keystore 서명→실제 Worker 검증을 모델 호출 없이 통과시켜야 한다.

### 5.4 Keystore·refresh·로그아웃

- C의 로그인 refresh는 Firebase SDK가 담당한다. SDK 내부 저장이 모두 Keystore wrapping돼 있다는 검증은 하지 않았으므로 그런 보장을 붙이지 않는다.
- 앱별 서명 private key는 Android Keystore, 필요하면 별도 세션 비밀의 AES-GCM 보호키도 Keystore에 둔다. token 문자열 자체를 Keystore에 저장하는 범용 vault라고 설명하지 않는다. [P12] [P13]
- 자체 refresh를 택하는 A라면 서버에는 hash/token family·회전 세대·만료·기기 binding을 저장하고, 앱은 Keystore AES-GCM 키로 암호화한 blob을 private 파일에 보관한다. 매번 고유 IV/AAD·원자 교체·백업 제외·키 소실 처리가 필요하다.
- A는 동시 갱신 single-flight, 같은 refreshRequestId의 응답 유실 복구, 재사용 탐지/가족 폐기까지 구현해야 한다. hash만 저장하면 유실된 새 refresh 원문을 재전달할 수 없다는 문제가 있어 C보다 범위가 커진다.
- 로그아웃은 FirebaseAuth.signOut, 진행 요청 취소/표시 무효화와 로컬 기기 세션 해제를 구분한다. 서버까지 해당 기기를 해제하려면 registry disable/revoke가 필요하다. 오프라인 로컬 logout만으로 서버 즉시 폐기를 보장하지 않는다.
- StrongBox/biometric/잠금화면·hardware-backed를 AAOS에서 전제하지 않는다. KeyInfo security level/alias 수명·서명·키 소실을 시험하며 원격 attestation은 chain/challenge/폐기까지 별도 검증해야 한다. [P14]

### 5.5 API 계약 제안

| route | 요구·역할 | 모델 호출 |
|---|---|---|
| GET /health | 공개 release/contract/auth_required 등 비밀 없는 상태 | 없음 |
| POST /v2/devices/challenge | 유효 ID token·entitlement, 등록/작업 purpose와 공개키/operation 결합 | 없음 |
| POST /v2/devices/register | 최근 로그인/승인+nonce서명·기기 수 확인, UID-key 연결 | 없음 |
| POST /v2/explanations | ID token+등록/활성 기기+서명·1회 nonce+Idempotency-Key, 최소 payload | 예산 예약 후만 |
| GET /v2/operations/{id} | 같은 UID/등록 기기의 fresh 서명·인가, 상태/완료 결과만 | 없음 |
| POST /v2/devices/revoke | 재인증/소유·관리 권한, 기기 disable | 없음 |

위는 새 설계다. 현재 /v1/preflight와 /v1/synthetic-soc의 고정 합성 계약은 유지한다. 새 v2 입력이 실제값을 받도록 하되 TEST_ONLY를 실제 관측으로 바꿔치기하지 않는다. 일반 앱은43자 TEST 인증이나 synthetic endpoint를 사용하지 않는다.

HTTP:200완료 /202처리중·확인필요 /401재인증 /403미승인·폐기 /409동일키다른본문 /429rate·quota /503인증키/원장 확인 불가. server diagnostic은 운영 로그/TEST에만 허용 vocabulary로 제공한다.

---

### 5.6 C안의 백업·복원·계정 전환

현재 allowBackup=false는 유지한다. C의 Firebase SDK 세션·등록 ID·동의·AI 결과가 복원됐는데 Keystore 키가 없는 상태도 방어해야 한다. Android12+에서는 cloud-backup과 device-transfer를 **각각** dataExtractionRules로 제외하고, 구버전 fullBackupContent도 검토한다. 빈 section이면 제외된다고 가정하지 않고 명시 exclude를 사용한다. [P15]

C MVP는 앱 private 저장 영역 전체를 cloud/D2D backup에서 제외하는 기존 정책을 유지·명시화하는 방향이다. Firebase 내부 preference 파일명을 추정한 부분 제외로 끝내지 않는다. 별도 AI 상태는 noBackupFilesDir도 활용하고, merged manifest/실제 복원 시험으로 OEM 동작을 확인한다. 이것이 기존 수동 백업/DB를 지우는 작업은 아니다.

시작 시 등록 key alias로 서버 challenge 서명을 확인하고 registry binding과 대조한다. key 소실/복원 mismatch면 기존 device ID만 믿지 않고 AI를 잠근다. 로컬 AI 세션만 무효화→재로그인/새 기기 등록→동의 scope/key binding 재확인하며 서버 옛 기기는 disable/정리한다. 기존 global/UID 사용량과 수동 기록5DB는 유지한다.

로그아웃·계정 전환은 이전 UID의 AI 결과/진행 작업/동의 grant 접근을 차단한다. 서버 해제는 온라인 확인이 필요하다. 로컬 logout만으로 원격 폐기를 완료했다고 알리지 않는다. 전환된 계정에 이전 계정의 등록/동의를 자동 승계하지 않는다.

---

## 6. Gemini 모델·기존 키·무료·과금

**2026-11-09 공식 문서 확인:** gemini-3.5-flash-lite는 **Stable**, Firebase AI Logic 지원 목록에도 있다. Live API/audio generation 모델은 아니다. 기존 모델명을 오류 해결이라는 이유로 무작정 바꾸지 않는다. [M1] [F18]

| 항목 | 확인 내용 | 운영 주의 |
|---|---|---|
| Free tier | 입력/출력 Free of charge | 실제 project/tier/지역·capacity 미확인 |
| Paid Standard 입력 | $0.30 /100만 tokens | text/image/video/audio 표, 이번은 text만 |
| Paid 출력 | $2.50 /100만 tokens, thinking 포함 | 보이는 한국어 문장만 토큰이라고 계산하지 않음 |
| 캐싱/검색 | 별도 가격·기능 조건 | MVP에서는 caching/grounding/tools/media 비활성 |
| RPM/inputTPM/RPD | 프로젝트별 active limits UI 안내 | 정확한 사용자 프로젝트 숫자는 미확인. 다른 모델 숫자 대입 금지 |
| RPD reset | Pacific midnight | CARITDA KST 일별 제한과 별개 |

[M2] [M3]

예시 산식: 입력1000+**총 과금 출력(thinking 포함)512**면 Standard 모델비 약 **$0.00158**다. 실제 호출당 고정가격이나 항상512 토큰 이하 과금된다는 보장은 아니다. Worker/DO/Auth/재시도·부가 기능은 별도다.

현재512 maxOutputTokens/12초/16KiB는 기존 방어 값이다. default thinking이 가시 JSON 출력에 미치는 영향·실제 finishReason을 검증해야 한다. MAX_TOKENS라면 자동으로 한도를 늘리지 않고 모델별 지원 설정·비용 예약 한도를 함께 검토한다. sampling 변경이 원인이라는 증거 없이 필터·schema를 제거하지 않는다.

### 기존 Gemini key 재사용

조건부로 가능하다. 해당 Secret이 유효·미폐기·미유출, 올바른 프로젝트/API restrictions·모델/지역·billing 권한을 가지면 C에서 계속 사용한다. 키를 확인하려고 Secret 값을 조회하지 않는다. 기존 성공 이력은 현재 유효성 보장이 아니다.

현행 문서는 신규 AI Studio key가 service-account-bound authorization key로 전환됐고, 명시적 restrictions가 있는 기존 standard key는 계속 동작한다고 설명한다. unrestricted standard key 거부 등 조건을 무시하지 않는다. **새 PC마다 새 Gemini key를 발급하는 문제는 아니다.** key/project 상태 확인은 관리자 과제이며 원문은 채팅/APK/GitHub로 보내지 않는다. [M4]

### Firebase·Cloudflare 비용

- AI Logic SDK 자체 무료와 모델 무료는 별개. billing 연결 후 Developer API 사용은 pay-as-you-go 조건을 확인한다. 기업 backend(현재 Agent Platform, 구 Vertex 계열)는 billing 조건이 다르다. [F16]
- Firebase 기본 비전화 Authentication과 선택 업그레이드 Identity Platform은 다르다. 공식 Auth 문서는 업그레이드 Spark3000DAU, Blaze 무료50000MAU 이후 과금을 설명한다. 별도 이메일 전송/로그인 rate limits도 있다. 이번은 SMS/전화·유료 Identity Platform 기능을 필수로 하지 않는다. [F13] [F14]
- Cloudflare Workers Paid 최소월$5, Worker/DO/SQLite 각각 별도 계량이다. Free DO 일별 요청100000/read500만/write100000·storage5GB 조건이 있으나 실제 계정 plan은 조회하지 않았다. Paid의 포함량 초과도 청구된다. 정확한 최신 표는 [C5] [C6]. **Cloudflare $5와 TEST 누적5회는 무관하다.**
- budget alert는 차단이 아니다. 최신 Firebase는 service-level spend cap에 의한 중지도 제공하지만 reporting 지연으로 초과될 수 있어 엄밀한 hard cap이 아니며 사용자/일별 제한도 아니다. [F15]

### 무료 서비스의 개인정보 조건

공식 약관은 무료 서비스 입력/출력을 개선·인적 검토에 사용할 수 있으며 **민감·기밀·개인정보를 무료 서비스에 제출하지 말라**고 명시한다. 단순 체크박스 동의로 이 금지를 무효화할 수 없다. paid 데이터 처리 조건은 별도다. [M5]

공모전의 통제된 에뮬레이터 SDK 관측도 원천은 ‘실제 SDK 수신’이지만 값은 시뮬레이션이다. 이름/VIN/위치/개인 사건·식별 가능한 시각/행동이 없는 비개인 요약인지 확인한 뒤만 무료 시험 후보로 삼는다. 실제 개인 차량·운전자 자료는 개인정보/목적·약관·paid 처리 정책을 검토하기 전 전송하지 않는다. 과금 전환은 관리자 승인 사항이며 이번에 변경하지 않았다.

---

## 7. 누적5회를 보존하는 정식 quota와 비용 상한

### 7.1 기존 원장은 그대로 보존

현재 BUDGET binding/BudgetGate 클래스/migration tag/기존 고정 전역 DO 이름/저장 key ‘budget’와 total/minute/count/ids를 초기화·이름 변경·새 namespace로 우회하지 않는다. 이름의 정확한 대소문자는 [R3] [R5] [R6] 소스를 따른다.

정식 확장은 **승인된 정책 변경과 비파괴 이전**이다. 기존 소진/예약은 전역 lifetime 사용량의 시작값이며, 승인 없는 추가 allowance는0이다. 기존5회가 소진되면 새 계정/기기/route를 만들어도 호출 불가다. 추가 운영 한도는 관리자 승인과 변경 이력이 있어야 한다.

### 7.2 한 권위 DO에서 통합

현재 작은 규모에는 같은 고정 DO/namespace 안에 추가 테이블 또는 새 버전 key를 두는 방식이 적합하다.

- immutable legacy snapshot/checkpoint + migration marker(1회), global lifetime/기간 예산.
- UID별·등록 기기별·일별/KST bucket, 분당 rate, 동시 실행 수.
- operationId/requestDigest/principal/device·예약/dispatch/결과/unknown 원장.

**모든 기존 TEST와 신규 일반 route가 같은 global gate를 통과해야 한다.** 한 경로라도 옛5회 gate만 따로 사용하거나 직접 Gemini 호출이 남으면 공유 비용 상한을 강제하지 못한다. 이전 때 모델 호출을 관리자 중단 상태로 두고, 구 Worker 버전의 혼합 트래픽/옛 writer를 차단한 뒤 총액을 이중 합산하지 않게 seed한다. namespace/migration tag를 새로 만들어 기존5회를 숨기지 않는다.

C 전환 시에는 v2만 일반 모델 경로로 활성화하고 **기존 v1 synthetic 모델 호출은 관리자 cutover에서 off**로 둔다. v1 코드/합성 계약·preflight·기존 원장은 보존한다. 향후 v1 모델 시험을 다시 허용하려면 새 통합 gate/dispatch 정책을 반드시 거치도록 검증해야 한다. 남은 옛 경로로 비용 제한을 우회하지 않는다. 이 문장은 이번에 실제 설정을 바꿨다는 뜻이 아니다.

한 기기 DO씩 각자 전역 잔액을 보는 구조나 KV/eventual counter는 전역 원자 상한이 아니다. 여러 DO의 원자 transaction도 전제하지 않는다. [C4]

### 7.3 원자 예약·중복·불명확한 결과

~~~text
reserve: 신원/기기/동의/입력 확인 후 한 transaction
  같은 operationId+다른digest ->409
  같은 operationId+같은주체/본문 ->기존 상태 반환, 완료 본문은 fresh READ proof로 조회
  global + UID + device + day + rate + concurrency 모두 검사
  최악 비용/호출 슬롯 RESERVED, 원 bucket 고정
transaction 밖에서 DISPATCHING -> Gemini HTTP
settle: 한 번만 SUCCEEDED/실패/사용량 기록
~~~

- 외부 전송 전 확실한 실패는 예약 해제 가능. 전송 뒤 timeout/crash/답변 유실은 **UNKNOWN**, 사용됐을 가능성을 보수적으로 남긴다.
- UNKNOWN을 시간이 지났다는 이유로 환불/재호출하지 않는다. upstream idempotency/결과 조회가 확인되지 않았으므로 end-to-end exactly-once는 보장하지 않는다.
- 버튼1회는 operationId1개. double tap·rotation·프로세스 복원·401 후 token 갱신·네트워크 retry에도 같은 작업 ID를 유지한다.
- 기존409 중복 차단은 재사용할 수 있으나 일반 UX에는 같은 결과/작업 상태 조회를 추가한다. 모델을 재호출해서 성공 화면을 복구하지 않는다.
- 일별 시간은 서버의 Asia/Seoul로 고정하는 제안이다. Google RPD Pacific·Cloudflare 무료 UTC reset과 다르며, 정산은 원 예약 날짜에 반영한다.

### 7.3a 중복 처리 순서와 dispatch 소유권

**순서 확정:** JWT→현재 UID entitlement/기기 활성·키버전·동의 확인→proof 검증→기존 operation 조회를 먼저 한다. 같은 UID/device/digest의 RESERVED 이후 operation은 nonce를 다시 소비하거나 quota를 늘리지 않고 기존 상태를 반환한다. 다른 주체/본문은 거부한다. 사용된 EXPLAIN nonce의 정확 재전송은 새 권한 작업이 아닌 멱등 상태조회로 처리한다. 완료 본문의 재조회에는 fresh READ challenge/proof와 현재 인가를 요구한다. 클라이언트는 이를 자동 수행하며 새 Gemini 요청을 만들지 않는다.

아직 ISSUED(도전만 발급) 상태인 operation 또는 신규 실행은 **동일 권위 DO transaction**에서 현재 기기/동의/키버전·nonce 목적/만료/미사용→nonce 소비→모든 예산 예약→RESERVED를 원자 처리한다. 암호 검증을 Worker 밖 단계에서 했더라도 이 transaction에서 버전/상태를 재검사한다. nonce·operation·registry·예산을 여러 DO의 원자성이라고 가정하지 않는다.

- dispatch는 권위 DO에서 CAS(RESERVED→DISPATCHING)로 ownerId/lease/attemptCount=1을 기록한 **한 소유자만** 수행한다. Worker handler마다 provider를 호출하지 않는다.
- 외부 fetch는 transaction 밖의 DO 작업이다. waitUntil/응답 수명 연장은 유실 방지 보조일 뿐 내구성 보장이 아니다. 성공/실패 정산도 owner·state CAS로 한 번만 한다.
- provider12초 timeout과 정산 여유를 포함한 lease(초기 예15초)를 둔다. 영속 alarm 또는 다음 접근에서 만료 DISPATCHING을 UNKNOWN으로 바꾸며 **RESERVED로 되돌리거나 재전송하지 않는다**.
- RESERVED에서 아직 dispatch 소유권이 없었던 작업은 CAS를 획득한 한 소유자가 처리할 수 있다. DISPATCHING 이후 소유권 인계로 재호출하지 않는다.
- local handler 종료와 provider 실제 종료는 다르다. 로컬 active slot과 uncertain-provider slot을 분리한다. UNKNOWN은 비용 예약과 uncertain slot을 유지하며, 확실한 완료/운영 조정 없이 새 호출을 허용하기 위해 슬롯을 재사용하지 않는다. 가용성보다 상한을 우선하는 MVP trade-off다.
- 응답 도착 후 저장 전 crash도 UNKNOWN이다. upstream 조회/idempotency 보장이 없으므로 모델을 다시 불러 복구하지 않는다. 사용량이 불명확하면 보수적 최악 예약을 유지/정산한다.

추가 failpoint: 두 handler가 동시에 같은 nonce/operation 예약, reservation 직후 종료, CAS 직후 fetch 전 종료, fetch 중 종료, 응답 저장 직전 종료, lease alarm과 성공 정산 경합. 모델 Mock의 dispatch 횟수는 operation당최대1이어야 한다. 실제 외부 exactly-once 보장은 아니다.

### 7.4 ‘비용 상한’의 정확한 범위

1. **MVP hard gate:** 전역/UID/기기별 요청 시도 수·동시성·입력 크기·고정 모델·출력 설정·도구 금지. 공급자 전송 전 원자 예약해 명확히 제어한다.
2. **금액 예산:** 입력 token count/가격 버전/최대 과금 출력(thinking 포함)/부가비용 상한이 검증돼야 최악액을 사전 예약할 수 있다. 정수 micro-unit으로 used+reserved<=limit를 검사한다.
3. 이 상한을 증명 못 하면 금액 hard cap이라고 광고하지 않는다. paid 경로는 해당 검증/승인 전 off, free도 프로젝트 한도/입력 정책을 따른다. provider reporting만 믿고 뒤늦게 막지 않는다.
4. Cloudflare·Auth 등의 별도 비용은 모델 원장으로 전부 제한되는 것이 아니다. 각 서비스 자체 limit/예산/관리 모니터링을 병행한다.

구체 UID/기기/일별 숫자는 계정 실제 quota와 승인 예산이 확인되기 전0/차단 기본으로 둔다. 예를 들어 ‘사용자 하루10회’를 넣더라도 기존5회 전역 lifetime을 자동으로 늘리지 않는다. 이번에는 기존 남은 횟수·실제 billing 상태를 읽지 않았다.

---

## 8. 실관측 payload와 개인정보 최소화

현재 AnalysisExplanation.payload는 이름/VIN/위치/원행 ID를 포함하지 않지만 절대 received_at·전체 quality 문자열 등을 가진다. 새 v2 builder에서는 허용된 요약만 명시적으로 선택한다. payload는 button 시점에 불변 snapshot으로 만들고 원관측을 바꾸지 않는다. [R19] [R25] [R28]

| 전송 후보 | 기본 제외 |
|---|---|
| signal/unit, SOC latest/min/max·비교 가능한 변화 | 운전자 이름/이메일/Firebase UID/deviceId를 모델 prompt에 넣는 것 |
| 유효 sample count·상대 관찰 길이·수신 age | VIN/위치/경로/Android ID/serial |
| rule version·공백/오류 개수·truncated/stale | 원본 DB·issue 본문·정비/개인메모·전체 시계열 |
| ODOMETER는 필요할 때 변화량 중심, 목적 있는 최소값 | 원행 store/row ID·무관한 절대 시각/세션 UUID |
| PLEOS_EMULATOR_SDK/실차 추정 출처와 한계 | 출처 TEST_ONLY를 SDK로 위장 |

첫 실모델 범위는 **SDK SOC 요약1종**을 권장한다. 누적거리 설명은 같은 구조의P1이다. sourceMeasuredAt은 현재 없으므로 앱 수신시각을 ECU 시각으로 부르지 않는다. session은 trip이 아니고 관측값 평균은 시간평균/효율·고장 지표가 아니다. VehicleAnalytics 분석 엔진의 quality/segment 결정을 LLM이 다시 계산해 덮어쓰지 않는다.

서버 contract 예시: caritda-analysis-v2, signal=EV_SOC, sourceClass, synthetic=false, window(duration/sampleCount/latestReceiptAge/truncated), facts(latest/min/max/meanOfReceipts/deltaLastComparable), quality(허용 enum/count), ruleVersion. 값 범위·finite·field set·형식·크기·미래/오래된 receipt·수량 상한은 JSON schema+서버 코드로 검사한다. 원시 자유 prompt/model/URL/tools는 받지 않는다.

정확한 수치·판정은 로컬 결정론적 코드가 만들고 LLM은 짧은 설명/action만 생성한다. backend가 검증한 facts와 evidence_digest·operationId·source·model/release를 응답에 묶고 앱도 검증한다. numeric prose 금지/불허 진단 문구 guard는 보조 장치이며 완전한 의미 안전성 보장은 아니다.

현행 nested JSON의 toString 동등 비교는 숫자표현/키 순서에서 거짓 불일치가 날 수 있다. v2는 **정확한 UTF-8 payload bytes의 hash + 재귀 typed facts 비교**를 정의한다. Android/Worker가 서로 다른 임의 재직렬화 hash를 만들지 않는다. 결과를 다른 분석/사건에 붙이지 않는다.

AI가 ‘고장이 아닙니다’처럼 금지어를 포함한 부정문을 생성해도 기존 guard는 거부한다. 이를 고장 원인으로 해석하거나 필터를 없애지 않는다. 비진단/안전 한계 안내는 **앱의 정적 문구**로 표시하고 모델 prompt는 허용 표현을 요청한다.

---

## 9. 실제 오픈소스·공식 코드 검증

2026-11-09 공개 metadata/LICENSE/지정 소스를 확인했다. ‘최근 commit’은 조사 당시 default branch 관측이며 제품 보증이 아니다. 샘플 전체 UI/SDK를 복제하지 말고 필요한 구조만 적용한다.

| 프로젝트·확인 소스 | 라이선스/유지보수 | 기술·CARITDA 재사용/주의 |
|---|---|---|
| [firebase/quickstart-android][O1] / [정확한 코드][X1] | [Apache-2.0 원문][L1] / 2026-11-02 / 활성 repo | Kotlin/Compose/StateFlow, AI Logic·Auth. Firebase.ai/startChat/sendMessage 및 auth.currentUser 패턴. 기존 View UI는 유지. App Check debug는 DEBUG 한정, 현재 별도 appcheck 폴더는 없음. [개발 문서][D1] |
| [firebase/firebase-android-sdk][O2] / [정확한 코드][X2] | [Apache-2.0 원문][L2] / 2026-11-09 / 활성 repo | Kotlin/Java SDK. QuotaExceeded/RequestTimeout/PromptBlocked/ResponseStopped 등 오류 분류. AppCheck refresher는 Auth refresh 구현과 다름. [개발 문서][D2] |
| [firebase/firebase-admin-node][O3] / [정확한 코드][X3] | [Apache-2.0 원문][L3] / 2026-11-03 / 활성 repo | TypeScript/Node Admin. JWT signature/alg/aud/iss/sub 기준 구현. Workers 무수정 실행 보장은 아님, revocation 추가조회 구분. [개발 문서][D3] |
| [panva/jose][O4] / [정확한 코드][X4] | [MIT 원문][L4] / 2026-11-08 / 활성 repo | TypeScript/WebCrypto, 6.2.16. Workers 대응 JWT/X509 검증 후보. Firebase claim/키회전/폐기를 별도 구성. Google 자체 프로젝트는 아님. [개발 문서][D4] |
| [cloudflare/templates][O5] / [정확한 코드][X5] | [MIT 원문][L5] / 2026-11-04 / 활성 repo | TypeScript Worker/SSE. fetch handler/에러 구조. env.AI.run의 Llama 예제이며 Gemini 완제품 proxy가 아님. 기존 CARITDA relay가 직접 재사용 우선. [개발 문서][D5] |
| [android/security-samples][O6] / [정확한 코드][X6] | [Apache-2.0 원문][L6] / 2026-08-07 / repo활성, 해당 샘플 중단 | Kotlin/Keystore AES-GCM. 역사적 키보호 예. README가 해당 샘플 deprecated/no longer maintained 명시. 생체인증 필요 설정을 AAOS에 그대로 복사하지 않음. [개발 문서][D6] |
| [android/car-samples][O7] / [정확한 코드][X7] | [Apache-2.0 원문][L7] / 2026-05-18 / 활성 repo | Kotlin native AAOS/CarPropertyManager. 읽기/구독 수명주기. Android Auto와AAOS 구분, OEM property/권한 별도. Pleos SDK를 raw VHAL로 우회하는 근거 아님. [개발 문서][D7] |
| [android/views-widgets-samples][O8] / [정확한 코드][X8] | [Apache-2.0 원문][L8] / 2025-07-11 / archived | Kotlin/Java multiwindow. 역사적 참고만. archived=true, 최신 adaptive 문서 우선. 앱이 Pleos 분할 위치/OS 홈을 강제할 수 있다는 예가 아님. [개발 문서][D8] |

추가 확인 코드: quickstart의 [App Check 초기화][X11], [이메일 로그인 유지][X12], [AI 오류 UI][X13], SDK의 [App Check 자동 갱신][X14]. App Check refresh를 Firebase Auth refresh라고 바꾸어 설명하지 않는다.

### 추천 제외·결손

- Pleos 공식 sample repo는 존재하고 commit959981d(2026-10-17)이 확인됐지만 LICENSE 파일/명시 허가를 확인하지 못했다. **공식 API 동작 참조만**, 코드를 OSS 허가가 있는 것처럼 복사 추천하지 않는다. [E1] [P5]
- 구 google-gemini/generative-ai-android는 deprecated repo로 이동·archived, 최종2025-05-14다. 신규 Android 연동에 추천하지 않는다. [E2]
- 예전 Pleos safe-driving repo 링크는 현재404여서 적용 자료로 추천하지 않는다.
- **차량 데이터 분석+Gemini 설명의 직접 맞는 검증 완제품은 찾지 못했다.** car-samples 수집 구조+CARITDA 결정론적 분석+Worker 설명의 조합 설계이며 실존 완제품 사례로 꾸미지 않는다.

10유형 coverage: Gemini Kotlin(1), Workers proxy 구조(5+공식 provider 문서), AI Logic/AppCheck(1/2), Auth JWT(3/4), Keystore(6·제약), 로그인 유지/자동 갱신(1+Auth 공식 문서), AAOS(7)/Pleos공식 문서, multiwindow(8·보관), 차량분석+AI(직접사례 미확인), 실패/quota(1/2+공식 문서).

Apache-2.0은 LICENSE/저작권·NOTICE·변경 고지를, MIT는 저작권/permission notice를 보존한다. 라이선스가 서비스 약관·SDK 배포/브랜드 권리·자동차 안전/개인정보 의무를 대신하지 않는다. deprecated/archived 코드를 신규 보안 구현의 정답으로 삼지 않는다.

---

## 10. 최종 사용자 UX와 체크박스 제거

### 최초1회

~~~text
AI 설명을 시작할까요?
차량에서 받은 충전량의 요약을 Gemini로 보내 쉽게 설명합니다.
이름·위치·차대번호·개인 메모는 보내지 않습니다.
AI는 차량 고장을 진단하거나 운행 안전을 판단하지 않습니다.
[로그인하고 시작] [나중에]

최초 로그인 → 이 기기 등록 → 보낼 정보/처리 방식 확인 → [동의하고 사용]
~~~

이것은 제안 문구다. 실제 전송 필드/무료·유료 데이터 처리·수신자/보관·철회가 정책과 일치해야 한다. Firebase 로그인, 기기 등록, 개인정보 동의, 관리자 비용 승인은 서로 다른 권한이다.

- ‘보안 Worker 배포·시험 호출 승인’은 **관리자 배포 체크**이므로 일반 사용자 화면에서 제거한다.
- ‘합성 SOC 전송 동의’는 TEST 목적이다. 일반 앱에는 실제 선택한 관측 요약에 맞는 최초 동의로 대체한다.
- 지속 ConsentGrant는 purpose/provider/필드·정책버전/보관/철회 상태를 기록한다. 매 버튼마다 내부 RequestAuthorization을 해당 불변 payload hash에 결합한다.
- 분석값이 바뀌어도 승인된 필드/목적 범위 안이면 반복 checkbox를 요구하지 않는다. 새 개인정보 필드/목적·제공자/정책이 바뀌면 새 동의가 필요하다.
- onStop의 in-flight 요청/표시 취소와 저장된 동의 철회를 분리한다. 기존 TEST의 매번 동의 초기화를 일반 UX에 그대로 복사하지 않는다.

### 평소 버튼1회

~~~text
차량 데이터 분석
[AI로 쉽게 설명]
  → 로그인 상태/ID token 자동 확보
  → 등록 기기 nonce·서명/동의/근거·quota 검사
  → 설명 중… [취소]
  → 검증된 모델 설명 + 관측 요약/수신 시각·출처 + 한계
~~~

숨은 인증/조회 HTTP가 여러 개라도 사용자가 누르는 버튼은 하나다. 자동 반복 모델 호출·무한 polling·새 requestId retry는 하지 않는다. 현재값/이전 사건/로컬 규칙/실제 모델 결과를 서로 다른 라벨로 표시한다.

### 오류 UX: 사용자와 운영 진단 분리

| 상황 | 일반 앱 문구/행동 | 개발자·운영 |
|---|---|---|
| 오프라인/TLS/timeout | ‘연결하지 못했어요. 차량 기록은 그대로예요.’ 수동 다시 시도/닫기 | APP_NETWORK/TLS/timeout·원인 코드 |
| ID token 만료 | 조용히1회 갱신. 실패 시 ‘다시 로그인하면 이용할 수 있어요.’ | 인증 단계, 모델 전송 전 실패 구분 |
| 기기 폐기/승인 없음 | ‘이 기기의 AI 이용 등록을 확인해 주세요.’ 등록/도움말 |403·registry 상태, 공통 토큰 요구 금지 |
| 사용자/전역 quota | ‘오늘 이용 가능한 설명을 모두 사용했어요.’ 또는 ‘이용 한도에 도달했어요.’ | 일별/전역/공급자 한도를 정확히 구별 |
| provider401/모델404 | ‘AI 연결에 문제가 있어요. 나중에 다시 이용해 주세요.’ | 관리자 설정 경보, 사용자에게 Secret 입력 요구 안 함 |
| MAX_TOKENS/형식·안전 검사 | ‘이번 답변을 표시하지 못했어요. 기본 분석은 볼 수 있어요.’ | finish/schema/guard 단계·안전한 vocabulary |
| 전송 뒤 결과 불명 | ‘처리 결과를 아직 확인하지 못했어요.’ 같은 작업 상태 확인 | UNKNOWN 예약 유지, 새 모델 자동 호출 금지 |
| 오래된 값/표본 부족 | ‘설명할 최근 차량 정보가 부족해요.’ 조회/기본 분석 | 최신 시도·stale/quality reason |

요청 한도가 일별이 아닌데 ‘내일 초기화’라고 안내하지 않는다. provider 429를 자체 quota와 동일 reset으로 묶지 않는다. cancel은 future 표시/추가 요청을 중단하지만 이미 서버에 도착한 모델 처리·과금이 취소됐다고 보장하지 않는다.

**재사용 UI:** CaritdaPalette/CaritdaTokens의 BODY22·SUPPORT20·TOUCH76과 CaritdaComponents.action/loading/state/card/expandable을 유지한다. Compose 전체 재작성/새 디자인 시스템은 필요 없다. 일반 화면은 쉬운 짧은 문장, 기술 상세는 접힌 영역/TEST로 분리한다. [R16] [R29]

주행/UNKNOWN/RESTRICTED에서는 기존 UXR 게이트를 유지한다. 정차 후 상세 설명을 보도록 하고 popup/overlay/음성으로 제한을 우회하지 않는다. 좁은 창·큰 글씨·분할·회전에서 요청 ID/취소/스크롤을 보존하되 회전마다 자동 재호출하지 않는다. UI/rotary/고령자 이해도는 실제 과제로 검증해야 한다. [P10] [P11]

---

## 11. 파일 단위 재사용·변경 계획

**이 표는 Codex의 후속 구현 계획이며 이번 조사에서 수정한 파일이 아니다.**

| 현재 파일 | 처리 | 구체 범위 |
|---|---|---|
| analytics/VehicleAnalytics.kt [R25] | 그대로 재사용 | segment·quality·truncated·비교 가능성 계산, LLM 재계산 금지 |
| analytics/AnalysisStore.kt [R26] | 재사용 | 기존 인사이트 보존, 별도 AI 결과 저장은 additive 모델 |
| vehicle/ObservationStore.kt [R28] | 재사용 | immutable storeId/rowId·원본/이전 보존. AI 호출로 raw 갱신 안 함 |
| vehicle/PleosReadClient.kt [R27] | 재사용 | 실제 typed SDK 수신/가시 구독, 비공개 추가 API 불필요 |
| ui/CaritdaDesign.kt [R29] | 재사용 | 현재 native component·테마·76dp 터치 |
| main/MainActivity.kt [R7] | 수정 | ‘미연결’ 미리보기에서 일반 AI 진입/로그인·동의/작업 상태 연결 |
| ai/AiExplanation.kt [R18] | 재사용+수정 | HTTPS/timeout/크기/cancel, TokenProvider·device signer·v2 endpoint·idempotency 추가. TEST 전용 제약은 별도 client로 분리 |
| ai/AnalysisExplanation.kt [R19] | 수정 | 최소 payload v2·지속 ConsentGrant와 per-request hash 분리·typed facts 검증 |
| ai/RelayDiagnostics.kt [R20] | 재사용+수정 | allowlist 진단 유지, 일반 UX error mapper와 auth/device/quota 코드 분리 |
| validation/GeminiPocActivity.kt [R21] | TEST 한정 유지 | 일반 앱에43자/배포 checkbox를 옮기지 않음 |
| validation/PocTokenSession.kt [R22] | 정식 경로에서 제외 | TEST10분 메모리 기능은 보존, production refresh 대체품 아님 |
| validation/SyntheticSocFixture.kt [R23] | TEST 한정 유지 | 실관측 입력으로 위장/일반 DB 저장 금지 |
| app/build.gradle.kts [R8] | 최소 수정 | Auth BoM/module, 공개 Firebase config·서버 환경 분리, release 서명 설정 별도 |
| main/AndroidManifest.xml [R12] | 검토·최소 변경 | 이미 INTERNET/DO 미선언 존재. 새 launcher/키·공통 token 불필요 |
| infra/src/index.ts [R3] | 핵심 재사용+분리 | 제한본문·Secret출구·모델 guard·진단, JWT/registry/quota·v2 route 추가. /v1 합성 계약은 유지 |
| infra/test/security.js [R30] | 확장 | 기존59의 의미 보존, 인증·키회전·실관측·quota/idempotency·구 v1 회귀 |
| infra/wrangler.existing.jsonc [R6] | 기존 identity 유지 | keep_vars/Secret/binding/class/migration 보존, 신규 공개 설정만 명시 검토 |
| infra/wrangler.jsonc [R5] | 초기 방어 보존 | 신규 초기 설치의 off 기본값. 기존 배포 갱신에 무작정 사용하지 않음 |
| infra/package.json [R4] | 수정 | jose6.2.16 후보+정확 tool pin/lockfile, npm ci 재현 |
| infra/README.md [R2] | 후속 갱신 | 사용자 설치와 관리자 운영·배포 절차 분리 |

추가 제안 파일(신규):
- Android ai/auth/FirebaseSessionProvider.kt, DeviceKeyStore.kt, DeviceRegistrationRepository.kt.
- Android ai/ConsentRepository.kt, MinimizedAnalysisPayload.kt, AiRequestCoordinator.kt, AiResultStore.kt, AiUserErrorMapper.kt.
- Worker src/auth/firebase-token.ts, device-registry.ts, quota-v2.ts, analysis-contract-v2.ts 및 각 test. 기존 index.ts를 작은 router로 정리하되 동작 회귀 후 수행.
- docs/PORTABLE_AI_ACCEPTANCE.md 및 공개 구성 예제. private key·실제 계정 비밀번호·인증 token·Google Secret·개인 DB는 추가하지 않는다.

C MVP는 **명시 FirebaseOptions 초기화 한 방식**으로 고정해 새 Google Services Gradle plugin 도입을 피한다. FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setApplicationId(공개 Firebase App ID).setApiKey(공개 Firebase API key).setProjectId(공개 Project ID).build())를 Auth 사용 전에 수행한다. 여기의 Firebase App ID는 Android applicationId 문자열과 다른 등록 식별자다. 관리자가 제공한 공개 구성만 variant 리소스로 관리하고, validation/일반 앱을 각각 올바른 Firebase Android 앱 등록과 매칭한다. 공개 Firebase 설정을 Gemini Secret과 혼동해 빼면 PC B 초기화가 실패하고, 반대로 Gemini Secret을 공개 설정에 넣으면 안 된다.

---

### 11.1 공개 구성과 운영 비밀 분리

C는 인증용 Firebase project ID를 명시적으로 선택하며 기존 Gemini Secret의 프로젝트와 같을 필요는 없다. 인증 도입 때문에 기존 Gemini 프로젝트 billing/키를 이동하거나 다시 발급하지 않는다. 기본 비전화 Auth만 쓰는 별도 Spark 프로젝트도 후보이며 실제 계정 조건은 관리자가 확인한다.

| 설정 | 책임/경계 |
|---|---|
| Firebase App ID·Project ID·Firebase API key | 관리자 최초 발급/제공하는 공개 앱 구성. Gemini 권한을 붙이지 않음 |
| 서버 FIREBASE_PROJECT_ID | 검증할 issuer/audience 고정값. 클라이언트가 바꿀 수 없음 |
| Gemini credential | 기존 Worker Secret 유지, APK/소스에 포함하지 않음 |
| UID entitlement·기기 registry·한도 | 관리자만 수정, 사용자 요청 body의 값으로 승인하지 않음 |
| 서비스 활성화/예산 변경 | 배포 담당자 최초 운영 설정, 최종 사용자 반복 flag 조작 없음 |
| 기존 TEST secret/만료·누적5회 | v1 진단용 정책 보존, v2 공통 비밀로 재사용하지 않음 |

Firebase ID token 서명 검증에는 공개 인증서만 필요하므로 Worker에 Firebase 서비스계정 private key를 넣을 필요가 없다. 고정 인증서 map endpoint는 https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com 이며 [F9] 규칙대로 cache/회전한다. 관리자 사용자관리 권한까지 클라이언트에 배포하지 않는다.

로그는 operation ID·허용 code·기간·token 사용량 등 최소 메타데이터로 제한한다. Authorization header·key/token·원문 prompt/개인 사건·응답 전체를 기본 로그에 남기지 않는다. 작업 결과 재조회 cache는 운영자가 승인한 짧은 보관기간/삭제 정책을 따르고, 원장 사용량·중복 방지 key는 결과 cache 삭제와 별개로 유지해 비용 제한이 초기화되지 않게 한다.

---

### 11.2 MVP 보관·삭제 기본 정책

다음은 **제안 운영 기본값**이며 동의문·서버 설정과 일치시킨 후 적용한다. 법적 보관기간 인증이 아니며 기존 기록/DB의 삭제 정책을 소급 변경하지 않는다.

| 저장소 | 기본 보관·접근·삭제 |
|---|---|
| provider 전송 원문 | 요청 메모리에서만, 별도 원문 로그/DB 저장 없음. 공급자 처리 약관은별도 |
| 서버 AI 결과/facts cache |24시간, 같은 UID+등록기기 fresh READ proof만 접근. 만료/동의 철회 시 본문 삭제·410, 재생성을위한자동모델호출없음 |
| operation metadata |ID/digest/state/예산·사용량의최소필드30일. operationId의서버발급시각24시간유효검사로삭제후옛작업재dispatch금지. 비용누적집계는별도보존 |
| registry/entitlement |활성승인기간만,30일미사용은disable기본. 해제7일뒤기기키연결상세제거, 필요최소폐기/audit30일. 재등록도기존예산초기화안함 |
| 일반 운영 로그 |허용 code/기간·operation ID 등7일. 이메일·JWT·Authorization·서명·nonce·원문prompt/응답제외 |
| 앱 AiResultStore |UID/device/digest에묶인24시간 derived cache. logout/계정전환/동의철회/키소실시해당AI cache제거. 원관측·사용자문제DB는삭제안함 |
| ConsentGrant |UID/device-key/purpose/fields/provider/policyVersion에결합,동의유효동안. 철회하면future전송/표시즉시차단·최소철회이력30일 |

UNKNOWN 사용량을 cache TTL이나 계정 삭제와 함께 환불하지 않는다. 결과·개인연결 상세를 삭제해도 global 소진액/예약은 최소 집계로 유지한다. 해시/가명값을 무조건 익명이라고 부르지 않으며 삭제 요청·백업·서비스 종료 정리 범위는 개인정보 정책에 반영한다. 기관/개인정보 요구로 기본기간을 바꿀 때도 중복/누적 제한이 초기화되지 않게 검증한다.

분석이 갱신된 뒤 이전 AI 응답이 도착하면 원 요청 snapshot의 설명으로 표시하거나 현재 화면 적용을 유보한다. 현재값 설명으로 조용히 덮어쓰지 않고 모델을 자동 재호출하지 않는다.

---

## 12. PC A → PC B: 빌드와 설치를 분리

### 12.1 PC B가 소스를 빌드하는 경우

1. 접근 가능한 GitHub의 검증 commit/tag 소스와 Gradle wrapper를 받는다. 개인 caches/DB/백업·local.properties·키 파일은 받지 않는다.
2. Android Studio/JDK·Gradle·AGP·Kotlin·API34/BuildTools·Pleos Maven dependency를 현재 프로젝트 기준으로 복원한다. artifact 다운로드 네트워크가 필요하다.
3. Firebase 공개 구성·같은 원격 backend URL은 재현하되 Gemini/Worker admin Secret은 필요 없다.
4. Debug는 PC B의 로컬 debug key로 독립 설치 시험할 수 있다. **같은 서명/비트 동일 APK라는 뜻은 아니다.** 배포용 업데이트에는 승인된 release signing 관리가 필요하다.
5. package-lock/정확 dependency lock·Gradle checksum 등은 후속에 관리한다. 현재 infra의 ^typescript/^wrangler만으로 byte-identical build를 약속하지 않는다.

### 12.2 PC B가 완성 APK만 설치하는 경우

Gradle/JDK/Maven/소스·Gemini key·Cloudflare 계정은 필요 없다. 단 **PC B에서 적합한 Pleos 전용 AVD/Vehicle Service**가 실행돼야 한다. 일반 Android폰/표준 AVD에 설치만 했다고 Vehicle SDK가 동작하지 않는다. [P4] [P5]

설치 담당자는 AVD의 current Android user/serial, API/서비스/네트워크·APK 무결성/서명을 확인한다. 사용자는 정상 앱의 최초 로그인/등록/동의만 수행한다. 설치에 필요한 ADB는 운영/설치 담당자 작업이며 매 AI 호출에 CMD를 실행하는 UX가 아니다.

### 12.2a 설치·빌드 담당자의 명령 예시

아래는 **후속 실행 예시이며 이번 조사에서 실행하지 않았다**. 최종 사용자의 AI 이용 절차가 아니다. release 서명·private key 보관을 먼저 준비하고, 설치 대상이 기존 debug와 다른 signer면 중단한다.

~~~powershell
# PC B가 소스를 빌드할 때, 프로젝트 루트 NextDrive-Pleos에서
.\gradlew.bat :app:assembleDebug :app:assembleValidation :app:assembleValidationAndroidTest
# 서명 구성이 완료된 경우에만 제출용 빌드
.\gradlew.bat :app:assembleRelease
# APK만 받는 PC B의 설치 담당자는 Gradle/JDK 없이 아래 설치 확인
adb devices -l
adb -s <serial> shell am get-current-user
apksigner verify --verbose --print-certs <signed-release.apk>
Get-FileHash <signed-release.apk> -Algorithm SHA256
adb -s <serial> install --user <current-user> -r <signed-release.apk>
~~~

실제 signed APK 경로와 인증서·versionCode를 출력/분석으로 확인한다. 일반적인 release 출력 이름은 app/build/outputs/apk/release/app-release.apk지만 현재 서명 파일이 생성돼 있다는 뜻은 아니다. 새 AVD에서도 current user를10으로 하드코딩하지 않는다. 설치 실패 때 uninstall/clear-data/강제 downgrade로 우회하지 않는다.

### 12.3 서명·업데이트·데이터

현재 일반 앱은 debug APK이고 release 서명은 미완료다. 새 release key로 같은 appId를 덮으면 서명 불일치가 날 수 있다. **기존 운영 앱을 삭제해 해결하지 않는다.** [P7]

- PC B의 깨끗한 시험 환경에는 정식 release APK 설치로 시작한다.
- PC A 기존 운영 데이터 유지 업데이트는 동일 signer/유효 rotation·버전 규칙을 확인한 별도 계획이 필요하다.
- 동일 appId여도 PC B AVD의 UID/SQLite/preferences/Keystore는 독립이다. APK 설치는 A의 기록 동기화가 아니다. 최초 empty 상태와 B의 새 관측으로 동작해야 한다.
- 로컬 이력 동기화는 이번 AI 설명 MVP 범위 밖이다. signing key·refresh 자료/기기 등록을 파일로 복제하지 않는다.
- 서로 다른 A/B SOC/거리는 정상이다. 각자의 실제 SDK 값·source/receipt·분석 snapshot으로 설명하고 A값/합성 fixture를 fallback으로 보내지 않는다.
- 미지원·빈 표본·오래된 값이면 설명을 유보하고 로컬 기록/다시 조회를 제공한다.

서버는 localhost/127.0.0.1/10.0.2.2가 아니라 같은 공개 HTTPS domain이다. 10.0.2.2는 현재 host PC alias여서 PC A 서버의 영구 주소가 아니다. URL은 허용 목록·TLS 검증·redirect금지로 보호한다. [P8]

### 12.4 제3자가 개발 설정 없이 실행할 조건

정식 서명 APK+지원 Pleos 이미지/Vehicle Service+인터넷/TLS+가용 backend·모델/예산+최초 계정/기기 승인+유효 동의가 갖춰져야 한다. **사용자에게 개발 도구 설정을 요구하지 않아도, 운영자가 준비해야 할 서버/계정/배포 조건까지 사라지는 것은 아니다.** 공모전 심사자 계정·기기 등록 방법과 연결이 안 될 때의 연락/기본 기능을 제출 안내에 넣는다.

---

## 13. Codex 실행 단계·합격 관문

모든 모델 호출·배포·정식 allowance 변경은 이번 조사와 별개 승인 작업이다. 각 단계 결과를 코드/로컬 Mock/실제 backend/실제 모델/PC B로 나눠 보고한다.

| 단계 | 작업 | 합격 기준·시험 |
|---|---|---|
| 0 기준/보존 | 최신main/실제 배포 버전·자동배포 연결·5DB/DO 보존 계획 | source와health/preflight release 구별, 예산 원본/식별자 보존, 키 원문 조회 없음 |
| 1 C 호환성 | Firebase Auth 의존성·이메일 로그인·앱 재시작/갱신을 validation에서 | compile/AARmetadata·Pleos sign-in/currentUser/getIdToken, GMS없는 조건 기록 |
| 2 서버 Auth/등록 | jose/JWT·allowlist·Keystore challenge·registry | 아래 PA 인증 부정시험, 재설치/분실 해제, unauthorized는모델0회 |
| 3 quota additive 이전 | 원장 snapshot·marker·통합 gate·operation ledger | legacy 사용량 유지, 추가승인0이면5회초과차단, 동시/분산 reserve 불변식 |
| 4 최소 실관측 계약 | 기존 분석에서 SOC 요약 v2→Worker schema/응답 guard | synthetic와실제경로분리, 개인정보제외, stale/숫자/해시/typedfacts검사 |
| 5 일반 UX | 지속 동의·AI버튼·로딩/취소·쉬운오류/설정 | 최초후43자/반복checkbox/CLI없음, rotation중복호출0 |
| 6 승인된 실모델 | 실제배포release 확인→모델없는검사→최소1회 | 정식예산이허용할때만, MODEL·modelID·operation/hash/facts·표시 확인 |
| 7 PC B release | 실제 다른PC/별도Pleos에일반APK설치·새데이터 | B에서새Gemini키/Worker없음, 로그인/등록후버튼1회 실제응답 |
| 8 제출 | release/hash·실행안내·영상·제약·심사자접근 | Debug/합성/로컬설명을실제일반Gemini성공으로표기하지않음 |

### 실제 모델 호출 예산표

기본 최소안은 **PC B 일반 앱 E2E1회를 PA26·PA27·PB14의공통 증거**로쓴다. PC A의단위/계약/auth/기기서명·배포확인단계는모델없는검사로먼저통과시킨다.

| 시험 | 새 provider 호출 예산 |
|---|---|
| 공개health·JWT/nonce·preflight·Mock·실기서명 |0회 |
| PC B 일반 APK·실SDK요약→Worker→검증된Gemini화면 |사전승인·원장잔액있는경우1회 |
| PC A에서도별도실모델 E2E를원하는경우 |추가1회가필요,기본허용0·별도승인 |
| 같은operation의전달유실/결과조회 |추가호출0회,UNKNOWN은유보 |
| provider실패후새operation으로재시험 |새비용시도,자동허용하지않음 |

현재잔액은조회하지않았으므로실모델시험은**[blocked: 정식정책/잔액·해당호출승인확인전]**이다. 기존5회소진·추가grant0이면PC B도blocked이며합격으로세지않는다. 새namespace/계정으로돌려서잔액을만들지않는다. 단계6과7을각각별도호출해야한다는뜻이아니다.

### 인수기준 PA01–PA30

| ID | 영역 | 합격 조건 |
|---|---|---|
| PA01 | 보존 | 일반/TEST DB·원관측·사건·분석/연결·초안·사용자/서명키 보존, 운영 대상 자동검사 금지 |
| PA02 | 배포 | code/release/health·authenticated contract의일치 확인, GitHub게시≠배포 구분 |
| PA03 | 호환 | 실제Pleos에서선택Auth라이브러리로그인/재실행·IDtoken갱신, 단순문서지원으로통과안함 |
| PA04 | 최초/일상 | 최초로그인·등록·동의후AI버튼1회, key/43자/checkbox/서버flag/CLI 불필요 |
| PA05 | JWT | none/HS256/잘못된서명/kid/aud/iss/sub/미래iat·auth_time/만료 모두거부·모델0회 |
| PA06 | 키회전 | max-age cache·unknownkid singleflight/cooldown, Googlekeyfetch실패시우회허용없음 |
| PA07 | 인가 | 임의신규/anonymous UID·다른기기ID/bodyUID 위조로미승인호출불가 |
| PA08 | 등록 | nonce만료/재사용/다른purpose·origin·공개키/본문서명 mismatch 거부 |
| PA09 | 폐기 | 서버 user/device disable 다음요청차단, Firebase단독revocation 지연명시·재인증동작 |
| PA10 | 갱신 | token만료는자동갱신1회, 같은operation유지, 실패시무한루프/새모델호출없음 |
| PA11 | 동의 | persistent scope/version과payload별승인분리, 철회뒤새전송/옛응답표시차단 |
| PA12 | key보호 | Gemini/admin credentials APK/로그/Git없음, Firebasepublicconfig와구별 |
| PA13 | 개인정보 | 이름/VIN/위치/개인본문/원행ID/불필요절대시각 미전송, free약관위반없음 |
| PA14 | 실관측 | 일반앱SDK SOC요약이며fixture변조아님, emulator/실차출처표시 |
| PA15 | 계약 | unknownfield/NaN/범위·단위/오래됨/표본부족/본문크기·model/URL 주입차단 |
| PA16 | 응답 | MODEL/model/contract/id/digest/facts/action/종료·문구검사, MOCK일반표시거부 |
| PA17 | 숫자 | nestedfacts 키순서/숫자형식·직렬화차이처리, 수치창작/다른근거응답거부 |
| PA18 | quota | legacy5회·소진/예약/namespace/id/key 보존, 새계정/기기/route로초기화불가 |
| PA19 | 원자성 | global/UID/device/day/rate/concurrency 동시원자예약, 다른DO/KV로상한분산안함 |
| PA20 | idempotency·nonce 순서 | doubletap/회전/네트워크재송/401후같은작업은모델추가호출0, 다른본문같은키409 |
| PA21 | owner/CAS·UNKNOWN | 전송뒤timeout/crash는자동환불·재호출없음, 상태조회/후속조정가능 |
| PA22 | 비용 | 모델/도구/출력·입력/가격버전·총과금token예약 검증, 확인안된금액hardcap주장없음 |
| PA23 | 오류 | offline/auth/providerquota/자체quota/format/safety/timeout 사용자행동이쉬우며코드수정요구없음 |
| PA24 | lifecycle | UXR/비가시/취소/회전·분할·process recreation에중복요청/무단표시없음 |
| PA25 | UI | 공통native토큰/두테마/큰글씨/좁은창/터치·rotary·고령이해도시험분리 |
| PA26 | 모델 | 실제승인모델1회성공·허용한도내, 로컬Mock/Google직접호출로일반앱E2E대체안함 |
| PA27 | PC B | 실제다른PC에일반releaseAPK설치, 새키/Worker재구축없이실SDK→버튼→MODEL응답 |
| PA28 | 업데이트 | release/debug signer충돌로uninstall/DB삭제강요없음, 동일서명업데이트기록유지 |
| PA29 | 오픈소스 | LICENSE/NOTICE·수정고지, deprecated/라이선스미확인코드복사제외 |
| PA30 | 제출정직성 | release서명/계정·네트워크·예산/환경/미검증범위를문서·영상에같이표시 |

PA는 **이번에 실행하지 않은 기준**이다. 기존110/369 assertion과 합산하거나통과로보고하지않는다.

---

## 14. PC A → PC B 재현성 검증표

| ID | 검증 | 합격 증거 |
|---|---|---|
| PB01 | 경로분리 | B소스빌드 vs APK설치 절차·필요도구가따로있음 |
| PB02 | 소스/의존성 | commit·wrapper·JDK/AGP/Kotlin·SDK·Maven·lock 기준기록 |
| PB03 | APK무결성 | release SHA256/cert/appId/versionCode·debuggable=false 확인 |
| PB04 | 지원환경 | Pleos전용AVD/API/VehicleService·serial/currentuser 확인 |
| PB05 | 설치보존 | 기존설치서명충돌/낮은버전시중단, uninstall/clear-data안함 |
| PB06 | 네트워크 | 동일HTTPSdomain DNS/TLS·인터넷/timeout 검증,localhost의존없음 |
| PB07 | 서버 | 배포release/계약·인증·모델/예산준비, B서버생성없음 |
| PB08 | 비밀 | B사용자에게Gemini/Workerkey발급·43자입력요구없음 |
| PB09 | 최초등록 | 같은계정가능하지만B새devicekey승인, A키/세션복제없음 |
| PB10 | 로컬독립 | B빈DB/다른관측정상, A기록자동동기화라고광고안함 |
| PB11 | SDK | BCapability/실수신·unit/receipt/source, 미지원/null분기검증 |
| PB12 | 분석 | B의실제SDK요약사용, A값/fixturefallback없음 |
| PB13 | 동의/UX | 최초후재실행/갱신에도AI버튼1회·반복checkbox없음 |
| PB14 | 모델 | Worker경유실제MODEL응답/ID/hash/facts/표시, 직접API만성공은불충분 |
| PB15 | 장애 | offline/만료/기기해제/quota/불명확처리결과·로컬기능유지 |
| PB16 | 사용자상태 | UXR/두창/화면크기/재생성·취소시안전/중복보호 |
| PB17 | 업데이트·백업/키소실 | 같은서명교체후B기록/동의/registry연계유지·키소실재등록처리 |
| PB18 | 심사자 | 별도개발설정없이APK·계정/등록·인터넷·정상예산으로실행가능 |

같은PC에AVD두개로연습한것은사전검사일뿐 **실제PC B 검증을대체하지않는다**. 이번에는어느PB도실행하지않았다.

---

## 15. 목표 일정·위험·최종 권고

공식 접수 마감은 2026-11-15 KST, PT는 12/3~4다. 현재 11/9 밤이므로 약6일이다. 1차 기획서 필수·개발 산출물 선택 가산점과 PT 실동작 요구를 구분한다. [G1] [G2]

이 일정은2~4개발일+1~2실증일견적의낙관적인목표다. Day1 Auth/Pleos/AAR검증실패, 배포버전불일치, 원장/정식예산차단이면AI완성약속을중지하고1차제출에서실동작한로컬분석과AI개발계획을구분한다. 보안약화/무단모델호출로날짜를맞추지않는다.

- **11/10:** C Auth의존성/Pleos로그인·자동token 검증, 배포불일치·예산보존/실제지원문제부터해결.
- **11/11:** WorkerJWT/registry·통합quota·v2실SOC계약. 무인증/위조/고갈시모델0회검사.
- **11/12:** 일반native버튼·지속동의·검증된실제모델응답까지연결. 승인된예산없으면무단추가호출안함.
- **11/13:** 실제PC B에release·별도관측·최초등록·버튼1회E2E, 서명/오프라인/UXR확인.
- **11/14:** 기능동결·기존회귀·영상·설치/계정안내·심사자접근준비.
- **11/15:** 제출버퍼. 새아키텍처/계정기능확대금지. 실동작미완료는명시하고가짜AI데모로대체하지않음.

| 위험 | 현재 수준/처리 |
|---|---|
| 수정Worker미확인 | 공개health에release없음. 모델없는버전확인부터 |
| Auth/Pleos | 일반NotRequired만확인, 실기로그인·갱신은Day1관문 |
| 인증/유출 | UID인가+등록key+JWT회전/폐기경계, 원문로그금지 |
| 비용/DO | 누적5우회금지·통합원자원장, UNKNOWN보수처리 |
| 개인차량자료 | free약관·최소화/동의·paid검토없이는전송안함 |
| 실모델응답 | format/guard/MAX_TOKENS원인미확정, 현재성공주장금지 |
| 오프라인 | 로컬기록/분석정상유지, 모델기능만실패안내 |
| release/PC B | debug signer와데이터보존, 별도PC검증필수 |
| 기간초과 | C핵심만, 새AI Logic/자체계정서버/음성·차량제어제외 |

최종권고: **기존Kotlin분석/Cloudflare/Gemini자산을유지하고FirebaseAuth만표준인증계층으로추가한다. 최초등록·동의를한번만마치면일상AI요청은한버튼으로자동인증한다. PC B는같은원격서버를이용하므로새Gemini키나Cloudflare구축은불필요하다. 단서버배포·정식예산·PleosAuth·release·PC B E2E가합격하기전현재연결완성이라고표현하지않는다.**

---

## 16. 공식 출처·고정 코드

다음자료는2026-11-09확인했다. 모델/요금/보안/정책은변경될수있으며프로젝트실제계정값은조회하지않았다. 고정repo코드는그commit의증거이고최신실행/배포증거는별도다.

### 저장소 기준 파일

- **R1** [docs/CODEX_PROGRESS.md][R1]
- **R2** [infra/gemini-relay/README.md][R2]
- **R3** [infra/gemini-relay/src/index.ts][R3]
- **R4** [infra/gemini-relay/package.json][R4]
- **R5** [infra/gemini-relay/wrangler.jsonc][R5]
- **R6** [infra/gemini-relay/wrangler.existing.jsonc][R6]
- **R7** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt][R7]
- **R8** [NextDrive-Pleos/app/build.gradle.kts][R8]
- **R9** [NextDrive-Pleos/build.gradle.kts][R9]
- **R10** [NextDrive-Pleos/settings.gradle.kts][R10]
- **R11** [NextDrive-Pleos/gradle/wrapper/gradle-wrapper.properties][R11]
- **R12** [NextDrive-Pleos/app/src/main/AndroidManifest.xml][R12]
- **R13** [docs/GEMINI_RESPONSE_FIX.md][R13]
- **R14** [docs/GEMINI_MINIMUM_POC.md][R14]
- **R15** [docs/CARITDA_ANALYTICS_AI_INTEGRATED_REPORT.md][R15]
- **R16** [docs/CARITDA_DESIGN_SYSTEM.md][R16]
- **R17** [docs/CARITDA_VIRTUAL_DRIVING_AI_POC.md][R17]
- **R18** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ai/AiExplanation.kt][R18]
- **R19** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ai/AnalysisExplanation.kt][R19]
- **R20** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ai/RelayDiagnostics.kt][R20]
- **R21** [NextDrive-Pleos/app/src/validation/java/com/nextdrive/pleos/GeminiPocActivity.kt][R21]
- **R22** [NextDrive-Pleos/app/src/validation/java/com/nextdrive/pleos/PocTokenSession.kt][R22]
- **R23** [NextDrive-Pleos/app/src/validation/java/com/nextdrive/pleos/SyntheticSocFixture.kt][R23]
- **R24** [NextDrive-Pleos/app/src/validation/AndroidManifest.xml][R24]
- **R25** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/analytics/VehicleAnalytics.kt][R25]
- **R26** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/analytics/AnalysisStore.kt][R26]
- **R27** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/PleosReadClient.kt][R27]
- **R28** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt][R28]
- **R29** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ui/CaritdaDesign.kt][R29]
- **R30** [infra/gemini-relay/test/security.js][R30]

### 공식 문서·검증 프로젝트 링크

- **F1** https://firebase.google.com/docs/ai-logic/get-started?platform=android
- **F2** https://firebase.google.com/docs/app-check
- **F3** https://firebase.google.com/docs/app-check/android/play-integrity-provider
- **F4** https://firebase.google.com/docs/app-check/android/custom-provider
- **F5** https://firebase.google.com/docs/ai-logic/app-check
- **F6** https://firebase.google.com/docs/android/android-play-services
- **F7** https://firebase.google.com/docs/ai-logic/faq-and-troubleshooting
- **F8** https://firebase.google.com/docs/ai-logic/quotas
- **F9** https://firebase.google.com/docs/auth/admin/verify-id-tokens
- **F10** https://firebase.google.com/docs/auth/admin/manage-sessions
- **F11** https://firebase.google.com/docs/auth/android/password-auth
- **F12** https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser
- **F13** https://firebase.google.com/docs/auth/limits
- **F14** https://firebase.google.com/docs/auth
- **F15** https://firebase.google.com/docs/projects/billing/spend-caps
- **F16** https://firebase.google.com/docs/ai-logic/pricing
- **F17** https://firebase.google.com/docs/app-check/custom-resource-backend
- **F18** https://firebase.google.com/docs/ai-logic/models
- **M1** https://aistudio.google.com/docs/models/gemini-3.5-flash-lite
- **M2** https://aistudio.google.com/docs/pricing
- **M3** https://aistudio.google.com/docs/rate-limits
- **M4** https://aistudio.google.com/docs/api-key
- **M5** https://aistudio.google.com/docs/terms
- **M6** https://ai.google.dev/api/generate-content?hl=en
- **C1** https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json
- **C2** https://developers.cloudflare.com/workers/runtime-apis/web-crypto/
- **C3** https://developers.cloudflare.com/workers/runtime-apis/nodejs/
- **C4** https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/
- **C5** https://developers.cloudflare.com/durable-objects/platform/pricing
- **C6** https://developers.cloudflare.com/workers/platform/pricing
- **P1** https://document.pleos.ai/en/docs/pleos-only/app-registration-guide/app-security-guide
- **P2** https://source.android.com/docs/automotive/start/what_automotive
- **P3** https://developer.android.com/training/cars/platforms/automotive-os/google-play/google-services
- **P4** https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- **P5** https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app
- **P6** https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- **P7** https://developer.android.com/google/play/app-updates?hl=en
- **P8** https://developer.android.com/studio/run/emulator-networking-address
- **P9** https://source.android.com/docs/devices/admin/multi-user-testing
- **P10** https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- **P11** https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode
- **P12** https://developer.android.com/privacy-and-security/keystore
- **P13** https://developer.android.com/privacy-and-security/cryptography
- **P14** https://developer.android.com/privacy-and-security/security-key-attestation
- **G1** https://appdevchallenge.auto-hmg.io/guide/schedule
- **G2** https://appdevchallenge.auto-hmg.io/guide/submission
- **E1** https://github.com/Pleos-Playground/pleos-sample-vehicle-app
- **E2** https://github.com/google-gemini/deprecated-generative-ai-android
- **O1** https://github.com/firebase/quickstart-android
- **L1** https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/LICENSE
- **X1** https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/firebase-ai/app/src/main/java/com/google/firebase/quickstart/ai/feature/text/TravelTipsViewModel.kt
- **D1** https://firebase.google.com/docs/ai-logic/app-check
- **O2** https://github.com/firebase/firebase-android-sdk
- **L2** https://github.com/firebase/firebase-android-sdk/blob/f27760badb7672cf6dc5d23ac030f69b7683c535/LICENSE
- **X2** https://github.com/firebase/firebase-android-sdk/blob/f27760badb7672cf6dc5d23ac030f69b7683c535/ai-logic/firebase-ai/src/main/kotlin/com/google/firebase/ai/type/Exceptions.kt
- **D2** https://firebase.google.com/docs/ai-logic/quotas
- **O3** https://github.com/firebase/firebase-admin-node
- **L3** https://github.com/firebase/firebase-admin-node/blob/d721044a86d209e4adcffc2416d12b31f8f4e259/LICENSE
- **X3** https://github.com/firebase/firebase-admin-node/blob/d721044a86d209e4adcffc2416d12b31f8f4e259/src/auth/token-verifier.ts
- **D3** https://firebase.google.com/docs/auth/admin/verify-id-tokens
- **O4** https://github.com/panva/jose
- **L4** https://github.com/panva/jose/blob/66a657ab11c5b2f347f2ae21e5200d37497d1c15/LICENSE.md
- **X4** https://github.com/panva/jose/blob/66a657ab11c5b2f347f2ae21e5200d37497d1c15/src/jwt/verify.ts
- **D4** https://github.com/panva/jose/blob/66a657ab11c5b2f347f2ae21e5200d37497d1c15/docs/jwt/verify/README.md
- **O5** https://github.com/cloudflare/templates
- **L5** https://github.com/cloudflare/templates/blob/b1c138230907c63c8f98d1a420d3e53e698eb4b7/LICENSE
- **X5** https://github.com/cloudflare/templates/blob/b1c138230907c63c8f98d1a420d3e53e698eb4b7/llm-chat-app-template/src/index.ts
- **D5** https://developers.cloudflare.com/ai-gateway/usage/providers/google-ai-studio/
- **O6** https://github.com/android/security-samples
- **L6** https://github.com/android/security-samples/blob/2ea592a337032cdec930b122415019aa1ff79428/LICENSE
- **X6** https://github.com/android/security-samples/blob/2ea592a337032cdec930b122415019aa1ff79428/BiometricLoginKotlin/app/src/main/java/com/example/biometricloginsample/CryptographyManager.kt
- **D6** https://developer.android.com/privacy-and-security/keystore
- **O7** https://github.com/android/car-samples
- **L7** https://github.com/android/car-samples/blob/9be0b199986770cd5cde7765765c0eaef0758d15/LICENSE
- **X7** https://github.com/android/car-samples/blob/9be0b199986770cd5cde7765765c0eaef0758d15/car-lib/CarGearViewerKotlin/automotive/src/main/java/com/example/cargearviewer/MainActivity.kt
- **D7** https://developer.android.com/training/cars/samples
- **O8** https://github.com/android/views-widgets-samples
- **L8** https://github.com/android/views-widgets-samples/blob/2238cc873501f9cda63605051de11832bb736a8a/LICENSE
- **X8** https://github.com/android/views-widgets-samples/blob/2238cc873501f9cda63605051de11832bb736a8a/MultiWindowPlaygroundKotlin/Application/src/main/java/com/android/multiwindowplayground/MainActivity.kt
- **D8** https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode
- **X11** https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/firebase-ai/app/src/main/java/com/google/firebase/quickstart/ai/MainActivity.kt
- **X12** https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/auth/app/src/main/java/com/google/firebase/quickstart/auth/kotlin/EmailPasswordFragment.kt
- **X13** https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/firebase-ai/app/src/main/java/com/google/firebase/quickstart/ai/feature/text/ChatViewModel.kt
- **X14** https://github.com/firebase/firebase-android-sdk/blob/f27760badb7672cf6dc5d23ac030f69b7683c535/appcheck/firebase-appcheck/src/main/java/com/google/firebase/appcheck/internal/DefaultTokenRefresher.java

[R1]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/docs/CODEX_PROGRESS.md
[R2]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/infra/gemini-relay/README.md
[R3]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/infra/gemini-relay/src/index.ts
[R4]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/infra/gemini-relay/package.json
[R5]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/infra/gemini-relay/wrangler.jsonc
[R6]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/infra/gemini-relay/wrangler.existing.jsonc
[R7]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
[R8]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/build.gradle.kts
[R9]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/build.gradle.kts
[R10]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/settings.gradle.kts
[R11]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/gradle/wrapper/gradle-wrapper.properties
[R12]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/AndroidManifest.xml
[R13]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/docs/GEMINI_RESPONSE_FIX.md
[R14]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/docs/GEMINI_MINIMUM_POC.md
[R15]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/docs/CARITDA_ANALYTICS_AI_INTEGRATED_REPORT.md
[R16]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/docs/CARITDA_DESIGN_SYSTEM.md
[R17]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/docs/CARITDA_VIRTUAL_DRIVING_AI_POC.md
[R18]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ai/AiExplanation.kt
[R19]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ai/AnalysisExplanation.kt
[R20]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ai/RelayDiagnostics.kt
[R21]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/validation/java/com/nextdrive/pleos/GeminiPocActivity.kt
[R22]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/validation/java/com/nextdrive/pleos/PocTokenSession.kt
[R23]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/validation/java/com/nextdrive/pleos/SyntheticSocFixture.kt
[R24]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/validation/AndroidManifest.xml
[R25]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/analytics/VehicleAnalytics.kt
[R26]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/analytics/AnalysisStore.kt
[R27]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/PleosReadClient.kt
[R28]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt
[R29]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ui/CaritdaDesign.kt
[R30]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/7ca8797f5e262e86a38507fdfbc7e9af0e25e48b/infra/gemini-relay/test/security.js
[F1]: https://firebase.google.com/docs/ai-logic/get-started?platform=android
[F2]: https://firebase.google.com/docs/app-check
[F3]: https://firebase.google.com/docs/app-check/android/play-integrity-provider
[F4]: https://firebase.google.com/docs/app-check/android/custom-provider
[F5]: https://firebase.google.com/docs/ai-logic/app-check
[F6]: https://firebase.google.com/docs/android/android-play-services
[F7]: https://firebase.google.com/docs/ai-logic/faq-and-troubleshooting
[F8]: https://firebase.google.com/docs/ai-logic/quotas
[F9]: https://firebase.google.com/docs/auth/admin/verify-id-tokens
[F10]: https://firebase.google.com/docs/auth/admin/manage-sessions
[F11]: https://firebase.google.com/docs/auth/android/password-auth
[F12]: https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser
[F13]: https://firebase.google.com/docs/auth/limits
[F14]: https://firebase.google.com/docs/auth
[F15]: https://firebase.google.com/docs/projects/billing/spend-caps
[F16]: https://firebase.google.com/docs/ai-logic/pricing
[F17]: https://firebase.google.com/docs/app-check/custom-resource-backend
[F18]: https://firebase.google.com/docs/ai-logic/models
[M1]: https://aistudio.google.com/docs/models/gemini-3.5-flash-lite
[M2]: https://aistudio.google.com/docs/pricing
[M3]: https://aistudio.google.com/docs/rate-limits
[M4]: https://aistudio.google.com/docs/api-key
[M5]: https://aistudio.google.com/docs/terms
[M6]: https://ai.google.dev/api/generate-content?hl=en
[C1]: https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json
[C2]: https://developers.cloudflare.com/workers/runtime-apis/web-crypto/
[C3]: https://developers.cloudflare.com/workers/runtime-apis/nodejs/
[C4]: https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/
[C5]: https://developers.cloudflare.com/durable-objects/platform/pricing
[C6]: https://developers.cloudflare.com/workers/platform/pricing
[P1]: https://document.pleos.ai/en/docs/pleos-only/app-registration-guide/app-security-guide
[P2]: https://source.android.com/docs/automotive/start/what_automotive
[P3]: https://developer.android.com/training/cars/platforms/automotive-os/google-play/google-services
[P4]: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
[P5]: https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app
[P6]: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
[P7]: https://developer.android.com/google/play/app-updates?hl=en
[P8]: https://developer.android.com/studio/run/emulator-networking-address
[P9]: https://source.android.com/docs/devices/admin/multi-user-testing
[P10]: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
[P11]: https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode
[P12]: https://developer.android.com/privacy-and-security/keystore
[P13]: https://developer.android.com/privacy-and-security/cryptography
[P14]: https://developer.android.com/privacy-and-security/security-key-attestation
[G1]: https://appdevchallenge.auto-hmg.io/guide/schedule
[G2]: https://appdevchallenge.auto-hmg.io/guide/submission
[E1]: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
[E2]: https://github.com/google-gemini/deprecated-generative-ai-android
[O1]: https://github.com/firebase/quickstart-android
[L1]: https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/LICENSE
[X1]: https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/firebase-ai/app/src/main/java/com/google/firebase/quickstart/ai/feature/text/TravelTipsViewModel.kt
[D1]: https://firebase.google.com/docs/ai-logic/app-check
[O2]: https://github.com/firebase/firebase-android-sdk
[L2]: https://github.com/firebase/firebase-android-sdk/blob/f27760badb7672cf6dc5d23ac030f69b7683c535/LICENSE
[X2]: https://github.com/firebase/firebase-android-sdk/blob/f27760badb7672cf6dc5d23ac030f69b7683c535/ai-logic/firebase-ai/src/main/kotlin/com/google/firebase/ai/type/Exceptions.kt
[D2]: https://firebase.google.com/docs/ai-logic/quotas
[O3]: https://github.com/firebase/firebase-admin-node
[L3]: https://github.com/firebase/firebase-admin-node/blob/d721044a86d209e4adcffc2416d12b31f8f4e259/LICENSE
[X3]: https://github.com/firebase/firebase-admin-node/blob/d721044a86d209e4adcffc2416d12b31f8f4e259/src/auth/token-verifier.ts
[D3]: https://firebase.google.com/docs/auth/admin/verify-id-tokens
[O4]: https://github.com/panva/jose
[L4]: https://github.com/panva/jose/blob/66a657ab11c5b2f347f2ae21e5200d37497d1c15/LICENSE.md
[X4]: https://github.com/panva/jose/blob/66a657ab11c5b2f347f2ae21e5200d37497d1c15/src/jwt/verify.ts
[D4]: https://github.com/panva/jose/blob/66a657ab11c5b2f347f2ae21e5200d37497d1c15/docs/jwt/verify/README.md
[O5]: https://github.com/cloudflare/templates
[L5]: https://github.com/cloudflare/templates/blob/b1c138230907c63c8f98d1a420d3e53e698eb4b7/LICENSE
[X5]: https://github.com/cloudflare/templates/blob/b1c138230907c63c8f98d1a420d3e53e698eb4b7/llm-chat-app-template/src/index.ts
[D5]: https://developers.cloudflare.com/ai-gateway/usage/providers/google-ai-studio/
[O6]: https://github.com/android/security-samples
[L6]: https://github.com/android/security-samples/blob/2ea592a337032cdec930b122415019aa1ff79428/LICENSE
[X6]: https://github.com/android/security-samples/blob/2ea592a337032cdec930b122415019aa1ff79428/BiometricLoginKotlin/app/src/main/java/com/example/biometricloginsample/CryptographyManager.kt
[D6]: https://developer.android.com/privacy-and-security/keystore
[O7]: https://github.com/android/car-samples
[L7]: https://github.com/android/car-samples/blob/9be0b199986770cd5cde7765765c0eaef0758d15/LICENSE
[X7]: https://github.com/android/car-samples/blob/9be0b199986770cd5cde7765765c0eaef0758d15/car-lib/CarGearViewerKotlin/automotive/src/main/java/com/example/cargearviewer/MainActivity.kt
[D7]: https://developer.android.com/training/cars/samples
[O8]: https://github.com/android/views-widgets-samples
[L8]: https://github.com/android/views-widgets-samples/blob/2238cc873501f9cda63605051de11832bb736a8a/LICENSE
[X8]: https://github.com/android/views-widgets-samples/blob/2238cc873501f9cda63605051de11832bb736a8a/MultiWindowPlaygroundKotlin/Application/src/main/java/com/android/multiwindowplayground/MainActivity.kt
[D8]: https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode
[X11]: https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/firebase-ai/app/src/main/java/com/google/firebase/quickstart/ai/MainActivity.kt
[X12]: https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/auth/app/src/main/java/com/google/firebase/quickstart/auth/kotlin/EmailPasswordFragment.kt
[X13]: https://github.com/firebase/quickstart-android/blob/0f76ce2548184f83f461c002978045d57ec9218d/firebase-ai/app/src/main/java/com/google/firebase/quickstart/ai/feature/text/ChatViewModel.kt
[X14]: https://github.com/firebase/firebase-android-sdk/blob/f27760badb7672cf6dc5d23ac030f69b7683c535/appcheck/firebase-appcheck/src/main/java/com/google/firebase/appcheck/internal/DefaultTokenRefresher.java

[P15]: https://developer.android.com/identity/data/autobackup
[P16]: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/sign
[P17]: https://www.rfc-editor.org/rfc/rfc7638
