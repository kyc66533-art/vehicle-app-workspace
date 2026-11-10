# AI 운영 적용 — 한 번의 승인 후 실행

**2026-11-10 운영 묶음 최종 승인 완료.** 아래 통합 실행은 이미 승인된 Worker 교체·반복 정책·모델 ON·선택 기록 실모델 최대1회·실패 시 OFF 복구를 수행합니다. 같은 승인을 다시 요청하지 않습니다. 기존 v1/v2 배포 문제나 Firebase 등록을 다시 설정하지 않습니다.

## 이번에 사용할 통합 실행

일반 PowerShell에서 다음 두 줄만 실행합니다. 이 실행을 쓰는 동안 아래 과거 A/B 수동 블록을 따로 중복 실행하지 않습니다. 새 빌드/Secret 입력/토큰 복사/계정 등록은 필요 없습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
node test/apply-operations.mjs --approved
```

실행기는 승인 번들 SHA256/설정·정책·단일100% 활성 버전·기존 Secret명/BUDGET namespace를 확인하고, 모델 OFF→ON 순으로 배포합니다. 각 단계에서 실제 공개 URL의 health/무인증401과 관리 API의 바인딩·운영 변수를 대조합니다. Firebase 등 기존 바인딩을 보존하며 Secret 값은 읽거나 출력하지 않습니다. 기존 단일100% 배포가 아니거나 공개 인증·바인딩 검사가 실패하면 다음 단계로 진행하지 않습니다. 이미 배포를 시도한 뒤의 실패는 모델 OFF 복구를 한 번만 시도하고 끝냅니다.

앱 인증/사적 기록은 추출하지 않으므로 다음 세 곳만 사용자가 확인합니다. 정상 확인은 Y, 실제 실패는 FAIL이며 각 횟수는 질문마다 숫자만 입력합니다. 이는 재승인이 아니라 실제 화면 검사 결과입니다. 개인정보/토큰/모델 본문은 입력하지 않습니다. 형식 오류는 서버 요청 없이 해당 항목만 다시 입력합니다.

1. **배포 전:** 일반 앱에서 기존 AI 설명→앱 완전 종료·재실행→기존 설명 확인→현재 이용량 조회. 로그인/기존 설명 정상·불확실없음을 Y로 확인하고 TEST/정식 횟수를 각각 입력합니다. 실제 현재값을 입력하며1/1을 고정하지 않습니다.
2. **모델 OFF 후:** 앱 횟수 조회에서 반복(한국 시간 일일·월간) 정책/모델 꺼짐/불확실없음을 확인하고 TEST/정식/잔여를 각각 숫자로 입력합니다. 배포 전 누적이 동일하고 잔여1~5여야 ON으로 진행합니다.
3. **모델 ON 후:** 앱 횟수 조회에서 모델 켜짐/이전 사용량 동일/불확실없음을 확인한 뒤 개인정보 없는 선택 기록의 새 설명을 **딱 한 번** 요청합니다. `AI 설명을 받았어요` 성공 안내와 새 본문을 확인합니다. 예전 설명이 남았다는 이유로 성공 처리하지 않습니다. 기존 설명 보기→종료·재실행→기존 설명 보기→횟수 조회 후 정상 여부를 Y로, TEST/정식/잔여는 각각 숫자로 입력합니다. 정식 시도가 정확히1회만 증가해야 합니다. 같은 KST 날짜라면 잔여도 정확히1 감소해야 합니다.

오류·UNKNOWN·모델 실패·조건 불일치면 `FAIL`을 입력하고 새 생성을 반복하지 않습니다. 실행기는 OFF 복구 후 `OPERATION_STOPPED`와 안전한 코드만 남깁니다. 과거 누적이나 실패를 환불하지 않습니다. 잔여0은 원장 오류와 구분해 `QUOTA_WAIT`로 대기하며 모델 OFF를 유지합니다. 최종 PASS는 실제 앱 확인을 포함하며, 일반 앱에 자동화 테스트를 실행하지 않습니다.

**현재 실제 적용 결과(2026-11-10):** 모델 OFF 배포/바인딩·정책·공개401 검사 성공, 기존 TEST1·정식5 보존, 사용자 확인 잔여0·불확실없음입니다. 최초 이월에서 과거 기기 사용5를 첫 일에 보수적으로 반영하므로 오늘 새 요청을 허용하지 않습니다. 모델 OFF 복구까지 확인했고 승인한 신규 시험은 아직0회입니다. 사용량/날짜/UID를 바꿔 우회하지 않습니다.

**2026-11-11 한국 시간00:00 이후** 아래 한 줄로 기존 OFF 배포와 앱 검사를 반복하지 않고 인증된 잔여 조회부터 이어갑니다. 같은 승인 범위이며 새 승인은 필요 없습니다. 실제 조회 잔여가 여전히0이면 그대로 대기하고 ON/모델 요청은 하지 않습니다. 일일 갱신 후에도 계정·기기·서비스 월간 상한은 남습니다.

```powershell
node test/apply-operations.mjs --approved --resume-quota
```

작업 폴더는 위와 같은 `infra\gemini-relay`입니다. 이 옵션은 확인된 잔여0·모델OFF·시험 미발행 기록에만 허용합니다. BUDGET namespace·운영 정책을 다시 읽어 확인하고, 잔여가 양수일 때에만 첫 ON 배포와 실제 최대1회 단계로 넘어갑니다. 앱/Worker 기능 코드의 재빌드나 OFF 반복 배포는 없습니다.

비공개 실행 기록: `.tools/worker-operations/activation-session.json`. 동일 파일이 있으면 보통 재실행은 기존 결과만 보여 주고 배포·모델 시험을 반복하지 않습니다. 중단/강제 종료 시 파일을 임의 삭제하지 말고 상태를 확인합니다. **첫 실행의 배포 전 입력 형식 오류만** 사용자 정상 확인 후 `--resume-input`으로 이어갈 수 있고, **확인된 잔여0 대기만** 위 `--resume-quota`로 이어갈 수 있습니다. 예전 중단 기록은 별도 파일로 보존합니다. 시험이 발행된 기록에서는 두 옵션 모두 거부합니다. `trial_instruction_issued=true`는 모델 요청 안내 이후 결과가 불확실할 수 있다는 뜻이며 새 요청을 허용하는 증거가 아닙니다. 프로세스가 강제 종료돼 OFF 복구 문구를 확인하지 못했으면 관리자가 Cloudflare에서 `MODEL_ENABLED=false`를 확인해야 합니다.

검사 완료 문구: `OPERATIONAL_ACCEPTANCE_PASS`. 아래의 A/B는 통합 실행의 동작 설명으로 보존하며 이번에는 별도로 실행하지 않습니다.

## 승인할 묶음

1. 대상: 기존 `caritda-ai-relay-dev`, 같은 workers.dev URL. 최종 `.bundle/portable-worker.mjs` SHA256은 **8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123**입니다.
2. 변경: 코드와 비밀정보 없는 `RECURRING_POLICY` 변수만 추가합니다. 기존 `PORTABLE_POLICY`, `GEMINI_API_KEY`, Firebase 설정/UID·기기 인가 목록은 입력·교체·삭제하지 않습니다. 기존 users의 enabled/after/devices는 계속 검사합니다. 모델 OFF 검증 후 같은 설정에서 모델 ON만 진행합니다.
3. 예산: 계정/기기 각각 일5·월100, 서비스 전체 **월300회**, 분1회, **월 USD3 예약예산**(요청당1센트). KST 일/월 경계로 자동 갱신하며 달마다 반복됩니다. 기존 사용도 최초 월에 보수적으로 반영하므로 첫 월 잔여가300보다 작을 수 있습니다. 실제 청구액·세금·Cloudflare 비용·다른 서비스의 같은 키 사용은 별개입니다. 실제 모델 시험은 **개인정보 없는 선택 기록 최대1회, 자동 재시도 없음**, 이 월간 상한 안에서 수행합니다.
4. 보존: BUDGET binding/class/migration/객체와 `budget`/`portable:v2`를 그대로 사용합니다. 사용/실패/UNKNOWN 환불이나 초기화가 없습니다. 기존 월 스키마에 device 카운터만 비파괴 추가하며 구버전 반복 정책의 월 카운터도 보수적으로 이월합니다.
5. 검증: 모델 OFF 공개 health/무인증401 → 앱의 인증된 횟수 조회에서 반복 정책/기존 누적/UNKNOWN 확인 → 모델 ON → 같은 앱에서 지정 기록 신규 설명 최대1회 → 기존 설명 재조회 시 누적 불변 확인.
6. 위험·복구: 공급자429/인증/통신 오류는 여전히 가능하며 실패도 예약을 유지합니다. UNKNOWN이면 멈추고 새 요청을 만들지 않습니다. 문제가 생기면 모델 OFF로 재배포합니다. 필요 시 RECURRING_POLICY를 빈 문자열로 바꾸면 기존 Secret 정책으로 되돌아가지만, 그 정책이 만료/소진된 상태라면 차단이 정상입니다. 원장을 이전 백업으로 되돌리지 않습니다. 코드 롤백이 필요하면 관리자가 직전 v2 버전을 확인하고 **모델 OFF 검증**과 함께 수행합니다.

## 준비된 파일

`test/prepare-recurring-policy.mjs`는 로컬 설정 파일만 만듭니다. 계정/UID/Secret 입력이 필요 없으며 외부 배포/모델 호출이 없습니다. Codex가 이미 다음 두 파일을 준비했습니다.

- `C:\Users\kyc66\NextDrive\.tools\worker-operations\model-off.json`
- `C:\Users\kyc66\NextDrive\.tools\worker-operations\model-on.json`

새 PC에서 설정 파일이 없는 경우에만 프로젝트 `infra/gemini-relay`에서 `node test/prepare-recurring-policy.mjs`를 실행합니다. 기존 파일은 임의 덮어쓰지 않으며 별도 검토합니다. 생성 파일은 절대 경로를 사용하므로 PC가 바뀌면 그 PC에서 새로 만듭니다. Firebase 앱 설정이나 일반 앱의 로컬 DB를 복사하는 절차가 아닙니다.

### A. 승인 후 모델 OFF 교체

일반 PowerShell에서 다음 블록을 한 번 실행합니다. Codex의 현재 실행 환경에는 Cloudflare 관리 연결이 없고 상위 폴더/네트워크 제한이 있으므로 관리자가 실행해야 합니다. 실패하면 같은 배포를 반복하지 말고 해당 단계의 안전한 오류 코드만 확인합니다. 정책·키·토큰·모델 원문은 공유하지 않습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
$approvedBundleHash = '8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123'
if ((Get-FileHash -LiteralPath .bundle\portable-worker.mjs -Algorithm SHA256).Hash -ne $approvedBundleHash) { throw '승인한 번들이 아닙니다. 중단합니다.' }
node test/deployment-status.mjs --cloudflare
if ($LASTEXITCODE -ne 0) { throw '배포 전 읽기 진단 실패. 중단합니다.' }
node node_modules/wrangler/bin/wrangler.js deploy .bundle/portable-worker.mjs --no-bundle --config ../../.tools/worker-operations/model-off.json --keep-vars
if ($LASTEXITCODE -ne 0) { throw '교체 실패. 반복 실행하지 마세요.' }
node test/issue-public-status.mjs
if ($LASTEXITCODE -ne 0) { throw '공개 모델 OFF/401 검증 실패. 모델을 켜지 마세요.' }
```

Cloudflare Worker 편집기에 코드를 다시 붙여넣거나 Secret을 새로 만들 필요가 없습니다. 원본 source 설정과 배포할 파일을 혼동하지 않도록 번들을 명시했습니다. 예상 공개 결과: v2 release, model false, 무인증401/AUTH_REQUIRED, pass true. 읽기 진단 결과는 `.tools`의 비공개 보고서만 사용합니다.

일반 CARITDA의 기존 AI 설명은 그대로 볼 수 있습니다. `AI 이용 가능 횟수 확인 · 모델 호출 없음`에서 **한국 시간 일일·월간 정책**, 실제 기존 TEST/정식 시도/잔여/불확실 요청을 확인합니다. 이전 보고값1/1을 현재값으로 고정하지 않습니다. UNKNOWN·인가 오류·정책 오류가 있으면 여기서 중단하고 모델 OFF를 유지합니다. 기존 설명 cold restart 검수도 이 단계에서 추가 호출 없이 할 수 있습니다.

### B. 같은 승인 범위에서 모델 ON·최대1회 검증

모델 OFF 단계의 인증·원장 보존·불확실없음이 확인된 뒤에만 실행합니다. 별도 Secret 수정은 없습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
if ((Get-FileHash -LiteralPath .bundle\portable-worker.mjs -Algorithm SHA256).Hash -ne '8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123') { throw '승인 번들 불일치' }
node node_modules/wrangler/bin/wrangler.js deploy .bundle/portable-worker.mjs --no-bundle --config ../../.tools/worker-operations/model-on.json --keep-vars
if ($LASTEXITCODE -ne 0) { throw '활성화 실패. 모델 요청하지 마세요.' }
```

앱에서 횟수 확인 → 개인정보 없는 기존 기록 선택 → 전송 요약 펼치기 → **새로 설명 생성1회** → 실제 MODEL 결과 확인 → 기존 설명 보기 → 앱 종료·재실행 후 기존 설명 보기를 확인합니다. 성공/실패 모두 한 번으로 끝내며, 실패했다고 재생성하지 않습니다. 이후 일반 사용의 일5/월간 상한 운영은 위 반복 예산 승인 범위에 포함되며 매일 Secret을 바꾸지 않습니다.

### C. 운영 중단

같은 최종 번들을 `model-off.json`으로 다시 배포하면 모델을 끄면서 원장·인증·결과는 유지합니다. 이 조치는 승인된 복구 범위에서만 수행합니다. 재설치·AVD 초기화·DO 삭제·사용량 환불로 복구하지 않습니다.

## 비용 근거와 한계

[Gemini Developer API 공식 가격](https://ai.google.dev/gemini-api/docs/pricing?hl=ko)의 Gemini3.5 Flash-Lite 텍스트 Standard 요금을 확인했습니다. 조회 변형에 입력0.15/출력1.25와 입력0.30/출력2.50 USD/백만 토큰이 함께 보여, **더 높은0.30/2.50**으로 산정했습니다. Vertex/Agent Platform 요금을 섞지 않습니다. 배포 시 관리자는 계정의 실제 요금/무료 할당량을 확인해야 합니다.

요청은 도구·검색 없이 JSON 봉투8192바이트 이하, 출력1024토큰 이하(SOC512)입니다. 비용 계획에서 입력을16384토큰으로 넉넉하게 잡으면 1회 `16384×0.30/1,000,000 + 1024×2.50/1,000,000 = USD0.0074752`입니다. 이를1센트 예약으로 올림해300회/월3달러를 제안합니다. 이는 로컬 비용 계산이며 실제 usageMetadata 청구내역을 측정한 결과가 아닙니다. 요금 인상·다른 API 경로·세금·플랫폼 비용까지 포괄하는 계정 전체 hard cap은 아닙니다. 최초 달은 과거 사용을 이월하고 불확실 요청도 예약하므로 과소계산을 피합니다.

원장 비용은 예약월 기준입니다. 월경계 전후 dispatch/실제 청구시점 차이와 UNKNOWN 정산은 관리자 확인 대상입니다. 향후 실제 청구 정산·대규모 이용은 별도 설계하며 이번에는 승인한 소규모 공모전 운영 상한을 사용합니다.
