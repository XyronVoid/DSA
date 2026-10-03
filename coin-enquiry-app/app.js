// Coin Enquiry — look up live cryptocurrency prices using the public CoinGecko API.
const API = "https://api.coingecko.com/api/v3";
const WATCHLIST_KEY = "coin-enquiry-watchlist";
const CURRENCY_KEY = "coin-enquiry-currency";

const $ = (sel) => document.querySelector(sel);
const els = {
  currency: $("#currency"),
  form: $("#search-form"),
  input: $("#search-input"),
  suggestions: $("#suggestions"),
  status: $("#status"),
  detail: $("#detail"),
  rows: $("#coin-rows"),
  tabs: document.querySelectorAll(".tab"),
};

const state = {
  currency: load(CURRENCY_KEY, "usd"),
  watchlist: new Set(load(WATCHLIST_KEY, [])),
  tab: "top",
  selectedId: null,
  chartDays: 7,
  suggestions: [],
  activeSuggestion: -1,
};

// ---------- storage ----------
function load(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}
function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ }
}

// ---------- API ----------
const cache = new Map();
const CACHE_MS = 60_000;

async function api(path) {
  const hit = cache.get(path);
  if (hit && Date.now() - hit.time < CACHE_MS) return hit.data;
  const res = await fetch(API + path);
  if (res.status === 429) throw new Error("Rate limit reached. Please wait a minute and try again.");
  if (!res.ok) throw new Error(`Request failed (${res.status}).`);
  const data = await res.json();
  cache.set(path, { time: Date.now(), data });
  return data;
}

const getMarkets = (ids) => {
  const params = new URLSearchParams({
    vs_currency: state.currency,
    order: "market_cap_desc",
    per_page: "50",
    page: "1",
    price_change_percentage: "24h",
  });
  if (ids) params.set("ids", ids.join(","));
  return api(`/coins/markets?${params}`);
};
const searchCoins = (q) => api(`/search?query=${encodeURIComponent(q)}`);
const getCoin = (id) =>
  api(`/coins/${encodeURIComponent(id)}?localization=false&tickers=false&community_data=false&developer_data=false`);
const getChart = (id, days) =>
  api(`/coins/${encodeURIComponent(id)}/market_chart?vs_currency=${state.currency}&days=${days}`);

// ---------- formatting ----------
function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function money(n, compact = false) {
  if (n == null || Number.isNaN(n)) return "—";
  const opts = { style: "currency", currency: state.currency.toUpperCase() };
  if (compact) Object.assign(opts, { notation: "compact", maximumFractionDigits: 2 });
  else if (Math.abs(n) < 1) opts.maximumSignificantDigits = 4;
  return new Intl.NumberFormat(undefined, opts).format(n);
}
function number(n) {
  return n == null ? "—" : new Intl.NumberFormat(undefined, { notation: "compact", maximumFractionDigits: 2 }).format(n);
}
function pct(n) {
  if (n == null) return '<span>—</span>';
  const cls = n >= 0 ? "up" : "down";
  return `<span class="${cls}">${n >= 0 ? "▲" : "▼"} ${Math.abs(n).toFixed(2)}%</span>`;
}
function stripHtml(html) {
  return new DOMParser().parseFromString(html || "", "text/html").body.textContent || "";
}

function setStatus(msg, isError = false) {
  els.status.textContent = msg;
  els.status.classList.toggle("error", isError);
}

// ---------- coin table ----------
async function renderTable() {
  const isWatch = state.tab === "watchlist";
  if (isWatch && state.watchlist.size === 0) {
    els.rows.innerHTML = `<tr><td colspan="6" class="status">Your watchlist is empty. Tap ☆ on any coin to add it.</td></tr>`;
    return;
  }
  els.rows.innerHTML = `<tr><td colspan="6" class="status">Loading…</td></tr>`;
  try {
    const coins = await getMarkets(isWatch ? [...state.watchlist] : null);
    if (!coins.length) {
      els.rows.innerHTML = `<tr><td colspan="6" class="status">No coins found.</td></tr>`;
      return;
    }
    els.rows.innerHTML = coins.map((c) => `
      <tr data-id="${escapeHtml(c.id)}">
        <td>${c.market_cap_rank ?? "—"}</td>
        <td><span class="coin-name"><img src="${escapeHtml(c.image)}" alt="" loading="lazy" />
          ${escapeHtml(c.name)} <span class="symbol">${escapeHtml(c.symbol)}</span></span></td>
        <td class="num">${money(c.current_price)}</td>
        <td class="num">${pct(c.price_change_percentage_24h)}</td>
        <td class="num hide-sm">${money(c.market_cap, true)}</td>
        <td>${starButton(c.id)}</td>
      </tr>`).join("");
  } catch (err) {
    els.rows.innerHTML = `<tr><td colspan="6" class="status error">${escapeHtml(err.message)}</td></tr>`;
  }
}

function starButton(id) {
  const on = state.watchlist.has(id);
  return `<button class="star ${on ? "on" : ""}" data-star="${escapeHtml(id)}"
    title="${on ? "Remove from" : "Add to"} watchlist" aria-pressed="${on}">${on ? "★" : "☆"}</button>`;
}

function toggleWatch(id) {
  if (state.watchlist.has(id)) state.watchlist.delete(id);
  else state.watchlist.add(id);
  save(WATCHLIST_KEY, [...state.watchlist]);
  document.querySelectorAll(`[data-star="${CSS.escape(id)}"]`).forEach((b) => {
    b.outerHTML = starButton(id);
  });
  if (state.tab === "watchlist") renderTable();
}

// ---------- coin detail ----------
async function showCoin(id) {
  state.selectedId = id;
  hideSuggestions();
  els.detail.hidden = false;
  els.detail.innerHTML = `<p class="status">Loading coin details…</p>`;
  els.detail.scrollIntoView({ behavior: "smooth", block: "start" });
  try {
    const coin = await getCoin(id);
    if (state.selectedId !== id) return; // user picked another coin meanwhile
    renderDetail(coin);
    loadChart();
  } catch (err) {
    els.detail.innerHTML = `<p class="status error">${escapeHtml(err.message)}</p>`;
  }
}

function renderDetail(coin) {
  const cur = state.currency;
  const m = coin.market_data || {};
  const pick = (obj) => (obj ? obj[cur] : null);
  const desc = stripHtml(coin.description?.en).trim();
  const short = desc.length > 400 ? desc.slice(0, 400).replace(/\s+\S*$/, "") + "…" : desc;

  const stats = [
    ["Market cap rank", coin.market_cap_rank ? `#${coin.market_cap_rank}` : "—"],
    ["Market cap", money(pick(m.market_cap), true)],
    ["24h volume", money(pick(m.total_volume), true)],
    ["24h high", money(pick(m.high_24h))],
    ["24h low", money(pick(m.low_24h))],
    ["7d change", pct(m.price_change_percentage_7d)],
    ["30d change", pct(m.price_change_percentage_30d)],
    ["Circulating supply", number(m.circulating_supply)],
    ["Max supply", number(m.max_supply)],
    ["All-time high", money(pick(m.ath))],
  ];

  els.detail.innerHTML = `
    <div class="detail-head">
      <div class="coin-name">
        <img src="${escapeHtml(coin.image?.large || coin.image?.small)}" alt="" />
        <div>
          <h2>${escapeHtml(coin.name)} <span class="symbol">${escapeHtml(coin.symbol)}</span></h2>
          <div class="price-big">${money(pick(m.current_price))}</div>
          <div>${pct(m.price_change_percentage_24h)} <span class="symbol">24h</span></div>
        </div>
      </div>
      <div>${starButton(coin.id)} <button id="close-detail" title="Close">✕</button></div>
    </div>
    <div class="stats">
      ${stats.map(([label, value]) => `
        <div class="stat"><div class="label">${label}</div><div class="value">${value}</div></div>`).join("")}
    </div>
    <div class="chart-controls">
      ${[1, 7, 30, 365].map((d) => `
        <button data-days="${d}" class="${d === state.chartDays ? "active" : ""}">${d === 1 ? "24h" : d === 365 ? "1y" : d + "d"}</button>`).join("")}
    </div>
    <canvas id="chart" aria-label="Price chart"></canvas>
    ${short ? `<p class="description">${escapeHtml(short)}</p>` : ""}
  `;
}

async function loadChart() {
  const id = state.selectedId;
  const canvas = $("#chart");
  if (!canvas) return;
  try {
    const data = await getChart(id, state.chartDays);
    if (state.selectedId !== id) return;
    drawChart(canvas, data.prices || []);
  } catch (err) {
    canvas.replaceWith(Object.assign(document.createElement("p"), {
      className: "status error", textContent: `Chart unavailable: ${err.message}`,
    }));
  }
}

function drawChart(canvas, prices) {
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  canvas.width = w * dpr;
  canvas.height = h * dpr;
  const ctx = canvas.getContext("2d");
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, w, h);
  if (prices.length < 2) return;

  const css = getComputedStyle(document.documentElement);
  const ys = prices.map((p) => p[1]);
  const min = Math.min(...ys), max = Math.max(...ys);
  const pad = { top: 16, bottom: 16, left: 4, right: 4 };
  const x = (i) => pad.left + (i / (prices.length - 1)) * (w - pad.left - pad.right);
  const y = (v) => pad.top + (max === min ? 0.5 : 1 - (v - min) / (max - min)) * (h - pad.top - pad.bottom);
  const color = ys[ys.length - 1] >= ys[0] ? css.getPropertyValue("--up") : css.getPropertyValue("--down");

  ctx.beginPath();
  ys.forEach((v, i) => (i ? ctx.lineTo(x(i), y(v)) : ctx.moveTo(x(i), y(v))));
  ctx.strokeStyle = color.trim();
  ctx.lineWidth = 2;
  ctx.stroke();

  ctx.lineTo(x(ys.length - 1), h);
  ctx.lineTo(x(0), h);
  ctx.closePath();
  ctx.globalAlpha = 0.12;
  ctx.fillStyle = color.trim();
  ctx.fill();
  ctx.globalAlpha = 1;

  ctx.fillStyle = css.getPropertyValue("--muted").trim();
  ctx.font = "12px system-ui, sans-serif";
  ctx.fillText(`High ${money(max)}`, pad.left + 4, 12);
  ctx.fillText(`Low ${money(min)}`, pad.left + 4, h - 4);
}

// ---------- search ----------
let searchTimer;
function onSearchInput() {
  clearTimeout(searchTimer);
  const q = els.input.value.trim();
  if (q.length < 2) return hideSuggestions();
  searchTimer = setTimeout(() => runSearch(q), 300);
}

async function runSearch(q, openFirst = false) {
  try {
    const { coins = [] } = await searchCoins(q);
    if (els.input.value.trim() !== q) return; // stale result
    state.suggestions = coins.slice(0, 10);
    if (!state.suggestions.length) {
      hideSuggestions();
      setStatus(`No coins match "${q}".`);
      return;
    }
    setStatus("");
    if (openFirst) return showCoin(state.suggestions[0].id);
    renderSuggestions();
  } catch (err) {
    setStatus(err.message, true);
  }
}

function renderSuggestions() {
  state.activeSuggestion = -1;
  els.suggestions.innerHTML = state.suggestions.map((c, i) => `
    <li data-index="${i}">
      <img src="${escapeHtml(c.thumb)}" alt="" width="20" height="20" />
      ${escapeHtml(c.name)} <span class="symbol">${escapeHtml(c.symbol)}</span>
      ${c.market_cap_rank ? `<span class="symbol" style="margin-left:auto">#${c.market_cap_rank}</span>` : ""}
    </li>`).join("");
  els.suggestions.hidden = false;
}

function hideSuggestions() {
  els.suggestions.hidden = true;
  state.activeSuggestion = -1;
}

function moveSuggestion(delta) {
  if (els.suggestions.hidden) return;
  const n = state.suggestions.length;
  state.activeSuggestion = (state.activeSuggestion + delta + n) % n;
  els.suggestions.querySelectorAll("li").forEach((li, i) =>
    li.classList.toggle("active", i === state.activeSuggestion));
}

// ---------- events ----------
els.input.addEventListener("input", onSearchInput);
els.input.addEventListener("keydown", (e) => {
  if (e.key === "ArrowDown") { e.preventDefault(); moveSuggestion(1); }
  else if (e.key === "ArrowUp") { e.preventDefault(); moveSuggestion(-1); }
  else if (e.key === "Escape") hideSuggestions();
});

els.form.addEventListener("submit", (e) => {
  e.preventDefault();
  const picked = state.suggestions[state.activeSuggestion];
  if (!els.suggestions.hidden && picked) return showCoin(picked.id);
  const q = els.input.value.trim();
  if (q) runSearch(q, true);
});

els.suggestions.addEventListener("click", (e) => {
  const li = e.target.closest("li");
  if (li) showCoin(state.suggestions[li.dataset.index].id);
});

document.addEventListener("click", (e) => {
  if (!els.form.contains(e.target)) hideSuggestions();
});

els.rows.addEventListener("click", (e) => {
  const star = e.target.closest("[data-star]");
  if (star) return toggleWatch(star.dataset.star);
  const row = e.target.closest("tr[data-id]");
  if (row) showCoin(row.dataset.id);
});

els.detail.addEventListener("click", (e) => {
  const star = e.target.closest("[data-star]");
  if (star) return toggleWatch(star.dataset.star);
  if (e.target.id === "close-detail") {
    els.detail.hidden = true;
    state.selectedId = null;
    return;
  }
  const range = e.target.closest("[data-days]");
  if (range) {
    state.chartDays = Number(range.dataset.days);
    els.detail.querySelectorAll("[data-days]").forEach((b) =>
      b.classList.toggle("active", b === range));
    loadChart();
  }
});

els.tabs.forEach((tab) => tab.addEventListener("click", () => {
  state.tab = tab.dataset.tab;
  els.tabs.forEach((t) => t.classList.toggle("active", t === tab));
  renderTable();
}));

els.currency.value = state.currency;
els.currency.addEventListener("change", () => {
  state.currency = els.currency.value;
  save(CURRENCY_KEY, state.currency);
  renderTable();
  if (state.selectedId) showCoin(state.selectedId);
});

window.addEventListener("resize", () => {
  clearTimeout(window.__chartResize);
  window.__chartResize = setTimeout(() => state.selectedId && loadChart(), 200);
});

renderTable();
