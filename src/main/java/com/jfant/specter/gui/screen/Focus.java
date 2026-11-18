package com.jfant.specter.gui.screen;

import com.jfant.specter.core.gui.GuiKeyEvents;

final class Focus {
   private static Focusable current;
   static boolean touched;

   private Focus() {
   }

   static Focusable get() {
      return current;
   }

   static boolean has(Focusable focusable) {
      return current == focusable;
   }

   static void set(Focusable focusable) {
      if (focusable != null) {
         touched = true;
      }

      if (current != focusable) {
         Focusable old = current;
         current = focusable;
         GuiKeyEvents.canUseKeys = focusable == null;
         if (old != null) {
            old.blur();
         }
      }
   }

   static void clear() {
      set(null);
   }
}
