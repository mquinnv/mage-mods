package com.mage.cubewheel.tracker;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Maps a container title to a tracker source id. Pure: no Minecraft/Fabric imports. */
public final class TrackerSources {
	private TrackerSources() {}

	/** First source whose regex is found in the title; invalid regexes are skipped. */
	public static Optional<String> match(String title, Map<String, String> sources) {
		if (title == null || sources == null) return Optional.empty();
		for (Map.Entry<String, String> e : sources.entrySet()) {
			if (e.getKey() == null || e.getValue() == null) continue;
			try {
				if (Pattern.compile(e.getValue()).matcher(title).find()) return Optional.of(e.getKey());
			} catch (PatternSyntaxException ignored) {
				// skip invalid regex
			}
		}
		return Optional.empty();
	}
}
