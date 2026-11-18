package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.CollisionShapeEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BlockCollisions.class})
public abstract class BlockCollisionSpliteratorMixin {
   @WrapOperation(
      method = {"computeNext"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
      )}
   )
   private VoxelShape onComputeNextCollisionBox(
      CollisionContext instance, BlockState blockState, CollisionGetter collisionView, BlockPos blockPos, Operation<VoxelShape> original
   ) {
      VoxelShape shape = (VoxelShape)original.call(new Object[]{instance, blockState, collisionView, blockPos});
      if (collisionView != Minecraft.getInstance().level) {
         return shape;
      } else {
         CollisionShapeEvent event = (CollisionShapeEvent)SpecterCore.EVENT_BUS.post(CollisionShapeEvent.get(blockState, blockPos, shape));
         return event.isCancelled() ? Shapes.empty() : event.shape;
      }
   }
}
