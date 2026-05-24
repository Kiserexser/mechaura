package com.xisal.mechaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MechAura implements ClientModInitializer {
    public static final String MOD_ID = "mechaura";
    private static boolean enabled = true;
    public static KeyBinding toggleKey;
    
    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mechaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.mechaura"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§b[MechAura] §f" + (enabled ? "§aВключена" : "§cВыключена")), true);
                }
            }
        });
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
}
