package com.watchcollection.app;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
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
import java.util.zip.GZIPInputStream;

/**
 * Watch-specific specification lookup.
 *
 * v0.5 원칙
 * - 사진은 검색하지 않는다. 사용자가 직접 넣는다.
 * - 레퍼런스가 있으면 레퍼런스 exact match를 최우선으로 사용한다.
 * - 공식 제조사 페이지는 가장 높은 신뢰도로 사용한다.
 * - 공식 페이지에 없는 필드는 신뢰 가능한 복수 출처 합의(consensus)로 채운다.
 * - 단일 저신뢰 출처에서만 나온 값은 억지로 채우지 않는다.
 * - 검색 결과 수를 줄이는 대신, 잘못된 모델의 스펙이 섞이는 것을 막는 데 우선순위를 둔다.
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
        String brand="", sku="", model="", name="";
    }

    private static class PageData {
        String url="", title="", text="";
        boolean official;
        int score;
        int tier;
        boolean exactReferenceMatch;
        boolean snippetOnly;
        ProductMeta product = new ProductMeta();
    }

    private static class ValueCandidate {
        String value="";
        String key="";
        PageData page;
        double numeric = Double.NaN;
        int weight;
    }

    private static final Map<String, String[]> OFFICIAL = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Set<String> HIGH_TRUST = new LinkedHashSet<>();
    private static final Set<String> WATCH_MEDIA = new LinkedHashSet<>();
    private static final Set<String> RETAIL_SOURCES = new LinkedHashSet<>();
    private static final Set<String> BLOCKED = new LinkedHashSet<>();

    static {
        official("Grand Seiko", "grand-seiko.com");
        official("TAG Heuer", "tagheuer.com");
        official("Hamilton", "hamiltonwatch.com");
        official("Mido", "midowatches.com");
        official("Tissot", "tissotwatches.com");
        official("Seiko", "seikowatches.com");
        official("Citizen", "citizenwatch-global.com", "citizenwatch.com", "citizen.jp");
        official("Casio", "casio.com");
        official("Orient", "orient-watch.com");
        official("Laco", "laco.de");
        official("Romanson", "romanson.com", "jestina.co.kr");
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

        Collections.addAll(HIGH_TRUST,
                "watchbase.com", "calibercorner.com", "shockbase.org", "watchcharts.com");
        Collections.addAll(WATCH_MEDIA,
                "hodinkee.com", "fratellowatches.com", "monochrome-watches.com",
                "ablogtowatch.com", "timeandtidewatches.com", "wornandwound.com",
                "teddybaldassarre.com", "watchuseek.com", "watchtime.com", "gearpatrol.com",
                "deployant.com", "thewatchpages.com", "revolutionwatch.com");
        Collections.addAll(RETAIL_SOURCES,
                "chrono24.com", "mastersintime.com", "sakurawatches.com", "seriouswatches.com",
                "jomashop.com", "creationwatches.com", "gnomonwatches.com", "exquisitetimepieces.com",
                "watchmaxx.com", "reddeerwatches.com", "japan-select.com");
        Collections.addAll(BLOCKED,
                "facebook.com", "instagram.com", "pinterest.com", "youtube.com", "youtu.be",
                "reddit.com", "quora.com", "amazon.com", "amazon.co.jp", "ebay.com",
                "aliexpress.com", "stackoverflow.com", "github.com", "microsoft.com",
                "wikipedia.org", "wiktionary.org", "imdb.com", "spotify.com", "tiktok.com");
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
        String cleanRefHint = normalizeReference(referenceHint);
        if (looksLikeReference(cleanRefHint)) {
            refs.removeIf(x -> compact(x).equals(compact(cleanRefHint)));
            refs.add(0, cleanRefHint);
        }
        String strongRef = refs.isEmpty() ? "" : refs.get(0);
        if (!strongRef.isEmpty()) {
            out.referenceNo = strongRef;
            for(String aliasRef : referenceAliases(strongRef, out.brand)) {
                boolean exists=false;
                for(String r:refs) if(compact(r).equals(compact(aliasRef))) { exists=true; break; }
                if(!exists) refs.add(aliasRef);
            }
        }

        List<String> queries = buildQueries(query, out.brand, strongRef);
        List<SearchItem> raw = new ArrayList<>();
        for (String q : queries) addSearch(raw, q);

        // 검색엔진이 제조사 제품 페이지를 놓치는 경우를 대비해 공식 sitemap을 직접 탐색합니다.
        if (!strongRef.isEmpty() && !out.brand.isEmpty()) {
            try {
                List<SearchItem> sitemapHits=searchOfficialSitemaps(out.brand, refs);
                raw.addAll(sitemapHits);
                if(sitemapHits.isEmpty()) raw.addAll(searchWaybackOfficial(out.brand, refs));
            } catch(Exception ignored) {}
        }

        // 일본 브랜드는 Yahoo Japan을 한 번 더 사용합니다. 일본 내수/단종 모델 검색에 특히 유용합니다.
        if (isJapaneseBrand(out.brand)) {
            String jq=!strongRef.isEmpty()?"\""+strongRef+"\" 腕時計 仕様":query+" 腕時計 仕様";
            try { raw.addAll(searchYahooJapan(jq)); } catch(Exception ignored) {}
        }

        List<SearchItem> ranked = rankAndFilter(raw, query, out.brand, refs);
        // 후보의 질이 낮을 때만 신뢰 출처를 exact-reference로 추가 탐색합니다.
        if (ranked.size() < 10 && !strongRef.isEmpty()) {
            String[] fallbackDomains = {"watchbase.com", "calibercorner.com", "chrono24.com", "mastersintime.com", "sakurawatches.com", "shockbase.org"};
            for (String domain : fallbackDomains) addSearch(raw, "site:" + domain + " \"" + strongRef + "\"");
            ranked = rankAndFilter(raw, query, out.brand, refs);
        }
        if(ranked.size()<6 && !strongRef.isEmpty()) {
            try { raw.addAll(searchGoogleHtml("\""+strongRef+"\" "+(out.brand.isEmpty()?"watch":out.brand+" watch"))); } catch(Exception ignored) {}
            ranked=rankAndFilter(raw,query,out.brand,refs);
        }
        if (ranked.isEmpty()) {
            out.message = "정확한 모델 후보를 찾지 못했습니다. 가능하면 브랜드와 레퍼런스 번호를 함께 입력해 주세요.";
            return out;
        }

        List<PageData> pages = fetchVerifiedPages(ranked, query, out.brand, refs);
        if (pages.isEmpty()) {
            out.message = "검색 결과는 있었지만 정확한 모델이라고 검증할 수 있는 페이지가 없었습니다. 잘못된 스펙은 채우지 않았습니다.";
            return out;
        }

        // 공식 Product 메타데이터로 브랜드/레퍼런스 보강
        for (PageData p : pages) {
            if (out.brand.isEmpty() && !p.product.brand.isEmpty()) out.brand = canonicalBrand(p.product.brand);
            if (out.referenceNo.isEmpty()) {
                String candidate = firstNonEmpty(p.product.sku, p.product.model);
                if (looksLikeReference(candidate)) out.referenceNo = candidate.toUpperCase(Locale.ROOT);
            }
        }

        // 스펙은 필드별 후보를 수집하고 공식 출처 또는 복수 출처 합의로 결정한다.
        out.caliber = chooseString(collectCaliber(pages));
        List<ValueCandidate> diameterCandidates = collectMeasurement(pages,
                new String[]{"case diameter","diameter","case size","case width","case diameter/width","직경","지름","케이스 지름","케이스 크기","ケース径","ケースサイズ","ケース幅","gehäusedurchmesser","durchmesser"}, 15, 70);
        diameterCandidates.addAll(collectMeasurementForDomain(pages,"midowatches.com",new String[]{"width","폭"},15,70));
        diameterCandidates.addAll(collectMeasurementForDomain(pages,"tissotwatches.com",new String[]{"width"},15,70));
        diameterCandidates.addAll(collectTripleDimension(pages,new String[]{"casio.com","orient-watch.com"},1,15,70));
        out.diameterMm = chooseNumeric(diameterCandidates, 0.35);

        List<ValueCandidate> l2lCandidates = collectMeasurement(pages,
                new String[]{"lug-to-lug","lug to lug","lug to lug length","case length","러그 투 러그","ラグ・トゥ・ラグ","ラグトゥラグ","ケース縦","全長","gehäuselänge"}, 20, 90);
        l2lCandidates.addAll(collectMeasurementForDomain(pages,"midowatches.com",new String[]{"length","길이"},20,90));
        l2lCandidates.addAll(collectMeasurementForDomain(pages,"tissotwatches.com",new String[]{"length"},20,90));
        l2lCandidates.addAll(collectTripleDimension(pages,new String[]{"casio.com","orient-watch.com"},0,20,90));
        out.lugToLugMm = chooseNumeric(l2lCandidates, 0.45);

        List<ValueCandidate> thicknessCandidates=collectMeasurement(pages,
                new String[]{"case thickness","thickness","average thickness","case height","두께","평균 두께","厚さ","ケース厚","höhe","gehäusehöhe"}, 3, 35);
        thicknessCandidates.addAll(collectTripleDimension(pages,new String[]{"casio.com","orient-watch.com"},2,3,35));
        out.thicknessMm = chooseNumeric(thicknessCandidates, 0.30);

        List<ValueCandidate> lugCandidates = collectMeasurement(pages,
                new String[]{"lug width","lugs width","lug size","strap width","band width","width between lugs","distance between lugs","lug distance","러그 폭","러그 넓이","러그 사이의 거리","ラグ幅","かん幅","バンド幅","バンド取付幅","bandanstoß","bandbreite"}, 8, 30);
        lugCandidates.addAll(collectMeasurementForDomain(pages,"midowatches.com",new String[]{"lugs width","lug width"},8,30));
        lugCandidates.addAll(collectMeasurementForDomain(pages,"tissotwatches.com",new String[]{"lugs","lug"},8,30));
        out.lugWidthMm = chooseNumeric(lugCandidates, 0.20);
        out.powerReserveHours = chooseNumeric(collectPowerReserve(pages), 1.0);
        out.waterResistance = chooseWater(collectWaterResistance(pages));
        out.movement = chooseString(collectMovement(pages));
        out.crystal = chooseString(collectCrystal(pages));
        out.caseMaterial = chooseString(collectCaseMaterial(pages));

        Set<String> sources = new LinkedHashSet<>();
        int officialCount = 0;
        int exactCount = 0;
        for (PageData p : pages) {
            if (p.official) officialCount++;
            if (p.exactReferenceMatch) exactCount++;
            if (sources.size() < 6) sources.add(p.url);
        }
        out.sourceUrls = String.join("\n", sources);

        int filled = countFilled(out);
        String basis;
        if (officialCount > 0) basis = "공식 출처 " + officialCount + "개 포함";
        else if (exactCount >= 2) basis = "레퍼런스 일치 출처 " + exactCount + "개 교차검증";
        else basis = "검증된 시계 전문 출처 사용";

        out.message = "정확성 우선 검색 완료 · " + basis + " · 스펙 " + filled + "개 확인. " +
                "공식/교차검증 기준을 통과하지 못한 항목은 빈칸으로 남겼습니다. 사진은 직접 선택해 주세요.";
        return out;
    }

    private static List<String> buildQueries(String query, String brand, String ref) {
        LinkedHashSet<String> q = new LinkedHashSet<>();
        String cleanModel = removeBrandAndReference(query, brand, ref);
        String[] officialDomains = OFFICIAL.get(brand);
        List<String> refQueries = queryReferenceVariants(ref, brand);

        if (!ref.isEmpty()) {
            // 1) 제조사 공식 사이트: 원본 레퍼런스와 구두점 제거형 모두 검색
            if (officialDomains != null) {
                for (String domain : officialDomains) {
                    for (String rv : refQueries) {
                        q.add("site:" + domain + " \"" + rv + "\"");
                    }
                    if (!cleanModel.isEmpty()) q.add("site:" + domain + " " + brand + " " + cleanModel + " " + ref);
                }
            }

            // 2) exact reference 중심 일반 검색
            for (String rv : refQueries) {
                q.add("\"" + rv + "\" " + (brand.isEmpty() ? "watch" : brand + " watch"));
            }
            q.add("\"" + ref + "\" watch specifications");
            q.add("\"" + ref + "\" diameter thickness lug width");
            q.add("\"" + ref + "\" caliber movement power reserve water resistance");
            if (!cleanModel.isEmpty()) q.add((brand.isEmpty() ? "" : brand + " ") + "\"" + cleanModel + "\" \"" + ref + "\"");

            // 3) 일본계 브랜드는 현지 표기까지 탐색
            if (isJapaneseBrand(brand)) {
                q.add("\"" + ref + "\" 腕時計 仕様");
                q.add("\"" + ref + "\" ケース径 厚さ ラグ幅");
            }
        } else {
            if (officialDomains != null) {
                for (String domain : officialDomains) {
                    q.add("site:" + domain + " \"" + query + "\"");
                    if (!cleanModel.isEmpty()) q.add("site:" + domain + " " + brand + " \"" + cleanModel + "\"");
                }
            }
            q.add("\"" + query + "\" watch");
            q.add("\"" + query + "\" watch specifications");
            if (!brand.isEmpty() && !cleanModel.isEmpty()) {
                q.add(brand + " \"" + cleanModel + "\" specs");
                q.add(brand + " \"" + cleanModel + "\" diameter thickness lug width");
            }
        }
        // 네트워크 호출 폭증을 막으면서도 서로 다른 의도의 질의를 충분히 유지합니다.
        List<String> out = new ArrayList<>(q);
        return out.size() > 12 ? new ArrayList<>(out.subList(0,12)) : out;
    }

    private static String removeBrandAndReference(String query, String brand, String ref) {
        String s = query;
        if (!brand.isEmpty()) s = s.replaceAll("(?i)" + Pattern.quote(brand), " ");
        if (!ref.isEmpty()) s = s.replaceAll("(?i)" + Pattern.quote(ref), " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static void addSearch(List<SearchItem> out, String query) {
        int before=out.size();
        try { out.addAll(searchBingRss(query)); } catch (Exception ignored) {}
        // RSS 결과가 빈약하거나 스니펫이 부족할 때 Bing HTML을 보조로 사용합니다.
        if(out.size()-before<5) {
            try { out.addAll(searchBingHtml(query)); } catch (Exception ignored) {}
        }
        try { out.addAll(searchDuckDuckGoLite(query)); } catch (Exception ignored) {}
    }

    private static List<SearchItem> rankAndFilter(List<SearchItem> raw, String query, String brand, List<String> refs) {
        Map<String, SearchItem> best = new LinkedHashMap<>();
        for (SearchItem item : raw) {
            item.link = normalizeResultUrl(item.link);
            if (!isHttp(item.link) || isBlocked(item.link)) continue;
            item.score = relevanceScore(item, query, brand, refs);

            boolean exactRef = snippetHasStrongReference(item, refs);
            boolean official = isOfficialUrl(item.link, brand);
            boolean watchSource = sourceTier(item.link) >= 2;

            // reference가 있으면 unknown domain도 exact match인 경우 후보로는 허용한다.
            // reference가 없으면 공식/시계전문 출처 + 모델 토큰 일치를 요구한다.
            boolean accept;
            if (!refs.isEmpty()) accept = exactRef || official || (watchSource && item.score >= 42);
            else accept = (official && item.score >= 45) || (watchSource && item.score >= 55);
            if (!accept) continue;

            SearchItem old = best.get(item.link);
            if (old == null || item.score > old.score) best.put(item.link, item);
        }
        List<SearchItem> list = new ArrayList<>(best.values());
        list.sort((a,b) -> Integer.compare(b.score, a.score));
        return list;
    }

    private static int relevanceScore(SearchItem item, String query, String brand, List<String> refs) {
        String hay = normalizeForMatch(item.title + " " + item.description + " " + item.link);
        int score = sourceTier(item.link) * 18;
        if (isOfficialUrl(item.link, brand)) score += 80;

        boolean refHit = false;
        for (String ref : refs) {
            if (compact(hay).contains(compact(ref))) { score += 120; refHit = true; break; }
        }

        if (!brand.isEmpty() && compact(hay).contains(compact(brand))) score += 22;
        List<String> tokens = meaningfulModelTokens(query, brand, refs);
        int hits = 0;
        for (String token : tokens) if (hay.contains(token)) hits++;
        score += Math.min(60, hits * 12);
        if (!tokens.isEmpty() && hits == tokens.size()) score += 20;

        if (containsAny(hay, "watch", "wristwatch", "timepiece", "automatic", "quartz", "caliber", "calibre")) score += 15;
        if (containsAny(hay, "diameter", "thickness", "power reserve", "water resistance", "lug width", "case size")) score += 10;
        if (!refHit && !containsAny(hay, "watch", "wristwatch", "timepiece") && sourceTier(item.link) == 0) score -= 50;
        return score;
    }

    private static List<PageData> fetchVerifiedPages(List<SearchItem> ranked, String query, String brand, List<String> refs) {
        List<PageData> pages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int attempts = 0;
        for (SearchItem item : ranked) {
            if (attempts >= 24 || pages.size() >= 12) break;
            if (!seen.add(item.link)) continue;
            attempts++;
            try {
                String html = fetchHtml(item.link, 900000);
                String text = normalize(cleanHtmlStructured(html) + "\n" + extractMetaText(html) + "\n" +
                        extractStructuredSpecText(html) + "\n" + item.title + "\n" + item.description);
                ProductMeta product = findProductMeta(html);
                boolean official = isOfficialUrl(item.link, brand);
                boolean exact = pageHasReference(item.title + "\n" + text + "\n" + product.sku + " " + product.model, refs);
                if (!pageMatchesQuery(text, item.title, query, brand, refs, official, exact, product)) continue;

                PageData p = new PageData();
                p.url = item.link;
                p.title = item.title;
                p.text = text;
                p.official = official;
                p.score = item.score;
                p.tier = official ? 5 : sourceTier(item.link);
                p.exactReferenceMatch = exact;
                p.product = product;
                pages.add(p);
            } catch (Exception ignored) {
                // 제조사/고신뢰 DB가 접근을 막아도 exact reference가 검색 스니펫에 명확히 있으면
                // 제한적인 보조 근거로만 사용한다. 일반 웹사이트 스니펫은 사용하지 않는다.
                boolean official = isOfficialUrl(item.link, brand);
                int tier = official ? 5 : sourceTier(item.link);
                boolean exact = snippetHasStrongReference(item, refs);
                if (!refs.isEmpty() && exact && (official || tier >= 2)) {
                    PageData p = new PageData();
                    p.url = item.link;
                    p.title = item.title;
                    p.text = normalize(item.title + "\n" + item.description);
                    p.official = official;
                    p.score = item.score - 10;
                    p.tier = tier;
                    p.exactReferenceMatch = true;
                    p.snippetOnly = true;
                    pages.add(p);
                }
            }
        }
        pages.sort((a,b) -> {
            if (a.official != b.official) return a.official ? -1 : 1;
            if (a.exactReferenceMatch != b.exactReferenceMatch) return a.exactReferenceMatch ? -1 : 1;
            if (a.tier != b.tier) return Integer.compare(b.tier, a.tier);
            return Integer.compare(b.score, a.score);
        });
        return pages;
    }

    private static boolean pageMatchesQuery(String text, String title, String query, String brand, List<String> refs,
                                            boolean official, boolean exactRef, ProductMeta product) {
        String hay = normalizeForMatch(title + " " + text + " " + product.name + " " + product.model + " " + product.sku);
        boolean watchContext = containsAny(hay,
                "watch", "wristwatch", "timepiece", "movement", "caliber", "calibre",
                "water resistance", "case diameter", "case size", "power reserve");
        if (!watchContext) return false;

        // 레퍼런스가 주어진 경우 비공식 페이지는 반드시 exact reference를 포함해야 한다.
        if (!refs.isEmpty()) {
            if (exactRef) return true;
            if (!official) return false;
            // 공식 페이지에서 레퍼런스 표기가 지역별로 생략될 때 모델 토큰으로 보조 검증
            List<String> tokens = meaningfulModelTokens(query, brand, refs);
            int hits = tokenHits(hay, tokens);
            return !tokens.isEmpty() && hits >= Math.min(2, tokens.size()) && hits / (double) tokens.size() >= 0.60;
        }

        if (!brand.isEmpty() && !compact(hay).contains(compact(brand)) && !official) return false;
        List<String> tokens = meaningfulModelTokens(query, brand, refs);
        if (tokens.isEmpty()) return official;
        int hits = tokenHits(hay, tokens);
        double ratio = hits / (double) tokens.size();
        return (official && ratio >= 0.50 && hits >= 1) || (hits >= 2 && ratio >= 0.60);
    }

    // ---------------------------- SPEC COLLECTION ----------------------------

    private static List<ValueCandidate> collectMeasurement(List<PageData> pages, String[] labels, double min, double max) {
        List<ValueCandidate> out = new ArrayList<>();
        for (PageData p : pages) {
            ValueCandidate best = null;
            for (String label : labels) {
                String escaped = Pattern.quote(label);
                Pattern[] patterns = new Pattern[]{
                        Pattern.compile("(?i)(?:^|\\n|[|•;])\\s*" + escaped + "\\s*(?:\\(\\s*mm\\s*\\))?\\s*[:=\\-]?\\s*(?:approx\\.?|approximately|about|ca\\.?)?\\s*([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*(?:mm)?\\b"),
                        Pattern.compile("(?i)" + escaped + "[^\\n0-9]{0,35}([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*mm\\b")
                };
                for (Pattern pt : patterns) {
                    Matcher m = pt.matcher(p.text);
                    while (m.find()) {
                        try {
                            double v = Double.parseDouble(m.group(1));
                            if (v < min || v > max) continue;
                            ValueCandidate c = candidate(trimNumber(m.group(1)), p);
                            c.numeric = v;
                            c.key = formatNumericKey(v, 0.1);
                            if (best == null || c.weight > best.weight) best = c;
                            break;
                        } catch (Exception ignored) {}
                    }
                    if (best != null) break;
                }
                if (best != null) break;
            }
            if (best != null) out.add(best);
        }
        return out;
    }

    private static List<ValueCandidate> collectMeasurementForDomain(List<PageData> pages, String domain, String[] labels, double min, double max) {
        List<PageData> filtered = new ArrayList<>();
        for (PageData p : pages) if (hostEndsWith(p.url, domain)) filtered.add(p);
        return collectMeasurement(filtered, labels, min, max);
    }

    private static List<ValueCandidate> collectTripleDimension(List<PageData> pages,String[] domains,int index,double min,double max) {
        List<ValueCandidate> out=new ArrayList<>();
        Pattern triple=Pattern.compile("(?i)(?:case size|case dimensions?|ケースサイズ|ケース寸法)[^0-9]{0,80}([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*[x×]\\s*([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*[x×]\\s*([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*mm");
        for(PageData p:pages) {
            boolean domainOk=false;
            for(String d:domains) if(hostEndsWith(p.url,d)) {domainOk=true;break;}
            if(!domainOk) continue;
            Matcher m=triple.matcher(p.text);
            if(!m.find()) continue;
            try {
                double v=Double.parseDouble(m.group(index+1));
                if(v<min||v>max) continue;
                ValueCandidate c=candidate(trimNumber(m.group(index+1)),p); c.numeric=v; c.key=formatNumericKey(v,0.1); out.add(c);
            } catch(Exception ignored) {}
        }
        return out;
    }

    private static List<ValueCandidate> collectPowerReserve(List<PageData> pages) {
        List<ValueCandidate> out = new ArrayList<>();
        Pattern h = Pattern.compile("(?i)(?:power reserve|running time|duration|operating time|파워\\s*리저브|パワーリザーブ|駆動期間|持続時間|gangreserve)[^0-9]{0,100}(?:up to|approx\\.?|approximately|about|over|more than|約|最大)?\\s*([0-9]{1,3}(?:\\.[0-9])?)\\s*(?:hours?|hrs?|hr|h|시간|時間)\\b");
        Pattern d = Pattern.compile("(?i)(?:power reserve|running time|duration|operating time|gangreserve)[^0-9]{0,100}([0-9]{1,2}(?:\\.[0-9])?)\\s*days?\\b");
        for (PageData p : pages) {
            Matcher mh = h.matcher(p.text);
            if (mh.find()) {
                double v = Double.parseDouble(mh.group(1));
                if (v >= 10 && v <= 400) {
                    ValueCandidate c = candidate(trimNumber(mh.group(1)), p);
                    c.numeric = v; c.key = formatNumericKey(v, 1.0); out.add(c); continue;
                }
            }
            Matcher md = d.matcher(p.text);
            if (md.find()) {
                double v = Double.parseDouble(md.group(1)) * 24.0;
                if (v >= 10 && v <= 400) {
                    ValueCandidate c = candidate(trimNumber(String.valueOf(v)), p);
                    c.numeric = v; c.key = formatNumericKey(v, 1.0); out.add(c);
                }
            }
        }
        return out;
    }

    private static List<ValueCandidate> collectWaterResistance(List<PageData> pages) {
        List<ValueCandidate> out = new ArrayList<>();
        String label = "(?:water resistance|water-resistant|water resistant|waterproof|\\bwr\\b|방수|防水|wasserdicht|wasserdichtigkeit)";
        Pattern meter = Pattern.compile("(?i)" + label + "[^0-9]{0,100}([0-9]{1,4})\\s*(?:m|metres|meters|meter)\\b");
        Pattern bar = Pattern.compile("(?i)" + label + "[^0-9]{0,100}([0-9]{1,3})\\s*(?:bar|atm)\\b");
        Pattern jpAtm = Pattern.compile("(?i)(?:防水|water resistance)[^0-9]{0,100}([0-9]{1,3})\\s*気圧");
        for (PageData p : pages) {
            int meters = -1;
            Matcher mm = meter.matcher(p.text);
            if (mm.find()) meters = Integer.parseInt(mm.group(1));
            if (meters < 1) {
                Matcher mb = bar.matcher(p.text);
                if (mb.find()) meters = Integer.parseInt(mb.group(1)) * 10;
            }
            if (meters < 1) {
                Matcher mj = jpAtm.matcher(p.text);
                if (mj.find()) meters = Integer.parseInt(mj.group(1)) * 10;
            }
            if (meters >= 10 && meters <= 4000) {
                ValueCandidate c = candidate(meters + " m", p);
                c.numeric = meters; c.key = String.valueOf(meters); out.add(c);
            }
        }
        return out;
    }

    private static List<ValueCandidate> collectCaliber(List<PageData> pages) {
        List<ValueCandidate> out = new ArrayList<>();
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?i)(?:caliber number|calibre number|caliber no\\.?|calibre no\\.?|caliber|calibre|칼리버 코드|칼리버|キャリバー(?:No\\.?)?|ムーブメント番号|werk)\\s*[:#=\\-]?\\s*([A-Z0-9][A-Z0-9._\\-/]{1,20})"),
                Pattern.compile("(?i)(?:movement caliber|movement calibre)\\s*[:#=\\-]?\\s*([A-Z0-9][A-Z0-9._\\-/]{1,20})")
        };
        for (PageData p : pages) {
            String value = "";
            for (Pattern pt : patterns) {
                Matcher m = pt.matcher(p.text);
                while (m.find()) {
                    String v = cleanValue(m.group(1)).toUpperCase(Locale.ROOT);
                    if (v.length() < 2 || !v.matches(".*\\d.*")) continue;
                    if (v.matches("[0-9]{2,4}")) continue; // 단순 사이즈/방수 숫자 오인 방지
                    value = v; break;
                }
                if (!value.isEmpty()) break;
            }
            if (!value.isEmpty()) {
                ValueCandidate c = candidate(value, p); c.key = compact(value); out.add(c);
            }
        }
        return out;
    }

    private static List<ValueCandidate> collectMovement(List<PageData> pages) {
        List<ValueCandidate> out = new ArrayList<>();
        Pattern near = Pattern.compile("(?is)(?:movement type|movement|drive system|type of movement|무브먼트 유형|무브먼트 타입|무브먼트|駆動方式|駆動方法|ムーブメント|uhrwerk|werk).{0,120}");
        for (PageData p : pages) {
            String probe = "";
            Matcher m = near.matcher(p.text);
            if (m.find()) probe = m.group();
            String v = classifyMovement(probe);
            if (v.isEmpty() && p.tier >= 4) v = classifyMovement(p.text);
            if (!v.isEmpty()) { ValueCandidate c=candidate(v,p); c.key=v.toLowerCase(Locale.ROOT); out.add(c); }
        }
        return out;
    }

    private static String classifyMovement(String text) {
        String l = normalizeForMatch(text);
        if (l.isEmpty()) return "";
        if (containsAny(l, "spring drive")) return "Spring Drive";
        if (containsAny(l, "eco-drive", "eco drive", "solar quartz", "solar-powered quartz", "solar powered quartz")) return "Solar Quartz";
        // "Automatic with manual winding"은 수동이 아니라 오토매틱이므로 automatic을 먼저 판정한다.
        if (containsAny(l, "automatic", "self-winding", "self winding", "오토매틱", "自動巻", "自動巻き", "automatik")) return "Automatic";
        if (containsAny(l, "hand-wound", "hand wound", "manual winding", "manual-winding", "manually wound", "수동", "手巻", "手巻き", "handaufzug")) return "Manual";
        if (containsAny(l, "solar", "ソーラー", "光発電")) return "Solar Quartz";
        if (containsAny(l, "quartz", "쿼츠", "クオーツ", "quarz")) return "Quartz";
        return "";
    }

    private static List<ValueCandidate> collectCrystal(List<PageData> pages) {
        List<ValueCandidate> out = new ArrayList<>();
        Pattern near = Pattern.compile("(?is)(?:crystal|glass|front glass|글라스|크리스탈|ガラス材質|ガラス|glas).{0,140}");
        for (PageData p : pages) {
            String probe = "";
            Matcher m = near.matcher(p.text);
            if (m.find()) probe = m.group();
            String l = normalizeForMatch(probe);
            String v = "";
            if (containsAny(l,"sapphire","サファイア","saphir")) v="Sapphire";
            else if (containsAny(l,"hardlex","ハードレックス")) v="Hardlex";
            else if (containsAny(l,"hesalite","acrylic")) v="Acrylic";
            else if (containsAny(l,"mineral","ミネラル")) v="Mineral";
            else if (containsAny(l,"k1","k-1")) v="K1 Mineral";
            if (!v.isEmpty()) { ValueCandidate c=candidate(v,p); c.key=v.toLowerCase(Locale.ROOT); out.add(c); }
        }
        return out;
    }

    private static List<ValueCandidate> collectCaseMaterial(List<PageData> pages) {
        List<ValueCandidate> out = new ArrayList<>();
        Pattern near = Pattern.compile("(?is)(?:case material|case\\s*[:=\\-]|material of case|케이스 소재).{0,160}");
        for (PageData p : pages) {
            String probe = "";
            Matcher m = near.matcher(p.text);
            if (m.find()) probe = m.group();
            String l = normalizeForMatch(probe);
            if (l.isEmpty() && p.official) l = normalizeForMatch(p.text);
            String v = "";
            if (containsAny(l,"titanium","티타늄","チタン","titan")) v="Titanium";
            else if (containsAny(l,"stainless steel","stainless-steel","스테인리스","ステンレス","edelstahl")) v="Stainless Steel";
            else if (containsAny(l,"bronze","브론즈")) v="Bronze";
            else if (containsAny(l,"ceramic","세라믹","セラミック","keramik")) v="Ceramic";
            else if (containsAny(l,"carbon composite","carbon case","carbon fiber")) v="Carbon";
            else if (containsAny(l,"18k gold","yellow gold","rose gold","white gold")) v="Gold";
            if (!v.isEmpty()) { ValueCandidate c=candidate(v,p); c.key=v.toLowerCase(Locale.ROOT); out.add(c); }
        }
        return out;
    }

    // ---------------------------- CONSENSUS ----------------------------

    private static ValueCandidate candidate(String value, PageData page) {
        ValueCandidate c = new ValueCandidate();
        c.value = value;
        c.page = page;
        // official 7, high-trust DB 5, watch media 4, retail 3, unknown exact-ref 2
        if (page.official && page.exactReferenceMatch && !page.snippetOnly) c.weight = 10;
        else if (page.official && !page.snippetOnly) c.weight = 7;
        else if (page.official) c.weight = 5;
        else if (page.tier >= 4 && !page.snippetOnly) c.weight = 5;
        else if (page.tier >= 4) c.weight = 3;
        else if (page.tier == 3) c.weight = 4;
        else if (page.tier == 2) c.weight = 3;
        else c.weight = page.exactReferenceMatch ? 2 : 1;
        return c;
    }

    private static String chooseNumeric(List<ValueCandidate> values, double tolerance) {
        if (values.isEmpty()) return "";
        for (ValueCandidate c : values) if (c.page.official && c.page.exactReferenceMatch && !c.page.snippetOnly) return trimNumber(c.value);
        for (ValueCandidate c : values) if (c.page.official && !c.page.snippetOnly && c.weight>=7) return trimNumber(c.value);

        int bestScore = -1;
        int bestCount = 0;
        ValueCandidate best = null;
        for (ValueCandidate seed : values) {
            int score = 0, count = 0;
            for (ValueCandidate c : values) {
                if (!Double.isNaN(seed.numeric) && !Double.isNaN(c.numeric) && Math.abs(seed.numeric - c.numeric) <= tolerance) {
                    score += c.weight; count++;
                }
            }
            if (score > bestScore || (score == bestScore && count > bestCount)) {
                bestScore = score; bestCount = count; best = seed;
            }
        }
        if (best == null) return "";
        // 고신뢰 단일 출처 또는 서로 다른 2개 이상 출처가 합의한 경우만 채운다.
        if (bestCount >= 2 || best.weight >= 5) return trimNumber(best.value);
        return "";
    }

    private static String chooseString(List<ValueCandidate> values) {
        if (values.isEmpty()) return "";
        for (ValueCandidate c : values) if (c.page.official && c.page.exactReferenceMatch && !c.page.snippetOnly) return c.value;
        for (ValueCandidate c : values) if (c.page.official && !c.page.snippetOnly && c.weight>=7) return c.value;
        Map<String,Integer> score = new LinkedHashMap<>();
        Map<String,Integer> count = new LinkedHashMap<>();
        Map<String,String> display = new LinkedHashMap<>();
        Map<String,Integer> maxWeight = new LinkedHashMap<>();
        for (ValueCandidate c : values) {
            String k = c.key == null || c.key.isEmpty() ? compact(c.value) : c.key;
            score.put(k, score.getOrDefault(k,0)+c.weight);
            count.put(k, count.getOrDefault(k,0)+1);
            display.putIfAbsent(k,c.value);
            maxWeight.put(k,Math.max(maxWeight.getOrDefault(k,0),c.weight));
        }
        String best=""; int bs=-1;
        for (String k : score.keySet()) if (score.get(k)>bs) { bs=score.get(k); best=k; }
        if (best.isEmpty()) return "";
        if (count.get(best)>=2 || maxWeight.get(best)>=5) return display.get(best);
        return "";
    }

    private static String chooseWater(List<ValueCandidate> values) {
        String s = chooseNumeric(values, 5.0);
        if (s.isEmpty()) return "";
        try { return String.valueOf((int)Math.round(Double.parseDouble(s.replace(" m","")))) + " m"; }
        catch (Exception e) { return s; }
    }

    // ---------------------------- SEARCH ENGINES ----------------------------

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
            while (event != XmlPullParser.END_DOCUMENT && list.size() < 16) {
                if (event == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("item".equalsIgnoreCase(tag)) cur = new SearchItem();
                } else if (event == XmlPullParser.TEXT && cur != null) {
                    String v = p.getText();
                    if ("title".equalsIgnoreCase(tag)) cur.title += v;
                    else if ("link".equalsIgnoreCase(tag)) cur.link += v;
                    else if ("description".equalsIgnoreCase(tag)) cur.description += v;
                } else if (event == XmlPullParser.END_TAG && "item".equalsIgnoreCase(p.getName()) && cur != null) {
                    cur.title = cleanHtmlInline(cur.title);
                    cur.description = cleanHtmlInline(cur.description);
                    cur.link = cur.link.trim();
                    list.add(cur);
                    cur = null;
                }
                event = p.next();
            }
        } finally { con.disconnect(); }
        return list;
    }

    private static List<SearchItem> searchDuckDuckGoLite(String query) throws Exception {
        String url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        String html = fetchHtml(url, 350000);
        List<SearchItem> list = new ArrayList<>();
        Pattern link = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
        Matcher m = link.matcher(html);
        while (m.find() && list.size() < 14) {
            String href = decodeEntities(m.group(1)).trim();
            String title = cleanHtmlInline(m.group(2));
            if (title.isEmpty()) continue;
            href = decodeDuckUrl(href);
            if (!isHttp(href)) continue;
            SearchItem s = new SearchItem();
            s.link = href; s.title = title; s.description = "";
            list.add(s);
        }
        return list;
    }

    private static List<SearchItem> searchBingHtml(String query) throws Exception {
        String url="https://www.bing.com/search?count=20&q="+URLEncoder.encode(query,StandardCharsets.UTF_8.name());
        String html=fetchHtml(url,500000);
        List<SearchItem> list=new ArrayList<>();
        Matcher block=Pattern.compile("(?is)<li[^>]+class=[\\\"'][^\\\"']*b_algo[^\\\"']*[\\\"'][^>]*>(.*?)</li>").matcher(html);
        while(block.find()&&list.size()<16) {
            String b=block.group(1);
            Matcher a=Pattern.compile("(?is)<h2[^>]*>.*?<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>").matcher(b);
            if(!a.find()) continue;
            SearchItem item=new SearchItem();
            item.link=decodeEntities(a.group(1)).trim();
            item.title=cleanHtmlInline(a.group(2));
            Matcher d=Pattern.compile("(?is)<p[^>]*>(.*?)</p>").matcher(b);
            item.description=d.find()?cleanHtmlInline(d.group(1)):"";
            if(isHttp(item.link)) list.add(item);
        }
        return list;
    }

    private static List<SearchItem> searchYahooJapan(String query) throws Exception {
        String url="https://search.yahoo.co.jp/search?p="+URLEncoder.encode(query,StandardCharsets.UTF_8.name());
        String html=fetchHtml(url,500000);
        List<SearchItem> list=new ArrayList<>();
        Matcher m=Pattern.compile("(?is)<a[^>]+href=[\\\"'](https?://[^\\\"']+)[\\\"'][^>]*>(.*?)</a>").matcher(html);
        Set<String> seen=new LinkedHashSet<>();
        while(m.find()&&list.size()<16) {
            String href=decodeEntities(m.group(1)).trim();
            if(hostEndsWith(href,"yahoo.co.jp")||hostEndsWith(href,"yahoo.com")||isBlocked(href)) continue;
            href=normalizeResultUrl(href);
            if(!seen.add(href)) continue;
            String title=cleanHtmlInline(m.group(2));
            if(title.length()<3) continue;
            SearchItem item=new SearchItem(); item.link=href; item.title=title; item.description="";
            list.add(item);
        }
        return list;
    }

    private static List<SearchItem> searchGoogleHtml(String query) throws Exception {
        String url="https://www.google.com/search?num=20&hl=en&q="+URLEncoder.encode(query,StandardCharsets.UTF_8.name());
        String html=fetchHtml(url,450000);
        List<SearchItem> out=new ArrayList<>();
        Set<String> seen=new LinkedHashSet<>();
        Matcher m=Pattern.compile("(?is)href=[\\\"'](?:/url\\?q=)?(https?://[^\\\"'& ]+)[^\\\"']*[\\\"']").matcher(html);
        while(m.find()&&out.size()<15) {
            String href=decodeEntities(m.group(1));
            if(hostEndsWith(href,"google.com")||hostEndsWith(href,"googleusercontent.com")||isBlocked(href)) continue;
            href=normalizeResultUrl(href);
            if(!seen.add(href)) continue;
            SearchItem item=new SearchItem(); item.link=href; item.title=href; item.description="Google result"; out.add(item);
        }
        return out;
    }

    private static List<SearchItem> searchWaybackOfficial(String brand,List<String> refs) {
        List<SearchItem> out=new ArrayList<>();
        String[] domains=OFFICIAL.get(brand);
        if(domains==null||refs.isEmpty()) return out;
        String ref=refs.get(0);
        for(String domain:domains) {
            try {
                String wildcard="*"+domain+"/*"+ref+"*";
                String api="https://web.archive.org/cdx/search/cdx?output=json&filter=statuscode:200&filter=mimetype:text/html&collapse=urlkey&limit=6&fl=timestamp,original&url="+
                        URLEncoder.encode(wildcard,StandardCharsets.UTF_8.name());
                String json=fetchHtml(api,250000);
                JSONArray rows=new JSONArray(json);
                for(int i=1;i<rows.length()&&out.size()<8;i++) {
                    JSONArray row=rows.optJSONArray(i); if(row==null||row.length()<2) continue;
                    String ts=row.optString(0,""); String original=row.optString(1,"");
                    if(ts.isEmpty()||original.isEmpty()||!urlHasAnyReference(original,refs)) continue;
                    SearchItem item=new SearchItem();
                    item.link="https://web.archive.org/web/"+ts+"id_/"+original;
                    item.title=brand+" official archive · "+ref;
                    item.description="Archived official product page · "+original;
                    out.add(item);
                }
            } catch(Exception ignored) {}
        }
        return out;
    }

    private static List<SearchItem> searchOfficialSitemaps(String brand,List<String> refs) {
        List<SearchItem> out=new ArrayList<>();
        String[] domains=OFFICIAL.get(brand);
        if(domains==null||refs.isEmpty()) return out;
        Set<String> seenMaps=new LinkedHashSet<>();
        Set<String> seenUrls=new LinkedHashSet<>();
        for(String domain:domains) {
            List<String> roots=new ArrayList<>();
            roots.add("https://"+domain+"/sitemap.xml");
            roots.add("https://"+domain+"/sitemap_index.xml");
            roots.add("https://www."+domain+"/sitemap.xml");
            for(String root:roots) scanSitemap(root,refs,out,seenMaps,seenUrls,0);
        }
        return out;
    }

    private static void scanSitemap(String sitemap,List<String> refs,List<SearchItem> out,Set<String> seenMaps,Set<String> seenUrls,int depth) {
        if(depth>1||out.size()>=12||!seenMaps.add(sitemap)) return;
        try {
            String xml=fetchHtml(sitemap,2200000);
            Matcher loc=Pattern.compile("(?is)<loc>\\s*(.*?)\\s*</loc>").matcher(xml);
            List<String> children=new ArrayList<>();
            while(loc.find()&&out.size()<12) {
                String url=decodeEntities(loc.group(1)).trim();
                if(!isHttp(url)) continue;
                String lower=url.toLowerCase(Locale.ROOT);
                if(lower.endsWith(".xml")||lower.contains("sitemap")) {
                    if(children.size()<10 && (lower.contains("product")||lower.contains("watch")||lower.contains("catalog")||children.size()<4)) children.add(url);
                    continue;
                }
                if(urlHasAnyReference(url,refs)&&seenUrls.add(url)) {
                    SearchItem item=new SearchItem(); item.link=url; item.title=url; item.description="Official sitemap reference match"; item.score=400;
                    out.add(item);
                }
            }
            if(out.size()<6) for(String child:children) scanSitemap(child,refs,out,seenMaps,seenUrls,depth+1);
        } catch(Exception ignored) {}
    }

    private static boolean urlHasAnyReference(String url,List<String> refs) {
        String h=compact(url);
        for(String ref:refs) if(!ref.isEmpty()&&h.contains(compact(ref))) return true;
        return false;
    }

    private static String decodeDuckUrl(String href) {
        try {
            if (href.startsWith("//")) href = "https:" + href;
            URI u = URI.create(href);
            if (u.getHost() != null && u.getHost().contains("duckduckgo.com") && u.getRawQuery() != null) {
                for (String part : u.getRawQuery().split("&")) {
                    if (part.startsWith("uddg=")) return java.net.URLDecoder.decode(part.substring(5), "UTF-8");
                }
            }
        } catch (Exception ignored) {}
        return href;
    }

    // ---------------------------- PAGE / META ----------------------------

    private static String fetchHtml(String url, int maxChars) throws Exception {
        HttpURLConnection con=open(url);
        try {
            int code=con.getResponseCode();
            if(code>=400) throw new IOException("HTTP "+code+" for "+url);
            InputStream raw=con.getInputStream();
            String enc=con.getContentEncoding();
            if((enc!=null&&enc.toLowerCase(Locale.ROOT).contains("gzip"))||url.toLowerCase(Locale.ROOT).endsWith(".gz")) raw=new GZIPInputStream(raw);
            StringBuilder b=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(raw,StandardCharsets.UTF_8))) {
                String line;
                while((line=r.readLine())!=null&&b.length()<maxChars) b.append(line).append('\n');
            }
            return b.toString();
        } finally { con.disconnect(); }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setConnectTimeout(9000);
        con.setReadTimeout(13000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36 WatchCollection/0.5");
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko-KR;q=0.8,ja;q=0.7");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return con;
    }

    private static String extractMetaText(String html) {
        if(html==null) return "";
        StringBuilder b=new StringBuilder();
        Matcher title=Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        if(title.find()) b.append(cleanHtmlInline(title.group(1))).append('\n');
        Matcher meta=Pattern.compile("(?is)<meta[^>]+(?:name|property)=[\\\"'](?:description|og:description|og:title|twitter:description|twitter:title)[\\\"'][^>]+content=[\\\"'](.*?)[\\\"'][^>]*>").matcher(html);
        while(meta.find()&&b.length()<5000) b.append(cleanHtmlInline(meta.group(1))).append('\n');
        return b.toString();
    }

    private static String extractStructuredSpecText(String html) {
        if(html==null) return "";
        String decoded=decodeEntities(html)
                .replace("\\\\u002F","/").replace("\\\\u0026","&")
                .replace("\\\\\"","\"").replace("\\\\/","/");
        StringBuilder b=new StringBuilder();
        // schema.org additionalProperty: {name: "Case diameter", value: "42 mm"}
        Matcher ap=Pattern.compile("(?is)[\\\"'](?:name|label)[\\\"']\\s*:\\s*[\\\"']([^\\\"']{2,60})[\\\"']\\s*,\\s*[\\\"'](?:value|text)[\\\"']\\s*:\\s*[\\\"']([^\\\"']{1,90})[\\\"']").matcher(decoded);
        while(ap.find()&&b.length()<12000) {
            String name=cleanHtmlInline(ap.group(1));
            if(isSpecLabel(name)) b.append(name).append(": ").append(cleanHtmlInline(ap.group(2))).append('\n');
        }
        // Next.js/Nuxt/Shopify 등의 평탄한 JSON key/value
        String keys="caseDiameter|diameter|caseSize|caseWidth|caseLength|lugToLug|lugWidth|distanceBetweenLugs|thickness|caseThickness|caseHeight|powerReserve|waterResistance|caliber|calibre|movement|movementType|crystal|glass|caseMaterial";
        Matcher kv=Pattern.compile("(?is)[\\\"']("+keys+")[\\\"']\\s*:\\s*[\\\"']?([^\\\"'{}\\[\\],]{1,100})[\\\"']?").matcher(decoded);
        while(kv.find()&&b.length()<18000) b.append(kv.group(1)).append(": ").append(cleanHtmlInline(kv.group(2))).append('\n');
        return b.toString();
    }

    private static boolean isSpecLabel(String name) {
        String n=normalizeForMatch(name);
        return containsAny(n,"diameter","case size","case width","case length","lug","thickness","height","power reserve","water resistance","caliber","calibre","movement","crystal","glass","case material","직경","두께","러그","방수","무브먼트","칼리버");
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
                out.name = product.optString("name", "");
                Object brand = product.opt("brand");
                if (brand instanceof JSONObject) out.brand = ((JSONObject) brand).optString("name", "");
                else if (brand instanceof String) out.brand = (String) brand;
                if (!out.sku.isEmpty() || !out.model.isEmpty() || !out.name.isEmpty()) return out;
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static JSONObject findProductObject(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject o = (JSONObject)obj;
            String type = o.optString("@type", "");
            if ("Product".equalsIgnoreCase(type)) return o;
            Object graph = o.opt("@graph");
            JSONObject g = findProductObject(graph);
            if (g != null) return g;
            for (String key : new String[]{"mainEntity","item","product"}) {
                JSONObject x = findProductObject(o.opt(key));
                if (x != null) return x;
            }
        } else if (obj instanceof JSONArray) {
            JSONArray a=(JSONArray)obj;
            for(int i=0;i<a.length();i++) {
                JSONObject x=findProductObject(a.opt(i));
                if(x!=null) return x;
            }
        }
        return null;
    }

    private static String cleanHtmlStructured(String s) {
        if (s == null) return "";
        String t = s
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?</noscript>", " ")
                .replaceAll("(?is)</?(?:tr|td|th|li|p|div|section|article|h1|h2|h3|h4|br)[^>]*>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        t = decodeEntities(t);
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    private static String cleanHtmlInline(String s) {
        if (s == null) return "";
        return decodeEntities(s.replaceAll("(?is)<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    // ---------------------------- SOURCE / MATCH HELPERS ----------------------------

    private static int sourceTier(String url) {
        if (containsDomain(url, HIGH_TRUST)) return 4;
        if (containsDomain(url, WATCH_MEDIA)) return 3;
        if (containsDomain(url, RETAIL_SOURCES)) return 2;
        return 0;
    }

    private static boolean containsDomain(String url, Set<String> domains) {
        for (String d : domains) if (hostEndsWith(url,d)) return true;
        return false;
    }

    private static boolean isOfficialUrl(String url, String brand) {
        String[] domains = OFFICIAL.get(brand);
        if (domains == null) return false;
        for (String d : domains) {
            if (hostEndsWith(url,d)) return true;
            // Internet Archive 안에 보관된 제조사 원본 URL도 공식 출처로 취급합니다.
            if (hostEndsWith(url,"web.archive.org") && normalizeForMatch(url).contains(d.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean isBlocked(String url) {
        return containsDomain(url, BLOCKED);
    }

    private static boolean hostEndsWith(String url, String domain) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            domain = domain.toLowerCase(Locale.ROOT);
            if(host.equals(domain) || host.endsWith("." + domain)) return true;
            if(host.equals("web.archive.org")) {
                String low=url.toLowerCase(Locale.ROOT);
                return low.contains("://"+domain+"/") || low.contains("://www."+domain+"/");
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private static String normalizeResultUrl(String url) {
        if (url == null) return "";
        String s = decodeEntities(url.trim());
        try {
            URI u = URI.create(s);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null) return s;
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            return scheme + "://" + host + path;
        } catch (Exception e) { return s; }
    }

    private static boolean snippetHasStrongReference(SearchItem item, List<String> refs) {
        return pageHasReference(item.title + " " + item.description + " " + item.link, refs);
    }

    private static boolean pageHasReference(String text, List<String> refs) {
        if (refs.isEmpty()) return false;
        String h = compact(text);
        for (String ref : refs) if (!ref.isEmpty() && h.contains(compact(ref))) return true;
        return false;
    }

    private static int tokenHits(String hay, List<String> tokens) {
        int hits=0;
        for(String t:tokens) if(hay.contains(t)) hits++;
        return hits;
    }

    private static List<String> meaningfulModelTokens(String query, String brand, List<String> refs) {
        String q = normalizeForMatch(query);
        String brandNorm = normalizeForMatch(brand);
        List<String> out = new ArrayList<>();
        for (String token : q.split("[^a-z0-9가-힣]+")) {
            if (token.length() < 2) continue;
            if (token.equals("watch") || token.equals("watches") || token.equals("mm") || token.equals("ref") || token.equals("model") || token.equals("automatic") || token.equals("quartz")) continue;
            if (!brandNorm.isEmpty() && brandNorm.contains(token)) continue;
            boolean isRefPart=false;
            for(String r:refs) if(compact(r).contains(compact(token)) && token.matches(".*\\d.*")) { isRefPart=true; break; }
            if(isRefPart) continue;
            if (!out.contains(token)) out.add(token);
        }
        return out;
    }

    private static List<String> queryReferenceVariants(String ref,String brand) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(ref==null||ref.isEmpty()) return new ArrayList<>(out);
        out.add(ref.toUpperCase(Locale.ROOT));
        String compactRef=ref.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
        if(!compactRef.equals(ref.toUpperCase(Locale.ROOT))) out.add(compactRef);
        for(String a:referenceAliases(ref,brand)) out.add(a);
        if(("Seiko".equals(brand)||"Grand Seiko".equals(brand))&&compactRef.matches("[A-Z]{3,5}\\d{3}")) {
            out.add(compactRef+"J1"); out.add(compactRef+"J");
        }
        return new ArrayList<>(out);
    }

    private static List<String> referenceAliases(String ref,String brand) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(ref==null) return new ArrayList<>(out);
        String u=ref.toUpperCase(Locale.ROOT).trim();
        String c=u.replaceAll("[^A-Z0-9]","");
        out.add(u);
        if(!c.equals(u)) out.add(c);
        // Seiko의 J/J1/K/K1 등 유통지역 suffix는 본체 reference와 함께 검색/검증합니다.
        if(("Seiko".equals(brand)||c.matches("(?:SPB|SLA|SJE|SRP|SRPE|SRPD|SSK|SSC|SNE|SNJ|SRPK|SRPL)\\d{3}[A-Z0-9]*"))) {
            String base=c.replaceFirst("(?:J1|K1|P1|J|K|P)$","");
            if(base.length()>=6&&!base.equals(c)) out.add(base);
        }
        return new ArrayList<>(out);
    }

    private static boolean isJapaneseBrand(String brand) {
        return "Seiko".equals(brand)||"Grand Seiko".equals(brand)||"Citizen".equals(brand)||"Casio".equals(brand)||"G-Shock".equals(brand)||"Orient".equals(brand);
    }

    private static String inferBrand(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<Map.Entry<String,String>> aliases = new ArrayList<>(ALIASES.entrySet());
        aliases.sort((a,b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String,String> e : aliases) {
            String a=e.getKey();
            if(a.length()<=2) {
                if(Pattern.compile("(?i)(?:^|[^a-z0-9])"+Pattern.quote(a)+"(?:$|[^a-z0-9])").matcher(lower).find()) return e.getValue();
            } else if(lower.contains(a)) return e.getValue();
        }
        return inferBrandFromReference(text);
    }

    private static String inferBrandFromReference(String text) {
        String u=text.toUpperCase(Locale.ROOT);
        if (u.matches(".*\\b(?:BM|BN|CA|CB|CC|NJ|NB|NY)\\d{4}-\\d{2}[A-Z]\\b.*")) return "Citizen";
        if (u.matches(".*\\b(?:SPB|SLA|SJE|SRP|SRPE|SRPD|SSK|SSC|SNE|SNJ|SRPK|SRPL)\\d{3}[A-Z0-9]*\\b.*")) return "Seiko";
        if (u.matches(".*\\b(?:SBGX|SBGA|SBGW|SLGA|SLGH|SLGW|SBGH|SBGP|SBGM)\\d{3}\\b.*")) return "Grand Seiko";
        if (u.matches(".*\\bH\\d{8}\\b.*")) return "Hamilton";
        if (u.matches(".*\\bT\\d{3}\\.\\d{3}\\.\\d{2}\\.\\d{3}\\.\\d{2}\\b.*")) return "Tissot";
        if (u.matches(".*\\bM0\\d{2}\\.\\d{3}\\.\\d{2}\\.\\d{3}\\.\\d{2}\\b.*")) return "Mido";
        if (u.matches(".*\\bL\\d\\.\\d{3}\\.\\d\\.\\d{2}\\.\\d\\b.*")) return "Longines";
        if (u.matches(".*\\bM\\d{5}[A-Z]{0,2}-\\d{3,4}\\b.*")) return "Tudor";
        return "";
    }

    private static String canonicalBrand(String brand) {
        String b = brand == null ? "" : brand.trim();
        if (b.isEmpty()) return "";
        String inferred=inferBrand(b);
        return inferred.isEmpty()?b:inferred;
    }

    private static List<String> referenceCandidates(String text) {
        List<String> out=new ArrayList<>();
        Matcher m=Pattern.compile("(?i)\\b[A-Z0-9][A-Z0-9._/-]{3,28}\\b").matcher(text.toUpperCase(Locale.ROOT));
        while(m.find()) {
            String token=m.group();
            if(!token.matches(".*\\d.*")) continue;
            if(token.matches("\\d+(?:\\.\\d+)?MM")) continue;
            if(token.matches("\\d{4}")) continue;
            if(!out.contains(token)) out.add(token);
        }
        out.sort((a,b)->Integer.compare(referenceStrength(b),referenceStrength(a)));
        return out;
    }

    private static String normalizeReference(String s) {
        if (s == null) return "";
        return normalize(s).trim().toUpperCase(Locale.ROOT);
    }

    private static int referenceStrength(String s) {
        int n=s.length();
        if(s.contains("-")||s.contains(".")) n+=6;
        if(s.matches(".*[A-Z].*")&&s.matches(".*\\d.*")) n+=6;
        if(s.matches(".*\\d{3,}.*")) n+=2;
        return n;
    }

    private static boolean looksLikeReference(String s) {
        if(s==null) return false;
        String v=s.trim();
        return v.length()>=4&&v.length()<=30&&v.matches("(?i)[A-Z0-9][A-Z0-9._/-]*")&&v.matches(".*\\d.*");
    }

    private static int countFilled(Result r) {
        int n=0;
        String[] v={r.movement,r.caliber,r.diameterMm,r.lugToLugMm,r.thicknessMm,r.lugWidthMm,r.powerReserveHours,r.waterResistance,r.crystal,r.caseMaterial};
        for(String s:v) if(s!=null&&!s.isEmpty()) n++;
        return n;
    }

    private static String formatNumericKey(double v, double step) {
        double rounded=Math.round(v/step)*step;
        return String.format(Locale.ROOT,"%.2f",rounded);
    }

    private static boolean isHttp(String s) {
        return s!=null&&(s.startsWith("https://")||s.startsWith("http://"));
    }

    private static String normalize(String s) {
        if(s==null) return "";
        return s.replace('×','x').replace('–','-').replace('—','-').replace('−','-')
                .replaceAll("[ \\t]+"," ");
    }

    private static String normalizeForMatch(String s) {
        return normalize(s).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
    }

    private static String compact(String s) {
        return normalizeForMatch(s).replaceAll("[^a-z0-9가-힣]","");
    }

    private static boolean containsAny(String text, String... values) {
        for(String v:values) if(text.contains(v)) return true;
        return false;
    }

    private static String firstNonEmpty(String... values) {
        for(String v:values) if(v!=null&&!v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static String cleanValue(String s) {
        if(s==null) return "";
        return s.trim().replaceAll("[;,]+$","");
    }

    private static String trimNumber(String s) {
        if(s==null) return "";
        String v=s.trim();
        try {
            double d=Double.parseDouble(v);
            if(Math.abs(d-Math.rint(d))<0.000001) return String.valueOf((long)Math.rint(d));
            return String.format(Locale.ROOT,"%.2f",d).replaceAll("0+$","").replaceAll("\\.$","");
        } catch(Exception e) { return v; }
    }
}
