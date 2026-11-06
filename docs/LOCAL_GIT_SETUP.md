# 로컬 Git / GCM 최초 연결

이 문서는 실행할 절차와 실제 결과를 구분합니다. 현재 Git 2.56.0.windows.4, Git Credential Manager 2.9.1은 설치되어 있습니다. Git의 system 설정에 `credential.helper=manager`가 등록되어 있어 재설치나 전역 configure는 필요하지 않습니다. 이 세션의 PATH에는 Git이 없으므로 절대 경로를 사용합니다.

## 현재 실제 확인 결과

- Git 버전 실행 성공.
- 별도 `.sync/git-smoke-20261107-001502` 폴더에서 init/add/commit/status/log 성공. 테스트 커밋 `fb82ef98b560f4f023436dac20297a7314356362`. 가상 테스트 작성자를 사용한 로컬 전용 기록이며 원격에 올리지 않았습니다. 실제 프로젝트의 Git 연결 성공을 뜻하지 않습니다.
- 실제 프로젝트 루트에는 `.git`이 없습니다.
- GCM `github list`는 Windows `wincredman` 저장소 접근 오류로 실패했습니다. 기존 계정 인증이 없는지, 이 세션에서 읽지 못하는지는 확정하지 못했습니다.
- 비대화형 `git ls-remote`는 `github.com:443` 연결 실패, 종료 코드 128입니다. 이 오류를 인증 실패로 단정하지 않습니다.
- 이 Codex 실행 환경의 프로젝트 루트 `.git` 경로는 읽기 권한으로 제한되어 있습니다. 이 제한을 우회해 메타데이터를 쓰거나 외부 프로세스로 대신 실행하지 않습니다. 별도 사용자 터미널에서 정상적인 최초 연결이 필요할 수 있습니다.

## 최초 인증 — 사용자 승인 후에만

Git 설치는 이미 확인됐으므로 설치 승인은 불필요합니다. 최초 GCM 로그인 승인은 별도로 요청한 상태입니다. 현재까지 로그인 명령이나 브라우저 인증을 시작하지 않았습니다.

아래 명령은 현재 승인 전에 실행하지 않았습니다. 일반 사용자 PowerShell에서 사용할 수 있는 명령이며 실행 정책 변경은 필요하지 않습니다.

```powershell
$gitExe = 'C:\Program Files\Git\cmd\git.exe'
& $gitExe credential-manager github login --url https://github.com --username kyc66533-art --browser
& $gitExe credential-manager github list
& $gitExe ls-remote --heads https://github.com/kyc66533-art/vehicle-app-workspace.git main
```

로그인은 GitHub의 정식 브라우저 OAuth 화면에서 진행합니다. 토큰·비밀번호·복구 코드는 채팅/프로젝트 파일에 입력하지 않습니다. `credential.helper=store`나 평문 토큰 캐시는 사용하지 않습니다. 이 명령의 인수는 설치된 GCM `github login --help`에서 확인했습니다.

## 파일·이력 보존 연결 절차

1. 인증 및 네트워크가 확인되면 현재 로컬 파일의 경로/해시와 원격 main SHA를 먼저 기록합니다. 루트 `.git` 또는 기존 로컬 이력이 있으면 새로 덮어쓰지 않습니다.
2. 아직 없는 별도 `.sync/remote-bootstrap-<시각>` 경로에 원격을 clone합니다. 같은 이름이 있으면 재사용·덮어쓰기·삭제하지 않습니다. 이 임시 경로는 Git 공유 제외 대상입니다.
3. clone이 전체 원격 이력을 보존하는지 확인합니다. Codex와 Aside 커밋이 모두 조상으로 남아 있어야 합니다. `docs/research/**`는 원격 기준으로 보존하고 내용을 임의로 바꾸지 않습니다.
4. 공유 대상 파일을 로컬 원본과 대조합니다. 동일 파일은 그대로 두고, 로컬만 있는 파일은 보존하고, 양쪽이 다르면 이번 작업/사용자 변경/원격 변경을 구분합니다. 충돌은 자동 덮어쓰지 않습니다. 원격 파일이 로컬에 없다고 삭제로 커밋하지 않습니다.
5. 파일 비교 후 적절한 권한이 있는 사용자 세션에서 기존 파일을 변경하지 않는 방식으로 원격 이력을 로컬 작업 폴더의 Git 메타데이터에 연결합니다. 이 단계는 **아직 수행하지 않았습니다**. 원격 내용을 현재 폴더 위로 강제 checkout하거나 기존 로컬 파일을 삭제하는 방식은 사용하지 않습니다.
6. 실제 루트의 `git status`, origin URL, main HEAD와 원격 이력을 확인합니다. 테스트 문서와 보고서만 명시적으로 커밋하고 일반 push한 다음 `ls-remote`/GitHub에서 내용과 커밋을 확인합니다. 이 검증이 끝나기 전에는 로컬 연결·인증·push 완료라고 보고하지 않습니다.

설정 완료 후에는 루트 `AGENTS.md`의 상시 승인에 따라 Codex가 검증된 작업을 자동 커밋·게시합니다. 최초 로그인 승인과 달리 이후 정상 게시에 반복 승인은 필요하지 않습니다. 현재 접근 장애가 유지되면 인증된 GitHub 연결 도구를 통한 문서 게시와 로컬 CLI 미완료 상태를 각각 보고합니다.
