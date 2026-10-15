package net.mage.cubewheel.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code /mcmmo} "Skills Panel | Browsing..." menu (captured 2026-10-01): 17 skill items named
 * {@code SKILL ▪ [CATEGORY]}, each with a lore line {@code Level: 137/2500}; Smelting's lore also says "This Skill
 * is a Child Skill". The menu carries levels only, never XP. Other items (General Information, Guides & Settings, Go
 * Back) have no such name and are ignored. Pure: callers copy their stacks into {@link Item}.
 */
public final class SkillsMenu {
	/** One menu stack's display name and lore lines, as plain or {@code §}-coded text. */
	public record Item(String name, List<String> lore) {}

	/** One skill's level and cap as the menu shows them; {@code child} for Smelting. */
	public record Entry(Skill skill, int level, int cap, boolean child) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern NAME = Pattern.compile("^(.+?)\\s*▪\\s*\\[(GATHERING|COMBAT|MISC)\\]$");
	private static final Pattern LEVEL = Pattern.compile("^Level:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)$");

	private SkillsMenu() {}

	/** The skill entries among {@code items}, in order; items that are not skill items are skipped. */
	public static List<Entry> parse(List<Item> items) {
		List<Entry> out = new ArrayList<>();
		if (items == null) return out;
		for (Item item : items) {
			entry(item).ifPresent(out::add);
		}
		return out;
	}

	/** Two or more skill entries; the title is deliberately not consulted. */
	public static boolean isSkillsMenu(List<Item> items) {
		return parse(items).size() >= 2;
	}

	private static Optional<Entry> entry(Item item) {
		if (item == null || item.name() == null || item.lore() == null) return Optional.empty();
		Matcher n = NAME.matcher(strip(item.name()));
		if (!n.matches()) return Optional.empty();
		Optional<Skill> skill = Skill.byName(n.group(1));
		if (skill.isEmpty()) return Optional.empty();
		Integer level = null;
		Integer cap = null;
		boolean child = false;
		for (String raw : item.lore()) {
			if (raw == null) continue;
			String line = strip(raw);
			if (line.contains("Child Skill")) child = true;
			Matcher l = LEVEL.matcher(line);
			if (level == null && l.matches()) {
				try {
					level = Integer.parseInt(l.group(1).replace(",", ""));
					cap = Integer.parseInt(l.group(2).replace(",", ""));
				} catch (NumberFormatException e) {
					level = null;
					cap = null;
				}
			}
		}
		if (level == null) return Optional.empty();
		return Optional.of(new Entry(skill.get(), level, cap, child));
	}

	private static String strip(String s) {
		return FORMATTING.matcher(s).replaceAll("").trim();
	}
}
