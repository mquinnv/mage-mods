package com.mage.cubewheel.sidebar;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.capture.CaptureLog;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.tracker.TrackerStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

/**
 * Minecraft adapter for the live sidebar: every {@link #INTERVAL_TICKS} ticks it reads the sidebar
 * lines exactly as the vanilla HUD draws them (same objective choice, filter, order and 15-line cap,
 * each line = the entry name formatted with its team's prefix/suffix). Only when they changed does it
 * capture them, parse "Key: value" numbers and push changed values into linked trackables. Read-only:
 * never sends anything.
 */
public final class SidebarWatcher {
	/** 10 ticks = twice a second; the read is ~15 short strings. */
	private static final int INTERVAL_TICKS = 10;
	private static final long SAVE_INTERVAL_MS = 10_000;
	/** Hud.SCORE_DISPLAY_ORDER in 26.2: score descending, then owner name case-insensitively. */
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
			.reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final int MAX_LINES = 15;

	/** The sidebar as drawn: title and lines (plain text); an absent sidebar has a null title. */
	public record Snapshot(String title, List<String> lines) {
		static final Snapshot ABSENT = new Snapshot(null, List.of());
	}

	private final SidebarLinker.SaveThrottle saveThrottle = new SidebarLinker.SaveThrottle(SAVE_INTERVAL_MS);
	private int ticks;
	private Snapshot last = Snapshot.ABSENT;
	private Map<String, Double> lastValues = Map.of();
	private boolean failureLogged;

	/** Latest parsed sidebar values ("Skills" -> 1851.0); empty when no sidebar is shown or off ManaCube. */
	public Map<String, Double> values() {
		return lastValues;
	}

	/**
	 * The sidebar lines as last read (plain text, at most every {@link #INTERVAL_TICKS} ticks); empty when
	 * no sidebar is shown or off ManaCube. Local counting reads its "World: X" line from here instead of
	 * reading the scoreboard a second time.
	 */
	public List<String> lines() {
		return last.lines();
	}

	/**
	 * Re-applies the current sidebar values after a menu scan changed the store, so an entry the menu
	 * just (re)wrote, e.g. a new prestige objective, follows the live value straight away. Never throws.
	 */
	public void reapply(long now) {
		try {
			TrackerStore store = CubeWheelClient.tracker();
			if (store == null || lastValues.isEmpty()) return;
			if (SidebarLinker.apply(lastValues, CubeWheelClient.config().current().tracker.sidebarLinks, store, now) > 0) {
				saveThrottle.markDirty();
			}
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] sidebar re-apply failed", e);
		}
	}

	/**
	 * Saves sidebar-driven tracker changes that the 10 s throttle has not written yet (disconnect,
	 * CLIENT_STOPPING), so they are not lost on quit. Never throws.
	 */
	public void flush() {
		try {
			TrackerStore store = CubeWheelClient.tracker();
			if (store != null && saveThrottle.consumeDirty()) store.save();
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] sidebar flush failed", e);
		}
	}

	/** END_CLIENT_TICK. */
	public void tick(Minecraft mc) {
		try {
			long now = System.currentTimeMillis();
			TrackerStore store = CubeWheelClient.tracker();
			if (store != null && saveThrottle.shouldSave(now)) store.save();
			if (++ticks < INTERVAL_TICKS) return;
			ticks = 0;
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (mc.level == null || mc.player == null || !ServerGate.active(cfg)) {
				// Left the server: forget, so values seen on the next join count as changes again.
				last = Snapshot.ABSENT;
				lastValues = Map.of();
				return;
			}
			Snapshot snap = read(mc);
			if (snap.equals(last)) return;
			last = snap;
			CaptureLog capture = CubeWheelClient.capture();
			if (capture != null && capture.enabled()) capture.sidebar(snap.title(), snap.lines(), now);
			Map<String, Double> values = SidebarParser.parse(snap.lines());
			Map<String, Double> changed = SidebarLinker.changed(lastValues, values);
			lastValues = values;
			if (changed.isEmpty() || store == null) return;
			if (SidebarLinker.apply(changed, cfg.tracker.sidebarLinks, store, now) > 0) saveThrottle.markDirty();
		} catch (RuntimeException e) {
			if (!failureLogged) CubeWheelClient.LOG.error("[cubewheel] sidebar watcher failed", e);
			failureLogged = true;
		}
	}

	/** Mirrors Hud.extractScoreboardSidebar/displayScoreboardSidebar (26.2) without drawing. */
	static Snapshot read(Minecraft mc) {
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = null;
		PlayerTeam own = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (own != null) {
			Optional<TeamColor> color = own.getColor();
			if (color.isPresent() && color.get().displaySlot() != null) {
				objective = scoreboard.getDisplayObjective(color.get().displaySlot());
			}
		}
		if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return Snapshot.ABSENT;
		// Only the left-hand text is read: the right-hand score column (blank on ManaCube) is left out so a
		// visible score ("15") can never glue onto a value.
		List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
				.filter(e -> !e.isHidden())
				.sorted(ORDER)
				.limit(MAX_LINES)
				.toList();
		List<String> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry e : entries) {
			PlayerTeam team = scoreboard.getPlayersTeam(e.owner());
			lines.add(PlayerTeam.formatNameForTeam(team, e.ownerName()).getString());
		}
		return new Snapshot(objective.getDisplayName().getString(), List.copyOf(lines));
	}
}
