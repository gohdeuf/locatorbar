package de.gohdeuf.triangulator.mixin;

import de.gohdeuf.triangulator.render.TriangulationHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class HudPositionMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void triangulator$handleHudDrag(long window, MouseButtonInfo buttonInfo, int action,
                                            CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouse = (MouseHandler) (Object) this;
        double x = MouseHandler.getScaledXPos(minecraft.getWindow(), mouse.xpos());
        double y = MouseHandler.getScaledYPos(minecraft.getWindow(), mouse.ypos());
        if (TriangulationHud.handleMouseButton(buttonInfo.button(), action, x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void triangulator$moveHud(long window, double x, double y, double deltaX, double deltaY,
                                      CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        double scaledX = MouseHandler.getScaledXPos(minecraft.getWindow(), x);
        double scaledY = MouseHandler.getScaledYPos(minecraft.getWindow(), y);
        TriangulationHud.handleMouseMove(scaledX, scaledY);
    }
}
