# 홈 중심 UX·기록 탐색 구현과 검증 상태

2026-11-08, Asia/Seoul. 기준 main `aee62bf2548470ee11bfd1e5154639e2ce79e1e5`와 로컬 필수 파일31개 SHA가 일치했습니다. 새 Aside 자동화 연구가 존재함을 확인하고 두 연구·현재 v3 코드·기존 AC/ON/RR 기록을 읽었습니다. 연구 파일은 수정하지 않았습니다.

## 현재 결과 — 로컬 구현·빌드 완료, 런타임 대기

앱·validation·계측 APK는 빌드했지만 **이번 NAV/AC/ON/RR 실행은 아직 하지 못했습니다.** 시작 시 Studio/에뮬레이터가 실행 중이지 않았고 ADB 서버 연결이 거부됐습니다. ADB 기본 start-server도 `could not read ok from ADB Server`로 실패했습니다. 제한을 우회하거나 AVD/SDK를 초기화하지 않았습니다. 사용자에게 기존 Pleos AVD 실행을 요청했습니다.

새 코드와 계측은 사용자 PC 프로젝트에만 보존합니다. 런타임 검증 전에는 앱 소스를 main에 게시하거나 일반 앱에 교체 설치하지 않습니다. 현재 일반 앱이 새 홈 UI로 실행됐다고 주장하지 않습니다. 문서 변경과 실행 검증 대기 사실은 GitHub 연결 도구로 공유할 수 있습니다.

최종 offline assembleDebug/assembleValidation/assembleValidationAndroidTest는 BUILD SUCCESSFUL in 38s, exit0(93작업:4실행/89 up-to-date)입니다. 일반 APK SHA256은 `53383639ce3a01e76f749880e23ebd9bb27991d142ee9845ae4742a3c2d539ca`입니다. 이는 빌드 증거이며 설치·실행·검사 통과 증거가 아닙니다. 기존 JBR/Gradle8.11.1/API34/Build-Tools35.0.0/SDK2.3.2 환경과 캐시를 사용했습니다.

## 로컬에 구현한 동작

- 본문 ScrollView 밖 최상단에 앱 내부 홈을 고정했습니다. 여유 있는 창은 홈/기록/차량 정보, 작은 창·큰 글꼴·입력 화면은 홈/메뉴입니다. 차량 OS 홈 Intent는 호출하지 않습니다. 홈은 중첩 이력을 정리하고, 이전은 직전 화면으로 돌아갑니다. 실제 홈 tag는 nav_HOME, 이전은 back으로 구분했습니다.
- 홈에 전체 문제 기록/내가 작성한 문제/문제 남기기/차량 정보/사용법을 제공합니다. 미확정 상태에서 내 기록을 누르면 이름 선택·명시 운전 시작 뒤 내 기록으로 연결합니다. 홈 자체는 운전자를 확정하지 않습니다.
- 네 필터에 로컬 검색을 결합했습니다. 카테고리의 한국어 이름, 미리보기100자 밖을 포함한 전체 본문, 추가 메모를 NFC·대소문자 구분 없이 검색합니다. 공백으로 나눈 단어는 모두 포함되어야 합니다. 실제 원문/DB는 정규화하거나 수정하지 않습니다.
- 적용 필터/검색어/결과 수/전체 건수와 조건 초기화를 표시합니다. 상세→이전은 같은 검색·필터·가능한 픽셀 위치로 돌아갑니다. 홈의 전체/내 작성 바로가기는 의도적으로 기존 검색을 지웁니다. 메뉴의 기록은 현재 조건을 유지합니다. 조건 불일치와 실제 빈 기록을 다르게 설명합니다.
- 수정 저장 후 같은 기록 상세로 돌아가고 이전 목록 조건을 유지합니다. 수정으로 기존 검색에서 빠지면 이유와 전체 기록 진입을 제공합니다. 수정 취소는 원문을 건드리지 않고 상세로 돌아갑니다. 변경 없는 수정 저장은 초안만 끝내고 가짜 수정 이력/revision을 추가하지 않습니다.
- 새 기록/수정 초안의 홈·이전 이동 전에 보존 결과를 확인합니다. SQLite 초안 쓰기에 실패하면 현재 입력 화면에 남고 보존 실패를 안내합니다. OS 중단/프로세스 종료 중 저장 실패까지 보존 성공으로 보장하지 않습니다.
- 네 수동 상태와 기존 이벤트 저장을 유지했습니다. 점검 완료/해결 확인의 의미를 상태 선택 화면에서 구분합니다. revision은 수정·재발 모두의 버전이므로 내용 버전과 RECURRENCE로 센 재발 횟수를 따로 표시합니다. DB 스키마는 v3 그대로입니다.
- 삭제는 기존 논리 삭제입니다. Aside의 휴지통 복원 제안은 이번에 구현하지 않았으며 복원/영구 파기 버튼이나 성공 문구를 추가하지 않습니다. 후속에는 별도 승인된 비파괴 복원·접근 권한·백업/원문 보존 정책부터 검증해야 합니다.

## 검사 준비 및 현재 실행 범위

NavigationChecks는 validation 앱만 허용합니다. N01~17은 홈/필터/카테고리·메모·긴 본문 검색/0건/초기화/상세 복귀/새·수정 초안을 준비합니다. 별도 force-stop 뒤 N18~28은 수정 초안 복원/취소/변경 없음/검색 불일치/네 수동 상태·전후 actor/time/초안 쓰기 실패/UNKNOWN/낮은 높이를 검사하도록 작성했습니다. **28개는 준비한 assertion 수이며 통과 수가 아닙니다.**

기존 Mvp/Onboarding 검사의 잘못된 home=뒤로 tag만 back으로 고쳤고 테스트의 본래 의미는 유지했습니다. 기존 AC/ON/RR의 과거 통과는 보존하지만 이번 코드의 성공으로 재사용하지 않습니다. 일반 사용자 이해도·TalkBack/키보드/rotary·실제 OS 분할 창과 IME는 자동 클릭과 별도 검증입니다.

보조 호스트 Java 검색 실험도 별도로 시도했으나 javac classpath/파일 realPath AccessDeniedException 때문에 실행하지 못했습니다. 이를 검색 통과로 세지 않습니다. 공식 Gradle APK 컴파일 성공과 이 보조 실험 실패를 구분합니다. 로그/실험 파일은 build/validation에만 둡니다.

## Aside NAV01~NAV28 대응표

| 기준 | 현재 상태 | 구현·남은 범위 |
|---|---|---|
| NAV01 | 조사 전용/해당 없음 | 연구 파일 단독 게시 조건은 Aside 작업의 기준. 이번은 사용자 승인한 앱 UX 작업이며 연구는 무수정 |
| NAV02 | 구현·실행 미검증 | 고정 앱 홈, modal 취소→홈. OS 홈/종료 호출 없음 |
| NAV03 | 구현·부분 | 홈/이전/사용법 복귀 분리, 조건·위치 복원 코드. 실제 focus/장비 검증 남음 |
| NAV04 | 구현·실행 미검증 | 새/수정 초안 보존 실패 시 이동 중단, full process 복원 검사 준비 |
| NAV05 | 기존 기능·재검증 대기 | 작성자 ID/확정·CarSafetyGate 재검사 유지 |
| NAV06 | 구현·실행 미검증 | 전체/내 작성 ID 필터와 미확정 이름 선택 후 목표 복귀 |
| NAV07 | 기존+개선·재검증 대기 | 같은 ID/중복 보호·수정 성공 안내·조건 유지 |
| NAV08 | 구현·실행 미검증 | 취소/변경 없음/충돌 보호·입력 유지 |
| NAV09 | 부분 | 기존 revision/읽음 유지, 재발 횟수 표시 분리. 독립 DB contentVersion 이전은 보류 |
| NAV10 | 기존 기능·재검증 대기 | 삭제 modal 취소/뒤로·OS 전환, 이번 운영 삭제 없음 |
| NAV11 | 부분 | 논리 삭제/정상 조회 제외 유지. 복원 UI는 미구현 |
| NAV12 | 코드 보존·실측 대기 | SDK/관측 저장은 무수정. 사용자 DB 전후 실측 비교는 연결 후 |
| NAV13 | 기존 보호·부분 | 삭제/기준 revision 검사 유지. 모든 stale 화면 종합 과제 미실행 |
| NAV14 | 정적 검토 | 새 Gleo 호출/가짜 AI 버튼 없음 |
| NAV15 | 범위 밖 | Gleo Action 수신 미연동, parameter 시험 미실행 |
| NAV16 | 범위 밖 | Gleo Callback/시간 제한 실제 시험 미실행 |
| NAV17 | 범위 밖 | 새 외부 전달/동의 경로 없음 |
| NAV18 | 범위 밖 | 새 AI payload/답변·자동 판단 없음 |
| NAV19 | 구현·실행 미검증 | 고정 바를 ScrollView와 분리, 긴 목록 검사 준비 |
| NAV20 | 구현·실행 미검증 | 전체 본문·메모·카테고리, 조건·0건·초기화 검사 준비 |
| NAV21 | 구현·실행 미검증 | 상세 복귀/수정 후 조건 유지·불일치 안내. 위치는 내용 변화 시 best effort |
| NAV22 | 실행 미검증 | 기존200개와 신규 낮은 높이6개 조합 검사 준비. 실제 OS/IME 별도 |
| NAV23 | 미검증 | TalkBack/물리 키보드/rotary 과제 미실행 |
| NAV24 | 재검증 대기 | 새 기록·실제 저장·재시작·수정 RR 재실행 필요 |
| NAV25 | 범위 밖/기존 요약 유지 | 선택1건 로컬 정비 상담만 유지. 새 상담 준비/AI 기능 미구현 |
| NAV26 | 구현·실행 미검증 | 홈/사용법 반복과 초안 보존 검사 준비 |
| NAV27 | 미검증 | 일반·고령 사용자 독립 과제 미실행 |
| NAV28 | 정적 검토 | 외부 에셋/가짜 API/미확인 완료 주장 없음. 공유 안전 검사 후 문서만 게시 |

## 런타임 연결 후 이어갈 순서

1. 일반 Android Studio의 Device Manager에서 기존 Pleos_Connect_v2.1.0을 부팅합니다. AVD/앱 데이터는 지우지 않습니다.
2. 정상 Windows 환경의 ADB 서버와 실제 serial/current Android user를 확인합니다. 운영 DB/journal·설정·파일을 새 개인 백업에 보존하고 문제/관측 해시를 기준으로 잡습니다.
3. validation 앱·계측 APK만 교체 설치하고 NAV 두 단계와 기존 AC 관련/MVP/ON/RR/이전 검사를 실행합니다. 문제 발견 시 수정→빌드→관련 검사 재실행합니다.
4. 실제 SDK SOC/거리/연료 미지원과 원관측 보존, 일반 앱 DB 불변을 확인합니다. 그 전 이번 수신 성공이라고 표시하지 않습니다.
5. 검증 통과 후 일반 앱을 install -r로 교체하고 기존 DB 전후 보존/실제 화면을 확인합니다. 그 뒤 검증한 앱 소스만 최신 main에 GitHub 연결 도구로 게시합니다.

```text
gradlew :app:assembleDebug :app:assembleValidation :app:assembleValidationAndroidTest
adb shell am instrument --user <현재사용자> -w -e navigationOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
adb shell am force-stop --user <현재사용자> com.nextdrive.pleos.validation
adb shell am instrument --user <현재사용자> -w -e navigationColdOnly true com.nextdrive.pleos.validation.test/com.nextdrive.pleos.MvpScenarioInstrumentation
```

navigationColdOnly 직전에는 validation fixture를 다시 초기화하지 않습니다. 기존 RR coldOnly와도 다른 모드입니다. 모든 자동화는 validation 대상만 사용합니다. 과거 운영 대상 runner 실행 경로를 되살리지 않습니다.

## 자동화 설계와의 차이

[자동화 기반 설계](AUTOMATION_FOUNDATION.md)는 문서만 추가했습니다. 새로운 회복 신호/엔진/제안/상태 자동 변경은 없습니다. 새 DB·권한·SDK API도 없습니다. Aside REC 기준은 후속 구현 때 별도로 실행해야 하며 이번 NAV를 회복 성공으로 보고하지 않습니다.
