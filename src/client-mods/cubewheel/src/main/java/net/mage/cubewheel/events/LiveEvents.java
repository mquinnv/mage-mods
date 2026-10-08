package net.mage.cubewheel.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scheduled events that show themselves while they run, read from the boss bars on screen: the Mana Pond's
 * "Mana Pond (Spawn) 120/256" (captured 2026-10-07; it runs ~5m20s and the bar goes when it ends). Pure: no
 * Minecraft/Fabric imports.
 */
public final class LiveEvents {
	/** An event running now: {@code progress} of {@code max} from its bar. */
	public record Running(String name, long progress, long max) {}

	/** A schedule entry's name and the bar that says it is running (groups 1 and 2: progress and max). */
	private record Bar(String name, Pattern pattern) {}

	private static final List<Bar> BARS = List.of(
			new Bar("Mana Pond", Pattern.compile("(?i)^mana pond\\b.*?(\\d+)\\s*/\\s*(\\d+)")));

	private LiveEvents() {}

	/**
	 * The events running now, by name, from the boss bar names on screen (formatting codes are stripped; the first bar
	 * matching an event wins). Empty for none, or null.
	 */
	public static Map<String, Running> running(List<String> bars) {
		Map<String, Running> out = new LinkedHashMap<>();
		if (bars == null) return out;
		for (Bar bar : BARS) {
			for (String name : bars) {
				if (name == null) continue;
				Matcher m = bar.pattern().matcher(name.replaceAll("§.", "").trim());
				if (!m.find()) continue;
				try {
					out.put(bar.name(), new Running(bar.name(), Long.parseLong(m.group(1)), Long.parseLong(m.group(2))));
				} catch (NumberFormatException ignored) {
					continue; // absurdly long digits: not a count
				}
				break;
			}
		}
		return out;
	}

	/** The panel row's right side while an event runs: "NOW 120/256". */
	public static String nowText(Running r) {
		return "NOW " + r.progress() + "/" + r.max();
	}
}
