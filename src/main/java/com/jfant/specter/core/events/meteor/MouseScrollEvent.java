package com.jfant.specter.core.events.meteor;

import com.jfant.specter.core.events.Cancellable;

public class MouseScrollEvent extends Cancellable {
   private static final MouseScrollEvent INSTANCE = new MouseScrollEvent();
   public double value;

   public static MouseScrollEvent get(double value) {
      INSTANCE.setCancelled(false);
      INSTANCE.value = value;
      return INSTANCE;
   }
}
