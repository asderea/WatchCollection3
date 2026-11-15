#!/usr/bin/env python3
"""실제 Drive 클라이언트를 메모리 HTTP 응답으로 검사한다.

실행: python3 tests/test_google_drive_client.py
필요: Python 3, Java 17 이상. org.json 준비 방식은 스냅샷 검사와 공유한다.
오프라인 환경에서는 WATCH_TEST_JSON_JAR에 json-20240303.jar 경로를 지정한다.
Google 계정, 실제 Drive API, OAuth 동의 화면에는 연결하지 않는다.
따라서 이 검사는 실제 Android 기기의 인증·업로드 전체 검증을 대신하지 않는다.
"""
from pathlib import Path
import os
import subprocess
import tempfile

from test_snapshot_exporter import prepare_fixture


PROJECT = Path(__file__).resolve().parents[1]


def main():
    """공통 Android/DB 더블과 실제 exporter에 실제 Drive 클라이언트를 함께 올린다."""
    production = PROJECT / "app/src/main/java/com/watchcollection/app/GoogleDriveClient.java"
    harness = PROJECT / "tests/fixtures/drive/com/watchcollection/app/CheckDriveClient.java"
    if not production.is_file() or not harness.is_file():
        raise RuntimeError("Drive 클라이언트 소스 또는 테스트 fixture를 찾지 못했습니다.")
    with tempfile.TemporaryDirectory(prefix="watchcollection-drive-test-") as directory:
        root = Path(directory)
        fixture = prepare_fixture(root)
        classpath = os.pathsep.join((str(fixture["classes"]), str(fixture["json_jar"])))
        subprocess.run(
            fixture["compiler"]
            + ["-encoding", "UTF-8", "-source", "17", "-target", "17", "-Xlint:-options",
               "-cp", classpath, "-d", str(fixture["classes"]), str(production), str(harness)],
            check=True,
        )
        # 사진 fixture도 임시 디렉터리에 만들고 검사 종료 후 함께 정리한다.
        subprocess.run(
            [fixture["java"], "-Djava.io.tmpdir=" + str(root), "-cp", classpath,
             "com.watchcollection.app.CheckDriveClient"],
            check=True,
        )


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        raise SystemExit("Drive 클라이언트 검사 실패: " + str(error))
