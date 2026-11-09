# CARITDA Portable AI v2 — 배포 전 관리자 절차

이 문서는 기존 v1 안내보다 우선합니다. **소스 게시·로컬 번들은 서버 배포 승인이 아닙니다.** 현재 서버를 Codex가 배포하거나 모델을 호출하지 않았습니다. 먼저 모델 호출 없이 인증을 확인하고, 별도 승인된 횟수만 실제 모델을 시험합니다.

## 현재 준비물과 보존 대상

- Firebase 프로젝트 `caritda`, 이메일/비밀번호 제공자와 일반/TEST Android 앱 등록은 사용자가 준비했습니다. Auth **24.2.0**으로 실제 Pleos 로그인·세션 복원·ID 토큰 갱신을 확인했습니다. 25.0.0은 현재 Kotlin 2.2.21과 메타데이터가 맞지 않아 사용하지 않습니다.
- 기존 Worker 주소와 `GEMINI_API_KEY` Secret을 그대로 사용합니다. 키를 새로 만들거나 표시하지 않습니다.
- 기존 `BUDGET` 바인딩, `BudgetGate` 클래스, `caritda-budget-v1` migration, namespace, `caritda-gemini-poc-global-v1` 객체, `budget` 저장값을 유지합니다. 새 namespace/DO를 만들지 않습니다.
- v2는 같은 객체에 `portable:v2` 원장만 추가합니다. 첫 인가 요청에서 기존 사용량을 체크포인트로 보존합니다. **승인 한도는 기존 사용량+v2 요청 시도의 합계**입니다. 실패도 소비하며 UNKNOWN은 재전송·환불하지 않습니다.
- 새 진입점은 `src/portable-worker.ts`입니다. v1 소스와 preflight는 남지만, 새 진입점의 v1 모델 경로는 꺼집니다. 기존 v1 토큰으로 새 예산을 우회할 수 없습니다.

## 1. 로컬 검증과 번들

일반 PowerShell에서 이 폴더를 열고 실행합니다. 다운로드가 필요한 최초 의존성 준비는 사용자가 이미 완료했습니다.

```powershell
npm run check
node test/portable-security.mjs
node test/bundle-portable.mjs
```

마지막 명령은 `.bundle/portable-worker.mjs`만 생성합니다. 배포나 Gemini 호출을 하지 않습니다. Codex sandbox에서는 esbuild의 상위 디렉터리 접근이 거부되므로 일반 PowerShell 실행이 필요했습니다. 이 원인을 우회해 보안 정책을 변경하지 않았습니다. 최종 소스로 다시 생성한 번들을 사용해야 합니다. `THIRD_PARTY_NOTICES.md`의 jose MIT 고지를 유지하세요.

## 2. 승인 후 — 모델 OFF 상태의 기존 Worker 교체

**관리자 승인 후에만** 다음 작업을 합니다.

1. Cloudflare Dashboard → **Workers & Pages → caritda-ai-relay-dev → Settings → Variables and Secrets**. 기존 Secret을 유지합니다. `PORTABLE_ENABLED=false`, `MODEL_ENABLED=false`, `POC_ENABLED=false`, `FIREBASE_PROJECT_ID=caritda`를 준비합니다.
2. Firebase Console → **caritda → Authentication → Users**에서 허용할 계정의 UID를 복사합니다. UID는 아래 Worker 정책의 키에만 넣습니다. 비밀번호·ID 토큰·서비스 계정 파일은 필요 없고 채팅에 보내지 않습니다.
3. 같은 Worker의 **Variables and Secrets → Add → Secret**에 `PORTABLE_POLICY`를 추가합니다. 아래 값의 `<UID>`를 실제 UID로, `expires_at`을 현재부터 최대31일 이내 Unix 초로 바꿉니다. 이 예의 모델 허용 횟수는 모두0입니다. 기기2대는 PC A/B 등록용 제안이며 관리자 승인값을 사용합니다.

```json
{"global_total":0,"per_minute":0,"expires_at":0,"users":{"<UID>":{"enabled":true,"after":0,"total":0,"daily":0,"devices":2,"device_total":0}}}
```

`expires_at=0` 예시는 그대로 두면 거부됩니다. 설정하려는 만료 Unix 초는 일반 PowerShell에서 `[DateTimeOffset]::UtcNow.AddDays(1).ToUnixTimeSeconds()`로 확인할 수 있습니다. 이 값은 만료 계산이며 시스템 시간을 바꾸지 않습니다.

4. **Bindings**에서 기존 `BUDGET → BudgetGate`가 유지되는지 확인합니다. 기존 Durable Object를 삭제·재생성하거나 migration을 다시 등록하지 않습니다.
5. **Edit code**에서 최종 `.bundle/portable-worker.mjs` 전체를 기존 Worker 모듈에 넣고 **Deploy**합니다. 외부 관리 작업이므로 반드시 승인 후 실행합니다. CLI를 쓴다면 기존 환경을 보존하는 `wrangler.existing.jsonc`가 새 진입점을 가리킵니다. 승인 없이 `npm run deploy`를 실행하지 않습니다.
6. `PORTABLE_ENABLED=true`, `MODEL_ENABLED=false`, `POC_ENABLED=false`로 설정합니다. 정책이 없거나 만료되면 거부됩니다. `/health`의 `release=caritda-portable-ai-v2`, 모델 비활성화를 확인합니다. 이는 모델 성공 검사가 아닙니다.
7. 일반/TEST 앱의 **차량 정보 → 차량 데이터 분석 → AI로 쉽게 설명**에서 해당 계정으로 최초 로그인하고 전송 안내에 동의하여 기기를 등록합니다. 등록에는 최근5분 이내 로그인과 실제 Keystore 서명이 필요합니다. 앱에서 **AI 이용 가능 횟수 확인 · 모델 호출 없음**을 눌러 기존 TEST 사용량과 승인 잔여를 확인합니다. 인증 없는 v2 요청은401이어야 합니다. 모델은 꺼져 있으므로 실제 모델 요청은 발생하지 않아야 합니다.

## 3. 실제 Gemini 시험 — 별도 승인·실제 잔액 확인 후

- 기존 테스트의 ‘합성값 최대1회’ 승인은 정식 v2 실제 SOC·추가 예산의 포괄 승인이 아닙니다. 실제 기기 등록과 잔액을 먼저 확인한 후 **해당 UID/기기, 에뮬레이터 SOC, 허용 시도 수/비용**을 승인받습니다.
- `global_total`은 초기화된 새 예산이 아닙니다. 기존5회 내에서 남은1회를 쓴다면 합계 상한5를 유지하고 UID/일/기기 상한을 각각1로 설정할 수 있습니다. 기존 소진이면 추가 승인 없이는0입니다. 추가 grant는 관리자가 명시적으로 승인한 경우에만 합계 상한을 늘립니다.
- `per_minute`는 최대2이며 정책이 요구하는 모든 상한을 같은 DO transaction에서 예약합니다. 동시 모델 요청은1입니다. 불명확한 이전 요청은 새 호출을 막으며, 이를 지워서 한도를 만들지 않습니다. 모델 요청 시도 수 상한이지 금액 hard cap은 아닙니다.
- 승인된 정책 저장 후에만 `MODEL_ENABLED=true`로 변경하고 앱의 AI 버튼을 명시적으로 누릅니다. Gemini가 실제 응답하고 서버·앱 검증을 통과한 경우만 MODEL로 표시합니다. 실패 시 자동 반복 호출하지 않습니다. 시험 후 모델을 다시 끌 수 있습니다.
- 이번 v2는 **에뮬레이터 SOC 요약만** 허용합니다. 실차 정보는 별도 데이터 이용·비용·개인정보 검토 전 거부합니다. 사용자 증상·이름·VIN·위치는 전송하지 않습니다.

## 프로토콜·보안과 한계

- Firebase JWT: `jose` RS256 검증, 공식 Google X509 고정 주소, kid/cache/cooldown, aud/iss/sub/exp/iat/auth_time/이메일 인증 제공자 확인. UID allowlist와 `after`(재인증 기준)를 매 요청 검사합니다. Firebase에서만 계정을 중지하면 기존 토큰에 지연이 있을 수 있으므로 Worker의 `enabled=false`도 적용합니다. 서비스 계정 키는 사용하지 않습니다.
- 기기 증명: P-256 Android Keystore 공개 JWK/RFC7638. 12개 UTF-8 길이 접두 필드, P1363 64바이트 서명. 목적·UID·기기/등록ID·thumbprint·POST·고정 origin/path·operation·정확 payload hash·nonce·Unix 만료를 묶습니다. Android의 원 서명 DER은 엄격하게 변환합니다. 하드웨어 attestation을 검증한 것은 아닙니다.
- Aside의 권장 헤더 대신 `challenge_id`, `signature`, 고정 payload 문자열을 JSON envelope로 보냅니다. 12필드 증명은 동일하며 nonce/목적/만료는 서버 도전값에서 가져옵니다. 기기ID/키버전만 전용 헤더입니다. 본문은 서버8KiB/앱4KiB, 모델 응답16KiB로 제한합니다.
- operation을 모델 호출 전에 영속화합니다. 재송신은 동일 intent/operation을 복구하며 다른 본문은409입니다. 완료 본문은 fresh READ 증명을 요구합니다. `RESERVED → DISPATCHING` CAS 후 모델 전송은 transaction 밖에서 한 번만 합니다. 전송 후 네트워크/종료 불확실성은 UNKNOWN이며 비용 예약과 동시성 슬롯을 남깁니다. exactly-once 외부 결제를 보증하지 않습니다.
- 결과는 서버24시간, 작업 메타데이터는30일 후 정리하도록 alarm을 예약합니다. UNKNOWN 최소 메타데이터/비용 원장/등록은 운영 해제 전 유지합니다. Cloudflare alarm 실제 실행·보관 삭제는 배포 후 검증 대상입니다. 로컬 결과는 암호화하여 동의 철회/로그아웃까지 보관합니다.
- 동의 철회는 먼저 서버 기기 동의를 해제하고 로컬 AI 결과·동의를 지웁니다. 서버 연결에 실패해도 로컬 전송은 중단하고 관리자에게 서버 해제를 안내합니다. 이미 전송된 요청·모델 제공자 보관까지 되돌리는 기능이 아닙니다. `active=false`인 관리자 폐기 기기는 재등록으로 복구되지 않습니다. 관리자 폐기 UI는 아직 없습니다.
- 제한된 자연어 필터와 숫자 금지만으로 모든 의미적 오답을 검출한다고 주장하지 않습니다. 수치는 원 분석에서 로컬 표시하고 응답의 전체 typed facts/hash/action을 검사합니다. 모델이 원본 DB나 문제 상태를 변경할 경로는 없습니다.

## PC B

완성 APK 설치에는 새 Firebase/Gemini/Cloudflare 설정이 필요 없습니다. 지원 Pleos 환경·인터넷·승인 계정과 기기 여유가 필요합니다. PC B는 별도의 Keystore와 로컬 DB를 사용합니다. PC A의 Firebase 세션·DB·백업·서명 개인키를 복사하지 않습니다. 자세한 절차와 실제/미검증은 `docs/PORTABLE_AI_ACCEPTANCE.md`를 확인하세요. 현재 제출용 정식 서명과 실제 다른 PC 시험은 완료되지 않았습니다.
