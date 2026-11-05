package de.gohdeuf.triangulator.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import de.gohdeuf.triangulator.config.TriConfig;
import de.gohdeuf.triangulator.TriangulatorMod;
import de.gohdeuf.triangulator.triangulation.LeastSquaresTriangulator;
import de.gohdeuf.triangulator.waypoint.TrackedTarget;
import de.gohdeuf.triangulator.waypoint.WaypointStore;

/**
 * Zeichnet die Schaetzungen oben links ins HUD.
 */
public final class TriangulationHud {

    private TriangulationHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("locator-triangulator", "hud"),
                (graphics, deltaTracker) -> render(graphics, deltaTracker)
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!TriConfig.get().hudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        List<TrackedTarget> targets = new ArrayList<>(WaypointStore.getInstance().all());
        targets.sort(Comparator
                .comparing((TrackedTarget target) -> target.playerUuid,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(target -> target.id));
        List<HudLine> lines = new ArrayList<>();
        lines.add(new HudLine(Component.translatable(
                "hud.triangulator.toggle", TriangulatorMod.getToggleHudKeyName()).getString(), 0xFFFFFFFF));
        if (targets.isEmpty()) {
            lines.add(new HudLine(Component.translatable("hud.triangulator.waiting").getString(), 0xFFFFAA00));
        } else {
            for (TrackedTarget t : targets) {
                var directPosition = t.directPosition();
                if (directPosition.isPresent()) {
                    TrackedTarget.DirectPosition position = directPosition.get();
                    double distance = Math.hypot(position.x() - player.getX(), position.z() - player.getZ());
                    lines.add(new HudLine(
                            Component.translatable("hud.triangulator.direct_position",
                                    t.name(), position.approximate() ? "~" : "",
                                    position.x(), position.z(), distance).getString(),
                            t.color | 0xFF000000));
                    lines.add(new HudLine(
                            Component.translatable(position.approximate()
                                    ? "hud.triangulator.approximate_position"
                                    : "hud.triangulator.direct_position_available").getString(),
                            position.approximate() ? 0xFFFFAA00 : 0xFF55FF55));
                    continue;
                }

                var estimate = t.estimate();
                if (estimate.isPresent()) {
                    LeastSquaresTriangulator.Result r = estimate.get();
                    double dx = r.x() - player.getX();
                    double dz = r.z() - player.getZ();
                    double dist = Math.hypot(dx, dz);
                    lines.add(new HudLine(
                            Component.translatable("hud.triangulator.estimate",
                                    t.name(), r.x(), r.z(), dist, r.rmsResidual()).getString(),
                            t.color | 0xFF000000));
                    lines.add(new HudLine(
                            Component.translatable(r.illConditioned()
                                    ? "hud.triangulator.move_sideways"
                                    : "hud.triangulator.bearings_sufficient").getString(),
                            r.illConditioned() ? 0xFFFFAA00 : 0xFF55FF55));
                } else {
                    int samples = t.sampleCount();
                    int required = TriConfig.get().minSamplesForEstimate;
                    String status = samples == 0
                            ? Component.translatable("hud.triangulator.waiting_for_first_bearing", t.name()).getString()
                            : samples < required
                                    ? Component.translatable("hud.triangulator.change_position",
                                            t.name(), samples, required).getString()
                                    : Component.translatable("hud.triangulator.bearings_too_similar",
                                            t.name()).getString();
                    lines.add(new HudLine(status, 0xFFFFAA00));
                }
            }
        }

        int x = 8;
        int y = 8;
        int textWidth = 0;
        for (HudLine line : lines) {
            textWidth = Math.max(textWidth, mc.font.width(Objects.requireNonNull(line.text())));
        }
        int width = textWidth + 12;
        int height = lines.size() * 12 + 8;
        graphics.fill(x, y, x + width, y + height, 0xA0000000);
        graphics.fill(x, y, x + 2, y + height, 0xFF55AAFF);
        for (HudLine line : lines) {
            graphics.text(mc.font, line.text(), x + 7, y + 4, line.color(), false);
            y += 12;
        }
    }

    private record HudLine(String text, int color) {
    }
}
