package com.mage.cubewheel.homes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognises a server's /homes reply and outgoing home edits. Pure: no Minecraft/Fabric imports. */
public final class HomesParser {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");
	private static final Pattern CLICK = Pattern.compile("^/homes? +([A-Za-z0-9_\\-]{1,32})$");

	private HomesParser() {}

	/** text = message plain string (may contain newlines); clickCommands = run/suggest-command values, as sent. */
	public record Reply(String text, List<String> clickCommands) {}

	public record Edit(boolean add, String name) {}

	public static Optional<List<String>> parse(Reply r) {
		Set<String> clicks = new LinkedHashSet<>();
		if (r.clickCommands() != null) {
			for (String c : r.clickCommands()) {
				if (c == null) continue;
				Matcher m = CLICK.matcher(c);
				if (m.matches()) clicks.add(m.group(1));
			}
		}
		if (!clicks.isEmpty()) return Optional.of(new ArrayList<>(clicks));

		if (r.text() == null) return Optional.empty();
		for (String line : r.text().split("\\R")) {
			if (!line.toLowerCase().contains("home") || line.indexOf(':') < 0) continue;
			String rest = line.substring(line.indexOf(':') + 1).trim();
			if (rest.endsWith(".")) rest = rest.substring(0, rest.length() - 1).trim();
			if (rest.isBlank()) continue;
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

	public static Optional<Edit> parseOutgoing(String commandWithoutSlash) {
		if (commandWithoutSlash == null) return Optional.empty();
		String[] parts = commandWithoutSlash.trim().split("\\s+");
		if (parts.length == 0 || parts.length > 2) return Optional.empty();
		String cmd = parts[0].toLowerCase();
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
