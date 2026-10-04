package net.mage.cubewheel.hud;

import net.mage.cubewheel.config.CubeWheelConfig;
import java.util.Optional;

/**
 * The popup under the crosshair when local counting adds progress to a tracked entry: "+1 Mana Wolves  ~10/74" in
 * cyan, or "✓ Mana Wolves  ~74/74" in green when the increment completes it. Further increments to the same entry
 * while it is shown merge into it ("+7 …") and restart its time; one to another entry replaces it, unless it comes
 * with the same signal ({@link #SAME_SIGNAL_MS}). It stays fully visible for {@code toast.seconds} after the last
 * increment, then fades over {@link #FADE_MS}. Thread-safe, as the counter and the HUD may call from different
 * threads. Pure: no Minecraft/Fabric imports.
 */
public final class ProgressToast {
	/** How long the fade after the visible time takes. */
	public static final long FADE_MS = 300;
	/**
	 * Another entry this soon after the last increment does not replace the toast: one block or kill counting for a
	 * job and a quest at once would otherwise flip it on every signal and it would never add up.
	 */
	static final long SAME_SIGNAL_MS = 50;

	/** What to draw: {@code alpha} runs from 1 (fully visible) down towards 0 during the fade. */
	public record Shown(String text, int color, double alpha) {}

	private String id;
	private String name;
	private long units;
	private String count;
	private boolean done;
	/** When the last increment arrived; the toast ends {@code seconds} + {@link #FADE_MS} after it. */
	private long lastAt;

	/**
	 * {@code units} objective units were counted for entry {@code id} (a {@code TrackerStore} counter key), shown as
	 * {@code name}; {@code count} is its count as the panels show it ("~10/74", "" if none) and {@code done} whether it
	 * is now at its target. Ignored while the popup is off.
	 */
	public synchronized void onIncrement(String id, String name, long units, String count, boolean done, long now,
			CubeWheelConfig.Toast cfg) {
		if (id == null || units <= 0 || cfg == null || !cfg.enabled) return;
		boolean shown = this.id != null && visible(now, cfg);
		if (shown && !id.equals(this.id) && now - lastAt < SAME_SIGNAL_MS) return;
		if (shown && id.equals(this.id)) {
			this.units += units;
		} else {
			this.id = id;
			this.units = units;
		}
		this.name = name == null ? "" : name;
		this.count = count == null ? "" : count;
		this.done = done;
		this.lastAt = now;
	}

	/**
	 * Takes back {@code units} of entry {@code id} (the server rejected a break, a chat catch replaces the bobber's):
	 * the shown total drops, without restarting its time; all of it taken back hides the toast. Other entries are
	 * ignored.
	 */
	public synchronized void onReverse(String id, long units, String count, boolean done) {
		if (id == null || !id.equals(this.id) || units <= 0) return;
		this.units -= units;
		if (this.units <= 0) {
			this.id = null;
			return;
		}
		this.count = count == null ? "" : count;
		this.done = done;
	}

	/** What to draw at {@code now}, or empty when there is nothing (none yet, gone, or the popup is off). */
	public synchronized Optional<Shown> current(long now, CubeWheelConfig.Toast cfg) {
		if (id == null || cfg == null || !cfg.enabled || !visible(now, cfg)) return Optional.empty();
		long age = now - lastAt;
		long visibleMs = visibleMs(cfg);
		double alpha = age < visibleMs ? 1.0 : 1.0 - (age - visibleMs) / (double) FADE_MS;
		String head = done ? "✓ " + name : "+" + units + " " + name;
		String text = count.isEmpty() ? head : head + "  " + count;
		return Optional.of(new Shown(text, done ? Panel.GREEN : Panel.CYAN, alpha));
	}

	private boolean visible(long now, CubeWheelConfig.Toast cfg) {
		long age = now - lastAt;
		return age >= 0 && age < visibleMs(cfg) + FADE_MS;
	}

	private static long visibleMs(CubeWheelConfig.Toast cfg) {
		return Math.round(cfg.seconds * 1000);
	}
}
