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
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Flip It",
	description = "Monitor item prices and calculate profitable flips with combination item support",
	tags = {"grand exchange", "flipping", "profit", "prices"}
)
public class OsrsFlipPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OsrsFlipConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	private NavigationButton navButton;
	private OsrsFlipPanel panel;
	private ManualPriceManager manualPriceManager;
	private CombinationRecipeManager recipeManager;
	private DataManager dataManager;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> autoRefreshTask;

	@Override
	protected void startUp()
	{
		manualPriceManager = new ManualPriceManager();
		ProfitCalculator profitCalculator = new ProfitCalculator();
		recipeManager = new CombinationRecipeManager();
		dataManager = new DataManager(gson);
		WikiPriceManager wikiPriceManager = new WikiPriceManager(okHttpClient, gson);

		panel = new OsrsFlipPanel(
			itemManager,
			clientThread,
			manualPriceManager,
			profitCalculator,
			recipeManager,
			dataManager,
			wikiPriceManager,
			config
		);

		// Load saved data
		dataManager.load(panel.getWatchlist(), panel.getWatchlistOffsets(), manualPriceManager, recipeManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/flipit/icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Flip It")
			.icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		panel.refreshWatchlist();
		panel.refreshCombinations();

		// Start auto-refresh timer
		executor = Executors.newSingleThreadScheduledExecutor();
		scheduleAutoRefresh();

		log.debug("OSRS Flip Plugin started!");
	}

	@Override
	protected void shutDown()
	{
		if (autoRefreshTask != null)
		{
			autoRefreshTask.cancel(false);
		}
		executor.shutdownNow();

		dataManager.save(panel.getWatchlist(), panel.getWatchlistOffsets(), manualPriceManager, recipeManager);
		clientToolbar.removeNavigation(navButton);
		log.debug("OSRS Flip Plugin stopped!");
	}

	private void scheduleAutoRefresh()
	{
		if (autoRefreshTask != null)
		{
			autoRefreshTask.cancel(false);
		}

		if (!config.autoRefresh())
		{
			return;
		}

		int minutes = Math.max(1, config.autoRefreshMinutes());
		autoRefreshTask = executor.scheduleAtFixedRate(() ->
		{
			if (config.autoRefresh())
			{
				log.debug("Auto-refreshing prices (every {} min)", minutes);
				panel.refreshWatchlist();
				panel.refreshCombinations();
			}
		}, minutes, minutes, TimeUnit.MINUTES);
	}

	@Provides
	OsrsFlipConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsFlipConfig.class);
	}
}
