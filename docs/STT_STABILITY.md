# STT 자동 종료·취소 안정화

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
