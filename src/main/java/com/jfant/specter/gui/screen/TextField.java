package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.render.PurpleCard;
import java.util.function.IntPredicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class TextField implements Focusable {
   String text = "";
   String placeholder = "";
   int maxLength = 256;
   IntPredicate filter;
   Runnable onChange;
   Runnable onCommit;
   Runnable onEnter;
   private int cursor;
   private int anchor;
   private int scrollX;
   private String atFocus = "";
   private int x;
   private int y;
   private int w;
   private int h;

   TextField(String placeholder) {
      this.placeholder = placeholder;
   }

   boolean focused() {
      return Focus.has(this);
   }

   void setText(String value) {
      this.text = value == null ? "" : value;
      this.cursor = this.anchor = this.text.length();
      this.scrollX = 0;
   }

   void focus() {
      this.atFocus = this.text;
      this.cursor = this.anchor = this.text.length();
      Focus.set(this);
   }

   void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      boolean focused = this.focused();
      PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 3, 15, -1609759464, -1609759464);
      float lit = Ui.hover(this, "focus", focused);
      if (lit > 0.01F) {
         PurpleCard.fillRounded(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 4, 15, Ui.alpha(822046407, lit), Ui.alpha(822046407, lit));
      }

      int edge = Ui.mix(-1333837902, -37177, lit);
      PurpleCard.strokeRounded(ctx, x, y, x + w, y + h, 3, edge, edge);
      int inner = w - 8;
      int caretPx = Ui.width(this.text.substring(0, this.cursor));
      if (caretPx - this.scrollX > inner) {
         this.scrollX = caretPx - inner;
      }

      if (caretPx < this.scrollX) {
         this.scrollX = caretPx;
      }

      if (Ui.width(this.text) <= inner) {
         this.scrollX = 0;
      }

      int textY = Ui.textY(y, h);
      ctx.enableScissor(x + 3, y, x + w - 3, y + h);
      if (this.text.isEmpty() && !focused) {
         Ui.text(ctx, this.placeholder, x + 4, textY, -1597985315);
      } else {
         if (focused && this.cursor != this.anchor) {
            int from = Ui.width(this.text.substring(0, Math.min(this.cursor, this.anchor))) - this.scrollX;
            int to = Ui.width(this.text.substring(0, Math.max(this.cursor, this.anchor))) - this.scrollX;
            Ui.fill(ctx, x + 4 + from, y + 2, x + 4 + to, y + h - 2, -2130743609);
         }

         Ui.text(ctx, this.text, x + 4 - this.scrollX, textY, -1);
         if (focused) {
            float at = Animator.follow(this, "caret", caretPx - this.scrollX, 0.07, Channel.GUI);
            boolean still = Math.abs(at - (caretPx - this.scrollX)) < 0.5F;
            double beat = Motion.now() * 3.141592653589793 / 0.55;
            float glow = still ? 0.35F + 0.65F * (float)(0.5 + 0.5 * Math.cos(beat)) : 1.0F;
            int cx = x + 4 + Math.round(at);
            Ui.fill(ctx, cx, y + 2, cx + 1, y + h - 2, Ui.alpha(-1, glow));
         }
      }

      ctx.disableScissor();
   }

   boolean click(int mx, int my) {
      if (!Ui.in(mx, my, this.x, this.y, this.w, this.h)) {
         return false;
      } else {
         Focus.touched = true;
         if (!this.focused()) {
            this.atFocus = this.text;
            Focus.set(this);
         }

         int target = mx - (this.x + 4) + this.scrollX;
         int best = 0;
         int bestDistance = 2147483647;

         for (int i = 0; i <= this.text.length(); i++) {
            int distance = Math.abs(Ui.width(this.text.substring(0, i)) - target);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = i;
            }
         }

         this.cursor = this.anchor = best;
         return true;
      }
   }

   @Override
   public boolean key(int key, int modifiers) {
      boolean control = (modifiers & 10) != 0;
      boolean shift = (modifiers & 1) != 0;
      if (key == 256) {
         this.text = this.atFocus;
         this.cursor = this.anchor = Math.min(this.cursor, this.text.length());
         Focus.clear();
         return true;
      } else if (key != 257 && key != 335) {
         if (control && key == 65) {
            this.anchor = 0;
            this.cursor = this.text.length();
            return true;
         } else if (control && key == 67) {
            if (this.cursor != this.anchor) {
               SpecterCore.mc.keyboardHandler.setClipboard(this.selected());
            }

            return true;
         } else if (control && key == 88) {
            if (this.cursor != this.anchor) {
               SpecterCore.mc.keyboardHandler.setClipboard(this.selected());
               this.deleteSelection();
               this.changed();
            }

            return true;
         } else if (control && key == 86) {
            this.insert(SpecterCore.mc.keyboardHandler.getClipboard());
            return true;
         } else if (key == 259) {
            if (this.cursor != this.anchor) {
               this.deleteSelection();
            } else {
               if (this.cursor <= 0) {
                  return true;
               }

               this.text = this.text.substring(0, this.cursor - 1) + this.text.substring(this.cursor);
               this.cursor--;
               this.anchor = this.cursor;
            }

            this.changed();
            return true;
         } else if (key == 261) {
            if (this.cursor != this.anchor) {
               this.deleteSelection();
            } else {
               if (this.cursor >= this.text.length()) {
                  return true;
               }

               this.text = this.text.substring(0, this.cursor) + this.text.substring(this.cursor + 1);
            }

            this.changed();
            return true;
         } else if (key == 263) {
            this.cursor = Math.max(0, this.cursor - 1);
            if (!shift) {
               this.anchor = this.cursor;
            }

            return true;
         } else if (key == 262) {
            this.cursor = Math.min(this.text.length(), this.cursor + 1);
            if (!shift) {
               this.anchor = this.cursor;
            }

            return true;
         } else if (key == 268) {
            this.cursor = 0;
            if (!shift) {
               this.anchor = this.cursor;
            }

            return true;
         } else if (key == 269) {
            this.cursor = this.text.length();
            if (!shift) {
               this.anchor = this.cursor;
            }

            return true;
         } else {
            return false;
         }
      } else if (this.onEnter != null) {
         this.onEnter.run();
         return true;
      } else {
         Focus.clear();
         return true;
      }
   }

   @Override
   public boolean chr(int codePoint) {
      if (codePoint < 32 || codePoint == 127) {
         return false;
      } else if (this.filter != null && !this.filter.test(codePoint)) {
         return true;
      } else {
         this.insert(new String(Character.toChars(codePoint)));
         return true;
      }
   }

   @Override
   public void blur() {
      if (this.onCommit != null) {
         this.onCommit.run();
      }
   }

   private String selected() {
      return this.text.substring(Math.min(this.cursor, this.anchor), Math.max(this.cursor, this.anchor));
   }

   private void deleteSelection() {
      int from = Math.min(this.cursor, this.anchor);
      int to = Math.max(this.cursor, this.anchor);
      this.text = this.text.substring(0, from) + this.text.substring(to);
      this.cursor = this.anchor = from;
   }

   private void insert(String added) {
      StringBuilder kept = new StringBuilder();
      int i = 0;

      while (i < added.length()) {
         int codePoint = added.codePointAt(i);
         i += Character.charCount(codePoint);
         if (codePoint >= 32 && (this.filter == null || this.filter.test(codePoint))) {
            kept.appendCodePoint(codePoint);
         }
      }

      if (!kept.isEmpty()) {
         if (this.cursor != this.anchor) {
            this.deleteSelection();
         }

         i = this.maxLength - this.text.length();
         if (i > 0) {
            String piece = kept.length() > i ? kept.substring(0, i) : kept.toString();
            this.text = this.text.substring(0, this.cursor) + piece + this.text.substring(this.cursor);
            this.cursor = this.cursor + piece.length();
            this.anchor = this.cursor;
            this.changed();
         }
      }
   }

   private void changed() {
      if (this.onChange != null) {
         this.onChange.run();
      }
   }
}
