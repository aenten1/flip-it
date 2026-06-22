/*
 * Copyright (c) 2026, ace_554
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.flipit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Sources prices from the OSRS Wiki real-time price API. Buy/sell prices are an
 * N-point moving average over the cached per-item 5-minute timeseries; volatility
 * is the coefficient of variation over a configurable recent window.
 */
@Slf4j
public class WikiPriceManager
{
	private static final String BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";
	private static final String USER_AGENT = "osrs-flip-plugin";
	private static final String SERIES_TIMESTEP = "5m";
	private static final long SERIES_FRESH_MS = 5 * 60 * 1000L; // re-fetch only when older than the 5m step
	private static final int SERIES_KEEP = 60;                  // trim to ~5h of 5m points
	private static final int POINTS_PER_HOUR = 12;              // 5-minute steps
	private static final int MIN_VOLATILITY_SAMPLES = 4;        // below this, volatility isn't meaningful

	private final OkHttpClient okHttpClient;
	private final Gson gson;

	private final Map<Integer, List<TimeseriesPoint>> seriesCache = new ConcurrentHashMap<>();
	private final Map<Integer, Long> lastFetchMs = new ConcurrentHashMap<>();

	public WikiPriceManager(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	/**
	 * Ensures each item's 5-minute timeseries is cached and fresh, re-fetching only
	 * when the last fetch is older than the 5-minute step (so manual refreshes and
	 * UI rebuilds are essentially free), then runs onComplete. One request per stale
	 * item; the cache is trimmed to the recent window.
	 */
	public void fetchSeries(List<Integer> itemIds, Runnable onComplete)
	{
		long now = System.currentTimeMillis();
		List<Integer> toFetch = new ArrayList<>();
		for (int id : itemIds)
		{
			Long last = lastFetchMs.get(id);
			if (last == null || now - last > SERIES_FRESH_MS || !seriesCache.containsKey(id))
			{
				toFetch.add(id);
			}
		}
		if (toFetch.isEmpty())
		{
			onComplete.run();
			return;
		}

		AtomicInteger pending = new AtomicInteger(toFetch.size());
		for (int id : toFetch)
		{
			fetchTimeseries(id, SERIES_TIMESTEP, points ->
			{
				if (!points.isEmpty())
				{
					List<TimeseriesPoint> trimmed = points.size() > SERIES_KEEP
						? new ArrayList<>(points.subList(points.size() - SERIES_KEEP, points.size()))
						: points;
					seriesCache.put(id, trimmed);
					lastFetchMs.put(id, System.currentTimeMillis());
				}
				if (pending.decrementAndGet() == 0)
				{
					onComplete.run();
				}
			});
		}
	}

	/**
	 * Fetches the wiki price timeseries for an item at the given timestep (e.g.
	 * "5m", "1h", "6h", "24h"). Runs on the OkHttp thread pool; onResult is invoked
	 * with the points in ascending time order (empty on failure). Callers should
	 * marshal back to the EDT themselves.
	 */
	public void fetchTimeseries(int itemId, String timestep, Consumer<List<TimeseriesPoint>> onResult)
	{
		HttpUrl url = HttpUrl.parse(BASE_URL + "/timeseries").newBuilder()
			.addQueryParameter("timestep", timestep)
			.addQueryParameter("id", String.valueOf(itemId))
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to fetch timeseries: {}", e.getMessage());
				onResult.accept(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				List<TimeseriesPoint> result = new ArrayList<>();
				try (Response r = response)
				{
					if (r.isSuccessful() && r.body() != null)
					{
						JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
						JsonElement dataEl = root != null ? root.get("data") : null;
						if (dataEl != null && dataEl.isJsonArray())
						{
							for (JsonElement el : dataEl.getAsJsonArray())
							{
								JsonObject o = el.getAsJsonObject();
								TimeseriesPoint p = new TimeseriesPoint();
								p.timestamp = getLongOrZero(o, "timestamp");
								p.avgHighPrice = getIntOrNull(o, "avgHighPrice");
								p.avgLowPrice = getIntOrNull(o, "avgLowPrice");
								result.add(p);
							}
						}
					}
					else
					{
						log.debug("Wiki timeseries API returned {}", r.code());
					}
				}
				catch (Exception e)
				{
					log.debug("Failed to parse timeseries: {}", e.getMessage());
				}
				onResult.accept(result);
			}
		});
	}

	public int getBuyPrice(int itemId, OsrsFlipConfig config)
	{
		Integer price = getRawPrice(itemId, true, config);
		if (price == null)
		{
			return -1;
		}
		int result = applyOffset(price, config.buyOffsetGp(), (double) config.buyOffsetPercent());
		return applyRounding(result, config.buyRoundInterval());
	}

	public int getSellPrice(int itemId, OsrsFlipConfig config)
	{
		Integer price = getRawPrice(itemId, false, config);
		if (price == null)
		{
			return -1;
		}
		int result = applyOffset(price, config.sellOffsetGp(), (double) config.sellOffsetPercent());
		return applyRounding(result, config.sellRoundInterval());
	}

	public int getRawBuyPrice(int itemId, OsrsFlipConfig config)
	{
		Integer price = getRawPrice(itemId, true, config);
		return price != null ? price : -1;
	}

	public int getRawSellPrice(int itemId, OsrsFlipConfig config)
	{
		Integer price = getRawPrice(itemId, false, config);
		return price != null ? price : -1;
	}

	/**
	 * Moving-average price: averages the last N non-null points (per Price Mode
	 * field) of the cached 5-minute series. Skips no-trade intervals and seeks back
	 * until it has N real values. N=1 yields the most recent real point.
	 */
	private Integer getRawPrice(int itemId, boolean isBuy, OsrsFlipConfig config)
	{
		List<TimeseriesPoint> series = seriesCache.get(itemId);
		if (series == null || series.isEmpty())
		{
			return null;
		}
		int n = Math.max(1, Math.min(20, config.movingAveragePoints()));
		PriceMode mode = config.priceMode();
		List<Integer> vals = new ArrayList<>();
		for (int i = series.size() - 1; i >= 0 && vals.size() < n; i--)
		{
			Integer v = fieldForMode(series.get(i), isBuy, mode);
			if (v != null)
			{
				vals.add(v);
			}
		}
		if (vals.isEmpty())
		{
			return null;
		}
		if (config.priceAggregation() == PriceAggregation.MEDIAN)
		{
			return median(vals);
		}
		long sum = 0;
		for (int v : vals)
		{
			sum += v;
		}
		return (int) Math.round((double) sum / vals.size());
	}

	private int median(List<Integer> vals)
	{
		List<Integer> sorted = new ArrayList<>(vals);
		Collections.sort(sorted);
		int m = sorted.size();
		if (m % 2 == 1)
		{
			return sorted.get(m / 2);
		}
		return (int) Math.round((sorted.get(m / 2 - 1) + sorted.get(m / 2)) / 2.0);
	}

	/**
	 * Coefficient of variation (standard deviation / mean, as a %) over the
	 * volatility window, taken as the larger of the buy-side and sell-side series.
	 * Returns 0 when there is too little data to judge.
	 */
	public double getVolatilityCv(int itemId, OsrsFlipConfig config)
	{
		List<TimeseriesPoint> series = seriesCache.get(itemId);
		if (series == null)
		{
			return 0;
		}
		int windowPoints = Math.max(1, Math.min(4, config.volatilityWindowHours())) * POINTS_PER_HOUR;
		PriceMode mode = config.priceMode();
		return Math.max(
			cvOfField(series, windowPoints, true, mode),
			cvOfField(series, windowPoints, false, mode));
	}

	private double cvOfField(List<TimeseriesPoint> series, int windowPoints, boolean isBuy, PriceMode mode)
	{
		List<Integer> vals = new ArrayList<>();
		for (int i = series.size() - 1; i >= 0 && vals.size() < windowPoints; i--)
		{
			Integer v = fieldForMode(series.get(i), isBuy, mode);
			if (v != null)
			{
				vals.add(v);
			}
		}
		if (vals.size() < MIN_VOLATILITY_SAMPLES)
		{
			return 0;
		}
		double mean = 0;
		for (int v : vals)
		{
			mean += v;
		}
		mean /= vals.size();
		if (mean == 0)
		{
			return 0;
		}
		double var = 0;
		for (int v : vals)
		{
			double d = v - mean;
			var += d * d;
		}
		var /= vals.size();
		return Math.sqrt(var) / mean * 100.0;
	}

	private Integer fieldForMode(TimeseriesPoint p, boolean isBuy, PriceMode mode)
	{
		switch (mode)
		{
			case BUY_HIGH_SELL_LOW:
				return isBuy ? p.avgHighPrice : p.avgLowPrice;
			case BUY_HIGH_SELL_HIGH:
				return p.avgHighPrice;
			case BUY_LOW_SELL_HIGH:
			default:
				return isBuy ? p.avgLowPrice : p.avgHighPrice;
		}
	}

	private int applyOffset(int price, int offsetGp, double offsetPercent)
	{
		double result = price + offsetGp;
		if (offsetPercent != 0.0)
		{
			result += price * (offsetPercent / 100.0);
		}
		return Math.max(0, (int) Math.round(result));
	}

	/**
	 * Rounds price to nearest interval. Positive interval = round up, negative = round down.
	 */
	private int applyRounding(int price, int interval)
	{
		if (interval == 0 || price <= 0)
		{
			return price;
		}
		int abs = Math.abs(interval);
		if (interval > 0)
		{
			return ((price + abs - 1) / abs) * abs;
		}
		else
		{
			return (price / abs) * abs;
		}
	}

	public boolean hasPrice(int itemId)
	{
		List<TimeseriesPoint> s = seriesCache.get(itemId);
		return s != null && !s.isEmpty();
	}

	private Integer getIntOrNull(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		if (el == null || el.isJsonNull())
		{
			return null;
		}
		return el.getAsInt();
	}

	private long getLongOrZero(JsonObject obj, String key)
	{
		JsonElement el = obj.get(key);
		if (el == null || el.isJsonNull())
		{
			return 0;
		}
		return el.getAsLong();
	}

	static class TimeseriesPoint
	{
		long timestamp;       // unix seconds
		Integer avgHighPrice; // sell side
		Integer avgLowPrice;  // buy side
	}
}
