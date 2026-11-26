package com.priyanshu.teji

import android.net.Uri
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * On-phone version of the laptop data program (server.py).
 * Answers /api/ping, /api/quotes, /api/candles and /api/search for the web app:
 * NSE prices from Yahoo Finance, crypto prices from Binance (Yahoo as backup).
 * Works for any NSE-listed stock and any Binance USDT pair.
 */
class LiveApi {

    private val pool = Executors.newFixedThreadPool(8)
    private val yahooHosts = listOf("https://query1.finance.yahoo.com", "https://query2.finance.yahoo.com")
    private val binanceHosts = listOf("https://data-api.binance.vision", "https://api.binance.com", "https://api1.binance.com")
    @Volatile private var binanceIdx = 0

    private val index = mapOf("NIFTY" to "^NSEI", "BANKNIFTY" to "^NSEBANK")
    private val defaultStocks = listOf("NIFTY", "BANKNIFTY", "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "SBIN", "ITC", "LT", "BHARTIARTL", "ETERNAL")
    private val defaultCrypto = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "LTCUSDT", "AVAXUSDT", "LINKUSDT")
    private val yahooTf = mapOf(
        "1m" to Pair("1m", "5d"), "5m" to Pair("5m", "1mo"), "15m" to Pair("15m", "1mo"),
        "1H" to Pair("60m", "6mo"), "1D" to Pair("1d", "2y")
    )
    private val binanceTf = mapOf("1m" to "1m", "5m" to "5m", "15m" to "15m", "1H" to "1h", "1D" to "1d")
    private val stockRe = Regex("^[A-Z0-9&\\-]{1,25}$")
    private val cryptoRe = Regex("^[A-Z0-9]{2,20}USDT$")

    private class Cached(val t: Long, val v: Any)
    private val cache = ConcurrentHashMap<String, Cached>()

    fun shutdown() {
        pool.shutdownNow()
    }

    private fun ysym(sym: String) = index[sym] ?: "$sym.NS"
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ---------- request routing ----------
    fun handle(path: String, uri: Uri): WebResourceResponse {
        val crypto = uri.getQueryParameter("part") == "crypto"
        val re = if (crypto) cryptoRe else stockRe
        return try {
            when (path) {
                "/api/ping" -> json("""{"teji":true,"version":4,"app":true,"crypto":true,"search":true}""", 200)
                "/api/quotes" -> {
                    val hot = syms(uri.getQueryParameter("hot"), re)
                    val cold = syms(uri.getQueryParameter("cold"), re).filter { it !in hot }
                    json(if (crypto) cryptoQuotes(hot + cold) else stockQuotes(hot, cold), 200)
                }
                "/api/candles" -> {
                    val sym = (uri.getQueryParameter("symbol") ?: "").uppercase()
                    val tf = uri.getQueryParameter("tf") ?: ""
                    if (!re.matches(sym) || !yahooTf.containsKey(tf)) json("""{"error":"unknown symbol or timeframe"}""", 400)
                    else json(JSONObject().put("candles", if (crypto) cryptoCandles(sym, tf) else stockCandles(sym, tf)).toString(), 200)
                }
                "/api/search" -> {
                    val q = (uri.getQueryParameter("q") ?: "").trim().take(40)
                    val res = if (q.isEmpty()) JSONArray() else if (crypto) cryptoSearch(q) else stockSearch(q)
                    json(JSONObject().put("results", res).toString(), 200)
                }
                else -> json("""{"error":"not found"}""", 404)
            }
        } catch (e: Exception) {
            json(JSONObject().put("error", (e.message ?: "request failed").take(160)).toString(), 502)
        }
    }

    private fun syms(raw: String?, re: Regex): List<String> =
        (raw ?: "").split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() && re.matches(it) }.distinct().take(60)

    private fun json(body: String, code: Int): WebResourceResponse =
        WebResourceResponse(
            "application/json", "utf-8", code, if (code == 200) "OK" else "Error",
            mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, ttlMs: Long, make: () -> T?): T? {
        val hit = cache[key]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.t < ttlMs) return hit.v as T
        val fresh = try {
            make()
        } catch (e: Exception) {
            if (hit != null) return hit.v as T
            throw e
        }
        if (fresh != null) {
            cache[key] = Cached(now, fresh)
            return fresh
        }
        return hit?.v as T?
    }

    // ---------- HTTP ----------
    private fun yahoo(path: String): String {
        var last: Exception? = null
        for (h in yahooHosts) {
            try {
                return Net.get(h + path)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Yahoo Finance not reachable")
    }

    private fun binance(path: String): String {
        var last: Exception? = null
        val n = binanceHosts.size
        for (k in 0 until n) {
            val i = (binanceIdx + k) % n
            try {
                val body = Net.get(binanceHosts[i] + path)
                binanceIdx = i
                return body
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Binance not reachable")
    }

    private fun chart(ys: String, interval: String, range: String): JSONObject {
        val d = JSONObject(yahoo("/v8/finance/chart/${enc(ys)}?interval=$interval&range=$range&includePrePost=false"))
        val res = d.optJSONObject("chart")?.optJSONArray("result")
        if (res == null || res.length() == 0) throw IOException("no data for $ys")
        return res.getJSONObject(0)
    }

    // ---------- parsing ----------
    private fun closesOf(res: JSONObject): List<Double> {
        val q = res.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0) ?: return emptyList()
        val arr = q.optJSONArray("close") ?: return emptyList()
        val out = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) if (!arr.isNull(i)) out.add(arr.getDouble(i))
        return out
    }

    private fun sparkOf(list: List<Double>): JSONArray {
        val a = JSONArray()
        list.takeLast(75).forEach { a.put(it) }
        return a
    }

    private fun quoteFromChart(res: JSONObject): JSONObject? {
        val meta = res.optJSONObject("meta") ?: JSONObject()
        val closes = closesOf(res)
        var price = meta.optDouble("regularMarketPrice", Double.NaN)
        if (price.isNaN() || price <= 0) price = closes.lastOrNull() ?: return null
        var prev = meta.optDouble("chartPreviousClose", Double.NaN)
        if (prev.isNaN()) prev = meta.optDouble("previousClose", Double.NaN)
        var time = meta.optLong("regularMarketTime", 0L)
        if (time == 0L) {
            val ts = res.optJSONArray("timestamp")
            if (ts != null && ts.length() > 0) time = ts.optLong(ts.length() - 1, 0L)
        }
        val q = JSONObject().put("price", price).put("time", time).put("spark", sparkOf(closes))
        if (!prev.isNaN() && prev > 0) q.put("prev", prev)
        val name = meta.optString("longName", "").ifEmpty { meta.optString("shortName", "") }
        if (name.isNotEmpty()) q.put("name", name)
        return q
    }

    private fun candlesFromChart(res: JSONObject): JSONArray {
        val out = JSONArray()
        val ts = res.optJSONArray("timestamp") ?: return out
        val q = res.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0) ?: return out
        val o = q.optJSONArray("open"); val h = q.optJSONArray("high"); val l = q.optJSONArray("low")
        val c = q.optJSONArray("close"); val v = q.optJSONArray("volume")
        if (o == null || h == null || l == null || c == null) return out
        for (i in 0 until ts.length()) {
            if (i >= c.length() || o.isNull(i) || h.isNull(i) || l.isNull(i) || c.isNull(i)) continue
            val vol = if (v != null && i < v.length() && !v.isNull(i)) v.getDouble(i) else 0.0
            out.put(
                JSONObject().put("t", ts.getLong(i) * 1000).put("o", o.getDouble(i)).put("h", h.getDouble(i))
                    .put("l", l.getDouble(i)).put("c", c.getDouble(i)).put("v", vol)
            )
        }
        return out
    }

    fun marketOpenNow(): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY && mins >= 9 * 60 + 15 && mins < 15 * 60 + 30
    }

    private fun marketState(latestTs: Long): String {
        val fresh = latestTs > 0 && (System.currentTimeMillis() / 1000 - latestTs) < 15 * 60
        return if (marketOpenNow() && fresh) "OPEN" else "CLOSED"
    }

    // ---------- stocks ----------
    private fun stockQuote(sym: String, ttl: Long): JSONObject? =
        cached("sq:$sym", ttl) { quoteFromChart(chart(ysym(sym), "5m", "1d")) }

    /** Yahoo spark: up to 15 symbols per request. Returns app symbol -> quote. */
    private fun sparkBatch(list: List<String>): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        for (chunk in list.chunked(15)) {
            val back = chunk.associateBy { ysym(it) }
            val body = yahoo("/v8/finance/spark?symbols=${enc(chunk.joinToString(",") { ysym(it) })}&range=1d&interval=5m")
            val d = JSONObject(body)
            if (d.has("spark")) {
                val res = d.optJSONObject("spark")?.optJSONArray("result") ?: JSONArray()
                for (i in 0 until res.length()) {
                    val item = res.getJSONObject(i)
                    val app = back[item.optString("symbol")] ?: continue
                    val resp = item.optJSONArray("response")?.optJSONObject(0) ?: continue
                    quoteFromChart(resp)?.let { out[app] = it }
                }
            } else {
                val keys = d.keys()
                while (keys.hasNext()) {
                    val ys = keys.next()
                    val app = back[ys] ?: continue
                    val o = d.optJSONObject(ys) ?: continue
                    val arr = o.optJSONArray("close") ?: continue
                    val closes = ArrayList<Double>()
                    for (i in 0 until arr.length()) if (!arr.isNull(i)) closes.add(arr.getDouble(i))
                    if (closes.isEmpty()) continue
                    val ts = o.optJSONArray("timestamp")
                    val q = JSONObject().put("price", closes.last()).put("spark", sparkOf(closes))
                        .put("time", if (ts != null && ts.length() > 0) ts.optLong(ts.length() - 1, 0L) else 0L)
                    val prev = o.optDouble("chartPreviousClose", o.optDouble("previousClose", Double.NaN))
                    if (!prev.isNaN() && prev > 0) q.put("prev", prev)
                    out[app] = q
                }
            }
        }
        return out
    }

    private fun stockQuotes(hotIn: List<String>, coldIn: List<String>): String {
        val hot = hotIn
        val cold = if (hotIn.isEmpty() && coldIn.isEmpty()) defaultStocks else coldIn
        val quotes = JSONObject()
        val errors: MutableList<String> = java.util.Collections.synchronizedList(ArrayList<String>())
        val hotF = hot.map { s -> pool.submit<Pair<String, JSONObject?>> { try { Pair(s, stockQuote(s, 1500)) } catch (e: Exception) { errors.add("$s: ${e.message}"); Pair(s, null) } } }
        for (f in hotF) {
            val r = try { f.get(15, TimeUnit.SECONDS) } catch (e: Exception) { null } ?: continue
            r.second?.let { quotes.put(r.first, it) }
        }
        val stale = ArrayList<String>()
        val now = System.currentTimeMillis()
        for (s in cold) {
            if (quotes.has(s)) continue
            val hit = cache["cq:$s"]
            if (hit != null && now - hit.t < 10000) quotes.put(s, hit.v as JSONObject) else stale.add(s)
        }
        if (stale.isNotEmpty()) {
            val got = try { sparkBatch(stale) } catch (e: Exception) { errors.add("spark: ${e.message}"); emptyMap() }
            got.forEach { (s, q) -> cache["cq:$s"] = Cached(System.currentTimeMillis(), q); quotes.put(s, q) }
            val missing = stale.filter { it !in got }.take(12)
            val mf = missing.map { s -> pool.submit<Pair<String, JSONObject?>> { try { Pair(s, stockQuote(s, 10000)) } catch (e: Exception) { errors.add("$s: ${e.message}"); Pair(s, null) } } }
            for (f in mf) {
                val r = try { f.get(15, TimeUnit.SECONDS) } catch (e: Exception) { null } ?: continue
                r.second?.let { quotes.put(r.first, it) }
            }
        }
        var latest = quotes.optJSONObject("NIFTY")?.optLong("time", 0L) ?: 0L
        if (latest == 0L) {
            val keys = quotes.keys()
            while (keys.hasNext()) latest = maxOf(latest, quotes.getJSONObject(keys.next()).optLong("time", 0L))
        }
        val out = JSONObject().put("state", marketState(latest)).put("asOf", System.currentTimeMillis())
            .put("quotes", quotes).put("errors", errors.size).put("source", "Yahoo Finance")
            .put("engine", if (Net.usingChromeEngine) "chrome" else "java")
        if (quotes.length() == 0) out.put("err", (Net.lastError.ifEmpty { errors.firstOrNull() ?: "no prices" }).take(160))
        return out.toString()
    }

    private fun stockCandles(sym: String, tf: String): JSONArray {
        val (interval, range) = yahooTf.getValue(tf)
        return cached("sc:$sym:$tf", 20000) { candlesFromChart(chart(ysym(sym), interval, range)) } ?: JSONArray()
    }

    private fun stockSearch(q: String): JSONArray {
        val out = JSONArray()
        val seen = HashSet<String>()
        try {
            val d = JSONObject(yahoo("/v1/finance/search?q=${enc(q)}&quotesCount=25&newsCount=0&listsCount=0&enableFuzzyQuery=false"))
            val arr = d.optJSONArray("quotes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val s = it.optString("symbol")
                val type = it.optString("quoteType")
                if (!s.endsWith(".NS") || (type != "EQUITY" && type != "ETF")) continue
                val app = s.removeSuffix(".NS")
                if (!seen.add(app)) continue
                val name = it.optString("longname", "").ifEmpty { it.optString("shortname", app) }
                out.put(JSONObject().put("sym", app).put("name", name).put("exch", "NSE"))
            }
        } catch (e: Exception) {
            // fall back to the direct symbol check below
        }
        val guess = q.uppercase().replace(" ", "")
        if (stockRe.matches(guess) && guess !in seen) {
            try {
                val qq = quoteFromChart(chart(ysym(guess), "1d", "5d"))
                if (qq != null) {
                    val res = JSONArray().put(JSONObject().put("sym", guess).put("name", qq.optString("name", guess)).put("exch", "NSE").put("price", qq.getDouble("price")))
                    for (i in 0 until out.length()) res.put(out.get(i))
                    return res
                }
            } catch (e: Exception) {
                // not a valid NSE symbol
            }
        }
        return out
    }

    // ---------- crypto ----------
    private fun spark(sym: String): JSONArray =
        try {
            cached("cs:$sym", 60000) {
                val rows = JSONArray(binance("/api/v3/klines?symbol=$sym&interval=15m&limit=96"))
                val a = JSONArray()
                for (i in 0 until rows.length()) a.put(rows.getJSONArray(i).getString(4).toDouble())
                a
            } ?: JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }

    private fun cryptoQuotes(symsIn: List<String>): String {
        val list = symsIn.ifEmpty { defaultCrypto }
        val body = cached("cq:" + list.joinToString(","), 1500) {
            val quotes = JSONObject()
            var source = "Binance"
            try {
                val rows = JSONArray(binance("/api/v3/ticker/24hr?symbols=${enc(JSONArray(list).toString())}"))
                val sparks = list.take(12).map { s -> pool.submit<Pair<String, JSONArray>> { Pair(s, spark(s)) } }
                    .mapNotNull { try { it.get(10, TimeUnit.SECONDS) } catch (e: Exception) { null } }.toMap()
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    val s = r.optString("symbol")
                    if (s !in list) continue
                    quotes.put(s, JSONObject()
                        .put("price", r.getString("lastPrice").toDouble())
                        .put("prev", r.getString("openPrice").toDouble())
                        .put("time", r.optLong("closeTime", System.currentTimeMillis()) / 1000)
                        .put("spark", sparks[s] ?: JSONArray()))
                }
            } catch (e: Exception) {
                source = "Yahoo Finance"
                val fs = list.take(20).map { s ->
                    pool.submit<Pair<String, JSONObject?>> {
                        try { Pair(s, quoteFromChart(chart(s.removeSuffix("USDT") + "-USD", "15m", "1d"))) } catch (x: Exception) { Pair(s, null) }
                    }
                }
                for (f in fs) {
                    val r = try { f.get(15, TimeUnit.SECONDS) } catch (x: Exception) { null } ?: continue
                    r.second?.let { quotes.put(r.first, it) }
                }
            }
            if (quotes.length() == 0) null
            else JSONObject().put("state", "OPEN").put("asOf", System.currentTimeMillis()).put("quotes", quotes).put("source", source).toString()
        }
        return body ?: JSONObject().put("state", "OPEN").put("quotes", JSONObject()).put("err", Net.lastError.ifEmpty { "no crypto prices" }).toString()
    }

    private fun cryptoCandles(sym: String, tf: String): JSONArray =
        cached("cc:$sym:$tf", 10000) {
            val out = JSONArray()
            try {
                val rows = JSONArray(binance("/api/v3/klines?symbol=$sym&interval=${binanceTf.getValue(tf)}&limit=1000"))
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONArray(i)
                    out.put(JSONObject().put("t", r.getLong(0)).put("o", r.getString(1).toDouble())
                        .put("h", r.getString(2).toDouble()).put("l", r.getString(3).toDouble())
                        .put("c", r.getString(4).toDouble()).put("v", r.getString(5).toDouble()))
                }
            } catch (e: Exception) {
                val (interval, range) = yahooTf.getValue(tf)
                val res = candlesFromChart(chart(sym.removeSuffix("USDT") + "-USD", interval, range))
                for (i in 0 until res.length()) out.put(res.get(i))
            }
            out
        } ?: JSONArray()

    private fun cryptoSearch(qIn: String): JSONArray {
        val q = qIn.uppercase().replace("/", "").replace(" ", "").removeSuffix("USDT")
        val pairs = cached("pairs", 3600000) {
            val rows = JSONArray(binance("/api/v3/ticker/price"))
            val l = ArrayList<Pair<String, Double>>()
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val s = r.optString("symbol")
                val p = r.optString("price", "0").toDoubleOrNull() ?: 0.0
                if (p > 0 && cryptoRe.matches(s)) l.add(Pair(s, p))
            }
            l
        } ?: arrayListOf()
        val starts = pairs.filter { it.first.startsWith(q) }
        val contains = pairs.filter { it.first.removeSuffix("USDT").contains(q) && !it.first.startsWith(q) }
        val out = JSONArray()
        (starts + contains).take(30).forEach {
            out.put(JSONObject().put("sym", it.first).put("name", it.first.removeSuffix("USDT")).put("exch", "Binance").put("price", it.second))
        }
        return out
    }

    // ---------- used by the background order watcher ----------
    fun lastPrices(part: String, symbols: List<String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        if (symbols.isEmpty()) return out
        if (part == "crypto") {
            val rows = JSONArray(binance("/api/v3/ticker/price?symbols=${enc(JSONArray(symbols).toString())}"))
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                r.optString("price").toDoubleOrNull()?.let { out[r.optString("symbol")] = it }
            }
        } else {
            val fs = symbols.map { s -> pool.submit<Pair<String, Double?>> { try { Pair(s, stockQuote(s, 1500)?.optDouble("price")) } catch (e: Exception) { Pair(s, null) } } }
            for (f in fs) {
                val r = try { f.get(15, TimeUnit.SECONDS) } catch (e: Exception) { null } ?: continue
                r.second?.let { if (!it.isNaN()) out[r.first] = it }
            }
        }
        return out
    }
}
