package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

final class SettingsPanel {
   private static final int GAP = 4;
   private static final int TITLE_ROW = 24;
   private static final int GROUP_ROW = 14;
   private Page page;
   private final Map<Setting<?>, Card> cards = new IdentityHashMap<>();
   private Editor editor;
   private final Scroll scroll = new Scroll();
   private final List<SettingsPanel.Placed> placed = new ArrayList<>();
   private List<String> descriptionLines = List.of();
   private int contentHeight;
   private int x;
   private int y;
   private int w;
   private int h;
   private int regionTop;
   private int regionHeight;
   private static final int INLINE_EDITOR = 170;
   private int inlineLeft;
   private int inlineTop;

   Module module() {
      return this.page != null && this.page.key instanceof Module module ? module : null;
   }

   Object pageKey() {
      return this.page == null ? null : this.page.key;
   }

   void setModule(Module module) {
      if (module == null) {
         this.setPage(null);
      } else if (this.module() != module) {
         this.setPage(Page.of(module));
      }
   }

   void setPage(Page page) {
      if (this.page == null || page == null || this.page.key != page.key) {
         this.page = page;
         this.cards.clear();
         this.editor = null;
         this.scroll.reset();
         if (page != null) {
            page.settings.onActivated();
         }
      }
   }

   void open(Editor editor) {
      this.editor = editor;
   }

   void closeEditor() {
      this.editor = null;
      Focus.clear();
   }

   boolean editing() {
      return this.editor != null;
   }

   private Card cardFor(Setting<?> setting) {
      return this.cards.computeIfAbsent(setting, s -> Cards.create((Setting<?>)s, this));
   }

   private void layout(int cw, boolean inline) {
      this.placed.clear();
      this.descriptionLines = !inline && !this.page.description.isEmpty() ? Ui.wrap(this.page.description, cw) : List.of();
      int cursor = inline ? 1 : 2 + this.descriptionLines.size() * 10 + 6;
      int half = inline ? -1 : (cw - 4) / 2;
      List<List<Card>> groups = new ArrayList<>();
      List<SettingGroup> owners = new ArrayList<>();

      for (SettingGroup group : this.page.settings) {
         List<Card> visible = new ArrayList<>();

         for (Setting<?> setting : group) {
            if (setting.isVisible()) {
               visible.add(this.cardFor(setting));
            }
         }

         if (!visible.isEmpty()) {
            groups.add(visible);
            owners.add(group);
         }
      }

      for (int g = 0; g < groups.size(); g++) {
         boolean headed = groups.size() > 1;
         if (headed) {
            this.placed.add(new SettingsPanel.Placed(null, owners.get(g), 0, cursor, cw));
            cursor += 14;
         }

         if (!headed || owners.get(g).sectionExpanded) {
            cursor = this.flow(groups.get(g), cursor, cw, half);
         }

         cursor += 2;
      }

      if (!this.page.extras.isEmpty()) {
         if (!groups.isEmpty()) {
            this.placed.add(new SettingsPanel.Placed(null, null, 0, cursor, cw));
            cursor += 8;
         }

         cursor = this.flow(this.page.extras, cursor, cw, half);
      }

      this.contentHeight = cursor + (inline ? 1 : 6);
   }

   private int flow(List<Card> list, int cursor, int cw, int half) {
      Card waiting = null;

      for (Card card : list) {
         boolean small = card.compact() && card.minWidth() <= half;
         if (small && waiting == null) {
            waiting = card;
         } else if (small) {
            this.placed.add(new SettingsPanel.Placed(waiting, null, 0, cursor, half));
            this.placed.add(new SettingsPanel.Placed(card, null, half + 4, cursor, cw - half - 4));
            cursor += Math.max(waiting.height(), card.height()) + 4;
            waiting = null;
         } else {
            if (waiting != null) {
               this.placed.add(new SettingsPanel.Placed(waiting, null, 0, cursor, cw));
               cursor += waiting.height() + 4;
               waiting = null;
            }

            this.placed.add(new SettingsPanel.Placed(card, null, 0, cursor, cw));
            cursor += card.height() + 4;
         }
      }

      if (waiting != null) {
         this.placed.add(new SettingsPanel.Placed(waiting, null, 0, cursor, cw));
         cursor += waiting.height() + 4;
      }

      return cursor;
   }

   void render(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      if (this.editor != null) {
         this.editor.render(ctx, x, y, w, h, mx, my);
      } else if (this.page == null) {
         Ui.text(ctx, "Pick a module", x + 8, y + 8, -4149795);
      } else {
         Ui.text(ctx, Ui.fit(this.page.title, this.page.on != null ? w - 52 : w - 12), x + 6, Ui.textY(y, 24), -1065473);
         if (this.page.on != null) {
            Ui.modulePill(ctx, x + w - 36, y + 6, 30, 12, this.page.on.getAsBoolean());
         }

         Ui.fill(ctx, x + 4, y + 24 - 1, x + w - 4, y + 24, 1440926975);
         this.regionTop = y + 24 + 2;
         this.regionHeight = h - 24 - 2;
         int cw = w - 12 - 4;
         this.layout(cw, false);
         this.scroll.size(this.regionHeight, this.contentHeight);
         int origin = this.regionTop - this.scroll.offset();
         int left = x + 6;
         boolean over = Ui.in(mx, my, x, this.regionTop, w, this.regionHeight);
         int hx = over ? mx : -9999;
         int hy = over ? my : -9999;
         ctx.enableScissor(x, this.regionTop, x + w, this.regionTop + this.regionHeight);
         int textTop = origin + 2;

         for (String line : this.descriptionLines) {
            Ui.text(ctx, line, left, textTop, -4149795);
            textTop += 10;
         }

         this.drawPlaced(ctx, left, origin, cw, hx, hy, this.regionTop, this.regionTop + this.regionHeight);
         ctx.disableScissor();
         this.scroll.bar(ctx, x + w - 1, this.regionTop, this.regionHeight);
      }
   }

   private void drawPlaced(GuiGraphics ctx, int left, int origin, int cw, int hx, int hy, int clipTop, int clipBottom) {
      for (SettingsPanel.Placed p : this.placed) {
         int top = origin + p.cy();
         if (p.card() == null) {
            if (p.group() == null) {
               Ui.fill(ctx, left, top + 3, left + cw, top + 4, 1440926975);
            } else {
               SettingGroup group = p.group();
               boolean hover = Ui.in(hx, hy, left, top, cw, 12);
               float lit = Ui.hover(group, "head", hover);
               Ui.wash(ctx, left, top, cw, 12, lit);
               if (hover) {
                  Ui.hint = group.sectionExpanded ? "Click to fold " + group.name + " away." : "Click to show " + group.name + ".";
               }

               int color = Ui.mix(-1065473, -37177, lit);
               Ui.arrow(ctx, left + 3, top + 2, Animator.progress(group, "open", group.sectionExpanded, Spec.EXPAND, Spec.COLLAPSE), color);
               Ui.text(ctx, group.name, left + 12, top + 2, color);
               int lineStart = left + 16 + Ui.width(group.name);
               Ui.fill(ctx, lineStart, top + 6, left + cw, top + 7, 1440926975);
            }
         } else {
            Card card = p.card();
            card.x = left + p.cx();
            card.y = top;
            card.w = p.cw();
            card.h = card.height();
            if (top + card.h >= clipTop && top <= clipBottom) {
               card.render(ctx, hx, hy);
            }
         }
      }
   }

   boolean click(int mx, int my, int button) {
      if (this.editor != null) {
         return this.editor.click(mx, my, button);
      } else if (this.page == null) {
         return false;
      } else if (this.page.toggle != null && Ui.in(mx, my, this.x + this.w - 36, this.y + 6, 30, 12)) {
         if (button != 1) {
            this.page.toggle.run();
         }

         return true;
      } else {
         return !Ui.in(mx, my, this.x, this.regionTop, this.w, this.regionHeight)
            ? false
            : this.clickPlaced(mx, my, button, this.x + 6, this.regionTop - this.scroll.offset());
      }
   }

   private boolean clickPlaced(int mx, int my, int button, int left, int origin) {
      for (SettingsPanel.Placed p : this.placed) {
         Card card = p.card();
         if (card == null && p.group() != null && Ui.in(mx, my, left, origin + p.cy(), p.cw(), 12)) {
            if (button != 1) {
               p.group().sectionExpanded = !p.group().sectionExpanded;
            }

            return true;
         }

         if (card != null && card.contains(mx, my)) {
            if (button == 1) {
               card.reset();
            } else {
               card.click(mx, my, button, Ui.shiftDown());
            }

            return true;
         }
      }

      return true;
   }

   void scrolled(double amount) {
      if (this.editor != null) {
         this.editor.scrolled(amount);
      } else {
         this.scroll.by(amount);
      }
   }

   int inlineHeight(int w) {
      if (this.page == null) {
         return 0;
      } else if (this.editor != null) {
         return 170;
      } else {
         this.layout(w - 8, true);
         return this.contentHeight;
      }
   }

   void renderInline(GuiGraphics ctx, int x, int y, int w, int mx, int my) {
      if (this.page != null) {
         if (this.editor != null) {
            this.editor.render(ctx, x, y, w, 170, mx, my);
         } else {
            this.inlineLeft = x + 4;
            this.inlineTop = y;
            this.layout(w - 8, true);
            this.drawPlaced(ctx, this.inlineLeft, y, w - 8, mx, my, -2147483648, 2147483647);
         }
      }
   }

   boolean clickInline(int mx, int my, int button) {
      if (this.editor != null) {
         return this.editor.click(mx, my, button);
      } else {
         return this.page == null ? false : this.clickPlaced(mx, my, button, this.inlineLeft, this.inlineTop);
      }
   }

   boolean scrolledInline(double amount) {
      if (this.editor == null) {
         return false;
      } else {
         this.editor.scrolled(amount);
         return true;
      }
   }

   private record Placed(Card card, SettingGroup group, int cx, int cy, int cw) {
   }
}
