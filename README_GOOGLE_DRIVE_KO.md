# Watch Collection v1.33.1 — Google Drive 연결 안내

## 1. 연결 후 사용할 수 있는 것

앱이 휴대전화의 데이터를 내 Google Drive에 내보내고, ChatGPT가 해당 문서를 조회해 시계 정보를 확인하는 구성입니다. 데이터의 기준은 휴대전화 앱입니다. Drive 문서를 직접 편집한 내용이 앱으로 들어오지는 않으며, 다음 동기화에서 앱 내용으로 갱신됩니다.

| 저장 자료 | 내용 |
| --- | --- |
| `Watch Collection · 최신 컬렉션` Google 문서 | 현재 보유, Wish, 방출 후보, 시계 스펙·역할, 스트랩, 월간 순위, 전체 착용 기록, 저장된 취향 프로필, 사진 링크, 데이터 취득 시각 |
| `WatchCollection_latest.json` | DB 15개 테이블의 전체 행·타입·NULL·스키마와 저장된 평가 프로필, 사진 연결 정보 |
| `images` 폴더 | 현재 보유·Wish 시계의 원본/표시 사진, 현재 시계 케이스백, 보유 스트랩 사진 |

Google 문서와 JSON은 같은 파일을 갱신합니다. 사진 내용이 바뀌면 새 사진 파일을 만들고 문서의 링크를 갱신합니다. 처음 연결 시 선택한 Google 계정의 일반 내 Drive에 `Watch Collection` 폴더가 만들어집니다.

앱은 `drive.file` 범위로 앱이 생성한 파일을 다룹니다. Google Drive의 이 범위는 개별 파일 접근 권한입니다. [공식 권한 설명](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)

## 2. 수정 앱 설치

1. 빌드한 `WatchCollection_v1.33.1_debug.apk`를 휴대전화로 옮깁니다. APK가 제공되지 않은 경우 소스 ZIP을 기존 GitHub 프로젝트에 반영하고 포함된 `build-apk.yml`로 빌드합니다.
2. 설치 창에서 기존 앱을 업데이트합니다. 기존 앱을 삭제하는 절차는 필요하지 않습니다.
3. 앱에서 기존 컬렉션과 착용 기록을 확인합니다.

패키지는 `com.watchcollection.app`, 버전은 `1.33.1 / versionCode 44`, DB 스키마는 `17`입니다. 기존 v1.33 소스에 포함된 서명키로 빌드합니다. 앱 업데이트와 Google Cloud의 OAuth 등록에는 아래 서명 정보를 사용합니다.

## 3. Google Cloud 프로젝트 설정 — 처음 한 번

### 3-1. 프로젝트 만들기와 Drive API 사용 설정

1. [Google Cloud Console](https://console.cloud.google.com/)에 접속합니다.
2. 상단의 프로젝트 선택 메뉴에서 새 프로젝트를 만듭니다. 예: `Watch Collection Personal`.
3. 만든 프로젝트를 선택합니다.
4. `API 및 서비스 → 라이브러리`에서 `Google Drive API`를 검색하고 `사용`을 누릅니다.

API 사용 설정 절차: [Google 공식 안내](https://developers.google.com/workspace/guides/enable-apis)

### 3-2. Google Auth Platform 설정

1. 콘솔에서 `Google Auth Platform`을 엽니다. 처음이라면 시작 절차를 진행합니다.
2. `Branding`의 앱 이름은 `Watch Collection`, 지원·연락 이메일은 본인 이메일로 입력합니다.
3. 개인 Google 계정으로 쓰는 경우 `Audience`에서 외부 사용자 유형을 선택합니다. 테스트 상태라면 `Test users`에 실제 휴대전화에서 사용할 Google 계정을 추가합니다.
4. `Data Access → Add or Remove Scopes`에서 다음 권한을 추가합니다.

```
https://www.googleapis.com/auth/drive.file
```

5. 저장합니다. 콘솔이 요구하는 필수 앱 정보도 입력합니다.

동의 화면·테스트 사용자·권한 설정: [Google 공식 안내](https://developers.google.com/workspace/guides/configure-oauth-consent)

### 3-3. Android OAuth 클라이언트 만들기

`Google Auth Platform → Clients → Create client`에서 아래와 같이 등록합니다.

| 항목 | 값 |
| --- | --- |
| 애플리케이션 유형 | Android |
| 이름 | Watch Collection Android |
| 패키지명 | `com.watchcollection.app` |
| SHA-1 인증서 지문 | `8A:4A:50:46:BB:18:77:84:69:14:8C:07:AB:8B:ED:47:60:23:9F:47` |

이 SHA-1은 기존 v1.32 소스에 포함된 `app/watchcollection-debug.keystore`에서 확인한 값입니다. 다른 서명키를 사용하는 빌드라면 그 APK의 실제 서명 지문을 등록해야 합니다.

이번 구현은 Android AuthorizationClient가 패키지·서명 등록으로 사용자 동의를 받습니다. 앱에 클라이언트 비밀키를 넣거나 Cloudflare Worker에 Google 토큰을 설정하는 과정은 없습니다.

Android OAuth 클라이언트와 패키지·SHA-1 설정: [Google 공식 안내](https://developer.android.com/identity/authorization)

## 4. 휴대전화 앱에서 첫 동기화

1. `컬렉션` 페이지 맨 아래의 `Google Drive 연동`을 누릅니다.
2. `Google Drive 연결`을 누릅니다.
3. Google 계정을 선택하고 앱이 요청하는 Drive 파일 권한에 동의합니다.
4. 사진과 데이터 업로드가 끝날 때까지 기다립니다.
5. `마지막 성공`에 날짜·시각이 표시되는지 확인합니다.
6. `최신 문서 열기`를 눌러 보유 수, Wish 수, 스트랩 수와 최근 착용 기록을 확인합니다.
7. `Drive 폴더 · 사진 열기`에서 합성에 쓸 시계·스트랩 사진이 올라왔는지 확인합니다.

자동 갱신은 기본적으로 켜져 있습니다. 앱 화면이 열려 있는 동안 1분마다 변경 여부를 확인하며 변경된 경우 업로드합니다. 앱을 다시 열었을 때도 확인합니다. 앱을 종료한 동안 계속 실행되는 백그라운드 동기화는 포함하지 않습니다.

다른 채팅에서 최신 정보를 바로 사용하려면 앱에서 `지금 동기화`를 누르고 마지막 성공 시각을 확인하면 됩니다. 데이터가 그대로일 때는 업로드를 줄이고, 마지막 성공으로부터 한 시간이 지나면 원격 파일과 연결 상태를 다시 확인하며 갱신합니다.

## 5. ChatGPT에서 Google Drive 연결

앱이 Drive에 저장하는 연결과 ChatGPT가 Drive를 읽는 연결은 각각 필요합니다.

1. ChatGPT의 `Apps` 또는 `Plugins`에서 Google Drive를 찾습니다.
2. 앱에서 사용한 파일에 접근 가능한 Google 계정으로 연결합니다.
3. 요청되는 권한을 확인하고 Google 인증을 완료합니다.

OpenAI는 연결한 Google 계정이 접근할 수 있는 Google Docs·Sheets·Slides를 대화에서 검색하고 참조할 수 있다고 설명합니다. 해당 채팅 환경에서도 Google Drive 기능을 사용할 수 있어야 합니다. [OpenAI 공식 안내](https://help.openai.com/en/articles/10929079-google-drive-app-and-setup-in-chatgpt)

## 6. 기존 두 시계 채팅에서 사용

앱의 `Google Drive 연동 → 다른 채팅에 사용할 문구 복사`를 누릅니다. 복사된 문구에는 실제로 생성된 최신 Google 문서의 링크가 들어 있습니다.

아래 두 채팅에 각각 붙여넣습니다.

- 시계 스트랩 합성
- 시계 컬렉션 정리

최초에는 문서를 읽은 뒤 다음을 확인하도록 요청하면 됩니다.

> 이 문서의 데이터 취득 시각, 현재 보유 시계 수, Wish 수, 스트랩 수를 먼저 확인해줘. 현재 보유와 과거 착용 기록을 구분하고 앞으로 시계 정보를 확인할 때 이 문서의 최신 내용을 조회해줘.

스트랩 합성에는 다음처럼 요청할 수 있습니다.

> 최신 문서에서 이 시계의 러그폭과 해당 스트랩 정보를 확인하고, 문서에 연결된 시계·스트랩 사진이 실제로 열리는지 확인한 뒤 합성해줘.

Drive 파일을 갱신해도 기존 채팅의 과거 답변이 자동 수정되지는 않습니다. 새 요청에서 문서를 다시 조회해 최신 데이터로 답변합니다. 파일 이름이나 메타데이터만 확인했을 때는 내용을 읽었다고 볼 수 없으므로 최초 조회에서 실제 항목 수를 대조합니다. [OpenAI 파일 접근 안내](https://help.openai.com/en/articles/10929079-google-drive-app-and-setup-in-chatgpt)

## 7. 저장 범위와 상태 표시

- **기준 데이터:** 현재 보유는 `watches`, Wish는 `wish_watches`입니다. 방출 후보는 현재 보유 상태이며, 과거 착용 기록만 남은 시계는 현재 컬렉션과 구분합니다.
- **착용 순위:** 그 달의 `wear_ratio` 합계입니다. 0.5회 기록도 그대로 합산합니다. 동점은 시계명과 ID로 정렬합니다.
- **사진:** 앱에 실제 저장된 PNG/JPEG/WebP/GIF 중 20 MiB 이하 파일을 업로드합니다. 외부 이미지 URL은 문서에 외부 링크로 표시합니다. 파일이 없거나 업로드가 실패한 경우 문서와 앱에 확인사항을 남깁니다.
- **데이터 사본:** JSON에는 전체 DB 행이 들어가지만 자동 복원 기능은 포함하지 않습니다. 컬렉션 단체 사진, 착용 사진, 헤리티지 사진, 캘린더 전용 썸네일은 경로 정보만 포함하므로 이 JSON을 전체 사진 백업으로 사용하지 않습니다.
- **갱신 실패:** 성공 시각은 Google 문서까지 저장된 경우에만 바뀝니다. 실패하면 오류와 마지막 성공 시각을 함께 표시합니다. JSON과 문서는 `revision`으로 같은 동기화인지 비교할 수 있습니다.
- **연동 중지:** 앱의 다음 자동 업로드를 중지합니다. 기존 Drive 자료는 유지됩니다. 다시 `Google Drive 연결`로 시작할 수 있습니다.
- **비공개 사용:** 앱은 파일 공개 공유를 설정하지 않습니다. 본인의 Google 계정 접근 권한으로 사용합니다.

## 8. 자주 확인할 오류

| 증상 | 확인할 내용 |
| --- | --- |
| Android OAuth 설정 오류 / 오류 코드 10 | 패키지명과 설치한 APK의 SHA-1, Google Cloud의 Android 클라이언트 등록 |
| 동의 화면에서 접근 차단 | 테스트 사용자 이메일, 계정의 조직 정책, Drive API 사용 설정 |
| 첫 동기화가 실패 | 앱의 오류 메시지와 네트워크, Google 권한 상태 확인 후 다시 `지금 동기화` |
| 사진 일부 누락 | 앱에 해당 원본 파일이 남아 있는지, 사진 확인사항의 이유와 크기 제한 |
| ChatGPT가 문서 이름만 찾음 | 실제 문서 링크를 붙여넣고 내용 읽기를 요청. 연결 계정이 그 문서를 열 수 있는지 확인 |
| 앱에서 수정했는데 다른 채팅은 예전 정보 | 앱의 `지금 동기화` 성공 후 해당 채팅에서 문서를 다시 읽도록 요청 |

## 구현 근거

- Android AuthorizationClient: https://developer.android.com/identity/authorization
- Drive의 개별 파일 권한: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Drive 업로드 시 Google 문서 변환 및 기존 문서 내용 갱신: https://developers.google.com/workspace/drive/api/guides/manage-uploads
- ChatGPT의 Google Drive 연결·문서 조회: https://help.openai.com/en/articles/10929079-google-drive-app-and-setup-in-chatgpt

위 문서들의 지원 범위를 바탕으로 읽기용 자료를 native Google 문서로 생성했습니다. 휴대전화의 SQLite 파일을 Drive에 올리기만 하면 ChatGPT에서 바로 SQL 조회가 된다는 전제로 구현하지 않았습니다.
