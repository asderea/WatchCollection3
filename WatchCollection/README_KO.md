# Watch Collection v0.7

개인 시계 컬렉션, 타임그래퍼 오차 측정, 착용 캘린더, 스트랩 라이브러리를 한 앱에서 관리하는 Android 앱입니다.

## v0.7 핵심 변경

### 1. 마이크 기반 Timegrapher 오차 측정
- 기존 수동 오차 입력 UI를 제거했습니다.
- Android `AudioRecord`로 48 kHz 모노 마이크 신호를 직접 분석합니다.
- 고역통과 필터 + adaptive envelope/threshold로 기계식 시계의 escapement impulse를 검출합니다.
- Android `AudioTimestamp`의 frame/time 정보를 이용해 가능한 기기에서는 명목 48 kHz 오디오 clock 편차를 보정합니다.
- 다음 일반 BPH 후보를 자동 비교합니다.
  - 18,000
  - 19,800
  - 21,600
  - 25,200
  - 28,800
  - 36,000 bph
- BPH가 결정되면 beat index와 tick/tock parity를 함께 회귀해 다음 값을 계산합니다.
  - Rate (s/day)
  - Beat Error (ms)
  - BPH
- 한 beat 내부의 impulse span과 사용자가 입력한 Lift angle을 이용해 Amplitude(°)를 보조 추정합니다.
- 신호가 부족하거나 impulse span을 안정적으로 찾지 못하면 amplitude는 억지로 만들지 않고 `-`로 표시합니다.
- 최소 측정 시간/beat 수/신호 품질 조건을 만족해야 Save 버튼이 활성화됩니다.

### 2. Timegrapher UI
- 종이식 timegrapher / Watch Accuracy Meter 계열 UI를 참고해 Watch Collection 스타일로 재구성했습니다.
- 상단 rate scale, BPH/Rate/Beat Error/Amplitude 수치, 중앙 rate marker, timegrapher trace, signal quality를 한 화면에서 확인합니다.
- Start / Stop / Save 흐름으로 사용합니다.

### 3. 자세별 오차 저장
측정값 저장 시 아래 6개 자세 중 하나를 선택합니다.
- Dial up
- Dial down
- 12 Up
- 9 Up
- 6 Up
- 3 Up

각 시계별로 자세별 가장 최근 Rate / Beat Error / Amplitude를 별도 표시합니다.
측정 기록에는 BPH, Rate, Beat Error, Amplitude, Lift angle, 신호 품질, 측정 시간, 메모를 함께 저장합니다.

### 4. 시계 대표 사진 자동 배경 통일
- 대표 사진을 선택하면 원본 파일을 먼저 앱 내부에 별도 보관합니다.
- 이미지 모서리와 연결된 단색 배경만 flood-fill 방식으로 검출합니다.
- 시계 다이얼/핸즈/스트랩 등 본체 픽셀은 유지하면서 배경만 사용자가 지정한 `#FCFBF7`로 통일합니다.
- 흰 다이얼이나 검은 다이얼을 단순 색상 치환하지 않고, 이미지 가장자리와 연결된 영역만 배경으로 판단합니다.
- 배경이 복잡해 신뢰도가 낮으면 억지로 제거하지 않고 원본을 유지합니다.
- `원본 복구` 버튼으로 자동 보정 전 사진으로 되돌릴 수 있습니다.
- v0.6 이전에 이미 저장된 사진은 원본 백업 경로가 없으므로, 새로 사진을 선택한 이후부터 원본 복구 기능을 사용할 수 있습니다.

### 5. 컬렉션 정렬 / 필터 / Monthly Wear
- 컬렉션 기본 정렬을 **전체 착용 횟수(가중치) 내림차순**으로 변경했습니다.
  - 하루 한 시계 착용: 1.0회
  - 하루 두 시계 착용: 각 0.5회
- 착용 횟수가 같은 경우 브랜드 → 모델명 순으로 정렬합니다.
- 기존 전체/기계식/쿼츠 필터에 **브랜드 필터**를 추가했습니다.
- 브랜드 필터는 등록된 브랜드를 자동으로 모아 가로 스크롤 칩으로 표시합니다.
- Monthly Wear는 이번 달 TOP 10을 두 페이지로 나눕니다.
  - 1페이지: 1~5위
  - 2페이지: 6~10위
- 좌/우 화살표 버튼으로 두 페이지를 전환합니다.

### 6. 기존 기능 유지
- 정확성 우선 무료/독립형 스펙 검색
- 일요일 시작 월간 착용 캘린더
- 하루 최대 2개 착용 / 각각 0.5회 집계
- 올해/이번 달 TOP3
- 캘린더/랭킹/스트랩에서 컬렉션 시계로 이동
- 18/19/20/21/22 mm + 기타 스트랩 관리
- 스트랩 제작사/색상/재질/버클/메모 관리

## 데이터 마이그레이션
DB 버전은 5입니다.
- 기존 시계/스펙/착용/스트랩/예전 수동 오차 데이터는 삭제하지 않습니다.
- `watches` 테이블에는 대표사진 원본 경로 필드가 추가됩니다.
- `timegrapher_records` 테이블이 새로 추가됩니다.
- 예전 수동 오차 기록은 UI에서 새로 입력할 수 없지만 DB에는 보존됩니다.
- 컬렉션 카드의 평균 오차는 Timegrapher 측정값이 존재하면 이를 우선 사용하고, 아직 새 측정값이 없으면 예전 기록을 fallback으로 사용합니다.

## 측정 팁
- 완전히 또는 충분히 감은 기계식 시계를 사용하세요.
- 조용한 환경에서 측정하세요.
- 가능하면 휴대폰 마이크와 시계 크라운/케이스를 가깝게 두세요.
- 전문 timegrapher의 contact microphone보다 휴대폰 마이크는 주변 소음과 기기별 오디오 clock 영향을 더 받습니다.
- 정확한 amplitude를 위해서는 해당 칼리버의 Lift angle을 입력해야 합니다.

## 프로젝트 구조
배포 ZIP을 풀면 최상위 폴더가 반드시 `WatchCollection`입니다.
`.github/workflows/build-apk.yml`은 ZIP에 포함하지 않고 별도 복붙용으로 제공합니다.
