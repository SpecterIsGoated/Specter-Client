package com.jfant.specter.pvp;

public final class AimMath {
   private static final double EASE_RATE = 12.0;

   private AimMath() {
   }

   public static double step(double error, double degreesPerSecond, double seconds) {
      if (!(seconds <= 0.0) && !(degreesPerSecond <= 0.0)) {
         double eased = error * (1.0 - Math.exp(-12.0 * seconds));
         double cap = degreesPerSecond * seconds;
         return Math.max(-cap, Math.min(cap, eased));
      } else {
         return 0.0;
      }
   }

   public static double[] counts(double degrees, double carry, double degreesPerCount) {
      if (degreesPerCount <= 0.0) {
         return new double[]{0.0, 0.0};
      } else {
         double total = degrees + carry;
         long whole = (long)(total / degreesPerCount);
         return new double[]{whole, total - whole * degreesPerCount};
      }
   }

   public static double wrap(double from, double to) {
      double d = (to - from) % 360.0;
      if (d > 180.0) {
         d -= 360.0;
      }

      if (d <= -180.0) {
         d += 360.0;
      }

      return d;
   }

   public static boolean withHand(double correction, double handDelta) {
      return handDelta == 0.0 || correction == 0.0 || Math.signum(correction) == Math.signum(handDelta);
   }
}
