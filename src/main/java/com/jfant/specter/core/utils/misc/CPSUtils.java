package com.jfant.specter.core.utils.misc;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.utils.PreInit;
import meteordevelopment.orbit.EventHandler;

public class CPSUtils {
   private static int clicks;
   private static int cps;
   private static int secondsClicking;
   private static long lastTime;

   private CPSUtils() {
   }

   @PreInit
   public static void init() {
      SpecterCore.EVENT_BUS.subscribe(CPSUtils.class);
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastTime >= 1000L) {
         if (cps == 0) {
            clicks = 0;
            secondsClicking = 0;
         } else {
            lastTime = currentTime;
            secondsClicking++;
            cps = 0;
         }
      }
   }

   public static void onAttack() {
      clicks++;
      cps++;
   }

   public static int getCpsAverage() {
      return clicks / (secondsClicking == 0 ? 1 : secondsClicking);
   }
}
