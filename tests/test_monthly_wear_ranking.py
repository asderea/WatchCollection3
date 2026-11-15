#!/usr/bin/env python3
"""월간 순위의 실제 Java 메서드와 실제 SQL을 실행하는 회귀 검사.

Android 화면 대신 작은 View/DB 대역으로 행과 버튼 결과만 확인한다.
실제 Android APK 빌드나 기기 화면 검사를 대체하지 않는다.
실행: python3 tests/test_monthly_wear_ranking.py
"""
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA_DIR = ROOT / "app/src/main/java/com/watchcollection/app"


def java_method(path, start, end):
    """원본 함수 자체를 가져와 테스트가 별도 구현을 검사하지 않도록 한다."""
    source = path.read_text(encoding="utf-8")
    return source[source.index(start):source.index(end, source.index(start))]


HARNESS = r'''
import java.util.*;
import java.text.SimpleDateFormat;

class MonthlyRankingHarness {
    int monthlyWearPage;
    DatabaseHelper db = new DatabaseHelper();
    SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM");
    LinearLayout rendered;
    long openedWatchId = -1;
    String currentMonthStart() { return "2026-11-01"; }
    String currentMonthEnd() { return "2026-11-30"; }
    String safeText(String value) { return value == null ? "" : value; }
    String formatWearScore(double score) { return String.valueOf(score); }
    void showCollection() { rendered = (LinearLayout) monthlyWearSummary(); }
    void showWatchDetailPopup(Watch watch) { openedWatchId = watch.id; }

    /* ACTUAL_METHOD */

    static class View {
        interface Click { void click(View view); }
        Click click;
        boolean enabled = true;
        void setOnClickListener(Click callback) { click = callback; }
        void performClick() { if(enabled && click != null) click.click(this); }
        void setPadding(int a, int b, int c, int d) {}
        void setEnabled(boolean value) { enabled = value; }
    }
    static class ViewGroup extends View {
        static class LayoutParams { static final int WRAP_CONTENT = -2; }
    }
    static class LinearLayout extends ViewGroup {
        List<View> children = new ArrayList<>();
        void addView(View child) { children.add(child); }
        void addView(View child, LayoutParams params) { children.add(child); }
        static class LayoutParams extends ViewGroup.LayoutParams {
            LayoutParams(int width, int height) {}
            LayoutParams(int width, int height, int weight) {}
        }
    }
    static class TextView extends View {
        String text;
        TextView(String value) { text = value; }
        void setGravity(int gravity) {}
        void setTextColor(int color) {}
    }
    static class Button extends TextView {
        Button(String value) { super(value); }
    }
    static class Gravity { static final int CENTER = 1; }
    static class UiKit {
        static final int GOLD = 1;
        static LinearLayout card(Object context) { return new LinearLayout(); }
        static LinearLayout row(Object context) { return new LinearLayout(); }
        static TextView text(Object context, String value, int size, boolean bold) { return new TextView(value); }
        static TextView muted(Object context, String value, int size) { return new TextView(value); }
        static TextView eyebrow(Object context, String value) { return new TextView(value); }
        static Button secondaryButton(Object context, String value) { return new Button(value); }
        static View spacer(Object context, int size) { return new View(); }
        static int dp(Object context, int size) { return size; }
    }
    static class Toast {
        static final int LENGTH_SHORT = 0;
        static String last = "";
        static Toast makeText(Object context, String text, int length) {
            last = text;
            return new Toast();
        }
        void show() {}
    }
    static class Watch {
        long id;
        String brand = "Brand", modelName;
    }
    static class DatabaseHelper {
        List<WearStat> stats = new ArrayList<>();
        Map<Long, Watch> watches = new HashMap<>();
        static class WearStat {
            long watchId;
            String watchName;
            double score;
        }
        List<WearStat> getTopWorn(String start, String end, int limit) {
            return new ArrayList<>(stats);
        }
        Map<Long, Double> getWearScores(String start, String end) {
            Map<Long, Double> out = new LinkedHashMap<>();
            for(WearStat stat:stats) out.put(stat.watchId, stat.score);
            return out;
        }
        Watch getWatch(long id) { return watches.get(id); }
    }
    static MonthlyRankingHarness fixture(int count, int... released) {
        MonthlyRankingHarness app = new MonthlyRankingHarness();
        Set<Integer> removed = new HashSet<>();
        for(int id:released) removed.add(id);
        for(int id=1; id<=count; id++) {
            DatabaseHelper.WearStat stat = new DatabaseHelper.WearStat();
            stat.watchId = id;
            stat.watchName = "Saved " + id;
            stat.score = count - id + 0.5;
            app.db.stats.add(stat);
            if(!removed.contains(id)) {
                Watch watch = new Watch();
                watch.id = id;
                watch.modelName = "Watch " + id;
                app.db.watches.put((long)id, watch);
            }
        }
        return app;
    }
    static void require(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
    static List<LinearLayout> rows(MonthlyRankingHarness app) {
        List<LinearLayout> out = new ArrayList<>();
        for(View view:app.rendered.children) {
            if(view instanceof LinearLayout) {
                LinearLayout row = (LinearLayout)view;
                if(!row.children.isEmpty() && !(row.children.get(0) instanceof Button))
                    out.add(row);
            }
        }
        return out;
    }
    static LinearLayout pager(MonthlyRankingHarness app) {
        for(View view:app.rendered.children) {
            if(view instanceof LinearLayout) {
                LinearLayout row = (LinearLayout)view;
                if(!row.children.isEmpty() && row.children.get(0) instanceof Button) return row;
            }
        }
        throw new AssertionError("페이지 버튼이 없음");
    }
    static void checkPage(MonthlyRankingHarness app, int from, int to) {
        List<LinearLayout> items = rows(app);
        require(items.size() == to-from+1, "행 누락: "+from+"~"+to+", 실제 "+items.size());
        require(((TextView)pager(app).children.get(1)).text.equals(from+"–"+to+"위"), "페이지 범위 오류");
        for(int i=0; i<items.size(); i++) {
            require(((TextView)items.get(i).children.get(0)).text.equals(String.valueOf(from+i)),
                    "연속 순위 오류");
        }
    }
    static void checkPages(int count) {
        MonthlyRankingHarness app = fixture(count, 2, 3, 7, 9);
        app.showCollection();
        if(count == 0) {
            require(rows(app).isEmpty(), "기록 없는 달에 행 생성");
            require(app.monthlyWearPage == 0, "빈 달 페이지 초기화 실패");
            return;
        }
        int pageCount = (count+2)/3;
        for(int page=0; page<pageCount; page++) {
            checkPage(app, page*3+1, Math.min(page*3+3, count));
            LinearLayout buttons = pager(app);
            require(buttons.children.get(0).enabled == (page>0), "이전 버튼 상태 오류");
            require(buttons.children.get(2).enabled == (page<pageCount-1), "다음 버튼 상태 오류");
            if(page<pageCount-1) buttons.children.get(2).performClick();
        }
        for(int page=pageCount-1; page>0; page--) {
            pager(app).children.get(0).performClick();
            checkPage(app, (page-1)*3+1, page*3);
        }
    }
    public static void main(String[] args) {
        for(int size:new int[]{0,1,2,3,4,5,6,7,8,9,10}) checkPages(size);

        MonthlyRankingHarness released = fixture(9, 2, 3, 7, 9);
        released.showCollection();
        checkPage(released, 1, 3);
        require(((TextView)rows(released).get(1).children.get(1)).text.equals("Saved 2"),
                "방출된 시계명 스냅샷 누락");
        rows(released).get(1).performClick();
        require(Toast.last.equals("컬렉션에서 방출된 시계의 착용 기록입니다."), "방출 행 클릭 오류");
        rows(released).get(0).performClick();
        require(released.openedWatchId == 1, "보유 시계 상세 연결 오류");
        pager(released).children.get(2).performClick();
        pager(released).children.get(2).performClick();
        checkPage(released, 7, 9);
        require(((TextView)rows(released).get(0).children.get(1)).text.equals("Saved 7"),
                "7위 스냅샷 누락");

        MonthlyRankingHarness allReleased = fixture(3, 1, 2, 3);
        allReleased.showCollection();
        checkPage(allReleased, 1, 3);

        MonthlyRankingHarness shrink = fixture(4);
        shrink.monthlyWearPage = 10;
        shrink.showCollection();
        checkPage(shrink, 4, 4);
        shrink.db.stats.remove(3);
        shrink.showCollection();
        checkPage(shrink, 1, 3);
        shrink.monthlyWearPage = -3;
        shrink.showCollection();
        checkPage(shrink, 1, 3);
        shrink.db.stats.clear();
        shrink.showCollection();
        require(shrink.monthlyWearPage == 0, "기록 삭제 후 페이지 초기화 오류");

        MonthlyRankingHarness tied = fixture(4);
        for(DatabaseHelper.WearStat stat:tied.db.stats) {
            stat.score = 1.5;
            stat.watchName = "Same";
        }
        Collections.reverse(tied.db.stats);
        tied.showCollection();
        for(int i=0; i<3; i++) {
            rows(tied).get(i).performClick();
            require(tied.openedWatchId == i+1, "동점/동명 시 ID 순서 오류");
        }
        pager(tied).children.get(2).performClick();
        rows(tied).get(0).performClick();
        require(tied.openedWatchId == 4, "동점 마지막 페이지 오류");
        System.out.println("PASS: 0~10개 페이지, 1~3/7~9위, 방출/보유 클릭, 마지막 페이지, 축소/초기화, 동점/동명");
    }
}
'''


class MonthlyWearRankingRegression(unittest.TestCase):
    def test_actual_java_method(self):
        method = java_method(
            JAVA_DIR / "MainActivity.java",
            "    private View monthlyWearSummary()",
            "    private View collectionFilterRow(",
        )
        source = HARNESS.replace("    /* ACTUAL_METHOD */", method)
        with tempfile.TemporaryDirectory(prefix="watch-ranking-") as directory:
            java_file = Path(directory) / "MonthlyRankingHarness.java"
            java_file.write_text(source, encoding="utf-8")
            result = subprocess.run(
                ["java", str(java_file)],
                capture_output=True, text=True, check=False,
            )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        print(result.stdout.strip())

    def test_actual_sql_keeps_released_records_and_half_wears(self):
        # DatabaseHelper의 실제 SQL 문자열을 SQLite에서 실행한다.
        method = java_method(
            JAVA_DIR / "DatabaseHelper.java",
            "    public List<WearStat> getTopWorn(",
            "    public String getMostWornWatchName(",
        )
        expression = method.split("String sql = ", 1)[1].split(";\n", 1)[0]
        sql = "".join(re.findall(r'"([^"]*)"', expression)) + "10000"
        db = sqlite3.connect(":memory:")
        self.addCleanup(db.close)
        db.executescript("""
            CREATE TABLE watches(id INTEGER PRIMARY KEY, model_name TEXT, representative_image_url TEXT);
            CREATE TABLE wear_records(watch_id INTEGER, watch_name_snapshot TEXT,
                representative_image_snapshot TEXT, wear_ratio REAL, wear_date TEXT);
            INSERT INTO watches VALUES (1, 'Current', 'current.jpg'), (3, 'Third', '');
            INSERT INTO wear_records VALUES
                (1, 'Current snapshot', '', 1.0, '2026-11-01'),
                (2, 'Released snapshot', 'old.jpg', 0.5, '2026-11-02'),
                (2, 'Released snapshot', 'old.jpg', 0.5, '2026-11-03'),
                (3, 'Third snapshot', '', 0.5, '2026-11-02'),
                (99, 'Other month', '', 10.0, '2026-10-01');
        """)
        rows = db.execute(sql, ("2026-11-01", "2026-11-30")).fetchall()
        self.assertEqual([row[0] for row in rows], [1, 2, 3])
        self.assertEqual(rows[1], (2, "Released snapshot", "old.jpg", 1.0))
        self.assertEqual(rows[2][3], 0.5)
        self.assertNotIn(99, [row[0] for row in rows])


if __name__ == "__main__":
    unittest.main(verbosity=2)

