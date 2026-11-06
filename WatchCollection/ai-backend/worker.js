/**
 * Watch Collection AI Backend v1.31 - Cloudflare Worker
 *
 * 필수 Secrets
 * - OPENAI_API_KEY: Responses API 호출용 프로젝트 키
 * - OPENAI_ADMIN_KEY: 조직 Costs API 조회용 Admin API 키
 * - WATCH_APP_TOKEN: Android 앱과 Worker 사이의 전용 접근 토큰
 *
 * 필수 Variables (크레딧 게이지/보호 기능)
 * - OPENAI_CREDIT_TOTAL_USD: 첫 지급분의 실제 지급액
 * - OPENAI_CREDIT_START_AT: 첫 지급분 시작 시각(ISO 8601)
 * - OPENAI_CREDIT_EXPIRES_AT: 첫 지급분 만료 시각(ISO 8601, 포함)
 *
 * 자동 월별 갱신 Variable
 * - OPENAI_CREDIT_RECURRING_TOTAL_USD: 이후 정규 월 지급액
 *   이 값을 설정하면 첫 지급분 만료 직후부터 월 단위로 지급 주기를 자동 계산합니다.
 *
 * 선택 Variables
 * - OPENAI_MODEL (기본값: gpt-6-luna)
 * - OPENAI_SERVICE_TIER (기본값: default)
 * - OPENAI_CREDIT_GUARD_ENABLED (기본값: 크레딧 설정이 완성되면 true)
 * - OPENAI_CREDIT_RESERVE_USD (기본값: 0.00)
 * - OPENAI_SPEC_REQUEST_RESERVE_USD (기본값: 0.05)
 * - OPENAI_RECOMMEND_REQUEST_RESERVE_USD (기본값: 0.01)
 * - OPENAI_USAGE_CACHE_SECONDS (기본값: 60)
 * - OPENAI_PROJECT_ID (선택: 특정 프로젝트 비용만 합산할 때만 사용)
 */

const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
const OPENAI_COSTS_URL = "https://api.openai.com/v1/organization/costs";
const COSTS_PAGE_LIMIT = 180;

// Cloudflare isolate 안에서만 유지되는 짧은 캐시입니다.
// Costs API 호출을 지나치게 반복하지 않되, 모델 호출 직후에는 무효화합니다.
let creditStatusCache = null;

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Content-Type, X-Watch-Token",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    const url = new URL(request.url);

    if (!authorized(request, env)) {
      return json({ ok: false, error: "Watch App Token이 올바르지 않습니다." }, 401, cors);
    }

    if (url.pathname === "/health") {
      return json({
        ok: true,
        model: env.OPENAI_MODEL || "gpt-6-luna",
        usage_configured: hasCreditConfiguration(env),
        credit_guard_enabled: isCreditGuardEnabled(env),
      }, 200, cors);
    }

    if (url.pathname === "/api/usage") {
      if (request.method !== "GET") {
        return json({ ok: false, error: "GET 요청만 지원합니다." }, 405, cors);
      }
      try {
        const force = url.searchParams.get("refresh") === "1";
        const data = await getCreditStatus(env, { force });
        return json({ ok: true, data }, 200, cors, { "Cache-Control": "no-store" });
      } catch (error) {
        return json({ ok: false, error: readableError(error) }, 502, cors, { "Cache-Control": "no-store" });
      }
    }

    if (!env.OPENAI_API_KEY) {
      return json({ ok: false, error: "서버에 OPENAI_API_KEY가 설정되지 않았습니다." }, 500, cors);
    }
    if (request.method !== "POST") {
      return json({ ok: false, error: "POST 요청만 지원합니다." }, 405, cors);
    }

    try {
      const payload = await request.json();

      if (url.pathname === "/api/watch/spec-search") {
        const gate = await enforceCreditGuard(env, "spec-search");
        if (gate) return json(gate.body, gate.status, cors, { "Cache-Control": "no-store" });

        const data = await searchWatchSpec(payload, env);
        invalidateCreditStatusCache();
        return json({ ok: true, data }, 200, cors);
      }

      if (url.pathname === "/api/watch/recommend") {
        const gate = await enforceCreditGuard(env, "recommend");
        if (gate) return json(gate.body, gate.status, cors, { "Cache-Control": "no-store" });

        const data = await recommendWatch(payload, env);
        invalidateCreditStatusCache();
        return json({ ok: true, data }, 200, cors);
      }

      if (url.pathname === "/api/outfit/complete") {
        const gate = await enforceCreditGuard(env, "recommend");
        if (gate) return json(gate.body, gate.status, cors, { "Cache-Control": "no-store" });

        const data = await completeOutfit(payload, env);
        invalidateCreditStatusCache();
        return json({ ok: true, data }, 200, cors);
      }

      if (url.pathname === "/api/collection/evaluate") {
        const gate = await enforceCreditGuard(env, "recommend");
        if (gate) return json(gate.body, gate.status, cors, { "Cache-Control": "no-store" });

        const data = await evaluateCollection(payload, env);
        invalidateCreditStatusCache();
        return json({ ok: true, data }, 200, cors);
      }

      return json({ ok: false, error: "지원하지 않는 경로입니다." }, 404, cors);
    } catch (error) {
      return json({ ok: false, error: readableError(error) }, 500, cors);
    }
  },
};

function authorized(request, env) {
  const expected = String(env.WATCH_APP_TOKEN || "").trim();
  const supplied = String(request.headers.get("X-Watch-Token") || "").trim();
  return expected.length >= 12 && supplied === expected;
}

async function searchWatchSpec(payload, env) {
  const query = String(payload?.query || "").trim();
  const currentBrand = String(payload?.current_brand || "").trim();
  const currentReference = String(payload?.current_reference || "").trim();
  if (!query) throw new Error("모델명이 비어 있습니다.");

  const schema = {
    type: "object",
    additionalProperties: false,
    properties: {
      brand: { type: "string" },
      model: { type: "string" },
      reference: { type: "string" },
      power: { type: "string", enum: ["Automatic", "Mechanical", "Quartz", "Solar-Quartz", ""] },
      movement: { type: "string" },
      diameter_mm: { type: "string" },
      lug_to_lug_mm: { type: "string" },
      thickness_mm: { type: "string" },
      lug_width_mm: { type: "string" },
      power_reserve_hours: { type: "string" },
      water_resistance: { type: "string" },
      crystal: { type: "string" },
      case_material: { type: "string" },
      confidence: { type: "number", minimum: 0, maximum: 1 },
      summary: { type: "string" },
      sources: {
        type: "array",
        maxItems: 8,
        items: {
          type: "object",
          additionalProperties: false,
          properties: {
            title: { type: "string" },
            url: { type: "string" },
            type: { type: "string", enum: ["official", "database", "dealer", "review", "other"] },
          },
          required: ["title", "url", "type"],
        },
      },
    },
    required: [
      "brand", "model", "reference", "power", "movement", "diameter_mm",
      "lug_to_lug_mm", "thickness_mm", "lug_width_mm", "power_reserve_hours",
      "water_resistance", "crystal", "case_material", "confidence", "summary", "sources",
    ],
  };

  const instructions = `
You are the specification verifier for a personal watch collection app.
Search the web and identify the exact watch model, not a neighboring reference or a similar family model.
Prioritize the manufacturer's official product page, official manual, or official catalog. Use reliable watch databases or authorized dealers only to fill gaps.
Use no more than two web searches for one request, and keep the search context concise.
Never invent a specification. If a field cannot be verified, return an empty string.
Normalize Power to one of: Automatic, Mechanical, Quartz, Solar-Quartz, or empty.
Return numeric dimensions as plain number strings without units. Return water resistance with its unit.
The sources array must contain real pages used for the exact model. Avoid search-result URLs, homepages, marketplaces with ambiguous model matching, and unrelated variants.
Write summary in Korean and keep it concise.
`;

  const userInput = JSON.stringify({
    query,
    current_brand: currentBrand,
    current_reference: currentReference,
  });

  return callOpenAI({
    env,
    instructions,
    input: userInput,
    schemaName: "watch_specification",
    schema,
    useWebSearch: true,
    maxOutputTokens: 2200,
  });
}

async function recommendWatch(payload, env) {
  const outfit = String(payload?.outfit || "").trim();
  const watches = Array.isArray(payload?.watches) ? payload.watches : [];
  const straps = Array.isArray(payload?.straps) ? payload.straps : [];
  if (!outfit) throw new Error("착장 설명이 비어 있습니다.");
  if (!watches.length) throw new Error("추천 가능한 시계가 없습니다.");
  if (!straps.length) throw new Error("추천 가능한 스트랩이 없습니다.");

  const schema = {
    type: "object",
    additionalProperties: false,
    properties: {
      outfit_summary: { type: "string" },
      recommendations: {
        type: "array",
        minItems: 1,
        maxItems: 3,
        items: {
          type: "object",
          additionalProperties: false,
          properties: {
            rank: { type: "integer", minimum: 1, maximum: 3 },
            watch_id: { type: "integer" },
            strap_id: { type: "integer" },
            reason: { type: "string" },
          },
          required: ["rank", "watch_id", "strap_id", "reason"],
        },
      },
    },
    required: ["outfit_summary", "recommendations"],
  };

  const instructions = `
You recommend watch and strap combinations from a user's existing collection.
Use only the supplied watch IDs and strap IDs. Never invent an item.
A strap is compatible only when strap.width_mm exactly equals watch.lug_width_mm.
Return up to three unique watches ranked from best to third.
Match formality, color, material, season, and the user's outfit.
Do NOT use last-worn date, days-not-worn, or monthly wear frequency as recommendation criteria.
The app has already removed watches in the user's exclusion list and watches marked unavailable or under repair; do not reintroduce them.
Recent consecutive wear must NOT be used as an exclusion or ranking criterion for outfit recommendations.
Every recommendation must include a compatible saved strap.
Write outfit_summary and reason in concise Korean.
`;

  return callOpenAI({
    env,
    instructions,
    input: JSON.stringify({ outfit, watches, straps }),
    schemaName: "watch_outfit_recommendations",
    schema,
    useWebSearch: false,
    maxOutputTokens: 1400,
  });
}


async function completeOutfit(payload, env) {
  const top = String(payload?.top || "").trim();
  const bottom = String(payload?.bottom || "").trim();
  const shoes = String(payload?.shoes || "").trim();
  const watch = payload?.watch && typeof payload.watch === "object" ? payload.watch : {};
  const strap = payload?.strap && typeof payload.strap === "object" ? payload.strap : {};
  const feedback = String(payload?.feedback || "").trim();
  const previous = payload?.previous && typeof payload.previous === "object" ? payload.previous : {};

  if (!Number.isFinite(Number(watch?.id)) || Number(watch.id) <= 0) {
    throw new Error("시계를 선택해야 합니다.");
  }
  if (!Number.isFinite(Number(strap?.id)) || Number(strap.id) <= 0) {
    throw new Error("스트랩을 선택해야 합니다.");
  }

  const schema = {
    type: "object",
    additionalProperties: false,
    properties: {
      top: { type: "string" },
      bottom: { type: "string" },
      shoes: { type: "string" },
      summary: { type: "string" },
      reason: { type: "string" },
    },
    required: ["top", "bottom", "shoes", "summary", "reason"],
  };

  const instructions = `
You complete a Korean user's outfit around one watch and one compatible strap that the user has already selected.
The user may leave top, bottom, or shoes blank. Fill only blank clothing fields with concrete, wearable recommendations.
Keep every non-empty user-entered clothing field unchanged unless feedback explicitly asks you to revise it.
When feedback and a previous recommendation are supplied, revise the recommendation according to the feedback while preserving unaffected user-entered fields.
Use the supplied watch and strap facts only. Do not invent another watch or strap.
Consider color harmony, material, formality, season-neutral practicality, and how the watch/strap acts as an accent.
Return concise Korean phrases. Avoid vague words such as '적당한 옷'.
summary should show the completed outfit in one short sentence. reason should explain the coordination logic in two or three short sentences.
`;

  return callOpenAI({
    env,
    instructions,
    input: JSON.stringify({ top, bottom, shoes, watch, strap, feedback, previous }),
    schemaName: "outfit_completion",
    schema,
    useWebSearch: false,
    maxOutputTokens: 900,
  });
}

async function evaluateCollection(payload, env) {
  const mode = String(payload?.mode || "current").trim() === "wish" ? "wish" : "current";
  const currentWatches = Array.isArray(payload?.current_watches) ? payload.current_watches : [];
  const wishWatches = Array.isArray(payload?.wish_watches) ? payload.wish_watches : [];
  const releaseCandidates = Array.isArray(payload?.release_candidates) ? payload.release_candidates : [];
  if (!currentWatches.length && !wishWatches.length) throw new Error("평가할 시계가 없습니다.");

  const metricNames = ["역할 다양성", "구성 균형", "중복 관리", "실용성", "장기 완성도"];
  const schema = {
    type: "object",
    additionalProperties: false,
    properties: {
      overall_score: { type: "integer", minimum: 0, maximum: 100 },
      summary: { type: "string" },
      metrics: {
        type: "array",
        minItems: 5,
        maxItems: 5,
        items: {
          type: "object",
          additionalProperties: false,
          properties: {
            name: { type: "string", enum: metricNames },
            score: { type: "integer", minimum: 0, maximum: 100 },
            reason: { type: "string" },
          },
          required: ["name", "score", "reason"],
        },
      },
    },
    required: ["overall_score", "summary", "metrics"],
  };

  const instructions = `
You are a rigorous watch-collection evaluator. Use only the supplied data and do not browse the web.
Score the collection on exactly five criteria, each from 0 to 100: 역할 다양성, 구성 균형, 중복 관리, 실용성, 장기 완성도.
Return each criterion exactly once in that order. overall_score must be a defensible holistic score from 0 to 100, not a prestige or price ranking.
역할 다양성: coverage of dress, field, diver, GMT/travel, casual, digital, and other distinct use cases supported by the supplied facts.
구성 균형: balance of styles, sizes, colors, power types, and everyday versus special-purpose pieces.
중복 관리: penalize watches that occupy nearly identical roles without a clear distinction; reward purposeful overlap.
실용성: evaluate wearability and practical coverage using supplied dimensions, water resistance, power type, straps, and known specs. Do not assume missing facts.
장기 완성도: evaluate whether the collection has a coherent identity and a sensible long-term path.
For mode=current, evaluate current_watches as the actual collection.
For mode=wish, evaluate the projected collection: remove a current watch when a wish item declares replace_watch_id for it, remove every release candidate, then add all wish_watches. Also assess whether the replacement plan is coherent.
Mention uncertainty when important fields are missing. Write concise but specific Korean reasons grounded in named supplied watches or roles.
`;

  return callOpenAI({
    env,
    instructions,
    input: JSON.stringify({ mode, current_watches: currentWatches, wish_watches: wishWatches, release_candidates: releaseCandidates }),
    schemaName: "collection_evaluation",
    schema,
    useWebSearch: false,
    maxOutputTokens: 1700,
  });
}

async function callOpenAI({ env, instructions, input, schemaName, schema, useWebSearch, maxOutputTokens }) {
  const body = {
    model: env.OPENAI_MODEL || "gpt-6-luna",
    reasoning: { effort: "none" },
    instructions,
    input,
    max_output_tokens: maxOutputTokens,
    text: {
      format: {
        type: "json_schema",
        name: schemaName,
        strict: true,
        schema,
      },
    },
  };

  if (useWebSearch) {
    body.tools = [{ type: "web_search", search_context_size: "low" }];
    body.max_tool_calls = 2;
  }

  const serviceTier = String(env.OPENAI_SERVICE_TIER || "").trim();
  if (serviceTier === "fast" || serviceTier === "default") body.service_tier = serviceTier;

  const response = await fetch(OPENAI_RESPONSES_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.OPENAI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const result = await safeJson(response);
  if (!response.ok) {
    const code = result?.error?.code ? ` [${result.error.code}]` : "";
    const message = result?.error?.message || `OpenAI API 오류 HTTP ${response.status}`;
    throw new Error(`${message}${code}`);
  }

  const text = extractOutputText(result);
  if (!text) throw new Error("OpenAI 응답에서 JSON 결과를 찾지 못했습니다.");

  try {
    return JSON.parse(text);
  } catch {
    throw new Error("OpenAI 응답 JSON을 해석하지 못했습니다.");
  }
}

/**
 * 크레딧 잔여량은 공개된 Credit grants 잔액 API를 읽는 값이 아닙니다.
 * 사용자가 입력한 실제 지급액에서 공식 Costs API의 비용을 차감한 추정치입니다.
 */
async function getCreditStatus(env, { force = false } = {}) {
  const config = readCreditConfiguration(env);
  if (!config.configured) {
    const guardEnabled = isCreditGuardEnabled(env);
    return {
      configured: false,
      estimated: true,
      status: "configuration_required",
      missing: config.missing,
      guard_enabled: guardEnabled,
      calls_blocked: guardEnabled,
      spec_search_allowed: !guardEnabled,
      recommendation_allowed: !guardEnabled,
      message: `Worker 설정 누락: ${config.missing.join(", ")}`,
      updated_at: new Date().toISOString(),
    };
  }

  const cacheSeconds = clampNumber(numberEnv(env.OPENAI_USAGE_CACHE_SECONDS, 60), 0, 600);
  const cacheKey = [
    config.mode,
    config.cycleIndex,
    config.totalUsd,
    config.startMs,
    config.endExclusiveMs,
    config.recurringTotalUsd,
    config.projectId,
    config.reserveUsd,
    config.specReserveUsd,
    config.recommendReserveUsd,
    isCreditGuardEnabled(env),
  ].join("|");

  if (!force && creditStatusCache && creditStatusCache.key === cacheKey) {
    const ageMs = Date.now() - creditStatusCache.savedAt;
    if (ageMs <= cacheSeconds * 1000) return creditStatusCache.value;
  }

  const nowMs = Date.now();
  const guardEnabled = isCreditGuardEnabled(env);
  let usedUsd = 0;
  let latestCostBucketEndMs = null;

  if (nowMs > config.startMs) {
    const costEndMs = Math.min(nowMs, config.endExclusiveMs);
    if (costEndMs > config.startMs) {
      const costs = await fetchOpenAICosts(env, config, config.startMs, costEndMs);
      usedUsd = Math.max(0, costs.totalUsd);
      latestCostBucketEndMs = costs.latestBucketEndMs;
    }
  }

  const remainingUsd = Math.max(0, config.totalUsd - usedUsd);
  const remainingPercent = config.totalUsd > 0
    ? clampNumber((remainingUsd / config.totalUsd) * 100, 0, 100)
    : 0;

  let status = "active";
  if (nowMs < config.startMs) status = "not_started";
  else if (nowMs >= config.endExclusiveMs) status = "expired";
  else if (remainingUsd <= 0) status = "exhausted";
  else if (remainingUsd <= Math.max(config.specReserveUsd, config.recommendReserveUsd, config.reserveUsd)) status = "low";

  const active = status === "active" || status === "low";
  const specSearchAllowed = !guardEnabled || (active && remainingUsd > Math.max(config.reserveUsd, config.specReserveUsd));
  const recommendationAllowed = !guardEnabled || (active && remainingUsd > Math.max(config.reserveUsd, config.recommendReserveUsd));
  const callsBlocked = guardEnabled && !specSearchAllowed && !recommendationAllowed;
  const daysRemaining = Math.max(0, Math.ceil((config.endExclusiveMs - nowMs) / 86400000));

  const value = {
    configured: true,
    estimated: true,
    status,
    currency: "usd",
    total_usd: roundMoney(config.totalUsd),
    used_usd: roundMoney(usedUsd),
    remaining_usd: roundMoney(remainingUsd),
    remaining_percent: roundTo(remainingPercent, 2),
    reserve_usd: roundMoney(config.reserveUsd),
    spec_request_reserve_usd: roundMoney(config.specReserveUsd),
    recommend_request_reserve_usd: roundMoney(config.recommendReserveUsd),
    guard_enabled: guardEnabled,
    calls_blocked: callsBlocked,
    spec_search_allowed: specSearchAllowed,
    recommendation_allowed: recommendationAllowed,
    start_at: new Date(config.startMs).toISOString(),
    expires_at: new Date(config.displayExpiresMs).toISOString(),
    next_cycle_start_at: config.mode === "monthly_auto"
      ? new Date(config.endExclusiveMs).toISOString()
      : null,
    renewal_mode: config.mode,
    renewal_assumed: config.mode === "monthly_auto",
    cycle_index: config.cycleIndex,
    recurring_total_usd: config.recurringTotalUsd > 0
      ? roundMoney(config.recurringTotalUsd)
      : null,
    days_remaining: daysRemaining,
    scope: config.projectId ? "project" : "organization",
    project_filter_enabled: Boolean(config.projectId),
    costs_data_end_at: latestCostBucketEndMs ? new Date(latestCostBucketEndMs).toISOString() : null,
    updated_at: new Date().toISOString(),
    note: config.mode === "monthly_auto"
      ? "첫 지급분 정보와 정규 월 지급액을 기준으로 현재 지급 주기를 자동 계산한 추정치입니다. OpenAI Credit grants의 실제 갱신 여부·추가 일할 지급·요금제 변경은 공식 API로 확인할 수 없어 차이가 날 수 있습니다."
      : "설정한 Credit grants 지급액에서 OpenAI Costs API 비용을 차감한 예상 잔여량입니다. Credit grants 화면의 실제 잔액과 비용 집계 지연으로 차이가 날 수 있습니다.",
  };

  creditStatusCache = { key: cacheKey, savedAt: Date.now(), value };
  return value;
}

async function enforceCreditGuard(env, action) {
  if (!isCreditGuardEnabled(env)) return null;

  const status = await getCreditStatus(env, { force: false });
  if (!status.configured) {
    return {
      status: 503,
      body: {
        ok: false,
        code: "credit_guard_not_configured",
        error: "크레딧 보호 설정이 완료되지 않아 유료 API 호출을 차단했습니다.",
        usage: status,
      },
    };
  }

  const allowed = action === "spec-search"
    ? status.spec_search_allowed
    : status.recommendation_allowed;

  if (allowed) return null;

  const requestReserve = action === "spec-search"
    ? status.spec_request_reserve_usd
    : status.recommend_request_reserve_usd;

  return {
    status: 402,
    body: {
      ok: false,
      code: "credit_guard_blocked",
      error: `예상 잔여 크레딧이 호출 보호 기준($${formatMoney(requestReserve)}) 이하이거나 지급 기간이 종료되어 API 호출을 차단했습니다.`,
      usage: status,
    },
  };
}

async function fetchOpenAICosts(env, config, startMs, endMs) {
  if (!env.OPENAI_ADMIN_KEY) {
    throw new Error("OPENAI_ADMIN_KEY가 설정되지 않아 비용을 조회할 수 없습니다.");
  }

  let page = "";
  let totalUsd = 0;
  let latestBucketEndMs = null;
  let pageCount = 0;

  do {
    const params = new URLSearchParams();
    params.set("start_time", String(Math.floor(startMs / 1000)));
    params.set("end_time", String(Math.max(Math.floor(endMs / 1000), Math.floor(startMs / 1000) + 1)));
    params.set("bucket_width", "1d");
    params.set("limit", String(COSTS_PAGE_LIMIT));
    if (config.projectId) params.append("project_ids", config.projectId);
    if (page) params.set("page", page);

    const response = await fetch(`${OPENAI_COSTS_URL}?${params.toString()}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${env.OPENAI_ADMIN_KEY}`,
        "Content-Type": "application/json",
      },
    });

    const result = await safeJson(response);
    if (!response.ok) {
      const code = result?.error?.code ? ` [${result.error.code}]` : "";
      const message = result?.error?.message || `OpenAI Costs API 오류 HTTP ${response.status}`;
      throw new Error(`${message}${code}`);
    }

    for (const bucket of Array.isArray(result?.data) ? result.data : []) {
      if (Number.isFinite(Number(bucket?.end_time))) {
        const bucketEndMs = Number(bucket.end_time) * 1000;
        latestBucketEndMs = latestBucketEndMs == null
          ? bucketEndMs
          : Math.max(latestBucketEndMs, bucketEndMs);
      }
      for (const row of Array.isArray(bucket?.results) ? bucket.results : []) {
        const value = Number(row?.amount?.value);
        if (Number.isFinite(value)) totalUsd += value;
      }
    }

    page = result?.has_more && result?.next_page ? String(result.next_page) : "";
    pageCount += 1;
    if (pageCount > 50) throw new Error("OpenAI Costs API 페이지 수가 비정상적으로 많아 조회를 중단했습니다.");
  } while (page);

  return { totalUsd, latestBucketEndMs };
}

function readCreditConfiguration(env, nowMs = Date.now()) {
  const missing = [];
  const firstTotalUsd = Number(env.OPENAI_CREDIT_TOTAL_USD);
  if (!Number.isFinite(firstTotalUsd) || firstTotalUsd <= 0) {
    missing.push("OPENAI_CREDIT_TOTAL_USD");
  }

  const firstStartRaw = String(env.OPENAI_CREDIT_START_AT || "").trim();
  const firstStartMs = parseDateEnv(firstStartRaw, false);
  if (!Number.isFinite(firstStartMs)) missing.push("OPENAI_CREDIT_START_AT");

  const firstEnd = parseInclusiveEndEnv(
    env.OPENAI_CREDIT_EXPIRES_AT,
    parseOffsetMinutes(firstStartRaw) ?? 0,
  );
  if (!Number.isFinite(firstEnd.displayMs) || !Number.isFinite(firstEnd.endExclusiveMs)) {
    missing.push("OPENAI_CREDIT_EXPIRES_AT");
  }

  const recurringRaw = String(env.OPENAI_CREDIT_RECURRING_TOTAL_USD || "").trim();
  const recurringTotalUsd = recurringRaw ? Number(recurringRaw) : 0;
  if (recurringRaw && (!Number.isFinite(recurringTotalUsd) || recurringTotalUsd <= 0)) {
    missing.push("OPENAI_CREDIT_RECURRING_TOTAL_USD");
  }

  if (!env.OPENAI_ADMIN_KEY) missing.push("OPENAI_ADMIN_KEY");
  if (
    Number.isFinite(firstStartMs)
    && Number.isFinite(firstEnd.endExclusiveMs)
    && firstEnd.endExclusiveMs <= firstStartMs
  ) {
    missing.push("OPENAI_CREDIT_EXPIRES_AT(시작보다 이후여야 함)");
  }

  let mode = "fixed";
  let cycleIndex = 0;
  let totalUsd = Number.isFinite(firstTotalUsd) ? firstTotalUsd : 0;
  let startMs = firstStartMs;
  let endExclusiveMs = firstEnd.endExclusiveMs;
  let displayExpiresMs = firstEnd.displayMs;

  if (
    missing.length === 0
    && recurringTotalUsd > 0
    && nowMs >= firstEnd.endExclusiveMs
  ) {
    mode = "monthly_auto";

    // 첫 지급분 만료 직후가 정규 월 지급분의 기준 시작 시각입니다.
    // 입력된 ISO 8601 오프셋을 유지해 같은 현지 날짜·시각으로 매월 이동합니다.
    const offsetMinutes = firstEnd.offsetMinutes ?? parseOffsetMinutes(firstStartRaw) ?? 0;
    const recurringAnchor = makeCalendarAnchor(firstEnd.endExclusiveMs, offsetMinutes);
    const recurringCycle = resolveMonthlyCycle(recurringAnchor, nowMs);

    cycleIndex = recurringCycle.index + 1;
    totalUsd = recurringTotalUsd;
    startMs = recurringCycle.startMs;
    endExclusiveMs = recurringCycle.endExclusiveMs;
    displayExpiresMs = endExclusiveMs - 1;
  } else if (recurringTotalUsd > 0) {
    mode = "monthly_auto";
  }

  return {
    configured: missing.length === 0,
    missing,
    mode,
    cycleIndex,
    totalUsd,
    firstTotalUsd: Number.isFinite(firstTotalUsd) ? firstTotalUsd : 0,
    recurringTotalUsd: Number.isFinite(recurringTotalUsd) ? recurringTotalUsd : 0,
    startMs,
    endExclusiveMs,
    displayExpiresMs,
    reserveUsd: Math.max(0, numberEnv(env.OPENAI_CREDIT_RESERVE_USD, 0.00)),
    specReserveUsd: Math.max(0, numberEnv(env.OPENAI_SPEC_REQUEST_RESERVE_USD, 0.05)),
    recommendReserveUsd: Math.max(0, numberEnv(env.OPENAI_RECOMMEND_REQUEST_RESERVE_USD, 0.01)),
    projectId: String(env.OPENAI_PROJECT_ID || "").trim(),
  };
}

function hasCreditConfiguration(env) {
  return readCreditConfiguration(env).configured;
}

function isCreditGuardEnabled(env) {
  const raw = String(env.OPENAI_CREDIT_GUARD_ENABLED || "").trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(raw)) return true;
  if (["0", "false", "no", "off"].includes(raw)) return false;
  return hasCreditConfiguration(env);
}

function parseDateEnv(value, endOfDayForDateOnly) {
  const raw = String(value || "").trim();
  if (!raw) return Number.NaN;
  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(raw)
    ? `${raw}${endOfDayForDateOnly ? "T23:59:59.999Z" : "T00:00:00.000Z"}`
    : raw;
  const parsed = Date.parse(normalized);
  return Number.isFinite(parsed) ? parsed : Number.NaN;
}


/**
 * 만료 시각은 사용자가 입력한 마지막 유효 시각으로 해석합니다.
 * 초 단위 입력은 다음 초부터, 밀리초 입력은 다음 밀리초부터 새 주기로 넘깁니다.
 */
function parseInclusiveEndEnv(value, fallbackOffsetMinutes = 0) {
  const raw = String(value || "").trim();
  if (!raw) {
    return { displayMs: Number.NaN, endExclusiveMs: Number.NaN, offsetMinutes: null };
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [year, month, day] = raw.split("-").map(Number);
    const displayMs = Date.UTC(year, month - 1, day, 23, 59, 59, 999)
      - fallbackOffsetMinutes * 60000;
    return {
      displayMs,
      endExclusiveMs: Number.isFinite(displayMs) ? displayMs + 1 : Number.NaN,
      offsetMinutes: fallbackOffsetMinutes,
    };
  }

  const displayMs = Date.parse(raw);
  if (!Number.isFinite(displayMs)) {
    return { displayMs: Number.NaN, endExclusiveMs: Number.NaN, offsetMinutes: null };
  }

  const hasFractionalSeconds = /:\d{2}\.\d+(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw);
  const stepMs = hasFractionalSeconds ? 1 : 1000;
  return {
    displayMs,
    endExclusiveMs: displayMs + stepMs,
    offsetMinutes: parseOffsetMinutes(raw),
  };
}

function parseOffsetMinutes(value) {
  const raw = String(value || "").trim();
  if (!raw) return null;
  if (/Z$/i.test(raw)) return 0;

  const match = raw.match(/([+-])(\d{2}):?(\d{2})$/);
  if (!match) return null;

  const hours = Number(match[2]);
  const minutes = Number(match[3]);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes) || hours > 23 || minutes > 59) {
    return null;
  }

  const sign = match[1] === "-" ? -1 : 1;
  return sign * (hours * 60 + minutes);
}

function makeCalendarAnchor(ms, offsetMinutes) {
  const shifted = new Date(ms + offsetMinutes * 60000);
  return {
    ms,
    offsetMinutes,
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth(),
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
    second: shifted.getUTCSeconds(),
    millisecond: shifted.getUTCMilliseconds(),
  };
}

function addCalendarMonths(anchor, monthsToAdd) {
  const absoluteMonth = anchor.year * 12 + anchor.month + monthsToAdd;
  const year = Math.floor(absoluteMonth / 12);
  const month = ((absoluteMonth % 12) + 12) % 12;
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  const day = Math.min(anchor.day, lastDay);

  const shiftedMs = Date.UTC(
    year,
    month,
    day,
    anchor.hour,
    anchor.minute,
    anchor.second,
    anchor.millisecond,
  );
  return shiftedMs - anchor.offsetMinutes * 60000;
}

function resolveMonthlyCycle(anchor, nowMs) {
  const shiftedNow = new Date(nowMs + anchor.offsetMinutes * 60000);
  let index = (
    (shiftedNow.getUTCFullYear() - anchor.year) * 12
    + (shiftedNow.getUTCMonth() - anchor.month)
  );
  index = Math.max(0, index);

  while (index > 0 && addCalendarMonths(anchor, index) > nowMs) index -= 1;
  while (addCalendarMonths(anchor, index + 1) <= nowMs) index += 1;

  return {
    index,
    startMs: addCalendarMonths(anchor, index),
    endExclusiveMs: addCalendarMonths(anchor, index + 1),
  };
}

function numberEnv(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clampNumber(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function roundMoney(value) {
  return roundTo(value, 6);
}

function roundTo(value, digits) {
  const scale = 10 ** digits;
  return Math.round((Number(value) + Number.EPSILON) * scale) / scale;
}

function formatMoney(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : "0.00";
}

function invalidateCreditStatusCache() {
  creditStatusCache = null;
}

function extractOutputText(response) {
  if (typeof response?.output_text === "string" && response.output_text.trim()) {
    return response.output_text.trim();
  }
  for (const item of response?.output || []) {
    if (item?.type !== "message") continue;
    for (const content of item?.content || []) {
      if (content?.type === "output_text" && typeof content.text === "string") {
        return content.text.trim();
      }
    }
  }
  return "";
}

async function safeJson(response) {
  const text = await response.text();
  if (!text.trim()) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { error: { message: text.slice(0, 500) } };
  }
}

function readableError(error) {
  if (!error) return "알 수 없는 서버 오류입니다.";
  if (typeof error === "string") return error;
  return error.message || String(error);
}

function json(value, status, extraHeaders, additionalHeaders = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=UTF-8",
      ...extraHeaders,
      ...additionalHeaders,
    },
  });
}
