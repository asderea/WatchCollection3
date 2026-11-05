package com.watchcollection.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Watch Collection 전용 AI 중계 서버 통신기.
 * OpenAI API 키는 Android 앱에 저장하지 않고 중계 서버에서만 보관합니다.
 */
public final class AiBackendClient {
    private static final String PREFS = "watch_collection_ai";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_ACCESS_TOKEN = "access_token";

    public interface ProgressCallback {
        void onProgress(String message);
    }

    public static class SpecResult {
        public String brand = "";
        public String model = "";
        public String reference = "";
        public String power = "";
        public String movement = "";
        public String diameterMm = "";
        public String lugToLugMm = "";
        public String thicknessMm = "";
        public String lugWidthMm = "";
        public String powerReserveHours = "";
        public String waterResistance = "";
        public String crystal = "";
        public String caseMaterial = "";
        public double confidence = 0.0;
        public String summary = "";
        public final List<String> sources = new ArrayList<>();
    }

    public static class Recommendation {
        public int rank;
        public long watchId;
        public long strapId;
        public String reason = "";
    }

    public static class RecommendationResult {
        public String outfitSummary = "";
        public final List<Recommendation> recommendations = new ArrayList<>();
    }

    /** Worker의 /api/usage 응답을 앱 UI에서 사용하기 위한 값 객체입니다. */
    public static class CreditUsage {
        public boolean configured;
        public boolean estimated = true;
        public boolean guardEnabled;
        public boolean callsBlocked;
        public boolean specSearchAllowed;
        public boolean recommendationAllowed;
        public boolean projectFilterEnabled;
        public String status = "";
        public String currency = "usd";
        public String startAt = "";
        public String expiresAt = "";
        public String costDataEndAt = "";
        public String updatedAt = "";
        public String scope = "organization";
        public String note = "";
        public String message = "";
        public double totalUsd;
        public double usedUsd;
        public double remainingUsd;
        public double remainingPercent;
        public double reserveUsd;
        public double specRequestReserveUsd;
        public double recommendRequestReserveUsd;
        public int daysRemaining;
    }

    private AiBackendClient() {}

    public static void saveConfig(Context context, String serverUrl, String token) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_URL, normalizeBaseUrl(serverUrl))
                .putString(KEY_ACCESS_TOKEN, token == null ? "" : token.trim())
                .apply();
    }

    public static String getServerUrl(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SERVER_URL, "");
    }

    public static String getAccessToken(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACCESS_TOKEN, "");
    }

    public static boolean isConfigured(Context context) {
        return !getServerUrl(context).trim().isEmpty() && !getAccessToken(context).trim().isEmpty();
    }

    public static boolean healthCheck(Context context) throws Exception {
        JSONObject response = request(context, "/health", null, "GET");
        return response.optBoolean("ok", false);
    }

    public static CreditUsage getCreditUsage(Context context, boolean forceRefresh) throws Exception {
        String path = forceRefresh ? "/api/usage?refresh=1" : "/api/usage";
        JSONObject root = request(context, path, null, "GET");
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalStateException(root.optString("error", "API 사용량 결과가 없습니다."));

        CreditUsage out = new CreditUsage();
        out.configured = data.optBoolean("configured", false);
        out.estimated = data.optBoolean("estimated", true);
        out.guardEnabled = data.optBoolean("guard_enabled", false);
        out.callsBlocked = data.optBoolean("calls_blocked", false);
        out.specSearchAllowed = data.optBoolean("spec_search_allowed", !out.callsBlocked);
        out.recommendationAllowed = data.optBoolean("recommendation_allowed", !out.callsBlocked);
        out.projectFilterEnabled = data.optBoolean("project_filter_enabled", false);
        out.status = data.optString("status", "");
        out.currency = data.optString("currency", "usd");
        out.startAt = data.optString("start_at", "");
        out.expiresAt = data.optString("expires_at", "");
        out.costDataEndAt = data.optString("costs_data_end_at", "");
        out.updatedAt = data.optString("updated_at", "");
        out.scope = data.optString("scope", "organization");
        out.note = data.optString("note", "");
        out.message = data.optString("message", "");
        out.totalUsd = data.optDouble("total_usd", 0.0);
        out.usedUsd = data.optDouble("used_usd", 0.0);
        out.remainingUsd = data.optDouble("remaining_usd", 0.0);
        out.remainingPercent = data.optDouble("remaining_percent", 0.0);
        out.reserveUsd = data.optDouble("reserve_usd", 0.0);
        out.specRequestReserveUsd = data.optDouble("spec_request_reserve_usd", 0.0);
        out.recommendRequestReserveUsd = data.optDouble("recommend_request_reserve_usd", 0.0);
        out.daysRemaining = data.optInt("days_remaining", 0);
        return out;
    }

    public static SpecResult searchWatchSpec(Context context, String query, String brand,
                                              String reference, ProgressCallback progress) throws Exception {
        if (progress != null) progress.onProgress("AI 서버에 연결합니다.");
        JSONObject body = new JSONObject();
        body.put("query", safe(query));
        body.put("current_brand", safe(brand));
        body.put("current_reference", safe(reference));

        if (progress != null) progress.onProgress("GPT-6 Luna가 공식 출처와 모델 일치 여부를 확인합니다.");
        JSONObject root = request(context, "/api/watch/spec-search", body, "POST");
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalStateException(root.optString("error", "AI 검색 결과가 없습니다."));

        SpecResult out = new SpecResult();
        out.brand = data.optString("brand", "");
        out.model = data.optString("model", "");
        out.reference = data.optString("reference", "");
        out.power = data.optString("power", "");
        out.movement = data.optString("movement", "");
        out.diameterMm = numberString(data.opt("diameter_mm"));
        out.lugToLugMm = numberString(data.opt("lug_to_lug_mm"));
        out.thicknessMm = numberString(data.opt("thickness_mm"));
        out.lugWidthMm = numberString(data.opt("lug_width_mm"));
        out.powerReserveHours = numberString(data.opt("power_reserve_hours"));
        out.waterResistance = data.optString("water_resistance", "");
        out.crystal = data.optString("crystal", "");
        out.caseMaterial = data.optString("case_material", "");
        out.confidence = data.optDouble("confidence", 0.0);
        out.summary = data.optString("summary", "AI 스펙 검색 완료");
        JSONArray sources = data.optJSONArray("sources");
        if (sources != null) {
            for (int i = 0; i < sources.length(); i++) {
                Object item = sources.opt(i);
                if (item instanceof JSONObject) {
                    String url = ((JSONObject) item).optString("url", "");
                    if (!url.trim().isEmpty()) out.sources.add(url.trim());
                } else if (item != null) {
                    String url = String.valueOf(item).trim();
                    if (!url.isEmpty()) out.sources.add(url);
                }
            }
        }
        if (progress != null) {
            progress.onProgress(String.format(java.util.Locale.KOREA,
                    "AI 검색 완료 · 신뢰도 %.0f%% · 출처 %d개",
                    Math.max(0.0, Math.min(1.0, out.confidence)) * 100.0, out.sources.size()));
        }
        return out;
    }

    public static RecommendationResult recommend(Context context, String outfit,
                                                   JSONArray watches, JSONArray straps,
                                                   ProgressCallback progress) throws Exception {
        if (progress != null) progress.onProgress("컬렉션과 착장을 AI 서버로 전송합니다.");
        JSONObject body = new JSONObject();
        body.put("outfit", safe(outfit));
        body.put("watches", watches == null ? new JSONArray() : watches);
        body.put("straps", straps == null ? new JSONArray() : straps);

        if (progress != null) progress.onProgress("GPT-6 Luna가 시계와 스트랩 조합을 비교합니다.");
        JSONObject root = request(context, "/api/watch/recommend", body, "POST");
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalStateException(root.optString("error", "추천 결과가 없습니다."));

        RecommendationResult out = new RecommendationResult();
        out.outfitSummary = data.optString("outfit_summary", "");
        JSONArray items = data.optJSONArray("recommendations");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                Recommendation r = new Recommendation();
                r.rank = item.optInt("rank", i + 1);
                r.watchId = item.optLong("watch_id", -1L);
                r.strapId = item.optLong("strap_id", -1L);
                r.reason = item.optString("reason", "");
                if (r.watchId > 0 && r.strapId > 0) out.recommendations.add(r);
            }
        }
        if (out.recommendations.isEmpty()) throw new IllegalStateException("호환 가능한 추천 조합을 받지 못했습니다.");
        if (progress != null) progress.onProgress("추천 조합을 정리했습니다.");
        return out;
    }

    private static JSONObject request(Context context, String path, JSONObject body, String method) throws Exception {
        String base = getServerUrl(context);
        if (base.trim().isEmpty()) throw new IllegalStateException("AI 서버 URL이 설정되지 않았습니다.");
        URL url = new URL(normalizeBaseUrl(base) + path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(90000);
        con.setRequestMethod(method);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("X-Watch-Token", getAccessToken(context));
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = con.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = con.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String text = readAll(stream);
        con.disconnect();
        if (text.trim().isEmpty()) throw new IllegalStateException("AI 서버가 빈 응답을 반환했습니다. HTTP " + code);
        JSONObject json = new JSONObject(text);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(json.optString("error", "AI 서버 오류 HTTP " + code));
        }
        return json;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String normalizeBaseUrl(String value) {
        String v = safe(value);
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static String numberString(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Math.abs(d - Math.rint(d)) < 0.000001) return String.valueOf((long) Math.rint(d));
            return new java.math.BigDecimal(String.valueOf(d)).stripTrailingZeros().toPlainString();
        }
        String s = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
