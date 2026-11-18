package com.jfant.specter.auto;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.misc.input.Input;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class AutoInput {
   private AutoInput() {
   }

   public static void hold(KeyMapping key, boolean down) {
      if (key.isDown() != down) {
         key.setDown(down);
         if (key.isDown() != down) {
            key.setDown(true);
         }
      }
   }

   public static void restore(KeyMapping key) {
      key.setDown(SpecterCore.mc.screen == null && Input.isPressed(key));
   }

   public static boolean physicallyHeld(KeyMapping key) {
      return SpecterCore.mc.screen == null && Input.isPressed(key);
   }

   public static Vec3 pointAlong(LocalPlayer player, double yaw, double pitch) {
      double p = Math.toRadians(Math.max(-89.5, Math.min(89.5, pitch)));
      double y = Math.toRadians(yaw);
      double horizontal = Math.cos(p);
      Vec3 direction = new Vec3(-Math.sin(y) * horizontal, -Math.sin(p), Math.cos(y) * horizontal);
      return player.getEyePosition().add(direction.scale(10.0));
   }
}
