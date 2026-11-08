# CARITDA: 차량 이상 회복 감지·상태 변경 자동화 연구

확인·작성일: **2026-11-08, Asia/Seoul**  
조사 시작 main: **3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3**  
게시 직전 재확인: **2026-11-08 KST, 동일 main 및 기존 16개 기준 파일 원문 일치**  
새 게시 파일: **docs/research/CARITDA_RECOVERY_STATUS_AUTOMATION_RESEARCH.md**

## 0. 결론

**B(근거가 있을 때 사용자에게 상태 확인을 제안)를 기본으로 권장한다. 단, 현재 CARITDA의 SOC·주행거리 실수신만으로 고장 회복 제안을 활성화할 근거는 없다. 네 상태의 자동 변경 허용목록은 기본 빈 목록으로 둔다.**

- ‘문제가 더는 관측되지 않음’은 특정 신호·영역·시각·관찰 조건의 사실이다. **원인 제거·실제 수리 완료·운행 안전 보증과 다르다.**
- 현재 구현에서 확인된 차량 읽기는 SOC·누적거리·연료 조회 시도다. 개발 문서는 에뮬레이터 SOC/거리 성공과 연료 미지원을 보고한다. 이번 연구에서 실제 값을 새로 수신하지 않았다.
- Door/Window 등은 공식 API 후보이며 권한·Capability·area·실제 수신·신호 의미를 추가 검증해야 한다. 타이어 현재 압력과 Safety telltales는 공식 샘플의 차단 정책이 확인됐다.
- M0는 이력/근거 구조·검증 장치·수동 확인 UX를 준비한다. M1은 공개 접근이 실제 확인된 신호에만 관찰·제안을 적용한다. M2는 특권·OEM 진단·실차·자동 전이 정책 검증이 필요한 단계다.
- 로컬 규칙 엔진이 사실과 자격을 판정하고, AI는 선택적 설명만 담당한다. Gleo나 일반 LLM은 필수 의존성이 아니다.
- 이번 작업은 **조사·설계만** 한다. 기존 코드·연구·DB·설정은 수정하지 않으며 빌드·SDK 호출·에뮬레이터 조작·사용자 시험·실차 검증을 수행하지 않았다.

### 근거 표시

- **확인 사실:** 공식 문서/샘플 소스 또는 고정 커밋 코드의 명시 내용.
- **설계 판단:** CARITDA에 적용할 정책·모델·문구·검증 기준.
- **미검증 가정:** 대상 환경의 실제 신호 지원·신뢰도·회복 효용·임계값·자동화 안전성.

아래 예시 관측·상태·시험값은 설계/검증용이며 실제 차량의 현재 상태가 아니다. CARITDA는 상표 검증 전 가칭이다.

---

## 1. 최신 코드 분석: 상태·수정·삭제·관측

필수 8개 파일 [R1]–[R8]을 읽고 RecordMigration·CarSafetyGate·Manifest [R9] [R10] [R11]도 확인했다. 저장소 링크는 조사 시작 커밋에 고정한다. 기존 내비게이션/Gleo 연구 [R3]의 당시 미구현 판단보다 최신 서비스·DB 코드가 우선한다.

### 1.1 기존 네 상태의 의미 유지

| 상태 | 유지할 의미 | 신호만으로 대신할 수 없는 확인 |
|---|---|---|
| UNRESOLVED / 미해결 | 사용자가 관리 중인 문제 | 관측 누락을 해결로 바꾸지 않음 |
| PLANNED / 점검 예정 | 점검을 계획했다는 기록 | 센서 정상화가 예약/계획을 증명하지 않음 |
| INSPECTED / 점검 완료 | 실제 점검을 했다고 확인한 기록 | 값이 좋아졌거나 OBD self-test가 끝났다는 사실과 다름 |
| RESOLVED / 해결 확인 | 사람이 문제 해결을 확인한 기록 | 앱이 고장 원인을 제거했다고 판정하는 상태가 아님 |

사용자 ‘증상이 더 느껴지지 않음’과 정비사/OEM의 ‘수리 후 검증’도 다른 확인이다. 승인 시 확인 주체·근거를 구분하며, 사용자 확인을 정비 인증으로 바꾸지 않는다.

### 1.2 구현 사실과 설계 영향

| 현재 구현 | 근거 | 새 기능에 대한 영향 |
|---|---|---|
| DB v3, 기존 issue/event/driver/draft 보존 | Repository 및 RecordMigration [R6] [R9] | 파괴적 이전·새 DB 재생성 금지 |
| EDITED는 전후 카테고리/본문/메모를 event에 남기고 revision+1 | Service.revise, Repository.revise [R5] [R6] | **현재 revision은 재발 전용 번호가 아니다** |
| RECURRENCE도 revision+1, 상태 UNRESOLVED | Service.recur [R5] | 재발 횟수는 RECURRENCE 이벤트로 구분. revision을 그대로 ‘재발 n회’로 읽지 않음 |
| 수동 STATUS는 revision을 올리지 않음 | Service.changeStatus, Repository.append | revision만으로 최신 상태 충돌을 검출할 수 없음 |
| 읽음은 issue+actor ID+revision으로 중복 방지 | Service.needsAcknowledgement·append | 회복 제안·관측 이벤트로 읽음/내용 차수를 불필요하게 초기화하지 않음 |
| 상태 변경은 확정 운전자+입력 허용 확인, 작성자 전용은 아님 | Service.requireDriver/changeStatus | 기존 공유 상태 변경 정책을 몰래 작성자 전용/무인 변경으로 바꾸지 않음 |
| 수정·논리 삭제는 원 작성자 ID 검사 | revise/deleteOwned/markDeleted | 작성자·snapshot·원래 사건 ID 불변 |
| deleted_at/deleted_by_id, 원문/event 유지 | [R6] [R9] | 기본 조회·새 회복 제안에서 제외. 자동 복원/ID 재사용 금지 |
| 삭제 복구·영구 파기 UI는 현재 없음 | RECORD_RELIABILITY [R2] | 이전 연구의 휴지통 권고가 이미 구현됐다고 설명하지 않음 |
| append/revise/delete는 각각 SQLite 트랜잭션 | [R6] | 상태·승인·연결 event도 하나의 커밋으로 확정해야 함 |

등록 원문·작성자 snapshot·발견/등록 시각은 유지한다. 기존 EDITED/DELETED/STATUS/RECURRENCE를 새로운 자동 이벤트로 재분류하거나 원문을 다시 쓰지 않는다.

### 1.3 현재 관측 저장의 강점과 부족한 증거

PleosReadClient [R7]는 버튼 요청의 readOnce이며 SOC·거리·연료 지원 확인/get 응답을 기록한다. 15초 Handler는 **타임아웃**이지 반복 수집이 아니다. onStop/release 이후 무중단 감시를 보장하지 않는다.

ObservationStore [R8]는 별도 append-only DB다. kind/raw/unit/receivedAt/source/supported/valid/session/status/detail/recordedAt/driverId를 저장한다. 실패는 raw/unit/receivedAt 없이 저장하며, mostRecent는 TEST_ONLY를 제외하고 최신 시도와 과거 유효 값을 각각 찾을 수 있다.

추가 확인이 필요한 부분:

1. receivedAt은 성공 콜백에서 **앱 시계로 찍은 시각**이다. ECU/차량의 측정 시각이 아니다.
2. getter/listener의 새 응답이 ECU의 새 측정이라는 보장은 현재 코드에 없다. 같은 캐시를 여러 번 받은 것을 연속 독립 관측으로 세지 않는다.
3. session은 readOnce마다 생성한 UUID다. 실제 운행/시동/ECU 세션이 아니며 두 조회가 같은 차량이라는 증명도 아니다.
4. 저장 DTO는 DB의 observation 행 ID를 노출하지 않는다. 차량 대응키·area·차량 측 시각·품질 상세도 현재 모델에 없다.
5. driverId는 선택 필드이고 현재 readOnce는 이를 전달하지 않는다. 관측 작성자 연결이 이미 완성됐다고 주장하지 않는다.
6. PLEOS_EMULATOR_SDK 분류는 hardware 문자열 검사다. 반대 분기가 PLEOS_VEHICLE_SDK라고 해서 실차 검증이 끝난 것은 아니다.
7. 과거 valid를 찾을 수 있다는 기능을 ‘현재 정상’으로 사용하면 안 된다. 반드시 최신 시도의 오류·지원 여부와 같이 판단한다.

개발 기록 [R1] [R2]의 최근 SOC100%/0km는 **독립 validation 앱의 에뮬레이터 검증 보고**다. 운영 앱의 과거15%/1000000km와 같은 DB/측정 흐름으로 합쳐 회복 증거를 만들지 않는다. 이번 연구는 운영 현재값을 조회하지 않았다.

---

## 2. 실제 접근성 표: SDK 문서·정책·실수신 분리

사용자가 지정한 SDK Introduction [S0]은 현재 docs 경로로 이동한다. 공식 소개는 SDK2.3.2/Service2.3.21, Sample 가이드는 SDK2.3.2/Service2.3.22를 표시한다. 이 차이와 모델별 차이가 있으므로 버전 표기 자체를 호환성/기능 성공 보증으로 삼지 않는다. [S0] [S10]

아래 권한은 **pleos.car.permission.** 접두사를 생략했다. ‘normal’은 Manifest 선언이 필요한 읽기 권한이라는 뜻이며 차량 기능 허용/수신 성공 보장은 아니다. 제어용 CONTROL_* 권한은 본 연구의 읽기 목적에 불필요하다.

### 2.1 신호·메서드·지원 단계

| 신호 | 확인한 get / Capability | Listener·area·권한 | 현재 접근 판정 |
|---|---|---|---|
| EV SOC | getEvBatteryStateOfCharge → Int? %, checkEvBatteryStateOfChargeCapabilities | register/unregisterEvBatteryStateOfCharge, area 없음, CAR_ENERGY | CARITDA typed 호출 구현과 에뮬레이터 수신 보고 있음. **SOH/수리 판정 아님** [R7] [S7] |
| 누적거리 | getCurrentDistanceDriven → Float? km, checkCurrentDistanceDrivenCapabilities | registerDistanceDriven / unregisterDistanceDriven, area 없음, CAR_DRIVE | CARITDA 단발 get 수신 보고 있음. listener는 앱에서 미구현/미검증 [R7] [S8] |
| 연료 | getFuelPercent / checkFuelPercentCapabilities를 앱이 호출 | 앱은 CAR_ENERGY, 단발 조회 | 최근 환경은 UNSUPPORTED, 값 없음. 0%로 바꾸지 않음 [R2] [R7] |
| Door 위치 | getCurrentDoorPosition(area) → Int?, checkDoorPositionCapabilities → config+area bounds | register/unregisterDoorPosition, DoorArea, CAR_DOORS normal | 문서 후보. CARITDA 권한 선언·개별 area 수신 검증 없음 [S5] [S9] |
| Door 잠금 | getDoorLockState(area) → Boolean?, checkDoorLockCapabilities → config+지원 area | register/unregisterDoorLockState, DoorArea, CAR_DOORS normal | 잠금과 닫힘은 별개. 문서 후보·실행 미검증 [S5] |
| Window 위치/잠금 | getCurrentWindowPosition / checkWindowPositionCapabilities, getCurrentWindowLockState / checkWindowLockCapabilities | registerWindowPosition / registerWindowLockState, WindowArea, CAR_WINDOWS normal | 문서 후보. 지원 area/범위·반환 의미·실수신 미검증 [S6] |
| EV 충전 상태 | getEvChargeSwitchState → Boolean?, checkEvChargeSwitchCapabilities | CAR_ENERGY normal, area 없음. 조사 목록에서 이 상태용 register는 미확인 | 문서는 current EV battery charging state로 설명. 실제 환경·정의 검증 필요. 충전 정상화≠배터리 고장 수리 [S7] |
| 저압 임계값 | getLowTirePressure(area) → Float? kPa, checkLowTirePressureCapabilities → config+wheel areas | WheelArea, CAR_SAFETY normal. checkWheelAreaCapabilities도 존재 | **현재 공기압이 아닌 critically low 임계값.** 문서 존재/차단목록 비포함, 차량 반환 미검증 [S1] [S2] |
| 현재 타이어 압력 | 샘플에 checkTirePressureCapabilities/getTirePressure 이름 존재 | register/unregisterTirePressure도 차단. 현재 공개 Tire 페이지에서 정확 시그니처/권한 미확인 | 공개 샘플 4개 모두 blocked. 현재 앱 사용 가능으로 분류하지 않음 [S1] [S11] |
| 경고등 | checkTelltalesCapabilities / getTelltalesInfo | register/unregisterTelltales, TelltaleInfoListener; area 없음 | 네 메서드 샘플 blocked. PRIVATE_CAR_SAFETY 관련 특권 경계 [S3] [S4] [S11] |
| TPMS 경고 | TelltaleType.TPMS, TelltaleData type/state/color/priority/warningCount 모델 | nullable Telltales 업데이트 | enum 존재가 저압 Boolean/개별 바퀴 압력의 공개 수신 증거 아님 [S3] |
| DTC/OEM 진단 | 조사 범위에서 구체 read/clear·Capability API 미확인 | 권한/area/listener 미확인 | SDK 전체에 없다는 증명이 아니라 **현재 확보한 경로 없음**. Vehicle Data API와 합치지 않음 |

Door/Window/SOC/거리 공개 메서드는 차단 목록에 없다는 이유만으로 샘플 수록·실차 사용 승인을 확정하지 않는다. 현재 앱 Manifest [R11]에는 CAR_ENERGY/CAR_DRIVE만 있다. Door/Window용 선언·호출은 후속 구현과 검증 대상이다.

### 2.2 영역·값·Listener의 해석

- DoorArea는 DoorValue의 묶음이며 ROW_1/2/3_LEFT/RIGHT, HOOD, REAR 등이 문서에 있다. WindowArea도 앞/뒤 유리·각 열·ROOF_TOP 등의 상수가 있다. **enum에 있어도 그 차량이 지원한다는 뜻은 아니다.**
- 위치 값은 차량별 AreaBounds로 해석한다. Sample의 Door 0..1 예시를 모든 차량의 최대값=1로 하드코딩하지 않는다. deprecated한 config 없는 Capability overload 대신 현재 정의를 확인한다.
- Sample은 전체 Supported여도 특정 area가 미지원 오류를 낼 수 있음을 설명한다. Capability accessType은 NONE/READ/WRITE/READ_WRITE이며 읽기 허용과 해당 area 성공을 따로 확인한다. [S10] [S13]
- 확인한 getter/listener는 nullable 값 중심이며 차량 측 측정 timestamp/TTL/ECU 품질 필드가 함께 온다는 근거는 확보하지 못했다.
- SDK initialized, listener 등록/해제 Complete, Receiving vehicle updates 표시는 실제 Service 응답·실제 값 수신과 다르다. 콜백의 값/실패와 관측 저장을 확인한다. [S10]
- set 완료 콜백의 요청값을 실제 상태로 취급하지 않는다. 이 기능은 읽기 전용이며 상태를 바꾸기 위해 문/창문/충전을 제어하지 않는다.
- 일반 Callback 안내의 area 없는 Door 등록 예시는 개별 명세와 다를 수 있다. 실제 AAR 타입·공식 Sample과 맞춰 컴파일/실행해야 한다. [S12]

### 2.3 PublicApiPolicy와 특권 경계

공식 Sample 고정 커밋 **959981d960ec71144accb27af5ae5d341e72a644**의 blockedApis: [S11]

~~~text
CarInfo.getCapabilityInfo
Safety.checkTelltalesCapabilities
Safety.getTelltalesInfo
Safety.registerTelltales
Safety.unregisterTelltales
Tire.checkTirePressureCapabilities
Tire.getTirePressure
Tire.registerTirePressure
Tire.unregisterTirePressure
~~~

정책은 Deprecated 제외도 검사한다. blocked에 없다는 사실만으로 모든 공개 정책 통과·실행까지 보장하지 않는다. 샘플은 PRIVATE_* 권한을 선언하지 않으며 CAR_TIRES 선언이 차단된 압력 API를 해제하는 근거도 아니다.

특히 getTelltalesInfo 원문은 어노테이션 CAR_SAFETY와 Permission 설명 PRIVATE_CAR_SAFETY(signature|privileged)가 불일치한다. 일반 권한만 추가하면 된다고 해석하지 않는다. 공식 확인/정책 변경 전 사용을 보류한다. [S4]

DTC 조사 범위는 Introduction·도메인 목록·Tire/Safety·telltale 모델/상수/리스너·권한·공식 샘플 및 DTC/OEM 진단 키워드 검색이다. 비공개/OEM 별도 경로의 존재 여부까지 판단하지 않았다.

---

## 3. 별도 Vehicle Data API: 같은 데이터 통로가 아니다

공식 get-vehicle-status [V1]는 **GET api.pleos.ai/v1/vehicles/{vin}/status**의 HTTP API다. Bearer access token·Brand·VIN이 필요하다. Connect SDK의 Manifest 권한을 얻었다고 이 토큰/차량 접근 권한이 생기지 않는다. 이번에는 문서만 읽었으며 실제 차량 endpoint를 호출하지 않았다.

| 공개 응답 항목 | 확인 내용 | 회복 분석 시 주의 |
|---|---|---|
| doors/hood/trunk/sunroof | open/closed/locked/invalid 등 필드별 상태 | 잠금/닫힘 의미를 SDK와 임의 통합하지 않음. 일반 window 전체가 지원된다고 추정하지 않음 |
| tire | 전/후·좌/우 압력, 0~254 valid / 255 invalid | 255는 값 아님. 단위별 decoding을 적용해야 함 |
| warnings | 워셔액·연료·오일·개별 저압 등 열거된 경고 코드 | 목록이 경고등 전체를 완전 대표한다는 보장 없음. 누락/부분 응답/미지원≠소등 |
| data.timestamp | 차량 데이터 발생 시각 UTC | 응답 meta.timestamp와 구분. 응답이 방금 왔다고 차량 데이터가 최신인 것은 아님 |

압력 변환은 이 API 문서에서 psi는 그대로, kpa는 raw×5, bar는 raw÷10이다. **이 규칙을 SDK의 Float kPa getter에 복사하지 않는다.** 원본/단위/decoder 버전을 보존한다. 예시 JSON은 실제 사용자의 현재 수신값이 아니다.

CARITDA의 원격 토큰·소유/공유 동의·범위·쿼터·갱신 보장·실차 응답은 미검증이다. warnings 예시에 MIL/DTC 전체가 포함됐다고 추정하지 않는다. 독립 텔레매틱스 결과와 에뮬레이터 SDK 값을 같은 연속 시계열로 섞지 않는다.

---

## 4. 문제별 회복 가능성: A~G 비교

아래의 ‘가능’은 조건 충족 후 설계상 제안 가능성이지 현재 CARITDA에서 신호가 수신됐다는 뜻이 아니다.

| 사례 | 관측 사실 | 회복 추정에 필요한 조건·추가 신호 | 수리 완료를 증명 못 하는 이유 | 상태 제안/자동화 판단 | 사용자 문구 예 |
|---|---|---|---|---|---|
| A. 저압 경고 소멸 | 같은 바퀴/차량의 유효 경고가 이전 활성→최신 비활성으로 관측 | 실제 압력·냉간 권장치/온도·TPMS 고장 여부·신선도·지원 범위. 현재 공개 접근은 차단/미검증 | 온도 상승만으로 경고가 꺼질 수 있고 누설/손상 원인은 남을 수 있음 [N1] | M2 접근·의미 검증 후 B. 자동 RESOLVED 금지 | ‘최근 조회에서는 이전 저압 경고가 관측되지 않았어요. 실제 공기압과 증상을 확인해 주세요.’ |
| B. 경고등 소등 | 같은 경고의 명시적 비활성 응답 | 점화/가동 조건·DTC 현재/과거/삭제·readiness·OEM 후검증 등. 각 경고 의미 분리 | DTC 삭제/전원 상태/간헐 조건이 소등을 만들 수 있음. not ready는 unknown이지 정상/실패 확정 아님 [N2] [N3] | 등 종류별 B 검토. MIL off만으로 해결/점검 완료 자동변경 금지 | ‘이전 경고가 최근 조회에서 확인되지 않았어요. 수리 완료는 확인되지 않았어요.’ |
| C. 문/창문 | 해당 area의 열림→닫힘/잠금 등 실제 값 변화 | 사용자 기록과 신호의 정확한 대응, 지원 area·범위·시각·연속성 | 문 닫힘은 당시 장치 상태. 래치/센서/모터 수리나 간헐 고장 소멸을 증명하지 않음 [N4] | M1 조건 일치 안내/B 후보. 고장 기록의 자동 해결 아님 | ‘최근 조회에서 앞왼쪽 문이 닫힌 상태였어요. 기록한 문제도 해결됐는지 확인해 주세요.’ |
| D. 수동 소음/진동, 대응 센서 없음 | 사람이 기록했고 앱에 관련 관측이 없음 | 실제 증상 확인·발생 조건·정비 결과, 필요 시 적합한 센서/진단 | 미수집은 미발생이 아님. SOC/거리로 소음 회복을 알 수 없음 [N5] | 센서 회복 제안 없음. 사용자 수동 확인만 | ‘이 증상을 확인할 센서 정보가 없어요. 직접 확인한 상태를 기록해 주세요.’ |
| E. 전후 데이터 부족 | 이전 비정상 또는 최신 비교값이 없음 | 검증된 baseline·동일 신호/area·차량과 시간 정보 | 첫 정상처럼 보이는 한 점은 회복 전후 비교가 아님 | INSUFFICIENT, B/C 없음 | ‘전후를 비교할 정보가 부족해 회복 여부를 알 수 없어요.’ |
| F. 실패/오래된 값 | 최신 오류·미지원·누락, 과거 valid는 존재할 수 있음 | 새로 받은 유효·지원·신선한 신호, 차량 측 갱신 의미 | 과거 값/응답 시간만으로 현재 정상 추정 불가 | UNKNOWN/STALE, 제안 만료/수락 차단 | ‘최신 상태를 확인하지 못했어요. 이전 값으로 해결 여부를 판단하지 않아요.’ |
| G. 정비 후 재발 관찰 | 기록된 조건·관찰 구간에서 동일 신호/증상이 있었거나 없었음 | 수리 내역·원래 발생 조건·관찰 공백·신호 범위·사람 확인 | 재현 못함과 원인 수리는 다름. 관찰하지 않은 기간은 정상 아님 [N5] | ‘관찰 구간 미관측’ 표시. 재발은 같은 사건에 연결, 무인 해결/재개 금지 | ‘정비 후 확인한 구간에서는 같은 신호가 관측되지 않았어요. 확인하지 못한 구간은 제외했어요.’ |

위 문구는 **실제 해당 조건이 있을 때만** 표시한다. 현재 미구현 신호에 저압 소멸·경고 회복 예시를 실제 알림처럼 넣지 않는다. ‘최신 조회에서 안 보임’도 해당 필드가 명시적·완전하게 보고된 경우에만 사용한다.

### 근거의 범위

- NHTSA [N1]는 추운 아침의 저압등이 타이어 가열/외기온 상승으로 꺼질 수 있어도 압력·육안 확인이 필요하다고 설명한다. 특정 Pleos 차량의 갱신주기/학습 조건은 제시하지 않는다.
- Ohio EPA [N2]와 연방 EPA의 2001년 지침 [N3]은 DTC 삭제·배터리 분리·readiness 미완료를 다룬다. 미국 배출가스 검사 맥락을 모든 차량 고장의 규정으로 확대하지 않는다.
- Ford Ranger 매뉴얼 [N4]은 점화 ON과 도어 미닫힘 경고 조건을 설명한다. 점화 OFF의 소등을 문 닫힘으로 읽지 않는다. 모든 제조사 센서 정확도를 보장하는 자료가 아니다.
- GM 서비스 공보 [N5]는 간헐 증상을 재현하지 못하면 발생 조건과 추가 자료를 확보하고 ‘재현하지 못함’을 알리라고 한다. 보편적인 N회/시간의 수리 완료 기준은 제공하지 않는다.

---

## 5. 회복 규칙의 자격과 데이터 안전

### 5.1 용어와 별도 판단 축

- **관측 정상화:** 허용된 특정 신호가 정책상 목표 상태로 관측됨.
- **더는 관측되지 않음:** 해당 관찰 범위에서 이전 신호가 명시적으로 미검출. 차량 전체 정상 의미 아님.
- **회복 가능성:** 앞의 사실이 사용자 문제와 관련 있을 수 있다는 조건부 제안.
- **사용자 해결 확인:** 사람이 실제 증상/점검 경험에 따라 상태를 선택한 기록.
- **수리 완료 검증:** 정비·OEM 후검증 등 별도 근거. 관측 정상화/사용자 선택과 동일하지 않음.

네 issue 상태와 별개로 관측 판단을 UNKNOWN / ABNORMAL_OBSERVED / NO_LONGER_OBSERVED / STALE / INSUFFICIENT / REAPPEARED 등으로 관리하는 것을 제안한다. 이름은 새 내부 모델 예시이며 SDK enum이 아니다.

### 5.2 회복 후보가 되기 위한 모두 충족 조건

1. 미삭제 issue와 해당 내용 revision의 명시적 신호 연결이 있다. 자유 문장에서 AI가 추측한 연결만으로 쓰지 않는다.
2. 차종/차량 대응키·환경·source·kind·area·단위·decoder가 일치하고 허용 정책이 있다.
3. 과거의 실제 비정상 근거와 비교 가능한 최신 근거가 모두 있다.
4. 최신 시도 자체가 성공·지원·유효하다. 과거 성공을 골라 최신 오류를 가리지 않는다.
5. 신호의 차량 측 갱신 의미/품질/시각을 검증했다. 콜백 시각만 새로 찍힌 값을 독립 샘플로 단정하지 않는다.
6. 정책이 요구한 연속 유효 관측·간격·관찰창·운행/환경 조건을 충족한다.
7. 관련 경고 필드가 완전하게 보고됐다. 배열 누락/구독 중단/미지원/null을 ‘경고 없음’으로 변환하지 않는다.
8. issue의 최신 상태/버전/삭제·사용자 거절/제안 만료를 재확인한다.

문 잠금 불량 문제에 단순 닫힘을 연결하거나, 엔진 소음에 SOC 상승을 연결하지 않는다. 장치 상태를 고장 판정으로 바꾸지 않는다.

### 5.3 연속 횟수와 신선도

- 모든 차량에 통하는 ‘정상 3회면 해결’, ‘10분 무재발이면 수리’ 규칙은 채택하지 않는다.
- N·최소 간격·최대 공백·freshness·hysteresis는 **신호/차량/정책 버전별 검증 값**이어야 한다. 미정이면 정책을 비활성화한다.
- 시험 fixture에 N=3을 쓰더라도 테스트용 경계값일 뿐 제품 안전 기준이 아니다. 중복 callback·같은 캐시 재수신을 3회로 세지 않는다.
- 오류·누락·지원/area 변경·환경 변경·연결 끊김이 끼면 연속성은 기본 리셋/보류한다. 이전 비정상 사실은 삭제하지 않는다.
- receivedAt(앱 수신), sourceMeasuredAt(있는 경우 실제 측정), recordedAt(DB 기록), evaluatedAt(판정)을 구분한다. 알 수 없는 시각은 null/unknown으로 둔다.
- 앱의 기존 ‘10분 오래됨’ UI 표시는 회복 판정 TTL로 자동 전용하지 않는다.
- 시계 역행·미래 시각·재부팅·원격 data.timestamp와 응답 meta.timestamp 차이를 처리한다. 로컬 간격에는 monotonic clock을 사용할 수 있지만 부팅을 넘는 측정 신선도를 대신하지 못한다.

---

## 6. A/B/C 상태 처리 비교

| 기준 | A: 기록만 남김 | B: 사용자 상태 확인 제안 | C: 자동 상태 변경 |
|---|---|---|---|
| 정확성 | 사실/미확인 범위에 한정하면 통제 쉬움 | 관측 관련성과 사람의 실제 확인을 결합 | 잘못된 신호-문제 연결이 바로 오완료로 이어짐 |
| 편의 | 사용자가 직접 이력을 읽고 상태 변경 | 근거·다음 행동을 명확히 제시 | 클릭은 줄지만 잘못된 완료가 안 보일 위험 |
| 오완료 방지 | 상태는 불변 | 제안만으로는 불변, 명시 승인 필요 | 별도 허용 전이·권한·검증 없으면 위험 |
| 책임/출처 | 규칙 관측과 사용자 상태 분리 | 규칙이 제안, 선택된 운전자가 승인 | 시스템 변경 이유·정책·근거와 책임 범위를 더 엄격히 기록 |
| 복구 | 원관측 보존, 새 판단 추가 | 거절/나중에/수동 수정 가능 | 되돌림·경합·만료·새 경고 처리까지 필요 |
| 구현 | 낮음~중간, 신호 자격 검증은 여전히 필요 | 중간, UX·원자 승인·충돌 검사 필요 | 높음, 실차/운영 검증과 감시·정책 관리 필요 |
| 권고 | M0 기반 및 shadow mode | **기본 목표. 실제 근거 확보 후 활성화** | **현재 네 상태에서는 비활성** |

### 6.1 자동 변경의 정확한 가능 범위

현재 허용목록은 **빈 목록**이다. 센서 소등만으로 PLANNED/INSPECTED/RESOLVED를 쓰지 않는다. UNRESOLVED로의 자동 재개도 현재는 하지 않는다.

향후 검증 가능한 자동화 후보는 우선 **기계 관측 episode의 시작/종료·신호 정상화 플래그**다. 이는 사람의 네 상태와 별도라 수리 완료로 오인하지 않게 한다.

M2에서 신뢰할 수 있는 OEM/정비 업무 완료 이벤트와 상태 의미가 정확히 대응하고, 사용자 사전 동의·실차 검증·허용 전이·되돌림 정책이 마련되면 일부 상태 전이를 별도 재검토할 수 있다. 그때도 진단 self-test 완료나 정비 접수/결제만으로 실제 수리 해결을 가정하지 않는다. **RESOLVED는 사람의 확인을 유지하는 방향을 우선 권장한다.**

기술적으로 enum 값을 쓰는 코드가 가능하다는 사실과 그 상태 변경이 정당하다는 증거는 별개다.

---

## 7. 권장 데이터 흐름

~~~text
허용 SDK/별도 승인 API → raw Observation 먼저 append·commit
→ source/차량/area/단위/시각/최신 오류 검증
→ 명시 Issue-Signal Binding + 버전 있는 로컬 규칙
→ 비교 가능한 이전 비정상과 최신 유효 관찰 평가
→ 관측 사실/부족/회복 후보를 새 구조화 이력으로 저장
→ B의 제안 (issue 상태는 그대로)
→ 정차·운전자·현재 상태·버전·근거·만료 재검사
→ 사용자 승인 시 같은 issue DB 트랜잭션에서
   승인 기록 + 기존 STATUS event + issue.status + 제안 종결
~~~

- 관측 저장이 실패하면 그 값을 이미 보존된 근거처럼 제안하지 않는다.
- 두 DB는 현재 별도 SQLiteOpenHelper다. 둘의 append와 상태변경이 자동으로 한 원자 트랜잭션이 되는 것으로 설명하지 않는다.
- 원관측은 먼저 확정하고, issue DB의 판정/승인/상태 event가 해당 immutable 관측을 참조한다. 상태 CAS 실패 시 관측을 지워 롤백하지 않는다.
- **관측과 승인 간의 경합:** 미래 구현에서는 관련 stream(차량/source/kind/area)의 관측 접수→raw 확정→최신 시도·evidenceEpoch 갱신/옛 제안 무효화와 제안 승인을 하나의 직렬화 조정기/일관된 lock 순서로 통과시킨다. raw를 쓰는 모든 경로가 참여해야 한다. 승인에는 issue state token뿐 아니라 관련 evidenceEpoch·정책 버전·유효기간도 비교한다. raw는 새로 확정됐는데 watermark가 아직 갱신되지 않은 중간에 승인하지 않는다.
- 비정상/최신 실패/누락이 승인보다 먼저 처리되면 제안을 무효화하고 수락을 거부한다. 승인 커밋이 먼저 선형화된 뒤 접수된 값은 이후 재등장/미확인 사실로 추가한다. 물리 차량의 실시간 원자 snapshot이나 미래 정상 보장을 주장하지 않는다.
- 두 DB 사이 실패/재시작·다중 writer로 조정 순서/최신 watermark를 신뢰할 수 없으면 승인을 잠그고, 시작 시 pending을 재검증/만료한 뒤 raw 최신 시도와 커서를 대조한다. 복구 완료 전 새 근거로 수락하지 않는다. 독립 프로세스 writer까지 같은 경계를 보장하지 못하면 기능을 비활성화한다. 별도 수동 상태 선택은 기존 정책을 유지하며 무효한 제안의 승인으로 기록하지 않는다.
- 관측 행 ID가 현재 DTO에 없으므로 후속 구현에서는 읽기 projection/참조 모델로 stable observationRef를 제공한다. 필요하면 최소 근거 snapshot+hash를 별도 보존하되 원관측을 바꾸지 않는다.
- 연결은 로컬 store 식별자+row ID 등 재현 가능한 참조로 설계한다. 다른 앱/백업/차량의 행 번호가 같은 것을 같은 관측으로 오인하지 않는다.

---

## 8. 이벤트·상태 변경 이력 모델

아래는 **설계 모델**이며 지금 DB에 존재하는 열/enum/API라는 뜻이 아니다.

### 8.1 기존 모델과 병행할 구조

| 논리 객체 | 최소 정보 | 보존 원칙 |
|---|---|---|
| IssueSignalBinding | issueId, boundRevision, 신호/area, 차량 대응키, 의미·연결 확인자, 정책 버전 | 본문 수정/재발/차량 대응 변경 시 재검토 |
| EvidenceSet | immutable observationRefs, evidenceEpoch/latestAttemptRef, 원본/단위/정규화값/decoder, source, 차량/area, 각 시각, 품질·누락·시도 결과 | 이전 값 덮어쓰기 금지, 시험/실차 분리 |
| RecoveryEvaluation | evaluationId, issueId/revision, 상태 판단, ruleId/version, 근거·거절 사유, evaluatedAt, episodeId | 사실/부족/추정 구분, 검증 안 된 확률 표시 금지 |
| RecoveryProposal | proposalId, fromStatus, proposedStatus, expected record/state token와 evidenceEpoch, evidence fingerprint, 만료, pending/accepted/rejected/snoozed/expired 등 | 같은 근거로 중복 제안·반복 강요 방지 |
| RecoveryEvent | origin, actorType, actorDriverId(사람만), reasonCode, rule/evidence/proposalRef, before/after, time | 시스템과 선택된 사람을 구분 |

현재 IssueEvent.actor는 Driver로 필수이고 actor_id는 drivers FK다. 시스템 이벤트를 운전자 A/B로 위장하거나 등록되지 않은 SYSTEM ID를 넣지 않는다. 우선 별도 recovery 이력/sidecar를 두고, 사람 승인 시 기존 STATUS event와 구조화 연결을 같은 DB 트랜잭션으로 저장하는 방식을 권장한다.

기존 EventKind.valueOf 소비 코드가 있으므로 새 enum 문자열만 DB에 넣으면 호환성 문제가 생긴다. reader·migration·rollback 검증 없이 기존 events에 새 종류를 쓰지 않는다. 과거 출처가 불명확한 event는 LEGACY_UNKNOWN으로 구분하고 임의로 자동/사용자 승인으로 재분류하지 않는다.

### 8.2 출처를 다섯 가지로 명확하게

| origin 제안 | 기록 내용 | 네 상태 변경 |
|---|---|---|
| SIGNAL_OBSERVATION / SYSTEM | 신호 소멸·재등장·미확인 사실 | 없음 |
| RULE_PROPOSAL / SYSTEM | 관련 근거와 상태 확인 제안 | 없음 |
| USER_ACCEPTED_PROPOSAL / USER | 제안을 본 사람이 승인, 현재 선택 이름 ID·snapshot | 검증 후 명시 선택한 상태로 |
| USER_MANUAL_CHANGE / USER | 제안과 무관한 수동 상태 선택 | 기존 허용 정책으로 |
| POLICY_AUTO_CHANGE / SYSTEM | 향후 별도 승인 정책의 자동 변경 사유 | 현재 비활성, 정책 허용 시에만 |

거절·나중에·만료·충돌·재등장도 별도 사건으로 기록한다. 선택된 이름은 실제 본인 인증을 증명하지 않는다. 상태를 바꾼 사람과 원 작성자를 혼동하지 않는다.

### 8.3 충돌·중복·수동 우선

- 현재 수동 STATUS/삭제는 revision을 올리지 않으므로 **revision 단독 검사로 충분하지 않다**. fromStatus·deletedAt·최신 event sequence 또는 새 monotonic stateVersion을 트랜잭션 안에서 함께 검사한다. updatedAt은 동일 ms/시계 변경이 가능해 단독 토큰으로 삼지 않는다.
- 사용자가 상태/본문/재발·조치 내용을 바꾸면 이전 제안을 만료시키거나 재평가한다. 최신 사람 결정을 자동으로 덮어쓰지 않는다.
- 승인과 STATUS event와 제안 종결은 하나의 원자 커밋이다. changeStatus 후 나중에 승인 행을 쓰는 두 단계로 끝내지 않는다.
- 같은 승인/자동 이벤트는 operation ID·proposal ID로 중복 방지한다. 같은 issue/episode/policy에 활성 제안 한 개를 기본으로 한다.
- 사용자가 거절하면 상태는 그대로, 같은 근거의 반복 제안을 막는다. 나중에는 정해진 재알림 정책을 적용하되 주행 중 팝업을 만들지 않는다.
- 삭제되면 pending 제안을 취소하고 승인·자동 변경·새 관찰 연결을 막는다. 삭제 이력과 원문을 자동 복원하지 않는다.
- 재등장 시 같은 issueId에 REAPPEARED 사실을 연결한다. 현재는 서비스의 recur를 무인 호출하지 않는다. 사람이 ‘다시 발생’을 선택하면 기존 RECURRENCE/UNRESOLVED·읽음 규칙을 따른다.
- 과거 read acknowledgement·작성자 snapshot·EDITED/DELETED/조치 event를 새 근거에 맞춰 다시 쓰지 않는다. 이력은 추가하고 현재 해석을 구분한다.

---

## 9. 규칙·AI·사용자·Gleo의 역할

| 계층 | 허용 역할 | 금지 역할 |
|---|---|---|
| 로컬 규칙 | 지원/유효/최신/area/동일 차량/연속성·정책 판정, 구조화 이유 | 없던 센서값·고장 원인·수리 완료 생성 |
| 선택 AI | 선택 기록과 허용 근거의 관련성 후보·쉬운 설명·확인 질문 | AI 관련성만으로 binding 확정, 숫자 보간, state 쓰기·진단/운행 안전 확정 |
| 사용자 | 실제 증상·점검 경험 확인, 상태 승인/거절·수동 변경 | 이름 선택을 인증된 정비사 서명으로 표현하지 않음 |
| Gleo | 나중에 승인된 음성 안내/읽기 진입을 검토할 수 있음 | 회복 판정의 필수 엔진이나 자유 추론 API로 가정하지 않음 |

M0/M1은 **규칙+로컬 문구 템플릿으로 충분히 시작할 수 있다**. Gleo 미연동이어도 저장·판정 근거·승인 기능은 독립 작동하도록 설계한다. 이전 Gleo 연구 [R3]가 확인한 음성→앱 Action→Callback과 앱발 독립 LLM 상담은 다른 방향이다. 이번 연구가 새 Gleo 호출 지원을 확인한 것은 아니다.

AI를 사용한다면 evidence ID별 사실만 입력하고 근거 없는 출력은 버린다. 출력이 실패해도 규칙 결과와 상태는 변하지 않는다. ‘회복 확률95%’처럼 검증되지 않은 수치를 표시하지 않는다. 개인 이름/VIN/위치/전체 기록·원본 세션은 외부 기본 전송에서 제외하고 실제 처리 정책과 항목·목적에 대한 동의를 먼저 받는다.

---

## 10. 화면·알림·한국어 문구

다음은 자체 작성한 설계 제안이다. 실제 조건이 없으면 회복 문구를 렌더링하지 않는다. 가짜 경고 소멸 데이터를 시연용 운영 기록에 넣지 않는다.

### 10.1 M0: 관련 근거가 없는 현재 문제

~~~text
문제 상세
소음 / 가속할 때 앞쪽에서 소리
현재 상태: 미해결

이 증상의 회복 여부를 판단할 센서 정보가 없어요.
직접 확인한 상태를 기록해 주세요.
[상태 변경] [점검·조치 메모]
~~~

‘회복을 감시 중’이라고 쓰지 않는다. 단발 조회만 하는 앱이 계속 수집 중이라는 인상을 주지 않는다.

### 10.2 M1 이후: 검증된 사실이 있을 때만 제안

~~~text
상태를 확인해 주세요
이전에 나타났던 경고가 최근 조회에서는 관측되지 않았어요.
확인한 항목·위치 / 관측한 때 / 출처
실제 수리 완료를 확인한 것은 아니에요.

[직접 상태 확인] [아직 문제가 있어요] [나중에]
현재 미해결 상태는 아직 바뀌지 않았어요.
~~~

[직접 상태 확인]은 기존 상태 선택으로 연결한다. ‘점검받았어요’는 INSPECTED, ‘문제 해결을 확인했어요’는 RESOLVED의 사용자 선택으로 구분하며 확정 전 의미를 알려준다. 최초 제안 버튼 한 번으로 자동 해결하지 않는다.

승인 후 문구는 선택 상태에 맞춘다. **INSPECTED: ‘운전자가 점검 완료로 상태를 변경했습니다.’ / RESOLVED: ‘운전자가 문제 해결을 확인하여 상태를 변경했습니다.’** 나머지는 ‘운전자가 미해결/점검 예정으로 상태를 변경했습니다’처럼 실제 선택을 그대로 표시한다. 확인한 이름·시각·이전/새 상태·연결 근거를 이력에 남긴다. 사람의 확인 문장을 규칙 단독 변경에 쓰지 않는다.

### 10.3 이력과 실패

~~~text
확인 이력
차량 관측: 지정 신호가 최근 조회에서 관측되지 않음
앱 제안: 상태 확인 요청 / 상태 불변
민수: 직접 확인 후 미해결 → 해결 확인
이후 관측: 동일 신호 재등장 / 사용자 확인 필요
~~~

- 최신 실패/오래됨: ‘최신 상태를 확인하지 못했어요. 이전 값으로 해결 여부를 판단하지 않아요.’
- 기록 변경/삭제: ‘기록이 달라졌어요. 현재 내용과 상태를 먼저 확인해 주세요.’ / ‘삭제된 기록에는 상태 제안을 적용하지 않아요.’
- 거절: ‘현재 상태를 유지했어요. 같은 근거로 다시 묻지 않을게요.’ 실제 중복 억제가 구현됐을 때만 사용.
- 저장 실패: ‘상태를 저장하지 못했어요. 변경 전 상태가 유지됐는지 다시 확인해 주세요.’ 원자성 확인 전 성공/보존을 보장하지 않음.

현재 DO 미선언·UXR UNKNOWN/RESTRICTED에서는 긴 설명/승인·상태 쓰기를 열지 않는다. 초안·pending 제안을 보존하고 허용 뒤 명시 재개한다. OS 차단을 overlay/알림/음성으로 우회하지 않는다. requiresDO=false와 BASELINE은 기어 P 인증과 다르다. [R10] [R11] [P1] [P2]

---

## 11. 오탐·미탐과 안전 대응

| 위험 | 결과 | 기본 대응 |
|---|---|---|
| 캐시/오래된 값/최신 오류 숨김 | 거짓 회복 | latestAttempt와 source freshness 함께 검사, 제안 만료 |
| 누락 warnings·null·구독 단절 | 거짓 소등 | unknown, 연속성 중단, 상태 불변 |
| 온도·점화 OFF·DTC clear | 소등을 수리로 오인 | 환경/진단 조건 분리, 사람 확인 |
| 잘못된 issue-signal 연결 | 다른 문제 오완료 | 명시 binding·area·차량 대응 검증, AI 단독 금지 |
| 불연속 에뮬레이터/차량/검증 앱 자료 | 가짜 전후 회복 | 환경·store·차량·episode 분리, reset |
| 간헐 고장 재발을 놓침 | 잘못된 안심 | 관찰 공백 표시, ‘현재 범위 미관측’, 실제 수리 보증 없음 |
| 사용자가 수동 변경한 뒤 오래된 승인 | 사람 결정 덮어쓰기 | state token/CAS·삭제 검사, 만료/재검토 |
| 이벤트 재전송·중복 callback | 중복 제안/승인 | stable evidence ref·idempotency key·활성 제안 제약 |
| AI의 부정확한 설명 | 진단/운행 오판 | 제한된 구조 출력·템플릿 fallback, state 경로와 분리 |
| 실수로 승인 | 원치 않는 완료 | 의미 있는 확인·이력·명시 수동 정정. 과거 event 삭제로 되돌리지 않음 |

오탐/미탐의 기준도 분리한다. ‘신호 소멸 탐지’의 정답과 ‘실제 수리 완료’의 정답은 다른 라벨이다. 사용자의 해결 선택만을 정비 진실의 gold standard로 삼지 않는다. 실제 정책 검증에는 차종·조건·관찰 공백·진단/정비 근거를 기록한 별도 데이터가 필요하다.

---

## 12. M0/M1/M2와 권장 순서

| 단계 | 현재 구현 가능한/후속 후보 | 활성화하지 않을 것 |
|---|---|---|
| **M0: 현재 앱 기반** | 새 수동 상태 출처·근거 구조·동시성/중복/삭제 보호 설계, 로컬 테스트 규칙 harness, 센서 부족 안내, 실제 수동 확인 UX | SOC/거리로 고장 회복 제안, 가짜 경고, 실제 네 상태 자동 변경 |
| **M1: 공개 접근 실수신 확인 후** | Door/Window 등 허용 신호의 정확한 state/area/freshness 확인, shadow 관찰→관련성 검증→B 제안, 선택적 로컬 설명 | 장치 닫힘을 수리로 확정, 임계값 미검증 자동 전이, listener 등록만으로 수신 성공 |
| **M2: 추가 권한·진단·실차** | 타이어 실압·telltale·DTC/OEM 경로 확보, 정비/후검증 근거, 별도 승인된 자동 전이 정책의 검토 | 특권 우회·public 차단 해제 가정·실차 미검증 자동 해결 |

M0의 B UI/이벤트 기반은 만들 수 있어도, **현재 없는 실제 회복 신호에 대한 제안 기능이 완성되는 것은 아니다**. SOC 상승은 충전량 변화 사실 또는 별도 편의 안내 후보이지 배터리/소음 고장 회복 근거가 아니다.

권장 순서: 기존 데이터 회귀 고정 → 새 출처·근거/승인 모델 → unknown/중복/충돌 검사 → 격리 fixture → 허용 신호 단발 실수신 → shadow mode → 사용자 확인 B → 필요 시 실차/추가 권한 M2. 자동 C는 별도 안전·정책 승인까지 꺼 둔다.

---

## 13. 에뮬레이터 검증 시나리오

**이번 연구에서 실행하지 않았다.** 향후에도 운영 앱/DB 대신 현행 validation 빌드와 격리 데이터를 사용한다. [R2]

| 시험 층 | 방법 | 증명하는 것 / 못 하는 것 |
|---|---|---|
| 로컬 unit/DB fixture | 합성 raw/오류/시각/변경 event | 규칙·원자성·중복·이전 보존. 실제 SDK 수신 증거 아님 |
| validation+공식 Virtual Console | 실제 지원된 속성을 문서에 맞게 바꾸고 get/listener 콜백 저장 | 해당 AVD/SDK 경로의 동작. 실차·고장 수리 증거 아님 |
| 실차/OEM 검증 | 승인된 읽기·진단·정비 후검증 프로토콜 | 해당 모델·조건의 품질. 다른 차량 전체로 자동 일반화 불가 |

시나리오:

1. 알려진 합성 비정상→정상 후보 N회, N-1회, 중복 callback, 같은 sampleId 재수신.
2. 중간 TIMEOUT/INVALID/권한 거부/area 미지원/서비스 끊김/null/부분 warnings 응답.
3. 과거 유효 이후 연속 실패 100회 이상: 과거 success를 최신 정상으로 쓰지 않음.
4. 과거 비정상 없는 첫 값, source timestamp 없는 캐시, 앱 시각만 새 값, 미래/역행 시각.
5. 다른 바퀴/문 area, 잠금↔닫힘 혼동, 단위 변환·255 sentinel·범위 밖.
6. source TEST_ONLY/EMULATOR/VEHICLE/원격 API/다른 validation UID·store/차량 대응 변경.
7. proposal 열린 뒤 manual STATUS/EDITED/RECURRENCE/DELETED 및 승인 동시 요청.
8. 승인 중 DB 실패/앱 종료: 승인·STATUS·issue 상태가 전부 반영되거나 모두 미반영.
9. 거절/나중에/만료 후 동일 근거 재전송, 재등장 후 같은 issue 연결.
10. 실제 SDK에서는 우선 현재 SOC/거리 성공·연료 미지원을 회귀 확인하되 회복으로 변환하지 않음. Door/Window는 Capability/area/실수신 확인 후에만 시험.
11. raw 확정 뒤 watermark 갱신 전 승인을 끼워 넣는 failpoint, 승인 직전 새 비정상/조회 실패, 승인 먼저·새 관측 나중의 두 순서, 프로세스 재시작/다중 writer를 시험한다. 선형화된 접수 순서와 최종 이력이 일치해야 한다.

현재 차단된 API의 console 조작 가능 여부를 추정하지 않는다. 실제 타이어를 감압하거나 DTC를 지워 소등을 만들거나 주행 중 증상을 재현하는 위험한 시험은 제안하지 않는다. 단위 시험 성공·AVD 수신·실차 수리 검증 결과를 각각 보고한다.

---

## 14. 자동화 인수기준 REC01–REC28

모두 **후속 미실행 기준**이다. 기존 RR/AC/ON/NAV 결과와 assertion 수를 덮어쓰지 않는다.

| ID | 우선 | 입력/과제 | 기대 결과 |
|---|---|---|---|
| REC01 | P0 | 기존 v3 DB/관측으로 추가 모델 이전 | driver/issue/event/draft/삭제/원관측 원본·관계·ID 보존, 실패 롤백 |
| REC02 | P0 | 현재 SOC/거리만 존재 | 고장 회복/수리 완료/네 상태 자동 변경 없음 |
| REC03 | P0 | getLowTirePressure 반환 | 임계값으로만 해석, 현재 압력/저압 해소로 쓰지 않음 |
| REC04 | P0 | blocked/권한/area/Capability 조건 | 문서 이름만으로 사용 허용·수신 성공 선언하지 않음 |
| REC05 | P0 | 초기화/등록 Complete·callback 없음 | 실제 관측 성공으로 세지 않음 |
| REC06 | P0 | 미지원/null/누락/최신 실패·과거 valid | unknown 또는 stale, 정상/회복으로 대체하지 않음 |
| REC07 | P0 | sourceMeasuredAt 미확인·캐시/중복 | 콜백 N회로 독립 연속 관측을 만들지 않음 |
| REC08 | P0 | N·간격·hysteresis·TTL 미검증 | 정책 비활성, 임의 보편 숫자로 활성화하지 않음 |
| REC09 | P0 | 잘못된 area/차량/issue binding | 후보 생성 차단, 소음에 SOC 연결 금지 |
| REC10 | P0 | 원격 압력·시각과 SDK 값 | decoder/단위/invalid/측정시각 분리, API 통로 혼동 없음 |
| REC11 | P0 | TEST_ONLY/환경·UID/store 변경 | 운영 근거에서 제외/episode 중단, 실차로 위장하지 않음 |
| REC12 | P0 | 후보 생성·알림·회복 이력 조회 자체 | 상태/읽음/revision 자동 변경 없음. 별도 문제 상세의 읽음 확인은 기존 UX/서비스 정책 유지 |
| REC13 | P0 | revision 같고 manual STATUS만 변경 | state token/event sequence로 충돌 탐지, 옛 승인 적용 안 함 |
| REC14 | P0 | 수정/재발/삭제 후 옛 proposal | 만료/취소, 원문·과거 이력 불변, 자동 복원 없음 |
| REC15 | P0 | 승인 반복·재전송 | 승인·STATUS·상태 전이 한 번, 동일 동작 중복 없음 |
| REC16 | P0 | 승인 저장 도중 예외/프로세스 종료 | issue DB의 승인/STATUS/상태/종결 원자성, 성공 오표시 없음 |
| REC17 | P0 | actor SYSTEM·legacy source | A/B 위장·FK 위반·과거 event 재분류 없음 |
| REC18 | P0 | UNKNOWN/RESTRICTED·운전자 미확정 | 사용자 승인/자동 확정 우회 없음, pending 보존 |
| REC19 | P0 | 기본 정책·소등만·not ready | 네 상태 자동 허용목록은 비어 있음, 수리 완료 출력 없음 |
| REC20 | P0 | AI 실패/허구 신호/명령형 기록 | 근거 없는 출력 폐기, 규칙·상태·차량 제어에 영향 없음 |
| REC21 | P1 | 거절/나중에/만료 | 상태 불변, 같은 근거 반복 강요 없음, 새 근거와 구분 |
| REC22 | P1 | 재등장·관찰 공백 | 같은 issue의 관찰 event, 자동 recur 호출·무관측기간 정상 추정 없음 |
| REC23 | P0 | raw 확정→watermark 갱신 사이 승인, 새 비정상/실패·승인 경합, 두 DB 일부 실패/재시작 | 직렬화/evidenceEpoch로 옛 근거 승인 차단, 재시작 재검증 전 수락 금지. 승인 뒤 새 관측은 후속 사실로, raw 원문 삭제 없음 |
| REC24 | P1 | 소음/진동 센서 없음 | 센서 부족 안내·수동 확인만, 가짜 회복 팝업 없음 |
| REC25 | P1 | 사용자 의미 이해 과제 | 신호 소멸/사용자 해결/정비 완료/운행 안전을 구분. 자동 클릭과 별도 |
| REC26 | P1 | 작은 창·확대·접근성 | 근거·현재/제안 상태·취소/나중에·출처 읽기 가능, 색만 의존 안 함 |
| REC27 | P1 | 회귀 전체 및 격리 runner | 기존 등록·수정·삭제·읽음·재발 보존, 운영 DB 무변경 |
| REC28 | P2 | 문서·배포·개인정보 감사 | 실제 검증 범위·동의·보관·권한·정책 버전 명시, API/AI 완료 과장 없음 |

오탐/미탐·precision/recall 등 실제 성능 목표는 차량/신호별 ground truth와 표본 계획을 정의한 뒤 설정한다. 임의의 95%·무오류 주장으로 배포하지 않는다. 안전 부정시험에서 예상치 못한 자동 상태 변경은 허용하지 않되, 이 시험의 0건을 실제 전체 위험 0으로 표현하지 않는다.

---

## 15. Codex 단계별 구현 요구사항

1. **기준 재확인:** 최신 main/RR 변경·정확한 SDK 타입·Sample 정책 확인. 본 연구 커밋은 코드 변경을 하지 않음.
2. **보존 우선:** 운영 데이터/키/백업은 게시하지 않고 기존 v3·관측 DB 사본과 격리 validation으로 migration/rollback 회귀를 고정.
3. **M0 구조:** 새 수동 상태 source와 recovery sidecar·stable observationRef·충돌/중복 모델을 설계. 기존 enum/FK와 호환. 실제 회복 제안은 비활성.
4. **규칙 harness:** null/오류/캐시/area/환경·시각/단위·증거 부족을 먼저 통과. 네 상태 자동 허용목록 빈 상태를 시험.
5. **M1 읽기 실험:** 공개 허용·최소 Manifest 권한·area/bounds·실제 콜백/갱신 의미를 검증. getter1개부터, 제어 권한/상시 수집은 별도 승인 없이 추가하지 않음.
6. **Shadow → B:** 구조화 사실만 기록하는 관찰 모드로 오탐을 검토한 뒤 명시 binding과 사람 확인 제안을 제한 활성화. 거절·만료·삭제·수동 충돌·원자 승인을 검증.
7. **선택 설명:** 먼저 로컬 템플릿, 필요할 때만 AI/Gleo를 별도 검토. AI 없이도 저장·안내·수동 확인이 유지돼야 함.
8. **M2/C 별도 관문:** 추가 권한·진단 근거·실차·사용자 동의·허용 전이·복구·운영 모니터링을 확인하기 전 C를 활성화하지 않음.

산출 로그에는 문서/샘플 정책 확인, 권한 선언, Service/Capability, 값/area, listener 실제 수신, 저장, 규칙 판정, 승인/상태 커밋, 사람/실차 시험을 각각 기록한다. 이번 연구를 실행 로그나 구현 완료로 제출하지 않는다.

---

## 16. 공식 출처·확인일·남은 미확인

조회/통합일: **2026-11-08 KST**. OEM/정부 자료는 해당 차종·배출가스 검사·기술자 안내 맥락의 근거이며 모든 차량의 보편 판정 규정이 아니다. 정확한 샘플 source commit과 앱 source commit은 별도로 고정한다.

남은 핵심: Door/Window의 실제 대상 지원·차량 측 갱신/품질 의미, 특권 telltale/압력 접근, DTC/OEM 진단, 원격 API 자격·동의, 신호별 N/간격/TTL, 실제 수리 ground truth·오탐/미탐, 실차·주행 UX·일반 사용자 이해도. 자동 상태 전이의 현재 허용 범위는 없으며 추후 별도 검토한다.

### 저장소 고정 근거
- **R1** [최신 진행 기록][R1]
- **R2** [기록 신뢰성·수정삭제·격리 검증][R2]
- **R3** [이전 내비게이션·Gleo 연구][R3]
- **R4** [IssueModels][R4]
- **R5** [CaritdaService][R5]
- **R6** [SqliteIssueRepository][R6]
- **R7** [PleosReadClient][R7]
- **R8** [ObservationStore][R8]
- **R9** [RecordMigration][R9]
- **R10** [CarSafetyGate][R10]
- **R11** [AndroidManifest][R11]

### Pleos SDK·Sample·별도 API
- **S0** [Vehicle SDK 소개, 원 지정 URL에서 현재 문서로 이동][S0]
- **S1** [Tire 공개 목록][S1]
- **S2** [getLowTirePressure 의미·단위][S2]
- **S3** [Safety 공개 목록][S3]
- **S4** [getTelltalesInfo 권한 설명 불일치][S4]
- **S5** [Door][S5]
- **S6** [Window][S6]
- **S7** [EvBattery][S7]
- **S8** [Odometer][S8]
- **S9** [Vehicle 권한][S9]
- **S10** [공식 Vehicle SDK Sample 가이드][S10]
- **S11** [공식 Sample PublicApiPolicy 고정 커밋][S11]
- **S12** [SDK Callback 안내][S12]
- **S13** [API 호환성·가용성][S13]
- **V1** [별도 Vehicle Data API get-vehicle-status][V1]
- **P1** [Pleos 주행 제한 정책][P1]
- **P2** [Pleos UXR 개발 가이드][P2]

### 경고 소멸·진단·간헐 문제의 한계
- **N1** [NHTSA Tire Safety / TPMS][N1]
- **N2** [Ohio EPA OBDII Readiness][N2]
- **N3** [US EPA-420-R-01-015, 2001, 본문 p.14][N3]
- **N4** [Ford Ranger Warning Lamps, 해당 모델 조건][N4]
- **N5** [GM 01-00-89-010R, 2023, p.1][N5]

[R1]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/docs/CODEX_PROGRESS.md
[R2]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/docs/RECORD_RELIABILITY.md
[R3]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/docs/research/CARITDA_NAVIGATION_RECORD_GLEO_UX.md
[R4]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/model/IssueModels.kt
[R5]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/CaritdaService.kt
[R6]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/SqliteIssueRepository.kt
[R7]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/PleosReadClient.kt
[R8]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt
[R9]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/RecordMigration.kt
[R10]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/safety/CarSafetyGate.kt
[R11]: https://github.com/kyc66533-art/vehicle-app-workspace/blob/3afb3fbb0b1dcf40c0d4524bd57a9c8149943bc3/NextDrive-Pleos/app/src/main/AndroidManifest.xml
[S0]: https://document.pleos.ai/api-reference/connect-sdk-pleos/Vehicle/intro
[S1]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/entry
[S2]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/get-low-tire-pressure
[S3]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry
[S4]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info
[S5]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry
[S6]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/entry
[S7]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/entry
[S8]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry
[S9]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
[S10]: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
[S11]: https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/959981d960ec71144accb27af5ae5d341e72a644/app/src/main/java/ai/pleos/playground/sample/vehicle/explorer/PublicApiPolicy.kt
[S12]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback
[S13]: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
[V1]: https://document.pleos.ai/api-reference/vehicle-data-api/vehicle-data-api/common/get-vehicle-status
[P1]: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
[P2]: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
[N1]: https://www.nhtsa.gov/vehicle-safety/tires
[N2]: https://epa.ohio.gov/divisions-and-offices/air-pollution-control/e-check/06-obd
[N3]: https://nepis.epa.gov/Exe/ZyPDF.cgi?Dockey=P1002KRN.pdf
[N4]: https://www.fordservicecontent.com/Ford_Content/vdirsnet/OwnerManual/Home/Content?variantid=7786&languageCode=en&countryCode=USA&moidRef=G539493&Uid=G2111368&ProcUid=G2111371&userMarket=USA&div=f&vFilteringEnabled=False&buildtype=web
[N5]: https://static.nhtsa.gov/odi/tsbs/2023/MC-10241415-0001.pdf
