# Watch Collection v1.1

개인 시계 컬렉션, 착용 캘린더, 스트랩, 자세별 오차 기록을 관리하는 Android 앱입니다.

## v1.1 주요 변경

### 오차
- Watch Accuracy Meter 기록 삭제 기능 추가
  - 최근 측정 카드에서 개별 기록 삭제
  - 시계별 오차 팝업의 최근 기록에서도 삭제 가능
  - 삭제 즉시 전체 평균/자세별 평균에 재반영
- 오차 화면 상단의 설명 문구 및 `외부 측정 연동` 설명 문구 제거
- 자세별 평균 표시에서 `n=측정횟수` 표기 제거
- `전체 시계 Rate 평균` 팝업 상단에 Rate 비교 그래프 추가
  - 작은 시계 사진 + 시계 이름 + 평균 Rate를 한 줄에 표시
  - 0 s/day 중앙선을 기준으로 + / - Rate를 막대로 시각화
- 전체 시계 Rate 평균 정렬
  - 기록이 있는 시계: `|평균 Rate|`가 작은 순
  - 기록이 없는 시계: 가장 뒤
  - 기록이 없는 시계끼리는 전체 착용 횟수가 많은 순
- POSITIONAL RATE의 `오차 한눈에 보기`는 유지

### 컬렉션
- 컬렉션 수량 제한 없음
- Monthly Wear를 등록/착용 시계 수에 맞춰 3개씩 무제한 페이지 표시
- Power / Brand 필터를 칩 대신 Spinner 드롭다운으로 변경
  - 기본값: `All / All`
  - Power: All / Automatic / Mechanical / Quartz
  - Brand: All / 등록된 브랜드 / 미지정
  - Solar-Quartz는 Quartz 필터에 포함
- 기본 시계 정렬은 전체 착용 횟수 내림차순 유지

### 스트랩
- Lug width + Material 이중 필터 추가
- Lug width: All / 18 / 19 / 20 / 21 / 22 mm / 기타
- Material: All / Leather / Bracelet / Rubber / Nato / Mesh / 기타
- 스트랩 저장 시 Material도 위 분류 중 하나를 선택
- 각 러그폭의 호환 시계는 전체 착용 횟수가 많은 순서로 표시

## 데이터 유지
- applicationId: `com.watchcollection.app` 유지
- 동일 debug 서명키 유지
- DB version 7 유지
- 기존 시계/사진/착용/스트랩/오차 데이터 유지
- 이번 버전은 DB 스키마 변경 없음

## 빌드
저장소 루트에 `.github/workflows/build-apk.yml`을 별도로 만들고 제공된 workflow를 붙여넣어 GitHub Actions에서 빌드합니다.
