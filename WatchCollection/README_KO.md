# Watch Collection v0.9

개인 시계 컬렉션, 오차 기록, 착용 캘린더, 스트랩 라이브러리를 한 앱에서 관리하는 Android 앱입니다.

## v0.9 핵심 변경

### 1. Watch Accuracy Meter 연동을 기본 오차 측정 경로로 추가
- 오차 탭에서 선택한 시계를 기준으로 `Watch Accuracy Meter로 측정` 버튼을 제공합니다.
- Watch Accuracy Meter가 설치되어 있으면 해당 앱을 바로 실행합니다.
- 설치되어 있지 않으면 Google Play의 Watch Accuracy Meter 페이지를 엽니다.
- Watch Accuracy Meter에서 측정을 마치고 Watch Collection으로 돌아오면 측정 결과 입력 팝업이 자동으로 열립니다.
- 자동 팝업을 놓쳤거나 앱 프로세스가 종료된 경우를 대비해 `측정 결과 입력` 버튼도 항상 제공합니다.

### 2. Watch Accuracy Meter 결과를 자세별 기록으로 저장
입력 가능한 값:
- BPH
- Rate (s/day)
- Beat Error (ms)
- Amplitude (°) · 선택 입력
- 자세
  - Dial up
  - Dial down
  - 12 Up
  - 9 Up
  - 6 Up
  - 3 Up
- 측정 날짜
- 메모

일반 BPH 후보는 다음을 안내합니다.
- 18,000
- 19,800
- 21,600
- 25,200
- 28,800
- 36,000 bph

### 3. 측정 출처 구분
- 기존 자체 타임그래퍼 측정값은 `자체 측정`으로 저장됩니다.
- Watch Accuracy Meter에서 옮긴 결과는 `Watch Accuracy Meter` 출처로 저장됩니다.
- 최근 측정 목록과 시계별 오차 팝업에서 출처를 구분해 표시합니다.
- 시계별 오차 한눈에 보기에는 전체 측정 횟수와 Watch Accuracy Meter 기록 횟수를 함께 표시합니다.
- 자세별 평균/전체 평균 계산에는 두 출처의 저장 기록이 모두 반영됩니다.

### 4. 자체 타임그래퍼는 실험적 보조 기능으로 유지
- v0.8의 멀티밴드 + autocorrelation + BPH Lock 엔진을 그대로 유지합니다.
- UI에서 `실험적 자체 타임그래퍼`로 명확히 구분합니다.
- Lift angle은 자체 측정의 amplitude 계산에만 사용합니다.

### 5. 기존 기능 유지
- 사진 선택 즉시 배경 `#FCFBF7` 자동 통일 + 원본 복구
- 컬렉션 착용 횟수순 기본 정렬
- 브랜드 필터
- Monthly Wear 1~5위 / 6~10위 화살표 전환
- 일요일 시작 착용 캘린더
- 하루 2개 시계 착용 시 각각 0.5회
- 자세별 평균 오차
- 시계별 오차 한눈에 보기 팝업
- 스트랩 18/19/20/21/22 mm + 기타 / 제작사 저장

## 데이터 보존
- `applicationId`: `com.watchcollection.app` 유지
- 기존 debug 서명키 유지
- DB 버전: 6
- v0.8 DB(v5)에서 `timegrapher_records.source` 열만 추가하는 migration을 수행합니다.
- 기존 시계/스펙/사진/착용/스트랩/오차 기록은 삭제하지 않습니다.
- 기존 앱을 삭제하지 말고 v0.9 APK를 **업데이트 설치**해야 데이터가 유지됩니다.

## Watch Accuracy Meter 연동 방식의 한계
Watch Accuracy Meter가 측정 결과를 다른 앱으로 직접 전달하는 공개 API/Intent 결과 인터페이스는 확인되지 않았기 때문에, Watch Collection이 BPH/Rate/Beat Error/Amplitude 숫자를 자동으로 읽어오지는 않습니다. 측정 후 해당 앱 화면의 값을 Watch Collection 팝업에 입력하는 방식입니다.

## 프로젝트 구조
배포 ZIP을 풀면 최상위 폴더가 반드시 `WatchCollection`입니다.
`.github/workflows/build-apk.yml`은 ZIP에 포함하지 않고 별도 복붙용으로 제공합니다.
