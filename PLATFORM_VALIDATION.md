# Pleos / AAOS 공통 기반 검증 현황

## 최신 검증 기록 — 사용자 확인: 기본 Debug APK 실행 성공 (2026-11-07)

검증 주체는 사용자이며, 일반 Windows Android Studio와 ADB를 사용한 직접 확인 결과입니다. 아래 성공은 Codex가 이 세션에서 재실행한 결과가 아닙니다. 정확한 실행 시각은 별도로 전달받지 않았으며 기록 시각은 `docs/CODEX_PROGRESS.md`를 참조합니다.

| 항목 | 사용자 직접 확인 결과 |
| --- | --- |
| Android SDK Platform 34 | 설치 완료 |
| Android SDK Build-Tools 35.0.0 | 설치 완료 |
| Command-line Tools / Platform-Tools | 설치 상태 확인 완료. 구체 버전은 이번 보고에 포함되지 않음 |
| ADB | Pleos Connect v2.1.0 에뮬레이터 연결 성공 |
| NextDrive-Pleos Gradle Sync | 성공 |
| Gradle / 기본 앱 의존성 | Gradle 8.11.1 및 관련 의존성 다운로드 성공 |
| Debug APK 빌드 | 성공 |
| APK 설치 / 화면 실행 | Pleos Connect 에뮬레이터에 설치하고 NextDrive 기본 테스트 화면 실행 성공 |

**현재 상태:** 일반 Windows 사용자 환경의 기본 앱 빌드·설치·실행은 확인되었습니다. **실제 Vehicle SDK 차량 데이터 수신은 아직 미구현/미검증입니다.** 기본 앱의 Gradle 의존성 다운로드를 Vehicle SDK 확보·권한 허용·Capability·조회 응답 성공으로 확대 해석하지 않습니다. Release APK, 실차 검증, Lint/단위 테스트 및 화면 크기 변경 검증은 이번 사용자 보고 범위에 포함되지 않았습니다.

기존 Codex 실행 환경의 다운로드 실패·ADB 시작 오류는 아래에 그대로 보존합니다. 이는 당시 실행 주체와 환경의 기록이며 현재 사용자 환경의 성공을 부정하지 않습니다. 이번 기록 반영을 위해 SDK/AVD를 재설치하거나 빌드·실행을 반복하지 않았습니다.

다음 작업은 `docs/VEHICLE_SDK_READ_PLAN.md`의 **최소 1개 차량 정보 읽기 기술 검증 계획**으로 한정합니다. 이번에는 계획만 작성했으며 SDK 의존성·차량 권한·어댑터·서비스 기능을 구현하지 않았습니다.

## 2026-11-07 공통 환경 구축·실행 시도

**현재 완료하지 못한 목표:** 정식 Gradle 빌드, APK 설치·실행, Vehicle SDK 연동 및 최소 1개 차량 정보 수신. 실패/미수행을 성공으로 표현하지 않습니다.

| 점검/시도 | 실제 결과 |
| --- | --- |
| Android Studio/JDK 위치 | 설치 레지스트리는 `C:\Program Files\Android\Android Studio`. 이전 `C:\Users\kyc66\Desktop1\jbr`는 없어 첫 sdkmanager 시도가 invalid JAVA_HOME으로 종료. 실제 포함 JBR로 수정 후 OpenJDK 21.0.8 실행 성공 |
| 컴파일 SDK/Build Tools | 기존 사용자 SDK에는 Platform 36 / Build Tools 36.1.0만 확인. API 34 시스템 이미지는 별개로 설치되어 있음 |
| SDK 설치 시도 | 시스템 SDK를 건드리지 않고 `.tools/android-sdk`에 Platform 34 / Build Tools 35.0.0 설치 요청. 기존 수락 라이선스만 복사해 사용. 올바른 JDK에서도 `Failed to download any source lists`, manifest IO 오류, `Failed to find package 'platforms;android-34'`로 종료 코드 1. 실제 패키지 파일 없음 |
| Gradle 준비 | 공식 CDN의 8.11.1 SHA-256 URL에 연결 실패. 배포본·Wrapper JAR/스크립트 미확보. 정식 Gradle 명령은 실행하지 못함 |
| 공식 Vehicle SDK | 공식 Maven의 Vehicle 2.3.1 POM에 연결 실패. AAR 다운로드·의존성 해석·SDK 초기화·권한/Capability 수신 모두 미검증 |
| ADB 접근 | `adb devices -l` 종료 코드 1. 서버 로그에 `Cannot mkdir '\.android': Permission denied`. 프로젝트 내부 임시 경로 및 문서화된 Android 사용자/호환 경로 지정 후에도 같은 오류. 에뮬레이터 연결·APK 설치 미수행 |
| Pleos AVD/실행 상태 | 기존 설정은 API 34 / RAM 4096 MB / Cold Boot. 사용자 세션에서 정상 부팅 확인된 사실 유지. 이번 프로세스 목록에는 실행 중 Studio/Emulator/QEMU가 없었으며 Codex에서 새 부팅 성공을 주장하지 않음 |

SDK 목록 수신 실패 뒤 '패키지를 찾지 못했다'는 결과는 API 34 자체가 제공되지 않는다는 뜻이 아닙니다. ADB 오류 역시 사용자 세션의 에뮬레이터 설치 불량으로 단정하지 않습니다.

이번에 생성한 도구용 폴더는 `.tools/android-sdk`, `.tools/android-user`, `.tools/tmp`, `.tools/adb-user`, `.tools/adb-home`, `.tools/adb-temp`입니다. 모두 Git 제외 대상이며 인증 파일·로그·라이선스·패키지를 공유하지 않습니다. 전역 환경 변수/PowerShell 정책/드라이버/루트 `.git`을 바꾸지 않았습니다. 로컬 Git/443 연결도 재시도하지 않았습니다.

일반 사용자 환경에서 이어갈 단계는 `docs/PLEOS_ENVIRONMENT_RECOVERY.md`에 정리했습니다. SDK/Gradle이 확보되고 ADB가 실제 Pleos 대상에 연결된 뒤에 APK 및 읽기 전용 도어 신호를 검증해야 합니다. 현재 앱 소스와 미연결 데이터 소스는 변경하지 않았습니다.

## 최신 환경 정정 — 사용자 확인 및 로컬 설정 재확인

- 사용자 직접 확인: Android Studio 정상 실행, Pleos Connect v2.1.0 시스템 이미지 설치, API 34 AVD 생성, RAM 4 GB / Cold Boot 설정, 차량용 디스플레이 에뮬레이터 정상 부팅.
- Codex가 파일로 재확인: `Pleos_Connect_v2.1.0.avd/config.ini`의 `hw.ramSize=4096`, `fastboot.forceColdBoot=yes`, `image.sysdir.1=system-images\\android-34\\pleos_car_emulator\\x86_64\\`, `tag.id=android-automotive`. 이미지 `source.properties`의 API 34 / Automotive 태그 확인.
- 이전의 IDE/AEHD 접근 오류는 이전 Codex 하위 프로세스에서 관측한 결과다. 사용자 세션의 현재 정상 실행·부팅을 부정하지 않는다. 아래 초기 조사 표는 과거 기록으로 보존한다.
- **전체 Gradle 빌드, APK 실행, Vehicle SDK 실제 수신은 여전히 미검증**이다. API 34 시스템 이미지 설치와 컴파일용 Platform 34 설치는 다른 항목이며, 현재 확인한 `platforms` 목록에는 API 36만 있다.
- GitHub 연동 및 최신 작업 기록은 `docs/CODEX_PROGRESS.md`에서 관리한다.

현재 서비스 아이디어는 재검토 중입니다. 기존 NextDrive/카잇다 서비스 기능은 추가 개발하지 않습니다. 폴더·파일·패키지명은 유지하며 기존 Google AI Studio 앱과 프로젝트 파일을 삭제하거나 초기화하지 않습니다.

## 현재 준비한 공통 테스트 앱

- 프로젝트: `NextDrive-Pleos`, 패키지 `com.nextdrive.pleos`.
- 화면: 실행 기기의 Automotive 하드웨어 특성, 차량 데이터 출처, 앞 왼쪽 도어·연료 잔량·누적 주행거리의 지원 여부/수신 상태/최근 수신 시각.
- 화면의 Automotive 확인은 Android의 feature 플래그 확인일 뿐이며 Pleos SDK 지원·실차 연결·심사 통과를 의미하지 않습니다.
- `vehicle/VehicleDataSource`는 앱 내부 인터페이스입니다. 공식 SDK 메서드가 아닙니다. `VehicleStatus`/`VehicleReading`은 항목별 가용성·출처·수신 시각을 보존하는 공통 모델입니다.
- 현재 선택된 `UnavailableVehicleDataSource`는 실제/모의 값 모두 생성하지 않고 '미연결·지원 미확인·미수신'을 반환합니다. 모의 데이터 자동 대체는 없습니다.
- 화면 시작 시 읽기를 요청하고 종료 시 연결 경계를 정리합니다. 이전 화면 세션의 지연 콜백은 무시합니다. 실제 SDK 초기화/연결/Capability/콜백 해제는 SDK 확보와 검증 후 별도 어댑터로 구현합니다.
- 제어·운행 기록·AI 인계·문제 관리·알림 기능은 없습니다. 운전 중 사용을 허용하는 DO 선언도 추가하지 않았습니다. 정차 테스트만 대상으로 하며 실제 주행 규제 연동은 미완료입니다.

## 실제로 실행한 점검

| 점검 | 실제 결과 | 해석 |
| --- | --- | --- |
| 설치 상태 재확인 | Studio/JBR/SDK/AVD는 기존 상태. Platform 36, Build Tools 36.1.0, 스마트폰 API 36.1 AVD 존재 | Platform 34 / Build Tools 35.0.0 / Pleos 이미지 / 정식 Gradle Wrapper는 여전히 없음 |
| Gradle 공식 SHA-256 URL 접근 | PowerShell `Invoke-WebRequest`가 '원격 서버에 연결할 수 없습니다'로 실패 | 다운로드·배포본 설치 미수행. URL: `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256` |
| Vehicle SDK 2.3.1 POM 접근 | 동일한 연결 오류 | POM/AAR 다운로드·SDK 설치 미수행. 저장소 오류나 계정 권한 문제로 단정하지 않음 |
| Android Studio 시작 | `studio64.exe`에 프로젝트 경로를 전달해 프로세스 시작 요청 성공. 직후 창 제목 `Cannot start the IDE` 관측 | IDE 정상 시작·프로젝트 열기·Sync 성공 아님. GUI 오류 원인의 세부 확정은 못함. 기존 SDK/설정은 변경하지 않음 |
| Emulator `-version` | Android Emulator 36.3.10.0 출력 | 실행 파일의 명령 실행 확인. 가상 기기 부팅 성공 아님 |
| Emulator `-list-avds` | `Medium_Phone_API_36.1`만 출력 | Pleos/Automotive AVD 없음. 기존 스마트폰 AVD는 수정하지 않음 |
| Emulator `-accel-check` | `accel: 11`, `Unable to open AEHD device: ERROR_ACCESS_DENIED` | 이 세션에서 가속 장치 접근 실패. PC 전체의 가상화 미지원·설치 불량으로 단정하지 않음 |
| AAPT2 `compile` | 설치된 Build Tools 36.1.0으로 리소스 컴파일 성공 | XML 리소스 처리 확인 |
| AAPT2 `link` | 설치된 Platform 36의 android.jar에 대해 리소스 연결 및 R.java 생성 성공 | 선언된 API 34 Gradle 빌드 검증과 구분 |
| JBR Javac의 R.java 검사 | 종료 코드 3, `Fatal Error: Cannot close compiler resources` | Javac 검사 성공으로 기록하지 않음. 이후 Kotlin 검사는 R.java 소스를 직접 해석하여 수행 |
| 포함 Kotlin 컴파일러 | `kotlinc-jvm 2.1.21`, JRE 21.0.8 확인 | 프로젝트 설정의 Kotlin 플러그인 2.2.21 다운로드/실행과는 별개 |
| Kotlin 소스 검사 | 포함 컴파일러 2.1.21 + 설치된 SDK 36 + AAPT 생성 R.java로 소스 4개 컴파일, 종료 코드 0 | 소스 타입 검사 통과. Gradle 빌드·DEX/APK 생성·런타임·공식 SDK 연동 성공을 의미하지 않음 |
| XML/참조 검사 | XML 파싱과 화면/소스의 문자열 및 View ID 참조 확인 성공 | 화면 실제 표시·가독성·주행 안전 검증을 대체하지 않음 |

검사 산출물은 `NextDrive-Pleos/app/build/validation` 아래에만 생성했습니다. 해당 `build` 폴더는 Git 제외 대상입니다. 검사에 사용한 매니페스트 복사본에는 도구에 필요한 package 속성을 추가했으며 원본 매니페스트/프로젝트의 SDK 버전은 바꾸지 않았습니다. 리소스 패키지 `.ap_`와 컴파일된 `.class`는 설치 가능한 APK가 아닙니다.

## 아직 완료되지 않은 항목

- 정식 Gradle 배포본/Wrapper 생성, 선언된 AGP 8.10.1·Kotlin 2.2.21 의존성 확보 및 Gradle Sync.
- SDK Platform 34 / Build Tools 35.0.0 준비.
- Android Studio 정상 시작과 프로젝트 열기.
- 공식 Vehicle SDK 설치·메서드 시그니처 확인·컴파일·연결 및 Capability 응답 확인.
- Pleos API 34 이미지 설치·Automotive AVD 생성·부팅.
- Gradle `assembleDebug`/`lintDebug`, APK 설치·실행·화면 크기 변경·복귀 검증.
- 실제 차량 신호 수신, 권한 허용/거부, 미지원, 콜백 지연/연결 해제 및 CarUxRestrictions 검증.

SDK 지원 현황은 `SDK_CAPABILITY_MATRIX.md`에 별도로 기록합니다. 도메인 이름이 공식 문서에 있다는 이유만으로 현재 에뮬레이터나 차량에서 지원된다고 판단하지 않습니다.

## 이 세션 밖에서 필요한 환경 조치

네트워크 요청과 IDE/가속 장치 접근이 이 실행 환경에서 실패했습니다. 제한을 우회하거나 관리자 권한을 요청하지 않았습니다. 일반 사용자 세션의 Android Studio/PowerShell에서 다음을 확인해야 합니다.

1. `C:\Users\kyc66\Desktop1\bin\studio64.exe`를 직접 열어 정상 시작되는지 확인합니다. 일반 실행에서도 실패하면 오류 내용을 먼저 확인하고 재설치·설정 초기화를 임의로 진행하지 않습니다.
2. SDK Manager에서 Platform 34 / Build Tools 35.0.0을 추가합니다. 기존 SDK·AVD는 그대로 둡니다. Gradle JDK는 포함 JBR 경로를 우선 사용합니다.
3. 공식 Gradle 8.11.1 배포본과 체크섬을 받아 검증하고 프로젝트 전용 배포본으로 정식 Wrapper를 생성합니다. 현재 `gradle-wrapper.properties`만 있으며 스크립트/JAR는 없습니다. 기존 README의 Wrapper 생성 절차를 참고합니다.
4. 공식 SDK Update Site를 추가해 Pleos API 34 이미지를 설치하고 별도 Automotive AVD를 생성합니다. Windows URL은 아래와 같습니다. 공식 안내는 Cold boot / RAM 4 GB이며 실제 제공 버전을 설치 시 다시 확인합니다.

   `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

5. 일반 세션에서도 가속 점검이 접근 거부되는지 확인합니다. BIOS·Windows 기능·드라이버·관리자 작업은 원인과 필요한 변경을 구체적으로 설명하고 별도 승인 후 수행합니다.
6. Pleos Maven 저장소에 접근 가능한 환경에서 Vehicle SDK POM/AAR와 실제 API를 확인한 뒤 읽기 전용 어댑터·최소 권한을 추가합니다. 의존성 추가만으로 설치/연동 성공이라고 기록하지 않습니다.
7. 정식 빌드와 Pleos 실행 후 항목별 Capability 결과, 단위·영역, 수신 시각, 오류 상태를 확인하고 아래 점검표를 갱신합니다.

현재 환경 구축과 공식 SDK 설치는 사용자가 요청한 작업 범위입니다. 같은 범위의 승인을 반복 요청하지 않으며, 여기 남긴 미완료 사유는 **현재 실행 환경에서의 접근 실패**입니다. 별도 관리자 작업이나 시스템 전역 설정 변경은 자동으로 수행하지 않습니다.

## 공통 기반 완료 기준

정상 IDE 시작 → 정식 Wrapper/Gradle 빌드·Lint → Pleos AVD 부팅 → APK 시작 → 최소 1개 차량 신호의 권한/Capability/수신 응답을 검증하고 한 화면에 출처·최근 시각과 함께 표시 → 재실행·크기 변경·연결 실패 대응 확인입니다. 서비스 아이디어가 확정되지 않아도 이 범위까지만 진행합니다.
