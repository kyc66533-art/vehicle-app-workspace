# Pleos STT 실행·재현 안내

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
4. 일반 앱에서 인식문을 기록에 반영하려면 확인·수정 후 기존 저장 버튼을 직접 눌러야 합니다. 자동 저장이나 자동 해결 상태 변경은 없습니다. 기존 일반 APK를 이번 작업에서 교체 설치하지는 않았습니다.

## 진단 경계

- [공식 Android 안내](https://developer.android.com/studio/releases/emulator)는 host audio 기본 차단과 시작 옵션을 설명합니다. 이 설치는 동적 adb emu avd hostmicon을 KO로 거부하므로 그 명령을 반복하지 않습니다. exit0만으로 성공을 판단하지 않습니다.
- Windows 피크 미터0만으로 마이크 입력 실패를 확정하지 않습니다. 실제 캡처 바이트와 RMS, Android AudioRecord read/silenced, SDK ready/partial/final/ended를 따로 확인합니다.
- netsimd는 [에뮬레이터 네트워크 시뮬레이터](https://developer.android.com/studio/run/emulator-networking-advanced)의 보조 프로세스입니다. 현재 emulator/qemu/netsimd 각1개를 확인했습니다. 별도 창만으로 중복이나 오류로 보지 않으며 임의 강제 종료하지 않습니다.
- SDK3.3.1 ON_DEVICE는 별도 서버 등록/키 없이 동작합니다. HYBRID/SERVER로 몰래 전환하지 않습니다. 음성 원본은 저장·외부 전송하지 않습니다.
- 실제 PC B/다른 마이크/실차/장시간 입력은 별도 미검증입니다. APK는 Debug이며 Release 서명 완료라고 표시하지 않습니다.
