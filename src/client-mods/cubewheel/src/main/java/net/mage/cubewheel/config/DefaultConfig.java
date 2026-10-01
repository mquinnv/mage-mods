package net.mage.cubewheel.config;

import static net.mage.cubewheel.config.WheelNode.dynamic;
import static net.mage.cubewheel.config.WheelNode.leaf;
import static net.mage.cubewheel.config.WheelNode.ring;
import static net.mage.cubewheel.config.WheelNode.slice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultConfig {
	private DefaultConfig() {}

	/** Current config format; see ConfigStore.migrate. */
	public static final int CONFIG_VERSION = 5;
	/** Default HUD lines; files from before version 2 with an old default (6 or 8) are upgraded to it. */
	public static final int HUD_MAX_LINES = 10;

	public static List<String> serverHosts() {
		return new ArrayList<>(List.of("manacube.com", "manacube.net"));
	}

	public static Map<String, String> trackerSources() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("jobs", "(?i)jobs");
		m.put("pquests", "(?i)quest");
		m.put("prestige", "(?i)prestige");
		m.put("challenges", "(?i)challenge");
		return m;
	}

	public static List<String> refreshCommands() {
		return new ArrayList<>(List.of("/pquests", "/prestige", "/jobs"));
	}

	/** The pre-2026-09-30 default for "Skills"; it also matched unrelated entries, so loading upgrades it. */
	public static final String OLD_SKILLS_LINK = "(?i)skill level";
	/** Only prestige objectives such as "Rank [✪4] · Reach 2,500 Skill Level". */
	public static final String SKILLS_LINK = "(?i)reach [\\d,]+ skill level";

	/** ManaCube Survival's sidebar title is "SURVIVAL"; the hub and other gamemodes show other titles. */
	public static final String SURVIVAL_SIDEBAR = "(?i)survival";

	public static Map<String, String> sidebarLinks() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("Skills", SKILLS_LINK);
		return m;
	}

	/** ManaCube's Mana worlds; used both as world names and as the "special worlds". */
	public static List<String> manaWorlds() {
		return new ArrayList<>(List.of("wolfhaven", "tangleroots", "sandara", "icehaven", "morend", "burninglands"));
	}

	public static CubeWheelConfig.Local local() {
		CubeWheelConfig.Local l = new CubeWheelConfig.Local();
		l.worlds = manaWorlds();
		l.specialWorlds = manaWorlds();
		return l;
	}

	/** Menus read for /cow reward cooldowns (the real title is unconfirmed; menus opened right after /cow always are). */
	public static final String COW_MENU_TITLE = "(?i)cash ?cow|daily reward";

	/** Label of the top-level ring that collects everything else (and, after an upgrade, your own entries). */
	public static final String MORE = "More";

	/** Boss spawn "Location:" regex -> warp. Locations seen: "Boss Arena", "Wolfhaven Mines", "Sandara Canyon", "Morend". */
	public static Map<String, String> bossWarps() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("(?i)boss arena", "/warp boss");
		m.put("(?i)wolfhaven", "/warp wolfhaven");
		m.put("(?i)tangleroot", "/warp tangleroots");
		m.put("(?i)sandara", "/warp sandara");
		m.put("(?i)icehaven", "/warp icehaven");
		m.put("(?i)morend", "/warp morend");
		m.put("(?i)burning ?lands", "/warp burninglands");
		return m;
	}

	/**
	 * Version 3 layout: the most used entries at the top level (the first ones get the first fan positions,
	 * slice 0 at the top), everything else under More. Every ring below the top holds at most 8 entries.
	 */
	/** One slice of several tiers: the first node on the ring, each next one an outer tier beyond the last. */
	static WheelNode chain(WheelNode... tiers) {
		for (int i = tiers.length - 1; i > 0; i--) tiers[i - 1].withOuter(tiers[i]);
		return tiers[0];
	}

	public static List<WheelNode> wheel() {
		List<WheelNode> w = new ArrayList<>();
		// Player warps: crops and spawners are player Sushi's warps, the ones most people want.
		// Two tiers on one slice: Crops on the ring, Spawners just outside it (point further out).
		w.add(leaf("Sushi", "minecraft:wheat", "/warp crops")
			.withOuter(leaf("Spawners", "minecraft:spawner", "/warp spawners")));
		// Homes: click for the full homes ring; hover to fan the (cached) homes out in an arc.
		w.add(dynamic("Homes", "minecraft:red_bed", "homes").shownAsArc());
		// Travel as one slice: Spawn on the ring, then outward (or scroll) Random TP, Teleporter, Warps menu, Back.
		w.add(chain(
			leaf("Spawn", "minecraft:compass", "/spawn"),
			leaf("Random TP", "minecraft:grass_block", "/rtp"),
			leaf("Teleporter", "minecraft:ender_pearl", "/teleporter"),
			leaf("Warps menu", "minecraft:oak_sign", "/warp"),
			leaf("Back", "minecraft:arrow", "/back")));
		w.add(leaf("Jobs", "minecraft:iron_pickaxe", "/jobs"));
		w.add(leaf("Kilton", "minecraft:skeleton_skull", "/kilton"));
		// Three tiers: the sell menu on the ring, sell hand outside it, sell all furthest out.
		w.add(leaf("Sell", "minecraft:gold_ingot", "/sell")
			.withOuter(leaf("Sell hand", "minecraft:gold_nugget", "/sell hand")
				.withOuter(leaf("Sell all", "minecraft:gold_block", "/sell all"))));
		// Vaults as one slice: PV 1 on the ring, then PV 2, the party vault, and the full vault menu.
		w.add(chain(
			leaf("PV 1", "minecraft:ender_chest", "/pv 1"),
			leaf("PV 2", "minecraft:ender_chest", "/pv 2"),
			leaf("Party vault", "minecraft:barrel", "/p vault"),
			leaf("All vaults", "minecraft:chest", "/pv")));
		w.add(leaf("Fly", "minecraft:feather", "/fly"));
		// Isles: the menu on the ring, the isle warps fanned out in an arc beyond it while it is hovered.
		w.add(leaf("Isles", "minecraft:filled_map", "/isles").withArc(
			leaf("Wolfhaven", "minecraft:bone", "/warp wolfhaven"),
			leaf("Tangleroots", "minecraft:vine", "/warp tangleroots"),
			leaf("Sandara", "minecraft:sand", "/warp sandara"),
			leaf("Icehaven", "minecraft:packed_ice", "/warp icehaven"),
			leaf("Morend", "minecraft:end_stone", "/warp morend"),
			leaf("Burninglands", "minecraft:magma_block", "/warp burninglands")));
		w.add(chain(
			leaf("Party quests", "minecraft:writable_book", "/pquests"),
			leaf("Prestige", "minecraft:nether_star", "/prestige"),
			leaf("Challenges", "minecraft:target", "/challenges")));
		w.add(leaf("Daily reward", "minecraft:milk_bucket", "/cow"));
		w.add(slice("Boss event", "minecraft:wither_skeleton_skull", "boss"));
		// TPA: "Accept <name>" (/tpaccept) while a teleport request is pending; friends to /tpa in its arc.
		w.add(slice("TPA", "minecraft:player_head", "tpa"));
		w.add(ring(MORE, "minecraft:chest",
			ring("Shops", "minecraft:emerald",
				leaf("Alchemist", "minecraft:brewing_stand", "/alchemist"),
				leaf("Enchanter", "minecraft:enchanting_table", "/enchanter"),
				leaf("Shop", "minecraft:emerald", "/shop"),
				leaf("Auction house", "minecraft:gold_block", "/ah"),
				leaf("Forge", "minecraft:anvil", "/forge"),
				leaf("Fish shop", "minecraft:cod", "/fish")),
			ring("Warps", "minecraft:oak_sign",
				// Server warps from the ManaCube Survival command list (wiki), first so a double-click lands here.
				ring("Server warps", "minecraft:lodestone",
					leaf("Pond", "minecraft:water_bucket", "/warp pond"),
					leaf("Crates", "minecraft:chest", "/warp crates"),
					leaf("Enchanter", "minecraft:enchanting_table", "/warp enchanter"),
					leaf("Kilton", "minecraft:skeleton_skull", "/warp kilton"),
					leaf("Leaderboard", "minecraft:oak_hanging_sign", "/warp leaderboard"),
					leaf("PvP", "minecraft:iron_sword", "/warp pvp"),
					leaf("1v1", "minecraft:shield", "/warp 1v1")),
				ring("Bosses", "minecraft:wither_skeleton_skull",
					leaf("Boss arena", "minecraft:wither_skeleton_skull", "/warp boss"),
					leaf("Bosses", "minecraft:nether_star", "/bosses"))),
			ring("Party", "minecraft:white_banner",
				leaf("Party menu", "minecraft:white_banner", "/p"),
				leaf("Party home", "minecraft:white_banner", "/p home"),
				leaf("Party warps", "minecraft:lodestone", "/p warps"),
				leaf("Claim", "minecraft:golden_shovel", "/p claim"),
				leaf("Map", "minecraft:map", "/p map"),
				leaf("Party vault", "minecraft:barrel", "/p vault")),
			leaf("Ender chest", "minecraft:ender_chest", "/ec")));
		return w;
	}

	/** The version 1-2 default wheel, kept so tests can check the upgrade from it. */
	static List<WheelNode> wheelV2() {
		List<WheelNode> w = new ArrayList<>();
		w.add(ring("Travel", "minecraft:compass",
			leaf("Spawn", "minecraft:red_bed", "/spawn"),
			leaf("Random TP menu", "minecraft:grass_block", "/rtp"),
			leaf("Teleporter", "minecraft:ender_pearl", "/teleporter"),
			leaf("Warps menu", "minecraft:oak_sign", "/warp"),
			leaf("Party home", "minecraft:white_banner", "/p home"),
			leaf("Back", "minecraft:arrow", "/back")));
		w.add(leaf("Fly", "minecraft:feather", "/fly"));
		w.add(dynamic("Homes", "minecraft:red_bed", "homes"));
		w.add(dynamic("Vaults", "minecraft:ender_chest", "vaults",
			leaf("Ender chest", "minecraft:ender_chest", "/ec"),
			leaf("Party vault", "minecraft:barrel", "/p vault")));
		w.add(ring("Shops", "minecraft:emerald",
			leaf("Sell", "minecraft:gold_ingot", "/sell"),
			leaf("Kilton", "minecraft:skeleton_skull", "/kilton"),
			leaf("Alchemist", "minecraft:brewing_stand", "/alchemist"),
			leaf("Enchanter", "minecraft:enchanting_table", "/enchanter"),
			leaf("Shop", "minecraft:emerald", "/shop"),
			leaf("Auction house", "minecraft:gold_block", "/ah"),
			leaf("Forge", "minecraft:anvil", "/forge"),
			leaf("Fish shop", "minecraft:cod", "/fish")));
		w.add(ring("Sell", "minecraft:gold_ingot",
			leaf("Sell menu", "minecraft:gold_ingot", "/sell"),
			leaf("Sell hand", "minecraft:gold_nugget", "/sell hand"),
			leaf("Sell all", "minecraft:gold_block", "/sell all")));
		w.add(ring("Warps", "minecraft:oak_sign",
			// Server warps from the ManaCube Survival command list (wiki), first so a double-click lands here.
			ring("Server warps", "minecraft:lodestone",
				leaf("Pond", "minecraft:water_bucket", "/warp pond"),
				leaf("Crates", "minecraft:chest", "/warp crates"),
				leaf("Enchanter", "minecraft:enchanting_table", "/warp enchanter"),
				leaf("Kilton", "minecraft:skeleton_skull", "/warp kilton"),
				leaf("Leaderboard", "minecraft:oak_hanging_sign", "/warp leaderboard"),
				leaf("PvP", "minecraft:iron_sword", "/warp pvp"),
				leaf("1v1", "minecraft:shield", "/warp 1v1")),
			// Player warps: crops and spawners are player Sushi's warps, the ones most people want.
			ring("Player warps", "minecraft:player_head",
				leaf("Crops (Sushi)", "minecraft:wheat", "/warp crops"),
				leaf("Spawners (Sushi)", "minecraft:spawner", "/warp spawners"))));
		w.add(ring("Isles & Bosses", "minecraft:filled_map",
			leaf("Isles menu", "minecraft:map", "/isles"),
			leaf("Wolfhaven", "minecraft:bone", "/warp wolfhaven"),
			leaf("Tangleroots", "minecraft:vine", "/warp tangleroots"),
			leaf("Sandara", "minecraft:sand", "/warp sandara"),
			leaf("Icehaven", "minecraft:packed_ice", "/warp icehaven"),
			leaf("Morend", "minecraft:end_stone", "/warp morend"),
			leaf("Burninglands", "minecraft:magma_block", "/warp burninglands"),
			leaf("Boss arena", "minecraft:wither_skeleton_skull", "/warp boss"),
			leaf("Bosses", "minecraft:nether_star", "/bosses")));
		w.add(ring("Progress", "minecraft:experience_bottle",
			leaf("Jobs", "minecraft:iron_pickaxe", "/jobs"),
			leaf("Party quests", "minecraft:writable_book", "/pquests"),
			leaf("Prestige", "minecraft:nether_star", "/prestige"),
			leaf("Challenges", "minecraft:target", "/challenges"),
			leaf("Daily reward", "minecraft:milk_bucket", "/cow")));
		w.add(ring("Party", "minecraft:white_banner",
			leaf("Party menu", "minecraft:white_banner", "/p"),
			leaf("Party warps", "minecraft:lodestone", "/p warps"),
			leaf("Claim", "minecraft:golden_shovel", "/p claim"),
			leaf("Map", "minecraft:map", "/p map")));
		return w;
	}

	/** HUD panels default to the top-left corner, which vanilla leaves empty (the tracker HUD is top right). */
	public static CubeWheelConfig.Position boostersPosition() {
		return new CubeWheelConfig.Position("top_left", 4, 4);
	}

	public static CubeWheelConfig.Position cooldownsPosition() {
		return new CubeWheelConfig.Position("top_left", 4, 4);
	}

	public static CubeWheelConfig.Position eventsPosition() {
		return new CubeWheelConfig.Position("top_left", 4, 4);
	}

	/** The Status panel: top left, first, so Jobs and Tracker stack under it. */
	public static CubeWheelConfig.Position statusPosition() {
		return new CubeWheelConfig.Position("top_left", 4, 4);
	}

	/** Registered after the other panels, so in the same corner it stacks below them. */
	public static CubeWheelConfig.Position jobsPanelPosition() {
		return new CubeWheelConfig.Position("top_left", 4, 4);
	}

	/** Registered after the Jobs panel, so in the same corner it stacks under it. */
	public static CubeWheelConfig.Position trackerPosition() {
		return new CubeWheelConfig.Position("top_left", 4, 4);
	}

	/**
	 * ManaCube's wiki (Survival > Survival Events) gives its times in "EST"; captures show they follow New York
	 * wall-clock time (KOTH began 12:30 EDT and LPS was announced for 13:00 EDT on 2026-09-30).
	 */
	public static final String EVENTS_TIMEZONE = "America/New_York";

	/**
	 * Only what the wiki page states. KOTH: the page says "every two hours" but lists 12:30, 2:30, 4:30, 6:30,
	 * 10:30 AM/PM (no 8:30), so the listed times are used. Boss: 1:30 ... 9:30 AM/PM as listed (no 11:30). Magic
	 * Pond, Morender Dragon and Shadow Sorcerer have no times on the page.
	 */
	public static List<CubeWheelConfig.EventDef> events() {
		List<CubeWheelConfig.EventDef> l = new ArrayList<>();
		l.add(new CubeWheelConfig.EventDef("LPS", "at 08:00, 13:00, 17:00"));
		l.add(new CubeWheelConfig.EventDef("KOTH", "at 00:30, 02:30, 04:30, 06:30, 10:30, 12:30, 14:30, 16:30, 18:30, 22:30"));
		l.add(new CubeWheelConfig.EventDef("Boss", "at 01:30, 03:30, 05:30, 07:30, 09:30, 13:30, 15:30, 17:30, 19:30, 21:30"));
		l.add(new CubeWheelConfig.EventDef("Golden Knight", "every 3h from 00:15"));
		l.add(new CubeWheelConfig.EventDef("Cursed Witch", "every 3h from 01:15"));
		l.add(new CubeWheelConfig.EventDef("Desert Golem", "every 3h from 02:15"));
		return l;
	}

	public static CubeWheelConfig create() {
		CubeWheelConfig c = new CubeWheelConfig();
		c.configVersion = CONFIG_VERSION;
		c.serverHosts = serverHosts();
		c.tracker.sources = trackerSources();
		c.tracker.refreshCommands = refreshCommands();
		c.tracker.sidebarLinks = sidebarLinks();
		c.tracker.local = local();
		c.events.schedule = events();
		c.events.bossWarps = bossWarps();
		c.wheel = wheel();
		return c;
	}
}
