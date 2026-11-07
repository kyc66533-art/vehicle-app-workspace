# 다음 기술 검증 계획 — 공식 Vehicle SDK 최소 1개 값 읽기

상태: **계획만 작성. 실제 Vehicle SDK 수신은 미구현/미검증.** 사용자 환경에서 API 34 / Build Tools 35.0.0 / Gradle 8.11.1 / Debug APK 빌드 / Pleos Connect v2.1.0 설치·기본 화면 실행은 확인되었습니다. 이미 성공한 환경 구축을 반복하거나 기존 소스를 초기화하지 않습니다.

## 목표와 제한

일반 앱에서 공식 Pleos Vehicle SDK의 읽기 전용 차량 정보를 최소 1개 실제 수신하는지 확인합니다. 1차 후보는 공식 시작 예제의 **앞 왼쪽 도어 위치**입니다. 문서에 있는 API라도 현재 에뮬레이터와 앱 권한에서 접근 가능한지는 실제 시험 전까지 확정하지 않습니다.

가족 인계·AI 진단·정비·알림 등 서비스 기능, 차량 제어, 루팅/시스템 앱 전환/서명·권한 우회는 범위에 넣지 않습니다. 지원하지 않거나 읽지 못하면 그대로 실패/미지원으로 기록합니다. SDK 메서드나 반환값 의미를 추측하지 않습니다.

## 1. SDK 확보와 실제 API 확인

다음 기술 검증 작업에서 아래 항목을 확인한 후 최소 변경을 적용합니다. **이번 문서 작성 중에는 변경하지 않았습니다.**

1. `settings.gradle.kts`의 공식 Maven 저장소를 확인합니다. 현재 파일에는 이미 `https://nexus-playground.pleos.ai/repository/maven-releases/`와 `ai.pleos.playground` 그룹 필터가 있어 중복 추가가 필요하지 않습니다.
2. 공식 시작 가이드의 후보 좌표는 `ai.pleos.playground:Vehicle:2.3.1`입니다. 사용하려는 이미지/SDK의 호환성을 확인하고 앱 모듈에 의존성을 추가한 뒤 Sync와 아티팩트 해석을 검증합니다. 현재 `app/build.gradle.kts`에는 Vehicle 의존성이 없습니다.
3. 다운로드된 AAR/External Libraries와 API Reference에서 실제 클래스, 초기화/연결 완료 방식, 콜백, 타입·null 처리·상수·영역, 함수별 `@RequiresPermission`을 확인합니다. 문서에서 세부 시그니처가 보이지 않으면 추측 대신 실제 라이브러리를 기준으로 검토합니다.
4. 도어 조회의 후보 권한은 `pleos.car.permission.CAR_DOORS`입니다. 실제 호출 함수의 요구 권한을 확인한 후 필요한 조회 권한만 선언합니다. `CONTROL_CAR_*`는 선언하지 않습니다. 매니페스트 선언만으로 권한 허용을 가정하지 않습니다.

## 2. 최소 읽기 어댑터 검증

실제 SDK를 확보한 뒤 기존 `VehicleDataSource` 경계에 최소 읽기 어댑터를 연결하는 것만 계획합니다. 현재 `UnavailableVehicleDataSource`를 실제 수신으로 표시하지 않습니다.

1. SDK의 실제 규약에 맞춰 Vehicle 서비스 초기화·연결 준비를 확인합니다.
2. 공식 예제의 `getDoor()`와 `checkDoorPositionCapabilities()` 흐름을 설치된 SDK 시그니처와 대조합니다.
3. 읽기 가능한 접근 수준(`READ` 또는 `READ_WRITE`)과 `DoorArea(DoorValue.ROW_1_LEFT)` 영역 지원을 확인합니다. 미지원/조회 거부/Capability 확인 실패면 값 조회를 성공으로 처리하지 않습니다.
4. 지원되는 경우에만 `getCurrentDoorPosition()`의 실제 비동기 응답을 받습니다. null·예외·타임아웃·미수신을 실제 값과 구분하고, 수신 시각을 기록합니다. 단위나 '열림/닫힘' 의미는 SDK의 공식 정의를 확인한 뒤 변환합니다.
5. 화면에 데이터 출처를 **Pleos 에뮬레이터의 Vehicle SDK**로 표시합니다. 지원 수준, 영역, 원본 값/해석된 값, 최근 수신 시각을 확인할 수 있게 합니다. 실차 계측으로 표현하지 않습니다.
6. 화면 종료 시 리스너/서비스를 정리하고 늦은 콜백을 무시합니다. 재실행 후 다시 연결되는지 확인합니다. 임의 폴링·백그라운드 상주는 넣지 않습니다.

도어가 일반 앱 권한 또는 현재 이미지에서 미지원이면 그 결과를 먼저 기록합니다. 다른 도메인을 시험할 필요가 생기면 공식 API·권한·Capability가 확인된 읽기 항목 1개를 별도로 선정하며, 가상 값으로 목표를 채우지 않습니다.

## 3. 사용자에게 필요한 Android Studio 조작 (다음 작업 시)

현재는 추가 SDK 설치나 소스 수정을 수행할 필요가 없습니다. 다음 기술 검증에서 최소 코드 변경이 준비되면 아래 순서로 확인합니다.

1. Android Studio에서 기존 `NextDrive-Pleos` 프로젝트를 엽니다. 이미 설치한 Platform 34 / Build Tools 35.0.0과 기존 JDK/Gradle 설정을 유지합니다.
2. Vehicle 의존성 변경 후 **Sync Now** 또는 **File → Sync Project with Gradle Files**를 실행합니다. 메뉴 이름은 Android Studio 버전에 따라 다를 수 있습니다.
3. Sync 실패 시 Build/Sync 창의 첫 오류를 확인합니다. 저장소 접근·SDK 버전·인증/권한 문제를 구분하며 임의 저장소·추측 버전으로 바꾸지 않습니다. 기본 SDK/AVD를 재설치하지 않습니다.
4. **External Libraries**에서 Vehicle 라이브러리가 실제로 해석됐는지 확인합니다. 문서상 의존성 한 줄 추가만으로 설치 완료라고 하지 않습니다.
5. **Tools → Device Manager**에서 기존 Pleos Connect v2.1.0 AVD를 실행하고 실행 대상으로 선택합니다. 새 AVD를 만들거나 기존 AVD를 삭제하지 않습니다.
6. 최소 어댑터가 구현된 뒤 **Run app**으로 빌드·설치·시작합니다. 정상적인 권한 안내가 제공되면 필요한 조회 목적을 확인하고 사용자 동의로 처리합니다. 권한이 거부되거나 플랫폼 전용이면 강제 grant·시스템 권한 우회 대신 오류를 기록합니다.
7. 앱의 테스트 화면과 **Logcat**에서 해당 앱 프로세스를 선택해 연결 → Capability → 읽기 응답을 확인합니다. SDK 원본 값·수신 시각·오류 코드가 주요 증거입니다. 계정 토큰·인증 파일·불필요한 차량 식별정보는 공유하지 않습니다.
8. 가능하면 지원되는 에뮬레이터 제어 화면에서 도어 상태를 바꾼 뒤 새 수신 값을 확인합니다. 상태 변경 기능이 없거나 검증하지 못하면 그 항목은 별도 미검증으로 기록합니다.

## 4. 성공·실패 판정 및 기록

| 항목 | 통과 증거 |
| --- | --- |
| SDK 확보 | 실제 해석된 SDK 버전과 아티팩트 확인, Sync 성공 |
| 빌드/실행 | 최소 어댑터 포함 APK 빌드·설치·앱 실행 성공 |
| 일반 앱 접근 | 사용 권한과 실제 호출의 허용/거부 결과 확인. 특권·시스템 앱 전환 없이 시험 |
| 기능 지원 | 읽기 Capability와 대상 영역 지원 응답 |
| 최소 1개 정보 수신 | 실제 조회 콜백의 유효한 값, 영역, 수신 시각 및 SDK 정의에 따른 해석 |
| 출처/실패 처리 | 에뮬레이터 출처 표시, 거부·미지원·null·실패·미수신의 구분 |

빈 화면/기본 화면 실행이나 `onComplete(null)`만으로 차량 값 수신 성공이라고 하지 않습니다. SDK 설치, 권한 허용, Capability 확인, 값 수신을 각각 기록합니다. 다음 작업 결과는 `docs/CODEX_PROGRESS.md`, `PLATFORM_VALIDATION.md`, 필요 시 `SDK_CAPABILITY_MATRIX.md`에 반영하고, 검증된 변경만 GitHub 연결 도구로 게시합니다.

## 공식 근거

- [Pleos 시작 가이드: Maven·Vehicle SDK·도어 조회 예제](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Capability 확인](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)
- [조회 권한 선언](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
