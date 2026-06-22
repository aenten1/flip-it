# OSRS Flip Plugin — Future Work

## High Priority

### Recipe Manual Price Overrides
Manual price overrides only work for watchlist items. Recipe ingredients pull from the GE/wiki cache with no way to override. Need to add price override fields to the recipe edit dialog or allow per-ingredient price overrides inline.

### Distinct "No Data" vs "Loss" Display
When prices are unavailable (returning -1), the profit shows red — same as an actual loss. Add a distinct "N/A" or gray indicator for missing data vs red for confirmed negative profit.

### Item Search Optimization
Name search iterates ~30,000 items on the client thread with 300ms debounce. Consider caching item names on first search, or using the wiki API for name lookups, to reduce client thread load.

## Medium Priority

### Drag-and-Drop Reordering
Watchlist and recipe rows reorder via ▲/▼ buttons. A nicer UX would be click-and-drag. It's awkward
with the current architecture (rows are rich custom JPanels rebuilt each refresh) — would need a
custom drag implementation (drag ghost, drop-index math) or a move to a JList/JTable reorder model.
Deferred from the 2026-06-18 reorder work (arrows shipped first).

### Import/Export
Export watchlist + recipes to a JSON file via user dialog. Import from file. Referenced in the deployment guide but not implemented.

### Auto-Refresh on GE Open
Refresh prices when the Grand Exchange interface is opened in-game. Could use RuneLite's widget events to detect GE open.

### Config Group Migration
Config group is `"osrsflip"`. If ever renamed to something more specific, a migration must be provided per AGENTS.md rules to avoid resetting user settings.

## Low Priority

### Historical Price Tracking
Could show price trend sparklines or averages over time. Would require storing periodic price snapshots in the data file.

### Profit Notifications
Alert the user when a watched item crosses a profit threshold, so they don't have to watch the panel.

Design (from 2026-06-18 discussion):
- Use RuneLite's injected `Notifier` (desktop/tray + optional sound; respects the user's global
  notification settings). Don't roll a custom tray.
- Hook into the existing auto-refresh callback: on each refresh, recompute each watched item's flip
  profit and compare to the threshold.
- **Edge-triggered**: notify only on the transition below -> at/above the threshold (track a
  per-item "was above" flag); re-arm once it drops back below. Avoids spamming every refresh cycle.
- Timeliness is bounded by the auto-refresh interval (default 10 min) and price freshness.
- Config: opt-in toggle (default off) + threshold value, optional sound.

Open decisions:
- Threshold type: flat gp profit vs profit margin % (or either).
- Global threshold vs per-item.
- Scope: watchlist only, or recipes too.

Compliance: clean — panel/desktop notification only, no game interaction.

### Configurable Max Search Results
Currently hardcoded to 10 results. Could be a config option.

## Architecture Notes
- All item data fetching must happen on the RuneLite `ClientThread` (via `clientThread.invokeLater()`), then UI updates on the Swing EDT (via `SwingUtilities.invokeLater()`)
- HTTP requests must use OkHttp's `enqueue()` — never block the client thread
- `itemManager.getItemPrice()` must also be called on the client thread
- Data persists to `.runelite/osrs-flip-plugin/data.json` via `DataManager.java`
- GE tax: 2% floored, capped at 5M, 0 below 50gp — implemented in `ProfitCalculator.calculateTax()`
- Wiki prices fetched from `https://prices.runescape.wiki/api/v1/osrs/` (latest + 1h endpoints)
