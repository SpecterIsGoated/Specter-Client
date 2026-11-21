package com.jfant.specter.gui.screen;

import com.jfant.specter.core.gui.GuiKeyEvents;
import net.minecraft.client.Minecraft;

/**
 * Which Specter text field (or key bind) has the keyboard.
 *
 * <p>Minecraft 26.3 reads the keyboard through SDL3, which only sends typed characters while "text input" is on for
 * the window. Vanilla turns it on for its own text boxes only, so Specter's fields (the search bar, text settings,
 * value fields) do it themselves here; without it, keys like Backspace work but no letter or digit is ever typed.
 */
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
            textInput(old, false);
            old.blur();
         }

         if (focusable != null) {
            textInput(focusable, true);
         }
      }
   }

   static void clear() {
      set(null);
   }

   /** Switches SDL text input on for (or off from) this owner. Never throws: typing is not worth a crash. */
   private static void textInput(Focusable owner, boolean on) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null || mc.textInputManager() == null) {
            return;
         }

         if (on) {
            mc.textInputManager().startTextInput(owner);
         } else {
            mc.textInputManager().stopTextInput(owner);
            // Back to the screen itself, so typing anywhere in Specter's GUI still starts a search.
            if (mc.gui.screen() instanceof SpecterScreen screen) {
               mc.textInputManager().startTextInput(screen);
            }
         }
      } catch (RuntimeException | LinkageError ignored) {
         // A window that is going away; the next focus tries again.
      }
   }
}
