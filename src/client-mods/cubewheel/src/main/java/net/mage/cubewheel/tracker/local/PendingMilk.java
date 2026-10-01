package net.mage.cubewheel.tracker.local;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Empty buckets the local player used on a milkable mob, waiting for the server to keep the swap. The client
 * fills the bucket at once; a refused milking (a claim, a spawn area) is undone by the server within a moment.
 * So a use counts once {@link #WAIT_TICKS} have passed and the player still has fewer empty buckets than just
 * before it. Bounded to {@link #CAPACITY} (oldest dropped). Pure: no Minecraft/Fabric imports.
 */
public final class PendingMilk {
	public static final int WAIT_TICKS = 20;
	public static final int CAPACITY = 16;

	/** A settled use: the mob it was on and whether the milking stuck. */
	public record Outcome(String typeId, String name, boolean confirmed) {}

	private record Entry(long deadline, int bucketsBefore, String typeId, String name) {}

	private final Deque<Entry> pending = new ArrayDeque<>();

	/** A bucket used on a milkable mob at {@code tick}, with {@code bucketsBefore} empty buckets held just before. */
	public void use(long tick, int bucketsBefore, String typeId, String name) {
		pending.addLast(new Entry(tick + WAIT_TICKS, bucketsBefore, typeId, name));
		while (pending.size() > CAPACITY) pending.removeFirst();
	}

	/** Settles every use whose wait is over against the empty buckets held now; in use order. */
	public List<Outcome> tick(long tick, int bucketsNow) {
		if (pending.isEmpty()) return List.of();
		List<Outcome> out = new ArrayList<>();
		while (!pending.isEmpty() && tick >= pending.peekFirst().deadline()) {
			Entry e = pending.removeFirst();
			out.add(new Outcome(e.typeId(), e.name(), bucketsNow < e.bucketsBefore()));
		}
		return out;
	}

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	public void clear() {
		pending.clear();
	}
}
