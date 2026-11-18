package com.jfant.specter.gui.screen;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiKeyEvents;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElementInfo;
import com.jfant.specter.core.systems.hud.HudGroup;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.PurpleCard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudAddPanel extends Screen {
   private static final int ROW = 18;
   private static final int ROW_GAP = 2;
   private static final int HEADER = 26;
   private static final int SEARCH = 24;
   private static final int STATUS = 12;
   private static final int WIDTH = 300;
   private final Screen parent;
   private final int x;
   private final int y;
   private final TextField search = new TextField("Search HUD elements");
   private final Scroll scroll = new Scroll();
   private final Set<HudElementInfo<?>> open = new HashSet<>();
   private int wx;
   private int wy;
   private int ww;
   private int wh;
   private int listTop;
   private int listBottom;
   private int statusTop;

   public HudAddPanel(Screen parent, int x, int y) {
      super(Component.literal("Add HUD element"));
      this.parent = parent;
      this.x = x;
      this.y = y;
      this.search.onChange = this.scroll::reset;
   }

   @Override
   protected void init() {
      GuiKeyEvents.canUseKeys = true;
      Focus.clear();
      Ui.dragging = null;
      this.search.focus();
   }

   @Override
   public void removed() {
      Focus.clear();
      GuiKeyEvents.canUseKeys = true;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      if (this.minecraft.level == null) {
         this.extractPanorama(context, deltaTicks);
      }
   }

   private List<HudAddPanel.Row> rows() {
      String query = this.search.text.trim();
      Map<HudGroup, List<HudAddPanel.Row>> grouped = new LinkedHashMap<>();

      for (HudElementInfo<?> info : Hud.get().infos.values()) {
         if (info.hasPresets() && !query.isEmpty()) {
            for (HudElementInfo<?>.Preset preset : info.presets) {
               String title = info.title + "  -  " + preset.title;
               if (Utils.searchTextDefault(title, query, false)) {
                  List<HudAddPanel.Row> into = grouped.computeIfAbsent(info.group, g -> new ArrayList<>());
                  into.add(new HudAddPanel.Row(null, title, info.description, preset, false));
               }
            }
         } else if (Utils.searchTextDefault(info.title, query, false)) {
            List<HudAddPanel.Row> into = grouped.computeIfAbsent(info.group, g -> new ArrayList<>());
            into.add(new HudAddPanel.Row(null, info.title, info.description, info, false));
            if (info.hasPresets() && this.open.contains(info)) {
               for (HudElementInfo<?>.Preset presetx : info.presets) {
                  into.add(new HudAddPanel.Row(null, presetx.title, info.description, presetx, true));
               }
            }
         }
      }

      List<HudGroup> groups = new ArrayList<>(grouped.keySet());
      groups.sort(Comparator.comparing(HudGroup::title));
      List<HudAddPanel.Row> list = new ArrayList<>();
      List<HudAddPanel.Row> cards = new ArrayList<>();

      for (CardSpot card : CardSpot.all()) {
         Module module = card.owner();
         if (module.category == SpecterClientAddon.HUD && !module.isActive() && Utils.searchTextDefault(module.title, query, false)) {
            cards.add(new HudAddPanel.Row(null, module.title, module.description, card, false));
         }
      }

      cards.sort(Comparator.comparing(HudAddPanel.Row::title));
      if (!cards.isEmpty()) {
         list.add(new HudAddPanel.Row("Specter", null, null, null, false));
         list.addAll(cards);
      }

      for (HudGroup group : groups) {
         list.add(new HudAddPanel.Row(group.title(), null, null, null, false));
         list.addAll(grouped.get(group));
      }

      return list;
   }

   private void pick(Object object) {
      if (object instanceof CardSpot card) {
         double s = SpecterCore.mc.getWindow().getGuiScale();
         card.moveTo((float)(this.x / s), (float)(this.y / s));
         if (!card.owner().isActive()) {
            card.owner().toggle();
         }

         SpecterCore.mc.gui.setScreen(this.parent);
      } else if (object instanceof HudElementInfo<?>.Preset preset) {
         Hud.get().add(preset, this.x, this.y);
         SpecterCore.mc.gui.setScreen(this.parent);
      } else if (object instanceof HudElementInfo<?> info) {
         if (info.hasPresets()) {
            if (!this.open.remove(info)) {
               this.open.add(info);
            }
         } else {
            Hud.get().add(info, this.x, this.y);
            SpecterCore.mc.gui.setScreen(this.parent);
         }
      }
   }

   private void layout() {
      this.ww = Math.min(this.width - 8, 300);
      this.wh = Math.min(this.height - 16, Math.max(200, this.height * 4 / 5));
      this.wx = (this.width - this.ww) / 2;
      this.wy = (this.height - this.wh) / 2;
      this.listTop = this.wy + 26 + 24;
      this.statusTop = this.wy + this.wh - 4 - 12;
      this.listBottom = this.statusTop - 2;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float deltaTicks) {
      this.renderBackground(ctx, mouseX, mouseY, deltaTicks);
      this.layout();
      Ui.hint = "";
      Utils.unscaledProjection();
      HudEditorScreen.renderElements(ctx);
      Utils.scaledProjection();
      PurpleCard.card(ctx, this.wx, this.wy, this.wx + this.ww, this.wy + this.wh);
      PurpleCard.fillRounded(ctx, this.wx + 3, this.wy + 3, this.wx + this.ww - 3, this.wy + 26 - 2, 3, 15, 2024816864, 812658592);
      Ui.text(ctx, "ADD HUD ELEMENT", this.wx + 12, Ui.textY(this.wy + 3, 21), -37177);
      this.search.render(ctx, this.wx + 6, this.wy + 26 + 2, this.ww - 12, 16);
      int listHeight = this.listBottom - this.listTop;
      PurpleCard.fillRounded(ctx, this.wx + 4, this.listTop, this.wx + this.ww - 4, this.listBottom, 3, 15, 1343358752, 1343358752);
      PurpleCard.strokeRounded(ctx, this.wx + 4, this.listTop, this.wx + this.ww - 4, this.listBottom, 3, 1886408608, 1886408608);
      List<HudAddPanel.Row> rows = this.rows();
      this.scroll.size(listHeight - 6, rows.size() * 20);
      int origin = this.listTop + 3 - this.scroll.offset();
      boolean over = Ui.in(mouseX, mouseY, this.wx + 4, this.listTop, this.ww - 8, listHeight);
      int rx = this.wx + 7;
      int rw = this.ww - 14 - 3;
      ctx.enableScissor(this.wx + 4, this.listTop + 1, this.wx + this.ww - 4, this.listBottom - 1);

      for (int i = 0; i < rows.size(); i++) {
         HudAddPanel.Row row = rows.get(i);
         int ry = origin + i * 20;
         if (ry + 18 >= this.listTop && ry <= this.listBottom) {
            if (row.header() != null) {
               Ui.text(ctx, row.header(), rx + 2, Ui.textY(ry, 18), -1065473);
               int lineStart = rx + 6 + Ui.width(row.header());
               Ui.fill(ctx, lineStart, ry + 9, rx + rw, ry + 9 + 1, 1440926975);
            } else {
               int indent = row.indent() ? 12 : 0;
               boolean hover = over && Ui.in(mouseX, mouseY, rx, ry, rw, 18);
               if (hover) {
                  Ui.wash(ctx, rx, ry, rw, 18);
                  Ui.hint = row.description();
               }

               String mark = "+";
               if (row.object() instanceof HudElementInfo<?> info && info.hasPresets() && this.search.text.isBlank()) {
                  mark = this.open.contains(info) ? "v" : ">";
               }

               Ui.text(ctx, Ui.fit(row.title(), rw - 10 - indent - 12), rx + 6 + indent, Ui.textY(ry, 18), hover ? -1 : -1911561);
               Ui.textRight(ctx, mark, rx + rw - 5, Ui.textY(ry, 18), hover ? -37177 : -4149795);
            }
         }
      }

      ctx.disableScissor();
      this.scroll.bar(ctx, this.wx + this.ww - 4, this.listTop + 3, listHeight - 6);
      if (rows.isEmpty()) {
         Ui.text(ctx, this.search.text.isBlank() ? "Every card is on the HUD already." : "Nothing matches.", this.wx + 14, this.listTop + 8, -4149795);
      }

      if (!Ui.hint.isEmpty()) {
         Ui.text(ctx, Ui.fit(Ui.hint, this.ww - 20), this.wx + 10, Ui.textY(this.statusTop, 12), -4149795);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.layout();
      int mx = (int)click.x();
      int my = (int)click.y();
      Focus.touched = false;
      if (!Ui.in(mx, my, this.wx, this.wy, this.ww, this.wh)) {
         SpecterCore.mc.gui.setScreen(this.parent);
         return true;
      } else {
         if (!this.search.click(mx, my) && click.button() != 1 && Ui.in(mx, my, this.wx + 4, this.listTop, this.ww - 8, this.listBottom - this.listTop)) {
            List<HudAddPanel.Row> rows = this.rows();
            int index = (my - (this.listTop + 3 - this.scroll.offset())) / 20;
            if (index >= 0 && index < rows.size()) {
               this.pick(rows.get(index).object());
            }
         }

         if (!Focus.touched && Focus.get() != null) {
            Focus.clear();
         }

         return true;
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scroll.by(verticalAmount);
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      Focusable focused = Focus.get();
      if (focused == null) {
         if (input.key() == 256) {
            SpecterCore.mc.gui.setScreen(this.parent);
            return true;
         } else {
            return false;
         }
      } else {
         if (focused == this.search && (input.key() == 257 || input.key() == 335)) {
            for (HudAddPanel.Row row : this.rows()) {
               if (row.object() != null) {
                  this.pick(row.object());
                  return true;
               }
            }
         }

         focused.key(input.key(), input.modifiers());
         return true;
      }
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      Focusable focused = Focus.get();
      if (focused != null) {
         return focused.chr(input.codepoint());
      } else if (input.codepoint() >= 32) {
         this.search.focus();
         return this.search.chr(input.codepoint());
      } else {
         return false;
      }
   }

   private record Row(String header, String title, String description, Object object, boolean indent) {
   }
}
