package net.mage.cubewheel;

import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.sidebar.SidebarGate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Decides whether CubeWheel may act on the server the client is connected to. */
public final class ServerGate {
	private ServerGate() {}

	/** Pure: true if the address (port ignored, case-insensitive) equals a host or is a subdomain of one. */
	public static boolean matches(String address, List<String> hosts) {
		String host = host(address);
		if (host.isEmpty() || hosts == null) return false;
		for (String h : hosts) {
			if (h == null || h.isBlank()) continue;
			String want = h.trim().toLowerCase(Locale.ROOT);
			if (host.equals(want) || host.endsWith("." + want)) return true;
		}
		return false;
	}

	/** Pure: lower-cased host part of "host[:port]", trailing dot removed; "" for null. */
	static String host(String address) {
		if (address == null) return "";
		String a = address.trim().toLowerCase(Locale.ROOT);
		int colon = a.indexOf(':');
		if (colon >= 0 && colon == a.lastIndexOf(':')) a = a.substring(0, colon);
		if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
		return a;
	}

	/** Lower-cased host of the current multiplayer server; empty in singleplayer and menus. */
	public static Optional<String> currentHost() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		if (server == null || server.ip == null) return Optional.empty();
		String h = host(server.ip);
		return h.isEmpty() ? Optional.empty() : Optional.of(h);
	}

	public static boolean active(CubeWheelConfig cfg) {
		if (cfg == null || !cfg.enabled) return false;
		return currentHost().map(h -> matches(h, cfg.serverHosts)).orElse(false);
	}

	/**
	 * {@link #active} and in ManaCube Survival: the live sidebar title matches
	 * {@code tracker.survivalSidebarPattern}. Menu scanning, refresh runs and local counting use this, so
	 * SkyBlock, Parkour or the hub on the same host never feed the trackers.
	 */
	public static boolean survival(CubeWheelConfig cfg) {
		if (!active(cfg) || cfg.tracker == null) return false;
		return SidebarGate.matches(CubeWheelClient.sidebar().title(), cfg.tracker.survivalSidebarPattern);
	}
}
