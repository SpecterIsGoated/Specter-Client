package com.jfant.specter.core.utils.player;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.mixininterface.IVec3d;
import com.jfant.specter.core.pathing.PathManagers;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.entity.DamageUtils;
import com.jfant.specter.core.utils.entity.EntityUtils;
import com.jfant.specter.core.utils.misc.text.TextUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.world.Dimension;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class PlayerUtils {
   private static final double diagonal = 1.0 / Math.sqrt(2.0);
   private static final Vec3 horizontalVelocity = new Vec3(0.0, 0.0, 0.0);
   private static final Color color = new Color();

   private PlayerUtils() {
   }

   public static Color getPlayerColor(Player entity, Color defaultColor) {
      if (Friends.get().isFriend(entity)) {
         return color.set(Config.get().friendColor.get()).a(defaultColor.a);
      } else {
         return Config.get().useTeamColor.get() && !color.set(TextUtils.getMostPopularColor(entity.getDisplayName())).equals(Utils.WHITE)
            ? color.a(defaultColor.a)
            : defaultColor;
      }
   }

   public static Vec3 getHorizontalVelocity(double bps) {
      float yaw = SpecterCore.mc.player.getYRot();
      if (PathManagers.get().isPathing()) {
         yaw = PathManagers.get().getTargetYaw();
      }

      Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
      Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
      double velX = 0.0;
      double velZ = 0.0;
      boolean a = false;
      if (SpecterCore.mc.player.input.keyPresses.forward()) {
         velX += forward.x / 20.0 * bps;
         velZ += forward.z / 20.0 * bps;
         a = true;
      }

      if (SpecterCore.mc.player.input.keyPresses.backward()) {
         velX -= forward.x / 20.0 * bps;
         velZ -= forward.z / 20.0 * bps;
         a = true;
      }

      boolean b = false;
      if (SpecterCore.mc.player.input.keyPresses.right()) {
         velX += right.x / 20.0 * bps;
         velZ += right.z / 20.0 * bps;
         b = true;
      }

      if (SpecterCore.mc.player.input.keyPresses.left()) {
         velX -= right.x / 20.0 * bps;
         velZ -= right.z / 20.0 * bps;
         b = true;
      }

      if (a && b) {
         velX *= diagonal;
         velZ *= diagonal;
      }

      ((IVec3d)horizontalVelocity).meteor$setXZ(velX, velZ);
      return horizontalVelocity;
   }

   public static void centerPlayer() {
      double x = Mth.floor(SpecterCore.mc.player.getX()) + 0.5;
      double z = Mth.floor(SpecterCore.mc.player.getZ()) + 0.5;
      SpecterCore.mc.player.setPos(x, SpecterCore.mc.player.getY(), z);
      SpecterCore.mc
         .player
         .connection
         .send(
            new Pos(
               SpecterCore.mc.player.getX(),
               SpecterCore.mc.player.getY(),
               SpecterCore.mc.player.getZ(),
               SpecterCore.mc.player.onGround(),
               SpecterCore.mc.player.horizontalCollision
            )
         );
   }

   public static boolean canSeeEntity(Entity entity) {
      Vec3 vec1 = new Vec3(0.0, 0.0, 0.0);
      Vec3 vec2 = new Vec3(0.0, 0.0, 0.0);
      ((IVec3d)vec1)
         .meteor$set(SpecterCore.mc.player.getX(), SpecterCore.mc.player.getY() + SpecterCore.mc.player.getEyeHeight(), SpecterCore.mc.player.getZ());
      ((IVec3d)vec2).meteor$set(entity.getX(), entity.getY(), entity.getZ());
      boolean canSeeFeet = SpecterCore.mc.level.clip(new ClipContext(vec1, vec2, Block.COLLIDER, Fluid.NONE, SpecterCore.mc.player)).getType() == Type.MISS;
      ((IVec3d)vec2).meteor$set(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
      boolean canSeeEyes = SpecterCore.mc.level.clip(new ClipContext(vec1, vec2, Block.COLLIDER, Fluid.NONE, SpecterCore.mc.player)).getType() == Type.MISS;
      return canSeeFeet || canSeeEyes;
   }

   public static float[] calculateAngle(Vec3 target) {
      Vec3 eyesPos = new Vec3(
         SpecterCore.mc.player.getX(),
         SpecterCore.mc.player.getY() + SpecterCore.mc.player.getEyeHeight(SpecterCore.mc.player.getPose()),
         SpecterCore.mc.player.getZ()
      );
      double dX = target.x - eyesPos.x;
      double dY = (target.y - eyesPos.y) * -1.0;
      double dZ = target.z - eyesPos.z;
      double dist = Math.sqrt(dX * dX + dZ * dZ);
      return new float[]{(float)Mth.wrapDegrees(Math.toDegrees(Math.atan2(dZ, dX)) - 90.0), (float)Mth.wrapDegrees(Math.toDegrees(Math.atan2(dY, dist)))};
   }

   public static boolean shouldPause(boolean ifBreaking, boolean ifEating, boolean ifDrinking) {
      if (ifBreaking && SpecterCore.mc.gameMode.isDestroying()) {
         return true;
      } else {
         return !ifEating
               || !SpecterCore.mc.player.isUsingItem()
               || !SpecterCore.mc.player.getMainHandItem().getItem().components().has(DataComponents.FOOD)
                  && !SpecterCore.mc.player.getOffhandItem().getItem().components().has(DataComponents.FOOD)
            ? ifDrinking
               && SpecterCore.mc.player.isUsingItem()
               && (
                  SpecterCore.mc.player.getMainHandItem().getItem() instanceof PotionItem
                     || SpecterCore.mc.player.getOffhandItem().getItem() instanceof PotionItem
               )
            : true;
      }
   }

   public static boolean isMoving() {
      return SpecterCore.mc.player.zza != 0.0F || SpecterCore.mc.player.xxa != 0.0F;
   }

   public static boolean isSprinting() {
      return SpecterCore.mc.player.isSprinting() && (SpecterCore.mc.player.zza != 0.0F || SpecterCore.mc.player.xxa != 0.0F);
   }

   public static boolean isInHole(boolean doubles) {
      if (!Utils.canUpdate()) {
         return false;
      } else {
         BlockPos blockPos = SpecterCore.mc.player.blockPosition();
         int air = 0;

         for (Direction direction : Direction.values()) {
            if (direction != Direction.UP) {
               BlockState state = SpecterCore.mc.level.getBlockState(blockPos.relative(direction));
               if (state.getBlock().getExplosionResistance() < 600.0F) {
                  if (!doubles || direction == Direction.DOWN) {
                     return false;
                  }

                  air++;

                  for (Direction dir : Direction.values()) {
                     if (dir != direction.getOpposite() && dir != Direction.UP) {
                        BlockState blockState1 = SpecterCore.mc.level.getBlockState(blockPos.relative(direction).relative(dir));
                        if (blockState1.getBlock().getExplosionResistance() < 600.0F) {
                           return false;
                        }
                     }
                  }
               }
            }
         }

         return air < 2;
      }
   }

   public static float possibleHealthReductions() {
      return possibleHealthReductions(true, true);
   }

   public static float possibleHealthReductions(boolean entities, boolean fall) {
      float damageTaken = 0.0F;
      if (entities) {
         for (Entity entity : SpecterCore.mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal) {
               float crystalDamage = DamageUtils.crystalDamage(SpecterCore.mc.player, entity.position());
               if (crystalDamage > damageTaken) {
                  damageTaken = crystalDamage;
               }
            } else if (entity instanceof Player player && !Friends.get().isFriend(player) && isWithin(entity, 5.0)) {
               float attackDamage = DamageUtils.getAttackDamage(player, SpecterCore.mc.player);
               if (attackDamage > damageTaken) {
                  damageTaken = attackDamage;
               }
            }
         }

         if (SpecterCore.mc.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.BED_RULE).explodes()) {
            for (BlockEntity blockEntity : Utils.blockEntities()) {
               BlockPos bp = blockEntity.getBlockPos();
               Vec3 pos = new Vec3(bp.getX(), bp.getY(), bp.getZ());
               if (blockEntity instanceof BedBlockEntity) {
                  float explosionDamage = DamageUtils.bedDamage(SpecterCore.mc.player, pos);
                  if (explosionDamage > damageTaken) {
                     damageTaken = explosionDamage;
                  }
               }
            }
         }
      }

      if (fall && SpecterCore.mc.player.fallDistance > 3.0) {
         float damage = DamageUtils.fallDamage(SpecterCore.mc.player);
         if (damage > damageTaken && !EntityUtils.isAboveWater(SpecterCore.mc.player)) {
            damageTaken = damage;
         }
      }

      return damageTaken;
   }

   public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
      return Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
   }

   public static double distanceTo(Entity entity) {
      return distanceTo(entity.getX(), entity.getY(), entity.getZ());
   }

   public static double distanceTo(BlockPos blockPos) {
      return distanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public static double distanceTo(Vec3 vec3d) {
      return distanceTo(vec3d.x(), vec3d.y(), vec3d.z());
   }

   public static double distanceTo(double x, double y, double z) {
      return Math.sqrt(squaredDistanceTo(x, y, z));
   }

   public static double squaredDistanceTo(Entity entity) {
      return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());
   }

   public static double squaredDistanceTo(BlockPos blockPos) {
      return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public static double squaredDistanceTo(double x, double y, double z) {
      return squaredDistance(SpecterCore.mc.player.getX(), SpecterCore.mc.player.getY(), SpecterCore.mc.player.getZ(), x, y, z);
   }

   public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double f = x1 - x2;
      double g = y1 - y2;
      double h = z1 - z2;
      return org.joml.Math.fma(f, f, org.joml.Math.fma(g, g, h * h));
   }

   public static boolean isWithin(Entity entity, double r) {
      return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ()) <= r * r;
   }

   public static boolean isWithin(Vec3 vec3d, double r) {
      return squaredDistanceTo(vec3d.x(), vec3d.y(), vec3d.z()) <= r * r;
   }

   public static boolean isWithin(BlockPos blockPos, double r) {
      return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= r * r;
   }

   public static boolean isWithin(double x, double y, double z, double r) {
      return squaredDistanceTo(x, y, z) <= r * r;
   }

   public static double distanceToCamera(double x, double y, double z) {
      return Math.sqrt(squaredDistanceToCamera(x, y, z));
   }

   public static double distanceToCamera(Entity entity) {
      return distanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
   }

   public static double squaredDistanceToCamera(double x, double y, double z) {
      Vec3 cameraPos = SpecterCore.mc.gameRenderer.getMainCamera().position();
      return squaredDistance(cameraPos.x, cameraPos.y, cameraPos.z, x, y, z);
   }

   public static double squaredDistanceToCamera(Entity entity) {
      return squaredDistanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
   }

   public static boolean isWithinCamera(Entity entity, double r) {
      return squaredDistanceToCamera(entity.getX(), entity.getY(), entity.getZ()) <= r * r;
   }

   public static boolean isWithinCamera(Vec3 vec3d, double r) {
      return squaredDistanceToCamera(vec3d.x(), vec3d.y(), vec3d.z()) <= r * r;
   }

   public static boolean isWithinCamera(BlockPos blockPos, double r) {
      return squaredDistanceToCamera(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= r * r;
   }

   public static boolean isWithinCamera(double x, double y, double z, double r) {
      return squaredDistanceToCamera(x, y, z) <= r * r;
   }

   public static boolean isWithinReach(Entity entity) {
      return isWithinReach(entity.getX(), entity.getY(), entity.getZ());
   }

   public static boolean isWithinReach(Vec3 vec3d) {
      return isWithinReach(vec3d.x(), vec3d.y(), vec3d.z());
   }

   public static boolean isWithinReach(BlockPos blockPos) {
      return isWithinReach(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public static boolean isWithinReach(double x, double y, double z) {
      return squaredDistance(SpecterCore.mc.player.getX(), SpecterCore.mc.player.getEyeY(), SpecterCore.mc.player.getZ(), x, y, z)
         <= SpecterCore.mc.player.blockInteractionRange() * SpecterCore.mc.player.blockInteractionRange();
   }

   public static Dimension getDimension() {
      if (SpecterCore.mc.level == null) {
         return Dimension.Overworld;
      } else {
         String var0 = SpecterCore.mc.level.dimension().identifier().getPath();

         return switch (var0) {
            case "the_nether" -> Dimension.Nether;
            case "the_end" -> Dimension.End;
            default -> Dimension.Overworld;
         };
      }
   }

   public static GameType getGameMode() {
      if (SpecterCore.mc.player == null) {
         return null;
      } else {
         PlayerInfo playerListEntry = SpecterCore.mc.getConnection().getPlayerInfo(SpecterCore.mc.player.getUUID());
         return playerListEntry == null ? null : playerListEntry.getGameMode();
      }
   }

   public static float getTotalHealth() {
      return SpecterCore.mc.player.getHealth() + SpecterCore.mc.player.getAbsorptionAmount();
   }

   public static boolean isAlive() {
      return SpecterCore.mc.player.isAlive() && !SpecterCore.mc.player.isDeadOrDying();
   }

   public static int getPing() {
      if (SpecterCore.mc.getConnection() == null) {
         return 0;
      } else {
         PlayerInfo playerListEntry = SpecterCore.mc.getConnection().getPlayerInfo(SpecterCore.mc.player.getUUID());
         return playerListEntry == null ? 0 : playerListEntry.getLatency();
      }
   }
}
