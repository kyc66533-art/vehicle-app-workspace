# Aside 독립 리서치: 현대자동차그룹 차량용 앱 공모전

- 조사 기준일: 2026-11-06 (KST)
- 문서 성격: 완료된 독립 조사 결과와 다음 검증 제안. 팀의 최종 기획 확정이나 구현 완료 보고가 아니다.
- 공유 저장소: kyc66533-art/vehicle-app-workspace
- 공유 대상 파일: docs/research/ASIDE_RESEARCH.md
- 범위: 운전자 불편 재조사, 기존 서비스·수상작, 후보 5개, 공개 SDK/API, 최종 권고. 이전 NextDrive 및 잠정명 카잇다 검토의 핵심 제한도 부록에 보존한다.
- 검증 수준: 공식 문서·조사·공개 후기 확인. 이번 조사에서 이용자 인터뷰, 정비사 평가, 실제 앱 빌드·설치·에뮬레이터 값 수신·실차 시험은 하지 않았다.
- 운영 원칙: 사용자 요청에 따라 향후 완료 리서치를 이 파일에 갱신·커밋한다. 다른 저장소 파일·코드·설정은 변경하지 않는다. 로그인 유지에 도움이 되도록 로그아웃하지 않되 인증 만료를 방지할 수 있다고 보장하지 않는다.

## 핵심 결론

**권고 선택: 기존 NextDrive의 가족 공동 차량 종합 인계 기획을 폐기하고, 간헐적인 차량 증상을 정비소에 설명하기 위한 기록 문제를 선택한다.**

이는 추천이지 최종 기획 확정이 아니다. 기존 코드나 문제 카드 구조까지 버리라는 뜻도 아니다. 최종 팀 결정 전에는 새 서비스 기능 개발 범위를 확정하지 않는다.

추천 후보는 진단 AI가 아니라 **증상 순간 기록 카드**다. 증상을 표시한 시점의 확보 가능한 차량 값을 남기고, 안전하게 주차한 뒤 빠진 설명만 짧게 보완해 정비 상담용 기록을 완성한다.

- 충전 실패는 수요 근거가 강하지만 충전기·인증·결제 시스템 접근이 부족하다.
- 주차 복귀·차량 기능 설명에는 이미 강한 해결 기능이 있다.
- 가족 인계는 구현할 수 있지만 문제 크기와 기존 채팅 대비 가치가 미검증이다.
- 증상 기록 역시 세계 최초가 아니다. ALLDATA의 사건 마커, OBD 도구의 기록·freeze frame·보고서가 선행한다.
- 남은 차별화 가설은 비전문 운전자의 입력 부담을 낮추고, 한 번의 사건만으로 설명에 쓸 수 있는 기록을 완성하는 UX다.
- 독창성과 시장 수요가 모두 확정된 수상 유력 아이디어를 발견했다는 결론은 내리지 않는다.

## A. 실제 운전자 불편 분석

### 근거 구분

- 조사: 특정 국가·표본의 응답이며 전체 운전자에게 일반화하지 않는다.
- 실험: 작업 부담을 측정한 것으로 불편 경험률과 다르다.
- 후기: 실제 공개 사례이나 대표성·빈도·현재 지속 여부는 미확인이다.
- 공식 한계: 제조사·사업자가 안내한 제약이며 경험자 비율은 아니다.
- PP100은 차량 100대당 문제 건수다. 응답률이나 실패 확률과 혼동하지 않는다.

| 번호 | 실제 문제 | 확인 근거·범위 | 앱이 해결할 수 있는 범위와 한계 |
|---|---|---|---|
| A01 | Android Auto·CarPlay 연결 문제 | J.D. Power 2026 미국 IQS, 신차 구매·리스 78,514명, 소유 90일. 연결 문제 전년 대비 +1.4 PP100 [S01]. 절대 실패율이나 1.4%라는 뜻은 아님 | 실패 동선 안내는 가능하나 차량·휴대폰 OS 연결 장애를 별도 앱이 고칠 수 없음 |
| A02 | 화면 조작의 주의 분산 | 같은 IQS에서 주의분산 문제를 구체 서술한 하위 집단 중 46%가 인포테인먼트·터치스크린을 지목 [S01]. 전체 46%가 아님 | 기능을 늘리기보다 입력·확인 단계 감소가 중요 |
| A03 | 목적지 입력의 부담 | AAA·Utah 2017 미국 실도로 실험, 21~36세 120명·차량 30종, 목적지 입력 평균 약 40초 [S02]. 작업 완료 시간이며 연속 눈떼기 시간이나 2026 발생률 아님 | 주행 중 긴 입력을 요구하지 않을 것. 음성도 인지 부담이 있음 |
| A04 | 급속충전 시도 실패 | CI 2025 전기차 조사, 보유자 1,809명 중 73%가 10회 중 1회 이상 실패 응답 [S03]. 시도의 73%가 실패했다는 뜻이 아님 | 중요한 문제지만 충전기 고장·인증·결제·설정에 원인이 분산됨 |
| A05 | 충전비·이용 조건 부담 | 같은 보고서에서 높은 충전비가 불편 이유로 제시되고 비용의 평가 중요도가 가장 높음 [S03] | 회원·로밍·주차비·시간 조건까지 정확히 알려면 사업자 데이터 필요 |
| A06 | 정비 예약 후 대기 | CI 2025.7 조사, 최근 1년 AS 경험자 31,474명, 예약 후 평균 9.5일 [S04]. 수리 작업 기간이나 운행 불능 기간 아님 | 예약·접수 효율은 도울 수 있으나 인력·부품 부족 자체는 해결 못함 |
| A07 | 수리 후 동일 문제 재발 | 같은 AS 조사에서 국산 6.9%, 수입 10.5% 자기보고 [S05]. 오진 독립감사 결과 아님 | 이전 증상·조치·재발 시점의 기록은 유효한 검증 가설 |
| A08 | 정비소에서 간헐 증상 미재현 | 국내 2026 사례 보도, Motor Ombudsman 안내, Volvo VIDA 정비 자료가 문제 존재·발생 조건 확인 필요성을 지지 [S06~S08]. 전국 빈도는 미확인 | 차량 진단 대신 사용자 경험과 확보 가능한 상황 기록을 보존 |
| A09 | 사전 설명과 다른 정비 내용 | CI AS 조사에서 5.8% 경험 자기보고 [S05] | 차량 데이터만으로 정비 동의·적정성을 판정 못함. 작업·견적 연동 필요 |
| A10 | 지하주차장 위치·층·복귀 경로 | 현대 공식 GPS 오차 수십~수백m 안내, 카카오 실내 주차 문제 정의 [S09~S10]. 빈도 통계 아님 | 좌표만으로 해결 불가. 그러나 층·기둥 메모도 기존 상용 기능 |
| A11 | 업데이트 후 재로그인 불안 | Kia App 2026-09-01 개별 후기 및 당일 개발자 답변 [S11]. 같은 후기의 다른 조회 오류는 해결됐다는 평가도 있음 | 별도 앱이 OEM 인증 정책을 제거할 수 없음 |
| A12 | 차량 정보 최신성 불신 | Kia App 2026-09-02 배터리 방전 관련 기록이 바뀌지 않는다는 후기, 09-03 캐시 재조회 안내 [S11]. EV SOC 오류로 확대 금지 | 마지막 조회 시각·실측/추정·조회 실패 표시. 원천 오류를 수정하는 것은 아님 |
| A13 | 목적지 전송 진입 단계 번거로움 | 마이현대 2025-11-24 개별 후기의 위젯 목적지 입력 요청 [S12]. 현대·기아 목적지 전송 자체는 이미 제공 [S13~S14] | 기능 부재가 아니라 접근 단계·중복 입력 문제 |
| A14 | 목적지 인근 진입로·고가도로 안내 혼란 | 2024-03-11 공개 커뮤니티 경험 [S15]. 원인·빈도·현재 재현은 미확인 | 입구 사전 확인 가설. 제3자 앱이 지도·경로 엔진을 자동 교정한다고 주장 못함 |
| A15 | 주차권·초과·현장 결제·환불 표시 혼란 | 모두의주차장 2024-10-29 후기. 이용자와 사업자 설명이 달라 부당결제 확정 불가 [S16] | 조건 표시를 개선할 수 있으나 정산·차단기 연동 없이 근본 해결 불가 |

### A의 해석

전체 운전자의 최대 불편 하나를 정할 동일 기준 통합 조사는 확보하지 못했다. 비교적 강한 근거는 디지털 조작·연결, 충전 실패, 정비 대기·재발이다. 다만 문제가 크다는 것과 작은 팀이 공개 API로 해결할 수 있다는 것은 다르다. 공개 후기의 불만을 현재도 열려 있는 장애나 앱 구매 의향으로 취급하지 않는다.

가족 공동차 인계 누락의 빈도와 피해는 이번 조사에서 직접 측정하지 못했다. 과거 2013 KTDB의 조사 대상 차량 월 1회 이상 운전자 평균 1.3명은 참고할 수 있지만, 2026 가족 공유율이나 인계 수요로 환산할 수 없다 [S17].

## B. 기존 서비스와 공모전 선행 사례

| 영역 | 이미 제공되는 기능 | 남은 공백의 성격 |
|---|---|---|
| Gleo AI | 차량 매뉴얼·정비 지침 기반 답변, 설정 검색, 차량 상태 안내, 공조·창문·복합 음성 명령 [S18~S19] | 일반 경고등 통역·기능 찾기는 직접 중복. 별도 앱의 존재 이유가 약함 |
| 마이현대·Kia App | 원격 제어, 차량 공유·상태, 목적지 전송, 충전·정비 서비스 [S11~S14] | 접근성·최신성·실패 후 안내는 가설이나 원천 시스템 문제는 직접 해결 못함 |
| ABRP·Tesla | 차량별 충전 경로, 도착/출발 SOC, 권장 충전 시간·진행 상태·알림 [S20~S21] | 필요한 충전량·몇 분 더 충전은 이미 상용화 |
| PlugShare | 작동·대기·결제·편의시설 체크인과 최근 후기 요약. 2026-07-01 공식 Check-in Summaries 발표 [S22] | 충전 실패 후기의 AI 요약 자체는 신규성이 없음 |
| KEPCO PLUG·ChargePoint | 충전 위치·상태·요금·진행·결제·알림, 제품별 문제 해결 절차 [S23~S24] | 내 차와 현장 인증·결제·장비를 연결하려면 현재 세션 데이터 필요 |
| 현대 기본 EV 화면 | 충전 시작 후 SOC·잔여시간·주행거리 표시, 예약 대기·실패 확인 절차 [S25~S26] | 충전 중이라는 사실을 다시 보여주는 것만으로 가치 부족 |
| Apple Maps·여기주차·카카오 | 자동 주차 핀, 메모, 층·구역·기둥 음성메모·사진, 실내 측위/복귀 보조 [S10,S27~S28] | 일반 주차 기록 자체는 강하게 중복. 실제 기존 방법 실패 상황을 특정해야 함 |
| Car Scanner·FIXD·OBDeleven | OBD 로그·재생·내보내기·DTC 해석·freeze frame [S29~S31] | 비전문가에게 필요한 설명을 낮은 부담으로 확보하는 UX는 검증 가설 |
| ALLDATA Diagnostics | 실시간 PID 기록, Mark 버튼 사건 표시, Skip to Marker, DTC 보고서 내보내기 [S32~S33] | 사건 마커+로그+정비 보고서도 최초가 아님 |
| Fleetio·Samsara·Motive·Geotab | 미해결 결함 지속, 담당자·조치·다음 점검자의 해결 확인·반려 [S34 및 이전 독립 조사] | 문제 카드·해결 릴레이 자체가 NextDrive의 독창성은 아님 |

### 공개 공모전·해커톤

- **2026 현대모비스:** MOBIDIC=AI 차량 진단, MobiMon=차량 정보 캐릭터, Car de Maker=운행 기록 AI 카드. 개발 기반은 InfoLINK/IDELINK/온디바이스 SDK이며 Pleos 상용 앱으로 소개하면 안 된다. MOBIDIC의 세부 입력·재현 지원은 공식 미공개 [S35].
- **2026 부산 Pleos:** 창밖동화=경로·지역 AI 동화, 차마고치=주유·충전·기어 활용 차량관리 게임, 차계부=유지비 자동 관리. 16개 우수작은 기사 기준 아이디어 단계다. 공식 본문에서 이름·기능이 모두 확인되는 것은 15개이므로 16개 전부의 세부 기능을 확인했다고 쓰지 않는다 [S36].
- **2026 24H 코드 레이스:** TaleSync=실시간 주행 데이터·아동 반응 연동 스토리. 일부 우수작은 공식 보도에 이름만 있어 기능을 이름으로 추정하지 않는다. 실제 시연 프로토타입 대회이며 서비스 출시와 다르다 [S37].
- **2016 Mercedes-Benz 해커톤:** MBarc=사진 비교 손상 평가, FleetWise=차량 특성 기반 절전·충전 경로, VANage=정비 주기 예측. 과거 프로토타입 사례이며 현행 상용 서비스로 취급하지 않는다 [S38].

공개 자료에서 특정 전체 조합을 못 찾았다는 사실은 세계 최초·특허 신규성·비침해의 증명이 아니다. 현재 비공개 공모전 출품작과의 중복은 알 수 없다.

## C. 아이디어 5개

### C1. 교대 인계 한 장 — 기존 NextDrive 핵심 축소

1. **불편·근거:** 다음 이용자가 최신 상태와 미해결 사항을 모름. A12는 최신성 관련 사례지만 가족 인계 누락 자체의 빈도·피해는 미검증.
2. **타깃:** 같은 차를 자주 번갈아 운전하는 가족·소규모 업무팀.
3. **실행 상황:** 출발 전 또는 주차 직후.
4. **한 문장:** 다음 운전자에게 달라진 사항과 아직 끝나지 않은 일만 한 장으로 전달한다.
5. **핵심 하나:** 미해결 사항의 다음 사용자 인계.
6. **중복:** Fleetio 등의 문제 지속·확인, OEM 차량 공유. 가족 채팅 대비 이점 검증 필요.
7. **SDK:** Odometer·Fuel 조회와 Capability. 경고등 권한은 필수 의존성에서 제외.
8. **난이도·제약:** 로컬 두 프로필은 낮음~중간. 실제 계정·초대·서버 동기화는 별도.
9. **UX:** 변경된 것만 보이되 매번 확인을 강요하면 번거로움.
10. **공모전:** 구현 안정성은 있으나 문제 크기·독창성이 약함. 예약·차계부·통계·진단 제거.

### C2. 충전 시작 실패 길잡이 — 완전 신규

1. **불편·근거:** A04 급속충전 실패. 현재 후보 중 규모 근거가 강함.
2. **타깃:** 공공 급속충전에 익숙하지 않은 EV 운전자.
3. **실행 상황:** 충전기를 연결했지만 시작되지 않거나 시작 여부가 불분명할 때.
4. **한 문장:** 확인 가능한 사실을 정리하고 다음 행동 하나를 안내한다.
5. **핵심 하나:** 충전 실패 상황의 다음 행동 선택.
6. **중복:** 제조사 매뉴얼·사업자 문제 해결·PlugShare 우회 팁. 충전 상태 표시나 후기 요약만으로 차별화 불가.
7. **SDK:** SOC와 충전 Boolean 상태 및 Capability. CPO 세션·인증·결제 데이터는 별도.
8. **난이도·제약:** 중간~높음. Boolean false로 고장·미연결·예약 대기를 구분 못함. SOC가 잠시 그대로라고 실패 판정 금지.
9. **UX:** 짧은 분기와 확인 불가 표시. 위험한 분해·강제 분리·전류 변경 유도 금지.
10. **공모전:** 문제는 크지만 해결 권한과 핵심 정보가 외부에 있음. 사업자 접근 경로 확보 시 재검토.

### C3. 주차 복귀 카드 — 완전 신규

1. **불편·근거:** A10 지하주차장 위치·층·복귀 문제.
2. **타깃:** 처음 방문하는 대형 시설 이용자.
3. **실행 상황:** 주차 직후 층·기둥·출입구를 짧게 남길 때.
4. **한 문장:** 내 차의 층·기둥·출입구를 남기고 휴대폰에서 다시 본다.
5. **핵심 하나:** 차량 복귀용 위치 맥락 저장.
6. **중복:** Apple Maps·여기주차·카카오 실내 주차와 강하게 중복.
7. **SDK:** STT와 FusedLocation. 좌표는 보조이며 층·기둥 자동 인식 근거 없음.
8. **난이도·제약:** 메모는 낮음~중간. 정확한 실내 측위는 별도 지도·인프라 필요.
9. **UX:** 간단하나 스마트폰 기존 앱보다 차량 앱이 나은 이유가 약함.
10. **공모전:** 구현은 쉬워도 차량 특화·독창성 부족. 주력 제외.

### C4. 목적지 입구 확인 카드 — 완전 신규

1. **불편·근거:** A14 목적지 인근 진입 혼란. 개별 사례 수준.
2. **타깃:** 복잡한 병원·물류시설·상가에 처음 방문하는 운전자.
3. **실행 상황:** 출발 전에 실제 차량 진입구를 확인할 때.
4. **한 문장:** 주소가 아니라 들어갈 입구와 접근 정보를 먼저 확인한다.
5. **핵심 하나:** 출발 전 진입구 확인.
6. **중복:** 기존 내비의 주차장 검색·거리뷰·시설 안내와 인접.
7. **SDK:** getDestinationInfo → onDestinationInfo, 선택 시 requestRoute. 조회 NAVI_ROUTE, 경로 요청 NAVI_ROUTE_SEARCH.
8. **난이도·제약:** 높음. SDK보다 신뢰할 입구 데이터 확보·갱신이 어려움. 지도 오류 자동 교정 주장은 금지.
9. **UX:** 주차 중 한 번 확인. 잘못된 진입 정보의 피해를 고려해야 함.
10. **공모전:** 소수 시설 시연은 가능하나 확장·데이터 운영 부담 큼. 파트너 없으면 제외.

### C5. 증상 순간 기록 카드 — 문제 카드와 잡소리노트 재결합

1. **불편·근거:** A07·A08. 증상이 사라져 정비소에서 설명·재현하기 어렵고 조치·재발 경과가 흩어짐.
2. **타깃:** 간헐적인 소음·진동·작동 이상을 경험하는 운전자. 가족차로 제한하지 않음.
3. **실행 상황:** 증상을 느낀 시점을 짧게 표시하고 안전하게 주차한 뒤 보완.
4. **한 문장:** 방금 느낀 증상에 당시 확보한 차량 정보를 붙이고 정차 후 빠진 설명만 묻는다.
5. **핵심 하나:** 정비 상담에 쓸 수 있는 한 번의 증상 기록 완성.
6. **중복:** ALLDATA 마커·기록, Car Scanner 로그, MOBIDIC 진단 분야와 인접. 최초가 아니라 비전문가의 낮은 입력 부담이 차이 가설.
7. **SDK:** Odometer·HVAC, 선택 STT·위치·AAOS 차속. 경고등·ECU·고장코드는 필수 아님.
8. **난이도·제약:** 중간. 읽기 전용으로 제한 가능. 주행 중 음성 호출·정책·실제 값 수신 검증 필요.
9. **UX:** 발생 표시 후 정차 중 최대 세 질문. 모르면 건너뜀. 매번 운행 시작·종료 입력 요구하지 않음.
10. **공모전:** 문제·데이터 연결이 구체적이고 한 사건으로 결과물이 생김. 실제 정비 도움과 지불 의향은 미검증.

#### C5 예시 흐름

1. 허용된 음성 경로 또는 주차 중 버튼으로 증상 발생을 표시한다.
2. 요청 시각과 수신 가능한 차량 값을 로컬에 기록한다.
3. 주차 후 위치·느낌·발생 상황 등 부족한 설명만 최대 세 항목 보완한다.
4. 사용자 표현, 값의 출처·조회 시각, 관련 조치·재발을 한 장의 기록으로 정리한다.

주행 중 음성 실행이 검증되지 않으면 동작한다고 홍보하지 않는다. 정차 후 회고 기록으로만 작동하면 증상 순간 기록 가치가 약해진 것으로 재평가한다. 운전자에게 증상을 일부러 재현하는 위험한 주행을 요청하지 않는다.

## D. 자체 비교 평가

공식 항목은 차량 특화성·창의성·기술 활용성·사업성이다. 공식 배점은 비공개이며 아래는 UX·기간 내 구현을 추가한 Aside 자체 판단이다 [S39].

- 각 1~5점, 동일 가중치, 총 30점.
- 1: 적합성·근거·통제력이 약함. 3: 가능성 있으나 중요 검증이 남음. 5: 비교 항목의 적합성이 높음.
- 사용자 테스트·수상 확률·공식 심사 결과가 아니며 검증에 따라 순위가 바뀔 수 있다.

| 후보 | 차량 특화 | 창의 | 기술 활용 | 사업성 | UX | 기간 내 구현 | 합계 |
|---|---:|---:|---:|---:|---:|---:|---:|
| C5 증상 순간 기록 | 5 | 3 | 4 | 3 | 4 | 3 | 22 |
| C2 충전 실패 길잡이 | 5 | 2 | 3 | 3 | 3 | 2 | 18 |
| C1 교대 인계 한 장 | 3 | 2 | 3 | 2 | 3 | 4 | 17 |
| C3 주차 복귀 | 2 | 1 | 2 | 2 | 4 | 4 | 15 |
| C4 입구 확인 | 3 | 2 | 3 | 2 | 3 | 2 | 15 |

C5는 차량 값과 사건의 연결이 분명하지만 전문 도구 선행 때문에 창의성을 높게 부여하지 않았다. C2는 문제는 크지만 데이터·해결 권한이 외부에 있다. C1은 구현이 쉬워도 가족이 별도 앱을 쓸 이유가 미검증이다. C3는 휴대폰으로도 대부분 가능하고 C4는 입구 데이터가 병목이다.

## E. 상위 3개

### 1위 C5 증상 순간 기록

- 사용자 가치: 기억에만 의존하지 않고 사라진 증상과 조치·재발 경과를 설명.
- 장점: 구체적인 사용 순간, 가족 가입·결제 연동 불필요, 한 번 기록해도 결과물, 진단 없이 성립.
- 단점: 매일 쓰지 않음, 공개 값이 전문 진단에 충분하지 않을 수 있음, 기록·마커 선행, 실제 정비 도움 미검증.
- 실현 가능성: 읽기 전용·로컬 저장 중심. 초반 음성·값 수신 검증 필수.

### 2위 C2 충전 실패 길잡이

- 사용자 가치: 불필요한 재시도 대신 다음 행동 이해.
- 장점: 국내 수요 근거가 강하고 차량·현장 상태를 연결할 가치.
- 단점: 예약·결제·인증·고장을 구분할 데이터 부족, 기존 매뉴얼·사업자 지원 중복, 잘못된 조작 안내 위험.
- 실현 가능성: 관측·사용자 선택 안내까지. 자동 진단·복구·전원 종료 후 감시는 제외.

### 3위 C1 교대 인계 한 장

- 사용자 가치: 공동차의 미해결 사항을 다음 이용자에게 남김.
- 장점: 범위 예측·기존 구조 재사용이 쉬움.
- 단점: 채팅·공유 앱 대비 가치 미검증, 복수 사용자 참여 필요, 선행 기능 존재.
- 실현 가능성: 로컬 시연 예비안. 쉬운 구현이 높은 사업성을 뜻하지는 않음.

## F. 최종 추천과 다음 검증

**선택: 기존 아이디어 전면 폐기 및 신규 아이디어 선택.** 폐기 대상은 가족 공동 차량 종합 인계를 중심으로 한 NextDrive 주력 기획이다. 팀 결정·사용자 승인 없이 저장소 코드나 사양을 변경하지 않는다.

선택할 문제는 **차량의 간헐 증상이 사라지기 전에 상황을 남기고 정비소에서 설명할 수 있게 만드는 것**이다. 서비스 범위는 진단이 아닌 정비 상담용 기록 보조로 제한한다.

### MVP 필수

1. 증상 발생 표시.
2. 확보 가능한 차량 값과 요청·수신 시각 로컬 저장.
3. 주차 후 부족한 설명 최대 세 항목 보완.
4. 한 장의 기록 조회·공유.

### 제외

가족 계정, 차량 예약, 경고등 자동 진단, 소음 원인 AI 판정, 정비소 예약·결제, 재현 예보, 통계·점수, 자동 해결 판단, 시동 차단·차량 제어, 무조건 녹음·위치 수집.

### 개발 전 48시간 기술 게이트

- 누적거리·공조 설정값을 실제 SDK에서 수신하는가?
- 권한·Capability·지원 영역과 null을 처리하는가?
- 음성 기록을 허용된 방식으로 실행할 수 있는가?
- 프로세스 재실행 후 기록이 남는가?
- 실제 SDK·Console 신호·앱 내부 fixture를 화면과 발표에서 구분하는가?

### 아직 수행하지 않은 사용자 가치 검증

- 실제 간헐 증상 경험자가 설명할 때 빠뜨린 항목은 무엇인가?
- 정비사는 어떤 항목을 유용하게 보는가?
- 휴대폰 메모보다 입력 부담이 줄어드는가?
- 지표 후보: 필요한 정보 충족도, 추가 질문 수, 기록 완료 부담. 재현 성공률 개선은 실험 전 확정하지 않는다.

## G. 기술 검증 요약

아래는 공식 문서 확인 수준이다. 실제 앱 컴파일·권한 승인·Console 값 수신·실차 적용과는 다르다.

| 기능 | 정확 경로·권한 | 확인 내용과 제한 |
|---|---|---|
| 연료 | Fuel.getFuelPercent(), registerFuelPercent(), checkFuelPercentCapabilities(); CAR_ENERGY normal | % 조회. null·차종별 지원 처리. Console 연료% 조작 미확인 |
| 누적거리 | Odometer.getCurrentDistanceDriven(), registerDistanceDriven(), checkCurrentDistanceDrivenCapabilities(); CAR_DRIVE normal | km. 일부 트립 getter는 m이므로 혼용 금지. Console 조작 명시 |
| EV SOC | EvBattery.getEvBatteryStateOfCharge(), registerEvBatteryStateOfCharge(), checkEvBatteryStateOfChargeCapabilities(); CAR_ENERGY normal | Int? %. Console 0~100%, 1% 단위. 실제 수신 미검증 |
| EV 충전 상태 | getEvChargeSwitchState(Boolean?), checkEvChargeSwitchCapabilities(); CAR_ENERGY normal | 충전 상태 Boolean. false는 케이블 미연결·고장·예약대기 중 어느 것인지 확정하지 못함 |
| 충전 추가 정보 | 현재 조사한 EvBattery 인터페이스 | 케이블 실제 연결, 남은 충전시간, 현재 목표 SOC getter, 실제 충전전력 조회 미확인. 필수 범위 제외 |
| 공조 설정온도 | HVAC.getHvacTemperature(area,unit,...); CAR_CLIMATE normal | 실제 실내온도가 아닌 목표 설정온도 |
| 현재 실내온도 | getHvacTemperatureCurrent(area,...), checkHvacTemperatureCurrentCapabilities(CapabilityConfig,List<SeatArea>); CAR_CLIMATE normal | 현재 °C. Console 현재온도 조작 카드는 가이드 목록에서 미확인 |
| AC·성에제거 | getHvacAcState(SeatArea,...), getHvacDefrosterState(WindowArea,...); CAR_CLIMATE normal | 각 영역 상태. 관련 Capability와 지원 영역 확인 필요 |
| 위치 | FusedLocation.isSupported(), initialize(), getLastKnownLocation(), registerFusedLocationCallback(), release(); FUSED_LOCATION dangerous | 마지막 위치는 새 계산 아님. null/오류·원본 timestamp·privacy block 처리. GPS로 지하 층 확정 불가 |
| 음성 | SpeechToText.initialize(), addListener(), request(), stop(), release(); STT_SERVICE 선언 | ON_DEVICE는 네트워크·서버 등록 불필요. permission protection 수준·실제 환경은 추가 확인. STT는 텍스트 변환이며 명령 실행 자체가 아님 |
| Gleo 연동 | App Schema, Gleo Action/Callback | actionCalls 최대3. component service는 sendBroadcast 경로. 상시 백그라운드·자동실행 보장 아님 |
| 내비 | NaviHelper.getDestinationInfo()→onDestinationInfo(); NAVI_ROUTE. requestRoute(); NAVI_ROUTE_SEARCH | 콜백으로 결과 확인. 요청 성공이 경로 완료 의미 아님. 내비 탑재·현재 구현에 의존 |
| 차속 선택 항목 | AAOS CarPropertyManager의 PERF_VEHICLE_SPEED, getFloatProperty(); android.car.permission.CAR_SPEED dangerous | m/s. Pleos FAQ도 경로 안내. 실제 권한·property 지원은 미검증 |
| 경고등 | Safety.registerTelltales(), checkTelltalesCapabilities(): PRIVATE_CAR_SAFETY signature/privileged | 일반 앱 Manifest 선언만으로 사용 보장 못함. getTelltalesInfo는 어노테이션과 본문 권한 상충. [blocked] 공식 답변 전 핵심 의존성 제외 |
| 타이어 | Tire.getLowTirePressure(area,...); CAR_SAFETY | 현재 공기압이 아닌 critically low pressure threshold(kPa). 현재 압력·저압 여부로 사용 금지 |
| 주행 UI | CarUxRestrictionsManager.registerListener()→onUxRestrictionsChanged() | 플랫폼 제한 반영. DO 선언만으로 모든 기능 허용 아님 |
| 전원·저장 | 일반 Android 로컬 저장 설계 | onDestroy 보장 없음. 관측·조작 시 저장하고 마지막 전원 종료 순간 전송에 의존하지 않음. 시동 자동실행·자동 운행 종료는 미확인 |
| 외부 공유 | HTTPS 서버·휴대폰 알림 등 추가 구성 | Pleos 데이터 반출·배포 정책 별도 확인. 원격 Vehicle Data API는 공식 안내상 2026.8 기준 유럽 한정, 국내 대체경로로 확정 금지 |

### 기술적 안전 원칙

- CapabilityConfig.accessType은 NONE/READ/WRITE/READ_WRITE. Vehicle SDK에 임의 isSupported 필드가 있다고 가정하지 않는다. FusedLocation의 isSupported와 구분한다.
- 비동기 getter 여러 개는 완전 동시 계측이 아니다. 원본 측정시각이 없으면 앱 요청·수신 시각으로 표시한다.
- API 문서 존재, Console 조작 가능, 앱이 수신 가능, 실차 지원은 네 가지 별도 주장이다.
- 설정온도로 실제 실내온도·외기온을 대체하지 않는다.
- 경고 없음·녹색·미조회 값을 운행 안전 보증으로 바꾸지 않는다.
- 현재 압력·DTC·RPM·조향 등을 공개 접근 검증 없이 있다고 설명하지 않는다.
- AI는 사용자 메모 구조화·짧은 설명 보조에만 사용 가능. 핵심 기록은 AI·인터넷 실패에도 남겨야 한다.
- 재현 조건을 분석하더라도 인과·고장 확정이나 위험한 재현 주행을 권하지 않는다.

## H. 이전 브랜드 검토 보존

- 기존 NextDrive는 차량관리 앱명으로 실제 선행 사용 확인 [S40].
- 잠정명 카잇다(CARITDA)는 2026-11-06 KIPRIS 국내 명칭 완전일치 검색 0건이었으나 사용 가능 확정 아님.
- 차량관리 앱 차잇(CHAIT), 자동차 할부승계 서비스 차잇다가 실제 존재 [S41~S42].
- KIPRIS 차잇다 출원 40-2026-0185623, 2026-09-04, 메리트앤메소드 주식회사. 9류 소프트웨어 및 42류 플랫폼·소프트웨어 개발 관련 지정상품·서비스 포함을 확인. 출원 중이며 등록 완료로 쓰지 않는다.
- KIPRIS에는 카잇(CARIT), 등록 40-2025967의 자동차 용품·관련 소매업 선행 등록도 확인됐다.
- 카잇다 최종 브랜드 확정은 보류 권고. 상표의 외관·호칭·관념과 구체 지정상품을 함께 검토해야 한다. 같은 소프트웨어 유사군이라고 자동 충돌도, 기능이 다르다고 자동 안전도 아니다.
- 이 보고서의 신규 후보명은 기능 설명용이며 브랜드 승인·상표 안전성 확인이 아니다.

## I. 공모전·공유 상태

- 공식 확인 일정: 1차 접수 2026-11-15, 최종 PT 12-03~04. 일정 변경은 공식 공지 재확인 [S39,S43].
- 1차 기획서만 가능, 개발 산출물 선택 가산점. 2차 실제 동작 앱 필수이며 에뮬레이터 화면 또는 기기 시연 인정 [S44].
- GitHub 브라우저 로그인과 main 쓰기는 ASIDE_SYNC_TEST.md 추가 커밋 2280ea3c6f764b7851b7d32852c77e541cc901aa로 검증했다. 이는 저장소 한정 GitHub App 권한을 별도로 설정했다는 뜻은 아니다.
- 이 문서의 업로드는 리서치 공유이며 다른 사양·코드·설정을 변경하지 않는다.

## J. 출처 목록

각 URL은 해당 조사에서 확인한 근거다. 게시 이후 변경될 수 있으며, 오래된 연구·개별 후기의 상태와 한계는 본문에 표시했다. 일부 J.D. Power 원문은 직접 fetch가 차단돼 공식 검색 발췌를 이용했다. 시장 미발견은 전수조사·부재 증명이 아니다.

- [S01] J.D. Power 2026 US IQS: https://www.jdpower.com/business/press-releases/2026-us-initial-quality-study-pr
- [S02] AAA 2017 실도로 인지부하 연구: https://aaafoundation.org/research/visual-cognitive-demands-using-vehicle-information-systems/ ; 보도 https://newsroom.aaa.com/2017/10/new-vehicle-infotainment-systems-create-increased-distractions-behind-wheel
- [S03] CI 2025 전기차 충전 경험: https://www.consumerinsight.co.kr/boardView?id=pr4_list&no=3822
- [S04] CI 2025 AS 시간·비용: https://www.consumerinsight.co.kr/boardView?no=3809&id=pr4_list
- [S05] CI 2025 AS 정비 결과: https://www.consumerinsight.co.kr/boardView?no=3807&id=pr4_list
- [S06] 국내 간헐 증상·고장코드 분쟁 사례: https://www.consumernews.co.kr/news/articleView.html?idxno=760462
- [S07] Motor Ombudsman 간헐 고장 안내: https://www.themotorombudsman.org/knowledge/garage-cannot-find-a-intermittent-car-fault
- [S08] Volvo VIDA 간헐 고장 재현 절차: https://vidaresources.volvocars.biz/help-wbt/us/efd21cd03d744710c0a801cc721d3675.htm
- [S09] 현대 주차 위치 확인·GPS 제약: https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/smart-control/parking-location
- [S10] 카카오모빌리티 실내 주차: https://report.kakaomobility.com/indoor-navigation-transforming-parking-experience
- [S11] Kia App 공식 Play·후기: https://play.google.com/store/apps/details?id=com.kia.oneapp.kr&hl=ko
- [S12] 마이현대 공식 Play·후기: https://play.google.com/store/apps/details?hl=ko&id=com.hyundai.oneapp.kr
- [S13] 현대 목적지 전송: https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/smart-control/destination-transfer
- [S14] 기아 목적지 전송: https://www.kia.com/kr/kia-connect/service/remote-control/location
- [S15] 2024 내비 진입 안내 경험: https://www.clien.net/service/board/cm_car/18633864
- [S16] 모두의주차장 공개 후기: https://apps.apple.com/kr/app/780174422?platform=iphone&see-all=reviews
- [S17] KTDB 2013 자동차 이용실태: https://www.ktdb.go.kr/DATA/pblcte/20160310013005320.pdf (본문51쪽 표3-3)
- [S18] Gleo 개발자·현재 기능: https://www.hyundaimotorgroup.com/ko/story/42dot-gleo-ai-developer-present-and-future
- [S19] Gleo 공식 사용설명: https://ownersmanual.hyundai.com/ivi/Connect-S/AVNT/KOR/Korean/003_Corefeatures_1.html
- [S20] ABRP: https://abetterrouteplanner.com/home ; https://abrp.featurebase.app/help/articles/0210787-edit-the-details-of-a-charging-stop-in-your-plan
- [S21] Tesla 충전: https://www.tesla.com/supercharger ; https://www.tesla.com/support/charging
- [S22] PlugShare 후기 요약: https://company.plugshare.com/checkin-summaries-what-8-million-drivers-built-together.html
- [S23] KEPCO PLUG: https://apps.apple.com/kr/app/kepco-plug/id6504782624
- [S24] ChargePoint Home Flex 문제 해결: https://www.chargepoint.com/drivers/home/troubleshooting/flex (특정 가정용 제품의 절차이며 모든 공공충전기에 일반화 금지)
- [S25] 현대 기본 충전 정보 화면: https://ownersmanual.hyundai.com/full_webhelp/CE1/2025/ko_KR/id23C1E0M40UI.html
- [S26] 현대 충전이 시작되지 않는 경우: https://ownersmanual.hyundai.com/full_webhelp/NE1/2027/ko_KR/id23C1G00D030.html
- [S27] Apple 주차 위치: https://support.apple.com/en-us/101587
- [S28] 여기주차: https://yooniquelabs.kr/apps/yeogi-parking/
- [S29] Car Scanner 기록: https://www.carscanner.info/en/records/
- [S30] FIXD: https://www.fixd.com/ ; https://fixd.zendesk.com/hc/en-us/articles/360004952152-What-is-freeze-frame-data
- [S31] OBDeleven freeze frame: https://support.obdeleven.com/en/articles/5608816-what-is-freeze-frame-data (차종·요금제 조건 확인 필요)
- [S32] ALLDATA 사건 마커: https://www.alldata.com/us/en/support/diagnostics/article/live-data-recording-playback-overview
- [S33] ALLDATA 보고서: https://www.alldata.com/us/en/support/diagnostics/article/export-dtc-scan
- [S34] Fleetio 미해결·해결 재확인: https://help.fleetio.com/inspections/inspections-item-failures
- [S35] 현대모비스 2026 해커톤: https://www.mobis.com/kr/aboutus/press.do?category=press&idx=6338
- [S36] 부산 Pleos 아이디어 공모전: https://www.hyundaimotorgroup.com/ko/story/hyundai-motor-company-pleos-connect-app-idea-contest
- [S37] 24H 코드 레이스: https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-24h-code-race-hackathon
- [S38] Mercedes-Benz 2016 해커톤 공식 배포: https://www.prnewswire.com/news-releases/algorithms-cloud-data-and-machine-learning-24-hour-hackathon-with-mercedes-benz-in-silicon-valley-300361113.html
- [S39] 현재 공모전 심사: https://appdevchallenge.auto-hmg.io/guide/judging-awards
- [S40] NextDrive 선행 앱: https://play.google.com/store/apps/details?id=com.nextgentechbd.nextdrive&hl=en
- [S41] 차잇 앱: https://play.google.com/store/apps/details?id=com.chait.app&hl=ko
- [S42] 차잇다 서비스: https://www.chaidda.com/ ; KIPRIS https://www.kipris.or.kr/ (본문 출원·등록번호로 재조회)
- [S43] 공모전 일정: https://appdevchallenge.auto-hmg.io/guide/schedule
- [S44] 공모전 시연·제출 유의사항: https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006 ; https://appdevchallenge.auto-hmg.io/guide/submission

### 공식 기술 문서

- Vehicle SDK: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- Fuel: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-fuel-percent
- Odometer: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-current-distance-driven
- EV SOC: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-battery-state-of-charge
- EV charging state: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-charge-switch-state
- HVAC target: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature
- HVAC actual: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature-current
- HVAC capability: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-temperature-current-capabilities
- Tire threshold: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/entry
- Safety events: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-telltales
- Safety getter: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info
- Capability model: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry
- Virtual Console: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- FusedLocation: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro ; https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions
- STT: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro ; https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions
- Gleo schema: https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/app-capability
- NaviHelper: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations ; https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- AAOS speed: https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED ; https://source.android.com/docs/automotive/voice/voice_interaction_guide/fulfilling_commands
- Pleos speed FAQ: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq
- Driving restrictions: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- Lifecycle: https://developer.android.com/guide/components/activities/process-lifecycle
- Remote Vehicle Data API region limitation: https://document.pleos.ai/en/api-reference/vehicle-data-api/intro

## 변경 이력

- 2026-11-06: 2026-11-06 조사 기준의 완료된 아이디어 전면 재검토(A~F), 기술 검증, 기존 브랜드 검토의 핵심 제한을 최초 공유. 추가 인터뷰·실행 검증을 수행한 것으로 표시하지 않음.


---

# 2026-11-07 추가 조사: 공개 도구로 실제 시연 가능한 신규 아이디어

> 이 절은 2026-11-06 기록을 수정하지 않고 추가한 후속 조사다. 아래 판단이 앞 절과 다르면 이 절의 이유와 검증 수준을 함께 읽는다. 이번에도 사용자 인터뷰, 정비사 평가, APK 빌드, SDK 호출, 에뮬레이터 값 수신 및 실차 시험은 수행하지 않았다. '시연'은 앞으로 구현할 계획이지 완료 실적이 아니다.

## 추가 조사 요약

- 기존 5개와 별도로 **신규 후보 11개**를 발굴했다. 일반 운전자의 기억 부담·휴식·차량 조작·시야·동승자 문제까지 범위를 넓혔다.
- **8개 필수 조건을 모두 실증한 후보는 0개다.** 특히 '기존 서비스가 충분히 못 푼다', '실제 반복 이용한다', '이 에뮬레이터에서 권한과 값이 실제 들어온다'는 아직 검증 전이다. 아이디어 수를 맞추려고 모두 구현 가능·차별화 완료라고 판정하지 않는다.
- 신규 후보의 우선 검증 순위는 **N1 정차 큐, N2 휴식 약속, N3 새 차 적응 카드**다. 구현 난도가 낮거나 중간인 축소 MVP만 비교했다. 창의성은 모두 제한적이다.
- **기존안까지 포함한 최종 추천 1개는 '증상 순간 기록 카드'의 읽기 전용 초소형 MVP 유지**다. 새 후보가 기존안보다 나은 문제 해결력을 입증하지 못했기 때문이지, 기존안을 보호하기 위해서가 아니다. 경고등·고장 진단·녹음 분석은 제외한다. 새로운 아이디어만 선택해야 한다면 N1이 우선 실험 대상이나 사업·신규성 검증을 통과한 확정안은 아니다.
- NextDrive 전체 가족 인계 기획은 여러 사람의 참여·공유 동기화와 수요 검증 부담이 있어 우선순위가 낮다. 기존 코드·사양을 변경하거나 폐기하는 조치는 하지 않는다.

## 0. 공식 일정·개발 환경 재확인

- 공식 홈페이지: 1차 접수 **2026-11-15**, 서류 결과 **11-23**, 최종 PT **12-03~04**, 최종 선정 **12-18**, 전시 **2027-02**. 모든 일정 KST, 변경 가능 [F01].
- 2026-11-07 기준 접수일까지 달력상 8일, 최초 PT일까지 26일이다. 초보 개발자의 학습·환경 구축·발표 준비도 이 안에 포함해야 한다.
- 1차는 접수양식 자체가 기획서이며 개발 산출물은 선택 가산점. **2차는 실제 동작 앱 시연 필수**, 에뮬레이터 화면 또는 기기 시연 인정. 목업 영상만으로 2차 요구를 충족한다고 해석하지 않는다 [F02,F03].
- Android Studio, Java/Kotlin, Android API 28 이상, Gradle 8.0 이상, **Pleos Connect Emulator 2.1.0**가 공식 개발 문서의 환경이다 [F04,F05]. SDK 각각의 버전은 Emulator 버전과 다르다. 예: NaviHelper 문서 3.0.5, STT 문서 3.3.1 [T05,T06].
- Cold boot 필수: 공식 문서는 Quick boot일 때 VHAL을 사용할 수 없다고 안내한다. 마이크 입력 설정도 별도다. AVD Bluetooth dongle 미지원. GPU 최소·권장 사양을 확인해야 하므로 이 PC에서 원활히 실행된다고 단정하지 않는다 [F06].
- 최신 문서에 LLM SDK 지원 종료가 명시됐다. 앱 핵심에 공개되지 않은 차량 내 LLM을 가정하지 않는다 [F07]. 이번 후보들은 유료 AI·외부 API 없이 핵심을 구성한다.
- 공식 차량특화성·창의성·기술활용성·사업성은 **1차 공개 기준**이다. 세부 배점 비공개, 2차 상세 기준은 공식 페이지에 추후 안내로 되어 있다. 아래 자체 점수를 공식 최종 PT 배점으로 부르지 않는다 [F08].
- 비공개 저장소 제출은 심사용 계정 접근 확인이 별도 필요하다 [F02]. 이번 작업은 리서치 공유일 뿐, 심사용 Collaborator 초대·APK/README 수정·공모전 접수는 하지 않는다.

## A. 실제로 확인한 사용자 불편

| 근거 | 실제 문제와 조사 | 해석의 한계 | 관련 후보 |
|---|---|---|---|
| P1 | EU ERSO 2023: 유럽 운전자 평균 23%가 최근 30일 눈을 뜨기 어려울 정도로 졸린 상태에서 운전했다고 응답. 국내 2015 휴게소 9곳 400명 조사에서 최근 일주일 졸음운전 경험 39.5% [P01,P02] | 자기보고. 국내 표본은 자가용 100명, 나머지 버스·화물로 일반운전자 대표성 제한. 시간만으로 피로를 측정할 수 없음 | N2 |
| P2 | 운전 중 할 일을 기억하는 부담: 24명 시뮬레이터 연구에서 주유소 같은 사건 기반 심부름 과제가 앞차 속도변화 대응에 더 큰 방해 [P03] | 작은 실험·2017 논문. 망각의 전국 발생률·정차 큐 구매의향·앱의 개선 효과를 입증하지 않음 | N1,N7 |
| P3 | J.D. Power 2025 US IQS 92,694명: 공조·글러브박스 등 기능의 화면 편입과 여러 탭·스와이프의 복잡성을 설명 [P04] | 42.6 PP100은 전체 인포테인먼트 문제로 공조 불편률이 아님. 원문 fetch 403으로 공식 검색 발췌 확인 | N3 |
| P4 | Autoglym 의뢰 영국 차주 1,000명 조사(RAC 소개, 2024): 거의 절반이 눈·얼음을 완전히 제거하기 전에 출발한 경험 [P05] | 업체 의뢰·과거 경험 자기보고. 내부 김서림 경험률로 바꿀 수 없음. 한국 반복빈도 미확인 | N4 |
| P5 | 2020 학술 연구: 5개국 4,479명 온라인 설문에서 최근 5년 차 동승 중 멀미 46%, 어린 시절 포함 59% [P06] | 매번 멀미한다는 뜻 아님. 운전자가 아닌 동승자 중심, 앱 예방·치료 효과 미확인 | N5 |
| P6 | TRAC/Probe 2025 캐나다 운전자 1,000명: 매월 공기압 확인 28%, 더 드물거나 전혀 안 함 68%, 냉간 측정 원칙을 모름 64% [P07] | 업계 의뢰 온라인 자기보고. 68%가 위험 공기압이라는 뜻 아님. 월간 문제로 매일 사용 수요는 약함 | N6 |
| P7 | Uber 2026 분실물 지수: 휴대폰 분실신고 100만 건 초과, 지갑·짐·열쇠 등도 주요 품목 [P08] | 승차공유 승객 신고자료. 자기 차 운전자의 망각률·신고자 수·분모·현재 수납상태를 알 수 없음 | N7 |
| P8 | TRL 2025 영국 면허소지자 1,850명 조사: 절반 이상이 눈부심 때문에 야간운전을 줄이거나 중단했거나 가능하면 그러고 싶다고 응답 [P09] | 세 선택을 합친 비율, 모두 실제 중단자가 아님. 읽기 앱으로 상대차 조명을 줄일 수 없음 | N8 |
| P9 | AAA/Utah 2017: 120명·차량30종에서 목적지 설정 등 IVIS 작업의 시각·인지 부담. 작은 차내 웰빙 연구도 존재 [P10,P11] | 과거 시스템·표본 한계. '편의 기능 추가가 안전해진다'는 근거가 아님. 소형 웰빙 연구를 일반 효과로 확장 금지 | N1,N10,N11 |

**문제와 앱 수요를 분리한다.** P1이 강하다고 휴식 타이머의 신규성이 생기지 않고, P5가 크다고 공개 데이터로 멀미를 예측할 수 있는 것도 아니다. 국내 일반운전자의 반복빈도·지불의향·차량앱 선호는 이번 조사에서 새로 측정하지 않았다. N9는 환기의 필요성보다 OEM 선행 기능이 강하게 확인된 반증 후보이며, CO2 노출률을 별도로 확보한 것처럼 쓰지 않는다.

## B. 신규 아이디어 11개

아래 API 묶음 T0~T8은 D절의 정확한 메서드·권한·문서 URL을 가리킨다. 모든 앱은 로컬 저장을 기본으로 하며 모바일 앱·서버는 핵심 MVP에 필요하지 않다. 다만 차량 전원 종료 후 감시·자동 기동·다른 앱 위 항상 표시·원격 전송은 공통으로 보장하지 않는다.

### N1. 정차 큐: 지금 기억만 남기고 주차 후 처리

1. **문제·근거:** 운전 중 할 일을 기억하거나 바로 처리하려는 부담(P2,P9). 실제 운전자의 망각빈도는 미확인.
2. **사용 상황:** 이동 중 '주차하면 서류 챙기기'를 남기고 목적지에서 처리.
3. **한 문장:** 지금 할 수 없는 일을 짧게 보관하고 주차 후 한 장의 처리 목록으로 돌려준다.
4. **핵심 하나:** 주행 중 보류한 자기 할 일의 주차 후 처리.
5. **차량 전용 이유:** 차량의 주행 제한과 주차 맥락으로 표시 시점을 제한할 수 있음. 단순 메모만이면 스마트폰보다 낫지 않음.
6. **중복·차이:** Gleo/Gemini 음성메모·OtoZen 위치알림·Android의 parked-resume UX가 선행. 차이는 위치 접근이 아니라 '지금 처리하지 않기→주차 후 완료' 흐름이라는 가설. OtoZen에 이 기능이 없다고 전수 확인한 것은 아님.
7. **SDK·권한:** T0 로컬저장, T1 주행제한, T2 GEAR_SELECTION 선택, T5 ON_DEVICE STT. 음성 권한 선언은 확인했으나 protection level·이 환경의 주행 실행은 **미검증**.
8. **난이도·병목:** 중간. 주차 중 입력·저장·처리목록은 단순, 운전 중 음성 호출과 앱 생존이 핵심 병목. 두 기능이 막히면 본래 가치가 약해짐.
9. **사용자 친화성:** 시작·종료 운행일지 불필요. 한 발화, 주차 후 체크. 주행 중 내용을 길게 읽어주거나 확인 터치를 요구하지 않음.
10. **공모전 경쟁력:** 일반운전자 반복상황·차량 맥락은 합리적. 창의성·기존 메모 대비 개선은 약하게 검증돼 조건부.
11. **실차 검증:** 실제 주행 음성 서비스 승인·정차 신호·프로세스 종료 후 복구. 다른 앱의 알림을 수집하는 기능은 없음.
12. **PT 시연:** 앱 실행→메모 2개 저장→Console D에서 내용 숨김→P와 UX 허용 확인 후 목록→완료→앱 재실행 후 남은 항목 유지. 음성이 막히면 '수동 입력/fixture'라 명시하며 주행 음성 성공이라고 주장하지 않음.

### N2. 휴식 약속: 졸음 감지 대신 쉬기로 한 계획 이행

1. **문제·근거:** 피곤한 채 운전(P1). '휴식을 계획했지만 미룸'의 별도 비율은 미확인.
2. **사용 상황:** 출발 전 본인이 정한 휴식 간격을 저장하고 긴 이동에서 실제 휴식 여부 확인.
3. **한 문장:** 운전자가 정한 쉬는 약속을 기억하고 주차 후 이행 여부를 남긴다.
4. **핵심 하나:** 개인 휴식계획과 실제 휴식의 연결.
5. **차량 전용 이유:** 기어/주행 맥락으로 교통신호 대기와 주차를 구분하는 것이 휴대폰 타이머 대비 가설적 이점.
6. **중복·차이:** OEM DAW·EROAD Drive Buddy Fatigue는 이미 휴식권고·타이머·미준수 기록까지 제공. 소비자용 독립·로컬·비감시 UX만 남은 차이 가설이며 타이머 최초가 아님.
7. **SDK·권한:** T0 elapsedRealtime·Room, T1 UX 제한, T2 normal CAR_POWERTRAIN의 GEAR_SELECTION 읽기. 실제 이동시간이 필요하면 T2의 차속은 dangerous CAR_SPEED로 별도 확인.
8. **난이도·병목:** 낮음~중간. P 아닌 시간을 곧 주행시간으로 부르면 안 됨. MVP는 관측 세션시간/주차시간으로 표기하고 이동시간은 차속 검증 후만 추가.
9. **사용자 친화성:** 간격은 한 번 설정, 주차 후 '쉬었음/아직' 한 번. 피로 점수·강제 설문·계속 운전하라는 안내 없음.
10. **공모전 경쟁력:** 문제 근거는 강하나 독창성·상용 중복이 큼. 저비용 소비자 UX의 실제 이점부터 검증.
11. **실차 검증:** P 수신, 앱 비활성·절전·재부팅의 미관측 구간 처리. 프로세스 중단시간을 운전한 시간으로 추정하지 않음. 법정 운행기록장치 대체 아님.
12. **PT 시연:** 명시된 데모 배속으로 짧은 세션→P 전환→사용자가 휴식 선택→중간에 D 전환하면 완료 처리 취소→충분한 관측 주차시간 후 기록. 항상 알림·전원 종료 후 알림은 약속하지 않음.

### N3. 새 차 적응 카드: 설명 듣기보다 직접 찾아보기

1. **문제·근거:** 화면 속 기본 기능 탐색 부담(P3). 낯선 차량의 학습·기억 문제에 대한 과거 연구도 있음 [P12].
2. **사용 상황:** 렌터카·공용차·새 차의 첫 출발 전.
3. **한 문장:** 이전 차와 다른 일상 조작 세 가지만 직접 찾아보고 출발한다.
4. **핵심 하나:** 정차 중 실제 조작 위치 찾기 완료.
5. **차량 전용 이유:** 실제 대시보드 앞에서 연습하고 지원되는 공조·와이퍼 신호를 즉시 확인할 수 있음. 차종별 콘텐츠는 자동 생성하지 않음.
6. **중복·차이:** Gleo의 매뉴얼·명령 수행, Hertz 첫 운전 가이드가 선행. '이전 차 대비 차이만/실제로 찾았는지'가 UX 가설. 차량 기능 설명 자체는 신규가 아님.
7. **SDK·권한:** T0,T1. 선택 T3 HVAC, T4 Wiper getter. 기본은 승인받아 직접 작성한 한 차종의 공조·앞유리 제습·와이퍼 안내와 수동 확인. 차량 정보를 알아서 식별하는 API는 이번 MVP에서 가정하지 않음.
8. **난이도·병목:** 낮음. 핵심 위험은 정확한 연식·트림별 콘텐츠와 배포 유지비. 1차종 시연 가능성이 전 차종 자동지원 뜻은 아님.
9. **사용자 친화성:** 첫 사용 1회 세 카드, 이후 생략. 매 운행 강제 반복 금지. 앱이 변속·제동·조향을 제어하거나 연습시키지 않음.
10. **공모전 경쟁력:** 실물 맥락과 쉬운 시연은 장점. 일반운전자의 매일 반복문제는 아니며 Gleo 대비 효용·사업성 미검증.
11. **실차 검증:** 사용자 수동 조작과 관측값의 일치, 콘텐츠 오안내, 제한 상태 전환 시 즉시 중지.
12. **PT 시연:** 한 차종의 기능 위치 찾기→Console 팬/와이퍼 상태가 바뀌면 실제 수신값 표시→수동 찾기 완료→D에서 안내 잠금. 수신하지 못하면 자동 판정 아닌 수동 확인임을 표시.

### N4. 출발 전 시야 준비 카드

1. **문제·근거:** 눈·얼음을 덜 제거한 채 출발(P4).
2. **사용 상황:** 겨울철 안전하게 주차한 상태에서 외부 눈·얼음 제거 준비.
3. **한 문장:** 시야 준비 순서를 짧게 안내하고 사용자가 완료를 확인한다.
4. **핵심 하나:** 출발 전 준비 순서와 완료 체크.
5. **차량 전용 이유:** 대시보드에서 준비 상태 확인. 그러나 차 밖 작업에는 휴대폰이 더 나을 수 있음.
6. **중복·차이:** Auto Defog·원격공조가 내부 습기 등 이미 처리. 외부 제거 확인은 남지만 센서로 검증 못함.
7. **SDK·권한:** T0,T1, 선택 T3 defroster 상태. 결빙·습도·외부온도 실측은 이번 확인된 Pleos 경로에서 확보 못함.
8. **난이도·병목:** 체크리스트는 낮음, 자동 결빙탐지·시야 완료 판정은 **미지원/미검증으로 제외**.
9. **사용자 친화성:** 짧은 순서, 준비 완료는 운전자가 판단. 공회전이나 위험한 유리 제빙 조작 유도 금지.
10. **공모전 경쟁력:** 계절성은 있지만 기존 기능 대비 직접 해결력·반복성이 약함. 주력 보류.
11. **실차 검증:** 안내 안전성, 제습 상태와 실제 외부 시야를 혼동하지 않는지.
12. **PT 시연:** 정차 체크카드→defroster 관측 표시→운전자 수동 확인. '시야 안전'·'출발 가능' 자동 판정 화면은 만들지 않음.

### N5. 동승자 멀미 준비 카드

1. **문제·근거:** 동승 중 멀미(P5).
2. **사용 상황:** 탑승 전 화면 읽기·좌석·환기 등에 대해 취약한 활동을 미리 피하고 싶은 동승자.
3. **한 문장:** 지난번 불편했던 활동을 기억해 다음 탑승 전 준비만 돕는다.
4. **핵심 하나:** 개인별 취약활동 회고를 다음 탑승 준비에 연결.
5. **차량 전용 이유:** 현재 공조·창문 상태를 함께 확인할 수 있으나 스마트폰 메모 대비 필수 이점은 약함.
6. **중복·차이:** KineStop 가상수평선, 멀미안경, 기존 행동 가이드가 있음. 실시간 예고·수평선 재생을 신규로 포장하지 않음.
7. **SDK·권한:** T0,T1, 선택 T3 recirculation·T4 window. 급회전·가속 예측이나 의학 센서는 없음.
8. **난이도·병목:** 준비·회고는 낮음, 실시간 멀미 예측/치료는 높은 불확실성으로 제외. 실제 효과 확인이 병목.
9. **사용자 친화성:** 첫 설정·이동 후 한 항목 회고만. 주행 중 승객이라도 운전자 디스플레이에 긴 입력 허용한다고 가정하지 않음.
10. **공모전 경쟁력:** 문제는 있지만 대상이 동승자이고 의료효과·차량 필요성 약함. 보류.
11. **실차 검증:** 읽기 신호와 지원 영역, 개인기록 프라이버시. 공기청정 on을 깨끗한 공기라고 표시하지 않음.
12. **PT 시연:** 정차 중 전회 불편활동 확인→현재 창문·공조 상태 출처 표시→준비 선택. 치료효과·주행 예측 성공을 시연하지 않음.

### N6. 냉간 타이어 점검 루틴

1. **문제·근거:** 점검 빈도·냉간 측정 이해 부족(P6).
2. **사용 상황:** 월간 수동 공기압 점검을 준비할 때.
3. **한 문장:** 문기둥 권장압과 수동 냉간 측정 기록을 다음 점검에 남긴다.
4. **핵심 하나:** 정확한 조건의 수동 점검 이행.
5. **차량 전용 이유:** 해당 차량의 권장값을 보관할 수 있으나 타이어 옆에서 쓰는 휴대폰이 더 편할 수 있음.
6. **중복·차이:** TPMS·계기판 표시, 정비 알림 앱이 선행. 냉간 점검 교육·기록도 최초가 아님.
7. **SDK·권한:** T0,T1, 선택 기존 Odometer CAR_DRIVE normal. **Tire.getLowTirePressure는 현재압이 아닌 임계치**라 사용하지 않음. 실제 측정은 수동 게이지.
8. **난이도·병목:** 낮음. 앱이 꺼진 시간을 냉간 주차시간으로 추정하면 안 됨. 냉간 여부·문기둥 사양은 사용자 확인.
9. **사용자 친화성:** 한 달에 한 번도 네 바퀴 입력은 부담. 타이어 규격·적재조건이 바뀌면 설정 정확성 문제.
10. **공모전 경쟁력:** 차량 특화 SDK 핵심성이 낮아 주력 제외. 현재압 자동수집을 넣으면 공개 경로가 미검증.
11. **실차 검증:** 권장압·단위·수동 측정 출처. 타이어 안전 보증·주행 가능 판정 없음.
12. **PT 시연:** 수동 입력값·냉간 확인 저장→이전 기록 조회. 네 바퀴 실시간 센서값처럼 꾸미지 않음.

### N7. 하차 준비 한 장

1. **문제·근거:** 차내 분실물(P7). 자가용 운전자 직접 근거는 약함.
2. **사용 상황:** 목적지 주차 후 휴대폰·서류·우산 등을 챙길 때.
3. **한 문장:** 주차 후 내가 지정한 소지품과 관측된 창문 상태를 한 번 확인한다.
4. **핵심 하나:** 하차 준비 확인.
5. **차량 전용 이유:** 차량 화면·창문 값과 연결되지만 단순 소지품 알림은 휴대폰으로도 가능.
6. **중복·차이:** OtoZen 준비물·장소 이탈 알림, OEM ROA·Kia App 창문 기능이 선행. 기능 묶음만으로 신규성 주장 금지.
7. **SDK·권한:** T0,T1,T2 선택,T4 window normal. 물체·아동·동물 감지는 없음.
8. **난이도·병목:** 낮음~중간. 시동 종료 뒤 알림·자동 팝업·도어 시점 전달은 미검증으로 제외.
9. **사용자 친화성:** 개인 목록 한 번, 주차 후 선택 확인. 매번 불필요 알림이면 해제 가능.
10. **공모전 경쟁력:** 간단하지만 직접 근거·차별성 약해 보류. 아동 방치 안전앱으로 승격하지 않음.
11. **실차 검증:** WindowArea 지원·stale값 표시. 값이 null이면 닫혔다고 보이지 않음.
12. **PT 시연:** Console 창문25%→실제 수신 시 확인 카드→앱 외부 수동 닫음→새 값. 안전한 하차 자동 보증 없음.

### N8. 야간 눈부심 준비 도우미

1. **문제·근거:** 야간 눈부심(P8).
2. **사용 상황:** 야간 출발 전 유리·미러·조명 상태 확인과 불편 회고.
3. **한 문장:** 눈부심을 고치는 것이 아니라 출발 전 확인과 이후 불편 기록을 돕는다.
4. **핵심 하나:** 야간 준비·불편 회고.
5. **차량 전용 이유:** 하향등 상태 연결 가능하지만 읽기 신호가 상대차 눈부심 해결로 이어지지 않음.
6. **중복·차이:** ECM 미러·자동등·안전 안내가 선행. Škoda Traffication 같은 위험정보 서비스도 존재.
7. **SDK·권한:** T0,T1,T4 light normal. 눈부심 센서·실시간 위험지도·교통 서버는 없음.
8. **난이도·병목:** 기록은 낮음, 본질적 눈부심 경감은 앱으로 불가. 핵심 해결력 기준 낮은 구현가능성으로 탈락.
9. **사용자 친화성:** 주행 중 화면을 보게 하면 오히려 나쁨. 신고·평가를 주행 중 요구하지 않음.
10. **공모전 경쟁력:** 문제 크기만으로 추천 불가. 상위3 제외.
11. **실차 검증:** 조명 on/off는 실제 노면 시야·광도·눈부심 측정이 아님.
12. **PT 시연:** 조명상태 카드·정차 후 회고까지만. 눈부심 해결 시연 불가라고 명시.

### N9. 내기순환 환기 타이밍

1. **문제·근거:** 환기 필요 상황은 있으나 이번에 CO2 노출 빈도를 새로 검증하지 못함. OEM 자동환기가 오히려 강한 반증.
2. **사용 상황:** 내기순환을 오래 쓰는 통근.
3. **한 문장:** 관측된 내기순환 지속시간을 알려준다.
4. **핵심 하나:** 순환 상태 기반 타이머.
5. **차량 전용 이유:** 스마트폰이 모르는 공조 상태를 읽을 수 있음.
6. **중복·차이:** 한국2026 쏘나타·EV6 저온공조 내기30분 후 외기 전환, 온습도 지능형 환기, 일부 사양 CO2 자동환기까지 이미 존재 [C19~C21]. 전차종 동일하진 않으나 신규성은 크게 약화.
7. **SDK·권한:** T0,T3 recirculation CAR_CLIMATE normal. CO2·PM2.5·습도 getter는 확인한 HVAC 공개목록에서 미확인.
8. **난이도·병목:** 타이머 낮음, 건강·공기질 판단 불가. 타이머로 CO2를 추정·수치화하지 않음.
9. **사용자 친화성:** 원치 않는 반복 알림 위험. 터널·오염 상황에서 무조건 창문 열라고 하지 않음.
10. **공모전 경쟁력:** 기술 경로는 명확하지만 기존 자동제어보다 약한 중복. 탈락.
11. **실차 검증:** 좌석 영역·순환상태 수신·관측 공백 처리.
12. **PT 시연:** Console Fresh/Recirculate 전환→실제 값과 관측시간 표시. 공기질 실측·안전권고로 보이지 않게 함.

### N10. 도착 전에 끝나는 짧은 오디오

1. **문제·근거:** 주행 중 콘텐츠 탐색 부담(P9), 끝내지 못하는 콘텐츠 불만의 정량 근거는 미확보.
2. **사용 상황:** 남은 이동시간에 맞는 짧은 콘텐츠를 듣고 싶을 때.
3. **한 문장:** 남은 시간에 맞춰 직접 제작한 짧은 오디오를 고른다.
4. **핵심 하나:** 이동시간과 콘텐츠 분량의 적합성.
5. **차량 전용 이유:** 현재 내비 상태·차량 오디오와 결합. 휴대폰 팟캐스트도 강한 대안.
6. **중복·차이:** Pocket Casts AAOS 실배포, TaleSync의 목적지 도착 시 이야기 완결 프로토타입이 직접 선행. AI 오디오 요약만 붙이지 않음.
7. **SDK·권한:** T0, T1, T6 NaviHelper NAVI_ROUTE, Android 로컬 미디어. 정확 ETA getter·필드·보호수준·에뮬레이터 응답은 이번에 완결 검증 못함.
8. **난이도·병목:** 고정 5분 오디오 재생은 낮음, 실제 ETA 자동맞춤은 내비 서비스 의존·권한 미검증으로 낮은 실현가능성. 저작권도 필요.
9. **사용자 친화성:** 출발 전 한 번 선택, 주행 중 검색 없음. 잘못된 타이밍으로 중요 길안내를 가리면 안 됨.
10. **공모전 경쟁력:** 직접 선행·핵심 API 불확실성 때문에 상위3 제외.
11. **실차 검증:** 기본 내비 앱·NaviService 이벤트와 음성 우선순위. 콜백 존재만으로 ETA 데이터가 온다고 단정하지 않음.
12. **PT 시연:** 본인 제작 오디오+수동시간/fixture라 표시. 실제 내비 연동 성공·동적 ETA 대응이라고 부르지 않음.

### N11. 주차 후 1분 리셋

1. **문제·근거:** 운전·통근 스트레스와 차내 웰빙 연구(P9), 개인별 효과 미검증.
2. **사용 상황:** 업무·귀가 전 안전한 주차장에서 짧게 쉬고 싶은 때.
3. **한 문장:** 주차 상태에서만 짧은 호흡·정리 시간을 제공한다.
4. **핵심 하나:** 짧은 주차 후 전환 루틴.
5. **차량 전용 이유:** 차량의 독립 공간·스피커. 스마트폰으로도 충분한 반례가 있음.
6. **중복·차이:** Polestar Breathe는 이미 차량용 다운로드 앱으로 호흡 애니메이션·사운드 제공 [C22]. 최초 명상앱 아님.
7. **SDK·권한:** T0,T1,T2 선택, Android 로컬 오디오. 생체신호 측정·시트/조명/공조 제어 없음.
8. **난이도·병목:** 낮음. 다만 앱이 '치료·스트레스 측정'을 한다면 검증 불가. 운전 중 루틴은 제외.
9. **사용자 친화성:** 한 번 눌러 시작·즉시 취소, 운전자에게 억지 호흡·눈감기를 강요하지 않음.
10. **공모전 경쟁력:** 구현은 쉬워도 강한 상용 중복·폰 대비 약한 우위로 탈락.
11. **실차 검증:** 주행 전환 즉시 화면·훈련 중단, 오디오 우선순위.
12. **PT 시연:** 주차 루틴→D 또는 UX 제한 수신 시 종료. 이 동작은 건강 효과 입증이 아님.

### 8개 필수 조건별 판정

O=축소 MVP 설계상 충족 가능, ?=근거·실행 검증 부족, X=제안한 직접 해결 범위에서 실패. **O도 실행 성공을 뜻하지 않는다.** 1=문제근거, 2=미해결 차별성, 3=차량우위, 4=공개 API, 5=위험제어 불필요, 6=외부서버/유료API 불필요, 7=기한 내 초보 구현, 8=낮은 입력·주의부담.

| 후보 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 실패·보류의 핵심 |
|---|---|---|---|---|---|---|---|---|---|
| N1 | O | ? | ? | ? | O | O | ? | ? | STT 권한보호·주행 실행·기존 메모 대비 개선 미검증 |
| N2 | O | ? | O | ? | O | O | O | O | EROAD·DAW 중복, 실제 기어수신·생명주기·휴식이행 수요 미검증 |
| N3 | O | ? | O | O | O | O | O | O | Gleo·Hertz 대비 가치와 차종 콘텐츠 유지비, 일상 반복성 약함 |
| N4 | O | ? | ? | O | O | O | O | ? | 시야 완료 자동판정 불가, 단순 체크리스트 우위 미확인 |
| N5 | O | ? | ? | O | O | O | O | ? | 동승자 특화, 폰 대비 우위·효과 미검증 |
| N6 | O | ? | X | O | O | O | O | ? | 수동기록은 가능, 차량우위 약함. 현재압 자동수집 확장은 미검증 |
| N7 | ? | ? | ? | O | O | O | O | ? | 자가용 운전자 직접근거·기존 알림 대비 우위 약함 |
| N8 | O | X | ? | X | O | O | X | ? | 기록 가능과 눈부심 해결은 다름 |
| N9 | ? | X | O | O | O | O | O | ? | 자동환기·30분 전환 선행, CO2 감지 불가 |
| N10 | ? | X | O | ? | O | O | X | O | TaleSync 선행, 실제 ETA 경로·내비환경 미검증 |
| N11 | O | X | ? | O | O | O | O | O | Polestar Breathe 직접 상용 선행 |

## C. 기존 서비스·수상작 중복성 분석

기존 기록의 ABRP·PlugShare·Car Scanner·FIXD·OBDeleven·ALLDATA·Fleetio 조사도 그대로 유효한 선행 참고다. 아래는 이번 조사에서 범위를 넓혀 확인한 내용이다. 일부 미제공 기능을 '없다'고 증명한 표가 아니며, 차종·연식·지역에 따라 적용이 다르다.

| 서비스·구분 | 이미 있는 기능 | 이번 판단 |
|---|---|---|
| C01 myHyundai/Bluelink, 한국 지원차종 양산 | 원격공조·열선통풍·원격창문·후석 확인 등 | 단순 공조/창문 상태·후석 알림 새 기능 아님 |
| C02 Kia App, 한국 지원차종 | 공조·창문·컨시어지·경고등·ETA/차위치 공유 | 상태조회·도착 알림을 신규로 포장하지 않음 |
| C03 Gleo AI, 차종·지역별 순차양산 | 자연어 공조·내비·매뉴얼·복수명령 | 기능 설명·음성조작 자체 중복 |
| C04 순정 제습·환기, 장착사양 | 습기 감지→A/C·외기·송풍, 공조OFF 자동환기 | 공조 조작 길잡이·환기 타이머 차별성 약함 |
| C05 DAW/주의운전알림 | 운전패턴 주의·휴식권고, 사양별 연락 | N2는 졸음감지 앱이 아님 |
| C06 한국 ccNC 기본내비 | 위험·카메라·제한속도·하이패스/EV통행료 등 | 경고·요금·상태정보 재표시만으로 가치 부족 |
| C07 EasyPark AAOS 실배포 | 지원 Polestar/Volvo/Renault 등 차내 주차결제·자동종료 | 주차결제 앱은 사업자·정산 연동 없이 대체 불가 |
| C08 Pocket Casts AAOS 실배포 | 휴대폰 없이 팟캐스트. AAOS/Auto 챕터는 미지원 안내 | 단순 차내 오디오가 신규 아님 |
| C09 Volvo Air Quality, 시장·차종별 양산 | PM2.5·외부 공기질/꽃가루·정화 표시 | Pleos 청정기 on/off를 같은 센서 기능이라 주장 금지 |
| C10 EROAD Drive Buddy Fatigue, 호주 상용 | 경량 업무차 포함 운전/휴식 규칙·잔여시간·미준수 기록 | N2 핵심구조도 선행. 소비자 로컬 UX는 시장 세분화 가설 |
| C11 KineStop, Android 휴대폰 배포 | 다른 화면 위 가상수평선·운동단서 | AAOS 앱이라 부르지 않음. 개발사 효능을 독립 입증처럼 쓰지 않음 |
| C12 Tesla Pet/Dog·아동남음 관련 양산사양 | 공조유지·온도/앱 확인·사양별 감지경보 | 생명안전 감시를 우리 읽기앱으로 흉내내지 않음 |
| C13 Škoda Traffication 상용 | 도로위험·역주행·악천후 경고 | 오프라인 앱에 같은 실시간 네트워크 정보가 있다고 가정 금지 |
| C14 Hertz Tesla 교육 웹 | 첫운전·회생제동·공조·충전 안내 | N3는 콘텐츠 설명만이면 중복 |
| C15 Google built-in/Gemini, 차종·지역·언어별 | 음성 할일·리마인더·ETA 메시지·매뉴얼 | N1 신규성 압박. 특정 Pleos 차량 탑재와 동일시 금지 |
| C16 TMAP Auto 지원순정내비 | 구간 평균속도·잔여시간/거리 안내 | 구간단속 알림 앱 별도 발굴하지 않음 |
| C17 TaleSync, 2026 해커톤 시연 프로토타입 | 주행데이터·아동반응·도착 시 이야기 완결 | N10 직접 선행. 출시앱 아님 |
| C18 창밖동화 등 Pleos 2026 공모전 아이디어 | 경로/지역동화, 로컬미션, ETA 웨이팅, 픽업추천, 위치음성광고 | 아이디어·출시 검토 단계와 상용앱 구분 |
| C19 한국2026 쏘나타 DN8 | 저온 공조 내기30분→외기 자동전환 | N9 타이머 선행 |
| C20 한국2026 EV6 | 같은 전환+지능형 자동환기+터널 연동 | N9를 OEM보다 나은 제어로 홍보하지 않음 |
| C21 일부 현대 IVI 사양 | CO2 증가 자동환기 설명 | 모든 현대기아 차에 있다고 일반화하지 않음 |
| C22 Polestar Breathe 상용 | 차량화면 호흡 애니메이션·사운드 | N11 직접 선행 |
| C23 OtoZen 휴대폰 앱 | 도착/접근/이탈 준비물·업무·캘린더 음성알림 | N1,N7은 준비물 알림 자체를 차별점으로 못 씀 |
| C24 현대 ccNC 연결복구 공식 매뉴얼 | 폰/차량 양쪽 삭제·재등록·증상별 표 | 오프라인 복구 매뉴얼 후보는 별도 신작으로 선정하지 않음 |

Android 공식 가이드의 '주행 중 작업 보류→주차 후 이어하기'도 N1의 UX 선행이다. 설계 패턴을 결합했다고 최초 아이디어라고 주장하지 않는다 [T01b].

## D. 공개 SDK/API 구현 검증

### 등급과 공통 원칙

- **문서 확인:** 이름·반환 의미·권한 또는 공식 샘플을 확인했다.
- **실행 미검증:** APK·권한 실제부여·콜백·Console→앱 수신을 시험하지 않았다. 아래 전부 여기에 해당한다.
- **미확인:** getter/권한보호/배포조건 등의 근거가 부족하다. 구현 가능이라고 단정하지 않는다.
- **제외:** 특권권한·핵심 미지원·서버/전문 센서 필요·직접 문제해결력 부족.

Pleos 권한(pleos.car.permission.*)과 AAOS 권한(android.car.permission.*)은 별개다. normal이라고 모든 차종·영역에서 값이 보장되지는 않는다. Capability/accessType/영역, SecurityException, null, API별 오류를 처리한다. 에뮬레이터 조작 카드 존재와 일반 앱 읽기 권한, 실제 값 수신, 실차 장착은 네 가지 별도 확인이다.

### T0. 로컬 저장·시간: 모든 후보

- Android Context.getFilesDir(), Room·Preferences: 앱 내부 저장에 별도 저장소 권한 불필요 [T00a]. 외부 공유·백업은 MVP 필수에서 제외.
- SystemClock.elapsedRealtime(): 부팅 후 단조시간, 구간 경과 계산. 재부팅 전후에 그대로 뺄 수 없으며 앱이 실행 안 된 구간의 차량 상태는 알 수 없다 [T00b].
- 앱 화면/프로세스 관측 세션만 1차 범위. 정확 알람·상시 foreground service·부팅 자동실행·POST_NOTIFICATIONS 승인·앱스토어 배포 조건은 필수 의존성에서 제외한다. 원한다면 별도 검증할 것.
- JVM 단위시험 가능한 상태기계와 UI clock fixture를 분리. fixture를 실제 차량 데이터처럼 표시하지 않는다.

### T1. 주행 UX: 모든 후보

- CarUxRestrictionsManager.getCurrentCarUxRestrictions(), registerListener()→onUxRestrictionsChanged(), unregisterListener().
- isRequiresDistractionOptimization() 및 activeRestrictions에 따라 키보드·긴목록·비디오·설정 등 잠금 [T01a]. 해당 공개 메서드 설명에는 별도 권한 요구가 표기되지 않았고 Pleos가 연동을 명시한다. 환경에서 연결 성공은 미검증.
- **제한 해제만으로 P라고 판단하지 않는다.** 지역·양산정책에 따라 다름 [T01c]. 신호대기에서 긴 할일목록을 열면 안 된다.
- DO 미선언 Activity는 주차에서 사용 가능, 주행에서 불가라는 Pleos 기본정책. DO 선언이 모든 기능 허가가 아님.
- 복잡한 확인·체크·훈련은 주차 UI에서만. 상태를 잃거나 알 수 없으면 잠금 유지(fail closed). 소비 앱이 제한 설정 자체를 바꾸지 않는다.

### T2. 선택한 기어·차속: N1/N2/N3/N7/N11 선택 연동

- AAOS CarPropertyManager.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, areaId), getCarPropertyConfig()로 속성지원 확인. global 영역은 공식 속성 사양을 따라야 한다.
- Android 공식 GEAR_SELECTION 읽기는 Car.PERMISSION_POWERTRAIN = android.car.permission.CAR_POWERTRAIN, **normal**, 쓰기 불가 [T02a,T02b]. 실제 변속기 단수 CURRENT_GEAR와 혼동하지 않는다.
- 최신 Android 공식·AOSP 권한과 Pleos 2.1.0 내장 라이브러리·배포정책이 일치하는지 **미검증**. Console P/R/N/D가 해당 Android 속성으로 일반앱에 실제 전달되는지 초기 게이트다.
- getIntProperty는 blocking 가능, 메인 스레드 호출 금지. 예외·미지원 처리 [T02c]. P와 UX허용을 함께 확인해 주차용 상세 UI를 연다. 앱이 변속하는 기능 없음.
- 실제 속도는 PERF_VEHICLE_SPEED 및 CAR_SPEED dangerous 경로가 별도다. 크루즈 목표속도는 실제속도가 아니고 P가 아닌 시간을 실제 운전시간으로 쓰면 안 된다.
- CarDrivingStateManager의 오래된 CAR_DRIVING_STATE는 privileged 경로이며 최신 CAR_DRIVING_STATE_3P 존재만으로 해당 Pleos 환경 지원을 확정하지 않는다 [T02b,T02d].
- 에뮬레이터: Console 기어전환 시험 대상. 미수신 시 manual/fixture 주차상태를 별도 배지로 시연할 수 있으나 '자동 주차 감지 완료'가 아니며 안전 기능 대체·실차 허용 근거도 아님.

### T3. 공조 읽기: N3/N4/N5/N9

- HVAC.getHvacRecirculationState(SeatArea, callback Boolean?), registerHvacRecirculationState(), checkHvacRecirculationCapabilities(): CAR_CLIMATE **normal 개별문서 확인**.
- HVAC.getHvacFanSpeed()/registerHvacFanSpeed()/checkHvacFanSpeedCapabilities(), getHvacFanDirection()/checkHvacFanDirectionCapabilities(), getHvacDefrosterState() 등 공개목록 확인. 개별 API 권한·영역은 구현 전 각 페이지 재확인.
- getHvacTemperature()는 목표 설정값, getHvacTemperatureCurrent()는 현재 실내온도. CAR_CLIMATE normal 문서 확인. Console 목표온도16~30°C 카드로 실내 실제온도 수신을 입증할 수 없음.
- getHvacAirCleanerState()는 청정기 on/off. 확인한 공개 HVAC 목록에 CO2·PM2.5·VOC·AQI·습도·외부온도 getter는 미확인. 다른 AAOS 경로까지 전혀 없다고 전수 부정하는 뜻은 아님.
- Console: Fresh/Recirculate, 풍량0~8, 풍향, 목표온도, HVAC/AC/Auto/Dual/Defroster/AirCleaner 지원 명시. 앱 실제수신은 미검증. 공조 쓰기·자동환기 제어는 모든 MVP에서 제외 [T03,T08].

### T4. 창문·와이퍼·조명 읽기: N3/N5/N7/N8

- Window.getCurrentWindowPosition(WindowArea, callback Int?), registerWindowPosition(), checkWindowPositionCapabilities(): CAR_WINDOWS **normal 개별문서 확인**. 문서상 영역별 범위 Capability가 있음.
- Wiper.getCurrentWiperState(WindowArea, callback WiperState?), registerWiperState(), checkWiperStateCapabilities(): CAR_WINDOWS **normal 개별문서 확인**. 와이퍼 작동≠강우량.
- Light.getCurrentLowBeamLightState(callback LightState?), registerLowBeamLightState(), checkLowBeamLightStateCapabilities(): CAR_LIGHTS **normal 개별문서 확인**. 조명 on/off≠광도·시야·눈부심.
- Console: 창문0~100%, 앞뒤 와이퍼 Off/Auto/Low/High, Exterior Lights Mode·High Beam 조작 명시. 각각 Console switch/mode와 getter 상태의 실제 대응은 **앱 수신시험 필요** [T04,T08].
- 모든 기능 읽기 전용. 창문·와이퍼·조명 제어, 전원 종료 뒤 감시 제외. 미지원은 unavailable로 표시, 정상/안전으로 바꾸지 않음.

### T5. 음성: N1 필수 실험, 다른 후보 선택

- SpeechToText.initialize(), addListener(), request(), stop(), release(); ON_DEVICE는 네트워크·서버 앱등록 불필요. **pleos.car.permission.STT_SERVICE 선언 필수** [T05].
- 선언 안내에는 protection level 확정 정보가 없으므로 normal/dangerous라고 임의 표기하지 않는다. 실제 권한부여와 주행 상태 호출은 미검증.
- STT는 텍스트 변환이지 앱 기능 실행이 아님. Gleo App Schema·Action/Callback 등록과 호출 경로를 별도 확인해야 한다. 최대3개 호출 정의라는 이전 문서 확인은 이번 앱의 등록·서비스 승인 성공과 다름.
- Emulator 마이크 설정 필요. 주행 음성 활성화·오디오 우선순위·다른 화면 상태에서 호출은 초기 테스트 게이트다. 부팅 자동 실행·상시 듣기는 약속하지 않음.
- 안전한 대체는 주차 중 수동입력·고정 발화 fixture. 본래 '주행 중 떠오른 일 기록' 성공을 입증하지 못하므로 N1의 핵심 게이트 실패 시 추천을 철회한다.

### T6. 내비: N10 핵심 미검증

- NaviHelper.initialize(), addListener(), getDestinationInfo()→onDestinationInfo(), onDestinationArrived() 등 공식 문서 확인.
- get*Info()/requestTrip()은 NAVI_ROUTE, 경로설정·변경은 NAVI_ROUTE_SEARCH 선언 안내. protection level은 이번 근거에서 확정 못함 [T06].
- **정확 ETA getter/필드·단위·갱신주기·권한보호를 완결 확인하지 못했다.** onTripInfo() 콜백 이름만으로 자동 ETA 앱을 구현 가능 처리하지 않는다.
- 공식 sample은 NaviService와 호환 기본 내비앱 설치·구성을 요구하며, 실제 이벤트는 앱 지원과 현재 상태에 달린다. 내비 엔진·경로데이터가 오프라인 무료로 제공된다고 보장하지 않는다.
- 수동 남은시간 fixture는 콘텐츠 선택 로직만 시연. 실제 내비 연동 시연이 아님.

### T7. 기존 두 기획의 공개 경로·제한 재적용

- Fuel.getFuelPercent()/registerFuelPercent()/checkFuelPercentCapabilities(): CAR_ENERGY normal 문서 확인. Odometer.getCurrentDistanceDriven()/registerDistanceDriven()/checkCurrentDistanceDrivenCapabilities(): CAR_DRIVE normal, km.
- 실제 기동·값 수신은 미검증. 문제 인계의 서버·다른 가족 단말 동기화는 이들 getter가 해결해주지 않는다.
- Safety.registerTelltales()/checkTelltalesCapabilities()는 PRIVATE_CAR_SAFETY signature/privileged. getTelltalesInfo() 권한 설명 상충. 자동 경고등 핵심 의존성 제외.
- Tire.getLowTirePressure()는 임계치(kPa)이지 현재 네 바퀴 압력·저압 Boolean 아님. N6에서 자동 수집으로 쓰지 않는다.
- 증상카드는 누적거리·사용자 표현만으로 기본 기록을 완성하고 공조·차속·위치는 선택. 현재온도 미지원이 전체기능 실패가 되게 설계하지 않는다. 센서 측정시각이 없으면 요청·수신 시각을 표시한다.

### T8. 공통 시연 신뢰도

1. 실제 SDK 수신 / Console 조작 / 앱 내부 fixture / 사용자 수동입력을 화면에서 구분.
2. 공급자 데이터와 앱의 추정·경과시간을 분리. 값이 오래되면 마지막 수신시각 표시.
3. 앱이 데이터를 못 받은 구간을 보간해 실제 운행/휴식으로 확정하지 않음.
4. 지원/권한 거부/null/네트워크 없음/재실행을 정상 흐름과 함께 시연.
5. 서버·휴대폰 없이 차량 화면에서 핵심 결과 확인. QR·원격 전송은 별도 검증 전 필수로 넣지 않음.
6. 최종 App Market 입점은 별도 심사·지원차종·지역 조건. 대회 APK sideload 시연과 상용배포 허가는 같지 않다 [F08].

## E. 자체 점수·신규 상위3개·실제 PT 계획

### 자체 평가 기준

각 항목1~5, 동일 가중 총35. 차량특화/창의/기술활용/사업성은 공식1차 항목 이름을 빌렸고, 수요/친화/구현은 추가한 자체 항목이다. 공식 배점·수상확률·사용자테스트 결과가 아니다. 구현1~2는 핵심범위가 미지원/부적합해 **신규 상위3에서 제외**했다. 3은 초기 기술 게이트가 남는 중간, 4~5는 축소 로컬 MVP의 개발 통제력이 높은 정도다. 실행 검증 완료를 뜻하지 않는다.

| 후보 | 차량 | 창의 | 기술 | 사업 | 수요 | 친화 | 구현 | 총35 | 판정 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| N1 정차 큐 |4|2|4|3|3|4|3|23|신규1위, 음성·주차 게이트 조건부|
| N2 휴식 약속 |4|2|3|2|4|4|4|23|신규2위, 상용 중복 강함|
| N3 새 차 적응 |4|2|3|2|3|3|5|22|신규3위, 콘텐츠·Gleo 대비 검증|
| N4 시야 준비 |3|2|2|2|3|3|4|19|스마트폰 대비 우위 약함|
| N5 멀미 준비 |3|2|3|2|4|3|4|21|수요 존재, 핵심해결·차량우위 약함|
| N6 냉간 점검 |2|1|1|2|3|2|4|15|수동 입력·폰 우위|
| N7 하차 준비 |3|1|3|2|2|3|4|18|근거 간접·기존알림 중복|
| N8 눈부심 |2|1|1|1|4|2|1|12|직접해결 불가, 상위 제외|
| N9 환기 타이머 |4|1|3|1|2|2|4|17|OEM 자동환기 중복|
| N10 도착 오디오 |4|1|3|2|2|4|2|18|TaleSync·ETA 미검증, 상위 제외|
| N11 주차 리셋 |2|1|1|1|3|4|5|17|Polestar Breathe 직접 선행|

N1/N2 동점에서, 안전을 위해 위험행동을 검출·권고하는 범위를 더 적게 요구하는 N1을 신규 우선 실험으로 뽑았다. 다만 STT 게이트 실패 시 N2로 자동 결정하지 말고 기존안을 포함해 재평가한다. 점수 차이1~2점에 통계적 의미는 없다.

### E1. N1 정차 큐 PT 90초 계획

- 0~20초: 주차 중 앱 실행, SDK/권한 상태 확인. 미확인 항목은 그 자리에서 명시.
- 20~40초: 허용된 음성 경로로 할일2개 저장. 실제 STT 결과인지 fixture인지 표시.
- 40~60초: Console D→긴목록/입력 잠금. 신호대기/UX 일부 허용만으로 목록을 열지 않음.
- 60~80초: P 실제 수신+UX허용→보류항목 표시→한 항목 완료.
- 80~90초: 앱 재실행→나머지 기록 보존. 권한거부 시 수동모드로 전환하되 '주행 음성 MVP 미충족' 표시.
- 성공 조건: 저장/잠금/복원은 재현 가능, 다른 앱·운전에 간섭하지 않음. 실패 조건: STT 불가, P 미수신, 주행 중 자동 상세 표시, 상태공백을 안전으로 오인.

### E2. N2 휴식 약속 PT 90초 계획

- 사용자가 간격 한 번 설정. Demo x배속임을 화면에 표시.
- D 관측 구간과 P 관측 구간을 타임라인이 아닌 숫자 상태로 최소 표시. 실제 이동시간이라고 부르지 않음.
- P→'잠깐 쉬기' 수동 선택, 관측된 주차 경과만 누적.
- 중간 D→휴식 완료 조건 미달로 남김. 다시 P 유지→사용자 확인 후 완료 기록.
- 앱 중단 구간은 unknown. 몸의 피로 회복·법정휴식 충족을 자동 판정하지 않음.
- 실패 조건: gear 미지원·백그라운드 구간을 운전으로 산정·녹색 안전점수·휴식 후 계속 운전해도 된다는 보증.

### E3. N3 새 차 적응 PT 90초 계획

- 한 차종·연식·트림 콘텐츠 출처를 먼저 보여준다. 공조·제습·와이퍼 세 항목만.
- 정차 중 기능 위치를 찾아 사용자가 직접 조작. SDK가 지원하면 변화된 값을 보여주고, 미지원이면 수동확인.
- 다음 카드로 넘어가고 '찾아봤음'만 로컬 저장. 앱의 차량제어는 없음.
- D 또는 제한/알 수 없음→교육 화면 중단.
- 실패 조건: 틀린 트림 안내, 실제 수신 없는 자동완료, 변속·제동 연습 유도. 자동 수신이 없어도 교육 핵심은 시연할 수 있으나 기술활용 점수는 낮춰야 함.

## F. 기존 NextDrive·증상 카드와 직접 비교

이 표는 기존 코드의 품질·현재 빌드 상태를 검사한 평가가 아니라 서비스 범위 비교다. 기존 코드가 구현돼 있다고 추정하지 않았다.

| 대상 | 해결 문제 | 기본 MVP·추가 의존 | 차량우위·차별화 | 반복 사용·병목 | 자체35 |
|---|---|---|---|---|---:|
| NextDrive 축소 | 공동차 미처리사항 인계 | 로컬2프로필은 쉬움. 실제 가족 공유·계정·동기화는 추가 | 상태+인계는 맞지만 Fleetio·OEM공유 선행 | 여러 가족의 참여와 채팅 대비 가치 미검증 |19|
| 증상 순간 기록 | 간헐 증상의 당시 상황을 정비소에 설명 | 사용자표현·시간·누적거리 로컬, 공조 선택. 음성은 추가 게이트 | 일반 메모보다 차량값 결합 이유 명확. ALLDATA 마커/OBD 선행 | 매일 쓰진 않지만 한 사건으로 결과물이 남음. 정비 도움 미검증 |24|
| N1 정차 큐 | 이동 중 할일의 보류와 주차후처리 | 로컬+UX+선택gear, STT 주행게이트 필수 | 범용 수요 가능, 음성메모·OtoZen·parked UX 중복 | 한 발화/한체크, 앱생존·기존메모 대비 가치 미검증 |23|
| N2 휴식 약속 | 정한 휴식의 이행 | 로컬시계+gear, 상시알림은 제외 | 기어 맥락은 합리적, EROAD·DAW 강한 선행 | 장거리 중심, 매일이 아님. 피로 진단으로 확대하면 불가 |23|
| N3 새 차 적응 | 낯선 조작의 초기 학습 | 정차 교육+자체콘텐츠, 공조/와이퍼 선택 | 대시보드 앞 실습, Gleo·Hertz 선행 | 차를 바꿀 때 위주. 콘텐츠 유지·정확성 부담 |22|

비교점수 구성: NextDrive=차량3+창의2+기술3+사업2+수요2+친화3+구현4=19. 증상카드=5+3+4+3+3+3+3=24. 새 후보는 E표와 동일하다. 앞 절의 30점 척도와 직접 비교하지 않는다.

- **NextDrive 유지의 유리점:** 로컬 UI 범위가 예측 가능하고 기존 문제 카드 구조를 활용할 여지는 있음. 그러나 실제 코드 재사용량·현재 동작은 확인하지 않음.
- **증상카드 유지의 유리점:** 설명이 어려운 한 사건이라는 결과가 분명하고 공개 읽기값이 기록을 보완한다. 진단·안전 보증·가족참여·외부 결제 없이 성립한다.
- **신규안의 유리점:** N1은 더 넓은 일상 상황, N2는 강한 피로 문제 근거, N3는 쉬운 정차 시연. 하지만 모두 기존 서비스가 충분히 못 푼다는 근거가 약하다.
- **재평가의 진짜 변화:** 공개 normal인 공조·창문·와이퍼·조명 읽기 경로를 더 찾았지만, 이를 단순 표시/기록하는 앱은 사용자 가치·신규성이 부족했다. API가 많다는 이유로 아이디어를 통과시키지 않았다.

## G. 최종 추천 1개와 선택 이유

### 추천: 증상 순간 기록 카드의 초소형 읽기 전용 MVP 유지, 확정 출시가 아닌 기술·가치 검증

1. 신규11개를 검토했지만 **명확한 결과물, 차량 데이터의 필요성, 구현 통제력**에서 기존 증상카드를 넘는 근거가 나오지 않았다.
2. 추천은 1점 차 승리가 아니라 범위의 문제다. '발생한 한 사건을 설명'하는 앱은 실제 기록이 남는다. 환기·눈부심·멀미 앱은 읽기만으로 본질적 문제를 해결하지 못한다.
3. NextDrive처럼 여러 가족이 가입해야 가치를 보거나 서버 동기화를 완성해야 하지 않는다. 단, 스마트폰 메모 대비 가치와 정비사의 실제 유용성은 아직 미검증이다.
4. **세계 최초·수상 유력·실차 검증 완료라고 말하지 않는다.** 기존 ALLDATA 마커/로그/보고서 선행을 발표에서 공개한다.
5. 신규만 택한다면 N1 정차 큐가 우선 실험 대상이다. 다만 기존 음성메모보다 실제로 덜 산만하고 주차후처리가 잘 되는지 보여주지 못하면 신작으로 확정하지 않는다.

### 추천 MVP의 구체 범위

- 정차 중 사건 생성·사용자 표현·요청/수신시각·누적거리 저장.
- 수신 가능한 공조 설정값만 선택 첨부. 미지원은 비움.
- 한 사건에 부족한 설명 최대3개, 모르면 건너뜀.
- 로컬 한 장 조회. 외부 공유는 부가기능이며 첫 시연은 차량화면 안에서 완료.
- 주행 중 음성 기록은 별도 초기 게이트. 이것이 실패하면 '정차 후 회고기록'으로 축소됐음을 인정하고 핵심가치를 다시 평가한다.
- 자동진단·소리 분석·경고등 특권권한·증상 재현 주행·차량제어·안전보증·상시감시·가족 계정 없음.

### 26일 내 개발 계획과 중단 기준

- **11/07~08:** 환경 설치, cold boot, 실제 SDK getter1개 및 Capability·null/권한거부 처리. STT/Gleo·기어 읽기를 별도 실험. 개발 리소스가 충돌하면 누적거리1개부터.
- **11/09~12:** Room 상태모델, 사건 생성/조회/재실행, 실제데이터/fixture 출처 배지. 출품범위 확정.
- **11/13~15:** 1차 기획서·실행영상·가능한 개발산출물 정리. 접수는 별도 사용자 절차이며 이번 조사에서 제출하지 않음.
- **11/16~22:** 허용될 때 경험자·정비사에게 기록항목 유용성 평가, 실패·미지원 경로 보완. 이번에 이미 인터뷰한 것으로 쓰지 않음.
- **11/23~29:** 지원환경 고정, 화면크기·주행잠금·권한거부·재실행·네트워크 없음 시험. 실제수신 데모와 fixture 데모 분리.
- **11/30~12/02:** 기능 동결, release APK·재현 절차·90초 발표 시연과 예비영상.
- **12/03~04:** 동작앱 시연. 2027년 전시는 핵심앱 개발 유예가 아님.
- **중단 기준:** 첫48시간 안 공개 getter를 실제로 못 읽거나 앱기동·권한 문제가 해소 안 되면 자동 차량기록 구현 가능을 주장하지 말 것. 정비사/운전자에게 일반메모 대비 가치가 없으면 기능을 늘리지 말고 재기획. N1도 STT·주차게이트 실패 시 상위 추천에서 제외.

## H. 미검증·미해결 사항과 공식 근거

| 항목 | 현재 상태 | 가장 작은 다음 검증 |
|---|---|---|
| 이 PC의 Emulator2.1.0 설치·GPU·cold boot | 미검증 | AVD 실제부팅·Console 연결 |
| public normal getter 실수신·Capability/영역 | 문서확인, 실행 미검증 | 한 API씩 호출·오류·null 기록 |
| GEAR_SELECTION과 Console P/R/N/D 매핑 | Android normal근거, Pleos 실행 미검증 | getCarPropertyConfig+getIntProperty 실수신, 상태손실잠금 |
| STT_SERVICE protection level/실제권한/주행실행 | 선언 경로만 확인, 나머지 미검증 | provider 확인·실제 앱 initialize/request·오류로그 |
| Gleo AppSchema 등록·다른 화면에서 음성호출 | 미검증 | 공식 예제 최소action1개 시험 |
| app background·절전·시동OFF·재부팅 | 미검증, 상시실행 약속 안 함 | 프로세스 종료/복원·공백표시 시험 |
| NaviHelper ETA API·필드·단위·권한보호·기본내비 | 미검증 | 공식 reference+sample에서 request/response를 실제확인 |
| CO2/AQI/습도·실제4륜압력 | 확인한 Pleos목록에서 확보못함 | 필수기능에서 제외. 타 API 있다면 권한부터검증 |
| 안전한 외부공유·반출·모바일 연동 | 핵심제외·정책 미검증 | 로컬결과 가치부터 확인 |
| 제품수요·기존앱대비 개선·지불의향 | 미검증 | 실제 경험자 테스트·경쟁흐름 비교 |
| 최종 시장배포·카테고리·DO 승인 | 미검증 | 공모전 sideload와 분리해 공식정책 확인 |

### 근거 URL 목록

#### 공식 공모전·환경

- F01 https://appdevchallenge.auto-hmg.io/
- F02 https://appdevchallenge.auto-hmg.io/guide/submission
- F03 https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006
- F04 https://appdevchallenge.auto-hmg.io/guide/development
- F05 https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- F06 https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator
- F07 https://document.pleos.ai/en/docs/pleos-only/release-note/emulator-release-note
- F08 https://appdevchallenge.auto-hmg.io/guide/judging-awards

#### 문제 근거

- P01 https://road-safety.transport.ec.europa.eu/document/download/2e5e26c2-29fc-43b1-a6b6-6e4be901be8f_en?filename=ERSO_SPI_Fatigue_2023-12-13.pdf
- P02 https://epts.kdi.re.kr/archive/polSuvy/view?CTE_SEQ=32650&POL_SUVY_SEQ=1 ; 결과보도 https://news.kbs.co.kr/news/view.do?ncd=3192619
- P03 https://pubmed.ncbi.nlm.nih.gov/27427304/ (Prospective memory while driving, 24명 시뮬레이터)
- P04 https://www.jdpower.com/business/press-releases/2025-us-initial-quality-study-iqs/
- P05 https://www.rac.co.uk/drive/news/motoring-news/young-people-dont-clear-ice-from-windscreen-before-driving/
- P06 https://researchonline.rca.ac.uk/4373/
- P07 https://tracanada.ca/be-tire-smart/vast-majority-of-canadian-motorists-plan-on-taking-summer-road-trips-but-most-will-avoid-the-u-s-survey ; 상세 https://tracanada.ca/wp-content/uploads/2025/12/250429-TRAC-Report.pdf
- P08 https://www.uber.com/us/en/newsroom/the-2026-uber-lost-found-index/
- P09 https://www.trl.co.uk/publications/glare-from-road-vehicle-lighting-on-uk-roads
- P10 https://aaafoundation.org/research/visual-cognitive-demands-using-vehicle-information-systems/
- P11 https://dl.acm.org/doi/10.1145/3191760 ; 실도로연구 검색확인 https://pmc.ncbi.nlm.nih.gov/articles/PMC8849608/ (본문 재조회403, 전체효과 일반화 금지)
- P12 https://pubs.lib.uiowa.edu/driving/article/id/28177/ (2005 과거 기기 학습성 연구, 최신차 빈도아님)

#### 경쟁 서비스·OEM·수상작

- C01 https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/smart-control ; https://ownersmanual.hyundai.com/ivi/STD_GEN5W/AVNT/KOR/Korean/009_BlueLink_service.html
- C02 https://www.kia.com/kr/customer-service/kia-members/kia-app/control ; https://www.kia.com/kr/customer-service/kia-members/kia-app/map
- C03 https://www.hyundai.com/worldwide/en/newsroom/detail/0000001168
- C04 https://ownersmanual.hyundai.com/full_webhelp/MX5/2026/ko_KR/id25006c63ca8.html ; https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/ide40ee3ba189.html
- C05 https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink/bluelink-safety/careless-driving-alerts
- C06 https://ownersmanual.hyundai.com/ivi/ccNC/AVNT/KOR/Korean/Settings_navigation.html
- C07 https://www.easyparkpartners.com/in-car-parking-solutions
- C08 https://support.pocketcasts.com/knowledge-base/android-automotive ; https://support.pocketcasts.com/knowledge-base/chapters/
- C09 https://www.volvocars.com/intl/media/press-releases/C64CA30573E8A1D9
- C10 https://www.eroad.com/en-au/products/eroad-drive-buddy-fatigue/
- C11 https://play.google.com/store/apps/details?hl=en_US&id=com.urbandroid.kinestop
- C12 https://www.tesla.com/ownersmanual/models/en_us/GUID-4F3599A1-20D9-4A49-B4A0-5261F957C096.html ; https://www.tesla.com/ownersmanual/model3/en_eu/GUID-61E5B0E4-3595-4D66-AA4A-188F3ED6DD49.html (공식검색발췌, 직접fetch403)
- C13 https://www.skoda-auto.com/connectivity/infotainment-apps-traffication
- C14 https://www.hertz.com/us/en/vehicles/tesla/faq/your-first-drive
- C15 https://built-in.google/cars ; https://built-in.google/cars/gemini
- C16 https://tmaphelp.zendesk.com/hc/ko/articles/31280628460955-%EA%B5%AC%EA%B0%84%EB%8B%A8%EC%86%8D-%EA%B3%BC%EC%86%8D%EB%8B%A8%EC%86%8D-%EC%95%88%EB%82%B4%EA%B0%80-%EB%82%98%EC%98%A4%EC%A7%80-%EC%95%8A%EC%95%84%EC%9A%94
- C17 https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-24h-code-race-hackathon
- C18 https://www.hyundaimotorgroup.com/ko/story/hyundai-motor-company-pleos-connect-app-idea-contest
- C19 https://ownersmanual.hyundai.com/full_webhelp/DN8/2026/ko_KR/idd75f541fec9.html
- C20 https://ownersmanual.kia.com/full_webhelp/CV1/2026/ko_KR/topics/chapter4_25_5.html
- C21 https://ownersmanual.hyundai.com/ivi/DA_GEN2_V/AV/EUR/Korean/010_Settings_car.html
- C22 https://www.polestar.com/us/news/polestar-breathe-mindfulness
- C23 https://www.otozen.com/pages/location-based-reminder
- C24 https://ownersmanual.hyundai.com/ivi/ccNC/AVNT/KOR/English/Phoneprojection_solution.html ; https://ownersmanual.hyundai.com/ivi/ccNC/AVNT/KOR/English/Appendix_troubleshooting.html

#### 기술 공식 URL

- T00a https://developer.android.com/guide/topics/data/data-storage
- T00b https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime()
- T01a https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide ; https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager
- T01b https://developer.android.com/design/ui/cars/guides/ux-requirements/driving-state
- T01c https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- T02a https://developer.android.com/reference/android/car/VehiclePropertyIds#GEAR_SELECTION
- T02b https://developer.android.com/reference/android/car/Car#PERMISSION_POWERTRAIN
- T02c https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager#getIntProperty(int,int)
- T02d https://source.android.com/docs/automotive/driver_distraction/consume
- T03 HVAC목록 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry
- T03 recirculation 개별권한 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-recirculation-state
- T04 window https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-window-position
- T04 wiper https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/get-current-wiper-state
- T04 light https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-low-beam-light-state
- T05 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro ; https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions ; Gleo https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/app-capability
- T06 https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro ; https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions ; https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners ; sample https://github.com/Pleos-Playground/pleos-sample-navi-app
- T07 Fuel/Odometer/Safety/Tire 개별 공식URL은 보존된 이전 조사 G/J절 참조.
- T08 https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide ; 셋업 https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator

### 이번 갱신 범위

- 앞의 2026-11-06 리서치 원문을 그대로 보존하고 이 날짜 섹션만 추가.
- 다른 프로젝트 파일·Codex 소스·저장소 설정·기존 코드 변경 없음.
- 조사와 제안만 완료했으며 구현·인터뷰 완료 주장은 하지 않음.
