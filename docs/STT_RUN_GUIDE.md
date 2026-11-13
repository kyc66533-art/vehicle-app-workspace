# Pleos STT 실행·재현 안내

## 2026-11-12 — 이번 PC의 확인된 복구 순서

이번에는 `hostmicon → OK`여도 발화 중 Android peak2였다. Windows 실제 캡처 peak7682를 확인하고 **기존 AVD 정상 Stop → 같은 AVD를 -allow-host-audio로 시작**한 뒤 Android peak14958와 실제 한국어 A→B→A 교차 입력이 복구됐다. 아래 과거 KO는 당시 기록이다. 앱/AVD 초기화나 SDK 교체는 없었으며 시작 옵션/장치 재초기화의 기여는 분리하지 않았다. [공식 host audio 안내](https://developer.android.com/studio/releases/emulator#improvements-for-host-audio-behavior)와 [이번 원인·검사·보존 결과](STT_STABILITY.md)를 따른다.

1. 수동 입력부터 확인한다. `음성 대화·설정·이력 → 음성 설정`에서 홈 자동 대기를 끈다. `홈 → 문제 남기기 → 음성으로 증상 입력`에서 준비 후 “차 앞쪽에서 이상한 소리가 나요”라고 말한다. 자동 종료 후 상황 입력란을 확인한다. 저장 전에는 초안이다.
2. 홈은 확정 운전자·개별 동의·마이크 권한·앱 전면·안전 허용이 필요하다. 이번 SDK가 ‘카잇다’를 ‘가 있다’로 변환하면 수정본은 **“카잇다를 부르셨나요?” → “네” → “말씀하세요” → 증상**으로 확인한다. 첫 확인에 증상을 바로 말하면 저장하지 않는다. 안내가 완전히 끝난 뒤 말한다.
3. 최대30초/전면3창과5분/5발화 대화는 별도다. 침묵·모호한 답·시간 초과 후 무한 재시작하지 않는다. 홈의 음성 대기 끄기/설정 OFF도 이용할 수 있다. 이번 실제 “음성 대기 꺼 줘”는 최종 결과가 없어 성공으로 검증하지 못했다.
4. 이번 복구 시험은 TEST의 Gemini 전송 OFF였다. 저장 안내는 로컬 TTS이며 새 AI 결과가 아니다. 기존 AI 동의/한도는 유지한다. 장애 진단을 위해 새 유료 요청을 만들지 않는다.

전면의 제한된 Pleos 대기와 독립 호출어·백그라운드 Gleo·주행 중·다른 PC·실차 성공은 별개다. 사용자 발화와 자동 callback 검사도 구분한다.

## 2026-11-11 — 음성 입력 후 AI 설명과 인계

음성 자동 반영→문장 수정/확인→**저장하고 AI 설명**으로 기존 AI 화면까지 바로 이어집니다. 버튼은 먼저 문제를 명시적으로 저장합니다. 다음 화면의 전송 요약을 확인한 뒤 **새로 설명 생성**을 눌러야 실제 요청하며, 저장된 설명 보기/듣기는 새 Gemini 요청이 아닙니다. 같은 화면의 차량 정보는 로컬 참고이고 AI에 전송하지 않습니다. 다음 운전자는 저장된 같은 기록에서 읽음·점검 이력을 이어갑니다.

미저장 초안 분석은 추가하지 않았습니다. 기존 STT 자동 종료/취소·초안과 사용자 데이터를 보존하고, 일반 앱 교체·실행 및 validation298개를 검증했습니다. 이번 신규 실제 Gemini·배포0회, 새 UI의 실제 발화/실모델/청취 전체 왕복은 미검증입니다. [자세한 결과와 최종 확인](STT_AI_HANDOVER_ACCEPTANCE.md)을 따릅니다. 아래 마이크/AVD 안내는 기존 설정을 재사용하며 이번 작업에서 다시 실행하거나 초기화하지 않았습니다.

---

2026-11-11 02:26:21 +09:00 (Asia/Seoul)

## 기존 AVD 실행

이번 PC는 Windows 실제 마이크 캡처가 정상인데 에뮬레이터 전달이 작은 값에 머물렀습니다. -allow-host-audio 명시 후 기존 AVD를 재실행해 실제 한국어/자동 종료/연속3회를 확인했습니다. 다른 PC에서도 이 옵션으로 시작할 수 있습니다. 마이크 전달은 매 실행 확인하며 결과가 없으면 성공으로 가정하지 않습니다.

1. 기존 AVD와 앱 데이터는 유지합니다. 작업 중 입력을 보존한 뒤 Android Studio Device Manager에서 실행 중인 해당 에뮬레이터를 정상 Stop합니다. Wipe Data/Delete는 사용하지 않습니다.
2. 일반 PowerShell에서 설치된 Android SDK 경로와 기존 AVD 이름을 확인합니다. SDK 경로가 다르면 실제 경로를 사용하세요. 시스템 환경변수는 바꾸지 않습니다.

```powershell
$caritdaSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
& (Join-Path $caritdaSdk 'emulator\emulator.exe') -list-avds
& (Join-Path $caritdaSdk 'emulator\emulator.exe') -avd 'Pleos_Connect_v2.1.0' -allow-host-audio
```

목록에 있는 기존 AVD 이름을 사용합니다. 위 명령은 새 AVD 생성·데이터 초기화·SDK 설치·Gemini 설정을 하지 않습니다. Pleos 공식 cold boot 설정은 기존 AVD에서 유지합니다. 이 PC는 forceColdBoot=yes이며 도구가 config.ini를 수정하지 않았습니다.

3. CARITDA TEST에서 음성 입력을 직접 시작합니다. 준비 안내 후 짧게 말하고 발화를 마치면 자동 인식 종료·최종 문장을 확인합니다. 다시 음성 입력으로 반복합니다. 취소는 이번 음성 입력만 버리며 정상 완료가 아닙니다. 원래 증상 입력은 보존합니다.
4. 일반 앱에서 운전자를 확인한 뒤 `증상 남기기 → 음성 입력`을 사용합니다. 최종 문장과 실제 종료 이벤트가 확인되면 **확인 팝업 없이 원래 증상 입력란에 추가**됩니다. 직접 수정한 뒤 기존 저장 버튼을 누릅니다. 초안 반영과 문제 DB 저장은 다르며 자동 저장/자동 해결은 없습니다. 2026-11-11 최종 통합에서 기존 서명으로 일반 APK를 `install -r` 교체했고 사용자가 팝업 없는 반영을 확인했습니다.

화면 회전·앱 재진입 때 인식은 취소하고 이미 반영된 초안을 보존합니다. 작성자/초안/원문이 음성 입력 도중 바뀌면 늦은 결과를 덮어쓰지 않습니다. 수동 인식 완료 버튼은 없으며 취소는 결과를 저장하지 않는 별도 동작입니다. Gleo 호출어는 이 STT 버튼과 별개로 [외부 승인·신뢰 확인](GLEO_INTEGRATION_ACCEPTANCE.md)이 필요합니다.

## 진단 경계

- [공식 Android 안내](https://developer.android.com/studio/releases/emulator)는 host audio 기본 차단과 시작 옵션을 설명합니다. 이 설치는 동적 adb emu avd hostmicon을 KO로 거부하므로 그 명령을 반복하지 않습니다. exit0만으로 성공을 판단하지 않습니다.
- Windows 피크 미터0만으로 마이크 입력 실패를 확정하지 않습니다. 실제 캡처 바이트와 RMS, Android AudioRecord read/silenced, SDK ready/partial/final/ended를 따로 확인합니다.
- netsimd는 [에뮬레이터 네트워크 시뮬레이터](https://developer.android.com/studio/run/emulator-networking-advanced)의 보조 프로세스입니다. 현재 emulator/qemu/netsimd 각1개를 확인했습니다. 별도 창만으로 중복이나 오류로 보지 않으며 임의 강제 종료하지 않습니다.
- SDK3.3.1 ON_DEVICE는 별도 서버 등록/키 없이 동작합니다. HYBRID/SERVER로 몰래 전환하지 않습니다. 음성 원본은 저장·외부 전송하지 않습니다.
- 실제 PC B/다른 마이크/실차/장시간 입력은 별도 미검증입니다. APK는 Debug이며 Release 서명 완료라고 표시하지 않습니다.
