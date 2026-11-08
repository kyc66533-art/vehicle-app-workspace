# 실제 SDK 자동 스냅샷 P0 검증

2026-11-08, Asia/Seoul. 기준 main `51254c185ef686895cfa3f1e332f65d3b35e8d99` (Aside 자동 기록 연구). 이전 앱 `bb3af514994714b84eeffbe97f82e145be092d4d`의 모든 기능/기록과 연구를 보존합니다.

## 이번 실제 구현

- 일반 CARITDA(가칭)와 [TEST] CARITDA 검증 전용: appId/namespace 유지. 일반은 차량, 검증은 TEST 문자/플라스크 형상입니다. main/validation source set 각각 legacy·adaptive·round·monochrome 리소스와 검증 고정 배너를 사용합니다.
- 앱 가시·CarSafetyGate ALLOWED에서 진입당 단발 자동 읽기. 첫 안내가 열려 있으면 시작하지 않습니다. 화면 redraw/메뉴 이동은 조회하지 않으며 EntryReadState를 구성 변경에 유지합니다. 실제 배경→복귀는 새 진입, 수동 새로 불러오기는 별도 명시 요청입니다. 실패 때 자동 무한 retry/listener/백그라운드 polling은 없습니다. 서비스 응답/Capability와 실제 값을 구별합니다.
- 기존 공개 SDK2.3.2 SOC/거리/연료 읽기 및 CAR_ENERGY/CAR_DRIVE만 유지. 연결 중단/제한은 generation으로 늦은 callback을 거부하고 미완료 항목을 값 없는 연결 중단으로 기록합니다.
- 관측 DB v2는 기존 observations 열/값/행/ID를 그대로 두고 observation_identity UUID 표만 추가합니다. ObservationRef는 저장소UUID+행ID입니다. 앱/로컬 차량 범위/출처/카드 종류를 키로 별도 caritda-snapshots.db에 카드 한 개를 갱신합니다. 물리 차량 식별/VIN은 확인하지 않았습니다.
- raw commit→파생 snapshot 순서입니다. 원본은 수정하지 않습니다. 같은 값에도 원관측과 조회 배치의 불변 snapshot은 보존하지만 요약 카드/변화 건수는 늘리지 않습니다. 값/결과가 달라지면 변화 이력을 남깁니다. UI는 최초와 최근10개 변화이며 DB의 전체 원본은 남습니다. 과거 원관측도 출처/세션별로 replay하므로 일반 앱의 기존15%/1000000km 기록도 과거값으로 남습니다. 이를 validation 값과 합치거나 고장/수리 증거로 해석하지 않습니다.
- 신호별 최신 실패와 과거 성공·단위·앱 수신시각·출처를 구별합니다. 차량 측 측정시각은 '확인할 수 없음'입니다. 시각 표시는 기기의 America/Los_Angeles 설정을 따릅니다. PC KST와 다르며 OS 시간대를 바꾸지 않았습니다. 값0km는 유효하고 연료 미지원은0%가 아닙니다.
- 증상 입력에서 최근 snapshot을 선택하면 초안 ID에 선택을 보존합니다. snapshot 연결은 선택이며 issue commit 후 별도 links 트랜잭션으로 처리합니다. prepare/pending 링크가 있어도 문제 원문이 commit되지 않았다면 UI 연결로 취급하지 않습니다. 실패 시 증상 성공과 연결 실패를 구별하고 같은 issue ID 상세에서 확인 후 재시도합니다. DB 두 개의 원자성을 주장하지 않습니다. 연결은 불변 snapshot ID여서 후속 갱신·숨김이 과거 증상의 근거를 바꾸지 않습니다.
- 카드 숨김/숨긴 정보 다시 보기를 구현했습니다. 기존 논리 삭제·작성자 권한·네 상태·읽음은 바꾸지 않습니다. AutoCard/ConcernLink의 최소 sidecar만 구현했고 RuleSnapshot/DetectorState/회복 승인/low episode·알림 예산·CardEvent 자동 상태 엔진은 후속입니다. SYSTEM enum이나 가짜 Driver는 없습니다.

## 빌드·실행과 데이터 보호

JBR/Gradle8.11.1/API34/Build-Tools35.0.0/SDK2.3.2 기존 캐시의 offline assembleDebug+assembleValidation+assembleValidationAndroidTest 최종 성공(1m21s,93작업:13실행/80 up-to-date). 일반 APK SHA256 `bf85ab3e7b11d571b877ab55cb9b6000ee3b462c8d8fc0e8625c1e944b19e0a5`. 대기 연결의 선택 변경과 확정 연결 불변을 마지막으로 보강하고, 이 최종 앱으로 기존174개 및 신규 검사를 다시 실행했습니다.

ADB emulator-5554/user10에서 자동 검사는 validation만 실행했습니다. 신규 AM assertion35+실제 force-stop 뒤4+일반 DB 사본 보존2=41, 기존 NAV17+12/RR36+2/migration18/MVP24/관측6/UI27/ON32=174로 총215개 자동 검사입니다. 이는 AM36/AC/ON/NAV 인수기준 전체 통과나 일반 사용자·실차 검증이 아닙니다. 아이콘 정적 검사는 이 수에 더하지 않았습니다.

SDK 실제 validation 증거(합성 fixture와 별도): session `7b46d17e-9f09-4efa-81aa-76984fb7d162`, SOC100%/receivedAt1794123861706, ODOMETER0.0km/receivedAt1794123861716, 각각 READ/RECEIVED·PLEOS_EMULATOR_SDK. 연료 ERROR_NOT_SUPPORTED_PROPERTY/UNSUPPORTED·값 없음. 이번 일반 앱에서도 자동 생성 화면의100%/0km/실제 시각·출처를 확인했습니다. 운전자 확정·테스트 증상 추가·상태 변경 없이 일반 홈의22건을 확인했습니다.

일반 앱을 force-stop 후 databases/journal·shared_prefs·files를 `.tools/automatic-backup/20261108-161755/before.tar` 및 개별 DB로 바이너리 백업했습니다. tar SHA256 `4bb1454aac8cb17976e75a821216b8add97e9eb98b271426cbfbb082a84224ef`. validation 전후 일반 문제 DB `f284705f5845fd82749d31346776bb52255a03b053e184768f88acde827a8a35`, 관측 DB `dd89e12f70919ed6b997cffcd89dfc520f4eb3824b6eb70372375d64df6d4263`가 바이트 동일했습니다.

사본에서 관측 이전 실패 시 version/원본 전체 롤백, 성공 시 전체 scalar 보존, 반복 이전/재열기 identity 고정을 검증한 뒤 일반 APK를 install -r로 교체했습니다. 일반 COLD 실행 Status ok, 실제 자동 조회·홈·스냅샷·GNB를 확인했습니다. 일반 문제 DB는 설치 후에도 위와 동일합니다. 관측 DB는 v2 메타데이터와 실제 SDK 응답이 추가되므로 파일 해시 동일을 요구하지 않고, 사본에서 모든 기존 행/열/ID와 integrity_check=ok를 확인했습니다. 이후 일반 앱을 다시 실행했습니다. 개인 백업/화면/DB/로그/키/APK는 게시하지 않습니다. 과거 검증 기록을 지우지 않았습니다.

복구 원칙: 이전 실패 시 SQLiteOpenHelper 트랜잭션이 스키마와 user_version을 함께 롤백합니다. 운영에서 실패하면 앱/실패 사본/백업을 보존하고 독립 사본의 전진 수정부터 검증합니다. v2를 구버전 앱으로 강제 열거나 DB를 지우지 않으며, 실제 백업 덮어쓰기 복구는 별도 사용자 승인 후에만 합니다.

첫 빌드에서 Adaptive icon background drawable에 직접 색상 literal을 넣어 AAPT가 거부했습니다. @color 리소스로 수정 후 모든 빌드가 성공했습니다. 첫 AM 보고의 checks31에 증거 문자열1줄이 포함돼 있던 집계도 수정했고, 최종35는 PASS assertion만 셉니다. 현재 실행한 주요 검사 실패는 없습니다. 기존 Lint 캐시 부재/호스트/ADB 과거 실패 기록은 아래 기존 문서에서 보존하며 이번 성공으로 덮지 않습니다.

로컬 로그: app/build/validation/automatic-build-final-2.txt, automatic-final-automaticOnly.txt, automatic-final-automaticColdOnly.txt, automatic-final-navigationOnly.txt, automatic-final-navigationColdOnly.txt, automatic-final-reliabilityOnly.txt, automatic-final-coldOnly.txt, automatic-final-migrationOnly.txt, automatic-final-default.txt, automatic-final-onboardingOnly.txt, automatic-general-preservation.txt. 일반 대상 UI instrumentation은 실행하지 않았습니다.

## 최종 설치 중 시스템 중단과 복구

최종 BF85AB3E… APK install -r는 Success였지만 직후 사용자10이 중지되고 headless 사용자0만 BOOTING 상태여서 실행이 `Error type 3: Activity class ... does not exist`로 실패했습니다. `/sdcard` 조회도 `Input/output error`/`Transport endpoint is not connected`, UiAutomation 연결 timeout이 발생했습니다. crash 버퍼에는 같은 시점 SystemUI `DeadSystemException: The system died`가 있었습니다. 과거 Profile HAL panic도 있었지만 이번 원인으로 확정하지 않습니다.

기존 백업과 마지막 정상 상태의 before-final-patch.tar(SHA256 839b28dd20071098a6007a696eae88b8b103e9d52aead6368586b517cfe7123a)를 보존했습니다. 기존 AVD에 정상 `adb reboot`를 한 번 요청했고, 데이터 wipe/AVD 재생성·강제 사용자 전환·보안 설정 변경은 하지 않았습니다. 초기 wait-for-device 직후 activity service 미준비는 일시적으로 있었지만 이후 boot_completed1/user10 정상 복귀를 확인했습니다.

복구 후 최종 일반 APK COLD 실행 Status ok, 홈 SOC100%/0km와22건/TEST 없음 실제 캡처 성공. 설치 base.apk SHA256이 위 BF85AB3E… 빌드 파일과 동일함을 확인했습니다. 재부팅 뒤에도 일반 문제 DB는 F284705F…로 동일하며, 일반 관측 DB 사본의 모든 기존 scalar/행과 integrity_check가 다시 통과했습니다. 이 최종2개 검사를 AM41에 한 번만 셉니다. 일반 APK 재실행도 성공했습니다. 장치 시스템 중단을 CARITDA 고장 진단 성공이나 앱 코드 오류로 단정하지 않으며 재발 여부는 남습니다.

## Pleos GNB 진단

두 launcher entry는 일반 `com.nextdrive.pleos/.MainActivity`, 검증 `com.nextdrive.pleos.validation/com.nextdrive.pleos.MainActivity` 각1개입니다. user10/display0, 일반task1000210·validation task1000211에서 top-resumed와 focused package가 탭에 따라 맞게 바뀌었습니다. 초기 전환 도중 mCurrentFocus=null/이전 프레임도 관찰해 UI idle/TEST 배너 확인 후 다시 캡처했습니다.

안정 화면은 일반 차량 아이콘/TEST 없음과 검증 플라스크 아이콘/TEST 배너로 구별됐습니다. 최근 앱은 새로 실행된 것이 왼쪽으로 재정렬됐고 노란 표시도 왼쪽의 실제 활성 아이콘 아래에 있었습니다. 따라서 위치가 고정돼 보이는 현상은 이번에 재현했지만 '다른 패키지 실행/활성 아이콘 불일치' 지속 오류는 재현하지 못했습니다. 이전 현상의 원인을 앱 버그나 OS 버그로 확정하지 않습니다. pinned/recent 모든 조합과 시스템 선택 알고리즘은 미검증입니다. 로그/화면을 로컬에 보존했고 OS overlay/root/시스템 UI 변경은 하지 않았습니다. [공식 GNB 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/components/gnb)는 최근 앱2개·최신 왼쪽 배치를 설명합니다.

## AM01~AM36 판정

통과14/부분10/미검증1/이번 범위 밖11입니다. 아래 표가 집계보다 우선하며 실제 자동 assertion41개와 별개입니다. 사용자 최신 지시에 따라 Aside의 low SOC P0 제안은 P1로 보류했습니다.

| 기준 | 판정 | 근거·남은 범위 |
|---|---|---|
| AM01 | 통과 | 시작 HEAD/로컬 SHA 일치, user10 백업, 관측 v1 사본 전체 행/롤백/반복 이전, 운영 문제 DB 바이트 불변. |
| AM02 | 통과 | 실제 진입 1배치, 화면 전환/1초 표시 tick/Activity 재생성 중 추가 조회 없음. 동시 요청 gate. |
| AM03 | 통과 | validation 및 일반 앱 SDK SOC100%/거리0km. 원본·시각·출처 저장, 실차 아님. |
| AM04 | 부분 | 실제 연료 UNSUPPORTED/값 없음, 기존 실패 표현 회귀. 실제 서비스 권한/area 오류 강제 재현은 하지 않음. |
| AM05 | 부분 | NaN/음수/SOC>100 파생 검증과 기존 실패 원본/표현 검사. 실제 SDK timeout·잘못된 값 응답 주입은 하지 않음. |
| AM06 | 통과 | 격리 사본의 최신 SOC 실패와 과거99%를 다른 근거로 보존. 카드와 원문을 새 정상으로 덮지 않음. |
| AM07 | 부분 | 측정시각 미제공 명시, 기존 미래/오래됨 표시 회귀. OS 시계 역행 실제 변경은 하지 않음. |
| AM08 | 통과 | 같은 값100배치→원본300행/카드1개/변화0건, 실제 force-stop 복원과 고정 연결 유지. |
| AM09 | 이번 범위 밖 | 사용자 최신 P0/P1 구분에 따라 low SOC 설정·규칙을 구현하지 않음. 예시값/가짜 카드는 없음. |
| AM10 | 이번 범위 밖 | low episode/hysteresis/재무장은 P1. 스냅샷은 고장/편의 임계값 판정이 아님. |
| AM11 | 이번 범위 밖 | 규칙 버전/알림 예산 엔진 없음. 이름/날짜를 스냅샷 카드 식별자로 사용하지 않음. |
| AM12 | 부분 | 앱 패키지+관측 저장소UUID+로컬 차량 범위+출처 분리 및 두 저장소/환경 시험. 실제 차량 식별/VIN·물리 차량 교체는 미확인. |
| AM13 | 통과 | 신호별 최신 시도/과거 유효값·원래 시각을 분리. SOC 실패가 거리 성공시각을 공유하지 않음. |
| AM14 | 부분 | 증상 원문/발견시각 불변, 불변 스냅샷 선택과 시각·인과 아님 UI 확인. 과거 날짜를 picker로 넣는 신규 전체 과제는 미실행. |
| AM15 | 통과 | 실제 validation 화면에서 링크 INSERT 실패→증상 commit/실패 안내/같은 ID의 확인창 재시도. 중복 증상 없음. |
| AM16 | 통과 | 카드 숨김/다시 표시·연결 후에도 원문/네 상태/작성자/이벤트/읽음 불변. 일반 문제 DB 해시 동일. |
| AM17 | 이번 범위 밖 | 상태 변경 제안/승인 엔진 없음. state token/evidenceEpoch 승인 경합은 후속 설계로 유지. |
| AM18 | 부분 | 원관측 commit 뒤 파생 INSERT 실패·원본 유지·재실행 replay 복구 검증. 승인 watermark 엔진/실제 디스크 고갈은 미구현·미검증. |
| AM19 | 통과 | 기존 EventKind/actor FK 불변. 자동 데이터는 별도 SnapshotStore, 가짜 SYSTEM 운전자/상태 이벤트 없음. |
| AM20 | 부분 | 기존 본인 미해결 논리 삭제와 카드 숨김/재표시 통과. low 안내 설정·오탐 분류·휴지통/영구 파기는 없음. |
| AM21 | 부분 | 두 APK label/package/launcher, 실제 차량/플라스크 아이콘과 고정 TEST 배너 확인. legacy/adaptive/round/monochrome XML 형상 분리 정적 검사. 모든 OEM mask/실제 themed launcher는 미검증. |
| AM22 | 통과 | 기존174 assertion 재실행, validation package guard 유지, 일반 앱에 테스트 문제 추가 없음. 운영 대상 UI 계측은 실행하지 않음. |
| AM23 | 통과 | 실제 onStop·release·force-stop 이후 추가 polling 없음. 종료 시 미완료 신호는 값 없는 중단 실패로 보존. 연결상실 중 계속 관측한다고 표시하지 않음. |
| AM24 | 통과 | 새 API/경고/수리 완료 판정·회복 버튼 없음. 실제 수신값과 '고장 진단이 아니에요' 구분. |
| AM25 | 이번 범위 밖 | listener 등록·연속 callback 기능을 구현하지 않음. |
| AM26 | 이번 범위 밖 | 모델별 수동 경고등 설명서 기능을 이번에 추가하지 않음. |
| AM27 | 통과 | 차량값/자동 정보/내 증상 중심 홈, 기존 이름 선택·다중 운전자·초안/권한·인계 회귀 및 운영 원본 보존. |
| AM28 | 부분 | 기존200 렌더 조합+낮은 높이6조합 회귀, 실제 GNB 전환/형상 확인. 신규 스냅샷 상세의 전 행렬·실제 themed/OS 분할·TalkBack은 남음. |
| AM29 | 미검증 | 일반/고령 사용자 독립 설명 과제를 수행하지 않음. 자동 검사를 사용성 성공으로 대체하지 않음. |
| AM30 | 통과 | 실제 저 SOC를 재현하지 않았고 low 카드/설정/시연을 넣지 않음. 현재100%를 낮은 충전량 감지로 설명하지 않음. |
| AM31 | 이번 범위 밖 | 폰 pairing/서버/토큰 생성·전송 없음. |
| AM32 | 이번 범위 밖 | concern 전역 최대2회 알림은 Aside 후속 설계 보존, 실행 엔진 없음. |
| AM33 | 이번 범위 밖 | 원격 알림 retry/outbox 없음. |
| AM34 | 이번 범위 밖 | FCM/실폰 표시/중복 시험 없음. |
| AM35 | 이번 범위 밖 | FCM 수락/표시/읽음 기능 없음. |
| AM36 | 부분 | 검증 소스/README/개인정보 제외 점검. Debug만 검증, 서명 Release·기획서 서명·심사 계정 접근은 미완료. Private 저장소 유지. |

## 시연·제출 준비와 사용 순서

1. 일반 차량 모양 CARITDA를 실행하세요. TEST 플라스크는 검증 앱입니다. 이름 확정 없이도 허용 진입의 차량값 조회는 가능합니다.
2. 본인 이름→운전 시작으로 기록 이름을 명시 확정하세요. 홈에서100%/0km 같은 예시 고정값이 아닌 이번 실제 응답을 확인하세요.
3. 자동으로 기록된 차량 정보에서 항목별 수신시각·출처·차량 측 시각 미제공을 확인하세요. 연결이 안 되면 차량 정보에서 명시 새로 조회할 수 있습니다.
4. 문제 남기기→소음/진동·상황 입력→최근 차량 정보 연결(선택)→이 차량 앱에 저장을 누르세요. 발견시각의 값/인과관계가 아니라는 안내를 확인하세요.
5. 같은 기록 상세에서 점검 메모·네 수동 상태·정비 상담용 요약을 확인하세요. 연결만 실패하면 같은 기록의 차량 정보 연결 다시 시도를 사용하세요.
6. 앱을 다시 열어 증상/이력/선택한 과거 snapshot 연결이 유지되는지 확인하세요. 새 진입은 현재 카드만 갱신하며 과거 연결을 바꾸지 않습니다. 원본/앱 데이터 초기화는 필요 없습니다.

이번 시연 가능 범위는 실제 에뮬레이터 단발 자동 조회→원본/스냅샷 기록→사람 증상과 선택 연결→점검/수동 상태→재실행 복원입니다. 낮은 SOC·listener·자동 회복·고장 진단·Gleo·폰/FCM·정비소/상시 마이크는 구현하지 않았습니다. 데이터 없음/오래됨은 정상 판정이 아닙니다.

Debug APK는 `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk`이며 Release가 아닙니다. release 서명 설정/키 준비·Release 실행 검증은 미완료입니다. 사용자가 기존 release 키를 보유했다면 Android Studio Build → Generate Signed App Bundle / APK → APK에서 보안 저장소의 키를 선택해 별도 검증해야 합니다. 키가 없으면 안전한 보관/생성 승인을 다음 단계에서 정하며 키/비밀번호를 GitHub에 올리지 않습니다. 이번에 새 비밀키·인증정보를 만들지 않았습니다.

저장소는 API 확인상 Private입니다. 공개 전환/초대는 하지 않았습니다. 제출 시 심사 계정의 접근, 서명한 기획서·동의서, README의 실제 Release 위치/실행 안내가 남아 있습니다. [공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)의 release APK·비공개 접근 요건을 확인했습니다. 현재 소스 게시가 공모전 제출 완료를 뜻하지 않습니다.

[개발 기록](CODEX_PROGRESS.md) · [기존 NAV](NAV_ACCEPTANCE.md) · [기존 기록 신뢰성](RECORD_RELIABILITY.md) · [기존 ON](ONBOARDING_ACCEPTANCE.md) · [Aside 자동 기록 연구](research/CARITDA_AUTOMATIC_RECORDING_MVP_RESEARCH.md). 연구 문서는 원격에 있으며 로컬에 없는 연구도 게시 트리에서 그대로 보존합니다.
