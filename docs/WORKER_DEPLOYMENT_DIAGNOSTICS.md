# Cloudflare Worker 편집기 진단 14개와 배포 호환성

2026-11-10, Asia/Seoul. 시작 main은 사용자가 지정한 `afd4678ba7cb2ad11937d79fc65069d08270d2da`와 같았습니다. 이 작업은 Worker 진단·최소 수정·검사만 수행했습니다. **실제 Cloudflare 배포, 원격 미리보기, Gemini 호출, Secret 조회·변경, 실제 DO/사용량 변경은 하지 않았습니다.** Android 앱/DB/AVD/SDK도 변경하지 않았습니다.

## 판정

사용자가 본 것은 Cloudflare Edit code의 `index.js`에 대한 **TypeScript 정적 진단**입니다. 기존 번들의 JavaScript 구문 실패나 Worker 실행 실패를 나타내는 로그가 아닙니다. 기존 번들과 수정본 모두 실제 로컬 `workerd`에서 모듈 로드·RSA JWT 검증·예외 생성·SQLite DO·P-256 서명을 통과했습니다. 진단 14개만을 이유로 ‘실행 불가’라고 할 근거는 없습니다.

다만 불필요한 Node식 `duplex` 옵션2곳은 정리했습니다. 수정 원본의 TypeScript 검사, 새 번들과 Wrangler 산출물 각각의 보안/실행 검사가 통과했습니다. 사용자는 **일반 PowerShell에서 최종 원본 번들 생성과 Wrangler dry-run 성공**을 확인했습니다. 따라서 **로컬 검사 기준 배포 준비가 된 상태**입니다. 실제 Cloudflare의 업로드 수락·원격 바인딩·Google 공개 인증서 통신·실제 모델은 이번에 시험하지 않았습니다.

## 사용자 제공 14개 진단의 원인

행 번호는 구형 `portable-worker.mjs`를 `index.js`에 붙여넣은 당시 기준입니다. 사용자 목록은13항목이며767행의 두 진단을 따로 세면14개입니다. 새 번들은 행 번호가 달라집니다.

| 구형 행 | 진단 | 원인·실행 판정 | 조치/근거 |
|---|---|---|---|
|82,323,723 (3개)|TS2345, `{fatal:true}`에 ignoreBOM 없음|편집기의 생성자 옵션 타입이 실제 Web API 기본값과 다르게 요구됨. ignoreBOM은 기본false이며 반드시 전달해야 하는 런타임 인수가 아님|workerd에서 같은 옵션으로 한국어·잘못된 UTF8 거부·BOM 기본 동작 확인. jose 원본을 수정하지 않음|
|432,1511 (2개)|TS2353, duplex|Response body를 다시 Request로 감싸면서 Node 스트리밍용 옵션을 넣었음. 기존 workerd는 실행했지만 Cloudflare/표준 RequestInit 타입에는 불필요한 속성|`bodyText`가 Request/Response 공통 headers/body만 받도록 변경하고 Response를 직접 전달. 두 옵션·강제 RequestInit cast 제거|
|749,750 (2개)|TS2339, Uint8Array.fromBase64|편집기 표준 라이브러리 선언이 새 API를 모름. jose는 존재 여부 검사 후 사용하며 없으면 atob 대체 경로 사용|workerd의 실제 RSA 검증을 해당 API가 없는 조건에서도 통과. polyfill·의존성 변경 없음|
|767,777,787 (3개)|TS2401, super 호출 위치|jose 배포 JS의 `super(...), this...` 쉼표 표현식과 native class field를 이전 JS 출력 대상으로 재타입검사할 때의 진단. ECMAScript 런타임의 파생 생성자 규칙 위반이 아님|로컬 ES2021 checkJs에서3개 재현, 실제 workerd에서 JWTExpired/JWTClaimValidationFailed 생성·거부 성공. 상위 호출/검증 순서를 변경하지 않음|
|767 (1개)|Error.captureStackTrace 타입|Node/V8 편의 API가 편집기 선언에 없음. 코드에는 `?.` 선택 호출이 있어 없어도 진행 가능|workerd에서 이 속성이 없는 조건의 만료 JWT 거부 성공|
|1364 두 속성,1365 (3개)|TS2339 protectedHeader/payload, TS2698 spread|타입 선언/tuple 정보를 지운 jose JS를 다시 추론하면서 내부 배열을 boolean/object union으로 오인. 정식 jose `.d.ts`의 jwtVerify 반환은 JWTVerifyResult|원본 TS는 정식 선언으로 검사 통과. 실제 workerd에서 RSA 성공 결과의 구조 확인·잘못된 서명/청구 거부. JWT 검증 우회나 결과 강제 변환을 넣지 않음|

정확한 Cloudflare 편집기 버전/선언 집합은 조회하지 않았습니다. 표준 TypeScript5.9.3/WebWorker 선언으로 구형 JS를 검사하면 ES2022에서9개, ES2021에서12개 진단이 나왔습니다. 이 프로필에는 Cloudflare에 보고되지 않은 WebCrypto BufferSource 선언 차이도 있어 **‘14개 모두 동일 환경에서 재현했다’고 표현하지 않습니다.** 코드 위치·의존성 원본·실제 실행을 함께 대조한 판정입니다.

## 실제 수정 범위

- `src/portable-common.ts`: reader 인수를 `Pick<Request,"headers"|"body">`로 명확히 하여 Response도 직접 받습니다. UTF8 fatal·stream size·Content-Length 검사는 유지합니다.
- `src/firebase-token.ts`, `src/portable-analysis.ts`: 인증서/모델 HTTP Response를 직접 reader에 전달합니다. 저장소·인증·quota·서명·모델·URL·timeout·출력 제한은 바꾸지 않았습니다.
- `test/bundle-portable.mjs`: **생성된 JS에만** 첫 줄 `// @ts-nocheck`와 generated 설명을 넣습니다. 타입이 지워진 제3자 코드의 이중 검사를 피하기 위한 표시입니다. 원본 `src/*.ts`는 계속 `tsc --noEmit`으로 검사합니다. 프로젝트 전체 검사나 보안 기능을 끄지 않았습니다.
- `test/editor-diagnostics.mjs`: 표시가 있는 상태와 표시를 제거한 ES2022/ES2021 checkJs 결과를 나란히 확인합니다. 새 JS는 표시 없이도 duplex2개가 제거돼 진단이9→7/12→10으로 줄고, 표시 적용 시0개입니다. **표시로0개가 된 사실 자체를 런타임 안전성 증명으로 쓰지 않습니다.**
- `test/workerd-compatibility.mjs`: Miniflare5.20261104.0-beta/설치된 workerd로16개 실제 로컬 실행 검사를 추가했습니다. 테스트용 export/JWT key resolver/로컬 인증 fixture는 메모리의 테스트 사본에만 붙이며 배포 파일에는 넣지 않습니다. 외부 fetch는 전부 거부하고 실제 외부 시도0을 검사합니다.

기존 `src/index.ts`, `portable-gate.ts`, 두 Wrangler 설정, package/lock/의존성 버전, BUDGET/class/migration/기존 객체 이름·원장 key는 이 작업에서 변경하지 않았습니다. Firebase/Gemini 인증을 완화하거나 전체 앱 기능을 재개발하지 않았습니다.

## Wrangler 종료 코드1의 실제 원인

Codex의 원본 dry-run과 사전 번들 `--no-bundle --dry-run` 모두 다음 esbuild 오류로 종료1이었습니다.

```text
Cannot read directory "../../..": Access is denied.
Could not resolve "C:\Users\kyc66\NextDrive\infra\gemini-relay\src\portable-worker.ts"
```

사전 번들 시 두 번째 경로는 `.bundle\portable-worker.mjs`였습니다. 로그 `.tools/worker-diagnostics/wrangler-dry-run.log`에는 시작/설정/전송 비활성 기록이 있고, 위 오류는 실제 명령의 표준 오류 출력에서 확인했습니다. `.env`가 없다는 기록은 정상 안내이며 이번 실패 원인이 아닙니다. 모델 API·Cloudflare 인증 HTTP 오류 또는 TypeScript 진단으로 실패한 것이 아닙니다.

권한 변경·sandbox 해제·부모 폴더 접근 재시도를 하지 않았습니다. 중간에 hash가 확인된 기존 번들의 두 reader 호출만 원본과 맞추고 메모리 esbuild transform으로 실행 검사를 했지만, 이를 전체 원본 빌드 성공으로 부르지 않았습니다. 이후 사용자가 아래의 **전체 원본 번들 생성과 Wrangler dry-run**을 일반 PowerShell에서 실행해 모두 성공했다고 확인했습니다. 실제 새 파일 두 개도 열어 별도로 검사했습니다.

```powershell
cd C:\Users\kyc66\NextDrive\infra\gemini-relay
node test/bundle-portable.mjs
node node_modules/wrangler/bin/wrangler.js deploy --dry-run --config wrangler.existing.jsonc --outdir .bundle/wrangler-diagnostic
```

이 명령에서 `--dry-run`을 빼면 안 됩니다. Codex sandbox의 원본 dry-run은 여전히 실패한 것이며, 일반 PowerShell 성공은 사용자 확인/산출물 검증으로 구분합니다.

## 최종 파일·검사

| 용도 | 로컬 파일 | SHA256 |
|---|---|---|
|Cloudflare Edit code에 붙여넣을 **전체 단일 ESM**|`infra/gemini-relay/.bundle/portable-worker.mjs`|`24FAC496B41181FA7295E3A0AFFE1AB2BE7B2F9D6281C0BF2664D2E6DB3FD09A`|
|Wrangler가 원본에서 만든 업로드 예정 JS 확인용|`infra/gemini-relay/.bundle/wrangler-diagnostic/portable-worker.js`|`5EC1FE44B485A9F52389E39FBCCF629B3ECF4E41A8871F97B7C938E0E9F07A74`|

파일 크기는 각각80,383/83,587 bytes입니다. `.bundle/index.js`는 구형 v1 산출물이므로 사용하지 않습니다. Wrangler JS는 편집기용 지시자가 없는 별도 산출물이며 이를 다시 붙여넣으면 의존성 타입 진단이 다시 보일 수 있습니다. **Edit code에는 위 mjs를 사용**합니다. `.ts` 원본 일부·source map·README를 붙여넣지 않습니다.

| 검사 | 결과 |
|---|---|
|원본 TypeScript|통과, emit/배포 없음|
|보안 회귀|64개, 원본·최종mjs·Wrangler JS 각각 통과; 같은 검사의 반복을192개로 합산하지 않음|
|실제 로컬 workerd|16개, 기존/수정mjs·Wrangler JS에서 확인; Google 네트워크/실제 모델 아님|
|생성 JS 편집기 프로필|표시 적용0개. 표시 제거 결과도 별도로 남김|
|최종 원본 번들·Wrangler dry-run|일반 PowerShell 성공 사용자 확인, 생성 JS를 직접 검사|
|Cloudflare 편집기 새 파일 재검사·업로드 수락|미실행|
|실제 Cloudflare JWT Google 인증서/DO·Gemini|미실행|
|Android 빌드·에뮬레이터 회귀|이번 범위 밖, 앱 변경 없음·과거 결과를 재실행으로 세지 않음|

workerd16개는 모듈/health·모델OFF 거부·실제 local SQL DO, 한국어·UTF8오류·크기 제한·BOM, 번들 jose의 RSA 결과·fromBase64 fallback·만료/잘못된 aud/서명 거부, 실제 challenge/alarm/P1363 등록, 외부 요청0입니다. JWT 테스트는 메모리 fixture 키를 쓰므로 실제 Firebase 계정 인증 성공과 다릅니다. SQLite DO는 새 로컬 격리 저장소이며 운영 namespace/사용량이 아닙니다.

## 가장 안전한 후속 배포 방법 — 이번에는 실행하지 않음

1. 검증된 GitHub 소스를 기준으로 일반 PowerShell에서 위 dry-run을 유지하고 성공 산출물/설정 diff를 검토합니다. 이후 **별도의 실제 배포 승인 후** 동일한 `wrangler.existing.jsonc`를 사용하는 Wrangler 경로를 권장합니다. keep_vars·기존 BUDGET/class/migration을 유지하는 설정이며 초기 설치용 `wrangler.jsonc`나 무심코 `npm run deploy`를 사용하지 않습니다.
2. Dashboard를 사용한다면 **caritda-ai-relay-dev → Edit code → 기존 index.js**에 최종 mjs 전체를 넣습니다. 파일명 index.js 자체는 문제없으며 ESM export를 유지합니다. 기존 코드/버전·설정은 보존하고 승인 없이 Deploy를 누르지 않습니다.
3. 실제 배포 단계가 승인되더라도 먼저 모델OFF로 버전·무인증 거부·기기 등록·기존 사용량을 확인합니다. 기존 Secret/DO를 새로 만들거나 삭제·초기화하지 않습니다. 새 권한/모델 예산은 이번 수정에 포함되지 않았습니다.
4. 편집기의 타입 진단과 실제 배포 실패를 구분합니다. `Uncaught SyntaxError`, 모듈/클래스 export 오류, 10021·binding/migration 오류 같은 실제 실패가 나오면 그 메시지를 보존하고 중단합니다. 진단이0개라는 이유만으로 기존 원장·실제 AI가 검증됐다고 하지 않습니다.

최종 커밋은 완료 응답에서 안내합니다. 번들/로그/실행 환경·개인 설정은 Git 제외이며 검증된 원본·검사·문서만 게시합니다. 기존 연구 문서는 수정하지 않습니다.

## 공식 근거

- [Cloudflare Quick Edit의 JavaScript 타입 검사](https://blog.cloudflare.com/improved-quick-edit/): 편집기 타입 진단의 성격을 설명하는 자료이며 현재 편집기 선언 버전을 증명하는 것은 아닙니다.
- [Wrangler 번들·dry-run](https://developers.cloudflare.com/workers/wrangler/bundling/), [명령 옵션](https://developers.cloudflare.com/workers/wrangler/commands/workers/): 산출물 검사와 실제 배포를 구분합니다.
- [Workers TextDecoder](https://developers.cloudflare.com/workers/runtime-apis/encoding/), [원본 런타임 타입 생성 권장](https://developers.cloudflare.com/workers/languages/typescript/)
- [TypeScript JS 검사/파일별 표시](https://www.typescriptlang.org/docs/handbook/intro-to-js-ts.html), [실제 배포·런타임 오류](https://developers.cloudflare.com/workers/observability/errors/)
