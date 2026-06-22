# Plugin Hub Submission Checklist

Pre-submission audit of this plugin against the [RuneLite Plugin Hub](https://github.com/runelite/plugin-hub)
requirements and the project's `AGENTS.md` rules. Check items off before opening the plugin-hub PR.

## 🔴 Blockers — build / CI will fail

- [x] **Plugin is at the repository root.** Extracted via `git subtree split` (full history
      preserved) and published as a standalone **public** repo:
      **https://github.com/aenten1/flip-it** (default branch `main`). `./gradlew build` verified
      green at the root, so the hub build will work. The unrelated monorepo root items
      (`07flip.tar.gz`, empty `OpenSpec/` & `superpowers/`, root `README.md`/`DEPLOYMENT_GUIDE.md`,
      and `.claude/settings.local.json`) were excluded.

> **Development workflow:** `flip-it` is the single source of truth — no syncing needed. The
> local directory `OSRS-Flip-Plugin/osrs-flip-plugin/` is now a direct clone of this repo (its
> `origin`), and the outer monorepo ignores it. Just edit there, `git commit`, and `git push`;
> changes land straight in this repo. (The old subtree-split sync step is gone.)
- [x] **`LICENSE` file (BSD 2-Clause)** added at the plugin root.
- [x] **Full BSD-2 header on every source file** (was: 7 files + the test had an
      "All rights reserved" stub instead of the permissive header).

## 🟠 Manual-review blockers

- [x] **Third-party-server toggle is opt-in + warned.** `useWikiPrices` now defaults to `false`
      and carries the required `warning`: *"This feature submits your IP address to a 3rd-party
      server not controlled or verified by RuneLite developers."*
- [x] **All wiki requests stay behind that toggle.** The price-history chart prompts to enable
      "Use Wiki Prices" if it's off; the moving-average / volatility price source (`fetchSeries`)
      only runs when the toggle is on. No wiki requests are made while it's disabled.

## 🟡 Polish — likely reviewer comments

- [x] **Renamed to "Flip It"** (matches the in-app panel title). Updated `@PluginDescriptor`
      `name`, `runelite-plugin.properties` `displayName`, and the docs. Package, directory,
      `@ConfigGroup("osrsflip")`, and all keyNames were left intact (renaming them would reset
      users' settings).
- [x] **Author set** to `aenten1` in `runelite-plugin.properties` (was `Flip Developer`).
- [x] **Icons set** — a 30×29 OSRS coins icon at the sidebar resource path
      (`src/main/resources/.../osrsflip/icon.png`) and a repo-root `icon.png` (≤48×72) for the
      Plugin Hub listing.
- [ ] **(Optional) Disk I/O off the client thread.** `DataManager.save()` runs synchronously in
      `shutDown()` (client thread). It's a tiny JSON file and widely tolerated, but moving it off
      the client thread would fully satisfy the "no blocking disk I/O on the client thread" rule.

## ✅ Verified clean (no action needed)

- No reflection in the prohibited sense (`gson.reflect.TypeToken` / `java.lang.reflect.Type` are
  standard Gson generics).
- No `Process`/`ProcessBuilder`, JNI/JNA, `Unsafe`, dynamic classloading, or Java serialization.
- HTTP via OkHttp `enqueue()` with an `@Inject`ed `OkHttpClient`; responses closed; off the client thread.
- `@Inject Gson` (derived via `gson.newBuilder()`); no hand-rolled `new Gson()`.
- File I/O confined to `RuneLite.RUNELITE_DIR/osrs-flip-plugin/`.
- No `META-INF/services/net.runelite.client.plugins.Plugin` file.
- Java 11 target, UTF-8 encoding.
- Config group `"osrsflip"` is specific (not the `example` template default).
- `shutDown()` cancels the scheduled task, `shutdownNow()`s the executor, removes the nav button.
- No build artifacts committed (the tracked `gradle/wrapper/gradle-wrapper.jar` is expected).
- `log.debug` only — no per-event `log.info` spam.
- Feature (a GE price/profit side panel) falls in no forbidden category
  (boss/PvP/menu/input/privacy).

## Opening the plugin-hub PR

**Status: ready to submit.** All blockers cleared; `./gradlew clean build` is green at the repo root.

1. Fork `runelite/plugin-hub`, create a branch.
2. Add a file at `plugins/flip-it` (no extension) containing:
   ```
   repository=https://github.com/aenten1/flip-it.git
   commit=<latest flip-it HEAD — run `git rev-parse HEAD` in this repo>
   ```
   Bump `commit` to the current HEAD on every release so the hub picks up new code.
3. Push and open a PR describing the plugin (see the drafted body below / in chat).
4. If CI fails, fix here in `flip-it`, update the `commit=` hash, and push to the same PR.
