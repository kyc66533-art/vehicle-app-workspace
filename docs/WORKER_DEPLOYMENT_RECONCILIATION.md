# Worker 공개 URL / 활성 버전 불일치 — v2 공개 응답 확인

2026-11-10, Asia/Seoul. 기준 main `4f8cc6f66e8950c533940b54c6ba0b8884885b31`. 초기에는 합격 기준을 충족하지 않았고, 승인된1회 교체 후 **공개 v2 응답·모델OFF 합격**을 확인했습니다. 배포 성공 메시지·100%만으로 판정하지 않았습니다. Android/일반 앱 데이터/Firebase 설정은 변경하지 않았습니다.

## 최종 실제 결과 — 2026-11-10 01:16:35 KST

사용자가 명시적 모델OFF 교체1회를 승인하고 일반 PowerShell에서 실행 완료를 알렸습니다. 관리 연결 도구가 없어 Codex가 직접 Cloudflare 배포에 성공한 것이 아닙니다. 갱신된 로컬 진단 보고서의 실제 공개 응답과 제어면 결과를 직접 대조했습니다.

| 항목 | 실제 결과 |
|---|---|
|활성 배포 ID|`638eef06-c013-43b1-bb44-973f7d698e62`|
|활성 버전 ID|`e6bb4d6a-87c1-4e62-a53f-c6ecada4b6f4`,100%,source=wrangler|
|공개 GET /health|200, release=`caritda-portable-ai-v2`, model_enabled=false|
|공개 무인증 POST /v2/account/status|503, code=`PORTABLE_DISABLED`, error.release=`caritda-portable-ai-v2`|
|현재 버전 지정 health|동일한200/v2/modelOFF|
|BUDGET|기존과 같은 namespace fingerprint·BudgetGate 클래스|
|바인딩|기존9개 이름/타입 차이0. GEMINI_API_KEY·PORTABLE_POLICY·TEST_APP_TOKEN Secret 이름 유지|
|진단 판정|`PUBLIC_V2_MODEL_OFF_PASS`|

503은 v2 진입점이 응답했지만 기능이 아직 비활성/바인딩 허용 조건을 통과하지 않은 상태입니다. **Firebase 인증·기기 등록·AI 기능 활성화가 완료됐다는 뜻이 아닙니다.** 이번 목표인 v1/v2 배포 불일치 해소와 모델OFF는 확인했습니다. PORTABLE_ENABLED/Firebase 설정을 추가 변경하거나 Gemini를 시험하지 않았습니다.

Secret 값·원격 원장 숫자 자체는 읽지 않았습니다. namespace와 바인딩 유지, 저장소/예산 코드 불변, 초기화·모델·인증 요청 없음으로 보존 경계를 확인했으며 ‘원격 누적 숫자를 전후 직접 조회했다’고 하지 않습니다. 안내한 before-repair.json 사본은 로컬에 생성되지 않았으므로 이를 백업 성공으로 기록하지 않습니다. 비교에는 교체 전에 실제 읽어 둔16:06:44 UTC 메타데이터의 namespace fingerprint와 바인딩 목록을 사용했습니다.

확정된 원인은 **공개 URL이 의도한 v2가 아닌 v1 기본 handler 동작을 제공한 배포 코드/진입점 불일치**입니다. 검증된 v2 번들을 명시한 교체 후 같은 공개 URL에서 v2가 반환됐습니다. 과거 정확히 어느 명령/모듈 선택이 구형 동작을 올렸는지, 원격 구버전 소스의 바이트까지는 확보하지 못했으므로 특정 과거 명령을 확정 원인으로 지목하지 않습니다. 아래 초기 실패 기록을 보존합니다.

## 실제 확인

- 사용자 일반 PowerShell에서 `node test/deployment-status.mjs --cloudflare`를 실행한 결과 파일을 직접 읽었습니다. Codex 자체의 공개 URL 호출은 EACCES, web 도구도 접근 실패였으므로 이를 Codex 네트워크 성공으로 바꾸어 보고하지 않습니다.
- 실제 배포 ID는 `33c3375c-baeb-4cc0-922b-9a1ce2921e16`, 버전 ID는 `3265582e-15e3-4327-be46-7d651f536a8a`입니다. 버전12, source=wrangler,100% 배포, 생성시각2026-11-09 15:48:31 UTC입니다. 배포와 버전은 서로 다른 ID입니다.
- 2026-11-09 16:06:44 UTC 공개 GET `/health`는200/contract=`caritda-gemini-synthetic-v1`, release 없음, enabled=false였습니다. POST `/v2/account/status`는404/NOT_FOUND였습니다. 두 요청은 인증정보·Cookie 없이 수행했고 Cache-Control=no-store 응답, Age 없음이었습니다.
- `Cloudflare-Workers-Version-Overrides`로 해당 현재 버전을 지정한 GET도 같은v1 응답이었습니다. 이 헤더가 적용됐다는 독립적인 version metadata 응답은 없으므로 헤더만으로 원격 소스 파일을 다운로드했다고 하지 않습니다. 다만 현재100% 할당과 공개 응답을 함께 대조했습니다.
- 실제 활성 버전 메타데이터에서 BUDGET→BudgetGate, GEMINI_API_KEY/PORTABLE_POLICY Secret 이름을 확인했습니다. Secret/정책 값은 출력·요청하지 않았습니다. BUDGET namespace는 원값 대신 SHA256으로 보존 비교합니다.

## 로컬 기본 export 실행 결과

문자열 검색만으로 판정하지 않고 실제 default.fetch를 로컬에서 호출했습니다. v2 번들에도 legacy 코드가 포함되므로 v1 문자열이 있다는 사실만으로 v1이라고 할 수 없습니다.

| 파일 | GET /health | POST v2 (꺼짐 / 활성+무인증) |
|---|---|---|
|`src/portable-worker.ts`|release=caritda-portable-ai-v2|503 PORTABLE_DISABLED /401 AUTH_REQUIRED, 둘 다 error.release=v2|
|`.bundle/portable-worker.mjs`|동일 v2|동일 v2|
|`.bundle/wrangler-diagnostic/portable-worker.js`|동일 v2|동일 v2|
|구형 `.bundle/index.js`|contract=caritda-gemini-synthetic-v1, release 없음|404 NOT_FOUND /404 NOT_FOUND|

구형 index.js SHA256은 `BAC61A3A109416CBE0BF4E36AF44668BEE02BC42E7D248AEAFB0649D81BECBF7`, 검증된mjs는 `24FAC496B41181FA7295E3A0AFFE1AB2BE7B2F9D6281C0BF2664D2E6DB3FD09A`입니다. old index.js의 기본 export는 `index_default → handle`, 올바른 파일들은 `portable_worker_default → handlePortable`입니다. 파일명 index.js 자체는 v1의 증거가 아닙니다. **현재 공개 응답은 로컬 구형 파일의 동작과 일치**합니다.

`wrangler.existing.jsonc`의 main은src/portable-worker.ts, name/keep_vars/workers_dev 및 BUDGET/class/migration은 정상입니다. 그러나 CLI의 위치 인수로 다른 JS를 주었거나 다른 설정/계정/배포 경로를 쓰면 이 main을 확인했다는 것만으로 올바른 업로드를 증명할 수 없습니다. 실제 과거 배포 명령/업로드 원문은 아직 확보하지 못했습니다.

## 판정과 아직 모르는 것

**확정:** 현재 공개 URL은 의도한 v2 기본 handler 동작을 보이지 않습니다. Firebase/Gemini 키·PORTABLE_POLICY 오류가 v2 `/health`의 release를 v1으로 바꾸는 코드 경로는 없습니다. 로컬 원본과 두 최신 번들의 기본 진입점은 올바릅니다.

**유력하지만 미확정:** 구형 index.js/잘못된 기본 모듈이 업로드됐을 가능성입니다. 어느 과거 명령에서 이 파일을 선택했는지, 동일 이름의 다른 계정/hostname을 보고 있는지까지 아직 확정하지 않습니다. 최신 버전이라는 사실은 내용이v2라는 뜻이 아닙니다.

버전 API/`versions view --json`는 메타데이터 조회이며 소스 코드 다운로드가 아닙니다. 응답의 script.etag는 불투명 식별자이고 로컬 파일 SHA256과 무조건 같은 해시라고 가정하지 않습니다. 실제 코드 확인이 추가로 필요하면 Dashboard의 해당 버전 코드 보기/다운로드가 제공되는 경우 그 버전을 명시해 사용합니다. 공식 content/v2 API는 현재 script content 조회이며 버전별 소스 endpoint를 임의로 만들지 않습니다. 계정 API 키/토큰을 채팅에 받거나 Wrangler 인증 파일에서 추출하지 않습니다.

### 라우팅·캐시 구분

- deployments status의100%는 선택한 계정/Worker의 제어면 정보입니다. 실제 배포 로그 URL 및 Dashboard Domains & Routes의 workers.dev가 이 문서의 공개 origin과 같은지 확인해야 합니다.
- 공개 HTTPS GET과 새 무인증 POST가 함께v1 동작이고 no-store라서 브라우저 GET 캐시만으로 설명하기 어렵습니다. 무조건 캐시 삭제·DNS/보안 설정 초기화를 하지 않습니다. CF-Ray는 요청 추적 ID이지 배포 버전 ID가 아닙니다.
- 직접 Version URL은 생산 주소와 host가 다릅니다. 현재 v2의 고정 ORIGIN 검사 때문에 `/health`가400 INVALID_PATH + error.release=v2일 수 있습니다. 이를 곧v1이라고 오판하거나 증명 검사를 위해 ORIGIN 제한을 풀지 않습니다. Version URL은 이미 활성화된 경우에만 참고하고 설정을 새로 켜지 않습니다.

## 읽기 전용 진단 — 이미 실행한 명령

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
node test/deployment-status.mjs --cloudflare
```

이 스크립트는 Wrangler `deployments status`/`versions view`와 health/무인증 status만 호출합니다. 모델·배포·설정 변경 함수는 없습니다. 보고서 `.tools/deployment-mismatch/report.json`은 Secret·정책 값/계정 이메일을 제외합니다. 종료0은 진단 수행 완료일 뿐 합격이 아닙니다. `acceptance=PUBLIC_V2_MODEL_OFF_PASS`를 별도로 봅니다.

## 최소 교체 방안 — 실제 배포는 별도 승인 전 실행하지 않음

이미 검증한 정확한mjs를 명시해1회만 교체하고 같은 공개 URL로 확인합니다. 기존 Worker의 **Domains & Routes 주소가 `caritda-ai-relay-dev.kyc66533.workers.dev`인지 먼저 확인**합니다. 다르면 실행을 중단합니다.

아래는 승인 후 일반 PowerShell에서 실행할 단일 순서입니다. 원본·DB·AVD를 삭제하지 않고 구형 파일도 임의 삭제하지 않습니다. 기존 read-only 보고서를 보존한 뒤 hash/default handler를 확인하므로 구형index.js가 선택되면 배포 전에 실패합니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
Copy-Item -LiteralPath ..\..\.tools\deployment-mismatch\report.json -Destination ..\..\.tools\deployment-mismatch\before-repair.json
if ((Get-FileHash -LiteralPath .bundle\portable-worker.mjs -Algorithm SHA256).Hash -ne '24FAC496B41181FA7295E3A0AFFE1AB2BE7B2F9D6281C0BF2664D2E6DB3FD09A') { throw '검토한 번들과 다릅니다. 배포하지 않습니다.' }
node test/verify-upload-entry.mjs .bundle/portable-worker.mjs
if ($LASTEXITCODE -ne 0) { throw 'v2 기본 진입점 검증 실패. 배포하지 않습니다.' }
# 아래 한 줄은 실제 코드 배포이므로 별도 승인 후에만 실행합니다.
node node_modules/wrangler/bin/wrangler.js deploy .bundle/portable-worker.mjs --no-bundle --config wrangler.existing.jsonc --name caritda-ai-relay-dev --keep-vars --var MODEL_ENABLED:false --var POC_ENABLED:false
if ($LASTEXITCODE -ne 0) { throw '배포 실패. 같은 명령을 반복하지 말고 오류를 확인합니다.' }
node test/deployment-status.mjs --cloudflare
```

코드 진입점은 명시된mjs입니다. `--keep-vars`로 기존 텍스트 설정을 유지하고 두 모델 플래그만false로 지정합니다. Secret는 기존 것을 그대로 사용합니다. `PORTABLE_POLICY`, 기존 BUDGET/class/migration/namespace/객체/원장에 변경·초기화 명령이 없습니다. 원격 원장 값 자체는 인증 없이 읽지 않으며 요청 카운터 보존을 확인하려고 모델을 호출하지 않습니다.

### 기대 결과와 중단 조건

1. 로컬 guard: LOCAL_V2_ENTRY_VERIFIED. old index.js에 대한 거부 시험도 통과했습니다.
2. 배포 로그의 URL이 정확히 위 origin이어야 합니다. 다른 계정/환경 주소이면 공개 URL 문제를 해결했다고 하지 않습니다.
3. 공개 `/health`: HTTP200, release=caritda-portable-ai-v2, model_enabled=false.
4. 무인증 POST `/v2/account/status`: 활성/바인딩 준비 시401 AUTH_REQUIRED 또는 꺼진 상태이면503 PORTABLE_DISABLED. 둘 다 error.release=caritda-portable-ai-v2. **404 NOT_FOUND면 실패**입니다. 503은v2 진입점 확인일 뿐 기능 활성화 완료가 아닙니다.
5. before/after 활성 버전·BUDGET namespace fingerprint·Secret/정책 이름을 비교합니다. Secret 값을 출력하지 않습니다. 새 release가 보여도 실제 Firebase 등록·AI 성공이라고 보고하지 않습니다.
6. 한 번 교체 후에도v1이면 재배포를 반복하지 않습니다. 계정/실제 workers.dev 주소·선택된version/코드·외부 배포 덮어쓰기·라우팅을 다시 대조하고 Cloudflare에 CF-Ray/시각/버전ID로 문의할 근거를 보존합니다.

## 초기 진단 단계 상태 — 아래 기록은 교체 전

- 진단 스크립트와 민감정보 필터4검사 통과. 현재 원본/최신 번들v2, 구형 번들v1 재현. upload guard는 최신mjs 허용/구형index 거부 검증.
- 실제 교체 배포는 아직 수행하지 않았습니다. **공개 URL 합격 기준은 여전히 미충족**입니다. 과거 배포 금지 지시를 임의로 해제하지 않고 위 한 번의 모델OFF 교체에 대한 승인이 필요합니다.

이후 사용자가1회 교체를 승인했고, 실행 후 최상단의 실제 공개 응답으로 합격을 확인했습니다. 초기 미충족을 삭제하거나 그 당시 이미 성공한 것처럼 바꾸지 않습니다. 다시 배포할 필요는 없습니다.

## 공식 근거

- [버전과 배포의 구분](https://developers.cloudflare.com/workers/versions-and-deployments/)
- [현재 배포 버전 override와 적용 제한](https://developers.cloudflare.com/workers/versions-and-deployments/version-overrides/)
- [versions view/deploy entry/keep-vars](https://developers.cloudflare.com/workers/wrangler/commands/workers/)
- [Worker content 조회](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/content/methods/get/)
