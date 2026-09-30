package com.mage.cubewheel.tracker.local;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the current world from the dimension id ("minecraft:wolfhaven": Paper names custom worlds'
 * dimensions after the world) and a sidebar "World: X" line. Pure: no Minecraft/Fabric imports.
 */
public final class WorldResolver {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** "World: Sandara" anywhere in a sidebar line (icon glyphs before it are ignored). */
	private static final Pattern WORLD_LINE = Pattern.compile("(?i)\\bworld\\s*:\\s*(.+)$");
	private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");

	private WorldResolver() {}

	public static WorldInfo resolve(String dimension, Collection<String> sidebarLines, Collection<String> specialWorlds) {
		Set<String> tokens = new LinkedHashSet<>();
		boolean known = false;
		if (dimension != null && !dimension.isBlank()) {
			known = true;
			String d = dimension.trim().toLowerCase(java.util.Locale.ROOT);
			int colon = d.indexOf(':');
			String namespace = colon < 0 ? "" : d.substring(0, colon);
			String path = colon < 0 ? d : d.substring(colon + 1);
			addTokens(tokens, path);
			if (!namespace.isEmpty() && !namespace.equals("minecraft")) addTokens(tokens, namespace);
		}
		if (sidebarLines != null) {
			for (String raw : sidebarLines) {
				if (raw == null) continue;
				Matcher m = WORLD_LINE.matcher(FORMATTING.matcher(raw).replaceAll(""));
				if (!m.find()) continue;
				String value = m.group(1).trim().toLowerCase(java.util.Locale.ROOT);
				if (value.isEmpty()) continue;
				known = true;
				addTokens(tokens, value);
			}
		}
		if (!known) return WorldInfo.UNKNOWN;
		boolean special = false;
		if (specialWorlds != null) {
			for (String s : specialWorlds) {
				if (s != null && tokens.contains(Singular.phrase(s))) special = true;
			}
		}
		return new WorldInfo(tokens, special, true);
	}

	/** The whole value (singular) plus each of its parts: "world_sandara" -> "world sandara", "world", "sandara". */
	private static void addTokens(Set<String> tokens, String value) {
		String whole = Singular.phrase(SPLIT.matcher(value).replaceAll(" "));
		if (!whole.isEmpty()) tokens.add(whole);
		for (String part : SPLIT.split(value)) {
			if (!part.isEmpty()) tokens.add(Singular.word(part));
		}
	}
}
