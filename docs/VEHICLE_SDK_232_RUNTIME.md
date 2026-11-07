# Vehicle 2.3.2 연동 구현 및 검증 상태

2026-11-07, Asia/Seoul. **SDK 확보와 SDK 포함 APK 빌드는 성공. 실제 서비스 응답·차량값 수신은 아직 미검증입니다.** 과거 다운로드 실패 기록은 삭제하지 않습니다.

## 확보와 API 확인

사용자 보고: 2.3.1 POM은 HTTP 404, 2.3.2 POM 정상, Android Studio Sync/Build APK(s) 성공. 이번 Codex는 로컬 설정과 Vehicle-2.3.2.aar, vehicle-aidl-1.1.1.aar, vehicle-data-1.1.11.aar, 공식 sources JAR을 실제 확인했습니다. 2.3.1로 되돌리지 않습니다.

공식 sources JAR의 공개 인터페이스와 배포 AAR을 대조한 호출:

| 항목 | Capability / 읽기 | 원본 타입·단위 | 요구 권한 |
| --- | --- | --- | --- |
| EV SOC | checkEvBatteryStateOfChargeCapabilities / getEvBatteryStateOfCharge | Int?, % | CAR_ENERGY |
| 누적 주행거리 | checkCurrentDistanceDrivenCapabilities / getCurrentDistanceDriven | Float?, km | CAR_DRIVE |
| 연료 잔량 | checkFuelPercentCapabilities / getFuelPercent | Int?, % | CAR_ENERGY |

초기화/해제는 공개 `Vehicle(context)`, `initialize()`, `release()`만 사용합니다. initialize 반환만으로 서비스 연결 성공을 표시하지 않으며 Capability 콜백 응답을 근거로 연결 응답을 표시하도록 구현했습니다. 제어/set/비공개 API 호출은 없습니다.

배포 AAR에서 VehicleException은 난독화되어 공식 소스의 이름으로 컴파일되지 않습니다. 반사 호출 없이 Exception과 공개 ErrorCode 이름에 해당하는 메시지로만 실패를 분류합니다. 공개 ErrorCode는 vehicle-data 모듈에 있고 Vehicle의 runtime 의존성이므로, `compileOnly("ai.pleos.playground:vehicle-data:1.1.11") { isTransitive = false }`로 컴파일에만 노출합니다. 런타임 배포는 Vehicle의 기존 전이 의존성을 유지합니다. vehicle-data를 implementation으로 승격했을 때는 테스트 APK의 불필요한 전이 그래프가 달라져 offline 해석이 실패했으며 이 변경은 사용하지 않습니다.

## 로컬에 구현한 동작 (런타임 미검증)

- 기존 홈의 차량 연결 화면에 `차량 데이터 읽고 저장하기` 버튼을 연결했습니다. UXR 입력 허용 시에만 실행하며 UI 전면 개편은 하지 않았습니다.
- PleosReadClient는 한 번의 요청 세션에서 세 Capability를 각각 확인하고 READ/READ_WRITE인 항목만 읽습니다. null·비정상 수치·미지원·권한·연결 오류·15초 타임아웃을 구분합니다. 확인되지 않은 값을 생성하지 않습니다.
- ObservationStore에 원본 문자열/단위/콜백 수신 시각/출처/지원/유효성/세션/Capability 접근 정보를 저장하도록 연결했습니다. 실패에는 숫자·수신 시각을 넣지 않습니다. 수동 문제 DB와 기존 관측 스키마는 변경하지 않았습니다.
- 에뮬레이터 출처는 공개 Build.HARDWARE의 ranchu/goldfish로 구분하고 실제 응답 상세에 hardware를 남깁니다. 앱 내부 TEST_ONLY 관측과 에뮬레이터 SDK 응답은 구분합니다. 아직 이 구분의 런타임 결과는 없습니다.
- 중복 요청을 막고 onStop에서 SDK release/타이머 정리/세대 번호 변경으로 늦은 콜백의 저장을 막습니다. 저장 실패를 수신 성공으로 표시하지 않습니다.
- 계측에 기존 24개 시나리오와 관측 저장 검사, 세 SDK 결과·최소 하나의 실제값·Activity 재시작 복원 검사를 준비했습니다. 실행하지 않은 검사를 통과로 표시하지 않습니다.

## 실제 검사

- 최종 `:app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL in 47s**, exit 0. 기존 JBR21.0.8/Gradle8.11.1/Platform34/Build-Tools35.0.0, offline 캐시 사용. 새로운 SDK 다운로드 재시도는 불필요했습니다.
- aapt permissions 확인: CAR_ENERGY, CAR_DRIVE 및 SDK 병합 매니페스트의 VEHICLE_BINDING만 포함. 제어 권한 없음.
- 앱 APK SHA256: `7a6412cc0cfc4b5aec24aa903540b485b345c2f90d8e1282dfdbaccf55056dd1`.
- 빌드 로그 `NextDrive-Pleos/app/build/validation/sdk232-build.txt`, APK `app/build/outputs/apk/debug/app-debug.apk`, 계측 APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`는 로컬에 생성했습니다. 이들은 GitHub 게시 대상이 아닙니다.
- **런타임 미수행:** 시작 시 adb devices가 비어 있었습니다. 기존 Pleos_Connect_v2.1.0 AVD를 시작했으나 사용자 .android 폴더의 emu-last-feature-flags.protobuf.lock 쓰기에서 error 5(접근 거부)가 반복되어 이번에 시작한 emulator/qemu 프로세스만 중단했습니다. 기존 AVD/SQLite/Android Studio를 초기화하지 않았습니다. 에뮬레이터 로그는 .tools에 보존합니다.
- 사용자에게 일반 Android Studio에서 기존 AVD를 부팅해 달라고 요청했습니다. 연결 전에는 설치/실행/Capability/실제 수신/DB 저장/이번 회귀 검사를 수행할 수 없습니다. 과거 24+5 통과는 과거 사실이며 새 SDK 연동 후 회귀 통과를 뜻하지 않습니다. Lint/Release/실차도 이번 미검증입니다.

## 남은 사용자 조작과 검증 순서

1. 일반 Android Studio에서 Tools > Device Manager를 엽니다.
2. 기존 Pleos_Connect_v2.1.0의 ▶를 눌러 정상 부팅합니다. Wipe Data/새 AVD/재설치를 하지 마세요.
3. 부팅 완료를 알려주시면 Codex에서 ADB 연결 → 기존 앱 교체 설치 → 기존 24개 시나리오와 실제 SDK 계측 → 결과/DB 복원 확인을 이어갈 수 있습니다. 사용자 파일 편집은 필요 없습니다.
4. 직접 화면을 확인하려면 기존 app을 Run하고 플랫폼 입력이 허용된 상태에서 `차량 데이터 연결 확인` → `차량 데이터 읽고 저장하기`를 누릅니다. 항목별 결과·시각·출처를 확인합니다. SDK 2.3.2를 유지합니다.

## 게시 범위

빌드로 검증한 Gradle 의존성 설정과 지침·결과 문서만 우선 게시합니다. 런타임 미검증 PleosReadClient, MainActivity, Manifest, 계측 수정은 사용자 PC 로컬에 보존하고 실제 에뮬레이터 검사 전에는 게시에서 제외합니다. 즉 GitHub 앱 화면에는 아직 새 읽기 버튼 연결이 반영되지 않습니다. Aside 연구와 기존 소스/이력을 보존합니다. SDK 실제 수신 성공 시 후속 커밋으로 게시합니다.

공식 근거: [Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission). 구체적인 타입·단위·요구 권한은 다운로드한 2.3.2 sources JAR과 AAR에서 확인했습니다.
