# 음성 증상 → Gemini 설명 → TTS → 운전자 인계

작업 기준: main `f59abc15bcfb26f4d784b696bd2c97cc683eee2f`, 2026-11-11 Asia/Seoul. 기존 일반 앱 한국어 STT·신규 Gemini1회·한국어 TTS 사용자 성공은 [독립 검증](GEMINI_INDEPENDENT_ACCEPTANCE.md)에 그대로 보존합니다. 이번 개발의 신규 실모델 성공 근거로 재사용하지 않습니다.

## 채택한 사용자 흐름

1. 기존 운전자로 운전 시작 → 증상 남기기 → 음성 입력.
2. 기존 ON_DEVICE/request/Locale.KOREA의 final과 실제 ended 후 입력란에 자동 반영. 문장을 직접 확인·수정합니다.
3. **저장하고 AI 설명**을 누르면 기존 SQLite commit·읽기 확인을 완료한 다음, 그 실제 issue ID로 기존 AI 화면을 엽니다. 실패하면 작성 화면과 원 작성자 초안을 유지합니다.
4. 증상은 미리 채워지고 전송 요약은 펼쳐집니다. 필요한 경우 전송 사본에서 개인정보를 지운 뒤 **새로 설명 생성**을 눌러야 외부 분석을 요청합니다. 기존 동의·Firebase 로그인·기기 등록을 재사용합니다.
5. 검증된 결과는 가능한 원인/추가 확인/정비소 점검의 한국어 문단으로 표시합니다. 새 요청/진행 중 요청 회수/기존 저장 결과와 설명에 실제 사용한 사본을 구분합니다.
6. **이 설명 음성으로 듣기**는 준비된 결과만 기존 기기 내 TTS로 읽습니다. 전체 문장을 짧은 구절로 나누어 전달하고 완료 후 다음 구절을 읽습니다. 중단·안전 제한·화면 이탈은 나머지 구절과 늦은 callback을 차단합니다. Gemini 재호출은 없습니다.
7. 이전 화면은 저장된 문제 상세입니다. 다음 운전자는 기존 인계 필요 목록에서 같은 기록을 읽고 명시적으로 읽음/점검 메모/수동 상태를 남깁니다. AI는 원문·작성자·revision·상태·인계 이벤트를 쓰지 않습니다.

기존 `저장 → 상세의 AI → 새로 생성`에서 `저장하고 AI 설명 → 새로 생성`으로 한 단계를 줄였습니다. 일반 저장 버튼도 유지합니다. 저장 없이 AI를 선택하는 버튼으로 오인하지 않도록 저장 시점을 버튼과 안내에 명시했습니다.

## 미저장 분석을 추가하지 않은 이유

현재 Worker의 `caritda-issue-v1`은 `source=USER_SELECTED_RECORD`를 엄격히 검사하고 category/situation만 허용합니다. 앱의 암호화 job/pending/history는 실제 issue ID·내용 revision에 연결됩니다. 미저장 초안을 같은 저장 기록으로 가장하거나 이후 두 저장소의 cache 귀속을 원자적으로 옮긴다고 가정하지 않았습니다. 이번에는 계약/서버/동의 범위를 바꾸지 않는 사용자 허용 대안인 **명시적 저장 후 즉시 분석 연결**을 채택했습니다.

임시 가짜 issue·기존 ID 재사용·분석 성공에 따른 자동 문제 저장은 없습니다. 음성/직접 편집 초안은 기존 drafts 저장을 유지하고, 저장 성공 시에만 소비합니다. 별도의 AI 전송 사본 편집은 동의한 계정의 기존 암호화 job에 선택 필드로 보존하며 문제 원문을 수정하지 않습니다. pending에는 이미 고정된 payload를 사용합니다.

## 구현 경계

- `MainActivity.kt`: 저장 후 AI 진입, STT 진행 중 두 저장 동작 차단, 초안 보존 실패 시 저장 중단, validation DB 계열 전달.
- `PortableAiActivity.kt`: 저장 성공/전송 사본/검증된 결과/로컬 차량 참고/기존 결과 재진입·TTS 연결.
- `ai/IssueAnalysisService.kt`: UI와 Gleo가 공유하는 저장 기록 분석 유스케이스. 현재 기록 버전 재확인, frozen pending/검증/결과 출처, 동시 진입 차단. 문제 writer와 차량 조회는 없습니다.
- `ai/auth/PortableSession.kt`: 기존 FirebaseSessionProvider·DeviceKeyStore·PortableAiClient에 위임하는 인증/전송 경계. 외부 Intent로 대체할 수 없습니다. 계측만 reflection으로 독립 fixture를 주입합니다.
- `integration/ExplanationPlayback.kt`, `SpeechOutput.kt`: 실제 표시 결과의 전체 TTS 전달, 완료/실패·중단 경계. 기존 ON_DEVICE와 Android 기기 내 한국어 fallback을 유지합니다.
- `integration/GleoIssueAnalysis.kt`: 기존 Action의 새 분석 실행에 위 공유 서비스를 재사용합니다. 세 Action·Schema·신뢰 확인·callback과 응답 예산은 유지합니다.
- `IntegratedFlowChecks.kt`, `MvpScenarioInstrumentation.kt`: 기본 prepare 앞의 고유 validation fixture 분기. 일반 앱/기존 validation DB에 시험 문제를 추가하지 않습니다.

Vehicle SDK2.3.2·STT/TTS3.3.1·Navi3.0.5·Fused1.1.3·Maven.security 필터, DB 스키마·Firebase 설정·Worker·Secret·BUDGET·할당량 코드는 변경하지 않았습니다. 모델ON과 일5/월100·서비스 월300 정책을 유지하며 이번 배포/실모델 요청은0회입니다.

## 차량 정보와 AI

이미 로컬에 commit된 SnapshotStore의 연결 근거와 최근 참고 스냅샷을 구분해 AI 화면에서 보여줍니다. 값/단위/앱 조회시각/출처를 표시하고 수신시각·오래됨·최근 실패/미지원은 상세에서 기존 VehiclePresentation으로 표현합니다. 추가 SDK 조회·polling·권한·자동 차량 전송은 없습니다.

차량값은 AI payload 입력으로 전달하지 않습니다. 증상 당시 측정/동일 시점 상관관계/원인·고장 확정도 주장하지 않습니다. 실제 차량 식별·ECU 측정시각은 미확인입니다. 새로운 센서 전송은 별도 동의·계약 변경이 필요한 후속 범위입니다.

## 이번 검사 결과

2026-11-11 17:31 +09:00 갱신. Windows11/JBR21/Gradle8.11.1/Kotlin2.2.21/API34, 기존 Pleos_Connect_v2.1.0/emulator-5554/user10에서 실행했습니다.

```text
:app:assembleDebug :app:assembleValidation :app:assembleValidationAndroidTest
:app:lintDebug :app:testDebugUnitTest
BUILD SUCCESSFUL (2m34s, 107 tasks, exit 0)
```

Lint 오류0/경고56. UnitTest는 NO-SOURCE이며 통과 개수를 만들지 않습니다. 이후 실제 SDK 문맥 검사를 추가한 계측 APK만 재빌드했고 1m14s/44 tasks/exit0입니다. 일반/validation 앱 소스와 APK는 그대로입니다. 기존 읽기 전용 의존성 캐시와 오프라인 빌드를 사용했고 새 다운로드/설치/전역 설정 변경은 없습니다. 최초 캐시 경로 누락으로 플러그인 해석이 실패한 기록, Kotlin daemon 권한 오류 후 지원되는 in-process 컴파일 전환 기록을 보존했습니다.

| validation 조기 분기 | assertion | 근거 |
| --- | ---: | --- |
| integratedFlowOnly | 35 | 실제 MainActivity→PortableAiActivity/SQLite/암호화 store, STT/provider/TTS 경계는 fixture. 저장 실패·편집·명시적 저장·B 읽음/점검 메모·UNKNOWN·동시 진입·TTS 전체 전달/취소/실패 |
| integratedFlowColdOnly | 4 | validation force-stop 후 새 계측 프로세스에서 원문/인계·점검 이력/암호화 결과·전송 사본·사용량 복원, API 없이 캐시 검증 |
| integratedTtsOnly | 2 | 실제 Pleos ON_DEVICE 한국어 TTS의 여러 구절 완료 callback. 준비된 시험 문구이며 새 Gemini 응답/물리 청취 확인과 구분 |
| integratedVehicleOnly | 4 | 실제 SDK SOC·거리 원관측 commit→선택 fixture snapshot 연결→AI 화면 동시 표시. provider는 fixture, 전송 body는 여전히 category/situation만 |
| speechDraftUiOnly | 21 | 기존 초안·final/ended·취소·Activity 재생성·저장·인계 UI 회귀 |
| issueAiOnly / issueAiColdOnly | 56 / 4 | 기존 계약·증거·인증·암호화 캐시·할당량·pending·프로세스 복원 |
| sttSessionOnly / sttAutomaticUiOnly | 40 / 6 | 기존 합성 상태·실제 SDK 준비/취소/재입력 UI |
| gleoOnly | 37 | 세 Action·Schema·신뢰 차단·callback·분석 정책 |
| coreDatabaseOnly / integrationOnly | 19 / 33 | SQLite/관측·안전 제한·기존 통합 계약 |
| integrationLiveOnly | 7 | 실제 공개 Vehicle20종 결과(12수신/8미지원), SOC100%/거리0.0km. Navi 상태·Fused last-known·TTS callback은 별도 메타데이터 |
| portableAiOnly / portableAiUiOnly | 26 / 4 | 기존 SOC 분석·로그인 화면 계약 회귀 |
| 합계 | **298** | 신규45 + 영향 범위253, 반복 실행 중복 합산 없음 |

각 분기의 PASS marker와 `INSTRUMENTATION_CODE: -1`을 확인했습니다. 기존 전체313/281 assertion 또는 이전 한국어3회를 이번 검사 수로 재사용하지 않습니다. 기본 destructive prepare와 일반 앱 UI 자동화는 실행하지 않았습니다. 첫 fixture 주입 시점 오류와 정상 이벤트의 updatedAt을 전체 issue 동일 비교에 포함한 검사 오류는 수정 후 재실행했습니다. 오류 기록을 성공으로 바꾸지 않았습니다.

### 일반 앱 설치·실행과 데이터 보존

기존 서명과 새 Debug의 인증서를 대조한 뒤 `install -r --user 10` 교체에 성공했습니다. 설치된 APK를 다시 가져와 빌드 산출물과 바이트 해시 일치를 확인했습니다.

- 일반 APK SHA256: `f33044d156269f219c689efa0bd46ff826bdc06b3183b9554955872cc5966f40`.
- 일반 MainActivity COLD 실행 `Status: ok`, 실제 resumed 확인. 일반 앱의 버튼/운전자/문제 입력을 자동 조작하지 않았습니다. 새 작성→분석 조작은 동일 소스 validation에서 검사했습니다.
- 변경 전/설치 직후/실행 후 일반·validation의 databases/files/shared_prefs 전체를 로컬 비공개 백업했습니다. 이전 APK도 보존합니다. 데이터 검증을 위해 최종 백업 시 앱은 종료했습니다.
- 기존 **226DB(일반42/validation184)**의 스키마·user_version·모든 원본 행·integrity_check·foreign_key_check가 전부 통과했습니다. 일반 원본1206행과 validation 원본9380행이 보존됐고 기존 DB의 수정/삭제 행은0입니다.
- 일반 현재 **24문제/96이벤트/10운전자/1초안** 그대로입니다. 원래 사용자 음성 초안을 소비하거나 시험 문제를 추가하지 않았습니다. 일반 foreground 진입에 따른 원관측300→320/스냅샷77→78/분석 인사이트152→154의23행만 추가됐습니다.
- 설치 직후 일반 files/prefs16개 전부 byte-identical. 실행 후 Firebase·AI 결과/사용량·동의·개인 파일을 포함한15개는 그대로이고 `files/profileInstalled` 표시 파일1개만 바뀌었습니다. AI 캐시·등록을 초기화하지 않았습니다.
- validation은 기존184DB/9380행이 그대로이며 새 고유 fixture28DB만 추가됐습니다(최종212DB). 기존 prefs/files120개는 동일하고 Firebase heartbeat/계측 cold fixture 포인터/profileInstalled 3개만 정상 시험 과정에서 갱신됐습니다. 기본 validation DB는 문제0/이벤트0/운전자2/초안0으로 그대로입니다.

실제 신규 Gemini 요청·Cloudflare 재배포·Secret/정책/모델 상태 변경은 **0회**입니다. 직전 사용자 확인의 모델ON·TEST1/정식6/잔여4와 기존 동의·인증·캐시를 보존했습니다. 이 숫자를 이번 새 서버 조회로 확인한 값으로 표시하지 않습니다. 공개 health는 이번 웹 도구에서 접근할 수 없었으며 인증된 운영 status/원장 조회나 운영 재개 실행기는 사용하지 않았습니다.

### 사용자가 직접 확인할 마지막 항목

일반 CARITDA에서 기존 운전자를 확인한 뒤 작성 화면의 **저장하고 AI 설명** 문구/배치와 저장 후 전송 요약을 확인해 주세요. 기존 AI 설명은 새 요청 없이 다시 보고 들을 수 있습니다. 이번 새 UI의 실제 사용자 발화→신규 Gemini→실제 청취 전체 왕복은 아직 미검증이며 이번에 과금 시험을 요청하거나 자동 실행하지 않았습니다. 이미 성공한 기존 일반 앱 STT/실모델1회/TTS 사실은 별도로 유지합니다.

외부 Gleo 승인·자연어 왕복, 이번 UI에서의 신규 실제 Gemini 요청, 실차·PC B·Release 서명/제출은 이번 로컬 검사로 통과 처리하지 않습니다. 검증된 명시적 파일만 기존 main을 부모로 **GitHub 연결 도구를 통한 게시**를 하며, 최종 커밋·원격 SHA 확인은 완료 응답에 기록합니다. 로컬 Git push와 구분합니다.
