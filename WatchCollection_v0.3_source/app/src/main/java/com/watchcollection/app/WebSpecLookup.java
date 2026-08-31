package com.watchcollection.app;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watch-specific web lookup.
 *
 * v0.3 검색 원칙
 * 1) 제조사 공식 도메인을 최우선으로 검색
 * 2) 레퍼런스 번호를 모델명보다 강하게 가중
 * 3) 검색 결과를 시계 관련성 점수로 정렬하고 기준 미달 결과는 폐기
 * 4) 실제 페이지 본문을 다시 검증해 모델/레퍼런스가 맞지 않으면 스펙 추출에서 제외
 * 5) 스펙은 공식 페이지 -> 신뢰 가능한 시계 전문 사이트 순서로 채움
 * 6) 대표 이미지는 공식 제조사 페이지에서만 가져옴
 */
public class WebSpecLookup {
    public static class Result {
        public String brand="", referenceNo="", movement="", caliber="", diameterMm="", lugToLugMm="", thicknessMm="", lugWidthMm="";
        public String powerReserveHours="", waterResistance="", crystal="", caseMaterial="", sourceUrls="", representativeImageUrl="", imageSourceUrl="", message="";
    }

    private static class SearchItem {
        String title="", link="", description="";
        int score;
    }

    private static class ProductMeta {
        String brand="", sku="", model="", image="";
    }

    private static class PageData {
        String url="", text="";
        boolean official;
        int score;
        ProductMeta product = new ProductMeta();
    }

    private static final Map<String, String[]> OFFICIAL = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Set<String> TRUSTED_WATCH_DOMAINS = new LinkedHashSet<>();
    private static final Set<String> BLOCKED_DOMAINS = new LinkedHashSet<>();

    static {
        official("Grand Seiko", "grand-seiko.com");
        official("TAG Heuer", "tagheuer.com");
        official("Hamilton", "hamiltonwatch.com");
        official("Mido", "midowatches.com");
        official("Tissot", "tissotwatches.com");
        official("Seiko", "seikowatches.com");
        official("Citizen", "citizenwatch-global.com", "citizenwatch.com");
        official("Casio", "casio.com");
        official("Orient", "orient-watch.com");
        official("Laco", "laco.de");
        official("Romanson", "jestina.co.kr", "romanson.com");
        official("Tudor", "tudorwatch.com");
        official("Longines", "longines.com");
        official("Rolex", "rolex.com");
        official("Omega", "omegawatches.com");
        official("Oris", "oris.ch");
        official("Rado", "rado.com");
        official("Certina", "certina.com");
        official("Sinn", "sinn.de");
        official("Nomos", "nomos-glashuette.com");
        official("Baltic", "baltic-watches.com");
        official("Bulova", "bulova.com");
        official("Timex", "timex.com");
        official("Swatch", "swatch.com");
        official("Cartier", "cartier.com");
        official("Breitling", "breitling.com");
        official("IWC", "iwc.com");
        official("Panerai", "panerai.com");
        official("Zenith", "zenith-watches.com");
        official("Blancpain", "blancpain.com");
        official("Breguet", "breguet.com");
        official("Jaeger-LeCoultre", "jaeger-lecoultre.com");
        official("Vacheron Constantin", "vacheron-constantin.com");
        official("Audemars Piguet", "audemarspiguet.com");
        official("Patek Philippe", "patek.com");
        official("Christopher Ward", "christopherward.com");
        official("Venezianico", "venezianico.com");
        official("Lorier", "lorierwatches.com");
        official("Farer", "farer.com");
        official("Doxa", "doxawatches.com");
        official("Squale", "squale.ch");
        official("G-Shock", "gshock.com", "casio.com");

        alias("grandseiko", "Grand Seiko"); alias("grand seiko", "Grand Seiko"); alias("gs", "Grand Seiko");
        alias("tag heuer", "TAG Heuer"); alias("tagheuer", "TAG Heuer");
        alias("jlc", "Jaeger-LeCoultre"); alias("jaeger lecoultre", "Jaeger-LeCoultre"); alias("jaeger-lecoultre", "Jaeger-LeCoultre");
        alias("ap", "Audemars Piguet"); alias("audemars piguet", "Audemars Piguet");
        alias("vacheron", "Vacheron Constantin"); alias("patek", "Patek Philippe");
        alias("gshock", "G-Shock"); alias("g-shock", "G-Shock");

        String[] trusted = {
                "watchbase.com", "calibercorner.com", "chrono24.com", "watchcharts.com",
                "hodinkee.com", "fratellowatches.com", "monochrome-watches.com",
                "ablogtowatch.com", "timeandtidewatches.com", "watchuseek.com",
                "wornandwound.com", "teddybaldassarre.com"
        };
        Collections.addAll(TRUSTED_WATCH_DOMAINS, trusted);

        String[] blocked = {
                "facebook.com", "instagram.com", "pinterest.com", "youtube.com", "youtu.be",
                "reddit.com", "quora.com", "amazon.com", "amazon.co.jp", "ebay.com",
                "aliexpress.com", "stackoverflow.com", "github.com", "microsoft.com",
                "wikipedia.org", "wiktionary.org", "imdb.com", "spotify.com", "tiktok.com"
        };
        Collections.addAll(BLOCKED_DOMAINS, blocked);
    }

    private static void official(String brand, String... domains) {
        OFFICIAL.put(brand, domains);
        alias(brand.toLowerCase(Locale.ROOT), brand);
    }

    private static void alias(String alias, String brand) {
        ALIASES.put(alias.toLowerCase(Locale.ROOT), brand);
    }

    public static Result lookup(String modelName) throws Exception {
        return lookup(modelName, "", "");
    }

    public static Result lookup(String modelName, String brandHint, String referenceHint) throws Exception {
        Result out = new Result();
        String query = normalize(modelName == null ? "" : modelName).trim();
        if (query.isEmpty()) {
            out.message = "모델명을 입력해 주세요.";
            return out;
        }

        String hintedBrand = canonicalBrand(brandHint == null ? "" : brandHint);
        out.brand = hintedBrand.isEmpty() ? inferBrand(query) : hintedBrand;
        List<String> refs = referenceCandidates(query);
        String cleanRefHint = referenceHint == null ? "" : referenceHint.trim().toUpperCase(Locale.ROOT);
        if (looksLikeReference(cleanRefHint)) {
            refs.remove(cleanRefHint);
            refs.add(0, cleanRefHint);
        }
        String strongRef = refs.isEmpty() ? "" : refs.get(0);
        if (!strongRef.isEmpty()) out.referenceNo = strongRef;

        List<SearchItem> raw = new ArrayList<>();
        String[] officialDomains = OFFICIAL.get(out.brand);

        // 1차: 공식 사이트에 exact query / reference를 강제해 검색
        if (officialDomains != null) {
            for (String domain : officialDomains) {
                addSearch(raw, "site:" + domain + " \"" + query + "\"");
                if (!strongRef.isEmpty()) addSearch(raw, "site:" + domain + " \"" + strongRef + "\" watch");
            }
        }

        // 2차: 모델 전체 문자열 + 시계 문맥. broad query라도 이후 점수/본문 검증으로 강하게 필터링한다.
        addSearch(raw, "\"" + query + "\" watch specifications");
        if (!strongRef.isEmpty()) addSearch(raw, "\"" + strongRef + "\" " + (out.brand.isEmpty() ? "watch" : out.brand + " watch"));
        if (!out.brand.isEmpty()) addSearch(raw, "\"" + query + "\" " + out.brand + " official watch");

        List<SearchItem> ranked = rankAndFilter(raw, query, out.brand, refs);
        if (ranked.isEmpty()) {
            out.message = "시계와 직접 관련된 검색 결과를 찾지 못했습니다. 모델명에 브랜드 또는 레퍼런스 번호를 함께 입력해 보세요.";
            return out;
        }

        List<PageData> pages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int attempts = 0;
        for (SearchItem item : ranked) {
            if (attempts >= 8 || pages.size() >= 5) break;
            if (!isHttp(item.link) || !seen.add(item.link)) continue;
            attempts++;
            try {
                String html = fetchHtml(item.link, 420000);
                String text = normalize(cleanHtml(html));
                boolean officialPage = isOfficialUrl(item.link, out.brand);
                if (!pageMatchesQuery(text, item.title, query, out.brand, refs, officialPage)) continue;

                PageData p = new PageData();
                p.url = item.link;
                p.text = text;
                p.official = officialPage;
                p.score = item.score;
                p.product = findProductMeta(html);
                pages.add(p);
            } catch (Exception ignored) {
                // 페이지 접근이 막힌 경우에도 검색 스니펫이 공식 사이트 + 강한 레퍼런스 일치면 출처 후보로만 유지
                if (isOfficialUrl(item.link, out.brand) && snippetHasStrongReference(item, refs)) {
                    PageData p = new PageData();
                    p.url = item.link;
                    p.text = normalize(item.title + " " + item.description);
                    p.official = true;
                    p.score = item.score - 10;
                    pages.add(p);
                }
            }
        }

        if (pages.isEmpty()) {
            out.message = "검색 결과는 있었지만 모델/레퍼런스 검증을 통과한 시계 페이지가 없었습니다. 관련 없는 결과는 저장하지 않았습니다.";
            return out;
        }

        pages.sort((a, b) -> {
            if (a.official != b.official) return a.official ? -1 : 1;
            return Integer.compare(b.score, a.score);
        });

        // 공식 Product JSON-LD에서 브랜드/레퍼런스/이미지를 우선 사용
        for (PageData p : pages) {
            if (out.brand.isEmpty() && !p.product.brand.isEmpty()) out.brand = canonicalBrand(p.product.brand);
            if (out.referenceNo.isEmpty()) {
                String candidate = firstNonEmpty(p.product.sku, p.product.model);
                if (looksLikeReference(candidate)) out.referenceNo = candidate;
            }
            if (p.official && out.representativeImageUrl.isEmpty()) {
                String image = p.product.image;
                if (image.isEmpty()) {
                    try { image = findRepresentativeImage(fetchHtml(p.url, 260000), p.url); } catch (Exception ignored) {}
                } else {
                    image = resolve(p.url, image);
                }
                if (!image.isEmpty()) {
                    out.representativeImageUrl = image;
                    out.imageSourceUrl = p.url;
                }
            }
        }

        // 각 필드를 높은 신뢰도의 페이지부터 개별 추출한다. 서로 다른 모델의 내용이 섞이지 않는다.
        out.caliber = firstRegex(pages, "(?i)(?:caliber|calibre|cal\\.?|칼리버)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9._\\-/]{1,18})");
        out.diameterMm = firstMeasurement(pages, new String[]{"case diameter","diameter","case size","직경","케이스 지름"}, 15, 70);
        out.lugToLugMm = firstMeasurement(pages, new String[]{"lug-to-lug","lug to lug","러그 투 러그"}, 20, 90);
        out.thicknessMm = firstMeasurement(pages, new String[]{"case thickness","thickness","두께"}, 3, 35);
        out.lugWidthMm = firstMeasurement(pages, new String[]{"lug width","lug size","strap width","band width","러그 폭"}, 8, 30);
        out.powerReserveHours = firstHours(pages);
        out.waterResistance = firstWaterResistance(pages);
        out.movement = firstMovement(pages);
        out.crystal = firstCrystal(pages);
        out.caseMaterial = firstCaseMaterial(pages);

        Set<String> sources = new LinkedHashSet<>();
        int officialCount = 0;
        for (PageData p : pages) {
            if (sources.size() >= 4) break;
            if (p.official) officialCount++;
            sources.add(p.url);
        }
        out.sourceUrls = String.join("\n", sources);

        String quality = officialCount > 0 ? "공식 페이지 " + officialCount + "개 포함" : "시계 전문 출처만 사용";
        String imageMsg = out.representativeImageUrl.isEmpty() ? " 공식 대표사진은 찾지 못했습니다." : " 공식 대표사진도 확인했습니다.";
        out.message = "관련성 검증을 통과한 " + pages.size() + "개 페이지에서 스펙을 추출했습니다 (" + quality + ")." + imageMsg + " 저장 전 값을 확인해 주세요.";
        return out;
    }

    private static void addSearch(List<SearchItem> out, String query) {
        try { out.addAll(searchBingRss(query)); } catch (Exception ignored) {}
    }

    private static List<SearchItem> rankAndFilter(List<SearchItem> raw, String query, String brand, List<String> refs) {
        Map<String, SearchItem> bestByUrl = new LinkedHashMap<>();
        for (SearchItem item : raw) {
            if (!isHttp(item.link) || isBlocked(item.link)) continue;
            item.score = relevanceScore(item, query, brand, refs);
            boolean official = isOfficialUrl(item.link, brand);
            boolean trusted = isTrustedWatchUrl(item.link);
            boolean refHit = snippetHasStrongReference(item, refs);

            // 공식은 70+, 비공식은 reference 일치나 trusted domain을 요구한다.
            boolean accept = (official && item.score >= 70)
                    || (trusted && item.score >= 55)
                    || (refHit && item.score >= 75);
            if (!accept) continue;

            SearchItem old = bestByUrl.get(item.link);
            if (old == null || item.score > old.score) bestByUrl.put(item.link, item);
        }
        List<SearchItem> list = new ArrayList<>(bestByUrl.values());
        list.sort((a,b) -> Integer.compare(b.score, a.score));
        return list;
    }

    private static int relevanceScore(SearchItem item, String query, String brand, List<String> refs) {
        String hay = normalizeForMatch(item.title + " " + item.description + " " + item.link);
        int score = 0;
        if (isOfficialUrl(item.link, brand)) score += 120;
        else if (isTrustedWatchUrl(item.link)) score += 35;

        boolean refHit = false;
        for (String ref : refs) {
            if (compact(hay).contains(compact(ref))) { score += 85; refHit = true; break; }
        }

        List<String> tokens = meaningfulTokens(query, brand);
        int hits = 0;
        for (String token : tokens) if (hay.contains(token)) hits++;
        score += Math.min(50, hits * 10);
        if (!tokens.isEmpty() && hits == tokens.size()) score += 20;

        if (containsAny(hay, "watch", "wristwatch", "timepiece", "automatic", "quartz", "caliber", "calibre")) score += 18;
        if (containsAny(hay, "diameter", "thickness", "power reserve", "water resistance", "lug width", "case size")) score += 8;
        if (!refHit && !containsAny(hay, "watch", "wristwatch", "timepiece") && !isOfficialUrl(item.link, brand)) score -= 45;
        return score;
    }

    private static boolean pageMatchesQuery(String pageText, String title, String query, String brand, List<String> refs, boolean official) {
        String hay = normalizeForMatch(title + " " + pageText);
        String compactHay = compact(hay);
        for (String ref : refs) if (compactHay.contains(compact(ref))) return true;

        List<String> tokens = meaningfulTokens(query, brand);
        int hits = 0;
        for (String token : tokens) if (hay.contains(token)) hits++;
        boolean watchContext = containsAny(hay, "watch", "wristwatch", "timepiece", "movement", "caliber", "calibre", "water resistance");

        if (tokens.isEmpty()) return official && watchContext;
        double ratio = hits / (double) tokens.size();
        if (official) return watchContext && (hits >= Math.min(2, tokens.size()) || ratio >= 0.65);
        return watchContext && hits >= 2 && ratio >= 0.55;
    }

    private static boolean snippetHasStrongReference(SearchItem item, List<String> refs) {
        if (refs.isEmpty()) return false;
        String hay = compact(item.title + " " + item.description + " " + item.link);
        for (String ref : refs) if (hay.contains(compact(ref))) return true;
        return false;
    }

    private static List<SearchItem> searchBingRss(String query) throws Exception {
        String url = "https://www.bing.com/search?format=rss&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        HttpURLConnection con = open(url);
        List<SearchItem> list = new ArrayList<>();
        try (InputStream in = con.getInputStream()) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, "UTF-8");
            int event = p.getEventType();
            SearchItem cur = null;
            String tag = "";
            while (event != XmlPullParser.END_DOCUMENT && list.size() < 12) {
                if (event == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("item".equalsIgnoreCase(tag)) cur = new SearchItem();
                } else if (event == XmlPullParser.TEXT && cur != null) {
                    String v = p.getText();
                    if ("title".equalsIgnoreCase(tag)) cur.title += v;
                    else if ("link".equalsIgnoreCase(tag)) cur.link += v;
                    else if ("description".equalsIgnoreCase(tag)) cur.description += v;
                } else if (event == XmlPullParser.END_TAG && "item".equalsIgnoreCase(p.getName()) && cur != null) {
                    cur.title = cleanHtml(cur.title);
                    cur.description = cleanHtml(cur.description);
                    cur.link = cur.link.trim();
                    list.add(cur);
                    cur = null;
                }
                event = p.next();
            }
        } finally {
            con.disconnect();
        }
        return list;
    }

    private static String fetchHtml(String url, int maxChars) throws Exception {
        HttpURLConnection con = open(url);
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && b.length() < maxChars) b.append(line).append(' ');
        } finally {
            con.disconnect();
        }
        return b.toString();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setConnectTimeout(9000);
        con.setReadTimeout(12000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 WatchCollection/0.3");
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko-KR;q=0.8,ko;q=0.7");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return con;
    }

    private static ProductMeta findProductMeta(String html) {
        ProductMeta out = new ProductMeta();
        Matcher m = Pattern.compile("(?is)<script[^>]+type=[\\\"']application/ld\\+json[\\\"'][^>]*>(.*?)</script>").matcher(html);
        while (m.find()) {
            String json = decodeEntities(m.group(1)).trim();
            try {
                Object root = json.startsWith("[") ? new JSONArray(json) : new JSONObject(json);
                JSONObject product = findProductObject(root);
                if (product == null) continue;
                out.sku = firstNonEmpty(product.optString("sku", ""), product.optString("mpn", ""));
                out.model = product.optString("model", "");
                Object brand = product.opt("brand");
                if (brand instanceof JSONObject) out.brand = ((JSONObject) brand).optString("name", "");
                else if (brand instanceof String) out.brand = (String) brand;
                out.image = jsonImage(product.opt("image"));
                if (!out.sku.isEmpty() || !out.model.isEmpty() || !out.image.isEmpty()) return out;
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static JSONObject findProductObject(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            Object type = jo.opt("@type");
            if (isProductType(type)) return jo;
            JSONArray names = jo.names();
            if (names != null) {
                for (int i=0; i<names.length(); i++) {
                    Object child = jo.opt(names.optString(i));
                    JSONObject found = findProductObject(child);
                    if (found != null) return found;
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            for (int i=0; i<arr.length(); i++) {
                JSONObject found = findProductObject(arr.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isProductType(Object type) {
        if (type instanceof String) return "product".equalsIgnoreCase((String) type);
        if (type instanceof JSONArray) {
            JSONArray arr = (JSONArray) type;
            for (int i=0; i<arr.length(); i++) if ("product".equalsIgnoreCase(arr.optString(i))) return true;
        }
        return false;
    }

    private static String jsonImage(Object image) {
        if (image instanceof String) return (String) image;
        if (image instanceof JSONArray && ((JSONArray) image).length() > 0) return jsonImage(((JSONArray) image).opt(0));
        if (image instanceof JSONObject) return firstNonEmpty(((JSONObject) image).optString("url", ""), ((JSONObject) image).optString("contentUrl", ""));
        return "";
    }

    private static String findRepresentativeImage(String html, String pageUrl) {
        String[] patterns = {
                "(?is)<meta[^>]+(?:property|name)=[\\\"']og:image[\\\"'][^>]+content=[\\\"']([^\\\"']+)",
                "(?is)<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+(?:property|name)=[\\\"']og:image[\\\"']",
                "(?is)<meta[^>]+(?:property|name)=[\\\"']twitter:image(?::src)?[\\\"'][^>]+content=[\\\"']([^\\\"']+)"
        };
        for (String regex : patterns) {
            Matcher m = Pattern.compile(regex).matcher(html);
            if (m.find()) return resolve(pageUrl, decodeEntities(m.group(1)));
        }
        return "";
    }

    private static String firstRegex(List<PageData> pages, String regex) {
        for (PageData p : pages) {
            Matcher m = Pattern.compile(regex).matcher(p.text);
            if (m.find()) return cleanValue(m.group(1));
        }
        return "";
    }

    private static String firstMeasurement(List<PageData> pages, String[] keys, double min, double max) {
        for (PageData p : pages) {
            for (String key : keys) {
                Matcher m = Pattern.compile("(?i)" + Pattern.quote(key) + "[^0-9]{0,35}([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*mm").matcher(p.text);
                while (m.find()) {
                    try {
                        double value = Double.parseDouble(m.group(1));
                        if (value >= min && value <= max) return trimNumber(m.group(1));
                    } catch (Exception ignored) {}
                }
            }
        }
        return "";
    }

    private static String firstHours(List<PageData> pages) {
        Pattern pt = Pattern.compile("(?i)(?:power reserve|파워 리저브|파워리저브)[^0-9]{0,40}([0-9]{1,3})\\s*(?:hours?|hrs?|h|시간)");
        for (PageData p : pages) {
            Matcher m = pt.matcher(p.text);
            if (m.find()) {
                int h = Integer.parseInt(m.group(1));
                if (h >= 10 && h <= 300) return String.valueOf(h);
            }
        }
        return "";
    }

    private static String firstWaterResistance(List<PageData> pages) {
        Pattern pt = Pattern.compile("(?i)(?:water resistance|water resistant|방수)[^0-9]{0,35}([0-9]{1,4})\\s*(m|metres|meters|bar|atm)");
        for (PageData p : pages) {
            Matcher m = pt.matcher(p.text);
            if (m.find()) return m.group(1) + " " + m.group(2).toLowerCase(Locale.ROOT).replace("metres","m").replace("meters","m");
        }
        return "";
    }

    private static String firstMovement(List<PageData> pages) {
        for (PageData p : pages) {
            String l = p.text.toLowerCase(Locale.ROOT);
            if (containsAny(l, "automatic", "self-winding", "self winding", "오토매틱")) return "Automatic";
            if (containsAny(l, "manual winding", "hand-wound", "hand wound", "수동")) return "Manual";
            if (containsAny(l, "solar quartz", "eco-drive", "solar-powered", "solar powered")) return "Solar Quartz";
            if (containsAny(l, "quartz", "쿼츠")) return "Quartz";
        }
        return "";
    }

    private static String firstCrystal(List<PageData> pages) {
        for (PageData p : pages) {
            String l = p.text.toLowerCase(Locale.ROOT);
            if (containsAny(l, "sapphire crystal", "sapphire glass", "사파이어")) return "Sapphire";
            if (containsAny(l, "hesalite", "acrylic crystal", "acrylic glass")) return "Acrylic";
            if (containsAny(l, "mineral crystal", "mineral glass", "미네랄")) return "Mineral";
            if (l.contains("hardlex")) return "Hardlex";
        }
        return "";
    }

    private static String firstCaseMaterial(List<PageData> pages) {
        for (PageData p : pages) {
            String l = p.text.toLowerCase(Locale.ROOT);
            if (containsAny(l, "titanium case", "case material titanium", "티타늄")) return "Titanium";
            if (containsAny(l, "stainless steel case", "case material stainless steel", "스테인리스 스틸")) return "Stainless Steel";
            if (containsAny(l, "bronze case", "case material bronze", "브론즈")) return "Bronze";
            if (containsAny(l, "ceramic case", "case material ceramic", "세라믹")) return "Ceramic";
            if (containsAny(l, "carbon case", "carbon composite")) return "Carbon";
        }
        return "";
    }

    private static String inferBrand(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        // 긴 alias부터 확인해 'GS' 같은 짧은 표현이 다른 문자열에 섞이는 것을 방지
        List<Map.Entry<String,String>> aliases = new ArrayList<>(ALIASES.entrySet());
        aliases.sort((a,b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String,String> e : aliases) {
            String a = e.getKey();
            if (a.length() <= 2) {
                if (Pattern.compile("(?i)(?:^|[^a-z0-9])" + Pattern.quote(a) + "(?:$|[^a-z0-9])").matcher(lower).find()) return e.getValue();
            } else if (lower.contains(a)) return e.getValue();
        }
        return inferBrandFromReference(text);
    }

    private static String inferBrandFromReference(String text) {
        String u = text.toUpperCase(Locale.ROOT);
        if (u.matches(".*\\bBM\\d{4}-\\d{2}[A-Z]\\b.*") || u.matches(".*\\bNB\\d{4}-\\d{2}[A-Z]\\b.*")) return "Citizen";
        if (u.matches(".*\\b(SPB|SLA|SJE|SRP|SRPE|SRPD|SSK|SSC|SNE)\\d{3}[A-Z0-9]*\\b.*")) return "Seiko";
        if (u.matches(".*\\b(SBGX|SBGA|SBGW|SLGA|SLGH|SLGW|SBGH)\\d{3}\\b.*")) return "Grand Seiko";
        if (u.matches(".*\\bH\\d{8}\\b.*")) return "Hamilton";
        if (u.matches(".*\\bT\\d{3}\\.\\d{3}\\.\\d{2}\\.\\d{3}\\.\\d{2}\\b.*")) return "Tissot";
        if (u.matches(".*\\bM0\\d{2}\\.\\d{3}\\.\\d{2}\\.\\d{3}\\.\\d{2}\\b.*")) return "Mido";
        if (u.matches(".*\\bL\\d\\.\\d{3}\\.\\d\\.\\d{2}\\.\\d\\b.*")) return "Longines";
        if (u.matches(".*\\bM\\d{5}[A-Z]{0,2}-\\d{3,4}\\b.*")) return "Tudor";
        return "";
    }

    private static String canonicalBrand(String brand) {
        String inferred = inferBrand(brand);
        return inferred.isEmpty() ? brand.trim() : inferred;
    }

    private static List<String> referenceCandidates(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?i)\\b[A-Z0-9][A-Z0-9._/-]{4,25}\\b").matcher(text.toUpperCase(Locale.ROOT));
        while (m.find()) {
            String token = m.group();
            if (!token.matches(".*\\d.*")) continue;
            if (token.matches("\\d+(?:\\.\\d+)?MM")) continue;
            if (!out.contains(token)) out.add(token);
        }
        out.sort((a,b) -> Integer.compare(referenceStrength(b), referenceStrength(a)));
        return out;
    }

    private static int referenceStrength(String s) {
        int n = s.length();
        if (s.contains("-") || s.contains(".")) n += 5;
        if (s.matches(".*[A-Z].*") && s.matches(".*\\d.*")) n += 5;
        return n;
    }

    private static boolean looksLikeReference(String s) {
        if (s == null) return false;
        String v = s.trim();
        return v.length() >= 4 && v.length() <= 28 && v.matches("(?i)[A-Z0-9][A-Z0-9._/-]*") && v.matches(".*\\d.*");
    }

    private static List<String> meaningfulTokens(String query, String brand) {
        String q = normalizeForMatch(query);
        String brandNorm = normalizeForMatch(brand);
        List<String> out = new ArrayList<>();
        for (String token : q.split("[^a-z0-9]+")) {
            if (token.length() < 2) continue;
            if (token.equals("watch") || token.equals("watches") || token.equals("mm") || token.equals("ref") || token.equals("model")) continue;
            if (!brandNorm.isEmpty() && brandNorm.contains(token) && token.length() < 4) continue;
            if (!out.contains(token)) out.add(token);
        }
        return out;
    }

    private static boolean isOfficialUrl(String url, String brand) {
        String[] domains = OFFICIAL.get(brand);
        if (domains == null) return false;
        for (String domain : domains) if (hostEndsWith(url, domain)) return true;
        return false;
    }

    private static boolean isTrustedWatchUrl(String url) {
        for (String domain : TRUSTED_WATCH_DOMAINS) if (hostEndsWith(url, domain)) return true;
        return false;
    }

    private static boolean isBlocked(String url) {
        for (String domain : BLOCKED_DOMAINS) if (hostEndsWith(url, domain)) return true;
        return false;
    }

    private static boolean hostEndsWith(String url, String domain) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            domain = domain.toLowerCase(Locale.ROOT);
            return host.equals(domain) || host.endsWith("." + domain);
        } catch (Exception e) { return false; }
    }

    private static String resolve(String base, String child) {
        try { return URI.create(base).resolve(child.replace("\\/", "/")).toString(); }
        catch (Exception e) { return child; }
    }

    private static boolean isHttp(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://"));
    }

    private static String cleanHtml(String s) {
        if (s == null) return "";
        return decodeEntities(s
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?</noscript>", " ")
                .replaceAll("(?is)<[^>]+>", " "))
                .replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static String normalize(String s) {
        return s.replace('×','x').replace('–','-').replace('—','-').replace('−','-').replaceAll("\\s+", " ");
    }

    private static String normalizeForMatch(String s) {
        return normalize(s == null ? "" : s).toLowerCase(Locale.ROOT);
    }

    private static String compact(String s) {
        return normalizeForMatch(s).replaceAll("[^a-z0-9]", "");
    }

    private static boolean containsAny(String text, String... values) {
        for (String v : values) if (text.contains(v)) return true;
        return false;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static String cleanValue(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[;,]+$", "");
    }

    private static String trimNumber(String s) {
        if (s == null) return "";
        if (s.endsWith(".0")) return s.substring(0, s.length()-2);
        return s;
    }
}
