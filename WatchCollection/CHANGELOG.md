# Changelog

## v0.7.0
- 기존 수동 오차 입력 UI 제거
- 마이크 기반 Timegrapher 기능 추가
  - 48 kHz AudioRecord 입력
  - high-pass filtering / adaptive beat detection
  - AudioTimestamp 기반 오디오 sample-clock 보정
  - 18,000 / 19,800 / 21,600 / 25,200 / 28,800 / 36,000 bph 자동 후보 판정
  - robust beat-index regression 기반 Rate 계산
  - tick/tock parity 기반 Beat Error 계산
  - Lift angle + impulse span 기반 Amplitude 보조 추정
  - signal quality / 측정 시간 / trace 표시
- 종이식 timegrapher 형태의 전용 측정 View 추가
- 측정값 자세별 저장
  - Dial up / Dial down / 12 Up / 9 Up / 6 Up / 3 Up
- 시계별 자세별 최근 측정값 요약 카드 추가
- `timegrapher_records` DB 테이블 추가
- DB v5 마이그레이션
- 대표 사진 선택 시 자동 배경 통일 기능 추가
  - 사용자 지정 배경 `#FCFBF7`
  - 모서리 연결 영역 flood-fill 방식
  - 복잡한 배경이면 원본 유지
- 대표 사진 원본 별도 저장 및 `원본 복구` 기능 추가
- 컬렉션 브랜드별 필터 추가
- 컬렉션 기본 시계 정렬을 전체 착용 횟수 내림차순으로 변경
  - 하루 2개 착용 기록은 각 0.5회로 반영
  - 동률은 브랜드 → 모델명 순
- Monthly Wear를 1~5위 / 6~10위 두 페이지로 분리
- Monthly Wear 좌/우 화살표 페이지 전환 추가
- 기존 applicationId / 서명키 유지: 기존 APK 위에 업데이트 설치 가능
- 기존 데이터 유지

## v0.6.0
- 월간 캘린더의 주 시작 요일을 월요일에서 일요일로 변경
- 요일 헤더를 `일 / 월 / 화 / 수 / 목 / 금 / 토` 순서로 변경
- 월 첫날의 셀 오프셋 계산도 일요일 기준으로 수정
- DB 구조 변경 없음: 기존 시계/오차/착용/스트랩 데이터 유지

## v0.5.0
- 시계 스펙 검색 엔진 전면 확장
- 캘린더 날짜 착용 선택을 팝업 UI로 변경
- 캘린더 TOP3 카드 폭을 캘린더와 동일하게 변경
- 좌우 버튼으로 올해/이번 달 TOP3 전환
- 스트랩 `기타` 폭 카테고리 추가
- 스트랩 제작사 필드 추가 및 카드 표시
- DB v4 마이그레이션
