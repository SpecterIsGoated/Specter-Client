package com.jfant.specter.core.events.entity.player;

import com.jfant.specter.core.events.Cancellable;

public class DoItemUseEvent extends Cancellable {
   private static final DoItemUseEvent INSTANCE = new DoItemUseEvent();

   public static DoItemUseEvent get() {
      return INSTANCE;
   }
}
