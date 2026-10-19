package de.gohdeuf.triangulator.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.gohdeuf.triangulator.triangulation.TriangulationService;

/**
 * Einziger Mixin der Mod: faengt eingehende Locator-Bar-Waypoint-Pakete ab,
 * bevor/wo sie der Vanilla-Client verarbeitet.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleWaypoint", at = @At("HEAD"))
    private void triangulator$onTrackedWaypoint(ClientboundTrackedWaypointPacket packet,
                                                CallbackInfo ci) {
        TriangulationService.getInstance().onPacket(packet);
    }
}
