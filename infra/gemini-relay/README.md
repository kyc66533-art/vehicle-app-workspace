# CARITDA Gemini 최소 중계 — 배포 기본 꺼짐

대상 Worker: `caritda-ai-relay-dev`, 모델: `gemini-3.5-flash-lite`. 기존 `GEMINI_API_KEY` Secret은 유지하며 값을 조회하거나 GitHub/APK에 넣지 않습니다. 이 폴더의 소스 게시가 배포·계정 설정·유료 호출 승인을 뜻하지 않습니다.

## 보안 경계

- `/v1/synthetic-soc`는 POST/JSON/4KiB만 받습니다. 임시 앱 토큰·24시간 이내 만료·켜짐 설정·Durable Object binding이 없으면 fail closed입니다. 브라우저 Origin/쿼리/압축 본문/추가 필드/임의 프롬프트·모델·URL·실제 SDK 출처를 거부합니다.
- 입력은 SOC80→60→18,20% 미만이라는 **고정 합성 fixture**만 허용합니다. 사용자 기록/운전자/VIN/위치/전체 DB를 받지 않습니다. Google에 보내는 내용도 고정된 합성값/기준/설명 제약뿐입니다.
- 전역 하나의 SQLite-backed `BudgetGate`가 transaction으로 **분당2회/누적5회**를 예약합니다. POP별 메모리/KV 제한을 전체 비용 상한이라고 부르지 않습니다. 실패한 모델 요청도 예약을 소비하며 자동 재시도/분 단위 전체 상한 초기화가 없습니다. request UUID 재전송은409입니다.
- `GEMINI_API_KEY`는 `env.GEMINI_API_KEY`에서만 읽고 Google의 `x-goog-api-key` 헤더에만 사용합니다. URL·로그·응답에는 넣지 않습니다. 앱 토큰은 다른 값입니다. 응답 원문/공급자 오류를 그대로 전달하지 않습니다.
- 모델12초/출력256토큰/응답16KiB 제한입니다.429/인증/네트워크/시간초과/형식·근거 오류를 구분합니다. 자유 문장 숫자/부품·수리·안전 확정은 거부하고 검증된 facts만 앱이 수치로 표시합니다. 이 필터를 전체 의미 안전성 인증이라고 주장하지 않습니다.
- `GET /health`는 모델 호출 없이 계약/정책을 보여줍니다. 인증된 `POST /v1/preflight`는 동일 입력 검증과 실제 DO binding 상태·남은 횟수를 확인하며 모델을 호출하거나 횟수를 예약하지 않습니다. 실제 호출 경로는 preflight 뒤 별도 POST입니다.
- 배포는 `POC_ENABLED=false`/만료 미설정으로 시작합니다. 기본 상태로 Gemini가 호출되면 안 됩니다. Cloudflare 계정/DO 과금·Gemini 무료 한도는 사용자 계정 조건에 따르며 무료라고 보장하지 않습니다.

## 사용자 승인 후 배포하는 두 가지 방법

먼저 검증 보고서의 로컬 결과를 확인하고 **기존 Worker 코드 교체·SQLite DO namespace 생성·새 임시 인증 설정·최대5회 모델 시험**을 승인해야 합니다. Codex는 Cloudflare 관리 연결 도구가 없고 Windows에서 Worker HEAD 연결도 실패했습니다. 계정 토큰/Google 키를 채팅에 보내지 마세요.

### A. Cloudflare Builds 사용 — PC Node 설치 없이

1. Cloudflare 대시보드 → **Workers & Pages → caritda-ai-relay-dev → Settings → Builds**의 Git 연결 항목을 엽니다. 화면에서 별도 **Builds** 탭으로 표시될 수도 있습니다.
2. GitHub 연결이 필요하면 이 저장소 `kyc66533-art/vehicle-app-workspace` 접근 권한을 직접 검토·승인합니다. 저장소를 공개할 필요는 없습니다. 연결 자체도 사용자 작업이며 Codex가 대신 계정 권한을 확대하지 않습니다.
3. Production branch는 `main`, **Root directory**는 `infra/gemini-relay`입니다. Build command는 `npm install && npm run check && npm run bundle`, Deploy command는 `npm run deploy`로 지정합니다. check/dry-run 실패 시 배포하지 않습니다. 이 단계는 Cloudflare의 Node/tsc/Wrangler에서 실제 확인해야 합니다.
4. 기존 Worker 코드/버전을 Dashboard의 Deployments에서 먼저 보존합니다. 이 설정은 Worker를 교체하고 `BudgetGate` SQLite namespace/migration을 생성합니다. Worker Secret `GEMINI_API_KEY`는 삭제하거나 새 값으로 덮어쓰지 않습니다.
5. 연결 시 자동 배포가 시작되거나 이후 main 변경이 재배포를 유발할 수 있습니다. 원하는 자동 배포 범위를 승인하지 않았다면 연결을 진행하지 말고 B를 사용하세요. 대상 watch path를 `infra/gemini-relay/**`로 제한하는 설정도 검토하세요. 기존 앱 코드 게시와 외부 배포 승인은 별개입니다.
6. 성공한 배포의 `/health`가 `contract=caritda-gemini-synthetic-v1`, `auth_required=true`, `enabled=false`인지 확인합니다. 원래 Worker가 이 계약과 다르면 아직 이번 소스로 교체된 것이 아닙니다.

### B. 이미 Node/npm이 있는 일반 PowerShell

Node/npm이 없는 PC에는 사용자 승인 없이 설치하지 않습니다. 설치가 승인돼 준비된 경우에만 다음을 실행합니다. Codex에서 아래 명령의 성공을 미리 주장하지 않습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
npm install
npm run check
npm run bundle
npx wrangler login
# 위 검사 성공과 배포 승인이 끝난 뒤에만 실행
npm run deploy
```

로그인은 사용자 브라우저에서 직접 합니다. `npm install`이 생성하는 lockfile은 별도 검토해 재현성에 활용하세요. 현재는 CLI가 없어 lockfile/tsc/Wrangler 성공을 만들어 기록하지 않습니다.

## 임시 앱 인증 — API 제공자 키와 다름

배포 후 **Workers & Pages → 해당 Worker → Settings → Variables and Secrets → Add**에서 설정합니다.

| 이름 | 종류 | 값/의미 |
|---|---|---|
| `GEMINI_API_KEY` | Secret | 이미 등록된 값 유지, 조회/공유 금지 |
| `TEST_APP_TOKEN` | Secret | 무작위32바이트의 base64url43자. Google 키가 아님 |
| `TEST_TOKEN_EXPIRES_AT` | Text | 현재부터24시간 이내 UTC ISO 시각, 시험은2시간 권장 |
| `POC_ENABLED` | Text | 처음은 `false`, 아래 인증 검증 후만 `true` |

앱 토큰 생성 예시(일반 PowerShell, **사용자 인증 설정 승인 뒤**):

```powershell
$pocBytes = New-Object byte[] 32
$pocRng = [Security.Cryptography.RandomNumberGenerator]::Create()
$pocRng.GetBytes($pocBytes)
$pocRng.Dispose()
$pocToken = [Convert]::ToBase64String($pocBytes).TrimEnd('=').Replace('+','-').Replace('/','_')
Set-Clipboard -Value $pocToken
# Worker의 TEST_APP_TOKEN Secret 입력란과 TEST 앱 토큰 입력란에 직접 붙여넣습니다.
# 토큰을 채팅·소스·파일·스크린샷에 넣지 마세요.
$pocExpiry = [DateTimeOffset]::UtcNow.AddHours(2).ToString('o')
$pocExpiry  # 비밀값이 아닌 만료시각만 출력
```

앱 입력 후 클립보드/클립보드 기록을 정리하고, 시험이 끝나면 `POC_ENABLED=false`로 설정하고 임시 토큰을 폐기하세요. 코드 재배포 시 wrangler의 기본 vars가 꺼짐/만료 미설정으로 돌아갈 수 있습니다. 영구 활성화를 위해 기본 방어를 제거하지 않습니다.

## 모델 비활성 상태에서 먼저 확인

아래 요청은 모델을 호출하면 안 됩니다. 일반 PowerShell이 접속 가능한 환경에서 수행합니다.

```powershell
$pocBase = 'https://caritda-ai-relay-dev.kyc66533.workers.dev'
Invoke-RestMethod "$pocBase/health"
# 다음은 HTTP401이어야 합니다. 200/모델 문장이면 활성화하지 마세요.
try {
    Invoke-WebRequest -UseBasicParsing -Method Post "$pocBase/v1/preflight" -ContentType 'application/json' -Body '{}'
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

정확한 합성 payload는 TEST 앱의 `SyntheticSocFixture`가 생성합니다. 앱에서 임시 토큰과 배포/합성 전송 동의를 입력하고 **보안 연결 확인 · 모델 호출 없음**을 누르면 인증된 preflight만 수행합니다. 모델 비활성 상태에서 인증·저장소 확인 성공/남은5회/활성화false를 확인해야 합니다. 원격 binding/분산 상태는 로컬 mocked 검사만으로 통과 처리하지 않습니다. 이 검증 후에만 활성화합니다.

## TEST 앱에서 실제 응답 확인

1. 기존 Pleos AVD를 정차 상태로 둡니다. 일반 앱은 교체할 필요가 없습니다. 이번 validation APK를 기존 데이터를 유지하며 설치합니다.
2. `[TEST] CARITDA 검증 전용` → 차량 정보 → **Gemini 연결 시험 · 합성 SOC**를 엽니다.
3. 보안 배포·인증 거부·preflight를 확인한 뒤 Worker의 `POC_ENABLED=true`를 승인해 설정합니다.
4. Google 키가 아닌43자 임시 앱 토큰을 앱에 붙여넣고, 배포/호출 승인 및 합성 데이터 전송 동의를 확인합니다.
5. **실제 Gemini 설명 요청**을 한 번 누릅니다. 앱이 인증된 preflight를 검증한 뒤 모델을 요청합니다. 실패 시 자동으로 재시도하지 않습니다.
6. 실제 HTTP/계약/모델ID/facts/hash/문구 검증이 통과한 경우에만 **Gemini 실제 모델 응답**과 왕복시간을 표시합니다. 이 시간에는 preflight·네트워크도 포함됩니다. Mock를 이 화면에 주입하지 않습니다.
7. 토큰은 요청 시 입력창에서 지우고 메모리에서만 사용합니다. 화면 종료/취소/안전 제한 시 연결·표시를 중단합니다. 이미 서버에 도달한 요청의 처리·사용량까지 취소됐다고 보장하지 않습니다. 화면 캡처는 FLAG_SECURE로 차단됩니다.
8. 시험 뒤 비활성화/토큰 폐기합니다. 일반 문제/관측 DB에 합성 값을 저장하지 않습니다. 실제 모델 응답 표시를 확인하기 전에는 전체 연결 성공이라고 발표하지 않습니다.

공식 근거: [Gemini generateContent](https://ai.google.dev/api/generate-content), [모델 목록](https://firebase.google.com/docs/ai-logic/models), [Cloudflare Secrets](https://developers.cloudflare.com/workers/configuration/secrets/), [Builds 설정](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/), [SQLite DO transaction](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/).
