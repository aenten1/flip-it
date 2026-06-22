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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages manual price overrides set by the user.
 */
public class ManualPriceManager
{
private final Map<Integer, Integer> manualBuyPrices;
private final Map<Integer, Integer> manualSellPrices;

public ManualPriceManager()
{
this.manualBuyPrices = new ConcurrentHashMap<>();
this.manualSellPrices = new ConcurrentHashMap<>();
}

public void setManualBuyPrice(int itemId, int price)
{
if (price >= 0)
{
manualBuyPrices.put(itemId, price);
}
}

public void setManualSellPrice(int itemId, int price)
{
if (price >= 0)
{
manualSellPrices.put(itemId, price);
}
}

public Integer getManualBuyPrice(int itemId)
{
return manualBuyPrices.get(itemId);
}

public Integer getManualSellPrice(int itemId)
{
return manualSellPrices.get(itemId);
}

public int getEffectiveBuyPrice(int itemId, int gePrice)
{
Integer manual = getManualBuyPrice(itemId);
return manual != null ? manual : gePrice;
}

public int getEffectiveSellPrice(int itemId, int gePrice)
{
Integer manual = getManualSellPrice(itemId);
return manual != null ? manual : gePrice;
}

public void clearManualPrices(int itemId)
{
manualBuyPrices.remove(itemId);
manualSellPrices.remove(itemId);
}

public boolean hasManualPrice(int itemId)
{
return manualBuyPrices.containsKey(itemId) || manualSellPrices.containsKey(itemId);
}
}
