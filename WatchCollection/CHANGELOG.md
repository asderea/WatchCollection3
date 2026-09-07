# Changelog

## v0.9.0
- Watch Accuracy Meter 외부 앱 연동 추가
  - 설치 시 앱 바로 실행
  - 미설치 시 Google Play 페이지 이동
  - Watch Accuracy Meter에서 돌아오면 결과 입력 팝업 자동 표시
  - 별도 `측정 결과 입력` 버튼 제공
- Watch Accuracy Meter 결과 저장 UI 추가
  - BPH / Rate / Beat Error / Amplitude / 자세 / 날짜 / 메모
  - 18,000 / 19,800 / 21,600 / 25,200 / 28,800 / 36,000 bph 후보 안내
- 타임그래퍼 기록에 `source` 필드 추가
  - `internal` / `watch_accuracy_meter` 구분
- 최근 측정 카드와 시계별 오차 팝업에서 측정 출처 표시
- 시계별 오차 팝업에 Watch Accuracy Meter 기록 횟수 표시
- 자세별 평균 및 전체 평균에는 기존 자체 측정 + Watch Accuracy Meter 기록을 함께 반영
- 자체 타임그래퍼를 `실험적 자체 타임그래퍼`로 재배치
- Android 11+ 패키지 조회를 위해 Watch Accuracy Meter package query 추가
- DB v5 → v6 migration: 기존 데이터 유지, `timegrapher_records.source` 열만 추가
- versionCode 9 / versionName 0.9.0

## v0.8.0
- Timegrapher 측정 엔진 v2 적용
  - 3개 주파수 대역 동시 분석
  - 500 Hz envelope autocorrelation 기반 BPH 후보 탐색
  - event interval consistency와 결합한 BPH 판정
  - 18,000 / 19,800 / 21,600 / 25,200 / 28,800 / 36,000 bph 자동 후보 유지
  - BPH lock / hysteresis 추가
  - lock 후 predicted beat window에서 adaptive threshold 완화
  - 약한 tick/tock 및 누락 beat 추적 강화
  - 일반 MIC 우선 + UNPROCESSED fallback
  - AudioTimestamp sample-clock 보정 유지
- 측정 결과 표시 속도 개선
  - 약 2~4초부터 잠정 BPH/Rate 표시
  - BPH 확정 시 LOCK 표시
  - 저장 조건을 최소 8초 / 18 유효 beat / signal 20%로 조정
- 자세별 오차 요약을 최근값에서 전체 평균으로 변경
  - 평균 Rate / Beat Error / Amplitude / 측정 횟수 표시
- 시계별 오차 한눈에 보기 팝업 추가
  - 전체 평균 / 자세별 평균 / 최근 6개 측정
- 컬렉션 각 시계 카드에 `오차` 버튼 추가
- 오차 탭에 `선택 시계 오차 한눈에 보기` 버튼 추가
- DB 스키마 변경 없음(DB v5 유지)
- 기존 applicationId / 서명키 유지: v0.7 위에 업데이트 설치 가능
- 기존 데이터 유지

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
