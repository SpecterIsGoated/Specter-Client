package com.jfant.specter.pvp;

public final class MouseTurns {
   private static volatile long lastNanos;
   private static volatile double lastDx;
   private static volatile double lastDy;

   private MouseTurns() {
   }

   public static void note(double dx, double dy) {
      if (dx != 0.0 || dy != 0.0) {
         lastNanos = System.nanoTime();
         lastDx = dx;
         lastDy = dy;
      }
   }

   public static boolean within(long millis) {
      return lastNanos != 0L && System.nanoTime() - lastNanos <= millis * 1000000L;
   }

   public static double dx() {
      return lastDx;
   }

   public static double dy() {
      return lastDy;
   }
}
