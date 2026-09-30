package com.mage.cubewheel.homes;

import com.mage.cubewheel.CommandSender;
import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.config.WheelNode;
import com.mage.cubewheel.wheel.RadialScreen;
import com.mage.cubewheel.wheel.WheelResolver;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the homes ring: sends one rate-limited /homes when the ring is opened
 * with a stale cache, swallows the matching reply, and tracks /sethome and /delhome.
 */
public final class HomesFetcher {
	private final HomesCache homes;
	private final HomesFetchPolicy policy = new HomesFetchPolicy();

	public HomesFetcher(HomesCache homes) {
		this.homes = homes;
	}

	/** RadialScreen.childrenProvider: homes rings fetch on demand, everything else uses the default. */
	public List<WheelNode> childrenFor(WheelNode node) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		Optional<String> host = ServerGate.currentHost();
		if (!node.isDynamic() || !"homes".equals(node.dynamic) || !ServerGate.active(cfg) || host.isEmpty()) {
			return RadialScreen.defaultChildren(node);
		}
		long now = System.currentTimeMillis();
		if (policy.shouldFetch(now, homes.isStale(host.get(), now, HomesFetchPolicy.MAX_AGE_MS))) {
			policy.armed(now);
			CubeWheelClient.LOG.info("[cubewheel] fetching /homes for {}", host.get());
			CommandSender.send("/homes");
		}
		List<WheelNode> children = WheelResolver.children(node, cfg.vaultCount, homes.get(host.get()));
		if (children.isEmpty() && policy.isArmed(now)) {
			return List.of(WheelNode.leaf("Loading…", "minecraft:clock", null));
		}
		return children;
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: false only for the reply to our own /homes. */
	public boolean onGameMessage(Component message, boolean overlay) {
		try {
			if (overlay || message == null) return true;
			if (!ServerGate.active(CubeWheelClient.config().current())) return true;
			Optional<String> host = ServerGate.currentHost();
			if (host.isEmpty()) return true;
			long now = System.currentTimeMillis();
			Optional<List<String>> parsed = HomesParser.parse(ComponentReplies.toReply(message));
			HomesFetchPolicy.Decision decision = policy.onMessage(now, parsed);
			if (decision == HomesFetchPolicy.Decision.IGNORE) return true;
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
