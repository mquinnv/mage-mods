package com.github.nyuppo.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Item groups for the grouped slot cycle (mage fork, 2026-11-04: "only rotate through items of the same type ...
 * my two potion things, the candy apple and the nectar ... rotate together", then tools, melee and ranged weapons
 * apart). Pure: no Minecraft imports, so it is unit-tested.
 *
 * <p>The config is a list of rules, checked in order; the first rule that matches puts the item in its group. A rule is
 * {@code group: alternative | alternative | ...}: the group label, then alternatives separated by {@code " | "} (a bar
 * with whitespace on both sides, so a regex may use a bare {@code |}). An alternative is matchers joined by
 * {@code " & "}, all of which must match. Rules with the same label are one group; a rule with no label is a group of
 * its own; the label {@code none} means "never cycled" (armor, shields, pendants). Matchers:
 * <ul>
 *   <li>{@code @tool}: the stack has the {@code minecraft:tool} component (pickaxes, axes, shovels, hoes, swords, shears)</li>
 *   <li>{@code @weapon}: the stack has the {@code minecraft:weapon} component (swords, axes, maces, spears, tridents)</li>
 *   <li>{@code name:<regex>}: the stack's display name (formatting stripped) contains a match, e.g. {@code name:(?i)candy apple}</li>
 *   <li>{@code lore:<regex>}: a lore line (formatting stripped) contains a match, e.g. {@code lore:(?i)pve damage}</li>
 *   <li>{@code id:<regex>}: the item id contains a match, e.g. {@code id:_sword$}</li>
 *   <li>anything else is an item id, e.g. {@code minecraft:bow} ({@code minecraft:} may be left out)</li>
 * </ul>
 * An item no rule matches is not cycled at all.
 */
public final class ItemGroups {
	/** The label of the rules whose items are never cycled. */
	public static final String NONE = "none";
	private static final Pattern LABEL = Pattern.compile("^([A-Za-z][\\w-]*):\\s+(.*)$");
	private static final Set<String> MATCHER_KINDS = Set.of("name", "lore", "id");

	/** What a matcher needs to know about a stack. */
	public record Item(String id, String name, List<String> lore, boolean tool, boolean weapon) {
		public Item(String id, String name, boolean tool, boolean weapon) {
			this(id, name, List.of(), tool, weapon);
		}
	}

	private interface Matcher { boolean test(Item item); }

	/** One rule: its group's index (-1 for {@code none}) and its alternatives, each a list of matchers to AND. */
	private record Rule(int group, List<List<Matcher>> alternatives) {}

	private final List<Rule> rules;
	private final List<String> labels;
	private final List<String> problems;

	private ItemGroups(List<Rule> rules, List<String> labels, List<String> problems) {
		this.rules = rules;
		this.labels = labels;
		this.problems = problems;
	}

	/** Parses the config lines; a bad matcher (an invalid regex) drops its alternative and is reported in {@link #problems()}. */
	public static ItemGroups parse(List<String> lines) {
		List<Rule> rules = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		if (lines != null) {
			for (String line : lines) {
				if (line == null || line.isBlank()) continue;
				String body = line.trim();
				String label = null;
				java.util.regex.Matcher lm = LABEL.matcher(body);
				if (lm.matches() && !MATCHER_KINDS.contains(lm.group(1).toLowerCase(Locale.ROOT))) {
					label = lm.group(1).toLowerCase(Locale.ROOT);
					body = lm.group(2);
				}
				List<List<Matcher>> alternatives = new ArrayList<>();
				for (String alt : body.split("\\s+\\|\\s+")) {
					List<Matcher> all = new ArrayList<>();
					boolean ok = true;
					for (String raw : alt.trim().split("\\s+&\\s+")) {
						String m = raw.trim();
						if (m.isEmpty()) continue;
						Matcher parsed = matcher(m, problems);
						if (parsed == null) ok = false;
						else all.add(parsed);
					}
					if (ok && !all.isEmpty()) alternatives.add(all);
				}
				if (alternatives.isEmpty()) continue;
				int group;
				if (NONE.equals(label)) {
					group = -1;
				} else if (label != null && labels.contains(label)) {
					group = labels.indexOf(label);
				} else {
					group = labels.size();
					labels.add(label == null ? "group " + (labels.size() + 1) : label);
				}
				rules.add(new Rule(group, alternatives));
			}
		}
		return new ItemGroups(rules, labels, problems);
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

	/** The index of {@code item}'s group (see {@link #label}), or -1 when it is in none (then it is not cycled). */
	public int groupOf(Item item) {
		if (item == null) return -1;
		for (Rule r : rules) {
			for (List<Matcher> all : r.alternatives()) {
				boolean hit = true;
				for (Matcher m : all) {
					if (!m.test(item)) {
						hit = false;
						break;
					}
				}
				if (hit) return r.group();
			}
		}
		return -1;
	}

	/** The label of group {@code g} ("group N" for an unlabelled rule, {@code none} for -1). */
	public String label(int g) {
		return g < 0 ? NONE : labels.get(g);
	}

	/** The number of groups (not rules). */
	public int size() {
		return labels.size();
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
