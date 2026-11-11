# 공식 SDK 및 실제 지원 기능 점검표

## DriveCheck·위치·내비 실제 재검증 — 2026-11-11 19:10 +09:00

기존 Vehicle2.3.2/NaviHelper3.0.5/Fused1.1.3/STT·TTS3.3.1을 유지했다. Vehicle20개 조회의12개 수신·8개 미지원, 실제 Fused last-known·선택 fixture 연결, 실제 TTS 완료 callback을 이번에도 확인했다. 일반 앱의 실제 STT·실모델 성공은 보존된 별도 과거 사실이며 이번 유료 모델 요청0이다.

Navi service3.0.4/내비 앱3.3.2-rc5-hotfix.3에서 preflight→목적지 전달→검색 시작→ROUTE_DRIVE→ROUTE_GUIDING→목적지 일치를 수신했다. onRoutingCompleted는30초 내 미수신이라 전체 성공을 만들지 않는다. isRouting=false라도 안내 모드가 활성화되어 있으면 기존 경로를 보호한다. 이번 fixture만 좌표 일치 후 취소·SAFE_GUIDING/inactive 확인. 생산 코드에는 기존 경로 취소가 없다.

SOC100→실제18 callback→반복18 GET→45 callback·사건 한 개/진입·해제·A/B 인계·재시작을 validation Main에서 확인했다. Console HTTP200과 SDK 수신은 별도다. 시험 후 Console100/P/0 및 SDK100/정차 gate 복원도 확인했다. DriveCheck는 정보 품질과 사용자 기준을 표시하며 고장/정상 판정을 하지 않는다.

공개 Vehicle2.3.2 sources에는 연속 실제 가속도·진동·소음 입력을 확보하지 못했다. cruise target은 실제 속도가 아니다. validation의 AAOS CAR_SPEED는 PERMISSION_DENIED라 capability/실수신/주기는 미검증이다. AAOS gear 단발 수신은 연속 운전 입력의 대체가 아니다. 기기 accelerometer 없음, 마이크 입력 장치 inventory는 파형 성공과 다르며 이번 자동 수집0. 위험 점수/음향 고장 감지/새 권한·센서 AI 전송은 없다.

[공식 1차 근거·설치 버전·A~F·433개 검사·정확한 미완료](docs/DRIVE_CHECK_ACCEPTANCE.md). 아래 예전 경로 미전달/AI OFF/수신0은 당시 이력으로 보존한다.

## 최종 통합 재검증 — 2026-11-11 15:46 +09:00

이번 실제 Vehicle20개 읽기에서도12개 RECEIVED/8개 UNSUPPORTED를 확인했습니다. SOC100%/0.0km 및 문·등화·페달 등은 에뮬레이터 SDK 응답이지 실차 고장 판정이 아닙니다. 기존 SDK/권한은 바꾸지 않았습니다.

일반 앱 실제 한국어 final+ended→초안 자동 반영을 사용자가 확인했습니다. Fused1.1.3은 실제 유효 last-known 위치/시각을 수신하고 validation 고유 sidecar에 선택 fixture 연결·재개방을 검증했습니다. confidence를 정확도(m)로 바꾸지 않습니다. Navi3.0.5는 경로 상태 수신까지 실제 확인, 사용자 목적지 전달·경로 시작은 미검증입니다. Pleos TTS는 onDone, 물리 청취는 별도입니다.

Gleo3개 Action/공식 Callback 구성·외부 Intent 로컬 처리 검증은 실제 wake word/음성 응답과 다릅니다. 시스템 음성 관련 앱은 존재하나 신뢰할 응답 수신자·호출 계약·Schema 승인은 확인되지 않아 개인정보 Broadcast를 차단합니다. Gemini 새 요청은 기존 정책을 거치도록 연결했고, 이번 모델/배포0회입니다. [공식 소스 파일·A~G 구분](docs/GLEO_INTEGRATION_ACCEPTANCE.md), [이번281개 검사와 일반 APK](docs/CARITDA_INTEGRATION_REPORT.md)를 따릅니다.

## 최신 공개 SDK 통합 — 2026-11-11 Asia/Seoul

Vehicle2.3.2의 기존3종+공개17종을 실제 에뮬레이터에서 읽었습니다. SOC100%/거리0km/문4개 위치/등화3개/방향지시/브레이크 페달/출력 저하 안내의12개 값 수신, 연료·오일·브레이크액·워셔액·저압기준4개는 UNSUPPORTED입니다. 새 CAR_DOORS/CAR_LIGHTS/CAR_SAFETY는 읽기만 사용하며 PRIVATE 권한은 선언하지 않았습니다. 타이어 저압 기준은 실압/고장이 아닙니다.

STT/TTS3.3.1/Fused1.1.3/Navi3.0.5를 추가했고 Maven .security 전이 의존성 필터 문제를 수정했습니다. Navi 경로 상태 callback과 Pleos TTS onDone을 실제 수신했습니다. 공식 STT request의 실제 한국어 자동 인식·연속3회 재입력을 확인했고 최종 화면은 자동 종료/취소로 정리했습니다. [통합 결과·API 근거](docs/CARITDA_INTEGRATION_REPORT.md), [STT 안정화](docs/STT_STABILITY.md)를 참조하세요. 아래 과거 미호출·보류 기록은 당시 사실입니다.


## 최신 등화 후보 검토 — 2026-11-10

Light/TurnSignal 공개 읽기·Capability/Listener API와 CAR_LIGHTS, Brake 페달/CAR_DRIVE, Safety/CAR_SAFETY 경로를 확인했습니다. 일부 javap는 권한 오류로 중단돼 전체 API 부재를 단정하지 않습니다. 이번 추가 Manifest 권한·SDK 신호 호출·Console 조작은0이며 실제 등화 고장 신호 수신은 미검증입니다. ON/OFF·페달·에뮬레이터 actual state는 물리 전구/LED 정상 판정이 아닙니다. Vehicle Data API의 frontHeadLamp/stopLamp 고장 데이터는 별도 유럽 OAuth 경로이며 한국 차량 내부 SDK로 사용할 수 있다고 가정하지 않습니다.

[공식 근거·가능/미검증 구분·승인 후 최소 계획](docs/LIGHTING_FEASIBILITY.md)을 참조하세요. 기존 SOC/거리 연동·권한은 유지했습니다. 사용자는 실제 Gemini 설명 성공을 확인했으며 아래 과거 ‘모델 미연결’은 당시 기록입니다. 이번 AI 안정화 작업의 추가 모델 호출은0회입니다.


## 최신 SOC 구독·Console PoC — 2026-11-08

Vehicle2.3.2 AAR의 registerEvBatteryStateOfCharge/unregisterEvBatteryStateOfCharge와 EvBatteryStateOfChargeListener.onEvBatteryStateOfChargeUpdated(Integer)/onFailed를 확인했습니다. 기존 CAR_ENERGY로 Capability 확인 후 실제 Console80% GET,18%/45% 변화 callback·원본/사용자 low 사건·해제·재실행 복원을 validation에서 검증했습니다. 같은18%는 SDK GET으로도 반복 수신하고 사건1개를 유지했습니다. 등록 반환만의 성공이 아닙니다.

관측 범위는 사용자 켜기+앱 가시+UXR ALLOWED이며 제한/종료 시 해제·공백입니다. 백그라운드 상시 감시/실차/고장 진단은 미검증·미구현입니다. 다른 Console 신호가 SDK에서 읽힌다고 일반화하지 않습니다. 인증된 AI 모델은 아직 호출하지 않았습니다. [공식 Console/SDK 근거·신호별 결과·실패와 승인 조건](docs/CARITDA_VIRTUAL_DRIVING_AI_POC.md)을 확인하세요. 아래 과거 런타임/실패 기록은 보존합니다.

## 최신 실제 런타임 — 2026-11-07 21:15 +09:00

Pleos Connect v2.1.0/API34 emulator-5554에서 Vehicle SDK 2.3.2 실제 응답을 수신했습니다. 아래 과거 실패/미검증 기록은 당시 상태로 보존합니다.

| 데이터 | 실제 Capability/권한 | 실제 결과 | SQLite |
| --- | --- | --- | --- |
| EV SOC | READ, CAR_ENERGY granted | 100 % | RECEIVED, supported/valid=true, 수신 시각/출처/세션 저장 |
| 누적 주행거리 | READ, CAR_DRIVE granted | 0.0 km | RECEIVED, supported/valid=true, 수신 시각/출처/세션 저장 |
| 연료 잔량 | Capability에서 ERROR_NOT_SUPPORTED_PROPERTY, CAR_ENERGY granted | 미지원, 읽기 미호출 | UNSUPPORTED, raw/unit/receivedAt=null |
| 서비스 연결 | SDK initialize 후 실제 Capability/get 콜백, VEHICLE_BINDING granted | 연결·응답 성공 | 세션별 결과 저장 |

출처 PLEOS_EMULATOR_SDK, hardware=ranchu. 실제 에뮬레이터 서비스의 시뮬레이션 응답이며 실차값/앱 자체 가상값과 구분합니다. 기존 24개 회귀 + 6개 관측 계측 통과. Activity 재시작과 추가 프로세스 force-stop/cold start 후 세션/값/미지원 복원 확인. 실제 권한 거부/타임아웃/끊김 조건은 이번 미실행이며 실차/Lint/Release도 미검증입니다.

세부 증거: [2.3.2 런타임 결과](docs/VEHICLE_SDK_232_RUNTIME.md). 기존 DB 스키마/사용자 기록/Aside 연구는 보존했습니다.

# 공식 SDK 및 실제 지원 기능 점검표

## 최신 상태 — Vehicle SDK 2.3.2 확보 및 포함 빌드 성공

사용자는 2.3.1 POM HTTP 404/2.3.2 POM 정상과 Sync/APK 성공을 확인했습니다. Codex도 실제 Vehicle 2.3.2, vehicle-aidl 1.1.1, vehicle-data 1.1.11 AAR 및 sources를 확인하고 수정본 앱/계측 APK 빌드를 성공했습니다(exit 0, 47초). 아래 다운로드 실패는 과거 사실입니다.

| 데이터 | 실제 AAR 공개 API 확인 | 권한·단위 | 런타임 |
| --- | --- | --- | --- |
| EV SOC | checkEvBatteryStateOfChargeCapabilities / getEvBatteryStateOfCharge | CAR_ENERGY, Int? % | AVD 미연결, Capability/값 미검증 |
| 누적 주행거리 | checkCurrentDistanceDrivenCapabilities / getCurrentDistanceDriven | CAR_DRIVE, Float? km | AVD 미연결, Capability/값 미검증 |
| 연료 잔량 | checkFuelPercentCapabilities / getFuelPercent | CAR_ENERGY, Int? % | AVD 미연결, Capability/값 미검증 |

SDK 초기화·Capability 후 읽기·관측 저장·화면 연결 코드는 로컬에 구현하고 컴파일했습니다. 실제 initialize/서비스 응답/권한 허용/데이터 수신/저장은 실행하지 못했습니다. 기존 AVD 시작 시 사용자 .android 잠금 파일 쓰기 error 5로 중단하여 일반 Android Studio 부팅을 요청했습니다. 실제 수신 0개이며 가상값으로 대체하지 않았습니다. 런타임 미검증 어댑터/UI/계측/Manifest는 로컬만 보존하고 빌드 검증한 Gradle 설정과 문서만 게시합니다.

자세한 현재 상태: [2.3.2 런타임 검증 안내](docs/VEHICLE_SDK_232_RUNTIME.md). 기존 DB 스키마/기록, Aside UI 조사와 이전 기록은 보존했습니다.

# 공식 SDK 및 실제 지원 기능 점검표

## SDK 다운로드 재검증 — 2026-11-07 20:31 +09:00

실제 앱 app/build.gradle.kts에 Vehicle 2.3.1 의존성을 추가하고 온라인 assembleDebug를 실행했습니다. debugRuntimeClasspath 의존성 해결이 공식 Maven GET의 `Permission denied: getsockopt`로 실패했습니다(exit 1, 41초). DNS 해석은 성공했으며 SDK AAR은 캐시에 없습니다. HTTP 응답을 받지 못해 인증/좌표 오류 여부는 미판정입니다. 현재 실행 정책에서 네트워크 승인 요청도 허용되지 않아 확대 권한 실행이나 우회는 하지 않았습니다.

SDK 설치/포함 빌드/초기화/앱 서비스 연결/Capability/실제 차량값은 여전히 미검증·미수신입니다. 기존 서비스 2.3.22 설치 확인과 구분합니다. 로컬 Gradle 의존성은 사용자가 파일 편집 없이 일반 Android Studio에서 Sync할 수 있게 보존했으며 미검증이므로 원격 게시에서 제외했습니다. 기존 APK·SQLite는 보존했습니다. 이번 후속 런타임/회귀 검사는 수행하지 않았습니다.

[현재 다운로드 결과와 다음 조작](docs/SDK_DOWNLOAD_RETRY.md). 아래 과거 기록을 삭제하지 않습니다.

# 공식 SDK 및 실제 지원 기능 점검표

## v0.2 검증 — 2026-11-07 (아래 과거 기록 보존)

실제 SDK 값 수신은 미완료입니다. 공식 샘플 README의 2.3.2와 Gradle 버전 파일의 2.3.1 불일치를 확인했습니다. 2.3.1을 첫 다운로드 후보로 선택했으나 두 버전 POM 요청 모두 연결 실패, Gradle 2.3.1 resolve도 공식 Nexus에서 Permission denied: getsockopt로 실패했습니다. SDK 바이너리 호환 버전 확정은 보류합니다.

| 대상 | 실제 확인 | Capability/값 수신 |
| --- | --- | --- |
| Vehicle SDK | AAR 미확보, 초기화·Sync·SDK 컴파일 미수행 | 미수행 |
| 에뮬레이터 서비스 | dumpsys package로 ai.pleos.playground.service.vehicle 2.3.22 설치 확인. 앱 bind/initialize 성공과 별개 | 미수행 |
| EV SOC | 후보 조회 권한 CAR_ENERGY. API별 권한·단위는 AAR 확인 필요 | 지원 미확인 / 미수신 |
| 누적 주행거리 | 기존 문서의 CAR_DRIVE 매핑을 확정하지 않음. API별 권한·단위 확인 필요 | 지원 미확인 / 미수신 |
| 연료 잔량 | 후보 CAR_ENERGY. 리터/% 또는 범위 환산 규칙 미확정 | 지원 미확인 / 미수신 |
| 도어 | 공식 typed 예제의 CAR_DOORS, checkDoorPositionCapabilities/getCurrentDoorPosition 흐름 확인 | 지원 미확인 / 미수신 |
| 권한 정의 | 현재 AVD Vehicle Service의 CAR_ENERGY/CAR_DRIVE/CAR_DOORS가 prot=normal인 것을 dumpsys로 확인. 앱에는 미선언·미요청이며 API 사용 허용을 의미하지 않음 | 실제 앱 권한 판정 미수행 |
| 공개 AAOS UX 제한 | v0.1 연동 유지. 정차 응답 및 기존 입력 잠금 회귀 통과 | Vehicle SDK 센서값 수신과 별개 |

차량 데이터 화면과 분리 SQLite 관측 저장소를 추가했습니다. 현 앱은 SDK_UNAVAILABLE 진단 상태만 숫자 없이 저장합니다. 실패 상태 저장·재실행 복원 등 5개 추가 계측 검사와 기존 24개 회귀 검사를 통과했습니다. TEST_ONLY 출처의 숫자는 독립 시험 DB 저장 형식 검증이며 SDK 실제 수신이 아닙니다. 실제 권한 거부·타임아웃·미지원 SDK 응답을 실행했다고 주장하지 않습니다.

자세한 근거·일반 Android Studio 다운로드 절차는 [v0.2 검증 기록](docs/CARITDA_V02_VALIDATION.md)을 참고하세요.

# 공식 SDK 및 실제 지원 기능 점검표

## 2026-11-07 실제 연동 시도 결과

- 공식 시작 가이드의 Maven URL, `Vehicle:2.3.1`, 읽기 권한 `pleos.car.permission.CAR_DOORS`, `getDoor()`/`getCurrentDoorPosition()` 및 도어 Capability 예제를 재확인했습니다.
- 최소 수신 시험 후보는 **앞 왼쪽 도어 위치**입니다. `DoorArea(DoorValue.ROW_1_LEFT)`의 지원 영역과 `READ`/`READ_WRITE` 접근을 확인한 뒤 조회하는 흐름입니다. 도어 제어/set API는 대상이 아닙니다.
- Vehicle 2.3.1 POM 다운로드 요청은 이 세션에서 연결 실패했습니다. SDK AAR·실제 클래스·API별 어노테이션·빌드·권한 허용·Capability·값 수신은 여전히 검증하지 못했습니다. 일반 앱에서 실제로 접근 가능하다고 확정하지 않습니다.
- JBR 경로를 수정한 뒤에도 컴파일 SDK 설치가 원격 manifest 수신 실패로 종료됐고 ADB 서버 시작도 권한 오류로 실패했습니다. 앱 매니페스트/Gradle/SDK 어댑터에 미검증 변경을 추가하지 않았으며 실제 차량 값으로 표시한 데이터도 없습니다.
- API Reference의 클래스 탐색 경로(`Vehicle`, `DoorArea`, `AccessType`)는 확인했지만 조회한 상세 페이지에는 함수 시그니처 본문이 노출되지 않았습니다. 추측으로 인자를 보충하지 않고 SDK 확보 후 실제 AAR와 함수별 권한을 확인합니다.

현재 Vehicle SDK는 다운로드·설치하지 못했습니다. 아래 '공식 문서 확인'과 '실제 런타임 지원'은 다른 검증입니다. 현재 테스트 앱에는 공식 SDK 호출이나 차량 권한을 추가하지 않았습니다.

| 대상 | 공식 문서 확인 | 설치 / 컴파일 | 실제 Capability / 수신 | 현재 화면 |
| --- | --- | --- | --- | --- |
| Vehicle SDK | 시작 가이드의 `ai.pleos.playground:Vehicle:2.3.1`, 공식 Maven 저장소 확인 | POM 접근 실패. AAR·시그니처·컴파일 미확인 | 서비스 연결 미확인 | SDK 미연결 |
| 앞 왼쪽 도어 | 시작 가이드의 `getDoor()` / `getCurrentDoorPosition()`, Capability 가이드의 `checkDoorPositionCapabilities()` 확인. 후보 조회 권한 `CAR_DOORS` | 미검증 | 읽기 접근 수준, 앞 왼쪽 영역 지원, 값·단위·상수 모두 미검증 | 지원 미확인 / 미수신 |
| 연료 잔량 | 대표 도메인 권한 매핑에서 Fuel → `CAR_ENERGY` 확인 | 구체 조회/Capability 메서드·단위·지원 차종 미검증 | 미검증 | 지원 미확인 / 미수신 |
| 누적 주행거리 | 대표 도메인 권한 매핑에서 Odometer → `CAR_DRIVE` 확인 | 구체 조회/Capability 메서드·단위 미검증 | 미검증 | 지원 미확인 / 미수신 |
| 주행 규제 | 공식 가이드의 `CarUxRestrictionsManager` / `CarUxRestrictions` 확인 | 연동 미구현 | 지역·카테고리별 규제 수신 미검증 | 정차 테스트 안내, DO 미선언 |

## 실제 검증 시 기록할 증거

1. 이미지/SDK 버전, 실행 대상이 Pleos Emulator인지 실차인지, 아티팩트 해석·빌드 결과.
2. API Reference와 설치된 AAR에서 확인한 정확한 패키지·메서드·콜백 시그니처 및 함수별 `@RequiresPermission`.
3. 앱 매니페스트의 최소 조회 권한, 실제 접근 허용/거부 결과. 문서상의 권한 이름 확인만으로 허용을 가정하지 않음.
4. 항목별 Capability의 읽기 접근 수준 및 지원 영역. `READ`/`READ_WRITE`이고 해당 영역이 지원될 때만 읽음. 제어/set API는 사용하지 않음.
5. 읽은 원본 값·단위·영역·수신 시각과 화면 변환 규칙. 예를 들어 리터를 미확인 탱크 용량으로 퍼센트 변환하지 않음.
6. 미지원/권한 거부/연결 실패/타임아웃/미수신/오래된 값/정상 값을 구분한 실제 결과.
7. 생명주기 전환 시 초기화·연결·자원 해제, 늦게 도착한 콜백 처리, 화면 크기 변경 후 복원.

차량 값 수신 전에는 '경고 없음', '도어 닫힘', 연료/주행거리 숫자를 임의로 표시하지 않습니다. Pleos 에뮬레이터 수신 성공을 실차 검증으로 표현하지 않습니다.

## 공식 근거

- [시작 가이드 / Vehicle SDK 의존성과 도어 조회](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Capability 확인](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)
- [Vehicle SDK 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [Pleos Emulator 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
