# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 00:19:00 +09:00 (Asia/Seoul).

## 최신 작업 — 개발 완료 직후 자동 게시 규칙

### 완료한 작업

- Git for Windows 2.56.0.windows.4를 `C:\Program Files\Git\cmd\git.exe`에서 확인. 재설치하지 않음. GCM 2.9.1과 기본 credential helper `manager` 확인.
- 별도 Git 제외 폴더 `.sync/git-smoke-20261107-001502`에서 init/add/commit/status/log 실행 성공. 테스트 커밋 `fb82ef98b560f4f023436dac20297a7314356362`, 작업 트리 변경 없음 확인. 로컬 전용 가상 테스트 작성자이며 GitHub에 올리지 않음.
- `AGENTS.md`의 무조건적인 자동 커밋 금지를 '작업 완료·검증 성공 후 이번 작업의 명시적인 변경 파일만 자동 커밋·게시'로 교체. 이후 정상 게시에는 반복 확인을 요청하지 않는 상시 승인을 기록.
- 문서와 앱 변경의 검증 게이트, 기존 사용자 변경/Aside 연구 파일 보호, 원격 충돌 중단, API 게시와 로컬 push의 구분, 직접 Git 명령을 사용하는 정책 미변경 경로를 문서화.
- 최초 로컬 연결 절차와 테스트 문서를 준비. 이번 작업은 서비스 기능이나 앱 소스/설정을 바꾸지 않음.

### 수정 파일

- 수정: `AGENTS.md`, `docs/GITHUB_SYNC.md`, `docs/CODEX_PROGRESS.md`.
- 생성: `docs/LOCAL_GIT_SETUP.md`, `docs/AUTO_PUBLISH_TEST.md`.
- `docs/research/**`와 기존 앱 파일은 수정/삭제/스테이징하지 않음. 원격에만 있는 Aside 파일도 보존 대상으로 확인.

### 실제 검사 결과와 오류

- Git 버전 및 격리된 로컬 커밋 검사 성공. 실제 프로젝트 루트의 `.git`은 아직 없음.
- GCM `github list`: `Unable to persist credentials with the 'wincredman' credential store` 오류. 이 세션에서 저장소 접근이 안 되어 로컬 인증 유무를 확정하지 못함.
- 비대화형 Git `ls-remote`: github.com:443 연결 실패, 종료 코드 128. 로컬 인증·clone·push 미검증.
- PowerShell 유효 정책은 `Restricted`, 각 scope는 Undefined. 시스템 정책 변경, Bypass 옵션, 보안 해제, 자격증명 추출은 하지 않음. 직접 Git 실행과 문서 작업은 가능함을 확인.
- 프로젝트 루트 `.git` 경로가 현재 Codex 파일 권한에서 읽기로 제한됨. 이 제한을 우회하여 초기화하거나 외부 프로세스로 대신 실행하지 않음.
- 원격 main의 작업 시작 기준은 `6f1467e1828cec601dbd6c6a5abf92ff6f5263a5`. Codex/Aside 파일 목록을 읽기 전용으로 확인. 새 문서만 이 원격 이력을 잇는 커밋으로 게시하고 해시를 대조하는 범위임.
- `AGENTS.md`의 현재 내용은 직접 읽고 이번 작업에 적용. 새 세션을 실행하는 재시작 검증은 `codex` 재실행 금지 조건 때문에 하지 않음.
- 공유할 Markdown 파일 5개의 독립 경로/자격증명 패턴 검사 통과. `AGENTS.md`는 11,164 bytes로 기본 32 KiB 제한 이내이며 무조건적인 자동 커밋 금지와의 충돌을 수정했음을 확인.
- 전체 Gradle 빌드·APK 실행·SDK 수신은 이번 문서 변경의 테스트 대상이 아니며 여전히 미검증. 기존 앱이 검증 완료됐다고 주장하지 않음.

### 게시 경로 및 남은 작업

이번 문서의 게시 경로는 이미 인증된 GitHub 연결 도구입니다. 로컬 Git commit/push 성공으로 기록하지 않습니다. 보고서는 검증 문서·지침과 같은 커밋에 포함하고 게시 후 원격 내용을 확인하며, 자기 자신의 SHA를 넣기 위한 반복 커밋 대신 최종 작업 응답에 검증 SHA를 제공합니다.

최초 GCM 로그인 승인은 사용자에게 별도로 요청한 상태입니다. 승인 전 브라우저 로그인/인증 명령을 시작하지 않았습니다. 이 승인은 현재 세션의 네트워크·자격 증명·루트 `.git` 권한 제한을 자동 해제하지 않습니다. 필요 시 일반 사용자 PowerShell에서 승인된 최초 연결을 수행하고 정상 CLI push를 별도 검증해야 합니다.

**완료:** 영구 지침·작업 절차·로컬 Git 실행 검사·문서 게시 테스트 준비. **미완료:** 프로젝트 루트 Git 연결, GCM 실제 인증, 원격 clone/비교 및 CLI push 검증. 스케줄러는 등록하지 않았습니다.

## 이전 초기 공유 작업 기록 (아래는 2026-11-06 기준)

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
- 최종 점검 중 다른 작업의 커밋 `2280ea3c6f764b7851b7d32852c77e541cc901aa`(`docs: add Aside GitHub connection test`)이 보고서 업로드 이후 추가된 것을 확인함. 추가 파일 `docs/research/ASIDE_SYNC_TEST.md`를 보존했으며 강제로 덮어쓰거나 로컬 파일로 가장하지 않음. 초기 공유 목록 26개와 별도로 원격에 추가된 문서임. 로컬 Git 연결 시 이 원격 이력도 반드시 보존해야 함.
- 로컬 Git 초기화·origin 연결·로컬 인증은 **미완료**. 연결된 GitHub 계정 인증만 사용했으며 인증 파일/토큰은 업로드하지 않음.
- **자동 동기화는 비활성/미등록.** 완료된 기존 커밋만 전송하는 스크립트와 절차만 준비했으며 구문 검사 외 실제 Git 통합 실행은 미검증. `.sync/validated-commit.json`도 생성하지 않음.
- 초기 공유는 사용자가 명시적으로 요청한 기존 소스 보존용이며 정식 빌드 미검증 상태를 포함함. 향후 자동 게시에는 동일 예외를 적용하지 않음.
