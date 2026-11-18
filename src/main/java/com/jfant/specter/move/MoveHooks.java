package com.jfant.specter.move;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class MoveHooks {
   private static final List<MoveHooks.Adjuster> adjusters = new ArrayList<>();

   private MoveHooks() {
   }

   public static void add(MoveHooks.Adjuster adjuster) {
      if (!adjusters.contains(adjuster)) {
         adjusters.add(adjuster);
      }
   }

   public static void remove(MoveHooks.Adjuster adjuster) {
      adjusters.remove(adjuster);
   }

   public static Vec3 adjust(Entity entity, MoverType type, Vec3 movement) {
      if (adjusters.isEmpty()) {
         return movement;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && (entity == mc.player || entity == mc.player.getVehicle())) {
            for (int i = 0; i < adjusters.size(); i++) {
               movement = adjusters.get(i).adjust(entity, type, movement);
            }

            return movement;
         } else {
            return movement;
         }
      }
   }

   public interface Adjuster {
      Vec3 adjust(Entity var1, MoverType var2, Vec3 var3);
   }
}
