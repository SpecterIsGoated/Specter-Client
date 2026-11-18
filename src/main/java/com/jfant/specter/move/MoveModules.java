package com.jfant.specter.move;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.modules.Modules;

public final class MoveModules {
   private MoveModules() {
   }

   public static void register() {
      Modules modules = Modules.get();
      modules.add(new Fly());
      modules.add(new BoatFly());
      modules.add(new NoClip());
      modules.add(new Speed());
      modules.add(new NoFall());
      modules.add(new Jesus());
      modules.add(new Xray());
      modules.add(new Fullbright());
      modules.add(new AutoSprint());
      SpecterCore.EVENT_BUS.subscribe(new LandingGuard());
   }
}
