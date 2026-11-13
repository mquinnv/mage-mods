package net.mage.cubewheel.compat.jade;

import java.util.Optional;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.manamobs.ModelMobRegistry;
import net.mage.cubewheel.tracker.local.ModelMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Optional Jade integration, loaded only through the {@code "jade"} entrypoint in {@code fabric.mod.json}; nothing
 * outside this package references Jade (JadeIsolationTest), so CubeWheel runs without it. In a mana world, a look at
 * any part of a ManaCube custom-model mob is redirected to the mob's interaction hitbox (one stable target per mob),
 * and {@link ManaMobNameProvider} names it by its tag. With {@code manaNames.jade} off, or for anything the registry
 * does not know, Jade's accessor and tooltip are left untouched.
 */
public final class CubeWheelJadePlugin implements IWailaPlugin {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static boolean loggedFailure;

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.addRayTraceCallback((hit, accessor, original) -> retarget(registration, accessor));
		registration.registerEntityComponent(ManaMobNameProvider.INSTANCE, Entity.class);
	}

	/** {@code accessor} re-pointed at its model mob's hitbox, or unchanged when there is nothing to redirect. */
	private static Accessor<?> retarget(IWailaClientRegistration registration, Accessor<?> accessor) {
		try {
			if (!(accessor instanceof EntityAccessor ea)) return accessor;
			Entity looked = ea.getEntity();
			Optional<ModelMob> mob = mobFor(looked);
			if (mob.isEmpty() || mob.get().hitbox() == looked.getId()) return accessor;
			Entity hitbox = ea.getLevel().getEntity(mob.get().hitbox());
			if (hitbox == null) return accessor; // not loaded on this client: keep what Jade found
			return registration.entityAccessor()
					.from(ea)
					.entity(hitbox)
					.hit(new EntityHitResult(hitbox, ea.getHitResult().getLocation()))
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

	/** The model mob {@code e} belongs to, when the Jade toggle is on; the registry is empty outside a mana world. */
	static Optional<ModelMob> mobFor(Entity e) {
		if (e == null || !CubeWheelClient.config().current().manaNames.jade) return Optional.empty();
		return ModelMobRegistry.byEntity(e.getId());
	}
}
