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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * A pop-out line chart of an item's recent wiki price history (sell = avgHighPrice,
 * buy = avgLowPrice). Drawn with Graphics2D — no external chart dependency. Each
 * point's exact timestamp and values are shown on hover.
 */
class PriceHistoryChart extends JPanel
{
	private static final NumberFormat GP = NumberFormat.getInstance();
	private static final DateTimeFormatter TIME_FMT =
		DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());
	private static final Color HIGH_COLOR = new Color(100, 200, 255);
	private static final Color LOW_COLOR = new Color(255, 180, 100);
	private static final Color GRID_COLOR = new Color(255, 255, 255, 28);

	private final List<WikiPriceManager.TimeseriesPoint> points;
	private final int marginL = 80;
	private final int marginR = 16;
	private final int marginT = 30;
	private final int marginB = 50;
	private int hoverIndex = -1;

	PriceHistoryChart(List<WikiPriceManager.TimeseriesPoint> points)
	{
		this.points = points;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setPreferredSize(new Dimension(560, 320));

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				updateHover(e.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverIndex = -1;
				setToolTipText(null);
				repaint();
			}
		};
		addMouseMotionListener(mouse);
		addMouseListener(mouse);
		setToolTipText(" "); // register with the tooltip manager
	}

	/**
	 * Opens a non-modal pop-out window showing the chart for the given item.
	 */
	static void show(String itemName, List<WikiPriceManager.TimeseriesPoint> points, String subtitle,
		long[] fiveDay, long[] oneYear)
	{
		JFrame frame = new JFrame("Price history — " + itemName);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel header = new JLabel(itemName + "   (" + subtitle + ")");
		header.setForeground(Color.WHITE);
		header.setBorder(new EmptyBorder(6, 10, 2, 10));
		content.add(header, BorderLayout.NORTH);
		content.add(new PriceHistoryChart(points), BorderLayout.CENTER);

		JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		footer.setBorder(new EmptyBorder(2, 10, 6, 10));
		JLabel fiveDayLabel = new JLabel(formatHL("5-day", fiveDay));
		fiveDayLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JLabel oneYearLabel = new JLabel(formatHL("1-year", oneYear));
		oneYearLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		footer.add(fiveDayLabel, BorderLayout.WEST);
		footer.add(oneYearLabel, BorderLayout.EAST);
		content.add(footer, BorderLayout.SOUTH);

		frame.setContentPane(content);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private static String formatHL(String label, long[] hl)
	{
		return label + "  Low " + gp(hl[0]) + " / High " + gp(hl[1]);
	}

	private static String gp(long v)
	{
		return v < 0 ? "—" : GP.format(v);
	}

	@Override
	protected void paintComponent(Graphics gg)
	{
		super.paintComponent(gg);
		Graphics2D g = (Graphics2D) gg;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int x0 = marginL;
		int y0 = marginT;
		int x1 = w - marginR;
		int y1 = h - marginB;
		int cw = x1 - x0;
		int ch = y1 - y0;

		if (points == null || points.isEmpty())
		{
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g.drawString("No price data available", w / 2 - 70, h / 2);
			return;
		}

		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (WikiPriceManager.TimeseriesPoint p : points)
		{
			if (p.avgHighPrice != null)
			{
				min = Math.min(min, p.avgHighPrice);
				max = Math.max(max, p.avgHighPrice);
			}
			if (p.avgLowPrice != null)
			{
				min = Math.min(min, p.avgLowPrice);
				max = Math.max(max, p.avgLowPrice);
			}
		}
		if (min == Integer.MAX_VALUE)
		{
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g.drawString("No price data available", w / 2 - 70, h / 2);
			return;
		}
		if (max == min)
		{
			max = min + 1;
		}

		// Axes + horizontal grid lines with y-axis value labels (top=max, bottom=min)
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.drawLine(x0, y0, x0, y1);
		g.drawLine(x0, y1, x1, y1);
		for (int t = 0; t <= 2; t++)
		{
			int val = min + (max - min) * (2 - t) / 2;
			int yy = y0 + ch * t / 2;
			g.setColor(GRID_COLOR);
			g.drawLine(x0, yy, x1, yy);
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			String s = GP.format(val);
			g.drawString(s, x0 - 6 - g.getFontMetrics().stringWidth(s), yy + 4);
		}

		drawSeries(g, x0, cw, y0, ch, min, max, true, HIGH_COLOR);
		drawSeries(g, x0, cw, y0, ch, min, max, false, LOW_COLOR);

		// X-axis labels: actual first/last timestamps
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		String tStart = TIME_FMT.format(Instant.ofEpochSecond(points.get(0).timestamp));
		String tEnd = TIME_FMT.format(Instant.ofEpochSecond(points.get(points.size() - 1).timestamp));
		g.drawString(tStart, x0, y1 + 16);
		g.drawString(tEnd, x1 - g.getFontMetrics().stringWidth(tEnd), y1 + 16);

		// Axis titles
		String xLabel = "Date / Time";
		g.drawString(xLabel, x0 + (cw - g.getFontMetrics().stringWidth(xLabel)) / 2, y1 + 34);

		Graphics2D gy = (Graphics2D) g.create();
		gy.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		gy.rotate(-Math.PI / 2.0);
		String yLabel = "Gold (GP)";
		int yl = gy.getFontMetrics().stringWidth(yLabel);
		gy.drawString(yLabel, -(y0 + ch / 2 + yl / 2), 14);
		gy.dispose();

		// Legend
		g.setColor(HIGH_COLOR);
		g.fillRect(x1 - 96, y0 - 16, 9, 9);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.drawString("High", x1 - 84, y0 - 8);
		g.setColor(LOW_COLOR);
		g.fillRect(x1 - 46, y0 - 16, 9, 9);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.drawString("Low", x1 - 34, y0 - 8);

		// Hover crosshair
		if (hoverIndex >= 0 && hoverIndex < points.size())
		{
			int hx = xFor(hoverIndex, x0, cw);
			g.setColor(new Color(255, 255, 255, 70));
			g.drawLine(hx, y0, hx, y1);
		}
	}

	private void drawSeries(Graphics2D g, int x0, int cw, int y0, int ch,
		int min, int max, boolean high, Color color)
	{
		g.setColor(color);
		g.setStroke(new BasicStroke(2f));
		Integer prevX = null;
		Integer prevY = null;
		for (int i = 0; i < points.size(); i++)
		{
			Integer v = high ? points.get(i).avgHighPrice : points.get(i).avgLowPrice;
			if (v == null)
			{
				// No trade in this interval — skip the missing point but keep the
				// line spanning the gap to the next available value.
				continue;
			}
			int px = xFor(i, x0, cw);
			int py = y0 + (int) Math.round((double) (max - v) / (max - min) * ch);
			if (prevX != null)
			{
				g.drawLine(prevX, prevY, px, py);
			}
			g.fillOval(px - 2, py - 2, 4, 4);
			prevX = px;
			prevY = py;
		}
	}

	private int xFor(int i, int x0, int cw)
	{
		if (points.size() == 1)
		{
			return x0 + cw / 2;
		}
		return x0 + (int) Math.round((double) i / (points.size() - 1) * cw);
	}

	private void updateHover(Point pt)
	{
		if (points == null || points.isEmpty())
		{
			return;
		}
		int x0 = marginL;
		int cw = getWidth() - marginR - x0;
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < points.size(); i++)
		{
			double dx = Math.abs(xFor(i, x0, cw) - pt.x);
			if (dx < bestDist)
			{
				bestDist = dx;
				best = i;
			}
		}
		hoverIndex = best;

		WikiPriceManager.TimeseriesPoint p = points.get(best);
		String time = TIME_FMT.format(Instant.ofEpochSecond(p.timestamp));
		String high = p.avgHighPrice != null ? GP.format(p.avgHighPrice) + " gp" : "—";
		String low = p.avgLowPrice != null ? GP.format(p.avgLowPrice) + " gp" : "—";
		setToolTipText("<html><b>" + time + "</b><br>High: " + high + "<br>Low: " + low + "</html>");
		repaint();
	}
}
