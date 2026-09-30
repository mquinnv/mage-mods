package com.mage.cubewheel;

import com.mage.cubewheel.config.ConfigStore;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.config.WheelNode;
import com.mage.cubewheel.homes.HomesCache;
import com.mage.cubewheel.tracker.TrackerStore;
import com.mage.cubewheel.wheel.RadialScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CubeWheelClient implements ClientModInitializer {
	public static final String MOD_ID = "cubewheel";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private static ConfigStore config;
	private static HomesCache homes; // initialised in Task 7
	private static TrackerStore tracker; // initialised in Task 8

	public static ConfigStore config() { return config; }

	/** Null until the homes feature is wired; callers treat null as "no homes". */
	public static HomesCache homes() { return homes; }

	/** Null until the tracker feature is wired. */
	public static TrackerStore tracker() { return tracker; }

	@Override
	public void onInitializeClient() {
		config = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("cubewheel.json"));
		String err = config.reload();
		if (err != null) LOG.error("[cubewheel] config load failed, using defaults/previous: {}", err);
		Keybinds.register();
		ClientTickEvents.END_CLIENT_TICK.register(CubeWheelClient::onEndTick);
		LOG.info("[cubewheel] initialised");
	}

	private static void onEndTick(Minecraft mc) {
		try {
			handleWheelKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] wheel key handler failed", e);
		}
		try {
			handleReloadKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] reload key handler failed", e);
		}
	}

	private static void handleWheelKey(Minecraft mc) {
		if (!Keybinds.wheel.consumeClick()) return;
		while (Keybinds.wheel.consumeClick()) {
			// drain queued presses: one press opens one wheel
		}
		if (mc.gui.screen() != null || mc.player == null) return;
		CubeWheelConfig cfg = config.current();
		if (!cfg.enabled) return;
		if (!ServerGate.active(cfg)) {
			mc.player.sendOverlayMessage(Component.literal("CubeWheel is only active on ManaCube"));
			return;
		}
		WheelNode root = WheelNode.ring("CubeWheel", null, cfg.wheel.toArray(WheelNode[]::new));
		mc.gui.setScreen(new RadialScreen(root, Keybinds.wheel));
	}

	private static void handleReloadKey(Minecraft mc) {
		if (!Keybinds.reload.consumeClick()) return;
		while (Keybinds.reload.consumeClick()) {
			// drain
		}
		String err = config.reload();
		if (err != null) LOG.warn("[cubewheel] reload failed: {}", err);
		if (mc.player == null) return;
		Component msg = err != null
				? Component.literal("[CubeWheel] " + err).withStyle(ChatFormatting.RED)
				: Component.literal("[CubeWheel] config reloaded").withStyle(ChatFormatting.GREEN);
		mc.player.sendSystemMessage(msg);
	}
}
