package com.mage.cubewheel.sva.mc;

import com.mage.cubewheel.sva.LegacyText;
import com.mage.cubewheel.sva.Sva;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/** Icon stacks and styled text for SVAs. Client thread only. */
final class SvaItems {
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	private SvaItems() {}

	/** Drops cached icons (the server resource pack, and so the item models, may have changed). */
	static void clearIcons() {
		ICONS.clear();
	}

	/**
	 * The vanilla item for {@code material}, wearing the SVA's resource-pack model when the client has it
	 * (ManaCube's server pack), else the plain item. Unknown materials show as paper.
	 */
	static ItemStack icon(Sva sva) {
		return ICONS.computeIfAbsent(sva.itemType(), k -> build(sva));
	}

	private static ItemStack build(Sva sva) {
		Identifier id = sva.itemId().isEmpty() ? null : Identifier.tryParse("minecraft:" + sva.itemId());
		Item item = id == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
		if (item == Items.AIR) item = Items.PAPER;
		ItemStack stack = new ItemStack(item);
		Identifier model = sva.itemModel() == null ? null : Identifier.tryParse(sva.itemModel());
		if (model != null && modelExists(model)) stack.set(DataComponents.ITEM_MODEL, model);
		if (sva.leatherColor() >= 0) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(sva.leatherColor()));
		if (!sva.enchants().isEmpty()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** ModelManager answers unknown ids with its shared MissingItemModel. */
	private static boolean modelExists(Identifier model) {
		ModelManager mm = Minecraft.getInstance().getModelManager();
		ItemModel found = mm.getItemModel(model);
		return found != null && !(found instanceof MissingItemModel);
	}

	/** A legacy-coded string as a styled component (hex colours included). */
	static MutableComponent text(String legacy) {
		MutableComponent out = Component.empty();
		for (LegacyText.Span s : LegacyText.parse(legacy)) {
			Style st = Style.EMPTY.withItalic(s.italic()).withBold(s.bold()).withUnderlined(s.underlined())
					.withStrikethrough(s.strikethrough()).withObfuscated(s.obfuscated());
			if (s.rgb() >= 0) st = st.withColor(TextColor.fromRgb(s.rgb()));
			out.append(Component.literal(s.text()).withStyle(st));
		}
		return out;
	}

	/** Plain strings of an item's lore (for disambiguating same-named SVAs). */
	static List<String> plainLore(ItemStack stack) {
		var lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		return lore.lines().stream().map(Component::getString).toList();
	}
}
