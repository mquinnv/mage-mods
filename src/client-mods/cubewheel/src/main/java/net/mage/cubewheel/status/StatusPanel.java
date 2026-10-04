package net.mage.cubewheel.status;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The "Status" panel (top left, above Jobs): the armor set you wear ("Phoenix 4/4"), coordinates and facing,
 * biome and light level, FPS and speed, game time and clock. Drawn by {@link net.mage.cubewheel.hud.PanelsHud} in
 * CubeWheel's style; replaces SimpleHUD Enhanced's status text and equipment display. Shown wherever you play
 * while {@code status.enabled}.
 */
public final class StatusPanel {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int GREY = 0xFFAAAAAA;
	private static final int WORN_LOW = 0xFFFFFF55;  // a piece under 25% durability
	private static final int WORN_OUT = 0xFFFF5555;  // a piece under 10%

	/** Appended to the set's name when its lore states a bonus that needs more pieces than are worn. */
	private static final String NO_BONUS = " ⚠ no set bonus";

	private static double lastX, lastZ;
	private static boolean hasLast;
	/** Horizontal speed, smoothed over a few ticks (blocks per second). */
	private static double speed;

	private StatusPanel() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			LocalPlayer p = mc.player;
			if (p == null) {
				hasLast = false;
				return;
			}
			if (hasLast) {
				double perSecond = Math.hypot(p.getX() - lastX, p.getZ() - lastZ) * 20;
				speed = speed * 0.6 + perSecond * 0.4;
			}
			lastX = p.getX();
			lastZ = p.getZ();
			hasLast = true;
		});
	}

	public static Optional<Panel> panel(long now) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (!cfg.status.enabled || p == null || mc.level == null) return Optional.empty();
		List<Panel.Line> lines = new ArrayList<>();
		if (cfg.status.armor) lines.add(armorLine(p));
		BlockPos pos = p.blockPosition();
		lines.add(Panel.Line.split(pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
				StatusFormat.facing(p.getDirection().getName()), Panel.WHITE));
		String biome = mc.level.getBiome(pos).unwrapKey().map(k -> StatusFormat.biome(k.identifier().toString())).orElse("");
		lines.add(Panel.Line.split(biome, "Light " + mc.level.getMaxLocalRawBrightness(pos), Panel.WHITE));
		lines.add(Panel.Line.split(mc.getFps() + " fps", StatusFormat.speed(speed), Panel.WHITE));
		LocalTime clock = LocalTime.now();
		lines.add(Panel.Line.split(StatusFormat.gameTime(mc.level.getOverworldClockTime()),
				StatusFormat.clock(clock.getHour(), clock.getMinute()), GREY));
		CubeWheelConfig.Position at = cfg.status.position;
		return Optional.of(new Panel("Status", lines, HudLayout.Corner.parse(at.corner), at.x, at.y));
	}

	/**
	 * "[chestplate] Phoenix   4/4": the set's name and count, yellow/red when a piece is wearing out; yellow with
	 * "⚠ no set bonus" when the lore's set bonus needs more pieces than are worn.
	 */
	private static Panel.Line armorLine(LocalPlayer p) {
		List<String> names = new ArrayList<>();
		List<List<String>> lores = new ArrayList<>();
		ItemStack icon = ItemStack.EMPTY;
		double lowest = 1;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.isEmpty()) {
				names.add(null);
				lores.add(List.of());
				continue;
			}
			names.add(s.getHoverName().getString());
			ItemLore lore = s.get(DataComponents.LORE);
			lores.add(lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList());
			if (icon.isEmpty() || slot == EquipmentSlot.CHEST) icon = s;
			if (s.isDamageableItem() && s.getMaxDamage() > 0) {
				lowest = Math.min(lowest, 1 - s.getDamageValue() / (double) s.getMaxDamage());
			}
		}
		ArmorSet set = ArmorSet.of(names, lores);
		int color = lowest < 0.10 ? WORN_OUT : lowest < 0.25 ? WORN_LOW : Panel.WHITE;
		boolean unmet = set.bonusUnmet();
		Panel.Line line = new Panel.Line("", 0, unmet ? set.name() + NO_BONUS : set.name(), unmet ? Panel.YELLOW : color, set.worn() == 0 ? "" : set.count(),
				set.matching() == 4 ? Panel.GREEN : color);
		return icon.isEmpty() ? line : line.withIcon(icon.copyWithCount(1));
	}
}
