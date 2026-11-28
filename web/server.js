/**
 * SwaraPlay Web v11.1.0 — Zero-Dependency Node.js Server + 100% EncDecEndpoints Instant Engine
 * Default Port: 8088 (override via PORT env var)
 *
 * Fitur Utama v11.0.0:
 * - 100% EncDecEndpoints (https://enc-dec.app): VidLink, Cinejoy, Videasy, Nexa, MeowTV,
 *   yFlix+Rapidshare, Hexa, VidFast, VidCore, VidUp, Byse/1hd.one, KissKH, OneTouchTV,
 *   AnimeKai+MegaUp, MegaPlay, Nexa Anime, + fallback hoster Abyss & FlixCloud
 * - First-Wins Instant Racing (/api/resolve?fast=1) + Background Full Server Discovery (/api/resolve/servers)
 * - Katalog Melimpah: Multi-halaman & multi-genre Cinemeta (Film/Serial), KissKH Dinamis (Drama Asia),
 *   dan 3-Lapis Anti-Kosong (AniList -> Jikan MAL -> enc-dec.app/db/kai) untuk Anime
 * - Pencarian Instan lintas semua katalog + Subtitle Bahasa Indonesia via Wyzie & provider
 * - Smart #EXTM3U content-sniffing HLS CORS Proxy + Streaming Pipe
 */

const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { URL } = require("url");
const { Readable } = require("stream");

const PORT = Number(process.env.PORT || 8088);
const PUBLIC_DIR = path.join(__dirname, "public");
const ENC_DEC_API = "https://enc-dec.app/api";
const ENC_DEC_DB = "https://enc-dec.app/db";

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";

// --- In-memory TTL cache (anti-empty safe) ---
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
function setCache(key, val, ttlMs) {
  if (val === null || val === undefined) return;
  if (Array.isArray(val) && val.length === 0) return;
  cache.set(key, { val, exp: Date.now() + ttlMs });
  if (cache.size > 1200) {
    const firstKey = cache.keys().next().value;
    cache.delete(firstKey);
  }
}

// --- HTTP Helpers ---
async function fetchWithTimeout(url, options = {}, timeoutMs = 8500) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, {
      ...options,
      redirect: options.redirect || "follow",
      signal: controller.signal,
      headers: {
        "User-Agent": UA,
        Accept: "*/*",
        ...(options.headers || {}),
      },
    });
  } finally {
    clearTimeout(timer);
  }
}

async function getJson(url, headers = {}, timeoutMs = 8500) {
  try {
    const res = await fetchWithTimeout(url, { method: "GET", headers }, timeoutMs);
    if (!res.ok) return null;
    const txt = await res.text();
    try {
      return JSON.parse(txt);
    } catch {
      return null;
    }
  } catch {
    return null;
  }
}

async function getText(url, headers = {}, timeoutMs = 8500) {
  try {
    const res = await fetchWithTimeout(url, { method: "GET", headers }, timeoutMs);
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

async function postJson(url, bodyObj, headers = {}, timeoutMs = 8500) {
  try {
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
    const txt = await res.text();
    try {
      return JSON.parse(txt);
    } catch {
      return null;
    }
  } catch {
    return null;
  }
}

function b64UrlDecodeToBuffer(str) {
  let s = String(str || "").replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4 !== 0) s += "=";
  return Buffer.from(s, "base64");
}

function b64UrlEncode(buf) {
  return Buffer.from(buf).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// --- Parse Decrypted Stream JSON ---
function extractStreamFromJson(raw, id, name, referer, origin) {
  if (!raw) return null;
  const data = raw.result || raw.stream || raw.data || raw;
  const candidates = [];

  const considerUrl = (u, label = "") => {
    if (!u || typeof u !== "string") return;
    const url = u.trim();
    if (!/^https?:\/\//i.test(url)) return;
    if (/\/(e|embed)\//i.test(url) && !/\.(m3u8|mp4)(\?|$)/i.test(url)) return;
    if (/\.(srt|vtt|jpg|jpeg|png|webp|gif|svg|css|js)(\?|$)/i.test(url)) return;
    const low = `${url} ${label}`.toLowerCase();
    let score = 10;
    if (low.includes(".m3u8")) score += 30;
    else if (low.includes(".mp4")) score += 22;
    else if (/(playlist|master|manifest|hls|stream|video|cdn)/i.test(low)) score += 15;
    if (low.includes("2160") || low.includes("4k")) score += 12;
    else if (low.includes("1080")) score += 9;
    else if (low.includes("720")) score += 6;
    candidates.push({
      url,
      score,
      quality: low.includes("4k") || low.includes("2160")
        ? "4K"
        : low.includes("1080")
        ? "1080p"
        : low.includes("720")
        ? "720p"
        : "HD",
    });
  };

  const walk = (node, depth = 0) => {
    if (!node || depth > 5) return;
    if (typeof node === "string") {
      if (/^https?:\/\//i.test(node)) considerUrl(node);
      return;
    }
    if (Array.isArray(node)) {
      for (const item of node) walk(item, depth + 1);
      return;
    }
    if (typeof node === "object") {
      const label = String(node.quality || node.label || node.resolution || node.name || "");
      for (const key of ["playlist", "file", "url", "stream", "src", "link", "hls", "video", "Video"]) {
        if (typeof node[key] === "string") considerUrl(node[key], label);
      }
      for (const key of ["sources", "streams", "links", "files", "qualities", "data", "result", "stream"]) {
        if (node[key] && typeof node[key] === "object") walk(node[key], depth + 1);
      }
    }
  };

  walk(data);
  if (!candidates.length) return null;
  candidates.sort((a, b) => b.score - a.score);
  const best = candidates[0];

  let subtitleUrl = "";
  const subTracks = [];
  const collectSubs = (arr) => {
    if (!Array.isArray(arr)) return;
    for (const t of arr) {
      if (!t || typeof t !== "object") continue;
      const sUrl = t.url || t.file || t.src || "";
      if (!/^https?:\/\//i.test(sUrl)) continue;
      const lang = String(t.language || t.lang || t.label || "Subtitle");
      subTracks.push({
        label: lang,
        lang: lang.toLowerCase(),
        url: `/api/sub?url=${encodeURIComponent(sUrl)}`,
      });
    }
  };
  collectSubs(data.captions || data.subtitles || data.tracks);
  if (raw.stream && typeof raw.stream === "object") collectSubs(raw.stream.captions || raw.stream.subtitles);
  const indoSub = subTracks.find((s) => s.lang.includes("indo") || s.lang === "id");
  const enSub = subTracks.find((s) => s.lang.includes("eng") || s.lang === "en");
  subtitleUrl = (indoSub || enSub || subTracks[0] || {}).url || "";

  const effectiveRef = data.referer || referer || "";
  const effectiveOrigin = origin || (effectiveRef ? new URL(effectiveRef).origin : "");

  return {
    id,
    name,
    quality: best.quality,
    streamUrl: best.url,
    proxyUrl: `/api/proxy?url=${encodeURIComponent(best.url)}&referer=${encodeURIComponent(effectiveRef)}&origin=${encodeURIComponent(effectiveOrigin)}`,
    referer: effectiveRef,
    origin: effectiveOrigin,
    subtitleUrl,
    subtitles: subTracks.slice(0, 12),
  };
}

// --- Multi-source ID Resolution (IMDb <-> TMDB / Flix / Kai) ---
async function resolveFlixMapping(imdbId, tmdbId, title, isMovie) {
  const key = `flix_map_${imdbId}_${tmdbId}_${title}_${isMovie}`;
  const hit = getCache(key);
  if (hit) return hit;

  const typeStr = isMovie ? "movie" : "tv";
  let result = { flixId: "", tmdbId: String(tmdbId || ""), imdbId: String(imdbId || "") };

  const tryParseDb = (dbJson) => {
    if (!dbJson) return;
    const list = Array.isArray(dbJson) ? dbJson : Array.isArray(dbJson.result) ? dbJson.result : [dbJson.result || dbJson];
    for (const entry of list) {
      if (!entry || typeof entry !== "object") continue;
      const info = entry.info || entry;
      if (!result.flixId && (info.flix_id || info.id)) result.flixId = String(info.flix_id || info.id);
      if (!result.tmdbId && info.tmdb_id) result.tmdbId = String(info.tmdb_id);
      if (!result.imdbId && info.imdb_id) result.imdbId = String(info.imdb_id);
      if (result.tmdbId && result.flixId) break;
    }
  };

  if (tmdbId) {
    tryParseDb(await getJson(`${ENC_DEC_DB}/flix/find?tmdb_id=${encodeURIComponent(tmdbId)}&type=${typeStr}`, {}, 5500));
  }
  if ((!result.tmdbId || !result.flixId) && imdbId && imdbId.startsWith("tt")) {
    tryParseDb(await getJson(`${ENC_DEC_DB}/flix/find?imdb_id=${encodeURIComponent(imdbId)}&type=${typeStr}`, {}, 5500));
  }
  if ((!result.tmdbId || !result.flixId) && title) {
    tryParseDb(await getJson(`${ENC_DEC_DB}/flix/search?query=${encodeURIComponent(title)}&type=${typeStr}`, {}, 5500));
  }
  if (!result.tmdbId && imdbId && imdbId.startsWith("tt")) {
    const metaType = isMovie ? "movie" : "series";
    const metaJ = await getJson(`https://v3-cinemeta.strem.io/meta/${metaType}/${encodeURIComponent(imdbId)}.json`, {}, 5500);
    const moviedbId = metaJ?.meta?.moviedb_id;
    if (moviedbId) result.tmdbId = String(moviedbId);
  }

  if (result.tmdbId || result.flixId) setCache(key, result, 6 * 60 * 60 * 1000);
  return result;
}

// =========================================================================
// 100% ENCDECENDPOINTS PROVIDERS (https://enc-dec.app)
// =========================================================================

// 1. VidLink (enc-vidlink)
async function resolveVidLink(tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const enc = await getJson(`${ENC_DEC_API}/enc-vidlink?text=${encodeURIComponent(tmdbId)}`, {}, 6000);
  const encId = enc?.result;
  if (!encId) return null;
  const url = isMovie
    ? `https://vidlink.pro/api/b/movie/${encId}`
    : `https://vidlink.pro/api/b/tv/${encId}/${season}/${episode}`;
  const json = await getJson(url, { Origin: "https://vidlink.pro", Referer: "https://vidlink.pro/" }, 7500);
  return extractStreamFromJson(json, "vidlink", "VidLink", "https://vidlink.pro/", "https://vidlink.pro");
}

// 2. Cinejoy (api.wing.st/g + enc-cinejoy / dec-cinejoy)
async function resolveCinejoy(title, year, imdb, tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const headers = { Origin: "https://cinejoy.pk", Referer: "https://cinejoy.pk/" };
  let servers = getCache("cinejoy_servers");
  if (!servers) {
    const srvJ = await getJson("https://api.wing.st/servers", headers, 5000);
    servers = Array.isArray(srvJ?.servers) ? srvJ.servers : Array.isArray(srvJ) ? srvJ : ["Lisbon", "Tokyo", "Berlin"];
    setCache("cinejoy_servers", servers, 30 * 60 * 1000);
  }
  const type = isMovie ? "movie" : "series";
  const tries = servers.slice(0, 4).map(async (srv) => {
    const srvName = typeof srv === "string" ? srv : srv.name || srv.id;
    if (!srvName) return null;
    let target = `https://api.wing.st/?title=${encodeURIComponent(title || "")}&type=${type}&year=${encodeURIComponent(year || "")}&imdb=${encodeURIComponent(imdb || "")}&tmdb=${encodeURIComponent(tmdbId)}&server=${encodeURIComponent(srvName)}`;
    if (!isMovie) target += `&season=${season}&episode=${episode}`;
    const encJ = await getJson(`${ENC_DEC_API}/enc-cinejoy?url=${encodeURIComponent(target)}`, {}, 5500);
    const encData = encJ?.result;
    if (!encData) return null;
    const res = await fetchWithTimeout(
      "https://api.wing.st/g",
      { method: "POST", headers: { ...headers, "Content-Type": "application/octet-stream" }, body: b64UrlDecodeToBuffer(encData) },
      6500
    );
    if (!res.ok) return null;
    const rawBuf = Buffer.from(await res.arrayBuffer());
    const decJ = await postJson(`${ENC_DEC_API}/dec-cinejoy`, { text: b64UrlEncode(rawBuf) }, {}, 5500);
    return extractStreamFromJson(decJ, `cinejoy_${srvName}`, `Cinejoy (${srvName})`, "https://cinejoy.pk/", "https://cinejoy.pk");
  });
  const results = (await Promise.allSettled(tries))
    .filter((r) => r.status === "fulfilled" && r.value)
    .map((r) => r.value);
  return results;
}

// 3. Videasy (api.wecollege.net + dec-videasy)
async function resolveVideasy(title, year, imdb, tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const headers = { Origin: "https://vidy.st", Referer: "https://vidy.st/" };
  const seedJ = await getJson(`https://api.wecollege.net/seed?mediaId=${encodeURIComponent(tmdbId)}`, headers, 5500);
  const seed = seedJ?.seed || seedJ?.result;
  if (!seed) return null;
  const dblTitle = encodeURIComponent(encodeURIComponent(title || ""));
  const srvList = ["miami", "dallas", "atlanta", "paris"];
  const tries = srvList.map(async (srv) => {
    const url = isMovie
      ? `https://api.wecollege.net/${srv}/sources?title=${dblTitle}&mediaType=movie&year=${encodeURIComponent(year || "")}&tmdbId=${tmdbId}&imdbId=${encodeURIComponent(imdb || "")}&enc=2&seed=${encodeURIComponent(seed)}`
      : `https://api.wecollege.net/${srv}/sources?title=${dblTitle}&mediaType=tv&year=${encodeURIComponent(year || "")}&episodeId=${episode}&seasonId=${season}&tmdbId=${tmdbId}&imdbId=${encodeURIComponent(imdb || "")}&enc=2&seed=${encodeURIComponent(seed)}`;
    const encTxt = await getText(url, headers, 6000);
    if (!encTxt || encTxt.length < 16) return null;
    const decJ = await postJson(`${ENC_DEC_API}/dec-videasy`, { text: encTxt.trim(), id: String(tmdbId) }, {}, 5500);
    return extractStreamFromJson(decJ, `videasy_${srv}`, `Videasy (${srv[0].toUpperCase() + srv.slice(1)})`, "https://vidy.st/", "https://vidy.st");
  });
  return (await Promise.allSettled(tries)).filter((r) => r.status === "fulfilled" && r.value).map((r) => r.value);
}

// 4. Nexa (providers.nexa.mov + enc-nexa / dec-nexa)
async function resolveNexa(idVal, platform, isMovie, season, episode) {
  if (!idVal) return null;
  const headers = { Referer: "https://nexa.mov/" };
  let srvs = getCache("nexa_servers");
  if (!srvs) {
    const st = await getJson("https://providers.nexa.mov/v1/status", headers, 5000);
    srvs = st && typeof st === "object" ? Object.keys(st).filter((k) => st[k]?.online !== false).slice(0, 4) : ["nova", "orion", "vega"];
    if (!srvs.length) srvs = ["nova", "orion", "vega"];
    setCache("nexa_servers", srvs, 20 * 60 * 1000);
  }
  const tries = srvs.slice(0, 3).map(async (srv) => {
    const target = isMovie && platform === "tmdb"
      ? `https://providers.nexa.mov/?platform=tmdb&id=${idVal}&server=${srv}`
      : `https://providers.nexa.mov/?platform=${platform}&id=${idVal}&server=${srv}&season=${season}&episode=${episode}`;
    const encJ = await getJson(`${ENC_DEC_API}/enc-nexa?url=${encodeURIComponent(target)}`, {}, 5500);
    const encData = encJ?.result;
    if (!encData) return null;
    const res = await fetchWithTimeout(
      "https://providers.nexa.mov/v1/playlist",
      { method: "POST", headers: { ...headers, "Content-Type": "application/octet-stream" }, body: b64UrlDecodeToBuffer(encData) },
      6500
    );
    if (!res.ok) return null;
    const buf = Buffer.from(await res.arrayBuffer());
    const decJ = await postJson(`${ENC_DEC_API}/dec-nexa`, { text: b64UrlEncode(buf) }, {}, 5500);
    return extractStreamFromJson(decJ, `nexa_${platform}_${srv}`, `Nexa (${srv})`, "https://nexa.mov/", "https://nexa.mov");
  });
  return (await Promise.allSettled(tries)).filter((r) => r.status === "fulfilled" && r.value).map((r) => r.value);
}

// 5. MeowTV (api.meowtv.ru + dec-meowtv)
async function resolveMeowTv(tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const baseHeaders = { Origin: "https://meowtv.ru", Referer: "https://meowtv.ru/" };
  const tickRes = await fetchWithTimeout("https://api.meowtv.ru/streams/ticket", { method: "POST", headers: baseHeaders }, 5500).catch(() => null);
  if (!tickRes || !tickRes.ok) return null;
  const tickJ = await tickRes.json().catch(() => null);
  const ticket = tickJ?.ticket || tickJ?.token;
  if (!ticket) return null;
  const reqHeaders = { ...baseHeaders, "X-Stream-Ticket": ticket };
  const tries = ["1", "2", "3"].map(async (srv) => {
    const url = isMovie
      ? `https://api.meowtv.ru/streams/movie/${tmdbId}?s=${srv}`
      : `https://api.meowtv.ru/streams/tv/${tmdbId}/${season}/${episode}?s=${srv}`;
    const encTxt = await getText(url, reqHeaders, 6000);
    if (!encTxt || encTxt.length < 16) return null;
    const decJ = await postJson(`${ENC_DEC_API}/dec-meowtv`, { text: encTxt.trim() }, {}, 5500);
    return extractStreamFromJson(decJ, `meowtv_${srv}`, `MeowTV #${srv}`, "https://meowtv.ru/", "https://meowtv.ru");
  });
  return (await Promise.allSettled(tries)).filter((r) => r.status === "fulfilled" && r.value).map((r) => r.value);
}

// 6. Hexa (cap.hexa.su + enc-hexa / dec-hexa)
async function resolveHexa(tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const capHeaders = { origin: "https://hexa.su", referer: "https://hexa.su/" };
  const chRes = await fetchWithTimeout("https://cap.hexa.su/15d2cf0395/challenge", { method: "POST", headers: capHeaders }, 5500).catch(() => null);
  if (!chRes || !chRes.ok) return null;
  const challenge = await chRes.json().catch(() => null);
  if (!challenge) return null;
  const solved = await postJson(`${ENC_DEC_API}/enc-hexa`, { challenge_data: challenge }, {}, 5500);
  if (!solved?.result) return null;
  const redeem = await postJson("https://cap.hexa.su/15d2cf0395/redeem", solved.result, capHeaders, 5500);
  const token = redeem?.token;
  if (!token) return null;
  const key = crypto.randomBytes(32).toString("hex");
  const reqHeaders = {
    Referer: "https://hexa.su/",
    Accept: "text/plain",
    "X-Api-Key": key,
    "X-Cap-Token": token,
    "X-Fingerprint-Lite": "e9136c41504646444",
  };
  const url = isMovie
    ? `https://theemoviedb.hexa.su/api/tmdb/movie/${tmdbId}/images`
    : `https://theemoviedb.hexa.su/api/tmdb/tv/${tmdbId}/season/${season}/episode/${episode}/images`;
  const encTxt = await getText(url, reqHeaders, 6500);
  if (!encTxt) return null;
  const decJ = await postJson(`${ENC_DEC_API}/dec-hexa`, { text: encTxt.trim(), key }, {}, 5500);
  return extractStreamFromJson(decJ, "hexa", "Hexa", "https://hexa.su/", "https://hexa.su");
}

// 7. VidFast / VidCore / VidUp (Stage 1 -> Stage 2 EncDec)
async function resolveVidStageProvider(slug, label, baseUrl, tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const pageUrl = isMovie ? `${baseUrl}/movie/${tmdbId}` : `${baseUrl}/tv/${tmdbId}/${season}/${episode}`;
  const html = await getText(pageUrl, { Referer: `${baseUrl}/` }, 6500);
  if (!html) return null;
  const m = html.match(/\\"en\\":\\"([^"\\]+)\\"/) || html.match(/"en"\s*:\s*"([^"]+)"/);
  if (!m || !m[1]) return null;
  const s1 = await getJson(`${ENC_DEC_API}/enc-${slug}?text=${encodeURIComponent(m[1])}&stage=1`, {}, 5500);
  const srvPath = s1?.result;
  if (!srvPath) return null;
  const srvUrl = srvPath.startsWith("http") ? srvPath : `${baseUrl}${srvPath.startsWith("/") ? "" : "/"}${srvPath}`;
  const srvTxt = await getText(srvUrl, { Referer: pageUrl, Origin: baseUrl }, 6500);
  if (!srvTxt) return null;
  const decServers = await postJson(`${ENC_DEC_API}/dec-${slug}`, { text: srvTxt.trim() }, {}, 5500);
  const list = Array.isArray(decServers?.result) ? decServers.result : [];
  for (const s of list.slice(0, 2)) {
    const dataToken = s?.data || s?.id;
    if (!dataToken) continue;
    const s2 = await getJson(`${ENC_DEC_API}/enc-${slug}?text=${encodeURIComponent(dataToken)}&stage=2`, {}, 5500);
    const streamPath = s2?.result;
    if (!streamPath) continue;
    const stUrl = streamPath.startsWith("http") ? streamPath : `${baseUrl}${streamPath.startsWith("/") ? "" : "/"}${streamPath}`;
    const stTxt = await getText(stUrl, { Referer: pageUrl, Origin: baseUrl }, 6500);
    if (!stTxt) continue;
    const decStream = await postJson(`${ENC_DEC_API}/dec-${slug}`, { text: stTxt.trim() }, {}, 5500);
    const parsed = extractStreamFromJson(decStream, slug, `${label}${s.name ? ` (${s.name})` : ""}`, `${baseUrl}/`, baseUrl);
    if (parsed) return parsed;
  }
  return null;
}

// 8. yFlix + Rapidshare (plus fallback Abyss & FlixCloud hosters)
async function decryptHosterUrl(embedUrl, labelPrefix) {
  if (!embedUrl) return null;
  if (/rapidshare|rapidairmax/i.test(embedUrl)) {
    const mediaUrl = embedUrl.replace("/e/", "/media/");
    const mJ = await getJson(mediaUrl, { Referer: embedUrl }, 6500);
    if (!mJ?.result) return null;
    const dec = await postJson(`${ENC_DEC_API}/dec-rapid`, { text: mJ.result, agent: UA }, {}, 5500);
    return extractStreamFromJson(dec, "yflix_rapid", `${labelPrefix} (Rapid)`, "https://rapidshare.cc/", "https://rapidshare.cc");
  }
  if (/megaup|4spromax/i.test(embedUrl)) {
    const mediaUrl = embedUrl.replace("/e/", "/media/");
    const mJ = await getJson(mediaUrl, { Referer: embedUrl }, 6500);
    if (!mJ?.result) return null;
    const dec = await postJson(`${ENC_DEC_API}/dec-mega`, { text: mJ.result, agent: UA }, {}, 5500);
    return extractStreamFromJson(dec, "animekai_mega", `${labelPrefix} (MegaUp)`, "https://megaup.site/", "https://megaup.site");
  }
  if (/playhydrax|abyss\.to/i.test(embedUrl)) {
    const html = await getText(embedUrl, { Referer: "https://playhydrax.com/" }, 6500);
    const m = html && html.match(/const\s+datas\s*=\s*"([^"]+)"/);
    if (!m) return null;
    const dec = await postJson(`${ENC_DEC_API}/dec-abyss`, { text: m[1] }, {}, 5500);
    return extractStreamFromJson(dec, "abyss", `${labelPrefix} (Abyss)`, "https://playhydrax.com/", "https://playhydrax.com");
  }
  if (/flixcloud\.cc/i.test(embedUrl)) {
    const html = await getText(embedUrl, { Referer: "https://flixcloud.cc/" }, 6500);
    const m = html && html.match(/type:\s*"data",\s*data:\s*(\{[\s\S]*?\})\s*,\s*uses:/);
    if (!m) return null;
    try {
      const decTok = await postJson(`${ENC_DEC_API}/dec-flixcloud?type=token`, { data: JSON.parse(m[1]) }, {}, 5500);
      if (!decTok?.result?.token) return null;
      const stJ = await getJson(`https://flixcloud.cc/api/m3u8/${decTok.result.token}`, { Referer: "https://flixcloud.cc/" }, 6000);
      const decSt = await postJson(`${ENC_DEC_API}/dec-flixcloud?type=stream`, { data: { context: decTok.result.context, stream: stJ } }, {}, 5500);
      return extractStreamFromJson(decSt, "flixcloud", `${labelPrefix} (FlixCloud)`, "https://flixcloud.cc/", "https://flixcloud.cc");
    } catch {
      return null;
    }
  }
  return null;
}

async function resolveYFlixRapid(mapping, season, episode) {
  if (!mapping?.flixId) return null;
  const yHeaders = { Referer: "https://yflix.to/", Accept: "application/json" };
  const encCid = (await getJson(`${ENC_DEC_API}/enc-movies-flix?text=${encodeURIComponent(mapping.flixId)}`, {}, 5000))?.result;
  if (!encCid) return null;
  const epJ = await getJson(`https://yflix.to/ajax/episodes/list?id=${mapping.flixId}&_=${encCid}`, yHeaders, 6000);
  if (!epJ?.result) return null;
  const parsedEp = await postJson(`${ENC_DEC_API}/parse-html`, { text: epJ.result }, {}, 5000);
  const epMap = parsedEp?.result || {};
  const sObj = epMap[String(season)] || epMap["1"] || Object.values(epMap)[0];
  const epEntry = sObj?.[String(episode)] || sObj?.["1"] || (sObj && Object.values(sObj)[0]);
  const eid = epEntry?.eid || epEntry?.id;
  if (!eid) return null;
  const encEid = (await getJson(`${ENC_DEC_API}/enc-movies-flix?text=${encodeURIComponent(eid)}`, {}, 5000))?.result;
  if (!encEid) return null;
  const srvJ = await getJson(`https://yflix.to/ajax/links/list?eid=${eid}&_=${encEid}`, yHeaders, 6000);
  if (!srvJ?.result) return null;
  const parsedSrv = await postJson(`${ENC_DEC_API}/parse-html`, { text: srvJ.result }, {}, 5000);
  const srvMap = parsedSrv?.result?.default || parsedSrv?.result || {};
  const firstSrv = Object.values(srvMap)[0];
  const lid = firstSrv?.lid || firstSrv?.id;
  if (!lid) return null;
  const encLid = (await getJson(`${ENC_DEC_API}/enc-movies-flix?text=${encodeURIComponent(lid)}`, {}, 5000))?.result;
  if (!encLid) return null;
  const viewJ = await getJson(`https://yflix.to/ajax/links/view?id=${lid}&_=${encLid}`, yHeaders, 6000);
  if (!viewJ?.result) return null;
  const decView = await postJson(`${ENC_DEC_API}/dec-movies-flix`, { text: viewJ.result }, {}, 5000);
  const embedUrl = decView?.result?.url;
  return await decryptHosterUrl(embedUrl, "yFlix");
}

// 9. Byse / 1hd.one (enc-byse / dec-byse)
async function resolveByse(imdbId, tmdbId, isMovie, season, episode) {
  if (!tmdbId) return null;
  const entryUrl = isMovie
    ? `https://0123movie.space/mv/${imdbId || "tt0000000"}/${tmdbId}/`
    : `https://0123movie.space/pl/${tmdbId}/${season}/${episode}`;
  try {
    const r = await fetchWithTimeout(entryUrl, { method: "GET", redirect: "manual", headers: { Referer: "https://1hd.one/" } }, 5500);
    const loc = r.headers.get("location");
    if (!loc) return null;
    const m = loc.match(/^(https?:\/\/[^/]+)\/[A-Za-z]\/([^/?#]+)/);
    if (!m) return null;
    const [, embedOrigin, code] = m;
    const bHeaders = { Referer: loc, Origin: embedOrigin };
    const det = await getJson(`${embedOrigin}/api/videos/${code}/embed/details`, bHeaders, 5500);
    if (!det) return null;
    const pb = await getJson(`${embedOrigin}/api/videos/${code}/embed/playback`, bHeaders, 5500);
    if (!pb?.playback) return null;
    const dec = await postJson(`${ENC_DEC_API}/dec-byse`, { text: pb.playback }, {}, 5500);
    return extractStreamFromJson(dec, "byse", "Byse HD", loc, embedOrigin);
  } catch {
    return null;
  }
}

// 10. AnimeKai + MegaUp
async function resolveAnimeKaiMegaUp(anilistId, malId, title, episode) {
  let kaiEntry = null;
  if (malId) {
    const r = await getJson(`${ENC_DEC_DB}/kai/find?mal_id=${malId}`, {}, 5500);
    kaiEntry = Array.isArray(r) ? r[0] : r?.result || r;
  }
  if (!kaiEntry?.episodes && anilistId) {
    const r = await getJson(`${ENC_DEC_DB}/kai/find?anilist_id=${anilistId}`, {}, 5500);
    kaiEntry = Array.isArray(r) ? r[0] : r?.result || r;
  }
  if (!kaiEntry?.episodes && title) {
    const r = await getJson(`${ENC_DEC_DB}/kai/search?query=${encodeURIComponent(title)}`, {}, 5500);
    const arr = Array.isArray(r) ? r : Array.isArray(r?.result) ? r.result : [];
    kaiEntry = arr[0];
  }
  const epsObj = kaiEntry?.episodes?.["1"] || (kaiEntry?.episodes && Object.values(kaiEntry.episodes)[0]);
  const epToken = epsObj?.[String(episode)]?.token || epsObj?.["1"]?.token;
  if (!epToken) return null;

  const kaiHeaders = { Referer: "https://animekai.to/", Accept: "application/json" };
  const encTok = (await getJson(`${ENC_DEC_API}/enc-kai?text=${encodeURIComponent(epToken)}`, {}, 5000))?.result;
  if (!encTok) return null;
  const srvJ = await getJson(`https://animekai.to/ajax/links/list?token=${epToken}&_=${encTok}`, kaiHeaders, 6000);
  if (!srvJ?.result) return null;
  const parsedSrv = await postJson(`${ENC_DEC_API}/parse-html`, { text: srvJ.result }, {}, 5000);
  const subGroup = parsedSrv?.result?.sub || parsedSrv?.result?.softsub || parsedSrv?.result?.dub || {};
  const servers = Object.values(subGroup);
  for (const s of servers.slice(0, 2)) {
    const lid = s?.lid || s?.id;
    if (!lid) continue;
    const encLid = (await getJson(`${ENC_DEC_API}/enc-kai?text=${encodeURIComponent(lid)}`, {}, 5000))?.result;
    if (!encLid) continue;
    const viewJ = await getJson(`https://animekai.to/ajax/links/view?id=${lid}&_=${encLid}`, kaiHeaders, 6000);
    if (!viewJ?.result) continue;
    const decView = await postJson(`${ENC_DEC_API}/dec-kai`, { text: viewJ.result }, {}, 5000);
    const parsed = await decryptHosterUrl(decView?.result?.url, "AnimeKai");
    if (parsed) return parsed;
  }
  return null;
}

// 11. MegaPlay (Anime)
async function resolveMegaPlay(anilistId, malId, episode) {
  const pageUrl = malId
    ? `https://megaplay.buzz/stream/mal/${malId}/${episode}/sub`
    : anilistId
    ? `https://megaplay.buzz/stream/ani/${anilistId}/${episode}/sub`
    : null;
  if (!pageUrl) return null;
  const headers = { Origin: "https://megaplay.buzz", Referer: "https://megaplay.buzz/" };
  const html = await getText(pageUrl, headers, 6500);
  const m = html && html.match(/data-id="(\d+)"/);
  if (!m) return null;
  const srcJ = await getJson(`https://megaplay.buzz/stream/getSources?id=${m[1]}&platform=OTHER`, headers, 6500);
  if (!srcJ) return null;
  if (typeof srcJ.sources === "string") {
    const dec = await postJson(`${ENC_DEC_API}/dec-megaplay`, { text: srcJ.sources }, {}, 5500);
    return extractStreamFromJson(dec, "megaplay", "MegaPlay", "https://megaplay.buzz/", "https://megaplay.buzz");
  }
  return extractStreamFromJson(srcJ, "megaplay", "MegaPlay", "https://megaplay.buzz/", "https://megaplay.buzz");
}

// 12. KissKH (Asian Drama + enc-kisskh / dec-kisskh)
async function resolveKissKh(title, episode, kisskhId = null) {
  let dramaId = kisskhId;
  if (!dramaId) {
    const search = await getJson(`https://kisskh.do/api/DramaList/Search?q=${encodeURIComponent(title || "")}&type=0`, { Referer: "https://kisskh.do/" }, 6000);
    dramaId = Array.isArray(search) && search[0]?.id ? search[0].id : null;
  }
  if (!dramaId) return null;
  const detail = await getJson(`https://kisskh.do/api/DramaList/Drama/${dramaId}?isq=false`, { Referer: "https://kisskh.do/" }, 6000);
  const eps = Array.isArray(detail?.episodes) ? detail.episodes : [];
  if (!eps.length) return null;
  const targetEp = eps.find((e) => Number(e.number) === Number(episode)) || eps[eps.length - 1];
  const epId = targetEp?.id;
  if (!epId) return null;
  const vidKey = (await getJson(`${ENC_DEC_API}/enc-kisskh?text=${epId}&type=vid`, {}, 5000))?.result;
  if (!vidKey) return null;
  const vJson = await getJson(
    `https://kisskh.do/api/DramaList/Episode/${epId}.png?err=false&ts=&time=&kkey=${encodeURIComponent(vidKey)}`,
    { Referer: "https://kisskh.do/" },
    6500
  );
  if (!vJson?.Video) return null;

  const subTracks = [];
  let subtitleUrl = "";
  try {
    const subKey = (await getJson(`${ENC_DEC_API}/enc-kisskh?text=${epId}&type=sub`, {}, 4500))?.result;
    if (subKey) {
      const subs = await getJson(`https://kisskh.do/api/Sub/${epId}?kkey=${encodeURIComponent(subKey)}`, { Referer: "https://kisskh.do/" }, 5000);
      if (Array.isArray(subs)) {
        for (const s of subs) {
          if (!s?.src) continue;
          const proxySub = `/api/sub?url=${encodeURIComponent(`${ENC_DEC_API}/dec-kisskh?url=${encodeURIComponent(s.src)}`)}`;
          subTracks.push({ label: s.label || s.land || "Subtitle", lang: String(s.land || s.label || "").toLowerCase(), url: proxySub });
        }
        const indo = subTracks.find((s) => s.lang.includes("id") || s.lang.includes("indo"));
        const en = subTracks.find((s) => s.lang.includes("en"));
        subtitleUrl = (indo || en || subTracks[0] || {}).url || "";
      }
    }
  } catch {}

  return {
    id: "kisskh",
    name: "KissKH",
    quality: "HD",
    streamUrl: vJson.Video,
    proxyUrl: `/api/proxy?url=${encodeURIComponent(vJson.Video)}&referer=${encodeURIComponent("https://kisskh.do/")}&origin=${encodeURIComponent("https://kisskh.do")}`,
    referer: "https://kisskh.do/",
    origin: "https://kisskh.do",
    subtitleUrl,
    subtitles: subTracks,
  };
}

// 13. OneTouchTV (Asian Drama fallback + dec-onetouchtv)
async function resolveOneTouchTv(title, year, episode) {
  if (!title) return null;
  const slug = `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")}${year ? `-${year}` : ""}`;
  const encTxt = await getText(`https://api3.devcorp.me/web/vod/${slug}/episode/${episode}`, {
    Origin: "https://onetouchtv.xyz",
    Referer: "https://onetouchtv.xyz/",
  }, 5500);
  if (!encTxt || encTxt.length < 20) return null;
  const dec = await postJson(`${ENC_DEC_API}/dec-onetouchtv`, { text: encTxt.trim() }, {}, 5500);
  return extractStreamFromJson(dec, "onetouchtv", "OneTouchTV", "https://onetouchtv.xyz/", "https://onetouchtv.xyz");
}

// --- Multi-Source Subtitle Lookup (OpenSubtitles v3 Stremio + Wyzie Subtitles) ---
const LANG_NAMES = {
  ind: "Bahasa Indonesia", id: "Bahasa Indonesia", indonesian: "Bahasa Indonesia",
  msa: "Bahasa Melayu", ms: "Bahasa Melayu", may: "Bahasa Melayu", malay: "Bahasa Melayu",
  eng: "English", en: "English", english: "English",
  jpn: "Japanese", ja: "Japanese", kor: "Korean", ko: "Korean",
  zho: "Chinese", zh: "Chinese", chi: "Chinese", tha: "Thai", th: "Thai",
  spa: "Spanish", es: "Spanish", fra: "French", fr: "French", ara: "Arabic", ar: "Arabic",
};
function formatLangLabel(rawLang, fallback = "Subtitle") {
  const k = String(rawLang || "").trim().toLowerCase();
  if (LANG_NAMES[k]) return LANG_NAMES[k];
  if (k.includes("indo")) return "Bahasa Indonesia";
  if (k.includes("malay") || k.includes("melayu")) return "Bahasa Melayu";
  if (k.includes("eng")) return "English";
  return rawLang || fallback;
}

async function fetchOpenSubtitlesV3(imdbId, isMovie, season, episode) {
  if (!imdbId || !imdbId.startsWith("tt")) return [];
  const subPath = isMovie
    ? `movie/${encodeURIComponent(imdbId)}.json`
    : `series/${encodeURIComponent(imdbId)}:${season}:${episode}.json`;
  const res = await getJson(`https://opensubtitles-v3.strem.io/subtitles/${subPath}`, {}, 6500);
  const subs = Array.isArray(res?.subtitles) ? res.subtitles : [];
  return subs
    .filter((s) => s && typeof s.url === "string" && s.url.startsWith("http"))
    .map((s, idx) => ({
      label: formatLangLabel(s.lang, `Subtitle #${idx + 1}`),
      lang: String(s.lang || "").toLowerCase(),
      url: `/api/sub?url=${encodeURIComponent(s.url)}`,
    }));
}

async function fetchWyzieById(id, isMovie, season, episode) {
  if (!id) return [];
  const url = isMovie
    ? `https://sub.wyzie.ru/search?id=${encodeURIComponent(id)}`
    : `https://sub.wyzie.ru/search?id=${encodeURIComponent(id)}&season=${season}&episode=${episode}`;
  const list = await getJson(url, {}, 6500);
  if (!Array.isArray(list)) return [];
  return list
    .filter((s) => s && typeof s.url === "string" && s.url.startsWith("http"))
    .map((s) => ({
      label: formatLangLabel(s.display || s.language, "Subtitle"),
      lang: String(s.language || s.display || "").toLowerCase(),
      url: `/api/sub?url=${encodeURIComponent(s.url)}`,
    }));
}

async function fetchWyzieSubtitles(tmdbId, imdbId, isMovie, season, episode) {
  const cKey = `subs_${imdbId}_${tmdbId}_${isMovie}_${season}_${episode}`;
  const hit = getCache(cKey);
  if (hit) return hit;

  const jobs = [];
  if (imdbId && imdbId.startsWith("tt")) {
    jobs.push(fetchOpenSubtitlesV3(imdbId, isMovie, season, episode));
    jobs.push(fetchWyzieById(imdbId, isMovie, season, episode));
  }
  if (tmdbId) {
    jobs.push(fetchWyzieById(tmdbId, isMovie, season, episode));
  }

  const settled = await Promise.allSettled(jobs);
  const all = [];
  for (const r of settled) {
    if (r.status === "fulfilled" && Array.isArray(r.value)) all.push(...r.value);
  }

  const seenUrl = new Set();
  const langCount = new Map();
  const out = [];
  all.sort((a, b) => {
    const score = (x) =>
      x.lang === "ind" || x.lang === "id" || x.lang.includes("indo")
        ? 0
        : x.lang === "msa" || x.lang === "ms" || x.lang.includes("malay")
        ? 1
        : x.lang === "eng" || x.lang === "en" || x.lang.includes("eng")
        ? 2
        : 3;
    return score(a) - score(b);
  });

  for (const s of all) {
    if (seenUrl.has(s.url)) continue;
    seenUrl.add(s.url);
    const n = (langCount.get(s.label) || 0) + 1;
    if (n > 3) continue; // max 3 tracks per language to keep menu clean
    langCount.set(s.label, n);
    out.push({
      label: n === 1 ? s.label : `${s.label} #${n}`,
      lang: s.lang,
      url: s.url,
    });
  }
  const finalSubs = out.slice(0, 18);
  if (finalSubs.length > 0) setCache(cKey, finalSubs, 30 * 60 * 1000);
  return finalSubs;
}

// --- Real Actor / Person Photo Fetcher (TVMaze + Wikipedia REST API) ---
async function fetchPersonPhoto(name) {
  if (!name) return "";
  const k = `person_photo_${name.toLowerCase().trim()}`;
  const hit = getCache(k);
  if (hit !== null) return hit;

  let photo = "";
  const tv = await getJson(`https://api.tvmaze.com/search/people?q=${encodeURIComponent(name)}`, {}, 4500);
  if (Array.isArray(tv) && tv.length) {
    const exact = tv.find((x) => x?.person?.image?.medium) || tv[0];
    photo = exact?.person?.image?.medium || exact?.person?.image?.original || "";
  }
  if (!photo) {
    const wikiSlug = encodeURIComponent(name.trim().replace(/\s+/g, "_"));
    const wk = await getJson(`https://en.wikipedia.org/api/rest_v1/page/summary/${wikiSlug}`, {}, 4500);
    photo = wk?.thumbnail?.source || "";
  }
  cache.set(k, { val: photo, exp: Date.now() + 24 * 60 * 60 * 1000 });
  return photo;
}

// --- First-Wins Instant Resolver + Background Full Collector ---
const activeResolves = new Map();

function buildProviderTasks({ id, imdbId, tmdbId, aniListId, malId, kisskhId, title, year, category, season, episode, mapping }) {
  const isMovie = category === "MOVIE";
  const isAnime = category === "ANIME";
  const isDrama = category === "ASIAN_DRAMA";
  const effTmdb = mapping?.tmdbId || tmdbId || "";
  const effImdb = mapping?.imdbId || imdbId || (String(id).startsWith("tt") ? id : "");

  const tasks = [];
  if (isAnime) {
    tasks.push(() => resolveAnimeKaiMegaUp(aniListId, malId, title, episode));
    tasks.push(() => resolveMegaPlay(aniListId, malId, episode));
    if (aniListId) tasks.push(() => resolveNexa(aniListId, "anilist", false, 1, episode));
    if (effTmdb) {
      tasks.push(() => resolveVidLink(effTmdb, false, season, episode));
      tasks.push(() => resolveCinejoy(title, year, effImdb, effTmdb, false, season, episode));
      tasks.push(() => resolveVideasy(title, year, effImdb, effTmdb, false, season, episode));
    }
  } else if (isDrama) {
    tasks.push(() => resolveKissKh(title, episode, kisskhId));
    tasks.push(() => resolveOneTouchTv(title, year, episode));
    if (effTmdb) {
      tasks.push(() => resolveVidLink(effTmdb, false, season, episode));
      tasks.push(() => resolveCinejoy(title, year, effImdb, effTmdb, false, season, episode));
      tasks.push(() => resolveVideasy(title, year, effImdb, effTmdb, false, season, episode));
      tasks.push(() => resolveNexa(effTmdb, "tmdb", false, season, episode));
      tasks.push(() => resolveMeowTv(effTmdb, false, season, episode));
    }
    if (mapping?.flixId) tasks.push(() => resolveYFlixRapid(mapping, season, episode));
  } else {
    if (effTmdb) {
      tasks.push(() => resolveVidLink(effTmdb, isMovie, season, episode));
      tasks.push(() => resolveCinejoy(title, year, effImdb, effTmdb, isMovie, season, episode));
      tasks.push(() => resolveVideasy(title, year, effImdb, effTmdb, isMovie, season, episode));
      tasks.push(() => resolveNexa(effTmdb, "tmdb", isMovie, season, episode));
      tasks.push(() => resolveMeowTv(effTmdb, isMovie, season, episode));
      tasks.push(() => resolveHexa(effTmdb, isMovie, season, episode));
      tasks.push(() => resolveVidFastOrStage("vidfast", "VidFast", "https://vidfast.vc", effTmdb, isMovie, season, episode));
      tasks.push(() => resolveVidFastOrStage("vidcore", "VidCore", "https://vidcore.io", effTmdb, isMovie, season, episode));
      tasks.push(() => resolveVidFastOrStage("vidup", "VidUp", "https://vidup.to", effTmdb, isMovie, season, episode));
      tasks.push(() => resolveByse(effImdb, effTmdb, isMovie, season, episode));
    }
    if (mapping?.flixId) tasks.push(() => resolveYFlixRapid(mapping, season, episode));
  }
  return tasks;
}

function resolveVidFastOrStage(slug, label, baseUrl, tmdbId, isMovie, season, episode) {
  return resolveVidStageProvider(slug, label, baseUrl, tmdbId, isMovie, season, episode);
}

async function resolveStreamsInstant(params, fastMode = true) {
  const cacheKey = `res_${params.id}_${params.category}_${params.season}_${params.episode}`;
  const cached = getCache(cacheKey);
  if (cached && cached.servers.length > 0) return cached;

  let job = activeResolves.get(cacheKey);
  if (!job) {
    const state = { servers: [], subtitles: [], done: false, firstResolve: null };
    const firstPromise = new Promise((resolve) => {
      state.firstResolve = resolve;
    });

    const addResult = (res) => {
      const items = Array.isArray(res) ? res : res ? [res] : [];
      for (const s of items) {
        if (!s || !s.streamUrl) continue;
        if (!state.servers.some((x) => x.streamUrl === s.streamUrl)) {
          state.servers.push(s);
          if (state.firstResolve) {
            const fn = state.firstResolve;
            state.firstResolve = null;
            fn(s);
          }
        }
      }
    };

    const fullPromise = (async () => {
      const isMovie = params.category === "MOVIE";
      const mapping = params.category === "ANIME" && !params.imdbId && !params.tmdbId
        ? { flixId: "", tmdbId: "", imdbId: "" }
        : await resolveFlixMapping(params.imdbId, params.tmdbId, params.title, isMovie);

      const tasks = buildProviderTasks({ ...params, mapping });
      const subTask = fetchWyzieSubtitles(mapping?.tmdbId || params.tmdbId, mapping?.imdbId || params.imdbId, isMovie, params.season, params.episode)
        .then((subs) => {
          state.subtitles = subs;
        })
        .catch(() => {});

      await Promise.allSettled(
        tasks.map((fn) =>
          Promise.resolve()
            .then(fn)
            .then(addResult)
            .catch(() => {})
        )
      );
      await subTask;

      state.done = true;
      if (state.firstResolve) {
        const fn = state.firstResolve;
        state.firstResolve = null;
        fn(null);
      }
      if (state.servers.length > 0) {
        setCache(cacheKey, { servers: state.servers, subtitles: state.subtitles, done: true }, 15 * 60 * 1000);
      }
      setTimeout(() => activeResolves.delete(cacheKey), 60 * 1000);
      return { servers: state.servers, subtitles: state.subtitles, done: true };
    })();

    job = { state, firstPromise, fullPromise };
    activeResolves.set(cacheKey, job);
  }

  if (fastMode) {
    if (job.state.servers.length === 0 && !job.state.done) {
      await Promise.race([job.firstPromise, new Promise((r) => setTimeout(r, 7500))]);
    }
    return {
      servers: job.state.servers,
      subtitles: job.state.subtitles,
      done: job.state.done,
    };
  }
  return await job.fullPromise;
}

// =========================================================================
// KATALOG MELIMPAH (Cinemeta Multi-Page + KissKH Dinamis + Anime 3-Layer)
// =========================================================================

function mapCinemeta(m, cat) {
  const id = m.imdb_id || m.id || "";
  return {
    id,
    imdbId: m.imdb_id || (String(id).startsWith("tt") ? id : ""),
    tmdbId: m.moviedb_id ? String(m.moviedb_id) : "",
    title: m.name || "Tanpa Judul",
    category: cat,
    poster: m.poster || `https://images.metahub.space/poster/medium/${id}/img`,
    backdrop: m.background || `https://images.metahub.space/background/medium/${id}/img`,
    year: String(m.releaseInfo || m.year || "").slice(0, 4),
    rating: String(m.imdbRating || ""),
    overview: m.description || "",
    genres: Array.isArray(m.genres) ? m.genres : [],
  };
}

function dedupMedia(list) {
  const seen = new Set();
  const out = [];
  for (const item of list) {
    if (!item || !item.title) continue;
    const key = `${item.category}_${item.id || item.title.toLowerCase()}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

// --- Anime 3-Layer Anti-Empty Fetcher (AniList -> Jikan MAL -> EncDec /db/kai) ---
async function fetchAnimeAniList(sort = "TRENDING_DESC", search = null, page = 1, perPage = 32) {
  const query = `query ($page: Int, $perPage: Int, $search: String, $sort: [MediaSort]) {
    Page(page: $page, perPage: $perPage) {
      media(type: ANIME, sort: $sort, search: $search, isAdult: false) {
        id idMal title { romaji english } coverImage { large extraLarge } bannerImage
        seasonYear averageScore description(asHtml: false) genres episodes
      }
    }
  }`;
  const res = await postJson(
    "https://graphql.anilist.co",
    { query, variables: { page, perPage, sort: [sort], ...(search ? { search } : {}) } },
    {},
    6500
  );
  return (res?.data?.Page?.media || []).map((a) => ({
    id: `ani_${a.id}`,
    aniListId: a.id,
    malId: a.idMal || null,
    imdbId: "",
    title: a.title?.english || a.title?.romaji || "Anime",
    category: "ANIME",
    poster: a.coverImage?.extraLarge || a.coverImage?.large || "",
    backdrop: a.bannerImage || a.coverImage?.extraLarge || a.coverImage?.large || "",
    year: String(a.seasonYear || ""),
    rating: a.averageScore ? (a.averageScore / 10).toFixed(1) : "",
    overview: String(a.description || "").replace(/<[^>]+>/g, ""),
    genres: a.genres || [],
    totalEpisodes: a.episodes || 12,
  }));
}

async function fetchAnimeJikan(mode = "airing", search = null, page = 1) {
  const url = search
    ? `https://api.jikan.moe/v4/anime?q=${encodeURIComponent(search)}&sfw=true&limit=24&page=${page}`
    : mode === "score"
    ? `https://api.jikan.moe/v4/top/anime?sfw=true&limit=24&page=${page}`
    : `https://api.jikan.moe/v4/top/anime?filter=${mode}&sfw=true&limit=24&page=${page}`;
  const res = await getJson(url, {}, 7500);
  return (res?.data || []).map((a) => ({
    id: `mal_${a.mal_id}`,
    malId: a.mal_id,
    aniListId: null,
    imdbId: "",
    title: a.title_english || a.title || "Anime",
    category: "ANIME",
    poster: a.images?.jpg?.large_image_url || a.images?.jpg?.image_url || "",
    backdrop: a.trailer?.images?.maximum_image_url || a.images?.jpg?.large_image_url || "",
    year: String(a.year || a.aired?.prop?.from?.year || ""),
    rating: a.score ? Number(a.score).toFixed(1) : "",
    overview: a.synopsis || "",
    genres: (a.genres || []).map((g) => g.name),
    totalEpisodes: a.episodes || 12,
  }));
}

async function fetchAnimeEncDecDb(search = "one") {
  const res = await getJson(`${ENC_DEC_DB}/kai/search?query=${encodeURIComponent(search || "a")}`, {}, 6500);
  const arr = Array.isArray(res) ? res : Array.isArray(res?.result) ? res.result : [];
  return arr.slice(0, 30).map((item) => {
    const info = item.info || item;
    return {
      id: `kai_${info.kai_id || info.mal_id || info.anilist_id || Math.random().toString(36).slice(2)}`,
      malId: info.mal_id || null,
      aniListId: info.anilist_id || null,
      imdbId: "",
      title: info.title_en || info.title_jp || "Anime",
      category: "ANIME",
      poster: info.poster || info.image || "",
      backdrop: info.poster || info.image || "",
      year: String(info.year || ""),
      rating: "",
      overview: "",
      genres: ["Anime"],
      totalEpisodes: 12,
    };
  });
}

async function fetchAnimeSmart(mode = "trending", search = null, page = 1) {
  const sortMap = { trending: "TRENDING_DESC", score: "SCORE_DESC", popular: "POPULARITY_DESC" };
  let items = await fetchAnimeAniList(sortMap[mode] || "TRENDING_DESC", search, page, 32);
  if (!items.length) {
    const jikanMode = mode === "score" ? "score" : mode === "popular" ? "bypopularity" : "airing";
    items = await fetchAnimeJikan(jikanMode, search, page);
  }
  if (!items.length) {
    items = await fetchAnimeEncDecDb(search || (mode === "score" ? "fullmetal" : "piece"));
  }
  return items;
}

// --- KissKH Dinamis Drama Asia Fetcher ---
const CURATED_DRAMAS = [
  { id: "tt10919420", imdbId: "tt10919420", title: "Squid Game", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt10919420/img", backdrop: "https://images.metahub.space/background/medium/tt10919420/img", year: "2021", rating: "8.0", overview: "Ratusan pemain terlilit utang mengikuti permainan mematikan demi hadiah miliaran won.", genres: ["Thriller", "Drama"] },
  { id: "tt27668559", imdbId: "tt27668559", title: "Queen of Tears", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt27668559/img", backdrop: "https://images.metahub.space/background/medium/tt27668559/img", year: "2024", rating: "8.2", overview: "Kisah cinta penuh ujian antara pewaris konglomerat dan pengacara muda.", genres: ["Romance", "Drama"] },
  { id: "tt21344706", imdbId: "tt21344706", title: "The Glory", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt21344706/img", backdrop: "https://images.metahub.space/background/medium/tt21344706/img", year: "2022", rating: "8.1", overview: "Rencana balas dendam matang terhadap para perundung masa sekolah.", genres: ["Thriller", "Drama"] },
  { id: "tt10795658", imdbId: "tt10795658", title: "Alice in Borderland", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt10795658/img", backdrop: "https://images.metahub.space/background/medium/tt10795658/img", year: "2020", rating: "7.8", overview: "Arisu dan teman-temannya bertahan hidup lewat permainan maut di Tokyo kosong.", genres: ["Sci-Fi", "Thriller"] },
  { id: "tt24640580", imdbId: "tt24640580", title: "Moving", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt24640580/img", backdrop: "https://images.metahub.space/background/medium/tt24640580/img", year: "2023", rating: "8.4", overview: "Anak-anak berkekuatan super dan orang tua mereka bersembunyi dari agen rahasia.", genres: ["Action", "Sci-Fi"] },
  { id: "tt10850932", imdbId: "tt10850932", title: "Crash Landing on You", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt10850932/img", backdrop: "https://images.metahub.space/background/medium/tt10850932/img", year: "2019", rating: "8.7", overview: "Pewaris Korea Selatan tersesat di Korea Utara akibat kecelakaan paralayang.", genres: ["Romance", "Comedy"] },
  { id: "tt20859920", imdbId: "tt20859920", title: "Alchemy of Souls", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt20859920/img", backdrop: "https://images.metahub.space/background/medium/tt20859920/img", year: "2022", rating: "8.7", overview: "Penyihir tangguh yang jiwanya terperangkap dalam tubuh lemah bertemu bangsawan muda.", genres: ["Fantasy", "Romance"] },
  { id: "tt13433812", imdbId: "tt13433812", title: "Vincenzo", category: "ASIAN_DRAMA", poster: "https://images.metahub.space/poster/medium/tt13433812/img", backdrop: "https://images.metahub.space/background/medium/tt13433812/img", year: "2021", rating: "8.4", overview: "Pengacara mafia Italia keturunan Korea menghadapi konglomerat korup.", genres: ["Crime", "Comedy"] },
];

async function fetchKissKhDramas(page = 1, country = 0, order = 1) {
  const url = `https://kisskh.do/api/DramaList/List?page=${page}&type=0&sub=0&country=${country}&status=0&order=${order}`;
  const res = await getJson(url, { Referer: "https://kisskh.do/" }, 6500);
  const rows = Array.isArray(res?.data) ? res.data : [];
  return rows.map((d) => ({
    id: `kisskh_${d.id}`,
    kisskhId: d.id,
    imdbId: "",
    title: d.title || "Drama Asia",
    category: "ASIAN_DRAMA",
    poster: d.thumbnail || "",
    backdrop: d.thumbnail || "",
    year: String(d.releaseDate || "").slice(0, 4),
    rating: "",
    overview: d.description || "",
    genres: [d.country || "Drama Asia"].filter(Boolean),
    totalEpisodes: d.episodesCount || 16,
  }));
}

async function loadHomeCatalog() {
  const hit = getCache("home_catalog_v11");
  if (hit && hit.animeTrend.length > 0 && hit.movies.length > 0) return hit;

  const [
    moviesTop1,
    moviesTop2,
    moviesAward,
    seriesTop1,
    seriesTop2,
    seriesAward,
    actionMovies,
    kissPop1,
    kissPop2,
    animeTrend,
    animeTop,
  ] = await Promise.all([
    getJson("https://v3-cinemeta.strem.io/catalog/movie/top.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/movie/top/skip=40.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/movie/imdbRating.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/series/top.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/series/top/skip=40.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/series/imdbRating.json"),
    getJson("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Action.json"),
    fetchKissKhDramas(1, 0, 1),
    fetchKissKhDramas(2, 0, 1),
    fetchAnimeSmart("trending", null, 1),
    fetchAnimeSmart("score", null, 1),
  ]);

  const movies = dedupMedia([
    ...(moviesTop1?.metas || []).map((m) => mapCinemeta(m, "MOVIE")),
    ...(moviesTop2?.metas || []).map((m) => mapCinemeta(m, "MOVIE")),
  ]);
  const series = dedupMedia([
    ...(seriesTop1?.metas || []).map((m) => mapCinemeta(m, "SERIES")),
    ...(seriesTop2?.metas || []).map((m) => mapCinemeta(m, "SERIES")),
  ]);
  const awardMovies = dedupMedia((moviesAward?.metas || []).slice(0, 40).map((m) => mapCinemeta(m, "MOVIE")));
  const awardSeries = dedupMedia((seriesAward?.metas || []).slice(0, 40).map((m) => mapCinemeta(m, "SERIES")));
  const action = dedupMedia((actionMovies?.metas || []).slice(0, 40).map((m) => mapCinemeta(m, "MOVIE")));
  const dramas = dedupMedia([...CURATED_DRAMAS, ...kissPop1, ...kissPop2]);

  const payload = { movies, series, awardMovies, awardSeries, action, dramas, animeTrend, animeTop };
  if (movies.length > 0 && animeTrend.length > 0) {
    setCache("home_catalog_v11", payload, 10 * 60 * 1000);
  }
  return payload;
}

async function loadCategoryPage(cat, genre = "", page = 1) {
  const skip = (Math.max(1, page) - 1) * 40;
  if (cat === "movie") {
    const url = genre
      ? `https://v3-cinemeta.strem.io/catalog/movie/top/genre=${encodeURIComponent(genre)}&skip=${skip}.json`
      : `https://v3-cinemeta.strem.io/catalog/movie/top/skip=${skip}.json`;
    const j = await getJson(url);
    return (j?.metas || []).map((m) => mapCinemeta(m, "MOVIE"));
  }
  if (cat === "series") {
    const url = genre
      ? `https://v3-cinemeta.strem.io/catalog/series/top/genre=${encodeURIComponent(genre)}&skip=${skip}.json`
      : `https://v3-cinemeta.strem.io/catalog/series/top/skip=${skip}.json`;
    const j = await getJson(url);
    return (j?.metas || []).map((m) => mapCinemeta(m, "SERIES"));
  }
  if (cat === "drama") {
    const countryMap = { Korea: 2, China: 3, Jepang: 4, Thailand: 5 };
    const countryCode = countryMap[genre] || 0;
    const rows = await fetchKissKhDramas(page, countryCode, 1);
    return page === 1 && !genre ? dedupMedia([...CURATED_DRAMAS, ...rows]) : rows;
  }
  if (cat === "anime") {
    const mode = genre === "Rating Tertinggi" ? "score" : genre === "Populer" ? "popular" : "trending";
    return await fetchAnimeSmart(mode, null, page);
  }
  return [];
}

// --- Detail Enricher (Cast, Runtime, Director, Trailer, Episodes, Similar) ---
async function loadMediaDetail(id, category, title = "", totalEpisodes = 12) {
  const cacheKey = `detail_${id}_${category}`;
  const hit = getCache(cacheKey);
  if (hit) return hit;

  let detail = {
    overview: "",
    runtime: "",
    language: "Inggris",
    releaseDate: "",
    directors: [],
    cast: [],
    trailerYoutubeId: "",
    episodes: [],
    similar: [],
  };

  if (category === "ANIME") {
    const malIdMatch = String(id).match(/^mal_(\d+)$/);
    const aniIdMatch = String(id).match(/^ani_(\d+)$/);
    let total = Number(totalEpisodes || 12);
    if (aniIdMatch) {
      const q = `query ($id: Int) { Media(id: $id, type: ANIME) { description(asHtml: false) duration seasonYear episodes trailer { id site } characters(perPage: 10, sort: ROLE) { nodes { name { full } image { medium } } } recommendations(perPage: 10) { nodes { mediaRecommendation { id idMal title { english romaji } coverImage { large } seasonYear averageScore episodes } } } } }`;
      const r = await postJson("https://graphql.anilist.co", { query: q, variables: { id: Number(aniIdMatch[1]) } }, {}, 6000);
      const m = r?.data?.Media;
      if (m) {
        if (m.description) detail.overview = String(m.description).replace(/<[^>]+>/g, "");
        if (m.duration) detail.runtime = `${m.duration} mnt/ep`;
        detail.language = "Jepang";
        if (m.seasonYear) detail.releaseDate = String(m.seasonYear);
        if (m.episodes) total = m.episodes;
        if (m.trailer?.site === "youtube" && m.trailer?.id) detail.trailerYoutubeId = m.trailer.id;
        detail.cast = (m.characters?.nodes || []).map((c) => ({ name: c.name?.full || "", role: "Karakter", photo: c.image?.medium || "" }));
        detail.similar = (m.recommendations?.nodes || [])
          .map((n) => n.mediaRecommendation)
          .filter(Boolean)
          .map((a) => ({
            id: `ani_${a.id}`,
            aniListId: a.id,
            malId: a.idMal || null,
            title: a.title?.english || a.title?.romaji || "Anime",
            category: "ANIME",
            poster: a.coverImage?.large || "",
            backdrop: a.coverImage?.large || "",
            year: String(a.seasonYear || ""),
            rating: a.averageScore ? (a.averageScore / 10).toFixed(1) : "",
            totalEpisodes: a.episodes || 12,
          }));
      }
    } else if (malIdMatch) {
      const r = await getJson(`https://api.jikan.moe/v4/anime/${malIdMatch[1]}/full`, {}, 6000);
      const a = r?.data;
      if (a) {
        detail.overview = a.synopsis || "";
        detail.runtime = a.duration || "24 mnt";
        detail.language = "Jepang";
        detail.releaseDate = String(a.year || a.aired?.string || "");
        if (a.episodes) total = a.episodes;
        if (a.trailer?.youtube_id) detail.trailerYoutubeId = a.trailer.youtube_id;
        const ch = await getJson(`https://api.jikan.moe/v4/anime/${malIdMatch[1]}/characters`, {}, 5000);
        detail.cast = (ch?.data || []).slice(0, 10).map((c) => ({
          name: c.character?.name || "",
          role: c.role || "Karakter",
          photo: c.character?.images?.jpg?.image_url || "",
        }));
      }
    }
    detail.episodes = Array.from({ length: Math.min(Math.max(total || 12, 1), 150) }, (_, i) => ({
      season: 1,
      episode: i + 1,
      title: `Episode ${i + 1}`,
    }));
  } else if (String(id).startsWith("kisskh_")) {
    const kid = String(id).replace("kisskh_", "");
    const d = await getJson(`https://kisskh.do/api/DramaList/Drama/${kid}?isq=false`, { Referer: "https://kisskh.do/" }, 6000);
    if (d) {
      detail.overview = d.description || "";
      detail.language = d.country || "Asia";
      detail.releaseDate = String(d.releaseDate || "").slice(0, 10);
      detail.episodes = (d.episodes || [])
        .slice()
        .sort((a, b) => Number(a.number) - Number(b.number))
        .map((e) => ({ season: 1, episode: Number(e.number) || 1, title: `Episode ${e.number}` }));
    }
  } else if (String(id).startsWith("tt")) {
    const metaType = category === "MOVIE" ? "movie" : "series";
    const metaJ = await getJson(`https://v3-cinemeta.strem.io/meta/${metaType}/${encodeURIComponent(id)}.json`, {}, 6500);
    const m = metaJ?.meta;
    if (m) {
      detail.overview = m.description || "";
      detail.runtime = m.runtime || "";
      detail.language = m.country || "Inggris";
      detail.releaseDate = m.released ? String(m.released).slice(0, 10) : String(m.releaseInfo || "");
      detail.directors = Array.isArray(m.director) ? m.director : [];
      const rawCastNames = (Array.isArray(m.cast) ? m.cast : []).slice(0, 10);
      detail.cast = await Promise.all(
        rawCastNames.map(async (name) => ({
          name,
          role: "Pemeran",
          photo: await fetchPersonPhoto(name),
        }))
      );
      if (Array.isArray(m.trailers) && m.trailers[0]?.source) detail.trailerYoutubeId = m.trailers[0].source;
      if (Array.isArray(m.videos)) {
        detail.episodes = m.videos
          .filter((v) => Number(v.season) > 0 && Number(v.episode) > 0)
          .map((v) => ({
            season: Number(v.season),
            episode: Number(v.episode),
            title: v.title || v.name || `Episode ${v.episode}`,
            thumbnail: v.thumbnail || "",
          }));
      }
    }
  }

  setCache(cacheKey, detail, 20 * 60 * 1000);
  return detail;
}

// --- Response & Proxy Helpers ---
function sendJson(res, status, obj) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify(obj));
}

function srtToVtt(text) {
  if (!text) return "";
  const clean = text.replace(/^\uFEFF/, "");
  if (clean.trim().startsWith("WEBVTT")) return clean;
  return "WEBVTT\n\n" + clean.replace(/\r+/g, "").replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, "$1.$2");
}

function rewriteM3u8(body, baseUrl, referer, origin) {
  const resolveAbs = (raw) => {
    try {
      return new URL(raw, baseUrl).href;
    } catch {
      return raw;
    }
  };
  const wrapProxy = (abs) =>
    `/api/proxy?url=${encodeURIComponent(abs)}&referer=${encodeURIComponent(referer)}&origin=${encodeURIComponent(origin)}`;

  return body
    .split(/\r?\n/)
    .map((line) => {
      const t = line.trim();
      if (!t) return line;
      if (t.startsWith("#")) {
        return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${wrapProxy(resolveAbs(uri))}"`);
      }
      return wrapProxy(resolveAbs(t));
    })
    .join("\n");
}

// --- HTTP Server ---
const server = http.createServer(async (req, res) => {
  try {
    const parsedUrl = new URL(req.url, `http://localhost:${PORT}`);
    const p = parsedUrl.pathname;

    if (p === "/api/health") {
      return sendJson(res, 200, { ok: true, version: "11.1.0", engine: "EncDecEndpoints" });
    }

    if (p === "/api/catalog/home") {
      const data = await loadHomeCatalog();
      return sendJson(res, 200, data);
    }

    if (p === "/api/catalog/category") {
      const cat = parsedUrl.searchParams.get("cat") || "movie";
      const genre = parsedUrl.searchParams.get("genre") || "";
      const page = Number(parsedUrl.searchParams.get("page") || 1);
      const items = await loadCategoryPage(cat, genre, page);
      return sendJson(res, 200, { items, page, hasMore: items.length >= 12 });
    }

    if (p === "/api/search") {
      const q = (parsedUrl.searchParams.get("q") || "").trim();
      if (!q) return sendJson(res, 200, { results: [] });
      const [mRes, sRes, kRes, aRes] = await Promise.all([
        getJson(`https://v3-cinemeta.strem.io/catalog/movie/top/search=${encodeURIComponent(q)}.json`),
        getJson(`https://v3-cinemeta.strem.io/catalog/series/top/search=${encodeURIComponent(q)}.json`),
        getJson(`https://kisskh.do/api/DramaList/Search?q=${encodeURIComponent(q)}&type=0`, { Referer: "https://kisskh.do/" }),
        fetchAnimeSmart("popular", q, 1),
      ]);
      const kissMapped = (Array.isArray(kRes) ? kRes : []).slice(0, 14).map((d) => ({
        id: `kisskh_${d.id}`,
        kisskhId: d.id,
        imdbId: "",
        title: d.title || "Drama Asia",
        category: "ASIAN_DRAMA",
        poster: d.thumbnail || "",
        backdrop: d.thumbnail || "",
        year: "",
        rating: "",
        genres: ["Drama Asia"],
        totalEpisodes: d.episodesCount || 16,
      }));
      const results = dedupMedia([
        ...(mRes?.metas || []).slice(0, 18).map((m) => mapCinemeta(m, "MOVIE")),
        ...(sRes?.metas || []).slice(0, 18).map((m) => mapCinemeta(m, "SERIES")),
        ...kissMapped,
        ...(aRes || []).slice(0, 18),
      ]);
      return sendJson(res, 200, { results });
    }

    if (p === "/api/detail") {
      const id = parsedUrl.searchParams.get("id") || "";
      const category = parsedUrl.searchParams.get("category") || "MOVIE";
      const title = parsedUrl.searchParams.get("title") || "";
      const totalEpisodes = Number(parsedUrl.searchParams.get("totalEpisodes") || 12);
      const detail = await loadMediaDetail(id, category, title, totalEpisodes);
      return sendJson(res, 200, detail);
    }

    if (p === "/api/resolve" || p === "/api/resolve/servers") {
      const params = {
        id: parsedUrl.searchParams.get("id") || "",
        imdbId: parsedUrl.searchParams.get("imdbId") || "",
        tmdbId: parsedUrl.searchParams.get("tmdbId") || "",
        aniListId: parsedUrl.searchParams.get("aniListId") || "",
        malId: parsedUrl.searchParams.get("malId") || "",
        kisskhId: parsedUrl.searchParams.get("kisskhId") || "",
        title: parsedUrl.searchParams.get("title") || "",
        year: parsedUrl.searchParams.get("year") || "",
        category: parsedUrl.searchParams.get("category") || "MOVIE",
        season: Number(parsedUrl.searchParams.get("season") || 1),
        episode: Number(parsedUrl.searchParams.get("episode") || 1),
      };
      const fastMode = p === "/api/resolve" && parsedUrl.searchParams.get("fast") !== "0";
      const out = await resolveStreamsInstant(params, fastMode);
      return sendJson(res, 200, out);
    }

    if (p === "/api/subtitles") {
      const id = parsedUrl.searchParams.get("id") || "";
      const imdbId = parsedUrl.searchParams.get("imdbId") || (id.startsWith("tt") ? id : "");
      let tmdbId = parsedUrl.searchParams.get("tmdbId") || "";
      const title = parsedUrl.searchParams.get("title") || "";
      const category = parsedUrl.searchParams.get("category") || "MOVIE";
      const season = Number(parsedUrl.searchParams.get("season") || 1);
      const episode = Number(parsedUrl.searchParams.get("episode") || 1);
      const isMovie = category === "MOVIE";
      let effImdb = imdbId;
      if (!effImdb && !tmdbId && title) {
        const m = await resolveFlixMapping("", "", title, isMovie);
        effImdb = m.imdbId || "";
        tmdbId = m.tmdbId || "";
      }
      const subtitles = await fetchWyzieSubtitles(tmdbId, effImdb, isMovie, season, episode);
      return sendJson(res, 200, { subtitles });
    }

    if (p === "/api/sub") {
      const subUrl = parsedUrl.searchParams.get("url");
      if (!subUrl || !/^https?:\/\//i.test(subUrl)) return sendJson(res, 400, { error: "URL subtitle tidak valid" });
      const raw = await getText(subUrl, {}, 8000);
      if (!raw) {
        res.writeHead(404);
        return res.end("");
      }
      let content = raw;
      try {
        const j = JSON.parse(raw);
        if (typeof j?.result === "string") content = j.result;
      } catch {}
      res.writeHead(200, {
        "Content-Type": "text/vtt; charset=utf-8",
        "Access-Control-Allow-Origin": "*",
      });
      return res.end(srtToVtt(content));
    }

    if (p === "/api/proxy") {
      const target = parsedUrl.searchParams.get("url");
      const referer = parsedUrl.searchParams.get("referer") || "";
      const origin = parsedUrl.searchParams.get("origin") || "";
      if (!target || !/^https?:\/\//i.test(target)) return sendJson(res, 400, { error: "URL tidak valid" });

      const headers = { "User-Agent": UA, Accept: "*/*" };
      if (referer) headers.Referer = referer;
      if (origin) headers.Origin = origin;
      if (req.headers.range) headers.Range = req.headers.range;

      const upstream = await fetchWithTimeout(target, { method: "GET", headers }, 18000);
      if (!upstream.ok && upstream.status !== 206) {
        res.writeHead(upstream.status || 502, { "Access-Control-Allow-Origin": "*" });
        return res.end("Upstream error");
      }

      const ct = (upstream.headers.get("content-type") || "").toLowerCase();
      const looksM3u8 = target.toLowerCase().includes(".m3u8") || ct.includes("mpegurl") || ct.includes("x-mpegurl");

      if (looksM3u8) {
        const text = await upstream.text();
        const rewritten = text.trimStart().startsWith("#EXTM3U") ? rewriteM3u8(text, upstream.url || target, referer, origin) : text;
        res.writeHead(200, {
          "Content-Type": "application/vnd.apple.mpegurl; charset=utf-8",
          "Access-Control-Allow-Origin": "*",
        });
        return res.end(rewritten);
      }

      // Sniff small text/octet responses for disguised #EXTM3U playlists
      const cl = Number(upstream.headers.get("content-length") || 0);
      if (cl > 0 && cl < 512 * 1024 && !ct.startsWith("video/") && !ct.startsWith("audio/")) {
        const buf = Buffer.from(await upstream.arrayBuffer());
        const head = buf.slice(0, 16).toString("utf8").trimStart();
        if (head.startsWith("#EXTM3U")) {
          const rewritten = rewriteM3u8(buf.toString("utf8"), upstream.url || target, referer, origin);
          res.writeHead(200, {
            "Content-Type": "application/vnd.apple.mpegurl; charset=utf-8",
            "Access-Control-Allow-Origin": "*",
          });
          return res.end(rewritten);
        }
        res.writeHead(upstream.status, {
          "Content-Type": ct || "application/octet-stream",
          "Access-Control-Allow-Origin": "*",
        });
        return res.end(buf);
      }

      const respHeaders = {
        "Content-Type": ct || "application/octet-stream",
        "Access-Control-Allow-Origin": "*",
        "Accept-Ranges": "bytes",
      };
      if (upstream.headers.get("content-length")) respHeaders["Content-Length"] = upstream.headers.get("content-length");
      if (upstream.headers.get("content-range")) respHeaders["Content-Range"] = upstream.headers.get("content-range");
      res.writeHead(upstream.status, respHeaders);
      if (upstream.body) {
        Readable.fromWeb(upstream.body).pipe(res);
      } else {
        res.end();
      }
      return;
    }

    // Static files + SPA fallback
    let filePath = path.join(PUBLIC_DIR, p === "/" ? "index.html" : p.replace(/^\/+/, ""));
    if (!filePath.startsWith(PUBLIC_DIR) || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(PUBLIC_DIR, "index.html");
    }
    const ext = path.extname(filePath).toLowerCase();
    const mimeMap = {
      ".html": "text/html; charset=utf-8",
      ".png": "image/png",
      ".jpg": "image/jpeg",
      ".svg": "image/svg+xml",
      ".css": "text/css",
      ".js": "application/javascript",
      ".json": "application/json",
    };
    res.writeHead(200, { "Content-Type": mimeMap[ext] || "application/octet-stream" });
    fs.createReadStream(filePath).pipe(res);
  } catch (err) {
    sendJson(res, 500, { error: err?.message || "Internal server error" });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`SwaraPlay Web v11.1.0 berjalan di http://0.0.0.0:${PORT}`);
});
