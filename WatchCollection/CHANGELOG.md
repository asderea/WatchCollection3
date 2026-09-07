# Changelog

## v0.6.0
- 월간 캘린더의 주 시작 요일을 월요일에서 일요일로 변경
- 요일 헤더를 `일 / 월 / 화 / 수 / 목 / 금 / 토` 순서로 변경
- 월 첫날의 셀 오프셋 계산도 일요일 기준으로 수정
- DB 구조 변경 없음: 기존 시계/오차/착용/스트랩 데이터 유지

## v0.5.0
- 시계 스펙 검색 엔진 전면 확장
  - Bing RSS/Bing HTML/DuckDuckGo Lite 조합
  - 일본 브랜드 Yahoo Japan 보조 검색
  - 후보 부족 시 Google HTML 보조 검색
  - 제조사 공식 sitemap 직접 탐색
  - 단종 공식 페이지 Internet Archive 보조 탐색
  - 레퍼런스 alias/구두점/Seiko 지역 suffix 정규화
  - JSON-LD/additionalProperty/embedded JSON 스펙 추출
  - 일본어/독일어 스펙 라벨 추가
  - Casio/Orient L×W×H, Mido/Tissot 필드 표기 보강
  - 신뢰도 기반 교차검증 유지
- 캘린더 날짜 착용 선택을 팝업 UI로 변경
- 캘린더 TOP3 카드 폭을 캘린더와 동일하게 변경
- 좌우 버튼으로 올해/이번 달 TOP3 전환
- 스트랩 `기타` 폭 카테고리 추가
- 스트랩 제작사 필드 추가 및 카드 표시
- DB v4 마이그레이션
