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

	private SkillTextParser() {}

	/**
	 * Parses one chat or action bar line. {@code ownName} (null disables the two broadcast forms only) is the local
	 * player's name; other players' broadcasts return empty.
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

		String name = Pattern.quote(ownName);
		m = Pattern.compile("^" + name + " has reached a total Power Level of ([\\d,]+)$").matcher(line);
		if (m.matches()) {
			try {
				return Optional.of(new PowerLevel(Long.parseLong(m.group(1).replace(",", ""))));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		}
		m = Pattern.compile("^" + name + " has reached level ([\\d,]+) in ([A-Za-z]+)$").matcher(line);
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
