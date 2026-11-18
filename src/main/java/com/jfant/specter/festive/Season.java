package com.jfant.specter.festive;

import com.jfant.specter.core.systems.config.Config;

public enum Season {
   Off("Off"),
   Winter("Winter"),
   Christmas("Christmas");

   private final String title;
   private static Config config;

   private Season(String title) {
      this.title = title;
   }

   @Override
   public String toString() {
      return this.title;
   }

   public static Season now() {
      if (config == null) {
         config = Config.get();
      }

      return config == null ? Off : config.season.get();
   }

   public static boolean wintry() {
      return now() != Off;
   }

   public static boolean christmas() {
      return now() == Christmas;
   }
}
