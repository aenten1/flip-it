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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for OsrsFlipPlugin core functionality.
 */
public class OsrsFlipPluginTest
{
	private ProfitCalculator profitCalculator;
	private CombinationRecipeManager recipeManager;
	private ManualPriceManager manualPriceManager;

	@Before
	public void setUp()
	{
		profitCalculator = new ProfitCalculator();
		recipeManager = new CombinationRecipeManager();
		manualPriceManager = new ManualPriceManager();
	}

	// --- Tax calculation tests ---

	@Test
	public void testCalculateTax_BelowThreshold()
	{
		// Items below 50gp have no tax
		assertEquals(0, profitCalculator.calculateTax(0));
		assertEquals(0, profitCalculator.calculateTax(49));
	}

	@Test
	public void testCalculateTax_Normal()
	{
		// 2% floored
		assertEquals(1, profitCalculator.calculateTax(50));
		assertEquals(2, profitCalculator.calculateTax(100));
		assertEquals(3, profitCalculator.calculateTax(150));
		assertEquals(2000, profitCalculator.calculateTax(100_000));
	}

	@Test
	public void testCalculateTax_Capped()
	{
		// Capped at 5,000,000
		assertEquals(5_000_000, profitCalculator.calculateTax(250_000_001));
		assertEquals(5_000_000, profitCalculator.calculateTax(500_000_000));
	}

	// --- Profit calculation tests ---

	@Test
	public void testCalculateBasicProfit_PositiveProfit()
	{
		// sell 150, tax = floor(150 * 0.02) = 3, profit = 150 - 3 - 100 = 47
		assertEquals(47, profitCalculator.calculateBasicProfit(100, 150));
	}

	@Test
	public void testCalculateBasicProfit_NegativeProfit()
	{
		// sell 150, tax = 3, profit = 150 - 3 - 200 = -53
		assertEquals(-53, profitCalculator.calculateBasicProfit(200, 150));
	}

	@Test
	public void testCalculateBasicProfit_InvalidPrices()
	{
		assertEquals(-1, profitCalculator.calculateBasicProfit(-1, 150));
		assertEquals(-1, profitCalculator.calculateBasicProfit(100, -1));
	}

	@Test
	public void testCalculateBasicProfit_NoTaxBelowThreshold()
	{
		// sell 30, tax = 0 (below 50gp), profit = 30 - 0 - 10 = 20
		assertEquals(20, profitCalculator.calculateBasicProfit(10, 30));
	}

	// --- Threshold tests ---

	@Test
	public void testGetProfitThreshold_NoProfit()
	{
		assertEquals(ProfitThreshold.NONE, profitCalculator.getProfitThreshold(-1));
		assertEquals(ProfitThreshold.NONE, profitCalculator.getProfitThreshold(0));
	}

	@Test
	public void testGetProfitThreshold_LowProfit()
	{
		assertEquals(ProfitThreshold.LOW, profitCalculator.getProfitThreshold(1));
		assertEquals(ProfitThreshold.LOW, profitCalculator.getProfitThreshold(49_999));
	}

	@Test
	public void testGetProfitThreshold_MediumProfit()
	{
		assertEquals(ProfitThreshold.MEDIUM, profitCalculator.getProfitThreshold(50_000));
		assertEquals(ProfitThreshold.MEDIUM, profitCalculator.getProfitThreshold(99_999));
	}

	@Test
	public void testGetProfitThreshold_HighProfit()
	{
		assertEquals(ProfitThreshold.HIGH, profitCalculator.getProfitThreshold(100_000));
		assertEquals(ProfitThreshold.HIGH, profitCalculator.getProfitThreshold(249_999));
	}

	// --- Recipe tests ---

	@Test
	public void testCombinationRecipe_AddIngredients()
	{
		CombinationRecipe recipe = new CombinationRecipe("Test Recipe", 1, 1);

		recipe.addIngredient(314, 2);
		recipe.addIngredient(315, 1);

		assertEquals(2, recipe.getIngredients().size());
		assertEquals(2, recipe.getIngredientQuantity(314));
		assertEquals(1, recipe.getIngredientQuantity(315));
	}

	@Test
	public void testCombinationRecipe_ResultQuantity()
	{
		CombinationRecipe recipe = new CombinationRecipe("Multi Result", 1, 5);
		assertEquals(5, recipe.getResultQuantity());
	}

	@Test
	public void testCombinationRecipe_ResultQuantity_ClampedToMinimumOne()
	{
		assertEquals(1, new CombinationRecipe("Zero Qty", 1, 0).getResultQuantity());
		assertEquals(1, new CombinationRecipe("Negative Qty", 1, -3).getResultQuantity());
	}

	@Test
	public void testCombinationRecipe_Validate_Valid()
	{
		CombinationRecipe recipe = new CombinationRecipe("Valid Recipe", 1, 1);
		recipe.addIngredient(314, 2);
		recipe.addIngredient(315, 1);

		assertTrue(recipe.validate());
	}

	@Test
	public void testCombinationRecipe_Validate_TooManyIngredients()
	{
		CombinationRecipe recipe = new CombinationRecipe("Too Many Ingredients", 1, 1);

		for (int i = 1; i <= 11; i++)
		{
			recipe.addIngredient(i, 1);
		}

		assertFalse(recipe.validate());
	}

	// --- Manual price tests ---

	@Test
	public void testManualPriceManager_SetAndGetPrices()
	{
		int itemId = 314;

		manualPriceManager.setManualBuyPrice(itemId, 100);
		manualPriceManager.setManualSellPrice(itemId, 150);

		assertEquals(Integer.valueOf(100), manualPriceManager.getManualBuyPrice(itemId));
		assertEquals(Integer.valueOf(150), manualPriceManager.getManualSellPrice(itemId));
	}

	@Test
	public void testManualPriceManager_EffectivePrice_UsesManualWhenSet()
	{
		manualPriceManager.setManualBuyPrice(314, 500);
		assertEquals(500, manualPriceManager.getEffectiveBuyPrice(314, 100));
	}

	@Test
	public void testManualPriceManager_EffectivePrice_FallsBackToGe()
	{
		assertEquals(100, manualPriceManager.getEffectiveBuyPrice(314, 100));
	}

	@Test
	public void testManualPriceManager_ClearPrices()
	{
		manualPriceManager.setManualBuyPrice(314, 500);
		manualPriceManager.clearManualPrices(314);
		assertNull(manualPriceManager.getManualBuyPrice(314));
	}

	// --- Recipe manager tests ---

	@Test
	public void testRecipeManager_AddAndRetrieve()
	{
		CombinationRecipe recipe = new CombinationRecipe("Steel Bar", 2353, 1);
		recipe.addIngredient(440, 1);
		recipeManager.addRecipe(recipe);

		assertEquals(1, recipeManager.getRecipeCount());
		assertNotNull(recipeManager.getRecipe("Steel Bar"));
	}

	@Test
	public void testRecipeManager_DuplicateNameOverwrites()
	{
		CombinationRecipe r1 = new CombinationRecipe("Test", 1, 1);
		r1.addIngredient(314, 1);
		recipeManager.addRecipe(r1);

		CombinationRecipe r2 = new CombinationRecipe("Test", 2, 1);
		r2.addIngredient(315, 1);
		recipeManager.addRecipe(r2);

		assertEquals(1, recipeManager.getRecipeCount());
		assertEquals(2, recipeManager.getRecipe("Test").getResultItemId());
	}

	@Test
	public void testRecipeManager_Remove()
	{
		CombinationRecipe recipe = new CombinationRecipe("Remove Me", 1, 1);
		recipe.addIngredient(314, 1);
		recipeManager.addRecipe(recipe);

		assertTrue(recipeManager.removeRecipe("Remove Me"));
		assertEquals(0, recipeManager.getRecipeCount());
	}
}
