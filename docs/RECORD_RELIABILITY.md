# 사용자 기록 신뢰성·수정·삭제 검증

작업 기준: main `5f753a2ebf97283993d8a6f97eb8214b98af25db`. 기존 AC/ON 결과와 과거 실패/개발 기록은 보존합니다.

## 실제 DB 적용 전 보존·복구 계획

현재 ADB 기기는 emulator-5554, Android 현재 사용자는 10입니다. 앱의 기록 운전자와 Android 사용자는 별개입니다. run-as 기본 user 0의 빈 폴더를 사용자 DB가 없다는 증거로 쓰지 않습니다.

앱을 정상 force-stop한 뒤 user 10의 databases/shared_prefs/files 전체 tar와 문제·관측 DB/journal을 `.tools/reliability-backup/`에 바이너리 보관했습니다. 기존 v2 백업 tar SHA256은 `68f1248d3dcb43a4be4db23e78a6083e21c17b8c0e6709b05e74c977e35a44b8`입니다. 개인정보가 포함될 수 있어 백업·원문·DB·로그는 게시하지 않습니다.

v3 계획은 기존 v2 문제 열/행/ID/운전자/이벤트/초안을 그대로 둔 채 issues에 deleted_at/deleted_by_id를 추가하는 방식입니다. 수정/삭제는 기존 이벤트 테이블에 명시적 이벤트를 추가합니다. SQLiteOpenHelper의 업그레이드 트랜잭션에서 실패하면 스키마·user_version까지 롤백합니다. 기존 관측 DB는 변경하지 않습니다. 운영 DB를 열기 전에 v1/v2 fixture와 운영 v2 백업의 독립 사본에서 원본 전체 행·관계·반복 열기·롤백을 검사합니다.

실패하면 앱을 중단하고 원본/실패 상태/백업을 모두 보존합니다. 자동 삭제·초기화·이전 DB 덮어쓰기나 구버전 APK로 v3 DB를 열기는 하지 않습니다. 복구는 별도 사본에서 전진 수정부터 검증하며 실제 기록 덮어쓰기가 필요하면 사용자 승인을 받습니다.

수정은 원 작성자 영구 ID와 확정 운전자 ID가 같은 경우에만 허용합니다. 발견 시각은 최초 발견의 의미를 보존하기 위해 이 버전의 수정 화면에서는 바꾸지 않습니다. 수정 전후 카테고리·본문·메모, 수정 시각과 작성자를 이벤트로 남기며 revision을 올려 이전 읽음과 수정 후 읽음을 구분합니다. 점검·재발·기존 확인 이력은 지우지 않습니다.

삭제는 확인창 승인 뒤 논리 삭제입니다. 원본·ID·참조 이력은 DB에 남고 일반 목록/상담에서 제외합니다. 사전 백업에도 원문이 남으므로 개인정보의 영구 파기와 다릅니다. 이 버전에는 사용자 복구·영구 파기 UI가 없습니다. 보존 기간/영구 파기는 별도 정책과 사용자 승인 후 구현합니다. 개발 중 과거 검증 기록을 자동 정리하지 않습니다.

## 테스트 격리

새 validation 빌드는 applicationId `com.nextdrive.pleos.validation`, 표시명 `CARITDA 검증 전용`입니다. 동일 앱 소스와 SDK/CarSafetyGate를 사용하지만 Linux UID/DB/preferences/files가 운영 앱과 다릅니다. 계측 runner는 이 ID 외에는 실행을 거부하고 validation 데이터만 준비합니다. 운영 앱 DB를 테스트용 쓰기 대상으로 삼지 않습니다. 기존 운영 대상 계측 APK는 실행하지 않습니다.

## 재현한 원인과 수정

기존 앱 소스를 별도 validation 앱으로 빌드한 뒤 수정 전 네 가지 검사를 실행했습니다. `RELIABILITY_BASELINE checks=4`: 정상 입력은 SQLite commit과 상세 이동에 성공했고, 완료 필터를 선택한 뒤 새 관리 중 기록을 저장하면 DB에는 있지만 목록에서는 숨겨지는 것을 재현했습니다. 또 새 기록→홈→초안 이어쓰기→저장→뒤로 두 번에서 이미 소비한 초안의 입력 화면이 되살아났고, 저장 버튼이 `journal ?: return`으로 아무 안내 없이 끝났습니다. 이 로그는 로컬 `app/build/validation/reliability-baseline.txt`에 있습니다.

따라서 사용자 증상을 모두 SQLite 손실로 단정하지 않습니다. 사용자 환경의 실제 SQLite 쓰기 실패는 당시 logcat에서 재현/입증하지 못했습니다. 제목/본문/시각 검증, 운전자 미확정·초안 소유자 불일치, 안전 상태 UNKNOWN/RESTRICTED는 원래 막아야 하는 조건으로 유지했습니다. Toast 외에도 상단 스크롤 밖으로 놓칠 수 있던 피드백은 스크롤과 분리해 지속 표시하고, 빈 본문에는 필드 오류를 붙였습니다. 새 입력에서는 이전 성공 안내를 지웁니다.

정상 저장은 트랜잭션 종료 후 원본/등록 이력을 읽어 확인하고 ‘저장되었습니다’와 상세 화면을 표시합니다. 저장된 초안의 과거 화면 경로를 제거하고 뒤로 가면 전체 목록을 엽니다. 연속 탭/같은 draft ID 재시도는 한 건만 만듭니다. 실패 시 입력/초안을 남기고 ‘다시 저장’을 제공합니다. 실제 디스크 고갈은 재현하지 않았지만 격리 DB의 이벤트 INSERT를 강제로 실패시켜 문제/수정/삭제 트랜잭션 롤백과 재시도를 검사했습니다.

목록에는 전체 기록/관리 중/완료/내가 작성한 기록, 선택 표시·각 건수와 전체 건수가 있습니다. 필터에 없으면 다른 기록이 삭제된 것이 아님을 안내하고 전체 기록으로 돌아갈 수 있습니다. 홈과 저장 후 진입은 전체 기록을 사용하고, 메뉴로 돌아올 때의 필터는 화면에 명시합니다.

## 실제 보존·빌드·실행 결과

검증 완료: 2026-11-08 +09:00 (Asia/Seoul).

- 기존 JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0/Pleos SDK2.3.2 캐시로 offline assembleDebug+assembleValidation+assembleValidationAndroidTest 성공. 기능 소스 빌드 `BUILD SUCCESSFUL in 1m 22s`, 최종 보호 runner 구성 후 같은 세 작업 재확인41s(93개 up-to-date), exit0. 보호용 debugAndroidTest 별도 빌드45s도 성공했습니다. 앱 소스와 운영 APK 해시는 같습니다.
- 최초 validation 빌드에서 `AccessDeniedException: .tools/gradle-home/.android/debug.keystore.lock`이 발생했습니다. 기존 키를 보존한 채 프로젝트 내부 새 경로에 같은 키를 복사하고 선택적 `caritdaDebugKeystore` 빌드 속성으로 사용했습니다. ACL/시스템 정책을 바꾸지 않았고 키/암호/로컬 경로 설정은 게시하지 않습니다. Android Studio 기본 설정은 그대로 동작합니다.
- Pleos Connect v2.1.0/API34 emulator-5554, Android user10에서 validation 앱·계측 APK를 별도 설치했습니다. 최종 신규 기록 검사36개(아래 RR01~30과 사본 이전/분류6), 실제 프로세스 중지 후 RR31~32 2개, 기존 이전18개·MVP24개·관측6개·UI27개·ON32개가 통과했습니다. 총145개 자동 검사이며 AC28/ON24의 모든 사람/실차 과제가 통과했다는 뜻은 아닙니다.
- ON의 다섯 주요 화면 × 다섯 콘텐츠 폭 × 네 글꼴 크기 × 두 테마 = 200개 조합을 새 필터/지속 안내가 포함된 소스로 다시 검사했습니다. 실제 OS 분할창·모든 대화상자·TalkBack/rotary 종합 검사와 일반 사용자 이해도는 미검증입니다.
- 이번 실제 SDK 응답은 SOC100%, 주행거리0.0km, 각 READ/RECEIVED입니다. 연료는 ERROR_NOT_SUPPORTED_PROPERTY/UNSUPPORTED로 원본 값이 없습니다. 이는 별도 앱에서 받은 에뮬레이터 SDK 응답이며 실차/앱 가상값이 아닙니다. 이전15%/1000000.0km 기록은 이전 수신 사실로 유지합니다. 이 작업은 Virtual Console의 숫자를 설정하지 않았습니다.
- validation 전체 검사 후에도 운영 문제 DB SHA256 `12a6246497416d321f8fcba8ffd027380ec74ddc66f4f6fea3bd9ed56ceb2a98`, 관측 DB SHA256 `dd89e12f70919ed6b997cffcd89dfc520f4eb3824b6eb70372375d64df6d4263`가 사전 백업과 같았습니다. 운영 앱에 새 검증 기록을 넣지 않았습니다.
- 사본의 이전/롤백 검사 후 운영 APK를 `install -r`로 교체하고 user10에서 COLD 실행 Status ok를 확인했습니다. v3로 이전한 운영 DB를 다시 별도 복사해 v2 원본과 모든 기존 driver/issue/event/setting/draft 열과 행이 동일함을 확인했습니다. `PRODUCTION_V3_PRESERVED`, 논리 삭제 표식0건·외래키 정상이며 관측 DB는 위 해시 그대로입니다. 데이터/AVD 삭제·재설치·초기화는 하지 않았습니다.
- 운영 APK SHA256 `40d1b1d21596d0fedc09bc9ddc16e858d6a280d47f4457713d34145280e977ee`.
- 기존 운영 대상 테스트 APK도 새 패키지 보호 코드로 교체했습니다. 실제 거부 시험에서 `Refusing tests against the user's application`, checks=0으로 DB 준비 전 중단하고 운영 문제/관측 해시 불변을 확인했습니다. 이는 의도한 거부 검사 성공이며 기능 테스트 실패나 운영 UI 자동화 성공을 뜻하지 않습니다. 정상 검사는 validation 대상만 사용합니다.

## 신규 수정·삭제 인수기준

아래는 실제 에뮬레이터의 validation 앱과 SQLite에서 통과했습니다. 운영 원본을 직접 고치거나 삭제하는 위험 검사는 하지 않았습니다.

| 기준 | 결과 | 확인한 동작 |
|---|---|---|
| RR01~02 | 통과 | 빈 본문 실패, 메모/초안 보존, 화면에 보이는 지속 안내와 필드 오류 |
| RR03~05 | 통과 | 연속 저장 한 건·성공 안내·상세 이동·전체 목록·재실행 유지 |
| RR06~10 | 통과 | 카테고리/본문/메모 수정, 발견 시각 유지, 전후 이력/revision, 같은 수정 ID 중복 방지, 상담 반영·재실행 |
| RR11~12 | 통과 | 다른 운전자의 UI/서비스 수정·삭제 거부, 이름 변경 뒤 원래 ID 권한과 작성 snapshot 보존 |
| RR13~17 | 통과 | 대상 표시 확인창 취소, 승인한 한 건 논리 삭제, 네 목록/상담 제외, 원본·참조 이력·ID 유지/재사용 차단 |
| RR18~20 | 통과 | 완료/관리 중/내 작성 필터의 정확한 포함·제외 |
| RR21~25 | 통과 | SQLite 등록/수정/삭제 이벤트 실패 주입, 원자 롤백·입력/초안 유지·같은 ID 재시도 |
| RR26 | 통과 | 저장 완료 후 뒤로 가기로 초안 없는 저장 화면 재등장 방지 |
| RR27~28 | 통과 | 실제 lifecycle UNKNOWN 저장 차단/초안 보존, 도메인 제한 시 수정·삭제 차단. 실제 주행 전환은 미검증 |
| RR29~30 | 통과 | 다른 문제의 인계/점검/재발/작성자 보존, 외래키 무결성 |
| RR31~32 | 통과 | 별도 force-stop 및 새 프로세스에서 수정 본문/메모/분류·작성자 UI 복원 |

사용자 요구 H01~15 중 작성/저장/목록/수정/삭제/필터/연속 클릭은 위 RR에서, 관측 보존과 테스트의 운영 DB 무변경은 해시 및 운영 이전 비교에서 확인했습니다. 검증 목적으로 운영 기록을 삭제하지 않았습니다.

## 과거 테스트 기록 분류와 안전한 정리안

백업의 문제는22건입니다. 보존된 로컬 로그의 `UI_ISSUE_ID`와 일치하는5건만 ‘Codex 생성 입증’으로 분류했습니다. 나머지17건은 사용자 작성 여부를 확정할 수 없어 보존했습니다. 사용자 작성으로 별도 확인된 ID는 아직 없으며, 이것이 실제 사용자 기록이0건이라는 뜻은 아닙니다. ‘검증용 초안을 보존합니다.’ 같은 문구만으로는 사용자 복사/변경 가능성을 배제할 수 없어 자동 삭제 대상으로 확정하지 않았습니다.

ID·본문 일부·원 작성자·상태·증거 분류가 있는 목록은 로컬 `.tools/reliability-backup/RECORD_CLEANUP_REVIEW.md`와 원본 JSON에 있습니다. 개인정보 때문에 GitHub에는 게시하지 않습니다. 기존 테스트 프로필도 임의로 지우거나 보관 상태를 바꾸지 않았습니다.

정리의 다음 순서는 사용자가 이 목록에서 대상 ID를 확인 → 새 백업 → 입증되고 승인된 항목만 별도 논리 보관/삭제 → 다른 문제/이벤트/운전자/관측 비교입니다. 이번에는 정리 삭제를 실행하지 않았습니다. 영구 파기와 작성자 부재/보관 계정의 일괄 정리는 별도 정책·승인·복구 계획이 필요합니다.

## Virtual Console 진단

[Pleos 공식 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)에 따른 실제 확인 결과입니다.

```text
adb devices -l
emulator-5554 device product:crp_avd model:Connect_on_x86_64_emulator

adb forward --list
emulator-5554 tcp:8088 tcp:8088

adb -s emulator-5554 forward tcp:8088 tcp:8088
exit 0

GET http://127.0.0.1:8088/
HTTP 200; title Pleos Connect Virtual Console; body 150815 bytes
```

현재는 정상 응답이라 사용자가 겪은 브라우저 연결 실패를 재현하지 못했습니다. PC 포트 소유 PID는 Get-NetTCPConnection/netstat에서 출력되지 않아 확정하지 않았지만, 해당 주소에서 올바른 Console 페이지를 받았습니다. 다른 프로그램 종료·포트 강제 해제·AVD 초기화·SDK 재설치·보안 정책 변경은 하지 않았습니다.

브라우저에서 위 주소를 다시 열어 주세요. 재발하면 AVD 실행/adb devices와 forward 목록부터 확인하고, 8088을 다른 프로그램이 쓰면 PID를 확인한 뒤 사용자 승인 없이 종료하지 않습니다. 콘솔의 숫자가 최댓값으로 바뀌는 현상은 이번에 값을 조작/재현하지 않았으며 앱의 SQLite 저장 오류 원인으로 간주하지 않습니다.

## 사용자 실행 및 검사 재현

이미 기존 앱을 데이터 보존 방식으로 교체 설치했습니다. Android Studio 파일 수동 수정은 필요 없습니다. 일반 `CARITDA`를 실행하고 본인의 이름으로 운전 시작 → 문제 남기기 → 입력/저장 → 성공 안내/상세 → 전체 기록을 확인해 주세요. 내 기록 수정에서 분류/문장/메모를 고친 뒤 재실행해 확인할 수 있습니다. 내 기록 삭제는 대상 문구와 원본 보존 안내를 읽고 취소/삭제를 선택해 주세요. 테스트하려면 본인이 만든 임시 기록만 사용하세요.

자동 검사는 일반 app 대신 validation APK와 계측 APK로 수행합니다. 기존 운영 대상 `com.nextdrive.pleos.test`와 verifyProduction 명령은 실행하지 마세요.

```text
gradlew :app:assembleDebug :app:assembleValidation :app:assembleValidationAndroidTest
adb shell am instrument --user 10 -w -e reliabilityOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
adb shell am force-stop --user 10 com.nextdrive.pleos.validation
adb shell am instrument --user 10 -w -e coldOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
adb shell am instrument --user 10 -w -e migrationOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
adb shell am instrument --user 10 -w com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
adb shell am instrument --user 10 -w -e onboardingOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
```

reliabilityOnly에는 사전 백업의 validation 앱 내부 사본이 필요합니다. user10은 이번에 확인한 Android 사용자이며 다른 환경에서는 현재 사용자를 먼저 확인합니다. coldOnly는 직전 reliabilityOnly의 데이터를 지우지 않고 실행합니다.

## 남은 범위와 Gleo 처리

Gleo는 미연동입니다. 향후 사용자가 선택한 미삭제 기록의 ID/revision/본문 snapshot을 확인·동의한 뒤 전달하는 독립 경로를 검토합니다. 삭제된 기록이나 다른 사람의 기록을 무단 전송하지 않고, Action/Callback의 공식 접근 경로·인증·권한은 별도로 검증해야 합니다. 임의 호출이 가능하다고 가정하지 않으며 가짜 답변이나 상담 버튼은 추가하지 않았습니다.

사용자가 과거 겪은 모든 저장 실패, 디스크 고갈, 실제 주행 제한 전환, 실차, TalkBack/rotary, Release, 사용자 이해도는 미검증입니다. 알려진 offline Lint 캐시 부족은 재시도하지 않았고 Lint 성공으로 보고하지 않습니다. 다음에는 사용자가 본인의 기록으로 저장/수정/삭제를 확인하고, 동의한 과거 테스트 ID 정리와 남은 접근성 검증을 진행합니다.

작업 도중 Aside가 main에 `dc6eb046a638e55cd23ae8f21e8cfa050add2858`로 새 `docs/research/CARITDA_NAVIGATION_RECORD_GLEO_UX.md`를 추가했습니다. 이번 변경 대상과 충돌이 없음을 확인했고 이 커밋의 트리/부모를 이어 게시합니다. 연구 문서는 수정하지 않으며 전체 내비게이션/Gleo 개편은 별도 작업입니다.
