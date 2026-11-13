# STT 자동 종료·취소 안정화

## 2026-11-12 23:38 +09:00 — 실제 수동 입력 복구와 홈 호출 확인

기준 main `7f913a466490664171706029a3235885558a7584`. 원격330blob/로컬 공통301파일 일치·원격 전용 연구29개 보존. 이번 실제 사용자 발화로 **수동 A → 홈 B → 수동 A**를 확인했다. 아래 과거 성공과 합산하지 않는다. 이번 Gemini 요청·Worker 배포0회.

### 환경과 직접 원인

| 확인 대상 | 실제 결과 | 경계 |
| --- | --- | --- |
| Windows 일반 사용자 PCM | 기본 입력252800bytes/RMS0.016347/peak7682, 통신용124800bytes/RMS0.017216/peak6597. open/start/close 성공·error0 | CodexSandboxOffline의 OPEN error1은 별도 계정의 접근 실패. 기본 물리 endpoint와 친숙한 장치명의 대응은 미확정 |
| 접근·라우팅 | Windows 사용자/NonPackaged/시스템 마이크 Allow. Android RECORD_AUDIO foreground/allow, microphoneMuted=false, isClientSilenced=false, MIC/VOICE_RECOGNITION routed type15 | 권한·장치 목록만으로 파형 성공을 보증하지 않음 |
| 복구 전 Android | 사용자가 TEST 버튼을 직접 눌러 발화했으나 PCM16/16kHz/mono160000bytes, RMS0.000014/peak2. read 음수 오류 없음 | 타이밍을 놓친 앞선 측정은 실제 발화 시험에서 제외 |
| 에뮬레이터 설정 | hostmicon OK/hw.audioInput=yes였지만 실행 인자에 allow-host-audio 없음 | 명령 수락과 실제 음성 전달은 별도 |
| 동일 AVD 정상 재실행 후 | allow-host-audio 옵션 명시·config SHA 동일. 실제 동일 발화 RMS0.033866/peak14958,160000bytes, silenced=false | 옵션과 장치 재초기화 각각의 기여는 분리하지 않음. 내부 결함까지 확정하지 않음 |
| 수동 STT 복구 | Kotlin 변경 전에 실제 한국어 진단 연속2회와 Main 작성 화면 자동 반영 성공 | 피크 증가를 STT 성공으로 대신하지 않음 |
| 남은 홈 실패 | 실제 ‘카잇다’ 발화에 최종 ‘가 있다’가 반복 수신. 기존 엄격한 호출 패턴이 거부 | 별도 어휘 인식 문제. 세션 충돌의 증거가 아님 |

host audio 기본 차단·시작 옵션·토글 재시작 경계는 [Android 공식 안내](https://developer.android.com/studio/releases/emulator#improvements-for-host-audio-behavior), 캡처 정책의 무음은 [isClientSilenced 계약](https://developer.android.com/reference/android/media/AudioRecordingConfiguration#isClientSilenced()), Windows 실제 캡처는 [waveInOpen](https://learn.microsoft.com/en-us/windows/win32/api/mmeapi/nf-mmeapi-waveinopen)과 대조했다. 단순 피크 미터를 캡처 성공으로 간주하지 않았으며 원본 음성은 저장하지 않았다.

### A/B 코드 관계·공유 자원

| 경로 | 호출 관계 | 정상 완료/해제 |
| --- | --- | --- |
| A 수동 기록 | MainActivity → SpeechControls → Main 전용 SpeechInput → PleosSpeechBackend → SpeechDraftDelivery → 원래 초안 | final+실제 ended 후 동일 listener detach. 취소/이탈/오류는 generation 무효화·stop/removeListener/release |
| B 홈 대기 | MainActivity → HomeVoiceRuntime → wake 전용 SpeechInput → activate → VoiceConversationController → 대화 전용 SpeechInput/WholeExplanationPlayback | wake 해제·focus 반환 후 TTS. 실제 전체 DONE 후 다음 STT. 홈 이탈/onStop/운전자·계정·권한·안전/OFF에서 stopEffects |
| 수동 대화 화면 | VoiceConversationActivity → 별도 SpeechInput/같은 Controller·서비스 | onStop의 controller.end·음성/AI 해제, onDestroy의 저장소/worker 닫기 |

각 SpeechInput/Backend는 별도 객체·listener다. 실제 AAR에서도 SpeechToText별 ViewModel/ServiceManager/OnDeviceStt 생성과 callback 등록/해제를 확인했다. 기반 CAAS 서비스와 물리 마이크는 공유한다. 기존 VoiceAudioOwner는 앱 프로세스 내 수동/홈/TTS 반이중 점유와 자기 소유자 해제를 보장하고 VoiceSessionOwner는 대화 중복을 막는다. 홈은 AndroidVoiceListeningFocus의 transient assistant focus를 사용하며 TTS는 SpeechOutput의 기존 focus를 쓴다. 수동 경로에 새 focus/AudioRecord를 추가하지 않았다. 다른 앱까지 잠그는 전역 소유권은 아니다.

c424ec25/a9c2b29/7f913a4의 SpeechInput을 비교했다. 초기 자동화 때 owner claim/release가 추가됐고 a9 이후 홈 도입 구간에는 수동 SpeechInput 변경이 없었다. 이번 실제 교차 실행에서 충돌은 재현되지 않았다. 유사 코드·동일 SDK만으로 충돌을 확정하지 않았으며 모든 외부 전화/내비 경합이 없다는 뜻도 아니다.

### 원인 확인 후 최소 수정

원인과 대상3파일을 수정 전에 사용자에게 알렸다. MainActivity·SpeechInput/Backend/Output·VoiceAudioOwner·수동 초안 전달·SQLite/AI 정책은 수정하지 않았다.

- VoiceCommandRouter: 관측된 **전체 문장 ‘가 있다’**만 확인 질문 후보로 구분. 정확한 카잇다/카있다 패턴 유지. ‘다 있다’·문장 일부 유사음·가 있다+증상은 자동 호출로 인정하지 않음.
- HomeVoiceRuntime: 후보이면 “카잇다를 부르셨나요? 맞으면 네라고 말씀해 주세요.”로 기존 제어기에 연결.
- VoiceConversationController: 실제 final+ended의 네/예/맞아요 뒤 “말씀하세요.”를 읽고 실제 DONE 후 **별도 증상**을 받음. 부정·모호한 답·확인 대신 말한 증상은 기록/AI 없이 종료. 5분/5발화·취소·generation 유지.
- 새 HomeWakeConfirmationChecks와 runner의 prepare 전 분기: 위 경계14개를 격리 검사. 일반 앱에 측정 UI/새 엔진을 넣지 않음.

이는 **전면의 제한된 Pleos STT 대기와 음성 확인**이다. SDK 호출어 어휘 자체를 수정한 것이 아니며 독립 엔진·백그라운드 호출·외부 Gleo 성공이 아니다.

### 실제 발화·자동 검사 구분

| 검사 | 결과 | 범위 |
| --- | --- | --- |
| 실제 한국어 진단 연속2회 | PASS | PLEOS_ON_DEVICE READY→STARTED/PARTIAL→ENDED→FINAL→COMPLETE. 두 번째 요청은 SDK 재사용 |
| 실제 Main 수동 자동 반영 | PASS | 홈 대기 OFF·사용자 발화/자동 종료/입력란 확인. 짧은 결과와 다음 전체 문장은 별도 입력 |
| 수정 전 실제 홈 호출 | FAIL | 최종 가 있다/엄격한 패턴 불일치, 새 문제0 |
| 수정 후 홈 호출→확인→증상→저장 | PASS | 첫 답 ‘그래’는 저장 없이 종료. 다시 가 있다→네→말씀하세요→실제 증상→1건/receipt1. 같은 운전자/revision1/UNRESOLVED·사용자 청취 확인 |
| 실제 TTS→후속 STT | PASS | 실제 전체 DONE 뒤 다음 READY. 홈 대화 playback DONE4 |
| 실제 “음성 대기 꺼 줘” | NOT_TESTED | 사용자 종료 응답은 있으나 해당 final은 없고 RECOGNITION_TIMEOUT/END_UNCONFIRMED. 음성 OFF 성공으로 계산하지 않음 |
| 설정 OFF·화면 이동 후 해제 | PASS | TEST 설정 OFF·AppOp running=false·CARITDA focus 없음·CAAS 마지막 캡처 stop. 다른 UID의 시스템 ai.umos.ambientai HOTWORD_TAP은 별도로 구분 |
| 마지막 A 재실행 | PASS | 같은 Main/UUID에서 실제 한국어 final+ended/COMPLETE·입력란 사용자 확인. 미저장 입력은 초안으로 보존 |
| 확인 분기14+관련 기존318 | PASS | 합계332 assertion, 합성과 실제 SDK를 아래 구분 |
| 일반 앱 이번 사용자 발화 | NOT_TESTED | 사람 시험은 별도 validation의 같은 Main/SDK 소스에서 실행 |
| 물리 전화/내비 포커스·주행·권한 철회·실차 | NOT_TESTED | 상태/포커스/권한/안전 fixture 성공으로 대체하지 않음 |

관련318 = STT상태40+홈29+자동화72+실제 SDK READY/취소/재연결 UI6+실제 전체 TTS3+음성 UI17+DB19+Gleo 내부37+단일AI56+인계35/cold4. 합성 callback을 사용한 항목을 실제 발화/실모델로 세지 않는다. Gleo 내부 계약과 외부 실행도 별도다. 전체477/Worker198 무조건 반복은 하지 않았다.

Debug/Validation/ValidationAndroidTest/Lint/Unit 작업 BUILD SUCCESSFUL2분31초, TEST 이름만 바로잡은 AndroidTest 재빌드1분4초. Lint0오류67경고. Unit NO-SOURCE는 성공 건수가 아니다. 무발화 독립 SDK 진단은 INITIALIZED→LISTENER_ADDED→REQUEST→READY→STOP, 오류NONE/final·ended없음으로 별도 기록했다. [공식 listener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)·[request/stop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop)·[한국어 locale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale) 계약과 대조했으며 타이머/stop으로 임의 final·ended·DONE을 만들지 않는다.

### 데이터 보존

시작 일반44DB/2667행+validation981DB/14356행=**1025DB/17023원본행**. 종료 사본과 원본행 다중집합·schema/index/view/trigger·version·integrity/FK를 전수 비교해 보존했다. 일반 문제25/이벤트100/운전자10/초안0 불변. 일반 업데이트 전에는125행, 업데이트/실행 후 최종 사본은 관측128·스냅샷5·파생 인사이트10·SOC평가13/공백3, 총159행 추가다. 기존 행 변경은 없지만 DB 전체 바이트 동일이라고 주장하지 않는다.

validation 기존981DB/14356행 변경0·새 고유 fixture DB50개. 실제 사용자 시험 UUID에 수동 저장1/자동 저장1과 마지막 수동 초안이 있다. 홈 세션2개 최종 ENDED·issue/receipt/암호화 본문 보존·Gemini operation0. 일반 files/prefs는 업데이트 전17개 동일, 최종15개 동일/2개 변경(Firebase heartbeat·설치 표시)이다. 기존 인증/AI 캐시 파일은 동일하다. validation419개 동일/6개 변경(heartbeat·진단/fixture 포인터·격리 AI 캐시·설치 표시). 백업/개인 로그/SDK 원문/DB는 공유하지 않는다.

일반 APK는 기존 인증서 일치를 확인한 뒤 install-r로 교체했다. 설치 내용 SHA256 `263b11576c6c1aeea4a67e97cefa1a4950f098c90797866c52d3260ea27ed7ef`와 빌드 산출물이 일치하고 Main 실행/resumed가 성공했다. 일반 UI 버튼 자동 조작0/새 문제0, 실제 사람 발화는 TEST 결과로만 보고한다. Vosk 모델/lib는 일반 APK에 없다.

설치 직후 **가동 중 tar 사본**에서는 observations_recent 인덱스 무결성 검사가 실패했다. 원본을 수리/재색인하지 않고 일반 앱의 쓰기를 중단한 뒤 새 사본을 만들자 전체1025DB의 원본행·무결성 검사가 통과했다. DB와 WAL의 비원자 복사 영향으로 판단하며 실패 사본도 보존했다. 이 실패를 조용히 성공 처리하거나 원본 DB 손상으로 확정하지 않는다.

검증된 명시11파일의 GitHub 연결 도구 게시 시도는 `user cancelled MCP tool call`로 중단했다. 새 main 커밋은 없으며 이후 원격 `7f913a466490664171706029a3235885558a7584` 불변을 읽기 확인했다. 같은 요청을 재시도하거나 다른 게시 경로로 우회하지 않았다. 로컬 수정/문서와 일반 APK 업데이트는 보존된다.

### 공식 자료 선택 재검토·제출 위험

이전 웹 원문 snapshot이 없어 ‘오늘 변경’으로 확정하지 않는다. 현재 공식 내용과 구현의 차이를 다음처럼 재확인했다.

1. [개발 안내](https://appdevchallenge.auto-hmg.io/guide/development)의 Android API28+/Gradle8+/Kotlin·Java와 현재 min28/Gradle8.11.1이 맞는다. [Vehicle 공식 샘플 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)의 min23은 공모전 조건을 낮추는 근거가 아니다. SDK2.3.2/AVD2.1은 실제 프로젝트와 대조했다.
2. [공식 release note](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)의 STT/TTS3.3.1과 initialize listener/ErrorType/Locale 사용이 일치한다. 영문/국문 표 Fused1.1.2/1.1.3 차이는 있으나 표만 보고 현재1.1.3을 내리지 않았다. [Vehicle](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)·[Navi](https://github.com/Pleos-Playground/pleos-sample-navi-app)·[Fused](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app) 공식 샘플의 의존성·권한·초기화 분리를 비교했으며 STT 공식 샘플이라고 보고하지 않는다.
3. [STT 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)의 ON_DEVICE는 기기 내 음성 변환이다. 호출/저장/명령 판단은 앱 책임이다. [Playground](https://developers.pleos.ai/playground)의 Gleo 소개는 일반 APK 독립 호출어 권한의 승인이 아니다. 새 일반 앱용 wake API는 확인하지 못했고 특권 API를 호출하지 않았다.
4. [제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)·[FAQ](https://appdevchallenge.auto-hmg.io/faq)·[규정](https://appdevchallenge.auto-hmg.io/guide/rules)의 접수 마감은 **2026-11-15 23:59**다. 서명 신청서/기획서가 필요하며 1차 영상/GitHub/APK 선택 평가 자료와 이후 실행 앱 의무를 구분한다. APK 제출 시 안내된 release 경로·SHA와 심사자 접근 조건을 준비해야 한다. README의 Debug/Release 미완료 표시는 맞으며 이번 변경의 Release 서명·제출본 검증은 하지 않았다. 공개 전환 또는 대체 다운로드 조건도 공식 안내를 따라야 한다.
5. [심사 기준](https://appdevchallenge.auto-hmg.io/guide/judging-awards)의 차량 필요성·운전자 불편·구현성 관점에서, 공동 운전자 인계/수정·점검 이력/실제 SDK 관측 연결이 CARITDA의 차량 활용 근거다. 단순 Gemini 대화만으로 상용 비서 대비 우월성을 입증한 것은 아니다. 기획의 시동/종료 후 감시·원격 가족 공유·자동 고장 판정은 현재 정차/전면/로컬 범위와 다르므로 실제 시연 성과에 포함하지 않는다.

접수 전 **P0**: 같은 AVD host audio 실행 확인, 수동 대체 경로 유지, 홈의 제한 대기/확인 질문·실제 OFF 동작 구분, 기획서/영상과 검증 범위 일치, Release/심사 접근 준비. **P1**: 외부 Gleo/PTT·독립 장시간 호출어·전화/내비 경합·실차. 안정 시연 가능성은 확인된 정차·전면·수동/확인형 홈으로 한정한 엔지니어링 판단이며 심사 결과 보장이 아니다.

## 2026-11-11 15:46 +09:00 — 일반 앱 음성 초안 자동 반영

공식 ON_DEVICE/request/Locale.KOREA와 자동 종료 수명주기는 그대로 유지했습니다. MainActivity의 인식 후 확인 AlertDialog만 제거하고 `SpeechDraftDelivery`로 최종/실제 ended 이후 원래 입력란에 한 번 추가합니다. 직접 수정·명시적 기록 저장은 유지합니다. 취소/빈 문장/다른 초안·작성자/동시 편집/늦은 callback은 원문을 덮어쓰지 않습니다.

일반 APK를 서명 일치 확인 후 교체 설치했고, 사용자가 **“팝업 없이 입력란에 반영됨”**을 확인했습니다. 이번 실제 SDK 이벤트는 `PLEOS_ON_DEVICE ENDED → FINAL → COMPLETE`였으며, 순서가 바뀌어도 두 이벤트가 모두 도착한 뒤 완료됩니다. 문장/음성 원본은 진단 로그나 외부에 남기지 않았습니다. 수동 종료 시험이나 마이크 재설정을 요청하지 않았습니다.

새 실제 MainActivity 격리 검사21개는 합성 callback 기반으로 원문/부분문장/final-only/중복/취소/재생성/저장/B 인계 확인을 검증합니다. 기존 세션40개와 실제 SDK READY·취소UI6개도 재실행했습니다. 실제 연속3회는 아래 이전 사용자 성공 사실이며 이번 1회 UI 확인과 별도로 셉니다. [이번 통합 결과](CARITDA_INTEGRATION_REPORT.md), [Gleo 후속 경계](GLEO_INTEGRATION_ACCEPTANCE.md)를 참조하세요.

아래는 자동 종료·취소 자체를 안정화한 이전 기록입니다.

갱신: 2026-11-11 02:26:21 +09:00 (Asia/Seoul). 사용자 최종 지시에 따라 **공식 request() 기반 자동 종료만 제공**합니다. 수동 인식 완료 버튼과 수동 최종화 실험은 사용자 화면에서 제거했습니다. 취소는 인식 완료와 다릅니다.

## 최종 검증 결과

| 기능 | 결과 | 검증 범위 |
| --- | --- | --- |
| 실제 한국어 인식 | IMPLEMENTED_AND_RUNTIME_VERIFIED | Pleos Connect2.1.0, SDK3.3.1 ON_DEVICE/request, 사용자 확인과 SDK FINAL/ENDED/COMPLETE 로그 |
| 연속3회·자동 종료 | IMPLEMENTED_AND_RUNTIME_VERIFIED | 실제 사용자 발화·화면 표시, 마지막 정상 시험에서 STT_END_TIMEOUT 없음 |
| 최종 자동/취소 UI | IMPLEMENTED_AND_RUNTIME_VERIFIED | 상태 검사40개(합성), 최종 APK의 실제 SDK READY→취소→재초기화2회/실제 View 검사6개 |
| 최종 UI 수정 후 추가 발화 | 별도 미수행 | 자동 인식 SDK 경로는 유지, 실제3회는 UI 정리 직전 사용자 확인. 추가 수동 종료 시험 없음 |
| 종료 이벤트 누락·늦은 결과·오류 복구 | 합성 callback 검증 | 시간초과는 실패/해제이며 성공으로 만들지 않음 |
| 직접 PCM 경로 | validation 전용 | 원인 분리용. 일반 앱의 주 경로로 사용하지 않음 |
| 수동 인식 완료 | 이번 UX 범위에서 제외 | 기존 SDK stop 이후 최종/종료 콜백 누락을 숨기거나 해결했다고 보고하지 않음 |

## 원인과 실제 복구

서로 다른 문제를 분리했습니다.

1. 기존 앱은 final에서 즉시 stop/release하고 onEnded를 처리하지 않았으며 같은 listener 제거가 없었습니다. 초기화/종료/재입력 경계를 고쳤습니다. SDK 연결을 정상 세션 사이에서 재사용하고, 오류·취소·화면 이탈 시 해제 후 다음 요청에서 재초기화합니다.
2. 실제 사용자 마이크 입력은 Windows에서 정상인데 Android 에뮬레이터 캡처가 매우 작은 값에 머물렀습니다. Windows 설정·SDK 재설치로 해결한 것이 아닙니다. 지원되는 **-allow-host-audio** 옵션을 명시해 기존 AVD를 정상 재실행한 뒤 Android 입력과 실제 한국어 인식이 복구됐습니다. GUI ON이 실제 전달을 보장하지 않았던 정확한 내부 사유와 시작 옵션/재초기화 각각의 기여는 별도로 분리 검증하지 않았습니다.
3. 수동 stop은 서비스 로그에서 close→AudioRecord stop/release까지 실행됐지만 SDK final/ended가 오지 않았습니다. 앱이 이를 STT_END_TIMEOUT으로 표시했습니다. 사용자 결정에 따라 정상 완료는 자동 종료만 사용하고, 사용자 중단은 별도의 취소로 처리합니다.

기존 passive Windows 피크 미터 스크립트는 실제 캡처를 열지 않아 0을 입력 실패의 증거로 쓸 수 없었습니다. 그 추론을 철회하고 실제 캡처로 확인했습니다. 원인 확인 없이 제한시간을 늘리거나 작은 잡음을 증폭하지 않았습니다.

## A — Windows 실제 입력과 Android 독립 캡처

사용자 Windows 세션의 active-capture-v2: PCM16bit/16kHz/mono, 기본 입력252800bytes/RMS0.049461/peak17058, 통신용124800bytes/RMS0.022387/peak6957, Realtek 직접124800bytes/RMS0.018303/peak4280. 모든 open/start/error=0, initialized/started/closed=true. 설정 변경·PCM 저장·외부 전송 없음. [공개 Windows 캡처 API](https://learn.microsoft.com/en-us/windows/win32/api/mmeapi/nf-mmeapi-waveinopen), [피크 미터의 경계](https://learn.microsoft.com/en-us/windows/win32/coreaudio/peak-meters)를 기준으로 구분했습니다.

Android user10의 RECORD_AUDIO/STT_SERVICE 허용, AudioManager microphoneMuted=false, 실제 AudioRecord isClientSilenced=false입니다. MIC/VOICE_RECOGNITION 각각5초, read=1600 short samples/회, 총80000samples=160000bytes, 음수 read오류 없음, routed type15 built-in mic.

| 실제 Android 검사 | MIC | VOICE_RECOGNITION |
| --- | --- | --- |
| 복구 전 RMS / peakS16 | 0.0000609435 / 2 | 동일 |
| 명시적 host-audio 재실행 후 RMS / peakS16 | 0.0004224895 / 167 | 0.0003019587 / 62 |

RMS는 실제 signed 샘플로 sqrt(sumSquares/sampleCount)/32768을 계산한 값입니다. 0.000061은 UI 기본값이 아니며 이전0.0000은 네 자리 반올림이었습니다. SDK RMS는 앱의 정규화 PCM RMS와 단위가 다릅니다. 어느 한 작은 값만으로 정상 발화·완전 무음을 확정하지 않습니다.

## B — 공식 request() 경로

[3.3.1 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop), [리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners), [언어 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)와 실제 AAR을 대조했습니다. Mode.ON_DEVICE와 Locale.KOREA를 명시합니다. 서버 등록·새 Secret·유료 STT 호출은 없습니다.

무발화 독립 진단은 SpeechInput을 거치지 않고 SDK 직접 INITIALIZED→LISTENER_ADDED→REQUEST→READY→STOP을 실행했습니다. 복구 전 RMS49회/SDK값약1.84~2, 복구 후46회/약7.82~14.90, final/ended없음·SDK오류NONE였습니다. 무발화 진단을 실제 발화 실패나 성공으로 바꾸지 않습니다.

실제 사용자 발화에서는 REQUEST→READY→STARTED/PARTIAL/RMS→ENDED→FINAL→COMPLETE를 확인했습니다. final/ended가 반대 순서로 와도 두 조건을 모두 확인하고 결과를 한 번만 전달합니다. 연속3회 완료와 화면 문장 표시는 사용자 확인 사실입니다. CARITDA 자체 진단 로그에는 인식 문장·음성·계정을 남기지 않습니다. SDK·시스템 원본 로그는 공유하지 않습니다.

## C — 정상 공개 PCM 엔진 분리

[CMUSphinx 공개 시험 음성](https://github.com/cmusphinx/pocketsphinx/blob/main/test/data/goforward.raw), [라이선스](https://github.com/cmusphinx/pocketsphinx/blob/main/LICENSE), [형식 근거](https://github.com/cmusphinx/pocketsphinx/blob/main/test/unit/test_acmod.c). 89160bytes, PCM16LE16kmono, SHA256 f15c60ec54059d8b66e410d0064945a0b0a04ea56e1ddca1958e493c0cf70e71. 사용자 음성이 아니며 로컬 validation 자료로만 보관합니다.

공식 ON_DEVICE/sendAudio/completeAudioSend/Locale.US에서 3/3 실제 final(21자·기대 단어 확인)/ended1회, 오류 없음, 약2.9초. [PCM 규격](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data)을 따랐습니다. 영어 공개 PCM 성공과 실제 한국어 마이크 성공은 별도 결과입니다.

수동 중단 원인 분리에서 전체 파일은 이미 자연 종료돼 비교에 충분하지 않았습니다. 38400bytes로 자른 별도 시험은 stop만/stop후EOF에서 final없음, EOF후stop에서 실제 final6자/ended1회였습니다. 이는 PCM 버퍼 실험이며 request 수동 최종화 성공으로 주장하지 않습니다. 수동 호환 분기는 최종 사용자 코드에서 제거했고 진단은 validation에만 보존합니다.

## 자동 종료·취소 동작

- 준비 중/음성 인식 중/자동 마무리/인식 완료/취소/실패를 구분합니다. 말을 마치면 자동 종료된다고 안내합니다. 사용자 화면에는 입력 종료 버튼이 없습니다.
- 취소는 SDK stop·동일 listener 제거·release·generation 무효화 후 즉시 재입력 가능한 별도 CANCELLED 상태입니다. SDK ended나 인식 성공을 만들어내지 않습니다. 지연 final은 전달하지 않고 기존 작성 내용과 DB를 바꾸지 않습니다.
- 실제 자동 종료가 없으면 기존 제한시간 안에 실패/연결 해제로 복구합니다. 부분 문장을 SDK 최종 결과로 승격하지 않습니다. 예외 진단 코드는 TEST에만 표시합니다.
- 실제 SDK 준비 뒤 취소·재초기화를 두 번 수행하는 View/SDK 검사6개가 통과했고 이전 성공 metadata도 유지됐습니다. 초기 UiAutomator 방식은 RMS 갱신 때문에 idle 대기에 실패했으므로 main-thread View click과 상태 관찰 계측으로 대체했습니다. 자동화 도구 실패를 SDK 실패로 계산하지 않았습니다.

## 빌드·데이터·후속 단계

assembleDebug/assembleValidation/assembleValidationAndroidTest/lintDebug 성공. testDebugUnitTest는 NO-SOURCE입니다. 새 자동/취소 상태40개와 실제 SDK/UI6개를 이전 수동 중심35개와 중복 집계하지 않습니다. 통합 전체 안전한 기존219+신규94=313개 assertion, 실제 발화3회·PCM3회·독립진단은 별도입니다. 과거297개 전체를 초기화 경로로 재실행한 결과가 아닙니다.

최종 백업·비교: 일반41DB/validation163DB, 시작 시188개 기존DB schema/version/원본행/integrity/FK 보존. 일반24문제/96이벤트/10운전자/0초안·원본1106행 불변, validation 원본8938행 보존. 일반 files/prefs15개와 인증·암호화 AI 결과 바이트 동일. 일반 앱 기존 설치 유지, TEST만 교체 설치했습니다. Vehicle2.3.2/Firebase/Gemini/Worker/원장/AVD 초기화·모델 호출·서버 배포 없음.

[다른 PC 실행 방법](STT_RUN_GUIDE.md)을 제공합니다. PC B 실제 실행·Release·실차는 미검증입니다. 다음 단계는 확인한 인식문을 기존 문제 초안에 반영→명시적 저장→운전자 인계의 실제 통합 검증입니다. 기본 확인/초안 연결 코드는 존재하지만 일반 사용자 DB에 시험 기록을 만들지 않았습니다. Gleo App Schema 승인·외부 Action/Callback 검증은 별도이며 STT 성공을 Gleo 명령 성공으로 표현하지 않습니다.
