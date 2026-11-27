/**
 * SwaraPlay Web v10.0.0 — Zero-Dependency Node.js Server + EncDecEndpoints Resolver & HLS Proxy
 * Default Port: 8088 (override via PORT env var)
 *
 * Uses 100% EncDecEndpoints (https://enc-dec.app) for all movie, series, Asian drama, and anime streams:
 * VidLink, Cinejoy (api.wing.st/g), Videasy (api.wecollege.net), Nexa (providers.nexa.mov),
 * MeowTV (api.meowtv.ru), yFlix + Rapidshare, Hexa, AnimeKai + MegaUp, MegaPlay, KissKH.
 */

const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 8088);
const PUBLIC_DIR = path.join(__dirname, "public");
const ENC_DEC_API = "https://enc-dec.app/api";
const ENC_DEC_DB = "https://enc-dec.app/db";
const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
};

const cache = new Map();
function getCache(key) {
  const entry = cache.get(key);
  if (!entry) return null;
  if (Date.now() > entry.exp) {
    cache.delete(key);
    return null;
  }
  return entry.val;
}
function setCache(key, val, ttlMs = 10 * 60 * 1000) {
  cache.set(key, { val, exp: Date.now() + ttlMs });
}

function b64UrlEncode(buf) {
  return Buffer.from(buf).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function b64UrlDecode(str) {
  let s = String(str || "").trim().replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  return Buffer.from(s, "base64");
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 8000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        "User-Agent": UA,
        ...(options.headers || {}),
      },
    });
    return res;
  } finally {
    clearTimeout(timer);
  }
}

async function getJson(url, headers = {}, timeoutMs = 8000) {
  const res = await fetchWithTimeout(url, { headers }, timeoutMs);
  if (!res.ok) return null;
  return await res.json();
}

async function getText(url, headers = {}, timeoutMs = 8000) {
  const res = await fetchWithTimeout(url, { headers }, timeoutMs);
  if (!res.ok) return null;
  return await res.text();
}

async function postJson(url, bodyObj, headers = {}, timeoutMs = 8000) {
  const res = await fetchWithTimeout(
    url,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(bodyObj),
    },
    timeoutMs
  );
  if (!res.ok) return null;
  return await res.json();
}

const STRICT_DUB_RE = /\b(dub|dubbed|dubbing|hindi|tamil|telugu|latino|spanish|russian|french|german|italian|dublado)\b/i;

function extractMediaFromDecrypted(raw, id, name, referer, origin) {
  if (!raw) return null;
  const data = raw.result || raw.stream || raw.data || raw;
  let bestUrl = "";
  let bestScore = -999;
  let bestQuality = "HD";
  let subId = "";
  let subEn = "";

  const consider = (u, label = "") => {
    if (typeof u !== "string") return;
    const url = u.trim();
    if (!url.startsWith("http")) return;
    if (STRICT_DUB_RE.test(`${url} ${label}`)) return;
    const isDirect = /\.(m3u8|mp4)(\?|$)/i.test(url) || /(playlist|master|manifest)/i.test(url);
    if (!isDirect && /\/(e|embed)\//i.test(url)) return;
    const low = `${url} ${label}`.toLowerCase();
    let score = low.includes("1080") ? 65 : low.includes("720") ? 60 : low.includes("4k") || low.includes("2160") ? 58 : 50;
    if (low.includes(".m3u8")) score += 25;
    else if (low.includes(".mp4")) score += 20;
    if (score > bestScore) {
      bestScore = score;
      bestUrl = url;
      bestQuality = low.includes("4k") || low.includes("2160") ? "4K" : low.includes("1080") ? "1080p" : low.includes("720") ? "720p" : "HD";
    }
  };

  const considerSub = (u, lang = "") => {
    if (typeof u !== "string" || !u.startsWith("http")) return;
    const l = String(lang).toLowerCase();
    if (l.includes("indo") || l === "id" || l === "ind") {
      if (!subId) subId = u;
    } else if (l.includes("eng") || l === "en") {
      if (!subEn) subEn = u;
    }
  };

  for (const k of ["playlist", "url", "file", "stream", "hls", "src"]) {
    if (typeof data[k] === "string") consider(data[k]);
  }
  if (data.stream && typeof data.stream === "object") {
    for (const k of ["playlist", "url", "file", "hls"]) consider(data.stream[k]);
  }
  for (const arr of [data.sources, data.streams, data.servers, raw.sources]) {
    if (!Array.isArray(arr)) continue;
    for (const el of arr) {
      if (typeof el === "string") consider(el);
      else if (el && typeof el === "object") {
        const lbl = el.label || el.quality || el.name || "";
        for (const k of ["url", "file", "src", "link", "playlist"]) consider(el[k], lbl);
      }
    }
  }
  for (const arr of [data.subtitles, data.tracks, data.captions, raw.captions]) {
    if (!Array.isArray(arr)) continue;
    for (const s of arr) {
      if (!s || typeof s !== "object") continue;
      considerSub(s.file || s.url || s.src, s.label || s.lang || s.language);
    }
  }

  if (!bestUrl) return null;
  const proxyUrl = `/api/proxy?url=${encodeURIComponent(bestUrl)}&referer=${encodeURIComponent(referer)}&origin=${encodeURIComponent(origin)}`;
  const chosenSub = subId || subEn;
  const subtitleUrl = chosenSub ? `/api/sub?url=${encodeURIComponent(chosenSub)}` : "";
  return { id, name, quality: bestQuality, streamUrl: bestUrl, proxyUrl, referer, origin, subtitleUrl };
}

async function lookupFlixMapping(imdb, title, isMovie) {
  const typeParam = isMovie ? "movie" : "tv";
  const urls = [];
  if (imdb && imdb.startsWith("tt")) {
    urls.push(`${ENC_DEC_DB}/flix/find?imdb_id=${encodeURIComponent(imdb)}&type=${typeParam}`);
    urls.push(`${ENC_DEC_DB}/flix/find?imdb_id=${encodeURIComponent(imdb)}`);
  }
  if (title) {
    urls.push(`${ENC_DEC_DB}/flix/search?query=${encodeURIComponent(title)}&type=${typeParam}`);
  }
  for (const u of urls) {
    try {
      const json = await getJson(u, {}, 6000);
      const entry = Array.isArray(json) ? json[0] : json?.result?.[0] || json?.result || json;
      if (!entry) continue;
      const info = entry.info || entry;
      const tmdbId = String(info.tmdb_id || info.tmdbId || entry.tmdb_id || entry.tmdbId || "");
      const flixId = String(info.flix_id || info.flixId || entry.flix_id || entry.id || "");
      if (tmdbId || flixId) return { tmdbId, flixId, episodes: entry.episodes || null };
    } catch (_) {}
  }
  return null;
}

// --- EncDecEndpoints Providers ---

async function resolveVidLink(tmdbId, isMovie, season, episode) {
  const enc = await getJson(`${ENC_DEC_API}/enc-vidlink?text=${encodeURIComponent(tmdbId)}`);
  if (!enc || enc.status !== 200 || !enc.result) return null;
  const url = isMovie
    ? `https://vidlink.pro/api/b/movie/${enc.result}`
    : `https://vidlink.pro/api/b/tv/${enc.result}/${season}/${episode}`;
  const data = await getJson(url, { Origin: "https://vidlink.pro", Referer: "https://vidlink.pro/" });
  return extractMediaFromDecrypted(data, "vidlink", "VidLink", "https://vidlink.pro/", "https://vidlink.pro");
}

async function resolveCinejoy(title, year, imdb, tmdbId, isMovie, season, episode) {
  const h = { Accept: "*/*", Origin: "https://cinejoy.pk", Referer: "https://cinejoy.pk/" };
  const srvJson = await getJson("https://api.wing.st/servers", h);
  const servers = (srvJson?.servers || []).slice(0, 3);
  const type = isMovie ? "movie" : "series";
  for (const s of servers) {
    try {
      let target = `https://api.wing.st/?title=${encodeURIComponent(title)}&type=${type}&year=${year}&imdb=${encodeURIComponent(imdb)}&tmdb=${tmdbId}&server=${encodeURIComponent(s.name)}`;
      if (!isMovie) target += `&season=${season}&episode=${episode}`;
      const enc = await getJson(`${ENC_DEC_API}/enc-cinejoy?url=${encodeURIComponent(target)}`);
      if (!enc || enc.status !== 200 || !enc.result?.data) continue;
      const rawRes = await fetchWithTimeout("https://api.wing.st/g", {
        method: "POST",
        headers: h,
        body: b64UrlDecode(enc.result.data),
      });
      if (!rawRes.ok) continue;
      const buf = Buffer.from(await rawRes.arrayBuffer());
      const dec = await postJson(`${ENC_DEC_API}/dec-cinejoy`, { text: b64UrlEncode(buf), state: enc.result.state });
      const parsed = extractMediaFromDecrypted(dec, `cinejoy_${s.name}`, `Cinejoy (${s.name})`, "https://cinejoy.pk/", "https://cinejoy.pk");
      if (parsed) return parsed;
    } catch (_) {}
  }
  return null;
}

async function resolveVideasy(title, year, imdb, tmdbId, isMovie, season, episode) {
  const h = { Accept: "*/*", Origin: "https://vidy.st", Referer: "https://vidy.st/" };
  const seedJson = await getJson(`https://api.wecollege.net/seed?mediaId=${tmdbId}`, h);
  const seed = seedJson?.seed;
  if (!seed) return null;
  const encTitle = encodeURIComponent(encodeURIComponent(title));
  const type = isMovie ? "movie" : "tv";
  for (const srv of ["miami", "boise", "vegas", "seattle"]) {
    try {
      const url = isMovie
        ? `https://api.wecollege.net/${srv}/sources?title=${encTitle}&mediaType=movie&year=${year}&tmdbId=${tmdbId}&imdbId=${imdb}&enc=2&seed=${encodeURIComponent(seed)}`
        : `https://api.wecollege.net/${srv}/sources?title=${encTitle}&mediaType=${type}&year=${year}&episodeId=${episode}&seasonId=${season}&tmdbId=${tmdbId}&imdbId=${imdb}&enc=2&seed=${encodeURIComponent(seed)}`;
      const encData = await getText(url, h);
      if (!encData) continue;
      const dec = await postJson(`${ENC_DEC_API}/dec-videasy`, { text: encData, id: String(tmdbId), seed });
      const parsed = extractMediaFromDecrypted(dec, `videasy_${srv}`, `Videasy (${srv})`, "https://vidy.st/", "https://vidy.st");
      if (parsed) return parsed;
    } catch (_) {}
  }
  return null;
}

async function resolveNexa(idVal, platform, isMovie, season, episode) {
  const animeSet = new Set(["kyoto", "tokyo", "hokkaido", "fuji"]);
  let servers = [];
  if (platform === "anilist") {
    servers = ["kyoto", "tokyo", "hokkaido", "fuji"];
  } else {
    const st = await getJson("https://providers.nexa.mov/v1/status", { Referer: "https://nexa.mov/" });
    servers = (st?.sources || []).map((x) => x.codename).filter((c) => c && !animeSet.has(c.toLowerCase())).slice(0, 3);
  }
  for (const srv of servers) {
    try {
      const url = isMovie && platform !== "anilist"
        ? `https://providers.nexa.mov/?platform=${platform}&id=${idVal}&server=${srv}`
        : `https://providers.nexa.mov/?platform=${platform}&id=${idVal}&server=${srv}&season=${season}&episode=${episode}`;
      const enc = await getJson(`${ENC_DEC_API}/enc-nexa?url=${encodeURIComponent(url)}`);
      if (!enc || enc.status !== 200 || !enc.result) continue;
      const rawRes = await fetchWithTimeout("https://providers.nexa.mov/v1/playlist", {
        method: "POST",
        headers: { Referer: "https://nexa.mov/", "Content-Type": "application/octet-stream" },
        body: Buffer.from(enc.result, "base64"),
      });
      if (!rawRes.ok) continue;
      const hex = Buffer.from(await rawRes.arrayBuffer()).toString("hex");
      const dec = await postJson(`${ENC_DEC_API}/dec-nexa`, { text: hex, server: srv });
      const parsed = extractMediaFromDecrypted(dec, `nexa_${srv}`, `Nexa (${srv})`, "https://nexa.mov/", "https://nexa.mov");
      if (parsed) return parsed;
    } catch (_) {}
  }
  return null;
}

async function resolveMeowTv(tmdbId, isMovie, season, episode) {
  const h = { Origin: "https://meowtv.ru", Referer: "https://meowtv.ru/" };
  const tRes = await fetchWithTimeout("https://api.meowtv.ru/streams/ticket", { method: "POST", headers: h });
  if (!tRes.ok) return null;
  const ticket = (await tRes.json())?.ticket;
  if (!ticket) return null;
  for (const srv of ["english", "acme", "dcloud"]) {
    try {
      const url = isMovie
        ? `https://api.meowtv.ru/streams/movie/${tmdbId}?s=${srv}`
        : `https://api.meowtv.ru/streams/tv/${tmdbId}/${season}/${episode}?s=${srv}`;
      const data = await getJson(url, { ...h, "X-Stream-Ticket": ticket });
      if (!data) continue;
      const dec = await postJson(`${ENC_DEC_API}/dec-meowtv`, { data });
      const parsed = extractMediaFromDecrypted(dec, `meowtv_${srv}`, `MeowTV (${srv})`, "https://meowtv.ru/", "https://meowtv.ru");
      if (parsed) return parsed;
    } catch (_) {}
  }
  return null;
}

async function resolveYFlixRapid(mapping, season, episode) {
  if (!mapping) return null;
  const yh = { Referer: "https://yflix.to/", Accept: "application/json" };
  const eid = mapping.episodes?.[String(season)]?.[String(episode)]?.eid;
  if (!eid) return null;
  const encEid = (await getJson(`${ENC_DEC_API}/enc-movies-flix?text=${encodeURIComponent(eid)}`))?.result;
  if (!encEid) return null;
  const srvResp = await getJson(`https://yflix.to/ajax/links/list?eid=${eid}&_=${encEid}`, yh);
  const parsedSrvs = (await postJson(`${ENC_DEC_API}/parse-html`, { text: srvResp?.result || "" }))?.result;
  const lid = parsedSrvs?.default?.["1"]?.lid || parsedSrvs?.default?.["2"]?.lid;
  if (!lid) return null;
  const encLid = (await getJson(`${ENC_DEC_API}/enc-movies-flix?text=${encodeURIComponent(lid)}`))?.result;
  const viewResp = await getJson(`https://yflix.to/ajax/links/view?id=${lid}&_=${encLid}`, yh);
  const decFlix = (await postJson(`${ENC_DEC_API}/dec-movies-flix`, { text: viewResp?.result || "" }))?.result;
  const embed = decFlix?.url;
  if (!embed || !embed.includes("/e/")) return null;
  const ref = embed.split("/e/")[0] + "/";
  const mediaResp = await getJson(embed.replace("/e/", "/media/"), { Referer: ref, Accept: "application/json" });
  const decRapid = await postJson(`${ENC_DEC_API}/dec-rapid`, { text: mediaResp?.result || "", agent: UA });
  return extractMediaFromDecrypted(decRapid, "yflix", "yFlix (Rapid)", ref, ref.replace(/\/$/, ""));
}

async function resolveHexa(tmdbId, isMovie, season, episode) {
  const chH = { origin: "https://hexa.su", referer: "https://hexa.su/" };
  const chRes = await fetchWithTimeout("https://cap.hexa.su/15d2cf0395/challenge", { method: "POST", headers: chH });
  if (!chRes.ok) return null;
  const challenge = await chRes.json();
  const solved = (await postJson(`${ENC_DEC_API}/enc-hexa`, { challenge_data: challenge }))?.result;
  if (!solved) return null;
  const redeem = await postJson("https://cap.hexa.su/15d2cf0395/redeem", solved, chH);
  const token = redeem?.token;
  if (!token) return null;
  const key = crypto.randomBytes(32).toString("hex");
  const url = isMovie
    ? `https://theemoviedb.hexa.su/api/tmdb/movie/${tmdbId}/images`
    : `https://theemoviedb.hexa.su/api/tmdb/tv/${tmdbId}/season/${season}/episode/${episode}/images`;
  const encText = await getText(url, {
    Referer: "https://hexa.su/",
    Accept: "text/plain",
    "X-Fingerprint-Lite": "e9136c41504646444",
    "X-Cap-Token": token,
    "X-Api-Key": key,
  });
  if (!encText) return null;
  const dec = await postJson(`${ENC_DEC_API}/dec-hexa`, { text: encText, key });
  return extractMediaFromDecrypted(dec, "hexa", "Hexa", "https://hexa.su/", "https://hexa.su");
}

async function resolveAnimeKaiMegaUp(anilistId, malId, title, episode) {
  const findUrl = anilistId
    ? `${ENC_DEC_DB}/kai/find?anilist_id=${anilistId}`
    : malId
    ? `${ENC_DEC_DB}/kai/find?mal_id=${malId}`
    : `${ENC_DEC_DB}/kai/search?query=${encodeURIComponent(title)}`;
  const entries = await getJson(findUrl);
  const entry = Array.isArray(entries) ? entries[0] : entries?.result?.[0];
  if (!entry) return null;
  const mirror = entry.info?.mirrors?.megaup?.[0];
  const epObj = entry.episodes?.["1"]?.[String(episode)];
  if (mirror && epObj?.sources) {
    for (const t of ["softsub", "sub"]) {
      for (const s of ["server1", "server2"]) {
        const p = epObj.sources?.[t]?.[s];
        if (!p) continue;
        const full = p.startsWith("http") ? p : `${mirror.replace(/\/$/, "")}${p}`;
        const ref = full.includes("/e/") ? full.split("/e/")[0] + "/" : "https://megaup.site/";
        const mUrl = full.replace("/e/", "/media/");
        const mJson = await getJson(mUrl, { Referer: ref, Accept: "application/json" });
        if (!mJson?.result) continue;
        const dec = await postJson(`${ENC_DEC_API}/dec-mega`, { text: mJson.result, agent: UA });
        const parsed = extractMediaFromDecrypted(dec, "animekai", "AnimeKai (MegaUp)", ref, ref.replace(/\/$/, ""));
        if (parsed) return parsed;
      }
    }
  }
  return null;
}

async function resolveMegaPlay(anilistId, malId, episode) {
  const h = { Origin: "https://megaplay.buzz", Referer: "https://megaplay.buzz/", "X-Requested-With": "XMLHttpRequest" };
  const pageUrl = malId
    ? `https://megaplay.buzz/stream/mal/${malId}/${episode}/sub`
    : anilistId
    ? `https://megaplay.buzz/stream/ani/${anilistId}/${episode}/sub`
    : null;
  if (!pageUrl) return null;
  const html = await getText(pageUrl, h);
  const m = html && html.match(/<title>File (\d+) - MegaPlay<\/title>/);
  if (!m) return null;
  const src = await getJson(`https://megaplay.buzz/stream/getSources?id=${m[1]}&platform=OTHER`, h);
  if (!src?.enc) return null;
  const dec = await postJson(`${ENC_DEC_API}/dec-megaplay`, { text: src.enc });
  return extractMediaFromDecrypted(dec, "megaplay", "MegaPlay", "https://megaplay.buzz/", "https://megaplay.buzz");
}

async function resolveKissKh(title, episode) {
  const list = await getJson(`https://kisskh.do/api/DramaList/Search?q=${encodeURIComponent(title)}&type=0`, { Referer: "https://kisskh.do/" });
  if (!Array.isArray(list) || !list.length) return null;
  const detail = await getJson(`https://kisskh.do/api/DramaList/Drama/${list[0].id}?isq=false`, { Referer: "https://kisskh.do/" });
  const ep = (detail?.episodes || []).find((e) => Math.floor(Number(e.number)) === Number(episode));
  if (!ep?.id) return null;
  const vidKey = (await getJson(`${ENC_DEC_API}/enc-kisskh?text=${ep.id}&type=vid`))?.result;
  if (!vidKey) return null;
  const vJson = await getJson(`https://kisskh.do/api/DramaList/Episode/${ep.id}.png?err=false&ts=&time=&kkey=${encodeURIComponent(vidKey)}`, { Referer: "https://kisskh.do/" });
  if (!vJson?.Video) return null;
  let subtitleUrl = "";
  try {
    const subKey = (await getJson(`${ENC_DEC_API}/enc-kisskh?text=${ep.id}&type=sub`))?.result;
    const subs = subKey ? await getJson(`https://kisskh.do/api/Sub/${ep.id}?kkey=${encodeURIComponent(subKey)}`, { Referer: "https://kisskh.do/" }) : [];
    const chosen = (subs || []).find((s) => /indo|id/i.test(s.land || s.label || "")) || (subs || []).find((s) => /eng|en/i.test(s.land || s.label || ""));
    if (chosen?.src) {
      subtitleUrl = `/api/sub?url=${encodeURIComponent(`${ENC_DEC_API}/dec-kisskh?url=${encodeURIComponent(chosen.src)}`)}`;
    }
  } catch (_) {}
  return {
    id: "kisskh",
    name: "KissKH",
    quality: "HD",
    streamUrl: vJson.Video,
    proxyUrl: `/api/proxy?url=${encodeURIComponent(vJson.Video)}&referer=${encodeURIComponent("https://kisskh.do/")}&origin=${encodeURIComponent("https://kisskh.do")}`,
    referer: "https://kisskh.do/",
    origin: "https://kisskh.do",
    subtitleUrl,
  };
}

// --- Catalog Helpers (Cinemeta + AniList) ---

function mapCinemeta(m, cat) {
  return {
    id: m.imdb_id || m.id,
    imdbId: m.imdb_id || (String(m.id || "").startsWith("tt") ? m.id : ""),
    title: m.name || "Tanpa Judul",
    category: cat,
    poster: m.poster || `https://images.metahub.space/poster/medium/${m.id}/img`,
    backdrop: m.background || `https://images.metahub.space/background/medium/${m.id}/img`,
    year: String(m.releaseInfo || m.year || "").slice(0, 4),
    rating: String(m.imdbRating || ""),
    overview: m.description || "",
    genres: Array.isArray(m.genres) ? m.genres : [],
  };
}

async function fetchAniList(sort = "TRENDING_DESC", search = null) {
  const query = `query ($search: String, $sort: [MediaSort]) {
    Page(page: 1, perPage: 20) {
      media(type: ANIME, sort: $sort, search: $search, isAdult: false) {
        id idMal title { romaji english } coverImage { large extraLarge } bannerImage
        seasonYear averageScore description(asHtml: false) genres episodes
      }
    }
  }`;
  const res = await postJson("https://graphql.anilist.co", {
    query,
    variables: { sort: [sort], ...(search ? { search } : {}) },
  });
  return (res?.data?.Page?.media || []).map((a) => ({
    id: `ani_${a.id}`,
    aniListId: a.id,
    malId: a.idMal || null,
    imdbId: "",
    title: a.title?.english || a.title?.romaji || "Anime",
    category: "ANIME",
    poster: a.coverImage?.extraLarge || a.coverImage?.large || "",
    backdrop: a.bannerImage || a.coverImage?.extraLarge || "",
    year: String(a.seasonYear || ""),
    rating: a.averageScore ? (a.averageScore / 10).toFixed(1) : "",
    overview: String(a.description || "").replace(/<[^>]+>/g, ""),
    genres: a.genres || [],
    totalEpisodes: a.episodes || 12,
  }));
}

async function loadHomeCatalog() {
  const hit = getCache("home_catalog");
  if (hit) return hit;
  const [moviesJ, seriesJ, actionJ, animeTrend, animeTop] = await Promise.all([
    getJson("https://v3-cinemeta.strem.io/catalog/movie/top.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/series/top.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Action.json"),
    fetchAniList("TRENDING_DESC"),
    fetchAniList("SCORE_DESC"),
  ]);
  const movies = (moviesJ?.metas || []).slice(0, 40).map((m) => mapCinemeta(m, "MOVIE"));
  const series = (seriesJ?.metas || []).slice(0, 40).map((m) => mapCinemeta(m, "SERIES"));
  const action = (actionJ?.metas || []).slice(0, 24).map((m) => mapCinemeta(m, "MOVIE"));
  const dramas = [
    { id: "tt10919420", imdbId: "tt10919420", title: "Squid Game", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt10919420/img", backdrop: "https://images.metahub.space/background/medium/tt10919420/img", year: "2021", rating: "8.0", overview: "Ratusan pemain terlilit utang mengikuti permainan mematikan demi hadiah miliaran won.", genres: ["Thriller", "Drama"] },
    { id: "tt27668559", imdbId: "tt27668559", title: "Queen of Tears", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt27668559/img", backdrop: "https://images.metahub.space/background/medium/tt27668559/img", year: "2024", rating: "8.2", overview: "Kisah cinta penuh ujian antara pewaris konglomerat dan pengacara muda.", genres: ["Romance", "Drama"] },
    { id: "tt21344706", imdbId: "tt21344706", title: "The Glory", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt21344706/img", backdrop: "https://images.metahub.space/background/medium/tt21344706/img", year: "2022", rating: "8.1", overview: "Rencana balas dendam matang terhadap para perundung masa sekolah.", genres: ["Thriller", "Drama"] },
    { id: "tt10795658", imdbId: "tt10795658", title: "Alice in Borderland", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt10795658/img", backdrop: "https://images.metahub.space/background/medium/tt10795658/img", year: "2020", rating: "7.8", overview: "Arisu dan teman-temannya bertahan hidup lewat permainan maut di Tokyo kosong.", genres: ["Sci-Fi", "Thriller"] },
    { id: "tt24640580", imdbId: "tt24640580", title: "Moving", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt24640580/img", backdrop: "https://images.metahub.space/background/medium/tt24640580/img", year: "2023", rating: "8.4", overview: "Anak-anak berkekuatan super dan orang tua mereka bersembunyi dari agen rahasia.", genres: ["Action", "Sci-Fi"] },
    { id: "tt10850932", imdbId: "tt10850932", title: "Crash Landing on You", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt10850932/img", backdrop: "https://images.metahub.space/background/medium/tt10850932/img", year: "2019", rating: "8.7", overview: "Pewaris Korea Selatan tersesat di Korea Utara akibat kecelakaan paralayang.", genres: ["Romance", "Comedy"] },
    { id: "tt20859920", imdbId: "tt20859920", title: "Alchemy of Souls", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt20859920/img", backdrop: "https://images.metahub.space/background/medium/tt20859920/img", year: "2022", rating: "8.7", overview: "Penyihir tangguh yang jiwanya terperangkap dalam tubuh lemah bertemu bangsawan muda.", genres: ["Fantasy", "Romance"] },
    { id: "tt13433812", imdbId: "tt13433812", title: "Vincenzo", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt13433812/img", backdrop: "https://images.metahub.space/background/medium/tt13433812/img", year: "2021", rating: "8.4", overview: "Pengacara mafia Italia keturunan Korea menghadapi konglomerat korup.", genres: ["Crime", "Comedy"] },
  ];

  const payload = { movies, series, action, dramas, animeTrend, animeTop };
  setCache("home_catalog", payload, 10 * 60 * 1000);
  return payload;
}

function sendJson(res, status, obj) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
  });
  res.end(JSON.stringify(obj));
}

function srtToVtt(text) {
  if (!text) return "";
  if (text.trim().startsWith("WEBVTT")) return text;
  return "WEBVTT\n\n" + text.replace(/\r+/g, "").replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, "$1.$2");
}

const server = http.createServer(async (req, res) => {
  try {
    const parsedUrl = new URL(req.url, `http://localhost:${PORT}`);
    const p = parsedUrl.pathname;

    if (p === "/api/catalog/home") {
      const data = await loadHomeCatalog();
      return sendJson(res, 200, data);
    }

    if (p === "/api/search") {
      const q = (parsedUrl.searchParams.get("q") || "").trim();
      if (!q) return sendJson(res, 200, { results: [] });
      const [mRes, sRes, aRes] = await Promise.all([
        getJson(`https://v3-cinemeta.strem.io/catalog/movie/top/search=${encodeURIComponent(q)}.json`),
        getJson(`https://v3-cinemeta.strem.io/catalog/series/top/search=${encodeURIComponent(q)}.json`),
        fetchAniList("POPULARITY_DESC", q),
      ]);
      const results = [
        ...(mRes?.metas || []).slice(0, 14).map((m) => mapCinemeta(m, "MOVIE")),
        ...(sRes?.metas || []).slice(0, 14).map((m) => mapCinemeta(m, "SERIES")),
        ...(aRes || []).slice(0, 12),
      ];
      return sendJson(res, 200, { results });
    }

    if (p === "/api/detail") {
      const id = parsedUrl.searchParams.get("id") || "";
      const category = parsedUrl.searchParams.get("category") || "MOVIE";
      if (category === "ANIME") {
        const total = Number(parsedUrl.searchParams.get("totalEpisodes") || 12);
        const episodes = Array.from({ length: Math.min(Math.max(total, 1), 100) }, (_, i) => ({
          season: 1,
          episode: i + 1,
          title: `Episode ${i + 1}`,
        }));
        return sendJson(res, 200, { episodes });
      }
      const cType = category === "MOVIE" ? "movie" : "series";
      const det = await getJson(`https://v3-cinemeta.strem.io/meta/${cType}/${encodeURIComponent(id)}.json`);
      const videos = (det?.meta?.videos || [])
        .filter((v) => v.season > 0 && v.episode > 0)
        .map((v) => ({ season: v.season, episode: v.episode, title: v.name || v.title || `Episode ${v.episode}` }));
      return sendJson(res, 200, {
        overview: det?.meta?.description || "",
        episodes: videos,
      });
    }

    if (p === "/api/resolve") {
      const category = parsedUrl.searchParams.get("category") || "MOVIE";
      const title = parsedUrl.searchParams.get("title") || "";
      const year = parsedUrl.searchParams.get("year") || "2024";
      const imdb = parsedUrl.searchParams.get("imdbId") || "";
      const season = Number(parsedUrl.searchParams.get("season") || 1);
      const episode = Number(parsedUrl.searchParams.get("episode") || 1);
      const anilistId = parsedUrl.searchParams.get("aniListId") || "";
      const malId = parsedUrl.searchParams.get("malId") || "";

      const cacheKey = `res_${category}_${imdb}_${anilistId}_${season}_${episode}`;
      const cached = getCache(cacheKey);
      if (cached) return sendJson(res, 200, { servers: cached });

      let results = [];
      if (category === "ANIME") {
        const settled = await Promise.allSettled([
          resolveAnimeKaiMegaUp(anilistId, malId, title, episode),
          resolveMegaPlay(anilistId, malId, episode),
          anilistId ? resolveNexa(anilistId, "anilist", false, 1, episode) : Promise.resolve(null),
        ]);
        results = settled.filter((r) => r.status === "fulfilled" && r.value).map((r) => r.value);
      } else {
        const isMovie = category === "MOVIE";
        const mapping = await lookupFlixMapping(imdb, title, isMovie);
        const tmdbId = mapping?.tmdbId || "";
        const jobs = [];
        if (category === "ASIAN_DRAMA") jobs.push(resolveKissKh(title, episode));
        if (tmdbId) {
          jobs.push(resolveVidLink(tmdbId, isMovie, season, episode));
          jobs.push(resolveCinejoy(title, year, imdb, tmdbId, isMovie, season, episode));
          jobs.push(resolveVideasy(title, year, imdb, tmdbId, isMovie, season, episode));
          jobs.push(resolveNexa(tmdbId, "tmdb", isMovie, season, episode));
          jobs.push(resolveMeowTv(tmdbId, isMovie, season, episode));
          jobs.push(resolveHexa(tmdbId, isMovie, season, episode));
        }
        if (mapping) jobs.push(resolveYFlixRapid(mapping, season, episode));
        const settled = await Promise.allSettled(jobs);
        results = settled.filter((r) => r.status === "fulfilled" && r.value).map((r) => r.value);
      }

      const unique = [];
      const seen = new Set();
      for (const s of results) {
        if (!seen.has(s.streamUrl)) {
          seen.add(s.streamUrl);
          unique.push(s);
        }
      }
      if (unique.length) setCache(cacheKey, unique, 5 * 60 * 1000);
      return sendJson(res, 200, { servers: unique });
    }

    if (p === "/api/proxy") {
      const target = parsedUrl.searchParams.get("url");
      const referer = parsedUrl.searchParams.get("referer") || "";
      const origin = parsedUrl.searchParams.get("origin") || "";
      if (!target || !/^https?:\/\//i.test(target)) {
        res.writeHead(400);
        return res.end("Invalid URL");
      }
      const headers = {};
      if (referer) headers["Referer"] = referer;
      if (origin) headers["Origin"] = origin;
      if (req.headers.range) headers["Range"] = req.headers.range;

      const upstream = await fetchWithTimeout(target, { headers }, 15000);
      const ctype = upstream.headers.get("content-type") || "application/octet-stream";

      if (target.includes(".m3u8") || ctype.includes("mpegurl") || ctype.includes("x-mpegurl")) {
        const text = await upstream.text();
        const base = new URL(target);
        const rewritten = text
          .split(/\r?\n/)
          .map((line) => {
            const trimmed = line.trim();
            if (!trimmed) return line;
            if (trimmed.startsWith("#")) {
              return line.replace(/URI="([^"]+)"/g, (_, uri) => {
                const abs = new URL(uri, base).toString();
                return `URI="/api/proxy?url=${encodeURIComponent(abs)}&referer=${encodeURIComponent(referer)}&origin=${encodeURIComponent(origin)}"`;
              });
            }
            const abs = new URL(trimmed, base).toString();
            return `/api/proxy?url=${encodeURIComponent(abs)}&referer=${encodeURIComponent(referer)}&origin=${encodeURIComponent(origin)}`;
          })
          .join("\n");
        res.writeHead(200, {
          "Content-Type": "application/vnd.apple.mpegurl",
          "Access-Control-Allow-Origin": "*",
        });
        return res.end(rewritten);
      }

      const outHeaders = {
        "Content-Type": ctype,
        "Access-Control-Allow-Origin": "*",
      };
      if (upstream.headers.get("content-length")) outHeaders["Content-Length"] = upstream.headers.get("content-length");
      if (upstream.headers.get("content-range")) outHeaders["Content-Range"] = upstream.headers.get("content-range");
      if (upstream.headers.get("accept-ranges")) outHeaders["Accept-Ranges"] = upstream.headers.get("accept-ranges");

      res.writeHead(upstream.status, outHeaders);
      const arrBuf = await upstream.arrayBuffer();
      return res.end(Buffer.from(arrBuf));
    }

    if (p === "/api/sub") {
      const target = parsedUrl.searchParams.get("url");
      if (!target || !/^https?:\/\//i.test(target)) {
        res.writeHead(400);
        return res.end("");
      }
      const txt = await getText(target, {}, 8000);
      res.writeHead(200, {
        "Content-Type": "text/vtt; charset=utf-8",
        "Access-Control-Allow-Origin": "*",
      });
      return res.end(srtToVtt(txt || ""));
    }

    // Static files
    const safeRel = p === "/" ? "index.html" : p.replace(/^\/+/, "");
    const filePath = path.join(PUBLIC_DIR, path.normalize(safeRel));
    if (filePath.startsWith(PUBLIC_DIR) && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath).toLowerCase();
      res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream" });
      return fs.createReadStream(filePath).pipe(res);
    }

    res.writeHead(404);
    res.end("Not found");
  } catch (err) {
    sendJson(res, 500, { error: String(err?.message || err) });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`SwaraPlay Web v10.0.0 running on http://0.0.0.0:${PORT}`);
});
