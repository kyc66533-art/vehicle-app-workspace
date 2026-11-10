# CARITDA 통합 구현·실행 검증

기준: GitHub main `e0517f3972b8043c6f13b448c412d23f2217682e`. 2026-11-10 Asia/Seoul. 기존 Android View·홈·3개 핵심 흐름을 유지하며 보조 기능을 연결했습니다. 아래 결과는 Pleos Connect v2.1.0 에뮬레이터이며 실차 검증이 아닙니다.

## 실제 구현

- Vehicle 2.3.2의 기존 SOC·거리·연료 단발 조회에 공개 읽기 17항목을 추가했습니다. 권한 → READ Capability → 영역 → 값 유효성 → 기존 ObservationStore 원본 저장 순서입니다. 추가 polling·제어·백그라운드 수집은 없습니다.
- 차량 정보의 접힌 보조 영역에서 문 위치·등화·방향지시·브레이크 페달·공개 차량 안내를 확인합니다. 홈은 재설계하지 않았습니다. 값/미지원/미수신/오류/이전값/오래됨을 구분합니다.
- 문제 작성에 명시적 음성 입력을 연결했습니다. 최종 인식 문장을 편집·확인하고 초안에 반영한 뒤 기존 저장 버튼을 눌러야 합니다. 자동 확정 저장은 없습니다.
- 문제 상세·정비 상담·이미 검증된 AI 설명에 사용자가 누르는 기기 내 TTS를 연결했습니다. AI 설명 듣기는 캐시/현재 표시문만 읽으며 새로운 Gemini 요청을 하지 않습니다.
- 문제 상세에서 사용자가 동의한 위치를 1회 가져와 좌표·측정 시각을 확인한 뒤 로컬 sidecar에 연결할 수 있습니다. 마지막 위치는 증상 발생 당시 위치로 확정하지 않습니다. 기존 문제 DB와 별도이며 AI 전송 payload에 포함하지 않습니다.
- 정비 상담의 길찾기는 사용자가 직접 확인한 장소·좌표를 입력하고 요청합니다. 기존 경로·약관 상태를 먼저 확인하고 기존 경로를 취소/덮어쓰지 않습니다. 연결 성공·요청 전송·경로 완료 callback은 구분합니다. 예약 기능은 없습니다.
- Gleo용 두 Action/Schema, 로컬 읽기 처리, 확인 화면, 음성 명령 문장의 선택적 초안 반영을 구현했습니다. 외부 Intent가 자동 읽음·기록 저장·Gemini 호출·상태 변경을 실행하지 않습니다. 신뢰할 Callback 수신 앱이 확인되지 않아 개인 기록을 implicit broadcast하지 않습니다.
- 속도 변화 계산과 센서 가용성 실험은 validation AndroidTest에만 있습니다. 일반 앱에 점수/위험도/고장 예측 메뉴를 넣지 않았습니다.

## 공식 SDK·API 및 의존성

| 대상 | 적용 | 확인한 근거와 경계 |
| --- | --- | --- |
| Vehicle | 2.3.2 유지 | 공개 AAR sources의 Door/Light/TurnSignal/Safety/Brake/Tire. CAR_DOORS/CAR_LIGHTS/CAR_SAFETY/CAR_DRIVE 읽기만 사용 |
| STT / TTS | 각 3.3.1 | 공개 AAR의 typed initialize/listener/request/speak. ON_DEVICE 사용. 서버 Client Secret·새 계정 불필요 |
| FusedLocation | 1.1.3 | 공개 sources의 isSupported/getLastKnownLocation/release. FUSED_LOCATION은 실제 기기에서도 dangerous. 사용자 허용 필요 |
| NaviHelper | 3.0.5 | 공개 sources의 initialize/getRouteStateInfo/requestRoute/listener. NAVI_ROUTE/NAVI_ROUTE_SEARCH는 기기에서도 normal |
| Gleo Interface | Intent·assets/schema.json | 별도 LLM SDK로 대체하지 않음. Schema 승인과 실제 Gleo Action/Callback 런타임 검증은 별도 |
| AAOS 속도·기어 | validation 전용 | 공개 CarPropertyManager/PERF_VEHICLE_SPEED/GEAR_SELECTION. SDK의 hidden Gear API는 호출하지 않음 |

공식 [Playground](https://developers.pleos.ai/playground), [SDK 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note), [Vehicle 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app), [Navi 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app), [Fused 샘플](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app)을 확인했습니다. 한국어 최신 릴리스와 실제 AAR 기준으로 버전을 정했습니다. 일부 영어 문서는 이전 버전을 안내합니다. 샘플의 mock location provider는 도입하지 않았습니다.

공식 [Gleo Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability), [승인·전달](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema), [Callback](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback), [AAOS 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds)을 기준으로 구현했습니다. `developers.hyundai.com` 본문은 도구 시간초과로 확인하지 못했습니다. 별도 Vehicle Data API의 지역/OAuth 고장 데이터를 내부 SDK 권한으로 대신 호출하지 않습니다.

### Maven 장애 수정

`ai.pleos.playground.security:universal-access:0.6.0`은 STT/TTS 3.3.1의 전이 의존성입니다. 기존 Maven content 필터가 `ai.pleos.playground`만 허용해 하위 그룹을 차단했습니다. 공식 저장소에 정확한 `.security` 그룹만 추가했습니다. SDK 버전/저장소 인증/Firebase/Gradle 전체를 변경하지 않았습니다. 사용자가 수정 후 Sync 성공을 확인했고 Codex에서도 일반·validation APK 빌드에 성공했습니다.

테스트 APK의 오래된 AndroidX compile 해석은 앱에 이미 선택된 annotation-experimental 1.3.1/lifecycle-common 2.6.1로 테스트 의존성을 맞췄습니다. 사용자 Android Studio 테스트 APK 빌드 성공과 이후 Codex 로컬 빌드를 구분해 기록합니다.

## 실제 SDK 실험

Android user 10, emulator-5554. 합성 앱 fixture와 별도의 고유 관측 DB를 사용했습니다. 실제 SDK 출처는 PLEOS_EMULATOR_SDK입니다.

| 신호 | 실제 결과 | 의미 |
| --- | --- | --- |
| SOC | 100 % | 조회 당시 SDK값, 고정 예시 아님 |
| 누적거리 | 0.0 km | 유효한 0, 미수신 아님 |
| 문 4개 위치 | 각 0 position | 차량 기준 위치값. 전 차종 공통 고장/닫힘 판정으로 일반화하지 않음 |
| 하향등 / 상향등 / 미등 | OFF / ON / ON | 논리 상태. 실제 전구·LED 정상 여부 아님 |
| 방향지시 | OFF | 실제 SDK 상태 |
| 브레이크 페달 | false | 페달 상태. 브레이크등 정상 여부 아님 |
| 출력 저하 안내 | false | 현재 해당 차량 안내가 없다는 수신 결과 |
| 연료, 엔진오일, 브레이크액, 워셔액 안내, 저압 기준 4개 | UNSUPPORTED | 8항목 모두 raw/unit/receivedAt를 만들지 않음 |

20개 요청 각각 종결 상태를 저장했고 12개 실제 값을 수신했습니다. 타이어 `getLowTirePressure`는 **임계 저압 기준 kPa**이며 실압/고장 경고가 아닙니다. 실압/Telltale 전체는 PRIVATE_CAR_SAFETY 경로이므로 선언·호출하지 않았습니다. 자동 고장 기록이나 경고를 합성하지 않았습니다.

NaviHelper는 실제 `onRouteStateInfo`에서 routing=false, terms=true를 받았습니다. 목적지 전달과 경로 완료는 이 읽기 시험으로 통과 처리하지 않습니다. Pleos ON_DEVICE TTS는 실제 onDone을 받았습니다. 물리 스피커 청취와 마이크 인식은 별도 사용자 시험입니다. FusedLocation은 최초 검사 시 사용자 권한이 없어 요청하지 않았습니다.

검증 전용 AAOS 실험에서 GEAR_SELECTION status=0/value=4를 읽었습니다. 기어값은 이동 증거가 아닙니다. 현재 속도 property는 가용 목록에서 확인되지 않았습니다. 원시 가속도 센서는 없었습니다. 원시 마이크를 자동 녹음하지 않았고 독립 고장 라벨이 없어 소음·진동 진단 성능은 산출하지 않습니다.

## 검증·데이터 보존

최신 검사와 제한은 아래 판정표와 STT 안정화 보고서를 기준으로 합니다. 과거 297개를 새 코드 전체 통과로 재사용하지 않습니다. 위험한 기존 prepare/default DB 초기화 경로는 실행하지 않습니다.

- 시작 전 일반·validation의 databases/shared_prefs/files 전체 로컬 tar 백업 완료. 일반 41개 DB, validation 147개 DB. 비공개 `.tools/issue-ai-backup/` 보관.
- 시작 시 일반 실제 DB는 문제 **24건**, 이벤트 **96건**, 운전자 **10명**, 초안 **0건**입니다. 과거 23건으로 고정하지 않았습니다.
- Gemini Secret·Worker·Firebase 프로젝트·모델 OFF·사용량/UNKNOWN 원장을 변경하지 않았습니다. 새 Gemini/유료 API/서버 배포 0회.
- 최초 신규 fixture 검사는 빈 DB에 legacy A/B가 있다고 가정해 실패했습니다. 등록한 영구 ID를 사용하도록 수정했습니다. 다음 실패는 시험 이름의 12자 제한이었으며 짧은 fixture 이름으로 수정했습니다.
- 기존 관측 검사의 ‘최근 3행이 SOC/거리/연료’ 가정은 추가 공개 신호로 깨졌습니다. 기본 3종을 명시적으로 조회하도록 수정했고, 새 coreDatabaseOnly는 SQLite fixture 부분만 실행하도록 분리했습니다. 최초 실행은 validation 선택 이름 변경과 실제 관측 추가까지 진행했으며 일반 앱에는 영향을 주지 않았습니다. 기존 validation 선택값도 최종 보존 검사에서 확인합니다.

## 사용 방법·검증 경계

1. 차량 정보 → 문·등화·차량 안내 확인: 실제 값과 미지원 구분.
2. 증상 남기기 → 음성으로 증상 입력: 마이크 허용 → 인식문 확인/수정 → 입력에 반영 → 기존 저장.
3. 문제 상세 → 이 기록 음성으로 듣기. AI 화면의 기존 설명 보기 후 음성 듣기는 모델 호출 없음.
4. 본인 기록 상세 → 현재 위치 함께 저장: 동의·권한 허용 → 측정 시각/좌표 확인 → 명시적 저장. 실패해도 원문 유지.
5. 정비 상담 → 정비 장소 길찾기: 확인한 장소·좌표를 입력. 이미 경로가 있으면 변경하지 않음.
6. Gleo 실제 발화는 Schema 심사·호환 런타임이 필요합니다. ADB Intent 실행은 Gleo 음성 인식 성공을 뜻하지 않습니다.

Release 서명 APK·PC B·실차·고령 사용자·TalkBack/Rotary·주행 중 복잡한 조작 검증은 로컬 Mock/콜백으로 대체하지 않습니다. APK/키/DB/개인 캡처/로그는 게시하지 않습니다. UI 팀은 MainActivity의 보조 액션 연결과 독립 integration 패키지, 기존 canEdit/canWrite·초안/이력·AI 전송 계약을 유지해야 합니다.

## 최종 상태 — 2026-11-11 02:26:21 +09:00 (Asia/Seoul)

| 기능 | 판정 | 실제 근거·제한 |
| --- | --- | --- |
| Vehicle20종 종결/12종 실수신 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 실제 SDK·고유 관측 DB, 미지원8개 분리 |
| 기존 기록·인계/AI 캐시 계약 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 안전한 기존 회귀부분219개, 신규 모델 호출0 |
| 한국어 자동 STT·연속 입력 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 공식 request/ON_DEVICE, 사용자 실제3회와 SDK final/ended 확인 |
| 자동/취소 최종 UI | IMPLEMENTED_AND_RUNTIME_VERIFIED | 합성40개·실제 SDK 준비/취소/재초기화 View6개; 최종 레이아웃 후 발화는 별도 미수행 |
| 직접 PCM | validation 전용 | 정상 공개 영어3/3, 실제 사용자 마이크와 별도 |
| TTS | IMPLEMENTED_RUNTIME_UNVERIFIED | 실제 onDone 수신, 물리 청취 별도 |
| 위치1회 | IMPLEMENTED_RUNTIME_UNVERIFIED | SDK valid metadata, 실제 사용자 문제 연결은 미수행 |
| Navi 길찾기 | IMPLEMENTED_RUNTIME_UNVERIFIED | 경로 상태 callback만 확인, 목적지 전달/경로 완료 미검증 |
| 외부 Gleo | BLOCKED_EXTERNAL_DEPENDENCY | 내부 Action/Schema만 구현, 승인·실제 호출 없음 |
| 속도·소음/진동 진단 | UNSUPPORTED_BY_AVAILABLE_API | 현재 실속도 property/가속도·고장 정답 없음, 독립 실험만 유지 |

기존219 + 통합33/실제SDK7/독립실험5/최종 합성STT40/실제 캡처경계3/실제 SDK·취소UI6 =313개 assertion 통과. 이전35개는 최종40개로 대체해 중복 집계하지 않았습니다. 실제 발화3회·공개 PCM3회·독립 request/종료순서 진단은 별도입니다. 과거297개 전체를 기본 DB 초기화 경로로 재실행하지 않았습니다.

최종 Debug/validation/AndroidTest/Lint 성공, unit NO-SOURCE. Debug APK는 NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk, TEST는 outputs/apk/validation/app-validation.apk, 계측은 outputs/apk/androidTest/validation/app-validation-androidTest.apk입니다. Release/PC B/실차는 미검증입니다.

최종 일반41DB/validation163DB 백업에서 시작188개 기존DB schema/version/원본행/integrity/FK를 확인했습니다. 일반24/96/10/0·원본1106행 불변, validation 원본8938행 보존. 추가119행은 앞선 실제 관측과 고유 fixture이며 일반 시험 기록은0개입니다. 일반 files/shared_prefs15개·인증·AI 암호화 결과 바이트 동일. 기존 validation 선택A도 보존했습니다. 일반 앱 기존 설치 유지, TEST만 교체했습니다. Gemini/Worker/Secret/예산/모델OFF·Firebase 설정은 변경하지 않았습니다.

호스트 오디오 전달 문제를 -allow-host-audio 명시 재실행으로 복구했습니다. 수동 stop의 SDK 콜백 누락은 성공으로 바꾸지 않았으며 사용자 최종 지시에 따라 수동 완료 UI를 제거하고 자동 종료/명시적 취소만 제공합니다. 요청 경로의 수동 EOF 실험도 사용자 코드에서 제거했습니다. [실제 STT 결과](STT_STABILITY.md)·[PC별 실행 안내](STT_RUN_GUIDE.md)를 따릅니다.

다음 단계는 음성 확인→문제 초안→저장→운전자 인계의 실제 통합 검증과 승인된 Gleo 외부 Action/Callback입니다. 새 유료 모델/서버 배포 없이 진행하며 인식문을 임의 저장·자동 해결하지 않습니다. UI 팀은 integration의 독립 컴포넌트·canEdit/canWrite·초안/이력/AI 최소 전송 계약을 유지해야 합니다. 검증된 파일만 GitHub 연결 도구로 게시하며 DB/키/음성/개인 캡처/로그/Aside는 제외합니다. 최종 커밋은 완료 응답에서 보고합니다.
