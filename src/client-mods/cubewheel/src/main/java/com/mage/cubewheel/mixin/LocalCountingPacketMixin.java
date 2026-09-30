package com.mage.cubewheel.mixin;

import com.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Local counting: reads damage and death packets (never changes or cancels them). Injected right after
 * PacketUtils.ensureRunningOnSameThread, so it runs once, on the client thread: at HEAD the handler is
 * first entered on the network thread and re-queued. Optional: if it does not apply, kills are not counted.
 */
@Mixin(ClientPacketListener.class)
public abstract class LocalCountingPacketMixin {
	private static final String ENSURE = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
			+ "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Inject(method = "handleDamageEvent", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
	private void cubewheel$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		try {
			LocalSignals.onDamageEvent(packet.entityId(), packet.sourceCauseId());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.DAMAGE, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break packet handling
			}
		}
	}

	/** After the data is applied (RETURN is only reached on the client thread): stacked-mob name changes. */
	@Inject(method = "handleSetEntityData", at = @At("RETURN"))
	private void cubewheel$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
		try {
			LocalSignals.onEntityData(packet.id());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.STACK, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break packet handling
			}
		}
	}

	/** Before the entities are removed, so they can still be described in capture. */
	@Inject(method = "handleRemoveEntities", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
	private void cubewheel$remove(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
		try {
			LocalSignals.onRemoveEntities(packet.getEntityIds());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.REMOVE, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break packet handling
			}
		}
	}

	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
	private void cubewheel$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		try {
			if (packet.getEventId() != 3) return; // EntityEvent.DEATH; skip the lookup for everything else
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) LocalSignals.onEntityEvent(packet.getEntity(mc.level), packet.getEventId());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				LocalSignals.fail(LocalSignals.Hook.DEATH, t);
			} catch (Throwable ignored) {
				// LocalSignals itself is unusable: stay silent rather than break packet handling
			}
		}
	}
}
