package com.jfant.specter.mixin.move;

import com.jfant.specter.move.MoveHooks;
import com.jfant.specter.move.NoClip;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class MoveEntityMixin {
   @WrapMethod(
      method = {"move"}
   )
   private void specter$move(MoverType type, Vec3 movement, Operation<Void> original) {
      original.call(new Object[]{type, MoveHooks.adjust((Entity)this, type, movement)});
   }

   @Inject(
      method = {"canCollideWith"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$collides(Entity other, CallbackInfoReturnable<Boolean> cir) {
      if (this == Minecraft.getInstance().player && NoClip.ignoresCollisionWith(other)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"push(Lnet/minecraft/world/entity/Entity;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$push(Entity other, CallbackInfo ci) {
      Entity self = (Entity)this;
      boolean selfProtected = NoClip.blocksPush(self, other);
      boolean otherProtected = NoClip.blocksPush(other, self);
      if (selfProtected || otherProtected) {
         ci.cancel();
         if (!selfProtected || !otherProtected) {
            if (!self.isPassengerOfSameVehicle(other) && !other.noPhysics && !self.noPhysics) {
               double dx = other.getX() - self.getX();
               double dz = other.getZ() - self.getZ();
               double largest = Mth.absMax(dx, dz);
               if (!(largest < 0.009999999776482582)) {
                  largest = Math.sqrt(largest);
                  dx /= largest;
                  dz /= largest;
                  double soften = Math.min(1.0, 1.0 / largest);
                  dx *= soften * 0.05000000074505806;
                  dz *= soften * 0.05000000074505806;
                  if (!selfProtected && !self.isVehicle() && self.isPushable()) {
                     self.push(-dx, 0.0, -dz);
                  }

                  if (!otherProtected && !other.isVehicle() && other.isPushable()) {
                     other.push(dx, 0.0, dz);
                  }
               }
            }
         }
      }
   }
}
