# Pleos 공통 환경 — 접근 제한 해소 후 이어갈 단계

현재 Codex 세션에서 SDK 패키지 목록·Gradle 배포 정보·Vehicle SDK POM 다운로드가 실패했고, ADB 서버는 사용자 디렉터리 생성 권한 오류로 시작하지 못했습니다. 사용자 승인이 부족한 것으로 단정하지 않으며 관리자 권한/보안 해제/네트워크 우회를 사용하지 않습니다. 아래는 **일반 사용자 Windows 환경에서 이어갈 안내이며 이 세션에서 완료한 작업이 아닙니다**.

서비스 기능은 보류합니다. 기존 소스·AVD·Aside 연구 파일을 보존합니다. 아래 절차에는 로컬 Git 연결 명령이 없습니다.

## 1. 현재 도구 경로와 SDK 준비

- Android Studio: `C:\Program Files\Android\Android Studio\bin\studio64.exe`.
- 포함 JDK: `C:\Program Files\Android\Android Studio\jbr` (실행 확인: Java 21.0.8).
- 사용자 SDK: `C:\Users\kyc66\AppData\Local\Android\Sdk`.
- 프로젝트: `C:\Users\kyc66\NextDrive\NextDrive-Pleos`.

Android Studio SDK Manager에서 **Android SDK Platform 34**와 **Android SDK Build-Tools 35.0.0**을 추가합니다. 이미 설치된 API 36 / Build Tools 36.1.0 / Pleos API 34 시스템 이미지·AVD는 보존합니다. 시스템 이미지와 컴파일용 Platform은 서로 다른 패키지입니다.

일반 PowerShell에서 설치할 경우의 명령은 다음과 같습니다. `JAVA_HOME` 변경은 이 창의 프로세스에만 적용되며 실행 정책은 변경하지 않습니다.

```powershell
$project = 'C:\Users\kyc66\NextDrive'
$sdk = 'C:\Users\kyc66\AppData\Local\Android\Sdk'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = $sdk
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$sdk" 'platforms;android-34' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'SDK 설치에 실패했습니다. 여기서 중단하세요.' }
```

## 2. 정식 Gradle Wrapper 확보

Gradle 8.11.1 공식 배포본은 `https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip`, 체크섬은 같은 URL의 `.sha256` 파일입니다. 현재 Codex 세션에서는 체크섬 URL 연결부터 실패했습니다.

1. 일반 Windows 환경에서 두 파일을 새 `.tools/gradle-bootstrap-<시각>` 폴더에 받습니다. 기존 파일을 덮어쓰지 않습니다.
2. SHA 파일의 64자리 값과 `Get-FileHash <zip경로> -Algorithm SHA256`을 대조합니다. 다르면 실행하지 않습니다. 검증한 ZIP만 새 폴더에 풉니다.
3. 기존 `NextDrive-Pleos/gradle/wrapper/gradle-wrapper.properties`를 `.tools`에 별도로 보관하고, 풀어 놓은 공식 배포본의 `bin/gradle.bat`을 사용해 다음 태스크를 실행합니다. SDK 경로는 1단계의 `ANDROID_HOME`으로 제공합니다.

   ```text
   gradle.bat -p C:\Users\kyc66\NextDrive\NextDrive-Pleos wrapper --gradle-version 8.11.1 --distribution-type bin
   ```

4. 생성된 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`와 properties의 버전을 확인하고 배포본 SHA-256을 Wrapper 설정에 검증 가능한 형태로 고정합니다. 원본 설정과 의도치 않은 차이가 있으면 검토합니다. Wrapper 파일을 임의로 흉내 내서 작성하지 않습니다.

## 3. 기본 APK 빌드

Wrapper 생성과 의존성 다운로드가 성공한 뒤 같은 일반 PowerShell 창에서 실행합니다.

```powershell
& "$project\NextDrive-Pleos\gradlew.bat" -p "$project\NextDrive-Pleos" :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
if ($LASTEXITCODE -ne 0) { throw '빌드 또는 검증에 실패했습니다. APK 실행 성공으로 보고하지 마세요.' }
```

예상 APK 경로는 `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk`입니다. 이번 작업에서는 APK가 생성되지 않았습니다. 테스트 태스크가 NO-SOURCE인 경우 단위 테스트를 실제로 수행했다고 표현하지 않습니다.

## 4. 기존 Pleos AVD에서 앱 실행

Android Studio Device Manager에서 이미 설치한 **Pleos Connect v2.1.0 / API 34 / RAM 4 GB / Cold Boot** AVD를 실행합니다. 일반 PowerShell에서 `adb devices -l`로 대상이 보이는지 확인합니다. 여러 기기가 있으면 대상 serial을 명시합니다.

대상이 실제 Pleos AVD이고 API 34/Automotive 환경인지 확인한 다음 아래 순서를 사용합니다. `<serial>`은 실제 출력값으로 바꿉니다.

```text
adb -s <serial> emu avd name
adb -s <serial> shell getprop ro.build.version.sdk
adb -s <serial> shell pm list features
adb -s <serial> install -r <APK의 전체 경로>
adb -s <serial> shell am start -W -n com.nextdrive.pleos/.MainActivity
adb -s <serial> shell pidof com.nextdrive.pleos
```

각 명령의 실제 성공 여부와 앱 화면 표시·재실행·크기 변경을 확인합니다. 초기 앱은 SDK 미연결 상태가 정상 표시이며, 이를 차량 신호 수신 성공으로 해석하지 않습니다. 설치 실패 시 기존 앱을 임의 삭제하지 않습니다.

## 5. Vehicle SDK 최소 읽기 검증

기본 APK/ADB 경로가 확인된 뒤 공식 Maven에서 `ai.pleos.playground:Vehicle:2.3.1` 아티팩트를 확보하고 실제 패키지·메서드·함수별 `@RequiresPermission`을 확인합니다. 이 의존성은 아직 앱에 추가하지 않았습니다.

최소 시험 후보는 공식 시작 가이드의 앞 왼쪽 도어 위치입니다. 다음을 각각 검증해야 합니다.

1. 일반 앱의 `CAR_DOORS` 선언과 실제 조회 권한 허용 여부. 문서에 권한명이 있다는 이유로 실제 접근을 보장하지 않습니다.
2. Vehicle 서비스 초기화/연결과 생명주기 해제.
3. 도어 위치 Capability의 읽기 접근과 앞 왼쪽 영역 지원.
4. 실제 조회 콜백의 원본 값·영역·단위/상수·수신 시각. null/실패/미지원/미수신을 실제 값과 구분.
5. 에뮬레이터 값임을 표시한 화면 및 관련 앱 로그. 실차 검증으로 표현하지 않음.

이후에 실제 SDK 어댑터를 구현·컴파일·실행하고, 검증에 통과한 소스만 GitHub 연결 도구로 게시합니다. 현재 가상 값을 넣거나 미확인 API를 만들어 연결 완료처럼 보이게 하지 않았습니다.

## 결과 전달 시 필요한 자료

- SDK 설치와 Gradle 태스크의 종료 결과, Wrapper 생성 여부.
- APK 생성 경로 및 실제 설치/시작 결과.
- Pleos AVD 이름/API/Automotive 확인과 앱 화면.
- SDK 연동 이후 항목별 권한/Capability/조회 응답 및 수신 시각.

로그 공유 시 API 키·계정 토큰·인증 파일·개인정보는 제외합니다. 성공과 실패를 `CODEX_PROGRESS.md`에 구분합니다.

공식 근거: [Pleos 시작 가이드](https://document.pleos.ai/docs/pleos-only/getting-started), [Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability), [조회 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [Android 도구 환경 변수](https://developer.android.com/tools/variables), [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
