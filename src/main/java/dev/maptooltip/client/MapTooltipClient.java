package dev.maptooltip.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MapTooltipClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        ReplacementManager.get().load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.map-tooltip.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.map-tooltip"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(MapTooltipClient::onTick);
    }

    private static void onTick(MinecraftClient client) {
        while (openKey.wasPressed()) {
            if (client.player != null && client.currentScreen == null) {
                client.setScreen(new MapTooltipScreen(null));
            }
        }
    }
}
