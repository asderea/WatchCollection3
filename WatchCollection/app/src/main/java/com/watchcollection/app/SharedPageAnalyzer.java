package com.watchcollection.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 특정 쇼핑몰에 종속되지 않고 공개 상품 페이지를 분석합니다.
 *
 * 핵심 원칙
 * - 플랫폼 이름이나 스토어 로고를 상품명/대표사진으로 확정하지 않는다.
 * - JSON-LD Product, 임베디드 상품 JSON, 공유 문구, H1, Open Graph를
 *   서로 다른 신뢰도로 평가한다.
 * - 후보를 점수화하여 실제 상품명/브랜드/대표사진을 선택한다.
 * - 페이지가 시계 상세인지 스트랩/브레이슬릿 구매 페이지인지 분류한다.
 */
public final class SharedPageAnalyzer {
    private static final int MAX_HTML_BYTES = 6 * 1024 * 1024;
    private static final int MAX_JSON_NODES = 12000;

    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern LINK_TAG = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern TITLE_TAG = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern H1_TAG = Pattern.compile("(?is)<h1\\b[^>]*>(.*?)</h1>");
    private static final Pattern H2_TAG = Pattern.compile("(?is)<h2\\b[^>]*>(.*?)</h2>");
    private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script>");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://[^\\s<>\\[\\]{}\"']+");
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?i)(?<![A-Z0-9])(?=[A-Z0-9._/-]{4,32}(?![A-Z0-9]))(?=[A-Z0-9._/-]*[A-Z])(?=[A-Z0-9._/-]*\\d)[A-Z0-9]+(?:[._/-][A-Z0-9]+)*");

    private static final Set<String> GENERIC_PLATFORM_TERMS = new LinkedHashSet<>();
    private static final Set<String> WATCH_BRANDS = new LinkedHashSet<>();
    private static final String[] STRAP_TERMS = {
            "watch strap", "watch band", "watch bracelet", "replacement strap", "replacement band",
            "시계 스트랩", "시계줄", "시계 줄", "스트랩", "러버 밴드", "가죽 밴드",
            "가죽 스트랩", "러버 스트랩", "메탈 브레이슬릿", "브레이슬릿", "나토 밴드",
            "nato strap", "nato band", "leather strap", "rubber strap", "silicone strap",
            "milanese", "mesh bracelet", "lug width", "러그 폭", "러그폭", "spring bar"
    };
    private static final String[] WATCH_TERMS = {
            "wristwatch", "손목시계", "automatic watch", "mechanical watch", "quartz watch",
            "오토매틱 시계", "기계식 시계", "쿼츠 시계", "다이버 워치", "파일럿 워치",
            "chronograph", "크로노그래프", "case diameter", "case thickness", "water resistance",
            "power reserve", "movement caliber", "무브먼트", "칼리버", "방수", "시계 케이스"
    };

    static {
        Collections.addAll(WATCH_BRANDS,
                "seiko", "grand seiko", "citizen", "casio", "g-shock", "hamilton", "mido", "tissot",
                "longines", "omega", "rolex", "tudor", "tag heuer", "cartier", "orient", "laco",
                "romanson", "bulova", "timex", "oris", "rado", "certina", "sinn", "nomos", "baltic",
                "breitling", "iwc", "panerai", "zenith", "blancpain", "breguet", "jaeger-lecoultre",
                "audemars piguet", "patek philippe", "vacheron constantin", "christopher ward",
                "venezianico", "doxa", "squale", "smiths", "nivada", "victorinox", "swatch");
        Collections.addAll(GENERIC_PLATFORM_TERMS,
                "naver", "네이버", "naver shopping", "네이버 쇼핑", "naver smartstore", "스마트스토어",
                "네이버 스마트스토어", "store", "shop", "shopping", "스토어", "쇼핑", "공식몰",
                "공식 스토어", "official store", "online store", "온라인 스토어", "상품 상세",
                "상품상세", "product detail", "product page", "home", "홈", "mall", "마켓",
                "market", "brand store", "브랜드 스토어", "판매자 스토어");
    }

    public enum Kind {
        WATCH,
        STRAP,
        UNKNOWN
    }

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static class Result {
        public String finalUrl = "";
        public String canonicalUrl = "";
        public String sharedTitle = "";
        public String productName = "";
        public String brand = "";
        public String referenceNo = "";
        public String description = "";
        public String category = "";
        public String structuredText = "";
        public String bodyText = "";
        public Kind kind = Kind.UNKNOWN;
        public int watchScore = 0;
        public int strapScore = 0;
        public final List<String> imageCandidates = new ArrayList<>();
        public final List<String> evidence = new ArrayList<>();
    }

    private SharedPageAnalyzer() {}

    public static Result analyze(String rawUrl, String sharedText, ProgressListener listener) throws Exception {
        String requested = normalizeUrl(rawUrl);
        progress(listener, "공유 링크의 최종 주소를 확인합니다.");
        Page page = fetch(requested);
        progress(listener, "페이지의 상품 데이터 후보를 수집합니다.");

        Collector collector = new Collector(page.finalUrl);
        collector.sharedTitle = extractSharedTitle(sharedText);
        if (!collector.sharedTitle.isEmpty()) {
            collector.addTitle(collector.sharedTitle, 96, "공유 문구");
        }

        collectMetaCandidates(page.html, collector);
        collectHeadingCandidates(page.html, collector);
        collectJsonCandidates(page.html, collector);
        collectRegexJsonFallbacks(page.html, collector);
        collectImageTags(page.html, collector);

        String body = stripHtml(page.html);
        if (body.length() > 160000) body = body.substring(0, 160000);
        collector.bodyText = body;

        Result result = new Result();
        result.finalUrl = page.finalUrl;
        result.canonicalUrl = firstNonEmpty(collector.canonicalUrl, page.finalUrl);
        result.sharedTitle = collector.sharedTitle;
        result.productName = collector.bestTitle();
        result.brand = collector.bestBrand();
        result.referenceNo = collector.bestReference(result.productName);
        result.description = collector.bestDescription();
        result.category = collector.bestCategory();
        result.structuredText = collector.structuredText();
        result.bodyText = body;
        result.imageCandidates.addAll(collector.bestImages(result.productName));
        result.evidence.addAll(collector.evidence);

        classify(result);

        progress(listener, "상품명 후보를 교차검증했습니다: " +
                (result.productName.isEmpty() ? "확정 못함" : result.productName));
        if (!result.brand.isEmpty()) progress(listener, "브랜드/제작사 후보: " + result.brand);
        progress(listener, "대표사진 후보 " + result.imageCandidates.size() + "개를 선별했습니다.");
        if (result.kind == Kind.STRAP) progress(listener, "스트랩/브레이슬릿 구매 페이지로 판단했습니다.");
        else if (result.kind == Kind.WATCH) progress(listener, "시계 상세 페이지로 판단했습니다.");
        else progress(listener, "시계와 스트랩 중 자동 분류가 애매합니다.");
        return result;
    }

    private static Page fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(25000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36 WatchCollection/1.21");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        con.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        con.setRequestProperty("Accept-Encoding", "gzip");
        con.setRequestProperty("Cache-Control", "no-cache");

        int code = con.getResponseCode();
        if (code < 200 || code >= 400) {
            con.disconnect();
            throw new IllegalStateException("페이지 응답 오류: HTTP " + code);
        }

        Charset charset = charsetFromContentType(con.getContentType());
        String encoding = con.getContentEncoding();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String finalUrl;
        InputStream raw = con.getInputStream();
        InputStream in = encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")
                ? new GZIPInputStream(raw) : raw;
        try (InputStream closeable = in) {
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = closeable.read(buffer)) > 0) {
                int write = Math.min(n, MAX_HTML_BYTES - total);
                if (write > 0) out.write(buffer, 0, write);
                total += write;
                if (total >= MAX_HTML_BYTES) break;
            }
            finalUrl = con.getURL().toString();
        } finally {
            con.disconnect();
        }
        return new Page(finalUrl, new String(out.toByteArray(), charset));
    }

    private static void collectMetaCandidates(String html, Collector c) {
        addMetaTitle(html, c, "property", "product:title", 105, "product:title");
        addMetaTitle(html, c, "itemprop", "name", 98, "itemprop name");
        addMetaTitle(html, c, "property", "og:title", 72, "og:title");
        addMetaTitle(html, c, "name", "twitter:title", 66, "twitter:title");
        addMetaTitle(html, c, "name", "title", 58, "meta title");

        String title = tagText(TITLE_TAG, html);
        if (!title.isEmpty()) c.addTitle(title, 48, "HTML title");

        String description = firstNonEmpty(
                meta(html, "property", "og:description"),
                meta(html, "name", "description"),
                meta(html, "name", "twitter:description"));
        if (!description.isEmpty()) c.addDescription(description, 65, "meta description");

        String brand = firstNonEmpty(
                meta(html, "property", "product:brand"),
                meta(html, "name", "brand"),
                meta(html, "itemprop", "brand"),
                meta(html, "property", "og:brand"));
        if (!brand.isEmpty()) c.addBrand(brand, 88, "meta brand");

        String category = firstNonEmpty(
                meta(html, "property", "product:category"),
                meta(html, "name", "category"),
                meta(html, "itemprop", "category"));
        if (!category.isEmpty()) c.addCategory(category, 82, "meta category");

        String image = firstNonEmpty(
                meta(html, "property", "product:image"),
                meta(html, "itemprop", "image"),
                meta(html, "property", "og:image:secure_url"),
                meta(html, "property", "og:image"),
                meta(html, "name", "twitter:image"),
                meta(html, "name", "twitter:image:src"),
                imageSrcLink(html));
        if (!image.isEmpty()) c.addImage(image, "", 72, "meta image");

        Matcher links = LINK_TAG.matcher(html);
        while (links.find()) {
            String tag = links.group();
            if ("canonical".equalsIgnoreCase(attr(tag, "rel"))) {
                String href = absoluteUrl(c.baseUrl, attr(tag, "href"));
                if (!href.isEmpty()) c.canonicalUrl = href;
            }
        }
    }

    private static void addMetaTitle(String html, Collector c, String attrName, String attrValue,
                                     int score, String source) {
        String value = meta(html, attrName, attrValue);
        if (!value.isEmpty()) c.addTitle(value, score, source);
    }

    private static void collectHeadingCandidates(String html, Collector c) {
        Matcher h1 = H1_TAG.matcher(html);
        int count = 0;
        while (h1.find() && count++ < 8) c.addTitle(h1.group(1), 92, "H1");
        Matcher h2 = H2_TAG.matcher(html);
        count = 0;
        while (h2.find() && count++ < 8) c.addTitle(h2.group(1), 54, "H2");
    }

    private static void collectJsonCandidates(String html, Collector c) {
        Matcher scripts = SCRIPT_TAG.matcher(html);
        int scriptCount = 0;
        int[] nodes = {0};
        while (scripts.find() && scriptCount++ < 120) {
            String attrs = scripts.group(1);
            String raw = decodeEntities(scripts.group(2)).trim();
            if (raw.isEmpty() || raw.length() > 3 * 1024 * 1024) continue;
            String type = attr("<script " + attrs + ">", "type").toLowerCase(Locale.ROOT);
            boolean likelyJson = type.contains("json") || raw.startsWith("{") || raw.startsWith("[");
            if (!likelyJson) continue;
            try {
                Object root = new JSONTokener(raw).nextValue();
                walkJson(root, "", 0, nodes, c);
            } catch (Exception ignored) {
                // 일부 사이트는 script 안에 JS 표현식을 섞습니다. 아래 regex fallback에서 처리합니다.
            }
            if (nodes[0] >= MAX_JSON_NODES) break;
        }
    }

    private static void walkJson(Object node, String parentKey, int depth, int[] nodes, Collector c) {
        if (node == null || depth > 14 || nodes[0]++ > MAX_JSON_NODES) return;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            boolean productLike = isProductObject(o);

            if (productLike) {
                String name = stringByKeys(o, "productName", "goodsName", "itemName", "displayName", "name", "title");
                String brand = valueByKeys(o, "brand", "brandName", "maker", "makerName", "manufacturer", "vendor");
                String description = stringByKeys(o, "description", "productDescription", "goodsDescription", "summary");
                String category = valueByKeys(o, "category", "categoryName", "productCategory", "breadcrumb");
                String ref = valueByKeys(o, "sku", "model", "modelNumber", "productNo", "goodsNo", "itemNo", "productId");
                if (!name.isEmpty()) c.addTitle(name, 118, "상품 JSON");
                if (!brand.isEmpty()) c.addBrand(brand, 112, "상품 JSON");
                if (!description.isEmpty()) c.addDescription(description, 100, "상품 JSON");
                if (!category.isEmpty()) c.addCategory(category, 100, "상품 JSON");
                if (!ref.isEmpty()) c.addReference(ref, 108, "상품 JSON");
                collectImagesFromObject(o, c, 116, "상품 JSON");
                c.addStructuredText(joinNonEmpty(" ", name, brand, description, category, ref));
            }

            JSONArray names = o.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i);
                Object child = o.opt(key);
                String lower = key.toLowerCase(Locale.ROOT);

                if (!productLike && isSpecificNameKey(lower)) {
                    String value = stringValue(child);
                    if (!value.isEmpty()) c.addTitle(value, 106, "임베디드 " + key);
                }
                if (!productLike && isBrandKey(lower)) {
                    String value = stringValue(child);
                    if (!value.isEmpty()) c.addBrand(value, 92, "임베디드 " + key);
                }
                if (!productLike && isReferenceKey(lower)) {
                    String value = stringValue(child);
                    if (!value.isEmpty()) c.addReference(value, 88, "임베디드 " + key);
                }
                if (isImageKey(lower)) collectImageValue(child, c, 98, "임베디드 " + key);
                walkJson(child, key, depth + 1, nodes, c);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) walkJson(a.opt(i), parentKey, depth + 1, nodes, c);
        }
    }

    private static boolean isProductObject(JSONObject o) {
        Object type = o.opt("@type");
        if (containsType(type, "Product")) return true;

        boolean specificName = hasAnyKey(o, "productName", "goodsName", "itemName", "displayName");
        boolean name = specificName || hasAnyKey(o, "name", "title");
        boolean productSignal = hasAnyKey(o,
                "offers", "price", "salePrice", "discountPrice", "sku", "model", "modelNumber",
                "productNo", "goodsNo", "itemNo", "productId", "productImage", "representativeImage",
                "image", "images", "category", "categoryName", "brand", "brandName", "vendor");
        return specificName || (name && productSignal);
    }

    private static void collectImagesFromObject(JSONObject o, Collector c, int score, String source) {
        String[] keys = {
                "image", "images", "imageUrl", "imageUrls", "representativeImage",
                "representativeImageUrl", "mainImage", "mainImageUrl", "thumbnail", "thumbnailUrl",
                "productImage", "productImages", "originalImage", "originalImageUrl", "contentUrl"
        };
        for (String key : keys) if (o.has(key)) collectImageValue(o.opt(key), c, score, source + " " + key);
    }

    private static void collectImageValue(Object value, Collector c, int score, String source) {
        if (value instanceof String) {
            c.addImage((String) value, "", score, source);
        } else if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String url = valueByKeys(o, "url", "contentUrl", "imageUrl", "src", "originalUrl");
            String alt = valueByKeys(o, "caption", "name", "alt", "description");
            if (!url.isEmpty()) c.addImage(url, alt, score, source);
            JSONArray names = o.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) collectImageValue(o.opt(names.optString(i)), c, score - 6, source);
            }
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) collectImageValue(a.opt(i), c, score - Math.min(12, i), source);
        }
    }

    private static void collectRegexJsonFallbacks(String html, Collector c) {
        String[] nameKeys = {"productName", "goodsName", "itemName", "displayName"};
        for (String key : nameKeys) {
            for (String value : regexJsonValues(html, key, 10)) c.addTitle(value, 100, "JSON 문자열 " + key);
        }
        String[] brandKeys = {"brandName", "makerName", "manufacturer", "vendor"};
        for (String key : brandKeys) {
            for (String value : regexJsonValues(html, key, 8)) c.addBrand(value, 88, "JSON 문자열 " + key);
        }
        String[] refKeys = {"sku", "modelNumber", "productNo", "goodsNo", "itemNo"};
        for (String key : refKeys) {
            for (String value : regexJsonValues(html, key, 8)) c.addReference(value, 84, "JSON 문자열 " + key);
        }
        String[] imageKeys = {"representativeImageUrl", "mainImageUrl", "productImageUrl", "originalImageUrl", "imageUrl"};
        for (String key : imageKeys) {
            for (String value : regexJsonValues(html, key, 16)) c.addImage(value, "", 92, "JSON 문자열 " + key);
        }
    }

    private static List<String> regexJsonValues(String html, String key, int max) {
        List<String> out = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?is)[\\\"]" + Pattern.quote(key) +
                "[\\\"]\\s*:\\s*[\\\"]((?:\\\\.|[^\\\"])*)[\\\"]");
        Matcher m = pattern.matcher(html);
        while (m.find() && out.size() < max) {
            String value = unescapeJsonString(m.group(1));
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    private static void collectImageTags(String html, Collector c) {
        Matcher tags = IMG_TAG.matcher(html);
        int count = 0;
        while (tags.find() && count++ < 300) {
            String tag = tags.group();
            String src = firstNonEmpty(attr(tag, "data-original"), attr(tag, "data-src"),
                    attr(tag, "data-lazy-src"), attr(tag, "src"));
            String alt = firstNonEmpty(attr(tag, "alt"), attr(tag, "title"));
            if (!src.isEmpty()) c.addImage(src, alt, 48, "IMG");
        }
    }

    private static void classify(Result r) {
        String title = normalizeForMatch(r.productName);
        String category = normalizeForMatch(r.category);
        String description = normalizeForMatch(r.description);
        String structured = normalizeForMatch(r.structuredText);
        String body = normalizeForMatch(r.bodyText.length() > 80000 ? r.bodyText.substring(0, 80000) : r.bodyText);
        String all = joinNonEmpty(" ", title, category, description, structured, body);

        int strap = 0;
        int watch = 0;
        for (String term : STRAP_TERMS) {
            String needle = normalizeForMatch(term);
            if (title.contains(needle)) strap += 16;
            if (category.contains(needle)) strap += 12;
            if (description.contains(needle)) strap += 5;
            if (structured.contains(needle)) strap += 4;
            if (body.contains(needle)) strap += 1;
        }
        for (String term : WATCH_TERMS) {
            String needle = normalizeForMatch(term);
            if (title.contains(needle)) watch += 14;
            if (category.contains(needle)) watch += 10;
            if (description.contains(needle)) watch += 4;
            if (structured.contains(needle)) watch += 3;
            if (body.contains(needle)) watch += 1;
        }

        if (containsAny(title, "strap", "band", "bracelet", "스트랩", "시계줄", "밴드", "브레이슬릿")) strap += 22;
        if (containsAny(title, "watch", "wristwatch", "시계", "chronograph", "automatic", "오토매틱")) watch += 9;
        if (strap > 0 && containsAny(title, "watch strap", "watch band", "시계 스트랩", "시계줄")) watch = Math.max(0, watch - 8);
        if (containsAny(all, "18mm", "19mm", "20mm", "21mm", "22mm", "18 mm", "20 mm", "22 mm")) strap += 3;

        String brandText=normalizeForMatch(joinNonEmpty(" ",r.brand,r.productName));
        for(String brand:WATCH_BRANDS) {
            if(brandText.contains(normalizeForMatch(brand))) { watch += 12; break; }
        }
        int specSignals=0;
        String[] specs={"case diameter","diameter","case thickness","lug to lug","water resistance",
                "power reserve","movement","caliber","무브먼트","칼리버","방수","파워리저브","케이스 직경"};
        for(String spec:specs) if(all.contains(normalizeForMatch(spec))) specSignals++;
        watch += Math.min(15,specSignals*3);
        if (!r.referenceNo.isEmpty() && strap < 10) watch += 6;

        r.strapScore = strap;
        r.watchScore = watch;
        if (strap >= 8 && strap >= watch + 3) r.kind = Kind.STRAP;
        else if (watch >= 8 && watch >= strap + 3) r.kind = Kind.WATCH;
        else r.kind = Kind.UNKNOWN;
    }

    private static String extractSharedTitle(String sharedText) {
        String value = cleanText(sharedText);
        if (value.isEmpty()) return "";
        value = URL_PATTERN.matcher(value).replaceAll(" ");
        value = value.replaceAll("(?i)\\b(shared via|share|공유하기|링크 공유)\\b", " ");
        String[] lines = value.split("[\\r\\n]+");
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        for (String line : lines) {
            String cleaned = cleanProductText(line);
            int score = titleQuality(cleaned);
            if (score > bestScore) {
                best = cleaned;
                bestScore = score;
            }
        }
        if (bestScore < 0) return "";
        return best;
    }

    private static String cleanProductText(String raw) {
        String value = cleanText(raw);
        if (value.isEmpty()) return "";
        value = value.replaceAll("(?i)\\s*[|｜·•]\\s*(?:네이버\\s*)?(?:스마트스토어|쇼핑|스토어).*$", "");
        value = value.replaceAll("(?i)\\s+[–—-]\\s+(?:official\\s+store|online\\s+store|smartstore|store|shop)\\s*$", "");
        value = value.replaceAll("\\s+", " ").trim();

        String[] parts = value.split("\\s*[|｜·•]\\s*|\\s+[–—]\\s+|\\s+-\\s+");
        String best = value;
        int bestScore = titleQuality(value);
        for (String part : parts) {
            String candidate = cleanText(part);
            int score = titleQuality(candidate) + 8;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best.trim();
    }

    private static int titleQuality(String raw) {
        String value = cleanText(raw);
        if (value.isEmpty()) return -1000;
        String normalized = normalizeForMatch(value);
        if (isGenericPlatformText(value)) return -500;
        int score = 0;
        int len = value.length();
        if (len >= 5 && len <= 180) score += 18;
        else if (len > 240) score -= 25;
        if (containsAny(normalized, "strap", "band", "bracelet", "watch", "시계", "스트랩", "밴드", "브레이슬릿")) score += 16;
        if (Pattern.compile(".*[A-Za-z가-힣].*\\d.*|.*\\d.*[A-Za-z가-힣].*").matcher(value).matches()) score += 7;
        if (normalized.contains("naver") || normalized.contains("네이버") || normalized.contains("smartstore") || normalized.contains("스마트스토어")) score -= 45;
        if (value.matches("^[\\p{Punct}\\s]+$")) score -= 100;
        return score;
    }

    private static boolean isGenericPlatformText(String raw) {
        String value = normalizeForMatch(raw);
        if (value.isEmpty()) return true;
        for (String term : GENERIC_PLATFORM_TERMS) {
            String t = normalizeForMatch(term);
            if (value.equals(t)) return true;
        }
        String reduced = value
                .replace("naver", "").replace("네이버", "")
                .replace("smartstore", "").replace("스마트스토어", "")
                .replace("shopping", "").replace("쇼핑", "")
                .replace("store", "").replace("스토어", "")
                .replace("shop", "").replace("공식몰", "")
                .replaceAll("[^a-z0-9가-힣]", "");
        return reduced.length() < 2;
    }

    private static String meta(String html, String attrName, String attrValue) {
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            String key = attr(tag, attrName);
            if (!attrValue.equalsIgnoreCase(key)) continue;
            String content = attr(tag, "content");
            if (!content.isEmpty()) return cleanText(content);
        }
        return "";
    }

    private static String imageSrcLink(String html) {
        Matcher tags = LINK_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (!"image_src".equalsIgnoreCase(attr(tag, "rel"))) continue;
            String href = attr(tag, "href");
            if (!href.isEmpty()) return cleanText(href);
        }
        return "";
    }

    private static String tagText(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        return m.find() ? cleanText(m.group(1)) : "";
    }

    private static String stripHtml(String html) {
        return cleanText(html
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript\\b[^>]*>.*?</noscript>", " ")
                .replaceAll("(?is)<[^>]+>", " "));
    }

    private static String attr(String tag, String name) {
        Pattern quoted = Pattern.compile("(?is)(?:^|\\s)" + Pattern.quote(name) +
                "\\s*=\\s*(['\"])(.*?)\\1");
        Matcher q = quoted.matcher(tag);
        if (q.find()) return decodeEntities(q.group(2));
        Pattern bare = Pattern.compile("(?is)(?:^|\\s)" + Pattern.quote(name) +
                "\\s*=\\s*([^\\s>]+)");
        Matcher b = bare.matcher(tag);
        return b.find() ? decodeEntities(b.group(1)) : "";
    }

    private static boolean containsType(Object value, String expected) {
        if (value instanceof String) return expected.equalsIgnoreCase((String) value);
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) if (expected.equalsIgnoreCase(a.optString(i))) return true;
        }
        return false;
    }

    private static boolean hasAnyKey(JSONObject o, String... keys) {
        for (String key : keys) if (o.has(key) && o.opt(key) != null && o.opt(key) != JSONObject.NULL) return true;
        return false;
    }

    private static String stringByKeys(JSONObject o, String... keys) {
        for (String key : keys) {
            String value = stringValue(o.opt(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String valueByKeys(JSONObject o, String... keys) {
        for (String key : keys) {
            String value = stringValue(o.opt(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String stringValue(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return cleanText(String.valueOf(value));
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            return cleanText(firstNonEmpty(o.optString("name"), o.optString("value"), o.optString("text"),
                    o.optString("url"), o.optString("contentUrl")));
        }
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            List<String> items = new ArrayList<>();
            for (int i = 0; i < Math.min(a.length(), 8); i++) {
                String s = stringValue(a.opt(i));
                if (!s.isEmpty()) items.add(s);
            }
            return String.join(" · ", items);
        }
        return "";
    }

    private static boolean isSpecificNameKey(String key) {
        return key.equals("productname") || key.equals("goodsname") || key.equals("itemname") ||
                key.equals("displayname") || key.equals("producttitle") || key.equals("goodstitle");
    }

    private static boolean isBrandKey(String key) {
        return key.equals("brandname") || key.equals("makername") || key.equals("manufacturer") ||
                key.equals("vendor") || key.equals("brand");
    }

    private static boolean isReferenceKey(String key) {
        return key.equals("sku") || key.equals("model") || key.equals("modelnumber") ||
                key.equals("productno") || key.equals("goodsno") || key.equals("itemno");
    }

    private static boolean isImageKey(String key) {
        return key.contains("image") || key.equals("thumbnail") || key.equals("thumbnailurl") ||
                key.equals("contenturl") || key.equals("picture") || key.equals("pictures");
    }

    private static String unescapeJsonString(String value) {
        if (value == null) return "";
        try {
            Object parsed = new JSONTokener("\"" + value + "\"").nextValue();
            return cleanText(String.valueOf(parsed));
        } catch (Exception ignored) {
            return cleanText(value.replace("\\/", "/").replace("\\\"", "\"").replace("\\n", " "));
        }
    }

    private static String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("링크를 입력하세요.");
        Matcher matcher = URL_PATTERN.matcher(value);
        if (matcher.find()) value = matcher.group();
        value = trimUrlPunctuation(value);
        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null) throw new IllegalArgumentException("올바른 링크가 아닙니다.");
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("올바른 링크가 아닙니다.");
        }
    }

    public static String extractFirstUrl(String text) {
        Matcher m = URL_PATTERN.matcher(text == null ? "" : text);
        return m.find() ? trimUrlPunctuation(m.group()) : "";
    }

    public static String extractSharedTitleHint(String text) {
        return extractSharedTitle(text);
    }

    private static String trimUrlPunctuation(String value) {
        String out = value == null ? "" : value.trim();
        while (!out.isEmpty() && ".,;:!?)>]}\"'".indexOf(out.charAt(out.length() - 1)) >= 0) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("(?i)charset\\s*=\\s*([a-zA-Z0-9._-]+)").matcher(contentType);
            if (m.find()) {
                try { return Charset.forName(m.group(1)); } catch (Exception ignored) {}
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String absoluteUrl(String base, String candidate) {
        String value = cleanText(candidate).replace("\\/", "/");
        if (value.isEmpty() || value.startsWith("data:") || value.startsWith("javascript:")) return "";
        try {
            URI resolved = new URI(base).resolve(value);
            String scheme = resolved.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return "";
            return resolved.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String decodeEntities(String value) {
        if (value == null) return "";
        String out = value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("\\/", "/");
        Matcher decimal = Pattern.compile("&#(\\d+);").matcher(out);
        StringBuffer sb = new StringBuffer();
        while (decimal.find()) {
            try {
                decimal.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(decimal.group(1)))));
            } catch (Exception e) {
                decimal.appendReplacement(sb, " ");
            }
        }
        decimal.appendTail(sb);
        return sb.toString();
    }

    private static String cleanText(String value) {
        return decodeEntities(value)
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("\\u002F", "/")
                .replace("\\u003A", ":")
                .replace("\\u0026", "&")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeForMatch(String value) {
        return cleanText(value).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_./\\-]+", " ")
                .replaceAll("[^a-z0-9가-힣 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, String... terms) {
        String normalized = normalizeForMatch(text);
        for (String term : terms) if (normalized.contains(normalizeForMatch(term))) return true;
        return false;
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

    private static class Page {
        final String finalUrl;
        final String html;
        Page(String finalUrl, String html) {
            this.finalUrl = finalUrl;
            this.html = html;
        }
    }

    private static class TextCandidate {
        final String value;
        final int score;
        final String source;
        TextCandidate(String value, int score, String source) {
            this.value = value;
            this.score = score;
            this.source = source;
        }
    }

    private static class ImageCandidate {
        final String url;
        final String alt;
        final int baseScore;
        final String source;
        ImageCandidate(String url, String alt, int baseScore, String source) {
            this.url = url;
            this.alt = alt;
            this.baseScore = baseScore;
            this.source = source;
        }
    }

    private static class Collector {
        final String baseUrl;
        String sharedTitle = "";
        String canonicalUrl = "";
        String bodyText = "";
        final List<TextCandidate> titles = new ArrayList<>();
        final List<TextCandidate> brands = new ArrayList<>();
        final List<TextCandidate> references = new ArrayList<>();
        final List<TextCandidate> descriptions = new ArrayList<>();
        final List<TextCandidate> categories = new ArrayList<>();
        final List<ImageCandidate> images = new ArrayList<>();
        final List<String> structured = new ArrayList<>();
        final List<String> evidence = new ArrayList<>();

        Collector(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        void addTitle(String raw, int baseScore, String source) {
            String cleaned = cleanProductText(raw);
            if (cleaned.isEmpty() || isGenericPlatformText(cleaned)) return;
            int score = baseScore + titleQuality(cleaned);
            titles.add(new TextCandidate(cleaned, score, source));
            evidence.add(source + " 상품명: " + cleaned);
        }

        void addBrand(String raw, int baseScore, String source) {
            String value = cleanProductText(raw);
            if (value.isEmpty() || isGenericPlatformText(value) || value.length() > 80) return;
            String normalized = normalizeForMatch(value);
            if (normalized.contains("seller") || normalized.contains("판매자") || normalized.contains("스토어")) return;
            brands.add(new TextCandidate(value, baseScore + Math.min(12, value.length()), source));
        }

        void addReference(String raw, int baseScore, String source) {
            String value = cleanText(raw);
            if (value.isEmpty() || value.length() > 48 || isGenericPlatformText(value)) return;
            references.add(new TextCandidate(value, baseScore, source));
        }

        void addDescription(String raw, int baseScore, String source) {
            String value = cleanText(raw);
            if (value.isEmpty() || isGenericPlatformText(value)) return;
            if (value.length() > 800) value = value.substring(0, 800);
            descriptions.add(new TextCandidate(value, baseScore, source));
        }

        void addCategory(String raw, int baseScore, String source) {
            String value = cleanText(raw);
            if (value.isEmpty() || isGenericPlatformText(value)) return;
            categories.add(new TextCandidate(value, baseScore, source));
        }

        void addImage(String rawUrl, String alt, int baseScore, String source) {
            String url = absoluteUrl(baseUrl, rawUrl);
            if (url.isEmpty()) return;
            images.add(new ImageCandidate(url, cleanText(alt), baseScore, source));
        }

        void addStructuredText(String value) {
            String cleaned = cleanText(value);
            if (!cleaned.isEmpty()) structured.add(cleaned);
        }

        String bestTitle() {
            if (titles.isEmpty()) return "";
            titles.sort((a, b) -> Integer.compare(b.score, a.score));
            return titles.get(0).value;
        }

        String bestBrand() {
            if (brands.isEmpty()) return "";
            Map<String, Integer> scores = new LinkedHashMap<>();
            Map<String, String> display = new LinkedHashMap<>();
            for (TextCandidate c : brands) {
                String key = normalizeForMatch(c.value);
                if (key.isEmpty()) continue;
                scores.put(key, scores.containsKey(key) ? scores.get(key) + c.score : c.score);
                display.put(key, c.value);
            }
            String best = "";
            int bestScore = Integer.MIN_VALUE;
            for (Map.Entry<String, Integer> e : scores.entrySet()) {
                if (e.getValue() > bestScore) {
                    bestScore = e.getValue();
                    best = display.get(e.getKey());
                }
            }
            return best;
        }

        String bestReference(String productName) {
            references.sort((a, b) -> Integer.compare(b.score, a.score));
            for (TextCandidate c : references) {
                String value = cleanText(c.value);
                if (validReference(value)) return value;
            }
            Matcher m = REFERENCE_PATTERN.matcher(productName == null ? "" : productName.toUpperCase(Locale.ROOT));
            return m.find() ? m.group() : "";
        }

        String bestDescription() {
            descriptions.sort((a, b) -> Integer.compare(b.score, a.score));
            return descriptions.isEmpty() ? "" : descriptions.get(0).value;
        }

        String bestCategory() {
            categories.sort((a, b) -> Integer.compare(b.score, a.score));
            return categories.isEmpty() ? "" : categories.get(0).value;
        }

        String structuredText() {
            String value = String.join(" · ", structured);
            return value.length() > 12000 ? value.substring(0, 12000) : value;
        }

        List<String> bestImages(String productName) {
            Map<String, ImageCandidate> unique = new LinkedHashMap<>();
            for (ImageCandidate c : images) {
                ImageCandidate existing = unique.get(c.url);
                if (existing == null || scoreImage(c, productName) > scoreImage(existing, productName)) unique.put(c.url, c);
            }
            List<ImageCandidate> list = new ArrayList<>(unique.values());
            list.sort((a, b) -> Integer.compare(scoreImage(b, productName), scoreImage(a, productName)));
            List<String> out = new ArrayList<>();
            for (ImageCandidate c : list) {
                if (scoreImage(c, productName) < 10) continue;
                out.add(c.url);
                if (out.size() >= 8) break;
            }
            return out;
        }

        private int scoreImage(ImageCandidate c, String productName) {
            int score = c.baseScore;
            String url = c.url.toLowerCase(Locale.ROOT);
            String path = url.replaceAll("[?#].*$", "");
            if (containsAny(path, "logo", "favicon", "sprite", "icon", "avatar", "profile", "seller", "brandmark", "watermark", "banner", "header", "footer")) score -= 140;
            if (containsAny(path, "product", "goods", "item", "main", "represent", "original", "detail", "large", "zoom")) score += 24;
            if (path.matches(".*\\.(jpg|jpeg|png|webp|avif)$")) score += 6;
            if (url.matches(".*(?:^|[/_-])(16|24|32|48|64|80|100|120)[x_-](16|24|32|48|64|80|100|120)(?:[/_.?-]|$).*") ) score -= 55;
            String alt = normalizeForMatch(c.alt);
            String product = normalizeForMatch(productName);
            if (!alt.isEmpty() && !product.isEmpty()) {
                int overlap = tokenOverlap(alt, product);
                score += overlap * 6;
            }
            return score;
        }
    }

    private static boolean validReference(String value) {
        String v = cleanText(value);
        if (v.length() < 3 || v.length() > 40) return false;
        if (isGenericPlatformText(v)) return false;
        return v.matches("(?i).*[A-Z].*\\d.*|.*\\d.*[A-Z].*") || v.matches("\\d{5,16}");
    }

    private static int tokenOverlap(String a, String b) {
        Set<String> x = new LinkedHashSet<>();
        for (String token : a.split(" ")) if (token.length() >= 2) x.add(token);
        int count = 0;
        for (String token : b.split(" ")) if (token.length() >= 2 && x.contains(token)) count++;
        return count;
    }
}
