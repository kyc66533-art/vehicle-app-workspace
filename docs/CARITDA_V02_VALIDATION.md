# CARITDA v0.2 — 실제 SDK 수신 미완료 / 검증된 기반 구현

2026-11-07, Asia/Seoul. v0.2의 최우선 목표인 실제 Pleos Vehicle SDK 값 수신은 **미완료**입니다. 이번 변경을 SDK 연동 완료 버전으로 표현하지 않습니다.

## SDK 확보 결과

공식 샘플 main 기준 README는 Vehicle 2.3.2 및 Pleos v2.1/Vehicle Service 2.3.22 실행을 설명하지만 실제 `gradle/libs.versions.toml`의 `vehicleSdk`는 2.3.1입니다. 샘플의 `VehicleClient.kt`에서 `Vehicle(context.applicationContext)`, `initialize()`, `getDoor()`, 비동기 Capability/읽기, `release()` 타입 기반 흐름을 확인했습니다. 실제 바이너리와 API별 어노테이션은 확보하지 못했으므로 이를 현재 프로젝트에서 검증한 것으로 표현하지 않습니다.

- 우선 다운로드 검증 후보: `ai.pleos.playground:Vehicle:2.3.1`. 시작 가이드와 샘플의 실제 Gradle 설정을 따릅니다. README의 2.3.2와 비교했으나 호환 버전 최종 확정은 보류합니다.
- 2.3.1/2.3.2 공식 POM 직접 요청: 모두 원격 서버 연결 실패.
- 프로젝트 설정을 영구 변경하지 않는 별도 Gradle init probe의 2.3.1 resolve: exit 1. 공식 Nexus 및 Google/Maven Central 모두 `Permission denied: getsockopt`.
- 첫 probe 실행은 init 파일 상대 경로, 다음은 PowerShell `-D` 인자 해석 문제로 실패했습니다. 절대 경로와 인자 인용을 수정한 최종 probe가 실제 네트워크 실패를 확인했습니다. 같은 다운로드를 반복하거나 제한을 우회하지 않습니다.
- 로컬 캐시에 SDK 아티팩트가 없습니다. SDK 다운로드/Sync/컴파일/초기화/서비스/Capability/읽기 단계는 모두 미완료입니다. 기존 앱의 Gradle 빌드 성공과 구분합니다.
- SDK 의존성이나 추측 어댑터를 기본 앱에 넣어 정상 v0.1 빌드를 막지 않았습니다. 미확보 SDK를 반사 호출·비공개 API·시스템 서명으로 대체하지 않습니다.

## 실제 구현한 부분

기존 홈에 `차량 데이터 연결 확인` 버튼과 최소 화면을 추가했습니다. 현 빌드가 SDK 미설치임을 명확히 표시하고 SOC/누적 주행거리/연료/도어를 모두 지원 미확인·미수신으로 안내합니다. `현재 미연결 상태 기록하기`는 센서 조회 버튼이 아니며 SDK_UNAVAILABLE 상태만 기록합니다. 재실행 후 기록을 표시합니다. 실제 SDK 예외/Capability 판정 로직은 아직 없습니다.

`ObservationStore`는 별도 append-only SQLite `caritda-observations.db`를 사용합니다. 기존 `caritda_v01.db`의 스키마/수동 기록은 그대로 둡니다. 기존 테이블 마이그레이션이 불필요하도록 분리했으며 향후 버전 변경 시 삭제 fallback 없이 오류를 보고합니다.

필드: 종류, 원본 문자열, 단위, 수신 시각, 기록 시각, 출처, nullable 지원 여부, 유효성, 세션 ID, 상태, 상세 설명, 미래 운전자 ID(nullable). 현재 A/B를 신규 영구 운전자 ID로 간주하지 않습니다. 미연결 상태에는 raw/unit/receivedAt이 null이고 valid=false입니다. 상태 기록 시각을 수신 시각으로 표시하지 않습니다.

출처는 PLEOS_EMULATOR_SDK / PLEOS_VEHICLE_SDK / TEST_ONLY / NONE으로 분리합니다. 현 실행의 출처는 NONE입니다. 시험용 숫자는 독립 계측 DB의 TEST_ONLY로만 사용하며 앱 관측 기록이나 SDK 수신 증거가 아닙니다. 지원되지 않음·권한 거부·연결 실패·타임아웃·유효하지 않음 상태 저장 형식을 준비했으나 실제 SDK 실패 경로를 실행한 것은 아닙니다.

기존 수동 문제 기록을 자동 기록으로 변경하지 않았습니다. 자동 감지는 별도 다음 작업입니다. 향후 검증된 관측 → 기준 정책(제조사/사용자/통계 구분) → 중복 키 → 자동 이벤트 → 표시 순서로 구현하고 관측 ID·정책 버전·출처를 연결해야 합니다. SOC 20% 예시는 사용자 편의 기준이며 고장/안전 판정이 아닙니다. ICE/HEV/EV를 공통 지원 목표로 유지합니다.

## 일반 Android Studio에서 필요한 다음 조작

1. 기존 NextDrive-Pleos 프로젝트를 열고 현재 `app`을 Pleos v2.1.0 AVD에 Run 합니다. 재설치/초기화/데이터 지우기는 하지 마세요.
2. 실제 SDK 다운로드를 확인하려면 `app/build.gradle.kts` 마지막에 아래 블록을 **임시로** 추가합니다. 기존 내용은 보존합니다. 이미 dependencies가 있으면 같은 블록에 한 줄만 추가합니다.

```kotlin
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.1")
}
```

3. `File > Sync Project with Gradle Files`를 선택합니다. Gradle 창에서 Offline Mode가 켜져 있다면 이번 다운로드 시에는 해제합니다. SDK/AVD/Android Studio를 재설치하거나 전역 보안 정책을 바꾸지 않습니다.
4. Sync 실패 시 Build/Sync 창의 첫 `Could not resolve` 원인과 공식 Nexus HTTP 오류를 전달해 주세요. 인증·프로젝트 사용 승인이 요구되면 공식 Pleos 개발자 절차를 따라야 하며 접근을 우회하지 않습니다. 임시로 추가한 dependency 블록만 되돌리면 기존 검증 앱으로 복귀할 수 있습니다.
5. Sync 성공 시 External Libraries에서 Vehicle 2.3.1이 실제 내려왔는지 확인하고, Build > Build App Bundle(s) / APK(s) > Build APK(s)를 실행합니다. SDK AAR의 메타데이터와 요구 Android API에 따라 호환성을 판정해야 하므로 Platform 35 등을 지금 임의 설치하지 않습니다.
6. 다운로드 성공 사실과 Sync/Build 결과를 알려주시면 실제 AAR의 SOC/주행거리/연료 Capability·단위·권한을 확인하여 typed adapter를 연결합니다. 현재 APK에 의존성을 추가하는 것만으로 값이 표시되지는 않습니다.

## 후속 우선순위

실제 SDK 아티팩트 확보 → AAR의 공식 read API/권한 확인 → SOC/누적 주행거리/연료별 Capability → 허용 항목 읽기 → 검증된 관측 저장/복원. 도어는 연결 진단 대안이며 핵심 상태 데이터 수신과 별도 보고합니다. 그 뒤 수동 문제에 관측 ID 연결과 중복 방지를 검토합니다. 운전자 관리 전면 구현·Aside 기반 UI 개편·AI·정비소 전송은 이번에 구현하지 않았습니다.

## 공식 확인 출처

- [시작 가이드](https://document.pleos.ai/docs/pleos-only/getting-started)
- [SDK 정책](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/): Capability 확인과 권한 승인 범위 준수 필요.
- [공식 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)
- [실제 버전 파일](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/main/gradle/libs.versions.toml)
- [typed Door 참고](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/main/app/src/main/java/ai/pleos/playground/sample/vehicle/vehicle/VehicleClient.kt)
