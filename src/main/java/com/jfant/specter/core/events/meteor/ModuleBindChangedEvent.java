package com.jfant.specter.core.events.meteor;

import com.jfant.specter.core.systems.modules.Module;

public class ModuleBindChangedEvent {
   private static final ModuleBindChangedEvent INSTANCE = new ModuleBindChangedEvent();
   public Module module;

   public static ModuleBindChangedEvent get(Module module) {
      INSTANCE.module = module;
      return INSTANCE;
   }
}
