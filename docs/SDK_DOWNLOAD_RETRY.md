# Pleos Vehicle SDK 다운로드 재검증

검증일: 2026-11-07 (Asia/Seoul). **다운로드 실패, SDK 포함 빌드 실패, 실제 차량값 수신 미수행.**

## 이번에 실제 수행한 변경과 실행

- 기존 `NextDrive-Pleos/settings.gradle.kts`의 공식 Maven URL과 `ai.pleos.playground` 그룹 필터를 확인했습니다. 공식 시작 가이드와 일치하여 변경하지 않았습니다.
- 사용자 PC의 `NextDrive-Pleos/app/build.gradle.kts`에 `implementation("ai.pleos.playground:Vehicle:2.3.1")`을 추가했습니다. 기존 Android/Kotlin/SDK 설정은 유지했습니다.
- 공식 시작 가이드와 샘플의 실제 Gradle 버전 파일에서 2.3.1을 재확인했습니다. README의 2.3.2 표기만으로 버전을 변경하지 않았습니다.
- 기존 JBR 21.0.8, Gradle 8.11.1, SDK Platform 34, Build-Tools 35.0.0과 기존 캐시를 사용하여 온라인 `:app:assembleDebug`를 실행했습니다. Kotlin in-process와 5초 HTTP 타임아웃을 사용했습니다. 별도 init probe가 아니라 실제 앱 debugRuntimeClasspath 의존성 해결입니다.
- 결과: `BUILD FAILED in 41s`, Gradle exit 1. `:app:checkDebugAarMetadata`에서 Vehicle 2.3.1 해석에 실패했습니다.
- SDK AAR은 프로젝트 내부 Gradle 캐시와 기존 사용자 캐시에서 확인되지 않았습니다. 기존 APK는 그대로 남아 있으며 새 APK 생성/설치/SDK 초기화/읽기/회귀 검사를 수행하지 않았습니다. 과거 24+5 검사 통과 기록은 과거 검증으로 보존하며 이번 SDK 포함 빌드 성공으로 확대하지 않습니다.
- 기존 앱/SQLite/AVD/Android Studio 설정을 삭제·초기화하지 않았습니다. SDK 다운로드와 빌드가 선행 조건이므로 서비스·Capability·실수신 후속 작업은 진행하지 않았습니다.

## 정확한 실패 단계와 원인 구분

```text
Execution failed for task ':app:checkDebugAarMetadata'.
Could not resolve all files for configuration ':app:debugRuntimeClasspath'.
Could not resolve ai.pleos.playground:Vehicle:2.3.1.
Could not GET 'https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.1/Vehicle-2.3.1.pom'.
Got socket exception during request. It might be caused by SSL misconfiguration
Permission denied: getsockopt
```

Google Maven과 Maven Central도 동일한 소켓 오류였습니다. 로그의 SSL 문구는 Gradle의 일반 안내이며 TLS 인증서 문제로 확정하지 않습니다.

| 분류 | 이번 증거와 판정 |
| --- | --- |
| 네트워크 제한 | 현재 Codex 실행 정책은 network restricted. DNS는 정상 해석되지만 HTTPS 소켓 단계에서 Permission denied. 실행 환경 네트워크 제한과 일치하며 가장 유력한 차단 원인 |
| 저장소 인증 | HTTP 401/403 응답을 받지 못했으므로 인증 필요 여부 판정 불가. 인증 파일·비밀번호를 추가하지 않음 |
| SDK 버전 | HTTP 404 또는 Maven 메타데이터를 받지 못해 존재 여부·이미지 호환성 미검증. 공식 2.3.1 좌표 자체는 확인 |
| Gradle 설정 | 프로젝트 설정 해석과 실제 공식 URL 요청까지 진행. DSL 오류나 저장소 누락이 이번 실패 원인은 아님. 다운로드 후 전이 의존성/메타데이터 호환성은 별도 확인 필요 |
| 기타 환경 | 현재 JBR/Gradle 실행은 성공. Windows 방화벽·보안 제품 등 호스트 원인의 완전 배제는 이 환경에서 불가. 보안 기능 변경/해제는 하지 않음 |

## 승인 경로의 제한

사용자는 공식 SDK 다운로드 재시도를 승인했지만, 현재 세션의 실행 정책에서 `sandbox_approval` 요청이 허용되지 않습니다. 따라서 범위를 좁힌 네트워크 권한 승인 창도 제가 열 수 없습니다. 사용자 승인만으로 이 도구 정책을 변경할 수 없으며, 승인 후 확대 권한 실행을 했다고 주장하지 않습니다. Sandbox 전체 해제·우회 프록시·방화벽/실행 정책 변경·다른 도구를 이용한 바이너리 반입을 시도하지 않았습니다.

필요한 범위는 공식 Maven 저장소 `nexus-playground.pleos.ai:443` 및 실제 전이 의존성이 요구하는 공식 저장소 접근입니다. 이 세션에서는 권한 확대가 불가능하므로 같은 명령을 반복하지 않습니다.

## 사용자가 남겨 둔 파일 수정 없이 할 수 있는 다음 단계

로컬 의존성은 이미 추가했으므로 Android Studio에서 파일을 편집할 필요가 없습니다.

1. 일반 Windows에서 기존 Android Studio와 `NextDrive-Pleos`를 엽니다.
2. Gradle Offline Mode가 켜져 있다면 이번 다운로드에 한해 해제하고 **File > Sync Project with Gradle Files**를 누릅니다.
3. 성공하면 External Libraries에서 `Vehicle:2.3.1`을 확인하고 **Build > Build App Bundle(s) / APK(s) > Build APK(s)**를 실행합니다. 메뉴 이름은 Android Studio 버전에 따라 조금 다를 수 있습니다.
4. 실패하면 Sync/Build 창의 첫 원인을 전달해 주세요. HTTP 401/403이면 공식 저장소 접근 승인/인증, 404이면 좌표, 소켓 오류이면 해당 실행 환경의 네트워크 접근을 각각 확인해야 합니다. 사용자 인증/보안 변경을 자동 수행하지 않습니다.

현재 로컬 빌드는 미다운로드 Vehicle 의존성 때문에 막혀 있습니다. 설치된 기존 앱과 APK/DB는 보존했습니다. 다운로드 성공 후 AAR의 실제 API/어노테이션을 확인해 어댑터를 연결해야 하며, Sync 성공만으로 현 앱이 차량값을 읽지는 않습니다.

미검증 Gradle 변경은 프로젝트 게시 규칙에 따라 GitHub에서 제외하고 로컬에만 보존합니다. 따라서 다른 PC에서 원격 저장소만 받아도 SDK 의존성이 추가된 상태가 되지는 않습니다. 결과 문서만 게시합니다.

## Aside 조사 반영 범위

작업 시작 main `a5c738aed91b43d98d4234a3d9718c0affe9c7d8`과 `docs/research/CARITDA_UI_UX_RESEARCH.md`를 읽었습니다. 홈/문제 분리, 이름 등록, 후보 선택과 명시적 운전 시작 구분, stable ID/당시 이름 snapshot, 초안 원 작성자 보존, 비파괴 A/B 이전, 플랫폼 UXR 우선 원칙을 향후 별도 UI 구현 기준으로 삼습니다. 이 문서의 인수 기준은 아직 미실행이며 이번 SDK 작업에서 UI나 연구 파일을 수정하지 않았습니다.

## 근거

- [공식 시작 가이드](https://document.pleos.ai/docs/pleos-only/getting-started)
- [공식 샘플 실제 버전 설정](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/main/gradle/libs.versions.toml)
- [Aside 조사 고정 커밋](https://github.com/kyc66533-art/vehicle-app-workspace/blob/a5c738aed91b43d98d4234a3d9718c0affe9c7d8/docs/research/CARITDA_UI_UX_RESEARCH.md)

로컬 원본 로그: `NextDrive-Pleos/app/build/validation/sdk-retry-build.txt` (게시 제외).
