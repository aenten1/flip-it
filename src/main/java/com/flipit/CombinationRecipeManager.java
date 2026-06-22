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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages combination recipes. Insertion-ordered so the display order is stable
 * and user-reorderable; all access is synchronized since the UI (EDT) and the
 * refresh executor can touch it concurrently.
 */
public class CombinationRecipeManager
{
	private final Map<String, CombinationRecipe> recipes = new LinkedHashMap<>();

	public synchronized void addRecipe(CombinationRecipe recipe)
	{
		if (recipe.validate())
		{
			recipes.put(recipe.getName(), recipe);
		}
	}

	public synchronized CombinationRecipe getRecipe(String name)
	{
		return recipes.get(name);
	}

	public synchronized List<CombinationRecipe> getAllRecipes()
	{
		return new ArrayList<>(recipes.values());
	}

	public synchronized boolean removeRecipe(String name)
	{
		return recipes.remove(name) != null;
	}

	public synchronized int getRecipeCount()
	{
		return recipes.size();
	}

	/**
	 * Moves the named recipe by {@code delta} positions in display order
	 * (-1 = up, +1 = down). No-op if it can't move that way.
	 */
	public synchronized void moveRecipe(String name, int delta)
	{
		List<CombinationRecipe> list = new ArrayList<>(recipes.values());
		int idx = -1;
		for (int i = 0; i < list.size(); i++)
		{
			if (list.get(i).getName().equals(name))
			{
				idx = i;
				break;
			}
		}
		int target = idx + delta;
		if (idx < 0 || target < 0 || target >= list.size())
		{
			return;
		}
		Collections.swap(list, idx, target);
		recipes.clear();
		for (CombinationRecipe r : list)
		{
			recipes.put(r.getName(), r);
		}
	}
}
