package com.mage.cubewheel.tracker.local.mc;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.capture.CaptureLog;
import com.mage.cubewheel.tracker.local.WorldInfo;
import com.mage.cubewheel.tracker.local.WorldResolver;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Minecraft adapter: the current world from the dimension id and the sidebar's "World: X" line. The
 * sidebar is not read here: the lines come from the live {@link com.mage.cubewheel.sidebar.SidebarWatcher}.
 * Re-resolved at most every {@link #TTL_TICKS} ticks and after every level change.
 */
final class WorldProbe {
	private static final int TTL_TICKS = 20;

	private WorldInfo cached = WorldInfo.UNKNOWN;
	private long resolvedAt = Long.MIN_VALUE / 2;
	private WorldInfo lastCaptured;

	void invalidate() {
		resolvedAt = Long.MIN_VALUE / 2;
	}

	WorldInfo current(Minecraft mc, List<String> specialWorlds, long tick) {
		if (tick - resolvedAt < TTL_TICKS) return cached;
		resolvedAt = tick;
		String dimension = mc.level == null ? null : mc.level.dimension().identifier().toString();
		List<String> sidebar = CubeWheelClient.sidebar().lines();
		cached = WorldResolver.resolve(dimension, sidebar, specialWorlds);
		if (!cached.equals(lastCaptured)) {
			lastCaptured = cached;
			CaptureLog capture = CubeWheelClient.capture();
			if (capture != null && capture.enabled()) {
				capture.world(dimension, sidebar, new ArrayList<>(cached.tokens()), cached.special(), System.currentTimeMillis());
			}
		}
		return cached;
	}
}
