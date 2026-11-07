## 최신 상태 — Pleos Vehicle SDK 실제 수신 확인

2026-11-07: 기존 v0.1을 보존한 채 SDK 2.3.2로 Pleos 에뮬레이터의 SOC 100%, 누적 주행거리 0.0km를 실제 수신·SQLite 저장했습니다. 연료 잔량은 미지원 응답으로 기록합니다. 실차값과 구분하며 앱 프로세스 재시작 후 복원, 기존 24개 회귀와 관측 6개 검사를 통과했습니다. [상세 결과](../docs/VEHICLE_SDK_232_RUNTIME.md).

# CARITDA v0.1 — 현재 실행 가능한 MVP

2026-11-07 사용자의 신규 MVP 명세가 아래의 서비스 보류 기록보다 우선합니다. 앱 표시명은 CARITDA(가칭), 프로젝트 폴더와 패키지는 기존 NextDrive-Pleos / com.nextdrive.pleos를 유지합니다.

운전자 A/B 선택 → 수기 증상 저장 → 앱 재시작 후 복원 → 다음 운전자 확인 → 점검 메모·상태 변경 → 정비 상담용 요약을 구현했습니다. SQLite에 로컬로 보관하며 재발 시 기존 카드에 이력을 추가합니다. 실제 차량 센서값이나 외부 전송을 구현한 것으로 표시하지 않습니다.

기존 Android Studio에서 이 폴더를 열고, 기존 JDK/SDK/Gradle 설정을 유지한 채 Pleos Connect v2.1.0 AVD를 선택하고 app을 Run 하세요. 정차 중 플랫폼 화면 제한이 해제된 경우에만 입력할 수 있습니다. 앱 삭제·데이터 초기화는 하지 마세요.

상세 사용 순서·시험 결과·파일 구조는 [MVP 검증 안내](../docs/CARITDA_V01_MVP.md)를 참고하세요. Debug 앱과 계측 APK 빌드 및 에뮬레이터 24개 검사는 통과했습니다. Lint 의존성 다운로드는 제한으로 실패했습니다. 실차와 Release 빌드는 미검증입니다.

## 아래 내용은 기본 환경 구축 당시의 과거 기록입니다

다음의 미설치·미빌드·서비스 보류 설명은 당시 상태이며 현재 상태는 위 기록과 최신 진행 보고서를 따릅니다. 과거 기록과 기존 차량 데이터 모델·레이아웃은 보존합니다.

# NextDrive-Pleos

## 현재 범위

아이디어 재검토 중이므로 기존 서비스 기능은 개발 보류입니다. 이 프로젝트는 Pleos/AAOS 공통 실행 환경과 차량 상태 한 화면 테스트만 준비합니다. 실행 검증 현황은 상위 `PLATFORM_VALIDATION.md`를 참조하세요.

테스트 화면은 실제 기기의 Automotive 특성, 차량 데이터 연결 상태, 앞 왼쪽 도어·연료·주행거리의 지원 확인 상태/값/최근 수신 시각을 표시합니다. 현재 `UnavailableVehicleDataSource`만 연결되어 있어 차량 수치는 표시하지 않습니다. Vehicle SDK 미설치 상태이며 실제 데이터 수신·Capability 확인은 아직 이루어지지 않았습니다. 모의 값이나 서비스 기능은 넣지 않았습니다.

폴더·패키지·앱 표시명은 기존 식별자를 유지합니다. 아래는 초기 골격 준비 기록이며 새로운 서비스 기능 구현 지시가 아닙니다.

Pleos Connect 차량 내부 AAOS용 신규 Kotlin 프로젝트입니다. 상위 `AGENTS.md`, `NEXTDRIVE_SPEC.md`, `DEVELOPMENT_PLAN.md`를 개발 기준으로 사용합니다. 기존 Google AI Studio 프로토타입의 코드·리소스는 사용하지 않았습니다.

## 현재 상태

- AAOS 필수 하드웨어 선언, 크기 변경 가능한 Activity, 한국어 시작 화면, Kotlin DSL 빌드 설정을 작성했습니다.
- 시작 화면은 데이터 미연결 상태를 안내합니다. 차량 수치, 운행 관리, 모의 데이터, 실제 SDK 연결은 아직 구현하지 않았습니다.
- 운전 중 사용을 허용하는 `distractionOptimized` 선언은 없습니다. 주행 규제 연동·카테고리 검증 전에는 정차 상태에서만 검증합니다.
- API 34와 Build Tools 35.0.0, Gradle 8.11.1이 준비되지 않아 **정식 빌드·APK 생성·앱 실행은 미검증**입니다. Pleos AVD 자체의 부팅은 사용자 세션에서 이미 확인했습니다.
- `gradle-wrapper.properties`는 사용할 버전 설정만 담고 있습니다. **`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`는 아직 없습니다.** 공통 환경 준비 요청에 따라 프로젝트 내부 SDK 설치를 시도했으나 패키지 목록 다운로드 실패로 종료됐습니다. Gradle/Pleos Maven 접근도 실패했습니다. 자세한 결과는 상위 `PLATFORM_VALIDATION.md`와 `docs/PLEOS_ENVIRONMENT_RECOVERY.md`를 확인하세요.

## 일반 Windows 환경에서 이어갈 실행 준비 순서

아래는 이후 수행할 절차이며 이번 작업에서 실행하지 않았습니다.

1. 현재 Android Studio 실행 파일: `C:\Program Files\Android\Android Studio\bin\studio64.exe`. 이전 `Desktop1` 경로는 현재 존재하지 않습니다.
2. SDK Manager에서 Android SDK Platform 34와 Build Tools 35.0.0을 설치합니다. 기존 API 36 SDK/휴대전화 AVD는 보존합니다.
3. 승인된 프로젝트 전용 Gradle 8.11.1 배포본을 공식 배포처에서 받고 SHA-256을 확인한 뒤, 이 프로젝트에서 `gradle wrapper --gradle-version 8.11.1 --distribution-type bin`으로 정식 Wrapper를 생성합니다. 생성된 Wrapper와 배포본의 체크섬도 검증·기록합니다.
4. Android Studio에서 이 폴더를 엽니다. SDK 위치를 `C:\Users\kyc66\AppData\Local\Android\Sdk`, Gradle JDK를 현재 포함 JBR(`C:\Program Files\Android\Android Studio\jbr`)로 확인합니다. 이번 점검에서 Java 21.0.8 실행을 확인했습니다. 시스템 전역 설정은 임의로 바꾸지 않습니다.
5. 이미 설치·부팅 확인된 Pleos Connect v2.1.0/API 34 AVD(RAM 4 GB, Cold boot)를 사용합니다. 재설치하거나 기존 AVD를 삭제하지 않습니다.
6. Gradle Sync 후 `gradlew.bat :app:assembleDebug :app:lintDebug`를 실행합니다. APK 예상 경로는 `app/build/outputs/apk/debug/app-debug.apk`이며 아직 파일은 없습니다.
7. Pleos AVD를 선택해 Run으로 실행합니다. 런처 표시, 시작 화면, 크기 변경, 백그라운드 복귀, 재실행, 플랫폼의 주행 제한을 확인합니다.

설치·환경 변경 승인 항목과 공식 링크는 상위 `DEVELOPMENT_PLAN.md`를 참조하세요. 패키지 ID `com.nextdrive.pleos`는 개발용 제안이며, 공식 프로젝트 등록·권한 신청 전 확정해야 합니다.
