package com.priyanshu.teji

import android.net.Uri
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * On-phone version of the laptop data program (server.py).
 * Answers /api/ping, /api/quotes and /api/candles for the web app:
 * NSE prices from Yahoo Finance, crypto prices from Binance (Yahoo as backup).
 */
class LiveApi {

    private val pool = Executors.newFixedThreadPool(6)
    private val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private val yahooHosts = listOf("https://query1.finance.yahoo.com", "https://query2.finance.yahoo.com")
    private val binanceHosts = listOf("https://data-api.binance.vision", "https://api.binance.com", "https://api1.binance.com")
    @Volatile private var binanceIdx = 0

    private val stocks = linkedMapOf(
        "NIFTY" to "^NSEI", "BANKNIFTY" to "^NSEBANK", "RELIANCE" to "RELIANCE.NS", "TCS" to "TCS.NS",
        "HDFCBANK" to "HDFCBANK.NS", "INFY" to "INFY.NS", "ICICIBANK" to "ICICIBANK.NS", "SBIN" to "SBIN.NS",
        "ITC" to "ITC.NS", "LT" to "LT.NS", "BHARTIARTL" to "BHARTIARTL.NS", "ETERNAL" to "ETERNAL.NS"
    )
    private val crypto = linkedMapOf(
        "BTCUSDT" to "BTC-USD", "ETHUSDT" to "ETH-USD", "SOLUSDT" to "SOL-USD", "BNBUSDT" to "BNB-USD",
        "XRPUSDT" to "XRP-USD", "LTCUSDT" to "LTC-USD", "AVAXUSDT" to "AVAX-USD", "LINKUSDT" to "LINK-USD"
    )
    private val yahooTf = mapOf(
        "1m" to Pair("1m", "5d"), "5m" to Pair("5m", "1mo"), "15m" to Pair("15m", "1mo"),
        "1H" to Pair("60m", "6mo"), "1D" to Pair("1d", "2y")
    )
    private val binanceTf = mapOf("1m" to "1m", "5m" to "5m", "15m" to "15m", "1H" to "1h", "1D" to "1d")

    private class Cached(val t: Long, val body: String)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val sparkCache = ConcurrentHashMap<String, Pair<Long, JSONArray>>()

    fun shutdown() {
        pool.shutdownNow()
    }

    fun handle(path: String, uri: Uri): WebResourceResponse {
        val part = uri.getQueryParameter("part") ?: "stocks"
        return try {
            when (path) {
                "/api/ping" -> json("""{"teji":true,"version":3,"app":true,"crypto":true}""", 200)
                "/api/quotes" -> json(if (part == "crypto") cryptoQuotes() else stockQuotes(), 200)
                "/api/candles" -> {
                    val sym = uri.getQueryParameter("symbol") ?: ""
                    val tf = uri.getQueryParameter("tf") ?: ""
                    val ok = yahooTf.containsKey(tf) && (if (part == "crypto") crypto.containsKey(sym) else stocks.containsKey(sym))
                    if (!ok) json("""{"error":"unknown symbol or timeframe"}""", 400)
                    else json(if (part == "crypto") cryptoCandles(sym, tf) else stockCandles(sym, tf), 200)
                }
                else -> json("""{"error":"not found"}""", 404)
            }
        } catch (e: Exception) {
            json(JSONObject().put("error", e.message ?: "request failed").toString(), 502)
        }
    }

    private fun json(body: String, code: Int): WebResourceResponse =
        WebResourceResponse(
            "application/json", "utf-8", code, if (code == 200) "OK" else "Error",
            mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        )

    private inline fun cached(key: String, ttlMs: Long, make: () -> String?): String {
        val hit = cache[key]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.t < ttlMs) return hit.body
        val fresh = make()
        if (fresh != null) {
            cache[key] = Cached(now, fresh)
            return fresh
        }
        if (hit != null) return hit.body
        throw IOException("no data")
    }

    // ---------- HTTP ----------
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun yahoo(path: String): JSONObject {
        var last: Exception? = null
        for (h in yahooHosts) {
            try {
                return JSONObject(get(h + path))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("yahoo failed")
    }

    private fun binance(path: String): String {
        var last: Exception? = null
        val n = binanceHosts.size
        for (k in 0 until n) {
            val i = (binanceIdx + k) % n
            try {
                val body = get(binanceHosts[i] + path)
                binanceIdx = i
                return body
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("binance failed")
    }

    private fun chart(ysym: String, interval: String, range: String): JSONObject {
        val d = yahoo("/v8/finance/chart/${URLEncoder.encode(ysym, "UTF-8")}?interval=$interval&range=$range&includePrePost=false")
        val res = d.optJSONObject("chart")?.optJSONArray("result")
        if (res == null || res.length() == 0) throw IOException("no data for $ysym")
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
        val spark = JSONArray()
        closes.takeLast(75).forEach { spark.put(it) }
        val q = JSONObject().put("price", price).put("time", time).put("spark", spark)
        if (!prev.isNaN() && prev > 0) q.put("prev", prev)
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

    private fun marketState(latestTs: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val weekday = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
        val inHours = weekday && mins >= 9 * 60 + 15 && mins < 15 * 60 + 30
        val fresh = latestTs > 0 && (System.currentTimeMillis() / 1000 - latestTs) < 15 * 60
        return if (inHours && fresh) "OPEN" else "CLOSED"
    }

    // ---------- stocks ----------
    private fun stockQuotes(): String = cached("q:stocks", 4000) {
        val futures = stocks.map { (sym, y) ->
            pool.submit<Pair<String, JSONObject?>> {
                try {
                    Pair(sym, quoteFromChart(chart(y, "5m", "1d")))
                } catch (e: Exception) {
                    Pair(sym, null)
                }
            }
        }
        val quotes = JSONObject()
        var latest = 0L
        var errors = 0
        for (f in futures) {
            val r = try { f.get(15, TimeUnit.SECONDS) } catch (e: Exception) { null }
            val q = r?.second
            if (r != null && q != null) {
                quotes.put(r.first, q)
                val t = q.optLong("time", 0L)
                if (r.first == "NIFTY" && t > 0) latest = t
            } else errors++
        }
        if (latest == 0L) {
            val keys = quotes.keys()
            while (keys.hasNext()) latest = maxOf(latest, quotes.getJSONObject(keys.next()).optLong("time", 0L))
        }
        if (quotes.length() == 0) null
        else JSONObject().put("state", marketState(latest)).put("asOf", System.currentTimeMillis())
            .put("quotes", quotes).put("errors", errors).put("source", "Yahoo Finance").toString()
    }

    private fun stockCandles(sym: String, tf: String): String = cached("c:$sym:$tf", 20000) {
        val (interval, range) = yahooTf.getValue(tf)
        JSONObject().put("candles", candlesFromChart(chart(stocks.getValue(sym), interval, range))).toString()
    }

    // ---------- crypto ----------
    private fun spark(sym: String): JSONArray {
        val hit = sparkCache[sym]
        if (hit != null && System.currentTimeMillis() - hit.first < 60000) return hit.second
        val out = JSONArray()
        try {
            val rows = JSONArray(binance("/api/v3/klines?symbol=$sym&interval=15m&limit=96"))
            for (i in 0 until rows.length()) out.put(rows.getJSONArray(i).getString(4).toDouble())
        } catch (e: Exception) {
            if (hit != null) return hit.second
        }
        sparkCache[sym] = Pair(System.currentTimeMillis(), out)
        return out
    }

    private fun cryptoQuotes(): String = cached("q:crypto", 1500) {
        val quotes = JSONObject()
        var source = "Binance"
        try {
            val syms = URLEncoder.encode(JSONArray(crypto.keys.toList()).toString(), "UTF-8")
            val rows = JSONArray(binance("/api/v3/ticker/24hr?symbols=$syms"))
            val sparks = crypto.keys.map { s -> pool.submit<Pair<String, JSONArray>> { Pair(s, spark(s)) } }
                .mapNotNull { try { it.get(10, TimeUnit.SECONDS) } catch (e: Exception) { null } }.toMap()
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val sym = r.optString("symbol")
                if (!crypto.containsKey(sym)) continue
                quotes.put(
                    sym, JSONObject()
                        .put("price", r.getString("lastPrice").toDouble())
                        .put("prev", r.getString("openPrice").toDouble())
                        .put("time", r.optLong("closeTime", System.currentTimeMillis()) / 1000)
                        .put("spark", sparks[sym] ?: JSONArray())
                )
            }
        } catch (e: Exception) {
            source = "Yahoo Finance"
            val futures = crypto.map { (sym, y) ->
                pool.submit<Pair<String, JSONObject?>> {
                    try { Pair(sym, quoteFromChart(chart(y, "15m", "1d"))) } catch (x: Exception) { Pair(sym, null) }
                }
            }
            for (f in futures) {
                val r = try { f.get(15, TimeUnit.SECONDS) } catch (x: Exception) { null }
                val q = r?.second
                if (r != null && q != null) quotes.put(r.first, q)
            }
        }
        if (quotes.length() == 0) null
        else JSONObject().put("state", "OPEN").put("asOf", System.currentTimeMillis())
            .put("quotes", quotes).put("source", source).toString()
    }

    private fun cryptoCandles(sym: String, tf: String): String = cached("cc:$sym:$tf", 10000) {
        val out = JSONArray()
        try {
            val rows = JSONArray(binance("/api/v3/klines?symbol=$sym&interval=${binanceTf.getValue(tf)}&limit=1000"))
            for (i in 0 until rows.length()) {
                val r = rows.getJSONArray(i)
                out.put(
                    JSONObject().put("t", r.getLong(0)).put("o", r.getString(1).toDouble())
                        .put("h", r.getString(2).toDouble()).put("l", r.getString(3).toDouble())
                        .put("c", r.getString(4).toDouble()).put("v", r.getString(5).toDouble())
                )
            }
        } catch (e: Exception) {
            val (interval, range) = yahooTf.getValue(tf)
            val res = candlesFromChart(chart(crypto.getValue(sym), interval, range))
            for (i in 0 until res.length()) out.put(res.get(i))
        }
        JSONObject().put("candles", out).toString()
    }
}
