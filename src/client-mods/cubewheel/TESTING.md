# CubeWheel manual test checklist

Run the client (`./gradlew runClient`) and connect to ManaCube (`play.manacube.com`) unless a step says otherwise.
Bind "Reload CubeWheel config" to a free key under Options > Controls > Key Binds > CubeWheel first.

## Wheel

- [ ] Hold `G` on ManaCube: the wheel opens around the screen centre, the game does not pause, the world stays visible behind a dim overlay.
- [ ] Move the mouse towards a slice: it highlights (white label, light box behind the icon); others stay grey.
- [ ] Flick towards Shops and release `G`: the wheel drills into the Shops ring and stays open ("◀ back" shows in the centre).
- [ ] In the Shops ring, left-click Sell: the wheel closes and `/sell` is sent.
- [ ] Hold `G`, flick onto Travel, click Spawn: `/spawn` is sent exactly once.
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
