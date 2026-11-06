# 공식 SDK 및 실제 지원 기능 점검표

현재 Vehicle SDK는 다운로드·설치하지 못했습니다. 아래 '공식 문서 확인'과 '실제 런타임 지원'은 다른 검증입니다. 현재 테스트 앱에는 공식 SDK 호출이나 차량 권한을 추가하지 않았습니다.

| 대상 | 공식 문서 확인 | 설치 / 컴파일 | 실제 Capability / 수신 | 현재 화면 |
| --- | --- | --- | --- | --- |
| Vehicle SDK | 시작 가이드의 `ai.pleos.playground:Vehicle:2.3.1`, 공식 Maven 저장소 확인 | POM 접근 실패. AAR·시그니처·컴파일 미확인 | 서비스 연결 미확인 | SDK 미연결 |
| 앞 왼쪽 도어 | 시작 가이드의 `getDoor()` / `getCurrentDoorPosition()`, Capability 가이드의 `checkDoorPositionCapabilities()` 확인. 후보 조회 권한 `CAR_DOORS` | 미검증 | 읽기 접근 수준, 앞 왼쪽 영역 지원, 값·단위·상수 모두 미검증 | 지원 미확인 / 미수신 |
| 연료 잔량 | 대표 도메인 권한 매핑에서 Fuel → `CAR_ENERGY` 확인 | 구체 조회/Capability 메서드·단위·지원 차종 미검증 | 미검증 | 지원 미확인 / 미수신 |
| 누적 주행거리 | 대표 도메인 권한 매핑에서 Odometer → `CAR_DRIVE` 확인 | 구체 조회/Capability 메서드·단위 미검증 | 미검증 | 지원 미확인 / 미수신 |
| 주행 규제 | 공식 가이드의 `CarUxRestrictionsManager` / `CarUxRestrictions` 확인 | 연동 미구현 | 지역·카테고리별 규제 수신 미검증 | 정차 테스트 안내, DO 미선언 |

## 실제 검증 시 기록할 증거

1. 이미지/SDK 버전, 실행 대상이 Pleos Emulator인지 실차인지, 아티팩트 해석·빌드 결과.
2. API Reference와 설치된 AAR에서 확인한 정확한 패키지·메서드·콜백 시그니처 및 함수별 `@RequiresPermission`.
3. 앱 매니페스트의 최소 조회 권한, 실제 접근 허용/거부 결과. 문서상의 권한 이름 확인만으로 허용을 가정하지 않음.
4. 항목별 Capability의 읽기 접근 수준 및 지원 영역. `READ`/`READ_WRITE`이고 해당 영역이 지원될 때만 읽음. 제어/set API는 사용하지 않음.
5. 읽은 원본 값·단위·영역·수신 시각과 화면 변환 규칙. 예를 들어 리터를 미확인 탱크 용량으로 퍼센트 변환하지 않음.
6. 미지원/권한 거부/연결 실패/타임아웃/미수신/오래된 값/정상 값을 구분한 실제 결과.
7. 생명주기 전환 시 초기화·연결·자원 해제, 늦게 도착한 콜백 처리, 화면 크기 변경 후 복원.

차량 값 수신 전에는 '경고 없음', '도어 닫힘', 연료/주행거리 숫자를 임의로 표시하지 않습니다. Pleos 에뮬레이터 수신 성공을 실차 검증으로 표현하지 않습니다.

## 공식 근거

- [시작 가이드 / Vehicle SDK 의존성과 도어 조회](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Capability 확인](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)
- [Vehicle SDK 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [Pleos Emulator 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
