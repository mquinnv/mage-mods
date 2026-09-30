package net.mage.cubewheel.tracker.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * Stacked mobs ("5x Tiger"): a stacker plugin kills one mob of a stack by lowering the count in the
 * entity's name, without a death event. Watches the names of entities the local player hit (and of their
 * passengers, whose name tags some plugins use) and reports every name change; a lower count under the
 * same bare name is that many kills, but only when both names show a count ("5x Tiger" -> "Tiger" is a
 * name flip, not four kills), and at most {@link #MAX_UNHIT_DROP} per change unless the local player hit
 * the stack again since the previous change. Attribution is the caller's job. Bounded to {@link #MAX}
 * entities. Pure: no Minecraft/Fabric imports.
 */
public final class StackWatch {
	public static final int MAX = 256;
	/** Largest drop counted for one name change without a local hit since the previous change. */
	public static final int MAX_UNHIT_DROP = 2;

	/** {@code entityId}'s name changed; {@code rootId} is the hit entity it belongs to (itself or its vehicle). */
	public record Change(int entityId, int rootId, String oldName, String newName, int killed) {}

	/** {@code hit}: the local player hit this stack since its name last changed. */
	private record Watched(int rootId, String name, boolean hit) {}

	private final Map<Integer, Watched> watched = new LinkedHashMap<>();

	/** The local player hit {@code entityId}: starts (or refreshes) watching it, whose name is now {@code rawName}. */
	public void watch(int entityId, int rootId, String rawName) {
		watched.remove(entityId);
		watched.put(entityId, new Watched(rootId, rawName, true));
		while (watched.size() > MAX) {
			Iterator<Integer> it = watched.keySet().iterator();
			it.next();
			it.remove();
		}
	}

	public boolean watching(int entityId) {
		return watched.containsKey(entityId);
	}

	/** The watched entity now shows {@code rawName}: the change, or empty if unwatched or unchanged. */
	public Optional<Change> onName(int entityId, String rawName) {
		Watched w = watched.get(entityId);
		if (w == null || Objects.equals(w.name(), rawName)) return Optional.empty();
		watched.put(entityId, new Watched(w.rootId(), rawName, false));
		int killed = killed(w.name(), rawName);
		if (!w.hit()) killed = Math.min(killed, MAX_UNHIT_DROP);
		return Optional.of(new Change(entityId, w.rootId(), w.name(), rawName, killed));
	}

	/** Mobs killed between two names that both show a count: the drop under the same bare name, else 0. */
	static int killed(String before, String after) {
		Optional<StackName.Parsed> a = StackName.parseCounted(before);
		Optional<StackName.Parsed> b = StackName.parseCounted(after);
		if (a.isEmpty() || b.isEmpty()) return 0;
		if (!a.get().name().toLowerCase(Locale.ROOT).equals(b.get().name().toLowerCase(Locale.ROOT))) return 0;
		return Math.max(0, a.get().count() - b.get().count());
	}

	/** Stops watching {@code id} and everything watched on its behalf. */
	public void forget(int id) {
		watched.remove(id);
		watched.values().removeIf(w -> w.rootId() == id);
	}

	/** Keeps only entries whose root is still of interest (e.g. still has a recent local hit). */
	public void retainRoots(IntPredicate keep) {
		watched.values().removeIf(w -> !keep.test(w.rootId()));
	}

	public int size() {
		return watched.size();
	}

	public void clear() {
		watched.clear();
	}
}
