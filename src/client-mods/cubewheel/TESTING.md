# CubeWheel manual test checklist

Run the client (`./gradlew runClient`) and connect to ManaCube (`play.manacube.com`) unless a step says otherwise.
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
- [ ] Reply not recognised (e.g. a server whose `/homes` format the parser rejects): after ~3 s the "Loading…" entry turns into "↻ Refresh" without moving the mouse; clicking it re-sends `/homes` at most once per 30 s (clicking again sooner shows Refresh again, no new "fetching /homes" log line).
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
- [ ] Unpinned entries at ≥80% (and not complete) show on the HUD without a pin, in yellow; complete pinned entries show green; at most 6 lines.
- [ ] With a potion effect active, the HUD sits below the effect icons.
- [ ] The toggle-HUD key hides/shows the HUD ("Tracker HUD OFF/ON" on the action bar) and the setting survives a restart (`tracker.hudVisible` in `config/cubewheel.json`).
- [ ] F1 hides the HUD together with the vanilla HUD.
- [ ] On a non-ManaCube server the HUD is not drawn and opening menus records nothing; the picker still opens (local data).
- [ ] "Forget entries older than 7 days" removes unpinned entries not seen for a week (edit `seenAt` in `config/cubewheel-tracker.json` to test); pinned ones stay.
- [ ] Capture key: action bar "Capture ON → config/cubewheel-captures". Run `/homes` (also via the wheel, whose reply is hidden), then open `/jobs`, `/pquests`, `/prestige`, `/challenges`: `config/cubewheel-captures/<UTC date>.jsonl` gets one `"kind":"chat"` line per chat message and one `"kind":"container"` line per menu (another only if the contents change while it is open).
- [ ] Capture key again: "Capture OFF"; no further lines are written. Capture is off again after a restart.
- [ ] Send the capture file back so the progress and homes parsers can be tuned.
