# CARITDA · DriveCheck 보조 통합 검증

기준: 2026-11-11 Asia/Seoul. 작업 시작 main `80f76b1bdac9044f649eba01e727ef4cee084b48`와 로컬209개 파일이 일치했다. 이후 main `d0e7281fea775d306dc2cf7d9f32e30a70d79ce8`에 연구8개만 추가된 것을 확인했다. 해당 연구와 기존18개 원격 전용 연구 자료를 보존하며 앱 전체 디자인을 개편하지 않는다. 게시 SHA는 완료 응답에서 확인한다.

## 1. 실제 구현과 검증 수준

A=공식 문서·공개 SDK, B=코드·빌드, C=격리 validation 자동 검사, D=실제 Pleos 에뮬레이터 SDK 응답, E=일반 앱에서 사람의 해당 흐름 확인, F=실차다. C의 합성 backend와 D의 실제 응답을 서로 대체하지 않는다.

| 기능 | 이번 결과 | 아직 확인하지 않은 범위 |
| --- | --- | --- |
| Fused 마지막 위치·선택 기록 연결 | A/B/C/D. 실제 SDK 위치·시각 수신과 fixture sidecar 재개방4개, Main의 동의·취소·append·재생성·cold restore8개 | 일반 앱에서 사람이 위치 연결하는 E, 실차 F |
| 정비 장소 좌표·Navi 상태 처리 | A/B/C/D 부분. 실제 목적지 일치·탐색 시작·ROUTE_DRIVE/ROUTE_GUIDING 수신, Main 입력/중복/취소/회전7개 | 현재 내비 앱의 onRoutingCompleted 미수신, 전체 완료 판정·E/F 미검증 |
| 차량 건강 보조 표시 | A/B/C/D. 실제20종 조회 중12종 수신·8종 미지원. 최신/오래됨/권한/오류/미지원/사용자 기준을 구분 | 차량 전체 정상·고장 판정, 실제 TPMS 압력, F |
| SOC 안내·사용자 기록·인계 | A/B/C/D. Console100→18→18→45, 실제 SDK GET/CALLBACK, 사건 한 건·명시적 A 저장·B 읽음·cold restore | 일반 앱에서 사람이 새 DriveCheck 흐름을 확인하는 E, F |
| STT·AI 설명·TTS | 기존 구현 유지, 이번 관련 회귀 재실행. 실제 Pleos TTS 완료 callback·실제 SDK 값의 로컬 AI 화면 표시 | 이번 새 유료 Gemini 요청0, 기존 실모델 성공을 새 시험 성공으로 사용하지 않음 |
| 급가감속 | 공개 API/권한·실제 입력 조사와 격리 합성 계산5개 | 실제 속도 권한/연속 속도·가속도/주기 검증이 부족해 일반 앱 분류·점수 없음 |
| 소음·진동 | validation 전용 최대5초 수동 마이크 진폭 PoC 보완, 합성7개·실제 기기 inventory | 이번 자동 마이크 수집0, 가속도 센서 미확보·차량 정상/이상 학습자료 없음. 일반 진단 없음 |

## 2. 위치 연결

기존 FusedLocation1.1.3 `getLastKnownLocation`만 재사용한다. 매 요청의 위치 설명·동의와 권한 확인 후 한 번 요청한다. 실제 위도·경도·SDK 측정 epoch·앱 수신 epoch·출처를 확인하고 10분 이상 된 위치, 미래 시각, 미수신, 권한 거부, 미지원, 오류,12초 timeout, 사용자 취소를 구분한다. confidence를 정확도(m)로 변환하지 않는다. 마지막 위치는 증상 발생 당시 위치를 입증하지 않는다.

사용자는 조회한 위치를 보고 다시 저장을 선택한다. issue DB의 원문/발견 시각/상태/작성자/이력은 쓰지 않는다. 기존 v1 `links` sidecar에 append하고 commit 후 같은 값의 재조회로 확인한다. 같은 issue/좌표/측정·수신 시각/출처의 재확인은 중복 삽입하지 않으며 새 연결은 과거 연결을 덮어쓰지 않는다. 연결 이력은 기본3개, 더 보기로 최대200개까지 읽는다. 저장 실패 시 기존 연결과 증상을 유지한다.

Main의 결과 확인 취소 뒤 버튼이 비활성으로 남던 오류를 자동 검사에서 발견하여 결과 수신 시 다시 그려 수정했다. 실제 Main fixture에서 취소→재요청→오래된 위치 명시적 연결→추가 연결→Activity 재생성→이력 조회·별도 프로세스 복원을 통과했다. 일반 앱 사용자 기록에 시험 위치를 추가하지 않았다. 위치·차량값을 AI body에 추가하지 않는다.

## 3. 내비게이션

NaviHelper3.0.5의 공개 API와 설치된 navi service3.0.4 / 내비 앱3.3.2-rc5-hotfix.3을 대조했다. SDK3.0.5를 설치 서비스 버전과 같은 숫자로 보고하지 않는다. 사용자 제공 API-reference destinations URL은 읽을 수 있는 본문이 없었고, 현재 공식 programming-guide destinations와 event-listeners·permissions·initialization·error 페이지 및 공식 Kotlin 샘플을 직접 확인했다.

`onRouteStarted`는 **경로 탐색 시작**이다. 기존 구현의 안내 시작 표기를 바로잡았다. preflight에서 `isRouting` 또는 `ROUTE_GUIDING`/`SIMULATION_GUIDING`이면 기존 경로를 건드리지 않고 차단한다. 약관 동의와 앱 준비를 확인한 뒤 좌표를 한 번 전달한다. 목적지 변경 이벤트 자체는 좌표 일치를 입증하지 않아 `getDestinationInfo` 결과를 비교한다. `onRoutingCompleted`, `ROUTE_DRIVE`, 조회된 안내 모드를 따로 수집한다. 현재 성공 조건은 목적지 일치+검색 완료+ROUTE_DRIVE+ROUTE_GUIDING의 일치이며 빠진 이벤트를 만들어 내지 않는다. 제한된 후속 상태 조회와30초 timeout, 오류/취소, generation 차단과 listener 해제를 유지한다.

일반 UI에는 수신 시각별 진행 이력을 최소 추가하고, 입력 좌표·장소 이름과 이력은 화면 재생성에서 복원한다. 앱의 “결과 확인 멈추기”는 listener만 해제한다. 생산 backend 인터페이스에는 기존 경로 취소 기능 자체가 없다. POI/가까운 정비소 검색·예약·견인 접수는 구현하지 않았다.

최종 실제 trace는 `CONNECTING → CONNECTED → PREFLIGHT_READY → REQUEST_SENT → ROUTE_SEARCH_STARTED → STATUS_ROUTE_DRIVE → MODE_ROUTE_GUIDING → DESTINATION_CONFIRMED → TIMEOUT`이었다. 요청·목적지 수신과 실제 안내 시작 이벤트/모드는 D로 확인했다. 그러나 **30초 안에 onRoutingCompleted가 오지 않아 전체 완료/guidanceConfirmed는 false**다. timeout에 누락 이벤트를 채우지 않는다. 이는 현재 설치 내비 앱의 이벤트 정합성 미완료이며 C의 완전한 합성 이벤트 성공과 다르다. 공식 문서도 Navigation 앱 구현 시나리오에 따라 이벤트가 달라질 수 있다고 설명한다. Main의 좌표 오류·중복 차단·검색/안내 구분·확인 중단·입력/이력 재생성7개와 SDK 실제 상태6개 검사를 별도로 통과했다.

초기 실제 시험에서 mode를 확인하지 않은 `isRouting=false`를 inactive로 해석하고, 목적지 일치+ROUTE_DRIVE만으로 조기에 완료하여 `onRoutingCompleted`를 놓친 잘못된 시험 기준을 발견했다. 이를 성공 근거로 재사용하지 않는다. mode 보호·완료 조건을 강화했다. 정리 시에는 이번 시험의 no-route preflight/dispatch 영수증과 같은 공식 문서 fixture 목적지의 현재 일치를 확인한 경우만 취소한다. 취소 후 늦게 도착한 목적지 초기화 응답을 기존 목적지 불일치로 오인하던 **시험 정리 코드**를 수정했고, 제한된 재조회로 inactive를 확인한다. 사용자 기존 경로 취소·변경은 수행하지 않는다.

## 4. DriveCheck·SOC

CARITDA의 기존 홈/기록/차량/AI 화면을 유지한다. “문·등화·차량 안내 확인”에 실제 수신값·단위·앱 수신 시각·출처·Capability 지원 여부와 상태를 표시한다. 최신 시도 실패와 이전 유효 값을 구별하며, 이전 값을 최신 정상값으로 표시하지 않는다. 오래됨/미래 시각/미지원/미수신/권한 부족은 차량 정상의 근거가 아니다. 도어·등화 ON·방향지시·페달은 단순 상태이며 고장으로 분류하지 않는다. 저압 기준은 실제 타이어 압력이 아니다.

SOC는 기존 `PleosSocObserver`/`ObservationStore`/`LowSocStore`를 사용한다. 등록과 유효 GET/변화 callback을 구별하며15초 내 유효 초기 수신이 없으면 timeout·해제한다. 이후 값이 변하지 않아 callback이 없다는 이유로 입력 실패를 만들지 않는다. 화면을 떠나거나 UXR 제한/실패면 해제·generation 차단·관측 공백을 유지한다. 원관측 commit 후 파생 안내를 정리하며 두 DB의 원자성을 주장하지 않는다. 시간 역순 원관측을 보존하면서 기존 안내 진입·해제·last 시각을 되돌리지 않도록 보완했다.

자동 안내는 사용자가 켠 기준에만 적용한다. 동일 범위의 LOW 반복, 날짜·운전자·재시작 때문에 사건을 중복 생성하지 않는다. 기준+2%p 이상 해제는 편의 기준이지 고장 수리나 안전 보장이 아니다. 실패/미수신/오래됨은 해제로 바꾸지 않는다. 정책·원관측·진입/해제 근거·기존 사건은 그대로 남는다.

DriveCheck의 확인 항목을 선택하면 원 작성자의 기존 초안을 우선 보존한다. 새로 작성할 때는 일반적인 편집 문장만 입력한다. 센서 숫자·시각·ID·좌표를 증상 문장이나 AI 입력으로 자동 복사하지 않는다. 사용자 확인→명시적 저장→기존 선택 AI 설명→다음 운전자 읽음의 흐름이다. 안내 수신만으로 문제/초안을 만들거나 상태를 해결로 바꾸지 않는다. 별도 DriveCheck 앱/서비스/전체 UI 개편은 없다.

실제 정차 validation MainActivity에서 공개 Console heartbeat·bridge와 P/0km/h/SOC100을 확인한 뒤 SOC18→18 반복→45를 요청했다. HTTP200과 별개로 SDK18 CALLBACK·반복18 GET·45 CALLBACK 및 원관측 참조를 확인했다. 진입1/해제1·동일 사건, A의 명시적 문제1개 저장, B 읽음1개, 종료 뒤 수신 중단·공백과 cold restore를 확인했다. 마지막 Console100/P/0와 SDK SOC100/실제 안전 gate ALLOWED 복원도 독립 확인했다. 일반 DB에 합성/Console 시험 기록을 복사하지 않았다. 최초 Console preflight는 gear.actual이 객체임을 문자열로 비교하여 안전하게 요청 전 거부됐고 파서를 고친 뒤 실행했다.

## 5. 안전운전·소음·진동 범위

Vehicle2.3.2의 실제 캐시된 공개 sources와 공식 샘플 public policy/catalog를 확인했다. 브레이크 페달은 boolean이고, cruise-control target speed는 설정값이라 실제 속도로 사용하지 않는다. 일반 사용 금지인 hidden Gear·TPMS/telltale·CarInfo capability API나 private 권한은 호출·추가하지 않았다. 공개 연속 실제 가속도/진동/소음 입력을 확보하지 못했다.

validation에 기존 선언된 AAOS CAR_SPEED 권한은 실제로 거부 상태다. 그래서 속도의 Capability/값/연속 수신/시간 해상도는 미검증이며 “하드웨어 미지원”으로 단정하지 않는다. 별도 공개 AAOS gear 단발 응답 status0/value4/config0Hz는 연속 운전 분석의 증거가 아니다. 실제 기기 가속도 센서는 없었다. 속도 차분의 수학·공백/세션/역순/NaN 검사는 합성 데이터5개이며 위험 사건 검출 성공이 아니다. 일반 앱에 급가감속 점수·경고·개인별 주행 기록을 추가하지 않았다.

마이크 입력 장치 inventory와 권한의 존재는 파형 수신 성공과 다르다. validation 진단 화면에서 사용자가 동의하고 누를 때만 최대5초 PCM RMS/peak를 측정하며 직접 중단할 수 있다. 정차/가시 상태·기존 RECORD_AUDIO 요청을 따르고 STT/TTS와 동시 실행하지 않는다. PCM은 메모리에서 축약 즉시 지우며 파일/네트워크/음성 문장/진단 로그를 남기지 않는다. 취소·안전 제한·onStop 때 stop/release·generation 차단하며 지연 결과를 무시한다. 디지털 진폭은 보정된 dB·음원·차량 진동·마모/베어링 판정이 아니다. 이번 자동 시험은 마이크를 시작하지 않았고7개 합성·안전 차단 검사만 수행했다. 정상/이상 label·실차 측정 기준이 없어 일반 진단으로 넣지 않았다.

## 6. 실제 수정 Kotlin 파일

| 범위 | 파일 | 핵심 변경 |
| --- | --- | --- |
| 일반 UI | `MainActivity.kt` | 위치 동의·append 이력, 목적지 입력/진행 이력, DriveCheck 표시와 사용자 초안 연결, fixture 분석 worker 격리 |
| 위치 | `integration/OneShotLocation.kt`, `LocationBackend.kt`, `LocationPresentation.kt`, `IssueLocationStore.kt` | 공개 SDK port, 단발/오류/취소, 시각·출처 표현, 기존 스키마의 append/readback/history |
| 내비 | `integration/InspectionNavigation.kt`, `NavigationBackend.kt` | 검색/목적지/모드/안내 분리, 기존 경로·약관 보호, 제한·생명주기 해제 |
| SOC·건강 | `vehicle/DriveCheck.kt`, `SocBackend.kt`, `PleosSocObserver.kt`, `LowSocStore.kt` | 읽기 품질·로컬 편의 기준, 초기 유효 수신 timeout·raw-first·시간 역순 방지 |
| validation | `IntegrationDiagnosticActivity.kt`, `MicrophoneProbe.kt`, `LocalAudioMetrics.kt` | 명시적 측정/중단·세대 차단·메모리 진폭 특성만 |
| 계측 | `DriveCheckP0Checks.kt`, `DriveCheckP1Checks.kt`, `DriveCheckNavigationLiveChecks.kt`, `DriveCheckSocLiveChecks.kt`, `DriveCheckMeasurementChecks.kt`, `DriveExperimentChecks.kt`, `MvpScenarioInstrumentation.kt` | 합성 backend와 실제 SDK 검사 분리·고유 fixture·prepare 이전 조기 분기 |

공통 분석 서비스/PortableAiClient/STT/TTS/Gleo3 Action·Callback·Firebase·Worker·키·서명·Gradle·Manifest는 변경하지 않았다. 위치/문제/관측/SOC의 기존 스키마와 버전도 바꾸지 않았다.

## 7. 빌드·검사·보존·게시

Debug/Validation/ValidationAndroidTest·LintDebug exit0. Lint0오류/56경고. UnitTest는 NO-SOURCE이며 테스트 성공 수에 넣지 않는다. 마지막 일반 코드 빌드는3m15s, 내비 시험 보완 뒤 AndroidTest를 별도 재빌드했다. 이번 **신규102+관련 회귀331=433 assertion**을 실제 실행했다. 과거298/313을 실행 없이 재사용하지 않았다. 시험 경로 복구 확인은 최종 NL07과 중복되어433에 추가하지 않았다.

| 검사 | assertion | 입력·환경 |
| --- | ---: | --- |
| P0 service26+위치 Main6+cold2+경로 Main7 | 41 | 격리 fixture backend·실제 View/SQLite/프로세스 |
| 실제 Navi 요청/부분 이벤트/timeout/owned fixture 정리 | 6 | 실제 SDK, 전체 경로 완료 성공은 아님 |
| P1 service24+SOC Main7+cold2 | 33 | 합성 SDK port·시간 전이·실제 View/SQLite |
| 실제 SOC Main10+cold3+복원2 | 15 | 공개 Console→실제 SDK18/45 callback·100 GET |
| 마이크 진폭·차단·중단 | 7 | 합성 PCM과 실제 장치 inventory; 마이크 시작0 |
| 기존 증상→AI View→TTS/차량·cold | 45 | 모델/STT fixture, 실제 TTS/Vehicle는 별도 검사 |
| 기존 STT 세션40·자동 UI6·초안 View21 | 67 | 합성 상태·실제 SDK 준비/취소 및 Main |
| 기존 issue AI56+cold4 | 60 | 로컬 fake provider/암호화 store/중복·한도·동의 |
| 기존 Gleo37·DB19·통합33·실제 SDK7 | 96 | 외부 Gleo 승인/실모델 성공 아님 |
| 기존 portable AI26+UI4 | 30 | fixture wire/pending·UI 계약 |
| 분석 계약24+운전 계산5+실제 위치 연결4 | 33 | 합성 분석·실제 Fused 위치와 sidecar는 분리 |

초기 fixture 운전자 이름 제한·위치 취소 UI·종료 직후 비동기 onStop 대기와 내비 시험 정리의 지연 응답 오류는 수정 후 재실행했다. 실패를 통과로 누락하지 않는다. 운영 앱 UI 자동화/기본 prepare/데이터 reset/AVD 재생성은0회다.

작업 시작에 일반42DB/원본1275행과 validation212DB/원본9535행을 새로 독립 백업했다. 최종 **기존254DB/원본10,810행 전체 multiset·스키마·user_version·integrity/FK 검사**를 통과했다. 일반 문제24/이벤트97/운전자10/초안0은 그대로이며 정상 SDK 진입의 관측/파생46행만 append됐다. 기존 DB를 수정·삭제해 시험을 준비하지 않았다. validation의 기존9535행은 추가/변경 없이 같고 고유 fixture76DB만 새로 만들었다. 실패했던 fixture도 기존 자료를 정리한다며 삭제하지 않았다.

일반 files/prefs16개 중 Firebase/동의/개인/AI 캐시 등15개 byte-identical, 설치 표시 `profileInstalled` 한 개만 정상 갱신됐다. validation은 기존132개 중128개 동일, Firebase SDK heartbeat·이번 검사 포인터2개·설치 표시만 갱신됐다. 인증 정체성·원래 AI 캐시를 교체하지 않았다. 백업/비교 결과와 개인 데이터는 로컬 비공개 `.tools`에만 있다.

기존 모델ON·Firebase 인증/Keystore·암호화 AI 캐시·동의·일5/월100/서비스 월300/비용 예약·pending/UNKNOWN 계약을 보존한다. 이번 모델 호출0·Worker 배포0·운영 정책 변경0이며 남은 사용량을 서버에서 새로 소비/조회하지 않았다. AI/Firestore 시험 응답은 격리 fixture이지 새 Gemini 성공이 아니다. 이전 실모델1회 성공은 별도 과거 근거다. 일반 앱에는 정비/Console/음성 시험 문제를 저장하지 않았다.

설치된 이전 일반 APK를 별도 백업하고 같은 서명인지 확인한 뒤 `install -r`로 교체했다. 새 Debug APK SHA256 `48f478ff99cd773e630ed1a9e1f246367105b9177c46e29ced76347aa77f445b`와 설치된 APK를 대조했다. MainActivity COLD/Status ok·resumed를 확인했으며 버튼 누름/문제 저장/운전자 변경 등 일반 UI 자동화는0이다. 같은 소스의 새 기능 흐름 검사는 validation에서 했다. Release 키/설정/SDK/기존 AVD/권한 상태를 초기화하지 않았다. 이번에 추가한 Console8088 forward는 복원 확인 뒤 제거하여 시작 시 설정으로 돌린다.

검증된 명시 파일만 **GitHub 연결 도구를 통한 게시** 대상으로 삼는다. 원격 main/대상 SHA를 다시 확인하고 최신 tree를 부모로 expected SHA/force=false를 사용한다. 원격 전용 연구26개와 다른 파일 SHA를 확인한다. 로컬 Git push·GCM 재설정·전체 업로드는 하지 않는다. DB/좌표/음성/개인 캡처/백업/APK/키/인증 파일/캐시·`.tools`는 게시하지 않는다.

## 8. 남은 확인과 기술적 이유

일반 앱에서 새 위치 연결과 새 DriveCheck 버튼의 사람 조작은 아직 E가 아니다. 이미 교체·실행한 같은 소스의 validation Main 검사는 C/D이며 이를 일반 실조작이나 실차 F로 확대하지 않는다. 정차한 일반 앱에서 본인 기존 기록을 열어 위치 연결을 선택하고, 저장 전 표시된 위치·시각을 확인하면 된다. 새 시험 문제 저장·새 Gemini 호출은 필요하지 않다.

내비 경로 탐색 완료 이벤트는 현재 서비스/앱 조합에서 빠진다. 숨겨진 API 호출·외부 경로 강제 변경·이벤트 합성으로 해결할 수 없다. 공식 SDK/내비 앱 제공 측의 해당 이벤트 동작 확인이 필요하며, 지금은 수신된 목적지/안내 상태와 누락된 완료를 그대로 보여주는 것이 안전한 구현이다. 일반 앱에서 본인이 확인한 정비 장소 좌표를 요청하는 시험은 현재 경로가 없는지 먼저 확인한 후 선택적으로 하면 된다. 실제 경로를 바꾸는 이 조작은 이번 자동 일반 UI 검사에서 실행하지 않았다.

급가감속은 연속 실제 속도/가속도 접근·측정 주기·관측 손실 처리가 검증돼야 한다. 마이크 PoC는 validation 진단 화면에서 선택적으로 직접 시작·중단해 확인할 수 있으나, 현재 정상/이상 근거자료 없이 고장 감지로 확장할 수 없다. 실제 센서·실차·물리 청취·접근성은 별도 검증이다. 외부 Gleo 승인은 계속 운영 측 확인이 필요하며 우회하지 않는다. 최종 전체 UI 개편·Release 서명/제작은 이번에 수행하지 않았다.

## 공식 1차 근거

- [공모전 개발 가이드](https://appdevchallenge.auto-hmg.io/guide/development), [Pleos Playground](https://developers.pleos.ai/playground), [공식 resources](https://developers.pleos.ai/playground/discover/resources).
- [NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [destinations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations), [이벤트 의미·앱별 차이·listener 해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions), [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release), [오류](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling).
- [Vehicle 공식 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app) main `959981d960ec71144accb27af5ae5d341e72a644`, [Navi 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app) `76ed592487a1eab7ba102b9d4b321705426886fe`, [Fused 샘플](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app) `c955c3a811371892e8803f3b1d9ad3f0426e832c`의 실제 Kotlin을 GitHub 연결 도구로 읽었다. 기존 Vehicle2.3.2/NaviHelper3.0.5/Fused1.1.3 AAR/sources와 대조했고 재설치하지 않았다.
