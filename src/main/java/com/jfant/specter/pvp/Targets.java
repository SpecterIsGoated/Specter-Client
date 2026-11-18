package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.friends.Friends;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Targets {
   private static final long LAST_HIT_MS = 5000L;
   private static int lastHitId = -1;
   private static long lastHitAt;

   private Targets() {
   }

   public static boolean enemy(Entity entity) {
      if (!(entity instanceof Player player && player != SpecterCore.mc.player)) {
         return false;
      } else {
         return player.isAlive() && !player.isCreative() && !player.isSpectator() ? !Friends.get().isFriend(player) : false;
      }
   }

   public static boolean fightable(Entity entity, boolean players, boolean hostiles) {
      return players && enemy(entity) ? true : hostiles && entity instanceof Enemy && entity instanceof LivingEntity living && living.isAlive();
   }

   public static void hit(Entity entity) {
      if (enemy(entity)) {
         lastHitId = entity.getId();
         lastHitAt = System.currentTimeMillis();
      }
   }

   @Nullable
   public static Player current(Vec3 around, double range) {
      if (SpecterCore.mc.level == null) {
         return null;
      } else {
         return System.currentTimeMillis() - lastHitAt < 5000L
               && SpecterCore.mc.level.getEntity(lastHitId) instanceof Player last
               && enemy(last)
               && last.position().distanceTo(around) <= range
            ? last
            : nearest(around, range);
      }
   }

   @Nullable
   public static Player nearest(Vec3 around, double range) {
      if (SpecterCore.mc.level == null) {
         return null;
      } else {
         Player best = null;
         double bestDistance = range * range;

         for (Player player : SpecterCore.mc.level.players()) {
            if (enemy(player)) {
               double distance = player.position().distanceToSqr(around);
               if (distance <= bestDistance) {
                  best = player;
                  bestDistance = distance;
               }
            }
         }

         return best;
      }
   }

   public static float health(LivingEntity entity) {
      return entity.getHealth() + entity.getAbsorptionAmount();
   }

   public static void reset() {
      lastHitId = -1;
      lastHitAt = 0L;
   }
}
