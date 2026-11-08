# CARITDA — 고정 홈·기록 검색 실행 검증 완료

2026-11-08: 스크롤과 분리한 앱 내부 홈, 전체/내 작성 바로가기, 네 필터와 카테고리·전체 본문·메모 검색/조건 초기화를 구현했습니다. 상세 이전의 검색·필터·목록 위치, 수정 취소/변경 없음/버전 충돌·초안 보존 실패 시 이동 차단을 확인했습니다. 네 수동 상태 변경과 기존 수정·논리 삭제는 유지합니다.

Pleos Connect v2.1.0 validation 앱에서 신규NAV29개+기존145개=174개 자동 검사를 통과했습니다. 일반 APK도 install-r로 교체하고 홈·스크롤·차량 정보·사용법을 확인했으며 운영 문제/관측 DB는 백업과 바이트 단위로 같습니다. 실제 SDK100%/0km 수신은 validation의 별도 관측이며 일반 앱의 과거 값을 회복 근거로 합치지 않습니다.

[검증 결과·NAV 기준별 부분/미검증과 사용 순서](docs/NAV_ACCEPTANCE.md), [자동화 후속 설계](docs/AUTOMATION_FOUNDATION.md), [진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. 자동 회복·Gleo·휴지통 복원은 미구현입니다. 실차/실제 주행 전환/종합 접근성·일반 사용자 시험·Lint는 남아 있습니다. 아래는 과거 기록입니다.

# CARITDA — 기록 저장·수정·삭제 신뢰성 개선

2026-11-08: 전체/관리 중/완료/내 작성 필터, 지속 저장 성공·실패 안내, 저장 후 상세 이동과 뒤로 가기 복구, 작성자 ID 기반 수정·논리 삭제를 구현했습니다. 발견 시각과 수정 전후 이력은 보존합니다. 기존 운전자·문제22건·이력·초안은 v3 이전 후에도 유지됐고 관측 DB 해시도 같습니다.

자동화는 별도 CARITDA 검증 전용 앱에서만 실행합니다. 기존 테스트를 포함한145개 검사와 운영 DB 보존·잘못된 운영 대상 테스트 거부를 확인했습니다. 과거 테스트 입증5건과 출처 미확정17건은 삭제하지 않았습니다. Virtual Console은 현재HTTP200입니다. 사람/실차/종합 접근성·Lint는 남아 있습니다.

[사용 순서·원인·새 인수기준·삭제 보존 정책](docs/RECORD_RELIABILITY.md), [진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. 이미 일반 앱을 데이터 보존 방식으로 교체 설치했습니다. 아래는 과거 기록이며 최신 결과를 우선합니다.

# CARITDA — Simple UI·첫 안내·사용법

2026-11-08: 홈의 다음 행동을 단순화하고 차량 정보의 충전량·주행거리·실제 수신 시각을 크게 표시했습니다. SDK/Capability/오류·세션은 불러오기 상세에 유지합니다. 신규 한 화면 안내와 여섯 항목 사용법/재보기, 초안 복귀를 구현했습니다. 기존 DB·운전자·수동 기록·SDK2.3.2·안전 제한은 보존했습니다.

앱/테스트 APK 빌드와 기존57개+신규32개 검사, 다섯 주요 화면200개 조합을 Pleos 에뮬레이터에서 확인했습니다. 현재 수신은 SOC15%/주행거리 원본1000000.0km, 연료 미지원이며 실차값/자동 진단이 아닙니다. ON기준은 통과13/부분8/미검증3이며 실제 사용자 이해도·실차·종합 접근성·Lint는 남아 있습니다.

[ON별 결과와 사용 순서](docs/ONBOARDING_ACCEPTANCE.md), [진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. 아래는 과거 버전의 기록이며 현재 상태는 이 절과 최신 진행 기록이 우선합니다.

# CARITDA — 운전자·UI M0 업데이트

2026-11-07: 이름 등록·카드 선택·운전 시작·이름 수정/보관, 홈/문제/차량 분리와 초안 복원을 구현했습니다. 기존 A/B DB는 영구 ID와 당시 이름을 추가하는 비파괴 v2로 이전했고 원래 문제 5건·이벤트 16건과 관측을 보존했습니다. 실제 Pleos 에뮬레이터에서 이전 18개 + 기존 24개 + 관측 6개 + 새 UI 27개 검사를 통과했습니다. SOC 100%/주행거리 0.0km 수신과 연료 미지원 처리를 유지합니다.

[사용 방법과 AC별 검증 범위](docs/UI_ACCEPTANCE.md), [이전·복구 계획](docs/UI_MIGRATION_PLAN.md), [최신 진행 기록](docs/CODEX_PROGRESS.md)을 참고하세요. Lint/실차/종합 접근성 검증은 미완료입니다. 아래는 이전 버전의 기록이며 현재 상태는 이 절과 최신 기록이 우선합니다.

## 최신 상태 — Pleos Vehicle SDK 실제 수신 확인

2026-11-07: 기존 v0.1을 보존한 채 SDK 2.3.2로 Pleos 에뮬레이터의 SOC 100%, 누적 주행거리 0.0km를 실제 수신·SQLite 저장했습니다. 연료 잔량은 미지원 응답으로 기록합니다. 실차값과 구분하며 앱 프로세스 재시작 후 복원, 기존 24개 회귀와 관측 6개 검사를 통과했습니다. [상세 결과](docs/VEHICLE_SDK_232_RUNTIME.md).

# CARITDA v0.1 — Vehicle App Workspace

Pleos Connect / Android Automotive OS에서 사용하는 차량 문제 기록·인계 MVP입니다. CARITDA(카잇다)는 상표 검증 전의 잠정 명칭이며 폴더·패키지 식별자는 NextDrive로 유지합니다.

현재 기능은 운전자 A/B 선택, 수기 증상 기록, SQLite 영구 저장, 다음 운전자 확인, 점검·상태·재발 이력, 정비 상담용 요약입니다. 모든 기록은 동일 차량 앱 내부에 저장하며 실제 차량 센서값·AI 진단·휴대폰 전송·정비소 접수는 제공하지 않습니다.

- Android 프로젝트와 실행: [NextDrive-Pleos](NextDrive-Pleos/README.md)
- MVP 사용·검증 안내: [docs/CARITDA_V01_MVP.md](docs/CARITDA_V01_MVP.md)
- 개발 기록: [docs/CODEX_PROGRESS.md](docs/CODEX_PROGRESS.md)
- 과거 환경 검증: [PLATFORM_VALIDATION.md](PLATFORM_VALIDATION.md)
- Vehicle SDK 미검증 항목: [SDK_CAPABILITY_MATRIX.md](SDK_CAPABILITY_MATRIX.md), [읽기 검증 계획](docs/VEHICLE_SDK_READ_PLAN.md)
- 게시 절차: [docs/GITHUB_SYNC.md](docs/GITHUB_SYNC.md)

2026-11-07: Debug 앱·테스트 APK 빌드 및 Pleos Connect v2.1.0/API 34 에뮬레이터 실행, 24개 계측 검사를 통과했습니다. Lint는 의존성 다운로드 제한으로 미완료입니다. 실차·배포용 Release APK·Vehicle SDK 데이터 수신은 미검증입니다. 기존 Google AI Studio 앱과 Aside 연구 파일은 수정하지 않았습니다.
