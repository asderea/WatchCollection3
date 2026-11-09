package com.watchcollection.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 컬렉션 AI 평가에 함께 전달하는 사용자 맞춤 프로필입니다.
 * 앱 안에서만 저장되며 컬렉션 평가를 실행할 때에만 Worker로 전송됩니다.
 */
public class CollectionEvaluationProfile {
    private static final String PREFS = "collection_evaluation_profile";
    private static final String KEY_JSON = "profile_json";
    public static final int PROFILE_VERSION = 1;

    public String movementPreference = "";
    public String preferredColors = "";
    public String dislikedColors = "";
    public String collectionDirection = "";
    public String coreWatches = "";
    public int targetCollectionSize = 8;
    public String wishlistPriority = "";
    public String longTermPlan = "";

    public static CollectionEvaluationProfile defaultProfile() {
        CollectionEvaluationProfile p = new CollectionEvaluationProfile();
        p.movementPreference = "Automatic 및 Mechanical 중심. Quartz와 Solar-Quartz는 역할이 분명한 경우 보완적으로 허용.";
        p.preferredColors = "Black, Navy, Silver, White, Gray, 차분한 Blue, Teal";
        p.dislikedColors = "Salmon, Burgundy, Pink, 지나치게 튀는 색상";
        p.collectionDirection = "각 시계의 역할이 분명한 소수 정예 컬렉션을 지향한다. Dress, Diver, Tool, Sports 역할을 균형 있게 구성하고 불필요한 중복을 줄인다. 실제 착용성과 기계식 비중을 중요하게 평가하며 브랜드와 가격대는 단계적으로 상향한다.";
        p.coreWatches = "Mido Ocean Star 200 B&W M026.930.11.011.00\n"
                + "Hamilton Khaki Field Mechanical 36 H69399930\n"
                + "Longines Spirit Zulu Time 42 Black L3.812.4.53.6";
        p.targetCollectionSize = 8;
        p.wishlistPriority = "1. Tudor Pelagos 42 Blue M25600TB-0001\n"
                + "2. Cartier Santos Medium\n"
                + "3. Rolex Explorer 36 124270\n"
                + "4. Grand Seiko SLGA009\n"
                + "5. TAG Heuer Formula 1 Solargraph WBY111E.BA0042";
        p.longTermPlan = "다이버 상향은 Pelagos 42 Blue, 비원형 스포츠·드레스 역할은 Santos Medium, 컴팩트 범용 스포츠는 Explorer 36, 고급 마감과 장기 완성도는 SLGA009, Solar-Quartz 보완 역할은 TAG Solargraph로 구성한다. 구체적인 교체 대상은 Wish 항목에 연결된 현재 시계를 우선한다.";
        return p;
    }

    public static CollectionEvaluationProfile load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            CollectionEvaluationProfile defaults = defaultProfile();
            save(context, defaults);
            return defaults;
        }
        try {
            return fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            CollectionEvaluationProfile defaults = defaultProfile();
            save(context, defaults);
            return defaults;
        }
    }

    public static void save(Context context, CollectionEvaluationProfile profile) {
        if (profile == null) profile = defaultProfile();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, profile.toJson().toString())
                .apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_JSON)
                .apply();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("profile_version", PROFILE_VERSION);
            json.put("movement_preference", safe(movementPreference));
            json.put("preferred_colors", safe(preferredColors));
            json.put("disliked_colors", safe(dislikedColors));
            json.put("collection_direction", safe(collectionDirection));
            json.put("core_watches", safe(coreWatches));
            json.put("target_collection_size", Math.max(1, targetCollectionSize));
            json.put("wishlist_priority", safe(wishlistPriority));
            json.put("long_term_plan", safe(longTermPlan));
            JSONObject weights = new JSONObject();
            weights.put("role_diversity", 25);
            weights.put("balance", 25);
            weights.put("overlap_management", 20);
            weights.put("practicality", 15);
            weights.put("long_term_completeness", 15);
            json.put("metric_weights_percent", weights);
        } catch (Exception ignored) {}
        return json;
    }

    public String fingerprint() {
        return sha256(toJson().toString());
    }

    private static CollectionEvaluationProfile fromJson(JSONObject json) {
        CollectionEvaluationProfile defaults = defaultProfile();
        CollectionEvaluationProfile p = new CollectionEvaluationProfile();
        p.movementPreference = json.optString("movement_preference", defaults.movementPreference);
        p.preferredColors = json.optString("preferred_colors", defaults.preferredColors);
        p.dislikedColors = json.optString("disliked_colors", defaults.dislikedColors);
        p.collectionDirection = json.optString("collection_direction", defaults.collectionDirection);
        p.coreWatches = json.optString("core_watches", defaults.coreWatches);
        p.targetCollectionSize = Math.max(1, json.optInt("target_collection_size", defaults.targetCollectionSize));
        p.wishlistPriority = json.optString("wishlist_priority", defaults.wishlistPriority);
        p.longTermPlan = json.optString("long_term_plan", defaults.longTermPlan);
        return p;
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(safe(value).hashCode());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
