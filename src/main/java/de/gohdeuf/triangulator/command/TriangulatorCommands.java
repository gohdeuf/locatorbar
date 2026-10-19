package de.gohdeuf.triangulator.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import de.gohdeuf.triangulator.LocatorBarTriangulator;
import de.gohdeuf.triangulator.waypoint.TrackedTarget;
import de.gohdeuf.triangulator.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Client-seitige Befehle (nur lokal, kein Server involviert).
 */
public final class TriangulatorCommands {

    private TriangulatorCommands() {
    }

    @SuppressWarnings("null")
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("triangulator")
                    .then(ClientCommands.literal("clear")
                            .executes(ctx -> {
                                WaypointStore.getInstance().clear();
                                return 1;
                            }))
                    .then(ClientCommands.literal("lizenz")
                            .executes(ctx -> {
                                showLicensePage(ctx.getSource(), 1);
                                return 1;
                            })
                            .then(ClientCommands.argument("seite", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        showLicensePage(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seite"));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("status")
                            .executes(ctx -> {
                                for (TrackedTarget t : WaypointStore.getInstance().all()) {
                                    var est = t.estimate();
                                    String msg = est.map(r -> String.format(
                                                    "%s: X %.0f Z %.0f, %.0fm, ±%.1fm, %d Peilungen",
                                                    t.name(), r.x(), r.z(),
                                                    Math.hypot(r.x(), r.z()),
                                                    r.rmsResidual(), r.sampleCount()))
                                            .orElse(t.name() + ": " + t.sampleCount() + " Peilungen");
                                    ctx.getSource().sendFeedback(Component.literal(Objects.requireNonNull(msg)));
                                }
                                return 1;
                            }))
                    .then(ClientCommands.literal("forget")
                            .then(ClientCommands.argument("id", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String id = StringArgumentType.getString(ctx, "id");
                                        WaypointStore.getInstance().remove(id);
                                        return 1;
                                    }))));
        });
    }

    private static final int LICENSE_LINES_PER_PAGE = 12;

    private static void showLicensePage(FabricClientCommandSource source, int requestedPage) {
        try (InputStream stream = TriangulatorCommands.class.getResourceAsStream(
                "/assets/locator-triangulator/LICENSE.txt")) {
            if (stream == null) {
                throw new IOException("Bundled LICENSE.txt resource is missing");
            }
            String license = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = license.split("\\R", -1);
            int pageCount = (lines.length + LICENSE_LINES_PER_PAGE - 1) / LICENSE_LINES_PER_PAGE;
            if (requestedPage > pageCount) {
                source.sendError(Component.literal("Die Lizenz hat nur " + pageCount + " Seiten."));
                return;
            }

            int firstLine = (requestedPage - 1) * LICENSE_LINES_PER_PAGE;
            int lastLine = Math.min(firstLine + LICENSE_LINES_PER_PAGE, lines.length);
            String page = String.join("\n",
                    java.util.Arrays.copyOfRange(lines, firstLine, lastLine));

            source.sendFeedback(
                    Component.literal("GNU General Public License v3.0  |  Seite "
                            + requestedPage + "/" + pageCount)
                            .withStyle(ChatFormatting.GOLD));
            source.sendFeedback(Component.literal(page));

            MutableComponent navigation = Component.empty();
            if (requestedPage > 1) {
                navigation.append(navigationButton("« Zurück", requestedPage - 1));
            }
            if (requestedPage > 1 && requestedPage < pageCount) {
                navigation.append(Component.literal("  |  "));
            }
            if (requestedPage < pageCount) {
                navigation.append(navigationButton("Weiter »", requestedPage + 1));
            }
            if (!navigation.getString().isEmpty()) {
                source.sendFeedback(navigation);
            }
        } catch (IOException e) {
            LocatorBarTriangulator.LOGGER.error("Unable to open the GPL license text", e);
            source.sendError(Component.literal("Der Lizenztext konnte nicht geladen werden. Details stehen im Log."));
        }
    }

    private static MutableComponent navigationButton(String label, int page) {
        return Component.literal(Objects.requireNonNull(label))
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/triangulator lizenz " + page))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Lizenzseite " + page + " anzeigen"))));
    }
}
