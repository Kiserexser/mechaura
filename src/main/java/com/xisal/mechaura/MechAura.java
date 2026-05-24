package com.xisal.mechaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MechAura implements ClientModInitializer {
    private static boolean enabled = true;
    private static boolean trainingMode = false;
    public static KeyBinding toggleKey;
    public static KeyBinding trainingKey;
    
    @Override
    public void onInitializeClient() {
        // Клавиша R - вкл/выкл KillAura
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mechaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.mechaura"
        ));
        
        // Клавиша G - режим обучения (бьём NPC)
        trainingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mechaura.train",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.mechaura"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§b[MechAura] §f" + (enabled ? "§aON" : "§cOFF")), true);
                }
            }
            
            if (trainingKey.wasPressed()) {
                trainingMode = !trainingMode;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§b[MechAura] §eРежим обучения: " + (trainingMode ? "§aВКЛЮЧЁН" : "§cВЫКЛЮЧЁН")), true);
                }
            }
        });
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static boolean isTrainingMode() {
        return trainingMode;
    }
}
