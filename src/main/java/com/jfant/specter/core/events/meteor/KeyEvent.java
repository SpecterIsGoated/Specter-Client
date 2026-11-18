package com.jfant.specter.core.events.meteor;

import com.jfant.specter.core.events.Cancellable;
import com.jfant.specter.core.utils.misc.input.KeyAction;

public class KeyEvent extends Cancellable {
   private static final KeyEvent INSTANCE = new KeyEvent();
   public net.minecraft.client.input.KeyEvent input;
   public KeyAction action;

   public static KeyEvent get(net.minecraft.client.input.KeyEvent input, KeyAction action) {
      INSTANCE.setCancelled(false);
      INSTANCE.input = input;
      INSTANCE.action = action;
      return INSTANCE;
   }

   public int key() {
      return INSTANCE.input.key();
   }

   public int modifiers() {
      return INSTANCE.input.modifiers();
   }
}
