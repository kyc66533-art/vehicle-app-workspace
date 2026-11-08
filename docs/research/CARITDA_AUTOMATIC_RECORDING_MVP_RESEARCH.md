# CARITDA — 자동 차량 상태 기록 MVP·경고등 통역사·스마트폰 알림 실현 가능성

확인일: **2026-11-08, Asia/Seoul**  
조사 시작 main: **bb3af514994714b84eeffbe97f82e145be092d4d**  
게시 직전 재확인: **동일 main과 기존 21개 기준 파일 원문 일치, 새 파일만 추가**  
게시 파일: **docs/research/CARITDA_AUTOMATIC_RECORDING_MVP_RESEARCH.md**

## 0. 제품 결정 요약

**1차 제출 MVP는 ‘앱을 열어 지원 차량값을 조회하면 상태를 자동 기록하고, 사람이 느낀 증상과 연결하는 앱’으로 정의한다. ‘차량 고장을 상시 자동 진단하는 앱’으로 정의하지 않는다.**

최초 자동 카드 후보는 아래 두 종류다. 모두 **후속 구현 제안**이며 현행 앱에서 이미 생성되는 기능이 아니다.

1. **차량 상태 스냅샷:** 실제 수신 검증된 SOC·누적거리의 값/수신시각/출처/확인 불가 상태를 한 카드에 묶는다. 정상 값도 기록할 수 있으나 고장 문제로 생성하지 않는다. 같은 차량 범위의 요약 카드 한 개를 갱신하고 원관측·의미 있는 변화 이력은 별도 보존한다.
2. **사용자 설정 낮은 충전량 안내:** 실제 수신 SOC가 사용자가 정한 조건을 만족할 때만 별도 편의 카드 생성. 예를 들어 20% 미만은 **사용자 기준 예시**이지 배터리 고장·OEM 경고 기준이 아니다.

권고 범위:
- **필수:** 실제 SDK 수신→원본 보존→로컬 규칙→자동 정보 카드→사용자 증상/점검 이력 연결, 검증 앱 TEST 식별, 중복·오류·관측 공백 처리.
- **보조:** 공식 자료를 출처로 보여주는 ‘직접 선택한 경고등 안내’. 자동 경고 수신과 명확히 구분한다.
- **제외/후순위:** 검증 안 된 자동 고장 감지·자동 해결, 특권 경고/압력 API, DTC 진단, 상시 마이크/AI 소음 진단, 운전 습관 점수, 무중단 백그라운드 감시, 휴대폰 푸시 완제품.

휴대폰 푸시는 기술적으로 조건부 가능하지만 수신 앱·신뢰 서버·기기 연계·동의·보안·실폰 검증이 필요하다. **11월15일 전에 핵심 데모를 희생하며 붙이지 않는다.**

### 증거 단계

- **확인 사실:** 고정 main 코드/최신 개발 기록 또는 공식 문서/샘플에 명시된 내용.
- **설계 판단:** 이 보고서의 카드·정책·UX·우선순위·인수기준.
- **미검증 가정:** 후속 자동 카드 실제 실행, 저 SOC 변화를 실제 받는 시험, listener 지속/재연결, 폰 연동, 실제 차량/일반 사용자 효과.

이번에는 연구만 수행했다. 앱 소스·DB·설정·기존 연구는 수정하지 않았으며 빌드/설치/SDK 호출/콘솔 조작/폰 알림/사용자 시험을 실행하지 않았다. 개발 기록의 과거 검증과 이번 조사 결과를 구분한다. CARITDA는 상표 검증 전 가칭이다.

---

## 1. 최신 main과 현재 구현

필수 문서 6개와 차량 관측·문제/이벤트·수정삭제·validation 구성·MainActivity·안전 게이트를 읽었다. 저장소 출처 [R1]–[R16]은 조사 시작 커밋에 고정했다.

### 1.1 최신 보고와 실제 기능의 경계

- CODEX_PROGRESS의 최신 절은 **2026-11-08 15:11 KST** 작업 보고다. NAV 신규29+기존145=**174개 자동 assertion** 통과 및 일반 앱 데이터 보존 교체 설치를 보고한다. NAV28 기준 전체나 사람/실차 시험 전체가 통과했다는 뜻은 아니다. [R1] [R2]
- validation에서 SOC100%, odometer0.0km READ/RECEIVED, 연료 UNSUPPORTED·값 없음이 보고됐다. 운영 앱의 과거15%/1000000.0km와 합쳐 회복·주행 변화 증거를 만들지 않았다. 이번 조사에서도 새 현재값을 조회하지 않았다.
- 홈·검색·필터·수정·논리 삭제·네 수동 상태·초안은 구현돼 있다. 자동 카드/자동 회복/Gleo/휴지통 복원은 미구현이다. AUTOMATION_FOUNDATION은 **설계 문서**이지 구현 완료 증거가 아니다. [R2] [R4]
- 현재 readOnce는 SOC·거리·연료의 Capability→get을 한 차례 수행한다. 15초 Handler는 timeout이다. MainActivity의 1초 freshnessTick은 **기존 값의 오래됨 표시 갱신**이지 SDK polling이 아니다. onStop은 pleos.stop/release로 관측을 끝낸다. [R7] [R15]

### 1.2 데이터·권한 보존

- 현재 issues DB v3와 별도 append-only observations DB를 유지한다. 관측 DB 원본을 사용자 편집으로 변경하지 않는다.
- revision은 EDITED와 RECURRENCE에서 모두 증가한다. 수동 STATUS는 revision을 올리지 않는다. 재발 횟수는 RECURRENCE로 세며 읽음은 당시 revision/운전자와 연결한다.
- 수정/삭제는 원 작성자 영구 ID, 상태 변경은 확정 운전자와 기존 서비스 정책을 따른다. 자동 시스템 관측을 가짜 Driver A/B로 기록하지 않는다.
- 삭제는 deleted_at/deleted_by_id를 사용하는 논리 삭제다. 원문/event/백업은 남고 복원/영구 파기 UI는 없다. 새 카드 정책을 기존 기록의 강제 삭제 제한이나 무단 일괄 정리로 소급하지 않는다. [R3] [R9] [R10] [R11] [R12]
- 현행 Manifest는 CAR_ENERGY/CAR_DRIVE와 DO 미선언 상태다. UXR UNKNOWN/RESTRICTED에서 입력을 우회하지 않는다. requiresDO=false와 BASELINE은 기어 P 인증이 아니다. [R14] [R16]

---

## 2. 공모전 적합성과 제출 조건

### 2.1 공식 확인 사실

공식 심사 기준은 **차량특화성·창의성·기술 활용성·사업성**이며 1차 세부 배점은 비공개다. 개발 산출물은 선택 가산점이다. 2차 PT 기준은 공식 페이지에서 추후 안내로 남아 있으므로 임의 배점표를 만들지 않는다. [C1]

- 접수 마감 **2026-11-15 KST**, 1차 결과11/23, 최종 PT12/3~4, 최종 선정12/18. 공식 일정에서 마감 시각까지 확인된 것은 아니므로 23:59를 단정하지 않는다. [C2]
- 접수양식(기획서)·참가자별 동의/서명이 필수다. 시연 영상은 목업/프로토타입/화면 녹화도 인정하며 GitHub/개발 산출물은 선택이다.
- GitHub 산출물을 내는 경우 소스+실행 가능한 **release APK**, apk/ 또는 큰 파일의 GitHub Release, 실행/환경/위치 README 안내가 요구된다. 현재 debug/validation 빌드 성공은 release 제출 준비 완료가 아니다.
- 비공개 저장소 제출은 가능하지만 공식 심사 계정 접근 확인이 필요하다. 이번 연구에서 collaborator 초대·공개 전환·APK 게시·메일 제출은 하지 않는다. [C3]
- 2차 PT 전 실제 동작 앱을 완성하고 1차 산출물은 그때까지 고도화할 수 있다. 휴대폰 푸시를 1차 필수 기능으로 오해하지 않는다.

### 2.2 범위 판단

| 항목 | 적합한 시연 포인트 | 범위 이탈/과장 위험 |
|---|---|---|
| 차량특화성 | 차량 내 실제 SOC/거리와 증상 당시 맥락의 연결 | 단순 모바일 메모장 또는 일반 배터리 위젯으로만 보이는 것 |
| 창의성 | 차량값·사람 증상·점검·재발을 같은 사건 이력에 연결 | 임계값 알림 자체를 혁신적 고장 진단이라고 주장 |
| 기술 활용성 | 실제 Pleos typed API·Capability·미지원·원본 보존을 보여줌 | 문서에만 있는 API/fixture를 실제 차량 수신처럼 설명 |
| 사업성 | 1인 자가 관리에서 공유 차량 인계로 확장할 구조 | 초기부터 계정/서버/운전점수/폰까지 벌여 검증 실패 |

핵심 스토리는 **‘기계가 아는 값은 자동 기록, 사람이 아는 증상은 짧게 보완, 다음 점검 때 함께 확인’**이다. 미래의 고장 자동 감지는 접근/의미 검증 후 확장 계획으로 구분한다.

---

## 3. SDK 신호 지원·접근 표 A/B/C/D

### 3.1 분류 기준

- **A:** 현행 CARITDA 개발 기록에서 해당 **값의 실수신** 검증이 보고됨. 여기서는 에뮬레이터 범위이며 실차 인증 아님.
- **B:** 공식 공개 API가 있지만 현행 앱의 값/구독/area 실행 검증 필요.
- **C:** 권한/공식 Sample PublicApiPolicy의 공개 카탈로그 제외 등 사용 제한 근거.
- **D:** 현재 확보한 공개 근거로 지원/보장을 확인 못 함. 전면적 API 부재를 뜻하지 않음.

권한은 pleos.car.permission. 접두사를 생략한다. 데이터 경로는 모두 **차량 내 Vehicle SDK**이며 별도 REST API는 §3.5다. getter/capability는 onComplete/onFailed 비동기이며 시간·실패 공통 조건은 §3.3에 적용한다. 공개 읽기 권한의 normal/Manifest 선언과 실제 Service/Capability 허용은 별개다. 현행 앱에 CAR_DOORS/CAR_WINDOWS는 없으며 후속 실험 시 실제 typed 함수의 최소 권한부터 검증한다. CONTROL_* 제어 권한을 복사하지 않는다. [S10]

| 대상 | 분류 | 실제 method / Capability | Listener·권한·area | 값/단위·시간 및 해석 |
|---|---|---|---|---|
| 1. EV SOC | **A get / B listener** | EvBattery.getEvBatteryStateOfCharge / checkEvBatteryStateOfChargeCapabilities | register/unregisterEvBatteryStateOfCharge, CAR_ENERGY, area 없음 | Int? %, 앱 수신시각과 측정시각 구분. 100% 수신 보고. SOH/고장 아님 [R2] [S3] |
| 2. 누적거리 | **A get / B listener** | Odometer.getCurrentDistanceDriven / checkCurrentDistanceDrivenCapabilities | register/unregisterDistanceDriven, CAR_DRIVE, area 없음 | Float? **km**, 0.0 수신 보고. 앱 수신시각. 트립거리 m와 혼동 금지 [S4] |
| 3. 연료잔량 | **B, 현재 환경 UNSUPPORTED** | Fuel.getFuelPercent / checkFuelPercentCapabilities | register/unregisterFuelPercent, CAR_ENERGY, area 없음 | Int? %. 실패 응답 확인은 유효 연료값의 A가 아님. 0% 대입 금지 [S5] |
| 4. Door 개폐/잠금 | **B** | getCurrentDoorPosition / checkDoorPositionCapabilities(config+area bounds), getDoorLockState / checkDoorLockCapabilities(config+areas) | register/unregisterDoorPosition·DoorLockState, CAR_DOORS, DoorArea | Int? 위치/Boolean? 잠금. bounds는 차량별, 잠금≠닫힘, 개별 area 실패 별도 [S6] |
| 5. Window 상태 | **B** | getCurrentWindowPosition / checkWindowPositionCapabilities, getCurrentWindowLockState / checkWindowLockCapabilities | register/unregisterWindowPosition·WindowLockState, CAR_WINDOWS, WindowArea | Int? 위치/Boolean? 잠금. 위치를 보편 %로 가정하지 않음 [S7] |
| 6. 실압/저압 경고 | **C** 실압·TPMS / **B** 저압 임계값 | Tire.getTirePressure/checkTirePressureCapabilities는 Sample blocked. getLowTirePressure / checkLowTirePressureCapabilities·checkWheelAreaCapabilities는 문서 존재 | 실압 register/unregisterTirePressure blocked. 저압 임계값 CAR_SAFETY, WheelArea | getLowTirePressure는 Float? **kPa 임계값**. 현재 압력 아님. TPMS는 제한된 Safety 경로 [S2] [S8] |
| 7. 차량 경고등 | **C** | Safety.checkTelltalesCapabilities / getTelltalesInfo | register/unregisterTelltales·TelltaleInfoListener, area 없음. 특권/권한 표기 주의 | Telltales의 type/state/color/priority 등. 공개 수신 성공 증거 없음 [S2] [S9] |
| 8. DTC/OEM 진단 | **D** | 공개 read/clear·진단 Capability 구체 method 미확인 | 권한/area/listener 미확인 | DTC 해석·삭제·진단값·시각 지원을 만들어내지 않음 |
| 9. 상태변화 listener | **B**, Safety/Tire 관련은 **C** | 위 register/unregister와 Vehicle.initialize/release | 동일 객체/area로 구독 관리, 각 신호 권한 계승 | 등록 반환≠실제 callback. CARITDA 현재 readOnce뿐, 실제 listener 연속 수신은 미검증 [S0] [S1] |
| 10. 앱 미사용 중 지속 관측 | **D: 지속 보장 미확인** | 상시 지속을 보장하는 단일 SDK method 없음 | Android 수명주기·FGS·AAOS 전원정책, 별도 검증 | 현재 onStop에서 release. 종료/절전 중 값·시각은 알 수 없음 [R15] [A1] [A5] |

추가 참고: getEvChargeSwitchState/checkEvChargeSwitchCapabilities는 CAR_ENERGY/Boolean?로 문서에 있다(B). 검토한 EvBattery 목록에서 해당 충전 switch listener는 미확인이다. 충전전력·케이블 상태로 확대하지 않는다. getEvChargeCurrentDrawLimit은 A 단위의 **제한값**이지 실측 전류가 아니다. 제어 set 메서드를 조회로 바꿔 설명하지 않는다. [S3]

### 3.2 PublicApiPolicy의 정확한 의미

공식 Sample의 고정 커밋959981d960ec71144accb27af5ae5d341e72a644와 현재 정책 blob이 동일함을 독립 재확인했다. 차단 집합은 CarInfo.getCapabilityInfo, Safety 네 개(check/get/register/unregister telltales), Tire 네 개(check/get/register/unregister tirePressure)다. [S2]

이는 **Sample public catalog 제외 정책**의 증거이지 모든 차량에서 절대로 호출할 수 없다는 실차 실험 결과는 아니다. 그러나 일반 앱 MVP의 사용 가능 근거로 삼을 수도 없다. Deprecated 제외와 차량별 Capability/area도 따로 확인한다.

checkTelltalesCapabilities에는 PRIVATE_CAR_SAFETY, getTelltalesInfo에는 CAR_SAFETY 표시가 있으며 기존 조사에서 권한 설명 불일치도 관찰됐다. 네 함수 전부가 동일 private 권한이라고 단정하지 않는다. **일반 권한을 추가하거나 private API를 우회해 해결하지 않는다.**

SDK 소개는2.3.2/Service2.3.21, Sample은2.3.2/Service2.3.22로 기준이 다르다. 버전 표시·SDK initialized·Supported·등록 Complete만으로 실제 연결/area/수신을 확정하지 않는다. [S0] [S1]

### 3.3 시간·실패 처리 — 모든 신호 공통

현재 scalar getter와 확인한 listener에는 원천 측정 timestamp가 함께 오는 근거를 확보하지 못했다. 현재 Observation.receivedAt은 앱 콜백 시계, recordedAt은 DB 기록 시각이다. session UUID는 readOnce 배치일 뿐 운행/시동 세션이 아니다. emulator/vehicle 분류도 hardware 문자열 휴리스틱이라 반대 분기를 실차 인증으로 표현하지 않는다. [R7] [R8]

| 조건 | 현재 코드/공식 근거 | MVP 처리 |
|---|---|---|
| null/NaN/음수/%>100 | 현재 client INVALID 처리, 실패 관측은 raw/unit/receivedAt 없음 | 고장 카드가 아닌 수신 실패. 잘못된 값을 정상화해 채우지 않음 |
| ERROR_NOT_SUPPORTED_PROPERTY/AREA | UNSUPPORTED | 지원 없음/해당 area 없음, 이전 값을 현재로 사용하지 않음 |
| SecurityException/ERROR_SECURITY_PERMISSION | PERMISSION_DENIED | 요청 불가 표시, normal 권한 승인창을 임의로 띄우지 않음 |
| ERROR_EMPTY_RESULT | INVALID | 빈 결과, 0/false 변환 금지 |
| 연결/알 수 없는 실패 | 현재 client CONNECTION_FAILED fallback | 이유와 원본 오류 요약을 보존, 확정 고장/미지원으로 과도 분류하지 않음 |
| 15초 내 완료 안 됨 | TIMEOUT | 최신 시도 실패, 무한 자동 retry/중복 카드 없음 |
| 과거 valid만 있음 | mostRecent(kind,true)로 조회 가능 | 최신 시도도 같이 검사, ‘이전 값’으로 표시, 새 감지에 재사용 금지 |

공식 VehicleException/errorCode 설명 [S11]을 실제 AAR 타입 확인 없이 복사하지 않는다. 현재 client는 공개 ErrorCode 이름과 fallback을 사용한다. 새 listener의 실패/해제/재연결도 값 성공과 분리해 검증해야 한다.

### 3.4 실제 사용 가능 범위의 결론

현재 검증된 수신값은 **SOC와 누적거리**다. 연료 실패도 확인했지만 연료 데이터 사용 가능이라는 뜻은 아니다. Door/Window는 후속 공개 신호 실험이며 이번 1차 MVP의 성공 조건에 필수로 넣지 않는다. 실제 압력·경고등·DTC가 없으면 자동 경고 통역/고장 감지를 시연하지 않는다.

### 3.5 별도 Vehicle Data API

Vehicle Data API는 connected-car 텔레매틱스 REST/OAuth2/VIN/Brand·차량 공유 동의의 별도 통로다. SDK는 차량 내 Service IPC/typed API/Manifest 권한이다. 서로의 지원 근거를 전용하지 않는다. [S12]

현재 확인한 소개의 지역 안내는 **‘현재(2026.8월) 유럽 지역에서만 사용 가능’**이다. 이는 문서에 적힌 기준일이며 11월8일 한국 사용 가능성을 입증하지 않는다. 원격 status에 타이어/경고 필드가 있다는 이유로 국내 SDK에서 받을 수 있다고 쓰지 않는다. 토큰·자격·한국 지원·수신 지연·동의·실차 응답은 미검증이고 이번에는 endpoint를 호출하지 않았다.

---

## 4. 자동 기록과 자동 이상 감지의 정의

| 종류 | 예 | 생성 대상 | 금지 해석 |
|---|---|---|---|
| 정상 값 변화 | SOC100→99%, 누적거리 변화 | 상태 스냅샷/수신 변화 이력 | 배터리 열화·고장·위험 운전 |
| 사용자 편의 임계값 | 설정한 SOC20% 미만 | 편의 안내 카드 | 공식 저전압/배터리 고장 경고 |
| 유효 범위 밖 수신 | SOC101/null/NaN 등 | 기술적 실패/unknown 표시 | ‘차량 배터리가 고장’ |
| 공식 차량 경고 | 허용 경로에서 실제 수신한 식별 가능한 warning | 경고 관측 카드, 근거/명세 연결 | 현재 blocked 신호를 수신했다고 주장 |
| 검증된 고장 가능성 신호 | 차종/조건별 의미가 검증된 진단 결과 | 후속 단계의 조건부 이상 검토 | 확정 수리 필요/원인/안전 보증 |
| 사람 체감 증상 | 소음·진동·냄새·작동 이상 | 사용자 문제 기록 | 관련 센서가 없는데 자동 정상/회복 |

**정보 카드와 문제 기록을 분리하는 것을 권고한다.** 관측/편의 카드는 시스템이 만들 수 있으나 기존 VehicleIssue는 사람이 의미를 부여하고 관리하는 사건이다. 카드를 읽거나 알림을 끈 행위가 기존 네 상태를 변경해서는 안 된다.

---

## 5. 최초 카드 1~2종과 실행 관문

### 5.1 필수 후보: 차량 상태 스냅샷

입력: 최신 시도의 SOC/거리 성공·지원·유효 값. 한 신호 실패 시 다른 신호의 시각/값을 공유하거나 둘 다 성공으로 표시하지 않는다.

- 해당 환경/차량 범위에 현재 상태 카드 한 개. 각 항목에 값/단위·앱 수신시각·출처·이전 값/미확인 상태를 개별 표시.
- ‘앱에서 방금 받은 값’과 ‘차량 측 새로 측정된 값’을 구분한다. sourceMeasuredAt 미제공이면 그대로 ‘측정시각 미제공’.
- 동일 값 재수신은 새 문제/새 카드가 아니라 최신 참조와 수신시각 갱신. 변화가 있으면 immutable 참조로 ‘이전 앱 수신값→이번 앱 수신값’을 남긴다. 실제 변화 발생시각을 역추정하지 않는다.
- 소음 메모에 연결할 때 ‘입력 시점 주변에 조회한 차량 정보’임을 표시한다. 사용자가 적은 과거 발견 시각의 값이라고 바꾸지 않는다.
- 전체 차량 ‘정상’ 녹색 인증이나 ‘이상0건’ 배지를 만들지 않는다.

이 후보는 현재100%/0km만 받아도 실제 수신→자동 기록→증상 연결 경로를 시연할 수 있다. 단, **자동 조회/카드/연결은 새로 구현·검증해야 한다**.

### 5.2 두 번째 후보: 낮은 충전량 편의 안내

설계 예: 사용자가 낮은 충전량 안내를 켜고 기준20%를 선택했을 때 **SOC<20%**이면 후보가 된다. 기준·비교 연산자를 UI와 규칙에 명시한다. 경계값20%는 이 예에서 미만 조건이 아니다.

~~~text
낮은 충전량 안내 · 사용자 설정
이번 요청에서 받은 충전량: 18%
설정: 20% 미만일 때 안내
앱 수신시각 / 출처 / 측정시각 미제공 여부
배터리 고장을 뜻하는 알림은 아니에요.
[확인] [이 안내 숨기기] [안내 설정]
~~~

위18%는 **문구 설계 예시**다. 현행 validation에서18% 수신이 확인됐다는 뜻이 아니다. 실제 값이 조건을 만족하지 않으면 이 카드를 만들지 않는다.

- 첫 관측부터 낮으면 ‘기준보다 낮은 값이 수신됨’. 이전 높은 값이 없는데 ‘20% 아래로 내려감’이라고 하지 않는다.
- get 응답은 현재 상태 API의 값이지만 ECU 측정시각/캐시 신선도까지 보장하지 않는다. 편의 정책은 **수신값 기준·측정 신선도 미확인** 품질을 명시하고, 최신 요청 성공·오류/지원/환경 및 갱신 의미 검증을 요구한다. 이를 회복·안전 경고 정책과 섞지 않는다.
- 타임아웃/이전 값/unknown으로 새 low episode를 열지 않는다. 과거15%를 가져와 새 카드로 재생하지 않는다.
- N회/TTL/재알림·hysteresis는 편의 정책의 버전 있는 설정이다. OEM 고장 기준으로 광고하지 않는다. 경계 흔들림/캐시/연속성 시험 전 활성화하지 않는다.

### 5.3 권장 관측 범위와 대안

**P0:** 앱이 보이고 UXR 입력 허용이 확인된 진입에서 한 차례 자동 조회→원본 저장→카드 갱신. 재구성/redraw마다 조회하지 않으며 한 번에 한 요청만 허용한다. 명시 새로 조회도 제공할 수 있다. ‘사용자 입력 없이 카드가 기록됨’과 ‘차량을 계속 감시함’을 구분한다.

**조건부 P1:** SOC listener를 별도 validation에서 실제 변화 callback까지 검증하면, 앱 가시/연결 중 자동 갱신에 추가할 수 있다. 등록 성공만으로 켜지지 않는다. onStop/연결 상실 때 해제·공백 표시, 복귀 시 Capability/현재값 확인을 한다. 무제한 polling 주기는 임의로 정하지 않는다.

콘솔에 값을 바꿀 수 있고 그 값이 SDK에 반영되는지는 이번 연구에서 확인하지 않았다. **저 SOC 실수신이 재현되지 않으면 두 번째 카드는 시연에서 빼고 첫 스냅샷 카드만 제출한다.** fixture 화면을 실제 SDK로 위장하거나 임계값을 말없이 바꿔 경고처럼 만들지 않는다.

사용자 지정 누적거리 도달 안내는 같은 인프라의 후속 후보지만 필수2종에는 넣지 않는다. 점검 기준/차량 baseline/거리 감소·에뮬레이터 reset·단위 검증이 더 필요하며 OEM 정비주기라고 주장할 수 없다.

---

## 6. 자동 카드의 모델·중복 방지·이력

아래 객체/열 이름은 **후속 설계**다. 현재 존재하는 DB/API라는 뜻이 아니다.

| 객체 | 필요한 필드 | 의미 |
|---|---|---|
| AutoCard | cardId, vehicleScope, sourceEnvironment, type, createdAt, latestEvaluatedAt, conditionState, visibilityState, relatedIssueIds | 네 사용자 상태와 별도 |
| CardEvidence | stable observationRef, signalKind/area, actualRaw/canonicalValue/unit, supported/valid/status, source, receivedAt/sourceMeasuredAt?/recordedAt | 각 신호별 실제 근거·시각, 없는 측정시각을 생성하지 않음 |
| RuleSnapshot | ruleId, version, userSetting/version, comparison/bounds, freshness quality, reason | 고장 기준과 사용자 편의 기준 분리 |
| DetectorState | monitoringEpoch, lastProcessedRef, activeEpisodeId, rearmState, latestAttemptRef, evidenceEpoch | 재시작·중복·실패로 잘못 재발시키지 않음 |
| CardEvent | eventId, origin, actorType/driverContext?, before/after, rule/evidence refs, time, feedback | 시스템 관측/사람 결정 구분 |
| ConcernLink | concernId, cardEpisodeId, issueId?, bindingRevision, relationOrigin | 같은 문제/새 독립 문제/단순 안내 연결의 기준 |

driverContext는 당시 선택된 이름일 수 있으나 시스템 관측의 작성자를 뜻하지 않는다. VehicleIssue/기존 EventKind는 drivers FK·valueOf reader가 있어 새 SYSTEM enum을 그냥 넣지 않는다. 별도 sidecar/read model과 명시 연결을 우선한다. [R4] [R8] [R9] [R11]

### 6.1 생성·갱신 규칙

- 스냅샷 카드 키: 차량 대응 범위+출처 환경+카드 종류. 같은 값이100번 와도 카드100개/문제100개를 만들지 않는다.
- low SOC 카드: **동일 concern의 active episode 한 개**. 반복 수신은 관측 참조/상태 이력을 추가하거나 갱신할 뿐 새 문제로 만들지 않는다.
- episode는 규칙의 조건이 실제 해제되고 재진입하는 것을 검증한 경우에만 새로 연다. 예시 재진입 억제 구간은 편의 설정이지 보편 안전 규정이 아니다. 오류/공백/앱 재시작/운전자 선택/날짜 변경/규칙 버전 변경만으로 재무장하지 않는다.
- ruleId는 안정적으로 유지하고 version 변경은 이력으로 남긴다. 버전을 식별자에 무조건 넣어 같은 문제·알림 예산을 초기화하지 않는다.
- duplicate/false-positive/dismiss 상태와 **실제 signal condition**은 별도다. 사람이 숨겼다고 센서가 정상화된 것으로 쓰지 않는다.
- 같은 issue의 재발과 독립 새 문제를 구분한다. 새 observation/episode ID만으로 새 concern을 만들어 알림 제한을 우회하지 않는다.

### 6.2 원본·파생 데이터·승인의 일관성

현재 Observation DTO는 행 ID를 내보내지 않으므로 읽기 projection/참조 모델에서 storeId+rowId 등을 노출하는 후속 작업이 필요하다. 다른 패키지/UID/차량의 같은 행 번호를 합치지 않는다. VIN을 비밀번호로 쓰거나 평문 식별값을 불필요하게 전송하지 않는다.

원관측을 먼저 commit하고 issue/카드 DB에서 불변 참조로 연결한다. 두 DB가 자동 원자 트랜잭션인 것처럼 구현하지 않는다. 모든 writer와 판정/승인 경로를 직렬화하고 최신 시도/evidenceEpoch를 갱신한 뒤 오래된 제안을 무효화한다. raw commit과 watermark 사이에 옛 근거 승인이 끼면 안 된다. 실패/재시작/다중 writer로 정합성이 불명확하면 제안 수락을 잠그고 재평가한다. [R4] [R5]

사용자가 카드에서 증상을 새 문제로 남기면 기존 인간 actor·작성자/초안/저장 중복 보호를 유지한다. 같은 issue DB의 생성+연결은 원자 커밋을 권장한다. 부분 실패가 가능한 구조라면 ‘문제 저장은 됐으나 차량 정보 연결 실패’를 사실대로 알리고 같은 ID로 연결만 재시도한다. 문제를 중복 생성하지 않는다.

---

## 7. 신호 변화와 기존 네 상태

미해결/점검 예정/점검 완료/해결 확인은 그대로 유지한다. 기계의 조건 해제는 별도 card condition이다. 점검 완료와 해결 확인의 인간 의미를 바꾸지 않는다.

- 자동 카드 생성·조회·조건 해제·알림 끄기·숨김은 기존 issue.status/revision/읽음을 바꾸지 않는다.
- 기존 문제 상세에서의 읽음 확인 동작은 현행 정책을 유지한다. ‘회복 이력 조회 자체’와 구분한다.
- 실제 해당 신호를 받은 경우에만 ‘최근 조회에서 해당 경고가 확인되지 않았습니다’ 또는 ‘앞왼쪽 도어가 닫혔습니다’ 같은 관측 문구를 후속 기능으로 검토한다. **이번 MVP는 그 신호를 수신하지 않아 이 문구를 실행 기능으로 넣지 않는다.**
- SOC 증가의 적합한 문구는 ‘이전 앱 수신값보다 충전량이 높습니다’이지 ‘배터리 문제가 수리됐습니다’가 아니다.
- 원본 관측/규칙 제안/사용자 승인/사용자 수동 변경/향후 정책 자동 변경의 origin을 구분한다.
- 사용자 결정 전 현재 상태·삭제·내용 revision 및 state token/evidenceEpoch·만료를 재확인한다. revision만으로 수동 STATUS 충돌을 알 수 없다.
- 사용자 수정·삭제·재발/새 실패가 먼저이면 옛 제안 차단. 승인이 먼저 확정된 뒤 신호가 다시 나타나면 후속 사실을 추가하고 사람 결정을 조용히 뒤집지 않는다.
- **현재 네 상태의 자동 전이 허용목록은 빈 목록**이다. 사람의 해결 선택은 정비사/OEM 수리 인증과 다르다. [R4] [R5]

---

## 8. 삭제 제한·오탐·숨김·영구 삭제

### 8.1 비교

| 방안 | 이점 | 문제/위험 | 권장 |
|---|---|---|---|
| 미해결 기록 삭제 금지 | 미완료 사건 유실 억제 | 오탐/중복/센서 없는 문제는 영원히 갇힐 수 있음, 개인정보 통제 제한 | **일괄 강제 금지 반대**. 중요 경고 안내/확인과 별개 |
| 오탐 신고·기록 해제 | 관련 없는 안내 정리·규칙 품질 개선 | 사용자의 피드백을 센서 정상/고장 부재로 오인 가능 | condition과 feedback 분리, 같은 episode 재생 억제 |
| 알림 끄기 | 반복 방해 감소 | 관측·이력도 중단했다고 오해 | 알림/관측/카드 표시 설정을 분리, OEM 경고는 영향 없음 |
| 보관/숨김 | 가역적 정리, 문제와 이력 보존 | 숨은 기록 위치를 모르면 삭제로 오해 | P0 카드에 권고, ‘숨긴 안내’와 다시 표시 경로 제공 |
| 복원 가능한 삭제 | 잘못 삭제한 사용자 기록 복구 | 권한·연결·읽음·보관기간/백업 검증 필요 | 기존 논리 삭제 유지, 휴지통 복원은 별도 후속 구현 |
| 영구 삭제 | 개인정보 요구에 대응 | 증거 참조/공유 기록/백업/서버 사본 영향 | 별도 명확한 범위·권한·보존 정책·실행 확인 후 |

### 8.2 권장 정책

1. **문제 기록:** 현행 작성자 권한과 확인창을 유지한다. 미해결도 논리 삭제를 허용하되 상태 변경·정상화로 해석하지 않는다. 기존 삭제 정책을 이번 연구가 바꾸지 않는다.
2. **자동 편의 카드:** ‘숨기기/오탐 또는 관련 없음/알림 끄기’를 제공한다. 동일 근거의 재생으로 강제로 다시 띄우지 않는다. 다시 표시/설정 복귀를 제공한다.
3. **후속 공식 경고 카드:** 숨김은 CARITDA 표시 통제일 뿐 OEM 계기판/안전 경고를 지우지 않는다. 계속 활성인 경고를 ‘해결’로 숨기지 않으며 공유 운전자의 표시 범위는 별도 정책으로 정한다.
4. **원관측:** 보존기간 내 무결성/append-only 원칙을 유지한다. 그러나 **영구 보유 의무나 개인정보 삭제 거부의 근거는 아니다**. 보관기한/최소화/정당한 보존 예외를 별도 검토해야 한다.
5. **영구 파기:** 원문·event snapshot·파생 카드·외부 payload·등록정보·백업/내보내기 사본까지 범위를 정의한다. 남길 최소 감사정보와 제거 대상, 공유/타인 기록의 영향, 참조 무결성·철회 절차를 확인한다. 원문 해시가 남았다고 항상 익명화됐다고 가정하지 않는다.
6. 현재 원문/백업이 남는 논리 삭제를 ‘완전히 지웠습니다’라고 쓰지 않는다. 현재 없는 복원/영구 파기 기능을 광고하지 않는다. 법적 적용/보존 기간은 이번 조사에서 확정하지 않았다.

사용자 통제권과 신뢰성은 **삭제를 금지하는 것보다 원본·의미·변경 출처·숨김/삭제 범위를 분리하는 것**으로 함께 확보한다.

---

## 9. 팀원 아이디어 통합

### 9.1 경고등 통역사

자동 경고는 현재 C/D 경계이므로 **직접 선택 모드부터** 검토한다.

~~~text
경고등 찾아보기 · 직접 선택
차량에서 본 경고를 선택해 주세요.
선택한 경고: 저압 타이어 / 켜짐·깜빡임 등 확인한 상황
이 항목은 사용자가 선택했으며 차량 신호로 자동 확인한 것이 아닙니다.
해당 모델·연식·동력계의 제작사 설명 / 공식 출처
[증상과 함께 기록] [선택 수정] [공식 설명서 보기]
~~~

지식 항목은 warningKey+제조사/모델/연식/시장/동력계/색·점등 형태+출처 URL/문서 버전/확인일을 가진다. 차량을 모르면 맞는 매뉴얼 확인부터 안내하고 다른 차종의 지시를 보편화하지 않는다. AI의 자유 추론보다 검토된 정적 한국어 템플릿을 우선한다.

공식 현대자동차 LX3/2026/ko_KR 설명서 [O1]는 예를 들어:
- TPMS가 현저한 저압뿐 아니라 시스템 이상과도 관련됨을 설명한다.
- 브레이크 경고가 EPB 체결/브레이크액 부족/시스템 이상 등 여러 원인과 관련됨을 설명한다.
- 충전/12V 충전 경고를 단순 EV 구동배터리 SOC 낮음과 다르게 설명한다.

따라서 SOC<20% 카드를 배터리 모양 ‘충전 경고등’으로 부르지 않는다. 그림/색만으로 고장 원인을 단정하거나 ‘주행해도 안전’이라고 답하지 않는다. 정확한 긴급 대응은 실제 해당 차량 제작사 지침을 보존해 제시한다. 운전 중 긴 매뉴얼/검색 입력을 열게 유도하지 않는다.

자동 수신이 후속에 허용되더라도 선택 출처 USER_SELECTED_WARNING과 SENSOR_VERIFIED_WARNING을 섞지 않는다. 사전 선택 항목을 실제 관측 목록으로 옮기지 않는다. 매뉴얼 존재가 센서 접근 가능성의 근거도 아니다.

### 9.2 잡소리 노트

기존 NOISE/VIBRATION/OTHER와 본문·메모·발견시각을 활용한다. 냄새/작동 이상은 OTHER에 짧은 안내로 시작할 수 있고 새 enum 이전은 필수 아니다.

입력 최소화: **무엇(소리/진동/냄새/작동) + 언제/어떤 상황 + 짧은 설명**. 실제 지원 차량값 snapshot을 선택적으로 첨부한다. 직접 느낀 사실과 차량값의 상관만 남기며 인과/진단으로 바꾸지 않는다.

- 발생시각이 과거면 현재 조회값을 그때의 값으로 붙이지 않는다.
- snapshot 실패는 증상 저장 실패로 만들지 않는다. 값 연결 실패/미확인을 따로 알린다.
- 상태/카테고리를 나중에 수정해도 이전 관측을 다시 쓰지 않는다.
- 상시 마이크·녹음·AI 음향 진단·운전 중 상세 입력은 이번 구현 범위가 아니다. 권한/동의/보관·정확도·DO 검증 없는 마이크 버튼을 추가하지 않는다.

### 9.3 시그널코치

운전 습관 점수는 이번 핵심과의 관련성이 낮다. 속도·가감속·주행 문맥·라벨 검증을 추가하고 사용자에게 운전 평가/감시 우려를 준다. **1차 MVP에서 제외**한다. 향후 검증된 신호가 실제 점검 맥락을 보완하는 경우에만 별도 실익을 검토한다.

---

## 10. 운전자 한 명 중심 UX, 다중 모델 보존

단일 사용자 모드는 **화면 우선순위**이지 DB에서 다른 운전자/기록을 지우는 모드가 아니다. 논리 운전자, Android user10, 앱 패키지 UID, 실제 차주 인증은 서로 다르다.

| 화면 | 1인 MVP 구성 | 간소화/보존 |
|---|---|---|
| 시작 | 최근 선택 이름+명시 ‘이 이름으로 시작’, 필요 시 이름 선택 | 여러 카드의 반복 선택을 줄이되 몰래 확정/인증했다고 하지 않음 |
| 홈 | 상태 스냅샷·편의 안내 / 증상 남기기 / 기록·점검 | 고정 앱 홈 유지, OS GNB와 구별 |
| 증상 입력 | 짧은 증상/발견시각/선택 snapshot | 초안·작성자·전후 수정 이력 보존 |
| 사건 상세 | 네 상태·점검 메모·재발·관련 차량 수신 이력 | 시스템 fact와 사람 변경을 다른 줄로 표시 |
| 기록 | 기본 전체, 검색/관리 중/완료 유지 | ‘내 작성/인계 확인’은 추가 메뉴, 다른 작성자 기록을 무단 숨기지 않음 |
| 설정 | 안내 기준·관측 범위·숨긴 카드·기록 이름·사용법 | 운전자 관리/추가는 secondary 메뉴로, 기존 영구 ID/보관/권한 유지 |

자동 카드에는 기본적으로 시스템 origin을 사용하고 당시 driverContext만 선택적으로 연결한다. 1인이라고 SYSTEM을 그 사람의 서명으로 바꾸지 않는다. 다중 확장 시 동일 issueId·작성자 snapshot·읽음·재발/수정 이력으로 인계할 수 있어야 한다.

폰 연계를 붙이기 전에는 이름 선택만으로 외부 전송 권한이 생기지 않는다. 별도 인증된 사용자/기기 연계와 동의가 필요하다.

---

## 11. 일반/검증 앱의 명확한 구분과 Pleos GNB

### 11.1 현재 확인 사실

- 일반 applicationId는 com.nextdrive.pleos, validation은 suffix .validation로 **com.nextdrive.pleos.validation**이다.
- validation은 initWith(debug), matchingFallbacks(debug), resValue app_name=‘CARITDA 검증 전용’이다.
- 읽은 main Manifest에는 application icon/roundIcon, activity icon/label, alias가 없다. **전체 리소스/병합 manifest/APK를 검사하지 않았으므로 최종 아이콘이 없거나 Android 기본 아이콘이라고 단정하지 않는다.**
- MainActivity 내부 제목은 CARITDA 하드코딩이다. 패키지 표시명 분리만으로 내부 화면이 구별되지는 않는다. 앱의 navigationBar는 자체 홈/기록/차량 UI이지 Pleos GNB가 아니다. [R13] [R14] [R15]

### 11.2 권장 패키징·디자인

| 항목 | 일반 | 검증 전용 |
|---|---|---|
| 표시명 | CARITDA | **[TEST] CARITDA 검증 전용**, prefix로 잘림에도 남김 |
| 실루엣 | 본래 앱 로고 | 시험용 별도 형상+굵은 TEST 띠/큰 T |
| 색 | 본래 청색/남색 | 보라/자홍 등 명확한 대비, 색 외 형상·문자 필수 |
| 내부 | 일반 제목 | 모든 주요 화면 ‘검증 전용 · TEST’ 상시 표시 |
| 아이콘 경로 | 원래 명시 자산 | adaptive/round/monochrome/legacy 모두 TEST 의미 유지 |

이는 제안 디자인이지 생성한 아이콘/구현 결과가 아니다. adaptive 108×108dp와 중앙66×66dp 안전영역에 핵심 표지를 두고 원형/squircle·단색 테마·작은 GNB·밝고 어두운 화면에서 검증한다. 예시 TEST 띠52×18dp·굵은11~12dp 글자는 실제 축소 시험 후 조정한다. 노란 얇은 하단 띠는 GNB 활성 표시와 혼동될 수 있어 피한다. [I3]

- validation 리소스는 **src/validation/res/**에 분리하고 application의 icon/roundIcon·label 및 모든 launcher activity/alias를 병합 결과로 확인한다. 기존 appId/namespace를 바꾸지 않는다.
- initWith는 설정 복사이지 src/debug/res 자동 포함이 아니다. matchingFallbacks는 의존 variant 선택용이지 로컬 debug 리소스 보충 규칙이 아니다. [I1]
- validation legacy PNG만 바꿔도 main의 adaptive/v26/monochrome 등이 선택될 수 있다. source-set 우선순위와 런타임 qualifier 선택을 각각 검사한다.
- resValue와 파일의 동일 app_name을 무작정 중복 선언하지 말고 locale/round/themed 경로까지 TEST가 남는지 확인한다.
- Android Studio Merged Manifest와 APK Analyzer로 두 APK의 label/icon/package/launcher entry를 확인한다. 빌드타입 둘 다 debuggable일 수 있으므로 DEBUG 여부 하나로 구분하지 않는다. [I2] [I8]

### 11.3 GNB의 관리 경계와 노란 표시

Pleos 공식 Layout은 GNB와 application window를 구별되는 **시스템 UI** 요소로 설명하고 시스템 UI가 앱보다 상위 레이어라고 명시한다. GNB 문서는 pinned/editable/recent 슬롯과 최근 앱2개 등을 설명한다. [I5] [I6]

하지만 **노란 활성 표시의 실제 소유 프로세스·선택/갱신 알고리즘은 직접 공식 근거로 확정하지 못했다.** AOSP CarSystemUI는 시스템 navigation bar 사례를 제공하지만 이를 Pleos 구현 사실로 복사하지 않는다. [I4]

앱이 고칠 수 있는 범위는 패키지별 자산·라벨·launcher entry·앱 내부 제목 및 정상 launch/task 사용이다. OS 표시를 앱 overlay로 덮거나 다른 패키지/task로 위장하지 않는다. 단순 아이콘 수정으로 GNB 선택 버그가 반드시 해결된다고 약속하지 않는다.

후속 읽기전용 진단: 정차 시험환경에서 일반→validation→다른 앱→각 복귀·GNB pinned/recent 진입을 재현하고 동일 시점 화면과 package/component·task/display/user·top-resumed/각 display top activity를 비교한다. dumpsys activity/window/display/package는 상태 확인 수단이다. focused window 하나만으로 모든 전경 상태를 판정하지 않는다. [I7]

예상 full component는 일반 com.nextdrive.pleos/com.nextdrive.pleos.MainActivity, validation com.nextdrive.pleos.validation/com.nextdrive.pleos.MainActivity다. **suffix가 Kotlin 클래스 패키지를 바꾸지 않으므로 validation/.MainActivity라고 추정하지 않는다.** 최종 manifest로 확인한다.

실행 package가 반대면 진입 entry/슬롯 매핑 후보, 실행은 맞고 자산만 같으면 리소스 후보, 실행/display는 맞고 다른 슬롯 표시가 지속되면 shell 매핑/갱신 후보다. 모두 가설이며 실제 dump/캡처 없이 원인 확정하지 않는다. 이 자료와 Pleos 빌드번호로 공식 문의할 수 있다. 이번에는 재현/기기 명령을 수행하지 않았다.

---

## 12. 앱 미사용 중 관측: 가능한 것과 불가능한 보장

| 상태 | 공식 근거/현행 경계 | 제품 표현 |
|---|---|---|
| 현행 앱에서 다른 앱으로 전환 | onStop이 pleos.stop/release | 지금은 계속 수집하지 않음 |
| 미래 background/FGS | 시작 제한·유형·권한·사용자 인지 요구, 불멸 프로세스 아님 | 승인된 범위에서 연결 중 관측 가능성 검증 필요 [A1] [A2] |
| WorkManager | 재부팅 이후 예약 재스케줄 가능, periodic 최소15분·지연 가능 | 실시간 listener 연속 감시 수단 아님 [A3] [A4] |
| 화면 꺼짐 | AAOS display-off와 CPU suspend는 다름 | 화면만 보고 관측 가능/불가 단정 금지 |
| AAOS STR/STD | 코드 실행 없음, 전원정책은 시스템 경계 | 앱/FGS로 무중단 감시 보장 불가 [A5] |
| 사용자 Stop/Settings force-stop | 서로 다른 동작. FGS Stop은 앱 제거, 예약 작업 가능; force-stop은 stopped state | 재시작 루프로 사용자 의도 우회 금지 [A6] [A7] |
| 부팅/서비스 재연결 | 초기화/Capability/구독 재등록 필요, 누락 replay 보장 미확인 | 현재 snapshot 복구와 공백을 표시 |

현재 target34/API34 환경과 Android15+의 추가 제한을 구분한다. 미래 OS 문서를 현행에서 발생한 실패로 쓰지 않는다. FGS type을 센서니까 무조건 dataSync라고 고르거나 정차 앱을 주행용 overlay/음성으로 우회하지 않는다.

P0 문구: **‘앱을 열어 연결된 동안 지원되는 값을 조회해 기록합니다. 종료·절전·연결 단절 중에는 기록 공백이 있을 수 있습니다.’** 단발 조회에서는 ‘관측 중’ 대신 ‘마지막 조회’를 쓰고 실제 listener 수신이 검증된 후에만 구독 상태를 표시한다. 이 한계를 사용법/기획서/영상에 함께 명시한다.

---

## 13. 스마트폰 알림 실현 가능성

### 13.1 차량 알림과 휴대폰 푸시

차량 AAOS 앱의 NotificationManager는 차량 디스플레이의 알림이다. 임의 휴대폰으로 보내는 API가 아니다. 차량 Bluetooth 연결이나 native AAOS를 Android Auto projection과 혼동하지 않는다.

권장 후속 구조:

~~~text
차량 로컬 기록/outbox
→ 인증된 HTTPS backend (연계 권한·동의·문제별 알림 원장)
→ FCM 등 push provider
→ 연결된 Android 폰 앱 (권한·중복·만료 검증)
→ 휴대폰 OS 알림
~~~

| 질문 | 판단 |
|---|---|
| 차량 앱만으로 가능? | 로컬 차량 알림은 가능 범주지만 임의폰 push는 아님. 수신 채널·서버/권한·연계 필요 |
| 폰 Android 앱 필요? | 직접 제어하는 FCM 방식에는 수신 폰 앱 필요. 웹푸시/기존 메신저 등 대체 채널도 사전 구독·계정·동의 필요 |
| 서버/FCM 필요? | FCM 방식은 trusted server가 요청. FCM만이 유일한 기술은 아니나 vehicle APK에 서버 비밀키를 넣어 대체하지 않음 |
| Google Play services? | 폰 FCM 지원환경 필요. 차량은 자체 backend에 HTTPS 업로드할 수 있다면 차량측 GMS 없이도 구조 가능. Pleos GMS/인터넷 가용성은 미검증 |
| 폰 알림권한? | Android13+ POST_NOTIFICATIONS·채널/사용자 설정 필요, 철회 가능. pairing 동의와 별도 |
| 현재 구현? | 현행 앱에는 원격 전송/계정/backend/FCM/동의/연계 구현 근거 없음. 이번에는 추가하지 않음 |

공식 FCM은 신뢰 서버와 Android 지원 수신환경을 요구한다. [F1] [F2] [F3] [F4]

### 13.2 1인 연계·개인정보·보안

- 사용자 확인 하에 짧은 만료의 일회용 코드/QR 등으로 차량·수신 폰을 연결하고 기기 목록/해제/차량 양도·로그아웃·동의 철회를 처리한다.
- VIN/운전자 이름/Android user ID/FCM 등록정보는 인증 수단이 아니다. 현재 로컬 이름 선택만으로 차주/외부 전송 권한을 확정하지 않는다.
- 계정 또는 별도 인증된 pairing 주체와 차량 권한을 확인한다. 타인의 운전자/사건 기록을 이름 선택만으로 업로드하지 않는다.
- 폰 잠금화면은 기본적으로 ‘CARITDA 확인할 기록이 있습니다’ 정도로 최소화하고 민감한 본문/VIN/위치·정비정보는 앱에서 권한 확인 후 본다.
- service account/private key/서버 OAuth credential은 신뢰 서버에만 저장한다. 차량/폰 APK나 GitHub에 넣지 않는다. 앱 등록정보도 비밀로 관리하고 서버에는 인증된 경로로 갱신한다.
- 토큰/등록정보는 재설치·데이터 삭제·기기 복원 등으로 바뀔 수 있다. 영어 문서와 SDK 버전의 최신/legacy 등록 방식 차이를 확인한 후 구현한다. 특정 새 API로 이번 앱을 무조건 바꾸라고 지시하지 않는다. [F3] [F7]

### 13.3 ‘같은 문제 휴대폰 최대2회’ 권장 규칙

**같은 차량·같은 논리 concern/issue에 전역 신규 논리 알림 슬롯 initial/reminder 두 개만 허용한다.** 계정/수신자별로 두 개씩 주는 정책이 아니다. 각 슬롯은 권한이 확인된 대상 폰 한 대에만 배정하고 여러 폰에 fan-out하지 않는 것이 초기 정책이다.

중요: **episode가 달라졌다는 이유만으로 같은 문제의2회 예산을 초기화하지 않는다.** 같은 문제 재발·반복 신호·새 관측 행·규칙 버전·재부팅·날짜/토큰/driver 변경은 reset 조건이 아니다. 새 독립 문제는 명시적 관계/판정으로 별도 concern을 만들고 이력을 남긴다.

- 전역 예산 unique key 예: **vehicleScope+stableConcernId+stage(initial/reminder)**. recipientAccountId를 예산 키에 넣어 계정 추가/변경으로 두 슬롯씩 늘리지 않는다. 별도 전달 원장에는 logicalNotificationId+recipientAccountId+deviceBindingId와 대상/상태를 기록한다. 원자 전역 카운터/unique+outbox로 중복 생성·fan-out을 방지하며 이미 사용한 슬롯을 새 계정/기기에 재생성하지 않는다.
- 네트워크 retry는 같은 logicalNotificationId/stage다. 세 번째 신규 알림을 만들지 않는다. Retry-After·backoff/jitter·만료·재시도 한도를 둔다. [F8]
- reminder는 아직 해당 문제/수신동의가 유효하고 사용자가 정한 재안내 조건일 때만. 해결/명시 확인/알림해제/TTL 만료 시 취소한다. 시점·중요도를 바꿔도 사용한 슬롯은 지우지 않는다.
- 중요도는 순서·표시·재안내 자격을 정할 수 있지만 기존 예산을 암묵적으로 초기화하지 않는다. **OEM 안전 경고와 차량 자체 경고는 이 제한의 대상이 아니며 억제하지 않는다.**
- 소음 등 직접 기록도 사용자가 외부 알림을 켠 문제만 대상이다. low SOC 편의 카드는 기본 차내로 한정하는 것이 초기 정책에 적합하다.
- 세 번째 이후에는 차내 기록에서 계속 확인한다. 단, 현행 앱의 비가시/절전 중 감시가 보장되지 않는다는 경계는 그대로다.

### 13.4 전달·표시·읽음은 다른 사건

FCM message ID는 **accepted for delivery**일 뿐 폰 전달 완료가 아니다. SDK 전달·OS 표시·탭·사용자 명시 확인도 다르다. [F5] [F9]

notification payload는 background에서 OS tray가 직접 표시할 수 있어 앱 선행 dedup이 어렵다. 엄격한 선행 검증에는 data-only→영속 dedup→자체 표시를 검토할 수 있으나 background/Doze/강제종료 제약과 지연/누락이 추가된다. [F6]

- 폰은 logical ID/stage/expiry/현재 concern 상태를 확인하고 영속 중복 방지한다. stable notification ID/tag도 사용하되 그것만으로 재설치 후 중복을 막지 못한다.
- 로컬 DB의 표시 예약과 OS 알림 표시는 하나의 원자 트랜잭션이 아니다. 중복 억제를 우선해 먼저 예약하면 crash에서 표시를 놓칠 수 있고, 반대로 하면 중복 위험이 있다. trade-off를 시험·명시한다.
- 앱 데이터 삭제/재설치·다중 폰은 로컬 ledger를 잃을 수 있다. 서버의 사용한 슬롯은 유지하고 새 토큰에 과거 슬롯을 무조건 replay하지 않는다.
- collapse는 미전달 메시지 대체이지 이미 표시한 중복 제거가 아니다. TTL 기본4주를 차량 경고 유효기간으로 무작정 쓰지 않는다. 짧은 업무상 만료를 정하고 stale 경고를 폐기한다. [F5] [F10]

따라서 약속할 것은 **‘동일 문제의 신규 논리 알림 기회 최대2개+중복 방지’**이며 ‘정확히2번 도착/표시/읽음’은 보장하지 않는다. HTTP 호출 횟수 최대2와 논리 슬롯2도 다르다. 실제 사용자 표현은 ‘같은 문제는 최대 두 차례만 알림을 요청하며, 기기·네트워크 상태에 따라 지연되거나 표시되지 않을 수 있습니다’처럼 검증 범위를 반영한다.

### 13.5 1차 전 판단

폰 앱·서버·인증·동의·등록 갱신·영구 원장·취소/만료·오프라인/중복·실폰 시험이 추가된다. 기존 기반이 없으므로 **11/15 전 P2**다. 목업을 제시한다면 ‘후속 스마트폰 연계 구상’이라고 표시하고 실제 push로 시연하지 않는다. 최종 PT 전에도 핵심 안정화 뒤 별도 go/no-go로 결정한다.

---

## 14. Codex에 전달할 구현 범위

### P0 — 1차 제출 핵심

1. 최신 main/기존174 assertion·운영 보존 기준을 고정한다. 기존 DB/운전자/문제/초안·연구를 유지한다.
2. 일반/validation label·아이콘·내부 TEST 표지를 분리한다. appId/namespace 변경·운영 데이터 테스트는 금지한다.
3. 기존 typed SOC/거리 읽기를 사용해 **허용된 앱 진입당 한 차례 자동 조회**를 구현한다. redraw/화면 재구성·동시 호출 중복을 막는다. 아직 시험하지 않은 listener를 필수 의존성으로 만들지 않는다.
4. 원관측을 먼저 저장하고 별도 sidecar 스냅샷 카드 한 개를 갱신한다. per-signal 최신 실패·시각·출처·측정시각 미제공을 표시한다.
5. 사용자 opt-in low SOC 규칙·episode/중복/재시작 보존을 구현하되 실제 저 SOC 수신 검증에 실패하면 해당 카드 시연을 비활성화한다.
6. 기존 잡소리/진동/기타 기록에 선택 snapshot을 연결한다. 사람 기록 원문/발견시각/actor와 센서 수신시각을 혼동하지 않는다.
7. 카드 숨김/다시 표시·편의 알림 설정을 제공한다. 기존 작성자 논리 삭제는 보존하고 상태 자동 변경은 넣지 않는다.
8. 단위/DB/실제 SDK/사람 이해도 결과를 분리하고 데모/기획서에 실제 한계를 적는다.

### P1 — 시간 한도 내 선택

- 실제 callback을 받는 foreground SOC listener와 재연결/해제 시험.
- 한 모델·연식 범위의 공식 출처 기반 수동 경고등 안내 소수 항목.
- TEST icon의 모든 qualifier·좁은 창·큰 글씨/단색·GNB 식별 시험과 OS 표시 재현 자료.
- release 빌드/실행 및 제출용 문서·영상/개인정보 제외 확인.

### P2 — 1차 뒤

Door/Window 새 권한/area 실수신, 타이어/telltale/OEM 공식 접근, DTC, 백그라운드 서비스·전원 복구, 폰 앱/backend/FCM, 휴지통·영구 파기 전체 정책. 운전 습관 점수·AI 음향 진단은 가치 검토 없이는 추가하지 않는다.

---

## 15. 인수기준 AM01–AM36

**후속 실행 기준이며 이번 연구에서 통과한 시험 수가 아니다.** REC/NAV/RR 결과와 별도 관리한다.

| ID | 우선 | 검증 과제 | 기대 결과 |
|---|---|---|---|
| AM01 | P0 | 최신 main·migration/운영 백업 사본 | 기존 driver/issue/event/draft·삭제·관측 원본/관계 보존, 운영 무변경 |
| AM02 | P0 | 앱 진입·UXR 변경·redraw/재구성 반복 | 허용 진입당 의도한 단발만, 동시 요청/초당 polling 없음 |
| AM03 | P0 | SOC/거리 실제 typed get | Capability/값/단위/출처·시각과 저장 증거, 에뮬레이터/실차 구분 |
| AM04 | P0 | 연료 미지원/null/권한/area 오류 | 0/false/정상으로 대체하지 않음, 새 문제/low 카드 없음 |
| AM05 | P0 | NaN/음수/%>100/timeout | INVALID/실패·unknown, 고장 판정 없음 |
| AM06 | P0 | 최신 실패 뒤 과거 valid 존재 | 이전 값 표시, 현재 감지/자동 카드 신규 근거로 쓰지 않음 |
| AM07 | P0 | sourceMeasuredAt 없음/시계 역행/미래값 | 앱 수신시각과 구별, 임의 ECU 시각/신선도 생성 없음 |
| AM08 | P0 | 동일 값100회/프로세스 재시작 | 스냅샷 카드1개, 원본 보존·관련 이력 안정, 문제 무한 생성 없음 |
| AM09 | P0 | SOC20/19 경계, 첫 관측부터 low | 명시 <20 조건·첫값 문구 정확, 배터리 고장/하강시각 추정 없음 |
| AM10 | P0 | 낮음/회복 구간·노이즈·unknown·중복 | episode 중복 억제, 검증된 재무장만 허용 |
| AM11 | P0 | 규칙 버전/기준/날짜/운전자/연결 변경 | 동일 concern을 새 문제로 복제·예산 reset하지 않음 |
| AM12 | P0 | validation/운영·source/차량/관측 store 변경 | 다른 환경 자료 결합 금지, epoch/관계 재검증 |
| AM13 | P0 | snapshot 일부 신호 실패 | 각 신호 값·시각·오류 따로, 전부 최신/정상으로 묶지 않음 |
| AM14 | P0 | snapshot 연결 뒤 과거 발견시각 입력 | 조회 당시 값과 증상 발생시각 구분, 인과/진단 없음 |
| AM15 | P0 | 증상 저장 성공·snapshot 연결 실패 | 사람 기록 유지·부분 실패 표시·같은 ID로 연결 재시도 |
| AM16 | P0 | 카드 생성/읽기/해제/숨김 | 네 issue 상태·본문 버전·기존 읽음 무변경, 별도 상세 읽음 정책 유지 |
| AM17 | P0 | 사용자 상태/수정/삭제와 제안 경합 | state token+evidenceEpoch/만료 검사, 최신 사람 결정 덮어쓰기 없음 |
| AM18 | P0 | raw commit/watermark 사이 실패·재시작 | 옛 근거 승인 잠금·재평가, 원관측 롤백 삭제 없음 |
| AM19 | P0 | SYSTEM event·기존 reader/FK | 가짜 Driver·기존 enum 파손 없음, 출처·사람 actor 분리 |
| AM20 | P0 | 미해결 삭제/오탐/숨김/알림 끄기 | 기존 작성자 논리 삭제 보존, 숨김≠정상화, 복원/영구 파기 과장 없음 |
| AM21 | P0 | 일반/validation 최종 APK | package·label·adaptive/round/monochrome·내부 TEST 즉시 구별 |
| AM22 | P0 | 운영대상 runner·기존 회귀 | validation 밖 거부,174개 기존 검사 의미 보존, 운영 DB 보존 |
| AM23 | P0 | 앱 전환/onStop/서비스 단절 | 관측 종료/공백 표시, 미수집 구간 정상으로 채우지 않음 |
| AM24 | P0 | 경고/고장/수리 완료 문구 감사 | 실제 미수신 warning·Door 정상화 문구를 실행 기능으로 넣지 않음 |
| AM25 | P1 | listener 등록 반환·callback 없음 | 수신 성공으로 세지 않음, 같은 객체/area로 해제·중복 구독 방지 |
| AM26 | P1 | 수동 경고 선택·모델 불명·자료 상이 | USER_SELECTED 표시, 해당 제조사 출처, 자동 확인/안전 보증 없음 |
| AM27 | P1 | 1인 UI·다른 작성자/초안/프로필 | 간소화만, 데이터/권한/인계 구조 보존 |
| AM28 | P1 | 좁은 창/큰 글씨/색각·단색 TEST/실제 GNB | 조작·근거 읽기 가능, 색만 구분 안 함, OS 노란표시 원인 미확정 구분 |
| AM29 | P1 | 일반 사용자 설명 과제 | 자동 수신/편의/직접 증상/진단 아님·관측 공백·삭제 범위를 구분 |
| AM30 | P0 | 실제 저 SOC 재현 불가 | low 카드 시연 off, 합성값을 실제로 위장하지 않고 스냅샷만 데모 |
| AM31 | P2 | 폰 pairing/권한철회/토큰·등록 변경 | 인증·동의 분리, 잘못된 대상 전송/해제 뒤 routing 없음 |
| AM32 | P2 | 같은 문제 반복/재발/새 episode/규칙·계정·수신기기 변경 | concern 전역 initial/reminder 총2개 유지, 수신자별 전달 원장은 별도·fan-out 없음, 독립 문제만 별도 |
| AM33 | P2 | 오프라인/timeout/429/503·중복/만료 | 동일 logical ID retry·backoff/TTL, 세 번째 신규 슬롯 없음 |
| AM34 | P2 | 폰 OS tray/data-only·재설치·다중 폰 | 표시 전 dedup 한계·ledger 상실·수신 폰 범위 검증, exactly-twice 주장 없음 |
| AM35 | P2 | FCM 수락/SDK수신/표시/탭/확인 | 사건/로그 구분, message ID를 전달/읽음으로 세지 않음 |
| AM36 | P0 | 제출/개인정보/기능 정직성 점검 | signed 기획서 준비, 실제/계획 구분, optional release 접근 확인, 비밀/DB/키 미게시 |

---

## 16. 11월15일까지 현실적인 우선순위

현재11/8 오후 기준 약1주다. 아래는 **개발 계획 제안**이지 일정 보장/실행 완료가 아니다.

| 날짜(KST) | 목표 | 중단/축소 기준 |
|---|---|---|
| 11/8 일 | 최신 기준·범위 고정, TEST 구분/2카드 명세 | 새 진단/폰 범위 추가 금지 |
| 11/9 월 | TEST 자산/내부 표지, 자동 단발 스냅샷·원본 연결 | 운영 DB 수정 시험 금지 |
| 11/10 화 | low SOC 중복·설정/오류, 잡소리 snapshot 연결 | low 실수신 미재현이면 필수에서 제거 |
| 11/11 수 | 격리 unit/DB/실제 SDK 회귀·재시작 | listener는 실제 수신/해제 증거 없으면 보류 |
| 11/12 목 | 사람 이해도·작은 화면·오탐/숨김 확인, 선택 경고 안내 최소 | 새 모델/경고 라이브러리 확대 금지 |
| 11/13 금 | 기능 동결·짧은 실증 영상·기획서 정리 | 미검증 기능은 계획/목업으로 분리 |
| 11/14 토 | 선택 release APK smoke/소스·README·심사 접근 준비, 제출 서명/자료 점검 | debug APK를 release라고 바꾸지 않음, 권한 초대/메일은 별도 승인 |
| 11/15 일 | 제출 마감 대응/오류 버퍼 | 새 기능 개발하지 않음, 정확한 마감 시각/공지 재확인 |

가장 작은 시연: **허용 환경에서 앱 진입→실제 SOC/거리 자동 조회·저장→스냅샷 표시→짧은 증상과 연결→점검 메모/상태는 사용자 선택→재시작 복원**. low SOC 실제 재현이 확인되면 두 번째 장면으로 추가한다. 폰 push/자동 경고 통역/상시 감시/AI 진단은 시연 성공으로 발표하지 않는다.

---

## 17. 남은 미확인과 출처

- 실제 low SOC 변화 입력/수신·캐시/갱신/측정 시각, 새 카드 엔진/연결/중복의 실행.
- foreground listener·서비스 단절·AAOS 절전/실차·주행 UXR 전환.
- 전체 res/merged manifest/APK의 최종 아이콘, 실제 GNB 노란 표시 원인.
- 해당 차종/연식 매뉴얼 콘텐츠 검수, 사람 이해도·접근성, release/배포 준비.
- 한국 Vehicle Data API 자격/지역·권한·실제 데이터, 특권/DTC/OEM 접근.
- 폰 앱·서버·연계·권한·실폰·전송 예산의 실제 검증.

확인 사실과 설계 판단을 구분하고, 지원하지 않는 기능을 우회하거나 이미 수신한 것처럼 표현하지 않는다. 아래 공식 자료는 2026-11-08 확인했으며 Android 버전별 조건과 문서 내 기준일을 따로 해석한다.

### 저장소 고정 근거

- **R1** [docs/CODEX_PROGRESS.md][R1]
- **R2** [docs/NAV_ACCEPTANCE.md][R2]
- **R3** [docs/RECORD_RELIABILITY.md][R3]
- **R4** [docs/AUTOMATION_FOUNDATION.md][R4]
- **R5** [docs/research/CARITDA_RECOVERY_STATUS_AUTOMATION_RESEARCH.md][R5]
- **R6** [docs/research/CARITDA_NAVIGATION_RECORD_GLEO_UX.md][R6]
- **R7** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/PleosReadClient.kt][R7]
- **R8** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt][R8]
- **R9** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/model/IssueModels.kt][R9]
- **R10** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/CaritdaService.kt][R10]
- **R11** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/SqliteIssueRepository.kt][R11]
- **R12** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/RecordMigration.kt][R12]
- **R13** [NextDrive-Pleos/app/build.gradle.kts][R13]
- **R14** [NextDrive-Pleos/app/src/main/AndroidManifest.xml][R14]
- **R15** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt][R15]
- **R16** [NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/safety/CarSafetyGate.kt][R16]

### 공식 자료

- **C1** [공모전 심사·시상][C1] — 공모전
- **C2** [공모전 일정][C2] — 공모전
- **C3** [공모전 지원·제출][C3] — 공모전
- **S0** [Vehicle SDK 소개][S0] — SDK·샘플·별도 API
- **S1** [공식 Vehicle SDK Sample][S1] — SDK·샘플·별도 API
- **S2** [Sample PublicApiPolicy 고정 소스][S2] — SDK·샘플·별도 API
- **S3** [EvBattery API][S3] — SDK·샘플·별도 API
- **S4** [Odometer API][S4] — SDK·샘플·별도 API
- **S5** [Fuel API][S5] — SDK·샘플·별도 API
- **S6** [Door API][S6] — SDK·샘플·별도 API
- **S7** [Window API][S7] — SDK·샘플·별도 API
- **S8** [Tire API][S8] — SDK·샘플·별도 API
- **S9** [Safety API][S9] — SDK·샘플·별도 API
- **S10** [Vehicle 권한][S10] — SDK·샘플·별도 API
- **S11** [Vehicle 오류 코드][S11] — SDK·샘플·별도 API
- **S12** [별도 Vehicle Data API 소개][S12] — SDK·샘플·별도 API
- **A1** [Foreground services][A1] — Android·AAOS 수명주기/전원
- **A2** [백그라운드 FGS 시작 제한][A2] — Android·AAOS 수명주기/전원
- **A3** [WorkManager persistent work][A3] — Android·AAOS 수명주기/전원
- **A4** [PeriodicWorkRequest][A4] — Android·AAOS 수명주기/전원
- **A5** [AAOS 전원 관리][A5] — Android·AAOS 수명주기/전원
- **A6** [Android15 stopped state][A6] — Android·AAOS 수명주기/전원
- **A7** [사용자의 FGS 앱 중지][A7] — Android·AAOS 수명주기/전원
- **F1** [FCM 구조][F1] — 휴대폰 push·FCM
- **F2** [FCM 신뢰 서버][F2] — 휴대폰 push·FCM
- **F3** [Android FCM 시작·등록·권한][F3] — 휴대폰 push·FCM
- **F4** [Android 알림 권한][F4] — 휴대폰 push·FCM
- **F5** [FCM TTL·수락/전달][F5] — 휴대폰 push·FCM
- **F6** [Android FCM 수신][F6] — 휴대폰 push·FCM
- **F7** [등록정보·토큰 관리][F7] — 휴대폰 push·FCM
- **F8** [FCM 오류·재시도][F8] — 휴대폰 push·FCM
- **F9** [FCM 전달 지표][F9] — 휴대폰 push·FCM
- **F10** [FCM collapsible messages][F10] — 휴대폰 push·FCM
- **I1** [Android build variants][I1] — 패키징·아이콘·시스템 UI
- **I2** [Manifest 병합][I2] — 패키징·아이콘·시스템 UI
- **I3** [Adaptive icons][I3] — 패키징·아이콘·시스템 UI
- **I4** [AOSP Automotive System UI][I4] — 패키징·아이콘·시스템 UI
- **I5** [Pleos Display Layout][I5] — 패키징·아이콘·시스템 UI
- **I6** [Pleos GNB][I6] — 패키징·아이콘·시스템 UI
- **I7** [Android dumpsys][I7] — 패키징·아이콘·시스템 UI
- **I8** [APK Analyzer][I8] — 패키징·아이콘·시스템 UI
- **O1** [현대자동차 LX3/2026 한국어 경고등 안내][O1] — 제작사 설명서

[R1]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/docs/CODEX_PROGRESS.md
[R2]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/docs/NAV_ACCEPTANCE.md
[R3]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/docs/RECORD_RELIABILITY.md
[R4]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/docs/AUTOMATION_FOUNDATION.md
[R5]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/docs/research/CARITDA_RECOVERY_STATUS_AUTOMATION_RESEARCH.md
[R6]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/docs/research/CARITDA_NAVIGATION_RECORD_GLEO_UX.md
[R7]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/PleosReadClient.kt
[R8]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt
[R9]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/model/IssueModels.kt
[R10]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/CaritdaService.kt
[R11]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/SqliteIssueRepository.kt
[R12]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/RecordMigration.kt
[R13]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/build.gradle.kts
[R14]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/AndroidManifest.xml
[R15]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
[R16]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/bb3af514994714b84eeffbe97f82e145be092d4d/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/safety/CarSafetyGate.kt
[C1]: https://appdevchallenge.auto-hmg.io/guide/judging-awards
[C2]: https://appdevchallenge.auto-hmg.io/guide/schedule
[C3]: https://appdevchallenge.auto-hmg.io/guide/submission
[S0]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
[S1]: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
[S2]: https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/959981d960ec71144accb27af5ae5d341e72a644/app/src/main/java/ai/pleos/playground/sample/vehicle/explorer/PublicApiPolicy.kt
[S3]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/entry
[S4]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry
[S5]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/entry
[S6]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry
[S7]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/entry
[S8]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/entry
[S9]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry
[S10]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
[S11]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code
[S12]: https://document.pleos.ai/api-reference/vehicle-data-api/intro
[A1]: https://developer.android.com/develop/background-work/services/fgs
[A2]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
[A3]: https://developer.android.com/develop/background-work/background-tasks/persistent
[A4]: https://developer.android.com/reference/androidx/work/PeriodicWorkRequest
[A5]: https://source.android.com/docs/automotive/power/power
[A6]: https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states
[A7]: https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
[F1]: https://firebase.google.com/docs/cloud-messaging/fcm-architecture?hl=en
[F2]: https://firebase.google.com/docs/cloud-messaging/server-environment?hl=en
[F3]: https://firebase.google.com/docs/cloud-messaging/android/get-started?hl=en
[F4]: https://developer.android.com/develop/ui/views/notifications/notification-permission
[F5]: https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan?hl=en
[F6]: https://firebase.google.com/docs/cloud-messaging/android/receive?hl=en
[F7]: https://firebase.google.com/docs/cloud-messaging/manage-tokens?hl=en
[F8]: https://firebase.google.com/docs/cloud-messaging/error-codes?hl=en
[F9]: https://firebase.google.com/docs/cloud-messaging/understand-delivery?hl=en
[F10]: https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types?hl=en
[I1]: https://developer.android.com/build/build-variants
[I2]: https://developer.android.com/build/manage-manifests
[I3]: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
[I4]: https://source.android.com/docs/automotive/hmi/system_ui
[I5]: https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout
[I6]: https://document.pleos.ai/docs/connect/guide/docs-design/components/gnb
[I7]: https://developer.android.com/tools/dumpsys
[I8]: https://developer.android.com/studio/debug/apk-analyzer
[O1]: https://ownersmanual.hyundai.com/full_webhelp/LX3/2026/ko_KR/id23C4LH00HL7.html
