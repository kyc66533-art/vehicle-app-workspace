# 선택한 문제 기록 Gemini 설명 — 관리자 설정

기존 Firebase/caritda 앱 등록·v2 배포 불일치는 해결됐습니다. 재설정하지 않습니다. 이 문서는 **이번 문제 기록 API 추가 배포**만 다룹니다. 소스 게시나 과거 모델 OFF 승인은 새 배포·사용자 기록 전송·추가 비용 승인이 아닙니다.

**현재 최종 수정: 공개키 수신 실패 해결.** workerd가 `redirect: error`를 지원하지 않아 요청 전 TypeError가 났습니다. 인증서/SOC/선택 기록/기존 v1의 외부 요청을 `manual`로 바꾸고 3xx를 거부합니다. 인증서 fetch/X509 및 Gemini native fetch를 실제 로컬 workerd로 시험했습니다. 다음 블록은 이 수정본을 모델 OFF로 한 번 교체하는 절차이며 별도 승인 후 실행합니다. 이전 진단 전용 번들은 배포하지 않습니다.

## 1. 코드 교체·인증만 확인 (사전 승인 뒤)

기존 Worker, Secret, `PORTABLE_POLICY`, `BUDGET` 바인딩/namespace/`BudgetGate`/migration/객체/누적 `budget`, `portable:v2`를 유지합니다. 새 DO·새 원장을 만들지 않습니다. 승인된 기존 UID 정책에 등록 기기 여유와 유효한 만료가 있어야 합니다. 기존 정책이 0회이면 그대로 인증 시험이 가능합니다.

일반 PowerShell에서 한 번 실행합니다. 다른 디렉터리의 구형 index.js는 사용하지 않습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
if ((Get-FileHash -LiteralPath .bundle\portable-worker.mjs -Algorithm SHA256).Hash -ne 'CE59954A7122F44133C6D5E631B59B6B9821903ED73273E64E500E0D1C43DEE4') { throw '검토한 번들이 아닙니다. 배포를 중단합니다.' }
node test/verify-upload-entry.mjs .bundle/portable-worker.mjs
if ($LASTEXITCODE -ne 0) { throw '진입점 검사 실패' }
node test/firebase-keys.mjs .bundle/portable-worker.mjs
if ($LASTEXITCODE -ne 0) { throw '공개키/X509 실제 런타임 검사 실패' }
node test/provider-edge.mjs .bundle/portable-worker.mjs
if ($LASTEXITCODE -ne 0) { throw 'Gemini 전송/리디렉션 차단 검사 실패' }
# 사용자 승인 전 아래 실제 배포 명령을 실행하지 않습니다.
node node_modules/wrangler/bin/wrangler.js deploy .bundle/portable-worker.mjs --no-bundle --config wrangler.existing.jsonc --name caritda-ai-relay-dev --keep-vars --var PORTABLE_ENABLED:true --var MODEL_ENABLED:false --var POC_ENABLED:false
if ($LASTEXITCODE -ne 0) { throw '같은 배포를 반복하지 말고 실패한 단계만 알려 주세요.' }
node test/issue-public-status.mjs
```

기대 결과: release `caritda-portable-ai-v2`, features `selected-issue-v1`와 `firebase-keys-manual-v1`, portable true/model false, 무인증 문제 API 401 `AUTH_REQUIRED`, pass true. 이 요청에는 사용자 본문·인증값이 없으며 모델이 호출되지 않습니다. 기존 v2 배포 오류를 다시 조사하는 절차가 아닙니다.

일반 CARITDA에서 **기록 확인 → 한 건 선택 → AI로 쉽게 설명 → 최초 로그인 → 전송 안내 확인 → 동의하고 이 기기 등록 → AI 이용 가능 횟수 확인**을 누릅니다. 모델 OFF에서는 AI 설명 요청을 누르지 않습니다. 로그인 비밀번호·ID 토큰은 앱에만 입력/내부 사용하며 채팅에 보내지 않습니다. 표시되는 기존 TEST 사용·정식 요청 시도·승인 잔여·불확실 요청 유무만 확인합니다. 등록이 성공해야 다음 단계로 갑니다.

`RECENT_LOGIN_REQUIRED`이면 AI 화면의 로그아웃 후 같은 계정으로 로그인하고 동의합니다. 앱/AVD/DB 삭제는 필요 없습니다. `POLICY_INVALID/UID_NOT_APPROVED/DEVICE_LIMIT`이면 기존 정책의 UID·만료·승인 기기 수만 관리자가 확인합니다. 기기나 사용량을 삭제해 해결하지 않습니다.

## 2. 실제 모델 최대 1회 (별도의 데이터·비용 승인 뒤)

**2026-11-10 승인된 현재 절차:** 기존 TEST1/정식0/잔여0, UID1개/after0/devices2를 사용자에게 확인했고 선택 기록 최대1회·모델ON·비용을 승인받았습니다. 실제 실행은 [ISSUE_ONE_CALL_TRIAL.md](ISSUE_ONE_CALL_TRIAL.md)의 상한2/UID·일·기기1 및 상태 확인을 따릅니다. 아래 일반 설명이 이번 승인 한도를 늘리지 않습니다.

첫 시험은 **사용자가 고른 한 건의 증상 종류와 개인정보를 뺀 본문**입니다. 메모·작성자·발견 시각·이력·차량값·다른 기록은 제외합니다. 앱 전송 미리보기와 동의 후에만 보냅니다. Worker는 유효 요청을 예약한 뒤 Gemini `gemini-3.5-flash-lite`에 한 번 요청합니다. 실패도 시도 예산을 소비하며 자동 재호출하지 않습니다.

관리자만 Cloudflare Dashboard → Workers & Pages → caritda-ai-relay-dev → Settings → Variables and Secrets에서 기존 `PORTABLE_POLICY` Secret을 갱신합니다. 기존 Secret 값/UID/토큰을 터미널·스크린샷·GitHub·채팅에 출력하지 않습니다.

- 현재 합계 사용량 = 표시된 legacy_total + portable_attempts. 새 `global_total`은 **현재 합계 + 승인된 추가 시도 수**입니다(최대100). 기존 사용량을 0으로 만들지 않습니다.
- 해당 UID의 `total`, `daily`, `device_total`은 현재 각 누적값을 포함하는 상한입니다. 사용 이력이 있는 UID/기기에 숫자1을 무조건 넣지 않습니다. 상세 원장 확인이 필요하면 관리자가 직접 대조합니다. 기존 UID를 다른 UID로 바꾸어 예산을 우회하지 않습니다.
- 첫 승인에는 추가1회 이하, 분당1회, 짧은 정책 만료를 사용합니다. `expires_at`은 Unix 초이며 과거 또는31일 초과이면 거부됩니다. `after`와 기존 기기 상한은 유지합니다.
- UNKNOWN이 있으면 새 호출을 차단합니다. 원장/사건 삭제·예약 환불로 재시도하지 않습니다. 결과 조회만 사용합니다.
- 정책 저장 뒤 `MODEL_ENABLED=true`로 변경합니다. 앱에서 AI 버튼을 한 번 누릅니다. 실패하면 같은 버튼을 반복해 새 호출하지 말고 진단 코드를 확인합니다. 시험 뒤 MODEL_ENABLED=false로 되돌립니다.

이 상한은 **요청 시도 수 제한**이며 달러 금액 hard cap은 아닙니다. 모델 가격/무료 한도는 계정에 따라 달라집니다. 일상 이용은 관리자가 승인한 UID/기기·일·전역 누적 상한과 만료를 조정하는 방식입니다. 무제한 이용이나 기존 TEST 5회 정책 우회가 아닙니다.

## 사용자 경험·데이터 경계

첫 기록 AI 동의와 SOC 동의는 분리합니다. 동일 앱·계정·Keystore 기기는 재사용합니다. 이후 선택한 기록의 AI 버튼은 유지된 동의/로그인으로 요청하며 43자리 토큰·체크박스를 요구하지 않습니다. ID 토큰은 Firebase SDK가 갱신하고 APK에는 공통 장기 비밀키가 없습니다.

기록별 ID/내용 버전의 로컬 hash로 진행 중 요청과 결과를 분리하며 그 ID는 서버에 보내지 않습니다. 실패/중단 후에는 같은 요청의 결과를 조회합니다. 원문 수정/삭제 시 이전 답변을 새 기록 답변으로 섞지 않습니다. 사용자 DB/상태를 AI가 수정할 경로가 없습니다.

본문의 전화·이메일·VIN·URL 및 명백한 프롬프트 탈취 지시는 앱/서버에서 거부합니다. 이름 등 모든 개인정보와 의미적 공격을 완전히 자동 탐지하지 못합니다. 본문 전송 사본을 편집할 수 있고 원문은 보존합니다. 고정 시스템 지시·격리 JSON·도구 없음·한정 응답 스키마·근거 인용/hash/수치/조건부 표현 검증을 함께 사용합니다. 이것이 모든 AI 오답을 없애는 보증은 아닙니다.

서버 결과는 기존 정책대로24시간, 작업 메타데이터는30일(UNKNOWN/누적 사용량은 예외) 보관합니다. 원문 payload는 dispatch 완료/실패 후 삭제합니다. 공급자 자체 보관은 별도 이용 조건을 따르며 앱에서 지우는 것으로 취소되지 않습니다. 로컬 AI 결과는 암호화 sidecar에 보관하고 해당 범위 동의 철회/로그아웃 시 지웁니다. 원래 문제/이벤트/관측은 지우지 않습니다.
