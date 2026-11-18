package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.EntityMoveEvent;
import com.jfant.specter.core.events.entity.player.JumpVelocityMultiplierEvent;
import com.jfant.specter.core.events.entity.player.PlayerMoveEvent;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({Entity.class})
public abstract class EntityMixin {
   @ModifyArgs(
      method = {"push(Lnet/minecraft/world/entity/Entity;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
      )
   )
   private void onPushAwayFrom(Args args, Entity entity) {
      if (entity instanceof FakePlayerEntity player && player.doNotPush) {
         args.set(0, 0.0);
         args.set(2, 0.0);
      }
   }

   @ModifyReturnValue(
      method = {"getBlockJumpFactor"},
      at = {@At("RETURN")}
   )
   private float onGetJumpVelocityMultiplier(float original) {
      if (this == SpecterCore.mc.player) {
         JumpVelocityMultiplierEvent event = (JumpVelocityMultiplierEvent)SpecterCore.EVENT_BUS.post(JumpVelocityMultiplierEvent.get());
         return original * event.multiplier;
      } else {
         return original;
      }
   }

   @Inject(
      method = {"move"},
      at = {@At("HEAD")}
   )
   private void onMove(MoverType type, Vec3 movement, CallbackInfo info) {
      if (this == SpecterCore.mc.player) {
         SpecterCore.EVENT_BUS.post(PlayerMoveEvent.get(type, movement));
      } else {
         SpecterCore.EVENT_BUS.post(EntityMoveEvent.get((Entity)this, movement));
      }
   }

   @Inject(
      method = {"isInvisibleTo"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> info) {
      if (player == null) {
         info.setReturnValue(false);
      }
   }

   @ModifyReturnValue(
      method = {"getPose"},
      at = {@At("RETURN")}
   )
   private Pose modifyGetPose(Pose original) {
      if (this != SpecterCore.mc.player) {
         return original;
      } else {
         return original == Pose.CROUCHING
               && !SpecterCore.mc.player.isShiftKeyDown()
               && ((PlayerEntityAccessor)SpecterCore.mc.player).meteor$canChangeIntoPose(Pose.STANDING)
            ? Pose.STANDING
            : original;
      }
   }
}
