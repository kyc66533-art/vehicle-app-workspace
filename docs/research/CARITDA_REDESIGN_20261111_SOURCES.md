# CARITDA 재설계: 공식 기준과 비교 근거

- 조사일: 2026-11-11, Asia/Seoul.
- 코드 기준: main [80f76b1bdac9044f649eba01e727ef4cee084b48](https://github.com/kyc66533-art/vehicle-app-workspace/commit/80f76b1bdac9044f649eba01e727ef4cee084b48).
- 이번 작업은 문서와 독립 HTML만 제작했다. APK·실차·사용자 만족도 검증이 아니다.

## 1. 직접 열람한 공식 자료

| ID | 자료/URL | 확인한 내용 | 적용 범위와 주의 |
|---|---|---|---|
| P1 | [Pleos UI Introduction](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 일관성, 한손 조작, 짧게 파악할 정보, 자동 스크롤·움직이는 이미지 억제. 대비 4.5:1 이상. 중요한 텍스트 높이 5mm/보조 3mm. 0.25초 초과 응답은 진행 상태 제공 | 공식 설계 가이드의 요구 표현과 원칙. 1~2초 인지는 목표이지 검증 결과가 아니다. 영문 120자/24단어·3줄 기준을 한국어 120자로 잘못 변환하지 않는다 |
| P2 | [Pleos Playground](https://developers.pleos.ai/playground) | Vehicle/NaviHelper/TTS 및 Gleo Interface 문서·설계·배포 자료의 포털 | SDK 소개는 실제 권한·실차 지원 보장이 아님 |
| P3 | [현대차 공모전 개발 안내](https://appdevchallenge.auto-hmg.io/guide/development) | API28 이상, Gradle8 이상, Kotlin/Java, Vehicle·FusedLocation·NaviHelper·STT/TTS 안내 | 현재 최신 SDK 버전은 개별 문서·프로젝트로 판정. 예시 시나리오는 주제 제한이 아님 |
| P4 | [Pleos 화면 레이아웃](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout) | Status bar/Driving view/GNB/앱 창 구분, 1~2 앱 창, 가로·세로 적응 필요 | OS 공조·알림 패널·키보드는 앱보다 상위. 앱에서 OS 외형이나 영역을 재구현하지 않음 |
| P5 | [Pleos 주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) | CarUxRestrictions에 따른 UI/동작 제한, 오디오 포커스, 큰 터치 영역, 단순 동작 | 안전 제한은 필수 보존. 음성이 있다고 상세 조작을 허용하는 것은 아님 |
| P6 | [Pleos 주행 제한 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy), [앱 카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category) | DO 미선언 앱은 주행 중 사용 불가, 카테고리·국가·OEM 정책별 판단 | 차량 관리가 제한적 허용 예시여도 CARITDA 승인 보장은 아님 |
| P7 | [Pleos Typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography), [Basic Button](https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/basic-button) | 글꼴 가독성 권장, 버튼 Pressed/Disabled/Loading 상태 | 보편적인 최소 버튼 dp는 본문에서 확정하지 못함. 12sp 권장을 차량 가독성 충족 기준으로 삼지 않음 |
| P8 | [Pleos 배포 안내](https://document.pleos.ai/docs/pleos-only/distribute/app-submission-guide) | App Market 심사, 플랫폼·안전·규제 확인 | Google Play 심사와 별개 |
| A1 | [AAOS 공식 개요](https://developer.android.com/training/cars/platforms/automotive-os) | 앱 내부 뒤로 이동 필요, 큰 시스템바·cutout, 직접 UX restriction 조회, automotive feature, hasSystemFeature | 기어 P나 속도로 restriction을 역추론하지 않음. Google Play에서는 임의 Activity DO 선언을 금지하는 별도 배포 규칙이 있음 |
| A2 | [차량 적응형 앱](https://developer.android.com/develop/adaptive-apps/guides/cars/build-adaptive-apps-for-cars) | 실제 창 크기 분류·다중 pane·car qualifier·오프라인·가로/세로 | 주로 주차 앱 적응 설명. Android Auto와 AAOS가 같은 런타임이라는 뜻이 아님 |
| A3 | [Car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality) | 배포 유형·카테고리·tier별 품질 요구 | Video/Games/Browsers Car differentiated의 64dp/24dp를 모든 Pleos 앱 의무라고 하지 않음 |

## 2. 비교 사례: 가져올 것과 가져오지 않을 것

| 실제 제품/자료 | 관찰 사실 | CARITDA 적용 | 적용하지 않을 요소 |
|---|---|---|---|
| [현대 ccNC 미국 공식 매뉴얼](https://webmanual.hyundai.com/ccNC/AVNT/USA/English/Systemoverview_home.html) | 이전·홈·메뉴 역할 분리, 여러 홈 페이지, 아이콘 위치 변경, 주행 시 일부 기능 제한 | 이전은 작업 맥락, 홈은 앱 첫 화면이라는 의미를 분리. 물리/OS 뒤로 버튼 부재에 대비 | 완성차 전체 런처의 다중 앱 페이지·드래그 편집을 단일 서비스 안에 복제하지 않음. 매뉴얼의 P/주차브레이크 사례를 앱의 안전 gate로 직접 구현하지 않음 |
| [기아 STD_GEN5W 미국 매뉴얼](https://ownersmanual.kia.com/ivi/STD_GEN5W/AVNT/USA/English/001_Basic_4.html) | 터치/길게 누르기/드래그/스와이프/핀치 안내, 주행 중 터치 조작 경고 | 주차 후 입력·점검, 한 번 누르는 명시적 버튼 | 숨은 제스처·긴누르기를 핵심 기록 조작으로 사용하지 않음. 다른 세대·국가의 매뉴얼은 Pleos 규정이 아님 |
| [Samsung Easy Mode 공식 지원](https://www.samsung.com/ae/support/mobile-devices/using-easy-mode-on-my-samsung-phone) | 큰 아이콘·글자·간격, 단순 홈, 연락처 바로가기 | 핵심 작업 우선, 라벨과 아이콘 병기, 큰 글자에서도 한 열로 전환 | 스마트폰 스와이프 홈·돋보기·연락처 앱 구조를 차량에 이식하지 않음. 고령자 만족도 입증 사례라고 과장하지 않음 |
| [W3C Older Users](https://www.w3.org/WAI/older-users/) | 대비·색 지각·근접 시력, 미세 운동, 청력, 단기 기억/주의 변화와 접근성의 연관 | 확대·대비·명확한 이름·오류 복구·자막/텍스트와 음성 병행 | WCAG 웹 적합성이나 나이만으로 모든 운전자 요구가 같다고 단정하지 않음 |

## 3. CARITDA 자체 설계 목표, 공식 규격과 분리

- 차량 주요 터치 영역 **최소 76×76dp**(기존 컴포넌트 하한 보존), 중요 행동도 76dp 이상, 간격 16dp 목표. 자체 보수적 목표이며 Pleos 공통 의무 수치라고 주장하지 않는다. 모든 단위는 CSS px와 다르다.
- 차량 본문 24sp, 보조 20sp, 제목 32sp, 주요 값 40sp를 시작점으로 한다. 실제 표시거리·밀도·5mm/3mm 가독성 목표와 비교하여 조정한다. 스마트폰은 본문18sp/보조16sp, 48dp 이상 터치 목표.
- 대비는 기본·선택·경고·포커스·버튼 텍스트 등 실제 조합으로 검사. 4.5:1 이상을 전체 핵심 조합에 적용. 색 외에 단어·아이콘/테두리로 상태 표현.
- UI 반응은 즉시 pressed/loading, 장시간 요청에 명시적 진행·취소. 이것은 Gemini 0.25초 완료 요구가 아니다.
- 타이머/AI/관측치가 상태를 자동 정상·해결로 만들지 않는다. 전체 차량 건강 점수·거짓 초록 체크 금지.
- 화면 확대 시 열을 줄이고 아래로 읽게 한다. 고정 높이·본문 ellipsis·가로 스크롤로 핵심 버튼을 숨기지 않는다.
- AAOS의 TalkBack/Switch Access/Voice Access 가용성은 스마트폰과 다르다. contentDescription·포커스 순서 등은 준비하되, 실제 Pleos rotary/접근성 서비스는 기기별 확인해야 한다.

## 4. 검증 계획과 완료 주장 경계

가설: 중복 목적지를 줄이고 '지금 확인할 일 → 행동'으로 배치하면 첫 사용자의 경로 이해를 도울 수 있다. 아직 향상 수치나 만족도는 없다.

후속 연구는 초보·60대 이상을 포함한 8~12명 규모의 형성 평가를 권고한다. 정차 환경에서 (1) 이전 운전자 문제 확인 (2) 문제 저장 (3) AI 전송 내용 이해 (4) 기록 수정 (5) 다음 운전자 확인 (6) 저장 실패 복구를 관찰한다. 무도움 완료, 잘못 누름, 저장/AI 전송/읽음/점검완료의 구분, 도움 요청과 시간, 읽기 불편을 수집한다. 소규모 결과를 전체 만족도나 실차 안전 인증으로 일반화하지 않는다.

1차 제출 전 범위는 차량 화면과 기능 회귀가 우선이다. 모바일 설치·원격 가족 공유·Gleo 승인·실차 HMI/주행 승인은 별도 이정표다.
