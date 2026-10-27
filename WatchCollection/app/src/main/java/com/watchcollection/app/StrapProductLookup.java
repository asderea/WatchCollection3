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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 구매 페이지의 공개 HTML/구조화 데이터(JSON-LD/Open Graph)를 읽어
 * 스트랩 기본 정보와 대표 이미지 후보를 추출합니다.
 *
 * 자바스크립트 실행이나 로그인이 필요한 사이트는 일부 정보가 비어 있을 수 있으며,
 * 추출 결과는 저장 전에 사용자가 직접 수정할 수 있습니다.
 */
public final class StrapProductLookup {
    private static final int MAX_HTML_BYTES = 4 * 1024 * 1024;
    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern LINK_TAG = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern TITLE_TAG = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern JSON_LD = Pattern.compile(
            "(?is)<script\\b[^>]*type\\s*=\\s*['\"]application/ld\\+json['\"][^>]*>(.*?)</script>");
    private static final Pattern WIDTH_LABELED = Pattern.compile(
            "(?i)(?:lug\\s*width|strap\\s*width|band\\s*width|watch\\s*band\\s*width|size|width|러그\\s*폭|스트랩\\s*폭)" +
                    "[^0-9]{0,30}(18|19|20|21|22)\\s*(?:mm|㎜)");
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
        public String message = "";
    }

    private StrapProductLookup() {}

    public static Result lookup(String rawUrl, ProgressListener listener) throws Exception {
        String requested = normalizeUrl(rawUrl);
        progress(listener, "구매 페이지에 연결합니다.");
        Page page = fetch(requested);
        progress(listener, "상품 페이지의 공개 정보를 읽었습니다.");

        Result out = new Result();
        out.finalUrl = page.finalUrl;

        String ogTitle = meta(page.html, "property", "og:title");
        String twitterTitle = meta(page.html, "name", "twitter:title");
        String ogDescription = meta(page.html, "property", "og:description");
        String descriptionMeta = firstNonEmpty(
                ogDescription,
                meta(page.html, "name", "description"),
                meta(page.html, "name", "twitter:description"));
        String ogImage = firstNonEmpty(
                meta(page.html, "property", "og:image:secure_url"),
                meta(page.html, "property", "og:image"),
                meta(page.html, "name", "twitter:image"),
                meta(page.html, "name", "twitter:image:src"),
                imageSrcLink(page.html));
        String htmlTitle = title(page.html);

        ProductData product = parseStructuredProduct(page.html);
        progress(listener, product.found
                ? "상품 구조화 데이터(JSON-LD)를 확인했습니다."
                : "Open Graph와 페이지 본문을 기준으로 정보를 분석합니다.");

        String rawTitle = firstNonEmpty(product.name, ogTitle, twitterTitle, htmlTitle);
        out.maker = cleanText(firstNonEmpty(product.brand, inferKnownMaker(page.finalUrl), inferMakerFromTitle(rawTitle)));
        out.name = cleanProductName(rawTitle, out.maker);
        out.description = trimDescription(firstNonEmpty(product.description, descriptionMeta));
        out.imageUrl = absoluteUrl(page.finalUrl, firstNonEmpty(product.imageUrl, ogImage));

        String body = stripHtml(page.html);
        String combined = joinNonEmpty(" ", product.combinedProperties, out.name, out.maker,
                out.description, body.length() > 180000 ? body.substring(0, 180000) : body);

        out.widthMm = inferWidth(firstNonEmpty(product.size, "") + " " + combined);
        out.material = normalizeMaterial(firstNonEmpty(product.material, combined));
        out.color = cleanText(firstNonEmpty(product.color, inferColor(combined)));
        out.buckle = inferBuckle(combined);

        if (out.name.isEmpty()) out.name = "구매 링크 스트랩";
        if (out.material.isEmpty()) out.material = "Extra";

        List<String> found = new ArrayList<>();
        if (!out.maker.isEmpty()) found.add("제작사");
        if (!out.name.isEmpty()) found.add("상품명");
        if (out.widthMm > 0) found.add("러그 폭");
        if (!out.material.isEmpty()) found.add("재질");
        if (!out.color.isEmpty()) found.add("색상");
        if (!out.imageUrl.isEmpty()) found.add("대표사진");
        out.message = found.isEmpty()
                ? "페이지는 열었지만 자동으로 확정할 정보를 찾지 못했습니다."
                : "확인한 정보: " + String.join(" · ", found);
        progress(listener, out.message);
        return out;
    }

    private static Page fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36 WatchCollection/1.20");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        con.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        con.setRequestProperty("Cache-Control", "no-cache");
        int code = con.getResponseCode();
        if (code < 200 || code >= 400) {
            con.disconnect();
            throw new IllegalStateException("상품 페이지 응답 오류: HTTP " + code);
        }

        Charset charset = charsetFromContentType(con.getContentType());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String finalUrl;
        try (InputStream in = con.getInputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) > 0) {
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

    private static Charset charsetFromContentType(String contentType) {
        if (contentType != null) {
            Matcher m = Pattern.compile("(?i)charset\\s*=\\s*([a-zA-Z0-9._-]+)").matcher(contentType);
            if (m.find()) {
                try { return Charset.forName(m.group(1)); } catch (Exception ignored) {}
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static ProductData parseStructuredProduct(String html) {
        ProductData data = new ProductData();
        Matcher scripts = JSON_LD.matcher(html);
        while (scripts.find()) {
            String json = decodeEntities(scripts.group(1)).trim();
            if (json.isEmpty()) continue;
            try {
                Object root = new JSONTokener(json).nextValue();
                JSONObject product = findProduct(root);
                if (product == null) continue;
                data.found = true;
                data.name = cleanText(product.optString("name"));
                data.description = cleanText(product.optString("description"));
                data.brand = brandValue(product.opt("brand"));
                data.imageUrl = imageValue(product.opt("image"));
                data.color = cleanText(product.optString("color"));
                data.material = cleanText(firstNonEmpty(
                        product.optString("material"), product.optString("category")));
                data.size = cleanText(firstNonEmpty(
                        product.optString("size"), product.optString("width")));
                data.combinedProperties = additionalProperties(product.opt("additionalProperty"));
                return data;
            } catch (Exception ignored) {}
        }
        return data;
    }

    private static JSONObject findProduct(Object node) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            if (containsType(object.opt("@type"), "Product")) return object;
            JSONArray names = object.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    Object child = object.opt(names.optString(i));
                    JSONObject found = findProduct(child);
                    if (found != null) return found;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findProduct(array.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean containsType(Object value, String expected) {
        if (value instanceof String) return expected.equalsIgnoreCase((String) value);
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                if (expected.equalsIgnoreCase(a.optString(i))) return true;
            }
        }
        return false;
    }

    private static String brandValue(Object value) {
        if (value instanceof String) return cleanText((String) value);
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            return cleanText(firstNonEmpty(o.optString("name"), o.optString("brand")));
        }
        return "";
    }

    private static String imageValue(Object value) {
        if (value instanceof String) return cleanText((String) value);
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                String s = imageValue(a.opt(i));
                if (!s.isEmpty()) return s;
            }
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            return cleanText(firstNonEmpty(o.optString("url"), o.optString("contentUrl")));
        }
        return "";
    }

    private static String additionalProperties(Object value) {
        if (!(value instanceof JSONArray)) return "";
        JSONArray a = (JSONArray) value;
        List<String> items = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object entry = a.opt(i);
            if (!(entry instanceof JSONObject)) continue;
            JSONObject o = (JSONObject) entry;
            String name = cleanText(o.optString("name"));
            String val = cleanText(firstNonEmpty(o.optString("value"), o.optString("description")));
            if (!name.isEmpty() || !val.isEmpty()) items.add(name + " " + val);
        }
        return String.join(" · ", items);
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

    private static String title(String html) {
        Matcher m = TITLE_TAG.matcher(html);
        return m.find() ? cleanText(m.group(1)) : "";
    }

    private static String stripHtml(String html) {
        String text = html
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<noscript\\b[^>]*>.*?</noscript>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        return cleanText(text);
    }

    private static int inferWidth(String text) {
        Matcher labeled = WIDTH_LABELED.matcher(text);
        if (labeled.find()) return Integer.parseInt(labeled.group(1));
        Matcher generic = WIDTH_GENERIC.matcher(text);
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
                x.contains("alligator") || x.contains("crocodile") || x.contains("가죽")) return "Leather";
        if (x.contains("rubber") || x.contains("silicone") || x.contains("fkm") ||
                x.contains("러버") || x.contains("실리콘")) return "Rubber";
        if (x.contains("milanese") || x.contains("mesh") || x.contains("메쉬")) return "Mesh";
        if (x.contains("nato") || x.contains("nylon") || x.contains("canvas") ||
                x.contains("fabric") || x.contains("나토") || x.contains("패브릭")) return "Nato";
        if (x.contains("bracelet") || x.contains("stainless steel") || x.contains("titanium") ||
                x.contains("metal band") || x.contains("브레이슬릿") || x.contains("스틸")) return "Bracelet";
        return "Extra";
    }

    private static String inferColor(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String[][] colors = {
                {"navy", "Navy"}, {"dark blue", "Dark Blue"}, {"blue", "Blue"},
                {"black", "Black"}, {"brown", "Brown"}, {"tan", "Tan"},
                {"cognac", "Cognac"}, {"burgundy", "Burgundy"}, {"red", "Red"},
                {"green", "Green"}, {"olive", "Olive"}, {"gray", "Gray"},
                {"grey", "Gray"}, {"white", "White"}, {"beige", "Beige"},
                {"orange", "Orange"}, {"yellow", "Yellow"}, {"purple", "Purple"},
                {"네이비", "Navy"}, {"파랑", "Blue"}, {"블루", "Blue"},
                {"검정", "Black"}, {"블랙", "Black"}, {"갈색", "Brown"},
                {"브라운", "Brown"}, {"녹색", "Green"}, {"그린", "Green"},
                {"회색", "Gray"}, {"그레이", "Gray"}, {"흰색", "White"}
        };
        for (String[] c : colors) if (lower.contains(c[0])) return c[1];
        return "";
    }

    private static String inferBuckle(String text) {
        String x = text.toLowerCase(Locale.ROOT);
        if (x.contains("deployant") || x.contains("deployment clasp") || x.contains("디버클")) return "Deployant";
        if (x.contains("butterfly clasp") || x.contains("butterfly buckle")) return "Butterfly clasp";
        if (x.contains("folding clasp") || x.contains("fold-over clasp")) return "Folding clasp";
        if (x.contains("tang buckle") || x.contains("pin buckle") || x.contains("버클")) return "Pin buckle";
        return "";
    }

    private static String cleanProductName(String raw, String maker) {
        String name = cleanText(raw);
        if (name.isEmpty()) return "";
        name = name.replaceAll("(?i)\\s*[|·]\\s*(official|official store|shop).*$", "");
        if (!maker.isEmpty()) {
            name = name.replaceAll("(?i)\\s*[|–—-]\\s*" + Pattern.quote(maker) + "\\s*$", "");
        }
        return name.trim();
    }

    private static String trimDescription(String value) {
        String text = cleanText(value);
        return text.length() <= 360 ? text : text.substring(0, 357).trim() + "…";
    }

    private static String inferMakerFromTitle(String title) {
        String value = cleanText(title);
        if (value.isEmpty()) return "";
        String[] parts = value.split("\\s*[|–—]\\s*|\\s+-\\s+");
        if (parts.length < 2) return "";
        String candidate = parts[parts.length - 1].trim();
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (candidate.length() < 2 || candidate.length() > 40) return "";
        if (lower.contains("official") || lower.equals("shop") || lower.equals("store") ||
                lower.contains("watch strap") || lower.contains("watch band")) return "";
        return candidate;
    }

    private static String inferKnownMaker(String url) {
        String host;
        try { host = new URI(url).getHost(); } catch (Exception e) { return ""; }
        if (host == null) return "";
        host = host.toLowerCase(Locale.ROOT);
        if (host.contains("hirsch")) return "Hirsch";
        if (host.contains("delugs")) return "Delugs";
        if (host.contains("accurateform")) return "Accurate Form";
        if (host.contains("watchgecko")) return "WatchGecko";
        if (host.contains("bartonwatchbands")) return "Barton";
        if (host.contains("strapcode")) return "Strapcode";
        if (host.contains("unclestraps")) return "Uncle Straps";
        if (host.contains("crownandbuckle")) return "Crown & Buckle";
        if (host.contains("nomadwatchworks")) return "Nomad Watch Works";
        return "";
    }

    private static String absoluteUrl(String base, String candidate) {
        String value = cleanText(candidate).replace("\\/", "/");
        if (value.isEmpty() || value.startsWith("data:")) return "";
        try {
            URI resolved = new URI(base).resolve(value);
            String scheme = resolved.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return "";
            return resolved.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("구매 링크를 입력하세요.");
        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null) throw new IllegalArgumentException("올바른 구매 링크가 아닙니다.");
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("올바른 구매 링크가 아닙니다.");
        }
    }

    private static String decodeEntities(String value) {
        if (value == null) return "";
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("\\/", "/");
    }

    private static String cleanText(String value) {
        return decodeEntities(value)
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
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

    private static class Page {
        final String finalUrl;
        final String html;
        Page(String finalUrl, String html) { this.finalUrl = finalUrl; this.html = html; }
    }

    private static class ProductData {
        boolean found;
        String name = "";
        String brand = "";
        String description = "";
        String imageUrl = "";
        String color = "";
        String material = "";
        String size = "";
        String combinedProperties = "";
    }
}
