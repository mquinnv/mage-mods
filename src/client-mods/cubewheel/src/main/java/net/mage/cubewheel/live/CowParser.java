package net.mage.cubewheel.live;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ManaCube's /cow (Cash Cow) reward broadcasts and the /cow menu's cooldown lore. Pure: no
 * Minecraft/Fabric imports.
 *
 * <p>Broadcasts seen: "[/CASHCOW] Relaxz claimed weekly ⺹ Elite Key", "[/CASHCOW] Relaxz claimed monthly ⺾
 * Ancient Key", "[/CASHCOW] Relaxz claimed their ⻁ Promo Key". The menu's wording is not captured yet; the
 * cooldown phrases below are guesses that capture mode will confirm.
 */
public final class CowParser {
	public enum Tier {
		DAILY, WEEKLY, MONTHLY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Tier find(String text) {
			if (text == null) return null;
			String t = text.toLowerCase(Locale.ROOT);
			for (Tier tier : values()) if (t.contains(tier.id())) return tier;
			return null;
		}
	}

	/** One broadcast; {@code tier} is null for "claimed their <key>" (which tier that is, is unknown). */
	public record Claim(String player, Tier tier, String reward) {}

	/** What one /cow menu item says: its tier and when it can be claimed, as ms from now (0 = now). */
	public record MenuState(Tier tier, long availableInMs) {}

	private static final Pattern CLAIM = Pattern.compile(
			"\\[/CASHCOW]\\s+(?:.*\\s)?([A-Za-z0-9_]{2,16})\\s+claimed\\s+(?:(daily|weekly|monthly)|their)\\s+(.+?)\\s*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern WAIT = Pattern.compile(
			"(?i)(?:available|claimable|claim|ready|come back|resets?|refresh(?:es)?|cooldown)\\s*(?:again\\s*)?(?:in|:)\\s*:?\\s*(.+)$");
	private static final Pattern READY = Pattern.compile("(?i)click to (?:claim|collect)|available now|claim now|ready to claim");
	private static final Pattern TOKEN = Pattern.compile(
			"(\\d+)\\s*(days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-z])", Pattern.CASE_INSENSITIVE);

	private CowParser() {}

	/** A claim broadcast, from the plain chat text (§ codes allowed). */
	public static Optional<Claim> claim(String text) {
		if (text == null) return Optional.empty();
		String s = strip(text).replace('\n', ' ').trim();
		Matcher m = CLAIM.matcher(s);
		if (!m.find()) return Optional.empty();
		Tier tier = m.group(2) == null ? null : Tier.valueOf(m.group(2).toUpperCase(Locale.ROOT));
		return Optional.of(new Claim(m.group(1), tier, m.group(3).trim()));
	}

	/**
	 * The tier and wait of one menu item: the tier from its name (or, failing that, its lore), the wait from
	 * the first lore line with "Available in 13h 2m"-like text, or 0 for "Click to claim". Empty if the item
	 * names no tier or states no time.
	 */
	public static Optional<MenuState> menuItem(String name, List<String> lore) {
		Tier tier = Tier.find(strip(name));
		List<String> lines = new ArrayList<>();
		if (lore != null) for (String l : lore) lines.add(strip(l).trim());
		if (tier == null) for (String l : lines) if ((tier = Tier.find(l)) != null) break;
		if (tier == null) return Optional.empty();
		for (String l : lines) {
			Matcher w = WAIT.matcher(l);
			if (w.find()) {
				OptionalLong ms = duration(w.group(1));
				if (ms.isPresent()) return Optional.of(new MenuState(tier, ms.getAsLong()));
			}
		}
		for (String l : lines) if (READY.matcher(l).find()) return Optional.of(new MenuState(tier, 0));
		return Optional.empty();
	}

	/** Sum of the d/h/m/s tokens in {@code text} ("13h 2m" = 13 h 2 min); empty if there are none. */
	public static OptionalLong duration(String text) {
		if (text == null) return OptionalLong.empty();
		Matcher m = TOKEN.matcher(text);
		long total = 0;
		boolean any = false;
		while (m.find()) {
			long n = Long.parseLong(m.group(1));
			total += n * switch (Character.toLowerCase(m.group(2).charAt(0))) {
				case 'd' -> 86_400_000L;
				case 'h' -> 3_600_000L;
				case 'm' -> 60_000L;
				default -> 1_000L;
			};
			any = true;
		}
		return any ? OptionalLong.of(total) : OptionalLong.empty();
	}

	static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "");
	}
}
