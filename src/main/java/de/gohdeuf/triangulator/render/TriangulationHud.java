package de.gohdeuf.triangulator.render;

import com.mojang.blaze3d.platform.Window;
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

    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final int MOUSE_RELEASE = 0;
    private static final int MOUSE_PRESS = 1;
    private static boolean editing;
    private static boolean dragging;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static int panelWidth;
    private static int panelHeight;

    private TriangulationHud() {
    }

    public static boolean isEditing() {
        return editing;
    }

    public static void setEditing(boolean value) {
        editing = value;
        if (!editing) {
            dragging = false;
            TriConfig.save();
        }
    }

    public static boolean handleMouseButton(int button, int action, double mouseX, double mouseY) {
        if (!editing || button != LEFT_MOUSE_BUTTON) return false;

        if (action == MOUSE_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null || mc.player == null || mc.gui.hud.isHidden()) return false;

            TriConfig config = TriConfig.get();
            if (mouseX < config.hudX || mouseX > config.hudX + panelWidth
                    || mouseY < config.hudY || mouseY > config.hudY + panelHeight) {
                return false;
            }
            dragging = true;
            dragOffsetX = (int) mouseX - config.hudX;
            dragOffsetY = (int) mouseY - config.hudY;
            return true;
        }

        if (action == MOUSE_RELEASE && dragging) {
            dragging = false;
            TriConfig.save();
            return true;
        }
        return false;
    }

    public static void handleMouseMove(double mouseX, double mouseY) {
        if (!editing || !dragging) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null || mc.gui.hud.isHidden()) {
            dragging = false;
            TriConfig.save();
            return;
        }

        Window window = mc.getWindow();
        TriConfig config = TriConfig.get();
        config.hudX = Math.max(0, Math.min((int) mouseX - dragOffsetX,
                window.getGuiScaledWidth() - panelWidth));
        config.hudY = Math.max(0, Math.min((int) mouseY - dragOffsetY,
                window.getGuiScaledHeight() - panelHeight));
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("locator-triangulator", "hud"),
                (graphics, deltaTracker) -> render(graphics, deltaTracker)
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!TriConfig.get().hudEnabled && !editing) return;
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
        if (editing) {
            lines.add(new HudLine(Component.translatable(
                    "hud.triangulator.move", TriangulatorMod.getMoveHudKeyName()).getString(), 0xFF55FF55));
        }
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

        int textWidth = 0;
        for (HudLine line : lines) {
            textWidth = Math.max(textWidth, mc.font.width(Objects.requireNonNull(line.text())));
        }
        panelWidth = textWidth + 12;
        panelHeight = lines.size() * 12 + 8;
        Window window = mc.getWindow();
        TriConfig config = TriConfig.get();
        int x = Math.max(0, Math.min(config.hudX, window.getGuiScaledWidth() - panelWidth));
        int y = Math.max(0, Math.min(config.hudY, window.getGuiScaledHeight() - panelHeight));
        if (x != config.hudX || y != config.hudY) {
            config.hudX = x;
            config.hudY = y;
            TriConfig.save();
        }
        graphics.fill(x, y, x + panelWidth, y + panelHeight, editing ? 0xC0000000 : 0xA0000000);
        graphics.fill(x, y, x + 2, y + panelHeight, editing ? 0xFF55FF55 : 0xFF55AAFF);
        for (HudLine line : lines) {
            graphics.text(mc.font, line.text(), x + 7, y + 4, line.color(), false);
            y += 12;
        }
    }

    private record HudLine(String text, int color) {
    }
}
