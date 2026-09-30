package com.mage.cubewheel.tracker;

import com.mage.cubewheel.CommandSender;
import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.config.CubeWheelConfig;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Minecraft adapter for the "Refresh trackers" key and the picker's Refresh button. One press starts
 * one {@link RefreshPolicy} run: each {@code tracker.refreshCommands} entry is sent, its menu is left
 * open until its items arrive (ContainerHook scans it), then closed with the normal close packet before
 * the next command. Never clicks a slot. Only menus that {@link MenuClassifier} recognises (or that are
 * still empty) are hidden and closed; any other screen, Esc, or pressing use/attack while the run waits
 * stops it and leaves the player's own menu alone.
 */
public final class RefreshController {
	/** How long the picker shows the last refresh result. */
	private static final long STATUS_MS = 8_000;

	private static final RefreshPolicy POLICY = new RefreshPolicy();
	/** Ids of the entries the run's menus showed (changed or just confirmed). */
	private static final Set<String> seenThisRun = new HashSet<>();
	private static boolean reopenPicker;
	/** Use/attack seen down at START_CLIENT_TICK; OR-ed with the END_CLIENT_TICK sample. */
	private static boolean inputSampled;
	/** Set once if hiding a menu ever throws (e.g. a Minecraft update); menus then flash visibly instead. */
	private static boolean hidingDisabled;
	private static String status;
	private static long statusAt;
	// shouldHide() runs every frame: classify each screen at most once per tick.
	private static long tickNo;
	private static Screen viewScreen;
	private static long viewTick = -1;
	private static RefreshPolicy.View viewCached;

	private RefreshController() {}

	public static boolean running() {
		return POLICY.running();
	}

	/**
	 * True while a refresh run is active: the recognised server menus it opens are not drawn
	 * (ScreenHideMixin) and take no clicks or keys except Esc (ContainerHook).
	 */
	public static boolean hidingMenus() {
		return !hidingDisabled && POLICY.running();
	}

	/**
	 * Whether {@code screen} is a server menu that the running refresh should keep invisible: a container
	 * (not the player's inventory) that is still empty or that MenuClassifier recognises. A menu the
	 * player opened themselves (a chest, warps) is never hidden.
	 */
	public static boolean shouldHide(Screen screen) {
		if (!hidingMenus() || !(screen instanceof AbstractContainerScreen<?>)) return false;
		RefreshPolicy.View v = view(Minecraft.getInstance(), screen);
		return v == RefreshPolicy.View.MENU_LOADING || v == RefreshPolicy.View.MENU_READY;
	}

	/** Called by the render mixin when hiding failed; logs once and stops hiding for the session. */
	public static void disableHiding(Throwable t) {
		if (hidingDisabled) return;
		hidingDisabled = true;
		CubeWheelClient.LOG.warn("[cubewheel] hiding refresh menus disabled for this session: {}", t.toString());
	}

	/** ContainerHook: an entry one of the run's menus showed. */
	static void noteSeen(String id) {
		if (POLICY.running() && id != null) seenThisRun.add(id);
	}

	/** The last refresh result for the picker, or null once it is older than a few seconds. */
	public static String pickerStatus() {
		if (status == null || Util.getMillis() - statusAt > STATUS_MS) return null;
		return status;
	}

	/** A direct user action: the keybind, or the picker's Refresh button ({@code fromPicker}). */
	public static void request(Minecraft mc, boolean fromPicker) {
		if (mc.player == null) return;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		if (!cfg.enabled) return;
		if (!ServerGate.active(cfg)) {
			mc.player.sendOverlayMessage(Component.literal("CubeWheel is only active on ManaCube"));
			if (fromPicker) setStatus("Refresh only works on ManaCube");
			return;
		}
		if (mc.gui.screen() != null && !fromPicker) return;
		long now = Util.getMillis();
		RefreshPolicy.Start start = POLICY.start(now, cfg.tracker.refreshCommands);
		switch (start.outcome()) {
			case COOLDOWN -> report(mc, fromPicker, "refresh skipped: wait " + start.waitSeconds() + "s");
			case RUNNING -> report(mc, fromPicker, "refresh already running");
			case NO_COMMANDS -> report(mc, fromPicker, "refresh skipped: tracker.refreshCommands is empty");
			case STARTED -> {
				seenThisRun.clear();
				inputSampled = false;
				reopenPicker = fromPicker;
				if (fromPicker) mc.gui.setScreen(null); // the run needs a clear screen; the picker comes back after
				CubeWheelClient.LOG.info("[cubewheel] tracker refresh: {}", cfg.tracker.refreshCommands);
				tick(mc); // the first command goes out with the keypress itself
			}
		}
	}

	/** START_CLIENT_TICK: notes use/attack held now, so a click shorter than a tick is not missed. */
	public static void sampleInput(Minecraft mc) {
		if (!POLICY.running()) return;
		try {
			if (userInput(mc)) inputSampled = true;
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] refresh input sample failed", e);
		}
	}

	/** END_CLIENT_TICK: advances a running refresh by one step. */
	public static void tick(Minecraft mc) {
		tickNo++;
		if (!POLICY.running()) return;
		try {
			if (mc.player == null || !ServerGate.active(CubeWheelClient.config().current())) {
				POLICY.abort();
				CubeWheelClient.LOG.info("[cubewheel] tracker refresh stopped: left the server");
				return;
			}
			Screen screen = mc.gui.screen();
			boolean input = inputSampled || userInput(mc);
			inputSampled = false;
			RefreshPolicy.Action action = POLICY.step(Util.getMillis(), view(mc, screen), input);
			switch (action.kind()) {
				case NONE -> { }
				case SEND -> {
					if (!CommandSender.send(action.command())) {
						POLICY.abort();
						report(mc, reopenPicker, "refresh stopped: could not send " + action.command());
					}
				}
				case CLOSE_MENU -> {
					if (screen instanceof AbstractContainerScreen<?> cs) {
						ContainerHook.scanNow(cs);
						mc.player.closeContainer(); // sends the close packet, like pressing Esc
					}
				}
				case FINISHED -> finished(mc);
				case ABORTED -> report(mc, reopenPicker, "refresh stopped: " + action.reason());
			}
		} catch (RuntimeException e) {
			POLICY.abort();
			CubeWheelClient.LOG.error("[cubewheel] tracker refresh failed", e);
		}
	}

	private static boolean userInput(Minecraft mc) {
		return mc.options.keyUse.isDown() || mc.options.keyAttack.isDown();
	}

	private static void finished(Minecraft mc) {
		String msg = RefreshPolicy.finishedMessage(seenThisRun.size(), POLICY.timedOut());
		seenThisRun.clear();
		report(mc, reopenPicker, msg);
		if (reopenPicker && mc.gui.screen() == null) mc.gui.setScreen(new TrackerScreen());
	}

	private static RefreshPolicy.View view(Minecraft mc, Screen screen) {
		if (screen == null) return RefreshPolicy.View.NONE;
		if (!(screen instanceof AbstractContainerScreen<?> cs) || (mc.player != null && cs.getMenu() == mc.player.inventoryMenu)) {
			return RefreshPolicy.View.OTHER;
		}
		if (screen == viewScreen && viewTick == tickNo && viewCached != null) return viewCached;
		RefreshPolicy.View v = RefreshPolicy.menuView(screen.getTitle().getString(), ContainerHook.views(cs),
				CubeWheelClient.config().current().tracker.sources);
		viewScreen = screen;
		viewTick = tickNo;
		viewCached = v;
		return v;
	}

	/** Grey chat line; from the picker also shown in the picker, where chat is hard to see. */
	private static void report(Minecraft mc, boolean picker, String text) {
		if (picker) setStatus(text);
		if (mc.player == null) return;
		mc.player.sendSystemMessage(Component.literal("[CubeWheel] " + text).withStyle(ChatFormatting.GRAY));
	}

	private static void setStatus(String text) {
		status = text;
		statusAt = Util.getMillis();
	}
}
