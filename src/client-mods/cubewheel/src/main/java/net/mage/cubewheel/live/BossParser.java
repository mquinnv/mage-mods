package net.mage.cubewheel.live;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ManaCube's boss spawn announcement, one chat message over several lines:
 * <pre>
 * ---------------------
 * A BOSS SPAWNED          (or "A MINI BOSS SPAWNED")
 *
 * Boss Mana Golem
 * Location: Wolfhaven Mines
 * ● Deal 50 damage for rewards…
 * </pre>
 * Pure: no Minecraft/Fabric imports.
 */
public final class BossParser {
	/** One spawn; {@code location} may be null when the message has no "Location:" line. */
	public record Spawn(String boss, String location, boolean mini) {}

	private static final Pattern HEADER = Pattern.compile("(?i)^A\\s+(MINI\\s+)?BOSS\\s+SPAWNED!?$");
	private static final Pattern BOSS = Pattern.compile("(?i)^Boss:?\\s+(.+)$");
	private static final Pattern LOCATION = Pattern.compile("(?i)^Location:\\s*(.+)$");

	private BossParser() {}

	public static Optional<Spawn> parse(String text) {
		if (text == null || text.isEmpty()) return Optional.empty();
		Boolean mini = null;
		String boss = null;
		String location = null;
		for (String raw : CowParser.strip(text).split("\\R")) {
			String line = raw.trim();
			if (line.isEmpty()) continue;
			if (mini == null) {
				Matcher h = HEADER.matcher(line);
				if (h.matches()) mini = h.group(1) != null;
				continue; // nothing counts before the header
			}
			Matcher b = BOSS.matcher(line);
			if (boss == null && b.matches()) {
				boss = b.group(1).trim();
				continue;
			}
			Matcher l = LOCATION.matcher(line);
			if (location == null && l.matches()) location = l.group(1).trim();
		}
		if (mini == null || boss == null || boss.isEmpty()) return Optional.empty();
		return Optional.of(new Spawn(boss, location, mini));
	}
}
