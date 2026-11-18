package com.jfant.specter.core.events.entity.player;

public class PlayerTickMovementEvent {
   private static final PlayerTickMovementEvent INSTANCE = new PlayerTickMovementEvent();

   public static PlayerTickMovementEvent get() {
      return INSTANCE;
   }
}
