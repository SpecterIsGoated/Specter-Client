package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.entity.DamageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class Blast {
   private Blast() {
   }

   public static Vec3 crystalAt(BlockPos base) {
      return Vec3.atBottomCenterOf(base.above());
   }

   public static AABB crystalBox(BlockPos base) {
      return EntityType.END_CRYSTAL.getDimensions().makeBoundingBox(crystalAt(base));
   }

   public static float crystal(LivingEntity target, Vec3 at, @Nullable BlockPos base) {
      return damage(target, at, 6.0F, base, base == null ? null : Blocks.OBSIDIAN.defaultBlockState());
   }

   public static float anchor(LivingEntity target, BlockPos anchor) {
      return damage(target, anchor.getCenter(), 5.0F, anchor, Blocks.AIR.defaultBlockState());
   }

   public static boolean anchorsExplode(Level world) {
      return world.dimension() != Level.NETHER;
   }

   public static float damage(LivingEntity target, Vec3 at, float power, @Nullable BlockPos swapPos, @Nullable BlockState swapState) {
      if (target != null && SpecterCore.mc.level != null) {
         if (!(target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            double distance = Math.sqrt(target.distanceToSqr(at));
            if (distance > power * 2.0) {
               return 0.0F;
            } else {
               double exposure = exposure(SpecterCore.mc.level, at, target.getBoundingBox(), swapPos, swapState);
               float raw = BlastMath.raw(power, distance, exposure);
               return raw <= 0.0F ? 0.0F : DamageUtils.calculateReductions(raw, target, SpecterCore.mc.level.damageSources().explosion(null));
            }
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   public static double exposure(Level world, Vec3 source, AABB box, @Nullable BlockPos swapPos, @Nullable BlockState swapState) {
      double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
      if (!(dx < 0.0) && !(dy < 0.0) && !(dz < 0.0)) {
         int seen = 0;
         int total = 0;

         for (double k = 0.0; k <= 1.0; k += dx) {
            for (double l = 0.0; l <= 1.0; l += dy) {
               for (double m = 0.0; m <= 1.0; m += dz) {
                  Vec3 point = new Vec3(Mth.lerp(k, box.minX, box.maxX) + offsetX, Mth.lerp(l, box.minY, box.maxY), Mth.lerp(m, box.minZ, box.maxZ) + offsetZ);
                  if (!blocked(world, point, source, swapPos, swapState)) {
                     seen++;
                  }

                  total++;
               }
            }
         }

         return total == 0 ? 0.0 : (double)seen / total;
      } else {
         return 0.0;
      }
   }

   private static boolean blocked(Level world, Vec3 from, Vec3 to, @Nullable BlockPos swapPos, @Nullable BlockState swapState) {
      Boolean hit = BlockGetter.traverseBlocks(from, to, null, (context, pos) -> {
         BlockState state = swapPos != null && pos.equals(swapPos) ? swapState : world.getBlockState(pos);
         VoxelShape shape = state.getCollisionShape(world, pos);
         BlockHitResult result = shape.clip(from, to, pos);
         return result == null ? null : Boolean.TRUE;
      }, context -> Boolean.FALSE);
      return hit != null && hit;
   }
}
