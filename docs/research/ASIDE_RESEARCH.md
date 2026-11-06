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
