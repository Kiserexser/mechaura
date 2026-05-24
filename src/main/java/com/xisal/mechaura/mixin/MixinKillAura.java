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

    // === НАСТРОЙКИ ===
    private static final float REACH = 3.5f;
    private static final float FOV = 70f;
    
    // === ДАННЫЕ ОБУЧЕНИЯ ===
    private boolean isTraining = false;              // Режим обучения (бьём NPC)
    private final List<Integer> learnedDelays = new ArrayList<>();      // Запомненные задержки
    private final List<Float> learnedYawOffsets = new ArrayList<>();    // Запомненные ошибки поворота
    private final List<Float> learnedPitchOffsets = new ArrayList<>();  // Запомненные ошибки наклона
    private int trainingHits = 0;
    private static final int MIN_TRAINING_HITS = 30;   // Минимум ударов для обучения
    
    // === ИСПОЛЬЗУЕМЫЕ ПАРАМЕТРЫ (из обученного профиля) ===
    private float learnedAvgDelay = 5.0f;
    private float learnedAvgYawOffset = 0.5f;
    private float learnedAvgPitchOffset = 0.3f;
    
    private int hitCooldown = 0;
    private final Random random = new Random();
    
    // Переключение режима обучения (вызывается из MechAura)
    public void setTrainingMode(boolean mode) {
        this.isTraining = mode;
        if (!mode && trainingHits >= MIN_TRAINING_HITS) {
            calculateLearnedProfile();
        }
    }
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (mc.player == null || mc.world == null) return;
        if (mc.isPaused()) return;
        
        // Только с мечом
        if (!(mc.player.getMainHandStack().getItem() instanceof SwordItem)) return;
        
        // === РЕЖИМ ОБУЧЕНИЯ (бьём NPC) ===
        if (isTraining) {
            LivingEntity npc = findNPC(mc);
            if (npc != null) {
                learnFromNPC(mc, npc);
            }
            return;
        }
        
        // === БОЕВОЙ РЕЖИМ (используем обученный стиль) ===
        if (!MechAura.isEnabled()) return;
        
        if (hitCooldown > 0) {
            hitCooldown--;
            return;
        }
        
        LivingEntity target = findTarget(mc);
        if (target == null) return;
        
        // Адаптивная задержка (на основе обученного стиля)
        int delay = calculateAdaptiveDelay();
        
        // Естественная ротация (с ошибками, как при обучении)
        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();
        
        float targetYaw = calculateYaw(mc.player, target);
        float targetPitch = calculatePitch(mc.player, target);
        
        // Добавляем обученные ошибки (чтобы не выглядело как робот)
        targetYaw += getNaturalYawError();
        targetPitch += getNaturalPitchError();
        
        // Плавный поворот (не мгновенный)
        mc.player.setYaw(targetYaw);
        mc.player.setPitch(targetPitch);
        
        // Атака
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        // Возвращаем реальный поворот
        mc.player.setYaw(realYaw);
        mc.player.setPitch(realPitch);
        
        hitCooldown = delay;
    }
    
    // ==================== ОБУЧЕНИЕ НА NPC ====================
    
    private void learnFromNPC(MinecraftClient mc, LivingEntity npc) {
        // Запоминаем текущую задержку между атаками
        if (hitCooldown <= 0) {
            int currentDelay = 4 + random.nextInt(8); // 4-12 тиков
            learnedDelays.add(currentDelay);
            
            // Запоминаем "ошибку" поворота (естественное дрожание мыши)
            float yawError = (float)((random.nextFloat() - 0.5) * 2.5);
            float pitchError = (float)((random.nextFloat() - 0.5) * 2.0);
            learnedYawOffsets.add(yawError);
            learnedPitchOffsets.add(pitchError);
            
            // Атакуем NPC
            mc.interactionManager.attackEntity(mc.player, npc);
            mc.player.swingHand(Hand.MAIN_HAND);
            
            hitCooldown = currentDelay;
            trainingHits++;
            
            // Уведомление в чат
            if (trainingHits == MIN_TRAINING_HITS) {
                if (mc.player != null) {
                    mc.player.sendMessage(net.minecraft.text.Text.literal("§a[NeuroAura] Обучение завершено! Стиль запомнен."), true);
                }
            }
        }
    }
    
    private void calculateLearnedProfile() {
        if (learnedDelays.isEmpty()) return;
        
        // Средняя задержка
        learnedAvgDelay = (float) learnedDelays.stream().mapToInt(v -> v).average().orElse(5.0);
        // Средняя ошибка поворота
        learnedAvgYawOffset = (float) learnedYawOffsets.stream().mapToDouble(v -> v).average().orElse(0.5);
        learnedAvgPitchOffset = (float) learnedPitchOffsets.stream().mapToDouble(v -> v).average().orElse(0.3);
        
        System.out.println("[NeuroAura] Профиль сохранён: Delay=" + learnedAvgDelay + 
                          ", YawError=" + learnedAvgYawOffset + 
                          ", PitchError=" + learnedAvgPitchOffset);
    }
    
    private int calculateAdaptiveDelay() {
        if (trainingHits < MIN_TRAINING_HITS) {
            // Если обучение не завершено — стандартная задержка
            return 3 + random.nextInt(6);
        }
        // Используем обученный стиль
        float variation = (random.nextFloat() - 0.5f) * (learnedAvgDelay * 0.3f);
        return Math.max(2, (int)(learnedAvgDelay + variation));
    }
    
    private float getNaturalYawError() {
        if (trainingHits < MIN_TRAINING_HITS) {
            return (float)((random.nextFloat() - 0.5) * 1.5);
        }
        return (float)((random.nextFloat() - 0.5) * learnedAvgYawOffset * 1.2);
    }
    
    private float getNaturalPitchError() {
        if (trainingHits < MIN_TRAINING_HITS) {
            return (float)((random.nextFloat() - 0.5) * 1.0);
        }
        return (float)((random.nextFloat() - 0.5) * learnedAvgPitchOffset * 1.2);
    }
    
    // ==================== ПОИСК NPC (для обучения) ====================
    
    private LivingEntity findNPC(MinecraftClient mc) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                String name = entity.getName().getString().toLowerCase();
                // NPC можно призвать через /z или это стойка
                if (name.contains("npc") || name.contains("dummy") || name.contains("з") || 
                    entity.getUuid().toString().contains("00000000-0000")) {
                    return (LivingEntity) entity;
                }
            }
        }
        return null;
    }
    
    // ==================== ПОИСК ЦЕЛИ (для боя) ====================
    
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
