# NextDrive — Pleos Connect 개발 계획

## 현재 우선 계획 — 공통 플랫폼만 개발

아이디어 전면 재검토에 따라 기존 서비스 기능 개발을 보류합니다. 아래의 초기 서비스 아키텍처·MVP 표는 이력으로 보존하며 사용자에게 새 명세를 받기 전에는 실행하지 않습니다.

현재 순서: 환경 점검·접근 확인 → 공식 도구/SDK 준비 → Android Studio/Gradle 빌드 → Pleos AVD 부팅 → 한 화면 차량 상태 표시 → 항목별 실제 권한/Capability/수신 성공 기록. 새로운 서비스 로직, 가족 계정, 운행·인계·문제 관리, 알림은 추가하지 않습니다.

현재 테스트 화면은 서비스 도메인과 분리한 `VehicleDataSource` 경계를 사용합니다. SDK에 연결되지 않은 경우 명시적으로 미연결·지원 미확인·미수신을 표시합니다. 구체적인 실행 결과와 외부 작업 절차는 `PLATFORM_VALIDATION.md`를 기준으로 확인합니다.

## 초기 환경 조사 및 기존 기획 기록 (아래 내용은 과거 작업 기준)

작성일: 2026-11-06 (Asia/Seoul). 현재 PC에서 수행한 읽기 전용 점검과 공식 문서 열람을 기준으로 합니다.

## 1. 개발 대상과 이번 작업 범위

- 대상은 **Pleos Connect 차량 내부 인포테인먼트에서 직접 실행하는 Android Automotive OS 앱**입니다. Android Studio와 Kotlin을 사용하고 Pleos 환경에서 실행 가능한 APK를 만듭니다.
- 신규 프로젝트: `C:\Users\kyc66\NextDrive\NextDrive-Pleos`.
- 상위 `AGENTS.md`와 `NEXTDRIVE_SPEC.md`를 요구사항으로 사용합니다. Google AI Studio의 기존 Android 프로토타입은 탐색·복사·수정하지 않았으며 코드·리소스를 재사용하지 않습니다. 기존 기능을 가져올 필요가 생기면 먼저 확인합니다.
- 스마트폰 알림 앱, 서버 동기화, FCM, LLM은 후속 범위입니다.
- 이번에는 환경 점검, 기본 AAOS 시작 화면과 빌드 설정, 구조 및 검증 계획까지만 준비했습니다. 설치·시스템 설정 변경·관리자 작업·하위 `codex` 실행은 하지 않았습니다.

## 2. 공식 요구사항과 선택 버전

공식 요구사항과 프로젝트에서 선택한 버전은 구분합니다. 아래 선택 조합은 문서상 호환 범위를 확인했지만 실제 Gradle Sync/빌드 검증 전입니다.

| 항목 | 공식 기준 / 확인 내용 | 이번 프로젝트 선택 |
| --- | --- | --- |
| 플랫폼 | Pleos Connect는 AAOS 기반 인포테인먼트 플랫폼 [S1][S3] | AAOS 필수 하드웨어를 선언한 단일 `app` 모듈 |
| IDE / 언어 | Android Studio / Kotlin 또는 Java [S2][S4] | 설치된 Android Studio / Kotlin |
| 최소 OS | `minSdk` API 28 이상 [S2][S4] | `minSdk = 28` |
| SDK / 이미지 | 시작 가이드는 SDK API 34와 Pleos API 34 이미지 안내 [S2][S4] | `compileSdk = 34`, `targetSdk = 34`로 시작. target 34는 프로젝트 선택이며 공모전의 별도 의무라고 단정하지 않음. 제출 전 최신 기준 재확인 |
| Gradle | 8.0 이상 [S1][S4] | 8.11.1, Wrapper 설정 파일만 작성 |
| Android Gradle Plugin | Pleos 문서는 정확한 AGP 버전을 강제하지 않음 | AGP 8.10.1. 공식 AGP 8.10 계열 기준 Gradle 8.11.1 / Build Tools 35.0.0 / JDK 최소 17 [S8] |
| Kotlin 플러그인 | 호환 범위 내 조합 선택 | 2.2.21. Gradle 8.11.1과 AGP 8.10.1은 KGP 공식 지원 범위 내 [S9]. Android Kotlin/D8/R8 호환성 기준도 함께 확인 [S13] |
| Java | AGP와 Gradle의 요구사항을 함께 충족 | 포함 JBR 21.0.8로 Gradle 실행 예정. Gradle 8.11.1은 Java 21 지원 [S10]. Java/Kotlin 바이트코드 대상은 모두 17 |
| Pleos Emulator | 문서 표기 2.1.0, API 34, AVD RAM 4 GB / Cold boot [S4] | 설치 및 AVD 생성 필요. 설치 시 제공 버전 재확인 |
| Vehicle SDK | 시작 가이드 예제 `ai.pleos.playground:Vehicle:2.3.1` [S2] | 후속 SDK 검증 시 후보. 이번에는 의존성·권한·API 호출을 추가하지 않음 |

SDK API 36이 이미 설치되어 있어도 Pleos API 34 이미지를 대체하지 않습니다. Android Emulator 실행 파일 버전 36.3.10과 Pleos 시스템 이미지 버전 2.1.0은 서로 다른 구성 요소입니다. Gradle과 Android Gradle Plugin 역시 별개입니다.

## 3. 실제 PC 점검 결과

| 항목 | 확인한 사실 | 판단 / 후속 조치 |
| --- | --- | --- |
| Android Studio | 레지스트리 설치 경로 `C:\Users\kyc66\Desktop1`; `bin\studio64.exe`, `product-info.json`, `build.txt` 존재. 빌드 `AI-252.27397.103.2522.14514259`, 설정 폴더 `AndroidStudio2025.2.2` | 설치 확인. 이번에 GUI 실행·Gradle Sync는 하지 않음 |
| JDK | `Desktop1\jbr\bin\java.exe -version`, `javac.exe -version` 모두 성공; OpenJDK/Javac 21.0.8 | 별도 JDK 설치를 우선 요구하지 않음. 포함 JBR 활용 예정 |
| Android SDK | `C:\Users\kyc66\AppData\Local\Android\Sdk` 존재; Platform API 36 / Build Tools 36.1.0 | 선택한 Platform 34 / Build Tools 35.0.0은 해당 SDK 경로에 없음. 추가 설치 필요 |
| Platform Tools | 절대 경로로 `adb.exe version` 성공; ADB 1.0.41 / Platform Tools 36.0.0 | 설치 확인. ADB 서버 기동·기기 접속·APK 설치는 하지 않음 |
| Command-line Tools | `cmdline-tools\latest`의 `source.properties`: 19.0; `sdkmanager.bat`, `avdmanager.bat` 존재 | 파일 확인만 수행. 명령 실행·라이선스 동의·패키지 설치는 하지 않음 |
| Android Emulator | `emulator.exe` 존재, `source.properties` 버전 36.3.10 | 실행·AVD 부팅 미검증 |
| 기존 이미지 / AVD | `android-36.1\google_apis_playstore\x86_64`; `Medium_Phone_API_36.1.ini`, `Medium_Phone.avd` 확인. RAM 2048 MB / medium_phone | 스마트폰용 이미지/AVD. 보존하고 별도 Automotive AVD 생성 필요 |
| Pleos 이미지 / AVD | 확인한 SDK `system-images`와 사용자 AVD 목록에 Pleos/API 34 이미지·Automotive AVD 없음 | 설치 필요 |
| Git | `Get-Command git`에서 발견 못함. 표준 `Program Files\Git`, 사용자 `Local\Programs\Git`, `C:\Git` 등 후보 경로에도 실행 파일 미확인 | 현재 세션에서 사용 불가. 다른 경로 설치 가능성은 남아 있음. 사용자 지정 설치 위치 확인 또는 승인 후 설치 |
| Gradle | `Get-Command gradle`에서 발견 못함. 사용자 `.gradle` 폴더 없음. Studio 내 Wrapper 파일/Gradle 실행 배포본도 검색 결과 없음 | 프로젝트용 Gradle 및 정식 Wrapper 준비 필요. 전역 Gradle 설치는 불필요 |
| 환경 변수 | 현재 프로세스에서 `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` 미설정. java/adb/git/gradle은 PATH에서 검색되지 않음 | 도구 미설치와 PATH 미등록을 구분. 우선 IDE 프로젝트 설정/절대 경로 활용, 시스템 PATH 변경 불필요 |
| 가속 드라이버 | `aehd` 서비스 Running 확인 | 실제 하드웨어 가속·Pleos AVD 부팅 성공을 의미하지 않음 |
| OS / 하드웨어 | 레지스트리 DisplayVersion 26H2 / Build 26300, ADB는 Windows 10.0.26300 표기. WMI OS·메모리·Hypervisor 조회는 접근 거부 | 레지스트리 ProductName만으로 Windows 제품명을 단정하지 않음. RAM/CPU 가상화/여유 디스크/호스트 에뮬레이터 요구사항은 추가 확인 필요 |

점검 방법: `Get-Command`, 후보 경로 `Test-Path`/파일 목록, SDK `source.properties`, AVD `config.ini`, Studio 제품 정보, 레지스트리 설치 정보, Java/Javac/ADB 버전 실행, `Get-Service`를 사용했습니다. 전체 디스크를 검색하거나 다른 앱 프로젝트를 읽지 않았으므로 발견되지 않은 항목을 PC 전체의 미설치로 단정하지 않습니다. 일부 PowerShell 묶음 명령은 후보 경로 부재 또는 WMI 접근 거부로 종료 코드 1을 반환했습니다. 이를 모두 성공한 점검으로 기록하지 않습니다.

## 4. 사전 승인이 필요한 설치·변경

아래는 제안 목록입니다. 아직 수행하거나 승인받지 않았습니다.

1. **필수:** Android SDK Platform 34, Build Tools 35.0.0 추가 설치. 기존 SDK·이미지는 삭제하지 않습니다.
2. **필수:** 공식 Gradle 8.11.1 배포본을 프로젝트 전용 도구 경로에 받아 공식 SHA-256을 확인하고 정식 Wrapper를 생성합니다. Wrapper JAR·스크립트·배포본 체크섬을 확인합니다. AGP/Kotlin 및 이후 필요한 라이브러리의 최초 다운로드를 포함합니다. 전역 설치는 하지 않습니다.
3. **필수:** SDK Update Sites에 아래 Windows용 URL을 추가하고 Pleos API 34 시스템 이미지를 설치합니다. Automotive 디바이스를 별도로 생성하고 RAM 4 GB / Cold boot로 구성합니다 [S4].

   `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

4. **프로젝트 설정:** Android Studio의 SDK 경로와 Gradle JDK를 확인·지정합니다. 우선 포함 JBR을 활용합니다. JAVA_HOME/PATH 등 시스템 전역 변경은 별도 필요성이 확인될 때만 제안합니다.
5. **권장:** Git 설치 위치 재확인 후 없다면 Git for Windows 설치. APK 빌드 자체의 필수 도구는 아니지만 변경 이력 관리에 필요합니다. Git 초기화·커밋·원격 연결은 아직 하지 않았습니다.
6. **조건부:** 부팅 점검에서 문제가 확인된 경우에만 Windows 가상화 기능/BIOS/드라이버 변경을 제안합니다. 현재 `aehd`가 실행 중이라는 이유만으로 추가 드라이버 설치나 시스템 변경을 진행하지 않습니다.
7. **SDK 단계:** Pleos Playground 계정과 신규 프로젝트를 준비하고 실제 권한 상태·카테고리를 확인합니다. 공식 문서는 Vehicle State가 별도 신청 없이 사용 가능한 상태라고 설명하지만 [S7], 도메인별 권한 선언과 심사 정책은 별도 확인해야 합니다 [S3][S5]. 계정 생성·약관 동의·외부 신청은 이번에 하지 않았습니다.

## 5. 신규 프로젝트 구조와 아키텍처

이번에 만든 구조:

```text
NextDrive/
├─ AGENTS.md
├─ NEXTDRIVE_SPEC.md
├─ DEVELOPMENT_PLAN.md
└─ NextDrive-Pleos/
   ├─ README.md
   ├─ .gitignore
   ├─ settings.gradle.kts
   ├─ build.gradle.kts
   ├─ gradle.properties
   ├─ gradle/wrapper/gradle-wrapper.properties
   └─ app/
      ├─ build.gradle.kts
      └─ src/main/
         ├─ AndroidManifest.xml
         ├─ java/com/nextdrive/pleos/MainActivity.kt
         └─ res/
            ├─ layout/activity_main.xml
            └─ values/strings.xml
```

위 Wrapper 설정 파일은 버전 지정만 포함합니다. `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`는 다운로드/설치 승인 후 생성할 예정이며 아직 없습니다. 현재 골격을 즉시 빌드 가능한 완성 프로젝트라고 표현하지 않습니다.

최소 실행 단계에서는 의존성을 줄이기 위해 표준 Activity와 XML을 사용합니다. 시작 화면에는 한국어 안내와 차량 데이터 미연결 상태만 표시하고, 수치나 기능 완료 상태를 꾸며 넣지 않습니다. 매니페스트에 `android.hardware.type.automotive` 필수 조건을 선언하고 Activity 크기 변경을 허용합니다. 스마트폰 모듈·Android Auto 투사 서비스·불필요한 네트워크/차량 권한은 추가하지 않습니다.

MVP 확장 시 단일 `app` 모듈 안에서 다음 패키지 경계를 사용합니다. 아래 패키지와 클래스는 **설계이며 아직 구현하지 않았습니다**.

| 경계 | 책임 |
| --- | --- |
| `ui/driver`, `ui/home`, `ui/trip`, `ui/handover`, `ui/issue` | 운전자 선택, 차량 요약, 수동 운행, 인계, 타이어 문제 상세. ViewModel과 UI 상태를 사용하고 화면에서 SDK 직접 호출 금지 |
| `domain/model`, `domain/usecase` | Driver/Trip/Snapshot/Issue/Action/Handover, 운행 시작·종료, 변화 비교, 규칙 기반 보고서, 인계 확인 |
| `data/local`, `data/repository` | Room 기반 영속 저장 및 트랜잭션. DataStore는 선택 프로필·표시 모드 등 설정용. SDK와 화면을 저장소 경계로 분리 |
| `data/vehicle/VehicleDataSource` | 차량 상태/기능 가용성/데이터 출처를 제공하는 앱 내부 추상 인터페이스. Pleos 실제 API로 오해할 이름·메서드를 만들어 호출하지 않음 |
| `data/vehicle/fake` | 명세서의 재현 가능한 가상 시나리오 |
| `data/vehicle/pleos` | 검증된 SDK 조회·콜백, Capability·권한·생명주기·오류를 앱 모델로 변환 |
| `platform/ux` | 공식 CarUxRestrictions 연동. 주행/정차 상태 미확인 시 상세 조작 제한 |

초기에는 수동 의존성 주입으로 복잡도를 줄입니다. 실제/모의 전환은 이후 명시적인 빌드 변형 또는 데이터 소스 설정으로 구성하고, 모의 빌드에는 별도 앱 ID/라벨을 사용합니다. 현재 시작 화면에는 어느 데이터 소스도 연결하지 않았습니다.

## 6. 실제 SDK와 모의 데이터 경계

| 기능 | 모의 단계 | 실제 SDK 단계 |
| --- | --- | --- |
| 가족 4명 선택 / 수동 운행 시작·종료 | 로컬 테스트 프로필, 앱 버튼 | 로컬 기능 유지. 자동 운전자 인식/시동 연동을 가정하지 않음 |
| 연료·주행거리·타이어 경고 | 53%→18%, +84 km, 앞 왼쪽 타이어 경고. 화면·보고서에 가상 데이터 표시 | 해당 도메인 API별 조회 권한·Capability·단위·영역·이벤트 가용성 확인 후 연결 |
| 스냅샷·변화 분석 | 가상 입력으로 규칙 검증 | 동일 도메인 로직에 조회 성공한 값만 전달. 결측값은 변화량 계산에서 제외 |
| 문제 카드 / 담당 / 점검 이력 / 확인 | 로컬 저장 기능 | 차량 데이터의 문제 관측 상태와 사람의 조치 기록을 분리 |
| 보고서 | 규칙 기반 한국어 존댓말 | 확인된 사실과 출처만 요약. LLM 없어도 작동 |
| 주행 규제 | 모의 제한 상태 테스트로 명시 | CarUxRestrictions의 플랫폼 정책 사용. 모의 '정차' 값으로 실제 규제를 우회하지 않음 |

모든 관측에는 출처, 획득 시각, 마지막 확인 시각, 가용성/오래됨 상태를 보존합니다. 미지원, 권한 거부, 연결 실패, 아직 미수신, 경고 없음은 다른 상태입니다. 실제 모드에서 실패 시 모의 값으로 자동 전환하지 않습니다. 사용자 입력/추정/차량 경고도 구분합니다. 전체 경고 조회 실패를 빈 경고 목록으로 저장해 정상으로 오해시키지 않습니다.

## 7. SDK 검증 목록과 기술적 제약

- Maven 저장소는 공식 `https://nexus-playground.pleos.ai/repository/maven-releases/`를 설정에 등록했습니다. 네트워크 접근·인증 필요 여부·실제 아티팩트 해석·2.3.1과 이미지 호환성은 아직 검증하지 않았습니다 [S2].
- 공식 대표 권한 매핑: Fuel/EvBattery는 `CAR_ENERGY`, Odometer는 `CAR_DRIVE`, Tire/Safety는 `CAR_SAFETY`입니다 [S5]. 이는 후보일 뿐이며 **각 함수의 `@RequiresPermission`을 다시 확인**합니다. 이번 매니페스트에는 이 권한을 선언하지 않았습니다.
- 각 지표의 실제 조회 메서드, 콜백 등록/해제, 타입·단위·타이어 위치 영역, 퍼센트 제공 여부를 API Reference에서 확인합니다. 예를 들어 잔량이 리터로 제공될 경우 검증되지 않은 탱크 용량으로 퍼센트를 만들지 않습니다.
- 기능별 Capability와 읽기 접근(`READ` 또는 `READ_WRITE`), 지원 영역을 확인한 다음 조회합니다. 도메인 존재만으로 모든 신호를 지원한다고 판단하지 않습니다 [S6]. 제어용 `CONTROL_CAR_*` 권한이나 set API는 사용하지 않습니다.
- 권한 선언과 실제 접근·심사 승인은 동일하지 않습니다. 공식 정책은 사전 협의/승인된 권한 범위를 요구하므로 카테고리와 사용 목적을 확인합니다 [S3].
- Vehicle 초기화·해제와 비동기 콜백을 Activity/프로세스 생명주기에 맞게 관리합니다. 에뮬레이터 가상 차량 신호 수신 성공은 실차 검증이 아닙니다.
- 자동 시동 팝업, P단 감지, 자동 종료, 전원 종료 후 수집은 검증 전입니다. 수동 시작·종료 및 영속 상태 복원을 기본으로 합니다. P단/주차브레이크/엔진 정지는 서로 다릅니다.
- 주행 제한은 `CarUxRestrictionsManager`의 변경을 반영해야 합니다 [S11]. 앱 카테고리와 운전 중 사용 가능 범위는 미확정이며 기본 화면도 아직 주행 중 사용 승인·검증 상태가 아닙니다. 상세 조작을 만들기 전에 이 경계를 구현합니다.
- 차량 화면은 항상 가로 전체 화면이라고 가정하지 않습니다. Pleos의 portrait/landscape·멀티 윈도우·포그라운드 복귀에 대응합니다 [S12].
- 차량 제어·운행 차단·고장 원인 단정·운행 안전 보증은 구현하지 않습니다. 해결 처리는 점검 수행, 경고 재관측, 사용자의 해결 확인을 구분합니다.

## 8. MVP 순서와 완료 기준

| 단계 | 작업 | 완료 기준 / 현재 상태 |
| --- | --- | --- |
| 0A 환경·골격 | 도구 비교, 독립 폴더, 문서, AAOS 시작 화면 | 이번에 파일 준비. XML 정적 확인과 버전 실행만 수행; 실행 가능한 APK 단계는 아님 |
| 0B 기본 실행 | 승인된 도구 설치, 정식 Wrapper, Sync, 빌드, Pleos AVD 실행 | `assembleDebug`, `lintDebug` 성공 기록; APK와 런처/시작 화면 확인; 크기 변경·복귀·재실행 확인. 현재 미수행 |
| 1 화면·규제 경계 | 운전자 선택→홈→운행→인계→문제 상세, CarUxRestrictions 반영 | 정차 탐색 확인; 주행·미확인 상태에서 상세 조작 차단, 필수 정보 유지. 플랫폼 정책·앱 카테고리 검토 |
| 2 로컬 운행 | 가족 4명, 가상 스냅샷, 수동 시작/종료, Room 저장 | 중복 시작/종료 방지; 진행 중 운행 및 저장 데이터 재실행 복원 |
| 3 분석·인계 | 결측 대응 변화 비교, 규칙 기반 보고서, 다음 운전자 확인 | 연료 53→18%, +84 km, 신규 저압의 가상 시나리오 재현. 불명 데이터를 정상/확정 사실로 표시하지 않음 |
| 4 문제 해결 흐름 | 타이어 카드, 담당 지정, 조치 이력, 해결 확인 | 인계 확인·점검 수행·실제 해결을 별도 상태로 저장. 미해결 문제가 다음 운전자에게 유지 |
| 5 Pleos 연동 | 후보 SDK·권한·Capability·가용 신호 연결 | 도메인별 지원·권한·성공/실패 기록. 실제/모의 출처 구분, 수신 지연·실패 처리. 미지원 신호는 unknown 유지 |
| 6 시연·배포 | 시나리오 회귀, 오류 대응, APK, 설치/시연 문서 | Pleos AVD에서 끝까지 시연·재실행 검증. APK 경로·빌드 결과·미검증 실차 항목 명시. 제출용 서명/등록 기준 별도 확인 |

이후 일정이 허용되면 서버·스마트폰 알림·예약을 확장합니다. 규칙 기반 보고서를 먼저 완성하고 LLM/서버 의존성을 핵심 흐름에 넣지 않습니다.

중요 테스트: 결측/미수신/오래된 데이터, 거리계 값 역전, 잔량 증가를 확정 주유로 오해하지 않기, 경고 반복 시 문제 카드 중복 방지, 담당 지정과 해결 구분, 운전자 교체 시 확인 상태, 저장 트랜잭션/프로세스 복원, 규제 활성화·미확인 상태. 실제 기능 구현 단계에서 필요한 테스트를 작성합니다.

## 9. 이번 검증과 다음 사용자 작업

- 확인 완료: 문서 읽기, 설치 파일/SDK 패키지/AVD 설정 조사, Java/Javac/ADB 버전 실행, 공식 문서의 요구사항 대조, 프로젝트 파일 생성.
- 정적 검증: 작성 XML 파싱, 문자열 참조, automotive 필수 선언, 런처 Activity 연결과 차량 권한 미선언 확인. Kotlin 컴파일이나 Android 빌드를 대체하지 않습니다.
- 미수행: 도구 다운로드·설치, 환경 변수/IDE 설정 변경, Wrapper 생성·실행, Gradle Sync, Kotlin 컴파일, Lint, APK 생성·설치, Pleos AVD 부팅, SDK 연결/Capability/권한 수신 테스트, 실차 테스트.
- WMI는 접근 거부되어 메모리·가상화·디스크 여유의 호스트 요구사항 충족을 확정하지 못했습니다. Windows 설정/작업 관리자 등에서 확인이 필요합니다.
- 다음 사용자 결정: 4절의 Platform 34 / Build Tools 35.0.0, 프로젝트 전용 Gradle·의존성, Pleos 이미지 설치 및 IDE/AVD 설정 진행 승인. Git은 별도 경로가 있다면 알려주시고 없다면 설치 여부를 결정합니다. Pleos 계정의 신규 프로젝트·Vehicle State 접근 상태도 확인합니다. 비밀 키는 문서나 채팅에 공유하지 않습니다.
- 다음 개발 완료 목표는 **Pleos Connect Emulator에서 시작 화면을 띄우는 최소 APK**입니다. 그 다음에 운전자 선택과 로컬 MVP를 진행합니다.

## 공식 근거 (2026-11-06 열람)

- [S1 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [S2 Pleos Connect 앱 개발 시작하기](https://document.pleos.ai/docs/pleos-only/getting-started)
- [S3 SDK Overview / API·권한 정책](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)
- [S4 Pleos Connect Emulator 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [S5 Vehicle SDK 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [S6 Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)
- [S7 프로젝트 생성 및 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [S8 Android Gradle Plugin 8.10 호환성](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [S9 Kotlin Gradle Plugin 호환성](https://kotlinlang.org/docs/gradle-configure-project.html)
- [S10 Gradle 8.11.1 Java 호환성](https://docs.gradle.org/8.11.1/userguide/compatibility.html)
- [S11 주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [S12 앱 구조 및 멀티 윈도우](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)
- [S13 Android Kotlin / AGP / D8 / R8 호환성](https://developer.android.com/build/kotlin-support)
