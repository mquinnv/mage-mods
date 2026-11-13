package net.mage.cubewheel.compat.jade;

import java.util.Optional;
import java.util.OptionalDouble;
import net.mage.cubewheel.manamobs.ModelMobRegistry;
import net.mage.cubewheel.tracker.local.ModelMob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

/**
 * For an entity the mana mob registry knows (in practice the model's {@code minecraft:interaction} hitbox, which
 * {@link CubeWheelJadePlugin}'s ray-trace callback makes every look land on): the object name becomes the tag's bare
 * name ("Mana Wolf"), Jade's own health line goes, and the tag's health, when it shows one, is added in its place.
 * Runs at {@link #PRIORITY}, after Jade's object name (-10100) and entity health (-8000), and inside the same
 * |priority| &gt; 5000 band so Jade's Lite display mode keeps it in the main tooltip with them.
 */
final class ManaMobNameProvider implements IEntityComponentProvider {
	static final ManaMobNameProvider INSTANCE = new ManaMobNameProvider();
	static final Identifier UID = Identifier.fromNamespaceAndPath("cubewheel", "mana_mob_name");
	static final int PRIORITY = -7900;

	private ManaMobNameProvider() {}

	@Override
	public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
		Optional<ModelMob> found = CubeWheelJadePlugin.mobFor(accessor.getEntity());
		if (found.isEmpty()) return;
		ModelMob mob = found.get();
		tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(Component.literal(mob.name())));
		tooltip.remove(JadeIds.MC_ENTITY_HEALTH);
		OptionalDouble health = ModelMobRegistry.liveHealth(mob);
		if (health.isPresent()) {
			tooltip.add(Component.literal("❤ ").withStyle(ChatFormatting.RED)
					.append(Component.literal(HealthText.format(health.getAsDouble())).withStyle(ChatFormatting.WHITE)));
		}
	}

	@Override
	public Identifier getUid() {
		return UID;
	}

	@Override
	public int getDefaultPriority() {
		return PRIORITY;
	}

	/** CubeWheel's own {@code manaNames.jade} toggle governs this, so Jade's per-provider switch is not offered. */
	@Override
	public boolean isRequired() {
		return true;
	}
}
