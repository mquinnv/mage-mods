package net.mage.cubewheel.live;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.mage.cubewheel.wheel.SliceViews;

/**
 * The latest boss spawn and the "Boss event" slice built from it: "Mana Golem · 2m" (gold) that sends the
 * warp mapped from its location, or an inert "No boss". Nothing is ever sent automatically. Pure: no
 * Minecraft/Fabric imports.
 */
public final class BossSlice {
	public static final String NONE = "No boss";

	private BossParser.Spawn latest;
	private long at;
	/** The last sign the boss is still up: its spawn, or a player it killed ("X was slain by Mana Golem [axes]"). */
	private long aliveAt;

	/** Set when ManaCube announces the latest boss's death: the slice then shows the placeholder at once. */
	private boolean dead;
	/** "BOSSES » 9 teamed up to take down a MANA GOLEM BOSS" (also "… a LAVA BEAST", no "BOSS"). */
	private static final java.util.regex.Pattern DEFEATED = java.util.regex.Pattern.compile(
			"(?i)teamed up to (?:take down|defeat|kill) (?:a |an |the )?(.+?)(?:\\s+BOSS)?\\s*!?\\s*$");
	private static final java.util.regex.Pattern SLAIN = java.util.regex.Pattern.compile("(?i)\\bwas slain by (.+)$");

	public void spawned(BossParser.Spawn spawn, long now) {
		if (spawn == null) return;
		latest = spawn;
		at = now;
		aliveAt = now;
		dead = false;
	}

	/**
	 * A chat line: a player slain by the latest boss ("Mana Golem", "Mana Golem Boss [axes]") shows it is still
	 * alive, so the slice stays up a while longer. Returns true if it was such a line.
	 */
	public boolean onChat(String text, long now) {
		if (latest == null || text == null) return false;
		String clean = text.replaceAll("§.", "").trim();
		java.util.regex.Matcher d = DEFEATED.matcher(clean);
		if (d.find()) {
			if (!d.group(1).trim().equalsIgnoreCase(latest.boss().trim())) return false;
			dead = true;
			return true;
		}
		java.util.regex.Matcher m = SLAIN.matcher(clean);
		if (!m.find() || !m.group(1).toLowerCase(java.util.Locale.ROOT).contains(latest.boss().toLowerCase(java.util.Locale.ROOT))) {
			return false;
		}
		aliveAt = Math.max(aliveAt, now);
		return true;
	}

	public BossParser.Spawn latest() {
		return latest;
	}

	/** What a client restart must not lose ({@code config/cubewheel-boss.json}); public fields for Gson. */
	static final class Saved {
		String boss, location;
		boolean mini, dead;
		long at, aliveAt;
	}

	/** Writes the latest spawn; false (and logged) on IO errors. Nothing to write before the first spawn. */
	public boolean save(java.nio.file.Path file) {
		if (latest == null) return true;
		Saved s = new Saved();
		s.boss = latest.boss();
		s.location = latest.location();
		s.mini = latest.mini();
		s.dead = dead;
		s.at = at;
		s.aliveAt = aliveAt;
		try {
			if (file.getParent() != null) java.nio.file.Files.createDirectories(file.getParent());
			java.nio.file.Files.writeString(file, new com.google.gson.Gson().toJson(s), java.nio.charset.StandardCharsets.UTF_8);
			return true;
		} catch (java.io.IOException | RuntimeException e) {
			org.slf4j.LoggerFactory.getLogger("cubewheel").warn("[cubewheel] could not save boss state to {}: {}", file, e.toString());
			return false;
		}
	}

	/** Restores the latest spawn written by {@link #save}; a missing or corrupt file leaves no boss. */
	public void load(java.nio.file.Path file) {
		if (!java.nio.file.Files.exists(file)) return;
		try {
			Saved s = new com.google.gson.Gson().fromJson(
					java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8), Saved.class);
			if (s == null || s.boss == null) return;
			latest = new BossParser.Spawn(s.boss, s.location, s.mini);
			at = s.at;
			aliveAt = s.aliveAt;
			dead = s.dead;
		} catch (java.io.IOException | RuntimeException e) {
			org.slf4j.LoggerFactory.getLogger("cubewheel").warn("[cubewheel] could not read boss state from {}: {}", file, e.toString());
		}
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

	/**
	 * The command for a spawn: the rules are tried against "boss · location", so a rule naming the boss (its own
	 * warp, e.g. "cursed witch") wins over its world's when it comes first.
	 */
	public static String warpFor(BossParser.Spawn spawn, Map<String, String> warps) {
		if (spawn == null) return null;
		return warpFor((spawn.boss() == null ? "" : spawn.boss()) + " · " + (spawn.location() == null ? "" : spawn.location()), warps);
	}

	/**
	 * The slice right now: the spawn until {@code maxAgeMs} after the last sign of life (its spawn or a kill it made),
	 * else the placeholder. ManaCube announces no deaths, so this is a best guess; bosses usually fall in minutes.
	 */
	public SliceViews.View view(Map<String, String> warps, long maxAgeMs, long now) {
		if (latest == null || dead || now - aliveAt > maxAgeMs || now < at - 60_000) {
			return new SliceViews.View(NONE, SliceViews.DIM, null, true);
		}
		String label = latest.boss() + " · " + LiveFormat.compact(now - at);
		String cmd = warpFor(latest, warps);
		if (cmd == null) return new SliceViews.View(label + " (no warp)", SliceViews.GOLD, null, true);
		return new SliceViews.View(label, SliceViews.GOLD, cmd, false);
	}
}
