package com.mage.cubewheel.tracker;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.capture.CaptureLog;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.tracker.ContainerScanner.ItemView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Minecraft adapter for the tracker: reads the top-container slots of server GUIs a few ticks after
 * they open (servers fill slots after the screen appears) and once more when they close. Purely
 * passive: never clicks, never sends anything. While a tracker refresh runs, the menus it opens take no
 * mouse input and no keys but Esc, so nothing can be clicked in a menu the player cannot see.
 */
public final class ContainerHook {
	private static final int FIRST_SCAN_TICK = 5;
	private static final int SECOND_SCAN_TICK = 20;

	/** The session of the most recently opened server menu (client thread only). */
	private static Session current;

	private ContainerHook() {}

	/** Scans {@code screen} now if it is the open server menu (the refresh calls this right before closing it). */
	static void scanNow(AbstractContainerScreen<?> screen) {
		Session s = current;
		if (s != null && s.screen == screen) s.scan();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register(ContainerHook::afterInit);
	}

	private static void afterInit(Minecraft mc, Screen screen, int width, int height) {
		try {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			if (mc.player != null && cs.getMenu() == mc.player.inventoryMenu) return; // own inventory
			// Fabric resets per-screen events on every (re)init, so a resize starts a fresh session.
			Session session = new Session(cs);
			current = session;
			ScreenEvents.afterTick(screen).register(s -> session.tick());
			ScreenEvents.remove(screen).register(s -> {
				session.scan();
				if (current == session) current = null;
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !hiddenByRefresh(s));
			ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !hiddenByRefresh(s));
			ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> !hiddenByRefresh(s));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !hiddenByRefresh(s));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> key.isEscape() || !hiddenByRefresh(s));
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] container hook failed", e);
		}
	}

	private static boolean hiddenByRefresh(Screen screen) {
		try {
			return RefreshController.shouldHide(screen);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Non-empty slots that do not belong to the player's inventory, as plain-string views. */
	static List<ItemView> views(AbstractContainerScreen<?> screen) {
		List<ItemView> out = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			ItemLore lore = stack.get(DataComponents.LORE);
			List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			out.add(new ItemView(slot.index, stack.typeHolder().getRegisteredName(), stack.getHoverName().getString(), lines));
		}
		return out;
	}

	private static final class Session {
		private final AbstractContainerScreen<?> screen;
		private int ticks;
		private List<ItemView> lastCaptured;

		Session(AbstractContainerScreen<?> screen) {
			this.screen = screen;
		}

		void tick() {
			ticks++;
			if (ticks == FIRST_SCAN_TICK || ticks == SECOND_SCAN_TICK) scan();
		}

		void scan() {
			try {
				CubeWheelConfig cfg = CubeWheelClient.config().current();
				if (!ServerGate.active(cfg)) return;
				String title = screen.getTitle().getString();
				List<ItemView> items = views(screen);
				long now = System.currentTimeMillis();
				CaptureLog capture = CubeWheelClient.capture();
				if (capture != null && capture.enabled() && !items.equals(lastCaptured)) {
					capture.container(title, items, now);
					lastCaptured = items;
				}
				Optional<String> source = MenuClassifier.classify(title, items, cfg.tracker.sources);
				TrackerStore store = CubeWheelClient.tracker();
				if (source.isEmpty() || store == null) return;
				// During a refresh run, every entry its menus show counts towards "Refreshed N".
				java.util.function.Consumer<String> seen = RefreshController.running() ? RefreshController::noteSeen : null;
				if (ContainerScanner.scan(source.get(), items, store, now, seen) > 0) {
					CubeWheelClient.sidebar().reapply(now);
					store.save();
				}
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.error("[cubewheel] container scan failed", e);
			}
		}
	}
}
