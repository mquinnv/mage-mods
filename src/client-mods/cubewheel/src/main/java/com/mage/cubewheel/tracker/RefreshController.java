package com.mage.cubewheel.tracker;

import com.mage.cubewheel.CommandSender;
import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.config.CubeWheelConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the "Refresh trackers" key and the picker's Refresh button. One press starts
 * one {@link RefreshPolicy} run: each {@code tracker.refreshCommands} entry is sent, its menu is left
 * open until its items arrive (ContainerHook scans it), then closed with the normal close packet before
 * the next command. Never clicks a slot. Any screen the run did not open, or Esc, stops it.
 */
public final class RefreshController {
	private static final RefreshPolicy POLICY = new RefreshPolicy();
	private static long runStart;
	private static boolean reopenPicker;
	/** Set once if hiding a menu ever throws (e.g. a Minecraft update); menus then flash visibly instead. */
	private static boolean hidingDisabled;

	private RefreshController() {}

	public static boolean running() {
		return POLICY.running();
	}

	/**
	 * True while a refresh run is active: the server menus it opens are not drawn (ScreenHideMixin) and
	 * take no clicks or keys except Esc (ContainerHook), so the player sees at most a brief cursor change.
	 */
	public static boolean hidingMenus() {
		return !hidingDisabled && POLICY.running();
	}

	/** Whether {@code screen} is a server menu that the running refresh should keep invisible. */
	public static boolean shouldHide(Screen screen) {
		if (!hidingMenus() || !(screen instanceof AbstractContainerScreen<?> cs)) return false;
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null || cs.getMenu() != mc.player.inventoryMenu;
	}

	/** Called by the render mixin when hiding failed; logs once and stops hiding for the session. */
	public static void disableHiding(Throwable t) {
		if (hidingDisabled) return;
		hidingDisabled = true;
		CubeWheelClient.LOG.warn("[cubewheel] hiding refresh menus disabled for this session: {}", t.toString());
	}

	/** A direct user action: the keybind, or the picker's Refresh button ({@code fromPicker}). */
	public static void request(Minecraft mc, boolean fromPicker) {
		if (mc.player == null) return;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (!cfg.enabled) return;
		if (!ServerGate.active(cfg)) {
			mc.player.sendOverlayMessage(Component.literal("CubeWheel is only active on ManaCube"));
			return;
		}
		if (mc.gui.screen() != null && !fromPicker) return;
		long now = System.currentTimeMillis();
		RefreshPolicy.Start start = POLICY.start(now, cfg.tracker.refreshCommands);
		switch (start.outcome()) {
			case COOLDOWN -> chat(mc, "refresh skipped: wait " + start.waitSeconds() + "s");
			case RUNNING -> chat(mc, "refresh already running");
			case NO_COMMANDS -> chat(mc, "refresh skipped: tracker.refreshCommands is empty");
			case STARTED -> {
				runStart = now;
				reopenPicker = fromPicker;
				if (fromPicker) mc.gui.setScreen(null); // the run needs a clear screen; the picker comes back after
				CubeWheelClient.LOG.info("[cubewheel] tracker refresh: {}", cfg.tracker.refreshCommands);
				tick(mc); // the first command goes out with the keypress itself
			}
		}
	}

	/** END_CLIENT_TICK: advances a running refresh by one step. */
	public static void tick(Minecraft mc) {
		if (!POLICY.running()) return;
		try {
			if (mc.player == null || !ServerGate.active(CubeWheelClient.config().current())) {
				POLICY.abort();
				CubeWheelClient.LOG.info("[cubewheel] tracker refresh stopped: left the server");
				return;
			}
			Screen screen = mc.gui.screen();
			RefreshPolicy.Action action = POLICY.step(System.currentTimeMillis(), view(mc, screen));
			switch (action.kind()) {
				case NONE -> { }
				case SEND -> {
					if (!CommandSender.send(action.command())) {
						POLICY.abort();
						chat(mc, "refresh stopped: could not send " + action.command());
					}
				}
				case CLOSE_MENU -> {
					if (screen instanceof AbstractContainerScreen<?> cs) {
						ContainerHook.scanNow(cs);
						mc.player.closeContainer(); // sends the close packet, like pressing Esc
					}
				}
				case FINISHED -> finished(mc);
				case ABORTED -> chat(mc, "refresh stopped: " + action.reason());
			}
		} catch (RuntimeException e) {
			POLICY.abort();
			CubeWheelClient.LOG.error("[cubewheel] tracker refresh failed", e);
		}
	}

	private static void finished(Minecraft mc) {
		TrackerStore store = CubeWheelClient.tracker();
		int n = store == null ? 0 : store.countSeenSince(runStart);
		String msg = "Refreshed " + n + (n == 1 ? " tracker" : " trackers");
		if (POLICY.timedOut() > 0) msg += " (" + POLICY.timedOut() + (POLICY.timedOut() == 1 ? " menu" : " menus") + " did not load)";
		chat(mc, msg);
		if (reopenPicker && mc.gui.screen() == null) mc.gui.setScreen(new TrackerScreen());
	}

	private static RefreshPolicy.View view(Minecraft mc, Screen screen) {
		if (screen == null) return RefreshPolicy.View.NONE;
		if (screen instanceof AbstractContainerScreen<?> cs && cs.getMenu() != mc.player.inventoryMenu) {
			return ContainerHook.views(cs).isEmpty() ? RefreshPolicy.View.MENU_LOADING : RefreshPolicy.View.MENU_READY;
		}
		return RefreshPolicy.View.OTHER;
	}

	private static void chat(Minecraft mc, String text) {
		if (mc.player == null) return;
		mc.player.sendSystemMessage(Component.literal("[CubeWheel] " + text).withStyle(ChatFormatting.GRAY));
	}
}
