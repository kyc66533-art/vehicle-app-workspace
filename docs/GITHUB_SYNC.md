# GitHub 공유 및 완료 작업 동기화

대상: `https://github.com/kyc66533-art/vehicle-app-workspace` (비공개). 폴더 `C:\Users\kyc66\NextDrive`와 기존 파일·기록을 보존합니다.

## 초기 공유와 로컬 Git의 차이

연결된 GitHub 계정/저장소 권한 확인에 성공했습니다. 로컬 Git/gh 실행 파일과 `.git`은 발견되지 않았습니다. 초기 공유는 사용자가 요청한 기존 소스와 문서의 기준 스냅샷이며 GitHub 연결 도구를 사용합니다. 전체 Gradle 빌드·APK 실행·SDK 수신은 미검증임을 보고서에 명시합니다. 이를 검증 완료 앱 배포로 표현하지 않습니다.

GitHub 연결 도구의 인증은 로컬 Windows의 Git 인증을 설정하지 않습니다. 로컬 명령으로 동기화하려면 Git 설치 위치 확인 또는 승인 후 Git for Windows 설치 및 사용자 로그인, 원격 기록을 보존하는 로컬 연결이 필요합니다. 현재 폴더를 삭제하거나 기존 파일 위에 `clone`/`checkout`/`reset --hard`를 실행하지 않습니다. 실제 연결 전 로컬 변경과 원격 내용을 대조합니다.

## 개발 완료 시 공유 절차

1. 변경 내용을 검토하고 필요한 검증을 실제로 실행합니다. 앱 소스/빌드 설정/리소스 변경은 정식 Gradle `:app:assembleDebug`, `:app:lintDebug`, `:app:testDebugUnitTest`를 모두 통과해야 합니다. 문서·스크립트 변경은 해당 변경의 검사와 스크립트 구문 검사 등을 기록합니다.
2. `docs/CODEX_PROGRESS.md`에 완료 작업·수정 파일·검증 결과·오류·미구현·다음 작업과 시간대를 갱신합니다.
3. 파일을 명시적으로 검토·스테이징하고 로컬 커밋합니다. 무조건적인 `git add .`나 자동 커밋은 사용하지 않습니다. 스크립트는 이 작업을 대신하지 않습니다.
4. 해당 HEAD에서 검증을 확인하고, Git 제외 경로 `.sync/validated-commit.json`에 아래 구조의 완료 기록을 작성합니다. `commit`은 실제 HEAD SHA, `reportSha256`은 `Get-FileHash docs/CODEX_PROGRESS.md -Algorithm SHA256`의 대문자 해시, `checks`는 이 HEAD의 실제 실행 결과입니다. 검사 이름만 채우거나 종료 코드를 꾸며 넣지 않습니다. HEAD가 바뀌면 검증을 다시 해야 합니다.

   ```json
   {
     "commit": "실제로 검증한 HEAD의 40자리 SHA",
     "status": "passed",
     "reportSha256": "진행 보고서의 실제 SHA256",
     "checks": [
       { "name": "assembleDebug", "exitCode": 0 },
       { "name": "lintDebug", "exitCode": 0 },
       { "name": "testDebugUnitTest", "exitCode": 0 }
     ]
   }
   ```

5. 프로젝트 루트에서 `powershell.exe -NoProfile -File .\scripts\Publish-ValidatedCommit.ps1`를 실행합니다. 실행 정책은 임의 변경하지 않습니다.
6. 업로드 후 출력된 커밋과 GitHub의 보고서·파일을 확인합니다. 현재 작업의 업로드 SHA는 다음 진행 보고서에서 참조할 수 있습니다. 보고서 안에 자기 자신의 커밋 SHA를 넣으려는 반복 커밋은 만들지 않습니다.

완료 기록은 검증한 작업을 표시하는 로컬 신뢰 기록이며 테스트 증명의 암호학적 서명이 아닙니다. 개발 담당자가 실제 검증 후에만 생성합니다. 현재는 정식 Gradle 검증이 없으므로 완료 기록을 만들지 않았고 게시 스크립트도 실행하지 않았습니다. 초기 업로드 예외를 향후 자동 동기화에 적용하지 않습니다.

현재 세션에서는 `.ps1` 직접 실행이 PowerShell 정책으로 차단됐습니다. 스크립트의 구문 검사와 별도 인라인 파일 검사는 통과했으나 스크립트 런타임은 미검증입니다. 운영 전 사용자 환경의 실행 정책을 확인해야 하며 정책 변경·우회는 자동으로 수행하지 않습니다.

## 보호 동작과 한계

- 등록된 HTTPS origin의 `main`만 사용하며 인증값이 들어간 URL·추가 push URL은 거부합니다.
- 작업 트리가 변경 중이거나 완료 기록/HEAD/보고서 해시가 일치하지 않으면 중단합니다.
- 추적된 텍스트 파일을 경로·크기·링크·자격증명 패턴으로 검사합니다. 검사로 모든 비밀 탐지가 보장되지는 않으므로 사람이 공유 파일도 검토합니다. 새 바이너리/파일 형식은 자동 게시하지 않습니다.
- 사용자 인증 팝업을 자동으로 띄우지 않습니다. 인증 실패 시 별도 사용자 승인·로그인 후 재시도합니다.
- fetch 후 원격 기록이 로컬 HEAD의 조상인지 확인합니다. 원격이 앞서 있거나 충돌 가능성이 있으면 중단합니다. 강제 push, 자동 pull/merge/rebase, 삭제/reset/stash는 하지 않습니다. 검사 중 원격이 바뀌어도 일반 push가 이력 덮어쓰기를 거부합니다.
- 수정 중인 코드·보고서를 자동 스테이징하거나 커밋하지 않습니다. 이미 검증된 커밋만 전송합니다.
- 실패 시 콘솔과 Git 제외 경로 `.sync/publish-errors.log`에 원인 범주를 기록합니다. 원격 오류 원문·토큰은 로그에 복사하지 않습니다. 로컬 로그는 자동 알림이 아니므로 무인 운영 전 오류 확인 방식도 합의해야 합니다.

## 30분 간격 Windows 작업 스케줄러 검토

**등록하지 않았습니다.** Git 설치·로컬 인증·정식 검증·게시 스크립트 실제 성공 확인과 사용자 별도 승인이 먼저 필요합니다.

승인 후 구성 후보:

- 현재 사용자 권한, 로그인한 동안 실행, 관리자 권한 사용 안 함.
- 30분 반복 트리거. 실행 파일은 Windows PowerShell, 인수는 `-NoProfile -NonInteractive -File "C:\Users\kyc66\NextDrive\scripts\Publish-ValidatedCommit.ps1"`.
- 작업 중복 실행은 `IgnoreNew`, 실행 제한 10분, Git 인증은 사전에 사용자 세션에서 완료.
- 변경 작업이 진행 중이면 중단하고 이후 주기에 재확인합니다. 완료 기록이 있는 기존 커밋만 push하므로 30분마다 미완성 코드를 수집하지 않습니다.
- 앱이 닫혀 있어도 Windows 작업은 실행할 수 있으나 Codex의 작업 완료 여부를 자동 판단하지 않습니다. 완료 기록과 실제 검사 결과가 필요합니다.
- 오류 시 작업 결과 코드/로컬 로그 확인이 기본입니다. Windows 알림 등을 추가하려면 별도로 합의하며 메일·외부 메시지는 자동 발송하지 않습니다.

공식 근거: [Git push와 fast-forward 보호](https://git-scm.com/docs/git-push), [Windows 반복 작업 트리거](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtasktrigger).
