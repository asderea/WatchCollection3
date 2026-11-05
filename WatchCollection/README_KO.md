# Watch Collection v1.30

개인 시계 컬렉션, 오차 기록, 착용 캘린더, 오착 사진, 스트랩, 수리 이력, 헤리티지와 컬렉션 사진을 관리하는 Android 앱입니다.

## v1.30 주요 변경

### API 크레딧 게이지 및 비용 보호
- 앱 우측 상단에 예상 API 크레딧 잔여 비율을 원형 게이지로 표시합니다.
- 게이지를 누르면 실제 지급액 기준의 예상 잔여액, 사용 비용, 만료일과 기능별 호출 상태를 확인할 수 있습니다.
- Cloudflare Worker가 조직 Costs API를 조회하며 Admin API Key는 Worker Secret에만 저장합니다.
- 스펙 정밀 검색과 착장 추천은 서로 다른 보호액을 사용해 크레딧 소진 직전에 자동 차단합니다.
- Credit grants 실제 지급액·시작일·만료일과 Worker 설정 방법은 `README_AI_SETUP_KO.md`에 정리되어 있습니다.

### TODAY'S WATCH + FORGOTTEN WATCH
- 캘린더 상단에서 TODAY'S WATCH와 FORGOTTEN WATCH를 좌우로 나란히 표시합니다.
- TODAY'S WATCH에는 API 연결 전에도 기본 추천 시계가 항상 나타납니다.
- 기본 추천은 장기 미착용, 이번 달 낮은 착용량, 최근 연속 착용 제외, 사용자 제외 목록, 수리 중 상태를 반영합니다.
- 기본 추천에는 스트랩을 표시하지 않습니다.

### 착장 AI 추천
- TODAY'S WATCH 카드를 누르면 오늘의 착장 입력 팝업이 열립니다.
- AI 추천은 착장 색상·격식·소재와 보유 시계·호환 스트랩을 기준으로 최대 3개 조합을 반환합니다.
- 착장 AI 추천에서는 장기 미착용과 이번 달 착용량을 고려하지 않습니다.
- 최근 연속 착용 여부는 착장 AI 추천에서 제외 조건으로 사용하지 않습니다.
- 사용자 제외 목록과 수리·보증 처리 중 상태는 계속 제외합니다.
- 당일 추천 완료 후 TODAY'S WATCH 카드는 1위 시계·스트랩 조합으로 바뀝니다.
- 다음 날짜에는 다시 로컬 기본 추천으로 돌아갑니다.

### AI 스펙 정밀 검색
- 시계 추가·수정 화면의 스펙 검색이 AI 백엔드를 우선 사용합니다.
- 공식 제조사 페이지와 정확한 Reference를 우선 검증합니다.
- 한 요청의 웹 검색 호출 수를 Responses API의 max_tool_calls로 최대 2회까지 제한합니다.
- AI 서버 오류 시 기존 무료 검색기로 자동 전환합니다.
- API 키는 Android 앱에 저장하지 않습니다.

### AI 백엔드
- `ai-backend/worker.js`: Cloudflare Worker용 중계 서버
- `README_AI_SETUP_KO.md`: API 키 생성부터 연결 및 예상 사용량 안내
- 기본 모델: `gpt-6-luna`
- 기본 reasoning effort: `none`
- 기본 service tier: `default`

## 데이터 유지
- applicationId: `com.watchcollection.app` 유지
- 동일 debug 서명키 유지
- DB version 15 유지
- 기존 시계, 스트랩, 착용 기록, 오착 사진, 수리 이력, 헤리티지, 오차 기록 유지

## 빌드
저장소 루트의 `.github/workflows/build-apk.yml`에 제공된 workflow를 붙여넣어 GitHub Actions에서 빌드합니다.
