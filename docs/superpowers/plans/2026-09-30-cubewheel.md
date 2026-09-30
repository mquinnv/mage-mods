# CubeWheel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `cubewheel`, a client-side Fabric mod for ManaCube Survival: a radial command wheel (vaults, shops, warps, isles, dynamic homes), a passive jobs/quest progress tracker with HUD + pin picker, and a capture mode for tuning parsers.

**Architecture:** Pure-Java logic classes (config, radial math, wheel resolution, homes parsing/caching/fetch policy, progress extraction, tracker store) are Minecraft-free and unit-tested with JUnit 5. Thin Minecraft adapters (screens, HUD, event hooks, command sending) wire them to the 26.2 client via Fabric API. Every command sent is the direct result of a user keypress/click (ManaCube macro rule).

**Tech Stack:** Java 25 (release), Fabric Loom 1.17-SNAPSHOT, Minecraft 26.2 (Mojang mappings), Fabric Loader 0.19.3, Fabric API 0.154.2+26.2, Gson (bundled with Minecraft), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-30-cubewheel-design.md` — read it before any task.

## Global Constraints

- Project root: `src/client-mods/cubewheel/` (sibling of `src/client-mods/hammerharvest/`, whose build files are the template).
- `minecraft_version=26.2`, `loader_version=0.19.3`, `loom_version=1.17-SNAPSHOT`, `fabric_api_version=0.154.2+26.2`; `org.gradle.java.home=/opt/homebrew/opt/openjdk@26/libexec/openjdk.jdk/Contents/Home`; javac `release = 25`.
- Mod id `cubewheel`; name `CubeWheel (unofficial ManaCube helper)`; package `com.mage.cubewheel`; `"environment": "client"`; license MIT; only dependency Fabric API.
- Config files live in the Fabric config dir: `cubewheel.json`, `cubewheel-homes.json`, `cubewheel-tracker.json`, `cubewheel-captures/<yyyy-MM-dd>.jsonl`.
- Default gated hosts: `manacube.com`, `manacube.net` (suffix match, case-insensitive, port ignored).
- The mod never sends a command on a timer. Only sends: a wheel/list selection by the user, and one `/homes` when the user opens the Homes ring (≤ once / 30 s).
- Pure classes (`config`, `wheel.RadialMath`, `wheel.WheelResolver`, `homes.HomesParser`, `homes.HomesCache`, `homes.HomesFetchPolicy`, `tracker.ProgressExtractor`, `tracker.Trackable`, `tracker.TrackerStore`, `tracker.TrackerSources`, `tracker.TrackerFormat`) must not import `net.minecraft.*` or `net.fabricmc.*` (Gson is fine).
- Build/test command (run from `src/client-mods/cubewheel`): `./gradlew build` — must be green at the end of every task.
- Commits end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_011CTc8nCBauC7zd8MQwYSWc
  ```

## Minecraft 26.2 API notes (verified with javap 2026-09-30)

Rendering and input APIs changed a lot in 26.x. **Do not code from memory of 1.20/1.21 — verify every
signature** with `javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar -public <class>`
and Fabric API jars under `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/` (or run
`./gradlew genSources` once and read the sources jar). Known facts:

- `GuiGraphics` is now **`net.minecraft.client.gui.GuiGraphicsExtractor`**. Methods: `fill(int x1,int y1,int x2,int y2,int argb)`, `text(Font, String|Component, int x, int y, int argb[, boolean shadow])`, `centeredText(Font, String|Component, int cx, int y, int argb)`, `item(ItemStack, int x, int y)`, `pose()` → `org.joml.Matrix3x2fStack`.
- `Screen.render` is now **`extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float partial)`**; background: `extractBackground(...)`. `keyPressed(net.minecraft.client.input.KeyEvent)`; mouse handlers take `net.minecraft.client.input.MouseButtonEvent` (record: `x()`, `y()`, `button()`), check the exact `mouseClicked`/`mouseReleased` signatures (they may carry an extra `boolean doubleClick`). Key releases: find `keyReleased(KeyEvent)`. `KeyEvent.input()` is the GLFW key code.
- `KeyMapping(String name, InputConstants.Type type, int code, KeyMapping.Category category)`; categories: `KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cubewheel","main"))` (verify the Identifier factory name). Register with Fabric's `fabric-key-mapping-api-v1` helper (class under `net.fabricmc.fabric.api.client.keymapping.v1` — verify name, probably `KeyMappingHelper.registerKeyMapping`).
- Chat hooks: `net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME` (return false to hide a game message; signature `(Component message, boolean overlay)`), `.GAME`; `ClientSendMessageEvents.COMMAND` (`String command` without the slash).
- Sending a command: `Minecraft.getInstance().getConnection().sendCommand(String withoutSlash)` (verify on `ClientPacketListener`).
- HUD: look for `HudElementRegistry` under `net.fabricmc.fabric.api.client.rendering.v1.hud` in `fabric-rendering-v1`.
- Server address: `Minecraft.getInstance().getCurrentServer()` → `ServerData.ip` (verify field/accessor).
- Screens: `net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT` for container screens; containers are `AbstractContainerScreen<?>`; slots via `screen.getMenu().slots`; item name `stack.getHoverName().getString()`; lore via `stack.get(DataComponents.LORE)` → `ItemLore.lines()` (verify).
- Config dir: `net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()`.

## Review Focus

1. **Wheel key released before the mouse moves** (quick tap) — expected: a tap with the cursor in the dead-zone closes the wheel without sending anything. Pinned by `RadialMathTest.deadZoneReturnsMinusOne` and the screen's release handling (Task 6 manual checklist).
2. **Home names with odd characters / servers that append hover text** (`/home farm_2`, `Homes (3): a, b, c`, trailing period) — expected: parsed correctly or rejected entirely, never a garbage home. Pinned in `HomesParserTest`.
3. **Unrelated chat arriving during the 3 s `/homes` window** (a player message, a broadcast) — expected: not suppressed, capture window stays armed. Pinned in `HomesFetchPolicyTest.unrelatedMessageNotSuppressed`.
4. **Hand-edited config with mistakes** (trailing comma, node with both `command` and `children`, `vaultCount: 500`) — expected: previous good config kept with a visible error; bad nodes dropped; numbers clamped. Pinned in `ConfigStoreTest`.
5. **Progress text shaped like `1.5k/3k`, `12,500 / 20,000`, `Progress: 45%`, or dates like `12/25`** — expected: numbers parsed with suffixes and commas; ratio wins over percent. Pinned in `ProgressExtractorTest`.

---

### Task 1: Project scaffold

**Files:**
- Create: `src/client-mods/cubewheel/{build.gradle,settings.gradle,gradle.properties,gradlew,gradlew.bat,gradle/wrapper/*}` (copy wrapper + gradle dir from `src/client-mods/hammerharvest/`)
- Create: `src/client-mods/cubewheel/src/main/resources/fabric.mod.json`
- Create: `src/client-mods/cubewheel/src/main/java/com/mage/cubewheel/CubeWheelClient.java`
- Create: `src/client-mods/cubewheel/LICENSE` (MIT, "Mage Realism")
- Test: `src/client-mods/cubewheel/src/test/java/com/mage/cubewheel/SmokeTest.java`

**Interfaces:**
- Produces: `com.mage.cubewheel.CubeWheelClient implements net.fabricmc.api.ClientModInitializer` with `public static final String MOD_ID = "cubewheel"` and `public static final org.slf4j.Logger LOG`.

- [ ] **Step 1: Copy build files.** `cp -R src/client-mods/hammerharvest/{gradlew,gradlew.bat,gradle,settings.gradle,gradle.properties,build.gradle} src/client-mods/cubewheel/`. Edit `settings.gradle`: `rootProject.name = 'cubewheel'`. Edit `gradle.properties`: `mod_version=0.1.0`, `maven_group=com.mage.cubewheel`, `archives_base_name=cubewheel`.
- [ ] **Step 2: Add JUnit to `build.gradle`.** Add `repositories { mavenCentral() }` and in `dependencies`:
  ```groovy
  testImplementation platform("org.junit:junit-bom:5.11.4")
  testImplementation "org.junit.jupiter:junit-jupiter"
  testRuntimeOnly "org.junit.platform:junit-platform-launcher"
  ```
  and `test { useJUnitPlatform() }`.
- [ ] **Step 3: `fabric.mod.json`:**
  ```json
  {
    "schemaVersion": 1,
    "id": "cubewheel",
    "version": "${version}",
    "name": "CubeWheel (unofficial ManaCube helper)",
    "description": "Unofficial client-side helper for ManaCube Survival: a radial command wheel for vaults, shops, warps and homes, plus a passive quest/job progress tracker. Not affiliated with ManaCube.",
    "authors": ["Mage Realism"],
    "license": "MIT",
    "environment": "client",
    "entrypoints": { "client": ["com.mage.cubewheel.CubeWheelClient"] },
    "depends": {
      "fabricloader": ">=${loader_version}",
      "fabric-api": "*",
      "minecraft": "~${minecraft_version}",
      "java": ">=25"
    }
  }
  ```
- [ ] **Step 4: Entry point:**
  ```java
  package com.mage.cubewheel;

  import net.fabricmc.api.ClientModInitializer;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;

  public final class CubeWheelClient implements ClientModInitializer {
  	public static final String MOD_ID = "cubewheel";
  	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

  	@Override
  	public void onInitializeClient() {
  		LOG.info("[cubewheel] initialised");
  	}
  }
  ```
- [ ] **Step 5: Smoke test** `SmokeTest`: `assertEquals("cubewheel", CubeWheelClient.MOD_ID);`
- [ ] **Step 6:** `cd src/client-mods/cubewheel && ./gradlew build` → BUILD SUCCESSFUL, `build/libs/cubewheel-0.1.0.jar` exists, test ran (check `build/test-results`).
- [ ] **Step 7: Commit** `feat(cubewheel): scaffold client mod project`.

---

### Task 2: Config model, defaults and store

**Files:**
- Create: `src/main/java/com/mage/cubewheel/config/WheelNode.java`, `CubeWheelConfig.java`, `DefaultConfig.java`, `ConfigStore.java`
- Test: `src/test/java/com/mage/cubewheel/config/ConfigStoreTest.java`

**Interfaces:**
- Produces:
  - `WheelNode` (Gson POJO): public fields `String label; String icon; String command; List<WheelNode> children; String dynamic;` plus `boolean isLeaf()` (command != null), `boolean isRing()` (children != null && dynamic == null && command == null), `boolean isDynamic()` (dynamic != null), static factories `leaf(label, icon, command)`, `ring(label, icon, WheelNode... children)`, `dynamic(label, icon, source, WheelNode... extras)`.
  - `CubeWheelConfig`: public fields `boolean enabled = true; List<String> serverHosts; int vaultCount = 3; int listThreshold = 8; Tracker tracker = new Tracker(); List<WheelNode> wheel;` nested `public static final class Tracker { double nearThreshold = 0.8; int hudMaxLines = 6; boolean hudVisible = true; Map<String,String> sources; }`.
  - `DefaultConfig.create()` → `CubeWheelConfig` with the spec's default layout.
  - `ConfigStore(Path file)`; `CubeWheelConfig current()`; `String reload()` → `null` on success, error message otherwise (previous config kept); `void save()`.

- [ ] **Step 1: Write failing tests** `ConfigStoreTest` (use `@TempDir Path dir`):
  ```java
  @Test void missingFileWritesDefaults() throws Exception {
  	Path f = dir.resolve("cubewheel.json");
  	ConfigStore s = new ConfigStore(f);
  	assertNull(s.reload());
  	assertTrue(Files.exists(f));
  	assertEquals(3, s.current().vaultCount);
  	assertEquals(8, s.current().wheel.size());
  }
  @Test void roundTripKeepsEdits() throws Exception {
  	Path f = dir.resolve("cubewheel.json");
  	ConfigStore s = new ConfigStore(f); s.reload();
  	s.current().vaultCount = 5; s.save();
  	ConfigStore t = new ConfigStore(f);
  	assertNull(t.reload());
  	assertEquals(5, t.current().vaultCount);
  }
  @Test void badJsonKeepsPreviousAndReportsError() throws Exception {
  	Path f = dir.resolve("cubewheel.json");
  	ConfigStore s = new ConfigStore(f); s.reload();
  	s.current().vaultCount = 4; s.save(); s.reload();
  	Files.writeString(f, "{ \"vaultCount\": 2, }}}");
  	String err = s.reload();
  	assertNotNull(err);
  	assertEquals(4, s.current().vaultCount);
  }
  @Test void clampsNumbersAndDropsInvalidNodes() throws Exception {
  	Path f = dir.resolve("cubewheel.json");
  	Files.writeString(f, """
  	  {"vaultCount": 500, "listThreshold": 1, "wheel": [
  	    {"label":"ok","command":"/spawn"},
  	    {"label":"both","command":"/x","children":[]},
  	    {"label":"none"},
  	    {"label":"ring","children":[{"label":"bad"},{"label":"sell","command":"sell"}]}
  	  ]}""");
  	ConfigStore s = new ConfigStore(f);
  	assertNull(s.reload());
  	CubeWheelConfig c = s.current();
  	assertEquals(54, c.vaultCount);
  	assertEquals(3, c.listThreshold);
  	assertEquals(List.of("ok", "ring"), c.wheel.stream().map(n -> n.label).toList());
  	assertEquals(1, c.wheel.get(1).children.size());
  	assertEquals("/sell", c.wheel.get(1).children.get(0).command); // leading slash added
  	assertNotNull(c.serverHosts); assertFalse(c.serverHosts.isEmpty());
  	assertNotNull(c.tracker.sources);
  }
  @Test void defaultsContainRequestedEntries() {
  	CubeWheelConfig c = DefaultConfig.create();
  	List<String> all = new ArrayList<>();
  	Deque<WheelNode> q = new ArrayDeque<>(c.wheel);
  	while (!q.isEmpty()) { WheelNode n = q.pop(); if (n.command != null) all.add(n.command); if (n.children != null) q.addAll(n.children); }
  	for (String cmd : List.of("/sell", "/kilton", "/alchemist", "/enchanter", "/warp crops", "/warp spawners", "/warp wolfhaven", "/warp tangleroots", "/warp morend", "/warp boss", "/rtp", "/jobs", "/pquests", "/prestige"))
  		assertTrue(all.contains(cmd), cmd);
  	assertTrue(c.wheel.stream().anyMatch(n -> "homes".equals(n.dynamic)));
  	assertTrue(c.wheel.stream().anyMatch(n -> "vaults".equals(n.dynamic)));
  }
  ```
- [ ] **Step 2:** `./gradlew test` → FAIL (classes missing).
- [ ] **Step 3: Implement.**
  - `WheelNode` as described. Factories set `children` to a `new ArrayList<>(List.of(...))` (ring always non-null list; dynamic extras list, possibly empty).
  - `DefaultConfig.create()` — serverHosts `["manacube.com","manacube.net"]`; tracker sources `{"jobs":"(?i)jobs","pquests":"(?i)quest","prestige":"(?i)prestige","challenges":"(?i)challenge"}` (LinkedHashMap); wheel (label / icon item id / command):
    - ring `Travel` `minecraft:compass`: Spawn `minecraft:red_bed` `/spawn`; Random TP `minecraft:grass_block` `/rtp`; Teleporter `minecraft:ender_pearl` `/teleporter`; Warps menu `minecraft:oak_sign` `/warp`; Party home `minecraft:white_banner` `/p home`; Back `minecraft:arrow` `/back`
    - dynamic `Homes` `minecraft:red_bed` source `homes`
    - dynamic `Vaults` `minecraft:ender_chest` source `vaults`, extras: Ender chest `minecraft:ender_chest` `/ec`; Party vault `minecraft:barrel` `/p vault`
    - ring `Shops` `minecraft:emerald`: Sell `minecraft:gold_ingot` `/sell`; Kilton `minecraft:skeleton_skull` `/kilton`; Alchemist `minecraft:brewing_stand` `/alchemist`; Enchanter `minecraft:enchanting_table` `/enchanter`; Shop `minecraft:emerald` `/shop`; Auction house `minecraft:gold_block` `/ah`; Forge `minecraft:anvil` `/forge`; Fish shop `minecraft:cod` `/fish`
    - ring `Warps` `minecraft:oak_sign`: Crops `minecraft:wheat` `/warp crops`; Spawners `minecraft:spawner` `/warp spawners`
    - ring `Isles & Bosses` `minecraft:filled_map`: Isles menu `minecraft:map` `/isles`; Wolfhaven `minecraft:bone` `/warp wolfhaven`; Tangleroots `minecraft:vine` `/warp tangleroots`; Sandara `minecraft:sand` `/warp sandara`; Icehaven `minecraft:packed_ice` `/warp icehaven`; Morend `minecraft:end_stone` `/warp morend`; Burninglands `minecraft:magma_block` `/warp burninglands`; Boss arena `minecraft:wither_skeleton_skull` `/warp boss`; Bosses `minecraft:nether_star` `/bosses`
    - ring `Progress` `minecraft:experience_bottle`: Jobs `minecraft:iron_pickaxe` `/jobs`; Party quests `minecraft:writable_book` `/pquests`; Prestige `minecraft:nether_star` `/prestige`; Challenges `minecraft:target` `/challenges`; Daily reward `minecraft:milk_bucket` `/cow`
    - ring `Party` `minecraft:white_banner`: Party menu `minecraft:white_banner` `/p`; Party warps `minecraft:lodestone` `/p warps`; Claim `minecraft:golden_shovel` `/p claim`; Map `minecraft:map` `/p map`
  - `ConfigStore`: Gson `new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()`. `reload()`: if file missing → `current = DefaultConfig.create(); save(); return null`. Else parse in try/catch (`JsonParseException | IOException | IllegalStateException`) → on failure return `"cubewheel.json: " + e.getMessage()` leaving `current` unchanged (if `current` is null, set defaults). Then `normalize(cfg)`: null `serverHosts` → defaults' hosts; null `tracker` → new; null `tracker.sources` → defaults' sources; null `wheel` → defaults' wheel; clamp `vaultCount` to [0,54], `listThreshold` to [3,16], `nearThreshold` to [0,1], `hudMaxLines` to [1,20]; `normalizeNodes(list)` recursively keeps a node iff `label` non-blank and exactly one of (command non-blank / children non-null / dynamic in {"homes","vaults"}) — except dynamic may also have children (extras); commands get a leading `/` if missing and are trimmed. Parse with a strict reader: `JsonReader r = new JsonReader(reader); r.setStrictness(Strictness.STRICT)` (Gson ≥2.11; if unavailable use `r.setLenient(false)`) and after reading ensure `r.peek() == JsonToken.END_DOCUMENT` else error "trailing content". `save()` writes pretty JSON (create parent dirs).
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** `feat(cubewheel): JSON config with defaults, validation and reload`.

---

### Task 3: Radial math and wheel resolution

**Files:**
- Create: `src/main/java/com/mage/cubewheel/wheel/RadialMath.java`, `WheelResolver.java`
- Test: `src/test/java/com/mage/cubewheel/wheel/RadialMathTest.java`, `WheelResolverTest.java`

**Interfaces:**
- Consumes: `WheelNode` (Task 2).
- Produces:
  - `RadialMath.sliceAt(double dx, double dy, int count, double deadZone)` → `int` (−1 if count ≤ 0 or `hypot(dx,dy) < deadZone`). Screen coords (y down). Slice 0 centred straight up, indices increase clockwise.
  - `RadialMath.sliceCenterDegrees(int index, int count)` → `double` (0 = up, clockwise).
  - `RadialMath.offset(double degrees, double radius)` → `double[]{x, y}` (screen coords).
  - `WheelResolver.children(WheelNode node, int vaultCount, List<String> homes)` → `List<WheelNode>`.

- [ ] **Step 1: Failing tests.**
  ```java
  class RadialMathTest {
  	@Test void deadZoneReturnsMinusOne() { assertEquals(-1, RadialMath.sliceAt(2, 3, 8, 10)); }
  	@Test void emptyWheel() { assertEquals(-1, RadialMath.sliceAt(0, -50, 0, 10)); }
  	@Test void upIsZero() { assertEquals(0, RadialMath.sliceAt(0, -50, 8, 10)); }
  	@Test void rightIsQuarter() { assertEquals(2, RadialMath.sliceAt(50, 0, 8, 10)); }
  	@Test void downIsHalf() { assertEquals(4, RadialMath.sliceAt(0, 50, 8, 10)); }
  	@Test void leftIsThreeQuarters() { assertEquals(6, RadialMath.sliceAt(-50, 0, 8, 10)); }
  	@Test void slightlyLeftOfUpWrapsToZero() { assertEquals(0, RadialMath.sliceAt(-5, -50, 8, 10)); }
  	@Test void boundaryJustPastHalfSlice() { // 8 slices → 45° each, slice 1 starts at 22.5°
  		double r = Math.toRadians(23); assertEquals(1, RadialMath.sliceAt(Math.sin(r) * 50, -Math.cos(r) * 50, 8, 10)); }
  	@Test void singleSliceAlwaysZero() { assertEquals(0, RadialMath.sliceAt(0, 50, 1, 10)); }
  	@Test void centersAndOffset() {
  		assertEquals(90.0, RadialMath.sliceCenterDegrees(1, 4), 1e-9);
  		double[] o = RadialMath.offset(90, 10); assertEquals(10, o[0], 1e-9); assertEquals(0, o[1], 1e-9);
  		double[] up = RadialMath.offset(0, 10); assertEquals(0, up[0], 1e-9); assertEquals(-10, up[1], 1e-9);
  	}
  }
  class WheelResolverTest {
  	@Test void ringReturnsChildren() {
  		WheelNode r = WheelNode.ring("S", null, WheelNode.leaf("a", null, "/a"));
  		assertEquals(List.of("/a"), WheelResolver.children(r, 3, List.of()).stream().map(n -> n.command).toList());
  	}
  	@Test void vaultsExpandThenExtras() {
  		WheelNode v = WheelNode.dynamic("V", null, "vaults", WheelNode.leaf("EC", null, "/ec"));
  		assertEquals(List.of("/pv 1", "/pv 2", "/pv 3", "/ec"), WheelResolver.children(v, 3, List.of()).stream().map(n -> n.command).toList());
  		assertEquals("Vault 2", WheelResolver.children(v, 3, List.of()).get(1).label);
  	}
  	@Test void zeroVaultsOnlyExtras() {
  		WheelNode v = WheelNode.dynamic("V", null, "vaults");
  		assertTrue(WheelResolver.children(v, 0, List.of()).isEmpty());
  	}
  	@Test void homesSortedCaseInsensitive() {
  		WheelNode h = WheelNode.dynamic("H", "minecraft:red_bed", "homes");
  		List<WheelNode> out = WheelResolver.children(h, 3, List.of("farm", "Base", "nether"));
  		assertEquals(List.of("Base", "farm", "nether"), out.stream().map(n -> n.label).toList());
  		assertEquals("/home Base", out.get(0).command);
  		assertEquals("minecraft:red_bed", out.get(0).icon);
  	}
  	@Test void leafHasNoChildren() { assertTrue(WheelResolver.children(WheelNode.leaf("a", null, "/a"), 3, List.of()).isEmpty()); }
  }
  ```
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3: Implement.**
  ```java
  public final class RadialMath {
  	private RadialMath() {}
  	public static int sliceAt(double dx, double dy, int count, double deadZone) {
  		if (count <= 0 || Math.hypot(dx, dy) < deadZone) return -1;
  		double deg = Math.toDegrees(Math.atan2(dx, -dy));
  		if (deg < 0) deg += 360;
  		double w = 360.0 / count;
  		return (int) Math.floor((deg + w / 2) / w) % count;
  	}
  	public static double sliceCenterDegrees(int index, int count) { return index * 360.0 / count; }
  	public static double[] offset(double degrees, double radius) {
  		double r = Math.toRadians(degrees);
  		return new double[] { Math.sin(r) * radius, -Math.cos(r) * radius };
  	}
  }
  ```
  `WheelResolver.children`: leaf → `List.of()`; ring → copy of children; dynamic `vaults` → leaves `("Vault " + i, "minecraft:ender_chest", "/pv " + i)` for i in 1..vaultCount then extras; dynamic `homes` → homes sorted with `String.CASE_INSENSITIVE_ORDER` → leaves `(name, node.icon != null ? node.icon : "minecraft:red_bed", "/home " + name)` then extras; unknown dynamic → extras.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** `feat(cubewheel): radial slice math and dynamic ring resolution`.

---

### Task 4: Homes parsing, cache and fetch policy

**Files:**
- Create: `src/main/java/com/mage/cubewheel/homes/HomesParser.java`, `HomesCache.java`, `HomesFetchPolicy.java`
- Test: `src/test/java/com/mage/cubewheel/homes/HomesParserTest.java`, `HomesCacheTest.java`, `HomesFetchPolicyTest.java`

**Interfaces:**
- Produces:
  - `HomesParser.Reply(String text, List<String> clickCommands)` record (text = message plain string, may contain `\n`; clickCommands = values of every run/suggest-command click event in the component tree, as sent, with leading `/`).
  - `HomesParser.parse(Reply r)` → `Optional<List<String>>` (empty Optional = not a homes reply).
  - `HomesParser.Edit(boolean add, String name)` record; `HomesParser.parseOutgoing(String commandWithoutSlash)` → `Optional<Edit>`.
  - `HomesCache(Path file)`: `List<String> get(String host)` (never null), `long fetchedAt(String host)` (0 if never), `void put(String host, List<String> homes, long now)`, `void add(String host, String name)`, `void remove(String host, String name)`, `boolean isStale(String host, long now, long maxAgeMs)` (true if never fetched), `void load()`, `void save()`. Host keys lower-cased.
  - `HomesFetchPolicy`: `static final long MIN_INTERVAL_MS = 30_000, WINDOW_MS = 3_000, MAX_AGE_MS = 300_000`; `boolean shouldFetch(long now, boolean cacheStale)`; `void armed(long now)` (records send time); `boolean isArmed(long now)`; `Decision onMessage(long now, Optional<List<String>> parsed)` where `enum Decision { IGNORE, ACCEPT_AND_SUPPRESS, ACCEPT_PASSIVE }` — armed & parsed present → `ACCEPT_AND_SUPPRESS` and disarm; not armed & parsed present → `ACCEPT_PASSIVE`; parsed empty → `IGNORE` (stay armed).

- [ ] **Step 1: Failing tests.**
  ```java
  class HomesParserTest {
  	static HomesParser.Reply r(String t, String... clicks) { return new HomesParser.Reply(t, List.of(clicks)); }
  	@Test void clickEventsWin() {
  		assertEquals(Optional.of(List.of("base", "farm_2")), HomesParser.parse(r("Homes: base, farm_2", "/home base", "/home farm_2")));
  	}
  	@Test void clickEventsDedupAndIgnoreOthers() {
  		assertEquals(Optional.of(List.of("base")), HomesParser.parse(r("x", "/home base", "/home base", "/spawn", "/home")));
  	}
  	@Test void textFallback() {
  		assertEquals(Optional.of(List.of("base", "farm", "nether")), HomesParser.parse(r("Homes: base, farm, nether")));
  	}
  	@Test void textFallbackWithCountAndTrailingPeriod() {
  		assertEquals(Optional.of(List.of("a", "b-1", "c")), HomesParser.parse(r("Your homes (3): a, b-1, c.")));
  	}
  	@Test void multiLineUsesHomesLine() {
  		assertEquals(Optional.of(List.of("x")), HomesParser.parse(r("-----\nHomes: x\n-----")));
  	}
  	@Test void rejectsSentenceWithSpaces() { assertEquals(Optional.empty(), HomesParser.parse(r("Home: you have no homes set"))); }
  	@Test void rejectsUnrelated() { assertEquals(Optional.empty(), HomesParser.parse(r("<Steve> hello: world"))); }
  	@Test void outgoing() {
  		assertEquals(Optional.of(new HomesParser.Edit(true, "farm")), HomesParser.parseOutgoing("sethome farm"));
  		assertEquals(Optional.of(new HomesParser.Edit(true, "home")), HomesParser.parseOutgoing("sethome"));
  		assertEquals(Optional.of(new HomesParser.Edit(false, "farm")), HomesParser.parseOutgoing("delhome farm"));
  		assertEquals(Optional.empty(), HomesParser.parseOutgoing("home farm"));
  	}
  }
  class HomesCacheTest {
  	@TempDir Path dir;
  	@Test void putGetStaleAndPersist() {
  		HomesCache c = new HomesCache(dir.resolve("h.json"));
  		assertTrue(c.isStale("play.manacube.com", 1000, 300_000));
  		c.put("Play.ManaCube.com", List.of("a", "b"), 1000);
  		assertFalse(c.isStale("play.manacube.com", 2000, 300_000));
  		assertTrue(c.isStale("play.manacube.com", 1000 + 300_001, 300_000));
  		c.add("play.manacube.com", "c"); c.add("play.manacube.com", "a"); c.remove("play.manacube.com", "b");
  		assertEquals(List.of("a", "c"), c.get("play.manacube.com"));
  		c.save();
  		HomesCache d = new HomesCache(dir.resolve("h.json")); d.load();
  		assertEquals(List.of("a", "c"), d.get("play.manacube.com"));
  		assertEquals(1000, d.fetchedAt("play.manacube.com"));
  	}
  	@Test void loadMissingOrCorruptIsEmpty() throws Exception {
  		Path f = dir.resolve("h.json"); Files.writeString(f, "{nope");
  		HomesCache c = new HomesCache(f); c.load();
  		assertEquals(List.of(), c.get("x"));
  	}
  }
  class HomesFetchPolicyTest {
  	@Test void fetchOnlyWhenStaleAndNotRecent() {
  		HomesFetchPolicy p = new HomesFetchPolicy();
  		assertFalse(p.shouldFetch(0, false));
  		assertTrue(p.shouldFetch(0, true));
  		p.armed(0);
  		assertFalse(p.shouldFetch(10_000, true));
  		assertTrue(p.shouldFetch(30_001, true));
  	}
  	@Test void suppressesOnlyWhileArmed() {
  		HomesFetchPolicy p = new HomesFetchPolicy();
  		p.armed(0);
  		assertEquals(HomesFetchPolicy.Decision.ACCEPT_AND_SUPPRESS, p.onMessage(500, Optional.of(List.of("a"))));
  		assertEquals(HomesFetchPolicy.Decision.ACCEPT_PASSIVE, p.onMessage(600, Optional.of(List.of("a"))));
  	}
  	@Test void unrelatedMessageNotSuppressed() {
  		HomesFetchPolicy p = new HomesFetchPolicy();
  		p.armed(0);
  		assertEquals(HomesFetchPolicy.Decision.IGNORE, p.onMessage(100, Optional.empty()));
  		assertTrue(p.isArmed(200));
  		assertEquals(HomesFetchPolicy.Decision.ACCEPT_AND_SUPPRESS, p.onMessage(300, Optional.of(List.of("a"))));
  	}
  	@Test void windowExpires() {
  		HomesFetchPolicy p = new HomesFetchPolicy();
  		p.armed(0);
  		assertFalse(p.isArmed(3_001));
  		assertEquals(HomesFetchPolicy.Decision.ACCEPT_PASSIVE, p.onMessage(3_001, Optional.of(List.of("a"))));
  	}
  }
  ```
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3: Implement.**
  - `HomesParser.parse`: `NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}")`. Click pass: for each click command matching `^/homes? +([A-Za-z0-9_\-]{1,32})$` collect group 1 into a `LinkedHashSet`; if non-empty return it as list. Text pass: for each line (`split("\\R")`) whose lower-case contains `"home"` and that has a `:` → `rest = line.substring(line.indexOf(':') + 1).trim()`, strip one trailing `.`; if blank skip; split on `","`, trim each; if every token matches NAME and there is ≥1 token → return distinct list. Otherwise `Optional.empty()`.
  - `parseOutgoing`: trim, split on whitespace; `sethome` → add (arg or `"home"`); `delhome`/`deletehome`/`rmhome` with arg → remove; else empty. Only accept names matching NAME.
  - `HomesCache`: `Map<String, Entry>` where `static final class Entry { List<String> homes = new ArrayList<>(); long fetchedAt; }`, Gson via `TypeToken<Map<String, Entry>>`. `load()` swallows IO/parse errors → empty map (log nothing; pure). `add` appends if absent; `remove` removes. `put` replaces list + fetchedAt.
  - `HomesFetchPolicy`: `long lastSent = Long.MIN_VALUE / 2; boolean armed;` `shouldFetch(now, stale) = stale && now - lastSent >= MIN_INTERVAL_MS`; `armed(now) { lastSent = now; armed = true; }`; `isArmed(now) = armed && now - lastSent <= WINDOW_MS`; `onMessage`: if parsed empty → IGNORE; if `isArmed(now)` → `armed = false`, ACCEPT_AND_SUPPRESS; else ACCEPT_PASSIVE.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** `feat(cubewheel): /homes reply parser, per-server cache, fetch policy`.

---

### Task 5: Progress extraction and tracker store

**Files:**
- Create: `src/main/java/com/mage/cubewheel/tracker/ProgressExtractor.java`, `Trackable.java`, `TrackerStore.java`, `TrackerSources.java`, `TrackerFormat.java`
- Test: `src/test/java/com/mage/cubewheel/tracker/ProgressExtractorTest.java`, `TrackerStoreTest.java`, `TrackerSourcesTest.java`, `TrackerFormatTest.java`

**Interfaces:**
- Produces:
  - `ProgressExtractor.Progress(double current, double max)` record; `ProgressExtractor.extract(List<String> lines)` → `Optional<Progress>`; `ProgressExtractor.parseNumber(String)` → `OptionalDouble` (commas, `k`=1e3, `m`=1e6, case-insensitive).
  - `Trackable(String id, String source, String name, double current, double max, long seenAt)` record with `double fraction()` (clamped [0,1], 0 if max ≤ 0) and `boolean complete()` (current ≥ max && max > 0); `static String idOf(String source, String name)` = `source + ":" + name`.
  - `TrackerStore(Path file)`: `void update(String source, String name, ProgressExtractor.Progress p, long now)`; `List<Trackable> all()` (sorted by fraction desc, then name); `boolean isPinned(String id)`; `void togglePin(String id)`; `List<Trackable> hudEntries(double nearThreshold, int maxLines)`; `int forgetOlderThan(long now, long ageMs)` (never removes pinned; returns count removed); `void load()`; `void save()`.
  - `TrackerSources.match(String title, Map<String,String> sources)` → `Optional<String>` (first source whose regex `find()`s in title; invalid regex skipped).
  - `TrackerFormat.line(Trackable t, long now)` → e.g. `"Carrot Grind  1,234 / 1,500 (82%) · 12m"`; `TrackerFormat.age(long ms)` → `"now"` (<60 s), `"12m"`, `"3h"`, `"2d"`.

- [ ] **Step 1: Failing tests.**
  ```java
  class ProgressExtractorTest {
  	static Optional<ProgressExtractor.Progress> x(String... l) { return ProgressExtractor.extract(List.of(l)); }
  	@Test void simpleRatio() { assertEquals(Optional.of(new ProgressExtractor.Progress(3, 10)), x("Progress: 3/10")); }
  	@Test void commasAndSpaces() { assertEquals(Optional.of(new ProgressExtractor.Progress(12500, 20000)), x("Harvested 12,500 / 20,000 carrots")); }
  	@Test void suffixes() { assertEquals(Optional.of(new ProgressExtractor.Progress(1500, 3000)), x("1.5k/3k")); }
  	@Test void wordAfterNumberIsNotSuffix() { assertEquals(Optional.of(new ProgressExtractor.Progress(3, 10)), x("Kill 3/10 mobs")); }
	@Test void ofForm() { assertEquals(Optional.of(new ProgressExtractor.Progress(7, 25)), x("7 of 25 jobs")); }
  	@Test void percentOnly() { assertEquals(Optional.of(new ProgressExtractor.Progress(45, 100)), x("Complete: 45%")); }
  	@Test void ratioWinsOverPercent() { assertEquals(Optional.of(new ProgressExtractor.Progress(1, 4)), x("25%", "1/4")); }
  	@Test void firstRatioLineWins() { assertEquals(Optional.of(new ProgressExtractor.Progress(2, 5)), x("Reward", "2/5", "9/9")); }
  	@Test void noneFound() { assertEquals(Optional.empty(), x("Click to claim", "Reward: $500")); }
  	@Test void zeroMaxIgnored() { assertEquals(Optional.empty(), x("0/0")); }
  	@Test void parseNumber() {
  		assertEquals(2_000_000, ProgressExtractor.parseNumber("2M").getAsDouble(), 1e-9);
  		assertTrue(ProgressExtractor.parseNumber("abc").isEmpty());
  	}
  }
  class TrackerStoreTest {
  	@TempDir Path dir;
  	static ProgressExtractor.Progress p(double c, double m) { return new ProgressExtractor.Progress(c, m); }
  	@Test void hudShowsPinnedThenNearIncomplete() {
  		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
  		s.update("jobs", "Low", p(1, 10), 0);
  		s.update("jobs", "Near", p(9, 10), 0);
  		s.update("jobs", "Done", p(10, 10), 0);
  		s.update("pquests", "Pinned", p(2, 10), 0);
  		s.togglePin(Trackable.idOf("pquests", "Pinned"));
  		assertEquals(List.of("Pinned", "Near"), s.hudEntries(0.8, 6).stream().map(Trackable::name).toList());
  		assertEquals(List.of("Pinned"), s.hudEntries(0.8, 1).stream().map(Trackable::name).toList());
  	}
  	@Test void updateReplacesAndPersists() {
  		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
  		s.update("jobs", "A", p(1, 10), 0);
  		s.update("jobs", "A", p(5, 10), 100);
  		s.togglePin("jobs:A");
  		s.save();
  		TrackerStore t = new TrackerStore(dir.resolve("t.json")); t.load();
  		assertEquals(1, t.all().size());
  		assertEquals(5, t.all().get(0).current(), 1e-9);
  		assertTrue(t.isPinned("jobs:A"));
  	}
  	@Test void forgetKeepsPinned() {
  		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
  		s.update("jobs", "Old", p(1, 10), 0);
  		s.update("jobs", "OldPinned", p(1, 10), 0);
  		s.togglePin("jobs:OldPinned");
  		s.update("jobs", "New", p(1, 10), 1_000_000);
  		assertEquals(1, s.forgetOlderThan(1_000_000, 500_000));
  		assertEquals(Set.of("OldPinned", "New"), s.all().stream().map(Trackable::name).collect(Collectors.toSet()));
  	}
  	@Test void loadCorruptIsEmpty() throws Exception {
  		Files.writeString(dir.resolve("t.json"), "][");
  		TrackerStore s = new TrackerStore(dir.resolve("t.json")); s.load();
  		assertTrue(s.all().isEmpty());
  	}
  }
  class TrackerSourcesTest {
  	@Test void matchesFirst() {
  		Map<String, String> m = new LinkedHashMap<>(); m.put("jobs", "(?i)jobs"); m.put("pquests", "(?i)quest"); m.put("bad", "(");
  		assertEquals(Optional.of("jobs"), TrackerSources.match("Your Jobs", m));
  		assertEquals(Optional.of("pquests"), TrackerSources.match("Party Quests (1/2)", m));
  		assertEquals(Optional.empty(), TrackerSources.match("Chest", m));
  	}
  }
  class TrackerFormatTest {
  	@Test void line() {
  		Trackable t = new Trackable("jobs:Carrot Grind", "jobs", "Carrot Grind", 1234, 1500, 0);
  		assertEquals("Carrot Grind  1,234 / 1,500 (82%) · 12m", TrackerFormat.line(t, 12 * 60_000));
  	}
  	@Test void ages() {
  		assertEquals("now", TrackerFormat.age(59_000));
  		assertEquals("3h", TrackerFormat.age(3 * 3_600_000L + 5));
  		assertEquals("2d", TrackerFormat.age(2 * 86_400_000L));
  	}
  }
  ```
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3: Implement.**
  - `ProgressExtractor`: `NUM = "(\\d[\\d,]*(?:\\.\\d+)?[kKmM]?)(?![A-Za-z])"` (suffix must touch the number and not start a word, so "10 mobs" is 10, not 10M); `RATIO = Pattern.compile(NUM + "\\s*(?:/|\\bof\\b)\\s*" + NUM)`; `PCT = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%")`. Pass 1: for each line, `RATIO.matcher(line)`; for each `find()` parse both; accept first with max > 0. Pass 2: first `PCT` match → `(pct, 100)`. `parseNumber`: strip spaces and commas, trailing k/m multiplies; `NumberFormatException` → empty.
  - `TrackerStore`: `LinkedHashMap<String, Trackable> items; LinkedHashSet<String> pins;` persisted as `{"items":[…],"pins":[…]}` via a small `Snapshot` class for Gson (records are fine with Gson ≥ 2.10). `hudEntries`: pinned items (present in map) sorted by fraction desc, then non-pinned with `fraction() >= near && !complete()` sorted by fraction desc, concatenated, limited to `maxLines`. Corrupt/missing file → empty.
  - `TrackerFormat.line`: `name + "  " + fmt(current) + " / " + fmt(max) + " (" + Math.round(fraction*100) + "%) · " + age(now - seenAt)` where `fmt` uses `String.format(Locale.ROOT, "%,d", Math.round(v))` for whole numbers, otherwise `"%,.1f"`. `Math.round(0.8226*100)` = 82.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** `feat(cubewheel): progress extraction and tracker store`.

---

### Task 6: Minecraft wiring — gate, command sender, keybinds, radial + list screens, config reload

**Files:**
- Create: `src/main/java/com/mage/cubewheel/ServerGate.java`, `CommandSender.java`, `Keybinds.java`, `wheel/RadialScreen.java`, `wheel/ListScreen.java`, `wheel/Icons.java`
- Modify: `CubeWheelClient.java`
- Test: `src/test/java/com/mage/cubewheel/ServerGateTest.java`
- Create: `src/client-mods/cubewheel/TESTING.md` (manual checklist — start it here, extend in Tasks 7–8)

**Interfaces:**
- Consumes: `ConfigStore`, `CubeWheelConfig`, `WheelNode`, `RadialMath`, `WheelResolver`.
- Produces:
  - `ServerGate.matches(String address, List<String> hosts)` → `boolean` (pure static: strip `:port`, lower-case, true if equals a host or ends with `"." + host`); `ServerGate.currentHost()` → `Optional<String>` (lower-cased host of `Minecraft.getInstance().getCurrentServer()`, empty in singleplayer/menus); `ServerGate.active(CubeWheelConfig)` → `boolean` (enabled && currentHost matches).
  - `CommandSender.send(String commandWithSlash)` → `boolean` — refuses (returns false, logs) when not `ServerGate.active`, strips leading `/`, calls `getConnection().sendCommand(...)`.
  - `CubeWheelClient` static accessors: `config()` → `ConfigStore`, `homes()` → `HomesCache` (Task 7 fills), `tracker()` → `TrackerStore` (Task 8 fills). Add now as fields initialised in `onInitializeClient` for config only; later tasks add theirs.
  - `RadialScreen(WheelNode root, KeyMapping holdKey)` and `ListScreen(Screen parent, String title, List<WheelNode> entries)`; a static hook `RadialScreen.childrenProvider` (`Function<WheelNode, List<WheelNode>>`) used to resolve children so Task 7 can inject homes fetching; default uses `WheelResolver.children(node, cfg.vaultCount, CubeWheelClient.homes()==null? List.of() : homes.get(host))`.
  - `Icons.stack(String itemId)` → `ItemStack` (unknown/null → `ItemStack.EMPTY`; uses `BuiltInRegistries.ITEM` + `Identifier.tryParse` — verify names).

- [ ] **Step 1: Failing test** `ServerGateTest`:
  ```java
  @Test void matching() {
  	List<String> h = List.of("manacube.com", "manacube.net");
  	assertTrue(ServerGate.matches("play.manacube.com", h));
  	assertTrue(ServerGate.matches("PLAY.MANACUBE.COM:25565", h));
  	assertTrue(ServerGate.matches("manacube.net", h));
  	assertFalse(ServerGate.matches("notmanacube.com", h));
  	assertFalse(ServerGate.matches("mage.example.org", h));
  	assertFalse(ServerGate.matches(null, h));
  }
  ```
  (Keep `matches` free of Minecraft types so this test runs without a game.)
- [ ] **Step 2:** `./gradlew test` → FAIL; implement `ServerGate.matches`; → PASS.
- [ ] **Step 3: Keybinds.** In `Keybinds`, register under category `cubewheel:main` (translation key `key.category.cubewheel.main`): `key.cubewheel.wheel` (GLFW `G`), `key.cubewheel.reload`, `key.cubewheel.tracker_hud`, `key.cubewheel.tracker_picker`, `key.cubewheel.capture` (unbound = `InputConstants.UNKNOWN.getValue()` / -1; verify). Add `src/main/resources/assets/cubewheel/lang/en_us.json` with human names ("Open command wheel (hold)", "Reload CubeWheel config", "Toggle tracker HUD", "Open tracker picker", "Toggle capture mode", category "CubeWheel").
- [ ] **Step 4: Client tick** (`ClientTickEvents.END_CLIENT_TICK`): if `wheel.consumeClick()` and no screen open → if `!ServerGate.active(cfg)` show action bar `"CubeWheel is only active on ManaCube"` (`player.displayClientMessage(Component, true)` — verify) else `mc.setScreen(new RadialScreen(rootNode, wheelKey))` where `rootNode = WheelNode.ring("CubeWheel", null, cfg.wheel.toArray(WheelNode[]::new))`. Drain extra clicks. `reload.consumeClick()` → `config().reload()`; error → red chat line `"[CubeWheel] " + err`, success → green `"[CubeWheel] config reloaded"` (`player.displayClientMessage(Component, false)`). Wrap handler bodies in try/catch → `LOG.error`.
- [ ] **Step 5: RadialScreen.** Behaviour per spec §Wheel behaviour:
  - Fields: `Deque<WheelNode> path` (root first), `List<WheelNode> entries` (current children), `int hovered = -1`, `boolean keyStillHeld = true`, `KeyMapping holdKey`.
  - `isPauseScreen()` → false. Background: dim with `fill(0,0,width,height,0x66000000)` in `extractBackground` (or skip vanilla blur — check what `extractBackground` does by default; override to only dim).
  - Geometry: centre `(width/2, height/2)`; ring radius `R = min(width,height) * 0.30` (clamp 60..140); dead-zone `R * 0.25`; each slice drawn as an 18px item icon at `offset(sliceCenterDegrees(i,n), R)` plus `centeredText` label 12px below; hovered slice gets a `fill` highlight box behind icon (0x80FFFFFF) and white label, others grey (0xFFAAAAAA). Centre shows the current ring label and, if depth > 1, "◀ back". Draw thin separators is optional — skip (YAGNI).
  - On every render: `hovered = RadialMath.sliceAt(mouseX - cx, mouseY - cy, entries.size(), dead)`.
  - Key release: Screens don't get key-up for bindings reliably; in `tick()` check `holdKey` physical state via `InputConstants.isKeyDown(window, key.getValue())` (for mouse-button bindings use GLFW `glfwGetMouseButton` — verify helpers; resolve bound key with `KeyMappingHelper.getBoundKeyOf` or equivalent). When it transitions held→released: if `hovered >= 0` → `activate(entries.get(hovered))`; else `onClose()`. After drilling into a sub-ring by release, `keyStillHeld=false` so subsequent actions are click-driven.
  - `mouseClicked`: left → if hovered ≥ 0 activate; if in dead-zone and depth > 1 → back; right → back (or close at root). `keyPressed` Esc → close (default).
  - `activate(node)`: leaf → `onClose()` then `CommandSender.send(node.command)`; ring/dynamic → `children = childrenProvider.apply(node)`; if `children.size() > cfg.listThreshold` → `minecraft.setScreen(new ListScreen(this, node.label, children))`; else push onto `path`, `entries = children`. Empty children → show centre text "(empty)" (for Homes, Task 7 shows "Loading…").
  - Public `void refresh()` re-resolves `entries` for the current top of `path` (Task 7 calls it when homes arrive).
- [ ] **Step 6: ListScreen.** Title at top; an `EditBox` filter (type-to-filter, case-insensitive `contains` on label and command); rows 20px: icon + label + grey command; mouse wheel scrolls; click row → leaf: close + send; ring/dynamic: open another `ListScreen`/`RadialScreen` as appropriate via the same `childrenProvider`; Esc → back to parent. Use vanilla `ObjectSelectionList` only if its 26.2 API is straightforward; otherwise hand-draw rows (preferred: simple, fewer API surprises).
- [ ] **Step 7:** `./gradlew build` → green (compile + tests).
- [ ] **Step 8: TESTING.md** — create with sections "Wheel" (hold G on ManaCube opens wheel; flick+release on Shops drills in; release on Sell sends /sell; right-click goes back; tap in centre closes; on a non-ManaCube server shows action-bar hint; edit vaultCount to 5 → reload key → Vaults shows 5; broken JSON → red message, old wheel still works; Isles ring (9 entries) opens as list).
- [ ] **Step 9: Commit** `feat(cubewheel): radial wheel, list fallback, gating and keybinds`.

---

### Task 7: Homes integration

**Files:**
- Create: `src/main/java/com/mage/cubewheel/homes/HomesFetcher.java`, `homes/ComponentReplies.java`
- Modify: `CubeWheelClient.java`, `wheel/RadialScreen.java` (loading placeholder only if needed), `TESTING.md`

**Interfaces:**
- Consumes: `HomesParser`, `HomesCache`, `HomesFetchPolicy`, `ServerGate`, `CommandSender`, `RadialScreen.childrenProvider`, `RadialScreen.refresh()`.
- Produces: `ComponentReplies.toReply(Component)` → `HomesParser.Reply` (plain text via `getString()`, click commands by walking `component.toFlatList()` / `visit` over styles and reading `Style.getClickEvent()`; in 26.x `ClickEvent` is a sealed interface with records like `ClickEvent.RunCommand(String command)` and `ClickEvent.SuggestCommand(String command)` — verify with javap and pattern-match on them).

- [ ] **Step 1:** In `onInitializeClient`: `homes = new HomesCache(configDir.resolve("cubewheel-homes.json")); homes.load();` expose `CubeWheelClient.homes()`.
- [ ] **Step 2: HomesFetcher** (singleton held by `CubeWheelClient`): `List<WheelNode> childrenFor(WheelNode node)` — if `node.isDynamic() && "homes".equals(node.dynamic)` and `ServerGate.active`: `host = currentHost()`; `if (policy.shouldFetch(now, homes.isStale(host, now, MAX_AGE_MS))) { policy.armed(now); CommandSender.send("/homes"); }`; return `WheelResolver.children(node, cfg.vaultCount, homes.get(host))`; if that list is empty and `policy.isArmed(now)` return a single non-actionable placeholder leaf `WheelNode.leaf("Loading…", "minecraft:clock", null)` — adjust `RadialScreen.activate` to ignore leaves with null command. Otherwise delegate to the default provider. Install via `RadialScreen.childrenProvider = fetcher::childrenFor`.
- [ ] **Step 3: Receive hook** — `ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> …)`: skip if `overlay` or gate inactive; `parsed = HomesParser.parse(ComponentReplies.toReply(message))`; `decision = policy.onMessage(now, parsed)`; on ACCEPT_*: `homes.put(host, parsed.get(), now); homes.save();` then if the current screen is a `RadialScreen` call `refresh()` (on the render thread: `Minecraft.getInstance().execute(...)`). Return `decision != ACCEPT_AND_SUPPRESS`. Everything in try/catch returning `true` on exception.
- [ ] **Step 4: Send hook** — `ClientSendMessageEvents.COMMAND.register(cmd -> HomesParser.parseOutgoing(cmd).ifPresent(e -> { if (e.add()) homes.add(host, e.name()); else homes.remove(host, e.name()); homes.save(); }))` (gate-checked).
- [ ] **Step 5:** `./gradlew build` → green.
- [ ] **Step 6: TESTING.md** — add "Homes" section: first open of Homes shows Loading… then homes within ~1 s; `/homes` reply not shown in chat; typing `/homes` yourself shows it normally; `/sethome test` → appears in wheel immediately; `/delhome test` → gone; reopening within 5 min sends no `/homes` (check logs).
- [ ] **Step 7: Commit** `feat(cubewheel): dynamic homes ring via suppressed /homes fetch`.

---

### Task 8: Tracker integration and capture mode

**Files:**
- Create: `src/main/java/com/mage/cubewheel/tracker/ContainerScanner.java`, `tracker/TrackerHud.java`, `tracker/TrackerScreen.java`, `capture/CaptureLog.java`
- Modify: `CubeWheelClient.java`, `TESTING.md`
- Test: `src/test/java/com/mage/cubewheel/capture/CaptureLogTest.java`, `src/test/java/com/mage/cubewheel/tracker/ContainerScannerTest.java`

**Interfaces:**
- Consumes: `TrackerStore`, `ProgressExtractor`, `TrackerSources`, `TrackerFormat`, `ServerGate`, keybinds `tracker_hud`, `tracker_picker`, `capture`.
- Produces:
  - `ContainerScanner.ItemView(int slot, String id, String name, List<String> lore)` record and `ContainerScanner.scan(String source, List<ItemView> items, TrackerStore store, long now)` → `int updated` (pure static; testable) plus the Minecraft adapter that builds `ItemView`s from an `AbstractContainerScreen`.
  - `CaptureLog(Path dir)`: `boolean enabled`, `void toggle()`, `void chat(String json, String text, long now)`, `void container(String title, List<ContainerScanner.ItemView> items, long now)` — appends one JSON line to `dir/<yyyy-MM-dd>.jsonl` (UTC date from `now`), creating dirs; IO errors logged, never thrown.

- [ ] **Step 1: Failing test** `CaptureLogTest` (`@TempDir`): enable, write one chat and one container line with `now = 0` → file `1970-01-01.jsonl` has 2 lines; parse each with Gson, assert `kind` is `chat`/`container`, container has `items[0].lore[0]`. Disabled log writes nothing. Also a `ContainerScannerTest`: items `[("Carrot Grind", ["Harvest carrots", "1,200/1,500"]), ("Deco", ["Nothing here"])]` → `scan("jobs", …)` returns 1 and store has `jobs:Carrot Grind` with current 1200.
- [ ] **Step 2:** `./gradlew test` → FAIL; implement `CaptureLog` + `ContainerScanner.scan`; → PASS. (`scan` strips `§x` formatting codes from names and lore with `replaceAll("§.", "")`, skips blank names.)
- [ ] **Step 3: Container hook.** `ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> { if (screen instanceof AbstractContainerScreen<?> cs) { … } })`. Server GUIs fill slots after the screen opens, so read items on a short delay: register `ScreenEvents.afterTick(screen)` and scan on ticks 5 and 20 after open (plus on `ScreenEvents.remove` — last chance). Title: `screen.getTitle().getString()`. Only if `ServerGate.active`: capture log (if enabled) → `CaptureLog.container(...)`; `TrackerSources.match(title, cfg.tracker.sources)` → `scan(...)`; `store.save()` if updated > 0. Only read top-container slots (exclude player inventory: slots whose `container` is the player's `Inventory`).
- [ ] **Step 4: Chat capture.** In the `ALLOW_GAME` path (Task 7's hook or a separate `ClientReceiveMessageEvents.GAME` listener): if capture enabled → `CaptureLog.chat(ComponentSerialization.CODEC → JSON string (verify API; fallback to `message.toString()`), message.getString(), now)`.
- [ ] **Step 5: TrackerHud.** Register a HUD element (Fabric `HudElementRegistry.attachElementAfter(VanillaHudElements.…, id, element)` or whatever 26.2's fabric-rendering-v1 offers — verify). If `ServerGate.active && cfg.tracker.hudVisible`: lines = `store.hudEntries(near, maxLines)`; draw at top-right, 4 px margin, semi-transparent backdrop `fill(…, 0x80000000)`, title "Tracker" in gold, each line `TrackerFormat.line(t, now)`; complete → green, ≥ near → yellow, else white. Keybind `tracker_hud` toggles `hudVisible` and saves config.
- [ ] **Step 6: TrackerScreen.** Keybind `tracker_picker` opens it (no gate needed — viewing local data). Hand-drawn list grouped by source header, rows `[★ or ☆] TrackerFormat.line`, click toggles pin (+ `store.save()`), scroll with mouse wheel, button "Forget entries older than 7 days" → `forgetOlderThan(now, 7d)`; empty state text: "Open /jobs, /pquests, /prestige or /challenges to start tracking."
- [ ] **Step 7: Capture toggle** keybind → `capture.toggle()`, action bar "Capture ON → config/cubewheel-captures" / "Capture OFF".
- [ ] **Step 8:** `./gradlew build` → green.
- [ ] **Step 9: TESTING.md** — add "Tracker & capture" section: open /jobs → entries appear in picker; pin one → HUD shows it; entries ≥80% show without pin; capture ON, run `/homes`, open `/jobs` `/pquests` `/prestige` → JSONL lines written; send the capture file back to tune parsers.
- [ ] **Step 10: Commit** `feat(cubewheel): passive progress tracker, HUD, picker and capture mode`.

---

### Task 9: Docs and release build

**Files:**
- Create: `src/client-mods/cubewheel/README.md`
- Modify: `src/client-mods/cubewheel/TESTING.md` (final pass)

- [ ] **Step 1: README** — what it is (unofficial, not affiliated with ManaCube), install (drop jar + Fabric API into mods for MC 26.2), keybinds table, config file reference with an example node of each kind (leaf / ring / dynamic with extras), rules note (every command is user-triggered; no timers; quote the ManaCube rule), how the tracker works and its limits (stale until you reopen a menu), capture mode instructions, known unknowns (Isles warp names except tangleroots unverified; `/homes` format; lore formats).
- [ ] **Step 2:** `./gradlew clean build` → green; note jar path `build/libs/cubewheel-0.1.0.jar`.
- [ ] **Step 3: Commit** `docs(cubewheel): README and manual test checklist`.
