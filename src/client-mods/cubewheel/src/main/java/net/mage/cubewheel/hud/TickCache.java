package net.mage.cubewheel.hud;

import java.util.function.Supplier;

/**
 * The last value built, reused until the client tick changes: a HUD panel is drawn every frame (144 a second and
 * more) but what it shows changes at most once a tick (20 a second). A build that throws stores nothing, so the next
 * call tries again. Not thread-safe (the client thread only). Pure: no Minecraft/Fabric imports.
 */
public final class TickCache<T> {
	private boolean built;
	private long tick;
	private T value;

	/** The value built in {@code tick}, building it with {@code build} if this is the first call of that tick. */
	public T get(long tick, Supplier<T> build) {
		if (!built || tick != this.tick) {
			T v = build.get();
			value = v;
			this.tick = tick;
			built = true;
		}
		return value;
	}

	/** Forgets the value; the next {@link #get} builds again. */
	public void clear() {
		built = false;
		value = null;
	}
}
