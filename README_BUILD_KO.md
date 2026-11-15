# Watch Collection v1.33.1 — APK 빌드와 설치

## 변경 사항
- 컬렉션 → 헤리티지 → 저장된 기록 선택 → **수정**
- 날짜, 연결 시계/직접 입력 시계, 제목, 내용 변경 가능
- 사진 추가 및 썸네일 우측 `×`를 이용한 개별 제거
- `변경 저장`을 눌러야 DB와 기존 사진 변경이 확정됩니다. `취소`는 기존 기록을 유지합니다.

## GitHub Actions를 이용해 APK 만들기

1. ZIP 압축을 해제합니다.
2. 압축 안의 `WatchCollection` 폴더 **내부 전체 내용**을 기존 GitHub 앱 저장소의 **최상위 디렉터리**에 업로드하거나 덮어씁니다. `.github` 숨김 폴더도 반드시 저장소 최상위에 포함합니다.
3. GitHub 저장소의 `Actions` 탭에서 `Build Watch Collection APK`를 선택하고 `Run workflow`를 실행합니다. `main`에 push해도 자동 빌드됩니다.
4. 빌드 완료 후 해당 실행의 `Artifacts`에서 `WatchCollection-v1.33.1-debug-apk`를 다운로드해 압축을 풉니다.
5. 생성된 `app-debug.apk`를 휴대전화에 옮기고 **기존 앱을 삭제하지 않은 상태에서 업데이트 설치**합니다.

## 버전·데이터 보존
- Android 패키지: `com.watchcollection.app`
- versionName: `1.33.1`, versionCode: `44` (기존 v1.33의 43보다 높음)
- DB 버전: `17` (변경 없음, 마이그레이션 불필요)
- `app/watchcollection-debug.keystore`: v1.33 소스의 기존 서명키 그대로 포함
- Google OAuth의 Android 인증서 SHA-1 변경 없음:
  `8A:4A:50:46:BB:18:77:84:69:14:8C:07:AB:8B:ED:47:60:23:9F:47`

## 검사 범위
- Java 전체 프로덕션 소스 구문 분석: `python3 tests/test_java_syntax.py`
- 헤리티지 DB 실제 Java 수정 메서드와 수정창 연결 검사: `python3 tests/test_heritage_edit.py`
- 기존 월간 착용 순위 회귀 검사: `python3 tests/test_monthly_wear_ranking.py`

**주의:** 제공된 소스 ZIP은 빌드·설치를 위한 소스이지 APK 파일이 아닙니다. 이 환경에는 Android SDK/Gradle이 없어 Android 전체 APK를 빌드하거나 기기에서 직접 설치해 검증하지 못했습니다. GitHub Actions의 실제 빌드 성공 여부를 확인한 뒤 설치해 주세요.
