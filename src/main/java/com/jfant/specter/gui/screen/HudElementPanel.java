package com.jfant.specter.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiKeyEvents;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElement;
import com.jfant.specter.core.systems.hud.XAnchor;
import com.jfant.specter.core.systems.hud.YAnchor;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.render.CardLayout;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.PurpleCard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudElementPanel extends Screen {
   private static final int WIDTH = 280;
   private static final int STATUS = 12;
   private final Screen parent;
   private final boolean right;
   private final SettingsPanel panel = new SettingsPanel();
   private int wx;
   private int wy;
   private int ww;
   private int wh;

   private HudElementPanel(Screen parent, boolean right, Page page) {
      super(Component.literal(page.title));
      this.parent = parent;
      this.right = right;
      this.panel.setPage(page);
   }

   public static HudElementPanel of(Screen parent, HudElement element) {
      Settings combined = new Settings();
      combined.groups.addAll(element.settings.groups);
      combined.groups.add(anchors(element));
      Card remove = new ButtonCard("Remove", "Deletes this element from the HUD.", true, () -> {
         element.remove();
         SpecterCore.mc.gui.setScreen(parent);
      });
      Page page = new Page(element, element.info.title, element.info.description, combined, element::isActive, element::toggle, List.of(remove));
      return new HudElementPanel(parent, element.x + element.getWidth() / 2 < Utils.getWindowWidth() / 2, page);
   }

   public static HudElementPanel of(Screen parent, CardSpot card) {
      Module module = card.owner();
      Page base = Page.of(module);
      Card remove = new ButtonCard("Remove", "Turns this card's module off, which takes the card off the HUD.", true, () -> {
         if (module.isActive()) {
            module.toggle();
         }

         SpecterCore.mc.gui.setScreen(parent);
      });
      List<Card> extras = new ArrayList<>(base.extras);
      extras.add(remove);
      Page page = new Page(module, base.title, base.description, base.settings, base.on, base.toggle, extras);
      float[] rect = CardLayout.liveRect(card);
      boolean right = rect == null || rect[0] + rect[2] / 2.0F < SpecterCore.mc.getWindow().getGuiScaledWidth() / 2.0F;
      return new HudElementPanel(parent, right, page);
   }

   public static HudElementPanel global(Screen parent) {
      Hud hud = Hud.get();
      Page page = new Page(hud, "HUD Settings", "Settings that apply to the whole HUD and the editor.", hud.settings, null, null, List.of());
      return new HudElementPanel(parent, true, page);
   }

   private static SettingGroup anchors(HudElement element) {
      Settings holder = new Settings();
      SettingGroup sg = holder.createGroup("Anchors");
      sg.add(
         new BoolSetting.Builder()
            .name("auto-anchors")
            .description("Automatically assigns anchors based on the position.")
            .defaultValue(true)
            .onModuleActivated(booleanSetting -> booleanSetting.set(element.autoAnchors))
            .onChanged(aBoolean -> {
               if (aBoolean) {
                  element.box.updateAnchors();
               }

               element.autoAnchors = aBoolean;
            })
            .build()
      );
      sg.add(
         new EnumSetting.Builder<XAnchor>()
                              .name("x-anchor")
                           .description("Horizontal anchor.")
                        .defaultValue(XAnchor.Left)
                     .visible(() -> !element.autoAnchors)
                  .onModuleActivated(xAnchorSetting -> xAnchorSetting.set(element.box.xAnchor))
               .onChanged(element.box::setXAnchor)
            .build()
      );
      sg.add(
         new EnumSetting.Builder<YAnchor>()
                              .name("y-anchor")
                           .description("Vertical anchor.")
                        .defaultValue(YAnchor.Top)
                     .visible(() -> !element.autoAnchors)
                  .onModuleActivated(yAnchorSetting -> yAnchorSetting.set(element.box.yAnchor))
               .onChanged(element.box::setYAnchor)
            .build()
      );
      return sg;
   }

   @Override
   protected void init() {
      GuiKeyEvents.canUseKeys = true;
      Focus.clear();
      Ui.dragging = null;
   }

   @Override
   public void removed() {
      Focus.clear();
      Ui.dragging = null;
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

   private void layout() {
      this.ww = Math.min(this.width - 8, 280);
      this.wh = this.height - 16;
      this.wy = 8;
      this.wx = this.right ? this.width - this.ww - 8 : 8;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float deltaTicks) {
      this.extractBackground(ctx, mouseX, mouseY, deltaTicks);
      this.layout();
      Ui.hint = "";
      Utils.unscaledProjection();
      HudEditorScreen.renderElements(ctx);
      Utils.scaledProjection();
      if (Ui.dragging != null) {
         if (!Input.isButtonPressed(InputConstants.MOUSE_BUTTON_LEFT)) {
            Ui.dragging.release();
            Ui.dragging = null;
         } else {
            Ui.dragging.drag(mouseX, mouseY);
         }
      }

      PurpleCard.card(ctx, this.wx, this.wy, this.wx + this.ww, this.wy + this.wh);
      int panelHeight = this.wh - 8 - 12 - 2;
      PurpleCard.fillRounded(ctx, this.wx + 4, this.wy + 4, this.wx + this.ww - 4, this.wy + 4 + panelHeight, 3, 15, 1343358752, 1343358752);
      PurpleCard.strokeRounded(ctx, this.wx + 4, this.wy + 4, this.wx + this.ww - 4, this.wy + 4 + panelHeight, 3, 1886408608, 1886408608);
      this.panel.render(ctx, this.wx + 6, this.wy + 6, this.ww - 12, panelHeight - 4, mouseX, mouseY);
      if (!Ui.hint.isEmpty()) {
         Ui.text(ctx, Ui.fit(Ui.hint, this.ww - 20), this.wx + 10, Ui.textY(this.wy + this.wh - 4 - 12, 12), -4149795);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.layout();
      int mx = (int)click.x();
      int my = (int)click.y();
      int button = click.button();
      Focus.touched = false;
      if (Focus.get() instanceof KeyCard keyCard && keyCard.mouse(button)) {
         return true;
      } else if (!Ui.in(mx, my, this.wx, this.wy, this.ww, this.wh)) {
         SpecterCore.mc.gui.setScreen(this.parent);
         return true;
      } else {
         this.panel.click(mx, my, button);
         if (!Focus.touched && Focus.get() != null) {
            Focus.clear();
         }

         return true;
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (Ui.dragging != null) {
         Ui.dragging.release();
         Ui.dragging = null;
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.panel.scrolled(verticalAmount);
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      Focusable focused = Focus.get();
      if (focused != null) {
         focused.key(input.key(), input.modifiers());
         return true;
      } else if (input.key() == InputConstants.KEY_ESCAPE) {
         if (this.panel.editing()) {
            this.panel.closeEditor();
         } else {
            SpecterCore.mc.gui.setScreen(this.parent);
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      Focusable focused = Focus.get();
      return focused != null && focused.chr(input.codepoint());
   }
}
