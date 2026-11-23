package com.jfant.specter.phone.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;

/**
 * A single-line text field: caret, arrows, home/end, backspace/delete, Ctrl+A/C/V/X, Enter to submit. Keep one per
 * field (PhoneUi.field(key) does that) and draw it with {@link PhoneUi#textField}.
 */
public final class TextInput {
   private final StringBuilder text = new StringBuilder();
   private int caret;
   private boolean all;
   public int maxLength = 120;
   public String placeholder = "";
   public Runnable onSubmit;
   public Runnable onChange;
   /** Which characters may be typed (default: printable). */
   public IntPredicate allowed = cp -> cp >= 32 && cp != 127;
   long focusedAt;

   public String get() {
      return this.text.toString();
   }

   public void set(String value) {
      this.text.setLength(0);
      this.text.append(value == null ? "" : value.length() > this.maxLength ? value.substring(0, this.maxLength) : value);
      this.caret = this.text.length();
      this.all = false;
   }

   public boolean isEmpty() {
      return this.text.isEmpty();
   }

   int caret() {
      return this.caret;
   }

   boolean allSelected() {
      return this.all;
   }

   boolean key(int key) {
      Minecraft mc = Minecraft.getInstance();
      boolean ctrl = mc.hasControlDown();
      if (ctrl && key == InputConstants.KEY_A) {
         this.all = true;
         return true;
      }
      if (ctrl && key == InputConstants.KEY_C) {
         mc.keyboardHandler.setClipboard(this.get());
         return true;
      }
      if (ctrl && key == InputConstants.KEY_X) {
         mc.keyboardHandler.setClipboard(this.get());
         this.set("");
         this.changed();
         return true;
      }
      if (ctrl && key == InputConstants.KEY_V) {
         String paste = mc.keyboardHandler.getClipboard();
         if (paste != null) {
            paste.codePoints().filter(cp -> cp != '\n' && cp != '\r').forEach(this::insert);
            this.changed();
         }
         return true;
      }
      if (key == InputConstants.KEY_BACKSPACE) {
         if (this.all) {
            this.set("");
         } else if (this.caret > 0) {
            int from = ctrl ? this.wordStart(this.caret) : this.text.offsetByCodePoints(this.caret, -1);
            this.text.delete(from, this.caret);
            this.caret = from;
         }
         this.changed();
         return true;
      }
      if (key == InputConstants.KEY_DELETE) {
         if (this.all) {
            this.set("");
         } else if (this.caret < this.text.length()) {
            this.text.delete(this.caret, this.text.offsetByCodePoints(this.caret, 1));
         }
         this.changed();
         return true;
      }
      if (key == InputConstants.KEY_LEFT) {
         this.all = false;
         if (this.caret > 0) this.caret = ctrl ? this.wordStart(this.caret) : this.text.offsetByCodePoints(this.caret, -1);
         return true;
      }
      if (key == InputConstants.KEY_RIGHT) {
         this.all = false;
         if (this.caret < this.text.length()) this.caret = this.text.offsetByCodePoints(this.caret, 1);
         return true;
      }
      if (key == InputConstants.KEY_HOME) {
         this.all = false;
         this.caret = 0;
         return true;
      }
      if (key == InputConstants.KEY_END) {
         this.all = false;
         this.caret = this.text.length();
         return true;
      }
      if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
         if (this.onSubmit != null) this.onSubmit.run();
         return true;
      }
      return false;
   }

   boolean chr(int codePoint) {
      if (!this.allowed.test(codePoint)) return false;
      this.insert(codePoint);
      this.changed();
      return true;
   }

   private void insert(int codePoint) {
      if (this.all) {
         this.text.setLength(0);
         this.caret = 0;
         this.all = false;
      }
      if (!this.allowed.test(codePoint)) return;
      if (this.text.length() + Character.charCount(codePoint) > this.maxLength) return;
      this.text.insert(this.caret, Character.toChars(codePoint));
      this.caret += Character.charCount(codePoint);
   }

   private int wordStart(int from) {
      int i = from;
      while (i > 0 && this.text.charAt(i - 1) == ' ') i--;
      while (i > 0 && this.text.charAt(i - 1) != ' ') i--;
      return i;
   }

   private void changed() {
      this.focusedAt = System.currentTimeMillis();
      if (this.onChange != null) this.onChange.run();
   }
}
