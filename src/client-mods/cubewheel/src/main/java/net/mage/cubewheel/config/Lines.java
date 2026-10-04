package net.mage.cubewheel.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text-line codecs for the settings screen, which edits maps and the event schedule as plain lines. Pure: no
 * Minecraft/Fabric imports. Values are not validated here; {@link ConfigNormalizer} does that when the config is applied.
 */
public final class Lines {
	private static final String ARROW = " -> ";

	private Lines() {}

	/** One {@code key -> value} line per entry, in map order. */
	public static List<String> mapLines(Map<String, String> m) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> e : m.entrySet()) out.add(e.getKey() + ARROW + e.getValue());
		return out;
	}

	/**
	 * Parses {@code regex -> value} lines into an insertion-ordered map. Splits at the last arrow, since regexes may
	 * contain {@code ->}. Blank lines are skipped silently; malformed ones are skipped and described in {@code problems}.
	 * A repeated key keeps the later value.
	 */
	public static Map<String, String> parseMap(List<String> lines, List<String> problems) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty()) continue;
			int at = line.lastIndexOf(ARROW);
			String key = at < 0 ? "" : line.substring(0, at).trim();
			String value = at < 0 ? "" : line.substring(at + ARROW.length()).trim();
			if (key.isEmpty() || value.isEmpty()) {
				problems.add("\"" + line + "\" ignored: expected  regex -> value");
				continue;
			}
			out.put(key, value);
		}
		return out;
	}

	/**
	 * One {@code name | when [| timezone] [| off]} line per event. The timezone is written only when set, {@code off}
	 * only when the event is disabled.
	 */
	public static List<String> eventLines(List<CubeWheelConfig.EventDef> defs) {
		List<String> out = new ArrayList<>();
		for (CubeWheelConfig.EventDef d : defs) {
			StringBuilder sb = new StringBuilder().append(d.name).append(" | ").append(d.when);
			if (d.timezone != null && !d.timezone.isBlank()) sb.append(" | ").append(d.timezone);
			if (!d.enabled) sb.append(" | off");
			out.add(sb.toString());
		}
		return out;
	}

	/**
	 * Parses {@code name | when [| timezone] [| off]} lines. Blank lines are skipped silently; a line missing its name or
	 * {@code when}, or naming two timezones, is skipped and described in {@code problems}. Empty extra parts are ignored.
	 */
	public static List<CubeWheelConfig.EventDef> parseEvents(List<String> lines, List<String> problems) {
		List<CubeWheelConfig.EventDef> out = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty()) continue;
			String[] parts = line.split("\\|", -1);
			String name = parts[0].trim();
			String when = parts.length > 1 ? parts[1].trim() : "";
			if (name.isEmpty() || when.isEmpty()) {
				problems.add("\"" + line + "\" ignored: expected  name | when");
				continue;
			}
			CubeWheelConfig.EventDef def = new CubeWheelConfig.EventDef(name, when);
			boolean ok = true;
			for (int i = 2; i < parts.length && ok; i++) {
				String part = parts[i].trim();
				if (part.isEmpty()) continue;
				if (part.equalsIgnoreCase("off")) def.enabled = false;
				else if (def.timezone == null) def.timezone = part;
				else {
					problems.add("\"" + line + "\" ignored: more than one timezone");
					ok = false;
				}
			}
			if (ok) out.add(def);
		}
		return out;
	}
}
