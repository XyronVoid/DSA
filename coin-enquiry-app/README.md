# Coin Enquiry

A small web app for looking up live cryptocurrency prices, built with plain HTML, CSS and JavaScript. It has no build step and needs no API key. Data comes from the free [CoinGecko API](https://www.coingecko.com/en/api).

## Features

- **Search** any coin by name or symbol, with live suggestions (use ↑/↓ and Enter to pick one).
- **Coin details:** price, 24h/7d/30d change, market cap, volume, 24h high/low, supply and all-time high.
- **Price chart** for 24h, 7d, 30d or 1y.
- **Top 50** coins by market cap.
- **Watchlist:** star coins to keep them handy (saved in your browser).
- **Currencies:** USD, INR, EUR, GBP and JPY.
- Light and dark themes that follow your system setting, and a layout that works on phones.

## Run it

Open `index.html` in a browser, or serve the folder:

```sh
cd coin-enquiry-app
python3 -m http.server 8000
# then visit http://localhost:8000
```

Responses are cached for 60 seconds to stay within CoinGecko's free-tier rate limit. If you hit the limit, the app shows a message and you can retry after a minute.
