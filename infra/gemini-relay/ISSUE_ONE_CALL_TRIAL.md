# 승인된 선택 기록 Gemini 1회 시험

2026-11-10 사용자 승인: **개인정보 없는 선택 기록 한 건, Gemini 최대1회, 모델 ON/데이터 전송/발생 가능한 비용**. 출력 최대1,024토큰. 기존 TEST1/정식0/잔여0을 확인했으며 허용 UID1개·after0·devices2를 사용자가 확인했습니다. 이 안내는 다른 계정·이후 추가 호출의 승인이 아닙니다.

## 1. 앱에서 먼저 확인

일반 CARITDA의 **기존 기록 한 건 → AI로 쉽게 설명**을 모델 OFF일 때 엽니다. 현재 MODEL_DISABLED 안내는 정상입니다. 새 앱은 본문 전송 전에 모델·잔액·불확실한 이전 요청을 확인합니다.

전송할 본문에 개인 이름·연락처 등이 있으면 사본에서 뺍니다. 원래 기록은 바뀌지 않습니다. `전송 요약 확인`은 미리보기일 뿐 외부 전송이 아닙니다. 원 작성자·메모·시간·이력·차량값·다른 기록은 보내지 않습니다. **불확실한 이전 요청이 있으면 아래 시험을 진행하지 말고 보고합니다.**

가능하면 이 AI 화면을 열어 둔 채 관리자 설정을 진행합니다. 이미 동의가 저장된 상태에서 모델 ON 이후 새로 기록의 AI 버튼을 누르면 그 한 번의 클릭으로 요청이 시작될 수 있습니다.

## 2. 관리자 한 번 — 기존 정책 한도만 변경

아래 블록은 사용자에게 확인받은 **UID 하나/after0/devices2** 설정에만 적용합니다. 다른 승인 UID가 있거나 기존 설정이 다르면 실행하지 않습니다. 기존 UID를 다른 UID로 교체하지 않습니다.

Firebase Console → caritda → Authentication → Users에서 **방금 기기 등록에 성공한 동일 계정**의 UID를 복사합니다. UID는 API 키나 ID 토큰이 아닙니다. 채팅에 보내거나 소스코드에 넣지 않습니다.

일반 PowerShell에서 실행합니다. 별도 ps1 파일·정책 변경·새 Secret 키 발급은 없습니다. `GEMINI_API_KEY`에는 접근하지 않습니다. JSON은 현재 프로세스의 표준입력으로 기존 PORTABLE_POLICY에 전달하며 값 자체를 출력하지 않습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
$caritdaTrialUid = Read-Host '기기 등록한 기존 Firebase UID만 입력하세요 (API 키/토큰 아님)'
if ($caritdaTrialUid -notmatch '^[A-Za-z0-9_-]{1,128}$' -or $caritdaTrialUid -like 'AIza*' -or $caritdaTrialUid.Length -eq 43) { $caritdaTrialUid = $null; throw 'Firebase UID를 확인하세요. 비밀키/토큰은 입력하지 않습니다.' }
$caritdaTrialUsers = @{}
$caritdaTrialUsers[$caritdaTrialUid] = @{ enabled = $true; after = 0; total = 1; daily = 1; devices = 2; device_total = 1 }
$caritdaTrialPolicy = @{ global_total = 2; per_minute = 1; expires_at = [DateTimeOffset]::UtcNow.AddDays(1).ToUnixTimeSeconds(); users = $caritdaTrialUsers }
try {
    $caritdaTrialPolicy | ConvertTo-Json -Depth 5 -Compress | node node_modules/wrangler/bin/wrangler.js secret put PORTABLE_POLICY --config wrangler.existing.jsonc --name caritda-ai-relay-dev
    if ($LASTEXITCODE -ne 0) { throw '정책 저장 실패. 모델을 켜지 말고 오류 코드만 알려 주세요.' }
} finally {
    $caritdaTrialUid = $null
    $caritdaTrialUsers = $null
    $caritdaTrialPolicy = $null
}
```

이 숫자는 **사용량 초기값이 아니라 상한**입니다. 기존 DO의 budget.total=1/ids 및 portable:v2 사용량은 수정하지 않습니다. 누적 한도2 = 과거1 + 이번 최대1입니다. UID·일·기기 상한1과 분당1도 동시에 적용됩니다. 같은 계정의 SOC/다른 기록도 이 한도를 공유하므로 이번에는 선택 기록만 시험합니다. 24시간 뒤 정책은 만료됩니다. 통신/모델 실패도 예약된 시도는 돌려주지 않습니다.

정책 업로드는 기존 Worker 설정 버전을 갱신할 수 있지만 코드/namespace/BUDGET/다른 Secret을 재설정하는 명령은 없습니다. 임의 UID 추가·기기 삭제·사용량 삭제 명령도 없습니다.

## 3. 모델 ON — 설정값만 변경

Cloudflare Dashboard → Workers & Pages → **caritda-ai-relay-dev** → Settings → Variables and Secrets → 기존 **MODEL_ENABLED**의 Edit → Text 값 `true` → Save/Deploy를 누릅니다. 기존 PORTABLE_ENABLED=true, POC_ENABLED=false, FIREBASE_PROJECT_ID, BUDGET, Gemini Secret은 그대로 둡니다. 새 Worker나 DO를 만들지 않습니다. 코드 편집/번들 업로드는 필요 없습니다.

PowerShell의 공개 확인은 모델을 호출하지 않습니다.

```powershell
Invoke-RestMethod 'https://caritda-ai-relay-dev.kyc66533.workers.dev/health' | Select-Object release,features,portable_enabled,model_enabled
```

release v2, features에 selected-issue-v1/firebase-keys-manual-v1, portable true/model true인지 확인합니다. `issue-public-status.mjs`는 모델 OFF 검사이므로 이 단계에서는 사용하지 않습니다.

앱의 **AI 이용 가능 횟수 확인**에서 다음을 확인합니다. 이 버튼은 모델을 호출하지 않습니다.

- 기존 TEST 사용 **1회**, 정식 요청 시도 **0회**
- 누적 승인 상한 **2회**, 승인 잔여 **1회**
- 모델 **켜짐**, 불확실한 이전 요청 **없음**

다르면 AI 요청하지 않고 원인을 보고합니다. 서버와 앱도 같은 조건을 다시 검사합니다. 기기/날짜/앱 재시작으로 새 한도가 생기지 않습니다.

## 4. 실제 요청 딱 한 번

현재 AI 화면의 **AI로 쉽게 설명**을 한 번 누릅니다. 새 화면에서 Main의 AI 버튼을 이미 눌러 로딩이 시작됐다면 안쪽 버튼을 추가로 누르지 않습니다. 로그인·토큰·체크박스를 반복 입력하지 않습니다.

기대 결과: **Gemini가 작성한 설명**과 가능한 원인 / 추가로 확인할 사항 / 정비소 점검이 필요한 경우. 원문·점검 상태·다른 기록은 변하지 않아야 합니다. 모델이 근거/형식/안전 검증을 통과하지 못하면 설명을 표시하지 않습니다. Mock나 로컬 규칙 설명으로 성공을 대체하지 않습니다.

실패하면 오류 코드만 보고하고 반복 요청하지 않습니다. 재전송/연결 실패는 같은 operation의 결과만 조회하며 추가 모델 시도를 자동 생성하지 않습니다. UNKNOWN을 지우거나 한도를 더 늘리지 않습니다.

## 5. 시험 종료

성공 또는 오류 확인 뒤 같은 Cloudflare 설정에서 **MODEL_ENABLED=false**로 되돌립니다. PORTABLE_ENABLED는 유지해 이미 요청한 결과/잔액을 조회할 수 있게 합니다. 정책·DO 누적 기록은 그대로 둡니다.

앱의 횟수 확인: 실제 provider dispatch가 있었다면 **기존1/정식1/잔여0**, 모델 꺼짐이어야 합니다. 이 숫자는 시도 수이며 성공 판정은 실제 Gemini 설명 표시와 함께 확인합니다. provider 전송 전 거부됐다면 정식0일 수 있습니다. 불확실함이 있으면 원인을 확인하기 전 추가 시도하지 않습니다.

일반 앱을 다시 실행해 로그인/동의와 이전 AI 설명이 유지되는지 확인합니다. 새 승인 상한이 없으면 다음 실모델 요청은 제한되며 기존 답변은 이전 설명으로 구분합니다. 일반 사용·시연 반복 한도는 관리자가 별도로 승인할 수 있지만 이번1회를 자동으로 연장하지 않습니다.

보고할 내용은 성공/오류 코드, 기존·정식·잔여 횟수, 모델 OFF 복원 여부입니다. 비밀번호·UID·키·토큰·문제 본문·모델 원문은 채팅/GitHub에 보내지 않습니다.
