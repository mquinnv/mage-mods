package com.mage.cubewheel.config;

import static com.mage.cubewheel.config.WheelNode.dynamic;
import static com.mage.cubewheel.config.WheelNode.leaf;
import static com.mage.cubewheel.config.WheelNode.ring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultConfig {
	private DefaultConfig() {}

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

	public static List<WheelNode> wheel() {
		List<WheelNode> w = new ArrayList<>();
		w.add(ring("Travel", "minecraft:compass",
			leaf("Spawn", "minecraft:red_bed", "/spawn"),
			leaf("Random TP", "minecraft:grass_block", "/rtp"),
			leaf("Teleporter", "minecraft:ender_pearl", "/teleporter"),
			leaf("Warps menu", "minecraft:oak_sign", "/warp"),
			leaf("Party home", "minecraft:white_banner", "/p home"),
			leaf("Back", "minecraft:arrow", "/back")));
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
		w.add(ring("Warps", "minecraft:oak_sign",
			leaf("Crops", "minecraft:wheat", "/warp crops"),
			leaf("Spawners", "minecraft:spawner", "/warp spawners")));
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

	public static CubeWheelConfig create() {
		CubeWheelConfig c = new CubeWheelConfig();
		c.serverHosts = serverHosts();
		c.tracker.sources = trackerSources();
		c.tracker.refreshCommands = refreshCommands();
		c.tracker.sidebarLinks = sidebarLinks();
		c.tracker.local = local();
		c.wheel = wheel();
		return c;
	}
}
