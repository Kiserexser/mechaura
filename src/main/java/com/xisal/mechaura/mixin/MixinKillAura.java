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
    private static final float REACH = 3.5f;
    private static final float FOV = 70f;
    private static final int MIN_DELAY = 4;
    private static final int MAX_DELAY = 8;
    private int hitCooldown = 0;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!MechAura.isEnabled() || mc.player == null || mc.world == null) return;
        
        if (!(mc.player.getMainHandStack().getItem() instanceof SwordItem)) return;
        if (hitCooldown > 0) { hitCooldown--; return; }
        
        LivingEntity target = findTarget(mc);
        if (target == null) return;
        
        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();
        float targetYaw = calculateYaw(mc.player, target);
        float targetPitch = calculatePitch(mc.player, target);
        
        mc.player.setYaw(targetYaw);
        mc.player.setPitch(targetPitch);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.setYaw(realYaw);
        mc.player.setPitch(realPitch);
        
        hitCooldown = MIN_DELAY + (int)(Math.random() * (MAX_DELAY - MIN_DELAY));
    }
    
    private LivingEntity findTarget(MinecraftClient mc) {
        double minDistance = REACH;
        LivingEntity closest = null;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity == mc.player) continue;
            if (((LivingEntity) entity).isDead()) continue;
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
