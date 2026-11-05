package com.github.nyuppo.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Item groups for the grouped slot cycle (mage fork, 2026-11-04: "only rotate through items of the same type ...
 * tools and weapons should be interchangeable ... my two potion things, the candy apple and the nectar ... rotate
 * together"). Pure: no Minecraft imports, so it is unit-tested.
 *
 * <p>A group is one config line of matchers separated by {@code " | "} (a bar with whitespace on both sides, so a regex
 * may use a bare {@code |} for alternation). An item is in the first group with a matching matcher:
 * <ul>
 *   <li>{@code @tool}: the stack has the {@code minecraft:tool} component (pickaxes, axes, shovels, hoes, swords, shears)</li>
 *   <li>{@code @weapon}: the stack has the {@code minecraft:weapon} component (swords, axes, maces, spears, tridents)</li>
 *   <li>{@code name:<regex>}: the stack's display name (formatting stripped) contains a match, e.g. {@code name:(?i)candy apple}</li>
 *   <li>{@code lore:<regex>}: a lore line (formatting stripped) contains a match, e.g. {@code lore:(?i)pve damage}</li>
 *   <li>{@code id:<regex>}: the item id contains a match, e.g. {@code id:_sword$}</li>
 *   <li>anything else is an item id, e.g. {@code minecraft:bow} ({@code minecraft:} may be left out)</li>
 * </ul>
 * An item in no group is not cycled at all.
 */
public final class ItemGroups {
	/** What a matcher needs to know about a stack. */
	public record Item(String id, String name, List<String> lore, boolean tool, boolean weapon) {
		public Item(String id, String name, boolean tool, boolean weapon) {
			this(id, name, List.of(), tool, weapon);
		}
	}

	private interface Matcher { boolean test(Item item); }

	private final List<List<Matcher>> groups;
	private final List<String> problems;

	private ItemGroups(List<List<Matcher>> groups, List<String> problems) {
		this.groups = groups;
		this.problems = problems;
	}

	/** Parses the config lines; a bad matcher (an invalid regex) is skipped and reported in {@link #problems()}. */
	public static ItemGroups parse(List<String> lines) {
		List<List<Matcher>> groups = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		if (lines != null) {
			for (String line : lines) {
				if (line == null || line.isBlank()) continue;
				List<Matcher> group = new ArrayList<>();
				for (String raw : line.trim().split("\\s+\\|\\s+")) {
					String m = raw.trim();
					if (m.isEmpty()) continue;
					Matcher parsed = matcher(m, problems);
					if (parsed != null) group.add(parsed);
				}
				if (!group.isEmpty()) groups.add(group);
			}
		}
		return new ItemGroups(groups, problems);
	}

	private static Matcher matcher(String m, List<String> problems) {
		String lower = m.toLowerCase(Locale.ROOT);
		if (lower.equals("@tool")) return Item::tool;
		if (lower.equals("@weapon")) return Item::weapon;
		if (lower.startsWith("name:") || lower.startsWith("lore:") || lower.startsWith("id:")) {
			String kind = lower.substring(0, lower.indexOf(':'));
			Pattern p;
			try {
				p = Pattern.compile(m.substring(kind.length() + 1).trim());
			} catch (PatternSyntaxException e) {
				problems.add("bad " + kind + " pattern \"" + m + "\": " + e.getDescription());
				return null;
			}
			return switch (kind) {
				case "name" -> item -> item.name() != null && p.matcher(item.name()).find();
				case "id" -> item -> item.id() != null && p.matcher(item.id()).find();
				default -> item -> {
					if (item.lore() != null) for (String l : item.lore()) if (l != null && p.matcher(l).find()) return true;
					return false;
				};
			};
		}
		String id = lower.contains(":") ? lower : "minecraft:" + lower;
		return item -> id.equals(item.id());
	}

	/** The index of the first group {@code item} is in, or -1 when it is in none (then it is not cycled). */
	public int groupOf(Item item) {
		if (item == null) return -1;
		for (int g = 0; g < groups.size(); g++) {
			for (Matcher m : groups.get(g)) if (m.test(item)) return g;
		}
		return -1;
	}

	public int size() {
		return groups.size();
	}

	public List<String> problems() {
		return problems;
	}

	/**
	 * The next slot to swap into the hand: the first slot after {@code cursor} (before it, when {@code step} is
	 * negative) whose item is in the group, wrapping around; -1 when none is. {@code inGroup[i]} says whether slot
	 * {@code first + i} holds an item of the group. Starting from the last slot swapped makes repeated presses walk
	 * every item of the group in turn (A, B, C, A, ...), since each swap leaves the previous hand item in that slot.
	 */
	public static int nextSlot(boolean[] inGroup, int first, int cursor, int step) {
		int n = inGroup.length;
		if (n == 0) return -1;
		int dir = step < 0 ? -1 : 1;
		int start = cursor - first; // may be outside [0, n): then the walk starts at an end
		for (int k = 1; k <= n; k++) {
			int i = Math.floorMod(start + dir * k, n);
			if (start < 0 || start >= n) i = dir > 0 ? k - 1 : n - k;
			if (inGroup[i]) return first + i;
		}
		return -1;
	}
}
