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
