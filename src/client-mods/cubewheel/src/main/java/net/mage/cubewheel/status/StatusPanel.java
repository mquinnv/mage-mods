package net.mage.cubewheel.status;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.hud.Fit;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
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
 * The "Status" panel (top left, above Jobs). While {@code status.armor}: a gear row (the armor pieces, a dot, the
 * main- and off-hand items, then the set you wear, "Phoenix 4/4") and under it what the set bonus does, or
 * "⚠ no set bonus" when too few pieces are worn. A piece wearing out gets Minecraft's thin durability bar on its
 * icon. Then, past a rule, a 2×2 grid: coordinates and facing | biome and a light-level disc / FPS and speed | game
 * time and the real clock behind their item icons. Drawn by {@link net.mage.cubewheel.hud.PanelsHud} in CubeWheel's
 * style; replaces SimpleHUD Enhanced's status text and equipment display. Shown wherever you play while
 * {@code status.enabled}.
 */
public final class StatusPanel {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** The gear row's slots: the armor, then main and off hand. */
	private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private static final int GREY = 0xFFAAAAAA;
	/** A set bonus that is not on (fewer pieces than a full set, no requirement stated). */
	private static final int DIM = 0xFF707070;

	/** The set's name in the gear row is cut to this many pixels, so the box never gets very wide. */
	private static final int MAX_NAME_W = 90;

	/** Replaces the set bonus when the lore's bonus needs more pieces than are worn. */
	private static final String NO_BONUS = "⚠ no set bonus";
	/** Between the armor and the held items. The leading space evens the gap after the item icons. */
	private static final String DOT = " ·";
	private static final String GAME_TIME_ICON = "minecraft:grass_block";
	/** Minecraft's globe. */
	private static final String CLOCK_ICON = "minecraft:globe_banner_pattern";

	/**
	 * The armor items {@link #set} was made from, by identity: the server sends a new stack when a piece changes, so
	 * lore is parsed only then, not every frame.
	 */
	private static final Object[] worn = new Object[ARMOR.length];
	private static ArmorSet set = ArmorSet.NONE;
	/** Per {@link #GEAR} slot: the item last checked for "Unbreakable" (by identity), and the answer. */
	private static final Object[] checked = new Object[GEAR.length];
	private static final boolean[] unbreakable = new boolean[GEAR.length];

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
		List<Panel.Line> lines = new ArrayList<>(2);
		if (cfg.status.armor) gear(p, lines);
		BlockPos pos = p.blockPosition();
		String biome = mc.level.getBiome(pos).unwrapKey().map(k -> StatusFormat.biome(k.identifier().toString())).orElse("");
		LocalTime clock = LocalTime.now();
		Panel.Grid grid = new Panel.Grid(List.of(
				List.of(Panel.Cell.split(StatusFormat.coords(pos.getX(), pos.getY(), pos.getZ()),
								StatusFormat.facing(p.getDirection().getName()), Panel.WHITE),
						Panel.Cell.disc(biome, Panel.WHITE, mc.level.getMaxLocalRawBrightness(pos))),
				List.of(Panel.Cell.split(mc.getFps() + " fps", StatusFormat.speed(speed), Panel.WHITE),
						new Panel.Cell(Icons.stack(GAME_TIME_ICON), StatusFormat.gameTime(mc.level.getOverworldClockTime()), GREY,
								Icons.stack(CLOCK_ICON), StatusFormat.clock(clock.getHour(), clock.getMinute()), GREY, -1))));
		CubeWheelConfig.Position at = cfg.status.position;
		return Optional.of(new Panel("Status", lines, HudLayout.Corner.parse(at.corner), at.x, at.y, grid));
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
		if (changed) set = readSet(p);
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
			lores.add(lore(s));
		}
		return ArmorSet.of(names, lores);
	}

	private static List<String> lore(ItemStack s) {
		ItemLore lore = s.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/**
	 * The gear row and the set bonus row. Empty slots are left out; the dot only sits between armor and held items.
	 * The set's name and count follow (the count green at 4/4, both yellow when the bonus is not met); with no armor
	 * and nothing held, "No armor".
	 */
	private static void gear(LocalPlayer p, List<Panel.Line> lines) {
		List<Panel.Piece> strip = new ArrayList<>(9);
		boolean armor = false, dotted = false;
		for (int i = 0; i < GEAR.length; i++) {
			ItemStack s = p.getItemBySlot(GEAR[i]);
			if (s.isEmpty()) continue;
			if (i < ARMOR.length) {
				armor = true;
			} else if (armor && !dotted) {
				strip.add(new Panel.Piece(null, DOT, GREY));
				dotted = true;
			}
			strip.add(piece(i, s));
		}
		ArmorSet set = armorSet(p);
		boolean unmet = set.bonusUnmet();
		if (set.worn() > 0) {
			// A leading space evens the gap after the item icons, which sit tight.
			Font font = Minecraft.getInstance().font;
			String name = Fit.cut(" " + set.name(), MAX_NAME_W, font::width);
			strip.add(new Panel.Piece(null, name, unmet ? Panel.YELLOW : Panel.WHITE));
			strip.add(new Panel.Piece(null, set.count(), set.matching() == 4 ? Panel.GREEN : unmet ? Panel.YELLOW : Panel.WHITE));
		} else if (strip.isEmpty()) {
			strip.add(new Panel.Piece(null, set.name(), GREY));
		}
		lines.add(Panel.Line.pieces(strip));
		if (unmet) {
			lines.add(new Panel.Line(NO_BONUS, Panel.YELLOW).withLoose());
		} else if (!set.bonus().isEmpty()) {
			// The bonus is on when the stated requirement is met; with none stated, at a full set.
			boolean on = set.required() > 0 || set.matching() == 4;
			lines.add(new Panel.Line(set.bonus(), on ? GREY : DIM).withLoose());
		}
	}

	/** An item of the gear row, with a wear bar only while it wears out (see {@link StatusFormat#wear}). */
	private static Panel.Piece piece(int slot, ItemStack s) {
		if (s != checked[slot]) {
			checked[slot] = s;
			unbreakable[slot] = s.has(DataComponents.UNBREAKABLE) || StatusFormat.unbreakableLore(lore(s));
		}
		double left = s.isDamageableItem() && s.getMaxDamage() > 0 ? 1 - s.getDamageValue() / (double) s.getMaxDamage() : -1;
		double wear = StatusFormat.wear(left, unbreakable[slot]);
		return new Panel.Piece(s, "", Panel.WHITE, wear, wear < 0 ? 0 : StatusFormat.wearColor(wear));
	}
}
