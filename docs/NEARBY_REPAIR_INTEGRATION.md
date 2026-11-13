# CARITDA 주변 정비소 자동 검색 — 구현 기록 (2026-11-13)

## 목표
정비 상담 화면의 기본 경로를 '현재 위치로 주변 정비소 찾기'로 바꾼다. 운전자는 좌표를 입력하지 않는다. Pleos FusedLocation 마지막 위치 → 명시적 동의·신선도 확인 → Firebase 인증 Cloudflare Worker /v2/repair/nearby → 카카오 Local 키워드 검색 → 최대 3곳의 주소·전화·직선거리 → 사용자가 고른 목적지에 기존 NaviHelper를 전달한다.

현재 판정: 소스 구현·별도 브랜치 게시. 새 Worker 배포·카카오 Local 키 발급 및 설정·Android 빌드·실사용 검증은 하지 않았다. 실제 정비소 검색이 성공했다고 보고하면 안 된다.

## 기존 기능 재사용
- MainActivity.kt: 정비 상담 화면의 새 검색 버튼, 상태, 결과 카드, 목적지 선택.
- integration/NearbyRepairClient.kt: Firebase ID 토큰을 사용하는 HTTPS 요청, 응답 검증, 오류 처리. 로컬 데이터베이스를 변경하지 않는다.
- infra/gemini-relay/src/nearby-repair.ts: 서버에만 보관할 카카오 REST 키, 반경 20km 거리순 검색, 블루핸즈 후보 우선 표시 및 중복 제거.
- infra/gemini-relay/src/portable-worker.ts: 기존 Firebase 인증 뒤 추가 라우트 처리. Gemini 호출이나 AI 사용량 장부는 건드리지 않는다.
- infra/gemini-relay/test/nearby-repair.test.mjs: 공급자 모의 응답만 쓰는 오프라인 테스트. 아직 실제 실행되지 않았으므로 PASS로 계산하지 않는다.

## 프라이버시와 안전
- 검색할 때마다 사용자가 위치·외부 서비스 전송에 동의한다. 정확한 위치는 CARITDA Worker로 보내고, 카카오 전송 전에 좌표를 소수점 3자리로 줄인다. 증상, 운전자 이름, 기존 DB는 보내지 않는다.
- 마지막 위치가 10분 이상 오래되거나 시각 이상이면 검색을 중지한다. 마지막 조회값은 현재 실제 위치를 보장하지 않는다.
- 화면 거리는 직선거리다. 실제 이동거리·영업시간·예약 가능 여부·공식 블루핸즈 인증 여부는 확인하지 않는다.
- 경로 요청은 사용자가 업체를 선택하고 확인한 뒤 실행한다. 기존 경로 보호, CarSafetyGate, SQLite·AI·음성 설정은 유지한다. 자동 예약·견인 호출 없음.
- 종전의 수동 좌표 입력은 고급 대체 경로와 기존 회귀 테스트를 위해 남겨 둔다.

## 활성화 전 필수 조건
1. 카카오 디벨로퍼스의 Local API 사용 가능 여부, 쿼터, 비용을 확인하고 사용자 계정에서 키를 발급한다. 새 서비스 가입이나 결제는 임의 진행하지 않는다.
2. Cloudflare Worker secret KAKAO_LOCAL_REST_KEY를 서버에만 등록한다. Android APK, GitHub, 문서에 키를 올리지 않는다.
3. 별도 호출 제한·비용 통제를 확인한 뒤 PLACES_ENABLED=true 설정 및 Worker 배포를 사용자 승인 후 진행한다.
4. 기존 Gemini Durable Object BUDGET, Firebase 인증, 암호화 캐시, AI 과금 원장은 절대 초기화·삭제·재생성하지 않는다.
5. Node 22 이상 환경에서 아래 오프라인 검사, npm run check, Worker dry-run을 수행한다. Android Studio에서 기존 빌드와 driveCheckNavigationUiOnly/인계/음성 회귀 검사를 실행한다.
6. Pleos 실제 위치→카카오 검색 결과→NaviHelper 경로 상태를 개별 검증한다. 기존 일반 APK를 임의 교체하지 않는다.

오프라인 검사 명령:
    cd infra/gemini-relay
    node --experimental-strip-types test/nearby-repair.test.mjs
    npm run check

## 사용자 경로
기록 → 기록 상세 → 점검·상태·상담 → 정비 상담 준비 → 현재 위치로 주변 정비소 찾기 → 동의 → 결과 카드 → 이곳으로 길찾기.

## GitHub 협업
기준은 음성 수정 PR #2의 커밋 a4e0cf5이다. 이 작업은 별도 feature 브랜치에만 게시하고 기존 main과 PR #2를 변경·병합하지 않는다.

공식 근거:
https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword
https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations
https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
