package net.mage.cubewheel.charms;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.live.LiveWatcher;
import net.mage.cubewheel.live.VaultPages;
import net.mage.cubewheel.wheel.Icons;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Minecraft adapter for the "Charms" HUD panel (replacing schrumboHUD's inventory grid): how full the inventory is,
 * the amulet in the pendant slot and the talismans carried, each as an icon and a few characters ({@link Charm}),
 * and how full each vault was when last opened.
 */
public final class CharmsPanel {
	/** Main inventory plus hotbar. */
	private static final int SLOTS = 36;
	/** "Equip a Pendant by placing in the bottom right slot of your inventory": the main grid's last slot. */
	private static final int PENDANT_SLOT = 35;
	private static final int RED = 0xFFFF5555;
	private static final int DIM = 0xFF707070;

	private CharmsPanel() {}

	public static Optional<Panel> panel(long now) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.charms.enabled || mc.player == null || !ServerGate.active(cfg)) return Optional.empty();
		Inventory inv = mc.player.getInventory();
		List<Panel.Line> lines = new ArrayList<>();
		int used = 0;
		List<Panel.Line> amulets = new ArrayList<>();
		List<Panel.Line> talismans = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			used++;
			ItemLore lore = stack.get(DataComponents.LORE);
			List<String> text = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			Optional<Charm> c = Charm.parse(stack.getHoverName().getString(), text);
			if (c.isEmpty()) continue;
			Charm charm = c.get();
			if (charm.kind() == Charm.Kind.AMULET) {
				boolean worn = i == PENDANT_SLOT;
				Panel.Line l = new Panel.Line(worn ? "◆" : "", Panel.GREEN, charm.text(), worn ? Panel.WHITE : DIM, "")
						.withIcon(Icons.stack(charm.icon()));
				if (worn) amulets.add(0, l);
				else amulets.add(l);
			} else {
				talismans.add(new Panel.Line("", 0, charm.text(), Panel.YELLOW, "").withIcon(Icons.stack(charm.icon())));
			}
		}
		InvMeter.Level level = InvMeter.level(used, SLOTS);
		int colour = level == InvMeter.Level.FULL ? RED : level == InvMeter.Level.HIGH ? Panel.YELLOW : Panel.GRAY;
		lines.add(new Panel.Line("", 0, "Inv", Panel.GRAY, used + "/" + SLOTS, colour).withProgress(used / (double) SLOTS));
		lines.addAll(amulets);
		lines.addAll(talismans);
		String vaults = vaultLine(LiveWatcher.vaultCount(cfg.vaultCount), LiveWatcher.vaultFill());
		if (!vaults.isEmpty()) lines.add(new Panel.Line(vaults, Panel.GRAY));
		CubeWheelConfig.Position p = cfg.charms.position;
		return Optional.of(new Panel("Charms", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	/** "PV1 30 · PV2 12 · PV3 45 · PV4 —": used slots per vault as last seen ("—": not opened yet). */
	static String vaultLine(int count, Map<Integer, Integer> fill) {
		StringBuilder sb = new StringBuilder();
		for (int page = 1; page <= count; page++) {
			if (sb.length() > 0) sb.append(" · ");
			Integer n = fill.get(page);
			sb.append("PV").append(page).append(' ').append(n == null ? "—" : n >= VaultPages.STORAGE ? "full" : n.toString());
		}
		return sb.toString();
	}
}
