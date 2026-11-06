package net.mage.cubewheel.tracker;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import net.mage.cubewheel.tracker.ContainerScanner.ItemView;

/**
 * Recognises ManaCube Skyblock's progress menus by what their items say. Pure: no Minecraft/Fabric imports.
 *
 * <p>Michael 2026-11-06 capture: every menu here opens from the /jobs hub (Island Team Quests, Task Board,
 * Prestige, Boss Challenges, Season Challenges). The hub and Boss Challenges show no counters and are no
 * tracker menus. Each rule is a named constant, tuned to that capture.
 */
public final class SkyblockMenus {
	/** A Task Board task: "SLAYER TASK", "ADVENTURE TASK". Its lore holds the objective and a "(0/76)" counter. */
	static final Pattern TASK_NAME = Pattern.compile("^[A-Z]+ TASK$");

	/** A Season Challenges category head: "Mob Challenge", "Mini Boss Challenge" (the hub's "Season Challenges" is not). */
	static final Pattern CHALLENGE_NAME = Pattern.compile("(?i)^\\w[\\w ]* Challenge$");

	/** The pane after a category head with its counter: "Current Level (1)", lore "Kill 164 / 3,500,000 Mobs". */
	static final Pattern CURRENT_LEVEL = Pattern.compile("(?i)^Current Level \\((\\d+)\\)$");

	/** The tiers of Island Team Quests, as tier headers ("Beginner Quests") and quest lore ("Beginner Quest"). */
	private static final String TIERS = "(beginner|intermediate|experienced|master|grand master|insane|epic|prestigious"
			+ "|legendary|godly)";

	/** Island Team Quests' tier headers: "Beginner Quests", "Grand Master Quests" (a book, no counter). */
	static final Pattern ISLAND_QUEST_NAME = Pattern.compile("(?i)^" + TIERS + "( island)? quests?$");

	/** An island quest's first lore line names its tier: "Beginner Quest". */
	private static final Pattern ISLAND_QUEST_LORE = Pattern.compile("(?i)^" + TIERS + " quest$");

	private SkyblockMenus() {}

	/** One of "tasks", "prestige", "isquests", "sbchallenges"; the first item that matches decides. */
	public static Optional<String> classify(List<ItemView> items) {
		if (items == null) return Optional.empty();
		for (int i = 0; i < items.size(); i++) {
			ItemView item = items.get(i);
			if (item == null) continue;
			String name = ContainerScanner.strip(item.name()).trim();
			List<String> lore = item.lore() == null ? List.of() : item.lore();
			// Michael 2026-11-06 capture: Skyblock's prestige pages ("Prestige 1 - Not Completed", "Rank [✪1]" with
			// "Harvest 25,000 crops [0/25,000]") are Survival's, so Survival's rule reads them.
			if (MenuClassifier.isPrestige(name)) return Optional.of("prestige");
			if (ISLAND_QUEST_NAME.matcher(name).matches()) return Optional.of("isquests");
			if (isChallengeHead(name) && i + 1 < items.size() && isCurrentLevel(items.get(i + 1))) {
				return Optional.of("sbchallenges");
			}
			// Michael 2026-11-06: lore rules need a counter, or a hub button would classify the whole menu and its
			// neighbours' counters would become entries (Boss Challenges' "Damage Resistance: 85%" beside "Complete
			// 5 Dragon Tasks" did, under the wiki's guess).
			boolean counted = ProgressExtractor.extract(lore).isPresent();
			if (counted && TASK_NAME.matcher(name).matches()) return Optional.of("tasks");
			if (counted && isIslandQuest(lore)) return Optional.of("isquests");
		}
		return Optional.empty();
	}

	static boolean isChallengeHead(String name) {
		return CHALLENGE_NAME.matcher(name).matches();
	}

	static boolean isCurrentLevel(ItemView item) {
		return item != null && CURRENT_LEVEL.matcher(ContainerScanner.strip(item.name()).trim()).matches();
	}

	/** "Beginner Quest" first, then a "Progress:" line. */
	private static boolean isIslandQuest(List<String> lore) {
		String first = null;
		for (String line : lore) {
			String t = ContainerScanner.strip(line).trim();
			if (!t.isEmpty()) {
				first = t;
				break;
			}
		}
		return first != null && ISLAND_QUEST_LORE.matcher(first).matches() && MenuClassifier.anyLineStarts(lore, "progress:");
	}
}
