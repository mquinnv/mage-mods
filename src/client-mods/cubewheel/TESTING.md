# CubeWheel manual test checklist

Run the client (`./gradlew runClient`, or drop `build/libs/cubewheel-0.1.0.jar` plus Fabric API into a 26.2 instance) and connect to ManaCube (`play.manacube.com`) unless a step says otherwise. See README.md for what each feature is meant to do.
Bind "Reload CubeWheel config" to a free key under Options > Controls > Key Binds > CubeWheel first.

## Wheel

- [ ] Hold `G` on ManaCube: the wheel opens around the screen centre, the game does not pause, the world stays visible behind a dim overlay.
- [ ] Move the mouse towards a slice: it highlights (white label, light box behind the icon); others stay grey.
- [ ] The top level reads, clockwise from the top: Crops (Sushi), Spawners (Sushi), Jobs, Kilton, Vaults, Fly: on/off, Progress, Daily reward…, Boss event…, More.
- [ ] Flick towards More and release `G`: the wheel drills into More and stays open ("◀ back" shows in the centre).
- [ ] In More › Sell, left-click Sell menu: the wheel closes and `/sell` is sent.
- [ ] Hold `G`, open More › Travel, click Spawn: `/spawn` is sent exactly once.
- [ ] Hold `G`, click More, release `G` over a slice: nothing is sent, the wheel stays open (clicking ends hold mode).
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
- [ ] No ring under More opens as a list (each has at most 8 entries); with `"listThreshold": 3` + reload, More › Shops opens as a list.
- [ ] Upgrade: with a pre-update `cubewheel.json` (configVersion 2) that has an extra entry such as `{"label": "Island", "command": "/is"}`, start the game: the log shows "wheel upgraded to the new default layout; your entries were moved to More › Custom: Island (/is)", More › Custom holds it, the file now says `"configVersion": 3`, and other settings (vaultCount, HUD) are unchanged. Restart: no second upgrade.

## List

- [ ] Typing filters rows by label or command (case-insensitive); "(no matches)" when nothing matches.
- [ ] Enter runs the first visible row.
- [ ] Mouse wheel scrolls when the list is taller than the screen (shrink the window to check).
- [ ] Click a row: the screen closes and the command is sent.
- [ ] Hold the wheel key (a letter key such as `C`), click a ring that opens as a list and keep holding: no `c`s appear in the filter (no key repeat either); release the key: nothing runs, the list stays open, and the filter now takes typing.
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
- [ ] Click an entry in the picker: its ☆ becomes ★ and the Tracker panel (top left, under the Jobs panel) shows it with an age suffix ("now", later "5m"). Click again to unpin.
- [ ] The HUD shows pinned entries first, then every other incomplete entry, closest to done first (not only those at ≥80%); at most 10 lines (`tracker.hudMaxLines`; an older config file with 6 or 8 is upgraded to 10 once on load and gets `"configVersion": 2`; set it back to 6, reload: it stays 6). Entries at ≥80% are yellow, the rest white; complete entries appear only when pinned (green).
- [ ] "Haven Harvester" (Progress: 67% of "Harvest or Mine 10,000 Wolfhaven Resources") shows `6,700 / 10,000 (67%)` on the HUD and in the picker, without `~`; "Discoverer" (two objectives) still shows `70 / 100 (70%)`.
- [ ] Hold the capture key for two seconds: capture toggles once ("Capture ON"), not on and off again. Same for the HUD key.
- [ ] Estimated entries on the HUD keep their `~` (and `✓?` at the target) and move up the list as they are counted.
- [ ] The picker's top line reads "Left-click: pin to HUD   Right-click: hide from HUD". Right-click an entry: it turns dark grey with "(hidden)" and disappears from the HUD, even if pinned. Right-click again: back on the HUD. Hidden entries survive a restart (`hidden` in `config/cubewheel-tracker.json`).
- [ ] With a potion effect active, nothing is drawn top right any more (the tracker is a panel on the left).
- [ ] World filter (`tracker.worldFilter` = `"sort"`, default): track "Slay N Tigers in Tangleroots", a Wolfhaven quest and "Mine N Stone", none pinned. In Tangleroots the tiger job is the first unpinned line and the Wolfhaven quest the last; in Wolfhaven the order flips; at spawn both world entries sit below "Mine N Stone". Set `"hide"` and reload: in Tangleroots the Wolfhaven quest is gone from the HUD (still in the picker) unless pinned. `"off"`: the usual closest-to-done order everywhere.
- [ ] The toggle-HUD key hides/shows the HUD ("Tracker HUD OFF/ON" on the action bar) and the setting survives a restart (`tracker.hudVisible` in `config/cubewheel.json`).
- [ ] Break `config/cubewheel.json` (delete a brace), press the reload key (red error), then the toggle-HUD key: the HUD toggles, the action bar says "CubeWheel: HUD toggled for this session (config has errors, not saved)", and the broken file on disk is unchanged. Fix the file and reload: toggling saves again.
- [ ] F1 hides the HUD together with the vanilla HUD.
- [ ] On a non-ManaCube server the HUD is not drawn and opening menus records nothing; the picker still opens (local data).
- [ ] On another ManaCube gamemode or the hub (sidebar not titled "SURVIVAL"): opening menus adds no tracker entries and the refresh key says "Tracker refresh only works in ManaCube Survival". Back in Survival both work again.
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
- [ ] Picker: click **Refresh**: the picker closes, the run happens, the picker reopens with fresh ages and shows "Refreshed N trackers" in gold above the buttons for a few seconds (not "refresh stopped: another screen was opened"). Within 60 s the button shows "refresh skipped: wait Ns" in the picker itself (and in chat) and the picker stays open.
- [ ] On a non-ManaCube server the key only shows "CubeWheel is only active on ManaCube" and sends nothing.
- [ ] Disconnect during a run: no error; the next run on reconnect works after the cooldown.
- [ ] Capture on, then run a refresh and also open a menu via a typed command: each `"kind":"container"` line has `afterCommand` naming the command (e.g. `"/prestige"`) and `afterCommandMs`. Use this to find the command that opens the "Rank [✪n]" objectives menu, then add it to `tracker.refreshCommands`.

## Job listings

- [ ] `/jobs`, then click an industry (e.g. Farming): its three listings appear in the picker under "jobs" as "Farming Beginner · Harvest Acacia Logs", "Farming Experienced · Catch Tangleroots Fireflies", "Farming Heavy · Harvest Cherry Logs", with the objective's own counter (`3,127 / 4,773`), not the hand-in line.
- [ ] The "FARMING INDUSTRY" items (level, streak, leaderboard "#1 … - 4,437 Jobs"), "Go Back" and "REFRESH JOB LISTINGS" never become entries.
- [ ] Reroll the listings ("REFRESH JOB LISTINGS", clicked by you) or complete one, then reopen the page: the old listings of that industry disappear from the picker unless pinned; other industries' listings and "GOLDEN CRATE" from the main jobs menu stay.

## Jobs panel

- [ ] With listings of several industries read: a "Jobs" panel at the top left, below the Events/Boosters/Cooldowns panels (none overlap; with their `y` set to 80, it stays below them), lists every listing grouped "⚒ Farming", "⚒ Hunting", … with Beginner, Experienced, Heavy in that order, even with more than `hudMaxLines` listings.
- [ ] Title reads "Jobs · Golden Crate 4/5" after the main `/jobs` menu was read.
- [ ] The Tracker panel shows no job entries, also pinned ones; "Toggle jobs panel" hides the panel ("Jobs panel OFF …" on the action bar) and the job entries are back on the Tracker panel; the setting survives a restart.
- [ ] A finished listing stays green with "· hand in" until the industry page no longer offers it.
- [ ] Breaking cherry logs moves "Heavy · Harvest Cherry Logs" live with "~".
- [ ] In Tangleroots, "Slay Tigers in Tangleroots" is white, "… in Sandara" dark grey, unscoped ones light grey.
- [ ] Hide a listing in the picker (right-click): it leaves the panel.
- [ ] An existing config with `"configVersion": 3` and events at `"y": 80`: after loading, `tracker.jobsPanel.position` is `{"corner": "top_left", "x": 4, "y": 80}` and `configVersion` is 4 (then 5).

## Tracker panel

- [ ] No tracker overlay top right any more; a "Tracker" panel sits directly under the Jobs panel on the left, exactly as wide (both panels' right edges line up), never overlapping it.
- [ ] Rows read marker, short title, count: "✦ ✪4 Skill Level 1.9k/2.5k", "✦ ✪9 Party Level 55 …/55", "⚑ Jungle Pursuit 0/15k"; counts line up on the right edge.
- [ ] "King of the Jungle" (two objectives) has two grey rows under it: "↳ 0% …", "↳ 10% Golden Knights".
- [ ] Pin one entry: grey "— Pinned" and "— Anywhere" headings appear; unpin it: with one group, no heading.
- [ ] An entry read over an hour ago ends in "·1h" (or more); newer ones show no age.
- [ ] "Toggle tracker HUD" hides/shows the panel; with `tracker.hudMaxLines` 2 only two entries show (objective rows do not count).
- [ ] "Toggle jobs panel" off: the Jobs panel goes, job entries (⚒ target) appear in the Tracker panel, which moves up.
- [ ] An existing config with `"configVersion": 4` and `tracker.jobsPanel.position` `{"corner": "top_left", "x": 6, "y": 80}`: after loading, `tracker.position` is the same and `configVersion` is 5.

## Progress popup

- [ ] Break a block a tracked job counts: "+1 <target>  ~n/m" appears in cyan just below the crosshair and fades after about 1.5 s.
- [ ] Break several quickly: one popup counting up ("+5 …") with the latest count; it fades 1.5 s after the last break.
- [ ] Kill a mob for a different entry while it shows: the popup switches to that entry ("+1 …").
- [ ] Finish an entry locally (or "You have completed the <quest>!"): green "✓ <name>  ~n/n".
- [ ] Open a /jobs or /pquests menu (a server read): no popup. Open the inventory while counting: the popup is hidden.
- [ ] Settings → HUD panels → Progress popup: "Enabled" off stops it; "Seconds shown" 4.0 keeps it about 4 s.
- [ ] Join with a full inventory: no notice. Drop an item, pick it up again: red "Inventory full" once.
- [ ] Wear a full set whose lore says "(Requires 4/4 pieces)", take one piece off: yellow "<Set> set bonus lost (3/4)"; put it back: green "<Set> set bonus active". Join or change worlds wearing it: no notice.
- [ ] Open a vault with more than 3 free slots, fill it to 3 or fewer and close it: yellow "PV n: 3 slots left" on closing; fill it completely: red "PV n full".

## Status panel

- [ ] Top row: helmet, chestplate, leggings, boots, a dot, the main- and off-hand items (empty slots left out), then "<Set> n/4" (count green at 4/4). Under it the set bonus on one line, cut with "…"; yellow "⚠ no set bonus" when too few pieces are worn; no second row for plain armor.
- [ ] A damaged breakable piece or tool under 25% durability shows Minecraft's thin bar on its icon (yellow, red under 10%); ManaCube "Unbreakable" gear never does.
- [ ] Under a thin rule, a 2×2 grid: "X Y Z  E +X" | biome with the light disc / "fps  b/s" | grass-block game time and globe clock. The columns line up, and walking around (digits changing) does not make the box or the panels below it twitch.
- [ ] Settings → Status → "Armor set" off: only the grid stays. Arrange screen: the Status panel drags by its whole box.

## Live estimates (local counting)

Open `/pquests`, `/prestige` (rank objectives) and `/challenges` once so the objectives are known, and pin the entries below. Turn capture on to see `local`/`world`/`estimate` lines while testing.

- [ ] With an open "Mine 300 Stone"-style quest (or any "Mine/Harvest or Mine ... Resources" objective), break 10 stone: the entry shows `~` and grows by 10 (percentage quests: the shown value is in objective units, e.g. `~6,437 / 10,000`); the age suffix does not change.
- [ ] Reopen `/pquests` (or press the refresh key): the `~` disappears and the menu's value is shown; the log has "[cubewheel] estimate for ...: counted N, actual M" (capture: a `"kind":"estimate"` line).
- [ ] Break fully grown wheat: "Harvest N Crops"/"Harvest N Wheat" go up by 1 each; breaking unripe wheat or a melon/pumpkin stem adds nothing. A melon block counts.
- [ ] Break grass, flowers and ferns: a "... Resources" objective does not move; breaking stone does.
- [ ] "Harvest N Sweet Berries" / "Cocoa Beans": picking a ripe bush / pod counts.
- [ ] Job listing "Farming Heavy · Harvest Cherry Logs": break a cherry log, the entry shows `~3,128 / 4,773`; "Catch … Tangleroots Fireflies" gets a `~` only in Tangleroot, from catching fireflies (below).
- [ ] In Burning Lands (if its sidebar says "World: Burning Lands" or its dimension is `burning_lands`), a "Burninglands" / "Burning Lands" objective counts.
- [ ] Place a stone and break it again: no count. Break a crop you planted once it is grown: counts.
- [ ] Break a block inside another player's claim (the block comes back): the count goes up and back down within a second (capture: `"signal":"reject"`).
- [ ] Kill a mob with a sword and one with a bow: "Kill N Mobs" +1 each. A mob another player finished off (they hit it last) does not count.
- [ ] In Tangleroots with the job "Slay N Tigers in Tangleroots" tracked, capture on: kill tigers. Each hit writes `"signal":"attack"` (raw name, passengers); a kill writes `"signal":"kill"` with `matched` = the job and `detail` `death, local hit` or `stack: 5x Tiger -> 4x Tiger (killed 1, local hit)`. If nothing counts, the `stack` / `death` / `removed` lines say why. The HUD entry shows a `~` estimate.
- [ ] Tigers (custom-model mobs: an unnamed `minecraft:slime` hitbox), capture on: the first hit on each tiger writes one `"signal":"nearby"` line (entities within 4 blocks: note whether a `text_display`/`armor_stand` carries "Tiger" and whether it is a passenger/vehicle of the slime). Each kill writes exactly one `"signal":"kill"` with `detail` `removal, method b (...)` (name tag found) or `method c (loot [Mana, Tiger Hide] -> tiger)` and `matched` = the job, and the job's `~` estimate goes up by one per tiger. No second `kill` line for the same tiger. Compare the count with `/jobs` after 10 kills.
- [ ] Kill a tiger with a katana sweep that also hits a second tiger: note whether the second one counts (a `method` line) or not (under-count, expected if no damage packet names you).
- [ ] Walk away (or teleport) right after hitting a tiger without killing it: no `kill` line; the `removed` line says `too long ago` / `too far`.
- [ ] Fireflies (Tangleroot, `minecraft:interaction` hitbox riding an `area_effect_cloud`), capture on, with "Farming Experienced · Catch Tangleroots Fireflies" tracked: catch one with the Firefly Bottle (and once by hitting). The action bar `+1  Sad Firefly` usually comes ~0.5-1 s *before* the removal; expect one `"signal":"kill"` with `detail` `removal, method c (loot [Sad Firefly] -> firefly), local hit` (or `held item [Firefly]` if no loot line showed) and the job's `~` +1. Compare with `/jobs` after 10 catches.
- [ ] Kill a Dart Frog (a real frog that dies normally): one `kill` line with `death, local hit`, no `removal` kill for it.
- [ ] Stacked mob (if any server area stacks): a name losing its count (`5x Tiger` -> `Tiger`) counts nothing; `5x Tiger` -> `4x Tiger` counts 1.
- [ ] No mixin errors for `LocalCountingHudMixin` / `TextDisplayAccessor`; no FPS change while fighting in a crowd.
- [ ] Kill a monster in the overworld: an objective scoped to Sandara or to "special worlds" does not move; in Sandara (or another Mana world) it does. Capture: the `"kind":"world"` line shows the dimension and `tokens` (note whether the dimension is named after the world and whether the sidebar has a `World:` line).
- [ ] Catch a fish in ManaCube Survival ("You caught a 52.2cm Common Flounder" in chat): "Catch N Fish" (pquests Fisherman, prestige Rank [✪8]) +1 each catch, exactly once. Reeling in without a catch adds nothing.
- [ ] With a fishing job tracked ("Fishing Beginner · Catch  Goldfish while fishing"): a Goldfish catch moves it +1 (and the any-fish entries); a Flounder moves only the any-fish entries. CamelCase species ("YellowSeaShroom") match too.
- [ ] Capture on: each catch writes a `local` `fish` line with the species as name, `detail` "chat; rarity=... size=...cm" and the entries it moved in `matched`.
- [ ] Prestige: without reopening /prestige, "Rank [✪8] · Catch 1,000 Fish" still gets `~` estimates from catches; reopening /prestige stores its objective (`objectives` in `cubewheel-tracker.json`).
- [ ] Shearing, capture on, with the job listing "Farming Experienced · Shear Sheep" tracked (open the listings page once; `config/cubewheel-tracker.json` `objectives` now has "Shear 10/84 Sheep" for it): shear a woolly adult sheep with vanilla shears. Expect a `"signal":"use"` line (`shears (held Shears); sheared before=false; pending, N ready nearby`) then a `"signal":"shear"` line with `detail` `confirmed, target` and `matched` = the job; the HUD entry shows `~11 / 84`. Right-click an already-sheared sheep or a lamb: a `use` line with `sheared before=true` / `not shearable now, ignored`, no count. Compare with `/jobs` after 10 shears.
- [ ] Shear with a custom ManaCube shears item (if any): same as above; if it shears several sheep at once, each sheep within 5 blocks gets a `shear` line `confirmed, area`. Note the `use` line's held name if nothing counts.
- [ ] Another player shears sheep next to you while you do nothing: no `shear` line, no count.
- [ ] Right-click a sheep with shears in creative or with `"tracker.local": {"shear": false}`: nothing counts.
- [ ] Area breaks (harvester), capture on, with "Harvest N Warped Wart Blocks" tracked: break warped wart blocks with the 3x3 harvester for ~30 s, then open `/jobs`. The `~` estimate should track the job within a few percent. Capture: `"signal":"area"` lines with `matched` = the job and summary lines `triggers attack=… ; counted minecraft:warped_wart_block=…`. If the job rises but `counted` does not, note the `skipped` reasons (`out_of_range`: the tool reaches past 4 blocks; `not_break`: the server replants or turns blocks into something other than air).
- [ ] Area breaks, single blocks: mine 20 stone normally with a plain pickaxe: the count goes up by 20, not more (summary: `skipped duplicate`/`not_break` only, no extra `counted`).
- [ ] Tree Feller, capture on, with "Chop N Logs" / a "... Logs" job tracked: right-click with an axe, hit an oak log (action bar `TREE FELLER ACTIVATED`), let the tree fall. Expect an `area` line `ability activated`, then `counted minecraft:oak_log=<tree's logs minus 1>`; leaves never count (`skipped not_log`). Compare with the job after 3 trees. After "Tree Feller has worn off", breaking a log again counts only that log.
- [ ] Sugar cane: break the bottom block of a 3-high cane: counts 1 (the two above pop off: `skipped cascade`). With a harvester that cuts a row of canes at one height, each cut cane counts once.
- [ ] Another player mines or chops next to you while you do nothing: no `area` counts (`NOT_ARMED`, nothing in the summary). While you mine right next to them, a few of their blocks may count (expected over-count, within 4 blocks and 1 s).
- [ ] Place a block next to you and have the harvester break it: `skipped placed`, no count.
- [ ] `"tracker.local": {"areaBreaks": false}` + reload: nothing beyond your own breaks counts; no `area` lines.
- [ ] No mixin errors for `LocalCountingLevelMixin`; no FPS change while chunks load or while mining with a harvester (the hook returns at once when no window is open).
- [ ] Push an estimate to its target: `~300 / 300 (99%) ✓?` in yellow, never green, until a menu read.
- [ ] Picker: estimated rows end with `+N~`; hovering one shows "Estimated from what you did since this menu was last read (…)" and, after a snap-back, "Last check: counted …, actual …".
- [ ] Linked sidebar values (e.g. Skills) replace estimates too.
- [ ] Quit and restart: estimates are still shown (`~`) until the next read. `config/cubewheel-tracker.json` has `objectives` and `estimates` and is rewritten at most every ~30 s while counting.
- [ ] `"tracker.local": {"enabled": false}` + reload: nothing counts and the `~` values disappear; set it back: the stored estimates reappear.
- [ ] Nothing is ever sent: counting produces no chat commands and opens no menu (watch the log and chat).
- [ ] On a non-ManaCube server nothing is counted.
- [ ] Break stone on another ManaCube gamemode (e.g. SkyBlock) or in the hub: no count.
- [ ] In creative or spectator mode (if available), breaking, killing or fishing counts nothing.
- [ ] The game log shows no mixin errors for `LocalCounting*Mixin`/`FishingHookAccessor`. (Optional: if one fails to apply, only that kind of counting stops; a hook that throws is logged once and switched off after 10 failures.)
- [ ] No FPS change while mining, fighting or fishing.

## Event timer

Bind "Toggle event HUD" under Options > Controls > Key Binds > CubeWheel first.

- [ ] In Survival, an "Events" panel is at the top left with 3 lines like `Golden Knight · 14:02`, soonest first, counting down each second; lines within 5 minutes are yellow.
- [ ] Compare with `/events`: each listed event's next start matches (note any differences, and whether KOTH also runs at 8:30 and the boss at 11:30).
- [ ] When an event is 5 minutes away, a gold chat line "[CubeWheel] <event> starts in 5 min (HH:MM)" appears once (local time). Nothing is sent to the server (chat input stays empty, no command in the log).
- [ ] Join (or reload) 2 minutes before a start: the alert appears once ("starts in 2 min"); reloading again does not repeat it.
- [ ] When the countdown reaches 0 the event moves to its next start and the list re-sorts.
- [ ] Press "Toggle event HUD": the panel hides ("Event HUD OFF"), survives a restart (`events.hudVisible`), alerts still come. Hold the key: toggles once.
- [ ] `"show": 5` + reload: five lines. `"alertMinutes": 0`: no alerts.
- [ ] Put `{"name": "Test", "when": "at <a time 6 minutes from now, New York time>"}` first in `events.schedule`, reload: it shows, alerts at 5 min. Add `{"name": "Bad", "when": "sometimes"}`: reload shows a yellow "events.schedule "Bad" ignored: ..." line and the rest keeps working.
- [ ] With boosters active too, the Boosters panel sits below the Events panel (no overlap).
- [ ] On the hub, another gamemode or a non-ManaCube server: no panel, no alerts.

## Boosters

- [ ] Capture on, activate a booster you own (e.g. a Sell booster from a crate or the Magic Pond): the capture file has a `"kind":"chat"` line with its message. Compare it with "You have received a 2x Sell Boost for 30m"; if it differs, note the real wording (and the "extended" wording when you activate a second one) and send the line.
- [ ] With a recognised message, a "Boosters" panel appears top left: `2x Sell · 29:59`, counting down each second; yellow in the last minute; gone when it reaches 0.
- [ ] A second booster of the same kind: the "extended from … to …" message sets the countdown to the new time.
- [ ] Two different boosters are listed soonest-ending first.
- [ ] Quit and restart (or reconnect) mid-booster: the panel shows the right remaining time (`config/cubewheel-boosters.json` holds `endsAt`).
- [ ] Another player typing "You have received a 2x Sell Boost for 30m" in chat (or `[SHOUT]`) adds nothing.
- [ ] Pin a tracker entry and have a potion effect active, then set `boosters.position` to `{"corner": "top_right", "x": 4, "y": 4}` and reload: the panel sits below the effect icons, never on top of them.
- [ ] `"boosters": {"enabled": false}` + reload: no panel, and new booster messages are ignored.
- [ ] On another ManaCube gamemode or the hub, or a non-ManaCube server: no panel.

## Item cooldowns

Needs a custom item with a cooldown in its lore (e.g. a crate weapon or tool with "ITEM EFFECTS: (Right-Click)" and "Cooldown: 5s"). Capture on, open a menu showing the item (e.g. `/pv 1`) so its lore is recorded as a `"kind":"container"` line; send it if anything below misbehaves.

- [ ] Right-click with a "(Right-Click)" item: a "Cooldowns" panel shows `<item name> · 5.0s` counting down (tenths under 10 s), yellow in the last 3 s, gone at 0. The ability itself works as before (nothing is cancelled).
- [ ] Right-click again while it counts: the countdown does not restart. After it ends, right-clicking starts it again.
- [ ] Right-click on a block and on a mob with the item: also starts it.
- [ ] A "(Shift + Right Click)" ability starts only while sneaking; an item with both shows "Name (Right-Click)" and "Name (Shift + Right Click)" separately.
- [ ] A "(Attack)" / "(Left-Click)" weapon: swinging at air or hitting a mob starts it.
- [ ] A "(When Consumed)" food/potion: finishing it starts the countdown; letting go of right-click halfway does not.
- [ ] A "(While Worn)" / "(When Held)" item with a cooldown never shows a countdown.
- [ ] Holding an item with "Uses: N" / "Uses Left: N" lore shows a grey `Uses: N` line that updates within half a second after a use; `"showUses": false` hides it.
- [ ] With events and boosters also showing, the three panels stack top left without overlapping; move `cooldowns.position` to `{"corner": "bottom_right", "x": 4, "y": 40}` and reload: it moves above the hotbar area on the right.
- [ ] `"cooldowns": {"enabled": false}` + reload: no panel, no countdowns.
- [ ] On the hub, another gamemode or a non-ManaCube server: nothing is shown or counted.
- [ ] The log shows no "[cubewheel] item cooldown hook failed" errors; no FPS change while clicking.

## mcMMO ability cooldowns

In Survival with a pickaxe (capture on, so the action-bar/chat lines are recorded if anything misbehaves):

- [ ] Right-click with the pickaxe ("MINING » You ready your pickaxe."), then mine: on "SUPER BREAKER ACTIVATED" the Cooldowns panel shows `Super Breaker · 4:00` (or the learned time) counting down.
- [ ] Right-click again while it counts: on "You are too tired to use that ability again. (Ns)" the countdown jumps to N seconds.
- [ ] When "Your Super Breaker ability is refreshed!" arrives, the line turns into a green `Super Breaker · ready` for about 5 s and disappears; the log shows `mcMMO cooldowns learned: {SUPER_BREAKER=N}` and `config/cubewheel-mcmmo.json` has `"Super Breaker": N`.
- [ ] The next activation counts down from the learned N (compare with the refresh message).
- [ ] Same with a shovel (Giga Drill Breaker) and an axe (Tree Feller); "too tired" goes to the tool last readied.
- [ ] Another player typing "SUPER BREAKER ACTIVATED" in chat does not start anything.
- [ ] Reconnect mid-countdown: it keeps counting. `"cooldowns": {"mcmmo": false}` + reload: no mcMMO lines (item cooldowns unchanged).
- [ ] On the hub / another gamemode: nothing is shown. The log has no "[cubewheel] mcMMO cooldown hook failed".

## Command cooldowns (/heal)

In Survival with capture on (the refusal wording is unknown: the `"kind":"chat"` line after a refused /heal is what tunes this).

- [ ] Click Heal on the wheel: on "You have been healed." the Cooldowns panel shows `/ Heal · 5:00` (golden apple) counting down, yellow in the last 10 s; typing `/heal` in chat does the same.
- [ ] /heal again while it counts: if the server heals you again, the countdown restarts from the shorter time and the log shows `command cooldowns learned: {/heal=N}`; if it refuses with a time left, the countdown jumps to that time and the log shows the learned length (time since the last heal plus the time left). `config/cubewheel-cooldowns.json` has `"/heal": N` (seconds).
- [ ] A refused /heal whose message shows no countdown: send its capture line.
- [ ] /heal with no reply within 3 s (e.g. in the hub, or a typo like `/heall`): nothing is shown.
- [ ] Another player typing "You have been healed." or "wait 5m before /heal" in chat starts nothing.
- [ ] `"cooldowns": {"enabled": false}` + reload: no panel; `"cooldowns": {"commands": {}}` + reload: no /heal row (item and mcMMO cooldowns unchanged).
- [ ] Settings › HUD panels › Cooldowns has a "Command cooldowns" list with `/heal -> 10m`. The log has no "[cubewheel] command cooldown hook failed".

## SVA catalog

Bind "Open SVA catalog" first. Start with `config/cubewheel-cache/` deleted.

- [ ] In singleplayer, press the key: the screen opens and says "Offline: join ManaCube to download the SVA catalog"; no request is made (log shows no SVA fetch).
- [ ] Join ManaCube: within a few seconds `config/cubewheel-cache/svas-survival.json` (~700 KB) and `owned-<uuid>.json` exist. The game never stutters while it downloads.
- [ ] Open the screen: a grid of ~1,200 items, header "SVA Catalog · N of N · you own K"; with ManaCube's resource pack loaded the icons are the custom models, otherwise vanilla items. Your SVAs have a green border.
- [ ] Type `valhalla`: only matching items; type `souls monsters` (lore words): still finds the Valhalla Helmet. Clearing the search shows everything again from the top.
- [ ] Hover an item: its coloured name and lore (hex colours such as the gold "➟ 2x Souls from Monsters"), "Circulation: N", owned/not owned, "Click: /ah search NAME".
- [ ] "Only owned" / "Not owned" / "All" and "Sort: A–Z / Rarest / Most common" behave; the counts in the header change.
- [ ] Type a friend's name in "Compare with player…", press Enter: "Comparing with Name (owns N)", borders green/pink/aqua with the legend top right. A made-up name says "No such player"; `bad name!` says "Not a Minecraft name". × clears it.
- [ ] Left-click an item: the screen closes and exactly one `/ah search NAME` is sent (the AH opens with results). Symbols like ☀ are not in the search.
- [ ] Press ↻ twice quickly: only one owned request (log/cache time), the second within 15 s does nothing.
- [ ] Resize the window / change GUI scale with the screen open: layout adapts, search text and compare name are kept.
- [ ] Scroll with the wheel and PgUp/PgDn; the scrollbar on the right tracks the position.
- [ ] Rejoin within 6 hours: the catalog is not downloaded again (file time unchanged); owned is re-fetched only if older than 10 minutes.
- [ ] Hover an SVA in your inventory or `/pv 1`: the tooltip ends with `✦ SVA · Circulation: N · owned`. A normal diamond sword gets no line.
- [ ] `"svas": {"tooltip": false}` + reload: no tooltip line; `"svas": {"enabled": false}`: the key does nothing but says so, nothing is fetched.
- [ ] Disconnect the network and open the screen: cached data still shows, the status line says "Offline: …" or "API error …" instead of hanging.
- [ ] On a non-ManaCube server: no tooltip line and no requests.

## Live slices

- [ ] Fly: `/fly` on and off: the slice shows `Fly: on` (green) / `Fly: off` (red).
- [ ] Daily reward, fresh install: `Daily reward: ready` (green). Claim a reward in `/cow`: chat shows `[/CASHCOW] <you> claimed daily …`; the log says `/cow claim: daily …` and `config/cubewheel-cow.json` holds the time under your lower-cased name.
- [ ] After claiming all three tiers (or once the /cow menu showed their cooldowns): the slice reads `Daily: 13h` (soonest tier), not green.
- [ ] Capture on, open `/cow`: send the `"kind":"container"` line. If an item's lore says e.g. `Available in 13h 2m`, the badge matches it within a minute; if the wording differs, note it.
- [ ] Another player's `[/CASHCOW] … claimed …` changes nothing.
- [ ] Boss event, no recent spawn: `No boss event` (grey); clicking it does nothing and sends nothing.
- [ ] When chat announces `A BOSS SPAWNED … Boss Mana Golem / Location: Wolfhaven Mines`: the slice shows `Mana Golem · <1m` (gold), counting up. Nothing is sent by itself. Click it: the wheel closes and `/warp wolfhaven` is sent once. Also check a mini boss (Cursed Witch at Morend → `/warp morend`) and the Boss Arena (`/warp boss`).
- [ ] 15 minutes after the announcement the slice is back to `No boss event`.
- [ ] Off Survival (hub, other gamemodes): boss and `/cow` messages there change nothing.
