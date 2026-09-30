package com.mage.cubewheel.homes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognises a server's /homes reply and outgoing home edits. Pure: no Minecraft/Fabric imports. */
public final class HomesParser {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");
	private static final Pattern CLICK_ANY = Pattern.compile("^/homes? +(\\S+)$");
	private static final Pattern HEADER = Pattern.compile(
			"^\\W*(your\\s+)?homes?\\b\\s*(\\(\\d+\\)|\\[\\d+\\])?\\s*:", Pattern.CASE_INSENSITIVE);

	private HomesParser() {}

	/** text = message plain string (may contain newlines); clickCommands = run/suggest-command values, as sent. */
	public record Reply(String text, List<String> clickCommands) {}

	public record Edit(boolean add, String name) {}

	public static Optional<List<String>> parse(Reply r) {
		String text = r.text() == null ? "" : r.text();
		String[] lines = text.split("\\R");

		Set<String> clicks = new LinkedHashSet<>();
		if (r.clickCommands() != null) {
			for (String c : r.clickCommands()) {
				if (c == null) continue;
				Matcher m = CLICK_ANY.matcher(c);
				if (!m.matches()) continue;
				if (!NAME.matcher(m.group(1)).matches()) return Optional.empty();
				clicks.add(m.group(1));
			}
		}
		if (!clicks.isEmpty()) {
			boolean header = false;
			for (String line : lines) {
				if (headerMatcher(line).find()) {
					header = true;
					break;
				}
			}
			if (header || clicks.size() >= 2) return Optional.of(new ArrayList<>(clicks));
		}

		for (String line : lines) {
			Matcher h = headerMatcher(line);
			if (!h.find()) continue;
			String rest = line.substring(h.end()).trim();
			if (rest.endsWith(".")) rest = rest.substring(0, rest.length() - 1).trim();
			if (rest.isBlank()) continue;
			if (rest.equalsIgnoreCase("none") || rest.equalsIgnoreCase("no")) return Optional.of(List.of());
			Set<String> names = new LinkedHashSet<>();
			boolean ok = true;
			for (String tok : rest.split(",")) {
				String t = tok.trim();
				if (!NAME.matcher(t).matches()) {
					ok = false;
					break;
				}
				names.add(t);
			}
			if (ok && !names.isEmpty()) return Optional.of(new ArrayList<>(names));
		}
		return Optional.empty();
	}

	/** Header at line start: "Homes:", "Your homes (3):", "Home:". Matches through the colon. */
	private static Matcher headerMatcher(String line) {
		return HEADER.matcher(line);
	}

	public static Optional<Edit> parseOutgoing(String commandWithoutSlash) {
		if (commandWithoutSlash == null) return Optional.empty();
		String[] parts = commandWithoutSlash.trim().split("\\s+");
		if (parts.length == 0 || parts.length > 2) return Optional.empty();
		String cmd = parts[0].toLowerCase(Locale.ROOT);
		String arg = parts.length == 2 ? parts[1] : null;
		if (arg != null && !NAME.matcher(arg).matches()) return Optional.empty();
		switch (cmd) {
			case "sethome":
				return Optional.of(new Edit(true, arg != null ? arg : "home"));
			case "delhome":
			case "deletehome":
			case "rmhome":
				return arg != null ? Optional.of(new Edit(false, arg)) : Optional.empty();
			default:
				return Optional.empty();
		}
	}
}
