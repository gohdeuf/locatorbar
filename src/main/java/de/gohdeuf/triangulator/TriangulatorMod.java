package de.gohdeuf.triangulator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

import de.gohdeuf.triangulator.command.TriangulatorCommands;
import de.gohdeuf.triangulator.config.TriConfig;
import de.gohdeuf.triangulator.render.TriangulationHud;
import de.gohdeuf.triangulator.triangulation.TriangulationService;

/**
 * Einstiegspunkt der Client-Mod.
 */
public class TriangulatorMod implements ClientModInitializer {

    public static final String MOD_ID = "triangulator";

    private static KeyMapping toggleHudKey;
    private static KeyMapping moveHudKey;

    public static String getToggleHudKeyName() {
        return toggleHudKey.getTranslatedKeyMessage().getString();
    }

    public static String getMoveHudKeyName() {
        return moveHudKey.getTranslatedKeyMessage().getString();
    }

    @Override
    public void onInitializeClient() {
        TriConfig.load();
        TriangulationService.getInstance().start();

        toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.triangulator.toggleHud",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_J,
                KeyMapping.Category.MISC
        ));
        moveHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.triangulator.moveHud",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_H,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHudKey.consumeClick()) {
                TriConfig.get().hudEnabled = !TriConfig.get().hudEnabled;
                TriConfig.save();
            }
            while (moveHudKey.consumeClick()) {
                boolean editing = !TriangulationHud.isEditing();
                TriangulationHud.setEditing(editing);
                if (editing) {
                    client.mouseHandler.releaseMouse();
                } else if (client.player != null && client.gui.screen() == null) {
                    client.mouseHandler.grabMouse();
                }
                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    editing ? "hud.triangulator.editing_started"
                                            : "hud.triangulator.editing_finished"));
                }
            }
            TriangulationService.getInstance().tick(client);
        });

        TriangulationHud.register();
        TriangulatorCommands.register();
    }
}
