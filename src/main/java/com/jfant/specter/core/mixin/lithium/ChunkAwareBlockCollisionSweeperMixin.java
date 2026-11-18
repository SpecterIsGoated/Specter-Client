package com.jfant.specter.core.mixin.lithium;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.CollisionShapeEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperVoxelShape;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ChunkAwareBlockCollisionSweeperVoxelShape.class})
public abstract class ChunkAwareBlockCollisionSweeperMixin extends ChunkAwareBlockCollisionSweeper<VoxelShape> {
   public ChunkAwareBlockCollisionSweeperMixin(Level world, @Nullable Entity entity, AABB box, boolean hideLastCollision) {
      super(world, entity, box, hideLastCollision);
   }

   @ModifyExpressionValue(
      method = {"computeNext()Lnet/minecraft/class_265;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
      )}
   )
   private VoxelShape modifyCollisionShape(VoxelShape original, @Local(name = {"state"}) BlockState state) {
      if (this.world != Minecraft.getInstance().level) {
         return original;
      } else {
         CollisionShapeEvent event = (CollisionShapeEvent)SpecterCore.EVENT_BUS.post(CollisionShapeEvent.get(state, this.pos, original));
         return event.isCancelled() ? Shapes.empty() : event.shape;
      }
   }
}
