package net.mage.cubewheel.cooldown;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads mcMMO super-ability messages (action bar or chat, as ManaCube Survival sends them):
 * <pre>
 * §a●● §a§lSUPER BREAKER ACTIVATED §a●●                                        -> Activated
 * §b§lMINING §7§l» §7Your §eSuper Breaker §7ability is §arefreshed!            -> Refreshed
 * §amcMMO§7 ➡ §7You are too tired to use that ability again. §e§n(12s)         -> TooTired
 * §b§lMINING §7§l» §7You §a§nready§7 your pickaxe.                              -> Readied
 * </pre>
 * Every pattern is anchored at the start of the line, so player chat ("§rSteve: SUPER BREAKER ACTIVATED")
 * never matches. Pure: no Minecraft/Fabric imports.
 */
public final class McmmoParser {
	/** The standard mcMMO super abilities; {@code defaultCooldownS} is mcMMO's default cooldown. */
	public enum Ability {
		SUPER_BREAKER("Super Breaker", "Mining", 240),
		GIGA_DRILL_BREAKER("Giga Drill Breaker", "Excavation", 240),
		TREE_FELLER("Tree Feller", "Woodcutting", 240),
		SERRATED_STRIKES("Serrated Strikes", "Swords", 240),
		SKULL_SPLITTER("Skull Splitter", "Axes", 240),
		BERSERK("Berserk", "Unarmed", 240),
		GREEN_TERRA("Green Terra", "Herbalism", 240),
		BLAST_MINING("Blast Mining", "Mining", 60);

		public final String label;
		public final String skill;
		public final int defaultCooldownS;

		Ability(String label, String skill, int defaultCooldownS) {
			this.label = label;
			this.skill = skill;
			this.defaultCooldownS = defaultCooldownS;
		}

		/** "SUPER BREAKER", "super  breaker", "Super Breaker" -> SUPER_BREAKER; else empty. */
		public static Optional<Ability> byName(String name) {
			if (name == null) return Optional.empty();
			String n = NON_LETTERS.matcher(name).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
			for (Ability a : values()) {
				if (a.label.toLowerCase(Locale.ROOT).equals(n)) return Optional.of(a);
			}
			return Optional.empty();
		}
	}

	/** The tool a "You ready your X." message names. */
	public enum Tool { PICKAXE, SHOVEL, AXE, HOE, SWORD, FISTS }

	public sealed interface Message permits Activated, Refreshed, TooTired, Readied {}

	public record Activated(Ability ability) implements Message {}

	public record Refreshed(Ability ability) implements Message {}

	/** "You are too tired to use that ability again. (12s)": the ability is not given. */
	public record TooTired(int seconds) implements Message {}

	public record Readied(Tool tool) implements Message {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern NON_LETTERS = Pattern.compile("[^A-Za-z]+");
	/** Player chat on ManaCube: "§r" then "[Rank] Name: ". */
	private static final Pattern PLAYER_CHAT = Pattern.compile("^§r[^:]{0,64}:\\s");
	/** Optional "MINING » " / "EXCAVATION » " skill prefix. */
	private static final String SKILL_PREFIX = "(?:[a-z][a-z ]{0,30}»\\s*)?";
	private static final Pattern ACTIVATED = Pattern.compile("^[^a-z0-9]*([a-z][a-z ]{2,40}?)\\s+activated[^a-z0-9]*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern REFRESHED = Pattern.compile(
			"^" + SKILL_PREFIX + "[^a-z0-9]*your\\s+[^a-z]*([a-z][a-z ]{2,40}?)[^a-z]*\\s+ability\\s+is\\s+refreshed\\W*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TOO_TIRED = Pattern.compile(
			"^(?:mcmmo\\W*)?you\\s+are\\s+too\\s+tired\\s+to\\s+use\\s+that\\s+ability\\s+again\\W*\\(\\s*(\\d{1,5})\\s*s\\s*\\)\\W*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern READIED = Pattern.compile(
			"^" + SKILL_PREFIX + "[^a-z0-9]*you\\s+ready\\s+your\\s+([a-z]+)\\W*$", Pattern.CASE_INSENSITIVE);

	private McmmoParser() {}

	/** Parses one message (legacy § codes allowed); empty if it is not one of the four. */
	public static Optional<Message> parse(String raw) {
		if (raw == null || raw.isEmpty() || raw.length() > 256 || PLAYER_CHAT.matcher(raw).find()) return Optional.empty();
		String line = WHITESPACE.matcher(FORMATTING.matcher(raw).replaceAll("")).replaceAll(" ").trim();
		if (line.isEmpty()) return Optional.empty();
		Matcher m = TOO_TIRED.matcher(line);
		if (m.matches()) return Optional.of(new TooTired(Integer.parseInt(m.group(1))));
		m = REFRESHED.matcher(line);
		if (m.matches()) return Ability.byName(m.group(1)).map(Refreshed::new);
		m = READIED.matcher(line);
		if (m.matches()) return tool(m.group(1)).map(Readied::new);
		m = ACTIVATED.matcher(line);
		if (m.matches()) return Ability.byName(m.group(1)).map(Activated::new);
		return Optional.empty();
	}

	private static Optional<Tool> tool(String word) {
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "pickaxe", "pick" -> Optional.of(Tool.PICKAXE);
			case "shovel", "spade" -> Optional.of(Tool.SHOVEL);
			case "axe" -> Optional.of(Tool.AXE);
			case "hoe" -> Optional.of(Tool.HOE);
			case "sword" -> Optional.of(Tool.SWORD);
			case "fists", "fist" -> Optional.of(Tool.FISTS);
			default -> Optional.empty();
		};
	}
}
