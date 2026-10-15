package net.mage.cubewheel.skills;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the boss bar mcMMO shows while a skill gains XP, as ManaCube Survival names it (14,461 captured bars, plain
 * text with the {@code §} codes already stripped):
 * <pre>
 * MINING » ʟᴠʟ. 243 [27,596 / 62,250 xp]
 * SWORDS » ʟᴠʟ.242 [16,101 / 62,000 xp]            (SWORDS omits the space after "ʟᴠʟ.")
 * </pre>
 * {@code ʟᴠʟ} is the small caps U+029F U+1D20 U+029F. Other bars ("MANA GOLEM [SWORDS] 2⺛ - Damage Dealt: 12 for
 * loot", "Anomaly: Mining Fatigue I - 0:42", "Mana Pond (Spawn) 120/256") never match because the pattern is
 * anchored and needs the {@code » lvl} and {@code xp} parts. Pure: no Minecraft/Fabric imports.
 */
public final class SkillBarParser {
	/** One skill bar: the level and the XP into it, with the XP the bar says that level needs. */
	public record Reading(Skill skill, int level, long xp, long needed) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern BAR = Pattern.compile(
			"^([A-Za-z ]+?)\\s*»\\s*lvl\\.?\\s*([\\d,]+)\\s*\\[\\s*([\\d,]+)\\s*/\\s*([\\d,]+)\\s*xp\\s*\\]$",
			Pattern.CASE_INSENSITIVE);

	private SkillBarParser() {}

	/** Parses one boss bar name; empty if it is not a skill bar of a known skill. */
	public static Optional<Reading> parse(String bossBarName) {
		if (bossBarName == null || bossBarName.isEmpty() || bossBarName.length() > 128) return Optional.empty();
		String line = FORMATTING.matcher(bossBarName).replaceAll("").replace("ʟᴠʟ", "lvl").trim();
		Matcher m = BAR.matcher(line);
		if (!m.matches()) return Optional.empty();
		Optional<Skill> skill = Skill.byName(m.group(1));
		if (skill.isEmpty()) return Optional.empty();
		try {
			return Optional.of(new Reading(skill.get(), Integer.parseInt(number(m.group(2))),
					Long.parseLong(number(m.group(3))), Long.parseLong(number(m.group(4)))));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static String number(String s) {
		return s.replace(",", "");
	}
}
