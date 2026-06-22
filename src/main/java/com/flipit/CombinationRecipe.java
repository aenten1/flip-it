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

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a combination recipe for crafting items.
 */
@Getter
public class CombinationRecipe
{
	private final String name;
	private final int resultItemId;
	private final int resultQuantity;
	private final Map<Integer, Integer> ingredients;
	@Setter
	private boolean applyOffsets;
	@Setter
	private boolean collapsed;

	public CombinationRecipe(String name, int resultItemId, int resultQuantity)
	{
		this.name = name;
		this.resultItemId = resultItemId;
		this.resultQuantity = Math.max(1, resultQuantity);
		this.ingredients = new HashMap<>();
	}

	public void addIngredient(int itemId, int quantity)
	{
		ingredients.put(itemId, quantity);
	}

	public int getIngredientQuantity(int itemId)
	{
		return ingredients.getOrDefault(itemId, 0);
	}

	public boolean validate()
	{
		if (ingredients.size() > 10)
		{
			return false;
		}

		for (int quantity : ingredients.values())
		{
			if (quantity <= 0)
			{
				return false;
			}
		}

		return true;
	}

	public int getTotalIngredientCount()
	{
		return ingredients.values().stream().mapToInt(Integer::intValue).sum();
	}
}
