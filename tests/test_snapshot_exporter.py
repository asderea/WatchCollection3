#!/usr/bin/env python3
"""실제 exporter 소스와 실제 org.json으로 데이터 내보내기 경계를 검사한다.

실행: python3 tests/test_snapshot_exporter.py
필요: Python 3, Java 17 이상. Android/DB 연결은 테스트 더블을 사용한다.
org.json 20240303 JAR은 공식 Maven Central에서 받고 SHA-256을 검증한다.
오프라인 환경에서는 WATCH_TEST_JSON_JAR에 같은 JAR의 경로를 지정한다.
APK 빌드와 실제 Google Drive 업로드를 대신하는 검사는 아니다.
"""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile
import urllib.error
import urllib.request

PROJECT = Path(__file__).resolve().parents[1]
JSON_VERSION = "20240303"
JSON_SHA256 = "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"
JSON_URL = "https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar"

STUBS = {
    "android/content/SharedPreferences.java": '''package android.content;
public interface SharedPreferences {
 String getString(String k,String fallback);
 default long getLong(String k,long fallback){return fallback;}
 default int getInt(String k,int fallback){return fallback;}
 default boolean getBoolean(String k,boolean fallback){return fallback;}
 default boolean contains(String k){return false;}
 default java.util.Map<String,?> getAll(){return java.util.Collections.emptyMap();}
 default Editor edit(){throw new UnsupportedOperationException("이 테스트 설정은 읽기 전용");}
 interface Editor {
  Editor putString(String k,String v);Editor putLong(String k,long v);Editor putBoolean(String k,boolean v);
  Editor remove(String k);boolean commit();void apply();
 }
}
''',
    "android/content/Context.java": '''package android.content;
import java.io.File;
public class Context {
 public static final int MODE_PRIVATE=0; public final File root; public String accessed="",profileRaw="";
 public Context(File root){this.root=root;} public Context getApplicationContext(){return this;}
 public File getFilesDir(){return root;}
 public SharedPreferences getSharedPreferences(String name,int mode){accessed=name;if(!name.equals("collection_evaluation_profile"))throw new AssertionError("다른 설정 접근");return (k,d)->k.equals("profile_json")?profileRaw:d;}
 public String getPackageName(){return "com.watchcollection.app";}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
}''',
    "android/content/pm/PackageInfo.java": '''package android.content.pm;
public class PackageInfo {public String versionName="1.33.0";}''',
    "android/content/pm/PackageManager.java": '''package android.content.pm;
public class PackageManager {public static class NameNotFoundException extends Exception{} public PackageInfo getPackageInfo(String p,int f)throws NameNotFoundException{return new PackageInfo();}}''',
    "android/database/Cursor.java": '''package android.database;
public interface Cursor extends AutoCloseable {
 int FIELD_TYPE_NULL=0,FIELD_TYPE_INTEGER=1,FIELD_TYPE_FLOAT=2,FIELD_TYPE_STRING=3,FIELD_TYPE_BLOB=4;
 boolean moveToNext(); int getColumnCount(); int getType(int i); long getLong(int i); double getDouble(int i);
 byte[] getBlob(int i); String getString(int i); String getColumnName(int i); void close();
}''',
    "android/database/sqlite/SQLiteDatabase.java": '''package android.database.sqlite;
import android.database.Cursor; import java.util.*; import org.json.*;
public class SQLiteDatabase {
 public static final Map<String,List<Map<String,Object>>> TABLES=new LinkedHashMap<>();
 public static boolean inTransaction=false,closed=false;
 public void beginTransactionNonExclusive(){inTransaction=true;} public void endTransaction(){inTransaction=false;}
 public void setTransactionSuccessful(){} public int getVersion(){return 17;}
 public Cursor rawQuery(String sql,String[] args){
  if(!inTransaction)throw new AssertionError("읽기 트랜잭션 누락");
  List<Map<String,Object>> data;
  if(sql.startsWith("SELECT * FROM "))data=TABLES.getOrDefault(sql.split(" ")[3],Collections.emptyList());
  else if(sql.startsWith("SELECT name,seq"))data=Collections.singletonList(row("name","watches","seq",99L));
  else data=Collections.singletonList(row("type","table","name",args[0],"tbl_name",args[0],"sql","CREATE TABLE "+args[0]));
  final List<Map<String,Object>> rows=data;
  return new Cursor(){ int pos=-1; final List<String> columns=rows.isEmpty()?new ArrayList<>():new ArrayList<>(rows.get(0).keySet());
   Object val(int i){return rows.get(pos).get(columns.get(i));}
   public boolean moveToNext(){return ++pos<rows.size();} public int getColumnCount(){return columns.size();}
   public int getType(int i){Object v=val(i);return v==null?0:v instanceof byte[]?4:v instanceof Double||v instanceof Float?2:v instanceof Number?1:3;}
   public long getLong(int i){return ((Number)val(i)).longValue();}public double getDouble(int i){return ((Number)val(i)).doubleValue();}
   public byte[] getBlob(int i){return (byte[])val(i);}public String getString(int i){return (String)val(i);}
   public String getColumnName(int i){return columns.get(i);}public void close(){}
  };
 }
 public static Map<String,Object> row(Object... v){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)r.put((String)v[i],v[i+1]);return r;}
}''',
    "android/net/Uri.java": '''package android.net;
public class Uri {private final String s;private Uri(String s){this.s=s;} public static Uri parse(String s){return new Uri(s);} public String getPath(){return java.net.URI.create(s).getPath();}}
''',
    "android/util/Base64.java": '''package android.util;
public final class Base64 {public static final int NO_WRAP=2;public static String encodeToString(byte[] b,int flags){return java.util.Base64.getEncoder().encodeToString(b);}}
''',
    "com/watchcollection/app/DatabaseHelper.java": '''package com.watchcollection.app;
import android.content.Context; import android.database.sqlite.SQLiteDatabase;import java.util.*;
public class DatabaseHelper {
 public static class WearStat {public long watchId;public String watchName="";public double score;}
 public static final List<WearStat> STATS=new ArrayList<>();public DatabaseHelper(Context c){}
 public SQLiteDatabase getReadableDatabase(){return new SQLiteDatabase();}public void close(){SQLiteDatabase.closed=true;}
 public List<WearStat> getTopWorn(String s,String e,int limit){return new ArrayList<>(STATS);}
}''',
    "com/watchcollection/app/CheckSnapshot.java": '''package com.watchcollection.app;
import android.content.Context;import android.database.sqlite.SQLiteDatabase;import java.nio.file.*;import java.util.*;import org.json.*;
public class CheckSnapshot {
 static int checks=0;static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);checks++;}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("snapshot-fixtures");Path photo=root.resolve("saved.png");
  byte[] png=new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0,1};Files.write(photo,png);
  Path outside=Files.createTempFile("outside",".png");Files.write(outside,png);
  List<Map<String,Object>> watches=new ArrayList<>();
  watches.add(SQLiteDatabase.row("id",1L,"brand","Owned Brand","model_name","Owned One","reference_no",null,"original_image_url",photo.toString(),"representative_image_url","https://example.test/watch.jpg","caseback_image_url",outside.toString(),"created_at",100L));
  watches.add(SQLiteDatabase.row("id",4L,"brand","Owned Brand","model_name","Owned Four","reference_no","R4","original_image_url","","representative_image_url","","caseback_image_url","","created_at",400L));
  watches.add(SQLiteDatabase.row("id",6L,"brand","Owned Brand","model_name","Owned Six","reference_no","R6","original_image_url","","representative_image_url","","caseback_image_url","","created_at",600L));
  SQLiteDatabase.TABLES.put("watches",watches);
  SQLiteDatabase.TABLES.put("wish_watches",Collections.singletonList(SQLiteDatabase.row("id",11L,"brand","Wish Brand","model_name","Wish One","original_image_url","content://unavailable/photo","replace_watch_id",1L)));
  SQLiteDatabase.TABLES.put("straps",Collections.singletonList(SQLiteDatabase.row("id",21L,"name","Blue strap","width_mm",20L,"image_path",root.resolve("missing.jpg").toString())));
  SQLiteDatabase.TABLES.put("wear_records",Collections.singletonList(SQLiteDatabase.row("id",31L,"watch_id",9L,"watch_name_snapshot","Archived Nine","wear_date","2026-11-14","wear_ratio",0.5,"slot",1L,"note","saved note")));
  SQLiteDatabase.TABLES.put("wish_release_candidates",Collections.singletonList(SQLiteDatabase.row("watch_id",4L,"created_at",400L)));
  for(int id=9;id>=1;id--){DatabaseHelper.WearStat s=new DatabaseHelper.WearStat();s.watchId=id;s.watchName="Watch "+id;s.score=10-id;DatabaseHelper.STATS.add(s);}
  Context c=new Context(root.toFile());CollectionSnapshotExporter.Export first=CollectionSnapshotExporter.capture(c);
  check(SQLiteDatabase.closed&&!SQLiteDatabase.inTransaction,"DB 연결/트랜잭션 정리");
  check(c.accessed.equals("collection_evaluation_profile"),"평가 프로필 이외 설정 접근 금지");
  check(first.json.optJSONObject("tables").length()==15,"전체 15개 사용자 테이블");
  check(first.json.optString("app_version").equals("1.33.0")&&first.json.optString("application_id").equals("com.watchcollection.app"),"앱 식별 메타데이터");
  check(first.json.optJSONObject("profile").length()==0,"미저장 프로필 기본값 채우기 금지");
  check(first.json.optJSONObject("tables").optJSONArray("watches").optJSONObject(0).get("reference_no")==JSONObject.NULL,"null 보존");
  check(first.json.optJSONObject("tables").optJSONArray("wear_records").optJSONObject(0).optDouble("wear_ratio",0)==0.5,"실수 값 보존");
  check(first.json.optJSONArray("sqlite_sequence").optJSONObject(0).optLong("seq")==99L,"삭제 후 자동 ID 진행 상태 보존");
  check(first.images.size()==1,"허용된 로컬 사진만 대상으로 선택");
  check(first.warnings.size()==3,"앱 밖/없는 파일/content URI 경고");
  Map<String,String> links=new HashMap<>();links.put(photo.toString(),"https://drive.google.com/file/d/test-photo/view");
  String doc=first.toDocument(links,Arrays.asList("upload fixture warning"));
  check(doc.contains("현재 보유 3점 / 위시 1점 / 스트랩 1개"),"보유/위시 분리 집계");
  check(doc.contains("9위 · Watch 9")&&doc.contains("현재 컬렉션에 없음(과거 기록)"),"누락 없이 과거 순위 표시");
  check(doc.indexOf("1위 · Watch 1")<doc.indexOf("2위 · Watch 2"),"DB 순서와 무관하게 순위 정렬");
  check(doc.contains("방출 후보로 지정됨(방출 완료 아님)"),"방출 후보를 실제 방출로 해석하지 않음");
  check(doc.contains("Archived Nine")&&doc.contains("비율 0.5"),"과거 착용 스냅샷/가중치 보존");
  check(doc.contains("https://drive.google.com/file/d/test-photo/view"),"실제 업로드 링크 매핑");
  check(doc.contains("https://example.test/watch.jpg (외부 URL"),"외부 이미지는 다운로드 없이 링크");
  check(doc.contains("upload fixture warning")&&doc.contains("전체 사진 백업이 아니다"),"업로드 경고/범위 표시");
  check(!doc.contains("Salmon")&&!doc.contains("Pelagos"),"기억 기반 미저장 값 주입 없음");
  CollectionSnapshotExporter.Export same=CollectionSnapshotExporter.capture(c);
  check(first.fingerprint.equals(same.fingerprint),"취득 시각이 달라도 같은 데이터 지문");
  png[png.length-1]=2;Files.write(photo,png);
  CollectionSnapshotExporter.Export changed=CollectionSnapshotExporter.capture(c);
  check(!first.fingerprint.equals(changed.fingerprint),"같은 경로 사진 콘텐츠 변경 감지");
  watches.get(0).put("model_name","Edited One");CollectionSnapshotExporter.Export edited=CollectionSnapshotExporter.capture(c);
  check(!changed.fingerprint.equals(edited.fingerprint),"DB 값 변경 감지");
  check(edited.toDocument(Collections.emptyMap(),Collections.emptyList()).contains("Drive 링크를 만들지 못함"),"사진 업로드 누락을 접근 가능으로 표시하지 않음");
  c.profileRaw="{\\\"movement_preference\\\":\\\"Mechanical\\\",\\\"unexpected_api_key\\\":\\\"secret-fixture\\\"}";
  CollectionSnapshotExporter.Export saved=CollectionSnapshotExporter.capture(c);
  check(saved.json.optJSONObject("profile").optString("movement_preference").equals("Mechanical"),"실제 JSON 라이브러리로 저장 프로필 읽기");
  check(!saved.json.toString().contains("secret-fixture"),"프로필에서도 허용되지 않은 필드 내보내기 금지");
  c.profileRaw="{broken";CollectionSnapshotExporter.Export broken=CollectionSnapshotExporter.capture(c);
  check(broken.json.optString("profile_status").equals("invalid")&&broken.json.optJSONObject("profile").length()==0,"손상된 프로필 경고/기본값 대체 금지");
  check(new JSONObject(edited.json.toString()).optJSONObject("tables").optJSONArray("wear_records").length()==1,"실제 JSON 직렬화 재파싱");
  System.out.println("SNAPSHOT_CHECKS="+checks+" PASS");
 }
}''',
}


def get_json_jar():
    """허용된 버전의 실제 JSON 라이브러리만 테스트 클래스 경로에 넣는다."""
    override = os.environ.get("WATCH_TEST_JSON_JAR")
    if override:
        jar = Path(override).expanduser().resolve()
        if not jar.is_file():
            raise RuntimeError("WATCH_TEST_JSON_JAR이 가리키는 JAR 파일이 없습니다.")
    else:
        cache = Path(tempfile.gettempdir()) / "watchcollection-test-deps"
        cache.mkdir(parents=True, exist_ok=True)
        jar = cache / ("json-" + JSON_VERSION + ".jar")
        if not jar.is_file() or hashlib.sha256(jar.read_bytes()).hexdigest() != JSON_SHA256:
            try:
                with urllib.request.urlopen(JSON_URL, timeout=30) as response:
                    data = response.read(1024 * 1024)
            except (OSError, urllib.error.URLError) as error:
                raise RuntimeError(
                    "org.json 의존성을 받지 못했습니다. 인터넷 연결을 확인하거나 "
                    "WATCH_TEST_JSON_JAR에 json-20240303.jar의 경로를 지정하세요."
                ) from error
            if hashlib.sha256(data).hexdigest() != JSON_SHA256:
                raise RuntimeError("다운로드한 org.json JAR의 SHA-256이 일치하지 않습니다.")
            with tempfile.NamedTemporaryFile(dir=cache, delete=False) as download:
                download.write(data)
                temporary = Path(download.name)
            temporary.replace(jar)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != JSON_SHA256:
        raise RuntimeError("org.json JAR의 버전 또는 SHA-256이 지정값과 다릅니다.")
    return jar


def prepare_fixture(build_dir):
    """실제 exporter와 공통 Android 테스트 더블을 지정한 임시 폴더에 컴파일한다."""
    java = shutil.which("java")
    if not java:
        raise RuntimeError("Java 17 이상이 필요합니다. java 실행 파일을 PATH에 추가하세요.")
    jar = get_json_jar()
    production_source = PROJECT / "app/src/main/java/com/watchcollection/app/CollectionSnapshotExporter.java"
    if not production_source.is_file():
        raise RuntimeError("프로젝트의 CollectionSnapshotExporter.java를 찾지 못했습니다.")
    root = Path(build_dir)
    root.mkdir(parents=True, exist_ok=True)
    for name, contents in STUBS.items():
        path = root / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")
    sources = sorted(str(p) for p in (root / "src").rglob("*.java"))
    sources.append(str(production_source))
    classes = root / "classes"
    classes.mkdir(exist_ok=True)
    compiler = [shutil.which("javac")] if shutil.which("javac") else [java, "com.sun.tools.javac.Main"]
    # 일부 경량 JDK에는 ct.sym이 없으므로 --release 대신 언어/바이트코드 수준을 지정한다.
    subprocess.run(compiler + ["-encoding", "UTF-8", "-source", "17", "-target", "17",
                              "-Xlint:-options", "-cp", str(jar), "-d", str(classes), *sources], check=True)
    return {"classes": classes, "json_jar": jar, "java": java,
            "compiler": compiler, "source_dir": root / "src"}


def main():
    with tempfile.TemporaryDirectory(prefix="watchcollection-snapshot-test-") as directory:
        prepared = prepare_fixture(directory)
        subprocess.run([prepared["java"], "-Djava.io.tmpdir=" + directory, "-cp",
                        str(prepared["classes"]) + os.pathsep + str(prepared["json_jar"]),
                        "com.watchcollection.app.CheckSnapshot"], check=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        raise SystemExit("스냅샷 검사 실패: " + str(error))
