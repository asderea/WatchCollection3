package com.watchcollection.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SharedPageAnalyzer가 선별한 실제 상품 정보를 스트랩 항목으로 변환합니다.
 * 특정 쇼핑몰 전용 규칙 대신 상품 페이지 전반의 구조화 데이터와 본문을 함께 사용합니다.
 */
public final class StrapProductLookup {
    private static final Pattern WIDTH_LABELED = Pattern.compile(
            "(?i)(?:lug\\s*width|strap\\s*width|band\\s*width|watch\\s*band\\s*width|size|width|러그\\s*폭|스트랩\\s*폭|밴드\\s*폭)" +
                    "[^0-9]{0,32}(18|19|20|21|22)\\s*(?:mm|㎜)");
    private static final Pattern WIDTH_GENERIC = Pattern.compile("(?i)(?<!\\d)(18|19|20|21|22)\\s*(?:mm|㎜)(?!\\d)");

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static class Result {
        public String finalUrl = "";
        public String maker = "";
        public String name = "";
        public int widthMm = -1;
        public String color = "";
        public String material = "";
        public String buckle = "";
        public String description = "";
        public String imageUrl = "";
        public final List<String> imageCandidates = new ArrayList<>();
        public String message = "";
    }

    private StrapProductLookup() {}

    public static Result lookup(String rawUrl, ProgressListener listener) throws Exception {
        return lookup(rawUrl, "", listener);
    }

    public static Result lookup(String rawUrl, String sharedText, ProgressListener listener) throws Exception {
        SharedPageAnalyzer.Result page = SharedPageAnalyzer.analyze(rawUrl, sharedText,
                message -> progress(listener, message));
        return fromPage(page, listener);
    }

    public static Result fromPage(SharedPageAnalyzer.Result page) {
        return fromPage(page, null);
    }

    private static Result fromPage(SharedPageAnalyzer.Result page, ProgressListener listener) {
        Result out = new Result();
        if (page == null) return out;

        out.finalUrl = firstNonEmpty(page.canonicalUrl, page.finalUrl);
        out.maker = cleanText(page.brand);
        out.name = cleanStrapName(page.productName, out.maker);
        out.description = trimDescription(page.description);
        out.imageCandidates.addAll(page.imageCandidates);
        if (!out.imageCandidates.isEmpty()) out.imageUrl = out.imageCandidates.get(0);

        String combined = joinNonEmpty(" ", page.productName, page.brand, page.category,
                page.description, page.structuredText,
                page.bodyText.length() > 160000 ? page.bodyText.substring(0, 160000) : page.bodyText);
        out.widthMm = inferWidth(combined);
        out.material = normalizeMaterial(combined);
        out.color = inferColor(combined);
        out.buckle = inferBuckle(combined);

        // 플랫폼 이름만 검출된 경우는 빈칸으로 남겨 사용자가 잘못 저장하지 않게 합니다.
        if (isGenericPlatformLabel(out.maker)) out.maker = "";
        if (isGenericPlatformLabel(out.name)) out.name = "";

        List<String> found = new ArrayList<>();
        if (!out.maker.isEmpty()) found.add("제작사");
        if (!out.name.isEmpty()) found.add("상품명");
        if (out.widthMm > 0) found.add("러그 폭");
        if (!out.material.isEmpty() && !"Extra".equals(out.material)) found.add("재질");
        if (!out.color.isEmpty()) found.add("색상");
        if (!out.imageCandidates.isEmpty()) found.add("대표사진 후보 " + out.imageCandidates.size() + "개");
        out.message = found.isEmpty()
                ? "상품 페이지는 열었지만 확정할 수 있는 스트랩 정보를 찾지 못했습니다."
                : "확인한 정보: " + String.join(" · ", found);
        progress(listener, out.message);
        return out;
    }

    private static int inferWidth(String text) {
        Matcher labeled = WIDTH_LABELED.matcher(text == null ? "" : text);
        if (labeled.find()) return Integer.parseInt(labeled.group(1));
        Matcher generic = WIDTH_GENERIC.matcher(text == null ? "" : text);
        Set<Integer> found = new LinkedHashSet<>();
        while (generic.find()) {
            found.add(Integer.parseInt(generic.group(1)));
            if (found.size() > 1) return -1;
        }
        return found.size() == 1 ? found.iterator().next() : -1;
    }

    private static String normalizeMaterial(String raw) {
        String x = cleanText(raw).toLowerCase(Locale.ROOT);
        if (x.contains("leather") || x.contains("calf") || x.contains("cowhide") ||
                x.contains("alligator") || x.contains("crocodile") || x.contains("shell cordovan") ||
                x.contains("가죽") || x.contains("카프") || x.contains("코도반")) return "Leather";
        if (x.contains("rubber") || x.contains("silicone") || x.contains("fkm") ||
                x.contains("러버") || x.contains("실리콘")) return "Rubber";
        if (x.contains("milanese") || x.contains("mesh") || x.contains("메쉬") || x.contains("밀레니즈")) return "Mesh";
        if (x.contains("nato") || x.contains("nylon") || x.contains("canvas") ||
                x.contains("fabric") || x.contains("나토") || x.contains("패브릭") || x.contains("캔버스")) return "Nato";
        if (x.contains("bracelet") || x.contains("stainless steel") || x.contains("titanium") ||
                x.contains("metal band") || x.contains("브레이슬릿") || x.contains("스틸") || x.contains("티타늄")) return "Bracelet";
        return "Extra";
    }

    private static String inferColor(String text) {
        String lower = cleanText(text).toLowerCase(Locale.ROOT);
        String[][] colors = {
                {"dark navy", "Dark Navy"}, {"navy", "Navy"}, {"dark blue", "Dark Blue"}, {"blue", "Blue"},
                {"black", "Black"}, {"brown", "Brown"}, {"tan", "Tan"}, {"cognac", "Cognac"},
                {"burgundy", "Burgundy"}, {"red", "Red"}, {"green", "Green"}, {"olive", "Olive"},
                {"gray", "Gray"}, {"grey", "Gray"}, {"white", "White"}, {"beige", "Beige"},
                {"orange", "Orange"}, {"yellow", "Yellow"}, {"purple", "Purple"},
                {"네이비", "Navy"}, {"파랑", "Blue"}, {"블루", "Blue"}, {"검정", "Black"},
                {"블랙", "Black"}, {"갈색", "Brown"}, {"브라운", "Brown"}, {"카멜", "Tan"},
                {"녹색", "Green"}, {"그린", "Green"}, {"올리브", "Olive"}, {"회색", "Gray"},
                {"그레이", "Gray"}, {"흰색", "White"}, {"화이트", "White"}, {"베이지", "Beige"}
        };
        for (String[] c : colors) if (lower.contains(c[0])) return c[1];
        return "";
    }

    private static String inferBuckle(String text) {
        String x = cleanText(text).toLowerCase(Locale.ROOT);
        if (x.contains("deployant") || x.contains("deployment clasp") || x.contains("디버클")) return "Deployant";
        if (x.contains("butterfly clasp") || x.contains("butterfly buckle") || x.contains("버터플라이")) return "Butterfly clasp";
        if (x.contains("folding clasp") || x.contains("fold-over clasp") || x.contains("폴딩 클라스프")) return "Folding clasp";
        if (x.contains("tang buckle") || x.contains("pin buckle") || x.contains("핀 버클") || x.contains("버클")) return "Pin buckle";
        return "";
    }

    private static String cleanStrapName(String raw, String maker) {
        String name = cleanText(raw);
        if (name.isEmpty()) return "";
        name = name.replaceAll("(?i)\\s*[|·｜]\\s*(?:official|official store|shop|store|스마트스토어|네이버 쇼핑).*$", "");
        if (!maker.isEmpty()) {
            name = name.replaceAll("(?i)\\s*[|–—-]\\s*" + Pattern.quote(maker) + "\\s*$", "");
        }
        return name.trim();
    }

    private static boolean isGenericPlatformLabel(String raw) {
        String x = cleanText(raw).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]", "");
        return x.isEmpty() || x.equals("naver") || x.equals("네이버") || x.equals("naverstore") ||
                x.equals("네이버스토어") || x.equals("smartstore") || x.equals("스마트스토어") ||
                x.equals("naversmartstore") || x.equals("네이버스마트스토어") ||
                x.equals("store") || x.equals("shop") || x.equals("shopping") || x.equals("쇼핑");
    }

    private static String trimDescription(String value) {
        String text = cleanText(value);
        return text.length() <= 500 ? text : text.substring(0, 497).trim() + "…";
    }

    private static String cleanText(String value) {
        return value == null ? "" : value
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String joinNonEmpty(String delimiter, String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) if (value != null && !value.trim().isEmpty()) out.add(value.trim());
        return String.join(delimiter, out);
    }

    private static void progress(ProgressListener listener, String message) {
        if (listener != null && message != null && !message.trim().isEmpty()) listener.onProgress(message.trim());
    }
}
