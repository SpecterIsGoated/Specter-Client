package com.jfant.specter.move;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class MoveInput {
   private static final Minecraft mc = Minecraft.getInstance();

   private MoveInput() {
   }

   public static boolean forward() {
      return mc.options.keyUp.isDown();
   }

   public static boolean back() {
      return mc.options.keyDown.isDown();
   }

   public static boolean left() {
      return mc.options.keyLeft.isDown();
   }

   public static boolean right() {
      return mc.options.keyRight.isDown();
   }

   public static boolean jump() {
      return mc.options.keyJump.isDown();
   }

   public static boolean sneak() {
      return mc.options.keyShift.isDown();
   }

   public static boolean sprintKey() {
      return mc.options.keySprint.isDown();
   }

   public static boolean anyHorizontal() {
      Options o = mc.options;
      return o.keyUp.isDown() != o.keyDown.isDown() || o.keyLeft.isDown() != o.keyRight.isDown();
   }

   public static int vertical() {
      return (jump() ? 1 : 0) - (sneak() ? 1 : 0);
   }

   public static boolean direction(float yawDegrees, double[] out) {
      double forward = (forward() ? 1 : 0) - (back() ? 1 : 0);
      double strafe = (left() ? 1 : 0) - (right() ? 1 : 0);
      if (forward == 0.0 && strafe == 0.0) {
         out[0] = 0.0;
         out[1] = 0.0;
         return false;
      } else {
         double length = Math.sqrt(forward * forward + strafe * strafe);
         forward /= length;
         strafe /= length;
         double yaw = Math.toRadians(yawDegrees);
         double sin = Math.sin(yaw);
         double cos = Math.cos(yaw);
         out[0] = forward * -sin + strafe * cos;
         out[1] = forward * cos + strafe * sin;
         return true;
      }
   }
}
