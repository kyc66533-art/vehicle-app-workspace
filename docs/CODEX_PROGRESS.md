# Codex 개발 진행 기록

마지막 업데이트: 2026-11-06 23:44 +09:00 (Asia/Seoul).

## 현재 범위

공모전 아이디어는 재검토 중입니다. 기존 NextDrive/카잇다 서비스 기능은 보류하며 이번 작업은 GitHub 공유·개발 기록·환경 확인으로 제한합니다. 기존 Google AI Studio 앱은 수정하지 않았고 현재 프로젝트 파일을 삭제하거나 초기화하지 않았습니다.

## 완료한 개발 작업

- GitHub 저장소 `kyc66533-art/vehicle-app-workspace`의 존재·비공개 상태와 연결 계정의 쓰기 권한 확인. 최초 조회에서 브랜치 없음/빈 저장소 확인.
- 로컬 루트·상위 경로·하위 앱 폴더에 Git 초기화 흔적 없음 확인. Git/gh는 PATH·일반 설치 경로·확인한 Codex 배포 폴더에서 미발견. 설치나 로컬 인증은 임의로 수행하지 않음.
- 사용자 확인 환경을 반영: Android Studio 정상 실행, Pleos Connect v2.1.0/API 34 이미지 설치·AVD 부팅. AVD 설정 파일에서 RAM 4096 MB / Cold Boot / Automotive 태그 재확인.
- 루트 `.gitignore`, 공유 파일 검사 스크립트, 검증된 기존 커밋만 게시하는 스크립트와 운영 절차 작성.
- 작업 완료마다 이 보고서를 갱신하도록 `AGENTS.md`에 지침 추가. 30분 스케줄러는 설계만 하고 등록하지 않음.

## 수정하거나 생성한 파일

- 생성: `.gitignore`, `README.md`, `docs/CODEX_PROGRESS.md`, `docs/GITHUB_SYNC.md`, `docs/UPLOADED_FILES.md`, `scripts/Test-ShareSafety.ps1`, `scripts/Publish-ValidatedCommit.ps1`.
- 수정: `AGENTS.md`, `PLATFORM_VALIDATION.md`.
- 기존 Android 소스·리소스·Gradle 설정 및 다른 명세서는 이번 작업에서 변경하지 않음. 공유 대상에는 기존 소스가 포함됨.

## 테스트 결과

- GitHub 인증·저장소 조회 성공. 연결 계정의 push 권한 확인. 로컬 Git 인증 성공으로 해석하지 않음.
- 초기 소스 커밋 `9d01e7739d5f1faceaaeab43c826747783706bbf`의 `main` 반영 확인. 재귀 트리 조회 결과 파일 26개, 추가 파일 0개이며 모든 Git blob SHA가 로컬 공유본과 일치함. 이 검증 후 현재 보고서에 결과를 반영하는 별도 문서 커밋을 추가함.
- Pleos AVD 설정 파일과 설치 이미지의 API 34/Automotive 태그 확인. 부팅 성공은 사용자 직접 확인 결과이며 이번 Codex 세션에서 다시 실행하지 않음.
- 새 PowerShell 스크립트 2개의 Parser 구문 검사 통과.
- 명시적으로 선정한 텍스트 파일 26개의 독립 인라인 경로·크기·바이너리·자격증명 패턴 검사 통과. 실제 키/토큰은 발견되지 않았으며 패턴 검사의 한계가 있어 파일도 검토함.
- `.ps1` 안전 검사 스크립트 실행 시 PowerShell 정책에 의해 차단됨. 정책을 변경/우회하지 않았으며 게시 스크립트의 실제 Git 동작은 테스트하지 못함. 위 인라인 파일 검사를 스크립트 런타임 검증과 구분함.
- 이전 작업: 설치된 AAPT2/SDK 36·Kotlin 2.1.21을 사용한 리소스 및 Kotlin 소스 오프라인 검사 통과. 정식 Gradle 툴체인 빌드가 아님.

## 현재 오류 및 제약

- 로컬 Git/gh 실행 파일 미발견, 로컬 `.git` 미생성. 따라서 로컬 origin 설정·Git 자격증명·CLI push는 미완료.
- 현재 세션의 PowerShell 스크립트 실행 정책으로 `.ps1` 직접 실행이 차단됨. 자동 게시 운영 전 사용자 환경에서 실행 정책과 조직 정책을 검토해야 함. 설정을 임의 변경하지 않음.
- 이전 Codex 프로세스의 IDE 시작/AEHD 접근 거부·Gradle/Pleos Maven 연결 실패는 과거 환경의 관측값. 사용자 세션에서 현재 IDE·Pleos 부팅은 정상 확인됨.
- 컴파일용 Platform 34와 Build Tools 35.0.0, 정식 Wrapper/Gradle, Vehicle SDK 설치·해석은 아직 미확인/미완료. 시스템 이미지 설치와 구분.

## 아직 구현·검증하지 않은 항목

- 전체 Gradle 빌드, Lint, Gradle 단위 테스트, APK 설치·실행, Vehicle SDK 실제 데이터·권한·Capability 수신.
- 로컬 Git 연결/인증, 게시 스크립트의 실제 Git 통합 실행, Windows 예약 작업 등록 및 실행.
- 기존 서비스 기능 전체는 개발 보류 상태이며 이번 공유를 통해 추가 구현하지 않음.

## 다음 개발 작업

1. Git 설치 위치 확인 또는 사용자 승인 후 설치, 사용자 승인에 따른 로컬 로그인과 원격 이력을 보존하는 로컬 연결.
2. 컴파일 SDK/빌드 도구·Wrapper·Gradle 준비 후 정식 빌드·테스트와 Pleos APK 실행 확인.
3. 공식 Vehicle SDK 확보 후 최소 읽기 전용 신호의 권한·Capability·수신을 한 화면에서 검증.
4. 실제 검증을 통과한 커밋/보고서를 공유. 게시 스크립트의 정상·dirty tree·인증 실패·원격 선행·분기 이력 사례를 테스트.
5. 위 조건 충족 후 필요하면 30분 예약 동기화를 사용자 별도 승인으로 등록.

## GitHub 업로드 상태

- **GitHub 연결 도구를 통한 초기 업로드 및 원격 검증 성공.** 저장소: [vehicle-app-workspace](https://github.com/kyc66533-art/vehicle-app-workspace), 브랜치 `main`, 비공개 유지.
- 첫 커밋: `9b381f49b3e1b6a1393c8b817e5eaf9fd342578b` — 저장소 README 초기화.
- 검증한 소스·문서 커밋: [9d01e773](https://github.com/kyc66533-art/vehicle-app-workspace/commit/9d01e7739d5f1faceaaeab43c826747783706bbf). 첫 커밋을 부모로 보존하는 일반 이력으로 반영했으며 강제 갱신하지 않음.
- 업로드 파일 26개는 `docs/UPLOADED_FILES.md`에 열거됨. 현재 보고서 갱신 커밋은 위 검증 결과를 추가하는 문서 변경이며 소스 변경이 아님.
- 로컬 Git 초기화·origin 연결·로컬 인증은 **미완료**. 연결된 GitHub 계정 인증만 사용했으며 인증 파일/토큰은 업로드하지 않음.
- **자동 동기화는 비활성/미등록.** 완료된 기존 커밋만 전송하는 스크립트와 절차만 준비했으며 구문 검사 외 실제 Git 통합 실행은 미검증. `.sync/validated-commit.json`도 생성하지 않음.
- 초기 공유는 사용자가 명시적으로 요청한 기존 소스 보존용이며 정식 빌드 미검증 상태를 포함함. 향후 자동 게시에는 동일 예외를 적용하지 않음.
