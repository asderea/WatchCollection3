# Watch Collection v1.0

개인 시계 컬렉션, 착용 캘린더, 스트랩, 자세별 오차 기록을 관리하는 Android 앱입니다.

## v1.0 주요 변경

### 오차
- 자체 마이크 타임그래퍼 기능 완전 제거
- Watch Accuracy Meter 연동을 기본 측정 경로로 사용
- Measurement Setup의 `선택 시계 오차 한눈에 보기` 버튼 제거
- `전체 시계 Rate 평균 보기` 팝업 추가
  - 시계 대표 사진 + 브랜드/이름
  - 시계별 전체 Rate 평균
  - 기록된 자세들의 평균 Rate
  - Dial up / Dial down / 12 Up / 9 Up / 6 Up / 3 Up 자세별 평균 Rate와 측정 횟수
- POSITIONAL RATE 카드의 `오차 한눈에 보기`는 유지
- 선택 시계 상세 오차 팝업에 전체 평균 Rate + 자세 평균 Rate + 6개 자세별 평균 표시
- 기존 자체 타임그래퍼 기록은 데이터 보존을 위해 삭제하지 않고 `기존 기록`으로 읽기만 함

### 컬렉션
- 기본 정렬은 기존과 동일하게 전체 착용 횟수 내림차순
- Monthly Wear를 한 화면 3개씩 표시
  - 1–3위 / 4–6위 / 7–9위 / 10위
  - 좌우 화살표로 이동
- Power 필터: All / Automatic / Mechanical / Quartz
  - Solar-Quartz는 Quartz 필터에 포함
- Brand 필터 유지

### 스펙
- 기존 DB의 `movement` 필드는 화면에서 `Power`로 사용
  - Automatic / Mechanical / Quartz / Solar-Quartz 중 선택
- 기존 DB의 `caliber` 필드는 화면에서 `Movement`로 사용
- 상세 스펙 입력 라벨을 영어로 통일
  - Model / Brand / Reference / Power / Movement / Diameter / Lug-to-lug / Thickness / Lug width / Power reserve / WR / Crystal / Case material / Warranty / Source URLs / Notes
- 간략 스펙에서 방수는 `WR` 접두어와 함께 표시
- Warranty 만료일 저장 추가
  - `~YYYY-MM-DD` 형태로 표시
  - 별도 DatePicker 팝업에서 선택
  - 착용 캘린더의 월간 캘린더와는 별개

## 데이터 유지
- applicationId: `com.watchcollection.app` 유지
- 동일 debug 서명키 유지
- DB v6 → v7 migration
- 기존 시계/사진/착용/스트랩/오차 데이터 유지
- watches 테이블에 `warranty_end_date`만 추가

## 빌드
저장소 루트에 `.github/workflows/build-apk.yml`을 별도로 만들고 제공된 workflow를 붙여넣어 GitHub Actions에서 빌드합니다.
