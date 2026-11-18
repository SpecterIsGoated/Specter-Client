package com.jfant.specter.auto;

import com.jfant.specter.auto.inv.InvSync;
import com.jfant.specter.core.systems.modules.Modules;

public final class AutoModules {
   private AutoModules() {
   }

   public static void register() {
      InvSync.init();
      Modules.get().add(new AutoTrident());
      Modules.get().add(new AutoInvisibility());
   }
}
