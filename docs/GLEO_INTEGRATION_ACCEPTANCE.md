# CARITDA Gleo·증상 음성 초안 통합 검증

## 2026-11-11 17:31 +09:00 — 일반 작성 흐름과 분석 서비스 공유

일반 작성→저장→AI 화면의 `IssueAnalysisService`를 기존 Gleo 새 분석 경로에서도 재사용합니다. 선택 기록 버전/frozen pending/결과 검증·동시 요청 차단을 공유하며 Gleo의 신뢰 확인·응답 예산·callback·3개 Action/Schema는 유지합니다. 외부 승인이나 Gleo 호출 성공을 새로 주장하지 않습니다. Gleo37개 회귀 통과, 일반 작성/인계·실제 SDK/TTS를 포함한 이번 전체298개는 [새 통합 검증](STT_AI_HANDOVER_ACCEPTANCE.md)을 따릅니다. 신규 Gemini 요청/Cloudflare 재배포는0입니다.

---

작업 기준: 2026-11-11 Asia/Seoul, main `d946720bb5598982ded7d56027b4eb788188740e`. 기존 사용자·Aside 변경과 실제 DB를 보존합니다. 최종 게시 커밋은 완료 응답과 GitHub 이력으로 확인합니다.

## 실제 확인한 공식 근거

아래 URL은 이번 작업에서 본문 또는 소스를 직접 열었습니다. 문서의 지원 설명과 이 에뮬레이터의 실제 실행 결과를 구분합니다.

| 자료 | 확인한 내용 |
| --- | --- |
| [Gleo 핵심 개념](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) | 앱 Action 연동이며 범용 LLM API가 아님 |
| [App Capability](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability) | assets/schema.json, 최대 3개 actionCalls, 영어 설명·parameter·activity/service |
| [Action Callback](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback) | success/asking/fail, 영어 완전 문장 message, voice/beep/silent, data는 JSON 객체 배열을 문자열화. 외부 Action 응답 대기 7초 |
| [공식 예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples) | Intent action=functionName, ACTION_PARAM, APP_ACTION_RESPONSE, packageName/response extra |
| [Schema 전달·심사](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema) | Manifest와 assets 준비만으로 실제 Gleo 등록·승인 완료가 아님 |
| [Playground](https://developers.pleos.ai/playground), [개발 리소스](https://developers.pleos.ai/playground/discover/resources) | 공개 SDK/샘플 접근 경로 |
| [STT](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) | 기존 3.3.1 ON_DEVICE/request 경로 보존, 별도 서버 인증 경로와 구분 |
| [FusedLocation](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro) | 1.1.3 isSupported/last-known/time/confidence. confidence는 정확도(m)가 아님 |
| [NaviHelper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro) | 3.0.5 연결/경로 조회/목적지 요청/경로 시작 callback 구분 |
| [Android callingPackage](https://developer.android.com/reference/android/app/Activity#getCallingPackage()) | API34의 일반 startActivity만으로 호출 앱 신원을 확인할 수 없음 |

공식 GitHub는 README 수준에서 멈추지 않고 다음 실제 파일까지 읽었습니다. 아래 SHA는 확인한 main 트리 조회 기준입니다.

| 저장소 / 기준 SHA | 읽은 실제 파일 |
| --- | --- |
| [Vehicle](https://github.com/Pleos-Playground/pleos-sample-vehicle-app), `959981d960ec71144accb27af5ae5d341e72a644` | `app/src/main/java/ai/pleos/playground/sample/vehicle/vehicle/VehicleClient.kt`, `explorer/PublicApiPolicy.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| [Navi](https://github.com/Pleos-Playground/pleos-sample-navi-app), `76ed592487a1eab7ba102b9d4b321705426886fe` | `app/src/main/java/ai/pleos/playground/navidevapp/screen/vm/NaviHelperViewModel.kt`, Manifest·app Gradle·version catalog |
| [Fused](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app), `c955c3a811371892e8803f3b1d9ad3f0426e832c` | `app/src/main/java/ai/pleos/playground/location/fused/sample/location/FusedLocationClient.kt`, Manifest·app Gradle·version catalog |

Vehicle 샘플 version catalog의 2.3.1로 앱을 낮추지 않았습니다. 설치된 Vehicle 2.3.2, Navi3.0.5, Fused1.1.3, STT/TTS3.3.1 및 공개 AAR/source 정의를 유지합니다. PublicApiPolicy의 실제 타이어 압력·Safety telltales·특권 capability 제한을 우회하지 않습니다. Fused 샘플의 mock provider도 운영 앱에 가져오지 않았습니다.

## 구현

- `SpeechDraftDelivery`와 MainActivity: final과 실제 ended가 모두 도착한 뒤 원래 편집 중이던 증상에 한 번만 덧붙입니다. 확인 팝업을 만들지 않습니다. 원문·초안 ID·작성자·화면·수정 가능 상태가 달라지면 적용하지 않습니다. 빈 문장/길이 초과/취소/늦은 callback은 기존 입력을 유지합니다. 문제 DB 저장은 기존 저장 버튼으로만 실행합니다.
- `CaritdaActions`: QUERY/DRAFT 재사용, ANALYZE 하나 추가. `ACTION_PARAM` 객체·공식 quoted-string 예제·개별 parameter를 처리하고 상충/알 수 없는 parameter/긴 입력을 거부합니다. 배터리 최신 앱 수신과 이전/오래됨/실패를 구분합니다. 인계 조회가 읽음·상태 이벤트를 만들지 않습니다.
- `GleoReplyChannel`: 공식 callback envelope를 만들되, 검증된 호출 패키지·서명 pin·명시적 수신 component·별도 읽기 동의가 모두 있어야 보냅니다. 현재 pin/패키지는 빈 값으로 유지합니다. 단순 Intent extra/referrer를 인증으로 쓰지 않습니다. 앱 TTS를 Gleo 답변과 동시에 자동 실행하지 않습니다.
- `GleoIssueAnalysis`: 기존 Firebase 세션·Keystore·PortableAiClient·암호화 캐시를 재사용합니다. `existing`은 저장된 결과만, `new`는 명시적 새 분석입니다. 동의/모델 OFF/잔여0/pending/UNKNOWN을 유지하고 원격 클라이언트의 재검증도 거칩니다. category/situation 이외 차량값·위치·기록·메모·이름을 추가하지 않았습니다.
- Action 진입부터 6초를 내부 응답 예산으로 잡고 약간의 전송 여유를 둡니다. 공식 7초보다 보수적인 앱 제한이며 전체 대화나 실제 모델 지연 보장이 아닙니다. 늦은 결과를 success로 반환하지 않고 자동 재호출·늦은 Gleo 재발화를 하지 않습니다. 이미 공급자에 전달됐을 수 있는 요청의 pending 원장은 지우지 않습니다.
- 분석 대상이 없는 “이거”를 최근 기록이라고 추측하지 않습니다. 한 건을 특정하지 못하면 asking/정차 중 기록 선택으로 연결합니다. 검증되지 않은 외부 호출자는 조회·저장·AI 실행 전에 앱 확인 화면을 거칩니다.
- Navi: listener를 명시적으로 제거하며 경로 검색 완료와 실제 경로 시작 callback을 따로 표시합니다. 기존 경로가 있으면 요청하지 않습니다. 위치 확인은 last-known 시각·출처를 표시하며 정확도(m)와 증상 당시 위치를 만들어내지 않습니다.

## 실제 외부 연결의 차단 조건

기기에는 `ai.pleos.gleoai.model.provider` 및 시스템 음성 서비스 `ai.umos.ambientai`가 있습니다. 따라서 “Gleo 관련 앱이 전혀 설치되지 않았다”고 결론 내리지 않습니다. 다만 점검 시 voiceinteraction은 mBound=true/mService=null, hotword 연결 없음이었고, 공식 APP_ACTION_RESPONSE에 대응하는 manifest receiver와 현재 등록 receiver는 확인되지 않았습니다. Schema 승인도 확인되지 않았습니다.

공식 예제의 implicit broadcast를 그대로 써서 사용자 기록을 전체 앱에 보내지 않았습니다. 공개 문서에서 수신자의 검증 가능한 패키지·서명·호출자 인증 계약까지 확인되지 않았습니다. 인증된 앱 결과 호출 또는 공식 서명 권한 등 검증 가능한 계약과 Schema 승인이 필요합니다. 현재 API34 plain startActivity 호출은 신원 증명이 아니므로 로컬 확인으로 제한합니다. 새 권한/임의 패키지 pin/계정/Secret을 만들지 않았습니다.

| Gleo 검증 단계 | 판정 기준 |
| --- | --- |
| A JSON Schema | 일반/validation 패키지·3개 Action·Manifest/dispatcher 일치 검사 |
| B 외부 Intent→앱 | validation 실제 Activity 실행; Gleo 자연어 라우팅과 다름 |
| C Callback 구성 | status/message/mode/문자열화 data의 실행 검사 |
| D 검증된 대상 Broadcast | 현재 대상 인증 불가. 전송 차단을 검사하며 성공 전송으로 보고하지 않음 |
| E Wake word→Action | 승인·활성 런타임 대기. 앱 이름 없는 라우팅도 미검증 |
| F Gleo 한국어 음성 | 미검증. 앱 자체 Pleos TTS와 구분 |
| G 신규 Gemini→7초 내 Gleo 음성 | 미검증. 모델/예산 변경·이번 신규 호출0, 캐시로 대체하지 않음 |

## 검사·산출물

실행 결과와 현재 데이터 전후 비교는 [최종 통합 보고서](CARITDA_INTEGRATION_REPORT.md)의 이번 작업 절을 따릅니다. 과거 313 assertion이나 실제 발화3회를 이번 새 버전 전체 실행 횟수로 재사용하지 않습니다. 앱의 수정 부분은 validation 고유 DB와 합성 callback, 실제 SDK 읽기 검사를 구분합니다. 일반 앱에는 시험 문제를 추가하지 않습니다.

## 시연 순서

1. 일반 CARITDA에서 기존 운전자를 확인하고 최근 차량 정보를 봅니다. 지원되지 않는 값은 그대로 미지원입니다.
2. 증상 남기기에서 기존 입력을 유지한 채 음성 입력을 시작합니다. 말을 마치고 최종/종료를 기다리면 문장이 입력란에 추가됩니다. 확인 팝업과 수동 완료 버튼은 없습니다.
3. 내용을 직접 확인·수정한 뒤 저장합니다. 다른 운전자가 해당 기록과 인계 필요 내용을 확인하고 명시적으로 읽음/점검 상태를 남깁니다.
4. 기존 AI 설명 보기는 API를 호출하지 않습니다. 새 분석은 기존 동의·인증·모델/예산 조건이 충족될 때만 기존 버튼으로 요청합니다. 이번 시험에서 신규 요청은 하지 않습니다.
5. Gleo 외부 승인이 완료되기 전에는 ADB Action 화면/내부 callback 시험만 시연하고 실제 핸즈프리 왕복이라고 발표하지 않습니다.

UI 팀은 기존 3개 핵심 흐름과 canEdit/canWrite, 초안/명시적 저장, category/situation 동의 경계를 유지해야 합니다. STT SDK 수명주기를 다시 작성하거나 수동 완료 팝업을 복원하지 마세요. Gleo·STT·TTS·위치·Navi를 독립 홈 메뉴로 늘리지 않았습니다.
