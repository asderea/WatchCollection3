package com.watchcollection.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "watch_collection.db";
    private static final int DB_VERSION = 5;

    public static class AccuracyRow {
        public long id, watchId;
        public String watchName = "", date = "", note = "";
        public double deviationSec, intervalHours, secPerDay;
    }

    public static class TimegrapherRow {
        public long id, watchId;
        public String watchName = "", date = "", position = "", note = "";
        public int bph;
        public double rateSecDay, beatErrorMs, amplitudeDeg = Double.NaN;
        public double liftAngleDeg, signalQuality, durationSeconds;
    }


    public static class PositionAverage {
        public String position = "";
        public int count;
        public double avgRateSecDay = Double.NaN;
        public double avgBeatErrorMs = Double.NaN;
        public double avgAmplitudeDeg = Double.NaN;
        public String latestDate = "";
    }

    public static class WearRow {
        public long id, watchId;
        public int slot = 1;
        public String watchName = "", date = "", note = "", imageUrl = "";
    }

    public static class WearStat {
        public long watchId;
        public String watchName = "", imageUrl = "";
        public double score;
    }

    public DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE watches (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "model_name TEXT NOT NULL, brand TEXT, reference_no TEXT, movement TEXT, caliber TEXT," +
                "diameter_mm TEXT, lug_to_lug_mm TEXT, thickness_mm TEXT, lug_width_mm TEXT," +
                "power_reserve_hours TEXT, water_resistance TEXT, crystal TEXT, case_material TEXT," +
                "source_urls TEXT, representative_image_url TEXT, original_image_url TEXT, image_source_url TEXT, notes TEXT," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE accuracy_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, watch_id INTEGER NOT NULL, record_date TEXT NOT NULL," +
                "deviation_sec REAL NOT NULL, interval_hours REAL NOT NULL, sec_per_day REAL NOT NULL," +
                "note TEXT, created_at INTEGER NOT NULL," +
                "FOREIGN KEY(watch_id) REFERENCES watches(id) ON DELETE CASCADE)");

        createTimegrapherTable(db);
        createWearTable(db);

        db.execSQL("CREATE TABLE straps (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, maker TEXT, name TEXT NOT NULL, width_mm INTEGER NOT NULL," +
                "color TEXT, material TEXT, image_path TEXT, buckle TEXT, notes TEXT, created_at INTEGER NOT NULL)");
    }


    private void createTimegrapherTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS timegrapher_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, watch_id INTEGER NOT NULL, record_date TEXT NOT NULL," +
                "bph INTEGER NOT NULL, rate_sec_day REAL NOT NULL, beat_error_ms REAL NOT NULL," +
                "amplitude_deg REAL, lift_angle_deg REAL NOT NULL, position TEXT NOT NULL," +
                "signal_quality REAL NOT NULL, duration_seconds REAL NOT NULL, note TEXT, created_at INTEGER NOT NULL," +
                "FOREIGN KEY(watch_id) REFERENCES watches(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timegrapher_watch ON timegrapher_records(watch_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timegrapher_date ON timegrapher_records(record_date)");
    }

    private void createWearTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE wear_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, watch_id INTEGER NOT NULL, wear_date TEXT NOT NULL," +
                "slot INTEGER NOT NULL DEFAULT 1, note TEXT, created_at INTEGER NOT NULL," +
                "UNIQUE(wear_date, slot)," +
                "FOREIGN KEY(watch_id) REFERENCES watches(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_wear_date ON wear_records(wear_date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_wear_watch ON wear_records(watch_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE watches ADD COLUMN representative_image_url TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE watches ADD COLUMN image_source_url TEXT DEFAULT ''");
            db.execSQL("CREATE TABLE IF NOT EXISTS straps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, width_mm INTEGER NOT NULL," +
                    "color TEXT, material TEXT, image_path TEXT, buckle TEXT, notes TEXT, created_at INTEGER NOT NULL)");
        }
        if (oldVersion < 3) {
            migrateWearRecordsToV3(db);
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE straps ADD COLUMN maker TEXT DEFAULT ''");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE watches ADD COLUMN original_image_url TEXT DEFAULT ''");
            createTimegrapherTable(db);
        }
    }

    private void migrateWearRecordsToV3(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE wear_records RENAME TO wear_records_v2_backup");
        createWearTable(db);
        db.execSQL("INSERT INTO wear_records(id,watch_id,wear_date,slot,note,created_at) " +
                "SELECT id,watch_id,wear_date,1,note,created_at FROM wear_records_v2_backup");
        db.execSQL("DROP TABLE wear_records_v2_backup");
    }

    public long saveWatch(Watch w) {
        ContentValues v = watchValues(w);
        SQLiteDatabase db = getWritableDatabase();
        if (w.id > 0) {
            db.update("watches", v, "id=?", new String[]{String.valueOf(w.id)});
            return w.id;
        }
        v.put("created_at", System.currentTimeMillis());
        return db.insert("watches", null, v);
    }

    private ContentValues watchValues(Watch w) {
        ContentValues v = new ContentValues();
        v.put("model_name", w.modelName); v.put("brand", w.brand); v.put("reference_no", w.referenceNo);
        v.put("movement", w.movement); v.put("caliber", w.caliber); v.put("diameter_mm", w.diameterMm);
        v.put("lug_to_lug_mm", w.lugToLugMm); v.put("thickness_mm", w.thicknessMm); v.put("lug_width_mm", w.lugWidthMm);
        v.put("power_reserve_hours", w.powerReserveHours); v.put("water_resistance", w.waterResistance);
        v.put("crystal", w.crystal); v.put("case_material", w.caseMaterial); v.put("source_urls", w.sourceUrls);
        v.put("representative_image_url", w.representativeImageUrl); v.put("original_image_url", w.originalImageUrl);
        v.put("image_source_url", w.imageSourceUrl);
        v.put("notes", w.notes);
        return v;
    }

    public List<Watch> getWatches() {
        List<Watch> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM watches ORDER BY brand, model_name", null);
        try { while (c.moveToNext()) list.add(readWatch(c)); } finally { c.close(); }
        return list;
    }

    public Watch getWatch(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM watches WHERE id=?", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? readWatch(c) : null; } finally { c.close(); }
    }

    private Watch readWatch(Cursor c) {
        Watch w = new Watch();
        w.id = c.getLong(c.getColumnIndexOrThrow("id"));
        w.modelName=s(c,"model_name"); w.brand=s(c,"brand"); w.referenceNo=s(c,"reference_no");
        w.movement=s(c,"movement"); w.caliber=s(c,"caliber"); w.diameterMm=s(c,"diameter_mm");
        w.lugToLugMm=s(c,"lug_to_lug_mm"); w.thicknessMm=s(c,"thickness_mm"); w.lugWidthMm=s(c,"lug_width_mm");
        w.powerReserveHours=s(c,"power_reserve_hours"); w.waterResistance=s(c,"water_resistance");
        w.crystal=s(c,"crystal"); w.caseMaterial=s(c,"case_material"); w.sourceUrls=s(c,"source_urls");
        w.representativeImageUrl=s(c,"representative_image_url"); w.originalImageUrl=s(c,"original_image_url");
        w.imageSourceUrl=s(c,"image_source_url"); w.notes=s(c,"notes");
        return w;
    }

    public void deleteWatch(long id) { getWritableDatabase().delete("watches", "id=?", new String[]{String.valueOf(id)}); }

    public long addAccuracy(long watchId, String date, double deviationSec, double intervalHours, String note) {
        double safeHours = intervalHours <= 0 ? 24.0 : intervalHours;
        ContentValues v = new ContentValues();
        v.put("watch_id", watchId); v.put("record_date", date); v.put("deviation_sec", deviationSec);
        v.put("interval_hours", safeHours); v.put("sec_per_day", deviationSec * 24.0 / safeHours);
        v.put("note", note); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("accuracy_records", null, v);
    }

    public List<AccuracyRow> getAccuracyRows(int limit) {
        List<AccuracyRow> list = new ArrayList<>();
        String sql = "SELECT a.*, w.model_name FROM accuracy_records a JOIN watches w ON w.id=a.watch_id " +
                "ORDER BY a.record_date DESC, a.id DESC LIMIT " + Math.max(1, limit);
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        try { while (c.moveToNext()) list.add(readAccuracy(c)); } finally { c.close(); }
        return list;
    }

    public List<AccuracyRow> getAccuracyRowsForWatch(long watchId, int limit) {
        List<AccuracyRow> list = new ArrayList<>();
        String sql = "SELECT a.*, w.model_name FROM accuracy_records a JOIN watches w ON w.id=a.watch_id " +
                "WHERE a.watch_id=? ORDER BY a.record_date ASC, a.id ASC LIMIT " + Math.max(1, limit);
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(watchId)});
        try { while (c.moveToNext()) list.add(readAccuracy(c)); } finally { c.close(); }
        return list;
    }

    private AccuracyRow readAccuracy(Cursor c) {
        AccuracyRow r = new AccuracyRow();
        r.id=c.getLong(c.getColumnIndexOrThrow("id")); r.watchId=c.getLong(c.getColumnIndexOrThrow("watch_id"));
        r.watchName=s(c,"model_name"); r.date=s(c,"record_date"); r.deviationSec=c.getDouble(c.getColumnIndexOrThrow("deviation_sec"));
        r.intervalHours=c.getDouble(c.getColumnIndexOrThrow("interval_hours")); r.secPerDay=c.getDouble(c.getColumnIndexOrThrow("sec_per_day")); r.note=s(c,"note");
        return r;
    }

    public double averageSecPerDay(long watchId) {
        Cursor tg = getReadableDatabase().rawQuery("SELECT AVG(rate_sec_day) FROM timegrapher_records WHERE watch_id=?", new String[]{String.valueOf(watchId)});
        try {
            if (tg.moveToFirst() && !tg.isNull(0)) return tg.getDouble(0);
        } finally { tg.close(); }
        // v0.6 이전 수동 기록은 삭제하지 않고 평균 표시의 fallback으로만 보존합니다.
        Cursor c = getReadableDatabase().rawQuery("SELECT AVG(sec_per_day) FROM accuracy_records WHERE watch_id=?", new String[]{String.valueOf(watchId)});
        try { return c.moveToFirst() && !c.isNull(0) ? c.getDouble(0) : Double.NaN; } finally { c.close(); }
    }

    public long addTimegrapherRecord(long watchId, String date, int bph, double rateSecDay,
                                     double beatErrorMs, double amplitudeDeg, double liftAngleDeg,
                                     String position, double signalQuality, double durationSeconds, String note) {
        ContentValues v = new ContentValues();
        v.put("watch_id", watchId); v.put("record_date", date); v.put("bph", bph);
        v.put("rate_sec_day", rateSecDay); v.put("beat_error_ms", beatErrorMs);
        if (Double.isNaN(amplitudeDeg)) v.putNull("amplitude_deg"); else v.put("amplitude_deg", amplitudeDeg);
        v.put("lift_angle_deg", liftAngleDeg); v.put("position", position == null ? "Dial up" : position);
        v.put("signal_quality", signalQuality); v.put("duration_seconds", durationSeconds);
        v.put("note", note); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("timegrapher_records", null, v);
    }

    public List<TimegrapherRow> getTimegrapherRows(int limit) {
        List<TimegrapherRow> list = new ArrayList<>();
        String sql = "SELECT t.*,w.model_name FROM timegrapher_records t JOIN watches w ON w.id=t.watch_id " +
                "ORDER BY t.id DESC LIMIT " + Math.max(1, limit);
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        try { while (c.moveToNext()) list.add(readTimegrapher(c)); } finally { c.close(); }
        return list;
    }

    public List<TimegrapherRow> getTimegrapherRowsForWatch(long watchId, int limit) {
        List<TimegrapherRow> list = new ArrayList<>();
        String sql = "SELECT t.*,w.model_name FROM timegrapher_records t JOIN watches w ON w.id=t.watch_id " +
                "WHERE t.watch_id=? ORDER BY t.id DESC LIMIT " + Math.max(1, limit);
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(watchId)});
        try { while (c.moveToNext()) list.add(readTimegrapher(c)); } finally { c.close(); }
        return list;
    }

    public List<PositionAverage> getPositionAveragesForWatch(long watchId) {
        List<PositionAverage> list = new ArrayList<>();
        String sql = "SELECT position, COUNT(*) AS n, AVG(rate_sec_day) AS avg_rate, " +
                "AVG(beat_error_ms) AS avg_beat, AVG(amplitude_deg) AS avg_amp, MAX(record_date) AS latest_date " +
                "FROM timegrapher_records WHERE watch_id=? GROUP BY position " +
                "ORDER BY CASE position WHEN 'Dial up' THEN 1 WHEN 'Dial down' THEN 2 WHEN '12 Up' THEN 3 " +
                "WHEN '9 Up' THEN 4 WHEN '6 Up' THEN 5 WHEN '3 Up' THEN 6 ELSE 7 END";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(watchId)});
        try {
            while (c.moveToNext()) {
                PositionAverage r = new PositionAverage();
                r.position = c.isNull(0) ? "" : c.getString(0);
                r.count = c.getInt(1);
                r.avgRateSecDay = c.isNull(2) ? Double.NaN : c.getDouble(2);
                r.avgBeatErrorMs = c.isNull(3) ? Double.NaN : c.getDouble(3);
                r.avgAmplitudeDeg = c.isNull(4) ? Double.NaN : c.getDouble(4);
                r.latestDate = c.isNull(5) ? "" : c.getString(5);
                list.add(r);
            }
        } finally { c.close(); }
        return list;
    }

    public double averageTimegrapherRateForWatch(long watchId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT AVG(rate_sec_day) FROM timegrapher_records WHERE watch_id=?",
                new String[]{String.valueOf(watchId)});
        try { return c.moveToFirst() && !c.isNull(0) ? c.getDouble(0) : Double.NaN; } finally { c.close(); }
    }

    public int getTimegrapherRecordCountForWatch(long watchId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM timegrapher_records WHERE watch_id=?",
                new String[]{String.valueOf(watchId)});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public double averageBeatErrorForWatch(long watchId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT AVG(beat_error_ms) FROM timegrapher_records WHERE watch_id=?",
                new String[]{String.valueOf(watchId)});
        try { return c.moveToFirst() && !c.isNull(0) ? c.getDouble(0) : Double.NaN; } finally { c.close(); }
    }

    public double averageAmplitudeForWatch(long watchId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT AVG(amplitude_deg) FROM timegrapher_records WHERE watch_id=? AND amplitude_deg IS NOT NULL",
                new String[]{String.valueOf(watchId)});
        try { return c.moveToFirst() && !c.isNull(0) ? c.getDouble(0) : Double.NaN; } finally { c.close(); }
    }

    private TimegrapherRow readTimegrapher(Cursor c) {
        TimegrapherRow r = new TimegrapherRow();
        r.id=c.getLong(c.getColumnIndexOrThrow("id")); r.watchId=c.getLong(c.getColumnIndexOrThrow("watch_id"));
        r.watchName=s(c,"model_name"); r.date=s(c,"record_date"); r.position=s(c,"position"); r.note=s(c,"note");
        r.bph=c.getInt(c.getColumnIndexOrThrow("bph")); r.rateSecDay=c.getDouble(c.getColumnIndexOrThrow("rate_sec_day"));
        r.beatErrorMs=c.getDouble(c.getColumnIndexOrThrow("beat_error_ms"));
        int ai=c.getColumnIndexOrThrow("amplitude_deg"); r.amplitudeDeg=c.isNull(ai)?Double.NaN:c.getDouble(ai);
        r.liftAngleDeg=c.getDouble(c.getColumnIndexOrThrow("lift_angle_deg"));
        r.signalQuality=c.getDouble(c.getColumnIndexOrThrow("signal_quality"));
        r.durationSeconds=c.getDouble(c.getColumnIndexOrThrow("duration_seconds"));
        return r;
    }

    // WEAR RECORDS ------------------------------------------------------------
    // 하루 1개면 1.0회, 하루 2개면 각 0.5회로 통계 계산한다.

    public void saveWear(long watchId, String date, int slot, String note) {
        int safeSlot = slot <= 1 ? 1 : 2;
        ContentValues v = new ContentValues();
        v.put("watch_id", watchId); v.put("wear_date", date); v.put("slot", safeSlot);
        v.put("note", note); v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("wear_records", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<WearRow> getWears(String date) {
        List<WearRow> list = new ArrayList<>();
        String sql = "SELECT r.*,w.model_name,w.representative_image_url FROM wear_records r " +
                "JOIN watches w ON w.id=r.watch_id WHERE r.wear_date=? ORDER BY r.slot";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{date});
        try { while (c.moveToNext()) list.add(readWear(c)); } finally { c.close(); }
        return list;
    }

    public Map<String, List<WearRow>> getWearRange(String start, String end) {
        Map<String, List<WearRow>> map = new LinkedHashMap<>();
        String sql = "SELECT r.*,w.model_name,w.representative_image_url FROM wear_records r JOIN watches w ON w.id=r.watch_id " +
                "WHERE r.wear_date BETWEEN ? AND ? ORDER BY r.wear_date,r.slot";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{start,end});
        try {
            while(c.moveToNext()) {
                WearRow r=readWear(c);
                List<WearRow> rows = map.get(r.date);
                if (rows == null) { rows = new ArrayList<>(); map.put(r.date, rows); }
                rows.add(r);
            }
        } finally { c.close(); }
        return map;
    }

    private WearRow readWear(Cursor c) {
        WearRow r = new WearRow();
        r.id=c.getLong(c.getColumnIndexOrThrow("id")); r.watchId=c.getLong(c.getColumnIndexOrThrow("watch_id"));
        int slotIndex = c.getColumnIndex("slot"); r.slot = slotIndex >= 0 ? c.getInt(slotIndex) : 1;
        r.watchName=s(c,"model_name"); r.date=s(c,"wear_date"); r.note=s(c,"note"); r.imageUrl=s(c,"representative_image_url");
        return r;
    }

    public void clearWearDate(String date) {
        getWritableDatabase().delete("wear_records", "wear_date=?", new String[]{date});
    }

    public int countWearInMonth(String yyyyMm) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT wear_date) FROM wear_records WHERE wear_date LIKE ?", new String[]{yyyyMm + "%"});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public double getWearScoreForWatch(long watchId, String start, String end) {
        String sql = "SELECT COALESCE(SUM(CASE WHEN " +
                "(SELECT COUNT(*) FROM wear_records x WHERE x.wear_date=r.wear_date)>=2 THEN 0.5 ELSE 1.0 END),0) " +
                "FROM wear_records r WHERE r.watch_id=? AND r.wear_date BETWEEN ? AND ?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(watchId),start,end});
        try { return c.moveToFirst() ? c.getDouble(0) : 0.0; } finally { c.close(); }
    }

    public double getAllTimeWearScoreForWatch(long watchId) {
        String sql = "SELECT COALESCE(SUM(CASE WHEN " +
                "(SELECT COUNT(*) FROM wear_records x WHERE x.wear_date=r.wear_date)>=2 THEN 0.5 ELSE 1.0 END),0) " +
                "FROM wear_records r WHERE r.watch_id=?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(watchId)});
        try { return c.moveToFirst() ? c.getDouble(0) : 0.0; } finally { c.close(); }
    }

    public Map<Long, Double> getAllTimeWearScores() {
        Map<Long, Double> out = new LinkedHashMap<>();
        String sql = "SELECT w.id, COALESCE(SUM(CASE WHEN r.id IS NULL THEN 0.0 WHEN " +
                "(SELECT COUNT(*) FROM wear_records x WHERE x.wear_date=r.wear_date)>=2 THEN 0.5 ELSE 1.0 END),0) AS score " +
                "FROM watches w LEFT JOIN wear_records r ON r.watch_id=w.id " +
                "GROUP BY w.id ORDER BY score DESC,w.brand,w.model_name";
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        try { while (c.moveToNext()) out.put(c.getLong(0), c.getDouble(1)); } finally { c.close(); }
        return out;
    }

    public Map<Long, Double> getWearScores(String start, String end) {
        Map<Long, Double> out = new LinkedHashMap<>();
        String sql = "SELECT r.watch_id, SUM(CASE WHEN " +
                "(SELECT COUNT(*) FROM wear_records x WHERE x.wear_date=r.wear_date)>=2 THEN 0.5 ELSE 1.0 END) AS score " +
                "FROM wear_records r WHERE r.wear_date BETWEEN ? AND ? GROUP BY r.watch_id ORDER BY score DESC";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{start,end});
        try { while (c.moveToNext()) out.put(c.getLong(0), c.getDouble(1)); } finally { c.close(); }
        return out;
    }

    public List<WearStat> getTopWorn(String start, String end, int limit) {
        List<WearStat> out = new ArrayList<>();
        String sql = "SELECT r.watch_id,w.model_name,w.representative_image_url, " +
                "SUM(CASE WHEN (SELECT COUNT(*) FROM wear_records x WHERE x.wear_date=r.wear_date)>=2 THEN 0.5 ELSE 1.0 END) AS score " +
                "FROM wear_records r JOIN watches w ON w.id=r.watch_id " +
                "WHERE r.wear_date BETWEEN ? AND ? GROUP BY r.watch_id ORDER BY score DESC,w.model_name LIMIT " + Math.max(1,limit);
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{start,end});
        try {
            while(c.moveToNext()) {
                WearStat s = new WearStat();
                s.watchId=c.getLong(0); s.watchName=c.getString(1)==null?"":c.getString(1);
                s.imageUrl=c.getString(2)==null?"":c.getString(2); s.score=c.getDouble(3); out.add(s);
            }
        } finally { c.close(); }
        return out;
    }

    public String getMostWornWatchName() {
        String sql = "SELECT w.model_name, SUM(CASE WHEN " +
                "(SELECT COUNT(*) FROM wear_records x WHERE x.wear_date=r.wear_date)>=2 THEN 0.5 ELSE 1.0 END) AS score " +
                "FROM wear_records r JOIN watches w ON w.id=r.watch_id GROUP BY r.watch_id ORDER BY score DESC LIMIT 1";
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        try { return c.moveToFirst() ? c.getString(0) : "–"; } finally { c.close(); }
    }

    // STRAPS -----------------------------------------------------------------

    public long saveStrap(Strap s) {
        ContentValues v = new ContentValues();
        v.put("maker",s.maker); v.put("name",s.name); v.put("width_mm",s.widthMm); v.put("color",s.color); v.put("material",s.material);
        v.put("image_path",s.imagePath); v.put("buckle",s.buckle); v.put("notes",s.notes);
        SQLiteDatabase db=getWritableDatabase();
        if(s.id>0){db.update("straps",v,"id=?",new String[]{String.valueOf(s.id)}); return s.id;}
        v.put("created_at",System.currentTimeMillis()); return db.insert("straps",null,v);
    }

    public List<Strap> getStraps(int widthMm) {
        List<Strap> list=new ArrayList<>();
        Cursor c = widthMm != 0
                ? getReadableDatabase().rawQuery("SELECT * FROM straps WHERE width_mm=? ORDER BY maker,material,color,name",new String[]{String.valueOf(widthMm)})
                : getReadableDatabase().rawQuery("SELECT * FROM straps ORDER BY CASE WHEN width_mm<0 THEN 999 ELSE width_mm END,maker,material,color,name",null);
        try { while(c.moveToNext()) list.add(readStrap(c)); } finally {c.close();}
        return list;
    }

    private Strap readStrap(Cursor c){
        Strap s=new Strap(); s.id=c.getLong(c.getColumnIndexOrThrow("id")); s.maker=this.s(c,"maker"); s.name=this.s(c,"name"); s.widthMm=c.getInt(c.getColumnIndexOrThrow("width_mm"));
        s.color=this.s(c,"color"); s.material=this.s(c,"material"); s.imagePath=this.s(c,"image_path"); s.buckle=this.s(c,"buckle"); s.notes=this.s(c,"notes"); return s;
    }

    public void deleteStrap(long id){getWritableDatabase().delete("straps","id=?",new String[]{String.valueOf(id)});}

    public List<Watch> getCompatibleWatches(int widthMm) {
        List<Watch> out=new ArrayList<>();
        for(Watch w:getWatches()) {
            try { if(Math.abs(Float.parseFloat(w.lugWidthMm)-widthMm)<0.1f) out.add(w); } catch(Exception ignored) {}
        }
        return out;
    }

    private String s(Cursor c,String col){int idx=c.getColumnIndex(col); if(idx<0||c.isNull(idx)) return ""; String v=c.getString(idx); return v==null?"":v;}
}
