package net.mage.cubewheel.cooldown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ManaCube's "still cooling down" action-bar notice, shown when an item is used before its cooldown is over:
 * "PHOENIX STAFF CD: ⬛⬛⬛⬛ (7s)", possibly among other " | "-separated parts ("+5 Mana | SAMURAI KATANA CD: …").
 * Pure: no Minecraft/Fabric imports.
 */
public final class CooldownBar {
	/** One notice: the item's name as the server writes it and the whole seconds left. */
	public record Notice(String item, long seconds) {}

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern NOTICE = Pattern.compile("^(.+?)\\s+CD:\\s*[^()]*\\((\\d+)s\\)\\s*$");

	private CooldownBar() {}

	public static List<Notice> parse(String actionBar) {
		if (actionBar == null || actionBar.isEmpty()) return List.of();
		List<Notice> out = new ArrayList<>();
		for (String part : FORMATTING.matcher(actionBar).replaceAll("").split("\\|")) {
			Matcher m = NOTICE.matcher(part.trim());
			if (m.matches()) out.add(new Notice(m.group(1).trim(), Long.parseLong(m.group(2))));
		}
		return out;
	}
}
