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
		// Two rows, no title: the inventory bar (vault fill on its right), then every charm side by side.
		List<Panel.Piece> amulets = new ArrayList<>();
		List<Panel.Piece> talismans = new ArrayList<>();
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
				// The worn one (pendant slot) first and bright; carried ones dim.
				boolean worn = i == PENDANT_SLOT;
				Panel.Piece piece = new Panel.Piece(Icons.stack(charm.icon()), charm.text(), worn ? Panel.WHITE : DIM);
				if (worn) amulets.add(0, piece);
				else amulets.add(piece);
			} else {
				// Just what it sells, as an icon after one "Auto$" label (lifetime earnings read as noise).
				talismans.add(new Panel.Piece(Icons.stack(charm.icon()), "", Panel.YELLOW));
			}
		}
		InvMeter.Level level = InvMeter.level(used, SLOTS);
		int colour = level == InvMeter.Level.FULL ? RED : level == InvMeter.Level.HIGH ? Panel.YELLOW : Panel.WHITE;
		String vaults = vaultLine(LiveWatcher.vaultCount(cfg.vaultCount), LiveWatcher.vaultFill());
		lines.add(new Panel.Line("", 0, "Inv " + used + "/" + SLOTS, colour, vaults, Panel.GRAY).withGauge(used / (double) SLOTS));
		List<Panel.Piece> charms = new ArrayList<>(amulets);
		if (!talismans.isEmpty()) {
			charms.add(new Panel.Piece(null, "Auto$", Panel.YELLOW));
			charms.addAll(talismans);
		}
		if (!charms.isEmpty()) lines.add(Panel.Line.pieces(charms));
		CubeWheelConfig.Position p = cfg.charms.position;
		return Optional.of(new Panel("", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}

	/** "PV 15·33·full·—": free slots per vault as last seen ("—": not opened yet); empty without vaults. */
	static String vaultLine(int count, Map<Integer, Integer> fill) {
		StringBuilder sb = new StringBuilder();
		for (int page = 1; page <= count; page++) {
			sb.append(sb.length() == 0 ? "PV " : "·");
			Integer used = fill.get(page);
			int free = used == null ? -1 : Math.max(0, VaultPages.STORAGE - used);
			sb.append(used == null ? "—" : free == 0 ? "full" : String.valueOf(free));
		}
		return sb.toString();
	}
}
