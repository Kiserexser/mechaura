package com.xisal.mechaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MechAura implements ClientModInitializer {
    private static boolean enabled = false;
    private static boolean trainingMode = false;
    
    public static KeyBinding toggleKey;
    public static KeyBinding trainingKey;
    public static KeyBinding spawnNPCKey;
    
    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mechaura.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.mechaura"
        ));
        
        trainingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mechaura.train",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.mechaura"
        ));
        
        spawnNPCKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mechaura.spawnnpc",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "category.mechaura"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal((enabled ? "§a[ON]" : "§c[OFF]") + " §fMechAura"), true);
            }
            
            if (trainingKey.wasPressed()) {
                trainingMode = !trainingMode;
                client.player.sendMessage(Text.literal("§e[Training] §f" + (trainingMode ? "§aStarted" : "§cStopped")), true);
            }
            
            if (spawnNPCKey.wasPressed()) {
                client.player.networkHandler.sendChatCommand("summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"TrainingDummy\"}',CustomNameVisible:1b,Invulnerable:1b,NoGravity:1b,ShowArms:1b}");
                client.player.sendMessage(Text.literal("§a[Spawned] §fTraining NPC"), true);
            }
        });
    }
    
    public static boolean isEnabled() { return enabled; }
    public static boolean isTrainingMode() { return trainingMode; }
}
