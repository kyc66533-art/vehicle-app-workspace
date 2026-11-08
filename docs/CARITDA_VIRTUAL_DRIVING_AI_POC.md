# CARITDA 가상 주행·자동 기록·AI 연결 PoC

2026-11-08 21:45 +09:00 (Asia/Seoul). 기준 main `3c1a5f5a034a0515bda5a9d1202863bb8d7fd17b`. Pleos Connect v2.1.0/API34, Vehicle SDK2.3.2, emulator-5554/Android user10. CARITDA는 상표 검증 전 가칭입니다.

**부분 성공:** Console→실제 SDK GET/변화 callback→유효성/사용자 규칙→자동 안내 저장·화면→재실행 복원은 성공했습니다. 실제 인증된 AI 모델 요청/응답은 승인된 중계 인증과 비용 설정을 전달받지 못해 **실행하지 않았습니다**. HTTPS 연결과 validation JSON Mock를 AI 연결 성공으로 바꾸어 보고하지 않습니다. 실차/실제 배터리 고장 실험이 아닙니다.

## 1. Console의 공식 범위와 실제 연결

[공식 설정 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)와 [신호별 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)를 읽었습니다. 지원 최소 버전은2.1.0이며 Energy의 Battery SOC와 Motion/Convenience/Safety 등의 시험 항목이 있습니다. Console의 조작 가능 항목과 앱 SDK 권한·Capability·실제 수신은 별도입니다.

- 기존 AVD/ADB user10을 확인했습니다. 시작 시 forward가 없어 **한 번** tcp8088→tcp8088을 등록했고 Console GET `/`가 HTTP200으로 응답했습니다. AVD 재생성/wipe/SDK 재설치는 하지 않았습니다.
- 자동 승인 검토가 복합 shell 명령 및 자동 브라우저 실행을 거절했습니다. 추가 승인 창은 정책상 불가능했고 자동 브라우저 실행을 다른 방법으로 재시도하지 않았습니다. 허용된 개별 ADB/로컬 HTTP 명령으로 진행했으며 보안 정책·sandbox·TLS 설정을 변경하지 않았습니다.
- 이번 CLI는 PC 브라우저의 GUI를 자동 조작하지 않았습니다. 실제 제공된 Console HTML/catalog/state를 읽고 **그 페이지가 사용하는 동일한 웹 제어 요청**을 로컬 Console에 보냈습니다. 브라우저 렌더/슬라이더 조작 성공으로 보고하지 않습니다. 사용자가 PC 브라우저에서 최종 수동 시연을 확인해야 합니다.
- 처음 SOC 변경은 `emulator_inactive`로 거절됐습니다. 페이지의 세션 유지 요청 `/browser/session`이 활성화돼야 한다는 것을 HTML과 state의 browser.active/bridge_ready로 확인했습니다. 같은 세션 요청을 유지하자 active/bridge가 true가 되고, catalog가 지정한 `/vehicle/ev-battery?field=soc&value=...` PUT이 HTTP200으로 반영됐습니다. 별도 VHAL 강제 쓰기/비공개 SDK 호출/루팅은 사용하지 않았습니다.
- 임시 Console 세션은 유한 시간으로 실행했습니다. 세션 만료 직후 복원 요청이 한 차례 거절된 것도 기록하고, 새 Console 세션에서 실제 복원 HTTP200을 확인했습니다. 최종 시험 상태는 **SOC100/P/0km/h**입니다. 세션이 닫히면 Console이 비활성 상태가 될 수 있으며 SDK 관측과 동일한 뜻이 아닙니다.

## 2. Console와 SDK의 별도 지원 표

| 신호 | Console에서 확인한 범위 | 앱의 실제 SDK/권한·Capability | 이번 결과 |
|---|---|---|---|
| SOC | Battery SOC0~100%, 실제80/18/45 설정 HTTP200 | EvBattery의 checkEvBatteryStateOfChargeCapabilities/getEvBatteryStateOfCharge/registerEvBatteryStateOfCharge, CAR_ENERGY | GET80, 실제 변화 callback18/45와 반복 GET18 수신·저장 성공 |
| 누적거리 | Odometer0~1,000,000km 항목 확인, 이번 변경 안 함 | 기존 Odometer/CAR_DRIVE/READ | 기존0.0km 실제 수신·회귀 유지 |
| 연료 | 이번 Console 조작 항목 확인/변경 안 함 | 기존 Fuel/CAR_ENERGY, ERROR_NOT_SUPPORTED_PROPERTY | 미지원/값 없음 유지; EV라는 판정 아님 |
| 도어 | Door Position 항목·공식 문서 확인 | Door 공개 읽기 후보/CAR_DOORS, 개별 권한·Capability·callback 미시험 | P1 보류, 열림을 고장으로 판단 안 함 |
| 창문 | Window Position 항목·공식 문서 확인 | Window 공개 읽기 후보/CAR_WINDOWS, 개별 권한·Capability 미시험 | P1 보류 |
| 기어 | 실제 Drive→Park 조작 성공 | 별도 SDK 기어 값 조회는 이번 미시험 | Console 값과 AAOS 제한만 확인, D를 속도 증거로 쓰지 않음 |
| 속도 | Console controls20→0km/h 조작 성공 | 별도 SDK/AAOS 속도 읽기 권한/Capability 미시험 | CarDrivingState/UX 제한 변화를 확인, 속도 SDK 수신 성공이라고 하지 않음 |
| TPMS·ABS | Telltales 항목/공식 문서 확인. tire-pressure는 catalog의 misc 항목이며 UI 노출·조작 미시험 | Safety/Tire 개별 공개 정책·특권/권한·Capability 미시험 | 강제 조회 안 함, 실제 압력/경고 자동 감지 미구현 |

설치 AAR에서 EvBatteryStateOfChargeListener의 `onEvBatteryStateOfChargeUpdated(Integer)`와 `onFailed(Exception)`, 등록·해제의 CAR_ENERGY annotation을 직접 확인했습니다. [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)도 해당 구독 API를 설명합니다. API 존재/등록 반환과 실제 callback 수신을 분리했습니다. 다른 신호의 대표 권한이 모든 세부 API 접근을 보장하는 것은 아닙니다. P1을 위해 새 차량 권한을 선언하지 않았습니다.

## 3. 실제 SOC 실험 — 최종 후보

현재 raw/파생 DB를 사용하는 validation 앱의 실제 UI에서 사용자 기준20% 미만을 명시적으로 켰습니다. 합성 fixture와 별개의 실제 SDK 실험입니다.

| 단계 | 실제 결과 |
|---|---|
|80% 설정 | SDK GET80 저장. 낮은 충전량 안내0건 |
|18% 설정 | 버튼 재조회 없이 SOC CALLBACK18 수신. 사용자 기준 사건1개/진입 이력1개 |
|18% 반복 | Console 반복 PUT200, 실제 SDK GET18 재수신. 같은 사건ID/사건1개/변경 이력1개 유지. 같은 값의 callback 자체를 매번 보장하지는 않음 |
|45% 설정 | 버튼 재조회 없이 CALLBACK45. **같은 사건ID**의 조건 해제/변경 이력2개. 수리 완료/사용자 문제 해결로 바꾸지 않음 |
|화면 종료 | SDK 해제·구독 해제/generation 차단, 관측 공백 기록. 이후 계속 관측 중이라고 표시하지 않음 |
|프로세스 재시작 | 사건ID/조건 해제/변경 이력 유지, 중복 진입 없음 |

최종 live stage에서 raw 관측은 READY80의6행→LOW18의7행→반복 SDK GET 뒤10행이었고, 사건 수는1개였습니다. 숫자는 validation 기본 관측 DB의 이 실험 당시 행 수이며 사용자 DB/고장 수가 아닙니다. 각 callback은 PLEOS_EMULATOR_SDK, `%`, 앱 receivedAt, source store UUID+row ID와 연결했습니다. GET/CALLBACK 경로는 원본 detail에 남습니다. 별도의 차량 측 측정시각은 제공되지 않았으므로 만들지 않았습니다.

초기 후보 실험과 최종 후보를 각각 실행했습니다. 중간 한 번은 초기80% 확인이 실패했고, Console 값을 다시 설정한 뒤 실제80 수신부터 재실행해 통과했습니다. 해당 실패의 단일 원인은 확정하지 않았습니다. Console HTTP 성공을 SDK 값 반영의 증거로 대신 쓰지 않았습니다.

최종 수동 화면 확인에서도 실제18%의 ‘낮은 충전량 안내’,45%의 ‘낮은 충전량 조건 해제’를 XML/화면 캡처로 확인했습니다. 자동 계측의 즉시 캡처는 DB commit 직후 프레임 전환 전18%가 찍힌 경우가 있어, 화면 표시 검증은 그 캡처 하나에 의존하지 않았습니다. 모든 캡처는 로컬 비공개 폴더에만 있습니다.

### A/B/C/D 관측의 구분

| 경로 | 판정 |
|---|---|
|A 버튼 GET | 기존 새로 조회 유지. 실제 반복18% 수신 성공 |
|B 화면 가시 상태의 Listener | 실제18/45 변화 callback 수신 성공. 등록 성공만으로 집계 안 함 |
|C 허용 수명주기 | 사용자 켜기+가시+UXR ALLOWED에서 구독, onStop/안전 제한/연결 실패 시 해제. 복귀 시 Capability/현재값을 다시 확인 |
|D 화면 비가시·종료 상태 | 상시 관측 미구현. FGS/무한 polling/시동 자동 실행 보장 없음. 공백으로 표시 |

## 4. 규칙·원본·중복 방지

새 `caritda-soc-notices.db`는 policy/evaluations/episodes/changes/gaps의 **별도 sidecar**입니다. 기존 문제/관측/스냅샷 DB version과 데이터를 이전하거나 초기화하지 않았습니다. ObservationStore에는 원래 append(Unit)를 유지하면서 실제 insert 결과의 안정적인 ObservationRef를 반환하는 appendRef만 추가했습니다.

- 사용자 설정은 기본 꺼짐, 기준1~95%이며 비교는 **미만(LT)**입니다. 기본 표시20%는 제조사 고장 임계값이 아닙니다.
- 중복 억제를 위해 해제는 기준+2%p 이상입니다.20% 기준에서20%는 새 low가 아니며22% 이상부터 해제합니다. 이 여유 폭은 버전 있는 편의 정책이며 수리/안전 규격이 아닙니다.
- 정상 정보/사용자 low/조건 해제/유효성 오류/오래됨·부족을 구분합니다. 실제 경고 신호를 읽은 것이 아니므로 SOC로 고장·운행 가능 여부를 판정하지 않습니다. 실패/미지원/오래된 값은 새 low나 해제가 아닙니다.
- 안정 scope=관측 저장소UUID+출처+SOC, 사건ID는 실제 해제·재진입 전까지 유지합니다. 날짜/운전자/세션/앱 재시작·같은 값·정책 버전 변경만으로 사건을 새로 만들지 않습니다. 기준 변경은 새 관측으로 같은 사건을 재평가하고 이력의 당시 규칙/기준/버전을 보존합니다.
- 평가 중복 키는 정책 버전+store UUID+row ID입니다. raw를 먼저 commit한 뒤 sidecar transaction을 수행합니다. 실패 주입 시 raw 보존/파생 rollback, 같은 참조로 한 번만 재시도를 검증했습니다. 두 DB가 원자 트랜잭션이라고 가정하지 않습니다.
- 켤 때 이미 저장돼 있던15%만으로 새 low 사건을 만들지 않습니다. 설정 이후 수신된 실제 새 참조부터 평가합니다. SOC의 실제 ECU 측정 신선도/차량 식별은 여전히 미확인이고, 앱 수신 기준임을 유지합니다.
- 사용자 문제/네 수동 상태/작성자·인계/수정/점검·재발/초안/불변 링크에 쓰지 않습니다. 기존 EventKind에 SYSTEM이나 가짜 운전자를 추가하지 않았습니다.

일반 앱에서는 차량 정보→충전량 자동 안내·설정과 기록으로 접근합니다. 상태·조건·시각·출처를 먼저 보이며 규칙/근거 ID는 수신 근거 자세히에 둡니다. 이 기능은 SOC 편의 안내이고 모든 차량 고장의 자동 진단이 아닙니다. 숨김/별도 안내 영구 삭제/푸시는 이번 미구현이며 안내 끄기는 이전 기록 삭제나 해결을 의미하지 않습니다.

## 5. AI 연결의 실제 결과와 승인 필요 항목

| 단계 | 결과 |
|---|---|
|에뮬레이터 인터넷/HTTPS | 공식 문서 HEAD 실제 HTTP200, TLS_AES_128_GCM_SHA256, 최종 측정359ms. 인증/선택 기록/키를 보내지 않음 |
|인증된 모델 요청 | **미실행**: 승인된 중계 URL·임시 앱 인증·모델/비용 범위가 전달되지 않음 |
|실제 모델 응답/지연/오류/차량 화면 | **미검증**. HTTPS 성공을 대신 사용하지 않음 |
|인터페이스/JSON | ExplanationInput/Contract/AiRelayClient 구현. HTTPS만 허용·리다이렉트 금지·응답16KiB 제한·시간 제한·요청ID 대응 |
|Mock | validation에서만 명시MOCK JSON과 거부 사례 검사. 일반 MODEL parser는 Mock 거부. 일반 앱은 AI 미연결 안내 |
|안전성 | 짧은 한국어/정보 목적·위험한 확정 문구 거부/최소 입력 계약 검사. 실제 모델의 사실 일치·고령자 이해도·전체 안전성을 검증한 것은 아님 |

실사용 기록을 외부로 전송하지 않았습니다. 입력 계약은 선택된 신호의 값/단위/앱 수신시각/출처·조건/규칙 버전·현재 평가와 임의 요청ID만 포함하며 이름/VIN/위치/전체 DB/로컬 관측ID는 제외합니다. AI 모듈은 규칙/사용자 상태 writer를 호출하지 않습니다. 모델 오류나 Mock가 원래 사건을 지우지 않습니다. 현재 실제 AI 설명 저장/사용자 동의·전송·취소 UI는 미완료입니다.

[OpenAI Docs의 공식 인증 지침](https://developers.openai.com/api/docs/guides/api-keys)을 확인했습니다. provider key는 신뢰하는 서버의 비밀 저장소에 두고 APK/소스/로그/브라우저에 넣지 않는 방식입니다. 이번에는 공급자를 확정하거나 특정 유료 모델을 선택하지 않았습니다. OpenAI 또는 다른 모델을 쓰려면 먼저 다음을 승인·준비해야 합니다.

1. 사용할 모델 제공자/프로젝트와 시험 비용 한도·호출 수.
2. 기존 또는 새 **HTTPS 중계 서버**의 소유자/운영·배포 승인. 이번에 새 서버를 만들거나 배포하지 않았습니다.
3. provider key는 서버 측 비밀 저장소에 설정. 채팅·저장소·APK에 보내지 않습니다.
4. 앱에는 짧은 수명의 제한된 앱 토큰만 메모리로 전달하는 인증 경로. provider key와 다른 값입니다.
5. 선택 데이터 미리보기/명시 동의·서버 보관 정책·요청 제한·취소·응답의 수치/근거 일치·실제 모델 문구 검증 후 차량 UI를 연결합니다.

공식 문서 조회는 계정 인증을 확인한 것이 아닙니다. 개인 인증 파일이나 환경 변수에서 키를 탐색하지 않았고 유료 API/계정 설정/발급/외부 배포를 수행하지 않았습니다. JSON Mock의 문구를 AI가 생성한 설명으로 발표하면 안 됩니다.

## 6. Gleo의 별도 범위

[2026-10-19 Playground 릴리즈 노트](https://document.pleos.ai/en/docs/pleos-only/release-note/playground-release-note)는 LLM SDK 종료와 Gleo Interface 사용을 안내합니다. [Gleo Core Concepts](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts)의 방향은 Gleo가 등록된 앱 Action을 호출하고 Callback으로 결과를 받는 것입니다.

- Gleo→CARITDA: 공식 인터페이스 방향은 확인, App Schema·승인·인증/실행 환경과 실제 호출은 미구현/미검증.
- CARITDA→독립 Gleo 자유 상담: 공식 지원 확인 못 함. Callback을 새 상담 시작 API로 사용하지 않았습니다.
- 별도 외부 AI: 앞 절의 승인된 중계/인증이 필요한 독립 경로이며 Gleo 연결이 아닙니다.

스키마 제출/음성/Callback 수신/최종 발화를 실행하지 않았습니다. 과거 LLM SDK를 다시 설치하거나 비공개 호출을 하지 않습니다.

## 7. 가상 주행 제한

Console Drive와20km/h 설정을 각각 HTTP200으로 확인했습니다. CarDrivingState가0→1→2로, UXR가 No DO→DO/0x50으로 변했고 display0 제한이 기록됐습니다. validation 화면에서 홈/기록/차량/메뉴가 enabled=false, 주차 안내가 표시됐습니다. 이 환경에서는 Activity는 여전히 최상위에 보였습니다. 모든 Pleos 환경에서 앱이 계속 보이거나 OS가 반드시 닫는다고 일반화하지 않습니다.

해당 수동 실험은 MainActivity/CarSafetyGate/PleosSocObserver가 최종과 동일한 전단계 APK에서 수행했습니다. 이후 변경은 sidecar 중복/이력 정책과 그 검사이며 제한 우회는 없습니다. 최종 후보에서도 기존 안전 회귀를 재실행했습니다. 속도0/P 복원 후 DO→No DO/0을 확인했습니다. 실제 운행/실차 제한 시험이 아니며, 단순 D만으로 이동했다고 판단하지 않았습니다.

## 8. 빌드·회귀·원본 보존

- 최종 offline assembleDebug/assembleValidation/assembleValidationAndroidTest BUILD SUCCESSFUL in1m31s,93작업13실행/80 up-to-date. 기존 Gradle8.11.1/JBR/Platform34/Build-Tools35.0.0/SDK2.3.2 유지.
- Debug APK SHA256 `8F94DEDE4E98F3172C255F8A4806F6298FCE1F59A88D6ADF5E2BAA695EE4205F`. 설치된 일반 base.apk와 일치했습니다. Release APK가 아닙니다.
- 기존 **297개**=CP48/cold3+MVP24/관측6/UI27+migration18+RR36/cold2+NAV17/cold12+ON32+AM35/cold4/사본보존2+PX25/3DB보존6. 최종 후보에서 재통과했습니다.
- 신규 **23개**=규칙/JSON13+실제 Console·SDK live8+실제 cold2. 총 **320 assertion 통과**. TLS 진단/수동 주행·UI 확인/행렬 render 개수는 assertion 합계에 더하지 않았습니다. 로그는 로컬 app/build/validation/poc-candidate-*.txt입니다.
- 기존PX198 native render/ON200/NAV낮은 높이6조합은 재통과했습니다. 새 SOC 화면의 전체 확대·작은 창·OS 분할/IME·TalkBack/rotary/고령자 과제는 미검증입니다.
- 초기 Kotlin 지역 함수 줄 구문 오류는 수정 후 빌드 성공했습니다. 전단계 메뉴 복귀 정확 픽셀 검사가 한 번 실패했고 같은 APK 재실행 CP48과 최종 후보 CP48은 통과했습니다. 단일 원인은 미확정이며 간헐성 재발 관찰이 남습니다. 과거 실패 로그를 지우거나 통과로 고치지 않았습니다.
- 소스·DB 백업은 `.tools/poc-backup/20261108-virtual-poc/`입니다. 일반 앱을 멈추고 전체 databases/shared_prefs/files의 before.tar와3DB를 새로 백업했습니다. 테스트는 validation에서만 실행했습니다. 후보 실제 SDK 실험 DB/이미지/Console 응답/제한 로그와 after-install.tar도 로컬 비공개로 보존했습니다.
- 모든 계측/가상 주행 전후 일반3DB는 바이트 동일했습니다. 교체 설치/화면 확인 후에도 문제 DB SHA256 `A906AA7FDEFF00A9AC444A8DB306EF06791D0DD1A0B461073932277DBAC1C92C`가 동일하며 **현재23건**을 확인했습니다. 기존 원문/운전자/이벤트/초안 전체 scalar와 관측/스냅샷/링크 포함·동일 version/integrity/FK를 독립 사본에서 비교했습니다. 일반 관측/스냅샷에는 기존 진입 시 실제100%/0km만 추가됐습니다.
- 일반 앱은 데이터 유지 install-r/COLD 실행을 확인했습니다. 자동 안내는 기본 꺼짐/안내0건/AI미연결이며 validation 사건을 복사하지 않았습니다. 일반 사용자 프로필/증상/조치/초안에 시험 데이터를 추가하거나 과거 출처 불명 기록을 지우지 않았습니다.
- 문제 Repository/Service/모델·RecordMigration/SnapshotStore/PleosReadClient/EntryReadState/CarSafetyGate는 기준 SHA와 동일합니다. 새 기존 DB migration이나 권한 우회는 없습니다. 추가 Android 권한은 HTTPS 진단/향후 승인된 중계용 INTERNET뿐입니다.

## 9. 구현 상태·다음 승인·실제 시연

구현: 가시·입력 허용 SOC 구독, 원본/규칙/사건/근거·해제·공백 저장, 중복/정책 변경·rollback/replay, 실제 상태 화면. 부분 구현: AI 최소 계약/HTTPS relay client/Mock JSON. 미구현·미검증: 인증된 실제 모델과 AI 차량 화면, P1 다른 신호, 상시 백그라운드/시동 자동 실행/자동 고장·수리 판정/Gleo/푸시, 실차, Release 서명/제출 APK. Lint는 기존 캐시 부재 상태에서 재실행하지 않았습니다. 사용자 직접 검수6개는 이전 사용자 보고이며 이번 자동 검사와 별도입니다.

일반 앱에 시험 사건을 남기고 싶지 않다면 **[TEST] 검증 앱**으로 다음을 시연합니다.

1. 기존 AVD가 P/0km/h인지 확인합니다. PC 브라우저에서 `http://127.0.0.1:8088/`를 열고 Emulation Controls→Energy→Battery SOC를 찾습니다. 이번 CLI는 GUI 자동화를 수행하지 않았으므로 실제 브라우저 조작을 먼저 확인하세요.
2. Console80% 설정→validation CARITDA→차량 정보→충전량 자동 안내·설정과 기록→20% 미만 안내 켜기. 정상 값에서는 low 사건이 없어야 합니다.
3. 브라우저18% 설정. 앱의 새로 조회를 누르지 않고 낮은 충전량 안내가 나타나는지 확인합니다. 반복18%에서 새 사건이 무한히 생기지 않아야 합니다.
4. 45% 설정. 같은 사건의 조건 해제 기록을 확인합니다. 고장 수리 완료 문구가 나오면 안 됩니다.
5. 앱 재실행 후 기록을 확인합니다. 앱이 꺼진 동안은 관측 공백이며 상시 감시라고 설명하지 않습니다.
6. AI는 아직 미연결임을 보여줍니다. 실제 모델 시연은 앞 절의 인증·비용/전송 승인 뒤 별도로 검증해야 합니다. Mock/개발자가 작성한 문구를 실제 AI 응답으로 발표하지 않습니다.
7. 시험 후0km/h/P/SOC100으로 복원하고 필요 없으면 안내를 끕니다. 끄기는 저장 이력 삭제나 문제 해결을 뜻하지 않습니다.

## 10. GitHub 게시

이번 변경은 사용자 PC 소스와 일반 앱에 반영됐습니다. 검증한 파일만 최신 main의 이력을 잇는 **GitHub 연결 도구를 통한 게시**로 반영합니다. 기존 Aside 연구/다른 파일/커밋은 보존하며 DB·키·개인 백업·로그·캡처·APK는 게시하지 않습니다. 최종 커밋ID는 완료 응답과 [이 문서의 커밋 기록](https://github.com/kyc66533-art/vehicle-app-workspace/commits/main/docs/CARITDA_VIRTUAL_DRIVING_AI_POC.md)에서 확인합니다. 자기SHA 삽입을 위한 반복 커밋은 만들지 않습니다.
