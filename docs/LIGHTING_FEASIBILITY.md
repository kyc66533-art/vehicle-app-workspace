# 등화장치 후보 기술 검토

2026-11-10. 연구 자료는 원격 main의 ASIDE_RESEARCH 전체를 읽었으며 로컬에 없는 연구 파일을 생성/수정하지 않았습니다. 이 문서는 조사·계획이며 새 권한/신호 연결/차량 제어를 구현한 결과가 아닙니다.

| 대상 | 공개 근거 | 현재 판정 |
|---|---|---|
| Light | 로컬 SDK2.3.2 Vehicle.jar에 getCurrentLowBeamLightState/getCurrentHighBeamLightState/getCurrentTailLampLightState 및 Switch/Capability API 확인 | API 존재. CAR_LIGHTS 추가 승인·실제 Capability/수신 검증 필요. ON/OFF는 전구 건전성 증거가 아님 |
| TurnSignal | getCurrentTurnSignalState/checkTurnSignalStateCapabilities/registerTurnSignalState | CAR_LIGHTS. 방향지시 상태 조회 후보, 전구 단선 진단으로 쓰지 않음 |
| Brake | checkBrakePedalStatusCapabilities/getCurrentBrakePedalStatus/registerBrakePedalStatus | CAR_DRIVE. 페달 작동 정보와 등화장치 점등·광량·고장은 별개 |
| Safety | 공개 Safety 경고 도메인 및 로컬 SDK의 seat/washer/power-down 경고 메서드 확인 | CAR_SAFETY. 이번 확인 범위에서 전구/LED 단선 경고 전용 SDK API를 확보하지 못함. 일반 앱 접근 불가로 확정한 것은 아님 |
| Virtual Console | Exterior Lights Mode/High Beam/Stop Lights/Hazard/Turn Signal, Brake Pedal, Telltales 공식 목록 | 시뮬레이션 가능. Console의 actual state는 에뮬레이터 모델 상태이며 물리 전구 정상 판정 아님. 현재AVD 직접 조작/SDK 수신은 미실행 |
| Vehicle Data API | frontHeadLamp/rearLeftStopLamp/rearRightStopLamp 및 방향지시등 고장 항목 | 차량 내부 SDK와 다른 OAuth/텔레매틱스 경로. 공식 제공지역 유럽 제한. 한국 공모전에서 사용 가능하다고 가정하지 않음 |

로컬 javap는 일부 클래스 처리 뒤 AccessDenied를 보고했습니다. 확인된 메서드의 존재만 기록하며 전체 클래스의 모든 API가 없다고 결론내리지 않습니다. 현재 Manifest에는 CAR_LIGHTS/CAR_SAFETY를 추가하지 않았고, 권한·Capability·callback/실수신 성공을 주장하지 않습니다.

## 다음 승인 후 최소 실험

validation 전용 읽기 프로브에서 CAR_LIGHTS만 최소 선언 → 공개 Capability → 지원되는 LowBeam/TurnSignal 읽기 → Console 변화 → 실제 callback과 값의 출처/시간 저장을 각각 확인합니다. 수신 거부/미지원/UNKNOWN을 0 또는 정상으로 바꾸지 않습니다. 제어 API는 호출하지 않습니다. Safety 추가 권한은 별도 판단합니다.

실제 ECU의 등화장치 고장 신호가 확인되면 영역·고장코드·환경·관측 UUID 기반 사건 하나를 만들고 반복은 갱신합니다. 신호 해제는 관측 이벤트이며 수리 완료/사용자 문제 RESOLVED로 바꾸지 않습니다. 사용자가 정차 후 점검/조치를 남기고 다음 운전자에게 인계합니다. AI 입력은 새로운 자동 신호 계약/동의가 필요하며 현재 USER_SELECTED_RECORD를 ECU 출처처럼 재사용하지 않습니다.

고장 신호를 확보하지 못하면 기존 문제 기록·조치·인계를 이용한 **정차 중 외관 점검 체크리스트**가 대안입니다. 실제 브레이크등/방향지시등을 안전하게 다른 사람이 확인한 사실을 수동으로 기록합니다. 위험한 주행 재현을 요구하지 않습니다. 새 체크리스트 UI는 이번에 구현하지 않았습니다.

## 공식 근거

- [SDK 도메인](https://document.pleos.ai/api-reference/connect-sdk-pleos/Vehicle/intro), [권한](https://document.pleos.ai/api-reference/connect-sdk-pleos/Vehicle/vehicle-permission): Light/TurnSignal=CAR_LIGHTS, Brake=CAR_DRIVE, Safety/Tire=CAR_SAFETY.
- [공식 Console](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide): 등화/페달/경고 시뮬레이션 목록.
- [Vehicle Data API 고장 항목](https://document.pleos.ai/en/api-reference/vehicle-data-api/vehicle-data-api/common/get-vehicle-status), [지역·인증](https://document.pleos.ai/en/api-reference/vehicle-data-api/intro): 2026년8월 기준 유럽 한정 안내. 최신 SDK와 설치된2.3.2의 지원 범위는 동일하다고 가정하지 않습니다.
