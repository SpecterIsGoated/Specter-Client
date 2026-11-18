package com.jfant.specter.core.events.game;

import com.jfant.specter.core.events.Cancellable;
import net.minecraft.client.gui.screens.Screen;

public class OpenScreenEvent extends Cancellable {
   private static final OpenScreenEvent INSTANCE = new OpenScreenEvent();
   public Screen screen;

   public static OpenScreenEvent get(Screen screen) {
      INSTANCE.setCancelled(false);
      INSTANCE.screen = screen;
      return INSTANCE;
   }
}
