package net.mage.cubewheel.tracker;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import net.mage.cubewheel.tracker.ContainerScanner.ItemView;

/**
 * Recognises ManaCube Skyblock's progress menus by what their items say. Pure: no Minecraft/Fabric imports.
 *
 * <p>Michael 2026-11-06: no real Skyblock menu capture exists yet, so every rule here follows the wiki's
 * wording (https://wiki.manacube.com/skyblock/). Each rule is a named constant so it is easy to tune once a
 * capture says what the menus really read.
 */
public final class SkyblockMenus {
	/**
	 * Prestige ranks ("Rank 1", "Rank [✪3]", "Prestige 2"). The whole name must match, so a listing such as
	 * "Rank 1 Voucher" is not a rank.
	 * Wiki: https://wiki.manacube.com/skyblock/prestige
	 */
	static final Pattern PRESTIGE_NAME = Pattern.compile("^(Rank \\d+|Rank \\[✪\\d+]|Prestige \\d+)$");

	/**
	 * The challenge categories ("Mob Challenge", "Mini Boss Challenge").
	 * Wiki: https://wiki.manacube.com/skyblock/challenges
	 */
	static final Pattern CHALLENGE_NAME = Pattern.compile(
			"(?i)^(mob|monster|dungeon|task|mining|resource|crop|fish|mini ?boss|boss) challenge$");

	/**
	 * Island quest tiers ("Beginner Island Quests", "Master Quests").
	 * Wiki: https://wiki.manacube.com/skyblock/island-quests
	 */
	static final Pattern ISLAND_QUEST_NAME = Pattern.compile(
			"(?i)^(beginner|intermediate|experienced|master|grand master|insane|epic|prestigious|legendary|godly)"
					+ "( island)? quests?$");

	/** A single island quest item says so in its lore ("Island Quest") and shows a counter. */
	private static final String ISLAND_QUEST_LORE = "island quest";

	/** A task item says "task" as a word in its lore (e.g. "Task: Kill 50 Zombies") and shows a counter. */
	private static final Pattern TASK_LORE = Pattern.compile("(?i)\\btasks?\\b");

	private SkyblockMenus() {}

	/** One of "tasks", "prestige", "isquests", "sbchallenges"; the first item that matches decides. */
	public static Optional<String> classify(List<ItemView> items) {
		if (items == null) return Optional.empty();
		for (ItemView item : items) {
			if (item == null) continue;
			String name = ContainerScanner.strip(item.name()).trim();
			List<String> lore = item.lore() == null ? List.of() : item.lore();
			boolean counted = ProgressExtractor.extract(lore).isPresent();
			if (counted && PRESTIGE_NAME.matcher(name).matches()) return Optional.of("prestige");
			if (CHALLENGE_NAME.matcher(name).matches()) return Optional.of("sbchallenges");
			if (ISLAND_QUEST_NAME.matcher(name).matches()) return Optional.of("isquests");
			// Michael 2026-11-06: lore rules need a counter, or a hub button ("Click to view your Island Quests")
			// would classify the whole menu and its neighbours' counters would become entries.
			if (counted && anyLineContains(lore, ISLAND_QUEST_LORE)) return Optional.of("isquests");
			if (counted && anyLineMatches(lore, TASK_LORE)) return Optional.of("tasks");
		}
		return Optional.empty();
	}

	private static boolean anyLineMatches(List<String> lore, Pattern p) {
		for (String line : lore) {
			if (line != null && p.matcher(ContainerScanner.strip(line)).find()) return true;
		}
		return false;
	}

	private static boolean anyLineContains(List<String> lore, String needle) {
		for (String line : lore) {
			if (line != null && ContainerScanner.strip(line).toLowerCase(Locale.ROOT).contains(needle)) return true;
		}
		return false;
	}
}
