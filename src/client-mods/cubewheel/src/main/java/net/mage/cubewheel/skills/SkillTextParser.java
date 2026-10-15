package net.mage.cubewheel.skills;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the chat lines mcMMO sends on ManaCube Survival that carry a skill level:
 * <pre>
 * §a✚ §7| §aMining is now level §a§l245                  (own level-up, also on the action bar)
 * Qualan has reached a total Power Level of 1,900          (broadcast naming the player)
 * Qualan has reached level 200 in WOODCUTTING              (broadcast; "*Buttle ... in Mining" is another player)
 * </pre>
 * The ability-unlock line ("mcMMO ➡ You have the unlocked the Rupture ability!") and player chat such as
 * "Qualan: Mining is now level 5" never match. Pure: no Minecraft/Fabric imports.
 */
public final class SkillTextParser {
	/** What a recognised line says. */
	public sealed interface Message permits LevelUp, PowerLevel, SkillMilestone {}

	/** The player's own skill went up to {@code level}. */
	public record LevelUp(Skill skill, int level) implements Message {}

	/** The player's own total power level, as broadcast. */
	public record PowerLevel(long power) implements Message {}

	/** The player's own broadcast milestone: {@code skill} reached {@code level}. */
	public record SkillMilestone(Skill skill, int level) implements Message {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** Leading decoration ("✚ |") is non-word; a letter before the phrase (chat "Name: ...") fails the match. */
	private static final Pattern LEVEL_UP = Pattern.compile("^\\W*\\|?\\s*([A-Za-z]+) is now level ([\\d,]+)\\s*$");

	/** The two own-name patterns for one name; rebuilt only when the name changes. */
	private record OwnPatterns(String name, Pattern power, Pattern milestone) {}

	private static volatile OwnPatterns own;

	private SkillTextParser() {}

	private static OwnPatterns patternsFor(String ownName) {
		OwnPatterns c = own;
		if (c != null && c.name().equals(ownName)) return c;
		String q = Pattern.quote(ownName);
		c = new OwnPatterns(ownName,
				Pattern.compile("^" + q + " has reached a total Power Level of ([\\d,]+)$", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^" + q + " has reached level ([\\d,]+) in ([A-Za-z]+)$", Pattern.CASE_INSENSITIVE));
		own = c;
		return c;
	}

	/**
	 * Parses one chat or action bar line. {@code ownName} (null disables the two broadcast forms only) is the local
	 * player's name (matched case-insensitively); other players' broadcasts return empty.
	 */
	public static Optional<Message> parse(String text, String ownName) {
		if (text == null || text.isEmpty() || text.length() > 256) return Optional.empty();
		String line = FORMATTING.matcher(text).replaceAll("").trim();

		Matcher m = LEVEL_UP.matcher(line);
		if (m.matches()) {
			Optional<Skill> skill = Skill.byName(m.group(1));
			Optional<Integer> level = number(m.group(2));
			if (skill.isPresent() && level.isPresent()) return Optional.of(new LevelUp(skill.get(), level.get()));
			return Optional.empty();
		}
		if (ownName == null || ownName.isEmpty()) return Optional.empty();

		OwnPatterns pats = patternsFor(ownName);
		m = pats.power().matcher(line);
		if (m.matches()) {
			try {
				return Optional.of(new PowerLevel(Long.parseLong(m.group(1).replace(",", ""))));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		}
		m = pats.milestone().matcher(line);
		if (m.matches()) {
			Optional<Skill> skill = Skill.byName(m.group(2));
			Optional<Integer> level = number(m.group(1));
			if (skill.isPresent() && level.isPresent()) return Optional.of(new SkillMilestone(skill.get(), level.get()));
		}
		return Optional.empty();
	}

	private static Optional<Integer> number(String s) {
		try {
			return Optional.of(Integer.parseInt(s.replace(",", "")));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
