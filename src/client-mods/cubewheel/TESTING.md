# CubeWheel manual test checklist

Run the client (`./gradlew runClient`, or drop `build/libs/cubewheel-0.1.0.jar` plus Fabric API into a 26.2 instance) and connect to ManaCube (`play.manacube.com`) unless a step says otherwise. See README.md for what each feature is meant to do.
Bind "Reload CubeWheel config" to a free key under Options > Controls > Key Binds > CubeWheel first.

## Wheel

- [ ] Hold `G` on ManaCube: the wheel opens around the screen centre, the game does not pause, the world stays visible behind a dim overlay.
- [ ] Move the mouse towards a slice: it highlights (white label, light box behind the icon); others stay grey.
- [ ] Flick towards Shops and release `G`: the wheel drills into the Shops ring and stays open ("◀ back" shows in the centre).
- [ ] In the Shops ring, left-click Sell: the wheel closes and `/sell` is sent.
- [ ] Hold `G`, flick onto Travel, click Spawn: `/spawn` is sent exactly once.
- [ ] Hold `G`, click Shops, release `G` over a slice: nothing is sent, the wheel stays open (clicking ends hold mode).
- [ ] Hold `G`, right-click back from a sub-ring, release `G` over a slice: nothing is sent, the wheel stays open.
- [ ] Hold `G`, alt-tab away (window loses focus) and release: nothing is sent; on return the wheel is open in click mode.
- [ ] Right-click inside a sub-ring goes back one level; right-click at the root closes.
- [ ] Click the centre inside a sub-ring goes back; click the centre at the root closes.
- [ ] Tap `G` and release without moving the mouse: the wheel closes, nothing is sent.
- [ ] Esc closes the wheel from any level.
- [ ] Rebind the wheel to a mouse button (e.g. Mouse 4): hold/release behaves the same as with `G`.
- [ ] On a non-ManaCube server (or singleplayer), pressing `G` shows the action-bar hint "CubeWheel is only active on ManaCube" and no wheel.
- [ ] Set `"enabled": false` in `config/cubewheel.json`, press the reload key: `G` does nothing (no wheel, no hint).
- [ ] Edit `vaultCount` to 5, press the reload key: green "[CubeWheel] config reloaded"; Vaults shows Vault 1..5 plus the `/ec` and `/p vault` extras (7 entries: stays a wheel).
- [ ] Break the JSON (delete a brace), press the reload key: red "[CubeWheel] cubewheel.json: ..." message; the old wheel still opens and works.
- [ ] Isles & Bosses (9 entries) opens as a list instead of a sub-ring.

## List

- [ ] Typing filters rows by label or command (case-insensitive); "(no matches)" when nothing matches.
- [ ] Enter runs the first visible row.
- [ ] Mouse wheel scrolls when the list is taller than the screen (shrink the window to check).
- [ ] Click a row: the screen closes and the command is sent.
- [ ] Esc or right-click returns to the wheel it was opened from; the wheel stays open in click mode (releasing `G` earlier does not close it).

## Homes

- [ ] Delete `config/cubewheel-homes.json`, open the wheel and go to Homes: it first shows "Loading…" (clock icon; clicking it does nothing), then your homes appear within ~1 s.
- [ ] The `/homes` reply triggered by the wheel is not shown in chat; the log shows one "[cubewheel] fetching /homes for …" line.
- [ ] Typing `/homes` yourself shows the reply in chat normally (and still updates the cached list).
- [ ] `/sethome test`: reopen the wheel, "test" is in the Homes ring immediately (no new `/homes` in the log).
- [ ] `/delhome test`: reopen the wheel, "test" is gone.
- [ ] Reopening Homes within 5 minutes sends no `/homes` (no new "fetching /homes" log line); after 5 minutes one is sent again.
- [ ] Reply not recognised (e.g. a server whose `/homes` format the parser rejects): after ~3 s the "Loading…" entry turns into "↻ Refresh" without moving the mouse, and that timeout sends nothing (no new "fetching /homes" log line); clicking Refresh re-sends `/homes` at most once per 30 s and the Homes ring re-resolves in place (no extra ring level: one right-click goes back to the root). Clicking again sooner shows Refresh again, no new "fetching /homes" log line.
- [ ] With the Homes ring open, a homes-looking chat message arriving (e.g. type `/homes` yourself, or have the server print a homes list) never sends `/homes`: no new "fetching /homes" log line.
- [ ] Back never sends `/homes`: add a sub-ring to the Homes node's `children` (e.g. `{"label":"Sub","children":[{"label":"Spawn","command":"/spawn"}]}`), reload, and with a stale cache open Homes (one "fetching /homes" line) then Sub; wait over 30 s and right-click back into Homes: no new "fetching /homes" line.
- [ ] With a stale but non-empty cached list, opening Homes shows the old homes immediately (no "Loading…") and they update when the reply arrives.
- [ ] If the `/homes` reply has a "next page" click (`/homes 2`), no home named "2" appears.
- [ ] With no homes set, `/homes` replies "none" (or similar): the ring shows only "↻ Refresh" (plus any configured extras); the reply is hidden, and reopening within 5 min sends no `/homes`.
- [ ] A Homes ring grown past `listThreshold` opens as a list once the homes arrive.
- [ ] Config reload and other "[CubeWheel] …" chat lines still show normally.

## Tracker & capture

Bind "Toggle tracker HUD", "Open tracker picker" and "Toggle capture mode" under Options > Controls > Key Binds > CubeWheel first.

- [ ] Before opening any menu, the picker shows "Open /jobs, /pquests, /prestige or /challenges to start tracking."
- [ ] Open `/jobs`, wait a second, close it: the picker lists the jobs that show progress (e.g. `1,200 / 1,500`) under a "jobs" header, sorted by percentage; decorative items without progress are absent.
- [ ] The mod never clicks in, opens or closes a menu itself, and sends no command while tracking.
- [ ] Items from your own inventory (bottom half of the menu) never appear in the picker.
- [ ] Click an entry in the picker: its ☆ becomes ★ and the top-right HUD shows it with an age suffix ("now", later "5m"). Click again to unpin.
- [ ] The HUD shows pinned entries first, then every other incomplete entry, closest to done first (not only those at ≥80%); at most 8 lines (`tracker.hudMaxLines`; a config file written before this change keeps its old value, e.g. 6). Entries at ≥80% are yellow, the rest white; complete entries appear only when pinned (green).
- [ ] Estimated entries on the HUD keep their `~` (and `✓?` at the target) and move up the list as they are counted.
- [ ] The picker's top line reads "Left-click: pin to HUD   Right-click: hide from HUD". Right-click an entry: it turns dark grey with "(hidden)" and disappears from the HUD, even if pinned. Right-click again: back on the HUD. Hidden entries survive a restart (`hidden` in `config/cubewheel-tracker.json`).
- [ ] With a potion effect active, the HUD sits below the effect icons.
- [ ] The toggle-HUD key hides/shows the HUD ("Tracker HUD OFF/ON" on the action bar) and the setting survives a restart (`tracker.hudVisible` in `config/cubewheel.json`).
- [ ] Break `config/cubewheel.json` (delete a brace), press the reload key (red error), then the toggle-HUD key: the HUD toggles, the action bar says "CubeWheel: HUD toggled for this session (config has errors, not saved)", and the broken file on disk is unchanged. Fix the file and reload: toggling saves again.
- [ ] F1 hides the HUD together with the vanilla HUD.
- [ ] On a non-ManaCube server the HUD is not drawn and opening menus records nothing; the picker still opens (local data).
- [ ] "Forget entries older than 7 days" removes unpinned entries not seen for a week (edit `seenAt` in `config/cubewheel-tracker.json` to test); pinned ones stay.
- [ ] Capture key: action bar "Capture ON → config/cubewheel-captures". Run `/homes` (also via the wheel, whose reply is hidden), then open `/jobs`, `/pquests`, `/prestige`, `/challenges`: `config/cubewheel-captures/<UTC date>.jsonl` gets one `"kind":"chat"` line per chat message and one `"kind":"container"` line per menu (another only if the contents change while it is open).
- [ ] While capture is on, action-bar text (e.g. job XP popups while farming) adds `"kind":"actionbar"` lines, and boss bars (quest/event bars) add `"kind":"bossbars"` lines with `name` and `progress`; each is written only when the value changes (standing still with an unchanged bar writes nothing). Action-bar game messages also appear as `"kind":"chat"` lines with `"overlay":true`, with consecutive repeats skipped.
- [ ] The game log shows no mixin errors for `cubewheel.mixins.json` at startup. (The mixins are optional: if one fails to apply the game still starts, and with capture on the log shows a single "action-bar/boss-bar capture disabled for this session" warning instead of per-tick errors.)
- [ ] Capture key again: "Capture OFF"; no further lines are written. Capture is off again after a restart.
- [ ] Send the capture file back so the progress and homes parsers can be tuned.

## Live sidebar

- [ ] On ManaCube with the Survival sidebar showing `Skills: Lvl …`: open the prestige rank menu once so "Rank [✪n] · Reach N Skill Level" is tracked, pin it. Gain a skill level: within a second the HUD entry's current value becomes the new level and its age shows "now", without opening any menu.
- [ ] The value never goes down: after re-opening the prestige rank menu, the entry shows the higher of the menu's and the sidebar's value.
- [ ] Complete (green) entries are not changed by the sidebar.
- [ ] Add `"Mana": "(?i)mana"` to `tracker.sidebarLinks`, reload: an entry whose name contains "mana" follows the sidebar's Mana value. `"sidebarLinks": {}` switches linking off.
- [ ] `config/cubewheel-tracker.json` is rewritten at most every ~10 s while linked values change (watch its modification time). Gain a skill level and quit (or disconnect) within 10 s: after a restart the entry has the new value.
- [ ] The default link only moves "… · Reach N Skill Level" entries; a quest merely named after skill levels is not changed. An old config with `"Skills": "(?i)skill level"` is upgraded to `"(?i)reach [\\d,]+ skill level"` when loaded.
- [ ] A sidebar clock line such as `Time: 12:30` is not read as a number.
- [ ] Capture on: `"kind":"sidebar"` lines with `title` and `lines` appear when the sidebar changes (not every tick); lines read like `Money: $2.89M`, `Skills: Lvl 1851` (icon glyphs may show as odd characters).
- [ ] No lag: with the sidebar changing constantly (money ticking), FPS is unchanged.
- [ ] On a non-ManaCube server the sidebar is ignored (no captures, no tracker changes).

## Refresh key

Bind "Refresh trackers" under Options > Controls > Key Binds > CubeWheel first.

- [ ] Press it once with no screen open: `/pquests`, `/prestige`, `/jobs` are sent one after another (log: "[cubewheel] tracker refresh: …"); each menu opens and closes by itself within about a second, the next command only after the previous menu closed. Chat ends with a grey `[CubeWheel] Refreshed N trackers`, N = the entries those menus showed (unchanged ones included, entries from earlier runs not).
- [ ] Hiding: during the run the menus are not drawn (only the mouse cursor briefly appears). If the log shows "hiding refresh menus disabled for this session" or mixin errors for `ScreenHideMixin`, the menus flash instead; the run still works.
- [ ] Nothing in a menu is ever clicked: move the mouse and click during a run; no item is taken or moved and no click reaches the menu (keys other than Esc are ignored too).
- [ ] Press it again within 60 s: `[CubeWheel] refresh skipped: wait Ns` and nothing is sent. After 60 s it runs again.
- [ ] Press Esc during a run: the current menu closes, `[CubeWheel] refresh stopped: the menu was closed`, nothing more is sent.
- [ ] Open chat (T) or the inventory (E) right after pressing: `[CubeWheel] refresh stopped: another screen was opened`, no further commands.
- [ ] Press the refresh key and immediately right-click a chest (or an NPC, or hold attack): `[CubeWheel] refresh stopped: you used or attacked something`; the chest opens normally, is visible and usable, and is not closed.
- [ ] Open a chest by other means during a run (e.g. a menu that is not a tracker menu opening between commands): the run stops ("another screen was opened" / "an unexpected menu opened") and the menu stays visible and clickable.
- [ ] Late menu: if the server is slow and a tracker menu opens after its 3 s timeout, it is still read and closed (hidden) and the run carries on.
- [ ] Put a harmless command that opens no menu (e.g. `"/list"`; not one that teleports) first in `tracker.refreshCommands`, reload, press: after ~3 s the next command is sent; the final line says "(1 menu did not load)".
- [ ] `"refreshCommands": []`: pressing says `refresh skipped: tracker.refreshCommands is empty`.
- [ ] Picker: click **Refresh**: the picker closes, the run happens, the picker reopens with fresh ages and shows "Refreshed N trackers" in gold above the buttons for a few seconds. Within 60 s the button shows "refresh skipped: wait Ns" in the picker itself (and in chat) and the picker stays open.
- [ ] On a non-ManaCube server the key only shows "CubeWheel is only active on ManaCube" and sends nothing.
- [ ] Disconnect during a run: no error; the next run on reconnect works after the cooldown.
- [ ] Capture on, then run a refresh and also open a menu via a typed command: each `"kind":"container"` line has `afterCommand` naming the command (e.g. `"/prestige"`) and `afterCommandMs`. Use this to find the command that opens the "Rank [✪n]" objectives menu, then add it to `tracker.refreshCommands`.

## Job listings

- [ ] `/jobs`, then click an industry (e.g. Farming): its three listings appear in the picker under "jobs" as "Farming Beginner · Harvest Acacia Logs", "Farming Experienced · Catch Tangleroots Fireflies", "Farming Heavy · Harvest Cherry Logs", with the objective's own counter (`3,127 / 4,773`), not the hand-in line.
- [ ] The "FARMING INDUSTRY" items (level, streak, leaderboard "#1 … - 4,437 Jobs"), "Go Back" and "REFRESH JOB LISTINGS" never become entries.
- [ ] Reroll the listings ("REFRESH JOB LISTINGS", clicked by you) or complete one, then reopen the page: the old listings of that industry disappear from the picker unless pinned; other industries' listings and "GOLDEN CRATE" from the main jobs menu stay.

## Live estimates (local counting)

Open `/pquests`, `/prestige` (rank objectives) and `/challenges` once so the objectives are known, and pin the entries below. Turn capture on to see `local`/`world`/`estimate` lines while testing.

- [ ] With an open "Mine 300 Stone"-style quest (or any "Mine/Harvest or Mine ... Resources" objective), break 10 stone: the entry shows `~` and grows by 10 (percentage quests: the shown value is in objective units, e.g. `~6,437 / 10,000`); the age suffix does not change.
- [ ] Reopen `/pquests` (or press the refresh key): the `~` disappears and the menu's value is shown; the log has "[cubewheel] estimate for ...: counted N, actual M" (capture: a `"kind":"estimate"` line).
- [ ] Break fully grown wheat: "Harvest N Crops"/"Harvest N Wheat" go up by 1 each; breaking unripe wheat or a melon/pumpkin stem adds nothing. A melon block counts.
- [ ] Place a stone and break it again: no count. Break a crop you planted once it is grown: counts.
- [ ] Break a block inside another player's claim (the block comes back): the count goes up and back down within a second (capture: `"signal":"reject"`).
- [ ] Kill a mob with a sword and one with a bow: "Kill N Mobs" +1 each. A mob another player finished off (they hit it last) does not count.
- [ ] Kill a monster in the overworld: an objective scoped to Sandara or to "special worlds" does not move; in Sandara (or another Mana world) it does. Capture: the `"kind":"world"` line shows the dimension and `tokens` (note whether the dimension is named after the world and whether the sidebar has a `World:` line).
- [ ] Catch a fish: "Catch N Fish" +1. Reeling in without a bite adds nothing.
- [ ] Push an estimate to its target: `~300 / 300 (99%) ✓?` in yellow, never green, until a menu read.
- [ ] Picker: estimated rows end with `+N~`; hovering one shows "Estimated from what you did since this menu was last read (…)" and, after a snap-back, "Last check: counted …, actual …".
- [ ] Linked sidebar values (e.g. Skills) replace estimates too.
- [ ] Quit and restart: estimates are still shown (`~`) until the next read. `config/cubewheel-tracker.json` has `objectives` and `estimates` and is rewritten at most every ~30 s while counting.
- [ ] `"tracker.local": {"enabled": false}` + reload: nothing counts and the `~` values disappear; set it back: the stored estimates reappear.
- [ ] Nothing is ever sent: counting produces no chat commands and opens no menu (watch the log and chat).
- [ ] On a non-ManaCube server nothing is counted.
- [ ] The game log shows no mixin errors for `LocalCounting*Mixin`/`FishingHookAccessor`. (Optional: if one fails to apply, only that kind of counting stops; a hook that throws is logged once and switched off after 10 failures.)
- [ ] No FPS change while mining, fighting or fishing.
