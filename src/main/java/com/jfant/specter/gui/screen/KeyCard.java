package com.jfant.specter.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.core.utils.misc.Keybind;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class KeyCard extends Card implements Focusable {
   private static final String PROMPT = "press a key...";
   private final Supplier<Keybind> keybind;
   private final Runnable changed;
   private final Runnable reset;

   KeyCard(String label, String description, Supplier<Keybind> keybind, Runnable changed, Runnable reset) {
      super(label, description);
      this.keybind = keybind;
      this.changed = changed;
      this.reset = reset;
   }

   @Override
   boolean compact() {
      return true;
   }

   @Override
   int minWidth() {
      return Ui.width(this.label) + Math.max(Ui.width("press a key..."), Ui.width(this.keybind.get().toString())) + 18;
   }

   @Override
   int height() {
      return 20;
   }

   @Override
   void content(GuiGraphicsExtractor ctx, int mx, int my, boolean hover) {
      boolean listening = Focus.has(this);
      float waiting = Ui.hover(this, "listening", listening);
      if (waiting > 0.01F) {
         float breath = 0.6F + 0.4F * (float)Math.sin(Motion.now() * 4.5);
         Ui.glow(ctx, this.x + 1, this.y + 1, this.w - 2, this.h - 2, waiting * breath);
      }

      String value = listening ? "press a key..." : this.keybind.get().toString();
      int textY = Ui.textY(this.y, this.h);
      Ui.text(ctx, Ui.fit(this.label, Math.max(16, this.w - 12 - Ui.width(value) - 8)), this.x + 6, textY, -1911561);
      Ui.textRight(ctx, Ui.fit(value, this.w - 12 - 20), this.x + this.w - 6, textY, listening ? -37177 : (this.keybind.get().isSet() ? -1 : -4149795));
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      Ui.press(this);
      Focus.set(this);
   }

   boolean mouse(int button) {
      if (!this.keybind.get().canBindTo(false, button, 0)) {
         return false;
      } else {
         this.keybind.get().set(false, button, 0);
         this.changed.run();
         Focus.clear();
         return true;
      }
   }

   @Override
   public boolean key(int key, int modifiers) {
      if (key == InputConstants.KEY_ESCAPE) {
         Focus.clear();
      } else if (key != InputConstants.KEY_DELETE && key != InputConstants.KEY_BACKSPACE) {
         if (this.keybind.get().canBindTo(true, key, modifiers)) {
            this.keybind.get().set(true, key, modifiers);
            this.changed.run();
            Focus.clear();
         }
      } else {
         this.keybind.get().set(Keybind.none());
         this.changed.run();
         Focus.clear();
      }

      return true;
   }

   @Override
   public boolean chr(int codePoint) {
      return true;
   }

   @Override
   public void blur() {
   }

   @Override
   void reset() {
      this.reset.run();
   }
}
