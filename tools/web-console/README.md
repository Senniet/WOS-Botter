# SurvivalOps Web (parallel to the Windows MSI)

The original Java automation engine, task registry and scheduler run on the `webserver` host in a separate workspace and checkout. The web UI is an authenticated HTTPS Python gateway, while Java owns the local-only API (`127.0.0.1:18765`). Only Serve-Emu is supported.

## Addresses

- Home LAN: `https://192.168.1.20:9098/` (TrueNAS reverse TCP proxy -> SSH remote tunnel -> webserver)
- Webserver network: `https://10.0.0.100:9098/`
- Internal Java API: `127.0.0.1:18765` (never expose publicly)
- TrueNAS Serve-Emu UI: `https://192.168.1.20:8448/`

The app uses a self-signed TLS certificate covering both IP addresses (stored only in `~/.config/survivalops-web`). Never copy the TLS private key or password file into Git.

## Deployment

Webserver user services:

- `survivalops-web-engine.service`: Java bot
- `survivalops-web.service`: authenticated HTTPS frontend
- `survivalops-web-lan-tunnel.service`: SSH reverse tunnel to TrueNAS using an existing authorized deployment key, remote bound to `127.0.0.1:19098`

TrueNAS runs the separate Docker container `survivalops-web-lan-proxy` with host networking, bound only to `192.168.1.20:9098`. It forwards TCP/TLS to the SSH tunnel and is set to restart automatically. It does **not** modify the existing Serve-Emu application.

Access password is initially provisioned under `~/.config/survivalops-web/initial-password.txt`, and may be changed in the Web Settings page. Password hash, TLS key, and session signing key must never be committed. The QA dashboard remains available separately.

## Current validation and limitations

- Web API, browser assets, task registry, profile persistence, frontend login and local Serve-Emu screenshot read have been tested.
- Browser UI exposes 65 Java task enum entries and 285 engine settings; the historical QA archive tracks 62 active/triaged routines (different counts, not identical universes).
- Web Task Builder supports point tap, swipe, wait, back and simple linear flows. Advanced FXML editor and MSI-only features have **not** been ported.
- The web engine is **stopped by default**, with no tasks enabled. No claim of complete live task-by-task E2E verification; use shared ADB maintenance lock and coordinate with the Windows MSI before enabling.
- The gateway temporarily blocks web controls when it detects recent external Task Coach evidence; this is a conservative signal, not a guaranteed global lease.

Avoid running the Windows client and Web scheduler against the same emulator simultaneously.

## Task behavior controls and live display

The Task Management page now reads both `/api/tasks?profile=...` and `/api/settings?profile=...`, maps the original task-specific configuration keys to an expandable behavior panel on each routine, and writes changes back through `/api/setting`. This preserves all original MSI configuration keys and per-profile values. The task's own activation switch is not duplicated in its options, nor is another routine's activation switch exposed as a behavior setting. Internal state fields (last-run timestamps, consecutive failure counts, etc.) are hidden from the ordinary behavior panel. Technical keys remain available via the explicit Advanced checkbox in System Settings; default view shows only core system settings.

Serve-Emu is rendered read-only in an embedded native `https://192.168.1.20:8448/` viewer; its own frontend and WebSocket worker deliver live frames. Automatic periodic ADB screenshot polling was removed. The former ADB screenshot/click/back controls remain as an explicitly requested fallback and refuse manual input while web automation or other detected controllers are active. The embedded iframe is inert to prevent accidental gameplay clicks while viewing. For full interactive native control use the separate Serve-Emu link.

Validation: `node tools/web-console/test_task_ui.cjs` (reads original Java task/setting APIs, asserts option ownership and security-sensitive UI behavior), `node --check tools/web-console/static/app.js`, `python3 -m unittest tools/web-console/test_web_console.py`, and Maven reactor build. Native Serve-Emu WebSocket upgrade and inbound data were verified on port 8448. Playwright Chromium validates the interface on desktop and mobile viewports; mobile Safari itself has not been visually tested.

## Task workspace redesign (November 2026)

The Task Management page is now an operator workbench rather than a grid of task cards. The 65 registered Java routines are assigned exactly once to nine curated Whiteout Survival work areas (daily routines, city, army, world map, heroes, alliance, rewards, events, system). The left navigation displays enabled/total counts, the central list shows task state and an immediate enable switch, and the right inspector contains the selected task's explanation, execution history, schedule actions, and all relevant saved MSI options. On narrow viewports the inspector becomes a dismissible bottom sheet. Filtering supports all/enabled/failed/favorites, contextual search supports common Dutch/English aliases, sorting supports logical/name/status, and starred tasks are remembered locally in the browser per profile.

The ordinary Overview page was also restyled into a flatter control-room layout; the original dashboard and Java API functionality is retained. The standalone MSI project and existing user task values were not touched. The automated browser test uses intercepted `/api/setting` and `/api/task` calls to verify switch payloads **without altering production tasks**.

Validation: `node tools/web-console/test_task_ui.cjs` uses the live Java task/setting endpoints and checks all 65 unique group assignments and option ownership. `node tools/web-console/test_browser.cjs` uses Playwright-core with an installed headless Chromium, accepts `SURVIVALOPS_WEB_BASE=https://192.168.1.20:9098/`, and checks authenticated navigation, desktop/mobile layout widths, task detail drawer, search, favorites and intercepted original API calls. JavaScript syntax, Python gateway smoke/security tests, and live HTTPS availability can be checked independently. The browser test reads the initial password file only from local private storage; when changed, provide its replacement through an approved local test mechanism. Do not commit credentials or unredacted screenshots.

## English operator interface (November 2026)

All operator-facing pages (including login, dashboard, task workbench, scheduler dialogs, task builder, profiles, QA labels, settings, live Serve-Emu, notifications and password-change errors) now use English and game-aligned Whiteout Survival terms. The HTML language is `en`; date formatting uses `en-GB` for a clear 24-hour clock. Each of the 65 registered routines has an explicit English label and purpose, and behavior controls use English names without changing persisted MSI config keys. Historical diagnostics and user-entered profile names/log lines remain as originally recorded (not translated or altered).

Browser tests check desktop and mobile navigation and the English task workbench. Python tests protect against reintroducing common Dutch UI wording. Localization changes are frontend-only apart from English server error messages and do not start the scheduler or change user settings.

## Compact Serve-Emu viewport (November 2026)

The Serve-Emu page is focused on the actual native live viewer: duplicate section title/intro removed; a 39 px compact connection heading remains; top navigation, top margins and panel padding are reduced only while on the emulator page. The continuous iframe uses the available dynamic viewport height with desktop and mobile minimums. Existing viewer URL, read-only safety, ADB fallback controls, connection status and other pages remain unchanged. The browser smoke checks that the live viewport starts near the top on desktop and mobile.

## Windhowler Endless Trial and Daily Overview (2026-11-18)

New Java task IDs: `ENDLESS_TRIAL` (209) and `DAILY_OVERVIEW_CHECK` (38), both **disabled by default** in the web profile. The latter opens the Daily sidebar, records which daily activities are visible (Arena, Pet Adventure, Alliance Contribution, Lighthouse), closes the sidebar and repeats at a configurable interval (240 minutes by default). It is **read-only** and does not launch or enable those activities. Wish Station is intentionally excluded because the user's Furnace has not unlocked it.

Endless Trial accepts a profile-configured attack target from 1 to 3 (default/max 3), and uses the 720x1280 Serve-Emu native Events -> Endless Trial -> Daily Challenges UI. It reads daily progress from an eligible tier's visual control (plus an exact 0/3 template and exact 3/3 three claimed-checkmark detector) and never assumes a successful attack from a mere button tap. Actual flow: Go from the Events page (the accompanying countdown is **remaining event duration**, not an attack cooldown), Windhowler World boss -> Attack -> prefilled formation -> Deploy -> await either verified damage result or a stable World screen -> reopen Endless Trial and confirm a higher participation count. At **3/3**, it taps precisely one third-tier Claim control, which the game uses to collect all three rewards at once. Already-claimed 3/3 is recognized as successful without tapping Claim again. Failure states are deferred rather than guessed, and the original Home/World recovery is retained. A native Java exclusive UI transaction prevents injected automation from interrupting the multi-screen flow.

**Live evidence on 2026-11-18:** A human-guided first and third attack plus an automated second Java attack raised Windhowler 0/3 -> 3/3. At 3/3 the live Java routine submitted **exactly one Claim** and recorded **SUCCEEDED**, with gem balance increasing **1,213 -> 1,713**. Subsequent live Java Run Now of the same routine recognized all rewards **already claimed**, skipped the button entirely, and finished **SUCCEEDED**. Daily Overview Check also ran once from the Java scheduler and finished **SUCCEEDED**, with verified Home return. All temporary profile switches were restored to false and the engine was stopped. Full 0/3 -> 3/3 automation without manual first/third attacks is still awaiting a new daily reset for real end-to-end confirmation; the normal-result World fallback was tested on saved screenshots but not by a complete live three-attack cycle. Do not claim otherwise.

Test screenshots under `modules/tasks/src/test/resources/endlesstrial` have unrelated player identifiers/game backgrounds blacked out; they retain only the necessary recognition regions. Test with `./mvnw -q -pl modules/tasks -am test -Dtest=EndlessTrialLiveFrameTest,EndlessTrialBattleFrameTest -Dsurefire.failIfNoSpecifiedTests=false`, the regular frontend/browser tests, and a Maven install. The full unrelated legacy desktop-test reactor was not run to completion due to known slow environment-dependent cases; no MSI build was requested or produced.
