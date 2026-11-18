package com.jfant.specter.core.events.entity.player;

import com.jfant.specter.core.events.Cancellable;

public class DoAttackEvent extends Cancellable {
   private static final DoAttackEvent INSTANCE = new DoAttackEvent();

   public static DoAttackEvent get() {
      return INSTANCE;
   }
}
