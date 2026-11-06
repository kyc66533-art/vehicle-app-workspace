# Vehicle App Workspace

Pleos Connect / Android Automotive OS용 공통 개발 기반입니다. 현재 공모전 아이디어를 재검토 중이며 기존 NextDrive/카잇다의 서비스 기능은 추가 개발하지 않습니다. 로컬 폴더와 패키지 식별자는 유지합니다.

- Android 프로젝트: [`NextDrive-Pleos`](NextDrive-Pleos/README.md)
- 최신 개발 기록: [`docs/CODEX_PROGRESS.md`](docs/CODEX_PROGRESS.md)
- 환경 검증: [`PLATFORM_VALIDATION.md`](PLATFORM_VALIDATION.md)
- SDK 실제 지원 점검: [`SDK_CAPABILITY_MATRIX.md`](SDK_CAPABILITY_MATRIX.md)
- 공유·동기화 절차: [`docs/GITHUB_SYNC.md`](docs/GITHUB_SYNC.md)

사용자는 Android Studio 정상 시작과 Pleos Connect v2.1.0 / API 34 / RAM 4 GB / Cold Boot AVD 부팅을 확인했습니다. 전체 Gradle 빌드, APK 실행, Vehicle SDK 실제 수신은 아직 검증하지 않았습니다.

현재 코드에는 차량 상태 한 화면과 미연결 데이터 소스 경계만 있습니다. 임의의 수치를 실제 데이터처럼 표시하지 않습니다. 기존 Google AI Studio 앱은 수정하거나 재사용하지 않습니다.
