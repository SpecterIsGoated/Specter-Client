package com.jfant.specter.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

/**
 * The local player as cosmetics see it this frame: interpolated feet position, body yaw, head height and what they are
 * doing (flying, sneaking, swimming). Filled once per frame by {@link #update(float)}, read by every cosmetic.
 */
public final class Wearer {
   public static double x, y, z;
   /** Body yaw in radians (0 = facing +z, like the game). */
   public static double bodyYaw;
   public static double headY;
   public static float pitchRad;
   public static boolean flying, sneaking, swimming, onGround, thirdPerson, valid;
   /** Horizontal speed in blocks per tick and vertical speed (smoothed). */
   public static float speed, climb;

   private Wearer() {
   }

   public static boolean update(float pt) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      valid = p != null && mc.level != null && !p.isRemoved();
      if (!valid) return false;
      x = Mth.lerp(pt, p.xo, p.getX());
      y = Mth.lerp(pt, p.yo, p.getY());
      z = Mth.lerp(pt, p.zo, p.getZ());
      bodyYaw = Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
      pitchRad = (float)Math.toRadians(p.getViewXRot(pt));
      headY = y + p.getEyeHeight(p.getPose()) + 0.12;
      flying = p.isFallFlying();
      sneaking = p.isCrouching();
      swimming = p.getPose() == Pose.SWIMMING;
      onGround = p.onGround();
      thirdPerson = !mc.options.getCameraType().isFirstPerson();
      double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
      speed = (float)Math.sqrt(dx * dx + dz * dz);
      climb = (float)(p.getY() - p.yo);
      return true;
   }

   /** Forward unit vector of the body. */
   public static double fwdX() {
      return -Math.sin(bodyYaw);
   }

   public static double fwdZ() {
      return Math.cos(bodyYaw);
   }
}
