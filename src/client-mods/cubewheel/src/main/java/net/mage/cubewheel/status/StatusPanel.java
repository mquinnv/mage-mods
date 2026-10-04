package net.mage.cubewheel.status;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import net.mage.cubewheel.hud.TwoColumn;
import net.mage.cubewheel.wheel.Icons;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The "Status" panel (top left, above Jobs), one box in two columns. Left: coordinates and facing ("123  64  -456
 * E +X"), biome and a light-level disc, FPS and speed, game time and the real clock behind their item icons. Right,
 * while {@code status.armor}: each armor piece with its durability bar, then the set you wear ("Phoenix 4/4") and
 * what its set bonus does (or "⚠ no set bonus" when too few pieces are worn). Drawn by
 * {@link net.mage.cubewheel.hud.PanelsHud} in CubeWheel's style; replaces SimpleHUD Enhanced's status text and
 * equipment display. Shown wherever you play while {@code status.enabled}.
 */
public final class StatusPanel {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int GREY = 0xFFAAAAAA;
	/** A set bonus that is not on (fewer pieces than a full set, no requirement stated). */
	private static final int DIM = 0xFF707070;
	/** An armor row: the full-size item, a gap, and at least this much bar. */
	private static final int SLOT_ROW_W = 18 + 30;
	/** Space between the set's name and its count. */
	private static final int COUNT_GAP = 4;
	/** The set bonus gets at most this many lines under the set's name. */
	private static final int BONUS_LINES = 2;

	/** Replaces the set bonus when the lore's bonus needs more pieces than are worn. */
	private static final String NO_BONUS = "⚠ no set bonus";
	private static final String GAME_TIME_ICON = "minecraft:grass_block";
	/** Minecraft's globe. */
	private static final String CLOCK_ICON = "minecraft:globe_banner_pattern";

	/**
	 * The armor items {@link #set} and {@link #sideLines} were made from, by identity: the server sends a new stack
	 * when a piece changes, so lore is parsed only then, not every frame.
	 */
	private static final Object[] worn = new Object[ARMOR.length];
	private static ArmorSet set = ArmorSet.NONE;
	/** The armor column's width and text lines for {@link #set}; null until made for it. */
	private static List<Panel.Line> sideLines;
	private static int sideWidth;

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
		BlockPos pos = p.blockPosition();
		lines.add(Panel.Line.split(StatusFormat.coords(pos.getX(), pos.getY(), pos.getZ()),
				StatusFormat.facing(p.getDirection().getName()), Panel.WHITE));
		String biome = mc.level.getBiome(pos).unwrapKey().map(k -> StatusFormat.biome(k.identifier().toString())).orElse("");
		lines.add(new Panel.Line(biome, Panel.WHITE).withLight(mc.level.getMaxLocalRawBrightness(pos)));
		lines.add(Panel.Line.split(mc.getFps() + " fps", StatusFormat.speed(speed), Panel.WHITE));
		LocalTime clock = LocalTime.now();
		lines.add(Panel.Line.pieces(List.of(
				new Panel.Piece(Icons.stack(GAME_TIME_ICON), StatusFormat.gameTime(mc.level.getOverworldClockTime()), GREY),
				new Panel.Piece(Icons.stack(CLOCK_ICON), StatusFormat.clock(clock.getHour(), clock.getMinute()), GREY))));
		Panel.Side side = cfg.status.armor ? armor(p, mc.font) : null;
		CubeWheelConfig.Position at = cfg.status.position;
		return Optional.of(new Panel("Status", lines, HudLayout.Corner.parse(at.corner), at.x, at.y, side));
	}

	/**
	 * The worn set, from the four pieces' names and lore (see {@link ArmorSet#of(List, List)}); re-read only when an
	 * armor item changes. Client thread only.
	 */
	public static ArmorSet armorSet(LocalPlayer p) {
		boolean changed = false;
		for (int i = 0; i < ARMOR.length; i++) {
			Object s = p.getItemBySlot(ARMOR[i]);
			if (s != worn[i]) {
				worn[i] = s;
				changed = true;
			}
		}
		if (changed) {
			set = readSet(p);
			sideLines = null;
		}
		return set;
	}

	private static ArmorSet readSet(LocalPlayer p) {
		List<String> names = new ArrayList<>(4);
		List<List<String>> lores = new ArrayList<>(4);
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
		}
		return ArmorSet.of(names, lores);
	}

	/**
	 * The right column: head to feet, each piece with a durability bar (yellow under 25%, red under 10%; none for an
	 * unbreakable piece), then "Phoenix  4/4" (the count green at 4/4) and the set bonus, word-wrapped to the column.
	 */
	private static Panel.Side armor(LocalPlayer p, Font font) {
		List<Panel.Slot> slots = new ArrayList<>(4);
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.isEmpty()) {
				slots.add(new Panel.Slot(null, -1, 0));
				continue;
			}
			double left = s.isDamageableItem() && s.getMaxDamage() > 0 ? 1 - s.getDamageValue() / (double) s.getMaxDamage() : -1;
			slots.add(new Panel.Slot(s, left, left < 0 ? 0 : StatusFormat.wearColor(left)));
		}
		ArmorSet set = armorSet(p);
		if (sideLines == null) sideText(set, font);
		return new Panel.Side(sideWidth, slots, sideLines);
	}

	/** The armor column's width and its text lines for {@code set}: made once per change of armor. */
	private static void sideText(ArmorSet set, Font font) {
		boolean unmet = set.bonusUnmet();
		// The bonus is on when the stated requirement is met; with none stated ("FULL SET EFFECTS"), at a full set.
		boolean on = set.required() > 0 ? !unmet : set.matching() == 4;
		String count = set.worn() == 0 ? "" : set.count();
		int natural = Math.max(SLOT_ROW_W, font.width(set.name()) + (count.isEmpty() ? 0 : COUNT_GAP + font.width(count)));
		if (unmet) natural = Math.max(natural, font.width(NO_BONUS));
		else natural = Math.max(natural, Math.min(TwoColumn.SIDE_MAX_W, font.width(set.bonus())));
		int width = TwoColumn.sideWidth(natural);
		List<Panel.Line> lines = new ArrayList<>();
		lines.add(new Panel.Line("", 0, set.name(), set.worn() == 0 ? GREY : unmet ? Panel.YELLOW : Panel.WHITE, count,
				set.matching() == 4 ? Panel.GREEN : unmet ? Panel.YELLOW : Panel.WHITE));
		if (unmet) {
			lines.add(new Panel.Line(NO_BONUS, Panel.YELLOW));
		} else {
			for (String l : TwoColumn.wrap(set.bonus(), width, font::width, BONUS_LINES)) lines.add(new Panel.Line(l, on ? GREY : DIM));
		}
		sideWidth = width;
		sideLines = List.copyOf(lines);
	}
}
