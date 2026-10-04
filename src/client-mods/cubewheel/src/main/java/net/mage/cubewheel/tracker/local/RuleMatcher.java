package net.mage.cubewheel.tracker.local;

import net.mage.cubewheel.tracker.local.CounterRule.Any;
import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Group;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import net.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Special;

/** Does a signal advance a rule? Kind, target and world must all fit. Pure: no Minecraft/Fabric imports. */
public final class RuleMatcher {
	/** Objective nouns whose block id is a different word: "Sweet Berries" grow on sweet_berry_bush. */
	private static final java.util.Map<String, String> ID_ALIASES = java.util.Map.of(
			"sweet berry", "sweet berry bush",
			"cocoa bean", "cocoa");

	/**
	 * Metals and gems named bare in mining objectives ("Mine 201 Sandara Iron"): the material stands for its ore
	 * blocks, plain, deepslate or nether ("iron ore", "deepslate iron ore").
	 */
	private static final java.util.Set<String> ORE_MATERIALS = java.util.Set.of(
			"iron", "gold", "copper", "coal", "diamond", "emerald", "lapis", "redstone", "quartz");

	/**
	 * Ore blocks that do not count as their bare material: nether gold ore drops nuggets, and ManaCube's
	 * "Mine … Gold" jobs don't count it (Michael 2026-10-04). Nether quartz ore is the only quartz ore, so it stays.
	 */
	private static final java.util.Set<String> NOT_THE_MATERIAL = java.util.Set.of("nether gold ore");

	/**
	 * Blocks ManaCube's mana worlds use in place of a crop: Wolfhaven "wheat" is a hay block you scythe
	 * (capture 2026-09-30). World scoping still applies through the rule, so a hay bale in the overworld
	 * doesn't count toward "Harvest … Wolfhaven Wheat".
	 */
	private static final java.util.Map<String, java.util.Set<String>> STAND_IN_BLOCKS = java.util.Map.of(
			"wheat", java.util.Set.of("minecraft:hay_block"),
			// Icehaven "Mine … Ice Crystals": the crystal is a plain ice block (capture 2026-10-04); packed/blue
			// ice stay out until seen.
			"ice crystal", java.util.Set.of("minecraft:ice"));

	private RuleMatcher() {}

	public static boolean matches(CounterRule rule, Signal signal) {
		if (rule == null || signal == null || !worldMatches(rule.world(), signal.world())) return false;
		return switch (signal) {
			case Signal.BlockBroken b -> blockMatches(rule, b);
			case Signal.MobKilled m -> rule.kind() == CounterRule.Kind.KILL && !m.groups().contains("player")
					&& targetMatches(rule.what(), m.typeId(), m.name(), m.groups());
			case Signal.FishCaught f -> rule.kind() == CounterRule.Kind.FISH && fishMatches(rule.what(), f.species());
			case Signal.Sheared s -> rule.kind() == CounterRule.Kind.SHEAR
					&& targetMatches(rule.what(), s.typeId(), s.name(), java.util.Set.of("mob"));
			case Signal.Milked m -> rule.kind() == CounterRule.Kind.MILK
					&& targetMatches(rule.what(), m.typeId(), m.name(), java.util.Set.of("mob"));
		};
	}

	/**
	 * "Catch N Fish" counts every catch; "Catch N YellowSeaShroom while fishing" only that species, compared
	 * as {@link FishCatchParser#speciesKey} ("Yellow Sea Shrooms" == "YellowSeaShroom"). A catch of unknown
	 * species never counts toward a named one.
	 */
	static boolean fishMatches(CounterRule.Target what, String species) {
		if (what instanceof Any) return true;
		if (!(what instanceof Named n) || species == null) return false;
		String key = FishCatchParser.speciesKey(species);
		return !key.isEmpty() && key.equals(FishCatchParser.speciesKey(n.singular()));
	}

	private static boolean blockMatches(CounterRule rule, Signal.BlockBroken b) {
		if (rule.kind() != CounterRule.Kind.BREAK && rule.kind() != CounterRule.Kind.HARVEST) return false;
		// A stand-in block ManaCube uses for a crop counts whole, with no ripeness to check.
		if (rule.what() instanceof Named n && b.id() != null
				&& STAND_IN_BLOCKS.getOrDefault(n.singular(), java.util.Set.of()).contains(b.id())) return true;
		if (b.crop() && !b.mature()) return false; // unripe crops never count
		if (rule.kind() == CounterRule.Kind.HARVEST && !b.crop()) return false;
		// "Resources"/"Blocks": instabreak vegetation is not a resource (plugins count real blocks).
		if (rule.what() instanceof Any && b.trivial() && !b.crop()) return false;
		// Ore in a mana world gives the world's own variant ("Icehaven Diamond"), which ManaCube's bare "Mine …
		// Diamonds/Gold" jobs don't count (Michael 2026-10-04); it still counts when the rule names that world.
		if (rule.world() instanceof AnyWorld && rule.what() instanceof Named n && ORE_MATERIALS.contains(n.singular())
				&& b.world() != null && b.world().special()) return false;
		return targetMatches(rule.what(), b.id(), b.name(), b.groups());
	}

	/** Unknown worlds never satisfy a world-scoped rule (under-count rather than over-count). */
	static boolean worldMatches(CounterRule.World want, WorldInfo at) {
		if (want instanceof AnyWorld) return true;
		if (at == null || !at.known()) return false;
		if (want instanceof Special) return at.special();
		return want instanceof NamedWorld n && (at.tokens().contains(n.token()) || at.tokens().contains(WorldResolver.key(n.token())));
	}

	/**
	 * Named targets match the registry path read as words ("oak_log" -> "oak log", singularised so
	 * "potatoes" is "potato"), or a display name that equals or ends with the name ("Mana Wolf" for
	 * "wolf" and "mana wolf").
	 */
	static boolean targetMatches(CounterRule.Target what, String id, String name, java.util.Set<String> groups) {
		return switch (what) {
			case Any a -> true;
			case Group g -> groups.contains(g.group());
			case Named n -> {
				String want = n.singular();
				String path = id == null ? "" : id.substring(id.indexOf(':') + 1);
				String fromId = Singular.phrase(path);
				String fromName = displayName(name);
				String compactWant = want.replace(" ", "");
				yield fromId.equals(want) || fromId.equals(ID_ALIASES.get(want))
						|| ORE_MATERIALS.contains(want) && fromId.matches("((deepslate|nether) )?" + want + " ore")
								&& !NOT_THE_MATERIAL.contains(fromId)
						|| fromName.equals(want) || fromName.endsWith(" " + want)
						// "Rattle Snakes" vs a "Rattlesnake" mob or a "rattlesnake" id
						|| (want.indexOf(' ') > 0 && (fromName.replace(" ", "").equals(compactWant)
								|| fromId.replace(" ", "").equals(compactWant)));
			}
		};
	}

	private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
	private static final java.util.regex.Pattern BRACKETS = java.util.regex.Pattern.compile("\\[[^\\]]*]|\\([^)]*\\)");
	private static final java.util.regex.Pattern LEVEL = java.util.regex.Pattern.compile("(?i)\\b(?:lv|lvl|level)\\.?\\s*\\d+\\b");
	private static final java.util.regex.Pattern NOT_WORD = java.util.regex.Pattern.compile("[^\\p{L}\\p{N}]+");
	private static final java.util.regex.Pattern TRAILING_NUMBER = java.util.regex.Pattern.compile("(?:\\s\\d+)+$");

	/**
	 * A mob display name reduced to comparable words: small caps folded ("ᴛɪɢᴇʀ"), stack and health
	 * decorations, "[..]"/"(..)" tags, "Lv. 5" and stray glyphs dropped, then singularised: "Tiger Lvl 12",
	 * "✦ Tiger ✦" and "Dart Frog 20⺛" read as "tiger" and "dart frog".
	 */
	static String displayName(String name) {
		if (name == null) return "";
		StringBuilder b = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			int k = SMALL_CAPS.indexOf(c);
			b.append(k >= 0 ? (char) ('a' + k) : c);
		}
		String s = StackName.parse(b.toString()).map(StackName.Parsed::name).orElse("");
		s = BRACKETS.matcher(s).replaceAll(" ");
		s = LEVEL.matcher(s).replaceAll(" ");
		s = NOT_WORD.matcher(s).replaceAll(" ").trim();
		s = TRAILING_NUMBER.matcher(s).replaceAll("");
		return Singular.phrase(s);
	}
}
