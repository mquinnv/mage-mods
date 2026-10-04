package net.mage.cubewheel.charms;

import java.util.function.Supplier;

/**
 * One parsed value per inventory slot, kept while the slot holds the same item object: the server sends a new stack
 * when an item changes, so its name and lore are read again only then, not every frame (as the Status panel does for
 * armor). A parse that throws stores nothing. Not thread-safe (the client thread only). Pure: no Minecraft/Fabric
 * imports.
 */
final class SlotCache<T> {
	private final Object[] keys;
	private final Object[] values;

	SlotCache(int slots) {
		keys = new Object[slots];
		values = new Object[slots];
	}

	/** The value parsed for {@code key} (compared by identity) in {@code slot}, parsing it with {@code parse} if new. */
	@SuppressWarnings("unchecked")
	T get(int slot, Object key, Supplier<T> parse) {
		if (key == null || keys[slot] != key) {
			T v = parse.get();
			values[slot] = v;
			keys[slot] = key;
			return v;
		}
		return (T) values[slot];
	}
}
