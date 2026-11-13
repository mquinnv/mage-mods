package net.mage.cubewheel.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.manamobs.ModelMobRegistry;
import net.mage.cubewheel.tracker.local.ModelMob;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
 * {@link ManaMobNameProvider} names it by its tag. The interaction is much smaller than the drawn model, so when
 * neither hit is a mob part, the look is also ray-tested against each registry mob's model box at its hitbox's live
 * position, up to the player's entity reach and never past what vanilla's crosshair hit ({@link Retarget#rayPick}).
 * Jade applies no hide check after the callbacks, and the provider
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
			Entity hitbox;
			Vec3 location;
			if (redirect.isPresent()) {
				hitbox = mc.level.getEntity(redirect.get().hitbox());
				if (hitbox == null) return accessor; // not loaded on this client: keep what Jade found
				location = redirect.get().viaVanilla() ? vanilla.getLocation() : jade.getHitResult().getLocation();
			} else {
				Entity camera = mc.getCameraEntity();
				if (camera == null || mc.player == null) return accessor;
				Vec3 eye = camera.getEyePosition(1.0f);
				Vec3 look = camera.getViewVector(1.0f);
				double range = mc.player.entityInteractionRange();
				double occluder = vanillaHit == null || vanillaHit.getType() == HitResult.Type.MISS
						? Double.POSITIVE_INFINITY : eye.distanceTo(vanillaHit.getLocation());
				List<Retarget.Candidate> candidates = new ArrayList<>();
				for (ModelMob m : ModelMobRegistry.mobs()) {
					Entity e = mc.level.getEntity(m.hitbox());
					if (e == null) continue;
					// a map lookup per registry mob (a few dozen at most); rayPick drops boxes beyond reach
					candidates.add(new Retarget.Candidate(m.hitbox(), m.bounds().move(e.getX(), e.getY(), e.getZ())));
				}
				if (candidates.isEmpty()) return accessor;
				Optional<Retarget.Pick> pick = Retarget.rayPick(eye.x, eye.y, eye.z, look.x, look.y, look.z, range, occluder,
						candidates);
				if (pick.isEmpty()) return accessor;
				hitbox = mc.level.getEntity(pick.get().hitbox());
				if (hitbox == null) return accessor;
				location = eye.add(look.normalize().scale(pick.get().distance()));
			}
			// entityAccessor() pre-fills level, player, serverConnected, serverData and showDetails, as Jade's own
			// entity path does; requireVerification matches it too.
			return registration.entityAccessor()
					.entity(hitbox)
					.hit(new EntityHitResult(hitbox, location))
					.requireVerification()
					.build();
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable e) {
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
