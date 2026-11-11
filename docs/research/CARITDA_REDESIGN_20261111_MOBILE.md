# CARITDA 모바일 기술 검토와 보존 계약

2026-11-11 · 코드 기준 main 80f76b1bdac9044f649eba01e727ef4cee084b48. 읽기 전용 감사. 이번 작업에서 빌드/설치/기능 실행/모델 호출은 하지 않았다.

## 결론

**차량 창의 반응형은 기존 기반이 있다. 현재 APK의 스마트폰 정상 실행은 입증되지 않았고, Manifest 한 줄 변경으로 해결되지 않는다.** 별도 모바일 앱은 기술적으로 가능하지만 독립 범위·안전 정책·SDK 분리·실기 검증이 필요하다. PortableAiActivity는 모바일 앱이 아니라 자동차 앱 내부 AI 화면이다.

## A/B/C는 다른 작업이다

| 구분 | 현재 | 1차 제출 범위 | 후속 작업 |
|---|---|---|---|
| A Pleos 앱 창 적응형 | Android View/가로세로/작은폭/큰글자 기반 있음 | 차량 앱 창 내 레이아웃 개선·OS 영역/IME/제한 회귀 | 실제 분할창·높이 변화·rotary·실차 확인 |
| B Android 폰 독립 설치 | automotive required와 SDK 직접결합, 비차량 UNKNOWN | 시각 시안·기술 검토만. 필수 제출 기능으로 확장 안 함 | 별도 :mobile 모듈 또는 검증된 플랫폼 flavor, 독립 appId·인증 등록·설치 검사 |
| C Android Auto 투사 | 해당 service/카테고리/호스트 계약 선언 없음 | 구현 안 함 | 허용 카테고리 적합성, Car App Library/호스트 템플릿·별도 심사 검토. 차량 APK UI를 그대로 투사할 수 없음 |

## 1. 실제 프로젝트 증거

경로 접두사 K = NextDrive-Pleos/app/src/main/java/com/nextdrive/pleos/.

| 파일:행 | 확인 사실 | 의미 |
|---|---|---|
| app/src/main/AndroidManifest.xml:15 | android.hardware.type.automotive required=true | 일반 폰 배포 지원을 주장할 수 없음. adb 설치 허용 여부와 정상 작동은 별도 |
| Manifest:23-24,35 | 터치/마이크 optional, android.car library required=false | 라이브러리 선택 선언만으로 직접 android.car 참조가 안전해지지 않음 |
| Manifest:3-22 | Pleos 차량/문/등화/안전/STT/TTS/위치/Navi 권한과 서비스 query | query는 서비스 설치·권한 보장이 아님 |
| Manifest:36-55 | DO 미선언, Main/Portable resizable+adjustResize, Action exported | 주행 중 앱 허용/Google Play 적합성이 아님. Action 호출자·수신자 검증 유지 |
| app/build.gradle.kts:28-39,43-68 | appId com.nextdrive.pleos, min28/target34, android.car, validation buildType | 제품 flavor 없음. validation은 모바일 빌드가 아님 |
| app/build.gradle.kts:72-88 | FirebaseAuth24.2.0, Vehicle2.3.2, Navi3.0.5, Fused1.1.3, STT/TTS3.3.1, compileOnly vehicle-data1.1.11 | 현재 모두 production에서 직접 의존. 플랫폼 분리 전 SDK 호출/클래스 로드 위험 |
| settings.gradle.kts:15-20 | ai.pleos.playground/security 그룹 필터 | 음성 transitive 의존성을 잃지 않게 보존 |
| K/MainActivity.kt:198-235 | PleosReadClient/Observer/CarSafetyGate 직접 조립 | VehicleDataSource 인터페이스가 있어도 앱 composition은 아직 분리 아님 |
| K/safety/CarSafetyGate.kt:23-38,53-56 | 비automotive/API29미만 UNKNOWN, 실제 UXR baseline+DO불필요시에만 ALLOWED | required=false로만 바꾸면 폰 입력은 계속 차단. 자동차 UNKNOWN을 ALLOWED로 바꾸면 안 됨 |
| K/vehicle/PleosReadClient.kt:32-40,69-96,117-159 | 실제 Vehicle.initialize, 권한/capability/값/오류 처리 | 외부 caller gate에 의존, 독립 phone capability gate는 없음 |
| K/integration/SpeechInput.kt:44-62, SpeechBackend.kt:24-49 | Pleos ON_DEVICE, Android fallback은 API31+/가용성 필요 | 폰 음성 성공 보장 아님 |
| K/PortableAiActivity.kt:78-81,157-217,239-262 | 자체 safety gate, visible+ALLOWED, 제한 시 취소 | 'Portable' 이름은 휴대폰 지원 근거가 아님 |
| K/ui/CaritdaDesign.kt:12-40,92-103,116-137 | 테마/76dp 터치/큰글자 heading stack/버튼 줄바꿈 | 재사용 가능한 native UI 기반 |
| K/MainActivity.kt:202,369-387,517,944-948 | 폭 변화 redraw, 탐색 폭800/높이360, 본문600, 분류1000/480 분기 | 높이만 변화한 경우 재평가와 IME 테스트 필요 |

현재 minSdk28이지만 safety gate는 API29 미만에서 UNKNOWN이다. API28 설치 가능 선언을 전체 기능 지원이라고 표현하지 않는다. source Manifest 감사이며 새 merged manifest/APK 검사는 하지 않았다.

## 2. 권장 분리 순서

### 첫 단계: 현재 :app 유지 + 후속 :mobile 모듈

공모전 차량 앱의 appId/서명/업데이트 경로/Gradle task·검증 runner를 그대로 두는 것이 우선이다. 모바일 때문에 기존 APK를 호환성 실험판으로 바꾸지 않는다.

공유 후보: IssueModels/Repository 계약, SQLite 비파괴 이전, 도메인 기록·운전자 소유권, payload 검증·요청 상태 의미, 읽기 전용 presentation model와 일부 native 컴포넌트.

플랫폼 주입 경계:
- EditingPolicy: 차량은 기존 fail-closed CarSafetyGate. 폰은 별도 명시 정책이며 '차량 정차 확인됨'이라고 표시하지 않음. 폰으로 차량 주행 여부를 추정하여 운전 사용을 권장하지 않음.
- VehicleDataSource/Observer: 차량은 실제 Pleos 구현, 폰은 '차량 연결 없음' 반환. 샘플 수치를 실제 건강값으로 생성하지 않음.
- Speech/Location/Navigation: 차량 SDK와 폰 OS 구현 분리. 없는 기능은 감추거나 이유를 표시하고 안전한 대체 제공.
- Gleo: 차량만, 기존 schema/action/trust contract 유지. 폰 exported Activity에 무비판적으로 복제하지 않음.
- 링크 오류를 잡으려는 광범위 try/catch보다 모바일 binary에 자동차 타입/초기화를 포함하지 않는 분리를 우선.

### 후속 통합 시: automotive/mobile flavor

platform dimension=automotive/mobile, validation은 독립 buildType. automotiveImplementation에 Pleos dependency, src/automotive에 Manifest/android.car/permission/구현; src/mobile에는 자동차 요구·권한·Gleo 제외.

기존 com.nextdrive.pleos와 signer는 자동차에 고정. 폰은 별도 applicationId/Firebase Android app 등록/인증 설정. 실제 사용자의 승인된 계정·기기 등록 범위를 확인한다. 기존 운영 인증 파일을 복사하거나 키를 추출하지 않는다.

flavor를 도입하면 assembleValidation 등 task 이름과 계측 target이 바뀔 수 있으므로 CI/문서/운영자의 기존 명령 호환 계획을 먼저 세운다. 정확한 com.nextdrive.pleos.validation guard를 '.validation으로 끝남' 같은 느슨한 규칙으로 바꾸지 않는다.

## 3. 창 크기·방향·글자 배율

- 기기 물리 해상도가 아니라 앱이 현재 얻은 가용 dp 폭/높이와 fontScale, IME/systemBars/cutout 인셋으로 결정.
- 차량도 portrait/분할/높이 축소를 지원하고, 폰도 landscape/다중창에서 재배치. 600/840dp는 자체 layout 시작점이지 OS 구분용 조건이 아님.
- fontScale1.5 이상은 한 열 우선, 2.0에서 제목·버튼·입력·오류·저장 모두 도달 가능. 가로 스크롤·글자 축소로 맞추지 않음.
- 기존 width listener 외에 height/IME/insets 변화 재평가 필요. 새 수신·observer 구독·SDK 조회는 layout redraw에서 실행하지 않음.
- View 유지가 우선. UI 개편과 Compose 전환·SDK 업그레이드·DB 이전을 동시에 하지 않음.

## 4. SQLite와 기기 간 공유

K/data/SqliteIssueRepository.kt:55-68,104-181은 caritda_v01.db v3의 로컬 authoritative 기록이다. drivers/issues/events/settings/drafts, transaction/revision·작성자 검사·논리 삭제를 유지한다.

별도 저장소: ObservationStore v2 / SnapshotStore v1 / LowSocStore v1 / AnalysisStore v1. Snapshot scope는 package와 로컬 observation store identity 기반이며 전역 차량 ID가 아니다. 둘 이상의 DB commit이 원자적이라고 주장하지 않는다.

**같은 Firebase 계정으로 로그인해도 차의 기록이 폰에 나타나지 않는다.**
- K/ai/auth/FirebaseSessionProvider.kt:12-83은 로그인·토큰 refresh·등록을 담당.
- infra/gemini-relay/src/firebase-token.ts 및 portable-worker/gate는 AI 중계의 토큰·기기·동의·nonce·budget/operation 정책.
- Firestore/Realtime Database 기반 기록 CRUD·멤버십·동기화 구현은 확인되지 않음. AI operation ledger를 가족 차량 DB로 쓰지 않음.
- 로컬 driver ID는 Firebase UID가 아니고, 같은 이름은 같은 사람의 증거가 아님.
- AES-GCM/Android Keystore 기반 AI cache는 계정·기기 sidecar. backup_rules/data_extraction_rules의 제외 정책을 우회해 세션·키·통째 DB를 복사하지 않음.

### 원격 공유를 하려면 별도 개발할 것

1. 서버의 차량/가족 멤버십·초대/탈퇴·역할 권한·차량 범위 접근제어.
2. 영구 기록/이벤트 ID와 로컬 profile↔계정 매핑. 작성 당시 이름 snapshot 보존.
3. offline outbox·idempotency·재시도·시간 역순/장치시계 대응, revision 충돌과 사용자 선택.
4. 읽음과 점검 상태의 기기 간 이벤트 의미, 논리 삭제 tombstone·복구/보존기간·삭제 전파 규칙.
5. 최소 수집·전송 사본/암호화·TLS·서버 로그 정책·동의/철회·유출 대응·접근 이력.
6. 현재 기록/위치/관측치의 공유 범위를 각각 선택. 차량 원관측을 기본으로 외부 전송하지 않음.
7. 계정 분리·기기 분실/해제·멤버 탈퇴 후 권한 회수, 서버·모델 비용과 운영 책임.

이것은 UI만으로 끝나지 않는다. 1차 모바일은 원격 공유 없이 독립 로컬 기록부터도 가능하지만, 차의 기록과 연결됐다고 소개해서는 안 된다.

## 5. 기존 기능·DB·인증·안전 보존

| 영역 | 변경 금지/회귀 조건 |
|---|---|
| 기록 | issue/driver 영구 ID, 작성 당시 이름, 발견시각, revision, 4수동상태, 조치/재발, 인계, 논리삭제, 원문/초안 모두 보존 |
| 차량 | Vehicle2.3.2 공개API, capability/권한/미지원/실패·최근성, 진입당 단발 조회, 명시적 켠 SOC의 가시/ALLOWED observer만. 종료 상시감시 없음 |
| AI | IssueAnalysisService·PortableSession·기존 Firebase/기기등록/동의·할당량·BUDGET·pending frozen/UNKNOWN·계정별 encrypted cache·source/revision 보존 |
| 전송 | 증상 category/situation만. 이름/ID/메모/시각/위치/차량값을 UI 개편으로 payload에 추가하지 않음. SOC 분석 모드의 별도 계약과 동의도 혼합하지 않음 |
| 음성 | STT final+ended 자동종료/취소 generation, TTS 명시재생·중단·이탈보호. Gleo 승인은 별도 |
| 제한 | 모든 클릭 시 현재 UXR 재검사. UNKNOWN/RESTRICTED를 ALLOWED로 바꾸지 않음. exported Action caller·callback 검증 유지 |
| 개발 안전 | 운영 UI 자동화·DB 초기화 금지. validation도 기존 데이터 보존하고 고유 fixture 조기분기만 사용 |

중요: MvpScenarioInstrumentation의 인자 없는 기본 prepare는 validation DB를 지우는 경로가 있다. designOnly 분기도 기본 prepare 뒤에 있으므로 안전한 독립 시험이라고 단정하지 않는다. 최신 runner를 읽고 audited early-return branch만 사용한다. 과거 298/313 등의 수치는 기존 문서의 당시 결과이며 이번 실행 결과가 아니다.

## 6. 실제 모바일 완성이라고 말하기 전 필요한 증거

별도 폰 APK 생성+merged manifest/클래스/설치 검사 → 자동차 SDK 없는 실제 폰 콜드 실행 → 로컬 저장/재시작/업데이트/초안/소유권 → 폰 전용 인증/기기등록/동의/usage/UNKNOWN 복구 → 회전/프로세스 종료/IME/다중창/큰글자/접근성 → 자동차 APK 동일 기능/데이터 회귀.

앱 스토어 배포·Android Auto 카테고리 승인·원격 동기화는 위 단계와도 별개의 완료 조건이다.
