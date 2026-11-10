# AI 최종 개선·운영 준비

2026-11-10 21:00 +09:00. 시작 main `f30d8a7c35088a4a491d9bd18f16bee5e72903cc`. 실제 배포/Secret 변경/실제 Gemini 호출은 이번 모두0회입니다. 이 문서는 이전 조사 반복이 아니라 최신 구현·검증 결과입니다.

## 구현

- 전송 요약은 같은 버튼으로 펼치고 접습니다. 원본 JSON 대신 선택 증상 종류·보낼 본문·보내지 않는 항목을 한국어로 보여줍니다. 최초 동의·철회·전송 사본 편집은 유지하며 원문을 변경하지 않습니다. SOC 설명도 기존 계약을 한국어로 표시할 뿐 분석을 다시 만들지 않습니다.
- 사용량 조회 결과는 기존 암호화 sidecar에 보관합니다. 알려진 잔여0/모델OFF/불확실 요청에서는 신규 생성을 비활성화하고 저장 설명은 유지합니다. pending은 새 intent 없이 기존 결과를 확인합니다. 전송 직전 preflight의 최종 확인도 유지합니다.
- 앱 진입 때60초 이상 지난 이용량만 인증된 status로 한 번 갱신합니다. 일반 redraw는 반복하지 않고 fixture UI는 이 네트워크 경로를 실행하지 않습니다. 기록 본문·Gemini는 이 조회에 사용하지 않습니다. 오래 열린 화면에서 한도 확인이 필요하면 명시적인 횟수 확인을 이용합니다. 일일 자동 갱신은 서버 정책이며 기존 누적 정책을 내일 갱신된다고 안내하지 않습니다.
- 저장 성공 설명 보기는 로컬 AES-GCM만 읽으며 새 요청·등록·횟수 조회를 하지 않습니다. 명시 새 생성·성공/실패 분리·버전별 보존은 이전 구현을 유지합니다. 성공/실패 뒤에는 현재 이용량을 조회해 표시하며 조회 실패가 성공 설명을 지우지 않습니다.
- 운영 변수 `RECURRING_POLICY`를 별도로 추가할 수 있습니다. 기존 `PORTABLE_POLICY` Secret은 그대로 서버에서 읽어 UID enabled/after/devices를 유지하고, 기간별 상한/만료없음/분1회만 승인된 overlay로 적용합니다. 관리자에게 UID·Secret 원문 재입력을 요구하지 않습니다. 값이 없으면 기존 정책 그대로, 잘못된 값이면 fail closed입니다.
- 기기 월간 카운터를 추가했습니다. 기존 recurring 월에 해당 필드가 없으면 기존 device 누적을 보수적으로 이월합니다. 계정/서비스/비용 카운터와 원본 `budget`/`portable:v2`를 삭제하거나 초기화하지 않습니다. 기존 UNKNOWN 차단·원자 예약·동일 operation 중복 방지는 유지합니다.
- 고정 모델 요청 JSON은8192바이트 이하, 출력1024토큰 이하(SOC512), 도구·검색 없음으로 제한합니다. 한도를 넘으면 공급자 호출 전에 거부합니다. API 키는 서버 Secret만 사용합니다.

## 준비한 초기 운영 정책

| 제한 | 값 |
|---|---|
| 계정·기기 일일 | 각각5회, KST |
| 계정·기기 월간 | 각각100회 |
| 서비스 전체 월간 | 300회 |
| 서비스 월간 예약예산 | USD3 |
| 요청당 예약 | USD0.01 |
| 모델 요청 속도 | 분당1회 |
| 인가 | 기존 UID/기기 목록 유지, Secret 매일 갱신 불필요 |

첫 월은 과거 사용을 이월하므로 잔여는 위 상한보다 작을 수 있습니다. 일일 갱신으로 월간 예산을 우회하지 않습니다. 비용은 현재 공식 Gemini Developer API 요금의 더 높은 조회값0.30/2.50 USD/백만 입력/출력 토큰으로 계획했습니다. 요청 입력16384토큰·출력1024의 보수적 계산은 USD0.0074752이므로1센트를 예약합니다. 실제 계정 청구액·세금·Cloudflare 비용의 보증은 아니며 실청구와 예약시점 차이는 관리 대상입니다. [요금·배포·복구 세부 절차](../infra/gemini-relay/OPERATIONS_SETUP.md)를 확인합니다.

## 실행한 검사

| 구분 | 최신 결과 |
|---|---|
| Debug/validation/계측 APK·Lint | 최종 BUILD SUCCESSFUL 2m2s, 오류0/경고50 |
| 선택 기록 AI/전송 요약/0회 차단 | 56 PASS (이전41+새15) |
| validation 프로세스 종료·재시작 | 4 PASS: 암호화 성공 설명·0회 상태·독립 기록/이력 복원, 네트워크 금지 |
| Portable 인증/요청·AI UI 회귀 | 26+4 PASS |
| 서버 서명·원장·issue | 64+42 PASS |
| recurring gate/기존 Secret 유지 overlay | 4+5 PASS |
| 월간·비용·정책 준비/기본 기간 검사 | 18+12 PASS |
| 공급자 오류 | 10 PASS |
| 새 번들 workerd/native provider | 16+5 PASS, 외부 모델0 |
| 최종 원본 번들·Wrangler dry-run | 사용자 일반 PowerShell 성공 |

Android90/서버176입니다. 이미 검증했고 이번 변경 영향이 없는 전체 UI·SDK·분석 검사는 무조건 반복하지 않았습니다. fixture의 MODEL 형식 응답은 실제 모델 응답이라고 보고하지 않습니다. 실제 모델 성공은 이전 사용자 검수 사실이며 이번 유료 호출은0회입니다. 현재 운영 서버의 일일 갱신 적용은 아직 미검증입니다.

## 보존·설치

실제 user10 일반/validation 파일을 변경 전후 비공개 백업했습니다. 기존186 DB 스키마·버전·원본 행·integrity/FK가 유지됐습니다. 일반 원본1075행/validation 원본8928행 그대로이며 각각 기존 DB의 추가행0입니다. 새 fixture DB2개는 validation에만 존재합니다. 일반 앱 현재 문제24/이벤트95/운전자10/초안0이며 과거23을 고정하지 않았습니다. Firebase·AI 캐시 관련 파일4개는 바이트 동일했습니다.

일반 앱은 `install -r --user 10` 교체·MainActivity 실행 성공, APK SHA256 `D81D9BD5F24218E9E02FAE2548BCF655267F730CC157FDDA20D3ED7FAA5CE744`입니다. 일반 앱 UI 자동화를 실행하지 않았습니다. **사용자 실제 성공 설명의 cold restart 재조회는 직접 검수 대기**, validation 고유 fixture의 프로세스 복원은 위4검사 통과입니다. 원문·로그인·AI 결과·관측·AVD·개인 백업을 초기화하지 않았습니다.

최종 Worker SHA256 `8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123`. 서버 관리 도구가 없어 승인 후 일반 PowerShell의 준비된 모델OFF/ON 설정을 사용합니다. 설정 파일에는 키·UID·Secret이 없습니다. 코드 게시가 실제 배포는 아닙니다.

## 수정 파일·협업

Android: PortableAiActivity.kt, ai/AiRequestUi.kt(신규), ai/PortableAiClient.kt, IssueAiChecks.kt, MvpScenarioInstrumentation.kt(고유 cold 검사 early branch만). MainActivity/Repository/Service/SDK/관측·문제 DB 스키마와 디자인 시스템은 변경하지 않았습니다.

Worker: portable-gate.ts, recurring-quota.ts, portable-issue.ts, portable-analysis.ts, provider-budget.ts(신규). test/issue-security.mjs, operations-readiness.mjs(신규), prepare-recurring-policy.mjs(신규). 나머지는 본 문서·운영 안내·AGENTS/진행 보고·UI팀 인계입니다.

구버전 opaque 결과의 자동 귀속/모든 성공 회차 선택 UI, PC B, Release, TalkBack/rotary/고령 사용자, 장시간 운영/실청구 정산은 이번 미검증·미완료입니다. Claude Code는 별도 브랜치에서 위 AI 파일의 새 캐시/사용량/접기 계약을 보존해 병합합니다. 연구 파일과 다른 작업자는 덮어쓰지 않습니다.

## 사용자에게 남은 작업

1. 일반 앱에서 기존 설명 보기 → 앱 종료·재실행 → 기존 설명 보기를 확인합니다. 이 단계에서 새 생성은 누르지 않습니다.
2. 준비된 운영 묶음(모델OFF 교체·비밀 아닌 반복 정책·월300/USD3·인증 확인·모델ON·선택 기록 최대1회)을 승인합니다.
3. 승인 후 관리자가 [운영 설정 A/B](../infra/gemini-relay/OPERATIONS_SETUP.md)의 두 블록을 단계 순서대로 실행하고 앱의 원장/UNKNOWN을 확인합니다. Firebase/키/Secret 재등록은 없습니다.

GitHub 연결 도구로 검증된 파일만 현재 main의 이력을 이어 게시하며 최종 커밋은 완료 보고로 제공합니다. 문서 자기 SHA를 넣기 위해 반복 커밋하지 않습니다.
