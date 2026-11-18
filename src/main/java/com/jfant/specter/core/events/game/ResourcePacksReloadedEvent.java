package com.jfant.specter.core.events.game;

public class ResourcePacksReloadedEvent {
   private static final ResourcePacksReloadedEvent INSTANCE = new ResourcePacksReloadedEvent();

   public static ResourcePacksReloadedEvent get() {
      return INSTANCE;
   }
}
