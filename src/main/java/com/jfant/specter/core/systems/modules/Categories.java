package com.jfant.specter.core.systems.modules;

import com.jfant.specter.SpecterClientAddon;

public class Categories {
   public static boolean REGISTERING;

   public static void init() {
      REGISTERING = true;
      SpecterClientAddon.registerCategories();
      REGISTERING = false;
   }
}
