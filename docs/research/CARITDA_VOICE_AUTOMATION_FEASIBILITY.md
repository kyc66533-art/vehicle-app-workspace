# CARITDA 자동화 기술 실현 가능성 보고서

조사일: 2026-11-12 (Asia/Seoul). 기준 main: [c2757596397f96c3fe2fe45927dd025895ec13b3](https://github.com/kyc66533-art/vehicle-app-workspace/commit/c2757596397f96c3fe2fe45927dd025895ec13b3).

## 1. 결론과 완료 주장 경계

**Gleo 없이도 사용자 시작·앱 가시·CarSafetyGate ALLOWED 범위의 반이중 음성 비서와 능동 안내는 개발할 수 있다. 현재 완성돼 있다는 뜻은 아니다.**

기존 STT/기록/단일 문제 AI/TTS/차량 안내를 재사용하되, 세션 조정기·원자적 자동 기록·대화 이력·오디오 포커스·알림 전달 원장이 빠져 있다. 진짜 생성형 후속 대화에는 현재 단일 기록 계약을 확장한 별도 AI 계약/서버 검증이 필요하다. 앱 종료 중 상시 차량 감시·시스템 호출어·주행 중 계속 대화는 이번 일반 APK에서 보장할 수 없다.

이 보고서는 코드/공식 자료/공개 바이너리의 정적 조사다. 이번 조사에서 APK 빌드·에뮬레이터 조작·일반 앱 조작·실모델 요청·Cloudflare 배포·운영 설정 변경은 하지 않았다. 과거 검증은 해당 보고 당시 결과로만 표시한다. 기존 Android/Worker/DB/기존 연구 파일을 수정하지 않았다.

함께 읽기: [구현 아키텍처](CARITDA_VOICE_AUTOMATION_ARCHITECTURE.md), [Codex 실행 지시서](CARITDA_AUTOMATION_CODEX_PLAN.md).

## 2. 근거 표기

- D: 이번 직접 열람한 공식 문서. A: 이번 확보한 공식 AAR/public 정의·sources. S: 공식 샘플 실제 코드.
- C: 기준 main 실제 소스. H: 기존 문서가 보고한 과거 실제/fixture 결과. P: 이번 설계 제안. E: 외부 환경/권한 의존.
- H는 이번 재실행이 아니고, public 메서드 존재는 권한·서비스 지원·실차 성공의 증거가 아니다.

### 주요 공식 출처

| ID | 직접 열람 URL | 핵심 근거 |
|---|---|---|
| D01 | https://developers.pleos.ai/playground | SDK/설계/Gleo 공식 자료 포털 |
| D02 | https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts | Gleo function calling, Schema/Action/Callback |
| D03 | https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability | assets/schema.json, 최대3개 actionCalls, 제한된 parameter 형식 |
| D04 | https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema | Schema 검토 후 활성화 설명, 설치/부팅시 스캔. 준비=활성화 완료 아님 |
| D05 | https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback | success/asking/fail, 영어 message, voice/beep/silent, 7초 timeout |
| D06 | https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples | Intent/action/APP_ACTION_RESPONSE 샘플. 수신자 신원 보장 계약은 별도 |
| D07 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro | v3.3.1, ON_DEVICE는 서버등록 불필요, request/stop/listener/release |
| D08 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro | v3.3.1, ON_DEVICE/서버모드 구분, 합성·재생 |
| D09 | https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide | 실제 CarUxRestrictions callback 기준 기능 제한 |
| D10 | https://developer.android.com/training/cars/platforms/automotive-os | OEM별 UX 제한, 시스템바/DO/특징. Google Play 배포규칙과 Pleos 심사 구분 |
| D11 | https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy | DO 미선언 주행 사용 불가, 지역·카테고리 차이 |
| D12 | https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development | 재생전 focus 요청, loss 처리, 통화/긴급음/Gleo와의 충돌 |
| D13 | https://developer.android.com/reference/android/speech/SpeechRecognizer | 일반/ON_DEVICE 가용성, RECORD_AUDIO, 연속 인식용 아님 |
| D14 | https://developer.android.com/reference/android/speech/RecognizerIntent | 외부 Activity·미설치 가능, PREFER_OFFLINE은 보장 아님 |
| D15 | https://developer.android.com/media/platform/sharing-audio-input | 입력 경합/무음, 일반 앱과 사전설치 Assistant의 차이 |
| D16 | https://developer.android.com/reference/android/app/role/RoleManager | role available/held/사용자 요청 |
| D17 | https://developer.android.com/reference/android/service/voice/VoiceInteractionService | 사용자가 선택한 서비스, 시스템 bind 보호 선언 |
| D18 | https://source.android.com/docs/automotive/voice/voice_interaction_guide/app_development | VIA/PTT/hotword, bundled privileged 경로 구분 |
| D19 | https://source.android.com/docs/automotive/voice/voice_interaction_guide/integration_flows | AAOS assistant 선택 흐름. Pleos 구현 제공 보장 아님 |
| D20 | https://developer.android.com/develop/background-work/services/fgs/service-types#microphone | API34 microphone FGS type/permission/while-in-use |
| D21 | https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start | background 시작 제한·예외 |
| D22 | https://developer.android.com/training/cars/platforms/automotive-os/notifications | AAOS HUN/소리·일반앱 카테고리·특권 차량알림 구분 |
| D23 | https://developer.android.com/develop/ui/views/notifications/notification-permission | API33 POST_NOTIFICATIONS, FGS 시작권한과 다른 의미 |
| D24 | https://developer.android.com/develop/ui/views/notifications/channels | channel 생성/importance |
| D25 | https://developer.android.com/reference/android/media/AudioFocusRequest | grant/loss, speech/exclusive focus |
| D26 | https://developer.android.com/about/versions/15/behavior-changes-15#audio-focus | target35의 top-app/FGS focus 조건. 현 target34에 소급하지 않음 |
| D27 | https://ai.google.dev/gemini-api/docs/generate-content/api-errors 및 https://ai.google.dev/gemini-api/docs/troubleshooting | 공식 검색 본문: 503 UNAVAILABLE는 일시 과부하/중단, bounded backoff·모델 변경 검토. 웹 추출이 빈 본문이어서 검색의 공식 본문으로 확인. 앱의 예산/idempotency 정책을 대체하지 않음 |

## 3. 운영사 답변과 Gleo의 정확한 위치

사용자 제공 운영사 답변은 이번 공모전의 별도 신청/활성화·검증환경·Callback 수신자/인증 지원을 제공하지 않는다는 것이다. 답변 원문을 별도 계정에서 재조회하지는 않았다.

- Gleo 사용 금지나 이미 승인 완료라고 해석하지 않는다.
- D04의 일반 제품 Schema 검토/활성화 설명과 이번 공모전 별도 절차 부재는 다른 사실이다. 참가자가 받을 수 없는 '승인 신청'을 필수 다음 행동으로 반복 요구하지 않는다.
- 기존 3 Action·Schema·Callback envelope/trust gate는 유지한다. 검증할 수 없는 패키지/서명 pin을 추측해 채우지 않는다.
- Gleo 실호출은 E. 독립 앱 대화는 별도 D07/D08 경로로 진행한다. ADB/fixture Action 호출과 앱 자체 TTS는 Gleo 자연어/음성 성공이 아니다.
- 대체 시연은 운영사 답변상 가능하나 실제와 fixture/미검증을 명시한다. 심사점수·선정 보장은 하지 않는다.

## 4. 기능별 실현 가능성 표

등급은 '지금 구현 가능 / 제한적으로 가능 / 외부 환경·권한 필요 / 현재 구현 불가'다. '가능'은 개발 가능성으로, 이미 end-to-end 완료라는 뜻이 아니다.

| 기능 | 공식/현재 구현 근거 | 개발 가능성 | 실행 조건·남은 작업 | 검증 방법 |
|---|---|---|---|---|
| 앱 내부 한 번 시작한 음성 세션 | D07/08,A,C SpeechInput/Output | 지금 구현 가능 | visible+ALLOWED, mic 허용, 명시 시작. 매 턴 자동 재청취는 새 조정기 필요 | 실제 STT→TTS→STT 3턴, 녹음 겹침0 |
| 시스템 어디서나 '카잇다' 호출 | D16~19, privileged hotword | 외부 환경·권한 필요 | Pleos 역할/PTT/키프레이즈 모델·OEM 가용성 미확인 | 실제 role/UI/PTT/hotword 승인경로가 있을 때만 |
| STT 발화 안의 '카잇다' 접두사 | D07,A request | 지금 구현 가능 | 이미 앱이 듣는 동안의 텍스트 파싱. 앱을 깨우는 기능 아님 | 앱이 닫힌 때 반응 안 함을 명시 |
| 앱 자체 키워드 엔진 상시마이크 | D15/20/21 | 제한적으로 가능 | foreground/FGS/전력/경합/UXR 조건, 현 visible gate에서는 background 금지 | 별도 실험범위, 1차 제외 |
| 특권탈취/서명우회로 wakeword | 공식 허용 경로 아님 | 현재 구현 불가 | 대안에서 제외 | 우회 구현0 |
| STT 최종·종료 결합 | D07,A,C | 지금 구현 가능 | 기존 final+ended/취소 generation 재사용. 800자 절단 수정 필요 | callback 순서/중복/오류/늦은결과 |
| 명확한 증상 자동 저장 | C CaritdaService/SqliteIssueRepository,P | 제한적으로 가능 | 명시 기록지시 또는 opt-in 증상자동화모드, 확정운전자, atomic token/payload 검사 필요 | issue/CREATED 각1, ambiguity때 쓰기0 |
| 저장 후 AI 자동 요청 | C IssueAnalysisService/Client,P | 제한적으로 가능 | 저장확정→동의범위/전송사본검증→최초1요청. 기존 내용확인 규칙을 일반 opt-in만으로 우회하지 않음 | fixture 모델1, 실제유료는별도승인 |
| AI 결과 자동 TTS | A/C per-piece completed 있음 | 지금 구현 가능 | aggregate typed 완료·focus·현재 세션검사 추가 | 전체문장/완료/중단/실패·중복재생0 |
| 로컬 차량·최근기록·인계 질문 | C CaritdaActions/DriveCheck | 지금 구현 가능 | 같은 로컬 query handler 재사용, 실제값/시각/미확인구분 | Gemini0, 조회가읽음/상태쓰지않음 |
| 같은 문제의 기존 AI 설명 재사용 | C cache/IssueAnalysisService | 지금 구현 가능 | 같은issue/revision/계정·동의, SAVED provenance. 새분석이라고 표시금지 | 원장변화0·로컬cache만 |
| 진짜 생성형 후속 질문 | 기존계약은 단일기록, P 신규계약 | 제한적으로 가능 | typed conversation projector·서버contract/검증/예산/transport path 추가. 현 situation에 대화내역위장 금지 | 질문참조·선행turn해시/지연응답/기존계약회귀 |
| 대화·재생 이력 자동 보관 | C 기존journal/AIcache, P sidecar | 지금 구현 가능 | 별도SQLite·암호화·idempotent seq·삭제/공유정책 | crash 각경계·복원·계정/운전자분리 |
| 실제 SOC 변화·중복 안내 | A/C PleosSocObserver+LowSocStore,H | 지금 구현 가능 | 이미 가시/ALLOWED opt-in callback·중복억제 있음. 자동TTS/대화연결 추가 | Console18→18→45 실제callback |
| 워셔/출력저하/오일/브레이크액 변화 | A public register,C getter | 제한적으로 가능 | 네 구독기능 추가·신호별capability/권한. 대부분 이 AVD 미지원 | source fixture와 실제callback 별도 |
| 홈 현재 안내 자동 갱신 | C VehicleAttention/Main,H | 지금 구현 가능 | 이미 있음. 새 observer 입력과 durable episode/delivery만 연결 | 정상/실패/오래됨·큐중복 |
| 차량안내→TTS→후속대화 | D12,A,C+P | 제한적으로 가능 | 알림음성·자동재청취 별도opt-in, focusgrant·eligible세션, alertId고정 | episode1회음성·같은알림문맥 |
| Android 알림 게시 | D22~24 | 제한적으로 가능 | 현재 Manifest에 POST_NOTIFICATIONS/service없음. 채널·권한·정책 필요 | 실제drawer/HUN/소리 각각검증 |
| 일반 차량알림의 HUN/소리 보장 | D22 | 외부 환경·권한 필요 | 앱 일반경고는 CALL/MESSAGE/NAVIGATION으로 위장불가. 특권CAR_WARNING도 일반권한아님 | OEM허용·실기확인 전 보장금지 |
| 앱종료/주행제한 중 상시감시·대화 | C gate/onStop,D09~11/20 | 현재 구현 불가 | 현설계의 visible-only/fail-closed 조건으로는 불가. 다른공식서비스/정책이 확보돼야별도설계 | FGS로gate우회0 |
| 503 이후 로컬 기능 지속 | C 실패/캐시경계,P controller | 지금 구현 가능 | 기록보존·실패안내·로컬조회/기존설명. 자동fresh retry는현재없음 | provider503/relay503/UNKNOWN 구분 |

## 5. 현재 구성요소의 구현·검증 단계

K=NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/. H 문서 수치는 당시 기록이며 새 검사수로 합산하지 않는다.

| 구성요소 | 실제 코드 | 자동검사/에뮬레이터/정상앱 근거 | 외부·추가 범위 |
|---|---|---|---|
| Vehicle2.3.2/PleosReadClient/Health | 실제20종 단발조회·권한·capability·원관측 | H CARITDA_INTEGRATION_REPORT:170-185, 12수신/8미지원. 이후Drive/B UI 재검증 보고 | 실차 없음. 모든신호 연속구독 아님 |
| PleosSocObserver/LowSocStore | opt-in callback·GET·15초최초수신timeout·generation·구간사건 | H DRIVE_CHECK_ACCEPTANCE:43-49 18callback/18GET/45callback·한사건·A저장/B읽음 | 자동TTS 없음. 종료감시 아님 |
| DriveCheck/VehicleAttention | CURRENT/STALE/UNSUPPORTED등·네SDK안내/SOC사용자기준·홈 | H B_LIGHT_UI_ACCEPTANCE/446·1600조합, SDK18홈/45제거. 일반교체/초기화면 확인 | 일반9화면 전체사람조작/실차 미검증 |
| STT3.3.1 | ON_DEVICE/request, final+ended, 늦은결과/취소보호 | H STT_STABILITY/통합보고의 실제한국어 및 fixture. 일반초안반영 사용자확인 | 연속대화·자동저장 없음 |
| TTS3.3.1/ExplanationPlayback | 전체결과100자전후분할·piece완료/취소 | H B_LIGHT_UI_ACCEPTANCE 2연속실제전체완료·취소/실패회귀 | 자동분석후자동재생·aggregate typed완료 없음 |
| IssueAnalysisService/Client/Store | 단일저장문제·frozen/pending·암호화·quota | H 실제일반신규Gemini1회+한국어TTS 사용자확인은 GEMINI_INDEPENDENT_ACCEPTANCE. 최신UI는fixture경계와구분 | 503이력은 사용자제공. 이번실호출0, 최신운영오류원장새조회안함 |
| Firebase/Relay | 인증·기기등록·동의·budget/operation ledger | C 직접소스대조, H 보존보고 | 인증=기록동기화아님, dialogue계약없음 |
| Gleo3 Action/ReplyChannel | Schema/dispatcher/envelope/신뢰차단·6초내부예산 | H Gleo37 fixture/Activity회귀, actualGleo음성아님 | 실제수신자/서명/라우팅 미검증. 운영사별도지원없음 |
| Fused1.1.3/Navi3.0.5 | 명시위치동의/last-known·기존경로보호 | H Fused실제수신, Navi목적지/상태수신. onRoutingCompleted없어부분성공 | 자동전송/지속위치수집 추가안함 |
| SQLite/운전자/인계 | 영구ID/작성당시이름/4수동상태/논리삭제/초안 | H 최신원본보존/교체, validation실제SQLite+fixture흐름 | 음성자동commit·대화sidecar는새개발 |

## 6. 공식 AAR·샘플과 실제 대조

### 6.1 이번 확보한 SDK 바이너리

고정좌표는 현재 app/build.gradle.kts와 settings.gradle.kts의 공식 Maven을 사용했다. 아래파일 모두 HTTP200으로 확보해 public class 서명을 검사했다. 설치·SDK변경은 하지 않았다.

| AAR URL | SHA256 |
|---|---|
| https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar | E11FDFE498CAD3ECC0D5A2A6992B0C4A4D135195F1455018127BC069FD5EF937 |
| https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/SpeechToText/3.3.1/SpeechToText-3.3.1.aar | 35109AD13400A7FA103CCA9FABBF9B785E63A6A98B17A0698A1AEF51458D37BF |
| https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/TextToSpeech/3.3.1/TextToSpeech-3.3.1.aar | BFBFB58376C69604AFAD3389878980461971683F0C51F619D03AB74B5E6A0375 |

검사 방법: class-file public descriptor 읽기와 기존 Android Studio JBR javap. JVM 서명 출력 뒤 sandbox의 JAR close AccessDenied가 발생해 clean exit 성공으로 기록하지 않는다. public 서명은 독립 class parser와도 일치했다. AAR 실행/설치는 하지 않았다.

- STT SpeechToText(Context,Mode), initialize(), addListener/removeListener(ResultListener), setLocale(Locale), request(), stop(), release(). ResultListener.onUpdated(String,boolean), onEndedRecognition(), onError(ErrorType).
- TTS TextToSpeech(Context,Mode), setAudioAttributes(AudioAttributes), speak(String,boolean), add/removeEventListener, stop(), release(). EventListener.onDone/onStop/onError. SDK callback에는 requestId/utteranceId 인자가 없어 adapter별 generation과 listener identity로 대조한다.
- AAR에 getAmbientAi/CallApi 등이 보여도 일반 앱에 허용된 공개 기능이라는 뜻은 아니다. 실행 후보는 공식 문서가 지원하는 API로만 제한한다.

Vehicle의 공식 소스 정의도 https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar (HTTP200)로 대조했다. Safety.kt의 엔진오일/브레이크액/워셔/출력저하는 CAR_SAFETY normal, Tire.kt의 실제 압력은 PRIVATE_CAR_SAFETY signature|privileged다. 소스 jar의 SHA는 별도로 산출하지 않았으며 위 해시는 AAR이다.

### 6.2 공식 샘플 실제 파일

- Vehicle: https://github.com/Pleos-Playground/pleos-sample-vehicle-app . pinned 959981d960ec71144accb27af5ae5d341e72a644의 app/src/main/java/ai/pleos/playground/sample/vehicle/vehicle/VehicleClient.kt 및 explorer/PublicApiPolicy.kt. 실제타이어압력/Telltales·특권경로를차단. 샘플2.3.1을현재2.3.2로잘못보고하거나앱을downgrade하지않음.
- Navi: https://github.com/Pleos-Playground/pleos-sample-navi-app/blob/main/app/src/main/java/ai/pleos/playground/navidevapp/screen/vm/NaviHelperViewModel.kt . initialize/addListener/removeListener/release, requestRoute, onRouteStarted와onRoutingCompleted가서로다른이벤트임을직접확인.
- Fused: https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app/blob/main/app/src/main/java/ai/pleos/playground/location/fused/sample/location/FusedLocationClient.kt . isSupported, initialize, register/unregisterFusedLocationCallback, getLastKnownLocation, release확인. mock provider/예제로그를운영앱에가져오지않음.

## 7. 신호별 공개 지원·현재 구현·검증을 분리한 표

모든구독은 initialize→권한→capability→register→실제유효callback→unregister/release 순으로검증한다. 최초GET callback과지속subscription callback도 구분한다. 아래GET/register는Vehicle2.3.2 public인터페이스에서확인했으며 Safety 권한은공식sources.jar도대조했다.

| 신호/실제의미 | GET / subscription | 권한·현재코드 | H 에뮬레이터 실제값·변화 | 자동화 판정 / 실차 |
|---|---|---|---|---|
| 구동배터리SOC% (12V전압/고장아님) | evBattery.getEvBatteryStateOfCharge / registerEvBatteryStateOfCharge(EvBatteryStateOfChargeListener) | CAR_ENERGY, 단발+기존SocObserver | 100;18/45 callback변화확인 | 가장먼저재사용. 실차미검증 |
| 연료잔량% | fuel.getFuelPercent / registerFuelPercent(FuelPercentListener) | CAR_ENERGY, 현재GET만 | UNSUPPORTED | 실차지원차종에서별도구독검증. 0%로대체금지 |
| 누적거리 | odometer.getCurrentDistanceDriven / registerDistanceDriven(DistanceDrivenListener) | CAR_DRIVE, 현재GET/분석 | 0.0km 수신. 일반거리변화callback미검증 | 고장경고가아님. 실차미검증 |
| 워셔액부족 boolean | safety.hasLowWasherFluidWarning / registerLowWasherFluidWaringState(LowWasherFluidWaringListener) | CAR_SAFETY normal, 현재GET | UNSUPPORTED | 새구독구현가능, 실제경고/변화미검증 |
| 출력저하 boolean | safety.hasPowerDownWarningIndicator / registerPowerDownWarningIndicator(PowerDownWarningIndicatorListener) | CAR_SAFETY normal, 현재GET | false 실제수신, true전환/callback미검증 | false=차량전체정상아님 |
| 브레이크액 수위 enum | safety.getBrakeFluidLevelState / registerBrakeFluidLevelState(OilLevelStateListener) | CAR_SAFETY normal, 현재GET | UNSUPPORTED | LOW/HIGH/CRITICALLY_LOW는수위안내. 제동고장확정아님 |
| 엔진오일 수위 enum | safety.getEngineOilLevelState / registerEngineOilLevelState(OilLevelStateListener) | CAR_SAFETY normal, 현재GET | UNSUPPORTED | 오일압력/교환주기/엔진손상추정금지 |
| 타이어저압 기준 kPa | tire.getLowTirePressure(WheelArea,...) | CAR_SAFETY public, 현재4바퀴GET | 4개UNSUPPORTED | 기준값은실제압력/펑크/저압상태아님. 자동경고근거로사용금지 |
| 실제타이어압력 | tire.getTirePressure/registerTirePressure | PRIVATE_CAR_SAFETY signature/privileged, sample차단 | 운영미호출·미검증 | 일반APK범위현재불가/공식권한외부필요 |
| 문위치/등화/페달 | Door/Light/TurnSignal/Brake 개별GET/listener | 현재공개17health항목중일부GET | 문4개0, 하향OFF/상향ON/미등ON/방향OFF/페달false | 상태사실만. 등화ON/페달=true를고장으로사용금지 |
| Telltales/추가수위 | AAR에Safety API존재 | 공개샘플Telltales차단, 각권한·공개지원재확인필요 | 현재CARITDA수신근거없음 | 메서드존재만으로지원/경고확정금지 |

AAR에서 Washer API의 Waring 철자는 실제정의 그대로다. 임의로 Warning으로수정하면컴파일실패할수있다. OilLevelStateListener.onOilLevelUpdated(OilLevelState), PowerDownWarningIndicatorListener.onPowerDownWarningIndicatorUpdated(Boolean?), LowWasherFluidWaringListener.onLowWasherFluidWaringUpdated(Boolean?)의null은정상으로판정하지않는다.

### 주기적조회

공개GET·callback은확인했지만 임의Hz·백그라운드무한polling 허용/보장 문서는확인하지못했다. 우선callback, 가시/허용진입한번GET·명시재시도 유지. 필요한fallback polling은서비스별정책/실기부하를확인한뒤opt-in·bounded·backoff·중단조건을정해야하며이번1차설계는추가하지않는다. WorkManager/FGS로실시간차량감시가자동가능해지는것도아니다.

## 8. 독립 음성과 시스템 비서의 차이

STT문서의 '음성명령기능제공안함'은 STT가텍스트를반환할뿐앱동작을실행하지않는다는뜻이다. CARITDA가자기앱텍스트명령을안전하게해석하는것까지금지됐다고읽지않는다.

1. **권고경로:** 앱가시상태에서사용자가한번시작→유한STT→해석/기록/AI→마이크닫힌TTS→현재세션내후속STT. 매번버튼은없지만상시마이크도아님.
2. **기본비서역할:** isRoleAvailable/isRoleHeld, 자격있는VoiceInteractionService또는ACTION_ASSIST, 사용자역할선택이필요. BIND_VOICE_INTERACTION은시스템bind를보호하는서비스선언이지이선언만으로APK플랫폼서명이반드시필요하다는뜻은아니다. Pleos역할선택/PTT제공은미확인.
3. **시스템hotword:** DSP/키프레이즈등록/모델·언어·OEM특권경로가필요. AlwaysOnHotwordDetector/SystemApi·CAPTURE_AUDIO_HOTWORD·SoundTrigger를일반권한처럼강제사용하지않는다. 관련AOSP: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/service/voice/AlwaysOnHotwordDetector.java , https://source.android.com/docs/core/audio/sound-trigger .
4. **자체키워드엔진:** 일반AudioRecord에알고리즘을붙일수는있으나전력·입력경합·FGS/UXR제약이있는별도녹음이며시스템전역저전력호출어와다르다. '카잇다'라는문자열이최종STT에있다고시스템wakeword가작동했다고보고하지않는다.

## 9. 주행·오디오·알림·백그라운드

- 현재Manifest는DO미선언, gate는비차량/연결미확인/UXR제한을허용하지않는다. 정차ALLOWED에서만새자동화동작. 음성이라고주행금지를우회하지않는다. 주행음성지역허용표도앱실행승인·기능별정책확보없이직접허가로사용하지않음.
- SDK TTS가차량오디오를재생해도앱의focus처리가자동보장되는것은아니다. 현재main Kotlin/XML에명시적focus/attributes/zone처리가확인되지않음. 출력전요청+grant, loss시즉시중단·재청취금지. EMERGENCY/SAFETY를자체경고에사칭하지않음. 실제Gleo/통화/내비의정책을따른다.
- 출력focus는마이크소유권이아니다. 통화/다른녹음/정책상무음은별도감지. 일반앱두개가동시에실오디오를받는다고가정하지않음.
- Android알림은현재추가구현. POST_NOTIFICATIONS와channel필요, 거부돼도인앱저장/표시는유지. AAOS 일반경고의HUN·소리는보장안됨. CALL/MESSAGE/NAVIGATION/특권CAR_WARNING으로위장하지않음.
- target34 microphone FGS에는FOREGROUND_SERVICE/FOREGROUND_SERVICE_MICROPHONE/type/mic권한·허용된시작조건이필요. background permission GRANTED표시만으로while-in-use제약을넘지못한다. 알림권한거부가FGS시작자체금지라는뜻은아니다. 이번에는FGS/BOOT감시를추가하지않는다.
- 앱onStop/안전제한/운전자변경/권한회수/종료명령/focusloss에서세션중단. 자동마이크재시작금지,기록/operation상태는보존. 프로세스복원후사용자재개전음성0.

## 10. 503·불확실 요청과 모델 대안

현재소스의정확한차이(C):
- provider의HTTP503 → portable-issue.ts가 ApiError(502,MODEL_HTTP_503). portable-gate는TIMEOUT/NETWORK/OUTCOME_UNKNOWN만UNKNOWN으로분류하므로이503는현재 **FAILED**. 이미예약/소모된사용량을임의환급하지않는다.
- Relay자체503/앱transport네트워크실패는pending이남을수있다. 같은operation을확인하고새operation을자동발급하지않는다.
- operation READ는RESERVED일때기존예약의최초dispatch를실행할수있다. 따라서결과조회가언제나모델0이라고말하면안된다. DISPATCHING/UNKNOWN을새provider호출로중복dispatch하지않는정책을유지한다. 순수무모델은local saved cache/로컬조회다.
- 확정실패새재시도는새operation/새할당량일수있다. 공식Gemini bounded backoff권고를클라이언트루프에바로붙이면중복비용/기존정책위반이될수있다.

권고(P): 저장성공과AI성공을분리. 실패에도issue/대화유지→로컬현재값/과거SAVED설명(출처표시)→세션유지. 재시도는현재동의/잔여/상태확인후명시음성승인또는사전에구체적으로승인한최대시도/비용안에서만. 1차기본은자동fresh재시도0. UNKNOWN은조회/복구만,새모델fallback금지. 기존모델과다른대체모델은서버allowlist/응답schema/품질/가격/예산예약/동의가일치할때만별도승인·구현·검증한다. 이번모델변경/실요청/배포0.

## 11. 최소 현실적 개발 경로

1. 앱내사용자시작+로컬질문·유한후속청취+이력부터;기존단발음성회귀.
2. 자동기록opt-in/명확한명령+동일turn원자저장→기존단일기록AI요청→자동TTS. 모델은fixture로연결시험.
3. 기존SOC사건→durable음성안내→동일alert문맥의로컬질문→명시기록연결.
4. 공개Safety4종subscription을개별capability검증하며추가. 미지원은미지원으로두기.
5. 생성형후속대화용새typed계약/동의/서버검증과503회귀를별도개발. 배포/유료E2E는별도허용뒤만.
6. 공식Gleo어댑터는보존하되차단상태의외부지원이전체개발을막지않게한다.

정확한파일·메서드·상태/DB/흐름/실패매트릭스는ARCHITECTURE, 13단계구현·A~E시험·복사프롬프트는CODEX_PLAN에있다.
