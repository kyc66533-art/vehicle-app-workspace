# Gemini AI 안정화 — 로컬 검증 결과

2026-11-10 20:05 +09:00. 시작 main `f690679dd50a1081d6b3b7d463f49ab6e37d5d33`. 이번 서버 배포·Secret/정책 변경·실제 Gemini 호출은 모두 0회입니다.

## 실제 사용자 검수와 확인 한계

사용자는 일반 앱의 선택한 문제 기록에 실제 Gemini 설명이 나타났고, 뒤이은 MODEL_HTTP_ERROR/TOTAL_LIMIT 이후에도 기존 설명이 화면에 남았다고 확인했습니다. 마지막 사용자 확인값은 기존 TEST1/정식1/잔여0/불확실없음/model ON이며 현재 원장을 다시 조회한 값은 아닙니다. 과거 문서의 ‘실모델 미연결’은 당시 상태입니다. 이번 Codex의 fixture 검사를 실제 모델 호출로 바꾸어 해석하지 않습니다.

MODEL_HTTP_ERROR에는 공급자 HTTP 상태가 보존되지 않아 과거 429/5xx 등을 확정할 수 없습니다. 공급자 본문이나 계정 로그를 임의 수집하지 않았습니다. TOTAL_LIMIT는 기존 전역·UID·일·기기 상한을 함께 검사하는 코드이며, 마지막 확인된 잔여0/기존 누적1+정식1은 전역2 상한과 일치합니다. 실제 오류 시점의 어떤 제한이 최초 차단했는지는 미확정입니다. UI가 보이는 것과 cold restart 복원은 별개입니다.

## 앱 변경

- 선택 기록 화면 진입은 자동 모델 요청을 하지 않습니다. `기존 AI 설명 보기 · API 호출 없음`은 AES-GCM/Android Keystore sidecar만 읽습니다. `새로 설명 생성`은 명시적 동작이며 기존 성공 결과가 있으면 추가 생성 확인이 필요합니다. 서버의 인증/동의/잔액 preflight를 유지합니다.
- Client의 issue `explain(payload,fresh=false)`는 검증된 캐시를 우선 반환합니다. `fresh=true`만 신규 생성 경로입니다. busy 단일 작업·영속 pending/intent·UNKNOWN 복원은 기존 구조를 유지하며 불확실 요청을 새 intent로 자동 재시도하지 않습니다.
- 결과를 job별로 보존하고 교체 전 성공 결과는 history에 남깁니다. 최근 FAILED의 안전한 코드/시각은 별도 보존해 성공 결과와 구분합니다. 기록 ID는 **로컬 암호화 저장소 내부 연결에만** 추가하며 외부 payload에는 넣지 않습니다.
- 현재 선택 기록의 기존 job을 일치하는 hash로 연결하고 이후 내용 수정 시 이전 설명임을 표시합니다. 이미 수정된 구버전 opaque job은 원래 기록을 확정할 정보가 없어 자동 귀속하지 않습니다. 같은 내용의 과거 성공들은 저장되지만 전체 회차 선택 UI는 아직 없습니다. 소스/본문 추측으로 결과를 잘못 연결하지 않습니다.
- 앱 운전자 전환은 Firebase 계정 변경과 다릅니다. 로그아웃/동의 철회 시 AI 사본 삭제는 기존 개인정보 정책을 유지합니다. 차량 원문/관측/초안/네 상태를 지우지 않습니다.

## 오류·사용량 회계

400→MODEL_REQUEST_REJECTED, 401/403→PROVIDER_AUTH, 429→MODEL_QUOTA, 그 외 공급자 응답→MODEL_HTTP_상태코드입니다. 모델 원문/키/토큰은 진단에 포함하지 않습니다. JSON/finishReason/안전차단·인증서·앱네트워크 진단은 기존 검사를 유지합니다. 화면은 한국어 안내와 안전한 확인 코드를 제공합니다.

모델 dispatch 전에 예약한 요청은 성공·공급자 오류 모두 사용량에 남습니다. 네트워크/타임아웃·처리 결과 불명은 UNKNOWN으로 남아 동시 요청을 막으며 자동 환불/재호출하지 않습니다. 앱에서 저장 설명을 여는 것은 예약도 요청도 하지 않습니다.

## 선택형 반복 정책 — 배포·활성화 미완료

새 `recurring-quota.ts`는 기존 BUDGET 클래스/인스턴스/`budget`/`portable:v2`를 유지하고 선택적인 `recurring` 필드만 추가합니다. 새 Secret을 설정하지 않으면 기존 정책 그대로입니다. UID 허용·Keystore 서명·단기 challenge·동의·분당 제한·UNKNOWN 차단을 유지합니다.

`PORTABLE_POLICY`의 기존 필드를 보존한 채 관리자 승인 후 다음 항목을 추가할 수 있습니다. 아래는 형식이며 운영 예산 승인이 아닙니다.

| 항목 | 의미 |
|---|---|
| recurring.user_daily | 계정당 하루 최대5, KST 날짜 경계 |
| recurring.device_daily | 기기당 하루, user_daily 이하 |
| recurring.user_monthly | 계정당 월간 예약 상한 |
| recurring.service_monthly | 서비스 전체 월간 예약 상한 |
| recurring.monthly_micros | 월간 비용 예약 크레딧 상한, USD의 백만분의1 단위로 운영자가 산정 |
| recurring.reserve_micros | 한 모델 시도당 보수적으로 예약하는 크레딧 |
| expires_at | 반복 정책에서만0 허용. UID enabled/after로 인가·철회, 매일 Secret 갱신 불필요 |

기존 global_total/UID total·daily·device_total은 스키마 호환을 위해 남지만 recurring 모드의 신규 예약 상한에는 위 기간별 값을 사용합니다. `after`, `devices`, `per_minute`와 모델 OFF는 계속 적용됩니다. status에 quota_mode/월간 상한을 표시합니다.

최초 전환 월은 legacyCheckpoint+기존 portable.used를 서비스 월간 예약으로 보수적으로 이월하고, UID 누적도 첫 월/기기 첫 일 제한에 보수적으로 반영합니다. 이전 원장은 수정하지 않습니다. 이후 KST 월 경계에는 신규 기간의 카운터만 사용하므로 영구 누적 상한 때문에 계속 막히지 않습니다. 일 갱신은 월간 서비스·비용 제한을 갱신하지 않습니다. 같은 DO 트랜잭션에서 예약/원장을 commit하며 실패 시 되돌아갑니다. 중복 operation은 재예약하지 않습니다.

비용 크레딧은 **청구액 실측이 아닙니다**. 모델 가격·최대 입력/출력·환율/세금·요청 종류에 맞는 보수적 reserve 값과 별도 제공자 지출 모니터링이 필요합니다. 비용은 예약시점 월로 귀속되며 월말 예약의 다음 달 dispatch/불확실 요청 정산은 실제 청구와 다를 수 있습니다. UNKNOWN은 계속 차단하고 관리자 확인이 필요합니다. 서버 결과 TTL24h/operation30일 및 monolithic ledger 용량1000개는 기존 제한이므로 대규모 장기 서비스는 원장 분할·보관 설계를 별도 진행해야 합니다. 이번은 소규모 MVP용 정책 구현이지 과금 서비스 완성 선언이 아닙니다.

### 남은 관리자 승인·설정

1. 월간 계정/서비스 요청 수·월간 USD 예산·요청당 reserve·분당 한도를 정합니다. 임의의 무제한 정책은 만들지 않습니다.
2. 배포 전 기존 account/status·설정 및 DO 관리 백업으로 현재 사용/UNKNOWN을 확인합니다. 키/정책 원문/UID를 보고서에 공개하지 않습니다.
3. 별도 승인 후 모델 OFF 상태에서 새 코드만 배포하고 기존 Secret/BUDGET/원장을 보존합니다. `recurring` Secret은 기존 users 등과 병합하며 새 DO 생성/삭제/usage 환불을 하지 않습니다.
4. 인증된 status의 반복 정책/KST 기간·잔여를 확인한 뒤, 데이터 전송·모델 비용·최대 호출 수에 관한 별도 승인이 있을 때만 모델 ON 시험을 합니다. 현재는 서버/Secret/모델 설정을 변경하지 않았습니다.

승인 대상 번들: `infra/gemini-relay/.bundle/portable-worker.mjs`, SHA256 `2D6907AEBA69E25420C8D9B20DE47A74A235DEACCF7B222A8EBF562537F39418`. 소스가 바뀌면 이 해시는 재검증해야 합니다. 실제 배포 명령은 이번에 실행하지 않았습니다.

## 실제 검사

| 검사 | 결과 | 구분 |
|---|---|---|
| assembleDebug/assembleValidation/assembleValidationAndroidTest/lintDebug | BUILD SUCCESSFUL 2m6s, Lint 오류0/경고50 | Codex 오프라인 Gradle, 최신 소스 |
| 문제 AI | 41 PASS (기존38+캐시/암호화 저장소 재개/이전 버전3) | Pleos user10, validation 고유 fixture |
| Portable/AI UI/Gemini PoC/분석 계약 | 26+4+86+24=140 PASS | 같은 validation, 실제 모델0 |
| 서버 portable/issue | 64+42 PASS | 서명·원장·provider fixture |
| 반복 정책 | 단위12+실제 gate4 PASS | KST 일/월·이월·예산·40일 후 정책·기존 누적 유지 |
| workerd/native fetch/공개키 | 16+5+16 PASS | 새 번들 로컬 runtime, 외부 모델0 |
| HTTP404/500/502/503·네트워크 오류 | 10 PASS | SOC/issue 두 경로 fixture, 공급자 본문 비노출 |
| 원본 번들·Wrangler dry-run | 사용자 일반 PowerShell 성공 | 서버 배포·Secret 변경 없음 |
| 일반 앱 교체/실행 | install-r 성공, MainActivity user10 foreground | 운영 UI 계측 없음 |

서버 합계169, Android 관련181입니다. 이전 전체320/297 등의 검사를 이번에 전부 재실행한 것은 아닙니다. 일반/validation 기본 DB를 초기화하는 기존 suite는 보존 조건 때문에 실행하지 않았습니다. TalkBack/rotary/실차/장시간/PC B/Release/유료 실모델·새 정책 운영은 미검증입니다.

## 데이터 보존

변경 전후 일반/validation의 private 파일을 로컬 비공개 백업했습니다. **현재 일반 앱 문제24/이벤트95/운전자10/초안0**을 확인했습니다. 과거23을 고정값으로 삼지 않습니다. 시작 시 존재한184 DB의 스키마·user_version·모든 원본 행 다중집합·integrity/FK 검사가 통과했습니다. 일반 원본1021행 유지/정상 앱 진입 관측 등6행 추가, validation 원본8915행 유지입니다. 신규 검증 fixture DB2개는 validation에만 있습니다. DB/백업/원문/인증/캡처는 게시하지 않습니다.

일반 Debug APK SHA256 `A9A3A316915369BAEEF973BFBED5FEB63A0FEA310F2ADE7C4A7B64C6A24396F0`. Release APK는 이번 산출물이 아닙니다.

## 사용자가 추가 모델 호출 없이 확인할 순서

1. 일반 CARITDA → 성공했던 기록 → AI 화면을 열고 기존 설명을 확인합니다.
2. `기존 AI 설명 보기 · API 호출 없음`으로 다시 봅니다. 새로 설명 생성은 누르지 않습니다.
3. 앱을 완전히 종료·재실행하고 같은 기록에서 다시 확인합니다. 다른 기록 운전자로 전환한 뒤에도 원 작성자/설명을 확인합니다.
4. 필요하면 이용 가능 횟수 조회만 하여 기존 누적이 늘지 않았는지 확인합니다. 이 조회는 Gemini 호출이 아닙니다.

일반 앱의 실제 성공 결과 cold restart/운전자 전환은 사용자 검수 대기입니다. 검증 앱의 새 암호화 Store/Client 인스턴스 복원·네트워크0 검사는 통과했지만 이를 사용자 계정 결과의 실검수로 대체하지 않습니다.

게시: GitHub 연결 도구로 검증된 소스와 문서만 게시합니다. 최종 SHA는 작업 완료 보고에서 제공하며 자기 자신의 SHA를 넣기 위한 반복 커밋은 만들지 않습니다. UI 협업은 [UI팀 인계](UI_TEAM_HANDOFF.md), 등화장치는 [기술 검토](LIGHTING_FEASIBILITY.md)를 따릅니다.
