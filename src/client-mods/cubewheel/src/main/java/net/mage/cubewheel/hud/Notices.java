package net.mage.cubewheel.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One-off notices for the popup under the crosshair, raised once per change of state (edge-triggered): the inventory
 * filling up, the worn set's bonus lost or regained, a personal vault nearly full or full. Each is fed the current
 * state (by {@code NoticeWatcher}, every tick) and compares it with what it saw last; the first sight of each is only
 * the baseline. After {@link #reset} (joining, another world) everything is a baseline again, and for
 * {@link #SETTLE_MS} what arrives only sets the baseline, so an inventory or armor that loads in over a few ticks
 * raises nothing. Not thread-safe: fed from the client thread. Pure: no Minecraft/Fabric imports.
 */
public final class Notices {
	/** Where notices go (the popup). */
	public interface Sink {
		void notice(String text, int color, long now);
	}

	/** After a reset, changes this long are taken silently. */
	public static final long SETTLE_MS = 3_000;
	/**
	 * A set bonus change must hold this long before it is announced: a warp re-sends the inventory a slot at a time,
	 * so for a few ticks the set reads 3/4 and "lost" then "active" popped up on every warp (Michael 2026-10-08).
	 */
	public static final long ARMOR_DEBOUNCE_MS = 2_000;
	/** A vault with this many free slots or fewer is nearly full. */
	public static final int VAULT_LOW = 3;
	public static final int RED = 0xFFFF5555;

	private final Sink sink;
	private long quietUntil = Long.MIN_VALUE;

	private boolean invKnown;
	private boolean invFull;

	private boolean armorKnown;
	/** The set whose bonus is active, or null. */
	private String metSet;
	/** A change from {@link #metSet} seen but not yet held for {@link #ARMOR_DEBOUNCE_MS}: what to, and since when. */
	private String pendingMet;
	private boolean pending;
	private long pendingSince;

	/** Free slots per vault page as last seen. */
	private final Map<Integer, Integer> vaultFree = new HashMap<>();

	public Notices(Sink sink) {
		this.sink = sink;
	}

	/** Forget everything seen: the next state of each is a baseline, and so is all that arrives until the settle ends. */
	public void reset(long now) {
		quietUntil = now + SETTLE_MS;
		invKnown = false;
		armorKnown = false;
		metSet = null;
		pending = false;
		vaultFree.clear();
	}

	/** {@code used} of the main inventory's {@code total} slots hold something: "Inventory full" when the last fills. */
	public void inventory(int used, int total, long now) {
		boolean full = used >= total;
		boolean fire = invKnown && !quiet(now) && full && !invFull;
		invKnown = true;
		invFull = full;
		if (fire) sink.notice("Inventory full", RED, now);
	}

	/**
	 * The worn set ({@code set}, as {@code ArmorSet} names it) has {@code matching} pieces on and its bonus needs
	 * {@code required} (0 = no bonus stated). "Phoenix set bonus lost (3/4)" when an active bonus goes ("… lost" alone
	 * when another set is shown now), "Phoenix set bonus active" when one comes on. A change counts only once it has
	 * held for {@link #ARMOR_DEBOUNCE_MS}; a shorter blip (a warp's inventory re-send) is nothing.
	 */
	public void armor(String set, int matching, int required, long now) {
		String met = required > 0 && matching >= required ? set : null;
		if (!armorKnown || quiet(now)) {
			// The baseline: taken as is, no announcement.
			armorKnown = true;
			metSet = met;
			pending = false;
			return;
		}
		if (Objects.equals(met, metSet)) {
			pending = false; // back to what was announced before the change held
			return;
		}
		if (!pending || !Objects.equals(met, pendingMet)) {
			pending = true;
			pendingMet = met;
			pendingSince = now;
		}
		if (now - pendingSince < ARMOR_DEBOUNCE_MS) return;
		pending = false;
		String was = metSet;
		metSet = met;
		if (was != null) {
			boolean same = was.equals(set) && required > 0;
			sink.notice(was + " set bonus lost" + (same ? " (" + matching + "/4)" : ""), Panel.YELLOW, now);
		}
		if (met != null) sink.notice(met + " set bonus active", Panel.GREEN, now);
	}

	/**
	 * Personal vault {@code page} has {@code free} empty slots as last seen: "PV 2: 3 slots left" when it drops to
	 * {@link #VAULT_LOW} or fewer, "PV 2 full" when it fills; each again only after it has been back above.
	 */
	public void vault(int page, int free, long now) {
		Integer prev = vaultFree.get(page);
		if (prev != null && prev == free) return;
		vaultFree.put(page, free);
		if (prev == null || quiet(now)) return;
		if (free <= 0 && prev > 0) {
			sink.notice("PV " + page + " full", RED, now);
		} else if (free > 0 && free <= VAULT_LOW && prev > VAULT_LOW) {
			sink.notice("PV " + page + ": " + free + (free == 1 ? " slot left" : " slots left"), Panel.YELLOW, now);
		}
	}

	private boolean quiet(long now) {
		return now < quietUntil;
	}
}
