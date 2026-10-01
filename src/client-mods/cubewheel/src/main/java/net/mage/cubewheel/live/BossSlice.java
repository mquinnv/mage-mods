package net.mage.cubewheel.live;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.mage.cubewheel.wheel.SliceViews;

/**
 * The latest boss spawn and the "Boss event" slice built from it: "Mana Golem · 2m" (gold) that sends the
 * warp mapped from its location, or an inert "No boss event". Nothing is ever sent automatically. Pure: no
 * Minecraft/Fabric imports.
 */
public final class BossSlice {
	public static final String NONE = "No boss event";

	private BossParser.Spawn latest;
	private long at;

	public void spawned(BossParser.Spawn spawn, long now) {
		if (spawn == null) return;
		latest = spawn;
		at = now;
	}

	public BossParser.Spawn latest() {
		return latest;
	}

	/** The command for {@code location}: the first {@code warps} regex found in it; null if none. */
	public static String warpFor(String location, Map<String, String> warps) {
		if (location == null || warps == null) return null;
		for (Map.Entry<String, String> e : warps.entrySet()) {
			try {
				if (e.getKey() != null && Pattern.compile(e.getKey()).matcher(location).find()) return e.getValue();
			} catch (PatternSyntaxException ex) {
				// ConfigStore drops invalid ones; skip defensively
			}
		}
		return null;
	}

	/** The slice right now: the spawn while it is at most {@code maxAgeMs} old, else the placeholder. */
	public SliceViews.View view(Map<String, String> warps, long maxAgeMs, long now) {
		if (latest == null || now - at > maxAgeMs || now < at - 60_000) {
			return new SliceViews.View(NONE, SliceViews.DIM, null, true);
		}
		String label = latest.boss() + " · " + LiveFormat.compact(now - at);
		String cmd = warpFor(latest.location(), warps);
		if (cmd == null) return new SliceViews.View(label + " (no warp)", SliceViews.GOLD, null, true);
		return new SliceViews.View(label, SliceViews.GOLD, cmd, false);
	}
}
