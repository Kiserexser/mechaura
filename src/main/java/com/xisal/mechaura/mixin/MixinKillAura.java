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

@Mixin(MinecraftClient.class)
public class MixinKillAura {
    
    // НАСТРОЙКИ (меняй под себя)
    private static final float REACH = 3.5f;           // Дальность атаки
    private static final float FOV = 70f;              // Угол обзора
    private static final int MIN_DELAY = 3;            // Минимальная задержка (тики)
    private static final int MAX_DELAY = 12;           // Максимальная задержка (тики)
    
    private int hitCooldown = 0;
    private int nextDelay = MIN_DELAY;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        // Проверки
        if (!MechAura.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.isPaused()) return;
        
        // Атакуем только мечом
        if (!(mc.player.getMainHandStack().getItem() instanceof SwordItem)) return;
        
        // Кулдаун
        if (hitCooldown > 0) {
            hitCooldown--;
            return;
        }
        
        // Поиск цели
        LivingEntity target = findTarget(mc);
        if (target == null) return;
        
        // ========== ОБХОД: Stupidity Packet ==========
        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();
        
        float targetYaw = calculateYaw(mc.player, target);
        float targetPitch = calculatePitch(mc.player, target);
        
        // ========== ОБХОД: Рандомное дрожание ==========
        float randomYaw = (float) ((Math.random() - 0.5) * 2.0);  // ±1 градус
        float randomPitch = (float) ((Math.random() - 0.5) * 1.5); // ±0.75 градуса
        targetYaw += randomYaw;
        targetPitch += randomPitch;
        // ================================================
        
        // Отправляем фейковый поворот
        mc.player.setYaw(targetYaw);
        mc.player.setPitch(targetPitch);
        
        // Атакуем
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        // Возвращаем реальный поворот
        mc.player.setYaw(realYaw);
        mc.player.setPitch(realPitch);
        
        // ========== ОБХОД: Случайный CPS ==========
        nextDelay = MIN_DELAY + (int)(Math.random() * (MAX_DELAY - MIN_DELAY));
        hitCooldown = nextDelay;
        // ==========================================
    }
    
    private LivingEntity findTarget(MinecraftClient mc) {
        double minDistance = REACH;
        LivingEntity closest = null;
        
        for (Entity entity : mc.world.getEntities()) {
            // Только живые существа
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == mc.player) continue;
            
            LivingEntity living = (LivingEntity) entity;
            
            // Игнорируем мёртвых
            if (living.isDead()) continue;
            
            // Игнорируем NPC (ловушки античита)
            String name = living.getName().getString();
            if (name.contains("NPC") || name.contains("Fake") || name.contains("Bot")) continue;
            
            // Игнорируем подозрительные UUID
            if (living.getUuid().toString().contains("00000000-0000")) continue;
            
            // Игнорируем слишком новые сущности (античит-ловушки)
            if (living.age < 10) continue;
            
            double distance = mc.player.distanceTo(living);
            if (distance < minDistance && isInFov(mc, living)) {
                minDistance = distance;
                closest = living;
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
