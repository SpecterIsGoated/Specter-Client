package com.jfant.specter.render;

import com.jfant.specter.core.systems.config.Config;

public enum Theme {
   Blocky("Blocky"),
   Clean("Clean");

   private final String title;
   private static Config config;

   private Theme(String title) {
      this.title = title;
   }

   @Override
   public String toString() {
      return this.title;
   }

   public static boolean clean() {
      if (config == null) {
         config = Config.get();
      }

      return config != null && config.theme.get() == Clean;
   }
}
