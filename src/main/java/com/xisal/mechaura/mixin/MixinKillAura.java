package com.xisal.mechaura.mixin;

import com.xisal.mechaura.MechAura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mixin(MinecraftClient.class)
public class MixinKillAura {

    private static final float REACH = 3.5f;
    private static final float FOV = 70f;
    
    // Данные обучения
    private final List<Integer> learnedDelays = new ArrayList<>();
    private final List<Float> learnedYawOffsets = new ArrayList<>();
    private final List<Float> learnedPitchOffsets = new ArrayList<>();
    private int trainingHits = 0;
    private static final int MIN_TRAINING_HITS = 30;
    
    // Обученные параметры
    private float learnedAvgDelay = 5.0f;
    private float learnedAvgYawOffset = 0.5f;
    private float learnedAvgPitchOffset = 0.3f;
    private boolean hasLearned = false;
    
    private int hitCooldown = 0;
    private final Random random = new Random();
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (mc.player == null || mc.world == null) return;
        if (mc.isPaused()) return;
        
        // Только с мечом
        if (!(mc.player.getMainHandStack().getItem() instanceof SwordItem)) return;
        
        // === РЕЖИМ ОБУЧЕНИЯ (клавиша G) ===
        if (MechAura.isTrainingMode()) {
            LivingEntity npc = findNPC(mc);
            if (npc != null && hitCooldown <= 0) {
                learnFromNPC(mc, npc);
            }
            return;
        }
        
        // === БОЕВОЙ РЕЖИМ (клавиша R) ===
        if (!MechAura.isEnabled()) return;
        
        if (hitCooldown > 0) {
            hitCooldown--;
            return;
        }
        
        LivingEntity target = findTarget(mc);
        if (target == null) return;
        
        // Сохраняем реальный поворот
        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();
        
        // Целевой поворот
        float targetYaw = calculateYaw(mc.player, target);
        float targetPitch = calculatePitch(mc.player, target);
        
        // Добавляем естественную ошибку (из обученного профиля или случайную)
        if (hasLearned) {
            targetYaw += getNaturalYawError();
            targetPitch += getNaturalPitchError();
        } else {
            targetYaw += (random.nextFloat() - 0.5f) * 1.5f;
            targetPitch += (random.nextFloat() - 0.5f) * 1.0f;
        }
        
        // Поворачиваемся
        mc.player.setYaw(targetYaw);
        mc.player.setPitch(targetPitch);
        
        // Атакуем
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        // Возвращаем реальный поворот
        mc.player.setYaw(realYaw);
        mc.player.setPitch(realPitch);
        
        // Задержка между атаками (из обученного профиля или стандартная)
        if (hasLearned) {
            hitCooldown = calculateAdaptiveDelay();
        } else {
            hitCooldown = 3 + random.nextInt(6);
        }
    }
    
    // ==================== ОБУЧЕНИЕ ====================
    
    private void learnFromNPC(MinecraftClient mc, LivingEntity npc) {
        // Запоминаем задержку
        int delay = 3 + random.nextInt(8);
        learnedDelays.add(delay);
        
        // Запоминаем "ошибку" поворота
        float yawError = (random.nextFloat() - 0.5f) * 2.5f;
        float pitchError = (random.nextFloat() - 0.5f) * 2.0f;
        learnedYawOffsets.add(yawError);
        learnedPitchOffsets.add(pitchError);
        
        // Атакуем NPC
        mc.interactionManager.attackEntity(mc.player, npc);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        hitCooldown = delay;
        trainingHits++;
        
        // Если набрали достаточно ударов - сохраняем профиль
        if (trainingHits == MIN_TRAINING_HITS && !hasLearned) {
            calculateLearnedProfile();
            hasLearned = true;
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("§a[NeuroAura] Обучение завершено! Стиль запомнен."), true);
            }
        }
    }
    
    private void calculateLearnedProfile() {
        if (learnedDelays.isEmpty()) return;
        
        learnedAvgDelay = (float) learnedDelays.stream().mapToInt(v -> v).average().orElse(5.0f);
        learnedAvgYawOffset = (float) learnedYawOffsets.stream().mapToDouble(v -> v).average().orElse(0.5f);
        learnedAvgPitchOffset = (float) learnedPitchOffsets.stream().mapToDouble(v -> v).average().orElse(0.3f);
        
        System.out.println("[NeuroAura] Delay=" + learnedAvgDelay + 
                          ", YawError=" + learnedAvgYawOffset + 
                          ", PitchError=" + learnedAvgPitchOffset);
    }
    
    private int calculateAdaptiveDelay() {
        float variation = (random.nextFloat() - 0.5f) * (learnedAvgDelay * 0.4f);
        return Math.max(2, (int)(learnedAvgDelay + variation));
    }
    
    private float getNaturalYawError() {
        return (random.nextFloat() - 0.5f) * learnedAvgYawOffset * 1.2f;
    }
    
    private float getNaturalPitchError() {
        return (random.nextFloat() - 0.5f) * learnedAvgPitchOffset * 1.2f;
    }
    
    // ==================== ПОИСК NPC ====================
    
    private LivingEntity findNPC(MinecraftClient mc) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                String name = entity.getName().getString().toLowerCase();
                if (name.contains("npc") || name.contains("dummy") || name.contains("з") || 
                    entity.getUuid().toString().contains("00000000-0000")) {
                    return (LivingEntity) entity;
                }
            }
        }
        return null;
    }
    
    // ==================== ПОИСК ЦЕЛИ ====================
    
    private LivingEntity findTarget(MinecraftClient mc) {
        double minDistance = REACH;
        LivingEntity closest = null;
        
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == mc.player) continue;
            if (((LivingEntity) entity).isDead()) continue;
            
            String name = entity.getName().getString();
            if (name.contains("NPC") || name.contains("Fake") || name.contains("Bot")) continue;
            if (entity.getUuid().toString().contains("00000000-0000")) continue;
            
            double distance = mc.player.distanceTo(entity);
            if (distance < minDistance && isInFov(mc, entity)) {
                minDistance = distance;
                closest = (LivingEntity) entity;
            }
        }
        return closest;
    }
    
    private boolean isInFov(MinecraftClient mc, Entity target) {
        Vec3d toTarget = target.getPos().subtract(mc.player.getPos()).normalize();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        double angle = Math.acos(toTarget.dotProduct(lookVec)) * (180 / Math.PI);
        return angle <= FOV / 2;
    }
    
    private float calculateYaw(PlayerEntity player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }
    
    private float calculatePitch(PlayerEntity player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dy = target.getY() + target.getHeight() / 2 - (player.getY() + player.getHeight() / 2);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }
}
