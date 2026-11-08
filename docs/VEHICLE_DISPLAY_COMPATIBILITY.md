# 차량 동력 방식·메뉴·조회 시각 검증

기준: 2026-11-08, main `26bcaed41007b5a4cd2d2db3068d48edaa4df959`. Kotlin Android View/Vehicle SDK2.3.2를 유지합니다. 이번 목표는11월10일 안정 버전 확보이며 AI·새 서버·상시 관측·차량 제어를 추가하지 않습니다.

## 공식 API와 확인 범위

[Vehicle SDK 소개](https://document.pleos.ai/api-reference/connect-sdk-pleos/Vehicle/intro)는 차량 내부 Vehicle Service의 비동기 Capability/get/release 흐름을 설명합니다. 외부 차량 계정용 Vehicle Data API와 다른 SDK입니다. 문서의 기본 정보 소개만으로 이번 AAR에 모델/동력 방식 메서드가 있다고 가정하지 않았습니다.

[도메인 권한](https://document.pleos.ai/api-reference/connect-sdk-pleos/Vehicle/vehicle-permission)과 [CarInfo의 VIN 조회 문서](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/get-v-i-n-info)를 대조했습니다. 일부 API 웹 페이지는 빈 본문/접근 오류여서 실제 설치된2.3.2 AAR의 공개 인터페이스·RequiresPermission annotation을 함께 확인했습니다. AAR의 class를 프로젝트 내부 진단 폴더로 추출해 javap 직접 파일 읽기 exit0으로 확인했습니다. 최초 jar 경유 조회는 결과 출력 뒤 AccessDeniedException으로 끝나 정상 종료로 판정하지 않았습니다.

| 대상 | 설치 AAR의 API | 필요한 조회 권한 | 판정 |
|---|---|---|---|
| CarInfo | checkVINInfoCapabilities/getVINInfo, checkCRNInfoCapabilities/getCRNInfo | CAR_INFO | API 존재. 현재 앱은 권한을 추가하거나 VIN/CRN을 수집하지 않음. 실제 Capability/응답은 미검증 |
| CarInfo의 getCapabilityInfo | 도메인 capability map | PRIVATE_CAR_INFO | 추가 특권 권한이므로 호출하지 않음 |
| 동력 방식/모델 | 설치 CarInfo 공개 인터페이스에서 직접 읽기 메서드를 찾지 못함 | 확인된 호출 없음 | 자동 식별 미구현·실차 식별도 미검증. VIN 디코딩/제조사·연식 추정 안 함 |
| EvBattery | checkEvBatteryStateOfChargeCapabilities/getEvBatteryStateOfCharge | CAR_ENERGY | 기존 단발 읽기 유지. 성공한 충전량만으로 EV 확정 안 함 |
| Fuel | checkFuelPercentCapabilities/getFuelPercent | CAR_ENERGY | 기존 조회 유지. UNSUPPORTED는 연료0/EV 판정이 아님 |
| Odometer | checkCurrentDistanceDrivenCapabilities/getCurrentDistanceDriven | CAR_DRIVE | 기존 km 조회 유지. 신호별 실제 응답과 시각 보존 |

CAR_INFO/PRIVATE_CAR_INFO를 manifest에 새로 추가하지 않았으며 기존 CAR_ENERGY/CAR_DRIVE만 유지합니다. 가능한 모든 Pleos 버전/실차에 동력 방식 API가 없다는 주장은 아닙니다. 현재 공식 문서·설치 AAR로 확정할 수 없는 정보를 앱이 만들어내지 않는다는 의미입니다.

## 차종 미확인과 수동 화면 배치

VehicleDisplaySettings의 별도 SharedPreferences에 화면 배치만 저장합니다. 기존 DB/관측/차량 식별/운전자/자동 판정 데이터에 차종을 기록하지 않습니다. 초깃값은 차종 미확인입니다. 메뉴→차량 동력 방식·시각 설정에서 변경하며 안전 입력 허용 상태에서만 저장합니다.

| 사용자 배치 | 우선 표시 |
|---|---|
| 차종 미확인/기타 | 실제 지원 또는 유효 수신 이력이 있는 에너지 신호와 누적거리 |
| 전기차 | 구동 배터리 충전량과 누적거리 |
| 내연기관차 | 연료 잔량과 누적거리 |
| 하이브리드/플러그인 하이브리드 | 연료·구동 배터리를 별도 항목으로, 누적거리 |

EV의 쉬운 이름은 배터리 충전량입니다. 그 외에는 구동 배터리 충전량으로12V 보조 배터리의 공통 건강 상태처럼 보이지 않게 합니다. 수동 선택은 사용자 화면 배치라고 명시하며 SDK가 차종을 확인했다고 표시하지 않습니다. 선택한다고 읽기 Capability/권한이 생기거나 가상 값이 채워지지 않습니다. 미지원은 지원되지 않음, 조회 실패/미수신은 확인할 수 없음/확인 전으로 구분합니다. 유효한0%/0km는 결측과 다릅니다.

현재 수동 설정에 따른 누락은 화면 배치뿐입니다. 수집한 SOC·거리·연료 결과/모든 원본·조회 이력은 유지합니다. 과거 선택 조회는 당시 수신한 신호 기준으로 보여주며 현재의 수동 차종을 과거의 SDK 식별 결과로 붙이지 않습니다. SnapshotStore/ObservationStore/SDK 수집 정책은 바꾸지 않았습니다.

## 메뉴 일관성

이전 메뉴는 원래 화면 본문 앞에 패널을 추가했고 원래 탭도 선택된 상태였습니다. 이제 MENU 화면에만 메뉴가 표시되며 원래 본문을 함께 그리지 않습니다. menuReturn이 시작 화면을 기억하고 닫기/메뉴 재누르기로 돌아갑니다. 선택 강조는 메뉴 하나이며 홈·이전의 기존 의미는 유지합니다.

공통 메뉴5개와 순서: 기록할 사람 선택·변경 → 내가 작성한 기록 → 차량 조회 기록 → 사용법·화면 설정 → 차량 동력 방식·시각 설정. 폭/출발 화면에 따라 이5개를 바꾸지 않습니다. 좁은 창에서 상단의 보조 탭이 접힌 경우에만 패널 바깥에 기록/차량 정보 이동을 별도로 제공합니다. 넓은 창에서는 이를 중복하지 않습니다.

메뉴는 기존 이동 이력에 불필요한 단계를 남기지 않는 임시 목적지입니다. 메뉴에서 사용법 같은 항목으로 이동했다가 닫으면 시작 화면으로 돌아갑니다. 원래 검색어·필터·스크롤/초안을 유지하며 보존 실패 시 이동을 막습니다. 메뉴 열기/닫기는 운전자 확정·문제 저장·읽음·삭제·상태 변경·SDK 새 조회를 수행하지 않습니다.

## 조회 시각과 내부 버전

- 차량 정보/조회 이력/연결 차량 정보는 기본 한국 시간입니다. `yyyy.MM.dd HH:mm:ss KST (+09:00)`과 한국 시간 설명을 표시합니다. 기본 날짜·시각은 예시가 아닌 원래 epoch의 표시 변환입니다.
- 기기 시간 사용을 선택하면 실제 IANA 시간대 이름과 당시 오프셋을 표시합니다. 예: America/Los_Angeles (-08:00). DST는 해당 시점 ZoneRules로 계산합니다. OS 시간대를 수정하거나 epoch를 덮어쓰지 않습니다.
- Android의 z 약어가 KST 대신 GMT+09:00으로 출력되는 것을 실제 화면에서 확인했습니다. 한국 시간의 KST와 기기 시간대 ID를 명시하는 방식으로 플랫폼 표현 차이를 없앴습니다.
- 수신 시각은22sp/본문 대비/강조로 높였습니다. 신호별 이전 성공값의 시각·최신 실패·10분 오래됨·미래 시각 경고를 유지합니다. 각 신호를 일괄 최신으로 표시하지 않습니다. 사용자 증상 발견/이벤트 시각의 기존 기기 시간 의미는 이번에 바꾸지 않았고 상담의 시간대 설명을 유지합니다.
- 일반 상담용 문자열에서 내용 버전과 확인 이력의 내부 revision 차수를 숨겼습니다. 실제 재발 횟수·수정/점검/상태/읽음 이력은 남깁니다. 모델·DB revision/권한/재발 계산/원문·이력은 변경하지 않았습니다. P1 상담 화면 전체 구획 재작성은 보류했습니다.

## 실행 검증 결과

2026-11-08 20:00 +09:00 (Asia/Seoul), emulator-5554/user10/Pleos Connect v2.1.0/API34에서 아래를 실제 수행했습니다.

- 최종 assembleDebug/assembleValidation/assembleValidationAndroidTest: offline BUILD SUCCESSFUL in1m26s,93작업13실행/80 up-to-date. 기존 JBR/Gradle8.11.1/Build-Tools35.0.0/Vehicle2.3.2 유지. 일반 Debug APK SHA256 `02D2BA03BBD58F9A78977D30FB68A17A71EF0FB53B2F283583050968FE5BCCA2`.
- 기존246개를 최종 앱에서 재실행: MVP24+observation6+UI27+migration18+RR36/cold2+NAV17/cold12+ON32+AM35/cold4/사본보존2+PX25/3DB보존6. 신규 CompatibilityChecks48+실제 force-stop 뒤 cold3=51. 합계 **297 assertion 통과**. 최종 로그는 app/build/validation/compat-final-*.txt, 이전 실패 로그도 삭제하지 않았습니다.
- 기존198 native render, ON200 조합, NAV 낮은 높이6조합 재통과. 새 공통 메뉴/설정은320/600/1024dp·100/200%·두 테마의12조합에서24화면 상태를 실행했습니다. 메뉴5항목 불변, 설정 버튼76dp/말줄임 없음/가로 넘침을 검사했습니다. 이는 실제 OS 분할 창·키보드·모든 차종/화면 조합·일반 사용자 이해도 시험을 대체하지 않습니다.
- 첫 신규 검사의 KST 약어 기대 실패는 실제 GMT+09:00 출력을 확인해 보완했습니다. 중간 기존 UI 검사는 dirty 초안의 운전자 전환 차단 후 메뉴에 안내가 남아서 실패했습니다. 차단 시 원 작성 화면으로 돌아가 피드백을 보이도록 수정하고 신규 검사를 추가한 뒤 최종 전체 검사를 다시 통과했습니다. 검사를 약화하거나 실패 결과를 통과로 바꾸지 않았습니다.
- 최종 실제 SDK session ed1d15fb-1df1-48bb-91bd-d874c096d4d9: EV_SOC100%/receivedAt1794135239509, ODOMETER0.0km/receivedAt1794135239728, READ/RECEIVED·PLEOS_EMULATOR_SDK. 연료 UNSUPPORTED/ERROR_NOT_SUPPORTED_PROPERTY·값 없음. CarInfo를 실제 조회한 결과가 아닙니다. 내연기관/HEV/PHEV의 성공 연료값은 in-memory validation fixture 검사뿐이며 실차/실제 SDK 수신 성공으로 보고하지 않습니다.
- 실제 cold 재실행 화면에서도 하이브리드 수동 배치·기기 시간대 복원을 확인했습니다. 연료 미지원/SOC·거리의 실제 값은 별도 카드입니다. 초안은 같은 ID/원문으로 cold 저장 검사를 통과했습니다. 해당 cold 홈의 초안 배너는 당시 화면 아래 영역이라 캡처에 모두 보이지 않으며 캡처만으로 초안 UI 전체 통과를 주장하지 않습니다.
- 일반 앱 install-r 성공/COLD Status:ok, 설치 base.apk SHA256이 위 APK와 일치했습니다. 실제 일반 홈은 차종 미확인·구동 배터리100%/거리0km·2026.11.08 19:57:41 KST(+09:00)·에뮬레이터 출처를 표시했습니다. 메뉴에는 원래 본문이 없고5항목/메뉴 하나 선택만 표시됐습니다. 일반 설정/기록 목록도 확인했습니다. OS 시간대 America/Los_Angeles는 그대로입니다.

### 현재 사용자 데이터 보존

- 개인 백업 `.tools/compat-backup/20261108-193209/`에 before.tar(전체 databases/shared_prefs/files), 개별3DB, 계측 전후 사본, after-install.tar·사본, 비공개 화면/XML을 보존했습니다. before.tar SHA256 `13AEA0E77E341DB53DE5B22AA526DB6B13440820B4F282219F2CB8B9E9E1F6B2`. 과거 개인 백업은 삭제하지 않았습니다.
- 모든 계측 전후 일반3DB는 바이트 동일했습니다. 일반 문제 DB는 교체 설치/화면 확인 후에도 SHA256 `A296015F415A8CAFE775C7E149C78EB2E46166AFCF5FD04344386FABF47D8FFB`로 동일합니다. **현재 일반 목록23건**을 직접 확인했습니다.22건으로 고정하지 않았습니다.
- 최신 백업과 설치 후3DB의 전체 scalar/운전자·문제·이벤트·초안·관측·불변 연결 포함/동일 schema version/integrity/FK를 validation의 읽기 전용 사본에서 비교했습니다. 일반 관측/스냅샷에는 진입 시 실제 SDK 결과가 추가됐고 모든 이전 원본은 유지됩니다. 과거 v1 관측 사본 보존2검사도 통과했습니다.
- IssueModels/CaritdaService/IssueRepository/SqliteIssueRepository/RecordMigration/PleosReadClient/ObservationStore/SnapshotStore/EntryReadState/CarSafetyGate 파일은 기준 blob SHA와 동일합니다. 변경한 ConsultationSummary는 표시 문자열만 바꿉니다. DB migration·초기화·운전자 자동 확정·일반 앱의 시험 기록 생성은 없었습니다.
- 일반 홈/메뉴/설정과 validation의 KST 홈·두 테마1024dp 설정·cold 하이브리드 화면을 로컬에서 시각 확인했습니다.1024dp native 캡처와 실제 전체 앱 창 캡처는 구분합니다. 개인 캡처/DB/키/APK/캐시/로그는 GitHub에 게시하지 않습니다.

### 이번14개 인수 항목의 판정

| 사용자 항목 | 결과 |
|---|---|
|1~3 공통 메뉴/복귀/단일 선택 | 통과: 세 출발 화면의 같은5항목·스크롤/검색/필터 복원, 두 테마/확대 메뉴 검사 |
|4~5 홈 보존/SOC 의미 | 통과: 기존 PX·ON 회귀와 실제 차종 미확인/구동 배터리 표기 |
|6 차종별 UI | 부분:6수동 배치·validation fixture/현재 에뮬레이터 화면 통과, 실제 ICE/HEV/PHEV 식별·신호 미검증 |
|7~8 미지원/허위 값 방지 | 통과: 실제 연료 미지원, 결측/0/이전 성공과 실패 구분, 합성 데이터는 validation에 한정 |
|9 시각/시간대 | 통과: 동일 epoch의 KST/LA 비교·실제 두 설정 화면, OS/원본 불변 |
|10 내부 버전/실제 이력 | 통과: 기본 상세·상담의 내부 번호 없음, 원문·메모·재발/점검 및 revision 보존 |
|11~12 데이터/재실행 | 통과: 현재23건과3DB 전체 보존, cold 설정/초안 검사와 실제 배치/시간 화면 복원 |
|13 작은 창/확대 | 부분: native 행렬 통과, 실제 OS 분할/IME·모든 차종 조합/물리 접근성 미검증 |
|14 실제 Pleos 에뮬레이터 | 통과: 최종 앱/검사 APK 실행과 일반 교체 설치. 실차 성공과 다름 |

Lint는 기존 캐시 부재 상태에서 이번에 재실행하지 않았습니다. 별도 src/test 단위 테스트 소스는 없으며297개 계측 assertion과 실제 빌드를 근거로 합니다. Release 빌드/서명은 실행하지 않았습니다.

## 변경 파일과 직접 확인 순서

앱6파일: MainActivity.kt, domain/ConsultationSummary.kt, ui/CaritdaDesign.kt·VehiclePresentation.kt·VehicleUi.kt, 신규 ui/VehicleDisplaySettings.kt. 검사4파일: MvpScenarioInstrumentation.kt, ValidationEnvironment.kt, ValidationUiActions.kt, 신규 CompatibilityChecks.kt. 문서6파일: AGENTS.md, docs/CARITDA_DESIGN_SYSTEM.md·CODEX_PROGRESS.md·이 문서, 루트/앱 README.md. 총16파일이며 연구는 수정하지 않습니다.

1. 일반 차량 아이콘 CARITDA에서 기존 홈·실제 충전량/거리·KST 수신시각을 확인합니다.
2. 홈/기록/차량 정보 각각에서 메뉴를 열어 같은5항목과 단일 선택을 확인합니다. 닫기 후 원래 위치로 돌아오는지 확인합니다.
3. 메뉴→차량 동력 방식·시각 설정에서 본인 차량에 맞는 **수동 배치**를 고릅니다. 미지원 연료가0%로 바뀌지 않는지 확인합니다. 차종 미확인으로 돌아갈 수도 있습니다.
4. 한국 시간/기기 시간을 바꿔 보고 날짜·시간대 차이를 확인합니다. 기본 한국 시간을 권장 표시로 유지하며 OS 시간대를 바꿀 필요는 없습니다.
5. 기록 검색→상세/정비 상담에서 내부 내용 버전 없이 실제 재발·점검 이력을 확인합니다.
6. 초안 작성→메뉴 열기/닫기→계속 작성, 앱 재실행 후 설정/기록을 확인합니다. 추가 시험은 [TEST] 앱에서 수행할 수 있습니다.

검증된16파일만 최신 main을 부모/기대HEAD로 GitHub 연결 도구를 통한 게시합니다. 최종 커밋은 완료 응답으로 제공하며 문서에 자기SHA를 넣기 위한 반복 커밋은 하지 않습니다.

## 범위와 남은 검증

내연기관·HEV·PHEV 실차와 실제 연료값 수신은 미검증입니다. 해당 배치/합성 값 검사는 validation 안에서만 수행하며 실제 SDK 성공과 구분합니다. CarInfo Capability는 호출하지 않았으므로 미지원이라고 판정하지 않습니다. 실제 사용자 과제·OS 분할/키보드/물리 접근성·실차 안전 제한·Release 서명/제출 준비는 별도 검증입니다. 계측은 validation 앱만 대상으로 하며 개인 DB·백업·로그·캡처는 게시하지 않습니다.
