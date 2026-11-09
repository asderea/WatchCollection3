# Watch Collection AI·크레딧 게이지 설정 가이드 (v1.32 · 월별 자동 갱신)

Watch Collection은 OpenAI API 키를 Android APK에 넣지 않습니다. 앱은 개인 Cloudflare Worker만 호출하고, Worker가 OpenAI Responses API와 조직 Costs API를 호출합니다.

```text
Watch Collection APK
        │
        │ HTTPS + X-Watch-Token
        ▼
Cloudflare Worker
        ├─ OPENAI_API_KEY   → Responses API
        └─ OPENAI_ADMIN_KEY → Organization Costs API
```

v1.30부터 앱 우측 상단에 원형 API 크레딧 게이지가 표시됩니다. 게이지를 누르면 다음 내용을 확인할 수 있습니다.

- 지급받은 전체 크레딧
- 현재까지 사용된 비용
- 예상 잔여 크레딧과 잔여 비율
- 지급 시작일과 만료일
- 스펙 정밀 검색·착장 추천·착장 완성·컬렉션 평가의 호출 가능 여부
- 소진 직전 자동 차단 상태

---


## v1.32 개인화 컬렉션 평가

v1.32의 `/api/collection/evaluate` 요청에는 다음 정보가 추가됩니다.

```text
평가 프로필
- 선호 작동 방식
- 선호·비선호 다이얼 색상
- 컬렉션 운영 방향
- 핵심 유지 시계
- 목표 컬렉션 수
- 장기 위시 우선순위
- 장기 교체·완성 계획

시계별 역할
- main_role: Dress / Diver / Tool / Sports 중 필수 선택
- secondary_role: 선택 사항
```

- 초기 평가 프로필에는 사용자의 현재 취향과 장기 계획이 입력되어 있으며 앱에서 직접 수정할 수 있습니다.
- 평가 프로필은 앱의 `SharedPreferences`에 저장되고 컬렉션 평가 요청 시에만 Worker로 전송됩니다.
- Worker는 메인 역할을 주 역할, 보조 역할을 보완 역할로 사용합니다. 역할이 비어 있으면 임의로 추정하지 않습니다.
- 총점은 역할 다양성 25%, 구성 균형 25%, 중복 관리 20%, 실용성 15%, 장기 완성도 15%의 고정 가중치를 사용합니다.
- 프로필이나 역할이 바뀌어도 자동으로 API를 호출하지 않고 앱에 `갱신 필요` 상태만 표시합니다.
- v1.32 Worker에 필요한 새로운 Cloudflare Variable 또는 Secret은 없습니다.

---

## v1.31부터 사용되는 AI 기능

Worker 코드 교체 후 아래 두 경로를 추가로 사용합니다. 새 Cloudflare Variable이나 Secret은 필요하지 않습니다.

```text
POST /api/outfit/complete
POST /api/collection/evaluate
```

- `/api/outfit/complete`는 사용자가 선택한 시계와 호환 스트랩을 고정하고, 비어 있는 상의·하의·신발만 추천합니다. 결과 아래 피드백을 넣어 다시 호출할 수 있습니다.
- `/api/collection/evaluate`는 현재 컬렉션 또는 Wish 교체·방출 계획이 반영된 예상 컬렉션을 100점 만점으로 평가합니다.
- 두 경로 모두 웹 검색을 사용하지 않고 `OPENAI_RECOMMEND_REQUEST_RESERVE_USD` 보호 기준을 적용합니다.
- 현재 컬렉션 평가는 앱 DB에 저장되며 최초 평가 또는 시계 추가·삭제로 컬렉션 ID 구성이 바뀔 때만 자동 호출합니다. 시계 정보 수정, 화면 이동, 앱 재실행만으로는 다시 호출하지 않습니다.
- Wish 평가는 사용자가 `Wish 컬렉션 평가` 버튼을 누를 때만 호출하고 결과를 DB에 저장합니다.
- 피드백을 이용한 착장 재추천은 버튼을 누를 때마다 새로운 API 요청 1회로 계산됩니다.

---

## 0. 먼저 알아둘 점

OpenAI의 문서화된 API에는 `Billing → Credit grants` 화면의 현재 잔액·지급액·만료일을 그대로 읽어오는 엔드포인트가 없습니다. 따라서 이 앱은 다음 방식으로 잔여량을 계산합니다.

```text
예상 잔여 크레딧
= Credit grants 화면에 표시된 실제 지급액
- 지급 시작 이후 OpenAI Costs API에 집계된 비용
```

따라서 앱에는 항상 **예상 잔여량**으로 표시됩니다. 비용 집계 지연, 조정 내역, 다른 프로젝트의 사용 여부에 따라 Billing 화면의 실제 잔액과 소폭 차이가 날 수 있습니다.

프로모션 크레딧 전부를 Watch Collection에 사용할 예정이라면:

1. Watch Collection 전용 OpenAI 프로젝트를 사용합니다.
2. 다른 용도의 API 호출을 같은 조직에서 가급적 하지 않습니다.
3. `OPENAI_PROJECT_ID`는 설정하지 않고 조직 전체 비용을 합산합니다.
4. Credit grants의 실제 지급액·시작일·만료일을 그대로 입력합니다.

첫 지급분이 일할 계산되었다면 원래 안내된 월 지급액이 아니라 **Credit grants 화면에 실제로 표시된 첫 지급액**을 입력해야 합니다. 이후 정규 월 지급액은 `OPENAI_CREDIT_RECURRING_TOTAL_USD`에 한 번만 입력합니다.

Worker는 첫 지급분 만료 직후를 다음 주기 시작점으로 삼아 매월 현재 주기의 시작일·만료일을 자동 계산합니다. Cloudflare Cron이나 매월 Variable 수정은 필요하지 않습니다. 다만 이 방식은 OpenAI의 실제 Credit grants를 조회하는 것이 아니라 입력한 결제 주기를 기준으로 계산하는 방식이므로, 구독 취소·요금제 변경·추가 일할 지급은 자동 감지하지 못합니다.

---

## 1. OpenAI API 프로젝트 만들기

1. OpenAI API Platform에 로그인합니다.
2. 사용할 API 조직을 선택합니다.
3. 프로젝트 선택 메뉴에서 `Create project`를 누릅니다.
4. 프로젝트 이름을 다음처럼 지정합니다.

```text
Watch Collection
```

5. 이후 일반 API 키는 반드시 이 프로젝트에서 만듭니다.

---

## 2. Responses API용 일반 프로젝트 키 만들기

다음 경로로 이동합니다.

```text
Project Settings
→ API Keys
→ Create new secret key
```

이름 예시:

```text
watch-collection-backend
```

생성된 `sk-...` 값을 복사합니다. 이 값은 Cloudflare Worker의 다음 Secret으로 저장합니다.

```text
OPENAI_API_KEY
```

이 키는 시계 스펙 검색, 오늘의 시계 추천, 착장 완성, 현재·Wish 컬렉션 평가에 사용됩니다.

> 일반 프로젝트 키는 Android 앱, GitHub 저장소, `strings.xml`, `gradle.properties`, README에 넣지 않습니다.

---

## 3. Costs API용 OpenAI Admin API Key 만들기

잔여 크레딧 계산에는 조직 단위 Costs API가 필요합니다. 일반 프로젝트 키가 아니라 **API Platform 조직 Admin API Key**를 별도로 사용합니다.

1. OpenAI API Platform에서 조직 설정을 엽니다.
2. `Admin keys` 메뉴로 이동합니다.
3. `Create new admin key`를 누릅니다.
4. 이름을 다음처럼 지정합니다.

```text
watch-collection-cost-reader
```

5. 권한 선택 화면이 보이면 Usage/Costs 조회에 필요한 읽기 권한을 부여합니다. 세부 권한 구분이 없다면 `Read only`를 사용합니다.
6. 생성 직후 표시되는 `sk-admin-...` 값을 복사합니다.

이 값은 Cloudflare Worker의 다음 Secret으로 저장합니다.

```text
OPENAI_ADMIN_KEY
```

주의사항:

- 조직 Owner만 Admin API Key를 만들 수 있습니다.
- Admin API Key는 모델 추론용이 아닙니다.
- 일반 `OPENAI_API_KEY`와 `OPENAI_ADMIN_KEY`는 서로 다른 키입니다.
- Admin API Key도 Android 앱이나 GitHub에 넣지 않습니다.

---

## 4. Credit grants의 실제 지급 정보 확인

OpenAI API Platform에서 다음 메뉴로 이동합니다.

```text
Billing
→ Credit grants
```

현재 활성 지급분과 정상 갱신 시 월 지급액에서 다음 네 값을 확인합니다.

1. 첫 지급분의 실제 지급액
2. 첫 지급분의 시작 시각 또는 시작일
3. 첫 지급분의 만료 시각 또는 만료일
4. 다음 갱신부터 적용될 정규 월 지급액

예를 들어 첫 지급분이 `$83.42`이고 이후 정상 월 지급액이 `$100.00`이면 다음처럼 입력합니다.

```text
OPENAI_CREDIT_TOTAL_USD = 83.42
OPENAI_CREDIT_RECURRING_TOTAL_USD = 100.00
```

날짜는 가능하면 시간대까지 포함한 ISO 8601 형식을 사용합니다.

```text
YYYY-MM-DDTHH:mm:ss+09:00
```

형식 예시이며 실제 값으로 그대로 복사하면 안 됩니다.

```text
OPENAI_CREDIT_START_AT   = 2026-11-05T00:00:00+09:00
OPENAI_CREDIT_EXPIRES_AT = 2026-12-04T23:59:59+09:00
```

Credit grants 화면에 날짜만 보인다면 해당 지급분의 실제 유효 기간을 확인한 뒤 시작일 00:00:00, 만료일 23:59:59로 입력합니다.

---

## 5. Cloudflare Worker 코드 교체

소스 ZIP에서 다음 파일을 사용합니다.

```text
WatchCollection/
└─ ai-backend/
   ├─ worker.js
   └─ wrangler.toml.example
```

Cloudflare Dashboard에서 기존 Worker를 엽니다.

```text
Workers & Pages
→ Overview
→ watch-collection-ai 선택
→ Edit code
```

1. 기존 Worker 코드를 전체 선택해 삭제합니다.
2. `ai-backend/worker.js`의 전체 내용을 붙여넣습니다.
3. `Deploy`를 누릅니다.

기존 Worker를 새로 만들 필요는 없습니다. 기존 Worker URL을 유지한 채 코드만 교체하면 앱의 서버 URL도 그대로 사용할 수 있습니다.

---

## 6. Cloudflare Secrets 등록

다음 경로로 이동합니다.

```text
Worker
→ Settings
→ Variables and Secrets
→ Add
```

아래 세 항목은 모두 Type을 `Secret`으로 지정합니다.

| 이름 | 값 | 용도 |
|---|---|---|
| `OPENAI_API_KEY` | `sk-...` | Responses API 호출 |
| `OPENAI_ADMIN_KEY` | `sk-admin-...` | Costs API 조회 |
| `WATCH_APP_TOKEN` | 직접 만든 긴 임의 문자열 | 앱과 Worker 인증 |

`WATCH_APP_TOKEN` 예시:

```text
wc_private_2026_random_token_83k2x9pq
```

최소 12자 이상을 사용하고, 가능하면 30자 이상의 무작위 문자열을 사용합니다. 앱에도 정확히 같은 값을 입력합니다.

Secret은 저장 후 값이 다시 표시되지 않으므로 별도 암호 관리자에 보관합니다.

---

## 7. Cloudflare Variables 등록

같은 `Variables and Secrets` 화면에서 아래 항목은 `Text` 또는 일반 Variable로 등록합니다.

### 7.1 모델 설정

```text
OPENAI_MODEL = gpt-6-luna
OPENAI_SERVICE_TIER = default
```

`fast` 티어는 더 비쌀 수 있으므로 프로모션 크레딧을 효율적으로 사용하려면 `default`를 유지합니다.

### 7.2 첫 지급분과 정규 월 지급액

```text
OPENAI_CREDIT_TOTAL_USD = 첫 지급분의 실제 지급액
OPENAI_CREDIT_START_AT = 첫 지급분의 실제 시작 시각
OPENAI_CREDIT_EXPIRES_AT = 첫 지급분의 실제 마지막 유효 시각
OPENAI_CREDIT_RECURRING_TOTAL_USD = 이후 정규 월 지급액
```

예시:

```text
OPENAI_CREDIT_TOTAL_USD = 83.42
OPENAI_CREDIT_START_AT = 2026-11-05T00:00:00+09:00
OPENAI_CREDIT_EXPIRES_AT = 2026-12-04T23:59:59+09:00
OPENAI_CREDIT_RECURRING_TOTAL_USD = 100.00
```

위 예시라면 Worker가 다음과 같이 자동 전환합니다.

```text
첫 지급분: 2026-11-05 00:00:00 ~ 2026-12-04 23:59:59 / $83.42
2회차:     2026-12-05 00:00:00 ~ 2027-01-04 23:59:59.999 / $100.00
3회차:     2027-01-05 00:00:00 ~ 2027-02-04 23:59:59.999 / $100.00
```

`OPENAI_CREDIT_RECURRING_TOTAL_USD`를 등록하지 않으면 기존 v1.30과 동일하게 첫 지급분 만료 후 호출이 차단됩니다. 금액과 날짜는 형식 예시이므로 본인의 실제 Credit grants 및 구독 혜택 금액을 사용합니다.

### 7.3 소진 직전 차단 설정

```text
OPENAI_CREDIT_GUARD_ENABLED = true
OPENAI_CREDIT_RESERVE_USD = 0.00
OPENAI_SPEC_REQUEST_RESERVE_USD = 0.05
OPENAI_RECOMMEND_REQUEST_RESERVE_USD = 0.01
OPENAI_USAGE_CACHE_SECONDS = 60
```

각 변수의 의미:

- `OPENAI_CREDIT_GUARD_ENABLED=true`
  - 지급 기간 종료 또는 잔여량 부족 시 Worker가 유료 API 호출을 차단합니다.
- `OPENAI_CREDIT_RESERVE_USD=0.00`
  - 기능별 보호액을 그대로 적용하기 위한 공통 최저값입니다.
- `OPENAI_SPEC_REQUEST_RESERVE_USD=0.05`
  - 스펙 정밀 검색은 웹 검색 도구 비용까지 발생하므로 잔여량이 5센트 이하이면 먼저 차단합니다.
- `OPENAI_RECOMMEND_REQUEST_RESERVE_USD=0.01`
  - 착장 추천은 웹 검색을 사용하지 않으므로 잔여량이 1센트 이하이면 차단합니다.
- `OPENAI_USAGE_CACHE_SECONDS=60`
  - Costs API를 60초 동안 임시 캐시합니다. 앱의 수동 새로고침은 캐시를 무시합니다.

이 설정은 지급액을 거의 모두 사용하면서도 비용 집계 지연으로 유료 잔액이 넘어가는 위험을 줄이기 위한 값입니다.

### 7.4 `OPENAI_PROJECT_ID`는 기본적으로 등록하지 않음

`OPENAI_PROJECT_ID`를 비워 두면 조직 전체 비용을 합산합니다. Credit grants는 조직 단위로 적용되므로 실제 잔여 크레딧과 가장 가까운 계산 방식입니다.

특정 Watch Collection 프로젝트 비용만 따로 보고 싶은 경우에만 다음 값을 추가합니다.

```text
OPENAI_PROJECT_ID = proj_...
```

단, 다른 프로젝트가 같은 조직의 크레딧을 사용했는데 이 값을 설정하면 앱의 잔여량이 실제보다 크게 표시될 수 있습니다.

모든 Secret과 Variable을 추가한 뒤 반드시 `Deploy`를 눌러 새 버전에 반영합니다.

---

## 8. Worker URL 확인

다음 경로에서 Worker 주소를 확인합니다.

```text
Worker
→ Settings
→ Domains & Routes
```

일반적인 주소 형식:

```text
https://watch-collection-ai.<Cloudflare 계정 서브도메인>.workers.dev
```

주소 끝에 `/`는 붙이지 않아도 됩니다. 앱이 자동으로 제거합니다.

---

## 9. PowerShell로 Worker 먼저 검사

아래 값 두 개만 본인의 값으로 바꿉니다.

```powershell
$workerUrl = "https://watch-collection-ai.<계정>.workers.dev"
$watchToken = "Cloudflare에 등록한 WATCH_APP_TOKEN"

$headers = @{
    "X-Watch-Token" = $watchToken
    "Accept" = "application/json"
}
```

### 9.1 연결 상태 확인

```powershell
$health = Invoke-RestMethod `
    -Method Get `
    -Uri "$workerUrl/health" `
    -Headers $headers

$health | ConvertTo-Json -Depth 10
```

정상 예시:

```json
{
  "ok": true,
  "model": "gpt-6-luna",
  "usage_configured": true,
  "credit_guard_enabled": true
}
```

`usage_configured`가 `false`라면 크레딧 지급 정보 또는 `OPENAI_ADMIN_KEY`가 누락된 상태입니다.

### 9.2 크레딧 잔여량 확인

```powershell
$usage = Invoke-RestMethod `
    -Method Get `
    -Uri "$workerUrl/api/usage?refresh=1" `
    -Headers $headers

$usage | ConvertTo-Json -Depth 10
```

정상 응답의 핵심 필드:

```json
{
  "ok": true,
  "data": {
    "configured": true,
    "estimated": true,
    "total_usd": 83.42,
    "used_usd": 0.25,
    "remaining_usd": 83.17,
    "remaining_percent": 99.70,
    "renewal_mode": "monthly_auto",
    "cycle_index": 0,
    "recurring_total_usd": 100.0,
    "next_cycle_start_at": "2026-12-04T15:00:00.000Z",
    "guard_enabled": true,
    "spec_search_allowed": true,
    "recommendation_allowed": true
  }
}
```

### 9.3 스펙 검색 실제 호출 확인

이 테스트는 모델 및 웹 검색 비용이 발생합니다.

```powershell
$headers["Content-Type"] = "application/json"

$body = @{
    query = "Seiko SPB519J1"
    current_brand = "Seiko"
    current_reference = "SPB519J1"
} | ConvertTo-Json

$result = Invoke-RestMethod `
    -Method Post `
    -Uri "$workerUrl/api/watch/spec-search" `
    -Headers $headers `
    -Body $body

$result | ConvertTo-Json -Depth 10
```

이전에 다음 두 줄만 실행했다면 결과가 나오지 않는 것이 정상입니다.

```powershell
$body = @{ ... } | ConvertTo-Json
$result | ConvertTo-Json -Depth 10
```

이 경우 `$body`만 만들었고, `Invoke-RestMethod` 결과를 `$result`에 대입하지 않았기 때문입니다. 반드시 다음 형태의 호출문이 있어야 합니다.

```powershell
$result = Invoke-RestMethod ...
```

---

## 10. 앱에 Worker 연결

1. Watch Collection 앱을 실행합니다.
2. 캘린더 탭으로 이동합니다.
3. `TODAY'S WATCH` 카드를 누릅니다.
4. `AI 연결 설정`을 누릅니다.
5. 다음 값을 입력합니다.

```text
AI 서버 URL
https://watch-collection-ai.<계정>.workers.dev

Watch App Token
Cloudflare Secret에 등록한 WATCH_APP_TOKEN
```

6. `저장 및 테스트`를 누릅니다.

연결 테스트는 `/health`만 호출하므로 모델 사용료가 발생하지 않습니다. 연결 성공 후 앱 우측 상단의 API 원형 게이지가 자동으로 갱신됩니다.

---

## 11. 원형 게이지 읽는 방법

상단 원형 게이지 중앙에는 예상 잔여 비율이 표시됩니다.

- 금색 링: 정상 사용 가능
- 주황색 링: 잔여량이 낮아 일부 기능 제한
- 빨간색 링: 호출 차단
- `—`: Worker의 크레딧 설정 미완료
- `!`: Costs API 조회 실패
- `···`: 조회 중

게이지를 누르면 상세 팝업에서 금액과 차단 상태를 확인할 수 있습니다.

앱은 다음 시점에 자동 갱신합니다.

- 앱 실행 및 복귀
- AI 연결 설정 저장 후
- 스펙 정밀 검색 완료 또는 실패 후
- 기존 오늘의 시계 AI 추천 완료 또는 실패 후
- 착장 완성 추천 완료 또는 실패 후
- 현재 컬렉션·Wish 컬렉션 평가 완료 또는 실패 후
- 게이지 상세창의 `새로고침` 선택 시

---

## 12. 호출 차단 동작

Worker는 유료 요청 전에 예상 잔여량을 확인합니다.

### 스펙 정밀 검색

```text
예상 잔여량 ≤ max(공통 보호액, 스펙 검색 보호액)
```

이면 호출을 차단합니다. 기본 설정에서는 `$0.05` 이하입니다.

### 추천·평가 계열

다음 기능은 같은 추천용 보호액을 사용합니다.

- 기존 오늘의 시계 AI 추천: `/api/watch/recommend`
- 선택한 시계·스트랩 기반 착장 완성: `/api/outfit/complete`
- 현재 컬렉션 및 Wish 컬렉션 평가: `/api/collection/evaluate`

```text
예상 잔여량 ≤ max(공통 보호액, 추천 요청 보호액)
```

이면 호출을 차단합니다. 기본 설정에서는 `$0.01` 이하입니다.

지급 시작 전과 예상 잔여량이 0인 경우에도 차단합니다. `OPENAI_CREDIT_RECURRING_TOTAL_USD`가 없으면 첫 지급 기간 만료 후에도 차단하며, 자동 월별 갱신이 설정되어 있으면 새 주기로 자동 전환합니다.

차단 시 Worker는 HTTP 402와 `credit_guard_blocked` 코드를 반환합니다. 스펙 정밀 검색은 기존 무료 검색기로 자동 전환되고, 추천·평가 계열 기능은 차단 사유를 앱에 표시합니다.

> Costs API 집계는 실시간 결제 잔액과 완전히 동일하지 않을 수 있으므로 0달러까지 정확히 맞춰 소진하는 절대 보장 장치는 아닙니다. 결제수단이 연결되어 있다면 OpenAI 프로젝트의 hard spend limit도 추가 보호 수단으로 설정하는 편이 안전합니다.

---

## 13. 다음 지급 주기 자동 갱신

다음 네 값은 최초 설정할 때만 입력합니다.

```text
OPENAI_CREDIT_TOTAL_USD
OPENAI_CREDIT_START_AT
OPENAI_CREDIT_EXPIRES_AT
OPENAI_CREDIT_RECURRING_TOTAL_USD
```

첫 지급분이 끝나면 Worker가 첫 만료 시각 다음 순간을 정규 지급 주기의 기준점으로 사용합니다. 이후 현재 날짜를 기준으로 월 단위 주기를 계산하므로 매월 Cloudflare Variable을 바꾸거나 다시 Deploy할 필요가 없습니다. `/api/usage`가 호출될 때 현재 주기만 계산하므로 별도의 Cron Trigger도 필요하지 않습니다.

다음 경우에는 실제 조건에 맞춰 Variable을 수정해야 합니다.

- ChatGPT/API 크레딧 혜택의 월 지급액이 변경된 경우
- 구독 결제일 또는 지급 주기가 변경된 경우
- 구독 취소·일시중지 후 다시 활성화한 경우
- 업그레이드로 중간 일할 추가 지급이 발생한 경우

공식 API로 실제 Credit grants 지급 여부를 조회하는 구조가 아니므로, 결제수단이나 구매 크레딧이 연결되어 있다면 추정 주기와 실제 지급 상태가 다를 때 유료 잔액이 사용될 수 있습니다. 프로모션 크레딧만 사용할 목적이라면 자동 충전과 구매 크레딧 상태도 함께 확인합니다.

---

## 14. 오류별 확인 순서

### `401 Watch App Token이 올바르지 않습니다.`

- 앱의 Watch App Token과 Cloudflare `WATCH_APP_TOKEN`이 정확히 같은지 확인합니다.
- 앞뒤 공백이 들어가지 않았는지 확인합니다.
- Secret 수정 후 Deploy했는지 확인합니다.

### `/health`는 정상인데 게이지가 `—`

다음 항목을 확인합니다.

```text
OPENAI_ADMIN_KEY
OPENAI_CREDIT_TOTAL_USD
OPENAI_CREDIT_START_AT
OPENAI_CREDIT_EXPIRES_AT
OPENAI_CREDIT_RECURRING_TOTAL_USD  # 자동 월별 갱신을 사용할 때
```

### `OpenAI Costs API 오류 HTTP 401 또는 403`

- 일반 프로젝트 키를 `OPENAI_ADMIN_KEY`에 넣지 않았는지 확인합니다.
- API Platform 조직 Admin API Key인지 확인합니다.
- Admin key에 Costs/Usage 조회 권한이 있는지 확인합니다.
- 키를 만든 조직과 크레딧이 지급된 조직이 같은지 확인합니다.

### `credit_guard_not_configured`

`OPENAI_CREDIT_GUARD_ENABLED=true`인데 필수 크레딧 변수가 빠진 상태입니다. 누락된 설정을 모두 추가해야 합니다.

### `credit_guard_blocked`

지급 기간이 끝났거나 잔여량이 보호 기준 이하입니다. Credit grants 실제 잔액과 만료일을 확인합니다.

### OpenAI `credit_balance_exhausted`

조직의 실제 선불 또는 프로모션 크레딧이 소진된 상태입니다. 앱의 추정치와 Billing 화면을 비교합니다.

### PowerShell에서 아무것도 출력되지 않음

`Invoke-RestMethod`를 실행하지 않았거나 결과를 변수에 대입하지 않은 경우가 가장 흔합니다.

```powershell
$result = Invoke-RestMethod ...
$result | ConvertTo-Json -Depth 10
```

형태로 실행합니다.

---

## 15. 보안 원칙

다음 값은 APK와 GitHub에 절대 포함하지 않습니다.

```text
OPENAI_API_KEY
OPENAI_ADMIN_KEY
WATCH_APP_TOKEN
```

Cloudflare에서는 모두 Secret 타입으로 저장합니다. 키가 노출됐다고 의심되면 기존 키를 폐기하고 새 키로 교체합니다.

앱에는 다음 두 값만 저장됩니다.

```text
Worker URL
WATCH_APP_TOKEN
```

`WATCH_APP_TOKEN`은 OpenAI 키가 아니지만 Worker에 접근할 수 있으므로 외부에 공유하지 않습니다.
