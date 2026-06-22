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

/**
 * Calculates profits for item flips and combinations.
 * GE tax is 2% of the sell price, floored to the nearest whole number,
 * capped at 5,000,000 gp. Items sold below 50 gp have no tax.
 */
public class ProfitCalculator
{
	private static final double GE_TAX_RATE = 0.02;
	private static final int GE_TAX_CAP = 5_000_000;
	private static final int GE_TAX_THRESHOLD = 50;

	/**
	 * Calculates the GE tax for a given sell price.
	 */
	public int calculateTax(int sellPrice)
	{
		if (sellPrice < GE_TAX_THRESHOLD)
		{
			return 0;
		}

		int tax = (int) Math.floor(sellPrice * GE_TAX_RATE);
		return Math.min(tax, GE_TAX_CAP);
	}

	/**
	 * Calculates profit for a basic flip (buy low, sell high with tax).
	 */
	public int calculateBasicProfit(int buyPrice, int sellPrice)
	{
		if (buyPrice < 0 || sellPrice < 0)
		{
			return -1;
		}

		int tax = calculateTax(sellPrice);
		return sellPrice - tax - buyPrice;
	}

	/**
	 * Calculates profit for a combination recipe.
	 */
	public int calculateCombinationProfit(CombinationRecipe recipe, int totalIngredientCost, int combinedSellPrice)
	{
		if (totalIngredientCost < 0 || combinedSellPrice < 0)
		{
			return -1;
		}

		int tax = calculateTax(combinedSellPrice);
		return combinedSellPrice - tax - totalIngredientCost;
	}

	/**
	 * Returns the profit threshold category for a given profit amount.
	 */
	public ProfitThreshold getProfitThreshold(int profit)
	{
		if (profit <= 0)
		{
			return ProfitThreshold.NONE;
		}
		else if (profit < 50_000)
		{
			return ProfitThreshold.LOW;
		}
		else if (profit < 100_000)
		{
			return ProfitThreshold.MEDIUM;
		}
		else if (profit < 250_000)
		{
			return ProfitThreshold.HIGH;
		}
		else if (profit < 500_000)
		{
			return ProfitThreshold.VERY_HIGH;
		}
		else if (profit < 1_000_000)
		{
			return ProfitThreshold.ULTRA_HIGH;
		}
		else
		{
			return ProfitThreshold.LEGENDARY;
		}
	}
}
