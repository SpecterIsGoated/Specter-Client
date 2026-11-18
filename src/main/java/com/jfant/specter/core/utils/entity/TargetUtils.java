package com.jfant.specter.core.utils.entity;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerEntity;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerManager;
import com.jfant.specter.core.utils.player.PlayerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public class TargetUtils {
   private static final List<Entity> ENTITIES = new ArrayList<>();

   private TargetUtils() {
   }

   @Nullable
   public static Entity get(Predicate<Entity> isGood, SortPriority sortPriority) {
      ENTITIES.clear();
      getList(ENTITIES, isGood, sortPriority, 1);
      return !ENTITIES.isEmpty() ? ENTITIES.getFirst() : null;
   }

   public static void getList(List<Entity> targetList, Predicate<Entity> isGood, SortPriority sortPriority, int maxCount) {
      targetList.clear();

      for (Entity entity : SpecterCore.mc.level.entitiesForRendering()) {
         if (entity != null && isGood.test(entity)) {
            targetList.add(entity);
         }
      }

      FakePlayerManager.forEach(fp -> {
         if (fp != null && isGood.test(fp)) {
            targetList.add(fp);
         }
      });
      targetList.sort(sortPriority);
      if (targetList.size() > maxCount) {
         targetList.subList(maxCount, targetList.size()).clear();
      }
   }

   @Nullable
   public static Player getPlayerTarget(double range, SortPriority priority) {
      return !Utils.canUpdate() ? null : (Player)get(entity -> {
         if (!(entity instanceof Player player && entity != SpecterCore.mc.player)) {
            return false;
         } else if (player.isDeadOrDying() || player.getHealth() <= 0.0F) {
            return false;
         } else if (!PlayerUtils.isWithin(entity, range)) {
            return false;
         } else if (!Friends.get().shouldAttack(player)) {
            return false;
         } else {
            return entity instanceof FakePlayerEntity fakePlayer ? !fakePlayer.noHit : EntityUtils.getGameMode(player) == GameType.SURVIVAL;
         }
      }, priority);
   }

   public static boolean isBadTarget(Player target, double range) {
      return target == null ? true : !PlayerUtils.isWithin(target, range) || !target.isAlive() || target.isDeadOrDying() || target.getHealth() <= 0.0F;
   }
}
