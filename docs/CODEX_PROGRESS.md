# Codex 개발 진행 기록

## 2026-11-11 19:10 +09:00 — 위치·내비·DriveCheck 보조 실제 통합

- 시작 main `80f76b1bdac9044f649eba01e727ef4cee084b48`, 로컬209개 일치/원격 전용 연구18개. 작업 중 main `d0e7281fea775d306dc2cf7d9f32e30a70d79ce8`의 연구8개 추가만 확인했다. 대상 source/blob는 불변이라 기존 규칙의 Aside-only 예외로 최신 tree/parent를 이어 보존한다. 로컬 Git/연구 수정 없음.
- 실제 Kotlin21개 변경(일반11/validation3/계측7). 위치는 매번 설명·동의/권한·실제 Fused last-known·유효/오래됨/시각·최종 확인→기존 v1 sidecar append/readback/history/cold 복원이다. 취소 후 버튼 재활성 오류를 수정했다. 기존 위치/문제 원문/작성자/상태/이력은 덮어쓰지 않는다.
- Navi 공개3.0.5를 실제 service3.0.4/maps3.3.2-rc5-hotfix.3과 대조했다. onRouteStarted는 검색 시작으로 수정. 기존 routing 또는 활성 안내모드/약관·준비/좌표·권한·중복·해제를 검사한다. 실제 목적지 일치·검색 시작·ROUTE_DRIVE/ROUTE_GUIDING 수신, **onRoutingCompleted 미수신→30초 timeout**. 전체 안내 완료를 만들지 않는다. 사용자 기존 경로는 건드리지 않고, 이번 fixture만 일치 검증 뒤 정리/SAFE_GUIDING·inactive 확인했다. 입력·실제 Main 이력/회전7개 통과.
- DriveCheck는 기존 차량 화면에서 정보 품질과 사용자 확인 기준만 표시한다. 선택한 항목→기존 초안 보존 또는 일반 편집 문장→사용자 명시 저장→선택 AI·B 인계다. 자동 안내로 문제/초안을 확정하지 않으며 숫자·시각·위치·차량 ID를 본문/AI에 자동 삽입하지 않는다. STT/공유 분석 서비스/Gemini/TTS/Gleo3 Action/Callback은 유지했다.
- SOC15초 유효 초기 수신 timeout·generation 해제와 시간 역순 상태 되돌림 방지를 보완했다. 실제 정차 validation Main에서 Console100→SDK18 CALLBACK→18 반복 GET→45 CALLBACK, 한 사건/진입1·해제1·A 명시 저장/B 읽음/cold/종료 공백을 확인했다. 마지막 Console100/P/0와 SDK100/실제 safety ALLOWED 복원. 공개20조회12수신·8미지원/실제 Fused·실제 TTS 완료도 재확인했다.
- P2: 공개 Vehicle 연속 실제 속도/가속도 미확보, validation AAOS CAR_SPEED 권한 거부로 실제 capability/주기 미검증. gear 단발은 연속 입력의 대체가 아니다. 가속도 센서 없음. P3: validation 수동 최대5초 마이크 RMS/peak·중단만 보완, 자동 수집0. 위험 점수·소음/진동 고장 판정·일반 앱 진단 없음.
- Debug/Validation/AndroidTest/Lint exit0. Lint0오류/56경고, Unit NO-SOURCE. **신규102+관련 회귀331=433 assertion** 실제 통과. SDK/합성/부분 이벤트·미검증 E/F를 구분했다. 초기 이름 제한/취소 UI/비동기 종료 대기/내비 정리 지연응답·Console 객체 파서는 수정 후 재검증했다. 기본 prepare/운영 UI 자동화·시험 문제/AVD reset0.
- 기존254DB 전체 원본10,810행·스키마·무결성/FK 보존. 일반24문제/97이벤트/10운전자/0초안 불변, 정상 진입 SDK 관측·파생46행 추가. 일반15개 private 파일 동일·설치 표시1개만 갱신. validation 원본9535행 보존/새 fixture76DB, SDK heartbeat·시험 포인터2개·설치 표시만 갱신. Firebase 인증·AI 캐시/동의·모델ON·일5/월100/서비스300/비용 예약·pending/UNKNOWN 유지, 이번 실모델/Worker 배포/정책 변경0.
- 일반 APK 기존 서명 일치·교체 설치/설치내용 SHA 대조·Main COLD/resumed 성공. APK SHA256 `48f478ff99cd773e630ed1a9e1f246367105b9177c46e29ced76347aa77f445b`. 새 흐름의 일반 사람 조작 E/실차 F는 미검증이며 자동 시험은 validation이다.
- [상세 파일·A~F·433개 결과·공식 자료·사용자 최소 확인](DRIVE_CHECK_ACCEPTANCE.md), 통합 보고서/SDK matrix/Console PoC/README/루트지침 갱신. 검증된 파일만 **GitHub 연결 도구를 통한 게시**, 최신 main parent/expected SHA·force=false·연구26개 포함 나머지 blob 검증을 적용한다. 자기 SHA 반복 커밋은 하지 않으며 게시 SHA는 최종 응답에서 확인한다.
- 남은 작업: 내비 앱의 검색완료 이벤트 정합성, 정차 일반 앱에서 새 위치 연결/DriveCheck 사람 확인, 실제 연속 운동 데이터/소음 label 확보 후 연구, 외부 Gleo 승인, 실차/물리 접근성, 후속 최종 UI·Release. 새 유료 AI 시험은 필요하지 않았다.

## 2026-11-11 17:31 +09:00 — STT·AI 설명·TTS·다음 운전자 인계 연결

- 기준 main `f59abc15bcfb26f4d784b696bd2c97cc683eee2f`/tree `2c069eda…f0b4ec`; 시작 시 원격222 blob과 존재하는 로컬204개가 일치했습니다. 로컬에 없는 Aside 연구18개는 원격 그대로 보존합니다. 로컬 Git/clone/push·AVD/사용자 DB 초기화·운영 재개 실행기는 사용하지 않았습니다.
- 일반 작성의 **저장하고 AI 설명**으로 기존 STT 자동 반영/직접 편집→명시적 commit/읽기 확인→같은 issue의 기존 Portable AI 화면을 연결했습니다. 전송 요약·실제 새 요청 버튼을 유지하며 저장 실패·초안 보존 실패에는 화면과 원 작성자를 유지합니다. 계약 source=USER_SELECTED_RECORD/issue ID·revision의 캐시/pending을 보존하기 위해 미저장 분석은 추가하지 않았고 가짜 issue/ID 재사용/AI에 따른 자동 저장은 없습니다.
- `IssueAnalysisService`를 UI/Gleo에서 공유해 현재 버전·frozen request·검증·동시 진입을 관리합니다. 새 결과/저장 캐시/진행 요청 회수를 구분하고 AI 전송 사본 편집은 계정별 암호화 job에 보존합니다. 기존 Firebase/PortableAiClient/Keystore/Worker/한도·동의를 그대로 재사용합니다. 실제 차량 snapshot을 AI 화면에 로컬 참고로 표시하며 category/situation 외 전송은 없습니다.
- TTS는 기존 SpeechOutput을 통해 표시된 결과 전체를 짧은 구절로 읽습니다. 완료/실패·취소·늦은 callback/안전 제한을 관리하며 AI 재호출은 없습니다. Gleo Action3/Schema/신뢰 gate·callback·외부 승인 대기는 유지합니다.
- 수정 Kotlin9개: MainActivity, PortableAiActivity, 신규 ai/IssueAnalysisService, 신규 ai/auth/PortableSession, 신규 integration/ExplanationPlayback, integration/SpeechOutput, integration/GleoIssueAnalysis, 신규 IntegratedFlowChecks, MvpScenarioInstrumentation. SDK/Manifest/빌드 설정/DB 스키마/Worker 소스/Secret/정책은 변경하지 않았습니다. 문서는 새 STT_AI_HANDOVER_ACCEPTANCE·통합/Gleo/STT 안내·AGENTS·본 기록입니다.
- Debug/validation/validationAndroidTest/Lint exit0(최종 앱2m34s/107 tasks), 추가 실제 차량 문맥 계측 APK1m14s/44 tasks exit0. Lint 오류0/경고56, Unit NO-SOURCE. 읽기 전용 기존 캐시/offline/in-process 컴파일을 사용합니다. 최초 플러그인 캐시 경로 누락·daemon 접근 오류와 fixture 주입/updatedAt 비교의 초기 검사 실패는 보존하고 수정 후 재검증했습니다.
- **신규45+관련 회귀253=298 assertion PASS**. 실제 View/SQLite에서 A음성 callback→편집/초안→명시적 저장→AI View/fixture 모델→전체 TTS 전달→B 읽음·점검 메모→재진입을 검사했습니다. validation force-stop 후 암호화 결과/전송 사본/사용량·인계 이력4개 복원, 실제 Pleos ON_DEVICE TTS2개, 실제 SDK SOC/거리 commit→AI 화면 동시 표시/차량값 미전송4개를 구분했습니다. 실제 Gemini나 실제 신규 발화로 꾸미지 않습니다. 기존 관련 STT/issue AI/Gleo/DB/Portable/Vehicle 검사도 통과했고 destructive prepare/일반 UI 자동화0입니다.
- 서명 일치/기존 APK 보존 후 일반 install-r/user10 성공, 설치 APK가 새 Debug SHA256 `f33044d156269f219c689efa0bd46ff826bdc06b3183b9554955872cc5966f40`과 일치했습니다. MainActivity COLD `Status:ok`/실제 resumed 확인. 일반 앱 새 버튼 실조작/발화·새 Gemini 왕복은 별도 사용자 확인이며 자동 조작하지 않았습니다. 최종 비공개 백업 검사 때 앱을 종료했습니다.
- 기존226DB(일반42/validation184)의 전체 원본1206+9380행/스키마/user_version/integrity/FK 보존. 일반24문제/96이벤트/10운전자/1초안 불변. 정상 진입의 원관측300→320/스냅샷77→78/인사이트152→154의23행만 추가. 설치 직후 files/prefs16개 동일, 실행 후 Firebase/동의/AI 캐시·사용량 등15개 동일·profileInstalled1개만 변경. validation 기존 DB 추가/변경0, 새 고유 fixture28DB·기존 시험 pointer/heartbeat/profile marker만 갱신했습니다. 일반 시험 문제 추가0입니다.
- 이번 신규 실모델·Cloudflare 배포/모델 상태·한도/원장 변경0회. 마지막 사용자 확인의 모델ON·TEST1/정식6/잔여4·기존 성공과 캐시를 보존했으며 이번 새 서버 사용량 조회 값으로 쓰지 않습니다. 공개health의 웹 도구 접근은 불가했으며 인증 운영 조회나 재개 실행기를 실행하지 않았습니다. 새 흐름의 실제 신규 Gemini/사용자 청취·외부 Gleo 왕복·실차/PC B/Release 서명은 여전히 미검증입니다.
- [상세 구현·298개·데이터·사용자 최종 확인](STT_AI_HANDOVER_ACCEPTANCE.md). 검증된 명시15개 파일만 기존 main 트리/부모/기대HEAD를 지켜 **GitHub 연결 도구를 통한 게시**를 진행하며, 원격 파일 SHA·나머지 파일/Aside 보존과 최종 커밋은 완료 응답에 기록합니다. 로컬 Git push와 구분합니다.

## 2026-11-11 16:45 +09:00 — 일반 CARITDA Gemini 신규 분석·앱 TTS 실제 성공

- 기준 main `ad004608f4617b21f284d669b6c0c81e6bb421a7`; 최초 로컬 tracked 변경 없음. Gleo 승인과 독립된 PortableAiActivity/Client·Firebase/Keystore·기존 Worker·암호화 캐시 경로를 확인했습니다.
- 사용자 인증 조회는 TEST1/정식5/잔여5, 반복 일/월 정책·월300, 모델OFF·불확실없음·동의유지였습니다. 이전0회는 최초 이월일의 일일 제한이며 현재는 모델OFF가 차단 원인이었습니다. 한도 증액/원장 초기화 없이 해결했습니다.
- 이번 실행 전 사용자에게 MODEL_ON 유지·최대1회·종류/증상만 전송·기존 일5/월100/서비스300/월USD3 예약·실패OFF를 설명하고 명시적 승인을 받았습니다. Standard 가격0.30/2.50 USD/백만 토큰, 보수적 계획약0.0074752/회·예약0.01이며 실제 청구를 조회했다고 주장하지 않습니다.
- 사용자 PowerShell의 기존 운영 실행기 재개로 실제 Worker 활성 버전 `200edeff-41b1-4454-9d40-b0d2809014e8`, 모델ON/공개401/기존 BUDGET·Secret/Firebase·기기 목록/정책 보존을 검증했습니다. 동일 번들 SHA256 `8A3349D1…9ECF123` 유지, 추가 Secret·계정·서버·예산 변경 없음입니다.
- 사용자가 일반 앱 **신규 Gemini 설명과 실제 한국어 TTS** 성공을 확인했습니다. 실행 기록 stage=COMPLETE/actual_model_success_user_verified=true, TEST1/정식6/잔여4, 종료16:41:45 KST. 재실행 후 저장 설명 복원·카운터도 확인됐습니다. 원본 실행 기록에 TTS 전용 필드가 없어 사용자 확인을 별도 로컬 근거로 보존했고 원본을 조작하지 않았습니다. Gemini 실제 신규1회, 캐시·Mock로 대체하지 않았습니다.
- 변경: 운영 실행기의 `--verify-tts` 선택 확인, 기존1회 사용량 검사를 함수로 분리하고 캐시만 재생/추가 호출/원장 변경/잔여 불일치 거부를 검증했습니다. 운영 실행기42+준비/비용18+반복정책12=72개 로컬 검사 exit0, 테스트 자체 배포/모델0회. Android/Worker 공급자 소스·SDK·Manifest·서명·APK는 변경하지 않아 불필요한 재빌드/계측/재설치 없음입니다. 직전281을 이번 신규 검사로 재집계하지 않았습니다.
- 전후 비공개 백업·전체 원본 비교:226DB(일반42/validation184), 일반 원본1160행/validation9380행·스키마·user_version·무결성 모두 유지. 일반24문제/96이벤트/10운전자/1초안 동일. 진입 관측 등46행 추가, 기존 원본 수정/삭제0. 일반 보호파일14개 동일, AI 결과/사용량1파일·Firebase 로컬 상태1파일 정상 갱신. 일반 APK SHA256 de5fd194…50faa277 유지입니다.
- 수정 문서: [독립 AI 실제 결과](GEMINI_INDEPENDENT_ACCEPTANCE.md), 통합 보고서, OPERATIONS_SETUP, README, 본 진행 기록. 운영 기록/비밀 설정/토큰/DB/캐시 원문/음성/APK/Aside 자료는 게시하지 않습니다. 검증된 명시적 파일만 **GitHub 연결 도구를 통한 게시**를 하고 최종 SHA는 완료 응답에 기록합니다.
- 남은 범위: 실제 토큰 청구/정확한 응답 ms·이번 SDK TTS callback 원시 로그는 별도 수집 안 함. TTS는 실제 사용자 청취 근거입니다. Gleo 외부 왕복/Vehicle 값 AI 전송/PC B/실차/Release 서명·제출은 이번 독립 성공에 포함하지 않습니다. 승인한 모델ON을 유지하며 이후에도 기존 인증·동의·호출/비용 제한을 적용합니다.


## 2026-11-11 15:46 +09:00 — 일반 앱 음성 초안·Gleo 안전 통합

- 기준 main `d946720bb5598982ded7d56027b4eb788188740e`; 최초 로컬 tracked 파일 일치. GitHub 연결 도구로 HEAD/tree/blob를 확인했으며 로컬 Git·Aside 연구 파일은 변경하지 않았습니다.
- MainActivity의 인식 확인 팝업을 제거하고 final+ended 이후 기존 입력란에 한 번 덧붙입니다. 원문/작성자/초안/동시편집/취소/중복 보호, Activity 재생성·명시적 저장·B 인계 확인을 유지했습니다. 사용자가 교체된 **일반 앱의 팝업 없는 반영**을 확인했고 실제 SDK ENDED→FINAL→COMPLETE를 확인했습니다. 수동 완료/마이크 재설정 없음.
- Gleo QUERY/DRAFT를 재사용하고 명시적 기존/새 분석 Action을 추가했습니다. 공식 Callback 영어 message/status/mode/data 문자열 계약, 신뢰하는 호출자/서명/명시적 receiver/읽기 동의/6초 내부 예산을 구현했습니다. 검증된 수신 계약·Schema 승인이 없어 외부 전송은 차단합니다. 시스템 관련 앱 존재와 실제 Gleo 음성 성공을 구분합니다.
- 기존 PortableAiClient/Firebase/Keystore/암호화 캐시·category/situation 동의·모델/한도/pending/UNKNOWN을 재사용했습니다. 새 모델/배포/설정 변경0회. 신규 Gemini 7초 왕복 미검증이며 캐시로 대체하지 않았습니다.
- Debug/validation/AndroidTest/Lint/서명 전 Release 빌드 exit0. Lint 오류0/경고56, Unit NO-SOURCE. 신규62 + 기존 영향 범위219 = **281 assertion** 실제 통과. 초기 빌드 인자·새 fixture 운전자 준비 오류는 수정 후 재실행했으며 성공으로 누락하지 않았습니다.
- 실제 Vehicle20개 중12개 수신/8개 미지원, Navi 경로 상태, Pleos TTS onDone, 실제 Fused last-known 수신·validation 선택 fixture DB 연결/재개방을 검증했습니다. 정확도(m)/실차/목적지 경로 시작/물리 음성 출력은 별도 미검증입니다.
- 설치 서명 일치 후 일반 `install -r` 성공 및 COLD foreground 실행. 현재 일반24문제/96이벤트/10운전자 유지, 사용자 음성 미저장 초안0→1. 원관측220→240/스냅샷73→74/인사이트144→146. 기존204DB의 스키마·모든 원본행·무결성 통과, 일반 인증/개인/AI캐시15파일 byte-identical. 기존 원본1112+9264행 보존. 백업/DB/개인 결과는 로컬에만 있습니다.
- 변경 파일: MainActivity, CaritdaActionActivity, CaritdaActions, SpeechDraftDelivery, GleoReplyChannel, GleoIssueAnalysis, InspectionNavigation, Manifest, 두 Schema, gleo resources, 신규3계측+runner, README/본 문서/통합·Gleo·STT·SDK 문서. 기존 View 홈/SDK 버전/AI 서버/Firebase 설정/안전 게이트/원본 스키마 보존.
- 공모전 공식 페이지·FAQ·개발/제출 가이드와 Gleo5개 문서, 공식 샘플의 Kotlin/Manifest/Gradle을 직접 열었습니다. [상세 파일·A~G](GLEO_INTEGRATION_ACCEPTANCE.md), [281개 결과·APK·보존](CARITDA_INTEGRATION_REPORT.md).
- 남은 범위: 외부 Gleo 승인·호출 신원·수신 계약, 실제 신규 AI 왕복, 사용자 정비소 경로 시작, Release 소유 키 서명·설치/게시 승인, PC B·실차·물리 접근성, 영상·기획서/공개 전 이력 검사. Private 저장소 공개·심사자 초대·제출을 실행하지 않았습니다.
- 검증된 명시적 파일만 기대 HEAD를 지정해 **GitHub 연결 도구를 통한 게시**를 진행합니다. 최종 SHA/원격 확인은 완료 응답에 기록하며 로컬 Git push 성공으로 표현하지 않습니다.


마지막 작업 갱신: 2026-11-11 02:26:21 +09:00 (Asia/Seoul)

## STT 자동 종료·취소 최종 정리 / SDK 통합 기반

- 기준 main e0517f3972b8043c6f13b448c412d23f2217682e와 원격 대상 SHA를 확인했습니다. Aside·UI 타 작업·원장·기존 설치를 보존합니다.
- Windows actual PCM은 정상(기본252800bytes/peak17058), Android는 read 정상·silenced=false지만 peak2였습니다. passive Windows meter0 추론은 철회했습니다. 기존 AVD에 -allow-host-audio를 명시해 정상 재실행한 뒤 Android peak167/62와 실제 한국어3회·자동 종료/재입력 성공을 사용자/SDK로그로 확인했습니다. netsimd/qemu/emulator 각1개, 초기화·SDK재설치·권한 우회 없음입니다.
- 사용자 최종 방향에 따라 수동 완료 버튼·수동 EOF 호환 경로를 제거했습니다. 공식 ON_DEVICE/request/Locale.KOREA 자동 인식과 SDK final/ended 확인을 유지하며, 취소는 별도 CANCELLED/stop·release·generation 차단으로 결과/초안을 바꾸지 않습니다. 종료 누락은 여전히 실패/복구로 구분하고 성공으로 만들지 않습니다.
- 최종 Debug/validation/AndroidTest/Lint 빌드 성공, UnitTest NO-SOURCE. 합성 상태40개와 실제 SDK 준비→취소→재초기화2회 View6개 통과. UiAutomator idle 대기는 RMS갱신으로 실패해 main-thread View 계측으로 대체했습니다. 기존219 + 이번94 =313 assertion이며 반복 시험은 중복 집계하지 않았습니다. 실제 발화3회·공개 영어PCM3회는 별도입니다. 최종 UI 수정 후 추가 발화는 요청하지 않았습니다.
- 공개 Vehicle2.3.2 읽기20종/실제12종·Navi 상태/TTS onDone 등 통합 결과는 별도 상태표에 기록했습니다. 추가 SDK의 .security Maven 그룹 필터를 수정해 universal-access0.6.0 문제를 해결했습니다. 외부 Gleo/실제 Navi 경로/물리청취/PC B/Release/실차는 미검증을 유지합니다.
- 최종 일반41DB/validation163DB 백업·시작188개DB 원본 보존. 일반24문제/96이벤트/10운전자/0초안·원본1106행 불변, validation 원본8938행 보존. 일반 files/prefs15개·Firebase 인증·AI 캐시 바이트 동일입니다. 일반 기존 APK 유지, validation만 교체. Worker/Secret/BUDGET/모델OFF 변경·실제 모델 호출·서버 배포0회입니다.
- 수정 파일·전체 상태는 [통합 보고서](CARITDA_INTEGRATION_REPORT.md), [STT 결과](STT_STABILITY.md), [다른PC 실행](STT_RUN_GUIDE.md)에 기록했습니다. 다음 단계는 실제 문제 저장/인계·Gleo 승인된 Action 통합이며 이번에 사용자 DB에 시험 기록은 추가하지 않았습니다. GitHub 연결 도구 게시 시 이번 명시 파일만 현재 main의 부모/기대HEAD로 연결하고 원격 나머지 SHA를 확인합니다.

마지막 업데이트: 2026-11-10 21:40 +09:00 (Asia/Seoul).

## 실제 운영 적용 결과 — OFF 배포·원장 보존, 잔여0으로 신규시험 대기

- 통합 실행의 최초 형식 오류는 배포 전 종료됐고 사용자 앱/캐시/로그인은 정상이라고 확인했습니다. 기존 중단 파일을 보존해 숫자별 입력으로 재개했으며, 일반 앱/Worker 기능 소스·승인 번들은 변경하지 않았습니다.
- OFF 버전abb3feac-ece2-4b02-b8ee-6368ebb588ad 실제 단일100%·기존 namespace/Secret명/Firebase 바인딩·RECURRING_POLICY 승인값 검증 PASS.12:29:55UTC 공개v2/modelOFF/무인증401 AUTH_REQUIRED 확인. 이후 입력 잔여0이 기존 양수 조건과 묶여 AUTHENTICATED_LEDGER_OR_QUOTA_MISMATCH로 중단됐고12:32:59UTC 모델OFF 복구·공개401을 확인했습니다. 복구 배포의 최신 버전ID는 따로 기록하지 않았으며 abb는 최초OFF 교체본입니다.
- 사용자가 전후TEST1/정식5/잔여0/불확실없음을 재확인했습니다. 원장 불일치/로그인 오류가 아니라 초기 전환의 과거기기누적5 보수적 이월에 따른 정상0입니다. 기존 사용량·일자·UID를 바꾸지 않았고 실제 모델 신규 안내/시험0회, ON도 진행하지 않았습니다. 기존 설명 재조회/cold·로그인 정상은 사용자 확인입니다.
- 원본 중단 기록은 별도 보존하고 사용자 확인값만 로컬 세션에 덧붙였습니다. 11-11 KST00후 --approved --resume-quota는 기존 OFF배포·캐시 검사를 반복하지 않고 바인딩/코드etag/정책·인증된잔여만 확인합니다. 여전히0이면 OFF대기, >0일 때만 첫ON·실제최대1회로 이어갑니다. 시험 발행 뒤에는 재개를 거부하고 UNKNOWN 재시도하지 않습니다. 추가 승인은 필요하지 않습니다.
- 운영 실행기 최종35 로컬검사·구문검사 PASS. 날짜 경과 실운영·실제 신규 모델·신규후감소량 검사는 미완료입니다. 여섯 완료기준 전체 통과라고 보고하지 않습니다. [실제 검증표](AI_ACTIVATION_VERIFICATION.md)·[재개 한 줄](../infra/gemini-relay/OPERATIONS_SETUP.md)을 따릅니다. 검증된 운영 도구/문서만 GitHub 연결 도구로 게시합니다.


## 현재 단계 — 운영 최종 승인·배포/검증 통합 실행 진행

- 사용자가 Worker 교체·기존 Secret/Firebase/기기/BUDGET 유지·반복 정책(일5/계정월100/서비스월300/USD3예약)·실제 Gemini 최대1회·실패시OFF를 최종 승인했습니다. 같은 승인이나 이전 빌드/회귀/SDK 조사를 반복하지 않습니다. 시작 main d50f85f·승인 번들8A3349D1… 일치입니다.
- Cloudflare 관리 연결이 없어 일반 PowerShell에 test/apply-operations.mjs --approved 한 번 실행을 요청했습니다. 사전 해시/설정/활성100%·공개401 → OFF배포 → namespace/Secret명·Firebase/정책 보존 → 인증된 앱 사용량 확인 → ON → 실제 신규1회·캐시/cold 복원·최종 누적 확인입니다. 오류면 OFF 복구 후 중단합니다. 기존 실행 기록이 있으면 배포·모델 시험을 반복하지 않습니다.
- 앱의 JWT/기록·모델 본문을 추출하지 않고 세 번의 필수 화면 확인만 집계 숫자로 입력합니다. 성공은 새 요청의 성공 안내/새 본문과정식시도+1로 확인하며 예전 설명이 남았다는 이유로 성공 처리하지 않습니다. 동일 KST 날짜의 재조회 후 잔여-1 검사를 포함합니다. 실제 모델을 실행기가 직접 호출하지 않습니다.
- 운영 실행기 최초 로컬 안전 fixture23개·구문 검사 PASS(후속35개), 배포/실모델 증거와 구분합니다. 앱/Worker 기능 소스·승인 번들·DB/AVD/인증은 수정하지 않았고 불필요한 재빌드도 하지 않았습니다. 현재 실제 결과는 사용자 실행 기록 수신 대기이며 여섯 완료 기준을 완료로 선기록하지 않습니다.
- 변경: test/apply-operations.mjs/apply-operations-checks.mjs, OPERATIONS_SETUP 통합 실행 안내, AI_ACTIVATION_VERIFICATION, AGENTS/본 기록. 연구·다른 작업자 변경을 보존하며 GitHub 연결 도구로 검증한 운영 도구/문서만 게시합니다. [실제 검증표](AI_ACTIVATION_VERIFICATION.md)의 대기 항목은 실행 결과 후 갱신합니다.


## 현재 단계 — AI 최종 UI·일일 갱신 운영 준비 완료, 배포 승인 대기

- 전송 요약 접기/펼치기와 한국어 설명을 구현했습니다. JSON/내부 계약은 기본 UI에 보이지 않으며 동의·전송 범위·원문은 그대로입니다. 알려진 잔여0/모델OFF/UNKNOWN은 신규 생성 차단, 저장 설명은 API 없이 조회합니다. 오래된 이용량은 진입당 한 번 인증된 status로 갱신하며 Gemini/본문 전송을 하지 않습니다. 누적 정책과 일일·월간 정책 문구를 구분했습니다.
- 기존 Secret을 다시 입력할 필요 없이 RECURRING_POLICY(비밀 아닌 값)만 더하는 runtime overlay를 구현했습니다. 기존 UID/after/devices·Firebase·Keystore·BUDGET/portable:v2는 유지합니다. 기기 월간 카운터를 비파괴 추가하고 기존 이월·원장·UNKNOWN·원자 중복 방지를 보존했습니다. 제안은 일5/계정·기기 월100/서비스 월300·예약USD3/회0.01/분1, 승인 전 미적용입니다.
- 공식 Gemini Developer API 요금 조회의 더 높은0.30/2.50 USD/백만 토큰으로 보수적 계획을 계산했습니다. 고정 요청8192바이트/출력1024토큰 이내·도구없음을 호출 전에 검사합니다. 월3달러는 예약 상한이며 계정 전체 실제 청구/세금/Cloudflare 비용 보장은 아닙니다. 설정 생성기는 비밀·UID 입력/배포 없이 기존 binding/migration을 보존합니다.
- 최종 Debug/validation/계측 APK·Lint BUILD SUCCESSFUL 2m2s, 오류0/경고50. Android56+프로세스cold4+관련26+UI4=90 PASS. 서버64+42+4+5+18+12+10+16+5=176 PASS. 사용자 최종 번들·dry-run 성공, 새 번들 workerd/native 검사를 별도로 통과했습니다. 변경 영향 없는 전체 UI/SDK/분석 suite는 반복하지 않았습니다.
- 일반 앱 install-r/user10/MainActivity 실행 성공. 변경 전후186 DB의 모든 기존 행/스키마/버전/integrity/FK 보존(일반1075행·validation8928행, 기존DB 추가행0). 일반 현재 문제24/이벤트95/운전자10/초안0, Firebase·AI캐시 파일4개 바이트 동일. 새 fixture DB2개는 validation에만 있습니다. 앱/AVD/사용자 DB/원장/백업을 초기화하지 않았습니다.
- 실제 Gemini 성공은 기존 사용자 검수 사실이며 이번 유료 호출·배포·Secret/정책 변경은0입니다. 일반 사용자 성공 결과의 cold restart는 직접 검수 대기이고 validation 고유 fixture의 프로세스 복원은 통과했습니다. 구버전 opaque 귀속/전체 결과 회차 UI·Release·PC B·실차·장시간/실청구는 미완료/미검증입니다.
- 일반 APK D81D9BD5F24218E9E02FAE2548BCF655267F730CC157FDDA20D3ED7FAA5CE744, 최종 Worker8A3349D1F5135E520583C6200FD26E7E9C9B93FC8D6284DEE026AAE4E9ECF123. 로컬 main과 동일한 기준f30d8a7에서 시작했고 연구/홈/UI전면개편은 수정하지 않았습니다. 검증된 파일만 GitHub 연결 도구로 게시하며 배포 승인과 구분합니다.
- 다음 사용자 작업: 기존 AI 설명 재조회/종료·재실행 확인, 월간 반복 예산·모델OFF/ON 교체·선택 기록 최대1회 묶음 승인. Cloudflare 관리 도구가 없어 승인 후 일반 PowerShell의 준비 블록이 필요합니다. Secret·Firebase 재설정은 없습니다. [최종 결과](AI_OPERATIONS_READY.md), [관리자 승인·복구 절차](../infra/gemini-relay/OPERATIONS_SETUP.md). 최종 커밋은 완료 보고에서 확인합니다.


## 현재 단계 — 실제 Gemini 사용자 성공 확인·AI 안정화 로컬 검증

- 사용자는 실제 선택 기록 Gemini 설명/이후 MODEL_HTTP_ERROR·TOTAL_LIMIT/오류 후 기존 설명 유지를 확인했습니다. 마지막 TEST1·정식1·잔여0·불확실없음·modelON은 사용자 보고이며 새 원장 조회가 아닙니다. 과거 아래 ‘미연결/모델0/승인시험 대기’는 당시 기록으로 보존합니다. 이번 실제 모델·배포·Secret/정책 변경은0회입니다.
- 선택 기록 화면 진입과 캐시 보기는 모델을 자동 호출하지 않습니다. 명시 새 생성·기존 결과 재확인·실패/성공 분리·로컬 기록/버전별 암호화 보존을 추가했습니다. 구버전 이미 수정된 opaque job의 자동 귀속·모든 성공 회차 선택 UI는 부분 구현입니다. 원문/네 상태/Repository/SDK/UI 디자인은 유지합니다.
- 공급자 과거 HTTP 원인은 미확정입니다. 이후 비공개본문 없이 MODEL_HTTP_상태 코드로 구분합니다. 기존 TOTAL_LIMIT는 누적 전역2 등과 마지막 보고가 일치하나 현재 오류시점 원장을 새로 확정하지 않았습니다. 실패도 예약 유지/UNKNOWN 자동 재시도 금지를 보존합니다.
- 선택형 KST 일5/월 UID·기기·서비스/비용 예약 정책을 추가했으며 기존 budget/portable:v2를 덮지 않고 보수적 첫월 이월합니다. 운영 단가·월 예산·배포 승인 대기이며 활성 서버에는 적용하지 않았습니다. 실제 청구액 보장/대규모 장기 원장 완성으로 주장하지 않습니다.
- 최종 Debug/validation/계측 APK·Lint BUILD SUCCESSFUL 2m6s(오류0/경고50). Pleos user10 validation41+26+4+86+24=181 PASS(기존관련178+신규3), 새 서버64+42+4+12+16+5+16+10=169 PASS. 원본 번들·Wrangler dry-run은 사용자 일반 PowerShell 성공. 이전 전체320/297 suite는 이번 전부 재실행하지 않았습니다.
- 일반 앱 install-r·MainActivity foreground 성공. 시작/종료 비공개 백업과184 기존 DB의 스키마·버전·원본 행·FK/integrity 보존 확인. 실제 일반 문제24/이벤트95/운전자10/초안0, 원본1021행 유지와 관측 등6행 추가, validation 원본8915행 유지. 사용자 기록23을 고정하지 않았습니다. 계정/키/관측/개인백업은 게시하지 않습니다.
- 변경: PortableAiActivity/PortableAiClient/PortableAnalysis/IssueAiChecks, portable-gate/portable-issue/portable-analysis/recurring-quota, issue-security/recurring-quota 검사, AGENTS/SDK표/본 기록, AI_STABILIZATION/LIGHTING_FEASIBILITY/UI_TEAM_HANDOFF. 연구 파일·MainActivity·스키마 변경 없음.
- 등화 공개 API/권한·공식 Console·유럽 Vehicle Data API를 분리했습니다. Light/TurnSignal/CAR_LIGHTS의 실제 수신/전구 고장 판단은 미검증이며 추가 권한을 선언하지 않았습니다. UI팀에는 기능 유지·보조·TEST 숨김·삭제없음과 캐시/새 요청 계약을 전달합니다.
- 다음: 추가 Gemini 없이 사용자가 기존 성공 설명 cold restart/운전자 전환을 검수합니다. 새 반복 정책은 운영 월간 예산·단가·배포의 별도 승인 후 확인합니다. GitHub 연결 도구로 검증된 파일만 기존 main 부모로 게시하고 최종 커밋은 완료 보고에서 제공합니다.
- 상세: [AI 안정화](AI_STABILIZATION.md), [등화 가능성](LIGHTING_FEASIBILITY.md), [Claude UI팀 인계](UI_TEAM_HANDOFF.md). 일반 APK SHA256 A9A3A316915369BAEEF973BFBED5FEB63A0FEA310F2ADE7C4A7B64C6A24396F0, 검토 번들2D6907AE…이며 Release/PC B/실차/장시간/새 유료 호출은 미검증입니다.


## 현재 단계 — 일반 앱 등록 성공·선택 기록 실제1회 승인/시험 대기

+- 공개 수정본d99f21c0…의 manual feature/401/pass true(02:30:46KST)를 확인했고 일반 앱 등록도 사용자 검수로 성공했습니다. API기존사용1/정식0/잔여0/modelOFF를 확인했습니다. 기존1회를 보존합니다. 불확실 상태는 문구가 모호해 미확인으로 남기고 최신 APK에 있음/없음·전역상한을 명시했습니다.
+- 신규 preflight가 상태/모델/잔액/불확실 결과를 먼저 읽고 본문 전송을 제어합니다. 모델OFF·잔여0에서 새 issue body/intent가 전송되지 않는2검사 추가, 최신38+140=178 PASS/전체 빌드·Lint 성공. 일반APK DC8F7E5A667847BE2F666EDFFD564D676221F3E145DBC0F60E2A79111804B811를 before-one-call 백업 후 install-r 교체했습니다. 사용자 로그인·동의는 유지했습니다.
+- 사용자가 선택 개인정보 없는 기록/최대Gemini1회/모델ON·비용을 승인했고 UID1개/after0/devices2를 확인했습니다. global2/UID·일·기기1/분1/24h 정책 안내와 종료 후MODELfalse 절차를 ISSUE_ONE_CALL_TRIAL에 모았습니다. PowerShell AST검사0오류, 실제Secret설정·실모델은 사용자가 실행하는 단계이며 현재미검증/0회입니다. 기존 누적1·원장·DB·키를 초기화하지 않았습니다.

## 추가 수정 — AUTH_KEYS_UNAVAILABLE 실제 원인 재현·수정

+- 사용자가 안전한 고정 안내에서 AUTH_KEYS_UNAVAILABLE를 확인했습니다. 로그인 계정/기기 등록의 이전 단계인 Google 공개 서명 인증서 fetch 실패였습니다. 실제 workerd 네이티브 X509 경로가 redirect:error 미지원 TypeError로 Google 요청 전 실패함을 재현했습니다. 기존 Mock/키 override runtime 검사가 이 경로를 놓쳤음을 기록합니다. Firebase/UID/기기 키를 재설정하지 않았습니다.
+- 인증서·SOC·선택기록·보존v1의 HTTP옵션만 manual로 고치고3xx를 거부했습니다. Google HTTP/body/JSON/schema/network/timeout/cooldown 세부 코드만 추가했으며 원문 예외·토큰·cert·모델 응답을 로깅하지 않습니다. 실제사용자용 코드에는 합성 probe logger가 없습니다.
+- 최종 source→MJS/dry-run 사용자 성공, MJS에서148 assertions(공개키16+keys workerd5+provider workerd5+64+42+기존workerd16), 별도v1 fixture59 PASS. 실제 모델/Google네트워크0회입니다. 인메모리 probe와 최종 전체 소스 번들을 구분하고 probe 파일은 배포/게시하지 않습니다.
+- 최신 앱 빌드/Lint 및176 검사 재통과, 일반APK E105AFFEAC0588FAC4AD4D176B75B76A3169FA5FEABD43AB205D0AFF0B8CE614. 사용자 인증 세션/DB 포함 새 백업 뒤 install-r 교체했습니다. 사용자23건/기존DB를 삭제하거나 초기화하지 않았습니다. 최종 모델OFF 수정본 CE59954A…를 사용자가 승인했고 사용자 교체 실행을 기다립니다. 그 뒤 실제 등록/잔액 확인·별도1회 기록/비용 승인 순서이며 실제 Gemini는 아직0회입니다.

## 추가 수정 — 일반 앱 기기 등록 안내 가시성·안전 진단

+- 사용자 검수: Firebase 로그인/문제 한 건 진입/JSON 미리보기는 성공했으나 등록 결과와 횟수가 나타나지 않았습니다. 로컬 등록 완료 동의 파일0개를 확인했습니다. 아직 서버 실패 코드는 미확인이고 서버 요청 자체가 없었다고 단정하지 않습니다. MODEL_ENABLED=false, 실제 Gemini0회를 유지합니다.
+- AI 상태 안내가 본문 스크롤에 포함되고 JSON 미리보기와 같은 message를 덮는 구조를 고쳤습니다. 진행/성공/실패 안내를 고정하고 미리보기는 별도 패널입니다. safe enum 오류 코드만 표시하고 Firebase callback15초 취소/late 응답 차단을 추가했습니다. SDK/계정/Worker/Secret/DO 정책은 바꾸지 않았습니다.
+- 최신 APK/계측 빌드·Lint 성공, 신규36/기존140 =176 PASS입니다. 이전34/174 결과를 최신 결과로 그대로 재사용하지 않았습니다. 일반 APK는 서명 호환 install-r로 갱신했고 Firebase 세션 포함 before-feedback 백업을 비공개로 보존했습니다. 새 고정 안내에서 실제 등록/잔액 결과를 기다리고 있습니다. 실모델 성공은 여전히 미검증입니다.
+- 최신 일반 APK SHA256 323ED7584F10FD3FAD1672839D539D0C18F29F6C7139C7A879AAC84EFAE2EA62. 로컬 소스와 서버 모델OFF/401 확인 결과만 GitHub 연결 도구로 게시하며 사용자 기록·키·SDK 세션·DB·백업은 제외합니다.

## 최신 작업 — 선택한 사용자 문제 기록 Gemini 설명 (로컬 검증 완료·모델 OFF 인증 대기)

- 기준 main db39d36c0724c8688c48b12082d8e3e2427f9d1c/트리를 GitHub 연결 도구로 읽고 로컬 tracked SHA와 대조했습니다. 시작 미게시 변경0이며 소스 사본·Android user10의 일반/validation 파일을 프로젝트 내부 비공개 백업에 보존했습니다. Firebase/SDK 재설치·기존 v2 배포 오류 재조사는 하지 않았습니다.
- 일반 문제 상세에 AI로 쉽게 설명을 연결했습니다. 기존 PortableAiActivity/FirebaseSessionProvider/Keystore/AiRelayClient를 재사용해 선택한 category/body만 전송합니다. SOC와 별도 최초 동의, 같은 등록 기기, 기록/내용별 암호화 결과·pending, 안전 상태/수명주기 취소를 적용했습니다. 원문·메모·작성자·ID·시간·이벤트·다른 기록·차량값은 전송하지 않습니다.
- Worker issue API/범위 철회/strict schema·PII/명백한 지시 공격 거부, 고정 system/JSON/no tools, 구조화 Gemini 응답의 원문 인용/hash/수치/조건부 원인 설명을 검증합니다. 실제 provider 완료만 MODEL이며 원 DB/상태를 변경하지 않습니다. 같은 BUDGET·v1누적/portable:v2·quota/nonce/동일operation 단일dispatch를 유지합니다. 모든 의미적 공격/오답을 완전히 차단한다고 주장하지 않습니다.
- 신규 실제 Pleos 격리 계측34 PASS, 기존 관련 회귀140 PASS(PortableAi26/View4/Gemini86/analytics contract24), Worker 기존64/신규42/실제 로컬workerd16 PASS입니다. 소스·생성MJS·Wrangler dry-run 산출물의 같은 검사를 중복 합산하지 않습니다. 최종 Debug/validation/test APK 빌드103tasks·Lint 오류0/기존경고50, unitTest NO-SOURCE입니다. 번들/dry-run은 Codex 상위폴더 EACCES 뒤 사용자가 일반 PowerShell에서 성공했고 최종 산출물을 다시 검사했습니다.
- 최초 fixture FK 준비 오류와 IA17 읽기전용 로딩/안전상태 generation 경쟁을 분리해 수정하고 재검사했습니다. 읽기전용 load generation과 외부요청 generation을 분리한 실제 View/회전34검사가 통과했고 ALLOWED 전송 제한을 유지합니다.
- 일반 Debug APK install -r --user10 교체 성공. 기존172 DB의 원본행/schema/user_version/integrity/FK 유지, 일반905행 추가0(issues23/events92/drivers10/drafts0), 기존 validation8831행 추가0입니다. 새 고유 fixture6개만 TEST에 추가됐습니다. 당시23건을 다음 작업의 고정값으로 쓰지 않습니다. 실제 일반 앱을 실행했고 기존 홈/자동 조회 코드는 유지합니다.
- 사용자는 새 문제 API의 모델OFF 교체/PORTABLE_ENABLED=true 인증만 승인했습니다. 사용자 배포 후 공개 /health200/release v2/features selected-issue-v1/portable true/model false 및 문제 API401 AUTH_REQUIRED(pass true, 2026-11-10 01:54:27 KST)를 로컬 보고서로 확인했습니다. 일반 앱 등록·잔액은 아직 대기이며 실제 기록 전송·모델ON/비용/추가예산은 아직 승인·실행하지 않았습니다. 현재 실제 Gemini0회, 일반 앱 실제 응답 미검증입니다. API 소스/Mock 성공으로 완료 처리하지 않습니다.
- 변경: MainActivity/PortableAiActivity/AiExplanation/PortableAiClient/PortableAnalysis/신규IssueExplanation, IssueAiChecks/runner, portable-worker/gate/신규portable-issue/검사2개, ISSUE_AI_SETUP/PORTABLE 안내/AGENTS/이 기록/ISSUE_GEMINI_ACCEPTANCE. 연구·기존 원장·Secret·Firebase 공개 설정은 게시 변경에 넣지 않습니다. [상세 결과·사용자 절차](ISSUE_GEMINI_ACCEPTANCE.md)를 따릅니다.
- 다음: 승인된 모델OFF 배포→일반 앱 로그인/기기·동의·사용량 조회→선택 한 건/최대1회 비용 승인→모델ON/실제 응답 표시. 검증된 소스·비밀정보 없는 보고서만 최신 main에 GitHub 연결 도구로 게시하고 실제 Git push라고 부르지 않습니다.

## 최신 작업 — 공개 Worker v1/v2 불일치 해소·모델OFF 실제 응답 확인

- 기준 main `4f8cc6f66e8950c533940b54c6ba0b8884885b31`을 다시 확인했습니다. 앱·Firebase·SDK·DB·AVD는 변경하지 않았습니다. 읽기 전용 진단을 먼저 수행하고 Gemini/실제 모델 요청 없이 배포 불일치만 처리했습니다.
- 로컬원본·최종mjs·Wrangler JS의 실제 default.fetch는 health v2/503 PORTABLE_DISABLED 또는401 AUTH_REQUIRED였습니다. 남아 있던 구형 `.bundle/index.js`의 default는v1 handle이며 health synthetic-v1/v2 API404를 재현했습니다. v2 번들에도 legacy 코드가 포함되므로 문자열 존재/파일명만으로 판정하지 않았습니다. 설정 main은 올바르지만 과거 실제 명령/업로드 소스는 미확보였습니다.
- Codex 공개 요청은EACCES/web 접근 실패였습니다. 사용자가 일반 PowerShell에서 새 `test/deployment-status.mjs --cloudflare`를 실행했고 결과 파일을 직접 읽었습니다. 실제 배포33c3375c…는 버전3265582e…(version12/source=wrangler)에100%였지만 공개 GET/v2 POST와 현재버전 지정 GET이모두v1 동작이었습니다. 따라서 최초에는 공개 합격 기준 미충족으로 기록했고 API 키/정책 오류로 잘못 진단하지 않았습니다. 조회는 Secret/정책 값·이메일을 제외하고 namespace는 hash로만 비교합니다.
- 사용자가 **모델OFF 명시적1회 교체 배포**를 별도로 승인했습니다. 검증한mjs SHA와 실제 default.fetch guard를 확인하고, 동일 Worker/기존설정 보존 config·keep-vars·두 모델flag false를 명시한 일반 PowerShell 블록을 안내했습니다. 사용자가 교체·공개 검사를 완료했으며 Codex가 직접 배포했다고 표현하지 않습니다. 새로운 계정/DO/Secret을 만들거나 초기화하지 않았습니다.
- 최종 공개 응답(2026-11-10 01:16:35 KST): GET health200/release=caritda-portable-ai-v2/model_enabled=false. 무인증 POST v2/account/status503/PORTABLE_DISABLED/error.release=v2. 현재 버전 지정 GET도v2입니다. 새 배포 `638eef06-c013-43b1-bb44-973f7d698e62`, 버전 `e6bb4d6a-87c1-4e62-a53f-c6ecada4b6f4`100%로 `PUBLIC_V2_MODEL_OFF_PASS`를 확인했습니다. 503은v2 기능이 아직비활성인상태이며 Firebase 등록/AI 성공으로 바꾸어 보고하지 않습니다.
- 전후 BUDGET namespace fingerprint·BudgetGate 클래스 및 바인딩9개 이름/타입이 동일했습니다. GEMINI_API_KEY/PORTABLE_POLICY/TEST_APP_TOKEN Secret 이름을 유지했습니다. Secret값·원격원장숫자는 읽지 않았고 모델/인증 요청·원장초기화를 하지 않았습니다. before-repair.json은 생성되지 않아 백업 성공으로 세지 않으며 기존에 읽은 메타데이터로 비교했습니다. Gemini 호출0입니다.
- 추가한 것은 읽기 전용 진단/출력 redaction4검사·upload default guard(최신v2 허용/old index거부), README·이 기록·[WORKER_DEPLOYMENT_RECONCILIATION.md](WORKER_DEPLOYMENT_RECONCILIATION.md)입니다. Worker 런타임·budget/namespace/config/package·연구·앱 코드는 그대로입니다. 과거 정확한 잘못된 파일 선택 경로는 미확정이나, v1을 제공하던 코드/진입점 불일치를 명시적v2교체로 해소하고 실제공개응답으로검증했습니다. 같은 배포 반복이나 인증/AI 추가작업을 하지 않습니다. 검증된 도구·문서만 **GitHub 연결 도구를 통한 게시**로 공유합니다.

## 최신 작업 — Cloudflare 편집기14개 진단·배포 호환성 (실제 배포/모델 호출 없음)

- 기준 main `afd4678ba7cb2ad11937d79fc65069d08270d2da`를 연결 도구로 다시 확인했습니다. 이번은 Worker 진단에 한정하며 Android 기능/설정/DB/AVD를 수정하거나 과거 앱 검사를 반복하지 않았습니다. 사용자의 최신 지시에 따라 실제 Cloudflare 배포·원격 미리보기·Gemini 호출·Secret/운영 DO/사용량 변경을 하지 않았습니다. 아래 이전 모델OFF 배포 승인을 이번 실제 배포 허가로 사용하지 않았습니다.
- 사용자가 제공한 index.js 행82/323/723의 TextDecoder 옵션,432/1511 duplex,749/750 fromBase64,767/777/787 super 및 captureStackTrace,1364/1365 JWT 객체/Spread 진단을 원본·jose 선언·번들과 대조했습니다. 13개 목록 중767행 두 진단을 나누면14개입니다. 구형 번들은 실제 workerd에서도 실행됐고, 생성 JS를 재타입추론하는 편집기 진단과 실제 배포/런타임 오류를 구분했습니다. 표준 TS5.9.3 프로필9/12개 재현은 Cloudflare 편집기와 동일한 선언 환경이라고 주장하지 않습니다.
- 최소 수정: bodyText가 Request/Response 공통 headers/body만 받고 인증서/모델 Response를 직접 읽도록 하여 Node식 duplex2곳·강제 cast를 제거했습니다. UTF8 fatal/크기 제한·인증·판정·budget/기존 원장 코드는 유지했습니다. 생성 번들에만 @ts-nocheck/generated 표시를 넣었으며 원본 TS tsc 검사는 계속 유지합니다. 표시 적용0개 자체를 실행 검증으로 대체하지 않습니다.
- Codex Wrangler 원본 dry-run과 사전번들 no-bundle dry-run은 동일한 esbuild `Cannot read directory "../../..": Access is denied`/entry resolve 오류로 종료1이었습니다. `.tools/worker-diagnostics/wrangler-dry-run.log`의 초기화 기록과 실제 stderr를 확인했습니다. .env 부재나 API 인증/모델 오류가 아니며 권한·보안 정책을 바꾸지 않았습니다. 사용자 일반 PowerShell에서 **새 원본 번들 생성과 Wrangler dry-run 모두 성공**한 답변을 받았고 새 산출물도 직접 검사했습니다. Codex sandbox 자체의 실패를 성공으로 고쳐 적지 않습니다.
- 검사: 원본 TS 통과, 원본·최종mjs·Wrangler JS 각각 보안64 통과(동일 검사 중복 합산 안 함). 새 workerd16개로 실제 RSA JWT 성공/만료·잘못된 aud/서명 거부, fromBase64/captureStackTrace 미제공 대체경로, UTF8/BOM/크기 제한, 실제 로컬 SQLite DO challenge/alarm·P1363 등록을 통과했습니다. 외부 요청0을 검사했고 별도 로컬 fixture 저장소만 사용했습니다. Google 실제 인증서 네트워크·실제 계정·모델 결과는 미검증입니다.
- 최종 Edit code 파일은 `infra/gemini-relay/.bundle/portable-worker.mjs`, SHA256 `24FAC496B41181FA7295E3A0AFFE1AB2BE7B2F9D6281C0BF2664D2E6DB3FD09A`입니다. Wrangler dry-run의 `portable-worker.js`도64/16 검사 통과했지만 편집기용 표시가 없는 별도 산출물입니다. 구형 `.bundle/index.js`를 쓰지 않도록 안내했습니다. 안전한 후속 경로는 검증된 원본+기존설정 보존 config로 dry-run 후 별도 승인된 Wrangler 배포입니다. 이번에 배포하지 않았습니다.
- 변경: `portable-common.ts`, `firebase-token.ts`, `portable-analysis.ts`, `test/bundle-portable.mjs`, 신규 editor/workerd 검사2개, README/PORTABLE_SETUP, 이 기록, [WORKER_DEPLOYMENT_DIAGNOSTICS.md](WORKER_DEPLOYMENT_DIAGNOSTICS.md). 앱·기존 budget gate/legacy index/두 Wrangler 설정/package/lock/연구 문서는 그대로입니다. 실제 편집기 재붙여넣기·Cloudflare 업로드 수락은 미실행입니다. 검증된 원본/검사/문서만 **GitHub 연결 도구를 통한 게시**로 공유하고 번들·로그·개인 설정은 제외합니다. 최종 SHA는 완료 응답에 기록합니다.

## 최신 작업 — Portable AI / Firebase 실제 호환·정식 인증 구현 (모델 OFF 배포 승인·사용자 실행 대기)

- 기준 main `b156648ab31fa615b2a26c8cd819db42c1bd0154`, 원격133 blob/로컬 공유115개가 시작 시 일치했습니다. Aside의 ASIDE_CARITDA_PORTABLE_AI 전체와 이전 Gemini 수정 보고서를 읽었습니다. 기존 연구18개와 원격 이력을 보존하며 **GitHub 연결 도구를 통한 게시**를 사용합니다. 로컬 Git 연결/push가 아닙니다.
- Firebase 공개 Android 설정을 일반/TEST별로 로컬 주입했습니다(원본 JSON Git 제외). Auth25.0.0의 Kotlin metadata2.4 충돌을 실제 확인했고 현재 Kotlin2.2.21/Gradle8.11.1/Vehicle2.3.2를 유지한 채 공식 Auth24.2.0을 선택했습니다. 사용자가 일반 Studio에서 Sync/누락 테스트 의존성, PowerShell에서 jose6.2.16 설치를 완료했습니다. 로그인 비밀번호는 TEST 앱에서만 사용자가 입력했고 Codex가 읽거나 게시하지 않았습니다.
- 실제 Pleos user10/emulator-5554에서 사용자 TEST 로그인2 assertion, 재실행·SDK ID 토큰·명시 갱신4 assertion이 통과했습니다. SDK 초기화/완료 대기 오류를 AuthStateListener/idle 대기로 수정했고 최초 실패 기록을 성공으로 지우지 않았습니다. 이후 최종 TEST 교체 설치에서도 세션4개 검사를 다시 통과했습니다. 이 결과는 Worker JWT/기기 등록/실제 모델 성공과 별개입니다.
- 실제 코드: FirebaseSessionProvider/DeviceKeyStore, package별 공개 옵션/backup 제외, 일반 PortableAiActivity, 기존 분석의 AI 버튼, PortableAnalysis/Client/암호화 Store, TEST 호환 화면과 fixture 전용 검사. 기존 AiRelayClient HTTPS 전송·VehicleAnalytics·AnalysisExplanation을 재사용했습니다. 고정 SOC 최소 요약/typed facts/hash 검증·지속 동의/철회·기기 서명·중단/UXR/계정 분리·intent 복구를 연결했습니다. 현재 실차·개인 증상 전송은 허용하지 않습니다.
- 서버: jose JWT/UID allowlist·기기 JWK/nonce/P1363 증명, 고정 v2 계약, 단일 기존 DO의 global/UID/device/day/rate/concurrency 원자 예약·단일 dispatch·UNKNOWN, 상태/결과 조회·retention alarm을 구현했습니다. 기존 namespace/BUDGET/BudgetGate/객체/budget/migration/누적5회 기록은 유지합니다. 기존 사용+v2 시도가 승인 합계 상한에 포함되며 새 허용0/모델OFF가 기본입니다. v1 코드/preflight는 보존하고 모델 경로는 cutover 후 끕니다. 원장 초기화·환불·재호출로 우회하지 않습니다.
- 최종 로컬: Worker64 security assertion(원본과 배포 번들에 동일 검사, 중복 합산 안 함), Android Auth/Keystore20+재실행2, Portable 분석/응답/암호화/intent26, 실제 새 View4, 기존 Gemini86/분석24, 세션4 및 Node WebCrypto4가 통과했습니다. Worker/provider/DO fixture는 실제 외부 연결 시험이 아닙니다. 기본 validation DB를 초기화하는 과거 전체 suite는 보존을 위해 실행하지 않았고 기존369개 전체 통과로 재사용하지 않습니다.
- Gradle Debug/validation/androidTest/Release, lintDebug/lintVitalRelease가146 tasks로 성공했습니다. Lint0오류/50경고이며, 기존 화면 설정 버튼의 들여쓰기 오류1개만 수정했습니다. testDebugUnitTest는 NO-SOURCE라 시험 건수로 세지 않습니다. Release APK는 **미서명**이며 제출용 설치 APK라고 표시하지 않습니다. 신규 release 키를 만들지 않았습니다.
- 실제 일반 앱은 설치 직전 유효 전체 tar와 기존 APK를 새로 백업하고 인증서 일치를 확인한 뒤 `install -r --user 10`으로 교체했습니다. 설치 직후5DB가 바이트 동일했습니다. 실제 화면·SDK SOC100%/거리0.0km·연료 미지원 재수신을 확인했습니다. 앱 진입 후 정상 GET3행·스냅샷1·분석2행이 추가됐고, 일반 문제23/이벤트92/운전자10/초안0과 두 앱10DB의 원본 행·schema/user_version·integrity/FK를 최종 백업에서 보존했습니다. 수치는 실측이며 다음 작업에 고정하지 않습니다. 초기 tar는 없는 폴더 오류로 전체 검증 실패했으나 복구에 사용하지 않았고 새 verified/final tar는 검증 성공했습니다. 백업/SDK 세션/개인 캡처는 비공개 `.tools`에만 남깁니다.
- 사용자는 **모델 OFF Worker 교체·UID 정책·기기 등록·기존 사용량 조회**를 승인했습니다. 최종 번들 SHA256 `6BBF958083E40C8A12727F35752CB1454A66F2765A9BD8CD23FD32182FE9765D`, 기존 Secret·DO 유지/모델 허용0 설정을 안내하고 사용자의 대시보드 작업 완료를 기다립니다. Cloudflare 관리 도구/CLI 권한을 우회하지 않았습니다. 실제 Gemini 호출은 이번 작업0회이고 추가 예산·Gemini 활성화·실제 SDK 요약 전송 시험은 아직 승인되지 않았습니다.
- 남은 것: 원격 v2 배포/release·Google 공개키/JWT/Keystore 등록·실제 잔액 확인, 해당 모델 호출 승인·실제 응답, 일반 앱 최초 로그인/동의와1버튼 왕복, 정식 서명·실제 PC B·전체 확대/주행/사용성 검수입니다. 상세 PA30/PB18·소스/실제/Mock 범위와 APK 해시는 [PORTABLE_AI_ACCEPTANCE.md](PORTABLE_AI_ACCEPTANCE.md), 설정 클릭 경로는 [PORTABLE_SETUP.md](../infra/gemini-relay/PORTABLE_SETUP.md)에 기록했습니다. 검증된 로컬 소스·보고서만 게시하며 Firebase JSON/키/DB/백업/로그/APK/연구 파일은 게시하지 않습니다.

## 최신 작업 — Gemini 응답 진단·TEST 인증 기억 (실모델 재시험은 사용자 배포 후)

- 기준 main `99547db7a2e2c73c0146685134849c430b82859a`, 원격128개 blob/로컬 공유111개 SHA 일치. 새 HINT D6를 포함한 원격 연구17개를 보존합니다. 사용자는 초기 Worker/Secret/DO/무인증401/preflight 성공과 일반PowerShell 모델 응답을 보고했습니다. 이번 실패의 공급자 코드/원문은 조회하지 않았고 키·토큰을 요청하지 않았습니다.
- 확인된 원인: 서버의 서로 다른 MODEL_* 오류가 동일한 앱 안내로 합쳐졌고, 토큰은 요청 시작·onStop에서 매번 지워졌습니다. 당시 upstream HTTP/finishReason은 보존되지 않아 실제 공급자 실패 원인은 미확정입니다. temperature0 제거는 공식 Gemini 3.x 기본 샘플링 권장에 따른 수정이며 ‘400 원인 확정’이 아닙니다. 출력256→512의 제한된 여유, thought 분리, HTTP/안전/MAX_TOKENS/JSON/스키마/문구/앱 facts 코드 구분을 구현했습니다.
- Worker `gemini-response-fix-v2`는 허용된 코드·HTTP·종료 이유·처리ms만 진단하며 공급자 오류 메시지/finishMessage/모델 원문/키/토큰을 노출하지 않습니다. 구형 Worker는 preflight release 검사(APP_RELAY_VERSION)로 모델 호출 전에 차단합니다. 실제 provider STOP·응답 검사를 통과한 결과만 MODEL이며 앱에서 계약/ID/hash/facts/동의·문구를 다시 확인합니다. 기존 BudgetGate 본문/전역 ID/저장 key/namespace/migration/분당2·누적5는 그대로입니다. 오류도 예산을 소비하며 재시도/초기화하지 않습니다.
- TEST 개발자 진단에 버튼 비활성 이유, 안전한 진단 코드, 선택형10분 메모리 인증을 추가했습니다. 동의는 별도로 취소하고 토큰을 파일/Bundle/로그에 저장하지 않습니다. preflight/화면 재생성으로 인증 유효기간을 자동 연장하지 않으며 인증 거부/만료/명시 지우기/취소/주행 제한 시 지웁니다. 최종 일반 앱 인증은 서버 발급 단기 세션 설계만 기록했습니다.
- Debug/validation/validationAndroidTest 모두 offline 빌드 성공. 최종 앱 빌드 gemini-fix-build-6.txt는1m36s/93작업13실행·80up-to-date. validation/test만 install-r, 일반 APK는 교체하지 않았습니다. 최종 **CARITDA_GEMINI_LOCAL_PASS checks=86**(native/계약/수명주기27 + Worker Mock59), 기존 분석·AI 계약 회귀24 통과로 총110개입니다. 이전46개 포함·신규40개이며 과거369 전체를 재실행했다고 보고하지 않습니다. 실제 공급자를 호출한 검사가 아닙니다.
- Node26.13.0/npm11.20.0/기설치Wrangler4.158.0 확인 후 tsc --noEmit도 성공했습니다. Node의 같은 Worker Mock59개와 기존 Worker 재배포 설정 검사를 추가 통과했으며59는 중복 합산하지 않습니다. wrangler.existing.jsonc는 vars 없이 keep_vars=true, 동일 Worker/BUDGET/migration으로 실제 대시보드 설정·Secret을 보존합니다. 초기 wrangler.jsonc의 꺼짐 기본값은 보존합니다.
- Wrangler bundle dry-run은 `Cannot read directory "../../..": Access is denied.` / src/index.ts resolve 오류로 실패했습니다. CLI도 정상 인증을 확인하지 못했습니다(`Not logged in`). 인증 없는 health 조회도 EACCES로 막혔습니다. 권한 우회/자동 로그인/실제 배포는 하지 않았습니다. 기존 PowerShell 정책에 따른 .ps1 로드 거부 뒤 정책 변경 없이 직접 명령으로 백업했습니다. [사용자 일반PowerShell 최소 배포 명령](../infra/gemini-relay/README.md).
- `.tools/gemini-fix-backup/20261109-195913/`에 일반/TEST 전체tar·일반5DB를 보존했습니다. 두 앱의 기존5DB씩 총10개 전후 SHA256이 모두 같고 문제·운전자·이벤트·초안·원관측·스냅샷·안내·분석 이력이 바이트 동일합니다. TEST 검사는 default DB 초기화 전 분기이며 합성자료를 일반 DB에 넣지 않았습니다. 일반 manifest에 Gemini 화면이 없고 validation에만 exported=false입니다. AVD/연구/개인백업/SDK/기존 UI는 보존합니다.
- 사용자가 **main 자동 배포 연결 없음**을 확인했고 **Worker 교체와 실제 최대1회 시험을 승인**했습니다. 승인을 다시 요구하지 않습니다. 다만 현재 Codex 환경의 Wrangler 권한/인증 문제로 사용자 일반PowerShell에서 배포가 필요합니다. 이번 실제 모델 호출0회, 원격 새401/preflight/실응답/TEST 실제 응답 표시·Cloudflare 실제 DO 검증은 아직 미완료입니다. 남은 예산0이면 중단하며 실패 후 다시 호출하지 않습니다.
- 검증된15개 소스/설정/문서를 최신main 이력을 잇는 **GitHub 연결 도구를 통한 게시**로 공유하고 부모·파일 SHA/나머지 원격 파일 보존을 확인합니다. 최종 커밋은 완료 응답에 보고하며 자기 SHA를 넣기 위한 반복 커밋은 만들지 않습니다. [원인·변경 파일·검사·미검증·사용자 순서](GEMINI_RESPONSE_FIX.md). 배포 이후에는 TEST에서 인증1회입력/선택적기억→모델 없는 preflight→남은 횟수 확인→승인된 실제1회→안전한 진단 코드 확인 순서입니다.

아래는 과거 기록으로 보존합니다.

---

마지막 업데이트: 2026-11-09 01:32 +09:00 (Asia/Seoul).

## 최신 작업 — Gemini 최소 중계·TEST 전용 요청 (배포/실응답 대기)

- **최종 사용자 선택: ‘보류 — 검증된 로컬 코드만 게시’.** Worker 교체/DO 생성/인증 설정/실모델 호출을 진행하지 않습니다. 아래 배포 안내는 향후 별도 승인용입니다. 실제 응답/TEST 실표시는 미검증으로 유지하고 소스/문서만 게시합니다.

- 기준 main1ef6bbdd4fb07d8c2e2407ca79496cc54fc3d743와 로컬99개 공유 파일 SHA가 일치했습니다. 사용자는 Worker/GEMINI_API_KEY/모델gemini-3.5-flash-lite 및 일반PowerShell 성공을 보고했고 앱 인증은 아직 없다고 확인했습니다. 키 값은 조회·요청하지 않았습니다. Windows Worker HEAD는 연결 실패였고 CF관리도구/Node/npm/tsc/Wrangler가 없어 설치·배포하지 않았습니다.
- Worker TypeScript/설정/보안 harness/배포 안내를 작성했습니다. env.GEMINI_API_KEY 전용, 짧은 앱 인증, 고정 합성 SOC·4KiB·경로/헤더/필드/시각 검증, 전역SQLite DO transaction 분당2/누적5·중복UUID·실패도예약·자동재시도없음입니다. 기본은POC꺼짐/만료미설정입니다. 모델 없는 health/인증된 preflight→명시적실요청, 모델12초/256출력토큰/16KiB·안전/facts 검증을 구현했습니다.
- 기존AiRelayClient/AnalysisExplanation/VehicleAnalysis·VehicleAnalytics.id를 재사용했습니다. validation source set에만 GeminiPocActivity/메모리 TEST_ONLY fixture/비공개Activity manifest를 추가하고 TEST 차량정보에 버튼1개를 붙였습니다. 일반 홈/그래프/DB/SDK·권한은 재설계하지 않았습니다. 토큰마스킹/저장금지/FLAG_SECURE/전송동의·취소/수명주기/CarSafetyGate를 적용하고 실제MODEL·preflight·로컬규칙을 분리합니다.
- Debug/validation/test APK 빌드 성공, 앱변경 최종gemini-build-4.txt 1m39s/93작업10실행·83up-to-date, 테스트도구수정 gemini-build-5.txt57s/4실행. validation APK SHA256E223734197C6CC360CB83DE3C397C2A397AB811BE8702FE7F3D696999A5D7389, validation/test만 데이터유지교체했습니다. 일반 APK는 교체하지 않았습니다. aapt manifest에서 GeminiPocActivity가 일반에는 없고 validation에만 있음을 확인했습니다.
- **CARITDA_GEMINI_LOCAL_PASS checks=46**(native/fixture/facts8 + Worker Web API38). 실제Worker소스를 기존WebView131엔진에서 실행하고provider/DO저장소만Mock했습니다. 무인증/오인증/만료/미설정/입력/동시·전체예산/재전송/429/공급자인증/형식·허위숫자·수리/안전/네트워크/12초deadline/비반환을검사했습니다. 실제모델을호출한것이아니며 CF실제DO·분산/tsc/Wrangler검증을대신하지않습니다. 기존369전체회귀는 이번최소작업에서재실행하지않았습니다.
- 테스트Long/Int비교컴파일오류, 브라우저 Origin제거어댑터/비가시WebViewcallback대기 문제를 수정했습니다. Worker인증정책을완화하지않고 local3통과·앞선실패로그를보존했습니다. CF코드배포/앱인증·최대5회실모델요청승인을질문했으며 승인·설정전실호출하지않습니다.
- .tools/gemini-poc-backup/20261109-010917에 두앱전체tar/일반5DB를새백업했습니다. 일반5DB는전후모두바이트동일, 문제DBA906AA7FDEFF00A9AC444A8DB306EF06791D0DD1A0B461073932277DBAC1C92C입니다. 새검사는fixture초기화전validation전용분기라기존default DB를삭제/합성프로필추가하지않습니다. AVD/개인백업/초안/관측/이력은보존했습니다.
- [실제/미검증·설정·승인·다음단계](GEMINI_MINIMUM_POC.md), [Cloudflare클릭경로/명령/토큰설정](../infra/gemini-relay/README.md). 원격무인증401/인증preflight·실제Gemini응답/TEST표시는미검증입니다. 키·데이터·백업·로그·APK는게시하지않고검증된소스/문서만최신main이력을잇는 **GitHub 연결 도구를 통한 게시**로공유합니다. 최종SHA는완료응답에보고합니다.

아래는 과거 기록으로 보존합니다.

---

마지막 업데이트: 2026-11-08 23:40 +09:00 (Asia/Seoul).

## 최신 작업 — SOC 현재/이전 구분·실제 SDK 시계열·자동 인사이트

- **Stage1·2 완료/Stage3 부분 구현:** SOC 현재값과 이전 사건 분리, 공통 버튼 경계/28dp 아이콘, SOC·누적거리 그래프/품질/통계/자동 인사이트/로컬 행동 설명을 구현했습니다. 사용자가 ‘AI 설정 없음 — 로컬 분석부터’를 선택해 실제 모델/유료 호출·서버 배포·실사용 외부 전송은 하지 않았습니다. 로컬 설명/JSON 검증을 실제 AI 응답이라고 표시하지 않습니다. [실험·분류·한계·사용법·미검증](CARITDA_ANALYTICS_AI_INTEGRATED_REPORT.md).
- main69d64efbd3ab756d883620fe9a7b15b20ce81ffa와 로컬93개 공유 파일 SHA가 일치했습니다. Aside3개 필수 연구는 로컬에 없어 기존 연결 도구로 읽은 고정 원격본/SHA를 대조해 재검토했고8개 연구를 보존합니다. Git/.git/clone/push·Codex 재실행·SDK/AVD 재설치/초기화·전역 보안 변경은 하지 않았습니다.
- 실제 원인: 일반 앱은 현재23건/안내0개, validation은3사건/6변경이었습니다. validation의18→45/6→29/17→39는 서로 다른 ID·SDK CALLBACK 원관측 근거입니다. 39/29/45는 과거 해제값이지 현재100이 아니며 앱 저장소도 다릅니다. 이 사례의 entered/last 순서는 우연히 같았고 정렬만을 원인으로 단정하지 않았습니다. 읽기 정렬을last_at으로 바꾸고 현재값/현재 조건/접힌 한 사건 한 카드·진입/해제·근거를 분리했습니다. 원본/스키마/규칙/수동 상태는 유지합니다.
- VehicleAnalytics/VehicleChart/AnalysisStore는 실제 원관측 참조로 동작합니다. worker의 기간·최대2000행과 환경 범위, 오류/단위/범위/미래·역순/오래됨/반복/세션·공백을 구분합니다. 실제epoch x축·단절 선·텍스트 대안·20행 근거 페이지를 제공하고 통계 평균의 모집단을 표시합니다. 새 GET/Callback 뒤 요청을 합쳐 분석하며 redraw/스크롤로 SDK 조회하지 않습니다. 원본 평균을 시간 평균/운행 효율/고장으로 해석하지 않습니다.
- 기존 LowSocStore의 changes를 그대로 이용해 기준 진입/해제를 설명합니다. 별도 임계값 감지기를 만들지 않습니다. 비교 가능 변화/3점 이상 같은 방향/20%p 탐색 후보/공백/수신 오류를 안정 키로 저장하고 반복값을 새 변화로 세지 않습니다. 새 파생caritda-analytics.db v1만 추가했으며 기존4DB 버전/열과 원관측은 그대로입니다. 다른 DB의 원자 트랜잭션을 가정하지 않습니다.
- AI 최소 분석 미리보기·고정 payload 동의/취소·정확한 facts/hash/허용action·창작 숫자/기존 숫자 뒤바꿈/수리·안전 확정·Mock 거부를 구현했습니다. 숫자는 검증된 facts에서 로컬 표시하고 모델 자유문장의 숫자는 거부합니다. 일반 앱은 미연결/전송 없음 안내이며 실제 응답 저장·전송중 취소·모델 의미 안전성은 미검증입니다. 기존 상담에 실제 증상 인용 질문/명시적 복사 버튼을 추가했지만 실제 클립보드 수동 검수는 미실시입니다.

### 최종 빌드·실행·검사

- 최종3APK offline BUILD SUCCESSFUL in2m3s,93작업13실행/80up-to-date. Gradle8.11.1/JBR/SDK34/Build-Tools35.0.0/Vehicle2.3.2 유지. Debug SHA2569738F47EB27A400EEDDB3B639BBB206F3C856B070F5678E85F388A3914BC3E40이며 일반 install-r/COLD Status:ok와 설치base.apk 일치를 확인했습니다. 일반 앱의 실제100%/분석 차트/TEST배너없음을 XML·화면에서 확인했습니다. Release가 아닙니다.
- 기존320개를 새 앱에서 다시 통과했습니다. 신규49=분석/AI/UX30(AN13/AI9/UI8)+실제SDK그래프9+cold2+4DB보존8, **최종369 assertion 통과**. 4DB8 중6개는 기존3DB 보호와 겹치며369는 고유 기능 수가 아닙니다. 실제SDK/합성/로컬계약/렌더를 구분합니다. 최종 로그analytics-final-*.txt, 시스템 중단 뒤 재개는retry-*.txt이며 동일mode 중복 합산하지 않았습니다.
- 신규54 native render(3화면×3폭×3글꼴×2테마), 기존PX198/ON200/NAV낮은높이6조합을 실행했습니다. 일반 텍스트4.5/버튼경계3 대비·76dp/가로 넘침·말줄임을 확인했습니다. Canvas 모든 픽셀/실제 OS 분할·IME/고령자/TalkBack/rotary/실차는 별도 미검증입니다.
- 최종 실제Console80→60(callback, -20%p/low0)→18(callback, -42%p/low인사이트)→18반복PUT/GET(같은사건)→45(callback/동일사건해제/차트반영), 원관측·인사이트cold복원을 확인했습니다. 출처PLEOS_EMULATOR_SDK, 앱수신시각/%이며 ECU시각·실차고장이 아닙니다. GET세션이 바뀐 구간을 억지로 +27%p/실제운행변화로 잇지 않습니다.
- 최종 validation의 실제Drive/20km/h에서 버튼6개 disabled/제한 안내, P/0 복원 후6개 enabled를 확인했습니다. 최종SOC100/P/0으로 복원했습니다. 새 차트/메뉴는 추가SDK 조회를 만들지 않으며 종료/비가시/제한 중 상시 감시는 없습니다.
- 중간palette 인수 누락 빌드2회는 수정했습니다. 최종 재검사 중Android가 System has crashed/Activity서비스 일시부재를 반환해 중단된mode는 성공으로 세지 않고 자연복구 확인 후 재실행했습니다. 같은시각 Bluetooth SIGABRT가 있었지만 전체 중단 원인은 미확정입니다. AVD재부팅/wipe/BT강제종료·설정변경은 하지 않았습니다. 중단로그·크래시로그는 보존했고 장시간 환경 안정성은 남아 있습니다.

### 원본·현재 데이터·게시

- .tools/analytics-backup/20261108-224310에 일반4DB/전체before.tar, 최종5DB/after-final-install.tar와 validation전후 전체tar/실제SDK시험DB/개인캡처/진단로그를 새로 보존했습니다. 일반 문제DB는 전후A906AA7FDEFF00A9AC444A8DB306EF06791D0DD1A0B461073932277DBAC1C92C로 바이트 동일/현재23건, 자동안내DB도 바이트동일/안내0/기본꺼짐입니다. 전후 모든 원문/이벤트/운전자/초안·관측/스냅샷/연결 scalar·ID·version포함과 integrity/FK를 독립 사본에서 확인했습니다. 새실험/Mock를 일반 앱에 넣지 않았습니다.
- 일반 원본의 과거15%도 공식GET detail/SDKsource/세션 근거를 읽기 전용 사본에서 재확인해 보존했습니다. validation의 원래3사건/원관측/설정/파일은 최초tar에서 복원하고 직후 안내/관측 해시 일치를 확인했습니다. 신규 실제시험은 별도tar/DB로 남깁니다. 과거출처불명·기존테스트·개인백업을 임의삭제하지 않았습니다.
- 변경17파일: 앱8(MainActivity/CaritdaDesign/ObservationStore/LowSocStore, 신규VehicleAnalytics/AnalysisStore/VehicleChart/AnalysisExplanation), 검사3(MvpScenarioInstrumentation/ValidationEnvironment/신규AnalyticsChecks), 문서6(AGENTS/디자인시스템/진행기록/신규통합보고서/루트·앱README). Repository/Service/IssueModels/RecordMigration/SnapshotStore/PleosReadClient/PleosSocObserver/CarSafetyGate/SDK·권한 설정은 기준SHA를 유지합니다.
- 남음: 실제AI중계/인증/모델·비용 승인·전송/응답 저장·실모델품질, 추가P1센서/Gleo/종료중감시/푸시/실차·사람 검수, Lint/Release서명·제출APK/영상/기획서. 저장소private를API로확인했으며 공개/심사자초대/외부제출은하지않았습니다. 기존사용자직접검수와 이번자동검사를 구분합니다.
- 검증17파일만 최신main부모·대상SHA·기대HEAD를 확인해 **GitHub 연결 도구를 통한 게시**하고 원격파일·부모·나머지SHA/Aside를 검증합니다. DB/원관측/개인화면/백업/키/로그/APK는게시하지않습니다. 최종커밋은완료응답으로보고하며 자기SHA삽입 반복커밋은하지않습니다.

아래는 과거 작업 기록으로 보존합니다.

---

마지막 업데이트: 2026-11-08 21:48 +09:00 (Asia/Seoul).

## 최신 작업 — 실제 Virtual Console SOC callback·자동 기록 PoC (AI는 미연결)

- **부분 성공:** Console→실제 SDK GET/18·45 변화 callback→사용자 기준→중복 없는 사건/해제/원본 저장·화면·cold 복원 성공. 실제 인증된 AI 모델은 승인된 중계/앱 인증·모델/비용 설정을 받지 못해 호출하지 않았습니다. HTTPS200/Mock는 AI 성공이 아닙니다. [상세 단계·근거·P1표·승인·시연](CARITDA_VIRTUAL_DRIVING_AI_POC.md).
- 기준 main3c1a5f5a034a0515bda5a9d1202863bb8d7fd17b와 로컬88개 공유 파일 SHA를 확인해 차이가 없었습니다. 최신 연구3개·기존 자동화/디자인/호환 자료, 공식 Console/SDK/릴리즈/Gleo와 OpenAI Docs의 키 보안 지침을 확인했습니다. Aside8개/기존 이력을 보존합니다. Git/.git/clone/push·Codex 재실행·SDK/AVD 재설치는 하지 않았습니다.
- 기존 forward가 없어8088을 한 번 등록했고 Console HTTP200을 확인했습니다. 복합 명령/자동 브라우저 실행은 자동 승인 검토가 거절했습니다. 허용된 개별 명령과 **공식 Console 페이지가 쓰는 동일 웹 요청**으로 진행했으며 GUI 자동화 성공이라고 하지 않습니다. 처음 emulator_inactive는 페이지의 browser 세션 비활성 원인이었고 같은 정상 세션 유지 요청 뒤 bridge_ready/active와SOC 변경200을 확인했습니다. 비공개 SDK/VHAL 강제 쓰기·sandbox/TLS/시스템 정책 해제는 없었습니다.
- 새 PleosSocObserver는 공개SOC Listener/Capability/get/CAR_ENERGY만 사용합니다. 사용자 켜기·앱 가시·ALLOWED에서 시작하며 redraw/탭으로 재등록/무한 polling하지 않습니다. onStop/제한/실패 시 unregister/release·generation 차단과 관측 공백을 기록합니다. 기존 단발GET/스냅샷은 유지하며 callbackSOC만으로 가짜 ODO/연료 배치를 만들지 않습니다.
- 새 LowSocStore는 별도 sidecar v1(policy/evaluations/episodes/changes/gaps)입니다. 원관측 commit 후 실제 insert의 storeUUID/rowID를 받는 appendRef를 추가하고 기존 append(Unit)는 유지했습니다. 사용자 미만 기준/2%p 해제 여유·규칙/설정버전·근거·상태 이력과 중복 키를 저장합니다. 같은 scope의 active 사건은 재시작/날짜/운전자/정책 버전 변경만으로 재무장하지 않습니다. 이전15%만으로 새 카드/실패·오래됨으로 해제를 만들지 않습니다. 실패 주입 rollback/원본 보존·replay를 검증했습니다.
- 일반 차량 정보에 ‘충전량 자동 안내·설정과 기록’ 화면을 추가했습니다. 기준·수신값/출처/시간·켜기/끄기·공백·조건 해제와 접힌 근거를 보여줍니다. 고장 진단/사용자 문제 해결/수리 완료가 아니며 네 수동 상태/원 작성자·이력·초안·불변 링크는 그대로입니다. 일반 앱은 기본 꺼짐이며 AI 미연결 안내만 표시합니다.
- AI 최소 입력/JSON 계약·HTTPS relay client를 구현했습니다. provider key는 APK에 없고 승인된 HTTPS endpoint와 임시 앱 토큰이 없으면 호출할 수 없습니다. 리다이렉트 금지/응답16KiB·시간 제한/요청ID/짧은 한국어·정보 목적/확정 문구 거부를 검사합니다. 이름/VIN/위치/전체DB/로컬 근거ID는 payload에서 제외하며 실사용 기록 전송·새 서버·유료 요청·계정/키 발급을 하지 않았습니다. 실제 모델 품질/수치 일치/동의·취소·AI 저장/화면 응답은 미검증/미완료입니다. 모델 글을 상태 writer/명령으로 쓰지 않습니다.

### 최종 실제 검사와 앱 적용

- 최종3APK offline assembleDebug/assembleValidation/assembleValidationAndroidTest BUILD SUCCESSFUL in1m31s,93작업13실행/80 up-to-date. 일반 Debug SHA2568F94DEDE4E98F3172C255F8A4806F6298FCE1F59A88D6ADF5E2BAA695EE4205F. 기존 JBR/Gradle8.11.1/API34/Build-Tools35.0.0/Vehicle2.3.2 유지, Release 아님.
- 기존297=CP48/cold3+MVP24/관측6/UI27+migration18+RR36/cold2+NAV17/cold12+ON32+AM35/cold4/사본보존2+PX25/3DB보존6을 **동일 최종 후보**에서 재통과했습니다. 신규 규칙/JSON13+실제ConsoleSDK8+cold2=23, 총320 assertion. 신규/기존 최종 로그는 app/build/validation/poc-candidate-*.txt입니다. 기존PX198/ON200/NAV낮은 높이6 render도 재통과했으며 사람이 이해하는지/실차/신규SOC 전체 창·접근성 통과와 다릅니다.
- 실제80 GET→18 CALLBACK→반복18 GET→45 CALLBACK, 사건1개/진입1개→해제 포함변경2개·같은ID를 확인했습니다. 원본 source=PLEOS_EMULATOR_SDK/unit%/앱receivedAt/UUID+행 참조를 보존했고 콜드 프로세스 뒤에도 유지됐습니다. 실제 수동 화면 확인에서18% 안내와45% 조건 해제를 확인했습니다. snapshot/사용자 문제의 해결 상태는 바꾸지 않았습니다.
- validation 네트워크 HEAD 실제HTTP200/TLS_AES_128_GCM_SHA256/359ms. 모델 인증/유료API 호출/사용자 데이터 전송은 없었습니다. JSON Mock와 MODEL로 표시한 합성 위험 문구 거부는 격리 검사뿐이며 실제 모델 응답으로 집계하지 않았습니다.
- 가상 Drive+20km/h에서 CarDrivingState0→1→2, UXR RequiresDO/0x50 및 입력 버튼 disabled를 확인했습니다. Activity는 이 환경에서 계속 보였으며 모든 Pleos에서 표시/백그라운드 동작을 보장하지 않습니다. 이 수동 실험은 Main/Safety/Observer가 최종과 동일한 전단계 APK에서 수행했습니다. 복원 P/0km/h/SOC100과DO→NoDO/0을 실제 확인했습니다. Console 세션 만료 때 복원이 한 번 거절돼 새 세션에서 정상 복원200을 재확인했고 가상 주행 상태로 남겨두지 않았습니다.
- 초기Kotlin 지역 함수 줄 구문 오류는 수정했습니다. 중간 초기80 확인 실패/메뉴 복귀 정확 픽셀 한 번 실패를 보존했고 값 재설정·같은APK 재검사/최종후보 전체재검사 통과를 구분했습니다. 이 두 간헐 실패의 단일 원인은 확정하지 않았습니다. 계측 캡처가 프레임 전환 전18%를 찍은 경우는 최종 수동45% 화면 확인으로 별도 검증했고 원래 캡처를 삭제하지 않았습니다.

### 현재 사용자 데이터와 보존

- user10 일반 앱을 먼저멈추고 .tools/poc-backup/20261108-virtual-poc/before.tar(databases/shared_prefs/files 전체)와3DB로 새로 백업했습니다. 기존 개인 백업/AVD/과거 출처 불명 기록을 초기화/삭제하지 않았습니다. 실제SDK validation 사본/화면·Console응답/제한 로그·after-install.tar도 로컬에만 보존합니다.
- 모든 계측·가상 주행 전후 일반3DB는 바이트 동일했습니다. 일반 앱 install-r/COLD Status:ok, 설치base.apk와위SHA 일치, 기본 안내꺼짐/안내0건/AI미연결·실제100%/0km와 **현재23건**을 확인했습니다. user 문제DB SHA256A906AA7FDEFF00A9AC444A8DB306EF06791D0DD1A0B461073932277DBAC1C92C는 교체/화면 확인 후에도 동일합니다. 현재 수를23으로 고정하는 코드는 없습니다.
- 최신 전후 독립3DB 전체 scalar/운전자/원문/이벤트/초안·관측/스냅샷/불변 링크 포함·동일 version/integrity/FK를 확인했습니다. 일반관측/스냅샷은 기존 진입의 실제SDK100/0 결과만 추가됐고 validation의18/45 사건·Mock·합성fixture를 복사하지 않았습니다. 일반 앱의 시험 증상/프로필/메모 추가도 없었습니다.
- Repository/Service/IssueModels/RecordMigration/SnapshotStore/PleosReadClient/EntryReadState/CarSafetyGate는 기준과 SHA 동일합니다. 기존3DB version/열은 그대로이며 새 sidecar는 일반 앱 첫 생성v1입니다. validation 개발 중 임시sidecar만 보호 runner의격리fixture 준비에서 초기화했습니다. 추가manifest 권한은INTERNET뿐이고 새로운 차량/제어·특권 권한은 없습니다.

### 변경 파일·미완료·다음 작업·게시

- 앱6: MainActivity.kt, ObservationStore.kt, AndroidManifest.xml, 신규 PleosSocObserver.kt·LowSocStore.kt·ai/AiExplanation.kt. 검사3: MvpScenarioInstrumentation.kt, ValidationEnvironment.kt, 신규 SocPocChecks.kt. 문서8: AGENTS.md, SDK_CAPABILITY_MATRIX.md, docs/AUTOMATION_FOUNDATION.md·CARITDA_DESIGN_SYSTEM.md·CODEX_PROGRESS.md·신규CARITDA_VIRTUAL_DRIVING_AI_POC.md, 루트/앱 README.md. 총17파일입니다.
- 미완료/미검증: 실제 인증된 AI/외부 모델·AI동의/취소/응답저장·전체 이력 UI, 새SOC화면 전체 접근성/장시간 부하·실차·종료중상시관측·시동자동실행/FGS, P1도어/창문/속도SDK·타이어/ABS, Gleo Schema/실행·실차/고장진단·수리완료판정·푸시, Lint(기존캐시부재로재실행안함)/Release서명·제출APK. 사용자직접검수6개는이전보고로이번자동검사와구분합니다.
- 다음은 승인된중계URL/임시앱인증/모델·비용/선택데이터동의 확인 후 실제모델요청→응답→오류/취소/근거·문구/차량UI를 검증하는 것입니다. 키를채팅/소스/APK에전달하지 않습니다. 승인이없어도Console→SDK→자동안내 시연은가능하나전체AI연결완료라고발표하지않습니다.
- 검증한17파일만 최신main부모/기대HEAD를확인해 **GitHub 연결 도구를 통한 게시**하고원격SHA·부모/Aside보존을검증합니다. DB/키/백업/개인화면/로그/APK/캐시는게시하지않습니다. 최종커밋은완료응답으로보고하며자기SHA삽입반복커밋은하지않습니다.

아래는 과거 개발 기록으로 보존합니다.

---

마지막 업데이트: 2026-11-08 20:02 +09:00 (Asia/Seoul).

## 최신 작업 — 차종 호환 표시·독립 공통 메뉴·KST 조회 시각 안정화

- 기준 main26bcaed41007b5a4cd2d2db3068d48edaa4df959와 로컬 공유 대상85파일 SHA를 대조해 미게시 차이가 없음을 확인했습니다. Aside 두 연구/디자인·검사/자동 기록 자료를 다시 기준으로 삼고 공식 Vehicle SDK 문서·설치2.3.2 AAR의 공개 메서드/권한 annotation을 확인했습니다. 연구와 다른 작업의 파일을 수정하지 않습니다. Git/.git/clone/push/재설치/보안 정책 변경은 수행하지 않았습니다.
- 홈의 기존 정보/확인할 기록/두 주요 행동을 유지했습니다. VehicleDisplaySettings는 차종 미확인/전기차/내연기관/HEV/PHEV/기타의 수동 화면 배치와 차량 시각 설정만 SharedPreferences에 저장합니다. SDK 식별·VIN 디코딩·고장 판정 데이터가 아닙니다. 연료·구동 배터리·거리의 실제 지원/미수신/과거 값/실패를 분리하며 미지원을0으로 만들지 않습니다. 선택해도 수집 정책·과거 조회·불변 링크는 그대로입니다.
- 설치 AAR의 CarInfo에는 VIN/CRN 읽기·각 Capability가 있고 CAR_INFO가 필요합니다. getCapabilityInfo는 PRIVATE_CAR_INFO annotation입니다. 동력 방식/모델 직접 읽기 공개 메서드는 찾지 못했습니다. 추가 권한이나 VIN/CRN 수집·특권 map 호출은 하지 않았고 CarInfo 실제 Capability/수신은 미검증입니다. SOC 성공이나 연료 미지원으로 EV를 확정하지 않았습니다. 세부 근거·공식 링크는 VEHICLE_DISPLAY_COMPATIBILITY.md에 있습니다.
- 메뉴를 독립 MENU 화면/동일5항목·순서로 통일했습니다. 원래 본문을 같이 그리지 않고 메뉴만 선택합니다. menuReturn으로 닫기/같은 메뉴/이전과 목적지 복귀의 검색어·필터·위치/초안을 유지합니다. 좁은 창의 접힌 탭 이동은 별도 영역입니다. 원문·운전자 확정·상태/읽음/SDK 조회를 메뉴 열기로 바꾸지 않습니다.
- 차량 조회/이력/연결 시각은 기본 KST(+09:00), 선택 기기 IANA 시간대/당시 offset을22sp/본문 대비로 표시합니다. 원래 epoch와 OS America/Los_Angeles는 그대로입니다. 발견 시각/이벤트의 기존 기기 시간 의미도 유지했습니다. 상담에서 내부 내용 버전/확인 revision 차수만 숨기고 실제 재발/점검·수정/인계/상태 이력과 DB revision은 그대로입니다. P1 상담 구획 전체 재작성은 보류했습니다.

### 실제 빌드·검사와 실패 보완

- 최종3APK assembleDebug/assembleValidation/assembleValidationAndroidTest offline BUILD SUCCESSFUL in1m26s,93작업13실행/80 up-to-date. 기존 JBR/Gradle8.11.1/API34/Build-Tools35.0.0/Vehicle2.3.2. 일반 Debug APK SHA25602D2BA03BBD58F9A78977D30FB68A17A71EF0FB53B2F283583050968FE5BCCA2.
- 기존246개를 최종 소스에서 재통과했습니다: MVP24/관측6/UI27/migration18/RR36+실제cold2/NAV17+실제cold12/ON32/AM35+실제cold4+관측 사본보존2/PX25+일반3DB보존6. 신규 CP48+실제cold3=51, **총297 assertion 통과**. 최종 compat-final-*.txt와 이전 실패 로그를 로컬에 남겼습니다.
- 신규 검사: 세 출발 화면 메뉴 이름·순서/본문 분리/단일 선택·복귀,6수동 배치/지원별 정보, 유효0과 결측/미지원/이전 실패, 다른 신호/TEST_ONLY 거부, 같은 epoch의 KST/LA, 설정·초안·menu recreate/cold, 내부 번호 없는 실제 이력을 검증했습니다. 메뉴/설정의320/600/1024dp·100/200%·두 테마24상태를 추가했고 기존PX198 native render·ON200·NAV낮은 높이6도 재통과했습니다. 실제 OS 분할·IME/모든 차종·물리 접근성/사용자 이해도 통과로 확대 해석하지 않습니다.
- 첫 시간 검사 실패는 Android의 z가 KST 대신 GMT+09:00을 출력하는 것을 실제 XML에서 확인해 명시 라벨로 수정했습니다. 중간 기본 UI 검사는 dirty 초안의 전환 차단 안내가 메뉴에 남아 실패했습니다. 차단 시 작성 화면으로 돌아가 원문/현재 운전자와 안내를 유지하도록 수정하고 새 검사1개를 추가한 뒤 전체를 다시 실행했습니다. 첫 jar 경유 javap의 AccessDenied 종료와 추출 class 직접 읽기 exit0도 구분했습니다.
- 최종 실제 SDK session ed1d15fb-1df1-48bb-91bd-d874c096d4d9: SOC100%/receivedAt1794135239509, 거리0.0km/1794135239728, READ/RECEIVED·PLEOS_EMULATOR_SDK. 연료 UNSUPPORTED/ERROR_NOT_SUPPORTED_PROPERTY·값 없음. 합성 연료/차종 fixture는 validation 메모리에서만 검사했습니다. 실제 ICE/HEV/PHEV나 CarInfo 수신 성공이라고 보고하지 않습니다.
- force-stop 후 validation 실제 화면에서도 하이브리드 수동 배치와 기기 시간 설정 복원을 확인했습니다. 초안 ID/원문은 cold 저장 검사로 확인했고 화면 아래 초안 배너를 보지 않은 캡처만으로 UI 전체 성공을 주장하지 않습니다. 일반 홈/메뉴/설정 전체 앱 창과 validation KST 홈/설정 두 테마 native1024dp·cold 화면을 시각 확인했습니다.

### 최신 사용자 데이터 백업·일반 앱 적용

- emulator-5554/user10의 일반 앱을 먼저 멈추고 databases/shared_prefs/files 전체를 .tools/compat-backup/20261108-193209/before.tar와3DB로 새로 백업했습니다. tar SHA25613AEA0E77E341DB53DE5B22AA526DB6B13440820B4F282219F2CB8B9E9E1F6B2. 기존 개인 백업/AVD/과거 시험/출처 불명 데이터는 삭제하지 않았습니다. 모든 UI 계측은 validation 패키지이며 일반 앱에는 시험 문제/프로필/초안을 만들지 않았습니다.
- 모든 검증 전후 일반3DB 바이트 동일을 확인한 후 install-r 성공/COLD Status:ok. 설치 base.apk SHA256은 위 최종 APK와 같았습니다. 실제 일반 홈의 차종 미확인·100%/0km·19:57:41 KST(+09:00)·출처와 동일 메뉴를 확인했고 현재 일반 목록은 **23건**이었습니다.22건으로 고정하지 않았습니다. OS 시간대는 America/Los_Angeles로 그대로입니다.
- 설치/화면 확인 뒤 문제 DB SHA256A296015F415A8CAFE775C7E149C78EB2E46166AFCF5FD04344386FABF47D8FFB는 설치 전과 같습니다.3DB 독립 사본의 모든 원본 scalar/운전자/이벤트/초안/관측/불변 연결 포함·동일 schema version·integrity/FK를 확인했습니다. 원관측/스냅샷에는 일반 진입의 실제 결과가 추가됐고 기존 행은 남았습니다. after-install.tar·개별 사본·개인 캡처/XML도 같은 비공개 폴더에 보존했습니다.
- 보호 파일 IssueModels/CaritdaService/IssueRepository/SqliteIssueRepository/RecordMigration/PleosReadClient/ObservationStore/SnapshotStore/EntryReadState/CarSafetyGate는 기준 SHA와 동일합니다. 새 DB schema/migration·권한·상시 관측·서버는 없습니다. 실제 PC의 소스와 일반 앱에 적용됐으며 원격 문서만 바꾼 작업이 아닙니다.

### 수정 파일·미완료·다음 작업·게시

- 앱6: MainActivity.kt, domain/ConsultationSummary.kt, ui/CaritdaDesign.kt·VehiclePresentation.kt·VehicleUi.kt, 신규 ui/VehicleDisplaySettings.kt. 검사4: MvpScenarioInstrumentation.kt, ValidationEnvironment.kt, ValidationUiActions.kt, 신규 CompatibilityChecks.kt. 문서6: AGENTS.md, docs/CARITDA_DESIGN_SYSTEM.md·CODEX_PROGRESS.md·신규VEHICLE_DISPLAY_COMPATIBILITY.md, 루트/앱 README.md. 총16파일입니다.
- 미검증/미완료: 실차 동력 방식·연료/HEV/PHEV 데이터, CarInfo Capability/실제 수신, 실제 OS 분할/IME·물리 TalkBack/rotary·고령 사용자 과제, 실차 주행 제한, Lint(기존 캐시 부재로 재실행 안 함), Release 서명/실행/제출 APK. 별도 src/test 소스는 없으며 실제 계측과 빌드를 근거로 합니다. P1 상담 전체 재배치와 새 AI/회복/푸시/상시 수집은 구현하지 않았습니다.
- 다음은 공통 메뉴/차종·시간 설정의 사용자 검수와 필요한 안정화, Release/기획서·시연 영상·심사 접근 점검입니다.11월15일 제출 전 대규모 기능 확장보다 검증된 버전 확보를 우선합니다. 키·DB·개인 캡처·APK·캐시·로그는 게시하지 않습니다.
- 검증된 이번16파일만 최신 main 부모/기대HEAD로 **GitHub 연결 도구를 통한 게시**하고 원격 파일SHA·부모·연구 보존을 검증합니다. 최종 커밋은 완료 응답에 보고하며 자기SHA를 문서에 넣기 위한 반복 커밋은 만들지 않습니다.

아래는 과거 개발 기록으로 보존합니다.

---

마지막 업데이트: 2026-11-08 18:33 +09:00 (Asia/Seoul).

## 최신 작업 — Premium Automotive UI 구현·실행·데이터 보존

- 기준/게시 직전 확인 대상 main: c112d72ee3d37d10d593583e60944e7e233eaf45. 중단 직전 로컬 UI 변경과 빌드/로그를 먼저 확인해 보존했고 완료된 검증을 처음부터 반복하지 않았습니다. 네 Aside 연구와 기존 AM/NAV/ON/RR 자료, 공식 Pleos UI/Layout/Typography·현대차그룹 소개·Android for Cars 기준을 읽었습니다. 연구8개/기존 이력은 수정하지 않습니다. 로컬 Git/.git/clone/push·Codex 재실행은 수행하지 않았습니다.
- 실제 Kotlin Android View UI: 고정 적응형 탐색, 두 수치 카드/한 확인 영역/두 CTA 홈, 메뉴의 이름·도움말, 짧은 차량 기본 화면과3건 미리보기/20건 페이지·선택 조회 상세, 간편 증상 버튼·선택 정보 접기, 문제/상담·불변 연결·역순 이력을 구현했습니다. snapshotText 장문 반복 대신 구조화한 표시를 사용합니다. 주행 상태는 UI 행동 실행 시에도 현재 값을 재검사합니다.
- CaritdaDesign.kt의 색/타입/간격/상태·버튼·카드·자체 Canvas 아이콘과 VehicleUi.kt의 읽기 전용 표현을 분리했습니다. OEM 자산/로고/폰트를 복제하거나 설치하지 않았습니다. 자체 토큰과 공식 원칙의 차이·60대 이상 과제표는 CARITDA_DESIGN_SYSTEM.md/DESIGN_ACCEPTANCE.md에 기록했습니다. 새 SDK/API/DB schema/AI/자동 회복/저SOC/원격 기능은 추가하지 않았습니다.

### 이번 실제 검사와 오류 구분

- 최종 assembleDebug/assembleValidation/assembleValidationAndroidTest offline BUILD SUCCESSFUL in1m32s,93작업(10실행/83 up-to-date). 일반 APK SHA256 0425332311983A4A52F0268AAA52376B947CA17BD4E488D15E11B7234166B15A. 기존 JBR/Gradle8.11.1/API34/Build-Tools35.0.0/Vehicle2.3.2를 유지했습니다.
- 기존215개 재통과: MVP24+observation6+UI27+migration18+RR36/실제cold2+NAV17/실제cold12+ON32+AM35/실제cold4/일반 관측 사본보존2. 신규PX UI25+운영3DB 독립사본6=31, 총246 assertion 통과입니다. 구형 단계의 통과 결과를 새 앱 결과로 재사용하지 않았습니다.
- ON 최초 실행은 예전 고정 change_driver 버튼을 기다린 선택자에서 실패했습니다. 새 운전자 선택 화면의 실제 start_driving 대기만 수정했고 테스트 APK 재빌드1m2s/4실행·89 up-to-date, 일반 앱 SHA 불변 확인 후 ON32만 재실행해 통과했습니다. 이전 실패 로그도 보존합니다. 첫 Java 환경 알림을 PowerShell이 stderr 오류로 중단한 건과 실제 Gradle 성공은 구분합니다.
- 새 native198 render는320/600/1024dp·100/150/200%·두 테마·10화면 상태와 낮은 높이 홈18조합입니다. 버튼76dp/말줄임·가로 넘침/스크롤 공간/고정 탐색을 측정했습니다. 기존ON200/NAV낮은 높이6도 재통과했습니다. 실제 전체 Pleos 앱 창을 복원한 밝음·어두움 홈과 선택 조회/작성/상세를 시각 확인했습니다. 자동 검사가 사람의 이해도·모든 OS 분할·IME·물리 접근성을 증명하지 않습니다.
- 최종 실제 SDK session b1d3eb8b-4d94-47c5-b11f-0e5500c026b6: SOC100%/1794129662676, 누적거리0.0km/1794129662844, READ/RECEIVED·PLEOS_EMULATOR_SDK. 연료는 UNSUPPORTED/ERROR_NOT_SUPPORTED_PROPERTY·값 없음. 원본/단위·시각·Capability·ID/세션은 기술 상세에 보존됩니다. 에뮬레이터 기기 시간대는PST, 보고 시각은KST이며 차량 측 측정시각 미제공은 그대로입니다.

### 일반 앱 설치와 원본 보존

- emulator-5554/user10 확인, 일반 앱 force-stop·databases/shared_prefs/files 전체 바이너리 백업 후 validation에서만 계측했습니다. .tools/design-backup/20261108-173150/에 before.tar/before-validation.tar/after-install.tar와3DB 전후 사본·비공개 캡처를 보존했습니다. 이전 개인 백업도 삭제하지 않았습니다.
- 계측 전후 일반 문제/관측/스냅샷 DB는 각각 바이트 동일했습니다. 일반 앱 install-r 성공/COLD Status:ok, 설치 base.apk SHA256과 빌드 SHA 일치, 홈·차량 정보의 실제100%/0km/시각/출처·일반 목록22건을 확인했습니다. 운전자 확정이나 검증 문제/프로필/메모를 일반 앱에 추가하지 않았습니다.
- 문제 DB는 교체·화면 확인 후에도 856D222EAC66E9109F71D41AC7413358698F02424415B95C5DBC036F1D21B1DE로 바이트 동일합니다.3DB 전후 전체 원본 scalar/작성자·초안·이벤트/불변 링크 포함·동일 schema version·integrity/FK를 validation의 읽기 전용 사본에서 확인했습니다. 관측/스냅샷은 일반 진입의 실제 SDK 결과만 추가됐습니다. 기존22건·연구·AVD·과거 검증 이름/출처 불명 기록을 임의 삭제하지 않았습니다.
- 보호 backend9파일은 기준 blob SHA와 동일하고 SnapshotStore는 SELECT 페이지 조회/건수만 추가했습니다. 데이터 의미·SDK 단발 진입 상태·CarSafetyGate·영구 ID·권한·네 수동 상태·논리 삭제/초안 정책을 유지합니다. 로컬 소스와 실제 일반 앱에 반영됐으며 GitHub만 바꾼 작업이 아닙니다.

### 이번 명시적 변경 파일

- 앱4개: MainActivity.kt, vehicle/SnapshotStore.kt, 신규 ui/CaritdaDesign.kt·ui/VehicleUi.kt. 빌드 설정/manifest/아이콘 리소스·다른 backend는 변경하지 않았습니다.
- 계측7개: MvpScenarioInstrumentation.kt, AutomaticChecks.kt, NavigationChecks.kt, ReliabilityChecks.kt, OnboardingChecks.kt, 신규 DesignChecks.kt·ValidationUiActions.kt. 실제 새 버튼/메뉴·선택 접기 경로를 검사하며 숨은 가짜 UI를 두지 않았습니다.
- 문서6개: AGENTS.md, 신규 docs/CARITDA_DESIGN_SYSTEM.md·docs/DESIGN_ACCEPTANCE.md, docs/CODEX_PROGRESS.md, 루트 README.md, NextDrive-Pleos/README.md. 총17파일이며 연구/DB/키/백업/로그/캡처/APK는 게시 대상이 아닙니다.

### 미검증·미완료와 다음 작업

- 실제60대 이상2~3명 과제, TalkBack/rotary·시스템 고대비/반사광, OS 분할/회전/IME·모든 대화상자 확대, 실차/실제 주행 RESTRICTED는 미검증입니다. synthetic/UNKNOWN 안전 검사를 실차 성공으로 바꾸지 않습니다. 별도 src/test 단위 소스는 없으며 Lint는 기존 캐시 부재 상태에서 재실행하지 않았습니다.
- Release 서명/Release 실행/제출 APK·비공개 저장소 심사자 접근은 미완료입니다. Debug APK를 Release로 부르거나 키를 게시하지 않습니다. 자동 진단·AI·자동 회복·Gleo·휴대폰/정비소 연동은 구현하지 않았습니다.
- 다음은 문서 과제표로 실제 사용자 시험→막힌 문구/경로 개선→OS 분할/키보드·접근성·Release 검증입니다. 검증된 이번17파일만 최신 main을 부모/기대HEAD로 GitHub 연결 도구를 통한 게시 후 원격 파일SHA·부모·Aside 연구 보존을 확인합니다. 실제 최종 커밋은 완료 응답으로 제공하며 자기SHA 삽입을 위한 반복 커밋은 하지 않습니다.

아래는 과거 개발 기록으로 보존합니다.

---

마지막 업데이트: 2026-11-08 16:53 +09:00 (Asia/Seoul).

## 최신 작업 — 실제 SDK 자동 스냅샷 P0 구현·검증

- 기준 main/Aside 자동 기록 연구: 51254c185ef686895cfa3f1e332f65d3b35e8d99. 작업 시작 시 로컬 앱 소스/리소스/빌드 설정 SHA가 모두 기준 원격과 같았습니다. NAV·ON·RR·자동화 후속 설계와 두 Aside 연구를 읽고 진행했습니다. 로컬에 없는 연구도 원격에서 읽었으며 연구8개와 기존 이력을 보존합니다. Git/clone/push/인증을 재시도하지 않았습니다.
- P0만 구현했습니다. 일반 CARITDA 차량 아이콘과 [TEST] 플라스크 아이콘/고정 배너, 차량 상태·자동 정보·내 증상 중심 홈, 허용 진입당 실제 SDK 단발 조회, 자동 스냅샷 한 카드 갱신/변화 이력, 증상과 불변 스냅샷 선택 연결·숨김/재표시가 동작합니다. 다중 운전자/영구ID/권한·원문·이력·초안·수동 네 상태는 유지했습니다.
- EntryReadState는 구성 변경에 유지하며 렌더링·탭·freshness 표시로 재조회하지 않습니다. 동시에 한 요청만 진행하고 수동 새로 조회는 별도입니다. 첫 안내 중에는 시작하지 않습니다. onStop/제한 시 release·늦은 callback 거부, 미완료 항목은 값 없는 중단 상태를 남깁니다. listener/상시 polling/FGS/자동 고장·수리 판정은 없습니다.
- 관측 DB v2는 저장소 UUID 표만 추가합니다. 기존 observations 전체 scalar/행/ID는 보존합니다. 별도 SnapshotStore의 cards/snapshots/links/selections/pending_links가 파생 데이터/선택/재시도를 담당합니다. scope는 앱/로컬 차량 범위/저장소UUID/출처/종류이며 실제 차량 식별·ECU 측정시각은 미확인입니다. 원관측 먼저 commit, 별도 파생 transaction/replay이며 두 DB 원자성·가짜 SYSTEM 운전자를 만들지 않습니다.
- 같은 값100배치에서도 원본300행/스냅샷 카드1개/값 변화0건을 확인했습니다. 일부 신호 실패는 원래 시각의 이전 성공과 구별합니다. 사용자 증상과 연결한 snapshot은 이후 새 조회·숨김·재시작으로 바뀌지 않습니다. 저장 실패 후 선택을 바꾸는 경우의 pending 링크도 수정했고 이미 commit한 연결은 변경할 수 없음을 검사했습니다.
- 실제 validation 화면에서 링크 INSERT 실패를 주입해 증상 commit/지속 부분 실패 안내/같은 issue ID의 확인창 재시도를 검증했습니다. 증상 저장과 차량 연결 실패를 혼동하지 않습니다. 원문/발견 시각·상태/점검·인계/재발은 바뀌지 않으며 차량값을 소음 원인이나 과거 발견 시각의 값으로 단정하지 않습니다.

### 실제 빌드·검사·SDK 결과

- 기존 JBR/Gradle8.11.1/API34/Build-Tools35.0.0/Vehicle2.3.2 캐시 offline assembleDebug+assembleValidation+assembleValidationAndroidTest 최종 BUILD SUCCESSFUL in1m21s, exit0,93작업 중13실행/80 up-to-date. 일반 APK SHA256 BF85AB3E7B11D571B877AB55CB9B6000EE3B462C8D8FC0E8625C1E944B19E0A5. 최종 pending 링크 수정 뒤 전체 회귀를 다시 실행했습니다.
- CARITDA_AM_PASS35 + 실제 force-stop 뒤 AM_COLD4 + 일반 관측 사본 PRESERVATION2 = 신규41 assertion 통과. 기존 NAV17+12, RR36+2, migration18, MVP24, observation6, UI27, ON32 = 기존174개도 최종 소스로 재통과했습니다. 총215개이며 AM36/AC/ON/NAV 인수기준 전부나 일반 사용자 이해도·실차가 통과했다는 뜻은 아닙니다.
- 최신 실제 validation SDK: session7b46d17e-9f09-4efa-81aa-76984fb7d162, EV_SOC100%/receivedAt1794123861706, ODOMETER0.0km/receivedAt1794123861716, READ/RECEIVED·PLEOS_EMULATOR_SDK. 연료 ERROR_NOT_SUPPORTED_PROPERTY/UNSUPPORTED·값 없음. 숫자를 앱에 고정하지 않았으며 SYNTHETIC_VALIDATION_ONLY와 TEST_ONLY 시험은 별도 이름의 validation fixture DB에만 있습니다.
- 일반 앱 install -r·COLD 실행, 버튼 없는 자동 조회·홈100%/0km·22건·TEST 없음과 SDK 출처/시각/측정시각 미제공을 실제 확인했습니다. 설치된 base.apk의 SHA256이 위 빌드 APK와 일치합니다. 검증된 코드가 사용자 PC와 일반 앱에 실제 반영됐으며 GitHub만 변경한 작업이 아닙니다. 운전자 확정/검증 문제·프로필·메모를 일반 앱에 추가하지 않았습니다.
- 기존 ON200개 화면/폭/글꼴/테마 및 NAV 낮은 높이6개 조합 재통과. 새 스냅샷 화면의 전 행렬·실제 OS 분할/IME·TalkBack/rotary·장시간 화면 신선도·일반/고령 사용자 과제·실차·실제 주행 RESTRICTED·실제 서비스 권한 거부/timeout/저 SOC는 미검증입니다. Lint는 기존 lint-gradle 캐시 부재 상태에서 재시도하지 않았고 통과로 보고하지 않습니다.

### 백업·원본 보존과 환경 오류

- emulator-5554/user10을 확인하고 일반 앱 force-stop 후 전체 databases/journal·shared_prefs/files를 .tools/automatic-backup/20261108-161755/before.tar 및 개별 DB로 바이너리 백업했습니다. tar SHA2564BB1454AAC8CB17976E75A821216B8ADD97E9EB98B271426CBFBB082A84224EF. 기존 백업을 삭제하지 않았습니다.
- 관측 v1 최신 원본의 독립 사본에서 이전 실패 rollback/version/원본 전체, 성공 보존, 반복 이전/재열기/identity 안정성을 확인한 뒤 운영 적용했습니다. 처음 validation 전후 일반 문제 DB F284705F5845FD82749D31346776BB52255A03B053E184768F88ACDE827A8A35, 관측 DD89E12F70919ED6B997CFFCD89DFC520F4EB3824B6EB70372375D64DF6D4263는 바이트 동일했습니다. 최종 재검사 때도 직전 백업의 두 DB와 같았습니다.
- 일반 앱 적용/실제 조회/재부팅 후 문제 DB는 위 F284705F… 그대로입니다. 관측 DB에는 metadata 및 실제 SDK 수신이 추가됐으므로 파일 해시가 같다고 주장하지 않습니다. validation의 독립 after 사본에서 모든 기존 observation scalar/행/ID 보존과 integrity_check=ok를 최종 재확인했습니다. 사용자 운전자·문제·이벤트·초안과 과거 검증 기록을 삭제/숨김 정리하지 않았습니다.
- 처음 아이콘 빌드에서 색 literal의 drawable 참조 오류가 있었고 @color로 수정했습니다. 초기 AM 집계에서 증거1줄을 assertion으로 센 것도 수정했습니다. 마지막35는 PASS 줄만 센 수입니다.
- 최종 APK 설치 Success 뒤 에뮬레이터가 headless user0 BOOTING/user10 중지 상태로 변해 Activity does not exist, /sdcard I/O/Transport endpoint 오류와 UiAutomation timeout이 났습니다. 같은 시점 SystemUI DeadSystemException이 있었지만 근본 원인을 확정하지 않습니다. before-final-patch.tar와 실패 로그를 보존하고 기존 AVD의 정상 adb reboot를 한 번 수행했습니다. 초기 activity service 미준비 후 boot_completed1/user10 복귀, 최종 앱 COLD 실행/실제 자동 조회·원본 보존을 확인했습니다. AVD wipe/새 AVD/앱 데이터 삭제·사용자 강제 전환·SDK/Studio 재설치·전역 설정 변경은 하지 않았습니다.
- GNB 일반task1000210/검증task1000211, user10/display0·각 launcher1개·top-resumed/focused package가 일치했습니다. 안정 화면에서 최신 앱이 왼쪽으로 재정렬되고 노란 표시는 해당 차량/플라스크 아이콘 아래에 표시됐습니다. 위치 고정처럼 보이는 현상은 있었지만 패키지 실행 불일치 지속 오류는 재현하지 못했습니다. 이전 원인은 미확정이며 OS UI를 수정하지 않았습니다. 기기 America/Los_Angeles 시간대도 유지했습니다.

### 인수기준·미구현·제출

AM01~36은 통과14/부분10/미검증1/이번 범위 밖11입니다. 자세한 근거·제약·실패와 복구·사용 방법은 docs/AUTOMATIC_RECORDING_ACCEPTANCE.md에 있습니다. 실제 자동 assertion41과 AM36을 구분합니다. 사용자 최신 P0/P1 지시에 따라 low SOC 설정/알림·listener·경고등 안내는 보류했고, 자동 진단/회복/상태 변경/Gleo/폰·FCM/정비소/상시 마이크·백그라운드 감시는 구현하지 않았습니다.

시연 가능: 일반 앱 진입→실제 차량값 단발 자동 기록→카드 확인→소음/진동 직접 기록+선택 연결→점검 메모/수동 상태→재실행 후 원본·연결 유지. Release 서명 설정/서명 APK·실행 검증/제출 파일 위치는 미완료입니다. 현재 Debug APK를 Release라고 표시하지 않습니다. 저장소는 Private이며 심사 계정 접근·기획서/동의서 서명은 사용자 확인 과제입니다. 키 생성/게시·공개 전환·외부 초대/제출은 하지 않았습니다.

### 이번 변경 파일33개와 공유

- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/AutomaticChecks.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/OnboardingChecks.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/ValidationEnvironment.kt
- NextDrive-Pleos/app/src/main/AndroidManifest.xml
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/EntryReadState.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/PleosReadClient.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/SnapshotStore.kt
- NextDrive-Pleos/app/src/main/res/drawable/ic_mark.xml
- NextDrive-Pleos/app/src/main/res/mipmap-anydpi/ic_launcher.xml
- NextDrive-Pleos/app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
- NextDrive-Pleos/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- NextDrive-Pleos/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
- NextDrive-Pleos/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml
- NextDrive-Pleos/app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml
- NextDrive-Pleos/app/src/main/res/values/icon_colors.xml
- NextDrive-Pleos/app/src/validation/res/drawable/ic_mark.xml
- NextDrive-Pleos/app/src/validation/res/mipmap-anydpi/ic_launcher.xml
- NextDrive-Pleos/app/src/validation/res/mipmap-anydpi/ic_launcher_round.xml
- NextDrive-Pleos/app/src/validation/res/mipmap-anydpi-v26/ic_launcher.xml
- NextDrive-Pleos/app/src/validation/res/mipmap-anydpi-v26/ic_launcher_round.xml
- NextDrive-Pleos/app/src/validation/res/mipmap-anydpi-v33/ic_launcher.xml
- NextDrive-Pleos/app/src/validation/res/mipmap-anydpi-v33/ic_launcher_round.xml
- NextDrive-Pleos/app/src/validation/res/values/icon_colors.xml
- NextDrive-Pleos/app/build.gradle.kts
- AGENTS.md
- docs/AUTOMATIC_RECORDING_ACCEPTANCE.md
- docs/AUTOMATION_FOUNDATION.md
- docs/CODEX_PROGRESS.md
- README.md
- NextDrive-Pleos/README.md

검증된 위 파일만 GitHub 연결 도구를 통한 게시로 main의 최신 트리·부모를 이어 공유합니다. 게시 직전 대상 SHA/HEAD 및 게시 후 blob/부모·나머지 파일/Aside8개를 확인합니다. 로컬 Git push 성공으로 표현하지 않습니다. DB·개인 백업·키·APK·캐시·로그/화면은 제외합니다. 자기 자신의 SHA를 보고서에 넣으려는 반복 커밋 없이 최종 SHA는 응답에서 보고합니다.

다음 사용자 확인: 차량 모양 일반 앱→이름 선택/운전 시작→자동 차량 정보→문제 남기기/소음·진동/차량 정보 연결 선택→저장→점검 메모/상태·정비 요약→재실행. 자동값은 증상 당시 값/고장 진단이 아닙니다. 파일 수동 편집·데이터 초기화는 필요 없습니다. 다음 단계는 새 화면 접근성/신선도·실제 사용자 과제와 안전한 Release 준비이며 P1/P2는 별도 작업입니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-08 15:11:09 +09:00 (Asia/Seoul).

## 최신 작업 — 고정 홈·검색 실행 검증 및 일반 앱 교체 설치 완료

### 중단 지점 재개와 데이터 보존

- 사용자가 기존 Pleos Connect v2.1.0을 실행한 뒤 이어서 진행했습니다. 시작 main은 acb615068e61a2b8a5830389e266e21ff61ff68b이며 이전에 보류한 로컬 소스8개와 빌드된 APK의 시각/해시를 확인했습니다. 연구7개와 다른 작업 결과를 보존합니다. 로컬 Git/인증/clone/push는 재시도하지 않았습니다.
- ADB emulator-5554 device, 현재 Android user10 연결을 확인했습니다. 일반 앱을 force-stop하고 databases/journal·shared_prefs·files 전체를 .tools/navigation-backup/before.tar 및 개별 문제/관측 DB로 바이너리 보관했습니다. tar SHA256 1095df849caccc3ba4edfe60567554328314986a90141be9658e87c676148c91. 기존 백업은 지우지 않았습니다.
- validation 검사 전후 일반 문제 DB SHA256 1e5d28543a72fc34fad3c2af076b702ed3c074fdcc275d2e9f7ee5e40327717f, 관측 DB SHA256 dd89e12f70919ed6b997cffcd89dfc520f4eb3824b6eb70372375d64df6d4263가 같았습니다. 이번 새 검증 문제·프로필·메모는 별도 validation UID/DB에서만 작성했습니다.
- 주요 검사 통과 뒤 일반 APK install -r Success, user10 COLD 실행 Status ok. 일반 앱에서는 운전자를 확정하거나 기록/상태/읽음/센서 조회를 변경하지 않고 홈·전체 목록 스크롤·차량 정보·사용법 화면만 확인했습니다. 앱 패키지를 지정한 조회 조작이며 운영 대상 instrumentation은 실행하지 않았습니다.
- 일반 앱의 전체 문제 기록22건 표시와 스크롤 전후 고정 홈의 동일 화면 좌표를 확인했습니다. 교체 설치·조회 후에도 두 DB가 위 백업 해시와 동일했습니다. 기존 운전자·원문·이벤트·초안·관측/논리 삭제 정보를 바이트 단위로 유지했습니다. AVD/앱/사용자 DB 초기화나 과거 검증 기록 정리는 하지 않았습니다.

### 구현 동작과 실제 검사

- 고정 앱 홈/이전/사용법 복귀 분리, 좁은 창 홈·메뉴/넓은 창 세 영역 탐색을 실제 앱에서 확인했습니다. 홈은 OS 홈 intent·운전자 확정·기록 저장/삭제·상태 변경을 하지 않습니다. 홈 반복과 이전 상세 부활 방지도 검증했습니다.
- 홈 전체/내 작성, 네 상태 필터, 카테고리·추가 메모·미리보기100자 밖 전체 본문 검색, 검색0건과 빈 저장소 구분, 조건 초기화, 상세 이전의 필터/검색/스크롤 위치를 검증했습니다. 내 작성은 영구 ID 기준입니다.
- 새/수정 초안의 홈·이전 이동/프로세스 중지 후 복원, 초안 쓰기 실패 시 이동 차단과 입력 유지, 수정 취소/같은 ID 수정/변경 없는 저장/버전 충돌을 검증했습니다. N29는 버전이 달라진 무변경 편집도 조용히 초안을 제거하거나 덮어쓰지 않음을 확인합니다.
- 네 수동 상태 변경은 유지했고 전후 상태·운전자·시각과 기존 원문/재발/점검 이력 보존을 확인했습니다. 사용자가 확인한 상태 변경을 버그로 다시 처리하지 않았습니다. 점검 완료와 해결 확인, 내용 버전과 재발 횟수의 표시를 구분합니다.
- 신규 CARITDA_NAV_PASS checks=17, 실제 force-stop 뒤 CARITDA_NAV_COLD_PASS checks=12. 신규29개 assertion 통과입니다. Aside NAV01~28 자체가 모두 통과했다는 뜻은 아닙니다.
- 기존 CARITDA_RECORD_PASS checks=36, CARITDA_COLD_PASS checks=2, MIGRATION_PASS checks=18, CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6, CARITDA_UI_PASS checks=27, CARITDA_ON_PASS checks=32. 기존145+신규29 = 총174개 자동 검사 통과입니다. 위험 검사는 모두 validation 앱에서 수행했습니다.
- ON 주요 화면200개 폭/글꼴/테마 조합과 NAV의 낮은 높이320dp에서 폭320/600/1024dp × 글꼴100/200% 6개 조합을 확인했습니다. 실제 OS 분할/IME·물리 장비의 전 범위 검사는 아닙니다.
- SDK2.3.2 실제 validation 수신은 SOC100%/누적 주행거리0.0km READ/RECEIVED, 연료 ERROR_NOT_SUPPORTED_PROPERTY/UNSUPPORTED·값 없음입니다. source=PLEOS_EMULATOR_SDK, 세션 ad950a11-e23f-4168-8dee-e5661c077445. 원본·단위·수신 시각·세션 저장과 재시작 복원을 확인했습니다. 실차/회복 판정이 아닙니다.
- 일반 앱의 과거15%/1000000.0km는 원래 시각·이전 값·오래됨·에뮬레이터 출처로 표시됐습니다. validation100%/0km와 합쳐 회복 증거로 쓰지 않았습니다. 새 SDK API·권한·스키마·자동 상태 변경·Gleo·휴지통 복원은 없습니다. 자동화는 기존 후속 설계 문서만 유지합니다.

### 빌드·초기 실패·미검증

- 기존 JBR/Gradle8.11.1/API34/Build-Tools35.0.0/SDK2.3.2 캐시 offline assembleDebug+assembleValidation+assembleValidationAndroidTest 최종 BUILD SUCCESSFUL in54s, exit0,93작업 중4실행/89 up-to-date. 마지막 변경은 계측 보강이며 일반 앱 소스/APK는 전 회귀와 동일합니다. APK SHA256 53383639ce3a01e76f749880e23ebd9bb27991d142ee9845ae4742a3c2d539ca.
- 첫 NAV는 N14가 루트 Back을 무조건 Activity 종료라고 가정해 실패했습니다. Android12+의 정상 백그라운드 동작을 공식 자료와 대조하고, 요구사항인 이전 상세 부활 방지를 검사하도록 수정했습니다. 앱을 강제 종료하도록 바꾸지 않았습니다. 이후 N01~29 통과했고 첫 실패 로그도 보존했습니다. 현재 주요 기능 검사 실패는 없습니다.
- 지난 회차의 ADB 서버 실패와 보조 호스트 javac AccessDenied 실험 실패는 아래 과거 사실로 남깁니다. 이번 ADB/Android 실행이 성공했으나 그 호스트 실험을 성공으로 바꿔 기록하지 않습니다.
- NAV 기준 판정은 통과11/부분9/미검증2/조사 전용·범위 밖6입니다. 상세 표는 docs/NAV_ACCEPTANCE.md 최신 절에 있습니다. 부분 항목의 모든 OS 중단·진짜 주행 RESTRICTED·확인창 경합·focus/커서·독립 contentVersion·휴지통 복원·stale 상담·OS 분할/IME는 완료라고 주장하지 않습니다.
- TalkBack/물리 키보드/rotary·일반/고령 사용자 독립 과제·실차·Release·Lint는 미검증/미완료입니다. 기존 자동화 연구 REC는 설계이며 실제 회복 엔진 검증이 아닙니다.

### 파일·공유·사용 방법

로컬과 검증된 게시 대상12개 파일:

- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/CaritdaService.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/ConsultationSummary.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/SqliteIssueRepository.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/RecordSearch.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/OnboardingChecks.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/NavigationChecks.kt
- docs/NAV_ACCEPTANCE.md
- docs/CODEX_PROGRESS.md
- README.md
- NextDrive-Pleos/README.md

위 소스는 실제 사용자 PC의 NextDrive-Pleos에 반영됐고 일반 APK도 교체 설치했습니다. GitHub 연결 도구를 통한 게시를 사용하며 최신 main/대상 SHA·커밋 부모·blob SHA·Aside 포함 나머지 파일을 확인합니다. 로컬 Git push가 아닙니다. 연구 문서는 수정하지 않고 DB/개인 백업/키/APK/캐시/로그/화면은 게시하지 않습니다. 이 보고서에 자기 자신의 SHA를 넣으려는 반복 커밋은 하지 않으며 최종 SHA는 응답에서 보고합니다.

사용자는 일반 CARITDA에서 이름 선택→운전 시작→전체 문제 기록/내가 작성한 문제→검색/네 필터/조건 초기화→상세→이전→고정 홈 순서로 확인해 주세요. 기존 초안은 원 작성자로 이어 쓰고, 수정 취소는 원문을 유지합니다. 파일 수동 수정·재설치·데이터 초기화는 필요 없습니다. 다음 단계는 부분/미검증 접근성·실제 창/IME·사용자 이해도 검증이며 자동 회복/추가 SDK/Gleo/복원은 별도 승인된 개발 단계입니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-08 13:22:34 +09:00 (Asia/Seoul).

## 최신 작업 — 고정 홈·검색 로컬 구현/빌드, 에뮬레이터 검증 대기

### 기준과 실제 구현

- 최신 main aee62bf2548470ee11bfd1e5154639e2ce79e1e5와 필수 로컬 파일31개 SHA가 일치함을 확인했습니다. Aside의 CARITDA_NAVIGATION_RECORD_GLEO_UX와 CARITDA_RECOVERY_STATUS_AUTOMATION_RESEARCH가 둘 다 존재하여 전체 기준과 NAV/REC를 읽었습니다. 기존 RR/AC/ON/진행 기록·v3 소스·SDK·관측·validation 구조를 확인했습니다. 연구 파일은 수정하지 않았습니다.
- 사용자가 네 수동 상태 변경 정상 동작을 확인한 사실을 유지합니다. 이미 해결된 문제를 재발 버그로 간주하지 않았습니다. 이번 Codex 실행 검증과 사용자 확인을 구분합니다.
- 본문 ScrollView 밖 고정 홈 바를 구현했습니다. 충분한 창은 홈/기록/차량 정보, 좁거나 큰 글꼴/입력 화면은 홈/메뉴입니다. 홈은 내부 HOME과 중첩 이력 정리, 이전은 직전 맥락, 사용법은 원래 화면 복귀입니다. OS HOME intent나 운전자 확정·기록 삭제·수동 상태 변경을 호출하지 않습니다.
- 홈의 전체 문제/내 작성/문제 남기기/차량 정보/사용법, 네 필터와 전체 본문/메모/카테고리 로컬 검색·현재 조건/결과 수·조건 초기화를 구현했습니다. 상세 복귀의 검색/필터/가능한 스크롤 위치를 보존합니다. 내 작성은 영구 ID 기준이며 미확정이면 명시 이름 선택 뒤 목표로 연결합니다.
- 수정 후 같은 ID 상세와 이전 목록 조건을 유지하고 검색 불일치를 설명합니다. 수정 취소는 원문 상세로 돌아가고, 변경 없는 수정은 초안만 끝내며 새 이벤트/revision을 만들지 않습니다. 기존 작성자/삭제 권한과 논리 삭제/이력은 그대로입니다. 복원/영구 파기 UI는 추가하지 않았습니다.
- 홈/이전 이동 전 초안 저장 실패를 확인해 입력 화면에 남기도록 했습니다. 소스의 기존 revision 의미를 유지하며 내용 버전과 이벤트 기반 재발 횟수를 나눠 표시합니다. 상태 선택에는 점검 완료/해결 확인의 뜻을 설명합니다. 수동 STATUS 이벤트 저장 로직은 유지했습니다.
- 새로운 DB/권한/SDK API/자동 회복/AI/Gleo 기능은 없습니다. docs/AUTOMATION_FOUNDATION.md에 future sidecar의 출처·행 참조·최신 시도·issue state token/evidenceEpoch·두 DB 경합/원자 승인/유보/중복 방지 설계만 기록했습니다. 공식 권한 문서와 샘플 PublicApiPolicy를 재확인했고 새 접근이 가능한 것으로 과장하지 않습니다.

### 실제 빌드와 현재 장애

- 기존 Windows/JBR/Gradle8.11.1/API34/Build-Tools35.0.0/Pleos SDK2.3.2 캐시의 offline assembleDebug+assembleValidation+assembleValidationAndroidTest 최종 성공: BUILD SUCCESSFUL in38s, exit0,93작업 중4실행/89 up-to-date. 일반 APK SHA256 53383639ce3a01e76f749880e23ebd9bb27991d142ee9845ae4742a3c2d539ca.
- 시작 시 Studio/에뮬레이터가 실행 중이지 않았고 ADB 127.0.0.1:5037 연결은 10061 connection refused였습니다. 기본 adb start-server도 could not read ok from ADB Server / failed to start daemon으로 실패했습니다. 원인은 확정하지 않았으며 권한 확대/보안 변경/다른 AVD 복제 등으로 우회하지 않았습니다. 사용자에게 일반 Android Studio에서 기존 Pleos_Connect_v2.1.0 실행을 요청했습니다.
- 따라서 이번에 일반/validation APK를 교체 설치하지 않았고 차량 SDK 값을 새로 수신하지 않았습니다. 사용자 DB의 새 백업/전후 해시 비교도 실행하지 못했습니다. 기존22건이나 이전 성공 수신값을 이번 검증 성공으로 재사용하지 않습니다. DB 삭제·초기화·과거 검증 기록 정리는 하지 않았습니다.
- 보조 호스트 Java 검색 실험도 시도했으나 classpath/파일 realPath의 AccessDeniedException으로 실행하지 못했습니다. APK 공식 Gradle 빌드 성공과 별개이며 검색 검사8개가 통과했다고 세지 않습니다. 실험 파일은 build/validation에만 남겼습니다.

### 검사 준비/미검증

- 신규 NavigationChecks N01~17과 프로세스 종료 뒤 N18~28을 작성하고 테스트 APK로 컴파일했습니다. 고정 홈, 네 필터, 긴 본문/메모/카테고리 검색,0건/초기화, 상세 이전, 새/수정 초안, 취소/무변경 수정, 네 상태 전후/actor/time, 초안 SQL 쓰기 실패, UNKNOWN,낮은 높이6조합을 검사하도록 준비했습니다. **28개는 작성한 assertion 수이며 실행/통과 수가 아닙니다.**
- 기존 Mvp/Onboarding 검사는 실제 이전 버튼 tag를 home에서 back으로 바꾸어 의미를 구분했습니다. 기존 AC/ON/RR 및 운영 DB 불변 검사는 이번에는 미실행입니다. 과거145개 성공은 이전 코드의 기록으로만 보존합니다.
- Aside NAV01~28별 구현·부분·범위 밖·실행 미검증은 docs/NAV_ACCEPTANCE.md에 적었습니다. Gleo A/B/C와 휴지통 제안은 실제 구현과 구분합니다. REC는 후속 설계이며 자동 회복을 실현하거나 판정한 적이 없습니다.
- TalkBack/키보드/rotary·실제 OS 분할/IME·실제 주행 제한 전환·실차·사용자 이해도는 미검증입니다. 기존 Lint 캐시 부족도 성공으로 바꾸지 않았습니다.

### 파일과 게시 범위

로컬에서 변경한 앱/계측 파일(미게시):

- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/CaritdaService.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/ConsultationSummary.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/SqliteIssueRepository.kt
- 신규 NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/RecordSearch.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/OnboardingChecks.kt
- 신규 NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/NavigationChecks.kt

이번에 공유 가능한 문서4개: AGENTS.md, 신규 docs/AUTOMATION_FOUNDATION.md, 신규 docs/NAV_ACCEPTANCE.md, docs/CODEX_PROGRESS.md. 문서·경로·공유 안전 검사와 최신 HEAD/대상 SHA 확인 후 **GitHub 연결 도구를 통한 게시**를 사용합니다. 실행 미검증 앱 소스는 제외하고 로컬에 보존합니다. 로컬 Git push가 아닙니다. 원격 부모/파일 SHA와 Aside·나머지 파일을 보존하고 커밋은 응답에 보고합니다.

### 다음에 이어갈 작업

사용자는 Android Studio → Device Manager → 기존 Pleos_Connect_v2.1.0의 ▶만 실행하고 차량 화면 부팅을 알려 주세요. 기존 AVD/앱/데이터를 초기화할 필요는 없습니다. 그 뒤 실제 serial/current user 확인→새 개인 백업→validation NAV 두 단계와 AC/ON/RR/이전/SDK 회귀→사용자 DB 불변 확인→일반 APK install-r/화면 확인→검증된 소스 게시 순서로 계속합니다. 새 기능 명세나 별도 업로드 프롬프트는 필요하지 않습니다. 현재 일반 앱이 새 UI로 실행된 것으로 보고하지 않습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-08 01:35:34 +09:00 (Asia/Seoul).

## 최신 작업 — 사용자 기록 신뢰성·본인 수정/논리 삭제·테스트 격리

### 재현한 원인과 실제 변경

- 기준 main 5f753a2ebf97283993d8a6f97eb8214b98af25db와 로컬 필수 파일25개 해시가 일치함을 확인하고 시작했습니다. 현재 Android user10의 실제 앱을 force-stop한 뒤 DB/journal·설정·files를 .tools/reliability-backup에 바이너리 백업했습니다. user0의 빈 폴더를 데이터 부재로 오인하지 않았습니다.
- 수정 전 동일 앱 소스를 독립 validation 앱으로 실행해 RELIABILITY_BASELINE checks=4를 확인했습니다. 정상 입력의 commit/상세 이동은 성공했습니다. 완료 필터가 새 관리 중 기록을 숨기는 현상, 새 기록→홈→초안 이어쓰기→저장→뒤로 두 번으로 초안이 없는 입력 화면이 되살아나 journal ?: return이 아무 안내 없이 끝나는 현상을 재현했습니다. 사용자 환경의 모든 과거 실패가 SQLite 손실이라는 증거는 없었습니다.
- 저장 결과/등록 이력을 commit 후 읽어 확인하고 ‘저장되었습니다’와 상세 이동을 제공합니다. 저장한 폼의 과거 경로를 제거하고 전체 기록으로 돌아갑니다. 지속 피드백을 스크롤 밖 상단에 두고, 빈 본문 필드 오류·다시 저장·입력/초안 보존을 구현했습니다. 새 입력에서 과거 성공 메시지는 지웁니다. 연속 탭/같은 초안 ID는 중복을 만들지 않습니다.
- 전체 기록/관리 중/완료/내가 작성한 기록 필터와 명시적 선택/건수/전체 복귀를 추가했습니다. 홈·저장 후 목록은 전체 기록을 열고 다른 메뉴 복귀의 선택 필터는 화면에 표시합니다.
- 확정 운전자 영구 ID가 원 작성자와 같을 때 카테고리/본문/메모 수정·삭제를 허용합니다. 발견 시각은 유지합니다. 수정 전후 내용/시각/수정자 이벤트와 revision을 남겨 이전 읽음과 구분합니다. 이름 변경 뒤에도 ID 권한과 원 작성 snapshot을 보존합니다.
- 삭제는 대상 문구가 있는 확인창 승인 후 논리 삭제이며 일반 목록/상담에서 제외합니다. 원본/ID/연결 이력과 사전 백업은 남습니다. 복구·영구 파기 UI는 없으며 개인정보의 영구 삭제라고 표시하지 않습니다. 기존 기록을 개발 중 임의 삭제하지 않았습니다.
- 문제 DB v3는 v2에 deleted_at/deleted_by_id만 추가합니다. 운전자·문제·인계·점검·재발·초안과 관측 DB/Vehicle SDK2.3.2/PleosReadClient/CarSafetyGate는 유지했습니다.

### 테스트 격리와 기존 데이터 보존

- validation 빌드(applicationId com.nextdrive.pleos.validation, 표시명 CARITDA 검증 전용)에서 같은 앱 UI/SDK/안전 코드를 실행합니다. DB/preferences/files/UID가 일반 앱과 다르며 runner는 다른 패키지를 DB 준비 전 거부합니다. validation fixture만 준비/초기화합니다. 정상 자동화는 assembleValidationAndroidTest를 사용합니다.
- 예전 운영 대상 계측 APK도 새 보호 코드로 교체했습니다. 실제 운영 대상 거부 검사에서 checks=0/Refusing tests against the user's application을 확인했고 문제/관측 해시가 그대로였습니다. 의도한 거부 검사이며 운영 UI를 테스트한 성공으로 표현하지 않습니다.
- 백업 사본은 문제22건입니다. 보존 로그의 UI_ISSUE_ID와 일치하는5건만 Codex 작성 입증, 나머지17건은 출처 미확정으로 보존했습니다. 사용자 작성 확인 ID가 없다는 것은 실제 사용자 기록이 없다는 뜻이 아닙니다. ‘검증용’ 문구만으로 자동 삭제/숨김을 하지 않았습니다. 개인 검토 목록은 .tools/reliability-backup/RECORD_CLEANUP_REVIEW.md와 JSON에만 저장했습니다.
- validation 전체 검사 뒤 운영 문제/관측 DB 해시가 사전 백업과 동일해 테스트 데이터 미유입을 확인했습니다. 기존 프로필의 이름/보관 상태도 바꾸지 않았습니다.
- 운영 적용 전 v2 백업 사본에서 실패 주입 rollback/원본 모든 필드/관계/반복 이전을 검사했습니다. 이후 일반 앱 APK install -r Success, 기존 Pleos Connect v2.1.0/API34 emulator-5554 user10의 COLD 실행 Status ok를 확인했습니다.
- 운영 v3 DB를 다시 사본으로 가져와 v2와 모든 기존 driver/issue/event/setting/draft 열과 행이 동일함을 검증했습니다. PRODUCTION_V3_PRESERVED, 삭제 표식0건·외래키 정상입니다. 관측 DB SHA256 dd89e12f70919ed6b997cffcd89dfc520f4eb3824b6eb70372375d64df6d4263도 불변입니다.

### 빌드·실제 검사 결과

- 기존 Windows/JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0 캐시 offline assembleDebug+assembleValidation+assembleValidationAndroidTest: 기능 소스 빌드1m22s 성공, 최종 보호 runner 구성 확인41s(93개 up-to-date) exit0. 보호용 debugAndroidTest45s 빌드 성공. 운영 APK는 동일 SHA256 40d1b1d21596d0fedc09bc9ddc16e858d6a280d47f4457713d34145280e977ee입니다.
- CARITDA_RECORD_PASS checks=36(새 RR01~30과 사본 이전/분류6), 별도 force-stop 뒤 CARITDA_COLD_PASS checks=2(RR31~32). 실제 SQLite 등록/수정/삭제 이벤트 INSERT 실패를 주입해 롤백/입력 유지/같은 ID 재시도를 검사했습니다. 데이터 위험 검사는 모두 validation에서 수행했습니다.
- 기존 MIGRATION_PASS checks=18, CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6, CARITDA_UI_PASS checks=27, CARITDA_ON_PASS checks=32. 합계145개이며 별도 운영 보존/거부 검사도 확인했습니다. ON 주요 화면200개 폭·글꼴·테마 조합을 새 필터/피드백 소스로 재검사했습니다. 기존 AC/ON의 사람·실차·접근성 미검증을 전부 통과로 바꾸지 않았습니다.
- 이번 실제 SDK 결과는 validation 앱에서 SOC100%/누적 주행거리0.0km READ/RECEIVED, 연료 ERROR_NOT_SUPPORTED_PROPERTY/UNSUPPORTED입니다. PLEOS_EMULATOR_SDK이며 실차/앱 가상값과 구분합니다. 이전15%/1000000.0km 기록은 과거 사실로 보존합니다. Console 숫자 입력은 하지 않았습니다.
- 로컬 로그는 app/build/validation/reliability-*.txt에 있습니다. 백업/DB/개인 정리 목록/APK/서명 키/캐시/로그/화면은 게시하지 않습니다.

### 오류·Virtual Console·남은 제한

- 최초 validation 서명 검증은 .tools/gradle-home/.android/debug.keystore.lock AccessDeniedException으로 실패했습니다. 기존 키를 바꾸지 않고 프로젝트 내 새 경로에 같은 키를 복사해 선택적 caritdaDebugKeystore 속성으로 빌드했습니다. 시스템 보안/ACL/정책 변경이나 SDK 재설치는 하지 않았습니다.
- ADB emulator-5554 device 및 forward 목록 emulator-5554 tcp:8088 tcp:8088, forward 재설정 exit0, http://127.0.0.1:8088/ HTTP200/title Pleos Connect Virtual Console/body150815bytes를 확인했습니다. 현재 브라우저 연결 실패는 재현되지 않았고 포트 소유 PID 조회는 출력 없어 확정하지 않았습니다. 다른 프로세스 종료/AVD 초기화 없이 정상 응답을 확인했습니다. Console 숫자 최댓값 문제는 별도 미재현이며 앱 저장 오류 원인으로 단정하지 않습니다.
- 실제 디스크 고갈·모든 과거 사용자 실패·실제 주행 제한 전환·실차·Release·TalkBack/rotary·신규/고령 사용자 이해도는 미검증입니다. 기존 offline Lint 캐시 부재는 재시도하지 않았고 Lint 통과로 보고하지 않습니다.
- Gleo는 미연동입니다. 향후 선택한 미삭제 기록의 ID/revision/본문 snapshot에 대한 사용자 확인·동의와 공식 Action/Callback 접근 검증이 필요하다는 메모만 남겼습니다. 가짜 AI 답변/상담 버튼은 없습니다.

### 변경 파일·공유·다음 작업

사용자 PC에 실제 반영한22개 소스/설정/문서:

- AGENTS.md
- README.md
- NextDrive-Pleos/README.md
- NextDrive-Pleos/app/build.gradle.kts
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/model/IssueModels.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/CaritdaService.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/ConsultationSummary.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/IssueRepository.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/SqliteIssueRepository.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/data/RecordMigration.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/OnboardingChecks.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MigrationChecks.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/ValidationEnvironment.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/RecordMigrationChecks.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/ReliabilityChecks.kt
- docs/RECORD_RELIABILITY.md
- docs/UI_MIGRATION_PLAN.md
- docs/ONBOARDING_ACCEPTANCE.md
- docs/UI_ACCEPTANCE.md
- docs/CODEX_PROGRESS.md

새 인수기준/원인/정책/명령은 docs/RECORD_RELIABILITY.md에 있습니다. 기존 AC/ON/이전 문서에는 최신 validation 경로와 과거 운영 명령 실행 금지를 추가하고 과거 결과는 보존했습니다.

작업 중 Aside가 dc6eb046a638e55cd23ae8f21e8cfa050add2858로 docs/research/CARITDA_NAVIGATION_RECORD_GLEO_UX.md만 추가했습니다. 대상 파일 SHA 충돌이 없음을 확인했고 이 최신 트리/부모를 이어 게시합니다. 연구는 수정하지 않으며 전체 탐색/AI 개편은 별도 단계입니다. GitHub 연결 도구를 통한 게시이며 로컬 Git push가 아닙니다. 게시 직전 HEAD/대상 SHA와 원격 커밋 부모/파일 해시/나머지 파일 보존을 확인하고 최종 커밋은 응답에 보고합니다.

사용자는 일반 CARITDA(검증 전용 앱과 구분)에서 본인 이름으로 운전 시작→문제 남기기→저장 안내/상세→전체 기록→내 기록 수정→재실행을 확인할 수 있습니다. 삭제는 본인이 만든 임시 기록으로 대상/취소/논리 삭제를 확인해 주세요. 이미 교체 설치했고 파일 직접 수정·데이터 초기화는 필요 없습니다. 기존 테스트 정리는 개인 목록의 대상 ID를 사용자 확인한 뒤 새 백업과 별도 승인된 처리로 진행하며 이번에는 삭제하지 않았습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-08 00:17:00 +09:00 (Asia/Seoul).

## 최신 작업 — Simple UI·한 화면 첫 안내·사용법 실제 구현

### 구현한 화면과 데이터 보존

- Aside 온보딩 보고서 e5473532c639cd69d8e0a8017697ad6cbe66783d와 기존 UI 보고서/AC/최신 소스·모델을 읽고 실제 Kotlin 화면에 반영했습니다. 연구 파일은 수정하지 않았습니다.
- 홈을 기록 이름/변경·확인 가능한 정보 범위·읽지 않은 전달 기록/관리 중/완료 건수·기록 보기·문제 남기기·차량 정보로 단순화했습니다. 긴 문제 목록·상담·점검·SDK 정보는 각각의 화면에 유지했습니다. 홈에서 기록 보기는 이전 필터를 초기화해 관리 중 기록을 우선, 모두 완료이면 완료 목록을 엽니다.
- 차량 기본 화면은 충전량·주행거리·단위·실제 수신 시각·오래됨·에뮬레이터 출처·차량값 불러오기에 집중했습니다. 값32sp/항목24sp/시간22sp/최소76dp 버튼, 자릿수 구분을 적용했습니다. 원본 값은 그대로 저장합니다. 연료 미지원은 제공되지 않는 항목에서 설명하며 0%로 바꾸지 않습니다.
- SDK/Capability/원본 상태/세션/오류/내부 출처/밀리초 시각은 불러오기 상세로 이동했습니다. 마지막 성공값과 최신 실패를 분리하고, 100회 이상 실패 뒤에도 이전 성공값을 찾는 읽기 전용 조회를 추가했습니다. 미수신·유효0·미지원·권한 없음·실패를 구분합니다.
- 신규 안내는 사용자가 지정한 한 화면과 운전자 선택/안내 건너뛰기입니다. 기존 설치/기록이 있으면 자동 제안을 생략합니다. 안내 완료/skip은 preference만 변경하고 운전자 확정/SDK 조회/읽음/안전 변경을 하지 않습니다. 사용법의 여섯 과제와 처음 안내 재보기·원래 화면/초안 복귀를 구현했습니다. 테마 선택은 사용법에 옮겼습니다.
- DB v2/운전자 영구 ID/A·B/작성 당시 이름/인계/조치/재발/초안을 유지했습니다. 관측 DB 버전과 원본 저장 구조, PleosReadClient·SDK2.3.2·CarSafetyGate는 변경하지 않았습니다. 자동 감지/상시 수집/AI/외부 연동은 추가하지 않았습니다.
- 화면이 보이는 동안 오래됨을 재평가하지만 SDK를 반복 호출하지 않습니다. 10분은 표시용 기준이며 고장 판정이 아닙니다. UNKNOWN/RESTRICTED 입력 차단과 안내 중단 상태를 유지하며, 실패한 초안 저장을 성공했다고 안내하지 않습니다.

### 최종 빌드와 실제 검사

1. 기존 앱을 force-stop하고 문제/관측 DB를 .tools/simple-ui-backup 및 앱 전용 비교 사본에 보존했습니다. 앱/DB/AVD 초기화나 삭제는 하지 않았습니다. 개인 백업은 게시하지 않습니다.
2. 기존 Windows/JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0 캐시의 offline assembleDebug+assembleDebugAndroidTest 성공: BUILD SUCCESSFUL in 1m 1s, exit0. 앱/계측 APK 모두 기존 Pleos Connect v2.1.0 emulator-5554에 install -r Success.
3. 최종 CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6, CARITDA_UI_PASS checks=27. PRODUCTION_MIGRATION_PASS로 원래 문제5건/이벤트16건과 원래 관측 보존도 확인했습니다. 회귀가 추가한 검증용 프로필/기록은 삭제하지 않았습니다.
4. 신규 CARITDA_ON_PASS checks=32: 안내 정책/UNKNOWN 차단/skip 무부작용/재실행/재보기/초안 복귀/홈 필터/기술 정보 분리/실제 SDK/기존 전체 행과 원래 관측 보존. 별도 fixture에서 유효0/미수신/성공 뒤105회 실패/15% 무진단/10분 경계/미래 시각/TEST_ONLY 배제를 검사했습니다. 가상값을 운영 관측 DB에 넣지 않았습니다.
5. 다섯 주요 화면 × 다섯 콘텐츠 폭320~1024dp × 글꼴100/130/150/200% × 두 테마 = 200개 렌더링 조합의 넘침/말줄임/최소76dp 버튼 검사 통과. 앱 리소스와 테마는 원복했고 OS 전역 설정은 바꾸지 않았습니다. 실제 Pleos의 홈/차량/사용법/재보기 화면 캡처도 검토했습니다.
6. 현재 실제 에뮬레이터 SDK 응답: SOC15%, 주행거리 원본1000000.0km(화면1,000,000km), 각 READ·RECEIVED. 연료 ERROR_NOT_SUPPORTED_PROPERTY·UNSUPPORTED·숫자 없음. PLEOS_EMULATOR_SDK 출처이며 실차/앱내 가상값과 구분합니다. 이전100%/0km 기록은 과거 수신 사실로 보존합니다. 기기 수신 시각과 PC의 보고 시각은 구분합니다.
7. 최종 앱 APK SHA256: 9cafeb74e304a06067c0830792c0943fa48b2196c93ba34926902adf949efef3. 로그/화면은 app/build/validation에 보존하며 APK/DB/키/캐시와 함께 게시하지 않습니다.

### 실패·부분·미검증

- 초기에 로컬 소스 구문 오류로 컴파일이 한 번 실패했고 수정 후 최종 빌드/전체 검사가 통과했습니다. 깨진 중간 코드는 게시하지 않습니다.
- ON01~ON24: 통과13/부분8/미검증3. 상세 범위는 docs/ONBOARDING_ACCEPTANCE.md에 별도 기록했습니다. 32개 assertion 통과가 24개 기준 전체 통과라는 뜻은 아닙니다. 기존 AC 판정도 새 ON 결과로 덮어쓰지 않았습니다.
- 신규 설치 UI는 데이터 삭제 없이 pending preference를 임시 설정하고 원복해 검사했습니다. 실제 빈 신규 설치, 실제 주행 RESTRICTED/OS 차단 전환, 저장소 장애 주입, 전체 안내/오류/상세의 모든 창 조합, 물리 키보드/TalkBack/rotary·초보/고령 사용자 독립 과제·실차·Release는 미검증입니다.
- 이전 offline Lint는 lint-gradle31.10.1 캐시 부재로 실패했습니다. 이번에는 같은 다운로드를 반복하지 않았고 Lint 통과로 보고하지 않습니다. deprecated API 경고가 남아 있습니다.

### 변경 파일·공유·다음 작업

사용자 PC에 실제 반영한 12개 파일:

- AGENTS.md, README.md, NextDrive-Pleos/README.md
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/domain/ConsultationSummary.kt
- NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/vehicle/ObservationStore.kt
- 신규 NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ui/GuideState.kt
- 신규 NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/ui/VehiclePresentation.kt
- NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt
- 신규 NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/OnboardingChecks.kt
- 신규 docs/ONBOARDING_ACCEPTANCE.md, docs/CODEX_PROGRESS.md

기준 main e5473532c639cd69d8e0a8017697ad6cbe66783d. GitHub 연결 도구를 통한 게시를 사용합니다. 게시 직전 HEAD/대상 SHA를 재확인하고 최신 트리/부모와 기대 HEAD를 사용해 명시적 검증 파일만 반영합니다. Aside 및 나머지 파일 SHA와 게시 커밋 부모를 대조하며 최종 SHA는 응답으로 보고합니다. 로컬 Git push가 아닙니다.

이미 교체 설치한 앱에서 이름 선택→운전 시작→차량 정보→차량값 불러오기→사용법/처음 안내 재보기를 확인할 수 있습니다. Android Studio 파일 수동 편집·재설치·데이터 삭제는 필요 없습니다. 다음은 ON 부분/미검증의 실제 창·접근성·사용자 이해 과제를 우선 검증하고 캐시 확보 가능 시 Lint를 완료하는 단계입니다. 자동 감지·AI·외부 연동은 별도 지시 후 진행합니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 22:15:17 +09:00 (Asia/Seoul).

## 최신 작업 — Aside UI·운전자 M0 구현·운영 DB 이전·실제 실행 성공

### 구현 결과

- 이름/별명 등록, 영구 ID, 후보 카드 선택→명시적 운전 시작, 선택 취소, 이름 수정·보관. 최근 운전자와 이번 이용의 확정을 구분하며 콜드 스타트는 재확인합니다. 차량 시동/OS 계정과 무관합니다.
- 홈/문제/차량 분리. 홈은 현재 기록 운전자·문제 기록 CTA·미해결/미확인 건수·최근 차량 상태 중심입니다. 긴 목록·조치·재발·상담은 별도 화면입니다. 운전자 수정·보관은 관리 화면에 분리했고 운전 시작은 고정 하단에 둡니다.
- 사용자 직접 기록 출처, 명시적 읽음 확인, 원 작성자 snapshot, 상담 범위/시간대/외부 미전송 표시를 유지했습니다. 자동 감지·Gleo·모바일/정비소 API는 구현하지 않았습니다.
- SQLite v1→v2 additive migration: 기존 열/행/ID/시각/상태/revision/순서를 그대로 유지하고 drivers/작성자 ID·당시 이름/drafts/idempotency 인덱스를 추가했습니다. 기존 A/B ID는 유지하며 신규 설치에는 가짜 A/B를 만들지 않습니다. 관측 DB/SDK 구현은 변경하지 않았습니다.
- 새 문제·조치·재발 초안의 원 작성자/대상/revision/내용을 SQLite에 보존합니다. 작성 중 다른 운전자 전환을 막고 저장·명시적 취소·계속 작성으로 구분합니다. 저장과 해당 초안 제거는 트랜잭션 처리하며 draft ID로 반복 저장을 막습니다. 제한 중에는 비확정 초안 보존만 허용합니다.
- 시스템/밝은/어두운 테마, 주요 버튼 76dp 이상·24sp·내용에 따라 높이 확장, 선택 체크·focus 테두리·지속 오류 문구, 좁은 창/큰 글꼴의 세로 메뉴, 목록 위치 보존과 IME adjustResize를 적용했습니다. 실제값 freshness는 표시용 10분 기준이며 고장 기준이 아닙니다.

### 단계별 검증과 데이터 보존

1. 기존 앱 force-stop 후 문제/관측 DB+journal와 이전 APK를 .tools/ui-migration-backup에 바이너리 보관하고 앱 전용 비교 사본을 만들었습니다. 개인 데이터이므로 게시하지 않습니다. 복구 절차는 docs/UI_MIGRATION_PLAN.md에 먼저 작성했습니다.
2. 모델/마이그레이션 단계 빌드 성공 후 운영 Activity를 열기 전에 독립 v1 fixture 18개 검사를 통과했습니다. 중간 예외로 스키마·행·user_version rollback, 모든 원래 필드/관계 비교, 반복 실행, 재연결, snapshot, 보관, 초안 중복 확정 방지를 검사했습니다.
3. 실제 운영 DB 이전 후 기존 문제 5건·이벤트 16건의 모든 원본 필드와 관계 보존, 원래 관측 행 보존을 비교 사본으로 확인했습니다. 이후 테스트가 추가한 행은 별도이며 기존 행을 지우지 않았습니다.
4. 최종 앱/계측 APK assembleDebug+assembleDebugAndroidTest: BUILD SUCCESSFUL in 55s, exit 0. 기존 Windows/JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0, 기존 캐시 offline 빌드. 앱·테스트 APK install -r 둘 다 Success. 실제 Pleos Connect v2.1.0/API34 emulator-5554에서 실행했습니다.
5. 최종 MIGRATION_PASS checks=18, CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6, CARITDA_UI_PASS checks=27. 합계 75개 검사이며 Aside AC28 전부 통과라는 뜻은 아닙니다.
6. 새 UI 검사는 이름 오류/등록/후보 취소/시작, dirty 전환 차단, Activity 재생성, 원 작성자 초안 복원, 저장 중복 방지, 조치·재발·명시적 취소·상태, 이름 변경과 보관 후 모든 기존 기록 보존을 포함합니다.
7. SDK 2.3.2 실제 응답 유지: EV SOC=100%, 누적 주행거리=0.0km, 각 Capability READ. 연료는 ERROR_NOT_SUPPORTED_PROPERTY로 값 없이 UNSUPPORTED 저장. PLEOS_EMULATOR_SDK이며 앱내 가상값/실차와 구분합니다. 최종 통합 세션 48861d71-6288-4b38-afc3-9dd1c0411794.
8. 320/480/600/840/1024dp 콘텐츠 폭에서 홈 CTA 폭·76dp 터치 높이, 테스트 프로세스 fontScale 100/130/150/200%에서 CTA 확장/말줄임 없음 검사 통과. 앱 리소스와 테마 설정은 테스트 후 원복했으며 OS 전역 글꼴/보안 정책은 변경하지 않았습니다. 두 테마 CTA 실제 색상의 대비 4.5:1 이상 확인. 실제 화면 캡처를 검토했습니다.
9. 추가 실제 키보드로 CARITDA_draft_restart_test 임시 초안 입력→force-stop/cold start→원 작성자 B로 운전 시작→계속 작성에서 내용 복원 확인. 사용자 확인 동작으로 해당 임시 초안만 취소했고 확정 문제/이력은 유지했습니다.

### 실패·부분 검증·남은 제한

- 첫 확장 검사는 별도 상태 dialog를 Activity decor에서 찾는 테스트 오류로 중단됐습니다. 접근성 노드로 선택하도록 수정 후 전체 통과했습니다. 그 과정의 검증용 프로필/카드도 삭제하지 않았습니다. 정상 완료한 검증 프로필은 보관 처리했습니다.
- offline lintDebug는 exit 1: com.android.tools.lint:lint-gradle:31.10.1 캐시 부재로 실행되지 못했습니다. 동일 네트워크 다운로드를 반복하지 않았으며 Lint 성공으로 기록하지 않습니다.
- 실차/Release/실제 주행 UXR 전환/서비스 단절·저장소 고갈 주입, TalkBack·물리 키보드·rotary 종합 검사, 모든 화면의 전체 글꼴·창 조합, 고령/신규 사용자 과제는 미검증입니다. deprecated onBackPressed 경고가 남습니다.
- AC01~AC28별 통과/부분/미검증과 범위를 docs/UI_ACCEPTANCE.md에 명시했습니다. UI 폭/폰트 검사는 콘텐츠를 제한한 계측이며 모든 실차 패널 검사와 다릅니다. 선택/확정·cache/비파괴 이전·native View 설계 조정도 기록했습니다.

### 변경 파일 / 공유

- AGENTS.md: 최신 UI M0 범위와 데이터 보존 지침.
- app/src/main/java/com/nextdrive/pleos/model/IssueModels.kt: 영구 운전자와 snapshot/초안 모델.
- data/IssueRepository.kt, data/SqliteIssueRepository.kt: 비파괴 v2·운전자/초안 저장.
- domain/CaritdaService.kt: 명시적 확정, ID 기반 인계, 원 작성자 draft 규칙·중복 방지.
- MainActivity.kt, app/src/main/AndroidManifest.xml: 실제 UI/탐색/테마/초안/IME.
- app/src/androidTest/.../MvpScenarioInstrumentation.kt, 신규 MigrationChecks.kt: 기존 회귀+UI/SDK/이전 검사.
- 신규 docs/UI_MIGRATION_PLAN.md, docs/UI_ACCEPTANCE.md, docs/CODEX_PROGRESS.md, README.md, NextDrive-Pleos/README.md.
총 14개 소스·설정·문서를 사용자 PC에 반영하고 GitHub 연결 도구로 게시합니다. 기존 SDK/관측 코드와 Aside 연구는 변경하지 않았습니다. APK SHA256 03529f7cabfb4e2db6a50d708fc345f0aeef999d5636f42c64acb82bbd6db475. 앱/테스트 APK·로그·화면·개인 DB·백업·키·캐시는 게시하지 않습니다.

작업 기준 main 6f6c281ed40c702378643ee2ba93356ad4f79413. 게시 직전 최신 HEAD/대상 SHA를 재확인하고 최신 트리/부모를 잇는 커밋과 기대 HEAD 갱신을 사용합니다. 원격 파일 SHA/부모 및 Aside 포함 나머지 파일 보존을 확인하며 최종 커밋은 응답으로 보고합니다. 로컬 Git push가 아닙니다.

사용자는 설치된 앱에서 이름 등록→카드 선택→운전 시작을 사용할 수 있습니다. 파일 편집/재설치/DB 초기화는 필요 없습니다. 다음 우선 작업은 부분 검증된 실제 창·접근성·입력 제한 전환과 Lint 완료입니다. 사용자 기능 확장은 별도 지시 후 진행합니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 21:18:19 +09:00 (Asia/Seoul).

## 최신 작업 — 실제 Pleos SDK 수신·저장·회귀 검증 성공

### 직접 수행한 결과

- 사용자가 부팅한 기존 Pleos Connect v2.1.0에 ADB emulator-5554 연결, boot_completed=1 확인.
- 이전 최종 빌드 앱/계측 APK를 install -r로 교체 설치: 둘 다 Success. 기존 앱/SQLite/AVD를 삭제하거나 초기화하지 않았습니다. 동일 APK SHA256 7a6412cc0cfc4b5aec24aa903540b485b345c2f90d8e1282dfdbaccf55056dd1이며 이전 빌드 성공(exit0,47초) 후 소스를 바꾸지 않아 재빌드는 생략했습니다.
- SDK initialize 후 실제 Capability/get 콜백으로 서비스 연결 응답을 확인했습니다. CAR_ENERGY/CAR_DRIVE/VEHICLE_BINDING granted=true.
- EV_SOC: Capability READ, 원본 100 %, receivedAt 1794053740683. ODOMETER: READ, 원본 0.0 km, receivedAt 1794053740751. 둘 다 supported=true, valid=true, RECEIVED로 저장했습니다.
- FUEL_PERCENT: Capability 콜백 ERROR_NOT_SUPPORTED_PROPERTY. 읽기를 호출하지 않고 UNSUPPORTED/supported=false/valid=false, 원본·단위·수신시각 null로 저장했습니다. 권한 거부나 연료0%로 해석하지 않습니다.
- 세 항목 모두 source=PLEOS_EMULATOR_SDK, 세션 06b5899d-baad-473e-9ed0-ce71ed35093f. 에뮬레이터 Vehicle Service 시뮬레이션 응답이지 실차/앱내 가상값이 아닙니다.
- CARITDA_MVP_PASS checks=24, CARITDA_OBSERVATION_PASS checks=6 전체 통과. 실제 UI 흐름과 SQLite, 기존 문제 ID 보존, SDK 응답 저장/Activity 재시작 복원 검사입니다. 시험용 문제 한 개가 추가됐고 기존 문제는 유지했습니다.
- 추가 force-stop → cold start Status ok. 새 SDK 읽기 없이 기존 세션·SOC·주행거리·연료 미지원이 실제 화면에서 복원됨을 UI XML/스크린샷으로 확인했습니다. 처음 XML에는 스크롤 밖 연료 카드가 없어 검사 실패했으나 스크롤 후 같은 세션/연료 상태를 확인했습니다. 앱/저장 오류가 아니었습니다.

### 수정·게시 파일

앞서 로컬에만 있던 실행 검증 성공 소스 4개: MainActivity.kt, 신규 vehicle/PleosReadClient.kt, AndroidManifest.xml, MvpScenarioInstrumentation.kt. 이번에 소스를 다시 변경한 것은 아니며 이전 구현을 실제로 검증한 뒤 게시합니다.
문서 갱신: docs/VEHICLE_SDK_232_RUNTIME.md, SDK_CAPABILITY_MATRIX.md, docs/CODEX_PROGRESS.md, README.md, NextDrive-Pleos/README.md. 총 게시 대상 9개. 기존 ObservationStore와 수동 DB 스키마는 변경하지 않았습니다.


추가 검수에서 입력 폼의 처음 초안에 취소 버튼이 없던 점과 취소 후 빈 폼이 남던 점을 수정했습니다. 새 문제·조치 폼에 항상 취소를 제공하고 확인 후 홈으로 돌아갑니다. 이 경로를 추가 회귀로 검증했습니다. 중단된 검사의 초안만 정차 UI의 명시적 취소로 정리했으며 기존 확정 기록은 유지했습니다.
### 한계·다음 작업

실제 권한 거부/타임아웃/서비스 끊김 실패 조건은 이번에 강제로 만들지 않았습니다. 해당 방어 로직의 모든 런타임 경로를 검증했다고 주장하지 않습니다. 연료는 현재 이미지에서 미지원입니다. 실차·Release·Lint·실제 주행 전환은 이번 미검증. 자동 감지/반복 수집/운전자 관리 개편/UI 전면 개편은 구현하지 않았습니다.

사용자 추가 파일 편집/재설치/SDK 설치는 필요 없습니다. 현 앱에서 차량 데이터 연결 확인 → 차량 데이터 읽고 저장하기로 다시 확인할 수 있습니다. 다음 기능 후보는 수동 문제와 최근 관측의 ID/시각/출처 연결입니다.

### 공유와 보존

작업 시작 main 50d27034cba44f0dc8a7027d4c38d00605458d68을 읽고 검증했습니다. 최신 HEAD/대상 SHA를 다시 확인한 뒤 최신 트리를 부모로 GitHub 연결 도구를 통해 검증된 파일만 게시합니다. 커밋 부모/파일 SHA/나머지 파일과 Aside 연구 보존을 원격에서 확인합니다. 최종 커밋은 응답으로 보고하며 자기 SHA를 넣기 위한 반복 커밋을 하지 않습니다. 로컬 Git push가 아닙니다.

로컬 증거 app/build/validation/sdk232-runtime.txt, sdk232-home/restored/fuel.xml, sdk232-restored.png와 앱/계측 APK는 게시하지 않습니다. 사용자 DB·키·SDK 바이너리·캐시는 공유하지 않습니다. 이전 실패 기록을 삭제하지 않았습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 21:05:11 +09:00 (Asia/Seoul).

## 최신 작업 — Vehicle 2.3.2 확보 확인, 실제 읽기 구현과 빌드 성공 / 런타임 대기

### 완료한 작업

- 사용자 보고(2.3.1 HTTP404, 2.3.2 Sync/다운로드/Build APK 성공)를 현재 상태로 반영했습니다. 기존 실패 기록은 보존합니다. 실제 로컬 Gradle이 2.3.2이고 Vehicle-2.3.2.aar, vehicle-aidl-1.1.1.aar, vehicle-data-1.1.11.aar와 공식 sources JAR이 존재함을 확인했습니다.
- 공식 공개 SDK 소스/배포 AAR에서 SOC/주행거리/연료 Capability·읽기 함수, nullable 타입, %/km 단위와 RequiresPermission을 확인했습니다. 제어 API를 호출하거나 특권 권한을 요청하지 않았습니다.
- 로컬 PleosReadClient에 Vehicle 초기화/해제, 세 Capability 확인, READ/READ_WRITE만 조회, 수치 검증, 출처/원본/단위/시각/세션 저장, 실패·타임아웃 분리, 생명주기 정리·늦은 콜백 무시를 구현했습니다. initialize 반환만으로 연결 성공을 표시하지 않습니다.
- MainActivity의 기존 최소 차량 화면에 읽기 버튼과 상태를 연결하고 Manifest에는 CAR_ENERGY/CAR_DRIVE만 추가했습니다. SDK가 VEHICLE_BINDING을 병합합니다. 기존 수동 문제 DB와 ObservationStore 스키마는 그대로 유지했습니다.
- 기존 24개 계측 시나리오를 유지하고, SDK 실제 결과/최소 한 개 값/재시작 저장 복원을 확인하는 계측으로 갱신했습니다. 이는 준비한 테스트이며 이번에 실행하지 못했습니다.

### 실제 빌드 결과와 해결한 오류

- 첫 컴파일은 배포 AAR의 VehicleException 난독화와 runtime-only ErrorCode 의존성으로 실패했습니다. 예외 타입을 반사 호출하지 않고 Exception/공개 ErrorCode 이름으로 처리했습니다. vehicle-data를 implementation으로 추가한 시도는 테스트 전이 의존성의 offline 해석이 실패했습니다. 최종 설정은 runtime 그래프를 유지하며 compileOnly, transitive=false로 공개 ErrorCode만 노출합니다.
- 최종 :app:assembleDebug :app:assembleDebugAndroidTest → BUILD SUCCESSFUL in 47s, exit 0. JBR21.0.8/Gradle8.11.1/Platform34/Build-Tools35.0.0, 기존 캐시 offline 빌드입니다. 새 APK와 계측 APK가 실제 생성됐습니다.
- aapt permissions: CAR_ENERGY, CAR_DRIVE, SDK의 VEHICLE_BINDING. 제어 권한 없음.
- 앱 APK SHA256: 7a6412cc0cfc4b5aec24aa903540b485b345c2f90d8e1282dfdbaccf55056dd1. 로그 app/build/validation/sdk232-build.txt. APK/로그/SDK sources/키/DB는 게시하지 않습니다.

### 런타임 실패·미검증

- 시작 시 ADB 연결 기기가 없었습니다. 기존 Pleos_Connect_v2.1.0을 초기화 없이 시작했으나 .android/emu-last-feature-flags.protobuf.lock 쓰기 error 5가 반복되어 이번에 시작한 emulator/qemu 프로세스만 중단했습니다. 보안 제한 우회나 SDK/AVD 재설치를 하지 않았습니다. AVD 시작 로그는 .tools에 보존합니다.
- 일반 Android Studio의 Device Manager에서 기존 AVD를 부팅해 달라는 사용자 조작 요청을 전달했습니다. 실제 앱 설치/실행, SDK initialize/서비스 응답, Capability, 실제값 수신/저장, 런타임 오류 분류와 이번 회귀 검사는 미수행입니다. 실제 수신값은 아직 0개입니다.
- 과거 24+5 검사 성공은 과거 기록이며 이번 수정본의 검사 성공으로 표현하지 않습니다. Lint/Release/실차/실제 주행 전환도 이번 미검증입니다.

### 변경 파일 및 게시 범위

사용자 PC에 직접 반영: AGENTS.md, app/build.gradle.kts(사용자가 적용한 2.3.2 유지 + ErrorCode 컴파일 노출), app/src/main/AndroidManifest.xml, MainActivity.kt, 신규 vehicle/PleosReadClient.kt, app/src/androidTest/.../MvpScenarioInstrumentation.kt, SDK_CAPABILITY_MATRIX.md, docs/VEHICLE_SDK_232_RUNTIME.md, docs/CODEX_PROGRESS.md.

빌드 검증한 app/build.gradle.kts와 AGENTS.md/결과 문서 3개만 우선 GitHub 연결 도구를 통해 게시합니다(총 5개). 런타임 미검증 Manifest/MainActivity/PleosReadClient/계측은 로컬에 보존하고 실제 AVD 검사 전 게시에서 제외합니다. 원격 앱에는 아직 새 읽기 UI가 반영되지 않습니다. 사용자 PC에서는 새 SDK 포함 APK를 실행할 준비가 되었습니다.

작업 시작 main c9d5469cef2913ca1ae497d0da8efa69b651f380과 Aside 조사 파일을 확인했습니다. 최신 HEAD/대상 SHA를 다시 대조하고 기존 main 트리/부모를 잇는 커밋, 기대 HEAD 갱신, 원격 해시·부모·Aside 포함 나머지 파일 보존을 확인합니다. 게시 커밋은 최종 보고하며 로컬 Git push 성공으로 표현하지 않습니다.

다음 최우선은 기존 AVD 부팅 → ADB 교체 설치 → 기존 24개 회귀와 실제 SDK 계측 실행 → 결과에 따른 연결/Capability 문제 해결입니다. 사용자가 편집할 파일은 없습니다. UI 개편·운전자 관리 확장·자동 이상 감지는 진행하지 않았습니다. 자세한 실행 순서는 docs/VEHICLE_SDK_232_RUNTIME.md에 있습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 20:31:59 +09:00 (Asia/Seoul).

## 최신 작업 — 실제 앱 Vehicle 의존성 추가 후 다운로드 실패 재확인

- 공식 시작 가이드·샘플 Gradle의 Vehicle 2.3.1과 settings.gradle.kts의 공식 Maven URL을 확인했습니다. settings 파일은 변경하지 않았습니다.
- 사용자 PC 로컬 `NextDrive-Pleos/app/build.gradle.kts`에 `implementation("ai.pleos.playground:Vehicle:2.3.1")`을 추가했습니다. 이전과 달리 init probe가 아니라 실제 앱 의존성으로 다운로드/assembleDebug를 실행했습니다.
- 결과: Gradle exit 1, BUILD FAILED in 41s. :app:checkDebugAarMetadata → debugRuntimeClasspath → Vehicle 2.3.1 POM의 공식 Nexus GET이 `Permission denied: getsockopt`로 실패. Google/Maven Central도 동일 오류였습니다. DNS는 정상 해석되었으며 HTTP 401/403/404 응답은 받지 못했습니다. 인증·버전 문제로 확정할 근거가 없습니다.
- 사용자/프로젝트 내부 Gradle 캐시에서 SDK AAR 없음. SDK 포함 Sync 동등 의존성 해결·빌드 실패. SDK 초기화/서비스 연결/Capability/실수신/수신값 저장은 미수행입니다. 기존 APK와 앱/SQLite 데이터는 보존했습니다. 이번 회귀 검사는 빌드 선행 실패로 실행하지 않았습니다. 과거 24+5 성공은 과거 기록으로 유지합니다.
- 현재 실행 정책은 network restricted 및 sandbox_approval 요청 불가입니다. 범위를 좁힌 네트워크 승인 창도 현재 도구로 요청할 수 없습니다. 사용자 승인을 받은 뒤 권한 확대 실행했다고 주장하지 않습니다. 보안 설정 변경·Sandbox 해제·우회·동일 명령 반복은 하지 않았습니다.
- 로컬 파일 편집은 완료했으므로 사용자는 일반 Android Studio에서 File > Sync Project with Gradle Files만 실행하면 다운로드를 확인할 수 있습니다. 성공 시 External Libraries/Build APK(s)를 확인합니다. 기존 앱 삭제·SDK/AVD 재설치는 불필요합니다.
- 현재 로컬 빌드는 미다운로드 의존성으로 막혀 있습니다. 미검증 app/build.gradle.kts는 로컬에 보존하고 GitHub 게시 대상에서는 제외합니다. 원격 앱 소스/빌드 설정은 기존 검증본을 유지합니다. 이 차이를 명확히 보고합니다.

### 변경 파일 및 다음 단계

로컬만: `NextDrive-Pleos/app/build.gradle.kts`.
문서 게시 대상: 신규 `docs/SDK_DOWNLOAD_RETRY.md`, 갱신 `SDK_CAPABILITY_MATRIX.md`, `docs/CODEX_PROGRESS.md`.
임시 로그: `NextDrive-Pleos/app/build/validation/sdk-retry-build.txt` (게시 제외). 기존 앱 소스/UI, 연구 파일, 사용자 DB/키, 전역 설정은 수정하지 않았습니다.

작업 시작 최신 main은 Aside의 `a5c738aed91b43d98d4234a3d9718c0affe9c7d8`이며 부모가 기존 Codex `1b55aeb685fa252f197d4760553813bf5457389c`임을 확인했습니다. CARITDA_UI_UX_RESEARCH.md를 읽고 이름/별명 등록, 후보 선택→운전 시작 확정, stable ID와 당시 이름·초안·이력 보존, 홈/문제 분리, UXR 우선 원칙을 향후 별도 UI 단계의 기준으로 확인했습니다. 이번에는 구현하지 않았습니다.

다음 최우선 작업은 네트워크가 허용되는 일반 Android Studio에서 아티팩트 확보/빌드 확인 → 실제 AAR API/권한 확인 → typed read adapter → Capability/실수신/저장/회귀입니다. 사용자 조작·오류 분류는 docs/SDK_DOWNLOAD_RETRY.md에 정리했습니다.

GitHub 연결 도구를 통한 문서 게시만 수행합니다. 최신 main 재확인, 대상 SHA 대조, 최신 트리 기반 커밋/기대 HEAD 갱신, 나머지 SHA와 Aside 연구 파일 보존을 확인합니다. 커밋 ID는 최종 응답으로 보고하며 로컬 Git push 성공으로 표현하지 않습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 17:13:57 +09:00 (Asia/Seoul).

## 최신 작업 — CARITDA v0.2 SDK 수신 시도 및 기반 구현 (핵심 목표 미완료)

### 완료 작업 / 변경 파일

- 로컬 사용자 PC NextDrive-Pleos에 구현: MainActivity의 차량 연결 확인 화면, vehicle/ObservationStore.kt의 별도 append-only SQLite, MvpScenarioInstrumentation.kt의 추가 5개 관측 검사. 기존 수동 문제 DB·테이블·데이터를 변경/삭제하지 않았습니다.
- AGENTS.md에 현재 v0.2 범위와 Aside 병렬 커밋 보존 규칙을 추가했습니다.
- SDK_CAPABILITY_MATRIX.md, docs/CARITDA_V02_VALIDATION.md, docs/CODEX_PROGRESS.md에 현재 사실과 다운로드 절차를 기록했습니다. 총 게시 대상 7파일. Aside 연구·Google AI Studio 앱·기존 기록은 수정하지 않았습니다.
- 관측 필드는 종류/원본/단위/수신시각/기록시각/출처/지원/유효성/세션/nullable 미래 운전자 ID입니다. 수신 실패 기록에는 값과 수신 시각이 null입니다. 운전자 A/B 재설계·자동 이상 감지 엔진은 구현하지 않았습니다.

### 실제 검사 결과

- 최종 assembleDebug + assembleDebugAndroidTest: BUILD SUCCESSFUL, 45초, exit 0. Windows JBR21.0.8/Gradle8.11.1/API34/Build-Tools35.0.0, 기존 캐시 offline 사용. SDK 없이 동작하는 현재 기반 앱 빌드 성공입니다.
- Pleos Connect v2.1.0 emulator-5554에 앱·테스트 APK 교체 설치 성공. 실제 UI 실행.
- CARITDA_MVP_PASS checks=24: 기존 저장·인계·조치·재발·상담·입력 제한·화면 폭 검사 모두 재통과했습니다.
- CARITDA_OBSERVATION_PASS checks=5: 실패 상태에 가상값 없음, 별도 TEST_ONLY DB의 필드 저장/재연결 복원, 잘못된 수신값 거부, 실제 화면에서 SDK_UNAVAILABLE 진단 저장, Activity 재시작 후 복원 통과. 테스트 숫자 42%는 독립 DB 저장 검사이며 실제 센서/SDK 응답이 아닙니다.
- 초기 확장 계측 실행은 종료 응답 없이 대기하여 force-stop으로 중단했습니다(중단 결과 Process crashed). 기존 시나리오 끝에서 Activity 종료를 명시하도록 테스트를 수정하고 재빌드·재설치한 최종 실행은 24+5 모두 통과했습니다. 기존 데이터를 삭제하지 않았습니다.
- 최종 앱 APK SHA256: 29812d449d92fb827c81b3ba157c102b31880d141f15be510fca81eb30618b46. 앱/테스트 APK와 v02-build.txt, v02-instrumentation.txt, v02-sdk-download.txt는 로컬 build 아래에만 있습니다.
- 현재 AVD의 ai.pleos.playground.service.vehicle versionName 2.3.22와 공개 권한 CAR_ENERGY/CAR_DRIVE/CAR_DOORS의 prot=normal 정의를 dumpsys로 확인했습니다. 설치된 서비스 존재는 앱 연결/권한 허용/Capability 성공과 다릅니다.

### 실패 / 미구현

- 공식 Maven 두 버전 POM 요청 연결 실패. Gradle 2.3.1 실제 resolve도 Nexus/Google/Maven Central에서 Permission denied: getsockopt로 실패(exit 1). 초기 probe 경로와 PowerShell 인자 문제는 수정했으며 최종 실패 원인은 네트워크입니다. 제한을 우회하거나 같은 다운로드를 반복하지 않았습니다.
- 샘플 README는 2.3.2, 실제 Gradle 버전 파일은 2.3.1입니다. 시작 가이드와 Gradle 기준 2.3.1을 다운로드 후보로 택했으나 바이너리 확보·이미지 호환성 검증은 실패/보류입니다.
- SDK 설치·SDK 의존성 포함 Sync/컴파일·초기화·서비스 연결·Capability·실제 읽기 모두 미완료. SOC/주행거리/연료/도어 원본 수신 0개. 실제 센서값 저장·복원도 미수행입니다. SDK 없이 현재 화면은 미설치/미확인을 안내합니다.
- Lint는 기존 미캐시 의존성/네트워크 제한 상태로 이번에 재실행하지 않았습니다. Release/실차/실제 주행 전환 미검증. AI·Gleo·자동 감지·운전자 관리 개편·외부 공유는 미구현입니다.

### 다음 작업 / 게시

일반 Android Studio에서 v0.2 검증 문서의 임시 Vehicle 2.3.1 dependency를 추가하고 Sync하여 아티팩트 확보 여부를 먼저 확인해야 합니다. 현재 기본 빌드에는 미확보 SDK를 영구 추가하지 않았습니다. 다운로드만으로 현재 앱이 센서를 읽지는 않습니다. 확보 후 AAR 기준 typed 읽기 어댑터를 구현하고 Capability·권한·실수신·저장 순으로 검증하는 것이 다음 최우선 작업입니다.

GitHub 연결 도구를 통한 게시만 사용합니다. 작업 시작 main a07d3234cbf6d4f2e7c4e93553e391ff13c652e7. 게시 직전 최신 HEAD와 대상 SHA를 재확인하고 Aside의 다른 파일 변경은 최신 트리에 보존합니다. 게시 커밋은 최종 응답으로 보고하며 자기 SHA 기록을 위한 반복 커밋은 하지 않습니다. APK/DB/인증/디버그 키/캐시/init probe/로그는 게시하지 않습니다. 로컬 Git push를 수행하지 않았습니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 16:14:00 +09:00 (Asia/Seoul).

## 최신 작업 — CARITDA v0.1 구현 및 Pleos 실제 실행

사용자의 신규 MVP 지시로 서비스 개발 보류를 해제했습니다. CARITDA는 상표 검증 전 가칭입니다. 폴더와 패키지를 유지하고 기존 NextDrive-Pleos에 실제 서비스 화면을 구현했습니다. 변경은 사용자 PC의 프로젝트 폴더에 직접 반영되었습니다. 기존 Google AI Studio 앱, Aside 연구 파일, 과거 개발 기록과 기존 앱 데이터를 삭제하거나 초기화하지 않았습니다.

### 완료한 기능

- 운전자 A/B와 선택 상태 저장, 미해결 문제 목록 및 해결 기록 표시.
- 증상 네 종류, 발생 상황, 발견 시각, 등록자, 추가 메모 수기 기록.
- SQLite 영구 저장 및 트랜잭션, 재실행 복원, 다음 운전자 확인과 중복 방지.
- 미해결/점검 예정/점검 완료/해결 확인, 점검·조치 메모와 변경 이력.
- 같은 카드의 재발 차수와 재확인, 과거 확인 이력 보존.
- 증상·시각·설명·확인자·조치·해결 상태를 포함하는 정비 상담 요약.
- 공개 AAOS CarUxRestrictions 기반 상세 입력 제한. 플랫폼 응답 미확인/연결 끊김/제한 시 입력을 차단합니다. 실제 차량 속도·기어를 추정하거나 Vehicle SDK 값을 꾸미지 않습니다.

### 생성·수정한 파일

- `AGENTS.md`: 신규 MVP 범위와 현재 표시명을 우선 지침으로 추가.
- `NextDrive-Pleos/app/build.gradle.kts`: 공개 android.car 컴파일 라이브러리와 프레임워크 계측 runner.
- `NextDrive-Pleos/app/src/main/AndroidManifest.xml`: 플랫폼 car 라이브러리 사용 선언. 차량 제어나 인터넷 권한을 추가하지 않음.
- `NextDrive-Pleos/app/src/main/res/values/strings.xml`: 표시명 CARITDA(가칭).
- `NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/MainActivity.kt`: 실제 서비스 UI.
- 같은 경로의 `model/IssueModels.kt`, `data/IssueRepository.kt`, `data/SqliteIssueRepository.kt`, `domain/CaritdaService.kt`, `domain/ConsultationSummary.kt`, `safety/CarSafetyGate.kt`: 모델·저장·업무 규칙·요약·입력 제한 신규 구현.
- `NextDrive-Pleos/app/src/androidTest/java/com/nextdrive/pleos/MvpScenarioInstrumentation.kt`: 실제 DB/Activity/UI 계측 시나리오.
- `README.md`, `NextDrive-Pleos/README.md`: 현재 실행 상태와 안내. 프로젝트 README의 이전 환경 구축 기록은 보존.
- `docs/CARITDA_V01_MVP.md`: 사용자 실행 순서, 9단계 인수 시나리오, 실제 검증과 한계.
- `docs/CODEX_PROGRESS.md`: 이 기록. 이전 실패·사용자 확인 기록은 아래에 보존.

### 실제 테스트 결과

검증 환경: Windows, JBR 21.0.8, Gradle 8.11.1, AGP 8.10.1/Kotlin 2.2.21, Platform 34/Build-Tools 35.0.0, 기존 Pleos Connect v2.1.0/API 34 AVD emulator-5554.

1. 최종 소스로 `:app:assembleDebug :app:assembleDebugAndroidTest` BUILD SUCCESSFUL(56초). 기존 Gradle/의존성 캐시와 프로젝트 내부 작업 캐시를 사용한 offline 빌드입니다. 기존 디버그 인증서로 서명하여 앱 교체 설치 시 데이터를 보존했습니다. 키·캐시·로컬 설정은 공유하지 않습니다.
2. ADB `install -r`로 앱/테스트 APK 설치 성공, 실제 Activity 실행 성공. 실제 플랫폼 Car UX 서비스에서 입력 허용 응답 확인.
3. `CARITDA_MVP_PASS checks=24`: 요청한 9단계 전체를 실제 UI에서 수행. SQLite 재연결과 Activity 재시작 복원, A→B 확인, 중복 확인 방지, 점검 메모, 네 상태, 재발/재확인/기존 이력 보존, 잘못된 입력 거부, 제한 시 여섯 변경 동작 차단, 기존 기록 보존, 600dp 콘텐츠 폭 홈/입력 화면 검사 통과.
4. 최종 앱 프로세스 force-stop 후 실행하여 B와 기존 진동 기록 복원 확인. 실제 터치로 점검 예정 변경, 상담 요약에 진동 설명·B·점검 메모 표시 확인. 실제 요약 화면 스크린샷을 로컬 build/validation에 보관했습니다.
5. 앱 APK: `NextDrive-Pleos/app/build/outputs/apk/debug/app-debug.apk`. SHA-256 `9e0c7197f9d76d1b1abe665c2e9cd804537d20b28560a5514f7ea5a68705f043`.

### 실패·미검증·주의할 한계

- Lint 미완료: offline 캐시에 lint-gradle 31.10.1이 없고 일반 온라인 재시도는 Google Maven/Maven Central의 `Permission denied: getsockopt`로 실패했습니다. 네트워크 제한을 우회하지 않았습니다. 컴파일·APK·실제 계측 성공과 구분합니다.
- 초기 프로젝트 내부 빌드에서 캐시 경로·debug.keystore 잠금 파일·Kotlin daemon 쓰기 문제가 발생했습니다. 기존 읽기 캐시 경로를 바로잡고 프로젝트 내부 디버그 키 경로와 in-process 컴파일로 해결했습니다. 시스템 정책·전역 설정 변경이나 새 소프트웨어 설치는 하지 않았습니다.
- deprecated onBackPressed 컴파일 경고가 남아 있습니다. Release 서명/배포 빌드·모든 화면 크기·실제 주행 제한 전환·실차는 미검증입니다.
- Vehicle SDK 차량 데이터 수신, AI 진단/Gleo, 휴대폰 알림, 외부 정비 접수, 차량 제어는 미구현이며 완료로 표시하지 않습니다. 공개 AAOS 화면 제한 응답은 Pleos Vehicle SDK 센서 수신과 별개입니다.
- 검증용 수기 카드는 예시임을 메모에 표시하고 보존했습니다. 에뮬레이터 시간대 America/Los_Angeles 기준 시각을 화면에 사용하며 PC 시간대와 다릅니다.

### 게시 경로·후속 작업

GitHub 연결 도구를 통한 게시 대상은 위의 명시적 소스·문서 16개입니다. APK·DB·로그·스크린샷·개인 키·.tools·로컬 설정은 포함하지 않습니다. 작업 기준 main `ab7a05645fe44aa6e7d6d31471809a1a28b15ba1`과 대상 SHA를 대조하고, 게시 직전 다시 확인합니다. 같은 main을 부모로 한 커밋과 기대 HEAD 갱신을 사용하며 나머지 파일과 Aside SHA 보존을 검증합니다. 본 문서의 자기 커밋 ID를 넣기 위한 재커밋은 하지 않으며 최종 보고에 실제 게시 커밋을 제공합니다. 로컬 Git push/인증 작업은 수행하지 않았습니다.

다음: 사용자가 Android Studio에서 기존 app을 Run 하여 인수 시나리오를 재확인합니다. 네트워크가 허용된 일반 환경에서 Lint를 완료하고 주행 제한 전환을 별도 검증합니다. 다음 기능 후보는 사용자 확인을 거치는 상담 요약 파일 내보내기입니다.

## 아래는 이전 작업 기록입니다

# Codex 개발 진행 기록

마지막 업데이트: 2026-11-07 14:24:17 +09:00 (Asia/Seoul).

## 최신 검증 기록 — 사용자 환경에서 기본 Debug APK 실행 성공

### 완료 확인과 검증 주체

사용자가 일반 Windows Android Studio 및 ADB에서 아래 8개 항목을 직접 완료·확인했다고 전달했습니다. 이번 기록은 사용자 확인 결과이며 Codex가 재설치·재빌드·재실행한 결과로 표현하지 않습니다. 실제 실행 시각은 별도 미제공이며 위 시각은 문서 갱신 시각입니다.

1. Android SDK Platform 34 설치 완료.
2. Android SDK Build-Tools 35.0.0 설치 완료.
3. Android SDK Command-line Tools 및 Platform-Tools 설치 상태 확인 완료.
4. ADB로 Pleos Connect v2.1.0 에뮬레이터 연결 성공.
5. NextDrive-Pleos Gradle Sync 성공.
6. Gradle 8.11.1 및 기본 앱 관련 의존성 다운로드 성공.
7. Debug APK 빌드 성공.
8. Pleos Connect 에뮬레이터에 APK 설치 및 NextDrive 기본 테스트 화면 실행 성공.

**현재 확인된 상태:** 일반 사용자 환경에서 기본 앱 빌드·설치·실행 성공. **아직 미구현/미검증:** 공식 Vehicle SDK 실제 데이터 수신, 일반 앱의 차량 권한 허용, 항목별 Capability와 조회 응답. 이번 사용자 보고를 Release/실차/Lint/단위 테스트 성공으로 확대하지 않습니다.

### 수정 파일과 이번 작업의 검사 범위

- `PLATFORM_VALIDATION.md`: 사용자 확인 결과를 최신 검증 기록으로 추가하고 과거 Codex 실패 기록을 보존했습니다.
- `docs/CODEX_PROGRESS.md`: 사용자 확인 성공 8개와 검증 주체, 미구현 범위, 다음 작업을 기록했습니다.
- `docs/VEHICLE_SDK_READ_PLAN.md`: 다음 작업의 읽기 전용 차량 정보 1개 기술 검증 계획과 Android Studio 단계별 조작을 작성했습니다.
- 앱 소스·매니페스트·Gradle 설정은 읽기만 했고 변경하지 않았습니다. 현재 공식 Maven 설정은 있으나 Vehicle 의존성과 도어 권한은 없음을 확인했습니다. 서비스 기능 구현·SDK 설치·SDK 호출·실제 데이터 수신 시험은 이번 작업에서 수행하지 않았습니다.
- 선택한 Markdown 문서 3개의 용량·링크·바이너리·자격증명 패턴 검사와 기존 SDK/ADB 실패 기록 보존 확인을 통과했습니다. GitHub 연결 도구로 게시하고 원격 커밋 부모와 파일 해시/나머지 파일 보존을 확인합니다. 로컬 Git 연결·push·443 접근은 시도하지 않습니다.

### 과거 실패 기록과 다음 작업

아래의 SDK 다운로드·Gradle 준비·ADB 시작 실패 내역을 삭제하지 않았습니다. 당시 Codex 실행 환경의 관측 결과와 현재 사용자 환경의 성공을 각각 보존합니다. 해결된 사용자 환경을 다시 초기화하거나 SDK/AVD를 재설치하도록 요구하지 않습니다.

다음에는 공식 Vehicle SDK의 **앞 왼쪽 도어 위치**를 1차 후보로, 실제 AAR·함수별 권한 확인 → 읽기 Capability/영역 확인 → 유효한 콜백 값·시각 수신만 기술 검증할 계획입니다. 일반 앱 접근 가능성은 아직 확정하지 않습니다. 설치된 SDK의 실제 시그니처를 먼저 확인하며, 추측 API나 가상 값으로 성공을 만들지 않습니다. 사용자의 다음 조작은 필요한 의존성 변경이 준비된 후 Sync → External Libraries 확인 → 기존 Pleos AVD 선택 → Run → 테스트 화면/Logcat 확인 순서입니다. 자세한 내용은 `docs/VEHICLE_SDK_READ_PLAN.md`를 참조합니다.

현재 아이디어 재검토 상태를 유지하며 가족 인계·진단·정비사 연동 등 서비스 기능은 구현하지 않습니다. Aside 연구 파일과 기존 커밋도 보존합니다.

## 이전 작업 — Pleos 정식 빌드·Vehicle SDK 수신 준비 시도

### 성공한 작업

- 설치 상태와 공식 문서를 대조했습니다. 현재 Android Studio/JBR은 `C:\Program Files\Android\Android Studio` 아래에 있고 포함 Java 21.0.8 실행에 성공했습니다. 이전 Desktop1 경로가 없음을 확인해 활성 README의 경로를 바로잡았습니다.
- 기존 Pleos API 34 시스템 이미지/AVD(RAM 4 GB, Cold Boot) 설정을 확인했습니다. 사용자 세션의 기존 부팅 성공 사실은 유지하고 이번 Codex 세션의 앱 실행 성공과 구분했습니다.
- 시스템 SDK를 변경하지 않는 프로젝트 내부 `.tools/android-sdk` 설치 대상, Java/Android 사용자/임시 경로를 준비했습니다. 기존 수락 라이선스만 사용했고 새 라이선스를 자동 수락하지 않았습니다. 전역 환경 변수·PowerShell 정책·드라이버는 바꾸지 않았습니다.
- 공식 시작 가이드의 Vehicle 2.3.1 Maven 좌표, 도어 조회 권한, Capability 및 앞 왼쪽 도어 조회 흐름을 재확인했습니다. 확인한 문서와 실제 아티팩트/권한/수신을 구분했습니다.

### 실패한 작업과 미수행 단계

| 요청 단계 | 실제 결과 |
| --- | --- |
| SDK Platform 34 / Build Tools 35.0.0 | 첫 시도는 과거 JAVA_HOME 경로로 실패. 현재 JBR로 수정한 설치 요청도 원격 목록/manifest 다운로드 실패 후 종료 코드 1. 프로젝트 내부 SDK의 두 패키지 파일 없음 |
| Gradle 8.11.1 / 정식 Wrapper | 공식 CDN SHA-256 URL 연결 실패. 배포본/Wrapper 미확보. 전체 Gradle 빌드·Lint·Gradle 테스트 미수행 |
| 정식 APK | SDK/Wrapper 선행 조건이 없어 생성하지 못함. 기존 오프라인 리소스·Kotlin 검사 산출물을 APK라고 표현하지 않음 |
| Pleos 실제 앱 실행 | ADB 서버 시작 실패, 종료 코드 1. 로그는 `Cannot mkdir '\.android': Permission denied`. 프로젝트 내 Android 사용자 경로·호환 경로 지정 후에도 같은 결과. APK 설치·시작 미수행 |
| Vehicle SDK 실수신 | 공식 Nexus의 Vehicle 2.3.1 POM 연결 실패. AAR/함수별 권한/Capability/일반 앱 접근/값 수신 모두 미검증. 가상 값으로 대체하지 않음 |

이 세션에서 일반 앱이 접근 가능한 차량 정보 1개를 실제 수신했다는 결과는 없습니다. 문서상 최소 후보는 앞 왼쪽 도어 위치이며, 실제 SDK 확보 후 읽기 권한·영역·Capability·조회 응답을 차례로 검증해야 합니다. 상세 Reference 일부는 본문 시그니처가 노출되지 않아 추측으로 코드를 만들지 않았습니다.

### 변경 파일 및 검사 범위

- 수정: `NextDrive-Pleos/README.md`, `PLATFORM_VALIDATION.md`, `SDK_CAPABILITY_MATRIX.md`, `docs/CODEX_PROGRESS.md`.
- 생성: `docs/PLEOS_ENVIRONMENT_RECOVERY.md` — 일반 Windows 환경에서 SDK·정식 Wrapper·APK·ADB·최소 SDK 조회를 이어갈 절차. 실행하지 않은 명령은 안내로 표시했습니다.
- 이번 게시 대상은 문서 5개입니다. 앱 소스·매니페스트·Gradle 설정은 바꾸지 않았고 미검증 소스도 게시하지 않습니다. 문서 내용·명시적 경로·용량·링크·바이너리·민감정보 패턴 검사를 통과했습니다. 안내의 PowerShell 예제 2개는 구문 검사만 통과했으며 실행 성공으로 표현하지 않습니다.
- 도구용 파일·로그·라이선스는 Git 제외 경로 `.tools` 아래에만 남겼습니다. 기존 프로젝트·Google AI Studio 앱·Aside 연구 파일을 삭제하거나 초기화하지 않았습니다.

### 다음 작업 및 공유

현재 접근 제한으로 공통 환경 완성을 달성하지 못했습니다. 동일한 실패를 반복하거나 권한/네트워크 제한을 우회하지 않습니다. 일반 Windows 사용자 환경에서 문서의 SDK/Gradle/ADB 준비를 완료한 뒤 실제 기본 APK 실행, 공식 SDK 읽기 전용 어댑터 컴파일과 최소 1개 신호 수신을 검증해야 합니다. 가족 인계·진단·정비사 연동 등 서비스 기능은 계속 보류합니다.

원격 작업 기준 main은 `8081743d7322c251e2fdacaf7d17ffed43b1cbfb`입니다. 검증된 문서만 기존 Codex/Aside 이력을 잇는 커밋으로 **GitHub 연결 도구를 통해 게시**하고 파일 해시·나머지 파일 보존을 확인합니다. 커밋 ID는 최종 응답에 보고하며 로컬 Git 연결/443 접근을 다시 시도하지 않습니다.

## 이전 작업 — GitHub 연결 도구로 게시 경로 확정

### 완료한 작업 및 변경 파일

- 로컬 Git 연결 작업을 중단하고, 사용자 지시에 따라 `codex_apps.github`를 완료 후 자동 게시의 기본 경로로 확정했습니다. 이번 작업에서는 Git/GCM/clone/push·443 포트·루트 `.git` 접근을 재시도하지 않았습니다.
- `AGENTS.md`: 기존 로컬 Git 우선 규칙을 연결 도구 우선 규칙으로 교체했습니다. 변경 파일 검토·가능한 검사·보고서 갱신·검증된 파일 커밋·원격 확인·커밋 ID 보고를 상시 절차로 저장했습니다.
- `docs/GITHUB_SYNC.md`: 현재 활성 연결 도구 절차와 보류된 로컬 연결 안내를 구분했습니다.
- `docs/AUTO_PUBLISH_TEST.md`: `connector-completion-20261107` 테스트용 문서 변경을 추가했습니다.
- `docs/CODEX_PROGRESS.md`: 이번 작업과 검사 범위·오류·다음 작업을 기록했습니다. Android 소스/설정 및 Aside 연구 파일은 변경하지 않았습니다.

### 검사 결과 및 게시 검증 범위

- 중단 지점에서 재개하면서 현재 파일과 GitHub 기록을 다시 확인했습니다. 원격 main은 여전히 `bc5e60805decad1eb33c3c2a89534f54d8cb9b3e`이며, 이미 작성된 4개 문서의 게시 단계만 미완료였습니다. 문서 변경을 다시 만들거나 기존 커밋을 재업로드하지 않고 남은 검사·게시·원격 대조를 수행합니다.
- 연결 도구로 작업 시작 main `bc5e60805decad1eb33c3c2a89534f54d8cb9b3e`와 트리를 조회했습니다. 변경 대상 4개 문서의 원격 blob SHA와 로컬 기준 SHA가 모두 일치하여 시작 시 충돌이 없음을 확인했습니다.
- 이번 변경은 Markdown 문서 4개만 포함합니다. 내용·공유 안전 검사 후 기존 main 트리를 기반으로 해당 파일만 커밋합니다. 원격 main과 커밋 부모, 해당 4개 파일의 blob SHA 및 나머지 파일 보존 여부를 게시 후 확인합니다.
- 재개 후 문서 4개의 명시적 경로·파일 크기·링크 여부·바이너리/NUL·자격증명 패턴 검사를 통과했습니다. `AGENTS.md`의 기본 크기 제한도 확인했습니다. 앱/스크립트 실행 테스트를 수행한 것으로 표현하지 않습니다.
- Aside의 `docs/research/ASIDE_RESEARCH.md`, `docs/research/ASIDE_SYNC_TEST.md`는 기존 내용과 이력을 그대로 보존합니다. 기준 HEAD나 대상 파일 SHA가 바뀌거나 충돌하면 덮어쓰지 않고 중단합니다.
- 게시 경로는 **GitHub 연결 도구**입니다. 로컬 Git push 성공으로 표현하지 않습니다. 검증한 커밋 ID와 게시 후 대조 결과는 최종 응답에 제공합니다.

### 현재 오류·미구현·다음 작업

- 공통 환경을 읽기 전용으로 재확인했습니다. 설치된 컴파일 플랫폼은 API 36, Build Tools는 36.1.0이며 API 34 컴파일 플랫폼/Build Tools 35.0.0과 정식 `gradlew.bat`/`gradle-wrapper.jar`는 아직 없습니다. Pleos API 34 시스템 이미지와 컴파일용 SDK를 구분합니다. 이미 사용자 확인된 에뮬레이터 부팅과 기존 오프라인 소스 검사는 반복하지 않았습니다.
- 로컬 Git 연결은 실패 재시도 대상이 아니라 현재 보류된 작업입니다. 사용자가 일반 Windows PowerShell에서 인증·원격 접근을 확인했다고 전달한 사실과 Codex 세션의 제한을 구분합니다. 로그인 승인을 다시 요청하지 않습니다.
- 전체 Gradle 빌드·APK 실행·Vehicle SDK 실제 수신은 미검증 상태이며 이번 문서 게시 테스트에 포함하지 않았습니다. 기존 서비스 기능은 계속 개발 보류입니다.
- 다음 개발 작업부터 동일한 연결 도구 게시 절차를 적용합니다. 앱 개발을 재개할 경우 해당 변경에 맞는 검증을 수행한 후 검증된 파일만 게시합니다. 스케줄러나 로컬 Git 환경 변경은 진행하지 않습니다.

## 이전 작업 — 개발 완료 직후 자동 게시 규칙

### 완료한 작업

- Git for Windows 2.56.0.windows.4를 `C:\Program Files\Git\cmd\git.exe`에서 확인. 재설치하지 않음. GCM 2.9.1과 기본 credential helper `manager` 확인.
- 별도 Git 제외 폴더 `.sync/git-smoke-20261107-001502`에서 init/add/commit/status/log 실행 성공. 테스트 커밋 `fb82ef98b560f4f023436dac20297a7314356362`, 작업 트리 변경 없음 확인. 로컬 전용 가상 테스트 작성자이며 GitHub에 올리지 않음.
- `AGENTS.md`의 무조건적인 자동 커밋 금지를 '작업 완료·검증 성공 후 이번 작업의 명시적인 변경 파일만 자동 커밋·게시'로 교체. 이후 정상 게시에는 반복 확인을 요청하지 않는 상시 승인을 기록.
- 문서와 앱 변경의 검증 게이트, 기존 사용자 변경/Aside 연구 파일 보호, 원격 충돌 중단, API 게시와 로컬 push의 구분, 직접 Git 명령을 사용하는 정책 미변경 경로를 문서화.
- 최초 로컬 연결 절차와 테스트 문서를 준비. 이번 작업은 서비스 기능이나 앱 소스/설정을 바꾸지 않음.

### 수정 파일

- 수정: `AGENTS.md`, `docs/GITHUB_SYNC.md`, `docs/CODEX_PROGRESS.md`.
- 생성: `docs/LOCAL_GIT_SETUP.md`, `docs/AUTO_PUBLISH_TEST.md`.
- `docs/research/**`와 기존 앱 파일은 수정/삭제/스테이징하지 않음. 원격에만 있는 Aside 파일도 보존 대상으로 확인.

### 실제 검사 결과와 오류

- Git 버전 및 격리된 로컬 커밋 검사 성공. 실제 프로젝트 루트의 `.git`은 아직 없음.
- GCM `github list`: `Unable to persist credentials with the 'wincredman' credential store` 오류. 이 세션에서 저장소 접근이 안 되어 로컬 인증 유무를 확정하지 못함.
- 비대화형 Git `ls-remote`: github.com:443 연결 실패, 종료 코드 128. 로컬 인증·clone·push 미검증.
- PowerShell 유효 정책은 `Restricted`, 각 scope는 Undefined. 시스템 정책 변경, Bypass 옵션, 보안 해제, 자격증명 추출은 하지 않음. 직접 Git 실행과 문서 작업은 가능함을 확인.
- 프로젝트 루트 `.git` 경로가 현재 Codex 파일 권한에서 읽기로 제한됨. 이 제한을 우회하여 초기화하거나 외부 프로세스로 대신 실행하지 않음.
- 원격 main의 작업 시작 기준은 `6f1467e1828cec601dbd6c6a5abf92ff6f5263a5`. Codex/Aside 파일 목록을 읽기 전용으로 확인. 새 문서만 이 원격 이력을 잇는 커밋으로 게시하고 해시를 대조하는 범위임.
- `AGENTS.md`의 현재 내용은 직접 읽고 이번 작업에 적용. 새 세션을 실행하는 재시작 검증은 `codex` 재실행 금지 조건 때문에 하지 않음.
- 공유할 Markdown 파일 5개의 독립 경로/자격증명 패턴 검사 통과. `AGENTS.md`는 11,164 bytes로 기본 32 KiB 제한 이내이며 무조건적인 자동 커밋 금지와의 충돌을 수정했음을 확인.
- 전체 Gradle 빌드·APK 실행·SDK 수신은 이번 문서 변경의 테스트 대상이 아니며 여전히 미검증. 기존 앱이 검증 완료됐다고 주장하지 않음.

### 게시 경로 및 남은 작업

이번 문서의 게시 경로는 이미 인증된 GitHub 연결 도구입니다. 로컬 Git commit/push 성공으로 기록하지 않습니다. 보고서는 검증 문서·지침과 같은 커밋에 포함하고 게시 후 원격 내용을 확인하며, 자기 자신의 SHA를 넣기 위한 반복 커밋 대신 최종 작업 응답에 검증 SHA를 제공합니다.

최초 GCM 로그인 승인은 사용자에게 별도로 요청한 상태입니다. 승인 전 브라우저 로그인/인증 명령을 시작하지 않았습니다. 이 승인은 현재 세션의 네트워크·자격 증명·루트 `.git` 권한 제한을 자동 해제하지 않습니다. 필요 시 일반 사용자 PowerShell에서 승인된 최초 연결을 수행하고 정상 CLI push를 별도 검증해야 합니다.

**완료:** 영구 지침·작업 절차·로컬 Git 실행 검사·문서 게시 테스트 준비. **미완료:** 프로젝트 루트 Git 연결, GCM 실제 인증, 원격 clone/비교 및 CLI push 검증. 스케줄러는 등록하지 않았습니다.

## 이전 초기 공유 작업 기록 (아래는 2026-11-06 기준)

## 현재 범위

공모전 아이디어는 재검토 중입니다. 기존 NextDrive/카잇다 서비스 기능은 보류하며 이번 작업은 GitHub 공유·개발 기록·환경 확인으로 제한합니다. 기존 Google AI Studio 앱은 수정하지 않았고 현재 프로젝트 파일을 삭제하거나 초기화하지 않았습니다.

## 완료한 개발 작업

- GitHub 저장소 `kyc66533-art/vehicle-app-workspace`의 존재·비공개 상태와 연결 계정의 쓰기 권한 확인. 최초 조회에서 브랜치 없음/빈 저장소 확인.
- 로컬 루트·상위 경로·하위 앱 폴더에 Git 초기화 흔적 없음 확인. Git/gh는 PATH·일반 설치 경로·확인한 Codex 배포 폴더에서 미발견. 설치나 로컬 인증은 임의로 수행하지 않음.
- 사용자 확인 환경을 반영: Android Studio 정상 실행, Pleos Connect v2.1.0/API 34 이미지 설치·AVD 부팅. AVD 설정 파일에서 RAM 4096 MB / Cold Boot / Automotive 태그 재확인.
- 루트 `.gitignore`, 공유 파일 검사 스크립트, 검증된 기존 커밋만 게시하는 스크립트와 운영 절차 작성.
- 작업 완료마다 이 보고서를 갱신하도록 `AGENTS.md`에 지침 추가. 30분 스케줄러는 설계만 하고 등록하지 않음.

## 수정하거나 생성한 파일

- 생성: `.gitignore`, `README.md`, `docs/CODEX_PROGRESS.md`, `docs/GITHUB_SYNC.md`, `docs/UPLOADED_FILES.md`, `scripts/Test-ShareSafety.ps1`, `scripts/Publish-ValidatedCommit.ps1`.
- 수정: `AGENTS.md`, `PLATFORM_VALIDATION.md`.
- 기존 Android 소스·리소스·Gradle 설정 및 다른 명세서는 이번 작업에서 변경하지 않음. 공유 대상에는 기존 소스가 포함됨.

## 테스트 결과

- GitHub 인증·저장소 조회 성공. 연결 계정의 push 권한 확인. 로컬 Git 인증 성공으로 해석하지 않음.
- 초기 소스 커밋 `9d01e7739d5f1faceaaeab43c826747783706bbf`의 `main` 반영 확인. 재귀 트리 조회 결과 파일 26개, 추가 파일 0개이며 모든 Git blob SHA가 로컬 공유본과 일치함. 이 검증 후 현재 보고서에 결과를 반영하는 별도 문서 커밋을 추가함.
- Pleos AVD 설정 파일과 설치 이미지의 API 34/Automotive 태그 확인. 부팅 성공은 사용자 직접 확인 결과이며 이번 Codex 세션에서 다시 실행하지 않음.
- 새 PowerShell 스크립트 2개의 Parser 구문 검사 통과.
- 명시적으로 선정한 텍스트 파일 26개의 독립 인라인 경로·크기·바이너리·자격증명 패턴 검사 통과. 실제 키/토큰은 발견되지 않았으며 패턴 검사의 한계가 있어 파일도 검토함.
- `.ps1` 안전 검사 스크립트 실행 시 PowerShell 정책에 의해 차단됨. 정책을 변경/우회하지 않았으며 게시 스크립트의 실제 Git 동작은 테스트하지 못함. 위 인라인 파일 검사를 스크립트 런타임 검증과 구분함.
- 이전 작업: 설치된 AAPT2/SDK 36·Kotlin 2.1.21을 사용한 리소스 및 Kotlin 소스 오프라인 검사 통과. 정식 Gradle 툴체인 빌드가 아님.

## 현재 오류 및 제약

- 로컬 Git/gh 실행 파일 미발견, 로컬 `.git` 미생성. 따라서 로컬 origin 설정·Git 자격증명·CLI push는 미완료.
- 현재 세션의 PowerShell 스크립트 실행 정책으로 `.ps1` 직접 실행이 차단됨. 자동 게시 운영 전 사용자 환경에서 실행 정책과 조직 정책을 검토해야 함. 설정을 임의 변경하지 않음.
- 이전 Codex 프로세스의 IDE 시작/AEHD 접근 거부·Gradle/Pleos Maven 연결 실패는 과거 환경의 관측값. 사용자 세션에서 현재 IDE·Pleos 부팅은 정상 확인됨.
- 컴파일용 Platform 34와 Build Tools 35.0.0, 정식 Wrapper/Gradle, Vehicle SDK 설치·해석은 아직 미확인/미완료. 시스템 이미지 설치와 구분.

## 아직 구현·검증하지 않은 항목

- 전체 Gradle 빌드, Lint, Gradle 단위 테스트, APK 설치·실행, Vehicle SDK 실제 데이터·권한·Capability 수신.
- 로컬 Git 연결/인증, 게시 스크립트의 실제 Git 통합 실행, Windows 예약 작업 등록 및 실행.
- 기존 서비스 기능 전체는 개발 보류 상태이며 이번 공유를 통해 추가 구현하지 않음.

## 다음 개발 작업

1. Git 설치 위치 확인 또는 사용자 승인 후 설치, 사용자 승인에 따른 로컬 로그인과 원격 이력을 보존하는 로컬 연결.
2. 컴파일 SDK/빌드 도구·Wrapper·Gradle 준비 후 정식 빌드·테스트와 Pleos APK 실행 확인.
3. 공식 Vehicle SDK 확보 후 최소 읽기 전용 신호의 권한·Capability·수신을 한 화면에서 검증.
4. 실제 검증을 통과한 커밋/보고서를 공유. 게시 스크립트의 정상·dirty tree·인증 실패·원격 선행·분기 이력 사례를 테스트.
5. 위 조건 충족 후 필요하면 30분 예약 동기화를 사용자 별도 승인으로 등록.

## GitHub 업로드 상태

- **GitHub 연결 도구를 통한 초기 업로드 및 원격 검증 성공.** 저장소: [vehicle-app-workspace](https://github.com/kyc66533-art/vehicle-app-workspace), 브랜치 `main`, 비공개 유지.
- 첫 커밋: `9b381f49b3e1b6a1393c8b817e5eaf9fd342578b` — 저장소 README 초기화.
- 검증한 소스·문서 커밋: [9d01e773](https://github.com/kyc66533-art/vehicle-app-workspace/commit/9d01e7739d5f1faceaaeab43c826747783706bbf). 첫 커밋을 부모로 보존하는 일반 이력으로 반영했으며 강제 갱신하지 않음.
- 업로드 파일 26개는 `docs/UPLOADED_FILES.md`에 열거됨. 현재 보고서 갱신 커밋은 위 검증 결과를 추가하는 문서 변경이며 소스 변경이 아님.
- 최종 점검 중 다른 작업의 커밋 `2280ea3c6f764b7851b7d32852c77e541cc901aa`(`docs: add Aside GitHub connection test`)이 보고서 업로드 이후 추가된 것을 확인함. 추가 파일 `docs/research/ASIDE_SYNC_TEST.md`를 보존했으며 강제로 덮어쓰거나 로컬 파일로 가장하지 않음. 초기 공유 목록 26개와 별도로 원격에 추가된 문서임. 로컬 Git 연결 시 이 원격 이력도 반드시 보존해야 함.
- 로컬 Git 초기화·origin 연결·로컬 인증은 **미완료**. 연결된 GitHub 계정 인증만 사용했으며 인증 파일/토큰은 업로드하지 않음.
- **자동 동기화는 비활성/미등록.** 완료된 기존 커밋만 전송하는 스크립트와 절차만 준비했으며 구문 검사 외 실제 Git 통합 실행은 미검증. `.sync/validated-commit.json`도 생성하지 않음.
- 초기 공유는 사용자가 명시적으로 요청한 기존 소스 보존용이며 정식 빌드 미검증 상태를 포함함. 향후 자동 게시에는 동일 예외를 적용하지 않음.
