package net.mage.cubewheel.hud;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.live.LiveWatcher;
import net.mage.cubewheel.live.VaultPages;
import net.mage.cubewheel.status.ArmorSet;
import net.mage.cubewheel.status.StatusPanel;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Minecraft adapter for {@link Notices}: each tick, feeds it how full the main inventory is (the 36 slots the Charms
 * panel counts), the worn set's bonus state ({@link StatusPanel#armorSet}, re-read only when an armor item changes)
 * and each vault's free
 * slots as last seen (what the Charms panel shows; it changes only when a vault is opened). Its notices go to the
 * popup ({@link ProgressToastHud#notice}). A new player or world resets it, so joining raises nothing. Nothing is fed
 * while a screen is open, as the popup is hidden then: what changed in a menu (a vault filled, armor swapped) is
 * seen, and shown, when it closes. A failure is logged and stops notices until the next player or world.
 */
public final class NoticeWatcher {
	private static final int SLOTS = 36;

	private static final Notices NOTICES = new Notices(ProgressToastHud::notice);
	private static LocalPlayer lastPlayer;
	private static ClientLevel lastLevel;
	/** Set by a failure; cleared when the player or world changes. */
	private static boolean failed;

	private NoticeWatcher() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NoticeWatcher::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			lastPlayer = null;
			lastLevel = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (p != lastPlayer || mc.level != lastLevel) {
			lastPlayer = p;
			lastLevel = mc.level;
			NOTICES.reset(now);
			failed = false;
		}
		if (failed) return;
		try {
			if (mc.gui.screen() != null) return;
			Inventory inv = p.getInventory();
			int used = 0;
			for (int i = 0; i < SLOTS; i++) if (!inv.getItem(i).isEmpty()) used++;
			NOTICES.inventory(used, SLOTS, now);
			ArmorSet set = StatusPanel.armorSet(p);
			NOTICES.armor(set.name(), set.matching(), set.required(), now);
			for (Map.Entry<Integer, Integer> e : LiveWatcher.vaultFill().entrySet()) {
				NOTICES.vault(e.getKey(), Math.max(0, VaultPages.STORAGE - e.getValue()), now);
			}
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] notices failed; off until the next player or world", e);
			failed = true;
		}
	}
}
