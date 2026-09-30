package com.mage.cubewheel.cooldown;

import com.mage.cubewheel.hud.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a custom item's lore for ability cooldowns and a uses counter. Lore on ManaCube is sectioned:
 * "ITEM EFFECTS: (Right-Click)" (also "(Shift + Right Click)", "(Attack)", "(When Consumed)", "(Sneak)", ...),
 * effect lines, then "Cooldown: 60s". A plain heading line such as "Right-Click:" or "Consume" works too.
 * Only sections the player triggers with a click, eating/drinking or sneaking yield an {@link Ability};
 * passive ones ("While Worn", "When Attacked", "Block Attack", ...) do not, since their moment cannot be
 * seen. A cooldown before any heading is taken as right-click. Pure: no Minecraft/Fabric imports.
 */
public record ItemAbilities(List<Ability> abilities, OptionalLong uses) {
	public enum Action { USE, ATTACK, CONSUME, SNEAK }

	/** One triggerable ability: {@code sneak} = only while sneaking; {@code section} = heading text ("" if none). */
	public record Ability(Action action, boolean sneak, long cooldownMs, String section) {}

	private static final ItemAbilities NONE = new ItemAbilities(List.of(), OptionalLong.empty());
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** "ITEM EFFECTS: (Right-Click)", "(Attack)", "Abilities (Sneak):" -> prefix, trigger. */
	private static final Pattern PAREN_HEADING = Pattern.compile("^(.*?)\\(([^()]{1,40})\\)\\s*:?\\s*$");
	/** "Right-Click:", "[Shift + Left Click]", "Consume", "Right-Click Ability:". */
	private static final Pattern PLAIN_HEADING = Pattern.compile(
			"^[\\[(]?\\s*([a-z][a-z +\\-]{2,28}?)\\s*(?:abilit(?:y|ies))?\\s*[\\])]?\\s*:?\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern COOLDOWN = Pattern.compile("^\\W*cooldown\\s*:?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEADING_DURATION = Pattern.compile("^(\\d+(?:\\.\\d+)?\\s*[a-z]+)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern INLINE_COOLDOWN = Pattern.compile(
			"(\\d+(?:\\.\\d+)?\\s*(?:s|secs?|seconds?|m|mins?|minutes?|h|hours?))\\s+cooldown\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern USES = Pattern.compile(
			"^\\W*(?:remaining\\s+)?uses(?:\\s+left)?\\s*:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);

	/** A heading's meaning: an action, or null for a passive section. */
	private record Trigger(Action action, boolean sneak) {}

	public static ItemAbilities parse(List<String> lore) {
		if (lore == null || lore.isEmpty()) return NONE;
		List<Ability> out = new ArrayList<>();
		OptionalLong uses = OptionalLong.empty();
		boolean inSection = false;
		Trigger section = null;
		String sectionName = "";
		for (String raw : lore) {
			if (raw == null) continue;
			String line = FORMATTING.matcher(raw).replaceAll("").trim();
			if (line.isEmpty()) continue;
			if (uses.isEmpty()) {
				Matcher u = USES.matcher(line);
				if (u.find()) {
					uses = OptionalLong.of(Long.parseLong(u.group(1).replace(",", "")));
					continue;
				}
			}
			String heading = heading(line);
			if (heading != null) {
				inSection = true;
				section = classify(heading);
				sectionName = heading;
				continue;
			}
			OptionalLong cd = cooldown(line);
			if (cd.isEmpty() || cd.getAsLong() <= 0) continue;
			if (!inSection) out.add(new Ability(Action.USE, false, cd.getAsLong(), ""));
			else if (section != null) out.add(new Ability(section.action(), section.sneak(), cd.getAsLong(), sectionName));
		}
		return out.isEmpty() && uses.isEmpty() ? NONE : new ItemAbilities(List.copyOf(out), uses);
	}

	/** The trigger text if the line is a section heading, else null. */
	private static String heading(String line) {
		Matcher p = PAREN_HEADING.matcher(line);
		if (p.matches()) {
			String prefix = p.group(1).trim().toLowerCase(Locale.ROOT);
			boolean headingPrefix = prefix.isEmpty() || prefix.endsWith(":") || prefix.contains("effect") || prefix.contains("abilit");
			if (headingPrefix && !prefix.matches(".*\\d.*")) return p.group(2).trim();
			return null;
		}
		Matcher h = PLAIN_HEADING.matcher(line);
		if (h.matches()) {
			String t = h.group(1).trim();
			Trigger tr = classify(t);
			// A plain line only counts as a heading when it is itself a trigger phrase.
			if (tr != null && isTriggerPhrase(t)) return t;
		}
		return null;
	}

	private static boolean isTriggerPhrase(String t) {
		String s = t.toLowerCase(Locale.ROOT).replaceAll("[\\s+\\-]+", " ").trim();
		return s.matches("((shift|sneak) )?(right|left) ?click( (shift|sneak))?|(on )?consume|when (consumed|eaten)|sneak|(on )?attack");
	}

	/** Heading text -> trigger; null for passive sections. */
	private static Trigger classify(String text) {
		String t = text.toLowerCase(Locale.ROOT);
		if (t.contains("consum") || t.contains("eaten") || t.matches(".*\\beat\\b.*")) return new Trigger(Action.CONSUME, false);
		if (t.contains("while") || t.contains("when") || t.contains("attacked") || t.contains("block") || t.contains("damage")) return null;
		boolean sneak = t.contains("shift") || t.contains("sneak");
		boolean click = t.contains("click");
		if (click && t.contains("right")) return new Trigger(Action.USE, sneak);
		if (click && t.contains("left")) return new Trigger(Action.ATTACK, sneak);
		if (t.matches("\\s*(on )?attack\\s*")) return new Trigger(Action.ATTACK, false);
		if (t.matches("\\s*(sneak|shift)\\s*")) return new Trigger(Action.SNEAK, false);
		return null;
	}

	private static OptionalLong cooldown(String line) {
		Matcher c = COOLDOWN.matcher(line);
		if (c.find()) {
			String v = c.group(1).trim();
			OptionalLong d = Durations.parse(v);
			if (d.isPresent()) return d;
			Matcher lead = LEADING_DURATION.matcher(v);
			return lead.find() ? Durations.parse(lead.group(1)) : OptionalLong.empty();
		}
		Matcher inline = INLINE_COOLDOWN.matcher(line);
		return inline.find() ? Durations.parse(inline.group(1)) : OptionalLong.empty();
	}
}
