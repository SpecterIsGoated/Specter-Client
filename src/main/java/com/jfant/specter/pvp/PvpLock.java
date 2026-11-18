package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;

public final class PvpLock {
   private static Object owner;
   private static int actedAge = -1;

   private PvpLock() {
   }

   public static boolean acquire(Object who) {
      if (owner != null && owner != who) {
         return false;
      } else {
         owner = who;
         return true;
      }
   }

   public static void release(Object who) {
      if (owner == who) {
         owner = null;
      }
   }

   public static boolean heldByOther(Object who) {
      return owner != null && owner != who;
   }

   public static boolean free(Object who) {
      return !heldByOther(who);
   }

   public static void claimTick() {
      if (SpecterCore.mc.player != null) {
         actedAge = SpecterCore.mc.player.tickCount;
      }
   }

   public static boolean actedThisTick() {
      return SpecterCore.mc.player != null && actedAge == SpecterCore.mc.player.tickCount;
   }

   public static void reset() {
      owner = null;
      actedAge = -1;
   }
}
