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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
public class OsrsFlipPanel extends PluginPanel
{
	private static final NumberFormat GP_FORMAT = NumberFormat.getInstance();
	private static final int SEARCH_DELAY_MS = 300;
	private static final int TIMER_ICON_SIZE = 12;
	private static final int TIMER_ICON_STEPS = 13;
	private static final Color VOLATILE_COLOR = new Color(255, 150, 0);
	private final ImageIcon[] timerIcons = new ImageIcon[TIMER_ICON_STEPS];

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final ManualPriceManager manualPriceManager;
	private final ProfitCalculator profitCalculator;
	private final CombinationRecipeManager recipeManager;
	private final DataManager dataManager;
	private final WikiPriceManager wikiPriceManager;
	private final OsrsFlipConfig config;

	@Getter
	private final List<Integer> watchlist = new ArrayList<>();
	@Getter
	private final Map<Integer, Boolean> watchlistOffsets = new ConcurrentHashMap<>();
	private final Map<Integer, String> nameCache = new ConcurrentHashMap<>();
	private final Map<Integer, int[]> priceCache = new ConcurrentHashMap<>(); // [buy, sell, rawBuy, rawSell]
	private final Map<Integer, Double> volatilityCache = new ConcurrentHashMap<>(); // CV % per item (wiki only)

	private JPanel watchlistPanel;
	private JPanel combinationsPanel;
	private JTextField searchField;
	private JLabel searchStatus;
	private JLabel refreshCountdown;
	private Timer searchTimer;
	private Timer countdownTimer;
	private long lastRefreshTime;

	public OsrsFlipPanel(
		ItemManager itemManager,
		ClientThread clientThread,
		ManualPriceManager manualPriceManager,
		ProfitCalculator profitCalculator,
		CombinationRecipeManager recipeManager,
		DataManager dataManager,
		WikiPriceManager wikiPriceManager,
		OsrsFlipConfig config)
	{
		super(false);
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.manualPriceManager = manualPriceManager;
		this.profitCalculator = profitCalculator;
		this.recipeManager = recipeManager;
		this.dataManager = dataManager;
		this.wikiPriceManager = wikiPriceManager;
		this.config = config;

		// Generate stopwatch icons with clockwise depletion
		for (int i = 0; i < TIMER_ICON_STEPS; i++)
		{
			double fillPct = 1.0 - ((double) i / (TIMER_ICON_STEPS - 1));
			timerIcons[i] = new ImageIcon(createTimerIcon(fillPct));
		}

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildTabs(), BorderLayout.CENTER);
	}

	/**
	 * Creates a stopwatch-shaped icon with a pie fill depleting clockwise.
	 * @param fill 1.0 = full, 0.0 = empty
	 */
	private BufferedImage createTimerIcon(double fill)
	{
		int s = TIMER_ICON_SIZE;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int cx = s / 2;
		int r = s / 2 - 2; // radius for clock face
		int top = 2; // top offset for face

		// Stopwatch nub on top
		g.setColor(new Color(180, 180, 180));
		g.fillRect(cx - 1, 0, 2, 2);

		// Clock face outline
		g.setColor(new Color(120, 120, 120));
		g.fillOval(cx - r - 1, top, r * 2 + 2, r * 2 + 2);

		// Clock face background
		g.setColor(new Color(60, 60, 60));
		g.fillOval(cx - r, top + 1, r * 2, r * 2);

		// Fill arc (clockwise from 12 o'clock)
		if (fill > 0.01)
		{
			g.setColor(new Color(100, 200, 255));
			int arcAngle = (int) (fill * 360);
			// startAngle 90 = 12 o'clock, positive = counter-clockwise in Java
			// so we go negative to draw clockwise depletion
			g.fillArc(cx - r, top + 1, r * 2, r * 2, 90, arcAngle);
		}

		g.dispose();
		return img;
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(0, 5));
		header.setBorder(new EmptyBorder(5, 0, 5, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Flip It");
		title.setForeground(Color.WHITE);
		titleRow.add(title, BorderLayout.WEST);

		refreshCountdown = new JLabel();
		refreshCountdown.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		titleRow.add(refreshCountdown, BorderLayout.EAST);

		header.add(titleRow, BorderLayout.NORTH);

		// Start countdown ticker
		lastRefreshTime = System.currentTimeMillis();
		countdownTimer = new Timer(1000, e -> updateCountdown());
		countdownTimer.start();

		JPanel searchRow = new JPanel(new BorderLayout(5, 0));
		searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchField = new JTextField();
		searchField.setToolTipText("Search by name or item ID");

		// Debounced search on keystrokes
		searchTimer = new Timer(SEARCH_DELAY_MS, e -> triggerSearch());
		searchTimer.setRepeats(false);

		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					searchTimer.stop();
					addItemFromSearch();
				}
				else
				{
					searchTimer.restart();
				}
			}
		});
		searchRow.add(searchField, BorderLayout.CENTER);

		JButton addButton = new JButton("Add");
		addButton.addActionListener(e -> addItemFromSearch());
		searchRow.add(addButton, BorderLayout.EAST);

		header.add(searchRow, BorderLayout.CENTER);

		searchStatus = new JLabel("Search by name or item ID");
		searchStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(searchStatus, BorderLayout.SOUTH);

		return header;
	}

	/**
	 * Triggers a name search on the client thread and shows a dropdown.
	 */
	private void triggerSearch()
	{
		String text = searchField.getText().trim();
		if (text.isEmpty())
		{
			searchStatus.setText("Search by name or item ID");
			searchStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}

		// If numeric, resolve the name
		try
		{
			int itemId = Integer.parseInt(text);
			if (itemId > 0)
			{
				clientThread.invokeLater(() ->
				{
					String name = resolveItemName(itemId);
					SwingUtilities.invokeLater(() ->
					{
						if (name != null)
						{
							searchStatus.setText(name + " (#" + itemId + ")");
							searchStatus.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
						}
						else
						{
							searchStatus.setText("Unknown item ID: " + itemId);
							searchStatus.setForeground(Color.RED);
						}
					});
				});
			}
			return;
		}
		catch (NumberFormatException ignored)
		{
		}

		// Name search (in-memory via itemManager.search — no client thread needed)
		List<int[]> results = searchItemsByName(text);
		showSearchResults(results);
	}

	/**
	 * Searches tradeable items by name via itemManager.search() — an in-memory
	 * lookup that does NOT touch the client thread. Names are cached in nameCache
	 * as a side effect. Returns up to config.maxSearchResults() entries as [itemId].
	 */
	private List<int[]> searchItemsByName(String query)
	{
		List<int[]> results = new ArrayList<>();
		String searchTerm = query.trim();
		if (searchTerm.isEmpty())
		{
			return results;
		}

		int max = Math.max(1, config.maxSearchResults());
		try
		{
			for (ItemPrice item : itemManager.search(searchTerm))
			{
				if (results.size() >= max)
				{
					break;
				}
				nameCache.put(item.getId(), item.getName());
				results.add(new int[]{item.getId()});
			}
		}
		catch (Exception e)
		{
			log.debug("Search failed: {}", e.getMessage());
		}

		return results;
	}

	private void showSearchResults(List<int[]> results)
	{
		if (results.isEmpty())
		{
			searchStatus.setText("No items found");
			searchStatus.setForeground(Color.RED);
			return;
		}

		searchStatus.setText(results.size() + " result(s) — click to add");
		searchStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPopupMenu popup = new JPopupMenu();
		for (int[] result : results)
		{
			int itemId = result[0];
			String name = nameCache.getOrDefault(itemId, "Item #" + itemId);
			JMenuItem item = new JMenuItem(name + "  (#" + itemId + ")");
			item.addActionListener(e ->
			{
				if (!watchlist.contains(itemId))
				{
					watchlist.add(itemId);
					saveData();
					refreshWatchlist();
				}
				searchField.setText("");
				searchStatus.setText("Added: " + name);
				searchStatus.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			});
			popup.add(item);
		}

		popup.setFocusable(false);
		popup.show(searchField, 0, searchField.getHeight());
		searchField.requestFocusInWindow();
	}

	private void addItemFromSearch()
	{
		String text = searchField.getText().trim();
		if (text.isEmpty())
		{
			return;
		}

		// Try numeric ID first
		try
		{
			int itemId = Integer.parseInt(text);
			if (itemId <= 0)
			{
				searchStatus.setText("Item ID must be positive");
				searchStatus.setForeground(Color.RED);
				return;
			}

			// Validate the ID exists
			clientThread.invokeLater(() ->
			{
				String name = resolveItemName(itemId);
				SwingUtilities.invokeLater(() ->
				{
					if (name == null)
					{
						searchStatus.setText("Unknown item ID: " + itemId);
						searchStatus.setForeground(Color.RED);
						return;
					}

					if (!watchlist.contains(itemId))
					{
						watchlist.add(itemId);
						saveData();
						refreshWatchlist();
						searchField.setText("");
						searchStatus.setText("Added: " + name);
						searchStatus.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
					}
					else
					{
						searchField.setText("");
						searchStatus.setText("Already in watchlist: " + name);
						searchStatus.setForeground(Color.YELLOW);
					}
				});
			});
			return;
		}
		catch (NumberFormatException ignored)
		{
		}

		// Non-numeric: trigger a search
		triggerSearch();
	}

	/**
	 * Resolves an item ID to a name. Must be called on the client thread.
	 * Returns null if the ID is invalid.
	 */
	private String resolveItemName(int itemId)
	{
		try
		{
			ItemComposition comp = itemManager.getItemComposition(itemId);
			if (comp != null && comp.getName() != null && !comp.getName().equals("null"))
			{
				nameCache.put(itemId, comp.getName());
				return comp.getName();
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to resolve item {}", itemId);
		}
		return null;
	}

	/**
	 * Adds a name-preview label to an ID text field. When a valid ID is typed,
	 * the label shows the item name. Supports name search with dropdown.
	 */
	private JLabel attachNamePreview(JTextField idField, JPanel parentForPopup)
	{
		JLabel preview = new JLabel(" ");
		preview.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		Timer timer = new Timer(SEARCH_DELAY_MS, e ->
		{
			String text = idField.getText().trim();
			if (text.isEmpty())
			{
				preview.setText(" ");
				preview.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				return;
			}

			// Numeric: resolve name
			try
			{
				int itemId = Integer.parseInt(text);
				if (itemId > 0)
				{
					clientThread.invokeLater(() ->
					{
						String name = resolveItemName(itemId);
						SwingUtilities.invokeLater(() ->
						{
							if (name != null)
							{
								preview.setText(name);
								preview.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
							}
							else
							{
								preview.setText("Unknown ID");
								preview.setForeground(Color.RED);
							}
						});
					});
				}
				return;
			}
			catch (NumberFormatException ignored)
			{
			}

			// Name search (in-memory via itemManager.search — no client thread needed)
			List<int[]> results = searchItemsByName(text);
			if (results.isEmpty())
			{
				preview.setText("No items found");
				preview.setForeground(Color.RED);
			}
			else
			{
				JPopupMenu popup = new JPopupMenu();
				for (int[] result : results)
				{
					int id = result[0];
					String name = nameCache.getOrDefault(id, "Item #" + id);
					JMenuItem menuItem = new JMenuItem(name + "  (#" + id + ")");
					menuItem.addActionListener(ev ->
					{
						idField.setText(String.valueOf(id));
						preview.setText(name);
						preview.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
					});
					popup.add(menuItem);
				}
				preview.setText(results.size() + " result(s)");
				preview.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				popup.setFocusable(false);
				popup.show(idField, 0, idField.getHeight());
				idField.requestFocusInWindow();
			}
		});
		timer.setRepeats(false);

		idField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				timer.restart();
			}
		});

		return preview;
	}

	private void updateCountdown()
	{
		if (!config.autoRefresh())
		{
			refreshCountdown.setText("Auto off");
			refreshCountdown.setIcon(null);
			refreshCountdown.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}

		long elapsed = System.currentTimeMillis() - lastRefreshTime;
		long intervalMs = Math.max(1, config.autoRefreshMinutes()) * 60_000L;
		long remaining = intervalMs - elapsed;

		if (remaining <= 0)
		{
			refreshCountdown.setText("Refreshing...");
			refreshCountdown.setIcon(null);
			refreshCountdown.setForeground(new Color(100, 200, 255));
			return;
		}

		long totalSecs = remaining / 1000;
		long mins = totalSecs / 60;
		long secs = totalSecs % 60;
		double pct = (double) remaining / intervalMs;

		// Pick icon based on fill percentage (index 0 = full, last = empty)
		int iconIdx = (int) ((1.0 - pct) * (TIMER_ICON_STEPS - 1));
		iconIdx = Math.max(0, Math.min(TIMER_ICON_STEPS - 1, iconIdx));

		refreshCountdown.setText(String.format("%d:%02d ", mins, secs));
		refreshCountdown.setIcon(timerIcons[iconIdx]);
		refreshCountdown.setHorizontalTextPosition(JLabel.LEFT);
		refreshCountdown.setForeground(new Color(180, 180, 180));
	}

	/**
	 * Called when a refresh completes to reset the countdown.
	 */
	public void markRefreshed()
	{
		lastRefreshTime = System.currentTimeMillis();
	}

	private void saveData()
	{
		dataManager.save(watchlist, watchlistOffsets, manualPriceManager, recipeManager);
	}

	public void refreshWatchlist()
	{
		List<Integer> ids = new ArrayList<>(watchlist);
		fetchPricesAndNames(ids, this::rebuildWatchlistUi);
	}

	public void refreshCombinations()
	{
		List<CombinationRecipe> recipes = recipeManager.getAllRecipes();
		log.debug("refreshCombinations: {} recipes", recipes.size());

		List<Integer> ids = new ArrayList<>();
		for (CombinationRecipe recipe : recipes)
		{
			ids.add(recipe.getResultItemId());
			ids.addAll(recipe.getIngredients().keySet());
		}
		fetchPricesAndNames(ids, this::rebuildCombinationsUi);
	}

	private void fetchPricesAndNames(List<Integer> itemIds, Runnable uiCallback)
	{
		log.debug("fetchPricesAndNames: {} items, wikiPrices={}", itemIds.size(), config.useWikiPrices());

		if (itemIds.isEmpty())
		{
			SwingUtilities.invokeLater(uiCallback);
			return;
		}

		if (config.useWikiPrices())
		{
			wikiPriceManager.fetchSeries(itemIds, () ->
			{
				// Compute prices + volatility from the cached series (safe on any thread)
				for (int itemId : itemIds)
				{
					int buy = wikiPriceManager.getBuyPrice(itemId, config);
					int sell = wikiPriceManager.getSellPrice(itemId, config);
					int rawBuy = wikiPriceManager.getRawBuyPrice(itemId, config);
					int rawSell = wikiPriceManager.getRawSellPrice(itemId, config);
					priceCache.put(itemId, new int[]{buy, sell, rawBuy, rawSell});
					volatilityCache.put(itemId, wikiPriceManager.getVolatilityCv(itemId, config));
				}

				// Cache names + ItemManager fallback on client thread, then update UI
				clientThread.invokeLater(() ->
				{
					for (int itemId : itemIds)
					{
						cacheItemName(itemId);

						// Fallback to ItemManager if wiki returned nothing
						int[] cached = priceCache.get(itemId);
						if (cached != null && cached[0] < 0 && cached[1] < 0)
						{
							int fallback = itemManager.getItemPrice(itemId);
							if (fallback > 0)
							{
								priceCache.put(itemId, new int[]{fallback, fallback, fallback, fallback});
							}
						}
					}
					SwingUtilities.invokeLater(uiCallback);
				});
			});
		}
		else
		{
			clientThread.invokeLater(() ->
			{
				for (int itemId : itemIds)
				{
					cacheItemName(itemId);
					volatilityCache.remove(itemId); // no series when wiki is off
					try
					{
						int price = itemManager.getItemPrice(itemId);
						priceCache.put(itemId, new int[]{price, price, price, price});
					}
					catch (Exception e)
					{
						log.debug("Failed to get price for item {}", itemId);
						priceCache.put(itemId, new int[]{-1, -1, -1, -1});
					}
				}
				SwingUtilities.invokeLater(uiCallback);
			});
		}
	}

	private void cacheItemName(int itemId)
	{
		try
		{
			String name = itemManager.getItemComposition(itemId).getName();
			if (name != null && !name.equals("null"))
			{
				nameCache.put(itemId, name);
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to get name for item {}", itemId);
		}
	}

	private String getCachedName(int itemId)
	{
		return nameCache.getOrDefault(itemId, "Item #" + itemId);
	}

	private int getCachedBuyPrice(int itemId)
	{
		int[] prices = priceCache.get(itemId);
		return prices != null ? prices[0] : -1;
	}

	private int getCachedSellPrice(int itemId)
	{
		int[] prices = priceCache.get(itemId);
		return prices != null ? prices[1] : -1;
	}

	private int getCachedRawBuyPrice(int itemId)
	{
		int[] prices = priceCache.get(itemId);
		return prices != null && prices.length > 2 ? prices[2] : getCachedBuyPrice(itemId);
	}

	private int getCachedRawSellPrice(int itemId)
	{
		int[] prices = priceCache.get(itemId);
		return prices != null && prices.length > 3 ? prices[3] : getCachedSellPrice(itemId);
	}

	private JTabbedPane buildTabs()
	{
		JTabbedPane tabs = new JTabbedPane();
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);

		watchlistPanel = new JPanel();
		watchlistPanel.setLayout(new BoxLayout(watchlistPanel, BoxLayout.Y_AXIS));
		watchlistPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane watchScroll = new JScrollPane(watchlistPanel);
		watchScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		watchScroll.setBorder(null);

		combinationsPanel = new JPanel();
		combinationsPanel.setLayout(new BoxLayout(combinationsPanel, BoxLayout.Y_AXIS));
		combinationsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel comboWrapper = new JPanel(new BorderLayout());
		comboWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton addRecipeBtn = new JButton("+ Add Recipe");
		addRecipeBtn.addActionListener(e -> showAddRecipeDialog(null));
		JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
		btnWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		btnWrap.add(addRecipeBtn);
		comboWrapper.add(btnWrap, BorderLayout.NORTH);

		JScrollPane comboScroll = new JScrollPane(combinationsPanel);
		comboScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		comboScroll.setBorder(null);
		comboWrapper.add(comboScroll, BorderLayout.CENTER);

		if (config.combinationsFirst())
		{
			tabs.addTab("Combinations", comboWrapper);
			tabs.addTab("Watchlist", watchScroll);
		}
		else
		{
			tabs.addTab("Watchlist", watchScroll);
			tabs.addTab("Combinations", comboWrapper);
		}

		return tabs;
	}

	private void rebuildWatchlistUi()
	{
		markRefreshed();
		watchlistPanel.removeAll();

		if (watchlist.isEmpty())
		{
			JLabel empty = new JLabel("No items — search above to add.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(new EmptyBorder(10, 5, 10, 5));
			watchlistPanel.add(empty);
		}
		else
		{
			for (int itemId : watchlist)
			{
				watchlistPanel.add(buildItemRow(itemId));
			}
		}

		watchlistPanel.revalidate();
		watchlistPanel.repaint();

		if (watchlistPanel.getParent() != null)
		{
			watchlistPanel.getParent().revalidate();
			watchlistPanel.getParent().repaint();
		}
	}

	private void moveWatchlistItem(int itemId, int delta)
	{
		int idx = watchlist.indexOf(Integer.valueOf(itemId));
		int target = idx + delta;
		if (idx < 0 || target < 0 || target >= watchlist.size())
		{
			return;
		}
		Collections.swap(watchlist, idx, target);
		saveData();
		rebuildWatchlistUi();
	}

	private JPanel buildItemRow(int itemId)
	{
		boolean useOffsets = watchlistOffsets.getOrDefault(itemId, false);

		JPanel row = new JPanel(new BorderLayout(5, 2));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(8, 5, 8, 5)
		));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

		String name = getCachedName(itemId);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel nameLabel = new JLabel(truncate(name, 18));
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setToolTipText(name);
		header.add(nameLabel, BorderLayout.WEST);

		JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		headerButtons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JButton overrideBtn = new JButton("Edit");
		overrideBtn.setMargin(new Insets(0, 4, 0, 4));
		overrideBtn.setToolTipText("Set manual prices");
		overrideBtn.addActionListener(e -> showOverrideDialog(itemId));
		headerButtons.add(overrideBtn);

		JButton upBtn = new JButton("▲");
		upBtn.setMargin(new Insets(0, 3, 0, 3));
		upBtn.setToolTipText("Move up");
		upBtn.addActionListener(e -> moveWatchlistItem(itemId, -1));
		headerButtons.add(upBtn);

		JButton downBtn = new JButton("▼");
		downBtn.setMargin(new Insets(0, 3, 0, 3));
		downBtn.setToolTipText("Move down");
		downBtn.addActionListener(e -> moveWatchlistItem(itemId, 1));
		headerButtons.add(downBtn);

		JButton refreshBtn = new JButton("\u21BB");
		refreshBtn.setMargin(new Insets(0, 4, 0, 4));
		refreshBtn.setToolTipText("Refresh prices");
		refreshBtn.addActionListener(e -> refreshWatchlist());
		headerButtons.add(refreshBtn);

		JButton removeBtn = makeTrashButton("Remove from watchlist", e ->
		{
			watchlist.remove(Integer.valueOf(itemId));
			watchlistOffsets.remove(itemId);
			manualPriceManager.clearManualPrices(itemId);
			saveData();
			refreshWatchlist();
		});
		headerButtons.add(removeBtn);

		header.add(headerButtons, BorderLayout.EAST);

		// Options bar with offset toggle
		JPanel optionsBar = new JPanel(new BorderLayout());
		optionsBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel offsetLabel = new JLabel("Apply Offsets");
		offsetLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		optionsBar.add(offsetLabel, BorderLayout.WEST);

		JLabel toggleLabel = new JLabel(new ImageIcon(createToggleIcon(useOffsets)));
		toggleLabel.setToolTipText("Apply buy/sell offsets and rounding");
		toggleLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		toggleLabel.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				boolean newVal = !watchlistOffsets.getOrDefault(itemId, false);
				watchlistOffsets.put(itemId, newVal);
				saveData();
				rebuildWatchlistUi();
			}
		});
		toggleLabel.setBorder(new EmptyBorder(0, 0, 0, 2));
		optionsBar.add(toggleLabel, BorderLayout.EAST);

		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.add(header);
		headerPanel.add(optionsBar);

		row.add(headerPanel, BorderLayout.NORTH);

		int geBuy = useOffsets ? getCachedBuyPrice(itemId) : getCachedRawBuyPrice(itemId);
		int geSell = useOffsets ? getCachedSellPrice(itemId) : getCachedRawSellPrice(itemId);

		int effectiveBuy = manualPriceManager.getEffectiveBuyPrice(itemId, geBuy);
		int effectiveSell = manualPriceManager.getEffectiveSellPrice(itemId, geSell);

		JPanel priceGrid = new JPanel(new GridLayout(3, 2, 4, 2));
		priceGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		boolean hasManualBuy = manualPriceManager.getManualBuyPrice(itemId) != null;
		boolean hasManualSell = manualPriceManager.getManualSellPrice(itemId) != null;

		priceGrid.add(makeLabel("Buy:", ColorScheme.LIGHT_GRAY_COLOR));
		priceGrid.add(makeLabel(
			formatGp(effectiveBuy) + (hasManualBuy ? " *" : ""),
			effectiveBuy > 0 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR));

		priceGrid.add(makeLabel("Sell:", ColorScheme.LIGHT_GRAY_COLOR));
		JPanel sellCell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		sellCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		Color sellColor = isVolatile(itemId) ? VOLATILE_COLOR
			: (effectiveSell > 0 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		sellCell.add(makeLabel(formatGp(effectiveSell) + (hasManualSell ? " *" : ""), sellColor));
		JLabel vMarker = volatilityMarker(itemId);
		if (vMarker != null)
		{
			sellCell.add(vMarker);
		}
		sellCell.add(createChartButton(itemId, getCachedName(itemId)));
		priceGrid.add(sellCell);

		boolean hasData = effectiveBuy > 0 && effectiveSell > 0;
		int profit = profitCalculator.calculateBasicProfit(effectiveBuy, effectiveSell);

		priceGrid.add(makeLabel("Profit:", ColorScheme.LIGHT_GRAY_COLOR));
		priceGrid.add(makeLabel(hasData ? formatGp(profit) : "N/A", profitColorFor(profit, hasData)));

		row.add(priceGrid, BorderLayout.CENTER);

		return row;
	}

	private void showOverrideDialog(int itemId)
	{
		String name = getCachedName(itemId);

		JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
		JTextField buyField = new JTextField();
		JTextField sellField = new JTextField();

		Integer manualBuy = manualPriceManager.getManualBuyPrice(itemId);
		Integer manualSell = manualPriceManager.getManualSellPrice(itemId);
		if (manualBuy != null) buyField.setText(String.valueOf(manualBuy));
		if (manualSell != null) sellField.setText(String.valueOf(manualSell));

		panel.add(new JLabel("Buy Price:"));
		panel.add(buyField);
		panel.add(new JLabel("Sell Price:"));
		panel.add(sellField);

		int result = JOptionPane.showConfirmDialog(this, panel,
			"Manual Prices - " + name, JOptionPane.OK_CANCEL_OPTION);

		if (result == JOptionPane.OK_OPTION)
		{
			try
			{
				String buyText = buyField.getText().trim();
				String sellText = sellField.getText().trim();

				if (buyText.isEmpty() && sellText.isEmpty())
				{
					manualPriceManager.clearManualPrices(itemId);
				}
				else
				{
					if (!buyText.isEmpty())
					{
						manualPriceManager.setManualBuyPrice(itemId, Integer.parseInt(buyText));
					}
					if (!sellText.isEmpty())
					{
						manualPriceManager.setManualSellPrice(itemId, Integer.parseInt(sellText));
					}
				}
				saveData();
				refreshWatchlist();
			}
			catch (NumberFormatException e)
			{
				JOptionPane.showMessageDialog(this, "Prices must be valid numbers.",
					"Invalid Input", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void showAddRecipeDialog(CombinationRecipe editingRecipe)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));

		// Top section: recipe name, result item with name preview
		JPanel topSection = new JPanel();
		topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));

		JPanel nameRow = new JPanel(new BorderLayout(5, 0));
		nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		nameRow.add(new JLabel("Recipe Name:"), BorderLayout.WEST);
		JTextField nameField = new JTextField();
		nameRow.add(nameField, BorderLayout.CENTER);
		topSection.add(nameRow);

		JPanel resultRow = new JPanel(new BorderLayout(5, 0));
		resultRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		JPanel resultFields = new JPanel(new GridLayout(1, 4, 5, 0));
		JTextField resultField = new JTextField();
		JTextField resultQtyField = new JTextField("1");
		resultFields.add(new JLabel("Result Item:"));
		resultFields.add(resultField);
		resultFields.add(new JLabel("  Qty:"));
		resultFields.add(resultQtyField);
		resultRow.add(resultFields, BorderLayout.CENTER);
		topSection.add(resultRow);

		JLabel resultPreview = attachNamePreview(resultField, panel);
		resultPreview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		topSection.add(resultPreview);

		if (editingRecipe != null)
		{
			nameField.setText(editingRecipe.getName());
			resultField.setText(String.valueOf(editingRecipe.getResultItemId()));
			resultQtyField.setText(String.valueOf(editingRecipe.getResultQuantity()));
		}

		panel.add(topSection, BorderLayout.NORTH);

		// Ingredient rows with name previews
		JPanel ingredientsContainer = new JPanel();
		ingredientsContainer.setLayout(new BoxLayout(ingredientsContainer, BoxLayout.Y_AXIS));

		// Each entry: [idField, qtyField, previewLabel]
		List<Object[]> ingredientRows = new ArrayList<>();

		Runnable addIngredientRow = () ->
		{
			JPanel row = new JPanel(new BorderLayout(5, 0));
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

			JPanel fields = new JPanel(new GridLayout(1, 4, 5, 0));
			JTextField idField = new JTextField();
			JTextField qtyField = new JTextField();
			fields.add(new JLabel("  Item:"));
			fields.add(idField);
			fields.add(new JLabel("  Qty:"));
			fields.add(qtyField);
			row.add(fields, BorderLayout.NORTH);

			JLabel preview = attachNamePreview(idField, panel);
			row.add(preview, BorderLayout.SOUTH);

			ingredientRows.add(new Object[]{idField, qtyField, preview});
			ingredientsContainer.add(row);
			ingredientsContainer.revalidate();
			ingredientsContainer.repaint();
		};

		if (editingRecipe != null && !editingRecipe.getIngredients().isEmpty())
		{
			for (Map.Entry<Integer, Integer> entry : editingRecipe.getIngredients().entrySet())
			{
				JPanel row = new JPanel(new BorderLayout(5, 0));
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

				JPanel fields = new JPanel(new GridLayout(1, 4, 5, 0));
				JTextField idField = new JTextField(String.valueOf(entry.getKey()));
				JTextField qtyField = new JTextField(String.valueOf(entry.getValue()));
				fields.add(new JLabel("  Item:"));
				fields.add(idField);
				fields.add(new JLabel("  Qty:"));
				fields.add(qtyField);
				row.add(fields, BorderLayout.NORTH);

				JLabel preview = attachNamePreview(idField, panel);
				row.add(preview, BorderLayout.SOUTH);

				ingredientRows.add(new Object[]{idField, qtyField, preview});
				ingredientsContainer.add(row);
			}
		}
		else
		{
			addIngredientRow.run();
		}

		JPanel ingredientSection = new JPanel(new BorderLayout(0, 5));
		JPanel labelRow = new JPanel(new BorderLayout());
		labelRow.add(new JLabel("Ingredients:"), BorderLayout.WEST);
		JButton addMoreBtn = new JButton("+ Add Ingredient");
		addMoreBtn.addActionListener(e -> addIngredientRow.run());
		labelRow.add(addMoreBtn, BorderLayout.EAST);
		ingredientSection.add(labelRow, BorderLayout.NORTH);

		JScrollPane ingredientScroll = new JScrollPane(ingredientsContainer);
		ingredientScroll.setPreferredSize(new Dimension(400, 150));
		ingredientSection.add(ingredientScroll, BorderLayout.CENTER);

		panel.add(ingredientSection, BorderLayout.CENTER);

		String dialogTitle = editingRecipe != null ? "Edit Recipe" : "Add Combination Recipe";

		// Loop so validation errors re-show the dialog with the user's data intact
		while (true)
		{
			int result = JOptionPane.showConfirmDialog(this, panel,
				dialogTitle, JOptionPane.OK_CANCEL_OPTION);

			if (result != JOptionPane.OK_OPTION)
			{
				return;
			}

			// Validate inputs
			String validationError = validateRecipeDialog(nameField, resultField, resultQtyField, ingredientRows, editingRecipe);
			if (validationError != null)
			{
				JOptionPane.showMessageDialog(this, validationError,
					"Validation Error", JOptionPane.ERROR_MESSAGE);
				continue;
			}

			String rName = nameField.getText().trim();
			int resultId = Integer.parseInt(resultField.getText().trim());

			// Check for duplicate name
			boolean isRename = editingRecipe != null && !editingRecipe.getName().equals(rName);
			boolean isNew = editingRecipe == null;
			if ((isNew || isRename) && recipeManager.getRecipe(rName) != null)
			{
				JOptionPane.showMessageDialog(this,
					"A recipe named \"" + rName + "\" already exists. Choose a different name.",
					"Duplicate Name", JOptionPane.WARNING_MESSAGE);
				continue;
			}

			// All valid — save the recipe
			if (editingRecipe != null)
			{
				recipeManager.removeRecipe(editingRecipe.getName());
			}

			int resultQty = Integer.parseInt(resultQtyField.getText().trim());
			CombinationRecipe recipe = new CombinationRecipe(rName, resultId, resultQty);
			for (Object[] fields : ingredientRows)
			{
				String idText = ((JTextField) fields[0]).getText().trim();
				String qtyText = ((JTextField) fields[1]).getText().trim();
				if (!idText.isEmpty() && !qtyText.isEmpty())
				{
					recipe.addIngredient(Integer.parseInt(idText), Integer.parseInt(qtyText));
				}
			}

			recipeManager.addRecipe(recipe);
			saveData();
			refreshCombinations();
			return;
		}
	}

	private String validateRecipeDialog(JTextField nameField, JTextField resultField,
		JTextField resultQtyField, List<Object[]> ingredientRows, CombinationRecipe editingRecipe)
	{
		String rName = nameField.getText().trim();
		if (rName.isEmpty())
		{
			return "Recipe name is required.";
		}

		String resultText = resultField.getText().trim();
		try
		{
			int resultId = Integer.parseInt(resultText);
			if (resultId <= 0)
			{
				return "Result item ID must be a positive number.";
			}
		}
		catch (NumberFormatException e)
		{
			return "Result item ID must be a valid number: \"" + resultText + "\"";
		}

		String resultQtyText = resultQtyField.getText().trim();
		try
		{
			int qty = Integer.parseInt(resultQtyText);
			if (qty <= 0)
			{
				return "Result quantity must be a positive number.";
			}
		}
		catch (NumberFormatException e)
		{
			return "Result quantity must be a valid number: \"" + resultQtyText + "\"";
		}

		int validIngredients = 0;
		int rowNum = 0;
		for (Object[] fields : ingredientRows)
		{
			rowNum++;
			String idText = ((JTextField) fields[0]).getText().trim();
			String qtyText = ((JTextField) fields[1]).getText().trim();

			if (idText.isEmpty() && qtyText.isEmpty())
			{
				continue;
			}

			try
			{
				int ingId = Integer.parseInt(idText);
				if (ingId <= 0)
				{
					return "Ingredient row " + rowNum + ": Item ID must be a positive number.";
				}
			}
			catch (NumberFormatException e)
			{
				return "Ingredient row " + rowNum + ": Invalid item ID \"" + idText + "\"";
			}

			try
			{
				int qty = Integer.parseInt(qtyText);
				if (qty <= 0)
				{
					return "Ingredient row " + rowNum + ": Quantity must be a positive number.";
				}
			}
			catch (NumberFormatException e)
			{
				return "Ingredient row " + rowNum + ": Invalid quantity \"" + qtyText + "\"";
			}

			validIngredients++;
		}

		if (validIngredients == 0)
		{
			return "Add at least one ingredient.";
		}

		return null;
	}

	private void rebuildCombinationsUi()
	{
		combinationsPanel.removeAll();

		List<CombinationRecipe> recipes = recipeManager.getAllRecipes();
		log.debug("rebuildCombinationsUi: {} recipes to display", recipes.size());

		if (recipes.isEmpty())
		{
			JLabel empty = new JLabel("No recipes. Click '+ Add Recipe' above.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(new EmptyBorder(10, 5, 10, 5));
			combinationsPanel.add(empty);
		}
		else
		{
			for (CombinationRecipe recipe : recipes)
			{
				combinationsPanel.add(buildRecipeRow(recipe));
			}
		}

		combinationsPanel.revalidate();
		combinationsPanel.repaint();

		if (combinationsPanel.getParent() != null)
		{
			combinationsPanel.getParent().revalidate();
			combinationsPanel.getParent().repaint();
		}
	}

	private JPanel buildRecipeRow(CombinationRecipe recipe)
	{
		boolean collapsed = recipe.isCollapsed();
		boolean useOffsets = recipe.isApplyOffsets();

		// Calculate prices regardless of collapsed state (needed for summary)
		int totalIngredientCost = 0;
		boolean ingredientsAvailable = !recipe.getIngredients().isEmpty();
		for (Map.Entry<Integer, Integer> entry : recipe.getIngredients().entrySet())
		{
			int unitPrice = useOffsets ? getCachedBuyPrice(entry.getKey()) : getCachedRawBuyPrice(entry.getKey());
			if (unitPrice > 0)
			{
				totalIngredientCost += unitPrice * entry.getValue();
			}
			else
			{
				ingredientsAvailable = false;
			}
		}

		String resultName = getCachedName(recipe.getResultItemId());
		int resultQty = recipe.getResultQuantity();
		int unitSellPrice = useOffsets ? getCachedSellPrice(recipe.getResultItemId()) : getCachedRawSellPrice(recipe.getResultItemId());
		int resultPrice = unitSellPrice > 0 ? unitSellPrice * resultQty : unitSellPrice;
		int tax = resultPrice > 0 ? profitCalculator.calculateTax(resultPrice) : 0;
		boolean hasData = resultPrice > 0 && ingredientsAvailable;
		int profit = profitCalculator.calculateCombinationProfit(recipe, totalIngredientCost, resultPrice);
		Color profitColor = profitColorFor(profit, hasData);
		String profitText = hasData ? formatGp(profit) : "N/A";

		JPanel row = new JPanel(new BorderLayout(0, 0));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 5, 4, 5)
		));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Top bar: collapse toggle + name + buttons
		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topBar.setBorder(new EmptyBorder(0, 0, 2, 0));

		JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel collapseBtn = new JLabel(collapsed ? "\u25B6" : "\u25BC");
		collapseBtn.setFont(collapseBtn.getFont().deriveFont(9f));
		collapseBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		collapseBtn.setToolTipText(collapsed ? "Expand" : "Collapse");
		collapseBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		collapseBtn.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				recipe.setCollapsed(!recipe.isCollapsed());
				saveData();
				rebuildCombinationsUi();
			}
		});
		namePanel.add(collapseBtn);

		JLabel nameLabel = new JLabel(truncate(recipe.getName(), 16));
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setToolTipText(recipe.getName());
		namePanel.add(nameLabel);

		topBar.add(namePanel, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JButton editBtn = new JButton("Edit");
		editBtn.setMargin(new Insets(0, 4, 0, 4));
		editBtn.addActionListener(e -> showAddRecipeDialog(recipe));
		buttons.add(editBtn);

		JButton upBtn = new JButton("▲");
		upBtn.setMargin(new Insets(0, 3, 0, 3));
		upBtn.setToolTipText("Move up");
		upBtn.addActionListener(e ->
		{
			recipeManager.moveRecipe(recipe.getName(), -1);
			saveData();
			rebuildCombinationsUi();
		});
		buttons.add(upBtn);

		JButton downBtn = new JButton("▼");
		downBtn.setMargin(new Insets(0, 3, 0, 3));
		downBtn.setToolTipText("Move down");
		downBtn.addActionListener(e ->
		{
			recipeManager.moveRecipe(recipe.getName(), 1);
			saveData();
			rebuildCombinationsUi();
		});
		buttons.add(downBtn);

		JButton refreshBtn = new JButton("\u21BB");
		refreshBtn.setMargin(new Insets(0, 4, 0, 4));
		refreshBtn.setToolTipText("Refresh prices");
		refreshBtn.addActionListener(e -> refreshCombinations());
		buttons.add(refreshBtn);

		JButton removeBtn = makeTrashButton("Remove recipe", e ->
		{
			recipeManager.removeRecipe(recipe.getName());
			saveData();
			refreshCombinations();
		});
		buttons.add(removeBtn);

		topBar.add(buttons, BorderLayout.EAST);

		// Options bar with offset checkbox
		JPanel optionsBar = new JPanel(new BorderLayout());
		optionsBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel offsetLabel = new JLabel("Apply Offsets");
		offsetLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		optionsBar.add(offsetLabel, BorderLayout.WEST);

		JLabel toggleLabel = new JLabel(new ImageIcon(createToggleIcon(recipe.isApplyOffsets())));
		toggleLabel.setToolTipText("Apply buy/sell offsets and rounding to this recipe");
		toggleLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		toggleLabel.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				recipe.setApplyOffsets(!recipe.isApplyOffsets());
				saveData();
				refreshCombinations();
			}
		});
		toggleLabel.setBorder(new EmptyBorder(0, 0, 0, 2));
		optionsBar.add(toggleLabel, BorderLayout.EAST);

		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.add(topBar);
		headerPanel.add(optionsBar);

		row.add(headerPanel, BorderLayout.NORTH);

		// Body content
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Separator between the Apply Offsets header and the ingredient list
		addLine(body, "---", ColorScheme.MEDIUM_GRAY_COLOR);

		if (collapsed)
		{
			// Collapsed: one line per ingredient (name + buy), result + sell, profit
			for (Map.Entry<Integer, Integer> entry : recipe.getIngredients().entrySet())
			{
				int ingId = entry.getKey();
				String ingName = getCachedName(ingId);
				int unitPrice = useOffsets ? getCachedBuyPrice(ingId) : getCachedRawBuyPrice(ingId);
				addLineWithChart(body, GP_FORMAT.format(entry.getValue()) + "x " + truncate(ingName, 12) + ": " + formatGp(unitPrice),
					ColorScheme.LIGHT_GRAY_COLOR, ingId, ingName, false);
			}
			String rQtyStr = resultQty > 1 ? resultQty + "x " : "";
			addLineWithChart(body, rQtyStr + resultName + ": " + formatGp(resultPrice), Color.WHITE, recipe.getResultItemId(), resultName, true);
			addLine(body, "Profit: " + profitText, profitColor);
		}
		else
		{
			// Expanded: full detail
			for (Map.Entry<Integer, Integer> entry : recipe.getIngredients().entrySet())
			{
				int ingId = entry.getKey();
				int qty = entry.getValue();
				String ingName = getCachedName(ingId);
				int unitPrice = useOffsets ? getCachedBuyPrice(ingId) : getCachedRawBuyPrice(ingId);
				int lineCost = unitPrice > 0 ? unitPrice * qty : 0;

				addLineWithChart(body, GP_FORMAT.format(qty) + "x " + truncate(ingName, 16), ColorScheme.LIGHT_GRAY_COLOR, ingId, ingName, false);
				addLine(body, "  @ " + formatGp(unitPrice) + " = " + formatGp(lineCost), ColorScheme.LIGHT_GRAY_COLOR);
			}

			addLine(body, "---", ColorScheme.MEDIUM_GRAY_COLOR);
			String sellLabel = resultQty > 1
				? "Sell: " + resultQty + "x @ " + formatGp(unitSellPrice) + " = " + formatGp(resultPrice)
				: "Sell: " + formatGp(resultPrice);
			addLineWithChart(body, sellLabel, Color.WHITE, recipe.getResultItemId(), resultName, true);
			addLine(body, "GE Tax: -" + formatGp(tax), new Color(255, 180, 100));
			addLine(body, "Cost: -" + formatGp(totalIngredientCost), ColorScheme.LIGHT_GRAY_COLOR);
			addLine(body, "Profit: " + profitText, profitColor);
		}

		row.add(body, BorderLayout.CENTER);

		// Dynamic max height
		int bodyLines = collapsed
			? recipe.getIngredients().size() + 2
			: recipe.getIngredients().size() * 2 + 5;
		int estimatedHeight = 28 + 22 + bodyLines * 16 + 16;
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, estimatedHeight));

		return row;
	}

	private BufferedImage createToggleIcon(boolean checked)
	{
		int s = 12;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Square border
		g.setColor(new Color(180, 180, 180));
		g.drawRect(0, 0, s - 1, s - 1);

		if (checked)
		{
			// Fill with blue
			g.setColor(new Color(100, 200, 255));
			g.fillRect(2, 2, s - 4, s - 4);

			// Checkmark in white
			g.setColor(Color.WHITE);
			g.setStroke(new java.awt.BasicStroke(2));
			g.drawLine(3, 6, 5, 9);
			g.drawLine(5, 9, 9, 3);
		}

		g.dispose();
		return img;
	}

	private String truncate(String text, int maxLen)
	{
		if (text == null || text.length() <= maxLen)
		{
			return text;
		}
		return text.substring(0, maxLen - 1) + "\u2026";
	}

	private void addLine(JPanel container, String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		container.add(label);
	}

	private JLabel makeLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		return label;
	}

	/**
	 * Profit text color: gray when inputs are missing (shown as "N/A"), green for a
	 * real gain, red for a real loss. Distinguishes "no data" from an actual loss.
	 */
	private Color profitColorFor(int profit, boolean hasData)
	{
		if (!hasData)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		return profit >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.RED;
	}

	/**
	 * A small clickable chart icon that opens the price-history pop-out for an item.
	 */
	private JLabel createChartButton(int itemId, String itemName)
	{
		JLabel btn = new JLabel(new ImageIcon(createChartIcon()));
		btn.setToolTipText("Price history chart");
		btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		btn.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				openPriceHistory(itemId, itemName);
			}
		});
		return btn;
	}

	/**
	 * Fetches the wiki timeseries (off the client thread) and opens the chart window
	 * with the last N points, per the configured interval and point count.
	 */
	private void openPriceHistory(int itemId, String itemName)
	{
		if (!config.useWikiPrices())
		{
			JOptionPane.showMessageDialog(this,
				"To use this feature, you must have \"Use Wiki Prices\" enabled in settings.",
				"Wiki Prices Required", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		TimeStep step = config.priceHistoryTimestep();
		int n = Math.max(2, config.priceHistoryPoints());
		// Display series at the configured interval, plus 1h (for 5-day) and 24h
		// (for 1-year) series for the high/low footer. Nested so all are ready first.
		wikiPriceManager.fetchTimeseries(itemId, step.getApiValue(), display ->
			wikiPriceManager.fetchTimeseries(itemId, "1h", h1 ->
				wikiPriceManager.fetchTimeseries(itemId, "24h", h24 ->
					SwingUtilities.invokeLater(() ->
					{
						List<WikiPriceManager.TimeseriesPoint> last = display.size() > n
							? new ArrayList<>(display.subList(display.size() - n, display.size()))
							: new ArrayList<>(display);
						long[] fiveDay = highLow(h1, 5 * 24);
						long[] oneYear = highLow(h24, Integer.MAX_VALUE);
						PriceHistoryChart.show(itemName, last,
							"last " + last.size() + " × " + step, fiveDay, oneYear);
					}))));
	}

	/**
	 * Returns {low, high} = {min avgLowPrice, max avgHighPrice} over the last
	 * {@code lastN} points of the series, or -1 when unavailable.
	 */
	private long[] highLow(List<WikiPriceManager.TimeseriesPoint> series, int lastN)
	{
		long low = Long.MAX_VALUE;
		long high = Long.MIN_VALUE;
		int start = Math.max(0, series.size() - lastN);
		for (int i = start; i < series.size(); i++)
		{
			WikiPriceManager.TimeseriesPoint p = series.get(i);
			if (p.avgLowPrice != null)
			{
				low = Math.min(low, p.avgLowPrice);
			}
			if (p.avgHighPrice != null)
			{
				high = Math.max(high, p.avgHighPrice);
			}
		}
		return new long[]{low == Long.MAX_VALUE ? -1 : low, high == Long.MIN_VALUE ? -1 : high};
	}

	private BufferedImage createChartIcon()
	{
		int s = 14;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(120, 120, 120));
		g.drawLine(2, 1, 2, s - 2);
		g.drawLine(2, s - 2, s - 1, s - 2);
		g.setColor(new Color(100, 200, 255));
		g.setStroke(new BasicStroke(1.5f));
		g.drawLine(3, 9, 6, 6);
		g.drawLine(6, 6, 9, 8);
		g.drawLine(9, 8, 12, 3);
		g.dispose();
		return img;
	}

	private BufferedImage createTrashIcon(Color main, Color stripe)
	{
		int s = 12;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(main);
		g.fillRect(5, 0, 2, 1);  // handle
		g.fillRect(1, 2, 10, 2); // lid
		g.fillRect(2, 4, 8, 7);  // body
		g.setColor(stripe);
		g.drawLine(4, 5, 4, 10); // stripes
		g.drawLine(6, 5, 6, 10);
		g.drawLine(8, 5, 8, 10);
		g.dispose();
		return img;
	}

	/**
	 * A borderless trash button sized to the 12x12 icon (same width as the Apply
	 * Offsets toggle). Hand cursor + turns red on hover to signal it's clickable.
	 */
	private JButton makeTrashButton(String tip, java.awt.event.ActionListener action)
	{
		JButton b = new JButton(new ImageIcon(createTrashIcon(new Color(210, 210, 210), new Color(80, 80, 80))));
		b.setRolloverIcon(new ImageIcon(createTrashIcon(new Color(235, 100, 100), new Color(130, 40, 40))));
		b.setRolloverEnabled(true);
		b.setToolTipText(tip);
		b.setMargin(new Insets(0, 0, 0, 0));
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		b.addActionListener(action);
		return b;
	}

	private boolean isVolatile(int itemId)
	{
		Double cv = volatilityCache.get(itemId);
		return cv != null && cv > config.volatilityThreshold();
	}

	private BufferedImage createVolatilityIcon()
	{
		int s = 14;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(VOLATILE_COLOR);
		g.fillPolygon(new int[]{7, 1, 13}, new int[]{1, 13, 13}, 3);
		g.setColor(new Color(40, 25, 0));
		g.fillRect(6, 4, 2, 5);
		g.fillRect(6, 10, 2, 2);
		g.dispose();
		return img;
	}

	/**
	 * The orange ⚠ marker shown on volatile items/recipes, or null if not volatile.
	 */
	private JLabel volatilityMarker(int itemId)
	{
		if (!isVolatile(itemId))
		{
			return null;
		}
		JLabel marker = new JLabel(new ImageIcon(createVolatilityIcon()));
		double cv = volatilityCache.getOrDefault(itemId, 0.0);
		marker.setToolTipText(String.format(
			"Volatile: %.0f%% variation over %dh (> %d%% threshold)",
			cv, config.volatilityWindowHours(), config.volatilityThreshold()));
		return marker;
	}

	/**
	 * Adds a text line to a BoxLayout body with a chart button after it. When
	 * showVolatility is true, the text turns orange and an ⚠ marker is added if the
	 * item is flagged volatile.
	 */
	private void addLineWithChart(JPanel container, String text, Color color, int itemId, String itemName, boolean showVolatility)
	{
		JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setAlignmentX(0f);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		boolean vol = showVolatility && isVolatile(itemId);
		JLabel label = new JLabel(text);
		label.setForeground(vol ? VOLATILE_COLOR : color);
		label.setBorder(new EmptyBorder(0, 0, 0, 4));
		line.add(label);
		if (vol)
		{
			JLabel vMarker = volatilityMarker(itemId);
			if (vMarker != null)
			{
				line.add(vMarker);
			}
		}
		line.add(createChartButton(itemId, itemName));
		container.add(line);
	}

	private String formatGp(int amount)
	{
		if (amount == -1)
		{
			return "N/A";
		}
		return GP_FORMAT.format(amount) + " gp";
	}
}
