# Changelog

## v0.4.0
- 대표사진 웹 자동검색 완전 제거
- 시계 대표사진 휴대폰에서 직접 선택/삭제 기능 추가
- 스펙 검색 엔진 정확성 우선 방식으로 재작성
- Bing RSS + DuckDuckGo Lite 다중 검색
- 레퍼런스 exact-match 기반 후보 확대 및 실제 페이지 2차 검증
- 공식 제조사 / 고신뢰 시계 DB / 전문매체 / 판매처 source tier 도입
- 비공식 페이지는 레퍼런스 본문 일치 요구
- 공식/고신뢰 페이지 접근 차단 시 exact-reference 검색 스니펫 제한적 fallback
- 필드별 후보 수집 + source-weighted consensus 적용
- 공식 값 우선, 비공식 값은 고신뢰 단일 DB 또는 복수 출처 합의 시에만 자동 입력
- 직경/러그투러그/두께/러그 폭/파워리저브 수치 tolerance 검증
- Seiko/Hamilton/Mido 공식 페이지 표기 패턴 보강
- 한국어 공식 페이지 필드명 대응 확대
- `Automatic with manual winding`을 Manual로 오인하던 판정 방지
- GitHub Actions에서 Gradle 프로젝트 경로 자동 탐색
- artifact 이름 `WatchCollection-v0.4-debug-apk`로 변경

## v0.3.0
- 시계 검색 엔진 전면 재작성
- 하단 네비게이션 아이콘 4종
- 착용 캘린더 올해/이번 달 TOP3
- 하루 최대 2개 시계 및 0.5회 weighted count
- 특정 스트랩 폭 화면 상단 호환 시계 표시
