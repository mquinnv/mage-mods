package net.mage.cubewheel.compat.jade;

import java.util.Optional;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.manamobs.ModelMobRegistry;
import net.mage.cubewheel.tracker.local.ModelMob;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Optional Jade integration, loaded only through the {@code "jade"} entrypoint in {@code fabric.mod.json}; nothing
 * outside this package references Jade (JadeIsolationTest), so CubeWheel runs without it.
 *
 * <p>Jade's own ray never lands on a ManaCube model mob with its default config: {@code RayTracing.canBeTarget}
 * skips the {@code hide-entities} types ({@code interaction}, {@code text_display}, {@code area_effect_cloud}, ...)
 * and invisible entities, and the {@code item_display} bones are zero-size, so it passes through to the block behind.
 * The ray-trace callback therefore reads vanilla's crosshair result ({@code Minecraft.hitResult}, computed every frame,
 * and it does hit interactions): when that entity is part of a registry mob, the accessor is rebuilt for the mob's
 * interaction hitbox, whatever Jade found (block, nothing, or an entity behind it). If Jade's own ray did land on a
 * mob part (a player who unhid interactions), that is used first. Either way there is one stable target per mob, and
 * {@link ManaMobNameProvider} names it by its tag. Jade applies no hide check after the callbacks, and the provider
 * is registered on {@code Entity.class}, so it fires for the (non-living) interaction. With {@code manaNames.jade} off,
 * outside a mana world, or for anything the registry does not know, Jade's accessor and tooltip are left untouched.
 * The decision itself is {@link Retarget#decide}.
 */
public final class CubeWheelJadePlugin implements IWailaPlugin {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static boolean loggedFailure;

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.addRayTraceCallback((hit, accessor, original) -> retarget(registration, accessor));
		registration.registerEntityComponent(ManaMobNameProvider.INSTANCE, Entity.class);
	}

	/** {@code accessor} re-pointed at the looked-at model mob's hitbox, or unchanged when there is nothing to do. */
	private static Accessor<?> retarget(IWailaClientRegistration registration, Accessor<?> accessor) {
		try {
			if (!enabled()) return accessor;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return accessor;
			EntityAccessor jade = accessor instanceof EntityAccessor ea ? ea : null;
			Entity jadeEntity = jade == null ? null : jade.getEntity();
			HitResult vanillaHit = mc.hitResult;
			EntityHitResult vanilla = vanillaHit instanceof EntityHitResult ehr ? ehr : null;
			Optional<Retarget.Redirect> redirect = Retarget.decide(
					jadeEntity == null ? null : jadeEntity.getId(),
					vanilla == null ? null : vanilla.getEntity().getId(),
					ModelMobRegistry::byEntity);
			if (redirect.isEmpty()) return accessor;
			Entity hitbox = mc.level.getEntity(redirect.get().hitbox());
			if (hitbox == null) return accessor; // not loaded on this client: keep what Jade found
			var location = redirect.get().viaVanilla() ? vanilla.getLocation() : jade.getHitResult().getLocation();
			// entityAccessor() pre-fills level, player, serverConnected, serverData and showDetails, as Jade's own
			// entity path does; requireVerification matches it too.
			return registration.entityAccessor()
					.entity(hitbox)
					.hit(new EntityHitResult(hitbox, location))
					.requireVerification()
					.build();
		} catch (VirtualMachineError e) {
			throw e;
		} catch (RuntimeException e) {
			if (!loggedFailure) {
				loggedFailure = true;
				LOG.warn("[cubewheel] Jade mana-mob redirect failed; leaving Jade's target as is", e);
			}
			return accessor;
		}
	}

	private static boolean enabled() {
		return CubeWheelClient.config().current().manaNames.jade;
	}

	/** The model mob {@code e} belongs to, when the Jade toggle is on; the registry is empty outside a mana world. */
	static Optional<ModelMob> mobFor(Entity e) {
		if (e == null || !enabled()) return Optional.empty();
		return ModelMobRegistry.byEntity(e.getId());
	}
}
