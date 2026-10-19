package de.gohdeuf.triangulator.triangulation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.TrackedWaypoint;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.gohdeuf.triangulator.LocatorBarTriangulator;
import de.gohdeuf.triangulator.config.TriConfig;
import de.gohdeuf.triangulator.waypoint.TrackedTarget;
import de.gohdeuf.triangulator.waypoint.WaypointStore;

/**
 * Wertet eingehende Waypoint-Pakete aus und steuert die Neuberechnung.
 * Aufgerufen vom Netzwerk-Thread (Mixin) und vom Client-Tick.
 */
public final class TriangulationService {

    private static final TriangulationService INSTANCE = new TriangulationService();

    public static TriangulationService getInstance() {
        return INSTANCE;
    }

    private int tickCounter;

    private TriangulationService() {
    }

    public void start() {
        WaypointStore.getInstance().clear();
    }

    /** Wird vom ClientPacketListenerMixin aufgerufen (Netzwerk-Thread!). */
    public void onPacket(ClientboundTrackedWaypointPacket packet) {
        try {
            handle(packet);
        } catch (RuntimeException e) {
            LocatorBarTriangulator.LOGGER.error("Unable to process tracked waypoint packet", e);
        }
    }

    private void handle(ClientboundTrackedWaypointPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        TrackedWaypoint waypoint = packet.waypoint();

        String id = waypoint.id().left()
                .map(uuid -> uuid.toString())
                .orElseGet(() -> waypoint.id().right().orElseThrow());
        Object operation = packet.operation();
        if (operation.toString().equals("UNTRACK")) {
            WaypointStore.getInstance().remove(id);
            return;
        }

        UUID uuid = waypoint.id().left().orElse(null);
        TrackedTarget target = WaypointStore.getInstance().getOrCreate(id, uuid, resolveName(mc, uuid));
        target.updateName(resolveName(mc, uuid));
        int color = waypoint.icon().color.orElse(-1);
        if (color != -1) target.color = color;

        TrackedTarget.DirectPosition directPosition = extractDirectPosition(waypoint);
        if (directPosition != null) {
            target.setDirectPosition(directPosition.x(), directPosition.z(), directPosition.approximate());
            return;
        }

        Double azimuth = extractAzimuth(waypoint);
        if (azimuth != null) {
            target.clearDirectPosition();
            LocalPlayer player = mc.player;
            if (player == null) return;
            // Normierung auf [-pi, pi)
            double theta = Math.atan2(Math.sin(azimuth), Math.cos(azimuth));
            long now = System.currentTimeMillis();
            target.addSample(new BearingSample(player.getX(), player.getZ(), theta, now),
                    TriConfig.get().maxSamplesPerTarget);
        }
    }

    /** Laeuft auf dem Client-Tick: verwirft alte Samples und rechnet neu. */
    public void tick(Minecraft mc) {
        if (++tickCounter < TriConfig.get().recalcIntervalTicks) return;
        tickCounter = 0;

        long cutoff = System.currentTimeMillis() - TriConfig.get().sampleTtlMillis;
        for (TrackedTarget t : WaypointStore.getInstance().all()) {
            t.updateName(resolveName(mc, t.playerUuid));
            t.removeSamplesOlderThan(cutoff);
            if (t.sampleCount() < TriConfig.get().minSamplesForEstimate) {
                t.setEstimate(java.util.Optional.empty());
                continue;
            }
            List<LeastSquaresTriangulator.WeightedSample> weighted = new ArrayList<>();
            for (BearingSample s : t.snapshot()) {
                weighted.add(new LeastSquaresTriangulator.WeightedSample(s, weight(s)));
            }
            t.setEstimate(LeastSquaresTriangulator.solve(weighted));
        }
    }

    /** Frische-Gewicht: juenger => staerker. */
    private static double weight(BearingSample s) {
        long age = System.currentTimeMillis() - s.timestampMillis();
        return 1.0 / (1.0 + age / 10_000.0);
    }

    private static String resolveName(Minecraft mc, UUID uuid) {
        if (uuid == null) return null;
        ClientPacketListener listener = mc.getConnection();
        if (listener != null) {
            var entry = listener.getPlayerInfo(uuid);
            if (entry != null && entry.getProfile() != null) {
                return entry.getProfile().name();
            }
        }
        if (mc.level != null) {
            for (var player : mc.level.players()) {
                if (player.getUUID().equals(uuid)) {
                    return player.getName().getString();
                }
            }
        }
        return null;
    }

    private static TrackedTarget.DirectPosition extractDirectPosition(TrackedWaypoint waypoint) {
        String waypointType = waypoint.getClass().getSimpleName();
        String fieldName;
        boolean approximate;
        if (waypointType.equals("Vec3iWaypoint")) {
            fieldName = "vector";
            approximate = false;
        } else if (waypointType.equals("ChunkWaypoint")) {
            fieldName = "chunkPos";
            approximate = true;
        } else {
            return null;
        }

        try {
            Field field = waypoint.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(waypoint);
            if (!approximate && value instanceof Vec3i vector) {
                return new TrackedTarget.DirectPosition(
                        vector.getX() + 0.5, vector.getZ() + 0.5, false);
            }
            if (approximate && value instanceof ChunkPos chunk) {
                return new TrackedTarget.DirectPosition(
                        chunk.getMiddleBlockX() + 0.5, chunk.getMiddleBlockZ() + 0.5, true);
            }
            LocatorBarTriangulator.LOGGER.warn("Unexpected value in {} waypoint field", fieldName);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LocatorBarTriangulator.LOGGER.error("Unable to read {} waypoint position", fieldName, e);
        }
        return null;
    }

    /** Liest den Winkel des nicht öffentlich zugänglichen AzimuthWaypoint-Untertyps. */
    private static Double extractAzimuth(TrackedWaypoint waypoint) {
        if (!waypoint.getClass().getSimpleName().equals("AzimuthWaypoint")) return null;
        try {
            Field angle = waypoint.getClass().getDeclaredField("angle");
            angle.setAccessible(true);
            Object value = angle.get(waypoint);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LocatorBarTriangulator.LOGGER.error("Unable to read azimuth from tracked waypoint", e);
            return null;
        }
    }
}
