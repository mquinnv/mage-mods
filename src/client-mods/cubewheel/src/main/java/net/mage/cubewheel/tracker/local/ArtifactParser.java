package net.mage.cubewheel.tracker.local;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ManaCube's archaeology announces every artifact brushed out of suspicious sand in chat, for everyone on the
 * server (capture 2026-10-10): your own as "[Artifacts] You dug up a  Pharaoh Staff", another player's as
 * "[Artifacts] Zzenns dug up an  Obsidian Pyramid Statue". Only "You" (or the player's own name) is a find of
 * theirs. Pure: no Minecraft/Fabric imports.
 */
public final class ArtifactParser {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	/** Player chat on ManaCube: "§r" then "[Rank] Name: " (as {@code McmmoParser}). */
	private static final Pattern PLAYER_CHAT = Pattern.compile("^§r[^:]{0,64}:\\s");
	private static final Pattern DUG_UP = Pattern.compile(
			"^\\[Artifacts] (?<player>[A-Za-z0-9_]{1,32}) dug up an? (?<name>[^\\s].{0,80})$", Pattern.CASE_INSENSITIVE);

	private ArtifactParser() {}

	/**
	 * The artifact you dug up, as a chat message announces it (legacy § codes and extra spaces allowed): the line
	 * says "You", or names {@code self} (may be null). Empty for another player's find or anything else.
	 */
	public static Optional<String> parse(String raw, String self) {
		if (raw == null || raw.isEmpty() || raw.length() > 256 || PLAYER_CHAT.matcher(raw).find()) return Optional.empty();
		String line = WHITESPACE.matcher(FORMATTING.matcher(raw).replaceAll("")).replaceAll(" ").trim();
		Matcher m = DUG_UP.matcher(line);
		if (!m.matches()) return Optional.empty();
		String player = m.group("player");
		boolean mine = player.equalsIgnoreCase("you") || self != null && !self.isBlank() && player.equalsIgnoreCase(self.trim());
		if (!mine) return Optional.empty();
		String name = m.group("name").trim();
		return name.isEmpty() ? Optional.empty() : Optional.of(name);
	}
}
