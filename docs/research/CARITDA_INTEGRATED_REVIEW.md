# CARITDA 통합 아이디어 심층 검증

- 작성·재확인: 2026-11-07 KST. 대상은 사용자가 제안한 A~G 통합 기능이다.
- 결론: **핵심 아이디어는 유지하고 기술적 구조를 수정한다.**
- 권장 범위: **사건 중심 로컬 A안 + 실제 수집 SOC 관측차트 1개를 조건부 추가**. AI·상시감시·모바일 실시간 공유·정비망 자동접수는 핵심 의존성에서 분리한다.
- 평가 범위: 공식 웹 문서·서비스·기존 리서치 확인. 이번 조사에서 코드를 수정하거나 APK 빌드·SDK 호출·에뮬레이터 실수신·인터뷰·정비사 평가를 하지 않았다.
- 사용자 확인 개발 상태: Windows/Android Studio/Kotlin, SDK Platform34·Build-Tools35.0.0, Gradle8.11.1 동기화, Debug APK 빌드 및 Pleos Connect2.1.0 설치·실행 성공. **실제 Vehicle SDK 데이터 수신은 미구현**. 이 상태를 독립 재실행 검증한 것은 아니다.
- 기존 ASIDE_RESEARCH.md는 보존한다. 이 파일만 새로 추가하며 Codex 코드·개발 기록·기획 명세·저장소 설정은 수정하지 않는다.
- 카잇다/CARITDA는 잠정명이다. 이전 조사에서 확인한 차잇·차잇다/CARIT 등 선행 사용·상표 검토 보류는 해소되지 않았다.

## 1. 통합 아이디어에 대한 최종 평가

### 선택: 핵심 아이디어는 유지하고 기술적 구조를 수정한다

'차량 상태를 더 많이 보여주는 AI 진단 앱'보다 **사용자가 관찰한 문제를 실제 확보한 데이터와 함께 보존하고, 다른 이용자와 정비 상담까지 같은 사건으로 이어주는 보완 도구**로 재정의한다.

가장 중요한 반증은 현대의 **데이터 기반 정비**다. 공식 서비스는 이미 고장 시점 데이터를 원격 수집하고, 입고 때 증상이 사라지는 정비 어려움에 대응한다고 명시한다 [C03]. 선제케어 역시 이상 감지·앱/문자/전화·조치 안내를 제공한다 [C02]. 따라서 '당시 데이터를 남긴다', '이상을 먼저 알려준다'를 CARITDA의 최초·고유 가치로 말하면 안 된다.

남는 차별화 가설은 다음의 연속성이다.

**사용자 표현 → 확보한 관측값/결측 표시 → 미해결 인계 → 실제 조치 기록 → 같은 사건의 재발 → 사용자가 확인한 정비 상담 한 장**

각 조각은 선행한다. Fleetio·Samsara에는 미해결 결함과 해결 확인이 있고 ALLDATA에는 로그·사건 마커가 있다 [C09,C10,C14]. 그러므로 결합 자체도 자동으로 독창적이지 않다. 비전문 이용자의 입력 부담과 정비 전달 품질이 실제로 개선되는지가 차별성의 성패다.

**선택 근거:** 사용자의 통합 목적은 유효한 검증 가설이며, 기본 APK 실행까지 진척됐다. 로컬 DB·읽기값 한두 개·사건 상태기계라면 서버·유럽 원격 API·특권 경고등 없이도 시연 범위를 통제할 수 있다.

**반대 근거:** OEM이 내부적으로 같은 정보를 충분히 전달하거나 정비사가 외부 기록을 읽지 않으면 별도 앱 가치가 작다. 실제 SDK 수신과 정비 도움은 미검증이며, 앱을 매번 열어야 한다면 관측 공백·낮은 이용률이 발생할 수 있다. 단순 기능 축소만으로 이를 해결하지 못하므로 '상시 모니터링 우선'에서 '사건·증거 우선'으로 구조를 바꿔야 한다.

**교체하지 않는 이유:** 앞선 신규11개 중 명백히 우월한 대안은 입증되지 않았다. 정차 큐·휴식 약속·새 차 적응도 중복·권한·수요 검증이 남는다. 사용자 아이디어라서 유지하는 것도, 일부 중복 때문에 폐기하는 것도 아니다.

## 2. 실제 운전자 불편과 근거

| 근거 | 확인한 사실 | 이 아이디어가 해결한다고 아직 말할 수 없는 것 |
|---|---|---|
| 간헐 고장 상담 | Motor Ombudsman은 고장코드가 없으면 시각·물리 점검, 증상 재현·장기간 관찰 등이 필요하다고 설명 [P01] | 해당 앱이 재현 성공률·수리 정확도를 높였다는 실증 없음 |
| OEM의 문제 인정 | 현대 데이터 기반 정비는 고장 당시 데이터 보존으로 입고 때 증상 소멸 문제에 대응 [C03] | 같은 문제를 OEM보다 잘 해결한다고 단정 불가 |
| 정비 후 같은 문제 재발 | CI2025 AS경험자31,474명 조사에서 국산6.9%, 수입10.5% 자기보고 [P02] | 전부 오진·기록 부족 때문이라는 인과 추론 불가 |
| 상담에 필요한 관찰 | 현대 ARS는 고장증상 상담을 명시, AAA는 들림·보임·냄새·느낌·발생 조건·최근 정비 등을 설명하라고 권고 [P03,P04] | 정비사가 CARITDA 양식을 선호하거나 실제 접수 시스템에서 수용한다는 검증 없음 |
| 공동 사용 | myHyundai·Kia App은 가족·친구 차량 공유를 제공 [C04,C06] | 공유 존재가 미해결 증상 인계 앱 수요·결제의향의 증거는 아님 |
| 통계·추세 수요의 반증 | Kia App은 주행기록·EV최근30일 소비량/평균전비·상태·안전점수 추이를 제공 [C06] | 그래프를 하나 더 추가하면 일반운전자에게 가치가 있다는 근거 없음 |

CI 수치는 조사 결과를 짧게 요약한 것이며 원문 표·장문을 복제하지 않았다. 국가·표본·자기보고의 한계를 유지한다. 이번 과제에서 인터뷰·실사용·정비 결과 검증은 하지 않았다.

### 고객 가치 질문 8개에 대한 답

1. **일반 운전자가 필요한가?** 간헐 증상·정비 재발의 문제 존재는 지지된다. 하지만 모든 운전자에게 매일 필요한 종합 관리 앱이라는 수요는 입증되지 않았다. 우선 대상은 '증상이 있었지만 설명·경과 전달이 어려웠던 이용자'다.
2. **기존 진단·정비만으로 충분하지 않은가?** OEM은 강한 데이터·서비스를 보유한다. 사용자가 느꼈지만 코드가 없는 상황, 다른 운전자의 관찰, 수리 후 재발의 연결이 추가 기회다. 내부 OEM 업무 흐름에도 이 공백이 있는지 확인해야 한다.
3. **공동 사용자 이점은?** '어떤 문제가 아직 남았는가, 누가 무엇을 조치했는가'의 정보 일관성이다. 다만 로컬 프로필 시연은 같은 차량 앱 내 공유일 뿐 실제 휴대폰 인계의 증명이 아니다.
4. **시각화는 필요한가?** 사건 당시 변화·수신 공백을 설명할 때만 유효하다. 일반 연비·배터리 대시보드는 OEM과 중복되고 장식이 될 수 있다. 차트 없는 A안이 먼저 성립해야 한다.
5. **정비사에게 유용한가?** 발생 조건·최근 조치가 상담에 중요하다는 근거는 있다. 우리 기록의 정확성·필요 항목·읽는 시간 개선은 미검증이다. 먼저 정비사가 '불필요한 데이터'를 줄이게 해야 한다.
6. **매번 실행 부담은?** 중요한 실패 위험이다. 수동 실행1회/관측세션 방식으로 솔직히 제한하고, 무기록 구간은 비운다. 실행 빈도·미기록률이 높으면 상시감시를 가정해 덮지 말고 제품 가치를 재평가한다.
7. **차량 화면이어야 하는가?** 차내 SDK 값을 바로 사건에 붙이고 다음 이용자가 같은 차에서 확인할 이유는 있다. 차 밖 정비 상담·사진·문서 공유는 휴대폰이 더 편할 수 있다. 전부 차량 화면에 넣는 것이 목적은 아니다.
8. **메모·카카오톡·제조사 앱 대비 구체 이점은?** 구조화된 사건ID, 데이터 출처·조회시각·단위·결측, 조치-재발 연결, 중복 작성 없이 상담 요약까지 이어지는 것. 반면 메모·채팅은 이미 익숙하고 공유가 쉽다. 동일 과제로 완료시간·누락·질문 수를 비교해 이점을 증명해야 한다.

## 3. 기존 서비스 대비 중복·차별성

분류: **① 충분 구현, ② 기존 기능의 UX 개선 여지, ③ 결합 가치 가능, ④ 차별화 가설이나 기술·시장 검증 필요**. ④는 '새로움 확정'이 아니다. 양산 서비스·상용 도구·프로토타입·아이디어를 구분한다.

| 필수 비교 서비스 | 이미 확인된 기능·조건 | CARITDA에 대한 판정 |
|---|---|---|
| Bluelink 차량진단 [C01] | 경고등 기반 원격1차진단, 상담·견인·정비 연결. 진단대상은 엔진/변속 계통, 상세진단은 방문. 차종·단말·통신 조건 | A모니터링①, B설명②. 모든 부품 상시진단으로 일반화 금지 |
| 현대 선제케어 [C02] | 지원차종·Bluelink가입·OTA 등 조건. 실시간 이상감지→알림/상담→긴급지원·정비. 알림 이력·셀프솔루션 | A/B/D 상당부분①. 원격 확인 가능한 데이터만 다루며 모든 상황 알림 아님 |
| 현대 데이터 기반 정비 [C03] | 지원차종·Bluelink. 고장시점 데이터 수집·분석·정비, 증상 소멸 문제 대응 | 당시데이터+정비연결의 직접 선행. F/C 조치-재발 연결을 더 잘하는지는④ |
| myHyundai 공유 [C04] | 가족·친구와 차량/기본정보·서비스 공유, 권한·인원은 기능별 상이 | 차량공유 자체①, 미해결 사건의 연속 인계③ |
| myHyundai 운행리포트 [C05] | 정기점검·주행정보 리포트, 차종·단말/법인·리스 등 제한 가능, 상용차 제외 안내 | 일반주간·월간리포트①. 증상 전후의 데이터 해석은③ |
| Kia App/Auto Q [C06,C07] | 주변·선호정비소 안내·예약, 공유, 차계부/정비내역, EV30일 전력/전비·상태·매뉴얼. 차종별 콘텐츠 차이 | A/C/D/E 상당부분①. 정비상담 전달 흐름 개선②/③ |
| Gleo AI [C08] | 차량 매뉴얼·상태/기능 설명·자연어 명령, 지원차종·지역 조건 | 경고등 쉬운 설명 자체①/②. CARITDA 자유문장 분석 API 승인과 다름 |
| Fleetio [C09] | 결함→Issue/Workflow, 다음 점검에서 해결 확인·반려, Failure Timeline | 미해결인계·조치 추적 자체①. 비전문 소규모 공동차 UX②/③ |
| Samsara [C10] | 이전 미해결 DVIR, 정비사/다음운전자 확인, fault·작업지시·정비 연계 | 종합관리 결합까지 강한 선행①. 소비자용 간소화만으로 우위 미확정 |
| Car Scanner [C11] | OBD데이터 기록·그래프·CSV/BRC 내보내기. 기록조건·연결 필요 | 데이터차트·내보내기①. 추가 OBD 없이 차내에서 짧게 기록하는 흐름②/④ |
| FIXD [C12] | 별도OBD센서+폰, 코드 설명·점검알림·가족차, 유료정비사상담 등 | 경고해석·정비사상담 자체①. 판매자의 효능·안전판정을 우리 앱 근거로 전이 금지 |
| OBDeleven [C13] | DTC freeze-frame·발생조건. 해당 기능 VAG/PRO·Ultimate 조건 | 당시센서값 보존①. 타브랜드·무료 Pleos에서 동일 데이터 이용 가능 뜻 아님 |
| ALLDATA [C14] | PID기록·Mark·마커로이동·재생그래프·DTC보고서 | 사건마커+로그+보고서 자체①. 비전문 입력 부담 개선②/④ |
| 현대모비스2026 [C15] | MOBIDIC AI진단, 운행카드 등. 다른 SDK 기반 수상 프로토타입 | AI진단·운행카드 신규 주장 불가. 세부 입력/API 미공개 |
| Pleos2026·24H [C16,C17] | 차계부·차량관리 게임 등 아이디어, TaleSync 주행연동 시연 | 아이디어·프로토타입을 출시앱으로 부르지 않음. 비공개작 중복은 알 수 없음 |

### 사용자 기능 A~G 분류

- **A:** 상태조회·선제알림은①. 공개 신호의 출처·관측 공백을 솔직히 보여주는②. OEM 고장탐지 대체는 제외.
- **B:** 경고해석은②. AI가 새롭다는 주장 제외, 공식 차량설명서에 근거한 표현 검증 필요.
- **C:** 단순 차량공유는①. 미해결 사건·조치·재발의 공동 인계는③.
- **D:** 예약·연락은①, 동일 사건의 상담 자료 전달은③. 모든 정비망에서 수용되면④가 될 수 있으나 미검증.
- **E:** 일반운행리포트①. 사건 전후 맥락·누락까지 보여주는 최소차트는③, 실제 추가가치 검증 필요.
- **F:** 사용자 미세증상과 데이터·미해결·조치재발 연결은④ 가설. OEM 소비자 안내에 안 나온다고 내부 시스템에도 없다는 뜻은 아님.
- **G:** 간결한 UI는②. 더 쉽다는 주장은 비교시험 전에는 목표다.

## 4. 기능별 Pleos SDK·API 지원 여부

### 4.1 서로 다른 연결 통로

| 기술 | 실제 역할 | 접근·지역·의존 | 현재 평가 |
|---|---|---|---|
| 차내 Connect Vehicle SDK [D01,D02] | AAOS 앱↔Vehicle Service IPC, 현재값/변경 콜백 | Vehicle.initialize→get도메인→capability→get/register→unregister/release, 차종/영역별 지원 | 문서확인·개발가능성높음, 사용자 앱 실수신 필요 |
| 원격 Vehicle Data API [D14,D15] | 커넥티드 텔레매틱스 REST, OAuth/동의 | 공식 소개가 **2026.8 기준 유럽 한정**, 한국 확대근거 이번에 못 찾음 | 문서확인, 한국 MVP 기본통로로 채택 불가 |
| Fleet API [D16,D17] | 계약형 관제, REST/Webhook | Pleos Fleet 계약·신청승인·담당자endpoint·Client ID/Secret/Project ID | 별도권한·계약 필요. Fleet Maintenance/Trip은 개요상 Coming soon |
| 국내 Hyundai/Kia Developers [D18] | 국내CCS 차량 데이터 연계의 별도 통로 | 프로젝트·고객동의·실차계약자 테스트·상용화심사. 공유받은 차량은 목록에 안 나오는 조건 | 존재 확인. 정확 센서/DTC/호출주기/접수 권한 미확인. 유럽API와 혼동 금지 |
| Gleo Interface [D19~21] | 등록 action을 음성으로 호출하고 callback 답변 | schema검토·승인, 앱 설치·부팅/스키마 인식, 실제 Gleo환경 | 문서확인·승인/실행검증 필요. 범용 LLM 분석과 다름 |
| AAOS Android API [D12,D13,D24~31] | UX제한·로컬저장·UI·선택 property·export | Android버전·차량별 지원, 일부 normal/dangerous/privileged 분리 | 일반 Android기능 개발가능성높음. 차량 실동작 별도 |
| 모바일·DB·FCM [D32] | 다른기기동기화·push | 별도앱·인증·권한규칙·신뢰서버·네트워크 | 일반모바일 구현가능, 공모전 핵심에서 제외 |

**한국 원격 접근이 모두 불가능한 것은 아니다.** 유럽 Vehicle Data API 제한을 국내 Hyundai/Kia Developers에 전이하면 안 된다. 반대로 국내 플랫폼이 있다고 선제케어·정밀 고장 데이터·정비망 접수까지 외부에 열린 것도 아니다.

### 4.2 센서·경고·기능 매트릭스

등급은 배타적이지 않다. 문서확인이어도 실행검증필요가 함께 붙는다. '현재 불가능'은 현재 선택한 경로·범위에 한정하며 기술 전체의 영구적 부재를 뜻하지 않는다.

| 데이터/기능 | 실제 메서드·권한·의미 | Emulator/실차와 판정 |
|---|---|---|
| 누적거리 | Odometer.getCurrentDistanceDriven(), registerDistanceDriven(), checkCurrentDistanceDrivenCapabilities(); pleos CAR_DRIVE normal, km [D04] | Console Odometer조작 문서확인. 사용자 앱 수신 미구현. 문서확인/개발가능성높음/실행검증필요 |
| 연료 잔량 | Fuel.getFuelPercent(), registerFuelPercent(), checkFuelPercentCapabilities(); CAR_ENERGY normal, % [D03] | 연료량L·순간소비율로 바꾸지 않음. Console 이번목록 연료%조작확인 못함. 실수신/차종/변경주기 검증 |
| EV SOC | EvBattery.getEvBatteryStateOfCharge(), registerEvBatteryStateOfCharge(), checkEvBatteryStateOfChargeCapabilities(); CAR_ENERGY normal, % [D05] | Console0~100%,1%단위. 실제 앱 callback·null/범위·정밀도 확인 필요 |
| EV 충전상태 | getEvChargeSwitchState(), checkEvChargeSwitchCapabilities(); CAR_ENERGY normal, Boolean? [D06] | Console On/Off. false만으로 케이블/결제/기기고장 원인 판정 불가 |
| 공조설정/실내온도 | getHvacTemperature() 목표값, getHvacTemperatureCurrent() 현재실내온도; CAR_CLIMATE normal [D10] | Console 목표온도≠실제온도. 에어컨냉매량·외기온 대체 불가 |
| 실제 타이어압: Pleos Tire | 이전 getLowTirePressure() 문서는 **critically low pressure threshold(kPa)**를 뜻한다 [D07]. 최신 공식 PublicApiPolicy는 Tire.getTirePressure/checkTirePressureCapabilities/registerTirePressure/unregisterTirePressure를 blocked로 분류 [D36] | 임계치를 현재압·저압Boolean으로 사용 불가. 실제압 API 이름이 보여도 일반앱 접근을 가정하지 않는다. 현재 공개 경로로 자동압력 추세를 약속하지 않음 |
| 실제 타이어압: AAOS 대안 | VehiclePropertyIds.TIRE_PRESSURE(kPa), CarPropertyManager.getFloatProperty(), wheel area/config; 최신 Android는 CAR_TIRES_3P dangerous 또는 CAR_TIRES signature/privileged [D13] | 사용자 Pleos2.1/API34환경에서 새3P경로 지원/권한/센서 미검증. 특별property는 Pleos지원팀검토 안내 [D12]. 지원 확정 전 B안 핵심 제외 |
| 타이어 경고·계기판 경고 | 이전 개별문서 Safety.registerTelltales()/checkTelltalesCapabilities()는 PRIVATE_CAR_SAFETY signature/privileged. getTelltalesInfo()는 권한설명 상충 [D08,D09] | Console TPMS/ABS Off/On/Blink가 일반앱 접근증거 아님. 최신 공식 PublicApiPolicy도 getTelltalesInfo/checkTelltalesCapabilities/registerTelltales/unregisterTelltales를 blocked로 분류 [D36]. 조회 polling을 권한 우회로 제안하지 않으며 자동경고 핵심 제외 |
| DTC/Freeze-frame | Pleos 현재 검증된 일반앱 경로 못 찾음. OBD제품·원격status warnings와 동일시 금지 | 별도권한·지원 경로 검토. 소유자 동의만으로 ECU 전체 접근 보장 안 됨 |
| 주기적 수집 | SDK register* 변경구독 또는 앱 살아있는 동안 getter 스케줄. capability/get는 비동기 onComplete/onFailed [D01] | 일반적인 주기·원본센서 timestamp·고주파 보장 없음. 초당 polling 임의강행 금지. 세션/공백 저장 |
| 과거7/30일 차내 조회 | Connect의 현재값·callback은 과거서버이력 API 아님 | 자체 DB에 실제 수집한 기간만 집계. 설치전/미실행기간 복원 현재경로로 불가 |
| 원격 기초이력 | GET /v1/vehicles/{vin}/status/data 등 startDate/endDate, 한요청 최대7일 구간 [D15] | 7일 보존기간이라는 뜻 아님. 30일보존·동의이전이력 보장 미확인. 유럽제약 별도 |
| 앱 미실행 수집 | Android FGS/수명주기 조건. WorkManager 반복 최소15분·지연 가능 [D25,D26] | FGS선언/시작조건·사용자알림·OEM전원정책 별도검증. WorkManager를 실시간경보 대체로 사용 불가 |
| 주행 중 자동알림 | CarUxRestrictionsManager.registerListener→onUxRestrictionsChanged, activeRestrictions 적용 [D24] | DO선언만으로 전부허용 아님. 임계값알림/색/음성/빈도·다른경고와간섭 검증. 우선 주차상세만 |
| 공식경고 의미 설명 | 확인된 Telltale enum/사용자선택과 차종·연식별 공식매뉴얼 대응 | DTC와경고아이콘 구분. 범용코드맵/LLM원인판정 금지. 입력출처와문서버전 표시 |
| 냉매량·고장원인·배터리 SOH | 확인한 공개경로에서 냉매g·원인확정·SOH진단 근거 못 찾음 | HVAC/냉동고온도/SOC가 대체측정 아님. 현재 데이터로 해당측정/진단 불가 |
| 상담용 로컬요약·프로필 | 앱 내부 DB/템플릿, 별도차량권한 없음 [D27] | 개발가능성높음. 로컬프로필=실제스마트폰동기화/OS사용자전환 아님 |
| 전달·접수 | ACTION_SEND/SAF는 문서공유 후보. 정비망 사건생성 API 미확인 [D28~31] | 파일생성≠공유성공≠접수완료. 차량picker/수신앱/브라우저/전화 UI 실행검증 |

### 4.3 이번 재확인에서 달라진 해석

- 공식 sample의 PublicApiPolicy.kt에서 경고등 묶음 및 실제압 API의 명시적 blocked 목록을 직접 확인했다 [D36]. 공개 경로로 검증되지 않은 함수를 reflection으로 찾거나 Manifest를 추가하는 것은 권한·지원의 증명이 아니다. getLowTirePressure의 과거 문서가 있다고 최신 artifact에 그대로 존재·동작한다고 보장하지 않는다.

- 최신 Android 문서의 CAR_TIRES_3P 경로 때문에 '현재압은 모든 일반앱에서 영원히 불가'라고 쓰지 않는다. 그러나 최신 문서가 Android14/Pleos2.1 지원을 입증하지도 않는다.
- Pleos availability 검색색인에는 과거 일괄 O/X표가 남아 있지만, 직접 열어 확인한 현재 본문은 property별 실지원·접근 검사와 특별property 사전지원검토를 요구한다 [D12]. 오래된 검색표로 현재 지원을 확정하지 않는다.
- 공식 sample은 Vehicle2.3.2·Service2.3.22·Android14 AVD에서 일부 검증을 보고하나, Door Lock read 실패 및 set완료와 follow-up read 불일치도 공개한다 [D33]. **sample 성공과 사용자 앱 성공, set응답과 지속상태 변경은 다르다.** 이 앱은 쓰기 API 자체를 제외한다.
- sample의 빌드는 Platform35/JDK17 기준이고 사용자 설치는 Platform34라고 보고됐다. 최소개발기준/API시스템이미지/compileSdk/targetSdk/Build-Tools는 다른 값이다. SDK의 AAR metadata·Gradle/JDK 호환을 확인할 것. 기존 Debug성공만으로 Vehicle 의존성 추가 빌드도 성공한다고 보지 않는다. 설치·프로젝트 변경은 이번 조사 범위가 아니다.

## 5. 데이터 분석 기능의 실질 가치와 구현 가능성

### 적용할 흐름

**원천 읽기값 → 품질검사/단위/시간 정리 → 관측점 시각화 → 이상한 관측의 표시 → 가능한 설명 후보 → 다른 근거로 추가검증**

Spotfire 공정 분석 경험의 강점은 차트 수가 아니라 출처·운전조건·결측·변경점·교란변수를 구분하는 습관이다. 이 경험이 자동차 진단 자격이나 차량 고장 모델의 타당성을 자동 보장하지는 않는다.

- **임계값 경고:** 사용자 선호의 낮은SOC 알림과 OEM 안전경고를 분리. 사용자설정20% 같은 값은 개인선호이지 고장임계값이 아니다. OEM 경고 기준을 수정하지 않는다.
- **통계추세:** 동일조건 반복관측·충분한 baseline·관측기간이 필요. 수치 몇개 변한 것과 통계적이상·배터리열화는 다르다.
- **결측:** null/invalid/권한거부/미지원/stale/앱중단을 값0으로 채우지 않는다. 관측되지 않은 구간은 선을 끊고 기간·관측건수를 표시.
- **비동기:** SOC와누적거리의 수신시각이 다를 수 있다. 원본측정시각이 없으면 요청·수신시각, 지연·허용되는시각차 정책을 저장하고 완전동시센서라고 부르지 않는다.

| 분석안 | 필요한 데이터·교란 | 현재 추천 |
|---|---|---|
| 타이어압 추이 | 실제각바퀴압+단위+냉간/열간+외기온+적재/주행조건·게이지출처. 단순 온도보정으로 누설 확정 불가 | 현재 자동수집경로 미검증. 제외. 수동게이지기록을 센서값으로 꾸미지 않음 |
| 연료-거리 | 잔량% 정밀도·탱크형상·기울기·주유/누유/누적거리. 주유증가/세션공백을 구분 | %차이만으로 L/100km 계산 금지. 검증된 실제L·탱크변환모델 없으면 잔량변화 관측만 |
| EV SOC-시간/거리 | SOC%, 충전상태, 주행거리·조건·공조·기온·배터리보정·회생·관측공백 | 차트1개 후보. SOC하락을 곧 열화/고장/전비kWh로 바꾸지 않음 |
| 12V/냉매/배터리건강 | 실제전압/냉매량/SOH 경로와정확도·조건 필요 | 견인배터리SOC와혼용금지. 현재MVP 제외 |
| 조치-재발 | 동일사건·수동증상·처음/최근발생·조치확인·관측회수 | 차트보다 사건목록이 먼저. 빈도를 실제운행노출률로 과장하지 않음 |

### 공모전 최소 차트 1개: '현재 수집 세션의 SOC 관측점 + 증상 마커'

- x축: **실제 앱 수신시각**, y축: SOC(%). 주행거리·충전상태는 확보됐을 때 툴팁/텍스트로만 추가. 이중축으로 정보를 과하게 늘리지 않는다.
- 실제수신점과 앱내 fixture는 별도 데이터셋·배지. Console 조작으로 수신했다면 **에뮬레이터 시뮬레이션 데이터**이지 실도로이력 아님.
- 그림은 유효시각2점 이상에서 관측점을 표시할 수 있으나 **2점만으로 통계추세·이상판정을 허용하는 기준은 아니다**. 한점은 숫자/기록표. 정상 baseline과비교가능한조건이 부족하면 '추세분석 보류/관측자료 부족'. 임의 표본N을 채웠다고 고장판정하지 않는다.
- 중복콜백·역순·재부팅·단위·수신지연 검사, 원본은 보존. charging flag가미확인이면 충전구간이라고 임의확정하지 않음.
- chart는 Android Canvas/View.onDraw 또는 현재 UI Toolkit의 local drawing으로 가능, 유료API·Spotfire서버·외부LLM 불필요 [D34]. 실제 구현·성능은 미검증.
- PT에는 짧은 현세션5~10개 관측을 준비할 수 있지만 실제로 수신한 것만. **가짜7일·30일 이력을 만들어 수집/분석 성공이라고 말하지 않는다.** 7/30일 버튼은 실제기록이 없으면 빈화면/관측부족을 보여준다.
- 사용자 가치: '언제 어떤 값이 있었고 어디가 비었는가'를 증상 설명에 붙이는 것. 이 값이 정비사가 필요로 하는 신호가 아니면 차트는 빼야 한다. 차트 미완성으로 A안을 지연시키지 않는다.

## 6. Gleo AI 연동 가능성과 한계

### 확인된 연동

Gleo Interface는 LLM이 등록 function을 고르고 앱에 Android action을 전달, 앱 callback을 받아 답변하는 구조다 [D19]. AppSchema actionCalls최대3개, 영어description, 정해진파라미터타입, required값도앱측검증 필요 [D20]. service component가 문서상 sendBroadcast 경로인 점을 실제Service.start와혼동하지 않는다.

- assets/schema.json과 Manifest action filter 필요.
- schema 검토·승인 후 연동 활성화.
- 현재 deliver-schema 본문은 Gleo AI App 부팅 시 스캔뿐 아니라 설치/제거 이벤트의 schema DB갱신도 안내한다 [D21]. 이전 '부팅1회뿐' 확인에 고정하지 않는다. 실제CARITDA패키지의 인식·승인·action/callback은 아직 시험하지 않았다.
- 예시 후보기능: '문제 기록 열기', '미해결 문제 보여주기', '정비 요약 열기'. 주행 중 상세화면은 UX규제에 따라 잠금. 임의파라미터로사건삭제/외부전송 실행금지.

### 확인되지 않은 부분

- 자유문장을 보내 분석결과를 직접 받는 public chat/completion endpoint·쿼터·가격·Gleo용키발급·서버등록상세를 현재 Interface문서에서 확인하지 못했다.
- 앱 action호출이 가능하다고 '사용자 소음표현을 Gleo가 자유롭게 분석'하는 기능까지 된다고 쓰면 안 된다.
- 과거 LLM SDK는 Emulator2.1 release note상 지원종료 [D23]. 구버전튜토리얼로현재무료LLM을약속하지 않는다.
- schema승인요청/회신기한·공모전기기Gleo가용성은 확인필요. Playground/에뮬레이터설치가승인성공이아니다.
- ON_DEVICE STT는 텍스트변환경로이며 Gleo reasoning과다르다. STT_SERVICE선언·실제권한·마이크/호출검증이별도 [D22].

### 무료 규칙 기반 fallback

정차 후 원문을 보존하고 '증상 / 발생조건 / 마지막관측값 / 조치 / 재발 / 정비사 질문' 템플릿에 넣는다. 사용자가수정·확인하기전에는추정문장으로확정하지않음. **정해진 필드 정리는 가능하지만 고장 원인 추론을 대체하지 않는다.**

예: '사용자 입력: 낮은 속도에서 오른쪽 소음. 기록시 누적거리: 실제수신값/미수신. 원인: 판단하지 않음. 상담시 확인할 점: 발생조건·이전조치.' 공식차종매뉴얼에대응되는경고만짧게설명하고출처·버전을남긴다. LLM을쓰지않으면'MVP가AI분석을구현했다'고홍보하지않는다.

## 7. 정비소 연동·공동 사용자 동기화

### 로컬 인계

앱 내부에서 '사용자A/B'를 선택하고 같은Room DB의미해결사건·인계확인을저장할수있다 [D27]. 이는 인증된사용자식별도아니고, AAOS OS사용자전환도아니며, 휴대폰간동기화도아니다. 프로필이바뀌어도원문/작성자/수정이력은남기고, 확인표시는차량안전보증으로표현하지않는다.

AAOS는OS사용자별앱데이터를분리한다. cross-user특권없이앱내프로필로다른OS사용자의DB를공유할수있다고말하지않는다 [D35]. 개인정보가있는공용차의실서비스는접근통제·삭제·이용동의를추가해야한다.

### 실제 스마트폰 공유

별도모바일앱 또는검증된웹클라이언트, 인증, 차량/그룹멤버권한, 원격DB, 충돌·오프라인재접속·탈퇴/삭제·감사기록이필요하다. FCM은 **DB가 아니라 메시지전달**이다. 신뢰할수있는서버/Admin SDK또는HTTPv1,수신앱·토큰수명관리·기기호환이필요 [D32]. Admin키를앱에넣지않는다.

FCM 자체는무료이나DB/Functions/스토리지/운영비는별개. 무료한도안이어도개발·보안비용이0은아니다. Android13+알림표시는POST_NOTIFICATIONS허용이필요하나이를모든데이터메시지수신필수조건으로확대하지않는다. AAOS의Play services/수신환경도시험대상이다. C안의최종PT완료를약속하기엔큰범위다.

### 현실적인 정비 상담 전달 단계

1. **기본:** 사건한장화면에증상·실제값·출처·조치·재발을보여주고사용자가대면/전화상담에활용.
2. **로컬파일:** PDF/텍스트/JSON 생성. 내부파일저장은일반앱으로가능. PDF는Android PdfDocument같은로컬기능으로개발가능하나한국어폰트·가독성은시험필요.
3. **선택 내보내기:** ACTION_CREATE_DOCUMENT(SAF) 또는 ACTION_SEND+Intent.createChooser+FileProvider content URI와임시읽기권한 [D28~31]. 차량의picker/DocumentsProvider/수신앱/브라우저·전화호출은미검증이므로무조건되는fallback은아님.
4. **수신·접수:** 파일생성/앱선택/Intent전송만으로정비소가받았다고표시금지. 사용자확인전자동전송하지않고, 접수완료는상대방확인과예약상태가있을때만.

현대공식검색/예약및전화080-600-6000,기아Auto Q공식검색/예약및현재공식대표전화080-200-2000을연결후보로둔다 [P03,C07]. 차량ACTION_DIAL은사용자가통화를시작하게하는후보이며실제차량전화UI는미검증. 번호자체도표시해안전한대안을남긴다.

이번공식안내·Developers조사범위에서일반제3자앱이Bluehands/Auto Q에사건·첨부물을등록하고정비결과를받는현재공개접수API는확인하지못했다. **API가전혀없다**고단정하지않는다. 제휴가능성·상용승인·접수확인규격은별도다.

인근정비사검색은업체DB·위치·데이터최신성·지도서비스조건이필요하다. 무료오프라인자동검색을약속하지않고, 공식온라인검색·수동지역선택·저장연락처를먼저쓴다. 서버없는로컬기록이자동으로휴대폰에서열리는것도아니다. 휴대폰으로의파일이동경로도검증해야한다.

## 8. A/B/C MVP 개발안 비교

| 안 | 핵심범위 | 의존/난도 | 주요위험 | 최종PT 전망 |
|---|---|---|---|---|
| A 최소MVP | 누적거리/SOC 중실제읽기1개+증상+로컬프로필인계+상담템플릿 | 낮음~중간. Vehicle연결/Room/UX. 서버·Gleo 불필요 | SDK첫수신·생명주기·정비사유용성. 로컬인계를폰동기화로오인 | 세안중가장통제가능. getter실수신전에는완료확정X |
| B 분석중심 | 실제SOC세션관측차트1개+사건/설명/인계 | 중간. SOC subscription/보존/누락·충전조건/차트 | 관측값이빈약해차트가장식이되거나통계과장. 타이어자동압경로미검증 | 작은단기관측차트는가능성있음. 주간/월간진단앱은미권장 |
| C 확장 | A/B+휴대폰real-sync+정비사시스템+Gleo | 높음. 인증·서버·수신앱·보안·승인/제휴 | API접근·스키마승인·정비망미확인·동기화오류·일정 | 현재미구현상태에서PT필수로약속하지않음. 로드맵/선택실험 |

### 권장 조합: A + B의 작은 일부

A의사건흐름이먼저완성되어야한다. SOC실수신이확인되고다른핵심기능을지연시키지않을때만**관측차트1개**를추가한다. B를위해가짜이력·새로운센서·AI이상모델을추가하지않는다. C는각어댑터를옵션으로분리한다.

제안 구조: VehicleReadAdapter → ObservationStore(Room) → IncidentStore/전이이력 → LocalProfileView → RuleSummary → 화면/선택Export. Gleo·국내원격·유럽원격·Fleet·Push·정비접수는독립어댑터로분리해접근실패가로컬기록을망가뜨리지않게한다. 이것은설계권고이며기존코드리팩터링을수행했다는뜻은아니다.

### 공모전 경쟁력 자체평가

1~5점. 높을수록유리하며난도는'쉬움/개발통제력',시연은'일정내시연가능성'으로환산. 동일가중45점, 공식배점·수상확률아님. SDK실행/가치검증으로바뀔수있다.

| 항목 | A | B | C | 근거 |
|---|---:|---:|---:|---|
| 차량특화성 |4|4|5|당시값+같은차인계, C는확장되나실현부담|
| 창의성 |3|2|3|사건흐름UX가설, 차트/결합자체선행|
| 기술활용성 |4|4|5|실제공개읽기·출처/결측설계, C는실패시점수성립안함|
| 사업성 |2|2|3|지불의향/파트너/반복이용미검증|
| 실제수요 |3|2|3|증상설명근거있음, 추가차트수요약함|
| UI/UX |4|3|2|A한장, B설명부담, C계정/동의/단계증가|
| 구현통제력 |4|3|1|현재실수신미구현, C외부승인의존|
| 일정내시연 |4|3|1|A좁은범위, C핵심미확인다수|
| 경쟁대비개선 |3|2|3|아직실증전, 전문도구/OEM강함|
| 합계 |31|25|26|합계만으로C선택금지: 구현·시연1점게이트탈락|

A+B선택부분은모든B기능을가져오는것이아니다. 기본평가는A31점수준의조건부목표, SOC차트효용이없으면빼는것이오히려낫다.

## 9. 최종 권장 기능과 제외 기능

### 필수

- 실제getter1개부터연결,Capability/read접근·권한/오류/null처리.
- 짧은정차후증상기록. 원문은유지하고정형필드3개이내/모름건너뛰기.
- 사건ID·처음/최근시점·관측출처·단위·수신시각·결측·수집세션.
- 로컬프로필전환·미해결확인·조치·재발추가. 해결은사용자/정비관찰로기록하며자동안전판정안함.
- 규칙기반상담한장. 정비사에게보일질문과확인된사실을분리.
- CarUxRestrictions연동,주행중긴글/차트/입력차단. 알수없으면상세잠금.
- 재시작후복원,수집중단/오래된값표시,데이터삭제및동의경계.

### 조건부

- SOC현세션관측차트1개.
- 실제차량환경에서지원확인한파일내보내기/공식예약페이지·전화연결.
- Gleo action호출1개만실험. 실패해도핵심저장·인계·요약은작동.

### 제외

- 전체차량상시진단,타이어실압자동추세,냉매량측정,SOH/고장원인확정.
- 특권경고등구독을필수로하는자동알림,OEM경고임계값대체.
- 가짜주간/월간데이터·미관측시간의정상추정·통계적이상을고장으로변환.
- 시동OFF감시·앱미실행무중단수집·부팅자동기동보장.
- 정비망자동접수·실시간모바일공유·차량제어·유료외부AI를PT필수에넣기.
- AI를쓰지않는MVP의이름/발표에서'AI분석구현완료'주장.

UI상'정상'녹색일괄표시대신'최근조회/미조회/사용자보고/미확인'을구분한다. 색만으로중요도를전달하지않고큰글씨·아이콘+라벨·짧은문장. 사용자선호알림을OEM위험경고색과혼동하지않게한다. 상태→사건→인계/상담의짧은동선을목표로하되가독성·완료시간은실측전목표다.

## 10. 1차 기획서에서 강조할 핵심 메시지

**핵심은 '차를 진단한다'가 아니라 '운전자가 겪은 문제와 이후 조치를 끊기지 않게 남겨 상담에 활용한다'이다.**

- 문제: 코드가없거나입고때사라진증상,다른사용자가모르는미해결상태,조치뒤재발설명의어려움.
- 해법: 확인된차내읽기값+사용자표현+사건이력의연결. 데이터가없으면없다고보여준다.
- 기존대비: OEM원격진단·데이터기반정비,ALLDATA마커,Fleetio/Samsara인계를인정한다. 비전문기록부담·한장상담품질개선은검증계획으로제시한다.
- 현재상태: 사용자확인Debug실행성공,Vehicle실수신아직미구현. 차내SDK연결이첫게이트.
- 기술: 공개normal읽기·로컬DB·안전UX. 특정국가원격API/특권경고등/Gleo승인에핵심의존안함.
- 사업: 초기엔간헐증상경험자·공동차이용자로좁힘. 실제MAU/Retention/지불의향은미측정,숫자임의생성금지.
- 시연약속: 한사건생성→실제수신값/결측표시→다음로컬사용자확인→조치/재발→정차상담요약. 모바일동기화/정비접수는선택검증전약속안함.

공식일정은접수11/15,최종PT12/03~04. 1차산출물선택가산점,2차동작앱시연필수,세부배점비공개/2차상세기준추후안내다 [F01~03]. 이보고서는기획서접수나공식승인을대신하지않는다.

## 11. 2차 PT 실제 시연 시나리오

아래는**앞으로구현할수있는지검증할계획**이며이번조사에서수행한결과가아니다. 기본시연3분,선택차트1분.

1. **연결(0:00~0:25):** 앱실행,Vehicle/Service버전·Capability·실제수신시각표시. 누적거리/SOC중검증된1개. 값이없으면대체실제센서라고꾸미지않음.
2. **증상(0:25~1:00):** 안전한주차상태에서'저속에서오른쪽소음'수동입력. 시각·확보값첨부. 진단원인필드없음. 사용자선택경고는별도'수동입력'라벨.
3. **인계(1:00~1:35):** 같은앱내로컬프로필B로전환→미해결사건확인→확인기록. 스마트폰푸시/실제OS사용자전환이라부르지않음.
4. **조치·재발(1:35~2:05):** 사용자제공조치메모추가→같은사건재발기록. 데모사례라명시하고실제정비성공사례로포장하지않음.
5. **상담(2:05~2:35):** 출처/누락/원문/조치/재발포함한장생성. 검증된export경로가있으면사용자동의후파일생성,없으면차량화면조회. 접수완료표시안함.
6. **안전·복원(2:35~3:00):** Console주행제한상태→긴글/차트/입력잠금. 앱재시작→사건이력보존·관측공백표시. 실제수신하지못한주차신호를임의확정하지않음.
7. **선택차트:** Console SOC를여러값으로바꾼후실제SDKcallback저장→현세션관측점·증상마커→한구간관측중단→선끊김. '에뮬레이터시뮬레이션관측'표시,7/30일은실기록없어분석보류.

### 합격 조건과 대체

- 최소성공: getter1개진짜수신+재실행보존+사건전체흐름+주행잠금.
- 임계상황: SDK가미지원이면수동기록UI시연은가능하나'차량데이터핵심MVP완성'이라할수없다. 발표기술활용범위를낮추고다른지원공개값으로재설계.
- Fixture는실데이터와분리해명시. 예비영상도실제동작앱녹화여야하며시연환경/버전/오류경로를남긴다.
- 절차중견인·정비전화·메일·실제수신자전송은자동실행하지않는다.

### 일정 제안(개발 예상이지 보장 아님)

11/07~09 SDK연결/관측저장,11/10~12 사건·로컬인계·템플릿,11/13~15 동작영상/1차자료.11/16~22 오류/권한/정비사필드평가·선택차트,11/23~29 가독성/UX잠금·export검증,11/30~12/02 기능동결·release/반복시연. 게이트실패·핵심지연시C와차트를먼저뺀다.2027전시는핵심개발유예가아니다.

## 12. 첫 개발 단계의 기술적 위험 5개

1. **SDK/Service/빌드 호환·진짜읽기:** 사용자기본APK성공과Vehicle의존성추가성공은별개. 정확artifact/JDK/compileSdk/AAR조건·initialize완료·Capability·읽기권한·한getter실수신까지로그확보. 실패하면기능확장중단.
2. **신호의 의미·권한 경계:** Tire임계치≠현재압,설정온도≠실내온도,원격API≠차내SDK,경고≠DTC. 현재SDK어노테이션·특권권한·지원영역을확인,불명확한값은필수제외.
3. **수집 수명주기·과거 이력:** callback해제/재연결·앱백그라운드/강제종료/절전·시동OFF후누락,DB원자적기록·재부팅시계. WorkManager/FGS만으로상시보장하지않음.
4. **주행 UX·안전한 외부행동:** 실제CarUxRestrictions수신·잠금·상태손실fail-closed,알림오인·오디오간섭. export/picker/전화는차량지원과동의확인. 파일생성·공유·접수세상태분리.
5. **Gleo·동기화의 외부의존·보안:** schema승인/action/callback/누락파라미터,별도모바일인증/DB규칙/FCM서버/개인정보동의. 이들이실패해도사건저장·로컬인계·템플릿은작동해야함. 키를앱에넣어우회하지않음.

## 13. 공식 근거 URL과 미확인 사항

### 남은 핵심 미확인

- 사용자앱의첫Vehicle값·listener·권한부여·모든차종지원,실차데이터정확도.
- 현재artifact에서경고등권한상충해소·일반앱허용,AAOS타이어3P/실압지원.
- 국내Developers의정확센서스키마·제한·상용심사/공유차량조건과정밀정비데이터접근.
- Gleo승인기한·에뮬레이터실행·자유문장분석publicendpoint/요금·인증상세.
- 앱미실행자동수집/시동OFF보존,차량ShareSheet/SAF/전화·브라우저/모바일데이터동기화.
- 정비망공개접수API·외부정비사의자료수용·실제상담개선/사용의향.
- 실제주간·월간baseline·외기/충전/운행조건·원인확정모델. 현자료부족시분석보류.

### 서비스·경쟁 근거

- C01 https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/bluelink-vehicle-care
- C02 https://www.hyundai.com/kr/ko/service-membership/car-management-service/remote-care/preemptive-care
- C03 https://www.hyundai.com/kr/ko/service-membership/car-management-service/remote-care/data-driven-maintenance
- C04 https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/car-share
- C05 https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/bluelink-vehicle-care/regular-inspection-report
- C06 https://www.kia.com/kr/customer-service/kia-members/kia-app/mycar
- C07 https://members.kia.com/kr/view/qnet/asn_prct/qnet_asn_prct_index.do ; https://members.kia.com/kr/knet/selectRsvPage.do
- C08 https://www.hyundai.com/worldwide/en/newsroom/detail/0000001168
- C09 https://help.fleetio.com/inspections/inspections-item-failures
- C10 https://kb.samsara.com/hc/en-us/articles/115000771767-Driver-Vehicle-Inspection-Reports-DVIRs ; https://www.samsara.com/products/telematics/fleet-maintenance
- C11 https://www.carscanner.info/en/records/
- C12 https://www.fixd.com/
- C13 https://support.obdeleven.com/en/articles/5608816-what-is-freeze-frame-data
- C14 https://www.alldata.com/us/en/support/diagnostics/article/live-data-recording-playback-overview ; https://www.alldata.com/us/en/support/diagnostics/article/export-dtc-scan
- C15 https://www.mobis.com/kr/aboutus/press.do?category=press&idx=6338
- C16 https://www.hyundaimotorgroup.com/ko/story/hyundai-motor-company-pleos-connect-app-idea-contest
- C17 https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-24h-code-race-hackathon

### 사용자 문제·공식 상담

- P01 https://www.themotorombudsman.org/knowledge/garage-cannot-find-a-intermittent-car-fault
- P02 https://www.consumerinsight.co.kr/boardView?no=3807&id=pr4_list (조사·수치만짧게요약,원문표/장문재배포안함)
- P03 https://www.hyundai.com/kr/ko/customer-service/service-network/service-reservation-center.html ; https://www.hyundai.com/kr/ko/customer-service/service-network/service-reservation-search.html
- P04 https://www.aaa.com/autorepair/articles/how-to-find-a-good-mechanic-a-5-step-guide

### 기술 공식 근거

- D01 https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- D02 https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- D03 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-fuel-percent
- D04 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-current-distance-driven
- D05 https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-battery-state-of-charge
- D06 https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-charge-switch-state
- D07 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/entry
- D08 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-telltales
- D09 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info
- D10 https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature-current
- D11 https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide
- D12 https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability (직접 본문 우선, 오래된 검색 O/X표를 현재 지원으로 확정하지 않음)
- D13 https://developer.android.com/reference/android/car/VehiclePropertyIds#TIRE_PRESSURE ; https://developer.android.com/reference/android/car/Car ; https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager
- D14 https://document.pleos.ai/api-reference/vehicle-data-api/intro
- D15 https://document.pleos.ai/en/api-reference/vehicle-data-api/vehicle-data-api/common/get-vehicle-data ; https://document.pleos.ai/en/api-reference/vehicle-data-api/vehicle-data-api/common/get-powertrain-data ; https://document.pleos.ai/en/api-reference/vehicle-data-api/vehicle-data-api/common/get-vehicle-operation-data
- D16 https://document.pleos.ai/en/api-reference/fleet-api/getting-started ; https://document.pleos.ai/en/api-reference/fleet-api/fleet-rest-api/rest-api-overview
- D17 https://document.pleos.ai/en/api-reference/fleet-api/intro
- D18 https://developers.hyundai.com/web/v1/hyundai/data_api ; https://developers.hyundai.com/web/v1/hyundai/guide_api ; https://developers.hyundai.com/web/v1/hyundai/faqs ; https://developers.kia.com/web/v1/kia/data_api ; https://developers.kia.com/web/v1/kia/guide_api ; https://developers.kia.com/web/v1/kia/faqs (일부직접fetch인증서오류로공식검색색인도활용)
- D19 https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts
- D20 https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/app-capability
- D21 https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema
- D22 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro ; https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions
- D23 https://document.pleos.ai/en/docs/pleos-only/release-note/emulator-release-note
- D24 https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide ; https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- D25 https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start ; https://developer.android.com/develop/background-work/services/fgs/service-types
- D26 https://developer.android.com/reference/androidx/work/PeriodicWorkRequest ; https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- D27 https://developer.android.com/guide/topics/data/data-storage
- D28 https://developer.android.com/training/sharing/send
- D29 https://developer.android.com/reference/androidx/core/content/FileProvider
- D30 https://developer.android.com/training/data-storage/shared/documents-files
- D31 https://developer.android.com/reference/android/graphics/pdf/PdfDocument
- D32 https://firebase.google.com/docs/database/android/start ; https://firebase.google.com/docs/database/security ; https://firebase.google.com/docs/cloud-messaging ; https://firebase.google.com/docs/cloud-messaging/server-environment ; https://firebase.google.com/docs/cloud-messaging/send/admin-sdk ; https://firebase.google.com/docs/cloud-messaging/android/get-started ; https://firebase.google.com/pricing?hl=ko
- D35 https://source.android.com/docs/automotive/users_accounts/multi_user ; https://source.android.com/docs/automotive/users_accounts/accounts ; https://source.android.com/docs/devices/admin/multiuser-apps
- D33 https://github.com/Pleos-Playground/pleos-sample-vehicle-app
- D34 https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing
- D36 https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/main/app/src/main/java/ai/pleos/playground/sample/vehicle/explorer/PublicApiPolicy.kt

### 공모전

- F01 https://appdevchallenge.auto-hmg.io/guide/schedule
- F02 https://appdevchallenge.auto-hmg.io/guide/submission ; https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006
- F03 https://appdevchallenge.auto-hmg.io/guide/judging-awards

## 14. 기존 NextDrive·증상 카드·신규11개 대비 개선 여부

| 대상 | 유지할 강점 | 이번 통합안이 보완할 부분 | 더 나쁘게 만들 수 있는 점 |
|---|---|---|---|
| 기존NextDrive | 공동차미해결인계·이슈구조 | 가족외에도사용,실제관측출처·조치재발·상담한장 연결 | 서버/계정/예약/경고/차트를전부넣으면범위폭증 |
| 기존증상순간기록 | 한사건기록·정차보완·진단배제 | 사건을다음사용자의미해결확인·조치·재발·상담으로확장 | 입력단계/프로필을강제하면단순메모보다번거로워짐 |
| 신규N1정차큐 | 넓은일상·보류후주차처리 | CARITDA는정비문제로좁혀출력과상담목적이구체적 | 대상빈도는낮고차량센서유용성이약하면큐보다불리 |
| 신규N2휴식약속/N3새차적응 | 강한피로문제/간단정차시연 | 이번안은하나의문제결과물이남음 | 정비기록가치검증안되면쉬운대안보다위험 |
| 신규N4~N11 | 시야준비·멀미·타이어루틴·하차·눈부심·환기·도착오디오·리셋 | 각중복·직접해결한계는이전보고서그대로참고 | 이문제들을추가탭으로흡수하지않음. 단순상태/리포트추가로신규성주장금지 |

**개선된 것은 기능 수가 아니라 구조와 주장 경계다.** 사용자표현과실제관측을분리하고,동일사건을인계·조치·재발·상담까지이어가는방향은기존안보다명확하다. 하지만비교시험전에는개선효과가검증된것은아니다.

### 세 결론의 선택·반대근거

- '현재 아이디어 유지하되 기능 축소': 범위축소는필요하나,OEM대체모니터링/AI중심구조를그대로두면핵심권한·수명주기의존이남아단독결론으로불충분.
- **'핵심 아이디어 유지하고 기술적 구조 수정': 선택.** 로컬사건·품질표시·규칙요약을기본층으로두고읽기·Gleo·원격·모바일·정비망을분리한다. 반대근거는OEM이미충분할가능성·수요/정비사유용성미검증이다.
- '다른 후보가 명백히 우수해 교체': 현재증거로명백한우월성없어미선택. 향후SDK게이트실패또는같은과제비교에서OEM/메모가충분하면교체가능성을열어둔다.

**최종 권고: A의 사건 흐름을 먼저 진짜로 작동시키고, 데이터가 유용할 때만 SOC 차트1개를 붙인다. 통합은 유지하되, 자동진단·상시감시·자동접수·무료범용Gleo분석을 성공 전제로 두지 않는다.**
