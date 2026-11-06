# 작업 완료 자동 게시 — 문서 테스트

목적: 별도의 '업로드해' 요청 없이 이번 작업 완료 절차에서 이 검증 문서를 커밋·게시하고 원격에서 확인합니다.

## 검사 범위와 실제 결과

- Git for Windows 2.56.0.windows.4 실행 확인.
- 별도 Git 제외 폴더에서 로컬 init/add/commit/status/log 성공. 실제 프로젝트 루트 Git 연결과는 별개입니다.
- GCM 2.9.1 및 `credential.helper=manager` 확인. Windows 자격 증명 저장소 접근은 실패했습니다.
- 직접 GitHub HTTPS 조회는 연결 실패(종료 코드 128)입니다. CLI 인증/clone/push 성공으로 표시하지 않습니다.
- PowerShell 정책은 Restricted이며 시스템 정책·보안 기능은 변경하지 않았습니다. 자동 게시의 기본 경로는 직접 Git 명령입니다.
- 루트 `AGENTS.md`에 작업 완료·검증 성공 시 명시적인 파일만 자동 커밋·게시하도록 규칙을 추가했습니다. 새 Codex 프로세스를 실행한 재시작 검증은 하지 않았습니다.
- 이번 변경은 문서만 포함합니다. Android 소스/설정 변경, Gradle 빌드, APK 실행, Vehicle SDK 수신 검증은 이번 테스트에 포함하지 않습니다.

## 게시 성공의 판정

연결된 GitHub 도구로 현재 main의 기존 이력을 잇는 커밋을 만들고, 이 문서·지침·보고서의 원격 blob 해시를 로컬 공유본과 대조해야 성공으로 판단합니다. 이 검증은 API 게시 테스트이며 로컬 Git push의 대체 성공 판정이 아닙니다. 최종 결과와 커밋은 `CODEX_PROGRESS.md` 및 작업 완료 응답을 참조합니다.

`docs/research/ASIDE_RESEARCH.md`, `docs/research/ASIDE_SYNC_TEST.md` 등 Aside 문서는 이번 변경에 포함하지 않습니다. 기존 원격 파일과 Codex/Aside 커밋을 보존합니다.
