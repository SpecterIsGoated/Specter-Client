package com.jfant.specter.core.systems.hud.screens;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.utils.Cell;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.containers.WContainer;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.gui.widgets.pressable.WMinus;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.systems.hud.HudElement;
import com.jfant.specter.core.systems.hud.XAnchor;
import com.jfant.specter.core.systems.hud.YAnchor;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.NbtUtils;
import net.minecraft.client.gui.GuiGraphics;

public class HudElementScreen extends WindowScreen {
   private final HudElement element;
   private WContainer settingsC1;
   private WContainer settingsC2;
   private final Settings settings;

   public HudElementScreen(GuiTheme theme, HudElement element) {
      super(theme, element.info.title);
      this.element = element;
      this.settings = new Settings();
      SettingGroup sg = this.settings.createGroup("Anchors");
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
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder()
                              .name("x-anchor"))
                           .description("Horizontal anchor."))
                        .defaultValue(XAnchor.Left))
                     .visible(() -> !element.autoAnchors))
                  .onModuleActivated(xAnchorSetting -> xAnchorSetting.set(element.box.xAnchor)))
               .onChanged(element.box::setXAnchor))
            .build()
      );
      sg.add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder()
                              .name("y-anchor"))
                           .description("Vertical anchor."))
                        .defaultValue(YAnchor.Top))
                     .visible(() -> !element.autoAnchors))
                  .onModuleActivated(yAnchorSetting -> yAnchorSetting.set(element.box.yAnchor)))
               .onChanged(element.box::setYAnchor))
            .build()
      );
   }

   @Override
   public void initWidgets() {
      this.add(this.theme.label(this.element.info.description, Utils.getWindowWidth() / 2.0));
      if (this.element.settings.sizeGroups() > 0) {
         this.element.settings.onActivated();
         this.settingsC1 = this.add(this.theme.verticalList()).expandX().widget();
         this.settingsC1.add(this.theme.settings(this.element.settings)).expandX();
      }

      this.settings.onActivated();
      this.settingsC2 = this.add(this.theme.verticalList()).expandX().widget();
      this.settingsC2.add(this.theme.settings(this.settings)).expandX();
      this.add(this.theme.horizontalSeparator()).expandX();
      WWidget widget = this.element.getWidget(this.theme);
      if (widget != null) {
         Cell<WWidget> cell = this.add(widget);
         if (widget instanceof WContainer) {
            cell.expandX();
         }

         this.add(this.theme.horizontalSeparator()).expandX();
      }

      WHorizontalList bottomList = this.add(this.theme.horizontalList()).expandX().widget();
      bottomList.add(this.theme.label("Active:"));
      WCheckbox active = bottomList.add(this.theme.checkbox(this.element.isActive())).widget();
      active.action = () -> {
         if (this.element.isActive() != active.checked) {
            this.element.toggle();
         }
      };
      WMinus remove = bottomList.add(this.theme.minus()).expandCellX().right().widget();
      remove.action = () -> {
         this.element.remove();
         this.onClose();
      };
   }

   @Override
   public void tick() {
      super.tick();
      if (this.settingsC1 != null) {
         this.element.settings.tick(this.settingsC1, this.theme);
      }

      this.settings.tick(this.settingsC2, this.theme);
   }

   @Override
   protected void onRenderBefore(GuiGraphics drawContext, float delta) {
      HudEditorScreen.renderElements(drawContext);
   }

   @Override
   public boolean toClipboard() {
      return NbtUtils.toClipboard(this.element);
   }

   @Override
   public boolean fromClipboard() {
      return NbtUtils.fromClipboard(this.element);
   }
}
