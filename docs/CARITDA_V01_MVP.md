# CARITDA v0.1 사용 및 검증

검증일: 2026-11-07 (Asia/Seoul). CARITDA는 상표 검증 전 가칭입니다.

## 구현 범위

- 메인: 현재 운전자 A/B, 미해결 문제, 해결 기록 표시 전환, 새 문제, 전체 상담 요약.
- 기록: 소음/진동/경고등/기타, 발생 상황, 발견 날짜·시각, 등록 운전자, 추가 메모.
- 인계: 동일 차량 앱 내 A/B 전환, 다른 운전자의 확인 시각·주체, 중복 확인 방지.
- 관리: 미해결/점검 예정/점검 완료/해결 확인, 점검·조치 메모, 등록·확인·상태 이력.
- 재발: 기존 카드에 새 발생 기록을 추가하고 미해결로 전환합니다. 이전 확인을 보존하며 새 차수는 다시 확인합니다.
- 상담: 증상·발견 시각·설명·확인 이력·조치·해결 상태를 하나의 스크롤 화면에 표시합니다.

SQLite `caritda_v01.db`에 문제·이벤트·선택 운전자를 저장합니다. 앱 재실행과 교체 설치에서 유지되며 앱 삭제/데이터 지우기는 보존을 보장하지 않습니다. 자동 삭제나 파괴적 마이그레이션은 없습니다. 시각은 기기 시간대를 사용하고 요약 화면에 시간대를 표시합니다. 현재 AVD는 America/Los_Angeles이므로 PC의 한국 시각과 다릅니다. 시스템 시간대를 임의 변경하지 않았습니다.

모든 문제·메모는 운전자가 입력한 로컬 기록입니다. 실제 차량 센서값, AI 진단, 휴대폰 전송, 외부 정비소 접수는 없습니다.

## Android Studio 실행

1. 기존 `C:\Users\kyc66\NextDrive\NextDrive-Pleos`를 엽니다. 새 프로젝트를 만들거나 기존 환경을 초기화하지 마세요.
2. 기존 SDK Platform 34, Build-Tools 35.0.0, Gradle 8.11.1, Android Studio JBR 설정을 유지합니다. 필요하면 Sync를 실행합니다.
3. Device Manager에서 기존 Pleos Connect v2.1.0/API 34 AVD를 시작합니다.
4. 실행 구성 `app`과 Pleos 에뮬레이터를 선택하고 Run을 누릅니다. 앱 이름은 `CARITDA(가칭)`입니다.
5. 정차 중 플랫폼 입력 허용 응답을 기다립니다. 연결 미확인/제한 시 입력은 잠깁니다. 이를 해제하는 수동 우회 버튼은 없습니다.

## 사용자 인수 시나리오

1. `운전자 A`를 선택합니다.
2. 새 문제에서 `진동`, 발생 상황 `가속할 때 핸들이 진동함`을 입력하고 발견 시각·메모를 확인하여 저장합니다.
3. 앱을 종료하고 다시 실행합니다. 데이터 지우기/앱 삭제는 하지 마세요.
4. 이전 진동 기록이 유지되는지 확인합니다.
5. `운전자 B`로 전환합니다.
6. 문제의 `문제와 기록 보기`를 엽니다.
7. 인계 확인 버튼을 누르고 B와 확인 시각이 표시되는지 확인합니다.
8. 점검·조치 메모에 점검 결과를 입력합니다. 필요하면 `상태 변경하기`에서 점검 예정/완료/해결 확인을 선택합니다.
9. `이 문제의 정비 상담용 요약`에서 증상·설명·등록자·B 확인·조치·현재 상태를 확인합니다.

검증 과정에서 만든 수기 예시 카드는 메모에 에뮬레이터 검증용임을 표시했으며 기존 기록과 함께 보존했습니다. 실제 차량 결함이라고 주장하지 않습니다.

## 실제 검증 결과

환경: Windows, Android Studio JBR 21.0.8, Kotlin 2.2.21, AGP 8.10.1, Gradle 8.11.1, API 34, Build-Tools 35.0.0, 실제 Pleos Connect v2.1.0 AVD `emulator-5554`.

- `:app:assembleDebug :app:assembleDebugAndroidTest`: 최종 소스에서 BUILD SUCCESSFUL. 기존 캐시 기반 offline 빌드, Kotlin in-process 컴파일 사용.
- Debug 앱과 테스트 APK를 ADB `install -r`로 설치했습니다. 기존 앱을 삭제하거나 저장 데이터를 지우지 않았습니다.
- 프레임워크 계측 실행 결과 `CARITDA_MVP_PASS checks=24`. 실제 SQLite, 실제 Activity와 입력 대화상자를 사용했습니다. 위 9단계, 재실행 복원, 중복 확인, 네 상태, 재발/이력 보존, 잘못된 입력 거부를 통과했습니다.
- 제한/미확인 상태를 주입한 서비스 검사에서 6개 변경 동작이 차단되고 DB가 유지됐습니다. 실제 AVD의 AAOS 화면 제한 서비스에서는 입력 허용 응답을 받았습니다. 실제 주행 전환·실차 안전 검증은 미수행입니다.
- 최종 APK를 프로세스 강제 종료 후 다시 실행하여 B 프로필과 기록 복원을 확인했습니다. ADB 터치로 상태를 점검 예정으로 변경하고 실제 요약 화면과 메모 표시도 확인했습니다.
- 홈·입력 화면의 600dp 콘텐츠 폭 검사 통과. 모든 차량 화면 크기/실차 인증 검사를 의미하지 않습니다.
- `:app:lintDebug`: 미완료. offline 캐시에 `com.android.tools.lint:lint-gradle:31.10.1`이 없고 온라인 재시도는 Maven 접근 `Permission denied: getsockopt`로 실패했습니다. Lint 성공으로 기록하지 않습니다.
- Release 서명/배포 APK, Vehicle SDK 데이터 수신, 실차는 미검증입니다. 빌드 경고는 기존 API `onBackPressed` 사용 중단 경고입니다.

로컬 산출물(게시 제외):

- 앱: `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk`
- 테스트: `NextDrive-Pleos/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- 빌드/검사/스크린샷: `NextDrive-Pleos/app/build/validation/`
- 앱 APK SHA-256: `9e0c7197f9d76d1b1abe665c2e9cd804537d20b28560a5514f7ea5a68705f043`

설치된 테스트 APK의 재실행 명령은 `adb -s emulator-5554 shell am instrument -w com.nextdrive.pleos.test/com.nextdrive.pleos.MvpScenarioInstrumentation`입니다. 별도 테스트 DB를 사용하며 실제 UI 시나리오는 검증용 카드 한 개를 추가합니다. 반복 실행 시 기존 카드는 삭제하지 않습니다.

## 소스 구조 및 다음 작업

`MainActivity`는 Kotlin/Android 기본 View로 화면을 구성합니다. `model`은 문제·이력·운전자, `data`는 저장 인터페이스와 SQLite 트랜잭션, `domain`은 기록/상태/요약 규칙, `safety/CarSafetyGate`는 공개 AAOS CarUxRestrictions 연결을 담당합니다. 실제 Pleos Vehicle SDK 센서 연동과 구분됩니다. 추가 Maven 라이브러리는 도입하지 않았습니다.

다음 버전의 첫 기능 후보는 사용자가 확인한 상담 요약의 파일 내보내기입니다. 외부 전송은 사용자 동작과 명시적 목적지를 요구하도록 설계하며 현재는 구현하지 않았습니다. Lint 의존성을 받을 수 있는 일반 Android Studio 환경에서 Lint를 완료하고 실제 주행 제한 전환을 별도 검증해야 합니다.
