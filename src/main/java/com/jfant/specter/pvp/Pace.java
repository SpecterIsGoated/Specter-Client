package com.jfant.specter.pvp;

import java.util.concurrent.ThreadLocalRandom;

public final class Pace {
   private Pace() {
   }

   public static int ticks(int base, int spread) {
      if (spread <= 0) {
         return Math.max(0, base);
      } else {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         double middle = (random.nextDouble() + random.nextDouble()) / 2.0;
         return Math.max(0, base + (int)Math.round(middle * spread));
      }
   }

   public static boolean chance(double percent) {
      return percent >= 100.0 || ThreadLocalRandom.current().nextDouble() * 100.0 < percent;
   }
}
