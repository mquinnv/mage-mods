package net.mage.cubewheel.homes;

import net.mage.cubewheel.CommandSender;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.wheel.RadialScreen;
import net.mage.cubewheel.wheel.WheelResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the homes ring: sends one rate-limited /homes when the ring is opened
 * with a stale cache, swallows the matching reply, and tracks /sethome and /delhome.
 */
public final class HomesFetcher {
	/** Runtime-only dynamic source of the "↻ Refresh" entry; never appears in config. */
	public static final String REFRESH_SOURCE = "homes-refresh";

	private final HomesCache homes;
	private final HomesFetchPolicy policy = new HomesFetchPolicy();
	private WheelNode lastHomesNode; // the configured Homes ring, so Refresh can resolve like it

	public HomesFetcher(HomesCache homes) {
		this.homes = homes;
	}

	/** True while a /homes we sent is still awaiting its reply (the Loading… placeholder is valid). */
	public boolean isLoading(long now) {
		return policy.isArmed(now);
	}

	/** The runtime "↻ Refresh" entry; screens re-resolve their current ring in place when it is activated. */
	public static boolean isRefreshEntry(WheelNode node) {
		return node != null && node.isDynamic() && REFRESH_SOURCE.equals(node.dynamic);
	}

	/**
	 * RadialScreen.childrenProvider. Resolution always comes from the cache; /homes is sent only when
	 * {@code userInitiated} and HomesFetchPolicy.mayFetch agrees: opening the Homes ring with a stale
	 * cache, or activating Refresh (forced), both at most once per MIN_INTERVAL_MS. Non-user calls
	 * (refresh, Back, tick, reply-driven) never send. Everything else uses the default resolver.
	 */
	public List<WheelNode> childrenFor(WheelNode node, boolean userInitiated) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		Optional<String> host = ServerGate.currentHost();
		boolean homesRing = node.isDynamic() && "homes".equals(node.dynamic);
		boolean refresh = isRefreshEntry(node);
		if (!(homesRing || refresh) || !ServerGate.active(cfg) || host.isEmpty()) {
			return RadialScreen.defaultChildren(node);
		}
		if (homesRing) lastHomesNode = node;
		WheelNode ring = lastHomesNode != null ? lastHomesNode : WheelNode.dynamic("Homes", null, "homes");
		long now = System.currentTimeMillis();
		boolean stale = homes.isStale(host.get(), now, HomesFetchPolicy.MAX_AGE_MS);
		if (policy.mayFetch(now, userInitiated, refresh, stale) && CommandSender.send("/homes")) {
			policy.armed(now);
			CubeWheelClient.LOG.info("[cubewheel] fetching /homes for {}", host.get());
		}
		List<String> names = homes.get(host.get());
		List<WheelNode> children = new ArrayList<>();
		if (names.isEmpty()) {
			children.add(policy.isArmed(now)
					? WheelNode.leaf("Loading…", "minecraft:clock", null)
					: WheelNode.dynamic("↻ Refresh", "minecraft:clock", REFRESH_SOURCE));
		}
		children.addAll(WheelResolver.children(ring, net.mage.cubewheel.live.LiveWatcher.vaultCount(cfg.vaultCount), names));
		return children;
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: false only for the reply to our own /homes. */
	public boolean onGameMessage(Component message, boolean overlay) {
		// Runs on the client thread (Fabric dispatches ALLOW_GAME after PacketUtils.ensureRunningOnSameThread),
		// so the unsynchronised policy/cache access here and in childrenFor is safe.
		try {
			if (overlay || message == null) return true;
			if (!ServerGate.active(CubeWheelClient.config().current())) return true;
			Optional<String> host = ServerGate.currentHost();
			if (host.isEmpty()) return true;
			long now = System.currentTimeMillis();
			Optional<List<String>> parsed = HomesParser.parse(ComponentReplies.toReply(message));
			HomesFetchPolicy.Decision decision = policy.onMessage(now, parsed);
			if (decision == HomesFetchPolicy.Decision.IGNORE) return true;
			// A passive (not requested) empty list never wipes the cache; only our own /homes reply can.
			if (parsed.get().isEmpty() && decision != HomesFetchPolicy.Decision.ACCEPT_AND_SUPPRESS) return true;
			homes.put(host.get(), parsed.get(), now);
			homes.save();
			CubeWheelClient.LOG.debug("[cubewheel] homes for {}: {} ({})", host.get(), parsed.get(), decision);
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				try {
					if (mc.gui.screen() instanceof RadialScreen wheel) wheel.refresh();
				} catch (RuntimeException e) {
					CubeWheelClient.LOG.error("[cubewheel] refreshing wheel after homes reply failed", e);
				}
			});
			return decision != HomesFetchPolicy.Decision.ACCEPT_AND_SUPPRESS;
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] homes message handler failed", e);
			return true;
		}
	}

	/** ClientSendMessageEvents.COMMAND: keeps the cache in step with /sethome and /delhome. */
	public void onCommand(String command) {
		try {
			if (!ServerGate.active(CubeWheelClient.config().current())) return;
			Optional<String> host = ServerGate.currentHost();
			if (host.isEmpty()) return;
			HomesParser.parseOutgoing(command).ifPresent(e -> {
				if (e.add()) homes.add(host.get(), e.name());
				else homes.remove(host.get(), e.name());
				homes.save();
			});
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] homes command handler failed", e);
		}
	}
}
