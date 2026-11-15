"""실제 DatabaseHelper.java의 헤리티지 수정 메서드를 추출해 JVM에서 회귀 검사합니다.

Android SDK 없이도 트랜잭션/기존 사진 보존/실패 시 롤백을 확인할 수 있도록
필요한 SQLite 인터페이스를 메모리 테스트 더블로 구성합니다.
"""
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DB = (ROOT / 'app/src/main/java/com/watchcollection/app/DatabaseHelper.java').read_text()
UI = (ROOT / 'app/src/main/java/com/watchcollection/app/MainActivity.java').read_text()


def extract_method(source, name):
    start = source.index('public boolean ' + name + '(')
    body_start = source.index('{', start)
    depth = 0
    for index in range(body_start, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError('Java method not closed')


JAVA = r'''
import java.util.*;

class ContentValues extends HashMap<String, Object> {
    void put(String key, String value) { super.put(key, value); }
    void put(String key, long value) { super.put(key, value); }
    void put(String key, int value) { super.put(key, value); }
}

class Cursor {
    final List<Object[]> rows;
    int position = -1;
    Cursor(List<Object[]> rows) { this.rows = rows; }
    boolean moveToNext() { return ++position < rows.size(); }
    long getLong(int index) { return (Long) rows.get(position)[index]; }
    String getString(int index) { return (String) rows.get(position)[index]; }
    void close() { }
}

class SQLiteDatabase {
    Map<Long, Map<String,Object>> entries = new HashMap<>();
    Map<Long, Map<String,Object>> photos = new LinkedHashMap<>();
    Map<Long, Map<String,Object>> backupEntries, backupPhotos;
    boolean successful;
    long nextPhotoId = 100;

    static Map<Long, Map<String,Object>> copy(Map<Long, Map<String,Object>> src) {
        Map<Long, Map<String,Object>> out = new LinkedHashMap<>();
        for(Map.Entry<Long,Map<String,Object>> row:src.entrySet()) {
            out.put(row.getKey(), new HashMap<>(row.getValue()));
        }
        return out;
    }
    void beginTransaction() {
        successful = false;
        backupEntries = copy(entries);
        backupPhotos = copy(photos);
    }
    void setTransactionSuccessful() { successful = true; }
    void endTransaction() {
        if(!successful) { entries = backupEntries; photos = backupPhotos; }
    }
    int update(String table, ContentValues values, String where, String[] args) {
        long id = Long.parseLong(args[0]);
        Map<Long,Map<String,Object>> target = table.equals("heritage_entries") ? entries : photos;
        Map<String,Object> row = target.get(id);
        if(row == null) return 0;
        if(table.equals("heritage_photos") && ((Long)row.get("entry_id")) != Long.parseLong(args[1])) return 0;
        row.putAll(values);
        return 1;
    }
    Cursor rawQuery(String sql, String[] args) {
        long entryId = Long.parseLong(args[0]);
        List<Map.Entry<Long,Map<String,Object>>> found = new ArrayList<>();
        for(Map.Entry<Long,Map<String,Object>> row:photos.entrySet()) {
            if(((Long)row.getValue().get("entry_id")) == entryId) found.add(row);
        }
        found.sort((a,b)->{
            int c = Integer.compare((Integer)a.getValue().get("display_order"),
                    (Integer)b.getValue().get("display_order"));
            return c != 0 ? c : Long.compare(a.getKey(), b.getKey());
        });
        List<Object[]> out = new ArrayList<>();
        for(Map.Entry<Long,Map<String,Object>> row:found) {
            out.add(new Object[]{row.getKey(), (String)row.getValue().get("image_path")});
        }
        return new Cursor(out);
    }
    int delete(String table, String where, String[] args) {
        long id = Long.parseLong(args[0]);
        Map<String,Object> row = photos.get(id);
        if(row == null || (Long)row.get("entry_id") != Long.parseLong(args[1])) return 0;
        photos.remove(id);
        return 1;
    }
    long insertOrThrow(String table, String unused, ContentValues values) {
        String path=(String)values.get("image_path");
        if(path.equals("FAIL")) throw new IllegalStateException("forced photo insertion failure");
        long id=nextPhotoId++;
        Map<String,Object> row=new HashMap<>(values);
        row.put("id",id);
        photos.put(id,row);
        return id;
    }
}

public class HeritageHarness {
    final SQLiteDatabase sqlite = new SQLiteDatabase();
    final List<String> deletedFiles = new ArrayList<>();
    SQLiteDatabase getWritableDatabase() { return sqlite; }
    void deleteInternalFile(String path) { deletedFiles.add(path); }

    ACTUAL_METHOD

    static void ensure(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
    static Map<String,Object> row(Object... pairs) {
        Map<String,Object> values=new HashMap<>();
        for(int i=0;i<pairs.length;i+=2) values.put((String)pairs[i],pairs[i+1]);
        return values;
    }
    public static void main(String[] args) {
        HeritageHarness h=new HeritageHarness();
        h.sqlite.entries.put(7L, row("id",7L,"created_at",123L,
                "event_date","2026-10-01","watch_id",10L,"title","기존 제목",
                "content","기존 내용","watch_name_snapshot","옛 시계"));
        h.sqlite.photos.put(11L,row("id",11L,"entry_id",7L,"image_path","old11.png","display_order",0));
        h.sqlite.photos.put(12L,row("id",12L,"entry_id",7L,"image_path","old12.png","display_order",1));
        h.sqlite.photos.put(50L,row("id",50L,"entry_id",8L,"image_path","other50.png","display_order",0));

        // 수정: 기존 ID/생성 시각 유지, 기존 사진 하나 보존, 하나 제거, 새 사진 추가
        ensure(h.updateHeritageEntry(7L,"2026-11-15",0L,"직접 입력","",
                "수정 제목","수정 내용",Arrays.asList(12L),Arrays.asList("new.png")),"edit failed");
        ensure(h.sqlite.entries.get(7L).get("title").equals("수정 제목"),"title");
        ensure(h.sqlite.entries.get(7L).get("event_date").equals("2026-11-15"),"date");
        ensure(h.sqlite.entries.get(7L).get("watch_name_snapshot").equals("직접 입력"),"manual watch");
        ensure(h.sqlite.entries.get(7L).get("created_at").equals(123L),"created timestamp changed");
        ensure(!h.sqlite.photos.containsKey(11L),"removed photo remained");
        ensure(h.sqlite.photos.containsKey(12L),"kept photo removed");
        ensure(h.sqlite.photos.get(12L).get("display_order").equals(0),"kept photo order");
        ensure(h.sqlite.photos.get(100L).get("display_order").equals(1),"new photo order");
        ensure(h.deletedFiles.equals(Arrays.asList("old11.png")),"deleted incorrect photos");
        ensure(h.sqlite.photos.containsKey(50L),"other entry photo modified");

        // 저장 실패: 트랜잭션 롤백, 물리 파일 삭제 금지
        Map<Long,Map<String,Object>> oldEntries=SQLiteDatabase.copy(h.sqlite.entries);
        Map<Long,Map<String,Object>> oldPhotos=SQLiteDatabase.copy(h.sqlite.photos);
        try {
            h.updateHeritageEntry(7L,"2026-12-01",10L,"테스트","", "실패 제목","실패 내용",
                    Arrays.asList(12L), Arrays.asList("FAIL"));
            throw new AssertionError("expected insert failure");
        } catch(IllegalStateException expected) { }
        ensure(h.sqlite.entries.equals(oldEntries),"entry rollback failed");
        ensure(h.sqlite.photos.equals(oldPhotos),"photo rollback failed");
        ensure(h.deletedFiles.size()==1,"existing files deleted on failure");

        // 데이터가 없는 ID/부적절한 ID는 수정하지 않음
        ensure(!h.updateHeritageEntry(999L,"date",0,"watch","","title","content",
                Collections.emptyList(), Collections.emptyList()),"absent row updated");
        ensure(!h.updateHeritageEntry(-1L,"date",0,"watch","","title","content",
                Collections.emptyList(), Collections.emptyList()),"negative id updated");
        System.out.println("PASS: actual Java method; update, photo keep/remove/add, rollback, missing ID");
    }
}
'''


class HeritageEditRegression(unittest.TestCase):
    def test_database_method_using_javac(self):
        method = extract_method(DB, 'updateHeritageEntry')
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            (path / 'HeritageHarness.java').write_text(JAVA.replace('ACTUAL_METHOD', method))
            subprocess.run(['javac', 'HeritageHarness.java'], check=True, cwd=path, capture_output=True, text=True)
            completed = subprocess.run(['java', 'HeritageHarness'], check=True, cwd=path, capture_output=True, text=True)
            self.assertIn('PASS:', completed.stdout)

    def test_detail_and_editor_wiring(self):
        self.assertIn('.setNeutralButton("수정",null)', UI)
        self.assertIn('showHeritageEditDialog(entryId)', UI)
        self.assertIn('editing?existing.eventDate:', UI)
        self.assertIn('editing?existing.title:', UI)
        self.assertIn('editing?existing.watchName:', UI)
        self.assertIn('content.setText(existing.content)', UI)
        self.assertIn('pendingHeritageExistingPhotos.addAll(db.getHeritagePhotos(existing.id))', UI)
        self.assertIn('db.updateHeritageEntry(existing.id', UI)
        self.assertIn('if(!pendingHeritageSaved) cleanupPendingHeritagePhotos()', UI)
        self.assertIn('session!=heritageEditorSession', UI)
        self.assertIn('pendingHeritagePhotoImportJobs>0', UI)


if __name__ == '__main__':
    unittest.main(verbosity=2)
