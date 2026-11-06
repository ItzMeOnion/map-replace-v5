package dev.maptooltip.client.mixin;

import dev.maptooltip.client.ReplacementManager;
import net.minecraft.client.render.MapRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps in our image just before Minecraft reads a map's pixels.
 *
 * Minecraft has changed how maps are drawn between versions, so this hooks both the older
 * draw(...) and the newer update(...) method. Each hook is optional (require = 0): whichever
 * exists in this build is used, and the log shows which one fired.
 *
 * Only this client sees the change; the server and the saved world are untouched.
 */
@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {

    @Inject(
            method = "draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/component/type/MapIdComponent;Lnet/minecraft/item/map/MapState;ZI)V",
            at = @At("HEAD"),
            require = 0
    )
    private void maptooltip$drawHook(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                     MapIdComponent mapId, MapState state, boolean hidden, int light,
                                     CallbackInfo ci) {
        if (mapId != null && state != null) {
            ReplacementManager.get().applyTo(mapId.id(), state, "draw");
        }
    }

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void maptooltip$updateHook(MapIdComponent mapId, MapState state, CallbackInfo ci) {
        if (mapId != null && state != null) {
            ReplacementManager.get().applyTo(mapId.id(), state, "update");
        }
    }
}
